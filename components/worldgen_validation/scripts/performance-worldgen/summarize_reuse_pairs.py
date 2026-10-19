"""Three paired end-to-end observations, without inventing uncensored tails or zero ratios."""

import hashlib
import json
from pathlib import Path
import statistics
import sys


def improvement(before, after):
    return {"absoluteDelta": after - before,
            "percent": (after / before - 1) * 100 if before > 0 else None,
            "baselineZero": before == 0}


def latency_gate(baseline, candidate):
    if candidate["upperSeconds"] is not None and candidate["upperSeconds"] <= baseline["lowerSeconds"]:
        return "NON_REGRESSION_PROVEN"
    if baseline["upperSeconds"] is not None and candidate["lowerSeconds"] > baseline["upperSeconds"]:
        return "REGRESSION_PROVEN"
    return "UNKNOWN_OVERLAPPING_CENSORED_INTERVALS"


def common_conditions(conditions):
    return {key: value for key, value in conditions.items() if key != "case"}


def summarize(roots):
    if len(roots) != 3 or len({root.resolve() for root in roots}) != 3:
        raise ValueError("Exactly three independent pairs required")
    pairs = []
    conditions = None
    pids, run_ids = set(), set()
    for root in roots:
        comparison = json.loads((root / "comparison.json").read_text(encoding="utf-8"))
        current = common_conditions(comparison["conditions"])
        if conditions is not None and conditions != current:
            raise ValueError("Different paired conditions")
        conditions = current
        memory = {}
        for pipeline in ("vanilla", "b_narrow", "b_reuse"):
            group = comparison["groups"][pipeline]
            for name, expected_hash in group["provenance"].items():
                if hashlib.sha256((root / pipeline / name).read_bytes()).hexdigest() != expected_hash:
                    raise ValueError("Changed measurement body")
            raw = json.loads((root / pipeline / "summary.json").read_text(encoding="utf-8"))
            actual = json.loads((root / pipeline / "conditions.json").read_text(encoding="utf-8"))
            if raw["pid"] in pids or actual["runIdentity"] in run_ids:
                raise ValueError("Reused performance process or world identity")
            pids.add(raw["pid"])
            run_ids.add(actual["runIdentity"])
            profile = json.loads((root / pipeline / "jfr-analysis.json").read_text(encoding="utf-8"))
            memory[pipeline] = {"afterGcHeap": profile["afterGcHeapUsed"],
                                "weightedAllocationBytes": profile["allocationWeightedBytesTotal"],
                                "fullCompleted": group["fullCompleted"]}
        baseline, candidate = comparison["groups"]["b_narrow"], comparison["groups"]["b_reuse"]
        pairs.append({"path": str(root), "groups": comparison["groups"], "memory": memory,
                      "p50Gate": latency_gate(baseline["currentViewport"]["p50"], candidate["currentViewport"]["p50"]),
                      "p95Gate": latency_gate(baseline["currentViewport"]["p95"], candidate["currentViewport"]["p95"])})
    medians = {}
    for pipeline in ("vanilla", "b_narrow", "b_reuse"):
        medians[pipeline] = {metric: statistics.median(pair["groups"][pipeline][metric] for pair in pairs)
                             for metric in ("canonicalPerSecond", "fullPerSecond", "sendReadyPerSecond")}
        medians[pipeline]["versusVanillaPercent"] = {
            metric: statistics.median(pair["groups"][pipeline]["versusVanillaPercent"][metric] for pair in pairs)
            for metric in ("fullPerSecond", "sendReadyPerSecond")}
    gains = {metric: improvement(medians["b_narrow"][metric], medians["b_reuse"][metric])
             for metric in ("canonicalPerSecond", "fullPerSecond", "sendReadyPerSecond")}
    return {"schema": 1, "scope": "THREE_NATIVE_TICKET_PAIRS_NOT_PLAYER_OR_PACKET", "conditions": conditions,
            "medians": medians, "improvement": gains, "pairs": pairs,
            "p95ProvenNonRegressionAllPairs": all(pair["p95Gate"] == "NON_REGRESSION_PROVEN" for pair in pairs),
            "memoryGate": "UNASSESSED_UNMATCHED_PHYSICAL_COMPLETION_COUNTS",
            "retention": "NOT_AUTOMATICALLY_ACCEPTED_CORRECTNESS_MEMORY_AND_TAILS_REQUIRE_REVIEW"}


def main():
    roots = [Path(value) for value in sys.argv[1:]]
    result = summarize(roots)
    parents = {root.resolve().parent for root in roots}
    if len(parents) != 1:
        raise ValueError("Pairs must share their fixed validation result parent")
    output = next(iter(parents)) / "reuse-paired-summary.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "medians": result["medians"],
                      "p95Proven": result["p95ProvenNonRegressionAllPairs"], "retention": result["retention"]}))


if __name__ == "__main__":
    main()
