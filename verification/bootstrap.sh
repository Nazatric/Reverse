#!/bin/bash
# Rebuilds everything the parity/verification tooling needs from a cold sandbox:
#   - the web reference build (dist/)
#   - npm deps for the app and the parity harness
#   - the headless Chromium + its shared libs (from @sparticuz/chromium)
#   - a JDK (jdk4py) and the Kotlin/JVM compiler (npm kotlin-compiler)
#   - the reference vectors (browser-sampled SVG geometry + logic vectors)
#
# Idempotent — each step is skipped when its artefact is already present.
# All artefacts live outside Git (node_modules/, dist/, /tmp, /home/user/toolchain,
# verification/vectors/), so a fresh checkout needs this before `verification/run.sh`.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== [1/6] app deps + reference build"
if [ ! -d "$ROOT/node_modules/vite" ]; then
  (cd "$ROOT" && npm install --no-audit --no-fund --silent)
fi
if [ ! -f "$ROOT/dist/index.html" ]; then
  (cd "$ROOT" && npm run build --silent)
fi

echo "== [2/6] parity harness deps"
if [ ! -d "$ROOT/parity/node_modules/puppeteer-core" ]; then
  (cd "$ROOT/parity" && npm install --no-audit --no-fund --silent)
fi

echo "== [3/6] headless chromium shared libs"
# NB: /tmp/chromium must stay free — @sparticuz/chromium extracts its binary there as a *file*.
if [ ! -f /tmp/lib/libnspr4.so ]; then
  rm -rf /tmp/chromework /tmp/lib
  mkdir -p /tmp/chromework /tmp/lib
  node -e '
    const fs = require("fs"), zlib = require("zlib");
    const src = process.argv[1] + "/node_modules/@sparticuz/chromium/bin/al2023.tar.br";
    zlib.brotliDecompress(fs.readFileSync(src), (e, b) => { if (e) throw e; fs.writeFileSync("/tmp/chromework/al2023.tar", b); });
  ' "$ROOT/parity"
  tar -xf /tmp/chromework/al2023.tar -C /tmp/lib
  # the tarball nests everything under lib/
  mv /tmp/lib/lib/* /tmp/lib/ && rmdir /tmp/lib/lib
fi

echo "== [4/6] jdk (jdk4py)"
if [ ! -x /tmp/jdkx/jdk4py/java-runtime/bin/java ]; then
  rm -rf /tmp/jdkx
  python3 -m pip install --quiet --target /tmp/jdkx jdk4py
fi

echo "== [5/6] kotlin/jvm compiler"
if [ ! -f /home/user/toolchain/node_modules/kotlin-compiler/lib/kotlin-compiler.jar ]; then
  npm install --prefix /home/user/toolchain --no-audit --no-fund --silent kotlin-compiler
fi
if [ ! -x /home/user/toolchain/kc ]; then
  printf '#!/bin/bash\nexec /tmp/jdkx/jdk4py/java-runtime/bin/java -cp /home/user/toolchain/node_modules/kotlin-compiler/lib/kotlin-compiler.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"\n' > /home/user/toolchain/kc
  chmod +x /home/user/toolchain/kc
fi

echo "== [6/6] reference vectors"
(cd "$ROOT" && node verification/gen-vectors.mjs)
(cd "$ROOT" && node verification/gen-path-vectors.mjs)

echo "bootstrap complete"
