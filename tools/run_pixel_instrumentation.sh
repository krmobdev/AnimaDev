#!/usr/bin/env bash
# Install locally compiled AnimaDev and Android test APK on exactly one trusted ADB device.
# Never triggers any GitHub actions, Git commands, or Termux builds remotely.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB_BIN="${ADB_BIN:-adb}"
APP="app/build/outputs/apk/debug/app-debug.apk"
TESTS="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
for apk in "$APP" "$TESTS"; do
    if [[ ! -f "$apk" ]]; then
        printf 'Missing %s — build with: bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest\n' "$apk" >&2
        exit 2
    fi
done
mapfile -t devices < <("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ "${#devices[@]}" -ne 1 ]]; then
    echo 'Expected exactly one authorized ADB device. No APK installed.' >&2
    "$ADB_BIN" devices -l >&2
    exit 2
fi
serial="${devices[0]}"
printf 'Testing device: %s\n' "$serial"
"$ADB_BIN" -s "$serial" install -r "$APP"
"$ADB_BIN" -s "$serial" install -r "$TESTS"
"$ADB_BIN" -s "$serial" shell am instrument -w -r \
    com.animacode.animadev.test/androidx.test.runner.AndroidJUnitRunner
