param([int]$View = 8, [int]$Simulation = 8)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$process = [Diagnostics.Process]::GetCurrentProcess()
$previousPriority = $process.PriorityClass
$previousAffinity = $process.ProcessorAffinity
try {
    $process.PriorityClass = 'BelowNormal'
    $process.ProcessorAffinity = 3840
    foreach ($pipeline in @('b','ordering2','vanilla')) {
        $output = Join-Path $validationRepository "validations/performance-worldgen/result/b_milestone/26.3-fabric/$pipeline-v$View-s$Simulation"
        & java '-XX:ActiveProcessorCount=4' (Join-Path $PSScriptRoot 'AnalyzeRecording.java') (Join-Path $output 'checkpoint.jfr') (Join-Path $output 'jfr-analysis.json')
        if ($LASTEXITCODE -ne 0) { throw 'JFR reduction failed' }
        & python (Join-Path $PSScriptRoot 'summarize_structure_isolation.py') $output
        if ($LASTEXITCODE -ne 0) { throw 'Bounded frontier summary failed' }
    }
} finally {
    $process.PriorityClass = $previousPriority
    $process.ProcessorAffinity = $previousAffinity
}
