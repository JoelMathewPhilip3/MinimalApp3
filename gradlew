#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin/manual"
GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"
ZIP_FILE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP_FILE"
    else
      echo "Error: curl or wget is required to download Gradle." >&2
      exit 1
    fi
  fi
  echo "Extracting Gradle..."
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP_FILE" -d "$DIST_DIR"
  else
    echo "Error: unzip is required." >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
