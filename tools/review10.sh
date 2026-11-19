#!/usr/bin/env bash
# Rodada 10 — "As Luzes do Posto": pacote de revisão da primeira aventura completa.
# Tudo com saves, preferências e aparências TEMPORÁRIOS (nunca os do proprietário) e som só por loopback (nada nos
# alto-falantes).
#
# Uso: tools/review10.sh [capturas|videos|marco|casos|desempenho|aventura-desempenho|testes|validadores|regressao|zip|tudo]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-10}
G=game/build/install/game/bin/game
BASE=${BASE:-$R/linha-de-base/bin}                   # binários do checkpoint (antes da rodada)
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R" output/tmp
# pasta temporária isolada: save, preferências do jogo e do som, aparências (vazia)
isolado() { local d; d="$(mktemp -d output/tmp/r10.XXXXXX)"; mkdir -p "$d/saves" "$d/aparencias"; echo "$d"; }
opts() { echo "-Dvx.prefs=- -Dvx.prefs.audio=- -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias"; }

roteiro() {  # roteiro ARQUIVO PASTA_ISOLADA SAIDA [som]
  local f="$1" iso="$2" out="$3" som="${4:-mudo}"
  mkdir -p "$out"
  JAVA_OPTS="-Dvx.som=$som $(opts "$iso")" $G --roteiro "$f" "$out" 30 > "$out/registro.txt" 2>&1
  grep -E "^missao|^lutar|^combate: (vitória|derrota)|Exception" "$out/registro.txt" > "$out/missao.txt" || true
}

mp4() {  # mp4 PASTA_DE_QUADROS SAIDA.mp4 (com o audio.wav da mesma pasta, se houver)
  local d="$1" o="$2"
  if [ -f "$d/audio.wav" ]; then
    ffmpeg -loglevel error -y -framerate 30 -i "$d/quadros/q_%05d.png" -i "$d/audio.wav" "${enc[@]}" -pix_fmt yuv420p \
      -c:a aac -b:a 192k -shortest "$o"
  else
    ffmpeg -loglevel error -y -framerate 30 -i "$d/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$o"
  fi
}

case "$what" in capturas|videos|tudo)
  # a aventura inteira em DUAS execuções reais com o mesmo save: jogar até salvar e sair; abrir de novo e continuar
  mkdir -p "$R/capturas" "$R/videos" "$R/registros"
  iso=$(isolado)
  roteiro tools/aventura/01-aventura.txt "$iso" "$iso/a" loopback
  cp "$iso/saves/aventura.json" "$R/registros/save-depois-da-aventura.json"
  roteiro tools/aventura/02-continuar.txt "$iso" "$iso/b" loopback
  cp "$iso"/a/*.png "$iso"/b/*.png "$R/capturas/"
  cat "$iso/a/missao.txt" "$iso/b/missao.txt" > "$R/registros/aventura-missao.txt"
  mp4 "$iso/a" "$iso/parte1.mp4"; mp4 "$iso/b" "$iso/parte2.mp4"
  printf "file '%s'\nfile '%s'\n" "$PWD/$iso/parte1.mp4" "$PWD/$iso/parte2.mp4" > "$iso/lista.txt"
  ffmpeg -loglevel error -y -f concat -safe 0 -i "$iso/lista.txt" -c copy "$R/videos/01-aventura-completa.mp4"
  for p in a b; do grep -E "quadros gravados|som gravado|loopback" "$iso/$p/registro.txt" >> "$R/registros/aventura-gravacao.txt" || true; done
  ffmpeg -loglevel error -y -i "$R/videos/01-aventura-completa.mp4" -vf "fps=1/6,scale=480:-1,tile=5x4" -frames:v 1 -q:v 3 \
    "$R/capturas/prancha-aventura.jpg"
  rm -rf "$iso"
  echo "aventura: $R/videos/01-aventura-completa.mp4"
esac

case "$what" in marco|tudo)
  mkdir -p "$R/marco"
  iso=$(isolado)
  roteiro tools/aventura/03-marco-antes-depois.txt "$iso" "$iso/m" loopback
  cp "$iso"/m/marco-*.png "$R/marco/"
  mp4 "$iso/m" "$R/videos/02-marco-acendendo.mp4"
  .venv/bin/python - "$R/marco" <<'EOF'
import sys
from pathlib import Path
from PIL import Image, ImageDraw
d = Path(sys.argv[1])
names = ["marco-antes", "marco-0-4s", "marco-1-0s", "marco-depois"]
ims = [Image.open(d / f"{n}.png").convert("RGB") for n in names]
W, H = ims[0].size
box = (int(W * 0.60), int(H * 0.36), int(W * 0.76), int(H * 0.68))
cr = [im.crop(box).resize(((box[2] - box[0]) * 2, (box[3] - box[1]) * 2), Image.NEAREST) for im in ims]
w, h = cr[0].size
b = Image.new("RGB", (w * 4, h + 34), "white")
dr = ImageDraw.Draw(b)
for i, (c, n) in enumerate(zip(cr, ["apagado", "aceso + 0,4 s", "aceso + 1,0 s", "aceso"])):
    b.paste(c, (i * w, 34))
    dr.text((i * w + 10, 10), n, fill="black")
b.save(d / "marco-antes-depois-perto.png")
EOF
  rm -rf "$iso"
esac

case "$what" in casos|tudo)
  # §16: recusar e voltar; cair no meio da trilha; save inválido (Continuar desabilitado com o motivo)
  mkdir -p "$R/casos"
  for c in 04-recusar-e-aceitar 05-cair-na-trilha; do
    iso=$(isolado)
    roteiro "tools/aventura/$c.txt" "$iso" "$iso/c"
    cp "$iso"/c/*.png "$R/casos/"; cp "$iso/c/missao.txt" "$R/casos/$c-missao.txt"
    rm -rf "$iso"
  done
  iso=$(isolado)
  printf '{ "formato": "valumo.save", "versao": 1, "id": "quebrado' > "$iso/saves/aventura.json"
  printf 'size 1280 720\nsettle\nshot c1-save-invalido\nclick continuar-jogo\nwait 0.3\nshot c2-motivo\nclick comecar\nwait 1.0\nshot c3-jogo-novo-mesmo-assim\n' > "$iso/r.txt"
  roteiro "$iso/r.txt" "$iso" "$iso/c"
  cp "$iso"/c/*.png "$R/casos/"
  ls "$iso/saves" > "$R/casos/c-save-invalido-pasta.txt"
  rm -rf "$iso"
esac

case "$what" in desempenho|tudo)
  # mesmos roteiros do checkpoint, binário de antes × de agora na MESMA sessão; + o encontro da missão (só o novo)
  rm -rf output/tmp/r10-bin && mkdir -p output/tmp/r10-bin && cp -r game/build/install/game viewer/build/install/viewer output/tmp/r10-bin/
  OUT_BASE="$R/desempenho" tools/qa/medir_desempenho.sh "$BASE" antes 3
  OUT_BASE="$R/desempenho" tools/qa/medir_desempenho.sh output/tmp/r10-bin depois 3
  OUT_BASE="$R/desempenho" CENARIOS="combate-trilha" tools/qa/medir_desempenho.sh output/tmp/r10-bin depois 3
esac

case "$what" in aventura-desempenho|tudo)
  # a missão ATIVA (rastreador + diário) no percurso da vila + 20 gravações/leituras do save, 3 vezes, pasta temporária;
  # e o percurso SEM missão no mesmo binário, na mesma sessão (custo da missão e da interface = diferença)
  mkdir -p "$R/desempenho/aventura"
  for r in 1 2 3; do
    for rot in tools/jogo/desempenho.txt tools/aventura/desempenho.txt; do
      n=$(basename "$(dirname "$rot")"); iso=$(isolado)
      JAVA_OPTS="-Dvx.som=mudo $(opts "$iso") -Xlog:gc*:file=$R/desempenho/aventura/$n-$r.gc.log::filecount=0" $G --roteiro "$rot" "$iso/o" 60 > "$iso/log.txt" 2>&1 || echo "FALHOU $n $r"
      cp "$iso/o/desempenho.txt" "$R/desempenho/aventura/$n-$r.txt" 2>/dev/null || cp "$iso/log.txt" "$R/desempenho/aventura/$n-$r.FALHA.txt"
      grep -E "^medir-save|^missao" "$iso/log.txt" >> "$R/desempenho/aventura/save-$r.txt" || true
      rm -rf "$iso"
    done
  done
  cat "$R"/desempenho/aventura/save-*.txt | grep medir-save
esac

case "$what" in testes|tudo)
  ./gradlew test --console=plain > "$R/testes.log" 2>&1 || true
  .venv/bin/python - > "$R/testes.txt" <<'EOF'
import glob, collections, xml.etree.ElementTree as ET
n = f = sk = 0; by = collections.Counter()
for x in glob.glob('*/build/test-results/test/*.xml'):
    r = ET.parse(x).getroot(); n += int(r.get('tests')); sk += int(r.get('skipped')); by[x.split('/')[0]] += int(r.get('tests'))
    for tc in r.findall('testcase'):
        if tc.find('failure') is not None or tc.find('error') is not None: f += 1; print('FALHA', r.get('name'), tc.get('name'))
print(f"testes {n}, falhas {f}, pulados {sk}: {dict(by)}")
EOF
  tail -1 "$R/testes.txt"
esac

case "$what" in validadores|tudo)
  V=viewer/build/install/viewer/bin/viewer
  { for a in "" "interrupcao-fina" "golpe-andando" "esquiva"; do $V --check $a 2>&1 | tail -1; done
    $V --check-cabelos 2>&1 | tail -1; $V --occlusion 2>&1 | tail -1
    tools/review7.sh gate 2>&1 | tail -1
    python3 tools/check_links.py 2>&1 | tail -1
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json 2>&1 | grep "arquivos" | tail -1; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in regressao|tudo)
  # capturas do checkpoint × de agora nos roteiros da 08/09.2 (mesmas entradas): o que mudou e por quê vai no relatório
  mkdir -p "$R/regressao"
  for s in tools/jogo/capturas.txt tools/combate92/capturas.txt; do
    n=$(basename "$(dirname "$s")")
    for lado in antes depois; do
      bin=$G; [ $lado = antes ] && bin="$BASE/game/bin/game"
      iso=$(isolado)
      # modo técnico: a captura 10 do 09.2 mostra a arena e a sensação na pausa (só técnicas desde o fechamento da 10)
      JAVA_OPTS="-Dvx.som=mudo -Dvx.tecnico=true $(opts "$iso")" $bin --roteiro "$s" "$iso/o" 30 > /dev/null 2>&1 || true
      mkdir -p "$R/regressao/$n-$lado"; cp "$iso"/o/*.png "$R/regressao/$n-$lado/" 2>/dev/null || true
      rm -rf "$iso"
    done
    .venv/bin/python tools/repro_compare.py imgs "$R/regressao/$n-antes" "$R/regressao/$n-depois" > "$R/regressao/$n.txt" 2>&1 || true
    tail -3 "$R/regressao/$n.txt"
  done
esac

case "$what" in zip|tudo)
  # só imagens e documentos (MP4 à parte, regra do proprietário)
  rm -f "$R/rodada-10-revisao.zip"
  (cd "$R" && zip -qr rodada-10-revisao.zip . -i '*.md' '*.txt' '*.png' '*.jpg' '*.json' -x 'regressao/*' 'linha-de-base/*' 'desempenho/*')
  ls -la "$R/rodada-10-revisao.zip"
esac
