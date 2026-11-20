#!/usr/bin/env python3
"""Rodada 12.2 — resumo do desempenho medido por `tools/review122.sh desempenho`: os DOIS binários (rodada 12.1 × 12.2),
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



print("# Desempenho — rodada 12.1 × 12.2 (1080p, janela oculta, mudo, glFinish por quadro, os dois binários intercalados)\n")
print("Vila fixa = `tools/jogo/desempenho.txt`; regiões = `tools/mundo/desempenho.txt` (ida e volta pela trilha correndo, giro")
print("de câmera no posto). GPU por passo com `-Dvx.gpu=true`. Mediana das 3 repetições. Rodada 12.1 = clone compilado de `8f2c3eb`.\n")
print("| Cenário | binário | quadro mediana (ms) | p95 | p99 | lógica mediana | GPU sombra | GPU soma | draws | triângulos | formas de colisão | malhas (MB) | cena montada (ms) | gerar + conferir, 1ª vez (ms) | quente: total / conferir (ms) | coletas |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
for name, key in [("vila fixa", "vila-fixa"), ("região v1 s1024", "v1-s1024"), ("região v2 s1024", "v2-s1024"), ("região v2 s54 (caminho média, abóbada, 3 moradores)", "v2-s54"), ("região v2 s28 (rural, 3 moradores)", "v2-s28")]:
    for lado in ("r121", "r122"):
        rows = game(f"{lado}-{key}")
        if not rows:
            continue
        print(f"| {name} | {'12.1' if lado == 'r121' else '12.2'} | {fmt(agg(rows, 'med'))} | {fmt(agg(rows, 'p95'))} | {fmt(agg(rows, 'p99'))} | {fmt(agg(rows, 'logica'))} | "
              f"{fmt(agg(rows, 'gpu_sombra'))} | {fmt(agg(rows, 'gpu_soma'))} | {fmt(first(rows, 'draws'), 0)} | {fmt(first(rows, 'tri'), 0)} | {fmt(first(rows, 'formas'), 0)} | "
              f"{fmt(first(rows, 'malhas'), 0)} ({fmt(first(rows, 'mb'), 1)}) | {fmt(agg(rows, 'montada'), 0)} | {fmt(agg(rows, 'gerar'), 0)} | "
              f"{fmt(agg(rows, 'quente'), 1)} / {fmt(agg(rows, 'conferir'), 1)} | {fmt(agg(rows, 'coletas'), 0)} |")

print("\n## Menu e memória (`tools/menu/14-memoria.txt`; o mundo novo da semente 1024 nasce v2 nos dois binários)\n")
for lado in ("r121", "r122"):
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
    print(f"**{'rodada 12.1' if lado == 'r121' else 'rodada 12.2'}** ({len(logs)} repetições): primeiro quadro do menu {fmt(med(first_frame), 0)} ms; "
          + "; ".join(f"{k} {fmt(med([v[0] for v in vs]), 0)} malhas ({fmt(med([v[1] for v in vs]), 1)} MB)" for k, vs in res.items() if 'regiao' in k)
          + "; " + "; ".join(f"{k}: {fmt(med(v), 0)} ms" for k, v in trans.items() if k.startswith(("Gerando", "Montando"))) + "\n")

if missing:
    print("\nFaltaram (não medidos nesta execução): " + ", ".join(missing))
