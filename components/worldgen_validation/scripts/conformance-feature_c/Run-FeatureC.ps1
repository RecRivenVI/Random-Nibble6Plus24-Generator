param([switch]$CheckOnly, [switch]$Flow, [switch]$Frontier, [switch]$Replay, [switch]$NativeRepeat, [switch]$Physical, [switch]$AcceptedFrontier, [switch]$NativeShulker, [switch]$AcceptedShulker, [switch]$PhysicalHives, [switch]$PreLight, [switch]$EditProtection, [switch]$Recovery, [switch]$Append)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
if (([int]$Flow.IsPresent + [int]$Frontier.IsPresent + [int]$Replay.IsPresent + [int]$NativeRepeat.IsPresent + [int]$Physical.IsPresent + [int]$AcceptedFrontier.IsPresent + [int]$NativeShulker.IsPresent + [int]$AcceptedShulker.IsPresent + [int]$PhysicalHives.IsPresent + [int]$PreLight.IsPresent + [int]$EditProtection.IsPresent + [int]$Recovery.IsPresent + [int]$Append.IsPresent) -gt 1) { throw 'Choose one C suite' }
$physicalRun = $Physical -or $PhysicalHives -or $PreLight
$mode = if ($PreLight) { 'prelight' } elseif ($PhysicalHives) { 'physical_hives' } elseif ($AcceptedShulker) { 'accepted_shulker' } elseif ($NativeShulker) { 'native_shulker' } elseif ($AcceptedFrontier) { 'accepted_frontier' } elseif ($Physical) { 'physical' } elseif ($NativeRepeat) { 'native_repeat' } elseif ($Replay) { 'replay' } elseif ($Frontier) { 'frontier' } elseif ($Flow) { 'flow' } else { 'current' }
$flowProperty = if ($PreLight) { ' -Dfeaturec.preLight=true' } elseif ($PhysicalHives) { ' -Dfeaturec.physical=true -Dfeaturec.physicalHives=true' } elseif ($AcceptedShulker) { ' -Dfeaturec.acceptedShulker=true' } elseif ($NativeShulker) { ' -Dfeaturec.nativeShulker=true' } elseif ($AcceptedFrontier) { ' -Dfeaturec.acceptedFrontier=true' } elseif ($Physical) { ' -Dfeaturec.physical=true' } elseif ($NativeRepeat) { ' -Dfeaturec.nativeRepeat=true' } elseif ($Replay) { ' -Dfeaturec.replay=true' } elseif ($Frontier) { ' -Dfeaturec.frontier=true' } elseif ($Flow) { ' -Dfeaturec.flow=true' } else { '' }
if ($EditProtection) {
    $mode = 'edit_protection'
    $flowProperty = ' -Dfeaturec.editProtection=true'
    $physicalRun = $true
}
if ($Recovery) {
    $mode = 'recovery'
    $flowProperty = ' -Dfeaturec.recovery=true'
    $physicalRun = $true
}
if ($Append) {
    $mode = 'append'
    $flowProperty = ' -Dfeaturec.append=true'
    $physicalRun = $true
}
$resultRoot = Join-Path $validationRepository "validations/$validationProject/result/$mode/$validationTarget"
if ($CheckOnly -and $Append) {
    $summaries = @(0..3 | ForEach-Object { Get-Content (Join-Path $resultRoot "host_$_/summary.json") -Raw | ConvertFrom-Json })
    $inputHash = (Get-FileHash (Join-Path $validationRepository "validations/$validationProject/result/current/$validationTarget/host_0/native-tree-contributions.nbt") -Algorithm SHA256).Hash.ToLowerInvariant()
    foreach ($s in $summaries) {
        if ($s.status -cne 'PASS_MONOTONE_APPEND_ONLY' -or $s.proofVersion -ne 1 -or $s.receiverBase -cne 'NATIVE_PHYSICAL_NOT_C' -or $s.hostSeed -ne 1 -or $s.receivers -le 0 -or $s.heightColumnsVerified -ne $s.receivers * 1024 -or $s.protectedEdits -ne $s.receivers -or $s.duplicateWrites -ne 0 -or $s.bindings -ne 0 -or $s.experimentalGenerationCalls -ne 0 -or $s.productionSwitch -or $s.inputHash -cne $inputHash -or $s.conflictReplacement -cne 'PENDING_NOT_IMPLEMENTED' -or $s.runtimeAtomicity -cne 'NOT_EVALUATED') { throw 'Incomplete incremental receiving proof' }
    }
    if (@($summaries.pid | Sort-Object -Unique).Count -ne 4) { throw 'Four independent append JVMs not proven' }
    foreach ($i in @(0,2)) {
        $fresh = $summaries[$i]; $reload = $summaries[$i+1]
        if ($fresh.reload -or !$reload.reload -or $fresh.reverse -ne ($i -eq 2) -or $reload.reverse -ne $fresh.reverse -or $fresh.accepted -le 0 -or $fresh.lateBatches -le 0 -or !$fresh.workerRejected -or !$fresh.foreignRejected -or $reload.accepted -ne 0 -or $reload.nativeGeneratedFeatures -ne 0 -or !$reload.lightReloadEqual -or $fresh.receivers -ne $reload.receivers -or $fresh.protectedBlockEntities -ne $reload.protectedBlockEntities) { throw 'Missing real incremental acceptance/restart evidence' }
    }
    if ($summaries[0].hives -le 0 -or $summaries[0].protectedBlockEntities -ne $summaries[0].hives -or $summaries[0].conflictChecks -ne 0 -or $summaries[1].conflictChecks -ne 0 -or $summaries[2].conflictChecks -le 0 -or $summaries[3].conflictChecks -le 0 -or @(Get-Content (Join-Path $resultRoot 'host_2/conflicts.json') -Raw | ConvertFrom-Json).Count -le 0) { throw 'Missing BE protection or unresolved reverse-order conflicts' }
    'PASS monotone physical append only: late additions, duplicate/effect protection, real conflicts refuse all writes, two independent restarts. Not conflict replacement or whole-order independence.'
    exit 0
}
if ($CheckOnly -and $Recovery) {
    if (Get-ChildItem -LiteralPath $resultRoot -Filter '*mismatch*.nbt' -Recurse -ErrorAction SilentlyContinue) { throw 'Recovery changed persisted content' }
    $partial = Get-Content (Join-Path $resultRoot 'host_0/interruption.json') -Raw | ConvertFrom-Json
    $saved = Get-Content (Join-Path $resultRoot 'host_1/interruption.json') -Raw | ConvertFrom-Json
    $recovered = Get-Content (Join-Path $resultRoot 'host_2/summary.json') -Raw | ConvertFrom-Json
    $reload = Get-Content (Join-Path $resultRoot 'host_3/summary.json') -Raw | ConvertFrom-Json
    if ($partial.status -cne 'INTERRUPTED_C_PRELIGHT_TRANSACTION' -or $partial.point -cne 'PARTIAL_REGION_WRITE' -or $partial.exit -ne 73 -or $partial.generated -ne 25 -or $partial.prepared -ne 25 -or $partial.written -ne 12 -or $partial.lightChunks -ne 0 -or $partial.bindings -ne 0) { throw 'Missing real partial-write interruption' }
    if ($saved.status -cne 'INTERRUPTED_C_PRELIGHT_TRANSACTION' -or $saved.point -cne 'SAVED_LIGHT_BEFORE_COMMIT' -or $saved.exit -ne 74 -or $saved.generated -ne 0 -or $saved.prepared -ne 25 -or $saved.written -ne 13 -or $saved.lightChunks -ne 9 -or $saved.bindings -ne 0 -or !$saved.refusalVerified) { throw 'Missing real save/commit interruption or preflight refusal' }
    foreach ($s in @($recovered, $reload)) {
        if ($s.status -cne 'PASS_C_PRELIGHT_RECOVERY_ONLY' -or !$s.reload -or $s.generated -ne 0 -or $s.prepared -ne 25 -or $s.lightChunks -ne 9 -or $s.accepted -ne 93 -or $s.effects -ne 88 -or $s.pendingCells -ne 3 -or $s.localSeedReads -ne 25 -or $s.existingReceivers -ne 25 -or $s.recoveryWrites -ne 0 -or !$s.committed -or $s.bindings -ne 0 -or $s.productionSwitch -or $s.fullOrSpawn) { throw 'Incomplete pre-light recovery/reload proof' }
        if (@($s.stages.PSObject.Properties | Where-Object { $_.Name -match 'spawn|full' }).Count -ne 0) { throw 'Recovery activated gameplay' }
    }
    if ($recovered.alreadyCommitted -or !$reload.alreadyCommitted -or $recovered.worldPath -cne $reload.worldPath -or @(@($partial.pid,$saved.pid,$recovered.pid,$reload.pid) | Sort-Object -Unique).Count -ne 4) { throw 'Four independent JVM phases not proven' }
    'PASS C pre-light transaction only: abrupt partial region write, abrupt saved LIGHT before commit, zero-regeneration recovery and committed reload. No runtime conflict replacement or power-loss proof.'
    exit 0
}
if ($CheckOnly -and $EditProtection) {
    $first = Get-Content (Join-Path $resultRoot 'host_0/summary.json') -Raw | ConvertFrom-Json
    $second = Get-Content (Join-Path $resultRoot 'host_1/summary.json') -Raw | ConvertFrom-Json
    $inputHash = (Get-FileHash (Join-Path $validationRepository "validations/$validationProject/result/current/$validationTarget/host_0/native-tree-contributions.nbt") -Algorithm SHA256).Hash.ToLowerInvariant()
    foreach ($s in @($first, $second)) {
        if ($s.status -cne 'PASS_PHYSICAL_EDIT_PROTECTION_ONLY' -or $s.proofVersion -ne 1 -or $s.receiverBase -cne 'NATIVE_PHYSICAL_NOT_C' -or $s.playerInput -cne 'SIMULATED_NATIVE_BLOCK_MUTATION' -or $s.hostSeed -ne 1 -or $s.receivers -lt 2 -or $s.accepted -le 0 -or $s.ownedWrites -ne $s.accepted -or $s.protectedPositions -lt $s.receivers -or $s.immediateReceivers -le 0 -or $s.delayedReceivers -le 0 -or $s.duplicateWrites -ne 0 -or !$s.workerRejected -or !$s.exceptionPropagated -or $s.bindings -ne 0 -or $s.experimentalGenerationCalls -ne 0 -or $s.productionSwitch -or $s.inputHash -cne $inputHash) { throw 'Incomplete physical edit protection proof' }
    }
    if ($first.reload -or !$second.reload -or $first.pid -eq $second.pid -or $first.receivers -ne $second.receivers -or $first.observedMutations -lt 2 * $first.receivers -or $second.nativeGeneratedFeatures -ne 0 -or $first.hives + $second.hives -le 0 -or $first.blockTicks + $second.blockTicks -le 0) { throw 'Missing real mutation/restart/positive-effect evidence' }
    'PASS native physical edit observation, edited-air protection, delayed first acceptance after restart and duplicate replay. Not real player input or dynamic conflict replacement.'
    exit 0
}
if ($CheckOnly -and $PreLight) {
    $first = Get-Content (Join-Path $resultRoot 'host_0/summary.json') -Raw | ConvertFrom-Json
    $second = Get-Content (Join-Path $resultRoot 'host_1/summary.json') -Raw | ConvertFrom-Json
    foreach ($s in @($first, $second)) {
        if ($s.status -cne 'PASS_C_PHYSICAL_PRELIGHT_ONLY' -or $s.prepared -lt 9 -or $s.lightChunks -ne 9 -or $s.accepted -le 0 -or $s.effects -le 0 -or $s.localSeedReads -ne $s.prepared -or $s.bindings -ne 0 -or $s.productionSwitch -or $s.fullOrSpawn) { throw 'Incomplete native C pre-light handoff' }
        if (@($s.stages.PSObject.Properties | Where-Object { $_.Name -match 'spawn|full' }).Count -ne 0) { throw 'Unexpected activation' }
    }
    if ($first.reload -or !$second.reload -or $first.pid -eq $second.pid -or $first.generated -ne $first.prepared -or $second.generated -ne 0 -or $first.accepted -ne $second.accepted -or $first.effects -ne $second.effects) { throw 'C restart/reception not proven' }
    'PASS experimental C physical pre-light handoff only; native disk/POI/LIGHT, no SPAWN/FULL or production activation.'
    exit 0
}
if ($CheckOnly -and $AcceptedShulker) {
    $previous = $null
    foreach ($variant in @('host_0','host_1')) {
        $summary = Get-Content (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        $audit = $summary.acceptance
        if ($summary.status -cne 'PASS_END_CITY_FINITE_ACCEPTANCE' -or $summary.proofVersion -ne 1 -or $summary.generations -ne 13 -or $summary.peakSessions -ne 3 -or $summary.bindings -ne 0 -or !$summary.physicalUnchanged -or $summary.fixtures.Count -ne 2 -or $summary.fixtures[0].entities -ne 3) { throw 'Missing complete C/B End City evidence' }
        if ($audit.rule -cne '26.3-native-end-city-shulker-yaw-1' -or $audit.nativeWorlds -ne 3 -or !$audit.rawEvidencePreserved -or $audit.canonicalHashChanged -or $audit.checkpoints -ne 4 -or $audit.exceptionsUsed -le 2) { throw 'Missing independent native basis / finite exception evidence' }
        $expectedCross = if ($variant -ceq 'host_1') { 4 } else { 0 }
        if ($audit.crossJvmComparisons -ne $expectedCross) { throw 'Missing C/B raw cross-host comparison' }
        $reports = @(Get-Content (Join-Path $resultRoot "$variant/acceptance/comparisons.json") -Raw | ConvertFrom-Json)
        if ($reports.Count -ne $audit.comparisons -or @($reports | Where-Object { !$_.result.accepted -or !$_.result.entities.accepted -or $_.result.entities.unexpectedDifferences.Count -ne 0 -or $null -ne $_.result.nonEntityDifference }).Count -ne 0) { throw 'Unexpected End City difference' }
        $native = Get-Content (Join-Path $resultRoot "$variant/native-entity-comparison.json") -Raw | ConvertFrom-Json
        if (!$native.accepted -or $native.unexpectedDifferences.Count -ne 0) { throw 'Native entity identity/position/data mismatch' }
        foreach ($row in $summary.fixtures) { if ($row.trace.writers -ne 1 -or $row.trace.selective -ne 1 -or $row.trace.draws -le 0) { throw 'C loop changed' } }
        if ($null -ne $previous) {
            if ($summary.pid -eq $previous.pid -or $summary.hostSeed -eq $previous.hostSeed) { throw 'Hosts not independent' }
            for ($i=0; $i -lt $summary.fixtures.Count; $i++) {
                foreach ($field in @('fixture','entities','calls')) {
                    if (($summary.fixtures[$i].$field | ConvertTo-Json -Depth 15 -Compress) -cne ($previous.fixtures[$i].$field | ConvertTo-Json -Depth 15 -Compress)) { throw "End City cross-host $field differs" }
                }
                foreach ($field in @('trace','rejected')) {
                    $a = $summary.fixtures[$i].$field; $b = $previous.fixtures[$i].$field
                    $names = @($a.PSObject.Properties.Name | Sort-Object)
                    if (($names -join ',') -cne (($b.PSObject.Properties.Name | Sort-Object) -join ',')) { throw "End City $field schema differs" }
                    foreach ($name in $names) { if ($a.$name -cne $b.$name) { throw "End City cross-host $field $name differs" } }
                }
            }
        }
        $previous = $summary
    }
    'PASS End City finite acceptance: original/lightweight B and full C; native-proven Shulker yaw only, raw data retained. No physical application.'
    exit 0
}
if ($CheckOnly -and $physicalRun) {
    $first = Get-Content (Join-Path $resultRoot 'host_0/summary.json') -Raw | ConvertFrom-Json
    $second = Get-Content (Join-Path $resultRoot 'host_1/summary.json') -Raw | ConvertFrom-Json
    foreach ($summary in @($first, $second)) {
        if ($summary.status -cne 'PASS_SEALED_PHYSICAL_BATCH_ONLY' -or $summary.proofVersion -ne 1 -or $summary.receiverBase -cne 'NATIVE_PHYSICAL_NOT_C' -or $summary.receivers -le 0 -or $summary.duplicateWrites -ne 0 -or $summary.experimentalGenerationCalls -ne 0 -or $summary.bindings -ne 0) { throw 'Incomplete physical receiving mechanics evidence' }
    }
    if ($first.reload -or !$second.reload -or $first.pid -eq $second.pid -or $first.receivers -ne $second.receivers -or $first.inputHash -cne $second.inputHash -or $first.accepted -le 0 -or $first.blockTicks -le 0 -or $first.protected -le 0 -or !$first.workerRejected -or !$first.unloadedDeferred -or !$first.changedBatchRejected -or !$first.staleBaseRejected -or !$first.foreignReceiptRejected -or !$second.lightReloadEqual -or $first.heightColumnsVerified -le 0 -or $second.heightColumnsVerified -ne $first.heightColumnsVerified -or $second.nativeGeneratedFeatures -ne 0) { throw 'Physical receive/restart proof failed' }
    if ($PhysicalHives -and (!$first.hiveSuite -or !$second.hiveSuite -or $first.hostSeed -ne 1 -or $second.hostSeed -ne 1 -or $first.hives -le 0 -or $second.hives -ne $first.hives -or $first.inputBatches -ne $second.inputBatches)) { throw 'Missing actual bee nest reception and reload' }
    'PASS sealed physical batch only: real native LevelChunk, duplicate/edit protection and clean restart. Not a complete C Mosaic world or dynamic late-conflict receiver.'
    exit 0
}
if ($CheckOnly -and ($NativeRepeat -or $NativeShulker)) {
    $pids = @(); $paths = @(); $first = $null
    foreach ($variant in @('host_0','host_1','host_2')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        $entityCount = if ($NativeShulker) { 3 } else { 1 }
        if ($summary.status -cne 'CAPTURED_NATIVE_ONLY' -or $summary.proofVersion -ne 1 -or $summary.entities -ne $entityCount -or $summary.bindings -ne 0 -or $summary.experimentalGenerations -ne 0 -or $summary.generator -cne 'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator' -or $summary.chunkMap -cne 'net.minecraft.server.level.ChunkMap') { throw 'Incomplete physical native repeat' }
        if ($summary.pid -in $pids -or $summary.worldPath -in $paths) { throw 'Native repeat did not use independent JVM/save' }
        if ($summary.trace.requestedWriters.Count -ne 9 -or $summary.trace.maxConcurrentFeatureWriters -ne 1 -or $summary.trace.featureSeedInvocationCount -le 0) { throw 'Incomplete native canonical procedure' }
        if (@($summary.stages.PSObject.Properties | Where-Object { $_.Name -match 'light|spawn|full' }).Count -ne 0 -or $summary.stages.'minecraft:features' -ne 9) { throw 'Native capture not stage-exact' }
        foreach ($file in @('target.bin.gz','entities-raw.nbt','entities-raw.json','trace.json')) { if (!(Test-Path -LiteralPath (Join-Path $resultRoot "$variant/$file"))) { throw "Missing native evidence $file" } }
        if ($null -ne $first) {
            foreach ($field in @('masterSeed','seed','dimension','x','z')) { if ($summary.fixture.$field -cne $first.fixture.$field) { throw "Native repeat fixture differs: $field" } }
            if ((Get-FileHash (Join-Path $resultRoot "$variant/trace.json")).Hash -cne (Get-FileHash (Join-Path $resultRoot 'host_0/trace.json')).Hash) { throw 'Native repeat order/seed/biomes differ' }
            if ($summary.comparison.fullParity -cne 'FAIL' -or !$summary.comparison.otherCapturedDataEqual -or $summary.comparison.diff.differingBlocks -ne 0 -or $summary.comparison.canonicalEntitiesEqual -ne $false -or $summary.comparison.rawEntityDiff.Count -le 0) { throw 'Native entity nondeterminism not isolated; inspect evidence, do not relax parity' }
            if ($NativeShulker) {
                $unexpected = @($summary.comparison.rawEntityDiff | Where-Object { $_.path -cnotmatch '^\$\.entities\[[0-2]\]\.(UUID|Rotation\[0\])$' })
                $yaw = @($summary.comparison.rawEntityDiff | Where-Object { $_.path -cmatch '\.Rotation\[0\]$' })
                if ($unexpected.Count -ne 0 -or $yaw.Count -ne 3) { throw 'Native Shulker has additional/different changes; inspect raw evidence' }
            } else {
                $attributePaths = @($summary.comparison.attributeValuesByIdDiff | ForEach-Object { $_.path } | Sort-Object)
                if (($attributePaths -join '|') -cne '$.minecraft:knockback_resistance.modifiers[0].amount|$.minecraft:spawn_reinforcements.base') { throw 'Native fixture has additional/different attribute changes; inspect raw evidence' }
            }
        }
        $pids += $summary.pid; $paths += $summary.worldPath; $first = $summary
    }
    'CONFIRMED native entity nondeterminism: three fresh physical Vanilla worlds, same seed/order, full parity remains FAIL. This is not a C acceptance PASS.'
    exit 0
}
if ($CheckOnly -and $Replay) {
    $previous = $null
    $input = Join-Path $resultRoot 'host_0/inputs.nbt'
    $inputHash = (Get-FileHash -LiteralPath $input -Algorithm SHA256).Hash.ToLowerInvariant()
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -cne 'PASS_FROZEN_INPUT_REPLAY_ONLY' -or $summary.generationParity -cne 'NOT_EVALUATED' -or $summary.proofVersion -ne 1 -or $summary.sources -ne 4 -or $summary.accepted -le 0 -or $summary.rejected -le 0 -or $summary.protected -le 0 -or $summary.rawEntities -le 0 -or $summary.inputHash -cne $inputHash -or $summary.bindings -ne 0 -or !$summary.physicalUnchanged -or $summary.physicalApplications -ne 0) { throw 'Incomplete frozen-input C replay proof' }
        if ($summary.hostSeed -notin @(17,23)) { throw 'Unexpected C replay host' }
        if ($null -eq $summary.replayGenerationCalls -or $summary.replayGenerationCalls -ne 0) { throw 'Replay triggered generation or lacks invocation evidence' }
        if ($variant -eq 'host_0') {
            if (!$summary.producer -or $summary.generatedTargets -ne $summary.targets.Count -or $summary.priorJvmReloads -ne 0 -or $summary.preparationGenerationCalls -lt $summary.generatedTargets) { throw 'Missing C producer' }
        } else {
            if ($summary.producer -or $summary.generatedTargets -ne 0 -or $summary.preparationGenerationCalls -ne 0 -or $summary.priorJvmReloads -ne $summary.receivers.Count -or $summary.pid -eq $previous.pid -or $summary.hostSeed -eq $previous.hostSeed) { throw 'Consumer regenerated inputs or was not independent' }
            foreach ($field in @('inputHash','scanCount','sources','packages','rawEntities','accepted','rejected','pending','pendingReceivers','protected')) {
                if ($summary.$field -cne $previous.$field) { throw "Frozen C replay differs: $field" }
            }
            foreach ($field in @('targets','receivers')) {
                if ($summary.$field.Count -ne $previous.$field.Count) { throw "C replay $field count differs" }
                for ($i = 0; $i -lt $summary.$field.Count; $i++) {
                    $a = $summary.$field[$i]; $b = $previous.$field[$i]
                    foreach ($name in $a.PSObject.Properties.Name) { if ($a.$name -cne $b.$name) { throw "C replay $field $i $name differs" } }
                }
            }
            foreach ($name in @('blockTicks','fluidTicks','postProcessing')) { if ($summary.effects.$name -cne $previous.effects.$name) { throw 'C replay effects differ' } }
        }
        $previous = $summary
    }
    'PASS frozen-input C receiving only: complete C bases preserved, delayed/concurrent delivery, protection and independent-JVM replay. Generation parity is NOT evaluated; the separate frontier gate remains required.'
    exit 0
}
if ($CheckOnly -and ($Frontier -or $AcceptedFrontier)) {
    if (Get-ChildItem -LiteralPath $resultRoot -Filter 'mismatch-*.json' -Recurse -ErrorAction SilentlyContinue) { throw 'C frontier full parity failed; inspect preserved mismatch evidence. No canonical fields were ignored.' }
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        $expectedStatus = if ($AcceptedFrontier) { 'PASS_FINITE_ENTITY_ACCEPTANCE' } else { 'PASS' }
        $expectedVersion = if ($AcceptedFrontier) { 2 } else { 1 }
        if ($summary.status -cne $expectedStatus -or $summary.proofVersion -ne $expectedVersion -or $summary.positiveSources -ne 4 -or $summary.masterSeed -ne 123456789 -or $summary.accepted -le 0 -or $summary.rejected -le 0 -or $summary.protected -le 0 -or $summary.bindings -ne 0 -or !$summary.physicalUnchanged -or $summary.physicalApplications -ne 0) { throw 'Incomplete C-to-C frontier proof' }
        if ($AcceptedFrontier) {
            $audit = $summary.acceptance
            $checkpoints = $summary.scan.Count + $summary.generated.Count
            $crossJvm = if ($variant -ceq 'host_1') { $checkpoints } else { 0 }
            if ($audit.rule -cne '26.3-native-drowned-fields-1' -or $audit.nativeWorlds -ne 3 -or !$audit.rawEvidencePreserved -or $audit.canonicalHashChanged -or $audit.checkpoints -ne $checkpoints -or $audit.crossJvmComparisons -ne $crossJvm -or $audit.exceptionsUsed -le 2) { throw 'Missing finite acceptance / cross-JVM full snapshot comparison' }
            $reports = @(Get-Content (Join-Path $resultRoot "$variant/acceptance/comparisons.json") -Raw | ConvertFrom-Json)
            if ($reports.Count -ne $audit.comparisons -or @($reports | Where-Object { !$_.result.accepted -or !$_.result.entities.accepted -or $_.result.entities.unexpectedDifferences.Count -ne 0 -or $null -ne $_.result.nonEntityDifference }).Count -ne 0) { throw 'Unexpected differences in finite acceptance' }
        }
        if ($summary.hostSeed -notin @(17,23)) { throw 'Unexpected C frontier host' }
        foreach ($row in $summary.generated) {
            if ($row.trace.writers -ne 1 -or $row.trace.selective -ne 1) { throw 'Foreign C frontier writer' }
            if (!(Test-Path (Join-Path $resultRoot "$variant/$($row.fixture.id).bin.gz"))) { throw 'Missing complete C base' }
        }
        if ($null -ne $previous) {
            if ($summary.pid -eq $previous.pid -or $summary.hostSeed -eq $previous.hostSeed -or $summary.priorJvmReloads -ne $summary.receivers.Count) { throw 'Independent C frontier replay missing' }
            foreach ($field in @('positiveSources','packages','accepted','rejected','pending','protected','pendingReceivers')) {
                if (($summary.$field | ConvertTo-Json -Depth 25 -Compress) -cne ($previous.$field | ConvertTo-Json -Depth 25 -Compress)) {
                    throw "C frontier differs: $field"
                }
            }
            foreach ($field in @('generated','receivers','scan')) {
                if ($summary.$field.Count -ne $previous.$field.Count) { throw "C frontier $field count differs" }
                for ($i = 0; $i -lt $summary.$field.Count; $i++) {
                    $a = $summary.$field[$i]; $b = $previous.$field[$i]
                    $names = if ($field -eq 'generated') { @('hash','outgoing') } elseif ($field -eq 'scan') { @('hash','cells') } else { @('x','z','seed','generated','incoming','rejected','pending','protected','baseHash') }
                    # Raw hashes are retained. The game-side audit compares EVERY scan/target snapshot,
                    # raw entities and all non-entity fields before the finite gate can pass.
                    if ($AcceptedFrontier) { $names = @($names | Where-Object { $_ -notin @('hash', 'baseHash') }) }
                    foreach ($name in $names) { if ($a.$name -cne $b.$name) { throw "C frontier $field $i $name differs" } }
                    if ($field -ne 'receivers' -and ($a.fixture | ConvertTo-Json -Compress) -cne ($b.fixture | ConvertTo-Json -Compress)) { throw 'C fixture differs' }
                    if ($field -eq 'generated') {
                        if (($a.calls | ConvertTo-Json -Depth 15 -Compress) -cne ($b.calls | ConvertTo-Json -Depth 15 -Compress)) { throw 'C frontier slot sequence differs' }
                        foreach ($name in @('writers','selective','slots','draws','randomHash')) { if ($a.trace.$name -cne $b.trace.$name) { throw 'C frontier RNG differs' } }
                    }
                }
            }
            foreach ($name in @('blockTicks','fluidTicks','postProcessing')) { if ($summary.effects.$name -cne $previous.effects.$name) { throw 'C frontier effects differ' } }
        }
        $previous = $summary
    }
    if ($AcceptedFrontier) { 'PASS finite entity acceptance: exact non-entity state, only native-proven drowned fields; original UUID-only hash failures remain separate. No physical application.' }
    else { 'PASS C-to-C finite frontier: Seed V1 provenance, full C bases, dense control, order/concurrency, independent receiving reference and cross-JVM replay. No physical application.' }
    exit 0
}
if ($CheckOnly -and $Flow) {
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS' -or $summary.proofVersion -ne 2 -or $summary.fixtures.Count -ne 21 -or $summary.bindings -ne 0 -or $summary.cells -le 0 -or $summary.treeSlots -le 0 -or $summary.coniferCells -le 0 -or $summary.coniferSlots -ne 2 -or $summary.peakSessions -lt 3 -or !$summary.physicalUnchanged -or !$summary.exceptionCleanup -or !$summary.capabilities -or $summary.repeatComparisons -ne 14) { throw 'Incomplete C full-flow proof' }
        if ($summary.hostSeed -notin @(17,23)) { throw 'Unexpected C host' }
        foreach ($fixture in $summary.fixtures) {
            if ($fixture.trace.writers -ne 1 -or $fixture.trace.selective -ne 1 -or $fixture.trace.draws -le 0) { throw 'Missing native C full-loop trace' }
            foreach ($suffix in @('.bin.gz','.nbt','-entities-raw.json')) {
                if (!(Test-Path -LiteralPath (Join-Path $resultRoot "$variant/$($fixture.fixture.id)$suffix"))) { throw 'Missing C complete snapshot/raw evidence' }
            }
        }
        if ($null -ne $previous) {
            if ($previous.pid -eq $summary.pid -or $previous.hostSeed -eq $summary.hostSeed) { throw 'C host JVM not independent' }
            for ($i = 0; $i -lt $summary.fixtures.Count; $i++) {
                $a = $previous.fixtures[$i]; $b = $summary.fixtures[$i]
                foreach ($field in @('fixture','hash','bHash','calls','treeSlots','cells','blockEntities','entities','blockTicks','fluidTicks','postProcessing','pendingBlockEntities')) {
                    if (($a.$field | ConvertTo-Json -Depth 25 -Compress) -cne ($b.$field | ConvertTo-Json -Depth 25 -Compress)) { throw "C full-flow $i differs: $field" }
                }
                foreach ($field in @('trace','rejected','outsideEffects','levelEscapes')) {
                    $names = @($a.$field.PSObject.Properties.Name | Sort-Object)
                    if (($names -join ',') -cne (($b.$field.PSObject.Properties.Name | Sort-Object) -join ',')) { throw "C $field schema differs" }
                    foreach ($name in $names) { if ($a.$field.$name -cne $b.$field.$name) { throw "C $i $field $name differs" } }
                }
            }
        }
        $previous = $summary
    }
    'PASS C full target FEATURES: dense policy control, native RNG sequence, selective trees, strict fallback, reverse/concurrent requests and two unrelated host JVMs.'
    exit 0
}
if ($CheckOnly) {
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS' -or $summary.proofVersion -ne 2 -or $summary.kernels.Count -ne 56 -or $summary.bindings -ne 0 -or $summary.cells -le 0 -or $summary.hives -le 0 -or $summary.receiving.accepted -le 0 -or $summary.receiving.protected -le 0 -or $summary.receiving.effects.coniferCells -le 0 -or $summary.receiving.physicalApplications -ne 0) { throw 'Incomplete C kernel/receiving proof' }
        foreach ($tree in @('spruce','pine','fallen_spruce_tree')) {
            $positive = @($summary.kernels | Where-Object { $_.feature -ceq $tree -and $_.success -and $_.cells -gt 0 })
            if ($positive.Count -eq 0) { throw "Missing real C conifer kernel $tree" }
        }
        if ($summary.receiving.effects.hives -le 0 -or $summary.receiving.effects.blockTicks -le 0 -or $summary.receiving.effects.unattachedRejected -le 0) { throw 'Missing C receiving side-effect positives' }
        if ($summary.hostSeed -notin @(17,23)) { throw 'Unexpected C host' }
        if ($null -ne $previous) {
            if ($previous.pid -eq $summary.pid -or $previous.hostSeed -eq $summary.hostSeed) { throw 'C host JVM not independent' }
            if ($summary.receiving.priorJvmReloads -ne $summary.receiving.receivers -or $summary.cells -ne $previous.cells -or $summary.hives -ne $previous.hives) { throw 'C independent replay incomplete' }
            for ($index = 0; $index -lt $summary.kernels.Count; $index++) {
                $currentKernel = $summary.kernels[$index]
                $previousKernel = $previous.kernels[$index]
                $fields = @($currentKernel.PSObject.Properties.Name | Sort-Object)
                if (($fields -join ',') -cne (($previousKernel.PSObject.Properties.Name | Sort-Object) -join ',')) { throw 'C kernel evidence schema differs' }
                foreach ($field in $fields) {
                    if ($field -eq 'effects') {
                        foreach ($effect in @('blockTicks', 'fluidTicks', 'postProcessing')) {
                            if ($currentKernel.effects.$effect -cne $previousKernel.effects.$effect) { throw "C kernel $index cross-host effect differs: $effect" }
                        }
                    } elseif ($currentKernel.$field -cne $previousKernel.$field) { throw "C kernel $index cross-host field differs: $field" }
                }
            }
        }
        $previous = $summary
    }
    'PASS C tree kernel / detached replay only: native data, pending receiver, idempotency, conflict ordering, protection and independent-JVM reload.'
    exit 0
}
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$originalAffinity = $orchestrator.ProcessorAffinity
$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$world = Join-Path $validationServer 'feature-c-validation'
if ($AcceptedFrontier) { & $PSCommandPath -NativeRepeat -CheckOnly }
if ($AcceptedShulker) { & $PSCommandPath -NativeShulker -CheckOnly }
try {
    $orchestrator.PriorityClass = 'BelowNormal'
    $orchestrator.ProcessorAffinity = 3840
    if (Test-Path -LiteralPath $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 -Dfeaturec.export=true' + $flowProperty
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureCServer" --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' '--args=nogui --world feature-c-validation' *> (Join-Path $resultRoot 'export.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $resultRoot 'export.log') -Tail 65; throw 'C launcher export failed' }
    $launch = Get-Content -LiteralPath (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    Move-ValidationToRecycleBin -LiteralPath $world
    $variants = if ($Recovery -or $Append) { 0..3 } elseif ($NativeRepeat -or $NativeShulker) { 0..2 } else { 0..1 }
    foreach ($hostVariant in $variants) {
        $worldName = if ($Append) { if ($hostVariant -lt 2) { 'feature-c-append-forward' } else { 'feature-c-append-reverse' } } elseif ($NativeRepeat -or $NativeShulker) { "feature-c-native-$hostVariant" } else { 'feature-c-validation' }
        $world = Join-Path $validationServer $worldName
        if ((-not $physicalRun -or $hostVariant -eq 0 -or ($Append -and $hostVariant -eq 2)) -and (Test-Path -LiteralPath $world)) { Move-ValidationToRecycleBin -LiteralPath $world }
        $hostSeed = if ($NativeRepeat -or $NativeShulker) { [string]$launch.nativeFixture.seed } elseif ($PhysicalHives -or $EditProtection -or $Append) { '1' } elseif ($Physical -or $PreLight -or $Recovery) { '23' } elseif ($hostVariant -eq 0) { '17' } else { '23' }
        [IO.File]::WriteAllText($validationProperties, [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$hostSeed"), [Text.UTF8Encoding]::new($false))
        $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeaturec.variant=host_$hostVariant" + $flowProperty
        $start = [Diagnostics.ProcessStartInfo]::new($launch.java)
        $start.WorkingDirectory = $launch.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $start.CreateNoWindow = $true
        foreach ($argument in $launch.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launch.classpath); $start.ArgumentList.Add($launch.main)
        for ($i = 0; $i -lt $launch.gameArguments.Count; $i++) {
            $argument = $launch.gameArguments[$i]
            if ($i -gt 0 -and $launch.gameArguments[$i - 1] -eq '--world') { $argument = $worldName }
            $start.ArgumentList.Add($argument)
        }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
        $process.ProcessorAffinity = 3840
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        [IO.File]::WriteAllText((Join-Path $resultRoot "host_$hostVariant.log"), $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        $expectedExit = if ($Recovery -and $hostVariant -eq 0) { 73 } elseif ($Recovery -and $hostVariant -eq 1) { 74 } else { 0 }
        $evidenceName = if ($Recovery -and $hostVariant -lt 2) { 'interruption.json' } else { 'summary.json' }
        if ($process.ExitCode -ne $expectedExit -or -not (Test-Path -LiteralPath (Join-Path $resultRoot "host_$hostVariant/$evidenceName"))) { Get-Content (Join-Path $resultRoot "host_$hostVariant.log") -Tail 90; throw 'C runtime failed or produced no expected evidence' }
        $process.Dispose()
        if (-not $physicalRun -or ($Append -and $hostVariant % 2 -eq 1) -or $hostVariant -eq $(if ($Recovery -or $Append) { 3 } else { 1 })) { Move-ValidationToRecycleBin -LiteralPath $world }
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
    $orchestrator.PriorityClass = $originalPriority
    $orchestrator.ProcessorAffinity = $originalAffinity
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
}
& $PSCommandPath -CheckOnly -Flow:$Flow -Frontier:$Frontier -Replay:$Replay -NativeRepeat:$NativeRepeat -Physical:$Physical -AcceptedFrontier:$AcceptedFrontier -NativeShulker:$NativeShulker -AcceptedShulker:$AcceptedShulker -PhysicalHives:$PhysicalHives -PreLight:$PreLight -EditProtection:$EditProtection -Recovery:$Recovery -Append:$Append
