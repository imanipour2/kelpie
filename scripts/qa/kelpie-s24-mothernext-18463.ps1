# R5 MotherNext 18463. Run locally on owner's Windows PC; never auto-run from CI.
# Preflight and InspectSigner are read-only on-device. No deletion or tunnel changes.
[CmdletBinding()]
param(
  [ValidateSet('Preflight','InspectSigner','Install')]
  [string]$Phase='Preflight',
  [Parameter(Mandatory=$true)][string]$Apk,
  [string]$Adb='C:\Android\platform-tools\adb.exe',
  [string]$SdkRoot='',
  [switch]$OwnerBackupConfirmed,
  [switch]$OwnerAuthorizesInstall
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$pkg='com.kelpie.browser.r5mothernextverify'
$expectedCode=40
$expectedName='0.2.20-r5-mother-viewport-sidecar-qa'
$knownSourceSha='f2689ef2d34ec083a9b09d74fb6c22dc050c32bb'
$expectedApkHash='2be5b95b05780e92b1265ee1ffdf2b90e0d15fb2c3b6a740bbe0b4e943dd2d36'
$expectedCert='c392053dddc38407e12d7154266bbe785b76370102477ea34b86731fdab53d9e'
function FindBuildTool([string]$name) {
  $roots=@($SdkRoot,$env:ANDROID_HOME,$env:ANDROID_SDK_ROOT,"$env:LOCALAPPDATA\Android\Sdk",'C:\Android\Sdk')
  foreach($root in $roots) {
    if (-not $root -or -not (Test-Path -LiteralPath $root)) { continue }
    $dir=Join-Path $root 'build-tools'
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    $candidate=@(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter $name -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
    if ($candidate.Count -gt 0) { return $candidate[0].FullName }
  }
  $cmd=Get-Command $name -ErrorAction SilentlyContinue
  if ($null -ne $cmd) { return $cmd.Source }
  throw "Android build tool $name was not found; pass -SdkRoot."
}
function ApkSigner([string]$file) {
  $tool=FindBuildTool 'apksigner.bat'
  $output=(& $tool verify --verbose --print-certs $file 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0 -or $output -notmatch 'Verified using v2 scheme[^\r\n]*:\s*true') {
    throw 'The APK lacks a verifiable v2 signing certificate.'
  }
  $match=[regex]::Match($output,'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
  if (-not $match.Success) { throw 'Unable to read signer certificate SHA256.' }
  return $match.Groups[1].Value.ToLowerInvariant()
}
function ADB([string[]]$argsList) {
  $output=(& $script:adbExe -s $script:device @argsList 2>&1 | Out-String).Trim()
  if ($LASTEXITCODE -ne 0) { throw "ADB command rejected: $($argsList[0])" }
  return $output
}
$apkFile=(Resolve-Path -LiteralPath $Apk -ErrorAction Stop).Path
$actual=(Get-FileHash -LiteralPath $apkFile -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expectedApkHash) { throw 'APK SHA256 differs from the reviewed exact-head build.' }
$aapt=FindBuildTool 'aapt.exe'
$badge=(& $aapt dump badging $apkFile 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt could not inspect the candidate APK.' }
$expectBadge="package: name='$pkg' versionCode='$expectedCode' versionName='$expectedName'"
if (-not $badge.Contains($expectBadge)) { throw 'APK identity/version differs from expected R5 MotherNext 18463 profile.' }
$candidateCert=ApkSigner $apkFile
if ($candidateCert -ne $expectedCert) { throw 'Candidate certificate SHA256 differs from exact CI artifact.' }
if (Test-Path -LiteralPath $Adb -PathType Leaf) {
  $script:adbExe=(Resolve-Path -LiteralPath $Adb).Path
} else {
  $adbCmd=Get-Command adb -ErrorAction SilentlyContinue
  if ($null -eq $adbCmd) { throw 'ADB unavailable; install Android SDK Platform Tools on owner laptop.' }
  $script:adbExe=$adbCmd.Source
}
$devices=@(& $script:adbExe devices -l)
$ready=@($devices | Where-Object { $_ -match '^\S+\s+device(\s|$)' })
if ($ready.Count -ne 1) { throw 'Connect and authorize exactly one Android device via USB debugging.' }
$script:device=($ready[0] -split '\s+')[0]
$model=ADB @('shell','getprop','ro.product.model')
$android=ADB @('shell','getprop','ro.build.version.release')
if ($model -notmatch '^SM-S928') { throw 'This script requires the owner Samsung Galaxy S24 Ultra.' }
$paths=ADB @('shell','pm','path',$pkg)
$installed=($paths -match '(?m)^package:')
Write-Host "PREFLIGHT: Samsung $model / Android $android / target package $pkg / expected port 18463"
Write-Host "APK: SHA256 $actual / source $knownSourceSha / certificate SHA256 $candidateCert"
if ($installed) {
  $bases=@(($paths -split '\r?\n') | Where-Object { $_ -match '^package:.*/base\.apk\s*$' })
  if ($bases.Count -ne 1) { throw 'Cannot safely identify installed base.apk for signer comparison.' }
  $temp=Join-Path ([IO.Path]::GetTempPath()) ("kelpie-cert-"+[guid]::NewGuid().ToString('N'))
  $null=New-Item -ItemType Directory -Path $temp -Force
  try {
    $oldApk=Join-Path $temp 'installed-base.apk'
    $null=ADB @('pull',$bases[0].Substring(8).Trim(),$oldApk)
    $installedCert=ApkSigner $oldApk
  } finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
  }
  if ($installedCert -ne $candidateCert) {
    throw 'SIGNER_MISMATCH: installed app cannot be updated with this CI APK. DO NOT UNINSTALL.'
  }
  $dump=ADB @('shell','dumpsys','package',$pkg)
  $code=[regex]::Match($dump,'(?m)versionCode=(\d+)')
  if (-not $code.Success) { throw 'Installed package versionCode unknown.' }
  if ([int]$code.Groups[1].Value -gt $expectedCode) { throw 'Downgrade refused.' }
  Write-Host 'SIGNER_MATCH: installed and candidate certificates are identical.'
} else {
  Write-Host 'TARGET_NOT_INSTALLED: no existing MotherNext data at this package identity.'
}
if ($Phase -ne 'Install') {
  Write-Host 'READ_ONLY_PASS: no installed APK, app data, port, pairing or tunnel modified.'
  return
}
if (-not $OwnerAuthorizesInstall) { throw 'Install requires -OwnerAuthorizesInstall after inspecting read-only output.' }
if ($installed -and -not $OwnerBackupConfirmed) {
  throw 'Installed app data may be changed by update; a recoverable owner backup must be confirmed.'
}
$installOutput=ADB @('install','-r','-t',$apkFile)
if ($installOutput -notmatch 'Success') { throw 'ADB did not confirm successful package installation.' }
Write-Host 'INSTALL_COMPLETED: no app removed, no tunnel changed. Verify local health and native STOP separately.'
