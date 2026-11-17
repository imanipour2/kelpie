# ADR-R5-005 — Agent-first startup with one combined native owner consent

Status: OWNER_APPROVED_PRODUCT_REQUIREMENT / IMPLEMENTATION_OPEN / DRAFT / NO_RELEASE
Date: 2026-11-18
Canonical approval: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit (EF-OPS-KELPIE-AGENT-FIRST-STARTUP-20261118)
Stacked on: https://github.com/imanipour2/kelpie/pull/55 (R5 private beta branch)
Supersedes in scope: separate background/agent opt-in prompts as product experience; does not supersede credential-sensitive approvals, native STOP, pairing security or Android restrictions.

## Owner intent
Kelpie is an AI-agent-first browser. A normal browser view without an active agent is not an acceptable substitute for the core task. On every **new real Kelpie process session** the app offers one decision on the **phone**, not by a ChatGPT tool: (1) **Enable verified AI agent + continue in background**, or (2) **Exit completely; disable agent**. Ordinary Activity HOME/ChatGPT switches or new tabs in the *same active process* do not ask again. A process restart resets authorization.

## Security-critical distinction
The combined approval is **one UX action**, but it must atomically bind two distinct conditions: app-owned background runtime eligibility and an authenticated, already paired **verified principal** agent authorization. Merely pressing a local button with no authenticated paired principal MUST NOT create a grant. The remote client may request a dialog but may never approve it. If there is no verified pairing, the startup gate leads to the existing secure pairing ceremony; if several clients are paired, show the verified name and let the owner choose, never silently pick one. The UI's principal label is advisory: native authentication must verify the actual principal on every command.

The gate may display a loading/connection state while the verified principal is unavailable. An app-created pending request must be bound to a principal whose current pairing/bearer authentication was proven through the trusted gateway. Do **not** call `AndroidAgentProcessSession.request(nameFromStoredPairing)` without proving a current authenticated association. This is the core implementation risk.

## State contract
- NEW_PROCESS / OFF -> STARTUP_GATE. The browser is not agent-operational; no remote DOM/screenshot/tab mutation before consent. The existing listener may be temporarily available **only** to finish authenticated pairing or session request, not as an open unauthenticated browser-control endpoint.
- PAIRED_AUTHENTICATED_PENDING -> show bound principal identity, agent/background scope, exclusions, persistent STOP, and two unambiguous actions. The same native approval tap confirms both aspects.
- Native affirmative + unlocked owner + visible STOP notification permission/channel + fresh pending authenticated principal -> ACTIVE_AGENT_SESSION with new process-memory generation; allow eligible Kelpie-owned WebViews in background, not other apps.
- Negative / Exit -> stop pending and active generation, cancel jobs, dispose private renderer, stop Kelpie-owned HTTP foreground service, close Activity/task. Never stop independent Termux or Windows tunnel without separate approval. Securely fail closed on interrupted cleanup; no automatic restart.
- STOP in app or Android notification -> OFF immediately; browser may remain visible but controls are disabled, with an explicit restart-agent path. Exit is separate and stronger.
- Lock / permission denied / renderer change -> SUSPENDED/FAIL-CLOSED according to existing admission policy. An unlock is not a new approval. Process death/reboot -> OFF; no persistent consent flag or silent fresh grant.
- Ordinary browsing (navigation, tabs, DOM, click, typing, scrolling and fresh private WebView screenshots) operates under existing authenticated principal + consent generation + document epoch/renderer fence + monotonic sequence. Sensitive operations (payment, publishing, irreversible deletion, credential export) require independent owner authorization.

## Native UX
Title: **Enable Kelpie AI Agent**
Body: **The verified agent [authenticated client name] can browse, operate and read Kelpie-owned tabs, including while Kelpie is in the background. Only Kelpie web pages may be captured. A visible STOP control remains available. Sensitive actions ask separately.**
Primary action: **Enable agent & background access**
Secondary action: **Exit Kelpie & disable agent**
Show pairing CTA when no authenticated principal exists; never show a misleading "Approve" for an unverified connection.
A system Android POST_NOTIFICATIONS permission request may follow the owner's button; this is a separate OS permission, not an additional app-owned agent consent. Fail closed on denial. Provide TalkBack labels, 48dp targets, coherent RTL/localization and English default on the global Kelpie app.

## Implementation seams (not completed by this ADR)
1. `KelpieBrowserSession` / `BrowserScreen`: R5-only startup gate before normal page interactions, preserving existing non-R5 behavior and embedded host support.
2. `PairingStore` + authenticated `Router.verifiedClientId()`: truthful verified principal identification, no trust in client-provided string and no pairing token disclosure.
3. `AgentSessionCoordinator` + `AgentSessionConsentUi`: one locally approved native flow, not a remotely approved grant; preserve `SessionConsentCore` as sole authority.
4. `R5AgentCoordinator` + `KelpieNetworkService`: ensure app-owned service and renderer follow ACTIVE/STOP/Exit; Termux tunnel is a separate dependency.
5. Native STOP notification and Android lifecycle: fail closed for missing notification, focus/lock, process death; avoid background Activity launches.
6. Update Android/iOS parity plan or obtain explicit scoped owner exception; do not merge overlapping PR53/54/55 independently.

## Tests and release gates
- No authenticated principal -> no grant even if a local user taps Enable; pairing first.
- Two paired principals -> explicit selection and true principal binding; other principal rejected.
- New process/reboot -> local approval required; HOME/ChatGPT switch -> no repeat.
- 25 cycles of authentic active background tab/new/select/close, DOM/screenshot/click/scroll, no unwanted Activity foreground.
- STOP via app/notification and Exit -> no privileged commands, stale-generation replay denied and Kelpie-owned foreground service stopped.
- Android 17 Samsung S24: keyguard, incoming call, Doze, Wi-Fi/cellular, notification blocked, process death, 48dp/TalkBack, long-running battery and temperature.
- Review 8 P0 security findings; signed APK provenance, rollback, explicit private QA acceptance. No automatic main merge/public release.

## Evidence / current limitation
PR55 source is draft and isolated QA. Owner-provided ADB evidence shows `com.kelpie.browser.r5betaverify` on port 18461 /health 200 and Termux 8080 /readyz 200. ChatGPT capabilities responds, but consentGeneration is null and tabs.list FORBIDDEN. The present ChatGPT exposed connector has browser_control, not agent_session. This ADR does **not** claim an installed fix, signed APK, passing CI, or live hardware QA.

References: Android FGS restrictions https://developer.android.com/develop/background-work/services/fgs ; notification permission https://developer.android.com/develop/ui/views/notifications/notification-permission ; project CDG and https://github.com/imanipour2/enerfoam-website/issues/98 .
Status: DOCOPS_CDG_APPROVED / GH_DRAFT_MIRROR / IMPLEMENTATION_PENDING / SYNC_BLOCKED_MAIN / BETA_NO_GO.
