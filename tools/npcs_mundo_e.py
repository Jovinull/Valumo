#!/usr/bin/env python3
"""NPCs de ofício do mundo procedural (laboratório de conteúdo, estudo pós-rodada 12): Brásia, Quelmo e Arvel.

Moram nas construções da biblioteca que apareciam vazias na v2 (P76): a ferreira na oficina com forja, o feirante na
barraca de feira, o caçador na cabana do caçador. A herbalista já existe (Índira) e o guardião também (Bramor):
nenhum dos dois é recriado.

Mesmo esqueleto, rosto e construtor dos NPCs da vila (`npcs_vila_e.npc`), mesma ALTURA NARRATIVA dos NPCs de ofício
(0,75 u por metro, reamostrando o rig pronto com `npcs_oficios_e.scale_vox`), e as mesmas regras da E. O que separa os
três é FORMA, não cor:

  brasia — ferreira, 1,72 m: coque alto preso no topo da cabeça, avental de couro que cobre o tronco inteiro com alças,
           mangas arregaçadas (antebraços à mostra), LUVAS grandes de couro, martelo no cinto. Atuação firme.
  quelmo — feirante, 1,62 m: boina larga caída para um lado, colete aberto sobre a camisa, faixa diagonal no peito,
           avental curto de bolsos e duas pochetes no cinto. Atuação brincalhona.
  arvel  — caçador, 1,84 m: gorro de pele com abas, MANTO curto de pele nos ombros e capinha nas costas, bolsa de caça
           com alça cruzada, rolo de corda no quadril, botas altas. Atuação calma.

Cada um tem um perfil próprio em assets/anim/npc_<papel>.json: `emote` = o aceno compartilhado e `oficio` = um gesto
curto do papel (punho na palma; arrumar a mercadoria; conferir o equipamento), tocado pelo clipe ATTACK do sistema
existente (`"attack": "oficio"`). Nenhum sistema de jogo novo.

Saída: assets/characters/npc/<chave>/ (gerado; não edite à mão).
"""
from pathlib import Path

import chars_b as B
import charlib_e as L
import npcs_oficios_e as O
import npcs_vila_e as T
from voxlib import C, Rig, Vox, exposed_faces

P = B.P
VS = 1 / 40
UNITS_PER_METER = 0.75

PROFILES = {
    "brasia": dict(title="Brásia (NPC, E)", meters=1.72, role="ferreira", profile="npc_ferreira",
        skin=P("#cf9474", ks=0.6), hair=P("#5b2e2a", kl=0.8), tip=None, coat=P("#e2d3bb"), coat_d=P("#b9a88f"),
        shirt=P("#e9dcc5"), vest=P("#e2d3bb"), pants=P("#3d3843"), boot=P("#3a2d2a"), cuff=P("#6a4636"),
        iris=C("#7c5a2c"), ink=C("#2e2224"), lip=C("#b06a62"), lip_d=C("#8a4e4a"),
        collar="round", belt="buckle", charm=None, tails=None, hair_style="short", hair_bottom=T.NECK + 6, crown=1.5,
        flare=0.0, fringe="swept", front_bottom=T.NECK, act_style="confident", freckles=False, ears=False, ribbon=None,
        acting={"torsoPitch": -2, "hipRoll": 3, "sway": 0.75, "emoteTempo": 0.95},
        note="Ferreira; coque alto preso, avental de couro com alças cruzadas nas costas, mangas arregaçadas, luvas de "
             "couro e martelo no bolso do avental"),
    "quelmo": dict(title="Quelmo (NPC, E)", meters=1.62, role="feirante", profile="npc_feirante",
        skin=P("#d9a888"), hair=P("#6b4a34", kl=0.85), tip=None, coat=P("#c48a3a"), coat_d=P("#94652a"),
        shirt=P("#efe2c8"), vest=P("#c48a3a"), pants=P("#5a4a5e"), boot=P("#5a3f32"), cuff=P("#8a5a3a"),
        iris=C("#4f6a52"), ink=C("#2c2420"), lip=C("#a8645a"), lip_d=C("#6f4038"),
        collar="v", belt="sash", charm=None, tails="short", hair_style="short", hair_bottom=T.NECK + 5, crown=1.8,
        flare=0.6, fringe="curtain", front_bottom=T.NECK, act_style="playful", freckles=False, ears=False, ribbon=None,
        acting={"headRoll": 4, "hipRoll": -3, "sway": 1.25, "emoteTempo": 0.85},
        note="Feirante; boina larga caída para a esquerda com pena, casaco aberto, faixa diagonal que dá a volta no ombro, "
             "avental curto e bolsa de moedas no cinto", beard="trim"),
    "arvel": dict(title="Arvel (NPC, E)", meters=1.84, role="cacador", profile="npc_cacador",
        skin=P("#b88066"), hair=P("#2f2a2c", kl=0.75), tip=None, coat=P("#5f5b3f"), coat_d=P("#46432e"),
        shirt=P("#c9b89a"), vest=P("#5f5b3f"), pants=P("#4a3f35"), boot=P("#3e3029"), cuff=P("#6b5a44"),
        iris=C("#5a6e4a"), ink=C("#2a2626"), lip=C("#946058"), lip_d=C("#5f3d38"),
        collar="high", belt="buckle", charm=None, tails=None, hair_style="short", hair_bottom=T.NECK + 4, crown=1.3,
        flare=0.0, fringe="swept", front_bottom=T.NECK + 1, act_style="sweet", freckles=False, ears=False, ribbon=None,
        acting={"headPitch": 3, "torsoPitch": 2, "sway": 0.7, "emoteTempo": 1.2},
        note="Caçador; gorro de pele com abas, gola de pele, capinha nas costas, corda enrolada a tiracolo, bolsa de "
             "caça nas costas e botas altas com punho de pele", beard="trim"),
}

LEATHER, LEATHER_D, LEATHER_L = P("#8a5a3c"), P("#6a4230"), P("#a9744c")
IRON = C("#5d4a5a", "metal")
IRON_L = C("#8d8494", "metal")
WOOD = P("#a8713f")
FUR, FUR_D, FUR_L = P("#7b6a58"), P("#5c4e42"), P("#ddd0b8")
ROPE, ROPE_D = P("#d6c08f"), P("#a8915f")
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def _part(rig, name):
    return next(v for n, v, p, par in rig.parts if n == name)


def _head_top(rig):
    return max(k[1] for k in _part(rig, "head").v)


def shell(solid, region, thick=2):
    """Casca colada à superfície: camadas de 6 vizinhos crescendo de `solid` para fora, só onde region(x, y, z).
    Cada célula da primeira camada encosta numa célula do sólido: chapéu sem faixa de ar sobre o cabelo."""
    have = set(solid.v)
    layer = {c for c in have if region(*c)}
    out = []
    for _ in range(thick):
        nxt = set()
        for (x, y, z) in layer:
            for (dx, dy, dz) in N6:
                q = (x + dx, y + dy, z + dz)
                if q not in have and q not in nxt and region(*q):
                    nxt.add(q)
        out.append(sorted(nxt))
        have |= nxt
        layer = nxt
    return out


# ----------------------------------------------------------------------------- Brásia (ferreira)

def dress_brasia(rig, body, s):
    """Avental de couro na frente (da barra ao peito), peitilho, alças que sobem pelos ombros e se CRUZAM nas costas,
    laço atrás na cintura, bolso com o martelo, mangas arregaçadas e luvas de couro grandes. Tudo dentro de |x| <= 4:
    o braço pendurado (x de 5 a 9) nunca passa pelo avental."""
    for (x, y, z), c in list(body.v.items()):                   # avental: frente, da barra até o peito
        ax = abs(x + 0.5)
        if 10 <= y <= T.NECK - 6 and z >= 1 and ax <= 4.6:
            body.v[(x, y, z)] = LEATHER_D if y == 10 else LEATHER
        elif T.NECK - 6 < y <= T.NECK - 3 and z >= 1 and ax <= 2.6:
            body.v[(x, y, z)] = LEATHER                          # peitilho
    for y in range(T.NECK - 5, T.NECK):                          # alças na frente, até o ombro
        T._front_paint(body, [(-4, y), (3, y)], LEATHER_D)
    back = {}
    for (x, y, z) in body.v:
        back[(x, y)] = min(back.get((x, y), 99), z)
    top, low = T.NECK - 1, T.HIP + 3                             # alças cruzadas em X nas costas
    for y in range(low, top + 1):
        u = (y - low) / (top - low)
        for x in (round(-4 + 7 * (1 - u)), round(3 - 7 * (1 - u))):
            if (x, y) in back:
                body.v[(x, y, back[(x, y)])] = LEATHER_D
    for (x, y) in ((-1, T.HIP + 1), (0, T.HIP + 1), (-1, T.HIP + 2), (0, T.HIP + 2)):   # nó do laço
        if (x, y) in back:
            body.set(x, y, back[(x, y)] - 1, LEATHER_D)
    for (x, y) in ((-3, T.HIP + 2), (-2, T.HIP + 1), (-2, T.HIP + 2), (1, T.HIP + 1), (1, T.HIP + 2), (2, T.HIP + 2),
                   (-1, T.HIP), (0, T.HIP), (-1, T.HIP - 1), (0, T.HIP - 2)):   # laçadas e pontas
        if (x, y) in back:
            body.set(x, y, back[(x, y)] - 1, LEATHER)
    L.shade(body, only={LEATHER})
    for x in range(-3, 1):                                       # bolso: costura de cima e dos lados
        T._front_paint(body, [(x, 15)], LEATHER_D)
    for y in range(11, 15):
        T._front_paint(body, [(-3, y), (0, y)], LEATHER_D)
    for y in range(15, 19):                                      # martelo saindo do bolso: cabo e cabeça
        T._front_paint(body, [(-2, y)], WOOD, out=1)
    for x in (-3, -2, -1):
        T._front_paint(body, [(x, 19)], IRON, out=1)
    T._front_paint(body, [(-3, 20), (-2, 20)], IRON_L, out=1)
    for n in ("arm_l", "arm_r"):                                 # manga arregaçada: dobra escura no cotovelo
        v = _part(rig, n)
        lo = min(k[1] for k in v.v)
        for pos in list(v.v):
            if pos[1] <= lo + 1:
                v.v[pos] = s["cuff"]
    for n in ("farm_l", "farm_r"):                               # antebraço de fora + luva grande de couro
        v = _part(rig, n)
        lo = min(k[1] for k in v.v)
        for pos in list(v.v):
            v.v[pos] = LEATHER_D if pos[1] <= lo + 4 else s["skin"]
        for pos in [p for p in v.v if p[1] == lo + 4]:          # punho da luva 1 voxel mais largo
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (pos[0] + dx, pos[1], pos[2] + dz)
                if q not in v.v:
                    v.v[q] = LEATHER
        L.shade(v)


def bun(rig, s):
    """Coque alto: bola de cabelo no alto da nuca, assentada dentro da coroa (sem vão), presa por uma tira de couro."""
    head = _part(rig, "head")
    top = _head_top(rig)
    hb = Vox()
    cx, cy, cz = -0.5, top + 1.2, -3.0
    B.sblob(hb, cx, cy, cz, 3.4, 3.2, 3.4, s["hair"], p=2.1)
    for k in [k for k in hb.v if k in head.v]:
        del hb.v[k]
    for (x, y, z) in list(hb.v):                                 # tira: anel na base da bola, onde ela sai do cabelo
        if y in (top, top + 1) and ((x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2) >= 2.6 ** 2:
            hb.v[(x, y, z)] = LEATHER_D
    L.shade(hb)
    rig.part("hat", hb, (cx, top, cz), "head")


# ----------------------------------------------------------------------------- Quelmo (feirante)

def dress_quelmo(rig, body, s):
    """Casaco aberto (bordas escuras) sobre a camisa, faixa diagonal que dá a volta no ombro direito (frente e costas),
    avental curto deslocado para a direita e bolsa de moedas à esquerda, ambos na frente e dentro de |x| <= 4."""
    teal, teal_d, apron = P("#3f8a8c"), P("#2f6a74"), P("#e6d7b6")
    for y in range(T.HIP + 3, T.NECK - 1):                       # camisa no meio do casaco aberto
        T._front_paint(body, [(x, y) for x in (-1, 0)], s["shirt"])
        T._front_paint(body, [(-2, y), (1, y)], s["coat_d"])
    lo, hi = T.HIP + 3, T.NECK - 2
    back = {}
    for (x, y, z) in body.v:
        back[(x, y)] = min(back.get((x, y), 99), z)
    for i, y in enumerate(range(lo, hi + 1)):                    # faixa: quadril esquerdo → ombro direito, 2 de largura
        x = 3 - round(i * 7 / (hi - lo))
        c = teal_d if i % 4 == 0 else teal
        T._front_paint(body, [(x, y), (x - 1, y)], c)
        for xx in (x, x - 1):                                    # nas costas, o mesmo traçado: volta pelo ombro
            if (xx, y) in back:
                body.v[(xx, y, back[(xx, y)])] = c
    for z in range(-3, 4):                                       # passagem por cima do ombro direito
        for x in (-4, -5):
            col = [k[1] for k in body.v if k[0] == x and k[2] == z]
            if col:
                body.v[(x, max(col), z)] = teal
    for y in range(10, T.HIP):                                   # avental curto, deslocado para a direita
        T._front_paint(body, [(x, y) for x in range(-4, 1)], apron)
    T._front_paint(body, [(x, 12) for x in range(-4, 1)], B.shadow(apron, 1.2))
    T._front_paint(body, [(-2, y) for y in range(10, 12)], B.shadow(apron, 1.2))
    for x in range(1, 5):                                        # bolsa de moedas pendurada no cinto, à esquerda
        for y in range(T.HIP - 3, T.HIP + 1):
            if x in (1, 4) and y == T.HIP - 3:
                continue
            T._front_paint(body, [(x, y)], LEATHER_D if y == T.HIP else LEATHER, out=1)
    T._front_paint(body, [(2, T.HIP), (3, T.HIP)], C("#d6a64a", "metal"), out=1)   # fecho


def beret(rig, s):
    """Boina larga e mole: casca de 2 voxels colada ao alto do cabelo, copa achatada mais larga que a cabeça e caída
    para a ESQUERDA (x > 0): a mão direita acena por baixo da aba do outro lado, sem entrar nela. Debrum escuro e uma
    pena creme do mesmo lado da caída."""
    head = _part(rig, "head")
    top = _head_top(rig)
    felt, felt_d, feather, feather_d = P("#3a6f7a"), P("#284f5a"), P("#ece2c8"), C("#8a5a3a")
    cap = Vox()
    cut = top - 4
    for layer in shell(head, lambda x, y, z: y >= cut + (1 if x <= -4 else 0)):
        for q in layer:
            cap.set(*q, felt)
    B.sblob(cap, 1.0, top + 1.2, -0.3, 8.4, 1.7, 7.6, felt, p=2.2)       # copa achatada, puxada para a esquerda
    for k in [k for k in cap.v if k in head.v]:
        del cap.v[k]
    for (x, y, z) in list(cap.v):                                # caída: a borda esquerda desce um voxel
        if x >= 6 and y <= top + 1 and (x, y - 1, z) not in head.v and (x, y - 1, z) not in cap.v:
            cap.set(x, y - 1, z, felt_d)
    lowest = {}
    for (x, y, z) in cap.v:
        lowest[(x, z)] = min(lowest.get((x, z), 99), y)
    for (x, z), y in lowest.items():                             # debrum: a última fileira de baixo
        if y <= cut + 1:
            cap.v[(x, y, z)] = felt_d
    L.shade(cap, only={felt})
    for i in range(6):                                           # pena: sai da fita e deita para trás, ponta escura
        y, z = top + 1 + i // 2, -2 - i
        col = feather_d if i >= 4 else feather
        cap.set(8, y, z, col)
        cap.set(8, y - 1, z, col if i >= 4 else feather)        # 2 de altura: a pena fica ligada (6 vizinhos)
    rig.part("hat", cap, (0, cut, 0), "head")


# ----------------------------------------------------------------------------- Arvel (caçador)

def dress_arvel(rig, body, s):
    """Gola de pele em volta do pescoço (dentro de |x| <= 4: o ombro e o braço ficam livres), capinha nas costas com
    barra de pele, corda grossa enrolada a tiracolo (ombro direito → quadril esquerdo), bolsa de caça NAS COSTAS acima
    do quadril (fora do balanço do braço e da coxa) e botas altas com punho de pele."""
    lo, hi = T.HIP + 1, T.NECK - 4
    for i, y in enumerate(range(lo, hi + 1)):                    # corda a tiracolo, saliente 1 voxel, 2 de largura
        x = 3 - round(i * 7 / (hi - lo))
        for k, xx in enumerate((x, x - 1)):
            T._front_paint(body, [(xx, y)], ROPE_D if (y + k) % 3 == 0 else ROPE, out=1)
    for layer, col in zip(shell(body, lambda x, y, z: T.NECK - 3 <= y <= T.NECK - 1 and abs(x + 0.5) <= 4.5 and z <= 4), (FUR_L, FUR)):
        for (x, y, z) in layer:
            body.set(x, y, z, FUR_D if (x * 3 + z) % 5 == 0 else col)
    for (x, y, z) in [k for k in body.v if k[1] == T.NECK - 1 and body.v[k] in (FUR, FUR_L, FUR_D)]:
        if (x, y + 1, z) not in body.v and abs(x + 0.5) <= 3.5:
            body.set(x, y + 1, z, FUR_L)                            # a gola sobe um pouco em volta do pescoço
    back = {}
    for (x, y, z) in body.v:
        back[(x, y)] = min(back.get((x, y), 99), z)
    for (x, y), z in back.items():                               # capinha: uma camada por trás, da gola até y = 18
        if 18 <= y <= T.NECK - 4 and abs(x + 0.5) <= 4.6:
            body.set(x, y, z - 1, FUR_L if y <= 19 else s["coat_d"])
    for x in range(0, 5):                                        # bolsa de caça nas costas, acima do quadril
        for y in range(T.HIP + 1, T.HIP + 6):
            bz = back.get((x, y))
            if bz is None:
                continue
            for z in range(bz - 3, bz):
                edge = y == T.HIP + 1 or z == bz - 3
                body.set(x, y, z, LEATHER_D if edge else LEATHER_L)
    for x in range(0, 5):                                        # aba da bolsa e tufo de pele
        bz = back.get((x, T.HIP + 5))
        if bz is not None:
            body.set(x, T.HIP + 6, bz - 2, LEATHER_D)
            body.set(x, T.HIP + 6, bz - 3, LEATHER_D)
    for (x, y) in ((1, T.HIP + 4), (2, T.HIP + 3), (3, T.HIP + 4)):
        bz = back.get((x, y))
        if bz is not None:
            body.set(x, y, bz - 4, FUR_L)
    for n in ("shin_l", "shin_r"):                               # botas altas até o joelho, punho de pele
        v = _part(rig, n)
        hi_y = max(k[1] for k in v.v)
        for pos in list(v.v):
            v.v[pos] = FUR_L if pos[1] >= hi_y - 1 else s["boot"]
        L.shade(v)


def fur_hat(rig, s):
    """Gorro de pele: casca de 2 voxels colada ao alto da cabeça (sem vão), barra clara na testa e abas que descem
    pelas laterais do cabelo até a altura dos olhos, sem tocar a placa do rosto (|x| >= 6, z <= 2)."""
    head = _part(rig, "head")
    top = _head_top(rig)
    hat = Vox()
    cut = top - 5
    for i, layer in enumerate(shell(head, lambda x, y, z: y >= cut)):
        for (x, y, z) in layer:
            hat.set(x, y, z, FUR_D if (x + 2 * z) % 4 == 0 and i == 1 else FUR)
    flap = shell(head, lambda x, y, z: top - 10 <= y < cut + 1 and abs(x + 0.5) >= 6 and -4 <= z <= 1, thick=1)[0]
    for (x, y, z) in flap:                                       # abas: uma camada da cor da copa, borda de baixo clara
        hat.set(x, y, z, FUR_L if y == top - 10 else FUR)
    for (x, y, z) in list(hat.v):                               # barra clara: as duas fileiras de baixo da copa
        if y in (cut, cut + 1) and abs(x + 0.5) < 6:
            hat.v[(x, y, z)] = FUR_L
    L.shade(hat, only={FUR})
    rig.part("hat", hat, (0, cut, 0), "head")


# ----------------------------------------------------------------------------- construção

DRESS = {"brasia": (dress_brasia, bun), "quelmo": (dress_quelmo, beret), "arvel": (dress_arvel, fur_hat)}


def build_one(key, out):
    s = PROFILES[key]
    spec = {k: v for k, v in s.items() if k not in ("meters", "beard", "glasses", "profile")}
    rig = T.npc("suvia", **spec)
    heads = [v for n, v, p, par in rig.parts if n == "head"] + list(rig.alts["head"].values())
    for head in heads:
        O.add_face_details(head, s)
    body = next(v for n, v, p, par in rig.parts if n == "torso")
    for x in (-1, 0):                                             # tira o enfeite de colo do esqueleto base
        for y in (T.NECK - 8, T.NECK - 7):
            body.v.pop((x, y, 4), None)
    dress, hat = DRESS[key]
    dress(rig, body, s)
    hat(rig, s)
    base_h = rig.all_voxels().bounds()[4] + 1
    scale = round(s["meters"] * UNITS_PER_METER / VS) / base_h
    scaled = Rig(s["title"], VS, "biped")
    for n, v, p, par in rig.parts:
        alt = {k: O.scale_vox(a, scale) for k, a in rig.alts.get(n, {}).items()} or None
        sv = O.scale_vox(v, scale)
        L.prune_specks(sv, label=f"{key}/{n}")
        scaled.part(n, sv, tuple(q * scale for q in p), par, alts=alt)
    top = scaled.all_voxels().bounds()[4] + 1
    body_top = round(rig.meta["bodyHeightVoxels"] * scale)
    head_h = round(rig.meta["headH"] * scale)
    faces = sum(exposed_faces(v) for _, v, _, _ in scaled.parts)
    scaled.meta = dict(rig.meta, headsTall=round(body_top / head_h, 2), bodyHeightVoxels=body_top, headH=head_h,
                       legH=round(T.HIP * scale), torsoH=round((T.NECK - T.HIP) * scale),
                       legFraction=round(T.HIP * scale / body_top, 3), faces=faces, metersNarrative=s["meters"],
                       worldUnits=round(top * VS, 3), escala=round(scale, 4), role=s["role"], note=s["note"],
                       weaponProfile=s["profile"], conteudo=True)
    scaled.meta["hands"] = {side: [round(q * scale, 2) for q in c] for side, c in rig.meta["hands"].items()}
    for vol in scaled.meta.get("volumes", []):
        vol["a"] = [round(q * scale, 2) for q in vol["a"]]
        if "b" in vol:
            vol["b"] = [round(q * scale, 2) for q in vol["b"]]
        vol["r"] = round(vol["r"] * scale, 2)
    scaled.save(str(Path(out) / "characters/npc" / key))
    print(f"  {key:7s} {top} voxels = {top * VS:.3f} u, {body_top / head_h:.2f} cabeças, {faces} faces, escala {scale:.3f}")
    return scaled


LINEUPS = {                                                       # conjuntos do modo LINHA (set=<nome>) para revisão
    "npcs-mundo": ["characters/e/adventurer"] + [f"characters/npc/{k}" for k in PROFILES],
    "npcs-mundo-elenco": ["characters/e/adventurer", "characters/npc/brasia", "characters/npc/bramor",
                          "characters/npc/quelmo", "characters/npc/kelyra", "characters/npc/indira",
                          "characters/npc/arvel", "characters/npc/teodor", "characters/npc/ondina"],
}


def build(out):
    import json
    for key in PROFILES:
        build_one(key, out)
    d = Path(out) / "studies/lineups"
    d.mkdir(parents=True, exist_ok=True)
    for name, dirs in LINEUPS.items():
        (d / f"{name}.json").write_text(json.dumps({"dirs": dirs, "larguraMax": 2.6, "vao": 0.42}, indent=1) + "\n")
        for k in PROFILES:
            (d / f"npc-{k}.json").write_text(json.dumps({"dirs": ["characters/e/adventurer", f"characters/npc/{k}"]},
                                                         indent=1) + "\n")
            (d / f"npc-{k}-so.json").write_text(json.dumps({"dirs": [f"characters/npc/{k}"]}, indent=1) + "\n")


if __name__ == "__main__":
    build(Path(__file__).resolve().parents[1] / "assets")
