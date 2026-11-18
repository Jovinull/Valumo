#!/usr/bin/env bash
# Rodada 09.1 — gera o roteiro de câmera (antes/depois) a partir de casos-camera.txt.
# Uso: gerar-camera.sh antes|depois|legado > roteiro.txt
#   antes  = só comandos que o binário da rodada 09 entende (capturas com o binário antigo)
#   depois = binário novo, com a medição de enquadramento
#   legado = binário novo com a câmera de luta da 09 (debug camera-09 on): os números do "antes"
# Cada caso: herói e fungo montados (montagem técnica), fungo perseguindo, 1,6 s para a câmera assentar e o fungo
# agir; a captura sai no instante definido pela lógica (preparação, recuperação ou golpe), igual nos dois binários.
set -euo pipefail
mode=$1
echo "size 1280 720"
echo "click comecar"
echo "wait 1.0"
[ "$mode" = legado ] && echo "debug camera-09 on"
grep -v '^#' "$(dirname "$0")/casos-camera.txt" | while IFS='|' read -r name hero enemy wait; do
  name=$(echo $name); hero=$(echo $hero); enemy=$(echo $enemy); wait=$(echo $wait)
  echo "vida-heroi 6"
  echo "posicionar $hero"
  echo "inimigo $enemy 180 CHASE"
  echo "wait 1.6"
  echo "$wait"
  [ "$mode" != antes ] && echo "enquadramento $name"
  echo "shot $name"
done
