"""Project-owned, installed Fabric alpha smoke; no desktop input or MCP source build."""

import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[4]
VALIDATION = ROOT / "validations/integration-mcp_alpha"
CONFIGURATION = tomllib.loads((VALIDATION / "validation.toml").read_text(encoding="utf-8"))
TARGET = os.environ.get("MOSAIC_MCP_TARGET", CONFIGURATION["targets"][0])
if TARGET not in CONFIGURATION["targets"]:
    raise ValueError("Unregistered MCP Target")
INPUT = tomllib.loads((ROOT / "inputs.toml").read_text(encoding="utf-8"))[
    CONFIGURATION["alpha"]["neoforge-input" if TARGET.endswith("-neoforge") else "input"]]
SOURCE = Path(INPUT["source"])
SNAPSHOT = SOURCE.name
BUNDLE = ROOT / "build/artifacts/minecraft_protocol" / SNAPSHOT
JAVA = Path("C:/Program Files/BellSoft/LibericaJDK-25-Full/bin/java.exe")
CACHE = Path.home() / ".gradle/caches"
JAR_HASH, = INPUT["sha256"]
MAX_BYTES = CONFIGURATION["alpha"]["storage-limit-bytes"]


def digest(path, algorithm="sha256"):
    return hashlib.file_digest(path.open("rb"), algorithm).hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def storage_bytes():
    return sum(path.stat().st_size for directory in (VALIDATION, BUNDLE)
               for path in directory.rglob("*") if path.is_file())


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def bundle():
    if (SOURCE.parent / ".withdrawn" / (SNAPSHOT + ".json")).exists():
        raise RuntimeError("Snapshot withdrawn")
    manifest = read_json(SOURCE / "alpha-manifest.json")
    if manifest["packagedRuntimeVerified"] is not True:
        raise RuntimeError("No packaged Runtime proof")
    artifact, = [item for item in manifest["artifacts"] if item["target"] == TARGET]
    if artifact["sha256"] != JAR_HASH:
        raise RuntimeError("Unexpected fixed JAR")
    BUNDLE.mkdir(parents=True, exist_ok=True)
    expected = {artifact["fileName"]: JAR_HASH,
                "AGENT_GUIDE.md": manifest["agentGuide"]["sha256"],
                "alpha-manifest.json": digest(SOURCE / "alpha-manifest.json")}
    for name, checksum in expected.items():
        source, destination = SOURCE / name, BUNDLE / name
        if digest(source) != checksum:
            raise RuntimeError("Distribution hash mismatch: " + name)
        if destination.exists() and digest(destination) != checksum:
            raise RuntimeError("Refusing changed local snapshot: " + name)
        if not destination.exists():
            shutil.copy2(source, destination)
        if digest(destination) != checksum:
            raise RuntimeError("Copy hash mismatch: " + name)
    return artifact


def pin(mode):
    tool = Path("D:/Workspaces/Repositories/GitHub/RecRivenVI/Mine-Craft-Protocol/components/validation_tools/regression-v1_release/task/Publish-AlphaSnapshot.ps1")
    value = json.loads(subprocess.check_output([
        "pwsh", "-NoProfile", "-File", str(tool), "-Mode", mode,
        "-Snapshot", str(SOURCE), "-Consumer", "random_nibble_01a044d0" + ("_neoforge" if TARGET.endswith("-neoforge") else "")], encoding="utf-8"))
    if value["inUse"] != (mode == "Pin"):
        raise RuntimeError("Alpha usage registration did not match requested lifecycle")
    return value


def allowed(library):
    result = not library.get("rules")
    for rule in library.get("rules", []):
        selector = rule.get("os", {})
        matches = selector.get("name", "windows") == "windows"
        matches &= selector.get("arch", "x86_64") in ("x86_64", "amd64")
        if matches and not rule.get("features"):
            result = rule["action"] == "allow"
    return result


def library_file(installation, relative, url, checksum=None, algorithm="sha1"):
    target = installation / "libraries" / relative
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        parts = Path(relative).parts
        group, module, version = ".".join(parts[:-3]), parts[-3], parts[-2]
        candidates = list((CACHE / "modules-2/files-2.1" / group / module / version).glob("*/" + parts[-1]))
        existing = next((path for path in candidates if not checksum or digest(path, algorithm) == checksum), None)
        if existing:
            shutil.copy2(existing, target)
        else:
            target.write_bytes(fetch(url))
    if checksum and digest(target, algorithm) != checksum:
        raise RuntimeError("Library checksum mismatch: " + str(target))
    return target


def prepare(stage):
    if TARGET.endswith("-neoforge"):
        from neoforge_client import prepare_native
        prepare_native(stage)
        return
    artifact = bundle()
    pin("Pin")
    for candidate in (VALIDATION / "instance" / TARGET).glob("*/minecraft-protocol/endpoint.json"):
        raise RuntimeError("Close or explicitly inspect the owned Runtime before preparing shared installation files: " + str(candidate))
    instance = VALIDATION / "instance" / TARGET / stage
    installation = VALIDATION / "instance" / TARGET / "installation"
    instance.mkdir(parents=True, exist_ok=True)
    if (instance / "minecraft-protocol/endpoint.json").exists():
        raise RuntimeError("Inspect existing endpoint before preparing an instance")
    mods = instance / "mods"
    mods.mkdir(exist_ok=True)
    jar = BUNDLE / artifact["fileName"]
    existing = list(mods.glob("*.jar"))
    if any(path.name != jar.name for path in existing) and stage == "minimal":
        raise RuntimeError("MCP-only instance contains another mod")
    shutil.copy2(jar, mods / jar.name)
    if digest(mods / jar.name) != JAR_HASH:
        raise RuntimeError("Installed MCP changed")
    info_path = CACHE / "fabric-loom/26.3/mojang_minecraft_info.json"
    info = read_json(info_path)
    facts = dict(line.split("=", 1) for line in (ROOT / "versions" / TARGET / "target.properties")
                 .read_text(encoding="utf-8").splitlines() if "=" in line)
    minecraft = TARGET.rsplit("-", 1)[0]
    profile = json.loads(fetch(f"https://meta.fabricmc.net/v2/versions/loader/{minecraft}/{facts['loader_version']}/profile/json"))
    save(installation / "fabric-profile.json", profile)
    natives = installation / "natives"
    natives.mkdir(parents=True, exist_ok=True)
    classpath = []
    for library in info["libraries"]:
        if not allowed(library):
            continue
        item = library["downloads"].get("artifact")
        if not item:
            continue
        path = library_file(installation, item["path"], item["url"], item["sha1"])
        classpath.append(path)
        if "natives-windows" in path.name:
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith(".dll"):
                        (natives / Path(name).name).write_bytes(archive.read(name))
    for library in profile["libraries"]:
        group, module, version = library["name"].split(":")
        relative = f"{group.replace('.', '/')}/{module}/{version}/{module}-{version}.jar"
        classpath.append(library_file(installation, relative, library["url"] + relative,
                                      library.get("sha256"), "sha256"))
    client = CACHE / "fabric-loom/26.3/minecraft-client.jar"
    if digest(client, "sha1") != info["downloads"]["client"]["sha1"]:
        raise RuntimeError("Vanilla client differs from official metadata")
    classpath.append(client)
    # This is ordinary installed Fabric, not Loom's product/probe development classpath.
    args = ["-Xmx4G", "-Xms256M", "-XX:ActiveProcessorCount=4", "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=" + str(natives), "-Dminecraft.protocol.probe.port=0",
            "-cp", os.pathsep.join(map(str, classpath)), profile["mainClass"],
            "--username", "MosaicAutomation", "--version", "26.3", "--gameDir", str(instance),
            "--assetsDir", str(CACHE / "fabric-loom/assets"), "--assetIndex", "26.3-34",
            "--accessToken", "0", "--uuid", str(uuid.uuid5(uuid.NAMESPACE_DNS, "MosaicAutomation")),
            "--userType", "legacy", "--width", "1920", "--height", "1080"]
    if stage != "minimal":
        product_facts = dict(line.split("=", 1) for line in (ROOT / "gradle.properties")
                             .read_text(encoding="utf-8").splitlines() if "=" in line)
        product_version = product_facts["mod_version"]
        if stage != "lifecycle_vanilla":
            args.insert(0, "-D" + product_facts["mod_id"] + ".developmentB=true")
        product = ROOT / "versions" / TARGET / "build/libs" / f"randomnibble6plus24generator-{TARGET}-{product_version}.jar"
        if not product.is_file():
            raise RuntimeError("Build the current product JAR first")
        shutil.copy2(product, mods / product.name)
        prepare_world(instance, vanilla=stage == "lifecycle_vanilla",
                      name={"lifecycle_b": "MCP B Lifecycle", "lifecycle_vanilla": "MCP Vanilla Lifecycle"}.get(stage, "Mosaic MCP Alpha"))
        if stage.startswith("lifecycle_"):
            target = ROOT / "versions" / TARGET
            probe = mods / (product_facts["mod_id"] + "_probe.jar")
            with zipfile.ZipFile(probe, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for folder in (target / "build/classes/java/probe", target / "build/resources/probe"):
                    for entry in sorted(folder.rglob("*")):
                        if entry.is_file():
                            archive.write(entry, entry.relative_to(folder).as_posix())
            source = hashlib.sha256()
            for entry in sorted((target / "src").rglob("*")):
                if entry.is_file():
                    source.update(entry.relative_to(target).as_posix().encode())
                    source.update(entry.read_bytes())
            prefix = "-D" + product_facts["mod_id"] + ".clientLifecycle."
            args[:0] = ["-Dvalidation.name=integration-mcp_alpha", prefix + "enabled=true",
                        prefix + "mosaic=" + str(stage == "lifecycle_b").lower(),
                        prefix + "directory=" + str(instance / "project-observer"),
                        prefix + "inputHash=" + source.hexdigest(),
                        "-Dminecraft.protocol.scopes=read,ui,control,input,command,capture,diagnostics"]
    (instance / "options.txt").write_text(
        "onboardAccessibility:false\nfullscreen:false\noverrideWidth:1920\noverrideHeight:1080\n"
        "soundCategory_master:0.0\nrenderDistance:2\nsimulationDistance:2\nmaxFps:30\n", encoding="utf-8")
    save(instance / "automation-launch.json", {"java": str(JAVA), "args": args, "stage": stage,
                                               "snapshot": SNAPSHOT, "jarSha256": JAR_HASH})
    print(json.dumps({"prepared": str(instance), "externalMods": [path.name for path in mods.glob("*.jar")]}))


def prepare_world(instance, vanilla=False, name="Mosaic MCP Alpha"):
    # Only identity from this project's closed validation fixture, never terrain or player data.
    source_name = (
        "phase3c2-production-123456789-minecraft-the_end-125--37-vanilla-control-runtime-proof-all_owner_biomes"
        if vanilla else
        "phase3c2-production-123456789-minecraft-overworld-129--42-features-2-lightweight-b-runtime-proof-"
        "natural-block_entities-natural_worldgen_sulfur_template")
    source = ROOT / "validations/integration-production_lifecycle/instance/26.3-fabric/server" / source_name
    destination = instance / "saves" / name
    if destination.exists():
        return
    sys.path.insert(0, str(Path(__file__).parents[1] / "development"))
    from prepare_target_only_world import read_nbt, write_nbt
    if not vanilla:
        marker = read_json(source / "mosaic-development-b.json")
        if marker != {"schema": 1, "pipeline": "26.3-lightweight-b-1"}:
            raise RuntimeError("Not a validated B identity")
    files = ["level.dat", "data/minecraft/world_gen_settings.dat"]
    if not vanilla:
        files.append("data/randomnibble6plus24generator/mosaic_world_profile.dat")
    for relative in files:
        root_name, value = read_nbt(source / relative)
        if relative == "level.dat":
            data = value["Data"][1]
            data["LevelName"] = 8, name
            data["GameType"] = 3, struct.pack(">i", 1)
            data["allowCommands"] = 1, b"\x01"
            data.pop("Player", None)
        elif relative.endswith("mosaic_world_profile.dat"):
            if value["data"][1]["feature_ordering_algorithm_version"] != (3, struct.pack(">i", 2)):
                raise RuntimeError("B profile mismatch")
        elif vanilla:
            def check_generators(compound):
                for key, (kind, child) in compound.items():
                    if kind == 10:
                        if key == "generator" and child["type"][1] != "minecraft:noise":
                            raise RuntimeError("Vanilla source contains a non-native generator")
                        check_generators(child)
            check_generators(value)
        write_nbt(destination / relative, root_name, value)
    if (source / "datapacks").is_dir():
        shutil.copytree(source / "datapacks", destination / "datapacks")
    if not vanilla:
        shutil.copy2(source / "mosaic-development-b.json", destination / "mosaic-development-b.json")


def launch(instance):
    bundle()
    pin("Pin")
    settings = read_json(instance / "automation-launch.json")
    for candidate in instance.parent.glob("*/minecraft-protocol/endpoint.json"):
        if candidate.parent.parent != instance:
            raise RuntimeError("This alpha validation runs only one client at a time: " + str(candidate))
    if (instance / "minecraft-protocol/endpoint.json").exists():
        raise RuntimeError("Refusing unverified existing Runtime record")
    previous = instance / "automation-process.json"
    if previous.exists():
        old = read_json(previous)
        archive = VALIDATION / "result/retained" / f"{instance.name}-{old['pid']}"
        for path in (previous, instance / "automation-stdout.log"):
            if path.exists():
                archive.mkdir(parents=True, exist_ok=True)
                destination = archive / path.name
                if destination.exists() and digest(destination) != digest(path):
                    raise RuntimeError("Refusing changed retained process evidence")
                if not destination.exists():
                    shutil.copy2(path, destination)
    args_file = instance / "automation-java.args"
    args_file.write_text("\n".join(json.dumps(arg) for arg in settings["args"]) + "\n", encoding="utf-8")
    with (instance / "automation-stdout.log").open("wb") as output:
        process = subprocess.Popen([settings["java"], "@" + str(args_file)], cwd=instance,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    affinity = 61440 if TARGET.endswith("-neoforge") else 3840
    subprocess.run(["pwsh", "-NoProfile", "-Command",
                    f"(Get-Process -Id {process.pid}).ProcessorAffinity=[IntPtr]{affinity}"], check=True)
    save(instance / "automation-process.json", {"pid": process.pid, "instance": str(instance),
                                              "launchedAt": time.time(), "snapshot": SNAPSHOT})
    save(instance / "automation-binding.json", {"unboundLaunchPid": process.pid})
    print(json.dumps({"pid": process.pid, "instance": str(instance)}))


def retire(instance, label):
    if Path(label).name != label or not label:
        raise ValueError("Expected a local retirement label")
    owner = (VALIDATION / "instance" / TARGET).resolve()
    if instance.resolve().parent != owner or instance.is_symlink():
        raise ValueError("Not an owned validation instance")
    process = read_json(instance / "automation-process.json")
    live = subprocess.check_output(["pwsh", "-NoProfile", "-Command",
                                    f"@(Get-Process -Id {process['pid']} -ErrorAction SilentlyContinue).Count"], text=True).strip()
    if live != "0" or (instance / "minecraft-protocol/endpoint.json").exists():
        raise RuntimeError("Save and normally close the owned Runtime before retirement")
    destination = owner / "retired" / (instance.name + "-" + label)
    if destination.exists():
        raise RuntimeError("Preserve existing retired fixture")
    destination.parent.mkdir(exist_ok=True)
    instance.rename(destination)
    save(VALIDATION / "result/retained" / (destination.name + ".json"),
         {"source": str(instance), "retainedAt": str(destination), "pid": process["pid"]})
    print(json.dumps({"retainedAt": str(destination)}))


def connect(instance):
    endpoint = read_json(instance / "minecraft-protocol/endpoint.json")
    process = read_json(instance / "automation-process.json")
    if endpoint["pid"] != process["pid"]:
        raise RuntimeError("Runtime PID differs from launched process")
    # Read OS identity before using the instance token; never scan ports or reuse a prior UUID.
    command = (f"$p=Get-CimInstance Win32_Process -Filter \"ProcessId={process['pid']}\"; "
               "$p | Select-Object ProcessId,CommandLine,@{n='startedMillis';e={([DateTimeOffset]$_.CreationDate).ToUnixTimeMilliseconds()}} | ConvertTo-Json -Compress")
    actual = json.loads(subprocess.check_output(["pwsh", "-NoProfile", "-Command", command], encoding="utf-8"))
    if not actual or str(instance) not in actual["CommandLine"]:
        # Java's @argfile contains gameDir, so also bind the repository-owned absolute argument file.
        if not actual or str(instance / "automation-java.args") not in actual["CommandLine"]:
            raise RuntimeError("Runtime process ownership mismatch")
    if endpoint.get("target") != TARGET:
        raise RuntimeError("Runtime Target mismatch")
    if Path(endpoint["gameDirectory"]).resolve() != instance.resolve():
        raise RuntimeError("Runtime game directory mismatch")
    started = datetime.fromisoformat(endpoint["processStartedAt"].replace("Z", "+00:00")).timestamp() * 1000
    if abs(started - actual["startedMillis"]) > 10:
        raise RuntimeError("Runtime process start mismatch")
    binding = instance / "automation-binding.json"
    previous = read_json(binding) if binding.exists() else None
    if previous is not None and previous != {"unboundLaunchPid": process["pid"]} and previous != endpoint:
        raise RuntimeError("Runtime changed: explicitly relaunch, never silently rebind")
    if previous != endpoint:
        save(binding, endpoint)
    return endpoint


def request(instance, path, body=None, lease=False):
    endpoint = connect(instance)
    token = (instance / "minecraft-protocol/token").read_text(encoding="utf-8").strip()
    headers = {"Authorization": "Bearer " + token,
               "X-MCP-Runtime-Instance": endpoint["runtimeInstanceId"],
               "Content-Type": "application/json"}
    if lease:
        headers["X-MCP-Control-Lease"] = read_json(instance / "automation-control.json")["leaseId"]
    base = endpoint.get("baseUrl") or endpoint.get("baseUri")
    if not base:
        base = "http://127.0.0.1:" + str(endpoint["port"])
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers=headers), timeout=60) as response:
            value = json.load(response)
    except urllib.error.HTTPError as error:
        value = json.load(error)
        save(instance / "automation-last-error.json", {"httpStatus": error.code, "path": path, "response": value})
        raise RuntimeError(f"MCP HTTP {error.code}: {json.dumps(value)}") from error
    if path == "/v0/session" and value.get("runtimeConnection") != endpoint:
        raise RuntimeError("Online handshake differs from discovered endpoint")
    return value


def smoke(instance, label="mosaic-smoke"):
    """Real client input smoke. It does not replace internal worldgen parity probes."""
    if Path(label).name != label:
        raise ValueError("Expected a local evidence label")
    result = VALIDATION / "result" / label
    if (result / "summary.json").exists():
        raise RuntimeError("Preserve prior smoke result; use a distinct label")
    result.mkdir(parents=True, exist_ok=True)
    report = {"status": "RUNNING", "snapshot": SNAPSHOT, "jarSha256": JAR_HASH,
              "scope": "REAL_CLIENT_INPUT_NOT_WORLDGEN_ORACLE", "steps": []}
    save(result / "summary.json", report)
    sequence = 0

    def observed(path, name):
        value = request(instance, path)
        save(result / (name + ".json"), value)
        return value

    def wait(path, predicate, name, timeout=180):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = request(instance, path)
            if predicate(value):
                save(result / (name + ".json"), value)
                report["steps"].append(name)
                save(result / "summary.json", report)
                return value
            time.sleep(0.75)
        save(result / (name + "-timeout.json"), value)
        raise RuntimeError("MCP observation timed out: " + name)

    def control():
        status = request(instance, "/v0/control/status")
        if status.get("reconsentRequired"):
            raise RuntimeError("Human revocation: no automatic reacquire")
        if status.get("takeoverActive"):
            value = request(instance, "/v0/control/renew", {"ttlMs": 60000}, lease=True)
        else:
            value = request(instance, "/v0/control/acquire", {"ttlMs": 60000})
        save(instance / "automation-control.json", value)

    def action(selector, kind="click"):
        nonlocal sequence
        control()
        # Always obtain a fresh tree; the Runtime resolves and guards the semantic selector again.
        observed("/v0/ui/tree", f"{sequence:02d}-before-ui")
        value = request(instance, "/v0/ui/action", {"action": kind, "selector": selector}, lease=True)
        if value.get("directBusinessCallUsed") or value.get("directMutationUsed"):
            raise RuntimeError("Unexpected direct mutation in player UI route")
        save(result / f"{sequence:02d}-ui-action.json", value)
        sequence += 1

    def enter(index):
        wait("/v0/ui/tree", lambda v: "TitleScreen" in v.get("screenClass", "")
             and v.get("overlayIdentity") == 0 and bool(v.get("children")), f"title-{index}")
        action({"translationKey": "menu.singleplayer"})
        wait("/v0/ui/tree", lambda v: "SelectWorldScreen" in v.get("screenClass", ""), f"worlds-{index}")
        action({"worldId": "Mosaic MCP Alpha"}, "double_click")
        # The fresh hidden test save may show Vanilla's experimental-data confirmation.
        state = wait("/v0/session", lambda v: v.get("inWorld") or "BackupConfirmScreen" in v.get("screenClass", ""), f"entry-{index}")
        if "BackupConfirmScreen" in state.get("screenClass", ""):
            action({"translationKey": "selectWorld.backupJoinSkipButton"})
        wait("/v0/session", lambda v: v.get("inWorld") is True, f"joined-{index}", 300)
        wait("/v0/ui/tree", lambda v: not v.get("screenClass") and v.get("overlayIdentity") == 0, f"world-ui-{index}", 120)
        return observed("/v0/player", f"player-{index}")

    def leave(index):
        control()
        value = request(instance, "/v0/pipelines", {"steps": [{"type": "key.tap", "key": 256}], "timeoutMs": 5000}, lease=True)
        operation = value.get("operationId")
        if operation:
            completed = wait("/v0/operations/" + operation, lambda v: v.get("state") in ("completed", "failed", "cancelled"), f"escape-{index}")
            if completed.get("state") != "completed":
                raise RuntimeError("Player input operation did not complete: " + str(completed.get("errorCode")))
        wait("/v0/ui/tree", lambda v: "PauseScreen" in v.get("screenClass", ""), f"pause-{index}")
        action({"translationKey": "menu.returnToMenu"})
        wait("/v0/ui/tree", lambda v: "TitleScreen" in v.get("screenClass", "")
             and v.get("overlayIdentity") == 0, f"saved-title-{index}", 300)

    try:
        enter(1)
        leave(1)
        enter(2)
        leave(2)
        request(instance, "/v0/control/release", {}, lease=True)
        idle = observed("/v0/input/state", "released-input")
        if idle.get("pressedKeyCount") != 0 or idle.get("pressedButtonCount") != 0 or idle.get("mode") != "READ":
            raise RuntimeError("Input cleanup did not complete")
        report["releasedInput"] = idle
        report["status"] = "PASS"
    except Exception as error:
        report["status"] = "FAIL"
        report["error"] = str(error)
        raise
    finally:
        try:
            state = request(instance, "/v0/control/status")
            if state.get("takeoverActive") and not state.get("reconsentRequired"):
                request(instance, "/v0/control/release", {}, lease=True)
        except Exception as cleanup_error:
            report["cleanupError"] = str(cleanup_error)
        save(result / "summary.json", report)


def check(label):
    result = VALIDATION / "result"
    summary = read_json(result / label / "summary.json")
    if summary.get("status") != "PASS" or summary.get("jarSha256") != JAR_HASH:
        raise RuntimeError("Current real-client smoke is incomplete")
    read_path = result / "minimal-session-verified.json"
    minimum = read_json(read_path if read_path.exists() else result / "minimal-session.json")
    if minimum.get("mode") != "READ" or minimum.get("target") != TARGET:
        raise RuntimeError("Missing MCP-only READ evidence")
    for index in (1, 2):
        if read_json(result / label / f"escape-{index}.json").get("state") != "completed":
            raise RuntimeError("Input operation was not successful")
    released = summary["releasedInput"]
    if released.get("pressedKeyCount") != 0 or released.get("pressedButtonCount") != 0 or released.get("mode") != "READ":
        raise RuntimeError("Input cleanup is not proved")
    names = ["minimal", "mosaic"]
    names += [name for name in ("lifecycle_vanilla", "lifecycle_b")
              if (VALIDATION / "instance" / TARGET / name / "automation-process.json").exists()]
    for name in names:
        instance = VALIDATION / "instance" / TARGET / name
        process = read_json(instance / "automation-process.json")
        active = subprocess.check_output(["pwsh", "-NoProfile", "-Command",
                                         f"@(Get-Process -Id {process['pid']} -ErrorAction SilentlyContinue).Count"], text=True).strip()
        if active != "0" or (instance / "minecraft-protocol/endpoint.json").exists():
            raise RuntimeError("Owned Runtime has not shut down")
        if "Stopping!" not in (instance / "automation-stdout.log").read_text(encoding="utf-8", errors="replace"):
            raise RuntimeError("No normal client stop evidence")
        current_crashes = [path for path in (instance / "crash-reports").glob("*.txt")
                           if path.stat().st_mtime >= process["launchedAt"]]
        if current_crashes:
            raise RuntimeError("New crash reports belong to this launch")
        jars = list((instance / "mods").glob("*minecraft_protocol*.jar"))
        if len(jars) != 1 or digest(jars[0]) != JAR_HASH:
            raise RuntimeError("Installed alpha changed or duplicated")
        if name == "minimal" and len(list((instance / "mods").glob("*.jar"))) != 1:
            raise RuntimeError("Minimal baseline is not MCP-only")
        if name == "minimal" and minimum["runtimeConnection"]["pid"] != process["pid"]:
            raise RuntimeError("Minimal READ does not belong to the verified launch")
    count = storage_bytes()
    if count > MAX_BYTES:
        raise RuntimeError("MCP validation exceeds storage budget")
    outcome = {"status": "PASS", "snapshot": SNAPSHOT, "jarSha256": JAR_HASH,
               "minimalRead": True, "realClientFreshJoinSaveExitReenter": True,
               "storageBytes": count, "storageLimitBytes": MAX_BYTES,
               "ownedProcessesStopped": True, "endpointsRemoved": True,
               "snapshotReleased": pin("Unpin")["inUse"] is False,
               "scope": "MCP_ALPHA_INTEGRATION_NOT_FULL_MILESTONE"}
    save(result / "check.json", outcome)
    print(json.dumps(outcome))


def quit_client(instance):
    status = request(instance, "/v0/control/status")
    if status.get("reconsentRequired"):
        raise RuntimeError("Human revocation; do not automatically reacquire")
    if status.get("takeoverActive"):
        control = request(instance, "/v0/control/renew", {"ttlMs": 60000}, lease=True)
    else:
        control = request(instance, "/v0/control/acquire", {"ttlMs": 60000})
    save(instance / "automation-control.json", control)
    ui = request(instance, "/v0/ui/tree")
    if "TitleScreen" not in ui.get("screenClass", "") or ui.get("overlayIdentity") != 0:
        raise RuntimeError("Quit helper requires title screen; save and exit the world first")
    try:
        result = request(instance, "/v0/ui/action", {"action": "click", "selector": {"translationKey": "menu.quit"}}, lease=True)
    except RuntimeError:
        failure = read_json(instance / "automation-last-error.json")
        if failure.get("httpStatus") != 503 or failure.get("response", {}).get("error") != "RUNTIME_SHUTTING_DOWN":
            raise
        result = failure
    save(VALIDATION / "result" / (instance.name + "-quit.json"), result)
    print(json.dumps({"quitSubmitted": True, "shutdownConfirmed": False, "requiresProcessAndLogCheck": True}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "launch", "read", "acquire", "call", "release", "smoke", "check", "quit", "retire"))
    parser.add_argument("--stage", choices=("minimal", "mosaic", "lifecycle_b", "lifecycle_vanilla"), default="minimal")
    parser.add_argument("--path", default="/v0/session")
    parser.add_argument("--body")
    parser.add_argument("--output")
    args = parser.parse_args()
    if args.action == "check":
        check(args.output or "mosaic-smoke-ready")
        return
    if args.action == "prepare":
        prepare(args.stage)
        return
    instance = VALIDATION / "instance" / TARGET / args.stage
    if args.action == "retire":
        retire(instance, args.output)
        return
    if args.action == "launch":
        launch(instance)
        return
    if args.action == "smoke":
        if args.stage != "mosaic":
            raise ValueError("Only the isolated Mosaic fixture supports this player smoke")
        smoke(instance, args.output or "mosaic-smoke")
        return
    if args.action == "quit":
        quit_client(instance)
        return
    if args.action == "acquire":
        value = request(instance, "/v0/control/acquire", {"ttlMs": 60000})
        save(instance / "automation-control.json", value)
        print(json.dumps({"controlAcquired": True}))
        return
    if args.action == "release":
        value = request(instance, "/v0/control/release", {}, lease=True)
    elif args.action == "read":
        if not args.path.startswith("/v0/"):
            raise ValueError("Expected a Native V0 read route")
        value = request(instance, args.path)
    else:
        value = request(instance, args.path, json.loads(args.body or "{}"), lease=True)
    if args.output:
        output = VALIDATION / "result" / args.output
        if not output.resolve().is_relative_to((VALIDATION / "result").resolve()):
            raise ValueError("Evidence path escapes validation")
        used = storage_bytes()
        if used > MAX_BYTES:
            raise RuntimeError("MCP validation storage budget exceeded")
        save(output, value)
    print(json.dumps(value, ensure_ascii=False))


if __name__ == "__main__":
    main()
