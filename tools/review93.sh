#!/usr/bin/env bash
# Rodada 09.3 — pacote de revisão: esquiva R2 (escolhida pelo proprietário), salto (antes × depois), transições, câmera.
# Vídeos do jogo real com o som da aplicação (loopback) e vídeos do visualizador (câmera fixa, a mesma pose do jogo) em
# velocidade normal e lenta.
#
# Uso: tools/review93.sh [animacao|videos [NN]|antes|desempenho|testes|validadores|regressao|documentos|zip|tudo]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-09-3}
G=game/build/install/game/bin/game
G092=${G092:-output/tmp/game-092/bin/game}          # binário da 09.2 guardado antes das mudanças (antes)
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R" output/tmp

video() {  # video ROTEIRO NOME [JAVA_OPTS extras] [binário]
  local f="$1" s="$2" opts="${3:-}" bin="${4:-$G}"
  local tmp; tmp="$(mktemp -d output/tmp/r93video.XXXXXX)"
  JAVA_OPTS="-Dvx.som=loopback $opts" $bin --roteiro "$f" "$tmp" 30 > "$tmp/registro.txt" 2>&1 || true
  grep -E "^combate|^mira|^grupo|^estado|quadros gravados" "$tmp/registro.txt" > "$R/videos/$s-registro.txt" || true
  if [ -f "$tmp/audio.wav" ]; then
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" -i "$tmp/audio.wav" "${enc[@]}" -pix_fmt yuv420p \
      -c:a aac -b:a 192k -shortest "$R/videos/$s.mp4"
  else
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$R/videos/$s.mp4"
  fi
  local n; n=$(ls "$tmp/quadros" | wc -l)
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" \
    -vf "select='not(mod(n\,$(( (n + 15) / 16 ))))',scale=480:-1,tile=4x4" -frames:v 1 -q:v 3 "$R/pranchas/$s.jpg"
  rm -rf "$tmp"
  echo "vídeo: $R/videos/$s.mp4 ($n quadros)"
}

lado() {  # lado SAIDA "rótulo1" A.mp4 "rótulo2" B.mp4 [...]: vídeos lado a lado (mesma duração), com rótulo
  local out="$1"; shift
  local ins=() f="" k=0
  while [ $# -gt 0 ]; do
    ins+=(-i "$2"); f+="[$k:v]scale=480:-2,drawtext=text='$1':x=10:y=10:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.5[v$k];"
    k=$((k + 1)); shift 2
  done
  local st=""; for i in $(seq 0 $((k - 1))); do st+="[v$i]"; done
  ffmpeg -loglevel error -y "${ins[@]}" -filter_complex "${f}${st}hstack=inputs=$k" "${enc[@]}" -pix_fmt yuv420p "$out" 2>/dev/null \
    || ffmpeg -loglevel error -y "${ins[@]}" -filter_complex "$(echo "$f" | sed 's/,drawtext=[^[]*//g')${st}hstack=inputs=$k" "${enc[@]}" -pix_fmt yuv420p "$out"
}

if [ "$what" = animacao ] || [ "$what" = tudo ]; then
  # visualizador: câmera fixa, corpo inteiro; normal (1×) e lento (0,25×)
  mkdir -p "$R/animacao" "$R/videos"
  HERO="mode=char,style=e,hero=characters/r4/adventurer_shield,tt=0,nohud=1,size=480x480,dist=3.4,ty=0.5"
  for vel in 1 0.25; do
    n=$(python3 -c "print(int(1.1/$vel*30))"); tag=$([ "$vel" = 1 ] && echo normal || echo lento)
    for mv in dodge_f dodge_l dodge_r backstep; do                 # 09.3: só a R2 (as alternativas saíram do perfil)
      for vista in perfil:90 frente:0 tres-quartos:40 costas:180; do
        nome=${vista%%:*}; y=${vista##*:}
        tools/anim.sh "output/tmp/r93a-${mv}-${nome}-$tag" "$HERO,seq=$mv+idle:0.6,myaw=$y,speed=$vel" "$n" 30 > /dev/null
      done
    done
    for m in parado andando correndo; do
      for v in "" "antigo-"; do
        tools/anim.sh "output/tmp/r93a-salto-$v$m-$tag" "$HERO,salto=$v$m,myaw=90,speed=$vel,dist=4.0,ty=0.7" "$(python3 -c "print(int(1.4/$vel*30))")" 30 > /dev/null
      done
    done
    # A1/A2 (09.2 × R1 × R2) ficaram no pacote entregue da rodada; os gestos removidos estão em docs/historico/esquivas-09-3
    lado "$R/videos/A3-R2-lados-e-recuo-de-frente-$tag.mp4" "R2 esquerda" "output/tmp/r93a-dodge_l-frente-$tag.mp4" "R2 direita" "output/tmp/r93a-dodge_r-frente-$tag.mp4" "recuo (09.2)" "output/tmp/r93a-backstep-perfil-$tag.mp4"
    for m in parado andando correndo; do
      lado "$R/videos/A4-salto-$m-antes-x-depois-$tag.mp4" "09.2" "output/tmp/r93a-salto-antigo-$m-$tag.mp4" "09.3" "output/tmp/r93a-salto-$m-$tag.mp4"
    done
  done
  # pranchas temporais (12 instantes) por gesto e vista, e do salto
  t=$(mktemp -d output/tmp/r93board.XXXXXX)
  specs=()
  for mv in dodge_f dodge_l dodge_r backstep; do for vista in perfil:90 frente:0 tres-quartos:40; do
    nome=${vista%%:*}; y=${vista##*:}
    for x in 0.00 0.05 0.10 0.15 0.20 0.25 0.30 0.35 0.40 0.45 0.50 0.56; do specs+=("$HERO,size=260x260,seq=$mv+idle:0.6,myaw=$y,t=$x,out=$t/${mv}__${nome}__$x.png"); done
  done; done
  for m in parado correndo antigo-parado antigo-correndo; do
    for x in 0.28 0.31 0.34 0.38 0.44 0.52 0.60 0.66 0.70 0.73 0.76 0.80 0.86 0.95; do specs+=("$HERO,size=240x300,salto=$m,myaw=90,dist=4.0,ty=0.7,t=$x,out=$t/salto-${m}__perfil__$x.png"); done
  done
  tools/snap.sh "${specs[@]}" > /dev/null
  python3 - "$t" "$R/animacao" <<'PY'
import glob, sys, collections
from PIL import Image, ImageDraw
t, out = sys.argv[1], sys.argv[2]
groups = collections.defaultdict(list)
for f in glob.glob(f"{t}/*.png"):
    a, v, x = f.split("/")[-1][:-4].split("__")
    groups[a].append((v, float(x), f))
for a, items in groups.items():
    views = sorted({v for v, _, _ in items})
    times = sorted({x for _, x, _ in items})
    w, h = Image.open(items[0][2]).size
    b = Image.new("RGB", (w * len(times), h * len(views) + 24), "white")
    d = ImageDraw.Draw(b)
    d.text((6, 6), f"{a} — instantes (s): " + ", ".join(f"{x:.2f}" for x in times), fill=(0, 0, 0))
    for r, v in enumerate(views):
        for v2, x, f in items:
            if v2 == v: b.paste(Image.open(f).convert("RGB"), (times.index(x) * w, 24 + r * h))
        d.text((6, 28 + r * h), v, fill=(255, 255, 255))
    b.save(f"{out}/{a}.png")
PY
  rm -rf "$t" output/tmp/r93a-*
  echo "animação: $(ls "$R"/animacao/*.png | wc -l) pranchas, $(ls "$R"/videos/A*.mp4 | wc -l) vídeos do visualizador"
fi

if [ "$what" = videos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas"
  for f in tools/combate93/${2:-[0-9][0-9]}*.txt; do video "$f" "$(basename "$f" .txt)"; done
fi

if [ "$what" = antes ] || [ "$what" = tudo ]; then
  # o mesmo roteiro no binário da 09.2 (antes) e lado a lado com o da 09.3
  mkdir -p "$R/videos" "$R/pranchas"
  if [ -x "$G092" ]; then
    for s in 01-esquivas-livre 02-esquivas-travadas 06-saltos 08-camera-casos; do
      [ -f "$R/videos/$s-antes-092.mp4" ] || video "tools/combate93/$s.txt" "$s-antes-092" "" "$G092"
      [ -f "$R/videos/$s.mp4" ] || video "tools/combate93/$s.txt" "$s"
      lado "$R/videos/B-$s-antes-x-depois.mp4" "09.2" "$R/videos/$s-antes-092.mp4" "09.3" "$R/videos/$s.mp4"
    done
  else echo "aviso: binário da 09.2 ausente ($G092)"; fi
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  mkdir -p "$R/desempenho"
  out="$R/desempenho/medicao.txt"; : > "$out"
  for b in "09.3:$G" "09.2:$G092"; do
    for enc_ in um grupo; do for rep in 1 2; do
      t=$(mktemp -d output/tmp/r93perf.XXXXXX)
      JAVA_OPTS="-Dvx.encontro=$enc_" ${b#*:} --roteiro tools/combate92/desempenho.txt "$t" 60 > "$t/log.txt" 2>&1 || true
      { echo "=== binário ${b%%:*}, encontro $enc_ (repetição $rep) ==="; cat "$t/desempenho.txt" 2>/dev/null; echo; } >> "$out"
      rm -rf "$t"
    done; done
  done
  grep -E "^===|quadro \(ms\)" "$out"
fi

if [ "$what" = testes ] || [ "$what" = tudo ]; then
  ./gradlew test --rerun-tasks -q > "$R/testes-log.txt" 2>&1 && echo "testes: ok" || echo "testes: FALHA (ver $R/testes-log.txt)"
  python3 - "$R/testes.txt" <<'PY'
import glob, sys, collections
import xml.etree.ElementTree as ET
by = collections.OrderedDict(); n = f = 0
for x in sorted(glob.glob("*/build/test-results/test/*.xml")):
    r = ET.parse(x).getroot()
    k = r.get("name").split(".")[-1]
    t, fl = int(r.get("tests")), int(r.get("failures")) + int(r.get("errors"))
    by[k] = (t, fl); n += t; f += fl
with open(sys.argv[1], "w") as o:
    for k, (t, fl) in by.items(): o.write(f"{k:32s} {t:4d} testes  {fl} falhas\n")
    o.write(f"\nTOTAL {n} testes, {f} falhas\n")
print(f"testes: {n}, falhas: {f}")
PY
  ./gradlew :game:test --tests 'vx.game.CameraModesTest' --rerun -i 2>/dev/null | grep -E "^    câmera |^       [0-9]" > "$R/camera-modos.txt" || true
fi

if [ "$what" = validadores ] || [ "$what" = tudo ]; then
  mkdir -p "$R/validadores"
  V=viewer/build/install/viewer/bin/viewer
  $V --check > "$R/validadores/check.txt" 2>&1; tail -1 "$R/validadores/check.txt"
  for k in interrupcao-fina golpe-andando esquiva; do $V --check $k 2>&1 | grep -v WARNING > "$R/validadores/$k.txt"; tail -1 "$R/validadores/$k.txt"; done
  $V --occlusion 2>&1 | grep -v WARNING > "$R/validadores/oclusao.txt"; tail -1 "$R/validadores/oclusao.txt"
fi

if [ "$what" = regressao ] || [ "$what" = tudo ]; then
  mkdir -p "$R/regressao"
  tools/review7.sh gate > "$R/regressao/gate.txt" 2>&1 || true
  tail -2 "$R/regressao/gate.txt"
fi

if [ "$what" = documentos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/documentos"
  cp tools/combate93/LEIA-ME-pacote.md "$R/LEIA-ME.md"
  cp docs/validation/relatorio-rodada-09-3.md "$R/relatorio-tecnico.md"
  cp docs/validation/roteiro-de-teste-fisico-combate.md "$R/roteiro-de-teste-fisico.md"
  cp docs/decisions/0018-*.md docs/decisions/0019-*.md docs/architecture/combate.md docs/architecture/jogo.md docs/art/guia-de-animacao.md \
     docs/validation/problemas-conhecidos.md "$R/documentos/"
  for k in combat gameplay weapon-animation visual-qa; do cp ".claude/skills/$k/SKILL.md" "$R/documentos/skill-$k.md"; done
  echo "documentos: $(ls "$R/documentos" | wc -l) arquivos"
fi

if [ "$what" = zip ] || [ "$what" = tudo ]; then
  z=output/visual-review/rodada-09-3-imagens-e-documentos.zip
  rm -f "$z"
  (cd output/visual-review && zip -qr "$(basename "$z")" round-09-3 -x '*.mp4' -x '*.wav' -x 'round-09-3/regressao/*.png')
  echo "zip: $z ($(du -h "$z" | cut -f1))"
fi
