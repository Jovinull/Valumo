#!/usr/bin/env python3
"""QA de engenharia — resume o recursos.csv da sessão longa (tools/qa/sessao_longa.py).

Uso: python3 tools/qa/recursos_resumo.py SAIDA/recursos.csv

Para cada coluna: valor no início, depois do aquecimento (1/4 da sessão), no fim, mínimo e máximo, e a inclinação por
hora na METADE FINAL (regressão linear). Vazamento = cresce de forma persistente depois do aquecimento e do teto dos
caches. Uma inclinação pequena num recurso que tem teto (malhas: compositor com 8 aparências) não é vazamento se o
valor parou de subir. Saída 1 se algum contador de recurso nativo (malhas, texturas, FBOs, buffers/fluxos do OpenAL,
threads) ainda sobe na metade final.
"""
import csv
import sys

rows = list(csv.DictReader(open(sys.argv[1])))
if len(rows) < 8:
    sys.exit("poucas amostras")
t = [float(r["relogio_s"]) for r in rows]


def slope(xs, ys):
    n = len(xs)
    mx, my = sum(xs) / n, sum(ys) / n
    den = sum((x - mx) ** 2 for x in xs) or 1.0
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / den


warm = len(rows) // 4
half = len(rows) // 2
print(f"amostras {len(rows)}, relógio {t[0]:.0f}–{t[-1]:.0f} s ({(t[-1] - t[0]) / 60:.1f} min), "
      f"tempo de jogo {float(rows[-1]['jogo_s']):.0f} s")
print(f"{'coluna':<22}{'início':>10}{'aquecido':>10}{'fim':>10}{'mín':>10}{'máx':>10}{'por hora (2ª metade)':>24}")
grow = []
for c in rows[0].keys():
    if c in ("relogio_s", "jogo_s", "quadros", "perf_quadros_guardados"):
        continue
    v = [float(r[c]) for r in rows]
    s = slope(t[half:], v[half:]) * 3600
    print(f"{c:<22}{v[0]:>10.2f}{v[warm]:>10.2f}{v[-1]:>10.2f}{min(v):>10.2f}{max(v):>10.2f}{s:>24.3f}")
    if c in ("malhas", "texturas", "fbos", "al_buffers", "al_fluxos", "threads") and v[-1] > max(v[half:half + 3]) and s > 0.5:
        grow.append(c)
print("recursos nativos ainda subindo na metade final: " + (", ".join(grow) if grow else "nenhum"))
sys.exit(1 if grow else 0)
