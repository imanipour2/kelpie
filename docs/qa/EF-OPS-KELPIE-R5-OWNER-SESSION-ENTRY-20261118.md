# R5 QA — explicit owner consent entry (2026-11-18)

Status: SOURCE DRAFT; no APK installation, native S24 acceptance, merge, or production release.

## Candidate version and rollback identity

Source candidate (NOT installed): QA package `com.kelpie.browser.r5betaverify`,
versionCode **35** / versionName **0.2.15-r5-easyconnect-qa**,
port **18461**. Current owner-observed installed beta remains versionCode 34 /
`0.2.14-r5-private-beta-qa` until confirmed hardware readback after installation.
The QA GitHub workflow checks package, both BuildConfig port constants,
signer and provenance. GitHub Actions debug signing is not evidence of
compatibility with the APK already installed on the user's Windows-built device:
compare actual SHA-256 signing certificate digests before any `adb install -r`;
if different, do not uninstall or wipe the working package.

## Root cause and observed device state

- Installed package: `com.kelpie.browser.r5betaverify`, version `0.2.14-r5-private-beta-qa`, local listener `18461` (owner screenshot and ADB).
- Owner's ADB/PowerShell probes: `18461/health` HTTP 200 and `18080/readyz` HTTP 200; Android browser and Termux processes exist.
- Live ChatGPT `kelpie_browser_control.capabilities`: `OWNER_CONSENT_OR_RENDERER_REQUIRED`, `consentGeneration: null`. `tabs.list` returns FORBIDDEN as designed.
- The installed ChatGPT provider currently exposes only a **fixed-schema** `kelpie_browser_control` tool; native R5 `kelpie_agent_session` is not published as a callable connector tool. An on-app native consent prompt only appears when an authenticated request is submitted. Merely opening Kelpie **must not auto-authorize** it.
- Pre-existing "Connect AI agent" bottom sheet copied setup instructions; it did not provide pending/active status or STOP.

## Scoped change

- Expose explicit `session.request`, `session.status`, `session.stop` methods through the native **paired** `kelpie_browser_control` MCP tool and advertise them in `tools/list`/native capabilities.
- Reuse existing verified owner routes `agent-session-request`, `agent-session-status`, `agent-session-stop`, without a new unauthenticated or auto-approved path. Only request creates PENDING; the local Compose approval control alone may create ACTIVE. No code changes to the native approval primitive or the Android STOP/lock safety gate.
- Update the connection sheet to display OFF / PENDING / native-authorized state, explain the explicit initiation flow, and provide an on-phone STOP control when authorized.
- Native `session.request` is exempt from **active browser grant**, not from **paired principal authentication** or from foreground/unlocked/window-focused admission.
- Add contract regression tests proving no grant exemption for `session.approve`, spoofed names, bad versions, tabs or ordinary mutations.

## Compatibility blocker: ChatGPT registered tool schema

The connected ChatGPT tool's **client-side enum** currently does not contain `session.request`, `session.status` or `session.stop`. The native source change alone will not dynamically extend an already registered plugin. Independently update/reconnect the connector's tool schema (or expose `kelpie_agent_session` with request/status/stop), then verify actual tool invocation. Do NOT silently overload `capabilities`, `documentation`, or an unrelated tab method to start a session.

## Owner-safe hardware acceptance

1. Build **exact head** isolated R5 APK with `-PkelpieR5PrivateBetaQaProfile=true`; pin commit, APK SHA256, signing fingerprint, package/version and rollback. Do not uninstall the existing beta or `r5fullverify` without explicit owner-authorized cutover.
2. Check authenticated connector schema publishes session verbs. Connect with verified paired principal, not by bypassing a bearer or printing tokens.
3. With Kelpie visible, invoke **explicit** `session.request`; expect PENDING and a genuine Android on-device prompt. With app backgrounded or locked, expect `USER_TAP_REQUIRED` (fail-closed).
4. Have the owner physically tap Approve. Do not drive the affirmative tap via ADB, accessibility or software automation. Confirm visible notification STOP and READY with the same principal/generation.
5. Verify `tabs.list`, DOM and screenshot, harmless scroll/click/normal text on public fixture, no foreground stealing during return to ChatGPT. Test `session.stop` and notification STOP deny stale generation and replay.
6. Exercise 25 background cycles, lock/unlock, screen off, network changes, reboot, Termux:Boot restart and 8 security P0 checks. Do not call these PASS without physical results.
7. Platform parity or a separate approved exception, exact CI PASS, provenance, owner hardware signoff and explicit release authorization are prerequisites to any public beta.

## Authority / sources

Owner decision: [canonical CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit) entry `EF-OPS-KELPIE-R5-ONE-TAP-CONSENT-20261118`. Device incident: [Enerfoam Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98). Base source: [Kelpie draft PR #55](https://github.com/imanipour2/kelpie/pull/55). Official Android developer docs on background activity launch restrictions and notification permission remain applicable.

Status: `CDG_OWNER_APPROVED / SOURCE_ONLY / QA_OPEN / CONNECTOR_SCHEMA_BLOCKED / SYNC_BLOCKED_MAIN / PUBLIC_BETA_NO_GO`. No website LIVE/STAGING change.


## Second-pass hardening: owner-initiated shortcut and Android loopback (2026-11-18)

An additional native owner-initiated path avoids relying on the installed ChatGPT
connector's fixed tool enum. When the existing `kelpie_browser_control.capabilities`
is invoked through a **uniquely paired bearer** identity, the process records its
non-secret principal ID for at most 120 seconds (RAM only). With exactly one recent
paired client, the foreground browser displays a **Connect** shortcut; tapping it
creates only a PENDING session request. A separate genuine on-device Approve tap
is mandatory, followed by persistent STOP notification. The connection sheet
shows the same guarded shortcut and state. Nothing opens Kelpie from background.

The Android loopback interface is shared by apps in the same profile. The base
`AuthMiddleware` can identify loopback as generic `localhost` even if a bearer
header is supplied. In isolated R5 QA `/mcp` and `/v1/agent-*` routes, middleware
now **prefers a validated PairingStore bearer identity** instead of broad
localhost. Session request/status/stop reject anonymous, generic localhost and
invalid principals. Metadata discovery remains possible without a grant, but
cannot create an owner session. Invalid/absent tokens do not produce a Connect
shortcut; multiple fresh agents are ambiguous and require explicit client
selection through an authenticated request. The same security boundary applies
to both remote and locally initiated prompts.

**Expected short path:** prior pairing + tunnel running -> ChatGPT
`capabilities` (no grant) -> Kelpie foreground Connect -> owner native Approve
-> ChatGPT `capabilities` returns a fresh `consentGeneration` -> authorized
`tabs.list` using the generation -> page snapshot/screenshot and STOP test.
The existing ChatGPT tool schema already supports capabilities and tabs.list;
the one-tap path is therefore *plausibly compatible* without adding a new
connector method. This is **not** accepted without real S24 proof. Updating and
refreshing connector metadata is still required for the alternative remote
`session.request/status/stop` paths.

**P0 acceptance additions:** verify that a locally unauthenticated third-party
app cannot impersonate a paired AI agent even after native consent, that invalid
bearers cannot see a pending-capable principal, and that a revoked or competing
client never inherits an active session. Treat the auth file as a local secret:
prove presence, permissions and matching origin without outputting its value.

Official references:
- Android security tips: https://developer.android.com/privacy-and-security/security-tips
- Android accessibility/touch targets: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android runtime notification consent: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- OpenAI custom MCP plugin metadata refresh: https://developers.openai.com/plugins/deploy/connect-chatgpt
- OpenAI Secure MCP Tunnel: https://developers.openai.com/api/docs/guides/secure-mcp-tunnels

**Still NOT done:** compiler CI on the complete PR head, signed provenance
readback, S24 install and physical owner prompt, active authenticated tab,
browser effects, Doze/lock 25-cycle, threat review, platform parity or exception,
main merge and independent release approval.
