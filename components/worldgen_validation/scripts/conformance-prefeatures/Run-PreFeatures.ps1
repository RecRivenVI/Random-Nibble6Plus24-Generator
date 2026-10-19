param(
    [ValidateSet('26.3-fabric', '26.3-neoforge', '26.1.2-neoforge')][string]$Target = '26.3-fabric',
    [ValidateSet('matrix', 'smoke', 'structures')][string]$Suite = 'matrix',
    [switch]$Lightweight,
    [switch]$StructureReuse,
    [switch]$Compact,
    [switch]$Bop,
    [switch]$BiomeOnly,
    [switch]$Offline,
    [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = '',
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash = '',
    [string]$BootstrapLauncher = '',
    [long]$CpuAffinity = 3840,
    [long]$HostSeed = 17,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
if ($ExpectedInputHash -and !$CheckOnly) { throw 'Explicit historical source identity is read-only' }
if ($EvidenceLabel -and !($Target -in @('26.3-fabric','26.3-neoforge') -and $Lightweight -and !$Bop)) { throw 'Bounded source-state evidence labels require audited modern Vanilla lightweight inputs' }
if ($BootstrapLauncher -and (!$EvidenceLabel -or $CheckOnly)) { throw 'Owned launcher bootstrap requires a new scoped Vanilla lightweight run' }
$env:MOSAIC_VALIDATION_TARGET = $Target
if ($Bop -and $Target -ne '26.3-fabric') { throw 'BOP validation is not yet adapted to this target' }
if ($BiomeOnly -and ($Target -ne '26.3-fabric' -or $Lightweight -or $StructureReuse -or $Compact -or $Bop)) { throw 'Detached BIOMES is currently a separate Vanilla 26.3 Fabric candidate' }
if ($Target -eq '26.1.2-neoforge' -and $StructureReuse) { throw '26.1.2 structure-source reuse is intrinsic to its lightweight consumer; no separate reuse comparison is implemented yet' }
if ($Compact -and ($Target -ne '26.1.2-neoforge' -or $Suite -ne 'structures')) { throw 'Explicit compact input validation requires the 26.1.2 structure suite' }
if ($Bop) { $validationProjectOverride = 'compatibility-bop' }
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$repoRoot = $validationRepository
if ($StructureReuse -and !$Lightweight) { throw 'Structure reuse is a lightweight consumer check' }
$resultVariant = if ($BiomeOnly) { "biome_only_$Suite" } elseif ($Bop) { "bop_$($Suite)_$($Lightweight)_host$HostSeed" } elseif ($StructureReuse) { "structure_reuse_$Suite" } elseif ($Lightweight) { "terrain_$Suite" } elseif ($Compact) { 'compact' } elseif ($Suite -eq 'structures' -and $Target -eq '26.1.2-neoforge') { 'native_structures' } elseif ($Suite -eq 'structures') { 'compact' } else { 'current' }
if ($EvidenceLabel) { $resultVariant += '_' + $EvidenceLabel }
$worldSuffix = if ($EvidenceLabel) { '-' + $resultVariant } else { '' }
$resultRoot = Join-Path $repoRoot "validations/$validationProject/result/$resultVariant/$validationTarget"
$selectionPath = Join-Path $resultRoot 'selection.json'
function Get-InputHash {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "versions/$validationTarget/src/main"), (Join-Path $repoRoot "versions/$validationTarget/src/probe"), (Join-Path $repoRoot 'components/worldgen_contract/src') -File -Recurse | Sort-Object FullName)
    $text = ($files | ForEach-Object { $_.FullName.Substring($repoRoot.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
    $text += [IO.File]::ReadAllText((Join-Path $repoRoot "versions/$validationTarget/target.properties"))
    if ($Bop) { $text += [IO.File]::ReadAllText((Join-Path $repoRoot 'inputs.toml')) }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
}
$inputHash = if ($ExpectedInputHash) { $ExpectedInputHash.ToUpperInvariant() } else { Get-InputHash }
$stageNames = if ($BiomeOnly) { @('structure_starts','structure_references','biomes') } elseif ($Target -eq '26.1.2-neoforge') { @('structure_starts','structure_references','biomes','noise','surface','carvers') } else { @('structure_starts','structure_references','biomes','terrain') }

if ($CheckOnly) {
    $selection = Get-Content -LiteralPath $selectionPath -Raw | ConvertFrom-Json
    if ($selection.suite -ne $Suite -or [bool]$selection.lightweight -ne [bool]$Lightweight -or [bool]$selection.structureReuse -ne [bool]$StructureReuse -or [bool]$selection.compact -ne [bool]$Compact -or [bool]$selection.biomeOnly -ne [bool]$BiomeOnly) { throw 'Selection does not match the requested suite' }
    if ($selection.ownedNativePort) {
        $nativeConfig = Get-Content (Join-Path $resultRoot 'native-launch-config.json') -Raw | ConvertFrom-Json
        $ownedConfig = Join-Path $validationServer $(if ($Target -eq '26.3-fabric') { "native-dli-$EvidenceLabel.cfg" } else { "native-args-$EvidenceLabel.txt" })
        if ($nativeConfig.owned -cne $ownedConfig -or $nativeConfig.requestedPort -ne 0 -or
                (Get-FileHash $ownedConfig).Hash -cne $nativeConfig.ownedSha256) { throw 'Owned pre-FEATURES native launch configuration changed' }
    }
    $passed = 0
    $surfacePositive = 0
    $carverPositive = 0
    foreach ($fixture in $selection.fixtures) {
        $fixtureRoot = Join-Path $resultRoot $fixture.id
        $native = Get-Content -LiteralPath (Join-Path $fixtureRoot 'native/summary.json') -Raw | ConvertFrom-Json
        $role = if ($BiomeOnly) { 'biome-only' } elseif ($Lightweight) { 'lightweight' } elseif ($Compact -or ($selection.suite -eq 'structures' -and $Target -ne '26.1.2-neoforge')) { 'compact' } else { 'reference' }
        $reference = Get-Content -LiteralPath (Join-Path $fixtureRoot "$role/summary.json") -Raw | ConvertFrom-Json
        if ($selection.ownedNativePort) {
            foreach ($evidenceRole in @('native', $role)) {
                $proof = if ($evidenceRole -ceq 'native') { $native } else { $reference }
                $execution = Get-Content (Join-Path $fixtureRoot "$evidenceRole.log.execution.json") -Raw | ConvertFrom-Json
                $expectedWorld = "prefeatures-$evidenceRole-$($fixture.id)$worldSuffix"
                if ($execution.pid -ne $proof.pid -or $execution.affinity -ne $selection.cpuAffinity -or
                        $execution.priority -cne 'BelowNormal' -or $execution.requestedPort -ne 0 -or
                        $execution.nativeConfig -cne $nativeConfig.owned -or $execution.nativeConfigSha256 -cne $nativeConfig.ownedSha256 -or
                        $execution.world -cne $expectedWorld -or
                        [IO.Path]::GetFullPath($proof.save).TrimEnd([IO.Path]::DirectorySeparatorChar) -cne
                        [IO.Path]::GetFullPath((Join-Path $validationServer $expectedWorld)).TrimEnd([IO.Path]::DirectorySeparatorChar)) { throw 'Independent pre-FEATURES ownership/resources differ' }
                $nativeLog = [IO.File]::ReadAllText((Join-Path $fixtureRoot "$evidenceRole.log"))
                if ($nativeLog -notmatch 'Starting Minecraft server on [^\r\n]+:0[\r\n]' -or $nativeLog.Contains('FAILED TO BIND TO PORT')) { throw 'Pre-FEATURES server did not use its isolated ephemeral port' }
            }
        }
        if ($native.environment.inputSha256 -cne $inputHash -or $reference.environment.inputSha256 -cne $inputHash) { throw "Evidence belongs to another source input: $($fixture.id)" }
        if ($native.status -ne 'PRODUCED' -or $reference.status -ne 'PASS' -or
                $native.featuresExecuted -ne 0 -or $reference.featuresExecuted -ne 0 -or
                $native.checkpoints.Count -ne $stageNames.Count -or $reference.checkpoints.Count -ne $stageNames.Count -or
                $native.pid -eq $reference.pid -or $native.save -eq $reference.save -or
                $native.hostSeed -ne $native.localSeed -or $reference.hostSeed -eq $reference.localSeed) {
            throw "Invalid pre-FEATURES proof: $($fixture.id)"
        }
        foreach ($field in @('fixture', 'masterSeed', 'localSeed', 'dimension', 'chunkX', 'chunkZ', 'environment', 'checkpoints')) {
            if (($native.$field | ConvertTo-Json -Depth 80 -Compress) -cne
                    ($reference.$field | ConvertTo-Json -Depth 80 -Compress)) {
                throw "Evidence differs: $($fixture.id) $field"
            }
        }
        if ($Bop) {
            foreach ($mod in @('biomesoplenty', 'terrablender', 'glitchcore', 'fabric-api')) {
                if (!$native.environment.mods.$mod) { throw "Missing loaded BOP dependency $mod" }
            }
            if (!$reference.bopContext.hostUnchanged -or !$reference.bopContext.concurrentSeedIsolation -or
                    ($native.bopContext.seedGrid | ConvertTo-Json -Compress) -cne ($reference.bopContext.seedGrid | ConvertTo-Json -Compress)) { throw 'BOP seed-state proof failed' }
            if ((Get-FileHash (Join-Path $fixtureRoot 'native/bop-palette.json')).Hash -ne
                    (Get-FileHash (Join-Path $fixtureRoot "$role/bop-palette.json")).Hash) { throw 'BOP stored palette count differs' }
        }
        if ($native.fixture -ne $fixture.id -or $native.masterSeed -ne $fixture.master -or
                $native.localSeed -ne $fixture.local -or $native.dimension -ne $fixture.dimension -or
                $native.chunkX -ne $fixture.x -or $native.chunkZ -ne $fixture.z) {
            throw "Fixture manifest differs from the selection: $($fixture.id)"
        }
        foreach ($stage in $stageNames) {
            $files = @("$stage-structures.json")
            if ($stage -in @('biomes', 'terrain', 'noise', 'surface', 'carvers')) { $files += "$stage.bin.gz" }
            foreach ($file in $files) {
                $left = Get-FileHash -LiteralPath (Join-Path $fixtureRoot "native/$file") -Algorithm SHA256
                $right = Get-FileHash -LiteralPath (Join-Path $fixtureRoot "$role/$file") -Algorithm SHA256
                if ($left.Hash -cne $right.Hash) { throw "Evidence file differs: $($fixture.id) $file" }
            }
        }
        if ($Target -eq '26.1.2-neoforge') {
            foreach ($file in @('beardifier.bin', 'beardifier.json', 'biome-frontier.bin.gz')) {
                if ((Get-FileHash (Join-Path $fixtureRoot "native/$file")).Hash -cne
                        (Get-FileHash (Join-Path $fixtureRoot "$role/$file")).Hash) { throw "Native separated inputs differ: $($fixture.id) $file" }
            }
        }
        if ($BiomeOnly) {
            if (!$reference.detachedAfterClose -or !$reference.serializationAfterClose -or $reference.virtualChunks -ne 0 -or $reference.detachedBiomes.protoChunks -ne 1) { throw 'Detached native BIOMES retains a session/noise graph or lacks a post-close check' }
            if ((Get-FileHash (Join-Path $fixtureRoot 'native/biomes-native.nbt.gz')).Hash -cne (Get-FileHash (Join-Path $fixtureRoot "$role/biomes-native.nbt.gz")).Hash) { throw 'Detached native BIOMES full NBT differs' }
        } elseif ($Lightweight -and $Target -eq '26.1.2-neoforge') {
            if ($reference.virtualChunks -ne 0 -or $reference.lightweightTerrain.protoChunks -ne 1 -or
                    $reference.lightweightTerrain.biomeCarriers -ne 8 -or $reference.lightweightTerrain.carverSources -ne 289 -or
                    $reference.lightweightTerrain.generationHolders -ne 0) { throw 'Separated native lightweight input proof missing' }
            $noise = @($reference.checkpoints | Where-Object stage -EQ 'noise')[0]
            $surface = @($reference.checkpoints | Where-Object stage -EQ 'surface')[0]
            $carvers = @($reference.checkpoints | Where-Object stage -EQ 'carvers')[0]
            if ($noise.snapshotSha256 -cne $surface.snapshotSha256) { $surfacePositive++ }
            if ($surface.snapshotSha256 -cne $carvers.snapshotSha256) { $carverPositive++ }
        } elseif ($Lightweight) {
            if ($StructureReuse) {
                $reuse = $reference.structureReuse
                $stateAware = $reuse.PSObject.Properties.Name -contains 'stateMismatches'
                if (!$reuse.closed -or $reuse.resident -ne 0 -or $reuse.peakResident -gt 361 -or (!$stateAware -and $reuse.hits -lt 289)) { throw 'Native structure reuse proof missing' }
                if ($stateAware) {
                    $expectedReplays = if ($reuse.climateState) { $reuse.hits } else { 0 }
                    if ($reuse.uniqueSources -ne 361 -or $reuse.queries -ne 2890 -or $reuse.transferred -ne 0 -or
                            $reuse.evaluations -ne 361+$reuse.stateMismatches -or $reuse.queries -ne $reuse.hits+$reuse.evaluations -or
                            $reuse.stateReplays -ne $expectedReplays -or $reuse.climateState -ne ($fixture.dimension -cne 'minecraft:the_end')) { throw 'Native climate entry/exit accounting incomplete' }
                }
            }
            if ($StructureReuse -and (!$reference.biomeReuse.closed -or $reference.biomeReuse.resident -ne 0 -or $reference.biomeReuse.nativeBuilds -ne 9 -or $reference.biomeReuse.targetCopies -ne 1 -or $reference.biomeReuse.preparationHits -ne 1)) { throw 'Native stored-biome copy/preparation proof missing' }
            foreach ($file in @('density.bin', 'carving-mask.bin', 'biome-frontier.json', 'carver-sources.json', 'internals.json', 'noise.bin.gz', 'surface.bin.gz', 'carved.bin.gz')) {
                if ((Get-FileHash -LiteralPath (Join-Path $fixtureRoot "native/$file")).Hash -cne
                        (Get-FileHash -LiteralPath (Join-Path $fixtureRoot "$role/$file")).Hash) { throw "Terrain internals differ: $($fixture.id) $file" }
            }
            if ($reference.virtualChunks -ne 0 -or $reference.lightweightTerrain.protoChunks -ne 1 -or $reference.lightweightTerrain.biomeCarriers -ne 8) { throw 'Lightweight path recreated the chunk graph' }
            $internals = Get-Content -LiteralPath (Join-Path $fixtureRoot "$role/internals.json") -Raw | ConvertFrom-Json
            if ($internals.surfaceChangedBlocks -gt 0) { $surfacePositive++ }
            if ($internals.carverChangedBlocks -gt 0) { $carverPositive++ }
            if ($internals.carverSourceQueries -ne 289 -or $internals.biomePalettes -ne 9 -or $internals.densitySamples -le 0) { throw 'Incomplete native internal observations' }
        }
        if ($selection.suite -eq 'structures') {
            if($Target.StartsWith('26.3-')){
                foreach($evidenceRole in @('native',$role)){
                    & python (Join-Path $PSScriptRoot 'check_beardifier_coverage.py') (Join-Path $fixtureRoot $evidenceRole)
                    if($LASTEXITCODE -ne 0){throw "Incomplete native Beardifier height coverage: $($fixture.id) $evidenceRole"}
                }
            }
            foreach ($file in @('beardifier.bin', 'beardifier.json', 'projection.json')) {
                if ((Get-FileHash -LiteralPath (Join-Path $fixtureRoot "native/$file")).Hash -cne
                        (Get-FileHash -LiteralPath (Join-Path $fixtureRoot "$role/$file")).Hash) { throw "Density differs: $($fixture.id)" }
            }
            if (($Compact -or $Target -ne '26.1.2-neoforge') -and ($reference.compactInputs.referenceRadius -ne 8 -or $reference.compactInputs.evaluatedSources -ne 289)) { throw 'Invalid compact frontier' }
            if ($fixture.structure) {
                $inputs = Get-Content -LiteralPath (Join-Path $fixtureRoot 'native/structure_references-structures.json') -Raw | ConvertFrom-Json -AsHashtable
                $key = "minecraft:$($fixture.structure)@[$($fixture.startX), $($fixture.startZ)]"
                if (-not $inputs.ContainsKey($key) -or $inputs[$key].pieces -le 0) { throw "Missing positive native start $key" }
            }
            if ($fixture.terrainOnly) {
                $beard = Get-Content -LiteralPath (Join-Path $fixtureRoot 'native/beardifier.json') -Raw | ConvertFrom-Json
                if ($beard.nonzero -le 0) { throw "Terrain adaptation not actually exercised $($fixture.id)" }
            }
        }
        $passed++
    }
    $expected = if ($Bop) { 9 } elseif ($selection.suite -eq 'matrix') { 72 } elseif ($selection.suite -eq 'smoke') { 4 } elseif ($selection.suite -eq 'structures') { 18 } else { throw 'Unknown suite' }
    if ($passed -ne $expected) { throw "Incomplete suite: $passed/$expected" }
    if (@($selection.fixtures.id | Select-Object -Unique).Count -ne $expected) { throw 'Duplicate fixtures' }
    if ($Lightweight -and ($surfacePositive -eq 0 -or $carverPositive -eq 0)) { throw 'No actual surface/carver block changes observed' }
    if ($selection.suite -eq 'matrix' -and !$Bop) {
        $origin = @($selection.fixtures | Where-Object { $_.dimension -eq 'minecraft:overworld' -and $_.x -eq 0 -and $_.z -eq 0 }).Count
        $overworld = @($selection.fixtures | Where-Object dimension -EQ 'minecraft:overworld').Count
        $nether = @($selection.fixtures | Where-Object dimension -EQ 'minecraft:the_nether').Count
        $end = @($selection.fixtures | Where-Object dimension -EQ 'minecraft:the_end').Count
        if ($origin -ne 8 -or $overworld -ne 40 -or $nether -ne 16 -or $end -ne 16) { throw 'Invalid dimension matrix' }
    }
    if ($selection.suite -eq 'structures') {
        foreach ($structure in @('village_plains', 'mineshaft', 'trial_chambers', 'monument', 'fortress', 'end_city', 'stronghold')) {
            foreach ($kind in @('start', 'neighbor')) {
                $fixture = @($selection.fixtures | Where-Object id -EQ "$structure-$kind")
                if ($fixture.Count -ne 1 -or $fixture[0].structure -ne $structure) { throw "Missing structure coverage $structure-$kind" }
                $distance = [Math]::Max([Math]::Abs($fixture[0].x - $fixture[0].startX), [Math]::Abs($fixture[0].z - $fixture[0].startZ))
                if (($kind -eq 'start' -and $distance -ne 0) -or
                        ($kind -eq 'neighbor' -and ($distance -lt 1 -or $distance -gt 8))) { throw "Invalid positive target $structure-$kind" }
            }
        }
        foreach ($structure in @('village_plains', 'trial_chambers')) {
            $halo = @($selection.fixtures | Where-Object id -EQ "$structure-halo")
            if ($halo.Count -ne 1 -or -not $halo[0].terrainOnly) { throw "Missing terrain-only coverage $structure" }
        }
        foreach ($id in @('mosaic-origin', 'mosaic-blocker')) {
            if (@($selection.fixtures | Where-Object id -EQ $id).Count -ne 1) { throw "Missing derived-seed regression $id" }
        }
    }
    "PASS: $($selection.suite) $passed/$expected independent pre-FEATURES fixtures; FEATURES=0."
    Write-Output $(if ($ExpectedInputHash) { "INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT $inputHash" } else { "INPUT_SCOPE: CURRENT_SOURCE $inputHash" })
    exit 0
}

$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$originalAffinity = $orchestrator.ProcessorAffinity
$prefix = (ConvertFrom-StringData (Get-Content -LiteralPath (Join-Path $repoRoot 'gradle.properties') -Raw)).mod_id + '.prefeatures.'
$gradleArguments = @('--max-workers=2', '--no-daemon', '--no-configuration-cache', '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4')
if ($Offline) { $gradleArguments += '--offline' }
$launcher = $null
$privateNativeConfig = $null
. (Join-Path $PSScriptRoot '../Native-ServerLaunch.ps1')

function Invoke-Probe {
    param([string]$World, [string]$Seed, [string[]]$Properties, [string]$Log, [string]$EvidencePath, [string]$ExpectedStatus)
    $worldPath = Join-Path $validationServer $World
    if (Test-Path -LiteralPath $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
    $crashDirectory = Join-Path $validationServer 'crash-reports'
    $previousCrashes = @{}
    if (Test-Path $crashDirectory) {
        Get-ChildItem $crashDirectory -File | ForEach-Object { $previousCrashes[$_.Name] = (Get-FileHash $_.FullName).Hash }
    }
    $text = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$Seed")
    if ($text -match '(?m)^level-name=') {
        $text = [regex]::Replace($text, '(?m)^level-name=.*$', "level-name=$World")
    } else { $text += "`nlevel-name=$World`n" }
    [IO.File]::WriteAllText($validationProperties, $text, [Text.UTF8Encoding]::new($false))
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 ' + ($Properties -join ' ')
    $exitCode = 0
    $nativeArgumentFile = $null -ne $launcher -and $Target.EndsWith('-neoforge') -and
            $launcher.main -ceq 'net.neoforged.devlaunch.Main' -and
            @($launcher.gameArguments).Count -eq 1 -and
            $launcher.gameArguments[0].StartsWith('@')
    if ($null -eq $launcher -or ($Target.EndsWith('-neoforge') -and !$nativeArgumentFile)) {
        if ($BootstrapLauncher) { throw 'Scoped native bootstrap cannot fall back to a different Gradle launcher' }
        $task = if ($Bop) { 'runValidationCompatibilityBopServer' } else { 'runValidationConformancePrefeaturesServer' }
        if ($Target.EndsWith('-neoforge')) {
            & (Join-Path $repoRoot 'gradlew.bat') ":version:$validationTarget`:$task" @gradleArguments *> $Log
        } else {
            & (Join-Path $repoRoot 'gradlew.bat') ":version:$validationTarget`:$task" @gradleArguments "--args=nogui --world $World --port 0" *> $Log
        }
        $exitCode = $LASTEXITCODE
    } else {
        # Reuse the template task's actual JVM configuration, avoiding a Gradle bootstrap per fixture.
        $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
        $start.WorkingDirectory = $launcher.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in $launcher.jvmArguments) {
            if ($privateNativeConfig -and $argument.StartsWith('-Dfabric.dli.config=')) { continue }
            $start.ArgumentList.Add($argument)
        }
        if ($privateNativeConfig -and $Target -eq '26.3-fabric') { $start.ArgumentList.Add("-Dfabric.dli.config=$privateNativeConfig") }
        $start.ArgumentList.Add('-cp')
        $start.ArgumentList.Add($launcher.classpath)
        $start.ArgumentList.Add($launcher.main)
        $gameArguments = @($launcher.gameArguments)
        if ($privateNativeConfig -and $Target -eq '26.3-neoforge') { $gameArguments = @('@' + $privateNativeConfig) }
        if ($Target -eq '26.3-fabric') {
            $worldIndex = [Array]::IndexOf($gameArguments, '--world')
            if ($worldIndex -lt 0 -or $worldIndex + 1 -ge $gameArguments.Count) { throw 'Missing template world argument' }
            $gameArguments[$worldIndex + 1] = $World
            if ($privateNativeConfig) {
                $ports = 0
                for ($i = 0; $i -lt $gameArguments.Count; $i++) {
                    if ($gameArguments[$i] -ceq '--port') {
                        if ($i + 1 -ge $gameArguments.Count -or $gameArguments[$i + 1] -notmatch '^\d+$') { throw 'Unknown exported native server port argument' }
                        $gameArguments[$i + 1] = '0'; $ports++
                    }
                }
                if ($ports -eq 0) { throw 'Exported native server port missing' }
            }
        }
        # DevLaunch reads its own unchanged argument file; the world is selected by server.properties.
        foreach ($argument in $gameArguments) { $start.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
        $process.ProcessorAffinity = [IntPtr]$CpuAffinity
        if ($privateNativeConfig) {
            $execution = @{ schema = 1; pid = $process.Id; affinity = $process.ProcessorAffinity.ToInt64(); priority = $process.PriorityClass.ToString(); world = $World; nativeConfig = $privateNativeConfig; nativeConfigSha256 = (Get-FileHash $privateNativeConfig).Hash; requestedPort = 0; gameArguments = $gameArguments }
            [IO.File]::WriteAllText($Log + '.execution.json', ($execution | ConvertTo-Json) + "`n", [Text.UTF8Encoding]::new($false))
        }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(300000)) {
            $process.Kill($true)
            $process.WaitForExit()
            [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
            throw "Probe exceeded the five-minute test bound: $World"
        }
        [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        $exitCode = $process.ExitCode
        $process.Dispose()
    }
    if ($exitCode -ne 0) {
        Get-Content -LiteralPath $Log -Tail 45
        throw "Probe process failed: $World ($exitCode)"
    }
    if (Test-Path $crashDirectory) {
        foreach ($report in Get-ChildItem $crashDirectory -File) {
            if (!$previousCrashes.ContainsKey($report.Name) -or $previousCrashes[$report.Name] -cne (Get-FileHash $report.FullName).Hash) {
                throw "New pre-FEATURES crash report: $($report.Name)"
            }
        }
    }
    if ($EvidencePath) {
        if (!(Test-Path $EvidencePath) -or (Get-Content $EvidencePath -Raw | ConvertFrom-Json).status -cne $ExpectedStatus) {
            throw "Pre-FEATURES evidence failed: $World"
        }
    } elseif (!(Test-Path (Join-Path $resultRoot 'launcher.json')) -or !(Test-Path (Join-Path $resultRoot 'fixtures.json'))) {
        throw 'Pre-FEATURES launcher export did not complete'
    }
    Move-ValidationToRecycleBin -LiteralPath $worldPath
}

function Preserve-FirstFailure {
    $failureRoot = Join-Path $repoRoot "validations/$validationProject/result/first_failure/$resultVariant/$validationTarget"
    $failed = @(Get-ChildItem $resultRoot -Filter summary.json -Recurse -File -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).status -cin @('FAIL','DIVERGED') })
    if ($failed.Count -gt 0 -and !(Test-Path $failureRoot)) {
        New-Item -ItemType Directory -Path (Split-Path $failureRoot -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $resultRoot -Destination $failureRoot -Recurse
    }
    foreach ($entry in $failed) {
        $summary = Get-Content -LiteralPath $entry.FullName -Raw | ConvertFrom-Json
        $savedWorld = [IO.Path]::GetFullPath($summary.save)
        $allowed = [IO.Path]::GetFullPath($validationServer) + [IO.Path]::DirectorySeparatorChar
        if (!$savedWorld.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or
                $summary.fixture -notmatch '^[a-z0-9_-]+$' -or $summary.role -notmatch '^[a-z0-9_-]+$') {
            throw 'Refusing failure world capture outside the owned validation instance'
        }
        $backup = Join-Path $validationServer "first-failure-$resultVariant-$($summary.fixture)-$($summary.role)"
        if ((Test-Path -LiteralPath $savedWorld) -and !(Test-Path -LiteralPath $backup)) {
            if ((Get-Item -LiteralPath $savedWorld).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing a linked failure world' }
            Copy-Item -LiteralPath $savedWorld -Destination $backup -Recurse
        }
    }
}

try {
    $orchestrator.PriorityClass = 'BelowNormal'
    if ($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($originalAffinity.ToInt64() -band $CpuAffinity) -ne $CpuAffinity) { throw 'Exactly four available logical CPUs required' }
    $orchestrator.ProcessorAffinity = [IntPtr]$CpuAffinity
    if ($EvidenceLabel -and (Test-Path $resultRoot)) { throw 'Preserve existing scoped source-state evidence; never overwrite it' }
    Preserve-FirstFailure
    if (Test-Path -LiteralPath $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    if ($BootstrapLauncher) {
        $bootstrapPath = [IO.Path]::GetFullPath($BootstrapLauncher)
        $allowed = [IO.Path]::GetFullPath((Join-Path $repoRoot "validations/$validationProject/result")) + [IO.Path]::DirectorySeparatorChar
        if (!$bootstrapPath.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or
                [IO.Path]::GetFileName($bootstrapPath) -cne 'launcher.json' -or
                ((Get-Item $bootstrapPath).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Foreign or linked launcher bootstrap' }
        $launcher = Get-Content $bootstrapPath -Raw | ConvertFrom-Json
        if ([IO.Path]::GetFullPath($launcher.directory) -cne [IO.Path]::GetFullPath($validationServer) -or
                $launcher.jvmArguments -cnotcontains "-Dvalidation.target=$validationTarget") { throw 'Unsafe or foreign pre-FEATURES launcher bootstrap' }
        if ($Target -eq '26.3-fabric' -and ($launcher.main -cne 'net.fabricmc.devlaunchinjector.Main' -or $launcher.jvmArguments -cnotcontains '-Dfabric.dli.env=server')) { throw 'Unknown Fabric server bootstrap' }
        $privateNativeConfig = New-OwnedNativeConfig $launcher
    }
    $suiteProperties = @(if ($Suite -eq 'structures') { "-D$($prefix)structures=true" }; if ($Compact) { "-D$($prefix)compact=true" }; if ($Lightweight) { "-D$($prefix)lightweight=true" }; if ($Lightweight -or $Bop -or $BiomeOnly) { "-D$($prefix)variant=$resultVariant" }; if ($StructureReuse) { "-D$($prefix)structureReuse=true" }; if ($BiomeOnly) { "-D$($prefix)biomeOnly=true" })
    Invoke-Probe -World "prefeatures-export$worldSuffix" -Seed '0' -Properties (@("-D$($prefix)export=true") + $suiteProperties) -Log (Join-Path $resultRoot 'export.log')
    $launcher = Get-Content -LiteralPath (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    $fixtures = @(Get-Content -LiteralPath (Join-Path $resultRoot 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($Suite -eq 'smoke' -and !$Bop) { $fixtures = @($fixtures[3], $fixtures[22], $fixtures[40], $fixtures[56]) }
    [IO.File]::WriteAllText($selectionPath, (@{ suite = $Suite; lightweight = [bool]$Lightweight; structureReuse = [bool]$StructureReuse; compact = [bool]$Compact; biomeOnly = [bool]$BiomeOnly; fixtures = $fixtures; ownedNativePort = [bool]$privateNativeConfig; cpuAffinity = $CpuAffinity } | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))
    $count = 0
    foreach ($fixture in $fixtures) {
        $fixtureRoot = Join-Path $resultRoot $fixture.id
        New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
        $properties = @(
            "-D$($prefix)fixture=$($fixture.id)", "-D$($prefix)master=$($fixture.master)",
            "-D$($prefix)dimension=$($fixture.dimension)", "-D$($prefix)x=$($fixture.x)",
            "-D$($prefix)z=$($fixture.z)", "-D$($prefix)inputHash=$inputHash"
        )
        $properties += $suiteProperties
        if ($Suite -eq 'structures' -or $Bop) { $properties += "-D$($prefix)local=$($fixture.local)" }
        Invoke-Probe -World "prefeatures-native-$($fixture.id)$worldSuffix" -Seed $fixture.local -Properties ($properties + "-D$($prefix)role=native") -Log (Join-Path $fixtureRoot 'native.log') -EvidencePath (Join-Path $fixtureRoot 'native/summary.json') -ExpectedStatus 'PRODUCED'
        $hostSeed = if ($Bop) { if ($fixture.local -eq "$HostSeed") { throw 'BOP host seed must be unrelated' }; "$HostSeed" } elseif ($fixture.local -eq '0') { '1' } else { '0' }
        $role = if ($BiomeOnly) { 'biome-only' } elseif ($Lightweight) { 'lightweight' } elseif ($Compact -or ($Suite -eq 'structures' -and $Target -ne '26.1.2-neoforge')) { 'compact' } else { 'reference' }
        Invoke-Probe -World "prefeatures-$role-$($fixture.id)$worldSuffix" -Seed $hostSeed -Properties ($properties + "-D$($prefix)role=$role") -Log (Join-Path $fixtureRoot "$role.log") -EvidencePath (Join-Path $fixtureRoot "$role/summary.json") -ExpectedStatus 'PASS'
        $summary = Get-Content -LiteralPath (Join-Path $fixtureRoot "$role/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS') { throw "Parity failed: $($fixture.id)" }
        $count++
        "PRE-FEATURES $count/$($fixtures.Count) PASS $($fixture.id) $($fixture.dimension) ($($fixture.x),$($fixture.z))"
    }
    if ((Get-InputHash) -cne $inputHash) { throw 'Target source changed during independent Oracle validation' }
} catch {
    Preserve-FirstFailure
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    $orchestrator.PriorityClass = $originalPriority
    $orchestrator.ProcessorAffinity = $originalAffinity
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
}
& $PSCommandPath -Target $Target -Suite $Suite -Lightweight:$Lightweight -StructureReuse:$StructureReuse -Compact:$Compact -Bop:$Bop -BiomeOnly:$BiomeOnly -HostSeed $HostSeed -EvidenceLabel $EvidenceLabel -CheckOnly
