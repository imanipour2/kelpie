# ADR — Consented task continuity on an Android-locked Kelpie-owned WebView (research only)

**Mission:** EF-OPS-KELPIE-LOCKED-TASK-20261115  
**Date:** 2026-11-15  
**State:** OWNER REQUESTED INVESTIGATION / DESIGN PROPOSAL / NO LOCKED-MODE PROOF / NO DEPLOYMENT  
**Owner directive:** Canonical CDG `EF-OPS-KELPIE-LOCKED-TASK-20261115` in [Drive CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit).  
**Source baseline:** `2bb8dc5fb710b9dcb3118cc84362af8423758d35`, Draft [#13](https://github.com/imanipour2/kelpie/pull/13) and aggregate CI-only [#14](https://github.com/imanipour2/kelpie/pull/14).  
**Scope:** Samsung Galaxy S24 Ultra / Android 17; own `WebView` only; authenticated paired principal and **affirmative native-device consent before screen-off**. Never imply permission to unlock or capture Android's lock screen, other apps, `FLAG_SECURE`, third-party credentials or system UI.

## 1. Owner goal versus existing implementation

The owner wants ChatGPT to navigate, click, type, scroll, inspect DOM and obtain freshly rendered images of the same Kelpie WebView, including while the **physical display is OFF and Android's keyguard is LOCKED**, after explicit prior user approval. An example task is building a *WordPress draft page* in the user's staging instance.

The current P4 design is incompatible. The `ConsentedCaptureLease` at `core/android/src/main/java/com/kelpie/browser/connect/ConsentedCaptureLease.kt` requires `deviceUnlocked` for each frame and expires in 90s / 20 default frames. `PrivateCaptureCoordinator.kt` checks `isKeyguardLocked` at approval/claim/post-encoding and does not give other browser methods the same capability-bound admission. `PrivateWebViewDisplay.kt` also refuses delivery when locked. The screen-off patch in #13 deliberately calls `stopByUser()` on `ACTION_SCREEN_OFF`; correct for its **current** fail-closed scope, but the opposite behavior from the owner's new **separately consented** mode. Keep this as the legacy safe default.

`PrivateCaptureQaOrigin.kt` admits only exact public HTTPS example.com and forbids nondefault ports/userinfo. It cannot be used on WordPress without a separate reviewed allowlist and environment gate. The native consent dialog in `core/android/src/main/java/com/kelpie/browser/ui/PrivateCaptureConsentDialog.kt` currently discloses a **90s, 20-frame** lease and the user taps `تأیید و شروع` or `لغو`; neither is permission for general locked-task automation.

Furthermore, `KelpieBrowserSession.kt` owns the `ComposeView`, `TabStore`, JS handler context, browser gateway and `PrivateCaptureCoordinator` inside the `Activity`; `close()` destroys tabs and the WebViews. `KelpieNetworkService.kt` owns a foreground HTTP/mDNS stage, but the browser rendering lifecycle is **not** independently service-owned. Stable connectivity to a service does not establish persistent UI rendering across Activity destruction. `HandlerContext.kt` and `InteractionHandler.kt` use the original WebView target and `evaluateJavascript`, while the legacy `ScreenshotHandler.kt` draws into a Bitmap on the UI thread, not P4's fresh nonce-proven private-display capture. Treat those separate paths as a security/control gap requiring a unified gate; the exact extent of bypass must be tested, not assumed.

## 2. Feasibility assessment

An app-owned `VirtualDisplay + Presentation + original WebView + ImageReader` has **already** produced 20/20 fresh pixel-proven hidden frames on the *unlocked* physical S24, and restored the same WebView/document/history/form/CSS scroll (old P4 commit `84ed682...`). A separate physical 90s expiry test at `b8c3a351...` passed. This proves a bounded background renderer while the device is **unlocked**, not rendering through a real keyguard.

Android does not automatically unlock WebView content, exempt JavaScript/timers from Doze, or guarantee remote connectivity during screen-off. Foreground service availability and `PARTIAL_WAKE_LOCK` are distinct from continued HTML compositor output. The latter may still stop, even when code runs. `MediaProjection` is **not** a substitute for an app-private WebView surface and does not authorize the system lock screen or other applications.

A truthful acceptance outcome is **UNKNOWN until a physical Samsung/Android 17 test** under the new consent model. If the platform suspends the compositor under keyguard, report `LOCKED_RENDERER_UNSUPPORTED`, not a cached image. The stable alternative for WordPress content creation is authenticated server-side automation (WordPress/WSP or narrowly permissioned WordPress REST), with Kelpie used for user-approved visual QA while possible; this can progress even if the phone display is locked, but does not imply remote screen pixels will be available.

References:
- [Android DisplayManager and VirtualDisplay](https://developer.android.com/reference/android/hardware/display/DisplayManager)
- [Android WebView](https://developer.android.com/reference/android/webkit/WebView)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Broadcast receiver rules](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
- [WordPress REST Pages](https://developer.wordpress.org/rest-api/reference/pages/)
- [WordPress REST authentication](https://developer.wordpress.org/rest-api/using-the-rest-api/authentication/)

## 3. Proposed staged architecture (NOT implemented or approved for deployment)

### Default mode — unchanged legacy security

Keep `SCREEN_OFF -> REVOKE` as default for every existing private-capture grant. Do not just delete `isLocked()` or modify the screen-off receiver to retain existing grants.

### Distinct optional LOCKED_TASK lease

A different, plainly named native dialog grants **`allowLockedOwnedWebView=true`** only to an already-authenticated paired principal after user-initiated foreground interaction. It must show: paired client identity, exact tab/document, origin and staging domain, action classes (read/visual/click/type/scroll/navigation), concrete finite duration and operation budget, sensitive-action limits, immediate native STOP, lock-screen effects, and inability to see Android lock UI. No silent auto-renew, automatic acceptance, permission inference from JSON, or OS lock bypass.

Persist **no raw bearer tokens, cookies, passwords, site screenshots, private HTML or secret input values**. Keep grant in an app-private in-memory process scope; fail closed on process death. Validate request ID + authenticated paired principal + session ID + exact WebView identity + origin + URL/navigation policy + monotonic expiry + per-action budget on **every** action before and after async WebView work, including screenshot delivery and DOM-derived actions. Prevent a user-switch/other-principal request from invalidating another client's legitimate grant (fix any registry handling ambiguity).

### Durable owner of browser and renderer

Extract the lifecycle of `TabStore`, the same WebView object, permission registry, native gateway, `VirtualDisplay`, and on-screen/background host from `KelpieBrowserSession` (Activity-owned) into a single service-scoped browser controller, with safe Activity attach/detach. Preserve app ownership (no window-over-keyguard display). Move a WebView onto the private display **before** the Activity loses compositor ownership; maintain one host, no clone/new session or reload. Reattach to Compose on return; compare document nonce, cookies-in-memory (not logged), form, history and CSS scroll. A foreground service notification must remain visible; STOP must be user-accessible and revoke before further dispatch.

### Single authorization surface

Do not expose unrestricted `/v1/click`, `/v1/screenshot`, `/v1/fill`, or legacy `browser-control` inside the locked-task process without the common grant. Admission should happen in the **canonical gateway/Router**, then pass an immutable target/lease ticket into `HandlerContext.withBrowserTarget`. A method's effects may outlive its entry check; revalidate immediately before sending an image or claiming success. Do not silently treat synthetic DOM `element.click()` as equivalent to a trusted physical gesture: some flows can reject it. Use screenshot nonce proof, no stale frame fallback, and bounded memory.

### WordPress-specific action plan

Stage A: public `example.com` only, read/snapshot/fresh pixel.  
Stage B: a **local synthetic page** inside the app demonstrating click/type/scroll during real lock with zero real accounts.  
Stage C: allow exactly one owner-approved *test* WordPress host, with recoverable backup, G0/G1 checks, strictly draft-only creation, and read-back through WordPress/WSP. Operations with side effects (Publish, Delete, plugin installation, billing, checkout or credential/role changes) require a new foreground confirmation; existing browser grant alone never authorizes them.  
Stage D: evaluate resilient server-side WordPress drafting plus limited visual preview. A service-side WordPress REST implementation is usually more suitable for long-running tasks and can be separately limited to draft endpoints.

### Required telemetry (metadata only)

Record timestamp, app package/build SHA, Android/security patch, `PowerManager.isInteractive`, `KeyguardManager.isKeyguardLocked`, screen-off/on events, device idle mode, browser state, virtual display attachment, WebView URL host hash (not secrets), task/Activity lifecycle, foreground-service state, frame fresh nonce match count, operation type/outcome, explicit local consent/STOP, and revocation cause. No screenshots or private credentials in log or Drive.

## 4. Physical test gates (human approval *before* request)

0. **Explain first** in Persian with exact text/labels of native dialog, domain, client, lease, STOP, expected lock action, and how to reject. Wait for explicit readiness rather than sending a 30-second pending request while the user is reading.
1. **Baseline unlocked:** app-private QA only; exactly public `example.com`; real native affirmative; take 3 distinct fresh frames (image hash and pixel nonce, no content retained); validate same-tab interaction and restore.
2. **Lock test:** while approved, press physical power to turn off/lock. Capture OS state by ADB *before* querying app status: display, notification, keyguard, activity/process, service. Try 3 new distinct frame requests and one synthetic DOM action against approved own WebView without waking or unlocking. No automation of the Android unlock screen. Distinguish `403_LOCKED_POLICY`, `SUSPENDED`, `TIMEOUT`, `STORED_OR_STALE_FRAME`, `PASS_FRESH_LOCKED_FRAME` precisely.
3. **Revoke:** native STOP, explicit app return, expiry, tab/site change, paired-client mismatch, device/process kill and lost network must all revoke or safely suspend. If user cannot access STOP without unlocking, expiry and a trusted host-side emergency revoke must still function.
4. **Stress:** 20 frames/90s for initial research, process recovery, 10-minute Doze and repeated idle (new lease only with consent), screen rotation, notification removal, Wi-Fi to LTE changes. No indefinite wake lock or indefinite session.
5. **WordPress staging only after explicit separate approval:** create one **draft**, inspect page via owner-approved authenticated path, avoid publication. Show exact target URL/id and rollback. Verify actual WordPress state, not HTML preview.
6. **Final code acceptance:** reviewers sign off threats, Android/iOS parity or Android-only experimental disclosure, authorized source/CI + staged artifact signature + pulled installed APK checksum. Draft branches MUST NOT merge automatically.

## 5. Decision record / follow-up

- Owner expressed desired capability; feasibility and permission envelope remain subject to research, not an implementation guarantee.
- Current screen-off safety Draft #13 remains **correct default**. Any different locked mode must be **separate and unmistakably consented**.
- No live WordPress changes, lock bypass, accessibility/MediaProjection abuse, installed QA changes, unsigned release, artifact promotion or silent permissions.
- **State:** `RESEARCH_DOCUMENTED / CODE_ARCHITECTURE_INCOMPLETE / LOCKED_MODE_NOT_IMPLEMENTED / HARDWARE_NOT_TESTED / SYNC_BLOCKED_MAIN / RELEASE_NO_GO`.
