param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('gc-churn', 'gc-retention', 'g1-evacuation', 'g1-humongous',
        'zgc-stall', 'metaspace-leak', 'container-budget', 'cpu-throttle')]
    [string]$Lab,
    [ValidateSet('fault', 'fixed')]
    [string]$Mode = 'fault',
    [string[]]$LabArgs = @(),
    [string[]]$JvmArgs = @(),
    [string]$EvidenceDir,
    [ValidateRange(0, 600)]
    [int]$HoldSeconds = 0,
    [ValidateRange(0, 60)]
    [int]$StartDelaySeconds = 0,
    [ValidateRange(1, 1200)]
    [int]$TimeoutSeconds = 90,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'jdk.ps1')
. (Join-Path $PSScriptRoot 'gc-evidence.ps1')
Invoke-WithCleanJavaEnvironment {
    $jdk = Get-LabJdk
    $repo = Split-Path $PSScriptRoot -Parent
    $catalog = Get-Content (Join-Path $PSScriptRoot 'gc-faults.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $profile = $catalog.PSObject.Properties[$Lab].Value
    $module = Join-Path $repo "labs/$Lab"
    $wrapper = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        Join-Path $module 'mvnw.cmd'
    } else {
        Join-Path $module 'mvnw'
    }
    if (-not $SkipBuild) {
        & $wrapper -B -ntp -f (Join-Path $module 'pom.xml') verify
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $Lab" }
    }
    $jar = Join-Path $module 'target/lab.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw "Build the project first: $jar" }
    if (-not $EvidenceDir) {
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        $EvidenceDir = Join-Path $repo "build/faults/$Lab-$Mode-$stamp"
    }
    $EvidenceDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($EvidenceDir)
    if ((Test-Path -LiteralPath $EvidenceDir) -and
        -not (Test-Path -LiteralPath $EvidenceDir -PathType Container)) {
        throw 'EvidenceDir must be a directory, not an existing file.'
    }
    if ((Test-Path -LiteralPath $EvidenceDir) -and
        @(Get-ChildItem -LiteralPath $EvidenceDir -Force).Count -gt 0) {
        throw 'Use a new or empty EvidenceDir to avoid mixing runs and overwriting evidence.'
    }
    New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
    $temporary = Join-Path $EvidenceDir 'tmp'
    New-Item -ItemType Directory -Path $temporary -Force | Out-Null
    $applicationArgs = if ($LabArgs.Count -gt 0) { @($Mode) + $LabArgs } else { @($profile.($Mode + 'Args')) }
    $entry = if ($HoldSeconds -gt 0 -or $StartDelaySeconds -gt 0) {
        @('-cp', $jar, 'dev.jvm.labs.PodLauncher')
    } else {
        @('-jar', $jar)
    }
    $tokens = @($profile.jvmArgs) + @('-XX:NativeMemoryTracking=summary',
        "-Djava.io.tmpdir=$temporary", '-XX:+HeapDumpOnOutOfMemoryError',
        '-XX:HeapDumpPath=oom.hprof',
        '-Xlog:gc*=debug,class+unload=info:file=jvm.log:uptime,level,tags:filecount=3,filesize=20M') +
        $JvmArgs + $entry + $applicationArgs

    function ConvertTo-NativeArgument([string]$Value) {
        if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
        $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
        $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
        return '"' + $escaped + '"'
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $jdk['java']
    $startInfo.Arguments = (@($tokens | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
    $startInfo.WorkingDirectory = $EvidenceDir
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($key in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')) {
        $startInfo.EnvironmentVariables.Remove($key)
    }
    $startInfo.EnvironmentVariables['LAB_START_DELAY_SECONDS'] = [string]$StartDelaySeconds
    $startInfo.EnvironmentVariables['LAB_HOLD_SECONDS'] = [string]$HoldSeconds
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $startedAt = [DateTimeOffset]::UtcNow
    $elapsed = [Diagnostics.Stopwatch]::StartNew()
    [void]$process.Start()
    Write-Output "Started PID=$($process.Id) evidence=$EvidenceDir"
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $finished = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $finished) { $process.Kill() }
    $process.WaitForExit()
    $elapsed.Stop()
    [IO.File]::WriteAllText((Join-Path $EvidenceDir 'stdout.log'), $stdout.Result)
    [IO.File]::WriteAllText((Join-Path $EvidenceDir 'stderr.log'), $stderr.Result)
    $logFiles = @(Get-ChildItem -LiteralPath $EvidenceDir -Filter 'jvm.log*' -File)
    $logs = ($logFiles | ForEach-Object { [IO.File]::ReadAllText($_.FullName) }) -join "`n"
    $signals = Get-GcLogSignals -Logs $logs
    $summary = [ordered]@{
        lab = $Lab
        mode = $Mode
        scope = 'local-jvm; no container resource enforcement is provided by this script'
        completed = $finished
        timedOut = -not $finished
        workloadCompleted = $stdout.Result.Contains('status=completed')
        startedAtUtc = $startedAt.ToString('o')
        elapsedMilliseconds = $elapsed.ElapsedMilliseconds
        exitCode = $process.ExitCode
        arguments = $tokens
        evidenceDirectory = $EvidenceDir
        logFiles = @($logFiles | ForEach-Object { $_.Name })
        gcLogEvidenceAvailable = $signals.gcLogEvidenceAvailable
        fullGcSummaryLines = $signals.fullGcSummaryLines
        explicitSystemGcFullLines = $signals.explicitSystemGcFullLines
        evacuationFailureCollections = $signals.evacuationFailureCollections
        evacuationFailureMentions = $signals.evacuationFailureMentions
        allocationStallEvents = $signals.allocationStallEvents
        caughtOom = $stdout.Result.Contains('caught=java.lang.OutOfMemoryError')
        interpretation = 'Signal counts are observations, not correctness assertions or causal proof.'
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $EvidenceDir 'result.json') -Encoding UTF8
    Write-Output ("Evidence: " + $EvidenceDir)
    Write-Output ($summary | ConvertTo-Json -Depth 5)
    if (-not $finished) { throw "Timed out; evidence preserved in $EvidenceDir" }
    if ($process.ExitCode -ne 0) { throw "Process exited $($process.ExitCode); inspect evidence in $EvidenceDir" }
}
