# R5 QA: focus callback closes owner-unlock restoration race

Date: 2026-11-17. Isolated private QA build only. Stacked on PR47 -> PR46 -> PR45 -> PR44 -> PR42.

## Evidence and classification

**Observed on owner SM-S928B Android 17 (previous installed PR46 APK):** physical keyguard unlock left the previously native-approved R5 process in `ACTIVE_AGENT_SESSION`, with `SUSPENDED_OR_TRANSFER / SUSPENDED / IDLE`; Kelpie MainActivity was top-resumed and focused, but the session did not become READY. The independent PR47 fix adds an active-state-flow trigger after the lock bit changes and an onResume fallback.

**Source-level race (not independently reproduced yet):** the PR47 rebind gate correctly requires `activity.hasWindowFocus()` before giving the original owner back their exact WebView. Android does not guarantee ordering between `onResume`, `ACTION_USER_PRESENT`, and window focus. If both first triggers occur *before* the app regains focus, the gate returns without scheduling a retry. If focus later changes without a new consent-flow emission, the original session stays suspended even though the owner unlocked.

## Bounded correction

Register `ViewTreeObserver.OnWindowFocusChangeListener` on the native Activity decor view at R5-coordinator initialization; remove it at coordinator close if the observer remains alive. **Only when focus is gained**, owner status remains SUSPENDED, and the same process-consent snapshot is Active and unlocked, enqueue the *existing* `rebindAfterRealOwnerUnlock` method. That method performs the full transfer-mutex protected same-generation, original-tab/WebView identity, focused/visible/resumed/attached/shown, interactive/unlocked Android keyguard and STOP-notification safety checks; no new grant is created by focus. The existing denial path remains fail closed. Repeated focus events while ready or after stop do nothing.

Pure JVM regression tests cover focus false -> true and denial despite regained focus when consent is OFF, generation changed, keyguard present or owner is PRIVATE; these assert policy only, **not** hardware lifecycle callback delivery.

## Evidence still required

1. GitHub PR head CI lint, Kotlin compile, all QA suites. No merge while review open.
2. Exact-head APK built and signed with the *same* debug key as installed owner QA; SHA and both installed Android profiles verified, no uninstall and no data wipe.
3. Native owner approves through real Android UI. Test (a) lock while Kelpie focused -> unlock, (b) keyguard -> transition to another app -> return Kelpie, (c) unlock while a notification temporarily steals focus -> focus returned after USER_PRESENT, (d) STOP before late focus callback, (e) new generation/revocation. Assert READY only for a genuine owner-approved same-WebView session and never while locked, hidden or background.
4. Verify DOM / PNG from paired ChatGPT control only after new owner-approved grant. Do not simulate local consent.
5. Preserve prior PR47 signed APK for rollback; record log classes `KelpieR5Unlock` without URLs, content, tokens or private data.

## Official references

- Android Activity.onWindowFocusChanged: https://developer.android.com/reference/android/app/Activity#onWindowFocusChanged(boolean) — focus and lifecycle ordering is independent.
- Android ViewTreeObserver.OnWindowFocusChangeListener: https://developer.android.com/reference/android/view/ViewTreeObserver.OnWindowFocusChangeListener — explicit focus-gained event.
- Android ViewTreeObserver: https://developer.android.com/reference/android/view/ViewTreeObserver — add/remove focus listener and isAlive safety.
- Android ACTION_USER_PRESENT: https://developer.android.com/reference/android/content/Intent#ACTION_USER_PRESENT — system keyguard event, not agent authorization.

## Operational handoff — no owner laptop

Owner temporarily away from the Windows/USB host. Direct ChatGPT `kelpie_browser_control.capabilities` still returns a valid response through phone/Termux independently. Existing native session is OFF, and the currently exposed installed ChatGPT plugin tools do **not** expose `kelpie_agent_session` (despite the R5 MCP server catalog advertising it), so new owner approval cannot be initiated through available ChatGPT controls. Dynamic extension listing was also observed FORBIDDEN. This is a **provider-tool exposure / onboarding follow-up**, not proof that the Termux tunnel failed. Do not infer remote Android shell or ADB connectivity from a capabilities reply.

Pending: restore USB when owner reaches host; independent native click/history RCA; incoming-call and Doze endurance; security review. GitHub main, website LIVE/STAGING and public beta unchanged. `SYNC_BLOCKED_MAIN / PUBLIC_BETA_NO_GO`.

Operational issue: https://github.com/imanipour2/enerfoam-website/issues/98
