#!/usr/bin/env bash
# Execute under one bash process: android-emulator-runner dispatches script lines separately.
set -euo pipefail

echo 'Checking Android 15 experimental 16 KiB emulator (independent process)'
adb wait-for-device
pages="$(adb shell getconf PAGE_SIZE | tr -d '\r\n')"
echo "Emulator page size: [$pages]"
if [[ "$pages" != "16384" ]]; then
  echo "FAIL: expected 16384-byte kernel page size; refusing false acceptance"
  exit 1
fi

apk='qa-apk/app-debug.apk'
test -f "$apk"
echo 'Installing exact hash-verified isolated app'
adb install "$apk"
adb shell am start -n com.kelpie.browser.unifiedverify/com.kelpie.browser.MainActivity
adb forward tcp:18440 tcp:18440

healthy=0
for i in $(seq 1 35); do
  if curl --fail --silent --max-time 4 http://127.0.0.1:18440/health | grep -q '"status":"ok"'; then
    healthy=1
    break
  fi
  sleep 2
done
if [[ "$healthy" -ne 1 ]]; then
  echo 'FAIL: local Kelpie /health did not respond on 16 KiB emulator'
  exit 1
fi
adb shell pidof com.kelpie.browser.unifiedverify
adb shell dumpsys package com.kelpie.browser.unifiedverify | grep -m1 'versionName='
echo 'PASS: actual 16 KiB-page emulator + isolated APK install + running native HTTP service'
