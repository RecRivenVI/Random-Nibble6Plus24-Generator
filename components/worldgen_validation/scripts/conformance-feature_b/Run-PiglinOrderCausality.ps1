param([ValidatePattern('^[a-z0-9_]+$')][string]$EvidenceLabel='piglin_order_iterator',[switch]$CheckOnly,[switch]$ReuseCaptures,[switch]$ReuseInitialReplays,[ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputHash='',[long]$CpuAffinity=3840)
$ErrorActionPreference='Stop'
if($ExpectedInputHash -and !$CheckOnly){throw 'Historical causal input identity is read-only'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$result=Join-Path $repo 'validations/conformance-feature_b/result'
$runner=Join-Path $PSScriptRoot 'Run-IndependentB.ps1'
function Get-Directory([string]$Suffix){Join-Path $result "$($EvidenceLabel)_$Suffix/independent/26.3-fabric/nether-chests/native_0"}
function Invoke-Capture([string]$Suffix,[string]$Tape=''){
    $arguments=@{Target='26.3-fabric';FixtureId='nether-chests';EvidenceLabel="$($EvidenceLabel)_$Suffix";AttributeCaptureOnly=$true;CpuAffinity=$CpuAffinity}
    if($Tape){$arguments.AttributeReplaySource=$Tape}
    if($CheckOnly -or ($ReuseCaptures -and !$Tape) -or ($ReuseInitialReplays -and $Suffix -in @('a_with_b','b_with_a'))){$arguments.CheckOnly=$true}
    if($ExpectedInputHash){$arguments.ExpectedInputHash=$ExpectedInputHash}
    & $runner @arguments
    if($LASTEXITCODE -ne 0){throw "Native attribute diagnostic failed: $Suffix"}
}
$captures=@()
# Four predeclared independent natural captures, not a search for a closest output.
foreach($suffix in @('a','b','c','d')){
    Invoke-Capture $suffix
    $directory=Get-Directory $suffix
    $row=Get-Content (Join-Path $directory 'summary.json') -Raw|ConvertFrom-Json
    $tape=Get-Content (Join-Path $directory 'attribute-order.json') -Raw|ConvertFrom-Json
    $signature=@($tape.records|Sort-Object entity|ForEach-Object{$_.entity+':'+($_.nativeOrder -join ',')}) -join ';'
    foreach($record in $tape.records){
        if(($record.nativeOrder -join ',') -cne ($record.usedOrder -join ',') -or $record.mapClass -cne 'it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap'){throw 'Natural capture altered native container order'}
        foreach($key in $record.keysInIterationOrder){if($key.hashCode -ne $key.identityHashCode){throw 'Native registry key hash was not identity based'}}
    }
    if($captures.Count){
        foreach($field in @('fixture','inputHash','environment','trace','terrainHash','biomes','structurePlacements','intersectingPieces','entities','blockEntities','blockTicks','fluidTicks','postProcessing','pendingBlockEntities')){
            if(($row.$field|ConvertTo-Json -Depth 90 -Compress) -cne ($captures[0].row.$field|ConvertTo-Json -Depth 90 -Compress)){throw "Native prerequisite differs: $field"}
        }
        if((Get-FileHash (Join-Path $directory 'structures.json')).Hash -cne (Get-FileHash (Join-Path $captures[0].directory 'structures.json')).Hash){throw 'Native structure metadata differs'}
    }
    $captures+=@{suffix=$suffix;directory=$directory;row=$row;tape=$tape;signature=$signature}
}
$other=@($captures|Where-Object{$_.signature -cne $captures[0].signature})
if(!$other.Count){throw 'UNRESOLVED: four bounded natural captures did not witness both orders; do not grant an exception'}
$a=$captures[0];$b=$other[0]
$replays=@(@{suffix='a_with_b';reference=$b;direction='a_to_b'},@{suffix='b_with_a';reference=$a;direction='b_to_a'},
    @{suffix='a_with_b_2';reference=$b;direction='a_to_b'},@{suffix='a_with_b_3';reference=$b;direction='a_to_b'})
foreach($replay in $replays){
    $tape=Join-Path $replay.reference.directory 'attribute-order.json'
    Invoke-Capture $replay.suffix $tape
    $directory=Get-Directory $replay.suffix
    $row=Get-Content (Join-Path $directory 'summary.json') -Raw|ConvertFrom-Json
    foreach($field in @('fixture','inputHash','environment','trace','terrainHash','biomes','structurePlacements','intersectingPieces')){
        if(($row.$field|ConvertTo-Json -Depth 90 -Compress) -cne ($a.row.$field|ConvertTo-Json -Depth 90 -Compress)){throw "Replay prerequisite differs: $field"}
    }
    if($row.hash -cne $replay.reference.row.hash){throw 'Replay full UUID-only snapshot hash differs'}
    if((Get-FileHash (Join-Path $directory 'structures.json')).Hash -cne (Get-FileHash (Join-Path $a.directory 'structures.json')).Hash){throw 'Replay structure metadata differs'}
    $trace=Get-Content (Join-Path $directory 'attribute-order.json') -Raw|ConvertFrom-Json
    $replay.changedEntities=@($trace.records|Where-Object{($_.nativeOrder -join ',') -cne ($_.usedOrder -join ',')}).Count
    $replay.row=$row
}
$rows=@($captures|ForEach-Object{$_.row})+@($replays|ForEach-Object{$_.row})
if(@($rows.pid|Sort-Object -Unique).Count -ne 8 -or @($rows.save|Sort-Object -Unique).Count -ne 8){throw 'Causal proof reused a JVM/save'}
if($a.row.hash -ceq $b.row.hash){throw 'Did not observe actual serialized target divergence'}
foreach($direction in @('a_to_b','b_to_a')){
    if(!@($replays|Where-Object{$_.direction -ceq $direction -and $_.changedEntities -eq 8}).Count){throw "UNRESOLVED: no effective eight-entity intervention in direction $direction; a no-op replay is not causal proof"}
}
$summary=@{schema=2;status='PASS_CAUSAL_ONLY';scope='NATIVE_PIGLIN_ATTRIBUTE_LIST_ORDER_NOT_B_PARITY_ACCEPTANCE';inputHash=$a.row.inputHash;naturalCaptures=4;selectedNativeA=$a.suffix;selectedNativeB=$b.suffix;bidirectionalReplays=4;hashA=$a.row.hash;hashB=$b.row.hash;pids=@($rows.pid);sourceFrozen=$true;productionSortChanged=$false;acceptanceChanged=$false;captures=@($captures|ForEach-Object{@{suffix=$_.suffix;pid=$_.row.pid;save=$_.row.save;hash=$_.row.hash;signature=$_.signature}});replays=@($replays|ForEach-Object{@{suffix=$_.suffix;pid=$_.row.pid;direction=$_.direction;changedEntities=$_.changedEntities}})}
$summaryPath=Join-Path $result "$EvidenceLabel-effective-causality.json"
if(!$CheckOnly){
    if(Test-Path $summaryPath){throw 'Preserve existing causal summary'}
    [IO.File]::WriteAllText($summaryPath,($summary|ConvertTo-Json -Depth 12)+"`n",[Text.UTF8Encoding]::new($false))
}else{
    $saved=Get-Content $summaryPath -Raw|ConvertFrom-Json
    foreach($field in @('schema','status','scope','inputHash','naturalCaptures','selectedNativeA','selectedNativeB','bidirectionalReplays','hashA','hashB','sourceFrozen','productionSortChanged','acceptanceChanged')){
        if(($saved.$field|ConvertTo-Json -Compress) -cne ($summary.$field|ConvertTo-Json -Compress)){throw "Causal summary differs: $field"}
    }
}
'PASS_CAUSAL_ONLY: four native witnesses, effective interventions in BOTH directions and full UUID-only replay; original strict B failure and acceptance remain unchanged.'
