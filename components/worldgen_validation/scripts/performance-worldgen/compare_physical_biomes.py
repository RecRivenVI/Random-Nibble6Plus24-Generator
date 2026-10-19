"""Paired physical-frontier evidence, including unfinished requests rather than hiding them."""
import argparse
import csv
import json
from pathlib import Path
import statistics


def stats(values):
    values = sorted(values)
    if not values:
        raise ValueError("No comparable physical requests")
    return {"count": len(values), "mean": statistics.mean(values),
            "median": statistics.median(values), "p95": values[min(len(values) - 1, int(.95 * len(values)))]}


def frontier(folder):
    folder = Path(folder)
    summary = json.loads((folder / "isolation-summary.json").read_text(encoding="utf-8"))
    trace = json.loads((folder / "checkpoint-timeline.json").read_text(encoding="utf-8"))
    analysis = folder / "jfr-analysis.json"
    memory = json.loads(analysis.read_text(encoding="utf-8")) if analysis.exists() else {}
    summary["afterGcHeapUsed"] = memory.get("afterGcHeapUsed")
    summary["allocationWeightedBytes"] = memory.get("allocationWeightedBytesTotal")
    with (folder / "jfr-analysis.json.events.tsv").open(encoding="utf-8") as stream:
        events = list(csv.DictReader(stream, delimiter="\t"))
    start = int(next(row["nanos"] for row in events if row["phase"].startswith("WORKLOAD_START_")))
    end = int(next(row["nanos"] for row in events if row["phase"] == "WORKLOAD_END"))
    if end <= start:
        raise ValueError("Invalid observation window")
    demands = {}
    for chunk in trace["chunks"]:
        if chunk["dimension"] != summary["conditions"]["dimension"]:
            continue
        first = chunk["firstNanos"]
        request = first.get("VIEW_DEMAND")
        if request is not None and start <= request <= end:
            ready = first.get("READY_TO_SEND")
            completed = ready is not None and ready <= end
            demands[chunk["key"]] = {"complete": completed,
                "latency": max(0, (ready - request) / 1e9) if completed else None,
                "restricted": max(0, ((min(ready, end) if ready is not None else end) - request) / 1e9)}
    return summary, trace, demands


def compare(left_folder, right_folder):
    left, left_trace, old = frontier(left_folder)
    right, right_trace, new = frontier(right_folder)
    a, b = dict(left["conditions"]), dict(right["conditions"])
    identities = [a.pop("runIdentity", None), b.pop("runIdentity", None)]
    if a.pop("referencePhysicalBiomes", None) is not True or b.pop("referencePhysicalBiomes", None) is not False:
        raise ValueError("Wrong physical BIOMES A/B controls")
    if a != b or not a.get("inputHash"):
        raise ValueError("Changed inputs/resources/source within physical A/B")
    if (a["cpu"], a["view"], a["simulation"], a["seconds"], a["structures"], a["pipeline"]) != (4, 8, 8, 60, "baseline", "b"):
        raise ValueError("Not the controlled production B frontier")
    if not old or old.keys() != new.keys():
        raise ValueError("Different physical demand sets")
    if left_trace.get("physicalBiomes", {}).get("builds") != 0 or right_trace.get("physicalBiomes", {}).get("builds", 0) <= 0:
        raise ValueError("Partial BIOMES candidate/control was not exercised")
    common = [key for key in old if old[key]["complete"] and new[key]["complete"]]
    rows = {}
    for name, summary, requests in (("reference", left, old), ("candidate", right, new)):
        rows[name] = {"canonical": summary["canonical"], "canonicalPerSecond": summary["canonicalPerSecond"],
            "FULL": summary["FULL"], "sendReady": summary["sendReady"], "heapPeak": summary["heapPeak"],
            "afterGcHeapUsed": summary["afterGcHeapUsed"],
            "allocationWeightedBytes": summary["allocationWeightedBytes"],
            "allocationWeightedBytesPerFull": summary["allocationWeightedBytes"] / summary["FULL"]
                if summary["allocationWeightedBytes"] is not None and summary["FULL"] else None,
            "unfinishedDemand": sum(not entry["complete"] for entry in requests.values()),
            "commonReadyLatency": stats([requests[key]["latency"] for key in common]),
            "restrictedAllDemandLatency": stats([entry["restricted"] for entry in requests.values()])}
    improvement = 100 * (1 - rows["candidate"]["restrictedAllDemandLatency"]["mean"] /
                         rows["reference"]["restrictedAllDemandLatency"]["mean"])
    return {"conditions": a, "runIdentities": identities,
            "processIds": [left_trace.get("processId"), right_trace.get("processId")],
            "demandCount": len(old), "commonReadyCount": len(common), **rows,
            "restrictedMeanImprovementPercent": improvement}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--screen", action="store_true")
    options = parser.parse_args()
    pairs = [compare(options.root / "b-physical-biome-reference-v8-s8", options.root / "b-v8-s8")] if options.screen else [
        compare(options.root / f"pair-{index}/reference", options.root / f"pair-{index}/candidate") for index in range(3)]
    if len({row["conditions"]["inputHash"] for row in pairs}) != 1:
        raise ValueError("Source changed between interleaved pairs")
    if not options.screen:
        for field in ("runIdentities", "processIds"):
            identities = [identity for row in pairs for identity in row[field]]
            if not all(identities) or len(set(identities)) != 6:
                raise ValueError("Reused/missing fresh JVM or world identity: " + field)
    result = {"status": "MEASURED", "scope": "SCREEN_NOT_RETENTION" if options.screen else "THREE_INTERLEAVED_PHYSICAL_PAIRS",
        "primaryMetric": "mean request-to-ready restricted to the same finite observation window for ALL demanded chunks",
        "censoring": "Unfinished requests are retained at their observed lower bound, never claimed as completed latency",
        "matchedP95": "Only common completed keys; inspect unfinished counts alongside this conditional statistic",
        "pairs": pairs,
        "medianRestrictedMeanImprovementPercent": statistics.median(row["restrictedMeanImprovementPercent"] for row in pairs),
        "retention": "PENDING correctness, p95/memory, noise review and all target/dimension gates"}
    destination = options.root / ("physical-biomes-screen.json" if options.screen else "comparison.json")
    destination.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
