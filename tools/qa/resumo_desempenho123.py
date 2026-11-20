#!/usr/bin/env python3
"""Rodada 12.3 — resumo do desempenho medido por `tools/review123.sh desempenho`: os DOIS binários (rodada 12.2 × 12.3),
intercalados, 3 repetições (Markdown na saída padrão).

Lê, da pasta dada:
- os relatórios `perf` do jogo (`vila-fixa-N.txt`, `vV-sS-N.txt`) e os logs (`.log`: geração e conferência);
- o bench do viewer (`bench-CENA-K.csv`, `.quadros.csv` por quadro, `.txt` com a GPU por passo);
- o roteiro de memória do menu (`menu-novo-N.log` e, se houver, `menu-publicado-N.log`).

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
    for f in sorted(glob.glob(os.path.join(D, prefix + "-[0-9].txt"))):
        t = open(f, encoding="utf-8").read()
        log = f[:-4] + ".log"
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



print("# Desempenho — rodada 12.2 × 12.3 (1080p, janela oculta, mudo, glFinish por quadro, os dois binários intercalados)\n")
print("Vila fixa = `tools/jogo/desempenho.txt`; torre por dentro = `tools/mundo/desempenho-torre.txt` (semente 2: duas voltas")
print("de câmera, ida à escada e volta; na 12.3 com o recorte ligado); regiões = `tools/mundo/desempenho.txt` (ida e volta pela")
print("trilha correndo). GPU por passo com `-Dvx.gpu=true`. Mediana das 3 repetições. Rodada 12.2 = clone compilado de `7261f3e`.\n")
print("| Cenário | binário | quadro mediana (ms) | p95 | p99 | lógica mediana | GPU sombra | GPU cena | GPU soma | draws | triângulos | formas de colisão | malhas (MB) | gerar + conferir, 1ª vez (ms) | coletas |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
for name, key in [("vila fixa", "vila-fixa"), ("torre por dentro (v2 s2, recorte)", "torre-dentro"), ("região v2 s44 (abóbada)", "v2-s44"), ("região v2 s28 (rural, Índira)", "v2-s28")]:
    for lado in ("r122", "r123"):
        rows = game(f"{lado}-{key}")
        if not rows:
            continue
        print(f"| {name} | {'12.2' if lado == 'r122' else '12.3'} | {fmt(agg(rows, 'med'))} | {fmt(agg(rows, 'p95'))} | {fmt(agg(rows, 'p99'))} | {fmt(agg(rows, 'logica'))} | "
              f"{fmt(agg(rows, 'gpu_sombra'))} | {fmt(agg(rows, 'gpu_cena'))} | {fmt(agg(rows, 'gpu_soma'))} | {fmt(first(rows, 'draws'), 0)} | {fmt(first(rows, 'tri'), 0)} | {fmt(first(rows, 'formas'), 0)} | "
              f"{fmt(first(rows, 'malhas'), 0)} ({fmt(first(rows, 'mb'), 1)}) | {fmt(agg(rows, 'gerar'), 0)} | {fmt(agg(rows, 'coletas'), 0)} |")

if missing:
    print("\nFaltaram (não medidos nesta execução): " + ", ".join(missing))
