param([ValidateSet('1.21.1-neoforge','1.20.1-forge')][string]$Target = '1.21.1-neoforge', [ValidateSet('smoke','matrix','structures')][string]$Suite = 'matrix', [switch]$CheckOnly, [switch]$Offline, [switch]$PhysicalBiomes, [switch]$RingScope, [switch]$NarrowLocks, [switch]$ReuseSources, [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = '', [switch]$Resume, [long]$CpuAffinity = 3840, [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash = '')
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = $Target
$expectedCount = switch ($Suite) { 'smoke' { 4 } 'matrix' { 72 } 'structures' { if ($Target -eq '1.20.1-forge') { 15 } else { 18 } } }
$validationName = if ($Target -eq '1.20.1-forge') { 'conformance-legacy_terrain' } else { 'conformance-prefeatures' }
$validationTask = if ($Target -eq '1.20.1-forge') { 'runValidationConformanceLegacyTerrainServer' } else { 'runValidationConformancePrefeaturesServer' }
$validation = Join-Path $repo "validations/$validationName"
if ($PhysicalBiomes -and $Target -ne '1.21.1-neoforge') { throw 'Detached native physical BIOMES has not been adapted to this Target' }
$variant = if ($PhysicalBiomes) { "physical_biomes_$Suite" } else { "legacy_terrain_$Suite" }
if ($RingScope) { $variant = if ($PhysicalBiomes) { "ring_scope_physical_biomes_$Suite" } else { "ring_scope_$Suite" } }
if ($NarrowLocks -and $Target -notin @('1.21.1-neoforge','1.20.1-forge')) { throw 'Narrow source locks have not been adapted to this Target' }
if ($NarrowLocks) { $variant = 'narrow_locks_' + $variant }
if ($ReuseSources -and !$NarrowLocks) { throw 'Scoped source reuse requires the separately audited 1.21.1 native guard' }
if ($ReuseSources) { $variant = 'source_reuse_' + $variant }
if ($EvidenceLabel -and !$ReuseSources -and !$RingScope -and $Suite -ne 'smoke') { throw 'Evidence labels require an explicit scoped candidate or a bounded native smoke regression' }
if ($EvidenceLabel) { $variant += '_' + $EvidenceLabel }
$output = Join-Path $validation "result/$variant/$target"
$prefix = (ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id + '.terrainInputs.'

function Get-SourceFingerprint {
    $sources = @(Get-ChildItem (Join-Path $repo "versions/$target/src"), (Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
    $fingerprint = ($sources | ForEach-Object { $_.FullName.Substring($repo.Length) + ':' + (Get-FileHash $_.FullName).Hash }) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}

function Check-Evidence([switch]$CompletedOnly, [string]$CurrentHash = '') {
    $completed = [Collections.Generic.List[string]]::new()
    $seenMissing = $false
    if ($RingScope -and $Suite -eq 'smoke') {
        $ring = Get-Content (Join-Path $output 'ring-proof.json') -Raw | ConvertFrom-Json
        if ($ring.status -cne 'PASS' -or $ring.positiveFocusedCases -ne 1536 -or $ring.falseNegatives -ne 0 -or $ring.queryRadius -ne 11) { throw 'Incomplete same-JVM ring component proof' }
        if ($Target -eq '1.20.1-forge' -and ($ring.inputHash -cne $CurrentHash -or $ring.ordinaryTargets -ne 1000 -or !$ring.parentRngContinuationIdentical -or !$ring.fallbackGuardsPassed -or $ring.activeSuppliers -ne 0 -or @($ring.nativeSeeds).Count -ne 4)) { throw 'Incomplete native parent-stream or ordinary-target proof' }
        if ($Target -eq '1.20.1-forge' -and $EvidenceLabel -eq 'climate_tail') {
            $tail = Get-Content (Join-Path $output 'ring-climate-tail.json') -Raw | ConvertFrom-Json
            if (!$ring.climateTailContinuationIdentical -or $tail.status -cne 'PASS' -or $tail.bidirectionalCases -ne 4 -or
                    !$tail.unsafeInputRejected -or !$tail.duplicateMinimaRejected -or !$tail.unknownParameterListRejected -or
                    @($tail.rows | Where-Object {!$_.originalSkipDiffers -or !$_.entryPreservedOnFallback -or !$_.exitLeafIdentityEqual -or !$_.continuationIdentical}).Count -ne 0) { throw 'Missing native climate cursor continuation proof' }
        }
    }
    $fixtures = @(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($fixtures.Count -ne $expectedCount -or @($fixtures.id | Select-Object -Unique).Count -ne $expectedCount) { throw 'Incomplete native terrain fixture manifest' }
    foreach ($fixture in $fixtures) {
        $root = Join-Path $output $fixture.id
        if ($CompletedOnly -and (!(Test-Path (Join-Path $root 'native/summary.json')) -or !(Test-Path (Join-Path $root 'lightweight/summary.json')))) { $seenMissing = $true; continue }
        if ($CompletedOnly -and $seenMissing) { throw 'Resume requires a contiguous fully verified prefix' }
        $a = Get-Content (Join-Path $root 'native/summary.json') -Raw | ConvertFrom-Json
        $b = Get-Content (Join-Path $root 'lightweight/summary.json') -Raw | ConvertFrom-Json
        if ($CurrentHash -and ($a.inputHash -cne $CurrentHash -or $b.inputHash -cne $CurrentHash)) { throw 'Native proof does not match the selected source fingerprint' }
        if ($RingScope -and $PhysicalBiomes -and $Suite -eq 'smoke' -and $ring.inputHash -cne $b.inputHash) { throw 'Ring component proof belongs to another source fingerprint' }
        if ($RingScope -and (!$b.ringScope -or $b.ringScope.fallback -cne 'NONE' -or $b.ringScope.queryRadius -ne 11)) { throw 'Candidate did not run in its audited finite scope' }
        if ($RingScope -and $Target -eq '1.20.1-forge' -and ($b.ringScope.activeSuppliers -ne 0 -or $b.ringScope.suppliers -ne $b.ringScope.slotsConsidered)) { throw 'Native ring supplier boundary incomplete' }
        if ($NarrowLocks -and !$b.narrowTemplateLocks) { throw 'Narrow source guard did not actually activate' }
        if ($ReuseSources -and (!$b.structureReuse -or $b.structureReuse.queries -ne 9*289 -or
                $b.structureReuse.uniqueSources -ne 361 -or $b.structureReuse.hits -le 0 -or
                $b.structureReuse.evaluations -ne 361+$b.structureReuse.stateMismatches -or
                $b.structureReuse.queries -ne $b.structureReuse.hits+$b.structureReuse.evaluations -or
                ($b.structureReuse.climateState -and $b.structureReuse.stateReplays -ne $b.structureReuse.hits) -or
                $b.structureReuse.transferred -ne 0)) { throw 'Pre-FEATURES scoped source reuse was not actually exercised' }
        if ($a.status -cne 'PRODUCED' -or $b.status -cne 'PASS' -or $a.pid -eq $b.pid -or $a.save -eq $b.save -or
                $a.hostSeed -ne $a.localSeed -or $b.hostSeed -eq $b.localSeed -or $a.featuresExecuted -ne 0 -or $b.featuresExecuted -ne 0 -or
                $a.regionBindings -ne 0 -or $b.regionBindings -ne 0 -or $b.terrainMetrics.protoChunks -ne 1 -or $b.terrainMetrics.biomeCarriers -ne 8 -or
                $b.terrainMetrics.generationHolders -ne 0 -or $b.terrainMetrics.carverSources -ne 289) { throw "Invalid native terrain boundary $($fixture.id)" }
        if ($a.density.samples -le 0 -or $a.density.slices -le 0 -or $b.density.samples -le 0) { throw 'No actual native density slices observed' }
        foreach ($field in @('fixture','dimension','x','z','localSeed','environment','inputHash','beardifier','checkpoints','density')) {
            if (($a.$field | ConvertTo-Json -Depth 90 -Compress) -cne ($b.$field | ConvertTo-Json -Depth 90 -Compress)) { throw "Evidence differs $($fixture.id) $field" }
        }
        if ($a.fixture -cne $fixture.id -or $a.dimension -cne $fixture.dimension -or $a.x -ne $fixture.x -or $a.z -ne $fixture.z -or $a.localSeed -ne $fixture.local) { throw 'Native terrain fixture identity differs' }
        $stages = @('structure_starts','structure_references','biomes','noise','surface','carvers')
        if (@($a.checkpoints.PSObject.Properties).Count -ne $stages.Count) { throw 'Incomplete six-stage proof' }
        $files = @('biome-frontier.json.gz','beardifier.bin','density-slices.bin')
        foreach ($stage in $stages) { $files += "$stage.canonical.gz"; $files += "$stage-structures.json" }
        foreach ($file in $files) {
            if ((Get-FileHash (Join-Path $root "native/$file")).Hash -cne (Get-FileHash (Join-Path $root "lightweight/$file")).Hash) { throw "Native terrain differs $($fixture.id) $file" }
        }
        if ($PhysicalBiomes) {
            if (!$b.physicalBiomesOwnerClosed -or ($b.physicalBiomes | ConvertTo-Json -Compress) -cne ($a.checkpoints.biomes | ConvertTo-Json -Compress)) { throw 'Detached physical BIOMES proof missing or different' }
            foreach ($file in @('biomes.canonical.gz','biomes-structures.json')) {
                if ((Get-FileHash (Join-Path $root "native/$file")).Hash -cne (Get-FileHash (Join-Path $root "lightweight/physical-biomes/$file")).Hash) { throw "Detached native BIOMES differs $($fixture.id) $file" }
            }
        }
        if ($fixture.structure) {
            $structure = Get-Content (Join-Path $root 'native/carvers-structures.json') -Raw | ConvertFrom-Json -AsHashtable
            $key = "minecraft:$($fixture.structure)@[$($fixture.startX), $($fixture.startZ)]"
            if (!$structure.pieces.ContainsKey($key) -or $structure.pieces[$key] -le 0) { throw "Positive structure has no pieces $key" }
        }
        if ($fixture.terrainOnly -and $a.beardifier.nonzero -le 0) { throw 'Empty terrain-only halo' }
        $completed.Add([string]$fixture.id)
    }
    if ($Suite -eq 'matrix') {
        if (@($fixtures | Where-Object dimension -CEQ 'minecraft:overworld').Count -ne 40 -or
                @($fixtures | Where-Object dimension -CEQ 'minecraft:the_nether').Count -ne 16 -or
                @($fixtures | Where-Object dimension -CEQ 'minecraft:the_end').Count -ne 16) { throw 'Incomplete dimension matrix' }
    }
    if ($CompletedOnly) {
        Write-Host "VERIFIED_PREFIX_NOT_COMPLETE: $($completed.Count)/$expectedCount, current source $CurrentHash"
        return $completed.ToArray()
    }
    Write-Host "PASS: $target independent native/lightweight six-stage $expectedCount fixtures."
}
if ($CheckOnly -and $Resume) { throw 'Resume executes only missing fixtures; use CheckOnly independently' }
if ($ExpectedInputHash -and !$CheckOnly) { throw 'Explicit historical fingerprints are read-only; never generate under another input identity' }
if ($CheckOnly) {
    $selectedHash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{Get-SourceFingerprint}
    Check-Evidence -CurrentHash $selectedHash
    Write-Host $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT $selectedHash"}else{"INPUT_SCOPE: CURRENT_SOURCE $selectedHash"})
    exit
}
$local = Join-Path $repo 'local.toml'
if (!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) { throw 'Accept the Minecraft EULA explicitly in local.toml' }
Add-Type -AssemblyName Microsoft.VisualBasic
$instance = Join-Path $validation "instance/$target/server"
New-Item -ItemType Directory -Force -Path $instance | Out-Null
$properties = Join-Path $instance 'server.properties'
$oldProperties = if (Test-Path $properties) { [IO.File]::ReadAllText($properties) } else { $null }
$oldOptions = $env:JAVA_TOOL_OPTIONS
$process = [Diagnostics.Process]::GetCurrentProcess()
$oldPriority = $process.PriorityClass; $oldAffinity = $process.ProcessorAffinity
if ($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($CpuAffinity -band $oldAffinity.ToInt64()) -ne $CpuAffinity) { throw 'Correctness affinity must contain exactly four available logical CPUs' }
$launcher = $null

function Recycle-World([string]$world) {
    $path = [IO.Path]::GetFullPath((Join-Path $instance $world))
    if (!$path.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $world -notmatch '^terrain-input-[a-z0-9_-]+$') { throw 'Unsafe terrain fixture cleanup' }
    if (Test-Path $path) {
        if ((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked validation world' }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    }
}

function Invoke-World([string]$world, [string]$seed, [string[]]$flags, [string]$log, [string]$proof, [string]$status) {
    Recycle-World $world
    [IO.File]::WriteAllText($properties, "level-name=$world`nlevel-seed=$seed`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
    $crashes = @{}
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { $crashes[$_.Name] = (Get-FileHash $_.FullName).Hash }
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 ' + ($flags -join ' ')
    if ($null -eq $launcher) {
        Push-Location $repo
        try {
            [string[]]$networkArgs = if ($Offline) {@('--offline')} else {@()}
            & .\gradlew.bat ":version:${target}:$validationTask" @networkArgs --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> $log
            if ($LASTEXITCODE -ne 0) { Get-Content $log -Tail 40; throw 'Native terrain-input export failed' }
        } finally { Pop-Location }
    } else {
        $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
        $start.WorkingDirectory = $launcher.directory; $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
        foreach ($item in $launcher.environment.PSObject.Properties) {
            if ($item.Name -cne 'MOD_CLASSES') { throw 'Unknown native loader environment key' }
            $start.Environment[$item.Name] = [string]$item.Value
        }
        foreach ($argument in $launcher.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launcher.classpath); $start.ArgumentList.Add($launcher.main)
        foreach ($argument in $launcher.gameArguments) { $start.ArgumentList.Add($argument) }
        $game = [Diagnostics.Process]::Start($start)
        $game.PriorityClass = 'BelowNormal'; $game.ProcessorAffinity = [IntPtr]$CpuAffinity
        $stdout = $game.StandardOutput.ReadToEndAsync(); $stderr = $game.StandardError.ReadToEndAsync()
        if (!$game.WaitForExit(300000)) { $game.Kill($true); $game.WaitForExit(); throw "Native terrain fixture hung $world" }
        [IO.File]::WriteAllText($log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        $code = $game.ExitCode; $game.Dispose()
        if ($code -ne 0) { Get-Content $log -Tail 35; throw "Native terrain fixture failed $world" }
    }
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { if (!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash) { throw "New terrain-input crash $($_.Name)" } }
    if ($proof -and (!(Test-Path $proof) -or (Get-Content $proof -Raw | ConvertFrom-Json).status -cne $status)) { throw "Missing/failed structure evidence $world" }
    Recycle-World $world
}

try {
    $process.PriorityClass = 'BelowNormal'; $process.ProcessorAffinity = [IntPtr]$CpuAffinity
    if (!$Resume -and (Test-Path $output)) {
        $previous = @(Get-ChildItem $output -Filter summary.json -Recurse)
        if ($previous.Count -gt 0 -and @($previous | Where-Object { $null -eq (Get-Content $_.FullName -Raw | ConvertFrom-Json).density }).Count -gt 0) {
            $baseline = Join-Path $validation "result/baseline_before_density/$target/$Suite"
            if (!(Test-Path $baseline)) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $baseline) | Out-Null
                Copy-Item -LiteralPath $output -Destination $baseline -Recurse
            }
        }
        $firstFailure = Join-Path $validation "result/first_failure/$variant/$target"
        $failed = @(Get-ChildItem $output -Filter summary.json -Recurse | Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).status -ceq 'FAIL' })
        if (($failed.Count -gt 0 -or !(Test-Path (Join-Path $output 'fixtures.json'))) -and !(Test-Path $firstFailure)) { New-Item -ItemType Directory -Force -Path (Split-Path -Parent $firstFailure) | Out-Null; Copy-Item -LiteralPath $output -Destination $firstFailure -Recurse }
        foreach ($entry in $failed) {
            $proof = Get-Content $entry.FullName -Raw | ConvertFrom-Json
            $saved = [IO.Path]::GetFullPath($proof.save)
            if (!$saved.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $proof.fixture -notmatch '^[a-z0-9_-]+$' -or $proof.role -notin @('native','lightweight')) { throw 'Unsafe failed-world archive' }
            $backup = Join-Path $instance "first-failure-$($proof.fixture)-$($proof.role)"
            if ((Test-Path $saved) -and !(Test-Path $backup)) {
                if ((Get-Item $saved).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked failure world' }
                Copy-Item -LiteralPath $saved -Destination $backup -Recurse
            }
        }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($output, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    }
    if ($Resume -and (!(Test-Path (Join-Path $output 'fixtures.json')) -or !(Test-Path (Join-Path $output 'launcher.json')))) { throw 'Resume requires the original manifest and native launcher' }
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $hash = Get-SourceFingerprint
    $variantFlags = if ($PhysicalBiomes) { @("-D$($prefix)physicalBiomes=true", "-D$($prefix)variant=$variant") } else { @() }
    if ($EvidenceLabel -and !$PhysicalBiomes -and !$RingScope -and !$NarrowLocks -and !$ReuseSources) { $variantFlags += "-D$($prefix)variant=$variant" }
    if ($RingScope) { $variantFlags += @('-Drandomnibble6plus24generator.structure.lazyRings=true', "-D$($prefix)variant=$variant"); if ($Suite -eq 'smoke') { $variantFlags += "-D$($prefix)ringProof=true" } }
    if ($NarrowLocks) { $variantFlags += "-D$($prefix)variant=$variant" }
    if ($ReuseSources) { $variantFlags += "-D$($prefix)variant=$variant" }
    if (!$Resume) { Invoke-World 'terrain-input-export' '0' (@("-D$($prefix)export=true", "-D$($prefix)suite=$Suite", "-D$($prefix)inputHash=$hash") + $variantFlags) (Join-Path $output 'export.log') '' '' }
    $launcher = Get-Content (Join-Path $output 'launcher.json') -Raw | ConvertFrom-Json
    if ($launcher.main -cne 'net.neoforged.devlaunch.Main' -or @($launcher.gameArguments).Count -ne 1 -or !$launcher.gameArguments[0].StartsWith('@')) {
        Write-Host 'Unknown loader command shape: retain the native Gradle task for each fresh fixture.'
        $launcher = $null
    }
    $fixtures = @(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    $completed = @()
    if ($Resume) {
        foreach ($entry in @(Get-ChildItem -LiteralPath $output -Filter summary.json -Recurse)) {
            $prior = Get-Content -LiteralPath $entry.FullName -Raw | ConvertFrom-Json
            if ($prior.status -ceq 'FAIL' -or $prior.inputHash -cne $hash) { throw 'Resume refuses content failures or another source fingerprint' }
        }
        $bindLogs = @(Get-ChildItem -LiteralPath $output -Filter '*.log' -Recurse | Where-Object { Select-String -LiteralPath $_.FullName -Pattern 'FAILED TO BIND TO PORT|Address already in use' -Quiet })
        if ($bindLogs.Count -eq 0) { throw 'Resume is only approved for a retained port-bind startup failure' }
        $completed = @(Check-Evidence -CompletedOnly -CurrentHash $hash)
        $firstFailure = Join-Path $validation "result/first_failure/$variant/$target"
        if (!(Test-Path -LiteralPath $firstFailure)) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $firstFailure) | Out-Null
            Copy-Item -LiteralPath $output -Destination $firstFailure -Recurse
            $crashDirectory = Join-Path $instance 'crash-reports'
            if (Test-Path -LiteralPath $crashDirectory) { Copy-Item -LiteralPath $crashDirectory -Destination $firstFailure -Recurse }
            foreach ($fixture in $fixtures | Where-Object { $_.id -notin $completed }) {
                foreach ($role in @('native','lightweight')) {
                    $world = Join-Path $instance "terrain-input-$role-$($fixture.id)"
                    if (Test-Path -LiteralPath $world) {
                        if ((Get-Item -LiteralPath $world).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked failed world' }
                        Copy-Item -LiteralPath $world -Destination $firstFailure -Recurse
                    }
                }
            }
            $record = @{status='ENVIRONMENT_FAILURE_NOT_WORLDGEN_PARITY';reason='Native logs prove port bind failed before generation';source=$hash;verifiedPrefix=$completed.Count;logFiles=@($bindLogs.FullName)}
            [IO.File]::WriteAllText((Join-Path $firstFailure 'resume-reason.json'), ($record | ConvertTo-Json -Depth 8))
        }
    }
    $count = $completed.Count
    foreach ($fixture in $fixtures) {
        if ($fixture.id -in $completed) { continue }
        $root = Join-Path $output $fixture.id
        if ($Resume -and (Test-Path -LiteralPath $root)) {
            $resolvedRoot = [IO.Path]::GetFullPath($root)
            if (!$resolvedRoot.StartsWith($output + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $fixture.id -notmatch '^[a-z0-9_-]+$' -or (Get-Item -LiteralPath $resolvedRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Unsafe incomplete fixture cleanup' }
            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolvedRoot, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
        }
        New-Item -ItemType Directory -Force -Path $root | Out-Null
        $flags = @("-D$($prefix)suite=$Suite","-D$($prefix)fixture=$($fixture.id)","-D$($prefix)dimension=$($fixture.dimension)","-D$($prefix)x=$($fixture.x)","-D$($prefix)z=$($fixture.z)","-D$($prefix)local=$($fixture.local)","-D$($prefix)inputHash=$hash") + $variantFlags
        Invoke-World "terrain-input-native-$($fixture.id)" $fixture.local ($flags + "-D$($prefix)role=native") (Join-Path $root 'native.log') (Join-Path $root 'native/summary.json') 'PRODUCED'
        $hostSeed = if ($fixture.local -eq '17') { '23' } else { '17' }
        $consumerFlags = $flags + "-D$($prefix)role=lightweight"
        if ($NarrowLocks) { $consumerFlags += '-Drandomnibble6plus24generator.structure.narrowLocks=true' }
        if ($ReuseSources) { $consumerFlags += '-Drandomnibble6plus24generator.structure.reuseSources=true' }
        Invoke-World "terrain-input-lightweight-$($fixture.id)" $hostSeed $consumerFlags (Join-Path $root 'lightweight.log') (Join-Path $root 'lightweight/summary.json') 'PASS'
        $count++; Write-Host "NATIVE TERRAIN $count/$expectedCount PASS $($fixture.id)"
    }
    if((Get-SourceFingerprint) -cne $hash){throw 'Target source changed while its oracle matrix was running'}
    Check-Evidence -CurrentHash $hash
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldOptions; $process.PriorityClass = $oldPriority; $process.ProcessorAffinity = $oldAffinity
    if ($null -ne $oldProperties) { [IO.File]::WriteAllText($properties, $oldProperties) }
}
