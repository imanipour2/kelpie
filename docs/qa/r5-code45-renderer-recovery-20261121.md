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


## Owner clarification — COMPLETE background browser control, whole-page vision and agent task execution (2026-11-21)

**Acceptance objective (owner directive, not implemented evidence):** Kelpie must enable an approved AI agent to **see, understand, navigate, operate, verify and report on the entire reachable web-page experience** while the Android browser Activity is in the background, **without bringing Kelpie forward unexpectedly**. Merely reading DOM, listing tabs, obtaining a viewport or showing one non-gray PNG is NOT sufficient. This clarification refines R5 beta acceptance and its boundary with the existing KAI-001 agent-intelligence workstream; it does not modify owner-frozen code45 connection/approval.

### Required behavioral contract

1. Full **tab and navigation control**: enumerate/open/select/close tabs; navigate links/URLs; back/forward, selected-tab identity, history and source-document epoch. Use the actual TabStore and same browser-owned WebView, not a surrogate headless browser.
2. Full **interaction and verified outcomes**: find UI controls using DOM/accessibility/visual geometry; click, select, focus, type, clear/edit text, scroll in all four directions and wait for async changes. Handle RTL, nested overflow, sticky/fixed UI, accordions and modals. Verify actual postconditions, not merely acceptance of a tool call.
3. **Fresh real screenshots** for explicitly permitted web pages from the same app-owned VirtualDisplay/ImageReader (not gray/stale, fabricated DOM composites or only synthetic fixture images). Associate every allowed frame with tab/document, lifecycle generation, viewport size, zoom, scroll x/y and source proof; protect password/OTP/payment/secure surfaces. A DOM snapshot alone misses graphics, placement, canvas, occlusion and images.
4. **Whole-page coverage and visual analysis**: coordinate DOM and accessibility snapshot, element geometry and a sequence of real permitted high-resolution visual tiles. Traverse vertical/horizontal/nested scroll regions, iframes where accessible and allowed, lazy/virtualized and paginated results, changing states, visual overlays and responsive layouts. Track which regions/items/states were *actually observed*, deduplicate overlaps and report evidence plus uncovered regions. A single viewport is not the whole page. For infinite/dynamic/inaccessible/protected content, report PARTIAL or UNKNOWN, not a false all-details/VERIFIED_EMPTY claim.
5. **Closed-loop agent execution**: OBSERVE → LOCATE → ACT → WAIT → VERIFY → SCAN MORE → REPORT. Bind each action receipt to real selected tab, document state, image/DOM evidence and postcondition, with bounded retries, stale-handle recovery and truthful unresolved/unknown reporting.
6. **Genuine HOME/background continuation**: after actual native HOME with owner-approved current principal, browser retains private hardware rendering and supports tabs, actions, screenshots and page scans without stealing focus from other apps. Returning foreground preserves original WebView/history/scroll (including RTL); repeated HOME and renderer lifecycle interruptions are handled truthfully.
7. **Existing owner/privacy boundaries unchanged**: no new pairing, auth strengthening, prompts, key rotations or port/tunnel changes. Preserve native STOP/revoke/lock and refusal of unauthorized cross-app capture and sensitive information; human handoff for OTP, CAPTCHA and payments where required. A protected site may remain partially unobservable—do not bypass guards to satisfy whole-page metrics.

### Engineering ownership split

**R5 runtime** ensures one real app-owned WebView on VirtualDisplay/Presentation/ImageReader, native lifecycle, image delivery, actionable tab/input/scroll operations. Code45 has an observed renderer readiness failure and source-confirmed fixture-only real-page pixel policy, both OPEN.

**Visual coverage coordinator** combines safe freshly captured images with DOM/AX coordinates to cover full reachable page, horizontal/vertical/nested scroll, virtualized/lazy-loaded results, and returns a coverage manifest with confidence/unknown. FullPage UNSUPPORTED cannot be quietly relabeled as complete.

**KAI-001 agent intelligence** plans and verifies autonomous tasks, requests additional region/state coverage and safely hands off sensitive steps. Source integrations in #69-#71 do not imply successful device E2E. Keep KAI code ownership distinct and pass only explicit versioned interface/receipts to R5, never a new owner-auth layer. KAI original: https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit

### Physical S24 acceptance matrix — all currently OPEN

| ID | Actual physical scenario and acceptance |
| --- | --- |
| BG-01 | After real HOME, existing paired agent lists/creates/selects/closes tabs while Kelpie stays in background; same TabStore |
| BG-02 | Permitted real page: navigate/history, read DOM/accessibility and see fresh non-gray **same-tab** pixels after HOME |
| BG-03 | Click/type/edit/clear/select/submit harmless form; independently verify actual page effect visually and structurally |
| BG-04 | Vertical, horizontal, RTL, nested scroll, sticky UI, accordion/modal and lazy results; no coordinate drift, each screenshot matches viewport |
| BG-05 | Analyze full reachable page via successive safe image tiles + DOM/AX evidence; coverage ledger, no unjustified omitted content or false empty claims |
| BG-06 | **25 real HOME/background action+vision lifecycle cycles**, distinct from 25 screenshot calls; no foreground steal |
| BG-07 | Foreground return and subsequent HOME retains browser WebView identity, tabs, history and RTL scroll, no stale frames |
| BG-08 | Existing STOP/revoke/keyguard and sensitive-page negative tests; no disclosure, cross-app capture or added pairing burden |
| BG-09 | Renderer interruption, network handover and reboot as supported by hardware; bounded truthful recovery, no silent success |

Per-scenario evidence: exact installed build and SHA, source tab/document/viewport/scroll, action before/after, permitted pixel/freshness proof, coverage areas, and blocked/unobservable reasons. The existing physical code40 25/25 after-HOME image calls are **positive component evidence only**, not end-to-end acceptance. Android OS may prevent indefinite execution or visual access to protected content; report these limits rather than asserting full coverage.

**STATUS:** OWNER_GOAL_CLARIFIED / WHOLE_BROWSER_AGENT_ACCEPTANCE_DEFINED / R5_RENDERER_AND_REAL_PAGE_PIXEL_OPEN / KAI_WHOLE_PAGE_DEVICE_E2E_OPEN / AUTH_FROZEN / NO_INSTALL / SYNC_BLOCKED_MAIN.
