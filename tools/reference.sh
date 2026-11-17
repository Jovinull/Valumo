#!/usr/bin/env bash
# Rodadas 05/05.1 — gera as REFERÊNCIAS CANÔNICAS da direção E em docs/art/reference/ (capturas reais do viewer).
# Uso: tools/reference.sh [pasta-de-saída]   (padrão docs/art/reference; o experimento de reprodução usa outra)
# Cada linha de SPECS = nome do arquivo | spec do snap. O manifesto (manifesto.tsv) registra spec, hash do
# perfil/rig e a revisão de origem; docs/art/reference/README.md explica cada imagem.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${1:-docs/art/reference}
mkdir -p "$OUT"
COMMON="nohud=1,ssaa=2"
SPECS=(
"01-proporcoes-herois-frente.png|mode=lineup,style=e,set=herois,myaw=0,pitch=4,preset=neutra,size=1500x640"
"02-personagens-isometrica-dia.png|mode=lineup,style=e,set=herois,myaw=35,pitch=20,size=1500x640"
"03-personagens-costas.png|mode=lineup,style=e,set=herois,myaw=180,pitch=6,preset=neutra,size=1500x640"
"04-personagens-perfil.png|mode=lineup,style=e,set=herois,myaw=90,pitch=6,preset=neutra,size=1500x640"
"05-silhuetas-frente.png|mode=lineup,style=e,set=herois,myaw=0,pitch=4,sil=1,size=1500x640"
"06-rosto-aventureiro-alegre.png|mode=char,style=e,char=0,yaw=12,pitch=4,dist=1.3,ty=0.93,expr=joy,size=640x640"
"07-rosto-exploradora-determinada.png|mode=char,style=e,char=1,yaw=12,pitch=4,dist=1.3,ty=0.93,expr=determined,size=640x640"
"08-rosto-maga-neutra.png|mode=char,style=e,char=2,yaw=12,pitch=4,dist=1.45,ty=0.98,size=640x640"
"09-equipamentos-em-repouso.png|mode=lineup,style=e,set=r4-armas,myaw=30,pitch=10,preset=neutra,size=2000x640"
"10-espada-corte-horizontal-impacto.png|mode=char,style=e,char=0,act=slash_h,hold=5,t=0.40,yaw=35,pitch=18,size=800x800"
"11-arco-puxado-perfil.png|mode=char,style=e,char=1,act=bow_shot,hold=5,t=1.10,yaw=-70,pitch=8,size=800x800"
"12-cajado-concentracao.png|mode=char,style=e,char=2,act=staff_cast,hold=5,t=0.86,yaw=60,pitch=8,size=800x800"
"13-criaturas-escala.png|mode=lineup,style=e,set=criaturas,myaw=30,pitch=12,size=2000x700"
"14-floresta-carvalho-o3.png|mode=scene,style=e,hx=11.0,hz=-1.0,hyaw=90,yaw=-90,pitch=18,dist=8,t=1.3,size=1600x900"
"15-arquitetura-casa-frente.png|mode=scene,style=e,hx=-5.4,hz=-3.0,hyaw=200,yaw=15,pitch=12,dist=7,t=1.3,size=1600x900"
"16-arquitetura-casa-fundos.png|mode=scene,style=e,hx=-6.5,hz=-9.5,hyaw=0,yaw=150,pitch=20,dist=7.5,t=1.3,size=1600x900"
"17-cenario-exploracao.png|mode=scene,style=e,t=1.3,yaw=28,pitch=17,dist=7.5,size=1920x1080"
"18-cenario-panorama-terreno.png|mode=scene,style=e,yaw=-40,pitch=28,dist=26,t=1.3,size=1920x1080"
"19-luz-dia.png|mode=scene,style=e,t=1.3,yaw=28,pitch=17,dist=7.5,preset=dia,size=1280x720"
"20-luz-tarde.png|mode=scene,style=e,t=1.3,yaw=28,pitch=17,dist=7.5,preset=tarde,size=1280x720"
"21-luz-neutra.png|mode=scene,style=e,t=1.3,yaw=28,pitch=17,dist=7.5,preset=neutra,size=1280x720"
"22-equipe-camera-de-jogo.png|mode=scene,style=e,hx=-0.6,hz=3.6,hyaw=20,yaw=18,pitch=13,dist=6.4,party=1,t=1.3,size=1920x1080"
)
# metadados por imagem: asset | fonte | perfil de animação | testes (rodada 05.1)
declare -A ASSET SRC PROF TESTS STATUS
ASSET[01]="protagonistas (3)"; SRC[01]="tools/chars_e.py tools/charlib_e.py"; PROF[01]="—"; TESTS[01]="AssetContractTest"; STATUS[01]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[02]="protagonistas (3)"; SRC[02]="tools/chars_e.py tools/charlib_e.py"; PROF[02]="—"; TESTS[02]="AssetContractTest"; STATUS[02]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[03]="protagonistas (3)"; SRC[03]="tools/chars_e.py tools/charlib_e.py"; PROF[03]="—"; TESTS[03]="AssetContractTest"; STATUS[03]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[04]="protagonistas (3)"; SRC[04]="tools/chars_e.py tools/charlib_e.py"; PROF[04]="—"; TESTS[04]="AssetContractTest"; STATUS[04]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[05]="protagonistas (3)"; SRC[05]="tools/chars_e.py tools/charlib_e.py"; PROF[05]="—"; TESTS[05]="AssetContractTest"; STATUS[05]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[06]="aventureiro"; SRC[06]="tools/chars_e.py tools/charlib_e.py"; PROF[06]="—"; TESTS[06]="AssetContractTest"; STATUS[06]="candidata à aprovação final — expressões refeitas na rodada 06 (atuação por personagem)"
ASSET[07]="exploradora"; SRC[07]="tools/chars_e.py tools/charlib_e.py"; PROF[07]="—"; TESTS[07]="AssetContractTest"; STATUS[07]="candidata à aprovação final — expressões refeitas na rodada 06 (atuação por personagem)"
ASSET[08]="maga"; SRC[08]="tools/chars_e.py tools/charlib_e.py"; PROF[08]="—"; TESTS[08]="AssetContractTest"; STATUS[08]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[09]="kits (espada, broquel, lâmina longa, arco, cajado)"; SRC[09]="tools/chars_e.py tools/chars_e_study.py tools/charlib_e.py"; PROF[09]="assets/anim/sword.json assets/anim/sword_shield.json assets/anim/longblade.json assets/anim/bow.json assets/anim/staff.json"; TESTS[09]="QualityTest AnimationQaTest"; STATUS[09]="candidata à aprovação final — alterada na 05.1"
ASSET[10]="aventureiro + espada"; SRC[10]="tools/chars_e.py tools/charlib_e.py"; PROF[10]="assets/anim/sword.json"; TESTS[10]="ChoreoTest viewer--check"; STATUS[10]="candidata à aprovação final — expressões refeitas na rodada 06 (atuação por personagem)"
ASSET[11]="exploradora + arco"; SRC[11]="tools/chars_e.py tools/charlib_e.py"; PROF[11]="assets/anim/bow.json"; TESTS[11]="ChoreoTest viewer--check"; STATUS[11]="candidata à aprovação final — expressões refeitas na rodada 06 (atuação por personagem)"
ASSET[12]="maga + cajado"; SRC[12]="tools/chars_e.py tools/charlib_e.py"; PROF[12]="assets/anim/staff.json"; TESTS[12]="ChoreoTest viewer--check viewer--occlusion"; STATUS[12]="candidata à aprovação final — expressões refeitas na rodada 06 (atuação por personagem)"
ASSET[13]="criaturas (4) + aventureiro"; SRC[13]="tools/creatures_e.py tools/chars_e.py"; PROF[13]="—"; TESTS[13]="AssetContractTest"; STATUS[13]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[14]="carvalhos O3, cena"; SRC[14]="tools/env_e.py assets/scenes/clearing_e.json"; PROF[14]="—"; TESTS[14]="AssetContractTest"; STATUS[14]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[15]="casa (frente)"; SRC[15]="tools/env_e.py tools/env_b.py"; PROF[15]="—"; TESTS[15]="—"; STATUS[15]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[16]="casa (fundos)"; SRC[16]="tools/env_e.py tools/env_b.py"; PROF[16]="—"; TESTS[16]="—"; STATUS[16]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[17]="cena de exploração"; SRC[17]="tools/env_e.py assets/scenes/clearing_e.json viewer/src/main/java/vx/viewer/World.java"; PROF[17]="—"; TESTS[17]="—"; STATUS[17]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[18]="relevo T1 + distância"; SRC[18]="viewer/src/main/java/vx/viewer/World.java viewer/src/main/java/vx/viewer/StyleProfile.java"; PROF[18]="—"; TESTS[18]="—"; STATUS[18]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[19]="preset dia"; SRC[19]="viewer/src/main/java/vx/viewer/StyleProfile.java"; PROF[19]="—"; TESTS[19]="—"; STATUS[19]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[20]="preset tarde"; SRC[20]="viewer/src/main/java/vx/viewer/StyleProfile.java"; PROF[20]="—"; TESTS[20]="—"; STATUS[20]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[21]="preset neutra"; SRC[21]="viewer/src/main/java/vx/viewer/StyleProfile.java"; PROF[21]="—"; TESTS[21]="—"; STATUS[21]="candidata à aprovação final — sem mudança visível desde a 05"
ASSET[22]="equipe na câmera de jogo"; SRC[22]="tools/chars_e.py assets/scenes/clearing_e.json"; PROF[22]="assets/anim/sword.json assets/anim/bow.json assets/anim/staff.json"; TESTS[22]="—"; STATUS[22]="candidata à aprovação final — sem mudança visível desde a 05"
specs=(); rev=$(git rev-parse --short HEAD 2>/dev/null || echo "sem-git")
dirty=$(git status --porcelain 2>/dev/null | grep -q . && echo "+alterações-não-commitadas" || echo "")
: > "$OUT/manifesto.tsv"
printf "arquivo\tasset\tfonte\tperfil_animacao\tspec_captura\tdependencias_sha256\ttestes\trevisao_git\tdata\tsituacao\n" >> "$OUT/manifesto.tsv"
for row in "${SPECS[@]}"; do
  f=${row%%|*}; s=${row#*|}; k=${f:0:2}
  specs+=("$s,$COMMON,out=$OUT/$f")
  deps="${SRC[$k]} ${PROF[$k]//—/}"
  h=$(cat $deps 2>/dev/null | sha256sum | cut -c1-12)
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$f" "${ASSET[$k]}" "${SRC[$k]}" "${PROF[$k]}" "$s,$COMMON" "$h" "${TESTS[$k]}" "$rev$dirty" "$(date +%F)" "${STATUS[$k]}" >> "$OUT/manifesto.tsv"
done
tools/snap.sh "${specs[@]}" > /dev/null
echo "reference: ${#SPECS[@]} imagens em $OUT"
