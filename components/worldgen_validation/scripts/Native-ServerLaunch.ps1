function New-OwnedNativeConfig([object]$Launch) {
    if($Target -eq '26.3-neoforge'){
        if($Launch.main -cne 'net.neoforged.devlaunch.Main' -or @($Launch.gameArguments).Count -ne 1 -or !$Launch.gameArguments[0].StartsWith('@')){throw 'Unknown NeoForge native argument file'}
        $source=[IO.Path]::GetFullPath($Launch.gameArguments[0].Substring(1))
        $allowed=[IO.Path]::GetFullPath((Join-Path $validationRepository "versions/$validationTarget/build/moddev"))+[IO.Path]::DirectorySeparatorChar
        if(!$source.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or ((Get-Item $source).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Foreign or linked NeoForge native arguments'}
        $destination=Join-Path $validationServer "native-args-$EvidenceLabel.txt"
        if(Test-Path $destination){throw 'Preserve existing owned native arguments'}
        $lines=[IO.File]::ReadAllLines($source)
        $main=@($lines|Where-Object{$_ -match '^\S' -and !$_.StartsWith('#')})[0]
        if($main -cne 'net.neoforged.fml.startup.Server'){throw 'Unknown NeoForge argument-file entry point'}
        $ports=0
        for($i=0;$i -lt $lines.Length;$i++){
            if($lines[$i].Trim() -ceq '--port'){
                if($i+1 -ge $lines.Length -or $lines[$i+1] -notmatch '^\d+$'){throw 'Unknown native NeoForge port argument'}
                $lines[$i+1]='0';$ports++
            }
        }
        if($ports -ne 1){throw 'NeoForge native port is not uniquely understood'}
        [IO.File]::WriteAllText($destination,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
        $record=@{schema=1;source=$source;sourceSha256=(Get-FileHash $source).Hash;owned=$destination;ownedSha256=(Get-FileHash $destination).Hash;requestedPort=0;replacedServerPortArguments=$ports;kind='NEOFORGE_ARGUMENT_FILE'}
        [IO.File]::WriteAllText((Join-Path $resultRoot 'native-launch-config.json'),($record|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        return $destination
    }
    $configs=@($Launch.jvmArguments|Where-Object{$_.StartsWith('-Dfabric.dli.config=')})
    if($configs.Count -ne 1){throw 'Ambiguous native development configuration'}
    $source=[IO.Path]::GetFullPath($configs[0].Substring('-Dfabric.dli.config='.Length))
    $allowed=[IO.Path]::GetFullPath((Join-Path $validationRepository '.gradle'))+[IO.Path]::DirectorySeparatorChar
    if(!$source.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or ((Get-Item $source).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Foreign or linked native launch configuration'}
    $destination=Join-Path $validationServer "native-dli-$EvidenceLabel.cfg"
    if(Test-Path $destination){throw 'Preserve existing owned native launch configuration'}
    $lines=[IO.File]::ReadAllLines($source)
    $section='';$ports=0
    for($i=0;$i -lt $lines.Length;$i++){
        if($lines[$i] -match '^\S'){$section=$lines[$i].Trim();continue}
        if($section -ceq 'serverArgs' -and $lines[$i].Trim() -ceq '--port'){
            if($i+1 -ge $lines.Length -or $lines[$i+1] -notmatch '^(\s+)\d+$'){throw 'Unrecognized native server port argument'}
            $lines[$i+1]=$Matches[1]+'0';$ports++
        }
    }
    if($ports -gt 1){throw 'Native server port selection is not uniquely understood'}
    # Loom can rewrite the shared config for a different task between game launches.
    if($ports -eq 0){$lines+=@('serverArgs',"`t--port","`t0")}
    [IO.File]::WriteAllText($destination,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
    $record=@{schema=1;source=$source;sourceSha256=(Get-FileHash $source).Hash;owned=$destination;ownedSha256=(Get-FileHash $destination).Hash;requestedPort=0;replacedServerPortArguments=$ports}
    [IO.File]::WriteAllText((Join-Path $resultRoot 'native-launch-config.json'),($record|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    return $destination
}
