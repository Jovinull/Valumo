"""Laboratório de conteúdo — pontos de interesse (ruínas, marcos, lugares para achar explorando). Candidatos.

Cada lugar tem uma FUNÇÃO potencial no mundo procedural (exploração, descoberta, encontro, missão, abrigo,
orientação, recompensa, narrativa ambiental) declarada no catálogo, com pontos autorados (entrada, área segura,
interação, spawn, observação) conferidos pelos testes. Nenhum comportamento de jogo é implementado aqui.

Linguagem: a mesma pedra lilás-quente assentada em 3 tons, a mesma cultura construtiva da vila (estuque, enxaimel,
telha coral) — as ruínas são da MESMA civilização, mais velhas. Nada de templo genérico nem de motivo de outra obra.
"""
import math

import env_b as EB
import conteudo_lib as L
from conteudo_lib import (BIG, IRON, IVY, LEAF_BUSH, MOSS_FIX as MOSS, PLASTER, ROOF, ROOF_D, SEED_P1, STONE, STONE_D, STONE_L, TIMBER,
                          WOOD_D, Face, asset, light, sblob, shade, shadow)
from envlib import hash3
from voxlib import Vox


def _weather(v, amount=0.45, seed=0, min_y=1):
    """Musgo nos topos expostos de pedra (manchas de 2×2) — ruína velha, nunca pontilhado."""
    for (x, y, z), c in list(v.v.items()):
        if c in (STONE, STONE_L, STONE_D) and y >= min_y and (x, y + 1, z) not in v.v:
            if hash3(x // 2, 3, z // 2, seed) < amount:
                v.v[(x, y, z)] = MOSS if hash3(x // 2, y // 2, z // 2, seed + 1) < 0.7 else light(MOSS)


def _rubble(v, spots, seed=0):
    """Blocos caídos (2–3 voxels), assentados no chão, em 3 tons de pedra."""
    for i, (x, z, n) in enumerate(spots):
        for j in range(n):
            bx = x + int(hash3(i, j, 1, seed) * 5) - 2
            bz = z + int(hash3(i, j, 2, seed) * 5) - 2
            s = 2 + int(hash3(i, j, 3, seed) * 2)
            h = 1 + int(hash3(i, j, 4, seed) * 2)
            col = (STONE, STONE_L, STONE_D)[int(hash3(i, j, 5, seed) * 3)]
            v.box(bx, 0, bz, bx + s - 1, h - 1, bz + s - 1, col)


# ============================================================================= torre arruinada

@asset("ruin_tower", integracao="depende_colisao_composta",
       dependencias=["formas compostas no Collision (interior caminhável): sem elas o interior fica dentro do sólido"], nome="Torre arruinada", categoria="poi", sub="ruina", lote=1,
       voxel=BIG, frequencia="rara", wind=True,
       tags=["ruina", "exploracao", "abrigo", "orientacao", "encontro", "colina", "isolada"],
       funcao="exploração e orientação: visível de longe; interior caminhável serve de abrigo, covil de inimigos "
              "(encontro) ou lugar de missão (objeto no centro); a escada quebrada leva a um ponto de vista",
       terreno=["grama", "colina", "rocha"], apoio={"tipo": "patamar", "margem_u": 0.8}, inclinacao_max_u=0.6,
       area_livre={"frente_u": 2.5, "raio_u": 3.0},
       colisao={"tipo": "nenhum", "formas": "anel", "_nota": "anel de paredes com porta: precisa de formas compostas "
                "(ver 'formas_u'); com uma forma só o interior ficaria bloqueado"},
       restricoes=["porta virada para a trilha ou para a clareira", "≥ 6 u de outras construções"],
       variacoes={"semente": [1, 2, 3], "hera": [True, False]})
def ruin_tower(semente=1, hera=True):
    """Anel de muralha de pedra (Ø 3,5 u, parede de 3,5 voxels) quebrado em DEGRAUS largos: alto atrás à esquerda
    (≈ 4 u), baixo na frente à direita (≈ 1,3 u). Porta em arco intacta na frente, seteira no lado alto, piso de lajes,
    escada de pedra quebrada subindo pela parede de dentro, blocos caídos dentro e fora, hera no lado alto, musgo nos
    topos. Lê como 'torre que caiu' a 30 u; o interior é caminhável (área segura declarada)."""
    v = Vox()
    s = semente
    Ro, Ri = 14.0, 10.5
    th0 = math.radians(225)                                         # lado mais alto (trás-esquerda)
    for x in range(-15, 15):
        for z in range(-15, 15):
            d = math.hypot(x + 0.5, z + 0.5)
            if d > Ro:
                continue
            for y in range(-6, 1):
                v.set(x, y, z, STONE)                               # fundação e piso
            if d < Ri:
                continue
            a = math.atan2(z + 0.5, x + 0.5)
            seg = int((a + math.pi) / (2 * math.pi) * 14)          # blocos de arco largos (~6 voxels)
            f = (0.5 + 0.5 * math.cos(a - th0)) ** 1.3
            hh = 8 + 27 * f
            hh = int(hh // 4) * 4 + (3 if hash3(seg, 0, 0, s) < 0.3 else 0)   # quebra em degraus largos
            if hash3(seg, 1, 0, s) < 0.25 and d > Ri + 1.6:
                hh -= 4                                             # pedra arrancada só na face de fora
            for y in range(1, hh):
                v.set(x, y, z, STONE)
    # sem espigão fino: coluna mais alta que os dois vizinhos por > 2 desce até o mais alto deles
    tops = {}
    for (x, y, z) in v.v:
        if y > 0:
            tops[(x, z)] = max(tops.get((x, z), 0), y)
    for (x, z), t in list(tops.items()):
        nb = [tops.get(k, 0) for k in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))]
        m = sorted(nb)[-2]
        if t > m + 2:
            for y in range(m + 1, t + 1):
                v.v.pop((x, y, z), None)
    # fiadas de pedra HORIZONTAIS no anel (blocos de ~3 voxels de arco × 2 de altura, desencontrados por fiada)
    for (x, y, z), c in list(v.v.items()):
        if c == STONE and y > 0:
            a = math.atan2(z + 0.5, x + 0.5)
            arc = (a + math.pi) * 12.5 / 3.0 + ((y // 2) % 2) * 0.5
            k = hash3(int(arc), y // 2, 0, 100 + s)
            v.v[(x, y, z)] = STONE_D if k < 0.25 else (STONE_L if k > 0.8 else STONE)
    EB.stones(v, seed=100 + s)
    for x in range(-11, 11):                                        # lajes do piso
        for z in range(-11, 11):
            if math.hypot(x + 0.5, z + 0.5) < Ri:
                v.set(x, 0, z, STONE_L if (x // 3 + z // 3) % 2 else STONE)
    # porta em arco na frente (+z): abre a muralha e põe a moldura de pedra clara
    for x in range(-4, 4):
        for y in range(1, 13):
            u = x + 0.5
            if abs(u) <= 3.0 and (y < 8 or (u / 3.0) ** 2 + ((y - 8) / 3.4) ** 2 <= 1.0):
                for z in range(9, 15):
                    v.v.pop((x, y, z), None)
    for x in range(-5, 5):
        for y in range(1, 14):
            u = x + 0.5
            ring = abs(u) <= 4.0 and (y < 8 or (u / 4.0) ** 2 + ((y - 8) / 4.4) ** 2 <= 1.0)
            inner = abs(u) <= 3.0 and (y < 8 or (u / 3.0) ** 2 + ((y - 8) / 3.4) ** 2 <= 1.0)
            if ring and not inner:
                for z in range(10, 15):
                    if math.hypot(x + 0.5, z + 0.5) <= Ro:
                        v.set(x, y, z, STONE_L)
    # seteira no lado alto
    a = th0
    for y in range(18, 24):
        for k in range(8, 15):
            v.v.pop((int(math.floor(math.cos(a) * k)), y, int(math.floor(math.sin(a) * k))), None)
    # escada quebrada por dentro (sobe da esquerda da porta, contornando a parede, e acaba no vazio)
    for i in range(9):
        a = math.radians(160 - i * 14)
        y = 1 + i * 2
        for r in (8.4, 9.4, 10.2):
            for dy in range(0, 2):
                v.set(int(math.floor(math.cos(a) * r)), y + dy - 1, int(math.floor(math.sin(a) * r)), STONE_L if dy else STONE)
            v.set(int(math.floor(math.cos(a - 0.08) * r)), y, int(math.floor(math.sin(a - 0.08) * r)), STONE_L)
    _rubble(v, [(-4, -3, 3), (14, 9, 4), (17, 2, 3), (-16, 6, 2)], seed=s)
    _weather(v, 0.5, seed=101 + s, min_y=3)
    if hera:                                                        # três ramos de hera de 2 de largura, com vãos
        for (off, top) in ((-0.42, 22), (0.0, 27), (0.38, 17)):
            for i in range(2):
                a = th0 + off + i * 0.075
                for y in range(1, top):
                    if hash3(int(off * 10), y // 3, i, s) < 0.85:
                        a2 = a + 0.06 * ((y // 5) % 2)
                        v.set(int(math.floor(math.cos(a2) * (Ro + 0.6))), y, int(math.floor(math.sin(a2) * (Ro + 0.6))),
                              IVY if hash3(i, y, 1, s) < 0.6 else light(IVY))
    for k in [k for k in v.v if k[1] < -6]:
        del v.v[k]
    import charlib_e
    charlib_e.prune_specks(v, label="ruin_tower")
    pts = {
        "entradas": [("porta", (0, 0, 17), 0)],
        "interacao": [("centro", (0, 1, 0), "examinar"), ("topo_da_escada", (-7, 17, -3), "olhar")],
        "spawns": [("covil_1", (-3, 1, -4), "criatura"), ("covil_2", (4, 1, -3), "criatura")],
        "observacao": [("escada", (-7, 17, -3), 30)],
        "area_segura": [(-7, -7, 7, 7)],
    }
    return v, pts


# ============================================================================= círculo de pedras

@asset("stone_circle", aprovacao="aprovado_conceito", integracao="aguardando_refinamento",
       dependencias=["refinamento artístico no lote 4 (proporções próprias, composição assimétrica, centro reconhecível, P1 legível na câmera de jogo, marcas de cultura do Valumo; sem portal genérico, sem P2)", "formas compostas no Collision (7 pedras + centro livre)"], nome="Círculo de pedras com semente de luz", categoria="poi", sub="santuario", lote=1,
       voxel=BIG, frequencia="rara", wind=True,
       tags=["misterio", "descoberta", "magia", "clareira", "colina", "encontro"],
       funcao="descoberta e mistério: lugar mágico antigo (a semente P1 no altar = 'vida mágica do lugar'); missão "
              "de ativar/proteger, encontro ao entardecer, ponto de chegada de trilha",
       terreno=["grama", "colina"], apoio={"tipo": "terreno", "_nota": "as pedras se enterram; aceita desnível"},
       inclinacao_max_u=0.7, area_livre={"raio_u": 4.0},
       colisao={"tipo": "nenhum", "formas": "pedras", "_nota": "7 pedras = 7 círculos (formas_u); uma forma só "
                "bloquearia o centro"},
       restricoes=["clareira aberta (sem árvore a menos de 1,5 u do anel)", "no máximo 1 por região"],
       notas="CONCEITO APROVADO, refinamento artístico pendente (decisão do proprietário, lote 1): as pedras agradam, "
             "mas o conjunto ainda lembra monólitos genéricos e a semente P1 quase some na câmera de jogo. Fica no "
             "catálogo como candidato artístico aceito, NÃO liberado para distribuição procedural definitiva. Estudo "
             "controlado de refinamento no lote 4 (sem refazer do zero).",
       variacoes={"semente": [1, 2, 3], "pedra_caida": [True, False]})
def stone_circle(semente=1, pedra_caida=True):
    """Sete pedras em pé, ovais e arredondadas (sem lintel: não é nenhum monumento conhecido), de alturas
    desiguais (1,1–2,2 u), inclinadas para fora, espaçamento irregular; uma caída e meio enterrada. No centro, laje
    baixa com UMA semente de luz P1 (2 voxels). Faixas entalhadas horizontais nas pedras (não espirais: P2 não foi
    aprovada), musgo nos topos."""
    v = Vox()
    s = semente
    n = 7
    stones_at = []
    for i in range(n):
        a = (i + 0.35 * (hash3(i, 1, 0, s) - 0.5)) * 2 * math.pi / n + 0.2
        if pedra_caida and i == 4:
            continue
        r = 24 + 2 * (hash3(i, 2, 0, s) - 0.5)
        cx, cz = math.cos(a) * r, math.sin(a) * r
        hgt = 9 + int(hash3(i, 3, 0, s) * 9)
        rx, rz = 2.6 + hash3(i, 4, 0, s) * 0.8, 2.2 + hash3(i, 5, 0, s) * 0.6
        st = Vox()
        sblob(st, 0, hgt * 0.5 - 1, 0, rx, hgt * 0.62, rz, STONE, p=2.6)
        for k in [k for k in st.v if k[1] < -3]:
            del st.v[k]
        lean = 0.08
        for (x, y, z), c in st.v.items():                              # gira para encarar o centro e inclina para fora
            v.set(int(round(cx + (x + max(0, y) * lean) * math.cos(a) - z * math.sin(a))), y,
                  int(round(cz + (x + max(0, y) * lean) * math.sin(a) + z * math.cos(a))), c)
        stones_at.append((cx, cz, max(rx, rz)))
    if pedra_caida:                                                     # a pedra caída: deitada, meio enterrada
        a = 4 * 2 * math.pi / n + 0.2
        cx, cz = math.cos(a) * 25, math.sin(a) * 25
        st = Vox()
        sblob(st, 0, 0, 0, 7.0, 2.6, 2.6, STONE, p=2.6)
        for (x, y, z), c in st.v.items():
            if y >= 0:
                v.set(int(round(cx + x * math.cos(a + 1.2) - z * math.sin(a + 1.2))), y,
                      int(round(cz + x * math.sin(a + 1.2) + z * math.cos(a + 1.2))), c)
        stones_at.append((cx, cz, 4.0))
    EB.stones(v, seed=110 + s, cell=(4, 3, 4))
    for (x, y, z), c in list(v.v.items()):                              # faixas entalhadas a 1/3 e 2/3
        if y in (5, 6) and c in (STONE, STONE_L) and any((x + d[0], y, z + d[2]) not in v.v for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))):
            v.v[(x, y, z)] = STONE_D
    for (x, y, z), c in list(v.v.items()):                              # musgo só no CAPUZ de cada pedra
        if (x, y + 1, z) not in v.v and y >= 8 and hash3(x // 2, 0, z // 2, 111 + s) < 0.7:
            v.v[(x, y, z)] = MOSS
    # laje central e semente de luz
    v.merge(L.stone_block(-4, 0, -3, 3, 1, 2, seed=112 + s, soft=True))
    v.box(-2, 2, -1, 1, 2, 0, STONE_L)
    v.set(-1, 3, -1, SEED_P1)
    v.set(-1, 4, -1, SEED_P1)
    for (x, z) in ((-6, 3), (5, -4), (3, 5), (-5, -5)):                # tufos de musgo no chão do anel
        v.box(x, 0, z, x + 1, 0, z + 1, MOSS)
    import charlib_e
    charlib_e.prune_specks(v, label="stone_circle")
    pts = {
        "entradas": [("anel", (0, 0, 30), 0)],
        "interacao": [("altar", (-1, 2, 4), "ativar")],
        "spawns": [("guardiao", (0, 0, -12), "criatura")],
        "area_segura": [(-14, -14, 14, 14)],
        "formas": [("circulo", (cx, cz), r) for (cx, cz, r) in stones_at],
    }
    return v, pts


# ============================================================================= ruína de casa coberta de vegetação

@asset("ruin_house", integracao="depende_colisao_composta",
       dependencias=["formas compostas no Collision (interior caminhável): sem elas o interior fica dentro do sólido"], nome="Ruína de casa tomada pela mata", categoria="poi", sub="ruina", lote=1,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["ruina", "narrativa", "descoberta", "recompensa", "mata", "beira_de_trilha"],
       funcao="narrativa ambiental e recompensa: restos de uma casa da mesma cultura da vila (alguém morou aqui); "
              "lareira e escombros para examinar, baú futuro, encontro pequeno",
       terreno=["grama", "mata"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.5,
       area_livre={"frente_u": 1.5, "raio_u": 2.8},
       colisao={"tipo": "nenhum", "formas": "paredes", "_nota": "dois cantos de parede + chaminé: formas compostas "
                "(formas_u); o interior é caminhável"},
       restricoes=["fora do núcleo da vila (≥ 12 u do assentamento)", "beira de trilha ou dentro da mata"],
       variacoes={"semente": [1, 2, 3], "telhado_caido": [True, False]})
def ruin_house(semente=1, telhado_caido=True):
    """Planta da casa de referência em ruína: piso de pedra inteiro, dois CANTOS de parede (o de trás-esquerda alto,
    com estuque descascado e prumos do enxaimel subindo como costelas; o da frente-direita baixo), o ARCO da porta
    ainda de pé sozinho, a chaminé inteira até o meio, um pedaço do telhado coral caído em rampa, uma árvore jovem
    crescendo dentro, hera e musgo. Mesma cultura construtiva da vila — a ruína conta uma história."""
    v = Vox()
    s = semente
    x0, x1, z0, z1 = -18, 17, -14, 13
    L.foundation(v, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top=2, depth=-5, seed=120 + s)
    for x in range(x0, x1 + 1):                                      # piso: lajes, algumas faltando (grama)
        for z in range(z0, z1 + 1):
            if hash3(x // 3, 0, z // 3, s) < 0.18:
                v.set(x, 2, z, MOSS)
    # canto de trás-esquerda (alto): parede de trás x0..-2 e lateral z0..2, altura em degraus
    def wall_run(cells, hmax, hmin, seed):
        n = len(cells)
        for i, (x, z) in enumerate(cells):
            t = i / max(1, n - 1)
            h = int(hmax + (hmin - hmax) * t)
            h = (h // 2) * 2 + int(hash3(i // 3, 1, 0, seed) * 2) * 2
            for y in range(3, h):
                band = y < 6
                v.set(x, y, z, STONE if band else (PLASTER if hash3((x + z) // 6, y // 5, 0, seed) < 0.6 else STONE))
    back = [(x, z0) for x in range(x0, -1)]
    left = [(x0, z) for z in range(z0, 3)]
    wall_run(list(reversed(back)), 6, 20, 121 + s)
    wall_run(left, 20, 6, 122 + s)
    for (x, z, h) in ((x0 + 1, z0, 24), (x0, z0 + 1, 24), (-8, z0, 18), (x0, -6, 19)):   # prumos como costelas
        for y in range(3, h):
            v.set(x, y, z, TIMBER)
    for x in range(x0 + 1, -6):                                       # frechal partido
        v.set(x, 18, z0, TIMBER)
    # canto da frente-direita (baixo) e o arco da porta de pé
    for x in range(4, x1 + 1):
        for y in range(3, 3 + int(3 + 4 * hash3(x // 3, 2, 0, s))):
            v.set(x, y, z1, STONE)
    for z in range(4, z1 + 1):
        for y in range(3, 3 + int(3 + 5 * hash3(z // 3, 3, 0, s))):
            v.set(x1, y, z, STONE)
    dx0, dx1 = -11, -4
    for x in range(dx0 - 1, dx1 + 2):
        for y in range(3, 3 + 14):
            u = x + 0.5 - (dx0 + dx1 + 1) / 2.0
            inside = abs(u) <= 4.0 and (y < 11 or (u / 4.0) ** 2 + ((y - 11) / 4.2) ** 2 <= 1.0)
            frame = abs(u) <= 5.0 and (y < 11 or (u / 5.0) ** 2 + ((y - 11) / 5.4) ** 2 <= 1.0)
            if frame and not inside:
                v.set(x, y, z1, STONE_L)
                v.set(x, y, z1 - 1, STONE_L if (x + y) % 3 else STONE)
    # chaminé (de pé até o meio) e lareira por dentro
    ch = Vox()
    for y in range(3, 27):
        ch.box(10, y, -10, 14, y, -6, STONE)
    for (bx, bz, top) in ((10, -10, 30), (12, -10, 28), (10, -8, 29), (13, -7, 28)):   # topo partido em blocos 2×2
        ch.box(bx, 27, bz, bx + 1, top, bz + 1, STONE)
    EB.stones(ch, seed=123 + s, cell=(2, 2, 2))
    for y in range(3, 8):
        for x in (11, 12, 13):
            ch.set(x, y, -6, shadow(STONE_D, 1.4) if y < 7 else STONE_L)
    v.merge(ch)
    EB.stones(v, seed=124 + s)
    # pedaço do telhado caído em rampa (da parede de trás até o piso)
    if telhado_caido:
        roof = Vox()
        for x in range(-16, -3):                                   # placa do telhado caída quase deitada
            for i in range(0, 15):
                y = 8 - i // 4
                z = z0 + 2 + i
                if (x > -6 and i > 10) or (x < -14 and i > 12) or (x + i) % 11 == 0 and i > 6:
                    continue                                        # borda quebrada
                c = ROOF_D if i % 3 == 0 else ROOF                  # fiadas ao longo da água
                roof.set(x, y, z, c)
                roof.set(x, y - 1, z, ROOF_D)
        shade(roof)
        v.merge(roof)
        for x in (-16, -10, -5):                                   # caibros partidos apoiados no muro
            L.line6(v, (x, 6, z0 + 14), (x, 15, z0 + 1), TIMBER)
        _rubble(v, [(-12, z0 + 19, 3)], seed=s + 3)                # telhas soltas ao pé da placa
    # árvore jovem crescendo dentro (canto da frente-esquerda) e arbustos nas bases
    EB.trunk_b(v, 9, 1.6, 0.9, y0=3, roots=3, seed=125 + s)
    crown = Vox()
    sblob(crown, 0, 15, 0, 4.6, 3.6, 4.4, L.LEAF, p=2.2)
    import env_e as EE
    EE.canopy_paint(crown, L.LEAF, 126 + s)
    for k in [k for k in v.v if k in crown.v]:
        del v.v[k]
    v.merge(crown)
    for (x, z, r) in ((x0 + 3, -9, 2.6), (8, z1 - 3, 2.2), (-2, z0 + 3, 2.4), (x1 + 2, 0, 2.6)):
        b = Vox()
        sblob(b, x, 3.5, z, r, r * 0.8, r, LEAF_BUSH, p=2.2)
        EE.canopy_paint(b, LEAF_BUSH, 127 + s)
        v.merge(b, overwrite=False)
    _weather(v, 0.55, seed=128 + s, min_y=6)
    # hera descendo da parede alta
    f = Face("z", z0, -1)
    for a in (x0 + 3, -12):
        for y in range(4, 19):
            for k in (0, 1):
                if hash3(a + k, y, 0, s) < 0.8:
                    f.put(v, a + k + (y // 5) % 2, y, IVY if hash3(a, y, 1, s) < 0.6 else light(IVY), d=1)
    _rubble(v, [(-2, 4, 3), (6, -4, 2), (x1 + 4, -10, 2), (-20, 8, 2)], seed=s + 7)
    import charlib_e
    charlib_e.prune_specks(v, label="ruin_house")
    pts = {
        "entradas": [("arco", (-8, 0, z1 + 4), 0), ("brecha", (x1 + 4, 0, -2), 90)],
        "interacao": [("lareira", (12, 3, -3), "examinar"), ("escombros", (-10, 3, -6), "procurar")],
        "spawns": [("escombros", (2, 3, -2), "criatura")],
        "area_segura": [(-14, -10, 8, 9)],
    }
    return v, pts
