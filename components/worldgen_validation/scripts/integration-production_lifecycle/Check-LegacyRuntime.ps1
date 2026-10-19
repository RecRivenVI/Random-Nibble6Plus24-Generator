param(
    [switch]$NarrowLocks,
    [switch]$ReuseSources,
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel = 'ticking_activity',
    [ValidateSet('minecraft:overworld','minecraft:the_nether','minecraft:the_end')]
    [string[]]$Dimensions = @('minecraft:overworld','minecraft:the_nether','minecraft:the_end')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = '1.21.1-neoforge'
$root = Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production"
$sources = @(Get-ChildItem (Join-Path $repo "versions/$target/src"), (Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
$fingerprint = ($sources | ForEach-Object { $_.FullName.Substring($repo.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
$fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
$pids = [Collections.Generic.HashSet[long]]::new()
$runs = [Collections.Generic.HashSet[string]]::new()
if ($Dimensions.Count -eq 0 -or @($Dimensions | Sort-Object -Unique).Count -ne $Dimensions.Count) { throw 'Empty/duplicate runtime dimensions' }
foreach ($dimension in $Dimensions) {
    foreach ($vanilla in @($true,$false)) {
        $prefix = if ($vanilla) { 'vanilla-control' } else { 'features-2/development-b/lazy-rings' }
        if ($NarrowLocks -and !$vanilla) { $prefix += '/narrow-locks' }
        if ($ReuseSources -and !$vanilla) { $prefix += '/source-reuse' }
        $leaf = Join-Path $root "$prefix/runtime-proof-frontier/$($dimension.Replace(':','-'))/$EvidenceLabel"
        $conditions = Get-Content -LiteralPath (Join-Path $leaf 'conditions-full-125--37.json') -Raw | ConvertFrom-Json
        if ($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.dimension -cne $dimension -or
                $conditions.x -ne 125 -or $conditions.z -ne -37 -or $conditions.masterSeed -ne 123456789 -or
                $conditions.evidenceLabel -cne $EvidenceLabel -or $conditions.mode -cne 'full' -or !$conditions.verifyReload -or
                $conditions.cpu -ne 4 -or $conditions.developmentB -eq $vanilla -or $conditions.lazyRings -eq $vanilla -or
                !$runs.Add([string]$conditions.runIdentity)) { throw "Wrong/duplicate runtime conditions: $dimension $prefix" }
        foreach ($mode in @('full','reload')) {
            $path = Join-Path $leaf "$mode-125--37.json"
            $row = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            if ($row.status -cne 'PASS') { throw "Runtime scenario is not PASS: $dimension $prefix $mode" }
            $proof = $row.runtimeProof
            if ($NarrowLocks -and !$vanilla -and (!$conditions.narrowLocks -or ($mode -eq 'full' -and $proof.narrowContexts -le 0))) { throw 'Narrow runtime path was not actually used' }
            if ($ReuseSources -and !$vanilla) {
                if (!$NarrowLocks -or !$conditions.reuseSources -or
                        ($mode -eq 'full' -and ($proof.sourceReusePreparations -le 0 -or $proof.sourceReuseHits -le 0 -or $proof.sourceReuseFallbacks -ne 0)) -or
                        ($mode -eq 'reload' -and $proof.sourceReusePreparations -ne 0)) { throw 'Source reuse runtime/zero-regeneration reload was not actually used' }
            }
            if ($row.status -cne 'PASS' -or $row.normalVanilla -ne $vanilla -or $row.mode -cne $mode -or
                    $row.dimension -cne $dimension -or $row.target -cne '[125, -37]' -or $row.worldSeed -ne 123456789 -or
                    $row.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or $row.fullStatus -cne 'minecraft:full' -or
                    $row.nativeFullChunkStatus -cne 'ENTITY_TICKING' -or !$row.lightCorrect -or !$row.markerPresent -or
                    !$pids.Add([long]$row.pid) -or $proof.pid -ne $row.pid -or !(Test-Path -LiteralPath "$path.physical.nbt.gz")) {
                throw "Incomplete/duplicate physical runtime evidence: $dimension $prefix $mode"
            }
            if ($proof.schema -ne 3 -or !$proof.nativeTickLoop -or !$proof.bothEntityTickingFuturesResolved -or
                    $proof.ticketFullFrontier -ne 49 -or $proof.physicalBiomeOwners -ne 49 -or $proof.physicalBiomeChecks -le 0 -or
                    !$proof.quiescentFrontierBeforeFixtures -or !$proof.testFixturesNotCanonicalGenerationCoverage -or
                    $proof.simulationTicks -lt 220 -or $proof.entityCallbacks -lt 220 -or $proof.furnaceCallbacks -lt 220 -or
                    $proof.blockCallbacks -le 0 -or $proof.fluidCallbacks -le 0 -or !$proof.entityCrossedPhysicalSeam -or
                    $proof.entityRegistrations -ne 1 -or !$proof.fluidCrossedPhysicalSeam -or !$proof.furnaceCooked -or
                    $proof.idleTimerResetCalls -ne 1 -or $proof.emptyEntityTicksBeforeFixtures -lt 0 -or
                    !$proof.probeTicketsRemoved -or $proof.generationBindings -ne 0 -or $proof.regionScopeBound -or $proof.spawnScopeBound -or
                    $proof.dependencyPyramid -cne $(if ($mode -eq 'reload') { 'loading' } else { 'generation' })) {
                throw "Natural runtime/readiness/cleanup proof missing: $dimension $prefix $mode"
            }
            if ($vanilla) {
                if ($row.featureOrderingVersion -ne 1 -or $proof.mosaicStateCreations -ne 0 -or $row.artifactGenerations -ne 0 -or
                        $row.spawnCalls -ne 0 -or $proof.PSObject.Properties.Name -contains 'finalPhysicalMetrics') { throw 'Vanilla entered Mosaic' }
            } else {
                $metrics = $proof.finalPhysicalMetrics
                if ($row.featureOrderingVersion -ne 2 -or $proof.mosaicStateCreations -le 0 -or $metrics.inFlight -ne 0 -or
                        $proof.ownerLocalSeed -ne $row.localWorldSeed -or $proof.adjacentLocalSeed -eq $proof.ownerLocalSeed) { throw 'Invalid Mosaic physical ownership' }
                foreach ($field in @('artifacts','publications','biomePreparations','biomePublications','poi','light','spawn','full')) {
                    if ($metrics.$field -ne $row.physicalMetrics.$field) { throw "Simulation repeated $field" }
                }
                if ($mode -eq 'reload') {
                    if ($metrics.artifacts -ne 0 -or $metrics.biomePreparations -ne 0 -or $metrics.spawn -ne 0 -or
                            $row.developmentB.generations -ne 0) { throw 'Runtime reload regenerated canonical/SPAWN state' }
                } elseif ($metrics.artifacts -le 0 -or $metrics.full -lt 49 -or $metrics.spawn -lt 49) { throw 'Missing real cold B frontier' }
            }
        }
    }
}
"PASS: $target $($Dimensions.Count) dimensions, $($pids.Count) independent fresh/reload JVMs; natural physical runtime and Vanilla zero-Mosaic control."
