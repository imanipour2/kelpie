#!/usr/bin/env python3
"""Owner-consent-safe Android R5 acceptance probes.

Never automates an affirmative native Approve or POST_NOTIFICATIONS grant.
Use only with a local, owner-authorized USB/ADB phone and QA-only package.
No tokens, page DOM, screenshots, or private notification contents are logged.
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

PACKAGE = "com.kelpie.browser.r5sourceverify"
MODEL = "SM-S928B"
PORT = 18457
DENIED_ROUTES = (
    "get-tabs",
    "navigate",
    "click",
    "fill",
    "screenshot",
    "browser-control",
    "mcp",
    "agent-session-approve",
)


class CheckFailure(Exception):
    pass


def adb(args, *, timeout=25):
    result = subprocess.run(
        [CONFIG.adb, "-s", CONFIG.serial, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise CheckFailure(
            f"ADB {' '.join(args[:3])} failed, rc={result.returncode}: "
            f"{result.stderr[:120]}"
        )
    return result.stdout


def http(route, method="POST", payload=None):
    data = json.dumps(payload if payload is not None else {}).encode("utf-8")
    req = urllib.request.Request(
        f"http://127.0.0.1:{CONFIG.port}{route}",
        method=method,
        data=data if method == "POST" else None,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=6) as response:
            return response.status, json.loads(response.read(4096))
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read(4096))


def report(name, result, detail):
    print(json.dumps({"case": name, "result": result, "detail": detail}, ensure_ascii=False))
    if result == "FAIL":
        FAILURES.append(name)


def expect(name, condition, success, failure):
    report(name, "PASS" if condition else "FAIL", success if condition else failure)


def status():
    code, body = http("/v1/agent-session-status")
    if code != 200 or body.get("success") is not True:
        raise CheckFailure("Authenticated localhost owner status unavailable")
    return body.get("status"), body


def ensure_off():
    state, _ = status()
    if state != "OFF":
        raise CheckFailure(f"Expected OFF before probe, actual {state}; never revoke an owner grant")
    return state


def foreground():
    adb(["shell", "am", "start", "-n", f"{CONFIG.package}/com.kelpie.browser.MainActivity"])
    time.sleep(0.6)


def preflight():
    devices = subprocess.run(
        [CONFIG.adb, "devices", "-l"],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15,
    )
    if devices.returncode != 0 or len([
        line for line in devices.stdout.splitlines() if "\tdevice" in line
    ]) != 1:
        raise CheckFailure("Exactly one authorized ADB device required")
    if CONFIG.serial not in devices.stdout:
        raise CheckFailure("Specified ADB device not attached")
    model = adb(["shell", "getprop", "ro.product.model"]).strip()
    expect("device-model", model.replace(" ", "-") == MODEL, model, f"Unexpected {model}")
    pkglist = adb(["shell", "pm", "list", "packages", "--user", "0", CONFIG.package])
    expect("r5-package-present", f"package:{CONFIG.package}" in pkglist, "Installed", "Missing")
    other = adb(["shell", "pm", "list", "packages", "--user", "0", "com.kelpie.browser"])
    expect("no-p4-package", "package:com.kelpie.browser.pcverify" not in other, "P4 absent", "P4 still installed")
    version = adb(["shell", "dumpsys", "package", CONFIG.package])
    expect(
        "r5-version", "versionName=0.2.11-r5-consent-qa" in version,
        "Expected QA variant version", "Version mismatch",
    )
    perm_line = next((line.strip() for line in version.splitlines()
                      if "android.permission.POST_NOTIFICATIONS:" in line), "Not found")
    report("post-notification-permission", "INFO", perm_line[:180])
    forwards = adb(["forward", "--list"])
    host_port = f"tcp:{CONFIG.port}"
    if any(host_port in line and CONFIG.serial not in line for line in forwards.splitlines()):
        raise CheckFailure("Host port already forwarded to another Android device")
    adb(["forward", host_port, f"tcp:{CONFIG.device_port}"])
    code, body = http("/health", method="GET")
    expect("health", code == 200 and body.get("status") == "ok", "HTTP 200", f"HTTP {code}")
    state, _ = status()
    report("owner-session-state", "INFO", state)
    return state


def negative():
    ensure_off()
    for route in DENIED_ROUTES:
        code, body = http(f"/v1/{route}", payload={"approved": True, "bypassRecordingGate": True})
        error = body.get("error", {})
        expect(
            f"deny-{route}",
            code == 403 and error.get("code") == "PERMISSION_REQUIRED",
            "403 PERMISSION_REQUIRED", f"HTTP {code}, error {error.get('code')}",
        )
    final, _ = status()
    expect("no-remote-escalation", final == "OFF", "OFF", f"Unexpected state {final}")


def pending_stop():
    ensure_off()
    foreground()
    code, result = http("/v1/agent-session-request")
    if code == 400 and result.get("error", {}).get("code") == "USER_TAP_REQUIRED":
        report("request-focus", "BLOCKED", "Fail-closed: device not unlocked/focused")
        return
    expect("request-pending", code == 200 and result.get("status") == "PENDING_NATIVE_APPROVAL",
           "Native prompt pending", f"HTTP {code}, status {result.get('status')}")
    if code != 200 or result.get("status") != "PENDING_NATIVE_APPROVAL":
        return
    try:
        pending, _ = status()
        expect("pending-visible", pending == "PENDING_NATIVE_APPROVAL",
               "PENDING_NATIVE_APPROVAL", f"Actual {pending}")
    finally:
        stop_code, stop_body = http("/v1/agent-session-stop")
        expect("owner-stop", stop_code == 200 and stop_body.get("status") == "STOPPED",
               "STOPPED", f"HTTP {stop_code}")
        state, _ = status()
        expect("stop-revoked", state == "OFF", "OFF", f"Actual {state}")


def stress_pending():
    ensure_off()
    foreground()
    completed = 0
    safe_reject = 0
    anomalies = 0
    for _ in range(CONFIG.cycles):
        state, _ = status()
        if state != "OFF":
            anomalies += 1
            break
        code, request = http("/v1/agent-session-request")
        if code == 400 and request.get("error", {}).get("code") == "USER_TAP_REQUIRED":
            safe_reject += 1
            continue
        if code != 200 or request.get("status") != "PENDING_NATIVE_APPROVAL":
            anomalies += 1
            break
        code, result = http("/v1/agent-session-stop")
        after, _ = status()
        if code == 200 and result.get("status") == "STOPPED" and after == "OFF":
            completed += 1
        else:
            anomalies += 1
            break
    report("stress-pending-stats", "INFO", {
        "requested": CONFIG.cycles, "complete": completed,
        "safe_focus_denials": safe_reject, "anomalies": anomalies,
        "not_granted_consent_cycles": True,
    })
    expect("stress-no-unauthorized-activation",
           anomalies == 0 and status()[0] == "OFF",
           "All cycles ended OFF", "Unexpected session state or response")


def owner_approved():
    # Read-only: actual Android notification permission and native approval MUST
    # have been performed by the owner, never by ADB or this script.
    state, body = status()
    expect("owner-native-approval", state == "ACTIVE_CONSENT_ONLY",
           "ACTIVE_CONSENT_ONLY", f"Actual {state}")
    if state != "ACTIVE_CONSENT_ONLY":
        return
    expect("agent-browser-actions-still-disabled", body.get("browserOperationsEnabled") is False,
           "No browser operations in consent-only QA", "Unexpected browser capability")
    output = adb(["shell", "dumpsys", "notification"])
    # Do not print notification content (other apps contain sensitive data).
    has_channel = "kelpie_r5_agent_qa" in output
    has_record = any("com.kelpie.browser.r5sourceverify" in x and
                     "NotificationRecord(" in x for x in output.splitlines())
    expect("stop-notification-channel", has_channel, "STOP channel exists", "STOP channel missing")
    expect("stop-notification-present", has_record, "R5 notification present", "No R5 record")


def owner_stopped():
    state, _ = status()
    expect("owner-stop-readback", state == "OFF", "OFF", f"Actual {state}")


def background_negative():
    ensure_off()
    foreground()
    adb(["shell", "input", "keyevent", "3"])
    time.sleep(0.4)
    try:
        code, body = http("/v1/agent-session-request")
        expect("home-request-fail-closed",
               code == 400 and body.get("error", {}).get("code") == "USER_TAP_REQUIRED",
               "400 USER_TAP_REQUIRED", f"HTTP {code}")
    finally:
        foreground()
        state, _ = status()
        if state == "PENDING_NATIVE_APPROVAL":
            http("/v1/agent-session-stop")
    expect("home-no-active", status()[0] == "OFF", "OFF", "Unexpected active session")


def lock_negative():
    ensure_off()
    adb(["shell", "input", "keyevent", "223"])  # KEYCODE_SLEEP; NEVER unlocks.
    time.sleep(0.6)
    try:
        code, body = http("/v1/agent-session-request")
        expect("locked-request-denied",
               code == 400 and body.get("error", {}).get("code") == "USER_TAP_REQUIRED",
               "400 USER_TAP_REQUIRED", f"HTTP {code}")
    finally:
        adb(["shell", "input", "keyevent", "224"])  # Wake, NOT unlock.
    expect("lock-no-active", status()[0] == "OFF", "OFF", "Unexpected active session")


def restart_off():
    ensure_off()
    adb(["shell", "am", "force-stop", CONFIG.package])
    foreground()
    code, health = http("/health", method="GET")
    expect("relaunch-health", code == 200 and health.get("status") == "ok",
           "HTTP 200", f"HTTP {code}")
    expect("relaunch-fresh-off", status()[0] == "OFF", "OFF", "Unexpected grant after restart")


def main():
    global CONFIG, FAILURES
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb", help="Absolute path to platform-tools adb")
    parser.add_argument("--serial", required=True, help="Authorized S24 ADB serial; not printed")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--port", type=int, default=PORT)
    parser.add_argument("--device-port", type=int, default=PORT)
    parser.add_argument("--cycles", type=int, default=50)
    parser.add_argument(
        "--phase", default="preflight",
        choices=["preflight", "negative", "pending-stop", "stress-pending",
                 "owner-approved", "owner-stopped", "home-negative", "lock-negative",
                 "restart-off"],
    )
    CONFIG = parser.parse_args()
    FAILURES = []
    if not (1 <= CONFIG.cycles <= 100):
        parser.error("cycles must be between 1 and 100")
    if not CONFIG.package.startswith("com.kelpie.browser.r5"):
        parser.error("QA R5 package required")
    try:
        preflight()
        if CONFIG.phase == "negative":
            negative()
        elif CONFIG.phase == "pending-stop":
            pending_stop()
        elif CONFIG.phase == "stress-pending":
            stress_pending()
        elif CONFIG.phase == "owner-approved":
            owner_approved()
        elif CONFIG.phase == "owner-stopped":
            owner_stopped()
        elif CONFIG.phase == "home-negative":
            background_negative()
        elif CONFIG.phase == "lock-negative":
            lock_negative()
        elif CONFIG.phase == "restart-off":
            restart_off()
    except (CheckFailure, OSError, ValueError, TimeoutError, urllib.error.URLError) as error:
        report("harness", "FAIL", str(error)[:180])
    sys.exit(1 if FAILURES else 0)


if __name__ == "__main__":
    main()
