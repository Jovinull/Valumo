#!/usr/bin/env bash
# Rodada 09.1 — pacote de revisão: câmera de combate (antes × depois), som (catálogo, vídeos com o som da própria
# aplicação, sincronia), desempenho, testes, validadores e zip.
# Uso:  tools/review91.sh [camera|videos|sons|desempenho|testes|validadores|regressao|zip|tudo]   (padrão: tudo)
# Saída: output/visual-review/round-09-1/ (fora do git). REBUILD=1 recompila antes.
#
# Os roteiros (tools/combate91/*.txt) entram na MESMA fila de eventos da janela que o GLFW preenche. O som dos vídeos
# é o que o jogo mixou (OpenAL Soft, dispositivo "loopback", -Dvx.som=loopback): cada quadro gravado leva exatamente o
# som daquele quadro de jogo — não é trilha colada depois. "posicionar"/"inimigo" são montagem técnica de cena.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-09-1}
G=game/build/install/game/bin/game
G09=${G09:-output/tmp/game-09/bin/game}          # binário da rodada 09 guardado antes das mudanças (capturas "antes")
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R" output/tmp

if [ "$what" = camera ] || [ "$what" = tudo ]; then
  # mesmos casos e mesmo instante lógico nos dois binários; "legado" = binário novo com a câmera da 09 (números do antes)
  rm -rf "$R/camera"; mkdir -p "$R/camera"/{antes,depois,legado-no-binario-novo}
  t=$(mktemp -d output/tmp/r91cam.XXXXXX)
  for m in antes depois legado; do tools/combate91/gerar-camera.sh $m > "$t/$m.txt"; done
  if [ -x "$G09" ]; then $G09 --roteiro "$t/antes.txt" "$R/camera/antes" 30 > "$t/antes.log" 2>&1 || true
  else echo "aviso: binário da rodada 09 ausente ($G09): sem capturas do binário antigo"; fi
  $G --roteiro "$t/depois.txt" "$R/camera/depois" 30 > "$t/depois.log" 2>&1
  $G --roteiro "$t/legado.txt" "$R/camera/legado-no-binario-novo" 30 > "$t/legado.log" 2>&1
  grep enquadramento "$t/legado.log" | sed 's/^/antes  /' > "$R/camera/enquadramento.txt"
  grep enquadramento "$t/depois.log" | sed 's/^/depois /' >> "$R/camera/enquadramento.txt"
  python3 - "$R/camera" > "$R/camera/binario-09-x-modo-camera-09.txt" <<'PY'
import sys, glob, os
from PIL import Image, ImageChops
R = sys.argv[1]
print("# capturas do binário da rodada 09 × binário novo com 'debug camera-09 on' (pixel a pixel)")
for f in sorted(glob.glob(R + "/antes/*.png")):
    n = os.path.basename(f)
    d = ImageChops.difference(Image.open(f).convert("RGB"), Image.open(R + "/legado-no-binario-novo/" + n).convert("RGB"))
    print(n, "idênticas" if d.getbbox() is None else "DIFERENTES")
PY
  python3 tools/combate91/prancha-camera.py "$R/camera" "$t/legado.log" "$t/depois.log" > /dev/null
  rm -rf "$R/camera/legado-no-binario-novo" "$t"   # já conferido: idêntico ao binário antigo (registro acima)
  # o caso citado no pedido: o roteiro EXATO da rodada 09 (tools/combate/camera.txt) no binário novo, ao lado da captura
  # original da rodada 09 (camera/06-carvalho-com-ajuste)
  C="$R/camera/roteiro-da-rodada-09"; mkdir -p "$C/depois"
  $G --roteiro tools/combate/camera.txt "$C/depois" 30 > /dev/null 2>&1 || true
  if [ -f output/visual-review/round-09/camera/06-carvalho-com-ajuste.png ]; then
    cp output/visual-review/round-09/camera/06-carvalho-com-ajuste.png "$C/06-carvalho-com-ajuste-ANTES-rodada-09.png"
    cp "$C/depois/06-carvalho-com-ajuste.png" "$C/06-carvalho-com-ajuste-DEPOIS-09-1.png"
    python3 - "$C" <<'PY'
import sys
from PIL import Image, ImageDraw
C = sys.argv[1]
a = Image.open(C + "/06-carvalho-com-ajuste-ANTES-rodada-09.png").convert("RGB").resize((640, 360))
b = Image.open(C + "/06-carvalho-com-ajuste-DEPOIS-09-1.png").convert("RGB").resize((640, 360))
o = Image.new("RGB", (1290, 390), "white"); o.paste(a, (0, 30)); o.paste(b, (650, 30)); d = ImageDraw.Draw(o)
d.text((6, 8), "camera/06-carvalho-com-ajuste (roteiro tools/combate/camera.txt da rodada 09) - ANTES", fill=(0, 0, 0))
d.text((656, 8), "mesmo roteiro, binario 09.1 - DEPOIS", fill=(0, 0, 0))
o.save(C + "/prancha-06-carvalho.png")
PY
  fi
  cat "$R/camera/binario-09-x-modo-camera-09.txt"
  echo "câmera: $(ls "$R"/camera/depois/*.png | wc -l) casos antes/depois"
fi

if [ "$what" = videos ] || [ "$what" = tudo ]; then
  mkdir -p "$R/videos" "$R/pranchas"
  for f in tools/combate91/${2:-[0-9][0-9]}*.txt; do
    s=$(basename "$f" .txt)
    tmp="$(mktemp -d output/tmp/r91video.XXXXXX)"
    JAVA_OPTS="-Dvx.som=loopback" $G --roteiro "$f" "$tmp" 30 > "$tmp/registro.txt" 2>&1 || true
    grep -E "^combate|^som|^gravação|quadros gravados" "$tmp/registro.txt" > "$R/videos/$s-registro.txt" || true
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" -i "$tmp/audio.wav" "${enc[@]}" -pix_fmt yuv420p \
      -c:a aac -b:a 192k -shortest "$R/videos/$s.mp4"
    cp "$tmp/audio.wav" "$R/videos/$s-som-do-jogo.wav"
    python3 tools/audio_sync.py "$tmp/audio.wav" "$tmp/registro.txt" 30 > "$R/videos/$s-sincronia.txt"
    n=$(ls "$tmp/quadros" | wc -l)
    ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" \
      -vf "select='not(mod(n\,$(( (n + 15) / 16 ))))',scale=480:-1,tile=4x4" -frames:v 1 "$R/pranchas/$s.png"
    rm -rf "$tmp"
    echo "vídeo: $R/videos/$s.mp4 ($n quadros) — $(tail -1 "$R/videos/$s-sincronia.txt")"
  done
fi

if [ "$what" = sons ] || [ "$what" = tudo ]; then
  # catálogo: cada efeito isolado (os próprios arquivos), uma faixa com todos em sequência e a prancha de revisão
  rm -rf "$R/sons"; mkdir -p "$R/sons/arquivos"
  cp assets/audio/sintese/*.wav assets/audio/sintese/analise.json "$R/sons/arquivos/"
  python3 tools/audio_board.py "$R/sons/prancha-efeitos.png" > /dev/null
  python3 - "$R/sons" <<'PY'
import json, sys, wave
import numpy as np
out = sys.argv[1]
var = json.load(open("assets/audio/sintese/variantes.json"))["efeitos"]
mix = json.load(open("assets/scenes/sons_e.json"))["efeitos"]
sr, gap = 48000, 0.6
track, lines, t = [], ["# catálogo isolado (faixa catalogo-isolado.wav): instante, efeito, arquivo, ganho da mistura aplicado"], 0.0
for cue, files in var.items():
    for f in files:
        w = wave.open("assets/audio/sintese/" + f)
        y = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(float) * mix[cue]["ganho"] * 0.8
        lines.append(f"{t:7.2f} s  {cue:16s} {f}")
        track += [y, np.zeros(int(gap * sr))]
        t += len(y) / sr + gap
y = np.concatenate(track)
with wave.open(out + "/catalogo-isolado.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(np.round(y).astype("<i2").tobytes())
open(out + "/catalogo-isolado.txt", "w").write("\n".join(lines) + "\n")
PY
  ffmpeg -loglevel error -y -loop 1 -framerate 2 -i "$R/sons/prancha-efeitos.png" -i "$R/sons/catalogo-isolado.wav" \
    -vf "scale=1280:-2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$R/sons/catalogo-isolado.mp4"
  echo "sons: $(ls "$R"/sons/arquivos/*.wav | wc -l) arquivos, catálogo $(grep -c ' s ' "$R/sons/catalogo-isolado.txt") entradas"
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  mkdir -p "$R/desempenho"
  for m in mudo dispositivo; do
    t=$(mktemp -d output/tmp/r91perf.XXXXXX)
    JAVA_OPTS="-Dvx.som=$m" $G --roteiro tools/combate91/desempenho.txt "$t" 60 > "$R/desempenho/combate-som-$m.txt" 2>&1 || true
    rm -rf "$t"
  done
  grep -h "perf\|som:" "$R"/desempenho/combate-som-*.txt | head -20
fi

if [ "$what" = testes ] || [ "$what" = tudo ]; then
  ./gradlew test :viewer:installDist --rerun-tasks -q > "$R/testes-saida.txt" 2>&1 || true
  python3 - > "$R/testes.txt" <<'PY'
import glob, re
t = f = 0
rows = []
for p in sorted(glob.glob("*/build/test-results/test/*.xml")):
    x = open(p).read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', x)
    n, fl = int(m[1]), int(m[3]) + int(m[4])
    t += n; f += fl
    rows.append(f"{p.split('TEST-')[1][:-4]:45s} {n:3d} testes, {fl} falhas")
print("\n".join(rows)); print(f"TOTAL: {t} testes, {f} falhas")
PY
  tail -1 "$R/testes.txt"
fi

if [ "$what" = validadores ] || [ "$what" = tudo ]; then
  mkdir -p "$R/validadores"
  V=viewer/build/install/viewer/bin/viewer
  $V --check > "$R/validadores/check.txt" 2>&1 || true
  $V --check interrupcao-fina > "$R/validadores/interrupcao-fina.txt" 2>&1 || true
  $V --occlusion > "$R/validadores/oclusao.txt" 2>&1 || true
  tail -2 "$R/validadores/check.txt"; tail -2 "$R/validadores/interrupcao-fina.txt"
fi

if [ "$what" = regressao ] || [ "$what" = tudo ]; then
  # presets idênticos ao canônico (gate), referências canônicas, criador e exploração da 08 contra a 09 publicada
  mkdir -p "$R/regressao"
  tools/review7.sh gate > "$R/regressao/gate.txt" 2>&1 || true
  tail -3 "$R/regressao/gate.txt"
fi

if [ "$what" = zip ] || [ "$what" = tudo ]; then
  # só capturas, pranchas e documentos: sem MP4, sem WAV (ficam na pasta) e sem imagens de regressão
  (cd output/visual-review && rm -f rodada-09-1-imagens-e-documentos.zip && \
   zip -qr rodada-09-1-imagens-e-documentos.zip round-09-1 -x '*.mp4' '*.wav' 'round-09-1/regressao/*.png')
  ls -la output/visual-review/rodada-09-1-imagens-e-documentos.zip
fi
