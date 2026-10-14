param(
    [ValidateSet('b', 'ordering2', 'vanilla')][string]$Pipeline = 'b',
    [int]$View = 8,
    [int]$Simulation = 8,
    [int]$Seconds = 60,
    [switch]$ReferenceStructures
)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
if ($ReferenceStructures -and $Pipeline -ne 'b') { throw 'Reference source evaluation is only a B comparison' }
$label = if ($ReferenceStructures) { 'b-source-reference' } else { $Pipeline }
$output = Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/26.3-fabric/$label-v$View-s$Simulation"
$worldName = "b-milestone-$label-v$View-s$Simulation"
$world = Join-Path $validationServer $worldName
$previousOptions = $env:JAVA_TOOL_OPTIONS
$previousProperties = [IO.File]::ReadAllText($validationProperties)
$process = [Diagnostics.Process]::GetCurrentProcess()
$previousPriority = $process.PriorityClass
$previousAffinity = $process.ProcessorAffinity
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
        foreach ($dimension in $preset.dimensions.PSObject.Properties) {
            $dimension.Value.generator.mosaic_profile.feature_ordering_algorithm_version = 2
            if ($Pipeline -eq 'b') { $dimension.Value.generator.type = 'randomnibble6plus24generator:development_b' }
        }
        [IO.File]::WriteAllText($presetPath, ($preset | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
        $levelType = 'randomnibble6plus24generator:phase3c2_hidden_mosaic'
    }
    if ($Pipeline -eq 'b') {
        [IO.File]::WriteAllText((Join-Path $world 'mosaic-development-b.json'), '{"schema":1,"pipeline":"26.3-lightweight-b-1"}', [Text.UTF8Encoding]::new($false))
    }
    $conditions = [ordered]@{ pipeline=$Pipeline; referenceStructureSources=$ReferenceStructures.IsPresent; structures='baseline'; masterSeed=123456789; x=125; z=-37; view=$View; simulation=$Simulation; seconds=$Seconds; moves=2; moveSeconds=15; moveChunks=2; cpu=4; affinity=3840; priority='BelowNormal'; cliFrontierNotPlayerSpawn=$true }
    [IO.File]::WriteAllText((Join-Path $output 'conditions.json'), ($conditions | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $world 'DISPOSABLE_STRUCTURE_RESEARCH.json'), ($conditions | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($validationProperties, "level-name=$worldName`nlevel-seed=123456789`nlevel-type=$levelType`nserver-ip=127.0.0.1`nserver-port=25589`nonline-mode=false`nview-distance=$View`nsimulation-distance=$Simulation`npause-when-empty-seconds=-1`n", [Text.UTF8Encoding]::new($false))
    $flags = @('-XX:ActiveProcessorCount=4', "-Dmosaic.profile.output=$($output.Replace('\','/'))", "-Dmosaic.profile.label=$worldName", '-Dmosaic.profile.workload=frontier', "-Dmosaic.profile.seconds=$Seconds", '-Dmosaic.profile.moves=2', '-Dmosaic.profile.moveSeconds=15', '-Dmosaic.profile.moveChunks=2', '-Dmosaic.profile.fixedTrajectory=true')
    if ($Pipeline -eq 'b') { $flags += '-Drandomnibble6plus24generator.developmentB=true' }
    if ($ReferenceStructures) { $flags += '-Dmosaic.profile.bReferenceStructures=true' }
    $env:JAVA_TOOL_OPTIONS = $flags -join ' '
    & (Join-Path $validationRepository 'gradlew.bat') :version:26.3-fabric:runValidationPerformanceWorldgenServer --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' "--args=nogui --world $worldName" *> (Join-Path $output 'console.log')
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath (Join-Path $output 'checkpoint.jfr'))) {
        Get-Content (Join-Path $output 'console.log') -Tail 60
        throw 'B performance workload did not complete'
    }
    "MEASURED $output"
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    [IO.File]::WriteAllText($validationProperties, $previousProperties, [Text.UTF8Encoding]::new($false))
    $process.PriorityClass = $previousPriority
    $process.ProcessorAffinity = $previousAffinity
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
}
