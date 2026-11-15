# S24 P3 — actual Compose AndroidView owner, isolated WebView reparenting research

**Date:** 2026-11-15 | **Status:** EXACT-SHA CI ALL PASS d1402dd / PERMISSION-FREE APK INSTALLED AND VERIFIED / PHYSICAL POSITIVE TEST NOT RUN / RELEASE NO-GO  
**Predecessors:** [P1](EF-OPS-KELPIE-S24-P1-SAME-WEBVIEW-HARDWARE.md) and [P2](EF-OPS-KELPIE-S24-P2-TABSTORE-SURFACE-PLAN.md).  
**Project tracker:** [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).  
**Authority:** CDG `EF-OPS-KELPIE-S24-SPIKE-005` approves **bounded local synthetic experiment only**, not production browser release. Product parity in Kelpie AGENTS remains mandatory when proposing a real feature on Android/iOS.

## Purpose and safety boundary

P1 proved that a synthetic WebView can be transferred to an app-private Presentation, render 20 changed colors and be returned with its state. P2 proved the same with a genuine `TabStore.activeTab.webView` on Samsung SM-S928B Android 17, HOME and ChatGPT foreground: two exact 24-event offline runs, 20/20 fresh pixels and matching restoration. **Neither used Compose's production AndroidView ownership in the original browser.**

P3 uses the **actual** production `TabWebViewContent` Compose function, wrapped in a synthetic-only `P3ComposeTabHost` inside a separate `com.kelpie.browser.p3verify` QA application. The original WebView must have been mounted by Compose. After explicit user consent, release its Compose ownership; require that the WebView has left the window before private-display reparenting. After 20 checked frames, dispose the Presentation and remount the *same object* into the actual Compose component; assert mount counter advanced, original tab ID/instance and documentVersion unchanged, and synthetic nonce/input/history/scroll preserved. On mismatch, fail closed and release resources. The P3 QA app does not start local HTTP/MCP/Nessie account, does not target actual user profile, and blocks all network requests on its fixture WebView. Its user interface and runtime are separate from installed Kelpie P1/P2/prod-like QA.

**Disallowed:** actual site tabs or cookies, background Activity launch, keyguard bypass, app content screenshots, recording raw images, remote-control binding or release signing. The original P3 artifact inherited browser/AndroidX permissions (P3-SEC-001). The corrected **P3-only** manifest overlay and dedicated CI now reject *any* permission in the APK; device `dumpsys package` confirmed zero requested permissions. This does **not** make it a production browser or authorize actual user data.

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

**CI and installed-app identity/security have passed; actual Compose fidelity, 20+20 background frames and physical acceptance remain UNVERIFIED until explicit local user approval and Samsung evidence readback.**


## Follow-up preflight: inherited Android permissions, remediation (2026-11-15)

On a physical Samsung SM-S928B / Android 17, the **original** P3 artifact (source `a3ef90bcbddd1af0428fbdfe9f51d0bcacee54f5`) was downloaded from dedicated GitHub Actions [43675894457](https://github.com/imanipour2/kelpie/actions/runs/43675894457), artifact `14146364248`. The ZIP SHA256 was `43aa59e5bc890e4031a8fb5bc6961bb897c56197358f33aabb0e3f01de245445`; embedded APK and installed `base.apk` both matched `853b5afb3b1b506371f17e74c10adccbaa0d3c24e94d76dba3c971ad6019f690`. The separate `com.kelpie.browser.p3verify` package installed successfully (version 0.2.7-qa/code 27); original `com.kelpie.browser.unifiedverify`, P0/P1 and P2 were preserved. ADB forwarding was empty.

**Security finding P3-SEC-001:** Android package-manager inspection of the installed P3 app found inherited `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` and a requested but denied `RECORD_AUDIO`, despite the synthetic fixture's `blockNetworkLoads` guard. This is inconsistent with the owner's bounded, zero-permission PoC boundary. No foreground capture, real URLs, user tabs, account or background screenshots were exercised on this APK. Baseline P3 **hardware acceptance is NOT established**.

Remediation restricted to this Draft branch: create `apps/android/app/src/p3Qa/AndroidManifest.xml` containing targeted `tools:node="remove"` permission removals; bind that manifest overlay to **only** Gradle's opt-in `kelpieP3VerificationProfile`, leaving normal, P1/P2 and unified builds untouched; add a dedicated CI APK AAPT check that fails closed on **any** `uses-permission` declaration. Changes: `b50d1d0`, `223b4a1`, `1a80674`. [Remediation CI](https://github.com/imanipour2/kelpie/actions/runs/43677776850) was running at the time of this entry, so no permission-free APK is yet claimed.

Further gates: latest exact-SHA CI PASS, package and permission audit of actual APK, isolated P3-only replacement and installed-base SHA readback, local affirmative on-device user consent, HOME and ChatGPT 20/20 fresh pixel runs, **actual Compose remount same WebView** restore, negative tests, evidence sync and independent security review. Keep Draft / RELEASE_NO_GO.


## Verified latest CI and device preflight (2026-11-15)

- **Exact source:** `d1402dd1f0327429a081a1cdf110dfe7cbe18e8d`, Draft [PR #6](https://github.com/imanipour2/kelpie/pull/6). [Dedicated CI 43679131770](https://github.com/imanipour2/kelpie/actions/runs/43679131770) PASS (formatter, unit tests, Android lint, 16KiB aligned, correct package and AAPT **zero declared permissions**); general [Android 43679136368](https://github.com/imanipour2/kelpie/actions/runs/43679136368), [Lint 43679136273](https://github.com/imanipour2/kelpie/actions/runs/43679136273), [macOS 43679136288](https://github.com/imanipour2/kelpie/actions/runs/43679136288) all PASS.
- Artifact `14147743655`; archive SHA-256 `12b4a3f845e869125642d71bcb0eadd6cace2d39d8491dbd5b64946f0f3b3c1e`, APK SHA-256 `a54aa1dd67d58d527d9c2c7b9f88715013bdd44464f429516ce4f2d9df086df7`. Independent installed `com.kelpie.browser.p3verify` v0.2.7-qa/code 27 pulled back from the physical SM-S928B; installed base APK hash matched and Android declared permissions were absent. Original unifiedverify plus P0/P1/P2 packages preserved; ADB forwarding empty.
- Negative unconsented test: launch and go HOME without approving; no `p3-compose-surface.jsonl` created. Further real readback with ChatGPT resumed on device on 2026-11-15 still found no P3 evidence file. Thus **no positive P3 test has begun**, no results may be inferred from P1/P2.
- [Operational issue #98 entry](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6889594532) and [Drive Live Master Log](https://docs.google.com/document/d/1HkCCpNjV7L9UoVCN6pJ_fhAsqBcn4OkNeIcOt_Iys8U/edit) carry the security and install evidence. **Acceptance status: CI_PASS / INSTALL_HASH_PASS / NO_PERMISSION_PASS / NO_CONSENT_NEGATIVE_PASS / HOME_POSITIVE_PENDING / CHATGPT_POSITIVE_PENDING / RESTORE_PENDING / NO_MERGE / RELEASE_NO_GO.**

Remaining **in-person gate**: open the P3 QA Activity on the phone; owner/participant must actually tap the local affirmative consent (the remote executor may only show the consent dialog, never simulate its approval). After on-device consent, physically press HOME and later reopen P3 to trigger restoration; repeat with ChatGPT foreground. Analyze 24-event synthetic-only JSONL, verify 20/20 fresh hidden frames and final same Compose WebView+state restoration. Do not treat absent evidence or a virtual device result as PASS.


## First locally consented physical attempt: FAIL-CLOSED, Compose-owned view detached on reset

**2026-11-15 | Device:** Samsung SM-S928B / Android 17; source for installed package `d1402dd1f0327429a081a1cdf110dfe7cbe18e8d`, SHA256 of installed APK `a54aa1dd67d58d527d9c2c7b9f88715013bdd44464f429516ce4f2d9df086df7`.

Exact one-event evidence (249 bytes) was read back over USB from the isolated app private files and saved on Windows: `C:\Enerfoam-Kelpie-QA\P3_fail_20261115\P3_COMPOSE_MOUNT_MISSING.jsonl`, SHA256 `4871441f9dab376eaf185e9cd376e80271acedbf7c91d5848c1fbb6caab4cedb`. Event was `COMPOSE_MOUNT_MISSING` with `activityVisible=true`, `tabStoreIdentity=true`, `sameDocument=true`, `composeOwner=true`, `composeMountCount=2`, `matched=false`. No initial proof or migrated/changed frames: **physical FAIL-CLOSED**. Consent was evidently attempted locally because `start()` clears/writes this evidence file only after the affirmative dialog callback.

**Root cause:** `start()` always calls `stop("RESTART")`. The prior `stop()` unconditionally removes the `originalWebView` from its `ViewGroup`, even when the Compose-owned WebView was **not migrated**. The actual production `TabWebViewContent` uses `installedWebViewRef` and returns early when that WebView is already remembered, leaving the detached WebView unmounted. Thus the subsequent strict `lastAttached === view && isAttachedToWindow` check correctly refused the run. The debug evidence showed Compose-owner and identity true but did not imply attachment true.

**Fix scoped solely to P3 QA:** [commit `08ab4ecbfa1582a1b1f8b2dd61022fe5d0cfe058`](https://github.com/imanipour2/kelpie/commit/08ab4ecbfa1582a1b1f8b2dd61022fe5d0cfe058) removes the WebView from its parent during `stop()` only when `migrated=true`, i.e. owned by the private Presentation. A preflight reset now leaves the actual Compose-owned WebView attached. The identity/attachment guard, synthetic-only restriction and all permission-removal controls stay unchanged. Official Android Compose `AndroidView` docs distinguish removal/release from attachment, and `ComposeView` lifecycle disposal is separate: https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/AndroidView.composable and https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views .

**Next gate:** dedicated exact-new-SHA [CI run 43683422134](https://github.com/imanipour2/kelpie/actions/runs/43683422134), separate Android/Lint/macOS; artifact hash + AAPT zero-permissions + S24 isolated reinstall and pull/readback; **new user on-device consent**; HOME 20/20 and Compose restoration; ChatGPT foreground 20/20 and restoration; negative STOP/keyguard/TTL. No P3 physical PASS or production release claimed.


## Final physical P3 bounded acceptance (2026-11-15): PASS — offline only

**Hardware:** Galaxy S24 Ultra SM-S928B, Android 17, Windows USB/ADB; explicit on-device local user consent. Exact **tested code** `429811e02797c2979f0d995422fc5dfa9f028266`. Four exact-SHA workflows PASS: [P3 43687811853](https://github.com/imanipour2/kelpie/actions/runs/43687811853), general Android `43687815291`, Lint `43687815319`, macOS `43687815292`. APK artifact `14151860167`; ZIP SHA256 `ac7751a68ad18a5e87d6492870a6efd08bf8394070717fdd2a39dc4542c6ceed`, APK SHA256 `46721016eb485f6219bbbdf5c8c647e5fbed4770b995c10bbdb8edae18d3abb0`, installed `base.apk` SHA256 exact match. No requested Android permissions; original unifiedverify/P0/P1/P2 retained.

**Capture:** 20/20 independent **fresh** pixel matches, every frame `activityVisible=false` with same TabStore WebView and document identity. Physical 23-event pre-restore log SHA256 `5aff327bd78d06c3f739a8e416b4d54b6001b8f4148aa524cf016db9f223c1ff`, archived locally before restoration.

**Restore:** User application foreground resumed, original WebView reattached via actual `TabWebViewContent` Compose host, `RESTORE_VERDICT matched=true`, Compose mount counter 1→2. Synthetic nonce, input, history and **scrollY=340** unchanged. The strict independent validator gave **PASS** for all 24 events with zero failures. Final redacted physical evidence SHA256 `819179d6d0b1324b81b5f56299550af4cd21718e6ae6822886614bbfb4498c1d` (6501 bytes), mirrored at [P3 RUN3 physical PASS JSONL](evidence/EF-OPS-KELPIE-S24-P3-RUN3-20HIDDEN-RESTORE-PASS.jsonl). Older FAIL evidence is retained for root-cause traceability.

**Archive:** The original verified latest CI ZIP in [Google Drive](https://drive.google.com/file/d/1u7CesTJFk-pxKDOBxv4zhF2NMJX9qrdR/view?usp=drivesdk) was read back by file metadata (30,390,751 bytes). [Enerfoam issue #98](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6891065951) carries the owner-facing complete evidence.

**Release boundary:** Hardware PoC is **ACCEPTED FOR THE BOUNDED SYNTHETIC RESEARCH MISSION ONLY**. No change to normal Kelpie or real tabs/sessions, no real-page screenshot proof, no merge/publish. Any production architecture, permissions, user-surface consent, iOS parity, rollbacks and staging acceptance require independent approval. `P3_OFFLINE_POC_ACCEPTED / PR6_DRAFT / PRODUCTION_RELEASE_NO_GO`.
