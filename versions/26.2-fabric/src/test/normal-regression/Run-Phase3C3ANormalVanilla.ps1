param(
    [long]$Seed = 246813579,
    [switch]$KeepWorld
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldName = "phase3c3a-normal-$Seed"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe normal regression cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) { [IO.Directory]::Delete($worldPath, $true) }
}

function Set-Property([string]$content, [string]$name, [string]$value) {
    if ($content -match "(?m)^$([regex]::Escape($name))=") {
        return [regex]::Replace($content, "(?m)^$([regex]::Escape($name))=.*$", "$name=$value")
    }
    return $content.TrimEnd() + [Environment]::NewLine + "$name=$value" + [Environment]::NewLine
}

try {
    Push-Location $repositoryRoot
    Remove-World
    New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
    $properties = Set-Property $originalProperties 'level-seed' ([string]$Seed)
    $properties = Set-Property $properties 'level-type' 'minecraft:normal'
    $properties = Set-Property $properties 'view-distance' '2'
    $properties = Set-Property $properties 'simulation-distance' '2'
    $properties = Set-Property $properties 'max-tick-time' '60000'
    [IO.File]::WriteAllText($propertiesPath, $properties)
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /c ""' + $gradleWrapper + '" :versions:26.2-fabric:runServer --args="nogui --world ' + $worldName + '""'
    $startInfo.WorkingDirectory = $repositoryRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment['JAVA_TOOL_OPTIONS'] = '-Drandomnibble6plus24generator.phase3c3a.normal.autoStop=true'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    while (-not $process.HasExited) {
        $latestLog = Join-Path $runRoot 'logs\latest.log'
        if ([DateTime]::UtcNow -gt $deadline) {
            $process.Kill()
            throw 'Normal Vanilla server regression timed out'
        }
        Start-Sleep -Milliseconds 250
    }
    $process.WaitForExit()
    $output = $outputTask.Result
    $stderrText = $errorTask.Result
    if ($process.ExitCode -ne 0) {
        throw "Normal Vanilla server exited with code $($process.ExitCode): $stderrText$output"
    }
    $latestLog = Join-Path $runRoot 'logs\latest.log'
    $log = Get-Content -LiteralPath $latestLog -Raw
    if ($log -notmatch 'Done \(') { throw 'Normal Vanilla server did not reach Done' }
    if ($log -notmatch 'Phase 3C3A normal Vanilla regression reached clean shutdown gate') {
        throw 'Normal Vanilla regression did not reach the clean shutdown gate'
    }
    if ($log -match 'Phase 3C3A Structure Overlay|Mosaic world identity|Structure Overlay') {
        throw 'Mosaic runtime branch was entered by a normal Vanilla world'
    }
    'PASS: normal Vanilla server reached Done and stopped cleanly'
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    if ($null -eq $originalJavaToolOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    }
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
