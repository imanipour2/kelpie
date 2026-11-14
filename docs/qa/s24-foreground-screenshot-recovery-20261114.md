# S24 Kelpie: foreground screenshot recovery QA (2026-11-14)

Status: **DRAFT / PATCH NOT INSTALLED / NOT PRODUCTION APPROVED**. Owner operational authority: EF-OPS-KELPIE-S24-20261114; tracking: [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98); canonical runbook: [EF-OPS-TOOLS-001](https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit).

## Source and root-cause evidence

- Device: Samsung Galaxy S24 Ultra (SM-S928B; Android API 37); existing isolated QA install `com.kelpie.browser.unifiedverify` version 0.2.5, SHA256 `cac51cda5850be1f561f464c2c7812f650962003a85c3b9e86102030840d4b21`. **Do not confuse with upstream production release.**
- Real ChatGPT `kelpie_browser_control` could navigate, click, type, snapshot and scroll in background, but screenshot returned generic `COMMAND_FAILED`.
- ADB observed `topResumedActivity=com.openai.chatgpt`, `document.visibilityState=hidden`. Promoting Kelpie with approved `adb shell am start -n com.kelpie.browser.unifiedverify/com.kelpie.browser.MainActivity` led to `visible` and successful PNG screenshot. Repeat HOME → failure → foreground → success.
- `WebViewHardwareCapture.kt` explicitly requires an attached, shown, visible WebView and copies from `Activity.window` via PixelCopy. `HandlerContext.hasVisibleDocument()` checks `visibilityState == visible` and non-zero viewport. This intentionally prevents blank/stale screenshots.
- `KelpieNetworkService.kt` already holds an ongoing notification with `PendingIntent.getActivity()`, `FLAG_ACTIVITY_SINGLE_TOP | CLEAR_TOP`. User tapping the notification is the least-privilege recovery path; no new service or arbitrary shell API is required.
- In `DeviceHandler.kt`, `get-device-info` and `get-viewport` returned raw maps rather than the standard `successResponse()`. This is a likely cause of otherwise unexplained extension `COMMAND_FAILED`, distinct from screenshot visibility.
- Reference docs: [Android PixelCopy](https://developer.android.com/reference/android/view/PixelCopy), [Android BAL restrictions](https://developer.android.com/guide/components/activities/secure-bal), [Notification contentIntent](https://developer.android.com/reference/android/app/Notification.Builder#setContentIntent(android.app.PendingIntent)). Do not claim an exact PixelCopy failure code without logcat.

## Proposed code change

- Return a clear `FOREGROUND_REQUIRED` error for any screenshot mode when the visible renderer gate fails; do not request PixelCopy on hidden content.
- Make existing notification recovery action clearer, retaining its already safe user-mediated PendingIntent.
- Repair device information / viewport result envelopes.
- Do **not** automatically start background activities, bypass lock state, export cookies/tokens, modify tunnel config, add permissions, or overwrite the installed APK without a verified compatible signature.

## Acceptance matrix (not yet passed for patch)

| Case | Setup | Expected |
|---|---|---|
| A1 | Kelpie foreground, valid public test page | PNG screenshot succeeds and pixels match |
| A2 | Home / ChatGPT foreground, Kelpie hidden | Deterministic foreground-required error; no stale image |
| A3 | Tap Kelpie's ongoing notification | Existing Activity resumes; tabs retained; screenshot succeeds |
| A4 | User dismisses/switches tabs | No unintended tab mutation; control preserved |
| A5 | Device-info and viewport extension | Standard success envelope, useful readback |
| A6 | Android 16 KiB page emulator | APK installs, launches and `/health=200` |
| A7 | Lock/unlock, Doze, Wi-Fi/cellular | No silent unlock, no unexpected auto-foreground; controlled recovery |
| A8 | Security regression | No credentials in logs; no added IPC permission or arbitrary shell |
| A9 | Build and automated checks | ktlint, unit tests, Android lint, assembleDebug, native alignment PASS |

## Verification and rollback

Build/CI and hardware testing must use this feature branch/PR, not `main`. If CI or device checks fail, keep the known 0.2.5 QA APK. Observe package signature compatibility before attempted in-place update. Never uninstall the sole working package just to try a non-production build. Production-signing, browser role eligibility, and 24-hour unattended recovery are separate release gates.

Website status: `LIVE/STAGING UNCHANGED`. GitHub commit/merge is **not** website deployment authorization.
