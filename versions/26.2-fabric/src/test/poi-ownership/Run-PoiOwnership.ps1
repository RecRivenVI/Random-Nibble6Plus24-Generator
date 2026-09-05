param(
    [ValidateSet('create','reload','vanilla','audit')][string]$Mode='create',
    [string]$World='', [string]$Reference='', [int]$TestProcessors=4, [long]$CpuAffinity=3840
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$run=Join-Path $repo 'runs\26.2-fabric\poi-ownership'
if(-not $World){$World='poi-'+[guid]::NewGuid().ToString('N').Substring(0,12)}
if($World -notmatch '^poi-[a-zA-Z0-9-]+$'){throw 'Unsafe test world name'}
$worldPath=Join-Path $run $World
$output=Join-Path $repo "versions\26.2-fabric\build\poi-ownership\$World-$Mode"
New-Item -ItemType Directory -Path $run,$output -Force|Out-Null
$eula=Join-Path $repo 'runs\26.2-fabric\server\eula.txt'
if(-not(Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)){throw 'Existing accepted EULA required'}
Copy-Item -LiteralPath $eula -Destination (Join-Path $run 'eula.txt')
$levelType='randomnibble6plus24generator:poi_test'
if($Mode -eq 'reload'){
    if(-not(Test-Path -LiteralPath (Join-Path $worldPath 'level.dat')) -or -not(Test-Path -LiteralPath $Reference)){throw 'Reload needs a saved fixture and reference'}
}else{
    if(Test-Path -LiteralPath $worldPath){throw 'Do not overwrite prior worlds'}
    New-Item -ItemType Directory -Path $worldPath|Out-Null
    if($Mode -eq 'vanilla'){$levelType='minecraft:normal'}else{
        $pack=Join-Path $worldPath 'datapacks\poi-test'
        $preset=Join-Path $pack 'data\randomnibble6plus24generator\worldgen\world_preset'
        New-Item -ItemType Directory -Path $preset -Force|Out-Null
        [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),'{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Physical POI ownership regression"}}')
        $profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=1;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
        $dimensions=[ordered]@{}
        foreach($dimension in @('overworld','the_nether','the_end')){
            $settings=switch($dimension){'the_nether'{'nether'} 'the_end'{'end'} default{'overworld'}}
            $biomes=if($dimension -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$settings"}}
            $dimensions["minecraft:$dimension"]=@{type="minecraft:$dimension";generator=@{type='randomnibble6plus24generator:mosaic';biome_source=$biomes;settings="minecraft:$settings";mosaic_profile=$profile}}
        }
        [IO.File]::WriteAllText((Join-Path $preset 'poi_test.json'),(@{dimensions=$dimensions}|ConvertTo-Json -Depth 12))
    }
}
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$World
level-seed=123456789
level-type=$levelType
server-ip=127.0.0.1
server-port=25587
online-mode=true
view-distance=2
simulation-distance=2
"@)
$process=[Diagnostics.Process]::GetCurrentProcess();$affinity=$process.ProcessorAffinity;$priority=$process.PriorityClass;$previous=$env:JAVA_TOOL_OPTIONS
try{
    if($CpuAffinity -ne 0){$process.ProcessorAffinity=[IntPtr]$CpuAffinity}
    $process.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    Push-Location $repo
    $env:JAVA_TOOL_OPTIONS=('-XX:ActiveProcessorCount={0} -Dmosaic.poi.test.mode={1} -Dmosaic.poi.test.output="{2}" -Dmosaic.poi.test.reference="{3}"' -f $TestProcessors,$Mode,$output.Replace('\','/'),$Reference.Replace('\','/'))
    & .\gradlew.bat --no-daemon --no-parallel --max-workers=2 ':versions:26.2-fabric:runServer' '-PpoiOwnershipTest' "--args=nogui --world $World" *> (Join-Path $output 'console.log')
    $result=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
    if($Mode -eq 'audit') {"AUDIT $output"}else{
        if($LASTEXITCODE -ne 0 -or $result.status -ne 'PASS'){throw "POI ownership failed: $output"}
        "PASS $output"
    }
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$process.ProcessorAffinity=$affinity;$process.PriorityClass=$priority;Pop-Location}
