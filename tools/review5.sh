#!/usr/bin/env bash
# Rodada 05 — vídeos de revisão (viewer real + ffmpeg). Velocidade normal; câmera de jogo; comparações
# equivalentes com a rodada 04 (mesma spec, lado a lado: esquerda = R04, direita = R05).
#   tools/review5.sh [videos|camera|comparacoes|todos]
set -euo pipefail
cd "$(dirname "$0")/.."
R=output/visual-review/round-05; VD=$R/videos; TMP=output/tmp/r5v; mkdir -p $VD $TMP
want() { [ "${1}" = "${SEL}" ] || [ "${SEL}" = todos ]; }
SEL="${1:-todos}"
anim() { tools/anim.sh "$@" > /dev/null; echo "review5: $1.mp4"; }
ENC=(-c:v libopenh264 -b:v 6M -pix_fmt yuv420p)
ISO="yaw=35,pitch=18,size=720x720,nohud=1"
LB=characters/r4/adventurer_longblade

if want videos; then
  anim $VD/01-espada-horizontal "mode=char,style=e,char=0,act=slash_h,hold=0.4,$ISO" 44 30
  anim $VD/02-espada-diagonal "mode=char,style=e,char=0,act=slash_d,hold=0.4,$ISO" 45 30
  anim $VD/03-espada-descendente "mode=char,style=e,char=0,act=slash_v,hold=0.4,$ISO" 48 30
  anim $VD/04-espada-sequencia "mode=char,style=e,char=0,act=combo,hold=0.4,$ISO" 68 30
  anim $VD/05-escudo-guarda-e-bloqueio "mode=char,style=e,hero=characters/r4/adventurer_shield,seq=shield_guard+shield_block,$ISO" 141 30
  anim $VD/06-lamina-longa-tres-ataques "mode=char,style=e,hero=$LB,act=long_combo,hold=0.4,$ISO" 85 30
  anim $VD/07-lamina-longa-guarda-duas-maos "mode=char,style=e,hero=$LB,act=guard_two_hands,hold=0.3,$ISO" 93 30
  anim $VD/07b-lamina-longa-guarda-duas-maos-frente "mode=char,style=e,hero=$LB,act=guard_two_hands,hold=0.3,yaw=0,pitch=8,size=720x720,nohud=1" 93 30
  anim $VD/08-arco-preparar-puxar-disparar "mode=char,style=e,char=1,act=bow_shot,hold=0.4,yaw=-60,pitch=12,size=720x720,nohud=1" 72 30
  anim $VD/08b-arco-tres-quartos-costas "mode=char,style=e,char=1,act=bow_shot,hold=0.4,yaw=200,pitch=14,size=720x720,nohud=1" 72 30
  anim $VD/09-cajado-conjuracao-frontal "mode=char,style=e,char=2,act=staff_cast,hold=0.4,yaw=60,pitch=12,size=720x720,nohud=1" 66 30
  anim $VD/10-cajado-conjuracao-circular "mode=char,style=e,char=2,act=staff_cast_wide,hold=0.4,$ISO" 72 30
  for h in "0:espada" "1:arco" "2:cajado"; do IFS=: read -r c n <<<"$h"
    anim $VD/11-reacao-a-dano-$n "mode=char,style=e,char=$c,clip=hit,$ISO" 54 30
    anim $VD/12-comemoracao-$n "mode=char,style=e,char=$c,clip=emote,$ISO" 96 30
  done
  anim $VD/11-reacao-a-dano-lamina-longa "mode=char,style=e,hero=$LB,clip=hit,$ISO" 54 30
  anim $VD/13-caminhada-com-equipamentos "mode=lineup,style=e,set=r4-armas,clip=walk,myaw=70,size=1800x620" 60 30
  anim $VD/14-corrida-com-equipamentos "mode=lineup,style=e,set=r4-armas,clip=run,myaw=70,size=1800x620" 45 30
fi

if want camera; then
  # câmera de jogo: a mesma da referência 22 (atrás e acima do herói, 6,4 u)
  GC="yaw=18,pitch=13,dist=6.4,size=1280x720,nohud=1"
  anim $VD/20-jogo-espada "mode=scene,style=e,hero=characters/e/adventurer,hx=-0.6,hz=3.6,hyaw=20,seq=walk:1.2+slash_h+slash_d+run:0.8+combo+idle:0.5,$GC" 220 30
  anim $VD/21-jogo-lamina-longa "mode=scene,style=e,hero=$LB,hx=-0.6,hz=3.6,hyaw=20,seq=walk:1.0+long_combo+guard_two_hands+idle:0.5,$GC" 210 30
  anim $VD/22-jogo-arco "mode=scene,style=e,hero=characters/e/ranger,hx=-0.6,hz=3.6,hyaw=20,seq=walk:1.0+bow_shot+idle:0.6,$GC" 110 30
  anim $VD/23-jogo-cajado "mode=scene,style=e,hero=characters/e/mage,hx=-0.6,hz=3.6,hyaw=20,seq=walk:1.0+staff_cast+staff_cast_wide+idle:0.5,$GC" 160 30
  anim $VD/24-jogo-equipe-caminhando "mode=scene,style=e,hx=-0.6,hz=3.6,hyaw=20,party=1,clip=walk,move=1,$GC" 120 30
fi

if want comparacoes; then
  # mesma spec da rodada 04 → esquerda R04 (arquivo entregue), direita R05 (gerado agora)
  R4=output/visual-review/round-04/videos
  for p in "01-espada-horizontal:01-espada-horizontal" "05-espada-sequencia:04-espada-sequencia" \
           "07-lamina-longa-tres-ataques:06-lamina-longa-tres-ataques" "08-arco-preparar-puxar-disparar:08-arco-preparar-puxar-disparar" \
           "10-cajado-segunda-conjuracao:10-cajado-conjuracao-circular"; do IFS=: read -r a b <<<"$p"
    ffmpeg -loglevel error -y -i $R4/$a.mp4 -i $VD/$b.mp4 -filter_complex "[0][1]hstack=shortest=1" "${ENC[@]}" $VD/30-r04-x-r05-$b.mp4
    echo "review5: $VD/30-r04-x-r05-$b.mp4"
  done
  # HIT/EMOTE: FK antigo (legacy=1) × coreografado
  for h in "0:espada" "2:cajado"; do IFS=: read -r c n <<<"$h"
    for clip in hit emote; do fr=$([ $clip = hit ] && echo 54 || echo 96)
      KEEP=1 tools/anim.sh $TMP/a "mode=char,style=e,char=$c,clip=$clip,legacy=1,$ISO" $fr 30 >/dev/null
      KEEP=1 tools/anim.sh $TMP/b "mode=char,style=e,char=$c,clip=$clip,$ISO" $fr 30 >/dev/null
      ffmpeg -loglevel error -y -framerate 30 -i $TMP/a-frames/f_%03d.png -framerate 30 -i $TMP/b-frames/f_%03d.png \
        -filter_complex hstack "${ENC[@]}" $VD/31-antes-depois-$clip-$n.mp4
      rm -rf $TMP/a-frames $TMP/b-frames; echo "review5: $VD/31-antes-depois-$clip-$n.mp4"
    done
  done
fi
