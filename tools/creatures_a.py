"""Direção A — criaturas originais (voxel clássico, formas cúbicas chanfradas, cores vivas).

  pompom  — pequeno animal selvagem: bola de pelo saltadora com orelhas longas e broto de folha.
  lumi    — criatura fantástica amigável: espírito-lanterna que flutua, com fagulhas em órbita.
  fungo   — inimigo simples: cogumelo-espinheiro carrancudo.
  tortuga — criatura grande e mansa: tartaruga-colina com um jardim no casco (referência de escala).
"""
import math

from envlib import hash3, lobe, ramp, toplight
from voxlib import C, Rig, Vox, tone

VS = 1 / 24
EYE = C("#2b2238")
WHITE = C("#ffffff")


def pompom():
    fur, belly, fur_d = C("#f7d9a8"), C("#fff3da"), C("#e2b77e")
    ear_in = C("#f59aa6")
    body = Vox()
    lobe(body, 0, 4.5, 0, 5.5, 4.6, 5.5, fur, p=3.2)
    body.paint(lambda x, y, z, c: belly if z >= 2 and y <= 4 and abs(x + 0.5) < 3.5 else None)
    body.paint(lambda x, y, z, c: fur_d if c == fur and y <= 1 else None)
    f = max(z for (x, y, z) in body.v)          # plano frontal
    for ex in (-3, 2):
        body.box(ex, 5, f, ex, 6, f, EYE)
        body.set(ex, 6, f + 0, EYE)
    body.set(-3, 7, f, None)
    body.set(-1, 4, f, C("#e9788a"))             # focinho
    body.set(0, 4, f, C("#e9788a"))
    body.set(-4, 4, f - 1, C("#f7b3a0"))
    body.set(3, 4, f - 1, C("#f7b3a0"))
    # olhos precisam de fundo: garante pelo atrás deles
    for ex in (-3, 2):
        for y in (5, 6):
            body.set(ex, y, f - 1, fur)
    # broto de folha na testa
    leaf = C("#5fc044", "foliage")
    body.box(0, 9, 1, 0, 10, 1, C("#3f9a3a", "foliage"))
    body.box(1, 11, 1, 2, 11, 1, leaf)
    body.set(-1, 10, 1, leaf)
    # patinhas
    for x in (-3, 2):
        body.box(x, 0, 2, x + 1, 0, 4, fur_d)
        body.box(x, 0, -3, x + 1, 0, -2, fur_d)

    def ear(sign):
        e = Vox()
        x = 2 if sign > 0 else -4
        e.box(x, 8, -1, x + 1, 14, -1, fur)
        e.box(x, 9, 0, x + 1, 13, 0, ear_in)
        e.box(x, 8, 0, x + 1, 8, 0, fur)
        e.set(x if sign > 0 else x + 1, 14, -1, None)
        e.box(x, 15, -1, x + (0 if sign < 0 else 0), 15, -1, fur_d)
        return e

    tail = Vox()
    lobe(tail, 0, 3.5, -6.5, 2, 2, 2, WHITE, p=3)
    rig = Rig("Pompom (A)", VS, "hopper", meta={"role": "pequeno animal selvagem"})
    rig.part("body", body, (0, 0, 0))
    rig.part("ear_l", ear(1), (3, 8.5, -0.5), "body")
    rig.part("ear_r", ear(-1), (-3, 8.5, -0.5), "body")
    rig.part("tail", tail, (0, 3.5, -5), "body")
    return rig


def lumi():
    glow, glow_l = C("#ffd95a", "emissive"), C("#fff6c8", "emissive")
    spark = C("#7fe8ff", "emissive")
    cap, cap_d = C("#8a5530"), C("#5f381d")
    core = Vox()
    lobe(core, 0, 6, 0, 4.2, 4.6, 4.2, glow, p=3.0)
    core.paint(lambda x, y, z, c: glow_l if y >= 8 or (z >= 3 and y >= 6) else None)
    f = max(z for (x, y, z) in core.v)
    for ex in (-2, 1):
        core.box(ex, 5, f, ex, 6, f, EYE)
    core.set(-1, 4, f, C("#e07a3a", "emissive"))
    core.set(0, 4, f, C("#e07a3a", "emissive"))
    # chapeuzinho de bolota com argola
    core.box(-3, 10, -3, 2, 10, 2, cap)
    core.box(-2, 11, -2, 1, 11, 1, cap_d)
    core.box(-1, 12, -1, 0, 13, 0, cap)
    core.set(-1, 14, 0, C("#f8c93a", "metal"))
    core.set(0, 14, 0, C("#f8c93a", "metal"))
    for (x, z) in ((-3, -3), (2, -3), (-3, 2), (2, 2)):
        core.set(x, 10, z, None)

    ring = Vox()
    for i in range(4):
        a = i * math.pi / 2 + 0.4
        x, z = round(math.cos(a) * 7 - 0.5), round(math.sin(a) * 7 - 0.5)
        ring.set(x, 6 + (i % 2) * 2, z, spark)
        ring.set(x, 7 + (i % 2) * 2, z, C("#e6fcff", "emissive") if i % 2 else spark)

    tail = Vox()   # chama pendente
    tail.box(-1, 0, -1, 0, 1, 0, glow)
    tail.set(-1, -1, -1, C("#ffb23a", "emissive"))
    tail.set(0, -2, -1, C("#ff8a2a", "emissive"))

    def leaf(sign):
        l = Vox()
        x = 4 if sign > 0 else -6
        g = C("#74cc60", "foliage")
        l.box(x, 6, -1, x + 1, 7, 0, g)
        l.set(x + (1 if sign > 0 else 0), 8, -1, C("#a6e27a", "foliage"))
        return l

    rig = Rig("Lumi (A)", VS, "floater", meta={"role": "criatura fantastica amigavel"})
    rig.part("core", core, (0, 6, 0))
    rig.part("ring", ring, (0, 6, 0), "core")
    rig.part("tail", tail, (0, 1.5, 0), "core")
    rig.part("leaf_l", leaf(1), (4, 6.5, 0), "core")
    rig.part("leaf_r", leaf(-1), (-4, 6.5, 0), "core")
    return rig


def fungo():
    stem, stem_d = C("#ece3b4"), C("#c4b77c")
    cap, cap_d, cap_l = C("#6a2f6e"), C("#4a1f52"), C("#93489a")
    spot = C("#ff9a3a", "emissive")
    body = Vox()
    lobe(body, 0, 4.5, 0, 4.6, 5.2, 4.2, stem, p=4.0)
    body.paint(lambda x, y, z, c: stem_d if y <= 1 or z <= -3 else None)
    f = max(z for (x, y, z) in body.v)
    for ex in (-3, 1):                                   # olhos bravos: brasa amarela sob sobrancelha inclinada
        body.box(ex, 4, f, ex + 1, 5, f, C("#ffd95a", "emissive"))
        body.set(ex + (1 if ex < 0 else 0), 4, f, EYE)
    for (x, y) in ((-4, 7), (-3, 7), (-2, 6), (-1, 6), (0, 6), (1, 6), (2, 7), (3, 7)):
        body.set(x, y, f, cap_d)
    body.box(-2, 2, f, 1, 2, f, C("#3a1f2e"))             # boca carrancuda com presas
    body.set(-3, 3, f, C("#3a1f2e"))
    body.set(2, 3, f, C("#3a1f2e"))
    body.set(-2, 2, f, WHITE)
    body.set(1, 2, f, WHITE)

    capv = Vox()
    lobe(capv, 0, 9, 0, 7.6, 5.0, 7.6, cap, p=3.4)
    for k in [k for k in capv.v if k[1] < 8]:
        del capv.v[k]
    toplight(capv, lambda c: True, [cap_d, cap_d, cap, cap_l], sparkle=0.05)
    capv.paint(lambda x, y, z, c: C("#d9c9e6") if y == 8 else None)
    for (x, y, z) in list(capv.v):
        if (x, y + 1, z) not in capv.v and hash3(x // 2, 0, z // 2, 4) < 0.22:
            capv.v[(x, y, z)] = spot
    # espinhos no chapéu
    for (x, z) in ((-5, -3), (4, -4), (-2, 4), (5, 3), (0, -6), (-6, 2)):
        ys = [y for (xx, y, zz) in capv.v if xx == x and zz == z]
        if ys:
            capv.set(x, max(ys) + 1, z, C("#d8c9a0"))
            capv.set(x, max(ys) + 2, z, WHITE)

    def arm(sign):
        a = Vox()
        x = 4 if sign > 0 else -6
        a.box(x, 3, -1, x + 1, 5, 0, stem_d)
        a.set(x + (1 if sign > 0 else 0), 2, 0, stem)
        return a

    def leg(sign):
        l = Vox()
        x = 1 if sign > 0 else -3
        l.box(x, 0, -1, x + 1, 0, 2, C("#5a4a2a"))
        return l

    rig = Rig("Fungo (A)", VS * 1.25, "hopper", meta={"role": "inimigo simples"})
    rig.part("body", body, (0, 0, 0))
    rig.part("cap", capv, (0, 8, 0), "body")
    rig.part("arm_l", arm(1), (4, 5, 0), "body")
    rig.part("arm_r", arm(-1), (-4, 5, 0), "body")
    rig.part("leg_l", leg(1), (2, 1, 0), "body")
    rig.part("leg_r", leg(-1), (-2, 1, 0), "body")
    return rig


def tortuga():
    """~2,1 u de altura e ~3,6 u de comprimento com voxel de 1/10 u."""
    vs = 1 / 10
    skin, skin_d, skin_l = C("#7fb86a"), C("#5c9450"), C("#a4d484")
    shell, belly = C("#8a6a48"), C("#e6d6a0")
    body = Vox()
    # casco em domo com placas
    dome = Vox()
    lobe(dome, 0, 9, -1, 11.5, 9.5, 14.5, shell, p=2.6)
    for k in [k for k in dome.v if k[1] < 7]:
        del dome.v[k]
    toplight(dome, lambda c: True, ramp(shell), sparkle=0.1)
    for (x, y, z) in list(dome.v):
        if ((x + 40) % 6 == 0 or (z + 40) % 7 == 0) and (x, y + 1, z) not in dome.v:
            dome.v[(x, y, z)] = tone(shell, -1.8)
    body.box(-10, 5, -14, 9, 7, 12, belly)
    lobe(body, 0, 7, -1, 11.5, 2.2, 14.5, tone(shell, -1.2), p=3)
    body.merge(dome)
    # jardim no casco: grama, árvore pequena, flores, pedra
    grass = ramp(C("#5fc044", "foliage"))
    for (x, y, z) in list(body.v):
        if (x, y + 1, z) not in body.v and y >= 15 and hash3(x, 0, z, 3) < 0.85:
            body.v[(x, y, z)] = grass[2 + (hash3(x, 1, z, 3) > 0.7)]
    top = max(y for (x, y, z) in body.v)
    for (x, z, c) in ((4, 3, "#ff4d5e"), (-5, 2, "#ffd23a"), (3, -6, "#ffffff"), (-4, -7, "#5aa0ff"), (-2, 6, "#ff9ac4")):
        ys = [y for (xx, y, zz) in body.v if xx == x and zz == z]
        if ys:
            body.set(x, max(ys) + 1, z, C("#4fa83e", "foliage"))
            body.set(x, max(ys) + 2, z, C(c))
    body.box(5, top - 3, -4, 7, top - 1, -2, C("#9aa3ad"))
    body.box(-1, top, -2, 0, top + 6, -1, C("#7a4f2c"))
    canopy = Vox()
    lobe(canopy, -0.5, top + 9, -1.5, 5, 4, 5, C("#46b040", "foliage"), p=5, rough=0.3)
    toplight(canopy, lambda c: True, ramp(C("#46b040", "foliage")))
    body.merge(canopy)

    head = Vox()
    lobe(head, 0, 8.5, 17, 5.2, 4.6, 5, skin, p=3.6)
    head.box(-2, 6, 12, 1, 8, 14, skin_d)                # pescoço
    toplight(head, lambda c: c == skin, [skin_d, skin_d, skin, skin_l], sparkle=0)
    f = max(z for (x, y, z) in head.v)
    # rosto na frente: olhos grandes e mansos, bochechas, sorriso
    for ex in (-4, 2):
        head.box(ex, 9, f, ex + 1, 10, f, EYE)
        head.set(ex if ex < 0 else ex + 1, 10, f, WHITE)
    head.box(-2, 6, f, 1, 6, f, skin_d)
    head.set(-3, 7, f, skin_d)
    head.set(2, 7, f, skin_d)
    head.set(-5, 8, f - 1, C("#f2a8a0"))
    head.set(4, 8, f - 1, C("#f2a8a0"))
    head.box(-3, 5, f - 1, 2, 5, f, skin_l)              # queixo claro
    head.box(-1, 13, 16, 0, 13, 17, C("#ff9ac4"))        # florzinha na cabeça
    head.set(-1, 14, 16, C("#ffe98a"))

    def leg(x, z):
        l = Vox()
        l.box(x, 0, z, x + 4, 5, z + 4, skin_d)
        l.box(x, 0, z, x + 4, 0, z + 5, skin)
        for i in (0, 2, 4):
            l.set(x + i, 0, z + 5, C("#f2ead0"))
        return l

    tail = Vox()
    tail.box(-1, 5, -17, 0, 6, -15, skin_d)
    tail.set(-1, 5, -18, skin)

    rig = Rig("Tortuga (A)", vs, "quadruped", meta={"role": "criatura grande (referencia de escala)"})
    rig.part("body", body, (0, 6, 0))
    rig.part("head", head, (0, 7, 13), "body")
    rig.part("tail", tail, (0, 6, -14), "body")
    rig.part("leg_fl", leg(5, 6), (7.5, 6, 8.5))
    rig.part("leg_fr", leg(-10, 6), (-7.5, 6, 8.5))
    rig.part("leg_bl", leg(5, -12), (7.5, 6, -9.5))
    rig.part("leg_br", leg(-10, -12), (-7.5, 6, -9.5))
    return rig


def build(out):
    pompom().save(f"{out}/creatures/a/pompom")
    lumi().save(f"{out}/creatures/a/lumi")
    fungo().save(f"{out}/creatures/a/fungo")
    tortuga().save(f"{out}/creatures/a/tortuga")
