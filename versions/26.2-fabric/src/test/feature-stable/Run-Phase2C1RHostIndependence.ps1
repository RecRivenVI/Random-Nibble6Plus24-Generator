param(
    [long[]]$HostSeeds = @(987654321, 42, -42),
    [switch]$ReuseExistingNative
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($HostSeeds.Count -lt 3) {
    throw 'Phase 2C1R host-independence verification requires at least three host seeds.'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runner = Join-Path $PSScriptRoot 'Run-Phase2C1RootCause.ps1'
$fixtures = @(
    @{ Id = 'v2-ow-origin-0'; Seed = '0' },
    @{ Id = 'v2-ow-origin-1'; Seed = '1' },
    @{ Id = 'v2-ow-origin-neg1'; Seed = '-1' },
    @{ Id = 'v2-ow-origin-123'; Seed = '123456789' }
)

$results = @()
foreach ($fixture in $fixtures) {
    $firstHost = $true
    foreach ($hostSeed in $HostSeeds) {
        $resultPath = Join-Path $repositoryRoot "versions\26.2-fabric\build\phase2c1r-$($fixture.Id)\isolated-host-$hostSeed-generated-result.json"
        if (Test-Path -LiteralPath $resultPath) {
            $existing = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
            if ($existing.status -eq 'MATCH') {
                $results += [PSCustomObject]@{
                    localSeed = $fixture.Seed
                    hostSeed = $hostSeed
                    featureHash = $existing.isolatedHash
                    firstCheckpointDivergence = $existing.firstCheckpointDivergence
                }
                $firstHost = $false
                continue
            }
        }

        $arguments = @{
            HostSeed = $hostSeed
            FrontierState = 'generated'
            MasterSeed = $fixture.Seed
            LocalSeed = $fixture.Seed
            Dimension = 'minecraft:overworld'
            ChunkX = 0
            ChunkZ = 0
            FixtureId = $fixture.Id
            DirectLocalSeed = $true
        }
        if ($ReuseExistingNative -or -not $firstHost) {
            $arguments.ReuseNative = $true
        }

        & $runner @arguments

        $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if ($result.status -ne 'MATCH') {
            throw "Host-independence mismatch for localSeed=$($fixture.Seed), hostSeed=$hostSeed"
        }
        $results += [PSCustomObject]@{
            localSeed = $fixture.Seed
            hostSeed = $hostSeed
            featureHash = $result.isolatedHash
            firstCheckpointDivergence = $result.firstCheckpointDivergence
        }
        $firstHost = $false
    }

    $hashes = @($results | Where-Object localSeed -eq $fixture.Seed | Select-Object -ExpandProperty featureHash -Unique)
    if ($hashes.Count -ne 1) {
        throw "Host seed changed the stable hash for localSeed=$($fixture.Seed): $($hashes -join ', ')"
    }
}

$summary = [PSCustomObject]@{
    status = 'PASS'
    localSeeds = $fixtures.Count
    hostSeedsPerLocal = $HostSeeds.Count
    comparisons = $results.Count
    hostSeeds = $HostSeeds
    results = $results
}
$summary | ConvertTo-Json -Depth 5
