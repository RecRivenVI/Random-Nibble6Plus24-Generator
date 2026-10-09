param([Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Unsafe label'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$output=Join-Path $repo "validations\performance-worldgen\instance\output\research\performance\$Label"
$researchProcess=[Diagnostics.Process]::GetCurrentProcess()
$previousAffinity=$researchProcess.ProcessorAffinity
$previousPriority=$researchProcess.PriorityClass
try{
    $researchProcess.ProcessorAffinity=[IntPtr]0xF00
    $researchProcess.PriorityClass=[Diagnostics.ProcessPriorityClass]::BelowNormal
    & 'C:/Program Files/BellSoft/LibericaJDK-25-Full/bin/java.exe' '-XX:ActiveProcessorCount=4' (Join-Path $PSScriptRoot 'AnalyzeRecording.java') (Join-Path $output 'checkpoint.jfr') (Join-Path $output 'jfr-analysis.json')
    if($LASTEXITCODE -ne 0){throw 'JFR analysis failed'}
    & python (Join-Path $PSScriptRoot 'summarize_structure_isolation.py') $output
    if($LASTEXITCODE -ne 0){throw 'Window validation failed'}
}finally{
    $researchProcess.ProcessorAffinity=$previousAffinity
    $researchProcess.PriorityClass=$previousPriority
}
