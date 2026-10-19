param([ValidateSet('26.3-fabric','26.3-neoforge','26.1.2-neoforge')][string]$Target = '26.3-fabric', [switch]$CheckOnly, [switch]$Smoke, [switch]$Handoff, [ValidatePattern('^[a-z0-9_-]*$')][string]$FixtureId = '', [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='', [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='', [long]$CpuAffinity=3840, [switch]$AttributeCaptureOnly, [string]$AttributeReplaySource='')
$ErrorActionPreference = 'Stop'
if ($Smoke -and $FixtureId) { throw 'Choose smoke or one diagnostic fixture, not both' }
if($FixtureId -ceq 'nether-chests' -and $Target -ne '26.3-fabric'){throw 'Natural Nether diagnostic has only been adapted to native 26.3 Fabric'}
if ($Handoff -and $Target -ne '26.1.2-neoforge') { throw 'Detached handoff verification is currently adapted only to 26.1.2 NeoForge' }
if($EvidenceLabel -and $Target -notin @('26.3-fabric','26.3-neoforge')){throw 'Independent result-base overrides require an audited modern native launcher'}
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical source identity is read-only'}
if($AttributeCaptureOnly -and ($Target -cne '26.3-fabric' -or $FixtureId -cne 'nether-chests' -or !$EvidenceLabel -or $Handoff -or $Smoke)){throw 'Attribute-order diagnostic needs a scoped native Fabric Nether capture'}
if($AttributeReplaySource -and !$AttributeCaptureOnly){throw 'Replay is diagnostic-only, never an independent parity acceptance path'}
$roles=if($AttributeCaptureOnly){@('native_0')}else{@('native_0','native_1','host_17','host_23')}
$env:MOSAIC_VALIDATION_TARGET = $Target
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$mode = if ($Smoke) { 'independent_smoke' } else { 'independent' }
if($Handoff){$mode='independent_handoff'+$(if($Smoke){'_smoke'}elseif($FixtureId){"_one_$FixtureId"}else{''})}
$defaultResultBase=Join-Path $validationRepository "validations/$validationProject/result"
$resultBase=if($EvidenceLabel){Join-Path $defaultResultBase $EvidenceLabel}else{$defaultResultBase}
$resultRoot = Join-Path $resultBase "$mode/$validationTarget"
$worldSuffix=if($EvidenceLabel){'_'+$EvidenceLabel}else{''}
$selectionPath = Join-Path $resultRoot 'selection.json'
if($FixtureId -ceq 'nether-chests' -and !$AttributeCaptureOnly){
    $causalPath=Join-Path $defaultResultBase 'piglin_order_iterator-effective-causality.json'
    $causal=Get-Content $causalPath -Raw|ConvertFrom-Json
    if($causal.schema -ne 2 -or $causal.status -cne 'PASS_CAUSAL_ONLY' -or $causal.scope -cne 'NATIVE_PIGLIN_ATTRIBUTE_LIST_ORDER_NOT_B_PARITY_ACCEPTANCE'){throw 'Need effective bidirectional native piglin order proof'}
    & (Join-Path $PSScriptRoot 'Run-PiglinOrderCausality.ps1') -CheckOnly -EvidenceLabel piglin_order_iterator -ExpectedInputHash $causal.inputHash -CpuAffinity $CpuAffinity
    if($LASTEXITCODE -ne 0){throw 'Independent native piglin order proof failed recheck'}
}
function Get-InputHash {
    $sources=@(Get-ChildItem (Join-Path $validationRepository "versions/$validationTarget/src/main"),(Join-Path $validationRepository "versions/$validationTarget/src/probe"),(Join-Path $validationRepository 'components/worldgen_contract/src') -File -Recurse|Sort-Object FullName)
    $text=($sources|ForEach-Object{$_.FullName.Substring($validationRepository.Length)+':'+(Get-FileHash $_.FullName).Hash}) -join "`n"
    $text+=[IO.File]::ReadAllText((Join-Path $validationRepository "versions/$validationTarget/target.properties"))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
}
$inputHash=if($ExpectedInputHash){$ExpectedInputHash.ToUpperInvariant()}else{Get-InputHash}
if ($CheckOnly) {
    $selection = Get-Content $selectionPath -Raw | ConvertFrom-Json
    if($selection.inputHash -cne $inputHash){throw 'Independent B evidence belongs to another source input'}
    if($EvidenceLabel){
        $nativeConfig=Get-Content (Join-Path $resultRoot 'native-launch-config.json') -Raw|ConvertFrom-Json
        $ownedConfig=Join-Path $validationServer $(if($Target -eq '26.3-fabric'){"native-dli-$EvidenceLabel.cfg"}else{"native-args-$EvidenceLabel.txt"})
        if($nativeConfig.owned -cne $ownedConfig -or $nativeConfig.requestedPort -ne 0 -or
                (Get-FileHash $ownedConfig).Hash -cne $nativeConfig.ownedSha256){throw 'Owned native launch configuration changed'}
    }
    $expected = if ($FixtureId) { 1 } elseif ($Smoke) { 4 } elseif ($Target -eq '26.1.2-neoforge') { 18 } else { 19 }
    if ($selection.fixtures.Count -ne $expected -or [bool]$selection.smoke -ne [bool]$Smoke -or [bool]$selection.handoff -ne [bool]$Handoff -or [string]$selection.requestedFixture -cne $FixtureId) { throw 'Incomplete independent B selection' }
    if ($FixtureId -and $selection.fixtures[0].id -cne $FixtureId) { throw 'Wrong diagnostic B fixture' }
    if([bool]$selection.attributeCaptureOnly -ne [bool]$AttributeCaptureOnly -or [string]$selection.attributeReplaySource -cne $AttributeReplaySource){throw 'Wrong diagnostic capture/replay scope'}
    $total = 0
    $positiveEffects = @{entities=0;blockEntities=0;blockTicks=0;fluidTicks=0;postProcessing=0;pendingBlockEntities=0}
    foreach ($fixture in $selection.fixtures) {
        $rows = @()
        foreach ($role in $roles) {
            if($Handoff -and !$role.StartsWith('native_')){
                $handoffRoot=Join-Path $resultRoot "$($fixture.id)/$role"
                $handoffProof=Get-Content (Join-Path $handoffRoot 'summary.json') -Raw | ConvertFrom-Json
                if(!$handoffProof.artifactRoundtrip -or !$handoffProof.nativeLoadControl -or $handoffProof.spawnBiomeCells -le 0){throw 'B handoff did not preserve native target/load semantics or carry real spawn palettes'}
                foreach($file in @('handoff-before.nbt.gz','handoff-after.nbt.gz','handoff-native-load-control.nbt.gz')){if(!(Test-Path (Join-Path $handoffRoot $file))){throw 'Missing raw B handoff transport evidence'}}
            }
            $folder = Join-Path $resultRoot "$($fixture.id)/$role"
            $row = Get-Content (Join-Path $folder 'summary.json') -Raw | ConvertFrom-Json
            if($EvidenceLabel){
                $execution=Get-Content (Join-Path $resultRoot "$($fixture.id)-$role.log.execution.json") -Raw|ConvertFrom-Json
                $expectedWorld="independent-b-$($fixture.id)-$role$worldSuffix"
                if($execution.pid -ne $row.pid -or $execution.affinity -ne $CpuAffinity -or $execution.priority -cne 'BelowNormal' -or
                        $execution.nativeConfig -cne $nativeConfig.owned -or $execution.nativeConfigSha256 -cne $nativeConfig.ownedSha256 -or $execution.requestedPort -ne 0 -or
                        $execution.world -cne $expectedWorld -or
                        [IO.Path]::GetFullPath($row.save).TrimEnd([IO.Path]::DirectorySeparatorChar) -cne
                        [IO.Path]::GetFullPath((Join-Path $validationServer $expectedWorld)).TrimEnd([IO.Path]::DirectorySeparatorChar)){throw 'Independent native launch ownership/resources differ'}
                $nativeLog=[IO.File]::ReadAllText((Join-Path $resultRoot "$($fixture.id)-$role.log"))
                if($nativeLog -notmatch 'Starting Minecraft server on [^\r\n]+:0[\r\n]' -or $nativeLog.Contains('FAILED TO BIND TO PORT')){throw 'Independent server did not use its isolated ephemeral port'}
            }
            if ($row.status -cne $(if ($role -eq 'native_0') { 'PRODUCED' } else { 'PASS' }) -or
                    $row.role -cne $role -or $row.actualWriters -ne 1 -or $row.bindings -ne 0 -or
                    $row.trace.maxConcurrentWriters -ne 1 -or $row.trace.decorationSeedReads -ne 1 -or
                    $row.trace.featureSeedCalls -le 0 -or $row.trace.requested.Count -ne 1 -or $row.inputHash -cne $selection.inputHash -or
                    ($row.fixture | ConvertTo-Json -Compress) -cne ($fixture | ConvertTo-Json -Compress)) { throw "Invalid independent B evidence $($fixture.id) $role" }
            if ($role.StartsWith('native_')) {
                if ($row.hostSeed -ne $fixture.seed) { throw 'Native seed not S(C)' }
            } else {
                if ($row.hostSeed -eq $fixture.seed -or !$row.physicalHostUnchanged) { throw 'B host seed/isolation failed' }
            }
            if ($role -eq 'native_0') {
                foreach ($effect in @($positiveEffects.Keys)) { $positiveEffects[$effect] += [long]$row.$effect }
                if (!$Smoke -and $fixture.id -in @('village','village-edge','mineshaft','mineshaft-edge','trial','monument','stronghold','fortress','end-city','nether-chests')) {
                    if ($row.intersectingPieces -le 0 -or $row.structurePlacements.PSObject.Properties.Count -le 0) { throw "Structure placement positive was not hit: $($fixture.id)" }
                }
                if ($fixture.id -in @('mineshaft','end-spike','end-city','nether-chests') -and $row.entities -le 0) { throw "Entity positive was not hit: $($fixture.id)" }
                if ($fixture.id -eq 'spawn-village-cat' -and $row.nativeStructureReads -le 0) { throw 'Actual native cat structure predicate was not observed' }
                if ($Target -eq '26.1.2-neoforge' -and $fixture.id -eq 'trial' -and @($row.outerReads.PSObject.Properties).Count -le 0) { throw 'Native Trial outer-read positive was not observed' }
            }
            if ($role -ne 'native_0') {
                if($fixture.id -ceq 'nether-chests' -and $row.finiteEntityRule -cne '26.3-native-bastion-piglin-attribute-order-1'){throw 'Wrong limited piglin acceptance rule'}
                $comparison = Get-Content (Join-Path $folder 'comparison.json') -Raw | ConvertFrom-Json
                if (!$comparison.accepted -or $null -ne $comparison.nonEntityDifference -or $comparison.entities.unexpectedDifferences.Count -ne 0) { throw 'New unexplained B/native difference' }
                foreach ($field in @('environment','inputHash','terrainHash','biomes','trace')) {
                    if (($row.$field | ConvertTo-Json -Depth 80 -Compress) -cne ($rows[0].$field | ConvertTo-Json -Depth 80 -Compress)) { throw "Independent B prerequisite differs: $field" }
                }
                if ($Target -eq '26.1.2-neoforge' -and ($row.outerReads | ConvertTo-Json -Depth 20 -Compress) -cne ($rows[0].outerReads | ConvertTo-Json -Depth 20 -Compress)) { throw 'Native outer prerequisite state differs' }
                if ((Get-FileHash (Join-Path $folder 'structures.json')).Hash -cne
                        (Get-FileHash (Join-Path $resultRoot "$($fixture.id)/native_0/structures.json")).Hash) { throw 'Independent referenced structure metadata differs' }
            }
            $rows += $row
            if($AttributeCaptureOnly){
                $tape=Get-Content (Join-Path $folder 'attribute-order.json') -Raw|ConvertFrom-Json
                if(@($tape.records).Count -ne 8 -or $tape.scope -cne 'DIAGNOSTIC_CAUSAL_TAPE_NOT_ACCEPTANCE_OR_PRODUCTION_SORT' -or $tape.mode -cne $(if($AttributeReplaySource){'replay'}else{'capture'})){throw 'Missing actual attribute-order tape'}
                if($AttributeReplaySource -and (!$tape.fullReplayComparison.strictParity -or !$tape.fullReplayComparison.accepted)){throw 'Diagnostic replay did not reproduce full target'}
            }
            $total++
        }
        if (@($rows.pid | Sort-Object -Unique).Count -ne $roles.Count -or @($rows.save | Sort-Object -Unique).Count -ne $roles.Count) { throw 'B evidence reused JVM/save' }
    }
    if (!$Smoke -and !$FixtureId) {
        foreach ($effect in @('entities','blockEntities','blockTicks','fluidTicks','postProcessing')) {
            if ($positiveEffects[$effect] -le 0) { throw "No real positive B fixture: $effect" }
        }
    }
    $positiveEffects | ConvertTo-Json -Compress
    if($AttributeCaptureOnly){"CAPTURED: native serialization-order diagnostic only, NOT independent B parity acceptance."}else{"PASS: $expected independent B fixtures, $total fresh JVM/save results; native repeats + two unrelated hosts."}
    Write-Output $(if($ExpectedInputHash){"INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT $inputHash"}else{"INPUT_SCOPE: CURRENT_SOURCE $inputHash"})
    exit 0
}

$process = [Diagnostics.Process]::GetCurrentProcess()
$oldPriority = $process.PriorityClass
$oldAffinity = $process.ProcessorAffinity
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or ($oldAffinity.ToInt64() -band $CpuAffinity) -ne $CpuAffinity){throw 'Exactly four available logical CPUs required'}
$oldOptions = $env:JAVA_TOOL_OPTIONS
$oldProperties = [IO.File]::ReadAllText($validationProperties)
$worldPath = $null
$privateNativeConfig=$null
$preFeaturesPrefix = (ConvertFrom-StringData (Get-Content (Join-Path $validationRepository 'gradle.properties') -Raw)).mod_id + '.prefeatures.'
$crashDirectory = Join-Path $validationServer 'crash-reports'
$oldCrashes = @{}
if (Test-Path $crashDirectory) {
    Get-ChildItem $crashDirectory -File | ForEach-Object { $oldCrashes[$_.Name] = (Get-FileHash $_.FullName).Hash }
}
$smokeFlag = if ($Smoke) { ' -Dfeatureb.smoke=true' } else { '' }
if($FixtureId -ceq 'nether-chests'){$smokeFlag+=' -Dfeatureb.naturalNether=true'}
if($AttributeCaptureOnly){$smokeFlag+=' -Dfeatureb.attributeOrder=true'}
if($AttributeReplaySource){$smokeFlag+=' "-Dfeatureb.attributeReplay='+$AttributeReplaySource+'"'}
if($Handoff){$smokeFlag+=" -Dfeatureb.handoff=true -Dfeatureb.variant=$mode"}

function Preserve-FirstFailure {
    $failureRoot = Join-Path $validationRepository "validations/$validationProject/result/independent_first_failure/$validationTarget/$mode"
    $failed = @(Get-ChildItem $resultRoot -Filter 'summary.json' -Recurse -File -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).status -ceq 'FAIL' })
    if ($failed.Count -gt 0 -and !(Test-Path $failureRoot)) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $failureRoot) -Force | Out-Null
        Copy-Item -LiteralPath $resultRoot -Destination $failureRoot -Recurse
    }
}

function Invoke-NativeCommand {
    param([object]$Launch, [string]$World, [string]$Log)
    if ($Target.EndsWith('-neoforge') -and ($Launch.main -cne 'net.neoforged.devlaunch.Main' -or
            @($Launch.gameArguments).Count -ne 1 -or !$Launch.gameArguments[0].StartsWith('@'))) {
        if($EvidenceLabel){throw 'Scoped NeoForge proof requires the actual native argument-file launcher'}
        & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureBServer" @gradleArguments *> $Log
        if ($LASTEXITCODE -ne 0) { Get-Content $Log -Tail 55; throw 'Native loader launch failed' }
        return
    }
    $start = [Diagnostics.ProcessStartInfo]::new($Launch.java)
    $start.WorkingDirectory = $Launch.directory
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Launch.jvmArguments) {
        if($EvidenceLabel -and $argument.StartsWith('-Dfabric.dli.config=')){continue}
        $start.ArgumentList.Add($argument)
    }
    if($EvidenceLabel -and $Target -eq '26.3-fabric'){$start.ArgumentList.Add("-Dfabric.dli.config=$privateNativeConfig")}
    if($EvidenceLabel){$start.ArgumentList.Add("-Dvalidation.result=$resultBase")}
    $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($Launch.classpath); $start.ArgumentList.Add($Launch.main)
    $arguments = @($Launch.gameArguments)
    if($EvidenceLabel -and $Target -eq '26.3-neoforge'){$arguments=@('@'+$privateNativeConfig)}
    if ($Target -eq '26.3-fabric') {
        $index = [Array]::IndexOf($arguments, '--world')
        if ($index -lt 0) { throw 'Missing native launcher world argument' }
        $arguments[$index + 1] = $World
        if($EvidenceLabel){
            # Exported gameArguments already include the DLI-added task arguments.
            $ports=0
            for($i=0;$i -lt $arguments.Count;$i++){
                if($arguments[$i] -ceq '--port'){
                    if($i+1 -ge $arguments.Count -or $arguments[$i+1] -notmatch '^\d+$'){throw 'Unknown exported server port argument'}
                    $arguments[$i+1]='0';$ports++
                }
            }
            if($ports -eq 0){throw 'Exported native server port is missing'}
        }
    }
    foreach ($argument in $arguments) { $start.ArgumentList.Add($argument) }
    $child = [Diagnostics.Process]::Start($start)
    $child.PriorityClass = 'BelowNormal'
    $child.ProcessorAffinity = [IntPtr]$CpuAffinity
    if($EvidenceLabel){
        $execution=@{schema=1;pid=$child.Id;affinity=$child.ProcessorAffinity.ToInt64();priority=$child.PriorityClass.ToString();world=$World;nativeConfig=$privateNativeConfig;nativeConfigSha256=(Get-FileHash $privateNativeConfig).Hash;requestedPort=0;gameArguments=$arguments}
        [IO.File]::WriteAllText($Log+'.execution.json',($execution|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    }
    $stdout = $child.StandardOutput.ReadToEndAsync()
    $stderr = $child.StandardError.ReadToEndAsync()
    if (!$child.WaitForExit(300000)) {
        $child.Kill($true); $child.WaitForExit()
        [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        throw 'Independent B exceeded five-minute test bound'
    }
    [IO.File]::WriteAllText($Log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
    $exitCode = $child.ExitCode
    $child.Dispose()
    if ($exitCode -ne 0) { Get-Content $Log -Tail 55; throw "Independent B process failed: $World" }
}

. (Join-Path $PSScriptRoot '../Native-ServerLaunch.ps1')

function Set-OracleWorld {
    param([string]$World, [string]$Seed)
    $text = [regex]::Replace($oldProperties, '(?m)^level-seed=.*$', "level-seed=$Seed")
    if ($text -match '(?m)^level-name=') { $text = [regex]::Replace($text, '(?m)^level-name=.*$', "level-name=$World") }
    else { $text += "`nlevel-name=$World`n" }
    [IO.File]::WriteAllText($validationProperties, $text, [Text.UTF8Encoding]::new($false))
}

$gradleArguments = @('--max-workers=2', '--no-daemon', '--no-configuration-cache', '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4')

try {
    $process.PriorityClass = 'BelowNormal'
    $process.ProcessorAffinity = [IntPtr]$CpuAffinity
    if($EvidenceLabel -and (Test-Path $resultRoot)){throw 'Preserve existing scoped independent B evidence; never overwrite it'}
    Preserve-FirstFailure
    if (Test-Path $resultRoot) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    $exportWorld='independent-b-export'+$worldSuffix
    $worldPath = Join-Path $validationServer $exportWorld
    if (Test-Path $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
    Set-OracleWorld -World $exportWorld -Seed '0'
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 -Dfeatureb.independent=true -Dfeatureb.export=true' + $smokeFlag
    $launchArguments = @($gradleArguments)
    if ($Target -eq '26.3-fabric') { $launchArguments += '--args=nogui --world independent-b-export --port 0' }
    if($EvidenceLabel){
        # Reuse only the template task's launch configuration. Export fixtures in a NEW JVM/save.
        $bootstrap=Get-Content (Join-Path $defaultResultBase "$mode/$validationTarget/launcher.json") -Raw|ConvertFrom-Json
        $portIndex=[Array]::IndexOf(@($bootstrap.gameArguments),'--port')
        if($Target -eq '26.3-neoforge'){
            if([IO.Path]::GetFullPath($bootstrap.directory) -cne [IO.Path]::GetFullPath($validationServer) -or $bootstrap.main -cne 'net.neoforged.devlaunch.Main' -or
                    $bootstrap.jvmArguments -cnotcontains "-Dvalidation.target=$validationTarget" -or @($bootstrap.gameArguments).Count -ne 1 -or !$bootstrap.gameArguments[0].StartsWith('@')){throw 'Unsafe or foreign NeoForge launcher bootstrap'}
        }elseif([IO.Path]::GetFullPath($bootstrap.directory) -cne [IO.Path]::GetFullPath($validationServer) -or
                $bootstrap.main -cne 'net.fabricmc.devlaunchinjector.Main' -or
                $bootstrap.jvmArguments -cnotcontains '-Dfabric.dli.env=server' -or
                $bootstrap.jvmArguments -cnotcontains "-Dvalidation.target=$validationTarget" -or
                $portIndex -lt 0 -or $portIndex+1 -ge @($bootstrap.gameArguments).Count -or
                $bootstrap.gameArguments[$portIndex+1] -cne '0'){throw 'Unsafe or foreign native launcher bootstrap'}
        $privateNativeConfig=New-OwnedNativeConfig $bootstrap
        Invoke-NativeCommand -Launch $bootstrap -World $exportWorld -Log (Join-Path $resultRoot 'export.log')
        $exportExit=0
    }else{
        & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureBServer" @launchArguments *> (Join-Path $resultRoot 'export.log')
        $exportExit=$LASTEXITCODE
    }
    if ($exportExit -ne 0 -or !(Test-Path (Join-Path $resultRoot 'launcher.json'))) { Get-Content (Join-Path $resultRoot 'export.log') -Tail 55; throw 'Independent B export failed' }
    Move-ValidationToRecycleBin -LiteralPath $worldPath
    $launch = Get-Content (Join-Path $resultRoot 'launcher.json') -Raw | ConvertFrom-Json
    $fixtures = @(Get-Content (Join-Path $resultRoot 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($Smoke) { $fixtures = @($fixtures | Where-Object id -In @('origin','nonorigin','nether','end-spike')) }
    if ($FixtureId) {
        $fixtures = @($fixtures | Where-Object id -CEQ $FixtureId)
        if ($fixtures.Count -ne 1) { throw 'Unknown diagnostic B fixture' }
    }
    [IO.File]::WriteAllText($selectionPath, (@{smoke=[bool]$Smoke;handoff=[bool]$Handoff;requestedFixture=$FixtureId;fixtures=$fixtures;inputHash=$inputHash;attributeCaptureOnly=[bool]$AttributeCaptureOnly;attributeReplaySource=$AttributeReplaySource} | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))
    foreach ($fixture in $fixtures) {
        foreach ($role in $roles) {
            $world = "independent-b-$($fixture.id)-$role$worldSuffix"
            $worldPath = Join-Path $validationServer $world
            if (Test-Path $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
            $seed = if ($role.StartsWith('native_')) { [string]$fixture.seed } else { $role.Substring(5) }
            Set-OracleWorld -World $world -Seed $seed
            $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeatureb.independent=true -Dfeatureb.fixture=$($fixture.id) -Dfeatureb.role=$role -Dfeatureb.inputHash=$inputHash -D$($preFeaturesPrefix)inputHash=$inputHash" + $smokeFlag
            $log = Join-Path $resultRoot "$($fixture.id)-$role.log"
            Invoke-NativeCommand -Launch $launch -World $world -Log $log
            $summaryPath = Join-Path $resultRoot "$($fixture.id)/$role/summary.json"
            if (!(Test-Path $summaryPath)) { throw 'No independent B summary' }
            $summary = Get-Content $summaryPath -Raw | ConvertFrom-Json
            if ($summary.status -cne $(if ($role -eq 'native_0') { 'PRODUCED' } else { 'PASS' })) { throw "Independent B failed: $($fixture.id) $role" }
            Move-ValidationToRecycleBin -LiteralPath $worldPath
            "INDEPENDENT B $($fixture.id) $role complete"
        }
    }
    if (Test-Path $crashDirectory) {
        foreach ($report in Get-ChildItem $crashDirectory -File) {
            if (!$oldCrashes.ContainsKey($report.Name) -or $oldCrashes[$report.Name] -cne (Get-FileHash $report.FullName).Hash) { throw "New independent B crash report: $($report.Name)" }
        }
    }
    if((Get-InputHash) -cne $inputHash){throw 'Target source changed during independent B validation'}
} catch {
    Preserve-FirstFailure
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldOptions
    [IO.File]::WriteAllText($validationProperties, $oldProperties, [Text.UTF8Encoding]::new($false))
    $process.PriorityClass = $oldPriority
    $process.ProcessorAffinity = $oldAffinity
}
& $PSCommandPath -Target $Target -CheckOnly -Smoke:$Smoke -Handoff:$Handoff -FixtureId $FixtureId -EvidenceLabel $EvidenceLabel -CpuAffinity $CpuAffinity -AttributeCaptureOnly:$AttributeCaptureOnly -AttributeReplaySource $AttributeReplaySource
