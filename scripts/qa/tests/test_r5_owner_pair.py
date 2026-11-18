import http.server
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import threading
import unittest
from socketserver import ThreadingMixIn

SCRIPT = pathlib.Path(__file__).parents[1] / 'r5-owner-pair-ubuntu.py'

class Server(ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

class Handler(http.server.BaseHTTPRequestHandler):
    request_count = 0
    def log_message(self, *args):
        pass
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('content-length', '2')
            self.end_headers()
            self.wfile.write(b'OK')
            return
        if self.path.startswith('/v1/pair/status?requestId='):
            self.reply(200, {'status':'approved','scope':'persistent','token': 'a'*64})
            return
        self.reply(404, {'error':{'code':'NOT_FOUND'}})
    def reply(self, code, payload):
        data = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def do_POST(self):
        if self.path != '/v1/pair':
            self.reply(404, {'error':{'code':'NOT_FOUND'}})
            return
        obj = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        assert self.headers['Content-Type']=='application/json'
        assert len(obj['computerPublicKey'])==64
        self.server.posts.append(obj)
        if len(self.server.posts) == 1:
            self.reply(202, {'status':'computer_challenge','requestId':'b'*64})
        else:
            assert obj['computerSignature']
            assert obj['requestId']=='b'*64
            with tempfile.TemporaryDirectory() as t:
                public_der = bytes.fromhex('302a300506032b6570032100' + obj['computerPublicKey'])
                public_path=pathlib.Path(t)/'pub.der'
                signature_path=pathlib.Path(t)/'sig.bin'
                message_path=pathlib.Path(t)/'msg.bin'
                public_path.write_bytes(public_der)
                signature_path.write_bytes(bytes.fromhex(obj['computerSignature']))
                message_path.write_bytes(('kelpie-computer-pair-v1\n' + obj['requestId'] + '\n' + obj['computerPublicKey']).encode('ascii'))
                ver = subprocess.run(['openssl','pkeyutl','-verify','-pubin','-inkey',str(public_path),'-keyform','DER','-sigfile',str(signature_path),'-in',str(message_path),'-rawin'],capture_output=True)
                assert ver.returncode == 0, ver.stderr
            self.reply(202, {'status':'pending','requestId':'b'*64,'approvalRequired':True})

class TestHelper(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = Server(('127.0.0.1',0), Handler)
        cls.server.posts=[]
        cls.thread = threading.Thread(target=cls.server.serve_forever,daemon=True)
        cls.thread.start()
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
    def test_full_pairing_and_probe(self):
        with tempfile.TemporaryDirectory() as folder:
            base=f'http://127.0.0.1:{self.server.server_port}'
            probe=subprocess.run(['python3',str(SCRIPT),'--base',base,'--probe'],capture_output=True,text=True)
            self.assertEqual(probe.returncode,0,probe.stderr)
            self.assertIn('LOCAL_HEALTH_PASS',probe.stdout)
            proc=subprocess.run(['python3',str(SCRIPT),'--base',base,'--state-dir',folder+'/state','--timeout','30'],capture_output=True,text=True,timeout=45)
            self.assertEqual(proc.returncode,0,proc.stderr)
            self.assertIn('PAIR_APPROVED:',proc.stdout)
            self.assertNotIn('a'*64, proc.stdout + proc.stderr)
            self.assertTrue((pathlib.Path(folder)/'state'/'ed25519-private.pem').exists())
            self.assertEqual((pathlib.Path(folder)/'state'/'authorization-header.txt').read_text(),'Bearer '+'a'*64+'\n')
            self.assertEqual(((pathlib.Path(folder)/'state').stat().st_mode & 0o777),0o700)
            self.assertEqual(((pathlib.Path(folder)/'state'/'authorization-header.txt').stat().st_mode & 0o777),0o600)
            self.assertEqual(((pathlib.Path(folder)/'state'/'ed25519-private.pem').stat().st_mode & 0o777),0o600)
            self.assertEqual(len(self.server.posts),2)
            self.assertEqual(self.server.posts[0]['computerPublicKey'],self.server.posts[1]['computerPublicKey'])
    def test_no_nonlocal_target(self):
        with tempfile.TemporaryDirectory() as folder:
            proc=subprocess.run(['python3',str(SCRIPT),'--base','https://example.org','--state-dir',folder],capture_output=True,text=True)
            self.assertNotEqual(proc.returncode,0)
            self.assertIn('ONLY_127_0_0_1_LOOPBACK_SUPPORTED',proc.stderr)
            self.assertEqual(list(pathlib.Path(folder).iterdir()),[])

if __name__=='__main__': unittest.main(verbosity=2)
