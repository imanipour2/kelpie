# R5 native process-session consent — isolated Android QA candidate

Status: **DRAFT / CONSENT-ONLY / NO WEBVIEW ACTIONS / OWNER-AUTHORIZED S24 QA INSTALLED / NO PUBLIC RELEASE / PARITY_OPEN**  
Date: 2026-11-16. Tracking: https://github.com/imanipour2/enerfoam-website/issues/98

## Product decision and separation of concerns
The owner-approved `EF-OPS-KELPIE-P4-SESSION-CONSENT-20261116` V2 goal is **one local native approval** for a paired client across ordinary sites and tabs during a real live Kelpie process session, without repeated per-frame or per-domain taps. This **does not** authorize transactions, public publishing, deleting, credential export, protected content, other apps or lockscreen capture. The technical ADR and Android/iOS parity decision are not finalized by this QA branch.

This source increment implements only:
- A process-memory `SessionConsentCore` owner; no persistence of grant/generation.
- Authenticated paired HTTP `/v1/agent-session-request`, `/v1/agent-session-status`, and `/v1/agent-session-stop`; principal always from `AuthenticatedClientContext`, never JSON. No unauthenticated grant route.
- A native Compose affirmative/deny dialog; remote clients cannot invoke approval. Approval requires foreground window, unlocked/interactive device and permission to post a visible STOP notification.
- A nonexported notification STOP receiver tied directly to process-memory consent, not a weak Activity reference. Local UI STOP and owner-authenticated remote STOP revoke the same generation.
- Lock screen `ACTION_SCREEN_OFF` suspends the authorization epoch; on resume, native keyguard/power state is checked before the session becomes active again. Actual remote operations remain disabled.
- A separate opt-in QA build profile `kelpieR5SourceQaProfile` (despite its retained development flag name), package `com.kelpie.browser.r5sourceverify`, port18457, code31. No production enablement or special service launch.
- Retention of consent through ordinary HOME and configuration re-creation; process death loses the singleton, and ordinary Activity destruction outside configuration change revokes. This is **not** proof of WebView/form state continuity.

## Explicitly not implemented
This branch does not connect `AgentOperationRuntime` or `VerifiedNativeBrowserAdapter` to real HTTP/MCP/Nessie operations, does not let the agent click/type/scroll, and does not return DOM/PNG or build private display/session-owned WebViews. It is a consent/security UX QA candidate, **not a functional background-control browser**.

## Security-sensitive negative tests required before any install/merge
1. Unauthenticated request (401), second principal denied without state leakage, wrong/stale prompt denied and inability to approve via remote method.
2. No native tap while locked/screen off; notification permission denial cannot activate a grant; no accessibility/ADB fabricated approval.
3. One real tap persists across HOME, ordinary page changes, and Activity recreation without a new grant. Process kill invalidates; local STOP and notification STOP revoke immediately.
4. Pending request expires; no consent generation in Android saved state, notification, logs, remote wrong-principal status or file storage.
5. Two independent paired principals and request/STOP races; no leakage after await; no browser effects accepted by this candidate.
6. Android/iOS parity or separately approved scoped exception before any `main` merge, and signed public `RELEASE_APPROVAL` later.
7. After a separately approved device plan, build exact CI SHA, verify APK SHA/package/version/debug signature/no RECORD_AUDIO/native alignment and real listener, then on SM-S928B test 50 cycles, lock/Doze/STOP/permissions while preserving current installed apps.

## Sources and policy
- Canonical CDG V2 and owner ADR: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit and https://docs.google.com/document/d/1SSDkaVlcbz7U8Pr0R5wvcKRFBB1EjRSOsyqRi9LCeCg/edit
- Android foreground-service start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android WebView native bridge risks: https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- Apple UIKit suspension baseline: https://developer.apple.com/documentation/uikit/about-the-background-execution-sequence
- Repository `AGENTS.md` parity, code, QA and release gates, plus `EF-OPS-KELPIE-RELEASE-POLICY-001`.

**QA-ONLY DEVICE INSTALL VERIFIED / NO MAIN MERGE / NO WORDPRESS / SOURCE+CONSENT_QA_ONLY / RELEASE_NO_GO.**

## Legacy route isolation (mandatory fail-closed QA invariant)

Review exposed a second control path: baseline Kelpie's authenticated `/v1/*` browser handlers and outbound Nessie connectivity predate R5 consent. Merely disabling `AgentOperationRuntime` would **not** disable those old paths; labeling the candidate consent-only while leaving them active would be misleading and could bypass its STOP policy. In the isolated R5 QA profile, the Router therefore denies all legacy browser methods (including `browser-control`, `mcp`, screenshot, navigation, click and nested bypass flags), allowing only `agent-session-request/status/stop` and non-sensitive `get-device-info`; the Nessie outbound agent is not started. The Android native manual WebView UI is not a substitute for this remote gate. A dedicated JVM negative test checks denial while preserving baseline behavior when the QA flag is off.

This is an intentional **consent-only** QA isolation measure, not the future complete one-consent agent runtime. Before enabling browser methods, the single AgentOperationRuntime gateway must enforce authenticated principal, session generation, STOP and lock at all native/MCP/Nessie boundaries, including after-await revalidation.

## 2026-11-16 S24 hardware readback and pending-dialog UX patch

Owner-authorized clean APK on Samsung SM-S928B (Android 17) remains v`0.2.11-r5-consent-qa`, package `com.kelpie.browser.r5sourceverify`, port `18457`, exact installed source SHA `7a900a7303b3d9f8f2fafcd12a458cc5c52b4bf6`. Verified physical Android POST_NOTIFICATIONS permission, G2 native approval/in-app STOP/notification action STOP, G3 lock→SUSPENDED_LOCKED→physical unlock→ACTIVE_CONSENT_ONLY with safetyEpoch 0→2, and force-stop while Active→fresh PID→OFF. Notification action proof: SystemUI `EID_QPNE_NOTI_ACTION_BUTTON` at 17:44:29.021 and native broadcast `R5_AGENT_SESSION_STOP` to unexported `AgentSessionStopReceiver` at 17:44:29.025. Exact evidence: https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6918903136 and https://github.com/imanipour2/enerfoam-website/issues/98#issuecomment-6918958001 .

**Post-install QA source-only improvement:** original pending consent `LOCAL_PROMPT_TTL_MS=300_000` was enforced at approval/readback but could remain visibly stale without a new event. The draft branch adds monotonic `SystemClock.elapsedRealtime()` deadline timing through key-scoped Compose `LaunchedEffect`, prompt-ID-checked `expirePendingIfDue`, StateFlow publication, and a precise expiration notice. Unit tests cover old-timer/new-prompt races; full CI and fresh hardware acceptance required. Official docs used: https://developer.android.com/develop/ui/compose/side-effects (effect lifecycle and cancellation by keys) and https://developer.android.com/reference/android/os/SystemClock (elapsedRealtime includes Doze). Neither source patch nor PR merge is proof of the currently installed APK's behavior.

**Remaining blockers:** 50 actual owner-granted cycles, two verified principals, longer Doze/network handover, notification permission/channel disabled negative on isolated QA profile, iOS parity or separately approved exception, and actual AgentOperationRuntime/owned-WebView DOM/PNG. NO MAIN MERGE / RELEASE_NO_GO / SYNC_BLOCKED_MAIN.
