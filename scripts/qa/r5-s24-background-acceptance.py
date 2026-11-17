#!/usr/bin/env python3
"""Owner-safe laptop/USB QA for isolated Kelpie R5 background-agent APK.

No automatic native approval, accessibility clicks, unlocking, installation,
remote STOP, or reading another app. Never log token/DOM/screenshot bytes.
Run --phase package now; hardware phases require an authorized USB device.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

PACKAGE = "com.kelpie.browser.r5fullverify"
VERSION = "0.2.13-r5-agent-qa"
PORT = 18460
USER0 = "0"
DENIED = (
    "get-tabs", "navigate", "click", "fill", "screenshot", "browser-control",
    "agent-runtime-status", "agent-tabs", "agent-command",
)


class PreflightError(Exception):
    pass


def emit(name, passed, detail):
    print(json.dumps({"case": name, "result": "PASS" if passed else "FAIL",
                      "detail": detail}, ensure_ascii=False))
    if not passed:
        raise PreflightError(name)


def cmd(argv, timeout=30, env=None):
    p = subprocess.run(argv, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout,
                       env=env, check=False)
    if p.returncode:
        raise PreflightError(f"{Path(argv[0]).name} failed with exit {p.returncode}")
    return p.stdout


def package_preflight(a):
    apk = Path(a.apk)
    emit("apk-present", apk.is_file(), "Local APK exists")
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    print(json.dumps({"case": "apk-sha256", "sha256": sha, "bytes": apk.stat().st_size}))
    if a.sha256:
        emit("expected-source-apk", sha.lower() == a.sha256.lower(),
             "SHA-256 matches the pinned expected artifact")
    with zipfile.ZipFile(apk) as z:
        members = [n for n in z.namelist() if
                   re.fullmatch(r"lib/(arm64-v8a|x86_64)/[^/]+\.so", n)]
        emit("native-abis", {n.split("/")[1] for n in members} ==
             {"arm64-v8a", "x86_64"}, "Both declared native ABIs present")
        if a.readelf:
            with tempfile.TemporaryDirectory(prefix="r5-qa-") as temp:
                for index, member in enumerate(members):
                    path = Path(temp) / f"native-{index}.so"
                    path.write_bytes(z.read(member))
                    output = cmd([a.readelf, "-lW", str(path)])
                    aligns = [line.split()[-1] for line in output.splitlines()
                              if line.lstrip().startswith("LOAD ")]
                    emit("16k-elf-" + member.split("/")[1],
                         bool(aligns) and all(int(v, 16) >= 16384 for v in aligns),
                         "All ELF LOAD segments >= 0x4000")
    badging = cmd([a.aapt, "dump", "badging", str(apk)])
    identity = next((line for line in badging.splitlines()
                     if line.startswith("package:")), "")
    emit("apk-identity", ("name='" + PACKAGE + "'") in identity
         and "versionCode='33'" in identity
         and ("versionName='" + VERSION + "'") in identity,
         "Exact isolated package, build code and version")
    permissions = cmd([a.aapt, "dump", "permissions", str(apk)])
    emit("no-microphone", "android.permission.RECORD_AUDIO" not in permissions,
         "No RECORD_AUDIO")
    emit("notification-declared",
         "android.permission.POST_NOTIFICATIONS" in permissions,
         "POST_NOTIFICATIONS declared; not silently granted")
    env = os.environ.copy()
    if a.java_home:
        env["JAVA_HOME"] = a.java_home
        env["PATH"] = str(Path(a.java_home) / "bin") + os.pathsep + env.get("PATH", "")
    signature = cmd([a.apksigner, "verify", "--verbose", "--print-certs",
                     str(apk)], env=env)
    emit("signed-debug-apk",
         "Verifies" in signature and
         "Verified using v2 scheme (APK Signature Scheme v2): true" in signature
         and "CN=Android Debug" in signature,
         "APK v2 signature verified; DEBUG-ONLY, not a production release")
    print(json.dumps({"case": "package-gate", "status": "LOCAL_QA_PASS_ONLY",
                      "apk_sha256": sha}))


def adb(a, arguments):
    return cmd([a.adb, "-s", a.serial] + arguments, timeout=20)


def request(a, method, path, payload=None):
    raw = json.dumps(payload if payload is not None else {}).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    req = urllib.request.Request(f"http://127.0.0.1:{a.port}{path}",
                                 data=raw if method == "POST" else None,
                                 headers=headers, method=method)
    # PNG Base64 is substantially larger than a normal JSON status response.
    # Reject unbounded payloads; NEVER parse a truncated success as corrupted JSON.
    max_response_bytes = 20 * 1024 * 1024 if path == "/v1/agent-command" else 64 * 1024
    try:
        with urllib.request.urlopen(req, timeout=18) as response:
            raw_response = response.read(max_response_bytes + 1)
            if len(raw_response) > max_response_bytes:
                raise PreflightError("Native response exceeds bounded QA capture limit")
            return response.status, json.loads(raw_response)
    except urllib.error.HTTPError as error:
        raw_error = error.read(65537)
        if len(raw_error) > 65536:
            raise PreflightError("Native error response exceeds bounded QA limit")
        return error.code, json.loads(raw_error)


def device_preflight(a):
    emit("serial-explicit", bool(a.serial), "Explicit ADB serial was supplied")
    devices = cmd([a.adb, "devices", "-l"], timeout=12)
    authorized = [line.split()[0] for line in devices.splitlines()[1:]
                  if re.search(r"^\S+\s+device(?:\s|$)", line)]
    emit("single-adb-target", authorized == [a.serial],
         "Exactly one authorized matching device")
    model = adb(a, ["shell", "getprop", "ro.product.model"]).strip()
    emit("owner-s24", model.replace(" ", "-") == "SM-S928B",
         "Samsung SM-S928B reported")
    found = adb(a, ["shell", "pm", "list", "packages", "--user", USER0, PACKAGE])
    emit("isolated-r5-installed", f"package:{PACKAGE}" in found,
         "Dedicated R5 QA package present")
    details = adb(a, ["shell", "dumpsys", "package", PACKAGE])
    emit("installed-r5-version",
         "versionName=" + VERSION in details and "versionCode=33" in details,
         "Expected R5 QA version and code")
    forwards = adb(a, ["forward", "--list"])
    if any(f"tcp:{a.port}" in line and a.serial not in line
           for line in forwards.splitlines()):
        raise PreflightError("Local QA port forwarded to a different device")
    adb(a, ["forward", f"tcp:{a.port}", f"tcp:{a.device_port}"])
    code, result = request(a, "GET", "/health")
    emit("local-health", code == 200 and result.get("status") == "ok",
         "HTTP 200 health")
    code, result = request(a, "POST", "/v1/agent-session-status")
    emit("session-status-readable", code == 200 and result.get("success") is True,
         "Authenticated forwarded localhost readback; no session created")
    return result


def negative(a, session):
    emit("consent-off-before-negative", session.get("status") == "OFF",
         "No native session is active")
    for route in DENIED:
        body = {"approved": True, "bypassRecordingGate": True,
                "consentGeneration": "not-a-grant",
                "operation": "READ_DOM", "tabId": "not-real",
                "documentEpoch": "not-real", "clientSequence": 1}
        code, result = request(a, "POST", "/v1/" + route, body)
        emit("denied-" + route,
             code == 403 and result.get("success") is not True,
             "403 without native consent")
    _, again = request(a, "POST", "/v1/agent-session-status")
    emit("no-consent-escalation", again.get("status") == "OFF",
         "Denial tests did not grant a session")


def owner_read_only(a, session):
    emit("human-approval-present",
         session.get("status") == "ACTIVE_AGENT_SESSION"
         and session.get("browserOperationsEnabled") is True,
         "Actual owner-native approved agent session and ready renderer")
    generation = session.get("generation")
    emit("generation-in-session", isinstance(generation, str)
         and len(generation) >= 20, "Native generation used only in memory")
    code, status = request(a, "POST", "/v1/agent-runtime-status",
                           {"consentGeneration": generation})
    emit("agent-status", code == 200 and status.get("success") is True
         and status.get("status") == "READY",
         "Owner-bound runtime READY; no generation logged")
    code, tabs = request(a, "POST", "/v1/agent-tabs",
                         {"consentGeneration": generation})
    emit("agent-tabs", code == 200 and tabs.get("success") is True
         and isinstance(tabs.get("tabs"), list),
         "Only number of tabs checked; no URLs or titles output")
    print(json.dumps({"case": "tabs-count", "count": len(tabs.get("tabs", []))}))
    return generation, tabs


def observe_dom(a, session):
    generation, result = owner_read_only(a, session)
    tab = next((item for item in result["tabs"] if item.get("active")), None)
    emit("active-tab", bool(tab), "Current active tab identified locally")
    payload = {"consentGeneration": generation, "clientSequence": a.sequence,
               "commandId": f"r5-owner-readonly-{a.sequence}",
               "operation": "READ_DOM", "tabId": tab["tabId"],
               "documentEpoch": tab["documentEpoch"]}
    code, receipt = request(a, "POST", "/v1/agent-command", payload)
    emit("verified-dom", code == 200 and receipt.get("success") is True
         and receipt.get("status") == "EXECUTED"
         and receipt.get("code") == "VERIFIED_EFFECT",
         "R5 native command completed; DOM not printed")
    content = (receipt.get("data") or {}).get("snapshotJson")
    emit("nonempty-dom", isinstance(content, str) and bool(content),
         "Nonempty real WebView observation; values not logged")
    print(json.dumps({"case": "redacted-dom-evidence", "bytes": len(content.encode("utf-8")),
                      "sha256": hashlib.sha256(content.encode("utf-8")).hexdigest()}))



def owner_screenshot(a, session):
    generation, result = owner_read_only(a, session)
    tab = next((item for item in result["tabs"] if item.get("active")), None)
    emit("owned-active-webview", bool(tab), "Observed native tab in owner-approved process session")
    command = {
        "consentGeneration": generation,
        "clientSequence": a.sequence,
        "commandId": f"r5-owner-screenshot-{a.sequence}",
        "operation": "SCREENSHOT",
        "tabId": tab["tabId"],
        "documentEpoch": tab["documentEpoch"],
    }
    code, result = request(a, "POST", "/v1/agent-command", command)
    emit("owned-screenshot-verified", code == 200 and result.get("success") is True
         and result.get("status") == "EXECUTED" and result.get("code") == "VERIFIED_EFFECT",
         "R5 exact-tab screenshot executed and effect was independently verified")
    encoded = (result.get("data") or {}).get("image")
    emit("owned-screenshot-png-field", isinstance(encoded, str) and bool(encoded),
         "Expected bounded PNG field exists; image never printed")
    data = base64.b64decode(encoded, validate=True)
    png_header = b"\x89PNG\r\n\x1a\n"
    valid = data[:8] == png_header and len(data) > 24
    width = int.from_bytes(data[16:20], "big") if valid else 0
    height = int.from_bytes(data[20:24], "big") if valid else 0
    emit("owned-screenshot-bounded-image", valid and 1 <= width <= 4096
         and 1 <= height <= 4096 and len(data) <= 12 * 1024 * 1024,
         "Valid PNG signature/dimensions/size, with no cross-app image logging")
    print(json.dumps({"case": "owned-screenshot-evidence", "width": width,
                      "height": height, "bytes": len(data),
                      "sha256": hashlib.sha256(data).hexdigest()}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("package", "preflight", "negative",
                                           "owner-readonly", "read-dom", "owner-screenshot", "owner-stopped"),
                        default="package")
    parser.add_argument("--apk", help="Exact candidate APK; mandatory in package phase")
    parser.add_argument("--sha256", help="Optional independently pinned APK SHA-256")
    parser.add_argument("--aapt", help="Android SDK aapt(.exe) path")
    parser.add_argument("--apksigner", help="Android SDK apksigner(.bat) path")
    parser.add_argument("--readelf", help="NDK llvm-readelf(.exe) for required 16 KB check")
    parser.add_argument("--java-home", help="Local Java 17 home, for APK signature tool")
    parser.add_argument("--adb", default="adb", help="ADB executable path")
    parser.add_argument("--serial", help="Exact owner-provided ADB device serial")
    parser.add_argument("--port", type=int, default=PORT, help="Local TCP forward port")
    parser.add_argument("--device-port", type=int, default=PORT)
    parser.add_argument("--sequence", type=int, default=1,
                        help="Unique monotonic command sequence for one real grant")
    a = parser.parse_args()
    if not (1 <= a.port <= 65535 and 1 <= a.device_port <= 65535):
        parser.error("Invalid port")
    if a.phase == "package":
        if not all((a.apk, a.aapt, a.apksigner, a.readelf)):
            parser.error("package phase requires --apk --aapt --apksigner --readelf")
    elif not a.serial:
        parser.error("device phase requires --serial; no fallback discovery")
    try:
        if a.phase == "package":
            package_preflight(a)
            return
        session = device_preflight(a)
        if a.phase == "negative":
            negative(a, session)
        elif a.phase in ("owner-readonly", "read-dom", "owner-screenshot"):
            if a.phase == "owner-readonly":
                owner_read_only(a, session)
            elif a.phase == "read-dom":
                observe_dom(a, session)
            else:
                owner_screenshot(a, session)
        elif a.phase == "owner-stopped":
            emit("owner-stop-readback", session.get("status") == "OFF",
                 "Owner STOP confirmed OFF without an automated click")
    except (PreflightError, ValueError, OSError, TimeoutError, KeyError,
            zipfile.BadZipFile, urllib.error.URLError, subprocess.TimeoutExpired) as error:
        print(json.dumps({"case": "harness-error", "result": "FAIL",
                          "detail": type(error).__name__ + ": " + str(error)[:140]}))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
