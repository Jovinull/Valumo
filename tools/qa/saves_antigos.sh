#!/bin/bash
# Saves antigos × binário novo (integração dos lotes de conteúdo; vale para qualquer mudança que possa quebrar save).
#
# O binário ANTIGO cria os saves de verdade, com entrada injetada:
#   v1-ativa         vila (save v1), missão aceita
#   v1-concluida     vila (save v1), missão concluída e entregue
#   v2-ativa-marco   região da semente 1024 (save v2), trilha limpa e marco aceso (POI mudou de estado)
#   v2-concluida     região da semente 1024 (save v2), missão concluída
#   menu             rodada 11.1: o save v1 concluído adotado como "Aventureiro", o personagem "Brisa", os mundos
#                    "Vale das Brumas" e "Gemeo" (1024) e as aventuras dos roteiros 02, 03 e 04 de tools/menu
# Depois, OS DOIS binários continuam de cópias idênticas de cada pasta. As linhas missao/regiao/menu-estado e a posição
# do herói têm de sair iguais, sem nenhum .invalido e sem exceção.
#
# Uso: tools/qa/saves_antigos.sh RAIZ_ANTIGA RAIZ_NOVA SAIDA_TEMPORARIA
#   RAIZ = um checkout com game/build/install/game e os assets gerados (o binário roda de dentro dela).
#   Nunca usa output/saves, output/personagens nem output/mundos: tudo mora em SAIDA.
set -u
[ $# -eq 3 ] || { sed -n 2,20p "$0"; exit 2; }
A=$(realpath "$1"); N=$(realpath "$2"); OUT=$(realpath -m "$3")
case "$OUT" in */tmp/*|*/output/tmp/*) ;; *) echo "SAIDA precisa ficar numa pasta temporária (…/tmp/…)"; exit 2;; esac
ROT=$(realpath "$(dirname "$0")/saves"); MENU=$(realpath "$(dirname "$0")/../menu")
rm -rf "$OUT"; mkdir -p "$OUT/dados" "$OUT/logs"

jogo() {   # raiz, opções, roteiro, rótulo
    (cd "$1" && JAVA_OPTS="-Dvx.som=mudo $2" timeout 900 game/build/install/game/bin/game --roteiro "$3" "$OUT/quadros/$4" 30 \
        > "$OUT/logs/$4.log" 2>&1)
    local s=$?; echo "  $4: saída $s"; return $s
}
proc="-Dvx.mundo=procedural -Dvx.semente=1024"
menu() { echo "-Dvx.menu=sim -Dvx.personagens=$1/p -Dvx.mundos=$1/m -Dvx.savegame=$1/s -Dvx.saves=$1/a"; }

echo "1. o binário antigo cria os saves"
for c in v1-ativa v1-concluida; do mkdir -p "$OUT/dados/$c"; jogo "$A" "-Dvx.savegame=$OUT/dados/$c" "$ROT/$c.txt" "criar-$c"; done
for c in v2-ativa-marco v2-concluida; do mkdir -p "$OUT/dados/$c"; jogo "$A" "$proc -Dvx.savegame=$OUT/dados/$c" "$ROT/$c.txt" "criar-$c"; done
M="$OUT/dados/menu"; mkdir -p "$M/s" "$M/a"; cp "$OUT/dados/v1-concluida/"*.json "$M/s/" 2>/dev/null
jogo "$A" "$(menu "$M")" "$MENU/02-personagens.txt" "criar-menu-02-personagens"
jogo "$A" "$(menu "$M") -Dvx.qa.recusar=4242" "$MENU/03-mundos.txt" "criar-menu-03-mundos"   # a semente recusada do roteiro
jogo "$A" "$(menu "$M")" "$MENU/04-aventura.txt" "criar-menu-04-aventura"

echo "2. os dois binários continuam de cópias idênticas"
for b in antigo novo; do
    raiz=$A; [ $b = novo ] && raiz=$N
    mkdir -p "$OUT/$b"; cp -a "$OUT/dados/." "$OUT/$b/"
    jogo "$raiz" "-Dvx.savegame=$OUT/$b/v1-ativa" "$ROT/continuar-vila.txt" "$b-v1-ativa"
    jogo "$raiz" "-Dvx.savegame=$OUT/$b/v1-concluida" "$ROT/continuar-vila.txt" "$b-v1-concluida"
    jogo "$raiz" "$proc -Dvx.savegame=$OUT/$b/v2-ativa-marco" "$ROT/continuar-regiao.txt" "$b-v2-ativa-marco"
    jogo "$raiz" "$proc -Dvx.savegame=$OUT/$b/v2-concluida" "$ROT/continuar-regiao.txt" "$b-v2-concluida"
    for m in m1-mesmo-personagem-vila m2-mesmo-personagem-regiao m3-outro-personagem-regiao m4-outro-personagem-vila; do
        jogo "$raiz" "$(menu "$OUT/$b/menu")" "$ROT/$m.txt" "$b-$m"
    done
done

echo "3. comparação"
falhas=0
linhas() { grep -E "^(missao|regiao|menu-estado):|^estado:" "$1" | sed -E 's/ vel .*//' ; }
for f in "$OUT"/logs/antigo-*.log; do
    c=${f##*/antigo-}; c=${c%.log}
    if diff <(linhas "$f") <(linhas "$OUT/logs/novo-$c.log") > "$OUT/logs/diff-$c.txt"; then
        n=$(linhas "$f" | wc -l); [ "$n" -ge 2 ] && echo "  igual   $c ($n linhas)" || { echo "  VAZIO   $c"; falhas=$((falhas+1)); }
    else echo "  DIFERE  $c (logs/diff-$c.txt)"; falhas=$((falhas+1)); fi
done
inv=$(find "$OUT" -name "*.invalido" | wc -l); echo "  arquivos .invalido: $inv"; [ "$inv" -eq 0 ] || falhas=$((falhas+1))
exc=$(grep -l "Exception\|erro:" "$OUT"/logs/*.log | wc -l); echo "  logs com exceção ou erro de save: $exc"; [ "$exc" -eq 0 ] || falhas=$((falhas+1))
echo "falhas: $falhas"; [ $falhas -eq 0 ]
