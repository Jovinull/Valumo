"""Laboratório de conteúdo do mundo (direção E) — biblioteca compartilhada.

Gramática da E para construções, árvores e pontos de interesse NOVOS, sem mudar nenhum asset aprovado:
paleta (a mesma de env_e/env_b), cisalhamento "fora de prumo", fundação de pedra assentada, paredes de estuque com
enxaimel, janelas e portas desenhadas no plano da parede ANTES do cisalhamento (acompanham o prumo, sem degrau),
telhados côncavos em fiadas, chaminé torta, lanterna, trepadeira e musgo.

Também mede o que o catálogo declara (dimensões, pegada no chão, tronco, custo) a partir dos VOXELS reais: nenhum
metadado de posição é número solto — ou é medido aqui, ou é um ponto autorado no espaço do modelo que os testes
(viewer/src/test/.../ConteudoMundoTest.java) conferem contra a geometria.

Convenções (contrato-de-assets.md §1): Y para cima, +Z = frente, modelo centrado em x = z = 0, y = 0 = chão (soleira).
"""
from __future__ import annotations

import math

import env_b as EB
import env_e as EE
from chars_b import P, light, normal, rbox, sblob, shade, shadow, stack  # noqa: F401  (reexportados)
from envlib import hash3
from voxlib import C, Vox, exposed_faces

BIG, FINE, SMALL = 1 / 8, 1 / 16, 1 / 24


def _c(name):
    """Cor da E: as trocas de env_e.E_PALETTE valem sobre as de env_b (casa e posto usam as mesmas)."""
    return EE.E_PALETTE.get(name, getattr(EB, name))


STONE, STONE_D, STONE_L = _c("STONE"), _c("STONE_D"), _c("STONE_L")
WOOD, WOOD_D, WOOD_L, TIMBER = _c("WOOD"), _c("WOOD_D"), _c("WOOD_L"), _c("TIMBER")
PLASTER, PLASTER_D = _c("PLASTER"), _c("PLASTER_D")
ROOF, ROOF_D = _c("ROOF"), _c("ROOF_D")
TEAL, TEAL_D, CREAM = _c("TEAL"), _c("TEAL_D"), _c("CREAM")
STRAW, STRAW_D = _c("STRAW"), _c("STRAW_D")
CORAL, PINK, YELLOW, BLUE = _c("CORAL"), _c("PINK"), _c("YELLOW"), _c("BLUE")
IRON, GLOW, GLOW_HI, FIRE, FIRE_R = _c("IRON"), _c("GLOW"), _c("GLOW_HI"), _c("FIRE"), _c("FIRE_R")
WATER = _c("WATER")
MOSS, LEAF_BUSH, LEAF = _c("MOSS"), _c("LEAF_BUSH"), _c("LEAF")
# Vegetação PRESA a estrutura rígida (musgo em telhado e pedra, hera de parede, folhas de floreira) é FOSCA, não
# folhagem: o vento afim da E desloca só o material folhagem, proporcional à altura acima da base do prop (shader
# scene.vert), e musgo de folhagem a 4 u desliza ~0,07 u sobre as telhas e abre fresta (a face do vizinho rígido foi
# descartada pelo mesher). Reproduzido no laboratório — inclusive na casa aprovada. Folhagem fica só no que balança de
# verdade e solto: copas, arbustos, ervas penduradas (testado: nenhum contato folhagem × rígido acima de 1,7 u).
MOSS_FIX = P("#92b54e")
IVY = P("#6a9f47")
BARK, BARK_D = _c("BARK"), _c("BARK_D")
GOLD = EB.GOLD

# Cores novas do laboratório (cada uma com papel; tabela em docs/art/conteudo-mundo.md §Paleta):
TERRACOTTA = P("#c8643c")              # vaso, telha de barro do forno — o terracota da faixa da exploradora
LEATHER, LEATHER_D = P("#9a6238"), P("#6e4130")   # couro esticado no varal do caçador (couro dos personagens)
MUSTARD, MUSTARD_D = P("#eeaa3a"), P("#c98f3a")   # telhado mostarda (variação permitida da gramática)
ROOF_TEAL, ROOF_TEAL_D = P("#3f8a8c"), P("#2f6a74")  # telhado teal (variação permitida)
SEED_P1 = C("#7ff0dc", "emissive")     # semente de luz P1 (assinatura aprovada: ≤ 3 voxels, 1 por objeto)

ROOFS = {                              # cor-base e linha da fiada; "coral" é a da casa de referência
    "coral": (ROOF, ROOF_D),
    "teal": (ROOF_TEAL, ROOF_TEAL_D),
    "mostarda": (MUSTARD, MUSTARD_D),
    "palha": (STRAW, STRAW_D),
}

NB6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


# ----------------------------------------------------------------------------- forma

def shear(v, k=0.08, y0=3):
    """Fora de prumo da E: desloca cada linha em +x por round((y − y0)·k). Desenhe tudo antes (janela, porta,
    enxaimel) e cisalhe no fim: as aberturas acompanham o prumo sem degrau (lição r6). Peças soltas com 1 voxel de
    largura em X se partem — faça-as com 2 (lição do posto de trilha)."""
    out = Vox()
    for (x, y, z), c in v.v.items():
        out.v[(x + (int(round((y - y0) * k)) if y > y0 else 0), y, z)] = c
    return out


def stone_block(x0, y0, z0, x1, y1, z1, seed=0, cell=(3, 2, 3), soft=True):
    """Volume de pedra assentada (3 tons em blocos largos), arestas amaciadas."""
    s = Vox()
    rbox(s, x0, y0, z0, x1, y1, z1, STONE, r=1 if soft else 0)
    EB.stones(s, seed=seed, cell=cell)
    return s


def foundation(v, x0, z0, x1, z1, top=2, depth=-6, seed=0):
    """Base de pedra enterrada (0,6–0,75 u abaixo do chão): absorve desnível do relevo sem mostrar o vão."""
    v.merge(stone_block(x0, depth, z0, x1, top, z1, seed=seed, soft=False))


class Face:
    """Plano de uma parede: axis 'z' = parede z = c (a corre em x); axis 'x' = parede x = c (a corre em z).
    out = +1/−1: sentido para fora. at(a, y, d) dá o voxel no plano deslocado d para fora."""

    def __init__(self, axis, c, out):
        self.axis, self.c, self.out = axis, c, out

    def at(self, a, y, d=0):
        if self.axis == "z":
            return (a, y, self.c + d * self.out)
        return (self.c + d * self.out, y, a)

    def put(self, v, a, y, col, d=0):
        v.set(*self.at(a, y, d), col)


def walls(v, x0, z0, x1, z1, y0, y1, col=None):
    """Caixa de paredes cheia (estuque), cantos amaciados (a coluna do canto sai, como na casa)."""
    col = col or PLASTER
    v.box(x0, y0, z0, x1, y1, z1, col)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        v.box(x, y0, z, x, y1, z, None)


def timber_face(v, f, a0, a1, y0, y1, posts=(), braces=True, plates=True):
    """Enxaimel numa face: prumos de canto (dois, deixando o canto amaciado), frechal e baldrame, prumos
    intermediários e mãos-francesas curvas junto aos cantos (a assinatura da casa)."""
    for a in (a0 + 1, a1 - 1):
        for y in range(y0, y1 + 1):
            f.put(v, a, y, TIMBER)
    for a in posts:
        for y in range(y0, y1 + 1):
            f.put(v, a, y, TIMBER)
    if plates:
        for a in range(a0 + 1, a1):
            f.put(v, a, y1, TIMBER)
            f.put(v, a, y0, TIMBER)
    if braces and a1 - a0 > 12:
        for i in range(5):
            f.put(v, a0 + 2 + i, y1 - 1 - (i * i) // 4, TIMBER)
            f.put(v, a1 - 2 - i, y1 - 1 - (i * i) // 4, TIMBER)


def face_line(v, f, a0, y0, a1, y1, col=None, d=0):
    """Traço contínuo no plano da face (escada 6-conexa): diagonal de enxaimel sem virar pontilhado."""
    col = col or TIMBER
    n = max(abs(a1 - a0), abs(y1 - y0), 1)
    pa, py = a0, y0
    f.put(v, pa, py, col, d=d)
    for i in range(1, n + 1):
        a = int(round(a0 + (a1 - a0) * i / n))
        y = int(round(y0 + (y1 - y0) * i / n))
        if a != pa and y != py:
            f.put(v, a, py, col, d=d)              # degrau: liga pela lateral, nunca só pela quina
        f.put(v, a, y, col, d=d)
        pa, py = a, y


def line6(v, a, b, col):
    """Traço 3D 6-conexo de a até b (diagonal real em escada, sem pontilhado e sem o 'L' do connect_path): galho
    fino, graveto, corda."""
    n = int(math.ceil(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]), 1)))
    prev = tuple(int(round(c)) for c in a)
    v.set(*prev, col)
    for i in range(1, n + 1):
        cur = tuple(int(round(a[k] + (b[k] - a[k]) * i / n)) for k in range(3))
        p = list(prev)
        for k in range(3):                     # anda um eixo por vez: cada passo encosta face com face
            if cur[k] != p[k]:
                p[k] = cur[k]
                v.set(*p, col)
        prev = cur


def collision_boxes(v, band=(3, 12), cell=2, skip=("foliage",)):
    """Formas de colisão MEDIDAS: colunas com voxel sólido na faixa de altura do corpo ACIMA do que se sobe a pé
    (y ≥ 3 = 0,375 u: piso, base e degraus até 0,30 u são pisáveis, como na conferência de alcance), numa grade de `cell` voxels
    (2 × 1/8 = 0,25 u, a grade da conferência de alcance da rodada 11), fundidas em retângulos gulosos. Devolve
    [(x0, z0, x1, z1)] em voxels (x1, z1 exclusivos). Serve às peças que uma forma só não descreve (anel, cantos)."""
    occ = set()
    for (x, y, z), c in v.v.items():
        if band[0] <= y <= band[1] and c[3] not in skip:
            occ.add((x // cell, z // cell))
    boxes = []
    left = set(occ)
    for (gx, gz) in sorted(occ, key=lambda k: (k[1], k[0])):
        if (gx, gz) not in left:
            continue
        w = 1
        while (gx + w, gz) in left:
            w += 1
        h = 1
        while all((gx + i, gz + h) in left for i in range(w)):
            h += 1
        for i in range(w):
            for j in range(h):
                left.discard((gx + i, gz + j))
        boxes.append((gx * cell, gz * cell, (gx + w) * cell, (gz + h) * cell))
    return boxes


def achas(v, x0, x1, y0, rows, z0, z1, d=4, gap=1):
    """Lenha rachada empilhada (voxel 1/16): achas de d×d com os cantos cortados (ponta arredondada), deitadas ao longo
    de z; na ponta, cerne claro no meio e anel de madeira; no comprido, casca escura embaixo e madeira em cima;
    fileiras desencontradas com VÃO entre as achas (a 1/8 e sem vão, a pilha lia como xadrez ou tijolo)."""
    step = d + gap
    xs_all = []
    for r in range(rows):                                       # miolo MAÇIÇO: vão só nas pontas, onde se vê (vão em
        y = y0 + r * d                                          # toda a profundidade expunha 4 faces compridas por acha)
        off = (r % 2) * (step // 2)
        xs = list(range(x0 + off, x1 - d + 2, step))
        if xs:
            xs_all.append((y, xs[0], xs[-1] + d - 1))
    for (y, xa, xb) in xs_all:
        for z in range(z0 + 1, z1):
            for x in range(xa, xb + 1):
                for dy in range(d):
                    v.set(x, y + dy, z, WOOD_D)
    for r in range(rows):
        y = y0 + r * d
        off = (r % 2) * (step // 2)
        for x in range(x0 + off, x1 - d + 2, step):
            for dx in range(d):
                for dy in range(d):
                    corner = dx in (0, d - 1) and dy in (0, d - 1)
                    if corner:
                        continue
                    core = 0 < dx < d - 1 and 0 < dy < d - 1
                    for z in range(z0, z1 + 1):
                        end = z in (z0, z1)
                        if end:
                            c = WOOD_L if core else WOOD
                        elif dy == d - 1 or dx in (0, d - 1):
                            c = WOOD_D if dy == 0 else (WOOD if dy < d - 1 else light(WOOD, 0.5))
                        else:
                            continue                            # miolo já preenchido
                        v.set(x + dx, y + dy, z, c)


def cross_brace(v, f, a0, a1, y0, y1):
    """Cruz de enxaimel (contraventamento em X) num painel, com traços contínuos."""
    face_line(v, f, a0, y0, a1, y1)
    face_line(v, f, a0, y1, a1, y0)


def window(v, f, a0, a1, y0, y1, shutters=True, box=True, mullion=True, flowers=(PINK, YELLOW, CORAL), frame=None):
    """Janela emissiva da E: moldura, pinázio e travessa, vidro quente com miolo claro, venezianas teal e floreira."""
    frame = frame or TIMBER
    for a in range(a0 - 1, a1 + 2):
        for y in range(y0 - 1, y1 + 2):
            f.put(v, a, y, frame)
    am, ym = (a0 + a1) // 2, (y0 + y1) // 2
    for a in range(a0, a1 + 1):
        for y in range(y0, y1 + 1):
            inner = a0 < a < a1 and y0 < y < y1
            f.put(v, a, y, GLOW_HI if inner and abs(a - am) <= 1 and abs(y - ym - 0.5) <= 1.5 else GLOW)
            if mullion and (a == am or y == ym):
                f.put(v, a, y, frame)
    if shutters:
        for (sa, sb) in ((a0 - 3, a0 - 2), (a1 + 2, a1 + 3)):
            for a in (sa, sb):
                for y in range(y0 - 1, y1 + 2):
                    f.put(v, a, y, TEAL_D if y == ym else TEAL, d=1)
    if box:
        for a in range(a0 - 1, a1 + 2):
            for y in (y0 - 3, y0 - 2):
                f.put(v, a, y, WOOD_D, d=1)
                f.put(v, a, y, WOOD_D, d=2)
            f.put(v, a, y0 - 1, IVY, d=2)
            if flowers and (a - a0 + 1) % 2 == 0:
                f.put(v, a, y0, flowers[((a - a0 + 1) // 2) % len(flowers)], d=2)


def round_window(v, f, ac, yc, r=3.2, frame=None):
    """Óculo redondo (empena), como o da casa."""
    frame = frame or TIMBER
    for a in range(int(ac - r) - 1, int(ac + r) + 2):
        for y in range(int(yc - r) - 1, int(yc + r) + 2):
            d = math.hypot(a - ac, y - yc)
            if d <= r:
                f.put(v, a, y, GLOW if d <= r - 1 else frame)
    for a in range(int(ac - r) + 1, int(ac + r)):
        f.put(v, a, int(round(yc)), frame)


def door(v, f, ac, y0, w=8, h=8, arch=4.2, col=None, knob=True, wicket=True, frame_col=None, frame=True):
    """Porta em arco da E (tábuas teal com junta escura a cada 3), moldura de pedra saliente, puxador dourado e
    postigo iluminado. ac = centro (pode ser x.5). w = largura em voxels (8 = 1 u), h = altura até o início do arco."""
    col = col or TEAL
    frame_col = frame_col or STONE_L
    half = w / 2.0
    for a in range(int(ac - half - 2), int(ac + half + 2)):
        u = a + 0.5 - ac
        for y in range(y0, y0 + h + int(arch) + 3):
            inside = abs(u) <= half and (y < y0 + h or (u / half) ** 2 + ((y - y0 - h) / arch) ** 2 <= 1.0)
            ring = abs(u) <= half + 1 and (y < y0 + h or (u / (half + 1)) ** 2 + ((y - y0 - h) / (arch + 1.2)) ** 2 <= 1.0)
            if inside:
                f.put(v, a, y, col if int(u + half) % 3 else TEAL_D if col == TEAL else shadow(col, 0.8))
            elif ring and frame:
                f.put(v, a, y, frame_col)
                f.put(v, a, y, frame_col if (a + y) % 3 else STONE, d=1)
    if knob:
        f.put(v, int(ac + half - 2), y0 + 5, GOLD, d=1)
    if wicket and w >= 6:
        for a in (int(ac) - 1, int(ac)):
            for y in (y0 + h, y0 + h + 1):
                f.put(v, a, y, GLOW_HI if a == int(ac) else GLOW)


def plank_door(v, f, a0, a1, y0, y1, col=None):
    """Porta de tábuas reta (fundos, depósito): tábuas verticais com travessa em Z."""
    col = col or WOOD
    for a in range(a0 - 1, a1 + 2):
        for y in range(y0, y1 + 2):
            f.put(v, a, y, TIMBER if (a in (a0 - 1, a1 + 1) or y == y1 + 1) else (col if (a - a0) % 2 else WOOD_D))
    n = a1 - a0
    for i in range(n + 1):
        f.put(v, a0 + i, y0 + 1 + int(round((y1 - y0 - 2) * i / max(1, n))), WOOD_D, d=1)
    f.put(v, a1 - 1, (y0 + y1) // 2, IRON, d=1)


def lantern(v, x, y, z, bracket=None):
    """Lanterna de parede da E (gaiola de ferro, luz quente). bracket = (dx, dz) do braço até a parede."""
    v.box(x - 1, y, z - 1, x + 1, y + 2, z + 1, GLOW)
    v.set(x, y + 1, z, GLOW_HI)
    v.box(x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, IRON)
    v.box(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, IRON)
    v.set(x, y + 4, z, IRON)
    if bracket:
        dx, dz = bracket
        n = max(abs(dx), abs(dz))
        for i in range(1, n + 1):
            v.set(x + round(dx * i / n), y + 5, z + round(dz * i / n), TIMBER)
        v.set(x, y + 5, z, TIMBER)


def chimney(v, x, z, y0, y1, lean=0.16, w=2, cap=None, seed=4, shoulder=None):
    """Chaminé torta de pedra com capelo coral (a da casa), cisalhada à parte (ela pende para +x). shoulder = altura
    até onde o fuste tem 1 voxel a mais de cada lado (chaminé externa: base larga e ombro, depois esbelta)."""
    cap = cap or CORAL
    ch = Vox()
    for y in range(y0, y1 + 1):
        dx = int(round((y - y0) * lean))
        ww = w if y < y1 - 2 else w + 1
        if shoulder is not None and y <= shoulder:
            ww = w + 1
        ch.box(x + dx - ww, y, z - ww, x + dx + ww, y, z + ww, STONE)
    for (cx, cy, cz) in list(ch.v):
        if cy >= y1 - 2:
            ch.v[(cx, cy, cz)] = STONE_D if cy == y1 - 2 else STONE_L
    EB.stones(ch, seed=seed, cell=(2, 2, 2))
    dx = int(round((y1 - y0) * lean))
    ch.box(x + dx - 1, y1 + 1, z - 1, x + dx + 1, y1 + 2, z + 1, cap)
    ch.set(x + dx, y1 + 2, z, IRON)
    v.merge(ch)


def vine(v, f, a, y0, y1, width=2, seed=6, flowers=True):
    """Trepadeira de 2 de largura (o cisalhamento parte a de 1), subindo em zigue-zague largo, com flores raras."""
    for y in range(y0, y1 + 1):
        ao = a + (y // 5) % 2
        for k in range(width):
            if hash3(y, k, a, seed) < 0.85:
                f.put(v, ao + k, y, IVY if hash3(y, k, 1, seed) < 0.65 else light(IVY), d=1)
        if flowers and hash3(y, 3, a, seed) < 0.10:
            f.put(v, ao, y, PINK, d=2)


def moss_patches(v, cols, amount=0.25, max_y=None, seed=21):
    """Musgo em manchas de 2×2 só no topo exposto das cores dadas (beiral, muro): nunca pontilhado."""
    for (x, y, z), c in list(v.v.items()):
        if c in cols and (x, y + 1, z) not in v.v and (max_y is None or y <= max_y):
            if hash3(x // 2, 0, z // 2, seed) < amount:
                v.v[(x, y, z)] = MOSS_FIX if hash3(x // 2, y, z // 2, seed + 1) < 0.7 else light(MOSS_FIX)


# ----------------------------------------------------------------------------- telhados

def roof_field(v, xs, zs, hfun, base, line, edge=None, thick=2, ridge=None, course=3):
    """Telhado como campo de altura: para cada coluna (x, z) preenche do menor vizinho − 1 até h, sem buracos nas águas
    íngremes. Fiadas: linha escura a cada 3 voxels de altura (y % 3 == 0), como a casa; luz e sombra vêm só da normal
    (shade). edge(x, z) -> True pinta a coluna de TIMBER (testeira). Devolve o Vox do telhado (já mesclado em v)."""
    r = Vox()
    hs = {}
    for x in xs:
        for z in zs:
            h = hfun(x, z)
            if h is not None:
                hs[(x, z)] = h
    for (x, z), h in hs.items():
        lo = min([h] + [hs[k] for k in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)) if k in hs]) - (thick - 1)
        for y in range(lo, h + 1):
            c = line if y % course == 0 else base
            if edge and edge(x, z):
                c = TIMBER
            r.set(x, y, z, c)
    if ridge:
        ridge(r, hs)
    shade(r, ctx=r)
    v.merge(r)
    return r, hs


def concave(t, curve):
    """Perfil côncavo de conto: 0 no beiral, 1 na cumeeira; curve > 0 deixa a água mais deitada embaixo."""
    return max(0.0, t) ** (1 + curve)


def gable_roof(v, x0, x1, z0, z1, y0, rise, ov=3, curve=0.5, sag=1.6, roof="coral", axis="x", edge=True):
    """Duas águas côncavas, cumeeira ao longo de `axis`, beiral largo `ov`, cumeeira que cede no meio e pontas que
    sobem (`sag`, em voxels). x0..x1, z0..z1 = paredes (o beiral passa delas)."""
    base, line = ROOFS[roof]
    ax0, ax1 = (x0 - ov, x1 + ov) if axis == "x" else (z0 - ov, z1 + ov)
    sp0, sp1 = (z0 - ov, z1 + ov) if axis == "x" else (x0 - ov, x1 + ov)
    mid = (sp0 + sp1) / 2.0
    half = (sp1 - sp0) / 2.0 + 0.5

    def h(x, z):
        a, s = (x, z) if axis == "x" else (z, x)
        u = (a - ax0) / max(1.0, ax1 - ax0)
        t = 1 - abs(s + 0.5 - mid - 0.5) / half
        dip = -sag * math.sin(math.pi * u) + sag * 0.8 * abs(2 * u - 1) ** 3
        return y0 + int(round(rise * concave(t, curve) + dip * (0.35 + 0.65 * t)))

    def is_edge(x, z):
        a = x if axis == "x" else z
        return edge and a in (ax0, ax1)

    def ridge(r, hs):
        for a in range(ax0, ax1 + 1):
            sm = int(math.floor(mid))
            for s in (sm, sm + 1):
                k = (a, s) if axis == "x" else (s, a)
                if k in hs:
                    pass
            ks = [((a, s) if axis == "x" else (s, a)) for s in (sm, sm + 1)]
            top = max(hs[k] for k in ks if k in hs)
            for k in ks:
                r.set(k[0], top + 1, k[1], line if a % 4 else TIMBER)

    xs = range(ax0, ax1 + 1) if axis == "x" else range(sp0, sp1 + 1)
    zs = range(sp0, sp1 + 1) if axis == "x" else range(ax0, ax1 + 1)
    return roof_field(v, xs, zs, h, base, line, edge=is_edge, ridge=ridge)


def gable_wall(v, f, a0, a1, y0, hs_top, posts=True, col=None):
    """Empena: fecha o triângulo sob o telhado no plano f (estuque com prumo central e travessa, como a casa).
    hs_top(a) = altura do telhado naquela posição; a parede vai até 2 voxels abaixo dele."""
    col = col or PLASTER
    am = (a0 + a1) / 2.0
    for a in range(a0, a1 + 1):
        top = hs_top(a) - 2
        for y in range(y0, top + 1):
            beam = posts and (abs(a + 0.5 - am - 0.5) < 1 or y == y0 + 5 or a in (a0, a1))
            f.put(v, a, y, TIMBER if beam else col)


def cone_roof(v, cx, cz, y0, R, rise, curve=0.55, roof="palha", seed=3, ragged=True, course=4):
    """Cone côncavo de palha (cabana redonda). Borda do beiral em ondas largas (±1 voxel em blocos de 3), nunca
    ruído de 1 voxel. Fiadas a cada `course` voxels (palha: 4, mais espaçada que a telha) e beiral com aba pendente."""
    base, line = ROOFS[roof]

    def h(x, z):
        d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
        rr = R + ((hash3(int(math.atan2(z + 0.5 - cz, x + 0.5 - cx) * 6), 0, 0, seed) - 0.5) * 2.0 if ragged else 0)
        if d > rr:
            return None
        return y0 + int(round(rise * concave(1 - d / R, curve)))

    rng = range(int(cx - R) - 3, int(cx + R) + 4)
    r, hs = roof_field(v, rng, range(int(cz - R) - 3, int(cz + R) + 4), h, base, line, course=course)
    for (x, z), hh in hs.items():                         # aba de palha pendente na borda (2 voxels)
        if any(k not in hs for k in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))):
            r.set(x, hh - 2, z, shadow(base, 0.8))
            v.set(x, hh - 2, z, shadow(base, 0.8))
    return r, hs


def hip_roof(v, x0, x1, z0, z1, y0, rise, ov=3, curve=0.45, roof="coral"):
    """Quatro águas côncavas (pirâmide alongada): para a torre e para casas de planta quadrada."""
    base, line = ROOFS[roof]
    X0, X1, Z0, Z1 = x0 - ov, x1 + ov, z0 - ov, z1 + ov
    hx, hz = (X1 - X0) / 2.0 + 0.5, (Z1 - Z0) / 2.0 + 0.5
    cx, cz = (X0 + X1) / 2.0, (Z0 + Z1) / 2.0
    m = min(hx, hz)

    def h(x, z):
        tx = 1 - (abs(x - cx) - (hx - m)) / m if abs(x - cx) > hx - m else 1.0
        tz = 1 - (abs(z - cz) - (hz - m)) / m if abs(z - cz) > hz - m else 1.0
        return y0 + int(round(rise * concave(min(tx, tz), curve)))

    return roof_field(v, range(X0, X1 + 1), range(Z0, Z1 + 1), h, base, line)


# ----------------------------------------------------------------------------- medição (metadados reais)

def measure(v, vs, trunk_band=None, ground_band=(0, 3)):
    """Mede o modelo como o catálogo declara: caixa (u), pegada no chão (colunas com voxel na faixa ground_band),
    área (u²), voxels, faces expostas, emissivo (%), e — para árvores — o tronco (centro e raio a partir das colunas
    de casca na faixa trunk_band)."""
    x0, y0, z0, x1, y1, z1 = v.bounds()
    foot = {(x, z) for (x, y, z) in v.v if ground_band[0] <= y <= ground_band[1]}
    fx = [k[0] for k in foot] or [0]
    fz = [k[1] for k in foot] or [0]
    faces = exposed_faces(v)
    emis = sum(1 for (x, y, z), c in v.v.items() if c[3] == "emissive" for d in NB6
               if (x + d[0], y + d[1], z + d[2]) not in v.v)
    out = {
        "voxel": vs,
        "dimensoes_u": [round((x1 - x0 + 1) * vs, 3), round((y1 - y0 + 1) * vs, 3), round((z1 - z0 + 1) * vs, 3)],
        "caixa_u": {"min": [round(x0 * vs, 3), round(y0 * vs, 3), round(z0 * vs, 3)],
                    "max": [round((x1 + 1) * vs, 3), round((y1 + 1) * vs, 3), round((z1 + 1) * vs, 3)]},
        "pegada_u": {"min": [round(min(fx) * vs, 3), round(min(fz) * vs, 3)],
                     "max": [round((max(fx) + 1) * vs, 3), round((max(fz) + 1) * vs, 3)],
                     "area_u2": round(len(foot) * vs * vs, 3)},
        "voxels": len(v.v),
        "faces_expostas": faces,
        "emissivo_pct": round(100.0 * emis / max(1, faces), 2),
    }
    if trunk_band:
        cells = {(x, z) for (x, y, z), c in v.v.items()
                 if trunk_band[0] <= y <= trunk_band[1] and c[3] != "foliage"}
        if cells:
            cx = sum(k[0] + 0.5 for k in cells) / len(cells)
            cz = sum(k[1] + 0.5 for k in cells) / len(cells)
            r = max(math.hypot(k[0] + 0.5 - cx, k[1] + 0.5 - cz) for k in cells) + 0.5
            out["tronco_u"] = {"centro": [round(cx * vs, 3), round(cz * vs, 3)], "raio": round(r * vs, 3),
                               "faixa_y_u": [round(trunk_band[0] * vs, 3), round((trunk_band[1] + 1) * vs, 3)]}
    return out


def u(p, vs):
    """Ponto autorado em voxels (centro do voxel) → u, arredondado."""
    return [round((c + 0.5) * vs, 3) for c in p]


# ----------------------------------------------------------------------------- registro

ASSETS = {}


def asset(name, **meta):
    """Registra um asset do laboratório. O construtor devolve (Vox, pontos), pontos em VOXELS do espaço do modelo:
    entradas [(id, (x, y, z), rumo_graus)], interacao [(id, (x, y, z), tipo)], spawns [(id, (x, y, z), tipo)],
    observacao [(id, (x, y, z), rumo)], conexoes [(id, (x, y, z), rumo)], area_segura [(x0, z0, x1, z1)] — conferidos
    pelos testes contra a geometria. `variacoes` = parâmetros seguros e seus valores; o construtor os recebe por nome."""
    def deco(fn):
        if name in ASSETS:
            raise ValueError(f"id repetido: {name}")
        meta.setdefault("versao", 1)
        meta.setdefault("variacoes", {})
        # aprovação ARTÍSTICA × liberação para INTEGRAÇÃO. Os lotes 1 e 2 foram avaliados pelo proprietário (2026-11-19):
        # o padrão deles é aprovado/liberado e as exceções vêm no registro. Lote 3 em diante nasce candidato.
        evaluated = meta.get("lote") in (1, 2)
        meta.setdefault("aprovacao", "aprovado" if evaluated else "candidato")
        # aprovado | aprovado_conceito | candidato | rejeitado
        meta.setdefault("integracao", "liberado" if evaluated else "aguardando_avaliacao")
        # liberado | depende_colisao_composta | aguardando_refinamento | aguardando_avaliacao | restrito_a_conjuntos |
        # nao_distribuivel
        meta.setdefault("dependencias", [])
        meta.setdefault("relacoes", [])                   # [(tipo, alvo: id de asset ou "tag:<nome>", [dist_min, dist_max] u)]
        ASSETS[name] = (fn, meta)
        return fn
    return deco


# ----------------------------------------------------------------------------- geometria de posicionamento (u)

def rot_u(x, z, deg):
    """O giro do viewer (Matrix4f.rotateY, graus): +Z do modelo vai para +X com 90°."""
    a = math.radians(deg)
    return x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)


def rect_world(pegada, x, z, r, grow=(0.0, 0.0, 0.0, 0.0)):
    """Cantos (mundo) da pegada {"min": [x0, z0], "max": [x1, z1]} crescida por (lados_esq, lados_dir, fundos, frente),
    girada por r e transladada para (x, z)."""
    (x0, z0), (x1, z1) = pegada["min"], pegada["max"]
    x0, x1, z0, z1 = x0 - grow[0], x1 + grow[1], z0 - grow[2], z1 + grow[3]
    return [tuple(a + b for a, b in zip(rot_u(cx, cz, r), (x, z))) for (cx, cz) in ((x0, z0), (x1, z0), (x1, z1), (x0, z1))]


def rects_overlap(p, q):
    """Separação de eixos entre dois retângulos girados (encostar não conta)."""
    for poly in (p, q):
        for i in range(4):
            ax, az = poly[(i + 1) % 4][1] - poly[i][1], -(poly[(i + 1) % 4][0] - poly[i][0])
            pa = [ax * x + az * z for (x, z) in p]
            qa = [ax * x + az * z for (x, z) in q]
            if max(pa) <= min(qa) + 1e-6 or max(qa) <= min(pa) + 1e-6:
                return False
    return True
