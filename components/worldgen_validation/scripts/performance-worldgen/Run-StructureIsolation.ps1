param(
    [ValidateSet('baseline','no-stronghold','no-other','none')][string]$Structures='baseline',
    [string]$Label='',
    [int]$View=2,[int]$Simulation=2,[int]$Seconds=60,
    [int]$MoveSeconds=15,[int]$MoveChunks=2,[int]$Moves=2,
    [ValidateSet('off','oracle','lazy')][string]$LazyStructure='off',
    [ValidateSet('off','oracle','production')][string]$RepairC='off',
    [ValidateSet('off','baseline','candidate')][string]$RepairD1='off',
    [switch]$NormalResources
)
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$run=Join-Path $repo 'validations\performance-worldgen\instance\26.2-fabric\server'
if(-not $Label){$Label="structures-$Structures-v$View-"+[guid]::NewGuid().ToString('N').Substring(0,8)}
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Unsafe research label'}
$world=Join-Path $run "DISPOSABLE-$Label"
$output=Join-Path $repo "validations\performance-worldgen\result\current\structure_isolation\$Structures-v$View-s$Simulation-$LazyStructure-$RepairC-$RepairD1"
if((Test-Path -LiteralPath $world)){throw 'Every experiment requires fresh world and output'}
New-Item -ItemType Directory -Path (Join-Path $world 'datapacks'),$output -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'validations\performance-worldgen\task\hidden_mosaic') -Destination (Join-Path $world 'datapacks\phase3c2-hidden-mosaic') -Recurse
$manifest=[ordered]@{disposable=$true;productionBaseline=(git -C $repo rev-parse HEAD);masterSeed=123456789;dimension='minecraft:overworld';target='125,-37';structures=$Structures;lazy=$LazyStructure;repairC=$RepairC;candidatePruning=$RepairD1;view=$View;simulation=$Simulation;seconds=$Seconds;moveSeconds=$MoveSeconds;moveChunks=$MoveChunks;moves=$Moves;activeProcessorCount=$(if($NormalResources){[Environment]::ProcessorCount}else{4});affinityMask=$(if($NormalResources){'inherited'}else{'0xF00'});priority=$(if($NormalResources){'inherited'}else{'BelowNormal'});gradleWorkers=2;simultaneousTestInstances=1;created=[DateTime]::UtcNow.ToString('o')}
[IO.File]::WriteAllText((Join-Path $world 'DISPOSABLE_STRUCTURE_RESEARCH.json'),($manifest|ConvertTo-Json))
[IO.File]::WriteAllText((Join-Path $output 'conditions.json'),($manifest|ConvertTo-Json))
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=DISPOSABLE-$Label
level-seed=123456789
level-type=randomnibble6plus24generator:phase3c2_hidden_mosaic
server-ip=127.0.0.1
server-port=25589
online-mode=true
view-distance=$View
simulation-distance=$Simulation
pause-when-empty-seconds=-1
max-tick-time=60000
"@)
$previous=$env:JAVA_TOOL_OPTIONS
$researchProcess=[Diagnostics.Process]::GetCurrentProcess()
$previousAffinity=$researchProcess.ProcessorAffinity
$previousPriority=$researchProcess.PriorityClass
try{
    # CLI test process tree only: logical CPUs 8..11 on this research workstation.
    if(-not $NormalResources){
        $researchProcess.ProcessorAffinity=[IntPtr]0xF00
        $researchProcess.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    }
    Push-Location $repo
    $flags=@(('-Dmosaic.profile.output="{0}"' -f $output.Replace('\','/')),"-Dmosaic.profile.label=$Label",'-Dmosaic.profile.workload=frontier',"-Dmosaic.profile.seconds=$Seconds","-Dmosaic.profile.moves=$Moves","-Dmosaic.profile.moveSeconds=$MoveSeconds","-Dmosaic.profile.moveChunks=$MoveChunks",'-Dmosaic.profile.structureExperiment=true',"-Dmosaic.profile.structures=$Structures",('-Dmosaic.profile.structureRoot="{0}"' -f $run.Replace('\','/')))
    if($LazyStructure -ne 'off'){$flags+='-Dmosaic.lazy.experiment=true'}
    if($LazyStructure -eq 'lazy'){$flags+='-Dmosaic.lazy.enabled=true'}
    if($RepairC -ne 'off'){$flags+='-Dmosaic.repairC=true'}
    if($RepairC -eq 'oracle'){$flags+='-Dmosaic.repairC.oracle=true'}
    if($RepairD1 -ne 'off'){$flags+='-Dmosaic.repairC=true';$flags+='-Dmosaic.lazy.experiment=true';$flags+='-Dmosaic.candidate.study=true'}
    if($RepairD1 -eq 'baseline'){$flags+='-Dmosaic.candidate.oracle=true'}
    if($NormalResources){$flags+='-Dmosaic.profile.maxCpu=0.80'}
    if($NormalResources){$env:JAVA_TOOL_OPTIONS=$flags -join ' '}
    else{$env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 '+($flags -join ' ')}
    & (Join-Path $repo 'gradlew.bat') '--no-daemon' '--no-parallel' '--max-workers=2' ':version:26.2-fabric:runValidationPerformanceWorldgenServer' "--args=nogui --world DISPOSABLE-$Label" *> (Join-Path $output 'console.log')
    if(-not (Test-Path -LiteralPath (Join-Path $output 'checkpoint.jfr'))){throw "No complete measurement window: $output"}
    $log=Get-Content -Raw -LiteralPath (Join-Path $output 'console.log')
    if($log -match 'Disabled (ring|structure)|before research save validation'){throw "Structure isolation assertion failed: $output"}
    "MEASURED $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$researchProcess.ProcessorAffinity=$previousAffinity;$researchProcess.PriorityClass=$previousPriority;Pop-Location}
