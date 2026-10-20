"""Physical BIOMES lock control; MEASURED is not correctness or retention approval."""

import argparse
import csv
import json
from pathlib import Path

from compare_native_frontier import read_run, ratio_bounds


def compare(reference, candidate):
    left, old, old_ever, old_wanted = read_run(reference)
    right, new, new_ever, new_wanted = read_run(candidate)
    a, b = dict(left), dict(right)
    if a.pop("referenceBiomeLocks", False) is not True or b.pop("referenceBiomeLocks", False):
        raise ValueError("Wrong physical BIOMES lock controls")
    a.pop("runIdentity")
    b.pop("runIdentity")
    if a != b or a["pipeline"] != "b" or a["target"] != "26.3-fabric":
        raise ValueError("Changed source, resources or pipeline between lock controls")
    if old["processId"] == new["processId"] or old["runIdentity"] == new["runIdentity"]:
        raise ValueError("Lock controls reused a process or world")
    if old_wanted != new_wanted or old_ever != new_ever:
        raise ValueError("Lock controls have different native demand")
    hits = {}
    for name, folder, expected in (("reference", reference, "narrow=false"),
                                   ("candidate", candidate, "narrow=true")):
        with (folder / "jfr-analysis.json.events.tsv").open(encoding="utf-8") as stream:
            rows = [row for row in csv.DictReader(stream, delimiter="\t")
                    if row["phase"] == "PHYSICAL_BIOMES_STRUCTURE_READY"]
        if not rows or any(row["detail"] != expected for row in rows):
            raise ValueError("The actual physical BIOMES lock path was not exercised")
        hits[name] = len(rows)
    ratios = {key: 100 * new[key] / old[key] if old[key] else None
              for key in ("canonicalPerSecond", "fullPerSecond", "sendReadyPerSecond")}
    ratios["finalNativeDemand"] = {
        key: ratio_bounds(new["finalNativeDemand"][key], old["finalNativeDemand"][key])
        for key in ("p50", "p95")}
    return {"status": "MEASURED_NOT_RETENTION_APPROVAL", "conditions": a,
            "actualLockPathHits": hits, "reference": old, "candidate": new,
            "versusReferencePercent": ratios}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    options = parser.parse_args()
    result = compare(options.root / "b-biome-lock-reference-v8-s8", options.root / "b-v8-s8")
    (options.root / "biome-lock-comparison.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["versusReferencePercent"]))


if __name__ == "__main__":
    main()
