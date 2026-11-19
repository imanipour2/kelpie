# Wave 1: request authority, Native First and candidate installation gates

Mission: **EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001**. This is a source/change record,
not a device acceptance certificate or permission to install. Target is existing
Draft PR #71, depending on #70 and #66. No main/release/phone deployment is implied.

## Root causes and changes

| Boundary | Root cause found in source | Correction and evidence |
|---|---|---|
| Bootstrap | Grant admission/delivery used private renderer state, making consent metadata depend on browser authority in some states and allowing foreground bypass in others. | `R5McpBootstrapGate`, `Router`, `AuthenticatedRequestDispatcher`: exact metadata and consent routes need a unique paired principal but no browser grant/lease. All other mother work needs the original active grant. |
| Authentication | R5 localhost fallback could replace absent/invalid bearer identity; mother used the private bridge's incompatible wire-version gate. | `AuthMiddleware`, `AccountPairingAuthorization`: all protected R5 `/v1/*` plus `/mcp` reject missing/invalid bearers, Origin and cross-site calls. Mother keeps canonical protocol versions; private MCP keeps its separate protocol set only on its existing `/mcp` endpoint. |
| Revocation race | A principal alone did not preserve the admitted session/lock epoch, so STOP/reapprove could make a new grant appear to authorize old work; a queued Main/JavaScript callback could outlive admission. | `AuthenticatedClientContext`, `R5RequestAuthorization`, `BrowserGatewayBinding`, `BrowserOperationGuard`: immutable grant/renderer/document captured in a coroutine scope, rechecked immediately before JS and callback; control routes recheck after the Main hop. |
| Unpair | Native and account-pairing paths could revoke a key without retiring a pending/active grant or observed-client entry. | Existing `SessionConsentCore.revokePrincipal`, process session, pairing store, trusted computers and observed registry now retire only that principal. |
| Sensitive aliases | Broad consent plus `fromGateway` could reach export or legacy interaction handlers outside canonical observation/identity policy. | `R5RemoteOperationPolicy` restricts Router, native capabilities, extension discovery and gateway authorization together. No raw script/cookie/storage/network/clipboard export or unbound actions in mother QA. |
| Error truth | Thrown messages and optimistic pending state could disclose internals or conflate dispatch with success. | Server-generated correlation ID, safe structured rejection/unknown results, exception class/stack-only diagnostic logging. Cancellation remains cancellation. |
| Owner connection | No-observed-agent UI suggested Termux first, and its observed-client TTL could survive a revocation race. | Successful reauthenticated metadata is observed; native approval checks the running server's real pairing/native-trust store before and after granting. Termux is an explicitly confirmed optional fixed service/script, with existing permission and no token/URL/principal extras. No real Termux settings changed. |
| Diagnostics | A health-200/readiness-503 probe was a red failure under the “Secure tunnel” label. | `ConnectionStatusSheet` and `McpActivityEvidence` report degraded local diagnostics with a separate unknown remote state. |
| Artifact selection | Deployment script pinned a stale source/APK/certificate and accepted a backup checkbox; a missing target could allow clean installation. | Structured same-HEAD candidate manifest, reviewed digest binding, exact existing package, local rollback bytes matched against on-device `sha256sum`, signer equality, concrete recovery/physical acceptance records and an independent owner install flag. |

No ports, package suffixes, outbound tunnel settings or installed credentials
are changed by these source corrections. Native consent remains in the existing
process/session state machine. No alternative authority registry was introduced.

## CI triage and source verification

The original PR #71 HEAD was
`68b7c2ccdf6a9e45d8198f07613c635b6d3e2367`. Its 13 workflow runs were:
9 failures, 3 successes, and one existing branch-restricted formatter skip.
Independent logs of all eight Android failures showed the same four Kotlin
formatting files; no tests were removed or disabled. The macOS job failed to
acquire a hosted runner and had no executed steps. That was infrastructure,
not a source/test failure.

The isolated formatting correction is commit
`37b9ca372c0a3e81e518e8d2ac0185283c15d3f3`. Its readback was 12 successful
workflows plus the same existing branch-restricted formatter skip. In particular,
[macOS run 44548619987](https://github.com/imanipour2/kelpie/actions/runs/44548619987)
and [MotherNext run 44548620304](https://github.com/imanipour2/kelpie/actions/runs/44548620304)
completed successfully. This establishes the isolated formatting fix, not the
later functional changes or installed APK. The root Wave 1 report records the
functional commit and its independent CI readback.

## Added and extended tests

- `AuthenticatedRequestDispatcherTest`: metadata at OFF/locked/STOP, first consent
  without a grant, STOP/reapprove and lock/resume races, final delivery recheck,
  credential revocation before dispatch and while queued for Main, correlated
  uncertain exceptions, cancellation and
  principal/method confusion. Uses suspended calls to exercise real transitions.
- `R5PrivateAgentAuthenticationTest`: every protected route, missing/bad bearer,
  Origin/cross-site, public discovery isolation and separate mother/private
  version admission on both MCP paths.
- `R5McpBootstrapGateTest`, `R5AgentControlPlaneRoutesTest`: exact exemptions and
  foreground/background owner admission.
- `R5RemoteOperationPolicyTest`: actual Router under a valid grant rejects raw
  export aliases and unbound actions without executing their handler; legitimate
  canonical routes remain represented.
- `HttpJsonTest`: strict parse and safe correlated-error serialization.
- `BrowserOperationGuardTest`: queued STOP/lock round trip/reapprove/unpair cannot
  execute a mutation; callback authority change refuses delivery; only fixed
  navigation metadata may follow a replacement document in the same renderer;
  one coroutine's queued witness cannot adopt another coroutine's newer grant.
- `NativePrincipalApprovalTest`: pending, revoked and expired account pairing
  cannot pass the native approval readback; current native computer revocation
  is read from the existing trust store, not the recent-observation cache.
- `R5ObservedAgentClientsTest`: revoke pending/active by matching principal only,
  remove only that observed client and retain unrelated grants.
- Native `test_browser_gateway.cpp`: real C++ initialize, ping, list and initialized
  notification work without a browser grant; denied tool invocation cannot
  dispatch a screenshot and later metadata remains usable.
- Instrumented `R5ControlPlaneWireTest`: actual HTTP server + JNI gateway using
  fixture pairing, both paths, no WebView, independent consent, revocation and
  browser dispatch counter. Compilation is not device execution evidence.
- `R5TermuxOwnerReconnectTest`: explicit owner choice and existing permission are
  both required; fixed command constants, cooldown and queued-not-approved state.
- `ConnectionToneTest`, `TunnelReadinessSnapshotTest`: health 200/readiness 503 is
  degraded local evidence, with no assertion that the remote tunnel is down.

The Kotlin/native test execution and physical-device results are recorded by the
root task; this document does not imply they ran merely because source exists.

### Installation guard tests executed locally

`pwsh -NoLogo -NoProfile -File scripts/qa/tests/test_mothernext_gates.ps1`
executed **39 scenarios, all PASS**, using the actual deployment script with
only SDK/ADB/GitHub boundaries mocked. Positive read-only readiness, authorized
same-package update/readback, every recovery/signing/source/runtime/CI gate,
TOCTOU installed-byte change and failure-without-auto-rollback were exercised.
No phone, network call, Android install or real credential was involved.

The unchanged legacy deployment script and new guard both passed the workflow's
PowerShell parser and static safety checks. The workflow still runs all earlier
checks and now executes these 39 scenarios on Windows. Local runtime was official
PowerShell 7.5.4 Linux x64; SHA-256 verified against the release's official
`hashes.sha256` before use:
`1fd7983fe56ca9e6233f126925edb24bf6b6b33e356b69996d925c4db94e2fef`.
Both edited workflow YAML files parsed, and the extracted provenance shell passed
`bash -n`. Windows CI is the required cross-platform readback.

## Candidate manifest and fail-closed preflight

Only the MotherNext QA version changes to **41 / 0.2.21-r5-wave1-qa**. The
workflow preserves every existing identity, port, consent, gateway, signature,
lint and unit-test check. It additionally verifies exact checkout/source and v2
signing, and publishes one APK with `candidate-manifest.json`, its digest,
APK digest and readable provenance. The manifest binds package/version/port,
source SHA, variant/profile, build UTC time, exact APK SHA-256, real signer
certificate SHA-256, workflow run/attempt and the no-auto-install policy.
It explicitly does not claim runtime or recovery evidence.

The owner-side script requires an independently reviewed source SHA and manifest
SHA-256. It reads the authorized S24 Ultra model/API/user, fresh user-0 inventory
with exactly the existing Kelpie package and existing Termux, and `/data` free
space of at least twice the candidate size plus 256 MiB, before any possible write.
A local rollback APK must hash exactly like the installed `base.apk`; its
identity/version and verified v2 signer must match the existing app and candidate.
It never pulls the installed APK, installs a parallel identity, clears data,
uninstalls an app, changes a port or configures a tunnel. It refuses clean install,
downgrade and redundant reinstall.

`RecoveryEvidence` is a separately reviewed JSON evidence index with its own
`ReviewedEvidenceSha256`. Required fields are enforced by the guard and exercised
in `test_mothernext_gates.ps1` (synthetic fixture, never reusable phone evidence):

- Exact source/candidate/package and device serial digest, model, Android/API/user.
- Installed APK digest, version and signer matching the live readback and local
  rollback APK.
- Cold backup archive and capture record: existing local files, nonempty, exact
  SHA-256, capture mode `COLD`, stopped-app evidence and installed APK binding.
- Successful cold restore record binding the exact archive and equal before/after
  data digests; a backup checkbox cannot satisfy this requirement.
- Successful rollback record binding this exact candidate to the installed APK,
  demonstrated version downgrade and retained data. A saved old APK alone is
  insufficient to establish Android rollback feasibility.
- Actual physical acceptance for the same candidate: metadata before consent,
  grant separation, in-flight STOP, lock/resume epochs, acknowledged transfer,
  document identity, stale-frame refusal, usable redacted private capture,
  at least 20 tab switches across 3 tabs, 25 fresh frames, 50 VirtualDisplay
  create/release cycles, rotation, HOME/resume, screen off/on, process restart,
  preserved pairing/restored browser state and sensitive-action refusal, with hashed records.
- Separate authenticated transport and native-consent round-trip records, bound
  to this candidate and the unchanged 18463/8080 ports. A 200/503 local probe is
  explicitly degraded with remote state unknown; it cannot substitute for the
  authenticated journey. Transport evidence must be from the last 15 minutes.
- Valid nonfuture recovery/device timestamps no older than seven days. Missing, stale or altered
  evidence always blocks readiness.

These checks validate integrity and binding of reviewed test records; a JSON
`PASS` fabricated without executing the experiment is not proof and must never
be supplied. No real recovery/device evidence bundle was manufactured here.
The current image-policy limitation alone prevents usable-redacted-capture
acceptance until implemented and tested.

The script reads GitHub directly for this exact HEAD and requires all 14 current
workflow families, plus success for every additional latest workflow. The only
accepted skip is the already-existing branch-restricted formatter, when its
branch guard applies. It binds the producer run and attempt to the manifest.
Network failure, incomplete pagination, failed/running/skipped test CI or an
unknown workflow state is NO_GO. A plain `OwnerBackupConfirmed` never authorizes
installation. `OwnerAuthorizesInstall` is independent; `Preflight` remains
read-only even with that flag, and only `Phase=Install` can perform one in-place
`adb install -r -t` after final identity/digest readback. An install failure or
bad post-install readback never triggers an automatic destructive rollback.

## Remaining physical gates

No new APK was installed or pulled by this work. The root mission retains the
actual installed APK identity and signer evidence independently from GitHub.
A CI debug signer can differ from the installed signer; neither the manifest nor
version bump solves that. Matching authorized signing material, actual data
restore/rollback evidence and all physical behavior tests remain required before
`BETA_INSTALL_GO`. Missing evidence preserves the existing phone app and means
**BETA_INSTALL_NO_GO**.

## Isolated Android fixture CI scope

`qa-wave1-contracts.yml` additionally defines `android-fixtures`, a 45-minute
Ubuntu job using the existing checkout/Node/Java 17/Android setup actions and the
official SDK emulator tools. It builds the existing library test APK with both
ARM64 and x86_64 support unchanged, then starts one fresh API 34 `google_apis`
x86_64 emulator with KVM and software graphics. It explicitly verifies that
there were no other devices and targets only `emulator-5554`; it never targets
the owner's phone or invokes the owner installation script.

The job runs four newly added fixture classes: `R5ControlPlaneWireTest` (1),
`R5ImageReaderCaptureInstrumentedTest` (2), `CapturePrivacyInstrumentedTest` (6)
and `NavigationHandlerInstrumentedTest` (2). XML readback requires all four
classes and at least 11 distinct tests, with zero failures, errors or skips.
The original JVM/native/browser tests remain in the existing jobs. Reports,
emulator logs and exact-checkout summaries contain only synthetic fixture data;
summary explicitly marks physical S24 acceptance `NOT_TESTED`. Workflow YAML,
every extracted shell and its Python verifier parsed locally; actual emulator
execution must be read from the functional commit's CI, not inferred here.

Official guides read on 2026-11-19:
[emulator command line](https://developer.android.com/studio/run/emulator-commandline),
[graphics/KVM acceleration](https://developer.android.com/studio/run/emulator-acceleration),
[command-line testing](https://developer.android.com/studio/test/command-line).
The job reuses installed SDK tooling and uses `-no-window`, `-no-snapshot` and
current `-gpu software`, avoiding the deprecated `swiftshader_indirect` mode.
The command-line guide now also describes the newer Android CLI; this scoped CI
addition does not replace the repository's existing SDK setup toolchain.
