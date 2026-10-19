"""Pending requests must remain censored rather than disappearing from percentiles."""

import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/performance-worldgen/summarize_legacy_performance.py"
SPEC = importlib.util.spec_from_file_location("legacy_performance", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LegacyPerformanceTests(unittest.TestCase):
    def test_pending_request_is_not_zero_or_dropped(self):
        chunks = [{"demandNanos": 0, "phases": {}}]
        result = MODULE.latency_bounds(chunks, "SEND_READY", 60_000_000_000, .5)
        self.assertEqual(60, result["lowerSeconds"])
        self.assertIsNone(result["upperSeconds"])
        self.assertIsNone(result["exactSeconds"])
        self.assertEqual(1, result["censored"])

    def test_dependencies_already_ready_before_demand_have_zero_wait(self):
        chunks = [{"demandNanos": 2_000_000_000, "phases": {"SEND_READY": 1_000_000_000}}]
        self.assertEqual(0, MODULE.latency_bounds(chunks, "SEND_READY", 60_000_000_000, .5)["exactSeconds"])

    def test_mixed_completion_keeps_tail_unknown(self):
        chunks = [{"demandNanos": 0, "phases": {"SEND_READY": 2_000_000_000}},
                  {"demandNanos": 30_000_000_000, "phases": {}}]
        self.assertEqual(2, MODULE.latency_bounds(chunks, "SEND_READY", 60_000_000_000, .5)["exactSeconds"])
        self.assertIsNone(MODULE.latency_bounds(chunks, "SEND_READY", 60_000_000_000, .95)["exactSeconds"])

    def test_active_and_queued_spans_have_separate_peaks(self):
        chunks = [{"work": [{"queuedNanos": 0, "startNanos": 10, "endNanos": 30},
                            {"queuedNanos": 0, "startNanos": 30, "endNanos": 40},
                            {"queuedNanos": 0}]}]
        self.assertEqual(1, MODULE.peak_work(chunks, 100))
        self.assertEqual(3, MODULE.peak_work(chunks, 100, True))

    def test_future_completion_outside_window_stays_censored(self):
        chunk = {"demandNanos": 0, "phases": {"SEND_READY": 70_000_000_000}}
        result = MODULE.latency_bounds([chunk], "SEND_READY", 60_000_000_000, .5)
        self.assertEqual(1, result["censored"])

    def test_bounded_longer_observation_does_not_change_request_time(self):
        chunk = {"demandNanos": 30_000_000_000, "phases": {"SEND_READY": 130_000_000_000}}
        short = MODULE.latency_bounds([chunk], "SEND_READY", 60_000_000_000, .5)
        longer = MODULE.latency_bounds([chunk], "SEND_READY", 180_000_000_000, .5)
        self.assertEqual(30, short["lowerSeconds"])
        self.assertIsNone(short["exactSeconds"])
        self.assertEqual(100, longer["exactSeconds"])
        self.assertEqual(0, longer["censored"])

    def test_obsolete_requests_are_separate_from_final_native_view(self):
        current = {"demandNanos": 0, "lastDemandNanos": 30}
        obsolete = {"demandNanos": 0, "lastDemandNanos": 15}
        self.assertEqual(([current], 1), MODULE.current_viewport([current, obsolete], 1))
        with self.assertRaises(ValueError):
            MODULE.current_viewport([current, obsolete], 2)

    def test_empty_demand_is_not_a_successful_fast_world(self):
        with self.assertRaises(ValueError):
            MODULE.current_viewport([], 0)

    def test_contaminated_initial_schema_is_never_a_current_measurement(self):
        with self.assertRaises(ValueError):
            MODULE.reduce({"status": "MEASURED", "schema": 1}, {})


if __name__ == "__main__":
    unittest.main()
