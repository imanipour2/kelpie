# EF-OPS-KELPIE-P4 — Apple request-owner isolation correction

**Scope:** narrow P4 security-parity fix; source-only, no release or deployment.  
**Branch base:** P4 draft PR #17 exact head `7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee`.  
**Tracking:** https://github.com/imanipour2/enerfoam-website/issues/98  
**Canonical constraints:** https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit  
**Status at authoring:** code changes committed; CI and full Apple XCTest execution not yet verified; `RELEASE_NO_GO`.

## Root cause and security impact

At pinned P4 source, Android `ConsentedCaptureRequestRegistry` already returns `false` without revocation on foreign/missing principal, mismatched request ID, or stale attempt; the legitimate owner's pending prompt/grant remains intact. The Swift counterpart instead executed `revoke()` on any failed `claimFrame` identity guard and on an unrelated `approveOnDevice` callback. Thus a separately authenticated but unauthorized party capable of reaching those methods, or a stale native callback, could clear another principal's consent state. This is a denial of consent availability, not evidence of unauthorized screenshot disclosure. No actual exploit or hardware reproduction is claimed.

## Correct invariant

1. Unknown request ID, wrong/absent verified principal or stale attempt: **deny with no change** to another owner's pending prompt or active grant.
2. Unrelated/stale local approval callback: **deny with no change** to the actual pending prompt.
3. Verified owning request whose WebView/tab/document differs or whose safety checks fail: **deny and revoke** via `ConsentedCaptureLease.claimFrame`.
4. Native explicit STOP: still uses `revoke()`, cancels both pending and approved state.
5. These changes **do not** implement the future one-consent-per-process Agent-Control Runtime; P4 30s/90s/20-frame QA boundaries remain unchanged.

## Changed files and regression coverage

- `core/apple/shared/Browser/ConsentedCaptureRequestRegistry.swift`: return without revocation for non-owner/missing identity and stale dialog callbacks.
- `apps/macos/Tests/ConsentedCaptureRequestRegistryTests.swift`: verify stale pending dialog cannot override new request; foreign request/client/attempt do not revoke owner; missing identity cannot revoke owner; legitimate changed-document claim revokes; premature claim preserves pending consent.
- No Android runtime, device package, WebView rendering, HTTP gateway, signing, secrets, release workflows, or website files changed. Android source already implements the corresponding negative-case behavior and has `wrongClientCannotRevokeOwner` / `wrongRequestCannotStopOwner` tests on the pinned P4 branch.

## Acceptance and evidence rules

- Verify Apple lint/project generation and iOS simulator compile for the **exact PR head** through GitHub Actions, and inspect actual job status.
- Check that the macOS CI workflow currently runs `make lint-swift` and Tuist generation, **not** automatic execution of these macOS XCTest test methods. Mark tests **ADDED / EXECUTION_NOT_PROVEN** until a genuine XCTest run is recorded.
- Recheck SwiftLint and source diff for unintended churn; root-cause and threat-model review required.
- P4 remains a Draft stacked dependency; this PR targets the P4 branch (not `main`) to avoid promoting unaccepted P4 code. Never merge into `main` until owner decisions, Apple parity and hardware gates are cleared.
- Do not confuse this correction with the laptop-only Agent Runtime R5 HEAD `0a49477b0bd584b7ae17474d0e0abf3913187f19`; it is separately unpushed.
- No WordPress TEST/STAGING/LIVE changes. `SYNC_BLOCKED_MAIN / RELEASE_NO_GO` until complete applicable acceptance and separate approvals.
