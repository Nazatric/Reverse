#!/usr/bin/env bash
# Runs an instrumentation test on the booted emulator, bounded so the job can always report.
#
# On any outcome it dumps logcat (which carries the full ANR trace) and the gradle log as
# annotations, because that is the only ground truth reachable from the authoring network.
set -uo pipefail

VP="${1:-412x915}"
TEST_CLASS="${2:-com.thegadget.app.parity.BootSmokeTest}"
DEST="parity/out/native/$VP"

cd android
# Build the APKs first so the bounded window below is spent running the test, not compiling.
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest >/dev/null 2>&1 || true
timeout -k 60 1500 gradle --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
  2>&1 | tee /tmp/instr.log
RC="${PIPESTATUS[0]}"
cd ..

# Pull whatever the harness wrote.
mkdir -p "$DEST"
adb pull /sdcard/Android/data/com.thegadget.app.debug/files/parity/. "$DEST/" >/dev/null 2>&1 || true
N=$(find "$DEST" -name '*.png' 2>/dev/null | wc -l)

# The money: the ANR trace, if any, lives in logcat.
adb logcat -d > /tmp/logcat.txt 2>/dev/null || true
ANR="$(grep -A60 'ANR in' /tmp/logcat.txt | head -80 | tr '\n' '|' | tr -d '\r')"
CRASH="$(grep -B2 -A25 'FATAL EXCEPTION' /tmp/logcat.txt | head -60 | tr '\n' '|' | tr -d '\r')"
BOOT="$(grep -h 'BOOT_OK' /tmp/instr.log /tmp/logcat.txt 2>/dev/null | head -1)"

echo "::notice::NATIVECAPTURE viewport=$VP png=$N gradle_rc=$RC boot=${BOOT:-none}"
[ -n "$BOOT" ] && echo "::notice::SMOKE $BOOT"
GRID="$(tr '\n' '|' < "$DEST/hub-grid.txt" 2>/dev/null)"
[ -n "$GRID" ] && echo "::notice::HUBGRID $GRID"
[ -n "$ANR" ] && echo "::error::ANRTRACE ${ANR:0:3800}"
[ -n "$CRASH" ] && echo "::error::CRASHLOG ${CRASH:0:3800}"
if [ -z "$ANR" ] && [ -z "$CRASH" ] && [ "$N" -eq 0 ]; then
  echo "::error::INSTRLOG $(tail -50 /tmp/instr.log | tr '\n' '|' | tr -d '\r')"
fi
exit 0
