param(
    [ValidateSet('env', 'bytecode', 'init', 'loader', 'layout', 'references', 'gc',
        'jit', 'jmm', 'virtual', 'heap-oom', 'direct', 'stack', 'diagnose')]
    [string]$Lab = 'env',
    [string[]]$JvmArgs = @(),
    [string[]]$LabArgs = @(),
    [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'jdk.ps1')
$jdk = Get-LabJdk
$repo = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $repo 'build/classes'
$agent = Join-Path $repo 'build/size-agent.jar'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$temporary = Join-Path $repo 'build/tmp'
New-Item -ItemType Directory -Path $temporary -Force | Out-Null
$sources = @(Get-ChildItem (Join-Path $repo 'src/main/java') -Recurse -Filter '*.java' |
    Sort-Object FullName | ForEach-Object { $_.FullName })
& $jdk['javac'] --release 25 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
$manifest = Join-Path $repo 'build/agent-manifest.mf'
$manifestText = 'Premain-Class: dev.jvm.labs.SizeAgent' + [char]10 + [char]10
[IO.File]::WriteAllText($manifest, $manifestText, [Text.Encoding]::ASCII)
& $jdk['jar'] --create --file $agent --manifest $manifest -C $classes 'dev/jvm/labs/SizeAgent.class'
if ($LASTEXITCODE -ne 0) { throw 'Agent packaging failed' }
if ($CompileOnly) {
    Write-Output "Compiled JDK 25 labs into $classes"
    return
}

$names = @{
    'env' = 'EnvironmentDemo'; 'bytecode' = 'BytecodeDemo'; 'init' = 'InitializationDemo'
    'loader' = 'ClassIdentityDemo'; 'layout' = 'ObjectLayoutDemo'; 'references' = 'ReachabilityDemo'
    'gc' = 'AllocationPressureDemo'; 'jit' = 'EscapeAnalysisDemo'; 'jmm' = 'MemoryModelDemo'
    'virtual' = 'VirtualThreadsDemo'; 'heap-oom' = 'HeapOomDemo'; 'direct' = 'DirectMemoryDemo'
    'stack' = 'StackDepthDemo'; 'diagnose' = 'DiagnosticsDemo'
}
$defaults = @('-Xmx64m')
switch ($Lab) {
    'layout' { $defaults += "-javaagent:$agent" }
    'heap-oom' { $defaults = @('-Xms32m', '-Xmx32m') }
    'direct' { $defaults += '-XX:MaxDirectMemorySize=16m' }
    'stack' { $defaults += '-Xss256k' }
    'virtual' {
        $defaults += '-Djdk.virtualThreadScheduler.parallelism=1'
        $defaults += '-Djdk.virtualThreadScheduler.maxPoolSize=1'
    }
}
$defaults += "-Djava.io.tmpdir=$temporary"
$launch = @($defaults) + @($JvmArgs) + @('-cp', $classes, ('dev.jvm.labs.' + $names[$Lab])) + @($LabArgs)
& $jdk['java'] @launch
if ($LASTEXITCODE -ne 0) { throw "Lab $Lab failed with exit code $LASTEXITCODE" }
