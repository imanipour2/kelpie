# R5 isolated QA package and dual-module port identity — 2026-11-16

**Status: SOURCE-ONLY, DRAFT, NO AGENT ROUTES, NOT INSTALLED, RELEASE_NO_GO.**

## Root cause and repair
In Draft P4 parallel QA, the app module declared `LOCAL_HTTP_PORT=18447` but `DeviceInfo.collect()` in the core library uses **`com.kelpie.browser.BuildConfig.LOCAL_HTTP_PORT`**. The core library had no parallel-QA branch and thus defaulted to `8420`. APK package and provenance checks alone would falsely imply a runtime listener on 18447. This was discovered through source-level readback, not through a live 18447 listener test. Repair adds the matching 18447 branch to the core and a CI assertion against **both generated BuildConfig sources**.

## R5 source-only QA slot
- Gradle `-PkelpieR5SourceQaProfile=true`, debug only; package `com.kelpie.browser.r5sourceverify`, version `0.2.11-r5-source-qa`, code 31, port `18457`.
- `PRIVATE_CAPTURE_QA_ENABLED=false`: this source artifact deliberately **does not** expose an R5 command gateway, native consent UI, background screenshot service or P4 private capture.
- It cannot overwrite the installed `unifiedverify` (port18440) or `pcverify` (port18437) packages.
- QA profile flags must be mutually exclusive. No `RECORD_AUDIO`; debug signature, per-APK SHA, provenance, 16-KiB native alignment, and both BuildConfig port values are mandatory in CI.
- No APK is installed and no device process is started by creating this package. This artifact is a compile/safety baseline for future R5 wiring, **not** a functionality acceptance build.

## Device readback and protected rollback
Samsung SM-S928B Android 17 / API37: ADB showed `device`; `unifiedverify` versionCode26 and `pcverify` versionCode29 installed; `pcparallelverify` absent. Device dozing/locked during readback. Both installed base APKs were pulled read-only to owner laptop; hashes are tracked in PR discussion. **APK backup does not back up app-private data**, so no overwrite, uninstall, clear, migration or install may be inferred as authorized.

## Required acceptance gates
1. Verify exact-head CI package/version/debug signature/no RECORD_AUDIO/JNI/native ELF alignment, and `core` plus `app` BuildConfig port agreement (18457 R5; 18447 P4 parallel).
2. After explicit device-test plan and local owner consent, independently confirm listener and `get-device-info` port on genuine S24; ADB presence alone is not this proof.
3. Finish session-grant UI, per-process principal/STOP, WebView lifecycle, authenticated HTTP/MCP/Nessie dispatch, image freshness, 50-cycle hardware QA, lock/Doze, two-principal and rollback.
4. Preserve AGENTS.md iOS/Android parity or secure separately approved exception before merge; release approval remains separate.

References: Android BuildConfig ownership in `core/android/src/main/java/com/kelpie/browser/device/DeviceInfo.kt`; `EF-OPS-KELPIE-P4-SESSION-CONSENT-20261116` V2 in CDG; [issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).
