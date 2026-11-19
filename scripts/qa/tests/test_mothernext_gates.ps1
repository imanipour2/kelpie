# Executes the real deployment script against fake SDK/ADB/GitHub boundaries only.
# No physical device, credentials, network, installation or Android package is used.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$guard=Join-Path $PSScriptRoot '../kelpie-s24-mothernext-18463.ps1'
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('kelpie-guard-tests-'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
$global:KelpieGuardFixture=$fixture
function Hash([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Json([string]$path,$value) { $value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $path -Encoding utf8 }
function Assert([bool]$ok,[string]$message) { if (-not $ok) { throw $message } }
@'
param([string]$Kind,[Parameter(ValueFromRemainingArguments=$true)][string[]]$ToolArgs)
$global:LASTEXITCODE=0
$dir=$global:KelpieGuardFixture
$stateFile=Join-Path $dir 'state.json'
$state=Get-Content $stateFile -Raw | ConvertFrom-Json
function Save { $state | ConvertTo-Json -Depth 10 | Set-Content $stateFile -Encoding utf8 }
if ($Kind -eq 'signer') {
  'Verified using v2 scheme (APK Signature Scheme v2): true'
  $cert=if ($ToolArgs[-1] -like '*rollback.apk') { $state.rollbackCert } else { $state.candidateCert }
  "Signer #1 certificate SHA-256 digest: $cert"
  return
}
if ($Kind -eq 'aapt') {
  if ($ToolArgs[-1] -like '*rollback.apk') {
    "package: name='com.kelpie.browser.r5mothernextverify' versionCode='40' versionName='0.2.20-r5-mother-viewport-sidecar-qa'"
  } else { "package: name='$($state.candidatePackage)' versionCode='41' versionName='0.2.21-r5-wave1-qa'" }
  return
}
ConvertTo-Json -InputObject @($ToolArgs) -Compress | Add-Content (Join-Path $dir 'commands.jsonl')
if ($ToolArgs[0] -eq 'devices') { 'List of devices attached'; 'fixture-usb device model:SM_S928B'; return }
if ($ToolArgs[0] -ne '-s' -or $ToolArgs[1] -ne 'fixture-usb') { throw 'Unexpected device selector in test.' }
$command=@($ToolArgs | Select-Object -Skip 2)
if ($command[0] -eq 'install') {
  $state.installCalls++
  if ($state.installFails) { Save; $global:LASTEXITCODE=1; 'Failure'; return }
  $state.installedHash=$state.candidateHash
  $state.code=41
  $state.name='0.2.21-r5-wave1-qa'
  Save
  'Success'
  return
}
$key=$command -join ' '
switch -Wildcard ($key) {
  'shell getprop ro.product.model' { $state.model }
  'shell getprop ro.build.version.release' { '17' }
  'shell getprop ro.build.version.sdk' { '37' }
  'shell am get-current-user' { '0' }
  'shell pm list packages --user 0 kelpie' {
    $state.inventoryReads++
    Save
    foreach ($package in $state.packages) { "package:$package" }
    if ($state.inventoryChanges -and $state.inventoryReads -gt 1) { 'package:com.kelpie.browser.parallel' }
  }
  'shell pm list packages --user 0 com.termux' { if ($state.termuxInstalled) { 'package:com.termux' } }
  'shell df -k /data' { "Filesystem 1K-blocks Used Available Use% Mounted on`n/dev/block/fixture 4000000 1 $($state.freeKiB) 1% /data" }
  'shell pm path --user 0 com.kelpie.browser.r5mothernextverify' {
    if ($state.installed) { 'package:/data/app/~~fixture/com.kelpie.browser.r5mothernextverify-test/base.apk' }
  }
  'shell sha256sum *' {
    $state.hashReads++
    $digest=$state.installedHash
    if ($state.changeDuringPreflight -and $state.hashReads -gt 1) { $digest='9'*64 }
    Save
    "$digest  /data/app/fixture/base.apk"
  }
  'shell dumpsys package com.kelpie.browser.r5mothernextverify' { "versionCode=$($state.code)`nversionName=$($state.name)" }
  default { throw "Disallowed/unexpected test ADB operation: $key" }
}
'@ | Set-Content (Join-Path $fixture 'fake-tool.ps1') -Encoding utf8
foreach ($kind in @('adb','signer','aapt')) {
  "& `"`$PSScriptRoot/fake-tool.ps1`" -Kind '$kind' @args" | Set-Content (Join-Path $fixture "$kind.ps1") -Encoding utf8
}
# Mock only external command discovery and GitHub readback. The guard is unmodified.
function global:Get-Command {
  [CmdletBinding()]param([Parameter(Position=0)][string]$Name)
  $kind=switch ($Name) { 'apksigner.bat' { 'signer' }; 'aapt.exe' { 'aapt' }; default { $null } }
  if ($null -ne $kind) { return [pscustomobject]@{Source=(Join-Path $global:KelpieGuardFixture "$kind.ps1")} }
  return Microsoft.PowerShell.Core\Get-Command @PSBoundParameters
}
function global:Invoke-RestMethod {
  [CmdletBinding()]param([string]$Uri,[hashtable]$Headers,[int]$TimeoutSec)
  if ($Uri -notmatch '^https://api.github.com/repos/imanipour2/kelpie/actions/runs\?head_sha=[a-f0-9]{40}&per_page=100$') {
    throw 'Test forbids unexpected network destination.'
  }
  return Get-Content (Join-Path $global:KelpieGuardFixture 'ci.json') -Raw | ConvertFrom-Json
}
$source='a'*40
$cert='c'*64
$package='com.kelpie.browser.r5mothernextverify'
$paths=@('android.yml','macos.yml','lint.yml','qa-android-private-capture.yml','qa-capture-format-patch.yml',
  'qa-p4-parallel-slot.yml','qa-r5-background-agent.yml','qa-r5-ktlint-autofix.yml','qa-r5-mother-deploy-script.yml',
  'qa-r5-mother-sidecar.yml','qa-r5-private-beta.yml','qa-r5-source-slot.yml','qa-r5-upstream-connection.yml','qa-wave1-contracts.yml')
function New-Fixture {
  'test-candidate-bytes' | Set-Content (Join-Path $fixture 'candidate.apk')
  'test-installed-rollback-bytes' | Set-Content (Join-Path $fixture 'rollback.apk')
  'encrypted fixture backup, no real user data' | Set-Content (Join-Path $fixture 'backup.bin')
  'fixture test record with exact measured assertions' | Set-Content (Join-Path $fixture 'record.txt')
  Remove-Item (Join-Path $fixture 'commands.jsonl') -ErrorAction SilentlyContinue
  $candidateHash=Hash (Join-Path $fixture 'candidate.apk')
  $installedHash=Hash (Join-Path $fixture 'rollback.apk')
  $record=@{path='record.txt';sha256=(Hash (Join-Path $fixture 'record.txt'))}
  $stamp=[DateTimeOffset]::UtcNow.AddMinutes(-5).ToString('o')
  $runs=@()
  for ($index=0;$index -lt $paths.Count;$index++) {
    $runs+=@{id=1000+$index;path=".github/workflows/$($paths[$index])";head_sha=$source;head_branch='fixture-wave1';
      status='completed';conclusion=$(if ($paths[$index] -eq 'qa-r5-ktlint-autofix.yml') {'skipped'} else {'success'});
      html_url="https://github.com/imanipour2/kelpie/actions/runs/$(1000+$index)";run_attempt=1}
  }
  $script:ci=@{total_count=$runs.Count;workflow_runs=$runs}
  $script:manifest=@{schemaVersion=1;sourceCommit=$source;repository='https://github.com/imanipour2/kelpie';
    mission='EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001';packageName=$package;versionCode=41;versionName='0.2.21-r5-wave1-qa';
    localHttpPort=18463;buildVariant='debug';gradleProfile='kelpieR5MotherNextQaProfile';buildUtc=$stamp;
    artifactSha256=$candidateHash;signerCertificateSha256=$cert;signingSchemeV2=$true;
    ciRunUrl=$runs[9].html_url;ciRunAttempt=1;ciWorkflow='.github/workflows/qa-r5-mother-sidecar.yml';
    installPolicy='NO_AUTO_INSTALL_SINGLE_PACKAGE_SIGNER_MATCH_REQUIRED'}
  $sha=[Security.Cryptography.SHA256]::Create()
  try { $serialHash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes('fixture-usb')))).Replace('-','').ToLowerInvariant() }
  finally { $sha.Dispose() }
  $checks=@{}
  foreach ($check in @('controlPlaneNoGrant','grantSeparation','stopInflight','lockResumeEpoch','tabTransferAck',
    'documentIdentity','staleFrameRejected','privateCaptureUsableAndRedacted','lifecycle25Cycles','realBehavior',
    'rotation','homeResume','screenOffOn','processRestart','pairingPreserved','browserStateRestored','sensitiveActionsNeedHuman')) { $checks[$check]=$true }
  $script:evidence=@{schemaVersion=1;sourceCommit=$source;packageName=$package;candidateSha256=$candidateHash;
    device=@{serialSha256=$serialHash;model='SM-S928B';androidRelease='17';sdk=37;userId=0};
    installed=@{artifactSha256=$installedHash;signerCertificateSha256=$cert;versionCode=40;versionName='0.2.20-r5-mother-viewport-sidecar-qa'};
    backup=@{captureMode='COLD';appStopped=$true;installedArtifactSha256=$installedHash;createdUtc=$stamp;record=$record;
      archive=@{path='backup.bin';sha256=(Hash (Join-Path $fixture 'backup.bin'))}};
    coldRestore=@{status='PASS';backupSha256=(Hash (Join-Path $fixture 'backup.bin'));dataBeforeSha256=('d'*64);dataAfterSha256=('d'*64);testedUtc=$stamp;record=$record};
    rollback=@{status='PASS';fromCandidateSha256=$candidateHash;toInstalledArtifactSha256=$installedHash;
      versionDowngradeVerified=$true;retainsData=$true;testedUtc=$stamp;record=$record};
    deviceAcceptance=@{status='PASS';sourceCommit=$source;candidateSha256=$candidateHash;testedUtc=$stamp;checks=$checks;record=$record;
      tabSwitchCycles=20;distinctTabs=3;freshFrames=25;displayCreateReleaseCycles=50};
    transport=@{sourceCommit=$source;candidateSha256=$candidateHash;browserPort=18463;diagnosticPort=8080;existingTunnelPreserved=$true;
      remoteAuthenticatedJourney='PASS';nativeConsentRoundTrip='PASS';healthStatus=200;readyStatus=503;
      probeClassification='PROBE_DEGRADED_REMOTE_UNKNOWN';testedUtc=$stamp;record=$record}}
  $script:state=@{candidatePackage=$package;candidateHash=$candidateHash;candidateCert=$cert;rollbackCert=$cert;
    installedHash=$installedHash;code=40;name='0.2.20-r5-mother-viewport-sidecar-qa';model='SM-S928B';installed=$true;
    installCalls=0;hashReads=0;changeDuringPreflight=$false;installFails=$false;packages=@($package);termuxInstalled=$true;
    freeKiB=1000000;inventoryReads=0;inventoryChanges=$false}
  $script:arguments=@{Apk=(Join-Path $fixture 'candidate.apk');CandidateManifest=(Join-Path $fixture 'manifest.json');
    ReviewedSourceCommit=$source;ReviewedManifestSha256='';RecoveryEvidence=(Join-Path $fixture 'evidence.json');
    ReviewedEvidenceSha256='';RollbackApk=(Join-Path $fixture 'rollback.apk');Adb=(Join-Path $fixture 'adb.ps1');
    OwnerAuthorizesInstall=$true;Phase='Install'}
}
function Run-Case([string]$name,[scriptblock]$change,[string]$expected,[int]$expectedInstalls=0) {
  New-Fixture
  & $change
  Json (Join-Path $fixture 'state.json') $state
  Json (Join-Path $fixture 'ci.json') $ci
  Json (Join-Path $fixture 'manifest.json') $manifest
  Json (Join-Path $fixture 'evidence.json') $evidence
  $arguments.ReviewedManifestSha256=Hash (Join-Path $fixture 'manifest.json')
  $arguments.ReviewedEvidenceSha256=Hash (Join-Path $fixture 'evidence.json')
  $output=''
  try { $output=(& $guard @arguments 6>&1 | Out-String) }
  catch { $output=$_.Exception.Message }
  Assert ($output.Contains($expected)) "${name}: expected $expected; received $output"
  $result=Get-Content (Join-Path $fixture 'state.json') -Raw | ConvertFrom-Json
  Assert ($result.installCalls -eq $expectedInstalls) "${name}: unauthorized install count $($result.installCalls)"
  $commands=if (Test-Path (Join-Path $fixture 'commands.jsonl')) { Get-Content (Join-Path $fixture 'commands.jsonl') -Raw } else { '' }
  Assert ($commands -notmatch '"(?:uninstall|pull|clear|forward|reverse)"') "${name}: prohibited ADB operation"
  Write-Host "PASS: $name"
}
try {
  Run-Case 'reviewed gates read only' { $arguments.Phase='Preflight' } 'BETA_INSTALL_GO'
  Run-Case 'same package authorized update readback' {} 'INSTALL_COMPLETED' 1
  Run-Case 'backup checkbox cannot replace restore evidence' { $arguments.RecoveryEvidence='';$arguments.OwnerBackupConfirmed=$true } 'RECOVERY_EVIDENCE_REQUIRED'
  Run-Case 'backup checkbox cannot replace failed restore' { $arguments.OwnerBackupConfirmed=$true;$evidence.coldRestore.status='FAIL' } 'COLD_RESTORE_NOT_PROVEN'
  Run-Case 'explicit owner approval independent' { $arguments.OwnerAuthorizesInstall=$false } 'OWNER_AUTHORIZATION_REQUIRED'
  Run-Case 'missing local installed APK cannot trigger pull' { $arguments.RollbackApk='' } 'LOCAL_ROLLBACK_APK_REQUIRED'
  Run-Case 'wrong installed APK bytes' { $state.installedHash='e'*64 } 'ROLLBACK_APK_NOT_INSTALLED_BYTES'
  Run-Case 'signer mismatch cannot trigger uninstall' { $state.rollbackCert='b'*64 } 'SIGNER_MISMATCH'
  Run-Case 'no parallel package fallback' { $state.installed=$false } 'EXISTING_SINGLE_APK_REQUIRED'
  Run-Case 'wrong candidate package' { $state.candidatePackage='com.kelpie.browser.parallel' } 'APK_IDENTITY_MISMATCH'
  Run-Case 'reviewed source must match manifest' { $manifest.sourceCommit='b'*40 } 'CANDIDATE_SOURCE_MISMATCH'
  Run-Case 'candidate hash must match reviewed bytes' { $manifest.artifactSha256='b'*64 } 'APK_SHA256_MISMATCH'
  Run-Case 'cold restored data digest mismatch' { $evidence.coldRestore.dataAfterSha256='e'*64 } 'COLD_RESTORE_NOT_PROVEN'
  Run-Case 'rollback must prove downgrade with data retained' { $evidence.rollback.versionDowngradeVerified=$false } 'ROLLBACK_NOT_PROVEN'
  Run-Case 'recovery must bind exact candidate' { $evidence.candidateSha256='e'*64 } 'RECOVERY_CANDIDATE_MISMATCH'
  Run-Case 'changed evidence file' { 'tampered' | Set-Content (Join-Path $fixture 'record.txt') } 'EVIDENCE_FILE_CHANGED'
  Run-Case 'changed backup archive' { 'tampered' | Set-Content (Join-Path $fixture 'backup.bin') } 'EVIDENCE_FILE_CHANGED'
  Run-Case 'failed device privacy behavior' { $evidence.deviceAcceptance.checks.privateCaptureUsableAndRedacted=$false } 'DEVICE_ACCEPTANCE_GATE_MISSING'
  Run-Case 'wrong physical device' { $evidence.device.serialSha256='e'*64 } 'RECOVERY_DEVICE_MISMATCH'
  Run-Case 'stale recovery trial' { $evidence.coldRestore.testedUtc=[DateTimeOffset]::UtcNow.AddDays(-8).ToString('o') } 'RECOVERY_EVIDENCE_STALE'
  Run-Case 'same HEAD CI failure' { $ci.workflow_runs[0].conclusion='failure' } 'CI_NOT_GREEN'
  Run-Case 'test skip is not formatter guard' { $ci.workflow_runs[0].conclusion='skipped' } 'CI_NOT_GREEN'
  Run-Case 'missing CI workflow' { $ci.workflow_runs=@($ci.workflow_runs | Select-Object -Skip 1);$ci.total_count-- } 'REQUIRED_CI_WORKFLOW_MISSING'
  Run-Case 'candidate must come from exact CI run attempt' { $manifest.ciRunAttempt=2 } 'CANDIDATE_CI_RUN_MISMATCH'
  Run-Case 'install rechecks installed bytes' { $state.changeDuringPreflight=$true } 'INSTALLED_APK_CHANGED_DURING_PREFLIGHT'
  Run-Case 'signer check alone never GO' { $arguments.Phase='InspectSigner' } 'SIGNER_CHECK_ONLY'
  Run-Case 'failed install never reports success or auto rollback' { $state.installFails=$true } 'ADB_READ_OR_INSTALL_FAILED' 1
  Run-Case 'multiple Kelpie packages in user0' { $state.packages+=@('com.kelpie.browser.parallel') } 'SINGLE_EXISTING_KELPIE_USER0_REQUIRED'
  Run-Case 'package inventory refreshed immediately before install' { $state.inventoryChanges=$true } 'SINGLE_EXISTING_KELPIE_USER0_REQUIRED'
  Run-Case 'existing Termux is required without installation fallback' { $state.termuxInstalled=$false } 'EXISTING_TERMUX_USER0_REQUIRED'
  Run-Case 'insufficient data storage preserves current app' { $state.freeKiB=1 } 'INSUFFICIENT_DATA_STORAGE'
  Run-Case 'twenty tab switches required' { $evidence.deviceAcceptance.tabSwitchCycles=19 } 'PHYSICAL_CYCLE_GATE_NOT_PROVEN'
  Run-Case 'at least three distinct tabs required' { $evidence.deviceAcceptance.distinctTabs=2 } 'PHYSICAL_CYCLE_GATE_NOT_PROVEN'
  Run-Case 'twenty five fresh frames required' { $evidence.deviceAcceptance.freshFrames=24 } 'PHYSICAL_CYCLE_GATE_NOT_PROVEN'
  Run-Case 'fifty display create release cycles required' { $evidence.deviceAcceptance.displayCreateReleaseCycles=49 } 'PHYSICAL_CYCLE_GATE_NOT_PROVEN'
  Run-Case 'transport uses existing browser port' { $evidence.transport.browserPort=8420 } 'TRANSPORT_EVIDENCE_BINDING_MISMATCH'
  Run-Case 'readiness 503 cannot claim remote ready' { $evidence.transport.probeClassification='PROBE_READY_LOCAL_REMOTE_UNKNOWN' } 'PROBE_IS_NOT_REMOTE_TUNNEL_PROOF'
  Run-Case 'local health cannot prove remote authenticated transport' { $evidence.transport.remoteAuthenticatedJourney='UNKNOWN' } 'AUTHENTICATED_TRANSPORT_NOT_PROVEN'
  Run-Case 'fresh transport experiment required' { $evidence.transport.testedUtc=[DateTimeOffset]::UtcNow.AddMinutes(-16).ToString('o') } 'TRANSPORT_EVIDENCE_STALE'
  Write-Host 'PASS: 39 real-script negative/positive gate scenarios; all Android boundaries mocked.'
} finally {
  Remove-Item Function:\Get-Command -ErrorAction SilentlyContinue
  Remove-Item Function:\Invoke-RestMethod -ErrorAction SilentlyContinue
  Remove-Variable KelpieGuardFixture -Scope Global -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $fixture -Recurse -Force
}
