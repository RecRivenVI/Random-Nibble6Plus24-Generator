param([int]$TestProcessors=4,[long]$CpuAffinity=0)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$run=Join-Path $repo 'runs\26.2-fabric\stronghold-test'
$label='stronghold-life-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$output=Join-Path $repo "versions\26.2-fabric\build\stronghold-test\$label"
$preset=Join-Path $run "$label\datapacks\stronghold-test\data\randomnibble6plus24generator\worldgen\world_preset"
New-Item -ItemType Directory -Path $preset,$output -Force|Out-Null
$eula=Join-Path $repo 'runs\26.2-fabric\server\eula.txt'
if(-not(Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)){throw 'Existing accepted development EULA required'}
Copy-Item -LiteralPath $eula -Destination (Join-Path $run 'eula.txt')
[IO.File]::WriteAllText((Join-Path $run "$label\datapacks\stronghold-test\pack.mcmeta"),'{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Stronghold production lifecycle test"}}')
$profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=1;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
$dimensions=[ordered]@{}
foreach($dimension in @('overworld','the_nether','the_end')){
    $settings=switch($dimension){'the_nether'{'nether'} 'the_end'{'end'} default{'overworld'}}
    $biomes=if($dimension -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$settings"}}
    $dimensions["minecraft:$dimension"]=@{type="minecraft:$dimension";generator=@{type='randomnibble6plus24generator:mosaic';biome_source=$biomes;settings="minecraft:$settings";mosaic_profile=$profile}}
}
[IO.File]::WriteAllText((Join-Path $preset 'stronghold_test.json'),(@{dimensions=$dimensions}|ConvertTo-Json -Depth 12))
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$label
level-seed=123456789
level-type=randomnibble6plus24generator:stronghold_test
server-ip=127.0.0.1
server-port=25586
online-mode=true
view-distance=2
simulation-distance=2
"@)
$p=[Diagnostics.Process]::GetCurrentProcess()
$affinity=$p.ProcessorAffinity
$priority=$p.PriorityClass
$previous=$env:JAVA_TOOL_OPTIONS
try{
    if($CpuAffinity -ne 0){$p.ProcessorAffinity=[IntPtr]$CpuAffinity}
    $p.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    Push-Location $repo
    foreach($pass in @('create','reload')){
        $env:JAVA_TOOL_OPTIONS=('-XX:ActiveProcessorCount={0} -Dmosaic.stronghold.test.lifecycle={1} -Dmosaic.stronghold.test.output="{2}" -Dmosaic.stronghold.test.reference="{3}"' -f $TestProcessors,$pass,(Join-Path $output "$pass.json").Replace('\','/'),(Join-Path $output 'create.json').Replace('\','/'))
        & .\gradlew.bat --no-daemon --no-parallel --max-workers=2 ':versions:26.2-fabric:runServer' '-PstrongholdTest' "--args=nogui --world $label" *> (Join-Path $output "$pass.log")
        if($LASTEXITCODE -ne 0){throw "Stronghold lifecycle server failed: $output"}
        $result=Get-Content -Raw -LiteralPath (Join-Path $output "$pass.json")|ConvertFrom-Json
        if($result.status -ne 'PASS'){throw "Stronghold lifecycle failed: $($result.failure)"}
    }
    "PASS $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$p.ProcessorAffinity=$affinity;$p.PriorityClass=$priority;Pop-Location}
