param([long]$HostSeed = 314159265, [switch]$KeepWorld)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot=(Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$propertiesPath=Join-Path $runRoot 'server.properties'
$originalProperties=[IO.File]::ReadAllText($propertiesPath)
$originalJavaToolOptions=$env:JAVA_TOOL_OPTIONS
$buildRoot=Join-Path $repositoryRoot 'versions\26.2-fabric\build'
$outputRoot=Join-Path $buildRoot 'phase2c1f-targeted-acceptance'
New-Item -ItemType Directory -Path $outputRoot -Force|Out-Null

$fixtures=@(
    @{Id='end-spike';Master='0';Local='3691099418492663734';Dim='minecraft:the_end';X=-1;Z=-3;Root='phase2c1r-phase2c1f-end-spike-final'},
    @{Id='pale-moss';Master='123456789';Local='-4874346107934243498';Dim='minecraft:overworld';X=-8532;Z=-4457;Root='phase2c1r-phase2c1f-pale-moss-final'},
    @{Id='capped-processor';Master='1';Local='-5110934803368866493';Dim='minecraft:overworld';X=3699;Z=-6116;Root='phase2c1r-phase2c1f-capped-final'},
    @{Id='nether-fossil';Master='-1';Local='1833653500061299851';Dim='minecraft:the_nether';X=10000;Z=-20000;Root='phase2c1r-phase2c1f-structure-final'}
)

$manifest=foreach($fixture in $fixtures){
    $root=Join-Path $buildRoot $fixture.Root
    $native=Get-Content -LiteralPath (Join-Path $root 'native-result.json') -Raw|ConvertFrom-Json
    [pscustomobject]@{
        id=$fixture.Id;masterSeed=$fixture.Master;localSeed=$fixture.Local;dimension=$fixture.Dim
        chunkX=$fixture.X;chunkZ=$fixture.Z
        nativeSnapshot=(Join-Path $root 'native\final-feature-stable.bin.gz').Replace('\','/')
        nativeHash=$native.featureHash
        nativeFeatureSeedInvocationCount=[long]$native.featureSeedInvocationCount
        nativeFeatureSeedSequenceHash=$native.featureSeedSequenceHash
        nativeDecorationSeedReads=[long]$native.decorationSeedReads
        nativeFeatureVisibleBiomeSequence=$native.featureVisibleBiomeSequence
    }
}
$manifestPath=Join-Path $outputRoot 'manifest.json'
$resultPath=Join-Path $outputRoot 'result.json'
$manifest|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $manifestPath -Encoding utf8
$worldName="phase2c1f-targeted-host-$HostSeed"

function Remove-World {
    $path=[IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix=$runRoot+[IO.Path]::DirectorySeparatorChar
    if(-not $path.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $path)-ne $worldName){throw "Unsafe targeted cleanup $path"}
    if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -Recurse -Force}
}

try{
    Push-Location $repositoryRoot
    Remove-World
    $properties=[regex]::Replace($originalProperties,'(?m)^level-seed=.*$',"level-seed=$HostSeed")
    [IO.File]::WriteAllText($propertiesPath,$properties)
    $env:JAVA_TOOL_OPTIONS=@(
        "-Drandomnibble6plus24generator.phase2c1f.manifest=$($manifestPath.Replace('\','/'))",
        "-Drandomnibble6plus24generator.phase2c1f.output=$($resultPath.Replace('\','/'))"
    ) -join ' '
    & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
    if($LASTEXITCODE -ne 0 -or -not(Test-Path -LiteralPath $resultPath)){throw 'Targeted acceptance process failed'}
    $result=Get-Content -LiteralPath $resultPath -Raw|ConvertFrom-Json
    if($result.status -ne 'PASS' -or $result.fixtures -ne 4){throw "Targeted acceptance mismatch $result"}
    $result|ConvertTo-Json -Depth 8
}finally{
    [IO.File]::WriteAllText($propertiesPath,$originalProperties)
    $env:JAVA_TOOL_OPTIONS=$originalJavaToolOptions
    Pop-Location
    if(-not $KeepWorld){Remove-World}
}
