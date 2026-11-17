#!/usr/bin/env python3
"""Rodada 08.1 — mede ALTERNÂNCIA (tremor) numa sequência de quadros: pixel que muda de t−1 para t e VOLTA em t+1
(A→B→A). Deslocamento normal de câmera lenta é monótono e quase não volta; z-fighting volta o tempo todo.
Uso: alternancia.py PASTA_DE_QUADROS [mapa.png]  → imprime média/máx de pixels alternando por quadro."""
import sys, glob
import numpy as np
from PIL import Image
fs = sorted(glob.glob(sys.argv[1] + '/*.png'))
F = [np.asarray(Image.open(f).convert('RGB'), dtype=np.int16) for f in fs]
heat = None
counts = []
for t in range(1, len(F) - 1):
    a, b, c = F[t - 1], F[t], F[t + 1]
    jump = np.abs(b - a).max(axis=2) > 25
    back = np.abs(c - a).max(axis=2) < 10
    alt = jump & back
    counts.append(int(alt.sum()))
    heat = alt.astype(np.int32) if heat is None else heat + alt
counts = np.array(counts)
print(f"quadros {len(F)}  alternância por quadro: média {counts.mean():.1f}  mediana {np.median(counts):.0f}  máx {counts.max()}  total {counts.sum()}")
if len(sys.argv) > 2:
    img = (np.clip(heat, 0, 8) * 31).astype(np.uint8)
    Image.fromarray(img).save(sys.argv[2])
