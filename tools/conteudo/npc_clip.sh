#!/usr/bin/env bash
# Laboratório de conteúdo — interpenetração de NPCs nos clipes reais (vx.viewer.NpcClipProbe, a mesma medida do
# MundoNpcTest), comparada com os NPCs aprovados. Relatório em markdown na saída padrão; sai com 1 se reprovar.
#   tools/conteudo/npc_clip.sh assets/characters/npc/brasia ... -- assets/characters/npc/kelyra ...
set -euo pipefail
cd "$(dirname "$0")/../.."
./gradlew -q :viewer:testClasses :viewer:installDist >/dev/null
exec "$JAVA_HOME/bin/java" -cp "viewer/build/classes/java/test:viewer/build/install/viewer/lib/*" vx.viewer.NpcClipProbe "$@"
