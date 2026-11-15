# P4 source and security audit — EF-OPS-KELPIE-P4-HARDENING-EXEC-001

Status: **DRAFT / SOURCE-REMEDIATED / NOT VERIFIED / RELEASE_NO_GO** · 2026-11-15.

## Frozen authority and source
- Kelpie `main`: `a884c5c6523fcfb95a5d74ea11aec3c054aec23f`.
- P4 Draft PR #11: `b8c3a351661b58578620e49860514c2571ebda2f`; parent dependencies PR #7 → #9 → #10 → #11. PR #12 is aggregate CI-only.
- Screen-off Draft PR #13: `2bb8dc5fb710b9dcb3118cc84362af8423758d35`; PR #14 is aggregate CI-only.
- This remediation branch descends from #13, not from LOCKED_TASK PR #15/#16. Do not merge research.
- Android QA: `com.kelpie.browser.pcverify` 0.2.9-qa, port 18437; not the original browser.
- Authoritative owner instruction in conversation: EF-OPS-KELPIE-P4-HARDENING-EXEC-001. Canonical CDG update/Drive original pending, not assumed approved by file creation.
- Operational evidence: https://github.com/imanipour2/enerfoam-website/issues/98; approved DOCOPS https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit.

## Findings, all release blockers until separately verified
| ID | Severity | Evidence / root cause | Source status |
|---|---|---|---|
| P4-S01 | P0 | `ConsentedCaptureRequestRegistry.claimFrame()` revoked the owner's grant for foreign requestId/clientId/attempt | Fix candidate, tests written, CI pending |
| P4-S02 | P0 | STOP/status lacked consistent owned-lease semantics; status returned renderer diagnostic data on NOT_FOUND | Fix candidate, independent paired-client device test open |
| P4-S03 | P0 | Legacy Router/BrowserGateway/ConnectBrowser entrypoints were not uniformly gated during private ownership | Shared QA operation policy introduced, nested transport audit/device verification open |
| P4-S04 | P0 | Screen-off previously denied frames but did not proactively terminate display | PR #13 source candidate inherited, physical SCREEN_OFF untested |
| P4-L01 | P0 | FAILED renderer seen in past physical negative test; RCA unconfirmed | Defensive per-resource cleanup candidate; fault injection/recovery open |
| P4-L02 | P1 | 90-second auto-release first failed on older source; corrected at b8c3a351, real no-frame 90s PASS only on old installed APK | Preserve evidence; new branch device retest open |
| P4-R01 | P1 | Pixel sequence increment before frame single-flight acquisition | Candidate fix; hardware freshness retest open |
| P4-R02 | P1 | Real CSS scroll stabilization was physically proven only at 84ed682 | Baseline only; new branch not tested |
| P4-M01 | P1 | `BrowserScreen.kt` has 505 lines at baseline, above AGENTS.md limit 500 | Open: meaningful component extraction required; no arbitrary trimming |
| P4-G01 | P0 | AGENTS.md requires release after every push; mission forbids release, merge, install without approval | Conflict / ADR / OWNER_RESOLUTION_REQUIRED |
| P4-G02 | P1 | AGENTS.md strict Android-iOS parity vs Android-only isolated VirtualDisplay QA | Conflict / ADR / OWNER_RESOLUTION_REQUIRED |

No private-site capture, original application overwrite, user-data export, merge or release authorized. Historical evidence is never a PASS on this remediation branch.
