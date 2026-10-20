param(
    [ValidateSet('26.3-fabric', '26.3-neoforge')][string]$Target = '26.3-fabric',
    [ValidateSet('b', 'ordering2', 'vanilla')][string]$Pipeline = 'b',
    [ValidateSet('overworld', 'the_nether', 'the_end')][string]$Dimension = 'overworld',
    [int]$View = 8,
    [int]$Simulation = 8,
    [int]$Seconds = 60,
    [switch]$ReferenceStructures,
    [switch]$ReferenceBiomes,
    [switch]$ReferencePhysicalBiomes,
    [switch]$ReferencePhysicalTerrain,
    [switch]$ReferenceBiomeLocks,
    [switch]$StructureReuseAudit,
    [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = ''
)
$ErrorActionPreference = 'Stop'
if ($StructureReuseAudit -and ($Pipeline -ne 'b' -or $ReferenceStructures -or $ReferenceBiomes -or $ReferencePhysicalBiomes)) { throw 'Structure history audit requires the ordinary audited modern B path' }
$env:MOSAIC_VALIDATION_TARGET = $Target
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
if ($ReferenceStructures -and $Pipeline -ne 'b') { throw 'Reference source evaluation is only a B comparison' }
if ($ReferenceBiomes -and ($Pipeline -ne 'b' -or $ReferenceStructures)) { throw 'Choose a single B reference control' }
if ($ReferencePhysicalBiomes -and ($Pipeline -ne 'b' -or $ReferenceStructures -or $ReferenceBiomes)) { throw 'Physical BIOMES A/B is a separate Vanilla 26.3 B candidate' }
if ($ReferencePhysicalTerrain -and ($Pipeline -ne 'b' -or $ReferenceStructures -or $ReferenceBiomes -or $ReferencePhysicalBiomes -or $ReferenceBiomeLocks -or $StructureReuseAudit)) { throw 'Physical TERRAIN A/B is a separate native 26.3 preparation candidate' }
if ($ReferenceBiomeLocks -and ($Target -ne '26.3-fabric' -or $Pipeline -ne 'b' -or $ReferenceStructures -or $ReferenceBiomes -or $ReferencePhysicalBiomes -or $StructureReuseAudit)) { throw 'Physical biome lock control is a separate Fabric B measurement' }
$label = if ($ReferencePhysicalBiomes) { 'b-physical-biome-reference' } elseif ($ReferenceBiomes) { 'b-biome-reference' } elseif ($ReferenceStructures) { 'b-source-reference' } else { $Pipeline }
if ($StructureReuseAudit) { $label = 'b-source-audit' }
if ($ReferenceBiomeLocks) { $label = 'b-biome-lock-reference' }
if ($ReferencePhysicalTerrain) { $label = 'b-physical-terrain-reference' }
if ($Dimension -ne 'overworld') { $label += "-$Dimension" }
if ($Seconds -ne 60) { $label += "-duration$Seconds" }
$output = Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/$validationTarget/$label-v$View-s$Simulation"
$worldName = "b-milestone-$label-v$View-s$Simulation"
if($EvidenceLabel){
    $output=Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/$validationTarget/$EvidenceLabel/$label-v$View-s$Simulation"
    $worldName+='-'+$EvidenceLabel
}
$world = Join-Path $validationServer $worldName
if($EvidenceLabel -and ((Test-Path $output) -or (Test-Path $world))){throw 'Preserve scoped performance evidence and world; never overwrite it'}
function Get-PerformanceInputHash {
    $sources=@(Get-ChildItem (Join-Path $validationRepository "versions/$validationTarget/src/main"),(Join-Path $validationRepository "versions/$validationTarget/src/probe"),(Join-Path $validationRepository 'components/worldgen_contract/src') -File -Recurse|Sort-Object FullName)
    $text=($sources|ForEach-Object{$_.FullName.Substring($validationRepository.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $text+=[IO.File]::ReadAllText((Join-Path $validationRepository "versions/$validationTarget/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
}
$inputHash=Get-PerformanceInputHash
$previousOptions = $env:JAVA_TOOL_OPTIONS
$previousProperties = [IO.File]::ReadAllText($validationProperties)
$process = [Diagnostics.Process]::GetCurrentProcess()
$previousPriority = $process.PriorityClass
$previousAffinity = $process.ProcessorAffinity
$completed = $false
$crashDirectory = Join-Path $validationServer 'crash-reports'
$existingCrashes = @{}
if (Test-Path -LiteralPath $crashDirectory) {
    Get-ChildItem -LiteralPath $crashDirectory -File | ForEach-Object { $existingCrashes[$_.Name] = (Get-FileHash -LiteralPath $_.FullName).Hash }
}
try {
    $process.PriorityClass = 'BelowNormal'
    $process.ProcessorAffinity = 3840
    foreach ($path in @($output, $world)) { if (Test-Path -LiteralPath $path) { Move-ValidationToRecycleBin -LiteralPath $path } }
    New-Item -ItemType Directory -Path $output,$world -Force | Out-Null
    $levelType = 'minecraft:normal'
    if ($Pipeline -ne 'vanilla') {
        $pack = Join-Path $world 'datapacks/phase3c2-hidden-mosaic'
        New-Item -ItemType Directory -Path (Split-Path -Parent $pack) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $validationRepository 'validations/performance-worldgen/task/hidden_mosaic') -Destination $pack -Recurse
        [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'), $validationPackMetadata, [Text.UTF8Encoding]::new($false))
        $presetPath = Join-Path $pack 'data/randomnibble6plus24generator/worldgen/world_preset/phase3c2_hidden_mosaic.json'
        $preset = Get-Content -LiteralPath $presetPath -Raw | ConvertFrom-Json
        foreach ($presetDimension in $preset.dimensions.PSObject.Properties) {
            $presetDimension.Value.generator.mosaic_profile.feature_ordering_algorithm_version = 2
            if ($Pipeline -eq 'b') { $presetDimension.Value.generator.type = 'randomnibble6plus24generator:development_b' }
        }
        [IO.File]::WriteAllText($presetPath, ($preset | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
        $levelType = 'randomnibble6plus24generator:phase3c2_hidden_mosaic'
    }
    if ($Pipeline -eq 'b') {
        [IO.File]::WriteAllText((Join-Path $world 'mosaic-development-b.json'), '{"schema":1,"pipeline":"26.3-lightweight-b-1"}', [Text.UTF8Encoding]::new($false))
    }
    $conditions = [ordered]@{ pipeline=$Pipeline; dimension="minecraft:$Dimension"; referenceStructureSources=$ReferenceStructures.IsPresent; referenceBiomes=$ReferenceBiomes.IsPresent; structures='baseline'; masterSeed=123456789; x=125; z=-37; view=$View; simulation=$Simulation; seconds=$Seconds; moves=2; moveSeconds=15; moveChunks=2; cpu=4; affinity=3840; priority='BelowNormal'; cliFrontierNotPlayerSpawn=$true }
    $conditions.target = $validationTarget
    $conditions.referencePhysicalBiomes = $ReferencePhysicalBiomes.IsPresent
    if ($ReferencePhysicalTerrain) { $conditions.referencePhysicalTerrain = $true }
    if ($ReferenceBiomeLocks) { $conditions.referenceBiomeLocks = $true }
    if ($StructureReuseAudit) { $conditions.structureReuseAudit = $true }
    $conditions.runIdentity = [Guid]::NewGuid().ToString('D')
    $conditions.inputHash = $inputHash
    [IO.File]::WriteAllText((Join-Path $output 'conditions.json'), ($conditions | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $world 'DISPOSABLE_STRUCTURE_RESEARCH.json'), ($conditions | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($validationProperties, "level-name=$worldName`nlevel-seed=123456789`nlevel-type=$levelType`nserver-ip=127.0.0.1`nserver-port=25589`nonline-mode=false`nview-distance=$View`nsimulation-distance=$Simulation`npause-when-empty-seconds=-1`n", [Text.UTF8Encoding]::new($false))
    $flags = @('-XX:ActiveProcessorCount=4', "-Dmosaic.profile.output=$($output.Replace('\','/'))", "-Dmosaic.profile.label=$worldName", '-Dmosaic.profile.workload=frontier', "-Dmosaic.profile.dimension=minecraft:$Dimension", "-Dmosaic.profile.seconds=$Seconds", '-Dmosaic.profile.moves=2', '-Dmosaic.profile.moveSeconds=15', '-Dmosaic.profile.moveChunks=2', '-Dmosaic.profile.fixedTrajectory=true')
    if ($Pipeline -eq 'b') { $flags += '-Drandomnibble6plus24generator.developmentB=true' }
    if ($ReferenceStructures) { $flags += '-Dmosaic.profile.bReferenceStructures=true' }
    if ($ReferenceBiomes) { $flags += '-Dmosaic.profile.bReferenceBiomes=true' }
    if ($ReferencePhysicalBiomes) { $flags += '-Dmosaic.profile.bReferencePhysicalBiomes=true' }
    if ($ReferencePhysicalTerrain) { $flags += '-Dmosaic.profile.bReferencePhysicalTerrain=true' }
    if ($ReferenceBiomeLocks) { $flags += '-Dmosaic.profile.referenceBiomeLocks=true' }
    if ($StructureReuseAudit) { $flags += '-Dmosaic.profile.structureReuseAudit=true' }
    $env:JAVA_TOOL_OPTIONS = $flags -join ' '
    $runArguments = @('--max-workers=1', '--no-daemon', '--no-configuration-cache', '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4')
    if ($Target -eq '26.3-fabric') { $runArguments += "--args=nogui --world $worldName --port 0" }
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationPerformanceWorldgenServer" @runArguments *> (Join-Path $output 'console.log')
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $output 'checkpoint.jfr'))) {
        Get-Content (Join-Path $output 'console.log') -Tail 60
        throw 'B performance workload did not complete'
    }
    if (Test-Path -LiteralPath $crashDirectory) {
        foreach ($report in Get-ChildItem -LiteralPath $crashDirectory -File) {
            if (!$existingCrashes.ContainsKey($report.Name) -or $existingCrashes[$report.Name] -cne (Get-FileHash -LiteralPath $report.FullName).Hash) {
                throw "Performance world produced a crash report: $($report.FullName)"
            }
        }
    }
    if((Get-PerformanceInputHash) -cne $inputHash){throw 'Target source changed during performance measurement'}
    $completed = $true
    "MEASURED $output"
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    [IO.File]::WriteAllText($validationProperties, $previousProperties, [Text.UTF8Encoding]::new($false))
    $process.PriorityClass = $previousPriority
    $process.ProcessorAffinity = $previousAffinity
    if ($completed -and (Test-Path -LiteralPath $world)) { Move-ValidationToRecycleBin -LiteralPath $world }
}
