#!/usr/bin/env bash
# Laboratório de conteúdo — as vistas de revisão de UM asset (renderer real, modo LINHA, herói como escala) + prancha.
#   tools/conteudo/vistas.sh <id> [pasta] [LxA]
# Vistas: 3/4 frente, frente, lado +x, lado −x, fundos, 3/4 fundos, silhueta, tarde (luz de fim de tarde).
set -euo pipefail
cd "$(dirname "$0")/../.."
id="$1"; out="${2:-output/tmp/vistas}"; size="${3:-1280x720}"
set="conteudo-${id//_/-}"
mkdir -p "$out"
s="mode=lineup,style=e,set=$set,size=$size"
tools/snap.sh "$s,myaw=-35,out=$out/$id-34.png" "$s,myaw=0,out=$out/$id-frente.png" \
  "$s,myaw=-90,out=$out/$id-ladox.png" "$s,myaw=90,out=$out/$id-lado-x.png" "$s,myaw=180,out=$out/$id-fundos.png" \
  "$s,myaw=145,out=$out/$id-34fundos.png" "$s,myaw=-35,sil=1,out=$out/$id-silhueta.png" \
  "$s,myaw=-35,preset=tarde,out=$out/$id-tarde.png" >/dev/null
.venv/bin/python tools/conteudo/prancha.py "$out/$id-prancha.png" 4 480 \
  "$out/$id-34.png:3/4 frente" "$out/$id-frente.png:frente" "$out/$id-ladox.png:lado +x" "$out/$id-lado-x.png:lado -x" \
  "$out/$id-fundos.png:fundos" "$out/$id-34fundos.png:3/4 fundos" "$out/$id-silhueta.png:silhueta" "$out/$id-tarde.png:tarde"
