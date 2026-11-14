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
- The later corrected build and physical retests have now been verified; see the dated readback below. Preserve the earlier failed-run evidence as a regression witness.

## Remaining release gates

1. Verify dedicated CI and whole Android workflow on the corrected exact HEAD, plus APK sha256 and ABI/package identity.
2. If a new ephemeral debug signing key prevents in-place install, back up/redact synthetic evidence, remove **only** the isolated P1 experiment after verifying package name, then install the corrected P1 APK. Preserve the original browser and prior PoC.
3. Repeat unlocked HOME and ChatGPT-foreground runs: unique `INITIAL_PROOF`/migration events, exactly 20 fresh nonce-matching frames, `activityVisible=false` for every match, matched `FRAMES_COMPLETE`, then matched `RESTORE_VERDICT` and original tab/document/form/history/scroll.
4. Keyguard behavior, Doze, long-run resource bounds, multi-tab authenticated session architecture, per-client consent/expiry/Pause/Stop and original Kelpie integration remain separately OPEN. No browser release or site deployment.

**Classification of original Run 1:** PHYSICAL FAILURE CONFIRMED; see below for later corrected-build PASS. Main Kelpie and ENERFOAM TEST/LIVE remain unchanged.

## Corrected P1 build and direct S24 re-test — PASS, isolated fixture only

**Corrective source:** `8abd2235e7084022f4ff7f11f7ff67783dc542ed` (single-flight lifecycle gate, JVM tests, syntax fix). Dedicated [S24 Private VirtualDisplay Spike QA run 43637889372](https://github.com/imanipour2/kelpie/actions/runs/43637889372) finished **SUCCESS**. It ran committed-source formatting comparison, Kotlin lint, JVM tests (including duplicate-callback regressions), Android lint, assemble, package identity and 16-KiB APK ZIP alignment. Artifact **14130736443** ZIP SHA256 `5af8e42d94a40c5b13244de8b92be9051c7e37a6f3b1f216cfdf5d93fda45f72`. APK SHA256 `704f46f98ef04f425f38646fe8a57b18415640f5bd89bf14d2be087681ce0efa`.

**Installation integrity:** transfer via verified Google Drive working copy and independent ZIP CRC/SHA256; exact app identity `com.kelpie.virtualdisplayprobe.p1`, QA version `0.2.1-p1-qa`, build 3. In-place upgrade was rejected as `INSTALL_FAILED_UPDATE_INCOMPATIBLE` owing to differing ephemeral debug signers. With old synthetic-only full log and exact prior APK backed up, **only** isolated `com.kelpie.virtualdisplayprobe.p1` was uninstalled and replaced. `adb pull base.apk` after install verified exact new APK SHA256. Neither original `com.kelpie.browser.unifiedverify` nor earlier isolated `com.kelpie.virtualdisplayprobe` was uninstalled/replaced. ADB forwarding list remained empty.

**Hardware acceptance A — HOME in foreground:** In standalone user-started QA Activity, 1 `INITIAL_PROOF`, 1 `MIGRATED_SAME_OBJECT`, exactly 20 `FRESH_MATCH` entries (sequences 1..20, all `activityVisible=false`, all matched), 1 `FRAMES_COMPLETE` (`matched=true`), 1 `RESTORE_VERDICT` (`matched=true`). The same Java `WebView` object survived the two reparentings; original synthetic document nonce, form value, history marker, and scrollY=340 were byte-identical. Preserved 24-event local synthetic-only JSONL `C:\Enerfoam-Kelpie-QA\P1\P1_BUILD3_HOME_PASS_24.jsonl` SHA256 `fcdb6a43266ce50eaeab93ee3ab0dcb71263dd3e2219b822b3d27f9ebb7c5fe6`. **PASS 20/20 + state restore.**

**Hardware acceptance B — ChatGPT as foreground:** Repeat separate 20-step capture while Android `topResumedActivity` was `com.openai.chatgpt`, not QA app. Same exact 24-event structure and 20/20 new background frames, with `FRAMES_COMPLETE=true`, `RESTORE_VERDICT=true`, exact same Java object and document/form/history/scroll state. Evidence `C:\Enerfoam-Kelpie-QA\P1\P1_BUILD3_CHATGPT_PASS_24.jsonl` SHA256 `6e30f139cbccf833fe499940920b2c85c148dd15a0fe312961da70d9e8e052d5`. **PASS 20/20 + state restore.**

The QA process was force-stopped after saving evidence and ChatGPT returned to Android foreground. The P1 test package remains installed but is stopped. Original browser and prior PoC packages remain installed and ADB forwarding is empty.

**Interpretation:** The failure was fixed on the actual physical SM-S928B/Android 17. This is strong evidence of *same WebView object reparenting*, changed frames and preservation of a *synthetic offline document* while Activity is hidden. It **does not** prove Android supports continuous long-running rendering under Doze/keyguard, or migration of the actual Kelpie tab, live authentication/cookies, multi-tabs, network state, 24-hour resource bounds, or a secure always-on remote controller. All such gates remain **OPEN** and need separate scope/authorization, device checks, rollback and QA. No Kelpie browser APK release/merge and no Enerfoam TEST/LIVE site change.

**Disposition:** `ISOLATED_P1_SAME_WEBVIEW_HARDWARE_PASS_BOTH_SCENARIOS`; `PRODUCTION_TAB_MIGRATION_UNVERIFIED`; `SESSION_SECURITY_OPEN`; `RELEASE_NO_GO`.
