#!/usr/bin/env bash
# Rodada 08.1 — validação da malha conforme (fim do z-fighting e das frestas) e dos dentes.
# Uso: tools/review81.sh [temporal|dentes|referencias|regressao|desempenho|testes|tudo]   (padrão: tudo)
# Saída: output/visual-review/round-08-1/ (fora do git). O mesmo binário roda nos dois modos de malha:
# -Dvx.mesh=legado (rodada 03: margem + T-junctions) × padrão (conforme).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=output/visual-review/round-08-1
G=game/build/install/game/bin/game
V=viewer/build/install/viewer/bin/viewer
what="${1:-tudo}"
if [ -n "${REBUILD:-}" ] || [ ! -x "$G" ]; then ./gradlew -q :game:installDist :viewer:installDist; fi
mkdir -p "$R"
enc=(-c:v libopenh264 -b:v 6M)
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx264 && enc=(-c:v libx264 -crf 20)

if [ "$what" = temporal ] || [ "$what" = tudo ]; then
  mkdir -p "$R/temporal"
  : > "$R/temporal/alternancia.txt"
  for cena in fachada terreno movimento outros rosto; do
    for m in legado conforme; do
      tmp="output/tmp/r81/t-$cena-$m"; rm -rf "$tmp"
      if [ $cena = rosto ]; then JAVA_OPTS="-Dvx.mesh=$m" $V --editor-script tools/temporal/rosto.txt "$tmp" 30 >/dev/null 2>&1
      else JAVA_OPTS="-Dvx.mesh=$m" $G --roteiro "tools/temporal/$cena.txt" "$tmp" 30 >/dev/null 2>&1; fi
      echo "$cena / malha $m: $(python3 tools/temporal/alternancia.py "$tmp/quadros" "$R/temporal/$cena-$m-mapa.png")" | tee -a "$R/temporal/alternancia.txt"
    done
    ffmpeg -loglevel error -y -framerate 30 -i "output/tmp/r81/t-$cena-conforme/quadros/q_%05d.png" "${enc[@]}" -pix_fmt yuv420p "$R/temporal/$cena-conforme.mp4"
    ffmpeg -loglevel error -y -framerate 30 -i "output/tmp/r81/t-$cena-legado/quadros/q_%05d.png" -framerate 30 -i "output/tmp/r81/t-$cena-conforme/quadros/q_%05d.png" \
      -filter_complex "[0]scale=960:-2[a];[1]scale=960:-2[b];[a][b]hstack" "${enc[@]}" -pix_fmt yuv420p "$R/temporal/$cena-legado-x-conforme.mp4"
  done
fi

if [ "$what" = dentes ] || [ "$what" = tudo ]; then
  mkdir -p "$R/dentes"
  for luz in dia tarde neutra; do
    nome=$luz
    $V --snap "mode=char,style=e,hero=characters/npc/kelyra,expr=joy,preset=$luz,dist=1.3,ty=0.95,yaw=15,size=700x700,nohud=1,out=$R/dentes/kelyra-alegre-close-$nome.png" >/dev/null 2>&1
    $V --snap "mode=char,style=e,char=0,expr=determined,preset=$luz,dist=1.3,ty=0.95,yaw=15,size=700x700,nohud=1,out=$R/dentes/aventureiro-determinado-close-$nome.png" >/dev/null 2>&1
    $V --snap "mode=char,style=e,hero=characters/npc/kelyra,expr=joy,preset=$luz,dist=4.6,yaw=15,size=700x700,nohud=1,out=$R/dentes/kelyra-alegre-distancia-de-jogo-$nome.png" >/dev/null 2>&1
    $V --snap "mode=char,style=e,char=0,act=slash_h,hold=5,t=0.40,preset=$luz,dist=4.6,yaw=35,pitch=18,size=700x700,nohud=1,out=$R/dentes/aventureiro-golpe-distancia-de-jogo-$nome.png" >/dev/null 2>&1
  done
  echo "dentes: $(ls "$R/dentes" | wc -l) capturas"
fi

if [ "$what" = referencias ] || [ "$what" = tudo ]; then
  rm -rf "$R/referencias"; mkdir -p "$R/referencias/novas" "$R/referencias/diferencas"
  tools/reference.sh "$R/referencias/novas" >/dev/null 2>&1
  python3 tools/temporal/comparar_referencias.py docs/art/reference "$R/referencias/novas" "$R/referencias/diferencas" | tee "$R/referencias/comparacao.txt"
fi

if [ "$what" = regressao ] || [ "$what" = tudo ]; then
  mkdir -p "$R/regressao"
  { $V --check 2>&1 | tail -1; $V --occlusion 2>&1 | grep -E "ROSTO COBERTO$|^occlusion"; R="$R/regressao" tools/review7.sh gate 2>&1 | tail -2; } | tee "$R/regressao/resumo.txt"
fi

if [ "$what" = desempenho ] || [ "$what" = tudo ]; then
  mkdir -p "$R/desempenho"
  for m in legado conforme; do
    JAVA_OPTS="-Dvx.mesh=$m" $G --roteiro tools/jogo/desempenho.txt "$R/desempenho/jogo-$m" 60 2>&1 | grep -vE "WARNING|libdecor|^jogo: output" > "$R/desempenho/jogo-$m.txt" || true
    JAVA_OPTS="-Dvx.mesh=$m" $V --layout jogo_vila_e --bench "1920x1080,style=e,quality=padrao,runs=3" 2>&1 | grep -E "TOTAL|exec 1" > "$R/desempenho/viewer-$m.txt" || true
  done
  for f in "$R"/desempenho/*.txt; do echo "== $f"; cat "$f"; done
fi

if [ "$what" = testes ] || [ "$what" = tudo ]; then
  ./gradlew -q test 2>&1 | grep -vE "WARNING" | tail -3 || true
  python3 - <<'PY' | tee "$R/testes.txt"
import glob, xml.etree.ElementTree as ET
t=f=0
for x in sorted(glob.glob('*/build/test-results/test/*.xml')):
    r=ET.parse(x).getroot(); n=int(r.get('tests')); e=int(r.get('failures'))+int(r.get('errors')); t+=n; f+=e
    print(f"{r.get('name')}: {n} testes, {e} falhas")
print(f"total: {t} testes, {f} falhas")
PY
fi
