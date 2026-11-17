"""Direção D — Stylized Voxel RPG: ambiente.

Linguagem de forma: massas ARREDONDADAS em camadas (copas em lóbulos sobrepostos, pinheiros em saias
recortadas), gradiente pintado por voxel (base fria e escura -> topo quente e claro), detalhe concentrado
em pontos focais (janelas acesas, runas, lanternas, cogumelos luminosos) e áreas de descanso entre eles.

Escalas: 1/12 u nos volumes grandes, 1/16 u nos médios, 1/24 u nas plantas pequenas. Todos os volumes
são MACIÇOS: o mesher não elimina faces voltadas para voxels ausentes, então esvaziar o miolo dobraria
as faces. O build imprime as faces expostas de cada prop e falha se algum estourar o orçamento.
"""
import math

from chars_d import blob, faces, fill_pits, hash3, lerp, lock, ring, shade, stack, sup, tone_c
from voxlib import C, PropSet, Vox, mix, tone

BIG, MID, FINE = 1 / 12, 1 / 16, 1 / 24


def F(*hexes):
    return [C(h, "foliage") for h in hexes]


# rampas de folhagem: sombra fria azulada -> luz amarelada
OAK = F("#1d5a52", "#27774c", "#3b9645", "#5db342", "#8fcd49", "#c4e562")
OAK2 = F("#1f5f66", "#2a8068", "#3fa06a", "#62bd6c", "#98d67a", "#d2ee9a")
PINE = F("#123f4a", "#18584e", "#22724f", "#34904f", "#5aae58", "#9ad27a")
BIRCH = F("#3f7f3a", "#5f9f38", "#86bd3a", "#aed545", "#d3e85e", "#eef58a")
BUSH = F("#1f6048", "#2b7d45", "#429a40", "#66b741", "#98d04c")
GRASS = F("#2f8a44", "#4aa646", "#6cc04a", "#9cd957")
BLOSSOM = F("#f29ac0", "#ffc4dc", "#fff0f6")

BARK = C("#6b4a36")
BARK_D = C("#4a3230")
BARK_L = C("#8f6a48")
BIRCH_BARK = C("#ece7da")
STONE = C("#8d95a3")
STONE_D = C("#6f7888")
STONE_L = C("#bcc3c9")
MOSS = C("#5f9f3c")
WOOD = C("#a06c3e")
WOOD_D = C("#6e452a")
WOOD_L = C("#c6924f")
TIMBER = C("#57382a")
PLASTER = C("#f1e2c0")
SLATE = C("#3a6b8e")
IRON = C("#4a505e", "metal")
GOLD = C("#f2c14a", "metal")
WARM = C("#ffc65a", "emissive")
WARM_L = C("#ffe9a8", "emissive")
WARM_D = C("#ff9a3a", "emissive")
RUNE = C("#6fe8ff", "emissive")
RUNE_L = C("#d8fbff", "emissive")
VIOLET = C("#b48aff", "emissive")
WATER = C("#3f9be0", "water")


def is_leaf(c):
    return c[3] == "foliage"


# ----------------------------------------------------------------------------- vegetação: blocos de construção

def canopy(v, lobes, pal, seed=0, p=2.3, clumps=5, flat=1.0):
    """Copa em lóbulos. Cada lóbulo tem seu próprio gradiente (topo claro, barriga escura) somado ao
    gradiente global da copa: é o que faz as camadas lerem a distância. 'clumps' espalha pequenos tufos
    na superfície de cada lóbulo para quebrar a silhueta lisa."""
    info = {}
    allb = []
    for i, (cx, cy, cz, rx, ry, rz) in enumerate(lobes):
        allb.append((cx, cy, cz, rx, ry, rz))
        for j in range(clumps):
            a = hash3(i, j, 1, seed) * 2 * math.pi
            e = (hash3(i, j, 2, seed) - 0.25) * 1.3
            r = rx * (0.30 + 0.14 * hash3(i, j, 3, seed))
            ce = math.cos(e)
            allb.append((cx + math.cos(a) * ce * rx * 0.86, cy + math.sin(e) * ry * 0.8, cz + math.sin(a) * ce * rz * 0.86,
                         r, r * 0.8 * flat, r))
    for (cx, cy, cz, rx, ry, rz) in sorted(allb, key=lambda b: b[1]):
        for x in range(int(math.floor(cx - rx)), int(math.ceil(cx + rx)) + 1):
            ax = abs((x + 0.5 - cx) / rx) ** p
            if ax > 1:
                continue
            for y in range(int(math.floor(cy - ry)), int(math.ceil(cy + ry)) + 1):
                ay = abs((y + 0.5 - cy) / ry) ** p
                if ax + ay > 1:
                    continue
                for z in range(int(math.floor(cz - rz)), int(math.ceil(cz + rz)) + 1):
                    if ax + ay + abs((z + 0.5 - cz) / rz) ** p <= 1:
                        info[(x, y, z)] = (y + 0.5 - cy) / ry
    if not info:
        return v
    ymin = min(k[1] for k in info)
    ymax = max(k[1] for k in info)
    n = len(pal)
    for (x, y, z), rel in info.items():
        g = (y - ymin) / max(1, ymax - ymin)
        t = 0.46 + 0.26 * rel + 0.56 * (g - 0.5)
        if (x, y + 1, z) not in info:
            t += 0.12
        if (x, y - 1, z) not in info:
            t -= 0.20
        t += (hash3(x // 2, y // 2, z // 2, seed) - 0.5) * 0.14
        v.set(x, y, z, pal[max(0, min(n - 1, int(t * n)))])
    return v


def bark_col(x, y, z, base, seed=0):
    a = int((math.atan2(z + 0.5, x + 0.5) + math.pi) * 5)
    h = hash3(a, y // 5, 0, seed)
    return tone_c(base, -1.0) if h < 0.3 else (tone_c(base, 0.5) if h > 0.8 else base)


def tree_trunk(v, h, r0, r1, base=BARK, lean=(0.0, 0.0), roots=5, seed=0, flare=2.2, bend=0.0, root_len=1.0):
    """Tronco afilado com base alargada, curvatura e raízes que mergulham no chão."""
    for y in range(-2, h):
        u = max(0.0, y / max(1, h - 1))
        r = r1 + (r0 - r1) * (1 - u) ** 1.6 + flare * max(0.0, 1 - y / (r0 * 1.6)) ** 2
        ox = lean[0] * u + bend * math.sin(u * math.pi)
        oz = lean[1] * u
        for x in range(int(math.floor(ox - r)) - 1, int(math.ceil(ox + r)) + 1):
            for z in range(int(math.floor(oz - r)) - 1, int(math.ceil(oz + r)) + 1):
                if (x + 0.5 - ox) ** 2 + (z + 0.5 - oz) ** 2 <= r * r:
                    v.set(x, y, z, bark_col(x, y, z, base, seed))
    for i in range(roots):
        a = (i + 0.3 + 0.4 * hash3(i, 0, 0, seed)) * 2 * math.pi / roots
        L = r0 * (1.5 + 0.8 * hash3(i, 1, 0, seed)) * root_len
        tmp = Vox()
        lock(tmp, (math.cos(a) * r0 * 0.6, r0 * 1.2, math.sin(a) * r0 * 0.6),
             (math.cos(a) * (r0 + L * 0.5), r0 * 0.5, math.sin(a) * (r0 + L * 0.5)),
             (math.cos(a) * (r0 + L), -1.5, math.sin(a) * (r0 + L)), max(1.0, r0 * 0.42), 0.8, base)
        for (x, y, z) in tmp.v:
            if y >= -2:
                v.set(x, y, z, bark_col(x, y, z, base, seed + 1))
    return v


def branch(v, a, m, b, r0, r1=0.8, base=BARK, seed=0):
    tmp = Vox()
    lock(tmp, a, m, b, r0, r1, base)
    for (x, y, z) in tmp.v:
        v.set(x, y, z, tone_c(base, -1.0) if hash3(x, y // 2, z, seed) < 0.3 else base)


def moss_base(v, r, seed=0, col=MOSS):
    for (x, y, z), c in list(v.v.items()):
        if 0 <= y < 4 and c[3] == "matte" and hash3(x, y, z, seed) < 0.5 - y * 0.12 and (x, y + 1, z) not in v.v:
            v.v[(x, y, z)] = tone_c(col, 0.5 if hash3(x, 1, z, seed) > 0.5 else -0.5)


# ----------------------------------------------------------------------------- árvores

def tree_oak(seed=1):
    v = Vox()
    tree_trunk(v, 34, 3.4, 2.0, lean=(1.5, -1.0), roots=5, seed=seed, bend=1.2)
    branch(v, (1, 22, 0), (7, 27, 2), (11, 33, 4), 1.8)
    branch(v, (1, 20, 0), (-6, 26, -2), (-11, 31, -3), 1.8)
    branch(v, (1, 26, -1), (2, 31, -6), (3, 35, -10), 1.6)
    canopy(v, [(1, 43, 0, 13, 9, 13), (-10, 38, 4, 9, 6.5, 9), (11, 39, -3, 9.5, 6.5, 9), (3, 37, -11, 8, 5.5, 8),
               (2, 51, 1, 9.5, 6.5, 9.5), (-5, 47, -6, 8, 5.5, 8), (7, 46, 7, 7.5, 5, 7.5), (-3, 36, 11, 7, 4.5, 7)],
           OAK, seed=seed)
    moss_base(v, 3.4, seed)
    return v


def tree_oak2(seed=3):
    """Variante 'guarda-sol': copas achatadas em andares, verde mais azulado com flores claras."""
    v = Vox()
    tree_trunk(v, 36, 3.0, 1.7, base=C("#5f4238"), lean=(-2.5, 1.5), roots=4, seed=seed, bend=-1.6)
    branch(v, (-1, 20, 0), (-8, 24, 3), (-13, 29, 5), 1.7, base=C("#5f4238"))
    branch(v, (-1, 24, 1), (6, 28, -2), (11, 33, -5), 1.7, base=C("#5f4238"))
    canopy(v, [(-13, 33, 5, 10, 4.2, 9.5), (11, 37, -5, 10.5, 4.4, 10), (-3, 43, 1, 13, 5.2, 12.5),
               (-4, 50, 1, 9, 4.2, 9), (5, 30, 9, 6.5, 3.2, 6.5)],
           OAK2, seed=seed, clumps=6, flat=0.8)
    for (x, y, z), c in list(v.v.items()):                    # flores esparsas no topo dos andares
        if is_leaf(c) and (x, y + 1, z) not in v.v and hash3(x // 2, y, z // 2, 11) < 0.10:
            v.v[(x, y, z)] = BLOSSOM[int(hash3(x, y, z, 2) * 3)]
    moss_base(v, 3.0, seed)
    return v


def tree_pine(seed=2):
    """Pinheiro em saias sobrepostas com borda recortada e pontas caídas mais claras."""
    v = Vox()
    tree_trunk(v, 62, 2.6, 0.9, base=C("#5a3f33"), roots=4, seed=seed, flare=1.6)
    tiers = [(14, 13.5, 13), (23, 12.0, 12), (32, 10.4, 12), (41, 8.6, 11), (49, 6.8, 10), (56, 5.0, 9), (62, 3.2, 9)]
    n = len(PINE)
    for ti, (y0, R, hgt) in enumerate(tiers):
        lobes_n = 7 if R > 8 else 5
        ph = hash3(ti, 0, 0, seed) * 6.28
        for dy in range(-2, hgt):
            u = (dy + 2) / (hgt + 2)
            for x in range(int(-R) - 2, int(R) + 3):
                for z in range(int(-R) - 2, int(R) + 3):
                    d = math.hypot(x + 0.5, z + 0.5)
                    a = math.atan2(z + 0.5, x + 0.5)
                    sc = 0.5 + 0.5 * math.cos(a * lobes_n + ph)        # 1 na ponta do recorte
                    rr = R * (1 - u) ** 0.85 * (0.80 + 0.20 * sc)
                    droop = -2.2 * (d / R) ** 2 * sc
                    if d <= rr and dy >= droop + (d / R) * 1.0 - 2 + 2 * (1 - sc) * (d / R):
                        t = 0.20 + 0.5 * u + 0.30 * (d / max(1.0, rr)) ** 2 * (0.5 + sc) + 0.10 * ti / len(tiers)
                        if dy <= droop + 1:
                            t -= 0.25
                        t += (hash3(x // 2, y0 + dy, z // 2, seed) - 0.5) * 0.12
                        v.set(x, y0 + dy, z, PINE[max(0, min(n - 1, int(t * n)))])
    return v


def tree_birch(seed=5):
    v = Vox()
    h = 40
    tree_trunk(v, h, 1.9, 1.1, base=BIRCH_BARK, lean=(3.0, 1.0), roots=3, seed=seed, flare=1.2, bend=1.5)
    for (x, y, z), c in list(v.v.items()):
        if c[3] == "matte" and y > 1:
            hsh = hash3(0, y, int((math.atan2(z, x) + 4) * 1.2), seed)
            v.v[(x, y, z)] = C("#3a3436") if hsh < 0.10 else (C("#cfc8bc") if hash3(x, y // 3, z, 1) < 0.25 else BIRCH_BARK)
    branch(v, (2, 26, 0), (7, 30, 2), (10, 34, 3), 1.2, base=C("#bdb5a8"))
    branch(v, (2, 28, 1), (-3, 32, -1), (-7, 36, -2), 1.2, base=C("#bdb5a8"))
    canopy(v, [(3, 41, 1, 8.5, 6.5, 8.5), (10, 36, 3, 6, 4.5, 6), (-6, 38, -2, 6.5, 4.5, 6.5), (4, 48, 1, 6, 5, 6),
               (0, 34, 7, 5, 3.6, 5), (5, 44, -6, 5.5, 4, 5.5)], BIRCH, seed=seed, clumps=6)
    # ramos pendentes (leves, balançam)
    for i in range(14):
        a = i * 2.4
        x, z = round(3 + 8 * math.cos(a)), round(1 + 8 * math.sin(a))
        ys = [k[1] for k in v.v if k[0] == x and k[2] == z and is_leaf(v.v[k])]
        if ys:
            for d in range(1, 3 + int(hash3(i, 0, 0, seed) * 5)):
                v.set(x, min(ys) - d, z, BIRCH[1 if d % 2 else 2])
    return v


def tree_big(seed=9):
    """Árvore-marco: tronco largo com contrafortes, nicho de cristais, lanternas penduradas, cipós."""
    v = Vox()
    tree_trunk(v, 62, 8.5, 5.0, lean=(2.0, -1.0), roots=7, seed=seed, flare=4.0, bend=1.5)
    for a, m, b, r in (((2, 44, 0), (14, 50, 4), (24, 58, 7), 3.2), ((2, 40, 0), (-13, 48, 3), (-24, 55, 5), 3.2),
                       ((2, 48, -1), (4, 56, -13), (5, 62, -22), 3.0), ((2, 46, 2), (-3, 54, 12), (-5, 60, 20), 2.8),
                       ((2, 52, 0), (10, 62, -8), (14, 70, -12), 2.6)):
        branch(v, a, m, b, r, 1.2)
    lobes = [(2, 78, 0, 26, 15, 26), (-22, 64, 6, 15, 10, 15), (24, 66, 8, 15, 10, 15), (5, 66, -24, 15, 9.5, 14),
             (-6, 68, 22, 14, 9, 13), (-14, 84, -10, 15, 9.5, 15), (16, 86, 4, 15, 9.5, 15), (2, 94, 0, 13, 8, 13),
             (-26, 74, -12, 11, 7.5, 11), (27, 76, -10, 11, 7.5, 11), (20, 62, -14, 10, 6.5, 10), (-16, 60, 18, 10, 6.5, 10)]
    canopy(v, lobes, OAK, seed=seed, clumps=6)
    # cipós pendendo da borda da copa
    for i in range(46):
        a = hash3(i, 0, 0, seed) * 6.283
        rr = 16 + hash3(i, 1, 0, seed) * 16
        x, z = round(2 + rr * math.cos(a)), round(rr * math.sin(a))
        ys = [k[1] for k in v.v if k[0] == x and k[2] == z and is_leaf(v.v[k])]
        if ys:
            L = 4 + int(hash3(i, 2, 0, seed) * 12)
            for d in range(1, L):
                v.set(x, min(ys) - d, z, OAK[2] if d < L - 2 else OAK[4])
            if i % 6 == 0:
                v.set(x, min(ys) - L, z, BLOSSOM[1])
    # nicho na base com cristais e degraus de pedra
    for x in range(-4, 4):
        for y in range(0, 11):
            for z in range(4, 14):
                if (x + 0.5) ** 2 / 14 + (y - 2) ** 2 / 70 <= 1 and (x, y, z) in v.v:
                    del v.v[(x, y, z)]
    for x in range(-4, 4):
        for y in range(0, 11):
            zs = [k[2] for k in v.v if k[0] == x and k[1] == y and 0 < k[2] < 9]
            if zs and (x + 0.5) ** 2 / 14 + (y - 2) ** 2 / 70 <= 1:
                v.set(x, y, max(zs), BARK_D)
    for (x, z, h, c) in ((-2, 6, 6, RUNE), (0, 5, 9, RUNE), (1, 7, 4, VIOLET), (-1, 8, 3, RUNE_L), (2, 6, 5, RUNE)):
        v.box(x, 0, z, x, h - 1, z, c)
        v.set(x, h, z, RUNE_L)
    for x in range(-6, 6):
        for z in range(12, 17):
            if hash3(x // 3, 0, z // 2, 4) < 0.8:
                v.set(x, 0, z, STONE if hash3(x // 3, 1, z // 2, 4) < 0.5 else STONE_D)
    # cogumelos-prateleira luminosos subindo o tronco
    for i, (ang, y) in enumerate(((0.9, 12), (1.3, 18), (2.6, 15), (-0.7, 22), (-2.2, 10), (3.6, 26), (0.2, 30))):
        cx, cz = 2 * y / 62 + math.cos(ang) * 8.2 * (1 - y / 140), -y / 62 + math.sin(ang) * 8.2 * (1 - y / 140)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz <= 5:
                    v.set(round(cx + math.cos(ang) * 2) + dx, y, round(cz + math.sin(ang) * 2) + dz, C("#f0b46a"))
        v.set(round(cx + math.cos(ang) * 3), y + 1, round(cz + math.sin(ang) * 3), WARM)
    # lanternas penduradas nos galhos
    for (x, y, z, L) in ((18, 53, 5, 9), (-18, 51, 4, 12), (4, 58, -17, 10), (-4, 56, 15, 13), (12, 60, -8, 7)):
        for d in range(L):
            v.set(x, y - d, z, C("#3a2f2a"))
        v.box(x - 1, y - L - 3, z - 1, x + 1, y - L - 1, z + 1, WARM)
        v.set(x, y - L - 2, z, WARM_L)
        v.box(x - 1, y - L, z - 1, x + 1, y - L, z + 1, IRON)
        v.box(x - 1, y - L - 4, z - 1, x + 1, y - L - 4, z + 1, IRON)
    moss_base(v, 8.5, seed)
    return v


def sapling():
    v = Vox()
    tree_trunk(v, 13, 1.2, 0.7, lean=(1.0, 0.5), roots=0, seed=4, flare=0.6)
    canopy(v, [(1, 16, 0, 5.2, 4.4, 5.2), (-2, 13, 2, 3.4, 2.6, 3.4), (3, 13, -2, 3.0, 2.4, 3.0)], OAK[1:], seed=12, clumps=3)
    return v


def bush(berry=False, seed=14):
    v = Vox()
    lobes = [(0, 4.5, 0, 6.2, 5.2, 6.0), (5, 3, 2, 4.2, 3.6, 4.2), (-4.5, 3, -2, 4.0, 3.4, 4.0)]
    if berry:
        lobes = [(0, 4, 0, 6.0, 4.8, 5.6), (-5, 3.5, 2, 4.2, 3.8, 4.2), (4, 3, -3, 4.2, 3.4, 4.0)]
    canopy(v, lobes, BUSH, seed=seed, clumps=3)
    for k in [k for k in v.v if k[1] < 0]:
        del v.v[k]
    if berry:
        for (x, y, z) in list(v.v):
            if hash3(x, y, z, 3) < 0.035 and any((x + d[0], y + d[1], z + d[2]) not in v.v for d in ((0, 1, 0), (0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 0, -1))):
                v.set(x, y, z, C("#ff4a6a") if hash3(x, z, y, 1) < 0.7 else C("#ffb0c0"))
    else:
        for (x, y, z) in list(v.v):
            if (x, y + 1, z) not in v.v and hash3(x, y, z, 6) < 0.03:
                v.set(x, y, z, C("#fff6c8"))
    return v


def blade(v, x, z, h, lean, pal, w=0):
    for y in range(h):
        u = y / max(1, h - 1)
        dx = round(lean[0] * u * u)
        dz = round(lean[1] * u * u)
        v.set(x + dx, y, z + dz, pal[min(len(pal) - 1, int(u * len(pal)))])


def grass(tall=False, seed=1):
    v = Vox()
    for i in range(4 if tall else 5):
        a = i * 2 * math.pi / (4 if tall else 5) + hash3(i, 0, 0, seed)
        r = 1.2 + 1.6 * hash3(i, 1, 0, seed)
        h = (4 if tall else 3) + int(hash3(i, 2, 0, seed) * (3 if tall else 4))
        blade(v, round(r * math.cos(a)), round(r * math.sin(a)), h, (math.cos(a) * 1.8, math.sin(a) * 1.8), GRASS)
    if tall:
        for y in range(7):
            v.set(0, y, 0, GRASS[min(3, y // 2)])
        v.set(0, 7, 0, C("#f2e6a8", "foliage"))
    return v


def fern():
    v = Vox()
    pal = F("#1f6a48", "#2f8a4a", "#4aa84c", "#7cc858")
    for i in range(7):
        a = i * 2 * math.pi / 7 + 0.3
        L = 7 + (i % 2)
        for s in range(1, L + 1):
            u = s / L
            x, z = round(math.cos(a) * s), round(math.sin(a) * s)
            y = round(4.6 * math.sin(u * math.pi * 0.72))
            v.set(x, y, z, pal[min(3, int(u * 4))])
            if s % 2 == 0 and s < L:
                px, pz = round(-math.sin(a)), round(math.cos(a))
                v.set(x + px, y, z + pz, pal[1])
                v.set(x - px, y, z - pz, pal[1])
    return v


def flower(kind, seed=1):
    v = Vox()
    stem = GRASS[1]
    if kind == "red":                                         # papoulas em taça
        for (x, z, h) in ((0, 0, 7), (3, 2, 5)):
            v.box(x, 0, z, x, h - 1, z, stem)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v.set(x + d[0], h, z + d[1], C("#e8323c"))
                v.set(x + d[0], h + 1, z + d[1], C("#ff6a5a"))
            v.set(x, h, z, C("#2a1c2e"))
        v.set(1, 2, 0, GRASS[2])
    elif kind == "yellow":                                    # margaridas chatas
        for (x, z, h) in ((0, 0, 6), (-3, 2, 4), (2, -3, 5)):
            v.box(x, 0, z, x, h - 1, z, stem)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v.set(x + d[0], h, z + d[1], C("#ffd83a"))
            v.set(x, h, z, C("#ff8a2a"))
    elif kind == "blue":                                      # espiga de sininhos
        for (x, z, h) in ((0, 0, 9), (3, -2, 6)):
            v.box(x, 0, z, x, h, z, stem)
            for i, y in enumerate(range(h - 5 if h > 6 else 2, h + 1, 2)):
                d = ((1, 0), (0, 1), (-1, 0), (0, -1))[i % 4]
                v.set(x + d[0], y, z + d[1], C("#5a8cff") if i % 2 else C("#8ab8ff"))
            v.set(x, h + 1, z, C("#b8d8ff"))
        v.set(-1, 1, 0, GRASS[2])
    else:                                                     # tufo de florzinhas brancas
        for i in range(5):
            a = i * 1.257 + 0.4
            x, z = round(2.2 * math.cos(a)), round(2.2 * math.sin(a))
            h = 4 + (i * 2) % 3
            v.box(x, 0, z, x, h - 1, z, stem)
            v.set(x, h, z, C("#ffffff"))
            if i % 2 == 0:
                v.set(x, h + 1, z, C("#fff2a8"))
    return v


def mushrooms(cap=C("#e23a3a"), seed=1):
    v = Vox()
    for (x, z, h, r) in ((0, 0, 6, 3.6), (5, 2, 3, 2.4), (-3, 4, 2, 1.9)):
        v.box(x, 0, z, x, h, z, C("#f3e7cf"))
        if r > 3:
            v.box(x - 1, 0, z - 1, x, h - 1, z, C("#f3e7cf"))
            v.box(x - 1, 0, z - 1, x, 0, z, C("#d9c8a8"))
        tmp = Vox()
        sup(tmp, x, h, z, r, r * 0.85, r, cap, p=2.2, ymin=h)
        shade(tmp, amp=0.9)
        v.merge(tmp)
        ring(v, h, x, z, r - 0.8, r - 0.8, C("#f6e2c8"), 2.2)
        top = max(k[1] for k in tmp.v)
        v.set(x, top, z, C("#fff6e6"))
        if r > 3:
            v.set(x - 2, top - 1, z + 1, C("#fff6e6"))
            v.set(x + 2, top - 1, z - 1, C("#fff6e6"))
            v.set(x + 1, top - 2, z + 2, C("#fff6e6"))
    return v


def mushroom_big():
    """Cogumelo gigante: haste curva com saia, chapéu em gradiente e lamelas/pintas luminosas."""
    v = Vox()
    stemc = C("#efe4cc")
    st = Vox()
    stack(st, -1, 15, lambda u: (2.5 * math.sin(u * 2.2), 1.2 * u, lerp(3.4, 2.2, u ** 0.6), lerp(3.4, 2.2, u ** 0.6)), stemc, 2.2)
    ring(st, 10, 2.0, 0.8, 4.2, 4.2, C("#f8f0de"), 2.2)
    ring(st, 9, 2.0, 0.8, 3.4, 3.4, C("#f8f0de"), 2.2)
    shade(st, amp=0.9, lo=-1.5)
    v.merge(st)
    cap = Vox()
    cx, cz = 2.2, 1.2
    sup(cap, cx, 15, cz, 12.5, 9.5, 12.0, C("#7a5ad0"), p=2.15, ymin=15)
    pal = [C("#3f3a9a"), C("#5a48b8"), C("#7a5ad0"), C("#a070e0"), C("#d08ae8"), C("#f6b0e0")]
    for (x, y, z) in list(cap.v):
        t = (y - 15) / 9.5 + (hash3(x // 2, y, z // 2, 3) - 0.5) * 0.15 + (0.1 if (x, y + 1, z) not in cap.v else 0)
        cap.v[(x, y, z)] = pal[max(0, min(5, int(t * 6)))]
    for (sx, sy, sz, r) in ((7, 20, 5, 2.4), (-5, 19, 6, 2.0), (0, 23, -3, 2.6), (9, 18, -5, 1.9), (-7, 18, -6, 2.2), (3, 21, 9, 1.6),
                            (-3, 22, 1, 1.4)):
        for (x, y, z) in list(cap.v):
            if (x - sx) ** 2 + (y - sy) ** 2 + (z - sz) ** 2 <= r * r and (x, y + 1, z) not in cap.v or \
                    ((x - sx) ** 2 + (y - sy) ** 2 + (z - sz) ** 2 <= r * r and (x - sx) ** 2 + (z - sz) ** 2 > 1):
                if any((x + d[0], y + d[1], z + d[2]) not in cap.v for d in ((0, 1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))):
                    cap.v[(x, y, z)] = C("#9af6e8", "emissive")
    for (x, y, z) in list(cap.v):                             # lamelas: face de baixo com raios luminosos
        if y == 15:
            a = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
            cap.v[(x, y, z)] = C("#66d8d0", "emissive") if int((a + 4) * 5) % 3 == 0 else C("#e8dcf0")
    v.merge(cap)
    # filhote ao lado
    b = Vox()
    stack(b, 0, 5, lambda u: (-8, 5, 1.4, 1.4), stemc, 2.2)
    sup(b, -8, 6, 5, 4.2, 3.6, 4.2, pal[3], p=2.2, ymin=6)
    shade(b, amp=0.9)
    b.set(-8, 9, 5, C("#9af6e8", "emissive"))
    b.set(-10, 7, 6, C("#9af6e8", "emissive"))
    v.merge(b)
    return v


# ----------------------------------------------------------------------------- pedra

def stone_at(x, y, z, seed=0, sx=4, sy=3):
    """Padrão de alvenaria: (é_junta, tom) para a célula."""
    row = y // sy
    xo = x + (row % 2) * (sx // 2) + int(hash3(row, 0, 0, seed) * 2)
    joint = (y % sy == 0) or (xo % sx == 0)
    h = hash3(xo // sx, row, z // 5, seed)
    return joint, h


def masonry(x, y, z, seed=0, sx=4, sy=3, moss=0.0):
    joint, h = stone_at(x, y, z, seed, sx, sy)
    if joint:
        return STONE_D
    if moss and h > 1 - moss:
        return tone_c(MOSS, -0.5)
    return STONE_L if h < 0.2 else (tone_c(STONE, -0.5) if h < 0.45 else STONE)


def boulder(v, cx, cy, cz, rx, ry, rz, seed=0, cuts=5, p=2.5, moss=0.5):
    """Pedra facetada: superelipsoide cortado por planos, rampa por normal, musgo em MANCHAS no topo."""
    tmp = Vox()
    sup(tmp, cx, cy, cz, rx, ry, rz, STONE, p=p, ymin=-2)
    for i in range(cuts):
        a = hash3(i, 1, 0, seed) * 2 * math.pi
        up = 0.25 + hash3(i, 2, 0, seed) * 0.7
        n = (math.cos(a), up, math.sin(a))
        ln = math.sqrt(sum(k * k for k in n))
        n = tuple(k / ln for k in n)
        dist = 0.5 + hash3(i, 3, 0, seed) * 0.3
        for (x, y, z) in list(tmp.v):
            if ((x + 0.5 - cx) / rx) * n[0] + ((y + 0.5 - cy) / ry) * n[1] + ((z + 0.5 - cz) / rz) * n[2] > dist:
                del tmp.v[(x, y, z)]
    shade(tmp, amp=1.1, lo=-2.0, hi=1.5)
    for (x, y, z) in list(tmp.v):
        if (x, y + 1, z) not in tmp.v and y > cy - ry * 0.2:
            h = hash3(x // 3, 0, z // 3, seed + 7)
            if h < moss:
                tmp.v[(x, y, z)] = tone_c(MOSS, 0.5 if hash3(x // 2, 1, z // 2, seed) > 0.55 else -0.5)
                if h < moss * 0.2 and (x, y - 1, z) in tmp.v and hash3(x // 2, 2, z // 2, seed) < 0.35:
                    for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):      # musgo escorrendo na borda
                        if (x + d[0], y, z + d[1]) not in tmp.v:
                            tmp.v[(x, y - 1, z)] = tone_c(MOSS, -1.0)
    v.merge(tmp)
    return v


def rocks(size):
    v = Vox()
    if size == "s":
        boulder(v, 0, 2.6, 0, 5.2, 5.4, 4.6, seed=2, cuts=5)
        boulder(v, 5, 1.2, 2, 2.8, 2.6, 2.6, seed=3, cuts=3)
    elif size == "m":
        boulder(v, 0, 6, 0, 9.5, 10.5, 8.2, seed=5, cuts=6)
        boulder(v, -8, 2.5, 4, 4.8, 4.6, 4.4, seed=6, cuts=4)
        boulder(v, 7, 1.5, -4, 3.4, 3.2, 3.2, seed=7, cuts=3)
    else:
        boulder(v, 0, 10, 0, 12.5, 14.5, 11, seed=8, cuts=8, p=2.7, moss=0.6)
        boulder(v, 11, 4, 6, 7.4, 7.4, 6.4, seed=9, cuts=5)
        boulder(v, -10, 3, -5, 6.0, 5.6, 5.4, seed=10, cuts=4)
        for (x, y, z, c) in ((3, 0, 11, RUNE), (-4, 0, 10, VIOLET), (4, 0, 12, RUNE)):          # cristais na base
            ys = [k[1] for k in v.v if k[0] == x and k[2] == z]
            y0 = (max(ys) + 1) if ys else 0
            v.box(x, y0, z, x, y0 + 3, z, c)
            v.set(x + 1, y0, z, c)
            v.set(x, y0 + 4, z, RUNE_L)
    for k in [k for k in v.v if k[1] < -2]:
        del v.v[k]
    # tufos de grama no pé da pedra
    for i in range(6 if size != "s" else 3):
        a = i * 1.1 + 0.5
        b = v.bounds()
        r = (b[3] - b[0]) * 0.5
        x, z = round(math.cos(a) * r), round(math.sin(a) * r * 0.8)
        if (x, 0, z) not in v.v:
            v.set(x, 0, z, GRASS[1])
            v.set(x, 1, z, GRASS[3])
    return v


# ----------------------------------------------------------------------------- arquitetura

def tile_col(x, y, z, base=SLATE, seed=5):
    row = y // 2
    xo = x + (row % 2) * 2
    h = hash3(xo // 4, row, 0, seed)
    k = 0.75 if h > 0.8 else (-0.75 if h < 0.22 else 0)
    if y % 2 == 0:
        k -= 0.75
    if xo % 4 == 0:
        k -= 0.5
    if h > 0.955:
        return tone_c(MOSS, -0.5)
    return tone_c(base, max(-2.0, k + 0.25))


def tiled_gable(v, x0, x1, z0, z1, y0, rise, over=4, over_x=3, curve=1.15, base=SLATE, fill=PLASTER, seed=5):
    """Telhado de duas águas (cumeeira em X), telhas em fiadas de 2 voxels com junta deslocada.
    O sótão é maciço; só o beiral fica em casca. Devolve y da cumeeira."""
    zc = (z0 + z1 + 1) / 2.0
    half = (z1 - z0 + 1) / 2.0 + over
    top = y0
    for z in range(z0 - over, z1 + over + 1):
        d = abs(z + 0.5 - zc)
        t = max(0.0, 1 - d / half)
        yt = y0 + int(round(rise * t ** curve))
        top = max(top, yt)
        inside_z = z0 <= z <= z1
        for x in range(x0 - over_x, x1 + over_x + 1):
            inside = inside_z and x0 <= x <= x1
            for y in range(y0 if inside else max(y0 - 2, yt - 2), yt + 1):
                if y >= yt - 1:
                    c = tile_col(x, yt, z, base, seed)
                    if x in (x0 - over_x, x1 + over_x):
                        c = TIMBER
                else:
                    c = fill
                v.set(x, y, z, c)
    for x in range(x0 - over_x - 1, x1 + over_x + 2):          # cumeeira
        for z in (int(zc) - 1, int(zc)):
            v.set(x, top + 1, z, tone_c(base, -1.5) if (x // 3) % 2 else tone_c(base, -1.0))
    return top + 1


def window(v, x0, y0, x1, y1, z, shutters=None, box=True, dz=1):
    """Janela acesa em parede de plano z (frente +Z se dz=1): moldura, vidro emissivo em gradiente,
    pinázios, venezianas e floreira."""
    v.box(x0 - 1, y0 - 1, z, x1 + 1, y1 + 1, z, TIMBER)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            u = (y - y0) / max(1, y1 - y0)
            v.set(x, y, z, WARM_D if u < 0.3 else (WARM if u < 0.7 else WARM_L))
    xm, ym = (x0 + x1) // 2, (y0 + y1) // 2
    v.box(xm, y0, z, xm + (1 if (x1 - x0) % 2 else 0), y1, z, TIMBER)
    v.box(x0, ym, z, x1, ym, z, TIMBER)
    v.box(x0 - 2, y0 - 2, z + dz, x1 + 2, y0 - 2, z + dz, WOOD_L)       # peitoril
    if shutters:
        for xs in ((x0 - 4, x0 - 2), (x1 + 2, x1 + 4)):
            for x in range(xs[0], xs[1] + 1):
                for y in range(y0 - 1, y1 + 2):
                    v.set(x, y, z + dz, tone_c(shutters, -1.0) if y % 2 == 0 else shutters)
    if box:
        v.box(x0 - 2, y0 - 5, z + dz, x1 + 2, y0 - 3, z + 2 * dz, WOOD_D)
        for i, x in enumerate(range(x0 - 2, x1 + 3)):
            v.set(x, y0 - 2, z + 2 * dz, BUSH[2 + i % 3])
            if i % 2 == 0:
                v.set(x, y0 - 1, z + 2 * dz, (C("#ff6a8a"), C("#ffd84a"), C("#ffffff"))[(i // 2) % 3])
            if i % 3 == 0:
                v.set(x, y0 - 6, z + 2 * dz, BUSH[1])
                v.set(x, y0 - 5, z + 3 * dz, BUSH[2])


def house():
    v = Vox()
    X0, X1, Z0, Z1 = -27, 26, -21, 20
    W0, W1 = 4, 25                       # parede: y de W0 a W1
    # fundação de pedra (desce para assentar no relevo)
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            for y in range(-6, W0):
                edge = x in (X0 - 1, X1 + 1) or z in (Z0 - 1, Z1 + 1)
                v.set(x, y, z, masonry(x + (z if x in (X0 - 1, X1 + 1) else 0), y + 6, z, 2, 5, 3, moss=0.12) if edge else STONE_D)
    # paredes maciças de estuque, com manchas suaves
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not (x in (X0, X1) or z in (Z0, Z1)):
                v.box(x, W0, z, x, W1, z, PLASTER)
                continue
            for y in range(W0, W1 + 1):
                h = hash3(x // 3, y // 3, z // 3, 3)
                k = (-0.5 if h < 0.14 else 0) + (-0.5 if y < W0 + 4 else 0) + (0.5 if h > 0.9 else 0)
                v.set(x, y, z, tone_c(PLASTER, k))
    # enxaimel
    for (zp, dz) in ((Z1, 1), (Z0, -1)):
        for y in (W0, W0 + 1, W1 - 1, W1):
            v.box(X0, y, zp, X1, y, zp, TIMBER)
        for x in (X0, X0 + 1, X1 - 1, X1, -11, -10, 9, 10):
            v.box(x, W0, zp, x, W1, zp, TIMBER)
        for i in range(9):                                     # mãos-francesas
            v.box(X0 + 2 + i, W1 - 2 - i, zp, X0 + 3 + i, W1 - 2 - i, zp, TIMBER)
            v.box(X1 - 3 - i, W1 - 2 - i, zp, X1 - 2 - i, W1 - 2 - i, zp, TIMBER)
    for xp in (X0, X1):
        for y in (W0, W0 + 1, W1 - 1, W1, 14):
            v.box(xp, y, Z0, xp, y, Z1, TIMBER)
        for z in (Z0, Z0 + 1, Z1 - 1, Z1, -8, -7, 6, 7):
            v.box(xp, W0, z, xp, W1, z, TIMBER)
        for i in range(8):
            v.set(xp, 15 + i, Z0 + 2 + i, TIMBER)
            v.set(xp, 15 + i, Z1 - 2 - i, TIMBER)
    # telhado em fiadas + empenas
    top = tiled_gable(v, X0, X1, Z0, Z1, W1 + 1, 27)
    for xp in (X0, X1):
        for z in range(Z0, Z1 + 1):
            ys = [k[1] for k in v.v if k[0] == xp and k[2] == z and k[1] > W1]
            if not ys:
                continue
            yt = max(ys) - 2
            for y in range(W1 + 1, yt + 1):
                c = v.v.get((xp, y, z))
                if c is None or c == PLASTER or c[3] == "matte":
                    edge = y >= yt - 1 or abs(z + 0.5) < 1 or y in (W1 + 1, W1 + 2, 38)
                    if (xp, y, z) in v.v and v.v[(xp, y, z)] == PLASTER or (xp, y, z) in v.v and y < yt:
                        v.set(xp, y, z, TIMBER if edge else tone_c(PLASTER, -0.5 if hash3(y // 3, z // 3, 0, 7) < 0.15 else 0))
        # óculo do sótão
        for y in range(30, 36):
            for z in range(-3, 3):
                d = math.hypot(y - 32.5, z + 0.5)
                if d <= 3.2:
                    v.set(xp, y, z, TIMBER if d > 2.2 else (WARM_L if y > 32 else WARM))
    # janelas laterais
    for xp, s in ((X0, -1), (X1, 1)):
        for zc in (-15, 13):
            for y in range(9, 14):
                for z in range(zc - 2, zc + 3):
                    edge = y in (9, 13) or z in (zc - 2, zc + 2)
                    v.set(xp, y, z, TIMBER if edge else (WARM if y < 12 else WARM_L))
            v.box(xp + s, 8, zc - 3, xp + s, 8, zc + 3, WOOD_L)
    # porta em arco com moldura, ferragens e visor aceso
    door = C("#9a5a34")
    for x in range(-8, 8):
        for y in range(W0, W0 + 21):
            arch = (y - (W0 + 13)) > 0 and math.hypot(x + 0.5, (y - (W0 + 13))) > 7.6
            if arch:
                continue
            inner = abs(x + 0.5) < 6 and not ((y - (W0 + 13)) > 0 and math.hypot(x + 0.5, y - (W0 + 13)) > 5.6)
            if inner:
                c = tone_c(door, -1.0) if x % 3 == 0 else (tone_c(door, 0.5) if hash3(x, y // 4, 0, 1) > 0.8 else door)
                v.set(x, y, Z1, c)
            else:
                v.set(x, y, Z1, TIMBER)
                v.set(x, y, Z1 + 1, TIMBER)
    for y in (W0 + 3, W0 + 12):
        v.box(-5, y, Z1 + 1, 4, y, Z1 + 1, IRON)
    v.box(3, W0 + 8, Z1 + 1, 3, W0 + 9, Z1 + 1, GOLD)
    v.box(-2, W0 + 14, Z1, 1, W0 + 16, Z1, WARM)
    v.box(-1, W0 + 14, Z1, 0, W0 + 16, Z1, WARM_L)
    # degraus de pedra
    for (z1_, y1_, xw) in ((Z1 + 5, 3, 9), (Z1 + 8, 1, 11)):
        for x in range(-xw, xw):
            for z in range(Z1 + 1, z1_ + 1):
                for y in range(-3, y1_ + 1):
                    v.set(x, y, z, masonry(x, y + 3, z, 6, 5, 2) if (y == y1_ or z == z1_ or x in (-xw, xw - 1)) else STONE_D)
                if hash3(x // 2, 0, z // 2, 8) < 0.2:
                    v.set(x, y1_, z, STONE_L)
    # alpendre sobre a porta
    for z in range(Z1 + 1, Z1 + 8):
        y = W1 - (z - Z1) // 2
        for x in range(-11, 11):
            v.set(x, y, z, tile_col(x, y + (z % 2), z, SLATE, 9))
            v.set(x, y - 1, z, TIMBER if x in (-11, 10) or z == Z1 + 7 else tile_col(x, y, z, SLATE, 9))
    for xs in (-10, 9):
        for i in range(5):
            v.set(xs, W1 - 5 + i, Z1 + 1 + i, TIMBER)
            v.set(xs, W1 - 6 + i, Z1 + 1 + i, TIMBER)
    # lanterna ao lado da porta
    v.box(11, 19, Z1 + 1, 11, 19, Z1 + 3, IRON)
    v.box(11, 17, Z1 + 3, 11, 18, Z1 + 3, IRON)
    v.box(10, 13, Z1 + 2, 12, 16, Z1 + 4, WARM)
    v.box(11, 14, Z1 + 3, 11, 15, Z1 + 3, WARM_L)
    v.box(10, 17, Z1 + 2, 12, 17, Z1 + 4, IRON)
    v.box(10, 12, Z1 + 2, 12, 12, Z1 + 4, IRON)
    # janelas da frente com venezianas e floreiras; janelas de trás simples
    teal = C("#3f8f8a")
    window(v, -22, 11, -16, 19, Z1, shutters=teal)
    window(v, 15, 11, 21, 19, Z1, shutters=teal)
    window(v, -20, 11, -15, 18, Z0, box=False, dz=-1)
    window(v, 2, 11, 7, 18, Z0, box=False, dz=-1)
    # água-furtada na água da frente: caixinha com telhadinho próprio e janela acesa
    for x in range(-6, 6):
        ax = abs(x + 0.5)
        rtop = 47 - round(ax * 0.9)
        for z in range(2, 17):
            for y in range(34, rtop + 1):
                if (x, y, z) in v.v:
                    continue
                roofp = y >= rtop - 1
                if z == 16 and not roofp:
                    continue
                if roofp:
                    c = tile_col(z, y * 2 + (0 if ax > 2 else 1), 0, SLATE, 3) if z < 16 else TIMBER
                elif ax > 5:
                    continue
                else:
                    c = TIMBER if (ax > 4 or z == 15 and y < 37) else PLASTER
                v.set(x, y, z, c)
    for x in range(-3, 3):
        for y in range(37, 43):
            if (x, y, 15) in v.v:
                v.set(x, y, 15, TIMBER if (x in (-3, 2) or y in (37, 42)) else (WARM_L if y > 39 else WARM))
    # chaminé de pedra
    for x in range(12, 19):
        for z in range(-13, -6):
            for y in range(34, 61):
                edge = x in (12, 18) or z in (-13, -7)
                v.set(x, y, z, masonry(x + z, y, 0, 4, 4, 3, moss=0.08) if edge or y == 60 else STONE_D)
    v.box(11, 61, -14, 19, 62, -6, STONE_D)
    for x in range(11, 20):
        for z in range(-14, -5):
            if x in (11, 19) or z in (-14, -6):
                v.set(x, 62, z, STONE_L if (x + z) % 3 else STONE)
    v.box(13, 62, -12, 17, 62, -8, C("#2a2a30"))
    # hera subindo o canto esquerdo e escorrendo do beiral
    for y in range(W0, W1 + 6):
        for x in range(X0 - 1, X0 + 12):
            n = hash3(x // 2, y // 2, 0, 21)
            reach = 11 - (y - W0) * 0.32 + 3 * math.sin(y * 0.5)
            if x - X0 < reach and n < 0.62 and (x, y, Z1 + 1) not in v.v:
                v.set(x, y, Z1 + 1, BUSH[1 + int(hash3(x, y, 0, 4) * 4)])
                if n < 0.2:
                    v.set(x, y, Z1 + 2, BUSH[3])
    for z in range(Z1 - 10, Z1 + 1):
        for y in range(W0, W1 - 4):
            if hash3(y // 2, z // 2, 0, 22) < 0.45 - (Z1 - z) * 0.035:
                v.set(X0 - 1, y, z, BUSH[1 + int(hash3(z, y, 0, 4) * 4)])
    return v, top


def well():
    v = Vox()
    R = 7.4
    for y in range(-4, 8):
        for x in range(-9, 9):
            for z in range(-9, 9):
                d = math.hypot(x + 0.5, z + 0.5)
                if d <= R and (d >= R - 2.2 or y < 4):
                    a = int((math.atan2(z + 0.5, x + 0.5) + math.pi) * R)
                    v.set(x, y, z, masonry(a, y + 4, 0, 1, 4, 3, moss=0.15) if d >= R - 2.2 else STONE_D)
                elif d < R - 2.2 and y == 4:
                    v.set(x, y, z, WATER if hash3(x, 0, z, 1) > 0.12 else C("#8fd0ff", "water"))
    ring_top = [k for k in v.v if k[1] == 7]
    for k in ring_top:
        v.v[k] = STONE_L if hash3(k[0] // 2, 0, k[2] // 2, 3) < 0.6 else STONE
    for xs in (-8, 7):
        v.box(xs, 8, -1, xs, 17, 0, WOOD_D)
        v.box(xs, 8, -1, xs, 9, 0, TIMBER)
    v.box(-8, 14, -1, 7, 14, 0, WOOD)
    v.box(-10, 13, 0, -9, 15, 0, IRON)                         # manivela
    v.box(-11, 11, 0, -11, 13, 0, IRON)
    v.box(-3, 13, -1, 2, 15, 0, C("#d9c7a2"))                  # corda enrolada
    v.box(-1, 11, -1, 0, 12, 0, C("#d9c7a2"))
    for x in range(-2, 2):                                     # balde
        for z in range(-2, 2):
            for y in range(8, 11):
                if abs(x + 0.5) + abs(z + 0.5) < 3.2:
                    v.set(x, y, z, WOOD_L if (x + z) % 2 else WOOD)
    v.box(-2, 9, -2, 1, 9, 1, IRON)
    v.box(-1, 10, -1, 0, 10, 0, WATER)
    tiled_gable(v, -9, 8, -5, 4, 18, 8, over=2, over_x=1, curve=1.0, fill=WOOD_D, seed=7)
    v.box(-9, 17, -5, 8, 17, 4, WOOD_D)
    for k in [k for k in v.v if k[1] == 17 and -8 < k[0] < 7 and -4 < k[2] < 3]:
        del v.v[k]
    # musgo e samambaia no pé
    for i in range(10):
        a = i * 0.7
        x, z = round(math.cos(a) * (R + 0.6) - 0.5), round(math.sin(a) * (R + 0.6) - 0.5)
        if hash3(i, 0, 0, 2) < 0.6:
            v.set(x, 0, z, GRASS[1])
            v.set(x, 1, z, GRASS[3])
    return v


def fence():
    v = Vox()
    for i, x in enumerate((-15, -1, 13)):
        h = 14 + (i % 2)
        for y in range(-3, h):
            for dx in (0, 1):
                for dz in (0, 1):
                    v.set(x + dx, y, dz, tone_c(WOOD_D, -0.5 if hash3(x + dx, y // 3, dz, 1) < 0.3 else 0))
        v.box(x, h, 0, x + 1, h, 0, WOOD)
        v.set(x, h + 1, 0, WOOD)
    for (y, c, sl) in ((5, WOOD, 0.04), (10, WOOD_L, -0.03)):
        for x in range(-16, 16):
            yy = y + round(sl * x + 0.3 * math.sin(x * 0.7))
            v.set(x, yy, -1, tone_c(c, -0.5) if hash3(x // 4, y, 0, 2) < 0.3 else c)
            v.set(x, yy + 1, -1, c)
    for x in (-15, -1, 13):
        v.set(x, 6, -2, IRON)
        v.set(x, 11, -2, IRON)
    for (x, n) in ((-14, 5), (12, 3), (3, 2)):                 # trepadeira
        for y in range(n):
            v.set(x + (y % 2), y, 2, BUSH[1 + y % 3])
    v.set(-13, 5, 2, C("#ffd0e4"))
    return v


def signpost():
    v = Vox()
    for y in range(-3, 27):
        v.box(-1, y, -1, 0, y, 0, tone_c(WOOD_D, -0.5 if hash3(0, y // 3, 0, 1) < 0.3 else 0))
    v.box(-2, 0, -2, 1, 1, 1, STONE)
    v.box(-2, 0, -2, -1, 2, -1, STONE_D)
    v.box(-1, 27, -1, 0, 27, 0, WOOD)
    for (x0, x1, y, tip, c) in ((-2, 11, 20, 1, WOOD_L), (-12, 1, 13, -1, WOOD)):
        for x in range(x0, x1 + 1):
            for yy in range(y, y + 5):
                v.set(x, yy, 1, tone_c(c, -0.5) if yy == y or hash3(x // 5, yy, 0, 3) < 0.15 else c)
        tx = x1 + 1 if tip > 0 else x0 - 1
        v.box(tx, y + 1, 1, tx, y + 3, 1, c)
        v.set(tx + tip, y + 2, 1, c)
        for x in range(x0 + 3, x1 - 2, 2):
            v.set(x, y + 2, 2, TIMBER)
        v.set(0 if tip > 0 else -1, y + 2, 2, IRON)
    v.box(1, 28, -1, 2, 28, 0, C("#e8503a"))                   # passarinho no topo
    v.box(1, 29, -1, 1, 29, 0, C("#e8503a"))
    v.set(3, 28, 0, C("#ffc040"))
    v.set(1, 29, 0, C("#2a1c2e"))
    v.set(-2, 2, 2, GRASS[2])
    v.set(-2, 3, 2, GRASS[3])
    return v


def lantern(v, x, y, z):
    """Lanterna de ferro pendurada com topo em (x, y, z) (centro 2x2)."""
    v.box(x, y - 2, z, x + 1, y - 1, z + 1, IRON)
    v.box(x - 1, y - 3, z - 1, x + 2, y - 3, z + 2, IRON)
    v.box(x - 1, y - 8, z - 1, x + 2, y - 4, z + 2, WARM)
    v.box(x, y - 7, z, x + 1, y - 5, z + 1, WARM_L)
    for (dx, dz) in ((-1, -1), (2, -1), (-1, 2), (2, 2)):
        v.box(x + dx, y - 8, z + dz, x + dx, y - 4, z + dz, IRON)
    v.box(x - 1, y - 9, z - 1, x + 2, y - 9, z + 2, IRON)
    v.box(x, y - 10, z, x + 1, y - 10, z + 1, IRON)


def lantern_post():
    v = Vox()
    for y in range(-3, 31):
        lean = round(0.04 * y)
        v.box(-1 - lean, y, -1, -lean, y, 0, tone_c(WOOD_D, -0.5 if hash3(0, y // 4, 0, 1) < 0.3 else 0))
    for (x, z) in ((-3, -2), (1, -1), (-2, 1), (0, -3)):
        v.box(x, 0, z, x + 1, 1 + (x % 2), z + 1, STONE if (x + z) % 2 else STONE_D)
    for i in range(9):                                         # braço curvo
        y = 31 + round(2.4 * math.sin(i / 8 * math.pi * 0.6))
        v.box(-2 + i, y, -1, -2 + i, y + 1, 0, WOOD_D)
    v.set(7, 31, -1, WOOD_D)
    lantern(v, 5, 32, -1)
    v.box(-3, 20, -1, -2, 21, 0, IRON)
    for y in range(3):
        v.set(-2, y + 2, 1, BUSH[1 + y])
    return v


def crate():
    v = Vox()
    for x in range(-6, 6):
        for y in range(0, 12):
            for z in range(-6, 6):
                on = (x in (-6, 5)) + (y in (0, 11)) + (z in (-6, 5))
                if on >= 2:
                    c = WOOD_D
                elif on == 1:
                    plank = (y if x in (-6, 5) or z in (-6, 5) else x) // 3
                    c = tone_c(WOOD_L, -0.5) if plank % 2 else WOOD_L
                    if (y % 3 == 0 and y not in (0, 11)) and (x in (-6, 5) or z in (-6, 5)):
                        c = tone_c(WOOD, -0.5)
                else:
                    c = WOOD
                v.set(x, y, z, c)
    for (x, y, z) in ((-6, 0, -6), (5, 0, -6), (-6, 0, 5), (5, 0, 5), (-6, 11, -6), (5, 11, -6), (-6, 11, 5), (5, 11, 5)):
        sx, sz, sy = (1 if x < 0 else -1), (1 if z < 0 else -1), (1 if y == 0 else -1)
        v.set(x, y, z, IRON)
        v.set(x + sx, y, z, IRON)
        v.set(x, y, z + sz, IRON)
        v.set(x, y + sy, z, IRON)
    for i in range(1, 11):                                     # travessa diagonal na frente
        v.set(-6 + i, i, 5, WOOD_D)
    return v


def barrel():
    v = Vox()
    for y in range(15):
        u = y / 14
        r = 5.0 + 1.5 * math.sin(u * math.pi) ** 0.8
        for x in range(-8, 8):
            for z in range(-8, 8):
                d = math.hypot(x + 0.5, z + 0.5)
                if d <= r:
                    a = int((math.atan2(z + 0.5, x + 0.5) + math.pi) * 3.2)
                    c = tone_c(WOOD, (-0.5 if a % 2 else 0.25) + (u - 0.5))
                    if y in (2, 3, 11, 12):
                        c = IRON
                    if y == 14:
                        c = WOOD_D if d > r - 1.2 else (tone_c(WOOD_L, -0.5) if (x // 2) % 2 else WOOD_L)
                    v.set(x, y, z, c)
    v.box(-1, 7, 6, 0, 8, 6, C("#3a2a22"))                     # batoque
    return v


def shrine():
    """Santuário rúnico: estrado circular, monólito com runas, pilares em ruína e cristal flutuante."""
    v = Vox()
    for (r, y0, y1) in ((17.5, -4, 0), (15, 1, 1), (12.5, 2, 2)):
        for x in range(-18, 18):
            for z in range(-18, 18):
                d = math.hypot(x + 0.5, z + 0.5)
                if d <= r:
                    for y in range(y0, y1 + 1):
                        a = int((math.atan2(z + 0.5, x + 0.5) + math.pi) * r)
                        if d > r - 1.2:
                            c = masonry(a, y + 4, 0, 3, 5, 9, moss=0.12)
                        else:
                            rr = int(d / 3)
                            c = masonry(int((math.atan2(z + 0.5, x + 0.5) + math.pi) * (rr * 3 + 2)), rr * 9 + 1, 0, 4, 5, 9, moss=0.1)
                            if abs(d / 3 - round(d / 3)) < 0.17:
                                c = STONE_D
                        v.set(x, y, z, c)
    # círculo de runas no piso
    for i in range(64):
        a = i * 2 * math.pi / 64
        if i % 8 in (0, 1, 2, 4, 5):
            x, z = round(9.5 * math.cos(a) - 0.5), round(9.5 * math.sin(a) - 0.5)
            v.set(x, 2, z, RUNE)
    for i in range(4):
        a = i * math.pi / 2
        for r in (7, 8):
            v.set(round(r * math.cos(a) - 0.5), 2, round(r * math.sin(a) - 0.5), RUNE_L)
    # monólito
    for y in range(3, 25):
        u = (y - 3) / 21
        hw = 3.2 - 1.0 * u if y < 22 else (2.2 - (y - 21) * 0.7)
        hd = 2.2 - 0.6 * u if y < 22 else (1.6 - (y - 21) * 0.5)
        for x in range(-4, 4):
            for z in range(-3, 3):
                if abs(x + 0.5) <= hw and abs(z + 0.5) <= hd:
                    h = hash3(x // 2, y // 3, z // 2, 7)
                    v.set(x, y, z, STONE_L if h > 0.8 else (tone_c(STONE, -0.5) if h < 0.3 else STONE))
    v.box(-4, 3, -3, 3, 4, 2, STONE_D)
    glyph = ["..XX..", ".X..X.", ".X..X.", "..XX..", "..XX..", ".XXXX.", "X.XX.X", "..XX..", "..XX..", ".X..X.", "X....X"]
    for row, line in enumerate(glyph):
        for col, ch in enumerate(line):
            if ch == "X":
                y = 19 - row
                x = col - 3
                for zz, c in ((1, C("#2fb0d0", "emissive")), (-2, C("#2fb0d0", "emissive"))):
                    zs = [k[2] for k in v.v if k[0] == x and k[1] == y and -4 < k[2] < 4]
                    if zs:
                        v.set(x, y, max(zs) if zz > 0 else min(zs), RUNE if row in (3, 4) else c)
    # cristal flutuante + estilhaços
    for dy in range(-4, 5):
        r = 2.5 * (1 - abs(dy) / 4.8)
        for x in range(-4, 4):
            for z in range(-4, 4):
                if abs(x + 0.5) + abs(z + 0.5) <= r + 0.4:
                    v.set(x, 30 + dy, z, RUNE_L if (x < 0 and dy > 0) else RUNE)
    for (x, y, z) in ((5, 27, 1), (-6, 31, -2), (2, 33, -5), (-3, 26, 4)):
        v.box(x, y, z, x, y + 1, z, RUNE)
        v.set(x, y + 2, z, RUNE_L)
    # pilares em ruína
    for i, (a, h) in enumerate(((0.75, 19), (2.4, 9), (3.9, 14), (5.5, 5))):
        px, pz = round(11.5 * math.cos(a)), round(11.5 * math.sin(a))
        v.box(px - 2, 2, pz - 2, px + 2, 3, pz + 2, STONE_D)
        for y in range(4, 4 + h):
            for x in range(px - 1, px + 2):
                for z in range(pz - 1, pz + 2):
                    if y == 3 + h and hash3(x, y, z, i) < 0.4 and h < 19:
                        continue
                    v.set(x, y, z, masonry(x + z, y, 0, i, 3, 4, moss=0.14) if not (x == px and z == pz) else STONE_D)
        if h == 19:
            v.box(px - 2, 23, pz - 2, px + 2, 24, pz + 2, STONE_L)
            v.box(px - 1, 25, pz - 1, px + 1, 25, pz + 1, STONE)
            v.box(px, 26, pz, px, 28, pz, VIOLET)
            v.set(px, 29, pz, RUNE_L)
        else:
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (-1, -1)):
                if hash3(dx, dz, i, 5) < 0.75:
                    v.set(px + dx, 4 + h, pz + dz, tone_c(MOSS, 0.5 if (dx + dz) % 2 else -0.5))
    # blocos caídos, cristais brotando, musgo nas bordas
    for (x, z, w) in ((13, 4, 3), (-13, -8, 2), (-8, 13, 2)):
        ys = [k[1] for k in v.v if k[0] == x and k[2] == z]
        y0 = max(ys) + 1 if ys else 0
        v.box(x, y0, z, x + w, y0 + w - 1, z + w - 1, STONE)
        v.box(x, y0 + w - 1, z, x + w - 1, y0 + w - 1, z + w - 1, tone_c(MOSS, -0.5))
    for (x, z, h, c) in ((-6, 9, 5, VIOLET), (-7, 10, 3, VIOLET), (-5, 10, 2, RUNE), (9, -8, 4, RUNE), (10, -7, 2, RUNE_L)):
        ys = [k[1] for k in v.v if k[0] == x and k[2] == z]
        y0 = max(ys) + 1 if ys else 0
        v.box(x, y0, z, x, y0 + h - 1, z, c)
        v.set(x, y0 + h, z, RUNE_L)
    for (x, y, z), c in list(v.v.items()):
        if c[3] == "matte" and c not in (STONE_D,) and (x, y + 1, z) not in v.v and y <= 1 and hash3(x // 2, y, z // 2, 12) < 0.22:
            v.v[(x, y, z)] = tone_c(MOSS, 0.5 if hash3(x, z, y, 2) > 0.6 else -0.5)
    return v


def campfire():
    v = Vox()
    for i in range(9):
        a = i * 2 * math.pi / 9
        tmp = Vox()
        sup(tmp, math.cos(a) * 8, 1.2, math.sin(a) * 8, 2.4, 1.6 + 0.6 * (i % 2), 2.4, STONE, p=2.6, ymin=0)
        shade(tmp, amp=0.9)
        v.merge(tmp)
    for (a, b) in (((-5, 0, -4), (5, 3, 4)), ((-5, 0, 4), (5, 3, -4)), ((0, 0, -6), (0, 3, 5)), ((-6, 0, 0), (5, 2, 0))):
        tmp = Vox()
        blob(tmp, a, b, 1.3, 1.1, WOOD_D)
        for k in tmp.v:
            v.set(k[0], k[1], k[2], C("#3a2a26") if hash3(k[0], k[1], k[2], 1) < 0.35 else WOOD_D)
    v.box(-2, 0, -2, 1, 1, 1, C("#ff7a2a", "emissive"))          # brasa
    red, org, yel, wht = C("#ff5a2a", "emissive"), C("#ff9a2a", "emissive"), C("#ffd24a", "emissive"), C("#fff3b0", "emissive")
    for y in range(2, 17):
        u = (y - 2) / 14
        r = 4.2 * (1 - u ** 1.3) + 0.3
        ox, oz = 1.2 * math.sin(y * 0.7), 0.9 * math.cos(y * 0.9)
        for x in range(-6, 6):
            for z in range(-6, 6):
                d = math.hypot(x + 0.5 - ox, z + 0.5 - oz)
                if d <= r:
                    q = d / r + u * 0.8
                    v.set(x, y, z, wht if q < 0.35 else (yel if q < 0.75 else (org if q < 1.2 else red)))
    for (x, y, z) in ((3, 18, 1), (-2, 20, -1), (1, 22, 2)):
        v.set(x, y, z, org)
    return v


def log():
    v = Vox()
    for x in range(-13, 13):
        for y in range(0, 9):
            for z in range(-4, 5):
                d = math.hypot(y + 0.5 - 4.0, z + 0.5 - 0.5)
                if d <= 4.3:
                    if x in (-13, 12):
                        c = (WOOD_L if int(d) % 2 == 0 else WOOD) if d < 3.4 else BARK
                    else:
                        c = bark_col(z, x, y - 4, BARK, 3)
                    v.set(x, y, z, c)
    blob(v, (4, 6, 2), (7, 10, 5), 1.4, 0.9, BARK)
    for x in range(-11, 11):                                   # musgo por cima
        for z in range(-3, 4):
            ys = [k[1] for k in v.v if k[0] == x and k[2] == z]
            if ys and hash3(x // 2, 0, z, 5) < 0.45 - abs(z) * 0.08:
                v.set(x, max(ys), z, tone_c(MOSS, 0.5 if hash3(x, 1, z, 5) > 0.5 else -0.5))
    for (x, z) in ((-6, 3), (-3, 4), (8, -3)):                 # cogumelinhos luminosos
        v.box(x, 7, z, x, 9, z, C("#f3e7cf"))
        v.box(x - 1, 10, z - 1, x + 1, 10, z + 1, C("#6fe8ff", "emissive"))
        v.set(x, 11, z, RUNE_L)
    return v


def stump():
    v = Vox()
    tree_trunk(v, 8, 5.2, 4.8, roots=4, seed=3, flare=1.8, root_len=0.45)
    for x in range(-7, 7):
        for z in range(-7, 7):
            d = math.hypot(x + 0.5, z + 0.5)
            if d <= 4.9 and (x, 7, z) in v.v:
                v.set(x, 7, z, (WOOD if int(d * 1.2) % 2 else WOOD_L) if d < 4.0 else BARK_L)
    v.box(-5, 8, -1, -5, 9, 0, BARK)                           # lasca
    v.box(2, 8, 1, 2, 10, 1, GRASS[1])                         # broto
    v.set(3, 10, 1, GRASS[3])
    v.set(1, 11, 1, GRASS[3])
    moss_base(v, 5, 4)
    return v


def dock():
    v = Vox()
    for x in range(-3, 40):
        for z in range(-6, 6):
            plank = (x + 3) // 4
            if (x + 3) % 4 == 3 and hash3(plank, 0, 0, 2) < 0.9:
                continue                                       # fresta entre tábuas
            zoff = round((hash3(plank, 1, 0, 2) - 0.5) * 2)
            if -6 + max(0, zoff) <= z < 6 + min(0, zoff) + 0:
                v.set(x, 5, z, tone_c(WOOD_L if plank % 2 else WOOD, -0.5 if hash3(plank, z // 3, 0, 3) < 0.25 else 0))
    for z in (-5, 4):
        v.box(-3, 4, z, 39, 4, z, WOOD_D)
    for x in (0, 13, 26, 38):
        for z in (-6, 5):
            top = 9 if x in (0, 38) else 4
            for y in range(-12, top + 1):
                for dx in (0, 1):
                    v.set(x + dx, y, z, tone_c(TIMBER, -0.5 if y < 0 else 0))
            if top == 9:
                v.box(x, 10, z, x + 1, 10, z, WOOD)
    for x in range(2, 38):                                     # corda entre os postes (lado -z)
        sag = round(2.2 * math.sin(((x - 2) % 12.3) / 12.3 * math.pi))
        v.set(x, 9 - sag, -6, C("#d9c7a2"))
    # poste de lanterna na ponta + caixote + cabeço de amarração
    v.box(38, 10, 5, 39, 22, 5, TIMBER)
    v.box(34, 22, 5, 39, 23, 5, TIMBER)
    lantern(v, 34, 22, 4)
    for x in range(28, 33):
        for y in range(6, 11):
            for z in range(0, 5):
                on = (x in (28, 32)) + (y in (6, 10)) + (z in (0, 4))
                v.set(x, y, z, WOOD_D if on >= 2 else WOOD_L)
    v.box(20, 6, -4, 21, 8, -3, TIMBER)
    v.box(19, 9, -5, 22, 9, -2, WOOD_D)
    return v


def reeds():
    v = Vox()
    pal = F("#3f7a48", "#5a9a4a", "#7ab850", "#a8d460")
    for i in range(7):
        a = i * 0.9 + 0.3
        r = 1.0 + 3.5 * hash3(i, 0, 0, 3)
        x, z = round(r * math.cos(a)), round(r * math.sin(a))
        h = 11 + int(hash3(i, 2, 0, 3) * 9)
        lean = (hash3(i, 3, 0, 3) - 0.5) * 3
        for y in range(-4, h):
            u = max(0, y) / h
            v.set(x + round(lean * u * u), y, z, pal[min(3, int(u * 4))])
        if i % 2 == 0:
            xx = x + round(lean)
            v.box(xx, h, z, xx, h + 3, z, C("#6b3f26"))
            v.set(xx, h + 4, z, C("#c9b07a"))
    return v


def lily():
    v = Vox()
    pal = F("#2f8a5a", "#4aa860", "#72c468")
    for (cx, cz, r) in ((0, 0, 4.4), (6, 3, 2.6)):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for z in range(int(cz - r) - 1, int(cz + r) + 2):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                a = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
                if d <= r and not (abs(a - 0.6) < 0.28 and d > 0.8):
                    v.set(x, 0, z, pal[0] if d > r - 1 else (pal[2] if hash3(x, 0, z, 1) > 0.7 else pal[1]))
    for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        v.set(-1 + d[0], 1, -1 + d[1], C("#ff9ac4"))
    for d in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
        v.set(-1 + d[0], 1, -1 + d[1], C("#ffd0e4"))
    v.set(-1, 1, -1, C("#ffd0e4"))
    v.set(-1, 2, -1, C("#ffe98a"))
    return v


def cloud():
    v = Vox()
    for (x, y, z, rx, ry, rz) in ((0, 4, 0, 10, 3.6, 6), (-8, 3, 1, 6.5, 2.8, 5), (8, 3.5, -1, 7, 3.2, 5), (2, 6.5, 0, 6, 3.2, 4.5),
                                  (-3, 6, -1, 4.5, 2.6, 4), (12, 2.5, 1, 4, 2, 3.5)):
        sup(v, x, y, z, rx, ry, rz, C("#ffffff"), p=2.2)
    for k in [k for k in v.v if k[1] < 1]:
        del v.v[k]
    pal = [C("#a9b8e6"), C("#c9d6f4"), C("#e6eefc"), C("#ffffff"), C("#fffaf0")]
    for (x, y, z) in list(v.v):
        d = 0
        while d < 4 and (x, y + d + 1, z) in v.v:
            d += 1
        i = 4 if d == 0 and y > 5 else (3 if d <= 1 else (2 if d <= 3 else 1))
        if (x, y - 1, z) not in v.v:
            i = 0 if y <= 2 else 1
        v.v[(x, y, z)] = pal[i]
    return v


# ----------------------------------------------------------------------------- build

BUDGET = {"tree_big": 45000, "house": 40000, "tree_oak": 9000, "tree_oak2": 9000, "tree_pine": 9000, "tree_birch": 9000,
          "bush": 2000, "bush_berry": 2000, "grass_tuft": 150, "grass_tall": 150, "flower_red": 150,
          "flower_yellow": 150, "flower_blue": 150, "flower_white": 150}


def build(out):
    ps = PropSet(f"{out}/environment/d", BIG)
    print("env_d: faces expostas por prop")
    report = []

    def add(name, vox, vs):
        n = faces(vox)
        b = vox.bounds()
        lim = BUDGET.get(name)
        report.append(f"{name}={n}")
        print(f"  {name:14s} {n:6d} faces  {(b[3]-b[0]+1)*vs:.2f}x{(b[4]+1)*vs:.2f}x{(b[5]-b[2]+1)*vs:.2f} u  (voxel 1/{round(1/vs)})"
              + (f"  limite {lim}" if lim else ""))
        if lim and n > lim:
            raise ValueError(f"{name}: {n} faces excedem o orçamento de {lim}")
        ps.add(name, vox, vs)

    add("tree_oak", tree_oak(), BIG)
    add("tree_oak2", tree_oak2(), BIG)
    add("tree_pine", tree_pine(), BIG)
    add("tree_birch", tree_birch(), BIG)
    add("tree_big", tree_big(), BIG)
    add("sapling", sapling(), BIG)
    add("bush", bush(), 1 / 14)
    add("bush_berry", bush(True, 15), 1 / 14)
    add("fern", fern(), 1 / 20)
    add("grass_tuft", grass(), FINE)
    add("grass_tall", grass(True, 4), MID)
    add("flower_red", flower("red"), FINE)
    add("flower_yellow", flower("yellow"), FINE)
    add("flower_blue", flower("blue"), FINE)
    add("flower_white", flower("white"), FINE)
    add("mushroom_red", mushrooms(), FINE)
    add("mushroom_big", mushroom_big(), BIG)
    add("rock_s", rocks("s"), MID)
    add("rock_m", rocks("m"), MID)
    add("rock_l", rocks("l"), BIG)
    hv, _ = house()
    add("house", hv, BIG)
    add("well", well(), BIG)
    add("fence", fence(), MID)
    add("signpost", signpost(), MID)
    add("lantern_post", lantern_post(), MID)
    add("crate", crate(), MID)
    add("barrel", barrel(), MID)
    add("shrine", shrine(), BIG)
    add("campfire", campfire(), 1 / 20)
    add("log", log(), MID)
    add("stump", stump(), MID)
    add("dock", dock(), BIG)
    add("reeds", reeds(), MID)
    add("lily", lily(), FINE)
    add("cloud", cloud(), 0.25)
    ps.save()
