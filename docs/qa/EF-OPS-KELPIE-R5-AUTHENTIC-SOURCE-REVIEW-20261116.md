# Kelpie R5 authentic Windows source — review and guarded engineering import

Status: **DRAFT / SOURCE-ONLY / NOT WIRED / NO INSTALL / NO MERGE / RELEASE_NO_GO**  
2026-11-16. Tracking: https://github.com/imanipour2/enerfoam-website/issues/98

## Authority and source provenance
- CDG `EF-OPS-KELPIE-P4-SESSION-CONSENT-20261116`, superseding clarification V2: one *real native* consent for a live process session and paired authenticated principal, all ordinary browser sites/tabs without repeat consent. STOP, lock, renderer lifetime, sensitive actions and screen-content boundaries remain independent gates.
- Local owner laptop source: `C:\Enerfoam-Kelpie-QA\agent-runtime-local-20261116`, verified **clean**, exact HEAD `0a49477b0bd584b7ae17474d0e0abf3913187f19` and exactly three descendants of `7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee`: `912c88a`, `d8fb2bb`, `0a49477b`.
- Original branch/worktree remains untouched. Local prerequisite Git bundle `C:\Enerfoam-Kelpie-QA\backups\r5-source-0a49477b-20261116.bundle`, verified by `git bundle verify`, SHA-256 `352df50e2dd14a6dfb76288b42c48a16915d5ebcee217e0b7251af0b9336319c`. This is a **partial bundle requiring the historical P4 base** and is intentionally not committed, uploaded or released.
- Twelve local Kotlin source/test files; initial high-confidence pattern scan found no private keys, GitHub/OpenAI tokens or literal authorization headers. This does **not** replace review of the complete repository or a credential-exposure audit.
- The remote review stack may be based on Draft PR25 `e9825ca9a44655ad4c5d77771029256a29b2a6d7` to retain PR22 P4 in-flight ownership fixes, PR23 parallel QA profile and PR24 read-only migration checks. Importing file trees on that new base does **not** preserve the original three commit SHAs as remote ancestry; original provenance stays in the verified local bundle.

## Inspected implementation and guard boundaries
- `SessionConsentCore`: ephemeral principal/generation; local UI-only approval callback; lock increments safety epoch; native STOP revokes. No durable consent tokens.
- `AgentAdmissionPolicy`: checks session/principal/generation/tab/document and rejects known sensitive categories, dangerous URL schemes; no fixed site allowlist. A generic website click can itself trigger high-impact actions; missing risk classification must be handled before connecting it to a real HTTP/MCP gateway.
- `AgentOperationRuntime`: serializes commands, requires gateway-verified principal, checks before and after asynchronous effects and returns UNCERTAIN when uncertain; STOP interrupts active jobs. Hardening here rejects zero/negative or reused sequence, duplicate command IDs even with newer sequences, retains bounded recent IDs and verifies lock/unlock safety epoch behavior.
- `VerifiedNativeBrowserAdapter` and `WebViewOwnershipState`: verify actual renderer identity/host epoch. **NativePort currently returns failure for execution/effect proof**; TabStore snapshot is read-only and does not implement tabless enumeration. No actual agent HTTP/MCP/Nessie handler, STOP indicator, service or private display is registered by this source import.

## High-priority fail-closed gaps before an executable build
1. Real authenticated MCP/HTTP/Nessie principal from paired connection metadata (not command JSON); replay, two-principal and binding lifecycle tests.
2. Native consent UI + persistent indicator + immediate STOP + app process session owner; revoke on process death, unpair and session end; screen lock denies capture until separately validated.
3. Source-level P4 PR22 in-flight security revalidation **after** await and before callbacks. Actual TabStore/WebView on main thread, renderer loss and ownership transitions. Navigation may change document epoch but must not allow stale element clicks.
4. Complete `NativePort` with verified effects, fresh pixels, tabs/new-tab/close-tab; no second WebView/cookie transfer, no other-app/notification/lockscreen/FLAG_SECURE capture.
5. Detect or independently authorize transactions, irreversible changes, credential export and publishing. The general consent is **not** permission for those effects.
6. Android/iOS parity (AGENTS.md) is still an unresolved independent approval gate; source-only Draft must not merge without actual parity or owner-authorized, narrowly scoped exception.
7. Hardware S24 tests: real one-tap consent, 20 fresh-content images, repeated navigation and 50 resource cycles, STOP/lock/Doze/foreground-to-background/two principals, battery and rollback. Hosted CI cannot satisfy this gate.

## Required exact-source checks
- `cd apps/android && ./gradlew --no-daemon :kelpie-browser:ktlintCheck :kelpie-browser:testDebugUnitTest` (hosted Linux/Windows with JDK17/Android SDK/CMake, not merely a static parser).
- Appropriate Android build checks on the exact PR SHA; no silent use of stale CI results.
- Gate inventory: R0 authentic source READ; R1 local protected backup VERIFY; R2 P4 security comparison IN PROGRESS; R3–R7 NOT ACCEPTED.

## References consulted
- Official Android WebViewClient `onRenderProcessGone`: https://developer.android.com/reference/android/webkit/WebViewClient
- Android WebView thread/lifecycle: https://developer.android.com/reference/android/webkit/WebView
- `ImageReader` acquireLatestImage/close and queue exhaustion: https://developer.android.com/reference/android/media/ImageReader
- Android 15 FGS runtime quotas: https://developer.android.com/develop/background-work/services/fgs/timeout
- Canonical ADR: https://docs.google.com/document/d/1SSDkaVlcbz7U8Pr0R5wvcKRFBB1EjRSOsyqRi9LCeCg/edit
- CDG: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit

**NO source-stage claim of working screenshots, live bridge, release, website publication or physical-device acceptance.**
