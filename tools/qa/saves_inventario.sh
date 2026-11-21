#!/bin/bash
# Publicação da v0.7 — matriz de saves com a MOCHILA, com os binários de verdade. Complementa tools/qa/saves_antigos.sh
# (os saves da vila, da região v2 e das aventuras do menu feitos por um binário antigo).
#
#   c02 v06-regiao-v1    v0.6 cria uma região do gerador v1 (semente 1024), missão ativa e marco aceso
#   c03 v06-regiao-v2    v0.6 cria uma região do gerador v2 (semente 1024), idem
#   c04 r13-cheia        rodada 13: presente da Índira + caderno da casa + a mochila completada até os 24 espaços
#   c05 r13-buracos      rodada 13: presente + caderno + madeira; as duas cataplasmas aplicadas (buraco no espaço 1)
#   c06 v07-itens        13.1: presente + caderno + uma cataplasma aplicada + dezenas de pilhas
#   c07 v07-vazia        13.1: sem item nenhum (o save não ganha os campos novos)
#   c10 item-desconhecido, c11 quantidade-invalida, c12 formato-futuro: cópias do c06 alteradas à mão
#
# Os bons: a 13.1 abre pelo Continuar e salva de novo sem mexer em nada; o resumo compara o arquivo de antes com o
# regravado (moedas, missões, descobertas, objetos, fontes, mochila na mesma ordem, aparência, mundo, vínculos, posição).
# Os recusados: C não continua, o arquivo fica igual (sha256) e só o inválido ganha .invalido.
#
# Uso: tools/qa/saves_inventario.sh RAIZ_V06 RAIZ_R13 RAIZ_NOVA SAIDA_TEMPORARIA
#   Nunca usa output/saves, output/personagens nem output/mundos: tudo mora em SAIDA.
set -u
[ $# -eq 4 ] || { sed -n 2,22p "$0"; exit 2; }
V06=$(realpath "$1"); R13=$(realpath "$2"); N=$(realpath "$3"); OUT=$(realpath -m "$4")
case "$OUT" in */tmp/*|*/output/tmp/*) ;; *) echo "SAIDA precisa ficar numa pasta temporária (…/tmp/…)"; exit 2;; esac
ROT=$(realpath "$(dirname "$0")/saves"); INV="$ROT/inventario"
rm -rf "$OUT"; mkdir -p "$OUT/dados" "$OUT/logs"

jogo() {   # raiz, opções, roteiro, rótulo
    (cd "$1" && JAVA_OPTS="-Dvx.som=mudo -Dvx.prefs=- $2" timeout 900 game/build/install/game/bin/game --roteiro "$3" "$OUT/quadros/$4" 30 \
        > "$OUT/logs/$4.log" 2>&1)
    local s=$?; echo "  $4: saída $s"; return 0
}
reg() { echo "-Dvx.mundo=procedural -Dvx.gerador=$1 -Dvx.semente=$2"; }

echo "1. os binários criam os saves"
mkdir -p "$OUT/dados"/{c02,c03,c04,c05,c06,c07}
jogo "$V06" "$(reg 1 1024) -Dvx.savegame=$OUT/dados/c02" "$ROT/v2-ativa-marco.txt" "criar-c02-v06-regiao-v1"
jogo "$V06" "$(reg 2 1024) -Dvx.savegame=$OUT/dados/c03" "$ROT/v2-ativa-marco.txt" "criar-c03-v06-regiao-v2"
jogo "$R13" "$(reg 2 7) -Dvx.savegame=$OUT/dados/c04" "$INV/r13-cheia.txt" "criar-c04-r13-cheia"
jogo "$R13" "$(reg 2 7) -Dvx.savegame=$OUT/dados/c05" "$INV/r13-buracos.txt" "criar-c05-r13-buracos"
jogo "$N" "$(reg 2 7) -Dvx.savegame=$OUT/dados/c06" "$INV/v07-itens.txt" "criar-c06-v07-itens"
jogo "$N" "$(reg 2 7) -Dvx.savegame=$OUT/dados/c07" "$INV/v07-vazia.txt" "criar-c07-v07-vazia"
for c in c10 c11 c12; do cp -r "$OUT/dados/c06" "$OUT/dados/$c"; done
python3 - "$OUT/dados" <<'PY'
import json, sys, glob, os
d = sys.argv[1]
def edit(case, fn):
    f = glob.glob(os.path.join(d, case, "*", "aventura.json"))[0]
    o = json.load(open(f, encoding="utf-8")); fn(o)
    json.dump(o, open(f, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
edit("c10", lambda o: o["progresso"]["inventario"].append({"espaco": len(o["progresso"]["inventario"]), "item": "item.espada_de_cristal", "qtd": 1}))
edit("c11", lambda o: o["progresso"]["inventario"][0].update(qtd=999))
edit("c12", lambda o: o.update(versao=3))
PY
for c in c02 c03 c04 c05 c06 c07 c10 c11 c12; do (cd "$OUT/dados/$c" && find . -name aventura.json -exec cp {} {}.antes \;); done
find "$OUT/dados" -name "aventura.json" | sort | xargs sha256sum > "$OUT/sha-antes.txt"

echo "2. a 13.1 abre"
jogo "$N" "$(reg 1 1024) -Dvx.savegame=$OUT/dados/c02" "$INV/continuar-e-salvar.txt" "abrir-c02"
jogo "$N" "$(reg 2 1024) -Dvx.savegame=$OUT/dados/c03" "$INV/continuar-e-salvar.txt" "abrir-c03"
for c in c04 c05 c06 c07; do jogo "$N" "$(reg 2 7) -Dvx.savegame=$OUT/dados/$c" "$INV/continuar-e-salvar.txt" "abrir-$c"; done
for c in c10 c11 c12; do jogo "$N" "$(reg 2 7) -Dvx.savegame=$OUT/dados/$c" "$INV/recusado.txt" "abrir-$c"; done
find "$OUT/dados" -name "aventura.json" | sort | xargs sha256sum > "$OUT/sha-depois.txt"

echo "3. comparação"
python3 - "$OUT" <<'PY'
import json, sys, glob, os, re
out = sys.argv[1]; d = os.path.join(out, "dados"); falhas = 0
antes = dict(l.split()[::-1] for l in open(os.path.join(out, "sha-antes.txt")))
depois = dict(l.split()[::-1] for l in open(os.path.join(out, "sha-depois.txt")))
def log(n): p = os.path.join(out, "logs", n + ".log"); return open(p, encoding="utf-8", errors="replace").read() if os.path.exists(p) else ""
def pilhas(o): return [(s["item"], s["qtd"]) for s in sorted(o["progresso"].get("inventario", []), key=lambda s: s["espaco"])]
nomes = {"c02": "v0.6, região v1", "c03": "v0.6, região v2", "c04": "rodada 13, 24 espaços", "c05": "rodada 13, buracos + consumível usado",
         "c06": "13.1, itens + fontes + consumível + dezenas de pilhas", "c07": "13.1, mochila vazia"}
for c, nome in nomes.items():
    f = glob.glob(os.path.join(d, c, "*", "aventura.json"))
    if not f: print(f"  FALHA  {c} {nome}: save não criado"); falhas += 1; continue
    f = f[0]; a = json.load(open(f + ".antes", encoding="utf-8")); b = json.load(open(f, encoding="utf-8"))
    erros = []
    if "exception" in log("abrir-" + c).lower() or "Exception" in log("abrir-" + c): erros.append("exceção no log")
    if not re.search(r"estado: EXPLORE", log("abrir-" + c)): erros.append("não continuou")
    for k in ("formato", "versao", "cena", "mundo", "vinculos", "aparencia", "jogador"):
        if a.get(k) != b.get(k): erros.append(k)
    for k in set(a["progresso"]) | set(b["progresso"]):
        if k == "inventario": continue
        if a["progresso"].get(k) != b["progresso"].get(k): erros.append("progresso." + k)
    if pilhas(a) != pilhas(b): erros.append("mochila")
    if not a["progresso"].get("inventario") and ("inventario" in b["progresso"] or "fontesColetadas" in b["progresso"]): erros.append("campos novos num save sem item")
    if b.get("tempoDeJogo", 0) < a.get("tempoDeJogo", 0): erros.append("tempoDeJogo")
    inv = pilhas(b); fontes = b["progresso"].get("fontesColetadas", [])
    resumo = f"{len(inv)} pilhas, {sum(q for _, q in inv)} unidades, fontes {fontes}, moedas {b['progresso'].get('moedas')}, descobertas {len(b['progresso'].get('descobertas', []))}"
    print(f"  {'igual ' if not erros else 'FALHA '} {c} {nome}: {resumo}" + (f" — difere: {erros}" if erros else ""))
    falhas += bool(erros)
    if c == "c05":
        esp = [s["espaco"] for s in a["progresso"]["inventario"]]
        print(f"         espaços no arquivo da 13: {esp} → regravado compactado: {[s['espaco'] for s in b['progresso']['inventario']]}")
for c, nome, invalido in (("c10", "item desconhecido", False), ("c11", "quantidade inválida", True), ("c12", "formato futuro", False)):
    f = glob.glob(os.path.join(d, c, "*", "aventura.json"))[0]
    lg = log("abrir-" + c); erros = []
    if antes.get(f) != depois.get(f): erros.append("arquivo mudou")
    if not re.search(r"estado: SELECT", lg): erros.append("continuou")
    tem = bool(glob.glob(os.path.join(d, c, "*", "*.invalido*")))
    if tem != invalido: erros.append(f".invalido {'ausente' if invalido else 'criado'}")
    print(f"  {'recusado' if not erros else 'FALHA   '} {c} {nome}: arquivo igual, ficou na escolha, .invalido {'sim' if tem else 'não'}" + (f" — {erros}" if erros else ""))
    falhas += bool(erros)
print(f"falhas: {falhas}")
PY
