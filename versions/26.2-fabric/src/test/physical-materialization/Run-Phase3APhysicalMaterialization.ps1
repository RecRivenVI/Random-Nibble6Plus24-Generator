param(
    [long]$MasterSeed = 123456789,
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 0,
    [int]$ChunkZ = 0,
    [int]$DuplicateRequests = 1,
    [string]$ExpectedHash = '',
    [ValidateSet('', 'adjacent', 'patch', 'modified-neighbor')]
    [string]$PatchShape = '',
    [ValidateSet('row-major', 'reverse', 'shuffle', 'parallel')]
    [string]$PatchOrder = 'row-major',
    [switch]$FaultSweep,
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
$scenario = if ($FaultSweep) { 'fault-sweep' } elseif ([string]::IsNullOrWhiteSpace($PatchShape)) { 'single' } else { "$PatchShape-$PatchOrder" }
$worldName = "phase3a-$scenario-$MasterSeed-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
if ([string]::IsNullOrWhiteSpace($ResultPath)) {
    $ResultPath = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3a-result.json'
}
$resultPath = [IO.Path]::GetFullPath($ResultPath)

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3A cleanup path: $worldPath"
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
    $packRoot = Join-Path $worldPath 'datapacks\phase3a-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    $packMetadata = @'
{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3A hidden Mosaic verification fixture"}}
'@
    [IO.File]::WriteAllText((Join-Path $packRoot 'pack.mcmeta'), $packMetadata)
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
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3a_hidden_mosaic.json'), $preset)
}

try {
    Push-Location $repositoryRoot
    Remove-World
    New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
    Write-HiddenPresetPack

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3a_hidden_mosaic'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if ($FaultSweep) {
        $options = @(
            '-Drandomnibble6plus24generator.phase3a.fault.verify=true',
            "-Drandomnibble6plus24generator.phase3a.fault.chunkX=$ChunkX",
            "-Drandomnibble6plus24generator.phase3a.fault.chunkZ=$ChunkZ",
            "-Drandomnibble6plus24generator.phase3a.fault.expectedHash=$ExpectedHash",
            "-Drandomnibble6plus24generator.phase3a.fault.output=$($resultPath.Replace('\','/'))"
        )
    } elseif ([string]::IsNullOrWhiteSpace($PatchShape)) {
        $options = @(
            '-Drandomnibble6plus24generator.phase3a.verify=fixture',
            "-Drandomnibble6plus24generator.phase3a.masterSeed=$MasterSeed",
            "-Drandomnibble6plus24generator.phase3a.dimension=$Dimension",
            "-Drandomnibble6plus24generator.phase3a.chunkX=$ChunkX",
            "-Drandomnibble6plus24generator.phase3a.chunkZ=$ChunkZ",
            "-Drandomnibble6plus24generator.phase3a.duplicateRequests=$DuplicateRequests",
            "-Drandomnibble6plus24generator.phase3a.output=$($resultPath.Replace('\','/'))"
        )
        if ($VerifyReload) {
            $options += '-Drandomnibble6plus24generator.phase3a.mutateBeforeStop=true'
            $snapshotPath = [IO.Path]::ChangeExtension($resultPath, '.snapshot.bin.gz')
            $options += "-Drandomnibble6plus24generator.phase3a.snapshotOutput=$($snapshotPath.Replace('\','/'))"
        }
    } else {
        $options = @(
            "-Drandomnibble6plus24generator.phase3a.patch.verify=$PatchShape",
            "-Drandomnibble6plus24generator.phase3a.patch.dimension=$Dimension",
            "-Drandomnibble6plus24generator.phase3a.patch.chunkX=$ChunkX",
            "-Drandomnibble6plus24generator.phase3a.patch.chunkZ=$ChunkZ",
            "-Drandomnibble6plus24generator.phase3a.patch.order=$PatchOrder",
            "-Drandomnibble6plus24generator.phase3a.patch.output=$($resultPath.Replace('\','/'))"
        )
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedHash)) {
        $options += "-Drandomnibble6plus24generator.phase3a.expectedHash=$ExpectedHash"
    }
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
    if ($LASTEXITCODE -ne 0) { throw "Phase 3A server failed: $LASTEXITCODE" }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3A result failed: $result" }
    if ($VerifyReload) {
        $reloadPath = [IO.Path]::ChangeExtension($resultPath, '.reload.json')
        $env:JAVA_TOOL_OPTIONS = @(
            '-Drandomnibble6plus24generator.phase3a.reload.verify=true',
            "-Drandomnibble6plus24generator.phase3a.reload.dimension=$Dimension",
            "-Drandomnibble6plus24generator.phase3a.reload.chunkX=$ChunkX",
            "-Drandomnibble6plus24generator.phase3a.reload.chunkZ=$ChunkZ",
            "-Drandomnibble6plus24generator.phase3a.reload.expectedHash=$($result.persistedHash)",
            '-Drandomnibble6plus24generator.phase3a.reload.expectMarker=true',
            "-Drandomnibble6plus24generator.phase3a.reload.referenceSnapshot=$($snapshotPath.Replace('\','/'))",
            "-Drandomnibble6plus24generator.phase3a.reload.output=$($reloadPath.Replace('\','/'))"
        ) -join ' '
        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
        if ($LASTEXITCODE -ne 0) { throw "Phase 3A reload server failed: $LASTEXITCODE" }
        $reload = Get-Content -LiteralPath $reloadPath -Raw | ConvertFrom-Json
        if ($reload.status -ne 'PASS' -or $reload.isolatedGenerations -ne 0 -or -not $reload.markerPreserved) {
            throw "Phase 3A reload result failed: $reload"
        }
    }
    $result | ConvertTo-Json -Depth 8
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
