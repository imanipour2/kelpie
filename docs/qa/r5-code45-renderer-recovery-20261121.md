# Kelpie S24 R5 — Renderer recovery integration lane (owner-frozen connection)

**Mission:** EF-OPS-KELPIE-R5-RENDER-RECOVERY-20261121  
**State:** REVIEW-GATED / DIAGNOSTIC-FIRST / NO APK INSTALL / NO MERGE  
**Base commit:** `c7823444953db4e64999d45d4a95e53c17fc6d11` (installed private v0.2.25/code45; [PR #74](https://github.com/imanipour2/kelpie/pull/74))  
**Working branch:** `fix/r5-renderer-recovery-code45-freeze-20261121`; PR targets PR74's head branch, **not main**.  
**Owner decision:** [CDG, EF-OPS-KELPIE-CONNECTION-BASELINE-FREEZE-20261121](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit). Execution evidence: [KELPIE-CONNECT-LIVE-001](https://docs.google.com/document/d/1_OynFl25a2KtynEOJyY3qAcTRIi8QrCmTuUmY47I0RU/edit); tracking: [Enerfoam issue #100](https://github.com/imanipour2/enerfoam-website/issues/100). This document is a source/QA mirror, **not a new owner/CDG decision**.

## Non-negotiable connection freeze

Retain the currently installed package `com.kelpie.browser.r5mothernextverify`, version `0.2.25-r5-connection` code45, same owner signature and mother MCP protocol, persistent Ed25519 computer trust, existing native owner decision, STOP, revoke, keyguard and data-delivery checks, port 18463, and existing Termux/tunnel admin port 8080. **Do not introduce any new authentication/consent restrictions, extra pairing prompts, token rotations, tunnel/plugin modification, fallback to unauthenticated localhost, or foreground Activity starts.** The owner's freeze does not assert that background rendering or ordinary-page screenshots are accepted; do not weaken sensitive-data protections to achieve an image.

Any truly necessary auth change must be separately reported for explicit owner approval before code edits. CI guard `qa-r5-connection-freeze.yml` compares the working HEAD against the exact installed baseline and fails if the core authentication/trust/config files change. It does not substitute for security review.

## What actually changed (not just transient connection)

The Android WebView + TabStore + private `DisplayManager.createVirtualDisplay` + `Presentation` + RGBA ImageReader compositor remains implemented at code45. Remote NAVIGATE and history still use WebView loadUrl/goBack/goForward; CLICK, SELECT, FILL, TYPE and horizontal/vertical SCROLL still use `evaluateJavascript`; tab commands still use TabStore and the single native WebView instance. The **admission conditions, physical tab ownership, lifecycle transitions, screenshot proof and delivery policies changed materially**.

Recorded physical code40/PR66 `68ade958af66b7f6de219abd2d9f9011478f5a40` on S24 Nov 18: local authenticated mother MCP after HOME returned 25/25 distinct, nonuniform 384x683 PNGs and background navigation changed DOM/title/image. Those were 25 **image calls**, not 25 full HOME or reboot cycles, nor remote Termux sidecar E2E.

### Source-verified independent blockers

1. **Ordinary-page images blocked in source**: `WebViewCapturePrivacy.kt` introduced on the #70→#71 integration; `inspect` requires DEBUG with the private test-registered `https://kai-fixture.invalid/` fixture. Ordinary live-page pixels are not exportable through mother, R5 or legacy screenshot routes even if compositor is healthy. Call sites: `HandlerContextCapture.kt`, `R5MotherMcpCaptureAdapter.kt`, `R5NativeBrowserPort.kt`, `PrivateWebViewDisplay.kt`. Never bypass this with unredacted raw ImageReader output. Provide a separate reviewed owner-approved redaction, scene/provenance, protected-window and fresh-frame provider, or keep refusal.
2. **Likely, not yet proved on this S24:** `R5AgentCoordinator.onPause` sets `ready=false` before transfer; `PrivateWebViewDisplay.start` checks native geometry, CSS callback, private host/window/surface, `VisualStateCallback`. Failure invokes `suspendOnRendererFailure` and `AndroidAgentProcessSession.rendererUnavailable`, invalidating runtime generation but retaining Ed25519 computer trust. Mother profile defers activation to genuine lifecycle events; on subsequent successful authenticated metadata requests while HOME, browser work can remain `RENDERER_NOT_READY` without a new qualifying native event. Determine the **first real failed gate** on hardware before a fix.
3. **Target and action admission altered:** `R5NativeBrowserPort.nativeTargetForBinding` now requires positive native WebView width/height; click/fill/type/select/scroll also check CSS viewport and bound target. A dormant or partly restored WebView can therefore block all actions without loss of tunnel or principal.
4. **Potential distinct renderer death**: `TabWebViewClients.onRenderProcessGone` and `TabStore.retireDeadRenderer` destroy/replace a dead WebView; do not infer a crash without an actual event in logcat.

## Single physical diagnostic, without laptop

Do **not** assume a connector metadata result proves browser readiness. The last connected client probe returned code45 device-info PASS but `tabs.list = UNAVAILABLE/RENDERER_NOT_READY` with computer approval unchanged.

**Preferred collection:** Existing native Android `Log.i` tags already in code45 (no new APK) are `KelpieR5Lifecycle`, `KelpieR5Transfer`, `KelpieR5Host`, `KelpieR5Viewport`, `KelpieR5Restore`, `KelpieR5Recovery`, plus renderer-process death reason. If on-device debug/log access can be performed with explicit owner action, collect a *bounded* diagnostic snapshot immediately before/after one real HOME lifecycle transition, then manually inspect locally and share only the structurally redacted gate/state lines. ADB wireless pairing via Termux or a phone-generated bugreport requires native Android user permission; **do not attempt hidden grants, remote taps, unsolicited Activity launch or pairing reset**. Device access/logcat availability was not proven in this source-only action.

Record: event order/elapsed timestamps, pause/resume epoch, readiness, owner state (name only, not secret generation), WebView attachment/visibility/size, CSS callback outcome, virtual display/window/surface validity, visual request status, renderer-death signal, `private_start`, `pause_commit_background`, `PRIVATE_RENDERER_TRANSFER_FAILED`, `RESTORE_VERIFY_FAILED`. Avoid URLs, tab titles, page text, images, OTP, tokens, exact Ed25519 keys, process environment, raw unfiltered logcat and IPs.

**Decision matrix:**

| First observed failure | Correct repair boundary |
| --- | --- |
| `WEBVIEW_CSS_NOT_READY` or `CSS_CALLBACK_TIMEOUT` | WebView lifecycle/attachment and visual scheduling before detach; no auth changes |
| `COMPOSE_RELEASE_TIMEOUT` | One true Compose host detach and event sequencing; no forced Activity foreground |
| `PRIVATE_HOST_NOT_ATTACHED` / `PRIVATE_HOST_WINDOW_NOT_DRAWABLE` | Presentation host attachment/visibility; preserve owned WebView |
| `VISUAL_STATE_CALLBACK_TIMEOUT` | Android WebView callback draw prerequisites and private-host geometry, not a blanket timeout boost |
| `CSS_STABILITY_FAILED` / `RESTORE_VERIFY_FAILED` | Physical reattachment and scroll restore; recover within approved generation/valid native event |
| `RENDERER_GONE` | Retire dead renderer and replace WebView using existing cleanup path |
| `PRIVATE_DISPLAY_COMMITTED` but `pause_commit_background ready=false` | Reconcile `WebViewOwnershipState`, target identity and lifecycle epoch |
| Screenshot refused `FRAME_PRIVACY_UNVERIFIED` or `UNSUPPORTED` | Safe real-page image provider, not virtual-display transport/pairing |
| `FRESH_DOCUMENT_AND_NATIVE_NONCE_MATCHED` | Compositor image proof healthy; inspect privacy/delivery separately |

## Controlled remediation plan (same PR chain)

**Gate A — Preserve baseline:** Freeze approval/STOP/trust and source diffs, assert no change to owner authentication files or build package/port. A new runtime guard must never ask for a second owner approval.

**Gate B — Fix first observed physical defect:** Use bounded event-driven reconciliation, existing native pause/resume/lock/unlock signal, transfer mutex and ownership generation. Avoid retry storms and unbounded callbacks; never activate another app or export stale/gray frame. New source behavior needs an exact reproduction test from a failed transfer while HOME to healthy private host with same trusted principal; lock/STOP/revoke must immediately fence it. Do **not** blindly force `ready=true` or remove native/host checks.

**Gate C — Restore permitted screenshot functionality:** Keep fixture tests, protected screen refusal and privacy/secret controls. Define an independently verified native public-page pixel provider before lifting fixture-only gate. It must refuse protected/password/OTP/payment/mixed private documents when safe redaction cannot be proved, and verify page/document epoch + compositor nonce + producer timestamp + crop/scale receipt. A separate review is needed before enabling actual page pixel export; it is **not** an authentication change.

**Gate D — S24 Android17 matrix:** On the real installed candidate, 25 genuine HOME → nonforeground commands → foreground return cycles (NOT merely 25 calls), tabs.list/new/select/close, tab.goto/back/forward, DOM/visible elements, click, type, horizontal/vertical and RTL scroll, safe page-image freshness, original WebView identity and history, STOP/revoke/keyguard negative tests, reboot and Wi-Fi/network transitions. No unwanted foreground steal. Include 2026-11-18 code40 25/25 screenshot behavior as positive comparator, with clear scope.

**Gate E — CI and delivery:** Exact HEAD Android/unit/instrumentation, contract, lint, 500-line guard, parity or explicitly approved scoped exception, review, owner approval and staged APK only after gates. Existing `R5AgentCoordinator.kt` is ~590 lines at base and must be split by responsibilities *when modifying it*, not masked by whitespace changes. Main is not the candidate base; PR74 itself remains DRAFT and unmerged.

## Traceability/readback

- [Code40 device RCA](https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit)
- [Kelpie Live Log (single operational master)](https://docs.google.com/document/d/1_OynFl25a2KtynEOJyY3qAcTRIi8QrCmTuUmY47I0RU/edit)
- [Owner CDG freeze](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit)
- [Website issue #98: transport incident](https://github.com/imanipour2/enerfoam-website/issues/98); [#100: one renderer/beta tracker](https://github.com/imanipour2/enerfoam-website/issues/100)
- Official Android references: [WebView VisualStateCallback](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback)), [VirtualDisplay](https://developer.android.com/reference/android/hardware/display/DisplayManager), [renderer process death](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview), [offscreenPreRaster](https://developer.android.com/reference/android/webkit/WebSettings#setOffscreenPreRaster(boolean)).

**Current status:** `AUTH_BASELINE_FROZEN / DEVICE_LOG_PENDING / CODE_REVIEW_BRANCH / NO_INSTALLED_PATCH / SYNC_BLOCKED_MAIN / NO_PUBLIC_RELEASE`.
