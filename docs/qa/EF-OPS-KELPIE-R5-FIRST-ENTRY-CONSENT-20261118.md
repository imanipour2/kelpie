# R5 first-entry background-consent gate — Android private QA
Status: DRAFT_SOURCE / CI_AND_S24_PHYSICAL_QA_REQUIRED / NO_PUBLIC_RELEASE
Decision: EF-OPS-KELPIE-AGENT-FIRST-STARTUP-20261118 and 2026-11-18 owner reaffirmation in canonical CDG
Branch: fix/r5-first-entry-background-gate-20261118 (based on PR58 exact-head c235dcd9)
Reference: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit

## Root cause and boundaries
The prior v0.2.15 owner beta was built from PR58 and lacked the initial entry gate from separate PR56. PR58 also hid the in-app Connect shortcut unless exactly one fresh bearer-verified principal was observed; a clean uninstall erased app-private pairing records. The owner saw neither consent dialog nor Connect, despite successful version install and occasional metadata-only MCP capabilities.

This isolated v0.2.16 patch adds a *pre-construction* owner choice in the standalone Android Activity, only on the private beta port 18461. It deliberately does **not** claim to merge PR56 or PR57, nor to create an authenticated agent principal from an unpaired localhost connection.

## Exact behavior
- On first Activity creation: native non-dismissable YES/NO dialog. Before YES, there is no KelpieBrowserSession, local HTTP listener, active renderer, or Kelpie FGS created by this Activity.
- YES: allow the creation of the private QA session. Native one-tap agent session remains separately bound to a recently bearer-verified principal and a real on-phone approval. Android POST_NOTIFICATIONS, keyguard, STOP and sensitive-action gates remain intact.
- NO: "Bye bye" toast; revoke process agent session, clear observed clients, stop Kelpie-owned notification/network service/stage and finish task; never touch independent Termux.
- Same Activity HOME/ChatGPT switch does not repeat the gate; a new Activity/session entry does. No durable background or agent approval is saved.
- A visible "Pair / Connect" entry remains in the QA browser even with zero verified agents; it opens truthful setup instructions, NOT a fake approval.
- A positive answer does not override Android background execution/FGS/Doze restrictions; neither does it authorize remote control without principal pairing.
- Existing non-R5 / embedded consumers remain unchanged; Android-only QA feature does not satisfy production Android/iOS parity.

## Required test matrix
1. Clean install: dialog displays before local 18461 server begins; no OS-level popup is misrepresented as native consent.
2. NO: app closes; no Kelpie-owned FGS/listener; no unapproved tab read, and Termux 18080 remains independent.
3. YES no pairing: browser opens; Pair / Connect remains visible; no privileged session can start; expired/localhost/multiple clients do not auto-grant.
4. YES valid unique authenticated client: Connect requests separate native approval; deliberate physical Approve + notification => session READY; STOP revokes.
5. Background HOME / ChatGPT switch, 25 tab/screenshot/DOM/scroll/click cycles, lock/unlock, notification denial, app kill/restart, reboot, Doze, network handoff.
6. ADB install signature compatibility, exact-head provenance, rollback and physical Samsung SM-S928B evidence.

## References
- Android FGS: https://developer.android.com/develop/background-work/services/fgs/launch
- Android 13+ notification consent: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android native QA source and PR56/57/58 (GitHub).
