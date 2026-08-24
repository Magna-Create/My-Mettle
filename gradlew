#!/usr/bin/env sh
set -eu

GRADLE_VERSION="9.3.1"
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/my-mettle-bootstrap"
DIST_DIR="$CACHE_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
ZIP_PATH="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

# Termux installs OpenJDK outside the conventional desktop Linux locations
# Gradle expects. Prefer the known Termux JDK 17 when it exists so users do not
# need to export JAVA_HOME manually before every build.
if [ -n "${PREFIX:-}" ] && [ -x "$PREFIX/lib/jvm/java-17-openjdk/bin/java" ]; then
    JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi

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

if [ -n "${PREFIX:-}" ] && [ -x "$PREFIX/bin/aapt2" ]; then
    # Google's Maven AAPT2 binary targets desktop Linux. Termux supplies an
    # Android-native build, so select it automatically on the phone.
    exec "$GRADLE_BIN" -p "$APP_HOME" \
        "-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2" "$@"
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
