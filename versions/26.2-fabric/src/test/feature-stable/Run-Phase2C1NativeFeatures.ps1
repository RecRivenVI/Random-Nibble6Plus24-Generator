param(
    [switch]$Smoke,
    [int]$Repeats = 4,
    [string]$ReferenceSnapshot = '',
    [string]$FixtureName = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runRoot = (Resolve-Path (Join-Path $repositoryRoot 'runs\26.2-fabric\server')).Path
$serverPropertiesPath = Join-Path $runRoot 'server.properties'
$resultRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase2c1-native'
$originalProperties = [IO.File]::ReadAllText($serverPropertiesPath)
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS

$fixtures = @(
    [pscustomobject]@{ Name='ow-origin'; Master='123456789'; Local='123456789'; Dimension='minecraft:overworld'; X=0; Z=0 },
    [pscustomobject]@{ Name='ow-a'; Master='123456789'; Local='-5161763991829980711'; Dimension='minecraft:overworld'; X=125; Z=-37 },
    [pscustomobject]@{ Name='ow-b'; Master='0'; Local='4728692025433535151'; Dimension='minecraft:overworld'; X=1; Z=0 },
    [pscustomobject]@{ Name='nether-a'; Master='-1'; Local='-4702794264821873409'; Dimension='minecraft:the_nether'; X=-1; Z=1 },
    [pscustomobject]@{ Name='end-a'; Master='-987654321'; Local='-7286762380808216340'; Dimension='minecraft:the_end'; X=-125; Z=37 }
)
if (-not [string]::IsNullOrWhiteSpace($FixtureName)) {
    $fixtures = @($fixtures | Where-Object Name -eq $FixtureName)
    if ($fixtures.Count -ne 1) { throw "Unknown or duplicate fixture name: $FixtureName" }
}
if ($Smoke) {
    $fixtures = @($fixtures[0])
    $Repeats = 1
}

function Remove-FixtureWorld([string]$worldName) {
    $worldPath = [IO.Path]::GetFullPath((Join-Path $runRoot $worldName))
    $prefix = $runRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $worldPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe native FEATURES cleanup path: $worldPath"
    }
    if ((Split-Path -Leaf $worldPath) -ne $worldName) {
        throw "Refusing mismatched native FEATURES cleanup path: $worldPath"
    }
    if (Test-Path -LiteralPath $worldPath) {
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

if (Test-Path -LiteralPath $resultRoot) {
    Remove-Item -LiteralPath $resultRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$results = [Collections.Generic.List[object]]::new()

try {
    Push-Location $repositoryRoot
    foreach ($fixture in $fixtures) {
        $expectedHash = $null
        for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
            $worldName = "phase2c1-native-$($fixture.Name)-$repeat"
            Remove-FixtureWorld $worldName
            try {
                $properties = [regex]::Replace(
                    $originalProperties,
                    '(?m)^level-seed=.*$',
                    "level-seed=$($fixture.Local)")
                [IO.File]::WriteAllText($serverPropertiesPath, $properties)
                $resultPath = Join-Path $resultRoot "$($fixture.Name)-$repeat.json"
                $evidencePath = Join-Path $resultRoot "$($fixture.Name)-$repeat-evidence"
                $env:JAVA_TOOL_OPTIONS = @(
                    "-Drandomnibble6plus24generator.phase2c1.native.masterSeed=$($fixture.Master)",
                    "-Drandomnibble6plus24generator.phase2c1.native.dimension=$($fixture.Dimension)",
                    "-Drandomnibble6plus24generator.phase2c1.native.chunkX=$($fixture.X)",
                    "-Drandomnibble6plus24generator.phase2c1.native.chunkZ=$($fixture.Z)",
                    "-Drandomnibble6plus24generator.phase2c1.native.output=$($resultPath.Replace('\', '/'))",
                    "-Drandomnibble6plus24generator.phase2c1.native.evidenceRoot=$($evidencePath.Replace('\', '/'))"
                ) -join ' '
                if ($fixture.Dimension -eq 'minecraft:overworld') {
                    $env:JAVA_TOOL_OPTIONS += ' -Drandomnibble6plus24generator.phase2c1.native.runBeforeInitialSpawn=true'
                }
                if (-not [string]::IsNullOrWhiteSpace($ReferenceSnapshot)) {
                    $referencePath = [IO.Path]::GetFullPath($ReferenceSnapshot).Replace('\', '/')
                    $env:JAVA_TOOL_OPTIONS += " -Drandomnibble6plus24generator.phase2c1.native.referenceSnapshot=$referencePath"
                }

                & (Join-Path $repositoryRoot 'gradlew.bat') ':versions:26.2-fabric:runServer' "--args=nogui --world $worldName"
                if ($LASTEXITCODE -ne 0) {
                    throw "Native FEATURES Gradle run failed for $($fixture.Name)/${repeat}: $LASTEXITCODE"
                }
                $result = [IO.File]::ReadAllText($resultPath) | ConvertFrom-Json
                if ($result.status -ne 'PASS') {
                    throw "Native FEATURES result was not PASS: $resultPath"
                }
                if ($null -eq $expectedHash) {
                    $expectedHash = $result.featureHash
                } elseif ($expectedHash -ne $result.featureHash) {
                    throw "Fresh-world repeatability mismatch for $($fixture.Name): $expectedHash != $($result.featureHash)"
                }
                $results.Add($result)
            } finally {
                Remove-FixtureWorld $worldName
            }
        }
    }
} finally {
    [IO.File]::WriteAllText($serverPropertiesPath, $originalProperties)
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}

$summary = [pscustomobject]@{
    status = 'PASS'
    freshWorlds = $results.Count
    uniqueFixtures = $fixtures.Count
    repeatsPerFixture = $Repeats
    dimensions = @($results.dimension | Sort-Object -Unique)
    hashes = @($results | Group-Object dimension, chunkX, chunkZ | ForEach-Object { $_.Group[0].featureHash })
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultRoot 'summary.json') -Encoding utf8
$summary | ConvertTo-Json -Depth 5
