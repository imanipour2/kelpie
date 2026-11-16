# Read-only Windows/ADB/R5 preflight. NEVER installs, deletes, signs or uploads anything.
# PowerShell 7: pwsh -NoProfile -File scripts/qa/p4-s24-r5-preflight.ps1
[CmdletBinding()]
param(
    [switch] $SelfTest,
    [string] $WorktreePath = 'C:\Enerfoam-Kelpie-QA\agent-runtime-local-20261116',
    [string] $EvidenceDirectory = '',
    [string] $DeviceSerial = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$expected = @{
    Model = 'SM-S928B'
    Head = '0a49477b0bd584b7ae17474d0e0abf3913187f19'
    Base = '7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee'
    ZipHash = '8cc90b3b433dc91c63e801c9e63b0e33d3865afb63137b1a41995bd4bb02a2f3'
    PatchHash = 'e2f6091e916ebc21f588d88ba8034d3f57994f3bf99748de752b897fd31cea74'
}

function Get-AuthorizedDeviceIds {
    param([string] $Listing)
    $ids = @(
        foreach ($line in [regex]::Split($Listing, '\r?\n')) {
            if ($line -match '^(?<id>[^\s]+)\s+device(?:\s|$)') {
                $Matches['id']
            }
        }
    )
    # Emit individual entries so an empty device set stays an empty array.
    return $ids
}

function Invoke-ReadOnly {
    param(
        [string] $Executable,
        [string[]] $Arguments
    )
    $output = & $Executable @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Read-only command failed (exit $LASTEXITCODE): $Executable"
    }
    return ($output | Out-String).Trim()
}

function Get-Sha256 {
    param([string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

if ($SelfTest) {
    $sample = "List of devices attached" + [Environment]::NewLine +
        "R5CX141NX1B device product:xx model:SM-S928B" + [Environment]::NewLine +
        "UNAUTHORIZED unauthorized" + [Environment]::NewLine
    $parsed = @(Get-AuthorizedDeviceIds -Listing $sample)
    if ($parsed.Count -ne 1 -or $parsed[0] -ne 'R5CX141NX1B') {
        throw 'ADB device parser self-test failed'
    }
    if (@(Get-AuthorizedDeviceIds -Listing "List of devices attached").Count -ne 0) {
        throw 'Empty-device parser self-test failed'
    }
    foreach ($key in @('Head', 'Base', 'ZipHash', 'PatchHash')) {
        if ($expected[$key] -notmatch '^[0-9a-f]{40}$' -and
            $expected[$key] -notmatch '^[0-9a-f]{64}$') {
            throw "Invalid expected source/hash length: $key"
        }
    }
    Write-Output 'SELF_TEST_PASS: no ADB, installation, device or repository was accessed.'
    exit 0
}

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'kelpie-p4-readonly-preflight'
}
$report = [ordered]@{
    purpose = 'EF-OPS-KELPIE-P4-S24-R5-READ-ONLY-PREFLIGHT'
    observedAtUtc = [DateTime]::UtcNow.ToString('o')
    deviceStatus = 'NOT_CHECKED'
    model = $null
    androidRelease = $null
    androidApi = $null
    deviceSerialSuffix = $null
    packages = @{}
    localR5Status = 'NOT_CHECKED'
    localR5Head = $null
    expectedHeadMatches = $false
    worktreeClean = $false
    p4BaseIsAncestor = $false
    zipShaMatches = $false
    patchShaMatches = $false
    issues = @()
    safeForInstall = $false
    releaseAuthorized = $false
}

try {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adbCmd) {
        foreach ($candidate in @(
            'C:\Enerfoam-Kelpie-QA\platform-tools\adb.exe',
            'C:\platform-tools\adb.exe'
        )) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $adbCmd = @{ Source = $candidate }
                break
            }
        }
    }
    if ($null -eq $adbCmd) { throw 'ADB_NOT_FOUND: add Android platform-tools to PATH or configured known path' }
    $adb = $adbCmd.Source
    $devicesText = Invoke-ReadOnly -Executable $adb -Arguments @('devices', '-l')
    $authorized = @(Get-AuthorizedDeviceIds -Listing $devicesText)
    if ($DeviceSerial) {
        if ($DeviceSerial -notin $authorized) { throw 'REQUESTED_DEVICE_NOT_AUTHORIZED' }
        $serial = $DeviceSerial
    } elseif ($authorized.Count -eq 1) {
        $serial = $authorized[0]
    } elseif ($authorized.Count -eq 0) {
        throw 'NO_AUTHORIZED_ADB_DEVICE: check USB debugging, the phone confirmation dialog and USB data mode'
    } else {
        throw 'MULTIPLE_ADB_DEVICES: rerun with -DeviceSerial to choose intended S24'
    }
    $report.deviceStatus = 'ADB_AUTHORIZED'
    $report.deviceSerialSuffix = $serial.Substring([Math]::Max(0, $serial.Length - 4))
    $report.model = Invoke-ReadOnly -Executable $adb -Arguments @('-s', $serial, 'shell', 'getprop', 'ro.product.model')
    $report.androidRelease = Invoke-ReadOnly -Executable $adb -Arguments @('-s', $serial, 'shell', 'getprop', 'ro.build.version.release')
    $report.androidApi = Invoke-ReadOnly -Executable $adb -Arguments @('-s', $serial, 'shell', 'getprop', 'ro.build.version.sdk')
    if ($report.model -ne $expected.Model) { throw 'MODEL_MISMATCH: not the approved Samsung SM-S928B' }
    foreach ($package in @('com.kelpie.browser.unifiedverify', 'com.kelpie.browser.pcverify')) {
        $apkPath = Invoke-ReadOnly -Executable $adb -Arguments @('-s', $serial, 'shell', 'pm', 'path', $package)
        $report.packages[$package] = ($apkPath -match '^package:')
    }

    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($null -eq $gitCmd) { throw 'GIT_NOT_FOUND' }
    if (-not (Test-Path -LiteralPath $WorktreePath -PathType Container)) {
        throw 'R5_WORKTREE_NOT_FOUND: do not substitute older Drive source'
    }
    $git = $gitCmd.Source
    $head = Invoke-ReadOnly -Executable $git -Arguments @('-C', $WorktreePath, 'rev-parse', 'HEAD')
    $report.localR5Head = $head
    $report.expectedHeadMatches = ($head -eq $expected.Head)
    $status = Invoke-ReadOnly -Executable $git -Arguments @('-C', $WorktreePath, 'status', '--porcelain', '--untracked-files=normal')
    $report.worktreeClean = [string]::IsNullOrWhiteSpace($status)
    & $git -C $WorktreePath merge-base --is-ancestor $expected.Base HEAD
    $report.p4BaseIsAncestor = ($LASTEXITCODE -eq 0)

    $evidence = 'C:\Enerfoam-Kelpie-QA\agent-runtime-evidence-20261116'
    $zip = Join-Path $evidence 'kelpie-agent-r5-android-gradle-tested-sourceonly.zip'
    $patch = Join-Path $evidence 'kelpie-agent-r5-android-gradle-tested.patch'
    if (Test-Path -LiteralPath $zip -PathType Leaf) {
        $report.zipShaMatches = ((Get-Sha256 $zip) -eq $expected.ZipHash)
    }
    if (Test-Path -LiteralPath $patch -PathType Leaf) {
        $report.patchShaMatches = ((Get-Sha256 $patch) -eq $expected.PatchHash)
    }
    $report.localR5Status = 'READ_ONLY_INSPECTED'
    foreach ($test in @('expectedHeadMatches', 'worktreeClean', 'p4BaseIsAncestor', 'zipShaMatches', 'patchShaMatches')) {
        if (-not $report[$test]) { $report.issues += "R5_$test" }
    }
} catch {
    $report.issues += $_.Exception.Message
} finally {
    New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
    $target = Join-Path $EvidenceDirectory 'kelpie-p4-r5-preflight.json'
    ($report | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $target -Encoding utf8
    Write-Output "Evidence JSON saved locally: $target (no upload; no raw serial/token)"
    Write-Output "Device: $($report.deviceStatus), Model: $($report.model), Android: $($report.androidRelease)"
    Write-Output "R5: $($report.localR5Status), HeadExpected: $($report.expectedHeadMatches), Clean: $($report.worktreeClean)"
    Write-Output "Issues: $($report.issues -join '; ')"
    Write-Output 'INSTALL_NOT_AUTHORIZED / RELEASE_NO_GO / NO_CHANGES_TO_PHONE'
}
if ($report.issues.Count -gt 0) { exit 2 }
exit 0
