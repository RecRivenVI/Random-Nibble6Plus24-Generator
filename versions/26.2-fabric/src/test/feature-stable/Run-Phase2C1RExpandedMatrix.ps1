param([long]$HostSeed = 987654321)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$runner = Join-Path $PSScriptRoot 'Run-Phase2C1RootCause.ps1'

$fixtures = @(
    [pscustomobject]@{ Id='ow-origin-0'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=0; Z=0; Direct=$false; Frontier='generated' },
    [pscustomobject]@{ Id='ow-origin-1'; Master='1'; Local='1'; Dim='minecraft:overworld'; X=0; Z=0; Direct=$false; Frontier='generated' },
    [pscustomobject]@{ Id='ow-origin-neg1'; Master='-1'; Local='-1'; Dim='minecraft:overworld'; X=0; Z=0; Direct=$false; Frontier='generated' },
    [pscustomobject]@{ Id='ow-origin-123'; Master='123456789'; Local='123456789'; Dim='minecraft:overworld'; X=0; Z=0; Direct=$false; Frontier='generated' },
    [pscustomobject]@{ Id='ow-nonorigin-0'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1024; Z=-1024; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-1'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1088; Z=-1024; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-2'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1152; Z=-1024; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-3'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1216; Z=-1024; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-4'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1024; Z=-960; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-5'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1088; Z=-960; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-6'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1152; Z=-960; Direct=$true },
    [pscustomobject]@{ Id='ow-nonorigin-7'; Master='0'; Local='0'; Dim='minecraft:overworld'; X=1216; Z=-960; Direct=$true },
    [pscustomobject]@{ Id='nether-0'; Master='0'; Local='0'; Dim='minecraft:the_nether'; X=2048; Z=-2048; Direct=$true },
    [pscustomobject]@{ Id='nether-1'; Master='0'; Local='0'; Dim='minecraft:the_nether'; X=2112; Z=-2048; Direct=$true },
    [pscustomobject]@{ Id='nether-2'; Master='0'; Local='0'; Dim='minecraft:the_nether'; X=2048; Z=-1984; Direct=$true },
    [pscustomobject]@{ Id='nether-3'; Master='0'; Local='0'; Dim='minecraft:the_nether'; X=2112; Z=-1984; Direct=$true },
    [pscustomobject]@{ Id='end-0'; Master='0'; Local='0'; Dim='minecraft:the_end'; X=3072; Z=-3072; Direct=$true },
    [pscustomobject]@{ Id='end-1'; Master='0'; Local='0'; Dim='minecraft:the_end'; X=3136; Z=-3072; Direct=$true },
    [pscustomobject]@{ Id='end-2'; Master='0'; Local='0'; Dim='minecraft:the_end'; X=3072; Z=-3008; Direct=$true },
    [pscustomobject]@{ Id='end-3'; Master='0'; Local='0'; Dim='minecraft:the_end'; X=3136; Z=-3008; Direct=$true }
)

$passed = 0
foreach ($fixture in $fixtures) {
    $runId = "v2-$($fixture.Id)"
    $frontierState = if ($fixture.PSObject.Properties.Name -contains 'Frontier') { $fixture.Frontier } else { 'empty' }
    $resultPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\build')).Path `
        "phase2c1r-$runId\isolated-host-$HostSeed-$frontierState-result.json"
    if (Test-Path -LiteralPath $resultPath) {
        $existing = [IO.File]::ReadAllText($resultPath) | ConvertFrom-Json
        if ($existing.status -eq 'MATCH') {
            $passed++
            Write-Host "PHASE2C1R_EXPANDED_REUSE $passed/$($fixtures.Count) $($fixture.Id) $($existing.nativeHash)"
            continue
        }
    }
    $parameters = @{
        HostSeed = $HostSeed
        FrontierState = $frontierState
        MasterSeed = $fixture.Master
        LocalSeed = $fixture.Local
        Dimension = $fixture.Dim
        ChunkX = $fixture.X
        ChunkZ = $fixture.Z
        FixtureId = $runId
    }
    if ($fixture.Direct) { $parameters.DirectLocalSeed = $true }
    & $runner @parameters
    if ($LASTEXITCODE -ne 0) { throw "Expanded fixture process failed: $($fixture.Id)" }
    $result = [IO.File]::ReadAllText($resultPath) | ConvertFrom-Json
    if ($result.status -ne 'MATCH') { throw "Expanded fixture diverged: $($fixture.Id): $result" }
    $passed++
    Write-Host "PHASE2C1R_EXPANDED_PASS $passed/$($fixtures.Count) $($fixture.Id) $($result.nativeHash)"
}

[pscustomobject]@{
    status='PASS'
    fixtures=$passed
    overworldOrigin=4
    overworldNonOrigin=8
    nether=4
    end=4
    hostSeed=$HostSeed
} | ConvertTo-Json
