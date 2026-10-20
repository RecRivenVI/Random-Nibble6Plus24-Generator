"""The JFR reducer must support both existing markers and native timestamp-only markers."""

import csv
import json
from pathlib import Path
import shutil
import subprocess
import unittest


COMPONENT = Path(__file__).resolve().parents[3]


class RecordingPerformanceTests(unittest.TestCase):
    def test_marker_schemas_keep_their_actual_time_domains(self):
        output = COMPONENT / "build/test_recording"
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK is required by the existing JFR tool")
        common = [java, "-XX:ActiveProcessorCount=1", "-Xmx256M"]
        subprocess.run(common + [str(COMPONENT / "src/test/java/RecordingFixture.java"), str(output)],
                       check=True, capture_output=True)
        for name, fallback in (("with-clock", False), ("without-clock", True)):
            result = output / (name + ".json")
            subprocess.run(common + [str(COMPONENT / "scripts/performance-worldgen/AnalyzeRecording.java"),
                                    str(output / (name + ".jfr")), str(result)], check=True, capture_output=True)
            data = json.loads(result.read_text(encoding="utf-8"))
            self.assertEqual(fallback, data["missingElapsedNanosUsesJfrWindowTimestamps"])
            self.assertEqual(2, data["eventCounts"]["mosaic.ChunkPhase"])
            self.assertGreater(data["wallSeconds"], 0)
            if not fallback:
                loads = data["cpuLoadSamples"]
                self.assertEqual(1, len(loads))
                self.assertEqual(len(loads), data["eventCounts"]["jdk.CPULoad"])
                for load in loads:
                    self.assertGreaterEqual(load["machineTotal"], 0)
                    self.assertLessEqual(load["machineTotal"], 1)
                    self.assertIn("jvmUser", load)
                    self.assertIn("jvmSystem", load)
                self.assertAlmostEqual(sum(row["machineTotal"] for row in loads) / len(loads),
                                       data["machineCpuLoadSampleMean"], places=12)
                self.assertEqual(.25, data["machineCpuLoadSampleMean"])
                self.assertEqual(.125, loads[0]["jvmUser"])
                self.assertEqual(.0625, loads[0]["jvmSystem"])
            else:
                self.assertEqual([], data["cpuLoadSamples"])
                self.assertIsNone(data["machineCpuLoadSampleMean"])
            with Path(str(result) + ".events.tsv").open(encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream, delimiter="\t"))
            values = [int(row["nanos"]) for row in rows]
            if fallback:
                self.assertEqual(0, values[0])
                self.assertAlmostEqual(data["wallSeconds"] * 1e9, values[1], delta=1)
            else:
                self.assertEqual([100, 200], values)


if __name__ == "__main__":
    unittest.main()
