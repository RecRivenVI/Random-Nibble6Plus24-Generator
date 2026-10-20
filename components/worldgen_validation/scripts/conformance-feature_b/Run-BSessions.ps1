param(
    [ValidateSet('26.3-neoforge')][string]$Target = '26.3-neoforge',
    [ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel = 'native_b_sessions',
    [long]$CpuAffinity = 61440,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
if ([Numerics.BitOperations]::PopCount([ulong]$CpuAffinity) -ne 4) { throw 'Exactly four logical CPUs required' }
$env:MOSAIC_VALIDATION_TARGET = $Target
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$resultRoot = Join-Path $validationRepository "validations/conformance-feature_b/result/sessions/$Target/$EvidenceLabel"
$output = Join-Path $resultRoot 'summary.json'
$sources = @(Get-ChildItem (Join-Path $validationRepository "versions/$Target/src/main"),(Join-Path $validationRepository "versions/$Target/src/probe"),(Join-Path $validationRepository 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
$sourceText = ($sources | ForEach-Object { $_.FullName.Substring($validationRepository.Length) + ':' + (Get-FileHash $_.FullName).Hash }) -join "`n"
$sourceText += [IO.File]::ReadAllText((Join-Path $validationRepository "versions/$Target/target.properties"))
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($sourceText)))
function Test-SessionResult {
    $summary = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($summary.schema -ne 1 -or $summary.status -cne 'PASS' -or $summary.inputHash -cne $hash -or
            $summary.scope -cne 'STANDALONE_B_CONTENT_ORDER_AND_FIVE_SESSIONS_NOT_PLAYER_OR_PERFORMANCE' -or
            $summary.serial -ne 5 -or $summary.reverseComparisons -ne 5 -or $summary.parallelComparisons -ne 5 -or
            $summary.peakSessions -ne 5 -or $summary.bindings -ne 0 -or !$summary.physicalHostUnchanged -or
            @($summary.fixtures.PSObject.Properties).Count -ne 5) { throw 'Incomplete B content/session evidence' }
    foreach ($id in @('origin','nonorigin','negative','fortress','end_spike')) {
        if ($summary.fixtures.$id -notmatch '^[a-f0-9]{64}$') { throw "Missing B fixture $id" }
    }
    $summary
}
if ($CheckOnly) { Test-SessionResult | ConvertTo-Json -Depth 5; exit 0 }
if (Test-Path -LiteralPath $resultRoot) { throw 'Preserve existing session evidence' }
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$worldName = "b-sessions-$EvidenceLabel"
$world = Join-Path $validationServer $worldName
if (Test-Path -LiteralPath $world) { throw 'Preserve previous session world' }
$priorOptions = $env:JAVA_TOOL_OPTIONS
$priorProperties = [IO.File]::ReadAllText($validationProperties)
$process = [Diagnostics.Process]::GetCurrentProcess()
$priorAffinity = $process.ProcessorAffinity
$priorPriority = $process.PriorityClass
$crashes = @{}
$crashRoot = Join-Path $validationServer 'crash-reports'
if (Test-Path $crashRoot) { Get-ChildItem $crashRoot -File | ForEach-Object { $crashes[$_.Name] = (Get-FileHash $_.FullName).Hash } }
try {
    $process.ProcessorAffinity = $CpuAffinity
    $process.PriorityClass = 'BelowNormal'
    [IO.File]::WriteAllText($validationProperties, "level-name=$worldName`nlevel-seed=0`nlevel-type=minecraft:normal`nserver-ip=127.0.0.1`nserver-port=0`nonline-mode=false`nview-distance=2`nsimulation-distance=5`n", [Text.UTF8Encoding]::new($false))
    $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 -Dfeatureb.independent=true -Dfeatureb.sessions=true -Dfeatureb.inputHash=$hash -Dfeatureb.sessionOutput=$($output.Replace('\','/'))"
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$Target`:runValidationConformanceFeatureBServer" --offline --max-workers=1 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=2' *> (Join-Path $resultRoot 'console.log')
    if ($LASTEXITCODE -ne 0) { throw 'B session game failed' }
    foreach ($report in @(Get-ChildItem $crashRoot -File -ErrorAction SilentlyContinue)) {
        if (!$crashes.ContainsKey($report.Name) -or $crashes[$report.Name] -cne (Get-FileHash $report.FullName).Hash) { throw 'New B session crash report' }
    }
    Test-SessionResult | ConvertTo-Json -Depth 5
    if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
} finally {
    $env:JAVA_TOOL_OPTIONS = $priorOptions
    [IO.File]::WriteAllText($validationProperties, $priorProperties, [Text.UTF8Encoding]::new($false))
    $process.ProcessorAffinity = $priorAffinity
    $process.PriorityClass = $priorPriority
}
