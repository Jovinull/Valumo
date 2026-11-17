#!/usr/bin/env bash
# Rodada 05.1 — pacote de revisão (viewer real + ffmpeg). "Antes" = perfis da rodada 05 preservados na linha de
# base (-Dvx.anim=...), mesmo binário e mesmos assets; "sem secundário" = -Dvx.secondary=false.
#   tools/review51.sh [videos|capturas|todos]
set -euo pipefail
cd "$(dirname "$0")/.."
R=output/visual-review/round-05-1; VD=$R/videos; CP=$R/capturas; TMP=output/tmp/r51v; mkdir -p $VD $CP $TMP
SEL="${1:-todos}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = todos ]; }
OLD="-Dvx.anim=$R/linha-de-base/anim-r05"
ENC=(-c:v libopenh264 -b:v 6M -pix_fmt yuv420p)
LB=characters/r4/adventurer_longblade
ISO="yaw=35,pitch=18"

# grava a mesma spec com dois JAVA_OPTS e junta lado a lado (esquerda = A, direita = B)
pair() { local out=$1 spec=$2 n=$3 a=$4 b=$5
  KEEP=1 JAVA_OPTS="$a" tools/anim.sh $TMP/a "$spec" $n 30 >/dev/null
  KEEP=1 JAVA_OPTS="$b" tools/anim.sh $TMP/b "$spec" $n 30 >/dev/null
  ffmpeg -loglevel error -y -framerate 30 -i $TMP/a-frames/f_%03d.png -framerate 30 -i $TMP/b-frames/f_%03d.png \
    -filter_complex hstack "${ENC[@]}" $out.mp4
  rm -rf $TMP/a-frames $TMP/b-frames $TMP/a.* $TMP/b.*; echo "review51: $out.mp4"; }
# mesma animação em 4 câmeras (frente, perfil, costas, 3/4), grade 2×2
quad() { local out=$1 base=$2 n=$3 opts=${4:-}; local i=0
  for c in "yaw=0,pitch=8" "yaw=90,pitch=8" "yaw=180,pitch=10" "yaw=-40,pitch=14"; do
    KEEP=1 JAVA_OPTS="$opts" tools/anim.sh $TMP/q$i "$base,$c,size=480x480,nohud=1" $n 30 >/dev/null; i=$((i+1)); done
  ffmpeg -loglevel error -y -framerate 30 -i $TMP/q0-frames/f_%03d.png -framerate 30 -i $TMP/q1-frames/f_%03d.png \
    -framerate 30 -i $TMP/q2-frames/f_%03d.png -framerate 30 -i $TMP/q3-frames/f_%03d.png \
    -filter_complex "[0][1]hstack[t];[2][3]hstack[u];[t][u]vstack" "${ENC[@]}" $out.mp4
  rm -rf $TMP/q*-frames $TMP/q*.mp4 $TMP/q*.gif; echo "review51: $out.mp4"; }

if want videos; then
  G2="mode=char,style=e,hero=$LB,act=guard_two_hands,hold=0.4"
  pair $VD/01-guarda-duas-maos-antes-depois-3q "$G2,$ISO,size=640x640,nohud=1" 100 "$OLD" ""
  pair $VD/02-guarda-duas-maos-antes-depois-frente "$G2,yaw=0,pitch=8,size=640x640,nohud=1" 100 "$OLD" ""
  pair $VD/03-guarda-duas-maos-antes-depois-perfil "$G2,yaw=-90,pitch=8,size=640x640,nohud=1" 100 "$OLD" ""
  quad $VD/04-guarda-duas-maos-quatro-angulos "$G2" 100
  tools/anim.sh $VD/05-guarda-para-repouso-e-caminhada "mode=char,style=e,hero=$LB,seq=guard_two_hands+idle:0.8+walk:1.6+idle:0.6,$ISO,size=720x720,nohud=1" 170 30 >/dev/null
  pair $VD/06-arco-hit-antes-depois-3q "mode=char,style=e,char=1,clip=hit,$ISO,size=640x640,nohud=1" 54 "$OLD" ""
  quad $VD/07-arco-hit-quatro-angulos-antes "mode=char,style=e,char=1,clip=hit" 54 "$OLD"
  quad $VD/08-arco-hit-quatro-angulos-depois "mode=char,style=e,char=1,clip=hit" 54
  pair $VD/09-arco-hit-depuracao-antes-depois "mode=char,style=e,char=1,clip=hit,dbg=1,yaw=-30,pitch=10,dist=2.3,ty=0.6,size=640x640,nohud=1" 54 "$OLD" ""
  pair $VD/10-espada-combo-secundario-sem-com "mode=char,style=e,char=0,act=combo,hold=0.4,yaw=200,pitch=12,size=640x640,nohud=1" 68 "-Dvx.secondary=false" ""
  pair $VD/11-lamina-longa-combo-secundario-sem-com "mode=char,style=e,hero=$LB,act=long_combo,hold=0.4,yaw=200,pitch=12,size=640x640,nohud=1" 85 "-Dvx.secondary=false" ""
  pair $VD/12-reacao-a-dano-secundario-sem-com "mode=char,style=e,char=0,clip=hit,yaw=160,pitch=10,size=640x640,nohud=1" 54 "-Dvx.secondary=false" ""
  pair $VD/13-exploradora-hit-tranca-sem-com "mode=char,style=e,char=1,clip=hit,yaw=160,pitch=10,size=640x640,nohud=1" 54 "-Dvx.secondary=false" ""
  tools/anim.sh $VD/14-interrupcao-espada-na-preparacao "mode=char,style=e,char=0,seq=idle:0.4+slash_h!0.25+hit+idle:0.5,$ISO,size=720x720,nohud=1" 60 30 >/dev/null
  tools/anim.sh $VD/15-interrupcao-lamina-longa-na-execucao "mode=char,style=e,hero=$LB,seq=idle:0.4+long_combo!0.45+hit+idle:0.5,$ISO,size=720x720,nohud=1" 66 30 >/dev/null
  tools/anim.sh $VD/16-interrupcao-arco-puxado "mode=char,style=e,char=1,seq=idle:0.4+bow_shot!1.0+hit+idle:0.5,yaw=-60,pitch=12,size=720x720,nohud=1" 84 30 >/dev/null
  pair $VD/17-cajado-concentracao-antes-depois "mode=char,style=e,char=2,act=staff_cast,hold=0.4,yaw=-40,pitch=12,size=640x640,nohud=1" 66 "$OLD" ""
  pair $VD/18-cajado-circular-antes-depois "mode=char,style=e,char=2,act=staff_cast_wide,hold=0.4,yaw=-40,pitch=12,size=640x640,nohud=1" 72 "$OLD" ""
  GC="hx=-0.6,hz=3.6,hyaw=20,yaw=18,pitch=13,dist=6.4,size=1280x720,nohud=1"
  tools/anim.sh $VD/20-jogo-espada "mode=scene,style=e,hero=characters/e/adventurer,seq=walk:1.2+slash_h+slash_d+run:0.8+combo+idle:0.5,$GC" 220 30 >/dev/null
  tools/anim.sh $VD/21-jogo-lamina-longa "mode=scene,style=e,hero=$LB,seq=walk:1.0+long_combo+guard_two_hands+walk:1.0+idle:0.4,$GC" 250 30 >/dev/null
  tools/anim.sh $VD/22-jogo-arco "mode=scene,style=e,hero=characters/e/ranger,seq=walk:1.0+bow_shot+hit+idle:0.6,$GC" 140 30 >/dev/null
  tools/anim.sh $VD/23-jogo-arco-camera-atras "mode=scene,style=e,hero=characters/e/ranger,hyaw=200,seq=walk:1.0+bow_shot+idle:0.6,hx=-0.6,hz=3.6,yaw=18,pitch=13,dist=6.4,size=1280x720,nohud=1" 110 30 >/dev/null
  tools/anim.sh $VD/24-jogo-cajado "mode=scene,style=e,hero=characters/e/mage,seq=walk:1.0+staff_cast+staff_cast_wide+idle:0.5,$GC" 160 30 >/dev/null
  tools/anim.sh $VD/25-jogo-equipe-caminhando "mode=scene,style=e,party=1,clip=walk,move=1,$GC" 120 30 >/dev/null
  echo "review51: vídeos ok"
fi

if want capturas; then
  # poses críticas da guarda de duas mãos (4 instantes × 4 câmeras) e empunhaduras com depuração
  for t in 0.00 0.42 0.90 2.34 3.05; do for c in "f:yaw=0,pitch=8" "p:yaw=-90,pitch=8" "c:yaw=180,pitch=10" "q:yaw=35,pitch=18"; do IFS=: read -r k cc <<<"$c"
    tools/snap.sh "mode=char,style=e,hero=$LB,act=guard_two_hands,hold=5,t=$t,$cc,size=420x420,nohud=1,out=$TMP/g_${t}_$k.png" >/dev/null; done; done
  .venv/bin/python tools/sheet.py grid $CP/guarda-duas-maos-poses-criticas.png 4 $TMP/g_*_f.png $TMP/g_*_p.png $TMP/g_*_c.png $TMP/g_*_q.png 2>/dev/null || \
    .venv/bin/python tools/sheet.py grid $CP/guarda-duas-maos-poses-criticas.png 5 $TMP/g_*.png
  for t in 0.00 0.10 0.24 0.60; do for o in "a:$OLD" "d:"; do IFS=: read -r k oo <<<"$o"
    JAVA_OPTS="$oo" tools/snap.sh "mode=char,style=e,char=1,clip=hit,hold=5,t=$t,dbg=1,yaw=-30,pitch=10,dist=2.3,ty=0.6,size=420x420,nohud=1,out=$TMP/h_${k}_$t.png" >/dev/null; done; done
  .venv/bin/python tools/sheet.py grid $CP/arco-hit-empunhadura-depuracao-antes-depois.png 4 $TMP/h_a_*.png $TMP/h_d_*.png
  tools/snap.sh "mode=lineup,style=e,set=r4-armas,myaw=35,out=$CP/armas-em-repouso.png" >/dev/null
  tools/snap.sh "mode=lineup,style=e,set=r4-armas,myaw=0,sil=1,out=$CP/armas-em-repouso-silhueta.png" >/dev/null
  JAVA_OPTS="$OLD" tools/snap.sh "mode=lineup,style=e,set=r4-armas,myaw=35,out=$CP/armas-em-repouso-r05.png" >/dev/null
  tools/snap.sh "mode=lineup,style=e,set=herois,myaw=0,out=$CP/herois-frente.png" >/dev/null
  tools/snap.sh "mode=lineup,style=e,set=herois,myaw=180,out=$CP/herois-costas.png" >/dev/null
  tools/snap.sh "mode=lineup,style=e,set=herois,myaw=0,sil=1,out=$CP/herois-silhueta.png" >/dev/null
  echo "review51: capturas ok"
fi
