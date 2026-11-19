#!/usr/bin/env bash
# QA de engenharia — medição comparável (janela oculta, mudo, cada quadro com glFinish): vila, combate (um | grupo |
# chefe), criador e bench do viewer, N repetições de cada, com log de GC.
# Uso: tools/qa/medir_desempenho.sh PASTA_DOS_BINARIOS ROTULO [repetições]
#   PASTA_DOS_BINARIOS contém game/ e viewer/ (cópias de */build/install/*): guarde os binários de ANTES de mexer para
#   comparar com os de depois na MESMA sessão (a GPU integrada varia com carga e temperatura). Rode da raiz, sem compilar
#   nada em paralelo. Saída: output/qa/desempenho/ROTULO/ (ou OUT_BASE/ROTULO). Som: SOM=mudo (padrão) | loopback (mistura
#   real do OpenAL Soft, sem alto-falante). Opções extras da JVM para todos: EXTRA="-Dvx.sombra.vbo=false ...".
set -euo pipefail
BIN="$1"; TAG="$2"; REPS="${3:-3}"
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
OUT="${OUT_BASE:-output/qa/desempenho}/$TAG"; mkdir -p "$OUT" output/tmp
G="$BIN/game/bin/game"; V="$BIN/viewer/bin/viewer"
# CENARIOS="vila bench" limita o que roda (padrão: todos)
quer() { [[ " ${CENARIOS:-vila combate-um combate-grupo combate-chefe criador bench} " == *" $1 "* ]]; }
run() {  # run NOME JAVA_OPTS ROTEIRO
  local nome="$1" opts="$2" rot="$3"
  quer "$nome" || return 0
  for r in $(seq 1 "$REPS"); do
    local t; t=$(mktemp -d output/tmp/audperf.XXXXXX)
    JAVA_OPTS="-Dvx.som=${SOM:-mudo} -Dvx.prefs=- -Dvx.prefs.audio=- -Xlog:gc*:file=$OUT/$nome-$r.gc.log::filecount=0 ${EXTRA:-} $opts" "$G" --roteiro "$rot" "$t" 60 > "$t/log.txt" 2>&1 || echo "FALHOU $nome $r"
    cp "$t/desempenho.txt" "$OUT/$nome-$r.txt" 2>/dev/null || cp "$t/log.txt" "$OUT/$nome-$r.FALHA.txt"
    rm -rf "$t"
  done
}
run vila "" tools/jogo/desempenho.txt
run combate-um "-Dvx.encontro=um" tools/combate92/desempenho.txt
run combate-grupo "-Dvx.encontro=grupo" tools/combate92/desempenho.txt
run combate-chefe "-Dvx.encontro=chefe" tools/combate92/desempenho.txt
quer criador && for r in $(seq 1 "$REPS"); do
  t=$(mktemp -d output/tmp/audperf.XXXXXX)
  JAVA_OPTS="${EXTRA:-}" "$V" --editor-script tools/criador/desempenho.txt "$t" 60 > "$t/log.txt" 2>&1 || echo "FALHOU criador $r"
  { cat "$t"/*.txt 2>/dev/null | grep -v "^WARNING" ; } > "$OUT/criador-$r.txt" || true
  rm -rf "$t"
done
quer bench && JAVA_OPTS="${EXTRA:-}" "$V" --layout jogo_vila_e --bench "1920x1080,style=e,quality=padrao,runs=3,out=$OUT/viewer-vila.csv" 2>&1 | grep -vE "WARNING|libdecor" > "$OUT/viewer-vila.txt" || true
echo "fim $TAG"
