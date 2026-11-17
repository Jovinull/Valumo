#!/usr/bin/env bash
# Rodada 06.1 — capturas comparáveis antes/depois dos NPCs (mesmas câmeras).
#   tools/review61.sh <fase: antes|depois> [npcs|trio|cena|todos]
set -euo pipefail
cd "$(dirname "$0")/.."
F="${1:?fase}"; SEL="${2:-todos}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = todos ]; }
D=output/visual-review/round-06-1/npcs/$F; TMP=output/tmp/r61/$F; mkdir -p $D $TMP
snap() { tools/snap.sh "$@" > /dev/null; }
sheet() { .venv/bin/python tools/sheet.py grid "$@" > /dev/null; }
gray() { .venv/bin/python -c "import sys; from PIL import Image; Image.open(sys.argv[1]).convert('L').convert('RGB').save(sys.argv[2])" "$1" "$2"; }

if want npcs; then
  for n in cartografa kelyra indira suvia; do H=characters/npc/$n
    snap "mode=char,style=e,hero=$H,yaw=0,pitch=4,preset=neutra,size=520x680,nohud=1,out=$TMP/${n}_f.png" \
         "mode=char,style=e,hero=$H,yaw=90,pitch=4,preset=neutra,size=520x680,nohud=1,out=$TMP/${n}_p.png" \
         "mode=char,style=e,hero=$H,yaw=180,pitch=4,preset=neutra,size=520x680,nohud=1,out=$TMP/${n}_c.png" \
         "mode=char,style=e,hero=$H,yaw=35,pitch=14,size=520x680,nohud=1,out=$TMP/${n}_i.png" \
         "mode=char,style=e,hero=$H,yaw=25,pitch=6,dist=1.5,ty=0.85,size=520x520,nohud=1,out=$TMP/${n}_close.png"
    sheet $D/$n-vistas.png 5 $TMP/${n}_f.png $TMP/${n}_p.png $TMP/${n}_c.png $TMP/${n}_i.png $TMP/${n}_close.png
    for e in neutral joy determined surprise; do
      snap "mode=char,style=e,hero=$H,yaw=8,pitch=4,dist=1.5,ty=0.85,expr=$e,size=360x360,nohud=1,out=$TMP/${n}_e_$e.png"; done
    sheet $D/$n-expressoes.png 4 $TMP/${n}_e_neutral.png $TMP/${n}_e_joy.png $TMP/${n}_e_determined.png $TMP/${n}_e_surprise.png
  done
  echo "review61 $F: npcs ok"
fi

if want trio; then
  for y in 0 90 180; do
    snap "mode=lineup,style=e,set=trio,myaw=$y,pitch=4,preset=neutra,size=1500x700,nohud=1,out=$TMP/t_$y.png" \
         "mode=lineup,style=e,set=trio,myaw=$y,pitch=4,sil=1,size=1500x700,nohud=1,out=$TMP/ts_$y.png"
    gray $TMP/t_$y.png $TMP/tg_$y.png; done
  sheet $D/trio-cor.png 1 $TMP/t_0.png $TMP/t_90.png $TMP/t_180.png
  sheet $D/trio-silhueta.png 1 $TMP/ts_0.png $TMP/ts_90.png $TMP/ts_180.png
  sheet $D/trio-valores.png 1 $TMP/tg_0.png $TMP/tg_90.png $TMP/tg_180.png
  snap "mode=lineup,style=e,set=elenco,myaw=20,pitch=6,size=2400x700,out=$D/elenco.png" \
       "mode=lineup,style=e,set=elenco,myaw=0,pitch=4,sil=1,size=2400x700,nohud=1,out=$D/elenco-silhueta.png"
  for p in neutra dia tarde; do
    snap "mode=char,style=e,hero=characters/npc/suvia,yaw=30,pitch=8,preset=$p,size=480x620,nohud=1,out=$TMP/sv_$p.png"; done
  gray $TMP/sv_neutra.png $TMP/sv_cinza.png
  sheet $D/suvia-luzes.png 4 $TMP/sv_neutra.png $TMP/sv_dia.png $TMP/sv_tarde.png $TMP/sv_cinza.png
  echo "review61 $F: trio ok"
fi

if want cena; then
  V="mode=scene,style=e,layout=vila_e,t=1.3,nohud=1"
  snap "$V,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,size=1920x1080,out=$D/vila-dia.png" \
       "$V,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,preset=tarde,size=1920x1080,out=$D/vila-tarde.png" \
       "$V,hx=1.0,hz=3.6,hyaw=200,yaw=24,pitch=13,dist=6.4,size=1920x1080,out=$D/vila-camera-de-jogo.png"
  echo "review61 $F: cena ok"
fi

# comparações antes/depois e vídeos (rodar depois de "antes" e "depois"; usa rigs antigos extraídos do git)
if [ "$F" = depois ] && want comparar; then
  A=$PWD/output/tmp/r61/antes_rigs/assets/characters/npc; C=output/visual-review/round-06-1/npcs/comparacoes; mkdir -p $C
  if [ ! -d "$A" ]; then mkdir -p $PWD/output/tmp/r61/antes_rigs && git archive HEAD assets/characters/npc | tar -x -C $PWD/output/tmp/r61/antes_rigs; fi
  for n in cartografa kelyra indira suvia; do
    .venv/bin/python tools/sheet.py grid $C/$n-antes-depois.png 1 output/visual-review/round-06-1/npcs/antes/$n-vistas.png $D/$n-vistas.png > /dev/null
    .venv/bin/python tools/sheet.py grid $C/$n-expressoes-antes-depois.png 1 output/visual-review/round-06-1/npcs/antes/$n-expressoes.png $D/$n-expressoes.png > /dev/null
  done
  for k in trio-silhueta trio-valores trio-cor elenco suvia-luzes; do
    .venv/bin/python tools/sheet.py grid $C/$k-antes-depois.png 1 output/visual-review/round-06-1/npcs/antes/$k.png $D/$k.png > /dev/null; done
  V="mode=scene,style=e,layout=vila_e,t=1.3,nohud=1,hx=5.2,hz=-3.4,hyaw=20,yaw=10,pitch=6,dist=3.0,ty=0.7,size=900x700"
  for p in dia tarde; do
    snap "$V,hero=$A/suvia,preset=$p,out=$TMP/sombra_antes_$p.png" "$V,hero=characters/npc/suvia,preset=$p,out=$TMP/sombra_depois_$p.png"; done
  sheet $C/suvia-na-sombra-antes-depois.png 2 $TMP/sombra_antes_dia.png $TMP/sombra_depois_dia.png $TMP/sombra_antes_tarde.png $TMP/sombra_depois_tarde.png
  for n in kelyra indira suvia; do
    for y in 30 150; do snap "mode=char,style=e,hero=characters/npc/$n,yaw=$y,pitch=10,dist=1.7,ty=0.75,size=520x520,nohud=1,out=$TMP/hair_${n}_$y.png"; done; done
  sheet $C/cabelos-close.png 3 $TMP/hair_kelyra_30.png $TMP/hair_indira_30.png $TMP/hair_suvia_30.png $TMP/hair_kelyra_150.png $TMP/hair_indira_150.png $TMP/hair_suvia_150.png
  snap "mode=scene,style=e,layout=vila_e,t=1.3,nohud=1,hx=-0.6,hz=2.6,hyaw=180,yaw=200,pitch=12,dist=6.4,size=1600x900,out=$C/cabelos-escala-de-jogo-costas.png"
  sheet $C/kelyra-alternativas-de-papel.png 2 $TMP/alt_a.png $TMP/alt_b.png 2>/dev/null || true
  echo "review61: comparações ok"
fi

if [ "$F" = depois ] && want videos; then
  M=output/visual-review/round-06-1/videos; P=output/visual-review/round-06-1/videos-pranchas; mkdir -p $M $P
  for n in kelyra indira suvia cartografa; do H=characters/npc/$n
    tools/anim.sh $M/$n-gesto "mode=char,style=e,hero=$H,act=emote,hold=0.3,yaw=25,pitch=8,size=560x700,nohud=1" 108 30 >/dev/null
    tools/anim.sh $M/$n-idle "mode=char,style=e,hero=$H,clip=idle,yaw=20,pitch=8,size=560x700,nohud=1" 90 30 >/dev/null
    tools/anim.sh $M/$n-caminhada-costas "mode=char,style=e,hero=$H,clip=walk,yaw=150,pitch=6,size=560x700,nohud=1" 72 30 >/dev/null
  done
  tools/anim.sh $M/trio-idle-lado-a-lado "mode=lineup,style=e,set=trio,myaw=20,pitch=4,size=1400x700,nohud=1" 120 30 >/dev/null
  tools/anim.sh $M/vila-dia "mode=scene,style=e,layout=vila_e,t=1.3,nohud=1,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,size=1280x720" 150 30 >/dev/null
  tools/anim.sh $M/vila-tarde "mode=scene,style=e,layout=vila_e,t=1.3,nohud=1,preset=tarde,hx=1.0,hz=3.6,hyaw=200,yaw=10,pitch=12,dist=7.4,size=1280x720" 150 30 >/dev/null
  tools/anim.sh $M/modular-c4-caminhada "mode=char,style=e,hero=characters/custom/c4_natural_castanho_chapeu,clip=walk,yaw=40,pitch=8,size=560x700,nohud=1" 72 30 >/dev/null
  tools/anim.sh $M/modular-c4-golpe "mode=char,style=e,hero=characters/custom/c4_natural_castanho_chapeu,clip=attack,yaw=30,pitch=8,size=560x700,nohud=1" 72 30 >/dev/null
  for f in $M/*.mp4; do n=$(basename $f .mp4)
    ffmpeg -loglevel error -y -i $f -vf "select='not(mod(n,6))',scale=280:-1,tile=6x3" -frames:v 1 $P/$n.png; done
  echo "review61: vídeos ok"
fi
