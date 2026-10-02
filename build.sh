#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1 && [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Error: JDK 17 is required. Set JAVA_HOME or add java to PATH." >&2
    exit 1
fi

if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Error: JAVA_HOME does not point to a valid JDK." >&2
    exit 1
fi

chmod +x ./gradlew
./gradlew test --no-daemon
./gradlew assembleDebug --no-daemon
./gradlew assembleRelease --no-daemon

echo "Debug APK: app/build/outputs/apk/debug/app-debug.apk"
echo "Release APK: app/build/outputs/apk/release/app-release-unsigned.apk"
