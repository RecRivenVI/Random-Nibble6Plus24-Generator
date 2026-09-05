param(
    [ValidateSet('cancel','reload','error','unexpected','close-error','close-unexpected','vanilla')][string]$Mode='cancel',
    [string]$World='', [string]$Reference='', [int]$TestProcessors=4, [long]$CpuAffinity=0
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$run=Join-Path $repo 'runs\26.2-fabric\generation-shutdown'
if(-not $World){$World='shutdown-'+[guid]::NewGuid().ToString('N').Substring(0,12)}
if($World -notmatch '^shutdown-[a-zA-Z0-9-]+$'){throw 'Unsafe world name'}
$worldPath=Join-Path $run $World
$output=Join-Path $repo "versions\26.2-fabric\build\generation-shutdown\$World-$Mode"
New-Item -ItemType Directory -Path $run,$output -Force|Out-Null
$eula=Join-Path $repo 'runs\26.2-fabric\server\eula.txt'
if(-not(Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)){throw 'Existing accepted EULA required'}
Copy-Item -LiteralPath $eula -Destination (Join-Path $run 'eula.txt')
$levelType='minecraft:normal'
if($Mode -eq 'reload'){
    if(-not(Test-Path -LiteralPath (Join-Path $worldPath 'level.dat')) -or -not(Test-Path -LiteralPath $Reference)){throw 'Reload needs a saved fixture and reference'}
    $levelType='randomnibble6plus24generator:shutdown_test'
}else{
    if(Test-Path -LiteralPath $worldPath){throw 'Do not overwrite prior worlds'}
    New-Item -ItemType Directory -Path $worldPath|Out-Null
    if($Mode -ne 'vanilla'){
        $levelType='randomnibble6plus24generator:shutdown_test'
        $pack=Join-Path $worldPath 'datapacks\shutdown-test'
        $preset=Join-Path $pack 'data\randomnibble6plus24generator\worldgen\world_preset'
        New-Item -ItemType Directory -Path $preset -Force|Out-Null
        [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),'{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Generation shutdown regression"}}')
        $profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=1;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
        $dimensions=[ordered]@{}
        foreach($dimension in @('overworld','the_nether','the_end')){
            $settings=switch($dimension){'the_nether'{'nether'} 'the_end'{'end'} default{'overworld'}}
            $biomes=if($dimension -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$settings"}}
            $dimensions["minecraft:$dimension"]=@{type="minecraft:$dimension";generator=@{type='randomnibble6plus24generator:mosaic';biome_source=$biomes;settings="minecraft:$settings";mosaic_profile=$profile}}
        }
        [IO.File]::WriteAllText((Join-Path $preset 'shutdown_test.json'),(@{dimensions=$dimensions}|ConvertTo-Json -Depth 12))
    }
}
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$World
level-seed=123456789
level-type=$levelType
server-ip=127.0.0.1
server-port=25585
online-mode=true
view-distance=2
simulation-distance=2
"@)
$p=[Diagnostics.Process]::GetCurrentProcess();$affinity=$p.ProcessorAffinity;$priority=$p.PriorityClass;$previous=$env:JAVA_TOOL_OPTIONS
try{
    if($CpuAffinity -ne 0){$p.ProcessorAffinity=[IntPtr]$CpuAffinity}
    $p.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    Push-Location $repo
    $env:JAVA_TOOL_OPTIONS=('-XX:ActiveProcessorCount={0} -Dmosaic.shutdown.test.mode={1} -Dmosaic.shutdown.test.output="{2}" -Dmosaic.shutdown.test.reference="{3}"' -f $TestProcessors,$Mode,$output.Replace('\','/'),$Reference.Replace('\','/'))
    & .\gradlew.bat --no-daemon --no-parallel --max-workers=2 ':versions:26.2-fabric:runServer' '-PgenerationShutdownTest' "--args=nogui --world $World" *> (Join-Path $output 'console.log')
    $log=Get-Content -Raw -LiteralPath (Join-Path $output 'console.log')
    $fault=$Mode -in @('error','unexpected','close-error','close-unexpected')
    if($fault){
        $observed=Get-Content -Raw -LiteralPath (Join-Path $output 'observed-crash.json')|ConvertFrom-Json
        if($observed.crashReports -lt 1 -or $observed.reportedRoot -notmatch 'EXPLICIT_|UNEXPECTED_'){throw 'Real failure was not reported'}
        $finished=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
        if($finished.status -ne 'PASS' -or $finished.inFlightAfterClose -ne 0 -or $finished.bindingsAfterClose -ne 0){throw 'Fault path did not finish cleanup'}
        "PASS expected real-error report: $output"
    }else{
        if($LASTEXITCODE -ne 0 -or $log -match 'Encountered an unexpected exception|Reported exception thrown|Exception stopping the server|Nothing else should replace'){
            throw "Unexpected shutdown failure: $output"
        }
        $result=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
        if($result.status -ne 'PASS' -or $result.crashReports -ne 0){throw 'Shutdown regression failed'}
        "PASS $output"
    }
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$p.ProcessorAffinity=$affinity;$p.PriorityClass=$priority;Pop-Location}
