param([ValidateSet('1.20.1-forge','1.21.1-neoforge')][string]$Target = '1.20.1-forge', [ValidateSet('smoke','structures','cold','screen','ocean')][string]$Suite = 'smoke', [ValidateSet('independent','shared')][string]$Preparation = 'independent', [ValidatePattern('^[a-z0-9_-]*$')][string]$FixtureId = '', [switch]$Handoff, [switch]$CheckOnly, [switch]$Offline, [switch]$Profile, [switch]$ExportOnly, [switch]$ArchiveInterrupted, [switch]$IdentityLifecycle, [switch]$RingScope, [switch]$NarrowLocks, [switch]$ReuseSources, [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = '', [ValidateSet('conformance-legacy_b','integration-world_identity')][string]$ValidationName = 'conformance-legacy_b', [long]$CpuAffinity = 3840)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = $Target
$Preparation=$Preparation.ToLowerInvariant(); $Suite=$Suite.ToLowerInvariant()
if($ExportOnly -and ($Suite -ne 'structures' -or $FixtureId -or $Handoff -or $CheckOnly -or $Profile)){throw 'ExportOnly only discovers native structure fixtures; it is not a parity run'}
if($Preparation -eq 'shared' -and $Target -ne '1.21.1-neoforge'){throw 'World-state sharing has not been adapted to this Target'}
if($Handoff -and (($Target -eq '1.21.1-neoforge' -and $Preparation -ne 'shared') -or ($Target -eq '1.20.1-forge' -and $Preparation -ne 'independent'))){throw 'B handoff requires the independently audited preparation of this Target'}
if($FixtureId -and $Suite -ne 'structures'){throw 'Single-case diagnostics require structure fixtures'}
if($ArchiveInterrupted -and ($CheckOnly -or $ExportOnly -or $Profile -or $FixtureId)){throw 'Explicit interrupted archive is only for a complete correctness suite rerun'}
if($IdentityLifecycle -and ($Target -ne '1.21.1-neoforge' -or $Suite -ne 'smoke' -or !$Handoff -or $ExportOnly -or $ArchiveInterrupted)){throw 'Identity boundary evidence requires 1.21.1 shared Handoff smoke'}
if($ValidationName -eq 'integration-world_identity' -and $Target -ne '1.21.1-neoforge'){throw 'World identity integration is only adapted to 1.21.1'}
if($IdentityLifecycle -and $ValidationName -ne 'integration-world_identity'){throw 'Use the separate world identity validation instance'}
$suiteName=$Suite+$(if($Preparation -eq 'shared'){'-shared'}else{''})
if($Handoff){$suiteName+='-handoff'}
if($IdentityLifecycle){$suiteName+='-identity'}
if($RingScope -and (!$Handoff -or ($Target -eq '1.21.1-neoforge' -and $Preparation -ne 'shared') -or ($Target -eq '1.20.1-forge' -and $Preparation -ne 'independent'))){throw 'Ring candidate requires this Target native handoff preparation'}
if($RingScope){$suiteName+='-ring-scope'}
if($NarrowLocks -and (!$RingScope -or !$Handoff -or !$EvidenceLabel)){throw 'Narrow locks require a separately labelled native ring/handoff proof'}
if($NarrowLocks){$suiteName+='-narrow-locks'}
if($ReuseSources -and !$NarrowLocks){throw 'Scoped source reuse requires the audited 1.21.1 native narrow-lock/shared guard'}
if($ReuseSources){$suiteName+='-source-reuse'}
if($EvidenceLabel -and !$RingScope -and !$Handoff){throw 'Evidence labels require an explicit ring or handoff proof'}
if($EvidenceLabel){$suiteName+='-'+$EvidenceLabel}
if($FixtureId){$suiteName+="-one-$FixtureId"}
$validation = Join-Path $repo "validations/$ValidationName"
$instance = Join-Path $validation "instance/$target/server"
$output = Join-Path $validation "result/$suiteName/$target"
$expectedCount = if ($Suite -in @('smoke','screen')) {4} elseif ($Suite -in @('cold','ocean')) {1} elseif ($Target -eq '1.20.1-forge') {15} else {18}
$executedCount = if($Suite -eq 'screen' -or $FixtureId){1}else{$expectedCount}
$prefix = (ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id + '.featureBInputs.'

function Get-InputHash {
    $sources=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -File -Recurse | Sort-Object FullName)
    $fingerprint=($sources | ForEach-Object {$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}

function Check-Evidence {
    $currentHash=Get-InputHash
    & (Join-Path $PSScriptRoot 'Run-ShulkerYawCausality.ps1') -Target $Target -ValidationName $ValidationName -RingScope:$RingScope -EvidenceLabel $EvidenceLabel -CheckOnly
    if($Preparation -eq 'shared') {
        $borrowing=Get-Content (Join-Path $output 'world-borrowing.json') -Raw | ConvertFrom-Json
        if($borrowing.status -cne 'PASS' -or !$borrowing.sameWorldState -or !$borrowing.differentSeedDetached -or !$borrowing.differentDimensionDetached -or !$borrowing.childClosePreservesOwner -or !$borrowing.ownerCloseRejectsChild -or !$borrowing.outOfRangeRejected){throw 'Incomplete world-state ownership proof'}
    }
    $fixtures = @(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($fixtures.Count -ne $expectedCount -or @($fixtures.id | Sort-Object -Unique).Count -ne $expectedCount) { throw 'Incomplete legacy B fixture manifest' }
    if($Suite -eq 'screen'){$fixtures=@($fixtures | Where-Object id -CEQ origin);if($fixtures.Count -ne 1){throw 'Screen fixture missing'}}
    if($FixtureId){$fixtures=@($fixtures | Where-Object id -CEQ $FixtureId);if($fixtures.Count -ne 1){throw 'Requested diagnostic fixture missing'}}
    $writtenStructures=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($fixture in $fixtures) {
        $rows = @()
        foreach ($role in @('native_0','native_1','host_17','host_23')) {
            $root = Join-Path $output "$($fixture.id)/$role"
            $row = Get-Content (Join-Path $root 'summary.json') -Raw | ConvertFrom-Json
            if($row.inputHash -cne $currentHash){throw 'B evidence differs from current Target source fingerprint'}
            if ($row.status -cne $(if ($role -eq 'native_0') {'PRODUCED'} else {'PASS'}) -or $row.role -cne $role -or $row.fixture -cne $fixture.id -or $row.localSeed -ne $fixture.local -or $row.x -ne $fixture.x -or $row.z -ne $fixture.z -or $row.dimension -cne $fixture.dimension -or $row.bindings -ne 0 -or $row.trace.writers -ne 1 -or $row.trace.maxWriters -ne 1 -or $row.trace.decorationSeedReads -ne 1 -or $row.trace.featureSeedCalls -le 0) { throw "Incomplete B proof $($fixture.id) $role" }
            if ($role.StartsWith('native_')) { if ($row.hostSeed -ne $row.localSeed) { throw 'Native B seed not S(C)' } }
            elseif ($row.hostSeed -eq $row.localSeed -or !$row.physicalHostUnchanged) { throw 'B host isolation failed' }
            if($RingScope -and $Target -eq '1.20.1-forge') {
                if(!$row.ringCandidateOptIn){throw 'Native ring candidate property missing from actual game'}
                if($role.StartsWith('native_')) {
                    if($null -eq $row.physicalRingScopeBound -or $row.physicalRingScopeBound){throw 'Native B Oracle acquired a finite scope'}
                }elseif(!$row.ringScope -or $row.ringScope.fallback -cne 'NONE' -or $row.ringScope.queryRadius -ne 11 -or $row.ringScope.activeSuppliers -ne 0 -or $row.ringScope.suppliers -ne $row.ringScope.slotsConsidered){throw 'Native ring candidate did not actually complete'}
            }
            if ($IdentityLifecycle) {
                $identity = Get-Content (Join-Path $root 'identity.json') -Raw | ConvertFrom-Json
                $closed = Get-Content (Join-Path $root 'identity-close.json') -Raw | ConvertFrom-Json
                if ($identity.status -cne 'PASS' -or $closed.status -cne 'PASS' -or !$closed.closedRejectsQueries -or
                        $identity.pid -ne $row.pid -or $closed.pid -ne $row.pid -or $identity.worldSeed -ne $row.hostSeed -or
                        $identity.scope -cne 'NATIVE_IDENTITY_BOUNDARY_NOT_MOSAIC_FULL' -or $identity.dimensionCount -ne 3 -or
                        $identity.memoryQueries -ne 10000 -or !$identity.replacementInvalidatedAllDimensions -or
                        !$identity.mixedProfileRejected -or !$identity.explicitReloadRestoredVanilla -or !$identity.authorityUnchanged) {
                    throw 'Incomplete native world identity lifecycle evidence'
                }
            }
            if($Handoff) {
                if($Target -eq '1.21.1-neoforge' -and !$role.StartsWith('native_')) {
                    $collision=Get-Content (Join-Path $root 'spawn-collision-after.json') -Raw|ConvertFrom-Json
                    if(!$collision.nativeInputsIdentical -or !$collision.afterOwnerCloseIdentical -or !$collision.copyMutationComponentOnly -or $collision.chunks -ne 8 -or $collision.voxels -le 0){throw 'Incomplete detached native population collision proof'}
                }
                if($row.handoff.structureLoadSeed -ne $fixture.local){throw 'Artifact/native loader did not observe S(C)'}
                if($fixture.structure -and !$fixture.terrainOnly -and $fixture.startX -eq $fixture.x -and $fixture.startZ -eq $fixture.z -and $row.handoff.structureLoadCalls -le 0){throw 'Structure loader seed observation positive was not hit'}
                if($role.StartsWith('native_')) {
                    if(!$row.handoff.nativeLoadControl -or !(Test-Path (Join-Path $root 'handoff-native-load-control.nbt.gz'))){throw 'Missing independent native load control'}
                }else {
                    if(!$row.handoff.artifactRoundtrip -or !$row.handoff.closedOwner -or $row.handoff.spawnBiomeCells -le 0){throw 'Incomplete detached artifact/SPAWN handoff'}
                    foreach($file in @('handoff-before.nbt.gz','handoff-after.nbt.gz','handoff-payload.nbt.gz','handoff-pending-before.nbt.gz','handoff-pending-after.nbt.gz')) {
                        if(!(Test-Path (Join-Path $root $file))){throw 'Missing raw detached handoff state'}
                    }
                }
            }
            if($Target -eq '1.21.1-neoforge') {
                foreach($file in @('uuid-only.nbt.gz','uuid-only.txt','shulker-yaw.json')){if(!(Test-Path (Join-Path $root $file))){throw 'Missing raw UUID-only/native-constructor evidence'}}
                $yaw=Get-Content (Join-Path $root 'shulker-yaw.json') -Raw | ConvertFrom-Json
                if($yaw.mode -cne 'off' -or $row.shulkerYawAcceptanceCount -ne $yaw.matchedTargetEntities){throw 'B altered constructor entropy or acceptance count'}
                if($row.shulkerYawAcceptanceCount -gt 0){
                    if($row.entityAcceptanceRule -cne '1.21.1-native-end-city-sentry-constructor-yaw-1' -or @($yaw.constructors).Count -ne $row.shulkerYawAcceptanceCount){throw 'Unknown entity acceptance rule'}
                }elseif($row.entityAcceptanceRule -cne 'root-uuid-only' -or $row.uuidOnlySnapshot -cne $row.snapshot){throw 'Unexpected non-UUID normalization'}
                if($null -eq $row.outerInputs -or $null -eq $row.liveContexts -or $row.liveContexts -ne 0){throw 'Missing B outer-input/lifetime proof or context leaked'}
                $outer=@($row.outerInputs.PSObject.Properties)
                if($outer.Count -gt 0) {
                    if(@(Get-ChildItem (Join-Path $root 'outer_inputs') -Filter '*.nbt.gz' -File).Count -ne $outer.Count){throw 'Incomplete actual outer-input bodies'}
                    foreach($entry in $outer) {
                        if($entry.Name -cnotmatch '^-?[0-9]+_-?[0-9]+$' -or !(Test-Path (Join-Path $root "outer_inputs/$($entry.Name).nbt.gz"))){throw 'Missing outer-input owner body'}
                    }
                }
                if(!$role.StartsWith('native_')) {
                    $borrowed=if($Preparation -eq 'shared'){80}else{0}
                    $roots=if($Preparation -eq 'shared'){1}else{81}
                    if($row.borrowedContexts -ne $borrowed -or $row.worldStateBuilds -ne $roots+@($row.outerInputs.PSObject.Properties).Count){throw 'B rebuilt or borrowed an unexpected world context'}
                    if($row.dimension -ceq 'minecraft:overworld') {
                        if($row.ringPreparations -le 0 -or (!$RingScope -and $row.ringBiomeSearches -le 0)){throw 'Missing actual Stronghold preparation counters'}
                        if($RingScope -and ($row.ringBiomeSearches -lt 0 -or $row.ringBiomeSearches -gt 128*$row.ringPreparations)){throw 'Invalid bounded native ring search count'}
                    }
                }
            }
            if($NarrowLocks){
                if($Target -eq '1.20.1-forge'){
                    if($role.StartsWith('native_')){if($row.narrowTemplateLocks){throw 'Native Oracle entered narrow-lock contexts'}}
                    elseif(!$row.narrowTemplateLocks){throw 'Native Forge narrow source guard did not actually activate'}
                }else{
                    if($role.StartsWith('native_')){if($row.narrowContexts -ne 0){throw 'Native Oracle entered narrow-lock contexts'}}
                    elseif($row.narrowContexts -le 0 -or $row.narrowContexts -ne $row.worldStateBuilds+$row.borrowedContexts){throw 'Narrow source guard did not actually activate'}
                }
            }
            if($ReuseSources -and !$role.StartsWith('native_')) {
                $reuse=$row.structureReuse
                if(!$row.targetStructureGraphDetached -or !$reuse -or $reuse.queries -ne 81*289 -or $reuse.hits -le 0 -or $reuse.evaluations -le 0 -or
                        $reuse.queries -ne $reuse.hits+$reuse.evaluations -or $reuse.peakResident -gt 441 -or
                        ($reuse.climateState -and $reuse.stateReplays -ne $reuse.hits) -or
                        $reuse.transferred -lt 1 -or $reuse.uniqueSources -ne 441 -or !$reuse.closed -or
                        $reuse.resident -ne 0){throw 'Scoped source reuse did not actually execute its bounded ownership handoff/cleanup'}
            }
            if ($fixture.id -eq 'end-spike' -and $row.entities -le 0) { throw 'Missing actual End Crystal' }
            if ($Suite -eq 'cold') {
                if (@($row.trace.lightReads).Count -eq 0 -or $row.snowLayers -le 0 -or !$row.physicalPoiUnchanged) { throw 'Cold B lacks real snow/light/isolation proof' }
                if ($role.StartsWith('native_') -and $row.nativeLightReadCrossChecks -ne @($row.trace.lightReads).Count) { throw 'Fresh-light candidate differs from physical native reads' }
            }
            if($Suite -eq 'ocean' -and @($row.trace.carvingMaskReads).Count -le 0){throw 'Ocean regression did not reach native carving-mask placement'}
            if ($fixture.structure -and !$fixture.terrainOnly) {
                $key="minecraft:$($fixture.structure)@[$($fixture.startX), $($fixture.startZ)]"
                if ($key -cnotin @($row.trace.structurePlacements)) { throw "No real structure placement $key" }
                if($Target -eq '1.21.1-neoforge' -and $role -ceq 'native_0' -and $row.trace.structureBlockWrites.$key -gt 0){[void]$writtenStructures.Add($fixture.structure)}
            }
            foreach ($field in @('physicalEntitiesDelta','blockSchedulerDelta','fluidSchedulerDelta')) { if ($null -eq $row.$field -or $row.$field -ne 0) { throw "B physical lifecycle changed $field" } }
            if (@($row.preInputs.PSObject.Properties).Count -ne 9 -or @(Get-ChildItem (Join-Path $root 'pre_inputs') -Filter '*.nbt.gz' -File).Count -ne 9) { throw 'Missing exact B prerequisites' }
            foreach ($file in @('raw.nbt.gz','canonical.nbt.gz','canonical.txt','structures.json','physical-poi-before.json','physical-poi-after.json')) { if (!(Test-Path (Join-Path $root $file))) { throw "Missing raw B proof $file" } }
            if ($row.physicalPoiUnchanged -ne $true -or (Get-FileHash (Join-Path $root 'physical-poi-before.json')).Hash -cne (Get-FileHash (Join-Path $root 'physical-poi-after.json')).Hash) { throw 'B changed physical POI' }
            if($Profile -and $role -ceq 'host_17' -and (!(Test-Path (Join-Path $root 'profile.jfr')) -or (Get-Item (Join-Path $root 'profile.jfr')).Length -le 1024)){throw 'Missing B preparation profile'}
            if ($role -ne 'native_0') {
                foreach ($field in @('fixture','dimension','x','z','localSeed','inputHash','environment','snapshot','shulkerYawAcceptanceCount','entityAcceptanceRule','structures','trace','preInputs','outerInputs','rejected')) {
                    if (($row.$field | ConvertTo-Json -Depth 90 -Compress) -cne ($rows[0].$field | ConvertTo-Json -Depth 90 -Compress)) { throw "B proof differs $field" }
                }
                if ((Get-FileHash (Join-Path $root 'canonical.txt')).Hash -cne (Get-FileHash (Join-Path $output "$($fixture.id)/native_0/canonical.txt")).Hash) { throw 'B canonical body differs' }
            }
            $rows += $row
        }
        if (@($rows.pid | Sort-Object -Unique).Count -ne 4 -or @($rows.save | Sort-Object -Unique).Count -ne 4) { throw 'B proof reused a JVM/save' }
    }
    if($Target -eq '1.21.1-neoforge' -and $Suite -eq 'structures') {
        foreach($structure in @($fixtures | Where-Object {$_.structure -and !$_.terrainOnly} | ForEach-Object structure | Sort-Object -Unique)) {
            if(!$writtenStructures.Contains($structure)){throw "No real target block change observed for structure $structure"}
        }
    }
    "PASS: $target legacy B $Suite $executedCount/$executedCount, $($executedCount*4) independent JVM/save proofs, finite version-specific native entity rules."
}
if ($CheckOnly) { Check-Evidence; exit }
if(!$ExportOnly){& (Join-Path $PSScriptRoot 'Run-ShulkerYawCausality.ps1') -Target $Target -ValidationName $ValidationName -RingScope:$RingScope -EvidenceLabel $EvidenceLabel -CheckOnly}
$local = Join-Path $repo 'local.toml'
if (!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) { throw 'Accept the Minecraft EULA explicitly in local.toml' }
Add-Type -AssemblyName Microsoft.VisualBasic
New-Item -ItemType Directory -Force -Path $instance | Out-Null
$properties = Join-Path $instance 'server.properties'
$oldProperties = if (Test-Path $properties) { [IO.File]::ReadAllText($properties) } else { $null }
$oldOptions = $env:JAVA_TOOL_OPTIONS
$process = [Diagnostics.Process]::GetCurrentProcess()
$oldPriority = $process.PriorityClass; $oldAffinity = $process.ProcessorAffinity
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($CpuAffinity -band $oldAffinity.ToInt64()) -ne $CpuAffinity){throw 'Correctness queue requires exactly four available CPUs'}
$launcher = $null

function Recycle-World([string]$world) {
    $path = [IO.Path]::GetFullPath((Join-Path $instance $world))
    if (!$path.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $world -notmatch '^feature-b-[a-z0-9_-]+$') { throw 'Unsafe B world cleanup' }
    if (Test-Path $path) {
        if ((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked B world' }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
}

function Preserve-Failure {
    $first = Join-Path $validation "result/first_failure/$suiteName/$target"
    if ((Test-Path $output) -and !(Test-Path $first)) { New-Item -ItemType Directory -Force -Path (Split-Path -Parent $first) | Out-Null; Copy-Item -LiteralPath $output -Destination $first -Recurse }
    $contentFailure=Join-Path $validation "result/first_content_failure/$suiteName/$target"
    $failedProofs=@(Get-ChildItem $output -Filter summary.json -Recurse -ErrorAction SilentlyContinue | Where-Object {(Get-Content $_.FullName -Raw | ConvertFrom-Json).status -ceq 'FAIL'})
    if($failedProofs.Count -gt 0 -and !(Test-Path $contentFailure)) { New-Item -ItemType Directory -Force -Path (Split-Path -Parent $contentFailure) | Out-Null; Copy-Item -LiteralPath $output -Destination $contentFailure -Recurse }
    foreach ($entry in @(Get-ChildItem $output -Filter summary.json -Recurse -ErrorAction SilentlyContinue)) {
        $proof = Get-Content $entry.FullName -Raw | ConvertFrom-Json
        if ($proof.status -cne 'FAIL') { continue }
        $saved = [IO.Path]::GetFullPath($proof.save)
        if (!$saved.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $proof.fixture -notmatch '^[a-z0-9_-]+$' -or $proof.role -notin @('native_0','native_1','host_17','host_23')) { throw 'Unsafe failed B save archive' }
        $backup = Join-Path $instance "first-failure-$suiteName-$($proof.fixture)-$($proof.role)"
        if ((Test-Path $saved) -and !(Test-Path $backup)) {
            if ((Get-Item $saved).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked failure world' }
            Copy-Item -LiteralPath $saved -Destination $backup -Recurse
        }
    }
    foreach ($world in @(Get-ChildItem -LiteralPath $instance -Directory -Filter 'feature-b-*')) {
        $resolved = [IO.Path]::GetFullPath($world.FullName)
        if (!$resolved.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or ($world.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe incomplete B world archive' }
        $backup = Join-Path $instance "first-failure-$suiteName-$($world.Name)"
        if (!(Test-Path $backup)) { Copy-Item -LiteralPath $resolved -Destination $backup -Recurse }
    }
}

function Preserve-Interrupted {
    if (!(Test-Path -LiteralPath $output)) { throw 'No interrupted output to preserve' }
    if (@(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^javaw?\.exe$' -and ($_.CommandLine -like "*$instance*" -or
            ($_.CommandLine -like "*$repo*" -and $_.CommandLine -like "*-Dvalidation.target=$target*" -and
                $_.CommandLine -like "*-Dvalidation.name=$ValidationName*"))
    }).Count) { throw 'Interrupted instance still has a game JVM' }
    $summaries = @(Get-ChildItem -LiteralPath $output -Filter summary.json -Recurse -File)
    if ($summaries.Count -le 0 -or $summaries.Count -ge $executedCount * 4) { throw 'Expected an explicitly interrupted, incomplete suite' }
    foreach ($summary in $summaries) {
        if ((Get-Content -LiteralPath $summary.FullName -Raw | ConvertFrom-Json).status -cnotin @('PASS','PRODUCED')) { throw 'Do not classify a recorded failure as an interruption' }
    }
    $archive = Join-Path $validation "result/interrupted/$suiteName/$target"
    if (Test-Path -LiteralPath $archive) { throw 'Interrupted archive already exists; never overwrite evidence' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $archive) | Out-Null
    Copy-Item -LiteralPath $output -Destination $archive -Recurse
    $worlds = @()
    foreach ($world in @(Get-ChildItem -LiteralPath $instance -Directory -Filter 'feature-b-*')) {
        $resolved = [IO.Path]::GetFullPath($world.FullName)
        if (!$resolved.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $world.Name -cnotmatch '^feature-b-[a-z0-9_-]+$' -or ($world.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe interrupted world archive' }
        $backup = Join-Path $instance "interrupted-$suiteName-$($world.Name)"
        if (Test-Path -LiteralPath $backup) { throw 'Interrupted world archive already exists' }
        Copy-Item -LiteralPath $resolved -Destination $backup -Recurse
        $worlds += $backup
    }
    $record = [ordered]@{status='INTERRUPTED';target=$target;suite=$suiteName;classification='Explicit user-confirmed external process termination; neither PASS nor FAIL';completedProofs=$summaries.Count;expectedProofs=$executedCount*4;originalOutput=$output;preservedWorlds=$worlds;archivedAt=[DateTimeOffset]::Now.ToString('o')}
    [IO.File]::WriteAllText((Join-Path $archive 'interruption.json'),($record | ConvertTo-Json -Depth 6)+"`n")
    Write-Host "INTERRUPTED evidence preserved: $archive"
}

function Invoke-World([string]$world,[string]$seed,[string[]]$flags,[string]$log,[string]$proof,[string]$status) {
    Recycle-World $world
    [IO.File]::WriteAllText($properties,"level-name=$world`nlevel-seed=$seed`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
    $crashes=@{}; Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { $crashes[$_.Name]=(Get-FileHash $_.FullName).Hash }
    $env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 '+($flags -join ' ')
    if($RingScope){$env:JAVA_TOOL_OPTIONS+=' -Drandomnibble6plus24generator.structure.lazyRings=true'}
    if ($null -eq $launcher) {
        Push-Location $repo
        try { [string[]]$networkArgs = if ($Offline) {@('--offline')} else {@()}; $name=($ValidationName -split '[-_]' | ForEach-Object { $_.Substring(0,1).ToUpperInvariant()+$_.Substring(1) }) -join ''; & .\gradlew.bat ":version:${target}:runValidation${name}Server" @networkArgs --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> $log; if ($LASTEXITCODE -ne 0) { Get-Content $log -Tail 35; throw 'B native Gradle launch failed' } } finally { Pop-Location }
    } else {
        $start=[Diagnostics.ProcessStartInfo]::new($launcher.java); $start.WorkingDirectory=$launcher.directory; $start.UseShellExecute=$false; $start.CreateNoWindow=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
        foreach($item in $launcher.environment.PSObject.Properties) { if($item.Name -cne 'MOD_CLASSES'){throw 'Unknown native loader environment'}; $start.Environment[$item.Name]=[string]$item.Value }
        foreach($argument in $launcher.jvmArguments){$start.ArgumentList.Add($argument)}
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launcher.classpath); $start.ArgumentList.Add($launcher.main)
        foreach($argument in $launcher.gameArguments){$start.ArgumentList.Add($argument)}
        $game=[Diagnostics.Process]::Start($start); $game.PriorityClass='BelowNormal'; $game.ProcessorAffinity=[IntPtr]$CpuAffinity
        $stdout=$game.StandardOutput.ReadToEndAsync();$stderr=$game.StandardError.ReadToEndAsync()
        $timedOut = !$game.WaitForExit(300000)
        if($timedOut){$game.Kill($true);$game.WaitForExit()}
        [IO.File]::WriteAllText($log,$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult());$code=$game.ExitCode;$game.Dispose()
        if($timedOut){throw "B fixture hung $world; output and failed world retained"}
        if($code -ne 0){Get-Content $log -Tail 35;throw "B game failed $world"}
    }
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New B crash $($_.Name)"} }
    if($proof -and (!(Test-Path $proof) -or (Get-Content $proof -Raw | ConvertFrom-Json).status -cne $status)){throw "Incomplete B result $world"}
    Recycle-World $world
}

try {
    $process.PriorityClass='BelowNormal';$process.ProcessorAffinity=[IntPtr]$CpuAffinity
    if ($ArchiveInterrupted) { Preserve-Interrupted }
    if(Test-Path $output) {
        $summaries=@(Get-ChildItem $output -Filter summary.json -Recurse)
        $failed=@($summaries | Where-Object {(Get-Content $_.FullName -Raw | ConvertFrom-Json).status -ceq 'FAIL'})
        if($failed.Count -gt 0){Preserve-Failure}
        elseif($summaries.Count -eq $executedCount*4) {
            $baseline=Join-Path $validation "result/baseline_initial/$suiteName/$target"
            if(!(Test-Path $baseline)) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $baseline) | Out-Null
                Copy-Item -LiteralPath $output -Destination $baseline -Recurse
            }
        }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($output,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Invoke-World 'feature-b-export' '0' @("-D$($prefix)export=true", "-D$($prefix)suite=$Suite", "-D$($prefix)preparation=$Preparation", "-D$($prefix)variant=$suiteName") (Join-Path $output 'export.log') '' ''
    $launcher=Get-Content (Join-Path $output 'launcher.json') -Raw | ConvertFrom-Json
    if($launcher.main -cne 'net.neoforged.devlaunch.Main' -or @($launcher.gameArguments).Count -ne 1 -or !$launcher.gameArguments[0].StartsWith('@')){$launcher=$null}
    $hash=Get-InputHash
    $fixtures=@(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    if($ExportOnly){
        if($fixtures.Count -ne $expectedCount -or @($fixtures.id | Sort-Object -Unique).Count -ne $expectedCount -or @($fixtures | Where-Object id -CEQ 'end_city-start').Count -ne 1){throw 'Incomplete native structure fixture discovery'}
        Write-Host "EXPORTED_ONLY_NOT_PARITY: $target structure fixtures $expectedCount"
        return
    }
    if($Suite -eq 'screen'){$fixtures=@($fixtures | Where-Object id -CEQ origin);if($fixtures.Count -ne 1){throw 'Screen fixture missing'}}
    if($FixtureId){$fixtures=@($fixtures | Where-Object id -CEQ $FixtureId);if($fixtures.Count -ne 1){throw 'Requested diagnostic fixture missing'}}
    foreach($fixture in $fixtures){foreach($role in @('native_0','native_1','host_17','host_23')){
        $root=Join-Path $output "$($fixture.id)/$role";New-Item -ItemType Directory -Force -Path $root | Out-Null
        $seed=if($role.StartsWith('native_')){[string]$fixture.local}else{$role.Substring(5)}
        $flags=@("-D$($prefix)suite=$Suite","-D$($prefix)preparation=$Preparation","-D$($prefix)variant=$suiteName","-D$($prefix)fixture=$($fixture.id)","-D$($prefix)role=$role","-D$($prefix)dimension=$($fixture.dimension)","-D$($prefix)local=$($fixture.local)","-D$($prefix)x=$($fixture.x)","-D$($prefix)z=$($fixture.z)","-D$($prefix)inputHash=$hash")
        if($NarrowLocks -and !$role.StartsWith('native_')){$flags+='-Drandomnibble6plus24generator.structure.narrowLocks=true'}
        if($ReuseSources -and !$role.StartsWith('native_')){$flags+='-Drandomnibble6plus24generator.structure.reuseSources=true'}
        if($Handoff){$flags+="-D$($prefix)handoff=true"}
        if($IdentityLifecycle){$flags+="-D$($prefix)identityLifecycle=true"}
        if($Profile -and $role -ceq 'host_17'){$flags+="-XX:StartFlightRecording=filename=$($root.Replace('\','/'))/profile.jfr,settings=profile,dumponexit=true,maxsize=128m"}
        Invoke-World "feature-b-$($fixture.id)-$role" $seed $flags (Join-Path $root 'console.log') (Join-Path $root 'summary.json') $(if($role -eq 'native_0'){'PRODUCED'}else{'PASS'})
        Write-Host "NATIVE B $($fixture.id) $role complete"
    }}
    Check-Evidence
} catch { Preserve-Failure; throw }
finally { $env:JAVA_TOOL_OPTIONS=$oldOptions; $process.PriorityClass=$oldPriority; $process.ProcessorAffinity=$oldAffinity; if($null -ne $oldProperties){[IO.File]::WriteAllText($properties,$oldProperties)} }
