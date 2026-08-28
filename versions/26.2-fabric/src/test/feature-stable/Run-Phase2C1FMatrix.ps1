param(
    [long]$HostSeed = 987654321,
    [switch]$ForceNative,
    [switch]$KeepWorlds
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$resultRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c1f-matrix'
$planPath = Join-Path $resultRoot 'plan.json'
$manifestPath = Join-Path $resultRoot 'native-manifest.json'
$isolatedResultPath = Join-Path $resultRoot "isolated-host-$HostSeed.json"
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS

function Remove-FixtureWorld([string]$worldName) {
    $worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe Phase 2C1F cleanup path: $worldPath"
    }
    if ((Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing mismatched Phase 2C1F cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

function Set-LevelSeed([string]$seed) {
    $properties = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$seed")
    [IO.File]::WriteAllText($serverPropertiesPath, $properties)
}

function Invoke-Server([string]$worldName) {
    Remove-FixtureWorld $worldName
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
    if ($LASTEXITCODE -ne 0) { throw "Phase 2C1F server failed for ${worldName}: $LASTEXITCODE" }
    if (-not $KeepWorlds) { Remove-FixtureWorld $worldName }
}

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

try {
    Push-Location $repositoryRoot

    Set-LevelSeed '246813579'
    $env:JAVA_TOOL_OPTIONS = "-Drandomnibble6plus24generator.phase2c1f.exportPlan=$($planPath.Replace('\', '/'))"
    Invoke-Server 'phase2c1f-export-plan'
    $plan = @(Get-Content -LiteralPath $planPath -Raw | ConvertFrom-Json)
    if ($plan.Count -ne 72) { throw "Expected 72 Phase 2C1F fixtures, found $($plan.Count)" }

    $manifest = [Collections.Generic.List[object]]::new()
    $index = 0
    foreach ($fixture in $plan) {
        $index++
        if ([string]$HostSeed -eq [string]$fixture.localSeed) {
            throw "Host seed equals fixture local seed: $($fixture.id)"
        }
        $fixtureRoot = Join-Path $resultRoot $fixture.id
        New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
        $nativeResultPath = Join-Path $fixtureRoot 'native.json'
        $nativeSnapshotPath = Join-Path $fixtureRoot 'native-feature-stable.bin.gz'
        $reuse = -not $ForceNative -and (Test-Path -LiteralPath $nativeResultPath) -and (Test-Path -LiteralPath $nativeSnapshotPath)
        if ($reuse) {
            $native = Get-Content -LiteralPath $nativeResultPath -Raw | ConvertFrom-Json
            $reuse = $native.status -eq 'PASS'
        }
        if (-not $reuse) {
            Set-LevelSeed ([string]$fixture.localSeed)
            $env:JAVA_TOOL_OPTIONS = @(
                "-Drandomnibble6plus24generator.phase2c1.native.masterSeed=$($fixture.masterSeed)",
                "-Drandomnibble6plus24generator.phase2c1.native.dimension=$($fixture.dimension)",
                "-Drandomnibble6plus24generator.phase2c1.native.chunkX=$($fixture.chunkX)",
                "-Drandomnibble6plus24generator.phase2c1.native.chunkZ=$($fixture.chunkZ)",
                "-Drandomnibble6plus24generator.phase2c1.native.output=$($nativeResultPath.Replace('\', '/'))",
                "-Drandomnibble6plus24generator.phase2c1.native.snapshotOutput=$($nativeSnapshotPath.Replace('\', '/'))"
            ) -join ' '
            # Overworld must capture before spawn selection; Nether/End do not exist at that hook and are untouched by it.
            if ($fixture.dimension -eq 'minecraft:overworld') {
                $env:JAVA_TOOL_OPTIONS += ' -Drandomnibble6plus24generator.phase2c1.native.runBeforeInitialSpawn=true'
            }
            Invoke-Server "phase2c1f-native-$($fixture.id)"
            $native = Get-Content -LiteralPath $nativeResultPath -Raw | ConvertFrom-Json
        }
        $invalidNative = ($native.status -ne 'PASS') -or ($native.writers -ne 9) `
                -or ($native.maxConcurrentFeatureWriters -ne 1) `
                -or ($native.requestedWriters -ne $native.completedWriters)
        if ($invalidNative) {
            throw "Invalid native canonical trace for $($fixture.id): $native"
        }
        $manifest.Add([pscustomobject]@{
            id = $fixture.id
            masterSeed = [string]$fixture.masterSeed
            localSeed = [string]$fixture.localSeed
            dimension = $fixture.dimension
            chunkX = [int]$fixture.chunkX
            chunkZ = [int]$fixture.chunkZ
            nativeSnapshot = $nativeSnapshotPath.Replace('\', '/')
            nativeHash = $native.featureHash
            nativeFeatureSeedInvocationCount = [long]$native.featureSeedInvocationCount
            nativeFeatureSeedSequenceHash = $native.featureSeedSequenceHash
            nativeDecorationSeedReads = [long]$native.decorationSeedReads
            nativeFeatureVisibleBiomeSequence = $native.featureVisibleBiomeSequence
        })
        Write-Host "PHASE2C1F_NATIVE_PASS $index/72 $($fixture.id) $($native.featureHash)"
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8

    Set-LevelSeed ([string]$HostSeed)
    $env:JAVA_TOOL_OPTIONS = @(
        "-Drandomnibble6plus24generator.phase2c1f.manifest=$($manifestPath.Replace('\', '/'))",
        "-Drandomnibble6plus24generator.phase2c1f.output=$($isolatedResultPath.Replace('\', '/'))"
    ) -join ' '
    Invoke-Server "phase2c1f-isolated-host-$HostSeed"
    $summary = Get-Content -LiteralPath $isolatedResultPath -Raw | ConvertFrom-Json
    if ($summary.status -ne 'PASS' -or $summary.fixtures -ne 72) {
        throw "Phase 2C1F isolated batch failed: $summary"
    }
    $summary | ConvertTo-Json -Depth 8
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}
