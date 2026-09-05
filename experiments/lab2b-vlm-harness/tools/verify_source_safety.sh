#!/usr/bin/env bash
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
HARNESS="$ROOT/experiments/lab2b-vlm-harness"
BASE="${LAB2B_BASE:-3f25ea228dfca24bc898a7c312015d2df0f3ab2d}"
fail() { echo "LAB2B_SOURCE_SAFETY=FAIL: $*" >&2; exit 1; }
if rg -n 'lab2b-vlm-harness|dev\.kian\.lab2b' "$ROOT/settings.gradle.kts"; then fail 'harness included by product'; fi
# Include staged AND unstaged tracked changes, not only the last commit.
changes="$(git diff --name-only "$BASE" -- app/ build.gradle.kts settings.gradle.kts gradle.properties gradle/ gradlew docs/n-bio-vnext/)"
[ -z "$changes" ] || fail "production/N-BIO changes: $changes"
if rg -n -i 'dev\.kian\.mymettle|com\.geniex|com\.qualcomm|\bRoom\b|DataStore|CameraX|retrofit|hilt|koin|\bnpu\b' "$HARNESS/app/src" "$HARNESS/app/build.gradle"; then
    fail 'forbidden production/vendor runtime dependency'
fi
# Bundled Latin OCR and harness INTERNET are explicitly authorised by the CPU/GPU mission.
rg -q 'com.google.mlkit:text-recognition:16.0.1' "$HARNESS/app/build.gradle" || fail 'bundled Latin OCR dependency missing'
python3 "$HARNESS/tools/verify_binary_safety.py"
git diff --check "$BASE"
echo 'LAB2B_SOURCE_SAFETY=PASS'
