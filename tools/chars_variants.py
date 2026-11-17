"""Rodada 02 — experimento controlado: três variantes do aventureiro na MESMA direção artística.

    v1  Storybook Refined   parte do aventureiro da B (voxel 1/32) e refina rosto (sobrancelhas,
                            cílio), cabelo (mechas pintadas, nuca recortada) e animação (pés plantados).
                            Preserva a simplicidade da B.
    v2  Storybook Fusion    proporção e materiais da B com o acabamento de personagem da D, em voxel
                            1/40 (tools/chars_e.py — é o mesmo código do herói consolidado).
    v3  Stylized Compact    parte do aventureiro da D (voxel 1/44), recompacta (pernas 14 -> 12),
                            troca para a paleta da B, simplifica materiais (ombreira de couro, sem
                            poção/bainha/crista) e divide a capa em dois segmentos articulados.

Saída: assets/characters/variants/{v1,v2,v3}. Comparação: modo LINHA do viewer (conjuntos
"variantes" e "v1-v2-v3") e docs/historico/experimentos/variantes-do-aventureiro.md.
"""
import math

import chars_b as B
import chars_d as D
import chars_e as E
import charlib_e as L
from voxlib import C, Vox, exposed_faces


# ----------------------------------------------------------------------------- V1

def v1():
    rig = B.adventurer()
    hair = C("#b9542d")
    head = next(v for n, v, _, _ in rig.parts if n == "head")
    brow = B.shadow(hair, 1.6)
    # sobrancelhas curtas e cílio externo: a B as descartou por ruído; em 2 voxels com o tom do
    # cabelo escurecido elas leem como expressão, não como sujeira
    for (x, y) in ((2, 27), (3, 27), (-3, 27), (-4, 27)):
        z = B.front_z(head, x, y)
        if z is not None and z >= 4:
            head.set(x, y, z, brow)
    for (x, y) in ((4, 25), (-5, 25)):
        z = B.front_z(head, x, y)
        if z is not None and z >= 4:
            head.set(x, y, z, B.EYE)
    # nuca recortada em três pontas + mechas pintadas (corrige a "nuca chapada")
    nape = Vox()
    for x0 in (-4.0, 0.0, 4.0):
        D.lock(nape, (x0, 25, -5.5), (x0 * 1.05, 23, -7.0), (x0 * 1.1, 21.6, -7.2), 2.0, 1.0, hair)
    for k in [k for k in nape.v if k in head.v]:
        del nape.v[k]
    B.shade(nape, ctx=head.copy().merge(nape))
    head.merge(nape, overwrite=False)
    L.hair_strands(head, hair, period=0.9, zmax=1, width=0.14)
    rig.name = "V1 Storybook Refined"
    rig.meta.update({"weaponMode": "sword", "footPlant": True,
                     "faces": sum(exposed_faces(v) for _, v, _, _ in rig.parts)})
    return rig


# ----------------------------------------------------------------------------- V3

def v3():
    """Aventureiro da D recompactado. Os utilitários da D leem HIP/NECK como globais do módulo:
    ajustamos e restauramos ao final (o build de chars_d não é afetado)."""
    saved = (D.HIP, D.NECK, D.SHOULDER, D.HAND_Y)
    D.HIP, D.NECK = 12, 29
    D.SHOULDER, D.HAND_Y = D.NECK - 2.5, D.HIP + 1
    try:
        return _v3()
    finally:
        D.HIP, D.NECK, D.SHOULDER, D.HAND_Y = saved


def _v3():
    HIP, NECK, Y = D.HIP, D.NECK, D.NECK
    hair = C("#c2582c")
    tunic, tunic_trim = C("#3a958a"), C("#f3e3c0")
    cape_c, lining = C("#eeaa3a"), C("#f3e3c0")
    pants = C("#6b4a5e")
    iris = C("#3d9c78")

    sk = D.skull(y0=NECK)
    bang = {-11: 6, -10: 5, -9: 5, -8: 6, -7: 8, -6: 12, -5: 12, -4: 12, -3: 12, -2: 11, -1: 13, 0: 13, 1: 12,
            2: 12, 3: 12, 4: 12, 5: 12, 6: 10, 7: 7, 8: 6, 9: 5, 10: 5}
    h = D.hair_shell(sk, hair, bang, lambda x, z: 2 if abs(x + 0.5) < 7 else 3, y0=NECK)
    D.sup(h, 0.5, NECK + 15.5, -1.5, 9.8, 5.0, 9.4, hair, p=2.3, ymin=NECK + 12)
    # metade das mechas da D: só as que desenham silhueta
    for a, m, b, r0 in (((3, Y + 17, 0), (7, Y + 21, -1), (10, Y + 19, -5), 3.0),
                        ((-3, Y + 17, -2), (-6, Y + 20.5, -4), (-8, Y + 19, -8), 2.8),
                        ((8, Y + 11, -4), (11, Y + 9, -6), (11.5, Y + 6, -9), 2.8),
                        ((-8, Y + 11, -4), (-11, Y + 9, -6), (-12, Y + 5, -8), 2.8),
                        ((0, Y + 8, -7), (0, Y + 4, -10), (-1, Y + 0, -12), 3.0)):
        D.lock(h, a, m, b, r0, 0.8, hair)
    for a, m, b, r0 in (((-5, Y + 14, 6), (-8, Y + 12, 7.5), (-9, Y + 7, 7.5), 2.2),
                        ((5, Y + 14, 6), (8, Y + 12, 7.5), (9.5, Y + 8, 7), 2.2)):
        D.lock(h, a, m, b, r0, 0.8, hair)
    for k in [k for k in h.v if k in sk.v]:
        del h.v[k]
    tmp = h.copy().merge(sk)
    D.fill_pits(tmp, hair, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == hair)
    D.shade(h, amp=0.7, lo=-1.0, hi=1.0)                 # menos degraus que a D original
    D.shade(sk, amp=0.7, bias=0.2, custom=D.SKIN_RAMP)
    head = h.copy().merge(sk)
    D.face(head, iris, D.tone(hair, -2.2), y0=NECK)
    head_top = max(k[1] for k in h.v if abs(k[0]) < 9 and abs(k[2]) < 8 and k[1] <= NECK + 20)

    t = D.torso_base(tunic, hem=tunic_trim)
    D.belt(t, HIP + 1, D.LEATHER_D)
    for i in range(10):
        x, y = -5 + i, NECK - 2 - i
        for xx in (x, x - 1):
            zf2 = D.front(t, xx, y)
            if zf2 is not None:
                t.set(xx, y, zf2 + 1, D.LEATHER)
    sc = Vox()
    D.sup(sc, 0, NECK - 0.3, 0, 8.2, 2.5, 6.8, cape_c, p=2.6)
    for k in [k for k in sc.v if abs(k[0] + 0.5) < 4.5 and abs(k[2] + 0.5) < 3.6 and k[1] >= NECK]:
        del sc.v[k]
    t.merge(sc)
    D.fill_pits(t, need=4)
    D.shade(t, amp=0.8, lo=-1.0, hi=1.0)

    # capa curta em DOIS segmentos (cape + cape_lo): o de baixo atrasa no movimento
    cape = Vox()
    ytop, ybot = NECK - 2, 9
    for y in range(ybot, ytop + 1):
        u = (ytop - y) / (ytop - ybot)
        half = D.lerp(6.5, 9.6, u ** 0.8)
        for x in range(-11, 11):
            ax = abs(x + 0.5)
            if ax > half:
                continue
            z = round(-6.2 - u * 2.4 + (ax / half) ** 2 * (1.4 + 1.6 * u))
            k = round((-0.9 * u + 0.3) * 2) / 2
            cape.set(x, y, z, D.tone_c(cape_c, k))
            cape.set(x, y, z + 1, D.tone_c(lining, -0.5))
    split = 18
    cape_hi = cape.cut(lambda x, y, z, c: y >= split)
    cape_lo = cape
    zs = [k[2] for k in cape_lo.v if k[1] == split - 1]
    z_split = (min(zs) + max(zs)) / 2 + 0.5

    arm_l = D.arm(D.CREAM, D.LEATHER, D.LEATHER_L)
    for k in list(arm_l.v):
        if k[1] >= NECK - 6:
            arm_l.v[k] = tunic_trim if k[1] == NECK - 6 else tunic
    arm_r = arm_l.flipped_x()
    pa = Vox()
    D.sup(pa, 9.6, NECK - 1.2, 0.5, 4.6, 3.6, 4.7, D.LEATHER, p=2.2, ymin=NECK - 4)
    for k in [k for k in pa.v if k[0] < 6]:
        del pa.v[k]
    arm_r.merge(pa.flipped_x())
    for a_ in (arm_l, arm_r):
        D.shade(a_, amp=0.8, lo=-1.0, hi=1.0)
    leg_l = D.leg(pants, D.LEATHER, D.LEATHER_L, D.LEATHER_D, cuff_y=6)
    leg_r = leg_l.flipped_x()
    for l_ in (leg_l, leg_r):
        D.shade(l_, amp=0.8, lo=-1.0, hi=1.0)
    sw, grip = D.sword()

    from voxlib import Rig
    rig = Rig("V3 Stylized Compact", D.VS, "biped")
    for n, v, piv, par in (("torso", t, (0, HIP, 0.5), None), ("head", head, (0, NECK, 0.5), "torso"),
                           ("arm_l", arm_l, (8.5, D.SHOULDER, 0.5), "torso"),
                           ("arm_r", arm_r, (-8.5, D.SHOULDER, 0.5), "torso"),
                           ("leg_l", leg_l, (3.5, HIP, 0.5), None), ("leg_r", leg_r, (-3.5, HIP, 0.5), None),
                           ("weapon", sw, grip, "arm_r"),
                           ("cape", cape_hi, (0, NECK - 2, -5.5), "torso"),
                           ("cape_lo", cape_lo, (0, split, z_split), "cape")):
        rig.part(n, v, piv, par)
    head_h = head_top - NECK + 1
    total = head_top + 1
    rig.meta = {"headsTall": round(total / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "legFraction": round(HIP / total, 3), "weaponMode": "sword", "footPlant": True,
                "faces": sum(exposed_faces(v) for _, v, _, _ in rig.parts)}
    return rig


def build(out):
    for key, fn in (("v1", v1), ("v2", lambda: E.adventurer("v2")), ("v3", v3)):
        rig = fn()
        rig.save(f"{out}/characters/variants/{key}")
        print(f"  {key}: {rig.name}  {rig.meta['headsTall']} cabeças  {rig.meta['faces']} faces")
