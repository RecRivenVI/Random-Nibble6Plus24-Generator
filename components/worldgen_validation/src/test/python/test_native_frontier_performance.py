"""Native frontier reports retain unfinished and still-relevant requests."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parents[3] / "scripts/performance-worldgen"
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location("native_frontier", SCRIPTS / "compare_native_frontier.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def chunk(key, demand=0, ready=None):
    phases = {"VIEW_DEMAND": demand}
    if ready is not None:
        phases["READY_TO_SEND"] = ready
    return {"key": key, "firstNanos": phases}


class NativeFrontierPerformanceTests(unittest.TestCase):
    def test_diagnostic_history_cannot_be_used_as_a_performance_control(self):
        conditions = {"structureReuseAudit": True}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name, value in (("conditions.json", conditions),
                                ("isolation-summary.json", {"conditions": conditions}),
                                ("checkpoint-timeline.json", {})):
                (root / name).write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Diagnostic"):
                MODULE.read_run(root)

    def test_short_high_view_window_is_not_merged_with_standard_window(self):
        MODULE.validate_window(45, 45)
        for actual, requested in ((45, 60), (60, 45)):
            with self.subTest(actual=actual), self.assertRaises(ValueError):
                MODULE.validate_window(actual, requested)

    def test_standard_view_evidence_cannot_be_used_as_high_view(self):
        conditions = {"cpu": 4, "affinity": 3840, "priority": "BelowNormal", "view": 8,
                      "simulation": 8, "seconds": 60, "moves": 2, "moveSeconds": 15,
                      "moveChunks": 2, "structures": "baseline", "cliFrontierNotPlayerSpawn": True}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name, value in (("conditions.json", conditions),
                                ("isolation-summary.json", {"conditions": conditions}),
                                ("checkpoint-timeline.json", {"availableProcessors": 4})):
                (root / name).write_text(json.dumps(value), encoding="utf-8")
            for view in (12, 16):
                with self.subTest(view=view), self.assertRaisesRegex(ValueError, "controlled"):
                    MODULE.read_run(root, view=view, seconds=45)

    def test_marker_emission_offset_does_not_rewrite_measured_duration(self):
        MODULE.validate_window(59.9775112, 60)
        for seconds in (20, 59, 66):
            with self.subTest(seconds=seconds), self.assertRaises(ValueError):
                MODULE.validate_window(seconds, 60)

    def test_pending_tail_remains_unbounded(self):
        result = MODULE.cohort([chunk("a", ready=2_000_000_000), chunk("b")],
                               {"a", "b"}, 60_000_000_000)
        self.assertEqual(2, result["requests"])
        self.assertEqual(2, result["p50"]["exactSeconds"])
        self.assertEqual(60, result["p95"]["lowerSeconds"])
        self.assertIsNone(result["p95"]["exactSeconds"])
        self.assertEqual(1, result["p95"]["censored"])

    def test_obsolete_request_does_not_become_current_tail(self):
        result = MODULE.cohort([chunk("current", ready=2_000_000_000), chunk("withdrawn")],
                               {"current"}, 60_000_000_000)
        self.assertEqual(1, result["requests"])
        self.assertEqual(2, result["p95"]["exactSeconds"])

    def test_future_completion_is_not_observed(self):
        result = MODULE.cohort([chunk("a", ready=70_000_000_000)], {"a"}, 60_000_000_000)
        self.assertEqual(1, result["p95"]["censored"])

    def test_empty_or_missing_native_demand_is_rejected(self):
        for wanted in (set(), {"missing"}):
            with self.subTest(wanted=wanted), self.assertRaises(ValueError):
                MODULE.cohort([chunk("a")], wanted, 60_000_000_000)

    def test_missing_demand_timestamp_is_not_fast(self):
        with self.assertRaises(ValueError):
            MODULE.cohort([{"key": "a", "firstNanos": {}}], {"a"}, 60_000_000_000)

    def test_known_control_cannot_turn_censored_candidate_into_point_value(self):
        candidate = {"lowerSeconds": 60, "upperSeconds": None, "exactSeconds": None}
        control = {"lowerSeconds": 10, "upperSeconds": 10, "exactSeconds": 10}
        result = MODULE.ratio_bounds(candidate, control)
        self.assertEqual(600, result["lowerPercent"])
        self.assertIsNone(result["upperPercent"])
        self.assertIsNone(result["exactPercent"])

    def test_unknown_control_keeps_ratio_interval(self):
        candidate = {"lowerSeconds": 5, "upperSeconds": 5, "exactSeconds": 5}
        control = {"lowerSeconds": 10, "upperSeconds": None, "exactSeconds": None}
        result = MODULE.ratio_bounds(candidate, control)
        self.assertEqual(0, result["lowerPercent"])
        self.assertEqual(50, result["upperPercent"])
        self.assertIsNone(result["exactPercent"])

    def test_known_points_produce_exact_ratio(self):
        candidate = {"lowerSeconds": 20, "upperSeconds": 20, "exactSeconds": 20}
        control = {"lowerSeconds": 10, "upperSeconds": 10, "exactSeconds": 10}
        self.assertEqual(200, MODULE.ratio_bounds(candidate, control)["exactPercent"])

    def test_zero_native_wait_does_not_manufacture_a_percentage(self):
        candidate = {"lowerSeconds": 20, "upperSeconds": 20, "exactSeconds": 20}
        control = {"lowerSeconds": 0, "upperSeconds": 0, "exactSeconds": 0}
        result = MODULE.ratio_bounds(candidate, control)
        self.assertIsNone(result["lowerPercent"])
        self.assertIsNone(result["exactPercent"])


if __name__ == "__main__":
    unittest.main()
