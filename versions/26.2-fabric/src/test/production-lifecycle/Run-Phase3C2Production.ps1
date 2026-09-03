param(
    [long]$MasterSeed = 123456789,
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 125,
    [int]$ChunkZ = -37,
    [ValidateSet('full', 'concurrent', 'patch', 'pair', 'reload')]
    [string]$Mode = 'full',
    [int]$Repeats = 16,
    [switch]$VerifyReload,
    [switch]$Mutate,
    [switch]$KeepWorld
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldName = "phase3c2-production-$MasterSeed-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
$resultRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3c2-production'))
$resultPath = Join-Path $resultRoot "$Mode-$ChunkX-$ChunkZ.json"

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3C2 cleanup path: $worldPath"
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
    $packRoot = Join-Path $worldPath 'datapacks\phase3c2-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $packRoot 'pack.mcmeta'),
        '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3C2 hidden production Mosaic"}}')
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
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3c2_hidden_mosaic.json'), $preset)
}

function Invoke-Production([string]$productionMode, [string]$output, [bool]$mutateBeforeStop, [bool]$expectMarker) {
    $options = @(
        "-Drandomnibble6plus24generator.phase3c2.production.mode=$productionMode",
        "-Drandomnibble6plus24generator.phase3c2.production.masterSeed=$MasterSeed",
        "-Drandomnibble6plus24generator.phase3c2.production.dimension=$Dimension",
        "-Drandomnibble6plus24generator.phase3c2.production.chunkX=$ChunkX",
        "-Drandomnibble6plus24generator.phase3c2.production.chunkZ=$ChunkZ",
        "-Drandomnibble6plus24generator.phase3c2.production.repeats=$Repeats",
        "-Drandomnibble6plus24generator.phase3c2.production.output=$($output.Replace('\','/'))"
    )
    if ($mutateBeforeStop) { $options += '-Drandomnibble6plus24generator.phase3c2.production.mutate=true' }
    if ($expectMarker) { $options += '-Drandomnibble6plus24generator.phase3c2.production.expectMarker=true' }
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Phase 3C2 production server failed: $LASTEXITCODE" }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3C2 production result failed: $result" }
    return $result
}

try {
    Push-Location $repositoryRoot
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    if ($Mode -ne 'reload' -or $VerifyReload) {
        Remove-World
        New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
        Write-HiddenPresetPack
    } elseif (-not (Test-Path -LiteralPath $worldPath)) {
        throw "Reload mode requires existing world: $worldPath"
    }

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3c2_hidden_mosaic'
    # Keep the ordinary server lifecycle intact while bounding the unrelated
    # spawn-preparation frontier used by this smoke test.
    $properties = Set-Property $properties 'view-distance' '2'
    $properties = Set-Property $properties 'simulation-distance' '2'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if ($VerifyReload) {
        $firstPath = Join-Path $resultRoot "full-$ChunkX-$ChunkZ.json"
        $first = Invoke-Production 'full' $firstPath ([bool]$Mutate) $false
        $reloadPath = Join-Path $resultRoot "reload-$ChunkX-$ChunkZ.json"
        $reload = Invoke-Production 'reload' $reloadPath $false $true
        if ($reload.artifactGenerations -ne 0 -or $reload.spawnCalls -ne 0) {
            throw "Production reload regenerated canonical data: $($reload | ConvertTo-Json -Depth 8)"
        }
        if (-not $reload.markerPresent -and $Mutate) {
            throw "Production reload did not preserve the modified BlockState"
        }
        $reload | ConvertTo-Json -Depth 8
    } else {
        $result = Invoke-Production $Mode $resultPath ([bool]$Mutate) $false
        $result | ConvertTo-Json -Depth 8
    }
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
