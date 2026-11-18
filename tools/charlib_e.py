"""Direção E — Fábula Voxel: biblioteca de personagens (rodada 02).

Linguagem consolidada: proporções e materiais da B (formas redondas, corpo em pera, sombra pintada em
poucos tons quentes/arroxeados), acabamento de personagem da D (olhos com íris e brilho, sobrancelhas,
cabelo em mechas com volume), legibilidade da A (cores vivas, separação clara entre peças).

Escala: voxel de 1/40 u. Marcos verticais (voxels):
    0..10   pernas (11)       HIP = 11 (pivô das pernas)
    9..25   tronco (barra da túnica cobre o topo das coxas)
    26..41  crânio (16)       NECK = 26 (pivô da cabeça)
    ..43/44 casca de cabelo   cabeça com cabelo = 18 → 44 / 18 = 2,44 cabeças
Rosto: placa frontal plana em z = 6; olhos 3 × 5 (cílio, pupila 2 × 2, brilho do MESMO lado nos dois
olhos, íris em 3 tons), sobrancelha de 3 voxels, boca de 2, blush em L. Sem nariz.

Hierarquia de detalhe (docs/art/especificacao-de-personagem.md): 1 silhueta, 2 grandes volumes de
roupa, 3 materiais e cores, 4 detalhes de identidade, 5 ornamentos. Cada função abaixo pertence a
uma camada; ornamentos só entram depois que a silhueta funciona em preto chapado.
"""
import math

import chars_b as B
import chars_d as D
from voxlib import C, Rig, Vox, exposed_faces

VS = 1 / 40

HIP = 11
NECK = 26
SKULL_H = 16
SHOULDER = 23.5
EYE_Y = NECK + 3          # linha de baixo dos olhos (olho 3 × 5)
FACE_Z = 6                # plano do rosto

# ----------------------------------------------------------------------------- cor e sombra

P = B.P                   # cor-base com rampa (sombra arroxeada / luz pêssego) registrada


def deep(c):
    """Tom de sombra profunda (reentrâncias, parte de baixo de abas)."""
    return B.shadow(c, 1.7)


def shade(v, hi=0.55, lo=-0.28, low2=-0.70, ctx=None, only=None):
    """Sombra pintada em 4 tons (profunda / sombra / base / luz) pela normal suavizada.
    Bandas largas, sem ruído: é o que dá a cara de ilustração e deixa a fusão de faces trabalhar."""
    src = ctx or v
    d = src.v
    for (x, y, z), c in list(v.v.items()):
        r = B.RAMPS.get(c)
        if r is None or (only and c not in only):
            continue
        if all((x + a, y + b, z + e) in d for (a, b, e) in D.N6):
            continue
        n = B.normal(src, x, y, z)
        L = 0.78 * n[1] + 0.22 * (n[0] * -0.36 + n[1] * 0.80 + n[2] * 0.48)
        if L > hi:
            v.v[(x, y, z)] = r[1]
        elif L < low2:
            v.v[(x, y, z)] = deep(c)
        elif L < lo:
            v.v[(x, y, z)] = r[0]
    return v


def front_z(v, x, y, zmin=-99):
    zs = [k[2] for k in v.v if k[0] == x and k[1] == y and k[2] >= zmin]
    return max(zs) if zs else None


def back_z(v, x, y):
    zs = [k[2] for k in v.v if k[0] == x and k[1] == y]
    return min(zs) if zs else None


def decal(v, x, y, c, zmin=0):
    z = front_z(v, x, y, zmin)
    if z is not None:
        v.set(x, y, z, c)


def decal_back(v, x, y, c):
    z = back_z(v, x, y)
    if z is not None:
        v.set(x, y, z, c)


def lerp(a, b, t):
    return a + (b - a) * t


# ----------------------------------------------------------------------------- paleta comum

SKIN = P("#f6c9a0", ks=0.55)
SKIN_TAN = P("#e8b185", ks=0.55)
SKIN_PALE = P("#f9d8ba", ks=0.55)
BLUSH = C("#f2988a")
MOUTH = C("#a9524a")
MOUTH_IN = C("#7a3440")
LASH = C("#3a2238")
PUPIL = C("#2a1c34")
EYE_HI = C("#fffaf0")
# Rodada 08 — dentes em marfim quente, bem abaixo do brilho do olho: com EYE_HI (o tom mais claro do rosto) os
# dentes viravam uma mancha branca que competia com os olhos (aventureiro determinado, Kelyra alegre).
TEETH = C("#dccbbb")
LEATHER = P("#9a6238")
LEATHER_D = P("#6e4130")
TAN = P("#c98f55")
CREAM = P("#f3e3c0")
GOLD = C("#f2c14e", "metal")
GOLD_D = C("#c98f3a", "metal")
STEEL = C("#d2d8e2", "metal")
STEEL_D = C("#9aa4b8", "metal")
STEEL_E = C("#eef1f4", "metal")
WOOD = P("#a8713f")
WOOD_D = P("#7a4c33")

# ----------------------------------------------------------------------------- cabeça


def skull(skin=SKIN):
    """Crânio 16 × 16 × 15 com rosto plano (placa em z = FACE_Z) e queixo cheio."""
    v = Vox()
    B.sblob(v, 0, NECK + 8.0, 0.5, 8.0, 8.0, 7.3, skin, p=2.6, p_lo=3.2)
    for k in [k for k in v.v if k[2] > FACE_Z]:
        del v.v[k]
    for x in range(-7, 7):
        for y in range(NECK + 1, NECK + 13):
            f = front_z(v, x, y)
            if f is not None and 3 <= f < FACE_Z and abs(x + 0.5) <= 6.6:
                for z in range(f, FACE_Z + 1):
                    v.set(x, y, z, skin)
    for ex in (8, -9):                                   # orelhas pequenas
        v.box(ex, NECK + 5, -1, ex, NECK + 7, 0, skin)
    shade(v, hi=0.62, lo=-0.40, low2=-0.9)
    return v


# Estilos de rosto (rodada 03): a GRAMÁTICA é fixa (placa plana, olho de 3 de largura, brilho do lado da
# luz, sobrancelha 2 acima do cílio, boca de 2, blush); o que varia por personagem está aqui.
FACE_DEFAULT = {
    "eye": "round",      # round (3 × 5) | almond (3 × 4, pálpebra) | tilt (canto externo erguido)
    "brow": "flat",      # flat | bold (2 linhas na ponta interna) | arch (meio erguido) | thin (alta e curta)
    "mouth": "smile",    # smile | smirk (um canto só) | small (1 voxel + sombra) | flat
    "freckles": False,   # 3 sardas sob o blush
    "big_hi": False,     # brilho de 2 voxels (olhar mais "molhado")
    "lash": False,       # ponta de cílio externa
    "acting": None,      # rodada 06: jeito próprio de expressar (bold | cool | soft); None = desenho comum antigo
}


def paint_face(v, iris, brow, expr="neutral", lash=False, mouth_style=None, style=None):
    """Rosto da E (pintar DEPOIS do shade do crânio). expr: neutral | joy | determined | surprise.

    Olho base 3 × 5 (lição da variante V3: olho alto lê melhor à distância de exploração):
        ey+4  cílio  cílio  cílio   (+ ponta externa se lash; 'tilt' ergue o canto externo)
        ey+3  brilho pupila pupila
        ey+2  íris   pupila pupila
        ey+1  íris   íris   íris-escura
        ey+0  íris-clara x3 (reflexo de baixo)
    'almond' remove a linha de cima (olho 3 × 4 com pálpebra). O brilho fica no MESMO lado nos dois
    olhos (lado da luz), nunca espelhado. `style` = chaves de FACE_DEFAULT."""
    st = dict(FACE_DEFAULT)
    st.update(style or {})
    if lash:
        st["lash"] = True
    if mouth_style:
        st["mouth"] = mouth_style
    if st.get("acting") and expr != "neutral":
        return act_face(v, iris, brow, expr, st)          # o rosto NEUTRO nunca passa por aqui (identidade aprovada)
    iris_l = B.light(iris, 1.6)
    iris_d = B.shadow(iris, 1.3)
    skin_d = B.shadow(SKIN, 1.2)
    ey = EYE_Y
    almond = st["eye"] == "almond"
    for xs in ((2, 3, 4), (-5, -4, -3)):
        inner = xs[0] if xs[0] > 0 else xs[2]
        outer = xs[2] if xs[0] > 0 else xs[0]
        out_dir = 1 if outer > 0 else -1
        top = ey + (3 if almond else 4)
        if expr == "joy":                                # olho fechado em arco contínuo (∩)
            for x in xs:
                decal(v, x, ey + 3, LASH)
            decal(v, xs[0], ey + 2, LASH)
            decal(v, xs[2], ey + 2, LASH)
        else:
            if expr == "surprise":
                top = ey + 5
            for x in xs:
                decal(v, x, top, LASH)
            if st["eye"] == "tilt" and expr != "surprise":
                decal(v, outer, top + 1, LASH)           # canto externo erguido: olhar decidido
            if expr == "surprise":                       # pupila pequena no meio do branco
                for x in xs:
                    for y in (ey + 4, ey + 3, ey + 1):
                        decal(v, x, y, EYE_HI)
                decal(v, xs[1], ey + 3, PUPIL)
                decal(v, xs[1], ey + 2, PUPIL)
                decal(v, xs[0], ey + 2, iris)
                decal(v, xs[2], ey + 2, iris)
                for x in xs:
                    decal(v, x, ey, EYE_HI)
            else:
                hi_row = ey + 2 if almond else ey + 3
                if not almond:
                    decal(v, xs[0], ey + 3, EYE_HI)
                    decal(v, xs[1], ey + 3, PUPIL)
                    decal(v, xs[2], ey + 3, PUPIL)
                decal(v, xs[0], ey + 2, EYE_HI if almond else iris)
                decal(v, xs[1], ey + 2, PUPIL)
                decal(v, xs[2], ey + 2, PUPIL)
                decal(v, xs[0], ey + 1, iris if not (almond and st["big_hi"]) else EYE_HI)
                decal(v, xs[1], ey + 1, iris if not almond else PUPIL)
                decal(v, xs[2], ey + 1, iris_d)
                for x in xs:
                    decal(v, x, ey, iris_l)
                if st["big_hi"] and not almond:
                    decal(v, xs[0], ey + 2, EYE_HI)
                if expr == "determined":                 # pálpebra baixa: o cílio desce um voxel
                    for x in xs:
                        decal(v, x, hi_row, LASH)
            if st["lash"]:
                decal(v, outer + out_dir, top, LASH)
        # sobrancelha (2 acima do cílio do olho redondo)
        by = ey + 6
        b = st["brow"]
        if expr == "determined":
            decal(v, inner, by - 1, brow)
            decal(v, xs[1], by, brow)
            decal(v, outer, by, brow)
            if b == "bold":
                decal(v, inner, by, brow)
        elif expr == "surprise":
            for x in xs:
                decal(v, x, by + 1, brow)
        elif expr == "joy":
            for x in xs:
                decal(v, x, by + 1, brow)
        elif b == "bold":
            for x in xs:
                decal(v, x, by, brow)
            decal(v, inner, by - 1 if False else by + 0, brow)
            decal(v, outer, by + 1, brow)                # ponta externa erguida, traço grosso
            decal(v, xs[1], by + 1, brow)
        elif b == "arch":
            decal(v, inner, by, brow)
            decal(v, xs[1], by + 1, brow)
            decal(v, outer, by, brow)
        elif b == "thin":
            decal(v, xs[1], by, brow)
            decal(v, outer, by, brow)
        else:
            for x in xs:
                decal(v, x, by, brow)
        decal(v, outer + out_dir, ey, BLUSH)
        decal(v, outer + out_dir, ey - 1, BLUSH)
        decal(v, outer, ey - 1, BLUSH)
        if st["freckles"]:
            for (dx, dy) in ((0, -2), (out_dir, -2), (-out_dir, -1)):
                decal(v, outer + dx, ey + dy, B.shadow(SKIN, 1.5))
    # boca
    my = ey - 2
    corner = skin_d
    m = st["mouth"]
    if expr == "joy":
        for x in (-2, -1, 0, 1):
            decal(v, x, my + 1, MOUTH if x in (-1, 0) else corner)
        decal(v, -1, my, MOUTH_IN)
        decal(v, 0, my, MOUTH_IN)
    elif expr == "surprise":
        for x in (-1, 0):
            decal(v, x, my + 1, MOUTH_IN)
            decal(v, x, my, MOUTH_IN)
    elif expr == "determined":
        for x in (-1, 0, 1):
            decal(v, x, my, MOUTH)
    elif m == "smirk":
        decal(v, -1, my, MOUTH)
        decal(v, 0, my, MOUTH)
        decal(v, 1, my + 1, MOUTH)                       # meio sorriso de um lado só
    elif m == "small":
        decal(v, -1, my, MOUTH)
        decal(v, 0, my, corner)
    elif m == "flat":
        decal(v, -1, my, MOUTH)
        decal(v, 0, my, MOUTH)
    else:
        decal(v, -1, my, MOUTH)
        decal(v, 0, my, MOUTH)
        decal(v, -2, my + 1, corner)
        decal(v, 1, my + 1, corner)
    return v


def act_face(v, iris, brow, expr, st):
    """Rodada 06 — expressões com ATUAÇÃO por personagem (joy | determined | surprise). A gramática não muda:
    placa plana, olho de 3 de largura nas mesmas colunas, brilho do lado da luz (coluna de menor x de cada
    olho), blush, sem nariz. Muda o que carrega a personalidade: boca (até 4 de largura, 2 linhas), sobrancelha
    (altura, ângulo, assimetria) e forma do olho (piscadela, meia-lua, olho apertado, olho arregalado COM íris).
      bold — impulsivo: piscadela + sorrisão; sobrancelhas em V + dentes cerrados; olhos arregalados + boca O.
      cool — contido: olhos de meia-lua + sorriso torto; mira (um olho apertado); surpresa cética (uma
             sobrancelha alta, boca pequena de lado).
      soft — gentil: olhos fechados ^ ^ + sorriso de gato + blush forte; serena (pálpebra baixa, boca firme);
             encantada (olho com brilho duplo, "o" pequeno)."""
    a = st["acting"]
    iris_l, iris_d = B.light(iris, 1.6), B.shadow(iris, 1.3)
    skin_d = B.shadow(SKIN, 1.2)
    ey, by, my = EYE_Y, EYE_Y + 6, EYE_Y - 2
    eyes = ((-5, -4, -3), (2, 3, 4))                       # (olho direito do personagem, olho esquerdo)

    def open_eye(xs, top=4, wide=False, squint=0, hi2=False):
        """Olho aberto: linhas ey..ey+top-1 + cílio em ey+top; squint corta linhas de cima; wide = branco
        acima da íris (o olhar continua com cor — a surpresa antiga virava branco vazio)."""
        rows = list(range(ey, ey + top))
        rows = rows[:len(rows) - squint] if squint else rows
        for y in rows:
            k = y - ey
            for i, x in enumerate(xs):
                c = iris_l if k == 0 else (iris if k == 1 else PUPIL if i > 0 else iris)
                if k == 1 and i == 2:
                    c = iris_d
                if wide and k >= top - 1:
                    c = EYE_HI
                decal(v, x, y, c)
        hi_y = rows[-1] if not wide else rows[-2]
        decal(v, xs[0], hi_y, EYE_HI)                      # brilho do lado da luz
        if hi2 and len(rows) > 2:
            decal(v, xs[0], hi_y - 1, EYE_HI)
            decal(v, xs[1], hi_y, EYE_HI)
        for x in xs:
            decal(v, x, rows[-1] + 1, LASH)
        if st["lash"]:
            decal(v, xs[2] + 1 if xs[0] > 0 else xs[0] - 1, rows[-1] + 1, LASH)

    def closed_arc(xs, up=True):                           # ∩ (up) ou ∪ (meia-lua de riso)
        for x in xs:
            decal(v, x, ey + (3 if up else 2), LASH)
        for x in (xs[0], xs[2]):
            decal(v, x, ey + (2 if up else 3), LASH)

    def brows(xs, lift=0, tilt=0, thick=False):
        """tilt > 0: ponta interna desce (bravo); tilt < 0: ponta interna sobe (preocupado/encantado)."""
        inner = xs[2] if xs[0] < 0 else xs[0]
        for x in xs:
            d = 0
            if x == inner:
                d = -tilt
            elif x == xs[1]:
                d = -tilt // 2 if tilt > 0 else 0
            decal(v, x, by + lift + d, brow)
            if thick:
                decal(v, x, by + lift + d - 1, brow)

    def mouth(cells):
        for (x, dy, c) in cells:
            decal(v, x, my + dy, c)

    R, Lf = eyes
    if a == "bold":
        if expr == "joy":
            closed_arc(R)                                  # piscadela: olho direito fechado
            open_eye(Lf)
            brows(R, lift=1)
            brows(Lf, lift=1)
            mouth([(-3, 1, skin_d), (-2, 1, MOUTH), (-1, 1, MOUTH), (0, 1, MOUTH), (1, 1, MOUTH), (2, 1, skin_d),
                   (-2, 0, MOUTH_IN), (-1, 0, MOUTH_IN), (0, 0, MOUTH_IN), (1, 0, MOUTH_IN)])   # sorrisão aberto
        elif expr == "determined":
            open_eye(R, squint=1)
            open_eye(Lf, squint=1)
            brows(R, tilt=2, thick=True)
            brows(Lf, tilt=2, thick=True)
            mouth([(-2, 1, MOUTH), (-1, 1, MOUTH), (0, 1, MOUTH), (1, 1, MOUTH),
                   (-2, 0, TEETH), (-1, 0, TEETH), (0, 0, TEETH), (1, 0, TEETH)])              # dentes cerrados
        else:
            open_eye(R, top=5, wide=True)                  # espanto de quem grita "ah!"
            open_eye(Lf, top=5, wide=True)
            brows(R, lift=2)
            brows(Lf, lift=2)
            mouth([(-2, 1, MOUTH), (-1, 1, MOUTH), (0, 1, MOUTH), (1, 1, MOUTH),
                   (-2, 0, MOUTH_IN), (-1, 0, MOUTH_IN), (0, 0, MOUTH_IN), (1, 0, MOUTH_IN)])
    elif a == "cool":
        if expr == "joy":
            closed_arc(R, up=False)                        # meia-lua de riso contido
            closed_arc(Lf, up=False)
            brows(R)
            brows(Lf, lift=1)
            mouth([(-2, 0, MOUTH), (-1, 0, MOUTH), (0, 0, MOUTH), (1, 1, MOUTH), (2, 1, MOUTH),
                   (-1, 1, skin_d)])                                                                  # sorriso torto, largo
        elif expr == "determined":
            open_eye(R, top=3, squint=1)                   # mira: o olho direito aperta
            open_eye(Lf, top=3)
            brows(R, lift=-1)                              # testa franzida só desse lado (reta, baixa)
            brows(Lf, lift=0)
            mouth([(-1, 0, MOUTH), (0, 0, MOUTH), (1, 0, skin_d)])
        else:
            open_eye(R, top=4, wide=True)
            open_eye(Lf, top=4, wide=True)
            brows(R, lift=0)
            brows(Lf, lift=2)                              # uma sobrancelha alta: surpresa cética
            mouth([(0, 0, MOUTH_IN), (1, 0, MOUTH), (0, 1, MOUTH)])
    else:  # soft
        if expr == "joy":
            closed_arc(R)
            closed_arc(Lf)
            brows(R, lift=1, tilt=-1)
            brows(Lf, lift=1, tilt=-1)
            mouth([(-2, 1, MOUTH), (-1, 0, MOUTH), (0, 0, MOUTH), (1, 1, MOUTH), (-1, 1, MOUTH_IN), (0, 1, MOUTH_IN)])
            for xs in eyes:                                # blush mais forte
                outer = xs[0] if xs[0] < 0 else xs[2]
                decal(v, outer, ey, BLUSH)
        elif expr == "determined":
            open_eye(R, top=4, squint=2, hi2=True)          # serena e firme: pálpebra bem baixa
            open_eye(Lf, top=4, squint=2, hi2=True)
            brows(R, tilt=1)
            brows(Lf, tilt=1)
            mouth([(-2, 0, skin_d), (-1, 0, MOUTH), (0, 0, MOUTH), (1, 0, skin_d)])
        else:
            open_eye(R, top=4, hi2=True)                   # encantada: olho cheio de brilho, sobrancelha em
            open_eye(Lf, top=4, hi2=True)                  # "telhadinho" e "o" pequeno, blush forte
            brows(R, lift=1, tilt=-2)
            brows(Lf, lift=1, tilt=-2)
            mouth([(-1, 0, MOUTH_IN), (0, 0, MOUTH)])
            for xs in eyes:
                outer = xs[0] if xs[0] < 0 else xs[2]
                decal(v, outer, ey, BLUSH)
                decal(v, outer + (-2 if outer < 0 else 2), ey - 1, BLUSH)
    for xs in eyes:                                        # blush e sardas como no neutro
        outer = xs[0] if xs[0] < 0 else xs[2]
        od = -1 if outer < 0 else 1
        decal(v, outer + od, ey, BLUSH)
        decal(v, outer + od, ey - 1, BLUSH)
        decal(v, outer, ey - 1, BLUSH)
        if st["freckles"]:
            for (dx, dy) in ((0, -2), (od, -2), (-od, -1)):
                decal(v, outer + dx, ey + dy, B.shadow(SKIN, 1.5))
    return v


def hair_base(sk, col, bang, nape, thick=1.7):
    """Casca de cabelo (D.hair_shell) com limites: bang(x) = altura relativa a NECK da franja na
    metade da frente; nape(x, z) = altura relativa da nuca atrás."""
    h = Vox()
    for (x, y, z) in D.dilate(sk, thick):
        if (x, y, z) in sk.v or y < NECK:
            continue
        lim = (bang(x, z) if callable(bang) else bang.get(x, 11)) if z >= 1 else (nape(x, z) if callable(nape) else nape)
        if y >= NECK + lim:
            h.set(x, y, z, col)
    return h


def hair_locks(h, col, locks):
    for (a, m, b, r0, r1) in locks:
        D.lock(h, a, m, b, r0, r1, col)
    return h


def hair_finish(h, sk, col, face_guard=None, sheen=True):
    """Limpa o que invadiu o crânio/rosto, fecha buracos e pinta (4 tons + faixa de brilho)."""
    for k in [k for k in h.v if k in sk.v]:
        del h.v[k]
    if face_guard:
        for k in [k for k in h.v if face_guard(*k)]:
            del h.v[k]
    tmp = h.copy().merge(sk)
    D.fill_pits(tmp, col, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == col)
    shade(h, hi=0.50, lo=-0.25, low2=-2.0)    # cabelo em 3 tons: a sombra profunda virava faixa na testa
    if sheen:                                            # faixa de brilho ilustrada no topo frontal
        hl = B.light(col, 1.8)
        for (x, y, z), c in list(h.v.items()):
            if y in (NECK + 14, NECK + 15) and z >= 1 and (x, y + 1, z) not in h.v and c in (col, B.RAMPS[col][1]):
                h.v[(x, y, z)] = hl
    return h


def hair_strands(h, col, period=0.85, twist=0.03, zmax=2, width=0.13):
    """Sulcos pintados entre mechas na parte de trás/lados: o cabelo lê como mechas largas sem
    geometria picotada (que vira ruído cruzado nas costas — defeito da primeira tentativa)."""
    r = B.RAMPS[col]
    groove = r[0]
    top = max(k[1] for k in h.v)
    for (x, y, z), c in list(h.v.items()):
        if z > zmax or y > top - 2:
            continue
        th = math.atan2(x + 0.5, -(z + 0.5))
        s = th / period + (top - y) * twist
        f = s - math.floor(s)
        if f < width and c in (col, r[1]):
            h.v[(x, y, z)] = groove
    return h


def head_with_expressions(sk_shaded, hair, iris, brow, lash=False, extra=None, style=None):
    """Monta a cabeça neutra e as variantes de expressão (mesmas posições de voxel)."""
    heads = {}
    for ex in ("neutral", "joy", "determined", "surprise"):
        s = sk_shaded.copy()
        paint_face(s, iris, brow, ex, lash=lash, style=style)
        hd = s.merge(hair)
        if extra:
            hd.merge(extra)
        heads[ex] = hd
    return heads


# ----------------------------------------------------------------------------- corpo


def torso(col, hem=None, y0=HIP - 2, rx0=6.7, rx1=5.0, rz0=5.0, rz1=3.9, belly=0.4):
    """Tronco em pera; a barra (y0..HIP-1) abre sobre as coxas."""
    v = Vox()

    def prof(y):
        t = (y - HIP) / (NECK - 1 - HIP)
        if y < HIP:
            k = (HIP - y) / max(1, HIP - y0)
            return (0, 0.5 + belly * 0.6, rx0 + 0.6 * k, rz0 + 0.5 * k)
        return (0, 0.5 + belly * math.sin(math.pi * min(1.0, t * 1.3)), rx0 - (rx0 - rx1) * t ** 1.25, rz0 - (rz0 - rz1) * t)

    B.stack(v, y0, NECK - 1, prof, col, p=2.7)
    for k in [k for k in v.v if k[1] == NECK - 1 and abs(k[0] + 0.5) > rx1 - 1.2]:
        del v.v[k]                                     # ombro arredondado
    if hem:
        for k in [k for k in v.v if k[1] == y0]:
            v.v[k] = hem
    return v


def belt(t, y, col, buckle=GOLD, h=2):
    for yy in range(y, y + h):
        for (x, yy2, z) in [k for k in t.v if k[1] == yy]:
            t.v[(x, yy2, z)] = col
    zf = front_z(t, 0, y)
    if buckle and zf is not None:
        t.box(-2, y - 1 if h > 1 else y, zf + 1, 1, y + h - 1 + (1 if h > 1 else 0), zf + 1, buckle)
        t.box(-1, y, zf + 1, 0, y + h - 1, zf + 1, LEATHER_D)
    return zf


def ring(v, y0, y1, rx, rz, col, cz=0.5, p=2.4, cx=0.0):
    B.stack(v, y0, y1, lambda y: (cx, cz, rx, rz), col, p=p)
    return v


def arm(sleeve, glove, cuff=None, puff=2.5, skin_hand=None, bell=False, sleeve_top=None):
    """Braço esquerdo (x>0) em leve pose A; mão grande e redonda. Devolve (esquerdo, direito)."""
    a = Vox()
    cx, cz = 7.2, 0.5
    B.sblob(a, 6.7, SHOULDER - 1.0, cz, 2.6, 2.6, 2.7, sleeve, p=2.3)       # ombro
    B.stack(a, 16, int(SHOULDER) - 1, lambda y: (cx + (0.25 if y < 19 else 0), cz, 2.1 + (0.2 if y > 20 else 0), 2.2), sleeve, p=2.4)
    if bell:                                                                # manga-sino
        B.stack(a, 13, 17, lambda y: (cx + 0.3, cz, 2.4 + (17 - y) * 0.35, 2.5 + (17 - y) * 0.35), sleeve, p=2.3)
        if cuff:
            for k in [k for k in a.v if k[1] == 13]:
                a.v[k] = cuff
        hand = skin_hand or SKIN
        B.sblob(a, cx + 0.4, 11.8, cz + 0.4, 2.1, 2.0, 2.1, hand, p=2.5)
    else:
        if puff:
            B.stack(a, 15, 16, lambda y: (cx + 0.2, cz, puff, puff), cuff or sleeve, p=2.6)
        B.sblob(a, cx + 0.4, 12.8, cz + 0.4, 2.45, 2.4, 2.5, skin_hand or glove, p=2.6)
        a.set(5, 13, 3, skin_hand or glove)                                  # polegar
    for k in [k for k in a.v if k[0] < 4]:
        del a.v[k]
    shade(a)
    return a, a.flipped_x()


def leg(pants, boot, cuff, sole, tall=False, cx=3.5, boot_style=None):
    """Perna esquerda (x>0): coxa curta e bota grande de bico redondo. Devolve (esquerda, direita).
    Rodada 09: boot_style="viajante" (personalização) = forro de lã creme virado em sino + tira com fivela no peito do pé, tudo
    dentro da canela (mesmo pivô de joelho, mesma sola e tornozelo: o IK do pé não muda). None = a bota canônica."""
    l = Vox()
    cz = 0.5
    top = 6 if not tall else 7
    B.stack(l, top, HIP - 1, lambda y: (cx, cz, 2.4, 2.5), pants, p=2.6)
    B.stack(l, 2, top - 1, lambda y: (cx + 0.1, cz, 2.8, 2.9), boot, p=2.8)
    B.sblob(l, cx + 0.2, 1.5, cz + 1.5, 3.0, 2.2, 4.3, boot, p=2.6)          # pé
    for y in (top - 1, top):
        ring(l, y, y, 3.25, 3.3, cuff, cz=cz, cx=cx + 0.1, p=2.8)
    for k in [k for k in l.v if k[1] == top and abs(k[0] + 0.5 - cx) < 2.2 and abs(k[2] + 0.5 - cz) < 2.2]:
        l.v[k] = pants
    for k in [k for k in l.v if k[1] < 0 or k[0] < 1]:
        del l.v[k]
    for k in [k for k in l.v if k[1] == 0]:
        l.v[k] = sole
    if boot_style == "viajante":
        # v2: forro de lã (creme) virado para fora, 3 fileiras abrindo em sino — a v1 usava o tom claro da própria bota
        # e não se lia na câmera de jogo (contraste de valor é o que separa a peça a distância)
        for y in (top - 2, top - 1, top):
            ring(l, y, y, 3.55 + 0.25 * (y - top + 2), 3.6 + 0.25 * (y - top + 2), CREAM, cz=cz, cx=cx + 0.1, p=2.6)
        for k in [k for k in l.v if k[1] == top and abs(k[0] + 0.5 - cx) < 2.2 and abs(k[2] + 0.5 - cz) < 2.2]:
            l.v[k] = pants                                               # a calça entra pela boca da bota
        ring(l, 3, 3, 3.0, 3.05, LEATHER_D, cz=cz + 0.4, cx=cx + 0.1, p=2.6)   # tira sobre o peito do pé
        fz = max(k[2] for k in l.v if k[1] == 3 and abs(k[0] + 0.5 - (cx + 0.1)) < 1.0)
        l.set(int(round(cx - 0.4)), 3, fz, GOLD)                         # fivela (2 voxels, presa à tira)
        l.set(int(round(cx + 0.6)), 3, fz, GOLD_D)
        for k in [k for k in l.v if k[1] < 0 or k[0] < 1]:
            del l.v[k]
    shade(l)
    return l, l.flipped_x()


# ----------------------------------------------------------------------------- articulações (rodada 03)

ELBOW = 17                # pivô do cotovelo (y); o antebraço inclui manga baixa, punho e mão
ANKLE = 1.5               # altura do tornozelo (alvo do IK de pé), em voxels
ARM_X = 7.45              # x do eixo do braço na altura do cotovelo


def knee_y(tall=False):
    """Pivô do joelho: logo acima da dobra da bota (a canela é a bota inteira)."""
    return 8 if tall else 7


def split_limb(v, cut_y, pivot, cap_r, cap_col):
    """Divide um membro em (segmento de cima, segmento de baixo) no plano y = cut_y.
    O de baixo ganha uma TAMPA esférica de raio cap_r no pivô, só em células do interior do segmento de
    cima: escondida com o membro reto, preenche a fresta (com o tom da dobra) quando a junta dobra."""
    upper = v.cut(lambda x, y, z, c: y >= cut_y)
    lower = v
    cx, cy, cz = pivot
    crease = deep(cap_col) if cap_col in B.RAMPS else cap_col
    r = int(cap_r) + 1
    for x in range(int(cx) - r, int(cx) + r + 1):
        for y in range(int(cy) - r, int(cy) + r + 1):
            for z in range(int(cz) - r, int(cz) + r + 1):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2 <= cap_r * cap_r:
                    if y >= cut_y and (x, y, z) in upper.v:
                        lower.set(x, y, z, crease)
    return upper, lower


def limb_parts(arm_l, arm_r, leg_l, leg_r, sleeve, pants, tall=False):
    """Partes articuladas (braço/antebraço, coxa/canela) com pivôs e hierarquia prontos para o rig."""
    k = knee_y(tall)
    ua_l, fa_l = split_limb(arm_l, ELBOW, (ARM_X, ELBOW, 0.5), 1.6, sleeve)
    ua_r, fa_r = split_limb(arm_r, ELBOW, (-ARM_X, ELBOW, 0.5), 1.6, sleeve)
    th_l, sh_l = split_limb(leg_l, k, (3.6, k, 0.5), 1.8, pants)
    th_r, sh_r = split_limb(leg_r, k, (-3.6, k, 0.5), 1.8, pants)
    return [
        ("arm_l", ua_l, (6.5, SHOULDER, 0.5), "torso"),
        ("farm_l", fa_l, (ARM_X, ELBOW, 0.5), "arm_l"),
        ("arm_r", ua_r, (-6.5, SHOULDER, 0.5), "torso"),
        ("farm_r", fa_r, (-ARM_X, ELBOW, 0.5), "arm_r"),
        ("leg_l", th_l, (3.5, HIP, 0.5), None),
        ("shin_l", sh_l, (3.6, k, 0.5), "leg_l"),
        ("leg_r", th_r, (-3.5, HIP, 0.5), None),
        ("shin_r", sh_r, (-3.6, k, 0.5), "leg_r"),
    ]


# ----------------------------------------------------------------------------- equipamento


STEEL_M = C("#b9c3d2", "metal")        # rodada 05: corpo da lâmina (tom médio) — fio claro × corpo × sulco escuro
STEEL_S = C("#7d889c", "metal")        # dorso/sulco
BRONZE = C("#c08a3e", "metal")
BRONZE_D = C("#8f6230", "metal")


def sword(blade=STEEL_M, edge=STEEL_E, fuller=STEEL_S, guard=GOLD, grip=LEATHER_D, gem=None, length=22, x=-8):
    """Espada de uma mão, VERTICAL (o animador inclina), punho no pivô. Rodada 05: leitura em três tons por
    coluna (fio claro nas bordas, corpo médio, sulco escuro no centro) e nervura em relevo nos dois lados da
    lâmina, para a espada ter volume e não parecer uma placa; pomo redondo, cabo com faixas, guarda com as
    pontas viradas e avesso escuro. Pivô e eixos inalterados (mesmos volumes de validação)."""
    s = Vox()
    hy = 13                                               # centro da mão
    for y in range(hy - 3, hy + 3):                       # cabo com faixas de couro
        s.box(x, y, 0, x + 1, y, 1, B.light(grip) if (y - hy) % 2 == 0 else grip)
    s.box(x - 1, hy - 5, -1, x + 2, hy - 4, 2, guard)     # pomo redondo (4 × 2 × 4 sem os cantos)
    for cx in (x - 1, x + 2):
        for cz in (-1, 2):
            del s.v[(cx, hy - 5, cz)]
            del s.v[(cx, hy - 4, cz)]
    s.set(x, hy - 5, -1, GOLD_D)
    s.set(x + 1, hy - 4, 2, B.light(guard) if guard in B.RAMPS else guard)
    for dx in range(-4, 6):                               # guarda: barra com as pontas viradas e avesso escuro
        up = 1 if dx in (-4, 5) else 0
        s.box(x + dx, hy + 3 + up, 0, x + dx, hy + 4 + up, 1, guard)
        s.set(x + dx, hy + 3 + up, -1, GOLD_D) if abs(dx - 0.5) < 2 else None
    if gem:
        s.box(x, hy + 3, 2, x + 1, hy + 4, 2, gem)
    for i in range(length):
        y = hy + 5 + i
        u = i / (length - 1)
        half = 1.6 + 1.1 * math.sin(math.pi * min(1.0, u * 1.25)) if u < 0.86 else 1.6 * (1 - u) / 0.14 + 0.2
        lo, hi_ = int(round(x + 0.5 - half)), int(round(x + 0.5 + half)) - 1
        for xx in range(lo, hi_ + 1):
            e = xx in (lo, hi_)
            core = xx in (x, x + 1) and 0.04 < u < 0.72
            c = edge if e else (fuller if core else blade)
            s.box(xx, y, 0, xx, y, 1, c)
        if 0.04 < u < 0.72:                                # nervura em relevo (dá volume à lâmina)
            for zz in (-1, 2):
                s.box(x, y, zz, x + 1, y, zz, blade)
    return s, (x + 1.0, hy, 1.0)


def staff(top_col, crystal, crystal_hi, length=38, x=-8):
    """Cajado VERTICAL na mão direita (meta.weaponMode = staff): cabo nodoso, gancho em meia-lua e
    cristal flutuante preso por aro dourado. O cabo passa pelo centro da mão."""
    s = Vox()
    hy = 13
    y0 = hy - 10
    for y in range(y0, y0 + length):
        k = (y - y0) // 4
        c = WOOD if k % 2 == 0 else WOOD_D
        s.box(x, y, 0, x + 1, y, 1, c)
        if y in (y0 + 9, y0 + 19):                       # nós
            s.box(x - 1, y, 0, x + 2, y, 1, WOOD_D)
    s.box(x, y0, 0, x + 1, y0, 1, GOLD_D)                # ponteira
    s.box(x - 1, hy - 2, -1, x + 2, hy - 2, 2, LEATHER)  # empunhadura
    s.box(x - 1, hy + 3, -1, x + 2, hy + 3, 2, LEATHER)
    ty = y0 + length
    # meia-lua: arco em X que abraça o cristal
    for i in range(0, 15):
        a = math.pi * (i / 14)
        px = x + 0.5 + 4.2 * math.cos(a)
        py = ty + 4.0 + 4.2 * math.sin(a) * 0.95
        s.box(int(round(px)) - 1, int(round(py)), 0, int(round(px)), int(round(py)), 1, top_col)
    s.box(x - 4, ty, 0, x + 5, ty + 1, 1, top_col)
    s.box(x - 1, ty + 1, 0, x + 2, ty + 2, 1, GOLD)
    B.sblob(s, x + 1.0, ty + 5.2, 1.0, 2.0, 2.8, 2.0, crystal, p=1.3)
    B.sblob(s, x + 1.0, ty + 5.6, 1.0, 0.9, 1.4, 0.9, crystal_hi, p=1.3)
    return s, (x + 1.0, hy, 1.0)


def staff_fork(band, crystal, crystal_hi, length=24, x=-8):
    """Cajado da rodada 03: cabo nodoso que se abre em DUAS GALHAS curvas abraçando um cristal grande,
    com aro dourado e um pingente. Silhueta 'Y com uma luz' lê na câmera de exploração; topo abaixo da
    aba do chapéu na pose de repouso."""
    s = Vox()
    hy = 13
    y0 = hy - 10
    for y in range(y0, y0 + length):
        c = WOOD if ((y - y0) // 4) % 2 == 0 else WOOD_D
        s.box(x, y, 0, x + 1, y, 1, c)
        if y in (y0 + 8, y0 + 17):
            s.box(x - 1, y, 0, x + 2, y, 1, WOOD_D)              # nós
    s.box(x, y0, 0, x + 1, y0, 1, GOLD_D)
    s.box(x - 1, hy - 2, -1, x + 2, hy - 2, 2, LEATHER)
    s.box(x - 1, hy + 3, -1, x + 2, hy + 3, 2, LEATHER)
    ty = y0 + length
    for sgn in (1, -1):                                         # galhas
        D.lock(s, (x + 1.0, ty - 1, 1.0), (x + 1.0 + sgn * 3.6, ty + 3.0, 1.0), (x + 1.0 + sgn * 2.2, ty + 8.0, 1.0),
               1.3, 0.7, WOOD)
    s.box(x - 1, ty - 1, 0, x + 2, ty, 1, band)                 # aro
    # rodada 05: cristal LAPIDADO (losango, p = 1) em dois tons — lê como cristal, não como bolha
    B.sblob(s, x + 1.0, ty + 4.6, 1.0, 2.4, 3.4, 2.4, crystal, p=1.05)
    B.sblob(s, x + 0.6, ty + 5.2, 1.6, 0.9, 1.6, 0.9, crystal_hi, p=1.05)
    for sgn in (1, -1):                                         # pontas das galhas em bronze
        s.set(int(round(x + 1.0 + sgn * 2.2)), ty + 8, 1, BRONZE)
    s.box(x + 3, ty - 3, 1, x + 3, ty - 1, 1, band)             # pingente
    s.set(x + 3, ty - 4, 1, crystal)
    return s, (x + 1.0, hy, 1.0)


def bow(wood=WOOD, wrap=None, string=CREAM, tip=GOLD, half=17, x=8, with_string=True):
    """Arco recurvo VERTICAL na mão esquerda (parte 'bow'): plano YZ, ventre à frente, corda atrás.
    Recurva nas pontas e 2 voxels de espessura para ler de frente e de lado."""
    w = Vox()
    hy = 13
    wrap = wrap or LEATHER_D
    tipz = 0
    for sgn in (1, -1):
        for i in range(half + 1):
            u = i / half
            # rodada 05: arco mais fundo (lê como arco de perfil e em 3/4) e recurva mais marcada nas pontas
            z = 3.6 - 6.6 * u ** 1.6 + (4.0 * ((u - 0.78) / 0.22) ** 1.4 if u > 0.78 else 0)
            y = hy + sgn * i
            zz = int(round(z))
            c = tip if i >= half - 1 else (wrap if i <= 2 else wood)
            w.box(x - 1, y, zz, x + 1, y, zz + 1, c)           # 3 de largura: lê de frente
            if c == wood and wood in B.RAMPS:                  # rodada 05: ventre (lado da corda) claro, costas escuras
                w.box(x - 1, y, zz, x + 1, y, zz, B.light(wood))
                w.box(x - 1, y, zz + 1, x + 1, y, zz + 1, B.shadow(wood))
            if 3 < i < half - 3:
                w.box(x - 1, y, zz + 2, x, y, zz + 2, B.light(wood) if sgn > 0 else wood)
            tipz = zz
    if with_string:
        for y in range(hy - half + 2, hy + half - 1):
            w.set(x, y, tipz - 1, string)
            w.set(x + 1, y, tipz - 1, string)
    return w, (x + 1.0, hy, 1.0)


def bow_strings(string=CREAM, half=17, x=8):
    """Rodada 04: a corda em DUAS partes (string_u, string_d), cada uma pendurada numa ponta do arco e
    apontando para o meio. O animador gira e estica cada metade até a mão que puxa (corda em V).
    Retorna [(nome, Vox, pivô)], pivôs nas pontas (espaço do modelo)."""
    hy = 13
    y_top, y_bot = hy + half - 2, hy - half + 2
    up, dn = Vox(), Vox()
    for y in range(hy, y_top + 1):
        up.box(x, y, 0, x + 1, y, 0, string)
    for y in range(y_bot, hy):
        dn.box(x, y, 0, x + 1, y, 0, string)
    return [("string_u", up, (x + 1.0, y_top + 0.5, 0.5)), ("string_d", dn, (x + 1.0, y_bot + 0.5, 0.5))]


def buckler(face, rim, boss, x=7, radius=6.4):
    """Broquel redondo (rodada 05: raio 6,4, era 5,6), seguro pela empunhadura central no punho esquerdo.
    Disco abaulado à frente da mão (face para +Z): aro de aço, tábuas em dois tons, bossa dourada com
    quatro rebites e uma folha pintada (motivo P3 discreto). Original: nada de brasão. Pivô = centro da mão."""
    s = Vox()
    hy = 13
    cx, cy = x + 0.5, hy + 0.5
    R = int(radius) + 1
    for xx in range(int(cx) - R, int(cx) + R + 1):
        for yy in range(int(cy) - R, int(cy) + R + 1):
            d = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
            if d > radius:
                continue
            if d > radius - 1.0:                                # aro de aço (2 de espessura)
                s.box(xx, yy, 3, xx, yy, 4, rim)
                continue
            plank = face if ((xx - x) // 3) % 2 == 0 else B.shadow(face) if face in B.RAMPS else face
            s.set(xx, yy, 3, plank)
            s.set(xx, yy, 4, B.light(plank) if (face in B.RAMPS and yy > hy + 1) else plank)
            if d < radius * 0.55:                               # abaulado: o centro sobe 1 voxel
                s.set(xx, yy, 5, plank)
    s.box(x - 1, hy - 1, 6, x + 1, hy + 1, 6, boss)             # bossa
    s.set(x, hy, 7, GOLD)
    for (dx, dy) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):       # rebites
        s.set(x + dx, hy + dy, 5, GOLD_D)
    for i in range(3):                                          # folha pintada (P3), dentro da face
        s.set(x - 1 - i, hy + 2 + i, 5, B.light(face) if face in B.RAMPS else face)
    s.box(x, hy - 2, 1, x + 1, hy + 2, 2, LEATHER_D)            # empunhadura atrás do disco
    return s, (x + 0.0, hy, 1.0)


def longblade(back=STEEL_S, body=STEEL_M, edge=STEEL_E, guard=BRONZE, grip=LEATHER_D, wrap=CREAM, length=34, x=-8):
    """Lâmina longa de estudo, VERTICAL com o punho no pivô. Rodada 05: deixou de ser um "bastão branco" —
    lâmina de UM fio com dorso escuro (−X), corpo médio e fio claro (+X, o lado que corta nos perfis), curva
    suave para trás (até 2 voxels na ponta), ponta em folha; guarda em folha de bronze; cabo de mão e meia
    com amarração cruzada. Só para estudar coreografia; não é equipamento do jogo."""
    s = Vox()
    hy = 13
    for y in range(hy - 5, hy + 3):                                # cabo com amarração cruzada
        for k, xx in enumerate((x, x + 1)):
            s.box(xx, y, 0, xx, y, 1, wrap if (y + k) % 3 == 0 else grip)
    s.box(x, hy - 6, 0, x + 1, hy - 6, 1, BRONZE_D)                 # pomo
    s.box(x - 1, hy - 7, 0, x + 2, hy - 7, 1, guard)
    for dx in range(-2, 4):                                         # guarda em folha (oval achatada)
        for dz in range(-1, 3):
            if (dx - 0.5) ** 2 / 9.0 + (dz - 0.5) ** 2 / 4.0 <= 1.0:
                s.set(x + dx, hy + 3, dz, guard)
    s.box(x, hy + 4, 0, x + 1, hy + 4, 1, BRONZE_D)                 # colar
    for i in range(length):
        y = hy + 5 + i
        u = i / (length - 1)
        off = -int(round(2.0 * u * u))                              # curva para trás (para o dorso)
        tip = u > 0.86
        w = 3 if not tip else max(1, int(round(3 * (1 - u) / 0.14)))
        x0 = x - 1 + off
        for k in range(w):
            xx = x0 + k + (3 - w)                                  # a ponta afina pelo dorso (o fio continua reto)
            c = back if (k == 0 and w > 1) else (edge if k == w - 1 else body)
            s.box(xx, y, 0, xx, y, 1, c)
    return s, (x + 1.0, hy, 1.0)


# ----------------------------------------------------------------------------- volumes (rodada 04)
# Volumes APROXIMADOS para a validação de interseções das armas (viewer: WeaponCheck). Coordenadas no
# espaço do modelo, em voxels, ligadas à parte que as carrega. Cápsula = segmento a–b com raio r;
# esfera = só "a". Tags: head, hat, torso, back, leg, arm_l, arm_r, blade, handle, staff, bow, arrow, shield.

def vol(part, tag, a, r, b=None):
    d = {"part": part, "tag": tag, "a": [round(c, 2) for c in a], "r": r}
    if b is not None:
        d["b"] = [round(c, 2) for c in b]
    return d


def body_volumes(back=None, hat=False, skirt=False, tall=False):
    k = knee_y(tall)
    v = [vol("head", "head", (0, 33.5, 0.0), 8.6, (0, 37.0, 0.0)),
         vol("torso", "torso", (0, 13.0, 0.5), 6.0, (0, 22.0, 0.5))]
    for sgn, s in ((1, "l"), (-1, "r")):
        v += [vol(f"leg_{s}", "leg", (sgn * 3.5, HIP, 0.5), 2.2, (sgn * 3.6, k, 0.5)),
              vol(f"shin_{s}", "leg", (sgn * 3.6, k, 0.5), 2.5, (sgn * 3.8, 2.0, 1.0)),
              vol(f"arm_{s}", f"arm_{s}", (sgn * 6.5, SHOULDER, 0.5), 2.2, (sgn * ARM_X, ELBOW, 0.5)),
              vol(f"farm_{s}", f"arm_{s}", (sgn * ARM_X, ELBOW, 0.5), 2.2, (sgn * 7.3, 11.5, 0.8))]
        if skirt:
            v.append(vol(f"skirt_{s}", "leg", (sgn * 4.2, 4.5, 0.0), 3.6, (sgn * 3.6, 10.5, 0.0)))
    if back:
        v.append(vol("torso", "back", back[0], back[2], back[1]))
    if hat:
        v.append(vol("hat", "hat", (0, 42.5, 0.0), 4.6, (-1.0, 48.5, 0.0)))
        for i in range(8):
            a = 2 * math.pi * i / 8
            v.append(vol("hat", "hat", (8.2 * math.cos(a), 41.5, 8.0 * math.sin(a)), 2.0))
    return v


def sword_volumes(part="weapon", x=-7.5, length=22):
    return [vol(part, "handle", (x, 8.0, 1.0), 1.2, (x, 16.0, 1.0)),
            vol(part, "blade", (x, 18.0, 1.0), 1.5, (x, 18.0 + length - 1, 1.0))]


def longblade_volumes(part="weapon", x=-7.5, length=34):
    # rodada 05: lâmina curva (até 2 voxels para −X na ponta) → dois segmentos seguindo a curva
    mid = 18.0 + length * 0.55
    return [vol(part, "handle", (x, 7.0, 1.0), 1.1, (x, 16.0, 1.0)),
            vol(part, "blade", (x, 18.0, 1.0), 1.4, (x - 0.6, mid, 1.0)),
            vol(part, "blade", (x - 0.6, mid, 1.0), 1.4, (x - 2.0, 18.0 + length - 1, 1.0))]


def staff_volumes(part="weapon", x=-7.5, length=24):
    top = 3 + length
    return [vol(part, "staff", (x, 3.0, 1.0), 1.2, (x, top, 1.0)),
            vol(part, "staff", (x, top + 4.4, 1.0), 3.4)]


def bow_volumes(x=8.5):
    return [vol("bow", "bow", (x, 13.0, 3.0), 1.2, (x, 30.0, 1.0)),
            vol("bow", "bow", (x, 13.0, 3.0), 1.2, (x, -4.0, 1.0)),
            vol("arrow", "arrow", (9.5, 14.5, 0.0), 0.8, (9.5, 14.5, 17.0))]


def buckler_volumes(x=7.5):
    # rodada 05: raio 6,4 e centro abaulado até z = 7 → anel mais largo e esfera central mais funda
    v = [vol("shield", "shield", (x, 13.5, 4.6), 2.9)]
    for i in range(8):
        a = 2 * math.pi * i / 8
        v.append(vol("shield", "shield", (x + 4.3 * math.cos(a), 13.5 + 4.3 * math.sin(a), 3.8), 2.2))
    return v


def arrow(shaft=WOOD, head=STEEL, fletch=None, x=9, length=21):
    """Flecha ao longo de +Z, encaixada na corda do arco (parte 'arrow', filha de 'bow', pivô no punho).
    Haste de 1 voxel, ponta de aço em losango 3 × 3, penas coloridas em cruz na traseira."""
    a = Vox()
    hy = 13 + 1
    fletch = fletch or C("#e0644e")
    z0 = 0
    for z in range(z0, z0 + length):
        a.set(x, hy, z, shaft)
    a.set(x, hy, z0 + length, head)                     # ponta em losango
    a.set(x, hy, z0 + length + 1, head)
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        a.set(x + dx, hy + dy, z0 + length - 1, head)
    for z in range(z0 + 1, z0 + 5):                     # penas
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if not (z == z0 + 4 and dy != 0):
                a.set(x + dx, hy + dy, z, fletch)
    a.set(x, hy, z0, CREAM)                              # nock
    return a


# ----------------------------------------------------------------------------- montagem


def prune_specks(vox, min_size=4, label=""):
    """Rodada 05: remove fragmentos (componentes 6-conexos menores que min_size, além do principal) —
    pontinhos soltos que leem como ruído ou peças flutuando. Imprime o que removeu (nada some em silêncio).
    Detalhes finos intencionais devem ser modelados conectados (ver connect_path)."""
    cells = set(vox.v)
    seen, comps = set(), []
    for c in cells:
        if c in seen:
            continue
        stack, comp = [c], []
        seen.add(c)
        while stack:
            x, y, z = stack.pop()
            comp.append((x, y, z))
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                nb = (x + d[0], y + d[1], z + d[2])
                if nb in cells and nb not in seen:
                    seen.add(nb)
                    stack.append(nb)
        comps.append(comp)
    comps.sort(key=len, reverse=True)
    removed = [c for comp in comps[1:] if len(comp) < min_size for c in comp]
    for c in removed:
        del vox.v[c]
    if removed:
        print(f"    limpeza {label}: {len(removed)} voxel(s) solto(s) removido(s)")
    return vox


def connect_path(vox, points, col_of):
    """Liga pontos em sequência com voxels 6-conexos (escada), para linhas finas (pena, gavinha) não virarem
    pontilhado. col_of(i) dá a cor do i-ésimo segmento."""
    for i in range(len(points) - 1):
        (x0, y0, z0), (x1, y1, z1) = points[i], points[i + 1]
        x, y, z = x0, y0, z0
        vox.set(x, y, z, col_of(i))
        while (x, y, z) != (x1, y1, z1):
            if y != y1:
                y += 1 if y1 > y else -1
            elif x != x1:
                x += 1 if x1 > x else -1
            else:
                z += 1 if z1 > z else -1
            vox.set(x, y, z, col_of(i))


def finish(name, parts, head_top, meta_extra=None, alts=None):
    """parts: [(nome, Vox, pivô, pai)]. Proporção = (topo do cabelo + 1) / (topo do cabelo - NECK + 1),
    sem chapéu, pena ou pontas de mecha (anotado em meta.note)."""
    rig = Rig(name, VS, "biped")
    for (n, vox, piv, par) in parts:
        prune_specks(vox, label=f"{name}/{n}")
        for k, alt in ((alts or {}).get(n) or {}).items():
            prune_specks(alt, label=f"{name}/{n}:{k}")
        rig.part(n, vox, piv, par, alts=(alts or {}).get(n))
    head_h = head_top - NECK + 1
    total = head_top + 1
    faces = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "bodyHeightVoxels": total, "legFraction": round(HIP / total, 3), "faces": faces,
                "footPlant": True, "joints": True, "ankle": ANKLE,
                "note": "cabeças = altura até o topo do cabelo / altura da cabeça com cabelo; chapéu e pontas fora"}
    if meta_extra:
        rig.meta.update(meta_extra)
    b = rig.all_voxels().bounds()
    print(f"  {name}: {b[3]-b[0]+1}x{b[4]-b[1]+1}x{b[5]-b[2]+1} vox, {rig.meta['headsTall']} cabeças, "
          f"{(b[4]-b[1]+1) * VS:.2f} u, {faces} faces")
    return rig
