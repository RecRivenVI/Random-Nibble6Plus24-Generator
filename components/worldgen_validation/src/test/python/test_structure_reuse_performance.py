"""The diagnostic model is tested without starting a Minecraft world."""

from pathlib import Path
import importlib.util
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[5]
PROBE = ROOT / "versions/26.3-fabric/src/probe"
SPEC = importlib.util.spec_from_file_location("source_audit", ROOT / "components/worldgen_validation/scripts/performance-worldgen/summarize_source_audit.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class StructureReusePerformanceTests(unittest.TestCase):
    def fixture(self):
        conditions = {"target": "26.3-fabric", "pipeline": "b", "structures": "baseline",
                      "structureReuseAudit": True, "cpu": 4, "affinity": 3840, "priority": "BelowNormal"}
        counters = {"openScopes": 2, "closedScopes": 2, "evaluationCountMismatch": 0,
                    "retainedAfterClose": 0, "evaluations": 100, "nativeNanos": 1000,
                    "maximumStates": 5, "repeat2": 10, "repeat4": 20, "repeat16": 30,
                    "nanos2": 100, "nanos4": 200, "nanos16": 300}
        return conditions, {"final": True, "availableProcessors": 4,
                            "aggregates": {"reuseAudit." + key: value for key, value in counters.items()}}

    def test_opportunity_is_not_promoted_to_speedup(self):
        conditions, trace = self.fixture()
        result = AUDIT.reduce(conditions, trace)
        self.assertFalse(result["performanceComparable"])
        self.assertEqual(10, result["opportunity"]["2"]["repeatedEvaluationsPercent"])

    def test_native_neoforge_audit_is_separately_named_and_unknown_targets_fail(self):
        conditions, trace = self.fixture()
        conditions["target"] = "26.3-neoforge"
        self.assertEqual("26.3-neoforge", AUDIT.reduce(conditions, trace)["conditions"]["target"])
        for target in ("26.1.2-neoforge", "1.21.1-neoforge", "1.20.1-forge", None):
            conditions["target"] = target
            with self.subTest(target=target), self.assertRaises(ValueError):
                AUDIT.reduce(conditions, trace)

    def test_incomplete_or_inconsistent_observer_cannot_pass(self):
        for field, value in (("closedScopes", 1), ("evaluationCountMismatch", 1),
                             ("retainedAfterClose", 1), ("repeat2", 101)):
            conditions, trace = self.fixture()
            trace["aggregates"]["reuseAudit." + field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                AUDIT.reduce(conditions, trace)

    def test_normal_or_unbounded_run_is_not_an_audit(self):
        conditions, trace = self.fixture()
        conditions["structureReuseAudit"] = False
        with self.assertRaises(ValueError):
            AUDIT.reduce(conditions, trace)

    def test_native_identity_history_model(self):
        javac = shutil.which("javac")
        self.assertIsNotNone(javac, "The build requires a JDK")
        java = Path(javac).with_name("java.exe" if Path(javac).suffix == ".exe" else "java")
        harness = ROOT / "components/worldgen_validation/src/test/java/io/github/recrivenvi/randomnibble6plus24generator/validation/StructureReuseHistoryTest.java"
        for target in ("26.3-fabric", "26.3-neoforge"):
            history = ROOT / f"versions/{target}/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/performance/StructureReuseHistory.java"
            with self.subTest(target=target), tempfile.TemporaryDirectory() as temporary:
                subprocess.run([javac, "-J-XX:ActiveProcessorCount=2", "-d", temporary, str(history), str(harness)], check=True, capture_output=True, text=True)
                result = subprocess.run([str(java), "-XX:ActiveProcessorCount=2", "-cp", temporary,
                                         "io.github.recrivenvi.randomnibble6plus24generator.validation.StructureReuseHistoryTest"], check=True, capture_output=True, text=True)
                self.assertIn("PASS:", result.stdout)

    def test_audit_is_opt_in_and_does_not_replace_native_work(self):
        for target in ("26.3-fabric", "26.3-neoforge"):
            base = ROOT / f"versions/{target}/src/probe"
            plugin = (base / "java/io/github/recrivenvi/randomnibble6plus24generator/probe/ValidationMixinPlugin.java").read_text(encoding="utf-8")
            source = (base / "java/io/github/recrivenvi/randomnibble6plus24generator/performance/mixin/PerfStructureReuseAuditMixin.java").read_text(encoding="utf-8")
            self.assertIn('Boolean.getBoolean("mosaic.profile.structureReuseAudit")', plugin)
            self.assertLess(source.index("original.call(evaluate)"), source.index("mosaic$history.observe("))
            self.assertIn("mosaic$history.invalidate(retained)", source)
            for forbidden in (".replay(", ".createStructures(", ".setSeed(", "sources.put(", "catch ("):
                self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
