param(
    [long]$MasterSeed = 123456789,
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 0,
    [int]$ChunkZ = 0,
    [ValidateSet('full', 'reload', 'pair', 'runtime', 'ocean-scan')]
    [string]$Mode = 'full',
    [switch]$VerifyReload,
    [string]$ResultPath = '',
    [switch]$KeepWorld
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldName = "phase3c1-$Mode-$MasterSeed-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
if ([string]::IsNullOrWhiteSpace($ResultPath)) {
    $ResultPath = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3c1-result.json'
}
$resultPath = [IO.Path]::GetFullPath($ResultPath)

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3C1 cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

function Set-Property([string]$content, [string]$name, [string]$value) {
    if ($content -match "(?m)^$([regex]::Escape($name))=") {
        return [regex]::Replace($content, "(?m)^$([regex]::Escape($name))=.*$", "$name=$value")
    }
    return $content.TrimEnd() + [Environment]::NewLine + "$name=$value" + [Environment]::NewLine
}

function Write-HiddenPresetPack {
    $packRoot = Join-Path $worldPath 'datapacks\phase3c1-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $packRoot 'pack.mcmeta'),
        '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3C1 hidden Mosaic fixture"}}')
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
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3c1_hidden_mosaic.json'), $preset)
}

function Invoke-Phase3C1([string]$verifyMode, [string]$output) {
    $options = @(
        "-Drandomnibble6plus24generator.phase3c1.verify=$verifyMode",
        "-Drandomnibble6plus24generator.phase3c1.masterSeed=$MasterSeed",
        "-Drandomnibble6plus24generator.phase3c1.dimension=$Dimension",
        "-Drandomnibble6plus24generator.phase3c1.chunkX=$ChunkX",
        "-Drandomnibble6plus24generator.phase3c1.chunkZ=$ChunkZ",
        "-Drandomnibble6plus24generator.phase3c1.output=$($output.Replace('\','/'))"
    )
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Phase 3C1 server failed: $LASTEXITCODE" }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3C1 result failed: $result" }
    return $result
}

try {
    Push-Location $repositoryRoot
    if ($Mode -ne 'reload') {
        Remove-World
        New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
        Write-HiddenPresetPack
    } elseif (-not (Test-Path -LiteralPath $worldPath)) {
        throw "Reload mode requires existing world: $worldPath"
    }

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3c1_hidden_mosaic'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if ($VerifyReload) {
        $firstPath = [IO.Path]::ChangeExtension($resultPath, '.full.json')
        $first = Invoke-Phase3C1 'full' $firstPath
        $reloadPath = [IO.Path]::ChangeExtension($resultPath, '.reload.json')
        $result = Invoke-Phase3C1 'reload' $reloadPath
        if ($result.artifactGenerations -ne 0 -or $result.spawnCalls -ne 0) {
            throw "FULL reload unexpectedly regenerated Artifact/SPAWN: $($result | ConvertTo-Json -Depth 10)"
        }
        if ($result.serializedHash -ne $first.serializedHash) {
            throw "FULL serialized chunk data changed across reload: $($first.serializedHash) != $($result.serializedHash)"
        }
    } else {
        $result = Invoke-Phase3C1 $Mode $resultPath
    }
    $result | ConvertTo-Json -Depth 12
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
