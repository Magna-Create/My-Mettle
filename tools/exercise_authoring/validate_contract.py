#!/usr/bin/env python3
"""Validate the external exercise-authoring schema and canonical example structurally."""

from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = ROOT / "docs" / "n-bio-vnext" / "exercise-import.schema.json"
EXAMPLE_PATH = ROOT / "docs" / "n-bio-vnext" / "exercise-import-example.json"


def main() -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    example = json.loads(EXAMPLE_PATH.read_text(encoding="utf-8"))

    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    errors = sorted(validator.iter_errors(example), key=lambda error: list(error.absolute_path))
    if errors:
        formatted = "\n".join(
            f"- {'/'.join(map(str, error.absolute_path)) or '<root>'}: {error.message}"
            for error in errors
        )
        raise SystemExit(f"Exercise authoring example does not conform to Draft 2020-12 schema:\n{formatted}")

    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise SystemExit("Exercise authoring schema must remain Draft 2020-12.")
    if example.get("format") != "my-mettle-exercise-authoring" or example.get("formatVersion") != 1:
        raise SystemExit("Canonical example must declare exercise-authoring format v1.")

    print("Exercise authoring Draft 2020-12 schema and canonical example are structurally valid.")


if __name__ == "__main__":
    main()
