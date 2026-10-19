"""Observe repeated native source evaluations, not a predicted cache speedup."""

import argparse
import hashlib
import json
from pathlib import Path


def reduce(conditions, trace):
    target = conditions.get("target")
    if target not in ("26.3-fabric", "26.3-neoforge"):
        raise ValueError("Source history has not been adapted to this native target")
    expected = {"target": target, "pipeline": "b", "structures": "baseline",
                "structureReuseAudit": True, "cpu": 4, "affinity": 3840, "priority": "BelowNormal"}
    if any(conditions.get(key) != value for key, value in expected.items()):
        raise ValueError("Not the explicit four-CPU native B source audit")
    if not trace.get("final") or trace.get("availableProcessors") != 4:
        raise ValueError("Final four-CPU lifecycle evidence is missing")
    totals = {key.removeprefix("reuseAudit."): value for key, value in trace["aggregates"].items()
              if key.startswith("reuseAudit.")}
    opened, closed = totals.get("openScopes", 0), totals.get("closedScopes", 0)
    if opened <= 0 or opened != closed or totals.get("evaluationCountMismatch") != 0 or totals.get("retainedAfterClose") != 0:
        raise ValueError("Audit/source counts or lifecycle cleanup differ")
    evaluations, nanos = totals.get("evaluations", 0), totals.get("nativeNanos", 0)
    if evaluations <= 0 or nanos <= 0 or not closed <= totals.get("maximumStates", 0) <= 16 * closed:
        raise ValueError("No bounded native input history was observed")
    repeats = [totals.get("repeat" + str(size), 0) for size in (2, 4, 16)]
    times = [totals.get("nanos" + str(size), 0) for size in (2, 4, 16)]
    if not 0 <= repeats[0] <= repeats[1] <= repeats[2] <= evaluations or not 0 <= times[0] <= times[1] <= times[2] <= nanos:
        raise ValueError("Impossible nested token-history opportunity")
    return {"status": "DIAGNOSTIC_OBSERVED_NOT_OPTIMIZATION_ACCEPTANCE", "conditions": conditions,
            "scope": "COMPLETED_NATIVE_SUPPLIER_CALLS_OVER_CLOSED_SCOPES_INCLUDING_SHUTDOWN_TAIL",
            "doesNotReplaceNativeWork": True, "performanceComparable": False, "counters": totals,
            "opportunity": {str(size): {"repeatedEvaluationsPercent": 100 * count / evaluations,
                                       "repeatedSupplierWallPercent": 100 * time / nanos}
                            for size, count, time in zip((2, 4, 16), repeats, times)},
            "limitations": "Token history, not cached carriers; native replay/result equivalence and end-to-end benefit remain unproved"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    path = parser.parse_args().directory
    conditions = json.loads((path / "conditions.json").read_text(encoding="utf-8"))
    trace = json.loads((path / "timeline.json").read_text(encoding="utf-8"))
    result = reduce(conditions, trace)
    result["provenance"] = {name: hashlib.sha256((path / name).read_bytes()).hexdigest()
                            for name in ("conditions.json", "timeline.json")}
    (path / "source-state-audit.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "opportunity": result["opportunity"]}))


if __name__ == "__main__":
    main()
