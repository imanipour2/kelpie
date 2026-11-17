# ADR-K1-003 — AndroidX Custom Tabs 1.8 compatibility lane (K1 QA)

Status: DRAFT / OWNER_APPROVED_DIRECTION / SOURCE_ONLY / CI_PENDING / NO_RELEASE  
Owner decision: EF-OPS-KELPIE-R5-UPGRADE-20261117  
CDG: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit  
K1 base: https://github.com/imanipour2/kelpie/pull/49  
Alternative newer API experiment: https://github.com/imanipour2/kelpie/pull/51

## Why this lane

The owner saw Google OAuth reject Kelpie's embedded WebView. Official Google policy requires a system-supported user agent. The approved K1 direction is Auth Tab preferred, Custom Tabs fallback, and no cookie theft or UA masquerading.

The current Android project uses compileSdk 34 and an older AGP toolchain. AndroidX Browser 1.9.0 Auth Tab is known to require at least compileSdk 36, with upgraded AGP; this is a separate toolchain migration with broad iOS parity/build implications. Therefore, keep the Auth Tab experiment unmerged and pursue a small compatible QA lane with AndroidX Browser **1.8.0**, which supports standard Custom Tabs under compileSdk 34. Never falsely label this lane as Auth Tab.

## Behavior

`UOAAccount.openInBrowser` uses `CustomTabsIntent.Builder().build().launchUrl` with the original exact registered HTTPS UOA authorization endpoint. A missing compatible handler causes fallback only to external `ACTION_VIEW`, never to an in-app WebView. Existing `UOAAuthorization` S256 PKCE and unpredictable state, exact callback scheme/host/path, one-time token exchange and timeout still determine success.

The cookie/session jar belongs to the external browser. No Google credential, token, OTP or cookie is copied to Kelpie. This only fixes the app-owned UOA OAuth user-agent. Third-party websites opened as Kelpie WebView tabs may still reject Google SSO; a separate owner-visible handoff with explicit no-session-continuity notice is required.

## SDK34 provider discovery and launcher boundary

A compatibility review after CI found that the first draft launched a Custom Tabs intent
without verifying that an actual Custom Tabs browser provider was available. On Android
11+, the package visibility declaration is required for provider discovery.

The corrected source adds `android.support.customtabs.action.CustomTabsService` to the
library manifest, checks `CustomTabsClient.getPackageName` and binds the resulting package
explicitly when launching. The dedicated `UOAExternalAuthLauncher` checks the exact HTTPS
UOA authorization endpoint before either route. When no provider is available, or its
activity disappears, the only fallback is an owner-visible Android `ACTION_VIEW` request.
No embedded WebView or custom User-Agent is used. A system VIEW handler is not proof
that an arbitrary third-party site's session will continue inside Kelpie.

CI source tests exercise legitimate and hostile authorization URLs; exact provider
selection, fallback, Android 17 callbacks, cancellation and process death require an
instrumented/physical S24 test. Relevant official guidance:
https://developer.chrome.com/docs/android/custom-tabs/howto-custom-tab-check and
https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsClient .

## Acceptance gates

1. Android Gradle dependency resolution and unchanged SDK34/AGP baseline; lint and unit test compile.
2. On physical S24, owner approves system/Custom Tabs login; external browser callback returns verified PKCE code and profile; cancel, missing browser and timeout produce safe error.
3. Reject malformed/duplicate/mismatched state and callbacks; no token/URL secret logging or WebView credential capture.
4. Report exact QA APK SHA, signing certificate, installed user profiles and rollback; never replace current device without explicit hardware approval.
5. Android/iOS parity policy still blocks merge to main without independent scoped exception. Publication requires separate release authorization, security audit and CI.

References:
- https://developer.android.com/jetpack/androidx/releases/browser
- https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- https://developers.google.com/identity/protocols/oauth2/resources/best-practices
- https://www.rfc-editor.org/rfc/rfc8252
- https://github.com/imanipour2/enerfoam-website/issues/98

Status: QA_DRAFT / OWNER_APPROVED_DEVELOPMENT / PARITY_OPEN / MAIN_NOT_MERGED / PUBLIC_BETA_NO_GO.
