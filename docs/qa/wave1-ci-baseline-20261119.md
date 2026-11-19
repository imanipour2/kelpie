# Wave 1 CI baseline and isolated formatting readback

Mission: **EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001**. Repository: [imanipour2/kelpie](https://github.com/imanipour2/kelpie).

## Scope and immutable revisions

- Original [Draft PR #71](https://github.com/imanipour2/kelpie/pull/71) HEAD: `68b7c2ccdf6a9e45d8198f07613c635b6d3e2367`.
- PR #71 base is [PR #70](https://github.com/imanipour2/kelpie/pull/70) at `fc25806dd9940ef13fc7a3b06b77c96a3977b6c9`; #70 depends on [PR #66](https://github.com/imanipour2/kelpie/pull/66) at `68ade958af66b7f6de219abd2d9f9011478f5a40`.
- [PR #69](https://github.com/imanipour2/kelpie/pull/69), `f52acd0334a62bcb4716c9c82b1b3a687a151f19`, was separately read as security/observation integration context, not an installed APK.
- Isolated formatting correction: [`37b9ca372c0a3e81e518e8d2ac0185283c15d3f3`](https://github.com/imanipour2/kelpie/commit/37b9ca372c0a3e81e518e8d2ac0185283c15d3f3).
- Completed readback independently retrieved at **2026-11-19 15:13:28 UTC** using the public GitHub Actions API for that exact commit.

Read before triage: `AGENTS.md`, `CLAUDE.md`, `core/README.md`. The referenced
`docs/policies/development-and-release.md` did not exist at the selected branch
(404); no contents or authorization were invented. The owner's mission forbids
installation/deployment until its separate gates pass, regardless of normal release guidance.

## All 13 original workflow runs

Result: **9 failure / 3 success / 1 existing conditional skip**. Each failed job
was fetched independently. Seven QA Android jobs checked the exact PR head;
the generic Android workflow used GitHub's PR merge checkout. Both are recorded
against the original head, without treating the checkout forms as identical.

| Workflow file | Run | Job | Run / job conclusion | Finding |
|---|---|---|---|---|
| `qa-r5-mother-deploy-script.yml` | [44541593948](https://github.com/imanipour2/kelpie/actions/runs/44541593948) | [135621063716](https://github.com/imanipour2/kelpie/actions/runs/44541593948/job/135621063716) | success / success | Passed its own scope; no phone acceptance |
| `qa-r5-ktlint-autofix.yml` | [44541594125](https://github.com/imanipour2/kelpie/actions/runs/44541594125) | [135621066471](https://github.com/imanipour2/kelpie/actions/runs/44541594125/job/135621066471) | skipped / skipped | Existing branch guard; not a disabled test |
| `lint.yml` | [44541594152](https://github.com/imanipour2/kelpie/actions/runs/44541594152) | [135621063810](https://github.com/imanipour2/kelpie/actions/runs/44541594152/job/135621063810) | success / success | Passed its own scope; no phone acceptance |
| `qa-capture-format-patch.yml` | [44541594236](https://github.com/imanipour2/kelpie/actions/runs/44541594236) | [135621234217](https://github.com/imanipour2/kelpie/actions/runs/44541594236/job/135621234217) | success / success | Passed its own scope; no phone acceptance |
| `qa-p4-parallel-slot.yml` | [44541593897](https://github.com/imanipour2/kelpie/actions/runs/44541593897) | [135621249264](https://github.com/imanipour2/kelpie/actions/runs/44541593897/job/135621249264) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-r5-upstream-connection.yml` | [44541594175](https://github.com/imanipour2/kelpie/actions/runs/44541594175) | [135621062094](https://github.com/imanipour2/kelpie/actions/runs/44541594175/job/135621062094) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-android-private-capture.yml` | [44541593828](https://github.com/imanipour2/kelpie/actions/runs/44541593828) | [135621227710](https://github.com/imanipour2/kelpie/actions/runs/44541593828/job/135621227710) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-r5-source-slot.yml` | [44541593734](https://github.com/imanipour2/kelpie/actions/runs/44541593734) | [135621100696](https://github.com/imanipour2/kelpie/actions/runs/44541593734/job/135621100696) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-r5-private-beta.yml` | [44541594291](https://github.com/imanipour2/kelpie/actions/runs/44541594291) | [135621185542](https://github.com/imanipour2/kelpie/actions/runs/44541594291/job/135621185542) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-r5-background-agent.yml` | [44541594226](https://github.com/imanipour2/kelpie/actions/runs/44541594226) | [135621245284](https://github.com/imanipour2/kelpie/actions/runs/44541594226/job/135621245284) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `qa-r5-mother-sidecar.yml` | [44541593890](https://github.com/imanipour2/kelpie/actions/runs/44541593890) | [135621062286](https://github.com/imanipour2/kelpie/actions/runs/44541593890/job/135621062286) | failure / failure | Main and test-source ktlint; compilation/tests blocked |
| `macos.yml` | [44541594079](https://github.com/imanipour2/kelpie/actions/runs/44541594079) | [135621073608](https://github.com/imanipour2/kelpie/actions/runs/44541594079/job/135621073608) | failure / cancelled | Hosted runner never acquired; no steps executed |
| `android.yml` | [44541593757](https://github.com/imanipour2/kelpie/actions/runs/44541593757) | [135621243270](https://github.com/imanipour2/kelpie/actions/runs/44541593757/job/135621243270) | failure / failure | Main-source ktlint; compile ran, test pass not established |

### Independently observed Android failure excerpts

The following are direct task-failure excerpts from each job's own decoded log,
not a root cause copied from another workflow. The seven QA jobs each also showed
4 main-source and 17 test-source ktlint findings; generic Android showed the
same 4 main-source findings. A formatting gate failure is not proof of a later
compiler, unit-test, packaging or device failure.

#### QA P4 Parallel Slot Android (No Install) — job 135621249264

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541593897/job/135621249264)

```text
2026-11-19T14:16:13.2312703Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
2026-11-19T14:16:13.2318644Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
```

#### QA R5 Upstream Connection Baseline (No Auto Install) — job 135621062094

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541594175/job/135621062094)

```text
2026-11-19T14:17:14.6716106Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
2026-11-19T14:17:14.6724634Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
```

#### Android S24 private capture consent QA — job 135621227710

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541593828/job/135621227710)

```text
2026-11-19T14:17:20.9418076Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
2026-11-19T14:17:20.9437204Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
```

#### QA R5 Native Consent-only Android (No Install) — job 135621100696

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541593734/job/135621100696)

```text
2026-11-19T14:16:20.4090033Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
2026-11-19T14:16:20.4101915Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
```

#### QA R5 Private Beta Candidate Android (No Auto Install) — job 135621185542

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541594291/job/135621185542)

```text
2026-11-19T14:16:43.6675329Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
2026-11-19T14:16:43.6689678Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
```

#### QA R5 Background Native Agent Android (No Install) — job 135621245284

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541594226/job/135621245284)

```text
2026-11-19T14:16:42.9626267Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
2026-11-19T14:16:42.9642726Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
```

#### QA R5 Mother Sidecar Android (Signer-safe, No Auto Install) — job 135621062286

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541593890/job/135621062286)

```text
2026-11-19T14:17:42.3528907Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
2026-11-19T14:17:42.3535285Z Execution failed for task ':kelpie-browser:ktlintTestSourceSetCheck'.
```

#### Android — job 135621243270

[Run and full job log](https://github.com/imanipour2/kelpie/actions/runs/44541593757/job/135621243270)

```text
2026-11-19T14:21:24.1673191Z Execution failed for task ':kelpie-browser:ktlintMainSourceSetCheck'.
```

### Four actual files and corrections

| File | Original diagnostic | Fix in 37b9ca3 |
|---|---|---|
| `core/android/src/main/java/com/kelpie/browser/network/R5AgentControlPlaneRoutes.kt` | Line 21:17, `standard:function-signature`; body expression fits beside the signature. | Use the formatter's expression-body placement. |
| `core/android/src/main/java/com/kelpie/browser/ui/R5TermuxOwnerReconnect.kt` | Lines 101, 120, 125, `standard:indent`; continuation indentation 24→20 and 36→32. | Repair continuation indentation, retaining behavior and messages. |
| `core/android/src/test/java/com/kelpie/browser/network/R5AgentControlPlaneRoutesTest.kt` | Lines 15–25, `standard:indent`; loop-body indentation was 4 columns too deep. | Format the same test assertions and loop. |
| `core/android/src/test/java/com/kelpie/browser/ui/R5TermuxOwnerReconnectTest.kt` | Lines 32–37, `standard:multiline-expression-wrapping` and `standard:wrapping`. | Format multiline assertions, preserving every case. |

Representative exact lint details from job 135621249264:

```text
2026-11-19T14:16:13.1315258Z /home/runner/work/kelpie/kelpie/core/android/src/main/java/com/kelpie/browser/network/R5AgentControlPlaneRoutes.kt:21:17 First line of body expression fits on same line as function signature (standard:function-signature)
2026-11-19T14:16:13.1317141Z /home/runner/work/kelpie/kelpie/core/android/src/main/java/com/kelpie/browser/ui/R5TermuxOwnerReconnect.kt:101:1 Unexpected indentation (24) (should be 20) (standard:indent)
2026-11-19T14:16:13.2284697Z /home/runner/work/kelpie/kelpie/core/android/src/test/java/com/kelpie/browser/network/R5AgentControlPlaneRoutesTest.kt:15:1 Unexpected indentation (12) (should be 8) (standard:indent)
2026-11-19T14:16:13.2299190Z /home/runner/work/kelpie/kelpie/core/android/src/test/java/com/kelpie/browser/ui/R5TermuxOwnerReconnectTest.kt:32:20 A multiline expression should start on a new line (standard:multiline-expression-wrapping)
```

### macOS infrastructure failure

Run [44541594079](https://github.com/imanipour2/kelpie/actions/runs/44541594079)
was a failure; job [135621073608](https://github.com/imanipour2/kelpie/actions/runs/44541594079/job/135621073608)
was **cancelled with no steps**. The job-log endpoint returned `BlobNotFound`;
that missing log is not a compiler diagnostic. The independent
[check-run annotations](https://api.github.com/repos/imanipour2/kelpie/check-runs/135621073608/annotations)
reported:

> The job was not acquired by Runner of type hosted even after multiple attempts

Additional runner notices identified macOS arm64 capacity constraints. No source
or test was changed to conceal the infrastructure failure. The same workflow
subsequently completed on the isolated formatting commit; see below.

### Existing formatter skip

`qa-r5-ktlint-autofix.yml` grants write permission only when the PR head belongs
to this repository **and** its branch is
`feat/r5-background-agent-p4-runtime-20261116`. PR #71 uses
`fix/r5-mother-atomic-webview-switch-20261119`, so that pre-existing automation
is skipped by design. Its restriction was not removed. Actual ktlint check/test
workflows still ran, first failing and then passing after formatting was fixed.

## Exact formatting-commit readback

The following API results all have head SHA
`37b9ca372c0a3e81e518e8d2ac0185283c15d3f3` and status `completed`:
**12 success / 1 conditional formatter skip / 0 failure**.

| Workflow | Run | Conclusion |
|---|---|---|
| QA R5 Mother Sidecar Android (Signer-safe, No Auto Install) | [44548620304](https://github.com/imanipour2/kelpie/actions/runs/44548620304) | success |
| QA R5 Background Native Agent Android (No Install) | [44548620457](https://github.com/imanipour2/kelpie/actions/runs/44548620457) | success |
| Format isolated R5 Android Kotlin source | [44548619814](https://github.com/imanipour2/kelpie/actions/runs/44548619814) | skipped |
| QA S24 Mother Deployment Script (No Device / No Install) | [44548619951](https://github.com/imanipour2/kelpie/actions/runs/44548619951) | success |
| Lint | [44548620194](https://github.com/imanipour2/kelpie/actions/runs/44548620194) | success |
| Generate Kotlin formatting patch for private capture review | [44548619876](https://github.com/imanipour2/kelpie/actions/runs/44548619876) | success |
| macOS | [44548619987](https://github.com/imanipour2/kelpie/actions/runs/44548619987) | success |
| QA R5 Upstream Connection Baseline (No Auto Install) | [44548619935](https://github.com/imanipour2/kelpie/actions/runs/44548619935) | success |
| Android S24 private capture consent QA | [44548619863](https://github.com/imanipour2/kelpie/actions/runs/44548619863) | success |
| QA R5 Native Consent-only Android (No Install) | [44548619958](https://github.com/imanipour2/kelpie/actions/runs/44548619958) | success |
| QA P4 Parallel Slot Android (No Install) | [44548620204](https://github.com/imanipour2/kelpie/actions/runs/44548620204) | success |
| QA R5 Private Beta Candidate Android (No Auto Install) | [44548620277](https://github.com/imanipour2/kelpie/actions/runs/44548620277) | success |
| Android | [44548619826](https://github.com/imanipour2/kelpie/actions/runs/44548619826) | success |

The macOS retry/new-head run passed without test changes. All eight Android
workflows cleared their original formatting failures. This table does **not**
claim that the later, uncommitted Wave 1 functional changes have passed final CI.
Those require their own commit, test run and exact artifact readback. CI jobs also
do not establish which APK is installed on the physical S24 Ultra.

## Owner-side candidate guard verification

New source test: `scripts/qa/tests/test_mothernext_gates.ps1`. It executes the
actual `kelpie-s24-mothernext-18463.ps1` against fake SDK/ADB/GitHub boundaries.
The test invokes no actual phone, APK installer, network connection, credential
or tunnel. No production acceptance record is generated from fixture results.

Local command (repository root):

```powershell
/tmp/kelpie-pwsh/pwsh -NoLogo -NoProfile -File scripts/qa/tests/test_mothernext_gates.ps1
```

Result: **39 / 39 PASS**, exit 0. Local log:
`/tmp/kelpie-mothernext-guard-tests.log`. Its final line is:

```text
PASS: 39 real-script negative/positive gate scenarios; all Android boundaries mocked.
```

Coverage includes a genuine read-only path, authorized same-package mock update
and readback; missing/failed cold restore despite backup checkbox; independent
owner flag; local rollback bytes; signer mismatch; no parallel-package fallback;
source and artifact mismatch; corrupted backup/test records; missing physical
capture gate; wrong/stale device evidence; failed, improperly skipped or missing
CI; wrong candidate run attempt; changed installed bytes before write; signer
inspection without readiness; failed install without an automatic rollback;
fresh user-0 single-package/Termux inventory, free storage, physical cycle
thresholds (20 switches/3 tabs, 25 frames, 50 displays), exact transport ports,
fresh authenticated transport evidence and degraded 200/503 probe classification.

The unchanged legacy and revised MotherNext scripts passed the deployment
workflow's PowerShell parser/static guard block, executed locally with:

```powershell
/tmp/kelpie-pwsh/pwsh -NoLogo -NoProfile -File /tmp/kelpie-mother-gate-static.ps1
```

Log: `/tmp/kelpie-mothernext-static-gates.log`, exit 0:

```text
PASS: original deployment and 18463 signer/backup gates parse correctly.
```

Both edited workflow YAML files parsed successfully; extracted provenance shell
passed `bash -n /tmp/kelpie-wave1-provenance.sh`. The Windows workflow retains
its earlier static checks and adds the same behavioral script; its functional
commit CI result remains a separate gate.

The local PowerShell runtime was the official 7.5.4 Linux x64 artifact, verified
against its official release checksum before execution:
`1fd7983fe56ca9e6233f126925edb24bf6b6b33e356b69996d925c4db94e2fef`.
Ephemeral local log paths above are trace aids; this checked-in record and linked
CI jobs preserve the relevant command, scope and outcome.

## Installation boundary

The target package, phone port 18463, installed app/data, pairing and Termux
tunnel are independent of these CI results. No APK was installed or pulled by
this triage or mock test. Source version 41 and its candidate manifest cannot
prove signer compatibility, cold restoration, rollback or physical behavior.
The root mission report must separately evidence those gates and the actual
installed APK. A missing gate remains **BETA_INSTALL_NO_GO**.


## Functional-test regression discovered after the formatting baseline

The first local full functional run executed 487 JVM tests with one failure in
`AccountPairingTests.r5QaModernMcpHeadersWorkOnlyOnTheScopedPrivateEndpoint`,
XML at `core/android/build/test-results/testDebugUnitTest/TEST-com.kelpie.browser.network.AccountPairingTests.xml`.
The stack identified line 163: a valid `Authenticated` result was incorrectly
returned where the existing test required rejection. The new wire-profile flag
had unintentionally widened private-protocol versions from `/mcp` to `/v1/mcp`.
The implementation was corrected to keep private versions scoped to the existing
`/mcp` endpoint, while mother retains canonical versions on both aliases. The
original assertion was retained; the new mother/private matrix now explicitly
covers that scope. The follow-up local `/tmp/kelpie-android-tests-final.log`
completed successfully at 15:17:45 UTC: 488 JVM tests, zero failures/errors/skips,
with both source and instrumentation compilation. Its AccountPairingTests XML
contains 7 passing cases; dispatcher XML contains 10 passing cases. This was an
intermediate source snapshot, before the later queued-operation guard and native
pairing approval tests were added. It is not the final Wave 1 candidate result;
those newer changes require their own full rerun and CI.
