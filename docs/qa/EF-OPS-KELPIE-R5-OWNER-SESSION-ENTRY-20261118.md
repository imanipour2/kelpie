# R5 QA — explicit owner consent entry (2026-11-18)

Status: SOURCE DRAFT; no APK installation, native S24 acceptance, merge, or production release.

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
