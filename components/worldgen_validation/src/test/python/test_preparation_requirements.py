"""Synthetic obligation snapshots: no game behavior or performance acceptance."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts/performance-worldgen"))
from summarize_preparation_requirements import reduce


def event(phase, nanos, detail="", key="owner"):
    return {"phase": phase, "nanos": str(nanos), "detail": detail, "key": key}


class PreparationRequirementTests(unittest.TestCase):
    def test_upgraded_and_unfinished_work_is_not_misclassified(self):
        events = [event("WORKLOAD_START_unit", 0), event("WORKLOAD_END", 100),
                  event("PLAN_SHAPE", 1, "status=minecraft:full;requirements=169"),
                  event("PREPARE_REQUIREMENT", 2, "obligation=minecraft:terrain;requested=null"),
                  event("PREPARED_REQUIREMENT", 3, "obligation=minecraft:terrain;requested=null"),
                  event("PREPARE_REQUIREMENT", 4, "obligation=minecraft:terrain;requested=null", "upgraded"),
                  event("PREPARED_REQUIREMENT", 5, "obligation=minecraft:features;requested=minecraft:full", "upgraded"),
                  event("PREPARE_REQUIREMENT", 6, "obligation=minecraft:terrain;requested=null", "pending")]
        result = reduce(events)
        self.assertEqual(3, result["preparations"])
        self.assertEqual(2, result["completed"])
        self.assertEqual(1, result["inFlight"])
        self.assertEqual(["owner"], [row["owner"] for row in
                                    result["completedWithPreFeaturesObligationAtBothSnapshots"]])

    def test_missing_production_hook_is_not_empty_success(self):
        with self.assertRaisesRegex(ValueError, "not exercised"):
            reduce([event("WORKLOAD_START_unit", 0), event("WORKLOAD_END", 100)])

    def test_repeated_owner_is_explicit(self):
        with self.assertRaisesRegex(ValueError, "Repeated"):
            reduce([event("WORKLOAD_START_unit", 0), event("WORKLOAD_END", 100),
                    event("PREPARE_REQUIREMENT", 2, "obligation=minecraft:terrain;requested=null"),
                    event("PREPARE_REQUIREMENT", 3, "obligation=minecraft:terrain;requested=null")])


if __name__ == "__main__":
    unittest.main()
