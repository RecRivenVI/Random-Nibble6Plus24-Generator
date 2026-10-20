"""Synthetic reducer boundaries only, never game or optimization acceptance evidence."""

import csv
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts/performance-worldgen"))
import compare_biome_locks as locks


class BiomeLockPerformanceTests(unittest.TestCase):
    def reduce(self, changed_source=False, wrong_path=False):
        with tempfile.TemporaryDirectory(prefix="biome-lock-unit-") as directory:
            root = Path(directory)
            folders = [root / "reference", root / "candidate"]
            runs = []
            for index, folder in enumerate(folders):
                folder.mkdir()
                detail = "narrow=true" if index else "narrow=false"
                with (folder / "jfr-analysis.json.events.tsv").open("w", newline="", encoding="utf-8") as stream:
                    writer = csv.writer(stream, delimiter="\t")
                    writer.writerow(("phase", "detail"))
                    writer.writerow(("PHYSICAL_BIOMES_STRUCTURE_READY", "narrow=false" if wrong_path else detail))
                bounds = {"lowerSeconds": 1, "upperSeconds": 1, "exactSeconds": 1}
                result = {"processId": index, "runIdentity": str(index), "canonicalPerSecond": 2 + index,
                          "fullPerSecond": 2 + index, "sendReadyPerSecond": 2 + index,
                          "finalNativeDemand": {"p50": bounds, "p95": bounds}}
                conditions = {"pipeline": "b", "target": "26.3-fabric", "inputHash": "same",
                              "runIdentity": str(index), "cpu": 4}
                if index == 0:
                    conditions["referenceBiomeLocks"] = True
                elif changed_source:
                    conditions["inputHash"] = "changed"
                runs.append((conditions, result, {"owner"}, {"owner"}))
            with patch.object(locks, "read_run", side_effect=runs):
                return locks.compare(*folders)

    def test_same_source_and_actual_paths_are_required(self):
        result = self.reduce()
        self.assertEqual("MEASURED_NOT_RETENTION_APPROVAL", result["status"])
        self.assertEqual(150, result["versusReferencePercent"]["sendReadyPerSecond"])
        self.assertEqual({"reference": 1, "candidate": 1}, result["actualLockPathHits"])

    def test_source_change_is_not_a_performance_control(self):
        with self.assertRaisesRegex(ValueError, "Changed source"):
            self.reduce(changed_source=True)

    def test_unused_switch_is_not_a_lock_comparison(self):
        with self.assertRaisesRegex(ValueError, "actual physical"):
            self.reduce(wrong_path=True)


if __name__ == "__main__":
    unittest.main()
