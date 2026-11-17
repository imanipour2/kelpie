# ADR-K1-001 — Owner-approved Kelpie R5 upgrade and browser-based OAuth
Status: OWNER_APPROVED_DIRECTION / K1_IMPLEMENTATION_IN_DRAFT / RELEASE_NO_GO
Decision ID: EF-OPS-KELPIE-R5-UPGRADE-20261117
Owner approval date: 2026-11-17
Canonical decision: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit
Approved implementation plan: https://docs.google.com/document/d/1ihOZMIuC4XMKHeTdrjckWnuhqumvKw5uA80hRCzqAT8/edit
Independent audit: https://docs.google.com/document/d/1beZYBF0Odwh9i5gcLF3DeThm5YNhXOLkdIsKiRNx3PE/edit
Hardware evidence: https://github.com/imanipour2/enerfoam-website/issues/98
Starting source: b79d14a2f55588b1905cd5d68e09ee1bf2f7d031; branch feat/k1-external-oauth-browser-20261117.

## Approved bounds

Keep one owner-controlled WebView, Native Owner Consent, STOP/lock fail-closed, principal and document-epoch protections. Six-phase roadmap: K0 source/artifact baseline; K1 Google/OAuth; K2 MCP commands/BACK; K3 device lifecycle; K4 Termux connectivity/onboarding; K5 release hardening/security. The eight P0 audit security findings are deferred **only for implementation sequencing**, not accepted risks for beta or production. No real credentials, payment, publishing, deletion, or public release from this decision. No Android/iOS parity exception was approved, so existing policy remains; do not merge Android-only code into main without resolving that gate.

## Finding: Google blocked sign-in within Kelpie

Owner screenshot: `accounts.google.com` returned "Couldn't sign you in / This browser or app may not be secure", with Google's warning on embedded browsers or automation. This is expected Google OAuth protection, not evidence of malware. `AccountLoginActivity.kt` currently loads Kelpie/UOA OAuth in a WebView and lets subsequent HTTPS Google login remain embedded. `UOAAccount.kt` only falls back to system browser when WebView cannot be created. `BrowserUserAgent.kt` masquerades as Android 14 / Pixel 8 Pro with static UA despite the actual S24 Ultra Android 17. `UOAAuthorization.kt` already implements random OAuth state, PKCE S256 and exact callback; preserve it.

## Technical direction and staged work

AUTH-01 (approved): use an OS-supported real browser for UOA app-owned OAuth (Auth Tab/Custom Tabs preferred with AndroidX Browser), retaining current PKCE/state, exact redirect/callback and once-only code exchange. First isolated implementation slice may use standard external browser ACTION_VIEW as safe baseline, with verified callback and UI/handoff; it is NOT full Auth Tab acceptance. Browser callback replay, cancellation, deep link interception and process death must be tested.

AUTH-02 (approved): for Google OAuth initiated inside arbitrary website tabs, show human choice to continue in a supported external browser, without promising that Chrome's cookie jar will become Kelpie WebView's cookie jar. No Google cookie extraction/injection or auto-login automation. Actual site-side session handoff only with site owner and separate permission. Third-party Google SSO feature remains OPEN until UI and physical E2E QA pass.

AUTH-03 (approved): stop static fabricated WebView UA; use Android System WebView's genuine current default UA. This improves browser honesty but **does not** circumvent Google's embedded-OAuth restrictions. No Chrome masquerading or automation concealment.

## Security and acceptance

Keep native consent and STOP; no bypass of CAPTCHA/OTP. Verify from the app: external UOA login, valid PKCE callback, invalid/duplicate callback rejected, no browser available -> safe error, no token leakage, cancel/timeout, host trust and UI clarity. Test Google webpage redirect separately and explain cookie limitation. Preserve APK signer, package ID, app data and rollback. QA first in isolated private APK on owner S24; no automatic ADB install or WordPress changes.

## References and version context

- Google: https://developers.google.com/identity/protocols/oauth2/policies
- Chrome Auth Tab: https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- AndroidX AuthTabIntent: https://developer.android.com/reference/androidx/browser/auth/AuthTabIntent
- AppAuth for Android: https://github.com/openid/AppAuth-Android
- RFC8252 native OAuth: https://www.rfc-editor.org/rfc/rfc8252
- Android WebView: https://developer.android.com/reference/android/webkit/WebView

## Governance and proof

Source of truth: CDG (Drive); code and CI: GitHub branch/PR; hardware: device logs and hashes. This branch is a code proposal; it is not the website's six-phase plan, nor a release approval. Link reviewed PR, CI, APK sha, installation test and separate Android/iOS parity decision before merge. Flag SYNC_BLOCKED_MAIN until main is read back after authorized merge.
