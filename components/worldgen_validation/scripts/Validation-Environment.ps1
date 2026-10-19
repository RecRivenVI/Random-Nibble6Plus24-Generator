$ErrorActionPreference = 'Stop'
$validationRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$localConfiguration = Join-Path $validationRepository 'local.toml'
if (-not (Test-Path -LiteralPath $localConfiguration) -or
        -not (Select-String -LiteralPath $localConfiguration -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)) {
    throw 'Validation not run: accept the Minecraft EULA explicitly in local.toml (eula = true).'
}
Add-Type -AssemblyName Microsoft.VisualBasic
$validationCaller = (Get-PSCallStack)[1].ScriptName
$validationProject = Split-Path -Leaf (Split-Path -Parent $validationCaller)
if (Get-Variable validationProjectOverride -ErrorAction SilentlyContinue) {
    if ($validationProjectOverride -notmatch '^[a-z]+-[a-z0-9_]+$') { throw 'Invalid validation project override' }
    $validationProject = $validationProjectOverride
}
$validationTarget = if ($env:MOSAIC_VALIDATION_TARGET) { $env:MOSAIC_VALIDATION_TARGET } else { '26.2-fabric' }
if ($validationTarget -notin @('26.2-fabric', '26.3-fabric', '26.3-neoforge', '26.1.2-neoforge', '1.21.1-neoforge', '1.20.1-forge')) { throw "Unregistered validation target: $validationTarget" }
$registeredValidation = Get-Content -LiteralPath (Join-Path $validationRepository "validations/$validationProject/validation.toml") -Raw
if ($registeredValidation -notmatch ('targets\s*=\s*\[[^\]]*"' + [regex]::Escape($validationTarget) + '"')) {
    throw "Validation $validationProject does not support $validationTarget"
}
$targetFacts = ConvertFrom-StringData (Get-Content -LiteralPath (Join-Path $validationRepository "versions/$validationTarget/target.properties") -Raw)
$pack = @{ pack_format = [int]$targetFacts.data_pack_major; description = 'Hidden Mosaic validation world' }
if (!$validationTarget.StartsWith('1.')) {
    $pack.min_format = @([int]$targetFacts.data_pack_major, [int]$targetFacts.data_pack_minor)
    $pack.max_format = @([int]$targetFacts.data_pack_major, [int]$targetFacts.data_pack_minor)
}
$validationPackMetadata = @{ pack = $pack } | ConvertTo-Json -Depth 4 -Compress
$validationServer = Join-Path $validationRepository "validations/$validationProject/instance/$validationTarget/server"
New-Item -ItemType Directory -Path $validationServer -Force | Out-Null
$validationProperties = Join-Path $validationServer 'server.properties'
if (-not (Test-Path -LiteralPath $validationProperties)) {
    [IO.File]::WriteAllText($validationProperties, "level-seed=0`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n", [Text.UTF8Encoding]::new($false))
}
$validationPropertiesText = [IO.File]::ReadAllText($validationProperties)
if ($validationPropertiesText -notmatch '(?m)^level-seed=') {
    [IO.File]::WriteAllText($validationProperties, $validationPropertiesText.TrimEnd() + "`nlevel-seed=0`n", [Text.UTF8Encoding]::new($false))
}
function Move-ValidationToRecycleBin {
    param([Parameter(Mandatory)][string]$LiteralPath)
    $resolvedValidationPath = [IO.Path]::GetFullPath($LiteralPath)
    $allowedValidationRoot = [IO.Path]::GetFullPath((Join-Path $validationRepository 'validations')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedValidationPath.StartsWith($allowedValidationRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing validation cleanup outside validations: $resolvedValidationPath"
    }
    if (-not (Test-Path -LiteralPath $resolvedValidationPath)) { return }
    if ((Get-Item -LiteralPath $resolvedValidationPath).PSIsContainer) {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolvedValidationPath, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    } else {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($resolvedValidationPath, 'OnlyErrorDialogs', 'SendToRecycleBin', 'ThrowException')
    }
}
