"""Laboratório de conteúdo — vegetação nova da direção E (candidatas).

Regra da biblioteca (regras-de-vegetacao.md): uma espécie só entra com silhueta DISTINTA das 8 aprovadas em preto
chapado (carvalhos O3/oval/jovem/outono, pinheiro, bétula, árvore-marco, salgueiro). Mesma gramática: voxel 1/8,
tronco em pera (trunk_b), pintura de copa por normal em 4 tons com manchas largas (canopy_paint), avesso escuro,
folhagem com vento afim. O que muda por espécie é a FORMA (como a copa se divide, a direção dominante, o vazio).
"""
import math

import env_b as EB
import env_e as EE
import conteudo_lib as L
from conteudo_lib import BIG, P, asset, sblob, shade, shadow, light
from envlib import hash3
from voxlib import Vox

LEAF_POPLAR = P("#8aa83f", "foliage")      # álamo: oliva-amarelado (entre o carvalho #76ad47 e a bétula #b8cb55)
LEAF_OLD = P("#5c8c46", "foliage")         # árvore antiga: verde-musgo escuro (abaixo do arbusto #6a9f47)
DEADWOOD = P("#9a7c68")                     # madeira seca acinzentada (casca sem seiva; família da casca #8f5d40)
SHELF = P("#e8cfa0")                        # orelha-de-pau (fungo de prateleira, bege — nunca o vermelho do fungo inimigo)


def _flat_masses(v, masses, base, seed, under=1.6):
    """Massas achatadas de folhagem (pratos): base cortada reta, avesso escuro, pintura larga — para copas em
    'nuvens' horizontais (árvore antiga) e em pares (bifurcada)."""
    tmp = Vox()
    for (x, y, z, rx, ry, rz) in masses:
        cut = y - ry * 0.45
        for k in list(sblob(Vox(), x, y, z, rx, ry, rz, base, p=2.3).v):
            if k[1] >= cut:
                tmp.v[k] = base
    EE.canopy_paint(tmp, base, seed, clump=5, amp=0.08)
    for (x, y, z), c in list(tmp.v.items()):
        if (x, y - 1, z) not in tmp.v:
            tmp.v[(x, y, z)] = shadow(base, under)
    return tmp


def _leaf_wins(v, leaf):
    for k in [k for k in v.v if k in leaf.v]:
        del v.v[k]
    v.merge(leaf)


# ============================================================================= bifurcada

@asset("tree_forked", nome="Árvore bifurcada (copas gêmeas)", categoria="vegetacao", sub="arvore", lote=1,
       voxel=BIG, frequencia="comum", wind=True, tronco_faixa=(3, 8),
       tags=["mata", "clareira", "beira_de_trilha", "comum"],
       funcao="massa de floresta com vão no meio: quebra a repetição das copas cheias; emoldura caminhos",
       terreno=["grama", "mata"], apoio={"tipo": "ponto"}, inclinacao_max_u=0.8,
       area_livre={"raio_u": 0.5},
       colisao={"tipo": "tronco", "raio": 0.4, "copa": 0.32, "copaDe": 0.45},
       restricoes=["fora da área livre de construções e POIs"],
       variacoes={"semente": [1, 2, 3], "cor": ["verde", "outono"]})
def tree_forked(semente=1, cor="verde"):
    """O tronco abre em V a 1,3 u em dois líderes; cada um carrega a própria copa (duas massas achatadas com franja)
    e entre elas fica um VÃO de céu. Silhueta de 'Y' largo: nenhuma das 8 tem copa partida."""
    base = EE.LEAF_BIG if cor == "verde" else EE.LEAF_AUTUMN
    v = Vox()
    s = semente
    EB.trunk_b(v, 12, 3.4, 2.3, path=lambda t: (0.6 * math.sin(t * 2.0), 0.3 * t), roots=5, seed=40 + s)
    lx, rx = -9.5 - 0.6 * (s % 2), 9.0 + 0.5 * ((s + 1) % 2)
    EB.branch(v, (0, 10, 0), (lx * 0.55, 19, 1), 1.7)
    EB.branch(v, (lx * 0.55, 19, 1), (lx, 25, 1.5), 1.25)
    EB.branch(v, (0, 10, 0), (rx * 0.55, 18, -1), 1.6)
    EB.branch(v, (rx * 0.55, 18, -1), (rx, 23, -1.5), 1.2)
    EB.branch(v, (lx * 0.55, 20, 1), (lx * 0.3, 27, -1), 0.9)
    shade(v)
    leaf = Vox()
    EE.crown_masses(leaf, [(lx, 29, 1.5, 6.6, 5.4, 6.2), (lx - 1.5, 33.5, 0.5, 4.6, 3.8, 4.4)], base, 50 + s, drops=6)
    EE.crown_masses(leaf, [(rx, 26.5, -1.5, 6.0, 4.8, 5.8), (rx + 1.2, 30.5, -2.0, 4.2, 3.4, 4.0)], base, 60 + s, drops=6)
    _leaf_wins(v, leaf)
    pts = {}
    return v, pts


# ============================================================================= álamo (copa vertical)

@asset("tree_poplar", nome="Álamo (copa em fuso)", categoria="vegetacao", sub="arvore", lote=1,
       voxel=BIG, frequencia="comum", wind=True, tronco_faixa=(2, 6),
       tags=["beira_de_agua", "alameda", "estrada", "quebra_vento", "comum"],
       funcao="vertical estreito: alamedas ao longo da estrada, quebra-vento de vila, beira d'água; separa planos",
       terreno=["grama", "margem", "caminho"], apoio={"tipo": "ponto"}, inclinacao_max_u=0.8,
       area_livre={"raio_u": 0.4},
       colisao={"tipo": "tronco", "raio": 0.25, "copa": 0.42, "copaDe": 0.2},
       restricoes=["em fileira ao longo de caminho ou margem (alameda), não solto no meio da clareira"],
       variacoes={"semente": [1, 2, 3], "altura": [1.0, 0.85, 1.12]})
def tree_poplar(semente=1, altura=1.0):
    """Fuso alto e estreito (≈ 1,4 u de largura × 6 u), mais largo no terço de baixo e fechando numa ponta
    arredondada; SULCOS verticais rasos dividem a folhagem em feixes (lê como ramos subindo), tronco quase todo
    escondido. Diferente do pinheiro (cone em saias horizontais) e do carvalho oval (dois andares redondos)."""
    v = Vox()
    s = semente
    k = altura
    EB.trunk_b(v, int(9 * k), 2.5, 1.4, path=lambda t: (0.4 * t, 0.2 * t), roots=4, seed=70 + s)
    shade(v)
    leaf = Vox()
    prof = [(9, 1.8), (12, 3.8), (17, 5.0), (23, 5.6), (29, 5.2), (35, 4.4), (40, 3.4), (44, 2.4), (47, 1.4)]
    prof = [(int(round(y * k)), r) for (y, r) in prof]
    for i in range(len(prof) - 1):
        (y0, r0), (y1, r1) = prof[i], prof[i + 1]
        for y in range(y0, y1):
            t = (y - y0) / max(1, y1 - y0)
            r = r0 + (r1 - r0) * t
            cx = 0.5 * math.sin(y * 0.12 + s)
            for x in range(-8, 9):
                for z in range(-8, 9):
                    dx, dz = x + 0.5 - cx, z + 0.5
                    a = math.atan2(dz, dx)
                    rr = r * (1 - 0.13 * max(0.0, math.cos(7 * a + s)) ** 6)     # 7 sulcos verticais rasos
                    if dx * dx + dz * dz <= rr * rr:
                        leaf.set(x, y, z, LEAF_POPLAR)
    for j in range(5):                                                       # feixes: lóbulos desencontrados
        a = j * 2.4 + s
        yb = int((16 + j * 6) * k)
        sblob(leaf, math.cos(a) * 3.6, yb, math.sin(a) * 3.6, 2.8, 4.6, 2.8, LEAF_POPLAR, p=2.2)
    for y in range(prof[-1][0], prof[-1][0] + 2):                          # ponta arredondada
        leaf.set(0, y, 0, LEAF_POPLAR)
        leaf.set(-1, y, 0, LEAF_POPLAR) if y == prof[-1][0] else None
    EE.canopy_paint(leaf, LEAF_POPLAR, 70 + s, clump=4, amp=0.06)
    for (x, y, z), c in list(leaf.v.items()):                               # sulcos mais escuros
        dx, dz = x + 0.5, z + 0.5
        a = math.atan2(dz, dx)
        if max(0.0, math.cos(7 * a + s)) ** 6 > 0.55 and c != shadow(LEAF_POPLAR, 1.6):
            leaf.v[(x, y, z)] = shadow(LEAF_POPLAR, 1.0)
        if (x, y - 1, z) not in leaf.v:
            leaf.v[(x, y, z)] = shadow(LEAF_POPLAR, 1.6)
    _leaf_wins(v, leaf)
    return v, {}


# ============================================================================= antiga retorcida (copa em pratos)

@asset("tree_gnarled", nome="Árvore antiga retorcida", categoria="vegetacao", sub="arvore_marco", lote=1,
       voxel=BIG, frequencia="rara", wind=True, tronco_faixa=(2, 6),
       tags=["colina", "ruina", "mirante", "marco_natural", "rara"],
       funcao="marco natural raro: topo de colina, junto a ruínas e mirantes; lugar de encontro ou de descanso",
       terreno=["grama", "colina", "rocha"], apoio={"tipo": "ponto"}, inclinacao_max_u=1.0,
       area_livre={"raio_u": 1.2},
       colisao={"tipo": "tronco", "raio": 0.55, "copa": 0.3, "copaDe": 0.5},
       restricoes=["no máximo 1 por região", "topo de colina, junto a ruína ou mirante"],
       variacoes={"semente": [1, 2, 3], "musgo": [True, False]})
def tree_gnarled(semente=1, musgo=True):
    """Tronco grosso, inclinado e torcido, com raízes expostas; três galhos longos quase horizontais terminam em
    PRATOS de folhagem achatados em alturas diferentes (copa em nuvens, com céu entre os pratos). Verde-musgo escuro,
    musgo sobre os galhos. Silhueta horizontal e escalonada: nenhuma das 8 é mais larga que alta com pratos soltos."""
    v = Vox()
    s = semente
    EB.trunk_b(v, 15, 4.2, 2.4, bark=EB.BARK_D, streak=EB.BARK, flare=2.8, roots=7,
               path=lambda t: (3.2 * math.sin(t * 2.4), -1.2 * t), seed=80 + s)
    top = (3.2 * math.sin(2.4), 14, -1.2)
    arms = [((-12, 21, 3), (-15.5, 22, 4.5)), ((12, 20, -2), (15, 21, -3)), ((2, 25, -4), (1, 28, -5))]
    for (mid, end) in arms:
        EB.branch(v, top, mid, 1.5, EB.BARK_D)
        EB.branch(v, mid, end, 1.1, EB.BARK_D)
    EB.branch(v, (-12, 21, 3), (-9, 25, 6), 0.9, EB.BARK_D)
    shade(v)
    if musgo:
        for (x, y, z), c in list(v.v.items()):
            if y > 15 and (x, y + 1, z) not in v.v and hash3(x // 2, 0, z // 2, 81) < 0.55:
                v.v[(x, y, z)] = L.MOSS
    pads = [(-15.5, 25.0, 4.5, 7.2, 3.8, 6.0), (-9.5, 28.5, 6.5, 4.6, 3.0, 4.0), (15.0, 24.0, -3.0, 6.6, 3.6, 5.6),
            (1.0, 32.0, -5.0, 7.8, 4.0, 6.6), (6.5, 28.5, 1.5, 4.0, 2.8, 3.6)]
    leaf = _flat_masses(v, pads, LEAF_OLD, 82 + s)
    _leaf_wins(v, leaf)
    import charlib_e
    charlib_e.prune_specks(v, label="tree_gnarled")
    return v, {"interacao": [("sombra", (2, 0, 9), "descansar")]}


# ============================================================================= seca

@asset("tree_dead", nome="Árvore seca", categoria="vegetacao", sub="arvore", lote=1,
       voxel=BIG, frequencia="rara", wind=False, tronco_faixa=(2, 6),
       tags=["ruina", "pantano", "mistério", "encruzilhada", "rara"],
       funcao="esparsa e sem folhas: marca lugares estranhos (ruínas, círculo de pedras, encruzilhada); leitura de "
              "'algo aconteceu aqui'",
       terreno=["grama", "terra", "rocha"], apoio={"tipo": "ponto"}, inclinacao_max_u=1.0,
       area_livre={"raio_u": 0.6},
       colisao={"tipo": "tronco", "raio": 0.35, "copa": 0.0, "copaDe": 1.0},
       restricoes=["no máximo 2 por região", "junto a ruína, círculo de pedras ou encruzilhada; nunca dentro da vila"],
       variacoes={"semente": [1, 2, 3], "ninho": [True, False]})
def tree_dead(semente=1, ninho=True):
    """Tronco acinzentado com oco escuro, cinco galhos que se bifurcam e afinam até gravetos (traço 6-conexo, sem
    pontilhado), orelhas-de-pau bege no tronco e um ninho. Nada de folhagem: o vazio é a silhueta."""
    import charlib_e
    v = Vox()
    s = semente
    EB.trunk_b(v, 17, 3.2, 1.4, bark=DEADWOOD, streak=shadow(DEADWOOD, 0.8),
               path=lambda t: (-1.4 * math.sin(t * 2.6), 0.6 * t), roots=5, seed=90 + s)
    limbs = [((-1, 13, 0), (-8, 22, 2), (-12, 27, 1)), ((-1, 14, 0), (7, 21, -1), (11, 26, -3)),
             ((-1, 15, 0), (-2, 24, -5), (-1, 30, -7)), ((-1, 12, 0), (5, 17, 5), (9, 19, 8)),
             ((-1, 16, 0), (2, 26, 3), (4, 32, 2))]
    for i, (a, b, c) in enumerate(limbs):
        EB.branch(v, a, b, 1.1, DEADWOOD)
        EB.branch(v, b, c, 0.75, DEADWOOD)
        # gravetos: dois por ponta, 6-conexos
        dx, dy, dz = c[0] - b[0], c[1] - b[1], c[2] - b[2]
        ln = max(1.0, math.sqrt(dx * dx + dy * dy + dz * dz))
        for k in (-1, 1):                                         # forquilha: continua o rumo do galho e abre
            tip = (c[0] + dx / ln * 4 + k * 2.2, c[1] + dy / ln * 4 + 1.5 + hash3(i, k, 2, s) * 1.5, c[2] + dz / ln * 4 + k * 0.8)
            L.line6(v, c, tip, light(DEADWOOD, 0.4))
    shade(v)
    for y in range(6, 10):                                       # oco escuro na frente do tronco
        for x in (-2, -1):
            fz = max((k[2] for k in v.v if k[0] == x and k[1] == y), default=None)
            if fz is not None:
                v.v[(x, y, fz)] = shadow(EB.BARK_D, 1.6)
    for (x, y, sgn) in ((1, 5, 1), (-2, 11, 1)):               # orelhas-de-pau
        fz = max((k[2] for k in v.v if k[0] == x and k[1] == y), default=0)
        v.box(x - 1, y, fz + 1, x + 1, y, fz + 2, SHELF)
    if ninho:
        v.box(-9, 22, 1, -6, 23, 3, EB.STRAW_D)
        v.box(-8, 24, 2, -7, 24, 2, EB.STRAW)
    charlib_e.prune_specks(v, label="tree_dead")
    return v, {}


# ============================================================================= em andares (exploração — ver estado)

@asset("tree_tiered", aprovacao="rejeitado", integracao="nao_distribuivel", nome="Árvore em andares (pratos empilhados)", categoria="vegetacao", sub="arvore", lote=1,
       voxel=BIG, frequencia="comum", wind=True, tronco_faixa=(2, 6),
       motivo_rejeicao="hipótese de partida (confundir-se com o pinheiro) NÃO se confirmou: em preto os vãos entre os "
                       "pratos a separam do cone do pinheiro. Rejeitada por outro motivo, visto na captura: pratos "
                       "simétricos e regulares num tronco reto leem como TOPIARIA ou suporte de bolo (artificial, sem a "
                       "irregularidade orgânica das copas da E); o prato do topo parece flutuar; é um clichê de árvore "
                       "estilizada sem identidade própria. A árvore antiga (pratos em galhos tortos, alturas desiguais) "
                       "cumpre o papel de copa horizontal com mais caráter. Decisão do proprietário (lote 1): REJEITADA para "
                       "florestas naturais; pode voltar no futuro como estudo ORNAMENTAL de jardim, se houver motivo "
                       "artístico. Não é publicada como árvore de floresta.",
       tags=["mata", "comum"], funcao="exploração: copa em andares horizontais", terreno=["grama", "mata"],
       apoio={"tipo": "ponto"}, inclinacao_max_u=0.8, area_livre={"raio_u": 0.5},
       colisao={"tipo": "tronco", "raio": 0.3, "copa": 0.4, "copaDe": 0.3},
       variacoes={})
def tree_tiered():
    """Tronco reto e três pratos horizontais largos que diminuem para cima, com vão entre eles."""
    v = Vox()
    EB.trunk_b(v, 34, 2.6, 1.2, path=lambda t: (0.3 * t, 0.0), roots=4, seed=95)
    shade(v)
    pads = [(0, 14.5, 0, 10.5, 3.2, 9.5), (0.5, 23.0, 0.3, 8.0, 3.0, 7.4), (0.8, 31.0, 0.5, 5.6, 2.8, 5.2), (0.9, 37.0, 0.6, 3.0, 2.2, 2.8)]
    leaf = _flat_masses(v, pads, EE.LEAF, 96)
    _leaf_wins(v, leaf)
    return v, {}
