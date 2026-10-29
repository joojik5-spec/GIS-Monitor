#!/usr/bin/env python3
"""Prepare a Solmi Sensor update bundle without publishing it."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil


APP_ID = "ru.solmi.sensor"
APK_NAME = "solmi-sensor.apk"
HISTORY_LIMIT = 40
MIN_SDK = 26


def normalized_notes(raw: str) -> str:
    lines = []
    for raw_line in raw.splitlines():
        line = raw_line.strip().lstrip("•-").strip()
        if line and line not in lines:
            lines.append(line)
    return "\n".join(f"• {line}" for line in lines) or "• Исправления и улучшения"


def load_previous(path: Path | None) -> dict:
    if path is None or not path.is_file():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return value if isinstance(value, dict) else {}


def prepare_bundle(apk: Path, output_dir: Path, notes_file: Path, run_number: int,
                   previous_manifest: Path | None = None) -> dict:
    if run_number <= 0:
        raise ValueError("run number must be positive")
    if not apk.is_file():
        raise FileNotFoundError(apk)

    output_dir.mkdir(parents=True, exist_ok=True)
    output_apk = output_dir / APK_NAME
    if apk.resolve() != output_apk.resolve():
        shutil.copy2(apk, output_apk)

    raw_notes = notes_file.read_text(encoding="utf-8") if notes_file.is_file() else ""
    notes = normalized_notes(raw_notes)
    version_code = 1300 + run_number
    version_name = f"1.3.0-Solmi.{run_number}"

    previous = load_previous(previous_manifest)
    history = [
        item for item in previous.get("history", [])
        if isinstance(item, dict) and item.get("code") != version_code
    ]
    if history and history[-1].get("notes") == notes:
        notes = "• Исправления и улучшения"
    history = (history + [{"code": version_code, "name": version_name, "notes": notes}])[-HISTORY_LIMIT:]

    payload = output_apk.read_bytes()
    manifest = {
        "schema": 1,
        "versionName": version_name,
        "versionCode": version_code,
        "minSdk": MIN_SDK,
        "notes": notes,
        "history": history,
        "artifacts": [{
            "applicationId": APP_ID,
            "file": APK_NAME,
            "size": len(payload),
            "sha256": hashlib.sha256(payload).hexdigest(),
            "url": APK_NAME,
        }],
    }
    (output_dir / "update-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--notes-file", required=True, type=Path)
    parser.add_argument("--run-number", required=True, type=int)
    parser.add_argument("--previous-manifest", type=Path)
    args = parser.parse_args()
    prepare_bundle(args.apk, args.output_dir, args.notes_file, args.run_number, args.previous_manifest)


if __name__ == "__main__":
    main()
