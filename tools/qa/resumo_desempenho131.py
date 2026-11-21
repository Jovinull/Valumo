#!/usr/bin/env python3
"""Rodada 13.1 — resumo do desempenho medido por `tools/review131.sh desempenho`: os DOIS binários (rodada 13 × 13.1),
intercalados, 3 repetições, e os casos só da 13.1 (andar com a mochila fechada × aberta × aberta numa aba vazia; a
mochila grande parada, aberta e rolada; abrir e fechar 20 vezes; o save com 283 pilhas). Markdown na saída padrão.

Lê, da pasta dada:
- os relatórios `perf` do jogo (`LADO-vila-fixa-N-desempenho.txt`, `LADO-v2-s7-N-desempenho.txt`, `r131-andando-V-N-andando.txt`,
  `r131-mochila-N-*.txt`) e os logs (`.log`: recursos dos itens, save);
- o roteiro de memória do menu (`menu-LADO-N.log`).

Toda linha da tabela é a MEDIANA das repetições; o que é determinista (draws, triângulos, formas, malhas) sai da
primeira. Número que faltar sai como "—" (e o arquivo que faltou é listado no fim): nada é inventado.
"""
import csv
import glob
import os
import re
import statistics
import sys

D = sys.argv[1]
missing = []


def num(s):
    return float(s.replace(",", "."))


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def fmt(x, nd=2):
    if x is None:
        return "—"
    if isinstance(x, int) or (isinstance(x, float) and x.is_integer() and nd == 0):
        return f"{int(x)}"
    return f"{x:.{nd}f}"


def find(pat, text, conv=num):
    m = re.search(pat, text)
    return tuple(conv(g) for g in m.groups()) if m else None


def game(prefix):
    rows = []
    for f in sorted(glob.glob(os.path.join(D, prefix + "-[0-9]-desempenho.txt"))):   # o roteiro grava "desempenho.txt"
        t = open(f, encoding="utf-8").read()
        log = f[:-len("-desempenho.txt")] + ".log"
        lg = open(log, encoding="utf-8", errors="replace").read() if os.path.exists(log) else ""
        r = {}
        q = find(r"quadro \(ms\): n=\d+ média ([\d,]+) mediana ([\d,]+) p95 ([\d,]+) p99 ([\d,]+)", t)
        if q:
            r["med"], r["p95"], r["p99"] = q[1], q[2], q[3]
        lo = find(r"lógica:[^\n]*?mediana ([\d,]+)", t)
        r["logica"] = lo[0] if lo else None
        dr = find(r"draws (\d+), triângulos (\d+); formas de colisão (\d+); cena montada em (\d+) ms[^\n]*malhas vivas (\d+) \(([\d,]+) MB\)", t)
        if dr:
            r["draws"], r["tri"], r["formas"], r["montada"], r["malhas"], r["mb"] = dr
        sh = find(r"triângulos de sombra (\d+)", t)
        r["tri_sombra"] = sh[0] if sh else None
        g = find(r"GPU por passo \(ms/quadro, \d+ quadros\)\s+sombra ([\d.]+)\s+cena ([\d.]+)\s+bloom ([\d.]+)\s+composicao ([\d.]+)\s+\| soma ([\d.]+)", t)
        if g:
            r["gpu_sombra"], r["gpu_cena"], r["gpu_soma"] = g[0], g[1], g[4]
        c = find(r"coletas de lixo durante a medição: (\d+)", t)
        r["coletas"] = c[0] if c else None
        ge = find(r"gerado e conferido em (\d+) ms", lg)
        r["gerar"] = ge[0] if ge else None
        mg = find(r"medir-geracao: [^\n]*total mediana ([\d.]+) ms \(máx ([\d.]+)\), compor ([\d.]+) ms, conferir na colisão real ([\d.]+) ms", lg)
        if mg:
            r["quente"], r["compor"], r["conferir"] = mg[0], mg[2], mg[3]
        rows.append(r)
    if not rows:
        missing.append(prefix)
    return rows


def agg(rows, k):
    return med([r.get(k) for r in rows])


def first(rows, k):
    return next((r[k] for r in rows if r.get(k) is not None), None)



def game_file(prefix, name):
    """Relatórios perf de um roteiro com vários 'perf off ARQUIVO' (a mochila): PREFIXO-N-ARQUIVO."""
    rows = []
    for f in sorted(glob.glob(os.path.join(D, prefix + "-[0-9]-" + name))):
        t = open(f, encoding="utf-8").read()
        r = {}
        q = find(r"quadro \(ms\): n=\d+ média ([\d,]+) mediana ([\d,]+) p95 ([\d,]+) p99 ([\d,]+)", t)
        if q:
            r["med"], r["p95"], r["p99"] = q[1], q[2], q[3]
        lo = find(r"lógica:[^\n]*?mediana ([\d,]+)", t)
        r["logica"] = lo[0] if lo else None
        de = find(r"desenho:[^\n]*?mediana ([\d,]+)", t)
        r["desenho"] = de[0] if de else None
        mem = find(r"memória Java em uso (\d+) MB", t)
        r["heap"] = mem[0] if mem else None
        c = find(r"coletas de lixo durante a medição: (\d+)", t)
        r["coletas"] = c[0] if c else None
        rows.append(r)
    if not rows:
        missing.append(prefix + "/" + name)
    return rows


LADOS = (("r13", "rodada 13"), ("r131", "13.1"))


def mn(xs):
    xs = [x for x in xs if x is not None]
    return min(xs) if xs else None


print("# Desempenho — rodada 13 × 13.1 (1080p, janela oculta, mudo, glFinish por quadro, os dois binários alternados)\n")
carga = ""
for nm in ("carga-antes.txt", "carga-depois.txt"):
    f = os.path.join(D, nm)
    if os.path.exists(f):
        carga += f"- {nm[:-4]}: " + open(f, encoding="utf-8").readline().strip() + "\n"
if carga:
    print("**Carga da máquina** (a medida divide a GPU com o que estiver aberto; os tempos absolutos variam entre execuções):\n")
    print(carga)
print("Vila fixa = `tools/jogo/desempenho.txt` (3 a 6 vezes por binário); região v2 s7 = `tools/itens/desempenho-fechado.txt` (a")
print("mochila FECHADA, ida e volta pela trilha correndo; 5 vezes por binário); GPU por passo com `-Dvx.gpu=true`. A coluna")
print("\"melhor\" é a menor mediana entre as repetições (a de menos interferência). Rodada 13 = clone do commit `26806fa`.")
print("Com outro programa disputando a GPU, a mediana de cada execução oscila muito (a mesma vila fixa da rodada 13 deu de 9 a")
print("49 ms); compare pela coluna \"melhor\", pelo que é determinista (draws, triângulos, malhas, lógica) e pelas medidas em")
print("PARES abaixo.\n")
print("| Cenário | binário | quadro mediana (ms) | melhor | p95 | lógica mediana | GPU cena (melhor) | draws | triângulos | malhas (MB) | coletas |")
print("|---|---|---|---|---|---|---|---|---|---|---|")
for name, key in [("vila fixa", "vila-fixa"), ("região v2 s7 (mochila fechada)", "v2-s7")]:
    for lado, rot in LADOS:
        rows = game(f"{lado}-{key}")
        if not rows:
            continue
        print(f"| {name} | {rot} | {fmt(agg(rows, 'med'))} | {fmt(mn([r.get('med') for r in rows]))} | {fmt(agg(rows, 'p95'))} | "
              f"{fmt(agg(rows, 'logica'))} | {fmt(mn([r.get('gpu_cena') for r in rows]))} | {fmt(first(rows, 'draws'), 0)} | "
              f"{fmt(first(rows, 'tri'), 0)} | {fmt(first(rows, 'malhas'), 0)} ({fmt(first(rows, 'mb'), 1)}) | {fmt(agg(rows, 'coletas'), 0)} |")

print("\n## Menu: tempo até o menu, entrar no mundo e memória (`tools/menu/14-memoria.txt`, 1 vez por binário)\n")
for lado, rot in LADOS:
    logs = sorted(glob.glob(os.path.join(D, f"menu-{lado}-[0-9].log")))
    if not logs:
        missing.append(f"menu-{lado}")
        continue
    first_frame, res, trans = [], {}, {}
    for f in logs:
        t = open(f, encoding="utf-8", errors="replace").read()
        m = find(r"menu: primeiro quadro (\d+) ms", t)
        if m:
            first_frame.append(m[0])
        for nm, n, mb in re.findall(r"menu-recursos (\S+): malhas (\d+) \(([\d.]+) MB\)", t):
            res.setdefault(nm, []).append((int(n), float(mb)))
        for lab, ms in re.findall(r"transição: (.+?) em (\d+) ms", t):
            lab = re.sub(r" de \S+$| \S+ \(\d+\)$", "", lab)
            trans.setdefault(lab, []).append(int(ms))
    print(f"**{rot}**: primeiro quadro do menu {fmt(med(first_frame), 0)} ms; "
          + "; ".join(f"{k} {fmt(med([v[0] for v in vs]), 0)} malhas ({fmt(med([v[1] for v in vs]), 1)} MB)" for k, vs in res.items())
          + "; " + "; ".join(f"{k}: {fmt(med(v), 0)} ms" for k, v in trans.items() if k.startswith(("Gerando", "Montando", "Carregando", "Preparando"))) + "\n")

HDR = "| Situação | quadro mediana (ms) | melhor | p95 | lógica mediana | desenho mediana | memória Java (MB) | coletas |\n|---|---|---|---|---|---|---|---|"


def line(label, rows):
    print(f"| {label} | {fmt(agg(rows, 'med'))} | {fmt(mn([r.get('med') for r in rows]))} | {fmt(agg(rows, 'p95'))} | {fmt(agg(rows, 'logica'))} | "
          f"{fmt(agg(rows, 'desenho'))} | {fmt(agg(rows, 'heap'), 0)} | {fmt(agg(rows, 'coletas'), 0)} |")


print("## A mochila aberta × fechada em PARES (só a 13.1; mesmo processo, parado, 3 s cada, 6 pares × 2 execuções)\n")
print("Cada par mede fechada e logo depois aberta (com a prévia girando) no mesmo lugar; a diferença do par cancela o que")
print("muda devagar na máquina. É o custo da mochila aberta sobre o jogo correndo.\n")
diffs, fech, aber = [], [], []
for f in sorted(glob.glob(os.path.join(D, "r131-pares-[0-9]-par[0-9]-fechada.txt"))):
    g = f.replace("-fechada.txt", "-aberta.txt")
    if not os.path.exists(g):
        continue
    qa = find(r"quadro \(ms\): n=\d+ média ([\d,]+) mediana ([\d,]+)", open(f, encoding="utf-8").read())
    qb = find(r"quadro \(ms\): n=\d+ média ([\d,]+) mediana ([\d,]+)", open(g, encoding="utf-8").read())
    if qa and qb:
        fech.append(qa[1]); aber.append(qb[1]); diffs.append(qb[1] - qa[1])
if diffs:
    print("| | fechada | aberta | aberta − fechada |\n|---|---|---|---|")
    print(f"| mediana dos {len(diffs)} pares (ms) | {fmt(med(fech))} | {fmt(med(aber))} | **{fmt(med(diffs))}** |")
    print(f"| menor e maior diferença (ms) | | | {fmt(min(diffs))} … {fmt(max(diffs))} |")
else:
    missing.append("r131-pares")
lotes = []
for f in sorted(glob.glob(os.path.join(D, "r131-pares-[0-9].log"))):
    lotes += re.findall(r"interface: [^\n]*", open(f, encoding="utf-8", errors="replace").read())
if lotes:
    print("\nDescargas do lote 2D (chamadas de desenho da interface) num quadro: " + "; ".join(sorted(set(lotes))) + ".")
    print("Antes de desenhar a mochila em camadas eram 88 com a mesma mochila (35 itens à vista), com a mesma imagem.\n")

print("## Andar com a mochila (só a 13.1; região v2 s7, 7 s de W em linha reta, processos separados, mesmo caminho, 2 vezes)\n")
print(HDR)
for label, v in [("fechada", "fechada"), ("aberta numa aba vazia (sem prévia)", "aba-vazia"), ("aberta, com a prévia girando", "aberta")]:
    rows = game_file(f"r131-andando-{v}", "andando.txt")
    if rows:
        line(label, rows)

print("\n## A mochila grande (só a 13.1; 283 pilhas; parada no começo da região, mesmo lugar e mesmo tempo)\n")
print(HDR)
for label, name in [("fechada", "parada-fechada.txt"), ("aberta, com a prévia girando", "parada-aberta.txt"),
                    ("aberta e rolada até o fim", "parada-aberta-rolada.txt")]:
    rows = game_file("r131-mochila", name)
    if rows:
        line(label, rows)
extra = []
for f in sorted(glob.glob(os.path.join(D, "r131-mochila-[0-9].log"))):
    t = open(f, encoding="utf-8", errors="replace").read()
    extra += ["- " + x for x in re.findall(r"itens: [^\n]*|mochila: aberta e fechada[^\n]*|medir-save:[^\n]*", t)]
if extra:
    print("\nRecursos dos itens (FBOs e malhas vivos antes e depois de abrir e fechar 20 vezes), o atlas e o save com 283 pilhas:\n")
    print("\n".join(extra))

if missing:
    print("\nFaltaram (não medidos nesta execução): " + ", ".join(missing))
