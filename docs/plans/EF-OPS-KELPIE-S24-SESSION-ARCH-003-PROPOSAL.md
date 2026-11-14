# EF-OPS-KELPIE-S24-SESSION-ARCH-003 — Proposal: continuous browser-control sessions

**Status:** PROPOSED FOR OWNER REVIEW — not approved, not implemented, not a release decision.
**Date:** 2026-11-14
**Scope:** Android Samsung SM-S928B/Android 17, authenticated ChatGPT MCP via existing on-device Kelpie/Termux tunnel.
**Related:** [Kelpie PR #2](https://github.com/imanipour2/kelpie/pull/2), [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98), [EF-OPS-TOOLS-001](https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit).

## Question: why does remote control survive when the app is hidden, but screenshots do not?

Control and rendering have separate lifecycles. Kelpie's foreground `KelpieNetworkService` can keep the paired HTTP/MCP control endpoint responsive while the browser Activity is stopped. Current **installed** 0.2.6-qa code uses `WebViewHardwareCapture.capture()`: it first requires an attached/shown/window-visible WebView with a valid visible rect and then calls `PixelCopy.request(activity.window, ...)`. `HandlerContext.hasVisibleDocument()` additionally checks `document.visibilityState === "visible"` and nonzero viewport. A live MCP `tabs.list` success therefore says nothing about a fresh window frame.

Physical evidence from the canonical runbook: 10/10 consecutive captures of a changing RTL fixture **while foreground**, with 10 distinct image hashes; after HOME, `document.visibilityState=hidden` and screenshot FAILED; returning to Kelpie restored PNG capture. Source: PR #1 / issue #98 physical on-device tests. The successful repeated HOME->notification tap->foreground->PNG cycles establish recovery, **not** autonomous background image rendering. Other logged screenshot successes without lifecycle/readiness/frame markers do not establish a fresh background image: PixelCopy copies the latest available window buffer, which could be old if nothing newly rendered.

The user's goal is **many navigation/DOM/screenshot operations in one trusted session**, not repeated one-shot notification approvals. PR #2's cached single screenshot is at best an emergency bridge and must not be accepted as the steady-state automation architecture.

## Working decision candidate (not approved): separate three planes

```
Authenticated ChatGPT MCP client (scoped session + paired client identity)
                  |
        Existing tunnel / private MCP endpoint
                  |
    Browser Control Session Manager (not tied to Activity)
        - session_id, user grant, scope, TTL, explicit STOP
        - sequenced operations, tab ID, document generation
        - per-client authorization, audit metadata, cancellation
                  |
       Browser Engine & Renderer Abstraction
        A. native Android WebView for real-device functionality
        B. experimental offscreen Android renderer (only if hardware QA proves fresh frames)
        C. headless Chromium/CDP worker for unattended workflow
                  |
          Evidence & Output Service
        DOM/snapshot; fresh image with frame nonce, URL, tab,
        generation, capture monotonic time, hash, dimensions
                  |
     Kelpie UI = viewer + consent/control panel
   (can detach; must not be required for every MCP step)
```

A **single explicit user action** "Start controlled browser session" authorizes a clearly scoped, time-limited set of operations on selected sites/tabs for one paired MCP client; a persistent visible notification identifies the active session and provides "Pause" / "Stop" controls. Default new sessions are read-only, site-allowlisted and non-persistent across reboot unless the user explicitly reauthorizes. Mutations and sensitive workflows (credentials/payment/personal content) require separate policy/confirmation; consent for one session is never permission to control the entire phone.

### Three execution modes

1. **Live Android view (immediately proven):** Keep Kelpie visible on the S24 while controlling it from ChatGPT on a different device. Multiple screenshots and DOM actions can run without per-image notifications. On the same phone, user-initiated split-screen is a useful temporary smoke test (Android multi-resume exists) but changes the viewport, and current code's `hasWindowFocus()` guard may need evidence-based revision. Do not mark split-screen as an exact full-screen mobile QA substitute.
2. **Native background control (short-term engineering spike):** Retain the WebView session independently of Activity UI where feasible; test whether `WebView.draw(Canvas)` / `postVisualStateCallback` / `setOffscreenPreRaster` with an **attached** offscreen renderer can produce verifiably fresh frames on SM-S928B after HOME, app switch, screen off and unlock. Offscreen pre-raster is not a generic headless renderer or guarantee of background surface access. Fail closed if no new frame. Avoid Overlay, Accessibility, MediaProjection or forced Activity starts as a shortcut.
3. **True unattended rendering (robust target):** Add an authenticated, isolated **headless Chromium + CDP** worker, potentially hosted on an explicitly configured workstation/server, or investigate Termux aarch64 Chromium as a feasibility spike. Headless Chrome has a virtual screen and `Page.captureScreenshot`, independent of the Android Activity's window. Its browser profile, cookies and rendering engine are NOT the native Samsung WebView and cannot be claimed as physical Android UX QA. Keep separate session stores/permissions, do not copy credentials implicitly, never expose an unauthenticated CDP port (bind localhost and control through the existing authenticated gateway).

Recommend **hybrid architecture**: continuous background-capable browsing/DOM/screenshot on an independently rendered engine for routine workflow, plus periodic real Samsung WebView on-screen visual and gesture QA. If native offscreen WebView proves genuinely fresh and stable on hardware, use it as the primary mobile-native renderer; otherwise do not disguise remote desktop/headless emulation as a physical phone.

## Platform findings and non-solutions

- A network foreground service keeps a communications endpoint alive but does **not** confer an always-visible Activity window or a live PixelCopy source. `connectedDevice` FGS type has platform prerequisites; inspect actual use case and minimum permissions.
- Android PixelCopy on `Window` may return the last queued buffer; a non-error status alone cannot prove freshness. A frame must have a deliberate on-page nonce/change, capture timestamp and changed pixel/hash evidence.
- `WebSettings.setOffscreenPreRaster(true)` only describes an offscreen WebView **attached to a window**, and has memory costs. It is not a proof of continuous background rendering after the Activity is stopped.
- Do not use MediaProjection for browser-only automation: it can capture the device screen/other sensitive apps, requires distinct explicit consent per projection session, has lock/session lifecycle limits, and is not a persistent browser renderer.
- Do not fake notification consent using Accessibility, issue ADB `am start` as a runtime dependency, bypass keyguard, grant privileged permissions, or create a permanent unauthenticated LAN/CDP listener.
- Do not call a single cached-image handoff (PR #2 QA proposal) the final solution to a 30-step automation session.

## Hardware experiment gate: prove or falsify offscreen native rendering

Use a self-contained public/local HTML fixture rather than a sensitive website. The page contains large high-contrast color blocks, an incrementing visible nonce inserted by the authenticated test client, an animated tile, navigation and a scrollable region. Capture **new pixels** and cross-check DOM nonce and screenshot pixel region.

| Mode | Test | Required evidence |
| --- | --- | --- |
| Android foreground | 20 sequential DOM mutations + fresh snapshots + screenshots | 20/20 nonce/pixels match; no extra prompts |
| HOME / ChatGPT full screen | WebView DOM and screenshot at 5s / 30s / 2m / 10m | Explicit status + nonce/pixel freshness; report stale/blank/denied separately |
| Device screen off / lock | DOM/renderer state and safe policy | No keyguard bypass; no phone-screen leakage; precise suspension |
| Split-screen | ChatGPT + Kelpie both visible | Confirm `isShown`, `RESUMED`, focus, resize and new pixels; viewport difference noted |
| Offscreen WebView candidate | Pre-raster + Canvas draw and documented readiness | 20 changed frames verified after HOME, bounded memory/CPU; fail closed otherwise |
| Headless candidate | Chromium/CDP `Page.captureScreenshot` | 20 changed frames while UI detached; confirmed independent profile and isolated loopback CDP |
| Process stop / reboot | Reconnect and session invalidation | No unauthorized auto-restoration; explicit consent required to resume |
| Concurrent paired clients | request replay/scope attack | Tenant/client isolation; no cross-client capture retrieval |
| Network, Doze, user 95 | Wi-Fi/data transitions, Dual App collision | No duplicate ownership, port contention or unauthenticated endpoint |
| Long run | 24h with many authenticated read-only steps | No leak/crash; validated fresh outputs; clear pause/resume semantics |

No screenshots to pass unless dimensions, source tab/document version, frame nonce, monotonic capture time and actual changed pixels all match; a base64 image and HTTP 200 alone are insufficient. Use a bounded queue, backpressure, output TTL, cancellation and remote errors distinguishing renderer-unavailable, stale-frame, session-expired and consent-required.

## Next tasks and release gates

1. **Stop treating repeated notifications as the final UX.** Keep the existing user-tap recovery as a fallback, not a per-screenshot loop.
2. Add a read-only diagnostic on QA devices recording Activity lifecycle, WebView window visibility, attached/renderer status and PixelCopy result code **without page content, cookies or secrets**.
3. Run the controlled S24 native offscreen proof-of-concept and compare to headless worker proof-of-concept. Choose based on evidence, not speculation.
4. Design a per-client authenticated `startSession / inspect / navigate / capture / pause / stop` contract; require user approval of session scope and duration; separate read-only from modifying actions.
5. Require CI, contract/instrumentation tests, physical Samsung QA and rollback before installing. Do not merge Draft PR #2 or publish the app before critical gates.
6. Record owner approval in the canonical Drive CDG before promoting this PROPOSED design to an authoritative implementation decision.

## Sources (checked November 14, 2026)

- Android WebView reference: https://developer.android.com/reference/android/webkit/WebView
- WebView offscreen pre-raster: https://developer.android.com/reference/android/webkit/WebSettings#setOffscreenPreRaster(boolean)
- PixelCopy: https://developer.android.com/reference/android/view/PixelCopy
- Android Activity multi-resume/split screen: https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
- Android FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android background-start restrictions: https://developer.android.com/guide/components/activities/secure-bal
- MediaProjection: https://developer.android.com/media/grow/media-projection
- Chrome Headless: https://developer.chrome.com/docs/automation-and-testing/headless
- Chrome CDP Page.captureScreenshot: https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-captureScreenshot
- Local test evidence: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6873953432
- Earlier recovery evidence: https://github.com/imanipour2/kelpie/pull/1
- Canonical test log: https://docs.google.com/document/d/1DIhXXti6Md9MHMSmofsUFoLUajj-ISOB2vIANbIE7QM/edit

**No code deployment, APK install, Termux modification or Enerfoam website change is authorized by this proposal.**
