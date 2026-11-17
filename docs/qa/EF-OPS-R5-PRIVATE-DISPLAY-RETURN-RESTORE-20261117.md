# R5 QA — recover app WebView after private-display background browsing

Date: 2026-11-17. Owner Samsung SM-S928B Android 17; **draft code, not an accepted release**. Stacked on PR #45 (MCP wrapper) -> PR #44 (foreground WebView screenshot) -> PR #42.

## Physical reproduced bug

On an explicitly owner-approved native R5 session, six fresh private-display screenshots (1080x1920) after scrolling a known public RTL QA fixture returned `EXECUTED / VERIFIED_EFFECT` with distinct content hashes and `captureSource=PRIVATE_DISPLAY`. Immediately after switching Kelpie from background to foreground, native runtime reported `SUSPENDED_OR_TRANSFER / RESTORING / RESTORE_PENDING`, and then process-scoped consent transitioned to `OFF`. No artificial approval, cross-app screenshot or post-revocation operation occurred.

## Source-level cause / bounded correction

The existing `PrivateWebViewDisplay.start()` saves CSS scroll coordinates **on entry into** the virtual display. R5 may scroll and navigate the exact WebView in the private display. On return, `verifyRestored()` repeatedly forces the *entry* coordinates, so its target can be stale relative to the most recently rendered document. Separately, reattaching WebView to Compose is bounded by only 2 seconds, potentially too short for a real Samsung display transfer. These are grounded code hazards; the exact failed subgate in the previous observed run was not logged and is not retroactively asserted.

For R5 QA only (`useNativeFrameProof = true`) snapshot the latest nonnegative finite CSS `scrollX/Y` from the exact same WebView immediately **before** `stop()` releases the private host. Use it as the restoration target, falling back to the original target if the read is unavailable. Reattach timeout increases to 5 seconds *only for R5*; P4 behavior remains unchanged. The existing 3-observation plus delayed stability check, Android-lock and STOP gates remain strict. Failed restoration still revokes the process session and **never** grants a fallback picture of another app.

New sanitizer-only Android diagnostic tag `KelpieR5Restore` logs one of `NO_VIEW_RESTORED`, `WEBVIEW_ATTACH_TIMEOUT`, `CSS_RESTORED`, `CSS_STABILITY_FAILED` for physical RCA, without URL, content, scroll coordinates or token. New JVM tests check latest-vs-stale preference and invalid/absent-snapshot fail-closed fallback.

## Required physical acceptance (still open)

Owner grants consent with a real tap; observe original tab/document and URL internally without logging their content. HOME and navigate or scroll the safe fixture while R5 is genuinely in background, capture 6 distinct nonce-proven frames, return to *same* Kelpie Activity, observe `CSS_RESTORED` and `READY/FOREGROUND`, compare same tab/document and actual CSS scroll to latest pre-return state. Verify fresh foreground PNG after restore, then repeat with keyguard and an incoming call. Test on Android 17, no-USB endpoint, OFF/STOP and failed restoration; keep production gated until independent review and CI are green.

Operational thread: https://github.com/imanipour2/enerfoam-website/issues/98

## External references (checked during remediation)

- Android Developers, Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle — interrupted activity receives onPause and resumes through onResume; onPause does not reliably allow long-running saving, so the repair runs in a bounded coroutine rather than blocking the Android callback.
- Android Developers, WebView API: https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback) — visual DOM synchronization is conditional on attachment; the separate PR #44 foreground PNG path retains its gate.

## Physical RCA added after first install: Android RTL signed horizontal scroll

The **real** owner S24 RTL fixture readback after HOME/SCROLL reported `window.scrollX = -68.26667022705078`, `scrollY = 640`. Under the first revision, WebView CSS coordinate parser required nonnegative X and Y, so the latest snapshot was dropped; the device returned `CSS_STABILITY_FAILED` and native grant OFF on restore. For RTL, CSSOM horizontal scroll is allowed to be negative. Fix both snapshot and R5 target policy to accept finite negative X, retain `Y >= 0` and all stable restoration/lock/principal checks. No coordinate values, URLs or pixels emitted to runtime logs; only this synthetic QA fixture value belongs in the test record. This is a hardware-proven root cause, not merely a guess.
