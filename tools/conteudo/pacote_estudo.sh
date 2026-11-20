#!/usr/bin/env bash
# Laboratório de conteúdo — pacote do ESTUDO de NPCs de ofício e ruínas: todas as evidências, reproduzíveis, com o
# renderer real (nada de imagem externa nem gerada por IA).
#   tools/conteudo/pacote_estudo.sh [destino] [parte ...]
# Partes (padrão: todas, nesta ordem):
#   npcs          vistas (frente, 3/4, perfis, costas, silhueta, tarde) em 1080p e 720p + elenco aprovado ao lado
#   expressoes    neutro, joy, determined, surprise em close, dos três
#   videos-npcs   idle, walk, emote (aceno) e oficio, em vários ângulos (MP4 + GIF)
#   ruinas        vistas no modo LINHA (com a ruína de casa aprovada) e comparação com todas as ruínas da biblioteca
#   vitrines      gera as cenas do estudo (+ caminhabilidade) e captura as tomadas do catálogo, interior e barraca
#   videos-ruinas giro das duas ruínas e entrada a pé (abóbada, portão do sítio)
#   testes        MundoNpcTest, ConteudoMundoTest, interpenetração (NpcClipProbe) e caminhabilidade
#   custo         malha real (MeshCost), faces dos NPCs e bench das duas vitrines (3 × 300 quadros, 1080p)
#   determinismo  dois processos, byte a byte (env_e + conteudo_e + npcs_mundo_e)
#   fechamento    vaga do feirante (antiga × laterais, 0/90/180/270/37°) e vídeos de revisão final (transições, câmera
#                 de jogo), em <destino>/fechamento/
# Rode build_assets antes (o Gradle faz sozinho em test/installDist). Saída fora do git (output/).
set -euo pipefail
cd "$(dirname "$0")/../.."
dest="${1:-output/visual-review/world-content-lab/estudo-npcs-e-ruinas}"
shift || true
parts="${*:-npcs expressoes videos-npcs ruinas vitrines videos-ruinas testes custo determinismo fechamento}"
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
PY=.venv/bin/python
NPCS="brasia quelmo arvel"
APROVADOS="kelyra indira suvia bramor teodor pluma ondina lunara cartografa"
mkdir -p "$dest"
has() { [[ " $parts " == *" $1 "* ]]; }

if has npcs; then
  echo "== npcs"
  tools/conteudo/npc_vistas.sh npcs-mundo "$dest/capturas/npcs/1080" 1920x1080 >/dev/null
  tools/conteudo/npc_vistas.sh npcs-mundo "$dest/capturas/npcs/720" 1280x720 >/dev/null
  tools/conteudo/npc_vistas.sh npcs-mundo-elenco "$dest/comparacoes/elenco" 1920x900 >/dev/null
  mkdir -p "$dest/silhuetas"
  cp "$dest/capturas/npcs/1080/npcs-mundo-silhueta.png" "$dest/silhuetas/novos-com-heroi.png"
  cp "$dest/comparacoes/elenco/npcs-mundo-elenco-silhueta.png" "$dest/silhuetas/elenco-aprovado-e-novos.png"
  tools/snap.sh "mode=lineup,style=e,set=npcs-mundo-elenco,size=1920x900,myaw=-90,sil=1,out=$dest/silhuetas/elenco-perfil.png" \
    "mode=lineup,style=e,set=npcs-mundo-elenco,size=1920x900,myaw=180,sil=1,out=$dest/silhuetas/elenco-costas.png" >/dev/null
fi

if has expressoes; then
  echo "== expressoes"
  d="$dest/capturas/expressoes"; mkdir -p "$d"
  specs=(); labels=()
  for k in $NPCS; do
    for e in neutro joy determined surprise; do
      x=""; [ "$e" != neutro ] && x=",expr=$e"
      specs+=("mode=lineup,style=e,set=npc-$k-so,size=700x700$x,myaw=0,dist=3.2,ty=0.85,out=$d/$k-$e.png")
      labels+=("$d/$k-$e.png:$k $e")
    done
    specs+=("mode=lineup,style=e,set=npc-$k-so,size=700x700,myaw=-60,dist=3.2,ty=0.85,out=$d/$k-34.png")
  done
  tools/snap.sh "${specs[@]}" >/dev/null
  $PY tools/conteudo/prancha.py "$d/expressoes.png" 4 420 "${labels[@]}" >/dev/null
fi

if has videos-npcs; then
  echo "== videos-npcs"
  d="$dest/videos/npcs"; mkdir -p "$d"
  s="mode=lineup,style=e,set=npcs-mundo-so,size=1280x720"
  tools/anim.sh "$d/idle-34" "$s,clip=idle,myaw=-35" 90 30 >/dev/null
  tools/anim.sh "$d/walk-perfil" "$s,clip=walk,myaw=-90" 60 30 >/dev/null
  tools/anim.sh "$d/walk-34" "$s,clip=walk,myaw=-35" 60 30 >/dev/null
  for a in "frente 0" "perfil -90" "costas 180"; do
    set -- $a
    tools/anim.sh "$d/aceno-$1" "$s,seq=emote,myaw=$2" 100 30 >/dev/null
  done
  for a in "frente 0" "34 -35" "perfil -90" "costas 180"; do
    set -- $a
    tools/anim.sh "$d/oficio-$1" "$s,seq=oficio,myaw=$2" 115 30 >/dev/null
  done
  tools/anim.sh "$d/elenco-walk-34" "mode=lineup,style=e,set=npcs-mundo-elenco,size=1920x900,clip=walk,myaw=-35" 60 30 >/dev/null
fi

if has ruinas; then
  echo "== ruinas"
  for id in ruin_vault ruin_farmstead ruin_house; do
    tools/conteudo/vistas.sh "$id" "$dest/capturas/ruinas/linha" 1920x1080 >/dev/null
  done
  mkdir -p "$dest/comparacoes"
  s="mode=lineup,style=e,set=conteudo-poi,size=1920x900"
  tools/snap.sh "$s,myaw=-30,out=$dest/comparacoes/ruinas-da-biblioteca-34.png" \
    "$s,myaw=-30,sil=1,out=$dest/comparacoes/ruinas-da-biblioteca-silhueta.png" \
    "$s,myaw=-30,preset=tarde,out=$dest/comparacoes/ruinas-da-biblioteca-tarde.png" >/dev/null
  cp "$dest/comparacoes/ruinas-da-biblioteca-silhueta.png" "$dest/silhuetas/ruinas-da-biblioteca.png" 2>/dev/null || true
fi

if has vitrines; then
  echo "== vitrines"
  mkdir -p "$dest/testes"
  $PY tools/conteudo/vitrine_estudo.py > "$dest/testes/caminhabilidade-vitrines.txt"
  $PY tools/conteudo/cenas.py "$dest/capturas/vitrines" vitrine_estudo_ruinas vitrine_estudo_npcs >/dev/null
  # tomadas que o catálogo não deriva: interior da abóbada, o portão do sítio por dentro e a barraca (voxel 1/16)
  read -r vx vz fx fz fr < <($PY - <<'EOF'
import json, math
d = json.load(open("assets/scenes/vitrine_estudo_ruinas.json"))
p = {q["n"]: q for q in d["props"]}
v, f = p["ruin_vault"], p["ruin_farmstead"]
print(v["x"], v["z"], f["x"], f["z"], f["r"])
EOF
)
  i="$dest/capturas/vitrines/vitrine_estudo_ruinas/interiores"; mkdir -p "$i"
  for luz in dia tarde; do
    pre=""; [ $luz = tarde ] && pre=",preset=tarde"
    P="mode=scene,style=e,nohud=1,layout=vitrine_estudo_ruinas,size=1920x1080$pre"
    tools/snap.sh "$P,hx=$(echo "$vx + 0.35" | bc),hz=$(echo "$vz + 0.4" | bc),hyaw=200,yaw=-15,pitch=6,dist=1.7,lift=0.75,out=$i/abobada-interior-$luz.png" \
      "$P,hx=$vx,hz=$(echo "$vz - 0.9" | bc),hyaw=0,yaw=180,pitch=4,dist=1.5,lift=0.8,out=$i/abobada-de-dentro-para-fora-$luz.png" >/dev/null
  done
  P="mode=scene,style=e,nohud=1,layout=vitrine_estudo_npcs,size=1920x1080"
  b="$dest/capturas/vitrines/vitrine_estudo_npcs/barraca"; mkdir -p "$b"
  tools/snap.sh "$P,hx=0.5,hz=-1.8,hyaw=180,yaw=0,pitch=17,dist=4.6,lift=0.9,out=$b/quelmo-camera-de-jogo.png" \
    "$P,hx=2.4,hz=-2.4,hyaw=230,yaw=35,pitch=20,dist=3.0,lift=0.9,out=$b/quelmo-3-4-alto.png" \
    "$P,preset=tarde,hx=0.5,hz=-1.8,hyaw=180,yaw=25,pitch=12,dist=3.4,lift=0.7,out=$b/quelmo-tarde.png" >/dev/null
fi

if has videos-ruinas; then
  echo "== videos-ruinas"
  d="$dest/videos/ruinas"; mkdir -p "$d"
  for id in ruin_vault ruin_farmstead; do
    tools/anim.sh "$d/giro-$id" "mode=lineup,style=e,set=conteudo-${id//_/-},size=1280x720,tt=1" 150 30 >/dev/null
  done
  read -r vx vz fx fz < <($PY -c "
import json; d=json.load(open('assets/scenes/vitrine_estudo_ruinas.json')); p={q['n']:q for q in d['props']}
print(p['ruin_vault']['x'], p['ruin_vault']['z'], p['ruin_farmstead']['x'], p['ruin_farmstead']['z'])")
  P="mode=scene,style=e,nohud=1,layout=vitrine_estudo_ruinas,size=1280x720"
  tools/anim.sh "$d/entrada-abobada" "$P,hx=$vx,hz=$(echo "$vz + 3.2" | bc),hyaw=180,yaw=0,pitch=14,dist=4.0,lift=0.9,seq=walk:2.6+idle:1.4" 120 30 >/dev/null
  tools/anim.sh "$d/portao-sitio" "$P,hx=$(echo "$fx - 1.0" | bc),hz=$(echo "$fz + 1.6" | bc),hyaw=170,yaw=350,pitch=16,dist=4.4,lift=0.9,seq=walk:2.4+idle:1.0" 105 30 >/dev/null
fi

if has testes; then
  echo "== testes"
  d="$dest/testes"; mkdir -p "$d"
  ./gradlew -q :viewer:test --tests 'vx.viewer.MundoNpcTest' --tests 'vx.viewer.ConteudoMundoTest' >/dev/null 2>&1 || true
  for t in MundoNpcTest ConteudoMundoTest; do
    cp "viewer/build/test-results/test/TEST-vx.viewer.$t.xml" "$d/" 2>/dev/null || true
  done
  args=(); for k in $NPCS; do args+=("assets/characters/npc/$k"); done
  args+=(--); for k in $APROVADOS; do args+=("assets/characters/npc/$k"); done
  tools/conteudo/npc_clip.sh "${args[@]}" > "$d/interpenetracao.md" || true
fi

if has custo; then
  echo "== custo"
  d="$dest/custo"; mkdir -p "$d"
  "$JAVA_HOME/bin/java" -cp "viewer/build/install/viewer/lib/*" tools/conteudo/MeshCost.java \
    | grep -E "^(nome|ruin_|stone_circle|house,|trail_post|workshop_forge|cabin_hunter)" > "$d/malha-ruinas.csv"
  $PY - > "$d/npcs.csv" <<'EOF'
import json
print("npc,papel,altura_u,metros,cabecas,escala,faces,voxels_por_parte_max")
for k in ("brasia", "quelmo", "arvel", "kelyra", "indira", "bramor", "teodor", "ondina"):
    m = json.load(open(f"assets/characters/npc/{k}/rig.json"))["meta"]
    print(f"{k},{m.get('role', '-')},{m.get('worldUnits', round(m['bodyHeightVoxels'] / 40, 3))},"
          f"{m.get('metersNarrative', '-')},{m['headsTall']},{m.get('escala', 1)},{m['faces']},-")
EOF
  for scene in vitrine_estudo_ruinas vitrine_estudo_npcs; do
    viewer/build/install/viewer/bin/viewer --bench "1920x1080,style=e,runs=3,frames=300,layout=$scene" 2>/dev/null \
      | grep "^bench" > "$d/bench-$scene.txt" || true
  done
fi

if has determinismo; then
  echo "== determinismo"
  t="$(mktemp -d output/tmp/determinismo.XXXXXX)"
  $PY tools/conteudo/determinismo.py "$t" > "$dest/testes/determinismo.txt" || true
  rm -rf "$t"
fi
if has fechamento; then
  echo "== fechamento"
  $PY tools/conteudo/vaga_feirante.py "$dest/fechamento/quelmo-vaga" >/dev/null
  $PY tools/conteudo/videos_revisao.py "$dest/fechamento/videos-npcs"
fi
echo "pacote em $dest"
