# Kelpie S24 P4 private-display frame failure — evidence-based RCA and bounded fix

**Mission:** EF-OPS-KELPIE-S24-REMEDIATION-002 · **Date:** 2026-11-15 · **State:** SOURCE REMEDIATION IN DRAFT / REAL-HARDWARE RETEST PENDING / RELEASE_NO_GO

## Original, unchanged acceptance target

After an explicit local positive gesture, a single verified paired client may request at most 20 *fresh* images during a 90-second grant from exactly the existing browser tab/WebView while the Activity is not visible and Android remains unlocked. The same Java WebView, document, history, form and scroll state must be restored when the Activity resumes. No screen-lock bypass, other-application capture, silent consent, generic WebView origin, token/image storage, automatic real-account capture or production release.

**Owner approval:** canonical CDG owner authorization `EF-OPS-KELPIE-S24-SPIKE-005` for bounded public/synthetic hardware research; implementation is stacked Draft [Kelpie PR #11](https://github.com/imanipour2/kelpie/pull/11) and auxiliary CI-only [PR #12](https://github.com/imanipour2/kelpie/pull/12). Never merge #12 as a feature or infer that a successful Android CI is approval of a published browser.

## Physical evidence (source of observed facts)

- Real Samsung SM-S928B, Android 17, independent `com.kelpie.browser.pcverify` 0.2.9-qa, installed APK SHA256 `b8dc3dfbf4863314279311711de60cdb1b1db94005d43d909af0315e5d8282da` from exact source `3054ec63a8395dc3537ee65af2bafb70b9058b44`.
- On-device native affirmative consent was **actually** recorded: `APPROVED` and `renderer=PRIVATE_DISPLAY`. ADB confirmed the original QA Activity was hidden behind ChatGPT.
- Five successive `capture-session-frame` attempts returned `SCREENSHOT_FAILED`, no valid PNG. Later status became `NOT_FOUND` while renderer briefly still reported `PRIVATE_DISPLAY`; activity return brought the display to `IDLE`. A separate read afterward reported `about:blank`, but this **does not establish** a same-object restoration failure without a complete pre/post document and process identity record.
- All hardware results are operational evidence in [ENERFOAM Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6894312744). No private screenshot data was captured.

## Proven difference from working P3

The isolated [P3 Compose QA](https://github.com/imanipour2/kelpie/blob/experiment/s24-p3-compose-owner-qa-20261115/apps/android/app/src/main/java/com/kelpie/browser/P3ComposeQaActivity.kt) produced 20/20 synthetic *pixel-matched* hidden frames and passed same-WebView restore on this phone at commit `429811e`. In each frame it used `WebView.evaluateJavascript("window.setProbe(sequence, r, g, b)")`, then sampled the changed RGB bytes at a known coordinate. **Receipt** timestamp was read using `SystemClock.elapsedRealtimeNanos()` *in the callback*; it did **not** compare the platform `Image.timestamp` to that clock. P3 also used a deliberately small 320×480 private display.

P4 used `view.postInvalidateOnAnimation()` as its only synthetic update and rejected `Image.timestamp <= SystemClock.elapsedRealtimeNanos()`. The two APIs are not guaranteed a common timestamp base. Even when clocks match, a bare invalidate on a hidden Activity may produce **no** new composited frame. P4's original ImageReader was up to 1080×1920, another untested difference. Hence the physical outcome cannot isolate the timestamp rejection from absent onImage callbacks, stale pixels, 1080p buffer pressure, or compositor STOP behavior.

## Independent cross-browser/platform research

1. [Android Image.getTimestamp](https://developer.android.com/reference/android/media/Image#getTimestamp()) explicitly warns image sources may use different nanosecond timebases. **Applied:** do not compare it to elapsedRealtimeNanos as proof of freshness.
2. [Chromium WebView BrowserViewRenderer](https://chromium.googlesource.com/chromium/src/+/refs/tags/37.0.2024.2/android_webview/browser/browser_view_renderer.cc) documents that Android may ignore invalidates while WebView is invisible and fallback ticks stop when an attached window is backgrounded. **Applied:** drive an observable document paint rather than merely invalidating a View.
3. [Chromium March 11, 2026 background thumbnail fix](https://chromium.googlesource.com/chromium/src/+/8199988e9a513fce11c259beef38ccb23d245c5e) traced failed `CopyFromSurface` to an opaque covering Activity stopping the old compositor; its narrow fix kept the original Activity PAUSED rather than STOPPED via translucent treatment. **Interpretation:** Chrome depends on preserving the surface/compositor; this is NOT permission to fake foreground visibility in Kelpie and is not proof that WebView automatically supports hidden capture.
4. [Chromium TabContentManager](https://chromium.googlesource.com/chromium/src/+/b30a4a65c0eaa51e9270011aeadb242543ed1050/chrome/browser/android/compositor/tab_content_manager.cc) explicitly refuses thumbnails of tabs lacking a visible Surface. **Interpretation:** generic Chrome thumbnail APIs do not solve unattended hidden capture.
5. [Firefox GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html) describes releasing GeckoView's compositor surface when a view is invisible and managing session active state explicitly for nonstandard hosts. [GeckoDisplay.capturePixels](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoDisplay.html) requires a ready compositor. **Interpretation:** an engine swap is not an established quick fix; it would create a new product architecture and iOS parity challenge.
6. [Android VirtualDisplay](https://developer.android.com/reference/android/hardware/display/DisplayManager) provides an app-supplied Surface and requires release at completion, not automatic background rendition. [Android Compose AndroidView](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose) requires correct ownership and remounting.

The result of reviewing Chrome/Firefox is **a compositor/visibility invariant**, not a claim that they already deliver arbitrary new screenshots from any stopped Android app.

## Narrow source remediation (Draft only)

- `PrivateCaptureQaPixelProof`: alter exactly one public `https://example.com` DOM-only 80×80 noninteractive rectangle with a deterministic per-attempt RGB nonce; refuse other origins, alternate ports and credentials. Clear the marker upon restore. It is not authorized for private URLs, signed-in sites, any broad origin or general screenshots.
- `PrivateWebViewDisplay`: wait for `ImageReader` callbacks, compare a pixel against the *current* nonce before accepting the frame, drop all stale/blank/mismatched frames without caching, enforce lease expiry/STOP/hidden/keyguard as before. Never classify a frame successful merely because a callback occurred.
- `capture-session-status` now includes bounded callback/match counts and an outcome enum, only on authenticated QA HTTP paths. Never disclose frame pixels, URLs, cookies or tokens.
- JVM tests check 20 unique nonce colors, rejection of stale pixels, refusal of non-public origin scripts, and 20-frame limit. This is **necessary, not sufficient** physical proof.

## Follow-up test matrix and gates

1. Exact final SHA: Android ktlint/JUnit/lint/APK build CI PASS; audit QA-only manifest, appId, debug signer and SHA against the installed pulled-back APK.
2. No user gesture: background screenshot **DENIED**, no marker, STOP/TTL no stale display.
3. Explicit physical native consent, public `example.com` only; record `imageCallbacks`, `matchedNonceFrames`, `lastFrameOutcome` on physical Android 17. If callbacks=0 investigate compositor/Surface; if callbacks>0 with zero matches inspect pixel origin/size/color without leaking frame; if match but PNG fails inspect bitmap and payload.
4. Freshness: exactly 20 successive, uniquely colored visible nonce frames while QA Activity is hidden; each verified by pixel values, monotonic receipt and no repeated stale frame; no privileged capture APIs.
5. Restoration: record process ID, original tab/webView identity, original URL/document proof/form/history/scroll before migration and after return. Do not pass when about:blank or a recreated tab is detected. Distinguish baseline HOME navigation timeouts from migration-specific regression.
6. Lifecycle: STOP, cancel, expired grant, wrong client/attempt, tab change, redirect, lockscreen, app kill, offline, resumed Activity, rotation; reject frame and clean up reliably. Verify cross-platform iOS parity separately.
7. Keep debug QA isolated and Draft, no stable production release / website deployment until separate security review, CDG owner decision and real-device acceptance.

**Status:** `RCA_SUPPORTED / PROPOSED_FIX_SOURCE_COMMITTED / CI_AND_PHYSICAL_RETEST_OPEN / RELEASE_NO_GO`.
