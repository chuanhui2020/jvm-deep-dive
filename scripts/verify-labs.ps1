$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'jdk.ps1')
$jdk = Get-LabJdk
& (Join-Path $PSScriptRoot 'run-lab.ps1') -CompileOnly
$repo = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $repo 'build/classes'
$script:checkNumber = 0

function Invoke-LabCheck {
    param([string]$Class, [string[]]$Flags = @(), [string[]]$Arguments = @())
    $tokens = @('-Xmx64m') + $Flags + @('-cp', $classes, ('dev.jvm.labs.' + $Class)) + $Arguments
    # Bound every JVM: a broken concurrency experiment must not hang verification.
    $script:checkNumber++
    $out = Join-Path $repo ('build/check-' + $script:checkNumber + '-' + $Class + '.out')
    $err = Join-Path $repo ('build/check-' + $script:checkNumber + '-' + $Class + '.err')
    $quoted = @($tokens | ForEach-Object { '"' + $_ + '"' })
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $jdk['java']
    $startInfo.Arguments = $quoted -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit(20000)) {
        $process.Kill()
        throw "Timed out: $Class"
    }
    $process.WaitForExit()
    [IO.File]::WriteAllText($out, $stdout.Result)
    [IO.File]::WriteAllText($err, $stderr.Result)
    if ($process.ExitCode -ne 0) {
        throw "Failed: $Class. $(Get-Content $err -Raw)"
    }
    return (Get-Content $out -Raw)
}

function Assert-Text([string]$Text, [string]$Expected) {
    if (-not $Text.Contains($Expected)) { throw "Expected '$Expected' in: $Text" }
}

Assert-Text (Invoke-LabCheck 'EnvironmentDemo') 'runtime=25'
Assert-Text (Invoke-LabCheck 'BytecodeDemo') 'result=14'
$init = (Invoke-LabCheck 'InitializationDemo').Replace([string][char]13, '')
Assert-Text $init ('constant=7' + [char]10 + 'after passive use')
Assert-Text $init 'Target initialized'
Assert-Text $init 'Parent initialized'
if ($init.Contains('Child initialized')) { throw 'Passive inherited-field access initialized Child' }
if ($init.IndexOf('Target initialized') -lt $init.IndexOf('after passive use')) { throw 'Premature initialization' }
$loader = Invoke-LabCheck 'ClassIdentityDemo'
foreach ($expected in @('sameName=true', 'sameClass=false', 'parentString=true', 'cast=ClassCastException')) {
    Assert-Text $loader $expected
}
$agent = Join-Path $repo 'build/size-agent.jar'
$layout = Invoke-LabCheck 'ObjectLayoutDemo' -Flags @("-javaagent:$agent")
Assert-Text $layout 'byte[1024]='
$compact = Invoke-LabCheck 'ObjectLayoutDemo' -Flags @("-javaagent:$agent", '-XX:+UseCompactObjectHeaders')
$ordinaryEmpty = [int]([regex]::Match($layout, 'Empty=(\d+)').Groups[1].Value)
$compactEmpty = [int]([regex]::Match($compact, 'Empty=(\d+)').Groups[1].Value)
if ($compactEmpty -gt $ordinaryEmpty) { throw 'Unexpected empty-object size comparison' }
$refs = Invoke-LabCheck 'ReachabilityDemo'
Assert-Text $refs 'cleared='
Assert-Text (Invoke-LabCheck 'AllocationPressureDemo' -Flags @('-XX:+UseG1GC', '-Xlog:gc') -Arguments @('10000', '4')) 'allocatedMiB=39'
Assert-Text (Invoke-LabCheck 'MemoryModelDemo') 'volatileRmw=100 atomic=200'
Assert-Text (Invoke-LabCheck 'VirtualThreadsDemo' -Flags @('-Djdk.virtualThreadScheduler.parallelism=1', '-Djdk.virtualThreadScheduler.maxPoolSize=1')) 'virtualTasks=12 completed=12'
Assert-Text (Invoke-LabCheck 'HeapOomDemo' -Flags @('-Xmx32m')) 'caught=java.lang.OutOfMemoryError'
Assert-Text (Invoke-LabCheck 'DirectMemoryDemo' -Flags @('-XX:MaxDirectMemorySize=16m')) 'directMiB=16'
Assert-Text (Invoke-LabCheck 'StackDepthDemo' -Flags @('-Xss256k')) 'caught=java.lang.StackOverflowError'
Assert-Text (Invoke-LabCheck 'EscapeAnalysisDemo') 'escapingBytes='
Assert-Text (Invoke-LabCheck 'EscapeAnalysisDemo' -Flags @('-XX:-DoEscapeAnalysis')) 'localBytes='
Assert-Text (Invoke-LabCheck 'DiagnosticsDemo' -Arguments @('deadlock', '1')) 'deadlocked=2'
Assert-Text (Invoke-LabCheck 'DiagnosticsDemo' -Arguments @('cpu', '1')) 'done checksum='
Assert-Text (Invoke-LabCheck 'DiagnosticsDemo' -Arguments @('retention', '1')) 'retainedMiB='
$bytecode = & $jdk['javap'] -classpath $classes -p -c -v 'dev.jvm.labs.BytecodeDemo'
if ($LASTEXITCODE -ne 0) { throw 'javap failed' }
foreach ($expected in @('major version: 69', 'ACC_SYNCHRONIZED', 'monitorenter', 'monitorexit', 'ACC_VOLATILE')) {
    Assert-Text ($bytecode -join [Environment]::NewLine) $expected
}
Write-Output 'PASS: all 14 labs; bytecode, initialization, class identity, memory, concurrency and diagnostics checked.'
