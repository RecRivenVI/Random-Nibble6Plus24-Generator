"""A second direct Gradle run must not race the first run's port or launch files."""

from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[5]


@unittest.skipUnless(shutil.which("pwsh"), "PowerShell is required for the production driver")
class ProductionDriverLeaseTests(unittest.TestCase):
    def test_busy_run_is_rejected_before_instance_preparation(self):
        command = r'''
$ErrorActionPreference = 'Stop'
$directory = Join-Path (Get-Location) 'validations/integration-production_lifecycle/instance'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$lease = [IO.File]::Open((Join-Path $directory 'production-gradle.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    $instance = Join-Path $directory '26.3-fabric/server/server.properties'
    $before = if (Test-Path $instance) { [IO.File]::ReadAllText($instance) } else { $null }
    try {
        & ./components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Offline
        throw 'The second driver unexpectedly passed its lease boundary'
    } catch {
        if ($_.Exception.Message -notlike 'This validation already has a direct Gradle run;*') { throw }
    }
    $after = if (Test-Path $instance) { [IO.File]::ReadAllText($instance) } else { $null }
    if ($before -cne $after) { throw 'The rejected driver mutated server.properties' }
    'PASS: busy driver rejected before touching the instance or launching a JVM'
} finally {
    $lease.Dispose()
}
'''
        completed = subprocess.run(
            ["pwsh", "-NoProfile", "-Command", command], cwd=ROOT,
            text=True, encoding="utf-8", capture_output=True, timeout=20,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertIn("PASS: busy driver rejected", completed.stdout)


if __name__ == "__main__":
    unittest.main()
