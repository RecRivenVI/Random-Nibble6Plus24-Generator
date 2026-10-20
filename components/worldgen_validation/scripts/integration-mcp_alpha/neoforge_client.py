"""Install the pinned final alpha into a project-owned native NeoForge client launch."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

import mcp_alpha as alpha


def native_mod_ids(log):
    return set(re.findall(r"^\s+[^\r\n]+ \(([a-z0-9_.-]+)\)\s*$", log, re.MULTILINE))


def confirm_minimal():
    instance = alpha.VALIDATION / "instance" / alpha.TARGET / "minimal"
    observed = alpha.read_json(alpha.VALIDATION / "result/minimal-neoforge-session.json")
    process = alpha.read_json(instance / "automation-process.json")
    if observed["target"] != alpha.TARGET or observed["runtimeConnection"]["pid"] != process["pid"]:
        raise RuntimeError("Minimal READ does not match the owned NeoForge process")
    if (instance / "minecraft-protocol/endpoint.json").exists():
        raise RuntimeError("Normally close minimal Runtime before adding product")
    live = subprocess.check_output(["pwsh", "-NoProfile", "-Command",
                                   f"@(Get-Process -Id {process['pid']} -ErrorAction SilentlyContinue).Count"], text=True).strip()
    log = (instance / "automation-stdout.log").read_text(encoding="utf-8")
    mods = native_mod_ids(log)
    jars = list((instance / "mods").glob("*.jar"))
    if (live != "0" or "Stopping!" not in log or list((instance / "crash-reports").glob("*.txt"))
            or mods != {"minecraft", "neoforge", "minecraft_protocol_probe"}
            or len(jars) != 1 or alpha.digest(jars[0]) != alpha.JAR_HASH):
        raise RuntimeError("Minimal native loader/JAR/exit proof is incomplete")
    result = {"status": "PASS", "target": alpha.TARGET, "pid": process["pid"],
              "snapshot": alpha.SNAPSHOT, "jarSha256": alpha.JAR_HASH,
              "mods": sorted(mods), "minimalUserRead": True, "normalExit": True,
              "scope": "NATIVE_MODDEV_LOADER_FINAL_MCP_JAR_NOT_OFFICIAL_INSTALLER_PROOF"}
    alpha.save(alpha.VALIDATION / "result/minimal-neoforge-check.json", result)
    return result


def prepare_native(stage):
    artifact = alpha.bundle()
    alpha.pin("Pin")
    if stage != "minimal":
        confirm_minimal()
    target_root = alpha.VALIDATION / "instance" / alpha.TARGET
    if list(target_root.glob("*/minecraft-protocol/endpoint.json")):
        raise RuntimeError("Close owned NeoForge endpoints before updating launch files")
    bootstrap = alpha.VALIDATION / "result/native-launch" / alpha.TARGET / "client.json"
    exported = alpha.read_json(bootstrap)
    facts = dict(line.split("=", 1) for line in (alpha.ROOT / "versions" / alpha.TARGET / "target.properties")
                 .read_text(encoding="utf-8").splitlines() if "=" in line)
    if (exported["target"] != alpha.TARGET or exported["loader"] != facts["loader_version"]
            or exported["main"] != "net.neoforged.devlaunch.Main"):
        raise RuntimeError("Export the current native NeoForge client launch first")
    instance = target_root / stage
    mods = instance / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    jar = alpha.BUNDLE / artifact["fileName"]
    if stage == "minimal" and any(path.name != jar.name for path in mods.glob("*.jar")):
        raise RuntimeError("Minimal NeoForge instance is not MCP-only")
    shutil.copy2(jar, mods / jar.name)
    if alpha.digest(mods / jar.name) != alpha.JAR_HASH:
        raise RuntimeError("Installed NeoForge alpha hash differs")
    # Native ModDev's real loader/runtime stays unchanged. Do not discover development product
    # folders alongside installed release JARs, especially in the MCP-only minimal stage.
    args = [value for value in exported["jvmArguments"]
            if not value.startswith(("-Dfml.modFolders=", "-Dvalidation.", "-Dmosaic.mcp.launchExport=", "-Xmx", "-Xms", "-XX:ActiveProcessorCount="))]
    args += ["-Xmx4G", "-Xms256M", "-XX:ActiveProcessorCount=4", "-Dminecraft.protocol.probe.port=0"]
    classpath = [entry for entry in exported["classpath"].split(os.pathsep)
                 if not any(part in entry.replace("\\", "/") for part in (
                     "/build/classes/java/main", "/build/classes/java/probe", "/build/resources/main",
                     "/build/resources/probe", "/build/embeddedProducts", "/build/classes/java/embeddedProducts",
                     "/build/resources/embeddedProducts", "/components/worldgen_contract/build/"))]
    native, = exported["gameArguments"]
    if not native.startswith("@"):
        raise RuntimeError("Expected the native NeoForge program argument file")
    source = Path(native[1:]).resolve()
    allowed = (alpha.ROOT / "versions" / alpha.TARGET / "build/moddev").resolve()
    if not source.is_relative_to(allowed):
        raise RuntimeError("Foreign native client argument file")
    lines = [line.strip().strip('"') for line in source.read_text(encoding="utf-8").splitlines()
             if line.strip() and not line.startswith("#")]
    if lines[0] != "net.neoforged.fml.startup.Client":
        raise RuntimeError("Not the native NeoForge client entrypoint")
    for name, value in (("--username", "MosaicAutomation"), ("--width", "1920"), ("--height", "1080")):
        index = lines.index(name)
        lines[index + 1] = value
    if "--quickPlaySingleplayer" in lines:
        raise RuntimeError("MCP controls world entry; do not inherit Quick Play")
    lines += ["--gameDir", str(instance)]
    owned = instance / "automation-native-client.args"
    owned.write_text("\n".join(json.dumps(value) for value in lines) + "\n", encoding="utf-8")
    args += ["-cp", os.pathsep.join(classpath), exported["main"]] + lines
    if stage != "minimal":
        product_facts = dict(line.split("=", 1) for line in (alpha.ROOT / "gradle.properties")
                             .read_text(encoding="utf-8").splitlines() if "=" in line)
        product = alpha.ROOT / "versions" / alpha.TARGET / "build/libs" / (
            f"{product_facts['mod_id']}-{alpha.TARGET}-{product_facts['mod_version']}.jar")
        shutil.copy2(product, mods / product.name)
        vanilla = stage == "lifecycle_vanilla"
        alpha.prepare_world(instance, vanilla=vanilla, name=(
            "MCP Vanilla Lifecycle" if vanilla else "MCP B Lifecycle" if stage == "lifecycle_b" else "Mosaic MCP Alpha"))
        if not vanilla:
            args.insert(0, "-D" + product_facts["mod_id"] + ".developmentB=true")
        if stage.startswith("lifecycle_"):
            target = alpha.ROOT / "versions" / alpha.TARGET
            with zipfile.ZipFile(mods / (product_facts["mod_id"] + "_probe.jar"), "w", zipfile.ZIP_DEFLATED) as archive:
                for folder in (target / "build/classes/java/probe", target / "build/resources/probe"):
                    for entry in sorted(folder.rglob("*")):
                        if entry.is_file():
                            archive.write(entry, entry.relative_to(folder).as_posix())
            source_hash = hashlib.sha256()
            for entry in sorted((target / "src").rglob("*")):
                if entry.is_file():
                    source_hash.update(entry.relative_to(target).as_posix().encode())
                    source_hash.update(entry.read_bytes())
            prefix = "-D" + product_facts["mod_id"] + ".clientLifecycle."
            args[:0] = ["-Dvalidation.name=integration-mcp_alpha", prefix + "enabled=true",
                        prefix + "mosaic=" + str(not vanilla).lower(),
                        prefix + "directory=" + str(instance / "project-observer"),
                        prefix + "inputHash=" + source_hash.hexdigest(),
                        "-Dminecraft.protocol.scopes=read,ui,control,input,command,capture,diagnostics"]
    (instance / "options.txt").write_text(
        "onboardAccessibility:false\nfullscreen:false\noverrideWidth:1920\noverrideHeight:1080\n"
        "soundCategory_master:0.0\nrenderDistance:2\nsimulationDistance:2\nmaxFps:30\n", encoding="utf-8")
    alpha.save(instance / "automation-launch.json", {"java": exported["java"], "args": args, "stage": stage,
               "snapshot": alpha.SNAPSHOT, "jarSha256": alpha.JAR_HASH, "nativeBootstrap": str(bootstrap),
               "nativeBootstrapSha256": alpha.digest(bootstrap), "installedProductNotDevelopmentFolders": True})
    print(json.dumps({"prepared": str(instance), "externalMods": [path.name for path in mods.glob("*.jar")]}))
