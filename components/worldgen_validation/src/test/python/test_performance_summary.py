"""Tool-only telemetry fixtures: do not substitute these for a game/Oracle run."""

import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/performance-worldgen/summarize_structure_isolation.py"


class PerformanceSummaryTests(unittest.TestCase):
    def reduce(self, dimension, chunks_dimension=None, extra_dimension=None, legacy=False, pipeline="b"):
        with tempfile.TemporaryDirectory(prefix="mosaic-summary-test-") as directory:
            root = Path(directory)
            def chunk(owner):
                return {"key": owner + ":125,-37", "dimension": owner,
                        "firstNanos": {"VIEW_DEMAND": 2_000_000_000,
                                       "PREPARE_BEGIN": 2_000_000_000,
                                       "PREPARE_END": 3_000_000_000,
                                       "STAGE_FULL_END": 4_000_000_000,
                                       "READY_TO_SEND": 5_000_000_000,
                                       "ENTITY_TICKING_READY": 6_000_000_000}}
            chunks = [chunk(chunks_dimension or dimension)]
            if extra_dimension:
                chunks.append(chunk(extra_dimension))
            data = {"chunks": chunks, "samples": [
                {"seconds": 2, "processCpuLoad": .1, "queuedPrepare": 0,
                 "activePrepare": 1, "heapUsed": 1000}], "structureExperiment": {}}
            conditions = {"pipeline": pipeline, "structures": "baseline", "view": 2,
                          "x": 125, "z": -37}
            if not legacy:
                conditions["dimension"] = dimension
            jfr = {"exclusiveCpuBuckets": {"other": 1}, "cpuSamples": 1,
                   "gcPauseMs": 0, "maxGcPauseMs": 0}
            for name, value in (("checkpoint-timeline.json", data),
                                ("conditions.json", conditions), ("jfr-analysis.json", jfr)):
                (root / name).write_text(json.dumps(value), encoding="utf-8")
            with (root / "jfr-analysis.json.events.tsv").open("w", newline="", encoding="utf-8") as stream:
                writer = csv.writer(stream, delimiter="\t")
                writer.writerow(("nanos", "key", "phase", "detail", "thread"))
                writer.writerows(((1_000_000_000, "WORKLOAD", "WORKLOAD_START_frontier", "", "Server thread"),
                                  (2_000_000_000, chunks[0]["key"], "VIEW_DEMAND", "", "Server thread"),
                                  (5_000_000_000, chunks[0]["key"], "READY_TO_SEND", "", "Server thread"),
                                  (21_000_000_000, "WORKLOAD", "WORKLOAD_END", "", "Server thread")))
            (root / "console.log").write_text("MOSAIC PROFILE MARKER WORKLOAD_END\n", encoding="utf-8")
            process = subprocess.run([sys.executable, str(SCRIPT), directory], capture_output=True, text=True)
            summary = json.loads((root / "isolation-summary.json").read_text()) if process.returncode == 0 else None
            return process, summary

    def test_each_dimension_uses_its_actual_chunks(self):
        for dimension in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"):
            with self.subTest(dimension=dimension):
                process, summary = self.reduce(dimension)
                self.assertEqual(process.returncode, 0, process.stderr)
                self.assertEqual(summary["FULL"], 1)
                self.assertEqual(summary["sendReady"], 1)
                self.assertEqual(summary["averageTargetLatency"]["p95"], 1)
                self.assertEqual(summary["initialCenterTimelineSeconds"]["READY_TO_SEND"], 3)
                self.assertEqual(summary["coverage"][0]["currentViewportReady"], 1)

    def test_wrong_dimension_cannot_pass(self):
        process, _ = self.reduce("minecraft:the_end", chunks_dimension="minecraft:overworld")
        self.assertNotEqual(process.returncode, 0)
        self.assertIn("No actual chunks", process.stderr)

    def test_generation_elsewhere_is_not_silently_merged(self):
        process, _ = self.reduce("minecraft:the_end", extra_dimension="minecraft:overworld")
        self.assertNotEqual(process.returncode, 0)
        self.assertIn("unintended dimension", process.stderr)

    def test_legacy_overworld_conditions_remain_readable(self):
        process, summary = self.reduce("minecraft:overworld", legacy=True)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(summary["FULL"], 1)

    def test_vanilla_cannot_hide_mosaic_generation(self):
        process, _ = self.reduce("minecraft:overworld", pipeline="vanilla")
        self.assertNotEqual(process.returncode, 0)
        self.assertIn("Vanilla control entered Mosaic", process.stderr)


if __name__ == "__main__":
    unittest.main()
