param([ValidateSet('the_nether','the_end')][string]$Dimension='the_nether', [ValidatePattern('^[a-z0-9_]*$')][string]$EvidenceLabel='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Join-Path $repo 'validations/integration-legacy_physical/result'
$leaf="minecraft-$Dimension"
$suffix=if($EvidenceLabel){'_'+$EvidenceLabel}else{''}
$pids=@(); $saves=@()
foreach($order in @('forward','reverse','parallel')){
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Run-ForgePhysical.ps1') -Dimension $Dimension -TargetOrder $order -EvidenceLabel $EvidenceLabel -CheckOnly
    if($LASTEXITCODE){throw "Native target order proof failed: $order"}
    foreach($pipeline in @('vanilla','b')){
        $folder=Join-Path $root "targets_$order$suffix/1.20.1-forge/$pipeline/$leaf"
        foreach($role in @('fresh','reload')){
            $proof=Get-Content (Join-Path $folder "$role/summary.json") -Raw|ConvertFrom-Json
            $pids+=$proof.pid
            if($role -eq 'fresh'){$saves+=$proof.save}
        }
    }
}
if(@($pids|Sort-Object -Unique).Count -ne 12 -or @($saves|Sort-Object -Unique).Count -ne 6){throw 'Target-order controls reused a JVM or save'}
$reference=Join-Path $root "targets_forward$suffix/1.20.1-forge/b/$leaf/targets"
foreach($order in @('reverse','parallel')){
    $candidate=Join-Path $root "targets_$order$suffix/1.20.1-forge/b/$leaf/targets"
    foreach($key in @('125_-37','126_-37','124_-37','125_-36','125_-38')){
        foreach($file in @('artifact.nbt','pending.nbt','instantiated.nbt','physical.nbt')){
            if((Get-FileHash (Join-Path $reference "$key/$file")).Hash -cne (Get-FileHash (Join-Path $candidate "$key/$file")).Hash){
                throw "Native B target changed with request order: $order/$key/$file"
            }
        }
    }
}
'PASS: five distinct-seed physical B owners, forward/reverse/pending-parallel, exact Artifact sidecars and physical projection; native save/reload; not ticking/player-entry acceptance.'
