# Kelpie S24 Private VirtualDisplay Research Probe

**Experimental, separately installed, permission-minimal APK. Not a Kelpie replacement and not a browser release.**

## Purpose

Determine whether a WebView hosted inside an application-owned private
`VirtualDisplay` and `Presentation` can generate **fresh, provably changed
frames** while the launcher, ChatGPT, or another Activity is foreground on
Samsung SM-S928B / Android 17. The experiment does not depend on accessibility,
ADB foreground commands, media projection, overlays, screenshots of third-party
apps, the Termux tunnel, or network connections.

The private rendering path uses `DisplayManager.createVirtualDisplay`,
`Presentation.setContentView(WebView)` and `ImageReader` (RGBA_8888). Each
test iteration sets a new RGB background through the fixture's JavaScript.
The reader samples a pixel at (160, 240). A frame passes only when its callback
arrives *after* the mutation and its RGB values match the expected per-sequence
nonce. Bitmap/image content is never saved or transmitted.

## Permission boundary

The AndroidManifest requests **zero Android permissions**. It declares one
user-launched exported Activity, no services/receivers or network access.
The fixture is `loadDataWithBaseURL` with only static inline HTML/JS and
`https://fixture.invalid/` as its fictional base; no external resources.
The app never accesses Chrome/Kelpie tabs, profiles, cookies, credentials,
the phone's screen or another application's surfaces.

The output is a private text file
`files/virtual-display-probe.jsonl`: event, sequence, main Activity visibility,
monotonic time, sampled RGB, callback timestamp and pass/fail only.

## Build

Run **only** the opt-in project profile:

```sh
cd apps/android
./gradlew --no-daemon :virtual-display-spike:ktlintCheck :virtual-display-spike:testDebugUnitTest :virtual-display-spike:lintDebug :virtual-display-spike:assembleDebug -PvirtualDisplaySpike=true
```

Default Kelpie builds do not include this module. The resulting APK has
application ID `com.kelpie.virtualdisplayprobe` and can coexist with the
installed `com.kelpie.browser.unifiedverify`.

## Physical test protocol (owner initiated)

1. Verify device identity `SM-S928B`, Android version, package name,
   artifact SHA256 and that no matching production package would be replaced.
2. Install the **verified probe** without uninstalling or resetting Kelpie.
   Open the app; use the **Start 20-frame local fixture** button.
3. Immediately press HOME and leave the probe running; do not lock the device.
   The 20 scheduled mutations take about 29 seconds after the 3-second grace.
4. Return to the probe after at least 35 seconds. Read the on-screen result.
5. For independent review, use USB debug `adb shell run-as
   com.kelpie.virtualdisplayprobe cat files/virtual-display-probe.jsonl` and
   save the sanitized JSONL to a private QA location. No screenshots or private
   user content are stored. Review failures, frame timestamps, and
   `activityVisible=false` per iteration.
6. Repeat while ChatGPT is the foreground Activity. Perform separate
   multi-window, network, orientation and longer idle testing only after this
   first proof gate. The test deliberately **stops on keyguard lock**; it does
   not unlock or override the lock screen.
7. Use `adb shell am force-stop com.kelpie.virtualdisplayprobe` to safely end
   the probe. Remove this separate QA app only after logging evidence.
   No Kelpie/Termux/WordPress changes are needed.

## Acceptance and limitations

**Research PASS** requires 20/20 changed, timestamp-valid, pixel-matching
frames with the *main* Activity not visible, no permission prompts, and
retained/verified JSONL. A static PNG hash, API success, DOM success or a
sample captured before HOME does **not** prove background fresh rendering.

**Research FAIL** includes no new frames, stale colors, killed process,
unexpected required permissions, or renderer/system instability. Frame
production while an Activity remains visible does not meet the background gate.

This is *not* a direct same-tab/state-migration demonstration: the standalone
probe deliberately creates a second, fixture-only WebView. If the hardware
research succeeds, a separate design and test must establish original-tab
state integrity, client authentication, per-session consent, cleanup,
CPU/RAM/GPU bounds, renderer upgrade resilience and 24-hour stability.

Official context:
- https://chromium.googlesource.com/chromium/src/+/refs/tags/108.0.5359.153/android_webview/docs/software_draw_deprecated.md
- https://developer.android.com/reference/android/hardware/display/DisplayManager
- https://developer.android.com/reference/android/app/Presentation
- https://developer.android.com/reference/android/media/ImageReader
- https://developer.android.com/guide/components/activities/secure-bal

**Status: experimental / CI and physical QA required / no release authorization.**


## P1: Same-WebView migration research (2026-11-14)

**Separate hardware gate. This does not make the production Kelpie background-capable.**
The second button in the fixture launcher opens `SameWebViewActivity` in the same offline-only, permission-free QA package. It creates **one** WebView in the Activity, seeds a synthetic DOM nonce, text input, history state and scroll position, then reparents the exact WebView object into a private `Presentation` backed by `VirtualDisplay + ImageReader`. It never loads a real site or credentials.

**Procedure:** Start the QA app, select **Open SAME-WebView migration QA**, then tap **Start migration / 20 changed frames**, immediately press HOME and leave the display unlocked. After at least 35 seconds, return to QA and tap **Restore SAME WebView to Activity**. Read `files/same-webview-migration.jsonl` with authorized `adb shell run-as com.kelpie.virtualdisplayprobe cat files/same-webview-migration.jsonl` and verify:
- `MIGRATED_SAME_OBJECT` is matched; no second WebView was substituted.
- Exactly 20/20 `FRESH_MATCH` entries with `activityVisible=false`, post-mutation callback times and nonce pixels.
- `RESTORE_VERDICT` is matched: the same Java WebView object, document identity, synthetic text input, history marker, and scroll are intact.
- `KEYGUARD_STOP`, failures, incomplete frames, a stale document, or state drift are **FAIL** and require investigation.
- The synthetic state is logged solely for comparison, **not real browser text**. No screenshots are retained.

This is deliberately a stricter experiment than the earlier fresh-frame-only PoC. It may fail due to Android View reparenting, display/context mismatch, a WebView renderer recreation, viewport reflow, or resource/lifecycle changes. Failure must **not** be hidden by creating another WebView and copying cookies; that would invalidate the same-tab claim.

**CI** checks only compile/lint/unit/packaging; it cannot prove physical Android rendering. Perform signer/ABI/package/rollback checks before installing this QA APK and preserve the already installed `com.kelpie.browser.unifiedverify` as-is. The check is non-production and must not be merged or released as a working background browser.

Further gates after physical PASS: repeat 10-minute and 1-hour capture; multi-tab, IME/RTL/touch and navigation restoration; memory/GPU/lock/Doze; user-approved scoped authenticated sessions, per-client identity, expiry/Pause/Stop; security review and proper native renderer integration. No lock bypass or screenshot of another application.
