# R5 Background Agent: P4 native-renderer integration (source review)

**Control:** EF-OPS-KELPIE-R5-BACKGROUND-RUNTIME-20261116  
**Owner intent:** one affirmative real native consent per live Android process session; paired AI can operate *Kelpie's own WebViews* while Samsung S24 Ultra is unlocked, including normal multi-site navigation, page data, non-sensitive click/fill/scroll and fresh private compositor PNG. Keep a visible STOP; never read other apps, lock screen, notifications, protected video or privileged OS UI.

**Status:** DRAFT/SOURCE_INTEGRATION; no completed exact-head CI or real hardware acceptance yet. PR #29 stacked on consent-only PR #27. Do not merge, publish or replace the tested QA APK until security, CI and device gates pass.

## What this branch actually changes
- Reuses the same `PrivateWebViewDisplay` and underlying TabStore WebView from P4. In R5 native-proof mode it creates app-owned `VirtualDisplay + Presentation + ImageReader` and paints a per-request nonce in a tiny **native View** overlay, not in the visited DOM. Original P4 example.com-only marker remains separate.
- Native R5 Android grant from `AndroidAgentProcessSession` is identical to previously verified owner tap/STOP generation. `R5AgentCoordinator` manages Main-thread renderer handoff, private ownership reducer, STOP/SCREEN_OFF cancellation and error/restore cleanup. `BrowserScreen` unmounts Compose ownership while the private Presentation holds the original WebView.
- `R5NativeBrowserPort` reads the actual TabStore/WebView object identity; `VerifiedNativeBrowserAdapter` checks owner epoch; `AgentOperationRuntime` handles paired principal, monotonically increasing command sequence, duplicate IDs, pre/post async consent checks and evidence verification. No JSON principal can approve or take ownership.
- Authenticated `agent-runtime-status`, `agent-tabs`, `agent-command` handlers support first-path READ_DOM, NAVIGATE, CLICK (non-form, no non-verified sensitive buttons), FILL/TYPE (no password/OTP/payment input), SCROLL and SCREENSHOT. A screenshot may only originate from a **fresh nonce-proved frame of the private Kelpie WebView** while the Activity is hidden and unlocked.
- Older direct `navigate`, `click`, `fill`, `screenshot`, `browser-control` remain denied by `Router.consentOnlyQa`; the QA MCP envelope never bypasses its own nested Router check.
- Isolated Gradle debug package `com.kelpie.browser.r5fullverify`, port `18460`, code33, version `0.2.13-r5-agent-qa`. Does not overwrite the installed consent-only `r5sourceverify` port18457 or the existing `unifiedverify`. Android release flags remain disabled.

## Known open engineering defects/gates (do not silently waive)
1. Full multi-tab OPEN/CLOSE/SWITCH native rehosting not implemented. `agent-tabs` can inspect tab metadata, but runtime presently operates a single actual active WebView. Treat as partial against original product requirement.
2. Native compositor overlay proves a fresh delivered ImageReader frame, but general real-site rendering after background navigation, non-stale web content and scroll/form state restoration require physical Samsung QA (P3/P4 synthetic/exmaple.com successes are not a real-site PASS).
3. Generic CSS click outcomes that cannot be independently confirmed must be `UNCERTAIN`; never automatically replay a potentially executed click. High-impact publish, pay, destructive submit and credential export remain explicitly denied without a separate per-action owner approval flow.
4. Session lock/screen-off must suspend and release native display. Race tests for STOP during load/JS/PNG, notification disabled, process death, renderer loss and 2 genuine paired principals not completed for integrated R5.
5. Browser lifecycle on Android 17 with Home, Doze, Wi-Fi/mobile handover and background Activity destruction needs hardware measurements, no assumptions from service status.
6. The existing Termux/outbound Secure MCP Tunnel must be **explicitly rebound and verified** end-to-end on phone port18460, with no Windows dependency; browser MCP and late result egress require security review. Current ChatGPT phone/laptop control connectors are unavailable (terminated/internal error); no APK install/readback was possible during this source run.
7. Core Android AGENTS.md requires strict Android/iOS parity, otherwise an owner-approved scoped exception; none has been produced for this private display runtime. No merge or public release.

## Acceptance plan
- Exact head: ktlint, JVM, Android build/lint and APK signature/port/provenance. Workflow `qa-r5-background-agent.yml` must pass and match the downloaded/pulled APK hash.
- Real S24: no consent→all commands 403; affirmative native Approve→bound generation; 20/20 fresh private pixel frames while ChatGPT is foregrounded, with DOM/page change proof; URL/header/element matching across normal sites and docs.
- CLICK/TYPE/SCROLL must verify effect on controlled non-sensitive test page; measure input values only on-device, no cookies/passwords/token leakage. Test STOP in notification and app while command pending, after-await denial, screen-off and on unlock same-grant safety epoch.
- Test real two-principal isolation and 50 owner-approved cycles (distinct from 50 pending/STOP loops). Confirm no ANRs, memory leaks and tab/document/scroll restore, use an independent vulnerability review.
- Verify Termux tunnel control and data-plane response from ChatGPT with the laptop unplugged, plus boot recovery and network switch.
- Only after hardware acceptance, owner can review release candidate, Android/iOS parity, signing, provenance and rollback separately.

## Official source use
- [Android VirtualDisplay API](https://developer.android.com/reference/android/hardware/display/DisplayManager): app-supplied Surface; release on STOP; OWN_CONTENT_ONLY prevents default-display mirroring.
- [Android WebView.postVisualStateCallback](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,android.webkit.WebView.VisualStateCallback)): visual state barrier before fresh compositor capture (not a guarantee for video).
- [Android ImageReader](https://developer.android.com/reference/android/media/ImageReader): acquire/close latest frames and avoid queue leaks.
- [Android security best practices](https://developer.android.com/privacy-and-security/security-best-practices) and [OWASP MASVS](https://mas.owasp.org/MASVS/): process-scope consent, least privilege, no cross-app capture, anti-replay.
- [Secure MCP tunnels](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels) for phone-only outgoing data/control path, not unrestricted public HTTP.
- Internal P4 ADR, P4 RCA, R5 source review, PR22 stale owner result hardening, PR27 native consent, project CDG and EF-DOC-CYCLE.

**Authoritative status:** `G2_CONSENT_PASS_PREVIOUS_APK / R5_INTEGRATION_SOURCE_ONLY / CI_IN_PROGRESS / DEVICE_NOT_INSTALLED / MULTITAB_OPEN / TUNNEL_OPEN / IOS_PARITY_OPEN / RELEASE_NO_GO / SYNC_BLOCKED_MAIN`.
