#!/usr/bin/env bash
# Rodada 04 — pacote de revisão da coreografia de armas em output/visual-review/round-04/.
# Tudo sai do viewer real (snap offscreen) e de tools/anim.sh (vídeo). Uso:
#   tools/review4.sh [armas|animacoes|validacao|ambiente|videos|demo|todos]
set -euo pipefail
cd "$(dirname "$0")/.."
R=output/visual-review/round-04
V=viewer/build/install/viewer/bin/viewer
PY=.venv/bin/python
only=${1:-todos}
want() { [ "$only" = todos ] || [ "$only" = "$1" ]; }
TMP=output/tmp/r4/review; mkdir -p "$TMP"

# prancha: 5 ângulos (frente, perfil dir., perfil esq., costas, isométrica) × 8 instantes + câmera de exploração
sheet() { # saida hero act dur
  local out=$1 hero=$2 act=$3 dur=$4 specs=() files=() i t a y p
  for a in "0:6" "90:6" "-90:6" "180:6" "35:26"; do y=${a%%:*}; p=${a##*:}
    for i in 0 1 2 3 4 5 6 7; do t=$(python3 -c "print(round($dur*$i/7,4))")
      specs+=("mode=char,style=e,hero=$hero,act=$act,hold=5,t=$t,yaw=$y,pitch=$p,size=300x340,nohud=1,ssaa=1,out=$TMP/s_${y}_$i.png"); files+=("$TMP/s_${y}_$i.png"); done; done
  for i in 0 1 2 3 4 5 6 7; do t=$(python3 -c "print(round($dur*$i/7,4))")
    specs+=("mode=char,style=e,hero=$hero,act=$act,hold=5,t=$t,yaw=200,pitch=17,dist=4.2,size=300x340,nohud=1,ssaa=1,out=$TMP/s_x_$i.png"); files+=("$TMP/s_x_$i.png"); done
  tools/snap.sh "${specs[@]}" >/dev/null
  $PY tools/sheet.py grid "$out" 8 "${files[@]}"
  echo "review4: $out"
}

if want armas; then
  mkdir -p $R/armas
  sheet $R/armas/espada-corte-horizontal.png characters/e/adventurer slash_h 1.05
  sheet $R/armas/espada-corte-diagonal.png characters/e/adventurer slash_d 1.10
  sheet $R/armas/espada-corte-descendente.png characters/e/adventurer slash_v 1.18
  sheet $R/armas/espada-sequencia.png characters/e/adventurer combo 1.86
  sheet $R/armas/escudo-guarda.png characters/r4/adventurer_shield shield_guard 3.3
  sheet $R/armas/escudo-bloqueio.png characters/r4/adventurer_shield shield_block 1.4
  sheet $R/armas/escudo-corte-horizontal.png characters/r4/adventurer_shield slash_h 1.05
  sheet $R/armas/lamina-longa-L1.png characters/r4/adventurer_longblade long_l1 1.18
  sheet $R/armas/lamina-longa-L2.png characters/r4/adventurer_longblade long_l2 1.18
  sheet $R/armas/lamina-longa-L3.png characters/r4/adventurer_longblade long_l3 1.28
  sheet $R/armas/lamina-longa-L4-sequencia.png characters/r4/adventurer_longblade long_combo 2.42
  sheet $R/armas/arco-disparo.png characters/e/ranger bow_shot 2.0
  sheet $R/armas/cajado-conjuracao-frontal.png characters/e/mage staff_cast 1.8
  sheet $R/armas/cajado-conjuracao-ampla.png characters/e/mage staff_cast_wide 2.0
fi

if want animacoes; then
  mkdir -p $R/animacoes
  sheet $R/animacoes/repouso-guarda-repouso-espada.png characters/e/adventurer guard_hold 1.75
  sheet $R/animacoes/repouso-guarda-repouso-lamina.png characters/r4/adventurer_longblade guard_hold 1.85
  for c in walk run idle; do
    tools/snap.sh "mode=lineup,style=e,set=r4-armas,clip=$c,myaw=70,t=0.9,size=2200x720,out=$R/animacoes/locomocao-$c-com-equipamentos.png" >/dev/null
  done
  # transições: corrida → golpe → corrida (quadros de 0,1 s), em cena, câmera de exploração
  specs=(); files=()
  for i in $(seq 0 15); do t=$(python3 -c "print(round(0.6+$i*0.1,2))")
    specs+=("mode=scene,style=e,hero=characters/e/adventurer,hyaw=25,seq=run:1.2+slash_h+run:1.0,t=$t,yaw=62,pitch=17,dist=5.5,size=480x400,nohud=1,ssaa=1,out=$TMP/tr_$i.png"); files+=("$TMP/tr_$i.png"); done
  tools/snap.sh "${specs[@]}" >/dev/null; $PY tools/sheet.py grid $R/animacoes/transicao-corrida-golpe-corrida.png 8 "${files[@]}"
  # pés: bancada de terreno de perfil (plano, rampa, degraus de 1 voxel, degraus de 0,2 u) e corrida irregular
  for g in flat ramp steps big; do specs=(); files=()
    for i in $(seq 0 7); do t=$(python3 -c "print(round(1.0+$i*0.057,3))")
      specs+=("mode=char,style=e,char=0,lab=$g,clip=walk,t=$t,yaw=90,pitch=2,size=360x360,nohud=1,ssaa=1,out=$TMP/lab_${g}_$i.png"); files+=("$TMP/lab_${g}_$i.png"); done
    tools/snap.sh "${specs[@]}" >/dev/null; $PY tools/sheet.py grid $R/animacoes/pes-perfil-$g.png 8 "${files[@]}"; done
  echo "review4: animacoes"
fi

if want validacao; then
  mkdir -p $R/validacao
  $V --check $R/validacao/check.csv > $R/validacao/check.txt 2>&1 || true
  $V --check legacy > $R/validacao/auditoria-rodada-03-legacy.txt 2>&1 || true
  # modo de depuração: volumes (verde/vermelho), pivôs, empunhaduras, ponta/emissão, trajetória
  for spec in "adventurer:slash_h:0.40" "adventurer:slash_v:0.45" "ranger:bow_shot:1.0" "mage:staff_cast:0.9"; do
    IFS=: read -r h a t <<<"$spec"
    tools/snap.sh "mode=char,style=e,hero=characters/e/$h,act=$a,hold=5,t=$t,yaw=35,pitch=20,dbg=1,size=900x900,nohud=1,out=$R/validacao/depuracao-$h-$a.png" >/dev/null
  done
  tools/snap.sh "mode=char,style=e,hero=characters/e/adventurer,clip=attack,legacy=1,t=0.13,yaw=35,pitch=20,dbg=1,size=900x900,nohud=1,out=$R/validacao/depuracao-antigo-espada-na-cabeca.png" >/dev/null
  echo "review4: validacao"
fi

if want ambiente; then
  mkdir -p $R/ambiente
  cp output/tmp/r4/env/fungo_antes_alto.png $R/ambiente/fungo-antes-camera-alta.png 2>/dev/null || true
  cp output/tmp/r4/env/cena_fungo_lumi_antes.png $R/ambiente/cena-fungo-lumi-antes.png 2>/dev/null || true
  cp output/tmp/r4/env/lumi_antes.png $R/ambiente/lumi-antes.png 2>/dev/null || true
  tools/snap.sh "mode=char,style=e,hero=creatures/e/fungo,yaw=15,pitch=28,size=500x500,nohud=1,out=$R/ambiente/fungo-depois-camera-alta.png" \
    "mode=char,style=e,hero=creatures/e/fungo,yaw=15,pitch=8,size=500x500,nohud=1,out=$R/ambiente/fungo-depois-camera-baixa.png" \
    "mode=char,style=e,hero=creatures/e/lumi,yaw=25,pitch=12,size=500x500,nohud=1,out=$R/ambiente/lumi-depois.png" \
    "mode=scene,style=e,hx=5.0,hz=-2.4,hyaw=120,yaw=-150,pitch=17,dist=4.5,t=1.3,size=1280x720,nohud=1,out=$R/ambiente/cena-fungo-lumi-depois.png" \
    "mode=scene,style=e,yaw=-40,pitch=28,dist=26,t=1.3,far=0,size=1600x900,nohud=1,out=$R/ambiente/relevo-panorama-sem-tratamento.png" \
    "mode=scene,style=e,yaw=-40,pitch=28,dist=26,t=1.3,far=1,size=1600x900,nohud=1,out=$R/ambiente/relevo-panorama-com-tratamento.png" \
    "mode=scene,style=e,t=1.3,far=0,size=1920x1080,nohud=1,yaw=28,pitch=17,dist=7.5,out=$R/ambiente/relevo-exploracao-sem-tratamento.png" \
    "mode=scene,style=e,t=1.3,far=1,size=1920x1080,nohud=1,yaw=28,pitch=17,dist=7.5,out=$R/ambiente/relevo-exploracao-com-tratamento.png" >/dev/null
  $PY tools/sheet.py grid $R/ambiente/relevo-panorama-antes-depois.png 2 $R/ambiente/relevo-panorama-sem-tratamento.png $R/ambiente/relevo-panorama-com-tratamento.png
  echo "review4: ambiente"
fi

anim() { tools/anim.sh "$@" > /dev/null; echo "review4: $1.mp4"; }
if want videos; then
  mkdir -p $R/videos; VD=$R/videos
  ISO="yaw=35,pitch=18,size=720x720,nohud=1"
  anim $VD/01-espada-horizontal "mode=char,style=e,char=0,act=slash_h,hold=0.4,$ISO" 44 30
  anim $VD/02-espada-diagonal "mode=char,style=e,char=0,act=slash_d,hold=0.4,$ISO" 45 30
  anim $VD/03-espada-descendente "mode=char,style=e,char=0,act=slash_v,hold=0.4,$ISO" 48 30
  anim $VD/04-escudo-guarda-e-bloqueio "mode=char,style=e,hero=characters/r4/adventurer_shield,seq=shield_guard+shield_block,$ISO" 141 30
  anim $VD/05-espada-sequencia "mode=char,style=e,char=0,act=combo,hold=0.4,$ISO" 68 30
  anim $VD/05b-espada-sequencia-60fps "mode=char,style=e,char=0,act=combo,hold=0.4,$ISO" 136 60
  anim $VD/06-lamina-longa-alternando-lados "mode=char,style=e,hero=characters/r4/adventurer_longblade,seq=long_l1+long_l2,$ISO" 71 30
  anim $VD/07-lamina-longa-tres-ataques "mode=char,style=e,hero=characters/r4/adventurer_longblade,act=long_combo,hold=0.4,$ISO" 85 30
  anim $VD/07b-lamina-longa-meia-velocidade "mode=char,style=e,hero=characters/r4/adventurer_longblade,act=long_combo,hold=0.4,speed=0.5,$ISO" 170 30
  anim $VD/08-arco-preparar-puxar-disparar "mode=char,style=e,char=1,act=bow_shot,hold=0.4,yaw=-60,pitch=12,size=720x720,nohud=1" 72 30
  anim $VD/09-cajado-conjuracao-frontal "mode=char,style=e,char=2,act=staff_cast,hold=0.4,yaw=60,pitch=12,size=720x720,nohud=1" 66 30
  anim $VD/10-cajado-segunda-conjuracao "mode=char,style=e,char=2,act=staff_cast_wide,hold=0.4,$ISO" 72 30
  anim $VD/11-caminhada-com-equipamentos "mode=lineup,style=e,set=r4-armas,clip=walk,myaw=70,size=1800x620" 60 30
  anim $VD/12-corrida-com-equipamentos "mode=lineup,style=e,set=r4-armas,clip=run,myaw=70,size=1800x620" 45 30
  anim $VD/13-corrida-para-ataque "mode=scene,style=e,hero=characters/e/adventurer,hyaw=25,seq=run:1.2+slash_h+run:1.0,yaw=62,pitch=17,dist=5.5,size=1280x720,nohud=1" 99 30
  anim $VD/14-caminhada-encosta-perfil "mode=char,style=e,char=0,lab=ramp,clip=walk,yaw=90,pitch=2,size=900x600,nohud=1" 90 30
  anim $VD/14b-caminhada-degraus-perfil "mode=char,style=e,char=0,lab=steps,clip=walk,yaw=90,pitch=2,size=900x600,nohud=1" 90 30
  anim $VD/14c-degraus-grandes-limite "mode=char,style=e,char=0,lab=big,clip=walk,yaw=90,pitch=2,size=900x600,nohud=1" 90 30
  anim $VD/14d-corrida-terreno-irregular "mode=char,style=e,char=0,lab=steps,clip=run,yaw=90,pitch=2,size=900x600,nohud=1" 60 30
  anim $VD/15-depuracao-volumes-e-trajetoria "mode=char,style=e,char=0,act=slash_h,hold=0.4,dbg=1,$ISO" 44 30
  # antes × depois (mesmo enquadramento): ataque da rodada 03 (legacy) × golpe novo, lado a lado
  for h in "0:slash_h:espada" "1:bow_shot:arco" "2:staff_cast:cajado"; do IFS=: read -r c a n <<<"$h"
    KEEP=1 tools/anim.sh $TMP/antes_$n "mode=char,style=e,char=$c,clip=attack,legacy=1,$ISO" 60 30 >/dev/null
    KEEP=1 tools/anim.sh $TMP/depois_$n "mode=char,style=e,char=$c,act=$a,hold=0.4,$ISO" 60 30 >/dev/null
    ffmpeg -loglevel error -y -framerate 30 -i "$TMP/antes_$n-frames/f_%03d.png" -framerate 30 -i "$TMP/depois_$n-frames/f_%03d.png" \
      -filter_complex hstack -c:v libopenh264 -b:v 6M -pix_fmt yuv420p $VD/16-antes-depois-$n.mp4
    rm -rf "$TMP/antes_$n-frames" "$TMP/depois_$n-frames"; echo "review4: $VD/16-antes-depois-$n.mp4"
  done
fi

if want demo; then
  mkdir -p $R/videos; VD=$R/videos; parts=()
  CAM="hyaw=25,yaw=48,pitch=17,dist=6.0,size=1280x720,nohud=1"
  KEEP=1 tools/anim.sh $TMP/demo_a "mode=scene,style=e,hero=characters/e/adventurer,seq=walk:1.4+slash_h+slash_v+run:1.0+combo+idle:0.6,$CAM" 210 30 >/dev/null
  KEEP=1 tools/anim.sh $TMP/demo_r "mode=scene,style=e,hero=characters/e/ranger,hx=2.0,hz=6.0,hyaw=200,seq=walk:1.2+bow_shot+idle:0.5,yaw=150,pitch=15,dist=5.5,size=1280x720,nohud=1" 114 30 >/dev/null
  KEEP=1 tools/anim.sh $TMP/demo_m "mode=scene,style=e,hero=characters/e/mage,hx=-0.8,hz=2.6,hyaw=20,seq=walk:1.0+staff_cast+staff_cast_wide+idle:0.5,yaw=75,pitch=15,dist=5.2,size=1280x720,nohud=1" 159 30 >/dev/null
  : > $TMP/demo_list.txt
  for d in demo_a demo_r demo_m; do i=0; for f in $TMP/$d-frames/f_*.png; do echo "file '$(realpath $f)'" >> $TMP/demo_list.txt; echo "duration 0.0333333" >> $TMP/demo_list.txt; done; done
  ffmpeg -loglevel error -y -f concat -safe 0 -i $TMP/demo_list.txt -c:v libopenh264 -b:v 8M -pix_fmt yuv420p -r 30 $VD/00-demonstracao-cenario.mp4
  rm -rf $TMP/demo_a-frames $TMP/demo_r-frames $TMP/demo_m-frames
  echo "review4: $VD/00-demonstracao-cenario.mp4"
fi
