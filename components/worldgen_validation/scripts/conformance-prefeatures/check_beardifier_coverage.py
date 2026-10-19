"""Require actual full-height 26.3 native Beardifier evidence, not a declared count."""

import argparse
import hashlib
import json
from pathlib import Path


def check(directory):
    report = json.loads((directory / "beardifier.json").read_text(encoding="utf-8"))
    keys = ("coverageVersion", "sampleBytes", "minY", "maxYInclusive", "height", "samples")
    if any(type(report.get(key)) is not int for key in keys):
        raise ValueError("Beardifier coverage/version/height metadata incomplete")
    height = report["height"]
    if report["coverageVersion"] != 2 or report["sampleBytes"] != 4 or height <= 0:
        raise ValueError("Not complete 26.3 float coverage")
    if report["maxYInclusive"] != report["minY"] + height - 1 or report["samples"] != 256 * height:
        raise ValueError("Beardifier height or declared sample count differs")
    payload = (directory / "beardifier.bin").read_bytes()
    if len(payload) != 4 * report["samples"]:
        raise ValueError("Beardifier payload omits or duplicates native height samples")
    if hashlib.sha256(payload).hexdigest() != report.get("sha256"):
        raise ValueError("Beardifier raw payload hash differs")
    return report["samples"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    samples = check(parser.parse_args().directory)
    print(f"PASS: complete 26.3 Beardifier coverage, {samples} actual float samples")


if __name__ == "__main__":
    main()
