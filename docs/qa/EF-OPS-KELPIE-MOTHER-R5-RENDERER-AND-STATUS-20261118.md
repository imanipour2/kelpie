# Mother MCP + R5 private WebView renderer + accessible connection status

Date: 2026-11-18. Status: DRAFT QA / NOT OWNER-ACCEPTED / NO MAIN MERGE / NO PUBLIC RELEASE.

## Baseline and root cause

The installed `com.kelpie.browser.r5motherverify` v0.2.18 (versionCode 38),
port 18462, retains standard mother MCP and valid remote ChatGPT tab/DOM/click
operations. However the beta deliberately set
`R5_NATIVE_CONSENT_QA_ENABLED=false` and
`R5_BACKGROUND_AGENT_QA_ENABLED=false`. As a result the activity's owner
approval never appeared and the R5 compositor/private display did not run.
Calling ordinary `WebView.draw(Canvas)` after HOME returned a uniform gray PNG
even when the protocol said `ok:true`. This violates image validity.

## Implementation scope

- Builds the same isolated QA identity on **port 18462**, increments app
  versionCode to 39 and versionName `0.2.19-r5-mother-background-qa`.
- Enables the existing native R5 agent coordinator, native once-per-install
  first-entry opt-in, per-process one-tap verified agent consent, owner-visible
  STOP notification and R5 VirtualDisplay/ImageReader fresh compositor
  evidence. No new tunnel, plugin, public listener or remote consent API.
- **Retains standard mother `BrowserGatewayBinding` on /mcp**. Keeps
  `router.consentOnlyQa=false`, `router.backgroundAgentQa=false` in this
  profile; never registers `R5PrivateMcpBridge` over mother MCP. Uses the
  dedicated mother QA flag to prefer a verified bearer identity over Android
  generic localhost for this background mode.
- Defers transferring the actual WebView to the private display until HOME.
  In background, standard MCP `tab.screenshot` fetches a **new** owner-approved
  ImageReader frame from the R5 private renderer. If no valid frame/consent,
  returns SCREENSHOT_FAILED instead of drawing the paused Activity and falsely
  claiming success. Full-page screenshots when hidden are explicitly rejected.
- During private ownership, standard MCP and direct legacy browser calls
  must share the same current verified principal and approved process
  generation; localhost alone is inadequate. STOP/lock/revocation suppress
  and invalidate operations, HOME does not revoke, owner unlock can resume.
  Credentials, tokens, URLs and page content are never stored in diagnostics.
- Connection Status UI now uses accessible green/red/neutral semantics with
  text labels and symbols (color is **not the sole signal**), colon-separated
  label/value cells, stronger value weight, and a direct owner agent-approval
  shortcut. An unobserved remote-control result remains neutral/unknown
  rather than a green guess.

## Security and acceptance

The host remains Samsung Galaxy S24 Ultra, Android 17, USB ADB on Windows,
with the **existing** OpenAI Secure MCP Tunnel and ChatGPT plugin. No
cross-application screen capture, MediaProjection, Android lock bypass, auto
pairing, auto consent, automatic STOP bypass, checkout/publish/delete/secret
export permission, or browser popup stealing the foreground is introduced.

Required physical checks before replacing/deleting old QA:
1. Exact GitHub HEAD CI, AAPT/manifest/version/port, SHA256 and signing cert.
2. Owner approves the background capability locally; a verified remote MCP
   principal is observed and **explicitly** owner-approved once for the process.
3. Standard mother MCP `tools/list`, `tabs.list`, snapshot, click,
   scroll and screenshot in foreground still work.
4. HOME with Samsung Launcher focused: background `tab.screenshot`
   contains readable freshly changed page, never gray/replayed bytes.
5. STOP/unpair/lock revoke or suspend correctly; capture with no consent
   returns an error instead of a success-shaped blank image.
6. 25 rounds HOME/resume/screenshot, lock/unlock, WiFi/LTE transitions,
   reboot/first unlock, repeated screenshots, tab-switch ownership and
   lifecycle restoration.
7. Trusted localhost adversary test, Android FGS/notification compliance,
   restore/rollback and Android/iOS parity or approved exception.

CI passing is not proof of fresh background pixels. The old
`com.kelpie.browser.r5betaverify` package and personal app data must
remain until all relevant gates pass. GitHub PR and canonical Drive RCA +
Issue #98 are evidence; GitHub main remains unchanged and WordPress LIVE
and STAGING are outside scope.

References: Android Developers VirtualDisplay/Presentation/ImageReader,
Android background execution and foreground-service restrictions;
W3C WCAG 2.2 1.4.1 Use of Color; Android Material 3 design components;
existing R5 source, R5 QA threat model, upstream pairing documentation
and CDG EF-OPS-KELPIE-R5-BALANCED-CONSENT-20261118.
