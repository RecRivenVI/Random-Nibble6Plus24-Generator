param(
    [ValidateSet('26.3-fabric','26.3-neoforge')][string]$Target='26.3-fabric',
    [switch]$CheckOnly,
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='climate_state_requests',
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='',
    [long]$CpuAffinity=3840
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '../Native-RequestEvidence.ps1')
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical source identity is read-only'}
if($CpuAffinity -le 0 -or [Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4){throw 'Four logical CPUs required'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target=$Target
$root=Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production/features-2/development-b/request-proof"
$dimensions=@('minecraft:overworld','minecraft:the_nether','minecraft:the_end')
$modes=@('concurrent','pair','patch')
if(!$CheckOnly){
    $env:MOSAIC_VALIDATION_TARGET=$target
    foreach($dimension in $dimensions){
        foreach($mode in $modes){
            & (Join-Path $PSScriptRoot 'Run-Phase3C2Production.ps1') -Dimension $dimension -Mode $mode -Repeats 32 -FeatureOrderingVersion 2 -DevelopmentB -RequestProof -EvidenceLabel $EvidenceLabel -CpuAffinity $CpuAffinity -Offline
            "Completed production requests $dimension $mode"
        }
    }
}
$files=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
$body=($files|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
$body+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))}
$pids=[Collections.Generic.HashSet[long]]::new()
$runs=[Collections.Generic.HashSet[string]]::new()
$worlds=[Collections.Generic.HashSet[string]]::new()
foreach($dimension in $dimensions){
    $seeds=@{}
    foreach($mode in $modes){
        $folder=Join-Path $root "$($dimension.Replace(':','-'))/$EvidenceLabel"
        $conditions=Get-Content (Join-Path $folder "conditions-$mode-125--37.json") -Raw|ConvertFrom-Json
        $result=Get-Content (Join-Path $folder "$mode-125--37.json") -Raw|ConvertFrom-Json
        if($conditions.target -cne $target -or $conditions.dimension -cne $dimension -or $conditions.mode -cne $mode -or
                $conditions.x -ne 125 -or $conditions.z -ne -37 -or $conditions.masterSeed -ne 123456789 -or
                $conditions.inputHash -cne $hash -or $conditions.evidenceLabel -cne $EvidenceLabel -or !$conditions.requestProof -or
                $conditions.featureOrdering -ne 2 -or !$conditions.developmentB -or $conditions.cpu -ne 4 -or $conditions.affinity -ne $CpuAffinity -or
                $conditions.verifyReload -or $conditions.repeats -ne 32 -or !$runs.Add([string]$conditions.runIdentity)){throw 'Wrong/stale production request conditions'}
        if($target -ceq '26.3-neoforge'){
            $raw=Join-Path $folder "$mode-125--37.json"
            $proof=Read-NativeRequestSidecar -RawPath $raw -RawResult $result
        }else{$proof=$result.requestProof}
        $expected=@('[125, -37]')
        if($mode -ceq 'pair'){$expected+='[126, -37]'}
        if($mode -ceq 'patch'){$expected=@();foreach($z in -38..-36){foreach($x in 124..126){$expected+="[$x, $z]"}}}
        $count=if($mode -ceq 'concurrent'){32}else{$expected.Count}
        if($result.status -cne 'PASS' -or $result.mode -cne $mode -or $result.dimension -cne $dimension -or
                $result.target -cne '[125, -37]' -or $result.worldSeed -ne 123456789 -or $result.featureOrderingVersion -ne 2 -or
                $result.ordinaryRequestCount -ne $count -or $result.returnedFullChunks -ne $count -or
                $result.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or $result.fullStatus -cne 'minecraft:full' -or !$result.lightCorrect -or
                $result.developmentB.generations -le 0 -or $result.targetArtifactGenerations -ne 1 -or $result.targetArtifactPublishes -ne 1 -or
                $result.physicalBiomeChecks -le 0 -or @($result.lifecycleDuplicates.PSObject.Properties).Count -ne 0 -or
                $proof.schema -ne 1 -or $proof.scope -cne 'ORDINARY_FULL_REQUESTS_NOT_TICKING_OR_PLAYER_ENTRY' -or
                $proof.inputHash -cne $hash -or $proof.logicalProcessors -ne 4 -or $proof.distinctChunkObjects -ne $expected.Count -or
                !$worlds.Add([string]$proof.save) -or
                [IO.Path]::GetFullPath($proof.save).TrimEnd([IO.Path]::DirectorySeparatorChar) -cne [IO.Path]::GetFullPath($conditions.world).TrimEnd([IO.Path]::DirectorySeparatorChar) -or
                $proof.bindings -ne 0 -or $proof.spawnBindings -ne 0 -or !$pids.Add([long]$proof.pid)){throw 'Incomplete ordinary physical FULL/dedup proof'}
        $actual=@($proof.owners|ForEach-Object{"[$($_.x), $($_.z)]"})
        if(($actual -join ';') -cne ($expected -join ';')){throw 'Wrong or duplicate requested physical owner'}
        foreach($owner in $proof.owners){
            if($owner.artifactCaptures -ne 1 -or $owner.publications -ne 1){throw 'Requested owner was materialized more/less than once'}
            $key="[$($owner.x), $($owner.z)]"
            if($seeds.ContainsKey($key) -and $seeds[$key] -cne [string]$owner.localSeed){throw 'Local seed changed across request shapes'}
            $seeds[$key]=[string]$owner.localSeed
        }
        if($mode -ceq 'pair' -and $result.adjacentLocalWorldSeed -eq $result.localWorldSeed){throw 'Adjacent owners reused a seed'}
    }
}
'PASS: three dimensions x 32 duplicate / adjacent pair / nine-owner patch; nine fresh JVM/save results, exact once per requested physical owner. Not ticking, player entry or packets.'
Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_ALL_SRC $hash"}else{"INPUT_SCOPE: CURRENT_ALL_SRC $hash"})
