#!/usr/bin/env bash
# Rodada 12.1 — pacote de revisão: acabamento da v2 (torre, piso, câmera, vilas de caminho, moradores dos ofícios,
# descobertas por tipo, salto, colisão exata). Dados SEMPRE temporários (cópias, conferidas por hash antes e depois).
# Som só por loopback ou mudo. "Antes" = o binário da rodada 12 num clone compilado (BASE=...).
#
# Uso: tools/review121.sh [vistas|antes|comparacoes|laje|demo|demo-v1|saves|desempenho|testes|validadores|zip|tudo]
#   BASE        = clone compilado da rodada 12 (padrão output/tmp/base-r12), para "antes", "laje" e "desempenho"
#   RAIZ_ANTIGA = clone compilado da main publicada (4cbde86), para "saves" e "demo-v1"
#   PARTE=regioes  = "desempenho" só das regiões (vila fixa e menu já medidos na mesma sessão)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-12-1}
G=game/build/install/game/bin/game
BASE=${BASE:-output/tmp/base-r12}
what="${1:-tudo}"
SEEDS=${SEEDS:-"1024 2048 4096 77 1 31337 7 14 6 31 555 33"}
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { sha256sum output/aparencias/*.json output/saves/aventura.json 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
isolado() {
  local d; d="$(realpath "$(mktemp -d output/tmp/r121.XXXXXX)")"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
regiao() {  # regiao RAIZ VERSÃO SEMENTE ROTEIRO SAIDA (a raiz é a do binário: assets dela)
  local out; out="$(realpath -m "$5")"; mkdir -p "$(dirname "$out")"
  (cd "$1" && JAVA_OPTS="-Dvx.som=mudo -Dvx.mundo=procedural -Dvx.gerador=$2 -Dvx.semente=$3 -Dvx.savegame=- -Dvx.prefs=-" $G --roteiro "$4" "$out" 30) > "$out.log" 2>&1 \
    || { echo "FALHOU v$2 s$3 (veja $out.log)"; tail -5 "$out.log"; }
}
video() { ffmpeg -loglevel error -y -framerate 30 -i "$1/q_%05d.png" -i "$2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"; }
prancha() { .venv/bin/python tools/qa/prancha.py "$@" > /dev/null; }

case "$what" in vistas|tudo)
  mkdir -p "$R/capturas"
  for s in $SEEDS; do
    regiao . 2 $s "$PWD/tools/mundo/04-vistas-v2.txt" "$R/capturas/v2-s$s"
    prancha "$R/capturas/prancha-v2-s$s.png" 5 480 $(ls "$R/capturas/v2-s$s"/*.png | sort)
  done
  for s in 1024 2048 4096 77; do regiao . 1 $s "$PWD/tools/mundo/01-vistas.txt" "$R/capturas/v1-s$s"; done
esac

case "$what" in antes|tudo)
  # a v2 da rodada 12 (binário e assets dela) nas mesmas sementes, para as comparações
  mkdir -p "$R/antes"
  for s in 1 31337 7 14 1024 77 2048 4096; do regiao "$BASE" 2 $s "$PWD/$BASE/tools/mundo/04-vistas-v2.txt" "$R/antes/v2-s$s"; done
esac

case "$what" in comparacoes|tudo)
  mkdir -p "$R/comparacoes"
  for s in 1 31337 7 14 1024 77 2048 4096; do
    [ -d "$R/antes/v2-s$s" ] && [ -d "$R/capturas/v2-s$s" ] || continue
    prancha "$R/comparacoes/s$s-r12-r121.png" 2 760 "$R/antes/v2-s$s/01-geral.png" "$R/capturas/v2-s$s/01-geral.png" \
      "$R/antes/v2-s$s/02-assentamento.png" "$R/capturas/v2-s$s/02-assentamento.png" "$R/antes/v2-s$s/07-ruina.png" "$R/capturas/v2-s$s/07-ruina.png"
  done
  for s in 1024 2048 4096 77; do
    [ -d "$R/capturas/v1-s$s" ] || continue
    prancha "$R/comparacoes/s$s-v1-v2.png" 2 760 "$R/capturas/v1-s$s/1-geral.png" "$R/capturas/v2-s$s/01-geral.png" \
      "$R/capturas/v1-s$s/2-assentamento.png" "$R/capturas/v2-s$s/02-assentamento.png"
  done
esac

case "$what" in laje|tudo)
  # entrar andando pelo arco da ruína de casa (semente 77: casa nas duas): rodada 12 (laje sem degrau, piso a 0,30 u) ×
  # rodada 12.1 (degraus de pedra, piso na laje). Quadros do passo pela soleira
  mkdir -p "$R/laje"
  printf 'size 1280 720\nclick comecar\nwait 1.0\nir-lugar ruina\nwait 0.8\nrecord on\nir-lugar ruina-dentro andando\nwait 0.6\nestado\nrecord off\n' > output/tmp/r121-laje.txt
  rt="$(realpath output/tmp/r121-laje.txt)"
  for lado in antes depois; do
    raiz=.; [ "$lado" = antes ] && raiz="$BASE"
    d="$(realpath -m output/tmp/r121-laje-$lado)"; rm -rf "$d"
    (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo -Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=77 -Dvx.savegame=- -Dvx.prefs=-" $G --roteiro "$rt" "$d" 30) > "$R/laje/$lado.log" 2>&1 || true
    prancha "$R/laje/laje-$lado.png" 6 320 $(ls "$d"/quadros/q_*.png | awk 'NR%5==1' | head -24)
  done
esac

case "$what" in demo|tudo)
  mkdir -p "$R/videos"
  iso=$(isolado)
  JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/mundo/07-demo-v2-torre.txt "$iso/d" 30 > "$R/registros/demo-torre.log" 2>&1
  video "$iso/d/quadros" "$iso/d/audio.wav" "$R/videos/01-torre-mirante-arvore-salvar.mp4"
  prancha "$R/videos/01-prancha.png" 8 240 $(ls "$iso/d/quadros"/q_*.png | awk 'NR%60==1')
  mkdir -p "$R/registros/exemplos"; cp "$iso"/mundos/m-*/mundo.json "$R/registros/exemplos/mundo-v2.json" 2>/dev/null || true
  cp "$iso"/mundos/m-*/aventuras/*/aventura.json "$R/registros/exemplos/aventura-v2-mirante-arvore.json" 2>/dev/null || true
  rm -rf "$iso"
  d="$(realpath -m output/tmp/r121-casa)"; rm -rf "$d"
  JAVA_OPTS="-Dvx.som=loopback -Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=14 -Dvx.savegame=- -Dvx.prefs=-" $G --roteiro tools/mundo/08-demo-v2-casa.txt "$d" 30 > "$R/registros/demo-casa.log" 2>&1
  video "$d/quadros" "$d/audio.wav" "$R/videos/02-casa-degraus-pote.mp4"
  prancha "$R/videos/02-prancha.png" 8 240 $(ls "$d/quadros"/q_*.png | awk 'NR%45==1')
  rm -rf "$d"
esac

case "$what" in saves|tudo)
  : "${RAIZ_ANTIGA:?defina RAIZ_ANTIGA (checkout compilado da main publicada)}"
  tools/qa/saves_antigos.sh "$RAIZ_ANTIGA" . output/tmp/r121-saves > "$R/registros/saves-antigos.txt" 2>&1 || true
  tail -14 "$R/registros/saves-antigos.txt"
esac

case "$what" in demo-v1|tudo)
  : "${RAIZ_ANTIGA:?defina RAIZ_ANTIGA}"
  [ -d output/tmp/r121-saves/dados/menu ] || tools/qa/saves_antigos.sh "$RAIZ_ANTIGA" . output/tmp/r121-saves > /dev/null 2>&1 || true
  mkdir -p "$R/videos"
  d="$(realpath "$(mktemp -d output/tmp/r121v1.XXXXXX)")"; cp -a output/tmp/r121-saves/dados/menu/. "$d/"
  JAVA_OPTS="-Dvx.som=loopback -Dvx.menu=sim -Dvx.personagens=$d/p -Dvx.mundos=$d/m -Dvx.savegame=$d/s -Dvx.saves=$d/a" \
    $G --roteiro tools/mundo/06-demo-v1-antigo.txt "$d/v" 30 > "$R/registros/demo-v1.log" 2>&1
  video "$d/v/quadros" "$d/v/audio.wav" "$R/videos/03-mundo-v1-antigo.mp4"
  rm -rf "$d"
esac

case "$what" in desempenho|tudo)
  # os DOIS binários (rodada 12 × 12.1), intercalados, 3 vezes, mesma sessão: vila fixa, região v1 1024, v2 1024 (rural),
  # v2 7 (caminho média) e v2 31337 (caminho pequena), com a GPU por passo; e o roteiro de memória do menu
  D="$R/desempenho"; mkdir -p "$D"
  run() {  # run RAIZ ROTULO ROTEIRO [opções]
    local raiz=$1 rot=$2 rt=$3 out; out="$(realpath -m output/tmp/r121p)"; rm -rf "$out"
    (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=- -Dvx.prefs=- ${4:-}" $G --roteiro "$rt" "$out" 60) > "$D/$rot.log" 2>&1 || echo "FALHOU $rot"
    cp "$out/desempenho.txt" "$D/$rot.txt" 2>/dev/null || true
  }
  for r in 1 2 3; do
    for lado in r12 r121; do
      raiz=.; [ "$lado" = r12 ] && raiz="$BASE"
      [ "${PARTE:-tudo}" = regioes ] || run "$raiz" "$lado-vila-fixa-$r" "$PWD/tools/jogo/desempenho.txt"
      run "$raiz" "$lado-v1-s1024-$r" "$PWD/tools/mundo/desempenho.txt" "-Dvx.mundo=procedural -Dvx.gerador=1 -Dvx.semente=1024"
      for s in 1024 7 31337; do run "$raiz" "$lado-v2-s$s-$r" "$PWD/tools/mundo/desempenho.txt" "-Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=$s"; done
    done
  done
  for r in 1 2 3; do
    [ "${PARTE:-tudo}" = regioes ] && break                        # PARTE=regioes: só as regiões (vila e menu já medidos)
    for lado in r12 r121; do
      raiz=.; [ "$lado" = r12 ] && raiz="$BASE"
      iso=$(isolado)
      (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $G --roteiro "$PWD/tools/menu/14-memoria.txt" "$iso/o" 30) > "$D/menu-$lado-$r.log" 2>&1 || echo "FALHOU menu $lado $r"
      rm -rf "$iso"
    done
  done
  python3 tools/qa/resumo_desempenho121.py "$D" > "$D/resumo.md"; cat "$D/resumo.md"
esac

case "$what" in testes|tudo)
  ./gradlew test --console=plain -q > "$R/registros/testes.log" 2>&1 || true
  python3 - <<'PY' | tee "$R/testes.txt"
import glob, collections, xml.etree.ElementTree as ET
t = f = e = sk = 0; by = collections.Counter(); rows = []
for x in sorted(glob.glob('*/build/test-results/test/*.xml')):
    r = ET.parse(x).getroot(); n = int(r.get('tests')); t += n; f += int(r.get('failures')); e += int(r.get('errors')); sk += int(r.get('skipped'))
    by[x.split('/')[0]] += n; rows.append((x.split('/')[0], r.get('name').split('.')[-1], n))
print(f"testes {t}, falhas {f}, erros {e}, pulados {sk}: {dict(by)}")
for m, c, n in rows: print(f"{m:7} {c:40} {n:4}")
PY
esac

case "$what" in validadores|tudo)
  V=viewer/build/install/viewer/bin/viewer
  { for c in "--check" "--check interrupcao-fina" "--check golpe-andando" "--check esquiva" "--check-cabelos"; do echo "$c: $($V $c 2>&1 | tail -1)"; done
    echo "--occlusion: $($V --occlusion 2>&1 | tail -1) (sai com 1 pela exceção conhecida do arco)"
    tools/review7.sh gate 2>&1 | grep -E "pares|gate"
    python3 tools/check_links.py 2>&1 | tail -1
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json 2>&1 | grep arquivos | tail -1
    rm -rf output/tmp/r121-ref; tools/reference.sh output/tmp/r121-ref > /dev/null 2>&1
    .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/r121-ref 2>&1 | tail -1; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in zip|tudo)
  (cd "$(dirname "$R")" && rm -f "$(basename "$R")/rodada-12-1-revisao.zip" && zip -qr "$(basename "$R")/rodada-12-1-revisao.zip" "$(basename "$R")" \
    -x "*.mp4" -x "*/capturas/v*-s*/*" -x "*/antes/v*-s*/*" -x "*/rodada-12-1-revisao.zip" -x "*/desempenho/*.log")
  ls -la "$R/rodada-12-1-revisao.zip"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"
