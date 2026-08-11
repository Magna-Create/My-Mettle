#!/usr/bin/env python3
"""Generate the deliberately selected My Mettle runtime biology assets.

The evidence corpus remains under docs/research. Only canonical anatomy, explicit segment policy,
and the selected healthy-adult-male profile cross the runtime boundary.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = REPO_ROOT / "docs/research/skeletal-muscle-reference/data"
OUTPUT_DIR = REPO_ROOT / "app/src/main/assets/reference"

ANATOMY_SOURCE = DATA_DIR / "anatomical_units_v0_1.csv"
SEGMENT_SOURCE = DATA_DIR / "segment_overrides_v0_1.csv"
PROFILE_SOURCE = DATA_DIR / "reference_profile_healthy_adult_male_v0_1.csv"
SEGMENT_POLICY_SOURCE = DATA_DIR / "segment_reference_policy_v0_1.csv"
DERIVED_SOURCE = DATA_DIR / "derived_reference_values_v0_1.csv"
SEGMENT_ARCHITECTURE_SOURCE = DATA_DIR / "segment_architecture_observations_v0_1.csv"

SOURCE_FILES = [
    ANATOMY_SOURCE,
    SEGMENT_SOURCE,
    PROFILE_SOURCE,
    SEGMENT_POLICY_SOURCE,
    DERIVED_SOURCE,
    SEGMENT_ARCHITECTURE_SOURCE,
]


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def nullable(value: str) -> str | None:
    value = value.strip()
    return value or None


def number(value: str) -> float | None:
    value = value.strip()
    return float(value) if value else None


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def whole_segment(unit: dict[str, str]) -> dict[str, Any]:
    return {
        "id": f"{unit['unit_id']}_whole",
        "muscleId": unit["unit_id"],
        "name": "Whole muscle",
        "segmentType": "WHOLE_MUSCLE",
        "anatomicalStatus": "WHOLE_FORMAL_MUSCLE",
        "statePolicy": "TRACK",
        "verificationStatus": unit["verification_status"],
    }


def build_anatomy() -> tuple[dict[str, Any], dict[str, dict[str, Any]], set[str]]:
    overrides_by_unit: dict[str, list[dict[str, str]]] = defaultdict(list)
    for override in rows(SEGMENT_SOURCE):
        overrides_by_unit[override["unit_id"]].append(override)

    muscles: list[dict[str, Any]] = []
    segment_by_id: dict[str, dict[str, Any]] = {}
    overridden_units = set(overrides_by_unit)

    for unit in rows(ANATOMY_SOURCE):
        overrides = overrides_by_unit.get(unit["unit_id"], [])
        segments = [
            {
                "id": override["segment_id"],
                "muscleId": override["unit_id"],
                "name": override["segment_name"],
                "segmentType": override["segment_type"],
                "anatomicalStatus": override["anatomical_status"],
                "statePolicy": override["state_policy"],
                "verificationStatus": override["verification_status"],
            }
            for override in overrides
        ] or [whole_segment(unit)]

        for segment in segments:
            if segment["id"] in segment_by_id:
                raise ValueError(f"Duplicate segment id: {segment['id']}")
            segment_by_id[segment["id"]] = segment

        muscles.append(
            {
                "id": unit["unit_id"],
                "name": unit["canonical_name"],
                "region": unit["region"].upper(),
                "unitKind": unit["unit_kind"],
                "lateralityModel": unit["laterality_model"],
                "instancePattern": nullable(unit["instance_pattern"]),
                "verificationStatus": unit["verification_status"],
                "segments": segments,
            }
        )

    if len(muscles) != 142:
        raise ValueError(f"Expected 142 muscles, generated {len(muscles)}")
    if len(segment_by_id) != 164:
        raise ValueError(f"Expected 164 segments, generated {len(segment_by_id)}")

    return (
        {
            "schemaVersion": 1,
            "datasetVersion": "anatomy_v0_1",
            "muscles": muscles,
        },
        segment_by_id,
        overridden_units,
    )


def estimate(value: float, uncertainty: float | None, source_id: str) -> dict[str, Any]:
    return {
        "value": value,
        "uncertainty": uncertainty,
        "sourceKind": "DIRECT_MEASUREMENT",
        "sourceId": source_id,
        "modelVersion": None,
    }


def empty_prior(
    profile_id: str,
    muscle_id: str,
    segment_id: str | None,
    absolute_share_policy: dict[str, Any],
    availability: str,
    uncertainty_class: str,
    selection_rule: str,
) -> dict[str, Any]:
    target_kind = "SEGMENT" if segment_id else "MUSCLE"
    target_id = segment_id or muscle_id
    return {
        "id": f"{profile_id}:{target_kind.lower()}:{target_id}",
        "targetKind": target_kind,
        "targetId": target_id,
        "muscleId": muscle_id,
        "segmentId": segment_id,
        "volumeCm3": None,
        "optimalFibreLengthMm": None,
        "pennationDeg": None,
        "geometricPcsaCm2": None,
        "effectivePcsaCm2": None,
        "structuralCapacityIndex": None,
        "absoluteSharePolicy": absolute_share_policy,
        "availability": availability,
        "uncertaintyClass": uncertainty_class,
        "selectionRule": selection_rule,
    }


def build_profile(
    segment_by_id: dict[str, dict[str, Any]],
    overridden_units: set[str],
) -> dict[str, Any]:
    profile_rows = rows(PROFILE_SOURCE)
    profile_ids = {row["profile_id"] for row in profile_rows}
    if profile_ids != {"healthy_adult_male_v0_1"}:
        raise ValueError(f"Unexpected profile ids: {sorted(profile_ids)}")
    profile_id = profile_ids.pop()

    policy_by_segment = {row["segment_id"]: row for row in rows(SEGMENT_POLICY_SOURCE)}
    derived_share_by_segment = {
        row["segment_id"]: float(row["value"])
        for row in rows(DERIVED_SOURCE)
        if row["variable"] == "RELATIVE_PARENT_VOLUME"
    }
    structural_share_by_segment = {
        row["entity_id"]: float(row["mean"])
        for row in rows(SEGMENT_ARCHITECTURE_SOURCE)
        if row["entity_type"] == "SEGMENT" and row["variable"] == "RELATIVE_PARENT_PCSA"
    }

    priors: list[dict[str, Any]] = []
    prior_by_target: dict[tuple[str, str], dict[str, Any]] = {}

    for selected in profile_rows:
        entity_type = selected["entity_type"]
        entity_id = selected["entity_id"]
        if selected["variable"] != "REFERENCE_VOLUME_MEAN" or selected["unit"] != "cm3":
            raise ValueError(f"Unsupported selected profile value: {selected}")

        if entity_type == "SEGMENT":
            segment = segment_by_id.get(entity_id)
            if segment is None:
                raise ValueError(f"Selected profile references unknown segment {entity_id}")
            muscle_id = segment["muscleId"]
            segment_id = entity_id
            share = derived_share_by_segment.get(entity_id)
            absolute_policy = {"kind": "KNOWN", "fraction": share} if share is not None else {"kind": "LATENT"}
        elif entity_type == "UNIT":
            muscle_id = entity_id
            segment_id = None if entity_id in overridden_units else f"{entity_id}_whole"
            absolute_policy = {"kind": "KNOWN", "fraction": 1.0}
        else:
            raise ValueError(f"Unsupported selected entity type: {entity_type}")

        segment_policy = policy_by_segment.get(segment_id or "")
        prior = empty_prior(
            profile_id=profile_id,
            muscle_id=muscle_id,
            segment_id=segment_id,
            absolute_share_policy=absolute_policy,
            availability=segment_policy["availability"] if segment_policy else "KNOWN",
            uncertainty_class=segment_policy["uncertainty_class"] if segment_policy else "UNKNOWN",
            selection_rule=selected["selection_rule"],
        )
        prior["volumeCm3"] = estimate(
            value=float(selected["mean"]),
            uncertainty=number(selected["sd"]),
            source_id=selected["selected_source_id"],
        )
        priors.append(prior)
        prior_by_target[(prior["targetKind"], prior["targetId"])] = prior

    for segment_id, policy in policy_by_segment.items():
        key = ("SEGMENT", segment_id)
        if key in prior_by_target:
            continue
        segment = segment_by_id.get(segment_id)
        if segment is None:
            raise ValueError(f"Segment policy references unknown segment {segment_id}")

        method = policy["structural_prior_method"]
        if method == "PARENT_CAPACITY_ALLOCATED_BY_SOURCE_PCSA_FRACTION":
            fraction = structural_share_by_segment.get(segment_id)
            if fraction is None:
                raise ValueError(f"No structural share found for {segment_id}")
            absolute_policy = {
                "kind": "STRUCTURAL_PRIOR",
                "fraction": fraction,
                "uncertainty": policy["uncertainty_class"],
            }
        elif method == "PARENT_LATENT_ALLOCATION":
            absolute_policy = {"kind": "LATENT"}
        elif method.startswith("SEGMENT_VOLUME_PLUS_"):
            # Direct-volume segments are already materialised above.
            raise ValueError(f"Direct segment prior was not selected for {segment_id}")
        else:
            raise ValueError(f"Unsupported structural prior method {method} for {segment_id}")

        prior = empty_prior(
            profile_id=profile_id,
            muscle_id=segment["muscleId"],
            segment_id=segment_id,
            absolute_share_policy=absolute_policy,
            availability=policy["availability"],
            uncertainty_class=policy["uncertainty_class"],
            selection_rule=policy["native_v1_rule"],
        )
        priors.append(prior)
        prior_by_target[key] = prior

    if len(profile_rows) != 47:
        raise ValueError(f"Expected 47 selected morphology rows, found {len(profile_rows)}")
    if len(policy_by_segment) != 23:
        raise ValueError(f"Expected 23 independent-segment policies, found {len(policy_by_segment)}")
    if len(priors) != 66:
        raise ValueError(f"Expected 66 runtime priors, generated {len(priors)}")

    return {
        "schemaVersion": 1,
        "id": profile_id,
        "version": 1,
        "population": {
            "sex": "MALE",
            "ageSummary": "18-50 years; selected morphology source male mean 32 +/- 10 years",
            "description": "Healthy/general adult male reference prior; not user anatomy",
        },
        "datasetVersion": "skeletal_muscle_reference_v0_1",
        "modelVersion": "biological_prior_policy_v0_1",
        "priors": priors,
    }


def json_text(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def generate() -> dict[Path, str]:
    anatomy, segment_by_id, overridden_units = build_anatomy()
    profile = build_profile(segment_by_id, overridden_units)
    manifest = {
        "schemaVersion": 1,
        "datasetVersion": "skeletal_muscle_reference_v0_1",
        "anatomyAsset": "anatomy_v1.json",
        "referenceProfileAsset": "reference_profile_healthy_adult_male_v1.json",
        "counts": {
            "muscles": len(anatomy["muscles"]),
            "segments": len(segment_by_id),
            "referencePriors": len(profile["priors"]),
        },
        "sourceSha256": {
            path.relative_to(REPO_ROOT).as_posix(): sha256(path)
            for path in SOURCE_FILES
        },
    }
    return {
        OUTPUT_DIR / "anatomy_v1.json": json_text(anatomy),
        OUTPUT_DIR / "reference_profile_healthy_adult_male_v1.json": json_text(profile),
        OUTPUT_DIR / "reference_manifest.json": json_text(manifest),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if committed assets are stale.")
    args = parser.parse_args()
    generated = generate()

    if args.check:
        stale = [path for path, content in generated.items() if not path.exists() or path.read_text(encoding="utf-8") != content]
        if stale:
            names = ", ".join(path.relative_to(REPO_ROOT).as_posix() for path in stale)
            raise SystemExit(f"Runtime reference assets are stale: {names}")
        print("Runtime reference assets are current.")
        return

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for path, content in generated.items():
        path.write_text(content, encoding="utf-8")
        print(path.relative_to(REPO_ROOT).as_posix())


if __name__ == "__main__":
    main()
