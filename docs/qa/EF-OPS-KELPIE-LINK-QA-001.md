# EF-OPS-KELPIE-LINK-QA-001 — Acceptance matrix for Android R5 private Kelpie Link

Decision: EF-OPS-KELPIE-LINK-ARCH-20261118 (owner-approved scoped development)
Source: https://github.com/imanipour2/kelpie/pull/57 stacked over PR56 and PR55
Date: 2026-11-18
Current status: DRAFT / NO SIGNED APK / NO DEVICE TEST / NO PUBLIC BETA

## Evidence levels: never equate these
A. Source changed: PR file and commit exist.
B. Kotlin/JVM CI: ktlint and unit-test results from a **specific SHA**.
C. Android APK: isolated build artifacts/signature match that SHA.
D. Native local: ADB finds actual package/version, /health returns 200, Termux /readyz only where applicable.
E. Authenticated remote MCP: bearer-verified client tools/list and capabilities, not just HTTP.
F. Owner-authorized E2E: native tap -> consentGeneration -> tabs.list -> tab.snapshot/screenshot -> readback + in-app STOP, zero leaked credentials.
G. Background recovery and product readiness: repeatable 25-cycle physical lock/background/reboot/network tests.

Only **F+G** establish beta acceptance. Earlier passing stages never substitute for later ones.

## Local user experience
| ID | Expected result | State |
|---|---|---|
| UX-01 | Fresh app start opens native agent-first gate; agent cannot read tabs before consent | OPEN |
| UX-02 | Connect agent flow starts with ChatGPT/Claude, no manual IP, port, token or ADB | SOURCE ONLY |
| UX-03 | Advanced options reveal HTTPS /mcp and other clients intentionally | SOURCE ONLY |
| UX-04 | Unauthenticated or merely stored pairing never displays a verified active agent | UNIT TEST SOURCE |
| UX-05 | After verified paired capabilities request, eligible principal appears without manually calling session.request | SOURCE ONLY |
| UX-06 | One genuine tap authorizes agent + background; return to ChatGPT/HOME does not reprompt within process | OPEN |
| UX-07 | No always-visible STOP notification -> native session grant denied | OPEN |
| UX-08 | Abort/Exit revokes native grant; independent Termux tunnel is preserved | OPEN |
| UX-09 | Native local approval explicitly not described as cloud connected until real tab readback | UNIT TEST SOURCE |
| UX-10 | If provider needs OAuth/plugin installation, UI directs to provider and does NOT silently install anything | SOURCE ONLY |

## MCP compatibility and security
| ID | Expected result | State |
|---|---|---|
| MCP-01 | tools/list offers browser-control, actions and agent-session; no accidental missing tool after connector refresh | OPEN |
| MCP-02 | Existing browser-control-only connector can call session.request/status/stop without fresh grant | UNIT TEST SOURCE |
| MCP-03 | session.approve/grant/unlock absent and fail-closed via any tool/name | UNIT TEST SOURCE |
| MCP-04 | request/status/stop always require verified paired principal and may NEVER grant; STOP only current principal | OPEN |
| MCP-05 | tabs/DOM/screenshot/click require native generation, exact principal, current doc epoch and replay fence | OPEN |
| MCP-06 | Locked device, renderer handoff, screen off, missing notification, process death revoke/suspend safely | OPEN |
| MCP-07 | Multi-principal chooses one explicitly; other principal cannot inherit prior session | OPEN |
| MCP-08 | Payment/publish/irreversible delete/credential access require separate scope; no other-app screenshot | OPEN |

## Broker/QR not yet implemented
| ID | Expected result | State |
|---|---|---|
| BKR-01 | Public HTTPS /mcp with OAuth 2.1, PKCE, resource metadata and CIMD verified in approved hosting | NOT IMPLEMENTED |
| BKR-02 | Signed short TTL one-time QR/App Link rendezvous, replay/expiry tests and no bearer in QR | NOT IMPLEMENTED |
| BKR-03 | Android Keystore device key, outbound-only tunnel, token rotation, revocation, redacted telemetry | NOT IMPLEMENTED |
| BKR-04 | ChatGPT and Claude remote provider auth tested on separately approved accounts | NOT IMPLEMENTED |

## Hardware matrix — Galaxy S24 Ultra / Android17
Perform at least 25 cycles with: app started/stopped, screen unlocked/locked, HOME↔ChatGPT, mobile data↔Wi-Fi, VPN on/off, airplane reconnect, Doze, notification denied, Termux tunnel reboot, PID/process death, restart needing new tap. Ensure no automatic activity bring-to-front and preserve login, cookies and tabs. Record Android build SHA, package, profile, nonce, test step, direct MCP readback, screenshot evidence, user consent and rollback; never collect bearer token/OTP/credentials.

## Release gate
No merge of PR55/56/57, installation over existing S24 R5 beta, signed public distribution or WordPress LIVE/STAGING modification without: green Android-specific CI on exact merge candidate, security review, owner native test and explicit separate release authorization. **SYNC_BLOCKED_MAIN / PRIVATE_BETA_NO_GO** until then.
