"""Direção A — Miniature Fantasy: personagens voxel clássicos, compactos, cores vivas.

Resolução: cabeça 10x9x9 voxels (12 de largura com cabelo), voxel de 1/24 u.
Proporção escolhida no estudo: ~2,5 cabeças (pernas 5, tronco 9), pernas ~20% da altura.
O construtor aceita alturas de perna/tronco, o que permite gerar o estudo de proporções
(2,2 a 4 cabeças) com exatamente o mesmo personagem e equipamento.
"""
from voxlib import C, Rig, Vox, mix, tone

VS = 1 / 24

SKIN = C("#f6caa0")
SKIN_D = C("#e2a77c")
BLUSH = C("#f29c8c")
EYE = C("#2b2238")
EYE_HI = C("#ffffff")
MOUTH = C("#b9614d")
GOLD = C("#f8c93a", "metal")
GOLD_D = C("#cc9320", "metal")
STEEL = C("#eef3fa", "metal")
STEEL_D = C("#b3c1d6", "metal")
LEATHER = C("#8a5530")
LEATHER_D = C("#5f381d")
WOOD = C("#a9703a")
WOOD_D = C("#7d4f26")


class Body:
    """Medidas e marcos de um bípede da direção A (em voxels)."""

    def __init__(self, leg_h=5, torso_h=9, arm_h=None, arm_w=3, torso_w=8):
        self.lh, self.th, self.aw, self.tw = leg_h, torso_h, arm_w, torso_w
        self.ah = arm_h if arm_h is not None else max(5, torso_h - 2)
        self.hip = leg_h                      # y do topo das pernas
        self.neck = leg_h + torso_h           # y da base da cabeça
        self.shoulder = self.neck - 2         # linha de voxels do ombro (o cachecol/gola cobre a de cima)
        self.hand = self.shoulder - self.ah + 1   # linha inferior da mão
        self.top = self.neck + 9              # topo do crânio (exclusivo)
        self.ax0 = torso_w // 2               # x interno do braço esquerdo
        self.ax1 = self.ax0 + arm_w - 1       # x externo do braço esquerdo
        self.acx = (self.ax0 + self.ax1 + 1) / 2.0


def skull(y0):
    """Crânio 10x9x9 com arestas verticais e queixo chanfrados (tira a leitura de 'caixa')."""
    v = Vox()
    v.box(-5, y0, -4, 4, y0 + 8, 4, SKIN)
    for y in range(y0, y0 + 9):
        for (x, z) in ((-5, -4), (4, -4), (-5, 4), (4, 4)):
            v.set(x, y, z, None)
    for x in range(-5, 5):
        v.set(x, y0, -4, None)
        v.set(x, y0, 4, None)
    for z in range(-4, 5):
        v.set(-5, y0, z, None)
        v.set(4, y0, z, None)
    v.paint(lambda x, y, z, c: SKIN_D if y == y0 else None)
    # orelhas pequenas que aparecem sob o cabelo
    for x in (-6, 5):
        v.box(x, y0 + 2, 0, x, y0 + 3, 1, SKIN_D)
    return v


def face(v, y0, iris=None, lash=False, smile=True):
    """Rosto no plano z=4: olhos 2x3 com brilho, bochechas, boca pequena.
    'iris' é a cor (escura) do olho inteiro: nesta resolução uma íris separada vira ruído."""
    for ex in (-3, 1):
        v.box(ex, y0 + 2, 4, ex + 1, y0 + 4, 4, iris or EYE)
        v.set(ex if ex < 0 else ex + 1, y0 + 4, 4, EYE_HI)
        if lash:
            v.set(ex - 1 if ex < 0 else ex + 2, y0 + 4, 4, EYE)
    v.set(-4, y0 + 1, 4, BLUSH)
    v.set(3, y0 + 1, 4, BLUSH)
    if smile:
        v.set(-1, y0 + 1, 4, MOUTH)
        v.set(0, y0 + 1, 4, MOUTH)
    else:
        v.set(-1, y0 + 1, 4, MOUTH)


def hair_cap(y0, hair, drops, back_len=6, side_len=4):
    """Base de cabelo: calota, laterais, nuca com barra irregular e franja com 'drops' por coluna."""
    h = Vox()
    hd = mix(hair, C("#3a2430"), 0.35)
    h.box(-5, y0 + 9, -4, 4, y0 + 9, 4, hair)
    for (x, z) in ((-5, -4), (4, -4), (-5, 4), (4, 4)):
        h.set(x, y0 + 9, z, None)
    # laterais: descem mais atrás do que na frente
    for z in range(-4, 3):
        low = y0 + 9 - side_len - (1 if z < -1 else 0)
        h.box(-6, low, z, -6, y0 + 8, z, hair)
        h.box(5, low, z, 5, y0 + 8, z, hair)
    # têmporas: fecham o canto chanfrado entre a franja e a lateral (sem isso a lateral lê como
    # uma segunda massa atrás do rosto em vista de 3/4)
    for x in (-5, 4):
        h.box(x, y0 + 5, 4, x, y0 + 8, 4, hair)
    for x in (-6, 5):
        h.box(x, y0 + 6, 3, x, y0 + 8, 3, hair)
    # nuca com barra em zigue-zague e um degrau de volume no alto
    for x in range(-5, 5):
        low = y0 + 9 - back_len - (1 if x % 2 == 0 else 0)
        h.box(x, low, -5, x, y0 + 8, -5, hair)
        h.set(x, low, -5, hd)
    h.box(-4, y0 + 5, -6, 3, y0 + 8, -6, hair)
    h.box(-4, y0 + 9, -5, 3, y0 + 9, -5, hair)
    # franja: z=4 rente à testa + uma camada saliente em z=5
    for x in range(-4, 4):
        h.set(x, y0 + 8, 4, hair)
        h.set(x, y0 + 7, 4, hair)
    for x, drop in zip(range(-4, 4), drops):
        for d in range(drop + 1):
            h.set(x, y0 + 7 - d, 5 if d == 0 else 4, hair)
    h.box(-4, y0 + 8, 5, 3, y0 + 8, 5, hair)
    return h


def head_short(b, hair, hair_hi):
    y0 = b.neck
    v = skull(y0)
    face(v, y0)
    h = hair_cap(y0, hair, (2, 1, 1, 2, 1, 0, 1, 2))
    # tufos no topo: quebram a silhueta
    h.box(-2, y0 + 10, -3, 1, y0 + 10, 1, hair)
    h.box(-1, y0 + 11, -2, 0, y0 + 11, -1, hair)
    h.set(2, y0 + 10, 1, hair)
    h.set(1, y0 + 11, 0, hair_hi)
    h.set(-3, y0 + 10, -1, hair)
    h.set(3, y0 + 10, -2, hair)
    h.paint(lambda x, y, z, c: hair_hi if c == hair and y >= y0 + 9 and (x + z) % 3 == 0 else None)
    v.merge(h)
    return v


def head_ponytail(b, hair, hair_hi, ribbon):
    y0 = b.neck
    v = skull(y0)
    face(v, y0, iris=C("#1f4a3a"), lash=True)
    h = hair_cap(y0, hair, (3, 1, 0, 0, 1, 1, 2, 3), back_len=3, side_len=4)
    h.box(-6, y0 + 1, 1, -6, y0 + 3, 2, hair)       # mechas longas emoldurando o rosto
    h.box(5, y0 + 1, 1, 5, y0 + 3, 2, hair)
    h.box(-3, y0 + 10, -3, 2, y0 + 10, 0, hair)
    h.box(-1, y0 + 7, -7, 0, y0 + 8, -7, ribbon)     # laço
    h.set(-2, y0 + 8, -7, ribbon)
    h.set(1, y0 + 8, -7, ribbon)
    h.paint(lambda x, y, z, c: hair_hi if c == hair and y >= y0 + 9 and (x + z) % 3 == 0 else None)
    v.merge(h)
    hd = mix(hair, C("#3a2430"), 0.35)
    tail = Vox()
    tail.box(-1, y0 + 6, -9, 0, y0 + 8, -8, hair)
    tail.box(-2, y0 + 3, -10, 1, y0 + 6, -9, hair)
    tail.box(-1, y0 + 0, -10, 0, y0 + 2, -9, hair)
    tail.box(-1, y0 - 2, -10, 0, y0 - 1, -10, hd)
    tail.set(-2, y0 + 3, -10, hd)
    tail.set(1, y0 + 3, -10, hd)
    return v, tail, (0, y0 + 7.5, -7.0)


def head_mage(b, hair, hair_hi):
    y0 = b.neck
    v = skull(y0)
    face(v, y0, iris=C("#3b2a78"), smile=False)
    h = hair_cap(y0, hair, (3, 2, 1, 0, 0, 1, 2, 3), back_len=8, side_len=7)
    h.box(-6, y0 + 0, -3, -6, y0 + 2, 0, hair)
    h.box(5, y0 + 0, -3, 5, y0 + 2, 0, hair)
    h.paint(lambda x, y, z, c: hair_hi if c == hair and (x * 3 + y + z) % 5 == 0 else None)
    v.merge(h)
    return v


def wizard_hat(b, col, band):
    """Chapéu de aba larga com a ponta caída para trás: a silhueta do conjurador."""
    y0 = b.neck + 8
    hat = Vox()
    cd, cl = tone(col, -1.2), tone(col, 1.0)
    for x in range(-9, 9):
        for z in range(-8, 9):
            d = ((x + 0.5) / 9.0) ** 2 + ((z - 0.0) / 8.5) ** 2
            if d <= 1.0:
                hat.set(x, y0, z, cd if d > 0.78 else col)
    hat.set(-9, y0, 0, None)
    # aba levemente erguida na frente
    for x in range(-5, 5):
        hat.set(x, y0 + 1, 8, cd)
        hat.set(x, y0, 8, None)
    hat.box(-5, y0 + 1, -4, 4, y0 + 2, 4, col)
    hat.box(-5, y0 + 1, -4, 4, y0 + 1, 4, band)
    hat.box(-1, y0 + 1, 5, 0, y0 + 2, 5, GOLD)
    hat.box(-4, y0 + 3, -4, 3, y0 + 4, 3, col)
    hat.box(-3, y0 + 5, -4, 2, y0 + 6, 2, col)
    hat.box(-2, y0 + 7, -5, 1, y0 + 8, 0, cl)
    hat.box(-2, y0 + 9, -6, 0, y0 + 9, -2, col)
    hat.box(-1, y0 + 9, -8, 0, y0 + 10, -5, col)
    hat.box(-1, y0 + 8, -10, 0, y0 + 9, -8, cd)
    hat.set(-1, y0 + 7, -10, GOLD)                 # pingente na ponta
    for (x, y, z) in ((-4, y0 + 3, 4), (3, y0 + 4, 4), (-3, y0 + 6, 3)):
        hat.set(x, y, z, cl)
    return hat, (0, y0, 0.5)


def legs(b: Body, pants, boot, boot_d, cuff=None):
    """Pernas curtas 3x3 com botas maiores que o membro. Devolve (esquerda x>0, direita x<0)."""
    l = Vox()
    l.box(1, 0, -1, 3, b.lh - 1, 1, pants)
    l.box(1, 0, -2, 4, 1, 2, boot)
    l.box(1, 0, 3, 4, 0, 3, boot)            # bico da bota
    l.box(1, 2, -1, 3, min(2, b.lh - 2), 1, cuff or boot)
    l.paint(lambda x, y, z, c: boot_d if c == boot and y == 0 else None)
    return l, l.flipped_x()


def arms(b: Body, sleeve, sleeve_d, hand=None, cuff=None):
    a = Vox()
    a.box(b.ax0, b.hand, -1, b.ax1, b.shoulder, 1, sleeve)
    a.box(b.ax0, b.hand + 2, -1, b.ax1, b.hand + 2, 1, cuff or sleeve_d)
    a.box(b.ax0, b.hand, -1, b.ax1, b.hand + 1, 1, hand or SKIN)
    a.set(b.ax1, b.shoulder, -1, None)                             # ombro arredondado
    a.set(b.ax1, b.shoulder, 1, None)
    return a, a.flipped_x()


def tunic(b: Body, col, col_d, col_l, belt=LEATHER, flare=True):
    """Tronco: túnica com barra clara que abre em sino sobre as pernas, cinto e fivela."""
    t = Vox()
    hw = b.tw // 2
    t.box(-hw, b.hip, -2, hw - 1, b.neck - 1, 2, col)
    if flare:
        t.box(-hw - 1, b.hip - 1, -3, hw, b.hip, 3, col)
        t.box(-hw - 1, b.hip - 1, -3, hw, b.hip - 1, 3, col_l)
        for (x, z) in ((-hw - 1, -3), (hw, -3), (-hw - 1, 3), (hw, 3)):
            t.box(x, b.hip - 1, z, x, b.hip, z, None)
    by = b.hip + 1
    t.box(-hw, by, -2, hw - 1, by, 2, belt)
    t.box(-hw, by, 3, hw - 1, by, 3, belt)
    t.box(-1, by, 3, 0, by, 3, GOLD)
    t.paint(lambda x, y, z, c: col_d if c == col and z <= -2 else None)
    return t


def sword(b: Body, x, long=12):
    """Espada vertical com o punho na mão direita; o animador a inclina para a frente."""
    s = Vox()
    y = b.hand
    s.set(x, y - 1, 0, GOLD_D)
    s.box(x, y, 0, x, y + 1, 0, LEATHER_D)
    s.box(x, y + 2, -2, x, y + 2, 2, GOLD)
    s.set(x, y + 2, -3, GOLD_D)
    s.set(x, y + 2, 3, GOLD_D)
    s.set(x, y + 3, 0, GOLD)
    s.box(x, y + 3, -1, x, y + 2 + long, 1, STEEL_D)
    s.box(x, y + 4, 0, x, y + 2 + long, 0, STEEL)
    s.set(x, y + 2 + long, -1, None)
    s.set(x, y + 2 + long, 1, None)
    s.set(x, y + 3 + long, 0, STEEL)
    return s, (x + 0.5, y + 1.0, 0.5)


def round_shield(b: Body, paint):
    s = Vox()
    cy = b.hand + 3
    x = b.ax1 + 1
    for y in range(-3, 4):
        for z in range(-3, 4):
            d = y * y + z * z
            if d <= 10:
                c = STEEL_D if d >= 8 else (tone(paint, 1.5) if (y == 0 or z == 0) else paint)
                s.set(x, cy + y, z, c)
    s.set(x + 1, cy, 0, GOLD)
    return s, (x, cy, 0.5)


def adventurer(name, leg_h=5, torso_h=9, voxel_size=VS):
    """Aventureiro com espada: túnica azul, cachecol vermelho esvoaçante, escudo redondo, ombreira."""
    b = Body(leg_h, torso_h)
    blue, blue_d, blue_l = C("#2f78de"), C("#2459ad"), C("#62a4f5")
    scarf, scarf_d = C("#e63b30"), C("#b3261f")
    hair, hair_hi = C("#8c4a22"), C("#b96a2c")

    t = tunic(b, blue, blue_d, blue_l)
    for i in range(min(5, torso_h - 4)):                   # alça diagonal no peito
        t.set(-3 + i, b.hip + 2 + i, 2, LEATHER_D)
    # cachecol: anel mais largo que o tronco, cobre o pescoço; nó à frente
    t.box(-5, b.neck - 1, -3, 4, b.neck - 1, 3, scarf)
    for (x, z) in ((-5, -3), (4, -3), (-5, 3), (4, 3)):
        t.set(x, b.neck - 1, z, None)
    t.box(1, b.neck - 3, 3, 2, b.neck - 2, 3, scarf_d)

    cape = Vox()   # pontas do cachecol caindo nas costas
    cl = min(6, torso_h - 3)
    cape.box(1, b.neck - 1 - cl, -4, 3, b.neck - 2, -4, scarf)
    cape.box(1, b.neck - 2 - cl, -4, 2, b.neck - 2 - cl, -4, scarf_d)
    cape.box(-1, b.neck - cl + 1, -4, 0, b.neck - 2, -4, scarf_d)

    hd = head_short(b, hair, hair_hi)
    leg_l, leg_r = legs(b, C("#414a6b"), LEATHER, LEATHER_D)
    arm_l, arm_r = arms(b, blue, blue_d, cuff=LEATHER)
    # ombreira no braço da espada: assimetria na silhueta
    arm_r.box(-b.ax1 - 2, b.shoulder - 1, -2, -b.ax0 - 1, b.shoulder, 2, STEEL_D)
    arm_r.box(-b.ax1 - 2, b.shoulder + 1, -1, -b.ax0 - 1, b.shoulder + 1, 1, STEEL)
    arm_r.set(-b.ax1 - 2, b.shoulder - 1, -2, None)
    arm_r.set(-b.ax1 - 2, b.shoulder - 1, 2, None)
    sw, grip = sword(b, -b.ax0 - 2)
    sh, sh_p = round_shield(b, blue)

    total = b.top + 2
    rig = Rig(name, voxel_size, "biped", meta={
        "headsTall": round(total / 10.0, 2), "legH": leg_h, "torsoH": torso_h, "headH": 10,
        "legFraction": round(leg_h / total, 3)})
    rig.part("torso", t, (0, b.hip, 0.5))
    rig.part("head", hd, (0, b.neck, 0.5), "torso")
    rig.part("arm_l", arm_l, (b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("arm_r", arm_r, (-b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("leg_l", leg_l, (2.5, b.hip, 0.5))
    rig.part("leg_r", leg_r, (-2.5, b.hip, 0.5))
    rig.part("weapon", sw, grip, "arm_r")
    rig.part("shield", sh, sh_p, "arm_l")
    rig.part("cape", cape, (2.0, b.neck - 1.5, -3.5), "torso")
    return rig


def ranger(name="Aventureira (A)"):
    """Aventureira leve: capuz caído, capa curta verde, arco e aljava nas costas, adaga na mão."""
    b = Body(5, 8, arm_w=2)
    green, green_d, green_l = C("#3fa24a"), C("#2c7a3c"), C("#74cc60")
    cloak, cloak_d = C("#2f6f52"), C("#235540")
    hair, hair_hi = C("#e2662b"), C("#ff9440")

    t = tunic(b, green, green_d, green_l, belt=LEATHER_D)
    t.box(-4, b.hip + 2, 2, 3, b.neck - 2, 2, C("#c98d4e"))        # colete de couro
    t.box(-1, b.hip + 2, 2, 0, b.neck - 2, 2, green_l)
    t.box(3, b.hip, 3, 4, b.hip + 1, 4, LEATHER)                     # bolsa no cinto
    t.set(3, b.hip + 1, 5, GOLD)
    # gola/capuz caído: volume atrás do pescoço
    t.box(-5, b.neck - 1, -3, 4, b.neck - 1, 3, cloak)
    for (x, z) in ((-5, 3), (4, 3)):
        t.set(x, b.neck - 1, z, None)
    t.box(-4, b.neck - 2, -4, 3, b.neck, -4, cloak)
    t.box(-3, b.neck - 1, -5, 2, b.neck, -5, cloak_d)
    # aljava em diagonal com penas das flechas acima do ombro (silhueta)
    for i in range(7):
        t.box(-4 + i // 2, b.hip + 1 + i, -4, -3 + i // 2, b.hip + 1 + i, -3, LEATHER)
    for (dx, c) in ((0, C("#f4f1e4")), (1, C("#e63b30"))):
        t.box(-1 + dx, b.neck, -4, -1 + dx, b.neck + 2, -4, WOOD_D)
        t.set(-1 + dx, b.neck + 3, -4, c)
        t.set(-1 + dx, b.neck + 4, -4, c)
    # arco atravessado nas costas: um "D" num só plano (z=-6) — madeira em curva, corda reta
    import math
    bow, string = C("#b9793c"), C("#efe6d0")
    x0, y0, x1, y1 = 5.0, b.hip - 2.0, -4.0, b.neck + 3.0
    t.line((round(x0), round(y0), -6), (round(x1), round(y1), -6), string)
    for i in range(41):
        k = i / 40.0
        bulge = math.sin(k * math.pi) * 3.2
        t.set(round(x0 + (x1 - x0) * k + 0.80 * bulge), round(y0 + (y1 - y0) * k - 0.55 * bulge), -6, bow)
    t.set(round(x0), round(y0), -6, GOLD)
    t.set(round(x1), round(y1), -6, GOLD)

    cape = Vox()
    cape.box(-4, b.hip - 1, -4, 3, b.neck - 3, -4, cloak)
    cape.box(-4, b.hip - 1, -4, 3, b.hip - 1, -4, cloak_d)
    for x in (-4, -1, 2):
        cape.set(x, b.hip - 1, -4, None)

    hd, tail, tail_p = head_ponytail(b, hair, hair_hi, C("#3fa24a"))
    leg_l, leg_r = legs(b, C("#6a4a34"), C("#5f381d"), C("#40240f"), cuff=C("#c98d4e"))
    arm_l, arm_r = arms(b, C("#f4f1e4"), C("#d9d2bd"), hand=LEATHER, cuff=LEATHER_D)
    dagger = Vox()
    x, y = -b.ax0 - 1, b.hand
    dagger.box(x, y, 0, x, y + 1, 0, LEATHER_D)
    dagger.box(x, y + 2, -1, x, y + 2, 1, GOLD)
    dagger.box(x, y + 3, 0, x, y + 6, 0, STEEL)
    dagger.set(x, y + 3, -1, STEEL_D)
    dagger.set(x, y + 4, -1, STEEL_D)

    total = b.top + 2
    rig = Rig(name, VS, "biped", meta={"headsTall": round(total / 10.0, 2), "legH": b.lh, "torsoH": b.th, "headH": 10})
    rig.part("torso", t, (0, b.hip, 0.5))
    rig.part("head", hd, (0, b.neck, 0.5), "torso")
    rig.part("tail", tail, tail_p, "head")
    rig.part("arm_l", arm_l, (b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("arm_r", arm_r, (-b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("leg_l", leg_l, (2.5, b.hip, 0.5))
    rig.part("leg_r", leg_r, (-2.5, b.hip, 0.5))
    rig.part("weapon", dagger, (x + 0.5, y + 1.0, 0.5), "arm_r")
    rig.part("cape", cape, (0, b.neck - 2.5, -3.5), "torso")
    return rig


def mage(name="Conjurador (A)"):
    """Conjurador: chapéu de aba larga com ponta caída, manto em sino, cajado com cristal."""
    b = Body(4, 9)
    robe, robe_d, robe_l = C("#7a46c8"), C("#5a2f9c"), C("#a878ee")
    trim = C("#f8c93a")
    hair, hair_hi = C("#e9e4f5"), C("#ffffff")
    crystal, crystal_l = C("#58e0ff", "emissive"), C("#d2f8ff", "emissive")

    t = tunic(b, robe, robe_d, robe_l, belt=C("#3a2a5c"), flare=False)
    t.box(-1, b.hip + 2, 2, 0, b.neck - 2, 2, trim)                 # faixa central dourada
    t.box(-5, b.neck - 1, -3, 4, b.neck - 1, 3, robe_l)              # gola larga
    for (x, z) in ((-5, -3), (4, -3), (-5, 3), (4, 3)):
        t.set(x, b.neck - 1, z, None)
    t.set(-1, b.neck - 2, 3, C("#58e0ff", "emissive"))               # broche
    t.set(0, b.neck - 2, 3, C("#58e0ff", "emissive"))
    t.box(-6, b.hip + 1, 0, -5, b.hip + 3, 2, C("#b8452f"))          # grimório no cinto
    t.box(-6, b.hip + 1, 0, -6, b.hip + 3, 0, C("#f4f1e4"))

    skirt = Vox()
    for y in range(1, b.hip + 1):
        w = 1 + (b.hip - y + 1) // 2
        w = min(w, 2)
        skirt.box(-4 - w, y, -2 - w, 3 + w, y, 2 + w, robe if y > 1 else robe_l)
        for (x, z) in ((-4 - w, -2 - w), (3 + w, -2 - w), (-4 - w, 2 + w), (3 + w, 2 + w)):
            skirt.set(x, y, z, None)
    skirt.paint(lambda x, y, z, c: robe_d if c == robe and (z <= -3 or (x + 50) % 4 == 0) else None)
    skirt.box(-1, 1, 3, 0, b.hip, 4, trim)
    skirt.box(-3, 1, -1, 2, b.hip - 1, 1, None)                      # oco para as pernas

    hd = head_mage(b, hair, hair_hi)
    hat, hat_p = wizard_hat(b, C("#6a3fc0"), trim)
    leg_l, leg_r = legs(b, robe_d, C("#4a3a2a"), C("#33261a"))
    arm_l, arm_r = arms(b, robe, robe_d, cuff=trim)
    for a, s in ((arm_l, 1), (arm_r, -1)):                          # mangas em sino
        x0, x1 = (b.ax0, b.ax1 + 1) if s > 0 else (-b.ax1 - 2, -b.ax0 - 1)
        a.box(x0, b.hand + 2, -2, x1, b.hand + 3, 2, robe)
        a.box(x0, b.hand + 2, -2, x1, b.hand + 2, 2, trim)

    staff = Vox()
    x, y = -b.ax0 - 2, b.hand
    staff.box(x, y - 6, 0, x, y + 8, 0, WOOD)
    for yy in (y - 5, y + 3, y + 6):
        staff.set(x, yy, 0, WOOD_D)
    staff.box(x, y + 8, -1, x, y + 8, 1, GOLD)                     # anel
    # garra de madeira abraçando um cristal grande (lê a distância)
    staff.box(x, y + 9, -2, x, y + 12, -2, WOOD_D)
    staff.box(x, y + 9, 2, x, y + 11, 2, WOOD_D)
    staff.set(x, y + 13, -1, WOOD_D)
    for dx in (-1, 0, 1):
        staff.box(x + dx, y + 10, -1, x + dx, y + 13, 1, crystal)
    staff.box(x, y + 9, -1, x, y + 14, 1, crystal)
    staff.box(x, y + 11, 0, x, y + 15, 0, crystal_l)
    staff.set(x + 1, y + 12, 1, crystal_l)

    total = b.top + 12
    rig = Rig(name, VS, "biped", meta={"headsTall": round((b.top + 2) / 10.0, 2), "legH": b.lh, "torsoH": b.th,
                                       "headH": 10, "weaponRest": 0.12, "note": "altura em cabeças sem o chapéu"})
    rig.part("torso", t, (0, b.hip, 0.5))
    rig.part("skirt", skirt, (0, b.hip, 0.5), "torso")
    rig.part("head", hd, (0, b.neck, 0.5), "torso")
    rig.part("hat", hat, hat_p, "head")
    rig.part("arm_l", arm_l, (b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("arm_r", arm_r, (-b.acx, b.shoulder + 0.5, 0.5), "torso")
    rig.part("leg_l", leg_l, (2.5, b.hip, 0.5))
    rig.part("leg_r", leg_r, (-2.5, b.hip, 0.5))
    rig.part("weapon", staff, (x + 0.5, y + 1.0, 0.5), "arm_r")
    return rig


# Estudo de proporções: mesmo personagem, só muda a altura de pernas e tronco.
PROPORTIONS = [("p22", 4, 7), ("p25", 5, 9), ("p30", 8, 11), ("p35", 11, 13), ("p40", 14, 15)]


def build(out):
    adventurer("Aventureiro (A)").save(f"{out}/characters/a/adventurer")
    ranger().save(f"{out}/characters/a/ranger")
    mage().save(f"{out}/characters/a/mage")
    for key, lh, th in PROPORTIONS:
        adventurer(f"proporcao {key}", lh, th).save(f"{out}/characters/study/{key}")
        # mesma altura total (normalizada à do herói de 2,5 cabeças): mostra o que acontece com a cabeça
        total = lh + th + 11
        adventurer(f"proporcao {key} normalizada", lh, th, voxel_size=VS * 25 / total).save(
            f"{out}/characters/study/{key}n")
