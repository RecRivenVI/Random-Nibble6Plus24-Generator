"""Paired performance must preserve unknown tails, zero denominators and resource identity."""

import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/performance-worldgen/summarize_reuse_pairs.py"
SPEC = importlib.util.spec_from_file_location("reuse_pairs", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReusePairTests(unittest.TestCase):
    def test_positive_from_zero_is_not_infinite_percent(self):
        self.assertEqual({"absoluteDelta": .5, "percent": None, "baselineZero": True}, MODULE.improvement(0, .5))

    def test_nonzero_ratio_is_ordinary_improvement(self):
        self.assertAlmostEqual(10, MODULE.improvement(1, 1.1)["percent"])

    def test_two_censored_tails_are_not_proven_equal(self):
        tail = {"lowerSeconds": 180, "upperSeconds": None}
        self.assertEqual("UNKNOWN_OVERLAPPING_CENSORED_INTERVALS", MODULE.latency_gate(tail, tail))

    def test_observed_better_tail_can_beat_censored_lower_bound(self):
        self.assertEqual("NON_REGRESSION_PROVEN", MODULE.latency_gate(
            {"lowerSeconds": 180, "upperSeconds": None}, {"lowerSeconds": 120, "upperSeconds": 120}))

    def test_censored_candidate_can_prove_regression(self):
        self.assertEqual("REGRESSION_PROVEN", MODULE.latency_gate(
            {"lowerSeconds": 120, "upperSeconds": 120}, {"lowerSeconds": 180, "upperSeconds": None}))

    def test_only_case_label_is_excluded_not_cpu_seed_or_window(self):
        first = {"case": "a", "cpu": 4, "seconds": 180, "masterSeed": 123}
        second = dict(first, case="b")
        self.assertEqual(MODULE.common_conditions(first), MODULE.common_conditions(second))
        for key, value in (("cpu", 2), ("seconds", 60), ("masterSeed", 17)):
            self.assertNotEqual(MODULE.common_conditions(first), MODULE.common_conditions(dict(second, **{key: value})))

    def test_repeated_directory_cannot_be_three_pairs(self):
        with self.assertRaises(ValueError):
            MODULE.summarize([Path("same")] * 3)


if __name__ == "__main__":
    unittest.main()
