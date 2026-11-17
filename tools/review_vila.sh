#!/usr/bin/env bash
# NPCs da vila (Kelyra, Índira, Súvia) — pacote de revisão com o viewer real + ffmpeg.
#   tools/review_vila.sh [fotos|videos|todos]
# Cena: layout=vila_e (assets/scenes/vila_e.json); clareira canônica e vitrine não são tocadas.
set -euo pipefail
cd "$(dirname "$0")/.."
D=output/visual-review/round-06/npcs-vila; TMP=output/tmp/vila; mkdir -p $D $TMP
SEL="${1:-todos}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = todos ]; }
V="mode=scene,style=e,layout=vila_e,t=1.3,nohud=1"
snap() { tools/snap.sh "$@" > /dev/null; }
sheet() { .venv/bin/python tools/sheet.py grid "$@" > /dev/null; }
NPCS="kelyra indira suvia"

if want fotos; then
  for n in $NPCS; do H=characters/npc/$n
    snap "mode=char,style=e,hero=$H,yaw=0,pitch=4,preset=neutra,size=600x760,nohud=1,out=$TMP/${n}_f.png" \
         "mode=char,style=e,hero=$H,yaw=90,pitch=4,preset=neutra,size=600x760,nohud=1,out=$TMP/${n}_p.png" \
         "mode=char,style=e,hero=$H,yaw=180,pitch=4,preset=neutra,size=600x760,nohud=1,out=$TMP/${n}_c.png" \
         "mode=char,style=e,hero=$H,yaw=35,pitch=14,size=600x760,nohud=1,out=$TMP/${n}_i.png"
    sheet $D/$n-vistas.png 4 $TMP/${n}_f.png $TMP/${n}_p.png $TMP/${n}_c.png $TMP/${n}_i.png
    for e in neutral joy determined surprise; do
      snap "mode=char,style=e,hero=$H,yaw=8,pitch=4,dist=1.5,ty=0.85,expr=$e,size=420x420,nohud=1,out=$TMP/${n}_e_$e.png"; done
  done
  sheet $D/expressoes.png 4 $TMP/kelyra_e_{neutral,joy,determined,surprise}.png \
        $TMP/indira_e_{neutral,joy,determined,surprise}.png $TMP/suvia_e_{neutral,joy,determined,surprise}.png
  for y in 0 90 180; do
    snap "mode=lineup,style=e,set=vila,myaw=$y,pitch=4,preset=neutra,size=1800x680,out=$TMP/l_$y.png" \
         "mode=lineup,style=e,set=vila,myaw=$y,pitch=4,sil=1,size=1800x680,nohud=1,out=$TMP/s_$y.png"; done
  sheet $D/comparacao-com-aventureiro-e-cartografa.png 1 $TMP/l_0.png $TMP/l_90.png $TMP/l_180.png
  sheet $D/silhuetas.png 1 $TMP/s_0.png $TMP/s_90.png $TMP/s_180.png
  snap "$V,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,size=1920x1080,out=$D/cena-vila.png" \
       "$V,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,preset=tarde,size=1920x1080,out=$D/cena-vila-tarde.png" \
       "$V,hx=0.6,hz=2.6,hyaw=200,yaw=-8,pitch=8,dist=4.2,ty=0.8,size=1600x900,out=$D/cena-perto.png"
  echo "review_vila: fotos ok"
fi

if want videos; then
  mkdir -p $D/videos $D/videos-pranchas
  for n in $NPCS; do H=characters/npc/$n
    tools/anim.sh $D/videos/$n-aceno "mode=char,style=e,hero=$H,act=emote,hold=0.3,yaw=25,pitch=8,size=560x700,nohud=1" 72 30 >/dev/null
    tools/anim.sh $D/videos/$n-caminhada "mode=char,style=e,hero=$H,clip=walk,yaw=60,pitch=6,size=560x700,nohud=1" 72 30 >/dev/null
    tools/anim.sh $D/videos/$n-caminhada-costas "mode=char,style=e,hero=$H,clip=walk,yaw=160,pitch=6,size=560x700,nohud=1" 72 30 >/dev/null
  done
  tools/anim.sh $D/videos/cena-vila "$V,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,size=1280x720" 120 30 >/dev/null
  for f in $D/videos/*.mp4; do n=$(basename $f .mp4)
    ffmpeg -loglevel error -y -i $f -vf "select='not(mod(n,6))',scale=280:-1,tile=6x2" -frames:v 1 $D/videos-pranchas/$n.png; done
  echo "review_vila: videos ok"
fi
