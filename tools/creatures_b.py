"""Direção B — Storybook Voxel: criaturas originais.

pompom  — bolota de pelo saltadora, orelhas em gota e rabo de pompom (hopper)
lumi    — espírito-lanterna: miolo de luz com rosto, chapeuzinho de lanterna, fagulhas em órbita (floater)
fungo   — cogumelo-espinheiro rabugento: chapéu vinho com espinhos, braços de cipó (hopper)
tortuga — gigante manso com um jardim inteiro no casco, ~2,2 u (quadruped)

Mesma linguagem dos personagens: massas redondas, 3 tons por material, rosto de olhos grandes.
"""
import math

from chars_b import (BLUSH, CREAM, EYE, EYE_HI, GOLD, MOUTH, P, back_z, front_z, light, normal, sblob,
                     shade, shadow, stack)
from envlib import hash3
from voxlib import C, Rig, Vox


def put_front(v, x, y, c):
    z = front_z(v, x, y)
    if z is not None:
        v.set(x, y, z, c)


def eyes(v, y, x0=2, w=2, h=2, blush=True):
    """Par de olhos na superfície frontal: x0..x0+w-1 (e o espelho), brilho no canto superior."""
    for xs in (x0, -x0 - w):
        for x in range(xs, xs + w):
            for yy in range(y, y + h):
                put_front(v, x, yy, EYE)
        put_front(v, xs, y + h - 1, EYE_HI)
    if blush:
        for x in (x0 + w, x0 + w + 1, -x0 - w - 1, -x0 - w - 2):
            put_front(v, x, y - 1, BLUSH)


# ----------------------------------------------------------------------------- pompom

def pompom():
    fur = P("#f3ddb6")
    belly = P("#fbf0d8")
    russet = P("#c9703f")
    pink = P("#f2a79a")
    foot = P("#a8603c")

    b = Vox()
    sblob(b, 0, 8.0, 0, 7.2, 6.8, 7.6, fur, p=2.3, p_lo=2.9)
    for (x, y, z) in list(b.v):                       # barriga clara e listras de esquilo nas costas
        if z >= 3 and y <= 8 and abs(x + 0.5) < 4.5:
            b.v[(x, y, z)] = belly
        if z <= 3 and y >= 8 and abs(abs(x + 0.5) - 2.5) < 1.0:
            b.v[(x, y, z)] = russet
    sblob(b, 0, 14.4, 1.0, 1.6, 1.3, 1.6, fur)        # topetinho
    shade(b)
    eyes(b, 8, x0=2, w=2, h=3)
    put_front(b, -1, 7, pink)
    put_front(b, 0, 7, pink)
    put_front(b, -1, 6, MOUTH)

    def ear():
        e = Vox()
        prof = [(1.6, 1.2), (2.0, 1.4), (2.3, 1.5), (2.4, 1.5), (2.3, 1.5), (2.1, 1.4), (1.8, 1.3), (1.4, 1.1), (0.9, 0.9)]
        for i, (rx, rz) in enumerate(prof):
            stack(e, 13 + i, 13 + i, lambda y: (4.0 + i * 0.25, -0.5, rx, rz), russet if i >= 6 else fur, p=2.0)
        shade(e)
        for i in range(2, 6):
            put_front(e, 4 + (i // 3), 13 + i, pink)
        return e

    ear_l = ear()
    ear_r = ear_l.flipped_x()

    tail = Vox()
    sblob(tail, 0, 6.5, -9.0, 3.4, 3.4, 3.2, belly, p=2.2)
    shade(tail)

    leg = Vox()
    leg.box(2, 0, 0, 4, 1, 6, foot)
    leg.box(2, 1, 6, 4, 1, 6, None)
    leg.set(2, 1, 0, None)
    shade(leg)

    rig = Rig("Pompom (B)", 1 / 32, "hopper", meta={"headsTall": 1.0})
    rig.part("body", b, (0, 0, 0))
    rig.part("ear_l", ear_l, (4.5, 13.5, -0.5), "body")
    rig.part("ear_r", ear_r, (-4.5, 13.5, -0.5), "body")
    rig.part("tail", tail, (0, 6.5, -7.0), "body")
    rig.part("leg_l", leg, (3.5, 1.5, 1.0), "body")
    rig.part("leg_r", leg.flipped_x(), (-3.5, 1.5, 1.0), "body")
    return rig


# ----------------------------------------------------------------------------- lumi

def lumi():
    glow = C("#ffd070", "emissive")
    glow_hi = C("#ffe6a0", "emissive")
    glow_d = C("#ffae52", "emissive")
    roof = P("#3f8a8c")
    roof_d = P("#2f6a74")
    leaf = P("#f3e3c0")

    core = Vox()
    sblob(core, 0, 7.5, 0, 5.6, 5.4, 5.6, glow, p=2.3)
    for (x, y, z) in list(core.v):
        if y >= 10 and z >= 0 and x <= 0:
            core.v[(x, y, z)] = glow_hi
        if y <= 4:
            core.v[(x, y, z)] = glow_d
    eyes(core, 6, x0=1, w=2, h=3, blush=False)
    for x in (4, -5):
        put_front(core, x, 5, C("#ff8f7a", "emissive"))
    put_front(core, -1, 4, EYE)
    put_front(core, 0, 4, EYE)

    body = Vox()                                      # chapéu de lanterna + argola + base
    for i, r in enumerate((7.2, 6.4, 5.2, 3.8, 2.2)):
        stack(body, 13 + i, 13 + i, lambda y: (0, 0, r, r), roof_d if i == 0 else roof, p=2.1)
    body.box(-1, 18, -1, 0, 18, 0, GOLD)
    for (x, y) in ((-2, 19), (-2, 20), (-1, 21), (0, 21), (1, 20), (1, 19)):
        body.set(x, y, 0, GOLD)
        body.set(x, y, -1, GOLD)
    stack(body, 1, 2, lambda y: (0, 0, 3.6 + 0.6 * (y - 1), 3.6 + 0.6 * (y - 1)), roof_d, p=2.1)
    shade(body)
    for (x, z) in ((6, 0), (-7, 0), (0, 6), (-1, -7)):   # pingentes dourados na aba
        body.set(x, 12, z, GOLD)

    tail = Vox()                                      # chama-rabicho
    for i, (r, ox) in enumerate(((2.4, 0), (2.0, 0.4), (1.6, 1.0), (1.2, 1.4), (0.8, 1.4), (0.6, 0.9))):
        stack(tail, -i, -i, lambda y: (ox, 0, r, r), glow if i < 3 else glow_d, p=2.0)

    lf = Vox()
    for i in range(5):
        lf.box(7 + i, 9 + (i + 1) // 2, -1, 7 + i, 10 + (i + 1) // 2 + (1 if 0 < i < 4 else 0), 0, leaf)
    shade(lf)

    ring = Vox()
    for k in range(5):
        a = k * 2 * math.pi / 5
        x, z = round(math.cos(a) * 10.5 - 0.5), round(math.sin(a) * 10.5 - 0.5)
        y = 7 + (k % 2) * 3
        ring.set(x, y, z, glow_hi)
        if k % 2 == 0:
            ring.set(x, y + 1, z, glow)
            ring.set(x + 1, y, z, glow)

    rig = Rig("Lumi (B)", 1 / 32, "floater", meta={"headsTall": 1.0})
    rig.part("body", body, (0, 7.5, 0))
    rig.part("core", core, (0, 7.5, 0), "body")
    rig.part("tail", tail, (0, 1.0, 0), "body")
    rig.part("leaf_l", lf, (6.5, 10.0, 0), "body")
    rig.part("leaf_r", lf.flipped_x(), (-6.5, 10.0, 0), "body")
    rig.part("ring", ring, (0, 7.5, 0), "body")
    return rig


# ----------------------------------------------------------------------------- fungo

def fungo():
    stem = P("#ecdcb8")
    cap = P("#b3475c")
    spot = P("#f6e6c6")
    gill = P("#e9c9a4")
    vine = P("#4f6b3e")
    thorn = P("#f0e0b0")
    foot = P("#6b3f4a")

    b = Vox()
    stack(b, 2, 16, lambda y: (0, 0.3, 5.8 - 1.2 * ((y - 2) / 14.0) ** 1.4, 5.4 - 1.2 * ((y - 2) / 14.0) ** 1.4),
          stem, p=2.5)
    shade(b)
    # rosto rabugento: olhos semicerrados, sobrancelha inclinada, boca torta com dente
    for xs, inner in ((2, 2), (-4, -3)):
        for x in (xs, xs + 1):
            put_front(b, x, 8, EYE)
            put_front(b, x, 9, EYE)
        put_front(b, xs if xs > 0 else xs + 1, 9, EYE_HI)
    for (x, y) in ((1, 10), (2, 11), (3, 11), (4, 12), (-2, 10), (-3, 11), (-4, 11), (-5, 12)):
        put_front(b, x, y, foot)
    for (x, y) in ((-2, 5), (-1, 6), (0, 6), (1, 5)):
        put_front(b, x, y, MOUTH)
    put_front(b, -1, 5, thorn)
    # colar de espinheiro na base
    for k in range(12):
        a = k * 2 * math.pi / 12
        x, z = round(math.cos(a) * 6.2 - 0.5), round(math.sin(a) * 6.0 - 0.2)
        b.box(x, 2, z, x, 3 + k % 2, z, vine)
        if k % 3 == 0:
            b.set(x + (1 if x >= 0 else -1), 4, z, thorn)

    c = Vox()
    sblob(c, 0, 13.0, 0, 10.5, 7.0, 10.5, cap, p=2.2)
    for (x, y, z) in list(c.v):
        if y < 13:
            del c.v[(x, y, z)]
    stack(c, 12, 12, lambda y: (0, 0, 9.4, 9.4), gill, p=2.2)
    stack(c, 13, 13, lambda y: (0, 0, 11.0, 11.0), cap, p=2.2)
    shade(c)
    for (sx, sz, r) in ((-5, 4, 2.3), (4, 6, 1.6), (6, -2, 2.0), (-3, -6, 2.4), (1, 0, 1.5), (-8, -2, 1.4), (2, -8, 1.3)):
        for (x, y, z) in list(c.v):                   # pintas grandes (na pele do chapéu)
            if y >= 14 and (x, y + 1, z) not in c.v or (y >= 14 and (x, y, z + 1) not in c.v and z > 0):
                if (x + 0.5 - sx) ** 2 + (z + 0.5 - sz) ** 2 <= r * r:
                    c.v[(x, y, z)] = spot
    for (x, z, h) in ((-2, -1, 3), (5, 2, 2), (-6, 1, 2), (1, -6, 2)):   # espinhos
        y0 = max(k[1] for k in c.v if k[0] == x and k[2] == z) + 1
        c.box(x, y0, z, x, y0 + h - 1, z, thorn)
        c.box(x - 1, y0, z, x + 1, y0, z, vine)
        c.box(x, y0, z - 1, x, y0, z + 1, vine)
        c.set(x, y0, z, thorn)
    c.translate(0, 3, 0)

    arm = Vox()
    for (x, y) in ((6, 9), (7, 9), (8, 8), (9, 7), (9, 6), (10, 5)):
        arm.box(x, y, 0, x, y, 1, vine)
    arm.set(8, 9, 0, thorn)
    arm.set(10, 7, 1, thorn)
    arm.box(10, 4, 0, 11, 4, 1, shadow(vine))

    leg = Vox()
    leg.box(1, 0, -2, 4, 1, 3, foot)
    leg.box(2, 0, 4, 3, 0, 4, foot)
    leg.set(1, 1, -2, None)
    leg.set(4, 1, -2, None)
    shade(leg)

    rig = Rig("Fungo (B)", 1 / 28, "hopper", meta={"headsTall": 1.0})
    rig.part("body", b, (0, 0, 0))
    rig.part("cap", c, (0, 16.0, 0), "body")
    rig.part("arm_l", arm, (5.5, 9.5, 0.5), "body")
    rig.part("arm_r", arm.flipped_x(), (-5.5, 9.5, 0.5), "body")
    rig.part("leg_l", leg, (2.5, 2.0, 0.5), "body")
    rig.part("leg_r", leg.flipped_x(), (-2.5, 2.0, 0.5), "body")
    return rig


# ----------------------------------------------------------------------------- tortuga

def tortuga():
    skin = P("#a8b574")
    skin_d = P("#8a9a62")
    belly = P("#eadfb4")
    shell = P("#b56c48")
    seam = P("#8c4c3c")
    rim = P("#ecd29c")
    grass = P("#8fc04e", "foliage")
    grass_l = P("#b4d45c", "foliage")
    leafc = P("#5fa85a", "foliage")
    bark = P("#8a5a3c")
    nail = P("#f3e6c4")

    b = Vox()
    sblob(b, 0, 11.0, 0, 12.5, 5.5, 16.5, belly, p=2.6)
    sh = Vox()
    sblob(sh, 0, 12.0, -1.0, 15.5, 14.0, 18.5, shell, p=2.3)
    for (x, y, z) in list(sh.v):
        if y < 12:
            del sh.v[(x, y, z)]
    stack(sh, 10, 11, lambda y: (0, -1.0, 16.4, 19.4), rim, p=2.3)
    # placas: costuras hexagonais aproximadas sobre a superfície
    for (x, y, z) in list(sh.v):
        if sh.v[(x, y, z)] != shell:
            continue
        u = x + (4 if ((z + 40) // 9) % 2 else 0)
        if (u + 40) % 9 == 0 or (z + 40) % 9 == 0:
            sh.v[(x, y, z)] = seam
    # jardim: o topo do casco vira gramado
    for (x, y, z) in list(sh.v):
        if (x, y + 1, z) in sh.v:
            continue
        n = normal(sh, x, y, z)
        if n[1] > 0.80 - 0.1 * hash3(x // 2, 0, z // 2, 3):
            sh.v[(x, y, z)] = grass_l if hash3(x, 1, z, 5) > 0.7 else grass
            if hash3(x, 2, z, 7) > 0.86:
                sh.set(x, y + 1, z, grass_l)
    shade(sh, only={shell, rim, seam})
    b.merge(sh)
    shade(b, only={belly})
    top = max(k[1] for k in b.v if abs(k[0]) < 2 and abs(k[2]) < 2)
    # arvorezinha torta
    tx, tz = -4, -5
    for i in range(6):
        b.box(tx + i // 3, top + i - 1, tz, tx + 1 + i // 3, top + i - 1, tz + 1, bark)
    cn = Vox()
    sblob(cn, tx + 2.5, top + 7.0, tz + 1, 5.2, 4.0, 5.0, leafc, p=2.2)
    sblob(cn, tx - 1.0, top + 6.5, tz + 2.5, 3.2, 2.6, 3.2, leafc, p=2.2)
    shade(cn, hi=0.45)
    b.merge(cn)
    # pedra, cogumelos e flores
    sblob(b, 6, top - 1.0, 4, 3.0, 2.4, 2.6, P("#b9ada6"), p=2.4)
    for (x, z, h, col) in ((7, -6, 3, "#d9644a"), (9, -3, 2, "#d9644a"), (-9, 3, 2, "#f2c14e")):
        y0 = max(k[1] for k in b.v if k[0] == x and k[2] == z) + 1
        b.box(x, y0, z, x, y0 + h - 1, z, nail)
        cc = P(col)
        b.box(x - 1, y0 + h, z - 1, x + 1, y0 + h, z + 1, cc)
        b.set(x, y0 + h + 1, z, light(cc))
    for i, (x, z) in enumerate(((2, 8), (-6, 9), (-10, -4), (3, -11), (10, 6), (-2, 3), (0, -14), (-8, -12))):
        ys = [k[1] for k in b.v if k[0] == x and k[2] == z]
        if not ys:
            continue
        y0 = max(ys) + 1
        b.set(x, y0, z, leafc)
        b.set(x, y0 + 1, z, [C("#f6e9d0"), C("#f29a8c"), C("#f2c14e"), C("#c9a0e8")][i % 4])

    def leg(fx, fz):
        l = Vox()
        stack(l, 0, 10, lambda y: (fx, fz, 4.4 - 0.5 * (y / 10.0), 4.6 - 0.5 * (y / 10.0)), skin, p=2.4)
        l.paint(lambda x, y, z, c: skin_d if y <= 1 else None)
        shade(l)
        for dx in (-3, -1, 1):
            zf = max(k[2] for k in l.v if k[1] == 0)
            l.set(int(fx) + dx, 0, zf + 1, nail)
            l.set(int(fx) + dx, 1, zf + 1, nail)
        return l

    hd = Vox()
    for i in range(8):                                # pescoço curvo
        sblob(hd, 0, 11.5 + i * 0.75, 15 + i * 1.1, 4.4, 4.2, 3.0, skin, p=2.3)
    sblob(hd, 0, 18.5, 25.0, 7.0, 6.2, 7.2, skin, p=2.5, p_lo=3.0)
    for (x, y, z) in list(hd.v):
        if y <= 15 and z >= 21:
            hd.v[(x, y, z)] = belly
    shade(hd)
    for xs in (3, -5):
        for x in (xs, xs + 1):
            for y in (18, 19, 20):
                put_front(hd, x, y, EYE)
        put_front(hd, xs, 20, EYE_HI)
    for x in (5, 6, -6, -7):
        put_front(hd, x, 17, BLUSH)
    for (x, y) in ((-3, 15), (-2, 14), (-1, 14), (0, 14), (1, 14), (2, 15)):
        put_front(hd, x, y, shadow(skin_d, 1.4))
    put_front(hd, -2, 17, skin_d)
    put_front(hd, 1, 17, skin_d)
    hd.box(-1, 25, 24, 0, 26, 25, leafc)              # brotinho na cabeça
    hd.box(-3, 27, 24, -1, 27, 25, grass_l)
    hd.box(0, 27, 24, 2, 28, 25, grass)

    def ear():
        e = Vox()
        for i in range(5):
            e.box(7 + i, 20 + i // 2, 22, 7 + i, 22 + i // 2 - (i // 3), 23, skin_d if i < 4 else rim)
        return e

    tl = Vox()
    for i in range(6):
        sblob(tl, 0, 9.0 - i * 0.6, -17.5 - i * 1.2, 2.6 - i * 0.35, 2.4 - i * 0.3, 1.6, skin, p=2.2)
    shade(tl)

    rig = Rig("Tortuga (B)", 1 / 16, "quadruped", meta={"headsTall": 1.0})
    rig.part("body", b, (0, 10.0, 0))
    rig.part("head", hd, (0, 12.0, 15.0), "body")
    rig.part("ear_l", ear(), (7.0, 21.0, 22.5), "head")
    rig.part("ear_r", ear().flipped_x(), (-7.0, 21.0, 22.5), "head")
    rig.part("leg_fl", leg(9.0, 10.0), (9.0, 10.0, 10.0), "body")
    rig.part("leg_fr", leg(-9.0, 10.0), (-9.0, 10.0, 10.0), "body")
    rig.part("leg_bl", leg(9.0, -11.0), (9.0, 10.0, -11.0), "body")
    rig.part("leg_br", leg(-9.0, -11.0), (-9.0, 10.0, -11.0), "body")
    rig.part("tail", tl, (0, 9.0, -17.0), "body")
    return rig


def build(out):
    pompom().save(f"{out}/creatures/b/pompom")
    lumi().save(f"{out}/creatures/b/lumi")
    fungo().save(f"{out}/creatures/b/fungo")
    tortuga().save(f"{out}/creatures/b/tortuga")
