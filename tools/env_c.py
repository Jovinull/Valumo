"""Direção C — Pixel Voxel Adventure: terreno (tiles 16x16) e props de cuboides com pixel art.

Tudo em texels (1/32 u). Um bloco de terreno tem 1/2 u = 16 texels, então um tile 16x16 mantém a
mesma densidade dos personagens e dos props. Copas são cuboides com textura de folhas recortada por
alfa nas bordas + um miolo escuro; flores, tufos e juncos são planos cruzados finos com alfa.
"""
import json
import math
import os

from cuboidlib import (Canvas, CuboidPropSet, M, bark, box, bricks, fill, hsh, leaves, mirror_x, moved,
                       planks, prism, save_strip, shingles, spr, tone, turned)

# ----------------------------------------------------------------------------- tiles de terreno

def _tile(base, rows, legend):
    c = Canvas(16, 16, base)
    return c.blit(spr(rows, legend))


GRASS_A = _tile("green3", """
    ................
    ..d.......l.....
    .d.d.....l......
    ..........d.....
    .....l.....d....
    ....l...........
    ............d...
    .d...........d..
    ..d....l........
    ......l.l.......
    ................
    ...d......d.....
    ....d......d....
    .l..............
    l........l......
    .........d......""", "d=green2 l=green4")
GRASS_B = _tile("green3", """
    ................
    .....d......l...
    ....d.d....l....
    ................
    .l........d.....
    l..........d....
    ......l.........
    .....l.......d..
    ............d...
    ..d.............
    ...d.....l......
    ........l.l.....
    ................
    .....d........l.
    ......d......l..
    ................""", "d=green2 l=green4")
GRASS_C = _tile("green3", """
    ................
    ...l............
    ..l.....d.......
    .........d...w..
    ............wyw.
    .....d.......w..
    ....d.d.........
    ..........l.....
    .........l......
    ..w.............
    .wyw......d.....
    ..w........d....
    .......l........
    ......l.....d...
    .............d..
    ................""", "d=green2 l=green4 w=white y=gold1")
FOREST = _tile("green2", """
    ................
    ..d......l......
    .d.d....l.......
    ...........d....
    .....o......d...
    ....oo..........
    ..........l.....
    .d.......l...d..
    ..d.............
    ......d.....y...
    .......d........
    ...l............
    ..l.......d.....
    ...........d..o.
    .....d..........
    ......d.........""", "d=green1 l=green3 o=brown2 y=gold0")
PATH_A = _tile("dirt2", """
    ................
    ...pp.......d...
    ..pppp..........
    ...dd.....l.....
    ........d.......
    .d..........pp..
    ...........pppp.
    ....l.......dd..
    ................
    ..d.....ppp.....
    .......ppppp..d.
    ........ddd.....
    .l..............
    .....d......l...
    ..pp............
    ..dd.....d......""", "p=sand0 d=dirt1 l=sand1")
PATH_B = PATH_A.flip_h().tile(16, 16, 5, 9)
SAND = _tile("sand1", """
    ................
    ..lll.......d...
    ................
    ........lll.....
    ...d............
    .............ll.
    ....ll..........
    ..........d.....
    ................
    .ll......lll....
    ......d.........
    ...............d
    ...lll..........
    ...........ll...
    ......d.........
    ................""", "l=sand2 d=sand0")
ROCK_A = _tile("grey2", """
    ................
    ..lll...........
    ..ddd......ll...
    ...........dd...
    .....l..........
    .....d....l.....
    ..........dd....
    .ll.............
    .dd.....lll.....
    ........ddd.....
    ...l............
    ...d.........ll.
    .............dd.
    ......ll........
    ......dd....l...
    ............d...""", "l=grey3 d=grey1")
ROCK_B = ROCK_A.flip_v().tile(16, 16, 9, 3)
DIRT_A = _tile("dirt1", """
    ................
    ..d.......l.....
    ..........l.....
    .....ss.........
    .....sd.....d...
    ................
    .l.......d......
    .l..............
    ............ss..
    ...d........sd..
    ................
    .......l........
    .......l....d...
    ..d.............
    ..........d.....
    ................""", "d=dirt0 l=dirt2 s=grey2")
DIRT_B = DIRT_A.flip_h().tile(16, 16, 6, 11)


def _side(top, fringe, light):
    c = DIRT_A.copy()
    c.blit(spr("""
        TLTTTTTLTTTTLTTT
        TTTTTTTTTTTTTTTT
        TfTTTfTTfTTTTfTT
        fffTffTfffTTfffT
        f.ff.fff.fffff.f
        ...f..f...f..f..""", {"T": top, "f": fringe, "L": light}))
    return c


GRASS_SIDE = _side("green3", "green2", "green4")
FOREST_SIDE = _side("green2", "green1", "green3")

TILES = [GRASS_A, GRASS_B, GRASS_C, FOREST, PATH_A, PATH_B, SAND, ROCK_A, ROCK_B, GRASS_SIDE, FOREST_SIDE, DIRT_A, DIRT_B]
TILE_MAP = {"grass_top": [0, 0, 1, 1, 2], "forest_top": [3], "path": [4, 5], "sand": [6], "rock": [7, 8],
            "grass_side": [9], "forest_side": [10], "dirt": [11, 12]}


# ----------------------------------------------------------------------------- regras de material

def stone(w, h, seed=0, base="grey2", bevel=True):
    """Pedra: chanfro claro em cima/esquerda, escuro embaixo/direita, trincas e lascas ordenadas."""
    w, h = max(1, int(round(w))), max(1, int(round(h)))
    c = Canvas(w, h, base)
    for y in range(h):
        for x in range(w):
            if hsh(x // 3, y // 2, seed) % 29 == 0:
                c.p[y][x] = tone(base, -1)
            elif hsh(x // 3, y // 2, seed + 5) % 31 == 0:
                c.p[y][x] = tone(base, 1)
    for k in range(w * h // 420 + (1 if min(w, h) >= 10 else 0)):
        x, y = 2 + hsh(k, 1, seed) % max(1, w - 4), hsh(k, 2, seed) % max(1, h // 2)
        dx = 1 if hsh(k, 3, seed) % 2 else -1
        for i in range(4 + hsh(k, 4, seed) % 6):
            c.set(x, y, tone(base, -1))
            c.set(x + 1, y, tone(base, 1))
            y += 1
            if hsh(k, 10 + i, seed) % 3 == 0:
                x += dx
    if bevel and w >= 4 and h >= 4:
        c.edges(top=1, left=1, bottom=-1, right=-1)
    return c


def stone_mat(seed=0, base="grey2", moss=None):
    def mat(face, w, h):
        if face == "py":
            c = stone(w, h, seed + 7, tone(base, 1), bevel=False)
            if moss:
                for y in range(h):
                    for x in range(w):
                        v = hsh(x // 4, y // 4, seed + 3) % 5
                        if v == 0 or (v == 1 and (x + y) % 2 == 0):
                            c.p[y][x] = moss
            return c
        if face == "ny":
            return fill(w, h, tone(base, -1))
        return stone(w, h, seed + {"px": 1, "nx": 2, "pz": 3, "nz": 4}[face], base)
    return mat


def bark_mat(base="brown1", seed=0):
    def mat(face, w, h):
        if face in ("py", "ny"):
            return rings(w, h)
        return bark(w, h, base, seed + {"px": 1, "nx": 2, "pz": 3, "nz": 4}[face])
    return mat


def rings(w, h, wood="brown3"):
    """Topo de tronco cortado: anéis concêntricos (quadrados arredondados) e casca na borda."""
    c = Canvas(w, h, wood)
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            c.p[y][x] = "brown1" if d == 0 else (tone(wood, -1) if d % 2 == 0 else wood)
    c.set(w // 2, h // 2, tone(wood, 1))
    return c


def plank_mat(base="brown3", size=5, vertical=False, seed=0, top=None):
    def mat(face, w, h):
        if face in ("py", "ny"):
            return planks(w, h, top or base, size, False, seed + 9)
        return planks(w, h, base, size, vertical, seed + {"px": 1, "nx": 2, "pz": 3, "nz": 4}[face])
    return mat


# ----------------------------------------------------------------------------- vegetação

OAK = ["green0", "green1", "green2", "green3"]
OAK2 = ["green1", "green2", "green3", "green4"]
PINE = ["ink", "green0", "green1", "green2"]
BIRCH = ["green2", "green3", "green4", "green4"]


def leafblock(cx, y, cz, w, h, d, ramp, seed=0, yaw=0, cut="tblr"):
    """Bloco de copa: casca com folhas recortadas por alfa nas bordas + miolo escuro 2 texels para dentro."""
    s1 = leaves(w, h, ramp, seed, cut=cut)
    s2 = leaves(d, h, ramp, seed + 1, cut=cut)
    top = leaves(w, d, ramp, seed + 2, lift=1, cut="tblr", grad=False)
    bot = leaves(w, d, ramp, seed + 3, lift=-1, grad=False)
    rot = (0, yaw, 0) if yaw else None
    org = (cx, y, cz) if yaw else None
    out = [box(cx - w / 2, y, cz - d / 2, w, h, d, None,
               {"pz": s1, "nz": s1.flip_h(), "px": s2, "nx": s2.flip_h(), "py": top, "ny": bot}, rot=rot, origin=org)]
    if min(w, h, d) > 6:
        core = Canvas(2, 2, ramp[0])
        out.append(box(cx - w / 2 + 2, y + 2, cz - d / 2 + 2, w - 4, h - 4, d - 4, None,
                       {f: core for f in ("px", "nx", "py", "ny", "pz", "nz")}, rot=rot, origin=org, free=True))
    return out


def trunk(cx, cz, w, y0, h, base="brown1", seed=0, rot=None, origin=None):
    return box(cx - w / 2, y0, cz - w / 2, w, h, w, bark_mat(base, seed), rot=rot, origin=origin)


def tree_oak(seed=1, ramp=OAK, h=74, lean=0):
    c = [trunk(0, 0, 12, -4, h + 4, seed=seed)]
    for i, (dx, dz) in enumerate(((7, 0), (-7, 1), (0, 7), (1, -7))):          # raízes
        c.append(box(dx - 4, -4, dz - 4, 8, 9 - i % 2 * 2, 8, bark_mat("brown1", seed + i)))
    c.append(trunk(0, 0, 6, h - 22, 30, seed=seed + 4, rot=(0, 0, -42), origin=(0, h - 22, 0)))   # galhos
    c.append(trunk(0, 0, 6, h - 30, 30, seed=seed + 5, rot=(0, 0, 48), origin=(0, h - 30, 0)))
    c.append(trunk(0, 0, 5, h - 26, 26, seed=seed + 6, rot=(44, 0, 0), origin=(0, h - 26, 0)))
    c += leafblock(0, h - 6, 0, 70, 40, 66, ramp, seed)
    c += leafblock(2, h + 30, -2, 46, 22, 44, ramp, seed + 10, yaw=20)
    c += leafblock(30, h - 14, 6, 36, 28, 38, ramp, seed + 20, yaw=-15)
    c += leafblock(-32, h - 2, -4, 34, 30, 40, ramp, seed + 30, yaw=12)
    c += leafblock(-4, h - 12, 32, 40, 26, 30, ramp, seed + 40)
    c += leafblock(6, h, -32, 38, 28, 28, ramp, seed + 50, yaw=-10)
    return c


def tree_pine(seed=3):
    c = [trunk(0, 0, 10, -4, 150, "brown1", seed)]
    y = 34
    for i, (w, h) in enumerate(((78, 26), (66, 26), (54, 24), (42, 24), (30, 22), (18, 22))):
        c += leafblock(0, y, 0, w, h, w, PINE, seed + i * 7, yaw=45 if i % 2 else 0, cut="tblr")
        y += h - 3
    c += leafblock(0, y, 0, 8, 14, 8, PINE, seed + 60, yaw=45)
    return c


def tree_birch(seed=5):
    def bk(face, w, h):
        if face in ("py", "ny"):
            return fill(w, h, "grey3")
        c = Canvas(w, h, "grey4")
        c.vline(w - 1, "grey3")
        for y in range(h):
            v = hsh(y, ord(face[1]), seed)
            if v % 6 == 0:
                x0 = v % max(1, w - 2)
                c.rect(x0, y, 2 + v % 2, 1, "grey0")
            elif v % 11 == 0:
                c.set(v % w, y, "grey2")
        return c
    c = [box(-3.5, -4, -3.5, 7, 108, 7, bk)]
    c.append(box(-1.5, 60, -1.5, 3, 22, 3, bk, rot=(0, 0, -40), origin=(0, 60, 0)))
    c.append(box(-1.5, 70, -1.5, 3, 20, 3, bk, rot=(0, 0, 44), origin=(0, 70, 0)))
    c += leafblock(0, 92, 0, 40, 30, 38, BIRCH, seed)
    c += leafblock(-2, 118, 2, 26, 18, 26, BIRCH, seed + 9, yaw=30)
    c += leafblock(18, 76, -4, 24, 20, 24, BIRCH, seed + 19, yaw=-20)
    c += leafblock(-18, 82, 6, 22, 20, 24, BIRCH, seed + 29, yaw=15)
    return c


def tree_big(seed=11):
    c = [trunk(0, 0, 30, -6, 130, "brown1", seed)]
    c.append(trunk(0, 0, 38, -6, 22, "brown1", seed + 1))
    for i, (dx, dz, yaw) in enumerate(((22, 4, 0), (-22, -2, 0), (2, 22, 0), (-4, -22, 0), (17, 17, 45), (-17, 16, 45), (16, -17, 45))):
        c.append(box(dx - 7, -6, dz - 7, 14, 16 + (i % 3) * 5, 14, bark_mat("brown1", seed + 2 + i),
                     rot=(0, yaw, 0) if yaw else None, origin=(dx, 0, dz) if yaw else None))
    c.append(trunk(0, 0, 14, 96, 64, seed=seed + 20, rot=(0, 0, -50), origin=(0, 96, 0)))
    c.append(trunk(0, 0, 14, 104, 60, seed=seed + 21, rot=(0, 0, 52), origin=(0, 104, 0)))
    c.append(trunk(0, 0, 12, 100, 60, seed=seed + 22, rot=(50, 0, 0), origin=(0, 100, 0)))
    c.append(trunk(0, 0, 12, 108, 56, seed=seed + 23, rot=(-52, 0, 0), origin=(0, 108, 0)))
    # buraco de coruja e cogumelos de casca
    c.append(box(-5, 60, 14.5, 10, 12, 1, None, {"pz": spr("""
        .bbbbbbbb.
        bbkkkkkkbb
        bkkkkkkkkb
        bkkkkkkkkb
        bkkkkkkkkb
        bkkkkkkkkb
        bkkkkkkkkb
        bkkkkkkkkb
        bkkyykyykb
        bkkkkkkkkb
        bbkkkkkkbb
        .bbbbbbbb.""", "b=brown2 k=ink y=gold2")}, skip="px nx py ny nz"))
    for i, (yy, dx) in enumerate(((36, -8), (44, 4), (30, 6))):
        c.append(box(dx, yy, 15, 9, 2, 5, M("sand1", up=1, bottom=-1)))
    c += leafblock(0, 128, 0, 128, 58, 122, OAK, seed)
    c += leafblock(4, 182, -4, 88, 40, 84, OAK, seed + 10, yaw=18)
    c += leafblock(0, 218, 0, 50, 24, 48, OAK, seed + 15, yaw=-12)
    c += leafblock(62, 112, 10, 62, 48, 66, OAK, seed + 20, yaw=-14)
    c += leafblock(-64, 122, -8, 60, 50, 70, OAK, seed + 30, yaw=10)
    c += leafblock(-8, 110, 62, 72, 46, 54, OAK, seed + 40, yaw=8)
    c += leafblock(10, 124, -62, 70, 48, 50, OAK, seed + 50, yaw=-9)
    c += leafblock(50, 150, -44, 44, 34, 44, OAK, seed + 60, yaw=30)
    c += leafblock(-48, 154, 44, 44, 34, 44, OAK, seed + 70, yaw=25)
    return c


def sapling():
    c = [trunk(0, 0, 3, -2, 18, "brown2", 2)]
    c += leafblock(0, 13, 0, 14, 11, 14, OAK2, 3)
    c += leafblock(1, 23, -1, 9, 7, 9, OAK2, 4, yaw=30)
    return c


def bush(berry=False, seed=14):
    ramp = OAK if berry else OAK2
    blocks = [(0, 0, 0, 26, 14, 24, 0), (-3, 12, 1, 16, 9, 16, 25), (12, 2, 6, 14, 11, 14, -20), (-12, 1, -5, 14, 10, 14, 15)]
    c = []
    for i, (x, y, z, w, h, d, yaw) in enumerate(blocks):
        cubs = leafblock(x, y - 1, z, w, h, d, ramp, seed + i * 5, yaw=yaw)
        if berry:
            for f in ("pz", "nz", "px", "nx", "py"):
                cv = cubs[0].faces[f] = cubs[0].faces[f].copy()
                for k in range(cv.w * cv.h // 26):
                    bx, by = 2 + hsh(k, i, seed + ord(f[1])) % max(1, cv.w - 5), 2 + hsh(k, i + 9, seed) % max(1, cv.h - 5)
                    cv.rect(bx, by, 2, 2, "red1").set(bx, by, "red2").set(bx + 1, by + 1, "red0")
        c += cubs
    return c


def plane(s, ang, x=0, y=0, z=0, tilt=0):
    """Plano fino com alfa (espessura 0,06 texel), girado `ang` graus em torno de Y no ponto (x, z)."""
    return box(x - s.w / 2, y, z - 0.03, s.w, s.h, 0.06, None, {"pz": s, "nz": s.flip_h()},
               skip="px nx py ny", rot=(tilt, ang, 0), origin=(x, y, z))


def cross(s, n=2, x=0, y=0, z=0, a0=45):
    return [plane(s, a0 + i * 180 / n, x, y, z) for i in range(n)]


TUFT = spr("""
    ...l.....d..
    .d.l..l..d..
    .d.gl.l.dg.l
    .dggl.lgdg.l
    ddgglglgdgl.
    .dggggggdgl.
    ..dgggggdg..
    ..ddggggd...""", "g=green3 d=green2 l=green4")
TUFT_TALL = spr("""
    ....y.......
    ...yy....y..
    ...y.....yy.
    ...l.....y..
    .d.l..l..d..
    .d.l..l..d..
    .d.gl.l.dg.l
    .d.gl.l.dg.l
    .dggl.lgdg.l
    .dggl.lgdg.l
    ddgglglgdgl.
    ddgglglgdgl.
    .dggggggdgl.
    .dggggggdgl.
    ..dgggggdg..
    ..ddggggd...""", "g=green3 d=green2 l=green4 y=sand1")
FERN = spr("""
    ......l..l......
    .....lgl.gl..l..
    .l..lgg.lgg.lg..
    lgl.lgd.lgd.lgl.
    .lgllgdlgd.lgd..
    ..lgglglgdlgd...
    .l.lgglggdgd..l.
    lgl.lggggdd.lgl.
    .lgl.lgggd.lgd..
    ..lggllggdlgd...
    ...ldgggggdd....
    .....dgggd......
    ......dgd.......
    ......dd........""", "g=green2 d=green1 l=green3")


def flower_sprite(petal, center="gold1"):
    return spr("""
        ..pPp..
        .pPPPp.
        .PPcPP.
        .pPPPs.
        ..psp..
        ...g...
        ...g.l.
        .l.gll.
        .llg...
        ..lg...
        ...g...
        ...g...""", {"P": petal, "p": tone(petal, 1) if petal != "white" else "white", "s": tone(petal, -1) if petal != "white" else "grey3",
                     "c": center, "g": "green2", "l": "green3"})


def flower(petal, center="gold1", seed=0):
    s = flower_sprite(petal, center)
    small = s.crop(0, 2, 7, 10)
    c = cross(s, 2)
    c += cross(small, 2, x=5, z=3 - seed % 3, a0=20)
    c += cross(small.flip_h(), 2, x=-4, z=-4 + seed % 2, a0=70)
    return c


REEDS = spr("""
    ....b.........
    ....b......b..
    ....b......b..
    ....b...l..b..
    ....g...l..b..
    .l..g...l..g..
    .l..g..lg..g..
    .l..g..lg..g.l
    .lg.g..lg..g.l
    .lg.g..lg.lg.l
    .lg.g.dlg.lg.l
    .lg.gldlg.lgl.
    ..g.gldlg.lgl.
    ..g.gld.g.lgl.
    ..gdgld.g.lg..
    ..gdgld.gdlg..
    ..gdgl..gdlg..
    ..gdgl..gdl...
    ..gdg...gdl...
    ..gdg...gd....
    ...dg...gd....
    ...dg...gd....""", "g=green2 d=green1 l=green3 b=brown2")


def mushroom_red():
    def one(x, z, s):
        cap = Canvas(7, 3, "red1").hline(2, "red0").set(1, 0, "white").set(4, 1, "white").set(5, 0, "red2")
        top = Canvas(7, 7, "red1").rect(1, 1, 2, 2, "white").set(5, 4, "white").set(3, 5, "white").set(5, 1, "red2")
        return [box(x - 1.5, 0, z - 1.5, 3, 4 * s, 3, M("sand2", bottom=-1)),
                box(x - 3.5, 4 * s - 0.5, z - 3.5, 7, 3, 7, M("red1", down=0), {"pz": cap, "nz": cap, "px": cap, "nx": cap, "py": top,
                                                                                 "ny": Canvas(7, 7, "sand1").edges(-1, -1, -1, -1)}),
                box(x - 2, 4 * s + 2.5, z - 2, 4, 1.5, 4, M("red1", up=1, bottom=0))]
    return one(-2, 0, 1.4) + one(4, 3, 0.9) + one(3, -4, 0.7)


def mushroom_big():
    stem = Canvas(12, 42, "sand2")
    stem.vline(0, "sand1").vline(11, "sand1").vline(10, "sand1", 6)
    for y in range(4, 40, 7):
        stem.set(3 + y % 5, y, "sand1").set(3 + y % 5, y + 1, "sand1")
    c = [box(-6, -2, -6, 12, 42, 12, "sand2", {"pz": stem, "nz": stem, "px": stem, "nx": stem}),
         box(-8, -2, -8, 16, 6, 16, M("sand2", bottom=-1, up=0)),
         box(-8, 28, -8, 16, 3, 16, M("sand1", bottom=-1, up=1))]

    def cap(w, h, seed, rim=False):
        cv = Canvas(w, h, "red1")
        for k in range(max(1, w * h // 60)):
            sx, sy = 2 + hsh(k, 1, seed) % max(1, w - 7), hsh(k, 2, seed) % max(1, h - 3)
            cv.blit(spr("""
                .ww.
                wwww
                swws""" if h < 8 else """
                .www.
                wwwww
                wwwww
                swwws
                .sss.""", "w=sand2 s=sand1"), sx, sy)
        cv.edges(top=1, bottom=-1)
        if rim:
            cv.hline(h - 1, "red0").hline(h - 2, "red0")
        return cv

    def tier(w, h, y, seed, rim=False):
        f = cap(w, h, seed, rim)
        g = cap(w, h, seed + 1, rim)
        top = cap(w, w, seed + 2).edges(top=0, bottom=0)
        under = Canvas(w, w, "sand1")
        for x in range(0, w, 3):
            under.vline(x, "sand0")
        return box(-w / 2, y, -w / 2, w, h, w, "red1", {"pz": f, "nz": g, "px": g, "nx": f, "py": top, "ny": under})
    c += [tier(46, 8, 38, 1, True), tier(40, 8, 46, 5), tier(30, 6, 54, 9), tier(16, 4, 60, 13)]
    return c


# ----------------------------------------------------------------------------- pedras

def rock(blocks, seed=0, moss="green2"):
    c = []
    for i, (x, y, z, w, h, d, yaw, tilt) in enumerate(blocks):
        rot = (tilt, yaw, 0) if (yaw or tilt) else None
        c.append(box(x - w / 2, y, z - d / 2, w, h, d, stone_mat(seed + i * 11, moss=moss if i % 2 == 0 else None),
                     rot=rot, origin=(x, y, z) if rot else None))
    return c


def rock_s():
    return rock([(0, -2, 0, 16, 13, 13, 12, 0), (5, -2, 4, 9, 8, 9, -25, 0), (-2, 9, -1, 9, 5, 8, 30, 0)], 2)


def rock_m():
    return rock([(0, -3, 0, 30, 24, 26, 14, 0), (-4, 18, 1, 19, 11, 17, -18, 4), (15, -3, 8, 15, 14, 14, 35, 0),
                 (-15, -3, -7, 13, 11, 13, -30, 0)], 5)


def rock_l():
    return rock([(0, -4, 0, 54, 44, 46, 10, 0), (4, 36, -2, 36, 22, 32, -16, 5), (-28, -4, 12, 26, 26, 24, 32, 0),
                 (30, -4, -10, 24, 32, 26, -24, 0), (-4, 54, 0, 20, 10, 18, 28, -4), (22, -4, 22, 16, 12, 16, 15, 0)], 8)


# ----------------------------------------------------------------------------- construções

def brick_mat(base="grey2", bw=10, bh=5, seed=0, top=None):
    def mat(face, w, h):
        if face in ("py", "ny"):
            return fill(w, h, top or tone(base, 1), top=0, bottom=0).edges(-1, -1, -1, -1)
        return bricks(w, h, base, bw, bh, seed + {"px": 1, "nx": 2, "pz": 3, "nz": 4}[face])
    return mat


def plaster(w, h, seed=0, posts=(), sill=True):
    """Parede de reboco com enxaimel desenhado: vigas no topo/base, prumos e manchas de reboco."""
    c = Canvas(w, h, "sand2")
    for k in range(w * h // 150):
        x, y = hsh(k, 1, seed) % w, 6 + hsh(k, 2, seed) % max(1, h - 12)
        c.rect(x, y, 3 + hsh(k, 3, seed) % 3, 1, "sand1")
        c.rect(x + 1, y + 1, 2, 1, "sand1")
    c.tone_checker(0, h - 6, w, 2, -1).rect(0, h - 4, w, 4, "sand1")
    c.rect(0, 0, w, 5, "brown1").hline(0, "brown2").hline(4, "brown0")
    if sill:
        c.rect(0, h - 3, w, 3, "brown1").hline(h - 3, "brown2")
    for x in posts:
        c.rect(x, 0, 5, h, "brown1").vline(x, "brown2").vline(x + 4, "brown0")
    return c


def window_group():
    """Janela voltada para +Z no plano z=0, centrada em x=0, base em y=0: moldura, vidro, venezianas e floreira."""
    win = spr("""
        ffffffffffffffffffffffff
        fFFFFFFFFFFFFFFFFFFFFFFf
        fFgggggggggFFgggggggggFf
        fFgGGggggggFFgGGggggggFf
        fFgGgggggggFFgGgggggggFf
        fFggggggggGFFggggggggGFf
        fFgggggggGGFFgggggggGGFf
        fFggggggGGgFFggggggGGgFf
        fFgggggggggFFgggggggggFf
        fFFFFFFFFFFFFFFFFFFFFFFf
        fFFFFFFFFFFFFFFFFFFFFFFf
        fFgggggggggFFgggggggggFf
        fFgGGggggggFFgGGggggggFf
        fFgGgggggggFFgGgggggggFf
        fFggggggggGFFggggggggGFf
        fFgggggggGGFFgggggggGGFf
        fFgggggggggFFgggggggggFf
        fFgggggggggFFgggggggggFf
        fFFFFFFFFFFFFFFFFFFFFFFf
        ssssssssssssssssssssssss
        fSSSSSSSSSSSSSSSSSSSSSSf""", "f=brown0 F=brown2 g=blue2 G=blue3 s=brown4 S=brown3")
    c = [box(-12, 0, 0, 24, 21, 2.5, M("brown1", up=1), {"pz": win})]
    slat = Canvas(7, 19, "green1")
    for y in range(0, 19, 3):
        slat.hline(y, "green2").hline(y + 2, "green0")
    slat.vline(0, "green0").vline(6, "green0")
    c.append(box(-19.5, 1, 0, 7, 19, 1.6, M("green1", up=1), {"pz": slat}))
    c.append(box(12.5, 1, 0, 7, 19, 1.6, M("green1", up=1), {"pz": slat}))
    c.append(box(-13, -6, 0, 26, 5, 6, plank_mat("brown2", 5, seed=4, top="brown0")))
    c += leafblock(0, -2, 3, 24, 4, 5, OAK2, 77, cut="t")
    for i, (x, col) in enumerate(((-8, "red1"), (-1, "gold1"), (6, "pink1"), (-4.5, "white"))):
        c.append(box(x, 1.5 + i % 2, 3 + i % 2, 3, 3, 3, M(col, up=1, bottom=-1)))
    return c


def roof_slab(w, length, ridge, angle, base="red1", z_sign=1, x0=None, thick=5, size=6, seed=0):
    """Água de telhado: laje inclinada girando em X sobre a cumeeira; telhas na face de cima."""
    x0 = -w / 2 if x0 is None else x0
    L = int(round(length))
    top = shingles(w, L, base, size, seed)
    under = planks(w, L, "brown1", 6, False, 3)
    edge = Canvas(L, thick, tone(base, -1)).hline(0, base)
    end = Canvas(w, thick, tone(base, -1)).hline(0, base)
    if z_sign > 0:
        return box(x0, ridge - thick, 0, w, thick, L, None,
                   {"py": top, "ny": under, "px": edge, "nx": edge, "pz": end, "nz": end},
                   rot=(angle, 0, 0), origin=(0, ridge, 0))
    return box(x0, ridge - thick, -L, w, thick, L, None,
               {"py": top.flip_v(), "ny": under, "px": edge, "nx": edge, "pz": end, "nz": end},
               rot=(-angle, 0, 0), origin=(0, ridge, 0))


def house():
    c = []
    HW, HD, Y0, WH, RIDGE = 70, 54, 8, 62, 143
    c.append(box(-HW - 4, -8, -HD - 4, 2 * HW + 8, 16, 2 * HD + 8, brick_mat("grey2", 12, 5, 1)))
    front = plaster(2 * HW, WH, 1, posts=(0, 30, 50, 85, 105, 135))
    back = plaster(2 * HW, WH, 2, posts=(0, 44, 91, 135))
    side = plaster(2 * HD, WH, 3, posts=(0, 51, 103))
    c.append(box(-HW, Y0, -HD, 2 * HW, WH, 2 * HD, "sand2", {"pz": front, "nz": back, "px": side, "nx": side}, skip="ny"))
    post = plank_mat("brown1", 6, vertical=True, seed=7)
    for sx in (-1, 1):
        for sz in (-1, 1):
            c.append(box(sx * HW - 3.5 + sx * 1.5, Y0, sz * HD - 3.5 + sz * 1.5, 7, WH, 7, post))
    # porta: batente saliente, folha de tábuas com ferragens, degrau de pedra, alpendre
    door = planks(28, 48, "brown3", 7, vertical=True, seed=2, joints=False)
    door.rect(0, 8, 12, 2, "grey1").rect(0, 36, 12, 2, "grey1").set(11, 8, "grey3").set(11, 36, "grey3")
    door.rect(21, 24, 3, 4, "gold1").set(21, 24, "gold2").rect(21, 28, 3, 1, "gold0")
    door.blit(spr("""
        .ffffff.
        ffgGggff
        fgGgggGf
        fggggGgf
        ffggggff
        .ffffff.""", "f=brown1 g=blue2 G=blue3"), 10, 6)
    door.edges(top=-1, left=-1, right=-1)
    c.append(box(-18, Y0, HD, 36, 53, 3, plank_mat("brown1", 6, vertical=True, seed=8)))
    c.append(box(-14, Y0, HD + 2.4, 28, 48, 1.4, "brown2", {"pz": door}))
    c.append(box(-22, 2, HD + 2, 44, 6, 12, stone_mat(3, "grey2")))
    c.append(_awning(HD))
    for sx in (-1, 1):
        c.append(box(sx * 20 - 1.5, 62, HD, 3, 3, 14, "brown1", rot=(28, 0, 0), origin=(0, 66, HD)))
    # janelas
    wg = window_group()
    c += moved(wg, -46, 30, HD) + moved(wg, 46, 30, HD)
    c += moved(turned(wg, 1), HW, 30, 0) + moved(turned(wg, 3), -HW, 30, 0)
    c += moved(turned(wg, 2), -20, 30, -HD)
    # empena (triângulo em degraus) + águas do telhado + cumeeira
    rise = RIDGE - (Y0 + WH)
    tri = Canvas(2 * HD, rise)
    for j in range(rise):
        half = int(HD * (j + 1) / rise)
        for x in range(HD - half, HD + half):
            d = min(x - (HD - half), HD + half - 1 - x)
            col = "brown1" if (d < 4 or abs(x - HD + 0.5) < 2.5 or j in range(rise - 5, rise) or 38 <= j <= 41) else "sand2"
            if col == "sand2" and hsh(x // 3, j // 2, 4) % 19 == 0:
                col = "sand1"
            tri.p[j][x] = col
    tri.blit(spr("""
        ..ffffff..
        .ffggggff.
        ffgGGgggff
        fgGGggggGf
        fgGgggggGf
        fggggggGGf
        fggggGGggf
        ffgggGggff
        .ffggggff.
        ..ffffff..""", "f=brown0 g=blue2 G=blue3"), HD - 5, 46)
    c += prism(tri, 2 * HW, (-HW, Y0 + WH, -HD), axis="x", rim=-1)
    ang = math.degrees(math.atan2(rise, HD))
    length = (HD + 9) / math.cos(math.radians(ang)) + 4
    c.append(roof_slab(2 * HW + 16, length, RIDGE + 4, ang, "red1", 1, seed=1))
    c.append(roof_slab(2 * HW + 16, length, RIDGE + 4, ang, "red1", -1, seed=2))
    cap = Canvas(2 * HW + 18, 8, "red0")
    for x in range(0, cap.w, 8):
        cap.vline(x, "ink")
    cap.hline(0, "red1")
    c.append(box(-HW - 9, RIDGE - 1, -4, 2 * HW + 18, 7, 8, "red0", {"pz": cap.crop(0, 0, cap.w, 7), "nz": cap.crop(0, 0, cap.w, 7), "py": cap}))
    # chaminé
    c.append(box(30, 96, -36, 16, 56, 16, brick_mat("grey2", 8, 4, 9)))
    c.append(box(28, 150, -38, 20, 4, 20, M("grey1", up=0, bottom=-1)))
    c.append(box(33, 154, -33, 10, 3, 10, M("grey0", up=-1)))
    return c


def _awning(HD):
    s = roof_slab(48, 18, 0, 0, "red1", 1, seed=5, thick=3)
    s.x, s.y, s.z = -24, 66, HD
    s.rot, s.origin = (26, 0, 0), (0, 69, HD)
    return s


def well():
    c = []
    bm = brick_mat("grey2", 7, 4, 3)
    c += [box(-15, -3, 11, 30, 19, 4, bm), box(-15, -3, -15, 30, 19, 4, bm),
          box(-15, -3, -11, 4, 19, 22, bm), box(11, -3, -11, 4, 19, 22, bm)]
    cm = stone_mat(5, "grey3")
    c += [box(-16.5, 16, 10, 33, 3, 6.5, cm), box(-16.5, 16, -16.5, 33, 3, 6.5, cm),
          box(-16.5, 16, -10, 6.5, 3, 20, cm), box(10, 16, -10, 6.5, 3, 20, cm)]
    water = Canvas(22, 22, "blue2")
    for (x, y, n) in ((3, 5, 5), (11, 9, 6), (5, 15, 4), (14, 17, 4), (13, 3, 3)):
        water.rect(x, y, n, 1, "blue3")
    c.append(box(-11, 2, -11, 22, 8, 22, "blue1", {"py": water}, skip="px nx pz nz ny"))
    pm = plank_mat("brown2", 5, vertical=True, seed=4)
    c += [box(-15, 19, -2.5, 5, 40, 5, pm), box(10, 19, -2.5, 5, 40, 5, pm)]
    c.append(box(-13, 46, -1.5, 26, 3, 3, M("brown1", up=1)))
    c.append(box(-4, 45, -2.5, 8, 5, 5, M("brown3", up=1, bottom=-1)))
    c.append(box(-0.5, 27, -0.5, 1, 18, 1, M("sand1", bottom=0)))
    bucket = planks(8, 7, "brown3", 3, vertical=True, seed=6)
    bucket.hline(1, "grey1").hline(5, "grey1")
    c.append(box(-4, 20, -4, 8, 7, 8, "brown3", {"pz": bucket, "nz": bucket, "px": bucket, "nx": bucket,
                                                  "py": Canvas(8, 8, "brown1").rect(1, 1, 6, 6, "blue2")}))
    R = 73
    for s in (1, -1):
        c.append(roof_slab(42, 26, R, 38, "red1", s, thick=3, size=5, seed=7 + s))
    c.append(box(-22, R - 3, -2, 44, 4, 4, M("red0", up=1)))
    tri = Canvas(32, 14)
    for j in range(14):
        half = int(16 * (j + 1) / 14)
        for x in range(16 - half, 16 + half):
            tri.p[j][x] = "brown2" if j % 4 != 3 else "brown1"
    c += prism(tri, 30, (-15, 57, -16), axis="x", rim=-1)
    return c


def fence():
    c = []
    pm = plank_mat("brown2", 6, vertical=True, seed=1)
    for i, x in enumerate((-30, 0, 30)):
        c.append(box(x - 2.5, -3, -2.5, 5, 31 + (i % 2), 5, pm))
        c.append(box(x - 3.5, 28 + (i % 2), -3.5, 7, 2, 7, M("brown3", up=1, bottom=-1)))
    rail = planks(64, 4, "brown3", 4, seed=2)
    rail.hline(0, "brown4").hline(3, "brown2")
    for y, tilt in ((19, 1.5), (9, -1.2)):
        c.append(box(-32, y, -1.5, 64, 4, 3, M("brown3", up=1), {"pz": rail, "nz": rail}, rot=(0, 0, tilt), origin=(0, y, 0)))
    return c


def signpost():
    c = [box(-2.5, -3, -2.5, 5, 42, 5, plank_mat("brown2", 6, vertical=True, seed=3)),
         box(-3.5, 39, -3.5, 7, 2, 7, M("brown3", up=1))]
    b1 = _arrow_board(30, 9, 1)
    c += prism(b1, 2.5, (-16, 26, 2.5), rim=-1, rot=(0, 0, 4), origin=(0, 30, 3))
    b2 = _arrow_board(24, 8, -1)
    c += prism(b2, 2.5, (-10, 14, 2.5), rim=-1, rot=(0, 0, -5), origin=(0, 18, 3))
    return c


def _arrow_board(w, h, direction):
    """Placa-seta de tábua: ponta recortada na geometria, moldura, pregos e uma seta pintada (sem texto)."""
    c = Canvas(w, h)
    mid = h // 2
    for y in range(h):
        cutn = abs(y - mid) if h % 2 else min(abs(y - mid), abs(y - mid + 1))
        for x in range(w - cutn):
            c.p[y][x] = "brown4"
    for y in range(h):
        for x in range(w):
            if c.p[y][x] is None:
                continue
            nb = [c.get(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(n is None for n in nb):
                c.p[y][x] = "brown2"
    c.hline(mid, "brown1", 5, w - 7)
    for i in range(3):
        c.set(w - 8 - i, mid - 1 - i, "brown1").set(w - 8 - i, mid + 1 + i, "brown1")
    c.set(2, 2, "grey3").set(2, h - 3, "grey3")
    return c if direction > 0 else c.flip_h()


def lantern_post():
    iron = M("grey0", top=1, bottom=0, up=1)
    c = [box(-4, -3, -4, 8, 6, 8, stone_mat(2)),
         box(-2, 3, -2, 4, 58, 4, plank_mat("brown1", 6, vertical=True, seed=5)),
         box(-1.5, 56, -1.5, 3, 3, 15, iron), box(-1, 52, 9, 2, 4, 2, iron),
         box(-5, 49, 5, 10, 3, 10, iron), box(-3, 52, 7, 6, 1.5, 6, iron)]
    glass = spr("""
        kyyyyyk
        kyYYYyk
        kyYWYyk
        kyYWYyk
        kyYWYyk
        kyYYYyk
        koyyyok
        kkkkkkk""", "k=grey0 y=gold1 Y=gold2 W=white o=gold0")
    c.append(box(-3.5, 41, 6.5, 7, 8, 7, "gold1", {"pz": glass, "nz": glass, "px": glass, "nx": glass}))
    c.append(box(-4.5, 39.5, 5.5, 9, 1.5, 9, iron))
    return c


def crate():
    f = planks(24, 24, "brown3", 6, seed=3)
    for i in range(24):
        f.set(i, i, "brown2").set(i + 1, i, "brown2").set(i - 1, i, "brown4")
    f.rect(0, 0, 24, 3, "brown2").rect(0, 21, 24, 3, "brown2").rect(0, 0, 3, 24, "brown2").rect(21, 0, 3, 24, "brown2")
    f.hline(0, "brown4").vline(0, "brown4").hline(23, "brown1").vline(23, "brown1")
    for (x, y) in ((1, 1), (22, 1), (1, 22), (22, 22)):
        f.set(x, y, "grey3")
    t = planks(24, 24, "brown3", 6, seed=5)
    t.rect(0, 0, 24, 3, "brown2").rect(0, 21, 24, 3, "brown2").rect(0, 0, 3, 24, "brown2").rect(21, 0, 3, 24, "brown2")
    t.hline(0, "brown4").vline(0, "brown4")
    return [box(-12, 0, -12, 24, 24, 24, "brown3", {"pz": f, "nz": f, "px": f.flip_h(), "nx": f.flip_h(), "py": t, "ny": t})]


def barrel():
    def staves(w, h=28):
        c = planks(w, h, "brown2", 4, vertical=True, seed=w, joints=False)
        for y in (4, 21):
            c.hline(y, "grey2").hline(y + 1, "grey1")
        c.hline(0, "brown3").hline(h - 1, "brown1")
        return c

    def piece(w, d, y, h):
        top = Canvas(w, d, "brown3")
        for x in range(0, w, 4):
            top.vline(x, "brown2")
        return box(-w / 2, y, -d / 2, w, h, d, "brown2",
                   {"pz": staves(w, h), "nz": staves(w, h), "px": staves(d, h), "nx": staves(d, h), "py": top})
    return [piece(16, 16, 0, 28), piece(20, 10, 0.5, 27), piece(10, 20, 0.5, 27)]


def shrine():
    c = [box(-36, -6, -32, 72, 10, 64, stone_mat(1, moss="green2")),
         box(-30, 4, -26, 60, 6, 52, stone_mat(2, moss="green2"))]
    rune = spr("""
        .c...ccc..c.c...ccc.
        .c...c.c..c.c...c...
        .ccc.ccc..ccc...ccc.
        ...c...c....c.....c.
        .ccc...c..ccc...ccc.""", "c=cyan1")
    altar = stone(24, 16, 4, "grey3").blit(rune, 2, 5)
    c.append(box(-12, 10, -10, 24, 16, 20, stone_mat(4, "grey3"), {"pz": altar}))
    c.append(box(-14, 26, -12, 28, 3, 24, stone_mat(6, "grey3")))
    glyph = spr("""
        .cc.
        c..c
        .cc.
        ....
        cccc
        .cc.
        ....
        c..c
        cccc
        c..c""", "c=cyan1")
    for sx in (-1, 1):
        px = sx * 26
        pf = stone(10, 56, 8 + sx, "grey2").blit(glyph, 3, 14)
        c.append(box(px - 7, 10, -7, 14, 5, 14, stone_mat(9, "grey3")))
        c.append(box(px - 5, 15, -5, 10, 56, 10, stone_mat(10 + sx, "grey2"), {"pz": pf}))
        c.append(box(px - 7, 71, -7, 14, 5, 14, stone_mat(11, "grey3")))
    c.append(box(-38, 76, -9, 76, 10, 18, stone_mat(12, "grey2", moss="green2")))
    c.append(box(-24, 86, -7, 48, 5, 14, stone_mat(13, "grey3", moss="green2")))
    c.append(box(-7, 91, -5, 14, 6, 10, stone_mat(14, "grey3")))
    cr = spr("""
        CCCcccccc
        CWCcccccc
        CCccccccd
        ccccccccd
        ccccccccd
        cccccccdd
        ccccccddd
        cccccdddd
        cccdddddd""", "c=cyan1 C=cyan2 W=white d=cyan0")
    c.append(box(-4.5, 38, -4.5, 9, 9, 9, "cyan1", {f: cr for f in ("px", "nx", "pz", "nz", "py", "ny")},
                 rot=(35.26, 45, 0), origin=(0, 42.5, 0)))
    c.append(box(-2, 50, -2, 4, 4, 4, M("cyan2", bottom=-1), rot=(35.26, 45, 0), origin=(0, 52, 0)))
    return c


FLAME = spr("""
    ....r....
    ...ry....
    ...ryr...
    ..ryyr...
    ..ryyyr..
    .ryyYyr..
    .ryYYyyr.
    ryyYWYyr.
    ryYWWYyyr
    ryYWWYYyr
    .ryYWYyr.
    ..ryyyr..""", "r=red2 y=gold1 Y=gold2 W=white")


def campfire():
    c = []
    for i in range(8):
        a = i * math.pi / 4 + 0.2
        x, z = math.cos(a) * 10, math.sin(a) * 10
        c.append(box(x - 3, -1, z - 3, 6, 4 + i % 2, 6, stone_mat(i, "grey2" if i % 3 else "grey3"), rot=(0, i * 37, 0), origin=(x, 0, z)))
    for i, yaw in enumerate((10, 70, 130)):
        c.append(box(-2, 1, -9, 4, 4, 18, bark_mat("brown1", i), rot=(-14, yaw, 0), origin=(0, 2, 0)))
    c.append(box(-4, 0, -4, 8, 1.5, 8, M("ink", up=0), {"py": spr("""
        kkrkkkyk
        krkkrkkk
        kkkykkrk
        rkkkkkkk
        kkrkkykk
        kkkkrkkr
        kykkkkkk
        kkkrkkyk""", "k=ink r=red1 y=gold1")}))
    c += cross(FLAME, 3, y=3, a0=15)
    c += cross(FLAME.crop(0, 4, 9, 8), 2, x=3, y=3, z=-2, a0=60)
    return c


def log():
    side = bark(13, 44, "brown1", 3).rot90()
    top = bark(14, 44, "brown1", 4)
    for y in range(44):
        for x in range(14):
            v = hsh(x // 3, y // 3, 9) % 6
            if v == 0 or (v == 1 and (x + y) % 2 == 0):
                top.p[y][x] = "green2" if hsh(x, y, 2) % 3 else "green3"
    c = [box(-7, -1, -22, 14, 13, 44, None, {"px": side, "nx": side, "py": top, "ny": top, "pz": rings(14, 13), "nz": rings(14, 13)})]
    c.append(box(6, 5, 6, 5, 5, 5, bark_mat("brown1", 6), rot=(0, 0, -25), origin=(7, 7, 8)))
    c += [box(-9.5, 6, -12, 3, 1.5, 5, M("sand2", up=1)), box(-9, 3, -8, 2.5, 1.5, 4, M("sand2", up=1))]
    return c


def stump():
    c = [box(-8, -2, -8, 16, 13, 16, bark_mat("brown1", 2))]
    for i, (dx, dz) in enumerate(((8, 2), (-8, -3), (1, 8), (-2, -8))):
        c.append(box(dx - 3, -2, dz - 3, 6, 5 + i % 2, 6, bark_mat("brown1", 3 + i)))
    c += cross(spr("""
        .l..
        lgl.
        .gll
        .g..""", "g=green2 l=green3"), 2, x=3, y=11, z=-2)
    return c


def dock():
    deck = planks(112, 30, "brown3", 7, vertical=True, seed=4, joints=False)
    for x in range(3, 112, 7):
        deck.set(x, 2, "grey1").set(x, 27, "grey1")
    edge = Canvas(112, 3, "brown2").hline(0, "brown3").hline(2, "brown1")
    for x in range(6, 112, 7):
        edge.vline(x, "brown1")
    c = [box(-4, 12, -15, 112, 3, 30, "brown2", {"py": deck, "ny": deck, "pz": edge, "nz": edge})]
    beam = M("brown1", up=0)
    c += [box(-4, 9.5, -12, 112, 2.5, 3, beam), box(-4, 9.5, 9, 112, 2.5, 3, beam)]
    bm = bark_mat("brown1", 5)
    for i, x in enumerate((0, 50, 102)):
        for z in (-15.5, 10.5):
            tall = 30 if x == 102 else 16
            c.append(box(x, -40, z, 5, 40 + tall, 5, bm))
            c.append(box(x - 0.5, tall, z - 0.5, 6, 1.5, 6, M("brown3", up=1)))
    c.append(box(103.75, 24, -11, 1.5, 1.5, 22, M("sand1", bottom=0)))
    c.append(box(84, 15, 3, 9, 8, 9, plank_mat("brown2", 3, vertical=True, seed=9, top="brown0")))
    return c


def lily():
    pad = spr("""
        ...dgggd....
        .dgggGggggd.
        .gggGgggggg.
        dggGggggGggd
        gggggGgGgggg
        ggGGGGG.....
        gggggGgG....
        dggGggggGggd
        .gggGgggggg.
        .dggGgggggd.
        ..dgggggd...
        ....ddd.....""", "g=green3 G=green4 d=green2")
    c = prism(pad, 0.7, (-6, 0.1, -6), axis="y", rim_color="green1")
    c += [box(-2.5, 0.8, 0.5, 5, 1.2, 5, M("pink1", up=0, bottom=-1)),
          box(-1.5, 2, 1.5, 3, 1.6, 3, M("white", up=0, bottom=0)),
          box(-0.6, 3.6, 2.4, 1.2, 1, 1.2, M("gold1", bottom=0))]
    return c


def cloud():
    def mat(face, w, h):
        if face == "py":
            return Canvas(w, h, "white")
        if face == "ny":
            return Canvas(w, h, "grey3")
        c = Canvas(w, h, "white")
        b = max(2, h // 3)
        c.rect(0, h - b, w, b, "grey4")
        c.checker(0, h - b - 2, w, 2, "grey4")
        c.checker(0, h - 3, w, 2, "grey3").hline(h - 1, "grey3")
        return c
    return [box(x, y, z, w, h, d, mat) for (x, y, z, w, h, d) in (
        (-96, 0, -48, 192, 44, 96), (-128, 6, -30, 70, 30, 64), (60, 4, -36, 68, 34, 70),
        (-50, 40, -34, 96, 30, 68), (-10, 64, -22, 50, 16, 44), (-84, 36, -20, 44, 20, 40))]


# ----------------------------------------------------------------------------- gravação

def props():
    p = {}
    p["tree_oak"] = tree_oak(1)
    p["tree_oak2"] = tree_oak(7, OAK2, h=66)
    p["tree_pine"] = tree_pine()
    p["tree_birch"] = tree_birch()
    p["tree_big"] = tree_big()
    p["sapling"] = sapling()
    p["bush"] = bush()
    p["bush_berry"] = bush(True, 15)
    p["fern"] = cross(FERN, 3, a0=0)
    p["grass_tuft"] = cross(TUFT, 2)
    p["grass_tall"] = cross(TUFT_TALL, 2) + cross(TUFT, 2, x=5, z=4, a0=10)
    p["flower_red"] = flower("red1", seed=0)
    p["flower_yellow"] = flower("gold1", "gold0", seed=1)
    p["flower_blue"] = flower("blue2", seed=2)
    p["flower_white"] = flower("white", seed=3)
    p["mushroom_red"] = mushroom_red()
    p["mushroom_big"] = mushroom_big()
    p["rock_s"] = rock_s()
    p["rock_m"] = rock_m()
    p["rock_l"] = rock_l()
    p["reeds"] = cross(REEDS, 3, a0=10) + cross(REEDS.flip_h(), 2, x=7, z=5, a0=30)
    for name, fn in (("house", house), ("well", well), ("fence", fence), ("signpost", signpost),
                     ("lantern_post", lantern_post), ("crate", crate), ("barrel", barrel), ("shrine", shrine),
                     ("campfire", campfire), ("log", log), ("stump", stump), ("dock", dock), ("lily", lily),
                     ("cloud", cloud)):
        p[name] = fn()
    return p


def build(out):
    tex = f"{out}/textures/c"
    os.makedirs(tex, exist_ok=True)
    save_strip(f"{tex}/terrain.png", TILES)
    with open(f"{tex}/terrain.json", "w") as f:
        json.dump({"tile": 16, "tiles": TILE_MAP}, f, indent=1)
    ps = CuboidPropSet(f"{out}/environment/c")
    for name, cubs in props().items():
        ps.add(name, cubs)
    ps.atlas_size = ps.save()
    return ps
