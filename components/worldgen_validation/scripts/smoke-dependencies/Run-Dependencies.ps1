param(
    [Parameter(Mandatory)][ValidateSet('26.3-fabric','26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge','1.20.1-forge')][string]$Target,
    [switch]$CheckOnly,
    [switch]$ServerOnly
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$validation=Join-Path $repo 'validations/smoke-dependencies'
$result=Join-Path $validation "result/$Target"
$instance=Join-Path $validation "instance/$Target"
$facts=Join-Path $repo "versions/$Target/target.properties"
$productFacts=ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $repo 'gradle.properties')))
$jar=Join-Path $repo "versions/$Target/build/libs/$($productFacts.mod_id)-$Target-$($productFacts.mod_version).jar"
$summary=Join-Path $result 'summary.json'

function Confirm-Smoke {
    $value=Get-Content -LiteralPath $summary -Raw|ConvertFrom-Json
    if($value.status -cne 'PASS' -or $value.target -cne $Target -or
            $value.factsSha256 -cne (Get-FileHash -LiteralPath $facts).Hash -or
            $value.productJarSha256 -cne (Get-FileHash -LiteralPath $jar).Hash){throw 'Missing/current dependency smoke mismatch'}
    foreach($role in @('server','client')){
        if($value.$role.status -cne 'PASS' -or $value.$role.target -cne $Target -or
                $value.$role.role -cne $role -or $value.$role.logicalProcessors -ne 4){throw 'Wrong native smoke role/resources'}
    }
    if($value.server.nativeTicks -lt 40 -or $value.client.playerTicks -lt 20 -or
            !$value.client.integratedServer -or $value.client.chunkClass -cne 'net.minecraft.world.level.chunk.LevelChunk'){throw 'No real client world/chunk proof'}
    "PASS: $Target native server + real integrated client Quick Play, normal save/exit. Not B parity or performance."
}
if($CheckOnly){Confirm-Smoke;exit}
if(Test-Path -LiteralPath $summary){throw 'Preserve existing dependency result; use CheckOnly'}
if(!(Select-String -LiteralPath (Join-Path $repo 'local.toml') -Pattern '^\s*eula\s*=\s*true\s*(#.*)?$' -Quiet)){throw 'No user EULA consent'}
New-Item -ItemType Directory -Path $result -Force|Out-Null
$controller=[Diagnostics.Process]::GetCurrentProcess()
$savedAffinity=$controller.ProcessorAffinity
$savedPriority=$controller.PriorityClass
$controller.ProcessorAffinity=[IntPtr]3840
$controller.PriorityClass='BelowNormal'
try{
    foreach($role in @('server','client')){
        if($ServerOnly -and $role -ceq 'client'){return}
        $directory=Join-Path $instance $role
        New-Item -ItemType Directory -Path $directory -Force|Out-Null
        if(Test-Path (Join-Path $directory 'crash-reports')){throw 'Preserve previous smoke failure'}
        if($role -ceq 'server'){
            $serverProof=Join-Path $result 'server.json'
            if(Test-Path -LiteralPath $serverProof){
                $prior=Get-Content -LiteralPath $serverProof -Raw|ConvertFrom-Json
                if($prior.status -cne 'PASS' -or $prior.target -cne $Target -or $prior.role -cne 'server' -or
                        $prior.logicalProcessors -ne 4 -or $prior.nativeTicks -lt 40 -or
                        !(Test-Path (Join-Path $directory 'world/level.dat'))){throw 'Invalid server smoke continuation'}
                continue
            }
            if(Test-Path (Join-Path $directory 'world')){throw 'Preserve previous smoke world'}
            [IO.File]::WriteAllText((Join-Path $directory 'server.properties'),"level-name=world`nlevel-seed=123456789`nonline-mode=false`nwhite-list=false`nserver-ip=127.0.0.1`nview-distance=2`nsimulation-distance=2`n")
        }else{
            $world=Join-Path $directory 'saves/DependencySmoke'
            if(Test-Path $world){throw 'Preserve previous client smoke world'}
            New-Item -ItemType Directory -Path (Split-Path $world) -Force|Out-Null
            Copy-Item -LiteralPath (Join-Path $instance 'server/world') -Destination $world -Recurse
            [IO.File]::WriteAllText((Join-Path $directory 'options.txt'),"onboardAccessibility:false`nfullscreen:false`noverrideWidth:1920`noverrideHeight:1080`nsoundCategory_master:0.0`nrenderDistance:2`nsimulationDistance:5`nmaxFps:30`n")
        }
        $title=$role.Substring(0,1).ToUpperInvariant()+$role.Substring(1)
        $task=":version:$Target`:runValidationSmokeDependencies$title"
        $out=Join-Path $result "$role.stdout.log"
        $err=Join-Path $result "$role.stderr.log"
        $command="Set-Location -LiteralPath '$repo'; `$env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4'; & .\gradlew.bat '$task' --no-daemon --no-configuration-cache --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=2'; exit `$LASTEXITCODE"
        $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        $process=Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-EncodedCommand',$encoded) -WorkingDirectory $repo -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
        $process.PriorityClass='BelowNormal';$process.ProcessorAffinity=[IntPtr]3840
        $deadline=[DateTime]::UtcNow.AddMinutes(15)
        while(!$process.WaitForExit(1000)){
            if([DateTime]::UtcNow -lt $deadline){continue}
            # Quick Play can remain on a failed login screen (template format-validation).
            # This is a failed smoke, never a fabricated successful world entry.
            $owned=@(Get-CimInstance Win32_Process|Where-Object{
                $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine.Contains($repo) -and
                $_.CommandLine.Contains($Target) -and
                $_.CommandLine -match 'validation\.name=smoke-dependencies|validationSmokeDependencies(Client|Server)Run'
            })
            foreach($game in $owned){Stop-Process -Id $game.ProcessId}
            if(!$process.WaitForExit(10000)){$process.Kill()}
            throw "$Target $role exceeded the bounded smoke; inspect retained logs"
        }
        $process.Refresh()
        if($process.ExitCode -ne 0){Get-Content -LiteralPath $out -Tail 25;Get-Content -LiteralPath $err -Tail 25;throw "$Target $role failed"}
        $proof=Get-Content -LiteralPath (Join-Path $result "$role.json") -Raw|ConvertFrom-Json
        if($proof.target -cne $Target -or $proof.role -cne $role -or $proof.status -cne 'PASS'){throw 'Missing fresh game proof'}
        if(!(Test-Path (Join-Path $directory 'world/level.dat')) -and $role -ceq 'server'){throw 'Native world did not save'}
        if($role -ceq 'client' -and !(Test-Path (Join-Path $directory 'saves/DependencySmoke/level.dat'))){throw 'Client world did not save'}
        if(Test-Path (Join-Path $directory 'crash-reports')){throw 'New smoke crash report'}
    }
    $value=@{schema=1;status='PASS';target=$Target;factsSha256=(Get-FileHash $facts).Hash;productJarSha256=(Get-FileHash $jar).Hash;
        server=(Get-Content (Join-Path $result 'server.json') -Raw|ConvertFrom-Json);
        client=(Get-Content (Join-Path $result 'client.json') -Raw|ConvertFrom-Json);
        command="pwsh -NoProfile -File components/worldgen_validation/scripts/smoke-dependencies/Run-Dependencies.ps1 -Target $Target";
        scope='VANILLA_WORLD_PRODUCT_LOADED_NOT_B_ORACLE_OR_PERFORMANCE'}
    [IO.File]::WriteAllText($summary,($value|ConvertTo-Json -Depth 8)+"`n")
    Confirm-Smoke
    Add-Type -AssemblyName Microsoft.VisualBasic
    $ownedInstance=[IO.Path]::GetFullPath($instance)
    $instanceRoot=[IO.Path]::GetFullPath((Join-Path $validation 'instance'))+[IO.Path]::DirectorySeparatorChar
    if(!$ownedInstance.StartsWith($instanceRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Smoke cleanup outside owned instance'}
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($ownedInstance,'OnlyErrorDialogs','SendToRecycleBin','ThrowException')
    foreach($role in @('server','client')){
        foreach($stream in @('stdout','stderr')){
            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile((Join-Path $result "$role.$stream.log"),'OnlyErrorDialogs','SendToRecycleBin')
        }
    }
}finally{
    $controller.PriorityClass=$savedPriority
    $controller.ProcessorAffinity=$savedAffinity
}
