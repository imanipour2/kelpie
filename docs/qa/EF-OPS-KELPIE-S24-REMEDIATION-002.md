# EF-OPS-KELPIE-S24-REMEDIATION-002 | 2026-11-14

STATUS: DRAFT / NOT DEPLOYED / NO RELEASE ACCEPTANCE.
Branch: fix/s24-recovery-crypto-20261114 based on 5d975eae3905235b622edb60393485b5e7b985c2. This branch is not installed on the phone.

## Baseline and evidence
- PR #1 is open, draft, unmerged; base qa/s24-unified-pixelcopy-scroll-16kb-20261113, not main.
- Prior physical SM-S928B Android 17: com.kelpie.browser.unifiedverify v0.2.5 code 25; previous installed APK from 193da4f, recorded SHA256 8088ae521227bb7083e40bcb2bb5cfe47f08ba68ef33e8d05d875fe975bcfee7. This is not a newly built artifact.
- New direct MCP status: appForeground=false; notificationsEnabled=true; screenshotReady=false; recovery=USER_TAP_REQUIRED; tabs.list succeeded and preserved existing tab.
- Earlier end-to-end notification tap / PNG was verified on a previous patch, not on this remediation branch. No website changes.

## Root cause analysis
P0-A: Historical restored encrypted prefs without corresponding Android Keystore key caused AEADBadTagException startup crash, after CI debug signing mismatch and clean reinstall. Source inspection confirms SecretStore.open() and reads lacked guarded recovery.
P0-B: Recovery notification returns a Boolean rather than a request ID; Not now merely cancels the fixed notification. There is no one-shot state/TTL/replay protection in the live flow. Sending an Android notification does not prove the user saw or accepted it.
P1: Notification HIGH importance does not guarantee heads-up. Current status icon is a launcher icon. usesCleartextTraffic=true; LAN binding, pairing, auth and FGS connectedDevice eligibility require review.

## Changes in this branch
- SecretStore now fails closed if encryption init/decryption fails, without erasing ciphertext, tabs or pairing files; unavailable secrets read as absent and secret writes fail explicitly. This is containment, not a completed recovery UI.
- Backup rules exclude sharedpref/kelpie_secrets.xml from legacy backup, modern cloud backup and device transfer. Manifest merger still needs verification.
- ForegroundRecoveryState provides an isolated clock-injected model: ID, 120s TTL, coalescing, expiry, decline and legal transitions; JVM tests cover replay and expiry. Integration code now connects matching PendingIntent IDs to approval/decline Activity and MCP status/capture, but awaits CI, runtime and lifecycle review.
- New branch added to Android QA workflow, including ktlint, unit, lint, APK packaging, RELRO and 16 KiB checks.

## Blocking acceptance gates
1. Verify the new request-ID PendingIntent / Activity / MCP integration on a real device, including denial, stale intent, replay, user-visible status, cancelled requests and coalescing; no device proof yet.
2. Add user-visible re-authentication and scoped recovery for failed Keystore, with explicit consent for any destructive reset.
3. QA versionName 0.2.6-qa / versionCode 26 allocated in source; verify stable protected signer and signer match before any attempted update. No uninstall/data wipe due to APK signing mismatch. Verify migration and rollback.
4. Real SM-S928B device tests: foreground, background, tap Allow, tap Not now, TTL, blocked/silent/DND, lock, split-screen, screenshot freshness, navigation race and tab preservation.
5. Instrumentation and MCP contract tests, concurrency, retry throttling and 24h reliability; Wi-Fi/cellular and reboot.
6. Audit Android HTTP cleartext, LAN endpoints, exported intents, pairing replay, connectedDevice FGS eligibility and local tunnel auth.
7. Verify actual 16 KiB runtime with getconf PAGE_SIZE=16384 in addition to APK alignment.

## Rollback
Preserve the installed APK and non-secret app data; never blindly uninstall. In-place adb install -r is permitted only when signing certificate, package and version constraints are verified; otherwise require an approved migration procedure. No remote launch, root, notification permission force-grant, or lock bypass.

## Sources
- Android notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android channels: https://developer.android.com/develop/ui/compose/notifications/channels
- Secure background Activity launch: https://developer.android.com/guide/components/activities/secure-bal
- Android backup: https://developer.android.com/identity/data/autobackup
- Android 16 KiB: https://developer.android.com/guide/practices/page-sizes
- Prior PR and issue: https://github.com/imanipour2/kelpie/pull/1 and https://github.com/imanipour2/enerfoam-website/issues/98

Code branch readback/CI required before acceptance. Canonical Drive Runbook and Live Master Log sync completed with readback; owner approval for any new architectural decision remains a separate CDG gate.


## 2026-11-14 — Post-approval capture handoff (QA-only, not yet device-accepted)

### Evidence-based root cause

The S24 proved that the Android notification, explicit "Allow & open Kelpie" action, Activity resume and original tab restoration work. Returning to ChatGPT subsequently places Kelpie in background; a *new* screenshot call is therefore rejected by the correct foreground policy. This is a time-of-capture/response design problem rather than a notification-importance problem. The earlier notification approval alone must not be confused with completed image capture.

### Proposed/implemented guarded flow

1. An authenticated, permitted foreground request arms a single short-lived ticket containing request UUID, active tab ID, document generation and original screenshot options. No capture occurs before the user taps the matching notification action.
2. An actual Activity resume AFTER approval schedules a bounded renderer-readiness loop on the Android main thread. It uses the original active WebView, existing visibility checks and PixelCopy/visual-state readiness. If the app is paused, the tab closes, navigation begins or the consent expires, it cancels instead of capturing or returning stale data.
3. The image is held *only in process memory*, capped at 12 MiB of base64 and 60 seconds after capture (subject also to the original request's 120-second expiry). No screenshot is saved to SharedPreferences, logs, backup or cloud storage.
4. The caller may retry `tab.screenshot` for the same tab with `params: {"requestId": "..."}` (or `recoveryRequestId`) and the original options. A matching live tab/document and request ID are mandatory. This consumes the staged image exactly once and returns the original request ID and capture timestamp. A miss returns `RECOVERY_CAPTURE_NOT_READY` without generating another notification.
5. The explicit `kelpie_request_foreground` extension also arms the default PNG/viewport screenshot. It does not start the Activity by itself.

### Samsung multi-profile remediation

ADB showed identical packages simultaneously hosted by Owner/User 0 and Dual App/User 95. Both processes tried to use the same local TCP port. Android's UID ranges identify the profile, so the primary profile keeps the existing port, while secondary profiles receive a deterministic per-profile port offset; mDNS advertises the actual port. This is a change to the *QA* browser only. Profiles remain installed and retain separate app data. Device and tunnel testing must still prove the connection picks the correct profile.

### Tests and remaining security gates

JVM tests now cover consent caching, one-time retrieval, replay rejection, wrong tab/document, expiry, cancellation and oversized image; plus deterministic owner/Dual App profile port mapping. Android QA and lint for **the latest commit** are pending and must be re-read. The on-device screenshot handoff has **not yet been proven**. We must test both default screenshot and `requestId` retry through the canonical MCP gateway, return to ChatGPT, physical decline, stale approval, screen lock, navigation race and request concurrency. Note that request UUID alone is not authenticated client identity: a future hardening pass must bind it to the original authenticated pairing client, or introduce an independent confidential redemption token, before shipping broadly to multiple paired clients. Treat the current code as QA-only.

### Source notes (official docs and community validation)

- https://developer.android.com/guide/components/activities/activity-lifecycle — Activity focus/resume versus later pause.
- https://developer.android.com/guide/components/activities/secure-bal — explicit user tap rather than remote background activity launch.
- https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,android.webkit.WebView.VisualStateCallback) — visual-state callback guarantees a future draw, not already-present pixels.
- https://developer.android.com/reference/android/view/PixelCopy — surface readiness and asynchronous copy completion.
- https://developer.android.com/develop/ui/compose/notifications/channels — channel importance does not guarantee heads-up on Samsung.
- Android developer community discussions (Stack Overflow and Samsung developer forums) were considered as implementation-risk reports, not substitutes for S24 hardware evidence.

### Release decision

Latest QA source version 0.2.7-qa (27) is not the previously installed 0.2.6-qa (26). Do not report installation, capture success, or merge/release acceptance before artifact SHA256/readback and physical S24 testing. All GitHub development text remains English.


## Follow-up contract and lifecycle audit (2026-11-14, branch only)

A second source inspection found an overlooked strict-protocol gate: the canonical `tab.screenshot` request schema originally accepted only `fullPage`, `format` and `quality`. The initial recovery implementation's `recoveryRequestId` would therefore have been rejected *before reaching Android*. The authoritative TypeScript Zod schema now permits an optional UUID recovery ID; new shared protocol and native C++ gateway tests assert acceptance and pass-through, and invalid IDs remain rejected by the strict JS schema. The generated native browser contract is derived from the shared package at build time; no checked-in generated payload was manually patched.

Also corrected a mismatch: `kelpie_request_foreground` stages a screenshot with canonical **viewport** resolution, not native, matching `tab.screenshot`'s native gateway behavior. First-frame capture now requires an actual RESUMED/FOCUSED Activity and waits for window focus rather than abandoning the request on a benign onResume/focus race. Browser tab changes, close and teardown explicitly invalidate staged capture and in-flight work.

The owner-facing message "Capture ready. Return to ChatGPT." is emitted only after a frame is actually staged. Do not interpret `ACTIVITY_RESUMED`, posted notification or the toast without matching MCP retrieval as end-to-end success.

At this checkpoint: source-only `0.2.7-qa` (build 27), latest Build/QA pending on GitHub, older in-progress builds are superseded/cancelled, installed `0.2.6-qa` (build 26) remains untouched. **NO-GO** until current SHA build passes, artifact checksum is independently verified, and physical Allow/Not now + one-shot cached image tests pass.
