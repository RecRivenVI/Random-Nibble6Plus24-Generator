param(
    [ValidateSet('matrix', 'smoke', 'structures')][string]$Suite = 'matrix',
    [switch]$Lightweight,
    [switch]$StructureReuse,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$repoRoot = $validationRepository
if ($StructureReuse -and !$Lightweight) { throw 'Structure reuse is a lightweight consumer check' }
$resultVariant = if ($StructureReuse) { "structure_reuse_$Suite" } elseif ($Lightweight) { "terrain_$Suite" } elseif ($Suite -eq 'structures') { 'compact' } else { 'current' }
$resultRoot = Join-Path $repoRoot "validations/$validationProject/result/$resultVariant/$validationTarget"
$selectionPath = Join-Path $resultRoot 'selection.json'

if ($CheckOnly) {
    $selection = Get-Content -LiteralPath $selectionPath -Raw | ConvertFrom-Json
    if ($selection.suite -ne $Suite -or [bool]$selection.lightweight -ne [bool]$Lightweight -or [bool]$selection.structureReuse -ne [bool]$StructureReuse) { throw 'Selection does not match the requested suite' }
    $passed = 0
    $surfacePositive = 0
    $carverPositive = 0
    foreach ($fixture in $selection.fixtures) {
        $fixtureRoot = Join-Path $resultRoot $fixture.id
        $native = Get-Content -LiteralPath (Join-Path $fixtureRoot 'native/summary.json') -Raw | ConvertFrom-Json
        $role = if ($Lightweight) { 'lightweight' } elseif ($selection.suite -eq 'structures') { 'compact' } else { 'reference' }
        $reference = Get-Content -LiteralPath (Join-Path $fixtureRoot "$role/summary.json") -Raw | ConvertFrom-Json
        if ($native.status -ne 'PRODUCED' -or $reference.status -ne 'PASS' -or
                $native.featuresExecuted -ne 0 -or $reference.featuresExecuted -ne 0 -or
                $native.checkpoints.Count -ne 4 -or $reference.checkpoints.Count -ne 4 -or
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
        if ($native.fixture -ne $fixture.id -or $native.masterSeed -ne $fixture.master -or
                $native.localSeed -ne $fixture.local -or $native.dimension -ne $fixture.dimension -or
                $native.chunkX -ne $fixture.x -or $native.chunkZ -ne $fixture.z) {
            throw "Fixture manifest differs from the selection: $($fixture.id)"
        }
        foreach ($stage in @('structure_starts', 'structure_references', 'biomes', 'terrain')) {
            $files = @("$stage-structures.json")
            if ($stage -in @('biomes', 'terrain')) { $files += "$stage.bin.gz" }
            foreach ($file in $files) {
                $left = Get-FileHash -LiteralPath (Join-Path $fixtureRoot "native/$file") -Algorithm SHA256
                $right = Get-FileHash -LiteralPath (Join-Path $fixtureRoot "$role/$file") -Algorithm SHA256
                if ($left.Hash -cne $right.Hash) { throw "Evidence file differs: $($fixture.id) $file" }
            }
        }
        if ($Lightweight) {
            if ($StructureReuse -and (!$reference.structureReuse.closed -or $reference.structureReuse.resident -ne 0 -or $reference.structureReuse.hits -lt 289 -or $reference.structureReuse.peakResident -gt 361)) { throw 'Native structure reuse proof missing' }
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
            foreach ($file in @('beardifier.bin', 'beardifier.json', 'projection.json')) {
                if ((Get-FileHash -LiteralPath (Join-Path $fixtureRoot "native/$file")).Hash -cne
                        (Get-FileHash -LiteralPath (Join-Path $fixtureRoot "$role/$file")).Hash) { throw "Density differs: $($fixture.id)" }
            }
            if ($reference.compactInputs.referenceRadius -ne 8 -or $reference.compactInputs.evaluatedSources -ne 289) { throw 'Invalid compact frontier' }
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
    $expected = if ($selection.suite -eq 'matrix') { 72 } elseif ($selection.suite -eq 'smoke') { 4 } elseif ($selection.suite -eq 'structures') { 18 } else { throw 'Unknown suite' }
    if ($passed -ne $expected) { throw "Incomplete suite: $passed/$expected" }
    if (@($selection.fixtures.id | Select-Object -Unique).Count -ne $expected) { throw 'Duplicate fixtures' }
    if ($Lightweight -and ($surfacePositive -eq 0 -or $carverPositive -eq 0)) { throw 'No actual surface/carver block changes observed' }
    if ($selection.suite -eq 'matrix') {
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
    exit 0
}

$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$worlds = [Collections.Generic.List[string]]::new()
$prefix = (ConvertFrom-StringData (Get-Content -LiteralPath (Join-Path $repoRoot 'gradle.properties') -Raw)).mod_id + '.prefeatures.'
$gradleArguments = @('--max-workers=2', '--no-daemon', '--no-configuration-cache', '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4')
$launcher = $null

function Invoke-Probe {
    param([string]$World, [string]$Seed, [string[]]$Properties, [string]$Log)
    $worldPath = Join-Path $validationServer $World
    if (Test-Path -LiteralPath $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
    $worlds.Add($worldPath)
    $text = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$Seed")
    [IO.File]::WriteAllText($validationProperties, $text, [Text.UTF8Encoding]::new($false))
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 ' + ($Properties -join ' ')
    $exitCode = 0
    if ($null -eq $launcher) {
        & (Join-Path $repoRoot 'gradlew.bat') ":version:$validationTarget`:runValidationConformancePrefeaturesServer" @gradleArguments "--args=nogui --world $World" *> $Log
        $exitCode = $LASTEXITCODE
    } else {
        # Reuse the template task's actual JVM configuration, avoiding a Gradle bootstrap per fixture.
        $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
        $start.WorkingDirectory = $launcher.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in $launcher.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp')
        $start.ArgumentList.Add($launcher.classpath)
        $start.ArgumentList.Add($launcher.main)
        $gameArguments = @($launcher.gameArguments)
        $worldIndex = [Array]::IndexOf($gameArguments, '--world')
        if ($worldIndex -lt 0 -or $worldIndex + 1 -ge $gameArguments.Count) { throw 'Missing template world argument' }
        $gameArguments[$worldIndex + 1] = $World
        foreach ($argument in $gameArguments) { $start.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
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
    Move-ValidationToRecycleBin -LiteralPath $worldPath
}

try {
    $orchestrator.PriorityClass = 'BelowNormal'
    if (Test-Path -LiteralPath $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    $suiteProperties = @(if ($Suite -eq 'structures') { "-D$($prefix)structures=true" }; if ($Lightweight) { "-D$($prefix)lightweight=true"; "-D$($prefix)variant=$resultVariant" }; if ($StructureReuse) { "-D$($prefix)structureReuse=true" })
    Invoke-Probe -World 'prefeatures-export' -Seed '0' -Properties (@("-D$($prefix)export=true") + $suiteProperties) -Log (Join-Path $resultRoot 'export.log')
    $launcher = Get-Content -LiteralPath (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    $fixtures = @(Get-Content -LiteralPath (Join-Path $resultRoot 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($Suite -eq 'smoke') { $fixtures = @($fixtures[3], $fixtures[22], $fixtures[40], $fixtures[56]) }
    [IO.File]::WriteAllText($selectionPath, (@{ suite = $Suite; lightweight = [bool]$Lightweight; structureReuse = [bool]$StructureReuse; fixtures = $fixtures } | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))
    $inputFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "versions/$validationTarget/src/main"), (Join-Path $repoRoot 'components/worldgen_contract/src') -File -Recurse | Sort-Object FullName)
    $inputHashes = ($inputFiles | ForEach-Object { $_.FullName.Substring($repoRoot.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
    $inputHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($inputHashes)))
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
        if ($Suite -eq 'structures') { $properties += "-D$($prefix)local=$($fixture.local)" }
        Invoke-Probe -World "prefeatures-native-$($fixture.id)" -Seed $fixture.local -Properties ($properties + "-D$($prefix)role=native") -Log (Join-Path $fixtureRoot 'native.log')
        $hostSeed = if ($fixture.local -eq '0') { '1' } else { '0' }
        $role = if ($Lightweight) { 'lightweight' } elseif ($Suite -eq 'structures') { 'compact' } else { 'reference' }
        Invoke-Probe -World "prefeatures-$role-$($fixture.id)" -Seed $hostSeed -Properties ($properties + "-D$($prefix)role=$role") -Log (Join-Path $fixtureRoot "$role.log")
        $summary = Get-Content -LiteralPath (Join-Path $fixtureRoot "$role/summary.json") -Raw | ConvertFrom-Json
        if ($summary.status -ne 'PASS') { throw "Parity failed: $($fixture.id)" }
        $count++
        "PRE-FEATURES $count/$($fixtures.Count) PASS $($fixture.id) $($fixture.dimension) ($($fixture.x),$($fixture.z))"
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    $orchestrator.PriorityClass = $originalPriority
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
    foreach ($path in $worlds) {
        if (Test-Path -LiteralPath $path) { Move-ValidationToRecycleBin -LiteralPath $path }
    }
}
& $PSCommandPath -Suite $Suite -Lightweight:$Lightweight -StructureReuse:$StructureReuse -CheckOnly
