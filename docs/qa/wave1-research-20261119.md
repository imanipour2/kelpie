# Kelpie Wave 1: native capture research and acceptance contract

Mission: `EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001`.
Review date: 2026-11-19. Scope: Android private beta source, not an installation
or a cross-platform release. The owner-directed review/install gates take
precedence over the repository's automatic release instructions.

## Primary sources and implementation consequences

| Source read | Version or scope | Consequence in this branch |
| --- | --- | --- |
| [WebView visual state](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback)) | API 23+; current reference | Callback establishes readiness for the next draw, not arrival of an ImageReader frame. It excludes video. Require attachment, positive CSS/native dimensions, a matching callback, and actual pixels from both document and native host. |
| [ImageReader](https://developer.android.com/reference/android/media/ImageReader) | API 19+; current reference | `acquireLatestImage` needs two available image slots to discard older images. Keep maxImages=2, close every acquired Image in finally, and serialize reader closure with in-flight copying. |
| [Image timestamps](https://developer.android.com/reference/android/media/Image#getTimestamp()) | API 19+; current reference | Producer timestamps can have source-specific timebases. Compare only within the same ImageReader. Record a separate elapsed-realtime acquisition timestamp; do not compare Image.timestamp to SystemClock as proof of freshness. |
| [DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager) | Private virtual displays; API 19+ | Keep PRESENTATION and OWN_CONTENT_ONLY. Do not use PUBLIC, AUTO_MIRROR, SECURE or a MediaProjection token. Secure content is blank on an unsecure display. Third-party applications cannot obtain CAPTURE_SECURE_VIDEO_OUTPUT. |
| [Presentation](https://developer.android.com/reference/android/app/Presentation) | Own display context; Android S window-context rule | Inflate the native proof host using Presentation.context. Changing the window type after creation may cause InvalidDisplayException. Retain the original WebView rather than copying its session into another instance. |
| [PixelCopy](https://developer.android.com/reference/android/view/PixelCopy) | API 24+ | SOURCE_NO_DATA means no producer buffer; SOURCE_INVALID can mean protected/destroyed content. Timeouts and invalid sources are refusals, never permission to return cached bytes. |
| [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE) | Current platform contract | Refuse protected-window capture. Do not change the flag or use a secure virtual display to recover blocked pixels. |
| [WebView termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination) | Updated 2026-10-23 | Every affected WebViewClient must handle renderer death. Remove/destroy the dead instance and references; it cannot be reused. A replacement requires a new native identity and fresh owner authorization. Background recovery must not start an Activity. |
| [Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent) | Command dispatch 0.95+; result callback 0.109+ | Requires user-granted RUN_COMMAND and user-enabled allow-external-apps. Use an explicit fixed service and reviewed fixed script. For results, use an explicit, one-shot PendingIntent with unique requestCode; API 31+ requires MUTABLE so Termux can supply result extras. Dispatch is not execution or owner consent. Do not copy the example's stdout/stderr logging into Kelpie. |
| [Android app signing](https://developer.android.com/studio/publish/app-signing) and [APK v3](https://source.android.com/docs/security/features/apksigning/v3) | Android signing and verified proof-of-rotation | Match the installed certificate or validate an actual signing lineage. A newly generated debug key cannot upgrade the installed package. Keep signing, restoration and owner installation gates separate from a build pass. |
| [SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo) | API 28+ | Multiple signers identify a package as a set; do not accept any-one match. Rotation history is supported for single-signer packages and must be verified by platform policy. |
| [Android 17 changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all) | Android 17 / API 37 | Cross-profile loopback is restricted; same-profile loopback is unchanged. Read back Kelpie and Termux user/profile identities. Do not change ports to compensate for a consent error. |
| [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) | Target SDK 37+ | ACCESS_LOCAL_NETWORK applies when targeting 37+. The official guidance says not to add/request it for targets <=36. Do not opportunistically bump targetSdk or alter existing loopback/Termux configuration in this wave. |

The Android references establish contracts, not proof that this Samsung device
or its current WebView provider satisfies the complete acceptance matrix.
Device OS, provider version and targetSdk must be collected independently.

## Community reports used only to choose regression fixtures

- [Flutter #148344](https://github.com/flutter/flutter/issues/148344), opened
  2024-05-14, reports blank/partial platform-view presentation which appears
  after scrolling. One discussion reports Flutter 3.22, Samsung S20FE and
  Android 13; it did not reliably reproduce on a further approximately 20
  attempts. This supports repeated first-frame, attach/resize and screenshot
  integration tests. It does not establish the same cause in Kelpie or Android
  17 and does not justify disabling hardware acceleration.
- [Termux #5047](https://github.com/termux/termux-app/issues/5047), opened
  2026-04-03, describes PendingIntent calls lacking a mutability flag and a
  hypothetical failure after raising targetSdk from 28 to 31+. It is a
  code-level report, not evidence that the owner's installed Termux crashes.
  Notification intents and result intents have different mutability needs;
  use the official RUN_COMMAND result contract rather than blindly applying
  FLAG_IMMUTABLE to all intents.
- [WebView #572898003](https://issuetracker.google.com/issues/572898003) search
  excerpts describe scroll-animation freezes on WebView 153–156. Full issue
  content required sign-in during this review, so this is not used as a
  confirmed root cause or a basis for a code workaround.
- [Chromium bug-report guidance](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/bugreport.md)
  recommends a minimal reproduction and contemporaneous device diagnostics.
  For this project, retain only sanitized fixture evidence; do not publish the
  owner's complete bugreport, accounts, real screenshots or token-bearing logs.

## Root causes identified in the prior capture implementation

1. Native nonce markers were repainted before WebView visual readiness, and
   ImageReader accepted them without requiring the visual callback. A new
   host surface could therefore contain a stale or gray WebView layer.
2. A native overlay proves the host compositor changed, not that the WebView
   content changed. Wave 1 also requires an ephemeral two-color marker in the
   actual document. There is no persistent content script or listener. It is
   removed after capture and on cancellation/STOP. Both proof regions are
   excluded from blank-frame detection and covered before PNG encoding.
3. The padded-bitmap path used `Bitmap.createBitmap` and recycled its source
   unconditionally. With identical bounds the API can return the same Bitmap,
   making the result recycled. It also assumed trailing padding after the last
   image row. The replacement copies bounded RGBA rows into a distinct bitmap,
   honors buffer position/stride and never exports padding.
4. STOP suspended for CSS scroll reads before invalidating pending frames and
   transfers. Now the existing display's operation generation and pending
   frame are invalidated synchronously before any suspension. Late callbacks
   cannot reactivate a stopped owner or publish a bitmap to an abandoned waiter.
5. Detaching only the recorded owner could leave a transfer's not-yet-committed
   WebView in the private host. Host disposal now detaches all child views,
   without destroying or copying either real tab.
6. Reader closure and native-buffer access could overlap. Acquisition and
   closure now share one short, bounded image-resource lock. This is distinct
   from, and does not replace, the coordinator's transfer mutex or consent state.

## Reuse and seams

`PrivateWebViewDisplay` remains the only lifecycle/ownership state holder.
The existing `OneShotFrameBuffer`, `R5FrameNonce`, epoch sequence,
`R5CssRestoreTarget` and viewport-admission contract remain in use.

- `R5PrivateFrameAcquisition`: one pending image and reader resource ownership.
- `R5RgbaFrameBuffer`: bounded plane geometry, pixel copy and blank-page rejection.
- `R5FrameContentCheck`: shared opaque-content/blank-frame check for both private
  ImageReader and visible-window PixelCopy. Proof regions cannot supply the
  content variation, and a later transparent sample cannot be hidden by an
  earlier nonuniform sample.
- `R5PageFrameProof` / `R5FrameProofVerifier`: request-local document witness and
  comparison with actual acquired pixels.
- `R5WebViewScrollRestoration`: stateless existing CSS restoration logic.
- `WebViewCapturePrivacy`: shared pre/post admission, owned by the privacy change.

Private capture returns pixels only after native ownership, viewport, visual
readiness, both nonce pairs, source timestamp progression and independent
pre/post privacy receipts agree. Native image timestamps are compared only
within the same reader; elapsed-realtime receipt timestamps are labeled
separately. Synthetic proof pixels are masked, not passed off as page content.

The privacy policy currently admits only natively registered, reviewed synthetic
fixtures. Real-page pixels return a typed refusal until reliable redaction and
hardware validation exist. This limitation is intentional and keeps the beta
installation decision NO_GO; it is not described as complete real-site capture.

## Test oracles and remaining gates

| Layer | Added or retained executable checks | Meaning and limit |
| --- | --- | --- |
| JVM plane geometry | Unpadded input, missing final-row padding, buffer position/crop, truncated buffer, overflow and budget limits | Verifies real RGBA copying and fail-closed bounds independently of Bitmap |
| JVM freshness | New host/old document, old host/new document, stale pair and all-current pair | Both actual pixel sources must match, not just a successful callback |
| JVM image quality | Gray page with fresh markers; non-marker scene variation | Marker colors cannot make a blank WebView pass |
| JVM viewport | CSS/native scaling, zero/offscreen/non-finite dimensions | Coordinates must be grounded in the captured viewport |
| Existing ownership | OneShotFrameBuffer cancellation and 200 concurrent STOP/callback races | A late or abandoned bitmap has exactly one disposal owner |
| Android instrumented Surface fixture | 25 ImageReader frames, monotonically advancing producer timestamp, proof-region masking; gray-frame refusal | Exercises native ImageReader/Bitmap, not the S24 WebView/background lifecycle |
| Physical acceptance still required | 20 tab cycles, 25 real WebView frames, 50 display create/release cycles, HOME/lock/rotation/render crash/STOP race, memory/CPU and scroll/history preservation | Must run against the exact built APK and same Samsung package after signing/recovery gates |

Tests being present is not a pass. Actual test execution and CI results are
recorded separately against the final commit. No physical device result,
restoration proof, certificate match or installation approval is inferred here.

## Renderer-loss integration

`HandlerContext.nativeRendererGone` is a native-only callback. TabStore invokes
it before publishing STOP or cancelling suspended browser work. The coordinator
first denies readiness/ownership and clears the private display's DOM-witness
references without evaluating the dead renderer; only then can cancellation run
capture cleanup. Any peer-tab renderer loss conservatively invalidates the
private capability because WebViews may share a renderer process.

Every installed tab WebViewClient acknowledges `onRenderProcessGone`, retires
that exact instance and creates a blank replacement with the same logical tab
ID and a new WebView/document identity. It does not reload the old URL. Late
page/title/dialog callbacks must match the exact current BrowserTab object and
WebView, so retaining a logical tab ID does not revive a dead instance. Duplicate
death notifications are idempotent. The visible HandlerContext pointer is
cleared before destroy; scoped operation targets are retired through the
existing BrowserGatewayRetirement registry.

Native SCREEN_OFF and focus listener registration moved into
`R5LifecycleSignals`. This helper owns registrations only; the existing
coordinator consent reducer, ownership state and transfer mutex remain the sole
authorities. Hardware renderer-crash, peer-death ordering and same-tab blank
recovery are required acceptance tests, not inferred from source inspection.

## Foreground reuse and cancellation ownership

Foreground PixelCopy uses the same ephemeral document marker, epoch sequence
and acquired-pixel comparator. A visual callback and `OnDraw` schedule the copy;
neither is accepted as producer proof. Both actual document samples must match
the current token with fully opaque alpha before the marker region is blacked
out. Private ImageReader continues to require the additional native-host pair.
The shared blank-frame detector independently rejects uniform or transparent
content outside proof regions. This policy still admits only native synthetic
fixtures; it does not claim real-page pixel redaction.

Timeouts can occur after a result is acquired but before its caller resumes.
Private capture therefore stores ownership of the taken Bitmap inside the
timeout body and recycles it in the enclosing `finally`. Foreground capture
also retains ownership across a `withContext` dispatcher handoff. Reader
callbacks, timeout cleanup, and a consumer that successfully takes the frame
must never each believe the other still owns it. These fixes follow the
official coroutine contract (retrieved 2026-11-19; site API version 1.11.0,
without changing the project's dependency versions):

- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout-or-null.html
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html

Normal marker removal cancels its request-local timer. A timer requesting
self-removal after 10 seconds is only a fallback for a surviving peer document
that cannot safely be called during renderer-death handling. Browser suspension
can delay timers; this is not claimed as a hardware deadline or lifecycle pass.
There are no persistent listeners, observers or global marker registries in the
document.

### Focused local execution, not the Android release gate

A standalone JVM compilation and JUnit run completed with **27 tests passed**
on 2026-11-19: bounded RGBA geometry (5), shared content checks (3), document
geometry (2), acquired-pixel freshness (4), OneShot ownership/cancellation (6),
and nonce epoch rotation (7). The OneShot suite includes a real timeout, a
cancelled waiting consumer and 200 simultaneous producer/STOP races. The
narrow native acquired-frame timeout and dispatcher-return edge still requires
instrumentation/device execution.

This focused run used cached Kotlin compiler 1.9.22, serialization 1.4.1 and
coroutines 1.6.4 with the Android 34 API stubs. It is not a substitute for the
project Gradle dependency graph, Android instrumentation, final-commit CI or
Samsung acceptance. Its temporary execution logs are
`/tmp/kelpie-capture-jvm-compile.log` and
`/tmp/kelpie-capture-jvm-results.log`. Installation remains blocked by the
separately recorded exact-artifact, signer, recovery and device gates.

## Canonical navigation effect correction

The shared NavigationHandler previously posted Back/Forward to Main, slept
200 ms, and reported the current snapshot as success. Reload could accept the
old document's `readyState=complete`, and its timeout path still returned
success. Navigate also bypassed its replacement-document requirement whenever
an isolated navigation read lease was present.

The handler now awaits Main dispatch with the captured BrowserOperationGuard.
It checks the original request, target and document immediately before the
native mutation, and the same original authority/renderer before and after
every fixed completion read. No queued Handler runnable or persistent window
marker remains. Native Back/Forward availability and the expected destination
history index are required; an unavailable destination is refused before a
native call. Reload and same-URL Navigate require both a changed native document
epoch and a changed fixed engine timeOrigin. The native epoch changes at
`onPageStarted`, so it cannot alone prove that the old complete DOM was replaced.
A
fragment-only Navigate can retain the same document only at the exact requested
URL. Completion requires two matching, nonloading, complete observations at
least 100 ms apart. An unsettled timeout returns UNCERTAIN with
`mayHaveCompleted=true`, never a success receipt.

R5 uses the actual BrowserTab document epoch and native WebView history. Legacy
isolated Connect contexts without a TabStore retain a fixed engine timeOrigin
fallback; a guarded R5 request without a registered native tab is refused.
Existing URL/title privacy sanitization remains in the output path.

The pure NavigationEffectProof suite compiled and passed **9 tests** locally:
old-complete reload (including a changed native epoch with the old engine
document), unavailable/no-op/wrong-direction history, same-URL history,
document/loading stability resets, redirect and fragment handling, malformed
native history, incomplete observations and a backwards clock. Logs:
`/tmp/kelpie-navigation-unit-compile.log` and
`/tmp/kelpie-navigation-unit-results.log`. Two additional Android instrumentation
fixtures use only a dedicated synthetic WebView and loopback MockWebServer to
exercise real history/reload and a pending same-URL load. They are **NOT_RUN**;
no device behavior or exact Gradle/CI pass follows from the focused JVM result.
