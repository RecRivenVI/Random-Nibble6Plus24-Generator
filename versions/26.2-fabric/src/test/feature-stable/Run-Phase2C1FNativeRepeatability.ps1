param([switch]$KeepWorlds)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$matrixRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c1f-matrix'
$plan = @(Get-Content -LiteralPath (Join-Path $matrixRoot 'plan.json') -Raw | ConvertFrom-Json)
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$fixtureIds = @(
    'ow-origin-00','ow-origin-03','ow-origin-06','ow-origin-07',
    'ow-nonorigin-10','ow-nonorigin-14','ow-nonorigin-23','ow-nonorigin-34',
    'nether-40','nether-43','nether-47','nether-55',
    'end-56','end-59','end-63','end-71'
)

function Remove-FixtureWorld([string]$worldName) {
    $path = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $path) -ne $worldName) {
        throw "Unsafe Phase 2C1F repeat cleanup path: $path"
    }
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
}

try {
    Push-Location $repositoryRoot
    $passed = 0
    foreach ($id in $fixtureIds) {
        $fixture = @($plan | Where-Object id -eq $id)
        if ($fixture.Count -ne 1) { throw "Missing repeat fixture $id" }
        $fixture = $fixture[0]
        $baseline = Get-Content -LiteralPath (Join-Path $matrixRoot "$id\native.json") -Raw | ConvertFrom-Json
        $repeatRoot = Join-Path $matrixRoot "$id\repeat-b"
        New-Item -ItemType Directory -Path $repeatRoot -Force | Out-Null
        $resultPath = Join-Path $repeatRoot 'native.json'
        $snapshotPath = Join-Path $repeatRoot 'native-feature-stable.bin.gz'
        $worldName = "phase2c1f-repeat-$id"
        Remove-FixtureWorld $worldName
        $properties = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$($fixture.localSeed)")
        [IO.File]::WriteAllText($serverPropertiesPath, $properties)
        $env:JAVA_TOOL_OPTIONS = @(
            "-Drandomnibble6plus24generator.phase2c1.native.masterSeed=$($fixture.masterSeed)",
            "-Drandomnibble6plus24generator.phase2c1.native.dimension=$($fixture.dimension)",
            "-Drandomnibble6plus24generator.phase2c1.native.chunkX=$($fixture.chunkX)",
            "-Drandomnibble6plus24generator.phase2c1.native.chunkZ=$($fixture.chunkZ)",
            "-Drandomnibble6plus24generator.phase2c1.native.output=$($resultPath.Replace('\', '/'))",
            "-Drandomnibble6plus24generator.phase2c1.native.snapshotOutput=$($snapshotPath.Replace('\', '/'))"
        ) -join ' '
        if ($fixture.dimension -eq 'minecraft:overworld') {
            $env:JAVA_TOOL_OPTIONS += ' -Drandomnibble6plus24generator.phase2c1.native.runBeforeInitialSpawn=true'
        }
        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
        if ($LASTEXITCODE -ne 0) { throw "Repeat native process failed for $id" }
        if (-not $KeepWorlds) { Remove-FixtureWorld $worldName }
        $repeat = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        $same = ($repeat.featureHash -eq $baseline.featureHash) `
                -and ($repeat.featureSeedInvocationCount -eq $baseline.featureSeedInvocationCount) `
                -and ($repeat.featureSeedSequenceHash -eq $baseline.featureSeedSequenceHash) `
                -and ($repeat.decorationSeedReads -eq $baseline.decorationSeedReads) `
                -and ($repeat.featureVisibleBiomeSequence -eq $baseline.featureVisibleBiomeSequence) `
                -and ($repeat.requestedWriters -eq $baseline.requestedWriters) `
                -and ($repeat.completedWriters -eq $baseline.completedWriters) `
                -and ($repeat.canonicalEntityNbt -eq $baseline.canonicalEntityNbt)
        if (-not $same) { throw "Same-order native repeatability failed for $id" }
        if ($baseline.entities -gt 0 -and $repeat.rawEntityNbt -eq $baseline.rawEntityNbt) {
            throw "Entity repeat fixture did not demonstrate raw UUID variation: $id"
        }
        $passed++
        Write-Host "PHASE2C1F_NATIVE_REPEAT_PASS $passed/$($fixtureIds.Count) $id $($repeat.featureHash)"
    }
    [pscustomobject]@{status='PASS';fixtures=$passed;ids=$fixtureIds}|ConvertTo-Json -Depth 4
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}
