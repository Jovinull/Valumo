"""Direção E — Fábula Voxel: criaturas (rodada 02).

Escala de complexidade (docs/art/visual-identity.md §12):
    nível 1  criatura pequena comum   voxel 1/32 u, 2 olhos 2 × 3 com brilho, 3–4 cores-base, ≤ 3 mil faces
    nível 2  criatura de referência   voxel 1/40 u (o mesmo do herói), olho da E com íris, ≤ 6 mil faces
    nível 3  criatura grande/cenário  voxel 1/16–1/20 u (família do ambiente), jardim/acessórios, ≤ 15 mil faces

pompom  — REFERÊNCIA (nível 2), refeito do zero: bola de pelo com orelhas altas de ponta ferrugem,
          bochechas fofas, broto na cabeça, rabo-pompom listrado.
fungo   — nível 1: o da B com o rosto limpo (a B registrou "rosto poluído": sobrancelha e boca
          disputavam poucos voxels). Olhos semicerrados + sobrancelha grossa + boca em arco.
lumi    — nível 1: o da B (já coerente com a E).
tortuga — nível 3: o da B (gigante manso; o jardim do casco usa a escala do ambiente).
"""
import math

import charlib_e as L
import creatures_b as CB
from chars_b import EYE, EYE_HI, P, light, normal, sblob, shadow, stack
from charlib_e import BLUSH, LASH, PUPIL, decal, shade
from voxlib import C, Rig, Vox, exposed_faces


def pompom():
    fur = P("#f2dcb2")
    belly = P("#fbefd6")
    russet = P("#c66a3c")
    pink = P("#f2a294")
    foot = P("#9e5a3a")
    sprout = P("#7cb04a", "foliage")
    iris = C("#8a4e30")

    b = Vox()
    sblob(b, 0, 10.0, 0, 9.2, 8.6, 9.6, fur, p=2.3, p_lo=2.9)
    for (x, y, z) in list(b.v):                       # barriga clara; duas listras ferrugem nas costas
        if z >= 4 and y <= 10 and abs(x + 0.5) < 6:
            b.v[(x, y, z)] = belly
        if z <= 2 and y >= 11 and abs(abs(x + 0.5) - 3.0) < 1.1:
            b.v[(x, y, z)] = russet
    for s in (1, -1):                                 # bochechas fofas: quebram o contorno da bola
        sblob(b, s * 8.4, 7.6, 3.6, 2.6, 2.2, 2.6, fur, p=2.2)
    sblob(b, 0, 18.4, 1.0, 2.0, 1.6, 2.0, fur)        # topetinho
    # placa de rosto plana (mesma regra do herói): sem ela os decalques caem em profundidades
    # diferentes na frente curva e o rosto se fragmenta em close
    FZ = 8
    for k in [k for k in b.v if k[2] > FZ and 5 <= k[1] <= 16 and abs(k[0] + 0.5) < 7.5]:
        del b.v[k]
    for x in range(-7, 7):
        for y in range(6, 16):
            zs = [k[2] for k in b.v if k[0] == x and k[1] == y]
            if zs and 4 <= max(zs) < FZ and ((x + 0.5) / 7.2) ** 2 + ((y + 0.5 - 10.8) / 5.6) ** 2 <= 1.0:
                for z in range(max(zs), FZ + 1):
                    b.set(x, y, z, belly if y <= 10 else fur)
    shade(b)
    # rosto: olhos 3 × 5 (mesma gramática do herói), nariz rosado, boca mínima, blush
    for xs in ((2, 3, 4), (-5, -4, -3)):
        # olho de animal (rodada 03): sem linha de cílio, brilho de 2 voxels — não é o rosto dos heróis
        decal(b, xs[1], 14, PUPIL)
        decal(b, xs[0], 13, EYE_HI)
        decal(b, xs[0], 12, EYE_HI)
        decal(b, xs[1], 13, PUPIL)
        decal(b, xs[2], 13, PUPIL)
        decal(b, xs[1], 12, PUPIL)
        decal(b, xs[2], 12, PUPIL)
        decal(b, xs[0], 11, iris)
        decal(b, xs[1], 11, iris)
        decal(b, xs[2], 11, shadow(iris, 1.2))
        for x in xs:
            decal(b, x, 10, light(iris, 1.6))
        out = 5 if xs[0] > 0 else -6
        decal(b, out, 9, BLUSH)
        decal(b, out + (1 if out > 0 else -1), 9, BLUSH)
    decal(b, -1, 9, pink)
    decal(b, 0, 9, pink)
    decal(b, -1, 8, shadow(fur, 1.6))
    decal(b, 0, 8, shadow(fur, 1.6))

    def ear():
        e = Vox()
        prof = [(2.0, 1.4), (2.5, 1.6), (2.8, 1.7), (2.9, 1.7), (2.8, 1.7), (2.6, 1.6), (2.3, 1.5), (1.9, 1.3), (1.4, 1.1), (0.9, 0.8)]
        for i, (rx, rz) in enumerate(prof):
            stack(e, 16 + i, 16 + i, lambda y: (5.0 + i * 0.32, -0.6, rx, rz), russet if i >= 7 else fur, p=2.0)
        shade(e)
        for i in range(2, 7):
            decal(e, 5 + (i // 3), 16 + i, pink)
        return e

    ear_l = ear()
    tail = Vox()
    sblob(tail, 0, 8.0, -11.6, 4.0, 4.0, 3.6, belly, p=2.2)
    for (x, y, z) in list(tail.v):
        if z < -13:
            tail.v[(x, y, z)] = russet
    shade(tail)
    leg = Vox()
    B_ = L.B
    B_.rbox(leg, 2, 0, -1, 5, 2, 7, foot, r=1)
    shade(leg)
    sp = Vox()                                        # broto: duas folhas que balançam
    sp.box(0, 20, 1, 0, 22, 1, sprout)
    for i in range(4):
        sp.set(1 + i, 22 + i // 2, 1, sprout)
        sp.set(-1 - i, 23 + i // 2, 1, light(sprout))
    sp.set(4, 23, 1, light(sprout))

    rig = Rig("Pompom (E)", 1 / 40, "hopper", meta={"headsTall": 1.0, "tier": 2})
    rig.part("body", b.merge(sp), (0, 0, 0))
    rig.part("ear_l", ear_l, (5.5, 16.5, -0.6), "body")
    rig.part("ear_r", ear_l.flipped_x(), (-5.5, 16.5, -0.6), "body")
    rig.part("tail", tail, (0, 8.0, -9.0), "body")
    rig.part("leg_l", leg, (3.5, 2.0, 1.0), "body")
    rig.part("leg_r", leg.flipped_x(), (-3.5, 2.0, 1.0), "body")
    rig.extra["affineWind"] = True
    rig.meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    return rig


def fungo():
    """O fungo da B com rosto limpo. Os voxels do rosto são repintados depois do modelo pronto."""
    rig = CB.fungo()
    body = next(v for n, v, _, _ in rig.parts if n == "body")
    skin = P("#ecdcb8")
    for (x, y) in [(x, y) for x in range(-6, 6) for y in range(4, 14)]:
        z = CB.front_z(body, x, y)
        if z is not None and body.v[(x, y, z)][:3] != skin[:3] and y > 4:
            # devolve a pele (com o tom de sombra que o shade daria) antes de redesenhar
            body.v[(x, y, z)] = skin
    shade(body)
    brow = P("#6b3f4a")
    for xs in ((2, 3), (-4, -3)):                     # olhos semicerrados 2 × 2 com brilho
        for x in xs:
            decal(body, x, 8, EYE)
            decal(body, x, 9, EYE)
        decal(body, xs[0], 9, EYE_HI)
    for (x, y) in ((1, 10), (2, 11), (3, 11), (4, 11), (-2, 10), (-3, 11), (-4, 11), (-5, 11)):
        decal(body, x, y, brow)                       # sobrancelha grossa em V (rabugento)
    for (x, y) in ((-2, 5), (-1, 6), (0, 6), (1, 5)):  # boca em arco, sem dente
        decal(body, x, y, CB.MOUTH)
    rig.name = "Fungo (E)"
    rig.meta = {"headsTall": 1.0, "tier": 1, "faces": sum(exposed_faces(v) for _, v, _, _ in rig.parts)}
    return rig


# ----------------------------------------------------------------------------- lumi (rodada 03)

def lumi():
    """Espírito-broto: corpo-gota luminoso com rosto próprio (olhos ovais 1 × 2), cálice de pétalas no
    topo (em vez do telhado de lanterna da rodada 02), antena enrolada com conta de luz, bracinhos de
    folha, rastro de luz e três sementes luminosas em órbita. Nível 1, voxel 1/32."""
    glow = C("#ffd27a", "emissive")
    glow_hi = C("#fff2c8", "emissive")
    glow_d = C("#ffb05a", "emissive")
    petal = P("#4fbf9c")
    petal_d = P("#2f8a78")
    petal_tip = P("#f08a7a")
    stem = P("#5f9a52", "foliage")
    eye = C("#3a2238")
    core = Vox()
    sblob(core, 0, 7.0, 0, 5.4, 6.0, 5.4, glow, p=2.2, p_lo=2.6)
    for (x, y, z) in list(core.v):
        if y >= 9 and z >= 0 and x <= 1:
            core.v[(x, y, z)] = glow_hi
        if y <= 3:
            core.v[(x, y, z)] = glow_d
    for xs in (2, -3):                                # olhos ovais verticais (não é o olho dos heróis)
        decal(core, xs, 7, eye)
        decal(core, xs, 8, eye)
    decal(core, -1, 5, eye)                           # boquinha em "v"
    decal(core, 0, 5, eye)
    for x in (4, -5):
        decal(core, x, 6, C("#ff9a8a", "emissive"))
    body = Vox()                                      # cálice de 5 pétalas abraçando o topo
    for i in range(5):
        a = i * 2 * math.pi / 5 + 0.3
        cx, cz = math.cos(a) * 4.2, math.sin(a) * 4.2
        # pétala grande em colher: nasce no ombro do corpo, sobe e abre para fora (silhueta de tulipa)
        L.D.lock(body, (cx * 0.55, 8.5, cz * 0.55), (cx * 1.25, 13.0, cz * 1.25), (cx * 1.6, 14.5, cz * 1.6), 3.0, 1.4, petal)
    for k in list(body.v):
        if k in core.v:
            del body.v[k]
    shade(body)
    for (x, y, z), c in list(body.v.items()):
        if y <= 10:
            body.v[(x, y, z)] = petal_d
        elif y >= 13 and abs(x) + abs(z) > 5:
            body.v[(x, y, z)] = petal_tip
    L.D.lock(body, (0, 12, 0), (1.5, 19, -1), (4.0, 20.0, 1.5), 1.0, 0.6, stem)   # antena
    # rodada 04: a conta da antena é a "semente de luz" verde-água (assinatura P1); o corpo continua quente
    sblob(body, 4.3, 19.8, 2.0, 1.5, 1.5, 1.5, C("#4fd6ae", "emissive"), p=2.0)
    tail = Vox()                                      # rastro de luz enrolado
    for i, (r, ox, oz) in enumerate(((2.4, 0, -0.5), (2.0, 0.6, -1.4), (1.6, 1.4, -2.0), (1.2, 1.6, -2.2), (0.8, 1.2, -1.8))):
        stack(tail, -i, -i, lambda y: (ox, oz, r, r), glow if i < 2 else glow_d, p=2.0)
    lf = Vox()
    for i in range(4):
        lf.box(6 + i, 6 + (i + 1) // 2, -1, 6 + i, 7 + (i + 1) // 2, 0, petal)
    shade(lf)
    ring = Vox()
    for k in range(3):
        a = k * 2 * math.pi / 3
        x, z = round(math.cos(a) * 9.5 - 0.5), round(math.sin(a) * 9.5 - 0.5)
        ring.box(x, 6 + k * 2, z, x + 1, 7 + k * 2, z + 1, glow_hi)
    rig = Rig("Lumi (E)", 1 / 32, "floater", meta={"headsTall": 1.0, "tier": 1})
    rig.part("body", body, (0, 7.0, 0))
    rig.part("core", core, (0, 7.0, 0), "body")
    rig.part("tail", tail, (0, 1.0, 0), "body")
    rig.part("leaf_l", lf, (5.5, 7.0, 0), "body")
    rig.part("leaf_r", lf.flipped_x(), (-5.5, 7.0, 0), "body")
    rig.part("ring", ring, (0, 7.0, 0), "body")
    rig.extra["affineWind"] = True
    rig.meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    return rig


# ----------------------------------------------------------------------------- fungo (rodada 03)

def fungo2():
    """Fungo da rodada 03: o chapéu cai sobre o rosto como um capuz (a aba faz a sobrancelha), olhos em
    fenda à sombra da aba, corpo atarracado. Silhueta da espécie = chapéu; o rosto é mínimo."""
    stem = P("#ecdcb8")
    cap = P("#b3475c")
    spot = P("#f6e6c6")
    gill = P("#d9b48e")
    vine = P("#4f6b3e")
    foot = P("#6b3f4a")
    b = Vox()
    stack(b, 2, 13, lambda y: (0, 0.3, 5.6 - 1.0 * ((y - 2) / 11.0) ** 1.3, 5.2 - 1.0 * ((y - 2) / 11.0) ** 1.3), stem, p=2.6)
    shade(b)
    for xs in ((1, 2, 3), (-4, -3, -2)):              # olhos 3 × 2 (rodada 04: legíveis da câmera de jogo)
        for x in xs:
            decal(b, x, 9, C("#2a1c34"))
            decal(b, x, 10, C("#2a1c34"))
        decal(b, xs[0] if xs[0] > 0 else xs[2], 10, C("#fffaf0"))      # brilho do lado da luz
    for (x, y) in ((-2, 6), (-1, 6), (0, 6), (1, 6), (-2, 7), (1, 7)):   # boca em arco para baixo
        if (x, y) in ((-2, 7), (1, 7)):
            continue
        decal(b, x, y, CB.MOUTH)
    c = Vox()
    sblob(c, 0, 13.0, 0.4, 10.0, 7.4, 10.0, cap, p=2.25)
    for k in [k for k in c.v if k[1] < 12 or (k[2] > 6 and k[1] < 14)]:
        del c.v[k]                                    # rodada 04: aba da frente na altura do resto e recuada (o rosto aparece de cima)
    stack(c, 11, 11, lambda y: (0, 0.4, 8.6, 8.6), gill, p=2.2)
    shade(c)
    for (sx, sz, r) in ((-4, 4, 2.6), (5, 2, 2.0), (-1, -5, 2.4), (6, -4, 1.6)):
        for (x, y, z) in list(c.v):
            if (x, y + 1, z) not in c.v and (x + 0.5 - sx) ** 2 + (z + 0.5 - sz) ** 2 <= r * r:
                c.v[(x, y, z)] = spot
    for (x, z, h) in ((-1, 0, 2), (3, -2, 2)):        # dois espinhos curtos no topo
        y0 = max(k[1] for k in c.v if k[0] == x and k[2] == z) + 1
        c.box(x, y0, z, x, y0 + h - 1, z, CB.light(stem))
    c.translate(0, 2, 0)
    arm = Vox()
    for (x, y) in ((5, 9), (6, 9), (7, 8), (8, 7), (8, 6)):
        arm.box(x, y, 0, x, y, 1, vine)
    arm.box(8, 4, -1, 9, 5, 2, P("#6f9a44", "foliage"))  # mão de folha
    leg = Vox()
    leg.box(1, 0, -2, 4, 1, 3, foot)
    leg.box(2, 0, 4, 3, 0, 4, foot)
    shade(leg)
    rig = Rig("Fungo (E)", 1 / 32, "hopper", meta={"headsTall": 1.0, "tier": 1, "lean": 0.06})
    rig.part("body", b, (0, 0, 0))
    rig.part("cap", c, (0, 14.0, 0), "body")
    rig.part("arm_l", arm, (5.0, 9.0, 0.5), "body")
    rig.part("arm_r", arm.flipped_x(), (-5.0, 9.0, 0.5), "body")
    rig.part("leg_l", leg, (2.5, 2.0, 0.5), "body")
    rig.part("leg_r", leg.flipped_x(), (-2.5, 2.0, 0.5), "body")
    rig.meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    return rig


# ----------------------------------------------------------------------------- tortuga (rodada 03)

def tortuga(shell_style="s3"):
    """Tortuga com três estudos de carapaça (rodada 03). s1 = rodada 02 refinada (jardim com árvore);
    s2 = mineral (placas de ardósia, musgo e geodos de cristal); s3 = ecossistema fantástico sem árvore
    (prateleiras de fungo-coral em camadas, frondes em espiral, cápsulas luminosas, marco de pedra)."""
    from envlib import hash3
    skin = P("#a8b574")
    skin_d = P("#8a9a62")
    belly = P("#eadfb4")
    shell = {"s1": P("#b56c48"), "s2": P("#8b8597"), "s3": P("#9a6a52")}[shell_style]
    seam = {"s1": P("#8c4c3c"), "s2": P("#655f72"), "s3": P("#734a3e")}[shell_style]
    rim = P("#ecd29c")
    moss = P("#8fbf55", "foliage")
    moss_l = P("#b1d466", "foliage")
    nail = P("#f3e6c4")
    b = Vox()
    sblob(b, 0, 11.0, 0, 12.5, 5.5, 16.5, belly, p=2.6)
    sh = Vox()
    sblob(sh, 0, 12.0, -1.0, 15.5, 14.0, 18.5, shell, p=2.3)
    for (x, y, z) in list(sh.v):
        if y < 12:
            del sh.v[(x, y, z)]
    stack(sh, 10, 11, lambda y: (0, -1.0, 16.4, 19.4), rim, p=2.3)
    for (x, y, z) in list(sh.v):                      # placas
        if sh.v[(x, y, z)] != shell:
            continue
        u = x + (4 if ((z + 40) // 9) % 2 else 0)
        if (u + 40) % 9 == 0 or (z + 40) % 9 == 0:
            sh.v[(x, y, z)] = seam
    top_cells = [(x, y, z) for (x, y, z) in sh.v if (x, y + 1, z) not in sh.v and normal(sh, x, y, z)[1] > 0.72]
    if shell_style == "s1":
        for (x, y, z) in top_cells:                   # gramado limpo (manchas largas, sem salpico)
            sh.v[(x, y, z)] = moss_l if hash3(x // 3, 1, z // 3, 5) > 0.6 else moss
    else:
        for (x, y, z) in top_cells:                   # musgo só em manchas grandes
            if hash3(x // 4, 1, z // 4, 9) > 0.45:
                sh.v[(x, y, z)] = moss_l if hash3(x // 2, 2, z // 2, 3) > 0.7 else moss
    shade(sh, only={shell, rim, seam})
    b.merge(sh)
    shade(b, only={belly})
    top = max(k[1] for k in b.v if abs(k[0]) < 2 and abs(k[2]) < 2)

    def ground(x, z):
        ys = [k[1] for k in b.v if k[0] == x and k[2] == z]
        return max(ys) + 1 if ys else top

    if shell_style == "s1":
        bark = P("#8a5a3c")
        leafc = P("#5fa85a", "foliage")
        tx, tz = -4, -5
        for i in range(6):
            b.box(tx + i // 3, top + i - 1, tz, tx + 1 + i // 3, top + i - 1, tz + 1, bark)
        cn = Vox()
        sblob(cn, tx + 2.5, top + 7.0, tz + 1, 5.2, 4.0, 5.0, leafc, p=2.2)
        sblob(cn, tx - 1.0, top + 6.5, tz + 2.5, 3.2, 2.6, 3.2, leafc, p=2.2)
        shade(cn, hi=0.45)
        b.merge(cn)
        for (x, z, col) in ((7, -6, "#d9644a"), (8, -3, "#d9644a"), (-9, 3, "#f2c14e")):
            y0 = ground(x, z)
            b.box(x, y0, z, x, y0 + 1, z, nail)
            b.box(x - 1, y0 + 2, z - 1, x + 1, y0 + 2, z + 1, P(col))
    elif shell_style == "s2":
        crystals = [((-3, -4), C("#7ff0dc", "emissive"), 7), ((5, 2), C("#b48aff", "emissive"), 5),
                    ((-8, 5), C("#7ff0dc", "emissive"), 4), ((2, -10), C("#b48aff", "emissive"), 4)]
        for (x, z), col, h in crystals:               # geodos: aglomerados de prismas inclinados
            y0 = ground(x, z)
            for j, (dx, dz, hh, lean) in enumerate(((0, 0, h, 0), (2, 1, h - 2, 1), (-2, 1, h - 3, -1), (1, -2, h - 3, 1))):
                for yy in range(hh):
                    ox = round(lean * yy / 3)
                    b.box(x + dx + ox, y0 + yy, z + dz, x + dx + ox + 1, y0 + yy, z + dz + 1,
                          C("#e6fff8", "emissive") if yy == hh - 1 else col)
            sblob(b, x, y0 - 0.5, z, 3.0, 1.4, 3.0, P("#6f6878"), p=2.2)   # base de pedra do geodo
        for (x, z) in ((7, -7), (-6, -9), (9, 6)):
            sblob(b, x, ground(x, z), z, 2.2, 1.6, 2.0, P("#a59fb0"), p=2.4)
    else:
        coral = P("#e8835a")
        coral_l = P("#f4b07a")
        frond = P("#5fa85a", "foliage")
        pod = C("#9ff0c8", "emissive")
        # prateleiras de fungo-coral: discos em camadas, presos a um "tronco" curto e torto
        for (x, z, levels) in ((-4, -4, 4), (6, 3, 3)):
            y0 = ground(x, z)
            for i in range(levels):
                yy = y0 + i * 3
                b.box(x, y0, z, x + 1, yy + 1, z + 1, P("#c9a07a"))
                r = 3.6 - i * 0.6
                ox = (1 if i % 2 else -1) * 1.2
                stack(b, yy + 2, yy + 2, lambda y: (x + 0.5 + ox, z + 0.5, r, r * 0.8), coral if i % 2 else coral_l, p=2.3)
        # frondes enroladas em espiral (como samambaia nascendo)
        for (x, z, rot) in ((-9, 4, 0.0), (2, -11, 2.0), (9, -5, 4.0)):
            y0 = ground(x, z)
            for t in range(10):
                a = rot + t * 0.55
                rr = 3.0 * (1 - t / 11)
                b.set(x + round(math.cos(a) * rr * 0.4), y0 + t // 2 + (2 if t > 4 else 0), z + round(math.sin(a) * rr), frond)
        # cápsulas luminosas em hastes curvas
        for (x, z, h) in ((0, 6, 5), (-6, 9, 4), (3, -6, 6), (10, 1, 4)):
            y0 = ground(x, z)
            for yy in range(h):
                b.set(x + (1 if yy > h // 2 else 0), y0 + yy, z, frond)
            sblob(b, x + 1.5, y0 + h + 0.5, z + 0.5, 1.2, 1.5, 1.2, pod, p=2.0)
        # marco de pedra: três pedras empilhadas
        for i, (r, dy) in enumerate(((2.0, 0.6), (1.6, 2.6), (1.1, 4.2))):
            sblob(b, -1, ground(-1, 1) + dy - 0.5, 1, r, 1.0, r, P("#c8bcb4") if i % 2 == 0 else P("#a99c98"), p=2.4)

    def leg(fx, fz):
        l = Vox()
        stack(l, 0, 10, lambda y: (fx, fz, 4.4 - 0.5 * (y / 10.0), 4.6 - 0.5 * (y / 10.0)), skin, p=2.4)
        l.paint(lambda x, y, z, c: skin_d if y <= 1 else None)
        shade(l)
        for dx in (-3, -1, 1):
            zf = max(k[2] for k in l.v if k[1] == 0)
            l.set(int(fx) + dx, 0, zf + 1, nail)
            l.set(int(fx) + dx, 1, zf + 1, nail)
        return l

    hd = Vox()
    for i in range(8):
        sblob(hd, 0, 11.5 + i * 0.75, 15 + i * 1.1, 4.4, 4.2, 3.0, skin, p=2.3)
    sblob(hd, 0, 18.5, 25.0, 7.0, 6.2, 7.2, skin, p=2.5, p_lo=3.0)
    for (x, y, z) in list(hd.v):
        if y <= 15 and z >= 21:
            hd.v[(x, y, z)] = belly
    shade(hd)
    for xs in (3, -5):                                # olhos de gigante manso: pálpebra pesada
        for x in (xs, xs + 1):
            for y in (18, 19):
                CB.put_front(hd, x, y, EYE)
            CB.put_front(hd, x, 20, skin_d)
        CB.put_front(hd, xs, 19, EYE_HI)
    for x in (5, 6, -6, -7):
        CB.put_front(hd, x, 17, BLUSH)
    for (x, y) in ((-3, 15), (-2, 14), (-1, 14), (0, 14), (1, 14), (2, 15)):
        CB.put_front(hd, x, y, shadow(skin_d, 1.4))
    tl = Vox()
    for i in range(6):
        sblob(tl, 0, 9.0 - i * 0.6, -17.5 - i * 1.2, 2.6 - i * 0.35, 2.4 - i * 0.3, 1.6, skin, p=2.2)
    shade(tl)
    rig = Rig(f"Tortuga {shell_style.upper()}" if shell_style != "s3" else "Tortuga (E)", 1 / 16, "quadruped",
              meta={"headsTall": 1.0, "tier": 3})
    rig.part("body", b, (0, 10.0, 0))
    rig.part("head", hd, (0, 12.0, 15.0), "body")
    rig.part("leg_fl", leg(9.0, 10.0), (9.0, 10.0, 10.0), "body")
    rig.part("leg_fr", leg(-9.0, 10.0), (-9.0, 10.0, 10.0), "body")
    rig.part("leg_bl", leg(9.0, -11.0), (9.0, 10.0, -11.0), "body")
    rig.part("leg_br", leg(-9.0, -11.0), (-9.0, 10.0, -11.0), "body")
    rig.part("tail", tl, (0, 9.0, -17.0), "body")
    rig.extra["affineWind"] = True
    rig.meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    return rig


# ----------------------------------------------------------------------------- mariposa (rodada 06)

def _wing(span, chord, front, tail=0.0, scallop=0.7, dihedral=0.32, base=None, band=None, edge=None, spot=None):
    """Asa plana (1 voxel de espessura, nervura de ataque com 2) no plano horizontal, para a ESQUERDA (+x).
    u = envergadura (0 na articulação), v = corda (+ = frente). Borda de fuga recortada em festões; diedro
    sobe a ponta em degraus de voxel. spot = (u, v, raio) do ocelo; tail = ponta de cauda na asa de trás."""
    v = Vox()
    for u in range(0, span + 1):
        k = u / span
        vf = front * math.sqrt(max(0.0, 1 - k ** 2.6)) + 1.2 * math.sin(math.pi * k)   # bordo de ataque cheio
        vb = -(chord * math.sqrt(max(0.0, 1 - k ** 2.2))) - 1.0 + scallop * (0.5 + 0.5 * math.cos(u * 1.25))
        if tail and k > 0.35:
            vb -= tail * max(0.0, 1 - abs(k - 0.62) / 0.2)              # cauda curta na asa de trás
        if k > 0.92:
            vf = min(vf, 1.5)
        y = round(dihedral * u)
        for vv in range(math.floor(vb), math.ceil(vf) + 1):
            col = base
            if band and u <= 3:
                col = band
            if edge and (u >= span - 1 or vv <= math.floor(vb) or (vv >= math.ceil(vf) and u > 2)):
                col = edge
            if spot:
                d = math.hypot(u - spot[0], vv - spot[1])
                if d <= spot[2]:
                    col = spot[3] if d > spot[2] - 1.1 else spot[4]
                if d < 0.75 and len(spot) > 5:
                    col = spot[5]
            v.set(u, y, vv, col)
            if vv >= math.ceil(vf) - 1 and u <= span - 2:
                v.set(u, y + 1, vv, shadow(base, 0.6) if col == base else col)   # nervura do bordo de ataque
    return v


def mariposa():
    """Mariposa de bosque (rodada 06) — nível 1 (voxel 1/32), anatomia nova no bestiário (inseto voador de
    quatro asas). Tórax felpudo com gola de pelo, cabeça redonda com rosto em PLACA (olhos ovais escuros 2 × 3,
    sem sobrancelha nem íris de herói), antenas plumosas, abdômen em gomos pendendo atrás. Asas anteriores com
    borda recortada, faixa escura na base e ocelo cujo miolo é a semente de luz P1 (um voxel por asa);
    posteriores menores com cauda curta. Tipo de animação: "flutter"."""
    fur = P("#f1e0c4")
    fur_d = P("#d9bf9c")
    head_c = P("#cdb4a2")
    plum = P("#7a4c68")
    rose = P("#a8728e")
    wing_c = P("#d9a796")
    wing_band = P("#9c6466")
    wing_edge = P("#f4e4cc")
    hind_c = P("#e8bfa6")
    ochre = P("#c9a064")
    ring_c = P("#5f3550")
    seed = C("#4fd6ae", "emissive")
    eye = C("#2a1c34")

    body = Vox()                                       # tórax
    sblob(body, 0, 12.0, 0.0, 4.0, 3.7, 4.0, fur_d, p=2.3)
    sblob(body, 0, 13.2, 1.8, 4.8, 2.5, 3.2, fur, p=2.2)          # gola de pelo
    for (x, y, z) in list(body.v):
        if y >= 14 and (x + z) % 3 == 0:
            body.v[(x, y, z)] = light(fur)
    shade(body)

    hd = Vox()                                         # cabeça com rosto em placa
    sblob(hd, 0, 15.2, 4.4, 3.7, 3.4, 3.1, head_c, p=2.4)
    for k in [k for k in hd.v if k[2] > 7]:
        del hd.v[k]
    for x in range(-3, 3):
        for y in range(13, 18):
            f = L.front_z(hd, x, y)
            if f is not None and 5 <= f < 7:
                for z in range(f, 8):
                    hd.set(x, y, z, head_c)
    for k in [k for k in hd.v if k in body.v]:
        del hd.v[k]
    shade(hd)
    for xs in ((1, 2), (-3, -2)):                       # olhos ovais 2 × 3 (brilho do lado da luz)
        for x in xs:
            for y in (14, 15, 16):
                decal(hd, x, y, eye)
        decal(hd, xs[0], 16, C("#fffaf0"))
    decal(hd, -1, 13, P("#8a4a52"))                   # boquinha
    decal(hd, 0, 13, P("#8a4a52"))
    for x in (3, -4):
        decal(hd, x, 14, BLUSH)
    for side in (1, -1):                               # antenas plumosas (6-conexas, farpas alternadas)
        pts = [(side * 1, 18, 5), (side * 2, 20, 4), (side * 3, 22, 3), (side * 4, 23, 1), (side * 5, 23, 0), (side * 6, 22, -1)]
        L.connect_path(hd, pts, lambda i: ochre)
        for i, (x, y, z) in enumerate(pts[1:-1]):
            hd.set(x + side, y, z, light(ochre))
            hd.set(x, y, z - 1, shadow(ochre))

    ab = Vox()                                         # abdômen em gomos
    for i, (cy, cz, r) in enumerate(((10.4, -3.0, 2.9), (9.2, -5.0, 2.6), (8.1, -6.9, 2.2), (7.2, -8.6, 1.7), (6.6, -10.0, 1.2))):
        sblob(ab, 0, cy, cz, r, r * 0.95, 1.6, plum if i % 2 == 0 else rose, p=2.3)
    for k in [k for k in ab.v if k in body.v]:
        del ab.v[k]
    shade(ab)

    fw = _wing(13, 7.5, 3.0, scallop=0.9, dihedral=0.0, base=wing_c, band=wing_band, edge=wing_edge,
               spot=(8.0, -1.6, 2.6, ring_c, wing_edge, seed))
    hw = _wing(9, 6.5, 0.8, tail=3.0, scallop=0.7, dihedral=0.0, base=hind_c, band=wing_band, edge=wing_edge)
    shade(fw)
    shade(hw)

    def place(w, dx, dy, dz):
        out = Vox()
        for (x, y, z), c in w.v.items():
            out.set(x + dx, y + dy, z + dz, c)
        return out

    fl = place(fw, 3, 12, 0)
    hl = place(hw, 2, 11, -3)
    rig = Rig("Mariposa (E)", 1 / 32, "flutter", meta={"headsTall": 1.0, "tier": 1})
    rig.part("body", body, (0, 12.0, 0))
    rig.part("head", hd, (0, 14.0, 2.5), "body")
    rig.part("abdomen", ab, (0, 11.0, -2.0), "body")
    rig.part("wing_fl", fl, (3.0, 12.5, 0.5), "body")
    rig.part("wing_fr", fl.flipped_x(), (-3.0, 12.5, 0.5), "body")
    rig.part("wing_bl", hl, (2.0, 11.5, -2.5), "body")
    rig.part("wing_br", hl.flipped_x(), (-2.0, 11.5, -2.5), "body")
    for n, v, _, _ in rig.parts:
        L.prune_specks(v, label=f"mariposa/{n}")
    rig.meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    print(f"  mariposa: {rig.meta['faces']} faces")
    return rig


def build(out):
    pompom().save(f"{out}/creatures/e/pompom")
    fungo2().save(f"{out}/creatures/e/fungo")
    lumi().save(f"{out}/creatures/e/lumi")
    tortuga("s3").save(f"{out}/creatures/e/tortuga")
    mariposa().save(f"{out}/creatures/e/mariposa")     # rodada 06
    for st in ("s1", "s2", "s3"):                     # estudos de carapaça (modo LINHA: r3-carapaca)
        tortuga(st).save(f"{out}/studies/tortuga_{st}")
