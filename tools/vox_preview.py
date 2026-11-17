#!/usr/bin/env python3
"""Prévia ortográfica COLORIDA de um rig ou de um .vox, sem abrir o viewer (sem GPU).

    .venv/bin/python tools/vox_preview.py assets/characters/npc/cartografa saida.png
    .venv/bin/python tools/vox_preview.py assets/environment/e/trail_post.vox saida.png [px]

Monta o rig em pose de amarração (cada parte no seu `origin` do rig.json; alternativas de expressão ficam de
fora), projeta frente, perfil e costas (o voxel mais próximo vence) e escurece de leve com a profundidade para
dar volume. Serve para conferir forma, paleta e fragmentos rapidamente — inclusive em máquina sem OpenGL.
Não substitui a captura do viewer (luz, contorno, sombra, animação): a aprovação continua sendo pela
captura real.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxlib import load_vox  # noqa: E402  (eixos já desespelhados; ida e volta testada na rodada 05)


def load(path):
    """{(x, y, z): (r, g, b)} em coordenadas do modelo (Y para cima, frente = +z)."""
    cells = {}
    if path.endswith(".vox"):
        for k, c in load_vox(path).v.items():
            cells[k] = c[:3]
        return cells
    doc = json.load(open(os.path.join(path, "rig.json")))
    for p in doc["parts"]:
        ox, oy, oz = p["origin"]
        for (x, y, z), c in load_vox(os.path.join(path, p["file"])).v.items():
            cells[(x + ox, y + oy, z + oz)] = c[:3]
    return cells


def view(cells, kind, px):
    pix = {}
    for (x, y, z), c in cells.items():
        if kind == "frente":                 # câmera em +z: a esquerda do personagem (+x) fica à direita da tela
            key, depth = (x, y), z
        elif kind == "costas":
            key, depth = (-x, y), -z
        else:                                # perfil esquerdo do personagem
            key, depth = (z, y), x
        if key not in pix or depth > pix[key][0]:
            pix[key] = (depth, c)
    xs = [k[0] for k in pix]
    ys = [k[1] for k in pix]
    dmin = min(d for d, _ in pix.values())
    dmax = max(d for d, _ in pix.values())
    im = Image.new("RGB", ((max(xs) - min(xs) + 3) * px, (max(ys) - min(ys) + 3) * px), (214, 208, 202))
    dr = ImageDraw.Draw(im)
    for (u, y), (d, c) in pix.items():
        k = 0.82 + 0.18 * ((d - dmin) / max(1, dmax - dmin))         # mais fundo = mais escuro
        col = tuple(int(v * k) for v in c)
        x0, y0 = (u - min(xs) + 1) * px, (max(ys) - y + 1) * px
        dr.rectangle((x0, y0, x0 + px - 1, y0 + px - 1), fill=col)
    return im


def main():
    src, out = sys.argv[1], sys.argv[2]
    px = int(sys.argv[3]) if len(sys.argv) > 3 else 8
    cells = load(src)
    tiles = [view(cells, k, px) for k in ("frente", "perfil", "costas")]
    W = sum(t.width for t in tiles) + 16 * (len(tiles) - 1)
    sheet = Image.new("RGB", (W, max(t.height for t in tiles)), (240, 236, 230))
    x = 0
    for t in tiles:
        sheet.paste(t, (x, sheet.height - t.height))
        x += t.width + 16
    sheet.save(out)
    print(f"{out}: {len(cells)} voxels (frente, perfil, costas)")


if __name__ == "__main__":
    main()
