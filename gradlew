#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/my-mettle-bootstrap"
DIST_DIR="$CACHE_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
ZIP_PATH="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$CACHE_ROOT"
    if [ ! -f "$ZIP_PATH" ]; then
        printf '%s\n' "My Mettle: downloading Gradle $GRADLE_VERSION…" >&2
        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 3 --output "$ZIP_PATH" "$DIST_URL"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$ZIP_PATH" "$DIST_URL"
        else
            printf '%s\n' "Install curl or wget, then run ./gradlew again." >&2
            exit 1
        fi
    fi

    command -v unzip >/dev/null 2>&1 || {
        printf '%s\n' "Install unzip, then run ./gradlew again." >&2
        exit 1
    }

    rm -rf "$DIST_DIR"
    unzip -q "$ZIP_PATH" -d "$CACHE_ROOT"
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
