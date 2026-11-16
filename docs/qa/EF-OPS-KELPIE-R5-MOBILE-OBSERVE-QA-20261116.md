# EF-OPS-KELPIE-R5-MOBILE-OBSERVE-QA-20261116

**Status:** DEVELOPMENT DRAFT / NO MAIN MERGE / NO PUBLIC RELEASE  
**Repo:** `imanipour2/kelpie`; base is consent-only PR #27, not a released R5.  
**Purpose:** First real owner-consented, remotely readable Android WebView observation, preparing a mobile-only ENERFOAM workflow. This stage is **NOT** final R5, a background capture engine, or a device-independent tunnel.

## Architecture and non-negotiable boundaries

1. Dedicated Android debug QA package `com.kelpie.browser.r5mobileverify` on `18459`, version `0.2.12-r5-mobile-observe-qa` (code32), isolated from installed R5 consent-only `r5sourceverify` on `18457` and the older connected `unifiedverify`. Does not uninstall, overwrite, share app data, or silently change Termux routing.
2. Existing `agent-session-request` may display a local dialog, but ONLY a genuine native owner tap creates the in-memory grant. The native dialog **explicitly warns** that the read-only pilot exposes visible WebView text and pixels. The persistent STOP notification discloses that scope.
3. New `POST /v1/agent-observe`, requiring the *verified bearer client ID* from `AuthenticatedClientContext` and the current process-bound native consent generation. The client may request `kind=tabs`, `kind=dom` or `kind=screenshot`. For DOM/PNG the caller must include the exact current `tabId` and `documentEpoch` obtained from `kind=tabs`; any mismatch rejects. Body-supplied identity is never trusted.
4. Only the real **currently active attached, shown** Kelpie `TabStore`/WebView is accessible, on Android Main. Screen unlocked, Activity RESUMED and visible, Android STOP notifications enabled, and same client/generation are rechecked before and after any suspend. On STOP, lock, page navigation, tab switch, backgrounding or process death the response is discarded. No other apps, lock screen, Notification shade, Accessibility, MediaProjection or screenshot cache.
5. `dom` exposes only visible `document.body.innerText` capped at 24k characters; no HTML, cookie storage, scripts or input values. `screenshot` uses `WebView.postVisualStateCallback` and `WebView.draw(Canvas)` of that shown WebView only. Pixel limits: 1280×1600, PNG compressed <=2.5MB; no full screen/whole-page/bypass. Image results can contain sensitive text; owner approves this explicitly.
6. Legacy remote `navigate`, `click`, `fill`, `evaluate`, `browser-control` and old `screenshot` remain denied by `Router`; only `agent-observe` is added to the consent-only allowlist. `mcp` is an authenticated envelope; its *inner* route remains subject to the same Router policy and existing stop/ownership checks. Actual `AgentOperationRuntime` command execution and verified-effect/receipt transport are **not yet wired**.
7. This pilot requires a visible Kelpie window. Multiwindow/split-screen *may* provide phone-only visibility, but must be demonstrated on real S24; Android background/private renderer is explicitly not complete. The existing phone-side Termux Secure MCP Tunnel is pointed at a separate historical Kelpie profile and is **NOT** assumed to point at `18459`. Re-pairing, transport routing, authenticating two real principals, and durable recovery must be verified before any laptop-free acceptance.

## QA acceptance (do not auto-mark PASS)

- [ ] Exact-head Github `ktlintCheck`, `testDebugUnitTest`, `assembleDebug`, binary signature, ports, provenance, manifest/no RECORD_AUDIO, native alignment.
- [ ] Real S24 installation as a **separate debug QA package**, identify SHA-256 and signing cert, /health after boot, STOP channel and permission state.
- [ ] Negative tests while OFF: `agent-observe` denied, 8 legacy routes denied, no DOM/PNG data leak, no cross-client privilege, no screenshot from other apps/lock screen.
- [ ] Native owner approval; correct disclosure text; `kind=tabs`, `kind=dom`, `kind=screenshot` on known static pages; compare real pixels with on-device screen. Verify stale tabId/documentEpoch and wrong generation rejection.
- [ ] Real STOP *during* pending image/DOM, lock while active, background/split screen, app restart, fail-closed after process death and zero sensitive data in logs.
- [ ] End-to-end ChatGPT mobile (no Windows ADB forwarding) via verified Termux outbound tunnel and authenticated MCP, including reboot and network handover.
- [ ] Independent security review and explicit owner acceptance of the **QA** build. Full R5 public release and iOS parity are separate; no exception to `AGENTS.md` parity/release rules is implied.

## Official references / implementation

- [Android WebView postVisualStateCallback](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,android.webkit.WebView.VisualStateCallback)): fresh visual state callback precedes drawing; used before viewport bitmap.
- [Android WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,android.webkit.ValueCallback)): must run on the WebView UI thread; script is a fixed read-only visible-text expression.
- [OpenAI Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels): outbound tunnel-client in the same network trust boundary as the MCP server, without laptop or inbound public listener.
- [Android Background Work/foreground service](https://developer.android.com/develop/background-work/services/fgs): keep user-visible STOP and obey Android power/background constraints; **not** permission to screenshot another app.
- Local project sources: `AGENTS.md`, `CLAUDE.md`, `docs/functionality.md`, consent-only PR27, P4 private-display research and `EF-OPS-TOOLS-001`.

**Release decision:** `READ_ONLY_PILOT_SOURCE / TRIAL_NOT_INSTALLED / MOBILE_TUNNEL_UNVERIFIED / G4_FULL_NOT_IMPLEMENTED / RELEASE_NO_GO`.
