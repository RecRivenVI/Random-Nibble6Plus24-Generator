param([switch]$Smoke, [switch]$CleanupOnly)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$serverRunRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $serverRunRoot 'server.properties'
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$outputRoot = if ($Smoke) {
    Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c0-feature-smoke'
} else {
    Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c0-feature-order'
}
$allowedWorldPrefix = [IO.Path]::GetFullPath((Join-Path $serverRunRoot 'phase2c0-feature-'))

function Remove-ProbeWorld([string]$WorldName) {
    $worldPath = [IO.Path]::GetFullPath((Join-Path $serverRunRoot $WorldName))
    if (-not $worldPath.StartsWith($allowedWorldPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe FEATURES probe cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

if ($CleanupOnly) {
    Get-ChildItem -LiteralPath $serverRunRoot -Directory -Filter 'phase2c0-feature-*' | ForEach-Object {
        Remove-ProbeWorld $_.Name
    }
    Write-Output 'Phase 2C0 temporary feature-order worlds cleaned'
    return
}

$dimensions = if ($Smoke) {
    @([pscustomobject]@{ Name='overworld'; Id='minecraft:overworld' })
} else {
    @(
        [pscustomobject]@{ Name='overworld'; Id='minecraft:overworld' },
        [pscustomobject]@{ Name='the_nether'; Id='minecraft:the_nether' },
        [pscustomobject]@{ Name='the_end'; Id='minecraft:the_end' }
    )
}
$orders = if ($Smoke) {
    @('row_major')
} else {
    @('center_first', 'center_last', 'row_major', 'reverse', 'shuffle_1', 'shuffle_2')
}

if (Test-Path -LiteralPath $outputRoot) {
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$completedWorlds = 0

try {
    Push-Location $repositoryRoot
    foreach ($dimension in $dimensions) {
        foreach ($order in $orders) {
            $worldName = "phase2c0-feature-$($dimension.Name)-$order"
            Remove-ProbeWorld $worldName
            try {
                $properties = [regex]::Replace($originalProperties, '(?m)^level-seed=.*$', 'level-seed=0')
                [IO.File]::WriteAllText($serverPropertiesPath, $properties)
                $compare = -not $Smoke -and $order -eq 'shuffle_2'
                $env:JAVA_TOOL_OPTIONS = @(
                    '-Drandomnibble6plus24generator.phase2c0.features.seed=0',
                    "-Drandomnibble6plus24generator.phase2c0.features.dimension=$($dimension.Id)",
                    "-Drandomnibble6plus24generator.phase2c0.features.order=$order",
                    "-Drandomnibble6plus24generator.phase2c0.features.outputRoot=$($outputRoot.Replace('\', '/'))",
                    "-Drandomnibble6plus24generator.phase2c0.features.compare=$($compare.ToString().ToLowerInvariant())"
                ) -join ' '

                & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
                if ($LASTEXITCODE -ne 0) {
                    throw "FEATURES order Gradle run failed for $($dimension.Name)/$order with exit code $LASTEXITCODE"
                }
                $runResult = Join-Path $outputRoot "$($dimension.Name)\$order\run.txt"
                if (-not (Test-Path -LiteralPath $runResult)) {
                    throw "FEATURES order probe produced no run metadata for $($dimension.Name)/$order"
                }
                $completedWorlds++
            } finally {
                Remove-ProbeWorld $worldName
            }
        }
    }
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}

if (-not $Smoke) {
    foreach ($dimension in $dimensions) {
        $summary = Join-Path $outputRoot "$($dimension.Name)\summary.txt"
        if (-not (Test-Path -LiteralPath $summary)) {
            throw "Missing FEATURES comparison summary for $($dimension.Name)"
        }
    }
}

Write-Output "Phase 2C0 fresh-world feature orders completed: $completedWorlds"
