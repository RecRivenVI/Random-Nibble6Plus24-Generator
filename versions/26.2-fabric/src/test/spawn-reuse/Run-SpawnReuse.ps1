param(
    [ValidateSet('candidate','oracle','evict','failure','reload')][string]$Mode='candidate',
    [string]$WorldName='',
    [long]$MasterSeed=123456789,
    [string]$Fixtures='overworld:0:0,overworld:125:-37,overworld:-10:11,the_nether:-11:-6,the_end:0:0',
    [switch]$ComparePalettes,
    [string]$ReferenceDirectory='',
    [switch]$Fortress
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($Fixtures -notmatch '^(spawn-candidates|[a-z_]+:-?\d+:-?\d+(,[a-z_]+:-?\d+:-?\d+)*)$'){throw 'Invalid fixture list; quote comma-separated arguments'}
$repository=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$run=Join-Path $repository 'runs\26.2-fabric\spawn-reuse'
if(-not $WorldName){$WorldName='spawn-reuse-'+[guid]::NewGuid().ToString('N').Substring(0,12)}
if($WorldName -notmatch '^spawn-reuse-[a-zA-Z0-9-]+$'){throw 'Unsafe test world name'}
$world=Join-Path $run $WorldName
$output=Join-Path $repository "versions\26.2-fabric\build\spawn-reuse\$WorldName-$Mode"
New-Item -ItemType Directory -Path $run,$output -Force | Out-Null
$eula=Join-Path $repository 'runs\26.2-fabric\server\eula.txt'
if(-not (Test-Path -LiteralPath $eula) -or -not (Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)) {throw 'Existing accepted development EULA required'}
Copy-Item -LiteralPath $eula -Destination (Join-Path $run 'eula.txt')
if($Mode -eq 'reload'){
    if(-not (Test-Path -LiteralPath (Join-Path $world 'level.dat'))){throw 'Reload requires an existing fixture'}
}else{
    if(Test-Path -LiteralPath $world){throw 'Fresh control must not reuse an existing world'}
    $pack=Join-Path $world 'datapacks\spawn-reuse-test'
    $preset=Join-Path $pack 'data\randomnibble6plus24generator\worldgen\world_preset'
    New-Item -ItemType Directory -Path $preset -Force|Out-Null
    [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),'{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"SPAWN reuse regression fixture"}}')
    $profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=1;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
    $dimensions=[ordered]@{}
    foreach($dimension in @('overworld','the_nether','the_end')){
        $settings=switch($dimension){'the_nether'{'nether'} 'the_end'{'end'} default{'overworld'}}
        $biomes=if($dimension -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$settings"}}
        $dimensions["minecraft:$dimension"]=@{type="minecraft:$dimension";generator=@{type='randomnibble6plus24generator:mosaic';biome_source=$biomes;settings="minecraft:$settings";mosaic_profile=$profile}}
    }
    [IO.File]::WriteAllText((Join-Path $preset 'spawn_reuse_hidden.json'),(@{dimensions=$dimensions}|ConvertTo-Json -Depth 12))
}
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$WorldName
level-seed=$MasterSeed
level-type=randomnibble6plus24generator:spawn_reuse_hidden
server-ip=127.0.0.1
server-port=25588
online-mode=true
view-distance=2
simulation-distance=2
"@)
$previous=$env:JAVA_TOOL_OPTIONS
try{
    Push-Location $repository
    $flags=@(('-Dmosaic.spawn.test.output="{0}"' -f $output.Replace('\','/')),"-Dmosaic.spawn.test.fixtures=$Fixtures")
    if($Mode -eq 'oracle'){$flags+='-Dmosaic.spawn.test.oracle=true'}
    if($Mode -in @('candidate','oracle','evict','failure')){$flags+='-Dmosaic.spawn.test.expectCarried=true'}
    if($Mode -eq 'evict'){$flags+='-Dmosaic.spawn.test.evict=true'}
    if($Mode -eq 'failure'){$flags+='-Dmosaic.spawn.test.failure=true'}
    if($Mode -eq 'reload'){$flags+='-Dmosaic.spawn.test.expectReload=true'}
    if($ComparePalettes){$flags+='-Dmosaic.spawn.test.comparePalettes=true'}
    if($ReferenceDirectory){$flags+=('-Dmosaic.spawn.test.reference="{0}"' -f (Resolve-Path -LiteralPath $ReferenceDirectory).Path.Replace('\','/'))}
    if($Fortress){$flags+='-Dmosaic.spawn.test.fortress=true'}
    $env:JAVA_TOOL_OPTIONS=$flags -join ' '
    & (Join-Path $repository 'gradlew.bat') ':versions:26.2-fabric:runServer' '-PspawnReuseTest' "--args=nogui --world $WorldName" *> (Join-Path $output 'console.log')
    if($LASTEXITCODE -ne 0){throw "SPAWN regression failed; see $output"}
    $log=Get-Content -Raw -LiteralPath (Join-Path $output 'console.log')
    if($log -match 'Encountered an unexpected exception|Exception stopping the server|Caught exception in thread|A single server tick took'){
        throw "SPAWN runtime did not finish cleanly; see $output"
    }
    $result=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
    if($result.status -ne 'PASS'){throw "SPAWN regression did not pass: $output"}
    "PASS $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;Pop-Location}
