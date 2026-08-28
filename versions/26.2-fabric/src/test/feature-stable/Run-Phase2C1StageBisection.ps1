param(
    [long]$HostSeed = 0,
    [string]$WorldSeed = '-5161763991829980711',
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 123,
    [int]$ChunkZ = -39,
    [string]$FixtureId = 'blocker',
    [string]$BlockProbe = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$safeFixtureId = $FixtureId -replace '[^A-Za-z0-9_.-]', '_'
$outputRoot = Join-Path $repositoryRoot "versions\26.2-fabric\build\phase2c1r-stage-$safeFixtureId"
$nativeEvidence = Join-Path $outputRoot 'native'
$isolatedEvidence = Join-Path $outputRoot "isolated-host-$HostSeed"
$nativeResult = Join-Path $outputRoot 'native-result.json'
$isolatedResult = Join-Path $outputRoot "isolated-host-$HostSeed-result.json"
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$worldSeed = $WorldSeed
$dimension = $Dimension
$chunkX = $ChunkX
$chunkZ = $ChunkZ
$nativeWorld = "phase2c1r-stage-native-$safeFixtureId"
$isolatedWorld = "phase2c1r-stage-$safeFixtureId-isolated-$HostSeed"

function Remove-FixtureWorld([string]$worldName) {
    $worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe path: $worldPath" }
    if ((Split-Path -Leaf $worldPath) -ne $worldName) { throw "Mismatched path: $worldPath" }
    if (Test-Path -LiteralPath $worldPath) { Remove-Item -LiteralPath $worldPath -Recurse -Force }
}

function Set-LevelSeed([string]$seed) {
    [IO.File]::WriteAllText($serverPropertiesPath, [regex]::Replace(
        $originalProperties, '(?m)^level-seed=.*$', "level-seed=$seed"))
}

if (Test-Path -LiteralPath $outputRoot) { Remove-Item -LiteralPath $outputRoot -Recurse -Force }
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
Remove-FixtureWorld $nativeWorld
Remove-FixtureWorld $isolatedWorld

try {
    Push-Location $repositoryRoot
    Set-LevelSeed $worldSeed
    $env:JAVA_TOOL_OPTIONS = @(
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.worldSeed=$worldSeed",
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.dimension=$dimension",
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.chunkX=$chunkX",
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.chunkZ=$chunkZ",
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.evidenceRoot=$($nativeEvidence.Replace('\','/'))",
        "-Drandomnibble6plus24generator.phase2c1r.stage.native.summaryOutput=$($nativeResult.Replace('\','/'))"
    ) -join ' '
    if (-not [string]::IsNullOrWhiteSpace($BlockProbe)) {
        $parts = $BlockProbe.Split(',')
        $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockX=$($parts[0]) -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockY=$($parts[1]) -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockZ=$($parts[2])"
    }
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $nativeWorld"
    if ($LASTEXITCODE -ne 0) { throw "Native stage process failed: $LASTEXITCODE" }

    Set-LevelSeed ([string]$HostSeed)
    $env:JAVA_TOOL_OPTIONS = @(
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.worldSeed=$worldSeed",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.dimension=$dimension",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.chunkX=$chunkX",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.chunkZ=$chunkZ",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.evidenceRoot=$($isolatedEvidence.Replace('\','/'))",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.summaryOutput=$($isolatedResult.Replace('\','/'))",
        "-Drandomnibble6plus24generator.phase2c1r.stage.isolated.nativeEvidenceRoot=$($nativeEvidence.Replace('\','/'))"
    ) -join ' '
    if (-not [string]::IsNullOrWhiteSpace($BlockProbe)) {
        $parts = $BlockProbe.Split(',')
        $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockX=$($parts[0]) -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockY=$($parts[1]) -Drandomnibble6plus24generator.phase2c1r.stageProbeBlockZ=$($parts[2])"
    }
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $isolatedWorld"
    if ($LASTEXITCODE -ne 0) { throw "Isolated stage process failed: $LASTEXITCODE" }
    Get-Content -LiteralPath $nativeResult
    Get-Content -LiteralPath $isolatedResult
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
    Remove-FixtureWorld $nativeWorld
    Remove-FixtureWorld $isolatedWorld
}
