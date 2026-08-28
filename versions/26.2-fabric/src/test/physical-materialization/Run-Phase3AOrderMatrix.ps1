$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runner = Join-Path $PSScriptRoot 'Run-Phase3APhysicalMaterialization.ps1'
$outputRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3a-orders'
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$orders = @('row-major', 'reverse', 'shuffle', 'parallel')
$results = @{}

foreach ($order in $orders) {
    $output = Join-Path $outputRoot "$order.json"
    if (-not (Test-Path -LiteralPath $output)) {
        & $runner -MasterSeed 123456789 -Dimension minecraft:overworld -ChunkX 48 -ChunkZ 48 `
            -PatchShape patch -PatchOrder $order -ResultPath $output
        if ($LASTEXITCODE -ne 0) { throw "Phase 3A order run failed: $order" }
    }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS' -or $result.chunks -ne 9) { throw "Invalid Phase 3A order result: $order" }
    $map = @{}
    foreach ($chunk in $result.results) { $map["$($chunk.x),$($chunk.z)"] = $chunk.hash }
    $results[$order] = $map
}

$baseline = $results['row-major']
foreach ($order in $orders) {
    foreach ($key in $baseline.Keys) {
        if ($results[$order][$key] -ne $baseline[$key]) {
            throw "Physical request order changed ${key}: row-major=$($baseline[$key]) $order=$($results[$order][$key])"
        }
    }
}

$modifiedOutput = Join-Path $outputRoot 'modified-neighbor.json'
& $runner -MasterSeed 123456789 -Dimension minecraft:overworld -ChunkX 64 -ChunkZ 64 `
    -PatchShape modified-neighbor -PatchOrder row-major -ResultPath $modifiedOutput
$modified = Get-Content -LiteralPath $modifiedOutput -Raw | ConvertFrom-Json
if ($modified.status -ne 'PASS' -or -not $modified.markerPreserved) {
    throw 'Modified physical neighbor was not preserved'
}

[pscustomobject]@{
    status = 'PASS'
    orders = $orders
    chunksPerOrder = 9
    identicalChunkHashes = 9
    modifiedNeighborPreserved = $true
} | ConvertTo-Json -Depth 5 | Tee-Object -FilePath (Join-Path $outputRoot 'summary.json')
