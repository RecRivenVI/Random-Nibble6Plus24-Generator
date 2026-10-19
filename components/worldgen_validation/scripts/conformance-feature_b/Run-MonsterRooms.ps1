param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
. (Join-Path $PSScriptRoot '../Validation-Environment.ps1')
$output = Join-Path $validationRepository "validations/$validationProject/result/native_monster/$validationTarget"
if ($CheckOnly) {
    $native = @(); $differences = @()
    foreach ($i in 0..2) {
        $s = Get-Content (Join-Path $output "fixture_$i/summary.json") -Raw | ConvertFrom-Json
        if ($s.status -cne 'CAPTURED_NATIVE_ONLY' -or $s.experimentalGenerations -ne 0 -or $s.bindings -ne 0 -or $s.generator -cne 'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator' -or $s.monsterRooms.Count -eq 0) { throw 'Missing independent physical monster-room proof' }
        $native += $s
    }
    if (@($native.pid | Select-Object -Unique).Count -ne 3 -or @($native.worldPath | Select-Object -Unique).Count -ne 3) { throw 'Native monster-room worlds not independent' }
    foreach ($i in 0..1) {
        $p = Join-Path $validationRepository "validations/$validationProject/result/structure_reuse/$validationTarget/monster_host_$i/monster-rooms.json"
        $b = Get-Content $p -Raw | ConvertFrom-Json
        if (@($b.fixtures.PSObject.Properties).Count -ne 3 -or $b.traces.Count -ne 6) { throw 'Missing B monster-room replay' }
        foreach ($f in $b.fixtures.PSObject.Properties.Value) {
            if ($f.hash -cne $f.originalHash -or !$f.biomeReuse.closed -or $f.biomeReuse.resident -ne 0) { throw 'B monster-room data/regression differs' }
        }
        foreach ($n in $native) {
            $left = @($n.monsterRoomTraces)[0]
            $traces = @($b.traces | Where-Object seed -CEQ $left.seed)
            if ($traces.Count -ne 2) { throw 'Missing original/reused B room traces' }
            foreach ($right in $traces) {
                $first = $null
                for ($j=0; $j -lt [Math]::Min($left.reads.Count,$right.reads.Count); $j++) {
                    $a=$left.reads[$j]; $c=$right.reads[$j]
                    if ($a.api -cne $c.api -or $a.value -cne $c.value -or ($a.pos -join ',') -cne ($c.pos -join ',')) { $first=@{index=$j; native=$a; b=$c}; break }
                }
                if ($null -eq $first -or $first.native.api -cne $first.b.api -or ($first.native.pos -join ',') -cne ($first.b.pos -join ',') -or
                    ([int][Math]::Floor($first.b.pos[0]/16.0) -eq $n.fixture.x -and [int][Math]::Floor($first.b.pos[2]/16.0) -eq $n.fixture.z)) { throw 'Warning was not traced to an out-of-writer terrain read' }
                $differences += @{host=$i; seed=$left.seed; writer=$left.writer; origin=$left.origin; firstDifference=$first}
            }
        }
    }
    [IO.File]::WriteAllText((Join-Path $output 'comparison.json'), (@{status='PASS_B_WARNING_CAUSE'; firstDifferences=$differences; fullVanillaFeatureParity=$false} | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    'CAPTURED three fresh physical Vanilla monster-room fixtures; warnings retained, not suppressed.'
    exit 0
}
$process = [Diagnostics.Process]::GetCurrentProcess()
$priority = $process.PriorityClass; $affinity = $process.ProcessorAffinity
$options = $env:JAVA_TOOL_OPTIONS
$properties = [IO.File]::ReadAllText($validationProperties)
$world = Join-Path $validationServer 'monster-room-export'
try {
    $process.PriorityClass = 'BelowNormal'; $process.ProcessorAffinity = 3840
    foreach ($path in @($output,$world)) { if (Test-Path -LiteralPath $path) { Move-ValidationToRecycleBin -LiteralPath $path } }
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 -Dfeatureb.export=true -Dfeatureb.nativeMonster=true'
    & (Join-Path $validationRepository 'gradlew.bat') ":version:$validationTarget`:runValidationConformanceFeatureBServer" --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4' '--args=nogui --world monster-room-export' *> (Join-Path $output 'export.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $output 'export.log') -Tail 40; throw 'Monster-room launcher export failed' }
    $launch = Get-Content (Join-Path $output 'launcher.json') -Raw | ConvertFrom-Json
    Move-ValidationToRecycleBin -LiteralPath $world
    $cases = @()
    foreach ($i in 0..2) { $cases += @{ name="monster-room-native-$i"; seed=$launch.monsterFixtures[$i].seed; flags="-Dfeatureb.nativeMonster=true -Dfeatureb.monsterIndex=$i -Dfeatureb.variant=fixture_$i"; evidence=(Join-Path $output "fixture_$i/summary.json") } }
    foreach ($i in 0..1) { $cases += @{ name="monster-room-b-$i"; seed=(@(17,23)[$i]); flags="-Dfeatureb.monsterOnly=true -Dfeatureb.structureReuse=true -Dfeatureb.variant=monster_host_$i"; evidence=(Join-Path $validationRepository "validations/$validationProject/result/structure_reuse/$validationTarget/monster_host_$i/monster-rooms.json") } }
    foreach ($case in $cases) {
        $name = $case.name
        $world = Join-Path $validationServer $name
        if (Test-Path -LiteralPath $world) { Move-ValidationToRecycleBin -LiteralPath $world }
        [IO.File]::WriteAllText($validationProperties, "level-seed=$($case.seed)`nlevel-type=minecraft:normal`nserver-ip=127.0.0.1`nserver-port=25589`nonline-mode=false`nview-distance=2`nsimulation-distance=2`npause-when-empty-seconds=-1`n", [Text.UTF8Encoding]::new($false))
        $env:JAVA_TOOL_OPTIONS = "-XX:ActiveProcessorCount=4 $($case.flags)"
        $start = [Diagnostics.ProcessStartInfo]::new($launch.java)
        $start.WorkingDirectory = $launch.directory; $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
        foreach ($argument in $launch.jvmArguments) { $start.ArgumentList.Add($argument) }
        $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($launch.classpath); $start.ArgumentList.Add($launch.main)
        foreach ($argument in $launch.gameArguments) { $start.ArgumentList.Add($(if ($argument -ceq 'monster-room-export') { $name } else { $argument })) }
        $child = [Diagnostics.Process]::Start($start)
        $child.PriorityClass = 'BelowNormal'; $child.ProcessorAffinity = 3840
        $stdout = $child.StandardOutput.ReadToEndAsync(); $stderr = $child.StandardError.ReadToEndAsync()
        $child.WaitForExit()
        [IO.File]::WriteAllText((Join-Path $output "$name.log"), $stdout.Result + $stderr.Result, [Text.UTF8Encoding]::new($false))
        if ($child.ExitCode -ne 0 -or !(Test-Path $case.evidence)) { Get-Content (Join-Path $output "$name.log") -Tail 50; throw 'Monster-room capture failed' }
        Move-ValidationToRecycleBin -LiteralPath $world
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $options
    [IO.File]::WriteAllText($validationProperties, $properties, [Text.UTF8Encoding]::new($false))
    $process.PriorityClass = $priority; $process.ProcessorAffinity = $affinity
}
& $PSCommandPath -CheckOnly
