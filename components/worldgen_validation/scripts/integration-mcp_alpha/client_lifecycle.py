"""Real-client lifecycle actuation through the pinned alpha, project-owned assertions."""

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import time

import mcp_alpha as alpha


class ClientScenario:
    def __init__(self, stage, label):
        if stage not in ("lifecycle_b", "lifecycle_vanilla") or Path(label).name != label:
            raise ValueError("Expected an isolated lifecycle stage and local evidence label")
        self.stage = stage
        self.instance = alpha.VALIDATION / "instance" / alpha.TARGET / stage
        self.result = alpha.VALIDATION / "result/client-lifecycle" / stage / label
        if self.result.exists():
            raise RuntimeError("Preserve the previous lifecycle result")
        self.result.mkdir(parents=True)
        self.report = {"status": "RUNNING", "stage": stage, "snapshot": alpha.SNAPSHOT,
                       "jarSha256": alpha.JAR_HASH, "scope": "REAL_CLIENT_NATIVE_LIFECYCLE_WITH_PROJECT_OBSERVER",
                       "arrange": "Creative/kill/dimension teleport/setblock are diagnostic player-command fixtures",
                       "checkpoints": [], "operations": []}
        self.observer = self.instance / "project-observer"
        self.world = "MCP B Lifecycle" if stage == "lifecycle_b" else "MCP Vanilla Lifecycle"
        self.run_id = None
        self.save()

    def save(self):
        alpha.save(self.result / "summary.json", self.report)

    def read(self, path):
        return alpha.request(self.instance, path)

    def wait(self, function, name, timeout=300):
        deadline = time.monotonic() + timeout
        last = None
        while time.monotonic() < deadline:
            try:
                last = function()
            except (FileNotFoundError, json.JSONDecodeError):
                last = None
            if last:
                alpha.save(self.result / (name + ".json"), last)
                return last
            time.sleep(0.75)
        alpha.save(self.result / (name + "-timeout.json"), {"last": last})
        raise RuntimeError("Real client condition timed out: " + name)

    def control(self):
        status = self.read("/v0/control/status")
        if status.get("reconsentRequired"):
            raise RuntimeError("Human revoked control; explicit conversation reconsent is required")
        if status.get("takeoverActive"):
            value = alpha.request(self.instance, "/v0/control/renew", {"ttlMs": 60000}, lease=True)
        else:
            value = alpha.request(self.instance, "/v0/control/acquire", {"ttlMs": 60000})
        alpha.save(self.instance / "automation-control.json", value)

    def ui(self, selector, action="click"):
        self.control()
        before = self.read("/v0/ui/tree")
        index = len(self.report["operations"])
        alpha.save(self.result / f"operation-{index}-before-ui.json", before)
        result = alpha.request(self.instance, "/v0/ui/action", {"action": action, "selector": selector}, lease=True)
        if result.get("directBusinessCallUsed") or result.get("directMutationUsed"):
            raise RuntimeError("UI did not use native player input")
        alpha.save(self.result / f"operation-{index}.json", result)
        self.report["operations"].append({"kind": "ui", "selector": selector, "action": action})
        self.save()

    def key(self, key):
        self.control()
        value = alpha.request(self.instance, "/v0/pipelines", {"steps": [{"type": "key.tap", "key": key}], "timeoutMs": 5000}, lease=True)
        operation = value["operationId"]
        finished = self.wait(lambda: self.terminal(operation), "key-" + operation, 15)
        if finished.get("state") != "completed":
            raise RuntimeError("Native key operation failed: " + str(finished.get("errorCode")))

    def terminal(self, operation):
        value = self.read("/v0/operations/" + operation)
        return value if value.get("state") in ("completed", "failed", "cancelled") else None

    def command(self, text, label):
        self.control()
        result = alpha.request(self.instance, "/v0/command/player", {"command": text}, lease=True)
        alpha.save(self.result / (label + "-command.json"), result)
        self.report["operations"].append({"kind": "player_command", "command": text,
                                          "result": label + "-command.json", "effectNeedsIndependentCheck": True})
        self.save()

    def tree(self, fragment):
        value = self.read("/v0/ui/tree")
        return value if fragment in value.get("screenClass", "") and value.get("overlayIdentity") == 0 else None

    def player(self, dimension):
        value = self.read("/v0/player")
        return value if value.get("available") and value.get("anchor", {}).get("dimension") == dimension else None

    def enter(self, label):
        self.wait(lambda: self.tree("TitleScreen"), label + "-title")
        self.ui({"translationKey": "menu.singleplayer"})
        self.wait(lambda: self.tree("SelectWorldScreen"), label + "-world-list")
        self.ui({"worldId": self.world}, "double_click")
        def opening():
            value = self.read("/v0/session")
            return value if value.get("inWorld") or "BackupConfirmScreen" in value.get("screenClass", "") else None
        opened = self.wait(opening, label + "-opening")
        if "BackupConfirmScreen" in opened.get("screenClass", ""):
            self.ui({"translationKey": "selectWorld.backupJoinSkipButton"})
        observed = self.wait(lambda: self.player("minecraft:overworld"), label + "-client-player")
        def ready():
            value = self.read("/v0/ui/tree")
            return value if not value.get("screenClass") and value.get("overlayIdentity") == 0 else None
        self.wait(ready, label + "-world-ui")
        def server():
            value = alpha.read_json(self.observer / "status.json")
            process = alpha.read_json(self.instance / "automation-process.json")
            return value if value.get("serverRunId") != self.run_id and value.get("pid") == process["pid"] else None
        state = self.wait(server, label + "-server-observer")
        if state.get("status") == "FAIL":
            raise RuntimeError("Project observer failed: " + str(state.get("failure")))
        self.run_id = state["serverRunId"]
        return observed["anchor"]

    def checkpoint(self, name, dimension="minecraft:overworld", baseline=None, marker=None):
        request = {"id": name, "dimension": dimension, "serverRunId": self.run_id}
        if baseline:
            request["baseline"] = baseline
        if marker:
            request["marker"] = marker
        temporary = self.observer / "request.pending"
        alpha.save(temporary, request)
        os.replace(temporary, self.observer / "request.json")
        def completed():
            value = alpha.read_json(self.observer / "status.json")
            if value.get("serverRunId") != self.run_id:
                raise RuntimeError("Unexpected integrated server replacement")
            if value.get("status") == "FAIL":
                raise RuntimeError("Project observer failed: " + str(value.get("failure")))
            return value if value.get("latest", {}).get("id") == name else None
        state = self.wait(completed, name + "-server-checkpoint")
        client = self.wait(lambda: self.player(dimension), name + "-client-checkpoint")
        record = state["latest"]
        if record["physicalStatus"] != "ENTITY_TICKING" or record["naturalPlayerTicks"] < 20 or not record["lightCorrect"]:
            raise RuntimeError("Physical native player readiness is incomplete")
        if len(record["physicalNeighbors"]) != 9:
            raise RuntimeError("Missing real physical runtime neighbors")
        self.report["checkpoints"].append({"name": name, "serverRunId": self.run_id, "server": record,
                                            "clientAnchor": client["anchor"]})
        self.save()
        return record

    def leave(self, label):
        self.key(256)
        self.wait(lambda: self.tree("PauseScreen"), label + "-pause")
        self.ui({"translationKey": "menu.returnToMenu"})
        self.wait(lambda: self.tree("TitleScreen"), label + "-saved-title")
        close = self.wait(lambda: alpha.read_json(self.observer / ("close-" + self.run_id + ".json")), label + "-server-close")
        if close.get("failure") or any(close.get(key) != 0 for key in ("inFlight", "bindings", "spawnBindings", "playerCount")) or close["observerRetained"]:
            raise RuntimeError("Integrated server did not cleanly release its lifecycle state")

    def run(self):
        try:
            anchor = self.enter("fresh")
            first = self.checkpoint("fresh_join")
            if not first["findSpawnCalls"] or not first["postProcessCalls"]:
                raise RuntimeError("Fresh player did not exercise native spawn search/post-processing")
            self.command("/gamemode survival", "survival")
            self.wait(lambda: (value if (value := self.player("minecraft:overworld")) and value.get("gameMode") == "survival" else None), "survival-player")
            self.command("/kill @s", "kill")
            self.wait(lambda: self.tree("DeathScreen"), "native-death-screen")
            self.ui({"translationKey": "deathScreen.respawn"})
            self.wait(lambda: self.player("minecraft:overworld"), "respawn-client")
            self.command("/gamemode creative", "creative")
            respawn = self.checkpoint("respawn")
            if respawn["deaths"] != 1 or respawn["respawns"] != 1:
                raise RuntimeError("Real death/respawn replacement was not observed exactly once")
            return_command = f"/execute in minecraft:overworld run tp @s {anchor['x']} {anchor['y']} {anchor['z']}"
            for dimension, name in (("minecraft:the_nether", "nether"), ("minecraft:the_end", "end")):
                self.command(f"/execute in {dimension} run tp @s 8.5 130.0 8.5", name)
                self.checkpoint(name, dimension)
                self.command(return_command, name + "_return")
                self.checkpoint(name + "_return")
            latest = self.report["checkpoints"][-1]["server"]
            marker = [latest["chunkX"] * 16 + 8, math.floor(latest["y"]) + 2, latest["chunkZ"] * 16 + 8]
            self.command(f"/setblock {marker[0]} {marker[1]} {marker[2]} minecraft:gold_block", "saved_marker")
            self.checkpoint("before_save", marker=marker)
            baseline = self.run_id
            self.leave("first_exit")
            self.enter("returning")
            saved = self.checkpoint("saved_reenter", baseline=baseline, marker=marker)
            if saved["findSpawnCalls"] != 0 or saved["artifactCaptures"] != 0:
                raise RuntimeError("Saved FULL owner or saved player respawned through generation")
            self.leave("second_exit")
            self.control()
            alpha.request(self.instance, "/v0/control/release", {}, lease=True)
            clean = self.read("/v0/input/state")
            if clean.get("pressedKeyCount") != 0 or clean.get("pressedButtonCount") != 0:
                raise RuntimeError("Native input was not released")
            self.report["inputReleased"] = True
            self.report["status"] = "PASS"
        except Exception as failure:
            self.report["status"] = "FAIL"
            self.report["failure"] = str(failure)
            raise
        finally:
            self.save()

    def cold(self, baseline_label):
        baseline_path = self.result.parent / baseline_label
        baseline = alpha.read_json(baseline_path / "summary.json")
        if baseline.get("status") != "PASS":
            raise RuntimeError("Cold restart requires a completed fresh/save/reenter baseline")
        before = next(item for item in baseline["checkpoints"] if item["name"] == "before_save")
        command = next(item["command"] for item in baseline["operations"]
                       if item.get("kind") == "player_command" and item["command"].startswith("/setblock "))
        coordinates = re.fullmatch(r"/setblock (-?\d+) (-?\d+) (-?\d+) minecraft:gold_block", command)
        if not coordinates:
            raise RuntimeError("Unknown test marker fixture")
        marker = [int(value) for value in coordinates.groups()]
        self.report["kind"] = "cold_restart"
        self.report["baseline"] = baseline_label
        try:
            self.enter("cold")
            saved = self.checkpoint("cold_reenter", baseline=before["serverRunId"], marker=marker)
            if saved["findSpawnCalls"] != 0 or saved["artifactCaptures"] != 0:
                raise RuntimeError("Cold client reload regenerated a saved player/FULL owner")
            self.leave("cold_exit")
            self.control()
            alpha.request(self.instance, "/v0/control/release", {}, lease=True)
            clean = self.read("/v0/input/state")
            if clean.get("pressedKeyCount") != 0 or clean.get("pressedButtonCount") != 0:
                raise RuntimeError("Cold restart left held native input")
            self.report["inputReleased"] = True
            self.report["status"] = "PASS"
        except Exception as failure:
            self.report["status"] = "FAIL"
            self.report["failure"] = str(failure)
            raise
        finally:
            self.save()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", choices=("lifecycle_vanilla", "lifecycle_b"), required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument("--expected-input-hash")
    parser.add_argument("--cold-baseline")
    args = parser.parse_args()
    if args.expected_input_hash and not args.check_only:
        parser.error("Historical input hashes are supported only by read-only checks")
    if args.check_only:
        check(args.stage, args.label, args.expected_input_hash)
    elif args.cold_baseline:
        ClientScenario(args.stage, args.label).cold(args.cold_baseline)
    else:
        ClientScenario(args.stage, args.label).run()


def check(stage, label, expected_hash=None):
    if stage not in ("lifecycle_b", "lifecycle_vanilla") or Path(label).name != label:
        raise ValueError("Invalid lifecycle evidence scope")
    directory = alpha.VALIDATION / "result/client-lifecycle" / stage / label
    report = alpha.read_json(directory / "summary.json")
    if report.get("status") != "PASS" or not report.get("inputReleased"):
        raise RuntimeError("Real-client scenario is incomplete")
    cold = report.get("kind") == "cold_restart"
    names = ["cold_reenter"] if cold else ["fresh_join", "respawn", "nether", "nether_return", "end", "end_return", "before_save", "saved_reenter"]
    if [item["name"] for item in report["checkpoints"]] != names:
        raise RuntimeError("Real-client checkpoint coverage differs")
    if len({item["serverRunId"] for item in report["checkpoints"]}) != (1 if cold else 2):
        raise RuntimeError("Fresh/reenter did not use independent server instances")
    if expected_hash is None:
        source = hashlib.sha256()
        target = alpha.ROOT / "versions" / alpha.TARGET
        for entry in sorted((target / "src").rglob("*")):
            if entry.is_file():
                source.update(entry.relative_to(target).as_posix().encode())
                source.update(entry.read_bytes())
        expected_hash = source.hexdigest()
    pids = set()
    for name in names:
        observed = alpha.read_json(directory / (name + "-server-checkpoint.json"))
        if observed["inputHash"] != expected_hash or observed["logicalProcessors"] != 4:
            raise RuntimeError("Source fingerprint or resource conditions differ")
        if observed["mosaic"] != (stage == "lifecycle_b"):
            raise RuntimeError("Mosaic/Vanilla control identity differs")
        row = observed["latest"]
        if row["id"] != name or row["physicalStatus"] != "ENTITY_TICKING" or row["naturalPlayerTicks"] < 20 or row["lightCorrect"] is not True:
            raise RuntimeError("Native player runtime readiness not proven")
        neighbors = row["physicalNeighbors"]
        if len({(item["x"], item["z"]) for item in neighbors}) != 9:
            raise RuntimeError("Real physical neighbor coverage differs")
        seeds = {item["localSeed"] for item in neighbors}
        if stage == "lifecycle_b" and len(seeds) <= 1:
            raise RuntimeError("Mosaic runtime neighbors did not retain independent local seeds")
        if stage == "lifecycle_b" and name in ("fresh_join", "nether", "end") and row["artifactCaptures"] != 1:
            raise RuntimeError("Fresh physical owner did not deduplicate its canonical Artifact")
        if stage == "lifecycle_vanilla" and seeds != {observed["worldSeed"]}:
            raise RuntimeError("Vanilla physical neighbors were changed to Mosaic seeds")
        if stage == "lifecycle_vanilla" and observed["artifactCapturesSinceLoad"] != 0:
            raise RuntimeError("Vanilla entered Mosaic canonical generation")
        client = alpha.read_json(directory / (name + "-client-checkpoint.json"))
        if client.get("available") is not True or client["anchor"]["dimension"] != row["dimension"]:
            raise RuntimeError("Client did not receive the expected dimension")
        if math.floor(client["anchor"]["x"] / 16) != row["chunkX"] or math.floor(client["anchor"]["z"] / 16) != row["chunkZ"]:
            raise RuntimeError("Client/server owner positions differ")
        pids.add(observed["pid"])
    for name in (("cold_exit",) if cold else ("first_exit", "second_exit")):
        closed = alpha.read_json(directory / (name + "-server-close.json"))
        if closed["inputHash"] != expected_hash or closed.get("failure") or closed["observerRetained"]:
            raise RuntimeError("Server close evidence differs")
        if any(closed.get(key) != 0 for key in ("bindings", "spawnBindings", "inFlight", "playerCount")):
            raise RuntimeError("World close leaked runtime lifecycle state")
    first = report["checkpoints"][0]["server"]
    respawn = None if cold else report["checkpoints"][1]["server"]
    reload = report["checkpoints"][-1]["server"]
    if not cold and (first["findSpawnCalls"] <= 0 or first["postProcessCalls"] <= 0 or respawn["deaths"] != 1 or respawn["respawns"] != 1):
        raise RuntimeError("Native spawn/death/respawn coverage missing")
    if (not cold and first["playerUuid"] != respawn["playerUuid"]) or reload["findSpawnCalls"] != 0 or reload["artifactCaptures"] != 0 or reload.get("marker") != "minecraft:gold_block":
        raise RuntimeError("Real player/save/reload semantics differ")
    if cold:
        baseline = alpha.read_json(directory.parent / report["baseline"] / "fresh_join-server-checkpoint.json")
        if baseline["pid"] in pids or baseline["inputHash"] != expected_hash:
            raise RuntimeError("Cold reload did not use an independent JVM with the same source")
    result = {"status": "PASS", "stage": stage, "inputHash": expected_hash, "pids": sorted(pids),
              "checkpointCount": len(names), "integratedServerInstances": 1 if cold else 2,
              "scope": "NATIVE_REAL_CLIENT_DEATH_AND_COMMAND_TELEPORT_ROUNDTRIP_NOT_PORTAL_SEARCH_OR_PERFORMANCE"}
    alpha.save(directory / "check.json", result)
    print(json.dumps(result))


if __name__ == "__main__":
    main()
