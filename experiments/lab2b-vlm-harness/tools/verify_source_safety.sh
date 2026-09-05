#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
HARNESS="$ROOT/experiments/lab2b-vlm-harness"
LAB2B_BASE="13fcff8e608e18e3ac4faa232d17c98da25750df"

fail() {
    echo "LAB2B_SOURCE_SAFETY=FAIL: $*" >&2
    exit 1
}

[ -d "$HARNESS" ] || fail "harness directory missing"

if grep -Eq 'lab2b-vlm-harness|dev\.kian\.lab2b' "$ROOT/settings.gradle.kts"; then
    fail "My Mettle root settings includes or references the standalone harness"
fi

FORBIDDEN_ROOT_CHANGES="$(git diff --name-only "$LAB2B_BASE"...HEAD -- \
    app/ build.gradle.kts settings.gradle.kts gradle.properties gradle/ gradlew gradlew.bat || true)"
if [ -n "$FORBIDDEN_ROOT_CHANGES" ]; then
    echo "$FORBIDDEN_ROOT_CHANGES" >&2
    fail "LAB-2B changed My Mettle build/runtime paths"
fi

TRACKED_MODELS="$(git ls-files '*.gguf' '*.bin' '*.safetensors' '*.onnx' '*.pte' '*.dlc' || true)"
if [ -n "$TRACKED_MODELS" ]; then
    echo "$TRACKED_MODELS" >&2
    fail "model/runtime binary artefacts are tracked"
fi

TRACKED_EXPERIMENT_BINARIES="$(git ls-files 'experiments/lab2b-vlm-harness/**/*.apk' 'experiments/lab2b-vlm-harness/**/*.aab' 'experiments/lab2b-vlm-harness/**/*.so' || true)"
if [ -n "$TRACKED_EXPERIMENT_BINARIES" ]; then
    echo "$TRACKED_EXPERIMENT_BINARIES" >&2
    fail "generated/native binaries are tracked in the harness"
fi

if grep -R --line-number -E '<uses-permission[^>]+android.permission.INTERNET' "$HARNESS/app/src/main"; then
    fail "harness requests INTERNET permission"
fi

if grep -R --line-number -Ei 'dev\.kian\.mymettle|project.*:app|\bRoom\b|DataStore|CameraX|mlkit|retrofit|okhttp|hilt|koin' \
    "$HARNESS/app/src" "$HARNESS/app/build.gradle"; then
    fail "forbidden My Mettle/product dependency marker found in harness source"
fi

git diff --check "$LAB2B_BASE"...HEAD

echo "LAB2B_SOURCE_SAFETY=PASS"
