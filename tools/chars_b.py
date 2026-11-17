"""Direção B — Storybook Voxel: personagens compactos de livro ilustrado.

Linguagem de forma: massas ARREDONDADAS (superelipsoides p≈2,5), cabeça grande, corpo em pera,
mãos e botas grandes, pernas curtas (≤ 30% da altura). Voxel de 1/32 u; cabeça de 14×12×13 voxels
(16 de largura com cabelo). Proporção alvo: 2,3–2,6 cabeças.

O sombreamento "pintado" é assado nas cores: cada material tem 3 tons (sombra arroxeada, base, luz
pêssego) escolhidos pela normal suavizada do volume — não há ruído nem pontilhado nos personagens.

Este módulo também exporta os utilitários de forma/sombra usados por creatures_b.py e env_b.py.
"""
import math

from voxlib import C, Rig, Vox

VS = 1 / 32

# ----------------------------------------------------------------------------- cor

SHADOW_TINT = (0.78, 0.66, 0.82)     # multiplicador de sombra: roxo/vinho
LIGHT_TINT = (1.00, 0.86, 0.58)      # luz somada em "screen": amarelo-pêssego

RAMPS = {}                           # base -> (sombra, luz)


def shadow(c, k=1.0):
    return tuple(round(c[i] * (1 + (SHADOW_TINT[i] - 1) * k)) for i in range(3)) + (c[3],)


def light(c, k=1.0):
    return tuple(min(255, round(c[i] + (255 - c[i]) * LIGHT_TINT[i] * 0.30 * k)) for i in range(3)) + (c[3],)


def P(hexstr, mat="matte", ks=1.0, kl=1.0):
    """Cor base registrada com sua rampa de 3 tons (usada por shade())."""
    c = C(hexstr, mat)
    RAMPS[c] = (shadow(c, ks), light(c, kl))
    return c


# ----------------------------------------------------------------------------- forma

def sblob(v, cx, cy, cz, rx, ry, rz, col, p=2.5, p_lo=None):
    """Superelipsoide; p_lo = expoente da metade inferior (maior = mais 'cheio')."""
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        dy = (y + 0.5 - cy) / ry
        pp = p_lo if (p_lo and dy < 0) else p
        ay = abs(dy) ** pp
        if ay > 1:
            continue
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            ax = abs((x + 0.5 - cx) / rx) ** pp
            if ax + ay > 1:
                continue
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ax + ay + abs((z + 0.5 - cz) / rz) ** pp <= 1.0:
                    v.set(x, y, z, col)
    return v


def stack(v, y0, y1, prof, col, p=2.5):
    """Fatias horizontais superelípticas: prof(y) -> (cx, cz, rx, rz). Dá corpos em pera, sinos etc."""
    for y in range(y0, y1 + 1):
        cx, cz, rx, rz = prof(y)
        if rx <= 0 or rz <= 0:
            continue
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if abs((x + 0.5 - cx) / rx) ** p + abs((z + 0.5 - cz) / rz) ** p <= 1.0:
                    v.set(x, y, z, col if not callable(col) else col(x, y, z))
    return v


def rbox(v, x0, y0, z0, x1, y1, z1, col, r=1):
    """Caixa com as arestas amaciadas em um degrau (tira o canto vivo sem perder o voxel)."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                e = (x in (x0, x1)) + (y in (y0, y1)) + (z in (z0, z1))
                if e >= 2 and r:
                    continue
                v.set(x, y, z, col)
    return v


def front_z(v, x, y):
    zs = [k[2] for k in v.v if k[0] == x and k[1] == y]
    return max(zs) if zs else None


def back_z(v, x, y):
    zs = [k[2] for k in v.v if k[0] == x and k[1] == y]
    return min(zs) if zs else None


_OFFS = [(dx, dy, dz) for dx in range(-2, 3) for dy in range(-2, 3) for dz in range(-2, 3)
         if (dx, dy, dz) != (0, 0, 0)]
_LDIR = (-0.36, 0.80, 0.48)


def normal(v, x, y, z):
    sx = sy = sz = 0
    d = v.v
    for (dx, dy, dz) in _OFFS:
        if (x + dx, y + dy, z + dz) in d:
            sx -= dx
            sy -= dy
            sz -= dz
    ln = math.sqrt(sx * sx + sy * sy + sz * sz)
    if ln < 1e-6:
        return (0.0, 1.0, 0.0)
    return (sx / ln, sy / ln, sz / ln)


def shade(v, hi=0.62, lo=-0.30, only=None, ctx=None):
    """Troca cada cor-base registrada por sombra/base/luz conforme a normal suavizada
    (principalmente o componente vertical: o resultado independe do ângulo de câmera).
    ctx = volume usado para estimar a normal (padrão: o próprio v)."""
    src = ctx or v
    d = src.v
    for (x, y, z), c in list(v.v.items()):
        r = RAMPS.get(c)
        if r is None or (only and c not in only):
            continue
        if ((x + 1, y, z) in d and (x - 1, y, z) in d and (x, y + 1, z) in d and (x, y - 1, z) in d
                and (x, y, z + 1) in d and (x, y, z - 1) in d):
            continue
        n = normal(src, x, y, z)
        L = 0.8 * n[1] + 0.2 * (n[0] * _LDIR[0] + n[1] * _LDIR[1] + n[2] * _LDIR[2])
        if L > hi:
            v.v[(x, y, z)] = r[1]
        elif L < lo:
            v.v[(x, y, z)] = r[0]
    return v


# ----------------------------------------------------------------------------- paleta comum

SKIN = P("#f6c9a0", ks=0.6)
SKIN_TAN = P("#eeb98a", ks=0.6)
SKIN_PALE = P("#f9d6b4", ks=0.6)
BLUSH = C("#f29a8c")
EYE = C("#3a2238")
EYE_IRIS = C("#7c4a3c")
EYE_HI = C("#fff8ea")
MOUTH = C("#b9604e")
LEATHER = P("#9a6238")
LEATHER_D = P("#6e4130")
TAN = P("#c98f55")
CREAM = P("#f3e3c0")
GOLD = C("#f2c14e", "metal")
GOLD_D = C("#c98f3a", "metal")
STEEL = C("#f7f1e3")
STEEL_D = C("#d8cbc6")
WOOD = P("#a8713f")
WOOD_D = P("#7a4c33")

# marcos verticais compartilhados (voxels)
HIP = 9          # topo das pernas / pivô do quadril
SHOULDER = 19    # linha do ombro
SKULL_Y0 = 21    # base do crânio
SKULL_CY = 27.0  # centro do crânio (12 de altura: y 21..32)
HEAD_CZ = 0.5


# ----------------------------------------------------------------------------- partes comuns

def skull(skin=SKIN):
    v = Vox()
    sblob(v, 0, SKULL_CY, HEAD_CZ, 7.0, 6.0, 6.5, skin, p=2.5, p_lo=3.1)
    sblob(v, 0, SKULL_CY - 1.0, HEAD_CZ + 0.5, 6.6, 5.0, 6.0, skin, p=4.0)   # rosto plano, bochechas cheias
    for ex in (7, -8):                       # orelhas discretas
        v.box(ex, 24, 0, ex, 25, 1, skin)
    shade(v)
    return v


def paint_face(v, brow=None, eye_y=23, mood="brave", lash=False, iris=EYE_IRIS):
    """Rosto na superfície frontal: olhos 2×3 com brilho, bochechas, boca. Pintar DEPOIS de shade()."""
    def put(x, y, c):
        z = front_z(v, x, y)
        if z is not None:
            v.set(x, y, z, c)

    for (x0, hi_x, iris_x) in ((2, 2, 3), (-4, -4, -3)):
        for x in (x0, x0 + 1):
            for y in range(eye_y, eye_y + 3):
                put(x, y, EYE)
        put(hi_x, eye_y + 2, EYE_HI)
        put(iris_x, eye_y, iris)
    for x in (4, 5, -5, -6):
        put(x, eye_y - 1, BLUSH)
    if mood == "brave":          # sorriso de canto: assimétrico, com atitude
        put(-1, eye_y - 2, MOUTH)
        put(0, eye_y - 2, MOUTH)
        put(1, eye_y - 1, MOUTH)
    elif mood == "calm":
        put(-1, eye_y - 2, MOUTH)
        put(0, eye_y - 2, MOUTH)
    elif mood == "wonder":       # boquinha em 'o'
        put(-1, eye_y - 2, MOUTH)
        put(0, eye_y - 2, MOUTH)
        put(-1, eye_y - 1, MOUTH)
        put(0, eye_y - 1, MOUTH)
    if lash:
        put(4, eye_y + 2, EYE)
        put(-5, eye_y + 2, EYE)
    if brow:
        if mood == "brave":
            for (x, y) in ((1, eye_y + 3), (2, eye_y + 4), (3, eye_y + 4), (-2, eye_y + 3), (-3, eye_y + 4), (-4, eye_y + 4)):
                put(x, y, brow)
        else:
            for x in (2, 3, -3, -4):
                put(x, eye_y + 4, brow)
    return v


def hair_shell(sk, keep, col, grow=(1.0, 1.3, 1.0), cy_off=0.5, cz_off=-0.2):
    """Casca de cabelo em volta do crânio: células de um superelipsoide maior, fora do crânio,
    que satisfazem keep(x, y, z)."""
    big = Vox()
    sblob(big, 0, SKULL_CY + cy_off, HEAD_CZ + cz_off, 7.0 + grow[0], 6.0 + grow[1], 6.5 + grow[2], col, p=2.4)
    h = Vox()
    for (x, y, z) in big.v:
        if (x, y, z) not in sk.v and keep(x, y, z):
            h.v[(x, y, z)] = col
    return h


def boots(col, sole, cuff, pants, tall=False):
    """Perna curta com bota grande e arredondada. Devolve (esquerda x>0, direita x<0)."""
    l = Vox()
    l.box(1, 3, -1, 4, HIP - 1, 2, pants)
    for z in (-1, 2):
        l.set(1, HIP - 1, z, None)
    l.box(1, 0, -2, 5, 3, 3, col)
    l.box(2, 0, 4, 4, 2, 4, col)           # bico redondo
    l.box(1, 0, 4, 5, 1, 4, col)
    l.box(2, 0, 5, 4, 0, 5, col)
    for (x, z) in ((1, -2), (5, -2)):
        l.box(x, 2, z, x, 3, z, None)
    l.set(1, 3, 3, None)
    l.set(5, 3, 3, None)
    top = 4 + (2 if tall else 0)
    l.box(1, 4, -2, 5, top, 3, col)
    l.box(0, top, -2, 5, top, 3, cuff)     # dobra do cano, um voxel mais larga
    l.box(1, top, -3, 5, top, -3, cuff)
    for (x, z) in ((0, -2), (5, -3), (1, -3), (0, 3), (5, 3)):
        l.set(x, top, z, None)
    l.paint(lambda x, y, z, c: sole if y == 0 else None)
    shade(l)
    return l, l.flipped_x()


def arms(sleeve, cuff, glove, puff=True, bell=False, skin=None):
    """Braço em leve pose 'A' com manga e mão grande. (esquerdo x>0, direito x<0)"""
    a = Vox()
    a.box(4, 17, -1, 6, SHOULDER, 1, sleeve)
    a.box(5, 13, -1, 7, 16, 1, sleeve)
    a.set(6, SHOULDER, -1, None)
    a.set(6, SHOULDER, 1, None)
    if bell:                               # manga-sino de mago
        a.box(5, 12, -2, 8, 14, 2, sleeve)
        a.box(4, 12, -1, 8, 13, 1, sleeve)
        for (x, z) in ((5, -2), (8, -2), (5, 2), (8, 2)):
            a.box(x, 14, z, x, 14, z, None)
        a.paint(lambda x, y, z, c: cuff if y == 12 else None)
        a.box(5, 10, -1, 7, 11, 1, skin or SKIN)
        a.set(5, 10, -1, None)
        a.set(7, 10, 1, None)
    else:
        if puff:
            a.box(5, 13, -2, 8, 14, 2, sleeve)
            for (x, z) in ((5, -2), (8, -2), (5, 2), (8, 2)):
                a.box(x, 13, z, x, 14, z, None)
            a.paint(lambda x, y, z, c: cuff if y == 13 else None)
        a.box(5, 10, -1, 8, 12, 2, glove)
        for (x, z) in ((5, -1), (8, -1), (5, 2), (8, 2)):
            a.set(x, 10, z, None)
            a.set(x, 12, z, None)
    shade(a)
    return a, a.flipped_x()


def torso_pear(col, y0=7, y1=20, rx0=5.7, rx1=3.4, rz0=4.3, rz1=3.0, belly=0.3):
    v = Vox()

    def prof(y):
        t = (y - y0) / max(1, (y1 - y0))
        return (0, 0.5 + belly * math.sin(math.pi * min(1.0, t * 1.4)), rx0 - (rx0 - rx1) * t ** 1.3,
                rz0 - (rz0 - rz1) * t)

    stack(v, y0, y1, prof, col, p=2.6)
    return v


def ring(v, y0, y1, rx, rz, col, cz=0.5, p=2.3):
    stack(v, y0, y1, lambda y: (0, cz, rx, rz), col, p=p)
    return v


def strap(v, pts, col):
    """Faixa pintada sobre a superfície frontal: pts = [(x, y)]."""
    for (x, y) in pts:
        z = front_z(v, x, y)
        if z is not None:
            v.set(x, y, z, col)


def finish(name, parts, head_top, meta_extra=None):
    """Monta o Rig a partir de [(nome, vox, pivô, pai)] e calcula proporções (sem arma/chapéu)."""
    rig = Rig(name, VS, "biped")
    for (n, vox, piv, par) in parts:
        rig.part(n, vox, piv, par)
    head_h = head_top - SKULL_Y0 + 1
    total = head_top + 1
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "headH": head_h,
                "bodyHeightVoxels": total, "legFraction": round(HIP / total, 3)}
    if meta_extra:
        rig.meta.update(meta_extra)
    return rig


# ----------------------------------------------------------------------------- aventureiro

def adventurer():
    """Protagonista: cabelo castanho-ferrugem rebelde, túnica verde-petróleo, cachecol mostarda,
    mochila com saco de dormir e espada larga de conto."""
    tunic = P("#3f9388")
    trim = CREAM
    scarf = P("#eaa93c")
    pants = P("#6b4a5e")
    hair = P("#b9542d", ks=0.8, kl=0.9)
    pack = P("#8a5536")
    roll = P("#e9d6a8")
    roll_e = P("#cdb98c")
    roll_s = P("#d9644a")

    # --- tronco
    t = torso_pear(tunic)
    t.paint(lambda x, y, z, c: trim if y == 7 else None)
    t.paint(lambda x, y, z, c: LEATHER if y == 11 else None)
    strap(t, [(x, y) for y in range(12, 20) for x in (2, -3)], LEATHER)
    zb = front_z(t, 0, 11)
    t.box(-1, 11, zb + 1, 0, 11, zb + 1, GOLD)          # fivela
    ring(t, 18, 20, 5.3, 4.7, scarf)
    ring(t, 19, 19, 5.9, 5.2, scarf)
    t.box(4, 18, 3, 5, 19, 4, scarf)                     # nó do cachecol
    t.box(5, 16, 4, 5, 17, 4, scarf)
    # mochila + saco de dormir
    rbox(t, -4, 9, -8, 3, 16, -4, pack)
    t.box(-3, 14, -9, 2, 16, -9, TAN)                    # aba
    t.box(-3, 10, -9, 2, 12, -9, LEATHER)                # bolso
    t.box(-1, 14, -10, 0, 14, -10, GOLD)
    for x in range(-6, 6):
        for y in range(16, 22):
            for z in range(-10, -3):
                if (y + 0.5 - 18.7) ** 2 + (z + 0.5 + 6.5) ** 2 <= 2.6 ** 2:
                    end = x in (-6, 5)
                    t.set(x, y, z, roll_s if x in (-4, -3, 2, 3) else (roll_e if end else roll))
    shade(t)

    # --- ponta do cachecol (balança)
    cape = Vox()
    cape.box(4, 12, -5, 6, 19, -4, scarf)
    cape.box(4, 12, -5, 4, 13, -5, None)
    cape.box(6, 18, -5, 6, 19, -4, None)
    cape.box(4, 12, -4, 6, 12, -4, None)
    shade(cape)
    cape.paint(lambda x, y, z, c: shadow(scarf) if y <= 13 else None)

    # --- cabeça
    sk = skull()
    paint_face(sk, brow=None, mood="brave")
    fringe = {-8: 23, -7: 24, -6: 25, -5: 27, -4: 29, -3: 29, -2: 28, -1: 29, 0: 30, 1: 30, 2: 29,
              3: 29, 4: 28, 5: 29, 6: 27, 7: 25}

    def keep(x, y, z):
        if z >= 3:
            return y >= fringe.get(x, 24)
        if z >= 0:
            return y >= 27 or (y >= 23 and abs(x + 0.5) > 7)
        return y >= 22 + ((x + 8) // 3) % 2

    h = hair_shell(sk, keep, hair)
    # mechas grandes: quebram a silhueta de "capacete"
    sblob(h, -2.0, 33.0, -1.0, 5.0, 2.4, 5.0, hair)
    sblob(h, 4.5, 32.0, 1.0, 3.6, 2.6, 4.0, hair)
    sblob(h, -6.5, 30.5, -1.0, 2.6, 3.0, 4.0, hair)
    sblob(h, 0.0, 28.0, -6.5, 5.0, 4.0, 2.4, hair)
    h.box(1, 35, -1, 2, 35, 0, hair)          # topete em gancho
    h.box(2, 36, -1, 3, 36, 0, hair)
    h.set(4, 36, -1, hair)
    for (x, y, z) in list(h.v):               # não invade o rosto
        if (x, y, z) in sk.v or (z >= 4 and y < fringe.get(x, 24)):
            del h.v[(x, y, z)]
    shade(h)
    hd = sk.copy()
    hd.merge(h)

    leg_l, leg_r = boots(TAN, LEATHER_D, light(TAN), pants)
    arm_l, arm_r = arms(tunic, trim, LEATHER)

    # --- espada larga (lâmina no plano XY), vertical, punho no pivô
    s = Vox()
    s.box(-8, 8, 0, -7, 12, 0, LEATHER_D)
    s.box(-8, 6, 0, -7, 7, 0, GOLD)
    s.box(-8, 6, -1, -7, 7, 1, GOLD_D)
    s.box(-8, 6, 0, -7, 7, 0, GOLD)
    s.box(-10, 13, 0, -5, 13, 0, GOLD)
    s.box(-10, 13, -1, -5, 13, 1, GOLD_D)
    s.box(-10, 13, 0, -5, 13, 0, GOLD)
    s.set(-11, 14, 0, GOLD)
    s.set(-4, 14, 0, GOLD)
    for y in range(14, 28):
        x0, x1 = (-9, -6) if y < 25 else ((-9, -6) if y < 26 else (-8, -7))
        for x in range(x0, x1 + 1):
            s.set(x, y, 0, STEEL if x in (-8, -7) else STEEL_D)
    s.set(-9, 25, 0, None)
    s.box(-8, 15, 0, -8, 22, 0, STEEL_D)      # sulco

    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", hd, (0, SKULL_Y0, 0.5), "torso"),
        ("arm_l", arm_l, (5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("arm_r", arm_r, (-5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("leg_l", leg_l, (3.0, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.0, HIP, 0.5), None),
        ("weapon", s, (-7.0, 11.0, 0.5), "arm_r"),
        ("cape", cape, (5.5, 19.5, -4.0), "torso"),
    ]
    return finish("Aventureiro (B)", parts, hd.bounds()[4])


# ----------------------------------------------------------------------------- ranger

def ranger():
    """Aventureira leve: capelinha musgo com capuz caído, gorro de pena, trança longa,
    arco na mão esquerda, adaga na direita, aljava e bolsa."""
    tunic = P("#f0dcae")
    cloak = P("#7c9a46")
    cloak_d = P("#5f7d3c")
    legg = P("#8c5340")
    hair = P("#eab04e", ks=0.8, kl=0.8)
    red = P("#d6513f")
    quiv = P("#b0623c")

    t = torso_pear(tunic, rx0=5.9, rx1=3.2, rz0=4.5, rz1=2.9)
    t.paint(lambda x, y, z, c: cloak_d if y == 7 else None)
    t.paint(lambda x, y, z, c: LEATHER if y == 11 else None)
    zb = front_z(t, 0, 11)
    t.box(-1, 11, zb + 1, 0, 11, zb + 1, GOLD)
    strap(t, [(-4 + i, 18 - i) for i in range(0, 8)] + [(-3 + i, 18 - i) for i in range(0, 7)], LEATHER_D)
    # bolsa no quadril esquerdo
    rbox(t, 3, 6, -6, 7, 10, -3, LEATHER, r=0)
    t.box(3, 9, -7, 7, 10, -7, TAN)
    t.box(3, 9, -6, 7, 10, -3, TAN)
    t.set(5, 8, -7, GOLD)
    # aljava diagonal nas costas, flechas sobre o ombro direito
    for i in range(0, 12):
        x = -1 - round(i * 0.42)
        t.box(x - 2, 9 + i, -7, x, 9 + i, -5, TAN if i in (2, 9) else quiv)
    t.box(-8, 20, -7, -6, 20, -5, TAN)
    for (x, z, top) in ((-8, -7, 24), (-7, -5, 25), (-6, -6, 23)):
        t.box(x, 21, z, x, top, z, CREAM)
        t.set(x, top, z, red)
        t.set(x, top - 1, z, red)
    # capelinha com barra em bico + capuz caído
    cp = Vox()

    def prof(y):
        k = (21 - y) / 6.0
        return (0, 0.3, 4.8 + 2.4 * k ** 0.8, 4.4 + 1.8 * k ** 0.8)

    stack(cp, 15, 21, prof, cloak, p=2.3)
    for (x, y, z) in list(cp.v):
        if y == 15 and (x + z) % 2 == 0:
            del cp.v[(x, y, z)]
    cp.paint(lambda x, y, z, c: cloak_d if y <= 16 else None)
    sblob(cp, 0, 20.3, -4.6, 5.0, 2.6, 3.2, cloak)
    sblob(cp, 0, 21.0, -4.4, 3.4, 1.6, 2.0, cloak_d)
    t.merge(cp)
    zc = front_z(t, 0, 19)
    t.box(-1, 18, zc + 1, 0, 19, zc + 1, GOLD)          # broche
    shade(t)

    sk = skull(SKIN_TAN)
    paint_face(sk, brow=None, mood="calm", lash=True)
    fringe = {-8: 21, -7: 21, -6: 24, -5: 28, -4: 29, -3: 29, -2: 30, -1: 30, 0: 30, 1: 30, 2: 29, 3: 29,
              4: 29, 5: 28, 6: 24, 7: 21}

    def keep(x, y, z):
        if z >= 2:
            return y >= fringe.get(x, 21)
        if z >= -1:
            return y >= 27 or (y >= 21 and abs(x + 0.5) > 7)
        return y >= 21

    h = hair_shell(sk, keep, hair, grow=(1.0, 1.3, 1.0))
    sblob(h, 0, 27.5, -6.0, 5.6, 5.0, 2.4, hair)
    for (x, y, z) in list(h.v):
        if (x, y, z) in sk.v or (z >= 4 and y < fringe.get(x, 21)):
            del h.v[(x, y, z)]
    shade(h)
    hd = sk.copy()
    hd.merge(h)
    head_top = hd.bounds()[4]

    # trança (balança): gomos alternados, laço vermelho, pincel na ponta
    tail = Vox()
    for i, y in enumerate(range(22, 9, -3)):
        ox = 1 if i % 2 else 0
        sblob(tail, 2.5 + ox * 0.6, y, -8.0 - min(i, 2) * 0.5, 2.0, 2.0, 1.9, hair)
    shade(tail)
    tail.paint(lambda x, y, z, c: shadow(hair, 1.3) if (y - 22) % 3 == 2 else None)
    tail.box(1, 9, -10, 4, 10, -8, red)
    tail.box(2, 6, -10, 3, 8, -9, hair)
    tail.set(2, 5, -9, shadow(hair))

    # gorro com pena
    hat = Vox()
    for i in range(5):                                         # copa pontuda puxada para trás
        stack(hat, 33 + i, 33 + i, lambda y: (0.5, -0.6 - 1.1 * i, 6.3 - 1.25 * i, 6.3 - 1.1 * i), cloak, p=2.2)
    stack(hat, 33, 33, lambda y: (0.5, 0.5, 7.2, 7.4), cloak_d, p=2.2)
    for (x, z) in ((0, 8), (1, 8), (0, 9), (1, 9), (0, 10)):   # bico da aba
        hat.set(x, 33, z, cloak_d)
    shade(hat)
    for i in range(0, 6):                                      # pena
        fx, fy, fz = 5 + i // 3, 34 + (i * 5) // 7, -1 - i
        hat.set(fx, fy, fz, red)
        hat.set(fx, fy + 1, fz, red if i < 4 else light(red))
        if 1 <= i <= 3:
            hat.set(fx, fy + 2, fz, light(red))
    hat.set(5, 34, 0, CREAM)

    leg_l, leg_r = boots(LEATHER_D, shadow(LEATHER_D), LEATHER, legg, tall=True)
    arm_l, arm_r = arms(tunic, cloak_d, TAN, puff=False)

    # adaga
    s = Vox()
    s.box(-8, 9, 0, -7, 12, 0, LEATHER_D)
    s.box(-8, 8, 0, -7, 8, 0, GOLD)
    s.box(-9, 13, 0, -6, 13, 0, GOLD)
    s.box(-8, 13, -1, -7, 13, 1, GOLD_D)
    s.box(-8, 14, 0, -7, 20, 0, STEEL)
    s.box(-8, 14, 0, -8, 19, 0, STEEL_D)
    s.set(-7, 21, 0, STEEL)

    # arco na mão esquerda (rígido com o braço)
    bow = Vox()
    for y in range(1, 22):
        k = (y - 11) / 10.0
        z = 4 - round(5.2 * k * k)
        bow.set(7, y, z, WOOD)
        if abs(k) < 0.95:
            bow.set(7, y, z - 1 if abs(k) > 0.5 else z, WOOD)
    bow.box(7, 10, 3, 7, 12, 4, LEATHER_D)
    bow.box(6, 10, 3, 6, 12, 4, LEATHER_D) if False else None
    for y in range(2, 21):
        bow.set(7, y, -2, CREAM)
    bow.set(7, 1, -1, GOLD)
    bow.set(7, 21, -1, GOLD)

    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", hd, (0, SKULL_Y0, 0.5), "torso"),
        ("arm_l", arm_l, (5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("arm_r", arm_r, (-5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("leg_l", leg_l, (3.0, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.0, HIP, 0.5), None),
        ("weapon", s, (-7.0, 11.0, 0.5), "arm_r"),
        ("bow", bow, (7.5, 11.0, 0.5), "arm_l"),
        ("tail", tail, (2.5, 23.5, -6.5), "head"),
        ("hat", hat, (0.5, 33.0, 0.5), "head"),
    ]
    return finish("Ranger (B)", parts, head_top)


# ----------------------------------------------------------------------------- mago

def mage():
    """Conjurador: chapéu enorme de ponta caída, cabelo creme fofo, manto-sino ameixa com barra
    dourada (parte 'skirt') e cajado com cristal emissivo."""
    robe = P("#7b4c86")
    robe_d = P("#5d3a6e")
    gold = P("#ecb84c")
    hair = P("#e4d8f4", ks=0.8, kl=0.5)
    crystal = C("#86f0dc", "emissive")
    crystal_hi = C("#e8fff6", "emissive")

    t = Vox()

    def prof(y):
        k = (y - 9) / 11.0
        return (0, 0.5 + 0.2 * math.sin(math.pi * k), 5.0 - 1.7 * k ** 1.2, 4.0 - 1.0 * k)

    stack(t, 9, 20, prof, robe, p=2.6)
    t.paint(lambda x, y, z, c: gold if y in (11, 12) else None)
    strap(t, [(x, y) for y in range(13, 19) for x in (-1, 0)], robe_d)
    ring(t, 18, 20, 5.6, 4.9, robe_d)                     # pelerine
    ring(t, 20, 21, 4.8, 4.3, CREAM)                      # gola
    zc = front_z(t, 0, 18)
    t.box(-1, 17, zc, 0, 18, zc, crystal)                 # broche
    shade(t)

    sk_ = Vox()                                           # manto-sino

    def bell(y):
        k = (10 - y) / 7.0
        return (0, 0.4, 5.1 + 2.5 * k ** 0.75, 4.1 + 2.4 * k ** 0.75)

    stack(sk_, 3, 10, bell, robe, p=2.4)
    for (x, y, z) in list(sk_.v):                         # oco embaixo: as botas aparecem
        if y <= 5:
            cx, cz, rx, rz = bell(y)
            if abs((x + 0.5) / (rx - 1.2)) ** 2.4 + abs((z + 0.5 - cz) / (rz - 1.2)) ** 2.4 <= 1.0:
                del sk_.v[(x, y, z)]
    sk_.paint(lambda x, y, z, c: gold if y == 3 else (robe_d if y == 4 else None))
    for (x, y) in ((-5, 7), (4, 6), (-2, 5), (1, 8), (6, 8), (-7, 5)):   # estrelinhas bordadas
        z = front_z(sk_, x, y)
        if z is not None:
            sk_.set(x, y, z, GOLD)
    for (x, y) in ((-4, 6), (3, 7), (0, 5)):
        z = back_z(sk_, x, y)
        if z is not None:
            sk_.set(x, y, z, GOLD)
    shade(sk_)

    sk = skull(SKIN_PALE)
    paint_face(sk, brow=None, mood="calm", iris=C("#6d4a8c"))

    def keep(x, y, z):
        if z >= 3:
            return y >= (28 if abs(x + 0.5) < 6 else 22)
        return y >= 22

    h = hair_shell(sk, keep, hair, grow=(1.6, 1.0, 1.2))
    sblob(h, 7.0, 24.5, -0.5, 2.6, 3.2, 4.6, hair)        # tufos laterais fofos
    sblob(h, -7.0, 24.5, -0.5, 2.6, 3.2, 4.6, hair)
    sblob(h, 0, 25.5, -6.5, 6.4, 4.4, 2.4, hair)
    for (x, y, z) in list(h.v):
        if (x, y, z) in sk.v or (z >= 4 and abs(x + 0.5) < 6 and y < 28):
            del h.v[(x, y, z)]
    shade(h)
    hd = sk.copy()
    hd.merge(h)
    head_top = 34

    # chapéu: aba larga inclinada para trás + cone torto com a ponta caída
    hat = Vox()
    for x in range(-12, 12):
        for z in range(-12, 13):
            d = ((x + 0.5) / 11.3) ** 2 + ((z + 0.0) / 11.0) ** 2
            if d <= 1.0:
                y = 31 + (1 if z >= 4 else 0) - (1 if (z <= -6 or d > 0.8 and x < -3 and z < 3) else 0)
                hat.set(x, y, z, robe)
                if d < 0.62:
                    hat.set(x, y + 1, z, robe)
    n = 7
    for i in range(n):
        k = i / (n - 1)
        r = 6.6 * (1 - k) ** 0.85 + 1.2
        cx, cz = 4.6 * k ** 2.0, -0.2 - 3.4 * k ** 2.0
        stack(hat, 33 + i, 33 + i, lambda y: (cx, cz, r, r), gold if i in (0, 1) else robe, p=2.2)
    sblob(hat, 6.6, 38.6, -4.6, 1.9, 1.4, 1.9, robe)      # ponta dobrada
    sblob(hat, 8.2, 37.8, -5.4, 1.5, 1.5, 1.5, robe)
    hat.set(8, 36, -6, gold)
    hat.set(8, 35, -6, gold)
    zf = front_z(hat, 0, 33)
    hat.box(-1, 33, zf + 1, 0, 34, zf + 1, GOLD)          # fivela
    for (x, y) in ((-4, 36), (2, 38), (4, 35)):
        z = front_z(hat, x, y)
        if z is not None:
            hat.set(x, y, z, GOLD)
    shade(hat)
    for (x, y, z), c in list(hat.v.items()):              # debrum claro na borda da aba
        if y <= 32 and c[:3] != GOLD[:3] and ((x + 0.5) / 11.3) ** 2 + (z / 11.0) ** 2 > 0.84:
            hat.v[(x, y, z)] = gold

    leg_l, leg_r = boots(LEATHER, LEATHER_D, TAN, robe_d)
    arm_l, arm_r = arms(robe, gold, None, bell=True, skin=SKIN_PALE)

    # cajado: modelado inclinado 45° para trás; o animador soma ~60° para a frente,
    # e o resultado é um cajado quase vertical, levemente apontado adiante.
    s = Vox()
    X = -7
    for i in range(-7, 15):
        for d in (0, 1):
            s.set(X, 11 + i, -i + d, WOOD if (i // 3) % 2 == 0 else WOOD_D)
            s.set(X - 1, 11 + i, -i + d, WOOD_D if (i // 3) % 2 == 0 else WOOD)
    ty, tz = 11 + 17, -17
    sblob(s, X, ty, tz + 0.5, 2.3, 3.2, 3.2, crystal, p=1.25)
    sblob(s, X, ty, tz + 0.5, 1.2, 1.8, 1.8, crystal_hi, p=1.25)
    s.box(X - 2, 11 + 14, -15, X + 1, 11 + 14, -13, GOLD)
    s.box(X - 2, 11 + 15, -16, X + 1, 11 + 15, -14, GOLD_D)
    s.set(X - 2, ty - 2, tz + 3, GOLD)
    s.set(X + 1, ty - 2, tz + 3, GOLD)
    s.set(X - 2, ty, tz - 2, GOLD)
    s.set(X + 1, ty, tz - 2, GOLD)

    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", hd, (0, SKULL_Y0, 0.5), "torso"),
        ("arm_l", arm_l, (5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("arm_r", arm_r, (-5.5, SHOULDER + 0.5, 0.5), "torso"),
        ("leg_l", leg_l, (3.0, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.0, HIP, 0.5), None),
        ("skirt", sk_, (0, 10.5, 0.5), "torso"),
        ("weapon", s, (X, 11.0, 0.5), "arm_r"),
        ("hat", hat, (0, 32.0, 0.5), "head"),
    ]
    return finish("Maga (B)", parts, head_top)


def build(out):
    adventurer().save(f"{out}/characters/b/adventurer")
    ranger().save(f"{out}/characters/b/ranger")
    mage().save(f"{out}/characters/b/mage")
