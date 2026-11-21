#!/usr/bin/env bash
# Rodada 13.1 — pacote de revisão da mochila viva (sobre o jogo correndo, sem limite, itens em 3D): a demonstração principal
# pelo menu, um save da v0.6 e um da rodada 13 (com itens) abertos por este jogo, a mochila nas resoluções oficiais, antes ×
# depois (rodada 13 × 13.1), a prancha dos modelos, desempenho 13 × 13.1, saves antigos, testes, validadores e ZIP. Dados
# SEMPRE temporários (cópias, conferidas por sha256 antes e depois). Som só por loopback ou mudo.
#
# Uso: tools/review131.sh [videos [1|2|3]|capturas|desempenho|saves|testes|validadores|zip|tudo]
#   BASE13 = clone compilado da rodada 13 (padrão output/tmp/base-r13, commit 26806fa)
#   BASE06 = clone compilado do checkpoint procedural-world-v0.6 (padrão output/tmp/base-v06)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD                                  # dentro de "(cd BASE && …)" o $PWD já é o do clone: roteiros novos vêm daqui
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-13-1}
G=$PWD/game/build/install/game/bin/game
BASE13=${BASE13:-output/tmp/base-r13}
BASE06=${BASE06:-output/tmp/base-v06}
G13=$PWD/$BASE13/game/build/install/game/bin/game
G06=$PWD/$BASE06/game/build/install/game/bin/game
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { find output/mundos output/personagens output/saves output/aparencias output/prefs* -type f 2>/dev/null | sort | xargs -d '\n' sha256sum 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
isolado() {
  local d; d="$(realpath "$(mktemp -d output/tmp/r131.XXXXXX)")"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
video() { ffmpeg -loglevel error -y -framerate 30 -i "$1/q_%05d.png" -i "$2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"; }
prancha() { .venv/bin/python tools/qa/prancha.py "$@" > /dev/null; }
regiao() { echo "-Dvx.menu=nao -Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=$1 -Dvx.savegame=- -Dvx.prefs=-"; }

case "$what" in videos|tudo)
  V="$R/videos"; mkdir -p "$V"; so="${2:-}"     # "videos 3" refaz só o 3
  # 01 a demonstração principal: menu → mundo novo (semente 7) → presente → andar com a mochila aberta → abrir no meio da
  #    luta → vencer → aplicar clicando → achado com a mochila aberta → abas → salvar → menu → continuar → nada duplica
  if [ -z "$so" ] || [ "$so" = 1 ]; then
  iso=$(isolado)
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/itens/01-demo.txt "$iso/d" 30) > "$R/registros/01-demo.log" 2>&1 || echo "FALHOU demo"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/01-mochila-viva.mp4"
  prancha "$V/01-prancha.png" 8 240 $(ls "$iso/d/quadros"/q_*.png | awk 'NR%60==1')
  rm -rf "$iso"
  fi
  # 02 um save feito pela v0.6 aberto por este jogo (o binário da v0.6 cria o mundo "Torre Caida" e salva; este abre)
  if [ -z "$so" ] || [ "$so" = 2 ]; then
  iso=$(isolado)
  (cd "$BASE06" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $G06 --roteiro "$ROOT/tools/menu/15-voltar-e-continuar.txt" "$iso/v06" 30) > "$R/registros/02-v06-cria.log" 2>&1 || echo "FALHOU v0.6"
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/itens/02-save-v06.txt "$iso/d" 30) > "$R/registros/02-save-v06.log" 2>&1 || echo "FALHOU save v0.6"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/02-save-da-v06-aberto.mp4"
  rm -rf "$iso"
  fi
  # 03 um save da rodada 13 COM itens (24 espaços) aberto pela 13.1: a mochila igual, as fontes não entregam de novo
  if [ -z "$so" ] || [ "$so" = 3 ]; then
  iso=$(isolado)
  (cd "$BASE13" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $G13 --roteiro "$ROOT/tools/itens/04-r13-cria.txt" "$iso/r13" 30) > "$R/registros/03-r13-cria.log" 2>&1 || echo "FALHOU r13 cria"
  find "$iso/mundos" -name aventura.json | sort | xargs -d '\n' sha256sum > "$R/registros/03-save-r13.sha256"
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/itens/05-r13-aberto.txt "$iso/d" 30) > "$R/registros/03-r13-aberto.log" 2>&1 || echo "FALHOU r13 aberto"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/03-save-da-rodada-13-aberto.mp4"
  cp "$iso/d/save-r13-aberto.png" "$V/03-save-r13-aberto.png" 2>/dev/null || true
  rm -rf "$iso"
  fi
  ls -la "$V"
esac

case "$what" in capturas|tudo)
  C="$R/capturas"; mkdir -p "$C/resolucoes" "$C/antes-depois" "$C/modelos"
  out="$(realpath -m output/tmp/r131cap)"; rm -rf "$out"
  (JAVA_OPTS="-Dvx.som=mudo $(regiao 7)" $G --roteiro tools/itens/03-resolucoes.txt "$out" 30) > "$R/registros/03-resolucoes.log" 2>&1 || echo "FALHOU resoluções"
  cp "$out"/*.png "$C/resolucoes/"
  prancha "$C/resolucoes/prancha.png" 4 480 $(ls "$C/resolucoes"/r*.png | sort)
  # a prancha dos modelos (sobre o painel, o pergaminho, em silhueta e pequenos)
  printf 'size 1280 720\nitens-prancha prancha-modelos.png\n' > "$out/prancha.txt"
  (JAVA_OPTS="-Dvx.som=mudo -Dvx.menu=nao -Dvx.prefs=- -Dvx.savegame=-" $G --roteiro "$out/prancha.txt" "$out/m" 30) > "$R/registros/modelos.log" 2>&1
  cp "$out/m/prancha-modelos.png" "$C/modelos/"
  # antes × depois: a mesma mochila (os 5 itens, herói ferido) na rodada 13 e na 13.1, 1080p e 720p
  printf 'size 1920 1080\nclick comecar\nwait 1.0\nmochila-dar item.ervas_trilha 5\nmochila-dar item.cataplasma 2\nmochila-dar item.fragmento_pedra 1\nmochila-dar item.madeira_velha 2\nmochila-dar item.registro_viagem 1\nvida-heroi 3\nkey I\nwait 0.6\nshot mochila-1080\nsize 1280 720\nwait 0.4\nshot mochila-720\n' > "$out/antes.txt"
  for lado in r13 r131; do
    bin=$G; raiz=.; [ "$lado" = r13 ] && { bin=$G13; raiz="$BASE13"; }
    (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo $(regiao 7)" $bin --roteiro "$out/antes.txt" "$out/$lado" 30) > "$R/registros/antes-$lado.log" 2>&1
    for f in "$out/$lado"/*.png; do cp "$f" "$C/antes-depois/$lado-$(basename "$f")"; done
  done
  prancha "$C/antes-depois/r13-x-r131.png" 2 960 "$C/antes-depois/r13-mochila-1080.png" "$C/antes-depois/r131-mochila-1080.png"
esac

case "$what" in desempenho|tudo)
  # rodada 13 × 13.1 com a mochila FECHADA (processos alternados: a região s7 5 vezes, a vila fixa 3, o menu 1), GPU por
  # passo; só a 13.1: aberta × fechada em PARES no mesmo processo, andar com ela fechada/aberta/aba vazia, a mochila grande.
  # A carga da máquina vai junto (carga-antes/depois.txt): com outro programa usando a GPU, os tempos absolutos variam muito
  D="$R/desempenho"; mkdir -p "$D"
  run() {  # run RAIZ BINÁRIO ROTULO ROTEIRO [opções]
    local out; out="$(realpath -m output/tmp/r131p)"; rm -rf "$out"
    (cd "$1" && JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=- -Dvx.prefs=- ${5:-}" "$2" --roteiro "$4" "$out" 60) > "$D/$3.log" 2>&1 || echo "FALHOU $3"
    for f in "$out"/*.txt; do [ -f "$f" ] && cp "$f" "$D/$3-$(basename "$f")"; done
  }
  S7="-Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=7"
  rm -f "$D"/*.txt "$D"/*.log
  uptime > "$D/carga-antes.txt"; ps -eo pcpu,comm --sort=-pcpu | head -8 >> "$D/carga-antes.txt"
  # 13 × 13.1 com a mochila FECHADA: processos alternados (a máquina pode estar em uso: o resumo dá a mediana e a melhor)
  for r in 1 2 3 4 5; do
    for lado in r13 r131; do
      raiz=.; bin=$G; [ "$lado" = r13 ] && { raiz="$BASE13"; bin=$G13; }
      run "$raiz" "$bin" "$lado-v2-s7-$r" "$ROOT/tools/itens/desempenho-fechado.txt" "$S7"
      [ "$r" -le 3 ] && run "$raiz" "$bin" "$lado-vila-fixa-$r" "$ROOT/tools/jogo/desempenho.txt"
      if [ "$r" = 1 ]; then
        iso=$(isolado)
        (cd "$raiz" && JAVA_OPTS="-Dvx.som=mudo $(opts "$iso")" $bin --roteiro "$ROOT/tools/menu/14-memoria.txt" "$iso/o" 30) > "$D/menu-$lado-$r.log" 2>&1 || echo "FALHOU menu $lado $r"
        rm -rf "$iso"
      fi
    done
  done
  # só a 13.1: a mochila aberta × fechada em PARES no mesmo processo; andar com ela fechada/aberta/aba vazia; a mochila
  # grande (rolada, abrir e fechar 20 vezes, save com 283 pilhas)
  for r in 1 2; do
    run . "$G" "r131-pares-$r" "$ROOT/tools/itens/desempenho-pares.txt" "$S7"
    for v in fechada aberta aba-vazia; do run . "$G" "r131-andando-$v-$r" "$ROOT/tools/itens/desempenho-andando-$v.txt" "$S7"; done
  done
  sv="$(realpath "$(mktemp -d output/tmp/r131s.XXXXXX)")"
  run . "$G" "r131-mochila-1" "$ROOT/tools/itens/desempenho-mochila.txt" "$S7 -Dvx.savegame=$sv"
  rm -rf "$sv"
  uptime > "$D/carga-depois.txt"; ps -eo pcpu,comm --sort=-pcpu | head -8 >> "$D/carga-depois.txt"
  python3 tools/qa/resumo_desempenho131.py "$D" > "$D/resumo.md"; cat "$D/resumo.md"
esac

case "$what" in saves|tudo)
  tools/qa/saves_antigos.sh "$BASE06" . output/tmp/r131-saves > "$R/registros/saves-antigos-v06.txt" 2>&1 || true
  tail -14 "$R/registros/saves-antigos-v06.txt"
  tools/qa/saves_antigos.sh "$BASE13" . output/tmp/r131-saves13 > "$R/registros/saves-antigos-r13.txt" 2>&1 || true
  tail -14 "$R/registros/saves-antigos-r13.txt"
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
    tools/review7.sh gate 2>&1 | grep -E "pares|gate" || true
    python3 tools/check_links.py 2>&1 | tail -1 || true
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json 2>&1 | grep arquivos | tail -1 || true
    rm -rf output/tmp/r131-ref; tools/reference.sh output/tmp/r131-ref > /dev/null 2>&1 || true
    echo "referências: $(ls output/tmp/r131-ref/*.png 2>/dev/null | wc -l) capturas; $(.venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/r131-ref 2>&1 | tail -1)"; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in zip|tudo)
  (cd "$(dirname "$R")" && rm -f "$(basename "$R")/rodada-13-1-revisao.zip" && zip -qr "$(basename "$R")/rodada-13-1-revisao.zip" "$(basename "$R")" \
    -x "*.mp4" -x "*/rodada-13-1-revisao.zip" -x "*/desempenho/*.log" -x "*/registros/*.log" -x "*.json" -x "*.invalido")
  ls -la "$R/rodada-13-1-revisao.zip"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"
