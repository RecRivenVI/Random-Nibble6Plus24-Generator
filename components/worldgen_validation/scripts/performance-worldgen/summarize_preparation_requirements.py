"""Read-only obligation snapshots, not proof of a lower-bound generation algorithm."""

import argparse
from collections import Counter
import csv
import json
from pathlib import Path


def fields(detail):
    return dict(part.split("=", 1) for part in detail.split(";"))


def reduce(events):
    starts = [int(row["nanos"]) for row in events if row["phase"].startswith("WORKLOAD_START_")]
    ends = [int(row["nanos"]) for row in events if row["phase"] == "WORKLOAD_END"]
    if len(starts) != 1 or len(ends) != 1 or ends[0] <= starts[0]:
        raise ValueError("Missing bounded workload")
    begins, completed, plans = {}, {}, []
    for row in sorted(events, key=lambda entry: int(entry["nanos"])):
        if not starts[0] <= int(row["nanos"]) <= ends[0]:
            continue
        phase, owner = row["phase"], row["key"]
        if phase == "PLAN_SHAPE":
            plans.append(fields(row["detail"]))
        elif phase == "PREPARE_REQUIREMENT":
            if owner in begins:
                raise ValueError("Repeated preparation for owner: " + owner)
            begins[owner] = fields(row["detail"])
        elif phase == "PREPARED_REQUIREMENT":
            if owner not in begins or owner in completed:
                raise ValueError("Unpaired preparation completion: " + owner)
            completed[owner] = fields(row["detail"])
    if not begins or not plans:
        raise ValueError("Production requirement observation was not exercised")
    pre_features = {"minecraft:biomes", "minecraft:terrain"}
    observations = [{"owner": owner, "begin": begins[owner], "end": end}
                    for owner, end in completed.items()
                    if begins[owner]["obligation"] in pre_features and end["obligation"] in pre_features]
    return {"scope": "BOUNDED_OBLIGATION_SNAPSHOTS_NOT_MINIMUM_COST_OR_FUTURE_DEMAND",
            "windowSeconds": (ends[0] - starts[0]) / 1e9, "preparations": len(begins),
            "completed": len(completed), "inFlight": len(begins) - len(completed),
            "beginObligations": dict(Counter(value["obligation"] for value in begins.values())),
            "endObligations": dict(Counter(value["obligation"] for value in completed.values())),
            "planRequests": dict(Counter(value["status"] for value in plans)),
            "plans": plans, "completedWithPreFeaturesObligationAtBothSnapshots": observations}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    options = parser.parse_args()
    with (options.root / "jfr-analysis.json.events.tsv").open(encoding="utf-8") as stream:
        result = reduce(list(csv.DictReader(stream, delimiter="\t")))
    result["conditions"] = json.loads((options.root / "conditions.json").read_text(encoding="utf-8"))
    (options.root / "preparation-requirements.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key not in
                      ("plans", "conditions", "completedWithPreFeaturesObligationAtBothSnapshots")}))
    print("Pre-FEATURES obligation at both snapshots:",
          len(result["completedWithPreFeaturesObligationAtBothSnapshots"]))


if __name__ == "__main__":
    main()
