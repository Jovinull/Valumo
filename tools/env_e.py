"""Direção E — Fábula Voxel: ambiente consolidado (rodada 02).

Base: a linguagem de forma da B (massas redondas, troncos em pera, casa fora de prumo, telhado
côncavo). O que muda, e por quê (docs/art/regras-de-vegetacao.md, estilo-arquitetonico.md):

  * Folhagem um pouco mais saturada e pintada em 4 tons com manchas LARGAS (blocos de 4 voxels,
    amplitude 0,14): a B usava 0,30 e as copas grandes viravam "camuflagem".
  * Copas com recorte: tufos pequenos na borda quebram o contorno liso de novelo sem virar ruído.
  * Biblioteca de árvores: 4 carvalhos de silhueta diferente (largo, oval em andares, jovem torto,
    outono), pinheiro, bétula e a árvore-marco. Poucas espécies, variação controlada.
  * Plantas pequenas a 1/24 u (eram 1/16: "grossas"), menos flores por touceira.
  * Casa: telhas em fiadas limpas (sem manchas aleatórias), estuque sem pintas, escada de pedra de
    três degraus, alpendre sobre a porta, floreira e lanterna mantidas.
  * Todo prop com folhagem declara "affineWind": o viewer pode fundir as faces da folhagem.

A paleta é a da B com ajustes pontuais (tabela em docs/art/paleta-e-materiais.md). As funções
de env_b leem as cores como globais do módulo; trocamos e restauramos ao redor de cada chamada.
"""
import math

import env_b as EB
from chars_b import P, light, normal, sblob, shade, shadow, stack
from envlib import hash3
from voxlib import C, PropSet, Vox

BIG, FINE, SMALL = 1 / 8, 1 / 16, 1 / 24

E_PALETTE = {
    "LEAF": P("#76ad47", "foliage"),
    "LEAF_AUTUMN": P("#e98f3a", "foliage"),
    "LEAF_PINE": P("#3f8a62", "foliage"),
    "LEAF_BIRCH": P("#b8cb55", "foliage"),
    "LEAF_BIG": P("#68a64a", "foliage"),
    "LEAF_BUSH": P("#6a9f47", "foliage"),
    "GRASS": P("#86b04a", "foliage"),
    "MOSS": P("#92b54e", "foliage"),
    "ROOF": P("#cb553f"),
    "ROOF_D": P("#a2403b"),
    "ROOF_L": P("#cb553f"),          # sem manchas claras aleatórias: a luz vem do sombreamento
    "PLASTER": P("#f4e4c4"),
    "PLASTER_D": P("#f4e4c4"),       # sem pintas no estuque
}


class palette:
    """Contexto que troca as cores globais de env_b pelas da E."""

    def __enter__(self):
        self.saved = {k: getattr(EB, k) for k in E_PALETTE}
        for k, c in E_PALETTE.items():
            setattr(EB, k, c)

    def __exit__(self, *a):
        for k, c in self.saved.items():
            setattr(EB, k, c)


LEAF = E_PALETTE["LEAF"]
LEAF_AUTUMN = E_PALETTE["LEAF_AUTUMN"]
LEAF_BIG = E_PALETTE["LEAF_BIG"]
LEAF_BUSH = E_PALETTE["LEAF_BUSH"]


# ----------------------------------------------------------------------------- copas

def canopy_paint(v, base, seed=0, clump=4, amp=0.14):
    """4 tons pela normal suavizada + manchas largas e fracas: lê como pincelada, não camuflagem."""
    tones = (shadow(base, 1.6), shadow(base, 0.8), base, light(base, 0.9))
    d = v.v
    for (x, y, z), c in list(d.items()):
        if c != base:
            continue
        if all((x + a, y + b, z + e) in d for (a, b, e) in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            d[(x, y, z)] = tones[1]
            continue
        n = normal(v, x, y, z)
        L = 0.75 * n[1] + 0.25 * (-0.36 * n[0] + 0.8 * n[1] + 0.48 * n[2])
        L += (hash3(x // clump, y // clump, z // clump, seed) - 0.5) * amp * 2
        d[(x, y, z)] = tones[3] if L > 0.60 else (tones[2] if L > 0.08 else (tones[1] if L > -0.45 else tones[0]))
    return v


def crown(v, lobes, base, seed=0, tufts=7, p=2.15):
    """Copa = novelos que se sobrepõem + tufos na borda (recorte da silhueta)."""
    tmp = Vox()
    for (x, y, z, rx, ry, rz) in lobes:
        sblob(tmp, x, y, z, rx, ry, rz, base, p=p)
    surf = sorted(k for k in tmp.v if (k[0], k[1] + 1, k[2]) not in tmp.v or (k[0] + 1, k[1], k[2]) not in tmp.v
                  or (k[0] - 1, k[1], k[2]) not in tmp.v or (k[0], k[1], k[2] + 1) not in tmp.v
                  or (k[0], k[1], k[2] - 1) not in tmp.v)
    placed = 0
    for i, (x, y, z) in enumerate(surf):
        if placed >= tufts:
            break
        if hash3(x, y, z, seed + 31) < tufts / max(1, len(surf)) * 1.6:
            n = normal(tmp, x, y, z)
            if n[1] < -0.35:
                continue
            r = 2.3 + 1.1 * hash3(x, y, z, seed + 7)
            sblob(tmp, x + n[0] * 2.0, y + n[1] * 1.6, z + n[2] * 2.0, r, r * 0.8, r, base, p=2.1)
            placed += 1
    canopy_paint(tmp, base, seed)
    v.merge(tmp)
    return v


def crown_masses(v, masses, base, seed=0, drops=7, p=2.2):
    """Copa da rodada 03 ("massas com cachos"): poucas massas grandes, cada uma com a BASE ACHATADA e
    avesso escuro (separa copa e tronco), leve variação de valor entre massas (lê volumes separados) e
    uma franja de cachos pendentes em ritmo regular na borda de baixo — o recorte vem do desenho, não
    de tufos aleatórios. masses = [(x, y, z, rx, ry, rz)]; a primeira é a dominante."""
    tmp = Vox()
    owner = {}
    for mi, (x, y, z, rx, ry, rz) in enumerate(masses):
        cut = y - ry * 0.55                                # base achatada
        for k in list(sblob(Vox(), x, y, z, rx, ry, rz, base, p=p).v):
            if k[1] >= cut and k not in tmp.v:
                tmp.v[k] = base
                owner[k] = mi
        for i in range(drops):                             # cachos pendentes na borda de baixo
            a = (i + 0.5 * (mi % 2)) * 2 * math.pi / drops + seed * 0.37
            cx, cz = x + math.cos(a) * rx * 0.78, z + math.sin(a) * rz * 0.78
            r = 1.7 + 0.5 * hash3(i, mi, 3, seed)
            for k in list(sblob(Vox(), cx, cut + 0.6, cz, r, r * 1.15, r, base, p=2.0).v):
                if k not in tmp.v:
                    tmp.v[k] = base
                    owner[k] = mi
    canopy_paint(tmp, base, seed, clump=5, amp=0.08)
    # variação de valor por massa: as satélites um pouco mais escuras/frias que a dominante
    for k, mi in owner.items():
        if mi > 0 and k in tmp.v and (mi % 2 == 1):
            c = tmp.v[k]
            tmp.v[k] = (max(0, c[0] - 6), max(0, c[1] - 4), min(255, c[2] + 2), c[3])
    # avesso: faces de baixo das massas um tom mais escuro (contato com o tronco)
    for (x, y, z), c in list(tmp.v.items()):
        if (x, y - 1, z) not in tmp.v:
            tmp.v[(x, y, z)] = shadow(base, 1.6)
    v.merge(tmp)
    return v


def crown_smooth(v, lobes, base, seed=0):
    """Estudo O1: novelos lisos da B (sem tufos), com a pintura larga da E."""
    tmp = Vox()
    for (x, y, z, rx, ry, rz) in lobes:
        sblob(tmp, x, y, z, rx, ry, rz, base, p=2.15)
    canopy_paint(tmp, base, seed)
    v.merge(tmp)
    return v


def oak_study(kind):
    """Carvalho de referência com três copas sobre o MESMO tronco (rodada 03)."""
    v = Vox()
    EB.trunk_b(v, 20, 3.6, 1.7, path=lambda t: (2.2 * math.sin(t * 2.6), 0.8 * t), seed=1)
    EB.branch(v, (1, 14, 0), (6, 19, -2), 1.1)
    EB.branch(v, (1, 13, 0), (-6, 18, 2), 1.1)
    shade(v)
    lobes = [(1, 27, 0, 11, 8.5, 10.5), (-7.5, 22, 3, 6.5, 5.5, 6.5), (8, 23, -3, 7, 6, 6.5),
             (2.5, 32.5, -1, 7, 5.2, 7), (-3, 24, -7, 6, 5, 5.5)]
    if kind == "o1":
        crown_smooth(v, lobes, LEAF, 1)
    elif kind == "o2":
        crown(v, lobes, LEAF, 1)
    else:
        crown_masses(v, [(1.5, 28.5, 0, 11.5, 8.0, 10.5), (-7.5, 23.5, 3, 7.0, 5.6, 6.8), (8.5, 24.5, -3, 7.0, 5.8, 6.6),
                         (2.5, 34.0, -1.5, 7.0, 5.0, 6.6)], LEAF, 1)
    return v


# ----------------------------------------------------------------------------- árvores

def tree_oak():
    """Carvalho largo (referência da rodada 03): 4 massas com base achatada e franja de cachos."""
    return oak_study("o3")


def tree_oak_tall():
    """Carvalho oval em dois andares: silhueta vertical que separa os planos da floresta."""
    v = Vox()
    EB.trunk_b(v, 26, 3.0, 1.5, path=lambda t: (-1.4 * math.sin(t * 3.0), 0.5 * t), seed=21)
    EB.branch(v, (-1, 18, 0), (4, 23, 1), 1.0)
    shade(v)
    crown_masses(v, [(-1, 29, 0, 8, 7, 7.5), (0, 38.5, -1, 6.5, 6.2, 6.5), (4.5, 25, 1, 4.8, 4.0, 4.6),
                     (-5, 33.5, 2, 4.8, 4.2, 4.6)], LEAF, 22, drops=6)
    return v


def tree_oak_young():
    """Árvore jovem torta, copa assimétrica puxada para um lado: quebra a repetição de alturas."""
    v = Vox()
    EB.trunk_b(v, 15, 2.4, 1.2, roots=3, path=lambda t: (3.5 * t * t, -0.8 * t), seed=33)
    shade(v)
    crown_masses(v, [(4, 21, -1, 7.5, 5.8, 7), (-1.5, 19, 2, 4.6, 3.8, 4.4), (7.5, 25, -2, 4.4, 3.6, 4.4)], LEAF, 34,
                 drops=5)
    return v


def tree_autumn():
    """Acento quente: copa em guarda-chuva âmbar (a árvore 'tree_oak2' da cena)."""
    v = Vox()
    EB.trunk_b(v, 20, 3.2, 1.5, bark=EB.BARK_D, streak=EB.BARK, path=lambda t: (-2.6 * math.sin(t * 2.2), -0.6 * t), seed=4)
    EB.branch(v, (-2, 15, 0), (-8, 20, 1), 1.0, EB.BARK_D)
    EB.branch(v, (-2, 14, 0), (5, 20, -1), 1.0, EB.BARK_D)
    shade(v)
    crown_masses(v, [(-2, 28, 0, 12, 6.6, 10.5), (-9, 23, 1, 6.4, 4.6, 6), (6.5, 23.5, -1, 6.6, 5, 6),
                     (-1, 32.5, 0, 7.4, 4.6, 7)], LEAF_AUTUMN, 5, drops=8)
    return v


def tree_big():
    """Árvore-marco: a da B (porta, lanternas, cogumelos-prateleira) com a copa da E."""
    with palette():
        v = EB.tree_big()
    # repinta a copa com as pinceladas largas da E
    leaf_tones = {shadow(EB.LEAF_BIG, 1.7), shadow(EB.LEAF_BIG, 0.85), EB.LEAF_BIG, light(EB.LEAF_BIG, 1.25),
                  shadow(LEAF_BIG, 1.7), shadow(LEAF_BIG, 0.85), LEAF_BIG, light(LEAF_BIG, 1.25)}
    canopy = Vox()
    for k, c in list(v.v.items()):
        if c in leaf_tones:
            canopy.v[k] = LEAF_BIG
    canopy_paint(canopy, LEAF_BIG, 10, clump=5)
    v.v.update(canopy.v)
    return v


# ----------------------------------------------------------------------------- casa

def _house_back(v):
    """Rodada 05: fundos da casa. A janela pequena saía em degrau (o cisalhamento de 0,08 voxel/voxel
    deslocava linhas do recorte de forma desencontrada) e a parede era um plano liso. Redesenha a janela
    acompanhando o cisalhamento linha a linha e acrescenta o enxaimel e os detalhes da frente (prumos,
    mãos-francesas, venezianas, floreira), sem mudar volume nem silhueta da casa."""
    x0, x1, z0, wy0, wy1 = -18, 17, -14, 3, 16
    sh = lambda y: int(round(max(0, y - 3) * 0.08))
    def put(x, y, z, c):
        v.set(x + sh(y), y, z, c)
    for y in range(6, 15):                                    # limpa a área da janela (estuque)
        for x in range(-7, 8):
            put(x, y, z0, EB.PLASTER)
    for y in range(7, 15):                                    # janela de dois vidros, moldura e travessa
        for x in range(-5, 6):
            edge = x in (-5, 5) or y in (7, 14)
            put(x, y, z0, EB.TIMBER if edge or x == 0 or y == 10 else (EB.GLOW_HI if 9 <= y <= 12 and abs(x) in (2, 3) else EB.GLOW))
    for sx in (-8, 6):                                        # venezianas
        for y in range(7, 15):
            for dx in (0, 1):
                put(sx + dx, y, z0 - 1, EB.TEAL_D if y == 10 else EB.TEAL)
    for x in range(-6, 7):                                    # floreira
        for y in (5, 6):
            put(x, y, z0 - 1, EB.WOOD_D)
            put(x, y, z0 - 2, EB.WOOD_D)
        put(x, 7, z0 - 2, EB.LEAF_BUSH)
        if (x + 6) % 3 == 0:
            put(x, 8, z0 - 2, (EB.PINK, EB.YELLOW, EB.CORAL)[((x + 6) // 3) % 3])
    for px in (-11, 10):                                      # prumos intermediários
        for y in range(wy0, wy1 + 1):
            put(px, y, z0, EB.TIMBER)
    for i in range(5):                                        # mãos-francesas curvas (espelho da frente)
        put(x0 + 3 + i, wy1 - 1 - (i * i) // 4, z0, EB.TIMBER)
        put(x1 - 3 - i, wy1 - 1 - (i * i) // 4, z0, EB.TIMBER)


def _fence():
    """Cerca da B com as peças soltas ligadas (rodada 05): o trecho selado da régua de baixo (x 2..4)
    descia um voxel e ficava desconectado; a folha sobre ele e a flor flutuavam."""
    v = EB.fence()
    for x in (1, 5):
        v.set(x, 1, 0, EB.WOOD)          # emenda da régua selada com os trechos vizinhos
    v.set(2, 2, 0, EB.LEAF_BUSH)         # talo da folha até a régua
    v.set(-4, 6, 1, EB.LEAF_BUSH)        # sépala sob a flor
    return v


def house():
    """Casa de referência: a da B com acabamento revisto (fiadas limpas, escada, alpendre)."""
    with palette():
        v = EB.house()
        _house_back(v)
        # rodada 05: a trepadeira da B rareia no topo e deixa uma folha solta, flutuando, sem vizinho
        nb6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        loose = [k for k in v.v if not any((k[0] + a, k[1] + b, k[2] + c) in v.v for a, b, c in nb6)]
        for k in loose:
            del v.v[k]
        if loose:
            print(f"  house: {len(loose)} voxel(s) solto(s) removido(s) {loose}")
    roof_set = {E_PALETTE["ROOF"], E_PALETTE["ROOF_D"]}
    # fiadas de telha: linha escura a cada 3 voxels + luz/sombra só pela normal (sem manchas)
    roof = Vox()
    for k, c in v.v.items():
        if c in roof_set:
            roof.v[k] = c
    shade(roof, ctx=v)
    v.v.update(roof.v)
    # porta: a soleira em x (cisalhada) fica em z = 14; escada de pedra de três degraus + alpendre
    z1 = 13
    sx = int(round(max(0, 3 - 3) * 0.08))
    dx0, dx1 = -11 + sx, -4 + sx
    for k in [k for k in v.v if k[1] <= 2 and z1 + 2 <= k[2] <= z1 + 7 and dx0 - 3 <= k[0] <= dx1 + 3]:
        del v.v[k]
    for i, (y, z) in enumerate(((2, z1 + 2), (1, z1 + 4), (0, z1 + 6))):
        for yy in range(-3, y + 1):
            v.box(dx0 - 2 + i, yy, z, dx1 + 2 - i, yy, z + 1, EB.STONE_L if yy == y else EB.STONE)
    ax = (dx0 + dx1) // 2
    roof_c = E_PALETTE["ROOF"]
    for x in range(dx0 - 3, dx1 + 4):                      # alpendre (beiral sobre a porta)
        for z in range(z1 + 1, z1 + 5):
            y = 15 - (z - z1 - 1) // 2
            v.set(x, y, z, roof_c if (z - z1) % 2 else E_PALETTE["ROOF_D"])
    for x in (dx0 - 3, dx1 + 3):                           # mãos-francesas do alpendre
        v.set(x, 13, z1 + 1, EB.TIMBER)
        v.set(x, 14, z1 + 2, EB.TIMBER)
    return v


# ----------------------------------------------------------------------------- plantas pequenas (1/24)

def flower(petal, center=EB.YELLOW, seed=1, n=2):
    """Touceira de 2 flores com corola em cruz: lê de cima e de lado, sem disco gordo."""
    v = Vox()
    stem = P("#79a24c", "foliage")
    pl = light(petal, 1.2)
    for i in range(n):
        x = int(hash3(i, 0, 0, seed) * 7) - 3
        z = int(hash3(i, 1, 0, seed) * 7) - 3
        h = 4 + int(hash3(i, 2, 0, seed) * 4)
        v.box(x, 0, z, x, h - 1, z, stem)
        v.set(x + (1 if i % 2 else -1), max(1, h // 2), z, stem)
        v.set(x + (1 if i % 2 else -1), max(1, h // 2) + 1, z, light(stem))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v.set(x + dx, h, z + dz, petal)
        v.set(x, h, z, center)
        v.set(x, h + 1, z, pl)
    return v


def grass(tall=False, seed=1):
    with palette():
        return EB.grass(tall, seed)


def build_studies(out):
    """Estudos da rodada 03 como rigs estáticos (para o modo LINHA): assets/studies/<nome>."""
    from voxlib import Rig
    for kind in ("o1", "o2", "o3"):
        r = Rig(f"Carvalho {kind.upper()}", BIG, "static")
        r.part("body", oak_study(kind), (0, 0, 0))
        r.extra["affineWind"] = True
        r.save(f"{out}/studies/oak_{kind}")


# ----------------------------------------------------------------------------- salgueiro (rodada 06)

LEAF_WILLOW = P("#9cbf55", "foliage")      # verde-amarelado: separa do carvalho (#76ad47) sem sair da paleta
LEAF_WILLOW_TIP = P("#c6d873", "foliage")


def tree_willow(seed=6, inner=7, depth=1.3):
    """Salgueiro da E (rodada 06): tronco baixo, grosso e inclinado que abre em quatro galhos arqueados;
    copa em GUARDA-CHUVA achatado de onde descem CORTINAS de folhagem pendente — fitas largas (~4–5 voxels)
    com VÃOS entre elas, comprimentos desiguais e pontas que abrem para fora e clareiam; uma roda interna de
    cortinas mais escuras dá profundidade pelos vãos. Silhueta de sino, oposta às massas arredondadas do
    carvalho O3. Mesma gramática da E: densidade 1/8, pintura por normal em 4 tons com manchas largas
    (canopy_paint), avesso escuro, folhagem com vento afim. v1 (rejeitada): galhos furavam a copa (pintas
    marrons), bola solta no topo e cortinas coladas como saia de abajur."""
    v = Vox()
    EB.trunk_b(v, 15, 3.6, 2.3, path=lambda t: (-1.6 * math.sin(t * 2.0), 0.9 * t), flare=2.0, roots=6, seed=seed)
    for a, b, r in (((-1, 12, 0), (-6, 20, 2), 1.4), ((-1, 13, 0), (5, 21, -3), 1.4),
                    ((-1, 13, 1), (1, 21, 6), 1.2), ((-2, 12, 0), (-3, 20, -6), 1.2)):
        EB.branch(v, a, b, r)
    shade(v)

    leaf = Vox()
    sblob(leaf, -0.5, 23.6, 0.0, 13.0, 5.6, 12.0, LEAF_WILLOW, p=2.05)         # guarda-chuva em cúpula (v2 lia como caixa)
    sblob(leaf, 3.0, 27.0, -2.5, 7.0, 3.2, 6.0, LEAF_WILLOW, p=2.1)            # topo em ondas, sem bola solta
    sblob(leaf, -5.0, 26.4, 3.0, 6.0, 3.0, 5.0, LEAF_WILLOW, p=2.1)
    for k in [k for k in leaf.v if k[1] < 21]:
        del leaf.v[k]
    curtains = Vox()

    def curtain(a, R, top, bot, w0, col, i):
        ln = top - bot
        tx, tz = -math.sin(a), math.cos(a)
        for y in range(bot, top + 1):
            u = (top - y) / ln                                                  # 0 em cima, 1 na ponta
            rr = R - 1.2 + 3.4 * u ** 1.3 + 0.5 * math.sin(y * 0.5 + i)        # nasce sob a cúpula e abre embaixo (sino)
            cx, cz = math.cos(a) * rr - 0.5, math.sin(a) * rr
            w = w0 if u < 0.8 else w0 - (u - 0.8) / 0.2 * (w0 - 0.8)            # ponta arredondada
            d = depth if u < 0.85 else min(depth, 0.9)
            for s_ in range(-4, 5):
                for q in range(-2, 3):
                    if (s_ / w) ** 2 + (q / d) ** 2 <= 1.0:
                        x = round(cx + tx * s_ + math.cos(a) * q * 0.9)
                        z = round(cz + tz * s_ + math.sin(a) * q * 0.9)
                        curtains.set(x, y, z, col)

    n = 11
    for i in range(n):                                                          # roda externa, com vãos
        a = (i + 0.3 * (hash3(i, 1, 2, seed) - 0.5)) * 2 * math.pi / n
        if i in (4, 9):
            continue                                                            # duas aberturas: o tronco aparece
        curtain(a, 11.4 + 1.0 * hash3(i, 2, 3, seed), 22, 2 + int(10 * hash3(i, 3, 4, seed)), 2.4, LEAF_WILLOW, i)
    for i in range(inner):                                                      # roda interna, mais curta
        a = (i + 0.5) * 2 * math.pi / max(1, inner)
        curtain(a, 7.2, 21, 9 + int(4 * hash3(i, 5, 6, seed)), 2.2, LEAF_WILLOW, i + 20)
    leaf.merge(curtains, overwrite=False)
    canopy_paint(leaf, LEAF_WILLOW, seed, clump=5, amp=0.08)
    for (x, y, z), c in list(leaf.v.items()):
        r2 = (x + 0.5) ** 2 + z * z
        if y < 21 and r2 < 9.0 ** 2:
            leaf.v[(x, y, z)] = shadow(LEAF_WILLOW, 1.2 if (x, y - 1, z) in leaf.v else 1.6)   # cortinas internas na sombra
        if (x, y - 1, z) not in leaf.v and y <= 10:
            leaf.v[(x, y, z)] = LEAF_WILLOW_TIP                                 # pontas claras
        elif (x, y - 1, z) not in leaf.v and y >= 21:
            leaf.v[(x, y, z)] = shadow(LEAF_WILLOW, 1.6)                        # avesso do guarda-chuva
    for k in [k for k in v.v if k in leaf.v]:                                   # folhagem tem prioridade: sem pintas de casca
        del v.v[k]
    v.merge(leaf)
    import charlib_e
    charlib_e.prune_specks(v, label="tree_willow")                              # ponta de cortina isolada pelo arredondamento
    return v

# ----------------------------------------------------------------------------- posto de trilha (rodada 06)

PARCH_E = P("#f3e6c8")
ROUTE = C("#c8473c")


def trail_post():
    """Posto de trilha (rodada 06): abrigo aberto de viajantes, menor que a casa e da MESMA cultura construtiva.
    Base de pedra assentada com degrau; quatro esteios de madeira escura levemente fora de prumo (0,06 voxel
    por voxel); mãos-francesas curvas como as da casa; telhado de duas águas CÔNCAVO em fiadas coral (linha
    escura a cada 3 voxels), beiral largo, cumeeira que cede no meio; oitões de estuque com enxaimel. Dentro:
    quadro de avisos e mapas (papéis presos, mapa com rota vermelha) e banco. Frente/lados: lanterna quente
    no beiral e placa-seta de trilha num esteio. Fundos: verso do quadro com contraventamento em X, barril
    de chuva e hera subindo num esteio — nenhuma face lisa. Densidade 1/8 u."""
    with palette():
        STONE, STONE_L, TIMBER, WOOD, WOOD_D, WOOD_L = EB.STONE, EB.STONE_L, EB.TIMBER, EB.WOOD, EB.WOOD_D, EB.WOOD_L
        ROOF, ROOF_D, PLASTER, GLOW, GLOW_HI, IRON = EB.ROOF, EB.ROOF_D, EB.PLASTER, EB.GLOW, EB.GLOW_HI, EB.IRON
        LEAF_B, CREAM = EB.LEAF_BUSH, EB.CREAM
        v = Vox()
        # base de pedra (borda irregular) + degrau na frente
        for x in range(-13, 13):
            for z in range(-9, 9):
                edge = (abs(x + 0.5) > 11.5) + (abs(z + 0.5) > 7.5)
                if edge == 2 or (edge and hash3(x, 0, z, 11) < 0.25):
                    continue
                for y in range(-4, 2):
                    v.set(x, y, z, STONE)
        for x in range(-5, 5):
            for y in range(-4, 1):
                v.set(x, y, 9, STONE)
                v.set(x, y, 10, STONE) if y <= -1 else None
        EB.stones(v, seed=5)
        for (x, y, z) in list(v.v):
            if y == 1 and (x, 2, z) not in v.v and hash3(x // 2, 1, z // 2, 3) < 0.10:
                v.v[(x, y, z)] = EB.MOSS
        # esteios
        H = 16
        for px in (-10, 9):
            for pz in (-7, 6):
                v.box(px, 2, pz, px + 1, H, pz + 1, TIMBER)
                v.box(px - 1, 2, pz - 1, px + 2, 2, pz + 2, STONE_L)            # sapata de pedra
        # frechais e travessas
        for z in (-7, 6):
            v.box(-11, H + 1, z, 11, H + 1, z + 1, TIMBER)
        for x in (-10, 9):
            v.box(x, H + 1, -8, x + 1, H + 1, 8, TIMBER)
        import charlib_e
        for (px, sx) in ((-9, 1), (9, -1)):                                     # mãos-francesas curvas, 6-conexas
            for z in (-7, -6, 6, 7):
                pts = [(px + sx * 1, H - 4, z), (px + sx * 2, H - 2, z), (px + sx * 4, H - 1, z), (px + sx * 6, H, z)]
                charlib_e.connect_path(v, pts, lambda i: TIMBER)
        # telhado côncavo de duas águas (cumeeira ao longo de x), cedendo 1 voxel no meio
        Z = 10.5
        for x in range(-14, 14):
            sag = 1 if abs(x + 0.5) < 6 else 0
            for z in range(-11, 11):
                t = abs(z + 0.5) / Z
                if t > 1:
                    continue
                y = H + 2 + round(6.5 * (1 - t) ** 1.12) - sag      # côncavo suave (v1 1,35 lia como pagode)
                col = ROOF_D if (round(abs(z + 0.5)) % 3 == 0) else ROOF
                v.set(x, y, z, col)
                v.set(x, y - 1, z, ROOF_D if abs(x + 0.5) > 12.5 else col)
        for x in range(-14, 14):                                                # cumeeira
            sag = 1 if abs(x + 0.5) < 6 else 0
            v.set(x, H + 9 - sag, 0, WOOD_D)
            v.set(x, H + 9 - sag, -1, WOOD_D)
        # oitões: estuque + enxaimel (frente e fundos do telhado, em x = ±11)
        for gx in (-11, 10):
            for z in range(-8, 8):
                ytop = H + 1 + round(6.5 * (1 - abs(z + 0.5) / Z) ** 1.12) - (0 if abs(gx) > 6 else 1)
                for y in range(H + 2, ytop + 1):
                    c = TIMBER if (abs(z + 0.5) < 1 or y == H + 2 or abs(abs(z + 0.5) - (y - H - 2) * 0.9) < 0.6) else PLASTER
                    v.set(gx, y, z, c)
        # quadro de avisos e mapas entre os esteios de trás (face para a frente)
        for x in range(-8, 8):
            for y in range(6, 15):
                frame = x in (-8, 7) or y in (6, 14)
                v.set(x, y, -6, TIMBER if frame else WOOD_L)
                v.set(x, y, -7, WOOD_D)                                         # verso
        papers = [(-7, 10, -4, 13, CREAM), (-3, 8, 2, 13, PARCH_E), (3, 11, 6, 13, CREAM), (3, 7, 6, 9, CREAM)]
        for (x0, y0, x1, y1, c) in papers:
            for x in range(x0, x1 + 1):
                for y in range(y0, y1 + 1):
                    v.set(x, y, -5, c)
            v.set(x0, y1, -4, ROUTE)                                            # tachinha
        for (x, y) in ((-2, 9), (-1, 10), (0, 10), (1, 11), (1, 12)):          # rota no mapa
            v.set(x, y, -5, ROUTE)
        v.set(-2, 12, -5, EB.LEAF_BUSH)
        v.set(-1, 12, -5, EB.LEAF_BUSH)
        for x in (-7, -6, -5):                                                  # linhas de texto nos avisos
            v.set(x, 12, -5, WOOD_D)
        for x in (4, 5):
            v.set(x, 12, -5, WOOD_D)
        # verso do quadro: contraventamento em X
        for i in range(9):
            v.set(-8 + round(i * 15 / 8), 6 + i, -8, WOOD_D)
            v.set(7 - round(i * 15 / 8), 6 + i, -8, WOOD_D)
        # banco sob o quadro
        v.box(-7, 5, -5, 6, 5, -3, WOOD)
        for bx in (-6, 5):
            v.box(bx, 2, -4, bx, 4, -4, WOOD_D)
        # lanterna no beiral da frente-direita
        v.box(10, H + 1, 9, 10, H + 1, 9, IRON)
        v.set(10, H, 9, IRON)
        v.box(9, H - 4, 8, 11, H - 1, 10, IRON)
        v.box(10, H - 3, 9, 10, H - 2, 9, GLOW_HI)
        for (x, z) in ((9, 9), (11, 9), (10, 8), (10, 10)):
            v.set(x, H - 3, z, GLOW)
            v.set(x, H - 2, z, GLOW)
        # placa-seta de trilha no esteio da frente-esquerda (aponta para a esquerda): tábua 3 × 8 + ponta
        for x in range(-18, -11):
            for y in (10, 11, 12):
                v.set(x, y, 7, WOOD_L)
        for (x, y) in ((-19, 10), (-19, 11), (-19, 12), (-20, 11), (-19, 9), (-19, 13)):
            v.set(x, y, 7, WOOD_L)
        for x in range(-17, -12):                                               # seta pintada
            v.set(x, 11, 8, WOOD_D)
        v.set(-16, 12, 8, WOOD_D)
        v.set(-16, 10, 8, WOOD_D)
        v.set(-11, 11, 7, IRON)                                                 # cravo no esteio
        # barril de chuva no canto de trás e hera no esteio de trás-esquerdo
        for y in range(2, 7):
            for x in range(8, 13):
                for z in range(-12, -7):
                    if (x - 10.0) ** 2 + (z + 9.5) ** 2 <= 5.2:
                        v.set(x, y, z, IRON if y in (3, 5) else WOOD)
        for y in range(3, 15):                                                  # hera: 2 de largura (o cisalhamento
            xo = -12 + (y // 4) % 2                                             # quebrava a de 1 voxel em pedaços)
            v.set(xo, y, -8, LEAF_B)
            v.set(xo + 1, y, -8, LEAF_B if hash3(y, 1, 2, 9) < 0.7 else EB.light(LEAF_B))
            if hash3(y, 2, 3, 9) < 0.4:
                v.set(xo, y, -9, LEAF_B)
    roof = Vox()
    for k, c in v.v.items():
        if c in (E_PALETTE["ROOF"], E_PALETTE["ROOF_D"]):
            roof.v[k] = c
    shade(roof, ctx=v)
    v.v.update(roof.v)
    # fora de prumo: cisalha em x conforme a altura (acima da base)
    out = Vox()
    for (x, y, z), c in v.v.items():
        out.set(x + (round((y - 2) * 0.06) if y > 2 else 0), y, z, c)
    return out


def build(out):
    build_studies(out)
    ps = PropSet(f"{out}/environment/e", BIG)
    leafy = {"affineWind": True}
    ps.add("tree_oak", tree_oak(), **leafy)
    ps.add("tree_oak2", tree_autumn(), **leafy)
    ps.add("tree_oak3", tree_oak_tall(), **leafy)
    ps.add("tree_oak4", tree_oak_young(), **leafy)
    ps.add("tree_big", tree_big(), **leafy)
    ps.add("tree_willow", tree_willow(), **leafy)            # rodada 06
    ps.add("trail_post", trail_post())                       # rodada 06
    ps.add("house", house(), **leafy)
    with palette():
        ps.add("tree_pine", EB.tree_pine(), **leafy)
        ps.add("tree_birch", EB.tree_birch(), **leafy)
        ps.add("sapling", EB.sapling(), **leafy)
        ps.add("bush", EB.bush(), **leafy)
        ps.add("bush_berry", EB.bush(True, 15), **leafy)
        ps.add("mushroom_big", EB.mushroom_big())
        ps.add("rock_s", EB.boulder([(0, 1.2, 0, 3.2, 2.6, 2.8), (2.4, 0.6, 1.6, 1.8, 1.5, 1.6)], seed=2, moss=0.4), **leafy)
        ps.add("rock_m", EB.boulder([(0, 3.0, 0, 5.6, 4.8, 4.8), (4.2, 1.4, 2.4, 3.0, 2.6, 2.8)], seed=5), **leafy)
        ps.add("rock_l", EB.boulder([(0, 6.5, 0, 9.5, 9.2, 8.5), (8, 3.0, 4.5, 5.0, 4.6, 4.5), (-6.5, 2.2, 5, 3.6, 3.4, 3.4)],
                                    seed=8, moss=0.6), **leafy)
        ps.add("well", EB.well(), **leafy)
        ps.add("fence", _fence())
        ps.add("shrine", EB.shrine(), **leafy)
        ps.add("log", EB.log(), **leafy)
        ps.add("stump", EB.stump(), **leafy)
        ps.add("dock", EB.dock())
        ps.add("lily", EB.lily(), **leafy)
        ps.add("cloud", EB.cloud(), 0.5)
        # rodada 10 — marco de luz do posto da trilha (missão "As Luzes do Posto"): os dois estados são props distintos
        ps.add("trail_beacon_off", trail_beacon(False), FINE, **leafy)
        ps.add("trail_beacon_on", trail_beacon(True), FINE, **leafy)
        for name, vox in (("fern", EB.fern()), ("mushroom_red", EB.mushrooms_red()), ("signpost", EB.signpost()),
                          ("lantern_post", EB.lantern_post()), ("crate", EB.crate()), ("barrel", EB.barrel()),
                          ("campfire", EB.campfire()), ("reeds", EB.reeds())):
            ps.add(name, vox, FINE, **leafy)
    for name, vox in (("grass_tuft", grass()), ("grass_tall", grass(True, 4)),
                      ("flower_red", flower(EB.CORAL, seed=1)), ("flower_yellow", flower(EB.YELLOW, C("#e2864a"), 2)),
                      ("flower_blue", flower(EB.BLUE, seed=3)), ("flower_white", flower(EB.CREAM, seed=4))):
        ps.add(name, vox, SMALL, **leafy)
    ps.save()
    print("  env_e: " + ", ".join(sorted(ps.entries)))


# rodada 10 — marco de luz do posto da trilha ------------------------------------------------------------------------
BEACON_GLASS_OFF = P("#6c7a80")        # vidro fosco, apagado (sem emissivo)
BEACON_SEED_OFF = P("#6f9a94")         # semente P1 sem brilho (lê como a mesma peça, apagada)
BEACON_SEED_ON = C("#7ff0dc", "emissive")   # semente P1 (assinatura-visual.md: verde-água, ≤ 3 voxels, 1 por objeto)


def trail_beacon(lit):
    """Marco do posto: estela de pedra assentada sobre um degrau, braço de ferro com uma lanterna de quatro vidros e, no
    topo da gaiola, UMA semente de luz P1. Apagado: vidro fosco e semente sem brilho (nada emissivo). Aceso: luz quente
    das lanternas da vila (GLOW/GLOW_HI) e a semente verde-água emissiva. A forma é a mesma nos dois estados: só a luz
    muda, para o antes/depois ser lido como "o mesmo marco, aceso"."""
    v = Vox()
    # degrau e base da estela (pedras assentadas, como os poços e muros da vila)
    EB.rbox(v, -4, 0, -4, 3, 1, 3, EB.STONE, r=0)
    EB.rbox(v, -3, 2, -3, 2, 3, 2, EB.STONE, r=0)
    # fuste que afina: 4×4 até a metade, 2×2 em cima, com faixas claras a cada 4 voxels
    for y in range(4, 22):
        a = -2 if y < 13 else -1
        b = 1 if y < 13 else 0
        v.box(a, y, a, b, y, b, EB.STONE)
    EB.stones(v, seed=11, cell=(2, 2, 2))
    for y in (8, 12, 16, 20):
        a, b = (-2, 1) if y < 13 else (-1, 0)
        v.box(a, y, a, b, y, b, EB.STONE_L)
    # capitel e braço de ferro para a frente (+z: o lado que encara quem chega pela trilha)
    v.box(-2, 22, -2, 1, 22, 1, EB.STONE_D)
    v.box(-1, 23, -1, 0, 23, 6, EB.IRON)
    v.box(-1, 22, 6, 0, 22, 6, EB.IRON)
    # lanterna pendurada: tampa, quatro colunas de ferro, vidro, base e a semente P1 no topo da gaiola
    v.box(-3, 21, 4, 2, 21, 9, EB.TEAL_D)
    v.box(-2, 20, 5, 1, 20, 8, EB.TEAL)
    glass, core = (EB.GLOW, EB.GLOW_HI) if lit else (BEACON_GLASS_OFF, BEACON_GLASS_OFF)
    v.box(-2, 14, 5, 1, 19, 8, glass)
    v.box(-1, 15, 6, 0, 18, 7, core)
    for (x, z) in ((-2, 5), (1, 5), (-2, 8), (1, 8)):
        v.box(x, 14, z, x, 19, z, EB.IRON)
    v.box(-2, 13, 5, 1, 13, 8, EB.TEAL_D)
    v.set(-1, 12, 6, EB.GOLD)                 # pingente dourado embaixo, como nas lanternas da vila
    seed = BEACON_SEED_ON if lit else BEACON_SEED_OFF
    v.set(-1, 22, 8, seed)                     # cápsula de 2 voxels em pé sobre a tampa, na frente do gancho
    v.set(-1, 23, 8, seed)                     # (assinatura P1: ≤ 3 voxels)
    return v
