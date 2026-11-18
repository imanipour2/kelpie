# Kelpie S24 owner-operated installation / retirement runbook

Status: PREPARED-SOURCE-ONLY / DEVICE-UNVERIFIED / NOT INSTALLED.
Code: https://github.com/imanipour2/kelpie/pull/64
Parent architecture: https://github.com/imanipour2/kelpie/pull/63
Canonical operation and evidence: https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit
Issue: https://github.com/imanipour2/enerfoam-website/issues/98

## Current design and critical functional gap

An **isolated Android QA** profile is defined:
- package `com.kelpie.browser.r5motherverify`
- `versionCode=38`, `versionName=0.2.18-r5-mother-connection-qa`
- Android core and application HTTP port **18462**
- standard upstream `BrowserGatewayBinding`, `/mcp`, durable Ed25519 computer pairing
- native More > Connection Status, local diagnostic port 8080 as a **probe target** only.

The existing `com.kelpie.browser.r5betaverify` package remains on port 18461.
**Mother comparison QA has all three R5 private agent/capture flags disabled**.
It is **not** a feature-equivalent replacement for private WebView background capture,
native STOP-gated R5 runtime, or the current ChatGPT custom connector. Do not
retire the old package merely because APK install and `/health` pass. A
feature-preserving integration needs a separate reviewed engineering change
and physical tests.

## Phase A: exact-head CI and APK provenance

1. Confirm GitHub [PR #64](https://github.com/imanipour2/kelpie/pull/64)
   exact HEAD and the corresponding
   `QA R5 Upstream Connection Baseline (No Auto Install)` run
   **COMPLETED SUCCESS**. Other required Android checks and review cannot be
   inferred from success on an older head.
2. Download **only** the run artifact
   `kelpie-upstream-connect-qa-<exact-head-sha>`.
   Unzip `app-debug.apk`, `app-debug.apk.sha256`, `provenance.txt`.
3. Check `provenance.txt` source_commit, package, versionCode,
   port, signature class and SHA256 against the unmodified artifact.
   The PowerShell script below independently checks the APK hash, package,
   code and signing certificate. Keep this debug APK out of the public channel.

## Phase B: read-only owner device preflight and code backup

On the **Windows laptop with one USB-connected Samsung S24 Ultra**:
- USB debugging authorized by owner. Do not expose the ADB server remotely.
- Android SDK Platform Tools at `C:\Android\platform-tools\adb.exe`;
  Android SDK Build-Tools (aapt.exe/apksigner.bat) installed.
- Save the script alongside the extracted `app-debug.apk`; use PowerShell.
- Read exact serial/model, Android version, and installed packages without
  modifying either installed app.
- Archive the old APK code with phase `BackupOldApk` before experimenting.
  **ADB APK pull is not a backup of private Android app data** (cookies,
  browser profile, pairing keys, session) on modern Android. Obtain a genuine,
  owner-controlled recoverable backup of important state where available.

Example commands (update absolute paths as needed):

```powershell
$script = ".\kelpie-s24-mother-deploy.ps1"
$apk = ".\app-debug.apk"
& $script -Phase Preflight -Apk $apk
& $script -Phase BackupOldApk
```

STOP if preflight cannot identify the exact source APK, signer, device,
current installed app or recoverable rollback.

## Phase C: side-by-side beta install

```powershell
& $script -Phase Install -Apk $apk
& $script -Phase Verify
```

Install never removes `com.kelpie.browser.r5betaverify`; the app ID is
different. The QA application may require fresh pairing: Android app
sandboxes do **not** share Ed25519 trusted-computer decisions. The
existing Termux tunnel must NOT be silently re-pointed from 18461 to
18462. First confirm normal browser controls, native UI, port 18462,
and test pairing over a separate authorized local path. This script uses
an ephemeral host-side ADB port forward **only for /health** and removes it.

If CI APK certificate is incompatible with an **already installed version
of the same mother QA package**, `adb install -r` can fail. Do not
uninstall the comparison app blindly if its profile contains important data.
Establish signing continuity/rollback before retrying.

## Phase D: mandatory physical acceptance

- New application opens on S24 Ultra Android 17, More > Connection Status
  works, shows proper browser port and can refresh tunnel diagnostic status.
- Ed25519 pairing once; rejected/revoked key fails; no rogue Android
  localhost app can inherit user authority without owner-approved scope.
- ChatGPT remote tool discovery; `tabs.list`, snapshot, screenshot,
  click, typing and scroll actually succeed from **verified** agent,
  without exposing saved credentials or other app displays.
- STOP/revoke and Android lock deny further control.
- User-approved 25-cycle testing: HOME/background, lock/wake,
  app relaunch, network changes, reboot and stability.
- Sensitive payment/publish/delete/credential-export human gates preserved.
- Explicit review of private R5 screenshot/background functionality:
  **this QA build alone does not satisfy that criterion**.

Neither `capabilities`, a 200 `/health`, tunnel `/readyz` nor a green
GitHub Actions run is acceptance of remote E2E or background capture.

## Phase E: acceptance-gated retirement of old package

The uninstall phase is **blocked by default**. Only after real owner QA
and working alternative are independently verified should a local JSON
attestation be created (initially every field false):

```json
{
  "new_app_local_health_pass": false,
  "new_pairing_verified": false,
  "new_tunnel_endpoint_verified": false,
  "remote_tabs_and_screenshot_pass": false,
  "background_features_pass": false,
  "physical_25_cycles_pass": false,
  "old_apk_archived": false,
  "owner_approves_old_package_deletion": false
}
```

For a correctly evidenced, owner-approved acceptance file ONLY:

```powershell
& $script -Phase RetireOld -AcceptanceFile ".\accepted-on-s24.json"
```

This asks the owner to type
`DELETE com.kelpie.browser.r5betaverify` and then runs
`adb uninstall com.kelpie.browser.r5betaverify`. Uninstall clears
that package's private data; the operation is **not** performed in
the GitHub build, CI or chat. Re-check all Android user/work profiles
if the owner needs removal from every profile.

## Environment limits and release gate

This ChatGPT execution environment has no direct Windows ADB or USB access,
Android SDK or connected user laptop shell. The read-only custom Kelpie
plugin in this chat currently returns `capabilities` but no authenticated
browser tab access. Therefore it cannot directly install, delete,
grant system permissions or verify device hardware here.

No GitHub main merge, production release or WordPress LIVE/STAGING change.
Android/iOS parity remains a separate merge gate. Never publish tunnel
secrets, signing keys, auth headers or full diagnostic dumps.
