# Kelpie S24 — Mother-first MCP and Termux button RCA
**ID:** EF-OPS-KELPIE-MOTHER-FIRST-CONNECTION-RCA-20261120  
**Status:** OWNER-DIRECTED DESIGN SIMPLIFICATION / SOURCE-ONLY / DRAFT REVIEW-GATED / DEVICE E2E NO-GO  
**Base:** Draft [PR #71](https://github.com/imanipour2/kelpie/pull/71) over PR #70 and #66; **not** deployed or merged to main.  
**User contract:** one installed Kelpie; retain existing ports (Kelpie `18463`, tunnel diagnostics `8080`); native OWNER consent and STOP; no new API keys, packages, repeated pairing, tunnel or extra auth ceremony.

## Source readback (specific paths, behavior)
| Surface | Current fork at 9a2d99b1 (pre-simplification) | Mother upstream |
|---|---|---|
| `core/android/src/main/java/com/kelpie/browser/ui/AgentConnectionCenterSheet.kt` | Long provider/transport setup wizard; if no recently observed bearer-verified agent, offers `R5TermuxOwnerReconnectAction()`; presents a second optional Termux confirmation | Not present |
| `.../ui/R5TermuxOwnerReconnect.kt` | Button → confirm dialog → `sendExplicitOwnerRequest()` → `Context.startService(Intent(com.termux.RUN_COMMAND,...ef-kelpie-request-consent))`. A non-null component returns **QUEUED_NOT_CONFIRMED**, *not* proof command executed or native prompt appeared. 12s timeout only adjusts local text, no reliable result/receipt or correlation ID. | Not present |
| `.../ui/R5OneTapConsentAction.kt` | A real affirmative tap calls `AgentSessionCoordinator.beginOneTapFromNativeUi(expectedPrincipal)`. Requires sole recent verified principal, RESUMED/visible/unlocked activity, STOP notification, and paired principal. Failure shows generic message; no Termux code. | Not present; mother pairing/CLI is the primary path |
| `.../agent/session/AgentSessionCoordinator.kt` | `agent-session-request` must have verified client; `beginOneTapFromNativeUi` grants only after native tap; `stopFromNativeUi` revokes. Safeguards are **not** redundant passwords or OTP. | Mother has local client pairing and direct HTTP/MCP tool endpoint; no elaborate Android relay UI |
| `.../ui/BrowserScreen.kt` | Two overlapping entry points: `R5AgentConnectShortcut` and `AgentConnectionCenterSheet`. Additional independent diagnostics sheet. | `ConnectControls`, `PairingDialog`, `PairedClientsScreen` and built-in mDNS/HTTP/MCP; direct pairing and Pause/Stop |
| `.../network/TunnelReadinessProbe.kt` | Original `/readyz` showed green despite zero successful remote polls. Source patch on PR #71 now reads bounded redacted `/health/control-plane`. | Tunnel diagnostics absent |
| `.../network/KelpieNetworkService.kt`, `.../KelpieBrowserSession.kt` | HTTPServer handed from Activity to FGS; `close()` stops staged server even during some configuration transitions; separate potential foreground-service lifecycle problem | Same upstream split should be compared and verified through Android lifecycle tests |

## Causal conclusions and confidence
1. **Confirmed UI/protocol mismatch:** neither Termux button sends an OTP nor does `RUN_COMMAND` return an acknowledged native consent result. It merely requests a fixed script to run and declares the dispatch queued. The actual authorization exchange must originate from an authenticated MCP client, followed by optional native session consent.
2. **Confirmed physical remote transport failure (separate):** `tunnel-client 0.0.20` reported `network_error` on every sampled command Poll, `/health/control-plane` degraded while `/readyz=200`; the S24 Termux/Ubuntu `curl` to `api.openai.com` suffered TLS unexpected EOF (`35`), while the laptop validated TLS successfully and selected IPv4 sites on S24 worked. Active v2rayNG VPN routing/exit is a hypothesis, not proven root cause; do not change VPN without reversible owner-reviewed testing.
3. **Confirmed process/service divergence:** live Kelpie PID with missing `KelpieNetworkService` and unavailable HTTP listener. Activity closures and `networkStage.stop()` may account for some occurrences; verify with native lifecycle tests.
4. **Confirmed unnecessary setup complexity:** choosing arbitrary MCP provider, duplicative setup guide, code/Termux wizard and OTP-like wording do not increase reliability of an already configured ChatGPT MCP plugin.

## Approved for coding only by owner's request (no new CDG ratification asserted)
**Mother-first, one approval path:** use the existing authenticated MCP binding; show only an agent identification/status and a single native **Allow control for this session** button if a uniquely bearer-verified principal was recently seen, then STOP. Do **not** turn an unverified app name or an HTTP 200 health reply into authorization. When agent has not been observed, instruct user to reconnect their *existing* MCP plugin; no artificial Termux code or token entry.
- In `AgentConnectionCenterSheet.kt` commit `037dccb2` replaces the wizard and its two Termux controls with a small native state machine based on `coordinator.active`, `pending`, `observedAgents`. Termux helper remains only as an unused internal fallback for compatibility; don't remove manifest permission or delete code without reference audit and separate review. No login secrets / HTTP credentials changed. Existing on-device native consent/STOP untouched.
- Keep `R5AgentConnectShortcut` visible but ensure the two entry points share the same coordinator; do not show two independent authorization prompts. Repeated taps must not create a new principal or bypass STOP.
- The original mother HTTP/MCP transport remains intact; this is a UX cut, **not** a rollback of native privacy boundaries. The mother can be used as architectural reference, not authorization to expose a network-facing unrestricted browser control service.
- **No 2FA/OTP removal in a server-side identity system is involved**: the removed step was an optional UX dispatch into Termux, not the cryptographic bearer/client verification.

## Official sources and community comparisons (read 2026-11-20)
- [Android services lifecycle](https://developer.android.com/develop/background-work/services) and [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/launch): Activity and FGS lifetimes differ; do not stop the browser listener simply because the user backgrounds an Activity; do not restart a new FGS illegally from background.
- [Termux RUN_COMMAND official wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent): requires runtime permission, `allow-external-apps=true`, package visibility for target SDK>=30, and a **PendingIntent** for asynchronous command result. `startService` return value is only dispatch admission, not execution result.
- [Termux RunCommandService source](https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/RunCommandService.java): plugin policy failure may short-circuit request.
- [Termux issue #1932](https://github.com/termux/termux-app/issues/1932): Android package visibility can make apparent service sends ineffective.
- [Upstream Kelpie README](https://github.com/UnlikeOtherAI/kelpie) + upstream `ConnectControls.kt`, `PairingDialog.kt`: direct MCP/CLI and explicit computer trust, Pause/Stop.
- [OpenAI tunnel-client troubleshooting](https://github.com/openai/tunnel-client/blob/master/docs/troubleshooting.md) and [Termux DNS issue #92](https://github.com/openai/tunnel-client/issues/92): `/readyz` and successful Poll are distinct. Issue92's resolver hypothesis cannot be copied blindly to S24's Ubuntu PRoot, where `/etc/resolv.conf` exists and DNS lookups succeeded.
- [v2rayNG mode docs](https://github.com/2dust/v2rayNG/wiki/Mode), [routing/SOCKS community issue #5467](https://github.com/2dust/v2rayNG/issues/5467), [MTU community case #6268](https://github.com/2dust/v2rayNG/issues/6268): per-app VPN, TCP/TLS and local proxy ports differ by mode; anecdotes are **not** evidence of this device's exact cause.

## Test plan / acceptance gates
- `AgentConnectionCenterSheet` when principal count=0: one clear reconnect instruction, **no Termux button, no unnecessary provider or OTP screen**.
- exactly one verified principal: one owner tap grants single-session control; unknown/multiple principals fail closed. Pending state never auto-approves.
- active/locked: STOP remains available, device lock suspends actions; HOME/rotation do not steal focus or erase tab.
- Android no-install CI: ktlint, JVM, isolated app build; require exact-HEAD CI readback; no APK deployment from code push.
- Network: use same runtime key and tunnel/workspace, **prove** TLS from S24 to control plane, successful `commands_poll_last_successful_timestamp_seconds>0`, remote authenticated `initialize/tools/list/tabs.list`; `/readyz` alone is insufficient.
- Durable single-package install only after signing/cold-backup/rollback and owner acceptance. Physical tests: HOME/Doze/reboot, 20 tab switches, screenshots (real pixel redaction), STOP/revoke, consent errors and TLS failure.
- If current DNS/VPN egress remains broken, simplifying UI will **not** reconnect ChatGPT; explicitly keep `E2E_NO_GO`.

**Authority:** Drive/CDG decisions govern deployment. This source-only work is Draft, not a new ratified decision; document readback → PR/CI → owner approval → staging/phone acceptance → live log/sync. Current `SYNC_BLOCKED_MAIN`.
