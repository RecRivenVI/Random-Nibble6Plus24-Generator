param([ValidateSet('overworld','the_nether','the_end')][string]$Dimension='overworld', [switch]$RecoverShutdown, [switch]$RingScope, [switch]$RuntimeProof, [switch]$FullWaitDiagnosis, [switch]$FailureProof, [switch]$PartialPreLight, [switch]$RequestProof, [ValidateSet(1,8,16,32)][int]$Requests=1, [ValidateSet('','forward','reverse','parallel')][string]$TargetOrder='', [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='', [switch]$CheckOnly, [switch]$Offline, [long]$CpuAffinity=3840, [switch]$ExportServer)
$ErrorActionPreference='Stop'
if($FullWaitDiagnosis -and (!$RuntimeProof -or $Dimension -ne 'overworld' -or $RingScope)){throw 'FULL wait diagnosis is the default Overworld runtime fixture, never a scoped-ring candidate'}
if($RingScope -and $RecoverShutdown){throw 'Scoped ring recovery requires its own lifecycle fixture; do not relabel an unscoped save'}
if($RuntimeProof -and $RecoverShutdown){throw 'Runtime promotion is separate from interrupted FULL recovery'}
if($FailureProof -and ($RecoverShutdown -or $RingScope -or $RuntimeProof -or $ExportServer -or $PartialPreLight)){throw 'Observation failure reporting is a separate bounded negative probe'}
if($PartialPreLight -and ($RecoverShutdown -or $RingScope -or $RuntimeProof)){throw 'Partial native save uses an independent default B world'}
if($EvidenceLabel -and $RecoverShutdown){throw 'Shutdown recovery must match its original owned save'}
if($RequestProof -and ($PartialPreLight -or $FailureProof -or $RuntimeProof -or $RecoverShutdown -or $RingScope -or $EvidenceLabel)){throw 'Request burst uses a separate default production FULL fixture'}
if(!$RequestProof -and $Requests -ne 1){throw 'Multiple requests require an explicit RequestProof'}
if($TargetOrder -and ($PartialPreLight -or $FailureProof -or $RuntimeProof -or $RecoverShutdown -or $RingScope -or $RequestProof)){throw 'Multiple physical owners use a separate default production fixture'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target='1.20.1-forge'
$validation=Join-Path $repo 'validations/integration-legacy_physical'
$variant=if($RingScope){'ring_scope'}elseif($RecoverShutdown){'recovery_shutdown'}else{'current'}
if($RuntimeProof){$variant='runtime_'+$variant}
if($FullWaitDiagnosis){$variant+='_full_wait_game_owned'}
if($FailureProof){$variant='observation_failure'}
if($PartialPreLight){$variant='partial_pre_light'}
if($EvidenceLabel){$variant+='_'+$EvidenceLabel}
if($RequestProof){$variant="requests_$Requests"}
if($TargetOrder){$variant="targets_$TargetOrder";if($EvidenceLabel){$variant+='_'+$EvidenceLabel}}
$result=Join-Path $validation "result/$variant/$target"
$dimensionKey="minecraft:$Dimension"
$leaf=$dimensionKey.Replace(':','-')
function Get-InputHash {
    $sourceFiles=@(Get-ChildItem (Join-Path $repo "versions/$target/src/main"),(Join-Path $repo "versions/$target/src/probe"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
    $fingerprint=($sourceFiles|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $fingerprint+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}
$hash=Get-InputHash

function Assert-Evidence {
    if((Get-InputHash) -cne $hash){throw 'Physical proof source changed during validation'}
    if($FailureProof){
        $pids=@()
        foreach($pipeline in @('vanilla','b')){
            $row=Get-Content (Join-Path $result "$pipeline/$leaf/fresh/summary.json") -Raw|ConvertFrom-Json
            if($row.status -cne 'FAIL' -or $row.inputHash -cne $hash -or $row.variant -cne $variant -or
                    $row.scope -cne 'PROBE_OBSERVATION_FAILURE_NOT_GENERATION_FAILURE' -or
                    $row.pipeline -cne $pipeline -or $row.role -cne 'fresh' -or $row.dimension -cne $dimensionKey -or
                    $row.processors -ne 4 -or $row.masterSeed -ne 123456789 -or
                    $row.failure -cne 'java.lang.IllegalStateException: Injected native physical observation failure' -or
                    $row.stopFailure -cne 'java.lang.IllegalStateException: Native stop followed an incomplete/failed request' -or
                    $null -eq $row.afterStop -or $row.afterStop.inFlight -ne 0 -or $row.lifecycle.workers -ne 0 -or
                    $row.bindings -ne 0){throw 'Probe reporting lost the original failure or leaked owned work'}
            $pids+=$row.pid
        }
        if(@($pids|Sort-Object -Unique).Count -ne 2){throw 'Failure report controls reused a JVM'}
        Write-Output "EXPECTED_PROBE_FAILURE_RETAINED: $target $dimensionKey, two fresh JVMs; not FULL or generation-failure acceptance"
        return
    }
    $pids=@()
    $saves=@()
    foreach($pipeline in @('vanilla','b')) {
        $common=Join-Path $result "$pipeline/$leaf"
        $lastSave=$null
        $roles=if($PartialPreLight){@('fresh','resume','reload')}else{@('fresh','reload')}
        foreach($role in $roles) {
            $proof=Get-Content (Join-Path $common "$role/summary.json") -Raw|ConvertFrom-Json
            $scope=if($PartialPreLight){'NATIVE_PRE_LIGHT_SAVE_RESUME_NOT_PLAYER_ENTRY'}else{'ORDINARY_NATIVE_FULL_NOT_PLAYER_ENTRY'}
            $staging=$PartialPreLight -and $role -eq 'fresh'
            $status=if($staging){@('initialize_light','minecraft:initialize_light')}else{@('full','minecraft:full')}
            if($proof.status -cne 'PASS' -or $proof.schema -ne 1 -or $proof.inputHash -cne $hash -or
                $proof.variant -cne $variant -or
                $proof.target -cne $target -or $proof.scope -cne $scope -or
                $proof.dimension -cne $dimensionKey -or $proof.pipeline -cne $pipeline -or $proof.role -cne $role -or
                $proof.processors -ne 4 -or $proof.minecraft -cne '1.20.1' -or !$proof.mods.forge -or
                !$proof.mods.randomnibble6plus24generator -or !$proof.mods.randomnibble6plus24generator_probe -or
                $proof.x -ne 125 -or $proof.z -ne -37 -or
                !$proof.markerPresent -or (!$staging -and !$proof.lightCorrect) -or $proof.fullStatus -notin $status -or
                $proof.coldLoadedChunks -ne 0 -or $proof.bindings -ne 0 -or $proof.afterStop.inFlight -ne 0 -or $proof.lifecycle.workers -ne 0) {
                throw "Incomplete native physical proof: $pipeline $role"
            }
            if($role -eq 'fresh' -and $pipeline -eq 'b') {
                if($RingScope -and (!$proof.ringCandidate -or $proof.physicalRingScopeBound -or $proof.ringContexts -le 0 -or $proof.ringSearches -gt $proof.ringSlots -or ($Dimension -eq 'overworld' -and $proof.ringSlots -le 0))){throw 'Physical finite ring candidate did not actually run'}
                if($proof.rootGenerations -ne 1 -or $proof.beforeSave.generations -le 0 -or
                    $proof.beforeSave.generations -ne $proof.beforeSave.publications -or
                    $proof.beforeSave.publications -ne $proof.beforeSave.poi -or
                    (!$staging -and ($proof.beforeSave.spawn -le 0 -or $proof.beforeSave.light -le 0 -or $proof.beforeSave.full -le 0)) -or
                    $proof.physicalBiomeChecks -le 0 -or $proof.physicalBiomeOwners -le 0 -or
                    $proof.localSeed -eq $proof.masterSeed -or !(Test-Path (Join-Path $common 'fresh/root-artifact.nbt'))) {
                    throw 'No complete actual Mosaic production path'
                }
            } elseif($PartialPreLight -and $pipeline -eq 'b' -and $role -eq 'resume'){
                if($proof.rootGenerations -ne 0){throw 'Persisted target regenerated canonical data'}
            } elseif($proof.rootGenerations -ne 0 -or $proof.beforeSave.generations -ne 0 -or
                    $proof.beforeSave.publications -ne 0 -or $proof.beforeSave.spawn -ne 0) { throw 'Saved/Vanilla world regenerated canonical data' }
            $expectedBiomeOwners=if($TargetOrder){5}elseif($RuntimeProof){49}else{1}
            if($pipeline -eq 'b' -and $role -ne 'fresh' -and ($proof.physicalBiomeOwners -ne $expectedBiomeOwners -or $proof.physicalBiomeChecks -le 0)){throw 'Reload lost the physical stored biome palette'}
            if($role -ne 'fresh' -and $proof.save -cne $lastSave){throw 'Reload used another save'}
            if($RequestProof){
                $burst=$proof.requestBurst
                if(!$burst -or $burst.schema -ne 1 -or $burst.scope -cne 'SAME_TARGET_PENDING_REQUESTS_NOT_PARALLEL_SESSION_PROOF' -or
                        $burst.requested -ne $Requests -or $burst.issued -ne $Requests -or $burst.completed -ne $Requests -or
                        $burst.pending -ne 0 -or !$burst.callerOffThread -or !$burst.samePhysicalIdentity){throw 'Native duplicate requests did not converge'}
                if($role -eq 'fresh' -and ($burst.peakPending -ne $Requests -or !$burst.allIssuedBeforeFirstCompletion)){throw 'Fresh requests did not actually overlap before completion'}
            }
            if($TargetOrder){
                if($pipeline -eq 'b' -and $proof.physicalBiomeOwners -ne 5){throw 'Missing held multi-target stored biome comparison'}
                if($pipeline -eq 'b' -and ($proof.beforeSave.light -ne 5 -or $proof.beforeSave.full -ne 5 -or
                        ($role -eq 'fresh' -and $proof.beforeSave.spawn -ne 5))){throw 'Multi-target lifecycle did not execute exactly once per target'}
                $multiple=$proof.multipleTargets
                if(!$multiple -or $multiple.schema -ne 1 -or $multiple.scope -cne 'FIVE_NATIVE_FULL_TARGETS_NOT_TICKING_OR_PLAYER_ENTRY' -or
                        $multiple.order -cne $TargetOrder -or $multiple.count -ne 5 -or $multiple.pending -ne 0 -or
                        @($multiple.targets).Count -ne 5 -or @($multiple.requestedOrder).Count -ne 5 -or @($multiple.completionOrder).Count -ne 5){throw 'Incomplete multi-target native request proof'}
                if(@($multiple.targets|Where-Object{$_.status -notin @('full','minecraft:full') -or !$_.lightCorrect}).Count){throw 'A target was not a real lit FULL Chunk'}
                $keys=@($multiple.targets|ForEach-Object{"$($_.x)_$($_.z)"}|Sort-Object)
                if(($keys -join ',') -cne '124_-37,125_-36,125_-37,125_-38,126_-37'){throw 'Unexpected multi-target footprint'}
                if($pipeline -eq 'b' -and @($multiple.targets.localSeed|Sort-Object -Unique).Count -ne 5){throw 'Multi-target provenance reused a local seed'}
                if($role -eq 'fresh' -and $TargetOrder -eq 'parallel' -and ($multiple.peakPending -ne 5 -or !$multiple.allIssuedBeforeFirstCompletion)){throw 'Five physical owners were not pending together'}
                if($TargetOrder -ne 'parallel' -and (($multiple.requestedOrder -join ';') -cne ($multiple.completionOrder -join ';') -or $multiple.peakPending -ne 1)){throw 'Sequential physical requests overlapped or completed out of order'}
            }
            if($PartialPreLight){
                $population=if($role -eq 'resume'){1}else{0}
                $cold=if($pipeline -eq 'b' -and $role -eq 'resume'){1}else{0}
                if($proof.rootPopulationCalls -ne $population -or $proof.coldBiomeComparisons -ne $cold){throw 'Partial population/cold inputs repeated, skipped or changed'}
                if($proof.partialWriterScopes -ne 0 -or (!$staging -and $proof.partialLoadedSections -le 0)){throw 'Native disk sections were not observed or writer scopes leaked'}
                if($staging){
                    if($proof.beforeSave.spawn -ne 0 -or $proof.beforeSave.full -ne 0 -or $proof.beforeSave.light -ne 0 -or
                            $proof.requestedStatus -notin $status -or $proof.ticketLevel -le 33){throw 'Checkpoint was already a FULL request'}
                }else{
                    $loaded=if($role -eq 'resume'){@('initialize_light','minecraft:initialize_light')}else{@('full','minecraft:full')}
                    if(!$proof.diskProjectionEqual -or !$proof.targetProjectionEqual -or $proof.loadedStatus -notin $loaded){throw 'No real partial disk checkpoint or preserved projection'}
                    if($pipeline -eq 'vanilla' -and $role -eq 'resume' -and !$proof.nativeNeighborReplayExact){throw 'Unexplained Vanilla neighborhood writes'}
                }
            }
            if($RuntimeProof){
                $runtime=$proof.runtimeProof
                if(!$runtime -or $runtime.schema -ne 1 -or !$runtime.nativeTickLoop -or
                        $runtime.scope -cne 'REGIONAL_TICKING_NOT_PLAYER_ENTRY_OR_PACKET' -or
                        $runtime.ticketFullFrontier -ne 49 -or !$runtime.bothEntityTickingFuturesResolved -or
                        !$runtime.bothBlockTickingFuturesResolved -or !$runtime.allCallbacksServerThread -or
                        !$runtime.quiescentBeforeFixtures -or !$runtime.testFixturesNotCanonicalCoverage -or
                        $runtime.simulationTicks -lt 220 -or $runtime.nativeIdleResetCalls -ne 1 -or
                        $runtime.dependencyChecks -le 0 -or !$runtime.entityCrossedPhysicalSeam -or
                        $runtime.entityRegistrations -ne 1 -or $runtime.entityCallbacks -le 0 -or
                        !$runtime.fluidCrossedPhysicalSeam -or $runtime.blockCallbacks -le 0 -or
                        $runtime.fluidCallbacks -le 0 -or $runtime.furnaceCallbacks -le 0 -or
                        !$runtime.furnaceCooked -or !$runtime.probeTicketsRemoved -or $runtime.generationBindings -ne 0){
                    throw 'Incomplete native regional promotion/simulation proof'
                }
                if($pipeline -eq 'b' -and $runtime.ownerLocalSeed -eq $runtime.adjacentLocalSeed){throw 'Runtime seam lacks distinct local seeds'}
            }
            $lastSave=$proof.save
            $pids+=$proof.pid
        }
        if($RecoverShutdown){
            $stop=Get-Content (Join-Path $validation "result/lifecycle_shutdown/$target/$pipeline/$leaf/summary.json") -Raw|ConvertFrom-Json
            if($stop.status -cne 'PASS' -or $stop.inputHash -cne $hash -or !$stop.requestIncompleteAtStop -or
                    $stop.save -cne $lastSave -or $stop.afterStop.generations -ne 0 -or $stop.afterStop.full -ne 0){throw 'Recovery was not the same actually interrupted native save'}
        }
        $saves+=$lastSave
    }
    $expectedProcesses=if($PartialPreLight){6}else{4}
    if(@($pids|Sort-Object -Unique).Count -ne $expectedProcesses -or @($saves|Sort-Object -Unique).Count -ne 2){throw 'Not independent JVMs and two independent worlds'}
    Write-Host "PASS: $target $dimensionKey native Vanilla/B $variant save/reload, $expectedProcesses JVMs, current input $hash"
}
if($CheckOnly){Assert-Evidence;exit}
$local=Join-Path $repo 'local.toml'
if(!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)){throw 'Explicit Minecraft EULA acceptance required'}
$instance=Join-Path $validation "instance/$target/server"
New-Item -ItemType Directory -Force -Path $instance|Out-Null
$properties=Join-Path $instance 'server.properties'
$oldProperties=if(Test-Path $properties){[IO.File]::ReadAllText($properties)}else{$null}
$oldOptions=$env:JAVA_TOOL_OPTIONS
$process=[Diagnostics.Process]::GetCurrentProcess()
$affinity=$process.ProcessorAffinity;$priority=$process.PriorityClass
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($CpuAffinity -band $affinity.ToInt64()) -ne $CpuAffinity){throw 'Exactly four available logical CPUs required'}
Add-Type -AssemblyName Microsoft.VisualBasic
function Recycle([string]$path) {
    $path=[IO.Path]::GetFullPath($path)
    if(!$path.StartsWith($validation+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe physical proof cleanup'}
    if(Test-Path $path){
        if((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refusing linked validation data'}
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
}
$facts=ConvertFrom-StringData (Get-Content (Join-Path $repo "versions/$target/target.properties") -Raw)
$modId=(ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id
$configuration=Get-Content (Join-Path $validation 'validation.toml') -Raw
$port=[int][regex]::Match($configuration,'(?m)^port\s*=\s*(\d+)\s*$').Groups[1].Value
try {
    $process.PriorityClass='BelowNormal';$process.ProcessorAffinity=[IntPtr]$CpuAffinity
    foreach($pipeline in @('vanilla','b')) {
        $worldName=if($RecoverShutdown){"legacy-lifecycle-shutdown-$pipeline-$Dimension"}elseif($variant -ne 'current'){"legacy-physical-$variant-$pipeline-$Dimension"}else{"legacy-physical-$pipeline-$Dimension"}
        $world=Join-Path $instance $worldName
        $common=Join-Path $result "$pipeline/$leaf"
        if(($FullWaitDiagnosis -or $FailureProof -or $PartialPreLight -or $RequestProof -or $TargetOrder) -and (Test-Path $common)){throw 'Preserve existing checkpoint/reporting/request evidence; never overwrite it on rerun'}
        $failureRoot=if($variant -ne 'current'){Join-Path $validation "result/first_failure/$variant/$target/$pipeline/$leaf"}else{Join-Path $validation "result/first_failure/$target/$pipeline/$leaf"}
        if((Test-Path $common) -and !(Test-Path $failureRoot)){
            $failed=@(Get-ChildItem $common -Filter summary.json -Recurse|Where-Object{(Get-Content $_.FullName -Raw|ConvertFrom-Json).status -cne 'PASS'})
            if($failed.Count){New-Item -ItemType Directory -Force -Path (Split-Path -Parent $failureRoot)|Out-Null;Copy-Item $common $failureRoot -Recurse}
        }
        if($RecoverShutdown){
            $stopPath=Join-Path $validation "result/lifecycle_shutdown/$target/$pipeline/$leaf/summary.json"
            $stop=Get-Content $stopPath -Raw|ConvertFrom-Json
            if(!(Test-Path (Join-Path $world 'level.dat')) -or $stop.status -cne 'PASS' -or $stop.inputHash -cne $hash -or
                    $stop.save -cne $world -or !$stop.requestIncompleteAtStop -or
                    $stop.afterStop.generations -ne 0 -or $stop.afterStop.full -ne 0){throw 'Run the matching clean shutdown with KeepWorld before recovery'}
        }else{Recycle $world}
        Recycle $common
        New-Item -ItemType Directory -Force -Path $world,$common|Out-Null
        $levelType='minecraft:normal'
        if($pipeline -eq 'b' -and !$RecoverShutdown) {
            $pack=Join-Path $world 'datapacks/native-mosaic'
            $preset=Join-Path $pack "data/$modId/worldgen/world_preset/native_physical.json"
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $preset)|Out-Null
            $dimensions=[ordered]@{}
            foreach($entry in @(@('overworld','overworld','overworld'),@('the_nether','nether','nether'),@('the_end','the_end','end'))){
                $source=if($entry[0] -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$($entry[1])"}}
                $profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=2;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
                $dimensions["minecraft:$($entry[0])"]=@{type="minecraft:$($entry[0])";generator=@{type="${modId}:development_b";biome_source=$source;settings="minecraft:$($entry[2])";mosaic_profile=$profile}}
            }
            [IO.File]::WriteAllText($preset,(@{dimensions=$dimensions}|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),(@{pack=@{pack_format=[int]$facts.data_pack_major;description='Disposable native Mosaic physical proof'}}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText((Join-Path $world 'mosaic-development-b.json'),(@{schema=1;pipeline=($target.Split('-')[0]+'-lightweight-b-1')}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
            $levelType="${modId}:native_physical"
        }
        if($pipeline -eq 'b' -and $RecoverShutdown){$levelType="${modId}:native_lifecycle"}
        [IO.File]::WriteAllText($properties,"level-name=$worldName`nlevel-seed=123456789`nlevel-type=$levelType`nserver-ip=127.0.0.1`nserver-port=$port`nonline-mode=false`nview-distance=2`nsimulation-distance=2`n",[Text.UTF8Encoding]::new($false))
        $roles=if($FailureProof){@('fresh')}elseif($PartialPreLight){@('fresh','resume','reload')}else{@('fresh','reload')}
        foreach($role in $roles) {
            $roleRoot=Join-Path $common $role
            New-Item -ItemType Directory -Force -Path $roleRoot|Out-Null
            $crashes=@{};Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object{$crashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
            $env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -D${modId}.nativePhysical.pipeline=$pipeline -D${modId}.nativePhysical.role=$role -D${modId}.nativePhysical.dimension=$dimensionKey -D${modId}.nativePhysical.inputHash=$hash -D${modId}.nativePhysical.variant=$variant"
            if($pipeline -eq 'b'){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.developmentB=true"}
            if($RingScope){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.structure.lazyRings=true"}
            if($RuntimeProof){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.runtimeProof=true"}
            if($FullWaitDiagnosis){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.fullWaitEvidence=true"}
            if($FailureProof){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.observationFailure=true"}
            if($PartialPreLight){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.partialPreLight=true"}
            if($RequestProof){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.requests=$Requests"}
            if($TargetOrder){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.nativePhysical.targetOrder=$TargetOrder"}
            if($ExportServer){$env:JAVA_TOOL_OPTIONS+=' -Dmixin.debug.export=true -Dmixin.debug.export.filter=net.minecraft.server.MinecraftServer'}
            [string[]]$network=if($Offline){@('--offline')}else{@()}
            Push-Location $repo
            try {
                & .\gradlew.bat ":version:${target}:runValidationIntegrationLegacyPhysicalServer" @network --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> (Join-Path $roleRoot 'console.log')
                if($LASTEXITCODE -ne 0){Get-Content (Join-Path $roleRoot 'console.log') -Tail 35;throw "Native physical $pipeline/$role exited unsuccessfully"}
            } finally {Pop-Location}
            Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object{if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New native physical crash $($_.FullName)"}}
            $proof=Get-Content (Join-Path $roleRoot 'summary.json') -Raw|ConvertFrom-Json
            $expectedStatus=if($FailureProof){'FAIL'}else{'PASS'}
            if($proof.status -cne $expectedStatus -or $proof.inputHash -cne $hash){throw "Native physical proof failed: $pipeline/$role $($proof.failure)"}
            Write-Host "COMPLETED $pipeline $role $dimensionKey PID=$($proof.pid)"
        }
    }
    Assert-Evidence
    foreach($pipeline in @('vanilla','b')){
        $completedWorld=if($RecoverShutdown){"legacy-lifecycle-shutdown-$pipeline-$Dimension"}elseif($variant -ne 'current'){"legacy-physical-$variant-$pipeline-$Dimension"}else{"legacy-physical-$pipeline-$Dimension"}
        Recycle (Join-Path $instance $completedWorld)
    }
} catch {
    $archive=if($variant -ne 'current'){Join-Path $validation "result/first_failure/$variant/$target/$pipeline/$leaf"}else{Join-Path $validation "result/first_failure/$target/$pipeline/$leaf"}
    if(!(Test-Path $archive)){
        New-Item -ItemType Directory -Force -Path $archive|Out-Null
        if(Test-Path $common){Copy-Item (Join-Path $common '*') $archive -Recurse}
        foreach($path in @($world,(Join-Path $instance 'logs/latest.log'),(Join-Path $instance 'crash-reports'))){if(Test-Path $path){Copy-Item $path $archive -Recurse}}
        [IO.File]::WriteAllText((Join-Path $archive 'failure.txt'),($_|Out-String))
    }
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS=$oldOptions
    if($null -ne $oldProperties){[IO.File]::WriteAllText($properties,$oldProperties,[Text.UTF8Encoding]::new($false))}
    $process.ProcessorAffinity=$affinity;$process.PriorityClass=$priority
}
