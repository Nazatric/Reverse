#!/bin/bash
# Differential test runner: compile the pure-Kotlin core + the vector runner and execute it.
# Uses the sandbox toolchain (JDK from jdk4py, Kotlin compiler from npm); on a normal machine
# substitute any JDK 17+ and kotlinc.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC="${KOTLINC:-/home/user/toolchain/kc}"
JAVA="${JAVA:-/tmp/jdkx/jdk4py/java-runtime/bin/java}"
OUT="${TMPDIR:-/tmp}/verification-out"
mkdir -p "$OUT"
# Compile into a scratch dir, then replace the jar only on success, so a failed compile can never
# be masked by stale bytecode from a previous run.
rm -rf "$OUT/classes"
mkdir -p "$OUT/classes"
# NB: capture with `|| true` — under `set -e` a non-zero kotlinc would otherwise abort this script
# silently, which would look exactly like a pass.
COMPILE_LOG="$("$KOTLINC" -d "$OUT/classes" -nowarn "$ROOT/android/app/src/main/kotlin/com/thegadget/app/core"/*.kt \
  "$ROOT/android/app/src/main/kotlin/com/thegadget/app/ui/SvgPaths.kt" \
  "$ROOT/verification/RunVectors.kt" 2>&1 || true)"
if printf '%s' "$COMPILE_LOG" | grep -Eq "error:|exception:"; then
  printf '%s\n' "$COMPILE_LOG" | grep -E "error:|exception:" | head -40
  echo "COMPILE FAILED - not running the harness"
  exit 1
fi
rm -f "$OUT/verification.jar"
"$JAVA" -cp "$OUT/classes:$(dirname "$KOTLINC")/node_modules/kotlin-compiler/lib/kotlin-stdlib.jar:/home/user/toolchain/node_modules/kotlin-compiler/lib/kotlin-stdlib.jar" RunVectorsKt "$@"
