"""Direção C — criaturas originais de cuboides com pixel art (1 texel = 1/32 u).

pompom  — bolinha de pelo saltadora com orelhas terminadas em pompons (anim "hopper")
lumi    — espírito-lanterna: chama com rosto dentro de uma lanterninha dourada (anim "floater")
fungo   — cogumelo-espinheiro ranzinza, inimigo simples (anim "hopper")
tortuga — jabuti gigante e manso com um jardim no casco, ~2,2 u (anim "quadruped")
"""
import math

from cuboidlib import Canvas, CuboidRig, M, box, bricks, fill, hsh, mirror_x, prism, spr, tone
from env_c import (GRASS_A, GRASS_SIDE, OAK2, TUFT, cross, flower_sprite, leafblock, mushroom_red, stone_mat,
                   trunk)


def rounded(w, h, col, r=2):
    """Retângulo com cantos chanfrados em degraus (para prismas 'arredondados')."""
    c = Canvas(w, h, col)
    for i in range(r):
        n = r - i
        for x in list(range(n)) + list(range(w - n, w)):
            c.set(x, i, None)
            c.set(x, h - 1 - i, None)
    return c


# ----------------------------------------------------------------------------- pompom

def pompom():
    fur, fur_d, pom = "sand2", "sand1", "pink1"
    front = rounded(12, 10, fur, 2)
    front.rect(2, 0, 3, 1, "white").rect(1, 1, 2, 1, "white")
    for x in range(12):
        if front.p[9][x]:
            front.p[9][x] = fur_d
    side = Canvas(10, 10, fur).hline(9, fur_d).rect(1, 1, 3, 1, "white")
    body = prism(front, 10, (-6, 2, -5), back=rounded(12, 10, fur, 2).tone_rect(0, 8, 12, 2, -1),
                 faces={"px": side, "nx": side.flip_h(), "py": Canvas(12, 10, fur).rect(2, 2, 4, 2, "white"), "ny": fill(12, 10, fur_d)})
    face = spr("""
        CCCCCCCCCC
        CWkCCCCWkC
        CkkCppCkkC
        bbCCCCCCbb
        CCwwwwwwCC
        cCwwwwwwCc""", "C=sand2 c=sand1 w=white k=ink W=white p=pink0 b=blush")
    cheek = Canvas(8, 6, fur).hline(5, fur_d)
    body.append(box(-5, 3, -6.2, 10, 6, 12.4, M(fur, up=0), {"pz": face, "nz": Canvas(10, 6, fur).hline(5, fur_d)}))
    body.append(box(-7.2, 3, -4, 14.4, 6, 8, M(fur, up=0), {"px": cheek, "nx": cheek}))
    body.append(box(-1.5, 11.5, 0, 3, 2, 3, M(fur, up=1, bottom=0), rot=(0, 0, 20), origin=(0, 12, 1.5)))   # topete

    def ear(sign):
        o = (sign * 3.2, 11.5, 0)
        r = (0, 0, -14 * sign)
        inner = Canvas(3, 7, fur).rect(1, 1, 1, 5, pom)
        return [box(sign * 3.2 - 1.5, 11, -1, 3, 7, 2, M(fur, bottom=0), {"pz": inner}, rot=r, origin=o),
                box(sign * 3.2 - 2, 17.5, -2, 4, 4, 4, M(pom, up=1, bottom=-1), rot=r, origin=o)]
    foot = box(1.2, 0, -2.5, 4, 2.5, 7, M("pink0", up=1, bottom=-1))
    tail = [box(-2.2, 4, -8.6, 4.4, 4.4, 4.4, M(pom, up=1, bottom=-1), rot=(25, 0, 0), origin=(0, 6, -6.5))]

    rig = CuboidRig("Pompom (C)", "hopper")
    rig.part("body", body, (0, 2, 0))
    rig.part("ear_l", ear(1), (3.2, 11.5, 0), "body")
    rig.part("ear_r", ear(-1), (-3.2, 11.5, 0), "body")
    rig.part("tail", tail, (0, 6, -5), "body")
    rig.part("leg_l", [foot], (3, 2.5, 0), "body")
    rig.part("leg_r", mirror_x([foot]), (-3, 2.5, 0), "body")
    return rig


# ----------------------------------------------------------------------------- lumi

def lumi():
    gold = M("gold1", top=1, bottom=-1, up=1, down=-1)
    dark = M("gold0", top=0, bottom=0, up=1, down=-1)
    lantern = [box(-5.5, 6, -5.5, 11, 2, 11, gold)]
    for sx in (-1, 1):
        for sz in (-1, 1):
            lantern.append(box(sx * 4.75 - 0.75, 8, sz * 4.75 - 0.75, 1.5, 9, 1.5, dark))
    roof = Canvas(13, 2, "gold1").hline(0, "gold2")
    for x in range(0, 13, 3):
        roof.set(x, 1, "gold0")
    lantern += [box(-6.5, 17, -6.5, 13, 2, 13, gold, {"pz": roof, "nz": roof, "px": roof, "nx": roof}),
                box(-4.5, 19, -4.5, 9, 2, 9, gold), box(-2.5, 21, -2.5, 5, 1.5, 5, gold)]
    ring = spr("""
        .ggg.
        g...g
        g...g
        .g.g.""", "g=gold1")
    lantern += prism(ring, 1, (-2.5, 22.5, -0.5), rim=-1)

    face = spr("""
        cCCCCCc
        CCCCCCC
        CkCCCkC
        CkCCCkC
        bCCCCCb
        CCkkkCC
        cCCCCCc
        .cCCCc.""", "C=cyan2 c=cyan1 k=blue0 b=pink1")
    glow = spr("""
        cCCCCCc
        CCWWCCC
        CWWCCCC
        CCCCCCC
        CCCCCCC
        CCCCCCc
        cCCCCcc
        .cCCcc.""", "C=cyan2 c=cyan1 W=white")
    top = Canvas(7, 7, "cyan2").rect(2, 2, 3, 3, "white")
    core = [box(-3.5, 8.3, -3.5, 7, 8, 7, "cyan2", {"pz": face, "nz": glow, "px": glow, "nx": glow.flip_h(), "py": top})]
    core.append(box(-1.5, 16, -1.5, 3, 2.5, 3, M("cyan2", up=1, bottom=0), rot=(0, 0, 12), origin=(0, 16, 0)))

    wisp = [box(-2.5, 2.8, -2.5, 5, 3.2, 5, M("cyan1", top=1, bottom=-1)),
            box(-1.5, 0.4, -1.8, 3, 2.6, 3, M("cyan0", top=1, bottom=0), rot=(-18, 0, 8), origin=(0, 2.8, 0)),
            box(-0.75, -1.8, -2.6, 1.5, 2, 1.5, M("cyan0", bottom=0), rot=(-30, 0, 8), origin=(0, 1, -1))]
    fin = spr("""
        ..cC.
        .cCCc
        cCWCc
        cCCc.
        .cc..
        .c...""", "c=cyan1 C=cyan2 W=white")
    wing_l = prism(fin, 1, (6, 9, -0.5), rim=-1)
    beads = []
    for i in range(6):
        a = i * math.pi / 3
        x, z = math.cos(a) * 9.5, math.sin(a) * 9.5
        col = "gold2" if i % 2 == 0 else "cyan2"
        beads.append(box(x - 0.9, 4.2 + (i % 2) * 0.8, z - 0.9, 1.8, 1.8, 1.8, M(col, bottom=-1), rot=(0, -math.degrees(a), 0),
                         origin=(x, 5, z)))

    rig = CuboidRig("Lumi (C)", "floater")
    rig.part("lantern", lantern, (0, 6, 0))
    rig.part("core", core, (0, 12, 0), "lantern")
    rig.part("ring", beads, (0, 5, 0), "lantern")
    rig.part("wing_l", wing_l, (6, 12, 0), "lantern")
    rig.part("wing_r", mirror_x(wing_l), (-6, 12, 0), "lantern")
    rig.part("tail", wisp, (0, 6, 0), "lantern")
    return rig


# ----------------------------------------------------------------------------- fungo

def fungo():
    stem_f = spr("""
        ssssssssssss
        SSSSSSSSSSSS
        SkkSSSSSSkkS
        SSkkSSSSkkSS
        SSykSSSSkySS
        SSyySSSSyySS
        SSSSSSSSSSSS
        SSSkwkkwkSSS
        sSSSkkkkSSSs
        .ssSSSSSSss.""", "S=sand2 s=sand1 k=ink y=gold1 w=white")
    stem_s = Canvas(9, 10, "sand2").hline(0, "sand1").hline(9, "sand1").vline(8, "sand1")
    back = stem_f.copy()
    for y in range(1, 9):
        for x in range(12):
            if back.p[y][x] not in (None, "sand1"):
                back.p[y][x] = "sand2"
    back.vline(3, "sand1", 2, 8).vline(8, "sand1", 3, 9)
    body = prism(stem_f, 9, (-6, 2, -4.5), back=back, faces={"px": stem_s, "nx": stem_s.flip_h()})

    def cap_side(w, h, seed, rim=False):
        c = Canvas(w, h, "purple1")
        c.hline(0, "purple2")
        for k in range(max(1, w // 7)):
            x = 2 + (hsh(k, seed) % max(1, w - 6))
            c.rect(x, max(0, h - 3), 3, 2, "cyan1").set(x, max(0, h - 3), "cyan2")
        if rim:
            c.hline(h - 1, "purple0")
        return c

    def tier(w, h, y, seed, rim=False, under=None):
        f = {"pz": cap_side(w, h, seed, rim), "nz": cap_side(w, h, seed + 1, rim), "px": cap_side(w, h, seed + 2, rim),
             "nx": cap_side(w, h, seed + 3, rim)}
        top = Canvas(w, w, "purple1")
        for k in range(w * w // 40):
            x, z = 1 + hsh(k, seed + 5) % max(1, w - 4), 1 + hsh(k, seed + 6) % max(1, w - 4)
            top.rect(x, z, 3, 3, "cyan1").set(x, z, "cyan2").set(x + 2, z + 2, "cyan0")
        top.edges(-1, -1, -1, -1)
        f["py"] = top
        if under:
            g = Canvas(w, w, "sand1")
            for x in range(0, w, 2):
                g.vline(x, "sand0")
            g.edges(-1, -1, -1, -1)
            f["ny"] = g
        return box(-w / 2, y, -w / 2, w, h, w, "purple1", f)
    cap = [tier(20, 3, 11, 1, rim=True, under=True), tier(17, 3, 14, 5), tier(12, 3, 17, 9), tier(6, 2, 20, 13)]
    thorn = M("sand2", top=1, bottom=-1, up=1)
    for i, (x, y, z, rx, rz) in enumerate(((8, 13, 0, 0, -55), (-8, 13, 2, 0, 55), (0, 13, 8, 55, 0), (1, 13, -8, -55, 0),
                                           (5, 17, 4, 30, -35), (-5, 17, -3, -30, 35), (0, 21, 0, 0, 8))):
        cap.append(box(x - 1, y, z - 1, 2, 5, 2, thorn, rot=(rx, 0, rz), origin=(x, y, z)))
        cap.append(box(x - 0.5, y + 5, z - 0.5, 1, 1.5, 1, M("sand1", bottom=0), rot=(rx, 0, rz), origin=(x, y, z)))

    vine = spr("""
        gG
        gG
        Gg
        gG
        Gg
        ss""", "g=green1 G=green2 s=sand2")
    arm = [box(6, 4, -1, 2, 6, 2, "green1", {"pz": vine, "nz": vine, "px": vine, "nx": vine}, rot=(0, 0, 22), origin=(6, 10, 0)),
           box(8.2, 4.4, -0.5, 1, 2.5, 1, M("sand2", bottom=0), rot=(0, 0, 70), origin=(8.5, 5, 0))]
    foot = box(1, 0, -2.5, 4, 2.5, 6.5, M("purple0", up=1, top=1, bottom=0))

    rig = CuboidRig("Fungo (C)", "hopper")
    rig.part("body", body, (0, 2, 0))
    rig.part("cap", cap, (0, 12, 0), "body")
    rig.part("arm_l", arm, (6, 10, 0), "body")
    rig.part("arm_r", mirror_x(arm), (-6, 10, 0), "body")
    rig.part("leg_l", [foot], (3, 2.5, 0), "body")
    rig.part("leg_r", mirror_x([foot]), (-3, 2.5, 0), "body")
    return rig


# ----------------------------------------------------------------------------- tortuga

def scales(base, seed=0):
    def mat(face, w, h):
        c = Canvas(w, h, base)
        for y in range(h):
            for x in range(w):
                row = y // 4
                xx = x + (3 if row % 2 else 0)
                if y % 4 == 3 and xx % 6 not in (0, 5):
                    c.p[y][x] = tone(base, -1)
                elif y % 4 == 0 and xx % 6 in (1, 2) and hsh(xx // 6, row, seed) % 2:
                    c.p[y][x] = tone(base, 1)
        if face == "ny":
            return fill(w, h, tone(base, -1))
        if face != "py" and h >= 6:
            c.tone_checker(0, h - 4, w, 2, -1).tone_rect(0, h - 2, w, 2, -1)
        return c
    return mat


def shell_mat(base, bw, bh, seed, moss=False):
    def mat(face, w, h):
        if face == "ny":
            return fill(w, h, "brown0")
        c = bricks(w, h, base, bw, bh, seed + ord(face[1]) + ord(face[0]), mortar=-1)
        if face == "py" and moss:
            for y in range(h):
                for x in range(w):
                    v = hsh(x // 5, y // 5, seed) % 4
                    if v == 0 or (v == 1 and (x + y) % 2 == 0):
                        c.p[y][x] = "green2" if (x * 3 + y) % 5 else "green3"
        return c
    return mat


def tortuga():
    skin = scales("green1", 1)
    body = [box(-25, 10, -32, 50, 10, 64, shell_mat("sand1", 12, 10, 2)),
            box(-30, 18, -37, 60, 6, 74, shell_mat("brown1", 10, 6, 3, moss=True)),
            box(-28, 24, -34, 56, 10, 68, shell_mat("brown2", 14, 10, 4, moss=True)),
            box(-24, 34, -29, 48, 7, 58, shell_mat("brown2", 12, 7, 5, moss=True))]
    # tampa do casco: um canteiro de grama (mesmo tile do terreno)
    gt = GRASS_A.tile(38, 46)
    gs = GRASS_SIDE.tile(38, 5)
    gs2 = GRASS_SIDE.tile(46, 5)
    body.append(box(-19, 41, -23, 38, 5, 46, "green3", {"py": gt, "pz": gs, "nz": gs, "px": gs2, "nx": gs2}, skip="ny"))
    # jardim
    from cuboidlib import moved
    tree = [trunk(0, 0, 4, 0, 13, "brown2", 3)] + leafblock(0, 10, 0, 18, 12, 18, OAK2, 21) + leafblock(1, 20, -1, 11, 7, 11, OAK2, 22, yaw=30)
    body += moved(tree, -6, 46, -8)
    body += moved(cross(TUFT, 2), 9, 46, 12) + moved(cross(TUFT, 2, a0=20), -12, 46, 14) + moved(cross(TUFT, 2, a0=70), 10, 46, -14)
    body += moved(cross(flower_sprite("red1"), 2), 6, 46, 2) + moved(cross(flower_sprite("gold1", "gold0"), 2, a0=30), -11, 46, 6)
    body += moved(cross(flower_sprite("white"), 2, a0=10), 13, 46, -4) + moved(cross(flower_sprite("pink1"), 2, a0=60), 0, 46, 17)
    body.append(box(8, 46, -16, 9, 6, 8, stone_mat(3, moss="green2"), rot=(0, 20, 0), origin=(12, 46, -12)))
    body += moved(mushroom_red(), -12, 46, -17)
    body += moved(mushroom_red(), 22, 40, 20)

    def leg(x, z):
        f = Canvas(13, 16, "green1")
        m = skin("pz", 13, 16)
        f.blit(m)
        f.rect(1, 13, 3, 3, "sand2").rect(5, 13, 3, 3, "sand2").rect(9, 13, 3, 3, "sand2")
        f.set(1, 13, "white").set(5, 13, "white").set(9, 13, "white")
        return [box(x - 6.5, 0, z - 6.5, 13, 16, 13, skin, {"pz": f})]

    head = [box(-6, 15, 34, 12, 10, 12, skin, rot=(-12, 0, 0), origin=(0, 20, 36))]
    face = spr("""
        GGGGGGGGGGGGGGGG
        GgGGGGGGGGGGGGgG
        GGGGGGGGGGGGGGGG
        GkkkGGGGGGGGkkkG
        GkWkGGGGGGGGkWkG
        GkkkGGGGGGGGkkkG
        GkkkGGGGGGGGkkkG
        bbGGGGGnGnGGGGbb
        bbGGGGGGGGGGGGbb
        SSSSSSSSSSSSSSSS
        SkSSSSSSSSSSSSkS
        SSkkkkkkkkkkkkSS
        sSSSSSSSSSSSSSSs""", "G=green2 g=green3 k=ink W=white b=blush n=green0 S=sand2 s=sand1")
    hs = Canvas(15, 13, "green2")
    hs.blit(scales("green2", 4)("px", 15, 13)).rect(0, 9, 15, 4, "sand2").hline(11, "ink", 0, 6).hline(12, "sand1")
    hs.rect(1, 3, 2, 3, "ink").set(1, 3, "white")
    head.append(box(-8, 19, 42, 16, 13, 15, scales("green2", 4), {"pz": face, "px": hs, "nx": hs.flip_h()}))
    head += moved(cross(flower_sprite("pink1").crop(0, 0, 7, 8), 2), 3, 32, 50)
    tail = [box(-3, 14, -45, 6, 5, 10, skin, rot=(20, 0, 0), origin=(0, 17, -36)),
            box(-1.5, 15, -50, 3, 3, 6, skin, rot=(20, 0, 0), origin=(0, 17, -36))]

    rig = CuboidRig("Tortuga (C)", "quadruped")
    rig.part("body", body, (0, 16, 0))
    rig.part("head", head, (0, 22, 36), "body")
    rig.part("leg_fl", leg(20, 22), (20, 16, 22))
    rig.part("leg_fr", leg(-20, 22), (-20, 16, 22))
    rig.part("leg_bl", leg(20, -22), (20, 16, -22))
    rig.part("leg_br", leg(-20, -22), (-20, 16, -22))
    rig.part("tail", tail, (0, 17, -36), "body")
    return rig


def build(out):
    for name, fn in (("pompom", pompom), ("lumi", lumi), ("fungo", fungo), ("tortuga", tortuga)):
        rig = fn()
        lo, hi = rig.body_bounds(())
        rig.meta.update({"heightTexels": round(hi[1], 1), "heightU": round(hi[1] / 32, 2)})
        rig.save(f"{out}/creatures/c/{name}", exclude=())
