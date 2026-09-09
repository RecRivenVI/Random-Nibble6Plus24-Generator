param([Parameter(Mandatory=$true)][string[]]$Labels)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
foreach($label in $Labels){
    if($label -notmatch '^[a-z0-9-]+$'){throw 'Unsafe label'}
    $folder=Join-Path $repo "versions/26.2-fabric/build/research/performance/$label"
    $summary=Get-Content -Raw -LiteralPath (Join-Path $folder 'isolation-summary.json')|ConvertFrom-Json
    $timeline=Get-Content -Raw -LiteralPath (Join-Path $folder 'checkpoint-timeline.json')|ConvertFrom-Json
    $jfr=Get-Content -Raw -LiteralPath (Join-Path $folder 'jfr-analysis.json')|ConvertFrom-Json
    $metrics=$timeline.lazyStructureMetrics;$count=[double]$metrics.closedContexts
    $attempts=($metrics.PSObject.Properties|Where-Object {$_.Name -like 'startAttempts.*'}|Measure-Object -Property Value -Sum).Sum
    $lockMs=($jfr.monitorBlockedMsTop.PSObject.Properties|Where-Object {$_.Name -like 'net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager*'}|Measure-Object -Property Value -Sum).Sum
    $before=$timeline.aggregates.'production.candidates.checksBefore';if($null -eq $before){$before=$metrics.setConsiderations}
    $after=$timeline.aggregates.'production.candidates.checksAfter';if($null -eq $after){$after=$metrics.setConsiderations}
    $prepNs=[double]$timeline.aggregates.'production.candidates.preparationNanos'
    $row=[ordered]@{
        label=$label;seconds=$summary.windowSeconds;closedContexts=$count
        canonicalPerSecond=$summary.canonicalPerSecond;fullPerSecond=($summary.FULL/$summary.windowSeconds);sendPerSecond=($summary.sendReady/$summary.windowSeconds)
        full=$summary.FULL;sendReady=$summary.sendReady;structureCpuPercent=$summary.structureCpuPercent;strongholdCpuPercent=$summary.strongholdCpuPercent
        structureMillisPerClosedTarget=$(if($count){($metrics.structureStageNanos+$prepNs)/1e6/$count}else{0})
        candidatePreparationMillisPerTarget=$(if($count){$prepNs/1e6/$count}else{0})
        setChecksBeforePerTarget=$(if($count){$before/$count}else{0});setChecksAfterPerTarget=$(if($count){$after/$count}else{0})
        checksReductionPercent=$(if($before){100*(1-$after/$before)}else{0})
        startsPerTarget=$(if($count){$attempts/$count}else{0});validStartsPerTarget=$(if($count){$metrics.validStarts/$count}else{0})
        jigsawPerTarget=$(if($count){$metrics.jigsawExpansions/$count}else{0});validPiecesPerTarget=$(if($count){$metrics.pieces/$count}else{0})
        observedTemplateMonitorWaitMillis=$lockMs;canonicalWorkerWallMillis=$jfr.syncSpanTotalMs.CANONICAL
        templateWaitSharePercent=$(if($jfr.syncSpanTotalMs.CANONICAL){100*$lockMs/$jfr.syncSpanTotalMs.CANONICAL}else{0})
        cpuPercent=$summary.processCpuPercent;serverOneCorePercent=$summary.serverThreadOneCorePercent
        peakQueued=$summary.peakExecutorQueue;peakActive=$summary.peakActiveSessions
        maxLagMs=$summary.maxLoggedServerLagMs;coverage=$summary.coverage
        identityFileCalls=$timeline.lifecycleProfileFileCalls;spawnCarried=$timeline.aggregates.'spawn.carriedHits';spawnCold=$timeline.aggregates.'spawn.coldRecoveries'
    }
    [IO.File]::WriteAllText((Join-Path $folder 'candidate-summary.json'),($row|ConvertTo-Json -Depth 8))
    [pscustomobject]$row|Select-Object label,canonicalPerSecond,structureCpuPercent,structureMillisPerClosedTarget,checksReductionPercent,startsPerTarget,jigsawPerTarget,templateWaitSharePercent,cpuPercent|Format-List
}
