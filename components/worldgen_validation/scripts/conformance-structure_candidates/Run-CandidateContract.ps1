param([int]$Seeds=64,[long]$CpuAffinity=3840)
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$run=Join-Path $repo "validations\conformance-structure_candidates\instance\${validationTarget}\server"
$world='candidate-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$output=Join-Path $repo "validations\conformance-structure_candidates\result\current\${validationTarget}\candidate-pruning"
New-Item -ItemType Directory -Path $run,$output -Force|Out-Null
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=$world
level-seed=0
level-type=minecraft:normal
server-ip=127.0.0.1
server-port=25584
online-mode=true
view-distance=2
simulation-distance=2
"@)
$process=[Diagnostics.Process]::GetCurrentProcess();$affinity=$process.ProcessorAffinity;$priority=$process.PriorityClass;$previous=$env:JAVA_TOOL_OPTIONS
try{
    $process.ProcessorAffinity=[IntPtr]$CpuAffinity;$process.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    $env:JAVA_TOOL_OPTIONS=('-XX:ActiveProcessorCount=4 -Dmosaic.candidate.test.seeds={0} -Dmosaic.candidate.test.output="{1}"' -f $Seeds,(Join-Path $output 'result.json').Replace('\','/'))
    Push-Location $repo
    & .\gradlew.bat --no-daemon --no-parallel --max-workers=2 ":version:${validationTarget}:runValidationConformanceStructureCandidatesServer" "--args=nogui --world $world" *> (Join-Path $output 'console.log')
    if($LASTEXITCODE -ne 0){throw "Candidate contract server failed: $output"}
    $result=Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')|ConvertFrom-Json
    if($result.status -ne 'PASS'){throw "Candidate contract failed: $($result.failure)"}
    "PASS $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$process.ProcessorAffinity=$affinity;$process.PriorityClass=$priority;Pop-Location}
