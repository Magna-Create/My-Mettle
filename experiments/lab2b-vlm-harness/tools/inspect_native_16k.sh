#!/usr/bin/env bash
set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAR_VERSION="0.3.5"
APK="$HARNESS_DIR/app/build/outputs/apk/debug/app-debug.apk"
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.qualcomm.qti/geniex-android/$AAR_VERSION"

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { echo "ERROR: required command '$1' not found" >&2; exit 2; }
}

require_cmd unzip
require_cmd sha256sum

mapfile -t AARS < <(find "$GRADLE_CACHE" -type f -name '*.aar' 2>/dev/null | sort)
if [ "${#AARS[@]}" -ne 1 ]; then
    echo "ERROR: expected exactly one cached GenieX $AAR_VERSION AAR under:" >&2
    echo "  $GRADLE_CACHE" >&2
    printf 'Found: %s\n' "${AARS[@]:-none}" >&2
    exit 2
fi
AAR="${AARS[0]}"

if [ ! -f "$APK" ]; then
    echo "ERROR: harness APK not found: $APK" >&2
    echo "Build the harness before running this audit." >&2
    exit 2
fi

OBJDUMP="${LLVM_OBJDUMP_BIN:-}"
if [ -z "$OBJDUMP" ]; then
    OBJDUMP="$(command -v llvm-objdump || true)"
fi
if [ -z "$OBJDUMP" ] && [ -n "${ANDROID_HOME:-}" ]; then
    OBJDUMP="$(find "$ANDROID_HOME/ndk" -type f -path '*/toolchains/llvm/prebuilt/*/bin/llvm-objdump' 2>/dev/null | sort -V | tail -n 1)"
fi
if [ -z "$OBJDUMP" ] || [ ! -x "$OBJDUMP" ]; then
    echo "ERROR: llvm-objdump not found." >&2
    echo "Android's current 16 KB procedure requires an NDK llvm-objdump. Set LLVM_OBJDUMP_BIN if needed." >&2
    exit 2
fi

READELF="${LLVM_READELF_BIN:-}"
if [ -z "$READELF" ]; then
    candidate="$(dirname "$OBJDUMP")/llvm-readelf"
    [ -x "$candidate" ] && READELF="$candidate"
fi
if [ -z "$READELF" ]; then
    READELF="$(command -v llvm-readelf || true)"
fi

ZIPALIGN="${ZIPALIGN_BIN:-}"
if [ -z "$ZIPALIGN" ] && [ -n "${ANDROID_HOME:-}" ]; then
    ZIPALIGN="$(find "$ANDROID_HOME/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign 2>/dev/null | sort -V | tail -n 1)"
fi
if [ -z "$ZIPALIGN" ]; then
    ZIPALIGN="$(command -v zipalign || true)"
fi
if [ -z "$ZIPALIGN" ] || [ ! -x "$ZIPALIGN" ]; then
    echo "ERROR: zipalign not found. Install Android SDK Build-Tools 35.0.0+ or set ZIPALIGN_BIN." >&2
    exit 2
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/aar" "$TMP/apk"
unzip -q "$AAR" -d "$TMP/aar"
unzip -q "$APK" -d "$TMP/apk"

echo "=== LAB-2B NATIVE / 16 KB AUDIT ==="
echo "AAR=$AAR"
echo "AAR_SHA256=$(sha256sum "$AAR" | awk '{print $1}')"
echo "APK=$APK"
echo "APK_SHA256=$(sha256sum "$APK" | awk '{print $1}')"
echo "LLVM_OBJDUMP=$OBJDUMP"
echo "LLVM_READELF=${READELF:-not-found}"
echo "ZIPALIGN=$ZIPALIGN"

echo
echo "=== AAR native inventory ==="
unzip -l "$AAR" | awk '/\.so$/ {print $4}' || true

echo
echo "=== APK native inventory ==="
unzip -l "$APK" | awk '/^ *[0-9]+ .*lib\/.*\.so$/ {print $4}' || true

mapfile -t APK_ABIS < <(find "$TMP/apk/lib" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort)
echo "APK_ABIS=${APK_ABIS[*]:-none}"
if [ "${#APK_ABIS[@]}" -ne 1 ] || [ "${APK_ABIS[0]:-}" != "arm64-v8a" ]; then
    echo "FAIL: harness APK must package only arm64-v8a for LAB-2B." >&2
    exit 1
fi

ELF_FAILURES=()

check_elf_tree() {
    local label="$1"
    local root="$2"
    local found=0
    local failed=0

    while IFS= read -r -d '' so; do
        found=1
        echo
        echo "[$label] $so"

        if [ -n "$READELF" ] && [ -x "$READELF" ]; then
            local machine
            machine="$($READELF -h "$so" 2>/dev/null | awk -F: '/^[[:space:]]*Machine:/{sub(/^[[:space:]]+/, "", $2); print $2; exit}' || true)"
            [ -n "$machine" ] && echo "MACHINE=$machine"
        fi

        local loads
        loads="$($OBJDUMP -p "$so" | grep 'LOAD' || true)"
        printf '%s\n' "$loads"
        if [ -z "$loads" ]; then
            echo "FAIL: no LOAD segments reported for $so" >&2
            ELF_FAILURES+=("$label:${so#$root/}:NO_LOAD")
            failed=1
            continue
        fi

        local file_failed=0
        local lowest_power=999
        local token
        while IFS= read -r token; do
            [ -z "$token" ] && continue
            if [[ "$token" =~ align[[:space:]]2\*\*([0-9]+) ]]; then
                local power="${BASH_REMATCH[1]}"
                if (( power < lowest_power )); then
                    lowest_power="$power"
                fi
                if (( power < 14 )); then
                    file_failed=1
                fi
            else
                echo "FAIL: could not parse LOAD alignment token in $so: $token" >&2
                file_failed=1
            fi
        done < <(printf '%s\n' "$loads" | grep -oE 'align 2\*\*[0-9]+' || true)

        if [ "$file_failed" -eq 1 ]; then
            echo "ELF_ALIGNMENT=FAIL lowest=2**$lowest_power (< 2**14)" >&2
            ELF_FAILURES+=("$label:${so#$root/}:2**$lowest_power")
            failed=1
        else
            echo "ELF_ALIGNMENT=PASS lowest=2**$lowest_power"
        fi

        if [ -n "$READELF" ] && [ -x "$READELF" ]; then
            if "$READELF" -l "$so" 2>/dev/null | grep -q 'GNU_RELRO'; then
                echo "RELRO=present"
            else
                echo "RELRO=not reported"
            fi
        else
            echo "RELRO=not checked (llvm-readelf unavailable)"
        fi
    done < <(find "$root" -type f -name '*.so' -print0)

    if [ "$found" -eq 0 ]; then
        echo "FAIL: no native libraries found in $label" >&2
        return 1
    fi
    [ "$failed" -eq 0 ]
}

ELF_OK=1
check_elf_tree "AAR" "$TMP/aar" || ELF_OK=0
check_elf_tree "APK" "$TMP/apk/lib/arm64-v8a" || ELF_OK=0

echo
echo "=== ELF alignment summary ==="
if [ "${#ELF_FAILURES[@]}" -eq 0 ]; then
    echo "UNALIGNED=none"
else
    printf 'UNALIGNED=%s\n' "${ELF_FAILURES[@]}"
fi

echo
echo "=== APK ZIP alignment check ==="
set +e
"$ZIPALIGN" -c -P 16 4 "$APK"
ZIP_RC=$?
set -e
if [ "$ZIP_RC" -eq 0 ]; then
    echo "ZIP_ALIGNMENT=PASS"
else
    echo "ZIP_ALIGNMENT=FAIL rc=$ZIP_RC" >&2
fi

if [ "$ELF_OK" -ne 1 ]; then
    echo "LAB2B_NATIVE_16K=FAIL_ELF_ALIGNMENT" >&2
    exit 1
fi
if [ "$ZIP_RC" -ne 0 ]; then
    echo "LAB2B_NATIVE_16K=FAIL_ZIP_ALIGNMENT" >&2
    exit 1
fi

echo "LAB2B_NATIVE_16K=PASS_STATIC"
echo "NOTE: static PASS does not replace the physical device PAGE_SIZE and runtime tests."
