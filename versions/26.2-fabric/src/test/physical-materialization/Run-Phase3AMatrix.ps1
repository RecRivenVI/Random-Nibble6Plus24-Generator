param([switch]$KeepWorlds)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$runner = Join-Path $PSScriptRoot 'Run-Phase3APhysicalMaterialization.ps1'
$outputRoot = Join-Path $repositoryRoot 'versions\26.2-fabric\build\phase3a-matrix'
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$fixtures = @(
    @{Id='origin-golden';M='123456789';D='minecraft:overworld';X=0;Z=0;H='5c246f0529e1ae1388cdaf11d42a5a17cf4cb5fbf65f862db62b2dc9bd29edae'},
    @{Id='non-origin-golden';M='123456789';D='minecraft:overworld';X=125;Z=-37;H='44cff6725fa04145cd8e0917f9bbecc578fe5479b2590db869b90c163d7edbf5'},
    @{Id='nether';M='-1';D='minecraft:the_nether';X=10000;Z=-20000;H='efacf7d37c3efb4de3020b86460275fe8fb6dabe56d323f87e7e5c4fe82c5fed'},
    @{Id='end';M='0';D='minecraft:the_end';X=10000;Z=-20000;H='269bb861ffeee29f5195c54f02f089bdcf3e3c81d8a2cd0fa289c16f0db740d9'},
    @{Id='end-spike';M='0';D='minecraft:the_end';X=-1;Z=-3;H='e49202a8e64670e2a166797a390b31e6ac252a7c4b0ed83fc8a59fbac4a11707'},
    @{Id='mineshaft-entity';M='123456789';D='minecraft:overworld';X=-20000;Z=30000;H='a31e4d04c9952a9427eaae184c687ee2e7150ce3583ca67f40ad234aab6e122c'},
    @{Id='pale-moss';M='123456789';D='minecraft:overworld';X=-8532;Z=-4457;H='398792dc0f78d263c39afe218d4ba984ed9238d82e4f0d3aebeb757076848ddd'},
    @{Id='capped-processor';M='1';D='minecraft:overworld';X=3699;Z=-6116;H='22510b5dc44ab8bef29d21faea353041d2e96096c0af6396da06a07cc4346fb9'},
    @{Id='pending-be';M='-9223372036854775808';D='minecraft:overworld';X=0;Z=0;H='cfdc7245c363a8fb5508370b62d265d313e92ff8860dcf310b09e511ea13bc38'},
    @{Id='beehive';M='9223372036854775807';D='minecraft:overworld';X=0;Z=0;H='c07d3ec1c46db370891d12145b321ba3feb1a8bc8c6e5cc1e390ee02d255ddc6'},
    @{Id='dungeon-be';M='-9223372036854775808';D='minecraft:overworld';X=-1;Z=1;H='65ffefc3d239b6cdc41ee5b6e4d4747ea910b26eab95d8ccfce5edbee6b0a5ee'},
    @{Id='post-heavy';M='0';D='minecraft:overworld';X=125;Z=-37;H='8055c503ee9a4a35e1ffb871e9b45f54980a5bf5d3ad8e0cc02f9c26f9d3a327'},
    @{Id='fluid-ticks';M='9223372036854775807';D='minecraft:the_nether';X=-125;Z=37;H='7876b5777f0b3481d63dbbb9d41d294fb8859a06047e155a7fbeb9f3ffb78a04'},
    @{Id='lush-heavy';M='9223372036854775807';D='minecraft:overworld';X=125;Z=-37;H='5c83e93c29410f80a6b5e387e4cde8579c3a9810820955635074b37f6031fa0d'},
    @{Id='vault';M='-987654321';D='minecraft:overworld';X=1;Z=0;H='5bf5d7948702d276164eba814b5dfc3e35e80e1f43a7ec0fa6034dcec94d55c5'},
    @{Id='end-chorus';M='9223372036854775807';D='minecraft:the_end';X=10000;Z=-20000;H='9adb5e659a617efab6aeb3d2919b177d5dbd5055f7003589d98527a0ff128041'}
)

$results = @()
$index = 0
foreach ($fixture in $fixtures) {
    $index++
    $output = Join-Path $outputRoot "$($fixture.Id).json"
    & $runner -MasterSeed ([long]$fixture.M) -Dimension $fixture.D -ChunkX $fixture.X -ChunkZ $fixture.Z `
        -ExpectedHash $fixture.H -ResultPath $output -KeepWorld:$KeepWorlds
    if ($LASTEXITCODE -ne 0) { throw "Phase 3A fixture failed: $($fixture.Id)" }
    $result = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS' -or $result.semanticHash -ne $fixture.H) {
        throw "Phase 3A fixture mismatch: $($fixture.Id)"
    }
    $results += $result
    Write-Host "PHASE3A_MATRIX_PASS $index/16 $($fixture.Id) $($result.semanticHash)"
}

$summary = [pscustomobject]@{
    status = 'PASS'
    fixtures = $results.Count
    origin = @($results | Where-Object { $_.dimension -eq 'minecraft:overworld' -and $_.chunkX -eq 0 -and $_.chunkZ -eq 0 }).Count
    overworld = @($results | Where-Object { $_.dimension -eq 'minecraft:overworld' }).Count
    nether = @($results | Where-Object { $_.dimension -eq 'minecraft:the_nether' }).Count
    end = @($results | Where-Object { $_.dimension -eq 'minecraft:the_end' }).Count
    results = $results
}
$summaryPath = Join-Path $outputRoot 'summary.json'
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
$summary | ConvertTo-Json -Depth 8
