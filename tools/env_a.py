"""Direção A — Miniature Fantasy: ambiente voxel clássico.

Linguagem de forma: massas CÚBICAS com cantos chanfrados (superelipsoide p alto), copas em blocos
empilhados, telhados retos e íngremes, cores vivas e legíveis. Voxel grande 1/8 u (árvores, construções,
pedras) e voxel fino 1/16 u (flores, objetos de mão) — o personagem usa 1/24 u.
"""
import math

from envlib import frame_box, gable_fill, gable_roof, hash3, lobe, ramp, rock, toplight, trunk
from voxlib import C, PropSet, Vox, tone

BIG, FINE = 1 / 8, 1 / 16

LEAF = C("#46b040", "foliage")
LEAF_PINE = C("#2f8f5a", "foliage")
LEAF_BIRCH = C("#9fd13e", "foliage")
LEAF_PINK = C("#f48fb6", "foliage")
BARK = C("#7a4f2c")
BARK_BIRCH = C("#e8e4d6")
STONE = C("#9aa3ad")
STONE_D = C("#7c8590")
MOSS = C("#5faa3c")
WOOD = C("#a9703a")
WOOD_D = C("#7d4f26")
WOOD_L = C("#c98d4e")
PLASTER = C("#f3e6c4")
ROOF = C("#d8432f")
GLOW = C("#ffd66b", "emissive")
RUNE = C("#6fe6ff", "emissive")
FIRE = C("#ff8a2a", "emissive")
FIRE_Y = C("#ffe07a", "emissive")
IRON = C("#59606e", "metal")
GOLD = C("#f6c636", "metal")
WATER = C("#3d9be0", "water")


def is_leaf(c):
    return c[3] == "foliage"


def canopy(v, lobes, base, p=5.0, rough=0.35, seed=1):
    for i, (x, y, z, rx, ry, rz) in enumerate(lobes):
        lobe(v, x, y, z, rx, ry, rz, base, p=p, rough=rough, seed=seed + i)
    toplight(v, is_leaf, ramp(base, 1.0), sparkle=0.12, seed=seed)


def tree_oak(seed=1, leaf=LEAF, h=13):
    v = Vox()
    trunk(v, 0, 0, 0, h + 4, 1.9, 1.4, BARK, roots=4, seed=seed)
    v.line((0, h - 3, 0), (4, h + 2, 2), BARK, 0)
    v.line((0, h - 4, 0), (-4, h + 1, -1), BARK, 0)
    canopy(v, [(0, h + 9, 0, 9.5, 7, 9.5), (-6, h + 5, 3, 6, 5, 6), (6, h + 6, -3, 6.5, 5, 6),
               (1, h + 15, 1, 6, 4.5, 6), (-3, h + 11, -6, 5, 4, 5)], leaf, seed=seed)
    return v


def tree_blossom():
    v = Vox()
    trunk(v, 0, 0, 0, 14, 1.7, 1.2, C("#6b4433"), lean=(1.5, 0), roots=4, seed=4)
    v.line((1, 10, 0), (6, 15, 1), C("#6b4433"), 0)
    v.line((1, 9, 0), (-5, 14, -2), C("#6b4433"), 0)
    canopy(v, [(1, 19, 0, 8.5, 5.5, 8), (-5, 16, -2, 5.5, 4, 5.5), (7, 16, 2, 5, 4, 5), (2, 24, 0, 5, 3.5, 5)],
           LEAF_PINK, seed=5)
    return v


def tree_pine(seed=2):
    v = Vox()
    trunk(v, 0, 0, 0, 12, 1.5, 1.1, C("#6a452b"), roots=3, seed=seed)
    y = 7
    for i, half in enumerate((9, 7.5, 6, 4.5, 3)):
        hgt = 8 if i < 4 else 7
        for dy in range(hgt):
            r = half * (1 - dy / (hgt + 2.0))
            for x in range(int(-r) - 1, int(r) + 2):
                for z in range(int(-r) - 1, int(r) + 2):
                    if max(abs(x + 0.5), abs(z + 0.5)) <= r and abs(x + 0.5) + abs(z + 0.5) <= r * 1.55:
                        v.set(x, y + dy, z, LEAF_PINE)
        y += hgt - 2
    toplight(v, is_leaf, ramp(LEAF_PINE, 1.0), sparkle=0.12, seed=seed)
    return v


def tree_birch():
    v = Vox()
    trunk(v, 0, 0, 0, 22, 1.2, 0.9, BARK_BIRCH, seed=6)
    for y in (3, 8, 12, 17):
        v.set(-1 if y % 2 else 0, y, 0 if y % 3 else -1, C("#3d3a36"))
        v.set(0, y + 1, -1, C("#3d3a36"))
    canopy(v, [(0, 25, 0, 6, 5.5, 6), (-3, 21, 2, 4.5, 3.5, 4.5), (3, 22, -2, 4.5, 3.5, 4), (0, 30, 0, 4, 3.5, 4)],
           LEAF_BIRCH, seed=7)
    return v


def tree_big():
    """Árvore-marco com casinha no tronco: ponto de interesse da clareira."""
    v = Vox()
    trunk(v, 0, 0, 0, 30, 5.2, 3.6, BARK, roots=6, seed=9)
    for (a, b) in (((0, 22, 0), (10, 30, 4)), ((0, 20, 0), (-11, 28, 2)), ((0, 24, 0), (2, 31, -10))):
        v.line(a, b, BARK, 1)
    # porta redonda + janela iluminada + degrau
    v.box(-2, 0, 4, 1, 6, 6, C("#5a3a20"))
    v.box(-2, 0, 5, 1, 5, 5, C("#c0503a"))
    v.set(-2, 5, 5, C("#5a3a20"))
    v.set(1, 5, 5, C("#5a3a20"))
    v.set(1, 3, 6, GOLD)
    v.box(-3, 0, 6, 2, 0, 7, STONE)
    v.box(3, 9, 3, 5, 11, 4, C("#5a3a20"))
    v.box(3, 9, 4, 4, 10, 4, GLOW)
    # cogumelos-prateleira no tronco
    for (x, y, z) in ((-6, 8, 1), (-6, 12, -2), (5, 15, -3)):
        v.box(x, y, z, x + 1, y, z + 2, C("#f0a04a"))
    canopy(v, [(0, 40, 0, 17, 10, 17), (-12, 33, 4, 9, 7, 9), (12, 34, 5, 9, 7, 9), (2, 35, -12, 9, 7, 8),
               (-8, 46, -6, 9, 6, 9), (8, 47, 3, 9, 6, 9), (0, 52, 0, 8, 5, 8), (-14, 40, -8, 6, 5, 6),
               (15, 41, -6, 6, 5, 6)], C("#3aa544", "foliage"), rough=0.4, seed=10)
    return v


def sapling():
    v = Vox()
    trunk(v, 0, 0, 0, 6, 0.9, 0.7, BARK)
    canopy(v, [(0, 9, 0, 4, 4, 4), (-2, 7, 1, 2.5, 2, 2.5)], LEAF, seed=12)
    return v


def bush(berry=False, seed=14):
    v = Vox()
    canopy(v, [(0, 3, 0, 5, 4, 5), (4, 2, 2, 3.5, 3, 3.5), (-3, 2, -3, 3, 2.5, 3)],
           C("#3f9f3a", "foliage"), seed=seed)
    if berry:
        for (x, y, z) in list(v.v):
            if hash3(x, y, z, 3) < 0.07 and ((x, y + 1, z) not in v.v or (x, y, z + 1) not in v.v):
                v.set(x, y, z, C("#e8344a"))
    for k in [k for k in v.v if k[1] < 0]:
        del v.v[k]
    return v


def grass(tall=False, seed=1):
    v = Vox()
    g = ramp(C("#58b044", "foliage"))
    n = 6 if tall else 4
    for i in range(n):
        x = int(hash3(i, 0, 0, seed) * 9) - 4
        z = int(hash3(i, 1, 0, seed) * 9) - 4
        h = (4 if tall else 2) + int(hash3(i, 2, 0, seed) * (4 if tall else 2))
        for y in range(h):
            lean = 1 if (y > h * 0.6 and hash3(i, 3, 0, seed) > 0.5) else 0
            v.set(x + lean, y, z, g[1] if y < h / 2 else (g[2] if y < h - 1 else g[3]))
    return v


def fern():
    v = Vox()
    g = ramp(C("#3f9a4a", "foliage"))
    for i in range(6):
        a = i * math.pi / 3 + 0.3
        for s in range(1, 7):
            x, z = round(math.cos(a) * s), round(math.sin(a) * s)
            y = round(3.2 * math.sin(s / 6.5 * math.pi))
            v.set(x, y, z, g[2] if s < 4 else g[3])
            if s in (2, 4):
                v.set(x + (1 if z else 0), y, z + (0 if z else 1), g[1])
    return v


def flower(petal, center=C("#ffd84a"), seed=1):
    v = Vox()
    stem = C("#4fa83e", "foliage")
    for i in range(3):
        x = int(hash3(i, 0, 0, seed) * 9) - 4
        z = int(hash3(i, 1, 0, seed) * 9) - 4
        h = 3 + int(hash3(i, 2, 0, seed) * 3)
        v.box(x, 0, z, x, h - 1, z, stem)
        v.set(x + 1, h // 2, z, stem)
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v.set(x + dx, h, z + dz, petal)
        v.set(x, h, z, center)
        v.set(x, h + 1, z, tone(petal, 1))
    return v


def mushrooms(cap, dots=C("#fff6e6"), seed=1):
    v = Vox()
    for i, (x, z, h, r) in enumerate(((0, 0, 5, 3), (4, 2, 3, 2), (-3, 3, 2, 2))):
        v.box(x, 0, z, x, h - 1, z, C("#f3e7cf"))
        if r == 3:
            v.box(x - 1, 0, z - 1, x + 1, h - 2, z + 1, C("#f3e7cf"))
        v.box(x - r, h, z - r, x + r, h, z + r, tone(cap, -1))
        v.box(x - r + 1, h + 1, z - r + 1, x + r - 1, h + (2 if r == 3 else 1), z + r - 1, cap)
        for (dx, dz) in ((-r, -r), (r, -r), (-r, r), (r, r)):
            v.set(x + dx, h, z + dz, None)
        v.set(x - 1, h + (2 if r == 3 else 1), z, dots)
        v.set(x + 1, h + 1, z + r - 1, dots)
    return v


def mushroom_big():
    v = Vox()
    stem = C("#efe2c8")
    trunk(v, 0, 0, 0, 11, 2.2, 1.6, stem, lean=(1.0, 0.5))
    cap = C("#8a5fd6")
    capr = ramp(cap)
    tmp = Vox()
    lobe(tmp, 1, 11, 0.5, 8.5, 6, 8.5, cap, p=3.0)
    for k in [k for k in tmp.v if k[1] < 11]:
        del tmp.v[k]
    toplight(tmp, lambda c: True, capr, sparkle=0.05)
    for (x, y, z) in list(tmp.v):
        if (x, y + 1, z) not in tmp.v and hash3(x // 2, 0, z // 2, 8) < 0.18:
            tmp.v[(x, y, z)] = C("#b9fff0", "emissive")
        if y == 11:
            tmp.v[(x, y, z)] = C("#e9d8f6")
    v.merge(tmp)
    return v


def house():
    v = Vox()
    x0, x1, z0, z1 = -18, 17, -14, 13
    beam = C("#5d3a22")
    stone = ramp(STONE)
    # fundação de pedra (desce abaixo do chão para assentar no relevo)
    v.box(x0 - 1, -5, z0 - 1, x1 + 1, 3, z1 + 1, STONE)
    v.paint(lambda x, y, z, c: stone[1] if hash3(x // 3, y // 2, z // 3, 2) < 0.3 else
            (stone[3] if hash3(x // 3, y // 2, z // 3, 2) > 0.85 else None))
    # paredes de estuque com enxaimel
    v.box(x0, 4, z0, x1, 19, z1, PLASTER)
    v.paint(lambda x, y, z, c: tone(PLASTER, -0.6) if c == PLASTER and hash3(x // 2, y // 2, z // 2, 3) < 0.12 else None)
    for y in (4, 12, 19):
        v.box(x0, y, z0, x1, y, z0, beam)
        v.box(x0, y, z1, x1, y, z1, beam)
        v.box(x0, y, z0, x0, y, z1, beam)
        v.box(x1, y, z0, x1, y, z1, beam)
    for x in (x0, x0 + 1, -9, -8, 8, 9, x1 - 1, x1):
        v.box(x, 4, z1, x, 19, z1, beam)
        v.box(x, 4, z0, x, 19, z0, beam)
    for z in (z0, z0 + 1, -1, 0, z1 - 1, z1):
        v.box(x0, 4, z, x0, 19, z, beam)
        v.box(x1, 4, z, x1, 19, z, beam)
    for i in range(6):   # contraventamentos diagonais
        v.set(x0 + 2 + i, 13 + i, z1, beam)
        v.set(x1 - 2 - i, 13 + i, z1, beam)
    # porta em arco
    door = C("#b5652f")
    v.box(-4, 4, z1, 4, 16, z1, beam)
    v.box(-3, 4, z1, 3, 15, z1, door)
    v.set(-3, 15, z1, beam)
    v.set(3, 15, z1, beam)
    for x in (-1, 1):
        v.box(x, 4, z1, x, 15, z1, tone(door, -1))
    v.set(2, 9, z1 + 1, GOLD)
    v.box(-5, 1, z1 + 2, 5, 3, z1 + 4, STONE)
    v.box(-4, 1, z1 + 5, 4, 2, z1 + 6, stone[1])
    # janelas com venezianas e floreira
    for wx in (-15, 10):
        v.box(wx, 9, z1, wx + 5, 14, z1, beam)
        v.box(wx + 1, 10, z1, wx + 4, 13, z1, C("#ffe9a6", "emissive"))
        v.box(wx + 2, 10, z1, wx + 3, 13, z1, C("#ffd27a", "emissive"))
        v.box(wx - 2, 9, z1 + 1, wx - 1, 14, z1 + 1, C("#3b7dd8"))
        v.box(wx + 6, 9, z1 + 1, wx + 7, 14, z1 + 1, C("#3b7dd8"))
        v.box(wx - 1, 7, z1 + 1, wx + 6, 8, z1 + 2, WOOD_D)
        for i, x in enumerate(range(wx - 1, wx + 7)):
            v.set(x, 9, z1 + 2, C("#4fa83e", "foliage"))
            if i % 2 == 0:
                v.set(x, 10, z1 + 2, C("#ff5d73") if i % 4 == 0 else C("#ffd84a"))
    for wz in (-9, 4):   # janelas laterais
        for xs in (x0, x1):
            v.box(xs, 9, wz, xs, 14, wz + 4, beam)
            v.box(xs, 10, wz + 1, xs, 13, wz + 3, C("#ffe9a6", "emissive"))
    # telhado
    tiles = ramp(ROOF)
    top = gable_roof(v, x0, x1, z0, z1, 20, tiles, tone(ROOF, -1.5), overhang=2, steep=0.95)
    gable_fill(v, x0, z0, z1, 20, PLASTER, beam, steep=0.95, overhang=2)
    gable_fill(v, x1, z0, z1, 20, PLASTER, beam, steep=0.95, overhang=2)
    v.box(x0, 24, -2, x0, 28, 1, beam)
    v.box(x0, 25, -1, x0, 27, 0, C("#ffe9a6", "emissive"))
    # chaminé
    v.box(8, 24, -8, 12, top + 4, -4, STONE)
    v.box(7, top + 4, -9, 13, top + 5, -3, stone[1])
    v.box(9, top + 5, -7, 11, top + 5, -5, C("#3a3d44"))
    v.paint(lambda x, y, z, c: stone[1] if c == STONE and y > 20 and hash3(x // 2, y // 2, z // 2, 4) < 0.35 else None)
    # trepadeira no canto
    for y in range(4, 17):
        for d in range(3):
            if hash3(y, d, 0, 6) < 0.7:
                v.set(x1 + 1, y, z1 - 2 - d - (y // 5), C("#3f9f3a", "foliage"))
    return v


def well():
    v = Vox()
    st = ramp(STONE)
    for y in range(-3, 5):
        for x in range(-6, 6):
            for z in range(-6, 6):
                d = math.hypot(x + 0.5, z + 0.5)
                if 3.4 <= d <= 5.6:
                    v.set(x, y, z, st[1 + int(hash3(x // 2, y, z // 2, 1) * 2.6)])
                elif d < 3.4 and y == 2:
                    v.set(x, y, z, WATER)
    for xs in (-6, 5):
        v.box(xs, 5, -1, xs, 14, 0, WOOD_D)
    v.box(-6, 13, 0, 5, 13, 0, WOOD)
    gable_roof(v, -6, 5, -4, 3, 15, ramp(C("#3b7dd8")), C("#2a5aa8"), overhang=1, steep=0.9)
    v.box(-1, 8, -1, 0, 12, 0, C("#c9b79a"))          # corda
    v.box(-2, 5, -2, 1, 7, 1, WOOD)
    v.box(-1, 7, -1, 0, 7, 0, None)
    return v


def fence():
    v = Vox()
    for x in (-8, -1, 6):
        v.box(x, -2, 0, x + 1, 7, 1, WOOD_D)
        v.set(x, 8, 0, WOOD)
        v.set(x + 1, 8, 1, WOOD)
    v.box(-8, 2, 0, 7, 2, 0, WOOD)
    v.box(-8, 5, 0, 7, 5, 0, WOOD_L)
    return v


def signpost():
    v = Vox()
    v.box(-1, -2, -1, 0, 21, 0, WOOD_D)
    v.box(-1, 22, -1, 0, 22, 0, WOOD)
    v.box(-1, 15, 1, 9, 19, 1, WOOD_L)
    v.box(10, 16, 1, 10, 18, 1, WOOD_L)
    v.set(11, 17, 1, WOOD_L)
    v.box(1, 17, 2, 7, 17, 2, C("#5a3a20"))
    v.box(-10, 9, 1, 0, 13, 1, WOOD)
    v.box(-11, 10, 1, -11, 12, 1, WOOD)
    v.set(-12, 11, 1, WOOD)
    v.box(-8, 11, 2, -3, 11, 2, C("#5a3a20"))
    return v


def lantern_post():
    v = Vox()
    v.box(-1, -2, -1, 0, 24, 0, WOOD_D)
    v.box(-2, 0, -2, 1, 1, 1, STONE_D)
    v.box(-1, 24, -1, 6, 25, 0, WOOD_D)
    v.box(4, 21, -1, 5, 23, 0, IRON)
    v.box(3, 15, -2, 6, 20, 1, GLOW)
    frame_box(v, 3, 15, -2, 6, 20, 1, GLOW, IRON)
    v.box(2, 21, -3, 7, 21, 2, IRON)
    v.box(3, 14, -2, 6, 14, 1, IRON)
    return v


def crate():
    v = Vox()
    frame_box(v, -5, 0, -5, 4, 9, 4, WOOD_L, WOOD_D)
    for i in range(1, 9):
        v.set(-5 + i, i, 4, WOOD_D if i in (1, 8) else WOOD)
        v.set(4, i, -5 + i, WOOD)
        v.set(-5, i, 4 - i, WOOD)
        v.set(4 - i, i, -5, WOOD)
    return v


def barrel():
    v = Vox()
    for y in range(13):
        r = 4.2 + (0.9 if 2 <= y <= 10 else 0.0) + (0.5 if 4 <= y <= 8 else 0.0)
        for x in range(-6, 6):
            for z in range(-6, 6):
                if math.hypot(x + 0.5, z + 0.5) <= r:
                    band = y in (2, 10)
                    v.set(x, y, z, IRON if band else (WOOD_D if (x + z) % 3 == 0 else WOOD))
    v.paint(lambda x, y, z, c: tone(WOOD, -1.5) if y == 12 and math.hypot(x + 0.5, z + 0.5) < 3.6 else None)
    return v


def shrine():
    """Ruína com monólito rúnico: o elemento 'fantástico' da clareira."""
    v = Vox()
    st = ramp(STONE)
    pick = lambda x, y, z: st[1 + int(hash3(x // 2, y // 2, z // 2, 3) * 2.7)]
    for i, (r, y) in enumerate(((14, 0), (12, 1), (10, 2))):
        for x in range(-r, r):
            for z in range(-r + 3, r - 3):
                if abs(x + 0.5) + abs(z + 0.5) * 0.6 < r * 1.25:
                    v.set(x, y, z, pick(x, y, z))
    v.box(-14, -3, -9, 13, -1, 8, STONE_D)
    for xs, h in ((-10, 19), (8, 11)):       # pilares (um quebrado)
        v.box(xs - 1, 3, -3, xs + 2, 4, 0, st[1])
        for y in range(5, h):
            for x in range(xs, xs + 2):
                for z in range(-2, 0):
                    v.set(x, y, z, pick(x, y * 2, z))
        if h > 15:
            v.box(xs - 1, h, -3, xs + 2, h + 1, 0, st[3])
            v.box(xs - 1, h + 2, -3, xs + 8, h + 3, 0, st[2])     # resto do lintel
            v.set(xs + 8, h + 2, -3, None)
            v.box(xs + 6, h + 2, 0, xs + 8, h + 2, 0, None)
        else:
            v.set(xs, h, -2, st[1])
            v.set(xs + 1, h, -1, st[3])
    # monólito central com runa
    for y in range(3, 17):
        w = 3 if y < 13 else (2 if y < 15 else 1)
        for x in range(-w, w):
            for z in range(-2, 1):
                v.set(x, y, z, pick(x + 9, y, z))
    for (x, y) in ((-1, 12), (0, 12), (-2, 11), (1, 11), (-1, 10), (0, 10), (-1, 9), (0, 8), (-1, 7), (0, 7), (-2, 6), (1, 6), (-1, 5), (0, 5)):
        v.set(x, y, 1, RUNE)
    # blocos caídos, cristais e musgo
    v.box(4, 3, 3, 8, 4, 5, st[2])
    v.box(11, 0, 4, 14, 2, 7, st[1])
    for (x, z, h) in ((-5, 4, 5), (-6, 5, 3), (-4, 5, 2), (5, -5, 4)):
        v.box(x, 3, z, x, 3 + h - 1, z, RUNE)
        v.set(x, 3 + h, z, C("#d6fbff", "emissive"))
    for (x, y, z) in list(v.v):
        if v.v[(x, y, z)][3] == "matte" and (x, y + 1, z) not in v.v and hash3(x, y, z, 12) < 0.3:
            v.v[(x, y, z)] = ramp(MOSS)[2 + (hash3(x, z, y, 2) > 0.6)]
    return v


def campfire():
    v = Vox()
    st = ramp(STONE)
    for i in range(10):
        a = i * 2 * math.pi / 10
        x, z = round(math.cos(a) * 6.5 - 0.5), round(math.sin(a) * 6.5 - 0.5)
        v.box(x - 1, 0, z - 1, x + 1, 1 + (i % 2), z + 1, st[1 + i % 3])
    v.line((-4, 0, -3), (4, 2, 3), WOOD_D, 0)
    v.line((-4, 0, 3), (4, 2, -3), WOOD_D, 0)
    v.line((0, 0, -5), (0, 2, 4), WOOD, 0)
    for y in range(1, 11):
        r = 3.2 * (1 - (y / 11.0) ** 1.4)
        for x in range(-4, 4):
            for z in range(-4, 4):
                d = math.hypot(x + 0.5 + math.sin(y * 0.9) * 0.6, z + 0.5)
                if d <= r:
                    v.set(x, y, z, FIRE_Y if d < r * 0.5 else FIRE)
    v.box(-3, 0, -3, 2, 0, 2, C("#3a3230"))
    return v


def log():
    v = Vox()
    for x in range(-8, 8):
        for y in range(0, 6):
            for z in range(-3, 3):
                if math.hypot(y + 0.5 - 2.6, z + 0.5) <= 2.9:
                    end = x in (-8, 7)
                    v.set(x, y, z, (WOOD_L if math.hypot(y - 2.1, z + 0.5) < 1.6 else WOOD) if end
                    else (WOOD_D if hash3(x // 3, y, z, 1) < 0.3 else BARK))
    for x in range(-6, 5):
        if hash3(x, 0, 0, 5) < 0.6:
            v.set(x, 5, 0 if x % 2 else -1, ramp(MOSS)[2])
    v.box(3, 4, 2, 3, 6, 2, C("#f3e7cf"))
    v.box(2, 7, 1, 4, 7, 3, C("#e8443a"))
    return v


def stump():
    v = Vox()
    trunk(v, 0, 0, 0, 5, 3.4, 3.0, BARK, roots=4, seed=3)
    for x in range(-4, 4):
        for z in range(-4, 4):
            d = math.hypot(x + 0.5, z + 0.5)
            if d <= 3.0:
                v.set(x, 4, z, WOOD if int(d) % 2 else WOOD_L)
    v.set(3, 0, 3, ramp(MOSS)[2])
    return v


def dock():
    v = Vox()
    for x in range(-2, 26):
        for z in range(-4, 4):
            v.set(x, 3, z, WOOD_L if (x // 3) % 2 == 0 else WOOD)
            if x % 3 == 2:
                v.set(x, 3, z, WOOD_D)
    for x in (0, 12, 24):
        for z in (-4, 3):
            v.box(x, -9, z, x, 2, z, WOOD_D)
            if x == 24:
                v.box(x, 4, z, x, 8, z, WOOD_D)
                v.set(x, 9, z, WOOD)
    v.box(24, 7, -3, 24, 7, 2, C("#c9b79a"))
    frame_box(v, 19, 4, -3, 22, 7, 0, WOOD_L, WOOD_D)
    return v


def reeds():
    v = Vox()
    g = ramp(C("#6aa84a", "foliage"))
    for i in range(7):
        x = int(hash3(i, 0, 0, 3) * 10) - 5
        z = int(hash3(i, 1, 0, 3) * 10) - 5
        h = 10 + int(hash3(i, 2, 0, 3) * 9)
        v.box(x, -3, z, x, h, z, g[1] if i % 2 else g[2])
        if i % 2 == 0:
            v.box(x, h + 1, z, x, h + 4, z, C("#6b3f22"))
    return v


def lily():
    v = Vox()
    g = ramp(C("#4fae5a", "foliage"))
    for x in range(-3, 3):
        for z in range(-3, 3):
            if math.hypot(x + 0.5, z + 0.5) <= 2.9 and not (x >= 0 and z == 0):
                v.set(x, 0, z, g[2] if (x + z) % 2 else g[3])
    v.set(-1, 1, -1, C("#ff9ac4"))
    v.set(-2, 1, -1, C("#ffd0e4"))
    v.set(-1, 1, -2, C("#ffd0e4"))
    v.set(-1, 2, -1, C("#ffe98a"))
    return v


def cloud():
    v = Vox()
    white = C("#ffffff")
    for (x, y, z, rx, ry, rz) in ((0, 2, 0, 9, 2.6, 5), (-6, 1.5, 1, 5, 2, 4), (6, 2.5, -1, 5, 2.5, 4), (1, 4, 0, 5, 2, 3)):
        lobe(v, x, y, z, rx, ry, rz, white, p=5.0)
    toplight(v, lambda c: True, [C("#b9cfee"), C("#d8e6fa"), C("#f2f7ff"), C("#ffffff")], sparkle=0)
    return v


def build(out):
    ps = PropSet(f"{out}/environment/a", BIG)
    ps.add("tree_oak", tree_oak(1))
    ps.add("tree_oak2", tree_blossom())
    ps.add("tree_pine", tree_pine())
    ps.add("tree_birch", tree_birch())
    ps.add("tree_big", tree_big())
    ps.add("sapling", sapling())
    ps.add("bush", bush())
    ps.add("bush_berry", bush(True, 15))
    ps.add("mushroom_big", mushroom_big())
    ps.add("rock_s", rock(Vox(), 0, 1.5, 0, 3.2, 2.6, 2.8, STONE, seed=2, moss=MOSS))
    ps.add("rock_m", rock(Vox(), 0, 2.5, 0, 5.5, 4.2, 4.6, STONE, seed=5, moss=MOSS))
    big = rock(Vox(), 0, 5, 0, 9.5, 8, 8.5, STONE, seed=8, moss=MOSS, cuts=6)
    rock(big, 8, 2, 5, 4.5, 3.6, 4, STONE, seed=9, moss=MOSS)
    ps.add("rock_l", big)
    ps.add("house", house())
    ps.add("well", well())
    ps.add("fence", fence())
    ps.add("shrine", shrine())
    ps.add("log", log())
    ps.add("stump", stump())
    ps.add("dock", dock())
    ps.add("lily", lily())
    ps.add("cloud", cloud(), 0.5)
    for name, vox in (("grass_tuft", grass()), ("grass_tall", grass(True, 4)), ("fern", fern()),
                      ("flower_red", flower(C("#ff4d5e"))), ("flower_yellow", flower(C("#ffd23a"), C("#ff9a2a"), 2)),
                      ("flower_blue", flower(C("#5aa0ff"), seed=3)), ("flower_white", flower(C("#ffffff"), seed=4)),
                      ("mushroom_red", mushrooms(C("#e8443a"))), ("signpost", signpost()),
                      ("lantern_post", lantern_post()), ("crate", crate()), ("barrel", barrel()),
                      ("campfire", campfire()), ("reeds", reeds())):
        ps.add(name, vox, FINE)
    ps.save()
