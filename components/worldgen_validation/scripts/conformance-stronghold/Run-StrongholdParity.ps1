param([int]$Seeds=128,[int]$TestProcessors=4,[long]$CpuAffinity=0)
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$run=Join-Path $repo 'validations\conformance-stronghold\instance\26.2-fabric\server'
$label='stronghold-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$output=Join-Path $repo "validations\conformance-stronghold\result\current\stronghold-test"
New-Item -ItemType Directory -Path $run,$output -Force|Out-Null
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$label
level-seed=0
level-type=minecraft:normal
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
    # Test-only resource limit. Never changes the production scheduler.
    if($CpuAffinity -ne 0){$p.ProcessorAffinity=[IntPtr]$CpuAffinity}
    $p.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    Push-Location $repo
    $env:JAVA_TOOL_OPTIONS=('-XX:ActiveProcessorCount={0} -Dmosaic.stronghold.test.seeds={1} -Dmosaic.stronghold.test.output="{2}"' -f $TestProcessors,$Seeds,(Join-Path $output 'result.json').Replace('\','/'))
    & .\gradlew.bat --no-daemon --no-parallel --max-workers=2 ':version:26.2-fabric:runValidationConformanceStrongholdServer' "--args=nogui --world $label" *> (Join-Path $output 'console.log')
    if($LASTEXITCODE -ne 0){throw "Native Stronghold regression failed: $output"}
    $result=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
    if($result.status -ne 'PASS'){throw "Native Stronghold regression failed: $($result.failure)"}
    "PASS $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$p.ProcessorAffinity=$affinity;$p.PriorityClass=$priority;Pop-Location}
