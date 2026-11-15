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

**Original plan status (superseded by subsequent physical readback below):** SOURCE_COMMITTED / DRAFT_PR / CI_PENDING / P2_DEVICE_NOT_INSTALLED / PRODUCTION_NO_GO.

## Physical hardware evidence — 2026-11-15 (isolated P2 PASS)

**Source and build:** `9d60b83260d48d8856a956618560e63d68d69d21`, [dedicated CI run 43669768571](https://github.com/imanipour2/kelpie/actions/runs/43669768571) = **SUCCESS**, including Kotlin source formatting, `SurfaceProbeLease` / fixture JVM tests, Android lint, assembled debug APK, package identity `com.kelpie.browser.p2verify` and 16-KiB alignment. GitHub artifact `14143549823`. ZIP SHA256 `1e7f9ad7aeb6e59791487d3b587c2766bf3fa238d5ad1cc2fdbab54b0969306a`; embedded APK SHA256 `9277621af9918b255e4bb618f600b55f11d6f41e1eb8f1fc2a6a206024f9c2dd`. Archived [original evidence artifact in Drive](https://drive.google.com/file/d/11g0YcdjqSY_KlWF93wUGdyp7Ymq4b0o4/view).

**Installation:** Physical Samsung SM-S928B Android 17, Windows USB ADB. Exact ZIP checksum and CRC checked independently; embedded APK checksum matched CI sidecar. Ephemeral debug signing prevented in-place upgrade. Older *isolated* P2 APK and 3 synthetic failed-run logs were backed up, then **only** `com.kelpie.browser.p2verify` was uninstalled and this exact isolated P2 APK installed. `adb pull` installed `base.apk` hash matched `9277621a...` byte-for-byte. Real Kelpie browser `com.kelpie.browser.unifiedverify`, previous P1 probe `com.kelpie.virtualdisplayprobe.p1`, all original app data remain untouched; no ADB forwards and no website modifications.

**Predecessor P2 failed-run root cause:** Initial synthetic fixture used `loadDataWithBaseURL(BASE,...,historyUrl=null)`. Android's [official WebView reference](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)) states that null historyUrl defaults to `about:blank`, while base URL defines JavaScript origin. After setting a valid historyUrl, actual `WebView.getUrl()` matched the fixture but the synthetic load did not update `TabStore.currentUrl`. This was resolved **without weakening the main guards**: verify actual page `location.origin === 'https://fixture.invalid'` before starting, keep `WebView.getUrl().startsWith(BASE)` and pin original `TabStore` tab ID, original WebView instance, original documentVersion, !isLoading, time-limited 90s lease, unique 20-frame sequence, no network, keyguard stop and manual consent. The three earlier attempts are recorded as FAIL, with synthetic-only local logs preserved. Do not conflate those failures with final PASS.

**Run A / HOME foreground:** Actual original `TabStore.activeTab.webView` moved Activity -> private Presentation -> Activity. Exactly 1 `INITIAL_PROOF`, 1 `MIGRATED_TABSTORE_WEBVIEW` (`matched=true`), 20 unique `FRESH_MATCH` records with sequences 1..20, all `activityVisible=false` and `matched=true`, 1 `FRAMES_COMPLETE=true`, 1 `RESTORE_VERDICT=true`. Same TabStore WebView identity and documentVersion; synthetic nonce/input/history/scroll byte-identical after restore. Raw local 24-event JSONL `P2_FINAL_HOME_9d60b83.jsonl` SHA256 `8e35c972ca3695a067003150c736f7fa2acf29fee70adb739b4b7788842f84a6`. **HARDWARE PASS (isolated fixture).**

**Run B / ChatGPT foreground:** Repeated independent on-screen approval, then brought `com.openai.chatgpt` to Android foreground during P2 capture. Same exact 1+1+20+1+1 event pattern, every fresh frame Activity hidden, 20 unique/20 matched, matched completion and matched restoration; same original tab, WebView, document and synthetic form/history/scroll state. Raw local 24-event JSONL `P2_FINAL_CHATGPT_9d60b83.jsonl` SHA256 `9d6891d16157c947c583b14bd5bcf8e29e604fc834edc0f3ae20f442ab05030e`. **HARDWARE PASS (isolated fixture).**

QA process force-stopped and ChatGPT foreground restored after evidence. All three separate packages still installed: production-like installed Kelpie browser, P1 and P2; no remote HTTP/MCP background screenshot feature was added. Test P2 may remain installed as a stopped, opt-in isolated APK.

**Limits and release gates:** This validates same-TabStore-WebView offline reparenting and changed background pixels in HOME and ChatGPT scenarios. It does **not** validate original actual user profile or logged-in tabs, Compose ownership when a real tab is visible in normal browser, Android keyguard/Doze/battery long-run, real remote-control permission, multi-tab/video/WebGL/IME, secure screenshot stream authorization, source reproducibility with stable signing, accessibility and release. **All remain OPEN**, require independent security review and owner CDG approval before product implementation. No merge or production release. No ENERFOAM TEST/STAGING/LIVE change. 

**Status at physical readback:** `P2_ISOLATED_TABSTORE_HARDWARE_PASS_BOTH_SCENARIOS` / `COMPOSE_INTEGRATION_OPEN` / `REAL_TAB_REMOTE_CAPTURE_NOT_IMPLEMENTED` / `PRODUCTION_RELEASE_NO_GO`.
