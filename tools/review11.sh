#!/usr/bin/env bash
# Rodada 11 — pacote de revisão da fundação procedural: vistas reais por semente, comparações lado a lado, a aventura
# numa região gerada (vídeo com o som da aplicação), desempenho (vila fixa × regiões geradas no mesmo binário), testes,
# validadores e regressão do mundo fixo. Saves, preferências e aparências TEMPORÁRIOS; som só por loopback.
#
# Uso: tools/review11.sh [vistas|comparacoes|videos|geracao|desempenho|testes|validadores|regressao|zip|tudo]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-11}
G=game/build/install/game/bin/game
SEEDS=${SEEDS:-"1024 2048 4096 77"}
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R" output/tmp
isolado() { local d; d="$(mktemp -d output/tmp/r11.XXXXXX)"; mkdir -p "$d/saves" "$d/aparencias"; echo "$d"; }
opts() { echo "-Dvx.prefs=- -Dvx.prefs.audio=- -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias"; }
proc() { echo "-Dvx.mundo=procedural -Dvx.semente=$1"; }
mp4() {
  local d="$1" o="$2"
  if [ -f "$d/audio.wav" ]; then
    ffmpeg -loglevel error -y -framerate 30 -i "$d/quadros/q_%05d.png" -i "$d/audio.wav" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$o"
  else ffmpeg -loglevel error -y -framerate 30 -i "$d/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$o"; fi
}

case "$what" in vistas|comparacoes|tudo)
  mkdir -p "$R/vistas" "$R/registros"
  for s in $SEEDS; do
    iso=$(isolado)
    JAVA_OPTS="-Dvx.som=mudo $(opts "$iso") $(proc "$s")" $G --roteiro tools/mundo/01-vistas.txt "$iso/o" 30 > "$iso/log.txt" 2>&1
    mkdir -p "$R/vistas/s$s"; cp "$iso"/o/*.png "$R/vistas/s$s/"
    grep -E "^mundo|^regiao|^estado" "$iso/log.txt" > "$R/registros/geracao-s$s.txt" || true
    rm -rf "$iso"
  done
  # lado a lado: a mesma vista em cada semente
  .venv/bin/python - "$R" $SEEDS <<'EOF'
import sys
from pathlib import Path
from PIL import Image, ImageDraw
R = Path(sys.argv[1]); seeds = sys.argv[2:]
out = R / "comparacoes"; out.mkdir(exist_ok=True)
views = sorted({p.name for s in seeds for p in (R / "vistas" / f"s{s}").glob("*.png")})
for v in views:
    ims = [Image.open(R / "vistas" / f"s{s}" / v).convert("RGB").resize((960, 540)) for s in seeds if (R / "vistas" / f"s{s}" / v).exists()]
    cols = 2
    rows = (len(ims) + 1) // 2
    sheet = Image.new("RGB", (960 * cols, (540 + 30) * rows), "white")
    d = ImageDraw.Draw(sheet)
    for i, (im, s) in enumerate(zip(ims, seeds)):
        x, y = (i % cols) * 960, (i // cols) * 570
        sheet.paste(im, (x, y + 30))
        d.text((x + 10, y + 8), f"semente {s} - {v[:-4]}", fill="black")
    sheet.save(out / f"{v[:-4]}.jpg", quality=88)
print("comparações:", len(views))
EOF
esac

case "$what" in videos|tudo)
  # a aventura numa região gerada em DUAS execuções reais com o mesmo save v2: jogar até salvar e sair; reabrir e continuar
  mkdir -p "$R/videos" "$R/capturas"
  s=${VIDEO_SEED:-1024}
  iso=$(isolado)
  JAVA_OPTS="-Dvx.som=loopback $(opts "$iso") $(proc "$s")" $G --roteiro tools/mundo/02-aventura.txt "$iso/a" 30 > "$iso/a.log" 2>&1
  JAVA_OPTS="-Dvx.som=loopback $(opts "$iso") $(proc "$s")" $G --roteiro tools/mundo/03-continuar.txt "$iso/b" 30 > "$iso/b.log" 2>&1
  cp "$iso"/a/*.png "$iso"/b/*.png "$R/capturas/"
  grep -hE "^mundo|^regiao|^missao|^lutar|^seguir|^ir-lugar|^conversar" "$iso/a.log" "$iso/b.log" > "$R/registros/aventura-s$s.txt" || true
  find "$iso/saves" -name aventura.json -exec cp {} "$R/registros/save-v2-s$s.json" \;
  mp4 "$iso/a" "$iso/p1.mp4"; mp4 "$iso/b" "$iso/p2.mp4"
  printf "file '%s'\nfile '%s'\n" "$PWD/$iso/p1.mp4" "$PWD/$iso/p2.mp4" > "$iso/l.txt"
  ffmpeg -loglevel error -y -f concat -safe 0 -i "$iso/l.txt" -c copy "$R/videos/01-aventura-regiao-s$s.mp4"
  ffmpeg -loglevel error -y -i "$R/videos/01-aventura-regiao-s$s.mp4" -vf "fps=1/5,scale=480:-1,tile=5x4" -frames:v 1 -q:v 3 "$R/capturas/prancha-aventura-s$s.jpg"
  rm -rf "$iso"
  echo "vídeo: $R/videos/01-aventura-regiao-s$s.mp4"
esac

case "$what" in desempenho|geracao|tudo)
  # mesmo binário, mesma sessão: a vila fixa (roteiro da 08) × cada região gerada (ida e volta pela trilha), 3 vezes
  mkdir -p "$R/desempenho"
  for r in 1 2 3; do
    iso=$(isolado)
    JAVA_OPTS="-Dvx.som=mudo $(opts "$iso") -Xlog:gc*:file=$R/desempenho/vila-fixa-$r.gc.log::filecount=0" $G --roteiro tools/jogo/desempenho.txt "$iso/o" 60 > "$iso/log.txt" 2>&1 || echo "FALHOU vila $r"
    cp "$iso/o/desempenho.txt" "$R/desempenho/vila-fixa-$r.txt"; rm -rf "$iso"
    for s in $SEEDS; do
      iso=$(isolado)
      JAVA_OPTS="-Dvx.som=mudo $(opts "$iso") $(proc "$s") -Xlog:gc*:file=$R/desempenho/s$s-$r.gc.log::filecount=0" $G --roteiro tools/mundo/desempenho.txt "$iso/o" 60 > "$iso/log.txt" 2>&1 || echo "FALHOU s$s $r"
      cp "$iso/o/desempenho.txt" "$R/desempenho/s$s-$r.txt" 2>/dev/null || cp "$iso/log.txt" "$R/desempenho/s$s-$r.FALHA.txt"
      grep -E "^mundo|^medir-geracao" "$iso/log.txt" >> "$R/desempenho/geracao-s$s.txt" || true
      rm -rf "$iso"
    done
  done
esac

case "$what" in testes|tudo)
  ./gradlew test --console=plain > "$R/testes.log" 2>&1 || true
  .venv/bin/python - > "$R/testes.txt" <<'EOF'
import glob, collections, xml.etree.ElementTree as ET
n = f = sk = 0; by = collections.Counter(); cls = {}
for x in glob.glob('*/build/test-results/test/*.xml'):
    r = ET.parse(x).getroot(); t = int(r.get('tests')); n += t; sk += int(r.get('skipped')); by[x.split('/')[0]] += t; cls[r.get('name').split('.')[-1]] = t
    for tc in r.findall('testcase'):
        if tc.find('failure') is not None or tc.find('error') is not None: f += 1; print('FALHA', r.get('name'), tc.get('name'))
print(f"testes {n}, falhas {f}, pulados {sk}: {dict(by)}")
for c in ['ProceduralWorldTest', 'QuestTest', 'AdventureTest', 'SaveGameTest']: print(c, cls.get(c))
EOF
  tail -5 "$R/testes.txt"
esac

case "$what" in validadores|tudo)
  V=viewer/build/install/viewer/bin/viewer
  { for a in "" "interrupcao-fina" "golpe-andando" "esquiva"; do $V --check $a 2>&1 | tail -1; done
    $V --check-cabelos 2>&1 | tail -1; $V --occlusion 2>&1 | tail -1
    tools/review7.sh gate 2>&1 | grep pares
    python3 tools/check_links.py 2>&1 | tail -1
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json 2>&1 | grep arquivos | tail -1; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in regressao|tudo)
  # o mundo fixo não pode mudar: referências canônicas (viewer) e a aventura da rodada 10 (jogo)
  rm -rf output/tmp/ref-r11 && tools/reference.sh output/tmp/ref-r11 > /dev/null 2>&1
  .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref-r11 > "$R/referencias.txt" 2>&1 || true
  tail -1 "$R/referencias.txt"
  iso=$(isolado)
  for p in 01-aventura 02-continuar; do
    JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $G --roteiro "tools/aventura/$p.txt" "$iso/$p" 30 > "$iso/$p.log" 2>&1 || true
  done
  grep -h "^missao" "$iso/01-aventura.log" "$iso/02-continuar.log" > "$R/registros/aventura-rodada-10-no-binario-11.txt" || true
  tail -1 "$R/registros/aventura-rodada-10-no-binario-11.txt"
  rm -rf "$iso"
esac

case "$what" in zip|tudo)
  rm -f "$R/rodada-11-revisao.zip"
  (cd "$R" && zip -qr rodada-11-revisao.zip . -i '*.md' '*.txt' '*.png' '*.jpg' '*.json' -x 'desempenho/*.gc.log')
  ls -la "$R/rodada-11-revisao.zip"
esac
