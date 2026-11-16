# P4 Android parallel QA identity — isolated staging slot

**Status:** DEVELOPMENT ONLY / DRAFT / NOT INSTALLED / NOT A RELEASE.  
**Base:** [PR #22](https://github.com/imanipour2/kelpie/pull/22) exact commit `5c99bb77103e81ea578607d5b55814cf68b15809`; stacked original P4 Draft [PR #17](https://github.com/imanipour2/kelpie/pull/17).  
**Tracking:** [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).  
**Owner authority:** [CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit) and Kelpie release policy EF-OPS-KELPIE-RELEASE-POLICY-001.

## Why a new identity is necessary

The Windows R5 build (three unpublished local commits based on P4) and already-installed P4 QA both use Android package `com.kelpie.browser.pcverify`, versionCode 29 and port 18437. Installing the former over the latter would replace an independently evidenced P4 baseline. The directly connected phone MCP currently responds as a **different**, older app `com.kelpie.browser.unifiedverify` version `0.2.6-qa`, build 26, port 18440; do not assume it is the P4 app.

Build an **additional parallel P4 test package** with a unique application ID. No device installation or runtime connection change is implied.

## Parallel identity and non-regression contract

| Attribute | Existing P4 QA | New parallel P4 QA |
|---|---|---|
| Gradle property | `-PkelpiePrivateCaptureQaProfile=true` | `-PkelpieParallelCaptureQaProfile=true` |
| Application ID | `com.kelpie.browser.pcverify` | `com.kelpie.browser.pcparallelverify` |
| Version name | `0.2.9-qa` | `0.2.10-parallel-qa` |
| Version code | 29 | 30 |
| HTTP port | 18437 | 18447 |
| P4 flag | true | true |
| Sensitive audio permission | removed | removed |
| Signing | debug | debug |

Use Gradle's existing `debug` profile and overlay, not a new application component or framework. Existing properties and release/default settings stay unchanged. The QA Android artifact must be verified by `aapt`, `apksigner`, 16KiB native ELF segment checks, permissions check, Gradle unit tests and source SHA provenance. Reject any mismatch.

## Scope and unresolved operational risks

- Both QA apps can be installed side by side by Android package ID, **but simultaneous listeners still need runtime port checks**. The parallel package binds 18447; no local port-forward is assumed.
- A separate package normally has a separate data directory and permissions, but simultaneous pairing, persistent notification IDs, Android app labels and task affinity still require actual device inspection before testing both at once.
- **Do not install**, clear data, force-stop the original, reconfigure Termux/runit tunnel, modify secrets or issue a release. A debug APK may be built and stored as a gated GitHub artifact only.
- The parallel package contains the P4 capture-only proof-of-concept, **not** the approved future one-consent Agent Runtime; do not represent it as R5.
- The Android/iOS parity rule is not waived. This QA-only additional identity remains on a Draft stacked branch pending explicit owner governance.
- Hardware acceptance requires authentic native consent/notification STOP, two genuinely paired principals, readback after HOME, locked/screen-off rejection, 20 fresh nonce-backed frames, resource lifetime/recovery and repeated 50-cycle QA. No protected system screen or FLAG_SECURE bypass.

## Test matrix and acceptance

| Gate | Environment | Required evidence |
|---|---|---|
| Android code tests | hosted CI | Gradle KtLint, JUnit, assembleDebug successful on exact branch HEAD |
| APK provenance | hosted CI | applicationId `pcparallelverify`, versionCode 30, port build config 18447, `apksigner` debug signature, no RECORD_AUDIO, native alignment, SHA256 |
| Baseline preservation | real S24/ADB | Existing `pcverify` and `unifiedverify` identity/versions/data untouched; no install unless separately approved |
| Native capture safety | real S24 | genuine per-session user gesture, status and STOP, lock/off negative tests |
| Agent Runtime | separate R5 source | latest laptop-only HEAD must be retrieved and reconciled; **not in this PR** |
| Merge/release | canonical governance | independent review, platform parity/explicit exception, rollback and release signoff |

**Result semantics:** `QA_ARTIFACT_BUILT` is not `DEVICE_INSTALLED`, and `CI_PASS` is not `HARDWARE_ACCEPTED`. Keep `SYNC_BLOCKED_MAIN / RELEASE_NO_GO` until all gates are closed.
