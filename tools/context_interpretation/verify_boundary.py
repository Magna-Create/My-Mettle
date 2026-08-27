#!/usr/bin/env python3
"""Static architecture guard for the 7A.5 language-model privacy/capability boundary."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NANO = ROOT / "app/src/main/java/dev/kian/mymettle/context/NanoNoteInterpreter.kt"
BOUNDARY = ROOT / "app/src/main/java/dev/kian/mymettle/context/NoteInterpreter.kt"
COORDINATOR = ROOT / "app/src/main/java/dev/kian/mymettle/context/ContextInterpretationCoordinator.kt"


def fail(message: str) -> None:
    raise SystemExit(f"7A.5 context architecture violation: {message}")


def main() -> None:
    nano = NANO.read_text(encoding="utf-8")
    boundary = BOUNDARY.read_text(encoding="utf-8")
    coordinator = COORDINATOR.read_text(encoding="utf-8")

    forbidden_nano_tokens = {
        "HealthConnect": "Nano must not receive Health Connect records.",
        "HealthObservation": "Nano must not receive structured physiological records.",
        "RoomInferenceRepository": "Nano must not receive N-BIO posterior/inference state.",
        "HistoryRepository": "Nano must not receive workout/exercise history.",
        "BodyMeasurement": "Nano must not receive structured body measurements.",
        ".download(": "Save-time interpretation must never trigger a Prompt API model download.",
        "generateContent(request.text": "Free-form model output is not the 7A.5 extraction path.",
    }
    for token, reason in forbidden_nano_tokens.items():
        if token in nano:
            fail(f"{reason} Found {token!r} in {NANO.relative_to(ROOT)}")

    forbidden_import_prefixes = (
        "dev.kian.mymettle.history.",
        "dev.kian.mymettle.inference.",
        "dev.kian.mymettle.profile.",
        "dev.kian.mymettle.data.local.entity.Health",
    )
    imports = re.findall(r"^import\s+([^\s]+)", nano, flags=re.MULTILINE)
    for imported in imports:
        if imported.startswith(forbidden_import_prefixes):
            fail(f"Nano adapter imports forbidden data boundary {imported!r}.")

    request_match = re.search(
        r"data class NoteInterpretationRequest\s*\((.*?)\)\s*\{",
        boundary,
        flags=re.DOTALL,
    )
    if request_match is None:
        fail("NoteInterpretationRequest declaration was not found.")
    request_body = request_match.group(1)
    for required in ("rawText", "scope", "exerciseName"):
        if required not in request_body:
            fail(f"NoteInterpretationRequest is missing bounded field {required}.")
    for forbidden in ("health", "history", "posterior", "bodyMeasurement", "heartRate", "hrv"):
        if forbidden.lower() in request_body.lower():
            fail(f"NoteInterpretationRequest contains forbidden structured field {forbidden}.")

    if "generateTypedContentRequest" not in nano or "@Generable" not in nano:
        fail("Nano extraction must remain schema-constrained Structured Output.")
    if "enableThinking = false" not in nano:
        fail("Thinking Mode must remain disabled for note extraction.")
    if "structuredOutputAvailable != true" not in nano:
        fail("Nano must fail closed when Structured Output is unavailable.")
    if "InterpretationExecutionOutcome.FALLBACK_SUCCESS" not in coordinator:
        fail("Coordinator no longer records deterministic fallback provenance.")

    print("7A.5 Nano privacy, Structured Output, no-download and fallback boundaries are intact.")


if __name__ == "__main__":
    main()
