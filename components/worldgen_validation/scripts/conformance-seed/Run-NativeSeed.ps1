param([switch]$CheckOnly, [switch]$PhysicalPlan, [switch]$RuntimeIdentity, [switch]$WorldLoadIdentity, [switch]$Offline, [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='', [long]$CpuAffinity=3840)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = '1.20.1-forge'
$validationRoot = Join-Path $repoRoot 'validations/conformance-seed'
if($WorldLoadIdentity){$RuntimeIdentity=$true}
if($PhysicalPlan -and $RuntimeIdentity){throw 'Native dependency and runtime identity are separate proofs'}
$resultKind = if($PhysicalPlan){'native-plan'}elseif($WorldLoadIdentity){'world-load-identity'}elseif($RuntimeIdentity){'runtime-identity'}else{'current'}
if($EvidenceLabel -and !$PhysicalPlan -and !$RuntimeIdentity){throw 'Evidence labels require a distinct native proof'}
if($EvidenceLabel){$resultKind+='-'+$EvidenceLabel}
$resultRoot = Join-Path $validationRoot "result/$resultKind/$target"
$resultPath = Join-Path $resultRoot $(if($PhysicalPlan){'native-plan.json'}else{'native-seed.json'})
if($PhysicalPlan -or $RuntimeIdentity){
    $sourceFiles=@(Get-ChildItem (Join-Path $repoRoot "versions/$target/src"),(Join-Path $repoRoot 'components/worldgen_contract/src') -Recurse -File|Sort-Object FullName)
    $fingerprint=($sourceFiles|ForEach-Object {$_.FullName.Substring($repoRoot.Length)+':'+(Get-FileHash -LiteralPath $_.FullName).Hash}) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $repoRoot "versions/$target/target.properties"))
    $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
}

function Assert-Result {
    $proof = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if($PhysicalPlan){
        if($proof.status -cne 'PASS' -or $proof.schema -ne 1 -or $proof.target -cne $target -or
                $proof.inputHash -cne $hash -or $proof.scope -cne 'NATIVE_DEPENDENCY_METADATA_NOT_PHYSICAL_FULL' -or
                $proof.nativeLifecycle -cne 'ServerStartedEvent' -or $proof.checks.Count -ne 36 -or
                !$proof.mods.forge -or !$proof.mods.randomnibble6plus24generator -or !$proof.mods.randomnibble6plus24generator_probe -or
                @($proof.checks|Where-Object {!$_.nativeEqual}).Count -ne 0){throw 'Incomplete native dependency metadata proof'}
        Write-Host "PASS: $target 36 native dependency plans; not physical FULL acceptance."
        return
    }
    if ($proof.status -ne 'PASS' -or $proof.target -ne $target -or $proof.schema -ne 1 -or
            $proof.nativeLifecycle -ne 'ServerStartedEvent' -or $proof.minecraft -ne '1.20.1' -or
            $proof.checks.Count -ne 45 -or !$proof.mods.forge -or
            !$proof.mods.randomnibble6plus24generator -or !$proof.mods.randomnibble6plus24generator_probe) {
        throw 'Incomplete native Forge seed/format proof'
    }
    foreach ($check in $proof.checks) {
        if (($check.expected | ConvertTo-Json -Depth 30 -Compress) -cne
                ($check.actual | ConvertTo-Json -Depth 30 -Compress)) { throw "Native assertion differs: $($check.check)" }
    }
    if($RuntimeIdentity){
        if($proof.inputHash -cne $hash -or $proof.runtimeIdentity.status -cne 'PASS' -or
                $proof.runtimeIdentity.scope -cne 'OWNERSHIP_STATE_NATIVE_GAME_NOT_PRODUCTION_WORLD_LOAD' -or
                $proof.runtimeIdentity.checks.Count -ne 17 -or
                @($proof.runtimeIdentity.checks|Where-Object {!$_.exact}).Count -ne 0){throw 'Incomplete native runtime ownership proof'}
        Write-Host "PASS: $target 17 world authority/SavedData checks in the native game; not production FULL acceptance."
        if($WorldLoadIdentity){
            $lifecycle=$proof.nativeIdentityLifecycle
            if($lifecycle.status -cne 'PASS' -or $lifecycle.scope -cne 'NATIVE_VANILLA_WORLD_LOAD_REPLACE_CLOSE_NOT_MOSAIC_FULL' -or
                    $lifecycle.dimensions -ne 3 -or $lifecycle.checks.Count -ne 16 -or $lifecycle.retainedLevelReferences -ne 0 -or
                    @($lifecycle.checks|Where-Object {!$_.exact}).Count -ne 0){throw 'Incomplete actual load/replacement/native close proof'}
            Write-Host "PASS: $target 16 actual Vanilla identity load/replacement/close checks across three dimensions; not Mosaic FULL acceptance."
        }
    }
    Write-Host "PASS: $target native seed/format 45 exact assertions, including 18 frozen seed vectors."
}

if ($CheckOnly) { Assert-Result; exit }
$local = Join-Path $repoRoot 'local.toml'
if (!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) {
    throw 'Accept the EULA explicitly in local.toml before running native verification'
}
Add-Type -AssemblyName Microsoft.VisualBasic
$instance = Join-Path $validationRoot "instance/$target/server"
New-Item -ItemType Directory -Force -Path $instance, $resultRoot | Out-Null
$properties = Join-Path $instance 'server.properties'
$original = if (Test-Path $properties) { [IO.File]::ReadAllText($properties) } else { $null }
$worldName = if($PhysicalPlan){'native-physical-plan'}else{'native-seed-contract'}
if($RuntimeIdentity){$worldName='native-runtime-identity'}
if($WorldLoadIdentity){$worldName='native-world-load-identity'}
if($EvidenceLabel){$worldName+='-'+$EvidenceLabel}
$world = Join-Path $instance $worldName
if(($PhysicalPlan -or $RuntimeIdentity) -and (Test-Path $resultPath) -and
        (Get-Content $resultPath -Raw|ConvertFrom-Json).status -ceq 'FAIL'){
    throw 'Preserve the failed native proof and world before rerunning'
}
if (Test-Path $world) {
    $resolved = (Resolve-Path -LiteralPath $world).Path
    if (!$resolved.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe world cleanup path' }
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolved, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
}
$oldFailure = Join-Path $validationRoot 'result/first_failure/plain-junit'
if (!(Test-Path $oldFailure)) {
    $oldReports = Join-Path $repoRoot "versions/$target/build/test-results/test"
    if (Test-Path $oldReports) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $oldFailure) | Out-Null
        Copy-Item -LiteralPath $oldReports -Destination $oldFailure -Recurse
    }
}
if (Test-Path $resultPath) {
    if(($PhysicalPlan -or $RuntimeIdentity) -and (Get-Content $resultPath -Raw|ConvertFrom-Json).status -ceq 'FAIL'){throw 'Preserve the failed native proof before rerunning'}
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($resultPath, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
}
$crashes = @{}
Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { $crashes[$_.FullName] = (Get-FileHash $_.FullName).Hash }
$process = [Diagnostics.Process]::GetCurrentProcess()
$priority = $process.PriorityClass
$affinity = $process.ProcessorAffinity
if($CpuAffinity -le 0 -or [System.Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4 -or
        ($CpuAffinity -band $affinity.ToInt64()) -ne $CpuAffinity){throw 'Native validation requires four available logical CPUs'}
$oldOptions = $env:JAVA_TOOL_OPTIONS
try {
    $process.PriorityClass = 'BelowNormal'
    $process.ProcessorAffinity = [IntPtr]$CpuAffinity
    $configuration=[IO.File]::ReadAllText((Join-Path $validationRoot 'validation.toml'))
    $serverSection=[regex]::Match($configuration,'(?ms)^\[instances\.server\]\s*\r?\n(?<body>.*?)(?=^\[|\z)').Groups['body'].Value
    $portMatch=[regex]::Match($serverSection,'(?m)^port\s*=\s*(\d+)\s*$')
    if(!$portMatch.Success){throw 'Native validation has no registered server port'}
    $port=[int]$portMatch.Groups[1].Value
    [IO.File]::WriteAllText($properties, "level-name=$worldName`nlevel-seed=123456789`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nserver-port=$port`nview-distance=2`nsimulation-distance=2`n")
    if($PhysicalPlan){$env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -Drandomnibble6plus24generator.physicalPlan.enabled=true -Drandomnibble6plus24generator.physicalPlan.inputHash=$hash -Drandomnibble6plus24generator.physicalPlan.variant=$resultKind"}
    if($RuntimeIdentity){$env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -Drandomnibble6plus24generator.runtimeIdentity.enabled=true -Drandomnibble6plus24generator.runtimeIdentity.inputHash=$hash -Drandomnibble6plus24generator.runtimeIdentity.variant=$resultKind"}
    if($WorldLoadIdentity){$env:JAVA_TOOL_OPTIONS+=' -Drandomnibble6plus24generator.runtimeIdentity.worldLoad=true'}
    [string[]]$network=if($Offline){@('--offline')}else{@()}
    Push-Location $repoRoot
    try {
        $console=Join-Path $resultRoot 'console.log'
        & .\gradlew.bat ":version:${target}:runValidationConformanceSeedServer" @network --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> $console
        if ($LASTEXITCODE -ne 0) { Get-Content $console -Tail 35|Out-Host; throw 'Native Forge seed/format game failed' }
    } finally { Pop-Location }
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object {
        if (!$crashes.ContainsKey($_.FullName) -or $crashes[$_.FullName] -cne (Get-FileHash $_.FullName).Hash) { throw "New native game crash: $($_.FullName)" }
    }
    Assert-Result
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($world, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
} catch {
    if($PhysicalPlan -or $RuntimeIdentity){
        $failureRoot=Join-Path $resultRoot 'first_failure'
        if(!(Test-Path $failureRoot)){
            New-Item -ItemType Directory -Path $failureRoot -Force|Out-Null
            foreach($path in @($world,(Join-Path $instance 'logs/latest.log'),(Join-Path $instance 'crash-reports'))){
                if(Test-Path $path){Copy-Item -LiteralPath $path -Destination $failureRoot -Recurse}
            }
            Get-ChildItem $resultRoot -File|Copy-Item -Destination $failureRoot
            [IO.File]::WriteAllText((Join-Path $failureRoot 'failure.txt'),($_|Out-String))
        }
    }
    throw
} finally {
    if ($null -ne $original) { [IO.File]::WriteAllText($properties, $original) }
    $process.PriorityClass = $priority
    $process.ProcessorAffinity = $affinity
    $env:JAVA_TOOL_OPTIONS = $oldOptions
}
