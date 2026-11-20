"""Laboratório de conteúdo — ruínas novas (estudo pós-rodada 12): a abóbada soterrada e o sítio abandonado. APROVADAS
pelo proprietário sem redesenho (2026-11-20); a integração ao gerador v2 é do Claude principal (depois da rodada 12.1).

A `ruin_house` aparecia em todas as regiões v2 e a `ruin_tower` espera o arco alargado (P74). Estas são FONTES NOVAS,
com ids novos, sem tocar nas duas (nem em conteudo_poi.py). Mesma civilização da vila e da ruína de casa: pedra
lilás-quente, estuque, enxaimel, tábua. Mas cada uma tem planta, silhueta, material, entrada, volume, vegetação e
espaço caminhável PRÓPRIOS:

  ruin_vault       abóbada de pedra meio soterrada num morro de terra e relva: só a boca em arco aparece inteira, o
                   fundo está enterrado; um rasgo no teto deixa a luz cair sobre o entulho. Silhueta baixa e larga
                   (um morro com uma boca), interior comprido e coberto.
  ruin_farmstead   a empena alta e solitária de um celeiro que sumiu (pedra embaixo, tábuas em cima, portão em arco e
                   óculo vazado), o rastro baixo das fundações à frente e um curral de pedra seca ao lado. Silhueta
                   alta e chata (uma parede contra o céu), espaço todo aberto.

Regras de jogo que a forma já respeita (lição da ruína de casa no gerador v2):
  - piso e soleira com topo ≤ 2 voxels (0,25 u): abaixo do degrau que se sobe a pé (0,30 u), o pé nunca afunda;
  - nada entre 0,30 u e 0,8 u com cara de degrau: entulho baixo (≤ 0,375 u, pisável), peça baixa com topo plano
    (barril, cocho, mureta do curral: pisáveis, uma altura só) ou parede alta (≥ 1,25 u);
  - vãos de passagem com folga para o corpo do herói (raio 0,3 u): boca da abóbada 1,75 u, portão 1,25 u, porteira 1,0 u.
"""
import math

import env_b as EB
import env_e as EE
import conteudo_lib as L
from chars_b import P
from conteudo_lib import (BIG, IRON, IVY, LEAF_BUSH, MOSS_FIX as MOSS, PLASTER, STONE, STONE_D, STONE_L, TIMBER, WOOD,
                          WOOD_D, Face, asset, light, sblob, shade, shadow)
from envlib import hash3
from voxlib import Vox

# Cores novas (papel na tabela de docs/art/conteudo-mundo.md §Paleta):
TURF = P("#86b04a")          # relva FOSCA do morro: a mesma cor da grama da E, mas não balança (topo a mais de 1,7 u)
EARTH, EARTH_D = P("#9a7458"), P("#76563f")   # terra exposta no corte do morro e nas bordas do rasgo
BOARD, BOARD_D = P("#9b8a78"), P("#73665a")   # tábua velha de celeiro, acinzentada pelo tempo (não é a madeira nova)


def _rubble_low(v, spots, y, seed=0):
    """Entulho BAIXO (1 voxel de altura sobre o piso): pedras soltas que se pisam, nunca um degrau falso."""
    for i, (x, z, n) in enumerate(spots):
        for j in range(n):
            bx = x + int(hash3(i, j, 1, seed) * 6) - 3
            bz = z + int(hash3(i, j, 2, seed) * 6) - 3
            s = 2 + int(hash3(i, j, 3, seed) * 2)
            col = (STONE, STONE_L, STONE_D)[int(hash3(i, j, 5, seed) * 3)]
            v.box(bx, y, bz, bx + s - 1, y, bz + s - 1, col)


def _weather(v, amount, seed, min_y):
    """Musgo FOSCO em manchas 2×2 nos topos expostos de pedra (o da ruína de casa, sem folhagem presa)."""
    for (x, y, z), c in list(v.v.items()):
        if c in (STONE, STONE_L, STONE_D) and y >= min_y and (x, y + 1, z) not in v.v:
            if hash3(x // 2, 3, z // 2, seed) < amount:
                v.v[(x, y, z)] = MOSS if hash3(x // 2, y // 2, z // 2, seed + 1) < 0.7 else light(MOSS)


def _shift(v, pts, dx):
    """Desloca a composição em x (voxels): a origem do prop é o centro da pegada (contrato do catálogo)."""
    out = Vox()
    for (x, y, z), c in v.v.items():
        out.v[(x + dx, y, z)] = c
    moved = {}
    for kind, items in pts.items():
        if kind == "area_segura":
            moved[kind] = [(x0 + dx, z0, x1 + dx, z1) for (x0, z0, x1, z1) in items]
        else:
            moved[kind] = [(i, (p[0] + dx, p[1], p[2]), r) for (i, p, r) in items]
    return out, moved


def _tufts(v, spots, seed, col=None):
    """Tufos de capim e ervas no chão (folhagem SOLTA, abaixo de 0,5 u: balançam sem abrir fresta em nada rígido)."""
    col = col or LEAF_BUSH
    for i, (x, z, r) in enumerate(spots):
        b = Vox()
        sblob(b, x, 1.2, z, r, 1.6, r, col, p=2.0)
        EE.canopy_paint(b, col, seed + i)
        v.merge(b, overwrite=False)


# ============================================================================= abóbada soterrada

VAULT_IN = (-7, 6)            # vão interno em x (14 voxels = 1,75 u)
VAULT_Z = (-15, 9)            # interior em z (o fundo é a parede z ≤ -16)
SPRING = 10                   # nascença do arco (1,25 u): a cabeça do herói passa até rente à parede


@asset("ruin_vault", nome="Abóbada soterrada", categoria="poi", sub="ruina", lote=3, voxel=BIG, frequencia="incomum",
       wind=True, tags=["ruina", "abrigo", "descoberta", "recompensa", "narrativa", "morro", "interior_coberto"],
       funcao="abrigo e descoberta: depósito abobadado da mesma cultura da vila, quase engolido pelo morro; dentro, "
              "a tábua entalhada no nicho do fundo, barris velhos e o entulho sob o rasgo de luz; encontro pequeno "
              "no fundo",
       terreno=["grama", "mata", "encosta_suave"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.5,
       area_livre={"frente_u": 2.0, "raio_u": 3.2},
       colisao={"tipo": "nenhum", "formas": "paredes", "camera": "teto_coberto",
                "_nota": "paredes da abóbada, morro, barris e o nicho: formas compostas medidas; o interior (1,75 u × "
                         "3,1 u) é caminhável. TETO: o interior é coberto — a abóbada e o morro precisam de formas SÓ DE "
                         "CÂMERA sobre medidas.cobertura_u (a câmera de terceira pessoa encurta o braço em vez de entrar "
                         "no teto); o rasgo de luz não tem teto e fica sem forma de câmera"},
       restricoes=["fora do núcleo da vila (≥ 12 u do assentamento)", "fundo do morro voltado para o declive ou para a "
                   "mata; a boca para uma clareira ou trilha", "nunca com a boca para a água"],
       relacoes=[("perto_de", "tag:trilha", [3.0, 12.0])],
       variacoes={"semente": [1, 2, 3], "raizes": [True, False]},
       aprovacao="aprovado", integracao="liberado",   # rodada 12.2: formas compostas medidas + CompositeCollisionTest
       decisao="proprietário, fechamento do estudo de NPCs de ofício e ruínas (2026-11-20): aprovada sem redesenho",
       dependencias=["formas compostas no Collision (interior caminhável)",
                     "formas só de câmera para o teto e o morro sobre medidas.cobertura_u (o interior é coberto)",
                     "integração ao gerador v2 pelo Claude principal, depois da validação da rodada 12.1"],
       notas="Piso 0,25 u e soleira 0,125 u (abaixo do degrau de 0,30 u); entulho com 1 voxel sobre o piso.")
def ruin_vault(semente=1, raizes=True):
    v = Vox()
    s = semente
    (ix0, ix1), (iz0, iz1) = VAULT_IN, VAULT_Z
    cx = (ix0 + ix1 + 1) / 2.0                                      # eixo da abóbada (x = -0,5)
    r_in, r_out = (ix1 - ix0 + 1) / 2.0, (ix1 - ix0 + 1) / 2.0 + 3.2
    wz0, wz1 = iz0 - 3, iz1                                         # parede do fundo: 3 voxels
    # --- morro: altura por coluna (fundo enterrado, boca exposta), terra no corte, relva no topo
    mound = Vox()
    for x in range(-18, 18):
        for z in range(-29, 13):
            u = ((x + 0.5 - cx) / 16.5) ** 2 + ((z + 0.5 + 8.0) / 19.5) ** 2
            if u >= 1:
                continue
            h = int(22.0 * (1 - u) ** 0.5 + hash3(x // 4, 0, z // 4, 31 + s) * 2.0 - 0.6)   # morro irregular, sem cúpula
            if z >= 8 and abs(x + 0.5 - cx) <= 11.5:                # corredor da boca: o morro não cobre a frente
                continue
            for y in range(0, h + 1):
                mound.set(x, y, z, TURF)                            # superfície toda de relva (sem listras)
    for (x, y, z) in list(mound.v):                                 # terra só no corte vertical ao lado da boca
        if z >= 7 and 10.5 <= abs(x + 0.5 - cx) <= 13.5 and (x, y + 1, z) in mound.v:
            mound.v[(x, y, z)] = EARTH_D if (x + y) % 4 == 0 else EARTH
    # --- abóbada: paredes, arco, fundo e a boca em aduelas
    vault = Vox()
    for z in range(wz0, wz1 + 1):
        for x in range(ix0 - 4, ix1 + 5):
            for y in range(2, int(SPRING + r_out) + 2):
                d = math.hypot(x + 0.5 - cx, max(0.0, y + 0.5 - SPRING))
                inner = abs(x + 0.5 - cx) <= r_in and (y < SPRING or d <= r_in)
                if z < iz0:                                         # parede do fundo: tudo até o arco externo
                    if d <= r_out:
                        vault.set(x, y, z, STONE)
                elif not inner and d <= r_out and abs(x + 0.5 - cx) <= r_in + 3.2:
                    vault.set(x, y, z, STONE)
    EB.stones(vault, seed=200 + s, cell=(3, 2, 3))
    ring = Vox()                                                    # aduelas da boca: anel claro saliente 1 voxel
    for x in range(ix0 - 4, ix1 + 5):
        for y in range(2, int(SPRING + r_out) + 2):
            d = math.hypot(x + 0.5 - cx, max(0.0, y + 0.5 - SPRING))
            inner = abs(x + 0.5 - cx) <= r_in and (y < SPRING or d <= r_in)
            if not inner and d <= r_in + 2.6 and abs(x + 0.5 - cx) <= r_in + 2.6:
                ang = math.atan2(y + 0.5 - SPRING, x + 0.5 - cx)
                joint = y >= SPRING and abs(math.sin(ang * 6.5)) < 0.16
                ring.set(x, y, wz1 + 1, STONE_D if joint else STONE_L)
                ring.set(x, y, wz1, STONE_D if joint else STONE_L)
    for (x, y) in ((-1, int(SPRING + r_in)), (0, int(SPRING + r_in)), (-1, int(SPRING + r_in) + 1),
                   (0, int(SPRING + r_in) + 1), (-1, int(SPRING + r_in) + 2), (0, int(SPRING + r_in) + 2)):
        ring.set(x, y, wz1 + 2, STONE_L)                            # fecho (pedra-chave) saliente
    ring.set(-1, int(SPRING + r_in) + 1, wz1 + 3, STONE_D)          # marca entalhada no fecho
    ring.set(0, int(SPRING + r_in) + 1, wz1 + 3, STONE_D)
    vault.merge(ring)
    # --- rasgo no teto: a abóbada e o morro caíram num trecho do meio
    hole = lambda x, y, z: -4 <= x <= 3 and -12 <= z <= -6 and y >= SPRING + 3
    for k in [k for k in vault.v if hole(*k)]:
        del vault.v[k]
    for k in [k for k in mound.v if -5 <= k[0] <= 4 and -13 <= k[2] <= -5 and k[1] >= SPRING + 3]:
        del mound.v[k]
    def interior(x, y, z):
        d = math.hypot(x + 0.5 - cx, max(0.0, y + 0.5 - SPRING))
        return iz0 <= z <= wz1 + 4 and y >= 2 and abs(x + 0.5 - cx) <= r_in and (y < SPRING or d <= r_in)
    for k in [k for k in mound.v if k in vault.v or interior(*k)]:  # a pedra manda; o morro só cobre por fora
        del mound.v[k]
    for (x, y, z) in list(mound.v):                                 # bordas do rasgo: terra à mostra
        if -6 <= x <= 5 and -14 <= z <= -4 and (x, y + 1, z) in mound.v:
            mound.v[(x, y, z)] = EARTH_D if (x + z) % 3 == 0 else EARTH
    for i in range(5):                                              # pedras da abóbada aflorando na relva do morro
        ox = int(cx + (hash3(i, 1, 9, s) - 0.5) * 20)
        oz = -24 + int(hash3(i, 2, 9, s) * 22)
        col = [k[1] for k in mound.v if k[0] == ox and k[2] == oz]
        if col and max(col) > 6:
            top = max(col)
            mound.box(ox - 1, top - 1, oz - 1, ox + 1, top, oz + 1, STONE if i % 2 else STONE_L)
    shade(mound, only={EARTH, TURF})
    v.merge(mound)
    v.merge(vault)
    # --- piso de lajes (topo y = 1: 0,25 u) e soleira (y = 0: 0,125 u)
    L.foundation(v, ix0 - 4, wz0, ix1 + 4, wz1 + 1, top=1, depth=-5, seed=210 + s)
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            v.set(x, 1, z, STONE_L if (x // 3 + z // 4) % 2 else STONE)
            if hash3(x // 3, 0, z // 3, s) < 0.12:
                v.set(x, 1, z, MOSS)
    for x in range(ix0 + 1, ix1):                                   # soleira gasta à frente da boca
        for z in range(wz1 + 2, wz1 + 4):
            v.set(x, 0, z, STONE if (x + z) % 4 else STONE_D)
    # --- entulho sob o rasgo (1 voxel sobre o piso: pisável) e samambaias na luz
    _rubble_low(v, [(-1, -9, 4), (2, -7, 2)], y=2, seed=220 + s)
    _tufts(v, [(-3, -10, 1.6), (2, -11, 1.4)], seed=221 + s)
    for k in [k for k in v.v if k[1] > 3 and v.v[k][3] == "foliage"]:  # samambaia baixa
        del v.v[k]
    # --- fundo: nicho com a tábua entalhada
    for x in range(-2, 2):
        for y in range(4, 10):
            v.v.pop((x, y, iz0 - 1), None)
            v.set(x, y, iz0 - 2, STONE_D)
    for x in range(-2, 2):                                          # tábua: pedra clara com um sinal (espiral de 3 voltas)
        for y in range(4, 9):
            v.set(x, y, iz0 - 1, STONE_L)
    for (x, y) in ((-2, 7), (-1, 7), (0, 7), (0, 6), (0, 5), (-1, 5), (-2, 5), (-2, 6), (1, 8), (1, 4)):
        v.set(x, y, iz0 - 1, shadow(STONE_D, 1.2))
    for x in range(-3, 3):                                          # peitoril do nicho
        v.set(x, 3, iz0 - 1, STONE)
    # --- barris velhos no canto do fundo-esquerda: um em pé (topo 0,75 u: peça baixa pisável), um tombado e aberto
    keg = Vox()
    for y in range(2, 6):
        for x in range(ix0, ix0 + 4):
            for z in range(iz0, iz0 + 4):
                if (x - ix0 - 1.5) ** 2 + (z - iz0 - 1.5) ** 2 <= 4.2:
                    keg.set(x, y, z, IRON if y in (3, 5) else (WOOD_D if (x + z) % 2 else WOOD))
    for (x0, z0, n) in ((ix0, iz0 + 5, 5), (ix0 + 1, iz0 + 7, 4), (ix0 + 4, iz0 + 6, 3)):   # o outro desfeito: aduelas
        for i in range(n):                                          # soltas no piso (1 voxel: pisa-se por cima)
            keg.set(x0 + i, 2, z0, WOOD_D if i % 2 else WOOD)
    keg.set(ix0 + 2, 2, iz0 + 6, IRON)                              # um aro caído entre as aduelas
    keg.set(ix0 + 3, 2, iz0 + 6, IRON)
    shade(keg, only={WOOD})
    v.merge(keg)
    # --- raízes e hera FOSCAS descendo do rasgo e da boca (presas a pedra/terra: não podem ser folhagem)
    if raizes:
        for (x, z) in ((-4, -9), (3, -8), (-2, -12), (1, -6)):
            top = max((k[1] for k in v.v if k[0] == x and k[2] == z), default=0)
            for y in range(top - 1, max(SPRING + 1, top - 7), -1):
                if (x, y, z) not in v.v:
                    v.set(x, y, z, IVY if hash3(x, y, z, s) < 0.7 else shadow(IVY, 0.8))
        f = Face("z", wz1 + 1, 1)
        for a in (ix0 - 3, ix1 + 2):
            for y in range(int(SPRING + 1), int(SPRING + r_out) - 1):
                if hash3(a, y, 7, s) < 0.75 and (a, y, wz1 + 1) in v.v:
                    f.put(v, a + (y // 4) % 2, y, IVY if hash3(a, y, 8, s) < 0.6 else light(IVY), d=1)
    # --- moitas na base do morro (folhagem solta e baixa) e musgo fosco nas pedras
    _tufts(v, [(-15, 6, 2.2), (14, 4, 2.0), (-9, 12, 1.6), (9, 13, 1.6)], seed=230 + s)
    _weather(v, 0.35, seed=240 + s, min_y=SPRING)
    import charlib_e
    charlib_e.prune_specks(v, label="ruin_vault")
    pts = {
        "entradas": [("boca", (0, 0, wz1 + 7), 0)],
        "interacao": [("tabua_entalhada", (0, 2, iz0 + 3), "examinar"), ("barris", (ix0 + 3, 2, iz0 + 9), "procurar"),
                      ("rasgo_de_luz", (0, 2, -9), "observar")],
        "spawns": [("fundo", (3, 2, iz0 + 4), "criatura")],
        "observacao": [("clareira", (0, 0, wz1 + 34), 180), ("flanco", (30, 0, 6), 250)],
        "area_segura": [(ix0 + 3, iz0 + 6, ix1 - 2, iz1 - 1)],
    }
    return v, pts


# ============================================================================= sítio abandonado

GABLE_Z = (-14, -12)          # a empena (3 voxels de espessura)
GABLE_X = (-14, 13)
EAVE, RIDGE = 21, 34          # beiral 2,75 u, cumeeira 4,25 u
DOOR_X = (-5, 4)              # portão: 10 voxels = 1,25 u
CORRAL = (30, -2)             # centro do curral (voxels)


@asset("ruin_farmstead", nome="Sítio abandonado: empena do celeiro e curral", categoria="poi", sub="ruina", lote=3,
       voxel=BIG, frequencia="incomum", wind=True,
       tags=["ruina", "marco", "narrativa", "descoberta", "campo", "beira_de_trilha", "visivel_de_longe"],
       funcao="marco de orientação e narrativa ambiental: um celeiro sumiu e deixou a empena de pé (visível de longe), "
              "o rastro das fundações e o curral; a pedra de marco com o sinal do sítio para examinar, o cocho para "
              "procurar, criaturas pequenas no curral",
       terreno=["grama", "campo"], apoio={"tipo": "patamar", "margem_u": 0.5}, inclinacao_max_u=0.4,
       area_livre={"frente_u": 2.0, "raio_u": 4.2},
       colisao={"tipo": "nenhum", "formas": "paredes", "_nota": "empena, curral e cocho: formas compostas medidas; o "
                "rastro das fundações (0,25 u) e o portão caído são pisáveis; o portão em arco atravessa a empena"},
       restricoes=["fora do núcleo da vila (≥ 12 u do assentamento)", "campo aberto ou beira de trilha: a empena é "
                   "um marco e precisa de céu atrás", "nunca dentro da mata fechada"],
       relacoes=[("perto_de", "tag:trilha", [2.0, 10.0])],
       variacoes={"semente": [1, 2, 3], "porteira_caida": [True, False]},
       aprovacao="aprovado", integracao="liberado",   # rodada 12.2: formas compostas medidas + CompositeCollisionTest
       decisao="proprietário, fechamento do estudo de NPCs de ofício e ruínas (2026-11-20): aprovada sem redesenho",
       dependencias=["formas compostas no Collision (curral com porteira; empena com o portão vazado)",
                     "integração ao gerador v2 pelo Claude principal, depois da validação da rodada 12.1"],
       notas="Rastro das fundações e soleira com 0,25 u; mureta do curral com topo plano a 0,75 u (peça baixa "
             "pisável, uma altura só, sem degrau falso); a empena passa de 1,25 u.")
def ruin_farmstead(semente=1, porteira_caida=True):
    v = Vox()
    s = semente
    gz0, gz1 = GABLE_Z
    gx0, gx1 = GABLE_X
    ac = (gx0 + gx1 + 1) / 2.0                                       # eixo da empena (x = -0,5)
    # --- empena: pedra até 0,75 u, tábuas acima; o canto de cima à esquerda caiu
    g = Vox()
    for x in range(gx0, gx1 + 1):
        half = abs(x + 0.5 - ac)
        top = EAVE + int(max(0.0, (gx1 - gx0 + 1) / 2.0 - half) * (RIDGE - EAVE) / ((gx1 - gx0 + 1) / 2.0))
        for y in range(0, top + 1):
            for z in range(gz0, gz1 + 1):
                if y <= 5:
                    g.set(x, y, z, STONE)
                else:
                    g.set(x, y, z, BOARD_D if (x - gx0) % 3 == 0 else BOARD)   # tábuas na vertical, junta escura
    for (x, y, z) in list(g.v):                                       # canto caído: borda irregular em blocos 2×2
        edge = 26 + int(hash3(x // 2, 1, 0, 300 + s) * 4) - (x - gx0) // 3
        if x <= -4 and y > max(edge, EAVE - 2):
            del g.v[(x, y, z)]
    EB.stones(g, seed=301 + s, cell=(3, 2, 3))
    for x in range(gx0, gx1 + 1):                                     # frechal (viga do beiral) e cinta sobre a pedra
        for z in range(gz0, gz1 + 1):
            if (x, EAVE, z) in g.v:
                g.v[(x, EAVE, z)] = TIMBER
            g.v[(x, 6, z)] = TIMBER
    # portão em arco (vazado de lado a lado) com batente de madeira
    dx0, dx1 = DOOR_X
    dc = (dx0 + dx1 + 1) / 2.0
    hw = (dx1 - dx0 + 1) / 2.0
    for x in range(dx0 - 1, dx1 + 2):
        for y in range(1, 17):
            u = abs(x + 0.5 - dc)
            inside = u <= hw and (y < 11 or (u / hw) ** 2 + ((y - 11) / 4.5) ** 2 <= 1.0)
            frame = u <= hw + 1 and (y < 11 or (u / (hw + 1)) ** 2 + ((y - 11) / 5.5) ** 2 <= 1.0)
            for z in range(gz0, gz1 + 1):
                if inside:
                    g.v.pop((x, y, z), None)
                elif frame and (x, y, z) in g.v:
                    g.v[(x, y, z)] = TIMBER
    # estuque que sobrou num remendo da pedra (a mesma cultura da casa) e pedra de marco entalhada
    for x in range(7, 12):
        for y in range(1, 5):
            if hash3(x // 2, y // 2, 1, s) < 0.6:
                g.set(x, y, gz1, PLASTER)
    for (x, y) in ((8, 2), (9, 2), (9, 3), (10, 3), (8, 4), (10, 1)):   # sinal do sítio na pedra (traço escuro)
        g.set(x, y, gz1 + 1, shadow(STONE_D, 1.2))
    for x in range(7, 12):
        for y in range(1, 5):
            if (x, y, gz1 + 1) not in g.v:
                g.set(x, y, gz1 + 1, STONE_L)
    for z in (gz0, gz1):                                              # mão-francesa e pendural no triângulo, nas 2 faces
        f = Face("z", z, 1 if z == gz1 else -1)
        L.face_line(g, f, int(ac) - 1, EAVE, int(ac) - 1, RIDGE - 6, TIMBER)
        L.face_line(g, f, int(ac), EAVE, int(ac), RIDGE - 6, TIMBER)
        for a0 in (gx0 + 4, gx1 - 4):
            L.face_line(g, f, a0, EAVE + 1, int(ac) - 1 if a0 < ac else int(ac), RIDGE - 7, TIMBER)
    # óculo vazado DEPOIS do madeiramento (sem vidro: o céu aparece pelo furo), aro de madeira
    oc_y = 27
    for x in range(int(ac) - 4, int(ac) + 4):
        for y in range(oc_y - 4, oc_y + 5):
            d = math.hypot(x + 0.5 - ac, y + 0.5 - oc_y)
            for z in range(gz0, gz1 + 1):
                if d <= 2.2:
                    g.v.pop((x, y, z), None)
                elif d <= 3.3 and (x, y, z) in g.v:
                    g.v[(x, y, z)] = TIMBER
    g = L.shear(g, k=0.06, y0=6)                                     # fora de prumo, como a vila
    v.merge(g)
    L.foundation(v, gx0 - 1, gz0 - 1, gx1 + 1, gz1 + 1, top=0, depth=-5, seed=302 + s)
    for x in range(dx0, dx1 + 1):                                     # soleira do portão (0,125 u)
        for z in range(gz0 - 1, gz1 + 2):
            v.set(x, 0, z, STONE_L)
    # --- a cumeeira caída no chão, atravessada no rastro do celeiro (2 × 2 deitada: 0,25 u, pisável)
    for k in (0, 1):
        L.line6(v, (-9, k, gz1 + 5), (6, k, gz1 + 13), TIMBER)
        L.line6(v, (-9, k, gz1 + 6), (6, k, gz1 + 14), shadow(TIMBER, 0.8))
    # --- rastro das fundações do celeiro (topo 0,25 u: pisável) com falhas
    for z in range(gz1 + 1, 7):
        for x in (gx0, gx0 + 1, gx1 - 1, gx1):
            if hash3(x // 2, 0, z // 3, 310 + s) < 0.82:
                v.box(x, 0, z, x, 1, z, STONE if (x + z) % 3 else STONE_D)
    for x in range(gx0, gx1 + 1):
        if hash3(x // 3, 1, 0, 311 + s) < 0.6:
            v.box(x, 0, 5, x, 1, 6, STONE if (x + 1) % 3 else STONE_D)
    # --- curral de pedra seca: anel com topo plano a 0,75 u (y 0..5), porteira de 1,0 u na frente (+z)
    ccx, ccz = CORRAL
    corral = Vox()
    for x in range(ccx - 15, ccx + 16):
        for z in range(ccz - 15, ccz + 16):
            d = math.hypot(x + 0.5 - ccx, z + 0.5 - ccz)
            gate = abs(x + 0.5 - ccx) <= 5.0 and z > ccz                 # porteira de 1,25 u
            if 11.0 <= d <= 13.6 and not gate:
                for y in range(-3, 6):
                    corral.set(x, y, z, STONE_L if y == 5 else STONE)
    EB.stones(corral, seed=320 + s, cell=(2, 2, 2))
    for (x, y, z) in list(corral.v):                                  # capeamento: a fiada de cima é uma só cor clara
        if y == 5:
            corral.v[(x, y, z)] = STONE_L if hash3(x // 2, 5, z // 2, 321 + s) < 0.7 else STONE
    v.merge(corral)
    # cocho de pedra (peça baixa: 0,5 u, topo plano) com água parada e escura
    for x in range(ccx - 2, ccx + 4):                                 # encostado no fundo do curral (−z)
        for z in range(ccz - 10, ccz - 7):
            for y in range(0, 4):
                inner = ccx - 1 <= x <= ccx + 2 and z == ccz - 9 and y == 3
                v.set(x, y, z, shadow(L.WATER, 1.3) if inner else (STONE_D if y == 0 else STONE))
    # porteira de tábuas caída na grama (1 voxel: pisável) ou ainda encostada na mureta
    if porteira_caida:
        for x in range(ccx - 4, ccx + 4):
            for z in range(ccz + 15, ccz + 18):
                if not (z == ccz + 16 and x in (ccx - 3, ccx + 2)):
                    v.set(x, 0, z, BOARD_D if (x - ccx) % 3 == 0 or z != ccz + 16 else BOARD)
    else:                                                             # encostada na mureta, fora do vão
        for x in range(ccx + 6, ccx + 12):
            for y in range(1, 6):
                if y in (1, 3, 5) or (x - ccx) % 3 == 0:
                    v.set(x, y, ccz + 13, BOARD_D if y == 5 else BOARD)
    # --- vegetação: hera fosca na pedra da empena, musgo fosco no capeamento, capim e flores soltos no chão
    f = Face("z", gz1 + 1, 1)
    for a in (gx0 + 1, gx1 - 3):
        for y in range(1, 12):
            if hash3(a, y, 3, s) < 0.75 and (a + (y // 5) % 2, y, gz1 + 1) not in v.v:
                f.put(v, a + (y // 5) % 2, y, IVY if hash3(a, y, 4, s) < 0.6 else light(IVY), d=0)
    _weather(v, 0.3, seed=330 + s, min_y=5)
    _tufts(v, [(gx0 - 2, gz1 + 3, 1.8), (gx1 + 2, gz0 + 2, 2.0), (ccx - 6, ccz - 4, 1.4), (ccx + 6, ccz + 4, 1.3),
               (ccx + 16, ccz - 4, 1.8), (-2, 3, 1.4)], seed=331 + s)
    flowers = Vox()
    for i in range(7):
        fx = ccx - 7 + int(hash3(i, 1, 2, s) * 13)
        fz = ccz - 5 + int(hash3(i, 2, 2, s) * 12)
        if (fx, 1, fz) not in v.v:
            flowers.set(fx, 1, fz, (L.PINK, L.YELLOW, L.CREAM)[i % 3])
            flowers.set(fx, 0, fz, LEAF_BUSH)
    v.merge(flowers, overwrite=False)
    import charlib_e
    charlib_e.prune_specks(v, label="ruin_farmstead")
    pts = {
        "entradas": [("portao", (0, 0, gz1 + 8), 0), ("porteira", (ccx, 0, ccz + 21), 0)],
        "interacao": [("pedra_do_marco", (9, 2, gz1 + 6), "examinar"), ("cocho", (ccx + 1, 2, ccz - 2), "procurar")],
        "spawns": [("curral", (ccx - 4, 2, ccz + 3), "criatura")],
        "observacao": [("trilha", (6, 0, 48), 180), ("campo", (-46, 0, -2), 90)],
        "area_segura": [(gx0 + 3, gz1 + 3, gx1 - 3, 3)],
    }
    return _shift(v, pts, -14)
