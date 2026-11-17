"""Direção E — Fábula Voxel: NPCs humanoides (rodada 06).

cartografa — cartógrafa itinerante: adulta viajante que registra trilhas, plantas e marcos. NÃO é uma
             quarta heroína: proporção, cabeça, rosto, roupa e silhueta são próprios.

Contrato aplicado (docs/art/contrato-de-qualidade.md, "NPCs humanoides futuros"):
  * proporção na faixa 2,0–3,0 cabeças: aqui ~2,8 (heróis: 2,35–2,60) — cabeça menor (crânio 14 × 14),
    pernas e braços mais longos (quadril em y = 14, pescoço em y = 29; heróis 11 e 26);
  * mesma linguagem da E: massas arredondadas, voxel 1/40 u, rosto em PLACA plana, sem nariz, brilho do
    olho do lado da luz, blush; sombra pintada em bandas (charlib_e.shade);
  * olho NOVO (2 × 3 + traço de pálpebra) atrás de óculos redondos de latão — nenhum herói usa óculos e o
    olho dos heróis é 3 × 5: a diferença é de estrutura, não de cor;
  * silhueta: casaco de viagem com abas atrás (partes skirt_l/skirt_r, seguem as pernas), gola alta, tubo
    de mapas na diagonal das costas (ponta sobre o ombro ESQUERDO), coque lateral com lápis, bolsa no
    quadril DIREITO (o aventureiro leva o talabarte do ombro direito; aqui a alça vem do esquerdo).
Rig: o mesmo bípede articulado dos heróis (torso, head, arm/farm, leg/shin, pivôs no rig.json), então
locomoção e IK funcionam sem mudar o Animator; gesto pelo perfil assets/anim/cartografa.json.
"""
import math

import charlib_e as L
import chars_b as B
import chars_d as D
from charlib_e import BLUSH, LASH, PUPIL, EYE_HI, decal, shade
from voxlib import C, Rig, Vox, exposed_faces

VS = 1 / 40
HIP = 14             # pivô das pernas
KNEE = 9             # pivô das canelas (logo acima do cano da bota)
NECK = 29            # pivô da cabeça
SHOULDER = 26.5      # pivô dos braços
ELBOW = 20           # pivô dos antebraços
SH_X = 5.8           # x do ombro
EL_X = 6.6           # x do cotovelo
FACE_Z = 5           # plano do rosto (crânio menor que o dos heróis)
EYE_Y = NECK + 4     # linha de baixo dos olhos
ANKLE = 1.5

P = B.P
SKIN = P("#dca27c", ks=0.55)          # pele morena-clara (os heróis: pêssego claro)
HAIR = P("#6a4256", kl=0.5)                  # castanho-ameixa (v1 escuro demais: lia como capacete)
COAT = P("#4d6b8e")                    # azul-ardósia
COAT_D = P("#3a516e")
SHIRT = P("#efe2c4")                   # pergaminho
VEST = P("#b98a52")                    # colete de couro claro
PANTS = P("#776746")                   # oliva-terra
BOOT = P("#5c3a2b")
BOOT_CUFF = P("#a8774a")
GLOVE = P("#b07a46")
SATCHEL = P("#c48a3c")                 # couro ocre
TUBE = P("#8e3f36")                    # couro vermelho-terroso
BRASS = C("#d6a64a", "metal")
BRASS_D = C("#a77a34", "metal")
RIM = C("#7a5230", "metal")            # aro dos óculos: bronze escuro (v1 em latão claro dominava o rosto)
RIM_HI = C("#b88a4a", "metal")
PARCH = P("#f3e6c8")
PENCIL = P("#f0c23e")
ERASER = C("#ef8f8f")
IRIS = C("#8a5a2a")                   # âmbar (heróis: verde-água, azul, violeta)


# ----------------------------------------------------------------------------- corpo

def torso():
    t = Vox()

    def prof(y):
        if y <= HIP + 2:                                 # casaco em linha A: abre da cintura até a barra
            k = (HIP + 2 - y) / 6.0
            return (0, 0.4, 4.7 + 1.9 * k, 3.5 + 1.2 * k)
        u = (y - HIP - 2) / (NECK - 3 - HIP)
        rx = 4.7 + 0.6 * math.sin(math.pi * min(1.0, u * 0.9)) - 0.7 * max(0.0, u - 0.8) / 0.2
        return (0, 0.4 + 0.25 * math.sin(math.pi * min(1.0, u * 1.2)), rx, 3.5 + 0.4 * math.sin(math.pi * min(1.0, u)))

    B.stack(t, 10, NECK - 1, prof, COAT, p=2.7)
    for k in [k for k in t.v if k[1] == NECK - 1 and abs(k[0] + 0.5) > 3.6]:
        del t.v[k]                                       # ombro caído (mais esguia que os heróis)
    # frente aberta do casaco: camisa de pergaminho + colete de couro
    for (x, y, z) in list(t.v):
        if z >= 2 and y >= HIP - 1:
            ax = abs(x + 0.5)
            if ax <= 1.6 and y <= NECK - 2:
                t.v[(x, y, z)] = SHIRT
            elif ax <= 3.2 and HIP <= y <= NECK - 4:
                t.v[(x, y, z)] = VEST
        if y <= 12 and z >= 2 and abs(x + 0.5) < 1.4:
            del t.v[(x, y, z)]                           # fenda da frente do casaco
    for (x, y, z) in list(t.v):                          # lapelas: borda escura em V ao longo da abertura
        if z >= 2 and HIP + 3 <= y <= NECK - 2:
            w = 1.6 + 1.7 * (y - HIP - 3) / (NECK - HIP - 5)
            if w <= abs(x + 0.5) <= w + 1.0:
                t.v[(x, y, z)] = COAT_D
    # barra de baixo um tom mais escuro; costura lateral
    for k in [k for k in t.v if k[1] == 10]:
        t.v[k] = COAT_D
    # gola alta virada (atrás e dos lados; a frente fica livre para o rosto)
    for y in (NECK - 1, NECK, NECK + 1):
        for x in range(-5, 5):
            for z in range(-4, 3):
                r = ((x + 0.5) / 4.6) ** 2 + ((z + 0.4) / 3.6) ** 2
                if 0.62 <= r <= 1.0 and not (z >= 1 and abs(x + 0.5) < 2.6):
                    t.set(x, y, z, COAT_D if y == NECK + 1 else COAT)
    shade(t)
    # cinto
    for (x, y, z) in [k for k in t.v if k[1] in (HIP + 1, HIP + 2)]:
        t.v[(x, y, z)] = L.LEATHER_D
    zf = L.front_z(t, 0, HIP + 1)
    t.set(-1, HIP + 1, zf + 1, BRASS)
    t.set(0, HIP + 1, zf + 1, BRASS)
    t.set(-1, HIP + 2, zf + 1, BRASS_D)
    t.set(0, HIP + 2, zf + 1, BRASS_D)
    # alça diagonal: ombro ESQUERDO -> quadril direito (por cima do colete)
    for i in range(12):
        x, y = 4 - i, NECK - 3 - i
        for xx in (x, x - 1):
            z = L.front_z(t, xx, y)
            if z is not None:
                t.set(xx, y, z + 1, L.LEATHER)
    # bolsa de campo no quadril direito: aba, fivela de latão, lápis no bolso
    s = Vox()
    B.rbox(s, -9, HIP - 2, -2, -6, HIP + 3, 3, SATCHEL)
    s.box(-10, HIP + 1, -2, -10, HIP + 3, 3, B.shadow(SATCHEL))
    s.box(-9, HIP + 3, -2, -6, HIP + 3, 3, B.light(SATCHEL))
    s.set(-10, HIP + 1, 0, BRASS)
    s.set(-10, HIP + 1, 1, BRASS)
    s.set(-7, HIP + 4, 1, PENCIL)
    s.set(-7, HIP + 5, 1, PENCIL)
    s.set(-7, HIP + 6, 1, C("#3a2a2a"))
    t.merge(s)
    return t


def tails():
    """Abas do casaco atrás das pernas (y 5..13): cada metade segue a perna do mesmo lado (Animator)."""
    out = []
    for side in (1, -1):
        v = Vox()
        for y in range(4, 13):
            k = (13 - y) / 9.0                               # 0 na cintura, 1 na barra
            for x in range(0, 8):
                xx = x if side > 0 else -1 - x
                if x > 4.4 + 2.4 * k:                        # abre em sino para os lados
                    continue
                for z in range(-7, -1):
                    if z < -3 - int(3.2 * k) or (x > 3.6 + 1.2 * k and z > -3):
                        continue
                    v.set(xx, y, z, COAT_D if y <= 5 else COAT)
        # recorte em V no meio (fenda das abas) e ponta arredondada
        for k2 in [k2 for k2 in v.v if abs(k2[0] + 0.5) < 0.6 + (13 - k2[1]) * 0.12]:
            del v.v[k2]
        shade(v)
        out.append(v)
    return out          # (esquerda, direita)


def arms():
    a = Vox()
    B.sblob(a, SH_X + 0.3, SHOULDER - 0.6, 0.4, 2.3, 2.3, 2.4, COAT, p=2.3)          # ombro
    B.stack(a, ELBOW, int(SHOULDER) - 1, lambda y: (EL_X - 0.2 + 0.1 * (y - ELBOW) / 6, 0.4, 1.8, 1.9), COAT, p=2.4)
    B.stack(a, ELBOW - 2, ELBOW - 1, lambda y: (EL_X, 0.4, 2.2, 2.3), SHIRT, p=2.5)   # punho dobrado
    B.stack(a, 16, ELBOW - 3, lambda y: (EL_X + 0.1, 0.5, 1.7, 1.8), GLOVE, p=2.5)    # luva de cano longo
    B.sblob(a, EL_X + 0.3, 15.0, 0.9, 2.1, 2.1, 2.2, GLOVE, p=2.6)                    # mão
    a.set(EL_X - 2, 15, 3, GLOVE)                                                       # polegar
    for k in [k for k in a.v if k[0] < 4]:
        del a.v[k]
    shade(a)
    return a, a.flipped_x()


def legs():
    l = Vox()
    cx, cz = 3.0, 0.4
    B.stack(l, 9, HIP - 1, lambda y: (cx, cz, 2.0, 2.1), PANTS, p=2.6)                # coxa longa e fina
    B.stack(l, 2, 8, lambda y: (cx + 0.1, cz, 2.3, 2.4), BOOT, p=2.8)                 # bota de cano alto
    B.sblob(l, cx + 0.2, 1.4, cz + 1.3, 2.6, 2.0, 3.8, BOOT, p=2.6)                    # pé
    for y in (8, 9):
        L.ring(l, y, y, 2.7, 2.8, BOOT_CUFF, cz=cz, cx=cx + 0.1, p=2.8)                # cano dobrado
    for k in [k for k in l.v if k[1] < 0 or k[0] < 1]:
        del l.v[k]
    for k in [k for k in l.v if k[1] == 0]:
        l.v[k] = L.LEATHER_D
    shade(l)
    return l, l.flipped_x()


def map_tube():
    """Tubo de mapas na diagonal das costas: fundo perto do quadril direito, ponta acima do ombro
    esquerdo; tampas de latão, alça e um mapa enrolado aparecendo em cima."""
    v = Vox()
    a, b = (-4.6, 14.0, -6.0), (10.4, 31.0, -4.8)         # ponta sai ao lado da cabeça (silhueta frontal)
    n = 40
    for i in range(n + 1):
        t = i / n
        c = tuple(a[k] + (b[k] - a[k]) * t for k in range(3))
        col = BRASS if t < 0.07 or t > 0.93 else (B.shadow(TUBE) if abs(t - 0.35) < 0.03 or abs(t - 0.7) < 0.03 else TUBE)
        B.sblob(v, c[0], c[1], c[2], 2.0, 1.2, 2.0, col, p=2.2)
    top = b
    for i in range(5):                                   # mapa enrolado saindo da ponta
        B.sblob(v, top[0] + 0.85 * i, top[1] + 1.0 + 0.95 * i, top[2] + 0.1 * i, 1.3, 0.8, 1.3, PARCH, p=2.2)
    shade(v)
    return v


def compass():
    """Bússola de bolso de latão (mão direita; o gesto a ergue para consultar). Disco 4 × 4 de frente,
    vidro escuro, agulha vermelha e argola — legível de 3/4 na câmera de exploração (v1 tinha 3 voxels)."""
    v = Vox()
    hx, hy, hz = -EL_X - 0.3, 15, 1
    for x in range(-2, 3):
        for y in range(-2, 3):
            if (x) ** 2 + (y) ** 2 <= 5:
                v.set(round(hx) + x, hy + y, hz + 2, BRASS if x * x + y * y >= 3 else C("#2f3a4a"))
    v.set(round(hx), hy, hz + 3, C("#e85a4a"))           # agulha
    v.set(round(hx), hy + 1, hz + 3, C("#f3e6c8"))
    v.set(round(hx), hy + 3, hz + 2, BRASS_D)             # argola
    v.set(round(hx) + 2, hy - 1, hz + 3, BRASS_D)         # dobradiça da tampa
    return v


# ----------------------------------------------------------------------------- cabeça

def skull():
    v = Vox()
    B.sblob(v, 0, NECK + 7.3, 0.5, 7.2, 7.3, 6.5, SKIN, p=2.4, p_lo=2.3)   # queixo arredondado (v2 era cubo)
    for k in [k for k in v.v if k[2] > FACE_Z]:
        del v.v[k]
    for x in range(-6, 6):
        for y in range(NECK + 1, NECK + 12):
            f = L.front_z(v, x, y)
            if f is not None and 2 <= f < FACE_Z and abs(x + 0.5) <= (3.6 if y <= NECK + 1 else 4.6 if y == NECK + 2 else 5.6):   # queixo afinado (06.1)
                for z in range(f, FACE_Z + 1):
                    v.set(x, y, z, SKIN)
    for ex in (7, -8):
        v.box(ex, NECK + 4, -1, ex, NECK + 6, 0, SKIN)
    for x in (-1, 0):                                    # queixo um voxel mais fino que o dos heróis
        pass
    shade(v, hi=0.62, lo=-0.40, low2=-0.9)
    return v


def hair(sk):
    """Meio-preso: franja diagonal partida à direita, mecha grossa emoldurando o rosto à esquerda, três
    mechas largas na coroa (quebram a cúpula), coque lateral ALTO à direita (aparece de frente), lápis
    atravessado, nuca com o cabelo solto até o pescoço. 3 tons + faixa de brilho + sulcos atrás."""
    def lim(x, z):
        ax = x + 0.5
        if z >= 1:
            if ax > 4.6 or ax < -5.0:
                return 6.5 if ax > 0 else 8.5           # têmporas: mais cabelo à esquerda
            return 11.8 - 0.30 * (ax + 4.0)             # franja diagonal: alta à direita, baixa à esquerda (≥ NECK+9,4)
        return 2.5
    h = Vox()
    for (x, y, z) in D.dilate(sk, 1.5):
        if (x, y, z) in sk.v or y < NECK:
            continue
        if y >= NECK + lim(x, z):
            h.set(x, y, z, HAIR)
    L.hair_locks(h, HAIR, [
        ((-3.0, NECK + 14.2, 3.0), (1.0, NECK + 13.4, 6.4), (4.6, NECK + 10.6, 6.0), 2.0, 1.3),  # franja varrida
        ((5.6, NECK + 11.0, 2.0), (7.6, NECK + 6.0, 3.0), (6.6, NECK + 1.5, 2.4), 2.0, 1.1),   # mecha do rosto
        ((-1.5, NECK + 14.6, 2.0), (1.2, NECK + 15.6, -0.6), (3.8, NECK + 13.8, -4.6), 2.2, 1.4),  # coroa: volume acima da risca
        ((4.4, NECK + 12.5, -1.0), (6.4, NECK + 10.0, -3.6), (5.6, NECK + 4.0, -5.0), 1.9, 1.2),
        ((-1.0, NECK + 5.0, -6.0), (0.0, NECK + 1.5, -6.6), (0.4, NECK - 1.5, -5.4), 2.0, 1.2),  # rabo solto curto
    ])
    B.sblob(h, -8.4, NECK + 12.6, -1.4, 2.7, 2.7, 2.7, HAIR, p=2.3)                             # coque lateral: sai do contorno
    D.lock(h, (-5.0, NECK + 13.6, -0.6), (-6.8, NECK + 13.6, -1.0), (-8.0, NECK + 12.8, -1.4), 1.6, 1.6, HAIR)   # mecha que sobe ao coque
    for k in [k for k in h.v if k in sk.v or (k[2] >= FACE_Z - 1 and NECK + 1 <= k[1] <= NECK + 9 and -5 <= k[0] <= 4)]:
        del h.v[k]                                       # nada de cabelo sobre olhos/sobrancelhas
    tmp = h.copy().merge(sk)
    D.fill_pits(tmp, HAIR, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == HAIR)
    shade(h, hi=0.50, lo=-0.25, low2=-2.0)
    hl = B.light(HAIR, 1.1)                               # brilho discreto (v2 em 1,8 deixava o topo rosado)
    for (x, y, z), c in list(h.v.items()):
        if y == NECK + 14 and z >= 2 and (x, y + 1, z) not in h.v and c in (HAIR, B.RAMPS[HAIR][1]):
            h.v[(x, y, z)] = hl
    groove = B.RAMPS[HAIR][0]
    for (x, y, z), c in list(h.v.items()):                # sulcos largos nas costas
        if z <= -2 and y < NECK + 11:
            th = math.atan2(x + 0.5, -(z + 0.5))
            f = th / 0.9 - math.floor(th / 0.9)
            if f < 0.14 and c in (HAIR, B.RAMPS[HAIR][1]):
                h.v[(x, y, z)] = groove
    # lápis amarelo atravessando o coque: linha 6-conexa (a v4 em diagonal solta perdia pedaços na limpeza)
    pts = [(-6, NECK + 16, 1), (-8, NECK + 15, 0), (-10, NECK + 13, -1), (-12, NECK + 11, -2)]
    n = [0]

    def col(i):
        return ERASER if i == 0 else (C("#3a2a2a") if i == len(pts) - 2 else PENCIL)
    L.connect_path(h, pts, col)
    h.set(-6, NECK + 16, 1, ERASER)
    h.set(-12, NECK + 11, -2, C("#3a2a2a"))
    return h


def glasses(v):
    """Óculos redondos de bronze EMPURRADOS PARA CIMA, apoiados no cabelo acima da franja (rodada 06, v3):
    acessório de leitura que entra na silhueta frontal sem cobrir o olhar (v1/v2 sobre os olhos: o aro
    dominava o rosto). Cada lente = anel de 3 × 3 com miolo de vidro claro."""
    top = {}
    for (x, y, z) in v.v:
        if z >= 2:
            top[(x, z)] = max(top.get((x, z), -99), y)
    for cx in (3, -4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = cx + dx, 3 + dz
                y = top.get((x, z), NECK + 14) + 1
                ring = abs(dx) + abs(dz) > 0
                v.set(x, y, z, RIM if ring else C("#cfe6ee", "metal"))
                if ring and (dx, dz) == (0, 1):
                    v.set(x, y, z, RIM_HI)
    for x in range(-2, 2):                                    # ponte
        y = top.get((x, 3), NECK + 14) + 1
        v.set(x, y, 3, RIM)


def face(v, expr):
    """Rosto da cartógrafa (pintado DEPOIS do shade): olho 2 × 3 âmbar + traço de pálpebra, sobrancelha
    reta e firme, boca pequena com canto erguido. Brilho do lado da luz (esquerda da tela) nos dois olhos."""
    eyes = ((2, 3), (-4, -3))
    ey = EYE_Y
    for (a, b) in eyes:
        outer = b if a > 0 else a
        if expr == "joy":                                 # meia-lua fechada
            decal(v, a, ey + 2, LASH)
            decal(v, b, ey + 2, LASH)
            decal(v, outer, ey + 1, LASH)
            continue
        rows = (ey + 1, ey + 2) if expr == "determined" else (ey, ey + 1, ey + 2) if expr != "surprise" else (ey - 1, ey, ey + 1, ey + 2)
        for y in rows:
            decal(v, a, y, IRIS)
            decal(v, b, y, PUPIL if y >= ey + 1 else IRIS)
            if expr == "surprise":
                decal(v, a, y, EYE_HI if y in (ey - 1, ey + 2) else IRIS)
        decal(v, a, rows[-1], EYE_HI)                    # brilho no mesmo lado (luz vem da esquerda da tela)
        lid = rows[-1] + 1
        decal(v, a, lid, LASH)
        decal(v, b, lid, LASH)
        if expr != "surprise":
            decal(v, outer + (1 if outer > 0 else -1), lid, LASH)   # traço externo da pálpebra
    # sobrancelhas — atuação "analítica" (rodada 06, v2): na expressão determinada ela AVALIA — uma sobrancelha
    # erguida, a outra baixa (antes as duas só desciam e a expressão quase igualava a neutra)
    browc = B.shadow(HAIR, 1.1)
    for (a, b) in eyes:
        inner = a if a > 0 else b
        if expr == "surprise":
            by = ey + 6
        elif expr == "determined":
            by = ey + 6 if a > 0 else ey + 4             # esquerda erguida, direita baixa
        elif expr == "joy":
            by = ey + 5
        else:
            by = ey + 5
        for x in (a, b):
            decal(v, x, by, browc)
    # boca
    my = NECK + 1
    if expr == "joy":                                    # sorriso largo, cantos erguidos
        for x in (-2, -1, 0, 1):
            decal(v, x, my + 1, L.MOUTH)
        decal(v, -3, my + 2, L.MOUTH)
        decal(v, 2, my + 2, L.MOUTH)
        for x in (-1, 0):
            decal(v, x, my, L.MOUTH_IN)
    elif expr == "surprise":                             # boca em O
        for x in (-1, 0):
            decal(v, x, my, L.MOUTH_IN)
            decal(v, x, my + 1, L.MOUTH_IN)
        decal(v, -2, my, L.MOUTH)
        decal(v, 1, my, L.MOUTH)
    elif expr == "determined":                           # boca puxada de lado: avaliando
        for x in (0, 1, 2):
            decal(v, x, my + 1, L.MOUTH)
        decal(v, -1, my + 1, B.shadow(SKIN, 1.2))
    else:
        decal(v, -1, my + 1, L.MOUTH)
        decal(v, 0, my + 1, L.MOUTH)
        decal(v, 1, my + 2, L.MOUTH)                    # canto erguido (sorriso discreto, assimétrico)
    for x in (4, 5, -5, -6):
        decal(v, x, ey - 1, BLUSH)


def head(expr, sk, hr):
    v = Vox()
    v.merge(sk)
    face(v, expr)
    v.merge(hr, overwrite=False)
    glasses(v)
    return v


# ----------------------------------------------------------------------------- rig

def volumes():
    vol = L.vol
    return [
        vol("head", "head", (0, NECK + 7.0, 0.5), 7.4),
        vol("torso", "torso", (0, HIP + 1, 0.4), 5.2, (0, NECK - 2, 0.4)),
        vol("pack", "back", (-4.0, 16.0, -6.0), 2.2, (10.0, 31.0, -4.8)),
        vol("leg_l", "leg", (3.0, 1.0, 0.8), 2.4, (3.0, HIP - 1, 0.4)),
        vol("leg_r", "leg", (-3.0, 1.0, 0.8), 2.4, (-3.0, HIP - 1, 0.4)),
        vol("arm_l", "arm_l", (SH_X, SHOULDER - 0.5, 0.4), 2.0, (EL_X, ELBOW, 0.4)),
        vol("farm_l", "arm_l", (EL_X, ELBOW, 0.4), 1.9, (EL_X + 0.3, 15.0, 0.9)),
        vol("arm_r", "arm_r", (-SH_X, SHOULDER - 0.5, 0.4), 2.0, (-EL_X, ELBOW, 0.4)),
        vol("farm_r", "arm_r", (-EL_X, ELBOW, 0.4), 1.9, (-EL_X - 0.3, 15.0, 0.9)),
    ]


def cartografa():
    sk = skull()
    hr = hair(sk)
    heads = {e: head(e, sk, hr) for e in ("neutral", "joy", "determined", "surprise")}
    t = torso()
    tl, tr = tails()
    arm_l, arm_r = arms()
    leg_l, leg_r = legs()
    ua_l, fa_l = L.split_limb(arm_l, ELBOW, (EL_X, ELBOW, 0.4), 1.5, COAT)
    ua_r, fa_r = L.split_limb(arm_r, ELBOW, (-EL_X, ELBOW, 0.4), 1.5, COAT)
    th_l, sh_l = L.split_limb(leg_l, KNEE, (3.1, KNEE, 0.4), 1.7, PANTS)
    th_r, sh_r = L.split_limb(leg_r, KNEE, (-3.1, KNEE, 0.4), 1.7, PANTS)
    tube = map_tube()
    for k in [k for k in tube.v if k in t.v]:
        del tube.v[k]
    parts = [
        ("torso", t, (0, HIP, 0.4), None),
        ("head", heads["neutral"], (0, NECK, 0.5), "torso"),
        ("pack", tube, (2.5, 28.0, -6.2), "torso"),
        ("skirt_l", tl, (2.5, 13.0, -2.5), "torso"),   # abas: seguem 78% da perna (Animator.secondary)
        ("skirt_r", tr, (-2.5, 13.0, -2.5), "torso"),
        ("arm_l", ua_l, (SH_X, SHOULDER, 0.4), "torso"),
        ("farm_l", fa_l, (EL_X, ELBOW, 0.4), "arm_l"),
        ("arm_r", ua_r, (-SH_X, SHOULDER, 0.4), "torso"),
        ("farm_r", fa_r, (-EL_X, ELBOW, 0.4), "arm_r"),
        ("leg_l", th_l, (3.0, HIP, 0.4), None),
        ("shin_l", sh_l, (3.1, KNEE, 0.4), "leg_l"),
        ("leg_r", th_r, (-3.0, HIP, 0.4), None),
        ("shin_r", sh_r, (-3.1, KNEE, 0.4), "leg_r"),
        ("tool", compass(), (-EL_X - 0.3, 15.0, 1.0), "farm_r"),
    ]
    alts = {"head": {e: heads[e] for e in ("joy", "determined", "surprise")}}
    rig = Rig("Cartógrafa (NPC, E)", VS, "biped")
    for (n, vox, piv, par) in parts:
        L.prune_specks(vox, label=f"cartografa/{n}")
        for _, alt in ((alts.get(n) or {}).items()):
            L.prune_specks(alt, label=f"cartografa/{n}:alt")
        rig.part(n, vox, piv, par, alts=alts.get(n))
    head_top = max(k[1] for k in hr.v if k[0] > -1 or k[2] > -2)   # topo do cabelo SEM coque/lápis
    head_h = head_top - NECK + 1
    total = head_top + 1
    faces = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "bodyHeightVoxels": total, "legFraction": round(HIP / total, 3), "faces": faces,
                "footPlant": True, "joints": True, "ankle": ANKLE, "npc": True,
                "weaponMode": "tool", "weaponProfile": "cartografa",
                "hands": {"r": [-EL_X - 0.3, 15.0, 1.0], "l": [EL_X + 0.3, 15.0, 1.0]},
                "volumes": volumes(),
                "note": "NPC (rodada 06): cabeças medidas sem coque/lápis; proporção própria (contrato: NPCs 2,0–3,0)"}
    b = rig.all_voxels().bounds()
    print(f"  cartografa: {b[3]-b[0]+1}x{b[4]-b[1]+1}x{b[5]-b[2]+1} vox, {rig.meta['headsTall']} cabeças, "
          f"{(b[4]-b[1]+1) * VS:.2f} u, {faces} faces")
    return rig


def build(out):
    cartografa().save(f"{out}/characters/npc/cartografa")
