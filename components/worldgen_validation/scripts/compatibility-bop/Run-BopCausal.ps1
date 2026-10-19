param([switch]$CheckOnly, [switch]$NoBuild, [string]$OnlyRun)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$root = Join-Path $validationRepository 'validations/compatibility-bop/result/causal_end'
if ($CheckOnly) {
    & python (Join-Path $PSScriptRoot 'Check-BopCausal.py') $root
    if ($LASTEXITCODE -ne 0) { throw 'Causal proof remains incomplete or inconsistent' }
    exit 0
}
$launcher = Get-Content (Join-Path $validationRepository 'validations/compatibility-bop/result/bop_smoke_False_host17/26.3-fabric/launcher.json') -Raw | ConvertFrom-Json
$runs = @(
    @{ name = 'native_natural_1'; seed = '0' },
    @{ name = 'isolated_natural_17'; seed = '17' },
    @{ name = 'native_natural_2'; seed = '0' },
    @{ name = 'isolated_natural_23'; seed = '23' },
    @{ name = 'native_natural_3'; seed = '0' },
    @{ name = 'native_from_isolated17'; seed = '0'; order = 'isolated_natural_17' },
    @{ name = 'native_from_isolated23'; seed = '0'; order = 'isolated_natural_23' },
    @{ name = 'isolated17_from_native'; seed = '17'; order = 'native_natural_1' },
    @{ name = 'isolated23_from_native'; seed = '23'; order = 'native_natural_1' },
    @{ name = 'native_repeat_from_native'; seed = '0'; order = 'native_natural_1' },
    @{ name = 'isolated17_from_native3'; seed = '17'; order = 'native_natural_3' },
    @{ name = 'isolated23_from_native3'; seed = '23'; order = 'native_natural_3' },
    @{ name = 'native_from_recorded_order'; seed = '0'; order = 'natural_witness_fca' },
    @{ name = 'isolated17_from_recorded_order'; seed = '17'; order = 'natural_witness_fca' },
    @{ name = 'isolated23_from_recorded_order'; seed = '23'; order = 'natural_witness_fca' }
)
if ($OnlyRun) {
    $runs = @($runs | Where-Object name -EQ $OnlyRun)
    if ($runs.Count -ne 1) { throw 'Unknown causal run' }
}
$originalOptions = $env:JAVA_TOOL_OPTIONS
$originalProperties = [IO.File]::ReadAllText($validationProperties)
$orchestrator = [Diagnostics.Process]::GetCurrentProcess()
$priority = $orchestrator.PriorityClass
$affinity = $orchestrator.ProcessorAffinity
try {
    $orchestrator.PriorityClass = 'BelowNormal'
    if ([Environment]::ProcessorCount -ge 12) { $orchestrator.ProcessorAffinity = [IntPtr]3840 }
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4'
    if (!$NoBuild) {
        & (Join-Path $validationRepository 'gradlew.bat') ':version:26.3-fabric:probeClasses' --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4'
        if ($LASTEXITCODE -ne 0) { throw 'Causal probe compilation failed' }
    }
    $sourceFiles = @(Get-ChildItem (Join-Path $validationRepository 'versions/26.3-fabric/src/main'), (Join-Path $validationRepository 'versions/26.3-fabric/src/probe') -File -Recurse | Sort-Object FullName)
    $fingerprint = ($sourceFiles | ForEach-Object { $_.FullName.Substring($validationRepository.Length) + ':' + (Get-FileHash $_.FullName).Hash }) -join "`n"
    $fingerprint += [IO.File]::ReadAllText((Join-Path $validationRepository 'inputs.toml'))
    $inputHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $originalEvidence = @(Get-ChildItem (Join-Path $validationRepository 'validations/compatibility-bop/result/feature_b') -File -Recurse | ForEach-Object { @{ path = $_.FullName; hash = (Get-FileHash $_.FullName).Hash } })
    foreach ($run in $runs) {
        $output = Join-Path $root $run.name
        if (Test-Path $output) { Move-ValidationToRecycleBin -LiteralPath $output }
        $world = "bop-causal-$($run.name)"
        $worldPath = Join-Path $validationServer $world
        if (Test-Path $worldPath) { Move-ValidationToRecycleBin -LiteralPath $worldPath }
        [IO.File]::WriteAllText($validationProperties, [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', "level-seed=$($run.seed)"), [Text.UTF8Encoding]::new($false))
        $modId = (ConvertFrom-StringData (Get-Content (Join-Path $validationRepository 'gradle.properties') -Raw)).mod_id
        $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dmosaic.bop.causal=true -Dmosaic.bop.causal.run=$($run.name) -Dmosaic.bop.causal.inputHash=$inputHash -D$($modId).prefeatures.inputHash=$inputHash"
        if ($run.order) {
            $order = Join-Path $root "$($run.order)/trace.json"
            if (!(Test-Path $order)) { throw "Missing natural input $order" }
            $env:JAVA_TOOL_OPTIONS += " -Dmosaic.bop.causal.order=$order"
        }
        $start = [Diagnostics.ProcessStartInfo]::new($launcher.java)
        $start.WorkingDirectory = $launcher.directory
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in $launcher.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launcher.classpath); $start.ArgumentList.Add($launcher.main)
        $arguments = @($launcher.gameArguments)
        $worldIndex = [Array]::IndexOf($arguments, '--world')
        $portIndex = [Array]::IndexOf($arguments, '--port')
        if ($worldIndex -lt 0 -or $portIndex -lt 0) { throw 'Missing launcher world/port' }
        $arguments[$worldIndex + 1] = $world
        $arguments[$portIndex + 1] = '0'
        foreach ($argument in $arguments) { $start.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::Start($start)
        $process.PriorityClass = 'BelowNormal'
        if ([Environment]::ProcessorCount -ge 12) { $process.ProcessorAffinity = [IntPtr]3840 }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $finished = $process.WaitForExit(300000)
        if (!$finished) { $process.Kill($true); $process.WaitForExit() }
        [IO.File]::WriteAllText((Join-Path $root "$($run.name).log"), $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult())
        $code = $process.ExitCode
        $process.Dispose()
        if (!$finished -or $code -ne 0) { throw "Causal process failed: $world ($code); world retained" }
        $summary = Get-Content (Join-Path $output 'summary.json') -Raw | ConvertFrom-Json
        if ($summary.status -ne 'CAPTURED') { throw "Causal capture failed: $world" }
        Move-ValidationToRecycleBin -LiteralPath $worldPath
        "CAPTURED: $($run.name), hash=$($summary.hash)"
    }
    foreach ($evidence in $originalEvidence) {
        if ((Get-FileHash $evidence.path).Hash -cne $evidence.hash) { throw "Original evidence changed: $($evidence.path)" }
    }
    [IO.File]::WriteAllText((Join-Path $root 'original_evidence.json'), (ConvertTo-Json -InputObject $originalEvidence -Depth 5), [Text.UTF8Encoding]::new($false))
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    $orchestrator.PriorityClass = $priority
    $orchestrator.ProcessorAffinity = $affinity
    [IO.File]::WriteAllText($validationProperties, $originalProperties, [Text.UTF8Encoding]::new($false))
}
if (!$OnlyRun) { & $PSCommandPath -CheckOnly }
