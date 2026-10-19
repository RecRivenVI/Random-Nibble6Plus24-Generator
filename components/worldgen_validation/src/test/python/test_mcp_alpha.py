"""The external alpha is a pinned test input, never a production dependency."""

import importlib.util
import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[5]
SPEC = importlib.util.spec_from_file_location(
    "mcp_alpha", ROOT / "components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py")
MCP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MCP)
sys.path.insert(0, str(Path(MCP.__file__).parent))
LIFECYCLE_SPEC = importlib.util.spec_from_file_location("client_lifecycle", Path(MCP.__file__).with_name("client_lifecycle.py"))
LIFECYCLE = importlib.util.module_from_spec(LIFECYCLE_SPEC)
LIFECYCLE_SPEC.loader.exec_module(LIFECYCLE)


class McpAlphaTests(unittest.TestCase):
    def test_native_library_os_rules(self):
        self.assertTrue(MCP.allowed({}))
        self.assertTrue(MCP.allowed({"rules": [{"action": "allow", "os": {"name": "windows"}}]}))
        self.assertFalse(MCP.allowed({"rules": [{"action": "allow", "os": {"name": "linux"}}]}))

    def test_runtime_is_bound_to_game_pid_directory_and_start(self):
        with tempfile.TemporaryDirectory() as temporary:
            instance = Path(temporary)
            record = {"pid": 42, "target": MCP.TARGET, "gameDirectory": str(instance),
                      "runtimeInstanceId": "first", "processStartedAt": "2026-10-17T00:00:00Z"}
            MCP.save(instance / "minecraft-protocol/endpoint.json", record)
            MCP.save(instance / "automation-process.json", {"pid": 42})
            actual = {"CommandLine": str(instance / "automation-java.args"), "startedMillis": 1792195200000}
            with patch.object(MCP.subprocess, "check_output", return_value=json.dumps(actual)):
                self.assertEqual(record, MCP.connect(instance))
                record["runtimeInstanceId"] = "second"
                MCP.save(instance / "minecraft-protocol/endpoint.json", record)
                with self.assertRaisesRegex(RuntimeError, "Runtime changed"):
                    MCP.connect(instance)

    def test_minimal_launcher_has_no_development_or_extra_mod_dependency(self):
        source = Path(MCP.__file__).read_text(encoding="utf-8")
        self.assertIn('stage == "minimal"', source)
        self.assertIn('manifest["packagedRuntimeVerified"] is not True', source)
        self.assertIn('".withdrawn"', source)
        self.assertIn('"-Dminecraft.protocol.probe.port=0"', source)
        self.assertNotIn("fabric.development=true", source)
        self.assertNotIn("pyautogui", source)
        self.assertNotIn("SendKeys", source)

    def test_real_client_observer_never_creates_or_mutates_gameplay(self):
        observer = (ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/verify/ClientLifecycleEvidence.java").read_text(encoding="utf-8")
        for forbidden in ("new ServerPlayer(", ".teleport(", ".hurtServer(", ".setBlock(", "getChunkFuture("):
            self.assertNotIn(forbidden, observer)
        self.assertIn("getEntityTickingChunkFuture().getNow(null)", observer)
        self.assertIn('input.get("serverRunId")', observer)
        self.assertIn('"Reload regenerated a saved FULL owner"', observer)
        self.assertIn("STATES.remove(server)", observer)

    def test_pass_label_without_real_checkpoint_coverage_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with patch.object(LIFECYCLE.alpha, "VALIDATION", directory):
                LIFECYCLE.alpha.save(directory / "result/client-lifecycle/lifecycle_b/fake/summary.json",
                                     {"status": "PASS", "inputReleased": True, "checkpoints": []})
                with self.assertRaisesRegex(RuntimeError, "coverage differs"):
                    LIFECYCLE.check("lifecycle_b", "fake")


if __name__ == "__main__":
    unittest.main()
