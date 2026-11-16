# Development, QA, merge and release policy

**Scope:** `imanipour2/kelpie` fork only.  
**Decision:** `EF-OPS-KELPIE-RELEASE-POLICY-001`, owner-approved 2026-11-16.  
**Canonical approval:** [ENERFOAM CDG](https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit).  
**Operational tracking:** [ENERFOAM issue #98](https://github.com/imanipour2/enerfoam-website/issues/98).  
**Authority:** This document implements the scoped owner decision. `AGENTS.md` is the repository's read-first entrypoint; `CLAUDE.md` references the same policy. The upstream UnlikeOtherAI repository is not changed.

## Intent and explicit supersession

Separate **saving code**, **reviewing code**, **integrating code**, and **publishing software**. This replaces **only** the former requirement that *every pushed fix must immediately run a full release/publish/install cycle* in this fork. Pushing a development commit, opening a PR, and merging approved changes do **not** themselves authorize publication, installation on a user's phone, production signing, npm publishing or deployment.

The separate `Platform Parity (CRITICAL)` rule in `AGENTS.md` remains in force. In particular, Android-only Agent-Control Runtime code requires an independently approved, scoped parity decision before merging into `main`. This release-policy decision is **not** that exception.

## Gates and permitted actions

| Phase | Permitted | Required checks | Prohibited or not implied |
| --- | --- | --- | --- |
| Local development | Small focused commits, builds, isolated test APKs | Source provenance, scoped tests, no committed generated files/secrets | No implicit release or installation |
| Development/QA branches (`feat/*`, `fix/*`, `qa/*`, `docs/*`, `policy/*`) | Push, pull request, CI, review artifacts | PR scope, test evidence, license/security review as applicable | No version bump or public release solely for a push; no automatic user-device install |
| `release/*` preparation | Release candidates and internal validation | Target component, version plan, rollback, CI, signing plan | No public release merely by pushing a release branch |
| Merge to `main` | Merge reviewed and approved PR | Relevant CI, review, platform-parity compliance or **separate approved** exception, docs updated, risk gates | Merge is not a release. Direct pushes to `main` are not normal practice |
| Public release | Version bump, signed/published components, GitHub Release, install/verification | **Explicit release authorization** specifying commit, components, QA evidence, sign-off and rollback | A debug APK or PR artifact is not a production release |

**Documentation/policy PR exception:** This policy's own `policy/*`-branch push and PR are expressly authorized by the decision without invoking the old release-after-push cycle. Its documentation-only merge may proceed after applicable review/CI, with no release of binaries or npm packages. This exception is not a blanket permission for Android feature merges.

### Main-branch controls

1. Use a PR into `main` with at least a documented human/owner review for governance changes and the applicable automated checks. For security-sensitive runtime changes, record threat-model review and hardware acceptance as appropriate. Never treat a green lint check as sufficient proof of device behavior.
2. Do not push directly to `main` except a separately authorized, documented emergency with a follow-up PR/audit.
3. A PR that touches `site/**`, publishing workflows, release tags, npm publication or credential/signing automation requires **independent deployment review before merging**. The existing `.github/workflows/pages.yml` deploys on `main` changes to `site/**`; it must not be misrepresented as harmless code-only merge.
4. Do not lower the security/signing requirements to satisfy the new development workflow.
5. GitHub branch protection/rulesets and environment permissions should enforce these expectations where supported. **Text instructions alone are not technical enforcement.** If protections cannot be inspected or set through the available integration, report an enforcement gap rather than claiming protection is active.

### Release authorization and procedure

A release starts only with a recorded `RELEASE_APPROVAL` that identifies:
- precise Git commit or reviewed release candidate, affected component(s) and version(s);
- successful relevant CI, security and platform checks, any scoped parity exception, required device acceptance and known limitations;
- Android production signing requirements (all four configured secrets and verified APK/AAB signatures, never an unsigned fallback), provenance, published artifact checksums and rollback;
- responsible release owner and installation/verification target.

After the approval: bump **only** the components being released; create a reviewed date-tagged GitHub Release (`release/YYYY-MM-DD`) with the component/version manifest; publish the affected artifacts, including npm only when the CLI/shared package is in scope; install the **published** artifact on its required target and verify that running installation. On macOS-app releases, preserve the existing signed/notarized build and local installed-app verification requirements. A dry-run candidate, CI artifact, unsigned Windows candidate or Android debug APK is not an approved release.

The existing `.github/workflows/release.yml` and `release-windows.yml` start on `release: published`, **not** an ordinary branch push. `lint.yml`, `android.yml` and other CI workflows may start on PRs or `main` pushes; their successful completion is not publication approval. This document does not modify the workflows themselves.

## ENERFOAM-specific boundaries

- Agent-Control Runtime development may continue on reviewable `feat/*` / `qa/*` branches with exact source/APK SHA evidence, separately identified QA application ID and rollback. Production/original Kelpie apps and `test.enerfoam.com` / live WordPress remain untouched without their own approval.
- The P4 draft PR #17 and R5 local Kotlin builds are **not** merged or published by adopting this policy.
- The user-approved goal of one local consent for an active Kelpie browser session does not waive Android permission rules, verified-client authorization, STOP controls, lockscreen isolation or separate approval for sensitive actions.
- The Android/iOS parity question is a **different governance decision** and must be resolved before merging Android-only functional changes, irrespective of this policy.

## Evidence, change control and conflicts

Follow the ENERFOAM `EF-DOC-CYCLE`: canonical owner decision in Google Drive CDG → exact GitHub branch/PR and CI evidence → reviewed merge → `main` readback → Live Master Log and conflict audit. Track outcomes as `POLICY_DOCUMENTED`, `PR_OPEN`, `CI_PENDING/PASS/FAIL`, `MERGED`, and `RELEASE_NOT_AUTHORIZED` separately.

This document does **not** overwrite the original release policy for other repositories and does not authorize the publishing or deployment of any software. Where repository automation or rules conflict with these rules, stop, record `SYNC_BLOCKED`, and obtain a decision instead of silently bypassing enforcement.
