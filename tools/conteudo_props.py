"""Laboratório de conteúdo, lote 2 — props e estruturas pequenas de assentamento (direção E, voxel 1/16).

Objetos do dia a dia que dizem QUEM mora ali e O QUE faz, sem texto: feira, praça, campo, lenha, ofício. Regras:
  * silhueta reconhecível na câmera de jogo (nada de vinte barris parecidos: cada prop tem forma própria);
  * três tons por seção (fio claro, corpo, sulco escuro) e sombra pela normal (shade);
  * mesma cultura da vila: madeira #b07a48/#84533a/#d3a066, ferro, pano creme e coral, palha;
  * vegetação presa a sólido é fosca (só balança o que é solto e baixo).
"""
import math

import env_b as EB
import conteudo_lib as L
from conteudo_lib import (CORAL, CREAM, FINE, GLOW, GLOW_HI, IRON, IVY, LEAF_BUSH, MOSS_FIX, PINK, STONE, STONE_D, STONE_L,
                          STRAW, STRAW_D, TEAL, TEAL_D, TERRACOTTA, TIMBER, WOOD, WOOD_D, WOOD_L, YELLOW, asset, light,
                          shade, shadow)
from envlib import hash3
from voxlib import Vox

BIG = L.BIG
BREAD = L.P("#d39a5a")                 # pão (tom da camurça dos personagens)
BURLAP = L.P("#cdb48a")                # saco de aniagem
CABBAGE = L.P("#86b04a", "foliage")    # folha de horta (baixa: balança pouco)
SOIL = L.P("#7a5440")                  # terra lavrada


def _post(v, x, z, y0, y1, col=None, w=2):
    v.box(x, y0, z, x + w - 1, y1, z + w - 1, col or WOOD_D)


def _wheel(v, cx, cy, z0, r=6.0, depth=2):
    """Roda de carroça vertical (plano x-y): aro, raios em cruz dupla e cubo de ferro."""
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            for z in range(z0, z0 + depth):
                if r - 1.3 <= d <= r:
                    v.set(x, y, z, WOOD_D)                       # aro de um tom (o anel em dois tons custava ~1 mil triângulos)
                elif d < 1.6:
                    v.set(x, y, z, IRON)
    for k in range(6):                                           # raios: traço contínuo do cubo até o aro
        a = k * math.pi / 3 + 0.26
        for z in range(z0, z0 + depth):
            L.line6(v, (cx - 0.5 + math.cos(a) * 1.2, cy - 0.5 + math.sin(a) * 1.2, z),
                    (cx - 0.5 + math.cos(a) * (r - 1.0), cy - 0.5 + math.sin(a) * (r - 1.0), z), WOOD)


# ============================================================================= carroça de feno

@asset("cart_hay", nome="Carroça de feno", categoria="prop", sub="transporte", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["rural", "campo", "estrada", "colheita", "comunidade_rural", "vila"],
       funcao="trabalho do campo e da estrada: carroça parada, varais no chão, feno amontoado",
       terreno=["grama", "terra", "caminho"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.3,
       area_livre={"raio_u": 0.6}, colisao={"tipo": "caixa", "escala": 0.85},
       restricoes=["fora do leito do caminho (encostada na borda)"],
       relacoes=[("perto_de", "granary_raised", [1.5, 5.0]), ("perto_de", "crop_field", [1.0, 6.0])],
       notas="a carga 'vazia' saiu das variações: baixava a altura em 26% (não é variação segura)",
       variacoes={"carga": ["feno", "sacos"]})
def cart_hay(carga="feno"):
    """Caixa de tábuas sobre eixo com DUAS RODAS GRANDES de raios (a silhueta da roda é o que lê de longe), varais
    pousados no chão para a frente, feno amontoado em domo (palha), forcado espetado. Vista de lado: roda + caixa
    + varal; nenhum prop aprovado tem roda."""
    v = Vox()
    x0, x1, z0, z1 = -12, 11, -8, 7
    v.box(x0, 7, z0, x1, 8, z1, WOOD_D)                          # leito
    for x in range(x0, x1 + 1):                                  # laterais em tábuas horizontais
        for y in (9, 10, 11, 12):
            for z in (z0, z1):
                v.set(x, y, z, WOOD_L if y == 12 else (WOOD_D if y == 10 else WOOD))
    for z in range(z0, z1 + 1):
        for y in (9, 10, 11, 12):
            for x in (x0, x1):
                v.set(x, y, z, WOOD_L if y == 12 else WOOD)
    for x in (x0 + 4, -1, x1 - 4):                               # fueiros
        for z in (z0, z1):
            v.box(x, 9, z, x, 13, z, TIMBER)
    v.box(-1, 5, z0 - 3, 0, 6, z1 + 3, IRON)                     # eixo
    _wheel(v, -0.5, 6.0, z0 - 2, r=6.5, depth=1)                 # rodas de 1 voxel (0,06 u): a espessura real nessa
    _wheel(v, -0.5, 6.0, z1 + 2, r=6.5, depth=1)                 # escala, e metade do custo das de 2
    for z in (z0 + 3, z1 - 4):                                   # varais na horizontal, apoiados num cavalete
        v.box(x1, 7, z, x1 + 12, 8, z + 1, WOOD_D)
    v.box(x1 + 10, 6, z0 + 2, x1 + 11, 6, z1 - 2, WOOD)          # travessa do cavalete
    for z in (z0 + 2, z1 - 3):
        v.box(x1 + 10, 0, z, x1 + 11, 5, z + 1, WOOD)
    if carga == "feno":
        hay = Vox()
        L.sblob(hay, -0.5, 12.5, -0.5, 11.0, 5.0, 7.5, STRAW, p=2.2)
        for k in [k for k in hay.v if k[1] < 13]:
            del hay.v[k]
        shade(hay)                                               # feno só pela normal (manchas custavam ~600 triângulos)
        v.merge(hay)
    elif carga == "sacos":                                       # sacos em dois andares + um no topo (mesmo volume)
        for i, (x, y, z) in enumerate(((-9, 9, -5), (-4, 9, -5), (1, 9, -5), (6, 9, -5), (-9, 9, 1), (-4, 9, 1), (1, 9, 1),
                                        (6, 9, 1), (-6, 13, -2), (-1, 13, -2), (4, 13, -2))):
            v.box(x, y, z, x + 3, y + 3, z + 3, BURLAP)
            v.box(x + 1, y + 4, z + 1, x + 2, y + 4, z + 2, STRAW_D)
    L.line6(v, (x1 - 2, 0, z1 + 4), (x1 - 6, 14, z1 + 4), WOOD)    # forcado encostado na roda
    for dx in (-1, 0, 1):
        v.box(x1 - 6 + dx, 15, z1 + 4, x1 - 6 + dx, 17, z1 + 4, IRON)
    v.box(x1 - 7, 14, z1 + 4, x1 - 5, 14, z1 + 4, IRON)
    shade(v)
    return v, {"interacao": [("carga", (-1, 0, z1 + 10), "examinar")]}


# ============================================================================= banco de praça

@asset("bench_wood", nome="Banco de praça", categoria="prop", sub="mobiliario", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["vila", "praca", "beira_de_caminho", "descanso", "ponto_de_encontro"],
       funcao="lugar para sentar: praça, porta de casa, beira de caminho (futuro ponto de NPC sentado)",
       terreno=["grama", "caminho", "terra"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.2,
       area_livre={"frente_u": 0.8, "raio_u": 0.3}, colisao={"tipo": "caixa", "escala": 0.9, "pisavel": True},
       restricoes=["assento virado para a praça, o caminho ou a vista"],
       relacoes=[("perto_de", "fountain_square", [2.0, 4.0]), ("perto_de", "tag:porta", [0.5, 2.0])],
       notas="a variação 'sem encosto' foi retirada: mudava a altura em 46% (não é variação segura; seria outro objeto)",
       variacoes={})
def bench_wood(encosto=True):
    """Assento de duas tábuas grossas sobre pés de toro em X, encosto de uma tábua com dois montantes curvos. Altura
    de assento ~0,4 u: o herói (1,2 u) caberia sentado."""
    v = Vox()
    v.box(-11, 6, -3, 10, 6, 2, WOOD_L)
    v.box(-11, 5, -3, 10, 5, 2, WOOD)
    for x in (-11, -1, 10):
        v.set(x, 6, -3, WOOD_D)
    for x in (-9, 7):                                            # pés em X
        for i in range(5):
            v.box(x, i, -3 + i // 2, x + 1, i, -3 + i // 2, WOOD_D)
            v.box(x, i, 2 - i // 2, x + 1, i, 2 - i // 2, WOOD_D)
    if encosto:
        for x in (-10, 8):
            v.box(x, 7, -3, x + 1, 12, -3, WOOD_D)
            v.set(x, 12, -4, WOOD_D)
        v.box(-11, 10, -4, 10, 12, -4, WOOD)
        v.box(-11, 12, -4, 10, 12, -4, WOOD_L)
    shade(v)
    return v, {"interacao": [("sentar", (0, 0, 8), "sentar")]}


# ============================================================================= mesa com bancos

@asset("table_bench", nome="Mesa com bancos", categoria="prop", sub="mobiliario", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["vila", "praca", "estalagem", "festa", "ponto_de_encontro"],
       funcao="mesa ao ar livre (praça, frente da estalagem): refeição, conversa, jogo — espaço público",
       terreno=["grama", "caminho", "terra"], apoio={"tipo": "patamar", "margem_u": 0.2}, inclinacao_max_u=0.15,
       area_livre={"raio_u": 0.8}, colisao={"tipo": "caixa", "escala": 0.9},
       relacoes=[("perto_de", "house", [2.0, 5.0]), ("perto_de", "fountain_square", [3.0, 6.0])],
       notas="v1 tinha 'servido: não', que tirava a jarra e baixava a altura em 29%; a variação segura é O QUE está servido",
       variacoes={"servido": ["refeicao", "festa"]})
def table_bench(servido="refeicao"):
    """Tampo comprido de tábuas sobre cavaletes, dois bancos corridos dos lados; jarra de barro, canecas, pão e uma
    tigela de frutas. Lê como 'mesa posta' de cima (câmera de jogo)."""
    v = Vox()
    v.box(-14, 11, -5, 13, 11, 4, WOOD_L)
    v.box(-14, 10, -5, 13, 10, 4, WOOD)
    for x in (-12, 11):                                          # cavaletes
        for i in range(10):
            v.box(x, i, -4 + i // 3, x + 1, i, -4 + i // 3, WOOD_D)
            v.box(x, i, 3 - i // 3, x + 1, i, 3 - i // 3, WOOD_D)
    for z0 in (-11, 8):                                          # bancos corridos
        v.box(-13, 6, z0, 12, 6, z0 + 3, WOOD_L)
        for x in (-11, 10):
            v.box(x, 0, z0 + 1, x + 1, 5, z0 + 2, WOOD_D)
    v.box(-6, 12, -2, -4, 15, 0, TERRACOTTA)                     # jarra (sempre)
    v.box(-5, 16, -1, -5, 16, -1, shadow(TERRACOTTA, 1.4))
    if servido == "refeicao":
        for (x, z) in ((-10, 2), (2, -3), (8, 2)):              # canecas
            v.box(x, 12, z, x + 1, 13, z + 1, WOOD)
            v.set(x, 13, z, STRAW)
        v.box(3, 12, 0, 7, 13, 2, BREAD)                         # pão
        for (x, z, c) in ((-1, -2, CORAL), (0, -1, YELLOW), (-2, -1, CORAL)):
            v.box(x, 12, z, x, 12, z, c)
    else:                                                        # festa: bolo, flores num pote, guirlanda de pano
        v.box(2, 12, -2, 7, 13, 2, BREAD)
        v.box(3, 14, -1, 6, 14, 1, CREAM)
        v.box(9, 12, -1, 10, 14, 0, TERRACOTTA)
        v.box(9, 15, -1, 10, 15, 0, PINK)
        for x in range(-13, 13, 2):
            v.set(x, 11, 5, CORAL if (x // 2) % 2 else YELLOW)
    shade(v)
    return v, {"interacao": [("sentar_norte", (0, 0, -16), "sentar"), ("sentar_sul", (0, 0, 15), "sentar")],
               "spawns": [("comensal_1", (-6, 0, 15), "npc"), ("comensal_2", (6, 0, -16), "npc")]}


# ============================================================================= pilha de caixotes e sacos

@asset("crate_stack", nome="Pilha de caixotes e sacos", categoria="prop", sub="carga", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["vila", "comercio", "estrada", "armazem", "feira"],
       funcao="carga parada: frente de comércio, porta de depósito, pátio de feira — diz 'aqui se troca coisa'",
       terreno=["caminho", "terra", "grama"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.2,
       area_livre={"raio_u": 0.3}, colisao={"tipo": "caixa", "escala": 0.85, "pisavel": True},
       restricoes=["encostada em parede ou barraca, nunca no leito do caminho"],
       relacoes=[("perto_de", "market_stall", [0.5, 3.0]), ("perto_de", "shed_storage", [0.3, 2.0])],
       variacoes={"semente": [1, 2, 3]})
def crate_stack(semente=1):
    """Dois caixotes de ripas empilhados em degrau + um aberto com maçãs + três sacos de aniagem amarrados tombados
    uns nos outros: silhueta em escada irregular, nunca o cubo do caixote aprovado."""
    v = Vox()
    s = semente
    def crate(x0, y0, z0, n=9, open_top=False):
        for x in range(x0, x0 + n):
            for y in range(y0, y0 + n):
                for z in range(z0, z0 + n):
                    on = (x in (x0, x0 + n - 1)) + (y in (y0, y0 + n - 1)) + (z in (z0, z0 + n - 1))
                    if open_top and y == y0 + n - 1 and on < 2:
                        continue
                    if on >= 2:
                        v.set(x, y, z, WOOD_D)
                    elif on == 1:
                        v.set(x, y, z, WOOD if (y - y0) % 3 else WOOD_D)
                    elif not (open_top and y >= y0 + n - 2):
                        v.set(x, y, z, STRAW_D)                  # MACIÇO por dentro: caixa oca gerava as paredes internas
                                                                 # (invisíveis) e dobrava as faces
    crate(-10, 0, -5)
    crate(-7, 9, -4)
    crate(0, 0, -6, n=8, open_top=True)
    for (x, z, c) in ((2, -4, CORAL), (4, -3, CORAL), (3, -2, YELLOW), (5, -5, CORAL)):
        v.box(x, 7, z, x + 1, 8, z + 1, c)
    sacks = [(-1, 0, 4), (4, 0, 3), (1, 5, 3)] if s == 1 else [(-2, 0, 4), (3, 0, 4), (0, 5, 4)]
    for i, (x, y, z) in enumerate(sacks):
        sk = Vox()
        L.sblob(sk, x + 2, y + 2.5, z + 2, 3.2, 2.8, 2.6, BURLAP, p=3.0)    # saco cheio: mais quadrado (e funde melhor)
        v.merge(sk)
        v.box(x + 1, y + 5, z + 1, x + 2, y + 6, z + 2, STRAW_D)
    shade(v)
    return v, {}


# ============================================================================= cestos

@asset("basket_group", nome="Cestos de colheita", categoria="prop", sub="carga", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["rural", "feira", "herbalista", "horta", "colheita"],
       funcao="produto colhido: horta, feira, porta da herbalista — distingue rural de comércio pelo conteúdo",
       terreno=["grama", "terra", "caminho"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.2,
       area_livre={"raio_u": 0.2}, colisao={"tipo": "circulo", "raio": 0.4},
       relacoes=[("perto_de", "crop_field", [0.3, 2.0]), ("perto_de", "hut_herbalist", [0.5, 3.0]),
                 ("perto_de", "market_stall", [0.3, 2.0])],
       publicar_variantes=True,
       integracao="restrito_a_conjuntos",
       notas="decisão do proprietário (lote 2): só como COMPLEMENTO contextual de um conjunto ou de uma construção "
             "(horta, feira, herbalista); nunca decoração isolada — em preto vira mancha, lê por cor e contexto",
       variacoes={"conteudo": ["horta", "ervas", "cogumelos"]})
def basket_group(conteudo="horta"):
    """Três cestos de vime (trama em xadrez largo de 2 voxels, borda clara), alturas diferentes, um tombado: repolhos
    e cenouras, ou maços de ervas, ou cogumelos BEGE (nunca o vermelho do fungo inimigo)."""
    v = Vox()
    def basket(cx, cz, r, h, fill):
        for y in range(h):
            rr = r - 0.6 + 0.6 * y / max(1, h - 1)
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                for z in range(int(cz - r) - 1, int(cz + r) + 2):
                    d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                    if d <= rr and (d > rr - 1.2 or y == 0):
                        v.set(x, y, z, STRAW if y == h - 1 else (STRAW if (y // 2) % 2 else STRAW_D))   # trama em fiadas
        for x in range(int(cx - r), int(cx + r) + 1):
            for z in range(int(cz - r), int(cz + r) + 1):
                if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r - 1.4:
                    fill(x, h - 1, z)
    def horta(x, y, z):                                          # repolhos e cenouras em blocos de 2 (fundem)
        c = CORAL if (x // 2 + z // 2) % 3 == 0 else LEAF_BUSH
        v.set(x, y, z, c)
        v.set(x, y + 1, z, c)
    def ervas(x, y, z):
        v.set(x, y, z, IVY)
        v.set(x, y + 1, z, PINK if (x // 2 + z // 2) % 2 else IVY)
    def cogumelos(x, y, z):
        v.set(x, y, z, CREAM)
        v.set(x, y + 1, z, CREAM if (x // 2 + z // 2) % 2 else L.P("#c9a27a"))
    f = {"horta": horta, "ervas": ervas, "cogumelos": cogumelos}[conteudo]
    basket(-5.0, 0.5, 4.6, 9, f)
    basket(4.5, -2.5, 3.8, 8, f)
    basket(3.5, 6.0, 3.0, 6, f)
    arc = [(-5.5 + math.cos(math.pi * i / 8) * 4.0, 8 + math.sin(math.pi * i / 8) * 5.0) for i in range(9)]
    for z in (0, 1):                                            # alça em arco no cesto grande (traço contínuo)
        for (ax, ay), (bx, by) in zip(arc, arc[1:]):
            L.line6(v, (ax, ay, z), (bx, by, z), STRAW_D)
    shade(v)
    return v, {}


# ============================================================================= lenha com machado

@asset("woodpile_axe", nome="Pilha de lenha com machado", categoria="prop", sub="trabalho", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["rural", "mata", "cacador", "fundos_de_casa", "trabalho"],
       funcao="trabalho diário: lenha rachada, cepo com machado cravado e cavacos — quem mora aqui corta lenha",
       terreno=["grama", "terra", "mata"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.3,
       area_livre={"raio_u": 0.4}, colisao={"tipo": "caixa", "escala": 0.85, "pisavel": True},
       relacoes=[("acompanha", "cabin_hunter", [1.0, 4.0]), ("perto_de", "woodshed_open", [0.8, 3.0])],
       variacoes={"machado": [True, False]})
def woodpile_axe(machado=True):
    """Pilha triangular de achas (pontas claras em meia-lua, casca escura), cepo baixo com o machado cravado e cavacos
    no chão. A 1/16 as achas leem como lenha (a 1/8 eram quadrados)."""
    v = Vox()
    for r in range(4):                                           # pilha em degraus de achas (ponta arredondada, vão)
        L.achas(v, -14 + r * 3, 2 - r * 2, r * 4, 1, -5, 5)
    v.cyl(8.0, 1.0, 0, 4, 3.0, WOOD_D)                           # cepo
    for x in range(5, 12):
        for z in range(-2, 4):
            if (x + 0.5 - 8) ** 2 + (z + 0.5 - 1) ** 2 <= 6.0:
                v.set(x, 5, z, WOOD_L)
    if machado:
        L.line6(v, (8, 5, 1), (12, 12, 1), WOOD)                 # cabo
        v.box(7, 5, 0, 9, 7, 2, IRON)                            # lâmina cravada
    for (x, z) in ((4, 6), (12, -4), (10, 6)):                   # cavacos (2×2: peça, não pontinho)
        v.box(x, 0, z, x + 1, 0, z + 1, WOOD_L)
    shade(v)
    return v, {"interacao": [("rachar", (8, 0, 9), "usar")]}


# ============================================================================= placa de ofício

PICTOS = {   # pictogramas 7×7 (linhas de cima para baixo), "#" = traço
    "ferreiro": ["..###..", ".#####.", "#######", "..###..", "..###..", ".#####.", "......."],
    "herbalista": ["...#...", "..###..", ".#####.", "..###..", "...#...", "...#...", "..###.."],
    "comerciante": [".#####.", "#.....#", "#.###.#", "#.#...#", "#.###.#", "#.....#", ".#####."],
    "estalagem": [".####..", ".#..##.", ".#..#.#", ".#..##.", ".#..#..", ".####..", "......."],
    "cacador": ["...#...", "..#.#..", "...#...", "...#...", "...#...", "..#.#..", ".#...#."],
}


@asset("sign_trade", nome="Placa de ofício", categoria="prop", sub="sinalizacao", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["vila", "comercio", "oficio", "porta", "identidade"],
       funcao="identifica o ofício de uma porta SEM texto: bigorna (ferreiro), folha (herbalista), moeda "
              "(comerciante), caneca (estalagem), flecha (caçador)",
       terreno=["caminho", "terra", "grama"], apoio={"tipo": "ponto"}, inclinacao_max_u=0.5,
       area_livre={"raio_u": 0.2}, colisao={"tipo": "circulo", "raio": 0.12, "camera": False},
       restricoes=["junto da entrada do ofício, a placa virada para o caminho"],
       relacoes=[("acompanha", "workshop_forge", [0.5, 3.0]), ("acompanha", "hut_herbalist", [0.5, 3.0]),
                 ("acompanha", "house_tall", [0.5, 3.0]), ("acompanha", "cabin_hunter", [0.5, 3.0])],
       publicar_variantes=True,     # cada ofício vira um prop (sign_trade__oficio-<x>): o gerador escolhe pelo id
       variacoes={"oficio": ["ferreiro", "herbalista", "comerciante", "estalagem", "cacador"]})
def sign_trade(oficio="ferreiro"):
    """Poste com braço e mão-francesa, tábua pendurada por duas correntes, pictograma do ofício em ferro dos DOIS
    lados (lê vindo de qualquer lado do caminho). Variação segura: só o pictograma muda."""
    v = Vox()
    _post(v, -1, -1, 0, 27)
    v.box(-1, 26, -1, 13, 27, 0, WOOD_D)                         # braço
    L.line6(v, (0, 20, 0), (6, 26, 0), WOOD_D)
    L.line6(v, (1, 20, 0), (7, 26, 0), WOOD_D)
    for x in (4, 10):                                            # correntes
        v.box(x, 23, 0, x, 25, 0, IRON)
    v.box(2, 13, -1, 12, 22, 0, WOOD_L)                          # tábua
    for x in range(2, 13):
        for z in (-1, 0):
            v.set(x, 22, z, WOOD)
            v.set(x, 13, z, WOOD)
    pic = PICTOS[oficio]
    for r, row in enumerate(pic):
        for c, ch in enumerate(row):
            if ch == "#":
                v.set(4 + c, 20 - r, 1, IRON)
                v.set(4 + c, 20 - r, -2, IRON)
    v.box(-2, 0, -2, 1, 1, 1, STONE_L)                           # sapata
    shade(v)
    return v, {}


# ============================================================================= mureta de pedra seca

@asset("fence_stone", nome="Mureta de pedra seca", categoria="prop", sub="cerca", lote=2,
       voxel=BIG, frequencia="comum",
       tags=["rural", "campo", "estrada", "divisa", "comunidade_rural"],
       funcao="divisa de campo e beira de estrada rural (a cerca de madeira aprovada é de quintal de vila)",
       terreno=["grama", "campo", "colina"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.5,
       area_livre={"raio_u": 0.1}, colisao={"tipo": "caixa", "escala": 1.0, "pisavel": True},
       restricoes=["em sequência ao longo de x (2 u por peça), girada para seguir a divisa"],
       relacoes=[("perto_de", "crop_field", [0.5, 3.0]), ("perto_de", "granary_raised", [1.0, 6.0])],
       variacoes={"semente": [1, 2, 3], "musgo": [True, False]})
def fence_stone(semente=1, musgo=True):
    """Pedras assentadas a seco em duas fiadas irregulares com capeamento de pedras em pé, ~0,6 u de altura e 2 u de
    comprimento (como a cerca aprovada); musgo fosco no topo. Peça modular: as pontas casam com a próxima."""
    v = Vox()
    s = semente
    for x in range(-8, 8):
        hh = 3 + (1 if hash3(x // 3, 0, 0, s) > 0.6 else 0)
        for y in range(-1, hh):
            for z in (-1, 0):
                v.set(x, y, z, STONE)
        if x % 2 == 0:                                           # capeamento em pé
            v.box(x, hh, -1, x, hh + 1, 0, STONE_L if hash3(x, 1, 0, s) < 0.5 else STONE_D)
    EB.stones(v, seed=250 + s, cell=(2, 1, 2))
    if musgo:
        for (x, y, z), c in list(v.v.items()):
            if (x, y + 1, z) not in v.v and hash3(x // 2, 2, z, s) < 0.35:
                v.v[(x, y, z)] = MOSS_FIX
    for k in [k for k in v.v if k[1] < 0]:
        del v.v[k]
    return v, {}


# ============================================================================= cerca de estacas

@asset("fence_picket", nome="Cerca de estacas", categoria="prop", sub="cerca", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["vila", "horta", "quintal", "herbalista"],
       funcao="cerca de horta e de quintal de ervas: mais baixa e mais 'doméstica' que a cerca de travessas",
       terreno=["grama", "terra"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.3,
       area_livre={"raio_u": 0.1}, colisao={"tipo": "caixa", "escala": 1.0},
       restricoes=["em sequência ao longo de x (1,5 u por peça) em volta de horta ou quintal"],
       relacoes=[("perto_de", "crop_field", [0.2, 1.5]), ("perto_de", "hut_herbalist", [1.0, 5.0])],
       variacoes={"pintada": [False, True]})
def fence_picket(pintada=False):
    """Estacas de ponta em diamante, alternando duas alturas, presas por duas travessas, um mourão mais grosso em cada
    ponta. Pintada: estacas creme (cerca de casa), senão madeira."""
    v = Vox()
    col = CREAM if pintada else WOOD
    for x in range(-12, 12, 3):
        h = 11 if (x // 3) % 2 == 0 else 9
        v.box(x, 0, 0, x + 1, h, 0, col)
        v.set(x, h + 1, 0, col)
        v.set(x + 1, h + 1, 0, shadow(col, 0.8))
    for y in (3, 8):
        v.box(-12, y, -1, 11, y, -1, WOOD_D)
    for x in (-13, 11):
        v.box(x, 0, -1, x + 1, 12, 0, WOOD_D)
    shade(v)
    return v, {}


# ============================================================================= suporte de ferramentas

@asset("tool_rack", nome="Suporte de ferramentas", categoria="prop", sub="trabalho", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["rural", "campo", "oficina", "horta", "trabalho"],
       funcao="trabalho da terra e da oficina: ancinho, pá, forcado e enxada pendurados num cavalete",
       terreno=["grama", "terra"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.3,
       area_livre={"frente_u": 0.6, "raio_u": 0.2}, colisao={"tipo": "caixa", "escala": 0.9},
       relacoes=[("perto_de", "shed_storage", [0.3, 2.0]), ("perto_de", "crop_field", [0.5, 3.0]),
                 ("perto_de", "workshop_forge", [0.5, 3.0])],
       variacoes={})
def tool_rack():
    """Cavalete em A com travessa; quatro ferramentas penduradas de cabeça para cima, cada uma com a cabeça de
    silhueta própria (dentes do ancinho, lâmina da pá, três dentes do forcado, enxada em L) — cabos de madeira,
    cabeças de ferro."""
    v = Vox()
    for x in (-10, 9):
        L.line6(v, (x, 0, -3), (x, 16, 0), WOOD_D)
        L.line6(v, (x, 0, 3), (x, 16, 0), WOOD_D)
    v.box(-10, 16, 0, 10, 17, 0, WOOD)
    for i, x in enumerate((-7, -2, 3, 7)):
        v.box(x, 3, 1, x, 16, 1, WOOD)                           # cabo
        if i == 0:                                               # ancinho
            v.box(x - 3, 17, 1, x + 3, 17, 1, IRON)
            for dx in range(-3, 4, 2):
                v.box(x + dx, 18, 1, x + dx, 19, 1, IRON)
        elif i == 1:                                             # pá
            v.box(x - 2, 17, 1, x + 2, 21, 1, IRON)
            v.box(x - 1, 22, 1, x + 1, 22, 1, IRON)
        elif i == 2:                                             # forcado
            v.box(x - 2, 17, 1, x + 2, 17, 1, IRON)
            for dx in (-2, 0, 2):
                v.box(x + dx, 18, 1, x + dx, 21, 1, IRON)
        else:                                                    # enxada
            v.box(x, 17, 1, x + 3, 17, 1, IRON)
            v.box(x + 2, 17, 1, x + 3, 19, 1, IRON)
    shade(v)
    return v, {"interacao": [("pegar", (0, 0, 8), "pegar")]}


# ============================================================================= varal de ervas

@asset("herb_rack", nome="Varal de ervas", categoria="prop", sub="oficio", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["herbalista", "oficio", "quintal", "rural"],
       funcao="ofício da herbalista ao ar livre: maços secando de cabeça para baixo e bandejas de flores",
       terreno=["grama", "terra"], apoio={"tipo": "terreno"}, inclinacao_max_u=0.3,
       area_livre={"frente_u": 0.6, "raio_u": 0.3}, colisao={"tipo": "caixa", "escala": 0.85, "camera": False},
       relacoes=[("acompanha", "hut_herbalist", [1.0, 4.0]), ("perto_de", "basket_group", [0.3, 2.0])],
       variacoes={})
def herb_rack():
    """Duas forquilhas com uma vara; maços pendurados (verde, rosa, amarelo) de cabeça para baixo, amarrados com
    cordão creme; duas bandejas de tábuas com flores espalhadas secando. Os maços são folhagem SOLTA e baixa
    (≤ 1,1 u): balançam pouco e não encostam em superfície."""
    v = Vox()
    for x in (-12, 11):
        v.box(x, 0, 0, x + 1, 15, 1, WOOD_D)
        v.set(x - 1, 16, 0, WOOD_D)
        v.set(x + 2, 16, 0, WOOD_D)
    v.box(-13, 16, 0, 13, 16, 1, WOOD)
    cols = (LEAF_BUSH, PINK, YELLOW, light(LEAF_BUSH), LEAF_BUSH, PINK)
    for i, x in enumerate(range(-9, 10, 3)):
        c = cols[i % len(cols)]
        v.set(x, 15, 0, CREAM)
        v.box(x, 14, 0, x, 14, 1, CREAM)
        v.box(x - 1, 10, 0, x + 1, 13, 1, c if c not in (PINK, YELLOW) else c)
        v.box(x, 9, 0, x, 9, 1, light(c))
    for (z0, h) in ((-6, 5), (4, 4)):                            # bandejas
        v.box(-8, h, z0, 7, h, z0 + 3, WOOD_L)
        for x in (-8, 7):
            v.box(x, 0, z0, x, h - 1, z0, WOOD_D)
            v.box(x, 0, z0 + 3, x, h - 1, z0 + 3, WOOD_D)
        for x in range(-7, 7, 2):
            v.set(x, h + 1, z0 + 1 + (x // 2) % 2, (PINK, YELLOW, CREAM)[(x // 2) % 3])
    shade(v)
    return v, {"interacao": [("colher", (0, 0, 9), "colher")]}


# ============================================================================= espantalho

@asset("scarecrow", nome="Espantalho", categoria="prop", sub="campo", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["rural", "campo", "horta", "comunidade_rural", "silhueta"],
       funcao="marca de campo cultivado: silhueta em cruz visível de longe; nenhum rosto (não é personagem)",
       terreno=["campo", "grama"], apoio={"tipo": "ponto"}, inclinacao_max_u=0.5,
       area_livre={"raio_u": 0.3}, colisao={"tipo": "circulo", "raio": 0.15, "camera": False},
       restricoes=["dentro ou na borda de horta/campo"],
       relacoes=[("dentro_de", "crop_field", None)],
       variacoes={"chapeu": ["palha", "teal"]})
def scarecrow(chapeu="palha"):
    """Estaca e travessa em cruz, saco de aniagem por cabeça SEM rosto (não é criatura nem NPC), chapéu de aba larga,
    casaco remendado (teal e coral) e tufos de palha saindo das mangas e da barra."""
    v = Vox()
    v.box(-1, 0, -1, 0, 26, 0, WOOD_D)
    v.box(-11, 19, -1, 10, 20, 0, WOOD_D)
    v.box(-5, 10, -2, 4, 20, 1, TEAL)                            # casaco
    v.box(-5, 10, -2, 4, 10, 1, TEAL_D)
    v.box(1, 13, 2, 3, 16, 2, CORAL)                             # remendo
    v.box(-10, 18, -2, -5, 21, 1, TEAL)                          # mangas
    v.box(5, 18, -2, 9, 21, 1, TEAL)
    for (x, y) in ((-12, 18), (-12, 20), (10, 19), (10, 21), (-4, 8), (0, 8), (3, 8)):
        v.box(x, y - 1, -1, x + 1, y, 0, STRAW)
    head = Vox()
    L.sblob(head, -0.5, 24.5, -0.5, 3.4, 3.2, 3.2, BURLAP, p=2.2)
    v.merge(head)
    v.box(-2, 21, -2, 1, 21, 1, STRAW_D)                         # amarração
    hc, hd = (STRAW, STRAW_D) if chapeu == "palha" else (TEAL, TEAL_D)
    for x in range(-7, 7):
        for z in range(-6, 6):
            if (x + 0.5) ** 2 + (z + 0.5) ** 2 <= 36:
                v.set(x, 27, z, hd if (x + 0.5) ** 2 + (z + 0.5) ** 2 > 25 else hc)
    v.box(-3, 28, -3, 2, 30, 2, hc)
    v.box(-3, 28, -3, 2, 28, 2, CORAL if chapeu == "palha" else CREAM)
    shade(v)
    return v, {}


# ============================================================================= estruturas pequenas (1/16)

@asset("market_stall", nome="Barraca de feira", categoria="estrutura", sub="comercio", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["vila", "praca", "feira", "comercio", "comerciante"],
       funcao="comércio na praça ou na beira do caminho: balcão com produtos, toldo listrado, placa pendurada",
       terreno=["caminho", "terra", "grama"], apoio={"tipo": "patamar", "margem_u": 0.2}, inclinacao_max_u=0.2,
       area_livre={"frente_u": 1.5, "lados_u": 0.4, "fundos_u": 0.6},
       colisao={"tipo": "caixa", "escala": 0.8, "formas": "medidas",
                "_nota": "o feirante fica ATRÁS do balcão, sob o toldo: com a caixa única ele estaria dentro do sólido"},
       # rodada 12: liberada — colisão composta no jogo; o feirante fica atrás do balcão, sob o toldo (CompositeCollisionTest)
       restricoes=["frente (balcão) virada para a praça ou o caminho"],
       relacoes=[("perto_de", "fountain_square", [3.0, 6.0]), ("perto_de", "crate_stack", [0.3, 2.5]),
                 ("acompanha", "house_tall", [2.0, 6.0])],
       variacoes={"toldo": ["coral", "teal", "mostarda"], "produtos": ["feira", "pao", "ervas"]})
def market_stall(toldo="coral", produtos="feira"):
    """Quatro esteios (os da frente mais altos), toldo de pano LISTRADO caindo para a frente com bainha reta, balcão
    de tábuas com produtos (cestos de fruta, pães, potes), caixotes e sacos embaixo, banquinho atrás, placa
    pendurada na frente. Silhueta: toldo inclinado sobre balcão — a única barraca do jogo."""
    v = Vox()
    X0, X1, Z0, Z1 = -18, 17, -10, 9
    for (x, z, top) in ((X0, Z1 - 1, 34), (X1 - 1, Z1 - 1, 34), (X0, Z0, 30), (X1 - 1, Z0, 30)):
        _post(v, x, z, 0, top)
    stripe = {"coral": CORAL, "teal": TEAL, "mostarda": L.MUSTARD}[toldo]
    for x in range(X0 - 2, X1 + 3):                              # toldo
        for z in range(Z0 - 1, Z1 + 6):
            y = 31 + int(round((z - (Z0 - 1)) * -0.32 + 3))
            c = stripe if ((x - X0) // 4) % 2 == 0 else CREAM
            v.set(x, y, z, c)
            if z == Z1 + 5:                                      # bainha reta
                v.box(x, y - 3, z, x, y - 1, z, c)
    v.box(X0, 13, Z1 - 4, X1 - 1, 14, Z1, WOOD_L)                # balcão
    for x in range(X0, X1):
        for y in range(0, 13):
            v.set(x, y, Z1, WOOD if (x // 3) % 2 else WOOD_D)
    v.box(X0, 13, Z1 + 1, X1 - 1, 13, Z1 + 1, WOOD_D)
    goods = {"feira": [((-14, 6), CORAL), ((-10, 6), YELLOW), ((-6, 7), CORAL)], "pao": [((-14, 6), BREAD), ((-10, 6), BREAD), ((-6, 7), BREAD)],
             "ervas": [((-14, 6), IVY), ((-10, 6), PINK), ((-6, 7), IVY)]}[produtos]
    for ((x, z), c) in goods:                                    # cestos no balcão
        v.box(x - 2, 15, z - 2, x + 1, 16, z + 1, STRAW_D)
        v.box(x - 1, 17, z - 1, x, 17, z, c)
        v.box(x - 2, 17, z - 1, x - 2, 17, z, c)
    for x in (0, 3, 6):                                          # potes
        v.box(x, 15, 6, x + 1, 17, 7, TERRACOTTA)
    v.box(10, 15, 5, 14, 16, 7, BREAD)
    for (x, z) in ((-14, -6), (-8, -6)):                         # caixotes embaixo/atrás
        v.box(x, 0, z, x + 4, 4, z + 4, WOOD)
        v.box(x, 4, z, x + 4, 4, z + 4, WOOD_D)
    v.box(6, 0, -6, 9, 6, -3, WOOD_D)                            # banquinho
    v.box(5, 7, -7, 10, 7, -2, WOOD_L)
    for x in (-3, 4):                                            # placa pendurada do toldo
        v.box(x, 24, Z1 + 4, x, 27, Z1 + 4, IRON)
    v.box(-4, 18, Z1 + 4, 5, 23, Z1 + 4, WOOD_L)
    for (x, y) in ((-1, 20), (0, 21), (1, 21), (2, 20), (0, 19), (1, 19)):
        v.set(x, y, Z1 + 5, CORAL)
    shade(v)
    return v, {"entradas": [("balcao", (0, 0, Z1 + 16), 0)],
               "interacao": [("comprar", (0, 0, Z1 + 13), "comerciar")],
               "spawns": [("feirante", (0, 0, -4), "npc")]}


@asset("notice_board", nome="Quadro de avisos de vila", categoria="estrutura", sub="comunidade", lote=2,
       voxel=FINE, frequencia="incomum",
       tags=["vila", "praca", "comunidade", "missao", "ponto_de_encontro"],
       funcao="quadro de avisos da praça (futuro ponto de pegar missões): papéis presos, telhadinho, degrau de pedra",
       terreno=["caminho", "terra", "grama"], apoio={"tipo": "patamar", "margem_u": 0.1}, inclinacao_max_u=0.25,
       area_livre={"frente_u": 1.4, "raio_u": 0.3}, colisao={"tipo": "caixa", "escala": 0.8},
       relacoes=[("perto_de", "fountain_square", [2.5, 6.0]), ("perto_de", "tower_watch", [2.0, 6.0])],
       variacoes={"telhado": ["coral", "teal"]})
def notice_board(telhado="coral"):
    """Dois mourões, quadro de tábuas com moldura, papéis creme e pergaminho presos por tachinhas coral, telhadinho de
    duas águas por cima (fiadas), degrau de pedra na frente. Mais vertical e mais 'público' que o quadro do posto
    de trilha (que fica dentro de um abrigo)."""
    v = Vox()
    for x in (-12, 10):
        _post(v, x, 0, 0, 30)
    for x in range(-11, 11):
        for y in range(10, 27):
            frame = x in (-11, 10) or y in (10, 26)
            v.set(x, y, 0, TIMBER if frame else WOOD_L)
            v.set(x, y, 1, WOOD_D)
    for (zp, zt, papers) in ((2, 3, ((-9, 18, -4, 24), (-2, 14, 4, 24), (6, 20, 8, 24), (6, 12, 8, 17), (-9, 12, -5, 16))),
                             (-1, -2, ((-8, 16, -2, 24), (1, 19, 8, 24), (2, 12, 7, 16)))):     # frente (+z) e verso
        for (x0, y0, x1, y1) in papers:
            for x in range(x0, x1 + 1):
                for y in range(y0, y1 + 1):
                    v.set(x, y, zp, CREAM if (x0 + y0) % 2 else L.P("#f3e6c8"))
            v.set(x0 + (x1 - x0) // 2, y1, zt, CORAL)
    base, line = L.ROOFS[telhado]
    for x in range(-15, 15):                                     # telhadinho de duas águas (cumeeira em x)
        for z in range(-5, 7):
            y = 31 + int(round(4 * (1 - abs(z - 0.5) / 6.5)))
            v.set(x, y, z, line if y % 3 == 0 else base)
    v.box(-15, 36, 0, 14, 36, 1, TIMBER)
    v.merge(L.stone_block(-12, -2, 3, 11, 1, 7, seed=260, soft=True))
    shade(v)
    return v, {"entradas": [("frente", (0, 0, 17), 0)], "interacao": [("ler", (0, 0, 10), "ler")],
               "spawns": []}


@asset("crop_field", nome="Horta em canteiros", categoria="estrutura", sub="campo", lote=2,
       voxel=FINE, frequencia="comum",
       tags=["rural", "horta", "campo", "comunidade_rural", "trabalho"],
       funcao="área de trabalho rural: canteiros de terra lavrada com repolhos, feijão em estacas e trigo baixo",
       terreno=["grama", "campo"], apoio={"tipo": "patamar", "margem_u": 0.1}, inclinacao_max_u=0.25,
       area_livre={"raio_u": 0.3}, colisao={"tipo": "nenhum"},
       restricoes=["terreno quase plano", "perto de casa rural, paiol ou herbalista"],
       relacoes=[("perto_de", "granary_raised", [2.0, 8.0]), ("perto_de", "scarecrow", [0.0, 2.0]),
                 ("perto_de", "fence_picket", [0.2, 1.5]), ("perto_de", "hut_herbalist", [2.0, 6.0])],
       notas="v1 variava o tipo de cultura (só trigo/só repolho): sem o feijão em estacas a altura caía 38% — a variação "
             "segura é a ORDEM dos canteiros",
       variacoes={"cultura": ["misto", "misto_b", "misto_c"]})
def crop_field(cultura="misto"):
    """Três canteiros de terra escura com sulcos (2 tons), separados por trilhas de pisar: repolhos em bolas, feijão
    subindo em estacas cruzadas e trigo baixo dourado. Baixo (≤ 0,8 u): é chão de trabalho, não obstáculo."""
    v = Vox()
    beds = [(-22, -6), (-6, 10), (10, 26)]
    kinds = {"misto": ("repolho", "feijao", "trigo"), "misto_b": ("trigo", "feijao", "repolho"),
             "misto_c": ("feijao", "repolho", "trigo")}[cultura]
    for (x0, x1), kind in zip(beds, kinds):
        for x in range(x0, x1 - 1):
            for z in range(-14, 15):
                v.set(x, 0, z, SOIL if z % 3 else shadow(SOIL, 0.8))   # uma camada (a segunda só somava lados)
        for x in range(x0 + 1, x1 - 2, 5):
            for z in range(-12, 13, 5):
                if kind == "repolho":
                    v.box(x, 1, z, x + 2, 2, z + 2, CABBAGE)
                    v.set(x + 1, 3, z + 1, light(CABBAGE))
                elif kind == "trigo":                            # fileira contínua: lê como plantio e funde
                    v.box(x, 1, z, x + 2, 4, z + 1, STRAW)
                    v.box(x, 5, z, x + 2, 5, z + 1, L.P("#e8c66a"))
                else:
                    v.box(x + 1, 1, z, x + 1, 11, z, WOOD_D)
                    for y in range(2, 10, 2):
                        v.set(x + 1, y, z + 1, CABBAGE)
                        v.set(x + 1, y + 1, z - 1, CABBAGE)
    return v, {"interacao": [("colher", (2, 0, 19), "colher")]}


@asset("guard_brazier", nome="Braseiro de sinal", categoria="estrutura", sub="defesa_orientacao", lote=2,
       voxel=FINE, frequencia="rara",
       tags=["guarda", "estrada", "borda", "sinal", "noite", "guardiao_de_trilhas", "fonte_de_luz"],
       funcao="sinal do guardião de trilhas: fogo em cesto de ferro sobre pilar de pedra; luz quente visível de longe "
              "ao entardecer",
       terreno=["grama", "terra", "colina"], apoio={"tipo": "patamar", "margem_u": 0.1}, inclinacao_max_u=0.4,
       area_livre={"raio_u": 0.8}, colisao={"tipo": "circulo", "raio": 0.4},
       restricoes=["entrada do assentamento ou junto da torre de vigia", "longe de palha (≥ 4 u)"],
       relacoes=[("acompanha", "tower_watch", [1.5, 5.0]), ("perto_de", "weapon_rack", [1.0, 4.0])],
       notas="aceso/apagado é ESTADO, não variação (a chama muda a altura): se a integração precisar dos dois, vira um par "
             "de props como o marco do posto (rodada 10)",
       variacoes={})
def guard_brazier(aceso=True):
    """Pilar de pedra assentada com capitel claro, cesto de ferro de quatro garras, brasas e chama em duas línguas
    (emissivo de fogo), lenha empilhada ao pé. Apagado: cinzas e carvão (sem emissivo)."""
    v = Vox()
    v.merge(L.stone_block(-4, -2, -4, 3, 14, 3, seed=270, soft=True))
    v.box(-5, 15, -5, 4, 16, 4, STONE_L)
    for (x, z) in ((-5, -5), (4, -5), (-5, 4), (4, 4)):          # garras do cesto
        v.box(x, 17, z, x, 22, z, IRON)
    v.box(-4, 17, -4, 3, 17, 3, IRON)
    for y in (19, 22):
        for x in range(-5, 5):
            for z in (-5, 4):
                v.set(x, y, z, IRON)
                v.set(z, y, x, IRON)
    if aceso:
        v.box(-3, 18, -3, 2, 19, 2, L.FIRE_R)
        for (x, z, h) in ((-2, -1, 26), (1, 1, 24), (-1, 2, 23)):
            v.box(x, 20, z, x + 1, h, z + 1, L.FIRE)
            v.set(x, h + 1, z, GLOW_HI)
    else:
        v.box(-3, 18, -3, 2, 19, 2, shadow(STONE_D, 1.6))
    for i, x in enumerate((-7, -3, 1)):                          # lenha ao pé
        v.box(x, 0, 6, x + 2, 1, 9, WOOD)
        v.box(x, 0, 6, x + 2, 1, 6, WOOD_L)
    shade(v)
    return v, {"interacao": [("acender", (0, 0, 13), "acender")]}


@asset("weapon_rack", nome="Suporte de lanças e escudos", categoria="estrutura", sub="defesa_orientacao", lote=2,
       voxel=FINE, frequencia="rara",
       tags=["guarda", "guardiao_de_trilhas", "torre", "borda"],
       funcao="posto do guardião de trilhas: lanças em pé, dois escudos redondos pintados, coberto por uma prancha",
       terreno=["grama", "terra"], apoio={"tipo": "patamar", "margem_u": 0.1}, inclinacao_max_u=0.3,
       area_livre={"frente_u": 0.8, "raio_u": 0.2}, colisao={"tipo": "caixa", "escala": 0.9},
       relacoes=[("acompanha", "tower_watch", [1.5, 5.0]), ("perto_de", "guard_brazier", [1.0, 4.0])],
       variacoes={})
def weapon_rack():
    """Cavalete com duas travessas; quatro lanças em pé (haste, ponta de ferro de três tons) e dois escudos redondos
    de tábuas com aro de ferro e pintura teal/coral em faixa; prancha-telhado contra a chuva. Equipamento de guarda,
    não de herói (sem lâmina larga, sem brilho)."""
    v = Vox()
    for x in (-12, 11):
        v.box(x, 0, -2, x + 1, 22, -1, WOOD_D)
    for y in (6, 18):
        v.box(-12, y, -3, 12, y, -3, WOOD)
    for x in (-8, -4, 0):                                        # lanças
        v.box(x, 1, -2, x, 26, -2, WOOD)
        v.box(x, 27, -2, x, 30, -2, IRON)
        v.set(x, 31, -2, light(IRON))
    for i, (cx, cy, col) in enumerate(((6.0, 13.0, TEAL), (6.0, 4.5, CORAL))):
        for x in range(int(cx) - 5, int(cx) + 5):
            for y in range(int(cy) - 5, int(cy) + 5):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= 4.6:
                    v.set(x, y, -4, IRON if d > 3.7 else (col if abs(y + 0.5 - cy) < 1.2 else WOOD))
                    v.set(x, y, -3, WOOD_D)
        v.set(int(cx), int(cy), -5, IRON)
    v.box(-14, 32, -5, 13, 32, 1, WOOD_D)                        # prancha-telhado
    shade(v)
    v.rot_y90(2)                                                 # escudos e lanças para +z (a frente do contrato)
    return v, {"interacao": [("equipar", (-1, 0, 11), "pegar")]}
