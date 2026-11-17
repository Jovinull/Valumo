#!/usr/bin/env bash
# Rodada 07 — pacote de revisão da personalização (viewer real; nada é imagem conceitual).
#   tools/review7.sh [gate|cabelos|pele|coberturas|matriz|interface|videos|todos]
set -euo pipefail
cd "$(dirname "$0")/.."
R=${R:-output/visual-review/round-07}; TMP=output/tmp/r7v; mkdir -p $R $TMP   # R= muda a pasta (rodadas seguintes)
SEL="${1:-todos}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = todos ]; }
snap() { tools/snap.sh "$@" > /dev/null; }
sheet() { .venv/bin/python tools/sheet.py grid "$@" > /dev/null; }
B=viewer/build/install/viewer/bin/viewer
HEROES="aventureiro:adventurer exploradora:ranger maga:mage"

if want gate; then
  # gate: preset recomposto (custom=) × rig canônico (hero=), mesmas câmeras; comparação pixel a pixel
  D=$R/presets; mkdir -p $D/pares
  for hp in $HEROES; do IFS=: read -r pid dir <<<"$hp"
    specs=()
    for v in "frente:0:4" "perfil:90:4" "costas:180:4" "iso:35:20"; do IFS=: read -r vn yaw pitch <<<"$v"
      for L in neutra dia; do
        specs+=("mode=char,style=e,hero=characters/e/$dir,yaw=$yaw,pitch=$pitch,preset=$L,size=600x720,nohud=1,out=$D/pares/$pid-$vn-$L-canonico.png")
        specs+=("mode=char,style=e,custom=$pid,yaw=$yaw,pitch=$pitch,preset=$L,size=600x720,nohud=1,out=$D/pares/$pid-$vn-$L-composto.png")
      done
    done
    for e in neutral joy determined surprise; do for L in neutra dia; do
      specs+=("mode=char,style=e,hero=characters/e/$dir,yaw=8,pitch=4,dist=1.5,ty=0.9,expr=$e,preset=$L,size=480x480,nohud=1,out=$D/pares/$pid-rosto-$e-$L-canonico.png")
      specs+=("mode=char,style=e,custom=$pid,yaw=8,pitch=4,dist=1.5,ty=0.9,expr=$e,preset=$L,size=480x480,nohud=1,out=$D/pares/$pid-rosto-$e-$L-composto.png")
    done; done
    specs+=("mode=scene,style=e,hero=characters/e/$dir,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=1280x720,out=$D/pares/$pid-exploracao-dia-canonico.png")
    specs+=("mode=scene,style=e,custom=$pid,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=1280x720,out=$D/pares/$pid-exploracao-dia-composto.png")
    snap "${specs[@]}"
  done
  .venv/bin/python - "$D" <<'PY'
import sys, glob, os
from PIL import Image, ImageChops
D=sys.argv[1]; rows=[]; fails=0
for c in sorted(glob.glob(f"{D}/pares/*-canonico.png")):
    k=c.replace("-canonico.png",""); m=k+"-composto.png"
    a=Image.open(c).convert("RGB"); b=Image.open(m).convert("RGB"); d=ImageChops.difference(a,b)
    n=sum(1 for p in d.getdata() if p!=(0,0,0)); mx=max(max(p) for p in d.getdata())
    fails+= n>0; rows.append((os.path.basename(k), n, mx))
with open(f"{D}/gate.md","w") as f:
    f.write("# Gate pixel a pixel dos presets (rodada 07)\n\nPreset recomposto em Java (`custom=<preset>`) × rig canônico (`hero=characters/e/...`), mesmas câmeras, sem tolerância.\n\n| Captura | pixels diferentes | desvio máx |\n|---|---|---|\n")
    for k,n,mx in rows: f.write(f"| {k} | {n} | {mx}/255 |\n")
    f.write(f"\n**{len(rows)} pares, {fails} com diferença.**\n")
print(f"gate: {len(rows)} pares, {fails} com diferença")
PY
  for hp in $HEROES; do IFS=: read -r pid dir <<<"$hp"
    sheet $D/$pid-canonico-x-composto.png 4 $D/pares/$pid-frente-dia-canonico.png $D/pares/$pid-frente-dia-composto.png $D/pares/$pid-costas-dia-canonico.png $D/pares/$pid-costas-dia-composto.png; done
fi

if want cabelos; then
  D=$R/cabelos; mkdir -p $D
  L=""; S=""
  for h in topete curto chanel rabo; do
    for v in "f:20:6" "p:90:4" "c:180:4"; do IFS=: read -r vn yaw pitch <<<"$v"
      snap "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.85,preset=neutra,size=360x360,nohud=1,out=$TMP/hs_${h}_$vn.png" \
           "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.85,sil=1,size=360x360,nohud=1,out=$TMP/hsil_${h}_$vn.png"
      L="$L $TMP/hs_${h}_$vn.png"; S="$S $TMP/hsil_${h}_$vn.png"; done; done
  sheet $D/estilos-frente-perfil-costas.png 3 $L
  sheet $D/estilos-silhuetas.png 3 $S
  C=""
  for col in c2582c e3a64a e2d4f2 6b4630 2e2632 e9c768 a83a2a a3a0ab 5f8a5a 4a5a9a; do
    snap "mode=char,style=e,custom=exploradora,cabelo=cabelo.chanel,corCabelo=$col,cobertura=nenhum,yaw=25,pitch=6,dist=1.6,ty=0.85,size=300x300,nohud=1,out=$TMP/cor_$col.png"
    C="$C $TMP/cor_$col.png"; done
  sheet $D/cores-de-cabelo.png 5 $C
  snap "mode=scene,style=e,custom=aventureiro,cabelo=cabelo.curto,corCabelo=e9c768,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=1280x720,out=$D/camera-de-jogo-curto-loiro.png" \
       "mode=scene,style=e,custom=maga,cabelo=cabelo.rabo,corCabelo=2e2632,cobertura=nenhum,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=160,yaw=-30,pitch=13,dist=5.2,size=1280x720,out=$D/camera-de-jogo-rabo-preto.png"
fi

if want pele; then
  D=$R/pele-e-rostos; mkdir -p $D
  L=""
  for sk in f6c9a0 e8b185 f9d8ba c98a5e 8f5c3e; do for P in neutra dia; do
    snap "mode=char,style=e,custom=aventureiro,pele=$sk,yaw=10,pitch=4,dist=1.5,ty=0.9,preset=$P,size=320x320,nohud=1,out=$TMP/sk_${sk}_$P.png"; L="$L $TMP/sk_${sk}_$P.png"; done; done
  sheet $D/tons-de-pele-neutra-e-dia.png 2 $L
  F=""
  for f in aventureiro exploradora maga; do for e in neutral joy determined surprise; do
    snap "mode=char,style=e,custom=aventureiro,rosto=rosto.$f,cabelo=cabelo.curto,corCabelo=6b4630,pele=c98a5e,expr=$e,yaw=10,pitch=4,dist=1.5,ty=0.9,size=320x320,nohud=1,out=$TMP/fx_${f}_$e.png"; F="$F $TMP/fx_${f}_$e.png"; done; done
  sheet $D/rostos-x-expressoes.png 4 $F
  I=""
  for ir in 3d9c78 3d8fd8 8a58c8 c08a2a 6b4630; do
    snap "mode=char,style=e,custom=maga,iris=$ir,cobertura=nenhum,yaw=6,pitch=3,dist=1.2,ty=0.95,size=320x320,nohud=1,out=$TMP/ir_$ir.png"; I="$I $TMP/ir_$ir.png"; done
  sheet $D/cores-de-iris.png 5 $I
fi

if want coberturas; then
  D=$R/chapeus-e-capuzes; mkdir -p $D
  for c in nenhum chapeu.maga capuz.c3; do
    L=""
    for h in topete curto chanel rabo; do for v in "f:25:6" "c:165:4"; do IFS=: read -r vn yaw pitch <<<"$v"
      snap "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=$c,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.85,size=320x320,nohud=1,out=$TMP/cv_${c}_${h}_$vn.png"
      L="$L $TMP/cv_${c}_${h}_$vn.png"; done; done
    sheet $D/${c}-quatro-estilos-frente-costas.png 4 $L
  done
  E=""
  for e in neutral joy determined surprise; do for c in chapeu.maga capuz.c3; do
    snap "mode=char,style=e,custom=exploradora,cabelo=cabelo.topete,cobertura=$c,expr=$e,yaw=10,pitch=4,dist=1.5,ty=0.9,size=300x300,nohud=1,out=$TMP/ce_${c}_$e.png"; E="$E $TMP/ce_${c}_$e.png"; done; done
  sheet $D/expressoes-com-chapeu-e-capuz.png 2 $E
  snap "mode=char,style=e,custom=aventureiro,cabelo=cabelo.chanel,cobertura=chapeu.maga,clip=run,t=0.4,yaw=60,pitch=8,size=500x600,nohud=1,out=$TMP/mv1.png" \
       "mode=char,style=e,custom=aventureiro,cabelo=cabelo.chanel,cobertura=chapeu.maga,clip=attack,t=0.35,yaw=30,pitch=8,size=500x600,nohud=1,out=$TMP/mv2.png" \
       "mode=char,style=e,custom=maga,cabelo=cabelo.rabo,cobertura=capuz.c3,clip=run,t=0.4,yaw=140,pitch=8,size=500x600,nohud=1,out=$TMP/mv3.png" \
       "mode=char,style=e,custom=maga,cabelo=cabelo.rabo,cobertura=capuz.c3,clip=attack,t=0.6,yaw=30,pitch=8,size=500x600,nohud=1,out=$TMP/mv4.png"
  sheet $D/poses-de-movimento.png 4 $TMP/mv1.png $TMP/mv2.png $TMP/mv3.png $TMP/mv4.png
fi

if want matriz; then
  D=$R/matriz; mkdir -p $D
  K="mode=char,style=e,yaw=25,pitch=6,size=400x500,nohud=1"
  snap "$K,custom=aventureiro,cabelo=cabelo.curto,cobertura=nenhum,out=$D/01-curto-sem-chapeu.png" \
       "$K,custom=exploradora,cabelo=cabelo.chanel,cobertura=nenhum,out=$D/02-medio-sem-chapeu.png" \
       "$K,custom=aventureiro,cobertura=chapeu.maga,out=$D/03-topete-com-chapeu.png" \
       "$K,custom=aventureiro,cabelo=cabelo.rabo,cobertura=chapeu.maga,out=$D/04-outro-cabelo-com-chapeu.png" \
       "$K,custom=maga,cabelo=cabelo.curto,cobertura=capuz.c3,out=$D/05-cabelo-sob-capuz.png" \
       "$K,custom=aventureiro,cabelo=cabelo.chanel,corCabelo=e9c768,pele=f9d8ba,cobertura=nenhum,out=$D/06-cabelo-e-pele-semelhantes.png" \
       "$K,custom=exploradora,cabelo=cabelo.curto,corCabelo=2e2632,pele=8f5c3e,cobertura=nenhum,preset=tarde,out=$D/07-cabelo-escuro-luz-de-tarde.png" \
       "$K,custom=maga,cabelo=cabelo.topete,corCabelo=e9c768,cobertura=nenhum,preset=tarde,out=$D/08-cabelo-claro-fim-de-tarde.png" \
       "$K,custom=aventureiro,cabelo=cabelo.curto,cobertura=chapeu.maga,expr=surprise,out=$D/09-expressao-com-chapeu.png" \
       "$K,custom=aventureiro,cabelo=cabelo.rabo,corCabelo=a83a2a,clip=walk,t=0.5,out=$D/10-caminhada.png" \
       "$K,custom=maga,cabelo=cabelo.curto,corCabelo=4a5a9a,clip=run,t=0.45,out=$D/11-corrida.png" \
       "$K,custom=aventureiro,cabelo=cabelo.chanel,cobertura=capuz.c3,clip=attack,t=0.35,out=$D/12-golpe-customizado.png" \
       "$K,custom=aventureiro,out=$D/13-volta-ao-preset.png"
  sheet $R/matriz-de-combinacoes.png 5 $(ls $D/*.png)
fi

if want interface; then
  rm -rf $R/interface && $B --custom-demo $R/interface > $R/interface-roteiro.txt 2>/dev/null
  sheet $R/interface-prancha.png 4 $(ls $R/interface/*.png)
fi

if want videos; then
  V=$R/videos; P=$R/videos-pranchas; mkdir -p $V $P
  for c in idle walk run attack; do
    tools/anim.sh $V/aventureiro-original-$c "mode=char,style=e,hero=characters/e/adventurer,clip=$c,yaw=30,pitch=8,size=480x600,nohud=1" 72 30 >/dev/null
    tools/anim.sh $V/aventureiro-personalizado-$c "mode=char,style=e,custom=aventureiro,cabelo=cabelo.rabo,corCabelo=2e2632,pele=c98a5e,cobertura=chapeu.maga,clip=$c,yaw=30,pitch=8,size=480x600,nohud=1" 72 30 >/dev/null
  done
  tools/anim.sh $V/maga-personalizada-corrida "mode=char,style=e,custom=maga,cabelo=cabelo.curto,corCabelo=e9c768,cobertura=capuz.c3,clip=run,yaw=60,pitch=8,size=480x600,nohud=1" 72 30 >/dev/null
  tools/anim.sh $V/exploradora-personalizada-golpe "mode=char,style=e,custom=exploradora,cabelo=cabelo.topete,corCabelo=a83a2a,cobertura=nenhum,clip=attack,yaw=40,pitch=8,size=480x600,nohud=1" 90 30 >/dev/null
  tools/anim.sh $V/cena-personalizado-andando "mode=scene,style=e,custom=aventureiro,cabelo=cabelo.curto,corCabelo=6b4630,cobertura=chapeu.maga,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=200,seq=walk:2.0+idle:0.8+run:1.2,yaw=18,pitch=13,dist=6.4,size=1280x720" 120 30 >/dev/null
  for f in $V/*.mp4; do n=$(basename $f .mp4)
    ffmpeg -loglevel error -y -i $f -vf "select='not(mod(n,6))',scale=240:-1,tile=6x2" -frames:v 1 $P/$n.png; done
fi
echo "review7: $SEL ok"
