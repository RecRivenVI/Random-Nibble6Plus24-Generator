param(
    [long]$MasterSeed = 123456789,
    [string]$Dimension = 'minecraft:overworld',
    [int]$ChunkX = 125,
    [int]$ChunkZ = -37,
    [ValidateSet('full', 'concurrent', 'patch', 'pair', 'reload')]
    [string]$Mode = 'full',
    [int]$Repeats = 16,
    [ValidateSet(1, 2)]
    [int]$FeatureOrderingVersion = 1,
    [switch]$DevelopmentB,
    [switch]$Bop,
    [switch]$VerifyReload,
    [switch]$Mutate,
    [switch]$StateProof,
    [switch]$PartialBiomes,
    [switch]$VanillaControl,
    [switch]$RuntimeProof,
    [switch]$RequestProof,
    [switch]$PublicationInventory,
    [switch]$PlayerLifecycle,
    [ValidateSet('','entities','block_entities')][string]$NaturalProof='',
    [switch]$CompletionOrder,
    [switch]$PhysicalHandoff,
    [switch]$LazyRings,
    [switch]$NarrowLocks,
    [switch]$ColdSpawn,
    [switch]$ReuseSources,
    [switch]$Offline,
    [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = '',
    [long]$CpuAffinity = 3840,
    [switch]$PreserveFailureOnly,
    [switch]$KeepWorld
)

if ($Bop) { $validationProjectOverride = 'compatibility-bop' }
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
. (Join-Path $PSScriptRoot '../Native-RequestEvidence.ps1')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($FeatureOrderingVersion -eq 2 -and $validationTarget -notin @('26.3-fabric','26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge')) {
    throw 'Target-only FEATURES has not been adapted to this Target.'
}
if ($FeatureOrderingVersion -eq 2 -and $validationTarget -in @('26.1.2-neoforge','1.21.1-neoforge') -and !$DevelopmentB) { throw 'This Target requires the distinct B identity for Ordering 2' }
if ($Bop -and $validationTarget -ne '26.3-fabric') { throw 'BOP compatibility is validated only on 26.3-fabric' }
if ($StateProof -and $validationTarget -notin @('26.3-fabric','26.3-neoforge')) { throw 'Physical state persistence evidence is currently adapted only to 26.3' }
if ($StateProof -and $PartialBiomes) { throw 'Use the partial-stage native-load proof separately from physical FULL state evidence' }
if ($VanillaControl -and ($validationTarget -notin @('26.3-fabric','26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge') -or $Bop -or $DevelopmentB -or
            $StateProof -or $PartialBiomes -or $FeatureOrderingVersion -ne 1 -or
            $Mode -notin @('full','reload'))) { throw 'Vanilla lifecycle control requires an adapted ordinary FULL/save/reload probe' }

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
if ($ColdSpawn -and ($validationTarget -ne '1.21.1-neoforge' -or !$DevelopmentB -or !$VerifyReload -or !$EvidenceLabel -or $FeatureOrderingVersion -ne 2 -or $Mode -ne 'full' -or $Dimension -ne 'minecraft:overworld' -or $VanillaControl -or $RuntimeProof -or $PhysicalHandoff -or $StateProof -or $PartialBiomes -or $CompletionOrder)) { throw 'Cold SPAWN is a separate 1.21.1 B LIGHT shutdown/restart proof' }
if ($ReuseSources -and ((!$ColdSpawn -and !$RuntimeProof) -or !$NarrowLocks -or !$DevelopmentB)) { throw 'Source reuse here is restricted to opt-in cold/runtime correctness proofs' }
if ($RequestProof -and ($validationTarget -notin @('26.3-fabric','26.3-neoforge') -or !$DevelopmentB -or !$EvidenceLabel -or
        $Mode -notin @('concurrent','pair','patch') -or $VerifyReload -or $RuntimeProof -or $StateProof -or $PartialBiomes -or
        $VanillaControl -or $PhysicalHandoff -or $ColdSpawn -or $Bop -or $CompletionOrder)) { throw 'Request proof requires separate labelled modern B concurrent/pair/patch conditions' }
if ($EvidenceLabel -and !$PhysicalHandoff -and !$RuntimeProof -and !$ColdSpawn -and !$RequestProof -and !$PlayerLifecycle) { throw 'Evidence labels require a distinct physical-handoff, runtime, request, player or cold-SPAWN proof' }
if ($LazyRings -and ($validationTarget -ne '1.21.1-neoforge' -or (!$PhysicalHandoff -and !$RuntimeProof -and !$ColdSpawn) -or !$DevelopmentB)) { throw 'Candidate ring scope requires an isolated 1.21.1 B physical or runtime proof' }
if ($NarrowLocks -and ($validationTarget -ne '1.21.1-neoforge' -or !$LazyRings -or !$EvidenceLabel)) { throw 'Narrow locks require the separately labelled 1.21.1 ring candidate' }
if ($RuntimeProof -and ($validationTarget -notin @('26.3-fabric','26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge') -or $StateProof -or $PartialBiomes -or
            $Bop -or !$VerifyReload -or $Mode -notin @('full','reload') -or
            (!$VanillaControl -and !$DevelopmentB))) {
    throw 'Native tick-loop evidence requires an adapted B/Vanilla FULL save/restart probe, separately from state/partial proofs'
}
if($NaturalProof -and ($validationTarget -ne '26.3-fabric' -or !$DevelopmentB -or !$RuntimeProof -or !$VerifyReload -or !$EvidenceLabel -or
        $VanillaControl -or $RequestProof -or $Mode -ne 'full')){throw 'Natural source evidence requires distinct labelled Fabric B runtime/save/reload conditions'}
if($PublicationInventory -and ($validationTarget -ne '26.3-fabric' -or !$DevelopmentB -or !$RuntimeProof -or !$VerifyReload -or !$EvidenceLabel -or
        $VanillaControl -or $RequestProof -or $NaturalProof -or $Mode -ne 'full')){throw 'Publication inventory requires separate labelled Fabric B runtime/save/reload, not a positive activation proof'}
if ($PartialBiomes -and ($validationTarget -ne '26.3-fabric' -or !$DevelopmentB -or !$VerifyReload)) { throw 'Partial BIOMES lifecycle requires 26.3 Fabric Development B and save/restart validation' }
if ($PhysicalHandoff -and ($validationTarget -ne '1.21.1-neoforge' -or (!$DevelopmentB -and !$VanillaControl) -or $StateProof -or $RuntimeProof -or $PartialBiomes)) { throw 'Scoped physical-handoff evidence currently requires the 1.21.1 B or Vanilla native lifecycle probe' }
if ($CompletionOrder -and ($validationTarget -ne '1.21.1-neoforge' -or !$DevelopmentB -or $VanillaControl -or $RuntimeProof -or $StateProof -or $PartialBiomes -or $Mode -ne 'full' -or $VerifyReload)) { throw 'Completed-input interleaving is a separate 1.21.1 Mosaic FULL probe, not reload/runtime evidence' }
if ($DevelopmentB -and ($validationTarget -notin @('26.3-fabric','26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge') -or $FeatureOrderingVersion -ne 2)) { throw 'Development B requires an adapted Target and explicit Ordering 2' }
if($PlayerLifecycle -and ($validationTarget -ne '26.3-fabric' -or (!$DevelopmentB -and !$VanillaControl) -or
        !$VerifyReload -or !$EvidenceLabel -or $Mode -ne 'full' -or $Dimension -ne 'minecraft:overworld' -or
        $RuntimeProof -or $StateProof -or $RequestProof -or $NaturalProof -or $PublicationInventory -or $Bop -or
        $ColdSpawn -or $PhysicalHandoff -or $PartialBiomes -or $CompletionOrder -or $LazyRings -or $NarrowLocks -or $ReuseSources)){
    throw 'Native player lifecycle requires a separate labelled Fabric B/Vanilla fresh/reload scenario'
}
$runRoot = $validationServer
$propertiesPath = Join-Path $runRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$originalPriority = $orchestrator.PriorityClass
$originalAffinity = $orchestrator.ProcessorAffinity
if ($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or
        ($CpuAffinity -band $originalAffinity.ToInt64()) -ne $CpuAffinity) {
    throw 'Correctness queue affinity must contain exactly four available logical CPUs'
}
$worldName = "phase3c2-production-$MasterSeed-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ"
if ($FeatureOrderingVersion -ne 1) { $worldName += "-features-$FeatureOrderingVersion" }
if ($DevelopmentB) { $worldName += '-lightweight-b' }
if ($VanillaControl) { $worldName += '-vanilla-control' }
if ($CompletionOrder) { $worldName += '-completion-order' }
if ($PhysicalHandoff) { $worldName += '-physical-handoff' }
if ($LazyRings) { $worldName += '-lazy-rings' }
if ($NarrowLocks) { $worldName += '-narrow-locks' }
if ($RuntimeProof) { $worldName += $(if ($validationTarget -in @('26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge')) { '-runtime-proof-frontier' } else { '-runtime-proof' }) }
if ($RequestProof) { $worldName += "-request-proof-$Mode" }
if ($NaturalProof) { $worldName += "-natural-$NaturalProof" }
if ($PublicationInventory) { $worldName += '-publication-inventory' }
if ($PlayerLifecycle) { $worldName += '-player-lifecycle' }
if ($ColdSpawn) { $worldName += '-cold-spawn' }
if ($ReuseSources -and !$ColdSpawn) { $worldName += '-source-reuse' }
if ($EvidenceLabel) { $worldName += "-$EvidenceLabel" }
$worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
$resultRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot "validations\$validationProject\result\current\${validationTarget}\phase3c2-production"))
if ($FeatureOrderingVersion -ne 1) { $resultRoot = Join-Path $resultRoot "features-$FeatureOrderingVersion" }
if ($DevelopmentB) { $resultRoot = Join-Path $resultRoot 'development-b' }
if ($PublicationInventory) { $resultRoot = Join-Path $resultRoot 'publication-inventory' }
if ($PlayerLifecycle) { $resultRoot = Join-Path $resultRoot 'player-lifecycle' }
if ($StateProof) { $resultRoot = Join-Path $resultRoot 'state-proof' }
if ($PartialBiomes) { $resultRoot = Join-Path $resultRoot 'partial-biomes' }
if ($CompletionOrder) { $resultRoot = Join-Path $resultRoot 'completion-order' }
if ($VanillaControl) { $resultRoot = Join-Path $resultRoot 'vanilla-control' }
if ($PhysicalHandoff) { $resultRoot = Join-Path (Join-Path $resultRoot 'physical-handoff') $Dimension.Replace(':','-') }
if ($LazyRings) { $resultRoot = Join-Path $resultRoot 'lazy-rings' }
if ($NarrowLocks) { $resultRoot = Join-Path $resultRoot 'narrow-locks' }
if ($ReuseSources -and !$ColdSpawn) { $resultRoot = Join-Path $resultRoot 'source-reuse' }
if ($RuntimeProof) { $runtimeDirectory = if ($validationTarget -in @('26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge')) { 'runtime-proof-frontier' } else { 'runtime-proof' }; $resultRoot = Join-Path (Join-Path $resultRoot $runtimeDirectory) $Dimension.Replace(':','-') }
if ($RequestProof) { $resultRoot = Join-Path (Join-Path $resultRoot 'request-proof') $Dimension.Replace(':','-') }
if ($ColdSpawn) { $resultRoot = Join-Path (Join-Path $resultRoot 'cold-spawn') $Dimension.Replace(':','-') }
if ($EvidenceLabel) { $resultRoot = Join-Path $resultRoot $EvidenceLabel }
$resultPath = Join-Path $resultRoot "$Mode-$ChunkX-$ChunkZ.json"
if ($RequestProof -and ((Test-Path -LiteralPath $worldPath) -or (Test-Path -LiteralPath $resultPath) -or
        (Test-Path -LiteralPath (Join-Path $resultRoot "conditions-$Mode-$ChunkX-$ChunkZ.json")))) {
    throw 'Preserve scoped production request evidence/world; never overwrite it'
}
if ($NaturalProof -and ((Test-Path -LiteralPath $worldPath) -or (Test-Path -LiteralPath $resultPath) -or
        (Test-Path -LiteralPath (Join-Path $resultRoot "conditions-$Mode-$ChunkX-$ChunkZ.json")) -or
        (Test-Path -LiteralPath (Join-Path $resultRoot 'natural-source.nbt')))) {
    throw 'Preserve scoped natural lifecycle evidence/world; never overwrite it'
}
if($PublicationInventory -and ((Test-Path -LiteralPath $worldPath) -or (Test-Path -LiteralPath $resultPath) -or
        (Test-Path -LiteralPath (Join-Path $resultRoot "conditions-$Mode-$ChunkX-$ChunkZ.json")))){throw 'Preserve publication inventory evidence/world; never overwrite it'}
if($PlayerLifecycle -and ((Test-Path -LiteralPath $worldPath) -or (Test-Path -LiteralPath $resultPath) -or
        (Test-Path -LiteralPath (Join-Path $resultRoot "conditions-$Mode-$ChunkX-$ChunkZ.json")))){throw 'Preserve native player lifecycle evidence/world; never overwrite it'}
$completed = $false

function Preserve-RuntimeFailure([string]$reason) {
    $failureRoot = Join-Path $resultRoot 'first_runtime_failure'
    if (Test-Path -LiteralPath $failureRoot) { return }
    New-Item -ItemType Directory -Force -Path $failureRoot | Out-Null
    foreach ($source in @((Join-Path $runRoot 'logs/latest.log'), (Join-Path $runRoot 'crash-reports'), $worldPath)) {
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $failureRoot -Recurse }
    }
    Get-ChildItem -LiteralPath $resultRoot -File | Copy-Item -Destination $failureRoot
    [IO.File]::WriteAllText((Join-Path $failureRoot 'failure.txt'),$reason)
}

function Remove-World {
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) `
            -or (Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing unsafe Phase 3C2 cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Move-ValidationToRecycleBin -LiteralPath $worldPath
    }
}

function Set-Property([string]$content, [string]$name, [string]$value) {
    if ($content -match "(?m)^$([regex]::Escape($name))=") {
        return [regex]::Replace($content, "(?m)^$([regex]::Escape($name))=.*$", "$name=$value")
    }
    return $content.TrimEnd() + [Environment]::NewLine + "$name=$value" + [Environment]::NewLine
}

function Get-ProductionInputHash {
    $files=@(Get-ChildItem (Join-Path $repositoryRoot "versions/$validationTarget/src"),(Join-Path $repositoryRoot 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
    $body=($files|ForEach-Object{$_.FullName.Substring($repositoryRoot.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $body+=[IO.File]::ReadAllText((Join-Path $repositoryRoot "versions/$validationTarget/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))
}

function Write-ResultSummary($evidence) {
    if ($StateProof) {
        # Full canonical state and diagnostics remain in their evidence files, not console output.
        $evidence | Select-Object * -ExcludeProperty stateProof,lifecycleDiagnostics | ConvertTo-Json -Depth 8
    } else {
        $evidence | ConvertTo-Json -Depth 8
    }
}

function Write-HiddenPresetPack {
    $packRoot = Join-Path $worldPath 'datapacks\phase3c2-hidden-mosaic'
    $presetDirectory = Join-Path $packRoot 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $presetDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $packRoot 'pack.mcmeta'),
        $validationPackMetadata)
    $profile = '"mosaic_profile":{"format_version":2,"seed_derivation_algorithm_version":1,"feature_ordering_algorithm_version":' + $FeatureOrderingVersion + ',"presentation_algorithm_version":1,"primary_dimension":"minecraft:overworld"}'
    $generatorType = if ($DevelopmentB) { 'development_b' } else { 'mosaic' }
    $preset = @"
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "randomnibble6plus24generator:$generatorType",
        "biome_source": {"type":"minecraft:multi_noise","preset":"minecraft:overworld"},
        "settings": "minecraft:overworld",
        $profile
      }
    },
    "minecraft:the_nether": {
      "type": "minecraft:the_nether",
      "generator": {
        "type": "randomnibble6plus24generator:$generatorType",
        "biome_source": {"type":"minecraft:multi_noise","preset":"minecraft:nether"},
        "settings": "minecraft:nether",
        $profile
      }
    },
    "minecraft:the_end": {
      "type": "minecraft:the_end",
      "generator": {
        "type": "randomnibble6plus24generator:$generatorType",
        "biome_source": {"type":"minecraft:the_end"},
        "settings": "minecraft:end",
        $profile
      }
    }
  }
}
"@
    [IO.File]::WriteAllText((Join-Path $presetDirectory 'phase3c2_hidden_mosaic.json'), $preset)
    if ($DevelopmentB) {
        $pipeline = ($validationTarget -replace '-(fabric|neoforge|forge)$','') + '-lightweight-b-1'
        [IO.File]::WriteAllText((Join-Path $worldPath 'mosaic-development-b.json'), (@{schema=1;pipeline=$pipeline} | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
    }
}

function Invoke-Production([string]$productionMode, [string]$output, [bool]$mutateBeforeStop, [bool]$expectMarker) {
    $crashDirectory = Join-Path $runRoot 'crash-reports'
    $previousCrashes = @{}
    if (Test-Path -LiteralPath $crashDirectory) {
        Get-ChildItem -LiteralPath $crashDirectory -File | ForEach-Object { $previousCrashes[$_.Name] = (Get-FileHash -LiteralPath $_.FullName).Hash }
    }
    if (Test-Path -LiteralPath $output) {
        $previous = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
        if ($previous.status -ne 'PASS') {
            $failure = Join-Path $resultRoot ('first_failure-' + (Split-Path $output -Leaf))
            if (!(Test-Path -LiteralPath $failure)) { Copy-Item -LiteralPath $output -Destination $failure }
        }
        Move-ValidationToRecycleBin -LiteralPath $output
    }
    foreach ($sidecar in @("$output.physical.nbt.gz", "$output.nbt.gz")) {
        if (Test-Path -LiteralPath $sidecar) { Move-ValidationToRecycleBin -LiteralPath $sidecar }
    }
    $options = @(
        '-XX:ActiveProcessorCount=4',
        "-Drandomnibble6plus24generator.phase3c2.production.mode=$productionMode",
        "-Drandomnibble6plus24generator.phase3c2.production.masterSeed=$MasterSeed",
        "-Drandomnibble6plus24generator.phase3c2.production.dimension=$Dimension",
        "-Drandomnibble6plus24generator.phase3c2.production.chunkX=$ChunkX",
        "-Drandomnibble6plus24generator.phase3c2.production.chunkZ=$ChunkZ",
        "-Drandomnibble6plus24generator.phase3c2.production.repeats=$Repeats",
        "-Drandomnibble6plus24generator.phase3c2.production.output=$($output.Replace('\','/'))"
    )
    if ($ColdSpawn) {
        $options = @($options | Where-Object { $_ -notlike '-Drandomnibble6plus24generator.phase3c2.production.mode=*' })
        $options += "-Drandomnibble6plus24generator.coldSpawn.mode=$productionMode"
        $options += "-Drandomnibble6plus24generator.coldSpawn.output=$($resultRoot.Replace('\','/'))"
        $options += "-Drandomnibble6plus24generator.coldSpawn.inputHash=$($conditions.inputHash)"
        $options += "-Drandomnibble6plus24generator.coldSpawn.x=$ChunkX"
        $options += "-Drandomnibble6plus24generator.coldSpawn.z=$ChunkZ"
    }
    if ($mutateBeforeStop) { $options += '-Drandomnibble6plus24generator.phase3c2.production.mutate=true' }
    if ($expectMarker) { $options += '-Drandomnibble6plus24generator.phase3c2.production.expectMarker=true' }
    if ($StateProof) { $options += '-Drandomnibble6plus24generator.phase3c2.production.stateProof=true' }
    if ($VanillaControl) { $options += '-Drandomnibble6plus24generator.phase3c2.production.vanillaControl=true' }
    if ($RuntimeProof) { $options += '-Drandomnibble6plus24generator.phase3c2.production.runtimeProof=true' }
    if ($CompletionOrder) { $options += '-Drandomnibble6plus24generator.phase3c2.production.completionOrder=true' }
    if ($DevelopmentB) { $options += '-Drandomnibble6plus24generator.developmentB=true' }
    if ($RequestProof) {
        $options += '-Drandomnibble6plus24generator.phase3c2.production.requestProof=true'
        $options += "-Drandomnibble6plus24generator.phase3c2.production.inputHash=$($conditions.inputHash)"
    }
    if ($NaturalProof) {
        $options += "-Drandomnibble6plus24generator.phase3c2.production.naturalProof=$NaturalProof"
        $options += "-Drandomnibble6plus24generator.phase3c2.production.naturalInputHash=$($conditions.inputHash)"
    }
    if($PublicationInventory){
        $options += '-Drandomnibble6plus24generator.phase3c2.production.publicationInventory=true'
        $options += "-Drandomnibble6plus24generator.phase3c2.production.inventoryInputHash=$($conditions.inputHash)"
    }
    if($PlayerLifecycle){
        $options += '-Drandomnibble6plus24generator.phase3c2.production.playerLifecycle=true'
        $options += "-Drandomnibble6plus24generator.phase3c2.production.playerInputHash=$($conditions.inputHash)"
    }
    if ($LazyRings) { $options += '-Drandomnibble6plus24generator.structure.lazyRings=true' }
    if ($NarrowLocks) { $options += '-Drandomnibble6plus24generator.structure.narrowLocks=true' }
    if ($ReuseSources) { $options += '-Drandomnibble6plus24generator.structure.reuseSources=true' }
    if ($PartialBiomes) {
        $baselineName = "expected-native-load-$($Dimension.Replace(':','-'))-$ChunkX-$ChunkZ.snapshot"
        $options += "-Drandomnibble6plus24generator.phase3c2.production.biomeBaseline=$((Join-Path $resultRoot $baselineName).Replace('\','/'))"
    }
    $env:JAVA_TOOL_OPTIONS = $options -join ' '
    $task = if ($Bop) { 'runValidationCompatibilityBopServer' } else { 'runValidationIntegrationProductionLifecycleServer' }
    $launchArguments = @('--max-workers=1', '--no-daemon', '--no-configuration-cache', '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4')
    if($RequestProof -or $NaturalProof -or $PublicationInventory -or $PlayerLifecycle){$launchArguments[-1]='-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=2'}
    if ($Offline) { $launchArguments += '--offline' }
    if ($validationTarget.EndsWith('-fabric')) { $launchArguments += "--args=nogui --world $worldName" }
    & (Join-Path $repositoryRoot 'gradlew.bat') ":version:${validationTarget}:$task" @launchArguments *> "$output.console.log"
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath "$output.console.log" -Tail 30 | Out-Host
        throw "Phase 3C2 production server failed: $LASTEXITCODE"
    }
    if (Test-Path -LiteralPath $crashDirectory) {
        foreach ($report in Get-ChildItem -LiteralPath $crashDirectory -File) {
            if (!$previousCrashes.ContainsKey($report.Name) -or $previousCrashes[$report.Name] -cne (Get-FileHash -LiteralPath $report.FullName).Hash) {
                throw "New production lifecycle crash report: $($report.Name)"
            }
        }
    }
    if (!(Test-Path -LiteralPath $output)) { throw 'Production run did not create fresh evidence' }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw "Phase 3C2 production result failed: $result" }
    if($PlayerLifecycle){
        $closed=Get-Content (Join-Path $resultRoot "player-close-$productionMode.json") -Raw|ConvertFrom-Json
        if($result.schema -ne 1 -or $result.scope -cne 'NATIVE_SERVER_PLAYER_LIFECYCLE_PROBE_TRANSPORT_NOT_CLIENT_HANDSHAKE_OR_RENDER' -or
                $result.inputHash -cne $conditions.inputHash -or $result.mode -cne $productionMode -or $result.normalVanilla -ne [bool]$VanillaControl -or
                $result.worldSeed -ne $MasterSeed -or [IO.Path]::GetFullPath($result.save) -cne $worldPath -or
                $result.logicalProcessors -ne 4 -or $result.joins -ne 2 -or $result.phases.Count -ne 7 -or
                !$result.noPlayerOrChannelRetained -or !$result.markerPresent -or $result.inFlight -ne 0 -or $result.bindings -ne 0 -or $result.spawnBindings -ne 0 -or
                !$closed.completed -or $closed.pid -ne $result.pid -or $closed.inputHash -cne $conditions.inputHash -or
                $closed.playerRetained -or $closed.prepareTaskRetained -or $closed.channelRetained -or
                $closed.inFlight -ne 0 -or $closed.bindings -ne 0 -or $closed.spawnBindings -ne 0){throw 'Native player lifecycle input/cleanup incomplete'}
        if(($result.phases.phase -join ',') -cne 'first_join,returning_player,respawn,nether,nether_return,end,end_return'){throw 'Native player lifecycle phase order differs'}
        foreach($phase in $result.phases){if($phase.physicalStatus -cne 'ENTITY_TICKING' -or !$phase.lightCorrect -or $phase.naturalTicks -lt 20){throw 'Player was not using real physical ticking chunks'}}
        if(($productionMode -eq 'reload' -or $VanillaControl) -and ($result.artifactGenerations -ne 0 -or $result.developmentB.generations -ne 0)){throw 'Player reload/Vanilla regenerated Mosaic terrain'}
        return $result
    }
    if ($ColdSpawn) {
        if ($result.schema -ne 1 -or $result.mode -cne $productionMode -or $result.target -cne "[$ChunkX, $ChunkZ]" -or
                $result.inputHash -cne $conditions.inputHash -or !$result.afterNativeLevelClose -or
                $result.inFlightAfterClose -ne 0 -or $result.workersAfterClose -ne 0) { throw 'Incomplete native cold-SPAWN shutdown evidence' }
        if ($productionMode -eq 'stage') {
            if (!$result.expectedShutdownCancellation -or $result.heldCancellationCompletions -ne 1 -or $result.closingNotifications -lt 1 -or $result.populationCalls -ne 0 -or $result.targetCanonicalPreparations -lt 1) { throw 'No actual pre-SPAWN canonical checkpoint' }
        } else {
            if ($result.loadedStatus -cne 'minecraft:light' -or $result.targetCanonicalPreparations -ne 0 -or $result.populationCalls -ne 1 -or
                    $result.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or $result.fullStatus -cne 'minecraft:full' -or
                    !$result.coldInputs.biomesEqual -or !$result.coldInputs.collisionsEqual -or !$result.coldInputs.transientCollisionSnapshotAbsent -or
                    $result.coldInputs.postPopulationComponentReads -ne 8 -or $result.coldInputs.coldSourcesAfterComponent -ne 8 -or
                    $result.coldInputs.scope -cne 'POST_POPULATION_COMPONENT_READS_NOT_NATURAL_BOUNDARY_COVERAGE') { throw 'Cold same-seed inputs were not reconstructed exactly' }
        }
        return $result
    }
    if ($CompletionOrder -and ($result.completionHandoff.forcedCompletedInputBeforeHandle -ne 1 -or $result.completionHandoff.nativeHolderCompletions -le 0 -or $result.completionHandoff.offOwnerThreadCompletions -ne 0)) { throw 'Native late-registration completion thread ownership was not proven' }
    if ($RuntimeProof) {
        $proof=$result.runtimeProof
        $runtimeSchema = if ($validationTarget -eq '1.21.1-neoforge') { 3 } elseif ($validationTarget -in @('26.3-fabric','26.3-neoforge','26.1.2-neoforge')) { 2 } else { 1 }
        if($proof -and $validationTarget -eq '26.3-fabric' -and
                ($proof.PSObject.Properties.Name -notcontains 'physicalBiomeSections' -or
                $proof.biomeQuartCoverageVersion -ne 1 -or $proof.physicalBiomeCells -ne $proof.physicalBiomeSections*64)){
            throw 'Incomplete physical biome quart coverage; require every cell in every real section'
        }
        if (!$proof -or $proof.schema -ne $runtimeSchema -or !$proof.nativeTickLoop -or !$proof.bothEntityTickingFuturesResolved -or $proof.ticketFullFrontier -ne 49 -or
                !$proof.testFixturesNotCanonicalGenerationCoverage -or $proof.simulationTicks -lt 220 -or
                !$proof.entityCrossedPhysicalSeam -or $proof.entityRegistrations -ne 1 -or
                $proof.entityCallbacks -le 0 -or $proof.blockCallbacks -le 0 -or
                $proof.fluidCallbacks -le 0 -or $proof.furnaceCallbacks -le 0 -or
                !$proof.fluidCrossedPhysicalSeam -or !$proof.furnaceCooked -or !$proof.probeTicketsRemoved -or
                $proof.generationBindings -ne 0) {
            throw 'Incomplete native ticket/tick-loop evidence; intermediate FULL is not runtime PASS'
        }
        if ($runtimeSchema -eq 3) {
            if ($NarrowLocks -and !$VanillaControl -and $productionMode -ne 'reload' -and $proof.narrowContexts -le 0) { throw 'Narrow structure preparation was not actually used' }
            if ($ReuseSources -and !$VanillaControl) {
                if ($productionMode -eq 'reload') {
                    if ($proof.sourceReusePreparations -ne 0) { throw 'FULL reload rebuilt source reuse contexts' }
                } elseif ($proof.sourceReusePreparations -le 0 -or $proof.sourceReuseHits -le 0 -or $proof.sourceReuseFallbacks -ne 0) { throw 'State-safe source reuse was not actually exercised in the native runtime proof' }
            }
            if (($VanillaControl -or $productionMode -eq 'reload') -and $proof.narrowContexts -ne 0) { throw 'Narrow structure preparation escaped into Vanilla/reload' }
            if ($proof.idleTimerResetCalls -ne 1 -or $proof.emptyEntityTicksBeforeFixtures -lt 0) { throw 'Native empty-level activity window was not controlled once at the fixture boundary' }
            if ($proof.regionScopeBound -or $proof.spawnScopeBound) { throw '1.21.1 isolated scope escaped into simulation' }
            if ($VanillaControl) {
                if ($proof.mosaicStateCreations -ne 0 -or $proof.PSObject.Properties.Name -contains 'finalPhysicalMetrics') { throw 'Vanilla runtime entered Mosaic physical state' }
            } elseif ($proof.mosaicStateCreations -le 0 -or $proof.finalPhysicalMetrics.inFlight -ne 0) {
                throw '1.21.1 runtime physical work did not settle'
            }
        } elseif ($proof.spawnBindings -ne 0 -or $proof.finalMaterializer.inFlightCount -ne 0) {
            throw 'Native runtime generation/spawn resources did not settle'
        }
        if (!$VanillaControl -and ($proof.ownerLocalSeed -ne $result.localWorldSeed -or
                $proof.adjacentLocalSeed -eq $proof.ownerLocalSeed)) { throw 'Runtime did not use two distinct physical Mosaic local seeds' }
        if ($runtimeSchema -ge 2 -and ($proof.physicalBiomeChecks -le 0 -or $proof.physicalBiomeOwners -ne 49)) { throw 'Native physical BIOMES dependency readiness across all demanded FULL chunks was not proven' }
        if ($runtimeSchema -ge 2 -and $proof.dependencyPyramid -cne $(if ($productionMode -eq 'reload') { 'loading' } else { 'generation' })) { throw 'Native fresh/loading dependency semantics were not distinguished' }
        if ($runtimeSchema -eq 3 -and $productionMode -eq 'reload' -and !$VanillaControl -and
                ($proof.finalPhysicalMetrics.artifacts -ne 0 -or $proof.finalPhysicalMetrics.biomePreparations -ne 0 -or $proof.finalPhysicalMetrics.spawn -ne 0)) { throw '1.21.1 runtime reload regenerated canonical/SPAWN inputs' }
        if ($runtimeSchema -ne 3 -and ($productionMode -eq 'reload' -or $VanillaControl) -and
                ($proof.finalMaterializer.artifactCaptureCount -ne 0 -or
                 $proof.finalDevelopmentB.generations -ne 0)) { throw 'Runtime/reload generated new canonical state unexpectedly' }
    }
    if ($VanillaControl) {
        if (!$result.normalVanilla -or $result.mode -cne $productionMode -or $result.dimension -cne $Dimension -or
                $result.target -cne "[$ChunkX, $ChunkZ]" -or $result.worldSeed -ne $MasterSeed -or
                $result.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or
                $result.fullStatus -cne 'minecraft:full' -or !$result.lightCorrect -or
                $result.artifactGenerations -ne 0 -or $result.spawnCalls -ne 0) {
            throw 'Ordinary Vanilla FULL/zero-Mosaic-work evidence is incomplete'
        }
        return $result
    }
    if ($productionMode.StartsWith('biomes-')) {
        if ($result.mode -cne $productionMode -or $result.dimension -cne $Dimension -or
                $result.target -cne "[$ChunkX, $ChunkZ]" -or $result.chunkStatus -cne 'minecraft:biomes' -or
                $result.artifactGenerations -ne 0 -or !$result.sameChunkFor16Requests -or
                !(Test-Path -LiteralPath "$output.nbt.gz")) { throw 'Partial BIOMES physical lifecycle evidence is incomplete' }
        if ($productionMode -eq 'biomes-first' -and !$result.concurrentUpgradeExactlyOneArtifact) { throw 'Partial/full upgrade race was not covered' }
        return $result
    }
    if ($result.mode -cne $productionMode -or $result.dimension -cne $Dimension -or
            $result.worldSeed -ne $MasterSeed -or $result.featureOrderingVersion -ne $FeatureOrderingVersion -or
            $result.target -cne "[$ChunkX, $ChunkZ]" -or !$result.lightCorrect -or
            $result.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or
            $result.fullStatus -cne 'minecraft:full') { throw 'Production evidence does not describe the requested physical FULL chunk' }
    if ($DevelopmentB -and (($productionMode -eq 'reload' -and $result.developmentB.generations -ne 0) -or ($productionMode -ne 'reload' -and $result.developmentB.generations -le 0))) { throw 'Development B dispatch/reload was not proven' }
    if ($NaturalProof) {
        $natural=$result.runtimeProof.natural
        $closed=Get-Content (Join-Path $resultRoot "natural-close-$productionMode.json") -Raw|ConvertFrom-Json
        if($natural.schema -ne 1 -or $natural.inputHash -cne $conditions.inputHash -or $natural.probeCreatedPhysicalEntitiesOrBlockEntities -or
                !$natural.nativeBlockEntityLoadControlExact -or $natural.sourceEntities -ne $natural.liveEntities -or $natural.sourceBlockEntities -ne $natural.liveBlockEntities -or
                $natural.capturedPublications -ne $(if($productionMode -ceq 'reload'){0}else{1}) -or
                !$closed.completed -or $closed.inputHash -cne $conditions.inputHash -or $closed.pid -ne $result.runtimeProof.pid -or
                $closed.retainedEntities -ne 0 -or $closed.retainedBlockEntities -ne 0 -or $closed.sourceRetained -or $closed.levelRetained -or $closed.serverRetained){throw 'Incomplete natural worldgen activation/native-close proof'}
        if(($NaturalProof -ceq 'entities' -and ($natural.sourceEntities -le 0 -or $natural.actualEndCrystalTicks -le 0)) -or
                ($NaturalProof -ceq 'block_entities' -and $natural.sourceBlockEntities -le 0)){throw 'Natural positive fixture missing'}
        foreach($name in @('natural-source.nbt',"natural-live-$productionMode.nbt","natural-saved-$productionMode.nbt")){
            if(!(Test-Path (Join-Path $resultRoot $name))){throw 'Missing raw natural worldgen lifecycle evidence'}
        }
    }
    if($PublicationInventory){
        $inventory=Get-Content (Join-Path $resultRoot "publication-inventory-$productionMode.json") -Raw|ConvertFrom-Json
        if($inventory.schema -ne 1 -or $inventory.scope -cne 'PASSIVE_PUBLISHED_INPUT_DISCOVERY_NOT_POSITIVE_ACTIVATION_PROOF' -or
                $inventory.inputHash -cne $conditions.inputHash -or $inventory.dimension -cne $Dimension -or $inventory.masterSeed -ne $MasterSeed -or
                $inventory.pid -ne $result.runtimeProof.pid -or $inventory.retainedOwners -ne 0 -or $inventory.serverRetained -or
                [IO.Path]::GetFullPath($inventory.save) -cne $worldPath -or
                !(Test-Path (Join-Path $resultRoot "publication-inventory-$productionMode.nbt"))){throw 'Incomplete passive publication inventory'}
        if(($productionMode -ceq 'full' -and $inventory.publications -le 0) -or ($productionMode -ceq 'reload' -and $inventory.publications -ne 0)){throw 'Publication discovery/reload boundary was not observed'}
    }
    if ($RequestProof) {
        if($validationTarget -ceq '26.3-neoforge'){
            $proof=Read-NativeRequestSidecar -RawPath $output -RawResult $result
        }else{$proof=$result.requestProof}
        $owners=if($productionMode -eq 'patch'){9}elseif($productionMode -eq 'pair'){2}else{1}
        $requests=if($productionMode -eq 'concurrent'){$Repeats}else{$owners}
        if($proof.schema -ne 1 -or $proof.scope -cne 'ORDINARY_FULL_REQUESTS_NOT_TICKING_OR_PLAYER_ENTRY' -or
                $proof.inputHash -cne $conditions.inputHash -or $proof.logicalProcessors -ne 4 -or $proof.distinctChunkObjects -ne $owners -or $proof.owners.Count -ne $owners -or
                [IO.Path]::GetFullPath($proof.save).TrimEnd([IO.Path]::DirectorySeparatorChar) -cne $worldPath.TrimEnd([IO.Path]::DirectorySeparatorChar) -or
                $proof.bindings -ne 0 -or $proof.spawnBindings -ne 0 -or $result.returnedFullChunks -ne $requests -or
                $result.ordinaryRequestCount -ne $requests -or $result.targetArtifactGenerations -ne 1 -or $result.targetArtifactPublishes -ne 1){throw 'Incomplete ordinary request identity/dedup proof'}
        foreach($owner in $proof.owners){if($owner.artifactCaptures -ne 1 -or $owner.publications -ne 1){throw 'Requested owner was generated more/less than once'}}
    }
    if ($StateProof -and (!$result.stateProof -or $result.stateProof.schema -ne 1 -or
            $result.stateProof.x -ne $ChunkX -or $result.stateProof.z -ne $ChunkZ -or
            $result.stateProof.dimension -cne $Dimension -or $result.stateProof.localSeed -ne $result.localWorldSeed -or
            !(Test-Path -LiteralPath "$output.physical.nbt.gz"))) { throw 'Fresh physical state persistence evidence is missing' }
    return $result
}

try {
    $orchestrator.PriorityClass = 'BelowNormal'
    $orchestrator.ProcessorAffinity = [IntPtr]$CpuAffinity
    Push-Location $repositoryRoot
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    if ($PreserveFailureOnly) {
        if (!(Test-Path -LiteralPath $resultPath) -or (Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json).status -cne 'FAIL') {
            throw 'Explicit failure preservation requires an existing failed result'
        }
        Preserve-RuntimeFailure 'Explicitly retained failed result, world, console and crash evidence; not PASS.'
        Write-Output 'PRESERVED_FAILURE_NOT_PASS'
        return
    }
    if ($validationTarget -eq '1.21.1-neoforge' -and (Test-Path -LiteralPath $worldPath)) {
        $prior = Join-Path $resultRoot "full-$ChunkX-$ChunkZ.json"
        if ((Test-Path -LiteralPath $prior) -and (Get-Content -LiteralPath $prior -Raw | ConvertFrom-Json).status -cne 'PASS') {
            Preserve-RuntimeFailure 'Original 1.21.1 readiness failure retained before retry; not accepted as PASS.'
        }
    }
    if ($RuntimeProof -and (Test-Path -LiteralPath $worldPath)) {
        $priorReload = Join-Path $resultRoot "reload-$ChunkX-$ChunkZ.json"
        if (!(Test-Path -LiteralPath $priorReload) -or (Get-Content -LiteralPath $priorReload -Raw | ConvertFrom-Json).status -cne 'PASS') {
            Preserve-RuntimeFailure 'Prior runtime proof was incomplete; original results, logs and world retained, not accepted as PASS.'
        }
    }
    if ($PartialBiomes -and
            (Test-Path -LiteralPath (Join-Path $resultRoot "biomes-reload-$ChunkX-$ChunkZ.json.nbt.gz")) -and
            !(Test-Path -LiteralPath (Join-Path $resultRoot "biomes-reload-$ChunkX-$ChunkZ.json"))) {
        $archive = Join-Path (Split-Path -Parent $resultRoot) 'partial-biomes-first-failure'
        if (!(Test-Path -LiteralPath $archive)) {
            New-Item -ItemType Directory -Path $archive -Force | Out-Null
            Copy-Item -LiteralPath $resultRoot -Destination (Join-Path $archive 'result') -Recurse
            foreach ($source in @((Join-Path $runRoot 'logs/latest.log'), (Join-Path $runRoot 'crash-reports'), $worldPath)) {
                if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $archive -Recurse }
            }
        }
    }
    if ($EvidenceLabel) {
        $conditions = [ordered]@{
            target = $validationTarget; dimension = $Dimension; x = $ChunkX; z = $ChunkZ; masterSeed = $MasterSeed
            inputHash = Get-ProductionInputHash
            evidenceLabel = $EvidenceLabel; world = $worldPath; result = $resultPath; mode = $Mode
            featureOrdering = $FeatureOrderingVersion; developmentB = $DevelopmentB.IsPresent
            lazyRings = $LazyRings.IsPresent; cpu = 4; affinity = $CpuAffinity
            narrowLocks = $NarrowLocks.IsPresent
            coldSpawn = $ColdSpawn.IsPresent; reuseSources = $ReuseSources.IsPresent
            requestProof = $RequestProof.IsPresent; repeats = $Repeats
            naturalProof = $NaturalProof
            publicationInventory = $PublicationInventory.IsPresent
            playerLifecycle = $PlayerLifecycle.IsPresent
            verifyReload = $VerifyReload.IsPresent; runIdentity = [Guid]::NewGuid().ToString('D')
        }
        [IO.File]::WriteAllText((Join-Path $resultRoot "conditions-$Mode-$ChunkX-$ChunkZ.json"), ($conditions | ConvertTo-Json -Depth 4) + "`n", [Text.UTF8Encoding]::new($false))
    }
    if ($Mode -ne 'reload' -or $VerifyReload) {
        Remove-World
        New-Item -ItemType Directory -Path $worldPath -Force | Out-Null
        if (!$VanillaControl) { Write-HiddenPresetPack }
    } elseif (-not (Test-Path -LiteralPath $worldPath)) {
        throw "Reload mode requires existing world: $worldPath"
    }

    $properties = Set-Property $originalProperties 'level-seed' ([string]$MasterSeed)
    $properties = Set-Property $properties 'level-name' $worldName
    $levelType = if ($VanillaControl) { 'minecraft:normal' } else { 'randomnibble6plus24generator:phase3c2_hidden_mosaic' }
    $properties = Set-Property $properties 'level-type' $levelType
    # Keep the ordinary server lifecycle intact while bounding the unrelated
    # spawn-preparation frontier used by this smoke test.
    $properties = Set-Property $properties 'view-distance' '2'
    $properties = Set-Property $properties 'simulation-distance' '2'
    if ($RuntimeProof -or $PlayerLifecycle) { $properties = Set-Property $properties 'pause-when-empty-seconds' '0' }
    [IO.File]::WriteAllText($propertiesPath, $properties)

    if ($PlayerLifecycle) {
        $first=Invoke-Production 'full' (Join-Path $resultRoot "full-$ChunkX-$ChunkZ.json") $false $false
        $reload=Invoke-Production 'reload' (Join-Path $resultRoot "reload-$ChunkX-$ChunkZ.json") $false $false
        if($first.pid -eq $reload.pid){throw 'Native player save/reload reused the same JVM'}
        'PASS: native server player preparation, returning player, death/respawn and dimension roundtrips; probe transport, not client rendering/handshake.'
    } elseif ($ColdSpawn) {
        $first = Invoke-Production 'stage' (Join-Path $resultRoot 'stage.json') $false $false
        $reload = Invoke-Production 'resume' (Join-Path $resultRoot 'resume.json') $false $false
        if ($first.pid -eq $reload.pid) { throw 'Cold-SPAWN checkpoint reused a JVM' }
        $warm = Get-Content -LiteralPath (Join-Path $resultRoot 'warm-inputs.json') -Raw | ConvertFrom-Json
        if ($warm.status -cne 'minecraft:light' -or $reload.coldInputs.biomes -cne $warm.biomes -or $reload.coldInputs.collisions -cne $warm.collisions) { throw 'Independent cold-SPAWN input hashes differ' }
        Write-ResultSummary $reload
    } elseif ($VerifyReload) {
        if ($PartialBiomes) {
            $partialFirst = Invoke-Production 'biomes-first' (Join-Path $resultRoot "biomes-first-$ChunkX-$ChunkZ.json") $false $false
            $partialReload = Invoke-Production 'biomes-reload' (Join-Path $resultRoot "biomes-reload-$ChunkX-$ChunkZ.json") $false $false
        }
        $firstPath = Join-Path $resultRoot "full-$ChunkX-$ChunkZ.json"
        $first = Invoke-Production 'full' $firstPath ([bool]$Mutate) $false
        $reloadPath = Join-Path $resultRoot "reload-$ChunkX-$ChunkZ.json"
        $reload = Invoke-Production 'reload' $reloadPath $false ([bool]$first.markerWritten)
        if ($VanillaControl -and $first.pid -eq $reload.pid) { throw 'Vanilla save/reload reused its game JVM' }
        if ($RuntimeProof -and $first.runtimeProof.pid -eq $reload.runtimeProof.pid) { throw 'Runtime save/reload reused its game JVM' }
        if ($RuntimeProof -and $validationTarget -eq '26.3-fabric' -and
                ($first.runtimeProof.ownerBiomeHashes|ConvertTo-Json -Compress) -cne ($reload.runtimeProof.ownerBiomeHashes|ConvertTo-Json -Compress)) { throw 'Stored owner biome data changed across native save/reload' }
        if ($reload.artifactGenerations -ne 0 -or $reload.spawnCalls -ne 0) {
            throw "Production reload regenerated canonical data: $($reload | ConvertTo-Json -Depth 8)"
        }
        if (-not $reload.markerPresent -and $Mutate) {
            throw "Production reload did not preserve the modified BlockState"
        }
        if ($StateProof) {
            foreach ($field in @('blocks','biomes','light','heightmaps','structureStarts','structureReferences','externalStarts','ownerLocators','blockEntities','postProcessing','poi')) {
                if (($first.stateProof.$field | ConvertTo-Json -Depth 80 -Compress) -cne
                        ($reload.stateProof.$field | ConvertTo-Json -Depth 80 -Compress)) {
                    throw "Physical persistent state changed on reload: $field; raw chunk NBT retained"
                }
            }
        }
        Write-ResultSummary $reload
    } else {
        $result = Invoke-Production $Mode $resultPath ([bool]$Mutate) $false
        Write-ResultSummary $result
    }
    if(($RequestProof -or $NaturalProof -or $PublicationInventory -or $PlayerLifecycle) -and (Get-ProductionInputHash) -cne $conditions.inputHash){throw 'Source changed during ordinary request proof'}
    $completed = $true
} catch {
    if ($RuntimeProof -or $RequestProof -or $PlayerLifecycle -or $validationTarget -eq '1.21.1-neoforge') { Preserve-RuntimeFailure ($_ | Out-String) }
    throw
} finally {
    [IO.File]::WriteAllText($propertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    $orchestrator.PriorityClass = $originalPriority
    $orchestrator.ProcessorAffinity = $originalAffinity
    if ($completed -and -not $KeepWorld) { Remove-World }
    Pop-Location
}
