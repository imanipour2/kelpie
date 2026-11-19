# R5 MotherNext 18463 — safe device preflight and installation handoff

Status: SOURCE REVIEW / OWNER APPROVED TO DEVELOP / NOT INSTALLED / DEVICE SIGNER UNVERIFIED. Owner S24 Ultra, Android 17. This is **not** a public release.

## Evidence

- [PR #71](https://github.com/imanipour2/kelpie/pull/71) is a Draft **stacked on PR #70**, which is stacked on PR #66. GitHub main and actual phone do not inherit these changes.
- Exact Android application source build: `f2689ef2d34ec083a9b09d74fb6c22dc050c32bb`. All 11 relevant build/QA workflows passed on that source; formatter isolate intentionally skipped.
- [CI run 44462622168](https://github.com/imanipour2/kelpie/actions/runs/44462622168), [artifact 14501556393](https://github.com/imanipour2/kelpie/actions/runs/44462622168/artifacts/14501556393) (expires 2026-12-03): package `com.kelpie.browser.r5mothernextverify`, versionCode 40, versionName `0.2.20-r5-mother-viewport-sidecar-qa`, port 18463, APK SHA256 `2be5b95b05780e92b1265ee1ffdf2b90e0d15fb2c3b6a740bbe0b4e943dd2d36`, Android Debug certificate SHA256 `c392053dddc38407e12d7154266bbe785b76370102477ea34b86731fdab53d9e`.
- Current remote connector `FORBIDDEN`, Windows Browser Use `tunnel-client has not been seen for 300 seconds`. Do not conclude that device USB/ADB is connected.
- Legacy `scripts/qa/kelpie-s24-mother-deploy.ps1` restored unchanged; it concerns older 18462 QA package and must **not** be used for new 18463 artifact.

## Read-only phase on owner Windows computer

Prerequisites: physical S24 + USB cable, Android USB debugging authorized by owner, `C:\Android\platform-tools\adb.exe`, Android SDK Build Tools (`aapt.exe` and `apksigner.bat`), and exact authenticated GitHub artifact extracted. Do **not** send signing keys, bearer or serial to ChatGPT. Windows PowerShell:

```powershell
$script = ".\kelpie-s24-mothernext-18463.ps1"
$apk = ".\app-debug.apk"
& $script -Phase Preflight -Apk $apk
```

- Script checks known exact-build SHA256, package and version identity, debug certificate, single authorized SM-S928 device, installed target package. If installed, pulls only its base.apk to a temporary local directory, compares **both signing certificates**, checks installed versionCode, removes the temporary copy. It does not read or export private Android app data.
- `SIGNER_MISMATCH`, missing SDK, APK hash mismatch, wrong device, missing installed base.apk or version readback: hard STOP; no `adb install`, no `adb uninstall`, no `pm clear`, no Termux tunnel change.
- The standalone script is [scripts/qa/kelpie-s24-mothernext-18463.ps1](https://github.com/imanipour2/kelpie/blob/fix/r5-mother-atomic-webview-switch-20261119/scripts/qa/kelpie-s24-mothernext-18463.ps1). Its syntax/source safety must pass [Windows QA workflow](https://github.com/imanipour2/kelpie/actions/workflows/qa-r5-mother-deploy-script.yml).

## Owner-gated non-destructive installation

Only after read-only preflight **PASS**, recoverable backup and rollback preparation, owner authorization for installation:

```powershell
& $script -Phase Install -Apk $apk -OwnerAuthorizesInstall -OwnerBackupConfirmed
```

Use `-OwnerBackupConfirmed` **only** if a genuine recoverable backup has been verified for an existing installed target app. A copy of an APK is **not** a backup of cookies, owner pairing, login, or user app data. If no target package is installed, the backup flag is not necessary. The command never uninstalls an app, changes tunnel configuration, captures system pixels, or grants native agent consent. Any subsequent native owner approval remains exclusively in the Android app.

## Go/no-go after installation

Before modifying the existing working Termux bridge, verify S24 package/signature and `http://127.0.0.1:18463/health`, then authenticated pairing, the owner consent STOP notification and same-principal MCP identity, 20 real tab and modal switches, 25 fresh private screenshot cycles, 0x0 renderer negative test, scroll/form and full-results coverage, screenshot privacy gates, lock/HOME/restart/network changes. Never infer `E2E_PASS` from `health=200` or a successful CI build. Do not send or persist sensitive screenshot pixels or credentials in logs.

## Rollback and governance

Preserve the existing installed app and Termux config; if installed certificate does not match the CI signer, preserve and report it rather than reinstalling. Public beta remains `NO_GO` until image privacy/Android physical QA and iOS parity or scoped exception. All actions and outcomes go to [Issue #98](https://github.com/imanipour2/enerfoam-website/issues/98) and PR #71. Source/CI is GitHub, owner approval in Drive CDG, device truth only from S24. `SYNC_BLOCKED_MAIN` persists.
