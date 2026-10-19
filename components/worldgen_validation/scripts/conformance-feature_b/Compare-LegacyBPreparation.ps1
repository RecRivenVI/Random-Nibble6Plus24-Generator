param([switch]$CheckOnly, [switch]$Offline)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$validation=Join-Path $repo 'validations/conformance-legacy_b'
$output=Join-Path $validation 'result/preparation_comparison/1.21.1-neoforge'
$runner=Join-Path $PSScriptRoot 'Run-LegacyB.ps1'

function Check-Pairs {
    $pairs=@()
    $fingerprint=$null
    foreach($round in 0..2) {
        $times=@{}
        foreach($mode in @('independent','shared')) {
            $root=Join-Path $output "pair-$round/$mode"
            $rows=@()
            foreach($role in @('native_0','native_1','host_17','host_23')) {
                $body=Join-Path $root "origin/$role"
                $proof=Get-Content (Join-Path $body 'summary.json') -Raw | ConvertFrom-Json
                if($proof.fixture -cne 'origin' -or $proof.localSeed -ne '123456789' -or $proof.x -ne 0 -or $proof.z -ne 0 -or $proof.dimension -cne 'minecraft:overworld' -or $proof.status -cne $(if($role -ceq 'native_0'){'PRODUCED'}else{'PASS'}) -or $proof.liveContexts -ne 0 -or $proof.bindings -ne 0 -or !$proof.physicalPoiUnchanged){throw 'Incomplete preparation comparison'}
                if(!$fingerprint){$fingerprint=$proof.inputHash}
                if($proof.inputHash -cne $fingerprint){throw 'Source changed within preparation A/B'}
                if($role -eq 'host_17') {
                    if(!(Test-Path (Join-Path $body 'profile.jfr'))){throw 'Missing measured preparation JFR'}
                    $expectedRoots=if($mode -ceq 'shared'){1}else{81}
                    if($proof.worldStateBuilds -ne $expectedRoots -or $proof.ringPreparations -ne $expectedRoots -or $proof.ringBiomeSearches -ne $expectedRoots*128){throw 'Actual native ring work differs from the measured hypothesis'}
                }
                $rows += $proof
                if($role -ne 'native_0') {
                    if((Get-FileHash (Join-Path $body 'canonical.txt')).Hash -cne (Get-FileHash (Join-Path $root 'origin/native_0/canonical.txt')).Hash){throw 'Compared B output differs from independent Native'}
                    foreach($field in @('trace','preInputs','outerInputs','rejected','environment','structures')) {
                        if(($proof.$field | ConvertTo-Json -Depth 90 -Compress) -cne ($rows[0].$field | ConvertTo-Json -Depth 90 -Compress)){throw "Compared B inputs differ $field"}
                    }
                }
            }
            if(@($rows.pid|Sort-Object -Unique).Count -ne 4 -or @($rows.save|Sort-Object -Unique).Count -ne 4){throw 'Preparation comparison reused process/save'}
            $times[$mode]=[double](($rows | Where-Object role -Like 'host_*' | Measure-Object elapsedMs -Average).Average)/1000
        }
        foreach($role in @('native_0','native_1','host_17','host_23')) {
            $left=Join-Path $output "pair-$round/independent/origin/$role/canonical.txt"
            $right=Join-Path $output "pair-$round/shared/origin/$role/canonical.txt"
            if((Get-FileHash $left).Hash -cne (Get-FileHash $right).Hash){throw 'Old/shared preparation canonical outputs differ'}
        }
        $pairs += [pscustomobject]@{pair=$round;independentSeconds=$times.independent;sharedSeconds=$times.shared;improvementPercent=100*(1-$times.shared/$times.independent)}
    }
    $old=@($pairs.independentSeconds | Sort-Object)[1]
    $new=@($pairs.sharedSeconds | Sort-Object)[1]
    $improvement=100*(1-$new/$old)
    $summary=[ordered]@{status='MEASURED';correctness='PASS';scope='MODULE_ONLY_NOT_PHYSICAL_READY';pairs=$pairs;sourceFingerprint=$fingerprint;independentMedianSeconds=$old;sharedMedianSeconds=$new;improvementPercent=$improvement;moduleScreenPromising=($improvement -ge 5 -and @($pairs|Where-Object improvementPercent -LE 0).Count -eq 0);logicalProcessors=4;priority='BelowNormal';profiler='JFR profile on host_17 in both modes';productionRetention='Pending physical end-to-end gates'}
    [IO.File]::WriteAllText((Join-Path $output 'summary.json'),($summary|ConvertTo-Json -Depth 12)+"`n")
    $summary|ConvertTo-Json -Depth 12
}
if($CheckOnly){Check-Pairs;exit}
Add-Type -AssemblyName Microsoft.VisualBasic
if(Test-Path $output) {
    $resolved=[IO.Path]::GetFullPath($output)
    if(!$resolved.StartsWith($validation+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or ((Get-Item $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Unsafe preparation result replacement'}
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolved,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
}
New-Item -ItemType Directory -Path $output -Force | Out-Null
foreach($round in 0..2) {
    $order=if($round -eq 1){@('shared','independent')}else{@('independent','shared')}
    foreach($mode in $order) {
        [string[]]$network=if($Offline){@('-Offline')}else{@()}
        & pwsh -NoProfile -File $runner -Target 1.21.1-neoforge -Suite screen -Preparation $mode -Profile @network
        if($LASTEXITCODE -ne 0){throw "Preparation comparison failed pair=$round mode=$mode"}
        $source=Join-Path $validation ('result/screen'+$(if($mode -eq 'shared'){'-shared'}else{''})+'/1.21.1-neoforge')
        $destination=Join-Path $output "pair-$round/$mode"
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Recurse
        Write-Host "PREPARATION PAIR $round $mode archived"
    }
}
Check-Pairs
