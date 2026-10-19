param(
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel = 'native_frontier',
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash = '',
    [ValidateSet('minecraft:overworld','minecraft:the_nether','minecraft:the_end')]
    [string[]]$Dimensions = @('minecraft:overworld','minecraft:the_nether','minecraft:the_end')
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target='26.3-neoforge'
$base=Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production"
$files=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
$body=($files|ForEach-Object { $_.FullName.Substring($repo.Length)+':'+(Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
$body += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))
if($ExpectedInputHash){$hash=$ExpectedInputHash.ToUpperInvariant()}
$pids=[Collections.Generic.HashSet[long]]::new()
$runs=[Collections.Generic.HashSet[string]]::new()
if($Dimensions.Count -eq 0 -or @($Dimensions|Sort-Object -Unique).Count -ne $Dimensions.Count){throw 'No/duplicate runtime dimensions'}
foreach($dimension in $Dimensions){
    foreach($vanilla in @($true,$false)){
        $prefix=if($vanilla){'vanilla-control'}else{'features-2/development-b'}
        $leaf=Join-Path $base "$prefix/runtime-proof-frontier/$($dimension.Replace(':','-'))/$EvidenceLabel"
        $conditions=Get-Content -LiteralPath (Join-Path $leaf 'conditions-full-125--37.json') -Raw|ConvertFrom-Json
        if($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.dimension -cne $dimension -or
                $conditions.x -ne 125 -or $conditions.z -ne -37 -or $conditions.masterSeed -ne 123456789 -or
                $conditions.cpu -ne 4 -or !$conditions.verifyReload -or $conditions.mode -cne 'full' -or
                $conditions.evidenceLabel -cne $EvidenceLabel -or
                $conditions.developmentB -eq $vanilla -or !$runs.Add([string]$conditions.runIdentity)) {throw 'Wrong/stale/duplicate native runtime conditions'}
        foreach($mode in @('full','reload')){
            $row=Get-Content -LiteralPath (Join-Path $leaf "$mode-125--37.json") -Raw|ConvertFrom-Json
            $proof=$row.runtimeProof
            if($row.status -cne 'PASS' -or $row.mode -cne $mode -or $row.dimension -cne $dimension -or
                    $row.target -cne '[125, -37]' -or $row.worldSeed -ne 123456789 -or
                    $row.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or
                    $row.fullStatus -cne 'minecraft:full' -or !$row.lightCorrect -or !$row.markerPresent -or
                    !$pids.Add([long]$proof.pid)) {throw 'Incomplete/duplicate runtime game evidence'}
            if($proof.schema -ne 2 -or !$proof.nativeTickLoop -or !$proof.bothEntityTickingFuturesResolved -or
                    $proof.ticketFullFrontier -ne 49 -or $proof.physicalBiomeOwners -ne 49 -or $proof.physicalBiomeChecks -le 0 -or
                    $proof.dependencyPyramid -cne $(if($mode -eq 'reload'){'loading'}else{'generation'}) -or
                    $proof.targetFullChunkStatus -cne 'ENTITY_TICKING' -or $proof.neighborFullChunkStatus -cne 'ENTITY_TICKING' -or
                    !$proof.quiescentFrontierBeforeFixtures -or !$proof.testFixturesNotCanonicalGenerationCoverage -or
                    $proof.simulationTicks -lt 220 -or $proof.entityCallbacks -lt 220 -or $proof.furnaceCallbacks -lt 220 -or
                    $proof.blockCallbacks -le 0 -or $proof.fluidCallbacks -le 0 -or !$proof.entityCrossedPhysicalSeam -or
                    $proof.entityRegistrations -ne 1 -or !$proof.fluidCrossedPhysicalSeam -or !$proof.furnaceCooked -or
                    !$proof.probeTicketsRemoved -or $proof.generationBindings -ne 0 -or $proof.spawnBindings -ne 0 -or
                    $proof.finalMaterializer.inFlightCount -ne 0){throw 'Native holder, biome, simulation or cleanup boundary missing'}
            $materializer=$proof.finalMaterializer
            if($vanilla){
                if(!$row.normalVanilla -or $row.pid -ne $proof.pid -or $row.artifactGenerations -ne 0 -or $row.spawnCalls -ne 0 -or
                        $materializer.artifactCaptureCount -ne 0 -or $materializer.planCount -ne 0 -or
                        $materializer.requestCount -ne 0 -or $proof.finalDevelopmentB.generations -ne 0){throw 'Ordinary Vanilla entered Mosaic'}
            }else{
                if($row.featureOrderingVersion -ne 2 -or $proof.ownerLocalSeed -ne $row.localWorldSeed -or
                        $proof.ownerLocalSeed -eq $proof.adjacentLocalSeed){throw 'Incorrect seed/contract ownership'}
                if($mode -eq 'reload'){
                    if($row.artifactGenerations -ne 0 -or $row.spawnCalls -ne 0 -or
                            $materializer.artifactCaptureCount -ne 0 -or $proof.finalDevelopmentB.generations -ne 0){throw 'Saved FULL regenerated'}
                }elseif($materializer.artifactCaptureCount -le 0 -or $proof.finalDevelopmentB.generations -le 0){throw 'No actual cold production generation'}
            }
        }
    }
}
"PASS: $target $($Dimensions.Count) dimensions; $($pids.Count) independent fresh/reload JVMs, native ENTITY_TICKING and zero-regeneration/Vanilla controls."
Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT $hash"}else{"INPUT_SCOPE: CURRENT_SOURCE $hash"})
