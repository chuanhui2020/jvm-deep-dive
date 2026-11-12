# Run after ./mvnw.cmd verify (./mvnw verify on Linux/macOS).
# No Pester dependency and no intentional memory-exhaustion workload.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'gc-evidence.ps1')
$runner = Join-Path $PSScriptRoot 'run-gc-fault.ps1'
$repo = Split-Path $PSScriptRoot -Parent

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

# Include phase starts, duplicate GC IDs, worker details and a ZGC statistics row.
$fixture = @'
[0.1s][info ][gc,start ] GC(2) Pause Full (System.gc())
[0.2s][info ][gc       ] GC(2) Pause Full (System.gc()) 20M->5M(32M) 1.234ms
[0.3s][info ][gc       ] GC(3) Pause Full (G1 Compaction Pause) 30M->6M(32M) 2.000ms
[0.4s][info ][gc       ] GC(4) Pause Young (Normal) (G1 Evacuation Pause) (Evacuation Failure: Allocation) 30M->20M(32M) 3.000ms
[0.4s][info ][gc       ] GC(4) Pause Young (Normal) (G1 Evacuation Pause) (Evacuation Failure: Allocation) 30M->20M(32M) 3.000ms
[0.4s][debug][gc,phases] GC(4) Restore Evacuation Failed Regions: 0.1ms
[0.5s][info ][gc,alloc ] GC(5) Y: Allocation Stalls: 1 1 1 1
[0.5s][info ][gc       ] Allocation Stall (allocator-0) 8.123ms
'@
$signals = Get-GcLogSignals $fixture
Assert-Condition ($signals.fullGcSummaryLines -eq 2) 'Full start/completion double-counted'
Assert-Condition ($signals.explicitSystemGcFullLines -eq 1) 'Explicit Full cause misclassified'
Assert-Condition ($signals.evacuationFailureCollections -eq 1) 'Evacuation GC IDs not deduplicated'
Assert-Condition ($signals.allocationStallEvents -eq 1) 'Stall statistics counted as events'
$absent = Get-GcLogSignals ''
Assert-Condition (-not $absent.gcLogEvidenceAvailable -and $null -eq $absent.fullGcSummaryLines) 'Missing logs reported as zero events'
$quiet = Get-GcLogSignals '[0.01s][info][gc] Using G1'
Assert-Condition ($quiet.gcLogEvidenceAvailable -and $quiet.fullGcSummaryLines -eq 0) 'Quiet GC logs treated as missing'

$workspace = Join-Path $repo ('build/evidence-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $workspace -Force | Out-Null
Push-Location $workspace
try {
    $original = @{}
    $injected = @{
        JAVA_TOOL_OPTIONS = '-XX:+UseSerialGC'
        JDK_JAVA_OPTIONS = '-XX:+UseParallelGC'
        _JAVA_OPTIONS = '-Xmx1m'
    }
    try {
        foreach ($key in $injected.Keys) {
            $original[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $injected[$key], 'Process')
        }
        & $runner -Lab container-budget -Mode fixed -LabArgs @('8') -SkipBuild `
            -EvidenceDir 'clean environment' | Out-Null
        foreach ($key in $injected.Keys) {
            Assert-Condition ([Environment]::GetEnvironmentVariable($key, 'Process') -eq $injected[$key]) "Caller environment not restored: $key"
        }
    } finally {
        foreach ($key in $original.Keys) {
            [Environment]::SetEnvironmentVariable($key, $original[$key], 'Process')
        }
    }
    & $runner -Lab container-budget -Mode fixed -LabArgs @('8') -SkipBuild `
        -EvidenceDir 'relative evidence' -StartDelaySeconds 1 -HoldSeconds 1 -TimeoutSeconds 20 `
        -JvmArgs @('-Dreview.value=two words "quoted"') | Out-Null
    $resultPath = Join-Path $workspace 'relative evidence/result.json'
    Assert-Condition (Test-Path -LiteralPath $resultPath) 'Evidence resolved against process cwd instead of PowerShell location'
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($result.completed -and $result.workloadCompleted -and -not $result.timedOut) 'Hold run status incorrect'
    $output = Get-Content -LiteralPath (Join-Path $workspace 'relative evidence/stdout.log') -Raw
    Assert-Condition ($output.Contains('-Dreview.value=two words "quoted"')) 'Native argument quoting lost content'
    Assert-Condition ($output.Contains('phase=after holdSeconds=1')) 'PodLauncher hold not reached'
    $before = (Get-FileHash -LiteralPath $resultPath).Hash
    try {
        & $runner -Lab container-budget -Mode fixed -SkipBuild -EvidenceDir 'relative evidence' | Out-Null
        throw 'Expected directory refusal'
    } catch {
        if ($_.Exception.Message -notlike 'Use a new or empty EvidenceDir*') { throw }
    }
    Assert-Condition ($before -eq (Get-FileHash -LiteralPath $resultPath).Hash) 'Existing result overwritten'

    & $runner -Lab container-budget -Mode fixed -LabArgs @('8') -SkipBuild `
        -EvidenceDir 'disabled logging' -JvmArgs @('-Xlog:disable') | Out-Null
    $result = Get-Content 'disabled logging/result.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($result.workloadCompleted -and -not $result.gcLogEvidenceAvailable `
        -and $null -eq $result.fullGcSummaryLines) 'Disabled logging incorrectly reported as healthy zero-event evidence'

    try {
        & $runner -Lab container-budget -Mode fixed -LabArgs @('8') -SkipBuild `
            -EvidenceDir 'timeout' -StartDelaySeconds 5 -TimeoutSeconds 1 | Out-Null
        throw 'Expected timeout'
    } catch {
        if ($_.Exception.Message -notlike 'Timed out;*') { throw }
    }
    $result = Get-Content 'timeout/result.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($result.timedOut -and -not $result.completed -and -not $result.workloadCompleted) 'Timeout status incorrect'
    Assert-Condition (Test-Path 'timeout/stdout.log') 'Timeout stdout evidence missing'

    try {
        & $runner -Lab container-budget -Mode fixed -LabArgs @('not-a-number') -SkipBuild `
            -EvidenceDir 'invalid workload' | Out-Null
        throw 'Expected nonzero exit'
    } catch {
        if ($_.Exception.Message -notlike 'Process exited *') { throw }
    }
    $result = Get-Content 'invalid workload/result.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($result.completed -and -not $result.workloadCompleted -and $result.exitCode -ne 0) 'Process exit confused with workload completion'
} finally {
    Pop-Location
}
Write-Output "PASS: GC evidence counts, missing logs, relative paths/quoting, clean environment/restoration, hold, timeout and overwrite protection. Evidence: $workspace"
