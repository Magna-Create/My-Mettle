#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-0.6.1}"
VERBOSE_NATIVE="${LAB2B_VERBOSE_NATIVE:-0}"
URL="https://github.com/qualcomm/GenieX/releases/download/v${VERSION}/geniex-android-aar-v${VERSION}.aar"

# Digests published by Qualcomm on the corresponding GitHub release assets.
EXPECTED_SHA_0_3_19="9bc409ff67ede99c1dcd7d9f732c13eb5e40eb71785795638ac539b32c26b3d8"
EXPECTED_SHA_0_6_1="2dff6eac964556ba5b002fb935abc9bc22b42abaffe11368ed987d92b3c7619f"

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { echo "ERROR: required command '$1' not found" >&2; exit 2; }
}

require_cmd curl
require_cmd unzip
require_cmd sha256sum

OBJDUMP="${LLVM_OBJDUMP_BIN:-$(command -v llvm-objdump || true)}"
if [ -z "$OBJDUMP" ] || [ ! -x "$OBJDUMP" ]; then
    echo "ERROR: llvm-objdump not found. Set LLVM_OBJDUMP_BIN if needed." >&2
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

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
AAR="$TMP/geniex-android-${VERSION}.aar"
EXTRACT="$TMP/aar"
mkdir -p "$EXTRACT"

echo "=== GenieX release 16 KB comparison (research only) ==="
echo "VERSION=$VERSION"
echo "URL=$URL"
echo "NOTE=This does not modify the LAB-2B harness dependency."

echo "Downloading Qualcomm release AAR..."
curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 -o "$AAR" "$URL"

ACTUAL_SHA="$(sha256sum "$AAR" | awk '{print $1}')"
echo "AAR_SHA256=$ACTUAL_SHA"
EXPECTED_SHA=""
case "$VERSION" in
    0.3.19) EXPECTED_SHA="$EXPECTED_SHA_0_3_19" ;;
    0.6.1) EXPECTED_SHA="$EXPECTED_SHA_0_6_1" ;;
esac

if [ -n "$EXPECTED_SHA" ]; then
    echo "EXPECTED_SHA256=$EXPECTED_SHA"
    if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
        echo "ERROR: v${VERSION} AAR SHA-256 does not match Qualcomm's published release digest." >&2
        exit 2
    fi
    echo "SHA256_MATCH=PASS"
else
    echo "SHA256_MATCH=UNVERIFIED_FOR_THIS_VERSION"
fi

unzip -q "$AAR" -d "$EXTRACT"

FAILURES=()
TOTAL=0

while IFS= read -r -d '' so; do
    TOTAL=$((TOTAL + 1))
    relative="${so#$EXTRACT/}"
    machine="unknown"
    if [ -n "$READELF" ] && [ -x "$READELF" ]; then
        machine="$($READELF -h "$so" 2>/dev/null | awk -F: '/^[[:space:]]*Machine:/{sub(/^[[:space:]]+/, "", $2); print $2; exit}' || true)"
        [ -n "$machine" ] || machine="unknown"
    fi

    loads="$($OBJDUMP -p "$so" | grep 'LOAD' || true)"
    if [ -z "$loads" ]; then
        echo "$relative machine=$machine ELF_ALIGNMENT=FAIL reason=NO_LOAD" >&2
        FAILURES+=("$relative:$machine:NO_LOAD")
        continue
    fi

    lowest=999
    parse_failed=0
    while IFS= read -r token; do
        [ -z "$token" ] && continue
        if [[ "$token" =~ align[[:space:]]2\*\*([0-9]+) ]]; then
            power="${BASH_REMATCH[1]}"
            (( power < lowest )) && lowest="$power"
        else
            parse_failed=1
        fi
    done < <(printf '%s\n' "$loads" | grep -oE 'align 2\*\*[0-9]+' || true)

    if [ "$VERBOSE_NATIVE" = "1" ]; then
        echo
        echo "$relative"
        echo "MACHINE=$machine"
        printf '%s\n' "$loads"
    fi

    if [ "$parse_failed" -eq 1 ] || [ "$lowest" -eq 999 ]; then
        echo "$relative machine=$machine ELF_ALIGNMENT=FAIL reason=PARSE" >&2
        FAILURES+=("$relative:$machine:PARSE")
    elif (( lowest < 14 )); then
        echo "$relative machine=$machine ELF_ALIGNMENT=FAIL lowest=2**$lowest" >&2
        FAILURES+=("$relative:$machine:2**$lowest")
    elif [ "$VERBOSE_NATIVE" = "1" ]; then
        echo "ELF_ALIGNMENT=PASS lowest=2**$lowest"
    fi
done < <(find "$EXTRACT" -type f -name '*.so' -print0)

echo
echo "=== Comparison summary ==="
echo "SO_COUNT=$TOTAL"
if [ "${#FAILURES[@]}" -eq 0 ]; then
    echo "UNALIGNED=none"
    echo "GENIEX_RELEASE_16K=PASS_STATIC_ELF"
    exit 0
fi

printf 'UNALIGNED=%s\n' "${FAILURES[@]}"
echo "GENIEX_RELEASE_16K=FAIL_ELF_ALIGNMENT" >&2
exit 1
