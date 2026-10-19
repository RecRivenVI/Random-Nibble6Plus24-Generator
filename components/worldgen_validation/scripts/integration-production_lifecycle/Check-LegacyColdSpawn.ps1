param([ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='light_restart')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target='1.21.1-neoforge'
$root=Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/cold-spawn/minecraft-overworld/$EvidenceLabel"
$files=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -File -Recurse|Sort-Object FullName)
$body=($files|ForEach-Object {$_.FullName.Substring($repo.Length)+':'+(Get-FileHash -LiteralPath $_.FullName).Hash}) -join "`n"
$body += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))
$conditions=Get-Content -LiteralPath (Join-Path $root 'conditions-full-125--37.json') -Raw|ConvertFrom-Json
if($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.masterSeed -ne 123456789 -or
        $conditions.x -ne 125 -or $conditions.z -ne -37 -or !$conditions.coldSpawn -or !$conditions.reuseSources -or
        !$conditions.lazyRings -or !$conditions.narrowLocks -or !$conditions.verifyReload -or $conditions.cpu -ne 4 -or
        $conditions.dimension -cne 'minecraft:overworld' -or $conditions.featureOrdering -ne 2 -or !$conditions.developmentB){throw 'Wrong/stale cold checkpoint conditions'}
$stage=Get-Content -LiteralPath (Join-Path $root 'stage.json') -Raw|ConvertFrom-Json
$resume=Get-Content -LiteralPath (Join-Path $root 'resume.json') -Raw|ConvertFrom-Json
$warm=Get-Content -LiteralPath (Join-Path $root 'warm-inputs.json') -Raw|ConvertFrom-Json
foreach($proof in @($stage,$resume)){
    if($proof.status -cne 'PASS' -or $proof.schema -ne 1 -or $proof.inputHash -cne $hash -or
            $proof.target -cne '[125, -37]' -or $proof.dimension -cne 'minecraft:overworld' -or
            !$proof.afterNativeLevelClose -or $proof.inFlightAfterClose -ne 0 -or $proof.workersAfterClose -ne 0){throw 'Incomplete cold native lifecycle proof'}
}
if($stage.pid -eq $resume.pid -or $stage.mode -cne 'stage' -or !$stage.expectedShutdownCancellation -or $stage.heldCancellationCompletions -ne 1 -or $stage.closingNotifications -lt 1 -or
        $stage.populationCalls -ne 0 -or $stage.targetCanonicalPreparations -lt 1 -or $resume.mode -cne 'resume' -or
        $resume.loadedStatus -cne 'minecraft:light' -or $resume.populationCalls -ne 1 -or $resume.targetCanonicalPreparations -ne 0 -or
        $resume.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or $resume.fullStatus -cne 'minecraft:full'){throw 'No real saved LIGHT/new-JVM FULL progression'}
$cold=$resume.coldInputs
if(!$cold.biomesEqual -or !$cold.collisionsEqual -or !$cold.transientCollisionSnapshotAbsent -or
        $cold.postPopulationComponentReads -ne 8 -or $cold.coldSourcesAfterComponent -ne 8 -or
        $cold.scope -cne 'POST_POPULATION_COMPONENT_READS_NOT_NATURAL_BOUNDARY_COVERAGE' -or
        $warm.status -cne 'minecraft:light' -or $warm.localSeed -ne -5161763991829980711 -or
        $cold.biomes -cne $warm.biomes -or $cold.collisions -cne $warm.collisions){throw 'Cold inputs differ or component coverage was mislabelled'}
'PASS: independent LIGHT shutdown/new-JVM FULL; exact eight-source cold inputs, not natural collision coverage or performance acceptance.'
