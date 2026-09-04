param(
    [long]$MasterSeed = 123456789,
    [ValidateSet('probe', 'reload')]
    [string]$Mode = 'probe',
    [switch]$KeepWorld
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldName = "phase3c3b-locate-$MasterSeed"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
$resultRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3c3b-locate'))
$resultPath = Join-Path $resultRoot "$Mode.json"
$previousPath = Join-Path $resultRoot 'probe.json'

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3C3B cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) { [IO.Directory]::Delete($worldPath, $true) }
}

function Set-Property([string]$content, [string]$name, [string]$value) {
    if ($content -match "(?m)^$([regex]::Escape($name))=") {
        return [regex]::Replace($content, "(?m)^$([regex]::Escape($name))=.*$", "$name=$value")
    }
    return $content.TrimEnd() + [Environment]::NewLine + "$name=$value" + [Environment]::NewLine
}

function Write-HiddenPresetPack {
    $packRoot = Join-Path $worldPath 'datapacks\phase3c3b-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $packRoot 'pack.mcmeta'),
        '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3C3B hidden Mosaic index"}}')
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
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3c3b_hidden_mosaic.json'), $preset)
}

try {
    Push-Location $repositoryRoot
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    if ($Mode -eq 'probe') {
        Remove-World
        New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
        Write-HiddenPresetPack
    } elseif (-not (Test-Path -LiteralPath $worldPath)) {
        throw "$Mode mode requires the probe world to exist: $worldPath"
    }
    if (Test-Path -LiteralPath $resultPath) { Remove-Item -LiteralPath $resultPath -Force }

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3c3b_hidden_mosaic'
    $properties = Set-Property $properties 'view-distance' '2'
    $properties = Set-Property $properties 'simulation-distance' '2'
    $properties = Set-Property $properties 'max-tick-time' '-1'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    $options = @(
        "-Drandomnibble6plus24generator.phase3c3b.locate.mode=$Mode",
        "-Drandomnibble6plus24generator.phase3c3b.locate.output=$($resultPath.Replace('\','/'))",
        '-Drandomnibble6plus24generator.phase3c3b.locate.autoStop=true'
    )
    if ($Mode -ne 'probe') {
        if (-not (Test-Path -LiteralPath $previousPath)) {
            throw "Reload mode requires the previous probe result: $previousPath"
        }
        $options += "-Drandomnibble6plus24generator.phase3c3b.locate.fixtureSpec=$((Get-Content -LiteralPath $previousPath -Raw | ConvertFrom-Json).fixtureSpec)"
    }
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Phase 3C3B locate server failed: $LASTEXITCODE" }
    $latestLog = Join-Path $runRoot 'logs\latest.log'
    $passLog = Select-String -Path $latestLog -Pattern 'Phase 3C3B Mosaic /locate PASS' -Quiet
    if ((-not (Test-Path -LiteralPath $resultPath)) -or (-not $passLog)) {
        throw "Phase 3C3B locate server did not report a PASS; inspect $latestLog"
    }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3C3B locate result failed: $result" }
    if ($Mode -eq 'reload' -and $result.artifactGenerations -ne 0) {
        throw "Phase 3C3B reload regenerated canonical data: $result"
    }
    if ($Mode -eq 'reload') {
        $probe = Get-Content -LiteralPath $previousPath -Raw | ConvertFrom-Json
        $locationsEqual = (($probe.locations | ConvertTo-Json -Depth 12 -Compress) -eq ($result.locations | ConvertTo-Json -Depth 12 -Compress))
        $tagsEqual = (($probe.tagQuery | ConvertTo-Json -Depth 12 -Compress) -eq ($result.tagQuery | ConvertTo-Json -Depth 12 -Compress))
        if ((-not $locationsEqual) -or (-not $tagsEqual)) {
            throw "Phase 3C3B reload changed persisted locate results"
        }
    }
    $result | ConvertTo-Json -Depth 12
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
