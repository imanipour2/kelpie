#!/usr/bin/env python3
"""Read-only Kelpie R5 MCP principal doctor; never prints secrets.

Runs inside the owner's existing Ubuntu/Termux environment. This is not
pairing, not native consent, not a tunnel restart, and not a grant.
"""
import argparse
import json
import re
import stat
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_BASE = "http://127.0.0.1:18461"
DEFAULT_HEADER = "/root/.local/share/kelpie-r5-owner/authorization-header.txt"
MAX_RESPONSE = 65536


class DiagnosticFailure(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise DiagnosticFailure("REDIRECT_REFUSED")


def validated_base(raw):
    parsed = urllib.parse.urlsplit(raw)
    if (parsed.scheme != "http" or parsed.hostname != "127.0.0.1"
            or parsed.port != 18461 or parsed.username or parsed.password
            or parsed.path or parsed.query or parsed.fragment):
        raise DiagnosticFailure("INVALID_LOCAL_ENDPOINT")
    return DEFAULT_BASE


def private_authorization_header(path):
    if path.is_symlink() or not path.is_file():
        raise DiagnosticFailure("AUTH_FILE_MISSING_OR_SYMLINK")
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode & 0o077:
        raise DiagnosticFailure("AUTH_FILE_PERMISSIONS_UNSAFE")
    authorization = path.read_text(encoding="ascii").strip()
    if not re.fullmatch(r"Bearer [0-9a-fA-F]{64}", authorization):
        raise DiagnosticFailure("AUTH_FILE_FORMAT_INVALID")
    return authorization


def parse_reply(raw):
    if len(raw) > MAX_RESPONSE:
        raise DiagnosticFailure("RESPONSE_TOO_LARGE")
    text = raw.decode("utf-8")
    data = []
    for line in text.splitlines():
        if line.startswith("data:"):
            item = line[5:].strip()
            if item and item != "[DONE]":
                data.append(item)
    candidate = data[-1] if data else text
    try:
        parsed = json.loads(candidate)
    except (ValueError, TypeError):
        raise DiagnosticFailure("INVALID_MCP_RESPONSE") from None
    if not isinstance(parsed, dict):
        raise DiagnosticFailure("INVALID_MCP_ENVELOPE")
    return parsed


def mcp(opener, base, authorization, method, request_id, params):
    envelope = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
        "params": params,
    }
    req = urllib.request.Request(
        base + "/mcp",
        data=json.dumps(envelope, separators=(",", ":")).encode("utf-8"),
        headers={
            "Authorization": authorization,
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "Mcp-Protocol-Version": "2025-11-25",
        },
        method="POST",
    )
    try:
        with opener.open(req, timeout=10) as response:
            body = response.read(MAX_RESPONSE + 1)
            return parse_reply(body)
    except urllib.error.HTTPError as error:
        raise DiagnosticFailure("HTTP_" + str(error.code)) from None
    except urllib.error.URLError:
        raise DiagnosticFailure("LOCAL_MCP_UNREACHABLE") from None


def call_result(message):
    if isinstance(message.get("error"), dict):
        raise DiagnosticFailure("JSONRPC_ERROR_" + str(message["error"].get("code", "UNKNOWN")))
    result = message.get("result")
    if not isinstance(result, dict):
        raise DiagnosticFailure("MCP_RESULT_MISSING")
    content = result.get("content", [])
    if not isinstance(content, list) or not content:
        raise DiagnosticFailure("MCP_CONTENT_MISSING")
    first = content[0]
    if not isinstance(first, dict) or not isinstance(first.get("text"), str):
        raise DiagnosticFailure("MCP_TEXT_MISSING")
    try:
        data = json.loads(first["text"])
    except ValueError:
        raise DiagnosticFailure("MCP_CONTENT_NOT_JSON") from None
    if not isinstance(data, dict):
        raise DiagnosticFailure("MCP_CONTENT_NOT_OBJECT")
    return data


def catalog_contract(message):
    result = message.get("result", {})
    if not isinstance(result, dict) or not isinstance(result.get("tools"), list):
        return "TOOLS_LIST_UNAVAILABLE"
    tools = result["tools"]
    names = {tool.get("name") for tool in tools if isinstance(tool, dict)}
    browser = next((item for item in tools if isinstance(item, dict)
                    and item.get("name") == "kelpie_browser_control"), None)
    schema = browser.get("inputSchema", {}) if browser else {}
    values = schema.get("properties", {}).get("method", {}).get("enum", [])
    has_session = {"session.request", "session.status", "session.stop"}.issubset(set(values))
    return ("R5_NATIVE_3_TOOLS_SESSION_ENUM_PRESENT" if
            {"kelpie_browser_control", "kelpie_agent_session", "kelpie_browser_actions"}.issubset(names)
            and has_session else "NATIVE_TOOL_CATALOG_MISMATCH")


def classify_session(data):
    if data.get("success") is True:
        status = data.get("status")
        if status in ("OFF", "PENDING_NATIVE_APPROVAL", "ACTIVE_AGENT_SESSION",
                      "ACTIVE_CONSENT_ONLY", "SUSPENDED_LOCKED", "NOT_FOUND"):
            return "ELIGIBLE_PRINCIPAL_CONFIRMED__" + status
        return "ELIGIBLE_PRINCIPAL_CONFIRMED__UNKNOWN_STATUS"
    error = data.get("error")
    if isinstance(error, dict) and error.get("code") == "PERMISSION_REQUIRED":
        return "PRINCIPAL_NOT_ELIGIBLE"
    return "SESSION_STATUS_FAILED"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--authorization-file", default=DEFAULT_HEADER)
    args = parser.parse_args()
    try:
        base = validated_base(args.base)
        authorization = private_authorization_header(Path(args.authorization_file))
        opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirect()
        )
        catalog = mcp(opener, base, authorization, "tools/list", 51, {})
        print("DIRECT_CATALOG=" + catalog_contract(catalog))
        payload = {"name": "kelpie_browser_control", "arguments": {
            "request": {"version": 1, "method": "session.status", "params": {}}}}
        status_data = call_result(mcp(opener, base, authorization, "tools/call", 52, payload))
        print("LOCAL_SESSION_STATUS=" + classify_session(status_data))
        if classify_session(status_data).startswith("ELIGIBLE_PRINCIPAL_CONFIRMED"):
            metadata = {"name": "kelpie_browser_control", "arguments": {
                "request": {"version": 1, "method": "capabilities", "params": {}}}}
            cap = call_result(mcp(opener, base, authorization, "tools/call", 53, metadata))
            print("LOCAL_CAPABILITIES=" + (
                "RECEIVED" if cap.get("version") == 1 and "nativeSession" in cap
                else "INVALID"))
    except DiagnosticFailure as error:
        # Deliberately do not print request, response, Authorization or file data.
        print("LOCAL_DIAG_ERROR=" + str(error))
        return 1
    except (OSError, UnicodeError, ValueError):
        print("LOCAL_DIAG_ERROR=ENVIRONMENT_OR_FORMAT")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
