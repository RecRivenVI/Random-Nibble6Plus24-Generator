"""The causal checker must reject matching RNG state with wrong values/consumption."""

import importlib.util
from pathlib import Path
import unittest
import copy

path = Path(__file__).resolve().parents[3] / "scripts/compatibility-bop/Check-BopCausal.py"
spec = importlib.util.spec_from_file_location("bop_causal", path)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def event(kind, *args):
    return [0, 7, 1, "test", kind, *args]


class RandomChecks(unittest.TestCase):
    def test_return_and_continuation(self):
        self.assertEqual(checker.validate_random([
            event("draw", 31, 7), event("api:bounded", 3, 1),
            event("draw", 24, 0), event("api:floatBits", 0), event("end")]), 2)

    def test_wrong_return_rejected(self):
        with self.assertRaises(AssertionError):
            checker.validate_random([event("draw", 31, 7), event("api:bounded", 3, 2)])

    def test_extra_draw_rejected(self):
        with self.assertRaises(AssertionError):
            checker.validate_random([event("draw", 31, 7), event("draw", 31, 8), event("api:bounded", 3, 2)])

    def test_missing_api_rejected(self):
        with self.assertRaises(AssertionError):
            checker.validate_random([event("draw", 31, 7), event("end")])

    def test_overflow_rejection(self):
        self.assertEqual(checker.validate_random([
            event("draw", 31, 2147483647), event("draw", 31, 7), event("api:bounded", 3, 1)]), 1)

    def test_equal_final_hash_does_not_waive_different_read(self):
        original = {"name": "a", "summary": {"pid": 1, "save": "a", "inputHash": "i", "environment": {}, "terrainHash": "t", "hash": "h", "rejected": {}, "rawEntityNbt": [], "javaRuntime": "j", "availableProcessors": 4}, "trace": {"effectiveBiomeOrder": ["b"], "effectiveFeatureOrder": [["f"]]}, "events": [event("read:block", 1, 2, 3, "air")], "before": ["air"], "after": ["stone"], "snapshot": b"snapshot"}
        replay = copy.deepcopy(original)
        replay["name"] = "b"
        replay["summary"].update(pid=2, save="b")
        replay["events"][0][-1] = "stone"
        with self.assertRaises(AssertionError):
            checker.identical(original, replay)


if __name__ == "__main__":
    unittest.main()
