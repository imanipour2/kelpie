# R5 observed-agent readiness label expiration (2026-11-18)

**Scope:** Native Android QA only, branch `fix/r5-expired-agent-banner-20261118` based on R5 PR #59 head `afb552f022ead7c9e424e2e64fd2765178d4f6ab`. **Not deployed to phone.** Browser/Termux tunnels, credentials, and owner consent rules are unchanged.

## Owner screenshot and root cause

At approximately 11:40 +03:30, the S24 showed "Verified AI agent ready" / "Connect"; tapping the shortcut opened a sheet showing "Native session: OFF" and "No authenticated paired AI client is active" while the banner behind reverted to "AI agent not yet paired".

`R5ObservedAgentClients.registry.observed` is a StateFlow of a **list**, not a time-expiring observable. The registry stores a 120-second monotonic sighting time. `R5AgentConnectShortcut` displayed the list without advancing time; on tap `AgentSessionCoordinator.requestFromNativeUi()` called `soleRecentPrincipal()` which expires the sighting, safely rejects a stale principal, and falls back to opening `AgentConnectionCenterSheet`. The previous button label can therefore be stale. The exact time of the last authenticated capabilities request is not independently proven from these two screenshots alone; the mechanism is code-confirmed and fits the observed sequence.

This is a **UI freshness bug**, not proof that Ed25519 computer pairing was removed. The safety admission check correctly failed closed. The separate question of why a prior ACTIVE native session became OFF remains unresolved.

## Minimal change

- Add `refreshExpired()` to `RecentVerifiedAgentClients`: synchronized monotonic cleanup and StateFlow publication only.
- Add one centralized Compose `R5ObservedAgentExpiryEffect` called by both visible shortcut and connection sheet. It polls at 1-second intervals **only while a nonempty recent agent list is being displayed**. It stops on disposal or when the list empties. Re-observation of the same principal can extend TTL without changing the list, and the periodic tick still honors that extension.
- Add regression tests for passive expiry and refreshed timestamp. Do not change bearer authorization, TTL, owner Approve, STOP, lock semantics, session generation, or renderer gates.
- No timer in background services, no auto-pairing, no auto-grant, no forced activity wake-up.

## Verification gates

1. Kotlin compilation, ktlint and `R5ObservedAgentClientsTest` in Android CI: PENDING.
2. Physical S24 with exact signed QA APK and owner consent: verify "Verified AI agent ready" automatically becomes "AI agent not yet paired" after 120s without tapping; re-authenticated capabilities while same process running can restore the label without pairing again; click within TTL must open the native owner approval prompt for one eligible principal only.
3. Confirm separate owner approval and STOP, identity mismatch fail closed, lock/unlock, app background and process death. Do not conflate client sighting TTL with native session expiry.
4. E2E ChatGPT native session and real tabs/snapshot/screenshot acceptance remains a separate gate; this UI correction does not fix connector tool schema or tunneled identity.

**Canonical operational RCA:** https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit

**Incident:** https://github.com/imanipour2/enerfoam-website/issues/98

**Status:** SOURCE_FIX_ONLY / OWNER_DEVICE_QA_PENDING / E2E_NO_GO / SYNC_BLOCKED_MAIN / PUBLIC_BETA_NO_GO.
