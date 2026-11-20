#!/usr/bin/env bash
# Laboratório de conteúdo — vistas de revisão de NPCs (renderer real, modo LINHA) + pranchas.
#   tools/conteudo/npc_vistas.sh <conjunto> [pasta] [LxA]
# Conjunto = arquivo assets/studies/lineups/<conjunto>.json (gerado por tools/npcs_mundo_e.py).
# Vistas: frente, 3/4, lado +x, lado −x, costas, 3/4 costas, silhueta, fim de tarde.
set -euo pipefail
cd "$(dirname "$0")/../.."
set="$1"; out="${2:-output/tmp/npc-vistas}"; size="${3:-1600x900}"
mkdir -p "$out"
s="mode=lineup,style=e,set=$set,size=$size"
tools/snap.sh "$s,myaw=0,out=$out/$set-frente.png" "$s,myaw=-35,out=$out/$set-34.png" \
  "$s,myaw=-90,out=$out/$set-ladox.png" "$s,myaw=90,out=$out/$set-lado-x.png" "$s,myaw=180,out=$out/$set-costas.png" \
  "$s,myaw=145,out=$out/$set-34costas.png" "$s,myaw=0,sil=1,out=$out/$set-silhueta.png" \
  "$s,myaw=-35,preset=tarde,out=$out/$set-tarde.png" >/dev/null
.venv/bin/python tools/conteudo/prancha.py "$out/$set-prancha.png" 2 800 \
  "$out/$set-frente.png:frente" "$out/$set-34.png:3/4 frente" "$out/$set-ladox.png:perfil +x" \
  "$out/$set-lado-x.png:perfil -x" "$out/$set-costas.png:costas" "$out/$set-34costas.png:3/4 costas" \
  "$out/$set-silhueta.png:silhueta" "$out/$set-tarde.png:fim de tarde"
