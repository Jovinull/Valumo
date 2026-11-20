#!/usr/bin/env bash
# Rodada 12.3 — pacote de fechamento da v2: a câmera da torre (P85, recorte contextual) antes × depois, a Índira no varal,
# o pote, a abóbada, regressões de câmera fora da torre, desempenho 12.2 × 12.3, testes, validadores e sementes. Dados SEMPRE
# temporários (cópias, conferidas por hash antes e depois). Som só por loopback ou mudo. "Antes" = o binário da rodada 12.2
# num clone compilado (BASE=...).
#
# Uso: tools/review123.sh [videos|menu|comparacoes|desempenho|testes|validadores|zip|tudo]
#   BASE = clone compilado da rodada 12.2 (padrão output/tmp/base-r122), para os vídeos antes × depois e o desempenho
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=${R:-output/visual-review/round-12-3-final}
G=$PWD/game/build/install/game/bin/game
BASE=${BASE:-output/tmp/base-r122}
GB=$PWD/$BASE/game/build/install/game/bin/game
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 18)
mkdir -p "$R/registros" output/tmp
originais() { sha256sum output/aparencias/*.json output/saves/aventura.json 2>/dev/null || true; }
originais > "$R/registros/originais-antes.sha256"
isolado() {
  local d; d="$(realpath "$(mktemp -d output/tmp/r123.XXXXXX)")"; mkdir -p "$d/aparencias" "$d/saves"
  cp output/aparencias/*.json "$d/aparencias/" 2>/dev/null || true
  cp output/saves/aventura.json "$d/saves/" 2>/dev/null || true
  echo "$d"
}
opts() { echo "-Dvx.menu=sim -Dvx.personagens=$1/personagens -Dvx.mundos=$1/mundos -Dvx.savegame=$1/saves -Dvx.saves=$1/aparencias -Dvx.prefs=$1/prefs.json -Dvx.prefs.audio=$1/audio.json"; }
video() { ffmpeg -loglevel error -y -framerate 30 -i "$1/q_%05d.png" -i "$2" "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"; }
prancha() { .venv/bin/python tools/qa/prancha.py "$@" > /dev/null; }
# roda RAIZ BINÁRIO ROTEIRO SAÍDA "JAVA_OPTS" — a raiz é a do binário (assets dela); nada do proprietário é tocado
roda() {
  local out; out="$(realpath -m "$4")"; rm -rf "$out"; mkdir -p "$(dirname "$out")"
  (cd "$1" && JAVA_OPTS="-Dvx.som=loopback -Dvx.savegame=- -Dvx.prefs=- $5" "$2" --roteiro "$3" "$out" 30) > "$out.log" 2>&1 \
    || { echo "FALHOU $3 (veja $out.log)"; tail -5 "$out.log"; }
}
regiao() { echo "-Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=$1"; }
# lado a lado: antes (esquerda) × depois (direita), o som do depois (as pastas de saída dos dois roteiros)
lado() {
  ffmpeg -loglevel error -y -framerate 30 -i "$1/quadros/q_%05d.png" -framerate 30 -i "$2/quadros/q_%05d.png" -i "$2/audio.wav" \
    -filter_complex "[0:v]scale=960:-2[a];[1:v]scale=960:-2[b];[a][b]hstack=inputs=2[v]" -map "[v]" -map 2:a \
    "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$3"
}

case "$what" in videos|tudo)
  V="$R/videos"; T=output/tmp/r123v; mkdir -p "$V" "$T"
  # 01 a torre antes × depois (o mesmo roteiro nos dois binários) e 05 a torre a 41° (semente 83)
  roda "$BASE" "$GB" "$PWD/tools/mundo/13-torre-camera.txt" "$T/01a" "$(regiao 2)"
  roda . "$G" "$PWD/tools/mundo/13-torre-camera.txt" "$T/01b" "$(regiao 2)"
  lado "$T/01a" "$T/01b" "$V/01-torre-antes-depois.mp4"
  prancha "$V/01-prancha-antes.png" 8 240 $(ls "$T/01a/quadros"/q_*.png | awk 'NR%40==1')
  prancha "$V/01-prancha-depois.png" 8 240 $(ls "$T/01b/quadros"/q_*.png | awk 'NR%40==1')
  for n in "02-torre-entrada:19-torre-entrada:2" "03-torre-subir-descer:14-torre-subir-descer:2" "04-torre-mirante:15-torre-mirante:2" \
           "08-abobada-por-dentro:16-abobada:44" "10-pote-cheio-vazio:17-pote:3"; do
    nome=${n%%:*}; rest=${n#*:}; rt=${rest%%:*}; s=${rest#*:}
    roda . "$G" "$PWD/tools/mundo/$rt.txt" "$T/$nome" "$(regiao "$s")"
    video "$T/$nome/quadros" "$T/$nome/audio.wav" "$V/$nome.mp4"
    prancha "$V/${nome%%-*}-prancha.png" 8 240 $(ls "$T/$nome/quadros"/q_*.png | awk 'NR%40==1')
  done
  roda "$BASE" "$GB" "$PWD/tools/mundo/13-torre-camera.txt" "$T/05a" "$(regiao 83)"
  roda . "$G" "$PWD/tools/mundo/13-torre-camera.txt" "$T/05b" "$(regiao 83)"
  lado "$T/05a" "$T/05b" "$V/05-torre-a-41-graus-antes-depois.mp4"
  prancha "$V/05-prancha-depois.png" 8 240 $(ls "$T/05b/quadros"/q_*.png | awk 'NR%40==1')
  # 06 câmera livre, 07 combate perto de obstáculo, 11 salto no terreno: os roteiros da 09.2 na vila, binário novo
  for n in "06-camera-livre:combate92/13-camera-livre" "07-combate-perto-de-obstaculo:combate92/22-carvalho-e-pedra" \
           "11-salto-e-corrida:combate92/11-salto-exploracao"; do
    nome=${n%%:*}; rt=${n#*:}
    roda . "$G" "$PWD/tools/$rt.txt" "$T/$nome" ""
    video "$T/$nome/quadros" "$T/$nome/audio.wav" "$V/$nome.mp4"
  done
  # 09 a Índira antes × depois (vaga do lado × vaga do varal)
  roda "$BASE" "$GB" "$PWD/tools/mundo/18-indira.txt" "$T/09a" "$(regiao 28)"
  roda . "$G" "$PWD/tools/mundo/18-indira.txt" "$T/09b" "$(regiao 28)"
  lado "$T/09a" "$T/09b" "$V/09-indira-antes-depois.mp4"
  mkdir -p "$R/capturas/indira"
  for f in 01-conversa 02-rua 03-lado 04-outro-lado 05-fala; do
    cp "$T/09a/$f.png" "$R/capturas/indira/antes-$f.png"; cp "$T/09b/$f.png" "$R/capturas/indira/depois-$f.png"
  done
  mkdir -p "$R/capturas/pote"; cp "$T/10-pote-cheio-vazio"/0*.png "$R/capturas/pote/"
  ls -la "$V"
esac

case "$what" in videos|menu|tudo)
  # 12 voltar ao menu e continuar (dados temporários: cópias; o jogo salvo de antes vira o "Aventureiro")
  V="$R/videos"; mkdir -p "$V"
  iso=$(isolado)
  (JAVA_OPTS="-Dvx.som=loopback $(opts "$iso")" $G --roteiro tools/menu/15-voltar-e-continuar.txt "$iso/d" 30) > "$R/registros/12-menu.log" 2>&1 || echo "FALHOU menu"
  video "$iso/d/quadros" "$iso/d/audio.wav" "$V/12-menu-e-continuar.mp4"
  prancha "$V/12-prancha.png" 8 240 $(ls "$iso/d/quadros"/q_*.png | awk 'NR%40==1')
  rm -rf "$iso"
esac

case "$what" in comparacoes|tudo)
  # pranchas antes × depois da torre, quadro a quadro nos mesmos instantes do roteiro
  C="$R/comparacoes"; T=output/tmp/r123v; mkdir -p "$C"
  for s in 01 05; do
    [ -d "$T/${s}a/quadros" ] || continue
    args=(); for k in 60 150 240 330 420 510 600 690; do
      a=$(printf "%s/%sa/quadros/q_%05d.png" "$T" "$s" "$k"); b=$(printf "%s/%sb/quadros/q_%05d.png" "$T" "$s" "$k")
      [ -f "$a" ] && [ -f "$b" ] && args+=("$a" "$b"); done
    prancha "$C/torre-$s-antes-depois.png" 2 760 "${args[@]}"
  done
  cp build/medidas/torre-camera.txt "$C/torre-camera-medidas.txt" 2>/dev/null || true
  cp build/medidas/abobada-camera.txt "$C/abobada-camera-medidas.txt" 2>/dev/null || true
esac

case "$what" in desempenho|tudo)
  # os DOIS binários (12.2 × 12.3), intercalados, 3 vezes, mesma sessão, GPU por passo: a vila fixa, a torre (semente 2,
  # o herói DENTRO com o recorte ligado), a abóbada (44) e uma vila rural (28)
  D="$R/desempenho"; mkdir -p "$D"
  run() {  # run RAIZ BINÁRIO ROTULO ROTEIRO [opções]
    local out; out="$(realpath -m output/tmp/r123p)"; rm -rf "$out"
    (cd "$1" && JAVA_OPTS="-Dvx.som=mudo -Dvx.gpu=true -Dvx.menu=nao -Dvx.savegame=- -Dvx.prefs=- ${5:-}" "$2" --roteiro "$4" "$out" 60) > "$D/$3.log" 2>&1 || echo "FALHOU $3"
    cp "$out/desempenho.txt" "$D/$3.txt" 2>/dev/null || true
  }
  for r in 1 2 3; do
    for lado in r122 r123; do
      raiz=.; bin=$G; [ "$lado" = r122 ] && { raiz="$BASE"; bin=$GB; }
      run "$raiz" "$bin" "$lado-vila-fixa-$r" "$PWD/tools/jogo/desempenho.txt"
      run "$raiz" "$bin" "$lado-torre-dentro-$r" "$PWD/tools/mundo/desempenho-torre.txt" "$(regiao 2)"
      for s in 44 28; do run "$raiz" "$bin" "$lado-v2-s$s-$r" "$PWD/tools/mundo/desempenho.txt" "$(regiao $s)"; done
    done
  done
  python3 tools/qa/resumo_desempenho123.py "$D" > "$D/resumo.md"; cat "$D/resumo.md"
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
  cp build/medidas/torre-camera.txt build/medidas/abobada-camera.txt "$R/" 2>/dev/null || true
esac

case "$what" in validadores|tudo)
  V=viewer/build/install/viewer/bin/viewer
  { for c in "--check" "--check interrupcao-fina" "--check golpe-andando" "--check esquiva" "--check-cabelos"; do echo "$c: $($V $c 2>&1 | tail -1)"; done
    echo "--occlusion: $($V --occlusion 2>&1 | tail -1) (sai com 1 pela exceção conhecida do arco)"
    tools/review7.sh gate 2>&1 | grep -E "pares|gate"
    python3 tools/check_links.py 2>&1 | tail -1
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json 2>&1 | grep arquivos | tail -1
    rm -rf output/tmp/r123-ref; tools/reference.sh output/tmp/r123-ref > /dev/null 2>&1
    .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/r123-ref 2>&1 | tail -1; } > "$R/validadores.txt"
  cat "$R/validadores.txt"
esac

case "$what" in zip|tudo)
  (cd "$(dirname "$R")" && rm -f "$(basename "$R")/rodada-12-3-revisao.zip" && zip -qr "$(basename "$R")/rodada-12-3-revisao.zip" "$(basename "$R")" \
    -x "*.mp4" -x "*/rodada-12-3-revisao.zip" -x "*/desempenho/*.log" -x "*/registros/*.log")
  ls -la "$R/rodada-12-3-revisao.zip"
esac

originais > "$R/registros/originais-depois.sha256"
cmp -s "$R/registros/originais-antes.sha256" "$R/registros/originais-depois.sha256" && echo "dados do proprietário: intactos" || echo "ATENÇÃO: dados do proprietário mudaram"
