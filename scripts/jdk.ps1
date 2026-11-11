function Get-LabJdk {
    $suffix = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
    if ($env:JAVA_HOME) {
        $bin = Join-Path $env:JAVA_HOME 'bin'
    } else {
        $compiler = Get-Command ('javac' + $suffix) -CommandType Application -ErrorAction Stop | Select-Object -First 1
        $bin = Split-Path $compiler.Source -Parent
    }
    $tools = @{}
    foreach ($name in @('java', 'javac', 'jar', 'javap', 'jcmd', 'jfr')) {
        $path = Join-Path $bin ($name + $suffix)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing JDK tool: $path. Set JAVA_HOME to a complete JDK 25."
        }
        $tools[$name] = $path
    }
    foreach ($name in @('java', 'javac')) {
        $version = & $tools[$name] --version
        if ($LASTEXITCODE -ne 0 -or ($version -join ' ') -notmatch '^(java|openjdk|javac) 25([. -]|$)') {
            throw "JDK 25 required; $name reports: $version"
        }
    }
    return $tools
}
