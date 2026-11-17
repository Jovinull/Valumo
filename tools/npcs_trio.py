"""Direção E — Fábula Voxel: trio de NPCs da vila (Kelyra, Índira e Súvia).

Um construtor só, três fichas (`SPECS`): o esqueleto é o mesmo NPC da cartógrafa (`npcs_e`: quadril 14,
pescoço 29, crânio 14 × 14, voxel 1/40 u), então locomoção, IK e validação funcionam sem mudar o Animator.
O que separa as três NÃO é só cor (regra da rodada 06 "nenhuma recoloração"):

  kelyra — cabelo longo liso escuro até a cintura, franja cheia; corpete ameixa com gola em V sobre a blusa,
           broche de estrela; casaco de abas longas (silhueta em A). Atuação: confiante.
  indira — cabelo claro com franja e uma mecha caída à frente do ombro, fita índigo na coroa, sardas;
           blusa índigo fechada de gola alta, faixa de tecido na cintura, SEM abas (silhueta reta).
           Atuação: doce.
  suvia  — cabelo escuro com pontas acobreadas, orelhas de raposa (partes `ear_l`/`ear_r`, filhas da cabeça,
           fora da medida de cabeças); corpete escuro de gola redonda, pingente de coração, abas curtas com
           barra cobre. Atuação: travessa.

Corpo na linguagem dos NPCs da E: tronco em massa única arredondada, sem volume de busto e sem decote
profundo (a gola mostra no máximo dois voxels de pele junto ao pescoço). Rosto em placa plana, sem nariz,
brilho do olho do lado da luz (coluna de menor x), blush; olho amendoado com delineado (4 de largura,
diferente do olho 3 × 5 dos heróis e do 2 × 3 da cartógrafa). Expressões com atuação própria (`acting`).
"""
import math

import charlib_e as L
import chars_b as B
import chars_d as D
from charlib_e import BLUSH, EYE_HI, TEETH, decal, shade
from npcs_e import (ANKLE, EL_X, ELBOW, EYE_Y, FACE_Z, HIP, KNEE, NECK, SH_X, SHOULDER, VS)
from voxlib import C, Rig, Vox, exposed_faces

P = B.P

SPECS = {
    "kelyra": dict(
        title="Kelyra (NPC, E)",
        skin=P("#d7a18d", ks=0.55), hair=P("#302732", kl=0.75), tip=None,
        coat=P("#513b62"), coat_d=P("#352943"), shirt=P("#d6b0bd"), vest=P("#9c405b"),
        pants=P("#493b55"), boot=P("#342d3c"), cuff=P("#79516a"),
        iris=C("#4c342e"), ink=C("#30232c"), lip=C("#8f3d54"), lip_d=C("#672b43"),
        collar="v", belt="buckle", charm="gem", tails="long", hair_style="sweep", hair_bottom=16, crown=1.25, flare=0.0, fringe="straight", front_bottom=16, act_style="confident",
        freckles=False, ears=False, ribbon=None, role="estalajadeira",
        acting={"headPitch": -3, "torsoPitch": -2, "hipRoll": 3, "sway": 0.8, "emoteTempo": 0.9},
        note="Kelyra, estalajadeira: cabelo longo liso com franja, mecha varrida à frente do ombro esquerdo, ponta em V; corpete ameixa, avental, molho de chaves"),
    "indira": dict(
        title="Índira (NPC, E)",
        skin=P("#ead0b8", ks=0.45), hair=P("#b68f67", kl=0.9), tip=None,
        coat=P("#46536e"), coat_d=P("#303c57"), shirt=P("#e8dcc2"), vest=P("#3b4a66"),
        pants=P("#63718a"), boot=P("#5a4636"), cuff=P("#8f7356"), sleeve=P("#e8dcc2"), puff=3.2,
        iris=C("#5a8f9d"), ink=C("#3a2e2a"), lip=C("#b96d72"), lip_d=C("#8d5260"),
        collar="pinafore", belt="apron", charm=None, tails=None, hair_style="braid", hair_bottom=NECK - 3, crown=1.3, flare=0.0, fringe="curtain", front_bottom=NECK, act_style="sweet",
        freckles=True, ears=False, ribbon=C("#7a5f93"), role="herbalista",
        acting={"headPitch": 4, "headRoll": 6, "torsoPitch": 2, "sway": 0.7, "emoteTempo": 1.25},
        note="Índira, herbalista: cabelo claro rente com franja em cortina e trança sobre o ombro direito; blusa creme de mangas bufantes, jardineira índigo, avental de sálvia, bolsa de ervas"),
    "suvia": dict(
        title="Súvia (NPC, E)",
        skin=P("#e5bca4", ks=0.5), hair=P("#302630", kl=0.75), tip=P("#a65739", kl=0.8),
        coat=P("#5a5470"), coat_d=P("#3f3a52"), shirt=P("#e0b896"), vest=P("#6e2e42"),
        pants=P("#4e3941"), boot=P("#3a2e33"), cuff=P("#b0643f"),
        iris=C("#789aaa"), ink=C("#2c2229"), lip=C("#ac5a60"), lip_d=C("#79404e"),
        collar="round", belt="copper", charm="heart", tails="short", hair_style="long", hair_bottom=21, crown=2.0, flare=1.7, fringe="swept", front_bottom=17, act_style="playful",
        freckles=False, ears=True, ribbon=None, role="mensageira",
        acting={"headRoll": -5, "hipRoll": -4, "sway": 1.4, "emoteTempo": 0.8},
        note="Súvia, mensageira: pontas acobreadas, orelhas de raposa, corpete vinho, mangas ardósia, bolsa de cartas"),
}

BRASS = C("#d6a64a", "metal")
BRASS_D = C("#a77a34", "metal")
COPPER = C("#c7774a", "metal")
COPPER_D = C("#94532f", "metal")
STEEL = C("#b9bec6", "metal")


def _front_paint(t, cells, c, out=0):
    """Pinta (out = 0) ou ressalta (out = 1) a superfície frontal do tronco nas células (x, y)."""
    for (x, y) in cells:
        zf = L.front_z(t, x, y)
        if zf is not None:
            t.set(x, y, zf + out, c)


def role_props(s, t):
    """Objeto do papel na vila (rodada 06.1), preso ao tronco: avental + chaves (estalajadeira), fita métrica
    + tesoura (tecelã), avental de sálvia + bolsa de ervas (herbalista), bolsa de cartas (mensageira)."""
    r = s["role"]
    if r in ("estalajadeira", "herbalista"):             # avental: painel claro da cintura à barra
        ap = P("#ebe1cc") if r == "estalajadeira" else P("#93ab78")
        _front_paint(t, [(x, y) for y in range(10, HIP + 3) for x in range(-3, 3)], ap)
        _front_paint(t, [(x, 10) for x in range(-3, 3)], B.shadow(ap, 1.3))
        if r == "herbalista":                             # peitilho do avental sobre a jardineira
            _front_paint(t, [(x, y) for y in range(HIP + 3, NECK - 6) for x in range(-2, 2)], ap)
    if r == "estalajadeira":                             # molho de chaves pendurado no quadril esquerdo
        for (x, y, z, c) in ((5, HIP, 2, BRASS), (5, HIP - 1, 2, BRASS_D), (6, HIP - 1, 2, BRASS),
                             (5, HIP - 2, 3, BRASS), (6, HIP - 3, 3, BRASS_D), (5, HIP - 3, 2, BRASS_D),
                             (6, HIP - 2, 2, BRASS)):
            t.set(x, y, z, c)
    elif r == "tecela":                                  # fita métrica no pescoço + tesoura no cinto
        tape, tick = C("#e2c25a"), C("#6b5a2a")
        for x in (-3, 2):
            _front_paint(t, [(x, y) for y in range(19, NECK - 1)], tape, out=1)
            _front_paint(t, [(x, y) for y in range(20, NECK - 1, 2)], tick, out=1)
        for (x, y) in ((3, HIP), (3, HIP - 1), (4, HIP - 2), (2, HIP - 2)):
            _front_paint(t, [(x, y)], STEEL, out=1)
    elif r == "herbalista":                              # bolsa de ervas no quadril direito com folhas
        bag, bag_d, leaf, leaf_l = P("#8a6240"), P("#6a4a30"), C("#5f8f45"), C("#8cbf5e")
        for x in range(-8, -5):
            for y in range(10, 15):
                for z in range(-1, 3):
                    t.set(x, y, z, bag_d if y == 10 or z == -1 else bag)
        for x in range(-8, -5):
            t.set(x, 14, 3, bag_d)                      # aba
        for (x, y, z, c) in ((-7, 15, 1, leaf), (-7, 16, 1, leaf_l), (-6, 15, 0, leaf_l), (-8, 15, 0, leaf), (-8, 16, -1, leaf_l)):
            t.set(x, y, z, c)
    elif r == "mensageira":                              # bolsa de cartas no quadril esquerdo, aba de cobre
        bag, bag_d, letter = P("#9a6a45"), P("#6f4a30"), C("#efe3c8")
        for x in range(5, 8):
            for y in range(10, 14):
                for z in range(-1, 3):
                    t.set(x, y, z, bag_d if y == 10 or x == 7 else bag)
        for x in range(5, 8):
            t.set(x, 13, 3, COPPER)
            t.set(x, 12, 3, COPPER_D)
        for (x, y, z) in ((5, 14, 0), (6, 14, 0), (6, 15, 0), (6, 14, 1)):
            t.set(x, y, z, letter)


# ----------------------------------------------------------------------------- corpo

def torso(s):
    t = Vox()
    coat, coat_d, vest, skin, shirt = s["coat"], s["coat_d"], s["vest"], s["skin"], s["shirt"]

    def prof(y):
        if y <= HIP + 2:                                 # linha A da cintura até a barra
            k = (HIP + 2 - y) / 6.0
            return (0, 0.4, 4.7 + 1.9 * k, 3.5 + 1.2 * k)
        u = (y - HIP - 2) / (NECK - 3 - HIP)
        rx = 4.7 + 0.6 * math.sin(math.pi * min(1.0, u * 0.9)) - 0.7 * max(0.0, u - 0.8) / 0.2
        return (0, 0.4 + 0.25 * math.sin(math.pi * min(1.0, u * 1.2)), rx, 3.5 + 0.4 * math.sin(math.pi * min(1.0, u)))

    B.stack(t, 10, NECK - 1, prof, coat, p=2.7)
    # corpete/blusa na frente (painel central); gola por personagem, rasa
    for (x, y, z) in list(t.v):
        if z < 2 or y < HIP - 1:
            continue
        ax = abs(x + 0.5)
        if ax <= 4.2:
            t.v[(x, y, z)] = vest
        if y <= 12 and ax < 1.4:
            del t.v[(x, y, z)]                            # fenda da barra entre as pernas
    col = s["collar"]
    if col == "v":                                       # V raso: blusa clara, pele só junto ao pescoço
        for y in range(NECK - 5, NECK - 1):
            half = (y - (NECK - 6)) * 0.55
            for x in range(-4, 4):
                if abs(x + 0.5) <= half:
                    zf = L.front_z(t, x, y)
                    if zf is not None:
                        t.set(x, y, zf, skin if y >= NECK - 3 and abs(x + 0.5) <= half - 0.6 else shirt)
    elif col == "round":                                 # gola redonda rasa com debrum claro
        for (y, half) in ((NECK - 3, 1.6), (NECK - 2, 2.6)):
            for x in range(-4, 4):
                ax = abs(x + 0.5)
                if ax <= half:
                    zf = L.front_z(t, x, y)
                    if zf is not None:
                        t.set(x, y, zf, shirt if ax > half - 1.0 or y == NECK - 3 else skin)
    elif col == "pinafore":                              # jardineira sobre blusa: peito alto e alças em creme
        for (x, y, z) in list(t.v):
            if z >= 1 and y >= NECK - 5 and (abs(x + 0.5) > 2.6 or y >= NECK - 2):
                t.v[(x, y, z)] = shirt
            if z >= 1 and y >= NECK - 5 and 2.6 < abs(x + 0.5) <= 3.4 and y < NECK - 1:
                t.v[(x, y, z)] = vest                    # alças
        for x in range(-3, 3):
            zf = L.front_z(t, x, NECK - 1)
            if zf is not None:
                t.set(x, NECK - 1, zf + 1, shirt)
    else:                                                # gola alta fechada
        for x in range(-3, 3):
            zf = L.front_z(t, x, NECK - 1)
            if zf is not None:
                t.set(x, NECK - 1, zf + 1, shirt)
    shade(t)
    for k in [k for k in t.v if k[1] == 10]:
        t.v[k] = coat_d
    # cintura
    belt = s["belt"]
    for k in [k for k in t.v if k[1] in (HIP + 1, HIP + 2)]:
        t.v[k] = L.LEATHER_D if belt in ("buckle", "copper") else coat_d if belt == "sash" else vest
    zf = L.front_z(t, 0, HIP + 1)
    if belt in ("buckle", "copper"):
        m1, m2 = (BRASS, BRASS_D) if belt == "buckle" else (COPPER, COPPER_D)
        for (x, y, c) in ((-1, HIP + 1, m1), (0, HIP + 1, m1), (-1, HIP + 2, m2), (0, HIP + 2, m2)):
            t.set(x, y, zf + 1, c)
    elif belt == "sash":                                 # faixa: laço caído do lado esquerdo
        for (x, y) in ((3, HIP + 1), (3, HIP), (4, HIP), (3, HIP - 1)):
            z = L.front_z(t, x, HIP + 1)
            t.set(x, y, z + 1, s["vest"])
    role_props(s, t)
    # adorno no peito
    metal = C("#d5a75b", "metal")
    if s["charm"] == "gem":                              # broche: gema 2 × 2 em aro de metal
        gem = C("#c8607e", "metal")
        for (x, y, c) in ((-4, NECK - 6, metal), (-3, NECK - 6, gem), (-4, NECK - 7, gem), (-3, NECK - 7, metal)):
            zf = L.front_z(t, x, y)
            if zf is not None:
                t.set(x, y, zf + 1, c)
    elif s["charm"] == "heart":                          # coração: 2 lóbulos + ponta
        heart = C("#c7614f", "metal")
        y0 = NECK - 9                                    # .X.X. / XXXXX / .XXX. / ..X.. (centro em x = −1)
        cells = [(-2, y0 + 3), (0, y0 + 3)] + [(x, y0 + 2) for x in range(-3, 2)] + [(x, y0 + 1) for x in (-2, -1, 0)] + [(-1, y0)]
        for (x, y) in cells:
            zf = L.front_z(t, x, y)
            if zf is not None:
                t.set(x, y, zf + 1, heart)
    return t


def tails(s):
    """Abas atrás das pernas (skirt_l/skirt_r): seguem 78% da perna do mesmo lado (Animator)."""
    if not s["tails"]:
        return None, None
    out = []
    long_ = s["tails"] == "long"
    y_lo = 3 if long_ else 8
    fl_x = 3.8 if long_ else 1.4                         # 06.1: abas longas abrem em sino (silhueta de frente)
    for side in (1, -1):
        v = Vox()
        for y in range(y_lo, 13):
            k = (13 - y) / float(13 - y_lo)
            for x in range(0, 8):
                xx = x if side > 0 else -1 - x
                if x > 4.4 + fl_x * k:
                    continue
                for z in range(-7, 0):
                    if z < -3 - int((3.2 if long_ else 1.8) * k) or (x > 3.6 + 1.2 * k and z > -2 - int(1.6 * k * long_)):
                        continue
                    hem = y <= y_lo + (1 if long_ else 0)
                    v.set(xx, y, z, (s["tip"] or s["coat_d"]) if (hem and not long_) else s["coat_d"] if hem else s["coat"])
        for k2 in [k2 for k2 in v.v if abs(k2[0] + 0.5) < 0.6 + (13 - k2[1]) * 0.12]:
            del v.v[k2]
        shade(v)
        out.append(v)
    return out[0], out[1]


def arms(s):
    a = Vox()
    sl, pf = s.get("sleeve", s["coat"]), s.get("puff", 2.3)
    B.sblob(a, SH_X + 0.3 + (pf - 2.3) * 0.3, SHOULDER - 0.6, 0.4, pf, 2.3 + (pf - 2.3) * 0.5, pf + 0.1, sl, p=2.3)
    B.stack(a, ELBOW, int(SHOULDER) - 1, lambda y: (EL_X - 0.2 + 0.1 * (y - ELBOW) / 6, 0.4, 1.8, 1.9), sl, p=2.4)
    B.stack(a, ELBOW - 2, ELBOW - 1, lambda y: (EL_X, 0.4, 2.2, 2.3), s["shirt"], p=2.5)   # punho dobrado
    B.stack(a, 16, ELBOW - 3, lambda y: (EL_X + 0.1, 0.5, 1.7, 1.8), s["skin"], p=2.5)
    B.sblob(a, EL_X + 0.3, 15.0, 0.9, 2.1, 2.1, 2.2, s["skin"], p=2.6)
    a.set(EL_X - 2, 15, 3, s["skin"])                     # polegar
    for k in [k for k in a.v if k[0] < 4]:
        del a.v[k]
    shade(a)
    return a, a.flipped_x()


def legs(s):
    l = Vox()
    cx, cz = 3.0, 0.4
    B.stack(l, 9, HIP - 1, lambda y: (cx, cz, 2.0, 2.1), s["pants"], p=2.6)
    B.stack(l, 2, 8, lambda y: (cx + 0.1, cz, 2.3, 2.4), s["boot"], p=2.8)
    B.sblob(l, cx + 0.2, 1.4, cz + 1.3, 2.6, 2.0, 3.8, s["boot"], p=2.6)
    for y in (8, 9):
        L.ring(l, y, y, 2.7, 2.8, s["cuff"], cz=cz, cx=cx + 0.1, p=2.8)
    for k in [k for k in l.v if k[1] < 0 or k[0] < 1]:
        del l.v[k]
    for k in [k for k in l.v if k[1] == 0]:
        l.v[k] = L.LEATHER_D
    shade(l)
    return l, l.flipped_x()


# ----------------------------------------------------------------------------- cabeça

def jaw_half(y):
    """Meia-largura da placa do rosto por linha: afina nas duas linhas do queixo (rodada 06.1). Abaixo disso
    fica a curva do próprio crânio, então o contorno de frente arredonda sem mudar a gramática da placa."""
    return 3.6 if y <= NECK + 1 else 4.6 if y == NECK + 2 else 5.6


def skull(s):
    v = Vox()
    B.sblob(v, 0, NECK + 7.3, 0.5, 7.2, 7.3, 6.5, s["skin"], p=2.4, p_lo=2.3)
    for k in [k for k in v.v if k[2] > FACE_Z]:
        del v.v[k]
    for x in range(-6, 6):
        for y in range(NECK + 1, NECK + 12):
            f = L.front_z(v, x, y)
            if f is not None and 2 <= f < FACE_Z and abs(x + 0.5) <= jaw_half(y):
                for z in range(f, FACE_Z + 1):
                    v.set(x, y, z, s["skin"])
    for ex in (7, -8):
        v.box(ex, NECK + 4, -1, ex, NECK + 6, 0, s["skin"])
    shade(v, hi=0.62, lo=-0.40, low2=-0.9)
    return v


def fringe_y(s, x):
    """Linha de baixo da franja na coluna x (uma linha de testa acima da sobrancelha; nas laterais desce).
    straight = reta e cheia; curtain = aberta em cortina no meio; swept = varrida de lado (alta à esquerda
    do personagem, baixa à direita)."""
    ax = abs(x + 0.5)
    if ax >= 4:
        return NECK + 7
    st = s["fringe"]
    if st == "curtain":
        return NECK + (12 if ax < 1 else 11)
    if st == "swept":
        return NECK + 10 + (1 if x >= -1 else 0) + (1 if x >= 2 else 0)
    return NECK + 11


def hair(s, sk):
    col = s["hair"]
    h = Vox()
    for (x, y, z) in D.dilate(sk, s["crown"]):
        if (x, y, z) in sk.v or y < NECK:
            continue
        if z < 2 or y >= fringe_y(s, x):
            h.set(x, y, z, col)
    style = s["hair_style"]
    top, bot, fl = NECK + 9, s["hair_bottom"], s["flare"]       # topo dentro do casco: o domo da massa pegava luz e virava um "+" na nuca

    def flared(y, cx, cz, rx):                           # abre para fora e para trás perto das pontas
        k = ((top - y) / max(1, top - bot)) ** 2
        return (cx + (0.45 * fl * k if cx > 0 else -0.45 * fl * k if cx < 0 else 0), cz - 0.5 * fl * k, rx + 0.6 * fl * k, 2.1 + 0.3 * fl * k)
    back = Vox()
    for cx, cz, rx in [(-4.8, -4.4, 2.7), (0, -5.2, 3.2), (4.8, -4.4, 2.7)]:
        B.stack(back, bot, top, lambda y, cx=cx, cz=cz, rx=rx: flared(y, cx, cz, rx), col, p=2.7)
    if style == "sweep":                                 # ponta em V: o meio das costas desce mais que as laterais
        for k in [k for k in back.v if k[1] < bot + 1.15 * abs(k[0] + 0.5)]:
            del back.v[k]
    elif style == "braid":                               # nuca arredondada: cantos de baixo recolhidos (não fecha em caixa)
        for k in [k for k in back.v if k[1] < bot + 3 and abs(k[0] + 0.5) > 5.6 - 1.2 * (bot + 3 - k[1])]:
            del back.v[k]
    h.merge(back)
    if style == "long":                                  # mechas da frente nos dois lados (Súvia)
        for cx, cz, rx in ((-5.8, 2.0, 1.35), (5.7, 1.7, 1.45)):
            B.stack(h, s["front_bottom"], top, lambda y, cx=cx, cz=cz, rx=rx: (cx, cz, rx, 2.1), col, p=2.7)
    locks = [((-5, NECK + 14, 1), (-3, NECK + 12, 5), (-1, NECK + 9, 6), 1.8, 1.1),
             ((4, NECK + 14, 1), (3, NECK + 12, 5), (1, NECK + 10, 6), 1.8, 1.1)]
    if style == "long":
        locks += [((-6, NECK + 7, -2), (-7, NECK + 1, 0), (-6, s["front_bottom"] + 1, 1), 2.1, 1.3),
                  ((6, NECK + 7, -2), (7, NECK + 1, 0), (5, s["front_bottom"] + 1, 1), 2.1, 1.3)]
    elif style == "sweep":                               # só o lado esquerdo desce à frente (o resto vai para trás do ombro)
        locks += [((6, NECK + 7, -2), (7, NECK + 2, 0), (5, NECK - 1, 1.4), 2.1, 1.7),
                  ((-6, NECK + 7, -2), (-7, NECK + 2, -2), (-6, NECK - 1, -3), 2.0, 1.6)]
    elif style == "braid":                               # mechas curtas emoldurando o rosto até o queixo + raiz da trança
        locks += [((-6, NECK + 8, -1), (-7, NECK + 3, 1), (-6, NECK + 1, 2), 1.6, 1.1),
                  ((6, NECK + 8, -1), (7, NECK + 3, 1), (6, NECK + 1, 2), 1.6, 1.1),
                  ((-4, NECK + 3, -4), (-5, NECK + 1, -2), (-4.5, NECK - 1, 1.0), 1.7, 1.5)]
    L.hair_locks(h, col, locks)
    for k in [k for k in h.v if k in sk.v or (k[2] >= FACE_Z and NECK + 2 <= k[1] < fringe_y(s, k[0]) and -4 <= k[0] <= 3)]:
        del h.v[k]
    for x in range(-6, 6):                               # franja cheia: tapa os furos do casco dilatado
        for y in range(fringe_y(s, x), NECK + 14):
            f = L.front_z(sk, x, y)
            if f is not None and not any((x, y, z) in h.v for z in range(f + 1, f + 3)):
                h.set(x, y, f + 1, col)
    shade(h, hi=0.48, lo=-0.23, low2=-1.5)
    lum = lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]
    for (x, y, z), c in list(h.v.items()):                # nuca: o casco entre as massas pegava brilho ("+")
        if z <= -3 and NECK <= y <= NECK + 9 and abs(x + 0.5) <= 4 and lum(c) > lum(col) * 1.04:
            h.v[(x, y, z)] = col
    groove = B.RAMPS[col][0]
    for (x, y, z), c in list(h.v.items()):
        if z <= -5 and bot + 1 <= y <= NECK + 8 and x in (-4, 2) and c in (col, B.RAMPS[col][1]):
            h.v[(x, y, z)] = groove
    if s["tip"]:                                         # pontas acobreadas abaixo do queixo
        tip = s["tip"]
        for (x, y, z), c in list(h.v.items()):
            if y <= min(bot, s["front_bottom"]) + 3 + (1 if (x + z) % 3 == 0 else 0) or (y <= bot + 3 and z < 0):
                for i, rc in enumerate(B.RAMPS[col]):
                    if c == rc:
                        h.v[(x, y, z)] = B.RAMPS[tip][min(i, len(B.RAMPS[tip]) - 1)]
                if c == col:
                    h.v[(x, y, z)] = tip
    if s["ribbon"] and style != "braid":
        for x in range(-3, 3):
            if (x, NECK + 14, 4) in h.v:
                h.v[(x, NECK + 14, 4)] = s["ribbon"]
    if s["role"] == "herbalista":                        # raminho de ervas atrás da orelha esquerda (sai do contorno)
        stem, leaf, flower = C("#5f7f3e"), C("#86b55a"), C("#9c86c9")
        for (x, y, z, c) in ((7, NECK + 9, -1, stem), (8, NECK + 10, -1, stem), (8, NECK + 11, -2, leaf),
                             (9, NECK + 11, -1, leaf), (9, NECK + 12, -2, stem), (10, NECK + 13, -2, flower),
                             (9, NECK + 13, -3, flower), (10, NECK + 14, -3, flower), (7, NECK + 11, 0, leaf),
                             (8, NECK + 12, 0, leaf)):
            h.v[(x, y, z)] = c
    return h


def hair_lock(s):
    """Mecha longa presa ao TRONCO (parte `lock`, rodada 06.1): fica parada em relação ao peito, então não o
    atravessa quando a cabeça gira; o topo some sob o cabelo da cabeça. sweep = mecha varrida à frente do
    ombro esquerdo, afinando até a cintura; braid = trança de gomos alternados sobre o ombro direito com fita."""
    col, style = s["hair"], s["hair_style"]
    v = Vox()
    if style == "sweep":
        L.hair_locks(v, col, [((4.8, NECK - 1, 1.6), (5.0, 23, 3.0), (4.3, 17, 3.6), 1.9, 0.9)])
        piv = (4.8, NECK - 1, 1.6)
    elif style == "braid":
        lt = B.shadow(col, 1.25)                         # gomos alternados claro/escuro: a trança lê contra a blusa creme
        pts = [(-3.6, NECK - 1, 2.6), (-3.5, 26.2, 3.8), (-3.4, 24.4, 4.5), (-3.3, 22.6, 4.8), (-3.3, 20.8, 4.8),
               (-3.3, 19.0, 4.6)]
        for i, (x, y, z) in enumerate(pts):
            r = 1.55 - 0.08 * i
            B.sblob(v, x + (0.35 if i % 2 else -0.35), y, z, r, 1.05, r, lt if i % 2 else col, p=2.2)
        for x in (-4, -3):
            v.set(x, 17, 5, s["ribbon"])                 # fita
            v.set(x, 17, 4, s["ribbon"])
        B.sblob(v, -3.4, 15.4, 4.6, 1.2, 1.3, 1.1, col, p=2.0)   # ponta solta
        piv = (-3.6, NECK - 1, 2.6)
    else:
        return None, None
    shade(v, hi=0.48, lo=-0.23, low2=-1.5)
    if style == "braid":
        for x in (-4, -3):
            v.v[(x, 17, 5)] = s["ribbon"]
    return v, piv


def fox_ears(s):
    """Orelhas de fantasia: base larga, ponta suave, interior claro (acessório articulado na cabeça)."""
    rim, fur, inner = C("#754135"), C("#edc7aa"), C("#d99286")
    body = s["tip"] or s["hair"]
    rows = {43: (-8, -3), 44: (-9, -3), 45: (-9, -4), 46: (-9, -5), 47: (-9, -6), 48: (-9, -7), 49: (-9, -8), 50: (-9, -9)}
    ears = []
    for side in (1, -1):
        ear = Vox()
        for y, (lo, hi) in rows.items():
            for x0 in range(lo, hi + 1):
                x = x0 if side == 1 else -1 - x0
                front = 2 if y <= 46 else 1
                back = -2 if y <= 45 else -1
                for z in range(back, front + 1):
                    ear.set(x, y, z, rim if (x0 in (lo, hi) or y >= 49) else body)
                if y in (44, 45, 46, 47) and lo + 1 < x0 < hi:
                    ear.set(x, y, front, fur if y >= 46 else inner)
        for x0 in (-6, -5):
            ear.set(x0 if side == 1 else -1 - x0, 43, 3, fur)
        ears.append(ear)
    return ears


def face(s, v, expr):
    """Olho amendoado 4 × 2 com delineado (ponta externa erguida), sobrancelha de 2, boca por atuação.
    Olhos: x −5..−2 (direito do personagem) e 1..4 (esquerdo); brilho na coluna de menor x (lado da luz)."""
    ey, my = EYE_Y, NECK + 1
    ink, iris, lip, lip_d = s["ink"], s["iris"], s["lip"], s["lip_d"]
    white = C("#e7d9d3")
    skin_d = B.shadow(s["skin"], 1.2)
    act = s["act_style"]
    eyes = ((-5, -2, -6), (1, 4, 5))                     # (esquerda, direita, ponta externa)

    def open_eye(left, right, outer, tall=False, half=False):
        top = ey + (4 if tall else 3)
        if half:                                         # pálpebra baixa: só a linha de baixo
            for x in range(left, right + 1):
                decal(v, x, ey + 2, ink)
                decal(v, x, ey + 1, ink if x in (left, right) else iris)
            decal(v, left + 1, ey + 1, EYE_HI)
            decal(v, outer, ey + 2, ink)
            return
        for x in range(left, right + 1):
            decal(v, x, top, ink)
            for y in range(ey + 1, top):
                decal(v, x, y, ink if x in (left, right) else iris if x in (left + 1, right - 1) else white)
        if tall:                                         # arregalado: branco embaixo da íris
            for x in range(left + 1, right):
                decal(v, x, ey + 1, white)
        decal(v, left + 1, top - 1, EYE_HI)
        decal(v, outer, top + 1, ink)                    # ponta do delineado

    def closed(left, right, outer, smile=True):          # ∩ fechado (riso) ou ∪
        for x in range(left, right + 1):
            decal(v, x, ey + (2 if smile else 1), ink)
        for x in (left, right):
            decal(v, x, ey + (1 if smile else 2), ink)
        decal(v, outer, ey + 2, ink)

    def brow(left, right, lift=0, tilt=0):
        """tilt > 0: ponta interna desce (bravo/decidido); < 0: sobe (preocupado/encantado)."""
        bc = B.shadow(s["hair"], 1.6)
        inner = right - 1 if left < 0 else left + 1
        for x in range(left + 1, right):
            y = ey + 5 + lift
            if x == inner and tilt < 0:
                y += 1
            decal(v, x, y, bc)
            if x == inner and tilt > 0:                  # ponta interna engrossa para baixo (sem xadrez)
                decal(v, x, y - 1, bc)

    def mouth(cells):
        for (x, dy, c) in cells:
            decal(v, x, my + dy, c)

    neutral_mouth = [(-2, 1, skin_d), (-1, 1, lip), (0, 1, lip), (1, 1, skin_d), (-1, 0, lip_d), (0, 0, lip_d)]
    if act != "confident":                               # bocas menores para Índira e Súvia
        neutral_mouth = [(-1, 1, lip), (0, 1, lip), (-1, 0, lip_d), (0, 0, lip_d)]
        if act == "playful":
            neutral_mouth = [(-1, 1, lip), (0, 1, lip), (1, 2, lip), (-1, 0, lip_d)]   # canto erguido

    (lR, rR, oR), (lL, rL, oL) = eyes
    if expr == "neutral":
        for e in eyes:
            open_eye(*e)
            brow(e[0], e[1])
        mouth(neutral_mouth)
    elif act == "confident":                              # Kelyra
        if expr == "joy":                                # riso confiante de olhos abertos, sobrancelhas erguidas
            open_eye(lR, rR, oR)
            open_eye(lL, rL, oL)
            brow(lR, rR, lift=1, tilt=-1)
            brow(lL, rL, lift=1, tilt=-1)
            mouth([(-3, 2, lip), (-2, 1, lip), (-1, 1, lip), (0, 1, lip), (1, 1, lip), (2, 2, lip),
                   (-2, 0, lip_d), (-1, 0, TEETH), (0, 0, TEETH), (1, 0, lip_d)])
        elif expr == "determined":                       # olhar firme, uma sobrancelha arqueada, meio sorriso
            open_eye(lR, rR, oR)
            open_eye(lL, rL, oL)
            brow(lR, rR, tilt=1)
            brow(lL, rL, lift=1)
            mouth([(-2, 1, lip), (-1, 1, lip), (0, 1, lip), (1, 2, lip), (-1, 0, lip_d), (0, 0, lip_d)])
        else:                                            # impressionada: olhos firmes, uma sobrancelha bem alta, "oh"
            open_eye(lR, rR, oR)
            open_eye(lL, rL, oL)
            brow(lR, rR, lift=0)
            brow(lL, rL, lift=2)
            mouth([(-1, 1, lip), (0, 1, lip), (-2, 0, lip), (1, 0, lip), (-1, 0, lip_d), (0, 0, lip_d),
                   (-1, -1, lip), (0, -1, lip)])
    elif act == "sweet":                                 # Índira
        if expr == "joy":                                # olhos fechados ∩, sorriso aberto, blush forte
            closed(lR, rR, oR)
            closed(lL, rL, oL)
            brow(lR, rR, lift=1, tilt=-1)
            brow(lL, rL, lift=1, tilt=-1)
            mouth([(-2, 1, lip), (1, 1, lip), (-1, 0, lip_d), (0, 0, lip_d), (-1, 1, lip_d), (0, 1, lip_d)])
            for x in (-7, 6):
                decal(v, x, ey, BLUSH)
        elif expr == "determined":                       # séria e decidida: boca firme, sobrancelha reta baixa
            open_eye(lR, rR, oR)
            open_eye(lL, rL, oL)
            brow(lR, rR, lift=-1)
            brow(lL, rL, lift=-1)
            mouth([(-1, 1, lip_d), (0, 1, lip_d)])
        else:                                            # encantada: brilho duplo no olho, "o" pequeno, blush forte
            open_eye(lR, rR, oR)
            open_eye(lL, rL, oL)
            for (l_, r_) in ((lR, rR), (lL, rL)):
                decal(v, r_ - 1, ey + 1, EYE_HI)
            brow(lR, rR, lift=1, tilt=-1)
            brow(lL, rL, lift=1, tilt=-1)
            mouth([(-1, 0, lip_d), (0, 0, lip)])
            for x in (-7, 6):
                decal(v, x, ey, BLUSH)
                decal(v, x, ey - 1, BLUSH)
    else:                                                # Súvia — travessa
        if expr == "joy":                                # piscadela + sorriso de canto
            closed(lR, rR, oR)
            open_eye(lL, rL, oL)
            brow(lR, rR, lift=0)
            brow(lL, rL, lift=1)
            mouth([(-2, 1, lip), (-1, 1, lip), (0, 1, lip), (1, 2, lip), (2, 2, lip), (-1, 0, lip_d), (0, 0, lip_d)])
        elif expr == "determined":                       # desafio: olhos semicerrados + sorriso torto
            open_eye(lR, rR, oR, half=True)
            open_eye(lL, rL, oL, half=True)
            brow(lR, rR, tilt=1)
            brow(lL, rL, tilt=1)
            mouth([(-1, 1, lip), (0, 1, lip), (1, 1, lip), (2, 2, lip), (-2, 1, skin_d)])
        else:                                            # susto: olhos arregalados, boca em "o" de lado
            open_eye(lR, rR, oR, tall=True)
            open_eye(lL, rL, oL, tall=True)
            brow(lR, rR, lift=2)
            brow(lL, rL, lift=1)
            mouth([(0, 1, lip), (0, 0, lip_d), (1, 0, lip_d), (1, 1, lip)])
    for x in (-6, -5, 4, 5):
        decal(v, x, ey - 1, BLUSH)
    if s["freckles"]:
        freckle = C("#d3a48c")
        for (x, y) in ((-4, ey - 2), (3, ey - 2)):
            decal(v, x, y, freckle)


def head(s, expr, sk, hr):
    v = Vox()
    v.merge(sk)
    face(s, v, expr)
    v.merge(hr, overwrite=False)
    return v


# ----------------------------------------------------------------------------- rig

def volumes():
    vol = L.vol
    return [
        vol("head", "head", (0, NECK + 7.0, 0.5), 7.4),
        vol("torso", "torso", (0, HIP + 1, 0.4), 5.2, (0, NECK - 2, 0.4)),
        vol("leg_l", "leg", (3.0, 1.0, 0.8), 2.4, (3.0, HIP - 1, 0.4)),
        vol("leg_r", "leg", (-3.0, 1.0, 0.8), 2.4, (-3.0, HIP - 1, 0.4)),
        vol("arm_l", "arm_l", (SH_X, SHOULDER - 0.5, 0.4), 2.0, (EL_X, ELBOW, 0.4)),
        vol("farm_l", "arm_l", (EL_X, ELBOW, 0.4), 1.9, (EL_X + 0.3, 15.0, 0.9)),
        vol("arm_r", "arm_r", (-SH_X, SHOULDER - 0.5, 0.4), 2.0, (-EL_X, ELBOW, 0.4)),
        vol("farm_r", "arm_r", (-EL_X, ELBOW, 0.4), 1.9, (-EL_X - 0.3, 15.0, 0.9)),
    ]


def npc(key, **override):
    s = dict(SPECS[key], **override)
    sk = skull(s)
    hr = hair(s, sk)
    heads = {e: head(s, e, sk, hr) for e in ("neutral", "joy", "determined", "surprise")}
    t = torso(s)
    tl, tr = tails(s)
    arm_l, arm_r = arms(s)
    leg_l, leg_r = legs(s)
    ua_l, fa_l = L.split_limb(arm_l, ELBOW, (EL_X, ELBOW, 0.4), 1.5, s.get("sleeve", s["coat"]))
    ua_r, fa_r = L.split_limb(arm_r, ELBOW, (-EL_X, ELBOW, 0.4), 1.5, s.get("sleeve", s["coat"]))
    th_l, sh_l = L.split_limb(leg_l, KNEE, (3.1, KNEE, 0.4), 1.7, s["pants"])
    th_r, sh_r = L.split_limb(leg_r, KNEE, (-3.1, KNEE, 0.4), 1.7, s["pants"])
    parts = [("torso", t, (0, HIP, 0.4), None),
             ("head", heads["neutral"], (0, NECK, 0.5), "torso")]
    if tl is not None:
        parts += [("skirt_l", tl, (2.5, 13.0, -2.5), "torso"), ("skirt_r", tr, (-2.5, 13.0, -2.5), "torso")]
    lock, lpiv = hair_lock(s)
    if lock is not None:
        parts += [("lock", lock, lpiv, "torso")]
    if s["ears"]:
        ear_l, ear_r = fox_ears(s)
        parts += [("ear_l", ear_l, (-5.5, NECK + 14, 0.5), "head"), ("ear_r", ear_r, (4.5, NECK + 14, 0.5), "head")]
    parts += [
        ("arm_l", ua_l, (SH_X, SHOULDER, 0.4), "torso"),
        ("farm_l", fa_l, (EL_X, ELBOW, 0.4), "arm_l"),
        ("arm_r", ua_r, (-SH_X, SHOULDER, 0.4), "torso"),
        ("farm_r", fa_r, (-EL_X, ELBOW, 0.4), "arm_r"),
        ("leg_l", th_l, (3.0, HIP, 0.4), None),
        ("shin_l", sh_l, (3.1, KNEE, 0.4), "leg_l"),
        ("leg_r", th_r, (-3.0, HIP, 0.4), None),
        ("shin_r", sh_r, (-3.1, KNEE, 0.4), "leg_r"),
    ]
    alts = {"head": {e: heads[e] for e in ("joy", "determined", "surprise")}}
    rig = Rig(s["title"], VS, "biped")
    for (n, vox, piv, par) in parts:
        L.prune_specks(vox, label=f"{key}/{n}")
        for _, alt in ((alts.get(n) or {}).items()):
            L.prune_specks(alt, label=f"{key}/{n}:alt")
        rig.part(n, vox, piv, par, alts=alts.get(n))
    head_top = max(k[1] for k in hr.v)                   # orelhas (partes próprias) ficam fora da medida
    head_h = head_top - NECK + 1
    total = head_top + 1
    faces = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "bodyHeightVoxels": total, "legFraction": round(HIP / total, 3), "faces": faces,
                "footPlant": True, "joints": True, "ankle": ANKLE, "npc": True, "actingStyle": s["act_style"], "acting": s["acting"], "role": s["role"],
                "weaponMode": "none", "weaponProfile": "npc_vila",   # gesto compartilhado: acenar
                "hands": {"r": [-EL_X - 0.3, 15.0, 1.0], "l": [EL_X + 0.3, 15.0, 1.0]},
                "volumes": volumes(), "note": s["note"]}
    b = rig.all_voxels().bounds()
    print(f"  {key}: {b[3]-b[0]+1}x{b[4]-b[1]+1}x{b[5]-b[2]+1} vox, {rig.meta['headsTall']} cabeças, "
          f"{(b[4]-b[1]+1) * VS:.2f} u, {faces} faces")
    return rig


def build(out):
    for key in SPECS:
        npc(key).save(f"{out}/characters/npc/{key}")
    # rodada 06.1 — as duas alternativas de papel da Kelyra (escolhida: estalajadeira; ver especificação §10)
    for role in ("estalajadeira", "tecela"):
        npc("kelyra", role=role).save(f"{out}/studies/kelyra_{role}")
