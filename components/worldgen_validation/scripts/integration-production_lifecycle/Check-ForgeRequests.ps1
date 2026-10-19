param([ValidateSet('the_nether','the_end')][string]$Dimension='the_nether', [ValidateSet(8,16,32)][int]$Requests=32)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Join-Path $repo 'validations/integration-legacy_physical/result'
$leaf="minecraft-$Dimension"
$identities=@()
foreach($count in @(1,$Requests)){
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Run-ForgePhysical.ps1') -Dimension $Dimension -RequestProof -Requests $count -CheckOnly
    if($LASTEXITCODE){throw 'Native request proof failed its current-source check'}
    foreach($pipeline in @('vanilla','b')){
        foreach($role in @('fresh','reload')){
            $proof=Get-Content (Join-Path $root "requests_$count/1.20.1-forge/$pipeline/$leaf/$role/summary.json") -Raw|ConvertFrom-Json
            $identities+=$proof.pid
        }
    }
}
if(@($identities|Sort-Object -Unique).Count -ne 8){throw 'Single/burst controls reused a JVM'}
foreach($pipeline in @('vanilla','b')){
    $single=Join-Path $root "requests_1/1.20.1-forge/$pipeline/$leaf"
    $burst=Join-Path $root "requests_$Requests/1.20.1-forge/$pipeline/$leaf"
    foreach($role in @('fresh','reload')){
        $a=Get-Content (Join-Path $single "$role/summary.json") -Raw|ConvertFrom-Json
        $b=Get-Content (Join-Path $burst "$role/summary.json") -Raw|ConvertFrom-Json
        if($a.save -ceq $b.save){throw 'Single/burst controls reused a save'}
        foreach($metric in @('generations','publications','poi','light','spawn','full','collisionReads','coldCollisionSources')){
            if($a.beforeSave.$metric -ne $b.beforeSave.$metric){throw "Burst repeated/changed lifecycle work: $pipeline/$role/$metric"}
        }
        if($a.rootGenerations -ne $b.rootGenerations -or $a.localSeed -ne $b.localSeed){throw 'Burst changed target generation authority'}
    }
    $files=if($pipeline -eq 'b'){@('structure.nbt','biome-frontier.nbt')}else{@('structure.nbt')}
    foreach($file in $files){
        if((Get-FileHash (Join-Path $single $file)).Hash -cne (Get-FileHash (Join-Path $burst $file)).Hash){throw "Burst changed physical input $pipeline/$file"}
    }
    if($pipeline -eq 'b' -and (Get-FileHash (Join-Path $single 'fresh/root-artifact.nbt')).Hash -cne (Get-FileHash (Join-Path $burst 'fresh/root-artifact.nbt')).Hash){throw 'Burst changed the detached target Artifact payload'}
}
"PASS: native single/$Requests pending requests, identical Artifact payload/biomes/structures/lifecycle counts; not parallel-target or request-order acceptance."
