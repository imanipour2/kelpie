# EF-OPS-KELPIE-S24-P1 — Same-WebView surface migration, hardware readback

**Date:** 2026-11-14  
**Device:** Samsung Galaxy S24 Ultra SM-S928B, Android 17, USB ADB on Windows  
**Scope:** User-delegated, offline synthetic QA only; not actual Kelpie tabs.  
**Project:** `EF-OPS-KELPIE-S24-REMEDIATION-002`, [Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).

## Build and installation preflight

- Experimental branch: `experiment/s24-same-webview-migration-20261114`; parent [PR #3](https://github.com/imanipour2/kelpie/pull/3), current [PR #4](https://github.com/imanipour2/kelpie/pull/4).
- Initial P1 commit: `504c62118237eac23578cb913e682fd85ba20e41`; isolated [CI run 43637068324](https://github.com/imanipour2/kelpie/actions/runs/43637068324) completed SUCCESS (ktlint, JVM unit, Android lint/build, package ID and 16-KiB APK ZIP alignment).
- Downloaded artifact 14129499883, archive SHA256 `59137841022054ddbceede82abe80169be3d8b876392c77a2d485eff34efcf8a`, APK SHA256 `c75c60e95d58ad7b15f7b3c9e2630ad4c84e8424801ae351205e5aac2ae97522`.
- Installed `com.kelpie.virtualdisplayprobe.p1` version `0.2.0-p1-qa` code 2; `adb pull` of installed `base.apk` yielded the same SHA256. No Android permissions requested.
- Both `com.kelpie.virtualdisplayprobe` (earlier fixture) and `com.kelpie.browser.unifiedverify` (real browser) remained installed, unaffected. No ADB port forwarding.

## First physical run — FAILED (root cause found)

Sequence: launch isolated launcher Activity, open `SameWebViewActivity`, press **Start migration / 20 changed frames** with authorized ADB-assisted UI input, press HOME, keep display unlocked. The main Activity was hidden for all recorded frame matches. Return to QA Activity and press **Restore SAME WebView**.

The app-private JSONL captured **two** `INITIAL_PROOF` events and **two** `MIGRATED_SAME_OBJECT` events: `WebViewClient.onPageFinished` was observed more than once and the test scheduled two independent migration/capture loops. For *each* nonce sequence 1..20 there were **two** `FRESH_MATCH` records (40 matching callbacks in all) with `activityVisible=false`. This is **not a valid 20/20 test**, because an exact-once sequence invariant was violated. `FRAMES_COMPLETE.matched=false` with duplicate counted iterations; `RESTORE_VERDICT.matched=false` because total counted frames were not exactly 20.

Important independent observation: the original Java `WebView` identity remained true and the synthetic `documentNonce`, input value, `history.state` marker and scrollY=340 were byte-for-byte identical before migration and after return. This is supporting *fixture-only* evidence, not a production browser acceptance.

- First-run raw synthetic-only log retained offline at `C:\Enerfoam-Kelpie-QA\P1\P1_HARDWARE_FIRST_RUN_DUPLICATE.jsonl`; SHA256 `6bce14b3406ddab82d389a958eccb0852edaa6d0ac485520d54fe8856733498b` (captured before manual restore, 45 events). Full post-restore log also read from the device; 46 events. No real tab text, login, screenshot, cookie or personal data was stored.
- **Verdict:** `P1_HARDWARE_RUN1_FAIL_DUPLICATE_CALLBACK`. Do not report 40/40 as acceptance; distinguish this from the earlier **independent** PR #3 fresh-frame PoC.

## Corrective source

- Added `ProbeLifecycleGate`, reset per run. Exactly once: `claimLoad()`, `claimMigration()`, and `claimStep(1..21)`. A repeated callback, delayed task or overlapping capture loop cannot increment the same step twice.
- Added independent JVM regression tests for repeated load/migration, out-of-order and duplicate step suppression.
- Updated QA build to `0.2.1-p1-qa` code 3 to distinguish the correction.
- **New APK build, signing/upgrade and physical rerun are not yet verified in this document.** Preserve the existing P1 application data and evidence unless a dedicated, verified QA-only rollback/uninstall is required. Never uninstall the main browser or first PoC.

## Remaining release gates

1. Verify dedicated CI and whole Android workflow on the corrected exact HEAD, plus APK sha256 and ABI/package identity.
2. If a new ephemeral debug signing key prevents in-place install, back up/redact synthetic evidence, remove **only** the isolated P1 experiment after verifying package name, then install the corrected P1 APK. Preserve the original browser and prior PoC.
3. Repeat unlocked HOME and ChatGPT-foreground runs: unique `INITIAL_PROOF`/migration events, exactly 20 fresh nonce-matching frames, `activityVisible=false` for every match, matched `FRAMES_COMPLETE`, then matched `RESTORE_VERDICT` and original tab/document/form/history/scroll.
4. Keyguard behavior, Doze, long-run resource bounds, multi-tab authenticated session architecture, per-client consent/expiry/Pause/Stop and original Kelpie integration remain separately OPEN. No browser release or site deployment.

**Classification:** PHYSICAL FAILURE CONFIRMED / SOURCE FIX IN REVIEW / MAIN KELPIE UNCHANGED / ENERFOAM TEST & LIVE UNCHANGED / RELEASE NO-GO.
