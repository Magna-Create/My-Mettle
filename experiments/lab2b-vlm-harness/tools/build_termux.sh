#!/usr/bin/env bash
# Build on the existing Termux toolchain, reusing verified native libraries.
set -euo pipefail
LAB_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LAB_GRADLE="$(find "$HOME" -type f -path '*/gradle-9.1.0/bin/gradle' -print -quit 2>/dev/null || true)"
if [ -z "$LAB_GRADLE" ]; then
    mkdir -p "$HOME/tools"
    LAB_TEMP="$(mktemp -d "$HOME/tools/lab2b-gradle.XXXXXX")"
    trap 'rm -rf "$LAB_TEMP"' EXIT
    curl -fL --retry 3 https://services.gradle.org/distributions/gradle-9.1.0-bin.zip -o "$LAB_TEMP/gradle.zip"
    (cd "$LAB_TEMP" && echo 'a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806  gradle.zip' | sha256sum -c -)
    unzip -q -o "$LAB_TEMP/gradle.zip" -d "$HOME/tools"
    LAB_GRADLE="$HOME/tools/gradle-9.1.0/bin/gradle"
fi
export PATH="$(dirname "$LAB_GRADLE"):$PATH"
LAB_AAPT2="$(command -v aapt2 || true)"
[ -n "$LAB_AAPT2" ] || { echo 'Termux aapt2 is missing from PATH.' >&2; exit 2; }
cd "$LAB_ROOT"
python tools/native_bundle.py reuse
./gradlew --no-daemon -Plab2bPrebuiltNative=true \
    -Pandroid.aapt2FromMavenOverride="$LAB_AAPT2" \
    testDebugUnitTest assembleDebug lintDebug
[ -d "$HOME/storage/downloads" ] || { echo 'Run termux-setup-storage, then copy app/build/outputs/apk/debug/app-debug.apk to Downloads.' >&2; exit 2; }
cp app/build/outputs/apk/debug/app-debug.apk "$HOME/storage/downloads/LAB-2B-OCR-debug.apk"
echo 'Open My Files > Downloads > LAB-2B-OCR-debug.apk to update the harness.'
