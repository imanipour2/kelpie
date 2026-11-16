# P4 Android: in-flight Nessie operation private-owner gate

**Mission:** EF-OPS-KELPIE-P4-HARDENING-EXEC-001 — source-only security fix, not the future Agent Runtime.  
**Baseline:** Draft PR #17 HEAD `7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee`.  
**Owner decisions:** [CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit), [EF-OPS-TOOLS-001 V2](https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit).  
**Operational issue:** https://github.com/imanipour2/enerfoam-website/issues/98.

## Independently observed source defect

The P4 `PrivateCaptureCoordinator` transfers the actual Java WebView to its private Presentation after authenticated and genuine on-device approval. While private ownership is active, ordinary remote DOM, navigation, click/type and screenshot routes must be denied; P4 is **capture-only**.

At pinned P4 HEAD, `ConnectBrowser.execute()` (Nessie path) checked `PrivateCaptureOperationPolicy.permits` only **before** its potentially suspended operations. `executeBrowser()` built a separate `HandlerContext(context.browserRetirement)` but did not inherit the original `context.privateCaptureRestricted` callback. Its `remoteDocumentGuard` checked job, account, tab and document but not private ownership. The `navigate()` authorization closure also ignored private ownership. Hence a command admitted before a private ownership handoff could continue after consent; it could attempt a WebView operation or return untrusted private DOM information. This is a **source-level race and potential boundary bypass**, not evidence of a successfully reproduced leak or unauthorized user action on an actual device.

## Small focused remediation

- `ConnectBrowser.execute()`: re-admit the final result after awaits, suppressing stale responses that complete after P4 handoff (matching `Router.handle` delivery gating).
- `open` and `select`: check the current P4 guard at the point of native tab mutation, not only the initial request.
- `isolated HandlerContext`: dynamically reuse the parent P4 restriction so `evaluateJS` and screenshots deny both before dispatch and on callback when ownership changes.
- `remoteDocumentGuard` and `navigate.authorized`: check private owner state as well as active job/account/tab/doc; no new generic authorization or bypass.
- `PrivateCaptureOperationPolicyTest`: extend explicit negative tests for `open`, `navigate`, `reload`, `snapshot`, `extract`, `click`, `type`, `scroll`, `tabs`, `control` under in-flight P4 transfers, plus owner-only capture allowance.

## Acceptance gates — no false PASS

1. Exact-head Kotlin/KtLint/Gradle unit tests and isolated Android QA APK CI **must** pass; check workflow logs and source SHA after the final change.
2. Unit policy tests validate admission semantics only. They do **not** prove complete asynchronous HTTP/MCP/Nessie route coverage or native WebView behavior; real two-principal, START→P4 transfer, delayed Nessie click/DOM, STOP/lock/screen-off and error recovery need instrumentation and S24 physical QA.
3. Any code merge must pass independent review, Apple platform parity or scoped owner exception, and complete P4 acceptance. This branch targets Draft P4 PR #17 rather than merging `main`.
4. Retain existing signed original app and separate QA packages. No silent install of laptop-only R5 APK (its package ID `com.kelpie.browser.pcverify` conflicts with existing P4 QA app). No release, site access or publication.

**Status:** `SOURCE_REMEDIATION_CANDIDATE / TESTS_PENDING / NO_HARDWARE_ACCEPTANCE / SYNC_BLOCKED_MAIN / RELEASE_NO_GO`.