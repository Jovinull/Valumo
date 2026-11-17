#!/usr/bin/env python3
"""Rodada 08.1 — compara referências canônicas (antigas × novas): pixels alterados, imagem de diferença e
pontos claros isolados (fresta de 1 pixel: bem mais claro que os 4 vizinhos — o sintoma das T-junctions).
Uso: comparar_referencias.py PASTA_ANTIGA PASTA_NOVA PASTA_DIFERENCAS"""
import sys, glob, os
import numpy as np
from PIL import Image
old, new, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)

def isolated(X):
    L = X.mean(axis=2); c = L[1:-1, 1:-1]
    nb = np.stack([L[:-2, 1:-1], L[2:, 1:-1], L[1:-1, :-2], L[1:-1, 2:]]).max(axis=0)
    return int((c > nb + 40).sum())

print(f"{'referência':42s} {'alterados':>9s} {'>25':>7s} {'frestas antes':>14s} {'depois':>7s}")
tot_a = tot_b = 0
for f in sorted(glob.glob(old + '/*.png')):
    name = os.path.basename(f)
    g = os.path.join(new, name)
    if not os.path.exists(g): print(f"{name:42s} ausente na nova"); continue
    A = np.asarray(Image.open(f).convert('RGB'), dtype=float); B = np.asarray(Image.open(g).convert('RGB'), dtype=float)
    d = np.abs(A - B).max(axis=2)
    ia, ib = isolated(A), isolated(B); tot_a += ia; tot_b += ib
    print(f"{name:42s} {100*(d>0).mean():8.2f}% {100*(d>25).mean():6.2f}% {ia:14d} {ib:7d}")
    vis = np.asarray(Image.open(g).convert('RGB')).copy(); vis[d > 25] = [255, 0, 255]
    Image.fromarray(vis).save(os.path.join(out, name))
print(f"{'TOTAL de pontos claros isolados':42s} {'':9s} {'':7s} {tot_a:14d} {tot_b:7d}")
