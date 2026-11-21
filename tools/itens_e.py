"""Rodada 13.1 — os modelos voxel dos itens da mochila (direção E).

Cada item do catálogo (`assets/scenes/itens_e.json`, campo "modelo") é um objeto pequeno de mão, em voxel 1/40 u (a
densidade do herói: é o que ele carrega), com até 0,4 u na maior medida. O mesmo modelo serve ao espaço da grade, ao
cartão do item recebido e à prévia grande que gira no detalhe (renderizados em tempo real pelo jogo, sem imagem
pré-desenhada). Um dia pode aparecer no mundo ou na mão.

Leitura por item (o que a silhueta e a cor dominante têm de dizer, mesmo pequeno):

| item | silhueta | cor dominante |
|---|---|---|
| ervas | maço em leque, de pé, amarrado embaixo | verde-folha, cordão de palha, um ponto de flor |
| cataplasma | rolo DEITADO de pano, amarrado, folha saindo da ponta | creme, cordão escuro |
| pedra | lasca de lápide, de pé, quebrada em diagonal, meio entalhe | cinza |
| madeira | duas aduelas curvas CRUZADAS em X | castanho, cinta de ferro |
| registro | caderno de pé, capa de couro, fita e páginas claras | vinho |

Regras da E: sombra pintada em bandas (`shade`), 3–5 matizes por modelo, sem preto nem branco puros, nada de ruído de
1 voxel em área grande. Folha de item é FOSCA (não é folhagem: não balança com o vento do shader). Gera
`assets/items/e/<nome>.vox` + `assets/items/e/itens.json` (voxelSize, origin, sizeVoxels, faces expostas).
"""
from __future__ import annotations

import json
import math
import os

from chars_b import P, light, shade, shadow
from voxlib import Vox, exposed_faces, write_vox

VS = 1 / 40          # voxel do herói: objeto de mão

# paleta dos itens (cores-base registradas com rampa; as do mundo vêm das mesmas famílias)
HERB = P("#78ad47")              # folha (a LEAF da E, fosca)
HERB_D = P("#5a8a3c")            # folha de baixo, mais escura
STEM = P("#6f7f36")              # talo
TWINE = P("#e0b057")             # palha (STRAW)
FLOWER = P("#e2644c")            # coral: o único ponto quente
FLOWER_C = P("#f4c653")          # miolo amarelo
CLOTH = P("#efe2bf")             # pano de algodão cru (CREAM puxado para o linho)
CLOTH_D = P("#d6c39a")           # dobra do pano
CORD = P("#6e4130")              # cordão de couro (LEATHER_D)
PASTE = P("#7f9e46")             # as ervas socadas, na ponta do rolo
STONE = P("#b8aaa6")
STONE_D = P("#8f8088")
STONE_L = P("#d2c6bc")
MOSS = P("#92b54e")              # musgo FOSCO
WOOD = P("#b07a48")
WOOD_D = P("#84533a")
WOOD_L = P("#d3a066")
IRON = P("#5d4a5a", "metal")
LEATHER = P("#8c3c3e")           # couro vinho da capa
LEATHER_D = P("#66293a")
PAGES = P("#f1e4c4")
PAGES_D = P("#d9c8a2")
STRAP = P("#6e4130")
BRASS = P("#f2c14e", "metal")
RIBBON = P("#e2644c")


def ervas():
    """Maço de ervas em leque: cinco raminhos amarrados embaixo por um cordão de palha; folhas alternadas; uma flor coral."""
    v = Vox()
    sprigs = [(-0.62, 10.0, -1), (-0.30, 12.0, 1), (0.0, 13.5, 0), (0.32, 12.0, -1), (0.64, 10.0, 1)]   # ângulo, comprimento, z
    tips = []
    for a, ln, z in sprigs:
        base = (round(math.sin(a) * 1.5), 0)
        knot = (0.0, 4.0)
        tip = (knot[0] + math.sin(a) * (ln - 4), knot[1] + math.cos(a) * (ln - 4))
        tips.append((tip, a, z))
        # talo: da base até o nó (juntos) e do nó até a ponta (abertos em leque)
        v.line((base[0], 0, z), (0, 4, z), STEM)
        v.line((0, 4, z), (round(tip[0]), round(tip[1]), z), STEM)
        # folhas alternadas ao longo do raminho, da metade para a ponta
        for i, t in enumerate((0.45, 0.62, 0.80, 0.97)):
            px = knot[0] + (tip[0] - knot[0]) * t
            py = knot[1] + (tip[1] - knot[1]) * t
            side = 1 if (i + int(a * 10)) % 2 == 0 else -1
            nx, ny = math.cos(a) * side, -math.sin(a) * side          # perpendicular ao raminho
            cx, cy = px + nx * 1.4, py + ny * 1.4 + 0.6
            col = HERB if py > 8 else HERB_D
            v.ellipsoid(cx, cy, z + 0.5, 1.5, 1.15, 0.9, col)
        v.ellipsoid(tip[0], tip[1] + 0.8, z + 0.5, 1.1, 1.6, 0.9, HERB)   # o broto da ponta
    # cordão de palha em volta do nó (duas voltas, com a ponta solta)
    for y in (3, 4):
        for x in range(-2, 2):
            for z in (-2, 2):
                v.set(x, y, z, TWINE)
        for z in range(-1, 2):
            v.set(-2, y, z, TWINE)
            v.set(1, y, z, TWINE)
    v.set(2, 3, 2, TWINE)
    v.set(3, 2, 2, TWINE)
    # a flor no raminho do meio, um pouco abaixo da ponta
    (fx, fy), _, fz = tips[1][0], None, tips[1][2]
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
        v.set(round(fx) + dx - 1, round(fy) + dy - 2, fz + 1, FLOWER)
    v.set(round(fx) - 1, round(fy) - 2, fz + 2, FLOWER_C)
    shade(v)
    return v


def cataplasma():
    """Rolo de pano DEITADO (ao longo de X), amarrado por duas voltas de cordão; nas pontas, a espiral do pano com as ervas
    socadas no miolo (a da direita fica de frente para a câmera da mochila); uma folha inteira saindo da outra, para cima."""
    v = Vox()
    r, cy, x0, x1 = 3.6, 3.6, -6, 4
    for x in range(x0, x1 + 1):
        for y in range(0, 8):
            for z in range(-4, 4):
                d = math.hypot(y + 0.5 - cy, z + 0.5)
                if d > r:
                    continue
                end = x in (x0, x1)
                ang = math.atan2(y + 0.5 - cy, z + 0.5)
                if end and d < 1.4:
                    c = PASTE                                         # o miolo verde de ervas
                elif end and abs(d - (2.3 + 0.25 * math.sin(ang))) < 0.45:
                    c = CLOTH_D                                       # a volta de dentro do pano (espiral)
                else:
                    c = CLOTH
                # a borda da última volta do pano, correndo ao longo do rolo
                if not end and d > r - 1 and abs(ang - 2.35) < 0.28:
                    c = CLOTH_D
                v.set(x, y, z, c)
    # duas voltas de cordão, um voxel acima do pano, e o nó em cima de uma delas
    for bx in (-3, 1):
        for y in range(-1, 9):
            for z in range(-5, 5):
                d = math.hypot(y + 0.5 - cy, z + 0.5)
                if r - 0.2 < d <= r + 0.85 and y >= 0:
                    v.set(bx, y, z, CORD)
    v.set(1, 8, 0, CORD).set(2, 8, 0, CORD).set(0, 8, -1, CORD).set(2, 9, 1, CORD)
    # a folha que escapa da ponta da ESQUERDA, inclinada para cima (a da direita, virada para a câmera, mostra a espiral)
    for i in range(6):
        px, py = x0 - i * 0.75, 4.2 + i * 0.85
        w = 1.25 if 1 <= i <= 3 else 0.8
        v.ellipsoid(px, py, 0.5, w, 0.9, w, HERB if i > 1 else HERB_D)
    v.line((x0, 4, 0), (x0 - 4, 8, 0), STEM)
    shade(v)
    return v


def pedra():
    """Lasca de pedra antiga, de pé: a base reta, a quebra em diagonal no alto, as bordas lascadas mais finas; na face, metade
    de um entalhe (um arco e um traço) — a outra metade ficou na pedra de onde ela saiu. Musgo no pé."""
    v = Vox()
    tops = {-5: 8, -4: 10, -3: 11, -2: 11, -1: 12, 0: 11, 1: 10, 2: 10, 3: 8, 4: 7, 5: 5}
    for x, top in tops.items():
        for y in range(0, top + 1):
            edge = x in (-5, 5) or y == top or (y == 0 and x in (-5, 4, 5))
            zs = range(-1, 1) if edge else range(-1, 2)
            for z in zs:
                v.set(x, y, z, STONE)
    for x, y in ((-5, 0), (5, 0), (-5, 8), (5, 5), (-1, 12)):
        v.set(x, y, -1, None)                                          # cantos lascados
    # o meio entalhe na face da frente (z = 1): um arco que a quebra corta e um traço vertical
    cx, cy = 1.5, 5.5
    for x in range(-4, 6):
        for y in range(1, 12):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            arc = 2.6 < d < 3.7 and x + 0.5 >= cx - 0.5
            bar = x == -2 and 2 <= y <= 8
            if (arc or bar) and v.has(x, y, 1):
                v.set(x, y, 1, None)                                   # o sulco
                v.set(x, y, 0, STONE_D)                                # o fundo do sulco, na sombra
    for x, top in tops.items():                                        # a aresta gasta da quebra, mais clara
        for z in (-1, 0, 1):
            if v.has(x, top, z):
                v.set(x, top, z, STONE_L)
    for x, y, z in ((-5, 1, 0), (-4, 0, 1), (-5, 2, 0), (-3, 0, 1), (-4, 1, 1)):
        if v.has(x, y, z):
            v.set(x, y, z, MOSS)
    shade(v)
    return v


def madeira():
    """Duas aduelas de barril, curvas e gastas, cruzadas em X; um pedaço da cinta de ferro ainda presa numa delas."""
    v = Vox()

    def stave(a, b, z0, flip):
        (ax, ay), (bx, by) = a, b
        n = 30
        ln = math.hypot(bx - ax, by - ay)
        ux, uy = (bx - ax) / ln, (by - ay) / ln
        px, py = -uy, ux                                               # largura, no plano XY
        for i in range(n + 1):
            t = i / n
            bow = math.sin(t * math.pi) * 1.3                          # a curva da aduela (o bojo do barril)
            cx, cy = ax + (bx - ax) * t, ay + (by - ay) * t
            half = 1.55 if 0.12 < t < 0.88 else 1.1                   # mais estreita nas pontas
            for k in range(-4, 5):
                s = k / 4 * half
                x, y = round(cx + px * s), round(cy + py * s)
                z = round(z0 + bow * flip)
                grain = abs(s) < 0.35 and 0.2 < t < 0.8
                c = WOOD_D if grain else (WOOD_L if (s > half * 0.6 and flip > 0) else WOOD)
                v.set(x, y, z, c)
                v.set(x, y, z - 1, WOOD)

    stave((-5, 0), (5, 13), 1, 1)         # a da frente
    stave((5, 0), (-5, 12), -2, -1)       # a de trás
    # a cinta de ferro: um anel fino em volta da aduela da frente, perto do pé
    for t in (0.2, 0.25):
        cx, cy = -5 + 10 * t, 13 * t
        for k in range(-3, 4):
            s = k / 3 * 2.0
            ux, uy = 10 / math.hypot(10, 13), 13 / math.hypot(10, 13)
            x, y = round(cx - uy * s), round(cy + ux * s)
            v.set(x, y, 2, IRON)
            v.set(x, y, -1, IRON)
    shade(v)
    return v


def registro():
    """Caderno de viagem de pé, de frente: capa de couro vinho com um filete em baixo-relevo, as páginas claras aparecendo nas
    três bordas, uma tira de couro com fivela de latão e a fita marcadora coral saindo por cima."""
    v = Vox()
    x0, x1, y0, y1, zb, zf = -4, 4, 0, 12, -2, 1
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            v.set(x, y, zf, LEATHER)                                   # capa da frente
            v.set(x, y, zb, LEATHER)                                   # capa de trás
    for y in range(y0, y1 + 1):                                        # lombada arredondada (à esquerda)
        for z in range(zb, zf + 1):
            v.set(x0 - 1 if zb < z < zf else x0, y, z, LEATHER)
    for x in range(x0, x1):                                            # o bloco de páginas, recuado 1 nas três bordas
        for y in range(y0 + 1, y1):
            for z in range(zb + 1, zf):
                line = (y % 2 == 0) and x == x1 - 1
                v.set(x, y, z, PAGES_D if line else PAGES)
    for x in range(x0 + 1, x1):                                        # filete em baixo-relevo na capa
        v.set(x, y0 + 1, zf, LEATHER_D).set(x, y1 - 1, zf, LEATHER_D)
    for y in range(y0 + 1, y1):
        v.set(x0 + 1, y, zf, LEATHER_D).set(x1 - 1, y, zf, LEATHER_D)
    for y in (5, 6):                                                   # a tira de couro que dá a volta
        for x in range(x0, x1 + 1):
            v.set(x, y, zf + 1, STRAP).set(x, y, zb - 1, STRAP)
        for z in range(zb - 1, zf + 2):
            v.set(x1 + 1, y, z, STRAP)
    for x, y in ((1, 5), (2, 5), (1, 6), (2, 6)):
        v.set(x, y, zf + 2, BRASS)                                     # a fivela
    v.set(2, 5, zf + 2, None)
    for y in range(y1 + 1, y1 + 4):                                    # a fita marcadora, saindo por cima e caindo para trás
        v.set(1, y, -1 if y < y1 + 3 else -2, RIBBON)
    v.set(1, y1 + 3, -3, RIBBON).set(1, y1 + 2, -3, RIBBON)
    shade(v)
    return v


MODELS = {"ervas": ervas, "cataplasma": cataplasma, "pedra": pedra, "madeira": madeira, "registro": registro}


def build(out):
    d = os.path.join(out, "items", "e")
    os.makedirs(d, exist_ok=True)
    manifest = {}
    for name, fn in MODELS.items():
        v = fn()
        b = v.bounds()
        if b[1] != 0:
            v.translate(0, -b[1], 0)                                   # apoiado no chão (y = 0), como os props
        origin = write_vox(os.path.join(d, f"{name}.vox"), v)
        b = v.bounds()
        size = [b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1]
        if max(size) * VS > 0.4 + 1e-6:
            raise ValueError(f"item {name}: {max(size) * VS:.3f} u passa de 0,4 u")
        manifest[name] = {"file": f"{name}.vox", "voxelSize": VS, "origin": origin, "sizeVoxels": size,
                          "voxels": len(v), "faces": exposed_faces(v)}
    with open(os.path.join(d, "itens.json"), "w") as f:
        json.dump(manifest, f, indent=1, sort_keys=True)
