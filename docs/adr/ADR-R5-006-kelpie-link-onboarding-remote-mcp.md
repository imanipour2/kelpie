# ADR-R5-006 — Kelpie Link: user-friendly pairing and verified multi-agent transport

Date: 2026-11-18
Status: OWNER APPROVED FOR SCOPED DEVELOPMENT / ARCHITECTURE CONTRACT / IMPLEMENTATION INCREMENTAL / NO RELEASE
Decision: EF-OPS-KELPIE-LINK-ARCH-20261118
Canonical approval: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit
Operational issue: https://github.com/imanipour2/enerfoam-website/issues/98
Stack: PR57 (this design and first UI slice) over PR56 (Agent-First native consent) over PR55 (isolated R5 Android Beta)

## Problem and acceptance criterion
Owner reports that Kelpie's current ChatGPT/Claude pairing is overtechnical: manually choose HTTPS vs tunnel, discover packages, interpret MCP, ADB and ports 18461/8080, manually open provider setup and then request native approval. This is inappropriate for a consumer, agent-first mobile browser.

**Target UX**:
- First-time **per AI provider**: open/connect provider's authorized plugin or custom MCP client; optional QR/deep link rendezvous; authenticate and confirm a named device. Provider installation and OAuth, Android OS permission or owner approval might legitimately require extra actions. No deceptive claim of universal first-time 1-tap.
- Repeat **within existing valid pairing**: request -> single genuine on-device owner tap to start agent + background; verified principal displayed, STOP available. Ordinary Home/ChatGPT transitions in same process do not reprompt. Process death/reboot or STOP revokes control, not permanent pairing.
- Normal user needs no ADB, Termux, port forwarding, IP, bearer tokens, static credentials, developer settings or manually copied /mcp URL.

## Existing supported facts (source)
- PR55 private beta is `com.kelpie.browser.r5betaverify` on local 18461; its companion Termux diagnostics were reachable at 8080 in owner-provided tests. Direct authenticated MCP transport returned capabilities but no active owner generation; tabs.list forbidden.
- PR56 implements an authenticated principal presence signal and local native consent gate, but not plugin enrollment, OAuth, stable cloud relay, QR or self-healing tunnel. It is not installed, and dedicated R5 QA must pass first.
- Existing `PairingStore` persists SHA-256 bearer hashes and emits short-lived pending approval; never put tokens on screen or in QR. `R5PrivateMcpBridge` performs MCP tool discovery. Existing public app uses default port 8420; the QA beta 18461 is not a stable consumer interface.
- [OpenAI Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels) is transport for authorized OpenAI integrations; native Android runtime support is **not established** by Linux arm64 binaries.
- Official [MCP 2026-07-28 authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization), RFC9728 metadata and OAuth2.1 PKCE are for HTTPS cloud/server provider pairing. Android [Verified App Links](https://developer.android.com/training/app-links/verify-android-applinks) require domain ownership, not arbitrary incoming QR; Android FGS [limitations](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) govern process wake/boot.

## Chosen staged design
### Slice A: Private Beta — avoid false promises
- Make AgentConnectionCenterSheet progressive: show first-party verified connection status; default ChatGPT and Claude only, provider help entry, local consent next action. Move endpoint entry, copied instructions and other MCP client options under Advanced. Hide ports and technical state from ordinary UX.
- Never represent pairing as *remote control ready*. Stages: awaiting authenticated client; awaiting native owner approval; native consent active (remote tab proof still pending). Existing remote authenticated tab/snapshot readback remains final E2E criterion.
- Reuse the verified bearer -> existing pairing store -> one native tap -> generation path. No new remote approval API and no persistence of consent.

### Slice B: First-time secure enrollment (NEXT GATED IMPLEMENTATION)
- A verified QR/App Link conveys **only** an ephemeral, single-use rendezvous challenge, signed server/device identity and expiry (short TTL), not a long-lived bearer, session consent or secret.
- On phone, show bound provider, device identity and a short verification phrase. OS app-link verification plus owner confirmation precede new pairing. Credentials are bound to Android Keystore private device key; server stores per-device public identity, client scopes and revocation state.
- Login uses Android Credential Manager Passkeys/Google only if deployed account service and relying party are approved. Does not silently scrape browser credentials, cookies, OTP or social login.
- Implement anti-replay nonce, rate limit, principal issuer and audience, pairing attempt audit, multiple-client selection, revoke all and per-client revoke, lost-device recovery. No one agent silently inherits another's grant.

### Slice C: Provider-neutral remote MCP gateway (REQUIRES APPROVED INFRASTRUCTURE)
- Stable domain HTTPS /mcp, Streamable HTTP, OAuth 2.1 PKCE, proper protected-resource authorization metadata, client registration with CIMD preferred, token audience binding, exact redirects and short-lived bearer. Provider installation/authorization stays inside ChatGPT/Claude UX.
- Device establishes outbound mutual-identity TLS/WSS or HTTP2 session to broker with rotation/heartbeat/backoff, bounded queues and telemetry; no publicly exposed phone HTTP listener or LAN port. Broker routes authenticated/principal/device-bound browser commands to the existing NativePort; renderer/consent/replay/evidence checks remain authoritative on device.
- Broker is a **separate deployable security component**; cannot claim it exists from a document or UI. Requires DNS, hosting, owner secrets and security review; do not provision or charge for infrastructure without separate authorization. Do not hardcode OpenAI-specific private tunnel internals into generic provider connector.
- First cut supports one active writer/controller per device, with explicit owner-selected principal; other agents fail with visible conflict. Pure read-only multi-agent sharing needs independent threat analysis.

## Status protocol and test matrix
`NOT_CONFIGURED` -> `REMOTE_AUTHENTICATED_PENDING_OWNER` -> `NATIVE_CONSENT_ACTIVE` -> `REMOTE_E2E_VERIFIED`; intermediate `TRANSPORT_OFFLINE`, `SUSPENDED_LOCKED`, `REVOKED`, `STALE_PAIRING` are distinct. R5 Android UI currently can support only first three *with known local evidence*; the final state must follow a real authenticated tabs.list/snapshot roundtrip (never inferred from /health or pairing metadata).

- Deny any data read/write before authenticating principal and native owner approval.
- UI selection and approval must not install third-party plugins or simulate a user click for approval.
- QR: expired, forged, replayed, wrong provider, invalid domain, account switch, multiple devices and revoked devices.
- Android 17 Samsung S24 Ultra: 25 real cycles including private renderer screenshot, HOME↔ChatGPT, lock, Doze, reboot, network handoff, VPN, STOP and notification-off; no unexpected Activity foreground.
- Android/iOS parity must be implemented or explicitly excepted before public launch.
- Signed provenance, failure/rollback, CI and physical e2e must be green before marking beta accepted.

## QA evidence / current state
PR56 generic Lint succeeded, dedicated R5 CI had failed on Kotlin format before the first remediation; re-run required. This PR57 contains UI + pure status tests + design contract, not QR service, HTTPS broker, OAuth server, phone installation, automatic tunnel bootstrap, or cloud plugin publication. It must remain draft and unmerged until security and physical QA. The current user-installed APK must be preserved.

References: Signal linked devices, Bitwarden Login with Device, VS Code Remote Tunnels, Docker MCP Toolkit, Tailscale device authentication, MCP specification, Android Developer documentation, PR55/56 source, governance EF-GOV-DOCOPS-001.
