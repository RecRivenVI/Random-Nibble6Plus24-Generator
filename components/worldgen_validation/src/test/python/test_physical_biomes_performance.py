"""Synthetic reducer tests only; never substitute for a game/Oracle performance run."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/performance-worldgen/compare_physical_biomes.py"
SPEC = importlib.util.spec_from_file_location("physical_comparison", SCRIPT)
REDUCER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REDUCER)


class PhysicalBiomeComparisonTests(unittest.TestCase):
    def test_origin_reuse_requires_real_candidate_and_reference_counts(self):
        with tempfile.TemporaryDirectory(prefix="origin-reuse-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            for folder, reference in ((old, True), (new, False)):
                self.fixture(folder, reference)
                def change(data):
                    data["conditions"]["referenceRelatedOrigins"] = data["conditions"].pop("referencePhysicalBiomes")
                    data["conditions"]["referencePhysicalBiomes"] = False
                self.mutate(folder, "isolation-summary.json", change)
                key = "production.origins." + ("recomputed" if reference else "reused")
                self.mutate(folder, "checkpoint-timeline.json", lambda data: data.update(aggregates={key: 12}))
            result = REDUCER.compare(old, new, "origins")
            self.assertEqual(12, result["candidate"]["originCounts"]["production.origins.reused"])
            self.mutate(new, "checkpoint-timeline.json", lambda data: data.update(aggregates={}))
            with self.assertRaisesRegex(ValueError, "origin reuse"):
                REDUCER.compare(old, new, "origins")

    def test_load_phase_counts_keep_missing_observations_visible(self):
        trace = {"chunks": [{"firstNanos": row} for row in (
            {"PREPARE_BEGIN": 1, "STAGE_EMPTY_END": 2},
            {"PREPARE_BEGIN": 3, "STAGE_EMPTY_END": 2},
            {"PREPARE_BEGIN": 1}, {})]}
        self.assertEqual({"beforeEmptyEnd": 1, "afterEmptyEnd": 1, "missingEmptyEnd": 1},
                         REDUCER.load_preparations(trace))

    def test_load_control_must_actually_move_preparation_out_of_empty(self):
        with tempfile.TemporaryDirectory(prefix="load-handoff-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            for folder, reference in ((old, True), (new, False)):
                self.fixture(folder, reference)
                def change(data):
                    data["conditions"]["referenceLoadPreparation"] = data["conditions"].pop("referencePhysicalBiomes")
                    data["conditions"]["referencePhysicalBiomes"] = False
                self.mutate(folder, "isolation-summary.json", change)
                self.mutate(folder, "checkpoint-timeline.json", lambda data:
                            [row["firstNanos"].update(PREPARE_BEGIN=1 if reference else 3,
                                                     STAGE_EMPTY_END=2) for row in data["chunks"]])
            result = REDUCER.compare(old, new, "load")
            self.assertEqual(3, result["reference"]["loadPreparation"]["beforeEmptyEnd"])
            self.assertEqual(3, result["candidate"]["loadPreparation"]["afterEmptyEnd"])
            self.mutate(new, "checkpoint-timeline.json", lambda data:
                        data["chunks"][0]["firstNanos"].update(PREPARE_BEGIN=1))
            with self.assertRaisesRegex(ValueError, "handoff"):
                REDUCER.compare(old, new, "load")

    def fixture(self, root, reference):
        root.mkdir()
        conditions = dict(cpu=4, view=8, simulation=8, seconds=60, structures="baseline", pipeline="b",
                          dimension="minecraft:overworld", inputHash="unit-fixture-only",
                          referencePhysicalBiomes=reference, runIdentity="old" if reference else "new")
        summary = dict(conditions=conditions, canonical=3, canonicalPerSecond=.05, FULL=2,
                       sendReady=2, heapPeak=100)
        ready = [10, 50, None] if reference else [8, 40, 52]
        chunks = []
        for index, second in enumerate(ready):
            first = {"VIEW_DEMAND": 1_000_000_000}
            if second is not None:
                first["READY_TO_SEND"] = second * 1_000_000_000
            chunks.append(dict(key=f"minecraft:overworld:{index},0", dimension="minecraft:overworld", firstNanos=first))
        trace = dict(chunks=chunks, processId=1 if reference else 2,
                     physicalBiomes=dict(builds=0 if reference else 3))
        (root / "isolation-summary.json").write_text(json.dumps(summary), encoding="utf-8")
        (root / "checkpoint-timeline.json").write_text(json.dumps(trace), encoding="utf-8")
        (root / "jfr-analysis.json.events.tsv").write_text(
            "nanos\tphase\n0\tWORKLOAD_START_frontier\n60000000000\tWORKLOAD_END\n", encoding="utf-8")

    def mutate(self, root, file, change):
        path = root / file
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data), encoding="utf-8")

    def test_unfinished_requests_remain_and_common_keys_are_paired(self):
        with tempfile.TemporaryDirectory(prefix="physical-biomes-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            self.fixture(old, True)
            self.fixture(new, False)
            result = REDUCER.compare(old, new)
            self.assertEqual(result["demandCount"], 3)
            self.assertEqual(result["commonReadyCount"], 2)
            self.assertEqual(result["reference"]["unfinishedDemand"], 1)
            self.assertEqual(result["candidate"]["unfinishedDemand"], 0)
            self.assertEqual(result["reference"]["restrictedAllDemandLatency"]["mean"], 39)
            self.assertEqual(result["candidate"]["commonReadyLatency"]["p95"], 39)

    def test_late_completion_is_censored_not_hidden(self):
        with tempfile.TemporaryDirectory(prefix="physical-biomes-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            self.fixture(old, True)
            self.fixture(new, False)
            self.mutate(new, "checkpoint-timeline.json", lambda data:
                        data["chunks"][2]["firstNanos"].update(READY_TO_SEND=65_000_000_000))
            result = REDUCER.compare(old, new)
            self.assertEqual(result["candidate"]["unfinishedDemand"], 1)
            self.assertEqual(result["candidate"]["restrictedAllDemandLatency"]["p95"], 59)

    def test_unmatched_demand_is_rejected(self):
        self.reject("checkpoint-timeline.json", lambda data: data["chunks"].pop())

    def test_changed_source_is_rejected(self):
        self.reject("isolation-summary.json", lambda data: data["conditions"].update(inputHash="changed"))

    def test_disabled_work_is_rejected(self):
        self.reject("checkpoint-timeline.json", lambda data: data["physicalBiomes"].update(builds=0))

    def test_terrain_uses_its_own_control_and_actual_stage_counter(self):
        with tempfile.TemporaryDirectory(prefix="physical-terrain-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            for folder, reference in ((old, True), (new, False)):
                self.fixture(folder, reference)
                def change_conditions(data):
                    data["conditions"]["referencePhysicalTerrain"] = data["conditions"].pop("referencePhysicalBiomes")
                    data["conditions"]["referencePhysicalBiomes"] = False
                self.mutate(folder, "isolation-summary.json", change_conditions)
                self.mutate(folder, "checkpoint-timeline.json", lambda data:
                            data.update(physicalTerrain=data.pop("physicalBiomes")))
            result = REDUCER.compare(old, new, "terrain")
            self.assertEqual(result["stage"], "terrain")
            self.assertEqual(result["candidate"]["partialPreparation"]["builds"], 3)
            with self.assertRaises(ValueError):
                REDUCER.compare(old, new)

    def reject(self, file, change):
        with tempfile.TemporaryDirectory(prefix="physical-biomes-unit-") as directory:
            old, new = Path(directory) / "old", Path(directory) / "new"
            self.fixture(old, True)
            self.fixture(new, False)
            self.mutate(new, file, change)
            with self.assertRaises(ValueError):
                REDUCER.compare(old, new)


if __name__ == "__main__":
    unittest.main()
