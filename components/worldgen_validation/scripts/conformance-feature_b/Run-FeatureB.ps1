param([switch]$CheckOnly, [switch]$Lightweight, [switch]$Preparation, [switch]$NativeCat, [switch]$StructureReuse)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
if (([int]$Lightweight.IsPresent + [int]$Preparation.IsPresent + [int]$NativeCat.IsPresent + [int]$StructureReuse.IsPresent) -gt 1) { throw 'Choose one B suite' }
$mode = if ($NativeCat) { 'native_cat' } elseif ($Preparation) { 'preparation' } elseif ($StructureReuse) { 'structure_reuse' } elseif ($Lightweight) { 'lightweight' } else { 'current' }
$resultRoot = Join-Path $validationRepository "validations/$validationProject/result/$mode/$validationTarget"
if ($CheckOnly -and $NativeCat) {
    $pids = @(); $paths = @()
    foreach ($i in 0..2) {
        $s = Get-Content (Join-Path $resultRoot "host_$i/summary.json") -Raw | ConvertFrom-Json
        if ($s.status -cne 'CAPTURED_NATIVE_ONLY' -or $s.entities -le 0 -or $s.experimentalGenerations -ne 0 -or $s.bindings -ne 0 -or $s.generator -cne 'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator' -or $s.fixture.seed -ne 6493561816561378690 -or $s.fixture.x -ne 0 -or $s.fixture.z -ne 2) { throw 'Missing independent native cat evidence' }
        $pids += $s.pid; $paths += $s.worldPath
        if ($i -gt 0 -and $s.comparison.attributeValuesByIdDiff.Count -ne 0) { throw 'Native cat attribute values changed; do not broaden acceptance' }
    }
    if (($pids | Select-Object -Unique).Count -ne 3 -or ($paths | Select-Object -Unique).Count -ne 3) { throw 'Cat worlds not independent' }
    'CAPTURED native cat repeats; full hashes/raw lists retained; attribute values exact by id. Not a strict hash PASS.'
    exit 0
}
if ($CheckOnly -and $Preparation) {
    $a = Get-Content (Join-Path $resultRoot 'host_0/summary.json') -Raw | ConvertFrom-Json
    $b = Get-Content (Join-Path $resultRoot 'host_1/summary.json') -Raw | ConvertFrom-Json
    foreach ($s in @($a,$b)) {
        if ($s.status -cne 'PASS_B_DETACHED_HANDOFF' -or $s.fixtures.Count -ne 19 -or $s.bindings -ne 0 -or $s.productionSwitch) { throw 'Incomplete detached B proof' }
        foreach ($f in $s.fixtures) { if ($f.spawnCells -le 0 -or !$f.artifactRawEntitiesEqual -or $f.trace.requestedWriters.Count -ne 1 -or $f.trace.maxConcurrentFeatureWriters -ne 1) { throw 'Missing actual handoff data' } }
    }
    if ($a.pid -eq $b.pid -or $a.hostSeed -eq $b.hostSeed) { throw 'B preparation hosts not independent' }
    for ($i = 0; $i -lt $a.fixtures.Count; $i++) {
        foreach ($field in @('fixture','spawnHash','spawnCells','structures','fullNeighbors')) {
            if (($a.fixtures[$i].$field | ConvertTo-Json -Depth 20 -Compress) -cne ($b.fixtures[$i].$field | ConvertTo-Json -Depth 20 -Compress)) { throw "B handoff cross-host differs: $field" }
        }
        $cross = Get-Content (Join-Path $resultRoot "host_1/$($a.fixtures[$i].fixture.id)/cross-host.json") -Raw | ConvertFrom-Json
        if (!$cross.accepted -or $null -ne $cross.nonEntityDifference -or $cross.entities.unexpectedDifferences.Count -ne 0) { throw 'Unexpected complete B cross-host difference' }
        foreach ($hostName in @('host_0','host_1')) {
            $comparison = Get-Content (Join-Path $resultRoot "$hostName/$($a.fixtures[$i].fixture.id)/comparison.json") -Raw | ConvertFrom-Json
            if (!$comparison.accepted) { throw 'Original B changed' }
            $reuse = Get-Content (Join-Path $resultRoot "$hostName/$($a.fixtures[$i].fixture.id)/structure-reuse.json") -Raw | ConvertFrom-Json
            if (!$reuse.closed -or $reuse.resident -ne 0 -or $reuse.peakResident -gt 361 -or $reuse.queries -ne ($reuse.hits + $reuse.evaluations)) { throw 'Detached B structure scope leaked' }
        }
    }
    'PASS B detached handoff: full target/Artifact raw data, stored SPAWN palettes, referenced starts and actual writer trace; two independent hosts. Not client acceptance.'
    exit 0
}
if ($CheckOnly -and ($Lightweight -or $StructureReuse)) {
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS' -or $summary.fixtures -ne 18 -or $summary.repeatComparisons -ne 24 -or $summary.bindings -ne 0 -or $summary.peakTargets -lt 5 -or $summary.proofVersion -ne 1 -or -not $summary.exceptionCleanup) { throw 'Incomplete local B proof' }
        if ($StructureReuse) {
            $locks = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/structure-locks.json") -Raw | ConvertFrom-Json
            if ($locks.status -cne 'PASS' -or $locks.structures -ne 7 -or $locks.parallelComparisons -ne 21 -or $locks.threads -ne 5 -or !$locks.nativeAlgorithm) { throw 'Missing cold-cache structure lock regression' }
        }
        if ($null -ne $previous -and $previous.pid -eq $summary.pid) { throw 'Local B JVM evidence not independent' }
        $saved = 0
        foreach ($entry in $summary.evidence.PSObject.Properties) {
            $serial = $entry.Value
            if ($StructureReuse -and (!$serial.structureReuse.closed -or $serial.structureReuse.resident -ne 0 -or $serial.structureReuse.peakResident -gt 361 -or $serial.structureReuse.queries -ne ($serial.structureReuse.hits + $serial.structureReuse.evaluations))) { throw 'Structure source scope ownership/lifecycle invalid' }
            if ($serial.hostSeed -eq $serial.seed -or $serial.fullNeighbors -gt 8) { throw 'Local B ownership proof missing' }
            if ($entry.Name -notmatch 'end-city$' -and $serial.hash -cne $serial.originalHash) { throw "Local B differs from original: $($entry.Name)" }
            $saved += 8 - $serial.fullNeighbors
            if ($null -ne $previous) {
                $other = $previous.evidence.($entry.Name)
                $fields = @('featureSeedCalls', 'featureSeedSequence', 'visibleBiomes', 'rejected', 'structureTags')
                $fields += if ($entry.Name -match 'end-city$') { 'nonEntityHash' } else { 'hash' }
                foreach ($field in $fields) {
                    # Diagnostic counters are maps; Java's immutable-map iteration is JVM-specific.
                    if ($field -eq 'rejected') {
                        $names = @($serial.rejected.PSObject.Properties.Name | Sort-Object)
                        if (($names -join ',') -cne (($other.rejected.PSObject.Properties.Name | Sort-Object) -join ',')) { throw "Local B rejection schema differs: $($entry.Name)" }
                        foreach ($name in $names) { if ($serial.rejected.$name -cne $other.rejected.$name) { throw "Local B rejection count differs: $($entry.Name) $name" } }
                        continue
                    }
                    if (($serial.$field | ConvertTo-Json -Depth 32 -Compress) -cne ($other.$field | ConvertTo-Json -Depth 32 -Compress)) { throw "Local B host differs: $($entry.Name) $field" }
                }
            }
        }
        if ($saved -le 0) { throw 'Local B did not remove any full neighbors' }
        $previous = $summary
    }
    Write-Host 'Lightweight B original/read/order/concurrency parity PASS'
    return
}
if ($CheckOnly) {
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS' -or $summary.fixtures -lt 15 -or $summary.repeatComparisons -ne 30 -or $summary.bindings -ne 0 -or $summary.peakTargets -lt 5 -or $summary.proofVersion -ne 3 -or -not $summary.exceptionCleanup) { throw 'Incomplete B proof' }
        if ($null -ne $previous -and $previous.pid -eq $summary.pid) { throw 'JVM evidence not independent' }
        foreach ($fixture in @('origin', 'origin-alt', 'nonorigin', 'village', 'village-edge', 'mineshaft', 'mineshaft-edge', 'trial', 'nether', 'end-spike')) {
            $serial = $summary.evidence."serial-$fixture"
            if ($serial.hostSeed -eq $serial.seed) { throw 'Host seed not independent' }
            if ([string]::IsNullOrEmpty($serial.structureProjectionHash) -or $serial.writerCount -ne 1 -or $serial.terrainNeighbors -ne 8) { throw 'Missing complete B structure/ownership evidence' }
            $orders = @('reverse', 'shuffled', 'parallel')
            if ($fixture -eq 'nonorigin') { $orders += @('generated-host', 'mutated-host') }
            foreach ($order in $orders) {
                $other = $summary.evidence."$order-$fixture"
                foreach ($field in @('hash', 'structureProjectionHash', 'featureSeedCalls', 'featureSeedSequence', 'visibleBiomes')) {
                    if (($serial.$field | ConvertTo-Json -Depth 16 -Compress) -cne ($other.$field | ConvertTo-Json -Depth 16 -Compress)) { throw "B trace differs: $fixture $order $field" }
                }
            }
            if ($null -ne $previous) {
                $other = $previous.evidence."serial-$fixture"
                foreach ($field in @('hash', 'structureProjectionHash', 'featureSeedCalls', 'featureSeedSequence', 'visibleBiomes')) {
                    if (($serial.$field | ConvertTo-Json -Depth 16 -Compress) -cne ($other.$field | ConvertTo-Json -Depth 16 -Compress)) { throw "B independent JVM/host differs: $fixture $field" }
                }
            }
        }
        $previous = $summary
    }
    "PASS B: 2 independent hosts, $($summary.fixtures) real fixtures each, reverse/parallel equality, neighbor invariance and physical isolation."
    exit 0
}
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$originalAffinity = $orchestrator.ProcessorAffinity
$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$world = Join-Path $validationServer 'feature-b-validation'
if ($Preparation) {
    & $PSCommandPath -NativeCat -CheckOnly
    & (Join-Path $PSScriptRoot '../conformance-feature_c/Run-FeatureC.ps1') -NativeRepeat -CheckOnly
    & (Join-Path $PSScriptRoot '../conformance-feature_c/Run-FeatureC.ps1') -NativeShulker -CheckOnly
}
try {
    $orchestrator.PriorityClass = 'BelowNormal'
    $orchestrator.ProcessorAffinity = 3840
    if (Test-Path -LiteralPath $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    $localFlag = if ($NativeCat) { ' -Dfeatureb.nativeCat=true' } elseif ($Preparation) { ' -Dfeatureb.preparation=true' } elseif ($StructureReuse) { ' -Dfeatureb.structureReuse=true' } elseif ($Lightweight) { ' -Dfeatureb.lightweight=true' } else { '' }
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 -Dfeatureb.export=true' + $localFlag
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureBServer" --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' '--args=nogui --world feature-b-validation' *> (Join-Path $resultRoot 'export.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $resultRoot 'export.log') -Tail 50; throw 'B launcher export failed' }
    $launch = Get-Content -LiteralPath (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    Move-ValidationToRecycleBin -LiteralPath $world
    $variants = if ($NativeCat) { 0..2 } else { 0..1 }
    foreach ($hostVariant in $variants) {
        if ($NativeCat) {
            $world = Join-Path $validationServer "feature-b-native-cat-$hostVariant"
            if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
        }
        $hostSeed = if ($NativeCat) { [string]$launch.nativeFixture.seed } elseif ($hostVariant -eq 0) { '17' } else { '23' }
        [IO.File]::WriteAllText($validationProperties, [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$hostSeed"), [Text.UTF8Encoding]::new($false))
        $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeatureb.variant=host_$hostVariant" + $localFlag
        $start = [Diagnostics.ProcessStartInfo]::new($launch.java)
        $start.WorkingDirectory = $launch.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in $launch.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launch.classpath); $start.ArgumentList.Add($launch.main)
        foreach ($argument in $launch.gameArguments) {
            $start.ArgumentList.Add($(if ($NativeCat -and $argument -ceq 'feature-b-validation') { "feature-b-native-cat-$hostVariant" } else { $argument }))
        }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
        $process.ProcessorAffinity = 3840
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        [IO.File]::WriteAllText((Join-Path $resultRoot "host_$hostVariant.log"), $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath (Join-Path $resultRoot "host_$hostVariant/summary.json"))) { Get-Content (Join-Path $resultRoot "host_$hostVariant.log") -Tail 75; throw 'B runtime failed or produced no summary' }
        $process.Dispose()
        Move-ValidationToRecycleBin -LiteralPath $world
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
    $orchestrator.PriorityClass = $originalPriority
    $orchestrator.ProcessorAffinity = $originalAffinity
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
}
& $PSCommandPath -CheckOnly -Lightweight:$Lightweight -Preparation:$Preparation -NativeCat:$NativeCat -StructureReuse:$StructureReuse
