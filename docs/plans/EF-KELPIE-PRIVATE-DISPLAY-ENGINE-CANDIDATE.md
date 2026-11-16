# Private WebView display engine candidate (Android only) — DRAFT

**2026-11-15 | Source-only | Not enabled in main browser or in a QA package yet**

This stacked branch builds on [consent policy PR #7](https://github.com/imanipour2/kelpie/pull/7) and [authenticated request inbox PR #9](https://github.com/imanipour2/kelpie/pull/9). It adds a separate native Android class `PrivateWebViewDisplay`, using P3's proven concept: **the same app-owned WebView** is temporarily moved from Compose into an app-private `VirtualDisplay + Presentation`, and `ImageReader` produces pixels while the browser Activity is hidden.

The class deliberately has **no network endpoint, no remote invocation, no local user-consent authority and no manifest privilege**. It can only be called by a future coordinator that verifies the real authenticated HTTP/MCP principal and already approved session on every frame. It must not be treated as having permission to read real pages until those controls are finished and independently tested.

## Implementation states

- `IDLE` — no surfaces or capture authority.
- `RELEASING_COMPOSE` — a UI-owned callback must release the real `TabWebViewContent` composition; the engine awaits `WebView.isAttachedToWindow=false` with a 2-second deadline.
- `PRIVATE_DISPLAY` — `VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY` plus `PRESENTATION`, no MediaProjection/accessibility/overlay, no other-app content. Only when Activity hidden and device unlocked may it attempt a new frame.
- `RESTORE_PENDING` — immediately drop all pending frames, call `ImageReader.close`, dismiss Presentation, release virtual display, quit worker thread and invoke the Compose restoration callback. Once native UI is visible, reattach the same original WebView and verify the initial scroll coordinates.
- `FAILED` — no frame is served, cleanup and rollback must be reviewed.

`captureFreshPng()` returns only transient in-memory Base64 image/metadata from a newly acquired ImageReader image; no disk logging, cookies, DOM, screenshot persistence, remote access, or screenshot cache fallback. Frames are bounded by max 1080×1920 and timeout 1800ms; overlapping frame requests denied. The caller must still enforce the lease token/identity/client/attempt and independently verify delivery freshness.

## Unverified issues, required follow-up

- Test *real* non-sensitive pages in a **separate QA app package** with explicit, per-session local user consent before connecting this engine to the normal browser. P3's synthetic 20/20+restore PASS is evidence of feasibility, not proof of this new implementation.
- Ensure `Image.timestamp` and request freshness are validated across Samsung and 16K Android emulator; no stale frame must be labeled as fresh.
- Defend against races: STOP during ImageReader callback/encoding, app/OS termination, keyboard/rotation/background changes, WebView/tab destruction, display teardown and session TTL. Lifecycle cleanup must not be allowed to destroy the original WebView.
- Verify user privacy: sensitive fields/secure content, read-only screenshots, no screenshot persistence or transport leakage, visible localized one-time approval and STOP. No protected/cross-app capture.
- Use Android16/17 targeting and release signing only from independently reviewed PR #8. Apple/iOS renderer parity and privacy review remain separate gates.

**Status:** `ANDROID_RENDERER_SOURCE_CANDIDATE / DEVICE_UNTESTED / UI_NOT_WIRED / CLIENT_AUTH_NOT_WIRED / RELEASE_NO_GO`.
