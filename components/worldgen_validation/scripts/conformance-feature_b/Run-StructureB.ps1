param(
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='climate_state_order',
    [switch]$CheckOnly,
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='',
    [long]$CpuAffinity=3840
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical source identity is read-only'}
$env:MOSAIC_VALIDATION_TARGET='26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$defaultBase=Join-Path $validationRepository "validations/$validationProject/result"
$resultBase=Join-Path $defaultBase $EvidenceLabel
$resultRoot=Join-Path $resultBase "structure_reuse/$validationTarget"
$conditionsPath=Join-Path $resultRoot 'conditions.json'
$nativeConfig=Join-Path $validationServer "structure-b-$EvidenceLabel.cfg"
function Get-InputHash {
    $files=@(Get-ChildItem (Join-Path $validationRepository "versions/$validationTarget/src/main"),(Join-Path $validationRepository "versions/$validationTarget/src/probe"),(Join-Path $validationRepository 'components/worldgen_contract/src') -File -Recurse|Sort-Object FullName)
    $body=($files|ForEach-Object{$_.FullName.Substring($validationRepository.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $body+=[IO.File]::ReadAllText((Join-Path $validationRepository "versions/$validationTarget/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body)))
}
function Write-Json([string]$Path,[object]$Data) {
    [IO.File]::WriteAllText($Path,($Data|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
}
$inputHash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{Get-InputHash}
if($CpuAffinity -le 0 -or [Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4){throw 'Exactly four logical CPUs required'}
if($CheckOnly){
    $conditions=Get-Content $conditionsPath -Raw|ConvertFrom-Json
    if($conditions.schema -ne 1 -or $conditions.inputHash -cne $inputHash -or $conditions.target -cne $validationTarget -or
            $conditions.cpu -ne 4 -or $conditions.affinity -ne $CpuAffinity -or $conditions.priority -cne 'BelowNormal' -or
            $conditions.evidenceLabel -cne $EvidenceLabel -or $conditions.nativeConfig -cne $nativeConfig -or
            (Get-FileHash $nativeConfig).Hash -cne $conditions.nativeConfigSha256){throw 'Wrong/stale structure B conditions'}
    $pids=[Collections.Generic.HashSet[long]]::new()
    $worlds=[Collections.Generic.HashSet[string]]::new()
    $fixtureIds=@('origin','nonorigin','village','village-edge','mineshaft','mineshaft-edge','trial','monument','stronghold','nether','fortress','end-spike','end-city','spike-outside','origin-alt','birch','extension-fallback','carved-surface-fallback')
    $expectedNames=@($fixtureIds|ForEach-Object{"serial-$_"})
    foreach($mode in @('reverse','parallel')){
        foreach($id in @('origin','origin-alt','nonorigin','village','mineshaft','trial','stronghold','nether','end-spike','birch','extension-fallback','carved-surface-fallback')){$expectedNames+="$mode-$id"}
    }
    $expectedNames+=@('generated-host-nonorigin','mutated-host-nonorigin')
    $previous=$null
    foreach($role in @('export','host_0','host_1')){
        $execution=Get-Content (Join-Path $resultRoot "$role.log.execution.json") -Raw|ConvertFrom-Json
        $world="structure-b-$EvidenceLabel-$role"
        if(!$pids.Add([long]$execution.pid) -or !$worlds.Add($world) -or $execution.affinity -ne $CpuAffinity -or $execution.priority -cne 'BelowNormal' -or
                $execution.inputHash -cne $inputHash -or $execution.world -cne $world -or $execution.nativeConfig -cne $nativeConfig -or
                $execution.nativeConfigSha256 -cne $conditions.nativeConfigSha256 -or $execution.requestedPort -ne 0){throw 'Structure B process ownership/resources differ'}
        $log=[IO.File]::ReadAllText((Join-Path $resultRoot "$role.log"))
        if($log -notmatch 'Starting Minecraft server on [^\r\n]+:0[\r\n]' -or $log.Contains('FAILED TO BIND TO PORT')){throw 'Structure B did not use its private ephemeral port'}
        if($role -ceq 'export'){continue}
        $summary=Get-Content (Join-Path $resultRoot "$role/summary.json") -Raw|ConvertFrom-Json
        if($summary.status -cne 'PASS' -or $summary.fixtures -ne 18 -or $summary.repeatComparisons -ne 24 -or $summary.bindings -ne 0 -or
                $summary.peakTargets -lt 5 -or $summary.proofVersion -ne 1 -or $summary.entityAcceptanceVersion -ne 1 -or !$summary.exceptionCleanup -or
                $summary.pid -ne $execution.pid -or $summary.inputHash -cne $inputHash -or
                [IO.Path]::GetFullPath($summary.save).TrimEnd([IO.Path]::DirectorySeparatorChar) -cne
                [IO.Path]::GetFullPath((Join-Path $validationServer $world)).TrimEnd([IO.Path]::DirectorySeparatorChar) -or
                [string]$summary.hostSeed -cne $(if($role -ceq 'host_0'){'17'}else{'23'})){throw 'Incomplete current-source structure B proof'}
        $names=@($summary.evidence.PSObject.Properties.Name|Sort-Object)
        if(($names -join ',') -cne (($expectedNames|Sort-Object) -join ',')){throw 'Missing or extra structure B comparisons'}
        $locks=Get-Content (Join-Path $resultRoot "$role/structure-locks.json") -Raw|ConvertFrom-Json
        if($locks.status -cne 'PASS' -or $locks.structures -ne 7 -or $locks.parallelComparisons -ne 21 -or $locks.threads -ne 5 -or !$locks.nativeAlgorithm){throw 'Cold structure lock regression missing'}
        foreach($name in $names){
            $row=$summary.evidence.$name
            if($row.hostSeed -eq $row.seed -or $row.fullNeighbors -gt 8 -or !$row.structureReuse.closed -or $row.structureReuse.resident -ne 0 -or
                    $row.structureReuse.peakResident -gt 361 -or $row.structureReuse.queries -ne $row.structureReuse.hits+$row.structureReuse.evaluations -or
                    !$row.biomeReuse.closed -or $row.biomeReuse.resident -ne 0){throw 'Structure B source/biome scope ownership failed'}
            $folder=Join-Path $resultRoot "$role/$name"
            foreach($file in @('features.bin.gz','original.bin.gz','entities.nbt','original-entities.nbt','entities-raw.json')){
                if(!(Test-Path (Join-Path $folder $file))){throw "Raw complete B evidence missing: $name $file"}
            }
            $comparisons=@('comparison.json')
            if(!$name.StartsWith('serial-')){$comparisons+='repeat-comparison.json'}
            if($role -ceq 'host_1'){$comparisons+='cross-host.json'}
            foreach($file in $comparisons){
                $comparison=Get-Content (Join-Path $folder $file) -Raw|ConvertFrom-Json
                # Gson omits a null record member; a present non-null difference must still fail.
                $nonEntity=$comparison.PSObject.Properties['nonEntityDifference']
                if(!$comparison.accepted -or ($null -ne $nonEntity -and $null -ne $nonEntity.Value) -or $comparison.entities.unexpectedDifferences.Count -ne 0){throw "Unexplained complete B difference: $role $name $file"}
            }
            if($null -ne $previous){
                foreach($field in @('featureSeedCalls','featureSeedSequence','visibleBiomes','structureTags')){
                    if(($row.$field|ConvertTo-Json -Depth 30 -Compress) -cne ($previous.evidence.$name.$field|ConvertTo-Json -Depth 30 -Compress)){throw "Structure B cross-host trace differs: $name $field"}
                }
                $other=$previous.evidence.$name.rejected
                if((@($row.rejected.PSObject.Properties|ForEach-Object Name|Sort-Object) -join ',') -cne (@($other.PSObject.Properties|ForEach-Object Name|Sort-Object) -join ',')){throw 'Rejection counter keys differ'}
                foreach($property in $row.rejected.PSObject.Properties){$key=$property.Name;if($row.rejected.$key -ne $other.$key){throw 'Rejection counter values differ'}}
            }
        }
        $previous=$summary
    }
    'PASS: 18 full B fixtures/host, 24 reverse/parallel repeats + 2 host-frontier conditions/host, 44 cross-host complete comparisons, 7 cold structures/21 parallel comparisons per host; no new entity exception.'
    Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT $inputHash"}else{"INPUT_SCOPE: CURRENT_SOURCE $inputHash"})
    return
}
if((Test-Path $resultRoot) -or (Test-Path $nativeConfig)){throw 'Preserve scoped B evidence and native configuration; never overwrite it'}
$owner=[Diagnostics.Process]::GetCurrentProcess()
$oldAffinity=$owner.ProcessorAffinity
$oldPriority=$owner.PriorityClass
if(($oldAffinity.ToInt64() -band $CpuAffinity) -ne $CpuAffinity){throw 'Requested four CPUs unavailable to the orchestrator'}
$oldOptions=$env:JAVA_TOOL_OPTIONS
$oldProperties=[IO.File]::ReadAllText($validationProperties)
$bootstrap=Get-Content (Join-Path $defaultBase "structure_reuse/$validationTarget/launcher.json") -Raw|ConvertFrom-Json
if([IO.Path]::GetFullPath($bootstrap.directory) -cne [IO.Path]::GetFullPath($validationServer) -or $bootstrap.main -cne 'net.fabricmc.devlaunchinjector.Main' -or
        $bootstrap.jvmArguments -cnotcontains '-Dfabric.dli.env=server' -or $bootstrap.jvmArguments -cnotcontains "-Dvalidation.target=$validationTarget"){throw 'Foreign native server bootstrap'}
New-Item -ItemType Directory -Path $resultRoot -Force|Out-Null
$configs=@($bootstrap.jvmArguments|Where-Object{$_.StartsWith('-Dfabric.dli.config=')})
if($configs.Count -ne 1){throw 'Ambiguous native configuration'}
$configSource=[IO.Path]::GetFullPath($configs[0].Substring('-Dfabric.dli.config='.Length))
$allowed=[IO.Path]::GetFullPath((Join-Path $validationRepository '.gradle'))+[IO.Path]::DirectorySeparatorChar
if(!$configSource.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or ((Get-Item $configSource).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Foreign or linked native configuration'}
$lines=[IO.File]::ReadAllLines($configSource)
$section='';$ports=0
for($i=0;$i -lt $lines.Length;$i++){
    if($lines[$i] -match '^\S'){$section=$lines[$i].Trim();continue}
    if($section -ceq 'serverArgs' -and $lines[$i].Trim() -ceq '--port'){
        if($i+1 -ge $lines.Length -or $lines[$i+1] -notmatch '^(\s+)\d+$'){throw 'Unknown native server port'}
        $lines[$i+1]=$Matches[1]+'0';$ports++
    }
}
if($ports -gt 1){throw 'Ambiguous native server port'}
if($ports -eq 0){$lines+=@('serverArgs',"`t--port","`t0")}
[IO.File]::WriteAllText($nativeConfig,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Json $conditionsPath @{schema=1;target=$validationTarget;inputHash=$inputHash;evidenceLabel=$EvidenceLabel;cpu=4;affinity=$CpuAffinity;priority='BelowNormal';nativeConfig=$nativeConfig;nativeConfigSha256=(Get-FileHash $nativeConfig).Hash;perProcessTimeoutSeconds=300}
function Invoke-Owned([object]$Launch,[string]$Role,[string]$Seed){
    $world="structure-b-$EvidenceLabel-$Role"
    $worldPath=Join-Path $validationServer $world
    if(Test-Path $worldPath){throw 'Preserve existing B save; fresh world required'}
    $properties=[regex]::Replace($oldProperties,'(?m)^level-seed=.*$',"level-seed=$Seed")
    [IO.File]::WriteAllText($validationProperties,$properties,[Text.UTF8Encoding]::new($false))
    $env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -Dfeatureb.structureReuse=true -Dfeatureb.inputHash=$inputHash -Dfeatureb.variant=$Role"+$(if($Role -ceq 'export'){' -Dfeatureb.export=true'}else{''})
    $start=[Diagnostics.ProcessStartInfo]::new($Launch.java)
    $start.WorkingDirectory=$validationServer
    $start.UseShellExecute=$false
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    foreach($argument in $Launch.jvmArguments){
        if(!$argument.StartsWith('-Dfabric.dli.config=') -and !$argument.StartsWith('-Dfeatureb.')){$start.ArgumentList.Add($argument)}
    }
    $start.ArgumentList.Add("-Dfabric.dli.config=$nativeConfig")
    $start.ArgumentList.Add("-Dvalidation.result=$resultBase")
    $start.ArgumentList.Add('-cp');$start.ArgumentList.Add($Launch.classpath);$start.ArgumentList.Add($Launch.main)
    $arguments=@($Launch.gameArguments)
    $index=[Array]::IndexOf($arguments,'--world')
    if($index -lt 0 -or $index+1 -ge $arguments.Count){throw 'Missing native world argument'}
    $arguments[$index+1]=$world
    $ports=0
    for($i=0;$i -lt $arguments.Count;$i++){
        if($arguments[$i] -ceq '--port'){
            if($i+1 -ge $arguments.Count -or $arguments[$i+1] -notmatch '^\d+$'){throw 'Unknown captured port argument'}
            $arguments[$i+1]='0';$ports++
        }
    }
    if($ports -eq 0){$arguments+=@('--port','0')}
    foreach($argument in $arguments){$start.ArgumentList.Add($argument)}
    $child=[Diagnostics.Process]::Start($start)
    $child.PriorityClass='BelowNormal';$child.ProcessorAffinity=[IntPtr]$CpuAffinity
    $log=Join-Path $resultRoot "$Role.log"
    Write-Json ($log+'.execution.json') @{pid=$child.Id;inputHash=$inputHash;affinity=$child.ProcessorAffinity.ToInt64();priority=$child.PriorityClass.ToString();world=$world;nativeConfig=$nativeConfig;nativeConfigSha256=(Get-FileHash $nativeConfig).Hash;requestedPort=0;gameArguments=$arguments}
    $stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync()
    $timedOut=!$child.WaitForExit(300000)
    if($timedOut){$child.Kill($true);$child.WaitForExit()}
    [IO.File]::WriteAllText($log,$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult(),[Text.UTF8Encoding]::new($false))
    $exitCode=$child.ExitCode;$child.Dispose()
    if($timedOut -or $exitCode -ne 0){Get-Content $log -Tail 55;throw "Structure B $Role failed/bounded out; raw results and save retained"}
    if($Role -cne 'export'){
        $summaryPath=Join-Path $resultRoot "$Role/summary.json"
        if(!(Test-Path $summaryPath) -or (Get-Content $summaryPath -Raw|ConvertFrom-Json).status -cne 'PASS'){throw 'No complete structure B PASS; save retained'}
    }
    Move-ValidationToRecycleBin -LiteralPath $worldPath
    "Structure B $Role completed"
}
try{
    $owner.PriorityClass='BelowNormal';$owner.ProcessorAffinity=[IntPtr]$CpuAffinity
    Invoke-Owned $bootstrap 'export' '0'
    $launch=Get-Content (Join-Path $resultRoot 'launcher.json') -Raw|ConvertFrom-Json
    Invoke-Owned $launch 'host_0' '17'
    Invoke-Owned $launch 'host_1' '23'
    if((Get-InputHash) -cne $inputHash){throw 'Source changed during structure B validation'}
}finally{
    $env:JAVA_TOOL_OPTIONS=$oldOptions
    [IO.File]::WriteAllText($validationProperties,$oldProperties,[Text.UTF8Encoding]::new($false))
    $owner.ProcessorAffinity=$oldAffinity;$owner.PriorityClass=$oldPriority
}
& $PSCommandPath -EvidenceLabel $EvidenceLabel -CpuAffinity $CpuAffinity -CheckOnly
