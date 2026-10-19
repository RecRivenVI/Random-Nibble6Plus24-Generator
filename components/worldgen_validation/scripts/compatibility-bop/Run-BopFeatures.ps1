param([switch]$CheckOnly, [switch]$NativeRepeats, [string]$OnlyFixture)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$resultRoot = Join-Path $validationRepository "validations/$validationProject/result/feature_b/$validationTarget"
$terrainRoot = Join-Path $validationRepository "validations/$validationProject/result/bop_smoke_False_host17/$validationTarget"
$selection = Get-Content (Join-Path $terrainRoot 'selection.json') -Raw | ConvertFrom-Json
if ($OnlyFixture) { $selection.fixtures = @($selection.fixtures | Where-Object id -EQ $OnlyFixture); if ($selection.fixtures.Count -ne 1) { throw 'Unknown BOP fixture' } }
if ($CheckOnly -and $NativeRepeats) {
    foreach ($fixture in $selection.fixtures) {
        $summaries = @(1..3 | ForEach-Object { Get-Content (Join-Path $resultRoot "$($fixture.id)/native_repeat_$_/summary.json") -Raw | ConvertFrom-Json })
        if (@($summaries | Where-Object status -NE 'PRODUCED').Count -ne 0 -or @($summaries.pid | Select-Object -Unique).Count -ne 3) { throw 'Invalid native BOP repeats' }
        foreach ($field in @('fixture', 'hostSeed', 'localSeed', 'inputHash', 'environment', 'biomes', 'terrainHash')) {
            foreach ($summary in $summaries) {
                if (($summary.$field | ConvertTo-Json -Depth 80 -Compress) -cne ($summaries[0].$field | ConvertTo-Json -Depth 80 -Compress)) { throw "Native repeat prerequisite differs: $field" }
            }
        }
        $summaries | Select-Object fixture,pid,terrainHash,hash | Format-Table -AutoSize
    }
    'CAPTURED: fresh independent native B repeats; this does not waive differing results.'
    exit 0
}
if ($CheckOnly) {
    $count = 0
    foreach ($fixture in $selection.fixtures) {
        $folder = Join-Path $resultRoot $fixture.id
        $native = Get-Content (Join-Path $folder 'native/summary.json') -Raw | ConvertFrom-Json
        if ($native.status -ne 'PRODUCED' -or $native.hostSeed -ne $fixture.local) { throw 'Invalid native B proof' }
        foreach ($hostSeed in @(17, 23)) {
            $other = Get-Content (Join-Path $folder "host_$hostSeed/summary.json") -Raw | ConvertFrom-Json
            if ($other.status -ne 'PASS' -or !$other.physicalHostUnchanged -or $other.pid -eq $native.pid -or $other.save -eq $native.save -or $other.localSeed -eq $other.hostSeed) { throw 'Invalid independent B proof' }
            foreach ($field in @('hash', 'environment', 'biomes', 'rejected', 'inputHash')) {
                if (($other.$field | ConvertTo-Json -Depth 80 -Compress) -cne ($native.$field | ConvertTo-Json -Depth 80 -Compress)) { throw "BOP B differs: $($fixture.id) $field" }
            }
            if ((Get-FileHash (Join-Path $folder 'native/features.bin.gz')).Hash -ne (Get-FileHash (Join-Path $folder "host_$hostSeed/features.bin.gz")).Hash) { throw 'BOP B snapshot differs' }
            $count++
        }
    }
    if ($count -ne 18) { throw "Incomplete BOP B matrix $count/18" }
    "PASS: 9 fresh physical native B controls / 18 unrelated-host B comparisons."
    exit 0
}
$launcher = Get-Content (Join-Path $terrainRoot 'launcher.json') -Raw | ConvertFrom-Json
$prefix = (ConvertFrom-StringData (Get-Content (Join-Path $validationRepository 'gradle.properties') -Raw)).mod_id + '.prefeatures.'
$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$priority = $orchestrator.PriorityClass
$inputFiles = @(Get-ChildItem (Join-Path $validationRepository 'versions/26.3-fabric/src/main') -File -Recurse | Sort-Object FullName)
$fingerprint = ($inputFiles | ForEach-Object { $_.FullName.Substring($validationRepository.Length) + ':' + (Get-FileHash $_.FullName).Hash }) -join "`n"
$fingerprint += [IO.File]::ReadAllText((Join-Path $validationRepository 'inputs.toml'))
$inputHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
try {
    $orchestrator.PriorityClass = 'BelowNormal'
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4'
    & (Join-Path $validationRepository 'gradlew.bat') ':version:26.3-fabric:probeClasses' --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4'
    if ($LASTEXITCODE -ne 0) { throw 'BOP probe compilation failed' }
    if (!$NativeRepeats -and !$OnlyFixture -and (Test-Path $resultRoot)) { Move-ValidationToRecycleBin -LiteralPath $resultRoot }
    New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
    foreach ($fixture in $selection.fixtures) {
        $roles = if ($NativeRepeats) { @('native_repeat_1', 'native_repeat_2', 'native_repeat_3') } else { @('native', '17', '23') }
        foreach ($role in $roles) {
            $seed = if ($role.StartsWith('native')) { $fixture.local } else { $role }
            $world = "bop-b-$($fixture.id)-$role"
            $worldPath = Join-Path $validationServer $world
            if (Test-Path $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
            [IO.File]::WriteAllText($validationProperties, [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$seed"), [Text.UTF8Encoding]::new($false))
            $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -D$($prefix)featureB=true -D$($prefix)fixture=$($fixture.id) -D$($prefix)role=$role -D$($prefix)local=$($fixture.local) -D$($prefix)dimension=$($fixture.dimension) -D$($prefix)x=$($fixture.x) -D$($prefix)z=$($fixture.z) -D$($prefix)inputHash=$inputHash"
            $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
            $start.WorkingDirectory = $launcher.directory
            $start.UseShellExecute = $false
            $start.RedirectStandardOutput = $true
            $start.RedirectStandardError = $true
            foreach ($argument in $launcher.jvmArguments) { $start.ArgumentList.Add($argument) }
            $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launcher.classpath); $start.ArgumentList.Add($launcher.main)
            $arguments = @($launcher.gameArguments)
            $worldIndex = [Array]::IndexOf($arguments, '--world')
            if ($worldIndex -lt 0) { throw 'No template world argument' }
            $arguments[$worldIndex + 1] = $world
            foreach ($argument in $arguments) { $start.ArgumentList.Add($argument) }
            $process = [Diagnostics.Process]::Start($start)
            $process.PriorityClass = 'BelowNormal'
            if ([Environment]::ProcessorCount -ge 12) { $process.ProcessorAffinity = [IntPtr]3840 }
            $stdout = $process.StandardOutput.ReadToEndAsync()
            $stderr = $process.StandardError.ReadToEndAsync()
            $finished = $process.WaitForExit(300000)
            if (!$finished) { $process.Kill($true); $process.WaitForExit() }
            [IO.File]::WriteAllText((Join-Path $resultRoot "$($fixture.id)-$role.log"), $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
            $code = $process.ExitCode
            $process.Dispose()
            Move-ValidationToRecycleBin -LiteralPath $worldPath
            if (!$finished -or $code -ne 0) { throw "BOP B process failed: $world ($code)" }
            $folder = if ($role.StartsWith('native')) { $role } else { "host_$role" }
            $summary = Get-Content (Join-Path $resultRoot "$($fixture.id)/$folder/summary.json") -Raw | ConvertFrom-Json
            if ($summary.status -notin @('PASS', 'PRODUCED')) { throw "BOP B proof failed: $world $($summary.failure)" }
            "BOP B $($fixture.id) $role complete"
        }
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    $orchestrator.PriorityClass = $priority
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
}
& $PSCommandPath -CheckOnly -NativeRepeats:$NativeRepeats -OnlyFixture $OnlyFixture
