#!/usr/bin/env bash
# Runs the parity capture harness on the booted emulator and pulls the PNGs off it.
#
# The harness writes one PNG per screen into the app's external files dir; those are pulled into
# parity/out/native/<viewport>/ so `parity/compare.mjs` can diff them against the web reference.
#
# Deliberately tolerant: a harness failure still reports what it produced, because the interesting
# output is the count and the log, not the exit code.
set -uo pipefail

VP="${1:-412x915}"
TEST_CLASS="${2:-com.thegadget.app.parity.BootSmokeTest}"
DEST="parity/out/native/$VP"

cd android
gradle --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
  2>&1 | tee /tmp/instr.log
RC="${PIPESTATUS[0]}"
cd ..

mkdir -p "$DEST"
adb pull /sdcard/Android/data/com.thegadget.app.debug/files/parity/. "$DEST/" >/dev/null 2>&1 || true

N=$(find "$DEST" -name '*.png' 2>/dev/null | wc -l)
grep -h "BOOT_OK" /tmp/instr.log 2>/dev/null | head -1 | sed 's/^/::notice::SMOKE /' || true
echo "::notice::NATIVECAPTURE viewport=$VP png=$N gradle_rc=$RC"
find "$DEST" -name '*.png' 2>/dev/null | sort | head -40

if [ "$N" -eq 0 ]; then
  echo "::error::NATIVELOG $(tail -60 /tmp/instr.log | tr '\n' '|' | tr -d '\r')"
  exit 1
fi
