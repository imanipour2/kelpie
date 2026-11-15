# EF-OPS-KELPIE-OSS-RCA-001 — Android background / screen-off / keyguard OSS comparative RCA

**Date:** 2026-11-15  
**Classification:** RESEARCH / TECHNICAL RECOMMENDATION / PROPOSED / NOT AN OWNER-APPROVED DEPLOYMENT  
**Canonical research original (Drive):** [Full Persian 10-section dossier](https://docs.google.com/document/d/1jyv47ShO1hkMcKAzuJR4467GW3IMoXCvF5xLWmgoiXA/edit)  
**Canonical owner requirement:** [CDG `EF-OPS-KELPIE-LOCKED-TASK-20261115`](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit)  
**Operational evidence:** [Enerfoam issue #98](https://github.com/imanipour2/enerfoam-website/issues/98)  
**Relevant experimental PR:** [#15](https://github.com/imanipour2/kelpie/pull/15) / [CI-only #16](https://github.com/imanipour2/kelpie/pull/16); `6f71a9b0036b6d74a787ff5661b4012d4b0d37ce` passed 11 exact-head CI checks, no physical locked test.

## Core conclusion

A real foreground service, reachable socket/HTTP/CDP, and **newly rendered** screenshot under keyguard are three independent capabilities. Foreground processing can persist when an Activity is stopped, and JavaScript/DOM may still respond even with **no compositing surface**, while fresh `Page.captureScreenshot` is impossible without an active rendering surface. In long device-idle periods Doze/App Standby can block network, defer work and ignore wakelocks even for important processes. A normal Android APK **cannot inherit** the shell/AOSP privileged powers used by ADB/scrcpy/Shizuku. No checked OSS project proves `same WebView + real Galaxy S24 Ultra security keyguard + fresh pixels + click/type + continuous unbroken transport` for a regular APK.

### Source-grounded reusable comparisons (actual code inspected)

| OSS | Exact source files | Observed implementation pattern | Transfer decision |
| --- | --- | --- | --- |
| [Termux](https://github.com/termux/termux-app) | `app/src/main/java/com/termux/app/TermuxService.java` (56–155) | Service owns terminal sessions independently of Activity, FGS and optional wake/Wi-Fi lock, explicit stop; `START_NOT_STICKY` | **ADOPT concept:** long-lived browser session owner and UI attach/detach, not a proof of pixel rendering |
| [KDE Connect](https://github.com/KDE/kdeconnect-android) | `src/main/java/org/kde/kdeconnect/BackgroundService.kt`, `KdeConnectBroadcastReceiver.kt` | Paired devices, persistent notification, `NetworkCallback.onAvailable/onLost`, resume on screen/network | **ADOPT concept:** reconnect/reconciliation with authenticated client principal |
| [Syncthing-Fork](https://github.com/researchxxl/syncthing-android) | `service/SyncthingService.java`, `RunConditionMonitor.java`, `NotificationHandler.java` | Battery/power-saving/network/time run-condition state, jobs and explicit resume | **ADOPT concept:** operation queue, checkpoint, fail-closed, idempotency |
| [Home Assistant Android](https://github.com/home-assistant/android) | `common/.../SensorWorker.kt`, `app/.../ForegroundServiceLauncher.kt` | Periodic WorkManager 15-minute network constrained; foreground service start exception handling | **ADOPT for telemetry**, reject as instant interactive screenshot engine |
| [RustDesk](https://github.com/rustdesk/rustdesk) | `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainService.kt`, `InputService.kt`, Android manifest | MediaProjection + ImageReader + VirtualDisplay and Accessibility; for screen-off input, code explicitly triggers display wake via WakeLock for some pointer events; projection stop/recovery | **REJECT as direct implementation:** wake/screen capture depends on different permissions; borrow cleanup/error handling only |
| [droidVNC-NG](https://github.com/bk138/droidVNC-NG) | `app/src/main/java/net/christianbeier/droidvnc_ng/MainService.java`, `capture/mediaprojection/MediaProjectionService.java` | VNC Service, separate MediaProjection Service, callback-based stop/release, separate Accessibility input | **REJECT screen mirroring as locked WebView strategy**; reuse revoke/cleanup/state concepts |
| [scrcpy](https://github.com/Genymobile/scrcpy) | `app/src/server.c`, `server/src/main/java/com/genymobile/scrcpy/device/Device.java` | `adb shell ... app_process`, system-side `SurfaceControl`/DisplayPower/input | **NOT transferable** to a regular installed APK; relevant as ADB diagnostic comparison |
| [android-webview-cdp / So7o](https://github.com/jan5o7o/android-webview-cdp) | `java/app/so7o/webview/{MainActivity,KeepAliveService,RelayServer}.java` and [README rendering/freeze experiments](https://github.com/jan5o7o/android-webview-cdp#running-it-off-the-phone-screen-and-testing-at-phone-sizes) | SM-F936B Android 16: FGS keeps CDP/relay responsive even background; `overlay_display_devices` via ADB creates active secondary display. On surface-less OFF display JS/DOM/network/input responded, but screenshots timed out, screencast 0 and rAF stalled | **PRIORITY REFERENCE:** separate compositor/pixel freshness gate from DOM/transport; **do not inherit** ADB `WRITE_SECURE_SETTINGS` dependency |
| [Headless Android](https://github.com/HakaI963/headless-android) | `app/src/main/java/com/bliss/headless/{VirtualDisplaySession,DisplayEngine}.java`; README | `ImageReader` owns VD surface, frame count/timestamp; Shizuku/Shell privilege; explicitly says real keyguard cannot be bypassed and requires unlocked phone | **ADOPT concept:** frame freshness telemetry; reject shell privileges for normal Kelpie |
| [Even Hub background-state](https://github.com/even-realities/everything-evenhub) | `plugins/everything-evenhub/skills/background-state/SKILL.md` | Snapshot JS state, run *new* HeadlessInAppWebView, restore before foreground return | **LIMITED:** only for serializable state; not equivalent to same live Woo/Elementor WebView/history/form |
| [WallMode](https://github.com/rvbcrs/WallMode) | README/browser recovery implementation | Detects WebView renderer crashes/network failures, bounded native recoveries; active display/kiosk assumptions | **ADOPT concept:** RendererGone failure state and bounded recovery; not evidence of lock-screen pixels |

Code paths, code-level excerpts, explicit source limits, platform links and test gates are in [Drive research](https://docs.google.com/document/d/1jyv47ShO1hkMcKAzuJR4467GW3IMoXCvF5xLWmgoiXA/edit). **Architectural patterns, not unlicensed source-copy**, especially for GPL/other copyleft sources. Verify exact component licenses before importing code.

## Android constraints that remain binding

- [MediaProjection](https://developer.android.com/media/grow/media-projection) session consent is specific and not reusable; OS/user/lock may stop it. For Kelpie own content use a **private self-owned** `VirtualDisplay` + `ImageReader` rather than capturing Android screen/UI or `FLAG_SECURE` from other apps.
- [Doze/App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby): CPU/network/jobs/wake restrictions, not guaranteed by foreground service. No silent exemption from battery optimization.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): `connectedDevice` and `specialUse` each have prerequisites/Play policy; the type must match actual use. `dataSync` and `mediaProcessing` may have duration restrictions in recent Android versions. QA must verify manifest + device-side behavior.
- [WebView](https://developer.android.com/reference/android/webkit/WebView): `setOffscreenPreRaster` is not a hidden keyguard renderer. `onRenderProcessGone` recovery may lose live document state; `pauseTimers` can affect all app WebViews. Keep Active Surface/Window independent of foreground interactive display.
- [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager): `isKeyguardLocked`, `isDeviceLocked`, `PowerManager.isInteractive` and physical `Display.STATE_OFF` are **not synonyms**; log all four. [Samsung background policies](https://developer.samsung.com/mobile/app-management.html) are mitigations only.

## Kelpie source gap and proposed architecture

Exact existing source at `6f71a9b`:
- `KelpieBrowserSession.kt` is Activity-owned and destroys TabStore/WebViews/gateway on `ON_DESTROY`.
- `KelpieNetworkService.kt` owns HTTP/mDNS, not the original app-owned browser renderer.
- `PrivateWebViewDisplay.kt` has P4 same-WebView `VirtualDisplay + ImageReader`, previously proven 20/20 fresh frames with phone **unlocked**, not locked.
- `PrivateCaptureCoordinator.kt` (Draft #13 default policy) revokes on `ACTION_SCREEN_OFF`; `ConsentedCaptureLease.kt` requires unlocked and 90s/20 frames, exact `example.com`.
- New `LockedTaskConsentLease.kt` / tests in PR #15 is **UNWIRED POLICY ONLY**; no native locked consent dialog, no service-hosted WebView, no common HTTP/MCP action admission, no device test.
- Generic permitted `CLICK` could activate WordPress Publish/Delete despite blocking a conceptual `PUBLISH` action; enforce **server-side staging Draft-only account/action restrictions** before any real test.

**Recommended separate owner-approved workstream**, not approved deployment:
1. Preserve **default screen-off revoke** for existing grants. Implement a **new, distinct, finite LOCKED_TASK opt-in** with native user consent, precise paired identity/tab/document/WebView/site/method scope and an always-effective STOP.
2. Extract **same original** `WebView` + `TabStore` + renderer into a service-owned, Activity-attachable controller, with private active Surface. NO `MediaProjection` or Shizuku for this app-owned content.
3. Gate **all** `screenshot/DOM/click/type/scroll/navigation` at common authenticated Router+post-await authorization, with fresh image nonce and no stale screenshot substitutions.
4. Implement network callback + queue + bounded resource/CPU, checkpoint/readback/duplicates protection, and explicit pause/error states; do not assume continuous network under Doze.
5. Separate long-running **WordPress Draft** creation through officially authenticated [WordPress REST Pages](https://developer.wordpress.org/rest-api/reference/pages/) or WSP, preserving Elementor Native First and G0–G4 gates. Browser visual QA only when compositor and consent are proven alive.
6. Before **any** 30-second consent request, **brief the user in Persian** about exact prompt, address `https://example.com`, actions, 90s TTL, 20 actions, Approve/Cancel/STOP and screen-lock sequence; obtain readiness first. No ADB-simulated taps/consent. QA on S24 real keyguard/Doze/locked fresh frames separately; never confuse screen-off without secure lock with actual keyguard.
7. Only after synthetic no-private-data tests, optionally test **staging WordPress draft** with verified recoverable backup and explicit separate owner approval; never publish/delete/change commerce.

## Status and disposition

`RESEARCH_COMPLETED / PROPOSED_ARCHITECTURE_ONLY / NO_S24_LOCKED_PHYSICAL_PROOF / DRAFT_RESEARCH_PR / 11_CI_SUCCESS_ON_UNWIRED_POLICY / RELEASE_NO_GO / SYNC_BLOCKED_MAIN`.

This document mirrors research findings, **not code merged into main**, not a new EF-GOV decision, not a claimed production implementation.