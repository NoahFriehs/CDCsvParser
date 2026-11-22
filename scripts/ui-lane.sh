#!/usr/bin/env bash
# The UI test lane: the plain Compose suite, then the persistence-
# across-restart sequence (one instrumentation invocation per leg).
#
# The process death between a setup leg and its assertion leg cannot be
# driven from a test: the connected instrumentation shares ONE process
# and ONE uid with the app, so any kill a test reaches either takes the
# runner down with the app or is refused to the app uid. The host (root
# context, on the CI emulator and on a local one alike) owns the
# force-stop, so this script is the orchestrator:
#
#     setup leg runs  ->  host: am force-stop  ->  assertion leg runs
#
# The legs are plain `am instrument` invocations (no per-leg gradle
# run) on the APKs gradle built once up front, freshly installed at
# the lane start so every run begins from a clean install. Each leg's
# `am instrument` output is captured to
# app/build/outputs/androidTest-results/lane/<leg>.log; the JUnit4
# summary is the gate (the runner's exit code reports -1 even on
# failure): success prints "OK (N tests)", a failure prints
# "FAILURES" (see instrument()).
set -euo pipefail

PKG=at.msd.friehs_bicha.cdcsvparser
TEST_PKG="${PKG}.test"
SERIAL=emulator-5554
RUNNER=androidx.test.runner.AndroidJUnitRunner
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
LANE_DIR=app/build/outputs/androidTest-results/lane

mkdir -p "$LANE_DIR"

instrument() {
  # $1 = leg name (the log file), $2 = the class filter
  # (Class or Class#method); empty = every class of the test APK.
  local log="$LANE_DIR/$1.log"
  local args=(adb -s "$SERIAL" shell am instrument -r -w)
  if [ -n "${2:-}" ]; then
    args+=(-e class "$2")
  fi
  "${args[@]}" "$TEST_PKG/$RUNNER" >"$log" 2>&1 || true
  # AndroidJUnitRunner's final line is "INSTRUMENTATION_CODE: -1" (its
  # finish() always reports Activity.RESULT_OK as the process code), so
  # the JUnit4 summary is the gate: success prints "OK (N test[s])",
  # a failure prints "FAILURES" + the failing methods.
  if grep -q "FAILURES" "$log" || ! grep -q "^OK (" "$log"; then
    echo "leg '$1' FAILED - tail of $log:" >&2
    tail -n 40 "$log" >&2
    return 1
  fi
  grep "^OK (" "$log" | head -n 2
}

fail() {
  echo "UI lane failed at: $1" >&2
  echo "--- logcat tail (last 4000 lines)" >&2
  adb -s "$SERIAL" logcat -d -v time -t 4000 >&2 || true
}

# 0: build both APKs once, start from a clean install.
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
adb -s "$SERIAL" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" uninstall "$TEST_PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" install "$APP_APK"
adb -s "$SERIAL" install "$TEST_APK"
adb -s "$SERIAL" shell am force-stop "$PKG"

# 1: the plain suite (two invocations: the persistence legs must never
#    run as part of it - the lane orchestrates their kills).
instrument plain-compose "$PKG.ComposeUiTest" || { fail "plain suite (ComposeUiTest)"; exit 1; }
instrument plain-legacy "$PKG.ExampleInstrumentedTest" || { fail "plain suite (ExampleInstrumentedTest)"; exit 1; }

# 2: persistence across a full process restart.
#    Leg 1: the imported model (Room + the C++ state blob) survives.
adb -s "$SERIAL" shell am force-stop "$PKG"
instrument persist-setup-model "$PKG.PersistenceRestartSetupTest#importFixtureAndArmFastStart" || { fail "persistence setup (model)"; exit 1; }
adb -s "$SERIAL" shell am force-stop "$PKG"
instrument persist-assert-model "$PKG.PersistenceRestartAssertTest#importedModelSurvivesProcessRestart" || { fail "persistence assertion (model)"; exit 1; }
#    Leg 2: the settings store (DataStore) survives.
adb -s "$SERIAL" shell am force-stop "$PKG"
instrument persist-setup-settings "$PKG.PersistenceRestartSetupTest#flipFastStartToggles" || { fail "persistence setup (settings)"; exit 1; }
adb -s "$SERIAL" shell am force-stop "$PKG"
instrument persist-assert-settings "$PKG.PersistenceRestartAssertTest#fastStartTogglesSurviveProcessRestart" || { fail "persistence assertion (settings)"; exit 1; }

echo "UI lane green: plain suite + persistence across a full process restart (both legs)"
