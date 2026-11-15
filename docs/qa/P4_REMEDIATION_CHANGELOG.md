# P4 hardening change log — unmerged Draft

Task: EF-OPS-KELPIE-P4-HARDENING-EXEC-001. Base `2bb8dc5fb710b9dcb3118cc84362af8423758d35` (inherits screen-off PR #13). Branch `fix/p4-authority-lifecycle-hardening-20261115`.

Changes recorded in branch commit history:
- Add explicit negative tests for non-owner client/request/attempt/replay, pending preservation, concurrent simulated principals.
- Alter registry `claimFrame` so a foreign attempt cannot revoke the actual owner; add `revokeOwned` and protect stale native approval callback.
- Require ownership for STOP and stop leaking renderer diagnostics on NOT_FOUND; add non-queueing single-flight frame mutex.
- Add PowerManager.isInteractive alongside KeyguardManager checks in coordinator and renderer.
- Harden renderer resource release so failure in one release step does not skip other steps; leave FAILED visible if cleanup incomplete; accept FAILED restoration attempts.
- Move QA pixel sequence increment after acquiring pending frame flight.
- Add QA-only shared operation policy and route admission checks for Router, BrowserGatewayBinding and ConnectBrowser.
- Add no-store header to authenticated HTTP responses.
- Extend resume recovery attempt when state FAILED.

Testing status: **TESTS AUTHORED / CI AND NEW HARDWARE QA NOT VERIFIED**. A red-first test commit was created before the source changes; its CI failure must be verified independently (do not invent a red run). Source fixes after that commit are candidates, not PASS.

Rollback: do not merge; restore/rebase to frozen base `2bb8dc5f` after preserving patches. Do not install or touch original Kelpie. Any failure in cleanup, release ownership or auth is SECURITY_NO_GO.

Related PRs: https://github.com/imanipour2/kelpie/pull/11 , https://github.com/imanipour2/kelpie/pull/13 ; evidence https://github.com/imanipour2/enerfoam-website/issues/98 .


## Follow-up source remediation (same mission, 2026-11-15)
- Reviewed exact CI job 132929555042: Android QA on previous source `5bf70056` **FAILED** `ktlintMainSourceSetCheck`: source imports in Coordinator/BrowserGateway and WebView restoration indentation; corrected in subsequent branch commits. Earlier source `42284e59` also failed both main and test ktlint. This does **not** assert the later CI is green.
- STOP cleanup executes using Kotlin `withContext(NonCancellable)` and then on the Android main dispatcher, so an incoming network coroutine cancellation does not interrupt the native release.
- Failed local start attempts immediately try `verifyRestored()` because foreground Activity cannot be assumed to emit another `onResume()`.
- Repeated Renderer STOP no longer repeats Compose reattachment while already RESTORE_PENDING.
- Admission TOCTOU: Router now gates **after** completing a handler as well as before; HandlerContext checks during JavaScript evaluation and screenshot invocation; Gateway checks again at direct execution and snapshot boundaries.
- Preserve unchanged original WebView, no new permissions, public example.com only; critical device tests remain pending.

Primary references: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-non-cancellable/ ; https://developer.android.com/reference/android/media/ImageReader ; https://developer.android.com/reference/android/content/BroadcastReceiver ; https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory ; https://developer.android.com/develop/adaptive-apps/cookbook/webview-state .
