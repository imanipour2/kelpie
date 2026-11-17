#!/usr/bin/env python3
"""Fail-closed S24 R5 USB readiness/readback; never grants consent or sends input.

This script intentionally has NO UI tap, text, swipe, keyevent, permission grant,
Termux command, screenshot persistence, account login or website mutation path.
Python 3.10+, standard library only; no access to owner secrets.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.request

EXPECTED_PACKAGE = "com.kelpie.browser.r5fullverify"
UPSTREAM_PACKAGE = "com.kelpie.browser"
FORK_SOURCE_SHA = "553a7bc152c50a208b53d80b3b0b15cc1d8d0a2b"
ADB_PORT = 18460
FIXTURE_PORT = 18080


def command(exe: pathlib.Path, serial: str, *args: str, timeout: int = 18) -> str:
    result = subprocess.run(
        [str(exe), "-s", serial, *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        errors="replace",
        timeout=timeout,
        check=False,
    )
    if result.returncode:
        raise RuntimeError(f"ADB_COMMAND_FAILED:{args[0]}:{result.returncode}")
    return result.stdout


def api_json(port: int, path: str) -> tuple[int, dict]:
    request = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}",
        data=b"{}",
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=7) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=pathlib.Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--connect", action="store_true", help="Restore USB port forwarding; no app UI input")
    parser.add_argument("--start-app", action="store_true", help="Explicit package-scoped am start only")
    parser.add_argument("--apk", type=pathlib.Path, help="Optional locally verified QA APK path")
    parser.add_argument("--output", type=pathlib.Path, help="Optional metadata-only JSON report file")
    args = parser.parse_args(argv)

    if not re.fullmatch(r"[A-Za-z0-9]{6,24}", args.serial):
        parser.error("Invalid ADB serial")
    if not args.adb.is_file():
        parser.error("ADB executable missing")

    report: dict = {
        "recorded_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "purpose": "private R5 Android readiness without app/input side effects",
        "expected_package": EXPECTED_PACKAGE,
        "source_basis_sha": FORK_SOURCE_SHA,
        "adb_serial_suffix": args.serial[-4:],
    }
    model = command(args.adb, args.serial, "shell", "getprop", "ro.product.model").strip()
    api_level = command(args.adb, args.serial, "shell", "getprop", "ro.build.version.sdk").strip()
    state = command(args.adb, args.serial, "get-state").strip()
    report.update(device_model=model, android_api=api_level, adb_state=state)
    if model != "SM-S928B" or api_level != "37" or state != "device":
        raise RuntimeError("UNVERIFIED_DEVICE_IDENTITY")

    installed = command(args.adb, args.serial, "shell", "pm", "list", "packages", "--user", "0", "kelpie")
    packages = sorted(
        x.removeprefix("package:") for x in installed.splitlines() if x.startswith("package:")
    )
    report["owner_profile_packages"] = packages
    if EXPECTED_PACKAGE not in packages:
        raise RuntimeError("EXPECTED_R5_PACKAGE_MISSING")
    extra = [name for name in packages if name not in {EXPECTED_PACKAGE, UPSTREAM_PACKAGE}]
    report["unwanted_qa_packages"] = extra
    if extra:
        raise RuntimeError("MULTIPLE_QA_PACKAGES_DETECTED")

    info = command(args.adb, args.serial, "shell", "dumpsys", "package", EXPECTED_PACKAGE)
    def first_value(pattern: str) -> str | None:
        match = re.search(pattern, info)
        return match.group(1) if match else None

    report["version_name"] = first_value(r"versionName=(\S+)")
    report["version_code"] = first_value(r"versionCode=(\d+)")
    reports = re.findall(r"android.permission.POST_NOTIFICATIONS: granted=(true|false)", info)
    report["owner_notification_permission"] = reports[0] if reports else "UNKNOWN"
    if report["version_code"] != "33":
        raise RuntimeError("UNEXPECTED_R5_INSTALL_VERSION")

    if args.apk:
        if not args.apk.is_file():
            raise RuntimeError("PINNED_APK_NOT_FOUND")
        digest = hashlib.sha256()
        with args.apk.open("rb") as stream:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                digest.update(chunk)
        report["local_apk_sha256"] = digest.hexdigest()

    if args.connect:
        command(args.adb, args.serial, "forward", f"tcp:{ADB_PORT}", f"tcp:{ADB_PORT}")
        command(args.adb, args.serial, "reverse", f"tcp:{FIXTURE_PORT}", f"tcp:{FIXTURE_PORT}")
    report["forward_present"] = f"tcp:{ADB_PORT}" in command(args.adb, args.serial, "forward", "--list")
    report["reverse_present"] = f"tcp:{FIXTURE_PORT}" in command(args.adb, args.serial, "reverse", "--list")

    if args.start_app:
        command(
            args.adb, args.serial, "shell", "am", "start", "-n",
            EXPECTED_PACKAGE + "/com.kelpie.browser.MainActivity",
        )

    try:
        code, status = api_json(ADB_PORT, "/v1/agent-session-status")
        report["r5_status_http"] = code
        report["r5_session"] = status.get("status", "UNKNOWN")
        report["r5_ready"] = status.get("browserOperationsEnabled", False)
    except (urllib.error.URLError, OSError, ValueError):
        report["r5_session"] = "API_UNREACHABLE"

    # Do not include consent generation, pairing data, URL, cookies, DOM, PNG
    # contents, auth principal, tokens or locally connected owner secrets.
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["r5_session"] != "API_UNREACHABLE" else 2


if __name__ == "__main__":
    sys.exit(main())
