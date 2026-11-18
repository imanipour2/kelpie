# Kelpie R5 connection simplification — upstream-equivalent baseline (2026-11-18)

Status: SOURCE-ONLY / MOTHER-CONNECTION-QA / NO PHONE INSTALL / NO MAIN MERGE / NO PUBLIC RELEASE.
Owner scope: AI agent, MCP plugin, tunnel and pairing architecture only. Browser rendering and P4/R5 functionality are not authorized for indiscriminate deletion.
Canonical operational evidence: https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit
Operational issue: https://github.com/imanipour2/enerfoam-website/issues/98
Policy: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit

## Evidence and source scope

Read upstream/fork `docs/api/computer-pairing.md`, `docs/architecture.md`,
`docs/functionality.md`, `docs/nessie-connect.md`; fork exact R5 PR55-62
changed-file inventories; and full relevant source files
`KelpieBrowserSession.kt`, `R5PrivateMcpBridge.kt`,
`R5PrivateMcpContract.kt`, `AgentSessionCoordinator.kt`,
`AuthMiddleware.kt`, `Router.kt`, `HTTPServer.kt`,
`R5AgentDeliveryGuard.kt`, `MainActivity.kt`,
`KelpieBrowserActivity.kt`, `apps/android/app/build.gradle.kts`,
`core/android/build.gradle.kts`, `core/native/core-state/src/peer_approval.cpp`,
and the prior R5 CI profile. This is not a claim of reading the entire upstream repository line by line.

## Architecture: why this comparison is necessary

Upstream Android creates a `BrowserGatewayBinding` and standard authenticated
`/mcp`, with persistent Ed25519 computer-key trust and normal pairing/revocation.
The special R5 beta additionally installs `R5PrivateMcpBridge` over the same
`mcp` route, turns on `Router.consentOnlyQa` and requires an independently
owner-consented session, principal identity and consent generation before tabs/DOM.
The ChatGPT plugin currently reaches metadata `capabilities` but `tabs.list`
returns FORBIDDEN with `consentGeneration=null`. A direct local principal was
previously observed ACTIVE then OFF; neither endpoint health nor a local active
principal proves the remote ChatGPT identity or usable browser actions.

The upstream key-pairing model is not unauthenticated: remote access needs
signed Ed25519 computer approval, a valid scoped bearer and revocation. Upstream
`localhostTrusted=true` is an explicit additional trust boundary for a real
loopback socket, which on Android can be accessed by other local apps. Restoring
that behavior increases same-device risk and MUST be covered by a local-app
adversary test before considering it an acceptable replacement for R5.

## Isolated comparison build

New Gradle property `kelpieR5MotherConnectionQaProfile=true` creates a separate
**debug-only** app `com.kelpie.browser.r5motherverify`, version code 38,
port 18462 (in both Android app and core BuildConfig). Existing package
`com.kelpie.browser.r5betaverify`, versionCode 37, port 18461, tunnel
configuration, bearer files and private pairing store are untouched.

The new profile deliberately sets all three private QA flags false:
`PRIVATE_CAPTURE_QA_ENABLED`, `R5_NATIVE_CONSENT_QA_ENABLED`,
`R5_BACKGROUND_AGENT_QA_ENABLED`. Therefore `KelpieBrowserSession` does not
replace the standard MCP router with `R5PrivateMcpBridge` and does not
activate the private R5 session coordinator/legacy route block. The standard Android
manifest is used rather than R5-specific agent/foreground permission overlays. This is a
**connection behavior baseline**, NOT a replacement R5-capability beta.
Private background-tab and native-frame screenshot functionality is not
claimed or tested for this separate app.

The verification workflow tests upstream computer trust, Android JVM tests,
ktlint and APK compilation, validates both core/application ports, flags,
package/version, Android signature, records source SHA and artifact hash.
A green build does not prove ChatGPT plugin schema, same authenticated
principal, remotely delivered screenshot, Android WebView background support
or deployment readiness. No workflow installs the APK or changes a tunnel.

## Acceptance and release gate

1. Exact GitHub head build, lint, unit/Ed25519 trust tests and artifact provenance pass.
2. Owner verifies device backup, installed package/version/signing cert and rollback
   before any optional side-by-side installation; compare only isolated QA app
   without uninstalling or changing the working R5 app.
3. Local test: genuine Ed25519 challenge/approval; single trusted computer
   reconnects across restart/IP change; unknown/revoked key rejected;
   loopback rogue-app boundary explicitly evaluated.
4. External ChatGPT: use a **separate** sandboxed connector/tunnel binding for
   port 18462 after owner approval, not the existing 18461 production-like
   test connector; compare tool discovery and principal identity. No tunnel
   change is authorized by this document.
5. Real mobile E2E: tabs, DOM/snapshot, screenshot, normal safe click/scroll,
   reconnect, STOP/revoke, duplicate/replay protection, 25 HOME/background/
   lock/reboot/network transitions, no external screen or app capture.
6. Only after results choose which R5 security gates can be retired without
   sacrificing owner consent obligations, credential isolation or STOP.
   Draft replacement for the private bridge must be tested with exact R5
   browser action and screenshot capabilities before any merge or install.

## Threat model / explicit non-goals

- Keep real bearer, computer private key, OTP, page cookies and phone content
  out of GitHub, workflow logs and prompts.
- Do not expose Android local MCP or port 18462 to the Internet or LAN without
  authenticated access and review. Keep TLS/private outbound tunnel for remote agents.
- Do not infer ChatGPT account identity from tunnel ID, address or successful
  metadata response. Workspace-level plugin permissions must be checked.
- Do not turn on unrestricted `localhost` as the only auth signal for an
  Internet-tunneled agent; forwarded bearer precedence must be verified first.
- No auto-approval, Android background restriction bypass, cross-app capture,
  unreviewed payments/publishing/deletions or independent release.
- Do not conflate the mother Nessie UOA/WSS connection with a direct ChatGPT
  plugin integration: they have different identity authorities and contracts.
- Preserve platform parity rule or acquire a specifically approved exception
  before merging Android-only changes.

## Rollback / source of truth

Revert the branch or disable the QA Gradle flag; the previous R5 beta is
unchanged. GitHub main remains authoritative for installed release source,
Google Drive CDG for decisions, Issue #98 + RCA document for device evidence.
Current status: SOURCE-ONLY / E2E-NOT-VERIFIED / PHONE-UNMODIFIED /
MAIN-SYNC-BLOCKED / PUBLIC-BETA-NO-GO.
