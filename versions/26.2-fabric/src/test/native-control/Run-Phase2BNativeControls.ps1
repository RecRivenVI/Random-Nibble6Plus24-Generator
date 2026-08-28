param([switch]$CleanupOnly)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$serverRunRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $serverRunRoot 'server.properties'
$resultRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2b-native-control'
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS

if ($CleanupOnly) {
    $manualWorld = [IO.Path]::GetFullPath((Join-Path $serverRunRoot 'phase2b-native-01'))
    $allowedManualPrefix = [IO.Path]::GetFullPath((Join-Path $serverRunRoot 'phase2b-native-'))
    if (-not $manualWorld.StartsWith($allowedManualPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe manual fixture cleanup path: $manualWorld"
    }
    if (Test-Path -LiteralPath $manualWorld) {
        Remove-Item -LiteralPath $manualWorld -Recurse -Force
    }
    $manualResults = [IO.Path]::GetFullPath((Join-Path $serverRunRoot 'native-results'))
    if (-not $manualResults.StartsWith($serverRunRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe manual result cleanup path: $manualResults"
    }
    if (Test-Path -LiteralPath $manualResults) {
        Remove-Item -LiteralPath $manualResults -Recurse -Force
    }
    Write-Output 'Phase 2B manual native-control fixtures cleaned'
    return
}

$fixtures = @(
    [pscustomobject]@{ Name='ow-origin-0'; Master='0'; Dimension='minecraft:overworld'; X=0; Z=0; Local='0' },
    [pscustomobject]@{ Name='ow-origin-123456789'; Master='123456789'; Dimension='minecraft:overworld'; X=0; Z=0; Local='123456789' },
    [pscustomobject]@{ Name='ow-positive'; Master='0'; Dimension='minecraft:overworld'; X=1; Z=0; Local='4728692025433535151' },
    [pscustomobject]@{ Name='ow-negative'; Master='1'; Dimension='minecraft:overworld'; X=-1; Z=1; Local='-4896871951352006295' },
    [pscustomobject]@{ Name='ow-mixed'; Master='-1'; Dimension='minecraft:overworld'; X=125; Z=-37; Local='-4101428214603732722' },
    [pscustomobject]@{ Name='ow-far'; Master='123456789'; Dimension='minecraft:overworld'; X=-20000; Z=30000; Local='-340583209543161320' },
    [pscustomobject]@{ Name='nether-origin'; Master='0'; Dimension='minecraft:the_nether'; X=0; Z=0; Local='2894682836749881434' },
    [pscustomobject]@{ Name='nether-mixed'; Master='-1'; Dimension='minecraft:the_nether'; X=1; Z=-1; Local='-4702794264821873409' },
    [pscustomobject]@{ Name='end-origin'; Master='0'; Dimension='minecraft:the_end'; X=0; Z=0; Local='8142669370447032820' },
    [pscustomobject]@{ Name='end-mixed'; Master='-1'; Dimension='minecraft:the_end'; X=1; Z=-1; Local='-5498982634037285003' }
)

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$results = [System.Collections.Generic.List[string]]::new()

try {
    Push-Location $repositoryRoot
    foreach ($fixture in $fixtures) {
        $worldName = "phase2b-native-control-$($fixture.Name)"
        $worldPath = [IO.Path]::GetFullPath((Join-Path $serverRunRoot $worldName))
        $allowedPrefix = [IO.Path]::GetFullPath((Join-Path $serverRunRoot 'phase2b-native-control-'))
        if (-not $worldPath.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing unsafe native-control cleanup path: $worldPath"
        }
        if (Test-Path -LiteralPath $worldPath) {
            Remove-Item -LiteralPath $worldPath -Recurse -Force
        }

        $properties = [regex]::Replace(
            $originalProperties,
            '(?m)^level-seed=.*$',
            "level-seed=$($fixture.Local)")
        [IO.File]::WriteAllText($serverPropertiesPath, $properties)

        $resultPath = Join-Path $resultRoot "$($fixture.Name).json"
        if (Test-Path -LiteralPath $resultPath) {
            Remove-Item -LiteralPath $resultPath -Force
        }
        $env:JAVA_TOOL_OPTIONS = @(
            "-Drandomnibble6plus24generator.phase2b.native.masterSeed=$($fixture.Master)",
            "-Drandomnibble6plus24generator.phase2b.native.dimension=$($fixture.Dimension)",
            "-Drandomnibble6plus24generator.phase2b.native.chunkX=$($fixture.X)",
            "-Drandomnibble6plus24generator.phase2b.native.chunkZ=$($fixture.Z)",
            "-Drandomnibble6plus24generator.phase2b.native.output=$($resultPath.Replace('\', '/'))"
        ) -join ' '

        & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
        if ($LASTEXITCODE -ne 0) {
            throw "Native control Gradle run failed for $($fixture.Name) with exit code $LASTEXITCODE"
        }
        if (-not (Test-Path -LiteralPath $resultPath)) {
            throw "Native control did not produce a result for $($fixture.Name)"
        }
        $result = [IO.File]::ReadAllText($resultPath)
        if ($result -notmatch '"status":"PASS"') {
            throw "Native control result was not PASS for $($fixture.Name): $result"
        }
        $results.Add($result)

        if (Test-Path -LiteralPath $worldPath) {
            Remove-Item -LiteralPath $worldPath -Recurse -Force
        }
    }
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}

[IO.File]::WriteAllLines((Join-Path $resultRoot 'summary.jsonl'), $results)
Write-Output "Phase 2B native controls PASS: $($results.Count)/$($fixtures.Count)"
