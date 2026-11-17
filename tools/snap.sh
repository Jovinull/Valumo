#!/usr/bin/env bash
# Captura estados do viewer sem abrir janela (render offscreen real, SSAA 2x).
#
# Uso:  tools/snap.sh "mode=char,style=a,out=output/tmp/x.png" ["outra spec" ...]
# Chaves das specs: ver viewer/src/main/java/vx/viewer/Main.java
# Variáveis de ambiente:
#   MODULES="chars_b env_b"  regenera só esses módulos de assets antes (padrão: nenhum)
#   ASSETS=1                 regenera todos os assets antes
#   REBUILD=1                recompila o viewer (necessário após mudar código Java/GLSL)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:=$HOME/.sdkman/candidates/java/current}"
export JAVA_HOME
if [ -n "${ASSETS:-}" ]; then python3 tools/build_assets.py; fi
if [ -n "${MODULES:-}" ]; then python3 tools/build_assets.py $MODULES; fi
BIN=viewer/build/install/viewer/bin/viewer
if [ -n "${REBUILD:-}" ] || [ ! -x "$BIN" ]; then ./gradlew -q :viewer:installDist; fi
args=()
for spec in "$@"; do args+=(--snap "$spec"); done
"$BIN" "${args[@]}" 2>&1 | grep -v -E '^WARNING|^$' || true
