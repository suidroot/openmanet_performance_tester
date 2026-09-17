#!/bin/sh
# Builds a debug APK for sideloading onto a phone: assembles it (running unit tests first,
# unless skipped), copies it to dist/ with a version-stamped filename, and - if exactly one
# adb device is attached - installs it directly.
#
# Uses the debug build type deliberately, not release: it's signed automatically with the
# machine-local debug keystore (no signing setup needed), which is exactly what every on-device
# test this project has done all along has used (see CLAUDE.md's Verification section). A real
# release build needs its own dedicated signing keystore, which this script intentionally does
# not create - that's a separate decision (see README's "Sideloading onto a device" section).
#
# Usage: scripts/build_sideload_apk.sh [--skip-tests] [--no-install]
set -eu
cd "$(dirname "$0")/.."

skip_tests=0
no_install=0
for arg in "$@"; do
  case "$arg" in
    --skip-tests) skip_tests=1 ;;
    --no-install) no_install=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

if [ "$skip_tests" -eq 0 ]; then
  echo "Running unit tests..."
  ./gradlew testDebugUnitTest
fi

echo "Building debug APK..."
./gradlew assembleDebug

apk_src="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$apk_src" ]; then
  echo "Expected APK not found at $apk_src" >&2
  exit 1
fi

version_name=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | sed 's/versionName = "\(.*\)"/\1/')
version_code=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | sed 's/versionCode = //')
timestamp=$(date +%Y%m%d-%H%M%S)

mkdir -p dist
apk_dest="dist/manet-perf-app-${version_name}-${version_code}-debug-${timestamp}.apk"
cp "$apk_src" "$apk_dest"
echo "Built $apk_dest ($(du -h "$apk_dest" | cut -f1))"

if [ "$no_install" -eq 1 ]; then
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not on PATH - copy $apk_dest to the phone and install it manually (see README)."
  exit 0
fi

device_count=$(adb devices | tail -n +2 | grep -c "device$" || true)
if [ "$device_count" -eq 1 ]; then
  echo "One adb device attached - installing..."
  adb install -r "$apk_dest"
elif [ "$device_count" -eq 0 ]; then
  echo "No adb device attached - copy $apk_dest to the phone and install it manually (see README)."
else
  echo "Multiple adb devices attached - install manually with:"
  echo "  adb -s <serial> install -r $apk_dest"
fi
