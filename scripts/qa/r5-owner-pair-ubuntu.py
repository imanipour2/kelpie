#!/usr/bin/env python3
"""On-device R5 owner pairing; only loopback, only Ed25519 verified keys.

Never emits bearer tokens or private keys to stdout, stderr or subprocess args.
Requires python3 stdlib and openssl with Ed25519 support.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

SPKI_ED25519 = bytes.fromhex('302a300506032b6570032100')
BASE = 'http://127.0.0.1:18461'

class PairError(RuntimeError):
    pass


def secure_dir(path):
    if path.is_symlink():
        raise PairError('STATE_DIR_SYMLINK_REFUSED')
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode & 0o077:
        raise PairError('STATE_DIR_PERMISSIONS_UNSAFE')


def https_local_only(base):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname != '127.0.0.1' or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
        raise PairError('ONLY_127_0_0_1_LOOPBACK_SUPPORTED')
    if not isinstance(parsed.port, int) or not (1024 <= parsed.port <= 65535):
        raise PairError('INVALID_LOCAL_PORT')
    return base


def http_json(base, path, payload=None):
    if not path.startswith('/') or not path.lstrip('/').replace('/', '').replace('-', '').replace('?', '').replace('=', '').replace('%', '').isalnum():
        raise PairError('INVALID_REQUEST_PATH')
    data = json.dumps(payload, separators=(',', ':')).encode('utf-8') if payload is not None else None
    headers = {'Accept': 'application/json'}
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, data=data, headers=headers, method='POST' if data is not None else 'GET')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=7) as response:
            raw = response.read(65537)
            if len(raw) > 65536:
                raise PairError('RESPONSE_TOO_LARGE')
            obj = json.loads(raw)
            return response.status, obj
    except urllib.error.HTTPError as error:
        try:
            result = json.loads(error.read(65536))
            code = result.get('error', {}).get('code', 'HTTP_ERROR')
        except (json.JSONDecodeError, ValueError, TypeError):
            code = 'HTTP_ERROR'
        # Never print the response body; it can contain secrets on unexpected servers.
        raise PairError(f'HTTP_{error.code}_{str(code)[:60]}') from None
    except urllib.error.URLError:
        raise PairError('LOOPBACK_SERVER_UNREACHABLE') from None


def run_openssl(args, data=None):
    command = ['openssl', *args]
    try:
        p = subprocess.run(command, input=data, capture_output=True, timeout=20, check=True)
        return p.stdout
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise PairError('OPENSSL_COMMAND_FAILED') from None


def identity(keyfile):
    if keyfile.is_symlink():
        raise PairError('KEY_SYMLINK_REFUSED')
    if not keyfile.exists():
        # Script-level flock protects against concurrent issuance and overwriting.
        run_openssl(['genpkey', '-algorithm', 'Ed25519', '-out', str(keyfile)])
        keyfile.chmod(0o600)
    if stat.S_IMODE(keyfile.stat().st_mode) & 0o077:
        raise PairError('PRIVATE_KEY_PERMISSIONS_UNSAFE')
    public = run_openssl(['pkey', '-in', str(keyfile), '-pubout', '-outform', 'DER'])
    if len(public) != 44 or not public.startswith(SPKI_ED25519):
        raise PairError('UNEXPECTED_ED25519_KEY')
    return public[len(SPKI_ED25519):].hex()


def atomic_secret(path, contents):
    if path.is_symlink():
        raise PairError('SECRET_PATH_SYMLINK_REFUSED')
    temporary = path.with_suffix('.pending')
    if temporary.is_symlink():
        raise PairError('SECRET_TMP_SYMLINK_REFUSED')
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, 'O_NOFOLLOW'):
        flags |= os.O_NOFOLLOW
    try:
        fd = os.open(str(temporary), flags, 0o600)
        try:
            with os.fdopen(fd, 'w', encoding='ascii') as stream:
                stream.write(contents)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
            path.chmod(0o600)
        except Exception:
            try:
                temporary.unlink(missing_ok=True)
            finally:
                raise
    except FileExistsError:
        raise PairError('SECRET_TEMP_ALREADY_EXISTS') from None


def pair(base, state, duration):
    if duration < 30 or duration > 600:
        raise PairError('INVALID_APPROVAL_TIMEOUT')
    keyfile = state / 'ed25519-private.pem'
    pubkey = identity(keyfile)
    client = {'clientId': str(uuid.uuid4()), 'clientName': 'ENERFOAM S24 owner MCP bridge', 'computerPublicKey': pubkey}
    print('PAIRING_CLIENT_NAME=ENERFOAM S24 owner MCP bridge')
    print('PAIRING_PUBLIC_KEY_SUFFIX=' + pubkey[-12:])
    status, started = http_json(base, '/v1/pair', client)
    if status != 202 or started.get('status') != 'computer_challenge':
        raise PairError('NO_SIGNED_CHALLENGE')
    request_id = started.get('requestId')
    if not isinstance(request_id, str) or not re.fullmatch(r'[0-9a-f]{64}', request_id):
        raise PairError('INVALID_CHALLENGE_FORMAT')
    message = f'kelpie-computer-pair-v1\n{request_id}\n{pubkey}'.encode('ascii')
    # OpenSSL Ed25519 one-shot signing needs a seekable input file. Stdin fails.
    with tempfile.NamedTemporaryFile(prefix='.challenge-', dir=str(state)) as challenge_file:
        challenge_file.write(message)
        challenge_file.flush()
        signature = run_openssl(['pkeyutl', '-sign', '-inkey', str(keyfile), '-rawin', '-in', challenge_file.name])
    if len(signature) != 64:
        raise PairError('INVALID_SIGNATURE_LENGTH')
    proven = {**client, 'requestId': request_id, 'computerSignature': signature.hex()}
    status, result = http_json(base, '/v1/pair', proven)
    if status != 202 or result.get('status') != 'pending':
        raise PairError('PAIRING_PROOF_REJECTED')
    print('PAIR_PROOF_ACCEPTED: signed local owner identity')
    if result.get('approvalRequired'):
        print('NATIVE_APPROVAL_REQUIRED: switch to Kelpie and verify the pending computer request.')
        print('Only tap Allow computer for this request you just initiated. Do not approve unexpected clients.')
    else:
        print('EXISTING_COMPUTER_KEY_TRUST_REUSED')
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
        code, info = http_json(base, '/v1/pair/status?requestId=' + request_id)
        if code != 200:
            raise PairError('PAIRING_STATUS_HTTP_NOT_200')
        status = info.get('status')
        if status == 'approved':
            token = info.get('token')
            if not isinstance(token, str) or not re.fullmatch(r'[0-9a-f]{64}', token):
                raise PairError('APPROVED_WITHOUT_VALID_TOKEN')
            # token content never appears in process argv or terminal output.
            atomic_secret(state / 'authorization-header.txt', 'Bearer ' + token + '\n')
            print('PAIR_APPROVED: local key and bearer stored privately; no token printed.')
            print('AUTH_HEADER_FILE=' + str(state / 'authorization-header.txt'))
            print('NEXT: configure mcp.extra_headers + discovery_extra_headers with file reference, then service restart.')
            return
        if status in ('denied', 'not_found'):
            raise PairError('PAIR_' + status.upper())
        time.sleep(2)
    raise PairError('NATIVE_APPROVAL_TIMEOUT')


def main():
    parser = argparse.ArgumentParser(description='Owner-initiated S24 R5 Ed25519 pairing, no data leakage.')
    parser.add_argument('--base', default=BASE)
    parser.add_argument('--state-dir', default=str(Path.home() / '.local/share/kelpie-r5-owner'))
    parser.add_argument('--timeout', default=300, type=int)
    parser.add_argument('--probe', action='store_true', help='Only check localhost health and Ed25519 sign support')
    args = parser.parse_args()
    base = https_local_only(args.base)
    os.umask(0o077)
    if args.probe:
        req = urllib.request.Request(base + '/health')
        try:
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=7) as rsp:
                if rsp.status != 200:
                    raise PairError('HEALTH_NOT_200')
        except urllib.error.URLError:
            raise PairError('LOOPBACK_SERVER_UNREACHABLE') from None
        print('LOCAL_HEALTH_PASS')
        run_openssl(['version'])
        print('OPENSSL_PRESENT')
        return
    state = Path(args.state_dir).expanduser().absolute()
    secure_dir(state)
    with open(state / 'pair.lock', 'a+', encoding='ascii') as lock:
        lock.flush()
        os.chmod(lock.name, 0o600)
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        pair(base, state, args.timeout)

if __name__ == '__main__':
    try:
        main()
    except (PairError, OSError) as error:
        print('PAIR_FAILED:', error, file=sys.stderr)
        sys.exit(1)
