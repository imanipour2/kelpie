# Kelpie Connection Center — private QA implementation and acceptance

Owner decision: EF-OPS-KELPIE-CONNECTION-CENTER-20261118, entered in canonical CDG:
https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit

Status: SOURCE-ONLY / NOT INSTALLED / NOT ACCEPTED / NO-TUNNEL-MUTATION / MAIN-SYNC-BLOCKED.

## Scope and evidence

The feature is a **native Android Connection Status bottom sheet** inside the
isolated mother-connection comparison profile. Sources consulted:
- https://github.com/UnlikeOtherAI/kelpie/blob/main/docs/api/computer-pairing.md (persistent Ed25519 computer trust)
- https://developers.openai.com/api/docs/guides/secure-mcp-tunnels (tunnel transport)
- https://github.com/openai/tunnel-client/blob/master/docs/connectors.md (configuration & local diagnostic endpoints)
- https://github.com/openai/tunnel-client/blob/master/docs/configuration.md (same; verify live deployment separately)
- Existing fork `BrowserScreen.kt`, `BrowserMoreMenu.kt`,
  `KelpieBrowserSession.kt`, `NetworkServiceState.kt`,
  `HTTPServer.kt`, `AuthMiddleware.kt` and R5 source/CI.
- Canonical operational RCA and incident:
  https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit
  https://github.com/imanipour2/enerfoam-website/issues/98

The More menu always offers `Connection Status`; the owner sees browser
network-service state, configured port, health/readiness probe results for
the **fixed local target** `127.0.0.1:8080`, and timestamp freshness of
in-memory authenticated MCP request activity. "Agent control" is always
marked **Not verified by this local diagnostic**. There is no false
`Connected` state derived from tunnel health, computer pairing or a
`capabilities` result. The existing Trusted computers management is
reachable from the bottom sheet.

## Security invariants

- Only `GET /healthz` and `GET /readyz` to the hard-coded numerical
  loopback address are permitted; no user-supplied URL, redirect, proxy,
  Authorization header, body extraction, Termux filesystem, ADB or shell.
- No new local or remote listener, background poll, service restart,
  pairing request, pairing approval, credential disclosure or tunnel rewrite.
- An in-memory monotonic timestamp is recorded only after middleware
  identifies a **non-localhost** authenticated principal on an MCP request;
  the identity itself and every payload remain unrecorded. Remote ChatGPT
  identity is NOT established by that observation.
- Android device lock/foreground and actual browser renderer are separate.
  No permission, native STOP, sensitive-action gate, cross-app boundary or
  R5 runtime policy is modified. The separate mother profile currently
  does NOT include the private R5 agent renderer; it is a comparison build.
- Without successful live probe, the screen shows unknown/unavailable,
  never a fabricated green tunnel state.

## Acceptance matrix (owner/device pending)

| Condition | Expected |
| --- | --- |
| No Termux/8080 listener | Not reachable; no inference of remote tunnel |
| /healthz 200, /readyz 503 | Running; not ready |
| Both 200 | Ready locally (not remote ChatGPT proof) |
| 302 redirect from local service | Not ready; do not follow redirect |
| /readyz 200 but /healthz failed | Not reachable |
| Only localhost implicit MCP request | No authenticated agent evidence |
| Verified MCP request then 2+ min | Stale |
| App process death | MCP evidence reset |
| More > Connection Status | Native sheet; tabs/session undisturbed |
| Check again | At most one manual bounded two-request check |
| Advanced | Status and fixed port labels, no secrets |
| Trusted computers | Existing manager opens, no auto consent |
| Wrong port/missing diagnostic listener | Honest unavailable / unknown |
| Lock, HOME, reboot, network handoffs | Physical S24 25-cycle QA OPEN |
| Remote ChatGPT `tabs.list`, snapshot, screenshot | E2E OPEN; not implied by CI |

No deployment or replacement of the existing app `com.kelpie.browser.r5betaverify`
port 18461. Experimental app `com.kelpie.browser.r5motherverify` port 18462
must be side-by-side and pass independent signing, backup and install gates.
The upstream mother localhost trust model needs hostile-local-app testing
before making it a production default. Android/iOS parity is a separate
required gate before `main` merge.

## Rollback

Discard this feature branch to remove the UI and monitor completely.
The current QA branch parent, private R5 APK, Termux secrets, tunnel, plugin,
WordPress LIVE/STAGING, and their pairing stores are unchanged.
