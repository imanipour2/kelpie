#requires -Version 5.1
<#
.SYNOPSIS
Re-sign a verified MotherNext CI debug APK with this Windows user's stable private key.
.DESCRIPTION
Offline packaging only. Does not install, contact a device, or publish a release.
See docs/qa/private-owner-debug-signing.md. The CI manifest checksum sidecar is required.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$BuildToolsPath,
    [Parameter(Mandatory = $true)][string]$InputApk,
    [Parameter(Mandatory = $true)][string]$CIManifest,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$ExpectedCommit,
    [Parameter(Mandatory = $true)][string]$OutputDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Resolve-File([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    Require (-not $item.PSIsContainer) "Expected a file: $Path"
    return $item.FullName
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-BytesHash([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Quote-NativeArgument([string]$Value) {
    # Windows CommandLineToArgvW escaping; never invoke a command shell.
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    return '"' + [regex]::Replace($escaped, '(\\+)$', '$1$1') + '"'
}

function Invoke-Tool([string]$Exe, [string[]]$Arguments, [string]$Password = '') {
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $Exe
    $start.Arguments = (($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    if ($Password) { $start.EnvironmentVariables['KELPIE_OWNER_SIGNING_PASSWORD'] = $Password }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $result = $stdout.GetAwaiter().GetResult() + "`n" + $stderr.GetAwaiter().GetResult()
        Require ($process.ExitCode -eq 0) ("{0} failed (exit {1})." -f [IO.Path]::GetFileName($Exe), $process.ExitCode)
        return $result.Trim()
    } finally {
        $start.EnvironmentVariables.Remove('KELPIE_OWNER_SIGNING_PASSWORD')
        $process.Dispose()
    }
}

function Get-ApkSigner([string]$Path) {
    $report = Invoke-Tool $script:java @('-jar', $script:apksigner, 'verify', '--verbose', '--print-certs', $Path)
    $schemes = @([regex]::Matches($report, '(?m)^Verified using v[1-4] scheme[^\r\n]*: (?:true|false)\s*$') | ForEach-Object { $_.Value.Trim() }) -join '; '
    Require ($report -match '(?m)^Verified using v2 scheme.*: true\s*$') ("APK v2 signature verification failed for {0}. {1}" -f [IO.Path]::GetFileName($Path), $schemes)
    $certs = [regex]::Matches($report, '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$')
    Require ($certs.Count -eq 1) 'Exactly one APK signing certificate is required.'
    return $certs[0].Groups[1].Value.ToLowerInvariant()
}

function Assert-ApkIdentity([string]$Path, $Manifest) {
    $badging = Invoke-Tool $script:aapt @('dump', 'badging', $Path)
    $identity = [regex]::Match($badging, "(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
    Require ($identity.Success) 'aapt did not return an APK identity.'
    Require ($identity.Groups[1].Value -ceq $Manifest.packageName -and
        $identity.Groups[2].Value -ceq [string]$Manifest.versionCode -and
        $identity.Groups[3].Value -ceq $Manifest.versionName) 'APK package/version differs from the CI manifest.'
    Require ($badging -match '(?m)^application-debuggable\s*$') 'Only a debuggable QA APK may use this private signing helper.'
}

function Get-PayloadHashes([string]$Path) {
    # Preserve dependency metadata: exclude only JAR signature entries, not all META-INF.
    $signatureEntry = '(?i)^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC)|SIG-[^/]+)$'
    $hashes = New-Object 'Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match $signatureEntry) { continue }
            Require (-not $hashes.ContainsKey($entry.FullName)) 'Duplicate APK payload entry.'
            $stream = $entry.Open()
            $sha = [Security.Cryptography.SHA256]::Create()
            try {
                $digest = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
                $hashes.Add($entry.FullName, ([string]$entry.Length + ':' + $digest))
            } finally { $sha.Dispose(); $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    return ,$hashes
}

function Assert-PayloadEqual($Before, $After) {
    Require ($Before.Count -eq $After.Count) 'Signing changed the APK payload entry count.'
    foreach ($name in $Before.Keys) {
        Require ($After.ContainsKey($name) -and $After[$name] -ceq $Before[$name]) "Signing changed APK payload: $name"
    }
}

function Set-PrivateAccess([string]$Path, [bool]$Directory) {
    $item = Get-Item -LiteralPath $Path -Force
    Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'Signing state must not be a reparse point.'
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $system = New-Object Security.Principal.SecurityIdentifier 'S-1-5-18'
    if ($Directory) {
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    } else {
        $acl = New-Object Security.AccessControl.FileSecurity
        $inherit = [Security.AccessControl.InheritanceFlags]::None
    }
    $existingOwner = $item.GetAccessControl([Security.AccessControl.AccessControlSections]::Owner).GetOwner([Security.Principal.SecurityIdentifier])
    Require ($existingOwner.Value -eq $sid.Value) 'Signing state must already belong to the current Windows user.'
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($principal in @($sid, $system)) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($principal, 'FullControl', $inherit, 'None', 'Allow')
        [void]$acl.AddAccessRule($rule)
    }
    # Persist only the modified DACL. PowerShell's Set-Acl provider can request
    # SACL privileges on a repeated write; signing needs no audit/owner changes.
    if ($Directory) { [IO.Directory]::SetAccessControl($Path, $acl) }
    else { [IO.File]::SetAccessControl($Path, $acl) }
    $readback = Get-Acl -LiteralPath $Path
    $rules = @($readback.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    Require ($readback.AreAccessRulesProtected -and $rules.Count -eq 2) 'Signing state ACL verification failed.'
    foreach ($rule in $rules) {
        Require ($rule.IdentityReference.Value -in @($sid.Value, $system.Value) -and
            $rule.AccessControlType -eq 'Allow' -and $rule.FileSystemRights -eq 'FullControl') 'Unexpected signing state permission.'
    }
}

function Get-KeyCertificate([string]$KeyPath, [string]$Password) {
    # Only the public X.509 certificate is exported, directly into memory.
    $public = Invoke-Tool $script:keytool @('-exportcert', '-rfc', '-alias', 'kelpie-private-owner-debug',
        '-keystore', $KeyPath, '-storetype', 'PKCS12', '-storepass:env', 'KELPIE_OWNER_SIGNING_PASSWORD') $Password
    $pem = [regex]::Match($public, '(?s)-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----')
    Require ($pem.Success) 'Cannot read the owner signing certificate.'
    return Get-BytesHash ([Convert]::FromBase64String($pem.Groups[1].Value))
}

function Open-OwnerKey([string]$SigningDir) {
    $identityDir = Join-Path $SigningDir 'owner-debug'
    $pending = @(Get-ChildItem -LiteralPath $SigningDir -Force | Where-Object { $_.Name -like '.pending-*' })
    Require ($pending.Count -eq 0) 'Incomplete owner-key generation exists. Preserve it for review; no replacement key was created.'
    $entropy = [Text.Encoding]::UTF8.GetBytes('KelpieConnect/private-owner-debug/v1')
    $scope = [Security.Cryptography.DataProtectionScope]::CurrentUser
    if (-not (Test-Path -LiteralPath $identityDir)) {
        $stage = Join-Path $SigningDir ('.pending-' + [Guid]::NewGuid().ToString('N'))
        [void][IO.Directory]::CreateDirectory($stage)
        Set-PrivateAccess $stage $true
        $random = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $plain = $null
        try {
            $rng.GetBytes($random)
            $password = [Convert]::ToBase64String($random)
            $plain = [Text.Encoding]::UTF8.GetBytes($password)
            $protected = [Security.Cryptography.ProtectedData]::Protect($plain, $entropy, $scope)
            [IO.File]::WriteAllBytes((Join-Path $stage 'password.dpapi'), $protected)
            $key = Join-Path $stage 'owner-debug.p12'
            [void](Invoke-Tool $script:keytool @('-genkeypair', '-noprompt', '-alias', 'kelpie-private-owner-debug',
                '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA', '-validity', '10000',
                '-dname', 'CN=Kelpie Private Owner Debug', '-storetype', 'PKCS12', '-keystore', $key,
                '-storepass:env', 'KELPIE_OWNER_SIGNING_PASSWORD', '-keypass:env', 'KELPIE_OWNER_SIGNING_PASSWORD') $password)
            $fingerprint = Get-KeyCertificate $key $password
            [IO.File]::WriteAllText((Join-Path $stage 'certificate-sha256.txt'), $fingerprint + "`n")
            foreach ($file in Get-ChildItem -LiteralPath $stage -File) { Set-PrivateAccess $file.FullName $false }
            # Publish a complete identity atomically; never replace an existing identity.
            [IO.Directory]::Move($stage, $identityDir)
        } finally {
            if ($plain) { [Array]::Clear($plain, 0, $plain.Length) }
            [Array]::Clear($random, 0, $random.Length)
            $password = $null
            $rng.Dispose()
            if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
        }
    }
    Require (Test-Path -LiteralPath $identityDir -PathType Container) 'Owner signing identity is not a directory.'
    Set-PrivateAccess $identityDir $true
    $key = Join-Path $identityDir 'owner-debug.p12'
    $passwordFile = Join-Path $identityDir 'password.dpapi'
    $certificateFile = Join-Path $identityDir 'certificate-sha256.txt'
    foreach ($file in @($key, $passwordFile, $certificateFile)) {
        Require (Test-Path -LiteralPath $file -PathType Leaf) 'Incomplete owner signing identity. No key or password was replaced.'
        Set-PrivateAccess $file $false
    }
    $plain = $null
    try {
        $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($passwordFile), $entropy, $scope)
        $password = [Text.Encoding]::UTF8.GetString($plain)
    } catch { throw 'Owner password cannot be decrypted by this Windows user. No signing identity was replaced.' }
    finally { if ($plain) { [Array]::Clear($plain, 0, $plain.Length) } }
    $fingerprint = Get-KeyCertificate $key $password
    $pinned = [IO.File]::ReadAllText($certificateFile).Trim()
    Require ($pinned -cmatch '^[0-9a-f]{64}$' -and $fingerprint -ceq $pinned) 'Owner certificate differs from its stored fingerprint.'
    return @{ Path = $key; Password = $password; Fingerprint = $fingerprint }
}

Require ($env:OS -eq 'Windows_NT') 'Run this helper on Windows under the owner account.'
Require ($PSVersionTable.PSEdition -eq 'Desktop') 'Use Windows PowerShell 5.1 (powershell.exe) for this signing recipe.'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Security
$script:java = Resolve-File (Join-Path $JavaHome 'bin\java.exe')
$script:keytool = Resolve-File (Join-Path $JavaHome 'bin\keytool.exe')
$script:apksigner = Resolve-File (Join-Path $BuildToolsPath 'lib\apksigner.jar')
$script:aapt = Resolve-File (Join-Path $BuildToolsPath 'aapt.exe')
$zipalign = Resolve-File (Join-Path $BuildToolsPath 'zipalign.exe')
$sdkProperties = Resolve-File (Join-Path $BuildToolsPath 'source.properties')
$InputApk = Resolve-File $InputApk
$CIManifest = Resolve-File $CIManifest
$checksumFile = Resolve-File ($CIManifest + '.sha256')
$OutputDir = [IO.Path]::GetFullPath($OutputDir)
Require (-not (Test-Path -LiteralPath $OutputDir)) 'OutputDir must be new; existing output is never overwritten.'
$ExpectedCommit = $ExpectedCommit.ToLowerInvariant()
$inputLock = $null
$manifestLock = $null
$keyLock = $null
$work = $null
$ownerKey = $null
try {
    # Keep the validated inputs unchanged while external tools read them.
    $inputLock = [IO.File]::Open($InputApk, 'Open', 'Read', 'Read')
    $manifestLock = [IO.File]::Open($CIManifest, 'Open', 'Read', 'Read')
    $manifestHash = Get-Sha256 $CIManifest
    $checksum = [regex]::Match([IO.File]::ReadAllText($checksumFile).Trim(), '^([0-9a-fA-F]{64})\s+\*?([^\r\n]+)$')
    Require ($checksum.Success -and $checksum.Groups[1].Value.ToLowerInvariant() -ceq $manifestHash -and
        $checksum.Groups[2].Value -ceq [IO.Path]::GetFileName($CIManifest)) 'CI manifest checksum sidecar mismatch.'
    $manifestText = [IO.File]::ReadAllText($CIManifest)
    $manifest = $manifestText | ConvertFrom-Json
    Require ($manifest.schemaVersion -eq 1 -and $manifest.sourceCommit -ceq $ExpectedCommit) 'CI schema/source commit mismatch.'
    Require ($manifest.repository -ceq 'https://github.com/imanipour2/kelpie' -and
        $manifest.ciWorkflow -ceq '.github/workflows/qa-r5-mother-sidecar.yml' -and
        $manifest.ciRunUrl -cmatch '^https://github\.com/imanipour2/kelpie/actions/runs/[0-9]+$') 'Unexpected CI provenance.'
    Require ($manifest.packageName -ceq 'com.kelpie.browser.r5mothernextverify' -and
        $manifest.localHttpPort -eq 18463 -and $manifest.gradleProfile -ceq 'kelpieR5MotherNextQaProfile' -and
        $manifest.buildVariant -ceq 'debug' -and $manifest.signingSchemeV2 -eq $true) 'Unexpected QA profile/package/port/signing scheme.'
    Require ($manifest.artifactFile -ceq [IO.Path]::GetFileName($InputApk) -and
        [string]$manifest.artifactSha256 -cmatch '^[0-9a-f]{64}$' -and
        [string]$manifest.signerCertificateSha256 -cmatch '^[0-9a-f]{64}$') 'Malformed CI artifact identity.'
    $sourceHash = Get-Sha256 $InputApk
    Require ($sourceHash -ceq $manifest.artifactSha256) 'Input APK hash differs from the CI manifest.'
    Require ((Get-ApkSigner $InputApk) -ceq $manifest.signerCertificateSha256) 'Input APK signer differs from the CI manifest.'
    Assert-ApkIdentity $InputApk $manifest
    $payloadBefore = Get-PayloadHashes $InputApk
    $javaVersion = Invoke-Tool $script:java @('-version')
    Require ($javaVersion -match 'version "17[.\"]') 'Java 17 is required for this signing recipe.'
    $buildTools = [regex]::Match([IO.File]::ReadAllText($sdkProperties), '(?m)^Pkg\.Revision\s*=\s*(35\.\d+\.\d+)\s*$')
    Require ($buildTools.Success) 'Android SDK Build Tools 35.x is required.'
    $apksignerVersion = Invoke-Tool $script:java @('-jar', $script:apksigner, 'version')

    $localData = [Environment]::GetFolderPath('LocalApplicationData')
    Require (-not [string]::IsNullOrWhiteSpace($localData)) 'No per-user LocalApplicationData directory is available.'
    $ownerDir = Join-Path $localData 'KelpieConnect'
    [void][IO.Directory]::CreateDirectory($ownerDir)
    Require (((Get-Item -LiteralPath $ownerDir).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'KelpieConnect must not be a reparse point.'
    $signingDir = Join-Path $ownerDir 'signing'
    [void][IO.Directory]::CreateDirectory($signingDir)
    Set-PrivateAccess $signingDir $true
    $lockPath = Join-Path $signingDir 'signing.lock'
    if (Test-Path -LiteralPath $lockPath) { Set-PrivateAccess $lockPath $false }
    $keyLock = [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    $ownerKey = Open-OwnerKey $signingDir

    $parent = [IO.Path]::GetDirectoryName($OutputDir)
    [void][IO.Directory]::CreateDirectory($parent)
    $work = Join-Path $parent ('.kelpie-signing-' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($work)
    $aligned = Join-Path $work 'aligned-input.apk'
    $signedName = 'kelpie-private-owner-debug.apk'
    $signedApk = Join-Path $work $signedName
    [void](Invoke-Tool $zipalign @('-P', '16', '-f', '4', $InputApk, $aligned))
    [void](Invoke-Tool $script:java @('-jar', $script:apksigner, 'sign', '--ks', $ownerKey.Path,
        '--ks-type', 'PKCS12', '--ks-key-alias', 'kelpie-private-owner-debug',
        '--ks-pass', 'env:KELPIE_OWNER_SIGNING_PASSWORD', '--key-pass', 'env:KELPIE_OWNER_SIGNING_PASSWORD',
        '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false',
        '--v4-signing-enabled', 'false', '--out', $signedApk, $aligned) $ownerKey.Password)
    Require ((Get-ApkSigner $signedApk) -ceq $ownerKey.Fingerprint) 'Signed APK certificate differs from the stable owner key.'
    [void](Invoke-Tool $zipalign @('-c', '-P', '16', '-v', '4', $signedApk))
    Assert-ApkIdentity $signedApk $manifest
    Assert-PayloadEqual $payloadBefore (Get-PayloadHashes $signedApk)
    Require ((Get-Sha256 $InputApk) -ceq $sourceHash -and (Get-Sha256 $CIManifest) -ceq $manifestHash) 'An input changed during signing.'
    $outputHash = Get-Sha256 $signedApk
    $signedManifest = [ordered]@{
        schemaVersion = 1
        artifactClass = 'PRIVATE_OWNER_DEBUG'
        productionRelease = $false
        signedUtc = [DateTime]::UtcNow.ToString('o')
        sourceCommit = $ExpectedCommit
        packageName = $manifest.packageName
        versionCode = $manifest.versionCode
        versionName = $manifest.versionName
        localHttpPort = $manifest.localHttpPort
        portEvidence = 'CI_GENERATED_BUILDCONFIG_BOUND_TO_INPUT_APK_SHA256'
        sourceCIArtifactSha256 = $sourceHash
        sourceCIManifestSha256 = $manifestHash
        sourceCISignerCertificateSha256 = $manifest.signerCertificateSha256
        sourceCIManifest = $manifest
        artifactFile = $signedName
        artifactSha256 = $outputHash
        signerCertificateSha256 = $ownerKey.Fingerprint
        signingIdentity = 'WINDOWS_CURRENT_USER_STABLE_PRIVATE_DEBUG'
        signingSchemeV2 = $true
        signingScheme = 'APK_V2_ONLY'
        payloadUnchangedExceptSignatureEntries = $true
        verifiedPayloadEntryCount = $payloadBefore.Count
        zipAlignment = '4_BYTE_WITH_16_KIB_UNCOMPRESSED_NATIVE_LIBRARIES'
        javaVersion = $javaVersion
        androidBuildToolsVersion = $buildTools.Groups[1].Value
        apksignerVersion = $apksignerVersion
        helperSha256 = Get-Sha256 $PSCommandPath
        installation = 'NOT_PERFORMED'
        deviceAcceptance = 'NOT_PERFORMED'
    }
    $utf8 = New-Object Text.UTF8Encoding $false
    [IO.File]::WriteAllText((Join-Path $work 'private-owner-debug-manifest.json'), (($signedManifest | ConvertTo-Json -Depth 20) + "`n"), $utf8)
    [IO.File]::Copy($CIManifest, (Join-Path $work 'source-ci-manifest.json'))
    [IO.File]::WriteAllText((Join-Path $work ($signedName + '.sha256')), "$outputHash  $signedName`n", $utf8)
    $localManifestHash = Get-Sha256 (Join-Path $work 'private-owner-debug-manifest.json')
    [IO.File]::WriteAllText((Join-Path $work 'private-owner-debug-manifest.json.sha256'), "$localManifestHash  private-owner-debug-manifest.json`n", $utf8)
    Remove-Item -LiteralPath $aligned
    [IO.Directory]::Move($work, $OutputDir)
    $work = $null
    [pscustomobject]@{ Result = 'PRIVATE_OWNER_DEBUG_SIGNED'; OutputDir = $OutputDir; SourceCommit = $ExpectedCommit;
        ArtifactSha256 = $outputHash; SignerCertificateSha256 = $ownerKey.Fingerprint; PayloadUnchanged = $true }
} finally {
    if ($ownerKey) { $ownerKey.Password = $null }
    if ($work -and (Test-Path -LiteralPath $work)) { Remove-Item -LiteralPath $work -Recurse -Force }
    if ($keyLock) { $keyLock.Dispose() }
    if ($manifestLock) { $manifestLock.Dispose() }
    if ($inputLock) { $inputLock.Dispose() }
}
