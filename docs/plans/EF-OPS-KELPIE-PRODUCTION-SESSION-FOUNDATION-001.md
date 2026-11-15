# EF-OPS-KELPIE-PRODUCTION-SESSION-FOUNDATION-001 — secure capture authorization contract

**2026-11-15 | Design/build work only | NOT a working background-screenshot implementation**

## Authority and scope

- Owner requests continuation toward a full Android browser, minimal repeated taps, and complete Android security/release controls.
- CDG's existing `EF-OPS-KELPIE-S24-SPIKE-005` authorizes a **synthetic offline** prototype only. The separately installed P3 `com.kelpie.browser.p3verify` passed 20/20 hidden frames and same original Compose/WebView restore on physical SM-S928B/Android 17. This does **not** authorize silently capturing real accounts or modifying production Kelpie.
- GitHub `main` remains the baseline for normal/browser code; [Draft P3 PR #6](https://github.com/imanipour2/kelpie/pull/6) is *not* merged or released.
- This separate Draft branch starts from main. It introduces a no-effects local-consent **authorization policy and parity tests**, not a renderer or a new remote endpoint. It must remain NO-GO until production integration, threat review, real phone acceptance and independent owner release approval.

## User journey (target, not yet implemented)

1. User opens Kelpie's normal browser and selects **Start a limited capture session** from a visible native interface. Present localized, purpose-specific text: exact participating tab, named authenticated agent/client, read vs capture scope, 90-second maximum, frame budget, and explicit **Start** or **Cancel**. Do not ask for confirmation **per frame** while that single grant is valid.
2. Only a local UI action issues an unpredictable **ephemeral token**. Bind it to exactly one real WebView Java instance, TabStore tab ID, document version, authenticated client identity and job attempt. Never accept an HTTP/MCP command to create/extend a grant.
3. Remote screenshot requests must pass the existing verified account/job/authorization fence first; the new lease then validates the same tab/document/WebView/client/attempt, device-unlocked state, a still-valid monotonic timeout, and remaining frame budget. Any mismatch cancels the grant and stops frame delivery.
4. The approved tab may be temporarily rendered by a **private app-owned VirtualDisplay/Presentation**, never by capturing another app, using MediaProjection, accessibility or an overlay. Its original Compose owner must release it before migration; returning the identical WebView with real scroll/form/history/cookie state requires atomic, independently tested rollback.
5. The user can always press **STOP** from a persistent in-app control and visible system notification where Android requires it. STOP releases VirtualDisplay/ImageReader/surfaces/threads, restores the original tab, invalidates the token and queued frames, and clears transient pixels. No automatic background Activity launch, keyguard bypass or recording of other apps.
6. Navigation, tab closing/switching, client disconnect, changed principal/job, page reload, lock screen, app process termination, consent expiration, network failures and display teardown revoke the grant. Restart demands a new local consent. Never auto-renew in the background.
7. All results are tagged with the live tab/document/job identity; screenshot bytes are produced only after the final guard and never retained in logs. No real account cookies, passwords, raw DOM, images or token bytes are written to diagnostic files.

## Implemented in this branch

- `core/android/.../connect/ConsentedCaptureLease.kt` with monotonic time dependency, SecureRandom 192-bit volatile token, UI-only entry contract, exact client/document/WebView pinning, no renewal, 90-second/240-frame absolute caps (20-frame default), revocation on any failed attempt. No filesystem persistence or network access.
- `core/android/src/test/.../ConsentedCaptureLeaseTest.kt` checks no consent, expiry boundary, wrong identities, wrong token, lock, STOP, unauthenticated calls, invalid limits, replay and budget exhaustion.
- `core/apple/shared/Browser/ConsentedCaptureLease.swift` + `apps/macos/Tests/ConsentedCaptureLeaseTests.swift` implement the same consent/identity/expiry/replay contract using Apple cryptographic randomness. The iOS WebKit transport remains a separate, platform-appropriate implementation and is **not** assumed to render an offscreen `WKWebView`.

## Explicit non-claims

- The `approveLocally` method must only be invoked from a trusted, native in-app user gesture. Its name alone is **not** an access-control boundary until the UI and remote transport are integrated and independently audited.
- There is no active screenshot hook, API endpoint, privileged service, network listener, real tab/surface migration, user session persistence or Android package built by these files. This branch cannot yet solve foreground-only screenshots for `com.kelpie.browser.unifiedverify`.
- Debug/development builds must not overwrite the installed 0.2.6-qa browser. The earlier P3 tests used a separate 0.2.7-qa package. Existing installed user data and Android Keystore keys must be preserved.
- Android and Apple exact feature parity is a repo policy. This branch only introduces cross-platform lease semantics; no claim of completed renderer parity.

## Required gates before claiming a normal Android release

1. **Decision/architecture:** owner approves real-tab production integration, visibility/consent wording, per-client identity and privacy threat model; record in canonical Drive CDG and GitHub PR/ADR through EF-DOC-CYCLE.
2. **Implementation:** wire the local UI and principal-bound lease to the existing authenticated gateway; add a versioned, reversible native private-display renderer with correct Compose ownership, no remote authority bypass, restored scroll and document pinning.
3. **Safety/QA:** unit+instrumented tests, 20/20 real hidden foreground app tests, ChatGPT top-Activity replay, STOP/keyguard/TTL/client swap/navigation/restart/resource cleanup, high-resolution RTL, secure-page and blank-frame failure tests, Android 17 OEM behavior.
4. **Release:** durable signing key (never a rotating ephemeral debug signature), build provenance, APK signature/SHA verification, Play Protect/install integrity, minimal manifest permissions, targetSdk/privacy controls, Android backup/KeyStore preservation, update and rollback, 24-hour persistence/connection tests.
5. **Handoff:** qualified review, P3→production evidence matrix, CI release build on exact SHA, staging install alongside existing browser, only then explicit owner GO. Merging GitHub code is never itself permission to install/release or modify any Enerfoam website.

## Reference material

- [Android VirtualDisplay](https://developer.android.com/reference/android/hardware/display/DisplayManager#createVirtualDisplay(java.lang.String,%20int,%20int,%20int,%20android.view.Surface,%20int))
- [Android WebView state management](https://developer.android.com/develop/adaptive-apps/cookbook/webview-state)
- [Android restrictions on background Activity launches](https://developer.android.com/guide/components/activities/background-starts)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Apple App privacy](https://developer.apple.com/app-store/app-privacy-details/)
- [Kelpie repository contract](../../../AGENTS.md) and [bounded physical evidence](https://github.com/imanipour2/kelpie/blob/experiment/s24-p3-compose-owner-qa-20261115/docs/qa/EF-OPS-KELPIE-S24-P3-COMPOSE-OWNER-QA.md)

**Status:** `PRODUCTION_FOUNDATION_SOURCE_ONLY / CI_PENDING / DEVICE_ADB_UNAVAILABLE / SECURITY_REVIEW_REQUIRED / OWNER_RELEASE_GO_REQUIRED`.
