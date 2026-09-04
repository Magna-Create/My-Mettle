#!/usr/bin/env python3
"""Fail CI when the Context Module author docs drift from local links or source names."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs" / "n-bio-vnext" / "context-modules"

REQUIRED_DOCS = {
    "README.md",
    "QUICKSTART.md",
    "AUTHORING.md",
    "CONCEPTS.md",
    "REFERENCE.md",
    "EXAMPLES.md",
    "VERSIONING_AND_REPLAY.md",
    "TROUBLESHOOTING.md",
}

REQUIRED_SYMBOLS = {
    "ContextFeatureDefinitionV7E",
    "ContextFeatureKey",
    "ContextFeatureEvidenceV7E",
    "ContextEvidenceMissingness",
    "ContextModuleV7E",
    "ContextModuleProviderV7E",
    "ContextModuleDescriptor",
    "ContextModuleStateV7E",
    "ContextModuleStateCodecV7E",
    "ContextReadViewV1",
    "ContextReadCapability",
    "ContextModuleResultV7E",
    "ContextSignalV1",
    "ContextSignalTarget",
    "ContextSignalTargetPolicyV1",
    "ContextSignalEffectRepresentation",
    "ContextEvidenceMaturity",
    "ProductionContextModuleRegistryV7E",
    "ProductionContextFeaturesV7E",
    "ContextModuleContractTckV1",
    "EpisodeAssociationModuleV1",
    "ObservationVarianceAssociationModuleV1",
}

FORBIDDEN_FICTIONAL_API = {
    "contextmodule.json",
    "ActivityContextDefinition",
    "onSignalRejected",
    "onAddEvidence",
    "confidence=0.8",
    "onCreate()",
    "onDestroy()",
    "publish(",
}

LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def main() -> int:
    errors: list[str] = []
    present = {path.name for path in DOCS.glob("*.md")}
    for missing in sorted(REQUIRED_DOCS - present):
        fail(f"Missing required author document: {missing}", errors)

    markdown_files = sorted(DOCS.rglob("*.md"))
    combined_docs = "\n".join(path.read_text(encoding="utf-8") for path in markdown_files)

    for path in markdown_files:
        text = path.read_text(encoding="utf-8")
        for raw_target in LINK_PATTERN.findall(text):
            target = raw_target.strip().split("#", 1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                fail(f"Broken internal link in {path.relative_to(ROOT)}: {raw_target}", errors)

    kotlin_text = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((ROOT / "app" / "src").rglob("*.kt"))
    )
    for symbol in sorted(REQUIRED_SYMBOLS):
        if symbol not in combined_docs:
            fail(f"Critical source symbol is not named in author docs: {symbol}", errors)
        if symbol not in kotlin_text:
            fail(f"Documented source symbol no longer exists: {symbol}", errors)

    for fictional in sorted(FORBIDDEN_FICTIONAL_API):
        if fictional in combined_docs:
            fail(f"Fictional/rejected API name appears in author docs: {fictional}", errors)

    for production_id in (
        "context.illness.episode.v1",
        "context.time_pressure.observation_variance.v1",
    ):
        if production_id not in kotlin_text or production_id not in combined_docs:
            fail(f"Production module ID is missing from source or docs: {production_id}", errors)

    if errors:
        print("Context Module documentation verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Context Module documentation verified: {len(markdown_files)} pages, "
        f"{len(REQUIRED_SYMBOLS)} source symbols, internal links resolved."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
