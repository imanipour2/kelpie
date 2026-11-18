"""Offline, fixture-only tests for r5-mcp-principal-doctor.py."""
import importlib.util
import io
import json
import pathlib
import stat
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

SRC = pathlib.Path(__file__).resolve().parents[1] / "r5-mcp-principal-doctor.py"
SPEC = importlib.util.spec_from_file_location("r5_principal_doctor", SRC)
doctor = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(doctor)


class DoctorContractTests(unittest.TestCase):
    def test_endpoint_locked_to_owner_loopback_port(self):
        self.assertEqual(doctor.validated_base("http://127.0.0.1:18461"), doctor.DEFAULT_BASE)
        for unsafe in ("http://0.0.0.0:18461", "https://127.0.0.1:18461",
                       "http://127.0.0.1:18460", "http://127.0.0.1:18461/path",
                       "http://user@127.0.0.1:18461"):
            with self.subTest(unsafe=unsafe), self.assertRaises(doctor.DiagnosticFailure):
                doctor.validated_base(unsafe)

    def test_private_file_rejects_public_mode_and_never_echoes_bearer(self):
        secret = "Bearer " + "a" * 64
        with tempfile.TemporaryDirectory() as work:
            file = pathlib.Path(work) / "auth.txt"
            file.write_text(secret, encoding="ascii")
            file.chmod(0o644)
            with self.assertRaisesRegex(doctor.DiagnosticFailure, "PERMISSIONS_UNSAFE"):
                doctor.private_authorization_header(file)
            file.chmod(0o600)
            self.assertEqual(doctor.private_authorization_header(file), secret)
            link = pathlib.Path(work) / "link.txt"
            link.symlink_to(file)
            with self.assertRaisesRegex(doctor.DiagnosticFailure, "SYMLINK"):
                doctor.private_authorization_header(link)

    def test_json_and_sse_frame_parsing(self):
        item = {"jsonrpc": "2.0", "id": 1, "result": {"content": []}}
        self.assertEqual(doctor.parse_reply(json.dumps(item).encode()), item)
        self.assertEqual(doctor.parse_reply(
            ("event: message\ndata: " + json.dumps(item) + "\n\n").encode()), item)

    def test_session_validity_not_inferred_from_metadata(self):
        self.assertEqual(doctor.classify_session({"success": True, "status": "OFF"}),
                         "ELIGIBLE_PRINCIPAL_CONFIRMED__OFF")
        self.assertEqual(doctor.classify_session({"success": False, "error": {
            "code": "PERMISSION_REQUIRED"}}), "PRINCIPAL_NOT_ELIGIBLE")
        self.assertEqual(doctor.classify_session({"version": 1, "nativeSession": "READY"}),
                         "SESSION_STATUS_FAILED")

    def test_renderer_readiness_separate_from_native_consent(self):
        self.assertEqual(doctor.renderer_class({"success": True, "status": "ACTIVE_AGENT_SESSION",
            "browserOperationsEnabled": False}), "DISABLED")
        self.assertEqual(doctor.renderer_class({"success": True, "status": "ACTIVE_AGENT_SESSION",
            "browserOperationsEnabled": True}), "ENABLED")
        self.assertEqual(doctor.capabilities_class({"nativeSession": "OWNER_CONSENT_OR_RENDERER_REQUIRED",
            "consentGeneration": None}), "NOT_READY")
        self.assertEqual(doctor.capabilities_class({"nativeSession": "READY",
            "consentGeneration": "unprinted-secret"}), "READY_GENERATION_PRESENT")

    def test_catalog_reports_missing_session_schema(self):
        schema = {"name": "kelpie_browser_control", "inputSchema": {
            "properties": {"method": {"enum": [
                "capabilities", "session.request", "session.status", "session.stop"]}}}}
        base = [schema, {"name": "kelpie_browser_actions"}, {"name": "kelpie_agent_session"}]
        self.assertEqual(doctor.catalog_contract({"result": {"tools": base}}),
                         "R5_NATIVE_3_TOOLS_SESSION_ENUM_PRESENT")
        base[0]["inputSchema"]["properties"]["method"]["enum"].remove("session.status")
        self.assertEqual(doctor.catalog_contract({"result": {"tools": base}}),
                         "NATIVE_TOOL_CATALOG_MISMATCH")

    def test_active_status_is_not_renderer_readiness(self):
        self.assertEqual(doctor.renderer_class({
            "success": True, "status": "ACTIVE_AGENT_SESSION",
            "browserOperationsEnabled": True}), "ENABLED")
        self.assertEqual(doctor.renderer_class({
            "success": True, "status": "ACTIVE_AGENT_SESSION",
            "browserOperationsEnabled": False}), "DISABLED")
        self.assertEqual(doctor.renderer_class({
            "success": True, "status": "ACTIVE_AGENT_SESSION"}), "UNKNOWN")
        self.assertEqual(doctor.renderer_class({
            "success": True, "status": "OFF"}), "NOT_EVALUABLE")

    def test_remote_metadata_readiness_does_not_print_generation(self):
        self.assertEqual(doctor.capabilities_class({
            "nativeSession": "READY", "consentGeneration": "private-secret-generation"}),
            "READY_GENERATION_PRESENT")
        self.assertEqual(doctor.capabilities_class({
            "nativeSession": "OWNER_CONSENT_OR_RENDERER_REQUIRED",
            "consentGeneration": None}), "NOT_READY")
        self.assertEqual(doctor.capabilities_class({
            "nativeSession": "READY", "consentGeneration": None}),
            "UNEXPECTED_OR_MALFORMED")

    def test_main_prints_only_redacted_classes(self):
        with tempfile.TemporaryDirectory() as work:
            path = pathlib.Path(work) / "auth.txt"
            secret = "Bearer " + "f" * 64
            path.write_text(secret)
            path.chmod(stat.S_IRUSR | stat.S_IWUSR)
            fake_catalog = {"result": {"tools": [{"name": "kelpie_browser_control",
                            "inputSchema": {"properties": {"method": {"enum": []}}}}]}}
            status = {"result": {"content": [{"type": "text", "text":
                        json.dumps({"success": False, "error": {"code": "PERMISSION_REQUIRED"}})}]}}
            out = io.StringIO()
            with patch("sys.argv", ["doctor", "--authorization-file", str(path)]), \
                 patch.object(doctor, "mcp", side_effect=[fake_catalog, status]), \
                 redirect_stdout(out):
                result = doctor.main()
            self.assertEqual(result, 0)
            self.assertIn("LOCAL_SESSION_STATUS=PRINCIPAL_NOT_ELIGIBLE", out.getvalue())
            self.assertNotIn(secret, out.getvalue())
            self.assertNotIn("f" * 64, out.getvalue())


if __name__ == "__main__":
    unittest.main()
