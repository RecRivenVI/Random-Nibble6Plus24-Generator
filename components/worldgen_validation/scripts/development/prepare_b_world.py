"""Create a fresh B development map from a closed, verified B server's identity only."""

import argparse
import json
import shutil
import struct
from pathlib import Path

from prepare_target_only_world import read_nbt, write_nbt


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--name", default="Mosaic B Milestone Fresh")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[4]
    source = args.source.resolve()
    server = repository / "validations/integration-production_lifecycle/instance/26.3-fabric/server"
    saves = repository / "instances/26.3-fabric/client/saves"
    destination = (saves / args.name).resolve()
    if source.parent != server.resolve() or destination.parent != saves.resolve() or destination.exists():
        raise ValueError("Only create a new save from this project's verified B server; never overwrite")
    marker = source / "mosaic-development-b.json"
    if json.loads(marker.read_text(encoding="utf-8")) != {"schema": 1, "pipeline": "26.3-lightweight-b-1"}:
        raise ValueError("Source is not a B development save")
    settings = source / "data/minecraft/world_gen_settings.dat"
    settings_name, settings_root = read_nbt(settings)
    count = 0

    def validate(compound):
        nonlocal count
        for name, (kind, value) in compound.items():
            if kind != 10:
                continue
            if name == "generator":
                if value.get("type") != (8, "randomnibble6plus24generator:development_b"):
                    raise ValueError("Source generator is not development_b")
                if value["mosaic_profile"][1]["feature_ordering_algorithm_version"] != (3, struct.pack(">i", 2)):
                    raise ValueError("Source profile is not Ordering 2")
                count += 1
            validate(value)

    validate(settings_root)
    if count != 3:
        raise ValueError("Expected three B development dimensions")
    profile = source / "data/randomnibble6plus24generator/mosaic_world_profile.dat"
    profile_name, profile_root = read_nbt(profile)
    if profile_root["data"][1]["feature_ordering_algorithm_version"] != (3, struct.pack(">i", 2)):
        raise ValueError("Saved profile mismatch")
    level_name, level_root = read_nbt(source / "level.dat")
    level = level_root["Data"][1]
    level["LevelName"] = 8, args.name
    level["GameType"] = 3, struct.pack(">i", 1)
    level["allowCommands"] = 1, b"\x01"
    level.pop("Player", None)
    write_nbt(destination / "level.dat", level_name, level_root)
    write_nbt(destination / "data/minecraft/world_gen_settings.dat", settings_name, settings_root)
    write_nbt(destination / "data/randomnibble6plus24generator/mosaic_world_profile.dat", profile_name, profile_root)
    shutil.copytree(source / "datapacks", destination / "datapacks")
    shutil.copyfile(marker, destination / marker.name)
    print(f"Prepared {destination}; development_b x3; Creative/commands; terrain, players, Overlay and index not copied")


if __name__ == "__main__":
    main()
