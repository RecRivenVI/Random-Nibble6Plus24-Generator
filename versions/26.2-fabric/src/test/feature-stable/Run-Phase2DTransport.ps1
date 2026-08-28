param([switch]$KeepWorlds)

$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repositoryRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot=(Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath=Join-Path $runRoot 'server.properties'
$originalProperties=[IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions=$env:JAVA_TOOL_OPTIONS
$outputRoot=Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2d-transport'
New-Item -ItemType Directory -Path $outputRoot -Force|Out-Null
$fixtures=@(
    @{Id='non-origin';Master='123456789';Dim='minecraft:overworld';X=125;Z=-37},
    @{Id='nether-structure';Master='-1';Dim='minecraft:the_nether';X=10000;Z=-20000}
)

function Remove-World([string]$name){
    $path=[IO.Path]::GetFullPath((Join-Path $runRoot $name));$prefix=$runRoot+[IO.Path]::DirectorySeparatorChar
    if(-not $path.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)-or(Split-Path -Leaf $path)-ne $name){throw "Unsafe Phase 2D transport cleanup $path"}
    if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -Recurse -Force}
}
function Set-Seed([string]$seed){
    $properties=[regex]::Replace($originalProperties,'(?m)^level-seed=.*$',"level-seed=$seed")
    [IO.File]::WriteAllText($propertiesPath,$properties)
}
function Run-Server([string]$world){
    Remove-World $world
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $world"
    if($LASTEXITCODE -ne 0){throw "Phase 2D transport server failed $world"}
    if(-not $KeepWorlds){Remove-World $world}
}

try{
    Push-Location $repositoryRoot
    $all=@()
    foreach($fixture in $fixtures){
        $artifact=Join-Path $outputRoot "$($fixture.Id).bin"
        $producerResult=Join-Path $outputRoot "$($fixture.Id)-producer.json"
        Set-Seed '246813579'
        $env:JAVA_TOOL_OPTIONS=@(
            '-Drandomnibble6plus24generator.phase2d.transport.mode=produce',
            "-Drandomnibble6plus24generator.phase2d.transport.masterSeed=$($fixture.Master)",
            "-Drandomnibble6plus24generator.phase2d.transport.dimension=$($fixture.Dim)",
            "-Drandomnibble6plus24generator.phase2d.transport.chunkX=$($fixture.X)",
            "-Drandomnibble6plus24generator.phase2d.transport.chunkZ=$($fixture.Z)",
            "-Drandomnibble6plus24generator.phase2d.transport.artifact=$($artifact.Replace('\','/'))",
            "-Drandomnibble6plus24generator.phase2d.transport.output=$($producerResult.Replace('\','/'))"
        ) -join ' '
        Run-Server "phase2d-transport-producer-$($fixture.Id)"
        $producer=Get-Content -LiteralPath $producerResult -Raw|ConvertFrom-Json
        foreach($case in @(
            @{Host='0';Frontier='absent'},@{Host='1';Frontier='absent'},@{Host='-1';Frontier='absent'},
            @{Host='0';Frontier='generated'},@{Host='0';Frontier='mutated'})){
            Set-Seed $case.Host
            $resultPath=Join-Path $outputRoot "$($fixture.Id)-host-$($case.Host)-$($case.Frontier).json"
            $env:JAVA_TOOL_OPTIONS=@(
                '-Drandomnibble6plus24generator.phase2d.transport.mode=consume',
                "-Drandomnibble6plus24generator.phase2d.transport.frontier=$($case.Frontier)",
                "-Drandomnibble6plus24generator.phase2d.transport.artifact=$($artifact.Replace('\','/'))",
                "-Drandomnibble6plus24generator.phase2d.transport.output=$($resultPath.Replace('\','/'))"
            ) -join ' '
            Run-Server "phase2d-transport-$($fixture.Id)-$($case.Host)-$($case.Frontier)"
            $result=Get-Content -LiteralPath $resultPath -Raw|ConvertFrom-Json
            if (($result.status -ne 'PASS') -or ($result.semanticHash -ne $producer.semanticHash) `
                    -or ($result.rawFingerprint -ne $producer.rawFingerprint)) {
                throw "Transport mismatch $($fixture.Id) $($case.Host)/$($case.Frontier)"
            }
            $all+=$result
            Write-Host "PHASE2D_TRANSPORT_PASS $($fixture.Id) host=$($case.Host) frontier=$($case.Frontier)"
        }
    }
    [pscustomobject]@{status='PASS';fixtures=$fixtures.Count;consumers=$all.Count;results=$all}|ConvertTo-Json -Depth 6
}finally{
    [IO.File]::WriteAllText($propertiesPath,$originalProperties);$env:JAVA_TOOL_OPTIONS=$originalJavaToolOptions;Pop-Location
}
