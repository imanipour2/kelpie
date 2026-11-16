# P5 Agentic Mobile Browser — comparative research and future architecture options

**ID:** EF-OPS-KELPIE-AGENTIC-MOBILE-RESEARCH-001  
**Version/date:** V1 / 2026-11-16  
**Status:** RESEARCH / PROPOSED / OWNER DECISION PENDING  
**Canonical research in Drive:** [Full Persian research document](https://docs.google.com/document/d/1oM-IQArjUKb0biVodw87F0KJGPh74XAwg01cADPgtDM/edit)  
**Operational record:** [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98)  
**Separate historical research:** [Android locked/screen-off investigation](https://docs.google.com/document/d/1jyv47ShO1hkMcKAzuJR4467GW3IMoXCvF5xLWmgoiXA/edit)  
**P4 candidate:** [Draft PR #17](https://github.com/imanipour2/kelpie/pull/17), source `7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee`.

> **No feature approval or code change.** This document records options for a *future* P5. It does not authorize implementation, installation, merge, release, LOCKED_TASK, capture on locked devices, or changes to ENERFOAM websites.

## What the comparison supports

1. **Product claims are not platform proofs.** Mobile agent browsers (Gemini/Chrome Android, Perplexity Comet, Samsung Browser) offer various page/agent workflows. Continuing a task after exiting a UI does *not* establish that the identical local WebView continues rendering. Browser-cloud approaches run a different browser/session.
2. **So7o demonstrates a technically useful distinction:** CDP DOM/JS/input can continue even when a fresh compositor frame is unavailable; fresh screenshots require a valid display/surface. It uses an experimental ADB-created display and exposes a DevTools transport. **Do not import privileged overlay-display setup, unprotected CDP, or debug capabilities into Kelpie**.
3. **KMPwright** demonstrates a shared browser command surface with platform-specific Android WebView and iOS WKWebView implementations. It does not prove continuous WKWebView rendering after iOS suspension.
4. **Agentic WebView** demonstrates typed browser actions, document-bound references, a request registry, navigation policies and lifecycle validation. Its screenshot provider requires a live Activity window; it does not replace P4's private renderer.
5. **KBrowser** documents desktop automation/JCEF; its Android and iOS claims must not be treated as tested background mobile operation.

Evidence classes: **A** = inspected source pinned to commit or official platform API; **B** = official vendor product claim; **C** = architectural inference/proposal; **D** = physical device-specific test with exact APK/source/device. Neither A, B nor passing CI alone substitutes for D.

## Source-pinned references

| Project | Inspected SHA | Applicable boundary | Caution |
| --- | --- | --- | --- |
| [So7o Android WebView CDP](https://github.com/jan5o7o/android-webview-cdp/tree/3c5745973aab58663aa132834ed228abc75699bd) | `3c5745973` | CDP transport vs fresh pixels, process freeze | ADB/secure settings and CDP exposure are unsuitable defaults |
| [KMPwright](https://github.com/bernaferrari/kmpwright/tree/3cf13b9eb83d75753fc06f8f6f368ae2dbf74492) | `3cf13b9eb` | Cross-platform command semantics, touch/locator conversion | No verified suspended iOS renderer |
| [Agentic WebView](https://github.com/shanthropic/agentic-webview/tree/e2473f504936ae369fd280240e625fbb35d97877) | `e2473f504` | Typed commands, document-scoped refs, origin policy, cancellation | PixelCopy uses attached Activity window |
| [KBrowser](https://github.com/lzdev42/KBrowser/tree/095fe5d511bcafda489e5232dfbe750b8d6a124d) | `095fe5d51` | Separation of UI and automation | Android/iOS testing not established |

Commercial/platform references (vendor claims, not direct local-renderer proof): [Chrome Android Auto Browse](https://blog.google/products-and-platforms/products/chrome/gemini-in-chrome-android-auto-browse/); [Perplexity Comet](https://www.perplexity.ai/en-GB/changelog/what-we-shipped--march-27-2026); [Samsung Browser](https://news.samsung.com/global/samsung-takes-its-browser-beyond-mobile-extending-agentic-ai-across-devices); [Opera Neon MCP](https://blogs.opera.com/news/2026/03/opera-neon-adds-mcp-connector-to-the-browser/); [Brave Agentic browsing](https://brave.com/blog/ai-browsing/).

Platform/security references: [Android DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager); [Android foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types); [Apple WKWebView](https://developer.apple.com/documentation/webkit/wkwebview); [Apple background execution](https://developer.apple.com/documentation/uikit/about-the-background-execution-sequence); [Chrome experimental WebMCP](https://developer.chrome.com/docs/modern-web-guidance/guides/webmcp/webmcp); [OWASP MASVS](https://mas.owasp.org/MASVS/).

## Proposed design for future P5 (not authorized)

Retain **the same real Java WebView** and the verified P4 `VirtualDisplay + Presentation + ImageReader` ownership path. Introduce only the **minimal separately scoped Interaction Executor** necessary to admit selected actions on that WebView.

`Authenticated agent -> operation-specific consent/admission -> same WebView -> typed action receipt -> fresh DOM/frame verification -> unified STOP/expiry/recovery`

Separate read-only capture/DOM from write actions such as scroll, click, type, fill and navigation. Pairing is not consent. Authorization binds principal, generation, request/attempt, tab, document, WebView identity, origin, operation, absolute monotonic deadline and budget. Check permissions at execution **and** before returning an observation. Treat webpage text and tool descriptions as untrusted content; do not allow prompt injection to grant privileges.

Possible increments:
- **P5-A:** short-lived read-only DOM/element references with document guard;
- **P5-B:** low-risk scroll/click with fresh results and separately approved local consent;
- **P5-C:** restricted fill/type/navigate with elevated review for sensitive actions;
- **P5-D:** observe–act–verify and bounded retry/recovery;
- **P5-E:** distinct iOS/remote options, **not** Android parity by assertion.

No locked-device continuation, MediaProjection of other apps, Accessibility-based takeover, privileged overlay or unrestricted CDP is in scope.

## Gates before any implementation or acceptance

**Precondition:** P4 hardware acceptance on the *exact* candidate APK/S24 is still pending: 20 genuinely fresh frames, no 21st, manual notification STOP, screen-off revocation, independent principals, same WebView/task/document/history/form/CSS scroll, failed-restore recovery, 50-cycle resource assessment. Do not reuse earlier 20/20 tests from a different commit.

For a future approved P5: require threat model, owner/CDG approval, red/green ownership and operation tests, Android instrumentation, actual S24 human consent, source/APK SHA + signer proof, privacy/redaction checks, rollback and formal iOS parity exception. Keep all tests initially on `https://example.com/` or controlled offline fixtures.

## Open decisions / governance

- Define read vs write operations and consent scope, expiry/budgets and sensitive-action approval.
- Establish an iOS policy consistent with OS suspension limits; remote cloud browser uses a *different session*, not silently migrated cookies.
- Resolve `AGENTS.md` release-per-push and Android/iOS mirror parity conflicts through the decision register before any merge.
- **Status:** `RESEARCH_DOCUMENTED / P5_NOT_APPROVED / P4_HARDWARE_QA_PENDING / RELEASE_NO_GO`. This file is a GitHub review mirror, not a new authoritative CDG decision; Drive holds the canonical research/decision original, GitHub main remains code authority.

