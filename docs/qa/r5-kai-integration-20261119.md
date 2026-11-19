# R5 + KAI source integration — QA-only checkpoint 2026-11-19

## Exact source provenance
- Owner-directed work: EF-OPS-KELPIE-KAI-REUSE-EXEC-001; goal controlled S24 beta, not automatic release.
- R5 base: `68ade958af66b7f6de219abd2d9f9011478f5a40` from PR #66 (itself based on a QA branch, **not** main).
- KAI source: `f52acd0334a62bcb4716c9c82b1b3a687a151f19` from PR #69 (Draft, not merged).
- This branch overlays 26 byte-identical KAI files on R5. All source blobs are from the same GitHub repository, without any new browser, local server, screenshot service or authorizer.
- Changes are QA source-only. No APK installation, signing change, remote port/tunnel/Termux adjustment, WordPress change or main merge.

## Reuse and conflict gates
1. Retain R5 native verified-principal admission, consent generation, STOP/keyguard and private WebView frame provider. Those are absent from main/PR69 and must not be replaced with an unrelated runtime.
2. Reuse canonical KAI shared Snapshot/Native ElementRef registry, DOM redaction helpers, legacy interaction validation, and native gateway updates; source tests come with their implementation.
3. Deliberately retain R5 versions of overlapping `HandlerContext.kt`, `ScreenshotHandler.kt`, `core/android/build.gradle.kts`, `docs/functionality.md`. Keep original R5 LLMHandler until native image/annotation contracts can be reconciled. These are genuine integration conflicts, **not** approvals to bypass KAI protection.
4. Outstanding highest-priority blocker: R5-specific `R5WebViewActionScripts.SNAPSHOT` returns raw page text/query/labels and requires scoped P0 hardening. R5 screenshot bytes have **no demonstrated reliable native secret masking**. Do not expose real-account images as an accepted beta.
5. KAI's synthetic-only Canvas safety policy and R5's real PrivateDisplay cannot be merged mechanically; a single owner/observation/frame/consent boundary must be tested.

## Required proof before beta
- Exact SHA CI Android build, ktlint, JVM, Native, and browser fixtures; rerun rather than reuse results of either parent PR.
- P0 leak fixtures including HTML encoding, OTP/card/secret, URL; fail closed on unverified image pixels.
- Observation-based action and postcondition tests plus native principal/STOP and renderer ownership.
- Live S24: current installed package/version/signature readback, owner approval, same-WebView foreground/HOME screenshots and actions, STOP/lock, network handoff, 25 real lifecycle cycles; rollback before installer work.
- Current chat connection checks: S24 MCP `Session terminated`; Windows Browser Use `tunnel-client has not been seen for 300 seconds`. No claim of current phone availability.
- Android/iOS parity or formally approved scoped exception before main merge.
- Governance: Drive CDG -> GitHub PR/ADR/CI -> reviewed main merge -> readback/Live Log. Until reconciled, `SYNC_BLOCKED_MAIN`.

Statuses: SOURCE_INTEGRATION_CANDIDATE; CI_UNVERIFIED; DEVICE_QA_BLOCKED; OWNER_ACCEPTANCE_PENDING; NO_MERGE; NO_APK_INSTALL.
