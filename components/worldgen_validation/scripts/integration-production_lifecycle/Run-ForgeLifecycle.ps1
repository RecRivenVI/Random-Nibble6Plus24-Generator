param([ValidateSet('shutdown','genuine','cancellation')][string]$Kind='shutdown', [ValidateSet('overworld','the_nether','the_end')][string]$Dimension='overworld', [switch]$KeepWorld, [switch]$PreserveFailureOnly, [ValidatePattern('^[a-z0-9_]+$')][string]$FailureLabel='native_failure', [switch]$CheckOnly, [switch]$Offline, [long]$CpuAffinity=3840)
$ErrorActionPreference='Stop'
if($KeepWorld -and $Kind -ne 'shutdown'){throw 'Only a clean shutdown fixture may be retained for native recovery'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target='1.20.1-forge'
$validation=Join-Path $repo 'validations/integration-legacy_physical'
$instance=Join-Path $validation "instance/$target/server"
$dimensionKey="minecraft:$Dimension";$leaf=$dimensionKey.Replace(':','-')
$result=Join-Path $validation "result/lifecycle_$Kind/$target"
if($PreserveFailureOnly){
    $from=Join-Path $result "b/$leaf"
    $destination=Join-Path $validation "result/preserved_failure/$FailureLabel/$Kind/$target/b/$leaf"
    if(!(Test-Path (Join-Path $from 'summary.json')) -or (Test-Path $destination)){throw 'Missing failure or existing immutable archive'}
    $proof=Get-Content (Join-Path $from 'summary.json') -Raw|ConvertFrom-Json
    if($proof.status -ceq 'PASS'){throw 'Refusing to archive a passing world as a failure'}
    New-Item -ItemType Directory -Force -Path (Split-Path $destination)|Out-Null
    Copy-Item $from $destination -Recurse
    $ownedSave=[IO.Path]::GetFullPath($proof.save)
    if(!$ownedSave.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Failure save is not owned by this validation'}
    if(Test-Path $ownedSave){
        if((Get-Item $ownedSave).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refusing linked failure save'}
        Copy-Item $ownedSave (Join-Path $destination 'world') -Recurse
    }
    Write-Host "PRESERVED $destination"
    exit
}
function Get-InputHash {
    $files=@(Get-ChildItem (Join-Path $repo "versions/$target/src/main"),(Join-Path $repo "versions/$target/src/probe"),(Join-Path $repo 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
    $fingerprint=($files|ForEach-Object{$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $fingerprint+=[IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}
$hash=Get-InputHash
$pipelines=if($Kind -eq 'shutdown'){@('vanilla','b')}else{@('b')}
function Assert-Evidence {
    if((Get-InputHash) -cne $hash){throw 'Native lifecycle source changed during verification'}
    $pids=@()
    foreach($pipeline in $pipelines){
        $common=Join-Path $result "$pipeline/$leaf"
        $proof=Get-Content (Join-Path $common 'summary.json') -Raw|ConvertFrom-Json
        if($proof.schema -ne 1 -or $proof.inputHash -cne $hash -or $proof.kind -cne $Kind -or
                $proof.scope -cne 'NATIVE_CLOSE_AND_FAILURE_NOT_FULL' -or $proof.minecraft -cne '1.20.1' -or
                $proof.target -cne $target -or $proof.dimension -cne $dimensionKey -or $proof.pipeline -cne $pipeline -or
                $proof.processors -ne 4 -or $proof.coldLoadedChunks -ne 0 -or
                $proof.afterStop.inFlight -ne 0 -or $proof.lifecycle.workers -ne 0 -or
                $proof.contextsRemaining -ne 0 -or $proof.contextsOpened -ne $proof.contextsClosed -or $proof.bindings -ne 0){throw 'Incomplete native lifecycle proof'}
        if($Kind -eq 'shutdown'){
            if($proof.status -cne 'PASS' -or !$proof.requestIncompleteAtStop -or $proof.stopSeconds -ge 20 -or $proof.retainedFailure){throw 'Expected lifecycle stop did not close cleanly'}
            if($pipeline -eq 'b' -and ($proof.preparationsBeforeStop -le 0 -or $proof.beforeStop.inFlight -le 0 -or $proof.contextsOpened -le 0 -or $proof.lifecycle.expectedTerminations -le 0)){throw 'No actual unfinished owned generation was cancelled'}
            if($pipeline -eq 'vanilla' -and ($proof.preparationsBeforeStop -ne 0 -or $proof.afterStop.generations -ne 0)){throw 'Vanilla entered Mosaic'}
        }else{
            $expected=if($Kind -eq 'genuine'){'java.lang.IllegalStateException: Injected genuine Mosaic generation failure'}else{'java.util.concurrent.CancellationException: Injected unexpected Mosaic cancellation'}
            if($proof.status -cne 'EXPECTED_FAILURE_OBSERVED' -or $proof.failure -or !$proof.nativeFutureFailureObserved -or $proof.failureObservation -cne 'NATIVE_GENERATION_STAGE_FUTURE' -or !$proof.retainedFailure.StartsWith($expected,[StringComparison]::Ordinal) -or $proof.retainedFailureIsExpected -or $proof.contextsOpened -le 0){throw 'Real exception or unowned cancellation was swallowed or not consumed through the native future'}
            $reports=@(Get-ChildItem (Join-Path $common 'crashes') -File)
            if($reports.Count -ne 1){throw 'Expected exactly one newly reported native generation crash'}
            $body=Get-Content $reports[0].FullName -Raw
            $console=Get-Content (Join-Path $common 'console.log') -Raw
            if(!$body.Contains($expected) -or !$body.Contains('MosaicPhysicalLevelState') -or
                    !$console.Contains('net.minecraft.ReportedException: Mosaic native physical generation')){throw 'Missing actual native reported exception'}
        }
        $pids+=$proof.pid
    }
    if(@($pids|Sort-Object -Unique).Count -ne $pipelines.Count){throw 'Lifecycle controls did not use independent JVMs'}
    Write-Host "PASS: $target $dimensionKey $Kind native lifecycle, current input $hash"
}
if($CheckOnly){Assert-Evidence;exit}
if(!(Select-String -LiteralPath (Join-Path $repo 'local.toml') -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)){throw 'Explicit EULA acceptance required'}
New-Item -ItemType Directory -Force -Path $instance|Out-Null
Add-Type -AssemblyName Microsoft.VisualBasic
function Recycle([string]$path){
    $path=[IO.Path]::GetFullPath($path)
    if(!$path.StartsWith($validation+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe lifecycle fixture cleanup'}
    if(Test-Path $path){
        if((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refusing linked validation data'}
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
}
$properties=Join-Path $instance 'server.properties'
$oldProperties=if(Test-Path $properties){[IO.File]::ReadAllText($properties)}else{$null}
$oldOptions=$env:JAVA_TOOL_OPTIONS
$process=[Diagnostics.Process]::GetCurrentProcess();$affinity=$process.ProcessorAffinity;$priority=$process.PriorityClass
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($CpuAffinity -band $affinity.ToInt64()) -ne $CpuAffinity){throw 'Four available logical CPUs required'}
$modId=(ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id
$facts=ConvertFrom-StringData (Get-Content (Join-Path $repo "versions/$target/target.properties") -Raw)
$port=[int][regex]::Match((Get-Content (Join-Path $validation 'validation.toml') -Raw),'(?m)^port\s*=\s*(\d+)\s*$').Groups[1].Value
try{
    $process.PriorityClass='BelowNormal';$process.ProcessorAffinity=[IntPtr]$CpuAffinity
    foreach($pipeline in $pipelines){
        $worldName="legacy-lifecycle-$Kind-$pipeline-$Dimension";$world=Join-Path $instance $worldName
        $common=Join-Path $result "$pipeline/$leaf"
        $archive=Join-Path $validation "result/first_failure/lifecycle_$Kind/$target/$pipeline/$leaf"
        if((Test-Path (Join-Path $common 'summary.json')) -and (Get-Content (Join-Path $common 'summary.json') -Raw|ConvertFrom-Json).status -ceq 'FAIL' -and !(Test-Path $archive)){
            New-Item -ItemType Directory -Force -Path (Split-Path $archive)|Out-Null;Copy-Item $common $archive -Recurse
        }
        Recycle $world;Recycle $common
        New-Item -ItemType Directory -Force -Path $world,$common|Out-Null
        $levelType='minecraft:normal'
        if($pipeline -eq 'b'){
            $pack=Join-Path $world 'datapacks/native-mosaic';$preset=Join-Path $pack "data/$modId/worldgen/world_preset/native_lifecycle.json"
            New-Item -ItemType Directory -Force -Path (Split-Path $preset)|Out-Null
            $dimensions=[ordered]@{}
            foreach($entry in @(@('overworld','overworld','overworld'),@('the_nether','nether','nether'),@('the_end','the_end','end'))){
                $source=if($entry[0] -eq 'the_end'){@{type='minecraft:the_end'}}else{@{type='minecraft:multi_noise';preset="minecraft:$($entry[1])"}}
                $profile=@{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=2;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
                $dimensions["minecraft:$($entry[0])"]=@{type="minecraft:$($entry[0])";generator=@{type="${modId}:development_b";biome_source=$source;settings="minecraft:$($entry[2])";mosaic_profile=$profile}}
            }
            [IO.File]::WriteAllText($preset,(@{dimensions=$dimensions}|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),(@{pack=@{pack_format=[int]$facts.data_pack_major;description='Disposable native lifecycle proof'}}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText((Join-Path $world 'mosaic-development-b.json'),(@{schema=1;pipeline=($target.Split('-')[0]+'-lightweight-b-1')}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
            $levelType="${modId}:native_lifecycle"
        }
        [IO.File]::WriteAllText($properties,"level-name=$worldName`nlevel-seed=123456789`nlevel-type=$levelType`nserver-ip=127.0.0.1`nserver-port=$port`nonline-mode=false`nview-distance=2`nsimulation-distance=2`n",[Text.UTF8Encoding]::new($false))
        $crashes=@{};Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|ForEach-Object{$crashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
        $env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -D${modId}.nativeLifecycle.kind=$Kind -D${modId}.nativeLifecycle.pipeline=$pipeline -D${modId}.nativeLifecycle.dimension=$dimensionKey -D${modId}.nativeLifecycle.inputHash=$hash"
        if($pipeline -eq 'b'){$env:JAVA_TOOL_OPTIONS+=" -D${modId}.developmentB=true"}
        [string[]]$network=if($Offline){@('--offline')}else{@()}
        $guardStop=[Threading.CancellationTokenSource]::new()
        $guard=Start-ThreadJob -ArgumentList $common,$repo,$PID,$guardStop.Token -ScriptBlock {
            param($output,$repository,$ownerPid,$token)
            while(!$token.WaitHandle.WaitOne(1000)){
                $summary=Join-Path $output 'summary.json'
                if(!(Test-Path $summary)){continue}
                try{$row=Get-Content $summary -Raw|ConvertFrom-Json}catch{continue}
                if($row.status -cne 'EXPECTED_STOP_REQUESTED' -or !$row.stopRequestedEpochMillis){continue}
                if([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()-$row.stopRequestedEpochMillis -lt 20000){continue}
                $all=@(Get-CimInstance Win32_Process)
                $game=$all|Where-Object{
                    $_.ProcessId -eq $row.pid -and $_.Name -eq 'java.exe' -and
                    $_.CommandLine.Replace('\\','\').Contains($repository) -and
                    $_.CommandLine -match 'validationIntegrationLegacyPhysicalServerRunProgramArgs'
                }|Select-Object -First 1
                if(!$game){break}
                $ancestor=$game;$owned=$false
                for($depth=0;$depth -lt 12 -and $ancestor;$depth++){
                    if($ancestor.ParentProcessId -eq $ownerPid){$owned=$true;break}
                    $parent=$ancestor.ParentProcessId
                    $ancestor=$all|Where-Object ProcessId -eq $parent|Select-Object -First 1
                }
                if(!$owned){throw 'Refusing to stop a lifecycle JVM not descended from this runner'}
                [IO.File]::WriteAllText((Join-Path $output 'budget-stop.json'),(@{
                    reason='EXPECTED_CLOSE_EXCEEDED_FIXED_20_SECONDS';pid=$game.ProcessId;inputHash=$row.inputHash
                    observedEpochMillis=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                }|ConvertTo-Json))
                & (Join-Path (Split-Path $game.ExecutablePath) 'jcmd.exe') $game.ProcessId Thread.print *> (Join-Path $output 'budget-thread-dump.txt')
                Stop-Process -Id $game.ProcessId
                break
            }
        }
        Push-Location $repo
        try{& .\gradlew.bat ":version:${target}:runValidationIntegrationLegacyPhysicalServer" @network --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> (Join-Path $common 'console.log');$code=$LASTEXITCODE}finally{
            Pop-Location
            $guardStop.Cancel()
            $guard|Wait-Job -Timeout 5|Out-Null
            if($guard.State -eq 'Running'){$guard|Stop-Job}
            $guard|Receive-Job -ErrorAction Stop|Out-Host
            $guard|Remove-Job
            $guardStop.Dispose()
        }
        if(Test-Path (Join-Path $common 'budget-stop.json')){throw 'Native lifecycle exceeded its fixed close bound; owned JVM terminated and evidence retained'}
        $new=@(Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue|Where-Object{!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash})
        if($Kind -eq 'shutdown' -and ($code -ne 0 -or $new.Count -ne 0)){throw 'Normal native shutdown crashed'}
        if($Kind -ne 'shutdown'){
            New-Item -ItemType Directory -Force -Path (Join-Path $common 'crashes')|Out-Null
            $new|Copy-Item -Destination (Join-Path $common 'crashes')
        }
        if(!(Test-Path (Join-Path $common 'summary.json'))){throw 'Native lifecycle did not produce evidence'}
    }
    Assert-Evidence
    if(!$KeepWorld){foreach($pipeline in $pipelines){Recycle (Join-Path $instance "legacy-lifecycle-$Kind-$pipeline-$Dimension")}}
}catch{
    if(!(Test-Path $archive)){
        New-Item -ItemType Directory -Force -Path $archive|Out-Null
        if(Test-Path $common){Copy-Item (Join-Path $common '*') $archive -Recurse}
        foreach($path in @($world,(Join-Path $instance 'logs/latest.log'),(Join-Path $instance 'crash-reports'))){if(Test-Path $path){Copy-Item $path $archive -Recurse}}
        [IO.File]::WriteAllText((Join-Path $archive 'failure.txt'),($_|Out-String))
    }
    throw
}finally{
    $env:JAVA_TOOL_OPTIONS=$oldOptions
    if($null -ne $oldProperties){[IO.File]::WriteAllText($properties,$oldProperties,[Text.UTF8Encoding]::new($false))}
    $process.ProcessorAffinity=$affinity;$process.PriorityClass=$priority
}
