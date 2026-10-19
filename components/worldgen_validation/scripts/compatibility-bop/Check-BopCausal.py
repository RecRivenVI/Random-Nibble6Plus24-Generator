"""Strict conditional replay checks; never converts the original B parity failures to PASS."""

import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def blocks(path):
    with gzip.open(path, "rb") as stream:
        x, z, minimum, height = struct.unpack(">4i", stream.read(16))
        values = []
        for _ in range(height * 256):
            length = struct.unpack(">H", stream.read(2))[0]
            values.append(stream.read(length).decode("utf-8"))
        require(stream.read() == b"", "Unexpected block payload suffix")
    return (x, z, minimum, height), values


def validate_random(events):
    """Check the API result, not merely the internal state/bit-consumption hash."""
    pending = []
    apis = 0
    for event in events:
        api, values = event[4], event[5:]
        if api == "draw":
            pending.append(values[:2])
        elif api.startswith("api:"):
            if api == "api:bounded":
                bound, actual = values
                require(bound > 0 and pending and all(bits == 31 for bits, _ in pending), "Invalid nextInt input/draw")
                if bound & (bound-1) == 0:
                    require(len(pending) == 1, "Extra power-of-two draw")
                    expected = (bound * pending[0][1]) >> 31
                else:
                    for n, (_, bits) in enumerate(pending):
                        expected = bits % bound
                        reject = bits - expected + (bound-1) >= 2**31
                        require(reject == (n < len(pending)-1), "Bounded RNG rejection/consumption mismatch")
                require(expected == actual, "Bounded RNG return mismatch")
            elif api == "api:floatBits":
                require(len(pending) == 1 and pending[0][0] == 24, "Invalid float consumption")
                expected = struct.unpack(">i", struct.pack(">f", pending[0][1] * 2**-24))[0]
                require(expected == values[0], "Float return mismatch")
            elif api == "api:long":
                require(len(pending) == 2 and all(bits == 32 for bits, _ in pending), "Invalid long consumption")
                expected = ((pending[0][1] << 32) + pending[1][1]) % 2**64
                if expected >= 2**63:
                    expected -= 2**64
                require(expected == values[0], "Long return mismatch")
            elif api == "api:boolean":
                require(len(pending) == 1 and pending[0][0] == 1 and bool(pending[0][1]) == values[0], "Boolean return mismatch")
            elif api == "api:int":
                require(pending == [[32, values[0]]], "Integer return mismatch")
            else:
                raise AssertionError(f"Unaudited causal API {api}")
            pending.clear()
            apis += 1
        elif api in ("seed", "slot", "end", "placed"):
            require(not pending, "Unaccounted RNG draws")
    require(not pending, "Unterminated RNG API")
    return apis


def load(root, name):
    folder = root / name
    summary = read_json(folder / "summary.json")
    trace = read_json(folder / "trace.json")
    require(summary["status"] == "CAPTURED", f"Incomplete capture {name}")
    require(summary["entities"] == 0, "Entity randomness is outside this causal fixture")
    require(trace["upstreamEnumeratorEqual"], "Unproven natural input")
    require(trace["upstreamTbSetClass"] == "java.util.HashSet", "Changed upstream collection")
    for api in ("draw", "api:bounded", "read:block", "write", "placed"):
        require(trace["counts"].get(api, 0) > 0, f"Missing trace coverage {api}")
    with gzip.open(folder / "events.jsonl.gz", "rt", encoding="utf-8") as stream:
        events = [json.loads(line) for line in stream]
    require(len(events) == trace["eventCount"], "Truncated trace")
    require(all(event[0] == n for n, event in enumerate(events)), "Non-contiguous events")
    validate_random(events)
    before_info, before = blocks(folder / "before.blocks.gz")
    after_info, after = blocks(folder / "after.blocks.gz")
    require(before_info == after_info, "Changed target bounds")
    require(blocks(folder / "final.blocks.gz") == (after_info, after), "Untraced post-decoration change")
    last_writes = {}
    x, z, minimum, height = before_info
    for event in events:
        if event[4] != "write":
            continue
        px, py, pz = event[5:8]
        require(px // 16 == x and pz // 16 == z, "Write escaped target")
        if minimum <= py < minimum + height:
            index = (py - minimum) * 256 + (pz % 16) * 16 + px % 16
            last_writes[index] = event
    for n, state in enumerate(after):
        writer = last_writes.get(n)
        require(state == (writer[9] if writer else before[n]), f"Untraced final block {name}:{n}")
    require(not name.startswith("isolated") or summary["physicalHostUnchanged"], "Host mutation")
    return {"name": name, "summary": summary, "trace": trace, "events": events,
            "info": before_info, "before": before, "after": after, "last": last_writes,
            "snapshot": gzip.decompress((folder / "features.bin.gz").read_bytes())}


def identical(source, replay):
    a, b = source["summary"], replay["summary"]
    require(a["pid"] != b["pid"] and a["save"] != b["save"], "Replay reused process/save")
    for field in ("inputHash", "environment", "terrainHash", "hash", "rejected", "rawEntityNbt", "javaRuntime", "availableProcessors"):
        require(a[field] == b[field], f"Same-condition mismatch {source['name']} -> {replay['name']}: {field}")
    require(source["trace"]["effectiveBiomeOrder"] == replay["trace"]["effectiveBiomeOrder"], "Replay input order differs")
    require(source["trace"]["effectiveFeatureOrder"] == replay["trace"]["effectiveFeatureOrder"], "Replay sorter differs")
    require(source["events"] == replay["events"], f"Same-condition ordered read/write/RNG differs: {source['name']} -> {replay['name']}")
    require(source["before"] == replay["before"] and source["after"] == replay["after"], "Same-condition block mismatch")
    require(source["snapshot"] == replay["snapshot"], "Same-condition full snapshot payload differs")
    return {"source": source["name"], "replay": replay["name"], "events": len(source["events"]), "hash": a["hash"], "status": "PASS"}


def rarity_branch(run):
    """Validate the observed 26.3 RarityFilter input/branch, not a guessed reason."""
    feature = "biomesoplenty:jagged_sandstone"
    events = run["events"]
    entries = [n for n, event in enumerate(events) if event[3:5] == [feature, "feature"]]
    require(len(entries) == 1, "Missing or repeated representative feature")
    n = entries[0]
    slot, seed, start, draw, api, following = events[n-2:n+4]
    require(slot[4] == "slot" and seed[4] == "seed" and draw[4:6] == ["draw", 24] and api[4] == "api:floatBits", "Unexpected rarity entry chain")
    require(run["trace"]["effectiveFeatureOrder"][start[1]][start[2]] == feature, "Feature index is not sorter input")
    expected_seed = (slot[5] + start[2] + 10000 * start[1]) % 2**64
    if expected_seed >= 2**63:
        expected_seed -= 2**64
    require(seed[5] == expected_seed, "Native feature seed formula differs")
    value = struct.unpack(">f", struct.pack(">i", api[5]))[0]
    threshold = struct.unpack(">f", struct.pack(">f", 1/3))[0]
    passes = value < threshold
    require((following[4] == "draw") if passes else following[4:6] == ["placed", False], "Rarity branch not observed")
    return {"run": run["name"], "step": start[1], "featureIndex": start[2],
            "decorationSeed": slot[5], "featureSeed": seed[5], "firstFloatRawBits": api[5],
            "rarityChance": 3, "rarityPass": passes}


def difference(a, b):
    require(a["info"] == b["info"] and a["before"] == b["before"], "Natural comparison input terrain differs")
    first = None
    for left, right in zip(a["events"], b["events"]):
        if left != right:
            first = {"left": left, "right": right}
            break
    x, z, minimum, _ = a["info"]
    members = []
    for n, (left, right) in enumerate(zip(a["after"], b["after"])):
        if left == right:
            continue
        members.append({"position": [x*16+n%16, minimum+n//256, z*16+(n//16)%16],
                        "leftState": left, "rightState": right,
                        "leftLastWriter": a["last"].get(n), "rightLastWriter": b["last"].get(n)})
    return {"left": a["name"], "right": b["name"], "firstEventDifference": first,
            "differingBlocks": len(members), "members": members,
            "classification": "proven_original_variability" if members else "equal"}


def check(root):
    names = ("native_natural_1", "native_natural_2", "native_natural_3", "isolated_natural_17", "isolated_natural_23",
             "native_from_isolated17", "native_from_isolated23", "isolated17_from_native",
             "isolated23_from_native", "native_repeat_from_native", "isolated17_from_native3", "isolated23_from_native3",
             "native_from_recorded_order", "isolated17_from_recorded_order", "isolated23_from_recorded_order")
    runs = {name: load(root, name) for name in names}
    require(len({r["summary"]["pid"] for r in runs.values()}) == len(runs), "Non-independent JVMs")
    baseline = runs["native_natural_1"]["summary"]
    for run in runs.values():
        for field in ("inputHash", "environment", "terrainHash", "localSeed", "javaRuntime", "availableProcessors"):
            require(run["summary"][field] == baseline[field], f"Environment/precondition differs: {field}")
    pairs = (("native_natural_1", "isolated17_from_native"),
             ("native_natural_1", "isolated23_from_native"),
             ("isolated_natural_17", "native_from_isolated17"),
             ("isolated_natural_23", "native_from_isolated23"),
             ("native_natural_1", "native_repeat_from_native"),
             ("native_natural_3", "isolated17_from_native3"),
             ("native_natural_3", "isolated23_from_native3"),
             ("native_from_recorded_order", "isolated17_from_recorded_order"),
             ("native_from_recorded_order", "isolated23_from_recorded_order"))
    strict = [identical(runs[a], runs[b]) for a, b in pairs]
    sources = [runs[name] for name in ("native_natural_1", "native_natural_3", "isolated_natural_17", "isolated_natural_23")]
    native_second = runs["native_natural_2"]
    require(any(native_second["events"] == source["events"] and native_second["summary"]["hash"] == source["summary"]["hash"] for source in sources), "Second native condition lacks a matching counterfactual")
    natural_pairs = (("native_natural_1", "isolated_natural_17"),
                     ("native_natural_1", "isolated_natural_23"),
                     ("native_natural_1", "native_natural_2"),
                     ("isolated_natural_23", "isolated_natural_17"),
                     ("native_natural_1", "native_natural_3"))
    diffs = [difference(runs[a], runs[b]) for a, b in natural_pairs]
    witness_folder = root / "natural_witness_fca"
    witness = read_json(witness_folder / "trace.json")
    witness_summary = read_json(witness_folder / "summary.json")
    require(witness["upstreamEnumeratorEqual"] and "diagnosticOrderSource" not in witness, "Recorded order was not naturally observed")
    recorded = runs["native_from_recorded_order"]
    require(recorded["trace"]["effectiveBiomeOrder"] == witness["naturalBiomeOrder"], "Recorded order input changed")
    require(recorded["trace"]["effectiveFeatureOrder"] == witness["naturalFeatureOrder"], "Recorded FeatureSorter output changed")
    require(recorded["summary"]["hash"] == witness_summary["hash"], "Recorded output not reproduced")
    require(gzip.decompress((root / "native_from_recorded_order/features.bin.gz").read_bytes()) == gzip.decompress((witness_folder / "features.bin.gz").read_bytes()), "Recorded complete payload not reproduced")
    diffs.append(difference(runs["native_natural_1"], recorded))
    require(any(d["differingBlocks"] > 0 for d in diffs), "No natural divergence reproduced")
    for entry in read_json(root / "original_evidence.json"):
        require(hashlib.sha256(Path(entry["path"]).read_bytes()).hexdigest().upper() == entry["hash"], "Original failure evidence changed")
    historical = []
    prior = root.parent / "feature_b/26.3-fabric/bop-the_end-1"
    for folder in ("native", "host_17", "host_23", "native_repeat_1", "native_repeat_2", "native_repeat_3"):
        old = read_json(prior / folder / "summary.json")
        matches = [run for run in runs.values() if run["summary"]["hash"] == old["hash"]]
        for match in matches:
            with gzip.open(prior / folder / "features.bin.gz", "rb") as stream:
                original = stream.read()
            with gzip.open(root / match["name"] / "features.bin.gz", "rb") as stream:
                require(original == stream.read(), "Historical hash match without complete payload equality")
        historical.append({"original": folder, "strictStatus": old["status"],
                           "completeOutputWitnesses": [run["name"] for run in matches],
                           "historicalReadRngChainRecorded": False,
                           "historicalExecutionClassification": "unexplained"})
    (root / "differences.json").write_text(json.dumps(diffs, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")
    return {"conditionalParity": "PASS", "bidirectionalReplay": strict,
            "representativeBranch": [rarity_branch(run) for run in runs.values()],
            "recordedOrderWitness": {"traceSha256": hashlib.sha256((witness_folder / "trace.json").read_bytes()).hexdigest(), "scope": "natural upstream order only; obsolete fluid-address trace is not read/RNG parity evidence"},
            "naturalDifferences": [{k: v for k, v in d.items() if k != "members"} for d in diffs],
            "historicalEvidence": historical,
            "productionSortingChanged": False, "originalStrictGateWaived": False,
            "scope": "26.3 Fabric pinned BOP/TerraBlender; seed 0 End (112,-512), B single writer"}


if __name__ == "__main__":
    root = Path(sys.argv[1])
    try:
        result = check(root)
    except Exception as failure:
        result = {"conditionalParity": "FAIL", "failure": str(failure), "classification": "unexplained"}
        (root / "analysis.json").write_text(json.dumps(result, indent=2)+"\n", encoding="utf-8")
        raise
    (root / "analysis.json").write_text(json.dumps(result, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))
