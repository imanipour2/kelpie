# R5 private QA / connected ChatGPT MCP tool-argument wrapper compatibility

Date: 2026-11-17. Physical device: owner Samsung SM-S928B Android 17, R5 isolated package.
Scope: stacked **draft** on PR #44, which is itself stacked on PR #42. No main, LIVE website or public/browser release changes.

## Root cause reproduced before change

The previously installed ChatGPT `kelpie_browser_control` plugin invokes `tools/call` with arguments shaped `{"request":{"version":1,"method":"capabilities","params":{}}}`. The R5 bridge expects the flat map `{"version":1,"method":"capabilities","params":{}}`. The R5 delivery guard tries to extract the top-level `method`, fails to recognize the wrapped metadata call as grant-exempt and denies its response with **HTTP 403 SESSION_REVOKED**. A local controlled request with the same MCP 2026-07-28 metadata and exact paired device transport returned **HTTP 200** in flat form and **HTTP 403** in wrapped form. Direct connected plugin also returned FORBIDDEN.

## Narrow correction and trust rules

- `R5PrivateMcpContract.toolArguments` normalizes only a single `request` wrapper on **kelpie_browser_control**, only version 1. Ambiguous mixed flat + wrapped payloads, non-object wrappers, version mismatches, wrappers on session/mutation tools and unknown tool names are fail-closed.
- The same normalization is shared by admission (`isGrantExempt`), explicit native consent generation extraction and the R5 tool dispatcher. No HTTP bearer or Origin/Host gate was modified. A correctly paired principal is still mandatory.
- Only static `capabilities` and `documentation` are exempt from a currently active owner-native browser grant. Real navigation, DOM, PNG, action and tab operations still require the current native Android-approved principal/generation, live tab/document and monotonic command sequence. This patch does **not** simulate a native approval, grant cross-app capture, or loosen sensitive input restrictions.
- Existing flat R5 clients retain identical behavior. Installed v1 connector methods beyond the R5-supported tool set may require a **separate** adapter/schema update; do not represent them as verified before E2E testing.

## Required acceptance

1. Compile and pass Kotlin formatting; run `R5PrivateMcpContractTest` including spoofing, missing generation and session guards on Windows; run applicable CI on exact commit.
2. Build isolated QA APK and check pinned SHA, app identity/Android v2 signer, ARM64/16-KiB safety and no audio permissions. Preserve prior signed QA APK for non-destructive rollback.
3. Install only into isolated owner-authorized QA package, read back installed APK SHA; ensure the dual-app profile is not cleared.
4. With no owner grant, direct connected `capabilities` must respond as *authenticated metadata* rather than 403. Every mutation/tab/image attempt remains denied unless the owner physically approves and gives a current generation. Confirm both flat and v1 wrapped requests over the real connector, not only localhost.
5. Separately finish visual ownership restore after return from background (existing PR #44 issue), BACK history RCA, call interruption, no-USB provider route, iOS parity/exception and independent security review. No automatic merge or release.

Audit trail: https://github.com/imanipour2/enerfoam-website/issues/98
