#!/usr/bin/env bash
# Rodada 06 — pacote de revisão da primeira expansão controlada (viewer real + ffmpeg).
#   tools/review6.sh [npc|criatura|arvore|estrutura|cena|videos|todos]
# Vitrine: layout=vitrine_e (assets/scenes/vitrine_e.json); a clareira canônica não é tocada.
set -euo pipefail
cd "$(dirname "$0")/.."
R=output/visual-review/round-06; TMP=output/tmp/r6v; mkdir -p $TMP
SEL="${1:-todos}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = todos ]; }
NPC=characters/npc/cartografa; MOTH=creatures/e/mariposa
V="mode=scene,style=e,layout=vitrine_e,t=1.3,nohud=1"
snap() { tools/snap.sh "$@" > /dev/null; }
sheet() { .venv/bin/python tools/sheet.py grid "$@" > /dev/null; }

if want npc; then
  D=$R/npc; mkdir -p $D
  snap "mode=char,style=e,hero=$NPC,yaw=0,pitch=4,preset=neutra,size=700x800,nohud=1,out=$D/frente.png" \
       "mode=char,style=e,hero=$NPC,yaw=90,pitch=4,preset=neutra,size=700x800,nohud=1,out=$D/perfil.png" \
       "mode=char,style=e,hero=$NPC,yaw=180,pitch=4,preset=neutra,size=700x800,nohud=1,out=$D/costas.png" \
       "mode=char,style=e,hero=$NPC,yaw=35,pitch=20,size=700x800,nohud=1,out=$D/isometrica.png" \
       "mode=char,style=e,hero=$NPC,yaw=12,pitch=4,dist=1.4,ty=0.9,size=700x700,nohud=1,out=$D/rosto.png"
  for e in neutral joy determined surprise; do
    snap "mode=char,style=e,hero=$NPC,yaw=12,pitch=4,dist=1.4,ty=0.9,expr=$e,size=500x500,nohud=1,out=$TMP/e_$e.png"; done
  sheet $D/expressoes.png 4 $TMP/e_neutral.png $TMP/e_joy.png $TMP/e_determined.png $TMP/e_surprise.png
  for y in 0 90 180; do
    snap "mode=lineup,style=e,set=r6-npc,myaw=$y,pitch=4,preset=neutra,size=1600x640,out=$TMP/l_$y.png" \
         "mode=lineup,style=e,set=r6-npc,myaw=$y,pitch=4,sil=1,size=1600x640,nohud=1,out=$TMP/s_$y.png"; done
  sheet $D/comparacao-protagonistas.png 1 $TMP/l_0.png $TMP/l_90.png $TMP/l_180.png
  sheet $D/silhueta-com-protagonistas.png 1 $TMP/s_0.png $TMP/s_90.png $TMP/s_180.png
  snap "$V,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=1920x1080,out=$D/cena-exploracao.png" \
       "$V,hx=-0.1,hz=-0.7,hyaw=200,yaw=14,pitch=10,dist=4.8,ty=0.7,size=1280x720,out=$D/cena-perto-do-posto.png"
  echo "review6: npc ok"
fi

if want criatura; then
  D=$R/criatura; mkdir -p $D
  snap "mode=char,style=e,hero=$MOTH,yaw=0,pitch=6,size=700x700,nohud=1,out=$D/frente.png" \
       "mode=char,style=e,hero=$MOTH,yaw=90,pitch=6,size=700x700,nohud=1,out=$D/perfil.png" \
       "mode=char,style=e,hero=$MOTH,yaw=180,pitch=30,size=700x700,nohud=1,out=$D/costas.png" \
       "mode=char,style=e,hero=$MOTH,yaw=30,pitch=16,size=700x700,nohud=1,out=$D/isometrica.png" \
       "mode=char,style=e,hero=$MOTH,yaw=0,pitch=6,sil=1,size=700x700,nohud=1,out=$TMP/ms0.png" \
       "mode=char,style=e,hero=$MOTH,yaw=90,pitch=6,sil=1,size=700x700,nohud=1,out=$TMP/ms90.png" \
       "mode=char,style=e,hero=$MOTH,yaw=180,pitch=30,sil=1,size=700x700,nohud=1,out=$TMP/ms180.png"
  sheet $D/silhueta.png 3 $TMP/ms0.png $TMP/ms90.png $TMP/ms180.png
  snap "mode=lineup,style=e,set=r6-criaturas,myaw=30,pitch=10,size=2200x700,out=$D/comparacao-criaturas.png" \
       "mode=lineup,style=e,set=r6-criaturas,myaw=30,pitch=10,preset=neutra,size=2200x700,out=$D/comparacao-criaturas-luz-neutra.png" \
       "mode=lineup,style=e,set=r6-criaturas,myaw=0,pitch=6,sil=1,size=2200x700,nohud=1,out=$D/comparacao-silhuetas.png" \
       "$V,hx=3.2,hz=-2.0,hyaw=200,yaw=-28,pitch=8,dist=4.6,ty=1.0,size=1280x720,out=$D/cena-perto-da-vegetacao.png"
  echo "review6: criatura ok"
fi

if want arvore; then
  D=$R/arvore; mkdir -p $D
  snap "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=20,pitch=8,dist=12,ty=2.0,size=1280x900,out=$D/isolada-frente.png" \
       "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=-60,pitch=8,dist=12,ty=2.0,size=1280x900,out=$D/isolada-lado.png" \
       "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=20,pitch=6,dist=6,ty=1.0,size=1280x900,out=$D/close-tronco.png" \
       "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=20,pitch=-4,dist=7,ty=3.0,size=1280x900,out=$D/close-copa.png" \
       "$V,hx=-4.5,hz=-3.0,hyaw=180,yaw=-10,pitch=10,dist=14,ty=1.6,size=1600x900,out=$D/cena-com-outras-arvores.png" \
       "$V,yaw=-40,pitch=28,dist=26,size=1920x1080,out=$D/panoramica.png"
  for p in dia tarde neutra; do
    snap "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=20,pitch=8,dist=13,ty=2.0,preset=$p,size=800x600,out=$TMP/luz_$p.png"; done
  sheet $D/luz-dia-tarde-neutra.png 3 $TMP/luz_dia.png $TMP/luz_tarde.png $TMP/luz_neutra.png
  echo "review6: arvore ok"
fi

if want estrutura; then
  D=$R/estrutura; mkdir -p $D
  snap "$V,hx=-2.6,hz=0.5,hyaw=0,yaw=20,pitch=10,dist=7,ty=1.6,size=1280x900,out=$D/frente.png" \
       "$V,hx=-2.6,hz=-2.6,hyaw=0,yaw=110,pitch=12,dist=8,ty=1.4,size=1280x900,out=$D/lateral-direita.png" \
       "$V,hx=-2.6,hz=-2.6,hyaw=0,yaw=290,pitch=12,dist=8,ty=1.4,size=1280x900,out=$D/lateral-esquerda.png" \
       "$V,hx=-2.0,hz=-7.2,hyaw=0,yaw=170,pitch=14,dist=5.2,ty=1.4,size=1280x900,out=$D/fundos.png" \
       "$V,hx=-2.6,hz=-2.6,hyaw=0,yaw=55,pitch=28,dist=9,ty=1.4,size=1280x900,out=$D/isometrica.png" \
       "$V,hx=-2.4,hz=-1.6,hyaw=0,yaw=20,pitch=8,dist=3.0,ty=1.5,size=1280x900,out=$D/close-materiais.png" \
       "$V,hx=-4.5,hz=-3.0,hyaw=180,yaw=-10,pitch=10,dist=14,ty=1.6,size=1600x900,out=$D/comparacao-com-a-casa.png" \
       "$V,hx=0.6,hz=0.6,hyaw=200,yaw=14,pitch=10,dist=6.5,ty=1.0,size=1280x720,out=$D/cena-com-npc.png"
  echo "review6: estrutura ok"
fi

if want cena; then
  D=$R/cena; mkdir -p $D
  snap "$V,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=1920x1080,out=$D/camera-de-jogo.png" \
       "$V,hx=-4.5,hz=-3.0,hyaw=180,yaw=-10,pitch=10,dist=14,ty=1.6,size=1920x1080,out=$D/apresentacao-geral.png" \
       "$V,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,party=1,size=1920x1080,out=$D/camera-de-jogo-equipe.png" \
       "$V,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,preset=tarde,size=1920x1080,out=$D/camera-de-jogo-tarde.png"
  echo "review6: cena ok"
fi

if want videos; then
  D=$R/videos; mkdir -p $D
  tools/anim.sh $D/01-npc-idle "mode=char,style=e,hero=$NPC,clip=idle,yaw=25,pitch=8,size=640x720,nohud=1" 90 30 >/dev/null
  tools/anim.sh $D/02-npc-caminhada "mode=char,style=e,hero=$NPC,clip=walk,yaw=60,pitch=6,size=640x720,nohud=1" 72 30 >/dev/null
  tools/anim.sh $D/03-npc-gesto-consultar-e-apontar "mode=char,style=e,hero=$NPC,act=emote,hold=0.4,yaw=20,pitch=8,size=640x720,nohud=1" 108 30 >/dev/null
  tools/anim.sh $D/04-npc-gesto-na-cena "$V,hx=-0.1,hz=-0.7,hyaw=200,yaw=14,pitch=10,dist=4.8,ty=0.7,size=1280x720" 100 30 >/dev/null
  for c in idle:90 walk:90 hit:54 emote:96; do IFS=: read -r n f <<<"$c"
    tools/anim.sh $D/05-mariposa-$n "mode=char,style=e,hero=$MOTH,clip=$n,yaw=30,pitch=12,size=640x640,nohud=1" $f 30 >/dev/null; done
  tools/anim.sh $D/06-mariposa-na-cena "$V,hx=3.2,hz=-2.0,hyaw=200,yaw=-28,pitch=8,dist=4.6,ty=1.0,size=1280x720" 120 30 >/dev/null
  tools/anim.sh $D/07-cena-camera-de-jogo "$V,hx=1.0,hz=2.5,hyaw=200,seq=walk:2.0+idle:1.0+run:1.4+idle:0.6,yaw=18,pitch=13,dist=6.4,size=1280x720" 150 30 >/dev/null
  tools/anim.sh $D/08-salgueiro-vento "$V,hx=6.8,hz=-5.0,hyaw=0,yaw=20,pitch=8,dist=12,ty=2.0,size=1280x720" 90 30 >/dev/null
  for f in $D/*.mp4; do n=$(basename $f .mp4)
    ffmpeg -loglevel error -y -i $f -vf "select='not(mod(n,6))',scale=320:-1,tile=6x3" -frames:v 1 $R/videos-pranchas-$n.png 2>/dev/null || true; done
  mkdir -p $R/videos-pranchas; mv $R/videos-pranchas-*.png $R/videos-pranchas/ 2>/dev/null || true
  echo "review6: videos ok"
fi
