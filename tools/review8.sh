#!/usr/bin/env bash
# Rodada 08 — pacote de revisão do protótipo jogável (capturas, vídeos, medição, testes, zip).
#
# Uso:  tools/review8.sh [capturas|videos|desempenho|testes|zip|tudo]   (padrão: tudo)
# Saída: output/visual-review/round-08/ (fora do git). REBUILD=1 recompila antes.
# A entrada dos roteiros (tools/jogo/*.txt) entra na MESMA fila de eventos da janela que o GLFW preenche e passa
# pelo mesmo código do jogo interativo. Não é automação do sistema operacional (sessão Wayland): o teste com
# teclado e mouse físicos é do proprietário (roteiro em docs/validation/roteiro-de-teste-fisico.md).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-08}   # R= muda a pasta (ex.: a reentrega da 08.1)
G=game/build/install/game/bin/game
V=viewer/build/install/viewer/bin/viewer
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ] || [ ! -x "$V" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
mkdir -p "$R"
enc=(-c:v libopenh264 -b:v 8M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)

# pasta de aparências só da revisão: uma aparência salva pelo criador (a "Brisa" do vídeo da 07.2, se existir)
SAVES=output/tmp/r8-aparencias
rm -rf "$SAVES"; mkdir -p "$SAVES"
if [ -f output/visual-review/round-07-2/video/brisa-001.json ]; then cp output/visual-review/round-07-2/video/brisa-001.json "$SAVES/"
else cat > "$SAVES/brisa-001.json" <<'JSON'
{"contrato":"valumo.aparencia","versao":1,"origem":{"preset":"exploradora"},"identidade":{"nome":"Brisa"},
 "aparencia":{"cabelo":{"estilo":"cabelo.chanel","cor":"#6b4630"},"pele":"#c98a5e","iris":"#8a5ac8"}}
JSON
fi
export GAME_OPTS="-Dvx.saves=$SAVES"

if [ "$what" = capturas ] || [ "$what" = tudo ]; then
  rm -rf "$R/capturas"
  $G --roteiro tools/jogo/capturas.txt "$R/capturas" 30 2>&1 | grep -E "^jogo" || true
  echo "capturas: $(ls "$R/capturas"/*.png | wc -l)"
fi

if [ "$what" = videos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas"
  for s in 01-movimento 02-encosta 03-obstaculos 04-camera 05-kelyra 06-aparencia; do
    tmp="$(mktemp -d output/tmp/r8video.XXXXXX)"
    $G --roteiro "tools/jogo/$s.txt" "$tmp" 30 2>&1 | grep -E "^estado|quadros gravados" > "$R/videos/$s-estado.txt" || true
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$R/videos/$s.mp4"
    n=$(ls "$tmp/quadros" | wc -l)
    # prancha: 12 quadros igualmente espaçados
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" \
      -vf "select='not(mod(n\,$(( (n + 11) / 12 ))))',scale=640:-1,tile=4x3" -frames:v 1 "$R/pranchas/$s.png"
    rm -rf "$tmp"
    echo "video: $R/videos/$s.mp4 ($n quadros)"
  done
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  rm -rf "$R/desempenho"; mkdir -p "$R/desempenho"
  # jogo: percurso medido quadro a quadro (lógica + desenho + interface, glFinish), janela oculta
  $G --roteiro tools/jogo/desempenho.txt "$R/desempenho" 60 2>&1 | grep -vE "WARNING|libdecor|^jogo" > "$R/desempenho/jogo.txt" || true
  # viewer na MESMA cena (jogo_vila_e), mesma resolução e qualidade: só o desenho (herói andando, câmera orbitando)
  $V --layout jogo_vila_e --bench "1920x1080,style=e,quality=padrao,runs=3,out=$R/desempenho/viewer-mesma-cena.csv" 2>&1 \
    | grep -vE "WARNING|libdecor" > "$R/desempenho/viewer-mesma-cena.txt" || true
  cat "$R/desempenho/jogo.txt"; tail -8 "$R/desempenho/viewer-mesma-cena.txt"
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
  # imagens, documentos e pranchas (sem os vídeos, que ficam na pasta)
  (cd output/visual-review && rm -f rodada-08-imagens-e-documentos.zip && \
   zip -qr rodada-08-imagens-e-documentos.zip round-08 -x 'round-08/videos/*.mp4')
  ls -la output/visual-review/rodada-08-imagens-e-documentos.zip
fi
