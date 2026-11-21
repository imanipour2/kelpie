# Kelpie R5 code45 — phone-only renderer diagnostics through Termux

Date: 2026-11-21. Status: READ-ONLY PLAYBOOK / DEVICE LOG PENDING / NOT A DEVICE-TEST RESULT.
Scope: Samsung Galaxy S24 Ultra, Android 17, installed 0.2.25-r5-connection build45, frozen mother pairing/trust and port 18463/8080. No Android app build/merge/install or authorisation changes.

## Reproduction observed through authenticated ChatGPT Kelpie connector

1. User foregrounded the existing Kelpie app, then used HOME and returned to ChatGPT. First `capabilities`, `kelpie_get_device_info`, `tabs.list` (four tabs), `kelpie_get_viewport` (1080 x 1920, DPR 2.8125) all succeeded.
2. First subsequent read-only `tab.snapshot` on a pre-existing inactive tab returned UNAVAILABLE / RENDERER_NOT_READY (mayHaveCompleted=false).
3. Immediate read-only `tab.snapshot` on the active tab, `tabs.list` and `kelpie_get_viewport` also returned UNAVAILABLE / RENDERER_NOT_READY (mayHaveCompleted=false).
4. This sequence establishes a short recovery followed by loss of renderer readiness; it does **not** establish that snapshot triggered the failure, and does **not** prove which physical host, native/CSS readiness, renderer or lifecycle gate first failed. The remote error says computer approval is unchanged.

## Phone-only procedure — user action required

Use **Termux's native shell**, not the Ubuntu/PRoot tunnel shell. There is no direct ADB/Termux shell exposed to the ChatGPT Kelpie connector. Run these only after the device owner authorises local Android wireless debugging.

### A. Check existing local ADB first (no installation or pairing if already connected)

```sh
command -v adb || echo ADB_NOT_INSTALLED
adb devices -l
```

If adb is absent, the owner may install Termux's normal CLI package: `pkg install android-tools`. This is *not* a Kelpie APK install or tunnel change. If an existing local device is shown as `device`, **do not pair again**.

### B. Wireless Debugging — only if ADB is not connected

On Samsung Settings > Developer options > Wireless debugging, enable on the owner's chosen trusted Wi-Fi network. With Termux and Settings in split-screen, select **Pair device with pairing code**. From the pairing dialog, read its **temporary pairing IP:PORT**, then in Termux:

```sh
adb pair PHONE_IP:PAIR_PORT
```

Enter the displayed six-digit pairing code into Termux only. **Never paste the pairing code or address into ChatGPT/GitHub.** After pairing, return to the parent Wireless debugging screen and use its distinct **IP address & Port** (the device-debugging connection port, NOT the temporary pairing port):

```sh
adb connect PHONE_IP:DEBUG_PORT
adb devices -l
```

The wireless debugging port is dynamically assigned by Android and is unrelated to Kelpie 18463 or tunnel 8080. Keep the owner's existing Kelpie Ed25519 pairing untouched. If pairing fails, stop and report only the error code without IP or PIN; no password-reset, `adb tcpip`, privileged setting changes or VPN/port mutations.

### C. Capture a scoped, read-only native lifecycle trace

Once `adb devices -l` shows the phone as `device`: open Kelpie normally, then press HOME and return to Termux. Without clearing logcat, read only known Kelpie structural tags:

```sh
adb shell logcat -d -v threadtime -t 4000 \
  -s KelpieR5Lifecycle:I KelpieR5Transfer:I KelpieR5Host:I \
     KelpieR5Viewport:I KelpieR5Restore:I KelpieR5Recovery:W '*:S' \
  | grep 'KelpieR5' | tail -n 80
```

These tags were inspected at code45 source in `R5AgentCoordinator.kt`, `R5PrivateViewportReadiness.kt`, and `R5PrivateHostSnapshot.kt`; messages concern epoch, readiness, host geometry and constant failure reasons rather than page contents. Nevertheless, **review locally before sharing**, redact any unexpected URL, tab title, user data, IP address, token, pairing code, personal content or identifiers. Do not upload raw `bugreport.zip`, unrestricted `logcat`, process environment, images, credentials or entire browser state.

If the filtered output is empty, report `NO_STRUCTURAL_LOG_LINES`, do not infer a healthy renderer. Reproduce one more HOME cycle while connected, then rerun the same read-only command. Do not clear the system log buffer.

### D. Diagnosis decision keyed to the first actual failure

| Local event | Technical boundary to investigate on PR75 (not an automatic fix) |
| --- | --- |
| CSS_GATE_NOT_ATTACHED, CSS_GATE_ZERO_NATIVE_SIZE, CSS_CALLBACK_TIMEOUT, CSS_GEOMETRY_REJECTED | Genuine WebView attachment/native size and CSS evaluation before/after transfer |
| PRIVATE_HOST_NOT_ATTACHED, PRIVATE_HOST_WINDOW_NOT_DRAWABLE, PRIVATE_HOST_DISPLAY_MISMATCH, PRIVATE_SURFACE_INVALID | Private Presentation/VirtualDisplay/ImageReader host |
| VIEWPORT_LAYOUT_TIMEOUT, VISUAL_STATE_CALLBACK_TIMEOUT, RESTORE_VERIFY_FAILED | Visual draw scheduling and reconciler/epoch handling |
| pause_commit_background ... ready=false after private_start | Reconcile ownership/current generation, physical private host and resume-before-transfer ordering |
| runtime suspended after PRIVATE_RENDERER_TRANSFER_FAILED | Follow preceding lines to the FIRST failure; this line alone is not root cause |

Keep separate the source-confirmed **fixture-only screenshot privacy** blocker (ordinary page frame export) from actual renderer-readiness and transport success. A successful `tabs.list` is not image proof. Capture cannot be enabled by removing privacy checks.

## Safe subsequent gate

With a redacted first failure, write a narrowly-scoped PR75 source test and minimal native lifecycle fix, preserving code45 signature, port, trusted computer approval, STOP/revoke/keyguard and privacy. Then CI and real S24 hardware test. No main merge or new APK from this playbook.

References: https://developer.android.com/tools/adb ; https://developer.android.com/studio/debug/dev-options ; https://github.com/imanipour2/kelpie/pull/75 ; https://github.com/imanipour2/enerfoam-website/issues/100 .
