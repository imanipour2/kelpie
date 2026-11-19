# Kelpie Android Wave 1 implementation and acceptance record

Mission: **EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001**. Date: 2026-11-19.
Disposition: **BETA_INSTALL_NO_GO / SOURCE_REVIEW_ONLY / CDG_PENDING /
SYNC_BLOCKED_MAIN**. This record does not authorize a merge, installation,
uninstallation, parallel app, credential reset or tunnel change.

## Authority, scope and dependency inventory

The owner's [execution document](https://docs.google.com/document/d/1w7-dRc3HqGXX8R_5a7XD3rbVRhwNSIVvoGUDvQCmMo8/edit)
authorizes P0 source remediation on the existing draft chain. Its separate
signing, recovery, physical acceptance and installation gates override automatic
release/deployment instructions in the repository. Android/iOS parity or an
approved S24 exception is still a merge/release gate.

| Reference | Readback for this mission | Authority boundary |
| --- | --- | --- |
| Kelpie main | `91dadda8caebe69394e4a6b4589892ae4a495e54` | Merged code; none of this draft work was merged |
| [PR #66](https://github.com/imanipour2/kelpie/pull/66) | Draft/open, `68ade958af66b7f6de219abd2d9f9011478f5a40`; base owner-friendly QA branch `e9ee1617d7760e4f3032e008be0aad19489af7ad` | Mother renderer/status dependency |
| [PR #69](https://github.com/imanipour2/kelpie/pull/69) | Draft/open, `f52acd0334a62bcb4716c9c82b1b3a687a151f19`; base main | KAI reuse/security source; not assumed merged |
| [PR #70](https://github.com/imanipour2/kelpie/pull/70) | Draft/open, `fc25806dd9940ef13fc7a3b06b77c96a3977b6c9`; base #66 | Integration dependency |
| [PR #71](https://github.com/imanipour2/kelpie/pull/71) | Existing branch `fix/r5-mother-atomic-webview-switch-20261119`; base #70 | All Wave 1 commits remain here |
| Original #71 source | `68b7c2ccdf6a9e45d8198f07613c635b6d3e2367` | Original 13-workflow failure baseline |
| Isolated style correction | `37b9ca372c0a3e81e518e8d2ac0185283c15d3f3` | Independently retested: 12 successes, 1 existing conditional formatter skip |
| Installed phone APK | VersionCode 40, digest below | Actual installed state; not any PR/CI build |

Read governance inputs: START HERE, Master Index, official CDG, the six-phase
EF-IMP-V2.1-REV2 plan, Blueprint V2, [EF-GOV-DOCOPS-001](https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit),
[Live Master Log](https://docs.google.com/document/d/1HkCCpNjV7L9UoVCN6pJ_fhAsqBcn4OkNeIcOt_Iys8U/edit),
the session ADR, KAI-001, RCA and relevant Issue #98 decisions/comments. Kelpie
`AGENTS.md`, `CLAUDE.md`, `core/README.md`, changed files, workflow definitions,
and the dependency/main refs were also read. The requested
`docs/policies/development-and-release.md` was absent; it is not claimed as read.
Enerfoam's current authority file is `governance/CURRENT-AUTHORITY.md`, not a
root-level file. Its main decision mirror does not yet incorporate the Kelpie
Drive decisions; source edits do not silently ratify or repair that governance
gap. No extra decision authority or duplicate historical master was created.

## Correction matrix

| Correction | Root cause | Production files / reused boundary | Executable evidence | Device acceptance still required |
| --- | --- | --- | --- | --- |
| CI baseline | Eight Android workflows independently stopped at formatting in four files; macOS failed to acquire a runner | Existing ktlint configuration; no checks disabled | Per-job log/excerpt and full 37b9 retest in [CI baseline](wave1-ci-baseline-20261119.md) | CI is not an installed APK |
| Windows shutdown | Existing close-state transition allowed reentrant teardown; the observed process-exit failure is separate and its first false HTTP-drain check does not establish continued owner-loop progress | `CloseLifecycle`, Windows shutdown boundaries, opt-in `ShutdownTrace`, existing close lifecycle/control-surface tests | Old state-machine unit reproduces double persistence/teardown; guarded unit passes; 8a hosted acceptance succeeds but Windows remains 73/74, requiring numeric drain/owner-progress evidence | Independent desktop regression scope; no S24/device changes |
| Metadata and consent | Browser grant was tested at the wrong boundary; private state could deadlock negotiation while foreground could bypass owner authority | `HTTPServer`, `Router`, `AuthenticatedRequestDispatcher`, `R5McpBootstrapGate`, `AgentSessionCoordinator`, existing native consent reducer | Dispatcher suspended-race tests; exact-method gate tests; compiled real HTTP/JNI wire fixture | Authenticated metadata at STOP/lock, one native prompt and actual response delivery |
| Principal and protocol | R5 invalid/missing bearer could fall back to localhost; mother/private protocol sets were conflated | `AuthMiddleware`, `AccountPairingAuthorization`, `AuthenticatedClientContext`, `R5RequestAuthorization`, `BrowserGatewayBinding` | Original `AccountPairingTests` retained; scoped protocol/authentication matrix and real native gateway tests | Same live paired client and current bearer, without exposing/resetting credentials |
| Revocation | A new grant or unlock could accidentally validate an old asynchronous operation; unpair did not retire every native reference | `SessionConsentCore`, `AndroidAgentProcessSession`, `R5ObservedAgentClients`, `PairingStore`, `TrustedComputers` | STOP/reapprove, lock/resume, unpair, pre-dispatch credential revocation and queued-Main consent tests | STOP-in-flight and lock/unlock on the exact APK |
| Safe dispatch/errors | Broad consent and extension aliases could expose sensitive routes; exceptions/parse failures gave misleading outcomes | `R5RemoteOperationPolicy`, `HttpJson`, existing Router and native gateway | Denied-handler counters, exact allowlists, structured unknown/correlation errors, strict JSON tests | Concurrent server/probe/MCP timestamps; historical HTTP 500 cause remains unproven |
| Atomic tab selection | Logical selection could lead the physical WebView/viewport | `R5AgentTabCommands`, `R5PrivateTabTransaction`, `R5NativeBrowserPort`, existing transfer mutex and `WebViewOwnershipState` | Real reducer/fake-host fault injection: zero viewport, commit failure, STOP/lock, cancellation and failed rollback | 20 transfers across 3 real tabs; preserved history, forms and scroll |
| Action identity/effect | Callback/value equality did not prove the same document or DOM node was acted upon | `VerifiedNativeBrowserAdapter`, `R5WebViewActionScripts`, `R5ElementActionProofScripts`, reused `InteractionScripts` | JVM document replacement/pre-dispatch assertions PASS; production-JS Chromium fixtures present, execution PENDING CI | Real click/type/select/scroll and uncertain-effect receipts |
| Queued action/navigation | Queued work could outlive its original grant; an old completed DOM or dispatch-only receipt did not prove navigation | `BrowserOperationGuard`, `HandlerContext`, `BrowserGatewayBinding`, `NavigationHandler`, `NavigationEffectProof`, `ScrollEffectEvidence` | 4 queued-guard, 9 navigation-oracle and 5 scroll-oracle tests PASS in the final JVM suite; two WebView navigation fixtures COMPILED / NOT_RUN_ON_ANDROID | Actual native navigation/history, lock races and physical touch/scroll effects |
| Private frame freshness | New native overlay did not prove a new document frame; callbacks could race STOP; row padding/recycling assumptions were unsafe | One `PrivateWebViewDisplay`, `R5PrivateFrameAcquisition`, `R5RgbaFrameBuffer`, `R5PageFrameProof`, `R5FrameProofVerifier`, existing OneShot/epoch helpers | Bounded/cropped planes, both-source nonce pixels, gray/transparent rejection, timeout/cancellation, 200 callback/STOP races; 25-frame ImageReader fixture COMPILED / NOT_RUN_ON_ANDROID | 25 fresh WebView frames; 50 display create/release cycles; no resource growth |
| Foreground frame freshness | Canvas/visual callback success was not proof of the copied producer frame | `ForegroundWebViewCapture`, same document proof/blank detector/OneShot holder, `HandlerContextCapture` | Acquired-pixel proof, exact viewport/owner/secure/native-overlay admission, cancellation ownership; Android fixtures COMPILED / NOT_RUN_ON_ANDROID | PixelCopy errors/late callback, actual S24 surfaces, foreground transitions |
| Privacy | Raw form/URL/title/selector data and unproved page pixels could leave through different handlers | Shared `ObservationPrivacyScripts`, `WebViewCapturePrivacy`, `CapturePrivacyEvidence`, `TabMetadataPrivacy`; `ScreenshotHandler`, `LLMHandler`, tab/navigation handlers | Pure JVM privacy/document/geometry tests PASS; production-JS Chromium fixtures PENDING CI; screenshot aliases share the same gate | Real-site reliable pixel redaction remains unimplemented; real pages are refused |
| Renderer/lifecycle | Dead WebViews and late callbacks could retain/revive obsolete references | `TabStore`, `TabWebViewClients`, `HandlerContext.nativeRendererGone`, `R5AgentCoordinator`, stateless `R5LifecycleSignals` | Exact-instance callback guards, native retirement, cancellation/ownership tests; instrumentation COMPILED / NOT_RUN_ON_ANDROID | Real/peer renderer death, HOME, rotation, lock, networking, Doze and reboot |
| Native First/status | Termux fallback was presented too early; local readiness was confused with remote connection | `AgentConnectionCenterSheet`, `R5TermuxOwnerReconnect`, `ConnectionStatusSheet`, `McpActivityEvidence` | Explicit opt-in/fixed-command tests; degraded-probe truth tests | Owner one-tap journey; Termux callback on Android 17 if separately enabled |
| Candidate/install safety | Stale hard-coded APK/source/cert plus backup checkbox could bypass meaningful recovery proof; exact-candidate post-install acceptance was also demanded before the first installation | MotherNext profile, existing sidecar workflow and `kelpie-s24-mothernext-18463.ps1` | Actual script with SDK/ADB/GitHub boundaries mocked; digest/signing/recovery/CI/device/storage/race negative cases, with pre-install and post-install phases kept distinct | Same signer, cold restore, rollback and independent owner approval before installation; actual candidate acceptance afterward |

For detailed file/test inventories see [admission and install gates](r5-wave1-admission-and-install-gates-20261119.md)
and [capture research/acceptance](wave1-research-20261119.md). Review the PR diff
for every changed file. No test was deleted or disabled to make a pipeline green.
The only expected workflow skip is an existing, differently scoped source
formatter; ktlint checks and all test suites still run.

## Functional commit mapping

The final combined tree is validated as one candidate; intermediate commits are
review boundaries and do not claim independent installation acceptance.

| Scope | Commit | CI disposition at this pre-CI source snapshot |
| --- | --- | --- |
| Authentication, original-grant admission, queued JS and Native First connection | [`b15242242a88`](https://github.com/imanipour2/kelpie/commit/b15242242a8828e942f4eae019f0a83904607bf5) | Final combined-head matrix PENDING |
| Atomic WebView ownership, capture/privacy/lifecycle, navigation/scroll | [`81020c024df7`](https://github.com/imanipour2/kelpie/commit/81020c024df7e3073d7621b909fdf3954dee7270) | Final combined-head matrix PENDING |
| Canonical R5 click/type effect receipts and native gateway enforcement | [`1d5050ba462f`](https://github.com/imanipour2/kelpie/commit/1d5050ba462f9c2f52961f0d8342f9eb6bf20d06) | Final combined-head matrix PENDING |
| Candidate provenance, full browser/emulator workflows, install guard and this report | Commit containing this record, on existing Draft PR #71 | Actual CI/manifest readback will be appended to PR #71 and the execution document |

## Runtime invariants and deliberately unavailable behavior

Authentication precedes any route side effect, and is rechecked after queued
Main-thread consent work and before response delivery. Only four exact MCP
metadata methods and native consent negotiation are grant-free, for a verified
non-localhost principal. A metadata exception cannot call a browser tool or
acquire a WebView. Foreground and background browser work both bind the original
principal, generation and lock safety epoch. Internal gateway flags do not grant
authority. Revocation after native request display yields an uncertain/denied
result; it never fabricates `pending=false` from a parse error.

Tab selection moves the same WebView before committing logical identity. The
existing mutex, consent reducer and ownership state remain authoritative. A
failed rollback revokes readiness. The final selection proof includes the exact
selected document and native renderer, plus the original grant; navigation or
lock/resume between transfer and verification cannot become success. New/close
operations in PRIVATE ownership are denied pending a proved native lifecycle.

Action scripts use existing shared guards and real post-action evidence. The
bounded, request-local mutation proof holds the actual node/selected option,
expires, and is consumed once. An equal-valued replacement node cannot prove the
original mutation. It is not a persistent page observer or a second browser
engine. Tokens, field values, internal body fingerprints and private attributes
are not emitted in action receipts. Canonical R5 click/type also require an observed local DOM/control effect; the
native gateway refuses a mere dispatch acknowledgement. Asynchronous click
effects without a synchronous visible witness remain uncertain and require a
fresh observation. This does not claim business-task completion. Canonical
scroll independently reads the same measured document before and after dispatch,
including RTL negative offsets and clamped boundaries; a no-op is not movement.
Navigation requires actual native history/document transition and stable completed
readback. A dispatched action without a verified effect remains uncertain, and
must not be blindly replayed.

Private images require both actual native-host and document nonce pixels;
foreground PixelCopy requires the actual document pair. Callback arrival alone,
changed PNG digest, a new native overlay or synthetic marker colors cannot make
an old/gray page valid. Proof regions are excluded from scene checks and covered
before encoding. Image timestamps are compared within their producer only;
receipt acquisition time is separately labeled monotonic time. Frames and image
resources have explicit disposal ownership on timeout, cancellation and STOP.

**Privacy remains a functional limitation:** only a natively registered reviewed
synthetic fixture is admitted to pixel export. A matching URL or page-created
attribute cannot enable that path. Real-page screenshots return `NEEDS_HUMAN`;
unsupported full-page stitching returns a typed refusal. This prevents sensitive
pixel export but does not complete usable real-site redaction or the installation
acceptance gate. DOM observations apply the shared policy, including origin-only
URLs and structural selectors; raw browser title/path/query/form exports are
not substitutes for approved observations.

Renderer death synchronously invalidates pending private and foreground proof
before coroutine cleanup. Every callback must match the exact current BrowserTab
and WebView. The dead view is detached/destroyed and references retired. Recovery
is a blank new WebView identity, with no automatic old-URL reload, background
Activity launch or restored process consent. Request-local cleanup timers are
fallback cleanup only, not a real-time guarantee while a WebView is suspended.

## Actual installed-device evidence

Read-only ADB/device observation at **2026-11-19 15:12:06 UTC**
(18:42:06 Asia/Tehran), independently of GitHub:

| Field | Observed value | Limit |
| --- | --- | --- |
| Device | Samsung SM-S928B, Android 17 / API 37, user 0 | Authorized ADB device; no install/reboot/force-stop performed |
| Package | `com.kelpie.browser.r5mothernextverify` | Earlier same-session user-0 inventory contained exactly this Kelpie package; another protected profile could not be enumerated |
| Version | `40` / `0.2.20-r5-mother-viewport-sidecar-qa`; targetSdk 34 | Existing live APK, not Wave 1 candidate 41 |
| Installed base.apk SHA-256 | `3ea605c53f6e696e465256880e602a199f01530d5121f0ad5444381d7afdaf34` | Computed on device; APK bytes were not exported |
| Known signer SHA-256 | `234c761b48afa2955787dbad8dbabd3fd9f667ef3ff35cd9ee7ed127246fe287` | Prior verified exact APK evidence; current matching byte digest links to that evidence, not a new SigningInfo read |
| WebView provider | `com.google.android.webview` `157.0.8103.50` | Actual installed provider |
| Package memory | PSS 150505 KiB; RSS 210220 KiB; swap PSS 83630 KiB | One baseline sample, not a leak/performance acceptance test |
| Native object counts | 7 WebViews, 19 Views, 4 ViewRootImpl, 1 Activity | Object counts do not prove tab actions or successful capture |
| Local diagnostics | Same-session port-8080 `/health` 200, `/readyz` 503 | Degraded readiness evidence; does not establish tunnel-down |
| Remote control probe | Latest read-only capabilities request returned upstream HTTP 403 | No authorized E2E browser operation is claimed for this session |

Raw port-18463 socket probes produced no parsed HTTP status; they are inconclusive
and are not reported as success or proof that the service is down. A connector
403 is an upstream denial, not evidence of a broken tunnel. Previous successful
tab/DOM/image observations in Issue #98 are historical and do not validate this
new source or the current session.

A separate read-only refresh at **2026-11-19 16:55:39 UTC** (20:25:39
Asia/Tehran) reconfirmed the same package, version 40, exact installed APK digest,
WebView provider 157.0.8103.50, and only that Kelpie package in user 0. It does not
retimestamp the earlier memory or transport samples. A subsequent package-manager
dump at **17:04:23 UTC** reported `apkSigningVersion=2` and
`past signatures:[]`. This is actual package-manager signing metadata, not a new
certificate SHA-256 or an invented SigningInfo API result. The known certificate
fingerprint remains linked through the identical previously verified APK bytes.

Automatic approval review rejected a proposed installed-APK export because
metadata could be checked without copying its payload to the connector. That
operation was not performed or retried under another name. On-device hashing
was the safer alternative. An existing owner-local rollback APK with the same
digest can be inspected without exporting installed APK bytes.

No app was installed, removed, cleared, force-stopped or switched to another
identity. No pairing token, port 18463/8080, tunnel or Termux setting was changed.
No personal screenshot, account content, OTP, bearer or cookie was placed in
source, logs, CI artifacts or Drive.

## Build and test evidence boundaries

The original full CI matrix was read job by job and independently rerun after
the isolated style correction. The later functional changes must pass their own
exact-head matrix. CI run links and the final APK manifest are recorded in PR #71
and the execution document after readback; the earlier 37b9 results cannot prove
the functional commit.

Local checks use real project dependencies and retained tests. An initial full
Android run compiled production and instrumentation sources and ran 487 JVM
tests, finding one real private-protocol scope regression. The implementation
was corrected while retaining the original assertion. The next full local Gradle
run passed 488 tests (zero failures/errors/skips). The final functional local run,
including queued authority, navigation/scroll and target-handoff tests, passed
**513 JVM tests in 90 suites, with zero failures/errors/skips**, plus ktlint and
instrumentation compilation. The final notification-wire fixture correction was
formatted and compiled separately. Shared/CLI production build and lint passed;
the actual native MCP gateway, extensions and registry passed all three CTest
targets. The original installation guard passed 39 scenarios against mocked
external boundaries. The phase correction retained those cases and passed
**88 scenarios** against the actual script, including read-only preflight
without install authorization, candidate-bound post-install refusals and
referenced-evidence revalidation after CI. None of those checks touched the
owner's installed package.
Compilation of an Android test is not its execution. Exact-head CI results,
including the real Chromium and isolated API 34 Android fixtures, are recorded
with the final candidate after they actually run.

Local full Node/shared execution encountered the environment's forbidden network
interface/socket operation in an existing CLI test. That test was retained;
the added exact-head workflow runs the original complete `pnpm test` on a normal
runner and executes production Android JS in pinned real Chromium fixtures.
The actual native C++ gateway, native extensions and MCP registry tests also run
there. A local Chromium launch blocked by the environment is not a browser-test
pass. Local JVM proxy trust configuration and compiler-process recovery are
environment repairs, not weakened TLS or altered production build rules.

## Single candidate and remaining installation gates

### First exact-head CI execution and runtime follow-up

The first functional head, `24af91bce6ae8b56c2f9adfcc2712b9e374ee165`,
completed the [actual contracts job](https://github.com/imanipour2/kelpie/actions/runs/44557671919/job/135678045755):
94/94 shared, 664/664 CLI, 31/31 real Chromium cases, 3/3 native CTest targets,
and native-selector DOM conformance. The Chromium run had zero failed, cancelled
or skipped cases. The original local mDNS environment restriction is covered
by this successful complete hosted CLI execution.

Its [designated MotherNext build](https://github.com/imanipour2/kelpie/actions/runs/44557672094)
also passed. Independent artifact-byte inspection found version 41, the same
package, 83,341,221 APK bytes, SHA-256
`0dc2aa9fcd7faebff0570fd066edd476bffce83a3b67af936cc12631f88b7d1d`, and a
valid v2 signature with certificate
`b7914248633e63424e15345a73364ce4c8bbcb5d8b53aec1855aaa19aa3296cc`.
The artifact and manifest hashes matched; v3/v3.1 were false. This certificate
does **not** match the installed certificate. This first artifact is historical
once a later source/CI correction is pushed; it is not silently relabeled as
the final candidate.

The new Android fixture job reached APK assembly but its emulator boot exhausted
the 480-second bound. Instrumentation did not execute and the missing-XML gate
correctly failed. A second demonstrated workflow defect then blocked evidence
publication: offline-device diagnostic ADB calls had no timeout. The follow-up
binds each ADB operation to a deadline, detects an exited emulator process, and
prints the synthetic boot log on failure. All 11 fixture cases and the zero-skip
gate remain required. That first attempt's missing emulator log was not used to
invent a GPU, SDK or product-code cause.

The bounded follow-up on `b667bff9882c2f6dc99e69a531a77fdb5e046c28`
then exposed the actual registration/discovery failure in
[job 135693786121](https://github.com/imanipour2/kelpie/actions/runs/44561116013/job/135693786121):
emulator 37.2.13.0 reported `Unknown AVD name [kelpie-wave1-api34]` and a missing
`kelpie-wave1-api34.ini` in its searched AVD home. It exited before Android boot;
none of the 11 instrumented cases ran. Artifact `14548429900` was published
and its ZIP SHA-256 independently verified as
`a535105cc8ddfd27578d881c88b31a6624a3ef0cb3c08fbc81ad14a83bd99e43`.
The empty class counts correctly failed the required-test gate.

This follow-up gives `avdmanager` and `emulator` the same task-local
`ANDROID_USER_HOME` and `ANDROID_AVD_HOME`, verifies both generated INI files and
the exact `emulator -list-avds` name before launch, and proves boot before the
long APK assembly. The official
[Android SDK environment-variable reference](https://developer.android.com/tools/variables),
updated 2026-10-05 and read 2026-11-19, documents these preference/AVD locations
and lookup precedence. The API 34 image, software graphics, KVM requirement,
both existing ABIs, all 11 tests and zero-skip gate are preserved. This is a CI
AVD-discovery correction; its success still requires actual hosted execution.

Actual readback on `89ecc0e133928c7f3d68e164f99d49d1c3daa140` then
proved this correction: the same
[Android fixture job](https://github.com/imanipour2/kelpie/actions/runs/44564567815/job/135702668530)
booted successfully in 37 seconds (16:19:37-16:20:14 UTC), before APK assembly.
That establishes AVD registration and boot, not an instrumented-test pass.
The complete contracts job again passed 94 shared, 664 CLI, 31 Chromium and
3 native CTest cases plus selector conformance. Final Android execution and
the following install-phase correction require their own exact-head readback.

That hosted Android run subsequently executed **all 11 cases: 5 PASS, 6 FAIL,
zero errors or skips**. The real HTTP/JNI wire case and both ImageReader cases
passed; two privacy cases passed, four privacy cases and both navigation cases
failed. The result ZIP `14552585393` was independently verified with SHA-256
`e28b6aea26ccafed970c5883960cd718d2c6905e5d00cc92e268026fee49951f`.
The XML gate correctly rejected this result rather than treating a successful
boot or APK assembly as a runtime pass.

Two concrete fixture defects were then corrected without weakening production
admission or deleting any of the 11 cases:

- Navigation's `MockWebServer.url()` resolved a canonical host name inside
  `withContext(Main)`, producing `NetworkOnMainThreadException` before WebView
  assertions ran. The fixture now starts/shuts down its literal loopback server
  on IO and passes immutable URLs into Main. WebView creation, `loadUrl`, native
  reads and the original effect assertions retain their UI-thread boundary.
- The native reviewed-image loader supplied a null `historyUrl`, although its
  base URL was the fixture URL. Android documents a null history entry as
  `about:blank`; the callback alone did not establish the native URL required
  by the unchanged privacy gate. The loader now gives both identities the same
  explicit URL and verifies native/DOM readiness before returning. The spoof
  test also waits for matching base/history URLs and still requires a refusal
  for missing native provenance. This fixes test infrastructure, not real-page
  redaction. The official
  [in-app WebView content guide](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
  and [API reference](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String))
  were read 2026-11-19 and applied to this fixture boundary.

Runtime success of these corrections is not inferred from compilation; their
next exact-head Android execution is recorded in PR #71 and the Drive readback.

The [actual 865fcdf5 Android rerun](https://github.com/imanipour2/kelpie/actions/runs/44567636742/job/135713645341)
then passed **all 11 instrumented cases, zero failures, errors or skips**:
one real HTTP/JNI wire case, six privacy/foreground WebView cases, two real
Surface/ImageReader cases and two WebView navigation cases. The strict XML
summary and artifact `14553856496` are tied to
`865fcdf58353a1ba0c5fc95f7babc6504b27e6a4`. The 25-frame Surface producer fixture
does not claim 25 private WebView frames or physical S24 acceptance; the summary
explicitly retains `physicalS24Acceptance: NOT_TESTED`.

On the same head, two other Android jobs failed while resolving dependencies,
before their Kotlin tests could run: MotherNext
[job 135713645736](https://github.com/imanipour2/kelpie/actions/runs/44567637319/job/135713645736)
could not find several AGP transitive Maven artifacts during configuration, and
background profile
[job 135713644523](https://github.com/imanipour2/kelpie/actions/runs/44567636786/job/135713644523)
could not resolve its runtime classpath. The declared dependencies/repositories
were unchanged. No global outage, deleted upstream version or source compile
failure is inferred from those logs; the next complete head must rerun them
with the same versions and normal TLS. This attempt produced no designated
MotherNext candidate, despite the independent 11-case runtime pass.

### Existing Windows shutdown regression exposed by the full matrix

The complete cross-platform matrix also found a reproducible Windows failure,
not a build/download timeout. On both the first functional head and `b667`,
CEF/application compilation completed and **73 of 74 CTest targets passed**.
In the [actual b667 job](https://github.com/imanipour2/kelpie/actions/runs/44561116178/job/135690380814),
`close_tab_control_surface_test` passed background, active and last-tab closure,
replacement-tab, live `get-tabs` and readiness assertions. Only process exit
after `close-browser` and final readiness-file removal failed.

The direct route is `DesktopHttpServer` -> `DesktopRouter`/guard -> `ShellHandler`
-> the Windows `WM_CLOSE` request. The test does not construct a browser-control
gateway or execute the Wave 1 selector/action scripts. Those Windows/desktop
engine files were unchanged relative to the PR #70 base. Nevertheless, the old
test discarded the close response and recorded no shutdown barrier, so its log
did not prove which stage prevented completion. Response acceptance and
token-free stage diagnostics are required before attributing that CI failure.

The pinned build uses CEF `152.0.6+g708dc14` / Chromium `152.0.7977.83`.
Its [upstream shutdown implementation](https://github.com/chromiumembedded/cef/blob/708dc14/libcef/browser/main_runner.cc)
drains pending UI and IO tasks during shutdown; the
[official CEF integration guide](https://github.com/chromiumembedded/cef/blob/master/docs/general_usage.md)
also documents the main-thread/message-loop boundary. These sources were read
2026-11-19. A bounded regression against the old `CloseLifecycle::Advance`
reproduced double persistence/teardown: nested persistence produced counts 2/2,
and nested shutdown produced counts 1/2. A scope-bound attempt guard now makes
both cases 1/1 and preserves incomplete-close retry and exception unwinding.
Those actual portable unit checks passed after the fix.

The existing process test now asserts HTTP 200 plus success/accepted for the
close request, then retains the unchanged exit/readiness assertions and time
limit. A fixed-enum, bounded stage trace is off by default and enabled only in
the isolated child test's explicit temporary profile/environment; no owner
environment, URL, token or browser content is recorded. The observed hosted
failure still requires its next Windows run. The independently proved reentry
defect is not silently relabeled as that failure's root cause without runtime
evidence. No readiness file is deleted early to satisfy the test.

The [actual 8a Windows rerun](https://github.com/imanipour2/kelpie/actions/runs/44570167504/job/135722707481)
still passed **73/74** targets. Its new assertions proved that `close-browser`
returned HTTP 200, `success=true` and `accepted=true`. The trace then recorded
`close-requested`, `begin-drain` and the first `waiting-http-drain` at 10545 ms;
there was no persistence or CEF-teardown stage. Because consecutive stage labels
were deduplicated, that single entry does not prove repeated HTTP-drain polling
for the full 60-second wait: owner-loop progress could itself have stopped.
The same run's real HTTP-server drain test passed. The next change therefore
adds only bounded numeric admission/listener snapshots and owner Tick/pump
progress in the isolated diagnostic path; it does not guess a new shutdown
policy, bypass the drain barrier, or weaken the original process-exit assertion.

Primary upstream reports were also compared on 2026-11-19:
[cpp-httplib #1645](https://github.com/yhirose/cpp-httplib/issues/1645)
concerns stop-before-listen startup; Kelpie already waits for listener readiness
and the observed control request completed. [Issue #2373](https://github.com/yhirose/cpp-httplib/issues/2373)
concerns newer timed thread-pool waits with an older MSVC implementation; it is
not established as this failure's cause. The [CMake 4.5.1 FetchContent reference](https://cmake.org/cmake/help/latest/module/FetchContent.html)
confirms that the first declaration wins, so a later Windows dependency tag is
not proof of the effective built version. Diagnostics record the actual compiled
HTTP-library version; no dependency upgrade or forced worker termination is
justified by these unrelated reports.

On that same `8a1563fcc310d3c4c8636b9527d49b39ae7d0831` head,
[Wave 1 contracts and actual Android execution](https://github.com/imanipour2/kelpie/actions/runs/44570167627)
passed: 94 shared, 664 CLI, 31 Chromium, 3 native CTest, selector conformance,
and all 11 Android cases with zero failures/errors/skips. Android evidence ZIP
`14553743300` independently matched SHA-256
`ef74b38668994254cd3320a462bd17f90919cf3f23dafec3a8739e07d3185a8f`.
All Android build families and the 88-case installation guard also passed.
The previous Maven metadata failures did not recur with the same declared
versions/repositories; their underlying infrastructure cause remains unproven.

The [8a MotherNext producer](https://github.com/imanipour2/kelpie/actions/runs/44570167739)
produced artifact `14555195629`: independently verified ZIP SHA-256
`6d7aaaf2c1063c87fd954d49089dc4fa57a623170047544906172db0f1d286f7`,
83,341,017 APK bytes, APK SHA-256
`9a62874bbde660bb0933f5e326ae9353ecf51ac86d07e49f331d3e9b9f3657af`,
and valid v2 certificate
`62fbb7d89db7435d1154be8ec0a895bc380b188f158ab602ae1055dfee952fc8`.
Package/version/port and manifest matched the actual bytes. This signer differs
from the installed signer. The artifact is historical after the next diagnostic
commit and is not relabeled or selected as that newer source's final APK.

Both Apple jobs failed before any build step: the hosted runner was not acquired
after multiple attempts, with provider annotations identifying macOS arm64
capacity constraints. This is runner admission evidence, not a Swift/iOS compile
failure. Subsequent exact-head attempts must still run those checks; they are
not waived. The final combined-head results and selected artifact are recorded
in PR #71 and the execution document after readback.

Additional protocol reference read during wire verification:
[MCP Streamable HTTP, 2025-06-18](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports).
The genuine `notifications/initialized` fixture omits `id`, expects 202 with
an exactly empty body, and retains invalid-origin and invalid-token refusals.
No protocol upgrade or package/port change was inferred from newer references.

This is the pre-CI source snapshot; final exact-head results are appended to PR #71
and the execution document after actual readback, without rebuilding a different
artifact merely to update this report. The designated candidate producer is
`.github/workflows/qa-r5-mother-sidecar.yml`, MotherNext profile, versionCode **41**
and versionName **0.2.21-r5-wave1-qa**, same package and port **18463**. Other
retained workflow profiles are regression builds; none is selected or installed
as a parallel app. Candidate build/readback status at this source snapshot: **PENDING**. Its manifest
must record the exact source SHA, APK digest, real signer, build UTC/variant/profile
and producer run/attempt. It makes no device/recovery claim. The final manifest
and whole CI matrix must agree.

| Gate | Disposition for this mission |
| --- | --- |
| Frozen local JVM suite | PASS: 513 tests / 90 suites; zero failures, errors and skips |
| Final functional exact-head CI | PENDING; local Gradle does not substitute for the full GitHub matrix |
| Single candidate APK/manifest | PENDING designated workflow and actual artifact/signature readback |
| Compatible signing material | BLOCKED until the installed signer or a genuinely verified valid lineage is available; generating a different debug certificate does not fix this |
| Backup | Historical encrypted hot backup and SQLite checks exist; cold restore on Android has not been demonstrated |
| Rollback | Saved prior APK alone is insufficient; same-signature version rollback with retained data has not been demonstrated |
| Physical behavior | Candidate has not been installed; 20 tab cycles/3 tabs, 25 WebView frames, 50 display cycles, lock/HOME/network/render crash and real effects are NOT_RUN |
| Real-page pixel export | FAIL_CLOSED; usable verified redaction remains open |
| Governance | CDG ratification, authority mirror synchronization and parity/exception remain open |
| Owner install approval | Separate Gate E follows the pre-install review/safety gates A-D; candidate acceptance Gate F follows permitted installation |

The strengthened existing PowerShell guard reads the actual device, exact app
inventory, free space and installed byte digest; verifies local rollback bytes
and signer; validates independently reviewed recovery records; and checks every
required exact-head CI workflow. The execution document's F04 and independent
gate F require native consent, readiness and real candidate E2E acceptance after
installation. Requiring those candidate-bound records before the only install
command created a circular gate. They therefore belong to a separate read-only
post-install acceptance phase, retaining their exact source/hash, physical
thresholds, evidence checks and refusal cases. Preflight does not claim that
later phase has passed and does not itself require permission to install.

The Install phase contains only a same-package in-place update after the
pre-install safety gates and a distinct owner flag. Successful installation is
reported as pending post-install acceptance, never as full behavior acceptance.
Integrity checks cannot turn a fabricated JSON PASS into evidence; no such
bundle was manufactured here. A failure never triggers an automatic uninstall
or destructive rollback. This phase correction is source/test work only; no
installation or candidate acceptance phase was executed on the owner's phone.

**BETA_INSTALL_NO_GO** therefore preserves the current device installation.
The source work and completed local JVM validation can be reviewed now. The
single traceable APK awaits its designated producer and manifest readback.
Installation readiness must not be inferred from source completion or CI.
