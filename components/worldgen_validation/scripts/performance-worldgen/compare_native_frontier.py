"""Compare bounded native frontiers without dropping unfinished or withdrawn demand."""

import argparse
import csv
import hashlib
import json
from pathlib import Path

from summarize_legacy_performance import latency_bounds


def cohort(chunks, wanted, deadline):
    selected = []
    for chunk in chunks:
        if chunk["key"] not in wanted:
            continue
        first = chunk["firstNanos"]
        if "VIEW_DEMAND" not in first or first["VIEW_DEMAND"] > deadline:
            raise ValueError("Demand has no observed request: " + chunk["key"])
        selected.append({"demandNanos": first["VIEW_DEMAND"], "phases": first})
    if len(selected) != len(wanted) or not selected:
        raise ValueError("Incomplete native demand cohort")
    return {"requests": len(selected),
            "p50": latency_bounds(selected, "READY_TO_SEND", deadline, .5),
            "p95": latency_bounds(selected, "READY_TO_SEND", deadline, .95)}


def ratio_bounds(value, control):
    if value is None or control is None:
        return None
    lower = (0 if control["upperSeconds"] is None else
             100 * value["lowerSeconds"] / control["upperSeconds"]
             if control["upperSeconds"] > 0 else None)
    upper = (100 * value["upperSeconds"] / control["lowerSeconds"]
             if value["upperSeconds"] is not None and control["lowerSeconds"] > 0 else None)
    exact = (100 * value["exactSeconds"] / control["exactSeconds"]
             if value["exactSeconds"] is not None and control["exactSeconds"] else None)
    return {"lowerPercent": lower, "upperPercent": upper, "exactPercent": exact}


def validate_window(seconds, requested):
    # The native deadline begins immediately before emitting the first JFR marker.
    # Keep the measured span unchanged; allow only bounded marker emission latency.
    if not requested - .25 <= seconds <= requested + 5:
        raise ValueError("Not the fixed bounded observation window")


def read_run(path, view=8, seconds=60):
    summary = json.loads((path / "isolation-summary.json").read_text(encoding="utf-8"))
    trace = json.loads((path / "checkpoint-timeline.json").read_text(encoding="utf-8"))
    conditions = json.loads((path / "conditions.json").read_text(encoding="utf-8"))
    if conditions.get("structureReuseAudit"):
        raise ValueError("Diagnostic structure history is not a performance control")
    if summary["conditions"] != conditions or trace["availableProcessors"] != conditions["cpu"]:
        raise ValueError("Summary or actual processor count differs from conditions")
    if view not in (8, 12, 16) or seconds not in (45, 60):
        raise ValueError("Unsupported controlled frontier conditions")
    expected = {"cpu": 4, "affinity": 3840, "priority": "BelowNormal", "view": view,
                "simulation": view, "seconds": seconds, "moves": 2, "moveSeconds": 15,
                "moveChunks": 2, "structures": "baseline", "cliFrontierNotPlayerSpawn": True}
    if any(conditions.get(key) != value for key, value in expected.items()):
        raise ValueError("Not the controlled four-CPU native frontier")
    with (path / "jfr-analysis.json.events.tsv").open(encoding="utf-8") as stream:
        events = sorted(csv.DictReader(stream, delimiter="\t"), key=lambda row: int(row["nanos"]))
    starts = [int(row["nanos"]) for row in events if row["phase"].startswith("WORKLOAD_START_")]
    ends = [int(row["nanos"]) for row in events if row["phase"] == "WORKLOAD_END"]
    if len(starts) != 1 or len(ends) != 1 or any(row["phase"] == "CPU_BUDGET_STOP" for row in events):
        raise ValueError("Incomplete or CPU-budget-stopped workload")
    start, end = starts[0], ends[0]
    seconds = (end - start) / 1e9
    validate_window(seconds, conditions["seconds"])
    wanted, ever = set(), set()
    for row in events:
        stamp = int(row["nanos"])
        if start <= stamp <= end and row["key"].startswith(conditions["dimension"] + ":"):
            if row["phase"] == "VIEW_DEMAND":
                wanted.add(row["key"])
                ever.add(row["key"])
            elif row["phase"] == "VIEW_WITHDRAWN":
                wanted.discard(row["key"])
    if summary["moves"] != conditions["moves"]:
        raise ValueError("Incomplete fixed exploration trajectory")
    chunks = [chunk for chunk in trace["chunks"] if chunk["dimension"] == conditions["dimension"]]
    if conditions["pipeline"] == "vanilla" and any(
            chunk.get("mosaic") or "PREPARE_BEGIN" in chunk["firstNanos"] for chunk in trace["chunks"]):
        raise ValueError("Vanilla control entered Mosaic")
    jfr = json.loads((path / "jfr-analysis.json").read_text(encoding="utf-8"))
    result = {key: summary[key] for key in (
        "canonical", "canonicalPerSecond", "FULL", "sendReady", "averageTargetLatency",
        "initialCenterTimelineSeconds", "processCoreEquivalents", "processCpuBudgetPercent",
        "serverThreadOneCorePercent", "maxLoggedServerLagMs", "cpuBucketsPercent",
        "peakAdmissionQueue", "peakExecutorQueue", "peakActiveSessions", "heapPeak", "gcPauseMs")}
    result.update(fullPerSecond=summary["FULL"] / seconds,
                  sendReadyPerSecond=summary["sendReady"] / seconds,
                  windowSeconds=seconds,
                  processId=trace["processId"], runIdentity=conditions["runIdentity"],
                  allDemand=cohort(chunks, ever, end),
                  finalNativeDemand=cohort(chunks, wanted, end),
                  withdrawnDemand=len(ever - wanted),
                  afterGcHeapUsed=jfr.get("afterGcHeapUsed"),
                  allocationWeightedBytes=jfr.get("allocationWeightedBytesTotal"),
                  monitorBlockedMsTop=jfr.get("monitorBlockedMsTop"),
                  provenance={name: hashlib.sha256((path / name).read_bytes()).hexdigest()
                              for name in ("conditions.json", "checkpoint-timeline.json",
                                           "jfr-analysis.json", "isolation-summary.json")})
    return conditions, result, ever, wanted


def compare(vanilla_path, b_path, view=8, seconds=60):
    native, vanilla, native_ever, native_wanted = read_run(vanilla_path, view, seconds)
    candidate, mosaic, b_ever, b_wanted = read_run(b_path, view, seconds)
    if native["pipeline"] != "vanilla" or candidate["pipeline"] != "b":
        raise ValueError("Not native Vanilla and B controls")
    resources = dict(native)
    resources.pop("pipeline")
    resources.pop("runIdentity")
    other = dict(candidate)
    other.pop("pipeline")
    other.pop("runIdentity")
    if resources != other or not resources.get("inputHash"):
        raise ValueError("Changed source, resource limits or trajectory between controls")
    if vanilla["processId"] == mosaic["processId"] or vanilla["runIdentity"] == mosaic["runIdentity"]:
        raise ValueError("Controls did not use independent processes and fresh worlds")
    if native_ever != b_ever or native_wanted != b_wanted:
        raise ValueError("Different native request sets; do not compare as the same frontier")
    ratios = {key: 100 * mosaic[key] / vanilla[key] if vanilla[key] else None
              for key in ("fullPerSecond", "sendReadyPerSecond")}
    ratios["finalNativeDemand"] = {key: ratio_bounds(mosaic["finalNativeDemand"][key],
                                                   vanilla["finalNativeDemand"][key])
                                   for key in ("p50", "p95")}
    return {"conditions": resources, "vanilla": vanilla, "b": mosaic,
            "versusVanillaPercent": ratios}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--view", type=int, choices=(8, 12, 16), default=8)
    parser.add_argument("--seconds", type=int, choices=(45, 60), default=60)
    options = parser.parse_args()
    result = {"status": "MEASURED_NOT_OPTIMIZATION_ACCEPTANCE",
              "scope": f"THREE_DIMENSION_SINGLE_PAIRS_4_CPU_{options.view}_{options.view}_{options.seconds}_SECONDS",
              "sendReady": "Server-side first readiness, not packet delivery or client rendering",
              "censoring": "All native requests retained; unfinished latency is an interval, not zero",
              "dimensions": {}}
    for dimension in ("overworld", "the_nether", "the_end"):
        suffix = "" if dimension == "overworld" else "-" + dimension
        if options.seconds != 60:
            suffix += f"-duration{options.seconds}"
        row = compare(options.root / (f"vanilla{suffix}-v{options.view}-s{options.view}"),
                      options.root / (f"b{suffix}-v{options.view}-s{options.view}"),
                      options.view, options.seconds)
        if row["conditions"]["dimension"] != "minecraft:" + dimension:
            raise ValueError("Evidence directory has the wrong dimension")
        result["dimensions"][dimension] = row
    rows = list(result["dimensions"].values())
    identities = [row[pipeline]["runIdentity"] for row in rows for pipeline in ("vanilla", "b")]
    pids = [row[pipeline]["processId"] for row in rows for pipeline in ("vanilla", "b")]
    if len(set(identities)) != 6 or len(set(pids)) != 6 or len({row["conditions"]["inputHash"] for row in rows}) != 1:
        raise ValueError("Reused process/world or changed source across dimensions")
    name = "vanilla-comparison.json" if options.view == 8 and options.seconds == 60 else f"vanilla-comparison-v{options.view}-duration{options.seconds}.json"
    (options.root / name).write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({dimension: row["versusVanillaPercent"]
                      for dimension, row in result["dimensions"].items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
