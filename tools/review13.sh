#!/usr/bin/env bash
# Rodada 13 — pacote de revisão da mochila: a demonstração principal pelo menu, um save da v0.6 aberto por este jogo, a
# mochila nas resoluções oficiais, a pausa antes × depois, desempenho v0.6 × rodada 13, saves antigos, testes,
# validadores e ZIP. Dados SEMPRE temporários (cópias, conferidas por sha256 antes e depois). Som só por loopback ou mudo.
#
# Uso: tools/review13.sh [videos|capturas|desempenho|saves|testes|validadores|zip|tudo]
#   BASE = clone compilado do checkpoint procedural-world-v0.6 (padrão output/tmp/base-v06)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-13}
G=$PWD/game/build/install/game/bin/game
BASE=${BASE:-output/tmp/base-v06}
GB=$PWD/$BASE/game/build/install/game/bin/game
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { find output/mundos output/personagens output/saves output/aparencias output/prefs* -type f 2>/dev/null | sort | xargs -d '\n' sha256sum 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
isolado() {
  local d; d="$(realpath "$(mktemp -d output/tmp/r13.XXXXXX)")"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
video() { ffmpeg -loglevel error -y -framerate 30 -i "$1/q_%05d.png" -i "$2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"; }
prancha() { .venv/bin/python tools/qa/prancha.py "$@" > /dev/null; }
regiao() { echo "-Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=$1 -Dvx.savegame=- -Dvx.prefs=-"; }

case "$what" in videos|tudo)
  V="$R/videos"; mkdir -p "$V"
  # 01 a demonstração principal: menu → personagem → mundo novo (semente 7) → presente, achados, luta, cura → salvar → menu
  #    → continuar → mochila igual → voltar às fontes (nada duplica)
  iso=$(isolado)
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/itens/01-demo.txt "$iso/d" 30) > "$R/registros/01-demo.log" 2>&1 || echo "FALHOU demo"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/01-mochila-do-comeco-ao-continuar.mp4"
  prancha "$V/01-prancha.png" 8 240 $(ls "$iso/d/quadros"/q_*.png | awk 'NR%60==1')
  rm -rf "$iso"
  # 02 um save feito pela v0.6 aberto por este jogo: o binário da v0.6 cria o mundo "Torre Caida" e salva; este abre
  iso=$(isolado)
  (cd "$BASE" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $GB --roteiro "$PWD/tools/menu/15-voltar-e-continuar.txt" "$iso/v06" 30) > "$R/registros/02-v06-cria.log" 2>&1 || echo "FALHOU v0.6"
  find "$iso/mundos" -name aventura.json | sort | xargs -d '\n' sha256sum > "$R/registros/02-saves-v06.sha256"
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/itens/02-save-v06.txt "$iso/d" 30) > "$R/registros/02-save-v06.log" 2>&1 || echo "FALHOU save v0.6"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/02-save-da-v06-aberto.mp4"
  prancha "$V/02-prancha.png" 8 240 $(ls "$iso/d/quadros"/q_*.png | awk 'NR%40==1')
  rm -rf "$iso"
  ls -la "$V"
esac

case "$what" in capturas|tudo)
  C="$R/capturas"; mkdir -p "$C/resolucoes" "$C/pausa"
  out="$(realpath -m output/tmp/r13cap)"; rm -rf "$out"
  (JAVA_OPTS="-Dvx.som=mudo $(regiao 7)" $G --roteiro tools/itens/03-resolucoes.txt "$out" 30) > "$R/registros/03-resolucoes.log" 2>&1 || echo "FALHOU resoluções"
  cp "$out"/*.png "$C/resolucoes/"
  printf 'size 1920 1080\nclick comecar\nwait 1.0\nkey ESC\nwait 0.5\nshot pausa-1080\nsize 1280 720\nwait 0.3\nshot pausa-720\n' > "$out/pausa.txt"
  for lado in v06 r13; do
    bin=$G; raiz=.; [ "$lado" = v06 ] && { bin=$GB; raiz="$BASE"; }
    (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo $(regiao 7)" $bin --roteiro "$out/pausa.txt" "$out/$lado" 30) > "$R/registros/pausa-$lado.log" 2>&1
    for f in "$out/$lado"/*.png; do cp "$f" "$C/pausa/$lado-$(basename "$f")"; done
  done
  prancha "$C/pausa/pausa-v06-x-r13.png" 4 480 "$C/pausa/v06-pausa-720.png" "$C/pausa/r13-pausa-720.png" "$C/pausa/v06-pausa-1080.png" "$C/pausa/r13-pausa-1080.png"
  prancha "$C/resolucoes/prancha.png" 4 480 $(ls "$C/resolucoes"/r*.png | sort)
esac

case "$what" in desempenho|tudo)
  # os DOIS binários (v0.6 × rodada 13), intercalados, 3 vezes, mesma sessão, GPU por passo: a vila fixa e a região da
  # semente 7 (a mochila fechada); o menu (tempo até o menu, entrar no mundo, memória); e só a rodada 13: a mochila vazia,
  # cheia e aberta, mais gravar e ler o save com a mochila cheia
  D="$R/desempenho"; mkdir -p "$D"
  run() {  # run RAIZ BINÁRIO ROTULO ROTEIRO [opções]
    local out; out="$(realpath -m output/tmp/r13p)"; rm -rf "$out"
    (cd "$1" && JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=- -Dvx.prefs=- ${5:-}" "$2" --roteiro "$4" "$out" 60) > "$D/$3.log" 2>&1 || echo "FALHOU $3"
    for f in "$out"/*.txt; do [ -f "$f" ] && cp "$f" "$D/$3-$(basename "$f")"; done
  }
  for r in 1 2 3; do
    for lado in v06 r13; do
      raiz=.; bin=$G; [ "$lado" = v06 ] && { raiz="$BASE"; bin=$GB; }
      run "$raiz" "$bin" "$lado-vila-fixa-$r" "$PWD/tools/jogo/desempenho.txt"
      run "$raiz" "$bin" "$lado-v2-s7-$r" "$PWD/tools/itens/desempenho-fechado.txt" "-Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=7"
      iso=$(isolado)
      (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $bin --roteiro "$PWD/tools/menu/14-memoria.txt" "$iso/o" 30) > "$D/menu-$lado-$r.log" 2>&1 || echo "FALHOU menu $lado $r"
      rm -rf "$iso"
    done
    sv="$(realpath "$(mktemp -d output/tmp/r13s.XXXXXX)")"
    (JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=$sv -Dvx.prefs=- -Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=7" \
      $G --roteiro tools/itens/desempenho-mochila.txt "$(realpath -m output/tmp/r13m)" 60) > "$D/r13-mochila-$r.log" 2>&1 || echo "FALHOU mochila $r"
    for f in output/tmp/r13m/*.txt; do [ -f "$f" ] && cp "$f" "$D/r13-mochila-$r-$(basename "$f")"; done
    rm -rf "$sv"
  done
  python3 tools/qa/resumo_desempenho13.py "$D" > "$D/resumo.md"; cat "$D/resumo.md"
esac

case "$what" in saves|tudo)
  tools/qa/saves_antigos.sh "$BASE" . output/tmp/r13-saves > "$R/registros/saves-antigos.txt" 2>&1 || true
  tail -14 "$R/registros/saves-antigos.txt"
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
    rm -rf output/tmp/r13-ref; tools/reference.sh output/tmp/r13-ref > /dev/null 2>&1
    echo "referências: $(ls output/tmp/r13-ref/*.png 2>/dev/null | wc -l) capturas; $(.venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/r13-ref 2>&1 | tail -1)"; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in zip|tudo)
  (cd "$(dirname "$R")" && rm -f "$(basename "$R")/rodada-13-revisao.zip" && zip -qr "$(basename "$R")/rodada-13-revisao.zip" "$(basename "$R")" \
    -x "*.mp4" -x "*/rodada-13-revisao.zip" -x "*/desempenho/*.log" -x "*/registros/*.log")
  ls -la "$R/rodada-13-revisao.zip"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"
