#!/usr/bin/env bash
# Rodada 05 — benchmark com as MESMAS specs da rodada 04 (round-04/performance), intercalado.
# r03-equivalente (ataques antigos, sem tratamento de distância) serve de controle da sessão: se ele
# variar em relação à rodada 04, a máquina/sessão mudou e os números devem ser lidos com essa margem.
set -euo pipefail
cd "$(dirname "$0")/.."
P=output/visual-review/round-05/performance; mkdir -p $P
B=viewer/build/install/viewer/bin/viewer
run() { name=$1; spec=$2; echo "== $name ($(date +%H:%M:%S))"; $B --bench "1920x1080,style=e,runs=3,frames=300,quality=padrao,$spec,out=$P/$name.csv" 2>&1 | grep "^bench" | tee $P/$name.txt; }
run r03-equivalente "legacy,far=0"
run r05-padrao ""
run r05-heroi-atacando "act=attack"
run r05-tres-herois-atacando "act=attack,party"
run r05-depuracao-ligada "act=attack,dbg"
run r05-alta-ssaa15 "quality=alta"
run r03-equivalente-repeticao "legacy,far=0"
run r05-padrao-repeticao ""
(uname -r; lscpu | grep "Model name"; glxinfo -B 2>/dev/null | grep -E "renderer|version string"; uptime) > $P/maquina.txt
