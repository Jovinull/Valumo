#!/usr/bin/env bash
# Rodada 09 — pacote de revisão: áudio (música, efeitos, contexto real no jogo e no criador) e os dois estudos de
# cabelo (crespo, coque). Nada é imagem ou som conceitual: tudo sai do viewer, do jogo e do LMMS reais.
#
#   tools/review9.sh [audio|contexto|ambiente|criador|cabelos|armas|olhos|botas|pausa|gate|testes|tudo]  (padrão: tudo)
#
# Saída: output/visual-review/round-09/ (fora do git). REBUILD=1 recompila viewer e jogo antes.
# Os roteiros ficam MUDOS no alto-falante: o som é gravado pelo loopback do OpenAL (sincronia por quadro).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
R=output/visual-review/round-09
V=viewer/build/install/viewer/bin/viewer
G=game/build/install/game/bin/game
PY=.venv/bin/python
SEL="${1:-tudo}"; want() { [ "$1" = "$SEL" ] || [ "$SEL" = tudo ]; }
if [ -n "${REBUILD:-}" ] || [ ! -x "$V" ] || [ ! -x "$G" ]; then ./gradlew -q :viewer:installDist :game:installDist; fi
mkdir -p "$R" output/tmp
enc=(-c:v libx264 -crf 20); ffmpeg -hide_banner -encoders 2>/dev/null | grep libx264 > /dev/null || enc=(-c:v libopenh264 -b:v 8M)
snap() { tools/snap.sh "$@" > /dev/null; }
sheet() { $PY tools/sheet.py grid "$@" > /dev/null; }

if want audio; then
  # música: render das sessões (LMMS), laço calibrado, master, OGG de runtime, QA, espectrogramas
  for x in a b; do
    python3 fontes/audio/musica/vila/vila_$x.py
    $PY tools/audio/render_musica.py fontes/audio/musica/vila/vila_$x/vila_$x.mmp "$R/musica/vila_$x" --runtime assets/audio/musica/vila_$x > /dev/null
    L=$(python3 -c "import json;d=json.load(open('$R/musica/vila_$x/vila_$x-render.json'));print(f\"{d['laco_inicio_amostra']},{d['laco_fim_amostra']}\")")
    $PY tools/audio/audio_qa.py --tipo musica --laco "$L" --imagens "$R/musica/vila_$x/img" --md "$R/musica/vila_$x/qa-jogo.md" \
      "$R/musica/vila_$x/vila_$x-jogo.wav" assets/audio/musica/vila_$x.ogg > /dev/null || true
    $PY tools/audio/audio_qa.py --tipo musica --imagens "$R/musica/vila_$x/img" --md "$R/musica/vila_$x/qa-escuta.md" "$R/musica/vila_$x/vila_$x-escuta.wav" > /dev/null || true
  done
  # efeitos: NÃO re-renderiza as tomadas (o ruído do Kicker muda a cada render); mede as versionadas
  $PY tools/audio/audio_qa.py --catalogo assets/audio/sons.json --md "$R/efeitos-sonoros/qa-catalogo.md" > /dev/null || true
  tail -1 "$R/efeitos-sonoros/qa-catalogo.md"
fi

if want ambiente; then
  # camadas de ambiente (LMMS → laço com crossfade → OGG) + jogo indo à margem do lago e se afastando (loopback)
  python3 fontes/audio/ambiente/ambiente.py > /dev/null
  for n in vento lago; do $PY tools/audio/render_ambiente.py fontes/audio/ambiente/$n.mmp "$R/ambiente" --runtime assets/audio/ambiente/$n > /dev/null; done
  tmp="$(mktemp -d output/tmp/r9amb.XXXXXX)"; D=$R/integracao/ambiente; mkdir -p "$D"
  JAVA_OPTS="-Dvx.audio=loopback -Dvx.musica=nenhuma -Dvx.audio.log=true -Dvx.prefs=$PWD/output/tmp/prefs-revisao.json -Dvx.audio.gravar=$PWD/$D/ambiente-mixagem.wav" \
    $G --roteiro tools/jogo/09-ambiente.txt "$tmp" 30 2>&1 | grep -E '^som|^estado|gravad' > "$D/ambiente-eventos.txt" || true
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" -i "$D/ambiente-mixagem.wav" "${enc[@]}" -pix_fmt yuv420p \
    -c:a aac -b:a 192k -shortest "$D/ambiente-com-som.mp4"; rm -rf "$tmp"
  grep -c 'passo.areia' "$D/ambiente-eventos.txt" | xargs echo "passos na areia:"
fi

if want pausa; then
  JAVA_OPTS="-Dvx.prefs=$PWD/output/tmp/prefs-revisao.json" $G --roteiro tools/jogo/08-pausa-volume.txt "$R/integracao/pausa" 30 2>&1 | grep -E '^jogo' || true
fi

if want armas; then
  # arma × cabeça composta, voxel a voxel (estudos não podem ser piores que o penteado do preset)
  $V --check-cabelos "$R/validacao/09-arma-x-cabelos.md" | tail -1
fi

if want olhos; then
  T=output/tmp/r9o; D=$R/personalizacao/olhos; mkdir -p "$T" "$D"; specs=()
  for p in aventureiro exploradora maga; do for o in redondos amendoados inclinados; do for e in neutral joy determined surprise; do
    specs+=("mode=char,style=e,custom=$p,olhos=olhos.$o,cobertura=nenhum,yaw=6,pitch=4,dist=1.5,ty=0.9,expr=$e,preset=dia,size=260x260,nohud=1,out=$T/${p}_${o}_$e.png"); done; done; done
  snap "${specs[@]}"
  for p in aventureiro exploradora maga; do sheet "$D/$p-formatos-x-expressoes.png" 4 $(for o in redondos amendoados inclinados; do for e in neutral joy determined surprise; do echo $T/${p}_${o}_$e.png; done; done); done
  $V --editor-script tools/criador/olhos-r09.txt "$D/criador" 2>&1 | grep -cE '^criador' | xargs echo "capturas do criador:"
fi

if want botas; then
  T=output/tmp/r9b; D=$R/personalizacao/botas; mkdir -p "$T" "$D/videos"; specs=()
  for p in aventureiro exploradora maga; do for f in botas.originais botas.viajante; do for v in "f:20:12" "p:90:8" "c:200:12"; do IFS=: read -r vn yaw pitch <<<"$v"
    specs+=("mode=char,style=e,custom=$p,pes=$f,yaw=$yaw,pitch=$pitch,dist=1.3,ty=0.18,preset=dia,size=260x260,nohud=1,out=$T/${p}_${f}_$vn.png"); done; done; done
  snap "${specs[@]}"
  sheet "$D/01-botas-tres-corpos.png" 6 $(for p in aventureiro exploradora maga; do for v in f p c; do echo $T/${p}_botas.originais_$v.png $T/${p}_botas.viajante_$v.png; done; done)
  for p in aventureiro exploradora; do for c in "perfil:90" "frente:15"; do IFS=: read -r cn yaw <<<"$c"
    tools/anim.sh "$D/videos/$p-viajante-correr-$cn" "mode=char,style=e,custom=$p,pes=botas.viajante,clip=run,yaw=$yaw,pitch=6,dist=1.6,ty=0.3,preset=dia,size=360x360,nohud=1" 36 30 > /dev/null; done; done
  $V --editor-script tools/criador/roupa-r09.txt "$D/criador" 2>&1 | grep -cE '^criador' | xargs echo "capturas do criador:"
fi

if want contexto; then
  # jogo real: loopback grava a mistura em sincronia com os quadros; registro de eventos com superfície
  tmp="$(mktemp -d output/tmp/r9ctx.XXXXXX)"; mkdir -p "$R/integracao"
  JAVA_OPTS="-Dvx.audio=loopback -Dvx.audio.log=true -Dvx.audio.gravar=$PWD/$R/integracao/jogo-contexto-mixagem.wav" \
    $G --roteiro tools/jogo/07-audio.txt "$tmp" 30 2>&1 | grep -E '^som|gravad|^estado' > "$R/integracao/jogo-contexto-eventos.txt" || true
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" -i "$R/integracao/jogo-contexto-mixagem.wav" \
    "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$R/integracao/jogo-contexto-com-som.mp4"
  rm -rf "$tmp"; grep -E 'gravad' "$R/integracao/jogo-contexto-eventos.txt"
fi

if want criador; then
  tmp="$(mktemp -d output/tmp/r9cr.XXXXXX)"; mkdir -p "$R/personalizacao/criador"
  JAVA_OPTS="-Dvx.audio=loopback -Dvx.audio.gravar=$PWD/$R/personalizacao/criador/sessao-mixagem.wav" \
    $V --editor-script tools/criador/cabelos-r09.txt "$tmp" 30 2>&1 | grep -E '^criador|gravad' || true
  cp "$tmp"/*.png "$R/personalizacao/criador/"
  ffmpeg -loglevel error -y -framerate 30 -i "$tmp/quadros/q_%05d.png" -i "$R/personalizacao/criador/sessao-mixagem.wav" \
    "${enc[@]}" -pix_fmt yuv420p -c:a aac -b:a 192k -shortest "$R/personalizacao/criador/sessao-com-som.mp4"
  rm -rf "$tmp"
fi

if want cabelos; then
  D=$R/personalizacao; T=output/tmp/r9c; mkdir -p "$D" "$T"
  ALL="topete curto chanel rabo crespo coque"; NEW="crespo coque"
  # 1. seis estilos: frente, perfil, costas (luz neutra) + silhuetas, mesma câmera
  specs=(); for h in $ALL; do for v in "f:20:6" "p:90:4" "c:180:4"; do IFS=: read -r vn yaw pitch <<<"$v"
    specs+=("mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.85,preset=neutra,size=360x360,nohud=1,out=$T/hs_${h}_$vn.png"
            "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.85,sil=1,size=360x360,nohud=1,out=$T/hsil_${h}_$vn.png"); done; done
  snap "${specs[@]}"
  sheet "$D/01-seis-estilos-frente-perfil-costas.png" 6 $(for v in f p c; do for h in $ALL; do echo $T/hs_${h}_$v.png; done; done)
  sheet "$D/02-seis-estilos-silhuetas.png" 6 $(for v in f p c; do for h in $ALL; do echo $T/hsil_${h}_$v.png; done; done)
  # 2. close (luz de dia e de tarde), 3. as 10 cores, 4. coberturas frente/costas, 5. expressões, 6. presets
  specs=(); for h in $NEW; do for L in dia tarde; do for v in "f:15:8" "p:100:6" "c:200:8"; do IFS=: read -r vn yaw pitch <<<"$v"
    specs+=("mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=$yaw,pitch=$pitch,dist=1.25,ty=0.95,preset=$L,size=480x480,nohud=1,out=$T/cl_${h}_${L}_$vn.png"); done; done
    for c in c2582c e3a64a e2d4f2 6b4630 2e2632 e9c768 a83a2a a3a0ab 5f8a5a 4a5a9a; do
      specs+=("mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,corCabelo=$c,cobertura=nenhum,yaw=25,pitch=8,dist=1.5,ty=0.9,preset=dia,size=300x300,nohud=1,out=$T/cor_${h}_$c.png"); done
    for cv in chapeu.maga capuz.c3; do for v in "f:20:6" "c:180:4" "p:90:4"; do IFS=: read -r vn yaw pitch <<<"$v"
      specs+=("mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=$cv,yaw=$yaw,pitch=$pitch,dist=1.7,ty=0.9,preset=dia,size=360x360,nohud=1,out=$T/cob_${h}_${cv}_$vn.png"); done; done
    for e in neutral joy determined surprise; do
      specs+=("mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,yaw=8,pitch=4,dist=1.5,ty=0.9,expr=$e,preset=dia,size=360x360,nohud=1,out=$T/ex_${h}_$e.png"); done
    for p in exploradora maga; do
      specs+=("mode=char,style=e,custom=$p,cabelo=cabelo.$h,cobertura=nenhum,yaw=25,pitch=6,dist=1.9,ty=0.8,preset=dia,size=360x360,nohud=1,out=$T/pr_${h}_$p.png"); done
  done
  snap "${specs[@]}"
  for h in $NEW; do
    sheet "$D/03-$h-close-dia-tarde.png" 3 $T/cl_${h}_dia_{f,p,c}.png $T/cl_${h}_tarde_{f,p,c}.png
    sheet "$D/04-$h-dez-cores.png" 5 $T/cor_${h}_*.png
    sheet "$D/05-$h-coberturas.png" 3 $T/cob_${h}_chapeu.maga_{f,c,p}.png $T/cob_${h}_capuz.c3_{f,c,p}.png
    sheet "$D/06-$h-expressoes.png" 4 $T/ex_${h}_{neutral,joy,determined,surprise}.png
    sheet "$D/07-$h-em-outros-corpos.png" 2 $T/pr_${h}_exploradora.png $T/pr_${h}_maga.png
  done
  # 8. escala de jogo (câmera de exploração, baixa resolução ampliada sem filtro) e costas na cena
  specs=(); for h in topete crespo coque; do
    specs+=("mode=scene,style=e,custom=aventureiro,cabelo=cabelo.$h,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=200,yaw=18,pitch=13,dist=6.4,size=480x270,out=$T/esc_${h}.png"
            "mode=scene,style=e,custom=aventureiro,cabelo=cabelo.$h,t=1.3,nohud=1,hx=1.0,hz=2.5,hyaw=20,yaw=18,pitch=13,dist=6.4,size=480x270,out=$T/escc_${h}.png"); done
  snap "${specs[@]}"
  for f in $T/esc_*.png $T/escc_*.png; do ffmpeg -loglevel error -y -i "$f" -vf scale=iw*3:ih*3:flags=neighbor "${f%.png}-x3.png"; done
  sheet "$D/08-escala-de-jogo-topete-crespo-coque.png" 3 $T/esc_{topete,crespo,coque}-x3.png $T/escc_{topete,crespo,coque}-x3.png
  # 9. animação: correr e golpe em 4 câmeras (vídeos), cabelo fundido à cabeça não pode atravessar o corpo
  for h in $NEW; do for c in "frente:0" "perfil:90" "costas:180" "tres-quartos:35"; do IFS=: read -r cn yaw <<<"$c"
    tools/anim.sh "$D/videos/$h-correr-$cn" "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,clip=run,yaw=$yaw,pitch=6,dist=2.4,ty=0.7,preset=dia,size=480x480,nohud=1" 48 30 > /dev/null
    tools/anim.sh "$D/videos/$h-golpe-$cn" "mode=char,style=e,custom=aventureiro,cabelo=cabelo.$h,cobertura=nenhum,clip=attack,yaw=$yaw,pitch=6,dist=2.4,ty=0.7,preset=dia,size=480x480,nohud=1" 40 30 > /dev/null
  done; done
  # 10. medidas estruturais (Jaccard de volume, caixa, voxels) — mesmo mapeamento do leitor Java
  python3 tools/hair_metrics.py "$D/medidas-cabelos.md" > /dev/null
fi

if want gate; then
  # presets idênticos ao canônico, sem sobrescrever a rodada 07
  R=$R/validacao/regressao tools/review7.sh gate | tail -1
fi

if want testes; then
  { ./gradlew test --console=plain 2>&1 | grep -E 'FAILED|tests completed|BUILD'; } > "$R/validacao/testes.txt" || true
  $V --check 2>&1 | tail -1 > "$R/validacao/viewer-check.txt"
  $V --occlusion 2>&1 | grep -E '^\S.* / ' | grep -v ' OK$' > "$R/validacao/viewer-occlusion.txt" || true
  cat "$R/validacao/testes.txt" "$R/validacao/viewer-check.txt" "$R/validacao/viewer-occlusion.txt"
fi
