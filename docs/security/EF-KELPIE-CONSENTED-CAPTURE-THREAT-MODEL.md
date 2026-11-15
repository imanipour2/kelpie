# Kelpie private Android screenshot service — security and identity threat model (DRAFT)

**2026-11-15 | Owner request: one short-lived, transparent approval per bounded session | Status: NOT INTEGRATED / NO REAL CAPTURE**

## Security properties and trust boundaries

The native Android activity, persistent TabStore, WebView renderer, HTTP/MCP LAN clients, outbound Nessie WebSocket, Android OS and the remote AI caller have different trust levels. **A paired HTTP client is NOT by itself authorized to capture a background frame.** A user must explicitly grant a bounded tab-specific capture session in Kelpie's own native UI and may STOP it at any moment.

| Boundary | Untrusted input / abuse case | Required protection and fail-closed acceptance |
|---|---|---|
| Paired LAN HTTP/MCP | Spoofed client ID in JSON/Origin/URL; leaked paired bearer | Client ID derived ONLY from AuthMiddleware/AuthDecision, passed via coroutine context; bearer and role verified at request time; never trust request body for the grant subject |
| Outbound account WebSocket | Replayed attempt, job cancellation, principal swap, impersonated transport | Derive client/job/attempt from validated UOA/ConnectAuthorization and authenticated socket generation; reject unbound or stale jobs; no borrowing from HTTP principal |
| On-device capture consent | UI overlay trick; remote automation taps native confirmation; persistent silent grant | Native, prominent user-controlled localized consent sheet with exact tab/agent/scope/duration, separate Cancel/Start and persistent STOP; no remote endpoint may invoke UI approval; confirmation never per frame within valid lease |
| Frame authority | Stolen grant token, stale screenshot callback, concurrent jobs, many frames | 192-bit unpredictable ephemeral token; max90s+per-frame budget; bind exact TabStore tab/document/version, Java WebView identity, authenticated principal and job attempt; invalidate in-flight callbacks on any mismatch before image serialization |
| WebView/Compose ownership | Detached or wrong WebView, tab switch during migration, window resize/scroll reset | Real Compose host releases before reparenting; private OWN_CONTENT_ONLY display; fail on wrong parent; restore same WebView object, scroll/form/history/document unchanged; destroy display/reader when reattached |
| Android background lifecycle | OS kills process, lockscreen, doze/FGS restrictions, background Activity launch | Never start Activity without real user gesture, never use MediaProjection/accessibility/overlays or bypass keyguard; require screen unlocked for each capture; durable foreground-session notification with STOP where Android mandates; no automatic approval after restart |
| Sensitive pages | Login/password/cookies/payment pages, private/FLAG_SECURE media, cross-app display | No cross-app capture. Native protection state must not be circumvented. Explicit owner-approved content policy; refuse sensitive origins or protected rendering where required; redaction and image review must be *tested*, not presumed |
| Pixel storage and transport | Images, token or auth details exposed in logs, crash reports, backups, app content provider | No raw image, tokens, form values, URL query/fragment, cookies or credentials in QA logs; use bounded in-memory buffers, short TTL, zeroing/recycling; only verified authorized transport receives encoded bytes; no screenshot persisted without separate action |
| Distribution | Release key mismatch, debug signature, APK substitution, accidental downgrade | Stable, owner-controlled keystore, required four signing secrets, apksigner and bundle validation, SHA/provenance readback on installed build; QA app separately named and never overwrites production app or backed-up user data |
| iOS parity | iOS native WKWebView has different render semantics | Mirror the consent/authority/STOP user contract but use platform-supported snapshot/session implementation, require iOS proof separately; no fictitious VirtualDisplay/iOS screenshot parity |

## Proof requirements (non-negotiable)

1. **Authentication binding:** Unit test body-supplied `clientId` spoof fails, revoked pairing fails, two jobs/attempts cannot share a token, per-request principal never leaks across coroutine contexts. BrowserGateway pin and account WebSocket paths need independent tests.
2. **Opt-in user interface:** TalkBack/VoiceOver accessibility, Persian RTL and English, large tap targets, disclosure of exact client/tab, visible STOP, explicit initial user action, 90-second TTL and clear expired state; never simulate user approval from ADB/agent.
3. **Capture proof:** On physical Samsung Android17 and Android16 emulator: 20/20 *new* frame pixels with Activity not visible and original browser tab owned by real Compose prior/after; mismatch or an unexpected foreground frame fails.
4. **Restoration:** Same Java WebView and TabStore identity; exact document, form, history and CSS scroll state. Confirm app remains usable after navigation and after screen rotation; no real cookies/page data in logs.
5. **Negative lifecycle:** No local grant, expired grant, keyguard, STOP during pixel callback, wrong client, tab switch, reload/redirect, login/logout, app kill/restore, changed account, disconnect, offline/Doze, render timeout and repeat stale nonce all DENY and cleanup without blank frame successes.
6. **Backwards compatibility:** Existing foreground capture, local pairing, account job and Google social login must still work with capture feature disabled. No new permissions unless justified by official Android API usage and tests.
7. **Release acceptance:** OWASP MASVS and MASTG checklist, current Google Play API level and Play Data Safety, signing provenance, independent code audit, SBOM/dependency vulnerability check, owner approved CDG decision, and post-install checksum verification. CI on a PR is **not** release permission.

## Actual current source observations (not new guarantees)

- Existing `core/android/network/AuthMiddleware.kt` already returns `AuthDecision.Authenticated(clientId)`, but `HTTPServer` discarded identity before router dispatch on main. This Draft feature branch adds `AuthenticatedClientContext` passing verified identity through coroutine context. **It has not yet connected the background renderer/screenshot route.**
- `NessieConnection.setForeground(false)` currently invokes `stopCurrent("App entered background")`. The production job cannot continue while ChatGPT is foreground until this job/lifecycle state machine is redesigned under the same local lease.
- P3 synthetic physical proof 20/20 and same-WebView restore PASS demonstrates feasibility, **not** authorization for real user tabs or cross-app capture.
- Android main manifest currently requests record audio and network/discovery and allows cleartext traffic (paired LAN), requiring threat review. API36 migration is separately proposed in Draft PR #8.

**Open review gates:** Native UI privacy text, authenticated account client identity, background job lifecycle, private renderer integration, true negative hardware tests, iOS implementation, key custody, API36 compatibility, code review and release approval.

**Classification:** `SECURITY_DESIGN_DRAFT / NO_REAL_TAB_CAPTURE / SOURCE_ONLY / RELEASE_NO_GO`.
