param([switch]$CheckOnly, [switch]$Trees, [switch]$Lightweight, [switch]$Conifers, [switch]$Compare)
$ErrorActionPreference = 'Stop'
if ($Compare -and ($Trees -or $Lightweight -or $Conifers)) { throw 'Compare is an independent suite; do not combine modes.' }
if ($Conifers) { $Trees = [switch]::new($true); $Lightweight = [switch]::new($true) }
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$suite = if ($Compare) { 'comparison' } elseif ($Conifers) { 'conifers' } elseif ($Lightweight) { if ($Trees) { 'lightweight_trees' } else { 'lightweight_ores' } } else { if ($Trees) { 'trees' } else { 'current' } }
$treeOptions = if ($Trees) { '-Dfeaturea.trees=true' } else { '' }
if ($Lightweight) { $treeOptions += ' -Dfeaturea.lightweight=true' }
if ($Conifers) { $treeOptions += ' -Dfeaturea.conifers=true' }
if ($Compare) { $treeOptions += ' -Dfeaturea.compare=true' }
$resultRoot = Join-Path $validationRepository "validations/$validationProject/result/$suite/$validationTarget"
if ($CheckOnly) {
    if ($Compare) {
        $previous = $null
        $totals = @()
        foreach ($variant in @('host_0', 'host_1')) {
            $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
            if ($summary.status -cne 'PASS_COMPARISON_ONLY' -or $summary.proofVersion -ne 1 -or $summary.logicalCpuLimit -ne 4 -or $summary.fixtures.Count -ne 12 -or $summary.aPositiveFixtures -le 0 -or $summary.cOutgoingFixtures -le 0 -or -not $summary.physicalUnchanged -or $summary.bindings -ne 0) { throw 'Incomplete design comparison proof' }
            if ($null -ne $previous -and ($summary.pid -eq $previous.pid -or $summary.hostSeed -eq $previous.hostSeed)) { throw 'Comparison hosts not independent' }
            foreach ($row in $summary.fixtures) {
                if ($row.samples.Count -ne 9 -or $row.fixture.seed -eq $summary.hostSeed) { throw 'Comparison sample or host ownership missing' }
                $pre = @($row.samples.preFeaturesHash | Sort-Object -Unique)
                if ($pre.Count -ne 1) { throw "Pre-FEATURES inputs differ: $($row.fixture.id)" }
                $bHash = ($row.samples | Where-Object mode -CEQ B | Select-Object -First 1).hash
                foreach ($mode in @('A', 'B', 'C')) {
                    $samples = @($row.samples | Where-Object mode -CEQ $mode)
                    if ($samples.Count -ne 3 -or @($samples.hash | Sort-Object -Unique).Count -ne 1 -or @($samples.round | Sort-Object -Unique).Count -ne 3) { throw 'Missing same-policy repeats' }
                    foreach ($sample in $samples) {
                        if ($sample.writers -ne 1 -or $sample.completeTerrainCalls -lt 1 -or $sample.completeNeighborTerrainCalls -ne ($sample.completeTerrainCalls - 1) -or $sample.generationNanos -le 0) { throw 'Missing measured generation boundary' }
                        if ($mode -eq 'A' -and ($sample.baseHash -cne $bHash -or $sample.coverage.status -cne 'PARTIAL' -or $sample.coverage.neighborStructures -cne 'NOT_RECONSTRUCTED')) { throw 'A incomplete support misrepresented' }
                        if (($row.fixture.id -in @('mineshaft', 'end-spike') -and $sample.targetData.entities -le 0) -or ($row.fixture.id -eq 'mineshaft' -and $sample.targetData.blockTicks -le 0) -or ($row.fixture.id -eq 'trial' -and $sample.targetData.blockEntities -le 0) -or ($row.fixture.id -eq 'monument' -and $sample.targetData.fluidTicks -le 0) -or ($row.fixture.id -eq 'village-edge' -and $sample.targetData.postProcessing -le 0)) { throw 'Comparison lost a real target side-effect positive' }
                    }
                    $median = @($samples.generationNanos | Sort-Object)[1] / 1e6
                    $totals += [pscustomobject]@{ Host = $variant; Fixture = $row.fixture.id; Mode = $mode; MedianMs = $median; CompleteNeighbors = $samples[0].completeNeighborTerrainCalls }
                    if ($null -ne $previous) {
                        $old = @($previous.fixtures | Where-Object { $_.fixture.id -ceq $row.fixture.id })
                        if ($old.Count -ne 1 -or ($old[0].samples | Where-Object mode -CEQ $mode | Select-Object -First 1).hash -cne $samples[0].hash) { throw 'Cross-host comparison mismatch' }
                    }
                }
                $reference = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/$($row.fixture.id)/reference.json") -Raw | ConvertFrom-Json
                if ($reference.kind -cne 'RETAINED_ISOLATED_V1_NOT_INDEPENDENT_NATIVE') { throw 'Reference boundary not labelled' }
            }
            $previous = $summary
        }
        $totals | Group-Object Mode | ForEach-Object {
            [pscustomobject]@{ Mode = $_.Name; MeanFixtureMedianMs = [math]::Round(($_.Group.MedianMs | Measure-Object -Average).Average, 2); MeanCompleteNeighbors = [math]::Round(($_.Group.CompleteNeighbors | Measure-Object -Average).Average, 2) }
        } | Format-Table
        'PASS comparison only: 12 fixtures x 3 modes x 3 rotated samples x 2 hosts. Not full A parity, C physical receiving, production throughput or visual acceptance.'
        exit 0
    }
    if ($Trees) {
        $previous = $null
        $previousConiferKernel = $null
        foreach ($variant in @('host_0', 'host_1')) {
            $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
            if ($summary.status -ne 'PASS' -or $summary.fixtures -ne $(if ($Conifers) { 8 } else { 6 }) -or $summary.positives -lt 2 -or $summary.bindings -ne 0 -or $summary.peakTargets -lt 5 -or $summary.kernel.status -ne 'PASS' -or $summary.kernel.outsideHiveOperations -le 0 -or $summary.kernel.maxTargetBlockEntities -le 0 -or $summary.kernel.blockTicks -le 0 -or $summary.kernel.postProcessing -le 0) { throw 'Incomplete tree proof' }
            if ($summary.proofVersion -ne $(if ($Conifers) { 4 } elseif ($Lightweight) { 3 } else { 2 }) -or $summary.independentColumns.status -ne 'PASS' -or $summary.independentColumns.fixtures -ne 90 -or $summary.independentColumns.columns -lt 300 -or $summary.independentColumns.densityColumns -ne $summary.independentColumns.columns -or $summary.independentColumns.fallbacks -le 0 -or $summary.independentColumns.fallbackChunks -le 0) { throw 'Missing independent local-column/fallback proof' }
            $queryWork = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/independent-column-work.json") -Raw | ConvertFrom-Json
            if ($queryWork.heightOnlyQueries -le 0 -or $queryWork.heightCacheHits -le 0 -or $queryWork.rawSamples -le 0) { throw 'Missing height-only and immutable height reuse Oracle coverage' }
            if ($null -ne $previous -and $previous.pid -eq $summary.pid) { throw 'Tree host JVM not independent' }
            $localFullWork = 0
            $fixtures = @('origin', 'nonorigin', 'birch-0', 'birch-1', 'birch-2', 'birch-3')
            if ($Conifers) {
                $fixtures += @('taiga-0', 'taiga-1')
                $kernel = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/conifer-kernel.json") -Raw | ConvertFrom-Json
                if ($kernel.status -ne 'PASS' -or $kernel.cases -ne 120 -or $kernel.hashes.Count -ne 120 -or $kernel.blockTicks -le 0 -or $kernel.postProcessing -le 0) { throw 'Missing conifer kernel' }
                foreach ($tree in @('spruce', 'pine', 'fallen_spruce_tree')) {
                    if ($kernel.successes.$tree -le 0 -or $kernel.targetChanges.$tree -le 0) { throw "No real conifer positive $tree" }
                }
                if ($null -ne $previousConiferKernel -and ($kernel.hashes | ConvertTo-Json -Compress) -cne ($previousConiferKernel.hashes | ConvertTo-Json -Compress)) { throw 'Conifer kernel cross-host mismatch' }
                $previousConiferKernel = $kernel
            }
            foreach ($fixture in $fixtures) {
                $serial = $summary.evidence."serial-$fixture"
                $native = $summary.evidence."native-$fixture"
                if ($fixture -like 'taiga-*' -and ($serial.metrics.supported -cnotcontains 'minecraft:trees_taiga' -or $serial.metrics.state.targetBlockWrites -le 0)) { throw "Missing natural taiga contribution $fixture" }
                if ($serial.hash -cne $native.hash -or $serial.bHash -cne $native.bHash -or $serial.writers -ne 1 -or $native.writers -ne 9 -or $serial.hostSeed -eq $serial.fixture.seed) { throw "Tree native mismatch/ownership $fixture" }
                if ($Lightweight -and ($null -eq $serial.localPreparation -or $serial.baselineFullNeighbors -ne $serial.localPreparation.fullNeighbors -or $serial.hash -cne $summary.evidence."original-$fixture".hash -or $serial.bHash -cne $summary.evidence."original-$fixture".bHash -or ($serial.rng | ConvertTo-Json -Compress) -cne ($summary.evidence."original-$fixture".rng | ConvertTo-Json -Compress))) { throw "Local/original tree preparation mismatch $fixture" }
                if ($Lightweight) {
                    if ($summary.evidence."original-$fixture".baselineFullNeighbors -ne 8) { throw 'Original tree preparation missing' }
                    if ($serial.metrics.state.extraTerrain -gt 0 -and @($serial.metrics.state.localTerrain.fallbacks.PSObject.Properties).Count -eq 0) { throw "Unexplained tree fallback $fixture" }
                    $localFullWork += $serial.baselineFullNeighbors + $serial.oreMetrics.extraTerrain + $serial.metrics.state.extraTerrain
                }
                if ((-not $Lightweight -and $serial.metrics.state.extraTerrain -ne 0) -or ($fixture -like 'birch-*' -and $serial.metrics.state.localTerrain.columns -le 0)) { throw "Local tree proof incomplete $fixture" }
                $nativeRng = @($native.rng | ForEach-Object { ($_ -split ':',2)[1] })
                if (($nativeRng | ConvertTo-Json -Compress) -cne ($serial.rng | ConvertTo-Json -Compress)) { throw "Tree RNG mismatch $fixture" }
                foreach ($order in @('reverse','parallel')) {
                    $other = $summary.evidence."$order-$fixture"
                    if ($other.metrics.state.extraTerrain -ne $serial.metrics.state.extraTerrain -or $serial.hash -cne $other.hash -or ($serial.rng | ConvertTo-Json -Compress) -cne ($other.rng | ConvertTo-Json -Compress)) { throw "Tree order/concurrency mismatch $fixture" }
                    if ($Lightweight -and ($other.baselineFullNeighbors -ne $serial.baselineFullNeighbors -or $other.oreMetrics.extraTerrain -ne $serial.oreMetrics.extraTerrain)) { throw "Tree fallback order dependence $fixture" }
                }
                if ($null -ne $previous -and $serial.hash -cne $previous.evidence."serial-$fixture".hash) { throw "Tree host mismatch $fixture" }
            }
            if ($Lightweight -and $localFullWork -ge (8 * $summary.fixtures)) { throw 'No measured tree preparation reduction' }
            $previous = $summary
        }
        'PASS A tree subset: native dense-vs-sparse equality, RNG, two hosts, strict cut and concurrency.'
        exit 0
    }
    $previous = $null
    foreach ($variant in @('host_0', 'host_1')) {
        $summary = Get-Content -LiteralPath (Join-Path $resultRoot "$variant/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS' -or $summary.fixtures -ne 10 -or $summary.bindings -ne 0 -or $summary.peakTargets -lt 5 -or $summary.proofVersion -ne $(if ($Lightweight) { 4 } else { 3 })) { throw 'Incomplete A proof' }
        if ($summary.evidence.'independent-height'.status -ne 'PASS' -or $summary.evidence.'independent-height'.fixtures -ne 90 -or $summary.evidence.'independent-height'.columns -ne 23040) { throw 'Independent height matrix incomplete' }
        foreach ($fixture in @('origin', 'nonorigin')) {
            if ($summary.evidence."reference-$fixture".writers -ne 9) { throw 'A/full reference distinction missing' }
        }
        if ($null -ne $previous -and $previous.pid -eq $summary.pid) { throw 'A host JVM not independent' }
        $positives = 0
        $localFullWork = 0
        foreach ($fixture in @('origin', 'origin-alt', 'nonorigin', 'village-edge', 'mineshaft', 'trial', 'monument', 'nether', 'fortress', 'end-spike')) {
            $serial = $summary.evidence."serial-$fixture"
            $oracle = $summary.evidence."oracle-$fixture"
            if ($serial.writerCount -ne 1 -or $oracle.writerCount -ne 9 -or $serial.hostSeed -eq $serial.seed) { throw 'A ownership/independence proof missing' }
            if ($serial.metrics.sourceChunks -ne 8 -or $null -eq $serial.metrics.unsupportedFeatures) { throw 'A unsupported matrix missing' }
            if ($serial.hash -cne $oracle.hash -or $serial.bHash -cne $oracle.bHash) { throw "A native mismatch $fixture" }
            if ($Lightweight -and ($null -eq $serial.localPreparation -or $serial.baselineFullNeighbors -ne $serial.localPreparation.fullNeighbors -or $serial.hash -cne $summary.evidence."original-$fixture".hash -or $serial.bHash -cne $summary.evidence."original-$fixture".bHash -or ($serial.metrics.trace | ConvertTo-Json -Compress) -cne ($summary.evidence."original-$fixture".metrics.trace | ConvertTo-Json -Compress))) { throw "Local/original ore preparation mismatch $fixture" }
            if ($Lightweight) {
                if ($summary.evidence."original-$fixture".baselineFullNeighbors -ne 8 -or $oracle.heightProof.localHeightColumns -ne $serial.metrics.localHeightQuery.columns) { throw "Missing original preparation/local-height proof $fixture" }
                if ($serial.metrics.extraTerrain -gt 0 -and @($serial.metrics.localHeightQuery.fallbacks.PSObject.Properties).Count -eq 0) { throw "Unexplained ore height fallback $fixture" }
                $localFullWork += $serial.baselineFullNeighbors + $serial.metrics.extraTerrain
            }
            if ($serial.hash -cne $summary.evidence."eager-$fixture".hash) { throw "A exact/light height mismatch $fixture" }
            if ($oracle.heightProof.status -ne 'PASS' -or $oracle.heightProof.columns -ne $serial.metrics.heightQuery.columns) { throw "A height proof incomplete $fixture" }
            $trace = @($serial.metrics.trace | ForEach-Object { $parts = $_ -split ':'; $parts[0] + ':' + ($parts[3..($parts.Count - 1)] -join ':') })
            if (($trace | ConvertTo-Json -Compress) -cne ($oracle.nativeTrace | ConvertTo-Json -Compress)) { throw "A native feature selection/order mismatch $fixture" }
            if ($serial.changed) { $positives++ }
            foreach ($order in @('shuffled', 'parallel')) {
                $other = $summary.evidence."$order-$fixture"
                if ($serial.hash -cne $other.hash -or ($serial.metrics.trace | ConvertTo-Json -Compress) -cne ($other.metrics.trace | ConvertTo-Json -Compress)) { throw "A order/concurrency mismatch $fixture $order" }
            }
            if ($null -ne $previous -and $serial.hash -cne $previous.evidence."serial-$fixture".hash) { throw "A independent host mismatch $fixture" }
        }
        if ($Lightweight -and $localFullWork -ge (8 * $summary.fixtures)) { throw 'No measured ore preparation reduction' }
        if ($positives -lt 4) { throw 'A real contribution positives insufficient' }
        $previous = $summary
    }
    'PASS A ore subset: native algorithm equality, B contribution positives, two independent hosts, order/concurrency and strict isolation.'
    exit 0
}
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$originalAffinity = $orchestrator.ProcessorAffinity
$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$world = Join-Path $validationServer 'feature-a-validation'
try {
    $orchestrator.PriorityClass = 'BelowNormal'
    $orchestrator.ProcessorAffinity = 3840
    if (Test-Path -LiteralPath $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeaturea.export=true $treeOptions"
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureAServer" --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' '--args=nogui --world feature-a-validation' *> (Join-Path $resultRoot 'export.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $resultRoot 'export.log') -Tail 60; throw 'A launcher export failed' }
    $launch = Get-Content -LiteralPath (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    Move-ValidationToRecycleBin -LiteralPath $world
    foreach ($hostVariant in 0..1) {
        $hostSeed = if ($hostVariant -eq 0) { '17' } else { '23' }
        [IO.File]::WriteAllText($validationProperties, [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$hostSeed"), [Text.UTF8Encoding]::new($false))
        $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeaturea.variant=host_$hostVariant $treeOptions"
        $start = [Diagnostics.ProcessStartInfo]::new($launch.java)
        $start.WorkingDirectory = $launch.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in $launch.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launch.classpath); $start.ArgumentList.Add($launch.main)
        foreach ($argument in $launch.gameArguments) { $start.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
        $process.ProcessorAffinity = 3840
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        [IO.File]::WriteAllText((Join-Path $resultRoot "host_$hostVariant.log"), $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        if ($process.ExitCode -ne 0) { Get-Content (Join-Path $resultRoot "host_$hostVariant.log") -Tail 85; throw 'A runtime failed' }
        if (-not (Test-Path -LiteralPath (Join-Path $resultRoot "host_$hostVariant/summary.json"))) { Get-Content (Join-Path $resultRoot "host_$hostVariant.log") -Tail 85; throw 'A runtime produced no success evidence (server failures may exit with code 0)' }
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
& $PSCommandPath -CheckOnly -Trees:$Trees -Lightweight:$Lightweight -Conifers:$Conifers -Compare:$Compare
