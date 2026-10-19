param([ValidateSet('26.3-fabric','26.3-neoforge')][string]$Target='26.3-fabric', [switch]$Guarded, [switch]$Counterexample, [switch]$RingContinuation, [switch]$CheckOnly, [switch]$Offline, [switch]$ExportNative, [switch]$PreserveFailureOnly, [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='', [long]$CpuAffinity=3840, [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if($Counterexample -and ($Target -ne '26.3-neoforge' -or $Guarded -or !$EvidenceLabel)){throw 'Explicit bounded NeoForge counterexample needs a unique evidence label'}
if($Target -ne '26.3-fabric' -and ($RingContinuation -or $PreserveFailureOnly)){throw 'Ring and native export adaptation remains Fabric-only'}
$validation=Join-Path $repo 'validations/conformance-prefeatures'
$variant=if($Guarded){'native_climate_state_guarded'}else{'native_climate_state'}
if($RingContinuation){
    if(!$Guarded -or $PreserveFailureOnly){throw 'Ring continuation is a separate guarded native component'}
    $variant='native_ring_continuation'
}
if($EvidenceLabel){$variant+='_'+$EvidenceLabel}
$result=Join-Path $validation "result/$variant/$target"
$instance=Join-Path $validation "instance/$target/server"
$modId=(ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id
function Get-InputHash {
    $files=@(Get-ChildItem (Join-Path $repo "versions/$target/src/main"),(Join-Path $repo "versions/$target/src/probe"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
    $text=($files|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $text+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
}
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical input identity is read-only'}
$hash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{Get-InputHash}
if($PreserveFailureOnly){
    if($CheckOnly -or $ExpectedInputHash){throw 'Failure export is separate from completed evidence checks'}
    $console=Get-Item (Join-Path $result 'first/console.log')
    $text=[IO.File]::ReadAllText($console.FullName)
    if(!$text.Contains("inputHash=$hash") -or !$text.Contains('mixin.debug.export=true')){throw 'Native bytecode export does not match this source/run'}
    $destination=Join-Path $result 'first/native-bytecode.txt'
    if(Test-Path $destination){throw 'Never overwrite native bytecode evidence'}
    $directory=Join-Path $instance '.mixin.out/class'
    $lines=[Collections.Generic.List[string]]::new()
    foreach($type in @('Climate$ParameterList','Climate$RTree')){
        $file=Get-Item (Join-Path $directory "net/minecraft/world/level/biome/$type.class")
        if($file.LastWriteTime -lt $console.CreationTime -or $file.LastWriteTime -gt $console.LastWriteTime){throw 'Stale exported native class'}
        $lines.Add("SHA256 $type $((Get-FileHash $file.FullName).Hash)")
        $listing=& javap '-J-XX:ActiveProcessorCount=2' -p -c -classpath $directory "net.minecraft.world.level.biome.$type"
        if($LASTEXITCODE){throw 'Native javap export failed'}
        foreach($line in $listing){$lines.Add($line)}
    }
    [IO.File]::WriteAllText($destination,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
    'PRESERVED_NATIVE_FAILURE_BYTECODE: no world started, no prior evidence replaced'
    exit
}
function Assert-Evidence {
    if(!$ExpectedInputHash -and (Get-InputHash) -cne $hash){throw 'Climate component source changed during validation'}
    if($RingContinuation){
        $pids=@();$saves=@();$rows=@()
        foreach($role in @('first','repeat')){
            $s=Get-Content (Join-Path $result "$role/summary.json") -Raw|ConvertFrom-Json
            if($s.schema -ne 1 -or $s.scope -cne 'BOUNDED_NATIVE_RING_AND_CONTINUATION_NOT_WORLDGEN_PARITY' -or
                    $s.inputHash -cne $hash -or $s.processors -ne 4 -or $s.environment.mods.minecraft -cne '26.3' -or
                    @($s.cases).Count -ne 16 -or @($s.dimensions).Count -ne 3 -or !$s.executorTerminated -or !$s.loadedUnchanged){throw 'Incomplete native ring continuation evidence'}
            if($s.status -cne 'BOUNDED_NATIVE_COMPONENT_PASS' -or $s.positionOrRandomDifferences -ne 0 -or $s.continuationBiomeDifferences -ne 0){throw "Native ring component exposed an unresolved difference; raw evidence retained in $result"}
            foreach($case in $s.cases){
                if(!$case.sameNativeEntry -or !$case.positionsEqual -or !$case.rootRandomContinuationEqual -or $case.nativeTasks -ne 128 -or
                        $case.lazyTasks -ge $case.nativeTasks -or $case.continuation.samples -ne 1445 -or $case.continuation.differentBiomes -ne 0 -or
                        $case.continuation.readHash -cnotmatch '^[a-f0-9]{64}$'){throw 'Native ring output/RNG/continuation check incomplete'}
            }
            if(@($s.cases.seed|Sort-Object -Unique).Count -ne 4 -or @($s.cases.prefixDirection|Sort-Object -Unique).Count -ne 2 -or
                    @($s.cases|Where-Object{$_.lazyTasks -gt 0}).Count -ne 8 -or @($s.cases|Where-Object{$_.lazyTasks -eq 0}).Count -ne 8){throw 'Missing ordinary/positive native ring cases'}
            $pids+=$s.pid;$saves+=$s.save;$rows+=,($s.cases|ConvertTo-Json -Depth 20 -Compress)
        }
        if(@($pids|Sort-Object -Unique).Count -ne 2 -or @($saves|Sort-Object -Unique).Count -ne 2 -or $rows[0] -cne $rows[1]){throw 'Controlled native ring evidence did not repeat across fresh JVMs/saves'}
        "VERIFIED_BOUNDED_NATIVE_COMPONENT: 16 ring/RNG cases and 23,120 independent continuation reads per JVM, source $hash. NOT full worldgen parity or all legal schedules."
        return
    }
    $pids=@();$saves=@();$cases=@()
    foreach($role in @('first','repeat')){
        $s=Get-Content (Join-Path $result "$role/summary.json") -Raw|ConvertFrom-Json
        $expectedStatus=if($Guarded){'GUARDED_COMPONENT_PASS'}else{'COUNTEREXAMPLE'}
        if($s.status -cne $expectedStatus -or $s.schema -ne 1 -or
                $s.scope -cne 'NATIVE_CLIMATE_CACHE_COMPONENT_NOT_STRUCTURE_ORACLE' -or
                $s.inputHash -cne $hash -or $s.processors -ne 4 -or @($s.cases).Count -ne 8 -or
                $s.loadedBefore -ne $s.loadedAfter -or $s.environment.mods.minecraft -cne '26.3') {throw 'Native climate counterexample evidence incomplete'}
        if($Guarded){
            if(@($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'entryMismatch' -and ($_.entryMismatch -or $_.stateMismatches -ne 1 -or $_.evaluations -ne 2)}).Count -or
                    @($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'exitLost' -and ($_.exitLost -or !$_.sameEntry -or $_.nativeNext -cne $_.cachedNext -or $_.stateReplays -ne 1)}).Count){throw 'Native climate input/output continuation not preserved'}
            if(@($s.dimensions).Count -ne 3 -or @($s.dimensions|Where-Object{$_.dimension -in @('minecraft:overworld','minecraft:the_nether') -and !$_.climateState}).Count){throw 'Native dimension index bridge missing'}
        }elseif(@($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'entryMismatch' -and !$_.entryMismatch}).Count -or
                @($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'exitLost' -and (!$_.exitLost -or !$_.sameEntry -or $_.nativeNext -ceq $_.cachedNext)}).Count){throw 'Native state difference was not observed'}
        if(@($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'entryMismatch'}).Count -ne 4 -or
                @($s.cases|Where-Object{$_.PSObject.Properties.Name -contains 'exitLost'}).Count -ne 4){throw 'Missing native entry/exit cases'}
        if(@($s.cases.childrenPerNode|Sort-Object -Unique).Count -ne 2 -or
                @($s.cases.firstPrefix|Sort-Object -Unique).Count -ne 2){throw 'Missing index shape/direction control'}
        $pids+=$s.pid;$saves+=$s.save
        $cases+=,($s.cases|ConvertTo-Json -Depth 20 -Compress)
    }
    if(@($pids|Sort-Object -Unique).Count -ne 2 -or @($saves|Sort-Object -Unique).Count -ne 2 -or $cases[0] -cne $cases[1]){throw 'Counterexample did not repeat in independent native JVMs/saves'}
    "VERIFIED_$expectedStatus : 26.3 native climate entry and exit, eight bidirectional cases per independent JVM, source $hash; NOT worldgen parity PASS."
}
if($CheckOnly){Assert-Evidence; if($ExpectedInputHash){'INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT'}else{'INPUT_SCOPE: CURRENT_SOURCE'};exit}
if(!$Guarded){throw 'Original counterexample is historical and read-only; execute the guarded native component instead'}
$local=Join-Path $repo 'local.toml'
if(!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)){throw 'Explicit Minecraft EULA acceptance required'}
if(Test-Path $result){throw 'Preserve existing native climate evidence; never overwrite it'}
foreach($role in @('first','repeat')){if(Test-Path (Join-Path $instance "climate-state-$variant-$role")){throw 'Preserve existing climate control world'}}
$owner=[Diagnostics.Process]::GetCurrentProcess()
$priority=$owner.PriorityClass;$affinity=$owner.ProcessorAffinity
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($affinity.ToInt64() -band $CpuAffinity) -ne $CpuAffinity){throw 'Exactly four available logical CPUs required'}
$oldOptions=$env:JAVA_TOOL_OPTIONS
try{
    $owner.PriorityClass='BelowNormal';$owner.ProcessorAffinity=[IntPtr]$CpuAffinity
    foreach($role in @('first','repeat')){
        $folder=Join-Path $result $role
        New-Item -ItemType Directory -Force -Path $folder|Out-Null
        $crashes=@{};Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object{$crashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
        $env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -D${modId}.prefeatures.export=true -D${modId}.prefeatures.lightweight=true -D${modId}.prefeatures.variant=$variant -D${modId}.prefeatures.climateAudit=true -D${modId}.prefeatures.auditRole=$role -D${modId}.prefeatures.inputHash=$hash"
        if($RingContinuation){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.prefeatures.ringAudit=true"}
        if($ExportNative){$env:JAVA_TOOL_OPTIONS+=' -Dmixin.debug.export=true -Dmixin.debug.export.filter=net.minecraft.world.level.biome.Climate*'}
        [string[]]$network=if($Offline){@('--offline')}else{@()}
        Push-Location $repo
        try{
            $gradleCpu=if($RingContinuation){2}else{4}
            $nativeArguments=@("--args=nogui --world climate-state-$variant-$role --port 0")
            if($Target -ne '26.3-fabric'){
                $properties=Join-Path $instance 'server.properties'
                $previousProperties=if(Test-Path $properties){[IO.File]::ReadAllText($properties)}else{$null}
                [IO.File]::WriteAllText($properties,"level-name=climate-state-$variant-$role`nlevel-seed=0`nserver-ip=127.0.0.1`nserver-port=0`nonline-mode=false`nview-distance=2`nsimulation-distance=2`npause-when-empty-seconds=-1`n",[Text.UTF8Encoding]::new($false))
                $nativeArguments=@()
            }
            try {
                & .\gradlew.bat ":version:${target}:runValidationConformancePrefeaturesServer" @nativeArguments @network --max-workers=1 --no-daemon --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=$gradleCpu" *> (Join-Path $folder 'console.log')
            } finally {
                if($Target -ne '26.3-fabric' -and $null -ne $previousProperties){[IO.File]::WriteAllText($properties,$previousProperties,[Text.UTF8Encoding]::new($false))}
            }
            if($LASTEXITCODE){Get-Content (Join-Path $folder 'console.log') -Tail 35;throw "Native climate $role process failed"}
        }finally{Pop-Location}
        Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object{if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New native climate crash $($_.FullName)"}}
        Write-Output "COMPLETED native climate $role"
    }
    Assert-Evidence
}finally{
    $env:JAVA_TOOL_OPTIONS=$oldOptions
    $owner.ProcessorAffinity=$affinity;$owner.PriorityClass=$priority
}
