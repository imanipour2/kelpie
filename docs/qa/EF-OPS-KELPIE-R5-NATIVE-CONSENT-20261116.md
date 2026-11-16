# R5 native process-session consent — isolated Android QA candidate

Status: **DRAFT / CONSENT-ONLY / NO WEBVIEW ACTIONS / NO DEVICE INSTALL / NO RELEASE / PARITY_OPEN**  
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

**NO INSTALL / NO MAIN MERGE / NO WORDPRESS / SOURCE+CONSENT_QA_ONLY / RELEASE_NO_GO.**
