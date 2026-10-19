param([ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel = 'guarded', [int]$Repeats = 32)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$target = '1.21.1-neoforge'
$root = Join-Path $repo "validations/integration-production_lifecycle/result/current/$target/phase3c2-production/features-2/development-b/physical-handoff"
$sources = @(Get-ChildItem (Join-Path $repo "versions/$target/src"), (Join-Path $repo 'components/worldgen_contract/src') -Recurse -File | Sort-Object FullName)
$fingerprint = ($sources | ForEach-Object { $_.FullName.Substring($repo.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
$fingerprint += [IO.File]::ReadAllText((Join-Path $repo "versions/$target/target.properties"))
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint)))
$pids = [Collections.Generic.HashSet[long]]::new()
foreach ($dimension in @('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')) {
    $leaf = Join-Path $root "$($dimension.Replace(':','-'))/lazy-rings/$EvidenceLabel"
    $first = Get-Content -LiteralPath (Join-Path $leaf 'full-125--37.json') -Raw | ConvertFrom-Json
    $many = Get-Content -LiteralPath (Join-Path $leaf 'concurrent-125--37.json') -Raw | ConvertFrom-Json
    $oneConditions = Get-Content -LiteralPath (Join-Path $leaf 'conditions-full-125--37.json') -Raw | ConvertFrom-Json
    $manyConditions = Get-Content -LiteralPath (Join-Path $leaf 'conditions-concurrent-125--37.json') -Raw | ConvertFrom-Json
    foreach ($conditions in @($oneConditions, $manyConditions)) {
        if ($conditions.inputHash -cne $hash -or $conditions.target -cne $target -or $conditions.dimension -cne $dimension -or
                $conditions.x -ne 125 -or $conditions.z -ne -37 -or $conditions.masterSeed -ne 123456789 -or
                $conditions.evidenceLabel -cne $EvidenceLabel -or !$conditions.developmentB -or !$conditions.lazyRings -or
                $conditions.featureOrdering -ne 2 -or $conditions.cpu -ne 4) { throw "Wrong concurrency conditions: $dimension" }
    }
    if ($oneConditions.mode -cne 'full' -or $manyConditions.mode -cne 'concurrent' -or
            $oneConditions.affinity -ne $manyConditions.affinity -or $oneConditions.runIdentity -ceq $manyConditions.runIdentity) {
        throw "Not independent same-resource FULL/concurrent runs: $dimension"
    }
    foreach ($row in @($first, $many)) {
        if ($row.status -cne 'PASS' -or $row.normalVanilla -or $row.dimension -cne $dimension -or
                $row.target -cne '[125, -37]' -or $row.worldSeed -ne 123456789 -or $row.featureOrderingVersion -ne 2 -or
                $row.fullChunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk' -or $row.fullStatus -cne 'minecraft:full' -or
                !$row.lightCorrect -or $row.physicalMetrics.inFlight -ne 0 -or !$pids.Add([long]$row.pid)) {
            throw "Incomplete/duplicate physical FULL evidence: $dimension"
        }
    }
    if ($first.mode -cne 'full' -or $first.ordinaryRequests -ne 1 -or $many.mode -cne 'concurrent' -or
            $many.ordinaryRequests -ne $Repeats -or !$many.concurrentSamePhysicalChunk -or
            $first.localWorldSeed -ne $many.localWorldSeed -or $first.physicalBiomeChecks -ne $many.physicalBiomeChecks) {
        throw "Concurrent requests did not converge on the same physical result: $dimension"
    }
    foreach ($field in @('artifacts','publications','biomePreparations','biomePublications','poi','light','spawn','full')) {
        if ($first.physicalMetrics.$field -ne $many.physicalMetrics.$field) { throw "Repeated $field work: $dimension" }
    }
    if ($many.artifactGenerations -ne $first.artifactGenerations -or $many.spawnCalls -ne $first.spawnCalls) {
        throw "Concurrent canonical/SPAWN work amplified: $dimension"
    }
}
"PASS: $target three dimensions, $Repeats requests share one physical result; canonical/lifecycle counts equal fresh single request."
