param(
    [long]$HostSeed = 0,
    [ValidateSet('empty','generated','mutated')][string]$FrontierState = 'empty',
    [string]$MasterSeed = '123456789',
    [string]$LocalSeed = '-5161763991829980711',
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 125,
    [int]$ChunkZ = -37,
    [string]$FixtureId = 'blocker',
    [switch]$DirectLocalSeed,
    [switch]$ReuseNative,
    [switch]$KeepWorlds
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$safeFixtureId = $FixtureId -replace '[^A-Za-z0-9_.-]', '_'
$outputRoot = Join-Path $repositoryRoot "versions\26.2-fabric\build\phase2c1r-$safeFixtureId"
$nativeEvidence = Join-Path $outputRoot 'native'
$isolatedEvidence = Join-Path $outputRoot "isolated-host-$HostSeed-$FrontierState"
$nativeResult = Join-Path $outputRoot 'native-result.json'
$isolatedResult = Join-Path $outputRoot "isolated-host-$HostSeed-$FrontierState-result.json"
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS

$masterSeed = $MasterSeed
$localSeed = $LocalSeed
$dimension = $Dimension
$chunkX = $ChunkX
$chunkZ = $ChunkZ
$nativeWorld = "phase2c1r-native-$safeFixtureId"
$isolatedWorld = "phase2c1r-$safeFixtureId-host-$HostSeed-$FrontierState"

function Remove-FixtureWorld([string]$worldName) {
    $worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe Phase 2C1R cleanup path: $worldPath"
    }
    if ((Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing mismatched Phase 2C1R cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

function Set-LevelSeed([string]$seed) {
    $properties = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$seed")
    [IO.File]::WriteAllText($serverPropertiesPath, $properties)
}

if ($ReuseNative) {
    if (-not (Test-Path -LiteralPath $nativeEvidence) -or -not (Test-Path -LiteralPath $nativeResult)) {
        throw "Cannot reuse missing native evidence: $nativeEvidence"
    }
    if (Test-Path -LiteralPath $isolatedEvidence) {
        Remove-Item -LiteralPath $isolatedEvidence -Recurse -Force
    }
    if (Test-Path -LiteralPath $isolatedResult) {
        Remove-Item -LiteralPath $isolatedResult -Force
    }
} elseif (Test-Path -LiteralPath $outputRoot) {
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
if (-not $ReuseNative) {
    Remove-FixtureWorld $nativeWorld
}
Remove-FixtureWorld $isolatedWorld

try {
    Push-Location $repositoryRoot

    if (-not $ReuseNative) {
        Set-LevelSeed $localSeed
        $env:JAVA_TOOL_OPTIONS = @(
            "-Drandomnibble6plus24generator.phase2c1.native.masterSeed=$masterSeed",
            "-Drandomnibble6plus24generator.phase2c1.native.dimension=$dimension",
            "-Drandomnibble6plus24generator.phase2c1.native.chunkX=$chunkX",
            "-Drandomnibble6plus24generator.phase2c1.native.chunkZ=$chunkZ",
            "-Drandomnibble6plus24generator.phase2c1.native.output=$($nativeResult.Replace('\', '/'))",
            "-Drandomnibble6plus24generator.phase2c1.native.evidenceRoot=$($nativeEvidence.Replace('\', '/'))"
        ) -join ' '
        if ($DirectLocalSeed) {
            $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1.native.directLocalSeed=$localSeed"
        }
        if ($dimension -eq 'minecraft:overworld') {
            $env:JAVA_TOOL_OPTIONS += ' -Drandomnibble6plus24generator.phase2c1.native.runBeforeInitialSpawn=true'
        }
        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $nativeWorld"
        if ($LASTEXITCODE -ne 0) { throw "Native evidence process failed: $LASTEXITCODE" }
    }

    Set-LevelSeed ([string]$HostSeed)
    $env:JAVA_TOOL_OPTIONS = @(
        "-Drandomnibble6plus24generator.phase2c1r.isolated.masterSeed=$masterSeed",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.dimension=$dimension",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.chunkX=$chunkX",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.chunkZ=$chunkZ",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.nativeEvidenceRoot=$($nativeEvidence.Replace('\', '/'))",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.isolatedEvidenceRoot=$($isolatedEvidence.Replace('\', '/'))",
        "-Drandomnibble6plus24generator.phase2c1r.isolated.summaryOutput=$($isolatedResult.Replace('\', '/'))"
    ) -join ' '
    $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1r.isolated.hostFrontierState=$FrontierState"
    if ($DirectLocalSeed) {
        $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1r.isolated.directLocalSeed=$localSeed"
    }
    if ([string]$HostSeed -eq $localSeed) {
        $env:JAVA_TOOL_OPTIONS += ' -Drandomnibble6plus24generator.phase2c1r.isolated.allowSameHostSeed=true'
    }
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $isolatedWorld"
    if ($LASTEXITCODE -ne 0) { throw "Isolated evidence process failed: $LASTEXITCODE" }

    $nativeSummary = Get-Content -LiteralPath $nativeResult -Raw | ConvertFrom-Json
    $isolatedSummary = Get-Content -LiteralPath $isolatedResult -Raw | ConvertFrom-Json
    $traceMismatch = ($isolatedSummary.status -ne 'MATCH') `
            -or ($nativeSummary.featureSeedInvocationCount -ne $isolatedSummary.featureSeedInvocationCount) `
            -or ($nativeSummary.featureSeedSequenceHash -ne $isolatedSummary.featureSeedSequenceHash) `
            -or ($nativeSummary.decorationSeedReads -ne $isolatedSummary.decorationSeedReads) `
            -or ($nativeSummary.featureVisibleBiomeSequence -ne $isolatedSummary.featureVisibleBiomeSequence) `
            -or ($nativeSummary.requestedWriters -ne $isolatedSummary.requestedWriters) `
            -or ($nativeSummary.completedWriters -ne $isolatedSummary.completedWriters) `
            -or ($nativeSummary.maxConcurrentFeatureWriters -ne $isolatedSummary.maxConcurrentFeatureWriters)
    if ($traceMismatch) {
        throw "Phase 2C1R/2C1F native-isolated trace mismatch: native=$nativeSummary isolated=$isolatedSummary"
    }
    Get-Content -LiteralPath $nativeResult
    Get-Content -LiteralPath $isolatedResult
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
    if (-not $KeepWorlds) {
        if (-not $ReuseNative) {
            Remove-FixtureWorld $nativeWorld
        }
        Remove-FixtureWorld $isolatedWorld
    }
}
