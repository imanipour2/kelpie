# P4 threat model and unified operation gate — source candidate

**Asset:** actual tab Java WebView, its cookies, document/form state, pixels, network identity, user consent and native resources.

**Trust boundaries:** untrusted JSON requestId/attemptId; authenticated native principal via Ktor pairing middleware; explicit real user gesture; registry local lease; WebView/Compose ownership; Android PowerManager/Keyguard; HTTP, MCP, Gateway, Nessie; transport result serialization.

**Threat → control:**
- B guesses a requestId or supplies forged JSON clientId → never call global revoke; reject before owner lease validation; stop/status require real authenticated principal.
- Old attempt/replay → reject without invalidating newer request generation.
- Tab/document/WebView drift for actual owner → fail closed and native cleanup.
- Ordinary screenshot/DOM/click/type while P4 has private ownership → deny at Router and BrowserGatewayBinding; Nessie direct path checks HandlerContext admission. Ordinary behavior when QA is disabled remains unchanged.
- Concurrent frames → `Mutex.tryLock()` fail without claiming grant/bumping pixel sequence.
- Expiry/STOP during PNG encode → registry status rechecked before response; physical race and transport boundary still require test/review.
- SCREEN_OFF / keyguard → context-registered non-exported system receiver, PowerManager.isInteractive + KeyguardManager check. The screen-off candidate is source-only until device QA.
- Diagnostics/traffic → NOT_FOUND status contains no renderer counters; authenticated HTTP responses `Cache-Control: no-store`; no CORS allow-all or raw CDP endpoint introduced.

**Admission limitations to investigate:** browser-control native fast-paths, asynchronous operations admitted before P4 starts, account/Nessie callbacks, remote navigation direct WebView paths, transport buffering after last authorization check, and process death. The existence of a policy class is not proof that every execution path is gated.

**Acceptance tests:** WrongClientCannotRevokeOwner, WrongRequestCannotStopOwner, StaleAttemptCannotAffectNewGeneration, AuthenticatedClientSwapDenied, OwnerDocumentChangeRevokesAndReleases (device release portion open), ReplayAfterStopDenied, PendingConsentCannotBeOverwritten, ConcurrentDistinctPrincipalsIsolated (JVM model only). Require two *real* separately paired clients on S24 for final security acceptance.

Sources: OWASP MASVS https://mas.owasp.org/MASVS/ ; Android app security https://developer.android.com/privacy-and-security/security-best-practices ; authenticated client source `AuthenticatedClientContext.kt`; project `AGENTS.md`.
