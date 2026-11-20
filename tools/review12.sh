#!/usr/bin/env bash
# Rodada 12 — pacote de revisão: gerador v2, colisão composta, descoberta. Dados SEMPRE temporários (cópias das
# aparências e do save do proprietário, conferidos por hash antes e depois). Som só por loopback ou mudo.
#
# Uso: tools/review12.sh [vistas|comparacoes|demo|demo-v1|saves|desempenho|testes|validadores|zip|tudo]
#   saves e demo-v1 precisam do binário publicado ANTES da rodada: RAIZ_ANTIGA=<checkout da main anterior, já compilado>
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-12}
G=game/build/install/game/bin/game
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { sha256sum output/aparencias/*.json output/saves/aventura.json 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
isolado() {
  local d; d="$(mktemp -d output/tmp/r12.XXXXXX)"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
regiao() {  # regiao VERSÃO SEMENTE ROTEIRO SAIDA
  JAVA_OPTS="-Dvx.som=mudo -Dvx.mundo=procedural -Dvx.gerador=$1 -Dvx.semente=$2 -Dvx.savegame=-" $G --roteiro "$3" "$4" 30 > "$4.log" 2>&1 \
    || { echo "FALHOU v$1 s$2 (veja $4.log)"; tail -5 "$4.log"; }
}
video() {  # video QUADROS AUDIO SAIDA.mp4
  ffmpeg -loglevel error -y -framerate 30 -i "$1/q_%05d.png" -i "$2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"
}

case "$what" in vistas|tudo)
  mkdir -p "$R/capturas"
  for s in 1024 2048 4096 77 1 555 9001 31337; do
    regiao 2 $s tools/mundo/04-vistas-v2.txt "$R/capturas/v2-s$s"
    .venv/bin/python tools/qa/prancha.py "$R/capturas/prancha-v2-s$s.png" 5 480 $(ls "$R/capturas/v2-s$s"/*.png | sort) > /dev/null
  done
  for s in 1024 2048 4096 77; do regiao 1 $s tools/mundo/01-vistas.txt "$R/capturas/v1-s$s"; done
esac

case "$what" in comparacoes|tudo)
  mkdir -p "$R/comparacoes"
  for s in 1024 2048 4096 77; do
    [ -d "$R/capturas/v1-s$s" ] || regiao 1 $s tools/mundo/01-vistas.txt "$R/capturas/v1-s$s"
    [ -d "$R/capturas/v2-s$s" ] || regiao 2 $s tools/mundo/04-vistas-v2.txt "$R/capturas/v2-s$s"
    .venv/bin/python tools/qa/prancha.py "$R/comparacoes/s$s-v1-v2.png" 2 760 "$R/capturas/v1-s$s/1-geral.png" "$R/capturas/v2-s$s/01-geral.png" \
      "$R/capturas/v1-s$s/2-assentamento.png" "$R/capturas/v2-s$s/02-assentamento.png" > /dev/null
  done
esac

case "$what" in demo|tudo)
  mkdir -p "$R/videos"
  iso=$(isolado)
  JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/mundo/05-demo-v2.txt "$iso/d" 30 > "$R/registros/demo-v2.log" 2>&1
  video "$iso/d/quadros" "$iso/d/audio.wav" "$R/videos/01-mundo-v2-descoberta.mp4"
  rm -rf "$iso"
esac

case "$what" in saves|tudo)
  : "${RAIZ_ANTIGA:?defina RAIZ_ANTIGA (checkout compilado da main anterior)}"
  tools/qa/saves_antigos.sh "$RAIZ_ANTIGA" . output/tmp/r12-saves > "$R/registros/saves-antigos.txt" 2>&1 || true
  tail -14 "$R/registros/saves-antigos.txt"
esac

case "$what" in demo-v1|tudo)
  : "${RAIZ_ANTIGA:?defina RAIZ_ANTIGA}"
  [ -d output/tmp/r12-saves/dados/menu ] || tools/qa/saves_antigos.sh "$RAIZ_ANTIGA" . output/tmp/r12-saves > /dev/null 2>&1 || true
  mkdir -p "$R/videos"
  d="$(mktemp -d output/tmp/r12v1.XXXXXX)"; cp -a output/tmp/r12-saves/dados/menu/. "$d/"
  JAVA_OPTS="-Dvx.som=loopback -Dvx.menu=sim -Dvx.personagens=$d/p -Dvx.mundos=$d/m -Dvx.savegame=$d/s -Dvx.saves=$d/a" \
    $G --roteiro tools/mundo/06-demo-v1-antigo.txt "$d/v" 30 > "$R/registros/demo-v1.log" 2>&1
  video "$d/v/quadros" "$d/v/audio.wav" "$R/videos/02-mundo-v1-antigo.mp4"
  rm -rf "$d"
esac

case "$what" in desempenho|tudo)
  # MESMO binário, MESMA sessão, intercalado, 3 vezes (nada compilando em paralelo): vila fixa (roteiro da 08), região v1
  # 1024, v2 1024 (rural) e v2 31337 (de caminho), com tempo de GPU por passo (-Dvx.gpu=true: a sombra em separado).
  # Depois, o bench do viewer (vila do jogo, vitrines A/B, bench denso) em duas rodadas de ordem inversa, e o roteiro de
  # memória do menu (tools/menu/14-memoria.txt: o mundo criado pela interface nasce na versão atual). RAIZ_ANTIGA, se
  # definida, roda o MESMO roteiro de memória no binário publicado (lá o mundo nasce v1).
  D="$R/desempenho"; mkdir -p "$D"; P=output/tmp/r12p
  for r in 1 2 3; do
    iso=$(isolado)
    JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=- -Dvx.prefs=- -Dvx.saves=$iso/aparencias" $G --roteiro tools/jogo/desempenho.txt "$P" 60 > "$D/vila-fixa-$r.log" 2>&1 || echo "FALHOU vila $r"
    cp "$P/desempenho.txt" "$D/vila-fixa-$r.txt"; rm -rf "$iso" "$P"
    for c in "1 1024" "2 1024" "2 31337"; do
      set -- $c
      JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.mundo=procedural -Dvx.gerador=$1 -Dvx.semente=$2 -Dvx.savegame=- -Dvx.prefs=-" $G --roteiro tools/mundo/desempenho.txt "$P" 60 > "$D/v$1-s$2-$r.log" 2>&1 || echo "FALHOU v$1 s$2 $r"
      cp "$P/desempenho.txt" "$D/v$1-s$2-$r.txt"; rm -rf "$P"
    done
  done
  V=viewer/build/install/viewer/bin/viewer; k=0
  for ordem in "jogo_vila_e vitrine_conteudo_vila_a vitrine_conteudo_vila_b bench_conteudo_alta" "bench_conteudo_alta vitrine_conteudo_vila_b vitrine_conteudo_vila_a jogo_vila_e"; do
    k=$((k + 1))
    for cena in $ordem; do
      JAVA_OPTS=-Dvx.gpu=true $V --layout "$cena" --bench "1920x1080,style=e,quality=padrao,runs=3,frames=300,out=$D/bench-$cena-$k.csv,dump=$D/bench-$cena-$k.quadros.csv" > "$D/bench-$cena-$k.txt" 2>&1 || echo "FALHOU bench $cena"
    done
  done
  for r in 1 2 3; do
    iso=$(isolado)
    JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $G --roteiro tools/menu/14-memoria.txt "$iso/o" 30 > "$D/menu-novo-$r.log" 2>&1 || echo "FALHOU menu novo $r"
    for q in quadro-menu quadro-vila; do cp "$iso/o/$q.txt" "$D/menu-novo-$r-$q.txt" 2>/dev/null || true; done
    rm -rf "$iso"
    if [ -n "${RAIZ_ANTIGA:-}" ]; then
      iso=$(isolado); a="$(realpath "$iso")"
      (cd "$RAIZ_ANTIGA" && JAVA_OPTS="-Dvx.som=mudo $(opts "$a")" $G --roteiro tools/menu/14-memoria.txt "$a/o" 30) > "$D/menu-publicado-$r.log" 2>&1 || echo "FALHOU menu publicado $r"
      for q in quadro-menu quadro-vila; do cp "$iso/o/$q.txt" "$D/menu-publicado-$r-$q.txt" 2>/dev/null || true; done
      rm -rf "$iso"
    fi
  done
  python3 tools/qa/resumo_desempenho12.py "$D" > "$D/resumo.md"; cat "$D/resumo.md"
esac

case "$what" in testes|tudo)
  ./gradlew test --console=plain -q > "$R/registros/testes.txt" 2>&1 || true
  python3 - <<'PY' | tee -a "$R/registros/testes.txt"
import glob, xml.etree.ElementTree as ET
t = f = e = 0
for x in glob.glob('*/build/test-results/test/*.xml'):
    r = ET.parse(x).getroot(); t += int(r.get('tests')); f += int(r.get('failures')); e += int(r.get('errors'))
print('testes', t, 'falhas', f, 'erros', e)
PY
esac

case "$what" in validadores|tudo)
  V=viewer/build/install/viewer/bin/viewer
  for c in "--check" "--check interrupcao-fina" "--check golpe-andando" "--check esquiva" "--check-cabelos"; do
    $V $c > "$R/registros/val-$(echo $c | tr -d ' -').txt" 2>&1; echo "$c: $(tail -1 "$R/registros/val-$(echo $c | tr -d ' -').txt")"
  done
  $V --occlusion > "$R/registros/val-occlusion.txt" 2>&1 || true; echo "--occlusion: $(tail -1 "$R/registros/val-occlusion.txt") (sai com 1 pela exceção conhecida do arco)"
  tools/review7.sh gate > "$R/registros/gate.txt" 2>&1; tail -2 "$R/registros/gate.txt"
  python3 tools/check_links.py > "$R/registros/links.txt" 2>&1 || true; tail -1 "$R/registros/links.txt"
  .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json > "$R/registros/audio-qa.txt" 2>&1 || true; grep arquivos "$R/registros/audio-qa.txt" | tail -1
  rm -rf output/tmp/r12-ref; tools/reference.sh output/tmp/r12-ref > /dev/null 2>&1
  .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/r12-ref > "$R/registros/referencias.txt" 2>&1; tail -1 "$R/registros/referencias.txt"
esac

case "$what" in zip|tudo)
  (cd "$(dirname "$R")" && rm -f "$(basename "$R").zip" && zip -qr "$(basename "$R").zip" "$(basename "$R")" -x "*.mp4" -x "*/capturas/v*-s*/*")
  ls -la "$R.zip"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"
