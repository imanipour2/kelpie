# EF-OPS-KELPIE-S24-REMEDIATION-002 | 2026-11-14

STATUS: DRAFT / NOT DEPLOYED / NO RELEASE ACCEPTANCE.
Branch: fix/s24-recovery-crypto-20261114 based on 5d975eae3905235b622edb60393485b5e7b985c2. This branch is not installed on the phone.

## Baseline and evidence
- PR #1 is open, draft, unmerged; base qa/s24-unified-pixelcopy-scroll-16kb-20261113, not main.
- Prior physical SM-S928B Android 17: com.kelpie.browser.unifiedverify v0.2.5 code 25; previous installed APK from 193da4f, recorded SHA256 8088ae521227bb7083e40bcb2bb5cfe47f08ba68ef33e8d05d875fe975bcfee7. This is not a newly built artifact.
- New direct MCP status: appForeground=false; notificationsEnabled=true; screenshotReady=false; recovery=USER_TAP_REQUIRED; tabs.list succeeded and preserved existing tab.
- Earlier end-to-end notification tap / PNG was verified on a previous patch, not on this remediation branch. No website changes.

## Root cause analysis
P0-A: Historical restored encrypted prefs without corresponding Android Keystore key caused AEADBadTagException startup crash, after CI debug signing mismatch and clean reinstall. Source inspection confirms SecretStore.open() and reads lacked guarded recovery.
P0-B: Recovery notification returns a Boolean rather than a request ID; Not now merely cancels the fixed notification. There is no one-shot state/TTL/replay protection in the live flow. Sending an Android notification does not prove the user saw or accepted it.
P1: Notification HIGH importance does not guarantee heads-up. Current status icon is a launcher icon. usesCleartextTraffic=true; LAN binding, pairing, auth and FGS connectedDevice eligibility require review.

## Changes in this branch
- SecretStore now fails closed if encryption init/decryption fails, without erasing ciphertext, tabs or pairing files; unavailable secrets read as absent and secret writes fail explicitly. This is containment, not a completed recovery UI.
- Backup rules exclude sharedpref/kelpie_secrets.xml from legacy backup, modern cloud backup and device transfer. Manifest merger still needs verification.
- ForegroundRecoveryState provides an isolated clock-injected model: ID, 120s TTL, coalescing, expiry, decline and legal transitions; JVM tests cover replay and expiry. NOT yet integrated with notification / Activity / MCP.
- New branch added to Android QA workflow, including ktlint, unit, lint, APK packaging, RELRO and 16 KiB checks.

## Blocking acceptance gates
1. Integrate request ID in PendingIntents and MCP, and authenticate matching approve/decline lifecycle; stale approval cannot authorize the new request.
2. Add user-visible re-authentication and scoped recovery for failed Keystore, with explicit consent for any destructive reset.
3. Increment versionCode and use stable protected signer; no uninstall/data wipe due to APK signing mismatch. Verify migration and rollback.
4. Real SM-S928B device tests: foreground, background, tap Allow, tap Not now, TTL, blocked/silent/DND, lock, split-screen, screenshot freshness, navigation race and tab preservation.
5. Instrumentation and MCP contract tests, concurrency, retry throttling and 24h reliability; Wi-Fi/cellular and reboot.
6. Audit Android HTTP cleartext, LAN endpoints, exported intents, pairing replay, connectedDevice FGS eligibility and local tunnel auth.
7. Verify actual 16 KiB runtime with getconf PAGE_SIZE=16384 in addition to APK alignment.

## Rollback
Preserve the installed APK and non-secret app data; never blindly uninstall. In-place adb install -r is permitted only when signing certificate, package and version constraints are verified; otherwise require an approved migration procedure. No remote launch, root, notification permission force-grant, or lock bypass.

## Sources
- Android notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android channels: https://developer.android.com/develop/ui/compose/notifications/channels
- Secure background Activity launch: https://developer.android.com/guide/components/activities/secure-bal
- Android backup: https://developer.android.com/identity/data/autobackup
- Android 16 KiB: https://developer.android.com/guide/practices/page-sizes
- Prior PR and issue: https://github.com/imanipour2/kelpie/pull/1 and https://github.com/imanipour2/enerfoam-website/issues/98

Code branch readback/CI required before acceptance. Canonical Drive sync not yet verified: SYNC_BLOCKED.
