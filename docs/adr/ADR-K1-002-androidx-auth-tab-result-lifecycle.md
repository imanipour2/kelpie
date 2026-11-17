# ADR-K1-002 — AndroidX Auth Tab for Kelpie-owned UOA OAuth (QA proposal)

Status: DRAFT IMPLEMENTATION / CI PENDING / NO DEVICE INSTALL / NO RELEASE  
Owner development direction: EF-OPS-KELPIE-R5-UPGRADE-20261117  
Canonical approval: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit  
First safe external-browser slice: https://github.com/imanipour2/kelpie/pull/49  
Audit: https://docs.google.com/document/d/1beZYBF0Odwh9i5gcLF3DeThm5YNhXOLkdIsKiRNx3PE/edit

## Root problem

Google refuses OAuth from embedded Android WebView. The owner saw the genuine rejection screen on S24. Replacing the fabricated user agent cannot make an embedded agent acceptable. Kelpie-owned UOA OAuth can use a browser-based Authorization Code + PKCE flow because the app owns its own client and redirect; ordinary third-party websites' Google sign-in is a separate integration problem with isolated cookie storage.

## Implementation proposal

- Keep the existing `UOAAuthorization` S256 PKCE, random state, strict custom-scheme callback, one-time code redemption, owner-visible user action and local credential storage unchanged.
- Launch `androidx.browser:browser:1.9.0` AuthTabIntent via a non-exported `AccountAuthTabActivity` with ActivityResult registration before `onCreate`. AndroidX provides a Custom Tabs fallback on browsers without Auth Tab support. If no Auth Tab handler can start, try normal external HTTPS ACTION_VIEW; if no browser exists, fail closed.
- The result carries the original attempt nonce and callback URI to the existing `UOAAccount.finishLogin` path. Independent `AccountCallbackActivity` may receive the browser custom scheme on fallback; the existing PKCE state is the single authority. A cancellation arriving later must not revoke a completed or superseded attempt; check the current generation and the pending attempt before responding.
- Do not make an Auth Tab callback equivalent to successful authentication. Only the `state`-checked one-use authorization code exchange and authenticated profile request establish account sign-in.
- No WebView, JS bridge, or transfer of Chrome/Google cookies occurs in this account-auth flow. The original embedded login Activity remains non-default for now and should be removed after device acceptance and parity review.

## No claims and open QA

This draft does NOT assert the Google account session now appears in arbitrary third-party tabs inside Kelpie. On a third-party site whose OAuth callback belongs to that site, Chrome and Kelpie WebView cookie stores are separate; generic owner-visible handoff UI is a future independently reviewed K1 step. Do not automate Google account credentials or CAPTCHA.

Before merging: ensure Gradle 1.9.0 resolves, Kotlin/Android instrumentation and CI pass, all included Android/iOS parity policy approvals or a scoped exception, and on-device owner-approved test of AuthTab and fallback with cancellation, rotation, process death, valid/invalid/duplicate callback, no-browser fail closed and PKCE rejection. The prior PR49 ACTION_VIEW path remains an independently reviewable fallback.

No change to existing R5 native AI-agent consent, STOP, tab/document epoch, microphone, Termux configuration, website LIVE/STAGING, or release status.

## Official sources

- https://developer.android.com/reference/androidx/browser/auth/AuthTabIntent
- https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- https://developers.google.com/identity/protocols/oauth2/resources/best-practices
- https://www.rfc-editor.org/rfc/rfc8252

Status: DRAFT / OWNER_APPROVED_DIRECTION / QA_AND_PARITY_OPEN / SYNC_BLOCKED_MAIN / PUBLIC_BETA_NO_GO.
