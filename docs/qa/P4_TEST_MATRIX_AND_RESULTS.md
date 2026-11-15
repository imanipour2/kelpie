# P4 test matrix and results (source-specific)

**Baseline hardware:** SM-S928B Android 17 isolated QA, prior build only. **New branch:** fix/p4-authority-lifecycle-hardening-20261115, **no installed APK verified**.

| Scenario | Historical source/build | Candidate source status |
|---|---|---|
| 20/20 fresh nonce PNGs on HOME | VERIFIED at `84ed682` | NOT TESTED |
| Same activity/tab/document/form/history/CSS scroll 11/11 | VERIFIED at `84ed682` | NOT TESTED |
| Zero-frame 90s automatic release before status call | VERIFIED at `b8c3a351` | NOT TESTED |
| Wrong JSON clientId / malformed request rejection | VERIFIED locally on `b8c3a351` | NOT TESTED |
| Two genuinely independent paired principals | NOT TESTED | NOT TESTED |
| Physical notification STOP tap | NOT TESTED | NOT TESTED |
| Screen-off revoke + OS display/notification release | PR #13 SOURCE-ONLY | NOT TESTED |
| FAILED renderer clean self-recovery | Prior FAILED observed; RCA OPEN | SOURCE-ONLY |
| Wrong client/request isolation regression | Historical source vulnerable on inspection | SOURCE-ONLY / JUnit ADDED |
| Distinct attempt and stale replay isolation | Historical source vulnerable on inspection | SOURCE-ONLY / JUnit ADDED |
| Unified HTTP/MCP/Nessie operation gate | Not comprehensively tested | SOURCE-ONLY / JUnit ADDED |
| Concurrent frame / sequence isolation | Missing source guard | SOURCE-ONLY / device NOT TESTED |
| Native stop idempotency / cleanup fault injection | NOT TESTED | NOT TESTED |
| 50-cycle leak profiling and 16KB/API36 compatibility | NOT TESTED | NOT TESTED |

No new CI status is asserted in this document until an exact-branch workflow run is inspected. Tests must record commit SHA, APK SHA256, signer, installed package, Android API, authentic consent and evidence timing.

**Required further work:** Android JVM unit tests, Kotlin formatting, Lint, Android build, emulator integration; fault-injection for each create/dismiss/reparent/restore failure; 20-frame/device negative tests; 50-cycle heap/native memory/thread/surface trends; rollback rehearsal.
