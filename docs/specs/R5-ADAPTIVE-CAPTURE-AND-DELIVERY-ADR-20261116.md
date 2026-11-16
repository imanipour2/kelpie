# R5 Adaptive Capture & Delivery Controller — Engineering ADR (DRAFT)

Status: **PROPOSED / isolated policy prototype, NOT wired to production renderer**
Date: 2026-11-16
Base: R5 native background agent fix `e31fc6532387fc39dad9fcc507086273aece0ce8` (draft PR #31)
Scope: Android policy proof-of-concept; eventual shared cross-platform behavior parity must be reviewed.
Owner input: use actual device resources **and** the AI consumer's visual task to choose image fidelity and transfer cost, without retaining screenshots or losing native WebView proof.

## Source observations

R5's `PrivateWebViewDisplay.kt` creates an app-only `VirtualDisplay`, `Presentation`, an `ImageReader(RGBA_8888, maxImages=2)`, copies one freshly proved frame to `Bitmap`, compresses with PNG, returns Base64. The compositor-origin nonce validates freshness **before** any optional image scaling or encoding. One-shot buffers are closed/recycled; the fixed 512-frame nonce budget is a separate engineering issue. `AgentCommand.kt` currently has no screenshot-purpose, preferred format or decoder-capability fields. The ordinary upstream Kelpie screenshot API also returns PNG/JPEG with optional CLI file output. Do NOT claim AI-side capability negotiation or binary transport already exists.

## Decision: two-stage, cost-aware, fail-closed pipeline

1. **Native acquisition:** Keep exact WebView identity, active native session, nonce proof and document/lock/STOP fences. Source frame remains a full-resolution native capture for now. Never use a screen capture of another Android application. Do not pre-scale the VirtualDisplay until separately proving the original WebView layout/scroll/hit-test invariants at the changed size.
2. **Pure decision planner:** The isolated `R5AdaptiveCapturePolicy` selects delivery encoding/resolution *after proof* from Android signals, explicitly declared AI task purpose and **negotiated** receiver support. This phase has no Android calls, does not access image bytes, and does not authorize capture.
3. **Bounded encoder:** In a later PR, apply the chosen output scaling/encoding to a single proved Bitmap; send content plus correct `sourceWidth/Height`, `outputWidth/Height`, mapping factors, native/tab/document proof and actual MIME, with one in-flight request, strict memory cleanup on every path. Keep the existing Base64+PNG wire contract for all legacy callers until the consumer negotiates a compatible extension.
4. **No screenshots persisted by default:** Release native Image and Bitmap immediately after encoding/delivery, with a bounded transient transport buffer. Persistent QA files must be explicitly enabled separately. No claim that clearing local buffers deletes copies previously accepted by the AI host.
5. **Adaptive feedback:** Use measured encode time, recent complete transfer latency, image byte count, actual slow/failed deliveries and Android resource/thermal state. Link-bandwidth estimates are advisory only, never proof of final AI endpoint throughput. Use hysteresis/cooldown across requests to avoid expensive oscillation. The pure prototype uses conservative fixed thresholds only as hypotheses requiring telemetry.
6. **Scale/fidelity correctness:** Native full-resolution lossless PNG/WebP for `FINE_TEXT`, `COORDINATE_ACTION`, `VISUAL_EVIDENCE`. Under severe memory or thermal pressure, return a *deferred* typed status rather than silently shrinking critical text/actions. For visual overview/layout, negotiated downsampling may lower transmission bytes. JPEG/WebP lossy allowed only for photographic/scene intent and negotiated decoder support, never the default for UI text, numeric tables, code, or click evidence. The planner never upscales low-resolution source.
7. **Task input trust:** Requested task purpose is not permission. Only a trusted agent runtime may request task intent, and the runtime must upgrade to precise capture when the next action requires readable fine text or coordinate anchoring. No hostile page script may set image policies, override native consent or influence targeted capture permissions.
8. **No global ADB input:** The earlier incident in ENERFOAM issue #98, where global `adb shell input text` wrote into ChatGPT composer, remains P0. QA must not automate global text/taps as a way to drive R5 or obtain consent. This policy does not address that P0 by itself.

## Android observables and limitations

- `ActivityManager.isLowRamDevice()`, `getMemoryClass()`, `getMemoryInfo()` indicate low-RAM class, process memory class and broader pressure; these are not a guarantee that a GPU buffer allocation will succeed.
- `PowerManager.getCurrentThermalStatus()` and `isPowerSaveMode()` indicate system pressure; don't confuse processor count with sustained rendering performance. Guard calls by API level and handle missing/unavailable signals.
- `ConnectivityManager.getNetworkCapabilities()` and underlying transport can provide link estimates, but end-to-end delivery to an AI model must be measured independently.
- `Bitmap.compress(PNG/WEBP_LOSSLESS/WEBP_LOSSY/JPEG)` capabilities depend on Android API level. PNG is backwards compatible; lossy output is opt-in and must be decoded/tested at receiver. Hardware compositing of WebView does not imply the screenshot encoder is hardware-accelerated.
- Source 1080x1920 RGBA = 8,294,400 bytes (~7.91 MiB). A smaller **encoded** output does not retroactively lower the full-sized ImageReader/GPU acquisition buffer's peak. Adaptive acquisition should be a separate future decision based on device testing.

## Pure prototype policy table (not production thresholds)

| Task intent | Output | Under pressure | Safety behavior |
| --- | --- | --- | --- |
| LEGACY (omitted) | Original size, PNG | Preserve original contract | No silent wire change |
| FINE_TEXT / COORDINATE_ACTION / VISUAL_EVIDENCE | Native size, lossless PNG or negotiated WebP lossless | Defer if critical | Never lower fine-text or click fidelity |
| LAYOUT | Up to 1920 long edge; 1536 under limits | Negotiate scale metadata | Refuse incompatible receiver cap |
| OVERVIEW | Up to 1536 long edge; 1280 under limits | Negotiate scale metadata | No upscaling |
| PHOTO_SCENE | Similar to overview | Negotiate lossy format and quality | JPEG/WebP-lossy only with explicit support |

`memoryClassMiB < 192`, speed indicators, long-edge caps and quality=86 are **starting candidates**, not Android's authoritative thresholds. Calibrate on low-memory hardware and representative Persian/English/RTL/UI text before shipping; do not hardcode them as unreviewed product promises.

## Multi-gate acceptance

1. Unit tests: old-client PNG fidelity, image-purpose choices, invalid input rejection, no enlargement, receiver lacks scale metadata, precise action under pressure, format negotiation, byte/encoder failures, and permission-independent deterministic decisions.
2. Renderer + protocol: snapshot nonce match is verified before any image transformation, screenshot stays tied to immutable owner principal/tab/document/session, correct MIME/scale metadata, no extra screenshot archives, canceled/STOP cleanup, no second unconsented request.
3. Device: baseline and optimized outputs on Samsung SM-S928B Android 17; measure true PNG/WebP file byte sizes, encoding latency, end-to-end transmission cost and click accuracy. Add a low-RAM 3/4 GB Android target and thermal/power/network scenarios. Compare fine UI text and RTL legibility at 1080, 720, 540 resolutions.
4. Stress: 20/20 compositor proof first, then 512/513/1000/2000 under nonce-epoch design **in a separately reviewed PR**, profile PSS/RSS, native/graphics memory, GC, FD/GPU surfaces and no retained content. No automatic retry of uncertain mutating operations.
5. Cross-platform: upstream Kelpie requires Android/iOS parity. Define equivalent protocol semantics and fallback for iOS before integrating into main/release.

## Implementation status and limitations

A pure Kotlin planner and focused JUnit tests are committed to a **dedicated unmerged development branch**, and the policy has been compiled and smoke-tested independently with a local `kotlinc` runner. This **does not prove** Android Gradle build, native capture integration, 2,000-frame stress, low-end phone suitability, energy improvement or actual AI image comprehension. The laptop Browser Use/ADB tunnel previously returned `Tunnel-client did not poll`; real hardware QA cannot be claimed for this policy from this session.

## References (source / role)

- Android [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager), [PowerManager](https://developer.android.com/reference/android/os/PowerManager), [NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities) — source for device signals; Android SDK/API checks required.
- Android [ImageReader](https://developer.android.com/reference/android/media/ImageReader) and [Bitmap.CompressFormat](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat) — buffer lifetime and format capabilities.
- Android [memory profiling](https://developer.android.com/topic/performance/memory) — runtime measurements, not benchmark predictions.
- [Playwright screenshots](https://playwright.dev/docs/screenshots), [Chrome DevTools Protocol Page.captureScreenshot](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-captureScreenshot) — optional on-wire image detail/format and screenshot control comparators.
- [Kelpie upstream Android screenshot](https://github.com/UnlikeOtherAI/kelpie/blob/main/core/android/src/main/java/com/kelpie/browser/handlers/ScreenshotHandler.kt), [upstream CLI screenshot](https://github.com/UnlikeOtherAI/kelpie/blob/main/packages/cli/src/commands/screenshot.ts) — no mandatory persistent screenshot storage.
- ENERFOAM [operational issue #98](https://github.com/imanipour2/enerfoam-website/issues/98), Kelpie draft PRs [#30](https://github.com/imanipour2/kelpie/pull/30) and [#31](https://github.com/imanipour2/kelpie/pull/31) — hardware context, safeguards and unmerged code.

**Gate:** `POLICY_PROTOTYPE / NO_APP_WIRING / NO_ANDROID_APK / HARDWARE_NOT_RETTESTED / SYNC_BLOCKED_MAIN / RELEASE_NO_GO`.
