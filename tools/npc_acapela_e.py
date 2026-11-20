#!/usr/bin/env python3
"""Acapela (NPC, direção E): uma criaturinha de rabo peludo de lagarto e cabeça redonda com tufinhos, em jardineira.

Mesmo esqueleto dos NPCs da vila (`npcs_vila_e`: quadril 14, pescoço 29, ombros ±5,8 / 26,5) — a locomoção e os
validadores valem sem mudança; o perfil é o `npc_acapela` (o de `npc_vila` com o aceno passando à frente das franjas
da bochecha). O que é PRÓPRIO dele:

  cabeça    crânio redondo (proporção chibi, ~2,1 cabeças), placa do rosto plana de contorno redondo; anel de
            tufinhos pontudos da cor da pele em volta e colar de pelo verde-escuro no queixo e nas bochechas; cabelo
            creme espetado (mechas inclinadas para um lado, mecha em V na testa, nuca) com DOIS TUFOS que descem atrás
            dos lados até os ombros; olhos grandes de íris escura (preta em cima, roxa embaixo, com fenda) e brilho,
            asinha verde-clara do lado de fora; sobrancelhas retangulares; focinho pequeno; boca ":3" no dia a dia e as
            duas presas quando ri
  corpo     gola alta preta de manga comprida; jardineira roxa (peitilho, alças com dois botões claros, bolso lilás,
            shorts com barra dobrada lilás); patas de pelo marrom com tufos e três garras; pernas e pés menta com dedos
  rabo      parte `tail` (filha do tronco), peluda, com tufos no dorso e na ponta: o Animator já o balança no parado, no
            andar, na corrida e no salto

Altura narrativa por reamostragem do rig pronto, como os NPCs de ofício, mas com `scale_keep` (os traços do rosto
vencem quando duas fileiras caem no mesmo voxel).

Saída: assets/characters/npc/acapela/ (gerado; não edite à mão).
"""
import json
from pathlib import Path

import chars_b as B
import charlib_e as L
import npcs_oficios_e as O
import npcs_vila_e as T
from voxlib import C, Rig, Vox, exposed_faces

P = B.P
VS = 1 / 40
NECK, HIP, KNEE, ELBOW = T.NECK, T.HIP, T.KNEE, T.ELBOW
SH_X, EL_X, SHOULDER = T.SH_X, T.EL_X, T.SHOULDER
METERS = 1.50                       # altura narrativa (0,75 u por metro): 1,125 u, a dos moradores da vila

MINT = P("#9ccfc8", ks=0.7, kl=0.6)             # pele (menta azulada)
MINT_D = P("#7fb7ae", ks=0.7)                   # pontas das franjas
CREAM = P("#f1d9a2", ks=0.45, kl=0.6)           # topete e cabelo (sombra leve: sem rosado)
CREAM_D = P("#dcbd84", ks=0.45)
BLACK = P("#2e2d33", kl=0.9)                    # gola alta e mangas
PURPLE = P("#a25ec6")                           # jardineira
PURPLE_D = P("#7d4699")
LILAC = P("#e3b5f2", kl=0.5)                    # barras dobradas
POCKET = P("#c98ade")                           # bolso do peitilho
FUR = P("#97593a")                              # mãos e antebraços de pelo
FUR_L = P("#b5774f")
CLAW = P("#f2e9d6")                             # garras e presas
BUTTON = C("#d9d9e2", "metal")
EYE_W = C("#cdf5c2")                            # asinha verde-clara do lado de fora do olho
IRIS_HI = C("#7d61b0")                          # íris: roxo da metade de baixo
IRIS_LO = C("#55408a")
FUR_G = P("#5f8e77", ks=0.7)                    # colar de pelo verde-escuro (queixo e bochechas), pontas do rabo
BROW_SH = C("#78a998")                          # sombra verde sob as sobrancelhas
EYE_INK = C("#2d4c47")                          # traço do olho
PUPIL = C("#1d1d24")
EYE_HI = C("#ffffff")
BROW = C("#26262b")
MOUTH = C("#55296a")
NOSE = C("#5f8f86")
TONGUE = C("#b4566e")
FZ = 7                                          # plano da placa do rosto (crânio maior que o dos NPCs da vila)
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def spec():
    """Ficha para os construtores da vila (tronco, braços, pernas) com a paleta do Acapela."""
    return dict(T.SPECS["suvia"], title="Acapela (NPC, E)", skin=MINT, hair=CREAM, coat=PURPLE, coat_d=PURPLE_D,
                shirt=BLACK, vest=PURPLE, pants=PURPLE, boot=MINT, cuff=LILAC, sleeve=BLACK, collar="high",
                belt="apron", charm=None, tails=None, role="morador", act_style="playful", freckles=False, ears=False,
                ribbon=None)


# ----------------------------------------------------------------------------- cabeça

def front(v, x, y):
    return L.front_z(v, x, y)


def paint(v, x, y, c, out=0):
    z = front(v, x, y)
    if z is not None:
        v.set(x, y, z + out, c)


def skull():
    """Crânio REDONDO; a placa do rosto é plana (contrato) com contorno de círculo."""
    import math
    v = Vox()
    cy = NECK + 9.5
    B.sblob(v, 0, cy, 0.0, 8.8, 9.2, 7.6, MINT, p=2.05)
    for k in [k for k in v.v if k[2] > FZ or k[1] < NECK]:
        del v.v[k]
    for x in range(-9, 9):
        for y in range(NECK + 1, NECK + 18):
            half = math.sqrt(max(0.0, 8.6 ** 2 - (y + 0.5 - cy) ** 2))
            f = front(v, x, y)
            if f is not None and f < FZ and abs(x + 0.5) <= half - 0.3:
                for z in range(f, FZ + 1):
                    v.set(x, y, z, MINT)
    L.shade(v)
    return v


def tri(v, a, b, c, z0, z1, col=MINT, tip_col=MINT_D):
    """Triângulo de lado reto (a e b na base, c na ponta) extrudado em z0..z1; o terço da ponta num segundo tom."""
    xs, ys = [p[0] for p in (a, b, c)], [p[1] for p in (a, b, c)]

    def side(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    reach = ((c[0] - mid[0]) ** 2 + (c[1] - mid[1]) ** 2) ** 0.5
    for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
        for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
            q = (x + 0.5, y + 0.5)
            d1, d2, d3 = side(a, b, q), side(b, c, q), side(c, a, q)
            if (d1 >= 0 and d2 >= 0 and d3 >= 0) or (d1 <= 0 and d2 <= 0 and d3 <= 0):
                far = ((q[0] - mid[0]) ** 2 + (q[1] - mid[1]) ** 2) ** 0.5 / max(reach, 1e-6)
                for z in range(z0, z1 + 1):
                    v.set(x, y, z, tip_col if far > 0.66 else col)


def ring(v):
    """Contorno de tufinhos pontudos em volta da cabeça redonda (atrás da placa do rosto, z ≤ 3) e, embaixo, o colar
    de pelo verde-escuro do queixo e das bochechas. Ângulos do lado +x (0° = para fora, 90° = para cima); −x é espelho."""
    import math
    f = Vox()
    c = (0.0, NECK + 9.5)
    R = 8.4
    tufts = [(a, 3.6, 2.4, MINT, MINT_D) for a in (0, 24, 48, 70)] + \
            [(a, 4.4, 2.6, FUR_G, FUR_G) for a in (-24, -48, -72)]
    for (deg, length, half, col, tip_col) in tufts:
        for sx in (1, -1):
            a = math.radians(deg)
            n = (sx * math.cos(a), math.sin(a))
            t = (-n[1], n[0])
            b = (c[0] + n[0] * (R - 1.2), c[1] + n[1] * (R - 1.2))
            tip = (c[0] + n[0] * (R + length), c[1] + n[1] * (R + length) + (0.6 if deg > 0 else 0.0))
            tri(f, (b[0] + t[0] * half, b[1] + t[1] * half), (b[0] - t[0] * half, b[1] - t[1] * half), tip, -3, 3,
                col, tip_col)
    for k in [k for k in f.v if k in v.v or k[1] < NECK - 1]:
        del f.v[k]
    L.shade(f, only={MINT, FUR_G})
    v.merge(f)


def hair(v):
    """Cabelo creme espetado (três pontas no alto, inclinadas para +x), mecha caindo sobre a testa e os DOIS TUFOS que
    descem atrás dos lados da nuca até a altura dos ombros (atrás dos ombros, fora do caminho dos braços)."""
    h = Vox()
    top = max(k[1] for k in v.v if abs(k[0] + 0.5) <= 3)
    import npcs_mundo_e as M
    for layer in M.shell(v, lambda x, y, z: z <= 0 and y >= NECK + 8 and abs(x + 0.5) <= 6.5, thick=2):
        for q in layer:                                               # cabelo atrás: só no meio da nuca (de frente, o
            h.set(*q, CREAM)                                          # contorno da cabeça continua menta)
    for x0 in range(-5, 6, 3):                                        # borda de baixo do cabelo em pontas
        tri(h, (x0 - 1.4, NECK + 9), (x0 + 1.4, NECK + 9), (x0, NECK + 6.0), -8, -5, CREAM, CREAM_D)
    for (a, b, c) in (((-6, top - 1), (5, top - 1), (4.5, top + 6.5)),      # mecha larga, ponta para +x
                      ((2, top - 1), (8.5, top - 3), (11.5, top + 3.0)),      # segunda mecha, mais deitada
                      ((-6, top - 2), (-1.5, top - 1), (-4.0, top + 3.5))):   # pontinha do outro lado
        tri(h, a, b, c, -3, 4, CREAM, CREAM_D)
    for (x, y) in [(x, y) for x in range(-3, 5) for y in range(NECK + 15, top + 1)]:   # mecha na testa (ponta em V)
        if y >= NECK + 15 + abs(x - 0.5) * 0.9:
            z = front(v, x, y)
            if z is not None:
                h.set(x, y, z + 1, CREAM)
    for sx in (1, -1):                                                # os dois tufos de trás, até os ombros
        b = Vox()
        B.sblob(b, sx * 7.6, NECK + 7.0, -4.4, 3.0, 3.6, 2.8, CREAM, p=2.2)
        B.sblob(b, sx * 9.9, NECK + 2.6, -4.6, 3.1, 4.8, 2.9, CREAM, p=2.2)
        for (a, c2, tip) in (((7.4, NECK - 1.2), (10.6, NECK - 1.2), (8.6, NECK - 5.0)),
                             ((9.8, NECK - 0.6), (12.9, NECK + 0.4), (12.6, NECK - 3.6))):
            tri(b, (sx * a[0], a[1]), (sx * c2[0], c2[1]), (sx * tip[0], tip[1]), -6, -3, CREAM, CREAM_D)
        for k in [k for k in b.v if (abs(k[0] + 0.5) < 6.5 and k[1] < NECK + 2) or k[2] > -2]:
            del b.v[k]
        h.merge(b)
    for k in [k for k in h.v if k in v.v]:
        del h.v[k]
    for (x, y, z) in list(h.v):                                       # dois tons fixos: topo claro, sem sombra rosada
        if (x, y + 1, z) not in h.v and h.v[(x, y, z)] == CREAM:
            h.v[(x, y, z)] = C("#f8e6bd")
    v.merge(h)


def eyes(v, expr):
    """Olho grande de íris escura (preta em cima, roxa embaixo, com a fenda descendo pelo roxo), brilhos brancos no
    alto, e a asinha verde-clara recortada do lado de fora."""
    e0 = NECK + 7
    for (x0, x1, outer, slit) in ((-7, -2, -8, -5), (1, 6, 7, 4)):
        step = -1 if outer < 0 else 1
        for y in range(e0 + 1, e0 + 6):                               # asinha de fora, recortada em pontas
            paint(v, outer, y, EYE_W)
            if y in (e0 + 2, e0 + 4):
                paint(v, outer + step, y, EYE_W)
        if expr == "joy":                                             # olho feliz: arco escuro
            for x in range(x0, x1 + 1):
                paint(v, x, e0 + 3 + (1 if x in (x0 + 1, x1 - 1) else 0) - (1 if x in (x0, x1) else 0), EYE_INK)
                if x not in (x0, x1):
                    paint(v, x, e0 + 4 if x not in (x0 + 1, x1 - 1) else e0 + 5, EYE_INK)
            continue
        top = e0 + (4 if expr == "determined" else 5)
        for x in range(x0, x1 + 1):
            for y in range(e0, top + 1):
                if (x in (x0, x1)) and (y in (e0, top)):
                    continue                                          # cantos arredondados
                col = PUPIL if y >= e0 + 3 else (IRIS_HI if y == e0 + 1 else IRIS_LO)
                if x == slit and y < e0 + 3 and y > e0:
                    col = PUPIL                                       # a fenda desce pelo roxo
                paint(v, x, y, col)
        hx = x0 + 1
        if expr == "surprise":                                        # olhar arregalado: brilho pequeno, íris menor
            paint(v, hx + 1, top - 1, EYE_HI)
            for x in (x0, x1):
                for y in range(e0, top + 1):
                    paint(v, x, y, MINT)
            continue
        for (x, y) in ((hx, top - 1), (hx + 1, top - 1), (hx, top - 2), (hx + 1, top - 2)):   # brilho 2 × 2
            paint(v, x, y, EYE_HI)
        paint(v, x1 - 1, e0 + 1, EYE_HI)                              # brilhinho de baixo, do outro lado
        if expr == "determined":
            for x in range(x0, x1 + 1):
                paint(v, x, top, EYE_INK)


def brows(v, expr):
    lift = {"surprise": 1, "determined": -1}.get(expr, 0)
    y = NECK + 14 + lift
    for (a, b, sh) in ((-5, -4, -6), (3, 4, 5)):
        paint(v, sh, NECK + 13, BROW_SH)                              # sombra verde embaixo e para fora
        for x in (a, b):
            paint(v, x, y, BROW)
            paint(v, x, y + 1, BROW)
    if expr == "determined":                                          # inclinadas para o meio
        paint(v, -3, y, BROW)
        paint(v, 2, y, BROW)


def mouth(v, expr):
    m0 = NECK + 3
    paint(v, -2, NECK + 6, NOSE)                                      # focinho: curvinha e ponto
    paint(v, 1, NECK + 6, NOSE)
    if expr == "surprise":                                            # bocão aberto com língua e uma presa
        for (y, a, b) in ((m0 + 1, -4, 3), (m0, -4, 3), (m0 - 1, -3, 2), (m0 - 2, -2, 1)):
            for x in range(a, b + 1):
                paint(v, x, y, MOUTH)
        for x in (-1, 0, 1):
            paint(v, x, m0 - 1, TONGUE)
        paint(v, 2, m0 + 1, CLAW)
        paint(v, 2, m0, CLAW)
        return
    if expr == "joy":                                                 # boca aberta com as duas presas
        for (y, a, b) in ((m0 + 1, -4, 3), (m0, -4, 3), (m0 - 1, -3, 2)):
            for x in range(a, b + 1):
                paint(v, x, y, MOUTH)
        for x in (-3, 2):
            paint(v, x, m0 + 1, CLAW)
            paint(v, x, m0, CLAW)
        for x in (-1, 0):
            paint(v, x, m0 - 1, TONGUE)
        return
    for (x, y) in ((-3, m0 + 1), (-2, m0), (-1, m0 + 1), (0, m0 + 1), (1, m0), (2, m0 + 1)):   # ":3"
        paint(v, x, y, EYE_INK)
    if expr == "determined":                                          # uma presa à mostra
        paint(v, 1, m0 - 1, CLAW)
        paint(v, 1, m0 - 2, CLAW)


def head(expr, base):
    v = Vox()
    v.merge(base)
    eyes(v, expr)
    brows(v, expr)
    mouth(v, expr)
    return v


def head_base():
    v = skull()
    ring(v)
    hair(v)
    return v


# ----------------------------------------------------------------------------- corpo

def dress_torso(t):
    """Gola alta preta, jardineira roxa: peitilho na frente, costas até a cintura alta, alças com botões, bolso."""
    bib_top = NECK - 6
    for (x, y, z), c in list(t.v.items()):
        ax = abs(x + 0.5)
        if y < HIP:                                              # os shorts são das pernas
            del t.v[(x, y, z)]
            continue
        bib = z >= 1 and ax <= 3.6 and y <= bib_top
        back = z <= -1 and y <= HIP + 5
        waist = y <= HIP + 3
        t.v[(x, y, z)] = PURPLE if (bib or back or waist) else BLACK
    for y in range(HIP + 4, NECK):                               # alças: do peitilho por cima do ombro até as costas
        for x in (-3, 2):
            T._front_paint(t, [(x, y)], PURPLE_D if y > bib_top else PURPLE)
    back_z = {}
    for (x, y, z) in t.v:
        back_z[(x, y)] = min(back_z.get((x, y), 99), z)
    for y in range(HIP + 5, NECK):
        for x in (-3, 2):
            if (x, y) in back_z:
                t.v[(x, y, back_z[(x, y)])] = PURPLE_D
    for x in (-3, 2):                                            # botões claros no alto do peitilho
        T._front_paint(t, [(x, bib_top)], BUTTON, out=1)
    for x in range(-2, 1):                                       # bolso lilás, pequeno
        for y in range(bib_top - 5, bib_top - 2):
            T._front_paint(t, [(x, y)], POCKET)
    for x in range(-3, 3):                                       # gola alta: anel dobrado em volta do pescoço
        for z in range(-3, 4):
            if (x, NECK - 1, z) in t.v and abs(x + 0.5) + abs(z) >= 2.5:
                t.set(x, NECK, z, BLACK)
    L.shade(t, only={PURPLE, BLACK})


def dress_arms(arm, farm):
    """Manga preta até o cotovelo; antebraço e mão de pelo marrom com tufos no punho e nas costas da mão; três garras."""
    for pos in list(arm.v):
        arm.v[pos] = BLACK
    lo = min(k[1] for k in farm.v)
    for (x, y, z) in list(farm.v):
        farm.v[(x, y, z)] = FUR if y < ELBOW - 1 else BLACK
    for yy in (ELBOW - 2, lo + 4):                                    # tufos: punho de pelo e costas da mão
        for (x, y, z) in [k for k in farm.v if k[1] == yy]:
            if (x + z) % 2 == 0:
                for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (x + dx, yy, z + dz)
                    if q not in farm.v:
                        farm.v[q] = FUR_L
                        if (x + dx + z + dz) % 3 == 0 and (x + 2 * dx, yy, z + 2 * dz) not in farm.v:
                            farm.v[(x + 2 * dx, yy, z + 2 * dz)] = FUR_L     # ponta do tufo (encostada)
    hand = [k for k in farm.v if k[1] <= lo + 1]
    if hand:
        zmax = max(k[2] for k in hand)
        front_cells = sorted({k[0] for k in hand if k[2] == zmax})
        for x in front_cells[:3]:                                     # três garras saindo da frente da mão
            farm.v[(x, lo, zmax + 1)] = CLAW
            farm.v[(x, lo + 1, zmax)] = FUR
    L.shade(farm, only={FUR})


def dress_legs(thigh, shin):
    for pos in list(thigh.v):
        thigh.v[pos] = PURPLE
    for (x, y, z) in list(thigh.v):                              # shorts largos: uma camada a mais
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y, z + dz)
            if q not in thigh.v and y >= KNEE + 1:
                thigh.set(*q, PURPLE)
    for (x, y, z) in list(thigh.v):                              # barra dobrada lilás
        if y <= KNEE + 1:
            thigh.v[(x, y, z)] = LILAC
    for pos in list(shin.v):
        shin.v[pos] = MINT
    zmax = max(k[2] for k in shin.v)
    for (x, y, z) in list(shin.v):                               # dedos com garras na ponta do pé
        if y <= 1 and z >= zmax - 1 and (x % 2 == 0):
            shin.v[(x, y, z)] = CLAW
    L.shade(thigh, only={PURPLE})
    L.shade(shin, only={MINT})


def tail():
    """Rabo de lagarto PELUDO: nasce dentro da base das costas (junta firme, acima do topo das coxas), vai para trás e
    desce até perto do chão; tufos pontudos no dorso e na ponta, com as pontas no verde-escuro do colar."""
    t = Vox()
    pts = [(0, HIP + 2.5, -2.6, 1.9), (0, HIP + 2, -5.0, 2.0), (0, HIP, -7.0, 2.0), (0.2, HIP - 4, -8.5, 2.1),
           (0.8, HIP - 7, -11.0, 1.8), (1.8, HIP - 9.5, -13.5, 1.5), (3.2, HIP - 11, -15.5, 1.25), (5.0, HIP - 11.6, -17.0, 1.0)]
    for (a, b) in zip(pts, pts[1:]):
        for i in range(9):
            u = i / 8
            cc = [a[k] + (b[k] - a[k]) * u for k in range(4)]
            B.sblob(t, cc[0], cc[1], cc[2], cc[3], cc[3], cc[3], MINT, p=2.0)
    for (a, b) in zip(pts[2:-1], pts[3:]):                           # tufos no dorso: cunhas para cima e para trás
        mx, my, mz, r = [(a[k] + b[k]) / 2 for k in range(4)]
        for dz in (-0.8, 0.8):
            tuft = Vox()
            tri(tuft, (mz - 1.6, my + r - 0.5), (mz + 1.4, my + r - 0.5), (mz - 2.2, my + r + 2.2), 0, 0, MINT, FUR_G)
            for (z, y, _), col in tuft.v.items():                     # o triângulo vive no plano (z, y)
                t.set(int(round(mx + dz)), y, z, col)
    tip = pts[-1]
    tri_tip = Vox()
    tri(tri_tip, (tip[2] + 0.8, tip[1] - 1.4), (tip[2] + 0.8, tip[1] + 1.4), (tip[2] - 2.6, tip[1] + 0.2), 0, 0,
        MINT, FUR_G)
    for (z, y, _), col in tri_tip.v.items():                          # tufo da ponta
        for xx in (int(tip[0]) - 1, int(tip[0])):
            t.set(xx, y, z, col)
    for (x, y, z) in list(t.v):                                       # barriga do rabo um tom abaixo
        if (x, y - 1, z) not in t.v and y > 3 and t.v[(x, y, z)] == MINT:
            t.v[(x, y, z)] = MINT_D
    L.shade(t, only={MINT})
    return t


# ----------------------------------------------------------------------------- rig

def build_rig():
    s = spec()
    base = head_base()
    heads = {e: head(e, base) for e in ("neutral", "joy", "determined", "surprise")}
    t = T.torso(s)
    dress_torso(t)
    arm_l, arm_r = T.arms(s)
    leg_l, leg_r = T.legs(s)
    ua_l, fa_l = L.split_limb(arm_l, ELBOW, (EL_X, ELBOW, 0.4), 1.5, BLACK)
    ua_r, fa_r = L.split_limb(arm_r, ELBOW, (-EL_X, ELBOW, 0.4), 1.5, BLACK)
    th_l, sh_l = L.split_limb(leg_l, KNEE, (3.1, KNEE, 0.4), 1.7, PURPLE)
    th_r, sh_r = L.split_limb(leg_r, KNEE, (-3.1, KNEE, 0.4), 1.7, PURPLE)
    for (a, f) in ((ua_l, fa_l), (ua_r, fa_r)):
        dress_arms(a, f)
    for (th, sh) in ((th_l, sh_l), (th_r, sh_r)):
        dress_legs(th, sh)
    parts = [("torso", t, (0, HIP, 0.4), None),
             ("head", heads["neutral"], (0, NECK, 0.5), "torso"),
             ("tail", tail(), (0, HIP + 2, -5.0), "torso"),
             ("arm_l", ua_l, (SH_X, SHOULDER, 0.4), "torso"),
             ("farm_l", fa_l, (EL_X, ELBOW, 0.4), "arm_l"),
             ("arm_r", ua_r, (-SH_X, SHOULDER, 0.4), "torso"),
             ("farm_r", fa_r, (-EL_X, ELBOW, 0.4), "arm_r"),
             ("leg_l", th_l, (3.0, HIP, 0.4), None),
             ("shin_l", sh_l, (3.1, KNEE, 0.4), "leg_l"),
             ("leg_r", th_r, (-3.0, HIP, 0.4), None),
             ("shin_r", sh_r, (-3.1, KNEE, 0.4), "leg_r")]
    alts = {"head": {e: heads[e] for e in ("joy", "determined", "surprise")}}
    rig = Rig(s["title"], VS, "biped")
    for (n, vox, piv, par) in parts:
        L.prune_specks(vox, label=f"acapela/{n}")
        for alt in (alts.get(n) or {}).values():
            L.prune_specks(alt, label=f"acapela/{n}:alt")
        rig.part(n, vox, piv, par, alts=alts.get(n))
    head_top = max(k[1] for k in heads["neutral"].v)
    head_h = head_top - NECK + 1
    total = head_top + 1
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "bodyHeightVoxels": total, "legFraction": round(HIP / total, 3),
                "footPlant": True, "joints": True, "ankle": T.ANKLE, "npc": True, "actingStyle": "playful",
                "acting": {"headRoll": 5, "sway": 1.2, "emoteTempo": 0.9}, "role": "morador",
                "weaponMode": "none", "weaponProfile": "npc_acapela",
                "hands": {"r": [-EL_X - 0.3, 15.0, 1.0], "l": [EL_X + 0.3, 15.0, 1.0]},
                "volumes": T.volumes(),
                "note": "Acapela: criaturinha de rabo peludo de lagarto, cabeça redonda com tufinhos, cabelo creme espetado "
                        "com dois tufos até os ombros, gola alta preta, jardineira roxa, patas de pelo marrom"}
    return rig


# cores que vencem na reamostragem, da mais forte para a mais fraca
FEATURES = [EYE_HI, CLAW, PUPIL, BROW, MOUTH, TONGUE, NOSE, EYE_INK, IRIS_HI, IRIS_LO, EYE_W, BROW_SH, BUTTON]


def scale_keep(v, scale):
    """Reamostragem como npcs_oficios_e.scale_vox, mas sem perder traço: quando vários voxels de origem caem no mesmo
    voxel de destino (a 0,8, uma fileira em cinco), vence a cor de TRAÇO do rosto (pupila, brilho, sobrancelha, boca,
    presa, narina, botão); sem traço, vence a cor da maioria. Com a regra de "o último vence", a linha da boca e as
    presas sumiam."""
    votes = {}
    for (x, y, z), c in v.v.items():
        xr = range(round(x * scale), max(round((x + 1) * scale), round(x * scale) + 1))
        yr = range(round(y * scale), max(round((y + 1) * scale), round(y * scale) + 1))
        zr = range(round(z * scale), max(round((z + 1) * scale), round(z * scale) + 1))
        for xx in xr:
            for yy in yr:
                for zz in zr:
                    d = votes.setdefault((xx, yy, zz), {})
                    d[c] = d.get(c, 0) + 1
    out = Vox()
    for k, d in votes.items():
        feat = [c for c in d if c in FEATURES]
        out.v[k] = max(feat, key=lambda c: (-FEATURES.index(c), d[c])) if feat else max(d, key=lambda c: (d[c], c))
    return out


def build_one(out):
    rig = build_rig()
    base_h = rig.all_voxels().bounds()[4] + 1
    scale = round(METERS * O.UNITS_PER_METER / VS) / base_h
    scaled = Rig(rig.name, VS, "biped")
    for n, v, p, par in rig.parts:
        alt = {k: scale_keep(a, scale) for k, a in rig.alts.get(n, {}).items()} or None
        sv = scale_keep(v, scale)
        L.prune_specks(sv, label=f"acapela/{n}")
        scaled.part(n, sv, tuple(q * scale for q in p), par, alts=alt)
    top = scaled.all_voxels().bounds()[4] + 1
    body_top = round(rig.meta["bodyHeightVoxels"] * scale)
    head_h = round(rig.meta["headH"] * scale)
    faces = sum(exposed_faces(v) for _, v, _, _ in scaled.parts)
    scaled.meta = dict(rig.meta, headsTall=round(body_top / head_h, 2), bodyHeightVoxels=body_top, headH=head_h,
                       legH=round(HIP * scale), torsoH=round((NECK - HIP) * scale),
                       legFraction=round(HIP * scale / body_top, 3), faces=faces, metersNarrative=METERS,
                       worldUnits=round(top * VS, 3), escala=round(scale, 4))
    scaled.meta["hands"] = {k: [round(q * scale, 2) for q in c] for k, c in rig.meta["hands"].items()}
    for vol in scaled.meta.get("volumes", []):
        vol["a"] = [round(q * scale, 2) for q in vol["a"]]
        if "b" in vol:
            vol["b"] = [round(q * scale, 2) for q in vol["b"]]
        vol["r"] = round(vol["r"] * scale, 2)
    scaled.save(str(Path(out) / "characters/npc/acapela"))
    print(f"  acapela {top} voxels = {top * VS:.3f} u, {body_top / head_h:.2f} cabeças, {faces} faces, escala {scale:.3f}")
    return scaled


LINEUPS = {
    "npc-acapela-so": ["characters/npc/acapela"],
    "acapela-elenco": ["characters/e/adventurer", "characters/npc/kelyra", "characters/npc/acapela",
                       "characters/npc/pluma", "characters/npc/quelmo"],
}


def build(out):
    build_one(out)
    d = Path(out) / "studies/lineups"
    d.mkdir(parents=True, exist_ok=True)
    for name, dirs in LINEUPS.items():
        (d / f"{name}.json").write_text(json.dumps({"dirs": dirs, "larguraMax": 2.6, "vao": 0.42}, indent=1) + "\n")


if __name__ == "__main__":
    build(Path(__file__).resolve().parents[1] / "assets")
