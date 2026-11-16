# S24 P4 — screen-off immediate private-capture revocation (isolated QA)

**Date:** 2026-11-15 · **Mission:** EF-OPS-KELPIE-S24-REMEDIATION-002 · **State:** DRAFT SOURCE ONLY / HARDWARE RETEST REQUIRED / RELEASE_NO_GO

## Observed baseline

- Samsung SM-S928B / Android 17 / Windows USB ADB; exact P4 source `b8c3a351661b58578620e49860514c2571ebda2f`, isolated `com.kelpie.browser.pcverify` 0.2.9-qa, public `https://example.com/` only.
- Real user-approved 90-second zero-frame lease auto-expiry PASSED: native notification and app-owned private virtual display absent in OS readback before calling the status API; after expiry status `NOT_FOUND/IDLE`, frame HTTP403. [Issue #98 proof](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6896438257).
- Follow-on local wrong `clientId` and unknown requestId negative tests passed (frame/STOP HTTP403), but **two truly separate remotely paired clients remain untested**. [Latest local audit](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6896517762).
- Real native STOP gesture test remains **NOT_TESTED** because consent was not obtained in the 30-second window. One exploratory run encountered Unicode console output failure; a later request saw `FAILED` renderer / 409 `RECORDING_IN_PROGRESS`. Restarting *only* isolated QA cleared that state; underlying failure root cause remains **OPEN**. It is not acceptable to imply STOP PASS.
- Code audit: before this patch, `PrivateCaptureCoordinator.isLocked()` and `PrivateWebViewDisplay.isLocked()` refused frames while locked, but there was no **event-triggered revoke** on `ACTION_SCREEN_OFF`. A private renderer could thus remain until TTL/next API read. This is an **unverified threat scenario**, not an observed physical keyguard leak.

## Narrow code change

Only in the QA-only coordinator (created exclusively if `privateCaptureQaEnabled`), register a nonexported context receiver for protected `Intent.ACTION_SCREEN_OFF`; on receipt call the same `stopByUser()` that revokes the grant, releases app-owned `VirtualDisplay`, restores the original `WebView` through existing Compose ownership and removes notification. `goAsync()/finish()` keep the broadcast result alive through suspend cleanup. Unregister at coordinator `close()`. No elevated permissions, no `MediaProjection`, no accessibility hooks, no external-app capture, no manifest receiver, no new endpoint or private URL support.

Android reference: [Intent.ACTION_SCREEN_OFF](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_OFF) is a *protected system broadcast*, only available to context-registered receivers. [Broadcast receiver guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts) describes lifecycle-bound registration/unregistration, `goAsync()` and Android 14 cached-process deferral caveats. A code-level receiver alone is **not** proof of immediate device-side release under Doze; test empirically.

## Exact acceptance and blockers

1. CI: Android S24 opt-in QA build, Kotlin formatting/lint, Android integration and native gating; ensure unchanged original package and no microphone/capture permissions.
2. On-device separate QA installation, verify signer/version and pulled APK SHA256; never overwrite original `unifiedverify`.
3. Public `example.com`, explicit local positive approval, Activity HOME, verify private display + active QA notification, **physically press power/lock**; read `dumpsys display` and notification *before querying capture status*. Expect both gone shortly after system `ACTION_SCREEN_OFF`, status `NOT_FOUND/IDLE` and subsequent frame 403. Reopen and compare same original tab, document nonce, form, URL, history and CSS scroll. No ADB fake approval or screenshots.
4. Repeat for manual real notification STOP, window expiration, app foreground return, mid-encoding keyguard, delayed Doze, rotation and simultaneous foreign paired clients. No privileged or private-account capture.
5. Any `FAILED` renderer must be independently investigated; don't count a manual force-stop as a successful recovery mechanism.
6. Security/Apple parity/release signer/rollback and owner review before changing the public app. **Do not merge this Draft or publish.**

## Workflow, ownership, and scope

Read the Enerfoam START HERE, Master Index, CDG `EF-OPS-KELPIE-S24-20261114`/SPIKE-005 and `EF-GOV-DOCOPS-001`, approved `EF-IMP-V2.1-REV2`, Blueprint V2, GitHub `AGENTS.md`/`CLAUDE.md`, Live Master Log and Issue #98 before changes. This PR is a GitHub **code/test mirror**, not CDG decision approval. WordPress/Woo/Elementor and Enerfoam G0–G4 not touched. `SYNC_BLOCKED_MAIN / RELEASE_NO_GO`.
