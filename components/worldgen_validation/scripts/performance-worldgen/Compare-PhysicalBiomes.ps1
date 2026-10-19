param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$output = Join-Path $repo 'validations/performance-worldgen/result/b_milestone/26.3-fabric/physical_biome_comparison'
$reducer = Join-Path $PSScriptRoot 'compare_physical_biomes.py'
if ($CheckOnly) {
    & python $reducer $output
    if ($LASTEXITCODE -ne 0) { throw 'Physical BIOMES paired evidence failed' }
    exit 0
}
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$proofRoot = Join-Path $repo 'validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/partial-biomes'
foreach ($coordinate in @('125--37','-11--6','2-0')) {
    foreach ($mode in @('biomes-first','biomes-reload','full','reload')) {
        $proof = Get-Content (Join-Path $proofRoot "$mode-$coordinate.json") -Raw | ConvertFrom-Json
        if ($proof.status -cne 'PASS' -or
                ($mode -eq 'biomes-first' -and !$proof.concurrentUpgradeExactlyOneArtifact) -or
                ($mode -eq 'reload' -and ($proof.artifactGenerations -ne 0 -or $proof.spawnCalls -ne 0 -or !$proof.markerPresent))) {
            throw 'Partial BIOMES correctness/lifecycle gate is incomplete'
        }
    }
}
if (Test-Path -LiteralPath $output) { Move-ValidationToRecycleBin -LiteralPath $output }
New-Item -ItemType Directory -Path $output -Force | Out-Null
foreach ($round in 0..2) {
    $order = if ($round -eq 1) { @('candidate','reference') } else { @('reference','candidate') }
    foreach ($mode in $order) {
        & (Join-Path $PSScriptRoot 'Run-BPerformance.ps1') -Target 26.3-fabric -Pipeline b -View 8 -Simulation 8 -Seconds 60 -ReferencePhysicalBiomes:($mode -eq 'reference')
        $label = if ($mode -eq 'reference') { 'b-physical-biome-reference' } else { 'b' }
        & (Join-Path $PSScriptRoot 'Analyze-BPerformance.ps1') -Target 26.3-fabric -Pipelines @($label)
        $source = Join-Path (Split-Path -Parent $output) "$label-v8-s8"
        $destination = Join-Path $output "pair-$round/$mode"
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Recurse
        Write-Host "PHYSICAL BIOMES PAIR $round $mode archived"
    }
}
& python $reducer $output
if ($LASTEXITCODE -ne 0) { throw 'Physical BIOMES paired evidence failed' }
