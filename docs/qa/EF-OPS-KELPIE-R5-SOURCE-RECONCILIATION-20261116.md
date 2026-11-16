# R5 Agent Runtime — source-first reconciliation and engineering entry gate

Status: PROPOSED; R5 laptop source NOT YET READ; no code import, install, merge or release.
Authority: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit (EF-OPS-KELPIE-P4-SESSION-CONSENT-20261116 V2 and fork release policy). Tracking: https://github.com/imanipour2/enerfoam-website/issues/98.

## Product requirement
One explicit native user consent per real running process session permits an authenticated paired client to operate Kelpie-owned WebViews across ordinary tabs, HTTPS sites, DOM, click/type/scroll and private WebView screenshots without per-page or per-frame approval. Visible STOP, nonpersistent authority, exact principal binding and live revalidation before and after asynchronous actions are mandatory. No other apps, lock screen, system notifications, protected content or automatic authorization for publication, payment, deletion or credential export. Screen-off/lock capture denied until separate scoped review and genuine Android QA; process death invalidates session.

## Reconcile authentic local R5 source before implementation
Last documented Windows worktree: C:\Enerfoam-Kelpie-QA\agent-runtime-local-20261116; R5 expected HEAD 0a49477b0bd584b7ae17474d0e0abf3913187f19, three unpublished commits based on P4 7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee. Reported earlier: 18/18 JVM tests and Android build. Current source remains unavailable because Windows Browser Use tunnel is offline. Do not reconstruct these three commits from a historical ZIP or assume this expected HEAD still exists.
Read-only helper scripts/qa/r5-local-source-reconcile.ps1 verifies worktree path, Git HEAD and ancestry, clean state, commit count and local presence of newer stacked source, and generates a local-only JSON summary with no token, cookie, private site data, ADB or network calls. The script is not proof of actual source review until run on laptop. A missing new stacked commit in the old worktree is expected and calls for a separate working copy, not mutation of the original.

## Ordered engineering gates
R0: Read actual local R5 source and preserve its Git graph; do not force-reset or cherry-pick into the original.
R1: Create a separate locally stored protected backup, verify hash and provenance, audit deltas for tokens, credentials and local secrets before any reviewed transfer.
R2: Compare R5 code against P4 PR17 and newer independent security PR22, Apple policy PR20, isolated QA app identity PR23 and boot preflight PR24. Never restore stale Nessie in-flight private-owner races.
R3: In a new reviewable branch implement a single process-session grant, user-local STOP, principal-bound authorization at HTTP/MCP/Nessie command boundaries, after-await revalidation, tab/document identity and renderer ownership. Respect Android app-owned WebView-only capture, no system screen capture.
R4: Require source-level negative tests for replay, principal swap, STOP mid-operation, screen off/keyguard fail-closed, process death, asynchronous late completion and multiple site/tab normal operations under one consent.
R5: Run exact-HEAD Gradle, Lint and iOS parity tests or obtain separately approved scoped platform-parity decision; no main merge until resolved.
R6: Build uniquely identified QA APK, verify SHA, signing, permissions and 16KiB native alignment, then only under a separately approved physical device test plan assess authenticated native one-tap consent, 20 fresh frames, 50 resource cycles and lock/Doze/STOP conditions.
R7: Restore/rollback and explicit release authorization before any public release or old APK removal.

## Distinctions and unresolved conflicts
P4's historical example.com / 90-second / 20-frame grant is a deliberately limited QA fixture and NOT the new one-consent product contract. Android/iOS parity remains mandatory under AGENTS.md unless independently approved. GitHub CI PASS is not real S24 acceptance. A former pcverify APK shares package identity with old local R5; do not overwrite it. The previous 18440/18437/18447 tunnel routing is not required to develop the source and must not be changed now.
Status: R5_LOCAL_SOURCE_UNREAD / DESIGN_GATES_RECORDED / NO_AGENT_CODE_IMPORTED / SYNC_BLOCKED_MAIN / RELEASE_NO_GO.