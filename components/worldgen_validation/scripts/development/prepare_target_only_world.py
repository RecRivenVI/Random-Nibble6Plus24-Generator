"""Create a fresh hidden Mosaic save from identity metadata, never copy terrain or players."""

import argparse
import gzip
import json
import shutil
import struct
from pathlib import Path


def read_nbt(path):
    data = memoryview(gzip.decompress(path.read_bytes()))
    offset = 0

    def take(size):
        nonlocal offset
        value = bytes(data[offset:offset + size])
        offset += size
        return value

    def number(fmt):
        return struct.unpack(fmt, take(struct.calcsize(fmt)))[0]

    def text():
        return take(number(">H")).decode("utf-8")

    def payload(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind])
        if kind == 8:
            return text()
        if kind == 9:
            element, count = number(">B"), number(">i")
            return element, [payload(element) for _ in range(count)]
        if kind == 10:
            entries = {}
            while (child := number(">B")) != 0:
                name = text()
                entries[name] = child, payload(child)
            return entries
        if kind in (7, 11, 12):
            count = number(">i")
            return count, take(count * {7: 1, 11: 4, 12: 8}[kind])
        raise ValueError(f"Unsupported NBT tag {kind}")

    kind, name = number(">B"), text()
    root = payload(kind)
    if kind != 10 or offset != len(data):
        raise ValueError(f"Invalid NBT root: {path}")
    return name, root


def write_nbt(path, name, root):
    def text(value):
        encoded = value.encode("utf-8")
        return struct.pack(">H", len(encoded)) + encoded

    def payload(kind, value):
        if kind in (1, 2, 3, 4, 5, 6):
            return value
        if kind == 8:
            return text(value)
        if kind == 9:
            element, values = value
            return struct.pack(">Bi", element, len(values)) + b"".join(payload(element, item) for item in values)
        if kind == 10:
            return b"".join(bytes([child]) + text(key) + payload(child, item) for key, (child, item) in value.items()) + b"\0"
        count, raw = value
        return struct.pack(">i", count) + raw

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(b"\x0a" + text(name) + payload(10, root), mtime=0))
    if read_nbt(path) != (name, root):
        raise ValueError(f"NBT round-trip failed: {path}")


def replace_profiles(compound):
    found = 0
    for name, (kind, value) in compound.items():
        if kind == 10:
            if name == "mosaic_profile":
                value["feature_ordering_algorithm_version"] = 3, struct.pack(">i", 2)
                found += 1
            found += replace_profiles(value)
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--name", default="Mosaic Target Only Fresh")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[4]
    saves = repository / "instances/26.3-fabric/client/saves"
    source = args.source.resolve()
    destination = (saves / args.name).resolve()
    if source.parent != saves.resolve() or destination.parent != saves.resolve() or destination.exists():
        raise ValueError("Only create a new named save in this project's 26.3 client; never overwrite one")
    # Minecraft 26.3 persists the authoritative worldgen settings separately from level.dat.
    settings = source / "data/minecraft/world_gen_settings.dat"
    profile = source / "data/randomnibble6plus24generator/mosaic_world_profile.dat"
    settings_name, settings_root = read_nbt(settings)
    profiles = replace_profiles(settings_root)
    if profiles != 3:
        raise ValueError(f"Expected three Mosaic dimensions, found {profiles}")
    profile_name, profile_root = read_nbt(profile)
    profile_root["data"][1]["feature_ordering_algorithm_version"] = 3, struct.pack(">i", 2)
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
    for path in (destination / "datapacks").rglob("*.json"):
        document = json.loads(path.read_text(encoding="utf-8"))

        def rewrite(value):
            if isinstance(value, dict):
                if "mosaic_profile" in value:
                    value["mosaic_profile"]["feature_ordering_algorithm_version"] = 2
                for child in value.values():
                    rewrite(child)
            elif isinstance(value, list):
                for child in value:
                    rewrite(child)

        rewrite(document)
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared: {destination}; 3 Mosaic dimensions; ordering=2; Creative; cheats; no terrain/player/index copied")


if __name__ == "__main__":
    main()
