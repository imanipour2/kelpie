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
| Candidate/install safety | Stale hard-coded APK/source/cert plus backup checkbox could bypass meaningful recovery proof | MotherNext profile, existing sidecar workflow and `kelpie-s24-mothernext-18463.ps1` | Actual script with SDK/ADB/GitHub boundaries mocked; digest/signing/recovery/CI/device/storage/race negative cases | Same signer, cold restore, rollback and independent owner approval |

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
targets. The installation guard passed 39 scenarios against mocked external
boundaries. None of those checks touched the owner's installed package.
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
| Owner install approval | Separate final approval remains required after all technical gates pass |

The strengthened existing PowerShell guard reads the actual device, exact app
inventory, free space and installed byte digest; verifies local rollback bytes
and signer; validates independently reviewed recovery/device/transport records;
and checks every required exact-head CI workflow. Integrity checks cannot turn
a fabricated JSON PASS into evidence. No such bundle was manufactured here.
Read-only preflight cannot bypass those gates. The Install phase contains only
a same-package in-place update after all gates and a distinct owner flag; a
failure never triggers an automatic uninstall or destructive rollback.

**BETA_INSTALL_NO_GO** therefore preserves the current device installation.
The source work and completed local JVM validation can be reviewed now. The
single traceable APK awaits its designated producer and manifest readback.
Installation readiness must not be inferred from source completion or CI.
