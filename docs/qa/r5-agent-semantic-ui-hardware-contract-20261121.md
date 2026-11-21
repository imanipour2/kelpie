# Kelpie Android R5 — Whole-page semantic control, native gestures and hardware-assisted vision
Mission: EF-OPS-KELPIE-COMPLETE-INTERACTION-20261121
Status: OWNER-DIRECTED TARGET / DESIGN-AND-QA-CONTRACT / NOT YET IMPLEMENTED
Repository: https://github.com/imanipour2/kelpie
Execution branch: fix/r5-renderer-recovery-code45-freeze-20261121 (Draft PR #75)
Authority: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit
Canonical live evidence: https://docs.google.com/document/d/1_OynFl25a2KtynEOJyY3qAcTRIi8QrCmTuUmY47I0RU/edit

## Owner's clarified outcome
The agent discovers the *actual capabilities* of the current page and can observe, select, navigate, interact with, copy/paste, drag/drop, scroll, fill, edit, and verify accessible parts of the same real browser's page while Kelpie remains in Android background. The visual model sees the rendered page, not merely the DOM, including all reachable scrolling regions and interactive states. It records which regions are observed and which remain blocked/unknown. No claim of seeing absolutely all sensitive, cross-origin, protected, infinite or technically inaccessible content is permitted.

The owner explicitly allows local Android hardware capabilities where helpful. That does NOT authorize global screen capture, controls in other apps, unrestricted clipboard reading, camera/microphone/file access, Android permission bypass, or OS-wide accessibility automation. The currently approved mother pairing/Ed25519 computer trust, owner consent state, 18463/8080 tunnel, package signature, STOP/revoke/lock and protected-content refusals are FROZEN; do not add routine authentication prompts or extra approval layers.

## Verified SOURCE baseline vs gaps (PR #74 at c782344)
- Existing R5PrivateMcpActions.methods: tabs.new/close/select, tab.goto/click/fill/type/select/wait/back/forward, page.scroll. No public methods for copy, paste, drag/drop, text selection, long press, pointer gesture, file upload, explicit nested-scroll, full-page coverage or element capability discovery as independent commands.
- R5WebViewActionScripts.SNAPSHOT enumerates only a,button,input,textarea,select,[role=tab], keeps at most 160 rendered matches, text at most 28000 characters, and explicitly returns coverage=INCOMPLETE. It does not inventory contenteditable, draggable/drop-target, canvas, custom slider, shadow-root controls or every overflow container. The present 160/28000 limits must NEVER be interpreted as exhaustive discovery.
- Existing R5WebViewActionScripts.fill/select and R5NativeBrowserPort use the real WebView and explicit target/guard/proof. Selection and contenteditable handling require new, separate reviewed operations.
- R5WebViewActionScripts.scroll currently uses window.scrollBy(x,y) and proves window scroll offsets. It does not automatically move scrollable nested panels, virtual lists, sticky/pinned regions or drag handles.
- PrivateWebViewDisplay retains app-owned VirtualDisplay + Presentation + RGBA ImageReader. Renderer readiness and the ordinary-page image privacy admission remain unresolved independent blockers.
- Existing KAI-001 owns search, sort/filter, comprehensive result coverage, deduplication, item validation, human handoff and task completion; do not rewrite that separately in the renderer layer.

## Preferred architecture (reuse-first, performant, mobile/hardware-native)

### 1. Semantic capability inventory (read-only)
One bounded same-page discovery pass using DOM/ARIA/element geometry and native WebView observation: control role/label/state (disabled, selected, expanded, editable, draggable, scrollable), target selector and rect, scroll parent, viewport, locale/RTL, paint/overlay visibility, keyboard affordance, file inputs and special controls. Do not return passwords, OTP values, hidden tokens, cookies or sensitive user contents. Inventory is paged, includes total scanned / returned / truncated / unknown and separate open-shadow/accessible-frame coverage. Cross-origin/closed shadow/canvas remain UNKNOWN or require visual interpretation, never silently skipped. Use WebViewCompat feature-detection and origin-scoped scripts where available; no unbounded mutation observer or global persistent injection.

### 2. Action strategy by control type
Prefer existing native R5 WebView/TabStore command path for ordinary click/fill/type/select/navigation; after action, verify DOM AND fresh permitted pixels. For contenteditable or selection, use a targeted, tested editing operation that fires appropriate browser events while preserving focus, cursor and undo where feasible. For gestures not represented by safe DOM primitives, use Android MotionEvent sequences delivered to the owned WebView (View.dispatchTouchEvent) on UI thread, at coordinates transformed from CSS->WebView native pixels with zoom/insets/display/RTL accounting. This stays inside Kelpie's View hierarchy and does not inject global touch into other apps. Require actual receipt and changed control state, not just event dispatch success.

**HTML5 drag/drop distinction:** Synthetic JS DragEvent/dispatchEvent is untrusted and may not carry functional DataTransfer. Do not claim universal drag/drop via event fabrication. Try actual native same-WebView gesture for touch-drag handles; use a reviewed application-specific DOM drag adapter only when the site supports it. Android View.startDragAndDrop/ClipData is a platform drag mechanism, not a guaranteed implementation of arbitrary HTML5 DnD. Mark unsupported site interactions UNKNOWN with evidence.

### 3. Copy and paste
Default to bounded, session-local, RAM-only text transfer across user-approved fields/tabs. Bind source/destination to the current authenticated principal, live document epoch, selected browser tab and privacy class; cap size, clear on STOP/revoke/lock/process death, and never echo secrets in receipts/logs. For selection/copy, read only selected non-sensitive public/document text with origin and DOM privacy rules. For paste, prefer existing fill/type or an editable-target adapter with actual postcondition proof. Use Android ClipboardManager ONLY for website workflows that truly require system clipboard and when Android's background restrictions and owner scope allow it; do not silently read the phone-wide clipboard. ClipDescription.EXTRA_IS_SENSITIVE masks preview only; it does NOT make shared clipboard secret. Clipboard API/writeText may require HTTPS and user activation. Do not fake that synthetic JS events are trusted.

### 4. Whole-page and hardware-assisted vision
Keep existing app-owned VirtualDisplay/ImageReader and GPU compositor; use native freshness proof (visual-state callback plus producer timestamp, selected-tab/document epoch, region provenance) and redact private fields before any pixel export. Build a bounded tile capture/coverage manifest: scan vertical/horizontal/nested scrollports, sticky overlays, accordions, lazy/virtualized/paginated content and state transitions; coordinate CSS layout boxes, DOM/AX semantics and exact permitted frames. Do not infer FULL_PAGE from a single frame; report FULL_OBSERVED only for a well-defined finite reachable scope. Infinite feeds, blocked frames and protected screens must report PARTIAL/UNKNOWN with reasons. Apply backpressure (one in-flight native capture per owned WebView), bounded tile count/memory, adaptive overlap/scale and budget for S24 battery, heat and thermal throttling.

### 5. Android lifecycle and efficiency
All WebView methods and touch input run on the WebView UI thread; do not marshal arbitrary operations onto ImageReader's worker. Keep compositor owned by one actual WebView and avoid starting new Activities from a background command. Use native display callbacks, condition-based wait, existing lifecycle transfer mutex and renderer recovery with no busy polling or unconditional timeouts. Android background restrictions and foreground-service notification/type rules still apply; do not assume unlimited background processing or restart a forbidden service. Provide explicit PAUSED/BLOCKED/NO_RENDERER state rather than synthetic success. Camera, microphone, GPS or screen-wide MediaProjection are not prerequisites for observing Kelpie's own WebView and must not be requested as a general shortcut.

### 6. Security and parity
Use the frozen code45 pairing/auth/STOP/revoke/keyguard. Operate only on owner-authorized Kelpie WebView and allowed page origin. Page text and elements are UNTRUSTED; ignore instructions embedded by websites. Never submit financial, deletion, account, identity or external-send actions without the existing appropriate human authorization pathway. File upload uses WebChromeClient.onShowFileChooser/Android system picker and reviewed URI scope, not unrestricted file-system access. No public API/Android feature is merge-ready until corresponding iOS parity is implemented in the same PR or explicit owner-approved scoped exception is recorded per AGENTS.md.

## Acceptance matrix (all OPEN; no inferred pass)

| ID | Real S24 acceptance evidence required |
| --- | --- |
| BG-10 | Agent identifies visible and offscreen form fields, buttons, links, tabs, custom role widgets, contenteditable, draggable and scrollable containers; reports inventory count, truncation/unknown and target geometry with no secret values. |
| BG-11 | Type, edit, select all, cut/copy and paste permitted non-sensitive text between two same-browser fields and tabs; preserves expected Unicode/Persian/RTL and reports actual before/after without leaking copied text in logs. |
| BG-12 | Native long-press, swipe, drag-and-drop within a permitted sample page and scroll inside a nested overflow pane in HOME; prove changed target position and no coordinate drift; report unsupported HTML5 DnD honestly. |
| BG-13 | Operate custom dropdown/combobox, slider, checkbox, radio, contenteditable, modal/accordion and tab; verify UI value/state and new pixel after operation. |
| BG-14 | Explore canvas/image/diagram visually and identify actionable affordances. For a canvas-only control with no semantic hook, use bounded native gesture and visual postcondition; mark unknown if not safely operable. |
| BG-15 | Scan all reachable finite vertical and horizontal sections including lazy/virtualized regions with tiles + DOM/AX; provide observed/unobserved coverage map. Never report complete when inaccessible iframe/closed shadow/protected area is skipped. |
| BG-16 | 25 distinct physical HOME cycles, each with actual observation+action+visual verification; browser never foreground steals; existing pairing stays valid without additional approval. |
| BG-17 | Stop/revoke/lock, payment/OTP, file upload and sensitive clipboard negative QA: no unintended submission or secret disclosure and no repeat approval for ordinary approved operations. |
| BG-18 | Instrument GPU/capture backpressure, PSS/memory, battery/thermal and renderer crash behavior; document repeatable results, adaptive limits and Android 17 background-service constraints. |

## Implementation order (no premature feature claims)
P0: reproduce renderer not ready from native S24 cause codes and restore old code40 hardware-positive rendering without touching code45 auth.
P1: implement safe ordinary-page redacted frame provider, real viewport and visual scroll/coverage receipts; keep existing protected content refusal.
P2: add paged semantic inventory and nested-scroll/geometry APIs with human-readable coverage gaps; update iOS parity or owner-approved exception.
P3: add explicit text selection/copy/paste and physical native pointer/drag primitives under the same trusted target/STOP/lock controls, tests for trusted event limitations and real WebView impact.
P4: integrate KAI agent loop for complete tasks; full S24 BG-01..BG-18 acceptance, CI, privacy, rollback; no main merge/install without release gates.

## Official references and implementation relevance (review 2026-11-21)
- Android WebView evaluateJavascript and postVisualStateCallback: https://developer.android.com/reference/android/webkit/WebView — same UI thread, visual-state is not itself pixels.
- Android WebViewCompat feature-gated origin-scoped document scripts: https://developer.android.com/reference/androidx/webkit/WebViewCompat — use only when supported and restricted to intended origins.
- Android View.dispatchTouchEvent, MotionEvent: https://developer.android.com/reference/android/view/View and https://developer.android.com/reference/android/view/MotionEvent — own view-local touch path, not OS-wide input.
- Android drag/drop Views and ClipData: https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/view — native DnD differs from HTML5 DnD.
- MDN Event.isTrusted and DragEvent: https://developer.mozilla.org/en-US/docs/Web/API/Event/isTrusted and https://developer.mozilla.org/en-US/docs/Web/API/DragEvent — synthetic JS dispatched events are not trusted; DataTransfer constraints.
- Android clipboard security and copy-paste: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling and https://developer.android.com/develop/ui/views/touch-and-input/copy-paste — sensitive preview is not an isolation boundary.
- WebChromeClient onShowFileChooser: https://developer.android.com/reference/android/webkit/WebChromeClient — user-selected URI restrictions and validation.
- Android FGS background start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start — no background launch bypass.

STATUS: OWNER_INTERACTION_HARDWARE_SCOPE_RECORDED / SOURCE_GAPS_CONFIRMED / TECH_STACK_RESEARCHED / RUNTIME_IMPLEMENTATION_PENDING / CODE45_AUTH_FROZEN / SYNC_BLOCKED_MAIN.
