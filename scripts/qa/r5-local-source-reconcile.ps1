# Source-only audit of the unpublished R5 Windows worktree.
# No ADB, networking, Git fetch/push, file upload, APK install, or repo mutation.
[CmdletBinding()]
param(
    [switch]$SelfTest,
    [string]$WorktreePath = 'C:\Enerfoam-Kelpie-QA\agent-runtime-local-20261116',
    [string]$EvidenceDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ExpectedHead = '0a49477b0bd584b7ae17474d0e0abf3913187f19'
$ExpectedBase = '7fbbce775e6bb2ac0b104cc0cf4372b6f648a5ee'
$StackedSourceHead = '10333bca15bc4d89f82c84120ae81108498b262e'

function Invoke-Git {
    param([string]$Root, [string[]]$Arguments)
    $result = & git -C $Root @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ('GIT_COMMAND_FAILED:' + $Arguments[0])
    }
    return @($result)
}

function Test-Ancestry {
    param([string]$Root,[string]$Ancestor,[string]$Descendant)
    & git -C $Root merge-base --is-ancestor $Ancestor $Descendant 2>$null
    return ($LASTEXITCODE -eq 0)
}

function Test-Hash {
    param([string]$Value)
    return ($Value -cmatch '^[0-9a-f]{40}$')
}

function New-Report {
    return [ordered]@{
        purpose = 'EF-OPS-KELPIE-R5-LOCAL-SOURCE-RECONCILIATION'
        observedAtUtc = [DateTime]::UtcNow.ToString('o')
        expectedHead = $ExpectedHead
        expectedBase = $ExpectedBase
        stackedSourceHead = $StackedSourceHead
        observedHead = $null
        observedBaseExists = $false
        headMatches = $false
        worktreeClean = $false
        baseAncestorOfHead = $false
        commitsSinceBase = $null
        localStackedHeadPresent = $false
        sourceChangedFileCount = $null
        evidenceStatus = 'INCOMPLETE'
        issues = @()
        importAuthorized = $false
        mergeAuthorized = $false
        installAuthorized = $false
        releaseAuthorized = $false
    }
}

if ($SelfTest) {
    if (-not (Test-Hash $ExpectedHead) -or -not (Test-Hash $ExpectedBase) -or -not (Test-Hash $StackedSourceHead)) {
        throw 'EXPECTED_SHA_FORMAT_INVALID'
    }
    $r = New-Report
    if ($r.importAuthorized -or $r.mergeAuthorized -or $r.releaseAuthorized -or $r.installAuthorized) {
        throw 'FAIL_CLOSED_SELFTEST_FAILED'
    }
    Write-Output 'SELF_TEST_PASS'
    exit 0
}

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'kelpie-r5-source-reconciliation'
}
$report = New-Report
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'GIT_NOT_FOUND' }
    if (-not (Test-Path -LiteralPath $WorktreePath -PathType Container)) { throw 'R5_WORKTREE_NOT_FOUND' }

    $root = (Invoke-Git -Root $WorktreePath -Arguments @('rev-parse','--show-toplevel') | Out-String).Trim()
    $expectedRoot = (Resolve-Path -LiteralPath $WorktreePath).Path.TrimEnd('\','/')
    if (-not [String]::Equals($root.TrimEnd('\','/'),$expectedRoot,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'WORKTREE_ROOT_MISMATCH'
    }

    $head = (Invoke-Git -Root $root -Arguments @('rev-parse','HEAD') | Out-String).Trim()
    $report.observedHead = $head
    $report.headMatches = [String]::Equals($head,$ExpectedHead,[StringComparison]::Ordinal)
    $status = @(Invoke-Git -Root $root -Arguments @('status','--porcelain','--untracked-files=normal'))
    $report.worktreeClean = ([string]::IsNullOrWhiteSpace(($status | Out-String).Trim()))

    & git -C $root cat-file -e ($ExpectedBase + '^{commit}') 2>$null
    $report.observedBaseExists = ($LASTEXITCODE -eq 0)
    if (-not $report.observedBaseExists) { throw 'EXPECTED_BASE_COMMIT_MISSING' }
    $report.baseAncestorOfHead = Test-Ancestry -Root $root -Ancestor $ExpectedBase -Descendant $head
    if (-not $report.baseAncestorOfHead) { throw 'R5_NOT_DESCENDED_FROM_EXPECTED_P4_BASE' }

    $count = (Invoke-Git -Root $root -Arguments @('rev-list','--count',($ExpectedBase+'..'+$head)) | Out-String).Trim()
    $report.commitsSinceBase = [int]$count
    # File names, not contents, commands, environment or Git remote URLs.
    $files = @(Invoke-Git -Root $root -Arguments @('diff','--name-only','--no-renames',($ExpectedBase+'..'+$head)))
    $report.sourceChangedFileCount = @($files | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count

    & git -C $root cat-file -e ($StackedSourceHead + '^{commit}') 2>$null
    $report.localStackedHeadPresent = ($LASTEXITCODE -eq 0)
    if (-not $report.headMatches) { $report.issues += 'EXPECTED_R5_HEAD_MISMATCH' }
    if (-not $report.worktreeClean) { $report.issues += 'R5_WORKTREE_HAS_UNCOMMITTED_CHANGES' }
    if ($report.commitsSinceBase -ne 3) { $report.issues += 'EXPECTED_THREE_LOCAL_COMMITS_MISMATCH' }
    if (-not $report.localStackedHeadPresent) { $report.issues += 'REMOTE_STACK_NOT_IN_LOCAL_GRAPH_DO_NOT_CHERRY_PICK_BLINDLY' }

    $report.evidenceStatus = if ($report.issues.Count -eq 0) {'SOURCE_GRAPH_INSPECTED'} else {'REVIEW_REQUIRED'}
} catch {
    $report.issues += $_.Exception.Message
    $report.evidenceStatus = 'BLOCKED'
}
finally {
    New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
    $target = Join-Path $EvidenceDirectory 'r5-source-reconciliation.json'
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $target -Encoding utf8
    Write-Output ("Evidence JSON (local only): " + $target)
    Write-Output ("R5 source graph: " + $report.evidenceStatus)
    Write-Output ("Expected HEAD matches: " + $report.headMatches)
    Write-Output ("Clean worktree: " + $report.worktreeClean)
    Write-Output ("Commits since P4: " + $report.commitsSinceBase)
    Write-Output ("Local remote-stack commit present: " + $report.localStackedHeadPresent)
    Write-Output ("Issues: " + ($report.issues -join ';'))
    Write-Output 'NO_NETWORK / NO_EXPORT / NO_INSTALL / NO_MERGE / RELEASE_NO_GO'
}
if ($report.evidenceStatus -ne 'SOURCE_GRAPH_INSPECTED') { exit 2 }
exit 0
