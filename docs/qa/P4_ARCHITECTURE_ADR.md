# P4 architecture ADR — draft for CDG review

**Decision scope:** isolated Android QA-only P4 hardening. **Status: PROPOSED / NOT RELEASE APPROVED.**

## Invariants
1. Preserve the original Java WebView; transfer its view ownership Compose → app-owned VirtualDisplay/Presentation → Compose; never recreate or reload to claim successful restoration.
2. Only locally approved, authenticated, bounded client/request/attempt/tab/document/WebView and exact `example.com` origin may claim a private frame.
3. Screen-off, noninteractive state, keyguard, STOP, expiry or tab/document drift must revoke; no locked-task capabilities.
4. Frame proof uses a deterministic QA pixel nonce, not cross-clock comparison of Image.timestamp and elapsedRealtime.
5. No unattended network action may extend 90-second/20-frame grant.

## Logical components
- **Capture Authorization:** `ConsentedCaptureRequestRegistry` + `ConsentedCaptureLease` (identity and monotonic allowance; no rendering).
- **Capture Session:** `PrivateCaptureCoordinator` (temporary sole coordinator/owner; refactor into a smaller explicit state manager after fault-injection).
- **Private Renderer:** `PrivateWebViewDisplay` (original WebView, Surface, ImageReader, nonce and CSS scroll restoration).
- **Operation Admission:** `PrivateCaptureOperationPolicy` invoked at Router, BrowserGatewayBinding and ConnectBrowser, only when QA coordinator owns a private display.
- **Lifecycle & Recovery:** coordinator, notification, receiver, expiry coroutine and renderer cleanup. Pending native recovery is not a successful release.

## State sketch
`IDLE → PENDING_LOCAL_APPROVAL → RELEASING_COMPOSE → PRIVATE_DISPLAY → RESTORE_PENDING → IDLE`.
Exception path: `RELEASING_COMPOSE/PRIVATE_DISPLAY/RESTORE_PENDING → FAILED`; recovery must return original WebView to Compose, revoke authority and free native resources; physical recovery not verified. Do not add cosmetic states merely to claim coverage.

## Rationale
Reject a new WebView (cookies/history/form identity lost), changing to GeckoView (engine migration risk), MediaProjection/Accessibility of other apps (out of scope), ADB-overlay-based rendering and LOCKED_TASK. Retain direct ownership and deterministic QA proof.

## Governance conflicts (must resolve before merge)
- `AGENTS.md` mandates full release/publish cycle on every pushed fix; this owner mission explicitly prohibits publishing. Security-first decision **for this Draft only**: retain unmerged, unreleased QA source, request scoped formal exception in CDG before any merge.
- `AGENTS.md` demands same-PR iOS parity. Android VirtualDisplay has no direct iOS equivalent; require portable policy/UX parity assessment, not pseudo-identical implementation. Formal CDG exception or matching Apple policy tests before product promotion.
- Approval/recording in canonical Drive is distinct from a GitHub Draft PR; `SYNC_BLOCKED` until owner approves the scoped ADR and records it.

References: Android VirtualDisplay https://developer.android.com/reference/android/hardware/display/DisplayManager ; ImageReader https://developer.android.com/reference/android/media/ImageReader ; Compose AndroidView https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose ; screen-off https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_OFF.


## External architecture comparison (source-pinned research; no code copied)
| Project / inspected revision | Applicable principle | Architectural difference / decision |
|---|---|---|
| [So7o Android WebView CDP](https://github.com/jan5o7o/android-webview-cdp/blob/3c5745973aab58663aa132834ed228abc75699bd/README.md) `3c57459` | A real, ON Surface is necessary for fresh compositor pixels; JS/CDP may remain available without screenshots. Validate surface dimensions, visibility and pixel content independently | Its off-screen demonstration writes `overlay_display_devices` via ADB shell `WRITE_SECURE_SETTINGS` and exposes CDP. **Do not import** privileged display creation or unprotected CDP into Kelpie |
| [Mozilla GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html) (online architecture page, no source SHA pinned) | Separate runtime/session/display ownership; adopt separation of concerns only | Kelpie remains Android WebView; no Gecko migration in this mission |
| [KDE Connect PairingHandler](https://github.com/KDE/kdeconnect-android/blob/d0534acc70cd9403fb56d78f645f228e21097492/src/main/java/org/kde/kdeconnect/PairingHandler.kt) `d0534ac` | Separate states for pairing, explicit approval, timeout and unpair | Persistent device trust is **not** a private-capture grant; Kelpie retains a new 90s local approval per session |
| [Syncthing-Fork](https://github.com/Catfriend1/syncthing-android) (revision not pinned; source-level comparison pending) | Investigate battery policy and service states for predictable recovery | File-sync background service cannot prove hidden WebView pixel rendering; no integration adopted |
| [Home Assistant Android ForegroundServiceLauncher](https://github.com/home-assistant/android/blob/6f48337910158c5ca1a3965e499143114bd6a3d3/app/src/main/kotlin/io/homeassistant/companion/android/util/ForegroundServiceLauncher.kt) `6f48337` | Explicit synchronized starting/running/restarting/stopping state and a delayed stop request during start | Its foreground-service lifecycle is not WebView ownership; adapt state testing only, do not copy FGS architecture wholesale |

This is a comparison of specific inspected files and documented ideas, **not** proof of Kelpie implementation or successful device tests. For Syncthing-Fork and GeckoView code-level SHA attribution remains open before claiming a complete source audit.
