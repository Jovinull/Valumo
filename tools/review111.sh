#!/usr/bin/env bash
# Rodada 11.1 — pacote de revisão da porta de entrada (menu principal, personagens, mundos, aventura = personagem × mundo).
# TODOS os dados ficam numa pasta temporária: cópias das aparências e do save do proprietário (os originais são
# conferidos por hash antes e depois), personagens, mundos, saves e preferências. Som só por loopback ou mudo.
#
# Uso: tools/review111.sh [capturas|resolucoes|video|desempenho|transicoes|testes|validadores|regressao|zip|tudo]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-11-1}
G=game/build/install/game/bin/game
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { sha256sum output/aparencias/*.json output/saves/aventura.json 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
# pasta isolada com CÓPIAS dos dados antigos do proprietário (o jogo salvo de antes é adotado ao abrir o menu)
isolado() {
  local d; d="$(mktemp -d output/tmp/r111.XXXXXX)"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
roda() {  # roda ROTEIRO PASTA_DADOS SAIDA [extra]
  JAVA_OPTS="-Dvx.som=${SOM:-mudo} $(opts "$2") ${4:-}" $G --roteiro "$1" "$3" 30 > "$3.log" 2>&1 || { echo "FALHOU $1 (veja $3.log)"; tail -5 "$3.log"; }
}

case "$what" in capturas|tudo)
  # A → B → C → D na MESMA pasta (cada roteiro parte do que o anterior deixou)
  mkdir -p "$R/capturas"
  iso=$(isolado)
  roda tools/menu/01-menu-principal.txt "$iso" "$iso/a"
  roda tools/menu/02-personagens.txt "$iso" "$iso/b"
  roda tools/menu/03-mundos.txt "$iso" "$iso/c" "-Dvx.qa.recusar=4242"
  roda tools/menu/04-aventura.txt "$iso" "$iso/d"
  for x in a b c d; do cp "$iso/$x"/*.png "$R/capturas/"; grep -hE "^menu|^esperar-menu|^transi|^missao|^estado|^prévia" "$iso/$x.log" > "$R/registros/roteiro-$x.txt" || true; done
  (cd "$iso" && find personagens mundos saves -type f | sort) > "$R/registros/arquivos-gerados.txt"
  cp -r "$iso/mundos" "$R/registros/mundos-exemplo"; find "$R/registros/mundos-exemplo" -name '*.png' -delete
  cp -r "$iso/personagens" "$R/registros/personagens-exemplo"
  rm -rf "$iso"
esac

case "$what" in resolucoes|tudo)
  mkdir -p "$R/resolucoes"
  iso=$(isolado)
  roda tools/menu/02-personagens.txt "$iso" "$iso/b"
  roda tools/menu/03-mundos.txt "$iso" "$iso/c" "-Dvx.qa.recusar=4242"
  roda tools/menu/07-resolucoes.txt "$iso" "$iso/r"
  cp "$iso"/r/*.png "$R/resolucoes/"
  .venv/bin/python tools/qa/prancha.py "$R/resolucoes/comparacao-leiautes-a-b.png" 2 960 "$iso"/r/r*-menu-*.png > /dev/null
  rm -rf "$iso"
esac

case "$what" in video|tudo)
  mkdir -p "$R/videos"
  iso=$(isolado)
  roda tools/menu/02-personagens.txt "$iso" "$iso/b"
  SOM=loopback roda tools/menu/08-video.txt "$iso" "$iso/v"
  ffmpeg -loglevel error -y -framerate 30 -i "$iso/v/quadros/q_%05d.png" -i "$iso/v/audio.wav" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$R/videos/01-porta-de-entrada.mp4"
  ffmpeg -loglevel error -y -i "$R/videos/01-porta-de-entrada.mp4" -vf "fps=1/3,scale=480:-1,tile=5x5" -frames:v 1 -q:v 3 "$R/capturas/prancha-video.jpg"
  grep -hE "^menu|^transi|^som" "$iso/v.log" > "$R/registros/video.txt" || true
  rm -rf "$iso"
  echo "vídeo: $R/videos/01-porta-de-entrada.mp4"
esac

case "$what" in desempenho|tudo)
  mkdir -p "$R/desempenho"
  for r in 1 2 3; do
    iso=$(isolado)
    roda tools/menu/02-personagens.txt "$iso" "$iso/b"
    roda tools/menu/03-mundos.txt "$iso" "$iso/c" "-Dvx.qa.recusar=4242"
    roda tools/menu/05-desempenho.txt "$iso" "$iso/e" "-Xlog:gc*:file=$R/desempenho/menu-$r.gc.log::filecount=0"
    for f in "$iso"/e/desempenho-*.txt; do cp "$f" "$R/desempenho/$(basename "$f" .txt)-$r.txt"; done
    grep -h "^menu: primeiro" "$iso/e.log" >> "$R/desempenho/tempo-ate-o-menu.txt" || true
    grep -h "^transi" "$iso/c.log" >> "$R/desempenho/transicoes-criar-mundo.txt" || true
    rm -rf "$iso"
  done
  # referência no mesmo binário: o jogo sem menu (vila da rodada 08)
  iso=$(isolado)
  JAVA_OPTS="-Dvx.som=mudo -Dvx.prefs=- -Dvx.savegame=$iso/saves -Dvx.saves=$iso/aparencias" $G --roteiro tools/jogo/desempenho.txt "$iso/o" 60 > "$iso/log.txt" 2>&1 || true
  cp "$iso/o/desempenho.txt" "$R/desempenho/vila-sem-menu-referencia.txt" 2>/dev/null || true
  rm -rf "$iso"
esac

case "$what" in transicoes|tudo)
  mkdir -p "$R/desempenho"
  iso=$(isolado)
  roda tools/menu/02-personagens.txt "$iso" "$iso/b"
  roda tools/menu/03-mundos.txt "$iso" "$iso/c" "-Dvx.qa.recusar=4242"
  roda tools/menu/06-transicoes.txt "$iso" "$iso/t"
  cp "$iso/t/recursos.csv" "$R/desempenho/transicoes-recursos.csv"
  grep -hE "^menu-recursos|^transi" "$iso/t.log" > "$R/desempenho/transicoes.txt" || true
  # o mesmo com som real (loopback): fluxos e vozes do OpenAL não podem crescer
  SOM=loopback roda tools/menu/06-transicoes.txt "$iso" "$iso/t2"
  cp "$iso/t2/recursos.csv" "$R/desempenho/transicoes-recursos-com-som.csv"
  rm -rf "$iso"
  tail -2 "$R/desempenho/transicoes.txt"
esac

case "$what" in testes|tudo)
  ./gradlew test --console=plain > "$R/testes.log" 2>&1 || true
  .venv/bin/python - > "$R/testes.txt" <<'PY'
import glob, collections, xml.etree.ElementTree as ET
n = f = sk = 0; by = collections.Counter(); cls = {}
for x in glob.glob('*/build/test-results/test/*.xml'):
    r = ET.parse(x).getroot(); t = int(r.get('tests')); n += t; sk += int(r.get('skipped')); by[x.split('/')[0]] += t; cls[r.get('name').split('.')[-1]] = t
    for tc in r.findall('testcase'):
        if tc.find('failure') is not None or tc.find('error') is not None: f += 1; print('FALHA', r.get('name'), tc.get('name'))
print(f"testes {n}, falhas {f}, pulados {sk}: {dict(by)}")
for c in ['MenuStoresTest', 'MenuFlowTest', 'MenuAdventureTest', 'ApresentacaoTest', 'ProceduralWorldTest', 'AdventureTest', 'SaveGameTest']: print(c, cls.get(c))
PY
  cat "$R/testes.txt"
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
  rm -rf output/tmp/ref-r111 && tools/reference.sh output/tmp/ref-r111 > /dev/null 2>&1
  .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref-r111 > "$R/referencias.txt" 2>&1 || true
  tail -1 "$R/referencias.txt"
  # os roteiros antigos (sem menu: -Dvx.menu=nao, o padrão dos roteiros) continuam iguais
  iso=$(isolado)
  for p in tools/aventura/01-aventura.txt tools/aventura/02-continuar.txt tools/jogo/06-aparencia.txt; do
    n=$(basename "$p" .txt)
    JAVA_OPTS="-Dvx.som=mudo -Dvx.prefs=- -Dvx.savegame=$iso/saves -Dvx.saves=$iso/aparencias" $G --roteiro "$p" "$iso/$n" 30 > "$iso/$n.log" 2>&1 && echo "$n ok" || echo "$n FALHOU"
  done > "$R/registros/roteiros-antigos.txt"
  grep -h "^missao" "$iso/01-aventura.log" "$iso/02-continuar.log" >> "$R/registros/roteiros-antigos.txt" || true
  JAVA_OPTS="-Dvx.som=mudo -Dvx.prefs=- -Dvx.mundo=procedural -Dvx.semente=1024 -Dvx.savegame=$iso/sp" $G --roteiro tools/mundo/02-aventura.txt "$iso/m" 30 > "$iso/m.log" 2>&1 && echo "mundo-procedural ok" >> "$R/registros/roteiros-antigos.txt" || echo "mundo-procedural FALHOU" >> "$R/registros/roteiros-antigos.txt"
  rm -rf "$iso"
  cat "$R/registros/roteiros-antigos.txt"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"

case "$what" in zip|tudo)
  rm -f "$R/rodada-11-1-revisao.zip"
  (cd "$R" && zip -qr rodada-11-1-revisao.zip . -i '*.md' '*.txt' '*.png' '*.jpg' '*.json' '*.csv' -x 'desempenho/*.gc.log')
  ls -la "$R/rodada-11-1-revisao.zip"
esac
