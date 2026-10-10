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
$validationServer = Join-Path $validationRepository "validations/$validationProject/instance/26.2-fabric/server"
New-Item -ItemType Directory -Path $validationServer -Force | Out-Null
$validationProperties = Join-Path $validationServer 'server.properties'
if (-not (Test-Path -LiteralPath $validationProperties)) {
    [IO.File]::WriteAllText($validationProperties, "online-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n", [Text.UTF8Encoding]::new($false))
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
