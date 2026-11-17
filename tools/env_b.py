"""Direção B — Storybook Voxel: ambiente artesanal e convidativo.

Linguagem de forma (o que separa B de A): massas REDONDAS (superelipsoide p≈2,2), copas como grandes
novelos, troncos em pera com curva em S, telhado côncavo com cumeeira selada, casa fora de prumo,
chaminé torta, cogumelos rechonchudos. Paleta quente e curta: verdes-oliva, coral, mostarda, creme,
pedra lilás-quente; sombras para o roxo, luzes para o pêssego (ver chars_b.shadow/light).

Voxel grande 1/8 u (árvores, casa, pedras) e fino 1/16 u (flores, objetos de mão).
"""
import math

from chars_b import GOLD, P, back_z, front_z, light, normal, rbox, sblob, shade, shadow, stack
from envlib import hash3
from voxlib import C, PropSet, Vox

BIG, FINE = 1 / 8, 1 / 16

LEAF = P("#7fae4a", "foliage")
LEAF_AUTUMN = P("#e89a3e", "foliage")
LEAF_PINE = P("#4a8a66", "foliage")
LEAF_BIRCH = P("#c2cc58", "foliage")
LEAF_BIG = P("#6fa44c", "foliage")
LEAF_BUSH = P("#6f9f4a", "foliage")
GRASS = P("#a9b854", "foliage")
BARK = P("#8f5d40")
BARK_D = P("#6f4336")
BARK_BIRCH = P("#f1e6cf")
BIRCH_MARK = P("#6b4a5e")
STONE = P("#b8aaa6")
STONE_D = P("#9a8a90")
STONE_L = P("#d2c6bc")
MOSS = P("#9ab552", "foliage")
WOOD = P("#b07a48")
WOOD_D = P("#84533a")
WOOD_L = P("#d3a066")
PLASTER = P("#f5e6c8")
PLASTER_D = P("#e6d0aa")
TIMBER = P("#7a4a3a")
ROOF = P("#cf5a44")
ROOF_D = P("#a8433f")
ROOF_L = P("#e6805a")
TEAL = P("#3f8a8c")
TEAL_D = P("#2f6a74")
CREAM = P("#f6ecd2")
STRAW = P("#e0b057")
STRAW_D = P("#bd8a44")
CORAL = P("#e2644c")
PINK = P("#f2a0a6")
YELLOW = P("#f4c653")
BLUE = P("#8098e0")
IRON = C("#5d4a5a", "metal")
GLOW = C("#ffd77a", "emissive")
GLOW_HI = C("#fff0c0", "emissive")
FIRE = C("#ff9340", "emissive")
FIRE_R = C("#f2603c", "emissive")
MAGIC = C("#45cdb8", "emissive")
MAGIC_HI = C("#a8f4e0", "emissive")
WATER = C("#6fb7c9", "water")


# ----------------------------------------------------------------------------- utilitários

def canopy_paint(v, base, seed=0, clump=3):
    """Pinta a folhagem em 4 tons por normal suavizada + manchas largas (pinceladas), sem pontilhado."""
    tones = (shadow(base, 1.7), shadow(base, 0.85), base, light(base, 1.25))
    d = v.v
    for (x, y, z), c in list(d.items()):
        if c != base:
            continue
        if ((x + 1, y, z) in d and (x - 1, y, z) in d and (x, y + 1, z) in d and (x, y - 1, z) in d
                and (x, y, z + 1) in d and (x, y, z - 1) in d):
            d[(x, y, z)] = tones[1]
            continue
        n = normal(v, x, y, z)
        L = 0.75 * n[1] + 0.25 * (-0.36 * n[0] + 0.8 * n[1] + 0.48 * n[2])
        L += (hash3(x // clump, y // clump, z // clump, seed) - 0.5) * 0.30
        d[(x, y, z)] = tones[3] if L > 0.62 else (tones[2] if L > 0.10 else (tones[1] if L > -0.42 else tones[0]))
    return v


def balls(v, lobes, base, seed=0, p=2.15, clump=3):
    tmp = Vox()
    for (x, y, z, rx, ry, rz) in lobes:
        sblob(tmp, x, y, z, rx, ry, rz, base, p=p)
    canopy_paint(tmp, base, seed, clump)
    v.merge(tmp)
    return v


def trunk_b(v, h, r0, r1, bark=BARK, path=lambda t: (0.0, 0.0), flare=2.4, roots=5, y0=0, streak=BARK_D, seed=0):
    """Tronco em pera: base larga que afina depressa, eixo curvo, raízes bojudas."""
    t_ = Vox()
    for y in range(y0 - 3, y0 + h):
        t = max(0.0, (y - y0) / max(1, h - 1))
        r = r1 + (r0 - r1) * (1 - t) ** flare
        cx, cz = path(t)
        stack(t_, y, y, lambda yy: (cx, cz, r, r), bark, p=2.0)
    for i in range(roots):
        a = (i + 0.3 + 0.4 * hash3(i, 0, 0, seed)) * 2 * math.pi / max(1, roots)
        sblob(t_, math.cos(a) * r0 * 1.05, y0 + 0.4, math.sin(a) * r0 * 1.05, r0 * 0.5, r0 * 0.55, r0 * 0.5, bark, p=2.0)
    for (x, y, z) in list(t_.v):
        if hash3(x, y // 5, z, seed + 3) < 0.16:
            t_.v[(x, y, z)] = streak
    shade(t_)
    v.merge(t_)
    return v


def branch(v, a, b, r, col=BARK):
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))) + 1
    for i in range(n + 1):
        t = i / n
        sblob(v, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, r, r, r, col, p=2.0)


def stones(v, pred=None, seed=0, cell=(3, 2, 3)):
    """Dá a um volume de pedra a leitura de 'pedras assentadas': blocos largos em 3 tons."""
    for (x, y, z), c in list(v.v.items()):
        if c != STONE or (pred and not pred(x, y, z)):
            continue
        off = 1 if (y // cell[1]) % 2 else 0
        k = hash3((x + off) // cell[0], y // cell[1], (z + off) // cell[2], seed)
        v.v[(x, y, z)] = STONE_D if k < 0.28 else (STONE_L if k > 0.78 else STONE)
    return v


def moss_top(v, amount=0.5, seed=0, col=MOSS, mats=("matte",)):
    for (x, y, z), c in list(v.v.items()):
        if c[3] in mats and (x, y + 1, z) not in v.v and hash3(x // 2, y, z // 2, seed) < amount:
            v.v[(x, y, z)] = col if hash3(x, y, z, seed + 1) < 0.7 else light(col)
    return v


def clip_ground(v, y_min=0):
    for k in [k for k in v.v if k[1] < y_min]:
        del v.v[k]
    return v


def top_y(v, x, z):
    ys = [k[1] for k in v.v if k[0] == x and k[2] == z]
    return max(ys) if ys else None


# ----------------------------------------------------------------------------- árvores

def tree_oak(seed=1):
    v = Vox()
    trunk_b(v, 20, 3.6, 1.7, path=lambda t: (2.2 * math.sin(t * 2.6), 0.8 * t), seed=seed)
    branch(v, (1, 14, 0), (6, 19, -2), 1.1)
    branch(v, (1, 13, 0), (-6, 18, 2), 1.1)
    shade(v)
    balls(v, [(1, 27, 0, 11, 9, 10.5), (-7.5, 22, 3, 6.5, 5.5, 6.5), (8, 23, -3, 7, 6, 6.5),
              (2.5, 33, -1, 7, 5.5, 7), (-3, 24, -7, 6, 5, 5.5)], LEAF, seed)
    return v


def tree_autumn():
    """Árvore de outono: copa em guarda-chuva, âmbar — o acento quente da floresta."""
    v = Vox()
    trunk_b(v, 20, 3.2, 1.5, bark=BARK_D, streak=BARK, path=lambda t: (-2.6 * math.sin(t * 2.2), -0.6 * t), seed=4)
    branch(v, (-2, 15, 0), (-8, 20, 1), 1.0, BARK_D)
    branch(v, (-2, 14, 0), (5, 20, -1), 1.0, BARK_D)
    shade(v)
    balls(v, [(-2, 27, 0, 12, 7, 10.5), (-9, 22, 1, 6, 4.6, 6), (6.5, 22.5, -1, 6.5, 5, 6), (-1, 32.5, 0, 7.5, 5, 7)],
          LEAF_AUTUMN, 5)
    return v


def tree_pine(seed=2):
    """Pinheiro de conto: saias arredondadas empilhadas e a ponta dobrada."""
    v = Vox()
    trunk_b(v, 12, 2.6, 1.5, bark=BARK_D, streak=BARK, roots=4, seed=seed)
    tmp = Vox()
    y = 7
    for i, R in enumerate((10.5, 9.0, 7.4, 5.6, 3.8)):
        hgt = 11 if i < 4 else 9
        bend = 0.22 * i * i
        for dy in range(hgt):
            k = dy / hgt
            r = R * (1 - k ** 1.5) ** 0.75 + 0.6
            stack(tmp, y + dy, y + dy, lambda yy: (bend + 0.15 * dy * (i >= 3), 0.0, r, r), LEAF_PINE, p=2.1)
        y += hgt - 3
    sblob(tmp, 4.8, y + 1.0, 0, 1.4, 1.4, 1.4, LEAF_PINE)
    canopy_paint(tmp, LEAF_PINE, seed, clump=3)
    v.merge(tmp)
    return v


def tree_birch():
    v = Vox()
    trunk_b(v, 25, 1.9, 1.0, bark=BARK_BIRCH, streak=BARK_BIRCH, roots=3,
            path=lambda t: (1.6 * math.sin(t * 4.2), 0.0), flare=3.0, seed=6)
    for y in (3, 7, 12, 16, 20):
        t = y / 24.0
        cx = round(1.6 * math.sin(t * 4.2))
        v.box(cx - 1, y, 0, cx, y, 1, BIRCH_MARK)
        v.set(cx + (1 if y % 2 else -2), y + 1, 0, BIRCH_MARK)
    balls(v, [(0, 27, 0, 6.5, 5.8, 6.2), (-4, 22.5, 1.5, 4.6, 3.8, 4.4), (4.5, 22, -1.5, 4.2, 3.6, 4.0),
              (1, 32.5, 0, 4.4, 3.6, 4.2)], LEAF_BIRCH, 7)
    return v


def tree_big():
    """Árvore-marco: tronco bojudo com porta redonda, lanternas penduradas e copa em novelos."""
    v = Vox()
    trunk_b(v, 38, 9.0, 4.6, path=lambda t: (2.5 * math.sin(t * 2.4), -1.5 * t), roots=7, flare=2.2, seed=9)
    branch(v, (2, 26, 0), (14, 36, 4), 2.0)
    branch(v, (2, 24, 0), (-13, 34, 3), 2.0)
    branch(v, (2, 28, -2), (4, 37, -13), 1.8)
    shade(v)
    # porta redonda + janela + degrau
    zf = front_z(v, 0, 3)
    for x in range(-4, 4):
        for y in range(0, 9):
            if ((x + 0.5) / 4.0) ** 2 + max(0.0, (y - 3.5) / 5.2) ** 2 <= 1.0:
                edge = ((x + 0.5) / 3.0) ** 2 + max(0.0, (y - 3.5) / 4.2) ** 2 > 1.0
                for z in range(zf - 4, zf + 1):
                    v.set(x, y, z, None)
                v.box(x, y, zf - 2, x, y, zf - 1, CREAM if edge else TEAL)
    v.set(1, 4, zf, GOLD)
    for x in (-2, 0):
        v.box(x, 0, zf - 1, x, 7, zf - 1, TEAL_D)
    rbox(v, -5, 0, zf, 4, 0, zf + 3, STONE_L, r=0)
    for (wx, wy) in ((6, 13),):
        z = front_z(v, wx, wy)
        v.box(wx - 1, wy - 1, z - 1, wx + 2, wy + 2, z, CREAM)
        v.box(wx, wy, z - 1, wx + 1, wy + 1, z, GLOW)
    for (x, y, z) in ((-9, 9, 1), (-8, 13, -3), (7, 18, -4)):       # cogumelos-prateleira
        zz = z
        v.box(x, y, zz, x + 2, y, zz + 3, STRAW)
        v.box(x, y + 1, zz + 1, x + 1, y + 1, zz + 2, light(STRAW))
    balls(v, [(1, 52, -1, 22, 15, 20), (-15, 43, 5, 11.5, 9, 11), (16, 45, -2, 11.5, 9.5, 11),
              (4, 44, -15, 10.5, 8.5, 10), (-5, 62, -2, 13, 9, 12.5), (10, 60, 3, 10, 7.5, 10), (-2, 42, 13, 9, 7, 8)],
          LEAF_BIG, 10, clump=4)
    # lanternas penduradas na copa
    for (x, z, drop) in ((-14, 8, 7), (13, 6, 9), (2, 15, 6), (17, -8, 6)):
        ys = [k[1] for k in v.v if k[0] == x and k[2] == z and k[1] > 30]
        if not ys:
            continue
        y0 = min(ys)
        v.box(x, y0 - drop, z, x, y0 - 1, z, WOOD_D)
        v.box(x - 1, y0 - drop - 3, z - 1, x + 1, y0 - drop - 1, z + 1, GLOW)
        v.box(x, y0 - drop - 2, z, x, y0 - drop - 2, z, GLOW_HI)
        v.box(x - 1, y0 - drop, z - 1, x + 1, y0 - drop, z + 1, TEAL_D)
        v.set(x, y0 - drop - 4, z, TEAL_D)
    return v


def sapling():
    v = Vox()
    trunk_b(v, 6, 1.2, 0.7, roots=0, path=lambda t: (0.8 * t, 0))
    balls(v, [(1, 9, 0, 4.2, 4.0, 4.2), (-2, 6.5, 1, 2.4, 2.0, 2.4)], LEAF, 12, clump=2)
    return v


def bush(berry=False, seed=14):
    v = Vox()
    balls(v, [(0, 2.6, 0, 5.6, 4.2, 5.2), (4.2, 2.0, 2.2, 3.6, 3.2, 3.6), (-3.8, 1.8, -2.4, 3.2, 2.8, 3.2)],
          LEAF_BUSH, seed, clump=2)
    clip_ground(v)
    if berry:
        for (x, y, z) in list(v.v):
            if hash3(x // 2, y // 2, z // 2, 3) < 0.16 and (x % 2 == 0 and z % 2 == 0) and (
                    (x, y + 1, z) not in v.v or (x, y, z + 1) not in v.v):
                v.set(x, y, z, CORAL if hash3(x, y, z, 4) < 0.7 else light(CORAL))
    else:
        for (x, z) in ((1, 2), (-3, 0), (4, -1)):
            y = top_y(v, x, z)
            if y is not None:
                v.set(x, y + 1, z, CREAM)
    return v


# ----------------------------------------------------------------------------- plantas pequenas (1/16)

def grass(tall=False, seed=1):
    v = Vox()
    tones = (shadow(GRASS, 1.2), GRASS, light(GRASS, 1.3))
    n = 7 if tall else 5
    for i in range(n):
        a = i * 2.4 + hash3(i, 0, 0, seed) * 0.8
        rad = 0.6 + 3.2 * hash3(i, 1, 0, seed)
        x0, z0 = math.cos(a) * rad, math.sin(a) * rad
        h = (5 if tall else 2) + int(hash3(i, 2, 0, seed) * (5 if tall else 3))
        for y in range(h):
            t = y / h
            x = round(x0 + math.cos(a) * 2.2 * t * t)
            z = round(z0 + math.sin(a) * 2.2 * t * t)
            c = tones[0] if t < 0.3 else (tones[1] if t < 0.75 else tones[2])
            v.set(x, y, z, c)
            if t < 0.34 and tall:
                v.set(x + 1, y, z, c)
    return v


def fern():
    v = Vox()
    g = P("#6f9f58", "foliage")
    tones = (shadow(g), g, light(g, 1.2))
    for i in range(7):
        a = i * 2 * math.pi / 7 + 0.3
        for s in range(1, 9):
            x, z = round(math.cos(a) * s), round(math.sin(a) * s)
            y = round(4.0 * math.sin(s / 8.5 * math.pi))
            c = tones[1] if s < 5 else tones[2]
            v.set(x, y, z, c)
            if 2 <= s <= 6:
                px, pz = round(-math.sin(a)), round(math.cos(a))
                v.set(x + px, y, z + pz, tones[0] if s % 2 else c)
                v.set(x - px, y, z - pz, tones[0] if s % 2 else c)
    return v


def flower(petal, center=YELLOW, seed=1, cup=False):
    """Três flores gordinhas por touceira; a corola é um disco legível de cima."""
    v = Vox()
    stem = P("#7fa650", "foliage")
    pl = light(petal, 1.2)
    for i in range(3):
        x = int(hash3(i, 0, 0, seed) * 9) - 4
        z = int(hash3(i, 1, 0, seed) * 9) - 4
        h = 3 + int(hash3(i, 2, 0, seed) * 4)
        v.box(x, 0, z, x, h - 1, z, stem)
        v.set(x + (1 if i % 2 else -1), max(1, h // 2), z, stem)
        if cup:
            v.box(x - 1, h, z - 1, x + 1, h + 1, z + 1, petal)
            v.set(x, h + 1, z, center)
            v.set(x - 1, h + 2, z, pl)
            v.set(x + 1, h + 2, z, pl)
            v.set(x, h + 2, z - 1, pl)
            v.set(x, h + 2, z + 1, pl)
        else:
            v.box(x - 1, h, z - 1, x + 1, h, z + 1, petal)
            for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                v.set(x + dx, h, z + dz, pl)
            v.set(x, h, z, center)
            v.set(x, h + 1, z, center)
    return v


def mushroom(v, x, z, h, r, cap, stem=CREAM, spots=True, lean=0.0):
    """Cogumelo rechonchudo: pé gordo, chapéu em cúpula com aba e pintas."""
    tmp = Vox()
    stack(tmp, 0, h, lambda y: (x + lean * y / max(1, h), z, max(0.9, r * 0.42 - 0.03 * y) + (0.5 if y < 2 else 0),
                                max(0.9, r * 0.42 - 0.03 * y) + (0.5 if y < 2 else 0)), stem, p=2.1)
    c = Vox()
    cx = x + lean
    sblob(c, cx, h, z, r, r * 0.78, r, cap, p=2.2)
    for k in [k for k in c.v if k[1] < h]:
        del c.v[k]
    stack(c, h - 1, h - 1, lambda y: (cx, z, r * 0.8, r * 0.8), shadow(stem, 0.6), p=2.2)
    shade(tmp)
    shade(c)
    if spots:
        n = 5 + int(r)
        for i in range(n):
            a = i * 2.4 + 0.6
            rad = r * (0.12 + 0.70 * ((i * 0.381966) % 1.0))
            sx, sz = cx + math.cos(a) * rad, z + math.sin(a) * rad
            sr = max(0.8, r * (0.13 + 0.07 * (i % 3)))
            for (px, py, pz) in list(c.v):
                if py >= h and ((px, py + 1, pz) not in c.v or (px, py, pz + 1) not in c.v or (px + 1, py, pz) not in c.v
                                or (px - 1, py, pz) not in c.v or (px, py, pz - 1) not in c.v):
                    if (px + 0.5 - sx) ** 2 + (pz + 0.5 - sz) ** 2 <= sr * sr:
                        c.v[(px, py, pz)] = CREAM
    tmp.merge(c)
    v.merge(tmp)
    return v


def mushrooms_red():
    v = Vox()
    mushroom(v, 0, 0, 5, 4.2, CORAL)
    mushroom(v, 5, 3, 3, 2.8, CORAL)
    mushroom(v, -4, 3, 2, 2.2, light(CORAL))
    return v


def mushroom_big():
    v = Vox()
    cap = P("#e8806a")
    mushroom(v, 0, 0, 10, 9.5, cap, lean=1.5)
    mushroom(v, -7, 4, 3, 3.4, cap)
    for (x, z) in ((5, 5), (6, 4), (-3, -6)):
        v.box(x, 0, z, x, 1, z, GRASS)
    return v


def reeds():
    v = Vox()
    g = P("#8fa858", "foliage")
    head = P("#8a5540")
    for i in range(7):
        x = int(hash3(i, 0, 0, 3) * 11) - 5
        z = int(hash3(i, 1, 0, 3) * 11) - 5
        h = 11 + int(hash3(i, 2, 0, 3) * 10)
        bend = 1 if hash3(i, 4, 0, 3) > 0.5 else -1
        for y in range(-3, h + 1):
            xx = x + (bend if y > h * 0.6 else 0) + (bend if y > h * 0.88 else 0)
            v.set(xx, y, z, shadow(g) if y < 3 else (g if y < h - 2 else light(g)))
        if i % 2 == 0:
            xx = x + bend
            v.box(xx, h - 5, z, xx + 1, h - 1, z + 1, head)
            v.set(xx, h, z, light(head))
    return v


def lily():
    v = Vox()
    g = P("#7fb070", "foliage")
    for x in range(-4, 4):
        for z in range(-4, 4):
            d = math.hypot(x + 0.5, z + 0.5)
            if d <= 3.7 and not (x >= 0 and z in (0, -1) and x > abs(z)):
                v.set(x, 0, z, light(g) if d > 2.8 else g)
    v.box(-2, 1, -2, -1, 1, -1, PINK)
    v.set(-2, 2, -2, light(PINK))
    v.set(-1, 2, -1, YELLOW)
    for (x, z) in ((-3, -2), (0, -1), (-2, 0), (-1, -3)):
        v.set(x, 1, z, light(PINK))
    return v


# ----------------------------------------------------------------------------- pedras

def boulder(parts, seed=0, moss=0.5):
    v = Vox()
    for (x, y, z, rx, ry, rz) in parts:
        sblob(v, x, y, z, rx, ry, rz, STONE, p=2.3, p_lo=3.0)
    for (x, y, z) in list(v.v):                    # veios largos: duas famílias de tom
        k = hash3((x + y) // 5, (y - z) // 4, (z + x) // 5, seed)
        if k < 0.22:
            v.v[(x, y, z)] = STONE_D
    shade(v)
    for (x, y, z), c in list(v.v.items()):
        if (x, y + 1, z) not in v.v and normal(v, x, y, z)[1] > 0.72 and hash3(x // 3, 0, z // 3, seed + 5) < moss:
            v.v[(x, y, z)] = MOSS if hash3(x, 1, z, seed) < 0.65 else light(MOSS)
    clip_ground(v)
    return v


# ----------------------------------------------------------------------------- casa

def house():
    v = Vox()
    x0, x1, z0, z1 = -18, 17, -14, 13
    wy0, wy1 = 3, 16
    # fundação de pedra
    rbox(v, x0 - 1, -5, z0 - 1, x1 + 1, wy0 - 1, z1 + 1, STONE, r=0)
    stones(v, seed=2)
    # paredes de estuque, cantos amaciados, leve barriga
    rbox(v, x0, wy0, z0, x1, wy1, z1, PLASTER, r=0)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        v.box(x, wy0, z, x, wy1, z, None)
    v.paint(lambda x, y, z, c: PLASTER_D if c == PLASTER and hash3(x // 4, y // 3, z // 4, 3) < 0.16 else None)
    # enxaimel: prumos de canto, frechal, travessas curvas
    for (x, z) in ((x0 + 1, z1), (x1 - 1, z1), (x0 + 1, z0), (x1 - 1, z0), (x0, z1 - 1), (x0, z0 + 1), (x1, z1 - 1), (x1, z0 + 1)):
        v.box(x, wy0, z, x, wy1, z, TIMBER)
    for (x, z) in ((x0 + 2, z1), (x1 - 2, z1), (0, z1), (1, z1), (x0 + 2, z0), (x1 - 2, z0), (x0, z1 - 2), (x0, z0 + 2), (x1, z1 - 2),
                   (x1, z0 + 2), (x0, 0), (x1, 0)):
        v.box(x, wy0, z, x, wy1, z, TIMBER)
    for z in (z0, z1):
        v.box(x0 + 1, wy1, z, x1 - 1, wy1, z, TIMBER)
        v.box(x0 + 1, wy0, z, x1 - 1, wy0, z, TIMBER)
    for x in (x0, x1):
        v.box(x, wy1, z0 + 1, x, wy1, z1 - 1, TIMBER)
        v.box(x, wy0, z0 + 1, x, wy0, z1 - 1, TIMBER)
    for i in range(5):                              # mãos-francesas curvas
        v.set(x0 + 3 + i, wy1 - 1 - (i * i) // 4, z1, TIMBER)
        v.set(x1 - 3 - i, wy1 - 1 - (i * i) // 4, z1, TIMBER)
    # porta em arco (assimétrica, à esquerda)
    dx0, dx1 = -11, -4
    for x in range(dx0 - 1, dx1 + 2):
        for y in range(wy0, wy0 + 14):
            u = (x + 0.5 - (dx0 + dx1 + 1) / 2.0)
            inside = abs(u) <= 4.0 and (y < wy0 + 8 or (u / 4.0) ** 2 + ((y - wy0 - 8) / 4.2) ** 2 <= 1.0)
            frame = abs(u) <= 5.0 and (y < wy0 + 8 or (u / 5.0) ** 2 + ((y - wy0 - 8) / 5.4) ** 2 <= 1.0)
            if inside:
                v.set(x, y, z1, TEAL if int(u + 4) % 3 else TEAL_D)
            elif frame:
                v.set(x, y, z1, STONE_L)
                v.set(x, y, z1 + 1, STONE_L if (x + y) % 3 else STONE)
    v.set(dx1 - 1, wy0 + 5, z1 + 1, GOLD)
    v.box(dx0 + 2, wy0 + 8, z1, dx0 + 5, wy0 + 9, z1, GLOW)      # postigo iluminado
    v.box(dx0 + 3, wy0 + 8, z1, dx0 + 4, wy0 + 9, z1, GLOW_HI)
    rbox(v, dx0 - 2, 0, z1 + 2, dx1 + 2, 2, z1 + 5, STONE_L, r=0)
    rbox(v, dx0 - 1, 0, z1 + 6, dx1 + 1, 1, z1 + 7, STONE, r=0)
    # lanterna ao lado da porta
    v.box(dx1 + 3, 13, z1 + 1, dx1 + 3, 13, z1 + 3, TIMBER)
    v.box(dx1 + 3, 12, z1 + 3, dx1 + 3, 12, z1 + 3, IRON)
    v.box(dx1 + 2, 9, z1 + 2, dx1 + 4, 11, z1 + 4, GLOW)
    v.set(dx1 + 3, 10, z1 + 3, GLOW_HI)
    v.box(dx1 + 2, 12, z1 + 2, dx1 + 4, 12, z1 + 4, IRON)
    v.box(dx1 + 2, 8, z1 + 2, dx1 + 4, 8, z1 + 4, IRON)
    # janela frontal com venezianas e floreira
    wx0, wx1 = 5, 12
    v.box(wx0 - 1, 7, z1, wx1 + 1, 14, z1, TIMBER)
    v.box(wx0, 8, z1, wx1, 13, z1, GLOW)
    v.box(wx0 + 1, 9, z1, wx1 - 1, 12, z1, GLOW_HI)
    v.box(wx0 + 3, 8, z1, wx0 + 4, 13, z1, TIMBER)
    v.box(wx0, 10, z1, wx1, 10, z1, TIMBER)
    for sx in (wx0 - 3, wx1 + 2):
        v.box(sx, 7, z1 + 1, sx + 1, 14, z1 + 1, TEAL)
        v.box(sx, 10, z1 + 1, sx + 1, 10, z1 + 1, TEAL_D)
    v.box(wx0 - 1, 5, z1 + 1, wx1 + 1, 6, z1 + 2, WOOD_D)
    for i, x in enumerate(range(wx0 - 1, wx1 + 2)):
        v.set(x, 7, z1 + 2, LEAF_BUSH)
        if i % 2 == 0:
            v.set(x, 8, z1 + 2, (PINK, YELLOW, CORAL)[(i // 2) % 3])
    # janelas laterais e dos fundos
    for xs in (x0, x1):
        for wz in (-9, 4):
            v.box(xs, 7, wz - 1, xs, 13, wz + 5, TIMBER)
            v.box(xs, 8, wz, xs, 12, wz + 4, GLOW)
            v.box(xs, 8, wz + 2, xs, 12, wz + 2, TIMBER)
    v.box(-4, 7, z0, 4, 13, z0, TIMBER)
    v.box(-3, 8, z0, 3, 12, z0, GLOW)
    v.box(0, 8, z0, 0, 12, z0, TIMBER)

    # telhado côncavo com cumeeira selada e beiral largo
    ov, steep, curve = 3, 1.08, 0.5
    zc = (z0 + z1) / 2.0
    half = (z1 - z0) / 2.0 + ov

    def roof_h(x, z):
        u = (x - (x0 - ov)) / float(x1 - x0 + 2 * ov)
        sag = -2.6 * math.sin(math.pi * u) + 2.0 * abs(2 * u - 1) ** 3
        t = max(0.0, 1 - abs(z + 0.5 - zc - 0.5) / half)
        return wy1 + 1 + int(round(half * steep * t ** (1 + curve) + sag * (0.35 + 0.65 * t)))

    for x in range(x0 - ov, x1 + ov + 1):
        for z in range(z0 - ov, z1 + ov + 1):
            yt = roof_h(x, z)
            lo = min(yt, roof_h(x, z - 1), roof_h(x, z + 1)) - 1
            for y in range(max(wy1, lo - 1), yt + 1):
                if y % 3 == 0:
                    c = ROOF_D
                else:
                    k = hash3((x + (y // 3) * 2) // 4, y // 3, 0, 5)
                    c = ROOF_L if k > 0.72 else ROOF
                if x in (x0 - ov, x1 + ov):
                    c = TIMBER
                v.set(x, y, z, c)
    zr = int(math.floor(zc))
    for x in range(x0 - ov, x1 + ov + 1):                    # cumeeira
        yt = max(roof_h(x, zr), roof_h(x, zr + 1))
        v.box(x, yt + 1, zr, x, yt + 1, zr + 1, ROOF_D if x % 4 else TIMBER)
    for xs in (x0, x1):                                      # empenas
        for z in range(z0 + 1, z1):
            for y in range(wy1 + 1, roof_h(xs, z) - 1):
                v.set(xs, y, z, TIMBER if z in (zr, zr + 1) or y == wy1 + 6 else PLASTER)
    for x in range(x0 + 1, x1):                              # forro (fecha o sótão)
        for z in range(z0 + 1, z1):
            v.set(x, wy1 + 1, z, PLASTER_D)
    # óculo redondo na empena direita
    for z in range(-3, 4):
        for y in range(20, 27):
            d = math.hypot(z + 0.0, y - 23.0)
            if d <= 3.2:
                v.set(x1, y, z, GLOW if d <= 2.2 else TIMBER)
    v.box(x1, 23, -2, x1, 23, 2, TIMBER)
    # musgo no beiral
    for (x, y, z), c in list(v.v.items()):
        if c in (ROOF, ROOF_L, ROOF_D) and (x, y + 1, z) not in v.v and y < wy1 + 8:
            if hash3(x // 2, 0, z // 2, 21) < 0.30 and x < 0 and y < wy1 + 6:
                v.v[(x, y, z)] = MOSS if hash3(x, y, z, 2) < 0.7 else light(MOSS)
    # chaminé torta de pedra
    ch = Vox()
    for y in range(22, 41):
        lean = int(round((y - 22) * 0.16))
        w = 2 if y < 38 else 3
        ch.box(9 + lean - w, y, -8 - w, 9 + lean + w, y, -8 + w, STONE)
    for (x, y, z) in list(ch.v):
        if y >= 38:
            ch.v[(x, y, z)] = STONE_D if y == 38 else STONE_L
    lean = int(round(18 * 0.16))
    ch.box(9 + lean - 1, 41, -9, 9 + lean + 1, 42, -7, CORAL)
    ch.box(9 + lean, 42, -8, 9 + lean, 42, -8, IRON)
    stones(ch, seed=4, cell=(2, 2, 2))
    v.merge(ch)
    # trepadeira no canto esquerdo
    for y in range(wy0, wy1 + 4):
        for d in range(4):
            if hash3(y, d, 0, 6) < 0.72 - 0.02 * y:
                x = x0 + d + (y // 6)
                v.set(x, y, z1 + 1, LEAF_BUSH if hash3(y, d, 1, 6) < 0.6 else light(LEAF_BUSH))
                if hash3(y, d, 2, 6) < 0.12:
                    v.set(x, y, z1 + 2, PINK)
    # fora de prumo: cisalha em X conforme a altura
    out = Vox()
    for (x, y, z), c in v.v.items():
        out.v[(x + int(round(max(0, y - 3) * 0.08)), y, z)] = c
    return out


# ----------------------------------------------------------------------------- construções pequenas

def well():
    v = Vox()
    for y in range(-3, 6):
        r_out = 6.2 + (0.5 if y in (4, 5) else 0)
        for x in range(-8, 8):
            for z in range(-8, 8):
                d = math.hypot(x + 0.5, z + 0.5)
                if 3.6 <= d <= r_out:
                    v.set(x, y, z, STONE)
                elif d < 3.6 and y == 3:
                    v.set(x, y, z, WATER)
    stones(v, seed=1, cell=(3, 2, 3))
    moss_top(v, 0.3, 4)
    for xs, lean in ((-6, -1), (5, 1)):
        for y in range(6, 15):
            v.box(xs + (lean if y > 11 else 0), y, -1, xs + (lean if y > 11 else 0), y, 0, WOOD_D)
    v.box(-6, 13, 0, 5, 13, 0, WOOD)
    v.box(-1, 7, 0, 0, 12, 0, CREAM)                      # corda
    rbox(v, -2, 4, -1, 1, 7, 2, WOOD, r=0)                # balde
    v.box(-2, 6, -1, 1, 6, 2, IRON)
    v.box(-1, 7, 0, 0, 7, 1, WATER)
    # telhadinho de palha em cúpula
    roof = Vox()
    sblob(roof, 0, 15, 0, 9.0, 5.5, 8.0, STRAW, p=2.0)
    clip_ground(roof, 15)
    stack(roof, 14, 14, lambda y: (0, 0, 9.4, 8.4), STRAW_D, p=2.0)
    for (x, y, z) in list(roof.v):
        if roof.v[(x, y, z)] == STRAW and hash3(x // 2, y, z // 3, 8) < 0.25:
            roof.v[(x, y, z)] = STRAW_D
    shade(roof)
    roof.box(-1, 20, -1, 0, 21, 0, CORAL)
    v.merge(roof)
    return v


def fence():
    v = Vox()
    for i, x in enumerate((-8, -1, 6)):
        lean = (-1, 0, 1)[i]
        h = (8, 9, 7)[i]
        for y in range(-2, h):
            xx = x + (lean if y > 4 else 0)
            v.box(xx, y, 0, xx + 1, y, 1, WOOD_D if y < h - 1 else WOOD)
        v.set(x + (lean if h > 5 else 0), h, 0, WOOD)
    for x in range(-8, 8):                                # réguas levemente seladas
        sag = 1 if -4 <= x <= -2 or 3 <= x <= 5 else 0
        v.set(x, 5 - sag, 0, WOOD_L)
        v.set(x, 2 - (1 if 2 <= x <= 4 else 0), 0, WOOD)
    for (x, y) in ((-5, 6), (-4, 6), (-4, 7), (2, 3)):
        v.set(x, y - (1 if x < -2 else 0), 0, LEAF_BUSH)
    v.set(-4, 7, 1, PINK)
    return v


def signpost():
    v = Vox()
    for y in range(-2, 23):
        x = int(round(math.sin(y / 22.0 * 2.2) * 1.4))
        v.box(x - 1, y, -1, x, y, 0, WOOD_D)
    v.box(0, 23, -1, 1, 23, 0, WOOD)
    for (y0, dirx, col) in ((16, 1, CREAM), (10, -1, WOOD_L)):
        for i in range(11):
            x = (1 + i) * dirx + (0 if dirx > 0 else -1)
            tilt = i // 5 if dirx > 0 else -(i // 6)
            hh = 4 if i < 9 else (2 if i == 9 else 0)
            v.box(x, y0 + tilt + (2 - hh // 2 if hh < 4 else 0), 1, x, y0 + tilt + (2 + hh // 2 if hh < 4 else 4), 1, col)
        for i in range(2, 8):
            x = (1 + i) * dirx + (0 if dirx > 0 else -1)
            v.set(x, y0 + 2 + (i // 5 if dirx > 0 else -(i // 6)), 2, TIMBER)
    v.box(-2, 0, -2, 1, 0, 1, STONE)
    v.set(1, 24, -1, CORAL)                               # passarinho
    v.set(1, 24, 0, CORAL)
    v.set(1, 25, 0, CORAL)
    v.set(1, 25, 1, YELLOW)
    return v


def lantern_post():
    v = Vox()
    for y in range(-2, 24):
        v.box(-1, y, -1, 0, y, 0, WOOD_D)
    for (x, y) in ((0, 24), (1, 25), (2, 26), (3, 26), (4, 26), (5, 25)):   # cajado de pastor
        v.box(x, y, -1, x + 1, y, 0, WOOD_D)
    rbox(v, -3, 0, -3, 2, 1, 2, STONE, r=0)
    stones(v, seed=3, cell=(2, 1, 2))
    v.box(5, 22, -1, 6, 24, 0, IRON)
    v.box(3, 21, -3, 8, 21, 2, TEAL_D)
    v.box(4, 22, -2, 7, 22, 1, TEAL)
    v.box(4, 15, -2, 7, 20, 1, GLOW)
    v.box(5, 16, -2, 6, 19, 1, GLOW_HI)
    for (x, z) in ((4, -2), (7, -2), (4, 1), (7, 1)):
        v.box(x, 15, z, x, 20, z, IRON)
    v.box(4, 14, -2, 7, 14, 1, TEAL_D)
    v.set(5, 13, -1, GOLD)
    return v


def crate():
    v = Vox()
    rbox(v, -5, 0, -5, 4, 9, 4, WOOD_L, r=0)
    for (x, y, z) in list(v.v):
        on = (x in (-5, 4)) + (y in (0, 9)) + (z in (-5, 4))
        if on >= 2:
            v.v[(x, y, z)] = WOOD_D
        elif on == 1 and (y in (3, 6) and y not in (0, 9)):
            v.v[(x, y, z)] = WOOD
    for (x, y, z) in ((-5, 9, -5), (4, 9, -5), (-5, 9, 4), (4, 9, 4), (-5, 0, -5), (4, 0, -5), (-5, 0, 4), (4, 0, 4)):
        v.set(x, y, z, None)
    v.box(-4, 9, -4, 3, 9, 3, STRAW)                      # palha e maçãs
    for (x, z, c) in ((-2, -1, CORAL), (1, 1, CORAL), (0, -3, YELLOW), (-3, 2, CORAL)):
        v.box(x, 10, z, x + 1, 11, z + 1, c)
        v.set(x, 12, z, LEAF_BUSH)
        v.set(x + 1, 11, z + 1, light(c))
    return v


def barrel():
    v = Vox()
    for y in range(13):
        r = 4.0 + 1.5 * math.sin(math.pi * (y + 0.5) / 13.0)
        for x in range(-6, 6):
            for z in range(-6, 6):
                d = math.hypot(x + 0.5, z + 0.5)
                if d <= r:
                    if y in (2, 10):
                        c = IRON
                    else:
                        c = WOOD_D if int((math.atan2(z + 0.5, x + 0.5) + math.pi) * 2.6) % 3 == 0 else WOOD
                    v.set(x, y, z, c)
    v.paint(lambda x, y, z, c: WOOD_L if y == 12 and math.hypot(x + 0.5, z + 0.5) < 3.4 else None)
    shade(v)
    return v


def shrine():
    """Portal-lua: anel de pedra musgosa sobre um estrado redondo, com um cristal que flutua no vão."""
    v = Vox()
    for y, r in ((-3, 14.5), (-2, 14.5), (-1, 14.5), (0, 14.5), (1, 12.5), (2, 10.5)):
        stack(v, y, y, lambda yy: (0, 0, r, r * 0.86), STONE, p=2.3)
    cy = 14.5
    for x in range(-12, 12):
        for y in range(3, 28):
            d = math.hypot(x + 0.5, y + 0.5 - cy)
            if 7.6 <= d <= 11.2:
                broken = (x > 3 and y > 21 and d > 8.8 and (x + y) % 5 < 3 and x < 8)
                if not broken:
                    v.box(x, y, -2, x, y, 1, STONE)
    for (x, y, z) in list(v.v):
        if v.v[(x, y, z)] == STONE and y >= 3:
            a = math.atan2(y + 0.5 - cy, x + 0.5)
            k = int((a + math.pi) / (2 * math.pi) * 14)
            if k % 2:
                v.v[(x, y, z)] = STONE_L if hash3(k, 0, 0, 3) > 0.4 else STONE_D
    stones(v, pred=lambda x, y, z: y < 3, seed=7)
    # runas no anel
    for k in range(10):
        a = math.pi * (0.08 + 0.84 * k / 9.0)
        x, y = round(math.cos(a) * 9.4 - 0.5), round(cy + math.sin(a) * 9.4 - 0.5)
        if (x, y, 1) in v.v:
            v.set(x, y, 2, MAGIC)
            if k % 3 == 0:
                v.set(x, y - 1, 2, MAGIC)
    # cristal flutuante + fagulhas
    sblob(v, 0, cy - 0.5, -0.5, 2.6, 4.6, 2.6, MAGIC, p=1.3)
    sblob(v, 0, cy - 0.5, -0.5, 1.3, 2.6, 1.3, MAGIC_HI, p=1.3)
    for (x, y, z) in ((-4, 19, 0), (4, 11, -1), (3, 20, 0), (-5, 10, 0)):
        v.set(x, y, z, MAGIC_HI)
    # pedras-lanterna, blocos caídos, musgo e flores
    for sx in (-12, 11):
        rbox(v, sx - 1, 1, 4, sx + 1, 5, 6, STONE_L, r=0)
        v.box(sx, 3, 6, sx, 4, 6, GLOW)
        v.box(sx - 2, 6, 3, sx + 2, 6, 7, STONE_D)
        v.box(sx - 1, 7, 4, sx + 1, 7, 6, STONE_D)
    rbox(v, 7, 3, 4, 10, 5, 7, STONE, r=0)
    rbox(v, 12, 1, -6, 15, 3, -3, STONE_D, r=0)
    shade(v)
    moss_top(v, 0.38, 12)
    for (x, z) in ((-7, 6), (5, 8), (-9, -5), (9, 2)):
        y = top_y(v, x, z)
        if y is not None:
            v.set(x, y + 1, z, LEAF_BUSH)
            v.set(x, y + 2, z, PINK if x < 0 else CREAM)
    return v


def campfire():
    v = Vox()
    for i in range(9):
        a = i * 2 * math.pi / 9
        sblob(v, math.cos(a) * 6.6, 1.0, math.sin(a) * 6.6, 1.9, 1.5 + 0.4 * (i % 2), 1.9, (STONE, STONE_D, STONE_L)[i % 3], p=2.3)
    clip_ground(v)
    shade(v)
    v.box(-3, 0, -3, 2, 0, 2, P("#5a3f44"))
    for (a, b) in (((-5, 0, -3), (2, 4, 1)), ((5, 0, -2), (-2, 4, 1)), ((0, 0, 5), (0, 4, -2))):
        v.line(a, b, WOOD_D, 0)
        v.line((a[0], a[1] + 1, a[2]), (b[0], b[1] + 1, b[2]), WOOD, 0)
    for y in range(2, 14):                                # chama em gota, 3 tons
        t = (y - 2) / 12.0
        r = 3.4 * math.sin(math.pi * min(1.0, t * 1.25 + 0.18)) * (1 - t) ** 0.35
        ox = math.sin(y * 0.7) * 0.7 * t
        for x in range(-5, 5):
            for z in range(-5, 5):
                d = math.hypot(x + 0.5 - ox, z + 0.5)
                if d <= r:
                    v.set(x, y, z, GLOW_HI if d < r * 0.4 and t < 0.5 else (GLOW if d < r * 0.72 else (FIRE if t < 0.7 else FIRE_R)))
    v.set(2, 15, -1, FIRE)
    v.set(-2, 17, 1, GLOW)
    return v


def log():
    v = Vox()
    for x in range(-9, 9):
        for y in range(0, 7):
            for z in range(-4, 4):
                d = math.hypot(y + 0.5 - 3.2, z + 0.5)
                if d <= 3.5:
                    if x in (-9, 8):
                        c = WOOD_L if d < 1.2 else (WOOD if d < 2.4 else BARK)
                        if 1.2 <= d < 1.7:
                            c = WOOD_D
                    else:
                        c = BARK
                    v.set(x, y, z, c)
    for (x, y, z) in list(v.v):
        if v.v[(x, y, z)] == BARK and hash3(x // 4, y, z, 1) < 0.2:
            v.v[(x, y, z)] = BARK_D
    v.box(-3, 6, -3, -2, 8, -2, BARK)                     # toco de galho
    shade(v)
    moss_top(v, 0.5, 5)
    mushroom(v, 4, 1, 8, 2.4, CORAL, spots=False)
    return v


def stump():
    v = Vox()
    trunk_b(v, 5, 3.3, 2.7, roots=5, flare=1.5, seed=3)
    for x in range(-5, 5):
        for z in range(-5, 5):
            d = math.hypot(x + 0.5, z + 0.5)
            if d <= 2.7:
                v.set(x, 4, z, WOOD_L if int(d * 1.4) % 2 == 0 else WOOD)
    clip_ground(v, -2)
    v.set(1, 5, -1, LEAF)
    v.set(1, 6, -1, LEAF)
    v.set(2, 7, -1, light(LEAF))
    v.set(0, 7, -1, light(LEAF))
    moss_top(v, 0.25, 9)
    return v


def dock():
    v = Vox()
    for x in range(-2, 27):
        plank = (x + 2) // 3
        ext = (0, 1, 0, -1, 0, 1, 1, 0, -1, 0)[plank % 10]
        for z in range(-4 - max(0, ext), 4 + max(0, -ext) + (1 if ext > 0 else 0)):
            c = (WOOD_L, WOOD, WOOD_L, STRAW_D)[plank % 4]
            if (x + 2) % 3 == 2:
                c = WOOD_D
            v.set(x, 3, z, c)
    for x in (0, 12, 25):
        for z in (-5, 4):
            v.box(x, -9, z, x + 1, 2, z + 1, WOOD_D)
            h = 9 if x == 25 else (5 if x == 12 and z == 4 else 3)
            v.box(x, 3, z, x + 1, h, z + 1, WOOD_D)
            v.box(x, h + 1, z, x + 1, h + 1, z + 1, WOOD)
    for z in range(-3, 4):                                # corda selada na ponta
        v.set(25, 7 - (1 if abs(z) < 2 else 0), z, CREAM)
    # lanterna no poste alto
    v.box(24, 10, 4, 26, 10, 6, TEAL_D)
    v.box(24, 11, 4, 26, 13, 6, GLOW)
    v.set(25, 12, 5, GLOW_HI)
    v.box(24, 14, 4, 26, 14, 6, TEAL_D)
    v.set(25, 15, 5, TEAL)
    rbox(v, 18, 4, -3, 21, 7, 0, WOOD, r=0)               # balde de peixe
    v.box(18, 6, -3, 21, 6, 0, IRON)
    v.box(19, 7, -2, 20, 7, -1, WATER)
    return v


def cloud():
    v = Vox()
    c0 = P("#fff6e6")
    for (x, y, z, rx, ry, rz) in ((0, 2.2, 0, 8, 2.9, 4.2), (-5, 1.8, 1, 4.2, 2.2, 3.2), (5.5, 2.4, -0.5, 4.4, 2.8, 3.2),
                                  (1, 4.2, 0, 4.2, 2.2, 2.8), (-2.5, 3.6, -1, 3.0, 2.0, 2.4)):
        sblob(v, x, y, z, rx, ry, rz, c0, p=2.2)
    clip_ground(v, 0)
    tones = (C("#d9b8c8"), C("#f3d6c4"), C("#fff0dc"), C("#fffaf0"))
    for (x, y, z) in list(v.v):
        up = 0
        while up < 3 and (x, y + up + 1, z) in v.v:
            up += 1
        v.v[(x, y, z)] = tones[3] if up == 0 else (tones[2] if up == 1 else tones[1])
        if y == 0:
            v.v[(x, y, z)] = tones[0]
    return v


def build(out):
    ps = PropSet(f"{out}/environment/b", BIG)
    ps.add("tree_oak", tree_oak(1))
    ps.add("tree_oak2", tree_autumn())
    ps.add("tree_pine", tree_pine())
    ps.add("tree_birch", tree_birch())
    ps.add("tree_big", tree_big())
    ps.add("sapling", sapling())
    ps.add("bush", bush())
    ps.add("bush_berry", bush(True, 15))
    ps.add("mushroom_big", mushroom_big())
    ps.add("rock_s", boulder([(0, 1.2, 0, 3.2, 2.6, 2.8), (2.4, 0.6, 1.6, 1.8, 1.5, 1.6)], seed=2, moss=0.4))
    ps.add("rock_m", boulder([(0, 3.0, 0, 5.6, 4.8, 4.8), (4.2, 1.4, 2.4, 3.0, 2.6, 2.8)], seed=5))
    ps.add("rock_l", boulder([(0, 6.5, 0, 9.5, 9.2, 8.5), (8, 3.0, 4.5, 5.0, 4.6, 4.5), (-6.5, 2.2, 5, 3.6, 3.4, 3.4)],
                             seed=8, moss=0.6))
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
                      ("flower_red", flower(CORAL, seed=1, cup=True)), ("flower_yellow", flower(YELLOW, C("#e2864a"), 2)),
                      ("flower_blue", flower(BLUE, seed=3)), ("flower_white", flower(CREAM, seed=4)),
                      ("mushroom_red", mushrooms_red()), ("signpost", signpost()),
                      ("lantern_post", lantern_post()), ("crate", crate()), ("barrel", barrel()),
                      ("campfire", campfire()), ("reeds", reeds())):
        ps.add(name, vox, FINE)
    ps.save()
