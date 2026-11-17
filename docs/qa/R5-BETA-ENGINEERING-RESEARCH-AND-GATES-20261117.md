# Kelpie R5 — beta engineering research, implementation choices and evidence gates

Date: 2026-11-17 | Scope: owner-authorized Android private QA in `imanipour2/kelpie`.
Status: SOURCE REVIEW + FOCUSED FIXES; NO MAIN MERGE / NO PUBLIC BETA / DIRECT MCP UNVERIFIED.
Decision anchors: EF-OPS-KELPIE-P4-SESSION-CONSENT-20261116 V2; EF-OPS-KELPIE-RELEASE-POLICY-001; EF-OPS-KELPIE-R5-TRIAL-20261116. Canonical owner decision record is ENERFOAM CDG, not this draft.

## Evidence hierarchy and existing baseline

1. **Physical evidence**: issue #98 and draft PR #34 identify one installed Android 17 SM-S928B QA APK with native owner consent; 2,000 verified private images, STOP resource release and 48 native command attempts (26 verified / 15 adapter failures / 6 admission rejects / 1 invalid argument). A non-secret USB/ADB harness proved the local R5 API, not the external ChatGPT plugin.
2. **Newer source only**: PR #35 adds ordinary-button/SPA proof and SELECT, WAIT, BACK and FORWARD, plus Android Autofill/WebAuthn feature detection and CAPTCHA hints. PR #36 adds a non-secret ChatGPT/Claude/Gemini CLI setup sheet. Neither is proven installed or ready; both are stacked drafts.
3. **Transport**: Phone's Termux tunnel reached ready/polling after correcting 18440 to 18460; the actual ChatGPT connector still returned FORBIDDEN while authenticated native `agent-*` endpoints returned 200. Legacy routes are intentionally denied; the exact upstream connector mapping is unavailable. Never claim the 403 issuer is known without gateway telemetry.
4. **Today's CI root cause**: PR #35 Android failed at Kotlin condition wrapping in `R5NativeBrowserPort`; PR #36 Android failed at multiline declaration formatting in `R5AgentConnectionSetupTest`. This patch removes the fragile multiline expression and standardizes the declaration, then relies on exact new-head CI rather than historical passing runs.

## Comparative research and practical choices

| Capability / source | Observed engineering or user evidence | Choice for R5 | Acceptance |
| --- | --- | --- | --- |
| Real WebView ownership | Android WebView must be controlled on its creating UI thread; destroying/reparenting a live WebView during tab changes risks renderer loss | A single authoritative TabStore and serialized transition lease; deny unknown transitions rather than create a second WebView | 3 tabs, history/form/scroll preserved, same app-owned renderer; no cross-app capture |
| Multiple tabs | Android WebChromeClient onCreateWindow is an explicit opt-in; default can block new windows | First implement native create/select/close with ownership handoff and STOP cancellation; add web-popup policy separately | Open/select/close/list 20 cycles; stale tab never executes |
| BACK/FORWARD | WebView canGoBack/canGoForward and goBack/goForward operate on real history | Prefer native methods, observe stable URL/epoch afterwards; do not claim success on method return alone | Genuine S24 history navigation, blocked no-history response |
| DOM/snapshot | Browser-use and agent-browser expose compact action targets; user reports say entire DOM per step is slow/expensive | Bounded visible interactive refs, fresh tab+document ID and semantic selectors; never leak hidden credential values | SPA/iframe/shadow-DOM diagnostic cases and stale-ref negative |
| Safe click and SPA | Reddit reports brittle long workflows, stale/partial pages; Vercel agent-browser checks occlusion | Observe -> act -> independently verify; no automatic replay of uncertain non-idempotent actions | 25 ordinary JS buttons incl. same-URL DOM updates, overlay and delayed change |
| Forms/select/type | Android WebView JS assignment is not hardware IME; some apps require real key events | Keep synthetic FILL clearly labelled; SELECT with independent readback; future native IME bridge, not global ADB shell input | Input/change, Vue/React controlled form and keyboard-specific test |
| Financial/destructive actions | Untrusted site DOM and generated labels do not prove action semantics | For beta, conservatively deny identified finance/secret/destructive links, non-GET form-button submits and sensitive-credential forms. Native policy must independently gate typed PAYMENT/credential export; heuristic JS is not complete | Tests for payment/transfer/delete and hidden POST submit; human authorization for exceptions |
| Screenshot fidelity | Existing physical PR34 achieved native 720x1280 AUTO and 1080x1920 HIGH; 512-frame epoch rotations | Preserve private native pixel proof, bounded 4/8 MiB PNG, quality negotiation and no image-retention | 2,000+ genuine frames, 24-hour low-memory, 16KB alignment, STOP cleanup |
| Session consent | User approved one on-device consent per active browser process session, not per site | Principal-scoped local approval; show active provider; persistent STOP; screen lock suspends, process kill revokes | 50 real approvals; two distinct principals; locked/STOP mid-flight, no remote approve |
| Android background | Android Doze, background-service types and One UI battery policy limit uptime | Respect OS lifecycle; safe reconnect and explicit unavailable state, not a fake keepalive or lock bypass | 24-hour Doze, power-saving, network handoff, restart + no duplicate tunnel |
| CAPTCHA/Turnstile | CAPTCHA is website-owned and user-restricted; cross-origin challenges may not permit synthetic events | Expose detection hints; agent may attempt ordinary permitted UI actions or request explicit human interaction; do not extract challenge tokens, fake a human, or bypass verification | Real CAPTCHA handoff and resumed task; denied external capture/credential leakage |
| Password/passkey | Android AutofillService is user-selected; Credential Manager/WebView WebAuthn needs capability and verified site association | Delegate secrets to Android; enable feature-detected APIs only for supported origins; never supply passwords/OTP/cards to AI | Android system Save/Fill; Digital Asset Links; unsupported-origin handling |
| Download/upload | A full browser needs chooser and download handling, while remote agents must not exfiltrate files silently | Android SAF/content URI picker and user-visible DownloadManager; per-task scoped file selection | Deny arbitrary filesystem reads; selected-file positive and cancel path |
| Search/address | A free browser must work without any agent or cloud account | Keep normal local navigation and search; account-free browsing baseline | Manual phone search/address, Back and tab controls without MCP |
| External AI setup | Opera Neon differentiates read and write tools and uses a persistent MCP proxy; cloud providers vary | Choose provider -> approved secure connection -> native session consent; disconnected/forbidden are explicit states | ChatGPT, Claude and Gemini CLI per-provider E2E successful tool calls; remote provider requirements |
| MCP auth and discovery | MCP 2026-07-28 removes protocol sessions and initialize; each call carries authenticated principal and protocol metadata, with protected-resource OAuth metadata for HTTP | Public HTTPS or trusted Secure MCP Tunnel; stateless versioned MCP adapter backed by *explicit server-owned* per-principal process-consent grants and monotonic command sequence, no token in QR/URL/clipboard | 2026 wire-contract/version headers, tools/list, audience/issuer/role/expired-token/STOP/replay negative tests, legacy-era interop only when explicitly reviewed |
| External cloud | Browser Use Cloud/TinyFish offer managed Chrome/proxies, unlike local WebView | Optional independent backend with separate billing, credentials and session ownership; never silently copy Kelpie cookies to cloud | Informed opt-in and data isolation, cloud failure leaves local browser usable |
| Accessibility/localization | WCAG mobile touch/focus and Android TalkBack apply to actual screens | Compose semantics, accessible STOP, English-first experiment; full locale framework before public RTL release | TalkBack, 320/390 widths, landscape, high font scale, focus/contrast |
| Release integrity | Repo requires iOS/Android parity unless separate exception, 500-line limit, signed production artifacts | Draft feature branch + exact CI + provenance + independent parity decision + owner release approval | Signed release SHA, 16KB native ELF, no leaked tokens, rollback from pinned APK |

### Competitor / experience interpretation

- **Opera Neon (MCP Connector, 2026-03-31):** separated read-tab/page/screenshot (default enabled) from mutation tools (opt-in); added a persistent proxy that can distinguish browser offline from connection failure. R5 should adopt the authorization UX and truthful availability state, **not** imply identical cloud uptime on Android.
- **Browser Use (open-source 1.0 and Cloud):** persistent agent execution, CDP browser functions, local versus managed cloud distinction, checkpointed workflows and optional CAPTCHA solver. The real browser profile and full cloud services are distinct from the exact Android WebView the owner uses.
- **Vercel agent-browser:** accessibility selectors and action refs; reported click blockers on overlays motivate fresh target/occlusion checks.
- **Anecdotal Reddit user feedback, not representative statistics:** reports brittle long-running browser agents with iframes, CAPTCHA and stale DOM; repeated screenshots/LLM calls increase latency and cost. Prefer deterministic native actions and periodic checkpoints over sending complete screenshots at every step.

## 2026-11-17 physical S24 deployment recovery / PR42 authentication-version gate

On the owner's unlocked Samsung SM-S928B running Android 17, independently
read back the installed SHA-256 of the source-pinned PR41 QA APK as
`9002104e0dd73fbb0e5c07b4387569435a15a62cebca4dd59c1e9508ec639ddf`
(source commit `a5ff22b46a1b8a00d9d02c262061d2f8b4de1b9a`).
A retained local execution ledger records safe old-app clean uninstall,
new app install, and absent mother package `com.kelpie.browser` in owner
user0 and dual profile user95. Owner user0 independently reports only
`com.kelpie.browser.r5fullverify`. Native health on forwarded port 18460
returns HTTP 200; local no-consent negative suite passed all nine legacy
and native routes with HTTP 403, remaining OFF afterward.

Reproduced a real **new-build** MCP blocker: with otherwise well-formed
`MCP-Protocol-Version: 2026-07-28` requests, both `server/discover`
and `tools/list` returned HTTP 400 `BAD_REQUEST` *before* hitting R5
wire admission. The old outer `AuthMiddleware` permits only
2025-03-26/2025-06-18, while the new R5 layer recognizes
2025-11-25/2026-07-28. Draft PR42 extends the **authenticated** version
allowlist (does not alter bearer/CSRF/Origin requirements) and adds a
negative-test matrix for duplicate/unsupported headers, Origin and bad
bearers. Re-test modern discovery on the exact PR42 physical APK before
calling MCP E2E connected. Distinguish the host's Windows management
tunnel/CDP port (needed for the authorized tooling) from the phone's
old Termux tunnel; no broad kill/uninstall without process/port readback.

## Critical 2026-07-28 MCP specification update (verified 2026-11-17)

The *current published* MCP core/Streamable HTTP specification is **2026-07-28**, replacing the 2025-11-25 revision for new implementations. It removes the old protocol `initialize` handshake and `Mcp-Session-Id` state. Every HTTP POST to a single `/mcp` endpoint must carry a self-contained request with protocol-version/client-capability metadata; the HTTP binding mirrors protocol version and method/name headers and rejects mismatch. A browser process **still needs local owner consent and per-principal authorization state**: MCP's lack of a protocol-level session is **not** permission to skip R5's application security controls. A remote bearer access token is validated as the resource-server identity, and the owner consent generation is verified against that principal on every call. Wrong principal, replayed clientSequence, stale tab/document epoch, STOP, lock and expired token must fail closed.

Security: validate incoming HTTP Origin; enforce loopback-only local listeners, secure remote tunnel/HTTPS, authenticated OAuth 2.1 protected-resource metadata, issuer/audience and PKCE requirements. Use public read-only tools by default, mutation permission separately, but no unauthenticated legacy fallback. Current ChatGPT plugin behavior may depend on older MCP revision: implement negotiated compatibility **only after** actual provider contract, gateway errors and wire logs are obtained. Do not infer FORBIDDEN's hop from current high-level error.

Migration/backward compatibility specifics: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports and https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http ; auth: https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization ; spec release: https://blog.modelcontextprotocol.io/posts/2026-07-28-spec-ga/ . Draft onboarding design and earlier technical notes that cite 2025-11-25 are **historical references**, not proof the new bridge implements the current wire contract.

## Implemented on this focused draft child

- Correct Kotlin formatting root causes preventing PR35/36 Android ktlint.
- The separate stacked draft PR #38 adds a bounded pure tab metadata projection shared by `agent-tabs` and an `agent-command LIST_TABS` implementation. It has a stable active/epoch readback proof and three source tests; it does not create, close or switch tabs and is not yet hardware-accepted.
- The separate stacked draft PR #40 adds bounded unique CSS selector-path fallback and role hints to interactive DOM snapshots where IDs are absent; selector freshness still requires real WebView tests and re-observation after SPA changes.
- The separate stacked draft PR #39 implements an **R5 QA-only** `/mcp` read facade with `server/discover`, `initialize`, `tools/list`, `tools/call`: local native session request/status/STOP, paired `capabilities`, authenticated `tabs.list`, command-sequenced `tab.snapshot` and bounded PNG `tab.screenshot`. It replaces the generic gateway's `mcp` handler only for the R5 QA application and never opens legacy `/v1` browser methods. Ktor header validation, complete modern/legacy wire interoperability, and the external 403 source remain **UNVERIFIED** and must be tested on S24 and provider endpoints before claiming a fix.
- Keep ordinary JS button dispatch, but add conservative in-document non-GET form/button, secret-bearing form, non-HTTP(S) anchor and explicit destructive-label guards, without granting the agent payment or secret permissions.
- Add static Kotlin contract tests for new guard strings. These are **not** substitutes for real WebView instrumentation.

## Private MCP wire admission hardening

R5 QA-only `/mcp` now validates a modern `2026-07-28` request's transport version,
`Mcp-Method`/`Mcp-Name` routing headers, and `_meta` protocol, clientInfo,
and clientCapabilities against its JSON-RPC body **before** dispatching to the
authenticated native bridge. Unsupported versions produce a JSON-RPC HTTP 400
with `-32022` and supported revision list. Legacy `2025-11-25` or a
headerless legacy initialize remains a deliberate compatibility path, still
subject to native bearer pairing and local session consent for any browser
content. These checks are source-only and require real modern/legacy MCP
Inspector + ChatGPT testing; Ktor's existing Content-Length requirement may
reject chunked clients and is a separate transport compatibility gate.

## PR41: separate mutation tool (source only)

Normal browsing actions are now implemented behind a distinct
`kelpie_browser_actions` discovery entry. The read-only tool is unchanged;
the mutating tool is marked non-read-only and delegates to native R5, requiring
paired transport principal, native approved process grant, tab/document epoch,
unique command ID and monotonic clientSequence. Fixed WebView scripts block
known password/OTP/payment controls and unreviewed POST form buttons, but
heuristics cannot prove that all dangerous actions are blocked. Therefore
real Android hardware action QA, security review and provider-specific
write-tool authorization remain release blockers. Native tab transfer
OPEN/SWITCH/CLOSE, external cloud and CAPTCHA handling are not introduced.

## Beta gates and no-go decisions

**G-B0 source/control**: identify exact owner-approved scope, PR stack and branch; no Android/iOS parity exception yet. Full benchmark spreadsheet unnecessary; this document is the component matrix.

**G-B1 CI:** lint, unit, Android Gradle build, native 16KiB, proper package namespace, static security, dependencies/license/SBOM, workflow exact-head SUCCESS. A failing or pending job is NO-GO. Flaky/timeouts investigated, never suppressed to green.

**G-B2 phone:** owner physically installs only verified test APK (distinct `com.kelpie.browser.r5fullverify`), backs up prior exact APK and confirms signature/hash; no global `adb shell input`. Native consent, full tabs, ordinary buttons/SELECT/WAIT/Back, lock/Doze/STOP, 2,000 fresh frames and memory/thermal verified.

**G-B3 agent connection:** use real ChatGPT connector after verified per-principal native approval, tools/list, tabs.list, snapshot, screenshot, click/fill/STOP through a versioned adapter; repeated no-USB run. Record exact 403 issuer if still blocked. Claude/Gemini independently verified or expressly excluded from beta scope, never shown connected by UI alone.

**G-B4 security/privacy:** prompt-injection separation (page text never owner command); no credential/OTP/card export; replay/mid-flight STOP/wrong principal/document; no public debug listeners, TLS auth and plugin tool-permission partition; independent review.

**G-B5 release approval:** explicit owner decision for Android-only parity exception or shared iOS parity, production signing (all four required signing secrets), non-debug package/provenance, rollback, accessibility, user docs, license notices, distribution channel and scope. **No public beta, release tag, main merge or live Enerfoam website change without these checks.**

Website-specific G0–G4 backup/staging/RTL/Woo QA are separate; this R5 QA creates no WordPress change.

## Sources read (2026-11-17)

- Android WebView UI-thread, history, multi-window, memory: https://developer.android.com/develop/ui/views/layout/webapps/webview and https://developer.android.com/reference/android/webkit/WebView
- Android WebChromeClient onCreateWindow: https://developer.android.com/reference/android/webkit/WebChromeClient
- Android Credential Manager/WebView: https://developer.android.com/identity/sign-in/credential-manager-webview ; Autofill: https://developer.android.com/identity/autofill
- Android background constraints: https://developer.android.com/develop/background-work/services/fgs/service-types ; https://developer.android.com/training/monitoring-device-state/doze-standby
- Android 16KB page size: https://developer.android.com/guide/practices/page-sizes
- OpenAI Secure MCP Tunnel: https://developers.openai.com/api/docs/guides/secure-mcp-tunnels ; plugin connection https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt
- MCP 2026-07-28 HTTP transport + authorization: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http ; https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization
- Anthropic remote MCP: https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp ; Gemini CLI: https://github.com/google-gemini/gemini-cli/blob/main/docs/tools/mcp-server.md
- Browser Use: https://github.com/browser-use/browser-use ; Opera Neon: https://blogs.opera.com/news/2026/03/opera-neon-adds-mcp-connector-to-the-browser/
- Vercel agent-browser: https://github.com/vercel-labs/agent-browser
- User feedback (anecdotal): https://www.reddit.com/r/LocalLLaMA/comments/1uh0uz7/whats_the_latest_on_agent_browser_use/ ; https://www.reddit.com/r/LocalLLaMA/comments/1rm5mkv/anyone_moved_off_browseruse_for_production_web/ ; https://www.reddit.com/r/AI_Agents/comments/1xr0r42/where_do_your_browser_agents_actually_break_login/
- Internal: GitHub Draft PRs #29/#33/#34/#35/#36; ENERFOAM issue #98; canonical Drive R5 device QA and EF-GOV-DOCOPS-001.
