#!/usr/bin/env bash
# Laboratório de conteúdo — pacote de revisão de um lote (renderer real): vistas no modo LINHA por asset, comparações
# com os aprovados (cor e silhueta), extremos das variações, vídeos (giro e vento), custos, catálogo.
#   tools/conteudo/pacote.sh [lote] [pasta]       padrão: 1 e output/visual-review/world-content-lab/lote-<lote>
# As capturas NO MUNDO saem de tools/conteudo/cenas.py (vitrines); este script não as refaz.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${JAVA_HOME:=$HOME/.sdkman/candidates/java/current}"; export JAVA_HOME
lote="${1:-1}"; dest="${2:-output/visual-review/world-content-lab/lote-$lote}"
py=.venv/bin/python
ids=$($py -c "import json;c=json.load(open('assets/environment/e/conteudo_catalogo.json'));print(' '.join(a['id'] for a in c['assets'] if a['lote']==$lote))")
mkdir -p "$dest/capturas/linha" "$dest/comparacoes" "$dest/variacoes" "$dest/videos"
for id in $ids; do tools/conteudo/vistas.sh "$id" "$dest/capturas/linha/$id" 1280x720 >/dev/null; done
cats=$($py -c "import json;c=json.load(open('assets/environment/e/conteudo_catalogo.json'));print(' '.join(sorted({a['categoria'] for a in c['assets'] if a['lote']==$lote})))")
for cat in $cats; do
  s="mode=lineup,style=e,set=conteudo-$cat-comparacao,size=1920x800"
  tools/snap.sh "$s,myaw=-30,out=$dest/comparacoes/$cat-34.png" "$s,myaw=0,sil=1,out=$dest/comparacoes/$cat-silhueta-frente.png" \
    "$s,myaw=-90,sil=1,out=$dest/comparacoes/$cat-silhueta-lado.png" "$s,myaw=-30,preset=tarde,out=$dest/comparacoes/$cat-tarde.png" >/dev/null
done
for id in $ids; do
  set_="conteudo-var-${id//_/-}"
  [ -f "assets/studies/lineups/$set_.json" ] || continue
  tools/snap.sh "mode=lineup,style=e,set=$set_,size=1920x700,myaw=-30,out=$dest/variacoes/$id.png" >/dev/null
done
for cat in $cats; do
  tools/anim.sh "$dest/videos/giro-$cat" "mode=lineup,style=e,set=conteudo-$cat,size=1280x720,tt=1" 150 30 >/dev/null
done
if [ "$lote" = 1 ]; then
  tools/anim.sh "$dest/videos/vento-vegetacao" "mode=scene,style=e,layout=vitrine_conteudo_vegetacao,size=1280x720,nohud=1,hx=1.0,hz=-2.0,yaw=0,pitch=14,dist=9,lift=1.8" 150 30 >/dev/null
fi
$JAVA_HOME/bin/java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/MeshCost.java > "$dest/custos-malha.csv"
cp assets/environment/e/conteudo_catalogo.json "$dest/catalogo.json"
cp output/conteudo/vitrines.txt "$dest/vitrines-assentamento.txt" 2>/dev/null || true
echo "pacote: $dest ($(find "$dest" -name '*.png' | wc -l) PNG, $(find "$dest" -name '*.mp4' | wc -l) MP4)"
