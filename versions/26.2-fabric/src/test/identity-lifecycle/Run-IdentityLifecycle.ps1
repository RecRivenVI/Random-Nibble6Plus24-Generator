param([switch]$MosaicOnly)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$run = Join-Path $repository 'runs\26.2-fabric\identity-lifecycle'
$stamp = [guid]::NewGuid().ToString('N').Substring(0, 12)
$results = Join-Path $repository "versions\26.2-fabric\build\identity-lifecycle\$stamp"
New-Item -ItemType Directory -Path $run, $results -Force | Out-Null
$acceptedEula = Join-Path $repository 'runs\26.2-fabric\server\eula.txt'
if (-not (Test-Path -LiteralPath $acceptedEula) -or -not (Select-String -LiteralPath $acceptedEula -Pattern '^eula=true$' -Quiet)) {
    throw 'This runtime test requires an existing accepted development-server eula.txt.'
}
Copy-Item -LiteralPath $acceptedEula -Destination (Join-Path $run 'eula.txt')
$previousOptions = $env:JAVA_TOOL_OPTIONS
$variants = if ($MosaicOnly) { @('mosaic') } else { @('vanilla', 'mosaic') }
try {
    Push-Location $repository
    foreach ($variant in $variants) {
        $world = "identity-$variant-$stamp"
        $levelType = 'minecraft:normal'
        if ($variant -eq 'mosaic') {
            $levelType = 'randomnibble6plus24generator:phase3c2_hidden_mosaic'
            $pack = Join-Path $run "$world\datapacks\identity-test"
            $preset = Join-Path $pack 'data\randomnibble6plus24generator\worldgen\world_preset'
            New-Item -ItemType Directory -Path $preset -Force | Out-Null
            [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'),
                '{"pack":{"pack_format":107,"min_format":[107,1],"max_format":[107,1],"description":"Identity lifecycle fixture"}}')
            $profile = @{format_version=2;seed_derivation_algorithm_version=1;feature_ordering_algorithm_version=1;presentation_algorithm_version=1;primary_dimension='minecraft:overworld'}
            $dimensions = [ordered]@{}
            foreach ($dimension in @('overworld', 'the_nether', 'the_end')) {
                $settings = switch ($dimension) { 'the_nether' {'nether'} 'the_end' {'end'} default {'overworld'} }
                $biomes = if ($dimension -eq 'the_end') { @{type='minecraft:the_end'} } else { @{type='minecraft:multi_noise';preset="minecraft:$settings"} }
                $dimensions["minecraft:$dimension"] = @{type="minecraft:$dimension";generator=@{type='randomnibble6plus24generator:mosaic';biome_source=$biomes;settings="minecraft:$settings";mosaic_profile=$profile}}
            }
            [IO.File]::WriteAllText((Join-Path $preset 'phase3c2_hidden_mosaic.json'), (@{dimensions=$dimensions}|ConvertTo-Json -Depth 12))
        }
        [IO.File]::WriteAllText((Join-Path $run 'server.properties'), @"
level-name=$world
level-seed=123456789
level-type=$levelType
server-ip=127.0.0.1
server-port=25587
online-mode=true
view-distance=2
simulation-distance=2
"@)
        foreach ($pass in @('create', 'restart')) {
            $output = Join-Path $results "$variant-$pass.json"
            $env:JAVA_TOOL_OPTIONS = "-Dmosaic.identity.test.mosaic=$($variant -eq 'mosaic') -Dmosaic.identity.test.output=$($output.Replace('\','/'))"
            & (Join-Path $repository 'gradlew.bat') ':versions:26.2-fabric:runServer' '-PidentityLifecycleTest' "--args=nogui --world $world" *> (Join-Path $results "$variant-$pass.log")
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output)) { throw "Identity runtime test failed: $variant/$pass; see $results" }
            $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
            if ($result.status -ne 'PASS' -or $result.hotFilesystemProbes -ne 0) { throw "Identity runtime regression: $result" }
            $result | ConvertTo-Json -Compress
        }
    }
    Write-Output "Evidence: $results"
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    Pop-Location
}
