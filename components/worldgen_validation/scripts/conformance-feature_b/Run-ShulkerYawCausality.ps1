param([ValidateSet('1.21.1-neoforge','1.20.1-forge')][string]$Target='1.21.1-neoforge',[switch]$CheckOnly,[switch]$RingScope,[ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='',[ValidateSet('conformance-legacy_b','integration-world_identity')][string]$ValidationName='conformance-legacy_b',[long]$CpuAffinity=3840)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = $Target
$validation = Join-Path $repo "validations/$ValidationName"
if($ValidationName -eq 'integration-world_identity' -and $Target -ne '1.21.1-neoforge'){throw 'World identity integration is only adapted to 1.21.1'}
$sourceSuite=if($target -eq '1.21.1-neoforge'){'structures-shared'}else{'structures'}
$source = Join-Path $validation "result/$sourceSuite/$target"
if(!(Test-Path (Join-Path $source 'fixtures.json')) -and !$CheckOnly){
    $preparation=if($target -eq '1.21.1-neoforge'){'shared'}else{'independent'}
    & (Join-Path $PSScriptRoot 'Run-LegacyB.ps1') -Target $target -Suite structures -Preparation $preparation -ExportOnly -ValidationName $ValidationName -CpuAffinity $CpuAffinity
    if($LASTEXITCODE-ne0){throw 'Native causal fixture discovery failed'}
}
$evidenceVariant = 'end-yaw'
if($RingScope){$evidenceVariant='end-yaw-ring-scope'}
if($EvidenceLabel){$evidenceVariant+='-'+$EvidenceLabel}
$output = Join-Path $validation "result/$evidenceVariant/$target"
$instance = Join-Path $validation "instance/$target/server"
$fixture = @(Get-Content (Join-Path $source 'fixtures.json') -Raw | ConvertFrom-Json | Where-Object id -CEQ 'end_city-start')
if ($fixture.Count -ne 1) { throw 'Need independently discovered End City fixture' }
$fixture = $fixture[0]
$root = Join-Path $output $fixture.id
$roles = @('native_a','native_b','native_a_with_b','native_b_with_a')
$prefix = (ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id + '.featureBInputs.'

function Get-InputHash {
    $sources=@(Get-ChildItem (Join-Path $repo "versions/$target/src"),(Join-Path $repo 'components/worldgen_contract/src') -File -Recurse | Sort-Object FullName)
    $fingerprint=($sources | ForEach-Object {$_.FullName.Substring($repo.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}

function Check-Evidence {
    $rows = @()
    foreach ($role in $roles) {
        $path = Join-Path $root $role
        $row = Get-Content (Join-Path $path 'summary.json') -Raw | ConvertFrom-Json
        $draws = Get-Content (Join-Path $path 'shulker-yaw.json') -Raw | ConvertFrom-Json
        $replay = $role -like '*_with_*'
        if ($row.status -cne $(if ($replay) {'REPLAYED'} else {'CAPTURED'}) -or $row.role -cne $role -or $row.localSeed -ne $fixture.local -or $row.hostSeed -ne $fixture.local -or $row.x -ne $fixture.x -or $row.z -ne $fixture.z -or $row.dimension -cne $fixture.dimension -or $row.entities -le 0 -or $row.bindings -ne 0 -or !$row.physicalPoiUnchanged) { throw "Incomplete causal proof $role" }
        if($target -eq '1.21.1-neoforge' -and $row.liveContexts -ne 0){throw 'Live owner remained after causal generation'}
        if($RingScope -and $Target -eq '1.20.1-forge' -and (!$row.ringCandidateOptIn -or $row.physicalRingScopeBound)){throw 'Native causal control entered finite rings or missed the requested property'}
        if ($draws.matchedTargetEntities -ne $row.entities -or @($draws.constructors).Count -ne $row.entities -or $draws.mode -cne $(if ($replay) {'replay'} else {'capture'})) { throw 'Missing constructor-to-serialized-entity association' }
        foreach ($constructor in $draws.constructors) {
            if (@($constructor.actualMathBits).Count -ne 3 -or @($constructor.usedMathBits).Count -ne 3) { throw 'Missing native Math.random inputs' }
            if (!$replay -and ($constructor.actualMathBits -join ',') -cne ($constructor.usedMathBits -join ',')) { throw 'Native capture changed entropy' }
        }
        if ($replay -and !$row.fullReplayEqual) { throw 'Full UUID-only replay differs' }
        if ($role -ceq 'native_b' -and !$row.nativeDivergenceOnlyObservedYaw) { throw 'Native repeat did not isolate yaw' }
        foreach ($field in @('physicalEntitiesDelta','blockSchedulerDelta','fluidSchedulerDelta')) { if ($null -eq $row.$field -or $row.$field -ne 0) { throw 'Causal probe escaped physical lifecycle' } }
        foreach ($file in @('raw.nbt.gz','canonical.nbt.gz','canonical.txt','structures.json','physical-poi-before.json','physical-poi-after.json')) { if (!(Test-Path (Join-Path $path $file))) { throw "Missing raw evidence $file" } }
        if ($rows.Count) {
            foreach ($field in @('fixture','dimension','x','z','localSeed','inputHash','environment','structures','trace','preInputs','outerInputs','rejected')) {
                if (($row.$field | ConvertTo-Json -Depth 90 -Compress) -cne ($rows[0].$field | ConvertTo-Json -Depth 90 -Compress)) { throw "Causal input differs $field" }
            }
        }
        $rows += $row
    }
    if (@($rows.pid | Sort-Object -Unique).Count -ne 4 -or @($rows.save | Sort-Object -Unique).Count -ne 4) { throw 'Reused JVM/save' }
    if ($rows[0].inputHash -cne (Get-InputHash)) { throw 'Causal proof does not match current Target sources' }
    foreach ($pair in @(@('native_a_with_b','native_b'), @('native_b_with_a','native_a'))) {
        if ((Get-FileHash (Join-Path $root "$($pair[0])/canonical.txt")).Hash -cne (Get-FileHash (Join-Path $root "$($pair[1])/canonical.txt")).Hash) { throw 'Full canonical replay mismatch' }
        $used = Get-Content (Join-Path $root "$($pair[0])/shulker-yaw.json") -Raw | ConvertFrom-Json
        $expected = Get-Content (Join-Path $root "$($pair[1])/shulker-yaw.json") -Raw | ConvertFrom-Json
        for ($i=0; $i -lt @($expected.constructors).Count; $i++) {
            if (($used.constructors[$i].usedMathBits -join ',') -cne ($expected.constructors[$i].usedMathBits -join ',')) { throw 'Wrong replay tape' }
        }
    }
    $proof = [ordered]@{status='PASS';target=$target;fixture=$fixture;scope='NATIVE_CAUSAL_PROOF_NOT_B_PARITY';freshJvms=4;source='LivingEntity.<init> Math.random ordinal 2';bidirectionalFullUuidOnlyEqual=$true;entities=$rows[0].entities;inputHash=$rows[0].inputHash}
    [IO.File]::WriteAllText((Join-Path $output 'causal-proof.json'), ($proof | ConvertTo-Json -Depth 12)+"`n")
    'PASS: native constructor entropy fully replayed both ways; replay comparison is full UUID-only, not B parity.'
}
if ($CheckOnly) { Check-Evidence; exit }
if (!(Select-String -LiteralPath (Join-Path $repo 'local.toml') -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) { throw 'EULA not accepted' }
$launcher = Get-Content (Join-Path $source 'launcher.json') -Raw | ConvertFrom-Json
if ($launcher.main -cne 'net.neoforged.devlaunch.Main' -or [IO.Path]::GetFullPath($launcher.directory) -cne $instance) { throw 'Wrong native launch context' }
Add-Type -AssemblyName Microsoft.VisualBasic
function Recycle-World([string]$world) {
    $path = [IO.Path]::GetFullPath((Join-Path $instance $world))
    if (!$path.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $world -cnotmatch '^feature-b-yaw-native_[a-z_]+$') { throw 'Unsafe causal world cleanup' }
    if (Test-Path $path) {
        if ((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked causal world' }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
}
$properties = Join-Path $instance 'server.properties'
$oldProperties = [IO.File]::ReadAllText($properties)
$oldOptions = $env:JAVA_TOOL_OPTIONS
$process = [Diagnostics.Process]::GetCurrentProcess()
$oldPriority = $process.PriorityClass; $oldAffinity = $process.ProcessorAffinity
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($CpuAffinity -band $oldAffinity.ToInt64()) -ne $CpuAffinity){throw 'Correctness queue requires exactly four available CPUs'}
try {
    $process.PriorityClass='BelowNormal'; $process.ProcessorAffinity=[IntPtr]$CpuAffinity
    $failedNative=Join-Path $source 'end_city-start/native_1/summary.json'
    $originalFailure=Join-Path $validation "result/original_yaw_failure/$target"
    if ((Test-Path $failedNative) -and (Get-Content $failedNative -Raw | ConvertFrom-Json).status -ceq 'FAIL' -and !(Test-Path $originalFailure)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $originalFailure) | Out-Null
        Copy-Item -LiteralPath (Join-Path $source 'end_city-start') -Destination $originalFailure -Recurse
    }
    if (Test-Path $output) {
        $completed=Join-Path $output 'causal-proof.json'
        $baseline=Join-Path $validation "result/baseline_initial/end-yaw/$target"
        if ((Test-Path $completed) -and (Get-Content $completed -Raw | ConvertFrom-Json).status -ceq 'PASS' -and !(Test-Path $baseline)) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $baseline) | Out-Null
            Copy-Item -LiteralPath $output -Destination $baseline -Recurse
        }
        $first = Join-Path $validation "result/first_failure/end-yaw/$target"
        if (!(Test-Path $first)) { New-Item -ItemType Directory -Force -Path (Split-Path -Parent $first) | Out-Null; Copy-Item -LiteralPath $output -Destination $first -Recurse }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($output,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    }
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $hash=Get-InputHash
    foreach ($role in $roles) {
        $path=Join-Path $root $role; New-Item -ItemType Directory -Force -Path $path | Out-Null
        $world="feature-b-yaw-$role"
        $previousWorld=Join-Path $instance $world
        $failedWorld=Join-Path $instance "first-failure-$world"
        if ((Test-Path $previousWorld) -and !(Test-Path $failedWorld)) {
            if ((Get-Item $previousWorld).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked previous causal world' }
            Copy-Item -LiteralPath $previousWorld -Destination $failedWorld -Recurse
        }
        Recycle-World $world
        [IO.File]::WriteAllText($properties,"level-name=$world`nlevel-seed=$($fixture.local)`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
        $crashes=@{}; Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { $crashes[$_.Name]=(Get-FileHash $_.FullName).Hash }
        $mode=if($role -like '*_with_*'){'replay'}else{'capture'}
        $flags=@("-D$($prefix)suite=end-yaw","-D$($prefix)fixture=$($fixture.id)","-D$($prefix)role=$role","-D$($prefix)dimension=$($fixture.dimension)","-D$($prefix)local=$($fixture.local)","-D$($prefix)x=$($fixture.x)","-D$($prefix)z=$($fixture.z)","-D$($prefix)inputHash=$hash","-D$($prefix)yawMode=$mode")
        $flags+="-D$($prefix)variant=$evidenceVariant"
        $expected=if($role -ceq 'native_b'){'native_a'}elseif($role -ceq 'native_a_with_b'){'native_b'}elseif($role -ceq 'native_b_with_a'){'native_a'}else{$null}
        if($expected){$flags+="-D$($prefix)yawExpected=$(Join-Path $root $expected)"}
        if($mode -ceq 'replay'){$flags+="-D$($prefix)yawTape=$(Join-Path $root "$expected/shulker-yaw.json")"}
        $env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 '+($flags -join ' ')
        if($RingScope){$env:JAVA_TOOL_OPTIONS+=' -Drandomnibble6plus24generator.structure.lazyRings=true'}
        $start=[Diagnostics.ProcessStartInfo]::new($launcher.java);$start.WorkingDirectory=$instance;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        foreach($item in $launcher.environment.PSObject.Properties){$start.Environment[$item.Name]=[string]$item.Value}
        foreach($argument in $launcher.jvmArguments){$start.ArgumentList.Add($argument)}
        $start.ArgumentList.Add('-cp');$start.ArgumentList.Add($launcher.classpath);$start.ArgumentList.Add($launcher.main)
        foreach($argument in $launcher.gameArguments){$start.ArgumentList.Add($argument)}
        $game=[Diagnostics.Process]::Start($start);$game.PriorityClass='BelowNormal';$game.ProcessorAffinity=[IntPtr]$CpuAffinity
        $stdout=$game.StandardOutput.ReadToEndAsync();$stderr=$game.StandardError.ReadToEndAsync();$timedOut=!$game.WaitForExit(300000)
        if($timedOut){$game.Kill($true);$game.WaitForExit()}
        [IO.File]::WriteAllText((Join-Path $path 'console.log'),$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult());$code=$game.ExitCode;$game.Dispose()
        if($timedOut -or $code -ne 0){Get-Content (Join-Path $path 'console.log') -Tail 30;throw "Native causal process failed $role"}
        Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New causal crash $($_.Name)"} }
        $proof=Get-Content (Join-Path $path 'summary.json') -Raw | ConvertFrom-Json
        if($proof.status -cne $(if($mode -ceq 'replay'){'REPLAYED'}else{'CAPTURED'})){throw "Incomplete causal run $role"}
        Recycle-World $world
        Write-Host "NATIVE CAUSAL $role complete"
    }
    Check-Evidence
} finally { $env:JAVA_TOOL_OPTIONS=$oldOptions;$process.PriorityClass=$oldPriority;$process.ProcessorAffinity=$oldAffinity;[IO.File]::WriteAllText($properties,$oldProperties) }
