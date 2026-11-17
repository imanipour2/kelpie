# Kelpie R5 — AI Connection Center (ADR / private QA)
Date: 2026-11-17
Status: OWNER GOAL ACCEPTED / NATIVE SETUP DRAFT IMPLEMENTED / LIVE PAIRING NOT IMPLEMENTED / CI AND DEVICE QA PENDING / NO RELEASE

## Decision
Make secure first-run agent connection the browser's main onboarding feature. The user should not need to understand Termux, ADB, local ports, relay processes or JSON schemas. Keep the native Android browser free and functional without an account, cloud service or AI subscription. A connected AI agent plans user-approved web interactions; Kelpie owns the actual WebView, tabs, credentials/autofill, native consent and STOP. Never claim the provider offers zero-cost hosting or AI inference.

## Research-backed provider behavior
1. **ChatGPT Plugins (2026)**: user/owner starts from ChatGPT *web*, Plugins > Add custom MCP server; choose an HTTPS endpoint OR an existing Secure MCP Tunnel with tunnel_id, configure auth and accept the connection risk. Custom MCP creation is not performed inside the ChatGPT mobile app. The user/account must have appropriate workspace permissions. Existing Kelpie Termux outbound tunnel and its readiness cannot be taken as proof of ChatGPT's final MCP tool routing: current direct connector still returns FORBIDDEN. Source: https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt and https://developers.openai.com/api/docs/guides/secure-mcp-tunnels.
2. **Claude**: remote MCP connectors are configured on claude.ai using a remotely reachable server. Even the Claude mobile client accesses the server via Anthropic cloud, not direct device-to-device loopback. Claude Free allows one custom connector per recent support documentation; availability may evolve. Private-network-only endpoints are not sufficient. Source: https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp.
3. **Gemini CLI**: `gemini mcp add --transport http kelpie https://example.org/mcp` creates a client config in the user's trusted command-line environment; this is distinct from an Android Gemini app integration. Source: https://github.com/google-gemini/gemini-cli/blob/main/docs/tools/mcp-server.md.
4. **Opera Neon** is a UX reference for an external MCP connection: user selects agent, verifies connection and chooses permissions. It is not evidence of one-tap account-free registration with all agents.
5. **Android primitives**: ACTION_SEND Sharesheet for non-secret setup instructions; verified App Links + assetlinks.json + actual release signature for returning users to a native pairing screen; NSD/mDNS for trusted same-Wi-Fi discovery; Android Keystore for local keys and biometric/native user confirmation; Android Autofill/Credential Manager for account secrets (not remote agent commands). Sources: https://developer.android.com/training/app-links/about and https://developer.android.com/reference/android/net/nsd/NsdManager.

## End-to-end target (three principal user actions)
- Step A, **Choose AI**: user chooses ChatGPT / Claude / Gemini CLI / Other MCP client. App discovers a previously configured *trusted* local tunnel/service and its genuine authenticated readiness; otherwise explains how to set one up. There is no fake `Connected` state.
- Step B, **Connect**: user approves the provider connection in the provider's own UI, using OAuth 2.1 / PKCE / dynamic provider metadata or its official private tunnel setup. Use an externally verified HTTPS origin or a trusted platform tunnel_id, not an unauthenticated public device URL. On Android, offer verified App Links to resume setup after provider authorization, with a non-secret request ID and CSRF/state binding. Authentication tokens must not be put in URLs, clipboard, QR codes or logs.
- Step C, **Authorize session**: the paired authenticated principal requests `agent-session-request`. On the phone, the user approves native consent once for the process session. A separate native persistent STOP control remains; device lock suspends. The agent then calls `tools/list`, `agent-tabs`, `agent-command` and a non-mutating screenshot to establish actual working status. Only successful authenticated E2E tool round trips may show CONNECTED. Every other failure state is explicit.
- Recovery: RECONNECTING / NEEDS_OWNER_APPROVAL / PROVIDER_SETUP_REQUIRED / TUNNEL_UNAVAILABLE / AUTH_EXPIRED / TOOL_SCHEMA_MISMATCH / FORBIDDEN / LOCKED / STOPPED. Suggest one visible troubleshooting action per failure and record redacted metadata only.
- Multiple AI providers: dedicated principal/client identity, grant and revoke per provider; never transfer a grant generation, cookies or tokens between principals; one controlled WebView owner at a time. Session switching requires local user consent and isolates outstanding commands.

## Source work in this branch
- `R5AgentConnectionSetup.kt`: provider-specific, non-secret setup instructions; strictly validates public HTTPS `/mcp` address with no query, fragment, user info, nonstandard port, private IP or local DNS name.
- `AgentConnectionCenterSheet.kt`: native Compose provider picker, endpoint validation, Android copy/Share setup actions, official provider documentation shortcut, explicit notice that **native session is not proof of remote connectivity**.
- Browser More menu adds an entry only when the R5 consent coordinator is present.
- JVM tests verify endpoint normalization, credential-leaking URL rejection and instruction safety.
- This is setup **UI and contract only**; it does not provision a remote relay, register an OAuth app, impersonate a human confirmation, or prove the direct ChatGPT tool is repaired.

## Required security for future automated pairing
1. Model trust: only Android native owner input may approve session and pairing; never a caller-provided user ID / URL / document prompt. Use challenge-response, key proof, CSRF state, short expiries, PKCE, HTTPS certificate validation and user-visible provider identity. Review whether OAuth audience/resource-server metadata matches MCP authorization guidance.
2. Strict transport: per-principal session generation, monotonic clientSequence, commandId dedup, tabId/epoch/renderer verification, no transparent retry of uncertain mutating operations.
3. Trusted pairing QR may be an enhancement, but must contain ONLY a short-lived single-use **non-authorizing** invitation and pinned service identity. Scanning cannot bypass native user consent.
4. User-directed cloud connections are independent: never copy a WebView cookie jar, password manager contents, OAuth bearer or bank data to an external agent without a separate explicit scoped grant. Payment and banking credentials remain excluded from browser-agent control.
5. Secrets/permissions never in clipboard/QR; clipboard carries only the public endpoint and ordinary setup wording. No unauthenticated listening on 0.0.0.0 as a convenience workaround.
6. Avoid an insecure fallback to legacy R5 routes to resolve existing direct ChatGPT `FORBIDDEN`. Need provider/secure-tunnel mapping evidence, not speculative writes.

## Product UX design and acceptance cases
- Installation: APK signature and 16KB native ABI alignment, private QA package independent from upstream, no Termux/USB requirement for eventual end-user setup. Optional tunnel must be installed/updated through a reviewed in-app/service path and user consent; background Android execution limits honored.
- Connect: ChatGPT web flow with secure tunnel; Claude remote HTTPS; Gemini CLI local command; no blanket promise of one-tap if provider requires web-only admin setup.
- Verify: successful authentication plus tool discovery plus `tabs.list`/screenshot. One test each for rejection (wrong principal, stale token, STOP, device lock, disabled notification, unreachable tunnel, malformed MCP URL, expired OAuth, wrong workspace).
- Consent: one real native click per process session, not one per site. Native user can revoke immediately.
- Reconnect: toggle airplane mode, process kill, lock/unlock, vendor app reload and provider switch without secret leakage or false CONNECTED status.
- Privacy: user may remove provider and local onboarding config. Profiles and passwords remain user-owned.
- Accessibility: Persian/English localization and RTL in product scope for final release, but preexisting private-QA source convention is English-only pending owner-approved internationalization plan.

## Gate / honest current result
The code now exposes a **setup helper**, not a completed cloud bridge. The current R5 Android tool access was still FORBIDDEN at last real connector readback and prior PR35 Android CI had failed before Kotlin formatting fixes. This branch requires fresh exact-head CI and on-device QA. Never merge PR35 or this branch into main / publish an APK until native and provider-specific end-to-end tests pass.
