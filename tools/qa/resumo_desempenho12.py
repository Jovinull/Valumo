#!/usr/bin/env python3
"""Rodada 12 — resumo do desempenho medido por `tools/review12.sh desempenho` (Markdown na saída padrão).

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


print("# Desempenho — rodada 12 (1080p, janela oculta, mudo, glFinish por quadro, MESMO binário e sessão, intercalado)\n")
print("Vila fixa = `tools/jogo/desempenho.txt`; regiões = `tools/mundo/desempenho.txt` (ida e volta pela trilha correndo,")
print("giro de câmera no posto). GPU por passo com `-Dvx.gpu=true` (consultas `GL_TIME_ELAPSED`). Mediana das repetições.\n")
print("| Cenário | rep. | quadro mediana (ms) | p95 | p99 | lógica mediana | GPU sombra | GPU cena | GPU soma | draws | triângulos cena | triângulos sombra | formas de colisão | malhas (MB) | cena montada (ms) | gerar + conferir, 1ª vez (ms) | quente: total / compor / conferir (ms) | coletas |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
for name, prefix in [("vila fixa", "vila-fixa"), ("região v1 s1024", "v1-s1024"), ("região v2 s1024 (rural)", "v2-s1024"),
                     ("região v2 s31337 (de caminho)", "v2-s31337")]:
    rows = game(prefix)
    if not rows:
        continue
    malhas = first(rows, "malhas")
    print(f"| {name} | {len(rows)} | {fmt(agg(rows, 'med'))} | {fmt(agg(rows, 'p95'))} | {fmt(agg(rows, 'p99'))} | {fmt(agg(rows, 'logica'))} | "
          f"{fmt(agg(rows, 'gpu_sombra'))} | {fmt(agg(rows, 'gpu_cena'))} | {fmt(agg(rows, 'gpu_soma'))} | {fmt(first(rows, 'draws'), 0)} | "
          f"{fmt(first(rows, 'tri'), 0)} | {fmt(first(rows, 'tri_sombra'), 0)} | {fmt(first(rows, 'formas'), 0)} | "
          f"{fmt(malhas, 0)} ({fmt(first(rows, 'mb'), 1)}) | {fmt(agg(rows, 'montada'), 0)} | {fmt(agg(rows, 'gerar'), 0)} | "
          f"{fmt(agg(rows, 'quente'), 1)} / {fmt(agg(rows, 'compor'), 1)} / {fmt(agg(rows, 'conferir'), 1)} | {fmt(agg(rows, 'coletas'), 0)} |")

print("\n## Bench do viewer (1920×1080, E, qualidade padrão, 3 × 300 quadros, duas rodadas de ordem inversa)\n")
print("| Cena | rodada | mediana (ms) | p95 | p99 | GPU sombra | GPU cena | GPU soma | draws | triângulos cena | triângulos sombra | malhas | MB de vértices |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
for cena in ["jogo_vila_e", "vitrine_conteudo_vila_a", "vitrine_conteudo_vila_b", "bench_conteudo_alta"]:
    for k in (1, 2):
        base = os.path.join(D, f"bench-{cena}-{k}")
        if not os.path.exists(base + ".csv"):
            missing.append(base + ".csv")
            continue
        runs = list(csv.DictReader(open(base + ".csv", encoding="utf-8")))
        frames = []
        if os.path.exists(base + ".quadros.csv"):
            frames = sorted(float(l.split(",")[3]) for l in open(base + ".quadros.csv") if l.strip())
        p99 = frames[min(len(frames) - 1, int(0.99 * len(frames)))] if frames else None
        txt = open(base + ".txt", encoding="utf-8", errors="replace").read() if os.path.exists(base + ".txt") else ""
        g = find(r"GPU por passo \(ms/quadro\)\s+sombra ([\d.]+)\s+cena ([\d.]+)\s+bloom ([\d.]+)\s+composicao ([\d.]+)\s+\| soma ([\d.]+)", txt)
        r0 = runs[0]
        print(f"| `{cena}` | {k} | {fmt(med([float(r['mediana_ms']) for r in runs]))} | {fmt(med([float(r['p95_ms']) for r in runs]))} | {fmt(p99)} | "
              f"{fmt(g[0] if g else None)} | {fmt(g[1] if g else None)} | {fmt(g[4] if g else None)} | {r0['draws']} | {r0['tri_cena']} | "
              f"{r0['tri_sombra']} | {r0['malhas']} | {r0['mb_vertices']} |")

print("\n## Menu e memória (`tools/menu/14-memoria.txt`: menu → criador → vila → mundo novo da semente 1024 → salvo)\n")
print("O mundo criado pela interface nasce na versão ATUAL do binário: v2 no novo, v1 no publicado.\n")
for label, pat in [("binário novo (mundo v2)", "menu-novo"), ("binário publicado (mundo v1)", "menu-publicado")]:
    logs = sorted(glob.glob(os.path.join(D, pat + "-[0-9].log")))
    if not logs:
        if pat == "menu-novo":
            missing.append(pat)
        continue
    first_frame, village, res, trans = [], [], {}, {}
    for f in logs:
        t = open(f, encoding="utf-8", errors="replace").read()
        m = find(r"menu: primeiro quadro (\d+) ms depois do início do processo \(cena da vila montada em (\d+) ms\)", t)
        if m:
            first_frame.append(m[0]); village.append(m[1])
        for name, n, mb in re.findall(r"menu-recursos (\S+): malhas (\d+) \(([\d.]+) MB\)", t):
            res.setdefault(name, []).append((int(n), float(mb)))
        for lab, ms in re.findall(r"transição: (.+?) em (\d+) ms", t):
            lab = re.sub(r" de \S+$| \S+ \(\d+\)$", "", lab)
            trans.setdefault(lab, []).append(int(ms))
    print(f"**{label}**, {len(logs)} repetições: primeiro quadro do menu {fmt(med(first_frame), 0)} ms (cena da vila montada em "
          f"{fmt(med(village), 0)} ms).\n")
    print("| Momento | malhas | MB de vértices |")
    print("|---|---|---|")
    for name, vals in res.items():
        print(f"| {name} | {fmt(med([v[0] for v in vals]), 0)} | {fmt(med([v[1] for v in vals]), 1)} |")
    if trans:
        print("\n| Transição | mediana (ms) | máx (ms) |")
        print("|---|---|---|")
        for lab, vals in trans.items():
            print(f"| {lab} | {fmt(med(vals), 0)} | {max(vals)} |")
    for q in ("quadro-menu", "quadro-vila"):
        qs = []
        for f in sorted(glob.glob(os.path.join(D, f"{pat}-[0-9]-{q}.txt"))):
            m = find(r"quadro \(ms\): n=\d+ média [\d,]+ mediana ([\d,]+) p95 ([\d,]+) p99 ([\d,]+)", open(f, encoding="utf-8").read())
            if m:
                qs.append(m)
        if qs:
            print(f"\n{q}: mediana {fmt(med([x[0] for x in qs]))} ms, p95 {fmt(med([x[1] for x in qs]))}, p99 {fmt(med([x[2] for x in qs]))}")
    print()

if missing:
    print("\nFaltaram (não medidos nesta execução): " + ", ".join(missing))
