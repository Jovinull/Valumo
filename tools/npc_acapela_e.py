#!/usr/bin/env python3
"""Acapela (NPC, direção E): uma criaturinha de rabo de lagarto, cabeça grande e franjada, em jardineira.

Mesmo esqueleto dos NPCs da vila (`npcs_vila_e`: quadril 14, pescoço 29, ombros ±5,8 / 26,5) — a locomoção, o aceno
compartilhado (`npc_vila`) e os validadores valem sem mudança. O que é PRÓPRIO dele:

  cabeça    crânio largo (proporção chibi, ~2,2 cabeças), placa do rosto plana; franjas pontudas da cor da pele nas
            bochechas e duas orelhas-franja no alto; topete creme em chama no meio da testa e juba creme fofa atrás;
            olhos grandes de contorno verde-claro e pupila escura vertical; dois pontinhos de sobrancelha; sorriso
            com duas presas
  corpo     gola alta preta de manga comprida; jardineira roxa (peitilho, alças com dois botões claros, bolso lilás,
            shorts com barra dobrada lilás); mãos e antebraços de pelo marrom com garras; pernas e pés menta com dedos
  rabo      parte `tail` (filha do tronco): o Animator já balança o rabo no parado, no andar, na corrida e no salto

Altura narrativa por reamostragem do rig pronto (`npcs_oficios_e.scale_vox`), como os NPCs de ofício.

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
EYE_W = C("#d3f6b9")                            # mancha clara do olho (verde-amarelada)
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
    v = Vox()
    B.sblob(v, 0, NECK + 9.5, 0.0, 8.6, 9.0, 7.6, MINT, p=2.3, p_lo=2.1)
    for k in [k for k in v.v if k[2] > FZ or k[1] < NECK]:
        del v.v[k]
    for x in range(-9, 9):                                       # placa do rosto plana, queixo arredondado
        for y in range(NECK + 1, NECK + 16):
            half = 4.6 if y <= NECK + 1 else 6.0 if y == NECK + 2 else 7.2 if y == NECK + 3 else 8.2
            f = front(v, x, y)
            if f is not None and f < FZ and abs(x + 0.5) <= half:
                for z in range(f, FZ + 1):
                    v.set(x, y, z, MINT)
    L.shade(v)
    return v


def tri(v, a, b, c, z0, z1, col=MINT, tip_col=MINT_D):
    """Triângulo de lado reto (a e b na base, c na ponta) extrudado em z0..z1; o terço da ponta um tom abaixo."""
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


# Franjas do lado +x (o lado −x é o espelho x → −1 − x): (base a, base b, ponta), em voxels do esqueleto NPC
FRILLS = (((3.5, NECK + 18.0), (8.8, NECK + 12.5), (13.0, NECK + 26.0)),    # orelha pontuda
          ((8.8, NECK + 14.0), (8.8, NECK + 7.0), (16.5, NECK + 10.5)),      # tufo do meio
          ((8.6, NECK + 8.5), (5.5, NECK + 2.0), (14.5, NECK + 1.5)))        # bochecha


def frills(v):
    """Contorno em estrela da cor da pele, de lados retos (atrás da placa do rosto, z ≤ 4: não cobrem os olhos)."""
    f = Vox()
    for (a, b, c) in FRILLS:
        tri(f, a, b, c, -2, 4)
        m = lambda p: (-p[0], p[1])                              # espelho em torno de x = 0 (centros de voxel)
        tri(f, m(a), m(b), m(c), -2, 4)
    for k in [k for k in f.v if k in v.v or k[1] < NECK]:
        del f.v[k]
    L.shade(f, only={MINT})
    v.merge(f)


def hair(sk, v):
    """Cabelo creme: cobre a parte de trás do crânio (casca de 3 voxels, de y = pescoço + 4 para cima), com tufos
    descendo na nuca; aparece de frente na borda de cima, atrás da franja em V."""
    import npcs_mundo_e as M
    h = Vox()
    for i, layer in enumerate(M.shell(sk, lambda x, y, z: z <= 1 and y >= NECK + 4, thick=3)):
        for q in layer:
            h.set(*q, CREAM)
    for x0 in range(-8, 8, 3):                                   # tufos da nuca, apontando para baixo
        tri(h, (x0 - 1.0, NECK + 6), (x0 + 2.0, NECK + 6), (x0 + 0.5, NECK + 2.5), -7, -4, CREAM, CREAM_D)
    for k in [k for k in h.v if k in v.v]:
        del h.v[k]
    L.shade(h, only={CREAM})
    v.merge(h, overwrite=False)


def tuft(v):
    """Topete creme: ponta em V na testa e uma chama alta que sobe do alto da cabeça e curva para +x."""
    t = Vox()
    top = max(k[1] for k in v.v if abs(k[0] + 0.5) <= 3 and v.v[k] != CREAM)
    for x in range(-4, 4):                                       # ponta em V na testa
        bottom = NECK + 17 - max(0, 2.5 - abs(x + 0.5))
        for y in range(int(round(bottom)), top + 1):
            z = front(v, x, y)
            if z is not None:
                t.set(x, y, z + 1, CREAM)
    for i in range(10):                                          # chama: base larga, curva para +x no alto
        y = top - 3 + i
        cx = -0.5 + 0.07 * i * i
        hw = 4.8 * (1 - i / 10) ** 0.8 + 0.5
        for x in range(int(cx - hw) - 1, int(cx + hw) + 2):
            if abs(x + 0.5 - cx) <= hw:
                for z in range(-3, 5 - i // 3):
                    t.set(x, y, z, CREAM_D if (i > 7 or abs(x + 0.5 - cx) > hw - 1) else CREAM)
    for k in [k for k in t.v if k in v.v and k[1] < top - 1]:
        del t.v[k]
    for (x, y, z) in list(t.v):                                  # dois tons fixos (a sombra roxa deixava rosado)
        if (x, y + 1, z) not in t.v and t.v[(x, y, z)] == CREAM:
            t.v[(x, y, z)] = C("#f8e6bd")
    v.merge(t)


def eyes(v, expr):
    """Mancha verde-clara grande (6 × 7) com pupila escura de 3 × 5 do lado de dentro e brilho branco no alto."""
    e0 = NECK + 7
    for (x0, x1, px0) in ((-8, -3, -6), (2, 7, 3)):
        joy = expr == "joy"
        for x in range(x0, x1 + 1):
            for y in range(e0, e0 + 7):
                if (x in (x0, x1)) and (y in (e0, e0 + 6)):
                    continue                                     # cantos arredondados
                paint(v, x, y, EYE_W)
        if joy:                                                  # olho feliz: arco escuro, mancha clara em volta
            for x in range(x0 + 1, x1):
                paint(v, x, e0 + 3, EYE_INK)
                paint(v, x, e0 + 4 if x in (x0 + 2, x1 - 2) else e0 + 3, EYE_INK)
            continue
        pw, ph, py0 = {"surprise": (2, 3, e0 + 2), "determined": (2, 4, e0 + 1)}.get(expr, (3, 5, e0 + 1))
        pxs = range(px0 + (1 if pw == 2 and px0 < 0 else 0), px0 + (1 if pw == 2 and px0 < 0 else 0) + pw)
        for x in pxs:
            for y in range(py0, py0 + ph):
                paint(v, x, y, PUPIL)
        hx = min(pxs)
        paint(v, hx, py0 + ph - 1, EYE_HI)                       # brilho: canto de cima, lado da luz
        if ph >= 4:
            paint(v, hx, py0 + ph - 2, EYE_HI)
        for x in range(x0 + 1, x1):                              # traço de cima
            paint(v, x, e0 + 6, EYE_INK)
        if expr == "determined":                                 # pálpebra baixa
            for x in range(x0, x1 + 1):
                paint(v, x, e0 + 5, EYE_INK)


def brows(v, expr):
    lift = {"surprise": 1, "determined": -1}.get(expr, 0)
    y = NECK + 15 + lift
    for (a, b) in ((-6, -5), (4, 5)):
        for x in (a, b):
            paint(v, x, y, BROW)
            paint(v, x, y + 1, BROW)
    if expr == "determined":                                     # inclinadas para o meio
        paint(v, -4, y, BROW)
        paint(v, 3, y, BROW)


def mouth(v, expr):
    m0 = NECK + 3
    for x in (-1, 0):                                            # narinas
        paint(v, x, NECK + 6, NOSE)
    if expr == "surprise":                                       # "o" redondo: 4 × 4 sem os cantos
        for (y, a, b) in ((m0 + 2, -1, 0), (m0 + 1, -2, 1), (m0, -2, 1), (m0 - 1, -1, 0)):
            for x in range(a, b + 1):
                paint(v, x, y, MOUTH)
        return
    if expr == "joy":                                            # boca aberta com as duas presas
        for (y, a, b) in ((m0 + 2, -4, 3), (m0 + 1, -4, 3), (m0, -3, 2), (m0 - 1, -2, 1)):
            for x in range(a, b + 1):
                paint(v, x, y, MOUTH)
        for x in (-3, 2):
            paint(v, x, m0 + 2, CLAW)
            paint(v, x, m0 + 1, CLAW)
        for x in (-1, 0):                                        # língua
            paint(v, x, m0 - 1, TONGUE)
        return
    if expr == "determined":                                     # boca fechada, uma presa à mostra
        for x in range(-3, 3):
            paint(v, x, m0 + 1, MOUTH)
        paint(v, 1, m0, CLAW)
        paint(v, 1, m0 - 1, CLAW)
        return
    for (y, a, b) in ((m0 + 2, -3, 2), (m0 + 1, -3, 2), (m0, -2, 1)):   # sorriso aberto pequeno
        for x in range(a, b + 1):
            paint(v, x, y, MOUTH)
    for x in (-2, 1):                                            # as duas presas, nos cantos de cima
        paint(v, x, m0 + 2, CLAW)
        paint(v, x, m0 + 1, CLAW)


def head(expr, base):
    v = Vox()
    v.merge(base)
    eyes(v, expr)
    brows(v, expr)
    mouth(v, expr)
    return v


def head_base():
    sk = skull()
    v = Vox()
    v.merge(sk)
    frills(v)
    hair(sk, v)
    tuft(v)
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
    for pos in list(arm.v):
        arm.v[pos] = BLACK
    lo = min(k[1] for k in farm.v)
    for (x, y, z) in list(farm.v):
        farm.v[(x, y, z)] = FUR if y < ELBOW - 1 else BLACK
    for (x, y, z) in [k for k in farm.v if k[1] == ELBOW - 2]:  # punho de pelo em tufos, logo abaixo do cotovelo
        if (x + z) % 2 == 0:
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, ELBOW - 2, z + dz)
                if q not in farm.v:
                    farm.v[q] = FUR_L
    hand = [k for k in farm.v if k[1] <= lo + 1]
    if hand:
        zmax = max(k[2] for k in hand)
        for (x, y, z) in hand:                                   # garras: as pontas da frente da mão
            if z >= zmax - 1 and y == lo:
                farm.v[(x, y, z)] = CLAW
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
    """Rabo de lagarto: nasce DENTRO da base das costas (junta firme, acima do topo das coxas), vai para trás e desce
    até perto do chão; a ponta curva para o lado."""
    t = Vox()
    pts = [(0, HIP + 2.5, -2.6, 1.9), (0, HIP + 2, -5.0, 2.0), (0, HIP, -7.0, 2.0), (0.2, HIP - 4, -8.5, 2.1), (0.8, HIP - 7, -11.0, 1.8),
           (1.8, HIP - 9.5, -13.5, 1.5), (3.2, HIP - 11, -15.5, 1.25), (5.0, HIP - 11.6, -17.0, 1.0)]
    for (a, b) in zip(pts, pts[1:]):
        for i in range(9):
            u = i / 8
            c = [a[k] + (b[k] - a[k]) * u for k in range(4)]
            B.sblob(t, c[0], c[1], c[2], c[3], c[3], c[3], MINT, p=2.0)
    for (x, y, z) in list(t.v):                                  # barriga do rabo um tom abaixo
        if (x, y - 1, z) not in t.v and y > 3:
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
                "note": "Acapela: criaturinha de rabo de lagarto, cabeça grande e franjada, topete creme, gola alta preta, "
                        "jardineira roxa, mãos de pelo marrom"}
    return rig


# cores que vencem na reamostragem, da mais forte para a mais fraca
FEATURES = [EYE_HI, CLAW, PUPIL, BROW, MOUTH, TONGUE, NOSE, EYE_INK, BUTTON]


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
