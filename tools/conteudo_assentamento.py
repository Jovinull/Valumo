"""Laboratório de conteúdo, lote 2 — estruturas complementares de assentamento (direção E, voxel 1/8).

O que liga as casas: depósito, paiol, poço de sarilho, chafariz de praça, lenheiro. Mesma cultura construtiva da casa
(pedra assentada, estuque, enxaimel, telha coral ou palha), escala menor, função legível sem texto. Vegetação PRESA é
fosca (IVY, MOSS_FIX — ver conteudo-mundo.md §8).
"""
import math

import env_b as EB
import conteudo_lib as L
from conteudo_lib import (BIG, CREAM, Face, GLOW, GLOW_HI, IRON, IVY, PLASTER, STONE, STONE_D, STONE_L, STRAW, STRAW_D,
                          TIMBER, WATER, WOOD, WOOD_D, WOOD_L, asset, shade)
from envlib import hash3
from voxlib import Vox


def _logs(v, x0, x1, z0, z1, y0, rows, step=3):
    """Toras 2×2 deitadas ao longo de z, fileiras desencontradas, cerne claro nas pontas."""
    for r in range(rows):
        y = y0 + 2 * r
        for x in range(x0 + (r % 2), x1 - 1, step):
            for z in range(z0, z1 + 1):
                end = z in (z0, z1)
                for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    v.set(x + dx, y + dy, z, WOOD_L if end else (WOOD if dy else WOOD_D))


# ============================================================================= depósito de uma água

@asset("shed_storage", nome="Depósito de uma água", categoria="estrutura", sub="armazenamento", lote=2,
       voxel=BIG, frequencia="comum",
       tags=["vila", "rural", "armazenamento", "fundos_de_casa", "oficio"],
       funcao="depósito de ferramentas e sacos atrás das casas e junto às oficinas: dá 'fundos' habitados à vila",
       terreno=["grama", "terra"], apoio={"tipo": "patamar", "margem_u": 0.3}, inclinacao_max_u=0.35,
       area_livre={"frente_u": 1.4, "lados_u": 0.4, "fundos_u": 0.2},
       colisao={"tipo": "caixa", "escala": 0.9},
       restricoes=["encostado nos fundos ou na lateral de uma construção, porta para o pátio"],
       relacoes=[("acompanha", "house_tall", [2.0, 5.0]), ("acompanha", "workshop_forge", [1.5, 4.0]),
                 ("acompanha", "hut_herbalist", [2.0, 5.0])],
       variacoes={"telhado": ["coral", "teal", "mostarda"], "barril": [True, False]})
def shed_storage(telhado="coral", barril=True):
    """Telhado de UMA água (alto atrás, baixo na frente) sobre caixa de estuque e enxaimel: o perfil em cunha não
    existe em nenhuma construção aprovada. Porta dupla de tábuas com travessas em Z, janelinha alta na lateral,
    barril de chuva no canto, hera na outra lateral."""
    v = Vox()
    X0, X1, Z0, Z1 = -10, 9, -7, 6
    L.foundation(v, X0 - 1, Z0 - 1, X1 + 1, Z1 + 1, top=1, depth=-4, seed=201)
    L.walls(v, X0, Z0, X1, Z1, 2, 14)
    fF, fB, fL, fR = Face("z", Z1, 1), Face("z", Z0, -1), Face("x", X0, -1), Face("x", X1, 1)
    for f, a0, a1 in ((fF, X0, X1), (fB, X0, X1)):
        L.timber_face(v, f, a0, a1, 2, 14, braces=False)
    for f, a0, a1 in ((fL, Z0, Z1), (fR, Z0, Z1)):
        L.timber_face(v, f, a0, a1, 2, 14, braces=False)
        L.cross_brace(v, f, a0 + 2, a1 - 2, 3, 10)
    # telhado de uma água: alto atrás (z0) e baixo na frente, beiral de 3 na frente
    base, line = L.ROOFS[telhado]
    def h(x, z):
        t = (Z1 + 3 - z) / (Z1 + 3 - (Z0 - 1))
        return 13 + int(round(7 * L.concave(t, 0.25)))
    roof, hs = L.roof_field(v, range(X0 - 2, X1 + 3), range(Z0 - 1, Z1 + 4), h, base, line,
                            edge=lambda x, z: x in (X0 - 2, X1 + 2))
    for xc, f in ((X0, fL), (X1, fR)):                          # oitões triangulares de estuque
        for z in range(Z0, Z1 + 1):
            for y in range(15, hs[(xc, z)] - 1):
                f.put(v, z, y, TIMBER if z in (Z0, Z1) or y == 15 else PLASTER)
    # porta dupla de tábuas, janelinha, barril, hera
    for (a0, a1) in ((-4, -1), (0, 3)):
        L.plank_door(v, fF, a0, a1, 2, 10, col=WOOD)
    L.window(v, fR, -2, 1, 8, 11, shutters=False, box=False)
    for a in (-6, 5):                                           # fundos: prumos intermediários, cruzes e janelinha alta
        for y in range(2, 15):
            fB.put(v, a, y, TIMBER)
    # (v2: as cruzes em painéis de 2 voxels viravam rabisco — os fundos ficam com prumos, janela e ferramentas)
    L.window(v, fB, -2, 1, 9, 12, shutters=False, box=False)
    for (a, y0, y1) in ((-4, 3, 8), (3, 3, 8)):                  # ferramentas penduradas na parede dos fundos
        for y in range(y0, y1 + 1):
            fB.put(v, a, y, WOOD, d=1)
        fB.put(v, a - 1, y1 + 1, IRON, d=1)
        fB.put(v, a, y1 + 1, IRON, d=1)
        fB.put(v, a + 1, y1 + 1, IRON, d=1)
    if barril:
        for y in range(0, 6):
            for x in range(X1 + 1, X1 + 6):
                for z in range(Z1 - 3, Z1 + 2):
                    if (x - X1 - 3) ** 2 + (z - Z1 + 1) ** 2 <= 5.2:
                        v.set(x, y, z, IRON if y in (1, 4) else WOOD)
        v.box(X1 + 2, 5, Z1 - 2, X1 + 4, 5, Z1, WATER)
    L.vine(v, fL, -3, 2, 13, seed=202)
    L.moss_patches(roof, set(roof.v.values()), amount=0.05, max_y=15, seed=203)
    v.merge(roof)
    out = L.shear(v, 0.06, y0=2)
    pts = {
        "entradas": [("porta", (0, 0, Z1 + 5), 0)],
        "interacao": [("porta", (0, 0, Z1 + 4), "abrir")],
        "spawns": [],
    }
    return out, pts


# ============================================================================= paiol sobre pés de pedra

@asset("granary_raised", nome="Paiol sobre pés de pedra", categoria="estrutura", sub="armazenamento", lote=2,
       voxel=BIG, frequencia="incomum",
       tags=["rural", "campo", "armazenamento", "graos", "comunidade_rural"],
       funcao="guarda de grãos de uma comunidade rural: suspenso sobre pés de pedra em cogumelo (contra umidade e "
              "roedores); marca o 'campo' como área de trabalho",
       terreno=["grama", "campo"], apoio={"tipo": "terreno", "_nota": "os pés se enterram; o corpo fica nivelado"},
       inclinacao_max_u=0.5, area_livre={"frente_u": 1.6, "lados_u": 0.6, "fundos_u": 0.6},
       colisao={"tipo": "caixa", "escala": 0.85},
       restricoes=["comunidade rural ou borda do campo; não no centro de vila de caminho"],
       relacoes=[("perto_de", "crop_field", [2.0, 8.0]), ("perto_de", "cart_hay", [1.5, 5.0])],
       variacoes={"telhado": ["palha", "mostarda", "coral"]})
def granary_raised(telhado="palha"):
    """Corpo de estuque e enxaimel ERGUIDO 0,75 u sobre seis pés de pedra em forma de cogumelo (fuste + chapéu largo),
    telhado de quatro águas de palha com beiral pendente, portinhola no oitão com escadinha de três degraus de
    madeira. Silhueta: caixa de pernas com chapéu — nada no jogo é suspenso assim."""
    v = Vox()
    X0, X1, Z0, Z1 = -8, 7, -6, 5
    for x in (X0 + 1, (X0 + X1) // 2, X1 - 2):
        for z in (Z0 + 1, Z1 - 2):
            st = L.stone_block(x - 1, -3, z - 1, x + 2, 4, z + 2, seed=210 + x + z, soft=True)
            v.merge(st)
            v.merge(L.stone_block(x - 2, 5, z - 2, x + 3, 5, z + 3, seed=211, soft=True))    # chapéu do pé
    v.box(X0, 6, Z0, X1, 6, Z1, TIMBER)                         # estrado
    L.walls(v, X0, Z0, X1, Z1, 7, 15)
    for f, a0, a1 in ((Face("z", Z1, 1), X0, X1), (Face("z", Z0, -1), X0, X1), (Face("x", X0, -1), Z0, Z1),
                      (Face("x", X1, 1), Z0, Z1)):
        L.timber_face(v, f, a0, a1, 7, 15, braces=False)
        L.cross_brace(v, f, a0 + 2, a1 - 2, 8, 14)
    fF = Face("z", Z1, 1)
    for a in range(-3, 3):                                      # portinhola (limpa o X ali)
        for y in range(8, 14):
            fF.put(v, a, y, TIMBER if a in (-3, 2) or y == 13 else (WOOD if a % 2 else WOOD_D))
    fF.put(v, 1, 11, IRON, d=1)
    roof, hs = L.hip_roof(v, X0, X1, Z0, Z1, 16, 8, ov=3, curve=0.45, roof=telhado)
    for (x, z), hh in hs.items():                               # aba de palha pendente
        if any(k not in hs for k in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))):
            v.set(x, hh - 2, z, L.shadow(L.ROOFS[telhado][0], 0.8))
    out = L.shear(v, 0.05, y0=7)
    for i, y in enumerate((5, 3, 1)):                           # escadinha de madeira até a portinhola
        z = Z1 + 1 + i * 2
        out.box(-2, y, z, 1, y, z + 1, WOOD_L)
        out.box(-2, 0, z + 1, -2, y - 1, z + 1, WOOD_D)
        out.box(1, 0, z + 1, 1, y - 1, z + 1, WOOD_D)
    for (x, z) in ((X1 + 2, -2), (X1 + 3, 0)):                  # sacos encostados
        out.box(x, 0, z, x + 1, 2, z + 1, CREAM)
        out.set(x, 3, z, STRAW_D)
    pts = {
        "entradas": [("escada", (-1, 0, Z1 + 9), 0)],
        "interacao": [("portinhola", (-1, 0, Z1 + 8), "abrir")],
        "spawns": [],
    }
    return out, pts


# ============================================================================= poço de sarilho

@asset("well_windlass", nome="Poço de sarilho", categoria="estrutura", sub="agua", lote=2,
       voxel=BIG, frequencia="incomum",
       tags=["vila", "praca", "agua", "ponto_de_encontro", "rural"],
       funcao="poço alternativo ao aprovado (redondo, cúpula de palha): bocal quadrado, sarilho com manivela e "
              "telhadinho de duas águas; ponto de encontro de vila de caminho ou de sítio",
       terreno=["grama", "terra", "caminho"], apoio={"tipo": "patamar", "margem_u": 0.3}, inclinacao_max_u=0.3,
       area_livre={"raio_u": 1.3},
       colisao={"tipo": "caixa", "escala": 0.8},
       restricoes=["não junto do poço aprovado (um poço por praça)"],
       relacoes=[("perto_de", "bench_wood", [1.5, 4.0]), ("perto_de", "tag:praca", None)],
       variacoes={"telhado": ["coral", "teal", "palha"]})
def well_windlass(telhado="coral"):
    """Bocal QUADRADO de pedra assentada com cantos claros, água por dentro; dois esteios de 2×2 com mãos-francesas;
    sarilho (tora com manivela de ferro, corda e balde); telhadinho de duas águas coral. Distinto do poço aprovado na
    planta (quadrada × redonda), na cobertura (duas águas × cúpula de palha) e no mecanismo (sarilho × travessa)."""
    v = Vox()
    v.merge(L.stone_block(-6, -4, -6, 5, 4, 5, seed=220, soft=False))
    v.box(-4, 1, -4, 3, 4, 3, None)
    v.box(-4, 1, -4, 3, 2, 3, WATER)
    for (x, z) in ((-6, -6), (5, -6), (-6, 5), (5, 5)):
        v.box(x, 0, z, x, 4, z, STONE_L)
    v.box(-6, 5, -6, 5, 5, 5, STONE_L)
    v.box(-4, 5, -4, 3, 5, 3, None)
    for x in (-6, 4):                                            # esteios 2×2
        v.box(x, 6, -1, x + 1, 17, 0, TIMBER)
    v.box(-5, 12, -1, 4, 12, 0, WOOD_D)                         # sarilho
    v.box(-4, 13, -1, 3, 13, 0, WOOD)
    v.box(-2, 11, -1, 1, 11, 0, CREAM)                          # corda enrolada
    v.box(6, 12, -1, 6, 12, 0, IRON)
    v.box(7, 10, 0, 7, 12, 0, IRON)                             # manivela
    v.box(7, 10, 1, 7, 10, 1, WOOD_D)
    for y in range(6, 11):
        v.set(-1, y, 0, CREAM)
    v.box(-2, 3, -1, 1, 5, 2, WOOD)                             # balde na borda
    v.box(-2, 4, -1, 1, 4, 2, IRON)
    roof, hs = L.gable_roof(v, -7, 6, -3, 2, 18, 4, ov=2, curve=0.4, sag=0.4, roof=telhado, axis="x")
    for x in (-6, 5):                                           # tesoura: oitão vazado só com a viga
        for z in range(-3, 3):
            v.set(x, 18, z, TIMBER)
    out = L.shear(v, 0.04, y0=6)
    pts = {
        "entradas": [("bocal", (0, 0, 13), 0)],
        "interacao": [("manivela", (9, 0, 3), "girar"), ("balde", (0, 0, 11), "encher")],
        "spawns": [],
    }
    return out, pts


# ============================================================================= chafariz de praça

@asset("fountain_square", nome="Chafariz de praça", categoria="estrutura", sub="agua", lote=2,
       voxel=BIG, frequencia="rara",
       tags=["vila", "praca", "ponto_de_encontro", "centro", "agua"],
       funcao="centro da praça de uma vila de caminho: ponto de encontro, referência de orientação dentro da vila",
       terreno=["caminho", "terra", "grama"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.25,
       area_livre={"raio_u": 2.0},
       colisao={"tipo": "circulo", "raio": 1.7},
       restricoes=["no máximo 1 por assentamento", "praça: espaço aberto em volta (bancos, barraca, quadro)"],
       relacoes=[("centro_de", "tag:praca", None), ("perto_de", "bench_wood", [2.0, 4.0]),
                 ("perto_de", "market_stall", [3.0, 6.0]), ("perto_de", "notice_board", [2.5, 6.0])],
       notas="a variação 'sem lampião' saiu: baixava a altura em 23%, e o lampião é parte da leitura de 'centro'",
       variacoes={})
def fountain_square(lampiao=True):
    """Bacia octogonal baixa de pedra com borda clara larga (dá para sentar), água; coluna central em degraus com
    quatro bicas de ferro e filetes d'água; no topo, um lampião de pedra com luz quente (habitação). A silhueta é
    baixa e larga com um eixo no meio — lê como 'centro' de praça de longe."""
    v = Vox()
    R = 13.5
    def octo(x, z, r):
        ax, az = abs(x + 0.5), abs(z + 0.5)
        return max(ax, az, (ax + az) / 1.414) <= r
    for x in range(-15, 15):
        for z in range(-15, 15):
            if octo(x, z, R + 1):
                for y in range(-4, 1):
                    v.set(x, y, z, STONE)
            if octo(x, z, R) and not octo(x, z, R - 3):
                for y in range(1, 5):
                    v.set(x, y, z, STONE)
            elif octo(x, z, R - 3):
                v.set(x, 1, z, STONE_D)
                v.set(x, 2, z, WATER)
                v.set(x, 3, z, WATER)
    EB.stones(v, seed=230, cell=(3, 2, 3))
    for x in range(-15, 15):                                     # borda larga clara (assento)
        for z in range(-15, 15):
            if octo(x, z, R) and not octo(x, z, R - 3):
                v.set(x, 5, z, STONE_L)
    # coluna central em degraus
    for (y0, y1, r) in ((1, 5, 3.6), (6, 12, 2.6), (13, 15, 1.8)):
        for y in range(y0, y1 + 1):
            for x in range(-5, 5):
                for z in range(-5, 5):
                    if (x + 0.5) ** 2 + (z + 0.5) ** 2 <= r * r:
                        v.set(x, y, z, STONE)
    EB.stones(v, seed=231, cell=(2, 2, 2))
    for y in (5, 12):
        for x in range(-5, 5):
            for z in range(-5, 5):
                if (x + 0.5) ** 2 + (z + 0.5) ** 2 <= (3.8 if y == 5 else 2.9) ** 2:
                    v.set(x, y, z, STONE_L)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):        # bicas e filetes
        bx, bz = (3 if dx > 0 else -4) if dx else 0, (3 if dz > 0 else -4) if dz else 0
        if dx:
            v.box(bx, 10, -1, bx + dx * 2, 10, 0, IRON)
            v.box(bx + dx * 3, 4, -1, bx + dx * 3, 9, 0, WATER)
        else:
            v.box(-1, 10, bz, 0, 10, bz + dz * 2, IRON)
            v.box(-1, 4, bz + dz * 3, 0, 9, bz + dz * 3, WATER)
    if lampiao:                                                  # lampião de pedra no topo
        v.box(-2, 16, -2, 1, 16, 1, STONE_L)
        v.box(-1, 17, -1, 0, 19, 0, GLOW)
        v.set(-1, 18, -1, GLOW_HI)
        for (x, z) in ((-2, -2), (1, -2), (-2, 1), (1, 1)):
            v.box(x, 17, z, x, 19, z, STONE_D)
        v.box(-2, 20, -2, 1, 20, 1, STONE_L)
        v.box(-1, 21, -1, 0, 21, 0, L.CORAL)
    L.moss_patches(v, {STONE, STONE_L, STONE_D}, amount=0.08, max_y=5, seed=232)
    pts = {
        "entradas": [("borda_sul", (0, 0, 18), 0), ("borda_leste", (18, 0, 0), 90)],
        "interacao": [("beber", (0, 0, 17), "beber"), ("sentar", (17, 0, 0), "sentar")],
        "spawns": [("conversa_1", (-12, 0, 17), "npc"), ("conversa_2", (17, 0, -12), "npc")],
    }
    return v, pts


# ============================================================================= lenheiro coberto (1/16)

@asset("woodshed_open", nome="Lenheiro coberto", categoria="estrutura", sub="armazenamento", lote=2,
       voxel=L.FINE, frequencia="incomum",      # 8 mil triângulos (achas a 1/16): acompanha sítios, não toda casa
       tags=["rural", "mata", "cacador", "inverno", "fundos_de_casa"],
       funcao="lenha guardada para o inverno, aberta dos lados: acompanha cabanas e casas de sítio, sinal de "
              "gente morando perto",
       terreno=["grama", "mata", "terra"], apoio={"tipo": "patamar", "margem_u": 0.2}, inclinacao_max_u=0.4,
       area_livre={"frente_u": 1.2, "lados_u": 0.3, "fundos_u": 0.1},
       colisao={"tipo": "caixa", "escala": 0.92},
       restricoes=["encostado em construção ou na borda da mata"],
       relacoes=[("acompanha", "cabin_hunter", [1.5, 5.0]), ("acompanha", "hut_herbalist", [2.0, 6.0]),
                 ("perto_de", "woodpile_axe", [0.8, 3.0])],
       notas="v1 era 1/8: as pontas das toras de 2×2 liam como xadrez (o defeito da lenha do lote 1); refeito em 1/16 "
             "com achas de ponta arredondada e vão entre elas",
       variacoes={"telhado": ["coral", "teal", "palha"], "cheio": [True, False]})
def woodshed_open(telhado="coral", cheio=True):
    """Quatro esteios 3×3 em sapatas de pedra, telhado de uma água (mais alto na frente aberta), estrado de tábuas e o
    vão cheio de ACHAS empilhadas com as pontas para a frente (cerne claro, anel, casca escura, vão entre elas).
    Leitura: 'lenha guardada' de qualquer ângulo, sem paredes."""
    v = Vox()
    X0, X1, Z0, Z1 = -18, 17, -8, 7
    for x in (X0, X1 - 2):
        for z in (Z0, Z1 - 2):
            v.box(x - 1, 0, z - 1, x + 3, 1, z + 3, STONE_L)
            v.box(x, 2, z, x + 2, 24 if z > 0 else 20, z + 2, TIMBER)
    v.box(X0, 2, Z0, X1, 2, Z1, WOOD_D)                         # estrado
    base, line = L.ROOFS[telhado]
    for x in range(X0 - 3, X1 + 4):
        for z in range(Z0 - 3, Z1 + 5):
            y = 21 + int(round(5 * (z - (Z0 - 3)) / ((Z1 + 4) - (Z0 - 3))))
            v.set(x, y, z, line if (z - Z0) % 4 == 0 else base)
            v.set(x, y - 1, z, base)
    for x in (X0 - 3, X1 + 3):
        for z in range(Z0 - 3, Z1 + 5):
            y = 21 + int(round(5 * (z - (Z0 - 3)) / ((Z1 + 4) - (Z0 - 3))))
            v.set(x, y, z, TIMBER)
            v.set(x, y - 1, z, TIMBER)
    v.box(X0, 18, Z0, X1, 19, Z0 + 1, TIMBER)                   # travessa dos fundos
    if cheio:
        L.achas(v, X0 + 3, X1 - 3, 3, 4, Z0 + 1, Z1)
    shade(v)
    pts = {
        "entradas": [("frente", (0, 0, Z1 + 12), 0)],
        "interacao": [("lenha", (0, 0, Z1 + 8), "pegar")],
        "spawns": [],
    }
    return v, pts
