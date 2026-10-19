"""Prepare closed, repository-owned clients without changing worlds or game controls."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import tomllib


def update_options(text, mode):
    width, height = (1920, 1080) if mode == "automatic" else (2560, 1440)
    values = {
        "fullscreen": "false",
        "overrideWidth": str(width),
        "overrideHeight": str(height),
        "soundCategory_master": "0.0" if mode == "automatic" else "0.5",
    }
    if mode == "acceptance":
        values["soundCategory_music"] = "0.0"
    output = []
    for line in text.splitlines():
        if line.partition(":")[0] not in values:
            output.append(line)
    output.extend(f"{key}:{value}" for key, value in values.items())
    return "\n".join(output) + "\n"


def owned_client_running(commands, repository, directory):
    root = str(repository).replace("\\", "/").casefold()
    instance = str(directory).replace("\\", "/").casefold()
    for command in commands:
        normalized = (command or "").replace("\\", "/").casefold()
        if instance in normalized:
            return True
        # Forge/NeoForge keep --gameDir inside this repository's generated argument file.
        if root in normalized and re.search(r"knotclient|net\.minecraft\.client\.main\.main|clientrunprogramargs\.txt", normalized):
            return True
    return False


def client_directory(repository, target, mode, validation):
    if Path(target).name != target or not (repository / "versions" / target / "target.properties").is_file():
        raise ValueError("Unregistered target")
    if mode == "acceptance":
        if validation:
            raise ValueError("Acceptance must not reuse an automatic validation instance")
        return repository / "instances" / target / "client"
    if not validation or Path(validation).name != validation:
        raise ValueError("Automatic clients require a validation name")
    specification = repository / "validations" / validation / "validation.toml"
    data = tomllib.loads(specification.read_text(encoding="utf-8"))
    settings = data.get("instances", {}).get("client", {})
    if target not in data["targets"] or (settings.get("width"), settings.get("height")) != (1920, 1080):
        raise ValueError("Automatic validation must declare a 1920x1080 client for this target")
    return repository / "validations" / validation / "instance" / target / "client"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", required=True)
    parser.add_argument("--mode", required=True, choices=("automatic", "acceptance"))
    parser.add_argument("--validation")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[4]
    directory = client_directory(repository, args.target, args.mode, args.validation).resolve()
    if not directory.is_relative_to(repository) or any(path.is_symlink() for path in directory.parents):
        raise ValueError("Refusing linked or external instance")
    # The game writes options on exit; changing a live client's file would race that save.
    query = subprocess.check_output(
        ["pwsh", "-NoProfile", "-Command",
         "@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='javaw.exe'\" | "
         "ForEach-Object { $_.CommandLine }) | ConvertTo-Json -Compress"],
        text=True, encoding="utf-8",
    )
    commands = json.loads(query) if query.strip() else []
    if isinstance(commands, str):
        commands = [commands]
    if owned_client_running(commands, repository, directory):
        raise RuntimeError("Client instance is running; save and close it before preparation")
    directory.mkdir(parents=True, exist_ok=True)
    options = directory / "options.txt"
    text = options.read_text(encoding="utf-8") if options.exists() else "onboardAccessibility:false\n"
    options.write_text(update_options(text, args.mode), encoding="utf-8", newline="\n")
    print(json.dumps({"directory": str(directory), "mode": args.mode, "preferencesPrepared": True}))


if __name__ == "__main__":
    main()
