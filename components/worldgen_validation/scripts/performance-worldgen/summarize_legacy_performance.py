"""Reduce the native legacy frontier without treating censored requests as fast results."""

import json
import hashlib
import math
from pathlib import Path
import statistics
import sys


def latency_bounds(chunks, phase, deadline, quantile):
    intervals = []
    completed = 0
    for chunk in chunks:
        if "demandNanos" not in chunk:
            continue
        demand = chunk["demandNanos"]
        stamp = chunk["phases"].get(phase)
        if stamp is not None and stamp <= deadline:
            value = max(0, stamp - demand) / 1e9
            intervals.append((value, value))
            completed += 1
        else:
            intervals.append((max(0, deadline - demand) / 1e9, math.inf))
    if not intervals:
        return None
    rank = max(0, math.ceil(quantile * len(intervals)) - 1)
    lower = sorted(a for a, _ in intervals)[rank]
    upper = sorted(b for _, b in intervals)[rank]
    return {"requests": len(intervals), "completed": completed,
            "censored": len(intervals) - completed, "lowerSeconds": lower,
            "upperSeconds": None if math.isinf(upper) else upper,
            "exactSeconds": lower if lower == upper else None}


def peak_work(chunks, deadline, queued=False):
    events = []
    for chunk in chunks:
        for work in chunk["work"]:
            begin = work.get("queuedNanos" if queued else "startNanos")
            end = work.get("startNanos" if queued else "endNanos", deadline + 1)
            if begin is not None and begin <= deadline:
                events.extend(((begin, 1), (min(end, deadline + 1), -1)))
    active = peak = 0
    for _, delta in sorted(events):
        active += delta
        peak = max(peak, active)
    return peak


def current_viewport(chunks, expected):
    demanded = [c for c in chunks if "demandNanos" in c]
    if not demanded:
        raise ValueError("No native viewport demand")
    final_demand = max(c["lastDemandNanos"] for c in demanded)
    current = [c for c in demanded if c["lastDemandNanos"] == final_demand]
    if len(current) != expected:
        raise ValueError("Final viewport cohort differs from native ChunkTrackingView")
    return current, len(demanded) - len(current)


def reduce(summary, conditions):
    if summary["status"] != "MEASURED" or summary["schema"] != 2 or not summary["coldAfterToken"] or summary["tokenRespawnSkips"] != 1:
        raise ValueError("Incomplete legacy frontier")
    for key in ("target", "dimension", "view", "simulation", "inputHash"):
        if summary[key] != conditions[key]:
            raise ValueError("Different conditions: " + key)
    seconds = summary["seconds"]
    if seconds < conditions["seconds"] or summary["cpus"] != conditions["cpu"]:
        raise ValueError("Truncated window or wrong CPU budget")
    chunks = summary["chunks"]
    deadline = round(seconds * 1e9)
    if conditions["pipeline"] == "vanilla" and any(chunk["work"] or chunk["canonicalInvocations"] for chunk in chunks):
        raise ValueError("Vanilla entered Mosaic preparation")
    if any(stamp > deadline + 1 for chunk in chunks for stamp in chunk["phases"].values()):
        raise ValueError("Post-window completion was counted")
    count = lambda phase: sum(phase in chunk["phases"] for chunk in chunks)
    result = {"schema": 1, "scope": "NATIVE_TICKET_FRONTIER_NOT_PLAYER_OR_PACKET", "seconds": seconds,
              "requestedViewportTargets": sum("demandNanos" in c for c in chunks),
              "observedPhysicalTargets": len(chunks),
              "canonicalCompleted": count("B_END"), "canonicalPerSecond": count("B_END") / seconds,
              "fullCompleted": count("STATUS_FULL"), "fullPerSecond": count("STATUS_FULL") / seconds,
              "sendReadyCompleted": count("SEND_READY"), "sendReadyPerSecond": count("SEND_READY") / seconds,
              "peakActiveWorkers": peak_work(chunks, deadline), "peakQueuedWorkers": peak_work(chunks, deadline, True),
              "processCpuBudgetFraction": summary["processCpuBudgetFraction"],
              "serverCpuFraction": summary["serverCpuFraction"],
              "gcMillis": summary["gcMillis"], "maxObservedTickGapMillis": summary["maxObservedTickGapMillis"]}
    for phase in ("STATUS_FULL", "SEND_READY"):
        result[phase] = {"p50": latency_bounds(chunks, phase, deadline, .5),
                         "p95": latency_bounds(chunks, phase, deadline, .95)}
    for metric in ("contexts", "narrowContexts", "structureCalls", "evaluatedStructureSources", "structureCumulativeNanos", "ringPreparations", "ringBiomeSearches"):
        result[metric] = summary[metric]
    for metric in ("sourceReuseHits", "sourceReuseEvaluations", "sourceStateMismatches", "sourceStateReplays"):
        if metric in summary:
            result[metric] = summary[metric]
    waits = [(w["startNanos"] - w["queuedNanos"]) / 1e9 for c in chunks for w in c["work"] if "startNanos" in w]
    active = [(w["endNanos"] - w["startNanos"]) / 1e9 for c in chunks for w in c["work"] if "startNanos" in w and "endNanos" in w]
    result["workerQueueObservedMedianSeconds"] = statistics.median(waits) if waits else None
    result["workerCompletedActiveMedianSeconds"] = statistics.median(active) if active else None
    focus = next((c for c in chunks if c["x"] == conditions["x"] and c["z"] == conditions["z"]), None)
    if focus is None:
        raise ValueError("Missing focus request")
    result["focusTimelineSeconds"] = {phase: stamp / 1e9 for phase, stamp in focus["phases"].items()}
    result["focusReadyWithinWindow"] = "SEND_READY" in focus["phases"]
    result["focusPendingLowerBoundSeconds"] = None if result["focusReadyWithinWindow"] else (deadline - focus["demandNanos"]) / 1e9
    result["finalSample"] = summary["samples"][-1]
    current, obsolete = current_viewport(chunks, result["finalSample"]["viewportChunks"])
    result["obsoleteViewportTargets"] = obsolete
    result["currentViewport"] = {"requests": len(current),
                                 "sendReady": sum("SEND_READY" in c["phases"] for c in current),
                                 "p50": latency_bounds(current, "SEND_READY", deadline, .5),
                                 "p95": latency_bounds(current, "SEND_READY", deadline, .95)}
    samples = [s for s in summary["samples"] if s["seconds"] <= 55]
    result["sampleAtOrBefore55Seconds"] = samples[-1] if samples else None
    return result


def main():
    if sys.argv[1] in ("--compare", "--compare-reuse", "--compare-rings", "--compare-baseline"):
        pipelines = {"--compare": ("vanilla", "b_eager", "b_ring", "b_narrow"),
                     "--compare-reuse": ("vanilla", "b_narrow", "b_reuse"),
                     "--compare-rings": ("vanilla", "b_eager", "b_ring"),
                     "--compare-baseline": ("vanilla", "b_eager")}[sys.argv[1]]
        compare(Path(sys.argv[2]), pipelines)
        return
    root = Path(sys.argv[1])
    summary = json.loads((root / "summary.json").read_text(encoding="utf-8"))
    conditions = json.loads((root / "conditions.json").read_text(encoding="utf-8"))
    result = reduce(summary, conditions)
    (root / "timeline-summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("canonicalPerSecond", "fullPerSecond", "sendReadyPerSecond", "focusReadyWithinWindow")}))


def compare(root, pipelines=("vanilla", "b_eager", "b_ring", "b_narrow")):
    groups = {}
    controls = None
    pids = set()
    runs = set()
    fields = ("target", "dimension", "view", "simulation", "seconds", "inputHash", "masterSeed", "x", "z", "moves", "moveChunks", "moveSeconds", "cpu", "affinity", "priority", "case")
    for pipeline in pipelines:
        path = root / pipeline
        raw = json.loads((path / "summary.json").read_text(encoding="utf-8"))
        conditions = json.loads((path / "conditions.json").read_text(encoding="utf-8"))
        current = {key: conditions[key] for key in fields}
        if controls is not None and current != controls:
            raise ValueError("Incomparable resource/input conditions")
        controls = current
        if conditions["pipeline"] != pipeline or raw["pid"] in pids or conditions["runIdentity"] in runs:
            raise ValueError("Wrong pipeline or reused process/run")
        pids.add(raw["pid"])
        runs.add(conditions["runIdentity"])
        row = reduce(raw, conditions)
        jfr = json.loads((path / "jfr-analysis.json").read_text(encoding="utf-8"))
        total = jfr["cpuSamples"]
        row["cpuSamplePercent"] = {key: value * 100 / total for key, value in jfr["exclusiveCpuBuckets"].items()} if total else None
        row["monitorCumulativeMillis"] = jfr["monitorBlockedMsTop"]
        row["provenance"] = {name: hashlib.sha256((path / name).read_bytes()).hexdigest()
                             for name in ("conditions.json", "summary.json", "jfr-analysis.json")}
        groups[pipeline] = row
    vanilla = groups["vanilla"]
    for row in groups.values():
        ratios = {}
        for key in ("fullPerSecond", "sendReadyPerSecond"):
            ratios[key] = row[key] / vanilla[key] * 100 if vanilla[key] else None
        for key in ("p50", "p95"):
            control = vanilla["currentViewport"][key]["exactSeconds"]
            value = row["currentViewport"][key]
            ratios[key] = {field: value[field] / control * 100 if control and value[field] is not None else None
                           for field in ("exactSeconds", "lowerSeconds", "upperSeconds")}
        row["versusVanillaPercent"] = ratios
    result = {"scope": "SINGLE_SCREEN_NOT_OPTIMIZATION_ACCEPTANCE", "conditions": controls, "groups": groups}
    (root / "comparison.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({name: {"fullPerSecond": row["fullPerSecond"], "sendReadyPerSecond": row["sendReadyPerSecond"],
                            "currentViewportReady": row["currentViewport"]["sendReady"], "versusVanillaPercent": row["versusVanillaPercent"]}
                      for name, row in groups.items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
