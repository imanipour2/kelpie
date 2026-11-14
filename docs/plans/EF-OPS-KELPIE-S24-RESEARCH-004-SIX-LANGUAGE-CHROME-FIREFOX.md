# EF-OPS-KELPIE-S24-RESEARCH-004 — Chrome, Firefox and Android background rendering

**Date:** 2026-11-14  
**Status:** RESEARCH REPORT / ARCHITECTURE PROPOSAL ONLY / NOT OWNER-APPROVED / NO CODE DEPLOYMENT  
**Platform:** Samsung SM-S928B, Android 17; authenticated ChatGPT MCP; existing Termux tunnel  
**Prior proposal:** [SESSION-ARCH-003](./EF-OPS-KELPIE-S24-SESSION-ARCH-003-PROPOSAL.md)  
**Tracking:** [Kelpie PR #2](https://github.com/imanipour2/kelpie/pull/2) / [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98)

## Executive finding

The key is to separate **browser execution/session**, **render surface**, and **MCP transport/consent**. A connected HTTP/MCP endpoint and working DOM are not evidence of a current renderable frame. Chrome desktop Headless runs a full browser with non-visible platform windows and a rendering pipeline; its `--headless` feature is **not** evidence that the Play Store Chrome for Android or a normal WebView can render fresh screenshots while backgrounded. Playwright's experimental Android API explicitly says the device must be **awake** for screenshots. Firefox desktop headless is likewise not GeckoView Android headless.

The most promising native Android discovery is Chromium's **documented WebView snapshot architecture**: `DisplayManager.createVirtualDisplay` (private, own-content-only) + `Presentation` + `WebView` + `SurfaceTexture` (or ImageReader) + frame readback. Unlike `MediaProjection.createVirtualDisplay`, this generates pixels belonging to the application's **own** WebView; it is not a grant to capture the phone's display or other apps. Chromium's WebView documentation recommends the virtual-display snapshot approach rather than deprecated software drawing. A Java answer from the WebView developer community and an independent Android 15 presentation/color-quadrant probe corroborate it.

**Critical qualification:** In April 2026 a Chromium WebView engineer said that *fully headless WebViews* are not supported like foreground WebViews; Activity-context dependencies, crashes and memory overhead remain. A private Presentation is a different arrangement from a detached WebView with no display, but its survival after HOME, lock, Doze, Android 17/One UI and renderer upgrades remains **unverified**. Treat a device-limited experiment, not a production guarantee.

## Primary code and physical evidence from this project

- Prior S24 experiment: 10 of 10 foreground screenshot captures with distinct frame hashes; after HOME `document.visibilityState=hidden`, screenshot rejected; after returning Kelpie foreground screenshot succeeded. HOME -> real notification tap -> foreground -> PNG success was explicitly recorded. It **does not** prove many screenshots of a hidden window.
- Current installed build (last device readback): `com.kelpie.browser.unifiedverify` 0.2.6-qa / code 26; when ChatGPT is the top Activity, Kelpie `appForeground=false` and `screenshotReady=false`, while `tabs.list` and `tab.snapshot` return the real test.enerfoam.com tab.
- Installed implementation: `WebViewHardwareCapture.kt` requires `isShown`, attached window, nonzero visible rect, and `PixelCopy.request(activity.window)`; `HandlerContext.hasVisibleDocument()` requires `document.visibilityState == "visible"`. This is an intentional **surface availability** gate. PixelCopy can copy the last composited buffer; a successful API call alone never proves the page changed.
- Latest Draft PR #2 one-shot capture cache is only a transitional approval/resume workaround. It is not a continuous-rendering/session solution, is not on the installed handset and still requires client identity bound to retrieval.

Canonical records: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6873953432 and https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit .

## Chrome/Chromium: what it actually implements

1. **Process/compositor separation:** a browser process routes actions and manages state; render processes execute DOM/layout/JS; GPU/Viz composites frames. Android WebView embeds Chromium's browser code in the application's process and uses out-of-process renderer(s), but its drawing is integrated into Android's View/window pipeline, not equivalent to a desktop headless Chrome window.
2. **Desktop Headless:** Chrome 112 unified headful/headless implementation. Since Chrome 132 the old headless shell is available as `chrome-headless-shell`. CDP's `Page.captureScreenshot` captures web contents via browser targets (`captureBeyondViewport`, `fromSurface` options). A non-visible desktop browser can therefore generate frames without exposing a phone Activity.
3. **Chrome Android remote debugging:** DevTools can inspect pages, DOM and screencast a page on a connected **awake** device. Chrome's official remote debugging guide says screencast disappears when device locks. Playwright's experimental Android support requires ADB and an awake device for screenshots. CDP on Android is a development route, not proof of permissionless unattended phone screenshots.
4. **Android WebView official snapshot path:** Chromium advises `VirtualDisplay` + `Presentation` + `SurfaceTexture` + graphics readback, explicitly advising *against* software `draw()/onDraw()` and disabling hardware acceleration because software rendering has known incompleteness (e.g. video/WebGL), memory and performance issues.
5. **Private own-content virtual display:** `DisplayManager.createVirtualDisplay(..., flags=0 / PRESENTATION / OWN_CONTENT_ONLY)` can create an app-owned private surface; `MediaProjection` is a different API for mirroring the real display/app window and requires its own per-session user consent. Secure/trusted/mirroring/public flags have additional platform restrictions. An unprivileged application's own `Presentation` can render pixels onto a private virtual display, but it cannot use this to capture other applications.
6. **Safety:** Chrome 136 tightened remote debugging of default user-data directories, following cookie theft attacks; an independently controlled browser must use an isolated profile and an authenticated, non-public interface. Android `setWebContentsDebuggingEnabled(true)` is expressly a production security liability without a deliberate trust model.

## Firefox/Mozilla: what is transferable

- **Desktop Firefox:** geckodriver/Marionette/WebDriver BiDi can run screenshot workflows, with desktop headless support (`MOZ_HEADLESS`). W3C's `browsingContext.captureScreenshot` defines a portable per-context screenshot contract.
- **Firefox Android:** geckodriver supports GeckoView/Fenix via ADB, but the Android device still has a lifecycle and display. A Mozilla bug from 2023 logged `headless mode is not currently supported on this platform` for GeckoView Android. Firefox Android 153+ also had a geckodriver onboarding automation issue, with a documented `automationtest` launch workaround in 0.37.1 (July 2026). Thus replacing Chromium with Gecko does **not** solve Android headlessness by itself.
- **GeckoView design:** `GeckoSession` is separate from `GeckoView` and `GeckoDisplay`; `GeckoDisplay` accepts an app-provided `Surface`, supports `capturePixels` and newer `captureFullPage`, and exposes compositor readiness. Mozilla's architecture docs say destroying GeckoView's surface pauses the compositor and an inactive session can freeze JavaScript. A Bugzilla discussion specifically warns that screenshotting without an attached display risks empty images.
- GeckoView is useful as an architecture **reference** (session/display abstraction, explicit surface ownership), not as a low-risk immediate replacement for Kelpie's Chromium WebView. Such a switch changes the engine and creates a large new compatibility and regression burden.

## Six-language review: findings and evidence quality

| Language | Selected material | Engineering conclusion | Evidence class |
| --- | --- | --- | --- |
| English | Chromium source docs; Android DisplayManager/PixelCopy; Mozilla GeckoView/GeckoDisplay; WebView developer-group engineering response | Strongest support for native own-surface snapshot spike, with explicit unsupported-background caveat | Primary |
| Russian | Habr: WebView preloader/visual-readiness measurement; Habr hidden desktop Chrome/CDP capture; 4PDA Termux Chromium/Selenium discussion (2024) | Preload/DOM readiness is not offscreen-frame reliability; local Termux Chromium is plausible but anecdotal and device-version-specific | Community |
| Chinese | Chinese Chrome official Headless/CLI documentation; CSDN discussion of WebView Chromium GPU/compositor pipeline | Confirms headless rendering concept and Surface-bound WebView drawing; some community advice recommends software layers **contrary to Chromium guidance**; do not copy it | Official localization + lower-confidence community |
| Spanish | Chrome automation/Headless official translated docs; Android MediaProjection consent docs; Spanish Stack Overflow on background screenshot limitation | Distinguish virtual self-owned rendering from phone-screen projection; localized platform sources confirm permission model, not Galaxy proof | Primary translation + dated forum |
| Arabic | Arabic Chrome Headless documentation and Android BAL/security documentation | Browser headless is not a background Activity launch exemption; permission/notification workflow should not silently foreground UI | Primary translation |
| French | French Chrome Headless docs and Android MediaProjection virtual-screen/consent docs | A virtual screen in MediaProjection is for screen capture after consent, **not** the same as `DisplayManager` private display for self-rendering | Primary translation |

No claim is made that all six languages yielded independent successful native Android 17 background-frame benchmarks. Translations of the same official source are not counted as six independent validations.

## Evidence-based solution ranking

| Route | Fresh native S24 frames after HOME? | Same native WebView state? | Complexity and risk | Recommendation |
| --- | --- | --- | --- | --- |
| Existing visible WebView/PixelCopy | Known **NO** when hidden; YES when foreground | Yes | Low | Keep as live real-device QA/fallback |
| Offscreen `setOffscreenPreRaster` only | Not proven; requires attached window | Potentially | Moderate and unsupported alone | Never use as sole fix |
| **Private VirtualDisplay + Presentation + own WebView** | **Plausible; must test on S24** | Potentially; must test handoff/surface reparenting | Medium/High GPU, focus/IME/renderer lifecycle risk | **First experimental spike** |
| `WebView.draw(Canvas)` / software layers | Incomplete, possibly stale/blank | Sometimes | High fidelity risk | Reject as main solution |
| `MediaProjection` of phone screen | Captures what is on the phone, not independent browser engine | No improvement | High privacy/scope/user-consent cost | Reject as browser renderer |
| Local Termux Chromium `--headless` / CDP | Can render while phone UI absent if runtime remains alive | **No** (distinct profile/tab) | Medium/High packaging, CPU/memory, updates, battery | Secondary feasibility spike |
| Desktop/cloud Chrome for Testing Headless | Yes on independent host | **No** (independent session) | Moderate infrastructure and security isolation | Reliable automation fallback, not physical Galaxy QA |
| GeckoView custom `GeckoDisplay` surface | Not proven on Android 17 | No without major migration | High rewrite risk | Reference implementation only |

### Exact-tab and trust caveat

A second renderer on a separate engine or a second WebView is **not** the original tab just because URL and cookies match: in-memory JS state, scroll, form values, DOM, animation, history and ephemeral authentication tokens can diverge. Never return a remote/headless screenshot as if it came from the exact active native Kelpie tab. Preferred experiment moves the **same** WebView instance between legitimate owned surfaces (or runs it on its private display and creates a separate, clearly labeled mirrored UI), but migration/presentation/accessibility/focus semantics must pass. Alternatively keep a separate `automationSessionId`/profile and label every result as `renderEngine`, `displayType`, `tabId`, `documentGeneration` and `frameId`.

## Research-based test gates for the native virtual-display spike

Use a minimal **non-production QA app** or isolated QA package and an innocuous local HTML fixture. No WordPress admin credentials, live site changes, additional dangerous permissions, external CDP listener or ADB foreground hacks.

1. Create `ImageReader` (RGBA_8888, bounded maxImages=2) or SurfaceTexture; a private `DisplayManager.createVirtualDisplay` with own-content-only/PRESENTATION intent, no public/secure/auto-mirror flags; `Presentation.setContentView(WebView)`; maintain hardware acceleration.
2. Draw a visible nonce, colored 4-quadrant test pattern and DOM counter; cross-check decoded image pixels/nonce, not just HTTP 200, PNG size or a hash of static output.
3. Record lifecycle: Activity resumed/paused/stopped, Presentation display state, WebView attached/window visibility, renderer alive, current tab/document generation, animation timestamps, bitmap bounds, GPU memory, virtual display existence and capture result.
4. Run 20 consecutive navigate/evaluate/scroll/screenshot steps, zero extra approval prompts after one scoped session consent. Require **20/20 fresh matching frame checks**, on HOME and while ChatGPT is foreground; repeat at +5s, +30s, +2m, +10m. Do not silently substitute the main-window PixelCopy or a stale cache.
5. Test the same WebView when view ownership moves to/from main screen, and alternative dedicated offscreen WebView. Any differences in tab, cookie, DOM or login state must be reported, not reconciled invisibly.
6. Cover app lock/unlock, screen off, Doze, Samsung battery usage limit, restart, network changes, multi-window, secondary Android User 95 collision and 24h process survival. Lock must not be bypassed; by default suspend capture on keyguard.
7. Falsification criterion: any stale frame / unbounded memory / blocked render / inaccessible page or unsupported Android permission invalidates the native approach as the sole solution. Do not paper over by `am start`, Accessibility, overlays or repeated notifications.

## Authenticated continuous-session contract (proposal only)

- `startSession`: explicit user-initiated grant per paired MCP client, permitted domains, allowed actions and expiry; visible status notification with Pause/Stop.
- `navigate/inspect/click/scroll/capture`: queued, atomic, client-authenticated actions with bounded concurrency, origin, tab identity and document generation. Sensitive mutations require separate approval policies.
- `capture`: return `frameId`, monotonic capture time, document/frame nonce proof, dimensions, renderer identity and hash. Never claim freshness without matching source-content evidence.
- `pause/stop/revoke`: immediate cancellation of pending work and sensitive frame buffers; reject replay or cross-client retrieval; reauthorize after reboot/lock according to policy.
- Standardize future interoperable browser operations against W3C WebDriver BiDi where practical, with CDP an isolated trusted adapter rather than raw remote network exposure.

The latest project **Draft PR #2** and 0.2.7-qa source are not evidence of implemented or accepted Continuous Session. This research is NOT an instruction to merge, install or ship.

## Selected high-authority sources and research links

1. Chromium official software-draw deprecation and VirtualDisplay snapshot path: https://chromium.googlesource.com/chromium/src/+/refs/tags/108.0.5359.153/android_webview/docs/software_draw_deprecated.md
2. DisplayManager private/own-content VirtualDisplay API: https://developer.android.com/reference/android/hardware/display/DisplayManager
3. Android WebView official WebSettings offscreen preraster (window-attached only): https://developer.android.com/reference/android/webkit/WebSettings
4. Direct WebView-engineer discussion (Apr 17 2026): https://groups.google.com/a/chromium.org/g/android-webview-dev/c/Dn7ag_10kQA
5. Developer-confirmed ImageReader/Presentation rendering answer: https://stackoverflow.com/questions/57465334/android-webview-with-layer-type-software-not-showing-html5-canvas-content
6. Independent Android 15 private VirtualDisplay/Presentation probe: https://github.com/go-xrkit/android
7. Flutter team's VirtualDisplay limitations: https://github.com/flutter/flutter/blob/master/docs/platforms/android/Virtual-Display.md
8. Chrome Headless official: https://developer.chrome.com/docs/automation-and-testing/headless/
9. Chrome remote debugging Android and lock caveat: https://developer.chrome.com/docs/devtools/remote-debugging/
10. Playwright experimental Android and awake-device caveat: https://playwright.dev/docs/api/class-android
11. CDP screenshot API: https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-captureScreenshot
12. Chromium RenderingNG architecture: https://developer.chrome.com/docs/chromium/renderingng-architecture
13. Mozilla GeckoDisplay/GeckoSession and architecture: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoDisplay.html ; https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html
14. Mozilla Bugzilla GeckoView headless not supported: https://bugzilla.mozilla.org/show_bug.cgi?id=1839953 ; geckodriver https://github.com/mozilla/geckodriver/releases
15. W3C WebDriver BiDi dated 2026-11-10: https://www.w3.org/TR/2026/WD-webdriver-bidi-20261110/
16. AndroidWorld academic study and benchmark: https://google-research.github.io/android_world/
17. Samsung official battery background management: https://developer.samsung.com/mobile/app-management.html
18. Chrome remote-debugging security change: https://developer.chrome.com/blog/remote-debugging-port
19. Android MediaProjection per-session consent (contrasting API): https://developer.android.com/media/grow/media-projection
20. Termux Chromium community implementation: https://github.com/MajorMajorMajorMajor/chrome-headless ; Russian discussion https://4pda.to/forum/index.php?showtopic=741456&st=13440
21. Russian WebView prerender article: https://habr.com/ru/articles/965866/ ; background DOM loading: https://habr.com/ru/articles/857190/
22. Chinese Chromium architecture community: https://gitcode.csdn.net/6a17a06510ee7a33f275c6ba.html ; official translated headless CLI https://developer.chrome.google.cn/docs/automation-and-testing/headless-cli?hl=zh-cn
23. Spanish official automation: https://developer.chrome.com/docs/automation-and-testing?hl=es ; background-view discussion https://es.stackoverflow.com/questions/406805/
24. Arabic Android BAL docs: https://developer.android.com/guide/components/activities/secure-bal?hl=ar ; Chrome Headless https://developer.chrome.google.cn/docs/automation-and-testing/headless?hl=ar
25. French Headless/MediaProjection: https://developer.chrome.com/docs/automation-and-testing/headless?hl=fr ; https://developer.android.com/media/grow/media-projection?hl=fr

**Approval boundary:** Original project sources/governance and code remain authoritative for their respective domains. This document is a research artifact, not a CDG decision. No device or website modification was made.
