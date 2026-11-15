# S24 P3 — actual Compose AndroidView owner, isolated WebView reparenting research

**Date:** 2026-11-15 | **Status:** SOURCE / CI PENDING / PHYSICAL NOT TESTED / RELEASE NO-GO  
**Predecessors:** [P1](EF-OPS-KELPIE-S24-P1-SAME-WEBVIEW-HARDWARE.md) and [P2](EF-OPS-KELPIE-S24-P2-TABSTORE-SURFACE-PLAN.md).  
**Project tracker:** [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).  
**Authority:** CDG `EF-OPS-KELPIE-S24-SPIKE-005` approves **bounded local synthetic experiment only**, not production browser release. Product parity in Kelpie AGENTS remains mandatory when proposing a real feature on Android/iOS.

## Purpose and safety boundary

P1 proved that a synthetic WebView can be transferred to an app-private Presentation, render 20 changed colors and be returned with its state. P2 proved the same with a genuine `TabStore.activeTab.webView` on Samsung SM-S928B Android 17, HOME and ChatGPT foreground: two exact 24-event offline runs, 20/20 fresh pixels and matching restoration. **Neither used Compose's production AndroidView ownership in the original browser.**

P3 uses the **actual** production `TabWebViewContent` Compose function, wrapped in a synthetic-only `P3ComposeTabHost` inside a separate `com.kelpie.browser.p3verify` QA application. The original WebView must have been mounted by Compose. After explicit user consent, release its Compose ownership; require that the WebView has left the window before private-display reparenting. After 20 checked frames, dispose the Presentation and remount the *same object* into the actual Compose component; assert mount counter advanced, original tab ID/instance and documentVersion unchanged, and synthetic nonce/input/history/scroll preserved. On mismatch, fail closed and release resources. The P3 QA app does not start local HTTP/MCP/Nessie account, does not target actual user profile, and blocks all network requests on its fixture WebView. Its user interface and runtime are separate from installed Kelpie P1/P2/prod-like QA.

**Disallowed:** actual site tabs or cookies, background Activity launch, keyguard bypass, app content screenshots, recording raw images, remote-control binding or release signing. Its diagnostic package may still inherit app permissions via manifest: this is **not** a minimal-permissions independent PoC; its narrow safety boundary is source/runtime and separate Android app identity, and a manifest audit must document any inherited permissions.

## Gates

1. Source-level: P3 files, correct activity manifest disabled in ordinary profiles, P3-specific Gradle identity, no changes to original `TabWebViewContent` behavior, no unreviewed platform parity exception. All Kotlin files <=500 lines and docs <=1000 lines.
2. Dedicated CI on **exact tested SHA**: clean KtLint verification, Gradle JVM tests, Android Lint, build, AAPT identity `com.kelpie.browser.p3verify` / label, native library / 16KiB alignment, artifact hash. Generic CI must also be read back separately.
3. Device preflight: ADB model/build, existing original browser/P1/P2 package presence, no new forwarding; check P3 absent or backup P3 package and synthetic logs. Install only exact verified P3 artifact, verify installed `base.apk` hash.
4. **HOME run:** user manually consents; original Compose mount found; no capture before explicit release; source emits `INITIAL_PROOF`, `MIGRATED_COMPOSE_WEBVIEW`, exact 20 independent `FRESH_MATCH` hidden activity frames (seq 1..20, single-use per run, nonce-changing), `FRAMES_COMPLETE`, then `RESTORE_VERDICT` with original Compose remount verified and unchanged document/form/history/scroll. Record any error as FAIL.
5. **ChatGPT foreground run:** same again with Android top Activity ChatGPT. Check Compose owner release and subsequent remount and full restoration. Collect only synthetic JSONL + SHA.
6. **Negative runs:** keyguard should fail closed, STOP should release display and cancel lease, invalid tab/document/lease cannot capture, no image data stored. Doze, long-run/24h, rotation, multi-tab, video/IME and real remote consent are **not claimed** by P3.
7. Update GitHub PR, issue #98, Drive live master log and readback. Keep Draft, NO MERGE/RELEASE until independent security and owner approval.

## Sources read and application

- ENERFOAM Drive START HERE; Master Index; CDG; EF-GOV-DOCOPS-001; EF-IMP-V2.1-REV2; Blueprint V2; EF-OPS-TOOLS-001; Live Master Log. GitHub `main` Kelpie AGENTS/CLAUDE and ENERFOAM `governance/decisions.json`, `CURRENT-AUTHORITY.md`; PRs #4/#5 and Issue #98.
- [Android AndroidView interoperability](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose) — production ComposeView + AndroidView created/updated only on UI thread; explicit composition release before reparenting.
- [Android WebView.loadDataWithBaseURL](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)) — explicit HTTPS base/history URLs and page origin, never assert outdated TabStore.currentUrl for synthetic direct load.
- [Android private displays](https://developer.android.com/reference/android/hardware/display/DisplayManager) — private OWN_CONTENT_ONLY, not screen mirroring.
- [Android activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle) and [foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — no background Activity launch or keyguard bypass.

**No claims of actual Compose fidelity or physical acceptance until CI + Samsung device readback.**
