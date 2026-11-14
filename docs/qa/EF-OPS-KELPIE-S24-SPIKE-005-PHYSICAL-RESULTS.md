# EF-OPS-KELPIE-S24-SPIKE-005 — Physical Samsung VirtualDisplay report

**Date:** 2026-11-14  
**Classification:** QA research proof of concept — NOT a production browser acceptance  
**Device:** Samsung SM-S928B, Android 17, Android Owner profile (User 0)  
**Build:** `com.kelpie.virtualdisplayprobe` 0.1.0-qa (versionCode 1)  
**Source tested:** `f3682d624ea646502c2bbe5063775edc46ff314f`  
**Workflow:** [S24 Private VirtualDisplay Spike QA, run 43632783023](https://github.com/imanipour2/kelpie/actions/runs/43632783023), conclusion **SUCCESS**  
**Artifact:** 14128019624; ZIP SHA256 `6f2205538682710d47a0bfc5782c93591fbcf64fdccc65208361974688cee7a7`  
**APK SHA256:** `393226113074dfc5e051b54e7e5d257772bb2b25d65104f87296063fd626ef4e`  
**Installation:** USB ADB `install` SUCCESS; after installation, `pm path` + `adb pull base.apk` + SHA256 yielded the exact same APK hash.  
**Permissions:** zero requested in experiment manifest; no remote browser/tunnel/WordPress access.

## Direct hardware evidence

| Test | Procedure | Observed | Disposition |
| --- | --- | --- | --- |
| 1. HOME/background | Start isolated fixture while Android unlocked, press HOME, leave experiment running, read private JSONL with `run-as` | 22 journal events: `FIXTURE_READY`, 20 `FRESH_MATCH`, `COMPLETED`. All 20 samples marked `activityVisible=false`, and each pixel nonce/color matched with `frameTimeNs` newer than mutation | **PASS 20/20** |
| 2. ChatGPT foreground | Restart only isolated fixture, start 20-step run, switch foreground to `com.openai.chatgpt/.MainActivity` for the observation period, read private JSONL | 22 journal events; 20 `FRESH_MATCH` events with `activityVisible=false`. Android `topResumedActivity` readback was ChatGPT | **PASS 20/20** |
| APK integrity | Independently read installed `base.apk` | Installed and CI APK SHA256 identical, APK size 2,348,122 bytes | **PASS** |
| Existing browser unaffected | Read installed package `com.kelpie.browser.unifiedverify` | Still `0.2.6-qa` / versionCode 26 | **PASS** |
| Network isolation | Check ADB forwards and standalone manifest | No ADB port forward; no Internet/MediaProjection/Accessibility/overlay permissions | **PASS** |
| Probe teardown | `am force-stop` only `com.kelpie.virtualdisplayprobe` after saving evidence | Successful, without uninstall/clear of main browser | **PASS** |

**Machine-readable second-run evidence (safe metadata only):** [ChatGPT foreground JSONL](./evidence/s24-private-display-20261114-chatgpt-foreground.jsonl).

First-run raw JSONL was overwritten by the second run by design. The first run is supported by the recorded direct device readback in the execution log (20/20 match, 20/20 background, complete). Do not claim raw first-run artifact retention.

## Interpretation

This is **direct physical proof** that a private `DisplayManager.createVirtualDisplay` + `Presentation` + Android `WebView` + `ImageReader` can generate **changed pixels** while the hosting Activity is hidden on this specific physical S24/Android 17 build. The baseline WebView `PixelCopy(activity.window)` rejected hidden content; the virtual-display experiment uses an owned surface and therefore does not depend on the Activity's visible window.

This result does **not** prove:
- that the exact **existing Kelpie browser tab** or its in-memory JavaScript, scroll, cookies, authentication and form state can be moved/reparented to and from a `Presentation`;
- that arbitrary web pages, scrolling, canvas/video/WebGL, IME, RTL and accessibility are fully rendered;
- unattended reboot restoration, 10-minute/24-hour stability, Doze, keyguard behavior, battery or RAM/GPU bounds;
- authenticated session management or cross-client security;
- permission to publish a browser or to access private user pages.

The demo WebView was a completely separate, local-only synthetic fixture. The experiment was launched with QA ADB, but **rendering proceeded independently after HOME**. This should not be misrepresented as a production no-ADB controller or a browser tab migration test.

## Proposed next engineering gates

1. **P1 — Same-WebView surface migration:** build a small instrumented test that reuses the same WebView instance between the real Activity and an app-owned `Presentation`, with explicit renderer lifecycle checks; verify URL, document nonce, form values, DOM state, scroll, cookies and tab history survive. Never destroy the original browser session to fabricate equivalence.
2. **P2 — Long-run renderer:** 20 cycles repeated over 10 minutes, 1 hour and 24 hours; CPU/GPU/RAM metrics, surface leaks, orientation, keyboard, animation and WebGL/video content; screen lock must suspend sensitive activity and must not unlock automatically.
3. **P3 — Session control:** introduce per-client, authenticated, explicitly user-approved scoped sessions with TTL/Pause/Stop and bounded access to selected tabs/sites. No default permanent public CDP port, no screenshot of other apps.
4. **P4 — Protocol and QA:** attach `rendererId`, `sessionId`, `tabId`, `documentVersion`, `frameTimestamp` and nonce/pixel proof to screenshot results; throttle concurrent requests and reject stale frames/cross-client replays.
5. Keep the existing PixelCopy foreground path for physical visible UI QA and keep notification recovery as an emergency fallback, not a per-frame workflow.

**Approval:** Owner permitted the bounded PoC. This evidence does not elevate a proposal into a final CDG architecture/release approval. The S24 probe can remain installed but is stopped. The actual Kelpie browser is unchanged and Enerfoam LIVE/STAGING/WordPress were not modified.

**Result:** `PRIVATE_VIRTUAL_DISPLAY_BACKGROUND_RENDER_PROVEN_ON_SM-S928B` / `SAME_TAB_MIGRATION_UNTESTED` / `LONG_RUN_UNTESTED` / `SESSION_SECURITY_OPEN` / `RELEASE_NO_GO`.
