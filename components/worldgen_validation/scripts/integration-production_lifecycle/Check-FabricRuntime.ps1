param(
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='all_quart_biomes',
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='',
    [ValidateSet(1,2)][int]$ExpectedRuntimeSchema=2,
    [long]$CpuAffinity=61440
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($CpuAffinity -le 0 -or [Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4){throw 'Four logical CPUs required'}
if($ExpectedRuntimeSchema -eq 1 -and !$ExpectedInputHash){throw 'Schema 1 is historical and requires its explicit source input'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target='26.3-fabric'
$base=Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production"
$files=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
$body=($files|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
$body+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))}
$pids=[Collections.Generic.HashSet[long]]::new()
$runs=[Collections.Generic.HashSet[string]]::new()
foreach($dimension in @('minecraft:overworld','minecraft:the_nether','minecraft:the_end')){
    foreach($vanilla in @($true,$false)){
        $prefix=if($vanilla){'vanilla-control'}else{'features-2/development-b'}
        $leaf=Join-Path $base "$prefix/runtime-proof/$($dimension.Replace(':','-'))/$EvidenceLabel"
        $conditions=Get-Content (Join-Path $leaf 'conditions-full-125--37.json') -Raw|ConvertFrom-Json
        if($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.dimension -cne $dimension -or
                $conditions.x -ne 125 -or $conditions.z -ne -37 -or $conditions.masterSeed -ne 123456789 -or
                $conditions.cpu -ne 4 -or $conditions.affinity -ne $CpuAffinity -or !$conditions.verifyReload -or $conditions.mode -cne 'full' -or
                $conditions.evidenceLabel -cne $EvidenceLabel -or $conditions.developmentB -eq $vanilla -or
                $conditions.featureOrdering -ne $(if($vanilla){1}else{2}) -or
                !$runs.Add([string]$conditions.runIdentity)){throw 'Wrong/stale/duplicate Fabric runtime conditions'}
        $ownerBiomes=$null
        foreach($mode in @('full','reload')){
            $row=Get-Content (Join-Path $leaf "$mode-125--37.json") -Raw|ConvertFrom-Json
            $proof=$row.runtimeProof
            if($row.status -cne 'PASS' -or $row.mode -cne $mode -or $row.dimension -cne $dimension -or
                    $row.target -cne '[125, -37]' -or $row.worldSeed -ne 123456789 -or
                    $row.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or
                    $row.fullStatus -cne 'minecraft:full' -or !$row.lightCorrect -or !$row.markerPresent -or
                    !$pids.Add([long]$proof.pid)){throw 'Incomplete/duplicate Fabric runtime evidence'}
            if($proof.schema -ne $ExpectedRuntimeSchema -or !$proof.nativeTickLoop -or !$proof.bothEntityTickingFuturesResolved -or
                    $proof.ticketFullFrontier -ne 49 -or !$proof.quiescentFrontierBeforeFixtures -or
                    !$proof.testFixturesNotCanonicalGenerationCoverage -or $proof.simulationTicks -lt 220 -or
                    $proof.entityCallbacks -lt 220 -or $proof.furnaceCallbacks -lt 220 -or
                    $proof.blockCallbacks -le 0 -or $proof.fluidCallbacks -le 0 -or !$proof.entityCrossedPhysicalSeam -or
                    $proof.entityRegistrations -ne 1 -or !$proof.fluidCrossedPhysicalSeam -or !$proof.furnaceCooked -or
                    !$proof.probeTicketsRemoved -or $proof.generationBindings -ne 0 -or $proof.spawnBindings -ne 0 -or
                    $proof.finalMaterializer.inFlightCount -ne 0){throw 'Missing native simulation/cleanup boundary'}
            if($ExpectedRuntimeSchema -eq 2){
                if($proof.PSObject.Properties.Name -notcontains 'physicalBiomeSections' -or
                        $proof.biomeQuartCoverageVersion -ne 1 -or $proof.physicalBiomeCells -ne $proof.physicalBiomeSections*64){throw 'Incomplete physical biome quart coverage'}
                $hashes=@($proof.ownerBiomeHashes.PSObject.Properties)
                if($proof.physicalBiomeOwners -ne 49 -or $proof.physicalBiomeChecks -le 0 -or $proof.physicalBiomeCells -le 0 -or
                        $proof.physicalBiomeChunks -lt 49 -or $hashes.Count -ne 49 -or
                        @($hashes|Where-Object {$_.Value -cnotmatch '^[a-f0-9]{64}$'}).Count -ne 0 -or
                        !$proof.biomeQueriesPreservedLoadedCountAndGeneration -or
                        $proof.targetFullChunkStatus -cne 'ENTITY_TICKING' -or $proof.neighborFullChunkStatus -cne 'ENTITY_TICKING' -or
                        $proof.dependencyPyramid -cne $(if($mode -eq 'reload'){'loading'}else{'generation'})){throw 'All-owner physical biome inputs were not observed'}
                $current=$proof.ownerBiomeHashes|ConvertTo-Json -Compress
                if($mode -eq 'full'){$ownerBiomes=$current}elseif($ownerBiomes -cne $current){throw 'Owner stored biome hashes changed across save/reload'}
            }
            if($vanilla){
                $materializer=$proof.finalMaterializer
                if(!$row.normalVanilla -or $row.pid -ne $proof.pid -or $row.artifactGenerations -ne 0 -or $row.spawnCalls -ne 0 -or
                        $materializer.artifactCaptureCount -ne 0 -or $materializer.planCount -ne 0 -or $materializer.requestCount -ne 0 -or
                        $row.mosaicPoi.invocationCount -ne 0 -or $proof.finalDevelopmentB.generations -ne 0){throw 'Ordinary Vanilla entered Mosaic generation'}
            }else{
                if($row.featureOrderingVersion -ne 2 -or $proof.ownerLocalSeed -ne $row.localWorldSeed -or
                        $proof.ownerLocalSeed -eq $proof.adjacentLocalSeed -or @($row.lifecycleDuplicates.PSObject.Properties).Count -ne 0){throw 'Wrong B seed/contract or duplicate lifecycle'}
                if($mode -eq 'reload'){
                    foreach($field in @('artifactGenerations','artifactPublishes','targetArtifactGenerations','targetArtifactPublishes','poiReconciliations','initializeLightCalls','lightCalls','spawnCalls','fullCalls')){
                        if($row.$field -ne 0){throw "Saved FULL repeated $field"}
                    }
                    if($row.developmentB.generations -ne 0 -or $proof.finalDevelopmentB.generations -ne 0){throw 'Saved B regenerated'}
                }elseif($row.developmentB.generations -le 0 -or $proof.finalDevelopmentB.generations -le 0 -or
                        $row.physicalBiomeChecks -ne 49){throw 'Cold B production/primary biome readiness not observed'}
            }
        }
    }
}
"PASS: 26.3-fabric three dimensions, 12 fresh/reload JVMs; runtime schema $ExpectedRuntimeSchema, not player entry/packets or an independent per-owner terrain Oracle."
Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_ALL_SRC $hash"}else{"INPUT_SCOPE: CURRENT_ALL_SRC $hash"})
