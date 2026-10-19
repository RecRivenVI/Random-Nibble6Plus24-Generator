param(
    [ValidateSet('vanilla','b_eager','b_ring','b_narrow','b_reuse')][string]$Pipeline='vanilla',
    [ValidateSet('overworld','the_nether','the_end')][string]$Dimension='overworld',
    [ValidateSet(8,12,16)][int]$View=8,
    [ValidateSet('screen','source_screen','source_observe','pair_a','pair_b','pair_c','collision_screen','collision_pair_a','collision_pair_b','collision_pair_c')][string]$Case='screen',
    [ValidateSet(60,180)][int]$ObserveSeconds=60,
    [switch]$CheckOnly,
    [switch]$Offline,
    [long]$CpuAffinity=3840
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$env:MOSAIC_VALIDATION_TARGET='1.21.1-neoforge'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$target=$validationTarget
$modId=(ConvertFrom-StringData (Get-Content (Join-Path $validationRepository 'gradle.properties') -Raw)).mod_id
$caseName=$Case+$(if($ObserveSeconds -ne 60){"-duration$ObserveSeconds"}else{''})
$output=Join-Path $validationRepository "validations/performance-worldgen/result/legacy_frontier_cold/$target/$Dimension/v$View/$caseName/$Pipeline"
$worldName="legacy-cold-perf-$Pipeline-$Dimension-v$View-$caseName"
$world=Join-Path $validationServer $worldName
$sourceFiles=@(Get-ChildItem (Join-Path $validationRepository "versions/$target/src"),(Join-Path $validationRepository 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
$fingerprint=($sourceFiles|ForEach-Object {$_.FullName.Substring($validationRepository.Length)+':'+(Get-FileHash -LiteralPath $_.FullName).Hash}) -join "`n"
$fingerprint += [IO.File]::ReadAllText((Join-Path $validationRepository "versions/$target/target.properties"))
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))

function Check-Measurement {
    $conditions=Get-Content -LiteralPath (Join-Path $output 'conditions.json') -Raw|ConvertFrom-Json
    $row=Get-Content -LiteralPath (Join-Path $output 'summary.json') -Raw|ConvertFrom-Json
    if($conditions.inputHash -cne $hash -or $row.inputHash -cne $hash -or $row.status -cne 'MEASURED' -or $row.schema -ne 2 -or
            $row.tokenRespawnSkips -ne 1 -or !$row.coldAfterToken -or
            $row.target -cne $target -or $row.dimension -cne "minecraft:$Dimension" -or $row.view -ne $View -or $row.simulation -ne $View -or
            $row.worldSeed -ne 123456789 -or $row.mosaic -ne ($Pipeline -ne 'vanilla') -or
            $conditions.seconds -ne $ObserveSeconds -or $row.seconds -lt $ObserveSeconds -or $row.moves -ne 2 -or
            $row.cpus -ne 4 -or !$row.nativePlayerTickets -or !$row.cliFrontierNotPlayerOrPacket -or !(Test-Path (Join-Path $output 'checkpoint.jfr'))) {throw 'Incomplete native performance window'}
    if($Pipeline -eq 'vanilla' -and ($row.contexts -ne 0 -or $row.narrowContexts -ne 0 -or $row.structureCalls -ne 0 -or @($row.chunks|Where-Object {$_.work.Count -gt 0 -or $_.canonicalInvocations -gt 0}).Count -gt 0)){throw 'Vanilla entered Mosaic generation'}
    if($Pipeline -in @('b_narrow','b_reuse') -and $row.narrowContexts -le 0){throw 'Narrow candidate was not actually used'}
    if($Pipeline -notin @('b_narrow','b_reuse') -and $row.narrowContexts -ne 0){throw 'Unexpected narrow candidate'}
    if($Pipeline -eq 'b_reuse' -and ($row.sourceReuseHits -le 0 -or $row.sourceReuseEvaluations -le 0)){throw 'Scoped source reuse was not actually used'}
    if($Pipeline -eq 'b_reuse' -and $Dimension -ne 'the_end' -and $row.sourceStateReplays -ne $row.sourceReuseHits){throw 'Native biome-search continuation was not actually replayed'}
    if($Case -eq 'collision_screen' -and $Pipeline -eq 'b_reuse' -and
            $row.samples[-1].physicalMetrics.populationCollisionReads -le 0){throw 'Population collision regression was not positively covered'}
    if($Case -like 'collision_*' -and $Pipeline -eq 'b_reuse' -and
            $row.samples[-1].physicalMetrics.populationColdCollisionSources -ne 0){throw 'Fresh population unexpectedly rebuilt terrain instead of detached inputs'}
    if($Pipeline -ne 'b_reuse' -and ($row.sourceReuseHits -ne 0 -or $row.sourceReuseEvaluations -ne 0)){throw 'Unexpected structure source reuse'}
    & python (Join-Path $PSScriptRoot 'summarize_legacy_performance.py') $output
    if($LASTEXITCODE -ne 0){throw 'Native timeline reduction failed'}
    "MEASURED (not correctness PASS): $output"
}
if($CheckOnly){Check-Measurement;exit}
$process=[Diagnostics.Process]::GetCurrentProcess();$oldAffinity=$process.ProcessorAffinity;$oldPriority=$process.PriorityClass
if([Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($oldAffinity.ToInt64() -band $CpuAffinity) -ne $CpuAffinity){throw 'Four available logical CPUs required'}
$oldOptions=$env:JAVA_TOOL_OPTIONS;$oldProperties=[IO.File]::ReadAllText($validationProperties)
$startedRun=$false
try {
    $games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'"|Where-Object {$_.CommandLine -like "*$validationRepository*" -and $_.CommandLine -match 'RunProgramArgs\.txt|KnotServer|KnotClient'})
    if($games.Count){throw 'Performance requires no other repository game/correctness JVM'}
    $process.ProcessorAffinity=[IntPtr]$CpuAffinity;$process.PriorityClass='BelowNormal'
    if(Test-Path $world){throw 'Preserve the existing performance world before rerunning'}
    if(Test-Path $output){
        $summary=Join-Path $output 'summary.json'
        if(!(Test-Path $summary) -or (Get-Content $summary -Raw|ConvertFrom-Json).status -cne 'MEASURED'){throw 'Preserve incomplete/failed performance evidence before rerunning'}
        Move-ValidationToRecycleBin -LiteralPath $output
    }
    New-Item -ItemType Directory -Force -Path $output,$world|Out-Null
    $startedRun=$true
    $levelType='minecraft:normal'
    if($Pipeline -ne 'vanilla'){
        $pack=Join-Path $world 'datapacks/hidden-mosaic-performance'
        New-Item -ItemType Directory -Path (Split-Path -Parent $pack) -Force|Out-Null
        Copy-Item -LiteralPath (Join-Path $validationRepository 'validations/performance-worldgen/task/hidden_mosaic') -Destination $pack -Recurse
        [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),$validationPackMetadata)
        $presetPath=Join-Path $pack "data/$modId/worldgen/world_preset/phase3c2_hidden_mosaic.json"
        $preset=Get-Content $presetPath -Raw|ConvertFrom-Json
        foreach($presetDimension in $preset.dimensions.PSObject.Properties){
            $presetDimension.Value.generator.type="${modId}:development_b"
            $presetDimension.Value.generator.mosaic_profile.feature_ordering_algorithm_version=2
        }
        [IO.File]::WriteAllText($presetPath,($preset|ConvertTo-Json -Depth 20))
        [IO.File]::WriteAllText((Join-Path $world 'mosaic-development-b.json'),'{"schema":1,"pipeline":"1.21.1-lightweight-b-1"}')
        $levelType="${modId}:phase3c2_hidden_mosaic"
    }
    $conditions=@{target=$target;inputHash=$hash;pipeline=$Pipeline;dimension="minecraft:$Dimension";view=$View;simulation=$View;seconds=$ObserveSeconds;masterSeed=123456789;x=125;z=-37;moves=2;moveChunks=2;moveSeconds=15;cpu=4;affinity=$CpuAffinity;priority='BelowNormal';case=$caseName;runIdentity=[Guid]::NewGuid().ToString('D');cliFrontierNotPlayerOrPacket=$true}
    [IO.File]::WriteAllText((Join-Path $output 'conditions.json'),($conditions|ConvertTo-Json))
    [IO.File]::WriteAllText($validationProperties,"level-name=$worldName`nlevel-seed=123456789`nlevel-type=$levelType`nserver-ip=127.0.0.1`nserver-port=25589`nonline-mode=false`nview-distance=$View`nsimulation-distance=$View`n")
    $prefix='mosaic.legacyPerformance.'
    $flags=@('-XX:ActiveProcessorCount=4',"-D${prefix}enabled=true","-D${prefix}output=$($output.Replace('\','/'))","-D${prefix}dimension=minecraft:$Dimension","-D${prefix}view=$View","-D${prefix}simulation=$View","-D${prefix}seconds=$ObserveSeconds","-D${prefix}inputHash=$hash","-D${prefix}mosaic=$($Pipeline -ne 'vanilla')")
    if($Pipeline -ne 'vanilla'){$flags+="-D${modId}.developmentB=true"}
    if($Pipeline -in @('b_ring','b_narrow','b_reuse')){$flags+="-D${modId}.structure.lazyRings=true"}
    if($Pipeline -in @('b_narrow','b_reuse')){$flags+="-D${modId}.structure.narrowLocks=true"}
    if($Pipeline -eq 'b_reuse'){$flags+="-D${modId}.structure.reuseSources=true"}
    $env:JAVA_TOOL_OPTIONS=$flags -join ' '
    $crashes=@{};Get-ChildItem (Join-Path $validationServer 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object {$crashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
    [string[]]$network=if($Offline){@('--offline')}else{@()}
    & (Join-Path $validationRepository 'gradlew.bat') ":version:${target}:runValidationPerformanceWorldgenServer" @network --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> (Join-Path $output 'console.log')
    if($LASTEXITCODE -ne 0){Get-Content (Join-Path $output 'console.log') -Tail 30;throw 'Native performance server failed'}
    Get-ChildItem (Join-Path $validationServer 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object {if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New performance crash: $($_.Name)"}}
    Check-Measurement
    & java '-XX:ActiveProcessorCount=4' (Join-Path $PSScriptRoot 'AnalyzeRecording.java') (Join-Path $output 'checkpoint.jfr') (Join-Path $output 'jfr-analysis.json')
    if($LASTEXITCODE -ne 0){throw 'JFR analysis failed'}
    Move-ValidationToRecycleBin -LiteralPath $world
} catch {
    if(!$startedRun){throw}
    $first=Join-Path $output 'first_failure'
    if(!(Test-Path $first)){
        New-Item -ItemType Directory -Path $first -Force|Out-Null
        foreach($path in @((Join-Path $validationServer 'logs/latest.log'),(Join-Path $validationServer 'crash-reports'),$world)){if(Test-Path $path){Copy-Item -LiteralPath $path -Destination $first -Recurse}}
        [IO.File]::WriteAllText((Join-Path $first 'failure.txt'),($_|Out-String))
    }
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS=$oldOptions;[IO.File]::WriteAllText($validationProperties,$oldProperties)
    $process.ProcessorAffinity=$oldAffinity;$process.PriorityClass=$oldPriority
}
