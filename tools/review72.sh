#!/usr/bin/env bash
# Rodada 07.2 — pacote de revisão do criador de personagens (capturas, vídeo do fluxo, testes).
#
# Uso:  tools/review72.sh [capturas|video|desempenho|testes|tudo]   (padrão: tudo)
# Saída: output/visual-review/round-07-2/ (fora do git). REBUILD=1 recompila o viewer antes.
# As capturas e o vídeo usam a entrada REAL do criador: os eventos do roteiro entram na fila da janela
# (Window.inject) e passam pelo mesmo controlador do uso interativo. O cursor do vídeo é desenhado pela
# interface na posição dos eventos (sessão Wayland: sem automação do sistema operacional).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-07-2}   # R= muda a pasta (regressões das rodadas seguintes)
V=viewer/build/install/viewer/bin/viewer
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$V" ]; then ./gradlew -q :viewer:installDist; fi
mkdir -p "$R"

if [ "$what" = capturas ] || [ "$what" = tudo ]; then
  rm -rf "$R/capturas"
  $V --editor-script tools/criador/capturas.txt "$R/capturas" 2>&1 | grep -E "^criador" || true
  ls "$R/capturas"/*.png | wc -l | xargs echo "capturas:"
fi

if [ "$what" = video ] || [ "$what" = tudo ]; then
  tmp="$(mktemp -d output/tmp/criador-video.XXXXXX)"
  $V --editor-script tools/criador/video.txt "$tmp" 30 2>&1 | grep -E "^criador" || true
  if ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264; then enc=(-c:v libx264 -crf 18)
  else enc=(-c:v libopenh264 -b:v 8M); fi
  mkdir -p "$R/video"
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$R/video/fluxo-completo.mp4"
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" \
    -vf "fps=15,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" \
    "$R/video/fluxo-completo.gif"
  cp "$tmp"/aparencias/*.json "$R/video/" 2>/dev/null || true
  n=$(ls "$tmp/quadros" | wc -l)
  rm -rf "$tmp"
  echo "video: $R/video/fluxo-completo.mp4 ($n quadros a 30 fps)"
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  # cliques rápidos com medição por quadro (lógica + envio + render com glFinish), 60 quadros por segundo
  rm -rf "$R/desempenho"
  $V --editor-script tools/criador/desempenho.txt "$R/desempenho" 60 2>&1 | grep -vE "WARNING|libdecor" || true
fi

if [ "$what" = testes ] || [ "$what" = tudo ]; then
  ./gradlew -q test 2>&1 | tail -5
  python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=0
for x in glob.glob('*/build/test-results/test/*.xml'):
    r=ET.parse(x).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors'))
print(f"testes: {t} executados, {f} falhas")
PY
fi
