# K1 — supported-browser OAuth for Kelpie (owner-approved implementation direction)

**Record:** EF-OPS-KELPIE-R5-UPGRADE-20261117 / AUTH-01..03  
**Date:** 2026-11-17  
**Status:** OWNER_APPROVED_D​IRECTION / DRAFT_PR / NO_MERGE / NOT_INSTALLED / RELEASE_NO_GO  
**Canonical owner decision:** https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit  
**Roadmap:** https://docs.google.com/document/d/1ihOZMIuC4XMKHeTdrjckWnuhqumvKw5uA80hRCzqAT8/edit  
**Independent audit:** https://docs.google.com/document/d/1beZYBF0Odwh9i5gcLF3DeThm5YNhXOLkdIsKiRNx3PE/edit  
**Device/test ledger:** https://github.com/imanipour2/enerfoam-website/issues/98

## Source-level root cause

The original `AccountLoginActivity` explicitly opened UOA authorization and ensuing Google handoffs inside an embedded Android WebView. `AccountLoginPolicy.allowsNavigation` allowed subsequent HTTPS IdP navigations. Google rejects some embedded sign-in user agents; an apparently successful TLS handshake is not an account security verdict. Separately `BrowserUserAgent.kt` hard-coded a fake Android 14 / Pixel 8 Pro / Version 131 UA on the real Android 17 Samsung.

## Decisions and boundaries

- **AUTH-01:** A *native-app-owned* UOA OAuth authorization MUST launch in a supported external browser context. Use an AndroidX Auth Tab / Chrome Custom Tabs integration when verified compatible; the system ACTION_VIEW browser is the safe no-new-dependency baseline/fallback. Keep existing OAuth authorization code with S256 PKCE, 32-byte random `state`, exact one-use callback and server code exchange. Do not read Chrome cookies/credentials/OTP. The callback handler MUST verify URL/nonce/state independently before the token exchange. This first slice can use the system browser without claiming Auth Tab support is already installed.
- **AUTH-02:** A Google/third-party SSO redirect from an *arbitrary website opened inside a Kelpie tab* is distinct. Browser-to-WebView cookie-jar sharing is NOT assured. A future main-frame-only, owner-confirmed handoff to a supported external browser MUST disclose that the login may not make the original Kelpie tab authenticated. No third-party cookie scraping/injection or unowned website server-side session transfer is permitted.
- **AUTH-03:** Stop impersonating Android14/Pixel8/131; use the real default Android System WebView UA in normal tabs, without misrepresenting WebView as Chrome or bypassing provider anti-automation policies. Regression-test site compatibility.
- Existing one-original-WebView ownership, native owner grant/STOP, consent generation, epoch/command-proof, and strict denial on lock/hidden/other-app remain unchanged.
- Existing audit KA-P0-01..08, KA-P1-01..12 and KA-P2-01..06 remain OPEN; fixture-only K1/K2 work does not authorize production, sensitive account automation or payments/deletions. Android/iOS parity policy and release signing remain in force; Android-only merge requires separate approval.

## Explicit source/test acceptance

1. App-owned sign-in authorization never creates an embedded auth WebView; enforce exact `https://authentication.unlikeotherai.com/oauth/authorize` entry.
2. The system handles OAuth pages; callback uses exact custom scheme and existing pending state/PKCE/one-use semantics, rejects malformed/duplicate/wrong state and does not accept login from a mere OAuth browser launch.
3. Cancellation, no supported browser, timeout and process recreation are visible/recoverable. Browser absence MUST fail closed rather than fallback into embedded Google login.
4. Ordinary WebView tabs use honest default UA. Main-frame Google OAuth handoff for third-party sites is a separate task and remains unimplemented until confirmed UX/QA.
5. For QA: JVM policy tests; Android build/lint; no credential/body/URL in logs; exact signed QA APK with rollback; real owner-approved S24 sign-in tests (with non-sensitive test account only after security gate) and provider/Chrome integration. **No installation or account login is claimed from CI alone.**

## References checked

- Google OAuth embedded-user-agent rejection: https://developers.google.com/identity/protocols/oauth2/web-server
- AndroidX AuthTabIntent (added in androidx.browser 1.9.0): https://developer.android.com/reference/androidx/browser/auth/AuthTabIntent
- AppAuth Android external browser pattern: https://github.com/openid/AppAuth-Android
- Native OAuth best practice RFC 8252: https://www.rfc-editor.org/rfc/rfc8252
- WebView and user agent: https://developer.android.com/reference/android/webkit/WebSettings
- WebViewClient URL interception: https://developer.android.com/reference/android/webkit/WebViewClient

**Not a final product acceptance, iOS parity exception, GitHub-main merge, device install, or website publication.**
