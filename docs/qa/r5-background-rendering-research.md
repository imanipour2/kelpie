# Android background WebView rendering: lifecycle and evidence

Sources read: **2026-11-20**. This note records public platform contracts and the
reasoning for a focused lifecycle repair. It does not establish hardware test
results or identify the Chromium revision in any installed WebView.

## Architecture choice

| Approach | Benefit | Cost and limit |
| --- | --- | --- |
| Activity-owned WebView | Closest to the normal Android View lifecycle; direct native input, focus, and accessibility. | Hiding the Activity does not provide an independently visible rendering window. Background JavaScript availability does not prove that fresh pixels are being produced. |
| Same WebView transferred to a private Presentation | Preserves the live document while giving it a separate display/window and an app-owned output Surface. Foreground interaction returns to the existing View. | Detach, attach, cancellation, restoration, and asynchronous completion must share one coherent ownership protocol. |
| Permanently private host with SurfaceTexture output | Could avoid repeated reparenting if foreground presentation also consumes the private output. | Requires a live GL consumer and additional input, focus, IME, accessibility, and lifecycle integration. SurfaceTexture does not make a hidden producer render. |

The selected repair retains the existing **same-WebView, private Presentation,
VirtualDisplay, and ImageReader** architecture. The demonstrated lifecycle race
does not justify replacing the frame transport. A permanent private host remains
a separate architectural option, requiring its own interaction and lifecycle
validation rather than being treated as a smaller fix.

## What the platform guarantees

`WebView.postVisualStateCallback` reports that the current DOM state is ready for
the next draw. That draw includes changes submitted before the callback request,
and may include later changes. It does not identify a particular acquired image
or promise a completion deadline. The documented conditions require attachment
and appropriate visibility; a `GONE` View also needs fixed dimensions. The
covered content excludes video. [1]

Current Chromium forwards visual-state requests through `WidgetCompositor`.
Its `CreateQueueSwapPromise` queues a swap promise and requests a main frame via
`SetNeedsAnimateIfNotInsideMainFrame()`. Consequently, a missing additional
application invalidation after registering the callback is not, by itself,
evidence of a scheduling defect. This implementation observation must not be
substituted for the public API contract or assumed to describe every provider
version. [2]

Chromium's `BrowserViewRenderer` separately tracks pause, attachment, and window
visibility. After a WebView has been attached, its client visibility requires an
attached, visible window and an unpaused renderer. This state controls its
begin-frame source. Native dimensions and a responsive JavaScript realm alone
therefore do not establish rendering liveness. View visibility, window visibility,
and Activity lifecycle are distinct observations. [3]

`Presentation` is a display-specific Dialog. Current AOSP creates a display
WindowContext and chooses the private presentation window type for a private
display. Display removal cancels the Presentation. Neither this implementation
nor the public contract establishes a blanket rule that the originating
Activity's `onStop` cancels every private Presentation. [4]

Absence of an explicit hardware-acceleration window flag does not prove software
rendering: application configuration can apply it automatically, including in
`WindowManagerGlobal.addView`. Observe the actual attached View/window state;
do not infer a defect from a missing flag alone. [5]

VirtualDisplay lifecycle callbacks provide stronger output signals:
`onPaused` means its Surface receives no buffers; `onResumed` reports resumption;
`onStopped` is terminal, and the application must still release the display. [6]
ImageReader consumers must close acquired images; `acquireLatestImage` requires
sufficient free acquisition slots to discard older images. SurfaceTexture instead
requires `updateTexImage` on a thread with its owning GL context current. Neither
consumer can independently guarantee producer progress. [7]

`offscreenPreRaster` applies to offscreen Views that remain attached. Android
documents extra memory use and recommends limited instances with dimensions no
larger than the screen. It is not a liveness or fresh-image guarantee. [8]

## Focused repair and retained evidence

The failure mode is an ordering error: a foreground-resume handler can observe a
transfer still in progress and return; the transfer later completes or fails,
leaving a state that still requires restoration. Handling only a successfully
committed private host misses the failed-transfer case.

The repair reconciles against the **latest lifecycle intent after serialized
transfer work**, including both successful and failed late completion. State
selection must occur after acquiring the transfer serialization boundary.
Every suspension requires renewed lifecycle and ownership checks before publishing
readiness. An old resume must not overwrite a newer pause, and old cleanup must
not dispose a newer host. This is a state-machine correction, not a longer timeout.

A physical-host lease is separate from the capture generation: resume immediately
retires frame evidence while the admitted transfer can settle. Physical teardown
retires the host lease. Verified `IDLE` restoration also retires interrupted
transfer tickets, preserving the consent generation and explicit stopped/locked
ownership states.

The relevant responsibilities remain separated:

- [R5AgentCoordinator](../../core/android/src/main/java/com/kelpie/browser/agent/execution/R5AgentCoordinator.kt)
  coordinates ownership through the serialized
  [R5LifecycleReconciler](../../core/android/src/main/java/com/kelpie/browser/agent/execution/R5LifecycleReconciler.kt).
- [PrivateWebViewDisplay](../../core/android/src/main/java/com/kelpie/browser/capture/PrivateWebViewDisplay.kt)
  owns physical transfer, display resources, and capture delivery.
- [R5PrivateViewportReadiness](../../core/android/src/main/java/com/kelpie/browser/capture/R5PrivateViewportReadiness.kt)
  observes geometry and visual-state completion.
- [R5PrivateFrameAcquisition](../../core/android/src/main/java/com/kelpie/browser/capture/R5PrivateFrameAcquisition.kt)
  validates acquired output; existing native frame witnesses remain required.

Host readiness and screenshot freshness are different claims. Successful
`show()`, nonzero layout, or a visual-state callback must not replace the existing
per-capture evidence tying an acquired frame to the current operation and owner.
Runtime unavailability must also remain distinct from durable computer approval.

Diagnostics must distinguish callback timeout from ownership cancellation,
request mismatch, lock, and changed geometry. A callback can arrive successfully
after its owner has become stale. Combining that rejection with timeout obscures
the cause. Window visibility, attachment, hardware state, and display state are
useful categorical observations, not new authorization requirements.

## Validation invariants

- Exactly one View parent and current host owner; bounded display, reader, image,
  and worker resources; idempotent cleanup after partial construction.
- Resume during geometry or visual-state waiting converges to foreground
  restoration without another resume event. The following pause can transfer
  again. Cover late success and late failure.
- Rapid pause/resume/pause cannot publish an obsolete host or overwrite current
  visibility. Test multiple lifecycle changes while one transfer is suspended.
- Lock, STOP, tab changes, and document changes invalidate affected pending
  results before asynchronous work completes. No retired frame is delivered.
- Repeated cycles return resources to baseline. Synthetic document changes and
  native frame witnesses establish freshness independently of host status.
- Include multi-window or translucent-Activity transitions: `onPause` is not
  equivalent to complete invisibility. [9]

Renderer process death is a separate case: Android requires removal/destruction
of the affected WebView and creation of a new instance. Retrying the dead object
is not recovery. [10] Foreground service process importance likewise does not
guarantee drawing. Android 17's documented all-app memory limits reinforce bounded
resource use; the reviewed documentation does not establish a blanket prohibition
on private VirtualDisplay rendering. Target-SDK-specific changes must be evaluated
against the application's actual target. [11]

## Physical feedback: Android handler contracts

The first installed candidate restored authenticated MCP discovery, tab access,
DOM reads and observed actions. A loopback-only RTL fixture also accepted a
background reload and button action; an independent snapshot confirmed its
counter changed from zero to one. Returning to the Activity preserved that
document and produced the verified CSS-restoration result.

The same physical acceptance run exposed two separate handler defects. Neither
was a computer-approval failure:

- `HandlerContext.evaluateJSReturningJSON` embeds its input in
  `JSON.stringify((expression))`. The scroll dispatcher supplied two statements
  separated by a semicolon, making the resulting JavaScript invalid before
  `scrollBy` could run. The dispatcher and top/bottom variants now return one
  immediately invoked function expression. The existing independent measured
  effect check and conservative uncertain-action result remain intact.
- The viewport and device-info handlers returned metadata without the native
  gateway's required `success: true` envelope. Both now use the existing
  `successResponse` helper. Existing fields and units are unchanged; CSS geometry
  still comes from the page snapshot, not native WebView pixel dimensions.

Regression coverage must execute the real handlers through the Android WebView
JSON wrapper and the native MCP gateway. Evaluating the scroll text as a whole
JavaScript program would miss the production expression-versus-statements bug.
These corrections do not relax native owner approval, STOP, lock handling,
document identity, privacy admission or acquired-frame freshness.

## Physical feedback: mobile RTL window-scroll origin

A wide RTL fixture on the actual Android WebView exposed a second geometry defect.
Its root client width was 384 CSS pixels and content width was 2184, while
`window.innerWidth` was 1536. Independent requests for the native horizontal
extremes measured approximately -1799.82 and -1151.64; scrolling left by 180
changed the measured position correctly, but the old zero-origin range rejected
that effect. This was a production measurement error, not a failed computer
approval or merely a fixture timeout.

Pinned Chromium source reads and changes window scroll through `LayoutViewport`.
Its minimum offset is negative scroll origin; maximum adds content size minus
visible size. The root `clientWidth` and window `innerWidth` are obtained from
different native sizes. [12] For the Android WebView host with overlay scrollbars,
the measured example therefore has horizontal origin `384 - 1536 = -1152` and
travel `2184 - 1536 = 648`, giving the RTL range `[-1800, -1152]` within the
existing subpixel tolerance. A visual-viewport width is not a substitute for the
window-scroll range. This Android-specific model must not silently classify a
classic desktop scrollbar gutter as a mobile origin shift.

The correction records the horizontal origin explicitly and requires it to
remain stable with the other geometry during effect verification. Android
instrumentation establishes its expected endpoints through independent standard
scroll requests before exercising the real handler; it does not copy the
production formula or require a false zero origin. Relative movement, actual
clamping, boundary refusal and metadata-envelope assertions remain required.
The fixture uses production viewport settings and enough vertical content for
the expanded layout viewport; its complete setup stays bounded to five seconds.

## Primary sources

1. [WebView visual-state API](https://developer.android.com/reference/android/webkit/WebView.html#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback)).
2. [Chromium WidgetCompositor](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/platform/widget/compositing/widget_compositor.cc), current `main` read on the date above.
3. [Chromium BrowserViewRenderer](https://chromium.googlesource.com/chromium/src/+/ad101d679f2ce6349335e1608564e1149ecb12ff/android_webview/browser/gfx/browser_view_renderer.cc), pinned commit `ad101d679f2ce6349335e1608564e1149ecb12ff`; visibility behavior also checked against current `main`.
4. [Presentation API](https://developer.android.com/reference/android/app/Presentation) and [AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/Presentation.java), observed blob `bdab39dcd2acd12510740a4122d53e81cc551d0c`.
5. [Hardware acceleration](https://developer.android.com/develop/ui/views/graphics/hardware-accel) and [AOSP WindowManagerGlobal](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/WindowManagerGlobal.java).
6. [VirtualDisplay.Callback](https://developer.android.com/reference/android/hardware/display/VirtualDisplay.Callback) and [DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager).
7. [ImageReader](https://developer.android.com/reference/android/media/ImageReader) and [SurfaceTexture](https://developer.android.com/reference/android/graphics/SurfaceTexture).
8. [WebSettings.setOffscreenPreRaster](https://developer.android.com/reference/android/webkit/WebSettings#setOffscreenPreRaster(boolean)).
9. [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle).
10. [Manage WebView objects](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview).
11. [Process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle), [Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all), and [Android 17 target-specific changes](https://developer.android.com/about/versions/17/behavior-changes-17).
12. Chromium at commit `fcc56177d69e3b0ac50f90c85a38f8a0cab99f92`: [LocalDOMWindow](https://github.com/chromium/chromium/blob/fcc56177d69e3b0ac50f90c85a38f8a0cab99f92/third_party/blink/renderer/core/frame/local_dom_window.cc), [Element client geometry](https://github.com/chromium/chromium/blob/fcc56177d69e3b0ac50f90c85a38f8a0cab99f92/third_party/blink/renderer/core/dom/element.cc), and [PaintLayerScrollableArea bounds](https://github.com/chromium/chromium/blob/fcc56177d69e3b0ac50f90c85a38f8a0cab99f92/third_party/blink/renderer/core/paint/paint_layer_scrollable_area.cc).
