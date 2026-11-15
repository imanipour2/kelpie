# P4 release readiness and rollback

**Verdict as of 2026-11-15: RELEASE_NO_GO / QA_PARTIAL_PASS (historical evidence only) / SYNC_BLOCKED.** The new hardening branch is not merged, released, installed, or hardware accepted.

| Gate | Condition | Status |
|---|---|---|
| R0 Baseline | authority, main, stacked drafts, hardware evidence inventory | PARTIAL: conflicts identified |
| R1 Ownership | non-owner cannot revoke/STOP, gate across transports | SOURCE REMEDIATED / CI + independent transport TEST OPEN |
| R2 Lifecycle | STOP, TTL, SCREEN_OFF, fault recovery, native resources | SOURCE REMEDIATED PARTIAL / HARDWARE OPEN |
| R3 Rendering | single flight, nonce proof, true restoration | SOURCE REMEDIATED PARTIAL / HARDWARE OPEN |
| R4 Device | isolated rebuilt APK, physical consent and all negative tests | NOT EXECUTED |
| R5 Release review | security audit, parity, signing, API36, 16KB, risk assessment | NOT EXECUTED |

**Rollback:** retain original Android packages and data; no automatic install. Current candidate branch can be discarded/reverted to pre-hardening `2bb8dc5fb710b9dcb3118cc84362af8423758d35` without altering `main`. Stacked P4 baseline is `b8c3a351`; prior healthy screenshot functionality remains independent. Do not overwrite APK signatures or original package IDs.

**Critical unresolved risks:** full shared operation-gate reachability, UI/Gateway/Nessie races, STOP/expiry during encode, native resource failure, CLOSED/FAILED recovery, screen-off physical behavior, battery/Doze, 2-principal real pairing, dependency vulnerabilities, Kotlin file length/style, Apple parity and repo's mandatory-release-on-push policy. Resolve formal CDG exceptions before merge.

**Sources for final verification:** Android SDK API 36, 16KB pages https://developer.android.com/guide/practices/page-sizes ; signing https://developer.android.com/studio/publish/app-signing ; OWASP MASVS https://mas.owasp.org/MASVS/ ; Android restrictions https://developer.android.com/develop/background-work/background-tasks/overview ; GitHub CI run logs for exact SHA.

**Site status:** ENERFOAM LIVE/STAGING/TEST = UNCHANGED BY THIS MISSION; no WordPress/Elementor/WooCommerce actions.
