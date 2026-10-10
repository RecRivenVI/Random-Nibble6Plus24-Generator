$ErrorActionPreference = 'Stop'
$files = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File -Recurse
foreach ($file in $files) {
    $parseErrors = $null
    $tokens = $null
    [Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
    if ($parseErrors.Count) { throw "$($file.FullName): $($parseErrors | Out-String)" }
}
"PASS: $($files.Count) PowerShell scripts parsed."
