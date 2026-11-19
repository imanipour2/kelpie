# Existing R5 MotherNext 18463 only. Never auto-run from CI or use a parallel package.
# Read-only preflight/acceptance; installation requires reviewed recovery and the owner flag.
[CmdletBinding()]
param(
  [ValidateSet('Preflight','InspectSigner','Install','PostInstall')][string]$Phase='Preflight',
  [Parameter(Mandatory=$true)][string]$Apk,
  [Parameter(Mandatory=$true)][string]$CandidateManifest,
  [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{40}$')][string]$ReviewedSourceCommit,
  [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$ReviewedManifestSha256,
  [string]$RecoveryEvidence='',
  [string]$ReviewedEvidenceSha256='',
  [string]$RollbackApk='',
  [string]$Adb='C:\Android\platform-tools\adb.exe',
  [string]$SdkRoot='',
  # Kept for callers of the old script. A checkbox is never restore/rollback evidence.
  [switch]$OwnerBackupConfirmed,
  [switch]$OwnerAuthorizesInstall
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$pkg='com.kelpie.browser.r5mothernextverify'
$expectedCode=41
$expectedName='0.2.21-r5-wave1-qa'
function Stop-NoGo([string]$reason) { throw "BETA_INSTALL_NO_GO: $reason" }
function Require([bool]$condition,[string]$reason) { if (-not $condition) { Stop-NoGo $reason } }
function File-Sha256([string]$file) {
  Require (Test-Path -LiteralPath $file -PathType Leaf) 'EVIDENCE_FILE_MISSING'
  return (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Read-ReviewedJson([string]$file,[string]$reviewedHash) {
  Require ($reviewedHash -cmatch '^[0-9a-f]{64}$') 'REVIEWED_EVIDENCE_DIGEST_REQUIRED'
  Require ((File-Sha256 $file) -ceq $reviewedHash) 'REVIEWED_EVIDENCE_CHANGED'
  return Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
}
function Assert-EvidenceFile($record,[string]$baseDir) {
  Require ($record.sha256 -cmatch '^[0-9a-f]{64}$') 'EVIDENCE_DIGEST_INVALID'
  $path=[string]$record.path
  Require (-not [string]::IsNullOrWhiteSpace($path)) 'EVIDENCE_FILE_MISSING'
  if (-not [IO.Path]::IsPathRooted($path)) { $path=Join-Path $baseDir $path }
  Require ((File-Sha256 $path) -ceq $record.sha256) 'EVIDENCE_FILE_CHANGED'
  Require ((Get-Item -LiteralPath $path).Length -gt 0) 'EVIDENCE_FILE_EMPTY'
}
function FindBuildTool([string]$name) {
  # An explicit SDK root wins; otherwise use the owner's established tool on PATH.
  if (-not $SdkRoot) {
    $cmd=Get-Command $name -ErrorAction SilentlyContinue
    if ($null -ne $cmd) { return $cmd.Source }
  }
  $roots=@($SdkRoot,$env:ANDROID_HOME,$env:ANDROID_SDK_ROOT,"$env:LOCALAPPDATA\Android\Sdk",'C:\Android\Sdk')
  foreach($root in $roots) {
    if (-not $root -or -not (Test-Path -LiteralPath $root)) { continue }
    $dir=Join-Path $root 'build-tools'
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    $found=@(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter $name -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
    if ($found.Count -gt 0) { return $found[0].FullName }
  }
  Stop-NoGo 'ANDROID_BUILD_TOOL_MISSING'
}
function ApkSigner([string]$file) {
  $tool=FindBuildTool 'apksigner.bat'
  $output=(& $tool verify --verbose --print-certs $file 2>&1 | Out-String)
  Require ($LASTEXITCODE -eq 0) 'APK_SIGNATURE_INVALID'
  Require ($output -match 'Verified using v2 scheme[^\r\n]*:\s*true') 'APK_V2_SIGNATURE_REQUIRED'
  $matches=[regex]::Matches($output,'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
  Require ($matches.Count -eq 1) 'SINGLE_REVIEWED_SIGNER_REQUIRED'
  return $matches[0].Groups[1].Value.ToLowerInvariant()
}
function Apk-Identity([string]$file) {
  $tool=FindBuildTool 'aapt.exe'
  $badge=(& $tool dump badging $file 2>&1 | Out-String)
  Require ($LASTEXITCODE -eq 0) 'APK_IDENTITY_UNREADABLE'
  $match=[regex]::Match($badge,"(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
  Require ($match.Success) 'APK_IDENTITY_UNREADABLE'
  return @{ Package=$match.Groups[1].Value; Code=[int]$match.Groups[2].Value; Name=$match.Groups[3].Value }
}
function ADB([string[]]$argsList) {
  $output=(& $script:adbExe -s $script:device @argsList 2>&1 | Out-String).Trim()
  Require ($LASTEXITCODE -eq 0) 'ADB_READ_OR_INSTALL_FAILED'
  return $output
}
function Device-Sha256([string]$text) {
  $sha=[Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text)))).Replace('-','').ToLowerInvariant() }
  finally { $sha.Dispose() }
}
function Read-InstalledIdentity {
  Require ((ADB @('shell','getprop','ro.product.model')) -ceq 'SM-S928B') 'DEVICE_MODEL_MISMATCH'
  Require ((ADB @('shell','getprop','ro.build.version.release')) -ceq '17') 'ANDROID_RELEASE_MISMATCH'
  Require ((ADB @('shell','getprop','ro.build.version.sdk')) -ceq '37') 'ANDROID_API_MISMATCH'
  Require ((ADB @('shell','am','get-current-user')) -ceq '0') 'ANDROID_USER_MISMATCH'
  $inventory=ADB @('shell','pm','list','packages','--user','0','kelpie')
  $kelpiePackages=@(($inventory -split '\r?\n') | Where-Object { $_ -match '^package:' })
  Require ($kelpiePackages.Count -eq 1 -and $kelpiePackages[0] -ceq "package:$pkg") 'SINGLE_EXISTING_KELPIE_USER0_REQUIRED'
  $termux=ADB @('shell','pm','list','packages','--user','0','com.termux')
  Require (@(($termux -split '\r?\n') | Where-Object { $_ -ceq 'package:com.termux' }).Count -eq 1) 'EXISTING_TERMUX_USER0_REQUIRED'
  $paths=ADB @('shell','pm','path','--user','0',$pkg)
  $packages=@(($paths -split '\r?\n') | Where-Object { $_ -match '^package:' })
  Require ($packages.Count -eq 1) 'EXISTING_SINGLE_APK_REQUIRED_NO_PARALLEL_INSTALL'
  $base=$packages[0].Substring(8).Trim()
  Require ($base -cmatch '^/data/app/[A-Za-z0-9_~+=./-]+/base\.apk$') 'INSTALLED_BASE_APK_UNIDENTIFIED'
  $hashOutput=ADB @('shell','sha256sum',$base)
  $hashMatch=[regex]::Match($hashOutput,'^([0-9a-f]{64})\s')
  Require ($hashMatch.Success) 'INSTALLED_APK_DIGEST_UNREADABLE'
  $dump=ADB @('shell','dumpsys','package',$pkg)
  $code=[regex]::Match($dump,'(?m)versionCode=(\d+)')
  $name=[regex]::Match($dump,'(?m)versionName=([^\r\n]+)')
  Require ($code.Success -and $name.Success) 'INSTALLED_VERSION_UNREADABLE'
  return @{ Sha256=$hashMatch.Groups[1].Value; Code=[int]$code.Groups[1].Value; Name=$name.Groups[1].Value.Trim() }
}
function Assert-InstallStorage {
  $disk=ADB @('shell','df','-k','/data')
  $rows=@(($disk -split '\r?\n') | Where-Object { $_ -match '^/' })
  Require ($rows.Count -eq 1) 'DATA_STORAGE_UNKNOWN'
  $fields=@($rows[0].Trim() -split '\s+')
  Require ($fields.Count -ge 6 -and $fields[3] -match '^\d+$') 'DATA_STORAGE_UNKNOWN'
  $freeBytes=[long]$fields[3] * 1024L
  $requiredBytes=([long](Get-Item -LiteralPath $apkFile).Length * 2L) + 256MB
  Require ($freeBytes -ge $requiredBytes) 'INSUFFICIENT_DATA_STORAGE'
}
function Assert-CurrentCi($manifest) {
  $uri="https://api.github.com/repos/imanipour2/kelpie/actions/runs?head_sha=$ReviewedSourceCommit&per_page=100"
  $result=Invoke-RestMethod -Uri $uri -Headers @{Accept='application/vnd.github+json'} -TimeoutSec 30
  Require ($result.total_count -gt 0 -and $result.total_count -le 100) 'CI_RESULTS_INCOMPLETE'
  $runs=@($result.workflow_runs | Where-Object { $_.head_sha -ceq $ReviewedSourceCommit })
  Require ($runs.Count -eq $result.total_count) 'CI_HEAD_MISMATCH'
  $required=@('android.yml','macos.yml','lint.yml','qa-android-private-capture.yml','qa-capture-format-patch.yml',
    'qa-p4-parallel-slot.yml','qa-r5-background-agent.yml','qa-r5-ktlint-autofix.yml','qa-r5-mother-deploy-script.yml',
    'qa-r5-mother-sidecar.yml','qa-r5-private-beta.yml','qa-r5-source-slot.yml','qa-r5-upstream-connection.yml','qa-wave1-contracts.yml')
  $latest=@($runs | Group-Object path | ForEach-Object { $_.Group | Sort-Object id -Descending | Select-Object -First 1 })
  foreach ($workflow in $required) {
    Require (@($latest | Where-Object { $_.path -ceq ".github/workflows/$workflow" }).Count -eq 1) 'REQUIRED_CI_WORKFLOW_MISSING'
  }
  foreach ($run in $latest) {
    # This existing source formatter is branch-scoped automation, never a disabled test.
    $expectedSkip=$run.path -ceq '.github/workflows/qa-r5-ktlint-autofix.yml' -and
      $run.head_branch -cne 'feat/r5-background-agent-p4-runtime-20261116' -and $run.conclusion -ceq 'skipped'
    Require ($run.status -ceq 'completed' -and ($run.conclusion -ceq 'success' -or $expectedSkip)) 'CI_NOT_GREEN'
  }
  $producer=@($latest | Where-Object { $_.html_url -ceq $manifest.ciRunUrl })
  Require ($producer.Count -eq 1 -and $producer[0].run_attempt -eq $manifest.ciRunAttempt) 'CANDIDATE_CI_RUN_MISMATCH'
  Require ($producer[0].path -ceq $manifest.ciWorkflow) 'CANDIDATE_CI_WORKFLOW_MISMATCH'
}
function Assert-RecoveryEvidence($evidence,$installed,[string]$evidenceDir) {
  Require ($evidence.schemaVersion -eq 1 -and $evidence.sourceCommit -ceq $ReviewedSourceCommit) 'RECOVERY_SOURCE_MISMATCH'
  Require ($evidence.packageName -ceq $pkg -and $evidence.candidateSha256 -ceq $actual) 'RECOVERY_CANDIDATE_MISMATCH'
  Require ($evidence.device.serialSha256 -ceq (Device-Sha256 $script:device)) 'RECOVERY_DEVICE_MISMATCH'
  Require ($evidence.device.model -ceq 'SM-S928B' -and $evidence.device.androidRelease -ceq '17' -and
    $evidence.device.sdk -eq 37 -and $evidence.device.userId -eq 0) 'RECOVERY_DEVICE_PROFILE_MISMATCH'
  Require ($evidence.installed.artifactSha256 -ceq $installed.Sha256 -and
    $evidence.installed.signerCertificateSha256 -ceq $candidateCert -and
    $evidence.installed.versionCode -eq $installed.Code -and $evidence.installed.versionName -ceq $installed.Name) 'RECOVERY_INSTALLED_APK_MISMATCH'
  Require ($evidence.backup.captureMode -ceq 'COLD' -and $evidence.backup.appStopped -eq $true -and
    $evidence.backup.installedArtifactSha256 -ceq $installed.Sha256) 'COLD_BACKUP_REQUIRED'
  Assert-EvidenceFile $evidence.backup.archive $evidenceDir
  Assert-EvidenceFile $evidence.backup.record $evidenceDir
  Require ($evidence.coldRestore.status -ceq 'PASS' -and
    $evidence.coldRestore.backupSha256 -ceq $evidence.backup.archive.sha256 -and
    $evidence.coldRestore.dataBeforeSha256 -cmatch '^[0-9a-f]{64}$' -and
    $evidence.coldRestore.dataAfterSha256 -ceq $evidence.coldRestore.dataBeforeSha256) 'COLD_RESTORE_NOT_PROVEN'
  Assert-EvidenceFile $evidence.coldRestore.record $evidenceDir
  Require ($evidence.rollback.status -ceq 'PASS' -and
    $evidence.rollback.fromCandidateSha256 -ceq $actual -and
    $evidence.rollback.toInstalledArtifactSha256 -ceq $installed.Sha256 -and
    $evidence.rollback.versionDowngradeVerified -eq $true -and $evidence.rollback.retainsData -eq $true) 'ROLLBACK_NOT_PROVEN'
  Assert-EvidenceFile $evidence.rollback.record $evidenceDir
  foreach ($stamp in @($evidence.backup.createdUtc,$evidence.coldRestore.testedUtc,$evidence.rollback.testedUtc)) {
    $time=[DateTimeOffset]::Parse($stamp)
    Require ($time -le [DateTimeOffset]::UtcNow -and $time -ge [DateTimeOffset]::UtcNow.AddDays(-7)) 'RECOVERY_EVIDENCE_STALE'
  }
}
function Assert-PostInstallEvidence($evidence,[string]$evidenceDir) {
  Require ($null -ne $evidence.PSObject.Properties['deviceAcceptance']) 'DEVICE_ACCEPTANCE_NOT_PROVEN'
  Require ($evidence.deviceAcceptance.status -ceq 'PASS' -and
    $evidence.deviceAcceptance.sourceCommit -ceq $ReviewedSourceCommit -and
    $evidence.deviceAcceptance.candidateSha256 -ceq $actual) 'DEVICE_ACCEPTANCE_NOT_PROVEN'
  foreach ($check in @('controlPlaneNoGrant','grantSeparation','stopInflight','lockResumeEpoch',
    'tabTransferAck','documentIdentity','staleFrameRejected','privateCaptureUsableAndRedacted','lifecycle25Cycles','realBehavior',
    'rotation','homeResume','screenOffOn','processRestart','pairingPreserved','browserStateRestored','sensitiveActionsNeedHuman')) {
    Require ($evidence.deviceAcceptance.checks.$check -eq $true) 'DEVICE_ACCEPTANCE_GATE_MISSING'
  }
  foreach ($threshold in @(@('tabSwitchCycles',20),@('distinctTabs',3),@('freshFrames',25),@('displayCreateReleaseCycles',50))) {
    $measurement=$evidence.deviceAcceptance.($threshold[0])
    Require (($measurement -is [int] -or $measurement -is [long]) -and $measurement -ge $threshold[1]) 'PHYSICAL_CYCLE_GATE_NOT_PROVEN'
  }
  Assert-EvidenceFile $evidence.deviceAcceptance.record $evidenceDir
  $acceptanceTime=[DateTimeOffset]::Parse($evidence.deviceAcceptance.testedUtc)
  Require ($acceptanceTime -le [DateTimeOffset]::UtcNow -and
    $acceptanceTime -ge [DateTimeOffset]::UtcNow.AddDays(-7)) 'DEVICE_ACCEPTANCE_EVIDENCE_STALE'
  Require ($null -ne $evidence.PSObject.Properties['transport']) 'AUTHENTICATED_TRANSPORT_NOT_PROVEN'
  Require ($evidence.transport.sourceCommit -ceq $ReviewedSourceCommit -and
    $evidence.transport.candidateSha256 -ceq $actual -and $evidence.transport.browserPort -eq 18463 -and
    $evidence.transport.diagnosticPort -eq 8080 -and $evidence.transport.existingTunnelPreserved -eq $true) 'TRANSPORT_EVIDENCE_BINDING_MISMATCH'
  Require ($evidence.transport.remoteAuthenticatedJourney -ceq 'PASS' -and
    $evidence.transport.nativeConsentRoundTrip -ceq 'PASS') 'AUTHENTICATED_TRANSPORT_NOT_PROVEN'
  $classification=if ($evidence.transport.healthStatus -eq 200) {
    if ($evidence.transport.readyStatus -eq 200) { 'PROBE_READY_LOCAL_REMOTE_UNKNOWN' }
    elseif ($null -eq $evidence.transport.readyStatus) { 'PROBE_RUNNING_READY_UNKNOWN' }
    else { 'PROBE_DEGRADED_REMOTE_UNKNOWN' }
  } elseif ($null -eq $evidence.transport.healthStatus) { 'PROBE_UNAVAILABLE_REMOTE_UNKNOWN' }
  else { 'PROBE_UNHEALTHY_REMOTE_UNKNOWN' }
  Require ($evidence.transport.probeClassification -ceq $classification) 'PROBE_IS_NOT_REMOTE_TUNNEL_PROOF'
  $transportTime=[DateTimeOffset]::Parse($evidence.transport.testedUtc)
  Require ($transportTime -le [DateTimeOffset]::UtcNow -and
    $transportTime -ge [DateTimeOffset]::UtcNow.AddMinutes(-15)) 'TRANSPORT_EVIDENCE_STALE'
  Assert-EvidenceFile $evidence.transport.record $evidenceDir
}
try {
  $manifest=Read-ReviewedJson $CandidateManifest $ReviewedManifestSha256
  Require ($manifest.schemaVersion -eq 1 -and $manifest.sourceCommit -ceq $ReviewedSourceCommit) 'CANDIDATE_SOURCE_MISMATCH'
  Require ($manifest.repository -ceq 'https://github.com/imanipour2/kelpie' -and
    $manifest.mission -ceq 'EF-OPS-KELPIE-BETA-WAVE1-IMPLEMENT-001') 'CANDIDATE_MISSION_MISMATCH'
  Require ($manifest.packageName -ceq $pkg -and $manifest.versionCode -eq $expectedCode -and
    $manifest.versionName -ceq $expectedName -and $manifest.localHttpPort -eq 18463) 'CANDIDATE_IDENTITY_MISMATCH'
  Require ($manifest.buildVariant -ceq 'debug' -and $manifest.gradleProfile -ceq 'kelpieR5MotherNextQaProfile' -and
    $manifest.signingSchemeV2 -eq $true -and
    $manifest.installPolicy -ceq 'NO_AUTO_INSTALL_SINGLE_PACKAGE_SIGNER_MATCH_REQUIRED') 'CANDIDATE_BUILD_POLICY_MISMATCH'
  Require ([DateTimeOffset]::Parse($manifest.buildUtc) -le [DateTimeOffset]::UtcNow) 'CANDIDATE_BUILD_TIME_INVALID'
  $expectedApkHash=[string]$manifest.artifactSha256
  $expectedCert=[string]$manifest.signerCertificateSha256
  Require ($expectedApkHash -cmatch '^[0-9a-f]{64}$' -and $expectedCert -cmatch '^[0-9a-f]{64}$') 'CANDIDATE_DIGEST_INVALID'
  $apkFile=(Resolve-Path -LiteralPath $Apk).Path
  $actual=File-Sha256 $apkFile
  Require ($actual -ceq $expectedApkHash) 'APK_SHA256_MISMATCH'
  $identity=Apk-Identity $apkFile
  Require ($identity.Package -ceq $pkg -and $identity.Code -eq $expectedCode -and $identity.Name -ceq $expectedName) 'APK_IDENTITY_MISMATCH'
  $candidateCert=ApkSigner $apkFile
  Require ($candidateCert -ceq $expectedCert) 'CANDIDATE_CERTIFICATE_MISMATCH'
  if (Test-Path -LiteralPath $Adb -PathType Leaf) { $script:adbExe=(Resolve-Path -LiteralPath $Adb).Path }
  else {
    $adbCmd=Get-Command adb -ErrorAction SilentlyContinue
    Require ($null -ne $adbCmd) 'ADB_UNAVAILABLE'
    $script:adbExe=$adbCmd.Source
  }
  $devices=@(& $script:adbExe devices -l)
  Require ($LASTEXITCODE -eq 0) 'ADB_DEVICES_FAILED'
  $ready=@($devices | Where-Object { $_ -match '^\S+\s+device(\s|$)' })
  Require ($ready.Count -eq 1) 'EXACTLY_ONE_AUTHORIZED_DEVICE_REQUIRED'
  $script:device=($ready[0] -split '\s+')[0]
  $installed=Read-InstalledIdentity
  $evidence=$null
  if ($Phase -eq 'PostInstall') {
    Require ($installed.Sha256 -ceq $actual -and $installed.Code -eq $expectedCode -and
      $installed.Name -ceq $expectedName) 'POST_INSTALL_CANDIDATE_NOT_INSTALLED'
    Require (-not [string]::IsNullOrWhiteSpace($RecoveryEvidence)) 'RECOVERY_EVIDENCE_REQUIRED_NOT_OWNER_BACKUP_CHECKBOX'
    $evidence=Read-ReviewedJson $RecoveryEvidence $ReviewedEvidenceSha256
    # Schema v1 keeps installed as the ORIGINAL baseline after an in-place update.
    $baseline=@{Sha256=$evidence.installed.artifactSha256;Code=$evidence.installed.versionCode;Name=$evidence.installed.versionName}
    Require ($baseline.Code -lt $expectedCode) 'ORIGINAL_ROLLBACK_BASELINE_REQUIRED'
  } else {
    Assert-InstallStorage
    Require ($installed.Code -lt $expectedCode) 'DOWNGRADE_OR_REINSTALL_REFUSED'
    $baseline=$installed
  }
  Require (-not [string]::IsNullOrWhiteSpace($RollbackApk)) 'LOCAL_ROLLBACK_APK_REQUIRED_NO_ADB_PULL'
  $rollbackFile=(Resolve-Path -LiteralPath $RollbackApk).Path
  Require ((File-Sha256 $rollbackFile) -ceq $baseline.Sha256) 'ROLLBACK_APK_NOT_INSTALLED_BYTES'
  $oldIdentity=Apk-Identity $rollbackFile
  Require ($oldIdentity.Package -ceq $pkg -and $oldIdentity.Code -eq $baseline.Code -and
    $oldIdentity.Name -ceq $baseline.Name) 'ROLLBACK_APK_IDENTITY_MISMATCH'
  Require ((ApkSigner $rollbackFile) -ceq $candidateCert) 'SIGNER_MISMATCH_DO_NOT_UNINSTALL'
  Write-Host 'SIGNER_MATCH: local rollback APK equals the original baseline; candidate signer is identical.'
  Write-Host "CANDIDATE: source $ReviewedSourceCommit / APK SHA256 $actual / package $pkg / port 18463"
  if ($Phase -eq 'InspectSigner') { Stop-NoGo 'SIGNER_CHECK_ONLY_RECOVERY_AND_RUNTIME_GATES_NOT_RUN' }
  Require (-not [string]::IsNullOrWhiteSpace($RecoveryEvidence)) 'RECOVERY_EVIDENCE_REQUIRED_NOT_OWNER_BACKUP_CHECKBOX'
  if ($null -eq $evidence) { $evidence=Read-ReviewedJson $RecoveryEvidence $ReviewedEvidenceSha256 }
  $evidenceDir=Split-Path -Parent (Resolve-Path -LiteralPath $RecoveryEvidence).Path
  Assert-RecoveryEvidence $evidence $baseline $evidenceDir
  if ($Phase -eq 'PostInstall') { Assert-PostInstallEvidence $evidence $evidenceDir }
  Assert-CurrentCi $manifest
  if ($Phase -eq 'Install') { Require $OwnerAuthorizesInstall.IsPresent 'OWNER_AUTHORIZATION_REQUIRED' }
  # Re-read identity and artifact digests immediately before any possible write.
  $current=Read-InstalledIdentity
  if ($Phase -ne 'PostInstall') { Assert-InstallStorage }
  Require ($current.Sha256 -ceq $installed.Sha256 -and $current.Code -eq $installed.Code -and
    $current.Name -ceq $installed.Name) 'INSTALLED_APK_CHANGED_DURING_PREFLIGHT'
  Require ((File-Sha256 $apkFile) -ceq $actual -and (File-Sha256 $rollbackFile) -ceq $baseline.Sha256 -and
    (File-Sha256 $CandidateManifest) -ceq $ReviewedManifestSha256 -and
    (File-Sha256 $RecoveryEvidence) -ceq $ReviewedEvidenceSha256) 'EVIDENCE_CHANGED_DURING_PREFLIGHT'
  # Revalidate files/freshness after CI; an index digest alone does not freeze its referenced files.
  Assert-RecoveryEvidence $evidence $baseline $evidenceDir
  if ($Phase -eq 'PostInstall') {
    Assert-PostInstallEvidence $evidence $evidenceDir
    Write-Host 'POST_INSTALL_ACCEPTANCE_PASS: installed candidate, CI and reviewed recovery/device/transport records agree; read-only.'
    return
  }
  if ($Phase -eq 'Preflight') {
    Write-Host 'PREFLIGHT_READY: pre-install candidate, signer, CI and recovery checks passed; separate owner install authorization remains required.'
    Write-Host 'POST_INSTALL_ACCEPTANCE_PENDING: candidate behavior, transport and native consent are checked after installation.'
    return
  }
  # The only device mutation in this file. No uninstall, data reset, port or tunnel command.
  $installOutput=ADB @('install','-r','-t',$apkFile)
  Require ($installOutput -match '^Success\s*$') 'INSTALL_FAILED_AFTER_AUTHORIZATION_PRESERVE_RECOVERY'
  $after=Read-InstalledIdentity
  Require ($after.Sha256 -ceq $actual -and $after.Code -eq $expectedCode -and $after.Name -ceq $expectedName) 'INSTALLED_READBACK_FAILED_DO_NOT_AUTO_ROLLBACK'
  Write-Host 'INSTALLED_PENDING_ACCEPTANCE: same package updated and APK digest read back; run read-only PostInstall with reviewed candidate behavior/transport evidence.'
} catch {
  $reason='BETA_INSTALL_NO_GO: UNVERIFIED_PREFLIGHT_OR_INSTALL_STATE'
  if ($_.Exception.Message.StartsWith('BETA_INSTALL_NO_GO:')) { $reason=$_.Exception.Message }
  throw $reason
}
