#!/usr/bin/env python3
"""Rodada 06 — silhueta ortográfica de props (.vox) na MESMA escala, para comparar espécies/estruturas.

    .venv/bin/python tools/prop_silhouette.py saida.png tree_willow tree_oak tree_pine
    .venv/bin/python tools/prop_silhouette.py saida.png trail_post house

Projeta cada modelo de frente (x × y) e de lado (z × y), em preto sobre branco, alinhado pela base, 4 px por
voxel. Complementa as capturas do viewer (o modo silhueta do viewer é para personagens; em cena pinta tudo).
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import asset_audit as A  # noqa: E402

PX = 4


def proj(cells, a, b):
    return {(k[a], k[b]) for k in cells}


def main():
    out, names = sys.argv[1], sys.argv[2:]
    models = []
    for n in names:
        vox, _ = A.read_vox(os.path.join(os.path.dirname(__file__), "..", "assets", "environment", "e", n + ".vox"))
        models.append((n, set(vox)))
    H = max(max(k[1] for k in c) for _, c in models) + 4
    tiles = []
    for n, c in models:
        for a in (0, 2):                                   # frente (x) e lado (z)
            p = proj(c, a, 1)
            w = max(k[0] for k in p) + 3
            im = Image.new("RGB", (w * PX, H * PX), "white")
            for (u, y) in p:
                for dx in range(PX):
                    for dy in range(PX):
                        im.putpixel(((u + 1) * PX + dx, (H - 2 - y) * PX + dy), (38, 36, 44))
            tiles.append(im)
    W = sum(t.width for t in tiles) + 20 * len(tiles)
    sheet = Image.new("RGB", (W, H * PX), "white")
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 20
    sheet.save(out)
    print(out, " | ".join(f"{n}: frente + lado" for n, _ in models))


if __name__ == "__main__":
    main()
