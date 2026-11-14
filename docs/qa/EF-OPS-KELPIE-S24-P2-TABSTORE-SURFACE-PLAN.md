# EF-OPS-KELPIE-S24-P2 — real TabStore-owned WebView, isolated surface hardware QA

**Date:** 2026-11-15  
**Scope:** technical experiment, **not a production browser or remote screenshot feature**.  
**Predecessor:** P1 [physical evidence](EF-OPS-KELPIE-S24-P1-SAME-WEBVIEW-HARDWARE.md), [PR #4](https://github.com/imanipour2/kelpie/pull/4).  
**Workstream:** [PR #5](https://github.com/imanipour2/kelpie/pull/5), [ENERFOAM Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).

## Source-of-truth preflight

Read the canonical ENERFOAM Drive **START HERE**, **Master Index**, approved **EF-IMP-V2.1-REV2**, **Blueprint V2**, **CDG**, and **EF-GOV-DOCOPS-001** before this work, then current GitHub main AGENTS.md, governance/decisions.json, CURRENT-AUTHORITY.md, live master log and issue #98. Kelpie AGENTS.md and CLAUDE.md require root-cause-first, a reproducible QA gate, separation of the core/browser and host, and iOS/Android parity **for product features**. P2 is an experimental Android-only hardware diagnostic, not an introduced product feature or an iOS parity waiver for production.

Scope approved in CDG for predecessor `EF-OPS-KELPIE-S24-SPIKE-005` is **bounded research only**. P2 has no new invented EF-GOV owner approval; P2 branch, draft PR and QA evidence are research and verification. Canonical Drive approval and a separate production gate would be needed for actual background screenshot behavior.

## Security boundary

- Build only with `-PkelpieP2VerificationProfile=true`, which produces `com.kelpie.browser.p2verify` and **disables the normal MainActivity launcher**. P2 QA Activity is disabled in default/normal builds.
- The experiment runs in a separate Android application sandbox from `com.kelpie.browser.unifiedverify`, `com.kelpie.browser` and `com.kelpie.virtualdisplayprobe.p1`.
- QA Activity uses **TabStore and its original WebViewClient and WebView** (not a cloned WebView). It deliberately loads only a hardcoded synthetic RTL `fixture.invalid` HTML document and sets `blockNetworkLoads=true` and no file/content access.
- No HTTP/MCP router, Nessie account connection, CookieSync service or persistent browser-control agent is started by the QA Activity. Device-level app permissions inherited from the browser library are not permission to browse actual websites in this test; no credentials or real user tabs are permitted in the QA scope.
- A visible local Android consent dialog must be positively accepted before starting. A `SurfaceProbeLease` pins `tabId`, `documentVersion`, expiry within 90 seconds and exact sequence 1–21. On mismatch, cancellation, keyguard, view loss or expiry the run fails closed and releases the private display.
- `VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY` avoids mirroring any other app; `Presentation` shows only this app's original WebView. `ImageReader` samples one synthetic pixel, never writes full screenshot bytes or content to disk. JSONL logs synthetic state/pixel/identity metadata only. No screenshots, login fields, cookies, tokens, HTTP responses, user pages or secrets are logged.
- Android's rules about foreground service and background Activity launches remain in force; no attempt to bypass lockscreen, start background Activity or promote a user command into perpetual permission.

## Planned acceptance on physical SM-S928B / Android 17

1. Dedicated GitHub Actions on exact source HEAD: committed KtLint, JVM `SurfaceProbeLeaseTest` and `P2OfflineFixtureTest`, Android lint and APK assembly; package ID/label and 16 KiB alignment. Record exact artifact/ZIP/APK hashes, source SHA and debug signing before installation. 
2. Verify ADB device serial/model/build, existing browser and P1 package identities, and that P2 is absent or backed up. Install **only** P2 QA APK; independent `adb pull base.apk` hash must equal the artifact hash. Do not uninstall/update original browser.
3. Launch P2 QA UI and consent locally. Press HOME: one `INITIAL_PROOF`, one `MIGRATED_TABSTORE_WEBVIEW`, exactly 20 unique `FRESH_MATCH` events while Activity hidden, one true `FRAMES_COMPLETE`, one true `RESTORE_VERDICT`; same `TabStore.activeTab`, Java WebView, `documentVersion`, synthetic DOM nonce/form/history/scroll. Any duplicate event, mismatch or stale frame is **FAIL**.
4. Repeat with ChatGPT foreground and another 20 unique fresh frames, then restore/verify. In separate negative run, lockscreen must cause immediate stop, not continued frame production or backdoor unlock. 
5. Collect anonymized, synthetic-only evidence and compare via SHA256 to the local source. Stop QA app and restore ChatGPT foreground. Read back original Kelpie `tabs.list`; report `screenshotReady` truthfully (expected false in original background browser).
6. **Not covered:** Compose viewport ownership, real authenticated tab reparenting, persistence through Android recreation, Doze/overnight, WebGL/video/IME, multi-tab, same tab under remote control, per-client remote identity, consent/pause/TTL and real end-to-end ChatGPT screenshots. Those are *separate* gates with new documented security review, owner approval and rollback.

## References used and how

- Android DisplayManager private/OWN_CONTENT_ONLY/PRESENTATION flags: https://developer.android.com/reference/android/hardware/display/DisplayManager — private app-owned presentation; never screen mirroring.
- Android background foreground-service start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start — do not launch service from background or borrow special privileges.
- Android WebView and Android Activity lifecycle docs: https://developer.android.com/reference/android/webkit/WebView ; https://developer.android.com/guide/components/activities/activity-lifecycle — preserve original WebView, dispose display and cancel work on lifecycle changes.
- Kelpie own `AGENTS.md`, `CLAUDE.md`, `core/android/src/main/java/com/kelpie/browser/browser/TabStore.kt`, `TabWebViewClients.kt`, `ui/TabWebViewContent.kt` and `connect/NessieConnection.kt`: use original source ownership, keep existing background stop authority and no remote control extension.
- ENERFOAM Drive EF-GOV-DOCOPS-001 / EF-OPS-TOOLS-001: branch/PR/CI/readback; no merge or live changes without owner approval.

**Current status:** SOURCE_COMMITTED / DRAFT_PR / CI_PENDING / P2_DEVICE_NOT_INSTALLED / PRODUCTION_NO_GO. Update this exact document when test readbacks exist.
