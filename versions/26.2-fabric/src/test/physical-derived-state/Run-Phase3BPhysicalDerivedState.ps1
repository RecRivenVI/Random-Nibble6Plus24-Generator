param(
    [long]$MasterSeed = 123456789,
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 0,
    [int]$ChunkZ = 0,
    [ValidateSet('single', 'patch', 'border', 'fault', 'reload', 'initialize-save', 'poi-scan', 'poi-scan-bee')]
    [string]$Mode = 'single',
    [ValidateSet('initialize_light', 'light')]
    [string]$TargetStatus = 'light',
    [ValidateSet('row-major', 'reverse', 'shuffle', 'parallel')]
    [string]$Order = 'row-major',
    [bool]$BorderSource = $true,
    [switch]$RequirePoi,
    [switch]$VerifyReload,
    [switch]$FeaturesSaveThenLight,
    [switch]$InitializeSaveThenLight,
    [int]$ScanLimit = 2000,
    [int]$ScanStart = 0,
    [int]$ScanMatches = 12,
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
$scenario = "$Mode-$TargetStatus-$Order"
$worldName = "phase3b-$scenario-$MasterSeed-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ"
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
if ([string]::IsNullOrWhiteSpace($ResultPath)) {
    $ResultPath = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3b-result.json'
}
$resultPath = [IO.Path]::GetFullPath($ResultPath)

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3B cleanup path: $worldPath"
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
    $packRoot = Join-Path $worldPath 'datapacks\phase3b-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $packRoot 'pack.mcmeta'),
        '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Phase 3B hidden Mosaic verification fixture"}}')
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
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3b_hidden_mosaic.json'), $preset)
}

function Invoke-Phase3B([string]$verifyMode, [string]$output, [bool]$expectNoArtifacts = $false) {
    $options = @(
        "-Drandomnibble6plus24generator.phase3b.verify=$verifyMode",
        "-Drandomnibble6plus24generator.phase3b.masterSeed=$MasterSeed",
        "-Drandomnibble6plus24generator.phase3b.dimension=$Dimension",
        "-Drandomnibble6plus24generator.phase3b.chunkX=$ChunkX",
        "-Drandomnibble6plus24generator.phase3b.chunkZ=$ChunkZ",
        "-Drandomnibble6plus24generator.phase3b.targetStatus=$TargetStatus",
        "-Drandomnibble6plus24generator.phase3b.order=$Order",
        "-Drandomnibble6plus24generator.phase3b.borderSource=$($BorderSource.ToString().ToLowerInvariant())",
        "-Drandomnibble6plus24generator.phase3b.requirePoi=$($RequirePoi.IsPresent.ToString().ToLowerInvariant())",
        "-Drandomnibble6plus24generator.phase3b.expectNoArtifacts=$($expectNoArtifacts.ToString().ToLowerInvariant())",
        "-Drandomnibble6plus24generator.phase3b.scanLimit=$ScanLimit",
        "-Drandomnibble6plus24generator.phase3b.scanStart=$ScanStart",
        "-Drandomnibble6plus24generator.phase3b.scanMatches=$ScanMatches",
        "-Drandomnibble6plus24generator.phase3b.output=$($output.Replace('\','/'))"
    )
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Phase 3B server failed: $LASTEXITCODE" }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3B result failed: $result" }
    return $result
}

try {
    Push-Location $repositoryRoot
    if ($Mode -ne 'reload' -or $FeaturesSaveThenLight -or $InitializeSaveThenLight) {
        Remove-World
        New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
        Write-HiddenPresetPack
    } elseif (-not (Test-Path -LiteralPath $worldPath)) {
        throw "Reload mode requires existing world: $worldPath"
    }

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-type' 'randomnibble6plus24generator:phase3b_hidden_mosaic'
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if ($InitializeSaveThenLight) {
        $initializePath = [IO.Path]::ChangeExtension($resultPath, '.initialize.json')
        $initialize = Invoke-Phase3B 'initialize-save' $initializePath $false
        if ($initialize.persistedIntermediateStatus -ne 'minecraft:initialize_light') {
            throw "Phase 3B INITIALIZE_LIGHT preparation failed: $initialize"
        }
        $result = Invoke-Phase3B 'reload' $resultPath $true
    } elseif ($FeaturesSaveThenLight) {
        $phase3aPath = [IO.Path]::ChangeExtension($resultPath, '.features.json')
        $env:JAVA_TOOL_OPTIONS = @(
            '-Drandomnibble6plus24generator.phase3a.patch.verify=patch',
            "-Drandomnibble6plus24generator.phase3a.patch.dimension=$Dimension",
            "-Drandomnibble6plus24generator.phase3a.patch.chunkX=$ChunkX",
            "-Drandomnibble6plus24generator.phase3a.patch.chunkZ=$ChunkZ",
            '-Drandomnibble6plus24generator.phase3a.patch.order=row-major',
            "-Drandomnibble6plus24generator.phase3a.patch.output=$($phase3aPath.Replace('\','/'))"
        ) -join ' '
        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName" | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "Phase 3A FEATURES preparation failed: $LASTEXITCODE" }
        $result = Invoke-Phase3B 'reload' $resultPath $true
    } else {
        $result = Invoke-Phase3B $Mode $resultPath ($Mode -eq 'reload')
    }

    if ($VerifyReload) {
        $reloadPath = [IO.Path]::ChangeExtension($resultPath, '.reload.json')
        $reload = Invoke-Phase3B 'reload' $reloadPath $true
        if ($reload.artifactRegeneration -ne 0 -or -not $reload.reloadedLightCorrect) {
            throw "Phase 3B reload result failed: $reload"
        }
        if ($result.lightDataHash -and $reload.lightDataHash -ne $result.lightDataHash) {
            throw "Phase 3B light data changed across reload: $($result.lightDataHash) != $($reload.lightDataHash)"
        }
    }
    $result | ConvertTo-Json -Depth 10
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    if (-not $KeepWorld) { Remove-World }
    Pop-Location
}
