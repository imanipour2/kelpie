# Kelpie R5 — S24 Hardware Acceptance Matrix (Consent-only QA)

**Control ID:** EF-OPS-KELPIE-R5-S24-ACCEPTANCE-20261116  
**Target:** Samsung SM-S928B, Android 17 (API 37), owner user 0  
**Installed QA:** `com.kelpie.browser.r5sourceverify`, v0.2.11-r5-consent-qa (code 31), port 18457  
**Installed build provenance:** GitHub consent-only QA artifact from commit `7a900a7303b3d9f8f2fafcd12a458cc5c52b4bf6`; SHA-256 `c94db8224e5c37f0052a9e38eaca6a122a1169fd5fa91ad1a91a88654020056f` (clean install owner-authorized). The later test-only commits are NOT on the phone.  
**Authority:** Owner authorized cleanup and experimental R5 installation, but native affirmative consent must be an actual, explicit on-device touch. No user action may be synthesized through ADB, Accessibility, API, or another client. Debug QA is not release authorization.

## Acceptance definitions

- **PASS:** Measured on the physical target with verifiable response, source revision, and expected state.
- **FAIL:** Actual forbidden access, state confusion, loss of STOP, wrong package, or a proven functional regression.
- **BLOCKED:** Required physical owner action, absent paired test principal, or feature not yet implemented.
- **NOT RUN:** Executable but not executed (not a pass).
- **SOURCE PASS:** JUnit/static CI assertion only; never claim this means hardware PASS.
- **SAFE DENIAL:** Expected refusal due to focus, lock, or notification denial. A safe denial is not a completed positive grant cycle.

## Gate G0 — Provenance and permission preflight

| ID | Probe | Acceptance criteria / evidence |
| --- | --- | --- |
| G0-01 | USB/ADB model and user | Exactly one authorized S24 device, Android API 37, owner user 0 |
| G0-02 | Package and signature | PM confirms R5 package; version/code; APK SHA-256 and `apksigner` v2; note signing cert separately |
| G0-03 | Isolation and cleanup | P4 `pcverify` absent, legacy `unifiedverify` preserved and not altered, no mixed 18457/18458 |
| G0-04 | localhost readiness | 18457 forwarded to correct device, GET `/health` returns 200, POST `/v1/agent-session-status` authenticates and starts OFF |
| G0-05 | Android notifications | `dumpsys package` POST_NOTIFICATIONS and AppOps recorded; current clean install intentionally begins `granted=false`. Never grant permission with ADB |
| G0-06 | OS security boundaries | STOP receiver unexported, no RECORD_AUDIO in QA manifest, no screen capture/MediaProjection permission |

## Gate G1 — Negative / fail-closed (automatable)

| ID | Probe | Acceptance criteria / evidence |
| --- | --- | --- |
| G1-01 | Remote browser and capture calls | All legacy `get-tabs`, `navigate`, `click`, `fill`, `screenshot`, `browser-control`, and `mcp` return 403/PERMISSION_REQUIRED even with bypass-shaped JSON; no side effects |
| G1-02 | Remote affirmative | Fabricated `agent-session-approve` returns 403; never sets Active |
| G1-03 | Pending then authenticated STOP | OFF → PENDING_NATIVE_APPROVAL → STOPPED → OFF; no affirmative tap; failed-focus `USER_TAP_REQUIRED` classified safe denial not pass |
| G1-04 | 50 pending/STOP loops | Record completed vs safe denials vs anomalies. Must finish OFF, no unintended Active; distinguish from 50 genuine approval cycles |
| G1-05 | HOME background request | 10 rounds of HOME → remote request 400 USER_TAP_REQUIRED → app reopen → OFF |
| G1-06 | Screen OFF/locked request | Only when already OFF: sleep then request denied; wake but never unlock by automation |
| G1-07 | Process restart without consent | Force-stop while OFF and relaunch: new process, health 200, state OFF |
| G1-08 | Stale/foreign principal | JUnit for duplicate pending, prompt mismatch, generation invalidation and other principal; physical two-client fixtures are a separate G3 test |

## Gate G2 — Owner-native approval and STOP (physical/manual)

| ID | Probe | Acceptance criteria / evidence |
| --- | --- | --- |
| G2-01 | First permission request | With Android `POST_NOTIFICATIONS=denied`, user taps native Approve and sees the actual Android permission system dialog. No ADB permission grant |
| G2-02 | Deny permission | Owner refuses system dialog: R5 stays OFF, no STOP notification, no remote browser authority |
| G2-03 | Grant permission + Approve | Owner explicitly grants Android permission and performs any required native approval; verify `ACTIVE_CONSENT_ONLY` with `browserOperationsEnabled=false`; capture visible native UI evidence without recording secrets |
| G2-04 | STOP notification presence | Active grant creates `kelpie_r5_agent_qa` channel importance >= LOW and active notification for R5 with a clearly accessible STOP action |
| G2-05 | STOP by owner | Owner taps actual notification STOP (and separately local UI STOP) → state OFF, notification removed, old generation unusable; verify rapid revocation |
| G2-06 | Channel disabled | Only in separate owner-approved QA profile, block STOP channel/group while OFF: new approval denied and no Active. Do not modify or revoke a live owner's notification settings |
| G2-07 | No false focus rejection | While compose AlertDialog has its separate `APPLICATION` focus, genuine owner tap succeeds when device unlocked, Activity visible/RESUMED and notification available |
| G2-08 | Revocation on permission change | With genuine Active session, owner toggles notifications off; next native resume/check revokes. Verify no hidden grant survives. Requires explicit owner action |

## Gate G3 — Stability and identity after a genuine grant

| ID | Probe | Acceptance criteria / evidence |
| --- | --- | --- |
| G3-01 | HOME/return | Same process + generation continues after ordinary HOME/return; no repetitive native Approve |
| G3-02 | Lock/unlock | Active becomes SUSPENDED_LOCKED, no browser action can run while locked; wake/unlock restores same generation but advances safety epoch, invalidating in-flight work |
| G3-03 | Process death | Force-stop of Active R5, then restart must require fresh owner approval (OFF) |
| G3-04 | 50 real grant/STOP cycles | 50 distinct owner-approved cycles, each verified Active → notification STOP → OFF; no crash/ANR, nonce/generation never reused. JUnit 50 loops are NOT this test |
| G3-05 | Two actually paired principals | Unauthorized second authenticated client cannot observe/control/STOP another client's active grant, even with stale generation. Requires two genuine fixtures |
| G3-06 | Doze, network handover, renderer loss | Validate suspension/recovery without bypassing Android keyguard, recording content only from Kelpie's own WebViews |
| G3-07 | Crash/ANR/resource health | Record logcat fatal/ANR count and RSS before/after loops; no private tokens/screenshots in logs |

## Gate G4 — Full browser agent release readiness (not implemented in consent-only APK)

One owner tap should authorize the authenticated owner to navigate multiple HTTPS domains and tabs, inspect actual DOM, click, fill, scroll, capture fresh pixels of **Kelpie-owned WebViews only**, and honor STOP under foreground/background and async race conditions. Reject non-Kelpie screen, protected apps, lock screen, notification shade, injection from web content, and sensitive actions without separate confirmation. These operations are intentionally denied in the current R5 consent-only QA: classify **IMPLEMENTATION_BLOCKED**, never PASS or a functional regression of this isolated profile.

## Repeatable automation

Permanent script: `scripts/qa/r5-s24-acceptance.py`. Required `--serial` is private local ADB ID; no serial, tokens, user data, or app notification contents are printed. Use portable Python 3 and absolute `--adb` path. Example with placeholder, not a credential:

```text
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase preflight
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase negative
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase pending-stop
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase stress-pending --cycles 50
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase home-negative
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase lock-negative
python scripts/qa/r5-s24-acceptance.py --adb "<path-to-adb>" --serial "<authorized-S24-serial>" --phase restart-off
```

Owner-approved manual checkpoints use `--phase owner-approved` (read-only after the native tap) and `--phase owner-stopped` (read-only after native STOP). The script never auto-taps Approve, grants Android POST_NOTIFICATIONS, disables security, touches websites, or installs/uninstalls an APK. Run negative and restart phases only while status OFF. Output is compact NDJSON case result lines for evidence handling; redact serial and private context before GitHub publication.

## Release and evidence

- GitHub fork `main` is code/CI authority, owner CDG is decision authority, physical S24 PM/ADB is installed-state authority. Draft PR27 contains the consent-only implementation and tests; PR28 is a separate optional signing-isolation QA variant and is **not** the installed build.
- Every evidence report must name installed APK SHA/commit separately from later test-source commit, provide measured Android state, CI run, failures, and rollback.
- No Android/iOS parity exemption or production release has been approved. CI, source JUnit, or a Debug APK build cannot authorize a public release or prove G4.
- No WordPress/Elementor/WooCommerce TEST/STAGING/LIVE changes are permitted in this QA task.
- Operational trail: https://github.com/imanipour2/enerfoam-website/issues/98

**Status:** `G0_READBACK_PARTIAL / G1_AUTOMATION_IMPLEMENTED / G2_OWNER_TOUCH_REQUIRED / G3_HARDWARE_OPEN / G4_IMPLEMENTATION_BLOCKED / RELEASE_NO_GO / SYNC_BLOCKED_MAIN`.
