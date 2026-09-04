param(
    [long]$MasterSeed = 123456789,
    [string]$Username = 'SpawnVerifier',
    [switch]$KeepWorld
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldName = "phase3c2r-spawn-$MasterSeed"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
$outputPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3c2r-spawn-result.json'))

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3C2R cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        [IO.Directory]::Delete($worldPath, $true)
    }
}

function Set-Property([string]$content, [string]$name, [string]$value) {
    if ($content -match "(?m)^$([regex]::Escape($name))=") {
        return [regex]::Replace($content, "(?m)^$([regex]::Escape($name))=.*$", "$name=$value")
    }
    return $content.TrimEnd() + [Environment]::NewLine + "$name=$value" + [Environment]::NewLine
}

function Write-HiddenPresetPack {
    $packRoot = Join-Path $worldPath 'datapacks\phase3c2r-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $packRoot 'pack.mcmeta'),
        '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3C2R spawn regression"}}')
    $profile = '"mosaic_profile":{"format_version":2,"seed_derivation_algorithm_version":1,"feature_ordering_algorithm_version":1,"presentation_algorithm_version":1,"primary_dimension":"minecraft:overworld"}'
    $preset = @"
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "randomnibble6plus24generator:mosaic",
        "biome_source": {"type":"minecraft:multi_noise","preset":"minecraft:overworld"},
        "settings": "minecraft:overworld",
        $profile
      }
    },
    "minecraft:the_nether": {
      "type": "minecraft:the_nether",
      "generator": {
        "type": "randomnibble6plus24generator:mosaic",
        "biome_source": {"type":"minecraft:multi_noise","preset":"minecraft:nether"},
        "settings": "minecraft:nether",
        $profile
      }
    },
    "minecraft:the_end": {
      "type": "minecraft:the_end",
      "generator": {
        "type": "randomnibble6plus24generator:mosaic",
        "biome_source": {"type":"minecraft:the_end"},
        "settings": "minecraft:end",
        $profile
      }
    }
  }
}
"@
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3c2r_hidden_mosaic.json'), $preset)
}

function Start-Server {
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
    $startInfo.Environment['JAVA_TOOL_OPTIONS'] = @(
        '-Drandomnibble6plus24generator.phase3c2r.spawn.trace=1',
        '-Drandomnibble6plus24generator.phase3c2r.spawn.verify=1',
        "-Drandomnibble6plus24generator.phase3c2r.spawn.output=$($outputPath.Replace('\','/'))",
        '-Drandomnibble6plus24generator.phase3c2r.spawn.autoStop=true'
    ) -join ' '
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    [void]$process.StandardOutput.ReadToEndAsync()
    [void]$process.StandardError.ReadToEndAsync()
    return $process
}

function Start-Client {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /c ""' + $gradleWrapper + '" :versions:26.2-fabric:runClient --args="--offlineDeveloperMode --username ' + $Username + ' --quickPlayPath quickplay-log.json --quickPlayMultiplayer localhost:25565""'
    $startInfo.WorkingDirectory = $repositoryRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    [void]$process.StandardOutput.ReadToEndAsync()
    [void]$process.StandardError.ReadToEndAsync()
    return $process
}

$serverProcess = $null
$clientProcess = $null
try {
    Push-Location $repositoryRoot
    Remove-World
    New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
    Write-HiddenPresetPack
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3c2r_hidden_mosaic'
    $properties = Set-Property $properties 'level-name' $worldName
    $properties = Set-Property $properties 'online-mode' 'false'
    $properties = Set-Property $properties 'enforce-secure-profile' 'false'
    $properties = Set-Property $properties 'view-distance' '2'
    $properties = Set-Property $properties 'simulation-distance' '2'
    $properties = Set-Property $properties 'max-tick-time' '-1'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if (Test-Path -LiteralPath $outputPath) {
        Remove-Item -LiteralPath $outputPath -Force
    }
    $serverProcess = Start-Server
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    while (-not $serverProcess.HasExited) {
        $listening = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue
        if ($listening) { break }
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Phase 3C2R server timed out before spawn handoff' }
        Start-Sleep -Milliseconds 250
    }
    if ($serverProcess.HasExited) { throw "Phase 3C2R server exited before accepting connections: $($serverProcess.ExitCode)" }

    $clientProcess = Start-Client
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    while (-not (Test-Path -LiteralPath $outputPath)) {
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Phase 3C2R client/spawn handoff timed out' }
        Start-Sleep -Milliseconds 250
    }
    $result = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3C2R spawn handoff failed: $result" }
    $log = Get-Content -LiteralPath (Join-Path $runRoot 'logs\latest.log') -Raw
    if ($log -match 'Unloaded chunk') { throw 'Phase 3C2R reproduced an Unloaded chunk failure' }
    if ($log -notmatch 'PLAYER_SPAWN|ticket type=PLAYER_SPAWN') { throw 'PLAYER_SPAWN ticket was not observed' }
    if ($log -notmatch 'SPAWN_SEARCH|ticket type=SPAWN_SEARCH') { throw 'SPAWN_SEARCH ticket was not observed' }
    $joinedPattern = [regex]::Escape($Username) + ' joined the game'
    if ($log -notmatch $joinedPattern) { throw 'Client did not join the Mosaic world' }
    $result | ConvertTo-Json -Depth 8
} finally {
    if ($clientProcess -and -not $clientProcess.HasExited) { $clientProcess.Kill() }
    if ($serverProcess -and -not $serverProcess.HasExited) {
        try { $serverProcess.StandardInput.WriteLine('stop') } catch { }
        [void]$serverProcess.WaitForExit(15000)
        if (-not $serverProcess.HasExited) { $serverProcess.Kill() }
    }
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    if ($null -eq $originalJavaToolOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    }
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
