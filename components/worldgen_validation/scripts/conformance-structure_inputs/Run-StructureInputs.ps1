param([ValidateSet('1.21.1-neoforge','1.20.1-forge')][string]$Target = '1.21.1-neoforge', [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$expectedCount = if ($Target -eq '1.20.1-forge') { 15 } else { 18 }
$validation = Join-Path $repo 'validations/conformance-structure_inputs'
$output = Join-Path $validation "result/current/$target"
$prefix = (ConvertFrom-StringData (Get-Content (Join-Path $repo 'gradle.properties') -Raw)).mod_id + '.structureInputs.'

function Check-Evidence {
    $fixtures = @(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    if ($fixtures.Count -ne $expectedCount -or @($fixtures.id | Select-Object -Unique).Count -ne $expectedCount) { throw 'Incomplete native structure fixture manifest' }
    foreach ($fixture in $fixtures) {
        $root = Join-Path $output $fixture.id
        $a = Get-Content (Join-Path $root 'native/summary.json') -Raw | ConvertFrom-Json
        $b = Get-Content (Join-Path $root 'compact/summary.json') -Raw | ConvertFrom-Json
        if ($a.status -cne 'PRODUCED' -or $b.status -cne 'PASS' -or $a.pid -eq $b.pid -or $a.save -eq $b.save -or
                $a.hostSeed -ne $a.localSeed -or $b.hostSeed -eq $b.localSeed -or $a.featuresExecuted -ne 0 -or $b.featuresExecuted -ne 0 -or
                $b.compactInputs.evaluatedSources -ne 289 -or $b.compactInputs.referenceRadius -ne 8) { throw "Invalid native structure boundary $($fixture.id)" }
        foreach ($field in @('fixture','dimension','x','z','localSeed','environment','inputHash','beardifier')) {
            if (($a.$field | ConvertTo-Json -Depth 90 -Compress) -cne ($b.$field | ConvertTo-Json -Depth 90 -Compress)) { throw "Evidence differs $($fixture.id) $field" }
        }
        if ($a.fixture -cne $fixture.id -or $a.dimension -cne $fixture.dimension -or $a.x -ne $fixture.x -or $a.z -ne $fixture.z -or $a.localSeed -ne $fixture.local) { throw 'Result does not match the actual fixture' }
        foreach ($file in @('structures.json','beardifier.bin')) {
            if ((Get-FileHash (Join-Path $root "native/$file")).Hash -cne (Get-FileHash (Join-Path $root "compact/$file")).Hash) { throw "Native input differs $($fixture.id) $file" }
        }
        if ($fixture.structure) {
            $structure = Get-Content (Join-Path $root 'native/structures.json') -Raw | ConvertFrom-Json -AsHashtable
            $key = "minecraft:$($fixture.structure)@[$($fixture.startX), $($fixture.startZ)]"
            if (!$structure.pieces.ContainsKey($key) -or $structure.pieces[$key] -le 0) { throw "Positive fixture is empty $key" }
        }
        if ($fixture.terrainOnly -and $a.beardifier.nonzero -le 0) { throw 'Terrain-only halo has no native density influence' }
    }
    $structures = @('village_plains','mineshaft','monument','fortress','end_city','stronghold')
    if ($Target -ne '1.20.1-forge') { $structures += 'trial_chambers' }
    foreach ($structure in $structures) {
        foreach ($kind in @('start','neighbor')) {
            $fixture = @($fixtures | Where-Object id -CEQ "$structure-$kind")
            if ($fixture.Count -ne 1) { throw "Missing native positive $structure-$kind" }
            $distance = [Math]::Max([Math]::Abs($fixture[0].x - $fixture[0].startX), [Math]::Abs($fixture[0].z - $fixture[0].startZ))
            if (($kind -eq 'start' -and $distance -ne 0) -or ($kind -eq 'neighbor' -and ($distance -lt 1 -or $distance -gt 8))) { throw 'Incorrect structure influence fixture' }
        }
    }
    Write-Host "PASS: $target independent native/compact $expectedCount structure inputs and raw-double Beardifier fixtures."
}
if ($CheckOnly) { Check-Evidence; exit }
$local = Join-Path $repo 'local.toml'
if (!(Test-Path $local) -or !(Select-String -LiteralPath $local -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) { throw 'Accept the Minecraft EULA explicitly in local.toml' }
Add-Type -AssemblyName Microsoft.VisualBasic
$instance = Join-Path $validation "instance/$target/server"
New-Item -ItemType Directory -Force -Path $instance | Out-Null
$properties = Join-Path $instance 'server.properties'
$oldProperties = if (Test-Path $properties) { [IO.File]::ReadAllText($properties) } else { $null }
$oldOptions = $env:JAVA_TOOL_OPTIONS
$process = [Diagnostics.Process]::GetCurrentProcess()
$oldPriority = $process.PriorityClass; $oldAffinity = $process.ProcessorAffinity
$launcher = $null

function Recycle-World([string]$world) {
    $path = [IO.Path]::GetFullPath((Join-Path $instance $world))
    if (!$path.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $world -notmatch '^structure-input-[a-z0-9_-]+$') { throw 'Unsafe structure fixture cleanup' }
    if (Test-Path $path) {
        if ((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked validation world' }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    }
}

function Invoke-World([string]$world, [string]$seed, [string[]]$flags, [string]$log, [string]$proof, [string]$status) {
    Recycle-World $world
    [IO.File]::WriteAllText($properties, "level-name=$world`nlevel-seed=$seed`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
    $crashes = @{}
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { $crashes[$_.Name] = (Get-FileHash $_.FullName).Hash }
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 ' + ($flags -join ' ')
    if ($null -eq $launcher) {
        Push-Location $repo
        try {
            & .\gradlew.bat ":version:${target}:runValidationConformanceStructureInputsServer" --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' *> $log
            if ($LASTEXITCODE -ne 0) { Get-Content $log -Tail 40; throw 'Native structure-input export failed' }
        } finally { Pop-Location }
    } else {
        $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
        $start.WorkingDirectory = $launcher.directory; $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
        foreach ($item in $launcher.environment.PSObject.Properties) {
            if ($item.Name -cne 'MOD_CLASSES') { throw 'Unknown native loader environment key' }
            $start.Environment[$item.Name] = [string]$item.Value
        }
        foreach ($argument in $launcher.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launcher.classpath); $start.ArgumentList.Add($launcher.main)
        foreach ($argument in $launcher.gameArguments) { $start.ArgumentList.Add($argument) }
        $game = [Diagnostics.Process]::Start($start)
        $game.PriorityClass = 'BelowNormal'; $game.ProcessorAffinity = [IntPtr]3840
        $stdout = $game.StandardOutput.ReadToEndAsync(); $stderr = $game.StandardError.ReadToEndAsync()
        if (!$game.WaitForExit(300000)) { $game.Kill($true); $game.WaitForExit(); throw "Native structure fixture hung $world" }
        [IO.File]::WriteAllText($log, $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        $code = $game.ExitCode; $game.Dispose()
        if ($code -ne 0) { Get-Content $log -Tail 35; throw "Native structure fixture failed $world" }
    }
    Get-ChildItem (Join-Path $instance 'crash-reports') -File -ErrorAction SilentlyContinue | ForEach-Object { if (!$crashes.ContainsKey($_.Name) -or $crashes[$_.Name] -cne (Get-FileHash $_.FullName).Hash) { throw "New structure-input crash $($_.Name)" } }
    if ($proof -and (!(Test-Path $proof) -or (Get-Content $proof -Raw | ConvertFrom-Json).status -cne $status)) { throw "Missing/failed structure evidence $world" }
    Recycle-World $world
}

try {
    $process.PriorityClass = 'BelowNormal'; $process.ProcessorAffinity = [IntPtr]3840
    if (Test-Path $output) {
        $summaries = @(Get-ChildItem $output -Filter summary.json -Recurse)
        if ($summaries.Count -lt 2 * $expectedCount -and (Test-Path (Join-Path $output 'fixtures.json'))) {
            $incomplete = Join-Path $validation "result/first_incomplete/$target"
            if (!(Test-Path $incomplete)) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $incomplete) | Out-Null
                Copy-Item -LiteralPath $output -Destination $incomplete -Recurse
            }
        }
        $firstFailure = Join-Path $validation "result/first_failure/$target"
        $failed = @(Get-ChildItem $output -Filter summary.json -Recurse | Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).status -ceq 'FAIL' })
        if (($failed.Count -gt 0 -or !(Test-Path (Join-Path $output 'fixtures.json'))) -and !(Test-Path $firstFailure)) { New-Item -ItemType Directory -Force -Path (Split-Path -Parent $firstFailure) | Out-Null; Copy-Item -LiteralPath $output -Destination $firstFailure -Recurse }
        foreach ($entry in $failed) {
            $proof = Get-Content $entry.FullName -Raw | ConvertFrom-Json
            $saved = [IO.Path]::GetFullPath($proof.save)
            if (!$saved.StartsWith($instance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $proof.fixture -notmatch '^[a-z0-9_-]+$' -or $proof.role -notin @('native','compact')) { throw 'Unsafe failed-world archive' }
            $backup = Join-Path $instance "first-failure-$($proof.fixture)-$($proof.role)"
            if ((Test-Path $saved) -and !(Test-Path $backup)) {
                if ((Get-Item $saved).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing linked failure world' }
                Copy-Item -LiteralPath $saved -Destination $backup -Recurse
            }
        }
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($output, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    }
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Invoke-World 'structure-input-export' '0' @("-D$($prefix)export=true") (Join-Path $output 'export.log') '' ''
    $launcher = Get-Content (Join-Path $output 'launcher.json') -Raw | ConvertFrom-Json
    if ($launcher.main -cne 'net.neoforged.devlaunch.Main' -or @($launcher.gameArguments).Count -ne 1 -or !$launcher.gameArguments[0].StartsWith('@')) {
        Write-Host 'Unknown loader command shape: retain the native Gradle task for each fresh fixture.'
        $launcher = $null
    }
    $sources = @(Get-ChildItem (Join-Path $repo "versions/$target/src"), (Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
    $fingerprint = ($sources | ForEach-Object { $_.FullName.Substring($repo.Length) + ':' + (Get-FileHash $_.FullName).Hash }) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
    $fixtures = @(Get-Content (Join-Path $output 'fixtures.json') -Raw | ConvertFrom-Json)
    $count = 0
    foreach ($fixture in $fixtures) {
        $root = Join-Path $output $fixture.id; New-Item -ItemType Directory -Force -Path $root | Out-Null
        $flags = @("-D$($prefix)fixture=$($fixture.id)","-D$($prefix)dimension=$($fixture.dimension)","-D$($prefix)x=$($fixture.x)","-D$($prefix)z=$($fixture.z)","-D$($prefix)local=$($fixture.local)","-D$($prefix)inputHash=$hash")
        Invoke-World "structure-input-native-$($fixture.id)" $fixture.local ($flags + "-D$($prefix)role=native") (Join-Path $root 'native.log') (Join-Path $root 'native/summary.json') 'PRODUCED'
        $hostSeed = if ($fixture.local -eq '17') { '23' } else { '17' }
        Invoke-World "structure-input-compact-$($fixture.id)" $hostSeed ($flags + "-D$($prefix)role=compact") (Join-Path $root 'compact.log') (Join-Path $root 'compact/summary.json') 'PASS'
        $count++; Write-Host "STRUCTURE INPUT $count/$expectedCount PASS $($fixture.id)"
    }
    Check-Evidence
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldOptions; $process.PriorityClass = $oldPriority; $process.ProcessorAffinity = $oldAffinity
    if ($null -ne $oldProperties) { [IO.File]::WriteAllText($properties, $oldProperties) }
}
