"""Checks the YAML content files below a folder against the MCHeli: Revived JSON Schemas.

Usage: validate.py <folder> <schemas>

<schemas> is a URL ending in a slash or a local folder holding <type>.schema.json. Every
assets/mcheli/<type folder>/**/*.yml below <folder> is checked with check-jsonschema against the schema of
its type. Problems are printed, written as GitHub annotations when running in Actions, and summarised in the
job summary. The exit code is 1 when any file has a problem.
"""
import json
import os
import pathlib
import subprocess
import sys

# content type folder -> schema file name, as the game names them (see ContentType and JsonSchemas)
TYPES = {
    "helicopters": "helicopter", "planes": "plane", "ships": "ship", "tanks": "tank", "vehicles": "vehicle",
    "weapons": "weapon", "throwable": "throwable", "hud": "hud",
}
IN_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"


def content_files(root, folder, suffix):
    found = []
    for assets in sorted(root.rglob("assets")):
        type_dir = assets / "mcheli" / folder
        if type_dir.is_dir():
            found += sorted(path for path in type_dir.rglob("*" + suffix) if path.is_file())
    return found


def schema_location(schemas, name):
    if schemas.startswith(("http://", "https://")):
        return schemas.rstrip("/") + "/" + name + ".schema.json"
    return str(pathlib.Path(schemas) / (name + ".schema.json"))


def annotate(kind, filename, message):
    if IN_ACTIONS:
        text = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::{kind} file={filename},title=MCHeli content::{text}")


def check(files, schema):
    result = subprocess.run(
        [sys.executable, "-m", "check_jsonschema", "-o", "json", "--schemafile", schema, *map(str, files)],
        capture_output=True, text=True)
    try:
        report = json.loads(result.stdout)
    except json.JSONDecodeError:
        print(result.stdout + result.stderr, file=sys.stderr)
        raise SystemExit(f"check-jsonschema failed for {schema}")
    problems = [(error["filename"], f'{error["path"]}: {error["message"]}') for error in report.get("errors", [])]
    problems += [(error["filename"], error["message"]) for error in report.get("parse_errors", [])]
    return problems


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    schemas = sys.argv[2] if len(sys.argv) > 2 else \
        "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/"
    checked, problems, rows = 0, [], []
    for folder, name in TYPES.items():
        for ignored in content_files(root, folder, ".yaml"):
            message = "the game only reads .yml files; rename this file to .yml"
            print(f"{ignored}: {message}")
            annotate("warning", ignored, message)
        files = content_files(root, folder, ".yml")
        legacy = len(content_files(root, folder, ".txt"))
        if not files and not legacy:
            continue
        found = check(files, schema_location(schemas, name)) if files else []
        for filename, message in found:
            print(f"{filename}: {message}")
            annotate("error", filename, message)
        checked += len(files)
        problems += found
        rows.append(f"| {folder} | {len(files)} | {len({f for f, _ in found})} | {legacy} |")
    print(f"Checked {checked} YAML files: {len(problems)} problems.")
    if legacy_total := sum(int(row.split("|")[4]) for row in rows):
        print(f"{legacy_total} legacy TXT files are not checked; convert them to YAML with /mcheli dumpyaml.")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("### MCHeli content\n\n| Folder | YAML files | With problems | TXT (not checked) |\n")
            out.write("|---|---|---|---|\n" + "\n".join(rows) + "\n\n")
            out.write(f"{len(problems)} problems in {checked} files.\n")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
