param([long[]]$HostSeeds = @(0, 42, -42), [switch]$KeepWorlds)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($HostSeeds.Count -lt 3) { throw 'At least three host seeds are required.' }

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$matrixRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c1f-matrix'
$outputRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c1f-host-independence'
$plan = @(Get-Content -LiteralPath (Join-Path $matrixRoot 'plan.json') -Raw | ConvertFrom-Json)
$selected = @('ow-origin-03','ow-nonorigin-14','nether-47','end-59')
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS

function Remove-FixtureWorld([string]$worldName) {
    $path = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $path) -ne $worldName) {
        throw "Unsafe host-independence cleanup path: $path"
    }
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$manifest = foreach ($id in $selected) {
    $fixture = @($plan | Where-Object id -eq $id)[0]
    $native = Get-Content -LiteralPath (Join-Path $matrixRoot "$id\native.json") -Raw | ConvertFrom-Json
    [pscustomobject]@{
        id=$id; masterSeed=[string]$fixture.masterSeed; localSeed=[string]$fixture.localSeed
        dimension=$fixture.dimension; chunkX=[int]$fixture.chunkX; chunkZ=[int]$fixture.chunkZ
        nativeSnapshot=(Join-Path $matrixRoot "$id\native-feature-stable.bin.gz").Replace('\','/')
        nativeHash=$native.featureHash
        nativeFeatureSeedInvocationCount=[long]$native.featureSeedInvocationCount
        nativeFeatureSeedSequenceHash=$native.featureSeedSequenceHash
        nativeDecorationSeedReads=[long]$native.decorationSeedReads
        nativeFeatureVisibleBiomeSequence=$native.featureVisibleBiomeSequence
    }
}
$manifestPath = Join-Path $outputRoot 'manifest.json'
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8

try {
    Push-Location $repositoryRoot
    $hashes = @{}
    foreach ($hostSeed in $HostSeeds) {
        if (@($manifest | Where-Object {[string]$_.localSeed -eq [string]$hostSeed}).Count -gt 0) {
            throw "Host seed $hostSeed equals a selected local seed"
        }
        $properties = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$hostSeed")
        [IO.File]::WriteAllText($serverPropertiesPath, $properties)
        $resultPath = Join-Path $outputRoot "host-$hostSeed.json"
        $worldName = "phase2c1f-host-independence-$hostSeed"
        Remove-FixtureWorld $worldName
        $env:JAVA_TOOL_OPTIONS = @(
            "-Drandomnibble6plus24generator.phase2c1f.manifest=$($manifestPath.Replace('\','/'))",
            "-Drandomnibble6plus24generator.phase2c1f.output=$($resultPath.Replace('\','/'))"
        ) -join ' '
        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $resultPath)) {
            throw "Host-independence process failed for host $hostSeed"
        }
        if (-not $KeepWorlds) { Remove-FixtureWorld $worldName }
        $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if ($result.status -ne 'PASS' -or $result.fixtures -ne 4) { throw "Host result failed: $result" }
        foreach ($item in $result.results) {
            if ($hashes.ContainsKey($item.id) -and $hashes[$item.id] -ne $item.hash) {
                throw "Host seed changed canonical hash for $($item.id)"
            }
            $hashes[$item.id] = $item.hash
        }
        Write-Host "PHASE2C1F_HOST_PASS host=$hostSeed fixtures=4"
    }
    [pscustomobject]@{status='PASS';localWorlds=4;hostSeeds=$HostSeeds;comparisons=4*$HostSeeds.Count;hashes=$hashes}|ConvertTo-Json -Depth 5
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}
