"""Direção C — Pixel Voxel Adventure: personagens de cuboides com pixel art desenhada.

Densidade: 1 texel = 1/32 u. Heróis com 36–40 texels, cabeça (com cabelo/chapéu) de ~15 texels:
2,3–2,6 cabeças. Pernas visíveis <= 25% da altura. A silhueta vem da geometria (cabelo em mechas,
cachecol, ombreira, saia da túnica, botas largas); a textura só acrescenta leitura (rosto, costura).
"""
from cuboidlib import Canvas, CuboidRig, M, box, fill, mirror_x, prism, spr, tone

SKIN = "S=skin2 s=skin1 d=skin0 l=skin3 k=ink W=white b=blush m=red0"


def shade_tips(cv, n=-1):
    """Regra: pixel com vazio logo abaixo (ponta de mecha/barra) desce um tom."""
    out = cv.copy()
    for y in range(cv.h):
        for x in range(cv.w):
            if cv.p[y][x] is not None and (y == cv.h - 1 or cv.p[y + 1][x] is None):
                out.p[y][x] = tone(cv.p[y][x], n)
    return out


def face_canvas(rows, iris, extra=""):
    return spr(rows, SKIN + " i=" + iris + " " + extra)


def skull(y0, front, hair, depth=11, ear=True):
    """Crânio 13 x 11 x `depth` com queixo estreitado (prisma do sprite do rosto) e orelhas."""
    w = front.w
    side = spr("""
        SSSSSSSSSSS
        SSSSSSSSSSS
        SSSSSSSSSSS
        SSSSSSSSSSS
        SSSSSSSSSSS
        SSSSSSSSSSS
        bSSSSSSSSSS
        bSSSSSSSSSS
        SSSSSSSSSSs
        sSSSSSSSSss
        sssssssssss""", SKIN)
    side = side.crop(0, 0, depth, front.h)
    top = fill(w, depth, hair)
    bot = fill(w, depth, "skin1")
    back = Canvas(w, front.h, hair)
    for y in range(front.h):
        for x in range(w):
            if front.p[y][x] is None:
                back.p[y][x] = None
    cubs = prism(front, depth, (-w / 2, y0, -depth / 2), back=back,
                 faces={"px": side, "nx": side.flip_h(), "py": top, "ny": bot})
    if ear:
        e = spr("""
            ss
            sd
            ss""", SKIN)
        ef = fill(1, 3, "skin1")
        l = box(w / 2, y0 + 4, -1.5, 1, 3, 2, "skin1", {"px": e, "pz": ef, "nz": ef})
        cubs += [l] + mirror_x([l])
    return cubs


# ----------------------------------------------------------------------------- peças comuns

def leg(pants, boot, cuff=None, hip=9, x0=1.0, shaft_h=3):
    """Perna esquerda (x>0): calça 4x5 e bota mais larga que a perna, com bico. Devolve lista."""
    b1, b0 = tone(boot, -1), tone(boot, -2)
    cuff = cuff or tone(boot, 1)
    foot_side = spr("""
        BBBBBBcB
        BBBBBBBB
        bbbbbbbb""", {"B": boot, "b": b1, "d": b0, "c": cuff})
    toe = spr("""
        BcccB
        BBBBB
        bbbbb""", {"B": boot, "b": b1, "d": b0, "c": cuff})
    foot = box(x0 - 0.5, 0, -3, 5, 3, 8, M(boot, up=0), {
        "pz": toe, "px": foot_side, "nx": foot_side.flip_h(),
        "py": spr("""
            BBBBB
            BBBBB
            BBBBB
            BBBBB
            BBBBB
            BBBBB
            cBBBc
            ccccc""", {"B": boot, "c": cuff}).flip_v().flip_v()})
    shaft_f = Canvas(5, shaft_h, boot).hline(0, cuff).set(4, shaft_h - 1, b1)
    shaft_s = Canvas(6, shaft_h, boot).hline(0, cuff).set(0, shaft_h - 1, b1)
    shaft = box(x0 - 0.5, 3, -3, 5, shaft_h, 6, M(cuff, up=0), {"pz": shaft_f, "nz": shaft_f, "px": shaft_s, "nx": shaft_s})
    thigh = box(x0, 3 + shaft_h, -2.5, 4, hip - 2 - shaft_h, 5, M(pants, bottom=-1))
    return [thigh, shaft, foot]


def arm(sleeve, fore, glove, shoulder=20.5, x0=5.0, sleeve_h=5, trim=None, long_sleeve=False):
    """Braço esquerdo (x>0): manga curta bufante 4x5, antebraço 3x3 e luva 4x3x4 (mitene)."""
    s1 = tone(sleeve, -1)
    trim = trim or tone(sleeve, 1)
    y_s = shoulder - sleeve_h
    sl = Canvas(4, sleeve_h, sleeve)
    sl.hline(sleeve_h - 1, trim).hline(0, tone(sleeve, 1)).vline(3, s1, 1, sleeve_h - 1)
    sl_side = Canvas(5, sleeve_h, sleeve)
    sl_side.hline(sleeve_h - 1, trim).hline(0, tone(sleeve, 1))
    sl_side.set(2, 1, tone(sleeve, 1)).set(4, 2, s1).set(0, 2, s1)
    cubs = [box(x0, y_s, -2.5, 4, sleeve_h, 5, M(sleeve, up=1, down=-1),
                {"pz": sl, "nz": sl.flip_h(), "px": sl_side, "nx": sl_side.flip_h()})]
    hand_y = 8.5
    fh = y_s - (hand_y + 3)
    if fh > 0:
        f = Canvas(3, int(round(fh)), fore)
        f.hline(0, tone(fore, -1))
        cubs.append(box(x0 + 0.5, hand_y + 3, -1.5, 3, fh, 3, fore, {"pz": f, "nz": f, "px": f, "nx": f}))
    g = spr("""
        cccc
        GGGg
        gGGg""", {"G": glove, "g": tone(glove, -1), "c": tone(glove, 1)})
    cubs.append(box(x0, hand_y, -2, 4, 3, 4, M(glove, up=1, down=-1), {"pz": g, "nz": g.flip_h(), "px": g, "nx": g.flip_h()}))
    return cubs


# ----------------------------------------------------------------------------- aventureiro

def adventurer():
    HIP, NECK = 9, 21
    tunic, scarf, hair = "blue2", "red1", "brown2"
    HAIR = {"H": hair, "h": tone(hair, 1), "d": tone(hair, -1), "D": tone(hair, -2)}

    # --- tronco: peito 10, cinto 11, saia da túnica 12 (silhueta em sino)
    chest_f = spr("""
        BBBBBBBBBB
        BBBBBBBBBB
        BBBBBBBBBB
        bHBBBBBBtb
        bHBBBBBtTb
        bBBBBBtTBb
        bBBBBtTBBb
        bbbbtTbbbb""", "B=blue2 b=blue1 H=blue3 t=brown3 T=brown1")
    chest_b = spr("""
        BBBBBBBBBB
        BBBBBBBBBB
        BBBBBBBBBB
        bBtBBBBBBb
        bBTtBBBBBb
        bBBTtBBBBb
        bBBBTtBBBb
        bbbbbTtbbb""", "B=blue2 b=blue1 t=brown3 T=brown1")
    chest_s = spr("""
        BBBBBB
        BBBBBB
        BBBBBB
        BBBBBB
        bBBBBb
        bBBBBb
        bBBBBb
        bbbbbb""", "B=blue2 b=blue1")
    chest = box(-5, 13, -3, 10, 8, 6, tunic, {"pz": chest_f, "nz": chest_b, "px": chest_s, "nx": chest_s})
    belt_f = spr("""
        LLLLLLLLLLL
        lllllllllll""", "L=brown2 l=brown1")
    belt_s = belt_f.crop(0, 0, 7, 2)
    belt = box(-5.5, 11, -3.5, 11, 2, 7, M("brown2", up=-1), {"pz": belt_f, "nz": belt_f, "px": belt_s, "nx": belt_s})
    buckle = box(-1.5, 10.5, 3.5, 3, 3, 0.8, "gold1", {"pz": spr("""
        ggg
        gdG
        GGG""", "g=gold2 G=gold1 d=gold0")})
    skirt_f = spr("""
        bbbbbbbbbbbb
        BBBbBBBBbBBB
        BBBbBBBBbBBB
        hhhhhhhhhhhh""", "B=blue2 b=blue1 h=blue3")
    skirt_s = spr("""
        bbbbbbbb
        BBBBBbBB
        BBBBBbBB
        hhhhhhhh""", "B=blue2 b=blue1 h=blue3")
    skirt = box(-6, 7, -4, 12, 4, 8, M(tunic, up=-1, down=-2),
                {"pz": skirt_f, "nz": skirt_f.flip_h(), "px": skirt_s, "nx": skirt_s.flip_h()})
    pouch_b = spr("""
        llll
        LgLL
        LLLl""", "L=brown3 l=brown2 g=gold1")
    pouch = box(-5, 9, -5.4, 4, 3, 1.4, M("brown3", up=0), {"nz": pouch_b})

    # cachecol: anel mais largo que o peito, o queixo afunda nele
    sc_f = spr("""
        RRrRRRRRRrRRR
        rrRrrrrrrRrrr
        ddddddddddddd""", "R=red2 r=red1 d=red0")
    sc_s = sc_f.crop(2, 0, 9, 3)
    sc = box(-6.5, 18.5, -4.5, 13, 3, 9, M(scarf, up=0, down=-1), {"pz": sc_f, "nz": sc_f.flip_h(), "px": sc_s, "nx": sc_s})
    knot = box(2.5, 18, -6, 3.5, 3.5, 2, M("red1", up=1), {"nz": spr("""
        RRRr
        RrRr
        rrrd
        rddd""", "R=red2 r=red1 d=red0")}, rot=(0, 0, 12), origin=(4, 19.5, -5))
    torso = [chest, belt, buckle, skirt, pouch, sc, knot]

    # ponta do cachecol (parte "cape": balança)
    tail_s = spr("""
        RRRR
        rRRr
        rRRr
        rRRr
        rrrr
        gggg
        rrrr
        rr.r
        r..r""", "R=red2 r=red1 d=red0 g=gold1")
    cape = prism(tail_s, 1.2, (2.5, 9.5, -6.6), back=tail_s.flip_h(), rim=-1)

    # --- cabeça
    face = face_canvas("""
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSWkSSSSSWkSS
        SSkkSSSSSkkSS
        SSkiSSSSSkiSS
        SbbSSSSSSSbbS
        SSSSSdmdSSSSS
        .sSSSSSSSSSs.""", "blue2")
    head = skull(NECK, face, hair)
    cap_f = spr("""
        dHHHHHHHHHHHHd
        HHhhhhHHHhhHHH
        HhHHHHHHHHHhHH
        HHHHHHHHHHHHHH""", HAIR)
    cap_s = spr("""
        dHHHHHHHHHHHd
        HhhhHHhhhhHHH
        hHHdHHHHdHHhH
        HHHdHHHHHdHHd""", HAIR)
    cap_top = Canvas(14, 13, hair)
    for (x, y) in ((3, 9), (4, 8), (5, 8), (6, 9), (9, 8), (10, 9), (4, 4), (5, 3), (9, 3), (10, 4), (7, 6)):
        cap_top.set(x, y, tone(hair, 1))
    cap_top.edges(top=-1, left=-1, right=-1)
    head.append(box(-7, NECK + 9, -6.5, 14, 4, 13, hair,
                    {"pz": cap_f, "nz": cap_s.crop(0, 0, 13, 4).tile(14, 4), "px": cap_s, "nx": cap_s.flip_h(), "py": cap_top},
                    skip="ny"))
    bangs = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHHhHHHHHHhHHH
        HHH.HHHHHH..HH
        HH...HHH....HH
        H.....H......H""", HAIR))
    head += prism(bangs, 1, (-7, NECK + 4, 5.5), rim=0)
    back_hair = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHhhhHHhhhHhhH
        HhhHHdhhHHdHhH
        HHHHHdHHHHdHHH
        HHHHdHHHHHHdHH
        HdHHdHHHHHHdHH
        HdHHdHHdHHHdHH
        HH.HHH.HHH.HHH
        H...H...HH..H.""", HAIR))
    head += prism(back_hair, 5, (-7, NECK, -6.5), rim=0)
    lock = shade_tips(spr("""
        HHH
        HHH
        HH.
        H..""", HAIR))
    lk = prism(lock, 0.8, (6.4, NECK + 5, 2.5), axis="x", rim=0)
    head += lk + mirror_x(lk)
    # coroa de mechas: silhueta espetada de frente e de lado
    crown = spr("""
        .......h..H.....
        ..H...hHH.HH....
        ..Hh.hHHHhHHH.H.
        hHHHhHHHHHHHHhHH
        HHHHHHHHHHHHHHH.""", HAIR)
    head += prism(crown, 6, (-8, NECK + 11, -4), rim=0)
    crown2 = spr("""
        ..H...hH......
        .HHh.hHHH.Hh..
        HHHHHHHHHHHHHh
        ..........HHHH""", HAIR)
    head += prism(crown2, 7, (-3.5, NECK + 11.25, -8), axis="x", rim=0)

    # --- membros
    leg_l = leg("blue0", "brown2", hip=HIP)
    arm_l = arm(tunic, "sand2", "brown3", shoulder=NECK - 0.5)
    arm_r = mirror_x(arm_l)
    # ombreira no braço da espada (assimetria)
    pd_top = spr("""
        GGGGGG
        GwwwGg
        GwGGGg
        GGGGGg
        Gggggg""", "G=grey3 g=grey2 w=grey4").rot90()
    arm_r.append(box(-10, NECK - 3.5, -3, 5, 3, 6, M("grey3", bottom=-2, up=0), {
        "py": spr("""
            gGGGG
            GwwGG
            GwGGG
            GGGGG
            GGGGG
            gGGGg""", "G=grey3 g=grey2 w=grey4"),
        "nx": spr("""
            GGGGGG
            GGyGGG
            dddddd""", "G=grey3 d=grey1 y=gold1")}))
    arm_r.append(box(-10.5, NECK - 1.5, -2, 1.5, 2, 4, M("grey2", bottom=0, up=1)))

    # --- espada: lâmina fina no plano ZY (o animador inclina para a frente)
    blade = spr("""
        .w.
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs
        wWs""", "w=grey4 W=grey3 s=grey2")
    hx, hy = -7.0, 10.0
    sword = prism(blade, 1, (hx - 0.5, hy + 3.5, -1.5), axis="x", rim=0)
    sword.append(box(hx - 1, hy + 2, -3.5, 2, 1.6, 7, M("gold1", bottom=-1, up=1)))
    sword.append(box(hx - 0.7, hy - 2.5, -0.7, 1.4, 4.5, 1.4, M("brown1", bottom=0)))
    sword.append(box(hx - 1, hy - 4, -1, 2, 1.6, 2, M("gold1", bottom=-1, up=1)))

    # --- broquel no antebraço esquerdo
    sh = spr("""
        ..ggggg..
        .gBBBBBg.
        gBBBwBBBg
        gBBwwwBBg
        gBwwywwBg
        gBBwwwBBg
        gBBBwBBBd
        .gBBBBBd.
        ..ggddd..""", "g=grey3 d=grey2 B=blue2 w=sand2 y=gold1")
    sh_back = spr("""
        ..ggggg..
        .gLLLLLg.
        gLLlLLLLg
        gLLlLLLLg
        gLLlLLLLg
        gLLlLLLLg
        gLLlLLLLg
        .gLLLLLg.
        ..ggggg..""", "g=grey2 L=brown2 l=brown1")
    shield = prism(sh, 1.4, (9, 8, -4.5), axis="x", back=sh_back, rim_color="grey2")
    shield.append(box(10.4, 11.5, -1, 0.8, 2, 2, M("gold1", up=1, bottom=-1)))

    rig = CuboidRig("Aventureiro (C)", "biped")
    rig.part("torso", torso, (0, HIP, 0))
    rig.part("head", head, (0, NECK, 0), "torso")
    rig.part("arm_l", arm_l, (7, NECK - 1.5, 0), "torso")
    rig.part("arm_r", arm_r, (-7, NECK - 1.5, 0), "torso")
    rig.part("leg_l", leg_l, (3, HIP, 0))
    rig.part("leg_r", mirror_x(leg_l), (-3, HIP, 0))
    rig.part("weapon", sword, (hx, hy, 0), "arm_r")
    rig.part("shield", shield, (9, 12.5, 0), "arm_l")
    rig.part("cape", cape, (4.5, 18.5, -6), "torso")
    return finish(rig, head_base=NECK)


def finish(rig, head_base, skip=("weapon", "shield"), head_top=None):
    """Preenche meta: altura total (sem arma) e cabeças de altura. Para quem usa chapéu, a conta é
    feita SEM o chapéu (proporção do corpo) e o chapéu entra em meta.hatH."""
    lo, hi = rig.body_bounds(skip)
    total = head_top or hi[1]
    head_lo, head_hi = head_base, total
    full = rig.body_bounds(("weapon",))[1][1]
    if full > total + 0.01:
        rig.meta["hatH"] = round(full - total, 1)
    rig.meta.update({"headsTall": round(total / (head_hi - head_lo), 2), "heightTexels": round(total, 1),
                     "headH": round(head_hi - head_lo, 1), "legFraction": round(7 / total, 3)})
    return rig




def hair_cap(y, hair, h=4):
    """Calota de cabelo 14 x h x 13 com faixa de brilho e mechas marcadas."""
    HAIR = {"H": hair, "h": tone(hair, 1), "d": tone(hair, -1)}
    f = spr("""
        dHHHHHHHHHHHHd
        HHhhhhHHHhhHHH
        HhHHHHHHHHHhHH
        HHHHHHHHHHHHHH""", HAIR).crop(0, 0, 14, h)
    sd = spr("""
        dHHHHHHHHHHHd
        HhhhHHhhhhHHH
        hHHdHHHHdHHhH
        HHHdHHHHHdHHd""", HAIR).crop(0, 0, 13, h)
    top = Canvas(14, 13, hair)
    for (x, yy) in ((3, 9), (4, 8), (5, 8), (6, 9), (9, 8), (10, 9), (4, 4), (5, 3), (9, 3), (10, 4), (7, 6)):
        top.set(x, yy, tone(hair, 1))
    top.edges(top=-1, left=-1, right=-1)
    return box(-7, y, -6.5, 14, h, 13, hair,
               {"pz": f, "nz": sd.tile(14, h), "px": sd, "nx": sd.flip_h(), "py": top}, skip="ny")


# ----------------------------------------------------------------------------- patrulheira

def ranger():
    HIP, NECK = 10, 21
    hair = "brown3"
    HAIR = {"H": hair, "h": tone(hair, 1), "d": tone(hair, -1), "D": tone(hair, -2)}

    chest_f = spr("""
        GGGGGGGGG
        GGGGGGGGG
        GGGGGGGGG
        gGGwGwGGg
        gGGGwGGGg
        gGGwGwGGg
        ggggggggg""", "G=green2 g=green1 w=sand2")
    chest_b = spr("""
        GGGGGGGGG
        GGGGGGGGG
        GGGGGGGGG
        gGGGGGtGg
        gGGGGtTGg
        gGGGtTGGg
        gggtTgggg""", "G=green2 g=green1 t=brown3 T=brown1")
    chest_s = spr("""
        GGGGGG
        GGGGGG
        GGGGGG
        gGGGGg
        gGGGGg
        gGGGGg
        gggggg""", "G=green2 g=green1")
    chest = box(-4.5, 14, -3, 9, 7, 6, "green2", {"pz": chest_f, "nz": chest_b, "px": chest_s, "nx": chest_s})
    belt_f = spr("""
        LLLLyyLLLL
        lllllyllll""", "L=brown2 l=brown1 y=gold1")
    belt_b = spr("""
        LLLLLLLLLL
        llllllllll""", "L=brown2 l=brown1")
    belt = box(-5, 12, -3.5, 10, 2, 7, M("brown2", up=-1),
               {"pz": belt_f, "nz": belt_b, "px": belt_b.crop(0, 0, 7, 2), "nx": belt_b.crop(0, 0, 7, 2)})
    pouch = box(2.5, 9.5, 3.5, 3, 3, 1.5, M("brown3", up=1), {"pz": spr("""
        LLL
        lyl
        lll""", "L=brown4 l=brown3 y=gold1")})
    cape_f = spr("""
        GGGGGGyGGGGGG
        gggggyYyggggg
        dgdgdgygdgdgd""", "G=green2 g=green1 d=green0 y=gold1 Y=gold2")
    cape_b = spr("""
        GGGGGGGGGGGGG
        ggggggggggggg
        dgdgdgdgdgdgd""", "G=green2 g=green1 d=green0")
    capelet = box(-6.5, 18.5, -4.5, 13, 3, 9, M("green1", up=1, down=-1),
                  {"pz": cape_f, "nz": cape_b, "px": cape_b.crop(0, 0, 9, 3), "nx": cape_b.crop(0, 0, 9, 3)})
    # aljava nas costas, flechas acima do ombro direito
    qo, qr = (-3, 15, -7.5), (0, 0, 24)
    qv = spr("""
        lll
        LLL
        LLL
        LyL
        LLL
        LLL
        LLL
        LLL
        lll
        ddd""", "L=brown2 l=brown3 d=brown1 y=gold1")
    quiver = [box(-4.5, 10, -9, 3, 10, 3, "brown2", {"nz": qv, "px": qv, "nx": qv, "pz": qv}, rot=qr, origin=qo)]
    for i, (ax, col) in enumerate(((-4.2, "red1"), (-3.3, "grey4"), (-2.6, "red1"))):
        az = -8.6 + i * 0.8
        quiver.append(box(ax, 20, az, 0.8, 2, 0.8, M("sand1", bottom=0), rot=qr, origin=qo))
        quiver.append(box(ax - 0.4, 22, az - 0.4, 1.6, 2.5, 1.6, M(col, bottom=-1, up=1), rot=qr, origin=qo))
    torso = [chest, belt, pouch, capelet] + quiver

    skirt_f = spr("""
        gggggggggggg
        GGGGgGGgGGGG
        GGGgGGGGgGGG
        hGhGhGhGhGhG""", "G=green2 g=green1 h=green3")
    skirt = [box(-6, 8.5, -4, 12, 4, 8, M("green2", up=-1, down=-2),
                 {"pz": skirt_f, "nz": skirt_f.flip_h(), "px": skirt_f.crop(2, 0, 8, 4), "nx": skirt_f.crop(2, 0, 8, 4)})]

    face = face_canvas("""
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SkWkSSSSSWkkS
        SSkkSSSSSkkSS
        SSkiSSSSSkiSS
        SbbSSSSSSSbbS
        SSSSSSmSSSSSS
        .sSSSSSSSSSs.""", "green2")
    head = skull(NECK, face, hair)
    head.append(hair_cap(NECK + 9, hair))
    bangs = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHhhHHHHHHHhHH
        HHHHHHH.HHHHHH
        HHH.....HH..HH
        HH...........H
        H............H""", HAIR))
    head += prism(bangs, 1, (-7, NECK + 3, 5.5), rim=0)
    back_hair = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHhhhHHhhhHhhH
        HhhHHdhhHHdHhH
        HHHHHdHHHHdHHH
        HHHHdHHHHHHdHH
        HHdHHHHdHHHdHH
        .HH.HHHHHH.HH.""", HAIR))
    head += prism(back_hair, 5, (-7, NECK + 2, -6.5), rim=0)
    lock = shade_tips(spr("""
        HHH
        HHH
        HHH
        hH.
        HH.
        HH.
        H..
        H..""", HAIR))
    lk = prism(lock, 0.8, (6.4, NECK + 1, 2.5), axis="x", rim=0)
    head += lk + mirror_x(lk)
    head.append(box(-6, NECK + 13, -5.5, 12, 1, 11, M(hair, bottom=0, up=0)))
    head.append(box(-4.5, NECK + 14, -4.5, 8, 0.8, 8, M(hair, bottom=0, up=1)))
    band = Canvas(15, 2, "green2").hline(1, "green1")
    head.append(box(-7.4, NECK + 9.5, -6.9, 14.8, 2, 13.8, M("green2", up=0),
                    {"pz": band, "nz": band, "px": band.crop(0, 0, 14, 2), "nx": band.crop(0, 0, 14, 2)}, skip="py ny"))
    feather = spr("""
        ...w
        ..wW
        .wWr
        wWWr
        wWr.
        wWr.
        Wr..
        Wr..
        g...""", "w=grey4 W=grey3 r=red1 g=sand0")
    head += prism(feather, 1, (6.9, NECK + 10.5, -4), axis="x", rim=-1, rot=(-22, 0, -16), origin=(7.4, NECK + 10.5, -2))
    head.append(box(-2, NECK + 8, -8, 4, 3.4, 2, M("red1", up=1), {"nz": spr("""
        RrrR
        RRRR
        rRRr""", "R=red2 r=red1")}))
    # rabo de cavalo: curva em S no plano ZY (esquerda da imagem = frente)
    pony = spr("""
        .HHHHH..
        HHhhHHH.
        HHHHhHHH
        ..HHHhHH
        ...HHHHH
        ....HhHH
        ....HHHd
        ....HhHd
        ...HHHHd
        ...HhHd.
        ..HHHHd.
        ..HHHd..
        .HHHd...
        .Hdd....""", HAIR)
    tail = prism(pony, 3, (-1.5, NECK - 2.5, -15.5), axis="x", rim=0)

    leg_l = leg("green0", "brown2", hip=HIP, shaft_h=4)
    arm_l = arm("sand2", "brown2", "skin2", shoulder=NECK - 0.5, x0=4.5, trim="sand1")
    arm_r = mirror_x(arm_l)

    bow = spr("""
        ....W.
        ...WWs
        ...W.s
        ..WW.s
        ..W..s
        .WW..s
        .W...s
        WW...s
        W....s
        G....s
        G....s
        G....s
        W....s
        WW...s
        .W...s
        .WW..s
        ..W..s
        ..WW.s
        ...W.s
        ...WWs
        ....W.""", "W=brown3 G=red1 s=sand2")
    hx, hy = -6.5, 10.0
    weapon = prism(bow, 1, (hx - 0.5, hy - 10.5, -5.5), axis="x", rim=-1)

    rig = CuboidRig("Patrulheira (C)", "biped")
    rig.part("torso", torso, (0, HIP, 0))
    rig.part("head", head, (0, NECK, 0), "torso")
    rig.part("arm_l", arm_l, (6.5, NECK - 1.5, 0), "torso")
    rig.part("arm_r", arm_r, (-6.5, NECK - 1.5, 0), "torso")
    rig.part("leg_l", leg_l, (3, HIP, 0))
    rig.part("leg_r", mirror_x(leg_l), (-3, HIP, 0))
    rig.part("weapon", weapon, (hx, hy, 0), "arm_r")
    rig.part("skirt", skirt, (0, 12.5, 0), "torso")
    rig.part("tail", tail, (0, NECK + 9.5, -7), "head")
    return finish(rig, head_base=NECK, skip=("weapon", "tail"), head_top=NECK + 14.8)


# ----------------------------------------------------------------------------- maga

def mage():
    HIP, NECK = 8, 20
    hair = "grey4"
    HAIR = {"H": hair, "h": "white", "d": tone(hair, -1), "D": tone(hair, -2)}
    ROBE = "P=purple1 p=purple0 L=purple2 y=gold1 Y=gold2 o=gold0 c=cyan1 C=cyan2"

    chest_f = spr("""
        PPPPPPPPPP
        PPPPPPPPPP
        PPPPPPPPPP
        pPPPyyPPPp
        pPPPyYPPPp
        pPPPyyPPPp
        oyyyyyyyyo
        pooooooooo""", ROBE)
    chest_b = spr("""
        PPPPPPPPPP
        PPPPPPPPPP
        PPPPPPPPPP
        pPPPPPPPPp
        pPPPLPPPPp
        pPPPPPPPPp
        oyyyyyyyyo
        pooooooooo""", ROBE)
    chest_s = chest_b.crop(0, 0, 6, 8)
    chest = box(-5, 12, -3, 10, 8, 6, "purple1", {"pz": chest_f, "nz": chest_b, "px": chest_s, "nx": chest_s})
    col_f = spr("""
        LLLLLLLLLLLLL
        LLLLLLLLLLLLL
        yoyoyoyoyoyoy""", ROBE)
    collar = box(-6.5, 17.5, -4.5, 13, 3, 9, M("purple2", up=0, down=-2),
                 {"pz": col_f, "nz": col_f, "px": col_f.crop(0, 0, 9, 3), "nx": col_f.crop(0, 0, 9, 3)})
    gem = box(-1.5, 17.5, 4.5, 3, 3, 0.8, "cyan1", {"pz": spr("""
        yCy
        cCc
        ycy""", ROBE)})
    torso = [chest, collar, gem]

    # saia do manto em três degraus (sino); a barra tem estrelas douradas
    def tier(w, h, d, y, hem=False):
        f = Canvas(w, h, "purple1")
        for x in range(w):
            if x % 4 == 1:
                f.vline(x, "purple0")
        f.vline(0, "purple0").vline(w - 1, "purple0")
        if hem:
            f.hline(h - 1, "gold0").hline(h - 2, "gold1")
            for x in range(2, w - 1, 4):
                f.set(x, h - 4, "gold2")
        s = f.crop(0, 0, d, h) if d <= w else f.tile(d, h)
        s.vline(d - 1, "purple0")
        return box(-w / 2, y, -d / 2, w, h, d, M("purple1", up=1, down=-2), {"pz": f, "nz": f, "px": s, "nx": s})
    skirt = [tier(12, 3, 9, 9), tier(13, 4, 11, 5), tier(14, 4, 13, 1, hem=True)]

    face = face_canvas("""
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SSSSSSSSSSSSS
        SkWkSSSSSWkkS
        SSkiSSSSSkiSS
        SSiiSSSSSiiSS
        SbbSSSSSSSbbS
        SSSSSSmSSSSSS
        .sSSSSSSSSSs.""", "purple2")
    face.rect(0, 0, 13, 2, hair)
    head = skull(NECK, face, hair, ear=False)
    bangs = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHhhhHHHHhhhHH
        HHHHHHHHHHHHHH
        HHHdHHdHHdHHHH
        HHH........HHH
        HH..........HH
        HH..........HH""", HAIR))
    head += prism(bangs, 1, (-7, NECK + 3, 5.5), rim=0)
    back_hair = shade_tips(spr("""
        HHHHHHHHHHHHHH
        HHhhhHHhhhHhhH
        HhhHHdhhHHdHhH
        HHHHHdHHHHdHHH
        HHHHdHHHHHHdHH
        HHdHdHHHHHHdHH
        HHdHdHHdHHHdHH
        HHdHHHHdHHHdHH
        HHdHHHHdHHdHHH
        HHHHdHHHHHdHHH
        .HHHdHHHHHHHH.
        ..HHHHHHHHHH..
        ...HHH.HHHH...""", HAIR))
    head += prism(back_hair, 3.5, (-7, NECK - 4, -6.5), rim=-1)
    lock = shade_tips(spr("""
        HHH
        HHH
        HhH
        HhH
        HHH
        HHd
        HHd
        yyy
        HHH
        HH.
        .H.""", dict(HAIR, y="purple2")))
    lk = prism(lock, 1, (6.5, NECK - 2, 2.5), axis="x", rim=-1)
    head += lk + mirror_x(lk)

    # chapéu pontudo: aba larga (octógono), copa em degraus, ponta dobrada para trás
    Y = NECK + 10
    ho, tilt = (0, Y, 0), -9
    brim = Canvas(19, 19)
    for z in range(19):
        for x in range(19):
            r2 = (x - 9) ** 2 + (z - 9) ** 2
            if r2 <= 92:
                brim.p[z][x] = "purple2" if 50 <= r2 <= 68 and (x + z) % 2 == 0 else "purple1"
    under = brim.copy().remap({"purple1": "purple0", "purple2": "purple0"})
    hat = prism(brim, 1.5, (-9.5, Y, -9.5), axis="y", back=under, rim_color="purple0", rot=(tilt, 0, 0), origin=ho)
    band = Canvas(11, 3, "purple1").hline(1, "gold1").hline(2, "gold0").hline(0, "purple2")
    band_f = band.copy().rect(4, 1, 3, 2, "gold2").set(5, 1, "gold0")
    hat.append(box(-5.5, Y + 1.5, -5.5, 11, 3, 11, M("purple1", up=0), {"pz": band_f, "nz": band, "px": band, "nx": band},
                   rot=(tilt, 0, 0), origin=ho))
    cone = M("purple1", top=1, bottom=-1, up=0)
    hat.append(box(-4, Y + 4.3, -4.8, 8, 2.7, 8, cone, rot=(tilt - 6, 0, 0), origin=ho))
    hat.append(box(-2.5, Y + 6.6, -4.4, 5, 2.6, 5, cone, rot=(-28, 0, 0), origin=(0, Y + 6.6, -2)))
    hat.append(box(-1.5, Y + 7.3, -5.4, 3, 2.6, 3, cone, rot=(-62, 0, 0), origin=(0, Y + 7.8, -3.4)))
    hat.append(box(-1, Y + 5.6, -8.4, 2, 2, 2, M("gold1", up=1, bottom=-1), rot=(-45, 0, 0), origin=(0, Y + 6.6, -7.4)))

    leg_l = leg("purple0", "brown1", hip=HIP, cuff="brown2")
    # mangas em sino: mais largas no punho
    up = Canvas(4, 4, "purple1").hline(0, "purple2").vline(3, "purple0", 1)
    ups = Canvas(5, 4, "purple1").hline(0, "purple2")
    cf = spr("""
        LLLLL
        PPPPp
        PPPPp
        yyyyy
        ooooo""", ROBE)
    cs = spr("""
        LLLLLL
        PPPPPP
        pPPPPp
        yyyyyy
        oooooo""", ROBE)
    hand = Canvas(3, 2, "skin2").hline(1, "skin1")
    arm_l = [box(5, 15.5, -2.5, 4, 4, 5, M("purple1", up=1), {"pz": up, "nz": up.flip_h(), "px": ups, "nx": ups}),
             box(4.5, 10.5, -3, 5, 5, 6, M("purple1", up=1, down=-2), {"pz": cf, "nz": cf.flip_h(), "px": cs, "nx": cs}),
             box(5.5, 8.5, -1.5, 3, 2, 3, "skin2", {"pz": hand, "nz": hand, "px": hand, "nx": hand})]
    arm_r = mirror_x(arm_l)

    # cajado: haste, crescente dourado e orbe
    hx, hy = -7.0, 9.5
    staff = [box(hx - 0.75, hy - 9, -0.75, 1.5, 26, 1.5, M("brown2", bottom=-1, left=0, right=-1))]
    cres = spr("""
        .y...y.
        yY...yy
        yy...yo
        yY...yo
        .yy.yo.
        ..yyo..
        ..yoo..""", ROBE)
    staff += prism(cres, 1.5, (hx - 0.75, hy + 16.5, -3.5), axis="x", rim=-1)
    orb = spr("""
        cCc
        cCc
        ccc""", "c=cyan1 C=cyan2")
    staff.append(box(hx - 1.5, hy + 19, -1.5, 3, 3, 3, M("cyan1", up=1, down=-1), {"pz": orb, "px": orb, "nx": orb.flip_h(), "nz": orb}))

    rig = CuboidRig("Maga (C)", "biped")
    rig.part("torso", torso, (0, HIP, 0))
    rig.part("head", head, (0, NECK, 0), "torso")
    rig.part("arm_l", arm_l, (7, 18.5, 0), "torso")
    rig.part("arm_r", arm_r, (-7, 18.5, 0), "torso")
    rig.part("leg_l", leg_l, (3, HIP, 0))
    rig.part("leg_r", mirror_x(leg_l), (-3, HIP, 0))
    rig.part("weapon", staff, (hx, hy, 0), "arm_r")
    rig.part("skirt", skirt, (0, 12, 0), "torso")
    rig.part("hat", hat, (0, Y, 0), "head")
    return finish(rig, head_base=NECK, skip=("weapon", "hat"))


def build(out):
    adventurer().save(f"{out}/characters/c/adventurer")
    ranger().save(f"{out}/characters/c/ranger")
    mage().save(f"{out}/characters/c/mage")
