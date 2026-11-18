#!/usr/bin/env bash
# Rodada 09.2 — pacote de revisão: mobilidade (golpe andando, esquiva, salto), mira opcional, grupo, ensaio de chefe,
# morte por desagregação e barras. Tudo sai da aplicação real (roteiros em tools/combate92/), com o som gravado pela
# própria aplicação (loopback, nada nos alto-falantes).
#
# Uso: tools/review92.sh [videos [NN]|comparacoes|capturas|animacao|desempenho|testes|validadores|regressao|documentos|zip|tudo]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-09-2}
G=game/build/install/game/bin/game
G091=${G091:-output/tmp/game-091/bin/game}          # binário da 09.1 guardado antes das mudanças (comparações "antes")
A091=${A091:-output/tmp/assets-091}                 # assets com o combate_e.json da 09.1
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R" output/tmp

# vídeo com o som da aplicação: roteiro → quadros + audio.wav → mp4 (+ prancha 4×4 e registro)
video() {  # video ROTEIRO NOME [JAVA_OPTS extras] [binário]
  local f="$1" s="$2" opts="${3:-}" bin="${4:-$G}"
  local tmp; tmp="$(mktemp -d output/tmp/r92video.XXXXXX)"
  JAVA_OPTS="-Dvx.som=loopback $opts" $bin --roteiro "$f" "$tmp" 30 > "$tmp/registro.txt" 2>&1 || true
  grep -E "^combate|^som|^mira|^grupo|^esquiva|^estado|quadros gravados" "$tmp/registro.txt" > "$R/videos/$s-registro.txt" || true
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

if [ "$what" = videos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas"
  for f in tools/combate92/${2:-[0-9][0-9]}*.txt; do
    s=$(basename "$f" .txt)
    case "$s" in 19-morte) continue ;; esac                     # a morte tem três versões (comparacoes)
    video "$f" "$s"
  done
fi

if [ "$what" = comparacoes ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas" "$R/comparacoes"
  # golpe parado: o MESMO roteiro no binário da 09.1 e no da 09.2
  if [ -x "$G091" ]; then video tools/combate92/01-golpe-parado.txt 01-golpe-parado-antes-091 "-Dvx.assets=$A091" "$G091"
  else echo "aviso: binário da 09.1 ausente ($G091): sem o 'antes'"; fi
  video tools/combate92/01-golpe-parado.txt 02-golpe-parado-depois
  # golpe andando: perfil A (09.1) × C (padrão) no mesmo binário
  video tools/combate92/03-golpe-andando.txt 03-golpe-andando-perfil-A "-Dvx.sensacao=A"
  video tools/combate92/03-golpe-andando.txt 03-golpe-andando-perfil-B "-Dvx.sensacao=B"
  # morte: antiga × D1 × D2, mesmo golpe final, lado a lado
  for m in antiga D1 D2; do video tools/combate92/19-morte.txt "19-morte-$m" "-Dvx.morte=$m"; done
  ffmpeg -loglevel error -y -i "$R/videos/19-morte-antiga.mp4" -i "$R/videos/19-morte-D1.mp4" -i "$R/videos/19-morte-D2.mp4" \
    -filter_complex "[0:v]scale=640:-2,drawtext=text='antiga (09.1)':x=20:y=20:fontsize=28:fontcolor=white:box=1:boxcolor=black@0.5[a];[1:v]scale=640:-2,drawtext=text='D1 fragmentos':x=20:y=20:fontsize=28:fontcolor=white:box=1:boxcolor=black@0.5[b];[2:v]scale=640:-2,drawtext=text='D2 fantastica (padrao)':x=20:y=20:fontsize=28:fontcolor=white:box=1:boxcolor=black@0.5[c];[a][b][c]hstack=inputs=3" \
    -map 2:a? "${enc[@]}" -pix_fmt yuv420p -c:a aac "$R/videos/19-morte-lado-a-lado.mp4" 2>/dev/null \
    || ffmpeg -loglevel error -y -i "$R/videos/19-morte-antiga.mp4" -i "$R/videos/19-morte-D1.mp4" -i "$R/videos/19-morte-D2.mp4" \
         -filter_complex "[0:v]scale=640:-2[a];[1:v]scale=640:-2[b];[2:v]scale=640:-2[c];[a][b][c]hstack=inputs=3" "${enc[@]}" -pix_fmt yuv420p "$R/videos/19-morte-lado-a-lado.mp4"
  ffmpeg -loglevel error -y -i "$R/videos/01-golpe-parado-antes-091.mp4" -i "$R/videos/02-golpe-parado-depois.mp4" \
    -filter_complex "[0:v]scale=640:-2[a];[1:v]scale=640:-2[b];[a][b]hstack=inputs=2" -an "${enc[@]}" -pix_fmt yuv420p "$R/videos/01-02-golpe-parado-antes-depois.mp4" 2>/dev/null || true
  ffmpeg -loglevel error -y -i "$R/videos/03-golpe-andando-perfil-A.mp4" -i "$R/videos/03-golpe-andando.mp4" \
    -filter_complex "[0:v]scale=640:-2[a];[1:v]scale=640:-2[b];[a][b]hstack=inputs=2" -an "${enc[@]}" -pix_fmt yuv420p "$R/videos/03-golpe-andando-A-x-C.mp4" 2>/dev/null || true
  echo "comparações: $R/videos/{01-02,03-…-A-x-C,19-morte-lado-a-lado}.mp4"
fi

if [ "$what" = animacao ] || [ "$what" = tudo ]; then
  # pranchas da animação (visualizador, câmera fixa, a mesma pose do jogo): rolamento, recuo, golpes com pernas andando
  mkdir -p "$R/animacao"
  t=$(mktemp -d output/tmp/r92anim.XXXXXX)
  specs=()
  for x in 0.00 0.04 0.08 0.12 0.16 0.20 0.24 0.28 0.32 0.36 0.42 0.50; do
    # rodada 09.3: o rolamento da 09.2 saiu do perfil (docs/historico/esquivas-09-3); a prancha usa a esquiva atual
    specs+=("mode=char,style=e,hero=characters/r4/adventurer_shield,seq=dodge_f+idle:0.5,t=$x,tt=0,myaw=90,size=360x360,nohud=1,dist=3.2,ty=0.45,out=$t/rolar_$x.png")
  done
  for x in 0.00 0.05 0.10 0.15 0.20 0.26 0.32 0.40; do
    specs+=("mode=char,style=e,hero=characters/r4/adventurer_shield,seq=backstep+idle:0.5,t=$x,tt=0,myaw=90,size=360x360,nohud=1,dist=3.2,ty=0.45,out=$t/recuo_$x.png")
  done
  for mv in slash_h slash_d slash_v; do for d in 0 90 180; do for x in 0.30 0.45 0.60 0.85; do
    specs+=("mode=char,style=e,hero=characters/r4/adventurer_shield,seq=idle:0.1+$mv+idle:0.4,pernas=walk:0:$d:1.05,t=$x,tt=0,myaw=90,size=300x300,nohud=1,dist=2.9,ty=0.5,out=$t/${mv}_rumo${d}_$x.png")
  done; done; done
  tools/snap.sh "${specs[@]}" > /dev/null
  python3 - "$t" "$R/animacao" <<'PY'
import glob, sys
from PIL import Image, ImageDraw
t, out = sys.argv[1], sys.argv[2]
def board(pat, name, cols, label):
    fs = sorted(glob.glob(f"{t}/{pat}"))
    ims = [Image.open(f).convert("RGB") for f in fs]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    b = Image.new("RGB", (w * cols, h * rows + 28), "white")
    d = ImageDraw.Draw(b)
    d.text((8, 8), label, fill=(0, 0, 0))
    for i, (im, f) in enumerate(zip(ims, fs)):
        b.paste(im, ((i % cols) * w, 28 + (i // cols) * h))
        d.text(((i % cols) * w + 6, 32 + (i // cols) * h), f.split("/")[-1][:-4], fill=(255, 255, 255))
    b.save(f"{out}/{name}.png")
board("rolar_*.png", "rolamento", 6, "Rolamento (perfil sword/sword_shield, 0,56 s): mergulho, bola com a espada no eixo do giro, levanta. Contato com o chao pelos volumes do rig.")
board("recuo_*.png", "recuo", 8, "Recuo sem direcao (0,40 s): agacha, salta para tras de frente, pousa.")
for mv in ("slash_h", "slash_d", "slash_v"):
    board(f"{mv}_*.png", f"golpe-andando-{mv}", 12, f"{mv} com as pernas andando por baixo (rumo 0 = frente, 90 = de lado, 180 = de costas; passada 1,05): tronco, bracos e espada = o golpe validado")
PY
  rm -rf "$t"
  echo "animação: $(ls "$R"/animacao/*.png | wc -l) pranchas"
fi

if [ "$what" = capturas ] || [ "$what" = tudo ]; then
  # capturas da aplicação em 1920×1080 e 1280×720 (mesmos instantes)
  mkdir -p "$R/capturas"
  for sz in "1920 1080" "1280 720"; do
    set -- $sz
    t=$(mktemp -d output/tmp/r92cap.XXXXXX)
    sed "s/^size .*/size $1 $2/" tools/combate92/capturas.txt > "$t/roteiro.txt"
    $G --roteiro "$t/roteiro.txt" "$t/saida" 30 > "$t/log.txt" 2>&1 || true
    for p in "$t"/saida/*.png; do cp "$p" "$R/capturas/$(basename "$p" .png)-${1}x${2}.png"; done
    rm -rf "$t"
  done
  echo "capturas: $(ls "$R"/capturas/*.png | wc -l)"
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  mkdir -p "$R/desempenho"
  out="$R/desempenho/medicao.txt"
  : > "$out"
  for enc_ in um grupo chefe; do
    for rep in 1 2; do
      t=$(mktemp -d output/tmp/r92perf.XXXXXX)
      JAVA_OPTS="-Dvx.encontro=$enc_" $G --roteiro tools/combate92/desempenho.txt "$t" 60 > "$t/log.txt" 2>&1 || true
      { echo "=== encontro $enc_ (repetição $rep) ==="; cat "$t/desempenho.txt" 2>/dev/null; echo; } >> "$out"
      rm -rf "$t"
    done
  done
  if [ -x "$G091" ]; then
    for rep in 1 2; do
      t=$(mktemp -d output/tmp/r92perf.XXXXXX)
      JAVA_OPTS="-Dvx.assets=$A091" $G091 --roteiro tools/combate92/desempenho-091.txt "$t" 60 > "$t/log.txt" 2>&1 || true
      { echo "=== binário 09.1, um fungo (repetição $rep) ==="; cat "$t/desempenho.txt" 2>/dev/null; echo; } >> "$out"
      rm -rf "$t"
    done
  fi
  grep -E "^===|mediana|p95|1% low|combate|morte|triângulos|draws|memória|lógica" "$out" | head -80
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
  for k in interrupcao-fina golpe-andando esquiva; do
    $V --check $k 2>&1 | grep -v WARNING > "$R/validadores/$k.txt"; tail -1 "$R/validadores/$k.txt"
  done
  $V --occlusion 2>&1 | grep -v WARNING > "$R/validadores/oclusao.txt"; tail -1 "$R/validadores/oclusao.txt"
fi

if [ "$what" = regressao ] || [ "$what" = tudo ]; then
  mkdir -p "$R/regressao"
  tools/review7.sh gate > "$R/regressao/gate.txt" 2>&1 || true
  tail -2 "$R/regressao/gate.txt"
fi

if [ "$what" = documentos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/documentos"
  cp tools/combate92/LEIA-ME-pacote.md "$R/LEIA-ME.md"
  cp docs/validation/relatorio-rodada-09-2.md "$R/relatorio-tecnico.md"
  cp docs/validation/roteiro-de-teste-fisico-combate.md "$R/roteiro-de-teste-fisico.md"
  cp docs/decisions/001[7-9]-*.md docs/decisions/002[0-2]-*.md docs/architecture/combate.md docs/architecture/jogo.md \
     docs/architecture/audio.md docs/validation/problemas-conhecidos.md "$R/documentos/"
  for k in combat gameplay weapon-animation visual-qa audio; do cp ".claude/skills/$k/SKILL.md" "$R/documentos/skill-$k.md"; done
  echo "documentos: $(ls "$R/documentos" | wc -l) arquivos"
fi

if [ "$what" = zip ] || [ "$what" = tudo ]; then
  # só prints, pranchas de animação e documentos (sem MP4, sem WAV, sem imagens de regressão)
  z=output/visual-review/rodada-09-2-imagens-e-documentos.zip
  rm -f "$z"
  (cd output/visual-review && zip -qr "$(basename "$z")" round-09-2 -x '*.mp4' -x '*.wav' -x 'round-09-2/regressao/*.png')
  echo "zip: $z ($(du -h "$z" | cut -f1))"
fi
