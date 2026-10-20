param(
    [ValidateSet('26.3-fabric','26.3-neoforge')][string]$Target='26.3-fabric',
    [ValidateSet('all','end_spike','monster_room','trial','sulfur_template','nether_chests')][string]$Fixture='all',
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='natural_worldgen',
    [switch]$CheckOnly,
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='',
    [long]$CpuAffinity=3840
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical source identity is read-only'}
if($CpuAffinity -le 0 -or [Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4){throw 'Four logical CPUs required'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$cases=@(
    @{id='sulfur_template';master=123456789;dimension='minecraft:overworld';x=129;z=-42;proof='block_entities'},
    @{id='end_spike';master=123456789;dimension='minecraft:the_end';x=2;z=0;proof='entities'},
    @{id='monster_room';master=246813580;dimension='minecraft:overworld';x=645;z=-623;proof='block_entities'},
    @{id='trial';master=123456789;dimension='minecraft:overworld';x=-13;z=-14;proof='block_entities'}
)
if($Fixture -ceq 'nether_chests'){
    $cases=@(@{id='nether_chests';master=123456789;dimension='minecraft:the_nether';x=-10;z=-10;proof='block_entities'})
}elseif($Fixture -ne 'all'){$cases=@($cases|Where-Object id -CEQ $Fixture)}
if($cases.Count -ne $(if($Fixture -ceq 'all'){4}else{1})){throw 'Natural fixture selection is incomplete'}
if(!$CheckOnly){
    $env:MOSAIC_VALIDATION_TARGET=$target
    foreach($case in $cases){
        & (Join-Path $PSScriptRoot 'Run-Phase3C2Production.ps1') -MasterSeed $case.master -Dimension $case.dimension -ChunkX $case.x -ChunkZ $case.z -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -NaturalProof $case.proof -VerifyReload -Mutate -EvidenceLabel ($EvidenceLabel+'_'+$case.id) -CpuAffinity $CpuAffinity -Offline
        "Completed natural lifecycle $($case.id)"
    }
}
$files=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
$body=($files|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
$body+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))}
$pids=[Collections.Generic.HashSet[long]]::new()
foreach($case in $cases){
    $runtimeFolder=if($Target -ceq '26.3-neoforge'){'runtime-proof-frontier'}else{'runtime-proof'}
    $folder=Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production/features-2/development-b/$runtimeFolder/$($case.dimension.Replace(':','-'))/$($EvidenceLabel+'_'+$case.id)"
    $conditions=Get-Content (Join-Path $folder "conditions-full-$($case.x)-$($case.z).json") -Raw|ConvertFrom-Json
    if($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.dimension -cne $case.dimension -or
            $conditions.masterSeed -ne $case.master -or $conditions.x -ne $case.x -or $conditions.z -ne $case.z -or $conditions.naturalProof -cne $case.proof -or
            !$conditions.verifyReload -or !$conditions.developmentB -or $conditions.featureOrdering -ne 2 -or $conditions.cpu -ne 4 -or $conditions.affinity -ne $CpuAffinity){throw 'Wrong/stale natural fixture conditions'}
    $first=$null
    foreach($mode in @('full','reload')){
        $row=Get-Content (Join-Path $folder "$mode-$($case.x)-$($case.z).json") -Raw|ConvertFrom-Json
        $natural=$row.runtimeProof.natural
        $close=Get-Content (Join-Path $folder "natural-close-$mode.json") -Raw|ConvertFrom-Json
        if($row.status -cne 'PASS' -or $row.mode -cne $mode -or $row.dimension -cne $case.dimension -or $row.worldSeed -ne $case.master -or
                $row.target -cne "[$($case.x), $($case.z)]" -or $row.fullStatus -cne 'minecraft:full' -or !$row.lightCorrect -or !$row.markerPresent -or
                !$pids.Add([long]$row.runtimeProof.pid) -or $row.runtimeProof.schema -ne 2 -or $row.runtimeProof.simulationTicks -lt 220 -or
                $row.runtimeProof.targetFullChunkStatus -cne 'ENTITY_TICKING' -or
                $row.runtimeProof.generationBindings -ne 0 -or $row.runtimeProof.spawnBindings -ne 0 -or $row.runtimeProof.finalMaterializer.inFlightCount -ne 0 -or
                $natural.schema -ne 1 -or $natural.inputHash -cne $hash -or $natural.probeCreatedPhysicalEntitiesOrBlockEntities -or !$natural.nativeBlockEntityLoadControlExact -or
                $natural.sourceEntities -ne $natural.liveEntities -or $natural.sourceBlockEntities -ne $natural.liveBlockEntities -or
                $natural.scope -cne 'NATURAL_INPUT_ACTIVATION_AND_IDENTITY_NOT_RUNTIME_NBT_IMMUTABILITY' -or
                !$close.completed -or $close.inputHash -cne $hash -or $close.pid -ne $row.runtimeProof.pid -or $close.retainedEntities -ne 0 -or
                $close.retainedBlockEntities -ne 0 -or $close.sourceRetained -or $close.levelRetained -or $close.serverRetained){throw 'Incomplete natural activation/tick/native-save/cleanup proof'}
        if($Target -ceq '26.3-fabric' -and $row.runtimeProof.physicalBiomeCells -ne $row.runtimeProof.physicalBiomeSections*64){throw 'Incomplete Fabric stored-biome cells proof'}
        if($Target -ceq '26.3-neoforge' -and $row.runtimeProof.physicalBiomeChecks -le 0){throw 'Missing NeoForge physical biome dependency proof'}
        if(($case.proof -ceq 'entities' -and ($natural.sourceEntities -le 0 -or $natural.actualEndCrystalTicks -le 0)) -or
                ($case.proof -ceq 'block_entities' -and $natural.sourceBlockEntities -le 0)){throw 'Natural positive was not hit'}
        if($case.id -ceq 'sulfur_template' -and $natural.sourcePendingBlockEntities -le 0){throw 'Natural pending BE positive was not hit'}
        foreach($file in @('natural-source.nbt',"natural-live-$mode.nbt","natural-saved-$mode.nbt")){
            if(!(Test-Path (Join-Path $folder $file))){throw 'Missing raw natural evidence'}
        }
        if($mode -ceq 'full'){
            if($natural.capturedPublications -ne 1){throw 'Natural data did not come from actual publication'}
            $first=$natural
        }else{
            if($natural.capturedPublications -ne 0 -or $row.developmentB.generations -ne 0){throw 'Saved natural target regenerated'}
            foreach($field in @('artifactGenerations','artifactPublishes','targetArtifactGenerations','targetArtifactPublishes','poiReconciliations','initializeLightCalls','lightCalls','spawnCalls','fullCalls')){
                if($row.$field -ne 0){throw "Saved natural FULL repeated $field"}
            }
            foreach($field in @('sourceArtifact','sourceEntities','sourceBlockEntities','sourcePendingBlockEntities')){
                if($natural.$field -cne $first.$field){throw 'Natural source changed across actual restart'}
            }
        }
    }
}
"PASS: $($cases.Count) natural fixtures, $($pids.Count) fresh/reload JVMs; actual published input, native installation, physical tick loop, save and identity preservation. Not immutable runtime NBT or a new content Oracle."
Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_ALL_SRC $hash"}else{"INPUT_SCOPE: CURRENT_ALL_SRC $hash"})
