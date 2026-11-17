# Kelpie R5 — Android personal agent-first browser scope and security decision

Date: 2026-11-17
Authority: User request for personally controlled S24 Android browser with connected AI agent; no automatic production/public-release authorization.
Status: ARCHITECTURE ADOPTED / PARTIAL SOURCE IMPLEMENTED / ANDROID CI PENDING / HARDWARE QA PENDING / CONNECTOR FORBIDDEN / BETA NO-GO
Base: draft PR #35 stacked on #34 (do not merge into main without source, device and security gates).
Device evidence: https://github.com/imanipour2/enerfoam-website/issues/98

## Goal and boundaries

Make Kelpie a locally free Android browser with a secure authenticated tool interface for any paired agent (ChatGPT, other provider or user's own agent). Kelpie owns the Android tab engine, cookies, UI, native user interaction and user-granted device capabilities. The connected agent owns planning, search strategy, click choices, text generation, optional cloud-tool selection and deciding when to ask the user for help. Do not bundle a paid LLM/cloud/browser account or claim paid services will be free.

## CAPTCHA and human takeover

- Allow ordinary native browser page interaction under one owner-approved process session, including normal links, challenge refresh controls and visible ordinary buttons when the website permits it.
- Never inject or fabricate CAPTCHA answers, bypass challenge verification, conceal automation identity, steal challenge tokens or circumvent website anti-bot authorization.
- R5 DOM snapshot includes a **heuristic** `humanChallengeDetected` hint for recognizable CAPTCHA/Turnstile/iframe markers. It is not proof of challenge presence/solvability and does not capture solution tokens.
- The agent may attempt normal supported interactions; if it cannot handle challenge legally or the widget requires actual human verification, it should explain the blocker, foreground the Kelpie session via user action and ask the person to finish, then resume only after fresh page observation. The app must not simulate native owner taps/approval. A dedicated typed `NEEDS_HUMAN` and foreground-resume UX remains to implement.
- Current R5 fixed-JS click is inadequate for cross-origin iframe CAPTCHA interiors or coordinate taps; do not claim complete CAPTCHA automation.

## Secure passwords, Autofill and passkeys

- Android Autofill framework (API 26+) is the primary path. Existing `android.webkit.WebView` virtual views let user's chosen AutofillService (e.g., Google Password Manager or compatible manager) offer login suggestions and save prompts. The user decides per credential and website.
- Explicitly declare `importantForAutofill = YES` on the same WebView in R5; never read provider vault, never add password export, never transmit password/OTP/card number through `agent-command`, logs, screenshots explicitly taken from login fields, prompts or untrusted cloud tools. Current R5 FILL/TYPE rejects password, one-time-code and cc-* fields.
- New R5 WebAuthn code feature-detects `WebViewFeature.WEB_AUTHENTICATION` and enables `WEB_AUTHENTICATION_SUPPORT_FOR_APP` only. This supports eligible sites verified with Digital Asset Links; it does NOT grant arbitrary-site browser privilege. Unprivileged QA APK must NOT enable `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER` without Android/browser privilege. Passkey sign-in needs actual Android UI hardware testing and genuine site association.
- Native Credential Manager account/biometric prompts must be user controlled. A saved cookie/profile may avoid later logins in an existing tab; do not extract Chrome-private or Samsung Password Store files.
- Do not use deprecated `WebSettings.setSavePassword` or `WebViewDatabase.setUsernamePassword` as a substitute for Android Autofill.
- QA: login as real user on a test account, save prompt opted in; restart Kelpie without deleting app state; verify subsequent suggestion; test provider off/on, mismatched origin and password redaction; verify no agent-visible secrets and no uncontrolled cross-profile transfer.

## Browser completeness acceptance inventory

### P0 — personal beta requirements
1. Address/search bar: URL and free text, configurable engine, query encoding and safe navigation; back/forward/reload/home.
2. TabStore: list, open, switch, close, restore; private-display ownership transfer must not destroy the transferred WebView or leak sessions; preserve forms, scroll, history and tab identity. Dedicated `agent-tabs` currently works; `OPEN_TAB/CLOSE_TAB/SWITCH_TAB/LIST_TABS` command variants are not implemented.
3. Page actions: semantic snapshot, click normal button, fill text, select, wait, scroll, screenshot (R5 private owned display); freshness, DOM/SPA state and native renderer proof.
4. Android Autofill, user-owned credential provider, optional eligible WebAuthn, bookmarks/history/download manager, safe link handling; app-private storage remains private.
5. Native owner-approved process session, paired principal binding, forced STOP, no lock-screen capture, no device-global ADB automation, no secret propagation.
6. Fix provider-specific direct MCP adapter `FORBIDDEN`; exact session-generation mapping, command ID monotonic sequencing, no stale replay, no generic legacy route reopening.
7. Full Android compile/ktlint/QA jobs and real S24 test on the exact artifact; memory, STOP, lock/Doze, 24h performance and rollback.

### P1 — professional browser quality
Accessibility tree and robust role/label locators, coordinate taps with device viewport mapping and fresh target proof, shadow DOM and iframe diagnostics, file chooser and SAF-based upload, Downloads API/notification, pop-up/new window policy, website permissions, PDF viewer/open/download, request desktop site, find-in-page, zoom, share/copy URL, open external app via explicit Android intent, clipboard, translation via optional external service, bookmarks and favorites search, history clear/site-data delete, TLS/Safe Browsing warnings, HTTPS-first, privacy preferences, third-party cookie setting, origin-scoped notifications/camera/mic only by user grant, per-site data deletion, multi-profile partition policies, user-agent hints and local settings backup/export.

### P2 — agent tool ecosystem
Versioned `tools/list` + capabilities, `tabs.*`, `navigate`, `snapshot`, `click`, `fill`, `select`, `wait`, `screenshot`, `scroll`, `download`, `upload`, `human.assistance`, status/stop and typed receipts. Agent is the planner, never delegated an unbounded raw JavaScript remote evaluator or unrestricted arbitrary local filesystem.
Agent may call its *own* externally connected cloud tools with separate credentials/consent; Kelpie need not know cloud vendor, store cloud keys or transport Android cookies. Explicit human permission before any session/token export. Remote cloud session is NOT the local phone WebView. Optionally create a bounded capability broker that reports provider-agnostic external-action offers, but do not implement a fake Google Cloud login or API.

### P3 — optional infrastructure
Managed browser profiles/CDP, anti-bot and proxy networks, parallel isolated cloud execution, file/object storage, web monitoring and long-running jobs remain outside the free Android core. Hosted providers may charge per minute, action or token. Offer open protocol connectors only, not unconditional free claims.

## Strict exclusions and approvals

Payments and banking credentials remain blocked from automated agent control until a distinct payment security design is approved; device biometrics/Android system overlays and payment credential access never become agent abilities. Generic action classifier keyword-based finance checks in PR35 are **not sufficient for production**. Critical issue: after autofill, clicking an ambiguous unlabeled financial button is not reliably identified from DOM alone. Harden with typed high-risk action classification and transaction boundaries before beta.

User preference permits ordinary publication/deletion choices by AI, but the R5 native enums `PUBLISH` and `IRREVERSIBLE_DELETE` are still unimplemented. Prompt injection is not owner authority; STOP/lock safety and principal/session identity are not relaxable. No silent re-authentication or cloud data upload.

## Research sources, scope and limitations

- Android Autofill: https://developer.android.com/identity/autofill
- WebView Autofill virtual structure: https://developer.android.com/reference/android/webkit/WebView
- WebView Credential Manager integration: https://developer.android.com/identity/sign-in/credential-manager-webview
- WebAuthn support and browser privilege: https://developer.android.com/reference/androidx/webkit/WebSettingsCompat
- Browser Use OSS agent/CLI + hosted cloud: https://github.com/browser-use/browser-use
- TinyFish cloud browser/CDP, paid minute pricing: https://www.tinyfish.ai/browser
- TinyFish API/agents and saved profiles: https://www.tinyfish.ai/
- Existing fork source: https://github.com/imanipour2/kelpie/pull/35
- Native QA: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6935540494

All vendor feature/pricing statements are vendor claims, may change, and are not tests of Kelpie Android. Android code changes have NOT been accepted on hardware merely by being committed to GitHub.
