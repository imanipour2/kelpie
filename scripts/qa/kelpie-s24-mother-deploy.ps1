# S24 QA deployment. No changes to the Termux tunnel, bearer or plugin.
[CmdletBinding()]
param(
  [ValidateSet('Preflight','InspectInstalledSigner','BackupOldApk','Install','Verify','RetireOld')]
  [string]$Phase='Preflight',
  [string]$Apk='',
  [string]$Adb='C:\Android\platform-tools\adb.exe',
  [string]$SdkRoot='',
  [string]$AcceptanceFile='',
  [ValidateSet('Mother18462','MotherNext18463')]
  [string]$QaProfile='Mother18462',
  [switch]$OwnerBackupConfirmed
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$oldPkg='com.kelpie.browser.r5betaverify'
if ($QaProfile -eq 'MotherNext18463') {
  $newPkg='com.kelpie.browser.r5mothernextverify'
  $devicePort=18463
  $expectedVersionCode=40
  $expectedVersionName='0.2.20-r5-mother-viewport-sidecar-qa'
} else {
  $newPkg='com.kelpie.browser.r5motherverify'
  $devicePort=18462
  $expectedVersionCode=38
  $expectedVersionName='0.2.18-r5-mother-connection-qa'
}

function AdbCall([string[]]$items) {
  $result = & $script:Adb -s $script:serial @items 2>&1
  if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($items[0]) (exit $LASTEXITCODE)" }
  return (($result | Out-String).Trim())
}
function DetectPhone {
  if (Test-Path -LiteralPath $Adb -PathType Leaf) {
    $script:Adb=(Resolve-Path -LiteralPath $Adb).Path
  } else {
    $cmd=Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { throw 'ADB not found: install Android platform-tools.' }
    $script:Adb=$cmd.Source
  }
  $devices=& $script:Adb devices -l
  $ok=@($devices | Where-Object { $_ -match '^\S+\s+device(\s|$)' })
  if ($ok.Count -ne 1) {
    Write-Host ($devices | Out-String)
    throw 'Connect exactly one authorized phone and allow USB debugging.'
  }
  $script:serial=($ok[0] -split '\s+')[0]
  $model=AdbCall -items @('shell','getprop','ro.product.model')
  $version=AdbCall -items @('shell','getprop','ro.build.version.release')
  if ($model -notmatch '^SM-S928') { throw "Unexpected device: $model" }
  Write-Host "Connected Samsung $model Android $version (one authorized USB device)"
}
function Installed([string]$pkg) {
  return ((AdbCall -items @('shell','pm','path',$pkg)) -match '(?m)^package:')
}
function ShowApps {
  foreach ($pkg in @($newPkg,$oldPkg)) {
    if (Installed $pkg) {
      $ds=AdbCall -items @('shell','dumpsys','package',$pkg)
      $v=[regex]::Match($ds,'versionName=\S+').Value
      Write-Host "$pkg INSTALLED $v"
    } else { Write-Host "$pkg NOT_INSTALLED" }
  }
}
function BuildTool([string]$name) {
  $roots=@($SdkRoot,$env:ANDROID_HOME,$env:ANDROID_SDK_ROOT,"$env:LOCALAPPDATA\Android\Sdk",'C:\Android\Sdk')
  foreach ($root in $roots) {
    if (-not $root -or -not (Test-Path -LiteralPath $root)) { continue }
    $dir=Join-Path $root 'build-tools'
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    $match=@(Get-ChildItem -LiteralPath $dir -Filter $name -Recurse -File -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
    if ($match.Count -gt 0) { return $match[0].FullName }
  }
  $found=Get-Command $name -ErrorAction SilentlyContinue
  if ($null -ne $found) { return $found.Source }
  throw "$name not found in Android SDK Build-Tools; supply -SdkRoot."
}
function GetSigner([string]$file) {
  $tool=BuildTool 'apksigner.bat'
  $details=(& $tool verify --verbose --print-certs $file 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0 -or $details -notmatch 'Verified using v2 scheme[^\r\n]*:\s*true') { throw 'Invalid APK signature.' }
  $digest=[regex]::Match($details,'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
  if (-not $digest.Success) { throw 'Missing APK signer fingerprint.' }
  return $digest.Groups[1].Value.ToLowerInvariant()
}
function CompareInstalledSigner {
  if (-not (Installed $newPkg)) {
    Write-Host 'SIGNER_PREFLIGHT: target package not installed; no upgrade compatibility to compare.'
    return
  }
  if (-not $script:apkSigner) { throw 'APK candidate signer unknown; specify -Apk.' }
  $paths=AdbCall -items @('shell','pm','path',$newPkg)
  $bases=@(($paths -split '\r?\n') | Where-Object { $_ -match '^package:.*/base\.apk\s*
  if (-not $Apk) { throw 'Specify -Apk app-debug.apk from the verified GitHub Actions run.' }
  $file=(Resolve-Path -LiteralPath $Apk).Path
  $hashFile="$file.sha256"
  if (-not (Test-Path -LiteralPath $hashFile)) { throw "Missing CI hash sidecar: $hashFile" }
  $txt=(Get-Content -LiteralPath $hashFile -Raw).Trim()
  if ($txt -notmatch '^([0-9a-fA-F]{64})') { throw 'Invalid SHA256 sidecar.' }
  $expected=$Matches[1].ToLowerInvariant()
  $actual=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $expected) { throw 'APK SHA256 mismatch; refusing installation.' }
  $aapt=BuildTool 'aapt.exe'
  $badging=(& $aapt dump badging $file 2>&1 | Out-String)
  $expectedBadge="package: name='$newPkg' versionCode='$expectedVersionCode' versionName='$expectedVersionName'"
  if ($LASTEXITCODE -ne 0 -or -not $badging.Contains($expectedBadge)) { throw 'Wrong package/version; refusing install.' }
  $script:apkSigner=GetSigner $file
  Write-Host "APK SHA256: $actual"
  Write-Host "Signing certificate SHA256: $script:apkSigner"
  Write-Host "Package/version/signature checked; QA port $devicePort."
  return $file
}
function CheckHealth {
  if (-not (Installed $newPkg)) { throw 'New QA app not installed.' }
  $forward=(& $script:Adb -s $script:serial forward 'tcp:0' "tcp:$devicePort" 2>&1 | Out-String).Trim()
  if ($LASTEXITCODE -ne 0 -or $forward -notmatch '^\d{3,5}$') { throw 'Cannot create temporary ADB port forward.' }
  try {
    $resp=Invoke-WebRequest -Uri "http://127.0.0.1:$forward/health" -UseBasicParsing -TimeoutSec 4 -MaximumRedirection 0
    $body=$resp.Content | ConvertFrom-Json
    if ($resp.StatusCode -ne 200 -or $body.status -ne 'ok') { throw 'Unexpected Kelpie health response.' }
    Write-Host "PASS: device Kelpie /health on port $devicePort."
  } finally {
    $null=& $script:Adb -s $script:serial forward --remove "tcp:$forward" 2>&1
  }
}
DetectPhone
switch ($Phase) {
  'Preflight' {
    ShowApps
    if ($Apk) {
      $null=VerifiedApk
      CompareInstalledSigner
    }
    Write-Host 'READ-ONLY PREFLIGHT PASS; no device modifications.'
  }
  'InspectInstalledSigner' {
    ShowApps
    $null=VerifiedApk
    CompareInstalledSigner
    Write-Host 'READ-ONLY SIGNER PREFLIGHT COMPLETE.'
  }
  'BackupOldApk' {
    if (-not (Installed $oldPkg)) { Write-Host 'Old QA app absent.'; break }
    $root=Join-Path (Get-Location) ("old-kelpie-apk-"+(Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $remote=AdbCall -items @('shell','pm','path',$oldPkg)
    $paths=@(($remote -split '\r?\n') | Where-Object { $_ -match '^package:' })
    $counter=0
    foreach ($line in $paths) {
      $counter++
      $destination=Join-Path $root "old-$counter.apk"
      $remotePath=$line.Substring(8).Trim()
      $output=& $script:Adb -s $script:serial pull $remotePath $destination 2>&1
      if ($LASTEXITCODE -ne 0) { throw "Old APK archive failed: $($output | Out-String)" }
      Write-Host "Saved code APK $counter SHA256 $((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash)"
    }
    Write-Warning 'This is APK CODE ONLY; Android private data, browser sessions and pairing are NOT backed up.'
    Write-Host "Old APK code archive: $root"
  }
  'Install' {
    $file=VerifiedApk
    CompareInstalledSigner
    if ((Installed $newPkg) -and -not $OwnerBackupConfirmed) {
      throw 'Existing QA package has private data; confirm recoverable backup before updating.'
    }
    Write-Host 'Installing QA package without clearing existing app data.'
    $result=AdbCall -items @('install','-r','-t',$file)
    if ($result -notmatch 'Success') { throw "Install did not succeed: $result" }
    $null=AdbCall -items @('shell','am','start','-n',"$newPkg/com.kelpie.browser.MainActivity")
    Start-Sleep -Seconds 2
    CheckHealth
    ShowApps
    Write-Host 'INSTALLED for local QA. Remote MCP/browser capture acceptance NOT established.'
  }
  'Verify' {
    ShowApps
    CheckHealth
    Write-Host 'Local HTTP PASS; tunnel, pairing and remote agent still require independent E2E tests.'
  }
  'RetireOld' {
    if ($QaProfile -eq 'MotherNext18463') {
      throw 'RetireOld is disabled for MotherNext18463; preserve existing owner app.'
    }
    if (-not $AcceptanceFile) { throw 'Missing -AcceptanceFile after real hardware acceptance.' }
    $e=Get-Content -LiteralPath $AcceptanceFile -Raw | ConvertFrom-Json
    $requirements=@('new_app_local_health_pass','new_pairing_verified','new_tunnel_endpoint_verified','remote_tabs_and_screenshot_pass','background_features_pass','physical_25_cycles_pass','old_apk_archived','owner_approves_old_package_deletion')
    foreach ($key in $requirements) {
      if ($e.$key -ne $true) { throw "Deletion BLOCKED: $key is not verified." }
    }
    CheckHealth
    if (-not (Installed $oldPkg)) { Write-Host 'Old app already absent.'; break }
    Write-Warning "ADB uninstall removes Android private data of $oldPkg. Old credentials cannot be recovered."
    $typed=Read-Host "Type DELETE $oldPkg to confirm"
    if ($typed -cne "DELETE $oldPkg") { throw 'Owner declined uninstall.' }
    $result=AdbCall -items @('uninstall',$oldPkg)
    if ($result -notmatch 'Success') { throw 'Uninstall failed.' }
    if (Installed $oldPkg) { throw 'Old app still present; check Android user profiles.' }
    Write-Host 'Old QA package removed from installed apps. Check work/secondary profiles separately.'
  }
}
 })
  if ($bases.Count -ne 1) { throw 'Unable to identify one installed base.apk.' }
  $temp=Join-Path ([IO.Path]::GetTempPath()) ("kelpie-cert-"+[guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $temp -Force | Out-Null
  try {
    $local=Join-Path $temp 'installed-base.apk'
    $out=& $script:Adb -s $script:serial pull $bases[0].Substring(8).Trim() $local 2>&1
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $local)) { throw 'Read-only installed APK pull failed.' }
    $installedSigner=GetSigner $local
  } finally {
    Remove-Item -LiteralPath $temp -Force -Recurse -ErrorAction SilentlyContinue
  }
  if ($installedSigner -ne $script:apkSigner) {
    throw 'SIGNER_MISMATCH: installed package cannot be safely replaced. No changes made.'
  }
  $dump=AdbCall -items @('shell','dumpsys','package',$newPkg)
  $versions=[regex]::Matches($dump,'(?m)versionCode=(\d+)')
  if ($versions.Count -eq 0) { throw 'Installed versionCode unavailable.' }
  if ([int]$versions[0].Groups[1].Value -gt $expectedVersionCode) { throw 'Downgrade refused.' }
  Write-Host 'SIGNER_MATCH: installed and candidate package share the verified signer.'
}
function VerifiedApk {
  if (-not $Apk) { throw 'Specify -Apk app-debug.apk from the verified GitHub Actions run.' }
  $file=(Resolve-Path -LiteralPath $Apk).Path
  $hashFile="$file.sha256"
  if (-not (Test-Path -LiteralPath $hashFile)) { throw "Missing CI hash sidecar: $hashFile" }
  $txt=(Get-Content -LiteralPath $hashFile -Raw).Trim()
  if ($txt -notmatch '^([0-9a-fA-F]{64})') { throw 'Invalid SHA256 sidecar.' }
  $expected=$Matches[1].ToLowerInvariant()
  $actual=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $expected) { throw 'APK SHA256 mismatch; refusing installation.' }
  $aapt=BuildTool 'aapt.exe'
  $badging=(& $aapt dump badging $file 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='com.kelpie.browser.r5motherverify' versionCode='38'") {
    throw 'Unexpected package/version; refusing installation.'
  }
  $signer=BuildTool 'apksigner.bat'
  $sig=(& $signer verify --verbose --print-certs $file 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0 -or $sig -notmatch 'Verified using v2 scheme[^\r\n]*:\s*true') {
    throw 'APK signature invalid; refusing installation.'
  }
  $digest=[regex]::Match($sig,'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
  if (-not $digest.Success) { throw 'APK signature fingerprint missing.' }
  Write-Host "APK SHA256: $actual"
  Write-Host "Signing certificate SHA256: $($digest.Groups[1].Value)"
  Write-Host 'APK package/version/signature verified; Android target port 18462.'
  return $file
}
function CheckHealth {
  if (-not (Installed $newPkg)) { throw 'New QA app not installed.' }
  $forward=(& $script:Adb -s $script:serial forward 'tcp:0' "tcp:$devicePort" 2>&1 | Out-String).Trim()
  if ($LASTEXITCODE -ne 0 -or $forward -notmatch '^\d{3,5}$') { throw 'Cannot create temporary ADB port forward.' }
  try {
    $resp=Invoke-WebRequest -Uri "http://127.0.0.1:$forward/health" -UseBasicParsing -TimeoutSec 4 -MaximumRedirection 0
    $body=$resp.Content | ConvertFrom-Json
    if ($resp.StatusCode -ne 200 -or $body.status -ne 'ok') { throw 'Unexpected Kelpie health response.' }
    Write-Host "PASS: device Kelpie /health on port $devicePort."
  } finally {
    $null=& $script:Adb -s $script:serial forward --remove "tcp:$forward" 2>&1
  }
}
DetectPhone
switch ($Phase) {
  'Preflight' {
    ShowApps
    if ($Apk) { $null=VerifiedApk }
    Write-Host 'READ-ONLY PREFLIGHT PASS; no device app removed.'
  }
  'BackupOldApk' {
    if (-not (Installed $oldPkg)) { Write-Host 'Old QA app absent.'; break }
    $root=Join-Path (Get-Location) ("old-kelpie-apk-"+(Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $remote=AdbCall -items @('shell','pm','path',$oldPkg)
    $paths=@(($remote -split '\r?\n') | Where-Object { $_ -match '^package:' })
    $counter=0
    foreach ($line in $paths) {
      $counter++
      $destination=Join-Path $root "old-$counter.apk"
      $remotePath=$line.Substring(8).Trim()
      $output=& $script:Adb -s $script:serial pull $remotePath $destination 2>&1
      if ($LASTEXITCODE -ne 0) { throw "Old APK archive failed: $($output | Out-String)" }
      Write-Host "Saved code APK $counter SHA256 $((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash)"
    }
    Write-Warning 'This is APK CODE ONLY; Android private data, browser sessions and pairing are NOT backed up.'
    Write-Host "Old APK code archive: $root"
  }
  'Install' {
    $file=VerifiedApk
    Write-Host 'Installing new isolated package; old app preserved.'
    $result=AdbCall -items @('install','-r','-t',$file)
    if ($result -notmatch 'Success') { throw "Install did not succeed: $result" }
    $null=AdbCall -items @('shell','am','start','-n',"$newPkg/com.kelpie.browser.MainActivity")
    Start-Sleep -Seconds 2
    CheckHealth
    ShowApps
    Write-Host 'INSTALLED for local QA. Remote MCP/browser capture acceptance NOT established.'
  }
  'Verify' {
    ShowApps
    CheckHealth
    Write-Host 'Local HTTP PASS; tunnel, pairing and remote agent still require independent E2E tests.'
  }
  'RetireOld' {
    if (-not $AcceptanceFile) { throw 'Missing -AcceptanceFile after real hardware acceptance.' }
    $e=Get-Content -LiteralPath $AcceptanceFile -Raw | ConvertFrom-Json
    $requirements=@('new_app_local_health_pass','new_pairing_verified','new_tunnel_endpoint_verified','remote_tabs_and_screenshot_pass','background_features_pass','physical_25_cycles_pass','old_apk_archived','owner_approves_old_package_deletion')
    foreach ($key in $requirements) {
      if ($e.$key -ne $true) { throw "Deletion BLOCKED: $key is not verified." }
    }
    CheckHealth
    if (-not (Installed $oldPkg)) { Write-Host 'Old app already absent.'; break }
    Write-Warning "ADB uninstall removes Android private data of $oldPkg. Old credentials cannot be recovered."
    $typed=Read-Host "Type DELETE $oldPkg to confirm"
    if ($typed -cne "DELETE $oldPkg") { throw 'Owner declined uninstall.' }
    $result=AdbCall -items @('uninstall',$oldPkg)
    if ($result -notmatch 'Success') { throw 'Uninstall failed.' }
    if (Installed $oldPkg) { throw 'Old app still present; check Android user profiles.' }
    Write-Host 'Old QA package removed from installed apps. Check work/secondary profiles separately.'
  }
}
