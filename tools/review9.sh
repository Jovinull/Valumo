#!/usr/bin/env bash
# Rodada 09 — pacote de revisão do primeiro combate.
#
# Uso:  tools/review9.sh [capturas|hitboxes|camera|videos|angulos|interrupcoes|validadores|regressao|desempenho|testes|zip|tudo]   (padrão: tudo)
# Saída: output/visual-review/round-09/ (fora do git). REBUILD=1 recompila antes.
# Os roteiros (tools/combate/*.txt) entram na MESMA fila de eventos da janela que o GLFW preenche e passam pelo mesmo
# código do jogo interativo (não é automação do sistema operacional). "posicionar"/"inimigo" são montagem técnica de
# cena, para cada vídeo começar no ponto que demonstra; a luta em si é só entrada de jogo.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-09}
G=game/build/install/game/bin/game
V=viewer/build/install/viewer/bin/viewer
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ] || [ ! -x "$V" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
mkdir -p "$R"
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)

if [ "$what" = capturas ] || [ "$what" = tudo ]; then
  rm -rf "$R/capturas"
  mkdir -p "$R/capturas"; $G --roteiro tools/combate/capturas.txt "$R/capturas" 30 2>&1 | grep -E "^jogo|^combate" > "$R/capturas.registro.txt" || true; mv "$R/capturas.registro.txt" "$R/capturas/registro.txt"
  echo "capturas: $(ls "$R/capturas"/*.png | wc -l)"
fi

if [ "$what" = hitboxes ] || [ "$what" = tudo ]; then
  rm -rf "$R/hitboxes"; mkdir -p "$R/hitboxes"
  $G --roteiro tools/combate/hitboxes.txt "$R/hitboxes" 30 >/dev/null 2>&1 || true
  ffmpeg -loglevel error -y -pattern_type glob -i "$R/hitboxes/*.png" -vf "scale=640:-1,tile=2x4" -frames:v 1 "$R/hitboxes/prancha.png" || true
  echo "hitboxes: $(ls "$R"/hitboxes/[hc]-*.png | wc -l) capturas"
fi

if [ "$what" = camera ] || [ "$what" = tudo ]; then
  rm -rf "$R/camera"; mkdir -p "$R/camera"
  $G --roteiro tools/combate/camera.txt "$R/camera" 30 >/dev/null 2>&1 || true
  ls "$R"/camera/*.png | sed 's/[^/]*$//' | head -0
  python3 - "$R/camera" <<'PY'
import sys, glob
from PIL import Image, ImageDraw
d = sys.argv[1]
fs = sorted(glob.glob(d + "/0*.png"))
ims = [Image.open(f).resize((640, 360)) for f in fs]
out = Image.new("RGB", (1280, 360 * ((len(ims) + 1) // 2)), "white")
for i, im in enumerate(ims):
    out.paste(im, ((i % 2) * 640, (i // 2) * 360))
    ImageDraw.Draw(out).text(((i % 2) * 640 + 8, (i // 2) * 360 + 50), fs[i].split("/")[-1][:-4], fill=(255, 255, 255))
out.save(d + "/prancha-sem-x-com-ajuste.png")
PY
  echo "câmera: $(ls "$R"/camera/0*.png | wc -l) capturas"
fi

if [ "$what" = videos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas"
  for f in tools/combate/${2:-[0-9][0-9]}*.txt; do                 # 2º argumento: só os roteiros que começam assim
    s=$(basename "$f" .txt)
    tmp="$(mktemp -d output/tmp/r9video.XXXXXX)"
    $G --roteiro "$f" "$tmp" 30 2>&1 | grep -E "^combate|quadros gravados" > "$R/videos/$s-registro.txt" || true
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$R/videos/$s.mp4"
    n=$(ls "$tmp/quadros" | wc -l)
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" \
      -vf "select='not(mod(n\,$(( (n + 15) / 16 ))))',scale=480:-1,tile=4x4" -frames:v 1 "$R/pranchas/$s.png"
    rm -rf "$tmp"
    echo "video: $R/videos/$s.mp4 ($n quadros)"
  done
fi

if [ "$what" = angulos ] || [ "$what" = tudo ]; then
  # golpes e interrupções do herói de espada e broquel em 4 ângulos (viewer, mesmo Sequencer do validador)
  mkdir -p "$R/angulos"
  HERO="hero=characters/r4/adventurer_shield"
  for spec in \
      "sequencia|idle:0.4+slash_h!0.64+slash_d!0.64+slash_v+idle:0.6" \
      "dano-apos-corte-h|idle:0.4+slash_h!0.55+hit+idle:0.6" \
      "dano-apos-corte-d|idle:0.4+slash_d!0.57+hit+idle:0.6" \
      "dano-no-encadeamento|idle:0.4+slash_h!0.64+slash_d!0.12+hit+idle:0.6" \
      "guarda-impacto|idle:0.4+guard_hold+idle:0.4" \
      "derrota|idle:0.3+hit!0.84+defeat+idle:0.3"; do
    name=${spec%%|*}; seq=${spec#*|}
    for yaw in 0 90 180 270; do
      tools/anim.sh "$R/angulos/$name-$yaw" "mode=char,style=e,$HERO,seq=$seq,yaw=$yaw,pitch=10,size=540x540,nohud=1" 100 30 >/dev/null 2>&1 || true
    done
    ffmpeg -loglevel error -y -i "$R/angulos/$name-0.mp4" -i "$R/angulos/$name-90.mp4" -i "$R/angulos/$name-180.mp4" -i "$R/angulos/$name-270.mp4" \
      -filter_complex "[0][1][2][3]xstack=inputs=4:layout=0_0|w0_0|0_h0|w0_h0" "${enc[@]}" -pix_fmt yuv420p "$R/angulos/$name-4angulos.mp4"
    ffmpeg -loglevel error -y -i "$R/angulos/$name-4angulos.mp4" -vf "select='not(mod(n\,8))',scale=360:-1,tile=6x2" -frames:v 1 "$R/angulos/$name-prancha.png"
    for yaw in 0 90 180 270; do rm -f "$R/angulos/$name-$yaw.mp4" "$R/angulos/$name-$yaw.gif"; done
    echo "ângulos: $name"
  done
fi

if [ "$what" = interrupcoes ] || [ "$what" = tudo ]; then
  mkdir -p "$R/interrupcoes"
  $V --check interrupcao-fina > "$R/interrupcoes/fina-depois.txt" 2>/dev/null || true
  $V --check interrupcao > "$R/interrupcoes/historico-passo-005.txt" 2>/dev/null || true
  grep -E "cortes inseguros|interrupcao-fina" "$R/interrupcoes/fina-depois.txt"
  grep -E " / " "$R/interrupcoes/historico-passo-005.txt"
fi

if [ "$what" = validadores ] || [ "$what" = tudo ]; then
  mkdir -p "$R/validadores"
  $V --check > "$R/validadores/check.txt" 2>/dev/null || true
  $V --occlusion > "$R/validadores/occlusion.txt" 2>/dev/null || true
  tail -1 "$R/validadores/check.txt"; grep -E "COBERTO|ESCONDIDA|occlusion:" "$R/validadores/occlusion.txt"
fi

if [ "$what" = regressao ] || [ "$what" = tudo ]; then
  # o que já existia continua igual: presets (gate 0 pixel), 22 referências, criador da 07.2, assets determinísticos
  G9="$R/regressao"; rm -rf "$G9"; mkdir -p "$G9"
  R="$G9/presets" tools/review7.sh gate 2>&1 | grep -E "^gate" | tee "$G9/gate.txt" || true
  rm -rf output/tmp/ref09 && tools/reference.sh output/tmp/ref09 >/dev/null 2>&1
  R="$G9/criador" tools/review72.sh capturas >/dev/null 2>&1 || true
  python3 - "$G9" <<'PY' | tee "$G9/comparacoes.txt"
import glob, sys, numpy as np
from PIL import Image
def cmp(a_glob, b_dir, nome):
    fs = sorted(glob.glob(a_glob)); bad = 0
    for f in fs:
        g = b_dir + "/" + f.split("/")[-1]
        a = np.asarray(Image.open(f).convert("RGB"), dtype=int); b = np.asarray(Image.open(g).convert("RGB"), dtype=int)
        d = int(np.abs(a - b).max()) if a.shape == b.shape else 999
        if d: bad += 1; print(f"  DIFERE {f.split('/')[-1]} (máx {d}/255)")
    print(f"{nome}: {len(fs)} imagens, {bad} diferentes")
cmp("docs/art/reference/*.png", "output/tmp/ref09", "referências canônicas × reprodução")
# o criador da 07.2 foi capturado com a malha anterior à 08.1 (malha conforme muda as bordas da cena 3D de fundo): a
# comparação que isola ESTA rodada é contra o código publicado da 08.1 (CRIADOR_BASE = capturas geradas num clone de 24e83df)
import os
base = os.environ.get("CRIADOR_BASE")
if base: cmp(base + "/*.png", sys.argv[1] + "/criador/capturas", "criador: 08.1 publicada (clone limpo) × rodada 09")
cmp("output/visual-review/round-07-2/capturas/*.png", sys.argv[1] + "/criador/capturas", "criador: 07.2 (malha antiga, diferença esperada nas bordas 3D) × rodada 09")
PY
  { python3 tools/asset_audit.py assets/characters/e | head -1; python3 tools/asset_audit.py assets/characters/npc | head -1;
    python3 tools/asset_audit.py assets/creatures/e | head -1; } | tee "$G9/auditoria.txt"
  h1=$(find assets/characters assets/creatures assets/environment assets/components -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum)
  python3 tools/build_assets.py >/dev/null 2>&1
  h2=$(find assets/characters assets/creatures assets/environment assets/components -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum)
  n=$(find assets/characters assets/creatures assets/environment assets/components -type f | wc -l)
  [ "$h1" = "$h2" ] && echo "geração determinística: $n arquivos, hash idêntico em duas gerações" | tee "$G9/determinismo.txt" \
    || echo "geração NÃO determinística" | tee "$G9/determinismo.txt"
  # a exploração e a conversa da rodada 08 (roteiros da 08 na cena com a arena)
  R="$G9/jogo-08" tools/review8.sh capturas >/dev/null 2>&1 || true
  echo "jogo 08: $(ls "$G9"/jogo-08/capturas/*.png 2>/dev/null | wc -l) capturas"
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  rm -rf "$R/desempenho"; mkdir -p "$R/desempenho"
  $G --roteiro tools/combate/desempenho.txt "$R/desempenho" 60 2>&1 | grep -vE "WARNING|libdecor|^jogo|^combate\[" > "$R/desempenho/combate.txt" || true
  $G --roteiro tools/jogo/desempenho.txt "$R/desempenho" 60 2>&1 | grep -vE "WARNING|libdecor|^jogo" > "$R/desempenho/exploracao.txt" || true
  cat "$R/desempenho/combate.txt" "$R/desempenho/exploracao.txt"
fi

if [ "$what" = testes ] || [ "$what" = tudo ]; then
  ./gradlew -q test 2>&1 | grep -vE "WARNING" | tail -5 || true
  python3 - <<'PY' | tee "$R/testes.txt"
import glob, xml.etree.ElementTree as ET
t=f=0
for x in sorted(glob.glob('*/build/test-results/test/*.xml')):
    r=ET.parse(x).getroot(); n=int(r.get('tests')); e=int(r.get('failures'))+int(r.get('errors')); t+=n; f+=e
    print(f"{r.get('name')}: {n} testes, {e} falhas")
print(f"total: {t} testes, {f} falhas")
PY
fi

if [ "$what" = zip ] || [ "$what" = tudo ]; then
  # só capturas, pranchas e documentos: sem MP4 (ficam na pasta) e sem as imagens de regressão (só os resumos .txt/.md)
  (cd output/visual-review && rm -f rodada-09-imagens-e-documentos.zip && \
   zip -qr rodada-09-imagens-e-documentos.zip round-09 -x '*.mp4' 'round-09/regressao/*.png')
  ls -la output/visual-review/rodada-09-imagens-e-documentos.zip
fi
