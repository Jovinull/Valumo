"""Direção D — criaturas originais em voxel fino.

pompom  : bolinha felpuda com broto na cabeça e orelhas-folha (hopper)
lumi    : espírito-lanterna com miolo emissivo, telhadinho e contas orbitando (floater)
fungo   : cogumelo rabugento de chapéu espinhento (hopper)
tortuga : gigante mansa com um jardim inteiro no casco (quadruped)
"""
import math

from chars_d import blob, decal, faces, fill_pits, hash3, lerp, lock, ring, shade, stack, sup, tone_c
from voxlib import C, Rig, Vox, tone

EYE = C("#231a2e")
WHITE = C("#ffffff")


def save(rig, out, key):
    n = sum(faces(v) for _, v, _, _ in rig.parts)
    b = rig.all_voxels().bounds()
    rig.meta["faces"] = n
    print(f"  {key}: {b[3]-b[0]+1}x{b[4]-b[1]+1}x{b[5]-b[2]+1} vox, {(b[4]-b[1]+1) * rig.voxel_size:.2f} u, {n} faces")
    rig.save(f"{out}/creatures/d/{key}")


def eye(v, x0, y0, w=3, h=3, hi_left=True, zmin=0):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            decal(v, x, y, EYE, zmin)
    decal(v, x0 if hi_left else x0 + w - 1, y0 + h - 1, WHITE, zmin)


# ----------------------------------------------------------------------------- pompom

def pompom():
    VS = 1 / 40
    fur, fur_d, belly = C("#f6e7c6"), C("#e0a060"), C("#fff8e6")
    teal = C("#3fc0ac")
    b = Vox()
    sup(b, 0, 10, 0, 9.4, 8.6, 9.2, fur, p=2.2)
    for (x, y, z, r) in ((9, 8, 2, 3.0), (-9, 8, 2, 3.0), (0, 18, 1, 3.2), (6, 4, -5, 3.5), (-6, 4, -5, 3.5),
                         (0, 12, -8.5, 3.6), (5, 15, -5, 3.4), (-5, 15, -5, 3.4)):
        sup(b, x, y, z, r, r, r, fur, p=2.0)      # tufos: silhueta felpuda
    for k in [k for k in b.v if k[1] < 1]:
        del b.v[k]
    fill_pits(b, fur, need=4, passes=2)
    for k in list(b.v):                            # manto caramelo nas costas, barriga clara
        x, y, z = k
        if z < -1 + 0.35 * (y - 10) - 0.04 * x * x and y > 5:
            b.v[k] = fur_d
        elif y < 7 and z > 0 and abs(x + 0.5) < 6:
            b.v[k] = belly
    shade(b, amp=0.8, lo=-1.5)
    eye(b, 3, 10)
    eye(b, -6, 10)
    for x in (-1, 0):
        decal(b, x, 9, C("#e8788a"))
    decal(b, -1, 8, C("#a85a50"))
    decal(b, 0, 8, C("#a85a50"))
    for x in (6, 7, -7, -8):
        decal(b, x, 8, C("#f6a69a"))
    # broto na cabeça (folhagem: balança com o vento)
    leaf, leaf_l = C("#58b848", "foliage"), C("#a8e05a", "foliage")
    b.box(-1, 19, 0, 0, 23, 1, leaf)
    for s in (1, -1):
        for i in range(6):
            x = (1 + i) if s > 0 else (-2 - i)
            y = 23 + round(2.2 * math.sin(i / 5 * math.pi * 0.9))
            wd = 2 if 1 <= i <= 3 else 1
            b.box(x, y, 1 - wd, x, y, wd, leaf_l if i > 2 else leaf)

    def ear():
        e = Vox()
        for i in range(15):
            u = i / 14
            cx, cy, cz = 5 + 3.5 * u, 16 + i, -1 - 5 * u * u
            r = 1.2 + 1.6 * math.sin(min(1, u * 1.2) * math.pi) ** 0.7
            c = teal if u > 0.72 else fur
            for x in range(int(cx - r), int(cx + r) + 1):
                e.box(x, round(cy), round(cz), x, round(cy), round(cz) + 1, c)
            if 0.15 < u < 0.7:
                e.box(round(cx - 0.5), round(cy), round(cz) + 1, round(cx + 0.5), round(cy), round(cz) + 1, C("#f4a8a0"))
        shade(e, amp=0.6, skip=(C("#f4a8a0"),))
        return e
    el = ear()
    er = el.flipped_x()
    tail = Vox()
    sup(tail, 0, 8, -11.5, 3.8, 3.8, 3.8, fur, p=2.0)
    sup(tail, 0, 9.5, -13.5, 2.4, 2.4, 2.4, teal, p=2.0)
    shade(tail, amp=0.8)

    def foot():
        f = Vox()
        sup(f, 4.5, 1.2, 4.5, 2.8, 2.2, 4.0, fur_d, p=2.4, ymin=0)
        shade(f, amp=0.8)
        return f
    fl = foot()
    rig = Rig("Pompom (D)", VS, "hopper", meta={})
    rig.part("body", b, (0, 0, 0))
    rig.part("ear_l", el, (5.5, 16, -0.5), "body")
    rig.part("ear_r", er, (-5.5, 16, -0.5), "body")
    rig.part("tail", tail, (0, 8, -9), "body")
    rig.part("leg_l", fl, (4.5, 2, 2), "body")
    rig.part("leg_r", fl.flipped_x(), (-4.5, 2, 2), "body")
    return rig


# ----------------------------------------------------------------------------- lumi

def lumi():
    VS = 1 / 40
    Y0 = 16                                        # flutua: base do corpo acima do chão
    roof, roof_l = C("#2f6f8a"), C("#7fd0d8")
    gold = C("#f2c14a", "metal")
    gold_d = C("#c28a2a", "metal")
    glow, glow_l, glow_d = C("#e8973a", "emissive"), C("#f6c060", "emissive"), C("#c86a2a", "emissive")
    wisp, wisp_l = C("#7ff2e0", "emissive"), C("#d8fff6", "emissive")
    cy = Y0 + 9
    core = Vox()
    for (x, y, z) in list(sup(Vox(), 0, cy, 0, 7.2, 7.6, 7.2, glow, p=2.3).v):
        d = math.sqrt((x + 0.5) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5) ** 2)
        core.set(x, y, z, glow_d if (y < cy - 4 or d > 7.6) else (glow_l if (y > cy + 2 and z > 0 and x < 2) else glow))
    for x0 in (2, -4):                             # rostinho
        for x in range(x0, x0 + 2):
            for y in range(cy - 1, cy + 2):
                decal(core, x, y, EYE)
    for x, y in ((-1, cy - 3), (0, cy - 3), (-2, cy - 2), (1, cy - 2)):
        decal(core, x, y, EYE)
    for x in (5, 6, -6, -7):
        decal(core, x, cy - 2, C("#e0603a", "emissive"))

    body = Vox()
    # telhadinho de lanterna: cone com beiral arrebitado, pináculo e argola
    for i in range(8):
        u = i / 7
        r = lerp(10.5, 2.0, u ** 0.7)
        ring(body, cy + 7 + i, 0, 0, r, r, roof, 2.6)
    ring(body, cy + 6, 0, 0, 9.0, 9.0, gold_d, 2.6)
    for (x, z) in ((10, 10), (-11, 10), (10, -11), (-11, -11)):
        sx, sz = (1 if x > 0 else -1), (1 if z > 0 else -1)
        body.box(x - sx * 2, cy + 7, z - sz * 2, x - sx, cy + 8, z - sz, roof_l)
        body.box(x - sx, cy + 8, z - sz, x, cy + 9, z, roof_l)
        body.set(x, cy + 10, z, gold)
    for k in list(body.v):
        if body.v[k] == roof and (k[0], k[1] + 1, k[2]) not in body.v and (k[1] - cy) % 3 == 0:
            body.v[k] = roof_l
    body.box(-1, cy + 15, -1, 0, cy + 16, 0, gold)
    for a in range(12):
        x, y = round(2.6 * math.cos(a * math.pi / 6) - 0.5), cy + 19 + round(2.6 * math.sin(a * math.pi / 6))
        body.set(x, y, 0, gold)
        body.set(x, y, -1, gold)
    # pratinho de baixo + quatro hastes
    ring(body, cy - 8, 0, 0, 5.5, 5.5, gold_d, 2.4)
    ring(body, cy - 9, 0, 0, 3.5, 3.5, gold_d, 2.4)
    for (x, z) in ((7, 0), (-8, 0), (0, 7), (0, -8)):
        pass
    shade(body, amp=0.8)

    tail = Vox()
    for i in range(12):
        u = i / 11
        r = 3.2 * (1 - u) ** 0.8 + 0.4
        cx = 1.6 * math.sin(u * 4.0)
        sup(tail, cx, cy - 10 - i, 0, r, 1.0, r, wisp_l if u < 0.3 else wisp, p=2.0)

    def wing():
        w = Vox()
        leaf, leaf_l = C("#49b89a", "foliage"), C("#a6ecc0", "foliage")
        for i in range(9):
            u = i / 8
            x = 8 + i
            h = round(1 + 3.2 * math.sin(u * math.pi) ** 0.8)
            y = cy + 1 + round(2 * u)
            w.box(x, y - h, 0, x, y + h // 2, 0, leaf_l if i > 4 else leaf)
            w.set(x, y - h // 3, 0, tone(leaf, -1))
        return w
    wl = wing()
    ringp = Vox()
    for i in range(6):
        a = i * math.pi / 3
        x, z = round(13 * math.cos(a) - 0.5), round(13 * math.sin(a) - 0.5)
        if i % 2:
            ringp.box(x, cy - 3, z, x + 1, cy - 2, z + 1, wisp)
        else:
            ringp.box(x, cy - 4, z, x + 1, cy - 2, z + 1, gold)
            ringp.set(x, cy - 1, z, wisp_l)

    rig = Rig("Lumi (D)", VS, "floater", meta={})
    rig.part("body", body, (0, cy, 0))
    rig.part("core", core, (0, cy, 0), "body")
    rig.part("ring", ringp, (0, cy - 3, 0), "body")
    rig.part("wing_l", wl, (7.5, cy + 1, 0.5), "body")
    rig.part("wing_r", wl.flipped_x(), (-7.5, cy + 1, 0.5), "body")
    rig.part("tail", tail, (0, cy - 9, 0), "body")
    return rig


# ----------------------------------------------------------------------------- fungo

def fungo():
    VS = 1 / 36
    stem, stem_d = C("#e9dfc8"), C("#b9a88e")
    capc, spot = C("#9a3470"), C("#f8d8b8")
    thorn = C("#4a2f3a")
    eyec = C("#ffe24a", "emissive")
    b = Vox()
    stack(b, 2, 18, lambda u: (0, 0, lerp(8.6, 7.2, u) + 0.9 * math.sin(u * 3.1), lerp(8.0, 6.8, u) + 0.9 * math.sin(u * 3.1)),
          stem, 2.3)
    for k in list(b.v):
        if k[1] < 5 and hash3(k[0] // 2, 0, k[2] // 2, 3) < 0.5:
            b.v[k] = stem_d
    shade(b, amp=0.8, lo=-1.5)
    # cara brava: olhos emissivos inclinados, sobrancelha grossa, boca serrilhada
    for s in (1, -1):
        for i in range(3):
            x = (2 + i) if s > 0 else (-3 - i)
            for y in (10, 11, 12):
                decal(b, x, y, eyec)
            if i > 0:
                decal(b, x, 13, eyec)
            decal(b, x, 14 if i > 0 else 13, thorn)
            decal(b, x, 15 if i > 1 else 14, thorn)
        decal(b, 5 if s > 0 else -6, 16, thorn)
        decal(b, 3 if s > 0 else -4, 11, thorn)               # pupila
        decal(b, 3 if s > 0 else -4, 12, thorn)
    for x in range(-4, 4):
        decal(b, x, 7 if x % 2 else 6, thorn)
        decal(b, x, 6 if x % 2 else 7, WHITE)
        decal(b, x, 5, thorn)

    cap = Vox()
    sup(cap, 0, 15, -1, 12.5, 10.5, 12.0, capc, p=2.2, ymin=15)
    ring(cap, 14, 0, -1, 11.5, 11.0, C("#f1d9c4"), 2.2)        # lamelas
    for k in list(cap.v):
        if cap.v[k] == capc:
            x, y, z = k
            for (sx, sy, sz, r) in ((5, 22, 6, 2.6), (-7, 20, 3, 2.2), (-2, 24, -3, 2.8), (8, 19, -6, 2.3), (-8, 18, -8, 2.0),
                                    (0, 19, 11, 1.8), (11, 17, 2, 1.6)):
                if (x - sx) ** 2 + (y - sy) ** 2 + (z - sz) ** 2 <= r * r:
                    cap.v[k] = spot
    shade(cap, amp=1.0, skip=(spot,))
    for i in range(9):                                        # espinhos na aba e no topo
        a = i * 2 * math.pi / 9 + 0.35
        x0, z0 = 11.4 * math.cos(a), 11.0 * math.sin(a) - 1
        blob(cap, (x0, 16.5, z0), (x0 * 1.38, 19.5, z0 * 1.38), 1.5, 0.5, thorn)
    blob(cap, (2, 25, 1), (4, 30, 0), 1.7, 0.5, thorn)
    blob(cap, (-4, 24, -3), (-8, 28, -5), 1.5, 0.5, thorn)

    cap.translate(0, 3, 0)

    def arm():
        a = Vox()
        vine = C("#6a8a3a")
        lock(a, (7.5, 12, 0), (12, 12, 1), (13, 6, 3), 1.7, 1.2, vine)
        for (x, y, z, dx, dz) in ((13, 5, 3, 1, 1), (13, 5, 3, -1, 1), (13, 4, 3, 0, 2)):
            blob(a, (x, y, z), (x + dx * 2, y - 2, z + dz * 1.5), 0.9, 0.4, thorn)
        a.set(10, 12, 1, thorn)
        a.set(12, 9, 2, thorn)
        shade(a, amp=0.7)
        return a

    def foot():
        f = Vox()
        sup(f, 5.0, 1.4, 2.5, 3.0, 2.4, 4.6, stem_d, p=2.4, ymin=0)
        shade(f, amp=0.8)
        return f
    al, fl = arm(), foot()
    rig = Rig("Fungo (D)", VS, "hopper", meta={})
    rig.part("body", b, (0, 0, 0))
    rig.part("cap", cap, (0, 18, 0), "body")
    rig.part("arm_l", al, (7.5, 12, 0.5), "body")
    rig.part("arm_r", al.flipped_x(), (-7.5, 12, 0.5), "body")
    rig.part("leg_l", fl, (4, 2.5, 0.5), "body")
    rig.part("leg_r", fl.flipped_x(), (-4, 2.5, 0.5), "body")
    return rig


# ----------------------------------------------------------------------------- tortuga

def tortuga():
    VS = 1 / 22
    skin, skin_l = C("#86ab7c"), C("#e9e2b8")
    shell, shell_d, shell_rim = C("#a8845a"), C("#6e5238"), C("#d9b878")
    grass = C("#5cb04a", "foliage")
    b = Vox()
    SY = 15                                                    # base do casco
    # corpo/plastrão
    sup(b, 0, SY, -2, 17, 6, 21, skin, p=2.4)
    for k in list(b.v):
        if k[1] < SY - 2:
            b.v[k] = skin_l
    # casco
    sup(b, 0, SY + 1, -2, 20, 15.5, 24, shell, p=2.25, ymin=SY)
    for k in list(b.v):
        if b.v[k] != shell:
            continue
        x, y, z = k
        if y <= SY + 2:
            b.v[k] = shell_rim if (math.atan2(z + 2, x) * 7 / math.pi) % 2 > 0.25 else shell_d
        else:                                                  # placas hexagonais (costuras escuras)
            u, w = (x + 0.5) / 7.0, (z + 2.5) / 7.5 + (0.5 if int(math.floor((x + 0.5) / 7.0)) % 2 else 0)
            fu, fw = abs(u - math.floor(u) - 0.5), abs(w - math.floor(w) - 0.5)
            if fu > 0.43 or fw > 0.44:
                b.v[k] = shell_d
    shade(b, amp=0.9)
    # jardim: tapete de grama no topo do casco
    top = {}
    for (x, y, z), c in b.v.items():
        if y > SY + 8 and (x, z) not in top or y > top.get((x, z), -99):
            if y > SY + 8:
                top[(x, z)] = y
    gr = [tone_c(grass, k) for k in (-0.75, 0, 0.75)]
    for (x, z), y in top.items():
        h = hash3(x, 0, z, 5)
        b.set(x, y + 1, z, gr[1 if h < 0.6 else (2 if h < 0.85 else 0)])
        if y > SY + 12 and h > 0.9:
            b.set(x, y + 2, z, gr[2])
        if hash3(x, 1, z, 9) < 0.06 and y < SY + 13:           # musgo escorrendo
            for d in range(1, 3):
                if (x, y - d, z) in b.v:
                    b.set(x, y - d, z, gr[0])
    ty = SY + 17

    def gy(x, z):
        return top.get((x, z), ty - 4) + 2
    # arvorezinha torta
    bark = C("#6a4630")
    lock(b, (-5, ty - 1, -7), (-7, ty + 5, -8), (-4, ty + 9, -6), 2.0, 1.3, bark)
    leafs = [tone_c(C("#4fa84a", "foliage"), k) for k in (-1.5, -0.75, 0, 0.75)]
    cv = Vox()
    for (x, y, z, r) in ((-4, ty + 12, -6, 5.6), (-8, ty + 10, -9, 4.0), (0, ty + 10, -4, 4.0), (-5, ty + 15, -7, 3.6)):
        sup(cv, x, y, z, r, r * 0.8, r, leafs[2], p=2.3)
    for (x, y, z) in list(cv.v):
        d = 0
        while d < 4 and (x, y + d + 1, z) in cv.v:
            d += 1
        cv.v[(x, y, z)] = leafs[3] if d == 0 else (leafs[2] if d < 2 else (leafs[1] if (x, y - 1, z) in cv.v else leafs[0]))
    b.merge(cv)
    # flores, cogumelos luminosos e pedras
    for i, (x, z) in enumerate(((6, 4), (9, -6), (3, -14), (-10, 3), (5, 10), (-3, 8), (11, 1), (-12, -12), (1, 2))):
        y = gy(x, z)
        if i % 3 == 2:
            b.box(x, y, z, x, y + 1, z, C("#f3ead2"))
            b.box(x - 1, y + 2, z - 1, x + 1, y + 2, z + 1, C("#6fe8ff", "emissive"))
            b.set(x, y + 3, z, C("#c8fbff", "emissive"))
        else:
            pc = (C("#ff7a9a"), C("#ffd84a"), C("#ffffff"), C("#c890ff"))[i % 4]
            b.box(x, y, z, x, y + 1, z, gr[0])
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                b.set(x + d[0], y + 2, z + d[1], pc)
            b.set(x, y + 2, z, C("#ffb020"))
    for (x, z, r) in ((8, -12, 2.6), (-9, -3, 2.0)):
        sup(b, x, gy(x, z) + 0.5, z, r, r * 0.8, r, C("#9aa3ad"), p=2.6)
    # lanterninha de pedra no jardim
    lx, lz = 7, -1
    ly = gy(lx, lz)
    b.box(lx, ly, lz, lx + 1, ly + 2, lz + 1, C("#8a929c"))
    b.box(lx, ly + 3, lz, lx + 1, ly + 4, lz + 1, C("#ffd27a", "emissive"))
    b.box(lx - 1, ly + 5, lz - 1, lx + 2, ly + 5, lz + 2, C("#7a828c"))
    b.box(lx, ly + 6, lz, lx + 1, ly + 6, lz + 1, C("#7a828c"))

    # cabeça + pescoço
    hd = Vox()
    lock(hd, (0, SY, 17), (0, SY + 3, 25), (0, SY + 8, 30), 5.0, 4.4, skin)
    sup(hd, 0, SY + 9, 33, 7.0, 6.2, 8.0, skin, p=2.4)
    for k in list(hd.v):
        if k[1] < SY + 6 and k[2] > 27 or (k[1] < SY - 1):
            hd.v[k] = skin_l
    fill_pits(hd, skin, need=4)
    shade(hd, amp=0.8)
    for s in (1, -1):                                          # olhos nas laterais-frente, dóceis
        for x in ((4, 5) if s > 0 else (-6, -5)):
            for y in range(SY + 10, SY + 13):
                decal(hd, x, y, EYE, zmin=30)
        decal(hd, 4 if s > 0 else -6, SY + 12, WHITE, zmin=30)
        decal(hd, 6 if s > 0 else -7, SY + 9, C("#f0a090"), zmin=30)
        hd.set(2 * s if s > 0 else -3, SY + 8, 40, EYE)      # narinas
    for x in range(-4, 4):
        decal(hd, x, SY + 6 + (1 if abs(x + 0.5) > 3 else 0), tone(skin, -2), zmin=30)
    hd.box(-1, SY + 15, 31, 0, SY + 17, 32, grass)             # folhinha na cabeça
    hd.box(1, SY + 17, 31, 3, SY + 17, 32, tone_c(grass, 0.75))

    def legp(front):
        l = Vox()
        cz = 14 if front else -17
        stack(l, 0, SY - 1, lambda u: (12.5, cz, lerp(5.8, 5.0, u), lerp(6.2, 5.2, u)), skin, 2.6)
        for k in list(l.v):
            if k[1] < 2:
                l.v[k] = tone(skin, -1.5)
        shade(l, amp=0.8)
        for dx in (-3, 0, 3):
            l.box(12 + dx, 0, cz + 6, 13 + dx, 1, cz + 6, skin_l)
        return l
    lf, lb = legp(True), legp(False)
    tl = Vox()
    blob(tl, (0, SY - 1, -21), (0, SY - 4, -29), 3.2, 0.8, skin)
    shade(tl, amp=0.8)

    rig = Rig("Tortuga (D)", VS, "quadruped", meta={})
    rig.part("body", b, (0, SY, 0))
    rig.part("head", hd, (0, SY + 1, 18), "body")
    rig.part("leg_fl", lf, (12.5, SY - 1, 14), "body")
    rig.part("leg_fr", lf.flipped_x(), (-12.5, SY - 1, 14), "body")
    rig.part("leg_bl", lb, (12.5, SY - 1, -17), "body")
    rig.part("leg_br", lb.flipped_x(), (-12.5, SY - 1, -17), "body")
    rig.part("tail", tl, (0, SY - 1, -21), "body")
    return rig


def build(out):
    print("creatures_d:")
    save(pompom(), out, "pompom")
    save(lumi(), out, "lumi")
    save(fungo(), out, "fungo")
    save(tortuga(), out, "tortuga")
