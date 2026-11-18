# EF-OPS-KELPIE-R5-ONE-TAP-CONSENT-20261118 — Private Android QA

**Status:** SOURCE IMPLEMENTATION / DEVICE UNVERIFIED / NO APK INSTALL / NO MAIN MERGE / PUBLIC RELEASE NO-GO.

## Authority

Owner directive [CDG EF-OPS-KELPIE-R5-BALANCED-CONSENT-20261118](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit), subordinate to [Agent-First](https://github.com/imanipour2/kelpie/pull/56), and mandatory [Kelpie RCA READ FIRST](https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit). Scoped isolated branch from [PR #61](https://github.com/imanipour2/kelpie/pull/61) exact head `18da0dafc0860a49a0f90e24e6c281b1b0114636` to retain 120-second sighting UI refresh. Base R5 actual candidate is [PR #59](https://github.com/imanipour2/kelpie/pull/59) `afb552f022ead7c9e424e2e64fd2765178d4f6ab`, not a claim about installed APK.

## Actual change

- For exactly one freshly bearer-authenticated, eligible, non-localhost principal, native owner taps clearly labeled **Allow control for this session** once. The tap itself covers request + approval in the same UI callback, not an HTTP/MCP call, lifecycle callback, or ChatGPT automatic approval.
- Rechecks exact principal equality after any Android POST_NOTIFICATIONS permission prompt, current 120s observation TTL, native Activity resumed/visible/unlocked, and notification STOP availability. The OS permission prompt, if required, is unavoidable. Reject ambiguous/no principals, stale callbacks, already active/pending consent, or unavailable STOP.
- Process-memory grant remains bound to that principal/generation and normal R5 WebView action/epoch/lock guards. No persistent consent token, no automatic grant on a subsequent app process, no cross-app screen or credential export authority.
- Ordinary tabs, navigation, DOM, clicking, scrolling, typing normal text and Kelpie WebView screenshots require **no repeated native prompt** while that same session remains active. They remain subject to existing R5 operation safety checks. Payment/publishing/deletion/exporting credentials and similar high-impact actions require separate action-level confirmation.
- Native STOP button and Android STOP notification remain available; request failing to post visible STOP revokes provisional grant immediately.
- Shortcut displays truncated verified principal ID and one-tap button. Connection sheet exposes the same path; preserves the existing setup/notification/disclosure text and STOP.
- This PR addresses duplicate user confirmation. It **does not** solve remote ChatGPT connector's stale MCP tool catalog nor principal mismatch; remote E2E test remains a separate requirement.

## Tests and release hold

Unit tests: `R5NativeOneTapConsentPolicyTest` cover one eligible identity, switched/stale identity, lock, Activity state, STOP notification, duplicate active and stale replay. Existing `R5ObservedAgentClientsTest` covers expiry and ambiguity.

CI gates: Kotlin compilation, ktlint, unit tests and private QA artifact/Android build on exact branch HEAD. Then owner-approved physical S24 device QA: single fresh principal one-tap shows native READY and STOP; later ordinary agent actions require zero prompts; duplicate tap/expired, multiple identities, no notification, screen lock, HOME/ChatGPT/background, process death/restart, STOP/replay and 25-cycle hardware test. Validate screenshot and tab epoch/generation after local and remote principal match. Preserve signed, rollback-ready installed QA APK until acceptance.

**Never treat** source edit or GitHub CI as installed Android functionality. No force-stop, app removal, tunable bearer rotation, additional public endpoint, tunnel edit, ChatGPT Plugin change, ADB consent tap, GitHub main merge or WordPress change performed here.

Incident tracker: https://github.com/imanipour2/enerfoam-website/issues/98
