param([switch]$Lazy,[switch]$Smoke,[switch]$Rings,[switch]$Strongholds,[switch]$StructurePositives,[switch]$Lightweight,[string]$Label,[string]$Reference='',[string]$Only='',[ValidateSet('off','oracle','production')][string]$RepairC='off',[ValidateSet('off','baseline','candidate')][string]$RepairD1='off')
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$run=Join-Path $repo 'validations\performance-worldgen\instance\26.2-fabric\server'
if(-not $Label){$Label='lazy-'+[guid]::NewGuid().ToString('N').Substring(0,8)}
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Unsafe label'}
$world=Join-Path $run "DISPOSABLE-$Label"
$output=Join-Path $repo "validations\performance-worldgen\result\current\lazy_verification"
if((Test-Path -LiteralPath $world)){throw 'Fresh fixture required'}
New-Item -ItemType Directory -Path $world,$output -Force|Out-Null
[IO.File]::WriteAllText((Join-Path $run 'server.properties'),@"
level-name=DISPOSABLE-$Label
level-type=minecraft:normal
level-seed=0
server-ip=127.0.0.1
server-port=25590
online-mode=true
view-distance=2
simulation-distance=2
max-tick-time=60000
"@)
$previous=$env:JAVA_TOOL_OPTIONS
$researchProcess=[Diagnostics.Process]::GetCurrentProcess()
$previousAffinity=$researchProcess.ProcessorAffinity
$previousPriority=$researchProcess.PriorityClass
try{
 $researchProcess.ProcessorAffinity=[IntPtr]0xF00
 $researchProcess.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
 Push-Location $repo
 $flags=@(('-Dmosaic.profile.output="{0}"' -f $output.Replace('\','/')),'-Dmosaic.lazy.experiment=true','-Dmosaic.lazy.verify=true','-Dmosaic.lazy.capture=true',"-Dmosaic.profile.label=$Label",('-Dmosaic.lazy.output="{0}"' -f $output.Replace('\','/')))
 if($Lazy){$flags+='-Dmosaic.lazy.enabled=true'}
 if($RepairC -ne 'off'){$flags+='-Dmosaic.repairC=true'}
 if($RepairC -eq 'oracle'){$flags+='-Dmosaic.repairC.oracle=true'}
 if($RepairD1 -ne 'off'){$flags+='-Dmosaic.repairC=true';$flags+='-Dmosaic.candidate.study=true'}
 if($RepairD1 -eq 'baseline'){$flags+='-Dmosaic.candidate.oracle=true'}
 if($Smoke){$flags+='-Dmosaic.lazy.smoke=true'}
 if($Rings){$flags+='-Dmosaic.lazy.ringAudit=true'}
 if($Strongholds){$flags+='-Dmosaic.lazy.strongholdMatrix=true'}
 if($StructurePositives){$flags+='-Dmosaic.candidate.positiveMatrix=true'}
 if($Only){$flags+="-Dmosaic.lazy.only=$Only"}
 if($Lightweight){$flags+='-Dmosaic.lazy.lightweight=true'}
 if($Reference){$flags+=('-Dmosaic.lazy.reference="{0}"' -f (Resolve-Path -LiteralPath $Reference).Path.Replace('\','/'))}
 $env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 '+($flags -join ' ')
 & (Join-Path $repo 'gradlew.bat') '--no-daemon' '--no-parallel' '--max-workers=2' ':version:26.2-fabric:runValidationPerformanceWorldgenServer' "--args=nogui --world DISPOSABLE-$Label" *> (Join-Path $output 'console.log')
 if(-not(Test-Path -LiteralPath (Join-Path $output 'report.json'))){throw "Missing report: $output"}
 $result=Get-Content -Raw -LiteralPath (Join-Path $output 'report.json')|ConvertFrom-Json
 if($result.status -ne 'PASS'){throw "Lazy comparison failed: $($result.failure)"}
 "PASS $output"
}finally{$env:JAVA_TOOL_OPTIONS=$previous;$researchProcess.ProcessorAffinity=$previousAffinity;$researchProcess.PriorityClass=$previousPriority;Pop-Location}
