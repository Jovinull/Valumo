"""Laboratório de conteúdo — construções novas da direção E (candidatas; nenhuma substitui a casa aprovada).

Cada construção usa a gramática da casa (base de pedra, estuque liso, enxaimel, telhado côncavo em fiadas, fora de
prumo, luz quente, toque vegetal) e muda a PLANTA, a VOLUMETRIA, o TELHADO e a ENTRADA conforme o papel no
assentamento. Escala: porta ~1,5 u, herói ~1,2 u, voxel 1/8 u. Pontos (entradas, interação, NPC) em voxels do modelo.
"""
import math

import conteudo_lib as L
from conteudo_lib import (BIG, CORAL, CREAM, Face, GLOW, GLOW_HI, GOLD, IRON, LEAF_BUSH, LEATHER, LEATHER_D, PINK, PLASTER,
                          STONE, STONE_D,
                          STONE_L, TEAL, TEAL_D, TERRACOTTA, TIMBER, WATER, WOOD, WOOD_D, WOOD_L, YELLOW, asset)
from envlib import hash3
from voxlib import Vox


def barrel(v, cx, cz, y0, h, water=True):
    """Barril de chuva redondo (aduelas de madeira, dois aros de ferro, água em cima)."""
    for y in range(y0, y0 + h):
        bulge = 0.35 * math.sin(math.pi * (y - y0 + 0.5) / h)
        for x in range(int(cx) - 4, int(cx) + 4):
            for z in range(int(cz) - 4, int(cz) + 4):
                if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 <= (2.4 + bulge) ** 2:
                    v.set(x, y, z, IRON if y in (y0 + 1, y0 + h - 2) else (WOOD if (x + z) % 3 else WOOD_D))
    if water:
        for x in range(int(cx) - 2, int(cx) + 2):
            for z in range(int(cz) - 2, int(cz) + 2):
                if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 <= 2.0:
                    v.set(x, y0 + h - 1, z, WATER)


# ============================================================================= sobrado (dois pavimentos)

@asset("house_tall", nome="Sobrado de enxaimel com balanço", categoria="construcao", sub="residencia_comercio", lote=1,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["vila", "residencia", "comercio", "dois_pavimentos", "centro_da_vila"],
       funcao="casa de comerciante no centro do assentamento: balcão de venda no térreo, moradia em cima",
       terreno=["grama", "terra", "caminho"], apoio={"tipo": "patamar", "margem_u": 0.6}, inclinacao_max_u=0.5,
       area_livre={"frente_u": 2.5, "lados_u": 0.8, "fundos_u": 1.2},
       colisao={"tipo": "caixa", "escala": 0.82},
       notas="a porta dos fundos é acabamento, não entrada: a caixa de colisão simples (centrada na malha, que a "
             "chaminé e a escada deslocam) cobre 0,4 u atrás da parede; com formas compostas ela vira entrada",
       restricoes=["centro do assentamento ou beira do caminho principal", "frente (balcão) virada para o caminho ou a praça"],
       variacoes={"telhado": ["coral", "teal", "mostarda"], "toldo": ["coral", "teal", "nenhum"], "trepadeira": [True, False]})
def house_tall(telhado="coral", toldo="coral", trepadeira=True):
    """Sobrado: térreo de PEDRA (mais estreito) e pavimento de cima em enxaimel que avança 2 voxels sobre a rua
    (balanço com barrotes à mostra e mãos-francesas). Empena virada para a frente (cumeeira em Z, o contrário da casa)
    com óculo; balcão de venda com toldo listrado no térreo; chaminé de pedra encostada na lateral direita."""
    v = Vox()
    gx0, gx1, gz0, gz1 = -14, 13, -10, 9          # térreo de pedra
    ux0, ux1, uz0, uz1 = -16, 15, -10, 11         # pavimento de cima (balanço na frente e nos lados)
    L.foundation(v, gx0 - 1, gz0 - 1, gx1 + 1, gz1 + 1, top=2, seed=12)
    # térreo de pedra assentada, com cunhais claros
    ground = L.stone_block(gx0, 3, gz0, gx1, 15, gz1, seed=13, soft=False)
    for (x, z) in ((gx0, gz0), (gx1, gz0), (gx0, gz1), (gx1, gz1)):
        for y in range(3, 16):
            if (y // 2) % 2 == 0:
                ground.set(x, y, z, STONE_L)
    v.merge(ground)
    # barrotes do balanço: chapa de vigas + pontas à mostra a cada 2 voxels na frente e nos lados
    v.box(ux0, 16, uz0, ux1, 16, uz1, TIMBER)
    for x in range(ux0, ux1 + 1, 2):
        v.set(x, 15, uz1, TIMBER)
    for z in range(uz0, uz1 + 1, 2):
        v.set(ux0, 15, z, TIMBER)
        v.set(ux1, 15, z, TIMBER)
    for (x, z, dx, dz) in ((gx0, gz1, -1, 1), (gx1, gz1, 1, 1)):    # mãos-francesas sob os cantos da frente
        for i in range(3):
            v.set(x + dx * i, 14 + i, z + dz * i, TIMBER)
            v.set(x + dx * i, 13 + i, z + dz * i, TIMBER) if i == 0 else None
    # pavimento de cima: estuque e enxaimel
    L.walls(v, ux0, uz0, ux1, uz1, 17, 28)
    front, back = Face("z", uz1, 1), Face("z", uz0, -1)
    left, right = Face("x", ux0, -1), Face("x", ux1, 1)
    for f, a0, a1 in ((front, ux0, ux1), (back, ux0, ux1), (left, uz0, uz1), (right, uz0, uz1)):
        L.timber_face(v, f, a0, a1, 17, 28, braces=False)
    for a in (-5, 2):
        for y in range(17, 29):
            front.put(v, a, y, TIMBER)
    L.cross_brace(v, front, -4, 1, 18, 27)
    L.window(v, front, -12, -8, 20, 25)
    L.window(v, front, 5, 9, 20, 25, flowers=(YELLOW, CORAL, PINK))
    L.window(v, back, -9, -5, 20, 25, box=False)
    L.cross_brace(v, back, 1, 10, 18, 27)
    L.window(v, left, -2, 3, 20, 25)
    L.window(v, right, -4, 1, 20, 25, box=False)
    # telhado de duas águas com cumeeira em Z (empena para a rua), oitões de estuque
    roof, hs = L.gable_roof(v, ux0, ux1, uz0, uz1, 29, 15, ov=3, curve=0.5, sag=1.0, roof=telhado, axis="z")
    for f, zc in ((front, uz1), (back, uz0)):
        L.gable_wall(v, f, ux0 + 1, ux1 - 1, 29, lambda a, zc=zc: hs[(a, zc)])
    L.round_window(v, front, -0.5, 35, r=3.2)
    # térreo — frente: porta em arco e balcão de venda com toldo
    gf = Face("z", gz1, 1)
    L.door(v, gf, -7.0, 3, w=8, h=7, arch=4.0)
    L.window(v, gf, 1, 10, 6, 11, shutters=False, box=False)
    for x in range(0, 12):                                        # tábua do balcão (cai como aba)
        for d in (1, 2):
            gf.put(v, x, 5, WOOD_L, d=d)
        gf.put(v, x, 4, WOOD_D, d=1)
    for (x, c) in ((1, TERRACOTTA), (3, CREAM), (4, CREAM), (7, TERRACOTTA), (9, YELLOW)):   # potes e pães
        gf.put(v, x, 6, c, d=2)
    if toldo != "nenhum":
        stripe = CORAL if toldo == "coral" else TEAL
        for x in range(0, 12):
            for d in range(1, 5):
                gf.put(v, x, 14 - d // 2, stripe if (x // 2) % 2 == 0 else CREAM, d=d)
    # térreo — lado esquerdo: janelinha e trepadeira; fundos: porta de tábuas e barril de chuva
    lf, bf = Face("x", gx0, -1), Face("z", gz0, -1)
    L.window(v, lf, -4, 0, 7, 11, box=False)
    if trepadeira:
        L.vine(v, lf, 4, 3, 26, seed=7)
    L.plank_door(v, bf, 3, 7, 3, 12)
    barrel(v, -8.5, gz0 - 3.5, 0, 6)
    # escada de pedra de três degraus até a soleira (y = 3), como a casa
    for i, (y, z) in enumerate(((2, gz1 + 1), (1, gz1 + 3), (0, gz1 + 5))):
        for yy in range(-3, y + 1):
            v.box(-12 + i, yy, z, -2 - i, yy, z + 1, STONE_L if yy == y else STONE)
    # lanterna entre a porta e o balcão
    L.lantern(v, -14, 9, gz1 + 2, bracket=(0, -2))
    L.moss_patches(roof, set(roof.v.values()), amount=0.07, max_y=32, seed=5)
    v.merge(roof, overwrite=True)
    out = L.shear(v, 0.08, y0=3)
    # chaminé encostada na lateral direita, depois do cisalhamento (ela tem o próprio torto)
    L.chimney(out, ux1 + 5, -3, 3, 47, lean=0.05, w=1, shoulder=17)
    out.merge(L.stone_block(ux1 + 2, -5, -7, ux1 + 8, 2, 1, seed=31, soft=False))     # sapata da chaminé
    pts = {
        "entradas": [("porta", (-7, 0, gz1 + 8), 0)],          # a porta dos fundos é só acabamento (ver notas)
        "interacao": [("balcao", (5, 1, gz1 + 4), "comerciar"), ("porta", (-7, 1, gz1 + 7), "bater")],
        "spawns": [("comerciante", (5, 0, gz1 + 6), "npc")],
        "area_segura": [],
    }
    return out, pts


# ============================================================================= cabana da herbalista (planta redonda)

def _ring_put(v, r, ang, y, col, width=2):
    """Coluna de 'width' voxels sobre a superfície de um cilindro de raio r, no ângulo ang."""
    for k in range(width):
        a = ang + (k - (width - 1) / 2) * (1.0 / r)
        v.set(int(math.floor(math.cos(a) * r)), y, int(math.floor(math.sin(a) * r)), col)


@asset("hut_herbalist", nome="Cabana redonda da herbalista", categoria="construcao", sub="oficio", lote=1,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["vila", "borda_da_mata", "oficio", "ervas", "planta_redonda"],
       funcao="casa de ofício na borda do assentamento ou isolada na mata: ervas, poções, cura (missões de coleta)",
       terreno=["grama", "mata"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.45,
       area_livre={"frente_u": 2.2, "lados_u": 0.8, "fundos_u": 0.6},
       colisao={"tipo": "circulo", "raio": 1.55},
       restricoes=["borda do assentamento ou clareira na mata", "≥ 4 u de fogo aberto (telhado de palha)"],
       variacoes={"telhado": ["palha", "mostarda", "teal"], "ervas": [True, False]})
def hut_herbalist(telhado="palha", ervas=True):
    """Planta REDONDA (cilindro de estuque com prumos radiais e cinta de enxaimel), telhado CÔNICO côncavo de palha com
    beiral largo e pote de barro no topo, portal reto saliente com porta em arco, ervas secando sob o beiral, canteiro
    elevado e potes. Silhueta: cone pontudo sobre tambor (não lê como cogumelo: cone côncavo, sem chapéu em domo)."""
    v = Vox()
    R = 10.8
    for x in range(-14, 14):                                   # fundação circular
        for z in range(-14, 14):
            if (x + 0.5) ** 2 + (z + 0.5) ** 2 <= 12.3 ** 2:
                v.box(x, -6, z, x, 2, z, STONE)
    EB_stones = __import__("env_b").stones
    EB_stones(v, seed=41)
    v.cyl(0, 0, 3, 14, R, PLASTER)
    for i in range(6):                                          # prumos radiais (o da porta fica no portal)
        ang = math.pi / 2 + math.pi / 6 + i * 2 * math.pi / 6
        for y in range(3, 15):
            _ring_put(v, R - 0.3, ang, y, TIMBER)
    for y in (3, 14):                                            # cintas
        for i in range(160):
            ang = i * 2 * math.pi / 160
            v.set(int(math.floor(math.cos(ang) * (R - 0.3))), y, int(math.floor(math.sin(ang) * (R - 0.3))), TIMBER)
    # portal reto saliente (a porta e a moldura precisam de uma face plana)
    v.box(-6, 3, 8, 5, 15, 11, PLASTER)
    for y in range(3, 16):
        for x in (-6, -5, 4, 5):
            v.set(x, y, 11, TIMBER)
    v.box(-6, 15, 8, 5, 15, 11, TIMBER)
    pf = Face("z", 11, 1)
    L.door(v, pf, 0.0, 3, w=7, h=7, arch=3.6, frame=False)
    # janelas nas laterais (planos x = ±11, salientes 1 voxel)
    for sx in (1, -1):
        f = Face("x", 11 if sx > 0 else -12, sx)
        for z in range(-3, 3):
            for y in range(6, 13):
                v.set(11 if sx > 0 else -12, y, z, PLASTER)
        L.window(v, f, -2, 1, 7, 11, shutters=False, box=False)
    # fundos: janelinha redonda
    L.round_window(v, Face("z", -11, -1), -0.5, 9.5, r=2.4)
    for z in range(-11, -9):
        for x in range(-3, 3):
            for y in range(7, 13):
                if (x + 0.5) ** 2 + (y - 9.5) ** 2 <= 2.6 ** 2 and (x, y, -11) not in v.v:
                    v.set(x, y, -11, PLASTER)
    L.round_window(v, Face("z", -11, -1), -0.5, 9.5, r=2.4)
    # telhado cônico de palha
    roof, hs = L.cone_roof(v, 0.0, 0.0, 14, 16.2, 17, curve=0.6, roof=telhado, seed=3)
    # pote de barro no topo (respiro)
    top = max(hs.values())
    v.box(-1, top - 1, -1, 0, top + 2, 0, L.TERRACOTTA)
    v.box(-2, top + 2, -2, 1, top + 2, 1, L.TERRACOTTA)
    v.box(-1, top + 2, -1, 0, top + 2, 0, L.shadow(L.TERRACOTTA, 1.6))
    out = L.shear(v, 0.06, y0=3)
    # ervas secando sob o beiral (frente-direita): vara + maços pendurados
    if ervas:
        for x in range(4, 14):
            out.set(x, 12, 12, WOOD_D)
        for i, x in enumerate(range(5, 14, 2)):
            col = (LEAF_BUSH, L.light(LEAF_BUSH), YELLOW, LEAF_BUSH, PINK)[i % 5]
            out.set(x, 11, 12, WOOD_D)
            out.box(x, 8, 12, x, 10, 12, col)
            out.set(x, 8, 13, col)
        for y in range(1, 12):                                  # esteio da vara (2 de largura)
            out.set(13, y, 12, WOOD_D)
            out.set(13, y, 13, WOOD_D)
    # canteiro elevado (frente-esquerda) e potes junto ao portal
    out.box(-15, 0, 9, -7, 2, 15, WOOD_D)
    out.box(-14, 2, 10, -8, 2, 14, L.BARK_D)
    for (x, z, c) in ((-13, 11, LEAF_BUSH), (-11, 13, L.light(LEAF_BUSH)), (-9, 11, LEAF_BUSH), (-12, 13, YELLOW),
                      (-9, 13, PINK), (-11, 11, L.light(LEAF_BUSH))):
        out.box(x, 3, z, x + 1, 4, z, c)
        out.set(x, 5, z, c) if c not in (YELLOW, PINK) else out.set(x, 5, z, c)
    for (x, z) in ((7, 13), (-8, 17)):
        out.box(x, 0, z, x + 2, 2, z + 2, L.TERRACOTTA)
        out.box(x, 3, z, x + 2, 3, z + 2, LEAF_BUSH)
        out.set(x + 1, 4, z + 1, L.light(LEAF_BUSH))
    # degrau de pedra até o portal e lanterna pendurada no portal
    for i, (y, z) in enumerate(((2, 12), (1, 14), (0, 16))):
        for yy in range(-3, y + 1):
            out.box(-5 + i, yy, z, 4 - i, yy, z + 1, STONE_L if yy == y else STONE)
    L.lantern(out, 7, 9, 13, bracket=(-1, -2))
    pts = {
        "entradas": [("porta", (0, 0, 19), 0)],
        "interacao": [("ervas", (9, 1, 15), "colher"), ("canteiro", (-11, 1, 17), "examinar")],
        "spawns": [("herbalista", (3, 0, 20), "npc")],
    }
    return out, pts


# ============================================================================= oficina com forja

def _logpile(v, x0, x1, z0, z1, y0, rows):
    """Pilha de lenha: toras de 2×2 deitadas ao longo de z, fileiras desencontradas; pontas em cerne claro com a
    casca escura em volta (a tora lê como tora, não como xadrez)."""
    for r in range(rows):
        off = r % 2
        y = y0 + 2 * r
        for x in range(x0 + off, x1 - 1, 3):
            for z in range(z0, z1 + 1):
                end = z in (z0, z1)
                for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    v.set(x + dx, y + dy, z, WOOD_L if end else (WOOD if dy else WOOD_D))
            if r and x + 2 < x1:
                for z in range(z0, z1 + 1):
                    v.set(x + 2, y, z, WOOD_D)


@asset("workshop_forge", integracao="depende_colisao_composta",
       dependencias=["formas compostas no Collision (interior caminhável): sem elas o interior fica dentro do sólido"], nome="Oficina com forja", categoria="construcao", sub="oficio", lote=1,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["vila", "oficio", "ferreiro", "artesao", "fogo", "aberto"],
       funcao="oficina de ferreiro/artesão: forja acesa, bigorna, bancada; lugar de NPC de ofício e de missões de "
              "consertar/forjar",
       terreno=["terra", "caminho", "grama"], apoio={"tipo": "patamar", "margem_u": 0.6}, inclinacao_max_u=0.35,
       area_livre={"frente_u": 2.5, "lados_u": 1.0, "fundos_u": 0.5},
       colisao={"tipo": "caixa", "escala": 0.85, "formas": "medidas",
                "_nota": "galpão aberto: com a caixa única a forja, a bigorna e a ferreira ficam dentro do sólido; use as "
                         "formas medidas (formas_medidas_u)"},
       restricoes=["fogo: longe de palha (cabana redonda) ≥ 4 u", "fumaça: não sob copa de árvore"],
       variacoes={"telhado": ["coral", "mostarda", "teal"], "placa": [True, False]})
def workshop_forge(telhado="coral", placa=True):
    """Planta em L: sala fechada de estuque e enxaimel à esquerda e GALPÃO ABERTO à direita, sob o mesmo telhado
    comprido (cumeeira em X, baixa). No galpão, a forja de pedra com brasas (luz quente), a coifa e a CHAMINÉ ALTA que
    passa da cumeeira (o traço vertical da silhueta), bigorna num toco, tina de têmpera, bancada com ferramentas,
    lenha. Placa de ofício pendurada com bigorna desenhada."""
    v = Vox()
    X0, X1, Z0, Z1 = -18, 17, -11, 9
    L.foundation(v, X0 - 1, Z0 - 1, X1 + 1, Z1 + 1, top=2, seed=51)
    for x in range(X0, X1 + 1):                                 # piso do galpão em lajes (mais claras)
        for z in range(Z0, Z1 + 1):
            if x > -2:
                v.set(x, 2, z, STONE_L if (x // 3 + z // 3) % 2 else STONE)
    # sala fechada
    rx1 = -3
    L.walls(v, X0, Z0, rx1, Z1, 3, 15)
    fF, fB, fL, fR = Face("z", Z1, 1), Face("z", Z0, -1), Face("x", X0, -1), Face("x", rx1, 1)
    for f, a0, a1 in ((fF, X0, rx1), (fB, X0, rx1), (fL, Z0, Z1), (fR, Z0, Z1)):
        L.timber_face(v, f, a0, a1, 3, 15, braces=(a1 - a0 > 12))
    L.door(v, fF, -7.0, 3, w=7, h=7, arch=3.6)
    L.window(v, fF, -15, -12, 7, 11)
    L.window(v, fB, -13, -8, 7, 11, box=False)
    L.window(v, fL, -4, 0, 7, 11, flowers=(YELLOW, CORAL))
    L.vine(v, fL, -9, 3, 14, seed=12)
    # galpão: muro baixo de pedra nos fundos, esteios 2×2 na frente e no canto
    v.merge(L.stone_block(rx1 + 1, 3, Z0, X1, 7, Z0 + 1, seed=52, soft=False))
    for (px, pz) in ((5, Z1 - 1), (X1 - 1, Z1 - 1), (X1 - 1, Z0)):
        v.box(px, 3, pz, px + 1, 15, pz + 1, TIMBER)
        v.box(px - 1, 2, pz - 1 if pz > 0 else pz, px + 2, 2, pz + 1 if pz < 0 else pz + 2, STONE_L)
    v.box(rx1 + 1, 15, Z1 - 1, X1, 15, Z1, TIMBER)              # frechal da frente aberta
    v.box(X1 - 1, 15, Z0, X1, 15, Z1, TIMBER)
    for z in (Z1 - 1, Z1):                                       # mãos-francesas do vão aberto (2 de fundo, contínuas)
        L.line6(v, (4, 11, z), (1, 14, z), TIMBER)
        L.line6(v, (7, 11, z), (10, 14, z), TIMBER)
        L.line6(v, (X1 - 2, 11, z), (X1 - 5, 14, z), TIMBER)
    # telhado comprido de duas águas (cumeeira em X), baixo
    roof, hs = L.gable_roof(v, X0, X1, Z0, Z1, 16, 11, ov=3, curve=0.5, sag=1.2, roof=telhado, axis="x")
    for f, xc in ((fL, X0),):
        L.gable_wall(v, f, Z0 + 1, Z1 - 1, 16, lambda a, xc=xc: hs[(xc, a)])
    # empena do lado aberto: só a tesoura (vigas), deixando ver o interior
    for z in range(Z0, Z1 + 1):
        top = hs[(X1, z)] - 2
        if abs(z + 1) <= 1:
            for y in range(16, top + 1):
                v.set(X1, y, z, TIMBER)
    for z in range(Z0, Z1 + 1):
        v.set(X1, 16, z, TIMBER)
    # forja: bloco de pedra com brasas, coifa
    v.merge(L.stone_block(6, 3, Z0 + 2, 14, 8, Z0 + 8, seed=53, soft=False))
    for x in range(8, 13):
        for z in range(Z0 + 3, Z0 + 7):
            v.set(x, 9, z, L.FIRE if (x + z) % 3 else L.FIRE_R)
            v.set(x, 8, z, L.FIRE_R)
    v.set(10, 10, Z0 + 4, L.GLOW_HI)
    hood = Vox()
    for y in range(13, 21):
        k = (y - 13) // 3
        hood.box(6 + k, y, Z0 + 2, 14 - k, y, Z0 + 7 - k, STONE)
    __import__("env_b").stones(hood, seed=54, cell=(2, 2, 2))
    for (x, z) in ((6, Z0 + 7), (14, Z0 + 7)):
        hood.box(x, 9, z, x, 12, z, STONE_D)
    v.merge(hood)
    # bigorna num toco, tina de têmpera, bancada com ferramentas encostada na sala
    v.cyl(2, 0, 3, 5, 1.8, WOOD_D)
    v.box(0, 6, -1, 4, 7, 0, IRON)
    v.box(1, 5, -1, 3, 5, 0, IRON)
    v.box(5, 7, -1, 6, 7, 0, IRON)
    barrel(v, 13.5, 0.5, 3, 5)
    v.box(rx1 + 1, 7, -8, rx1 + 3, 7, 5, WOOD_L)
    for z in (-8, 5):
        v.box(rx1 + 3, 3, z, rx1 + 3, 6, z, WOOD_D)
    for (z, c) in ((-6, IRON), (-3, IRON), (0, IRON), (3, IRON)):  # ferramentas penduradas na parede
        v.box(rx1 + 1, 9, z, rx1 + 1, 12, z, WOOD_D)
        v.box(rx1 + 1, 12, z - 1, rx1 + 1, 13, z + 1, c) if z != 0 else v.box(rx1 + 1, 13, z, rx1 + 1, 13, z + 1, c)
    L.moss_patches(roof, set(roof.v.values()), amount=0.06, max_y=19, seed=8)
    v.merge(roof)
    out = L.shear(v, 0.06, y0=3)
    # chaminé alta da forja (passa da cumeeira) e lenha do lado de fora
    L.chimney(out, 10, Z0 + 4, 21, 44, lean=0.07, w=1, cap=L.STONE_D)
    _logpile(out, X1 + 3, X1 + 10, -8, 0, 0, 2)
    out.box(X1 + 3, 0, -9, X1 + 9, 0, 1, STONE_D)
    # placa de ofício pendurada no frechal (bigorna desenhada)
    if placa:
        for y in range(9, 15):
            out.set(1, y, Z1 + 1, IRON) if y >= 13 else None
        out.box(-2, 8, Z1 + 1, 4, 12, Z1 + 1, WOOD_L)
        for (x, y) in ((-1, 10), (0, 10), (1, 10), (2, 10), (3, 11), (0, 9), (1, 9), (-1, 11), (0, 11), (1, 11), (2, 11)):
            out.set(x, y, Z1 + 2, IRON)
        out.box(-2, 13, Z1 + 1, 4, 13, Z1 + 1, WOOD_D)
    for i, (y, z) in enumerate(((2, Z1 + 1), (1, Z1 + 3), (0, Z1 + 5))):
        for yy in range(-3, y + 1):
            out.box(-11 + i, yy, z, -3 - i, yy, z + 1, STONE_L if yy == y else STONE)
    # pedra de amolar no pátio (roda vertical em cavalete, manivela de ferro): o sinal de ofício visto da frente
    gx, gz = 18, Z1 + 7                                          # no canto do pátio: não fecha a frente do galpão
    for (x, z) in ((gx - 3, gz - 1), (gx + 2, gz - 1), (gx - 3, gz + 1), (gx + 2, gz + 1)):
        out.box(x, 0, z, x, 4, z, WOOD_D)
    out.box(gx - 3, 4, gz - 1, gx + 2, 4, gz + 1, WOOD)
    for y in range(3, 11):
        for x in range(gx - 4, gx + 4):
            d = math.hypot(x + 0.5 - gx, y + 0.5 - 7)
            if d <= 3.6:
                out.set(x, y, gz, STONE_L if d > 2.4 else STONE)
    out.box(gx, 7, gz - 1, gx, 7, gz - 2, IRON)
    out.box(gx, 7, gz - 2, gx, 9, gz - 2, IRON)
    pts = {
        "entradas": [("porta", (-7, 0, Z1 + 8), 0), ("galpao", (10, 0, Z1 + 4), 0)],
        "interacao": [("forja", (10, 3, Z0 + 10), "usar"), ("bigorna", (2, 3, 4), "usar"),
                      ("bancada", (rx1 + 6, 3, -2), "examinar")],
        "spawns": [("ferreira", (6, 3, 2), "npc")],
        "area_segura": [(-1, -2, 16, 8)],
    }
    return out, pts


# ============================================================================= cabana do caçador (troncos, "saltbox")

@asset("cabin_hunter", nome="Cabana do caçador", categoria="construcao", sub="rural", lote=1,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["mata", "isolada", "rural", "cacador", "trilha"],
       funcao="cabana isolada na mata ou na beira da trilha: caçador/batedor, abrigo, missões de caça e rastreio",
       terreno=["mata", "grama"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.5,
       area_livre={"frente_u": 2.0, "lados_u": 0.8, "fundos_u": 0.8},
       colisao={"tipo": "caixa", "escala": 0.8},
       notas="APROVADA COM PAREDES DE TRONCOS (decisão do proprietário, lote 1): exemplo aprovado de arquitetura "
             "RÚSTICA ISOLADA — florestas, trilhas, regiões afastadas, abrigos de caçador, pequenos acampamentos. NÃO "
             "autoriza casas da vila a abandonar pedra + estuque + enxaimel. Preservar o telhado assimétrico (saltbox) e "
             "os oitões de estuque.",
       restricoes=["fora do núcleo da vila: mata, trilha, borda ou isolada", "não substitui casas de estuque no assentamento"],
       variacoes={"telhado": ["teal", "coral", "mostarda"], "couro": [True, False]})
def cabin_hunter(telhado="teal", couro=True):
    """Cabana baixa de TRONCOS empilhados (cantos cruzados com pontas claras), telhado assimétrico ("saltbox"): água da
    frente curta e da trás longa, descendo sobre o telheiro da lenha. Oitões de estuque e enxaimel (a família da casa),
    chaminé de pedra externa na empena, alpendre de dois esteios sobre a porta de tábuas, varal com couro esticado,
    cepo com machado."""
    v = Vox()
    X0, X1, Z0, Z1 = -11, 10, -8, 7
    L.foundation(v, X0 - 1, Z0 - 1, X1 + 1, Z1 + 1, top=1, seed=61)
    for y in range(2, 13):                                       # troncos: cursos de 2, cantos cruzados
        k = (y - 2) // 2
        top = (y - 2) % 2 == 1
        col = L.light(WOOD) if top else (WOOD if k % 2 == 0 else WOOD_D)
        ex = 2 if k % 2 == 0 else 0
        ez = 0 if k % 2 == 0 else 2
        for x in range(X0 - ex, X1 + ex + 1):
            for z in (Z0, Z1):
                v.set(x, y, z, col)
        for z in range(Z0 - ez, Z1 + ez + 1):
            for x in (X0, X1):
                v.set(x, y, z, col)
        v.box(X0 + 1, y, Z0 + 1, X1 - 1, y, Z1 - 1, WOOD_D)
    for (x, z) in ((X0 - 2, Z0), (X1 + 2, Z0), (X0 - 2, Z1), (X1 + 2, Z1), (X0, Z0 - 2), (X1, Z0 - 2), (X0, Z1 + 2), (X1, Z1 + 2)):
        for y in range(2, 13):
            if v.get(x, y, z):
                v.set(x, y, z, WOOD_L)                           # pontas dos troncos (cerne claro)
    # telhado saltbox: cumeeira deslocada para a frente; água de trás longa até o telheiro
    base, line = L.ROOFS[telhado]
    rz = 2                                                       # cumeeira em z = 2
    def h(x, z):
        if z >= rz:
            t = 1 - (z - rz) / (Z1 + 3 - rz + 0.5)
            return 13 + int(round(9 * L.concave(t, 0.4)))
        t = 1 - (rz - z) / (rz - (Z0 - 8) + 0.5)
        return 7 + int(round(15 * L.concave(t, 0.35)))
    roof, hs = L.roof_field(v, range(X0 - 3, X1 + 4), range(Z0 - 8, Z1 + 4), h, base, line,
                            edge=lambda x, z: x in (X0 - 3, X1 + 3))
    for x in range(X0 - 3, X1 + 4):
        top = max(hs[(x, rz)], hs[(x, rz - 1)])
        roof.set(x, top + 1, rz, line if x % 4 else TIMBER)
        roof.set(x, top + 1, rz - 1, line if x % 4 else TIMBER)
    v.merge(roof)
    for xc in (X0, X1):                                          # oitões de estuque com enxaimel
        f = Face("x", xc, -1 if xc < 0 else 1)
        for z in range(Z0, Z1 + 1):
            top = hs[(xc, z)] - 2
            for y in range(13, top + 1):
                beam = z in (Z0, Z1, rz, rz - 1) or y == 13
                f.put(v, z, y, TIMBER if beam else PLASTER)
    # telheiro dos fundos: esteios e lenha
    for x in (X0 - 1, X1):
        v.box(x, 0, Z0 - 7, x + 1, hs[(x, Z0 - 7)] - 1, Z0 - 6, TIMBER)
    _logpile(v, X0 + 2, X1 - 3, Z0 - 5, Z0 - 2, 0, 3)
    # frente: porta de tábuas, janela, alpendre com dois esteios
    fF = Face("z", Z1, 1)
    L.plank_door(v, fF, -6, -1, 2, 11, col=WOOD)
    L.window(v, fF, 3, 6, 6, 9, box=False)
    for x in (-9, 2):
        v.box(x, 0, Z1 + 4, x + 1, 12, Z1 + 5, TIMBER)
    for x in range(-11, 5):
        for z in range(Z1 + 1, Z1 + 7):
            v.set(x, 13 - (z - Z1) // 3, z, base if (z - Z1) % 3 else line)
    # fundos e lado direito: janelinhas
    L.window(v, Face("z", Z0, -1), 2, 5, 6, 9, shutters=False, box=False)
    L.window(v, Face("x", X1, 1), -2, 1, 6, 9, box=False)
    out = L.shear(v, 0.05, y0=3)
    L.chimney(out, X0 - 4, -1, -3, 30, lean=0.03, w=1, shoulder=10, cap=L.STONE_D)
    out.merge(L.stone_block(X0 - 7, -5, -4, X0 - 2, 1, 2, seed=62, soft=False))
    # varal de couro (lado direito) e cepo com machado
    if couro:
        for z in (-1, 6):
            out.box(X1 + 5, 0, z, X1 + 6, 13, z + 1, WOOD_D)
        out.box(X1 + 5, 13, -1, X1 + 6, 13, 7, WOOD_D)
        for z in range(1, 6):
            for y in range(4, 12):
                edge = z in (1, 5) or y in (4, 11)
                out.set(X1 + 5, y, z, LEATHER_D if edge else LEATHER)
        for y in range(4, 12, 2):
            out.set(X1 + 5, y, 0, CREAM)
            out.set(X1 + 5, y, 6, CREAM)
    out.cyl(X1 + 6, Z1 + 6, 0, 3, 1.8, WOOD)
    out.box(X1 + 5, 4, Z1 + 6, X1 + 7, 4, Z1 + 6, WOOD_D)
    out.box(X1 + 6, 4, Z1 + 5, X1 + 6, 6, Z1 + 5, IRON)
    L.lantern(out, 4, 8, Z1 + 3, bracket=(0, -2))
    pts = {
        "entradas": [("porta", (-4, 0, Z1 + 9), 0)],
        "interacao": [("couro", (X1 + 8, 1, 3), "examinar"), ("lenha", (-1, 1, Z0 - 10), "pegar")],
        "spawns": [("cacador", (6, 0, Z1 + 9), "npc")],
    }
    return out, pts




# ============================================================================= torre de vigia

@asset("tower_watch", nome="Torre de vigia", categoria="construcao", sub="defesa_orientacao", lote=1,
       voxel=BIG, frequencia="rara", wind=True,
       tags=["vila", "borda", "estrada", "marco", "orientacao", "mirante"],
       funcao="torre na borda do assentamento ou da estrada: referência de orientação vista de longe, mirante, posto "
              "de guarda (NPC), sino de alarme",
       terreno=["grama", "terra", "colina"], apoio={"tipo": "patamar", "margem_u": 0.4}, inclinacao_max_u=0.6,
       area_livre={"frente_u": 1.8, "lados_u": 0.6, "fundos_u": 0.6},
       colisao={"tipo": "caixa", "escala": 0.62},
       restricoes=["borda do assentamento ou saída da trilha, com vista livre", "no máximo 1 por assentamento"],
       variacoes={"telhado": ["coral", "teal", "mostarda"], "hera": [True, False]})
def tower_watch(telhado="coral", hera=True):
    """Fuste de pedra quadrado com talude em degrau (cornija clara), porta em arco e seteiras iluminadas; no alto, o
    PISO DE VIGIA em balanço (mãos-francesas curvas), guarda-corpo, quatro esteios e telhado de quatro águas côncavo
    com cata-vento; sino dourado e lanterna sob o telhado; escada de mão na lateral. Lê como torre a 30 u."""
    v = Vox()
    v.merge(L.stone_block(-9, -6, -9, 8, 11, 8, seed=71, soft=False))
    v.merge(L.stone_block(-8, 12, -8, 7, 27, 7, seed=72, soft=False))
    v.box(-9, 12, -9, 8, 12, 8, STONE_L)                         # cornija do talude
    for (x, z) in ((-9, -9), (8, -9), (-9, 8), (8, 8)):
        v.set(x, 12, z, None)
    fF = Face("z", 8, 1)
    L.door(v, fF, -0.5, 1, w=6, h=7, arch=3.0)
    for (f, a) in ((Face("z", 7, 1), -0.5), (Face("z", -8, -1), -0.5), (Face("x", 7, 1), -0.5), (Face("x", -8, -1), -0.5)):
        for y in range(17, 22):                                  # seteiras iluminadas, uma por face
            f.put(v, int(a), y, GLOW if y not in (17, 21) else STONE_D)
    # piso de vigia em balanço
    v.box(-12, 28, -12, 11, 29, 11, WOOD)
    for x in range(-12, 12):
        for z in (-12, 11):
            v.set(x, 28, z, TIMBER)
    for z in range(-12, 12):
        for x in (-12, 11):
            v.set(x, 28, z, TIMBER)
    for (f, a0, a1) in ((Face("z", 7, 1), -8, 7), (Face("z", -8, -1), -8, 7), (Face("x", 7, 1), -8, 7), (Face("x", -8, -1), -8, 7)):
        for a in (a0 + 2, a1 - 3):
            for k in (0, 1):                                     # mãos-francesas (traço contínuo, 2 de largura)
                L.line6(v, f.at(a + k, 22, 0), f.at(a + k, 25, 2), TIMBER)
                L.line6(v, f.at(a + k, 25, 2), f.at(a + k, 27, 4), TIMBER)
    # guarda-corpo e esteios
    for (x, z) in ((-12, -12), (10, -12), (-12, 10), (10, 10), (-1, -12), (-1, 10), (-12, -1), (10, -1)):
        v.box(x, 30, z, x + 1, 33, z + 1, TIMBER)
    for x in range(-12, 12):                                     # corrimão + balaústres inteiros (30–32)
        for z in (-12, 11):
            v.set(x, 33, z, WOOD_D)
            if x % 3 == 0:
                v.box(x, 30, z, x, 32, z, WOOD)
    for z in range(-12, 12):
        for x in (-12, 11):
            v.set(x, 33, z, WOOD_D)
            if z % 3 == 0:
                v.box(x, 30, z, x, 32, z, WOOD)
    for (x, z) in ((-12, -12), (10, -12), (-12, 10), (10, 10)):
        v.box(x, 34, z, x + 1, 40, z + 1, TIMBER)
    roof, hs = L.hip_roof(v, -12, 11, -12, 11, 41, 10, ov=2, curve=0.4, roof=telhado)
    top = max(hs.values())
    v.box(-1, top + 1, -1, 0, top + 2, 0, TIMBER)
    v.box(-1, top + 3, -1, 0, top + 4, 0, IRON)
    v.box(-3, top + 5, -1, 2, top + 5, 0, IRON)                  # cata-vento
    v.box(1, top + 6, -1, 2, top + 6, 0, IRON)
    # sino e lanterna sob o telhado
    v.box(-1, 39, -1, 0, 40, 0, TIMBER)
    v.box(-1, 38, -1, 0, 38, 0, GOLD)                            # sino: ombro estreito, boca larga
    v.box(-2, 36, -2, 1, 37, 1, GOLD)
    v.box(-3, 35, -2, 2, 35, 1, GOLD)
    v.box(-2, 35, -3, 1, 35, 2, GOLD)
    v.box(-1, 34, -1, 0, 34, 0, IRON)                            # badalo
    L.lantern(v, 9, 35, 9)
    # escada de mão na lateral direita (presa à parede: o cisalhamento não a parte)
    for y in range(0, 28):
        v.set(8, y, -3, WOOD_D)
        v.set(8, y, 2, WOOD_D)
        if y % 3 == 0:
            for z in range(-3, 3):
                v.set(9, y, z, WOOD)
        v.set(9, y, -3, WOOD_D)
        v.set(9, y, 2, WOOD_D)
    if hera:
        L.vine(v, Face("x", -9, -1), -5, 0, 24, seed=73)
    L.moss_patches(v, {STONE, STONE_L, STONE_D}, amount=0.3, max_y=12, seed=74)
    out = L.shear(v, 0.04, y0=3)
    for i, (y, z) in enumerate(((0, 9), (0, 11))):
        out.box(-4 + i, -3, z, 3 - i, y, z + 1, STONE_L)
    # corda do sino: desce do badalo, passa por uma argola no beiral do piso e cai pela frente até a altura da mão
    L.line6(out, (0, 34, 0), (0, 30, 12), CREAM)
    for y in range(6, 30):
        out.set(0, y, 12, CREAM)
    out.box(-1, 5, 12, 1, 5, 12, WOOD_D)                         # punho de madeira
    pts = {
        "entradas": [("porta", (-1, 0, 15), 0), ("escada", (12, 0, -1), 90)],
        "interacao": [("corda_do_sino", (0, 0, 15), "tocar"), ("porta", (-1, 0, 14), "bater")],
        "spawns": [("vigia", (4, 0, 15), "npc")],
        "observacao": [("piso_de_vigia", (-1, 30, 0), 0)],
    }
    return out, pts
