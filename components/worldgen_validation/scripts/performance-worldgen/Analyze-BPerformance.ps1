param(
    [ValidateSet('26.3-fabric', '26.3-neoforge')][string]$Target = '26.3-fabric',
    [ValidateSet('overworld', 'the_nether', 'the_end')][string]$Dimension = 'overworld',
    [ValidateSet('b', 'ordering2', 'vanilla', 'b-source-reference', 'b-source-audit', 'b-biome-reference', 'b-physical-biome-reference', 'b-biome-lock-reference', 'b-physical-terrain-reference', 'b-load-reference', 'b-origin-reference')][string[]]$Pipelines = @('b','ordering2','vanilla'),
    [int]$View = 8,
    [int]$Simulation = 8,
    [int]$Seconds = 60,
    [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel = ''
)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = $Target
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$process = [Diagnostics.Process]::GetCurrentProcess()
$previousPriority = $process.PriorityClass
$previousAffinity = $process.ProcessorAffinity
try {
    $process.PriorityClass = 'BelowNormal'
    $process.ProcessorAffinity = 3840
    foreach ($pipeline in $Pipelines) {
        $label = $pipeline
        if ($Dimension -ne 'overworld') { $label += "-$Dimension" }
        if ($Seconds -ne 60) { $label += "-duration$Seconds" }
        $output = Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/$validationTarget/$label-v$View-s$Simulation"
        if ($EvidenceLabel) {
            $output = Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/$validationTarget/$EvidenceLabel/$label-v$View-s$Simulation"
        }
        & java '-XX:ActiveProcessorCount=4' (Join-Path $PSScriptRoot 'AnalyzeRecording.java') (Join-Path $output 'checkpoint.jfr') (Join-Path $output 'jfr-analysis.json')
        if ($LASTEXITCODE -ne 0) { throw 'JFR reduction failed' }
        & python (Join-Path $PSScriptRoot 'summarize_structure_isolation.py') $output
        if ($LASTEXITCODE -ne 0) { throw 'Bounded frontier summary failed' }
        if ($pipeline -eq 'b-source-audit') {
            & python (Join-Path $PSScriptRoot 'summarize_source_audit.py') $output
            if ($LASTEXITCODE -ne 0) { throw 'Native source audit reduction failed' }
        }
    }
} finally {
    $process.PriorityClass = $previousPriority
    $process.ProcessorAffinity = $previousAffinity
}
