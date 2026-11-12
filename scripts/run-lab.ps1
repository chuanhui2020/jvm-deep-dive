param(
    [ValidateSet('env', 'bytecode', 'init', 'loader', 'layout', 'references', 'gc', 'jit', 'jmm', 'virtual', 'heap-oom', 'direct', 'stack', 'diagnose', 'gc-churn', 'gc-retention', 'g1-evacuation', 'g1-humongous', 'zgc-stall', 'metaspace-leak', 'container-budget', 'cpu-throttle')]
    [string]$Lab = 'env',
    [string[]]$JvmArgs = @(),
    [string[]]$LabArgs = @(),
    [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'jdk.ps1')
$jdk = Get-LabJdk
$repo = Split-Path $PSScriptRoot -Parent
$wrapper = Join-Path $repo 'mvnw.cmd'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    $wrapper = Join-Path $repo 'mvnw'
}
$module = Join-Path $repo ('labs/' + $Lab)
$pom = if ($CompileOnly) { Join-Path $repo 'pom.xml' } else { Join-Path $module 'pom.xml' }
& $wrapper -B -ntp -f $pom package '-DskipTests'
if ($LASTEXITCODE -ne 0) { throw 'Maven packaging failed' }
if ($CompileOnly) {
    Write-Output 'Packaged all standalone labs'
    return
}
$jar = Join-Path $module 'target/lab.jar'
$temporary = Join-Path $repo 'build/tmp'
New-Item -ItemType Directory -Path $temporary -Force | Out-Null
$defaults = @('-Xmx64m')
switch ($Lab) {
    'layout' { $defaults += "-javaagent:$jar" }
    'heap-oom' { $defaults = @('-Xms32m', '-Xmx32m') }
    'direct' { $defaults += '-XX:MaxDirectMemorySize=16m' }
    'stack' { $defaults += '-Xss256k' }
    'virtual' {
        $defaults += '-Djdk.virtualThreadScheduler.parallelism=1'
        $defaults += '-Djdk.virtualThreadScheduler.maxPoolSize=1'
    }
}
$faultCatalog = Get-Content (Join-Path $PSScriptRoot 'gc-faults.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$faultEntry = $faultCatalog.PSObject.Properties[$Lab]
if ($faultEntry) {
    $defaults = @($faultEntry.Value.jvmArgs)
}
$launch = @($defaults) + @("-Djava.io.tmpdir=$temporary") + @($JvmArgs) + @('-jar', $jar) + @($LabArgs)
& $jdk['java'] @launch
if ($LASTEXITCODE -ne 0) { throw "Lab $Lab failed with exit code $LASTEXITCODE" }
