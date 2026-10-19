param([ValidateSet('1.21.1-neoforge','1.20.1-forge')][string]$Target='1.21.1-neoforge', [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='', [switch]$CheckOnly, [long]$CpuAffinity = 61440)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$env:MOSAIC_VALIDATION_TARGET = $Target
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$target = $validationTarget
$variant='narrow_lock_concurrency'+$(if($EvidenceLabel){'_'+$EvidenceLabel}else{''})
if($EvidenceLabel -and $target -ne '1.20.1-forge'){throw 'Label not adapted to this Target'}
$root = Join-Path $validationRepository "validations/conformance-structure_inputs/result/$variant/$target"
$manifest = if($target -eq '1.20.1-forge') {
    Join-Path $validationRepository "validations/conformance-legacy_terrain/result/ring_scope_structures_climate_tail/$target/fixtures.json"
} else { Join-Path $validationRepository "validations/conformance-prefeatures/result/narrow_locks_ring_scope_physical_biomes_structures/$target/fixtures.json" }
$expectedStructures=if($target -eq '1.20.1-forge'){6}else{7}
$expectedJobs=if($target -eq '1.20.1-forge'){2}else{5}
$sources = @(Get-ChildItem (Join-Path $validationRepository "versions/$target/src"), (Join-Path $validationRepository 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
$fingerprint = ($sources | ForEach-Object { $_.FullName.Substring($validationRepository.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
$fingerprint += [IO.File]::ReadAllText((Join-Path $validationRepository "versions/$target/target.properties"))
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))

function Check-Evidence {
    $rows = @('candidate_first','reference_first') | ForEach-Object {
        $row = Get-Content -LiteralPath (Join-Path $root "$_/summary.json") -Raw | ConvertFrom-Json
        if ($row.status -cne 'PASS' -or $row.order -cne $_ -or $row.inputHash -cne $hash -or
                !$row.templateCacheInitiallyEmpty -or $row.structures -ne $expectedStructures -or $row.parallelComparisons -ne $expectedStructures*3 -or
                $row.maxConcurrentJobs -lt 2 -or $row.maxConcurrentJobs -gt $expectedJobs -or $row.processors -ne 4 -or
                $row.hostSeed -ne 17 -or !$row.physicalChunksUnchanged -or $row.featuresExecuted -ne 0 -or @($row.projections).Count -ne $expectedStructures) { throw 'Incomplete cold parallel structure proof' }
        if($target -eq '1.20.1-forge' -and ($row.coldPoolSizes -le 0 -or $row.scope -cne 'NATIVE_COLD_LOCK_COMPONENT_NOT_INDEPENDENT_ORACLE')){throw 'Missing native Forge cold-cache boundary'}
        if($target -eq '1.20.1-forge' -and ($row.rotationCache.status -cne 'PASS' -or
                $row.rotationCache.groups -ne 48 -or $row.rotationCache.directions -ne 6 -or
                $row.rotationCache.parallelRounds -ne 64 -or !$row.rotationCache.nativeFailureReported -or
                !$row.rotationCache.recovered -or $row.rotationCache.activeInitializers -ne 0)){throw 'Missing native rotation/cache exception proof'}
        $row
    }
    if ($rows[0].pid -eq $rows[1].pid -or ($rows[0].projections | ConvertTo-Json -Compress) -cne ($rows[1].projections | ConvertTo-Json -Compress)) { throw 'Cold-order structure results differ or reused a JVM' }
    $conditions = Get-Content -LiteralPath (Join-Path $root 'conditions.json') -Raw | ConvertFrom-Json
    if ($conditions.inputHash -cne $hash -or $conditions.fixtureHash -cne (Get-FileHash -LiteralPath (Join-Path $root 'fixtures.json')).Hash -or $conditions.cpu -ne 4) { throw 'Cold comparison conditions changed' }
    "PASS: $expectedStructures native structures, $($expectedStructures*6) concurrent comparisons, two cold-cache orders in independent JVMs; not an independent worldgen Oracle or performance A/B."
}
if ($CheckOnly) { Check-Evidence; exit }
if ([Convert]::ToString($CpuAffinity,2).Replace('0','').Length -ne 4) { throw 'Exactly four logical CPUs required' }
$launcherPath = Join-Path $validationRepository "validations/conformance-structure_inputs/result/current/$target/launcher.json"
$launcher = Get-Content -LiteralPath $launcherPath -Raw | ConvertFrom-Json
if ($launcher.main -cne 'net.neoforged.devlaunch.Main' -or @($launcher.gameArguments).Count -ne 1 -or !$launcher.gameArguments[0].StartsWith('@')) { throw 'Run the native structure-input export before cold comparisons' }
if ([IO.Path]::GetFullPath($launcher.directory) -cne [IO.Path]::GetFullPath($validationServer) -or
        ![IO.Path]::GetFullPath($launcher.gameArguments[0].Substring(1)).StartsWith($validationRepository+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Native launcher points outside this validation instance/repository' }
if($target -eq '1.20.1-forge') {
    $program=Get-Content -LiteralPath $launcher.gameArguments[0].Substring(1) -Raw
    if($program -notmatch '(?m)^mosaic-worldgen\.mixins\.json\s*$' -or $program -notmatch '(?m)^mosaic-structure-input-test\.mixins\.json\s*$'){
        throw 'Refresh the native Forge prepareValidationConformanceStructureInputsServerRun before comparison: product/probe Mixin bootstrap is missing'
    }
}
$oldProperties = [IO.File]::ReadAllText($validationProperties)
$oldOptions = $env:JAVA_TOOL_OPTIONS
$process = [Diagnostics.Process]::GetCurrentProcess()
$priority = $process.PriorityClass
$affinity = $process.ProcessorAffinity
$world = $null
try {
    $process.PriorityClass = 'BelowNormal'; $process.ProcessorAffinity = [IntPtr]$CpuAffinity
    if (Test-Path -LiteralPath $root) {
        if (Test-Path -LiteralPath (Join-Path $root 'first_failure')) {
            if (@(Get-ChildItem $root -Filter summary.json -Recurse).Count) { throw 'Preserve the failed in-game cold comparison before rerunning' }
            $retained = $root + '_launcher_failure'
            if (Test-Path -LiteralPath $retained) { throw 'Previous launcher failure archive already exists' }
            Move-Item -LiteralPath $root -Destination $retained
        } else {
            if (@(Get-ChildItem $root -Filter summary.json -Recurse | Where-Object {(Get-Content $_.FullName -Raw | ConvertFrom-Json).status -cne 'PASS'}).Count) { throw 'Preserve the failed cold comparison before rerunning' }
            Move-ValidationToRecycleBin -LiteralPath $root
        }
    }
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    Copy-Item -LiteralPath $manifest -Destination (Join-Path $root 'fixtures.json')
    $conditions = @{inputHash=$hash; fixtureHash=(Get-FileHash -LiteralPath $manifest).Hash; cpu=4; affinity=$CpuAffinity; priority='BelowNormal'; independentNativeOracle=$false; runIdentity=[Guid]::NewGuid().ToString('D')}
    [IO.File]::WriteAllText((Join-Path $root 'conditions.json'), ($conditions | ConvertTo-Json))
    foreach ($order in @('candidate_first','reference_first')) {
        $out = Join-Path $root $order
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        $world = Join-Path $validationServer ("structure-input-locks-$order"+$(if($EvidenceLabel){'_'+$EvidenceLabel}else{''}))
        if (Test-Path $world) { throw 'Existing cold test world retained; do not overwrite it' }
        [IO.File]::WriteAllText($validationProperties, "level-name=$(Split-Path -Leaf $world)`nlevel-seed=17`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
        $crashes=@{};Get-ChildItem (Join-Path $validationServer 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object {$crashes[$_.Name]=(Get-FileHash $_.FullName).Hash}
        $env:JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -Drandomnibble6plus24generator.structure.lazyRings=true -Drandomnibble6plus24generator.structureInputs.lockProof=true -Drandomnibble6plus24generator.structureInputs.lockOrder=$order -Drandomnibble6plus24generator.structureInputs.inputHash=$hash"
        if($target -eq '1.20.1-forge'){$env:JAVA_TOOL_OPTIONS+=" -Drandomnibble6plus24generator.structure.narrowLocks=true -Drandomnibble6plus24generator.structureInputs.lockVariant=$variant"}
        $start=[Diagnostics.ProcessStartInfo]::new($launcher.java);$start.WorkingDirectory=$launcher.directory;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        if ($launcher.PSObject.Properties['environment']) {
            foreach($entry in $launcher.environment.PSObject.Properties){if($entry.Name -cne 'MOD_CLASSES'){throw 'Unknown native loader environment'};$start.Environment[$entry.Name]=[string]$entry.Value}
        }
        foreach($arg in $launcher.jvmArguments){$start.ArgumentList.Add($arg)}
        $start.ArgumentList.Add('-cp');$start.ArgumentList.Add($launcher.classpath);$start.ArgumentList.Add($launcher.main)
        foreach($arg in $launcher.gameArguments){$start.ArgumentList.Add($arg)}
        $game=[Diagnostics.Process]::Start($start);$game.PriorityClass='BelowNormal';$game.ProcessorAffinity=[IntPtr]$CpuAffinity
        $stdout=$game.StandardOutput.ReadToEndAsync();$stderr=$game.StandardError.ReadToEndAsync()
        $game.WaitForExit();[IO.File]::WriteAllText((Join-Path $out 'console.log'),$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult())
        $code=$game.ExitCode;$game.Dispose()
        Get-ChildItem (Join-Path $validationServer 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object {if(!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash){throw "New cold-comparison crash: $($_.Name)"}}
        if($code -ne 0 -or !(Test-Path (Join-Path $out 'summary.json')) -or (Get-Content (Join-Path $out 'summary.json') -Raw|ConvertFrom-Json).status -cne 'PASS'){throw "Cold comparison failed: $order"}
        Move-ValidationToRecycleBin -LiteralPath $world
        "COLD STRUCTURES $order complete"
    }
    Check-Evidence
} catch {
    $archive=Join-Path $root 'first_failure'
    if(!(Test-Path $archive)) {
        New-Item -ItemType Directory -Force -Path $archive|Out-Null
        foreach($path in @((Join-Path $validationServer 'logs/latest.log'),(Join-Path $validationServer 'crash-reports'),$world)) {if($path -and (Test-Path $path)){Copy-Item -LiteralPath $path -Destination $archive -Recurse}}
        [IO.File]::WriteAllText((Join-Path $archive 'failure.txt'),($_|Out-String))
    }
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS=$oldOptions
    [IO.File]::WriteAllText($validationProperties,$oldProperties)
    $process.PriorityClass=$priority;$process.ProcessorAffinity=$affinity
}
