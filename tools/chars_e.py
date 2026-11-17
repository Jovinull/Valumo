"""Direção E — Fábula Voxel: heróis consolidados (rodada 02).

Três arquétipos com a MESMA estrutura de rig e proporção (2,44 cabeças, pernas 25%), mas silhuetas,
cabelos, roupas, posturas e paletas secundárias próprias:

    adventurer  combate     topete em gancho, cachecol mostarda, ombreira de couro, mochila com
                            saco de dormir, espada larga em folha             (teal + mostarda + ferrugem)
    ranger      explorador  capuz pontudo levantado, capinha de barra em folha, trança, arco recurvo
                            na mão esquerda, aljava diagonal                   (musgo + creme + coral)
    mage        conjurador  chapéu de aba larga com copa torta, manto aberto na frente (duas metades
                            que acompanham as pernas), cajado de meia-lua com cristal (ameixa + ouro)

Biblioteca e regras: tools/charlib_e.py e docs/art/especificacao-de-personagem.md.
"""
import math

import chars_b as B
import chars_d as D
import charlib_e as L
from charlib_e import (CREAM, GOLD, GOLD_D, LEATHER, LEATHER_D, NECK, HIP, SHOULDER, SKIN, SKIN_PALE, SKIN_TAN,
                       TAN, FACE_Z, P, front_z, decal, decal_back, shade)
from voxlib import C, Vox

Y = NECK


def face_guard(x, y, z):
    """Nada de cabelo na frente dos olhos/sobrancelhas."""
    return y <= NECK + 9 and -6 <= x <= 5 and z >= 3


# ----------------------------------------------------------------------------- aventureiro

# Rosto do aventureiro (rodada 03): olhar decidido (canto externo erguido), sobrancelha grossa, meio sorriso.
ADV_FACE = {"eye": "tilt", "brow": "bold", "mouth": "smirk", "acting": "bold"}
ADV_HAIR = "h2"          # escolhido no estudo da rodada 03 (docs/historico/experimentos/rodada-03-estudos.md)


def adventurer_hair(sk, hair, style):
    """Cabelo do aventureiro. r02 = versão da rodada 02 (pontas da coroa liam como antenas);
    h1 natural assimétrico; h2 topete refinado (escolhido); h3 recorte de vento com rabinho."""
    Yh = NECK
    nape = lambda x, z: 2 if abs(x + 0.5) < 6 else 3
    nape_tips = [((-4.5, Yh + 6, -7), (-5, Yh + 3, -8.5), (-5.2, Yh + 1.5, -8.8), 2.5, 1.3),
                 ((0, Yh + 6, -7.5), (0, Yh + 3, -9), (-0.3, Yh + 0.8, -9.2), 2.6, 1.3),
                 ((4.5, Yh + 6, -7), (5, Yh + 3, -8.5), (5.2, Yh + 1.5, -8.8), 2.5, 1.3)]
    sides = [((7, Yh + 11, 0), (9.6, Yh + 8, 1), (9.6, Yh + 4.5, 0.5), 2.2, 0.9),
             ((-8, Yh + 11, 0), (-10.6, Yh + 8, 1), (-10.6, Yh + 4.5, 0.5), 2.2, 0.9)]
    fringe = [((-4, Yh + 14, 6), (-6, Yh + 12, 7.5), (-6.8, Yh + 9.5, 7.6), 2.0, 0.7),
              ((3, Yh + 14, 6), (4.8, Yh + 12.5, 7.5), (5.6, Yh + 10, 7.6), 1.9, 0.7),
              ((0, Yh + 15, 6), (-1.2, Yh + 13.5, 8.2), (-2, Yh + 11, 8.2), 1.8, 0.6)]
    bang = {-9: 4, -8: 5, -7: 8, -6: 10, -5: 11, -4: 11, -3: 12, -2: 11, -1: 12, 0: 12, 1: 11, 2: 11, 3: 12,
            4: 11, 5: 10, 6: 8, 7: 5, 8: 4}
    tail = None
    if style == "r02":
        locks = [((-1, Yh + 14, 3), (0, Yh + 19.5, 5.5), (3.5, Yh + 19.5, 8.5), 2.7, 1.2),
                 ((3.5, Yh + 19.5, 8.5), (5.0, Yh + 18.8, 9.4), (5.2, Yh + 17.0, 9.2), 1.2, 0.7),
                 ((-2, Yh + 15, -3), (-4.5, Yh + 18, -6.5), (-6.5, Yh + 16.5, -10), 2.8, 1.0),
                 ((3, Yh + 15, -3), (5.2, Yh + 17.5, -6.5), (7, Yh + 15.5, -10), 2.6, 1.0)] + sides + nape_tips + fringe
    elif style == "h1":
        # natural e assimétrico: risca à esquerda, franja varrida, coroa em volume macio, um redemoinho
        bang = {x: (12 if x < -1 else 11 if x < 3 else 9) for x in range(-10, 10)}
        bang.update({-9: 5, -8: 6, 8: 4, 9: 4})
        locks = [((5, Yh + 15, 5), (0, Yh + 15, 8.2), (-5.6, Yh + 11.6, 8.2), 2.4, 1.0),
                 ((0, Yh + 15, -2), (1, Yh + 17.6, -5), (2, Yh + 15, -9.4), 3.3, 1.7),
                 ((-2, Yh + 16, 1), (-3.2, Yh + 18, 2), (-4.6, Yh + 17.2, 3.4), 1.7, 0.7),
                 ((7, Yh + 11, 0), (9.7, Yh + 7, 1), (9.4, Yh + 3, 0.6), 2.4, 1.0),
                 ((-8, Yh + 11, 0), (-10.4, Yh + 8.6, 1), (-10.2, Yh + 6.2, 0.6), 2.0, 0.9),
                 ((3, Yh + 14, 6), (5.2, Yh + 12, 7.6), (6, Yh + 9.6, 7.6), 2.0, 0.8)] + nape_tips
    elif style == "h2":
        # topete refinado: uma onda grossa que nasce na testa e termina em cacho redondo; coroa lisa
        locks = [((0, Yh + 14, 4), (1, Yh + 19, 6.2), (3, Yh + 19.2, 9.4), 2.9, 1.6),
                 ((3, Yh + 19.2, 9.4), (4.4, Yh + 17.8, 10.2), (3.6, Yh + 16.4, 9.6), 1.6, 1.0),
                 ((0, Yh + 16, -1), (0, Yh + 17.2, -6), (0, Yh + 14.6, -10), 3.4, 2.0)] + sides + nape_tips + fringe
    elif style == "curto":
        # rodada 07: curto e bagunçado — casca rente, laterais e nuca curtas, tufos espetados no topo
        bang = {x: (12 if abs(x + 0.5) < 5 else 9 if abs(x + 0.5) < 8 else 6) for x in range(-11, 11)}
        nape = lambda x, z: 3                                  # v1 (nuca 5, laterais 8) deixava o lado da cabeça careca
        locks = [((-3, Yh + 15, 3), (-4.4, Yh + 16.8, 0), (-5.6, Yh + 16.6, -3.4), 2.0, 0.9),   # tufos deitados para trás
                 ((2, Yh + 15.5, 3), (3.0, Yh + 17.2, 0), (4.4, Yh + 16.8, -3.6), 2.1, 0.9),    # (v1 espetados liam como chifres)
                 ((-0.5, Yh + 15.5, 1), (-0.5, Yh + 17.4, -2.5), (0, Yh + 16.6, -6), 2.2, 1.0),
                 ((0, Yh + 15, -4), (0.6, Yh + 16.8, -7), (1.2, Yh + 15.4, -9.4), 2.1, 0.9),
                 ((-6, Yh + 13, -3), (-8.6, Yh + 14.6, -5), (-9.6, Yh + 13, -6.2), 1.7, 0.7),
                 ((6, Yh + 13, -4), (8.4, Yh + 14.2, -5.6), (9.2, Yh + 12.6, -6.6), 1.7, 0.7),
                 ((1, Yh + 14, 6), (2.6, Yh + 12.6, 7.8), (3.6, Yh + 11.2, 7.8), 1.7, 0.6),
                 ((-2, Yh + 14, 6), (-3.6, Yh + 13, 7.8), (-4.6, Yh + 11.8, 7.8), 1.6, 0.6)]
    else:
        # recorte de vento: mechas varridas para trás e um rabinho curto amarrado (parte 'tail')
        locks = [((-4, Yh + 15, 4), (-7, Yh + 17, -1), (-10, Yh + 15, -6), 2.6, 0.9),
                 ((2, Yh + 16, 3), (-1, Yh + 18.6, -3), (-4, Yh + 17, -8.4), 2.7, 0.9),
                 ((6, Yh + 14, 2), (5.2, Yh + 16.6, -4), (3.4, Yh + 15, -9), 2.4, 0.9),
                 ((4, Yh + 14, 6), (1, Yh + 13.6, 8.2), (-3.4, Yh + 11.6, 8.2), 2.2, 0.9)] + sides + nape_tips
        tail = Vox()
        for i, (yy, zz, r) in enumerate(((Yh + 9, -9.5, 2.0), (Yh + 7, -11, 1.9), (Yh + 5, -12, 1.6), (Yh + 3.4, -12.4, 1.2))):
            B.sblob(tail, 0, yy, zz, r, r, r, hair, p=2.2)
        tail.box(-1, Yh + 8, -11, 0, Yh + 9, -10, LEATHER)
    h = L.hair_base(sk, hair, bang, nape, thick=1.5 if style == "curto" else 2.0)
    head_top = h.bounds()[4]
    L.hair_locks(h, hair, locks)
    h = L.hair_finish(h, sk, hair, face_guard=face_guard)
    L.hair_strands(h, hair)
    if tail is not None:
        shade(tail)
    return h, head_top, tail


def adventurer(variant="e", hair_style=None, face=None, name=None, kit="sword", hair_hex=None, iris_hex=None, _parts=None):
    hair_style = hair_style or (ADV_HAIR if variant == "e" else "r02")
    face = face if face is not None else (ADV_FACE if variant == "e" else {})
    tunic = P("#3a958a")
    trim = CREAM
    scarf = P("#eeaa3a")
    pants = P("#6b4a5e")
    hair = P(hair_hex or "#c2582c", ks=0.85, kl=0.55)
    pack = P("#8a5536")
    roll = P("#ecd9ab")
    roll_s = P("#d9644a")
    boot = P("#c48a52")
    iris = C(iris_hex or "#3d9c78")
    brow = B.shadow(hair, 1.6)

    # --- cabeça
    sk = L.skull(SKIN)
    h, head_top, ptail = adventurer_hair(sk, hair, hair_style)
    heads = L.head_with_expressions(sk, h, iris, brow, style=face)
    if _parts is not None:                                 # rodada 07: componentes (tools/components_e.py)
        _parts.update(sk=sk, hair=h, hood=None, iris=iris, brow=brow, face=face)

    # --- tronco
    t = L.torso(tunic, hem=trim)
    zb = L.belt(t, HIP + 1, LEATHER)
    for y in range(HIP + 4, NECK - 3, 3):                      # costura frontal
        decal(t, -1, y, B.shadow(tunic, 1.2))
    # talabarte: ombro direito -> quadril esquerdo
    for i in range(11):
        x, y = -4 + i, NECK - 3 - i
        for xx in (x, x + 1):
            z = front_z(t, xx, y)
            if z is not None:
                t.set(xx, y, z + 1, LEATHER_D)
    # cachecol: anel + nó à esquerda com duas pontas
    sc = Vox()
    L.ring(sc, NECK - 3, NECK - 1, 5.7, 4.8, scarf)
    L.ring(sc, NECK - 2, NECK - 2, 6.2, 5.3, scarf)
    B.sblob(sc, 3.6, NECK - 3.2, 4.6, 1.8, 1.6, 1.4, scarf, p=2.2)
    sc.box(3, NECK - 9, 5, 4, NECK - 4, 5, scarf)
    sc.box(5, NECK - 8, 5, 5, NECK - 4, 5, scarf)
    sc.box(5, NECK - 8, 4, 5, NECK - 7, 4, None)
    for k in [k for k in sc.v if abs(k[0] + 0.5) < 4.2 and abs(k[2] - 0.5) < 3.3 and k[1] >= NECK - 2]:
        del sc.v[k]
    # rodada 05.1: as duas pontas pendentes do nó viram parte própria ("scarf"), com movimento secundário
    scarf_tail_keys = {k for k in sc.v if k[1] <= NECK - 5 and k[0] >= 3 and k[2] >= 4}
    t.merge(sc)
    # bolsinha no quadril direito
    L.B.rbox(t, -8, HIP - 1, -1, -6, HIP + 2, 3, LEATHER)
    t.box(-8, HIP + 2, -1, -6, HIP + 2, 3, TAN)
    t.set(-9, HIP + 1, 1, GOLD)
    # mochila + saco de dormir
    B.rbox(t, -4, HIP + 1, -9, 3, NECK - 7, -5, pack)
    t.box(-3, NECK - 9, -10, 2, NECK - 7, -10, TAN)            # aba
    t.box(-1, NECK - 9, -11, 0, NECK - 9, -11, GOLD)
    t.box(-3, HIP + 2, -10, 2, HIP + 4, -10, LEATHER)          # bolso
    for x in range(-6, 6):
        for y in range(NECK - 7, NECK - 1):
            for z in range(-11, -4):
                if (y + 0.5 - (NECK - 4.0)) ** 2 + (z + 0.5 + 7.6) ** 2 <= 2.75 ** 2:
                    t.set(x, y, z, roll_s if x in (-4, -3, 2, 3) else roll)
    shade(t)
    # rodada 05.1: mochila + saco de dormir e pontas do cachecol saem do tronco DEPOIS da pintura (mesmas
    # cores); ficam como partes filhas do tronco para o atraso estilizado do Animator (secundário)
    pk, st = Vox(), Vox()
    for k in [k for k in t.v if k[2] <= -6 and HIP + 1 <= k[1] <= NECK - 1]:
        pk.v[k] = t.v.pop(k)
    for k in [k for k in scarf_tail_keys if k in t.v]:
        st.v[k] = t.v.pop(k)

    # --- membros
    arm_l, arm_r = L.arm(tunic, LEATHER, cuff=trim)
    # ombreira de couro em duas placas no braço da espada (camada 4: identidade de combate)
    pa = Vox()
    B.sblob(pa, 7.4, SHOULDER - 0.2, 0.5, 3.4, 2.6, 3.5, LEATHER, p=2.2)
    B.sblob(pa, 8.2, SHOULDER - 2.6, 0.5, 2.9, 1.8, 3.1, LEATHER_D, p=2.2)
    for k in [k for k in pa.v if k[0] < 5]:
        del pa.v[k]
    for k in list(pa.v):
        if k[1] == int(SHOULDER - 1) and (k[0], k[1] - 1, k[2]) not in pa.v:
            pa.v[k] = GOLD_D
    pa.set(8, int(SHOULDER + 1), 3, GOLD)
    shade(pa)
    arm_r.merge(pa.flipped_x(), overwrite=True)
    leg_l, leg_r = L.leg(pants, boot, B.light(boot), LEATHER_D)

    # kit (rodada 04): "sword" (herói oficial), "shield" (espada + broquel, estudo) ou "longblade" (estudo)
    if kit == "longblade":
        sw, grip = L.longblade()
        wvol = L.longblade_volumes()
    else:
        sw, grip = L.sword()
        wvol = L.sword_volumes()

    alts = {"head": {k: v for k, v in heads.items() if k != "neutral"}}
    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", heads["neutral"], (0, NECK, 0.5), "torso"),
        *L.limb_parts(arm_l, arm_r, leg_l, leg_r, tunic, pants),
        ("weapon", sw, grip, "farm_r"),
    ]
    if pk.v:
        parts.insert(2, ("pack", pk, (0, NECK - 6, -6.5), "torso"))
    if st.v:
        parts.insert(3, ("scarf", st, (4, NECK - 4, 5), "torso"))
    if kit == "shield":
        sh, sgrip = L.buckler(P("#3a958a"), L.STEEL_D, L.GOLD)
        parts.append(("shield", sh, sgrip, "farm_l"))
        wvol = wvol + L.buckler_volumes()
    if ptail is not None:
        parts.append(("tail", ptail, (0, NECK + 9.5, -9.0), "head"))
    name = name or ("Aventureiro (E)" if variant == "e" else "V2 Storybook Fusion")
    profile = {"sword": "sword", "shield": "sword_shield", "longblade": "longblade"}[kit]
    vols = L.body_volumes(back=((0, 14.5, -8.0), (0, 22.0, -8.0), 3.4)) + wvol
    return L.finish(name, parts, head_top, {"weaponMode": "sword", "weaponProfile": profile, "volumes": vols,
                                            "hands": {"r": [-7.0, 13.0, 1.0], "l": [7.0, 13.0, 1.0]}}, alts=alts)



# ----------------------------------------------------------------------------- exploradora

RNG_FACE = {"eye": "almond", "brow": "thin", "mouth": "small", "freckles": True, "lash": True, "acting": "cool"}
RNG_HOOD = "c3"          # escolhido no estudo da rodada 03


def ranger(hood_style=None, face=None, name=None, hair_hex=None, iris_hex=None, _parts=None):
    """Exploradora. hood_style: c0 = rodada 02 (capuz verde pontudo); c1 = c0 refinado;
    c2 = capuz de folha azul-petróleo/terracota com a ponta enrolada para a frente;
    c3 = capuz abaixado e assimétrico, cabelo à mostra, faixa, pena e bússola (escolhido)."""
    hood_style = hood_style or RNG_HOOD
    face = RNG_FACE if face is None else face
    teal = hood_style in ("c2", "c3")
    hood_c = P("#2f6f73") if teal else P("#6f9a44")
    hood_d = P("#24575c") if teal else P("#557a38")
    lining = P("#c8643c") if teal else P("#3f5f2e")      # forro: terracota (c2/c3) ou verde escuro
    tunic = P("#f0dcae")
    legg = P("#8c5340")
    boot = P("#6e4130")
    hair = P(hair_hex or "#e3a64a", ks=0.85, kl=0.5)
    coral = P("#c8643c") if teal else P("#e0644e")
    quiv = P("#b0623c")
    bronze = C("#c08a3e", "metal")
    iris = C(iris_hex or "#3d8fd8")
    brow = B.shadow(hair, 1.9)

    sk = L.skull(SKIN_TAN)
    hood = Vox()
    if hood_style == "c3":
        # cabelo inteiro à mostra: franja varrida, laterais até o queixo, massa atrás (a trança sai dela)
        bang = {x: (10 if x < 2 else 9) for x in range(-10, 10)}
        bang.update({-9: 4, -8: 5, 7: 4, 8: 4, 9: 4, 3: 10})
        h = L.hair_base(sk, hair, bang, 2, thick=1.3)        # casca fina: o volume vem das mechas
        L.hair_locks(h, hair, [((4, Y + 15, 5), (0, Y + 14.6, 8.2), (-5.4, Y + 11.4, 8.2), 2.3, 1.0),
                               ((-1, Y + 15, -2), (-1, Y + 16, -5.5), (0, Y + 14, -9), 2.8, 1.6),
                               ((7, Y + 11, 0), (9.6, Y + 7, 1), (9.2, Y + 2.4, 0.6), 2.4, 1.0),
                               ((-8, Y + 11, 0), (-10.4, Y + 7, 1), (-10, Y + 2.4, 0.6), 2.4, 1.0),
                               ((3, Y + 14, 6), (5, Y + 12, 7.6), (5.8, Y + 9.6, 7.6), 1.9, 0.8)])
        h = L.hair_finish(h, sk, hair, face_guard=face_guard, sheen=True)
        L.hair_strands(h, hair, period=0.95)
        if _parts is not None:
            _parts["hair_sem_faixa"] = h.copy()                # cabelo antes da faixa do capuz (componente puro)
        # faixa terracota amarrada (nó e duas pontas soltas do lado direito) + pena do lado esquerdo
        for (x, y, z), c in list(h.v.items()):
            if y in (NECK + 13, NECK + 14) and z >= -7 and z <= 5 and (x, y + 1, z) in h.v:
                h.v[(x, y, z)] = lining
        B.sblob(hood, -9.4, NECK + 13.5, -3.0, 1.6, 1.4, 1.6, lining, p=2.0)
        L.D.lock(hood, (-9.6, NECK + 13, -4), (-11.6, NECK + 10.5, -6.5), (-11.4, NECK + 7.5, -8), 1.0, 0.6, lining)
        L.D.lock(hood, (-9.2, NECK + 13, -4.5), (-10.4, NECK + 9.5, -7.8), (-9.8, NECK + 6.5, -8.6), 1.0, 0.6, lining)
        feather = [(9, NECK + 13, -1), (10, NECK + 14, -2), (10, NECK + 15, -3), (11, NECK + 16, -4), (11, NECK + 17, -5),
                   (12, NECK + 18, -6)]
        # rodada 05: a pena é uma escada 6-conexa (antes, degraus só na diagonal liam como pontilhado)
        L.connect_path(hood, feather, lambda i: CREAM if i < 4 else coral)
        for i, (x, y, z) in enumerate(feather):
            if 1 <= i <= 4:
                hood.set(x, y + 1, z, CREAM)
    else:
        bang = {x: (10 if abs(x + 0.5) < 6 else 6) for x in range(-10, 10)}
        bang.update({-3: 11, -2: 10, 1: 10, 2: 11})
        h = L.hair_base(sk, hair, bang, 2, thick=1.1)        # casca fina: quem dá volume é o capuz
        L.hair_locks(h, hair, [((-4, Y + 13, 6), (-5.5, Y + 11, 7.6), (-6.4, Y + 9.6, 7.6), 1.8, 0.7),
                               ((3, Y + 13, 6), (4.6, Y + 11.5, 7.6), (5.4, Y + 9.6, 7.6), 1.8, 0.7),
                               ((0, Y + 13.5, 6.5), (-0.6, Y + 12, 7.8), (-1.2, Y + 10.4, 7.8), 1.6, 0.6)])
        h = L.hair_finish(h, sk, hair, face_guard=face_guard, sheen=False)
        if hood_style == "c0":
            B.sblob(hood, 0, NECK + 8.6, -0.4, 10.4, 9.8, 9.4, hood_c, p=2.35)
            L.D.lock(hood, (0, Y + 15, -3), (0, Y + 19.5, -8), (0.5, Y + 15.5, -13.5), 3.4, 1.0, hood_c)
            open_r, open_cy = (7.3, 7.3), NECK + 6.2
        elif hood_style == "c1":
            B.sblob(hood, 0, NECK + 8.8, -0.6, 9.9, 9.4, 9.2, hood_c, p=2.4)
            L.D.lock(hood, (0, Y + 15, -4), (0, Y + 18, -8), (1.5, Y + 15.2, -11.5), 3.0, 1.4, hood_c)
            open_r, open_cy = (8.0, 7.8), NECK + 6.6
        else:
            # c2: capuz de folha — copa redonda, ponta que sobe e se ENROLA PARA A FRENTE (broto)
            B.sblob(hood, 0, NECK + 8.6, -0.6, 10.0, 9.4, 9.2, hood_c, p=2.4)
            L.D.lock(hood, (0, Y + 15, -3), (0, Y + 18.2, -2), (0, Y + 18.6, 2.4), 2.8, 1.5, hood_c)
            L.D.lock(hood, (0, Y + 18.6, 2.4), (0, Y + 18.2, 4.6), (0, Y + 16.8, 4.8), 1.5, 1.0, hood_c)
            open_r, open_cy = (7.8, 7.6), NECK + 6.4
        inner = sk.copy().merge(h)
        for k in [k for k in hood.v if k in inner.v]:
            del hood.v[k]
        for (x, y, z) in list(hood.v):
            open_face = z >= 1 and ((x + 0.5) / open_r[0]) ** 2 + ((y + 0.5 - open_cy) / open_r[1]) ** 2 < 1.0
            if open_face or y < NECK - 1 or (y < NECK + 2 and z > 2):
                del hood.v[(x, y, z)]
        for (x, y, z) in list(hood.v):                   # borda da abertura no tom do forro
            if z >= 1 and ((x + 0.5) / (open_r[0] + 0.9)) ** 2 + ((y + 0.5 - open_cy) / (open_r[1] + 0.9)) ** 2 < 1.0:
                hood.v[(x, y, z)] = lining
        if hood_style == "c2":                           # borda recortada em folhas (terracota)
            for (x, y, z) in list(hood.v):
                if y <= NECK + 1 and z < 1 and ((math.atan2(x + 0.5, z) * 7 / math.pi) % 1.0) < 0.35:
                    hood.v[(x, y, z)] = lining
        shade(hood)
    # capuz é chapéu: fica fora da razão de cabeças (como o chapéu da maga); conta o topo do cabelo
    head_top = h.bounds()[4]
    heads = L.head_with_expressions(sk, h, iris, brow, extra=hood, style=face)
    if _parts is not None:
        _parts.update(sk=sk, hair=h, hood=hood, iris=iris, brow=brow, face=face, lining=lining)

    # trança (à esquerda das costas; a aljava fica à direita): gomos alternados, laço coral
    tail = Vox()
    for i, y in enumerate(range(NECK + 2, NECK - 13, -3)):
        ox = 0.6 if i % 2 else 0.0
        B.sblob(tail, 5.0 + ox, y, -8.6 - min(i, 2) * 0.4, 2.1, 2.0, 2.0, hair, p=2.3)
    shade(tail)
    tail.paint(lambda x, y, z, c: B.shadow(hair, 1.3) if (y - NECK - 2) % 3 == 2 else None)
    tail.box(3, NECK - 13, -11, 7, NECK - 12, -8, coral)
    B.sblob(tail, 5.3, NECK - 15.5, -9.4, 1.4, 1.8, 1.3, hair, p=2.2)
    if _parts is not None:
        _parts["tail"] = tail

    # tronco: túnica creme, cinto, capinha com barra recortada em folhas
    t = L.torso(tunic, hem=hood_d, rx0=6.5, rx1=4.9)
    L.belt(t, HIP + 1, LEATHER)
    cp = Vox()

    def prof(y):
        k = (NECK - 1 - y) / 7.0
        return (0, 0.3, 5.6 + 2.6 * k ** 0.8, 5.0 + 2.2 * k ** 0.8)
    B.stack(cp, NECK - 8, NECK - 1, prof, hood_c, p=2.3)
    for (x, y, z) in list(cp.v):                         # barra em folhas: triângulos de 3 voxels
        if y <= NECK - 7:
            th = math.atan2(x + 0.5, z - 0.3)
            ph = (th / (2 * math.pi) * 14) % 1.0
            cut = (y - (NECK - 8)) + 1
            if abs(ph - 0.5) * 2 > cut / 2.2:
                del cp.v[(x, y, z)]
    cp.paint(lambda x, y, z, c: hood_d if y <= NECK - 7 else None)
    if hood_style == "c3":
        # capuz abaixado: rolo de tecido em volta do pescoço, mais volumoso e caído sobre o ombro esquerdo
        B.sblob(cp, 0, NECK - 1.2, -2.6, 6.4, 2.6, 4.4, hood_c, p=2.2)
        B.sblob(cp, 4.6, NECK - 2.6, -1.0, 3.4, 3.0, 4.2, hood_c, p=2.2)
        B.sblob(cp, 0.5, NECK - 3.2, -6.0, 4.6, 3.6, 2.4, hood_d, p=2.2)
        for k in [k for k in cp.v if abs(k[0] + 0.5) < 4.6 and k[2] > -2.6 and k[1] >= NECK - 2]:
            del cp.v[k]
        for (x, y, z) in list(cp.v):                     # forro terracota na dobra de cima
            if y >= NECK - 1 and (x, y + 1, z) not in cp.v:
                cp.v[(x, y, z)] = lining
    t.merge(cp)
    if hood_style == "c3":                                # bússola de bronze na correia do peito
        zc2 = front_z(t, -2, NECK - 8)
        if zc2 is not None:
            t.box(-3, NECK - 9, zc2 + 1, -1, NECK - 7, zc2 + 1, bronze)
            t.set(-2, NECK - 8, zc2 + 2, C("#7fe0d0", "emissive"))
    zc = front_z(t, 0, NECK - 3)
    t.box(-1, NECK - 4, zc + 1, 0, NECK - 3, zc + 1, GOLD)   # broche
    # aljava vertical à direita das costas, flechas sobre o ombro direito
    B.rbox(t, -6, HIP + 1, -9, -3, NECK - 4, -6, quiv)
    t.box(-6, HIP + 4, -10, -3, HIP + 5, -10, TAN)
    t.box(-6, NECK - 6, -10, -3, NECK - 5, -10, TAN)
    for (x, z, top) in ((-6, -8, NECK + 1), (-5, -7, NECK + 2), (-4, -9, NECK), (-3, -7, NECK + 1)):
        t.box(x, NECK - 4, z, x, top, z, CREAM)
        t.box(x, top - 1, z, x, top + 1, z, coral)
    # bolsa no quadril esquerdo
    B.rbox(t, 5, HIP - 2, -3, 8, HIP + 2, 2, LEATHER)
    t.box(5, HIP + 1, -3, 8, HIP + 2, 2, TAN)
    shade(t)

    arm_l, arm_r = L.arm(tunic, TAN, cuff=hood_d, puff=None)
    leg_l, leg_r = L.leg(legg, boot, B.light(boot), B.shadow(boot), tall=True)
    bw, grip = L.bow(wood=P("#9a6236"), wrap=coral, with_string=False)

    alts = {"head": {k: v for k, v in heads.items() if k != "neutral"}}
    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", heads["neutral"], (0, NECK, 0.5), "torso"),
        *L.limb_parts(arm_l, arm_r, leg_l, leg_r, tunic, legg, tall=True),
        ("bow", bw, grip, "farm_l"),
        *[(n, v, pv, "bow") for (n, v, pv) in L.bow_strings()],
        ("arrow", L.arrow(fletch=coral, length=15), grip, "bow"),
        ("tail", tail, (5.0, NECK + 3.5, -7.5), "head"),
    ]
    vols = L.body_volumes(back=((0, 15.0, -7.5), (0, 25.0, -7.5), 2.8), tall=True) + L.bow_volumes()
    return L.finish(name or "Exploradora (E)", parts, head_top,
                    {"weaponMode": "bow", "weaponProfile": "bow", "lean": 0.045, "tailGain": 0.45, "volumes": vols,
                     "hands": {"r": [-7.0, 13.0, 1.0], "l": [7.0, 13.0, 1.0]}}, alts=alts)


# ----------------------------------------------------------------------------- maga

MAGE_FACE = {"eye": "round", "brow": "flat", "mouth": "small", "big_hi": True, "lash": True, "acting": "soft"}
MAGE_HAT = "m1"          # escolhido no estudo da rodada 03
# Chapéus: raio da aba (x, z), altura da aba acima do pescoço, níveis da copa, raio da base da copa
HATS = {"m0": ((12.2, 11.8), 14, 7, 7.0, False),        # rodada 02
        "m1": ((10.4, 10.0), 15, 6, 6.2, True),         # aba menor e erguida, faixa escura de separação
        "m2": ((11.2, 10.8), 15, 7, 5.6, True)}         # aba média virada, copa alta e estreita


def mage(hat_style=None, face=None, staff_style="r3", name=None, hair_hex=None, iris_hex=None, _parts=None):
    hat_style = hat_style or MAGE_HAT
    face = MAGE_FACE if face is None else face
    robe = P("#7a4a8c")
    robe_d = P("#5a3670")
    gold = P("#ecb84c")
    hair = P(hair_hex or "#e2d4f2", ks=0.8, kl=0.4)
    tights = P("#4a3058")
    crystal = C("#7ff0dc", "emissive")
    crystal_hi = C("#e6fff8", "emissive")
    iris = C(iris_hex or "#8a58c8")
    brow = B.shadow(hair, 2.6)

    sk = L.skull(SKIN_PALE)
    bang = {x: 10 for x in range(-10, 10)}
    bang.update({-7: 4, -8: 3, -9: 3, 6: 4, 7: 3, 8: 3, -1: 11, 0: 11})
    h = L.hair_base(sk, hair, bang, 3, thick=1.8)
    L.hair_locks(h, hair, [
        ((7, Y + 8, 1), (9.4, Y + 5, 1.5), (8.8, Y + 1.5, 2), 2.8, 1.4),      # tufos do chanel
        ((-8, Y + 8, 1), (-10.4, Y + 5, 1.5), (-9.8, Y + 1.5, 2), 2.8, 1.4),
        ((0, Y + 8, -7), (0, Y + 4, -9.5), (0, Y + 1.5, -9.5), 3.4, 1.6),
        ((-4, Y + 14, 6), (-6, Y + 12, 7.5), (-6.6, Y + 9.6, 7.6), 1.9, 0.7),
    ])
    h = L.hair_finish(h, sk, hair, face_guard=face_guard, sheen=False)
    L.hair_strands(h, hair, period=1.0)
    head_top = h.bounds()[4]
    heads = L.head_with_expressions(sk, h, iris, brow, style=face)
    if _parts is not None:
        _parts.update(sk=sk, hair=h, hood=None, iris=iris, brow=brow, face=face)

    # chapéu: aba larga com a frente erguida + copa torta com a ponta dobrada e um amuleto
    (brx, brz), by_off, n, r0, band = HATS[hat_style]
    hat = Vox()
    by = NECK + by_off
    for x in range(-13, 13):
        for z in range(-12, 14):
            d = ((x + 0.5) / brx) ** 2 + ((z - 0.5) / brz) ** 2
            if d <= 1.0:
                lift = (1 if z >= 7 else 0) + (1 if z >= 10 else 0) - (1 if d > 0.82 and z < 0 else 0)
                if hat_style == "m2" and x > 3 and d > 0.5:
                    lift += 1                              # aba virada do lado esquerdo
                hat.set(x, by + lift, z, robe)
                if d < 0.55:
                    hat.set(x, by + 1 + lift, z, robe)
    for i in range(n):
        k = i / (n - 1)
        r = r0 * (1 - k) ** 0.9 + 1.1
        cx, cz = -3.6 * k ** 1.8, -0.5 - 3.0 * k ** 1.8
        B.stack(hat, by + 2 + i, by + 2 + i, lambda y: (cx, cz, r, r), gold if i == 0 else robe, p=2.2)
    tip = by + n + 1.5                                    # a ponta dobrada acompanha a altura da copa
    L.D.lock(hat, (-3.8, tip, -3.6), (-6.2, tip + 1.5, -5), (-8.2, tip - 1, -6), 1.6, 0.9, robe)
    am = int(tip) - 4
    hat.box(-9, am, -7, -8, am + 2, -6, gold)             # amuleto de estrela pendurado
    hat.set(-10, am + 1, -7, gold)
    hat.set(-7, am + 1, -7, gold)
    for k in [k for k in hat.v if k in sk.v or k in h.v]:
        del hat.v[k]
    shade(hat)
    for (x, y, z), c in list(hat.v.items()):              # debrum dourado na borda da aba
        if y <= by + 3 and ((x + 0.5) / brx) ** 2 + ((z - 0.5) / brz) ** 2 > 0.86:
            hat.v[(x, y, z)] = gold
    if band:                                              # separação cabelo × aba: avesso escuro da aba
        for (x, y, z), c in list(hat.v.items()):
            if (x, y - 1, z) not in hat.v and c != gold and ((x + 0.5) / brx) ** 2 + ((z - 0.5) / brz) ** 2 <= 0.86:
                hat.v[(x, y, z)] = L.deep(robe_d)

    # tronco + pelerine curta com gola (manto rígido acima da cintura: nada cruza as pernas)
    t = L.torso(robe, rx0=6.0, rx1=4.8, rz0=4.6, y0=HIP)
    L.belt(t, HIP + 1, gold, buckle=None, h=1)
    for y in range(HIP + 2, NECK - 6):
        decal(t, -1, y, robe_d)
        decal(t, 0, y, robe_d)
    L.ring(t, NECK - 6, NECK - 2, 6.3, 5.6, robe_d)
    L.ring(t, NECK - 6, NECK - 6, 6.8, 6.0, gold)
    L.ring(t, NECK - 2, NECK - 1, 5.0, 4.4, CREAM)
    zc = front_z(t, 0, NECK - 4)
    t.box(-1, NECK - 5, zc + 1, 0, NECK - 4, zc + 1, crystal)
    shade(t)

    # manto-sino aberto na frente, em duas metades que acompanham as pernas (skirt_l / skirt_r)
    sk_ = Vox()

    def bell(y):
        k = (HIP + 1 - y) / (HIP - 1)
        return (0, 0.4, 6.0 + 2.2 * k ** 0.8, 5.2 + 2.2 * k ** 0.8)   # sino menor que na rodada 02
    B.stack(sk_, 2, HIP + 1, bell, robe, p=2.4)
    for (x, y, z) in list(sk_.v):
        cx, cz, rx, rz = bell(y)
        hollow = abs((x + 0.5) / (rx - 1.3)) ** 2.4 + abs((z + 0.5 - cz) / (rz - 1.3)) ** 2.4 <= 1.0
        slit = z > 1 and abs(x + 0.5) < 1.0 + (HIP + 1 - y) * 0.28
        if hollow or slit:
            del sk_.v[(x, y, z)]
    sk_.paint(lambda x, y, z, c: gold if y == 2 else (robe_d if y == 3 else None))
    for (x, y) in ((-5, 7), (5, 5), (-3, 4), (6, 8), (-7, 5)):
        decal(sk_, x, y, GOLD)
    shade(sk_)
    skirt_l = sk_.cut(lambda x, y, z, c: x >= 0)
    skirt_r = sk_

    arm_l, arm_r = L.arm(robe, None, cuff=gold, bell=True, skin_hand=SKIN_PALE)
    leg_l, leg_r = L.leg(tights, LEATHER, TAN, LEATHER_D)
    if staff_style == "r02":
        st, grip = L.staff(gold, crystal, crystal_hi, length=25)   # topo abaixo da aba do chapéu
    else:
        st, grip = L.staff_fork(gold, crystal, crystal_hi, length=24)

    alts = {"head": {k: v for k, v in heads.items() if k != "neutral"}}
    parts = [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", heads["neutral"], (0, NECK, 0.5), "torso"),
        *L.limb_parts(arm_l, arm_r, leg_l, leg_r, robe, tights),
        ("skirt_l", skirt_l, (3.5, HIP, 0.5), "torso"),
        ("skirt_r", skirt_r, (-3.5, HIP, 0.5), "torso"),
        ("weapon", st, grip, "farm_r"),
        ("hat", hat, (0, by, 0.5), "head"),
    ]
    vols = L.body_volumes(hat=True, skirt=True) + L.staff_volumes(length=24 if staff_style != "r02" else 25)
    return L.finish(name or "Maga (E)", parts, head_top,
                    {"weaponMode": "staff", "weaponProfile": "staff", "weaponRest": 0.0, "lean": -0.03, "volumes": vols,
                     "hands": {"r": [-7.0, 13.0, 1.0], "l": [7.0, 13.0, 1.0]}}, alts=alts)


def build(out):
    adventurer("e").save(f"{out}/characters/e/adventurer")
    ranger().save(f"{out}/characters/e/ranger")
    mage().save(f"{out}/characters/e/mage")
