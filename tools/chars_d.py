"""Direção D — Stylized Voxel RPG: personagens em voxel fino (1/44 u).

Linguagem de forma: cabeça grande e arredondada (18 voxels de largura, 20-22 com cabelo), corpo
em pera, mãos e botas maiores que os membros, equipamento que desenha a silhueta. A cor é pintada
por voxel: cada parte passa por `shade()`, que estima a normal da superfície e escolhe um tom da
rampa com desvio de matiz (luz quente, sombra fria) — o shader da engine soma sol, AO e rim por cima.

Este arquivo também guarda as primitivas compartilhadas com creatures_d.py e env_d.py.
"""
import math

from voxlib import C, Rig, Vox, mix, tone

VS = 1 / 44

# ----------------------------------------------------------------------------- primitivas D


def hash3(x, y, z, seed=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def sup(v, cx, cy, cz, rx, ry, rz, col, p=2.0, ymin=None, ymax=None):
    """Superelipsoide (p=2 redondo; 3+ vai ficando 'almofada')."""
    for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1):
        ax = abs((x + 0.5 - cx) / rx) ** p
        if ax > 1:
            continue
        for y in range(int(math.floor(cy - ry)) - 1, int(math.ceil(cy + ry)) + 1):
            if (ymin is not None and y < ymin) or (ymax is not None and y > ymax):
                continue
            ay = abs((y + 0.5 - cy) / ry) ** p
            if ax + ay > 1:
                continue
            for z in range(int(math.floor(cz - rz)) - 1, int(math.ceil(cz + rz)) + 1):
                if ax + ay + abs((z + 0.5 - cz) / rz) ** p <= 1.0:
                    v.set(x, y, z, col)
    return v


def ring(v, y, cx, cz, rx, rz, col, p=2.5):
    """Uma fatia horizontal (superelipse) na altura y."""
    for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1):
        ax = abs((x + 0.5 - cx) / rx) ** p
        if ax > 1:
            continue
        for z in range(int(math.floor(cz - rz)) - 1, int(math.ceil(cz + rz)) + 1):
            if ax + abs((z + 0.5 - cz) / rz) ** p <= 1.0:
                v.set(x, y, z, col)
    return v


def stack(v, y0, y1, prof, col, p=2.5):
    """Empilha fatias de y0 a y1 (inclusive). prof(t) -> (cx, cz, rx, rz), t = 0 na base, 1 no topo."""
    n = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        cx, cz, rx, rz = prof((y - y0) / n)
        ring(v, y, cx, cz, rx, rz, col(y) if callable(col) else col, p)
    return v


def lerp(a, b, t):
    return a + (b - a) * t


def blob(v, a, b, r0, r1, col):
    """Cápsula afilada de a até b (raio r0 -> r1): mechas, galhos, chifres, caudas."""
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2])) * 2) + 1
    for i in range(n + 1):
        t = i / n
        cx, cy, cz = (a[k] + (b[k] - a[k]) * t for k in range(3))
        r = r0 + (r1 - r0) * t
        ri = int(r) + 1
        for x in range(int(math.floor(cx)) - ri, int(math.floor(cx)) + ri + 1):
            for y in range(int(math.floor(cy)) - ri, int(math.floor(cy)) + ri + 1):
                for z in range(int(math.floor(cz)) - ri, int(math.floor(cz)) + ri + 1):
                    if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2 <= r * r:
                        v.set(x, y, z, col)
    return v


def lock(v, a, m, b, r0, r1, col):
    """Mecha curva: cápsula afilada ao longo de uma Bézier quadrática a -> m -> b."""
    n = 14
    prev = a
    for i in range(1, n + 1):
        t = i / n
        q = tuple((1 - t) ** 2 * a[k] + 2 * t * (1 - t) * m[k] + t * t * b[k] for k in range(3))
        blob(v, prev, q, lerp(r0, r1, (i - 1) / n), lerp(r0, r1, t), col)
        prev = q
    return v


def dilate(src, r):
    """Conjunto de células a distância <= r de qualquer voxel de src (inclui src)."""
    ri = int(math.ceil(r))
    offs = [(dx, dy, dz) for dx in range(-ri, ri + 1) for dy in range(-ri, ri + 1) for dz in range(-ri, ri + 1)
            if dx * dx + dy * dy + dz * dz <= r * r]
    out = set()
    surf = [k for k in src.v if any((k[0] + d[0], k[1] + d[1], k[2] + d[2]) not in src.v for d in N6)]
    for (x, y, z) in surf:
        for dx, dy, dz in offs:
            out.add((x + dx, y + dy, z + dz))
    out.update(src.v.keys())
    return out


N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def front(v, x, y, zmin=-99):
    zs = [k[2] for k in v.v if k[0] == x and k[1] == y and k[2] >= zmin]
    return max(zs) if zs else None


def decal(v, x, y, col, zmin=0):
    """Pinta o voxel mais à frente da coluna (x, y)."""
    z = front(v, x, y, zmin)
    if z is not None:
        v.set(x, y, z, col)


def hollow(v):
    """Mantido como no-op de propósito: o mesher da engine não faz culling entre voxels ausentes, então
    esvaziar o miolo EXPÕE as faces internas da casca (dobra a contagem). Volumes ficam maciços."""
    return v


def fill_pits(v, col=None, need=5, passes=1):
    """Fecha covas de 1 voxel (célula vazia com >= need vizinhos cheios): menos ruído e menos faces."""
    for _ in range(passes):
        occ = v.v
        cand = {}
        for (x, y, z), c in occ.items():
            for d in N6:
                k = (x + d[0], y + d[1], z + d[2])
                if k not in occ:
                    cand.setdefault(k, []).append(c)
        for k, cs in cand.items():
            if len(cs) >= need:
                occ[k] = col or cs[0]
    return v


def faces(v):
    occ = v.v
    return sum(1 for k in occ for d in N6 if (k[0] + d[0], k[1] + d[1], k[2] + d[2]) not in occ)


_OFFS = [(dx, dy, dz) for dx in range(-2, 3) for dy in range(-2, 3) for dz in range(-2, 3) if (dx or dy or dz)]
_TONE = {}


def tone_c(c, k, hue=0.035):
    key = (c, k, hue)
    r = _TONE.get(key)
    if r is None:
        r = _TONE[key] = tone(c, k, hue) if k else c
    return r


def shade(v, amp=1.0, L=(-0.30, 0.78, 0.55), skip=(), lo=-2.0, hi=1.5, step=0.5, bias=0.0, extra=None, hue=0.035,
          custom=None):
    """Pinta a rampa por voxel a partir da normal estimada (vizinhança 5x5x5).
    Emissivos e cores em `skip` ficam intactos. Metal e folhagem usam menos degraus (faixa de paleta curta)."""
    occ = v.v
    ln = math.sqrt(sum(k * k for k in L))
    L = tuple(k / ln for k in L)
    out = {}
    for (x, y, z), c in occ.items():
        if c[3] in ("emissive", "water") or c in skip:
            continue
        if all((x + d[0], y + d[1], z + d[2]) in occ for d in N6):
            continue
        nx = ny = nz = 0.0
        for dx, dy, dz in _OFFS:
            if (x + dx, y + dy, z + dz) not in occ:
                nx += dx
                ny += dy
                nz += dz
        n = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        k = (nx * L[0] + ny * L[1] + nz * L[2]) / n * 1.3 * amp + bias
        if extra:
            k += extra(x, y, z, c)
        st = step if c[3] == "matte" else 0.75
        k = round(k / st) * st
        k = max(lo, min(hi, k)) if c[3] == "matte" else max(-1.5, min(1.5, k))
        if k and custom and c in custom:
            sh, li = custom[c]
            out[(x, y, z)] = mix(c, sh, min(1.0, -k / 2.0)) if k < 0 else mix(c, li, min(1.0, k / 1.5))
        elif k:
            out[(x, y, z)] = tone_c(c, k, hue)
    occ.update(out)
    return v


def finish(v, **kw):
    shade(v, **kw)
    return hollow(v)


# ----------------------------------------------------------------------------- paleta comum

SKIN = C("#f6c9a0")
SKIN_TAN = C("#e9b088")
SKIN_RAMP = {SKIN: (C("#d48a78"), C("#ffe6c8"))}
BLUSH = C("#f29a8c")
MOUTH = C("#b05a52")
EYE_K = C("#2a1c2e")       # cílio / contorno
PUPIL = C("#1d1a30")
WHITE = C("#ffffff")
GOLD = C("#f2c14a", "metal")
GOLD_D = C("#c28a2a", "metal")
STEEL = C("#93a3bd", "metal")
STEEL_D = C("#66758f", "metal")
LEATHER = C("#8a5632")
LEATHER_D = C("#5c3622")
LEATHER_L = C("#b47a46")
CREAM = C("#f1e4c6")

# medidas do bípede D (voxels)
HIP, NECK = 14, 31          # pernas 14, tronco 17
HEAD_H = 18
SHOULDER = NECK - 2.5
HAND_Y = HIP + 1            # linha inferior da mão


# ----------------------------------------------------------------------------- cabeça

def skull(skin=SKIN, y0=NECK):
    v = Vox()
    sup(v, 0, y0 + 9, 0, 9, 9.3, 8, skin, p=2.7, ymin=y0)
    # rosto achatado: placa em z=6 com borda arredondada (os traços leem limpos)
    for k in [k for k in v.v if k[2] > 6]:
        del v.v[k]
    for x in range(-8, 8):
        for y in range(y0 + 1, y0 + 15):
            f = front(v, x, y)
            if f is not None and 3 <= f < 6 and abs(x + 0.5) <= 7.6:
                for z in range(f, 7):
                    v.set(x, y, z, skin)
    # orelhas
    for s in (1, -1):
        for y in range(y0 + 5, y0 + 9):
            for z in range(-2, 1):
                x = 9 if s > 0 else -10
                if not (y in (y0 + 5, y0 + 8) and z in (-2, 0)):
                    v.set(x, y, z, skin)
    return v


def face(v, iris, brow, y0=NECK, lash_tick=False, eye_w=4, mouth="smile"):
    """Olhos 4x5: cílio, pupila com brilho, íris em degradê; sobrancelha, blush e boca.
    O brilho fica do MESMO lado nos dois olhos (lado da luz), não espelhado."""
    iris_l = tone(iris, 1.6)
    iris_d = tone(iris, -1.2)
    for s in (1, -1):
        xs = list(range(2, 2 + eye_w)) if s > 0 else list(range(-2 - eye_w, -2))
        for i, x in enumerate(xs):
            last = i == eye_w - 1
            decal(v, x, y0 + 9, EYE_K)
            decal(v, x, y0 + 8, WHITE if i == 0 else (iris_d if last else PUPIL))
            decal(v, x, y0 + 7, iris_d if i in (0, eye_w - 1) else PUPIL)
            decal(v, x, y0 + 6, iris)
            decal(v, x, y0 + 5, iris_l if 0 < i else iris)
        decal(v, xs[-1], y0 + 6, iris_l)
        outer = xs[-1] + 1 if s > 0 else xs[0] - 1
        inner = xs[0] if s > 0 else xs[-1]
        if lash_tick:
            decal(v, outer, y0 + 9, EYE_K)
            decal(v, outer, y0 + 10, EYE_K)
        for x in xs:                                         # sobrancelha (levemente arqueada)
            decal(v, x, y0 + (11 if x != inner else 11), brow)
        decal(v, outer, y0 + 12 if False else y0 + 11, brow) if False else None
        decal(v, outer, y0 + 4, BLUSH)
        decal(v, outer + s, y0 + 4, BLUSH)
        decal(v, outer, y0 + 3, BLUSH)
    if mouth == "smile":
        decal(v, -1, y0 + 3, MOUTH)
        decal(v, 0, y0 + 3, MOUTH)
        decal(v, -2, y0 + 4, tone(SKIN, -1.2))
        decal(v, 1, y0 + 4, tone(SKIN, -1.2))
    elif mouth == "o":
        decal(v, -1, y0 + 3, MOUTH)
        decal(v, 0, y0 + 3, MOUTH)
    return v


def hair_shell(sk, col, bang, nape, y0=NECK, thick=1.9, side=None):
    """Cabelo como casca em volta do crânio. bang[x] = linha da franja (altura relativa a y0) na metade
    da frente; nape(x) = altura da nuca na metade de trás."""
    h = Vox()
    for (x, y, z) in dilate(sk, thick):
        if (x, y, z) in sk.v or y < y0:
            continue
        if z >= 0:
            lim = bang(x, z) if callable(bang) else bang.get(x, 12)
        else:
            lim = nape(x, z) if callable(nape) else nape
        if y >= y0 + lim:
            h.set(x, y, z, col)
    return h


# ----------------------------------------------------------------------------- corpo

def torso_base(col, hem=None, w_bot=7.0, w_top=5.9, d_bot=4.7, d_top=4.1, skirt=3, p=3.2):
    """Tronco em pera com a barra da túnica caindo sobre as coxas."""
    t = Vox()
    stack(t, HIP, NECK - 1, lambda u: (0, 0, lerp(w_bot, w_top, u), lerp(d_bot, d_top, u)), col, p)
    # ombros arredondados
    for y, cut in ((NECK - 1, 1.0),):
        for k in [k for k in t.v if k[1] == y and abs(k[0] + 0.5) > w_top - cut]:
            del t.v[k]
    if skirt:
        stack(t, HIP - skirt, HIP - 1, lambda u: (0, 0, lerp(w_bot + 0.9, w_bot + 0.1, u), lerp(d_bot + 0.9, d_bot + 0.1, u)),
              col, p)
        if hem:
            for k in [k for k in t.v if k[1] == HIP - skirt]:
                t.v[k] = hem
    return t


def belt(t, y, col, buckle=GOLD, w=7.0, d=4.7, tall=2):
    for yy in range(y, y + tall):
        tmp = Vox()
        ring(tmp, yy, 0, 0, w + 0.75, d + 0.75, col, 3.2)
        for k in tmp.v:
            if abs(k[0] + 0.5) > w - 1.5 or abs(k[2] + 0.5) > d - 1.5:
                t.v[k] = col
    zf = max(k[2] for k in t.v if k[1] == y)
    if buckle:
        t.box(-2, y - 1, zf, 1, y + tall, zf + 1, buckle)
        t.box(-1, y, zf + 1, 0, y + tall - 1, zf + 1, LEATHER_D)
    return zf


def arm(sleeve, glove, cuff, puff=2.6, fore=2.0, cuff_trim=None, skin_hand=False, sleeve_end=None):
    """Braço esquerdo (x>0): manga bufante, antebraço fino, luva com punho virado e polegar."""
    a = Vox()
    top = NECK - 2
    cx, cz = 8.5, 0.5
    sleeve_end = sleeve_end if sleeve_end is not None else HAND_Y + 5

    def prof(u):
        r = lerp(fore, puff, min(1.0, u * 1.8)) if u < 0.8 else lerp(puff, puff - 0.7, (u - 0.8) / 0.2)
        return (cx - (0.4 if u > 0.8 else 0), cz, r, r)
    stack(a, sleeve_end, top, prof, sleeve, 2.6)
    # luva: punho virado (mais largo), mão arredondada
    stack(a, HAND_Y + 3, sleeve_end - 1, lambda u: (cx, cz, 2.5, 2.5), cuff, 3.0)
    if cuff_trim:
        ring(a, sleeve_end - 1, cx, cz, 2.5, 2.5, cuff_trim, 3.0)
    hand = SKIN if skin_hand else glove
    sup(a, cx, HAND_Y + 1.6, cz, 2.3, 2.2, 2.3, hand, p=2.6)
    a.set(6, HAND_Y + 2, 3, hand)       # polegar
    a.set(6, HAND_Y + 1, 3, hand)
    return a


def leg(pants, boot, boot_cuff, sole, cuff_y=7, toe=5, strap=None, knee=None):
    """Perna esquerda (x>0): coxa curta, bota de cano com dobra, bico arredondado."""
    l = Vox()
    cx, cz = 3.5, 0.5
    stack(l, cuff_y, HIP - 1, lambda u: (cx, cz, 2.5, 2.5), pants, 3.0)
    if knee:
        l.box(2, cuff_y + 2, 2, 4, cuff_y + 3, 2, knee)
    stack(l, 2, cuff_y - 1, lambda u: (cx + 0.2, cz, lerp(2.9, 2.6, u), lerp(2.9, 2.6, u)), boot, 3.0)
    # pé
    sup(l, cx + 0.3, 1.2, cz + 1.4, 3.1, 2.6, 4.2 + (toe - 5) * 0.5, boot, p=2.8, ymin=0)
    for k in [k for k in l.v if k[1] == 0]:
        l.v[k] = sole
    # dobra do cano
    for y in (cuff_y - 1, cuff_y):
        ring(l, y, cx + 0.3, cz, 3.3, 3.3, boot_cuff, 3.0)
    for k in [k for k in l.v if k[1] == cuff_y and abs(k[0] + 0.5 - cx) < 2.3 and abs(k[2] + 0.5 - cz) < 2.3]:
        l.v[k] = pants
    if strap:
        for k in [k for k in l.v if k[1] == 3 and l.v[k] == boot]:
            l.v[k] = strap
        l.set(6, 3, 1, GOLD)
    for k in [k for k in l.v if k[0] < 1]:
        del l.v[k]
    return l


def count_faces(rig):
    return sum(faces(v) for _, v, _, _ in rig.parts)


def make_rig(name, parts, meta_extra=None):
    """parts: lista (nome, Vox, pivô, pai). Calcula meta e imprime orçamento."""
    rig = Rig(name, VS, "biped")
    for n, v, piv, par in parts:
        rig.part(n, v, piv, par)
    b = rig.all_voxels().bounds()
    total = b[4] - b[1] + 1
    head_h = HEAD_H + 2                     # crânio + casca de cabelo (sem tufos, pena ou chapéu)
    body = NECK + head_h
    rig.meta = {"headsTall": round(body / head_h, 2), "legH": HIP, "torsoH": NECK - HIP, "headH": head_h,
                "legFraction": round(HIP / body, 3), "faces": count_faces(rig),
                "note": "cabeças = (pescoço + cabeça com cabelo) / cabeça; tufos, pena e chapéu ficam fora da razão"}
    if meta_extra:
        rig.meta.update(meta_extra)
    print("   ", " ".join(f"{n}={faces(v)}" for n, v, _, _ in rig.parts))
    print(f"  {name}: {b[3]-b[0]+1}x{total}x{b[5]-b[2]+1} vox, {rig.meta['headsTall']} cabeças, "
          f"{total * VS:.2f} u, {rig.meta['faces']} faces")
    return rig


# ----------------------------------------------------------------------------- aventureiro

def sword():
    """Espada vertical na mão direita (x<0): lâmina larga com sulco e fio claro, guarda curva com gema."""
    s = Vox()
    y = HAND_Y
    gem = C("#7ff2ff", "emissive")
    s.box(-9, y - 3, 0, -8, y - 2, 1, GOLD)                 # pomo
    s.box(-9, y - 4, 0, -8, y - 4, 1, GOLD_D)
    s.box(-9, y - 1, 0, -8, y + 3, 1, LEATHER_D)            # punho
    s.box(-9, y + 1, 0, -8, y + 1, 1, LEATHER_L)
    # guarda curva
    for z in range(-4, 6):
        up = 1 if z in (-4, 5) else 0
        s.box(-9, y + 4 + up, z, -8, y + 5 + up, z, GOLD)
    s.box(-10, y + 4, -1, -7, y + 6, 2, GOLD)
    s.box(-10, y + 5, 0, -7, y + 5, 1, gem)
    s.box(-10, y + 4, -1, -7, y + 4, 2, GOLD_D)
    # lâmina: 6 de largura na base, afila
    n = 22
    for i in range(n):
        yy = y + 7 + i
        half = 3 if i < n - 7 else (2 if i < n - 4 else (1 if i < n - 1 else 0))
        if i == 0:
            half = 2
        for z in range(1 - half, half + 1):
            edge = z in (1 - half, half)
            s.box(-9, yy, z, -8, yy, z, C("#c9d6e8", "metal") if edge else STEEL)
        if 1 <= i < n - 8:
            s.box(-9, yy, 0, -8, yy, 1, STEEL_D)             # sulco
    s.box(-9, y + 7 + n, 0, -8, y + 7 + n, 0, C("#c9d6e8", "metal"))
    return s, (-8.0, y + 1.5, 1.0)


def adventurer():
    hair, hair_tip = C("#e2a23c"), C("#ffd873")
    tunic, tunic_trim = C("#22828c"), C("#8fe0cf")
    cape_c, lining = C("#cf3340"), C("#f0b848")
    pants = C("#39415f")
    iris = C("#2fae7c")

    # --- cabeça
    sk = skull()
    bang = {-11: 6, -10: 5, -9: 5, -8: 6, -7: 8, -6: 12, -5: 12, -4: 12, -3: 12, -2: 11, -1: 13, 0: 13, 1: 12,
            2: 12, 3: 12, 4: 12, 5: 12, 6: 10, 7: 7, 8: 6, 9: 5, 10: 5}
    h = hair_shell(sk, hair, bang, lambda x, z: 2 if abs(x + 0.5) < 7 else 3)
    # volume extra no topo/atrás e mechas grandes e legíveis
    sup(h, 0.5, NECK + 15.5, -1.5, 9.8, 5.0, 9.4, hair, p=2.3, ymin=NECK + 12)
    Y = NECK
    for a, m, b, r0 in (((3, Y + 17, 0), (7, Y + 21, -1), (10, Y + 19, -5), 3.0),        # topete varrido
                        ((-3, Y + 17, -2), (-6, Y + 20.5, -4), (-8, Y + 19, -8), 2.8),
                        ((1, Y + 17, -5), (1, Y + 20.5, -8), (2, Y + 18, -12), 2.8),
                        ((8, Y + 11, -4), (11, Y + 9, -6), (11.5, Y + 6, -9), 2.8),      # laterais caindo
                        ((-8, Y + 11, -4), (-11, Y + 9, -6), (-12, Y + 5, -8), 2.8),
                        ((5, Y + 6, -6), (7, Y + 3, -9), (8, Y + 1, -11), 2.8),          # nuca
                        ((-5, Y + 6, -6), (-7, Y + 3, -9), (-7, Y + 2, -11), 2.8),
                        ((0, Y + 8, -7), (0, Y + 4, -10), (-1, Y + 0, -12), 3.0)):
        lock(h, a, m, b, r0, 0.7, hair)
    # franja: três mechas saltando à frente do rosto
    for a, m, b, r0 in (((-5, Y + 14, 6), (-8, Y + 12, 7.5), (-9, Y + 7, 7.5), 2.2),
                        ((5, Y + 14, 6), (8, Y + 12, 7.5), (9.5, Y + 8, 7), 2.2),
                        ((1, Y + 16, 7), (-1, Y + 15, 8.5), (-2, Y + 12.5, 8.5), 2.0)):
        lock(h, a, m, b, r0, 0.8, hair)
    for k in [k for k in h.v if k in sk.v]:
        del h.v[k]
    tmp = h.copy().merge(sk)
    fill_pits(tmp, hair, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == hair)

    def hair_extra(x, y, z, c):
        k = (y - (NECK + 9)) * 0.06                        # pontas escuras, topo claro
        if y in (NECK + 14, NECK + 15) and z > -4:
            k += 0.7                                       # faixa de brilho
        return k
    shade(h, amp=0.75, extra=hair_extra, lo=-1.5)
    shade(sk, amp=0.7, bias=0.2, custom=SKIN_RAMP)
    head = h.copy().merge(sk)
    face(head, iris, tone(hair, -2.2))
    hollow(head)

    # --- tronco
    t = torso_base(tunic, hem=tunic_trim)
    zf = belt(t, HIP + 1, LEATHER_D)
    # gola em V e fecho
    for i in range(4):
        for s in (1, -1):
            x = i if s > 0 else -1 - i
            decal(t, x, NECK - 5 + i, tunic_trim)
    for y in range(HIP + 4, NECK - 5, 3):
        decal(t, 0, y, GOLD)
    # talabarte diagonal (ombro direito -> quadril esquerdo), frente e costas
    for i in range(12):
        x, y = -5 + i, NECK - 2 - i
        for xx in (x, x - 1):
            zf2 = front(t, xx, y)
            if zf2 is not None:
                t.set(xx, y, zf2 + 1, LEATHER)
    t.box(-2, NECK - 6, zf + 0, -1, NECK - 5, zf + 1, GOLD)
    # bolsa no quadril direito + poção no esquerdo
    t.box(-9, HIP - 2, 0, -8, HIP + 1, 4, LEATHER)
    t.box(-9, HIP + 1, 0, -8, HIP + 2, 4, LEATHER_L)
    t.set(-10, HIP + 0, 2, GOLD)
    potion = C("#ff6fa0", "emissive")
    t.box(5, HIP - 2, 5, 6, HIP, 6, potion)
    t.box(5, HIP + 1, 5, 6, HIP + 1, 6, C("#e8d2a8"))
    # bainha no quadril esquerdo, apontando para trás
    for i in range(13):
        y, z = HIP + 3 - i * 0.62, -3 - i * 0.78
        c = GOLD if i in (0, 12) else LEATHER_D
        t.box(8, round(y), round(z), 9, round(y) + 1, round(z), c)
    # cachecol: anel largo + nó e pontas no peito
    sc = Vox()
    sup(sc, 0, NECK - 0.3, 0, 8.2, 2.5, 6.8, cape_c, p=2.6)
    sup(sc, 4.5, NECK - 2.5, 5.2, 2.6, 2.4, 2.2, cape_c, p=2.2)
    for k in [k for k in sc.v if abs(k[0] + 0.5) < 4.5 and abs(k[2] + 0.5) < 3.6 and k[1] >= NECK]:
        del sc.v[k]
    t.merge(sc)
    fill_pits(t, need=4)
    shade(t, amp=0.9, extra=lambda x, y, z, c: (y - HIP - 8) * 0.04 if c == tunic else 0)
    hollow(t)

    # --- capa curta: vermelha por fora, forro dourado, barra rasgada
    cape = Vox()
    ragged = [0, 1, 2, 1, 0, 0, 1, 3, 2, 0, 0, 0, 1, 0, 0, 2, 4, 2, 0, 0, 1, 2, 1, 0]
    ytop, ybot = NECK - 2, 6
    for y in range(ybot, ytop + 1):
        u = (ytop - y) / (ytop - ybot)
        half = lerp(6.5, 10.4, u ** 0.8)
        for x in range(-12, 12):
            ax = abs(x + 0.5)
            if ax > half or y < ybot + ragged[x + 12]:
                continue
            fold = math.sin(x * 0.62 + 0.9)
            z = round(-6.2 - u * 3.6 + (ax / half) ** 2 * (1.6 + 2.2 * u) + 0.8 * fold * u)
            k = round((0.5 * fold - 0.9 * u + 0.3) * 2) / 2
            cape.set(x, y, z, tone_c(cape_c, k))
            cape.set(x, y, z + 1, tone_c(lining, round((0.4 * fold - 0.6 * u) * 2) / 2))
            if y == ybot + ragged[x + 12] or y == ybot:
                cape.set(x, y, z, tone_c(cape_c, -1.0))

    # --- membros
    arm_l = arm(CREAM, LEATHER, LEATHER_L, cuff_trim=GOLD_D)
    for k, c in list(arm_l.v.items()):                      # manga curta da túnica sobre a camisa
        if k[1] >= NECK - 6:
            arm_l.v[k] = tunic_trim if k[1] == NECK - 6 else tunic
    arm_r = arm_l.flipped_x()
    # ombreira em camadas no braço da espada
    pa = Vox()
    sup(pa, 9.6, NECK - 1.2, 0.5, 4.8, 4.0, 4.9, STEEL, p=2.2, ymin=NECK - 3)
    sup(pa, 10.6, NECK - 3.4, 0.5, 3.8, 2.6, 4.2, STEEL_D, p=2.2, ymin=NECK - 6, ymax=NECK - 4)
    for k in list(pa.v):
        if k[1] in (NECK - 3, NECK - 6) and (k[0], k[1] - 1, k[2]) not in pa.v:
            pa.v[k] = GOLD
    for k in [k for k in pa.v if k[0] < 6]:
        del pa.v[k]
    for i, x in enumerate(range(7, 13)):                    # crista
        pa.box(x, NECK + 2 - (i // 3), 0, x, NECK + 3 - (i // 2), 1, GOLD)
    arm_r.merge(pa.flipped_x())
    for a_ in (arm_l, arm_r):
        finish(a_, amp=0.9)
    leg_l = leg(pants, LEATHER, LEATHER_L, LEATHER_D, strap=LEATHER_D, knee=None)
    leg_r = leg_l.flipped_x()
    for l_ in (leg_l, leg_r):
        finish(l_, amp=0.9)
    sw, grip = sword()

    return make_rig("Aventureiro (D)", [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", head, (0, NECK, 0.5), "torso"),
        ("arm_l", arm_l, (8.5, SHOULDER, 0.5), "torso"),
        ("arm_r", arm_r, (-8.5, SHOULDER, 0.5), "torso"),
        ("leg_l", leg_l, (3.5, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.5, HIP, 0.5), None),
        ("weapon", sw, grip, "arm_r"),
        ("cape", cape, (0, NECK - 2, -5.5), "torso"),
    ])


# ----------------------------------------------------------------------------- patrulheira

def bow():
    """Arco recurvo vertical na mão direita; plano YZ, corda atrás."""
    w = Vox()
    wood, wrap = C("#6b4226"), C("#2fa58c")
    y0 = HAND_Y + 2
    n = 17
    tipz = 0
    for s in (1, -1):
        for i in range(n + 1):
            u = i / n
            z = round(3.0 - 6.0 * u ** 1.6 + (3.2 * (u - 0.78) / 0.22 if u > 0.78 else 0))
            y = y0 + s * i
            c = GOLD if i >= n - 1 else (tone_c(wood, 0.5) if i > 2 else wrap)
            w.box(-9, y, z, -8, y, z + 1, c)
            w.box(-9, y, z, -8, y, z, wood if 2 < i < n - 1 else c)
            tipz = z
    for y in range(y0 - n + 1, y0 + n):
        w.set(-9, y, tipz - 1, C("#f3ead2"))
    return w, (-8.0, y0, 1.0)


def ranger():
    hair = C("#c9502c")
    green, green_l = C("#4f9a3e"), C("#b9e06a")
    vest = C("#7b4a2c")
    tights = C("#2f5a4a")
    iris = C("#3d9be8")
    ribbon = C("#35c0b0")
    Y = NECK

    sk = skull()
    bang = {-11: 3, -10: 3, -9: 3, -8: 4, -7: 6, -6: 12, -5: 12, -4: 12, -3: 12, -2: 12, -1: 13, 0: 13, 1: 13,
            2: 12, 3: 12, 4: 12, 5: 12, 6: 10, 7: 8, 8: 5, 9: 3, 10: 3}
    h = hair_shell(sk, hair, bang, lambda x, z: 3)
    sup(h, 0, Y + 15, -1.5, 9.7, 5.0, 9.3, hair, p=2.3, ymin=Y + 12)
    for a, m, b, r0 in (((-3, Y + 16, 6.5), (2, Y + 15.5, 8.5), (8, Y + 12, 7.5), 2.3),     # franja varrida
                        ((-6, Y + 14, 6), (-8.5, Y + 12, 7), (-9, Y + 8, 7), 2.0),
                        ((9, Y + 8, 3), (10, Y + 3, 4), (9, Y - 1, 5), 2.0),             # mechas longas laterais
                        ((-9.5, Y + 8, 3), (-10.5, Y + 3, 4), (-9.5, Y - 1, 5), 2.0),
                        ((0, Y + 17, -4), (0, Y + 15, -9), (2, Y + 9, -10), 3.2)):      # recolhido atrás
        lock(h, a, m, b, r0, 0.9, hair)
    tmp = h.copy().merge(sk)
    fill_pits(tmp, hair, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == hair)
    # faixa na testa
    for k in list(h.v):
        if k[1] in (Y + 13, Y + 14) and k[2] > -2:
            h.v[k] = ribbon
    def hair_extra(x, y, z, c):
        if c != hair:
            return 0
        return (y - (Y + 9)) * 0.06 + (0.7 if y in (Y + 16, Y + 17) and z > -5 else 0)
    shade(h, amp=0.75, extra=hair_extra, lo=-1.5)
    shade(sk, amp=0.7, bias=0.2, custom=SKIN_RAMP)
    head = h.copy().merge(sk)
    face(head, iris, tone(hair, -2.0), lash_tick=True)
    # pena na faixa (lado esquerdo)
    fe = Vox()
    for i in range(11):
        u = i / 10
        x, y, z = 10 + round(1.5 * u), Y + 12 + i, round(-1 - 4.5 * u * u)
        wd = 0 if i in (0, 10) else (2 if 3 <= i <= 8 else 1)
        c = C("#fff4dc") if i < 7 else C("#e8503a")
        fe.box(x, y, z - wd, x, y, z + (1 if wd else 0), c)
        fe.set(x, y, z, tone(c, -1.5))
    head.merge(fe)
    hollow(head)

    # trança (parte 'tail'): gomos alternados, laço e ponta
    tail = Vox()
    n = 9
    for i in range(n):
        u = i / (n - 1)
        cy = Y + 7 - i * 2.6
        cz = -11.5 - 1.2 * math.sin(u * 2.4)
        r = lerp(3.0, 2.1, u)
        sup(tail, 2.5 + (0.9 if i % 2 else -0.9), cy, cz, r, 1.9, r * 0.9, tone_c(hair, 1.0 if i % 2 else 0), p=2.2)
    yb = round(Y + 7 - (n - 1) * 2.6)
    tail.box(0, yb - 2, -15, 4, yb - 1, -11, ribbon)
    tail.box(-2, yb - 3, -14, -1, yb, -13, ribbon)
    tail.box(5, yb - 3, -14, 6, yb, -13, ribbon)
    lock(tail, (2.5, yb - 2, -13), (2.5, yb - 5, -13.5), (3.5, yb - 8, -14.5), 2.0, 0.7, hair)
    finish(tail, amp=0.6)

    # tronco: camisa creme, colete de couro, saiote verde em folhas
    t = torso_base(CREAM, w_bot=6.6, w_top=5.7, skirt=0)
    for k, c in list(t.v.items()):
        if HIP + 3 <= k[1] <= NECK - 5:
            t.v[k] = vest
    for y in range(HIP + 4, NECK - 5, 2):                   # cadarço do colete
        decal(t, -1, y, CREAM)
        decal(t, 0, y, CREAM)
    sk_ = Vox()
    stack(sk_, HIP - 4, HIP + 1, lambda u: (0, 0, lerp(8.6, 6.9, u), lerp(6.3, 4.9, u)), green, 2.8)
    for k in list(sk_.v):                                    # barra em pontas de folha
        a = math.atan2(k[2] + 0.5, k[0] + 0.5)
        tip = abs(math.sin(a * 4))
        if k[1] < HIP - 4 + round(2.2 * tip):
            del sk_.v[k]
    for k in list(sk_.v):
        if (k[0], k[1] - 1, k[2]) not in sk_.v:
            sk_.v[k] = green_l
    t.merge(sk_)
    zf = belt(t, HIP + 1, LEATHER_D, w=6.9, d=4.9)
    # adaga na cintura (atrás, à esquerda) e bolsinha
    t.box(7, HIP - 3, -4, 8, HIP + 1, -3, LEATHER_D)
    t.box(7, HIP + 2, -4, 8, HIP + 2, -3, GOLD)
    t.box(7, HIP + 3, -4, 8, HIP + 5, -3, C("#2a2030"))
    t.box(-9, HIP - 2, 0, -8, HIP + 1, 3, LEATHER_L)
    t.set(-10, HIP, 1, GOLD)
    # capuz baixado: gola volumosa + bolsa do capuz nas costas
    co = Vox()
    sup(co, 0, NECK - 0.6, -0.3, 8.6, 2.9, 7.2, green, p=2.5)
    sup(co, 0, NECK - 3, -6.2, 5.6, 4.2, 3.2, green, p=2.2)
    for k in [k for k in co.v if abs(k[0] + 0.5) < 4.5 and abs(k[2] + 0.5) < 3.6 and k[1] >= NECK]:
        del co.v[k]
    for k in list(co.v):
        if k[1] >= NECK - 1 and (k[0], k[1] + 1, k[2]) not in co.v and (abs(k[0] + 0.5) > 6.5 or abs(k[2]) > 5):
            co.v[k] = green_l
    t.merge(co)
    t.box(-1, NECK - 4, 6, 0, NECK - 3, 7, GOLD)            # broche
    t.set(-1, NECK - 3, 7, C("#ff6a5a", "emissive"))
    t.set(0, NECK - 3, 7, C("#ff6a5a", "emissive"))
    # aljava nas costas (ombro direito) com flechas
    for i in range(15):
        u = i / 14
        cx, cy = lerp(-2.5, -9.0, u), lerp(HIP + 1, NECK, u)
        sup(t, cx, cy, -7.4, 2.3, 1.2, 2.3, LEATHER if 2 < i < 13 else LEATHER_D, p=2.4)
    for dx, dz, c in ((0, 0, C("#f4f0e2")), (1.6, 1, C("#e8503a")), (-1.2, 1.4, C("#f4f0e2"))):
        for i in range(8):
            x, y = round(-9.6 + dx - i * 0.42), NECK + 1 + i
            t.set(x, y, round(-7.6 + dz), WOOD if i < 3 else c)
            if i >= 4:
                t.set(x - 1, y, round(-7.6 + dz), c)
                t.set(x, y, round(-7.6 + dz) - 1, c)
    fill_pits(t, need=4)
    shade(t, amp=0.9)
    hollow(t)

    arm_l = arm(CREAM, LEATHER, LEATHER_D, puff=2.4, cuff_trim=GOLD_D, sleeve_end=HAND_Y + 6)
    for k, c in list(arm_l.v.items()):
        if c == CREAM and k[1] < NECK - 7:
            arm_l.v[k] = SKIN if k[1] > HAND_Y + 6 else LEATHER
    arm_r = arm_l.flipped_x()
    for a_ in (arm_l, arm_r):
        finish(a_, amp=0.9, custom=SKIN_RAMP)
    leg_l = leg(tights, C("#6e4630"), LEATHER_L, LEATHER_D, cuff_y=10, strap=None)
    leg_l.box(2, 6, 3, 4, 6, 3, GOLD_D)
    leg_r = leg_l.flipped_x()
    for l_ in (leg_l, leg_r):
        finish(l_, amp=0.9)
    bw, grip = bow()

    return make_rig("Patrulheira (D)", [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", head, (0, NECK, 0.5), "torso"),
        ("arm_l", arm_l, (8.5, SHOULDER, 0.5), "torso"),
        ("arm_r", arm_r, (-8.5, SHOULDER, 0.5), "torso"),
        ("leg_l", leg_l, (3.5, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.5, HIP, 0.5), None),
        ("weapon", bw, grip, "arm_r"),
        ("tail", tail, (2.5, NECK + 7, -10.0), "head"),
    ])


WOOD = C("#9a6a3c")


# ----------------------------------------------------------------------------- maga

def mage():
    robe, robe_l = C("#5a43b0"), C("#9a86ea")
    hatc = C("#4a3698")
    hair = C("#dcd2f2")
    iris = C("#c24fd0")
    trim = C("#f2c14a")
    rune = C("#7ff2ff", "emissive")
    Y = NECK

    sk = skull()
    bang = lambda x, z: (12 if abs(x + 0.5) < 6.5 else (1 if z < 5 else 4))
    h = hair_shell(sk, hair, bang, lambda x, z: 0)
    for s in (1, -1):                                        # duas mechas longas até o peito
        lock(h, (s * 8.5, Y + 6, 4), (s * 9.5, Y - 1, 5.5), (s * 8, Y - 7, 6), 2.3, 1.0, hair)
        lock(h, (s * 6, Y + 4, -7), (s * 8, Y - 1, -9), (s * 10, Y - 4, -9), 2.8, 0.9, hair)
    lock(h, (0, Y + 4, -8), (0, Y - 1, -10), (0, Y - 5, -10.5), 3.0, 1.0, hair)
    tmp = h.copy().merge(sk)
    fill_pits(tmp, hair, need=4, passes=2)
    h = tmp.cut(lambda x, y, z, c: c == hair)
    shade(h, amp=0.7, lo=-1.5, extra=lambda x, y, z, c: (y - (Y + 6)) * 0.05)
    shade(sk, amp=0.7, bias=0.2, custom=SKIN_RAMP)
    head = h.copy().merge(sk)
    for k in [k for k in head.v if k[1] > Y + 13 + 0.2 * k[2]]:
        del head.v[k]                                        # escondido pelo chapéu
    face(head, iris, tone(hair, -3.0), lash_tick=True, mouth="o")
    hollow(head)

    # chapéu: aba larga inclinada, copa torta e ponta caída com estrela
    hat = Vox()
    by = Y + 12
    for x in range(-15, 15):
        for z in range(-16, 13):
            d = math.hypot((x + 0.5) / 13.6, (z + 2.0) / 13.2)
            if d <= 1.0:
                a = math.atan2(z + 2.0, x + 0.5)
                yy = by + 0.20 * (z + 2) + 1.3 * math.sin(a * 3 + 0.6) * d * d - 1.2 * d * d
                y = round(yy)
                hat.set(x, y, z, hatc)
                if d < 0.82:
                    hat.set(x, y + 1, z, hatc)
                if d > 0.9:
                    hat.set(x, y, z, robe_l)
    n = 9
    for i in range(n + 1):
        u = i / n
        cx, cz = 2.5 * u * u, -1.0 - 7.5 * u * u
        r = lerp(9.6, 3.0, u ** 0.8)
        ring(hat, by + 1 + i, cx, cz, r, r * 0.96, trim if i in (1, 2) else hatc, 2.2)
    lock(hat, (2.5, by + 8.5, -8.5), (4, by + 10.5, -13), (6, by + 6, -16.5), 3.0, 1.1, hatc)
    star = C("#ffe27a", "emissive")
    hat.box(5, by + 3, -18, 7, by + 5, -16, star)
    for d in ((2, 0, 0), (-2, 0, 0), (0, 2, 0), (0, -2, 0), (0, 0, -2)):
        hat.set(6 + d[0], by + 4 + d[1], -17 + d[2], star)
    zf = max(k[2] for k in hat.v if k[1] == by + 2 and k[0] == 0)
    hat.box(-2, by + 1, zf, 1, by + 4, zf + 1, GOLD)         # fivela
    hat.box(-1, by + 2, zf + 1, 0, by + 3, zf + 1, hatc)
    for (x, y) in ((5, by + 7), (-5, by + 6), (-2, by + 10)):  # estrelas bordadas
        decal(hat, x, y, trim, zmin=-30)
    fill_pits(hat, need=4)
    shade(hat, amp=0.9, skip=(robe_l,))
    hollow(hat)

    # tronco + mantelete
    t = torso_base(robe, skirt=0)
    for y in range(HIP + 2, NECK - 3):
        decal(t, -1, y, trim)
        decal(t, 0, y, trim)
    zf = belt(t, HIP + 2, C("#3a2a6e"), buckle=GOLD)
    man = Vox()
    stack(man, NECK - 6, NECK, lambda u: (0, 0, lerp(10.2, 6.5, u ** 0.7), lerp(7.6, 5.4, u ** 0.7)), CREAM, 2.4)
    for k in [k for k in man.v if abs(k[0] + 0.5) < 4.5 and abs(k[2] + 0.5) < 3.6 and k[1] >= NECK]:
        del man.v[k]
    for k in list(man.v):
        if k[1] == NECK - 6:
            man.v[k] = trim
    t.merge(man)
    t.box(-1, NECK - 5, 7, 0, NECK - 3, 8, GOLD)
    t.box(-1, NECK - 4, 8, 0, NECK - 4, 8, rune)
    # livro pendurado no cinto
    t.box(-9, HIP - 3, -1, -8, HIP + 2, 3, C("#b0402f"))
    t.box(-9, HIP - 3, 0, -8, HIP + 2, 0, CREAM)
    t.set(-10, HIP, 2, GOLD)
    fill_pits(t, need=4)
    shade(t, amp=0.9)
    hollow(t)

    # saia do manto (parte 'skirt'): sino com barra dourada e runas
    sk2 = Vox()
    y0s = 2
    stack(sk2, y0s, HIP + 1, lambda u: (0, 0, lerp(10.4, 7.2, u ** 0.75), lerp(8.6, 4.9, u ** 0.75)), robe, 2.6)
    for k, c in list(sk2.v.items()):
        if k[1] in (y0s + 1, y0s + 2):
            sk2.v[k] = trim
        a = math.atan2(k[2] + 0.5, k[0] + 0.5)
        if k[1] > y0s + 3 and abs(math.sin(a * 5 + 0.3)) < 0.16:
            sk2.v[k] = tone_c(robe, -1.0)                    # pregas
    for i in range(10):
        a = i * math.pi / 5 + 0.3
        x, z = round(9.2 * math.cos(a) - 0.5), round(7.4 * math.sin(a) - 0.5)
        for yy in (y0s + 5, y0s + 6):
            zz = [k for k in sk2.v if k[1] == yy]
            best = min(zz, key=lambda k: (k[0] - 10 * math.cos(a)) ** 2 + (k[2] - 9 * math.sin(a)) ** 2)
            sk2.v[best] = rune
    shade(sk2, amp=0.9, extra=lambda x, y, z, c: (y - 8) * 0.05)
    hollow(sk2)

    # braços com manga de sino
    def bell():
        a = Vox()
        cx, cz = 8.5, 0.5
        stack(a, HAND_Y + 3, NECK - 2, lambda u: (cx - 0.4 * u, cz, lerp(3.5, 2.3, u ** 0.6), lerp(3.6, 2.3, u ** 0.6)),
              robe, 2.6)
        ring(a, HAND_Y + 3, cx, cz, 3.5, 3.6, trim, 2.6)
        ring(a, HAND_Y + 3, cx, cz, 2.5, 2.6, C("#2c1f5a"), 2.6)
        sup(a, cx, HAND_Y + 1.6, cz, 2.2, 2.2, 2.2, SKIN, p=2.6)
        return a
    arm_l = bell()
    arm_r = arm_l.flipped_x()
    for a_ in (arm_l, arm_r):
        finish(a_, amp=0.9, custom=SKIN_RAMP)
    shoe = C("#3a2a6e")
    leg_l = leg(C("#f1e4c6"), shoe, trim, C("#241a48"), cuff_y=5, toe=6)
    leg_r = leg_l.flipped_x()
    for l_ in (leg_l, leg_r):
        finish(l_, amp=0.9)

    # cajado (filho rígido de arm_l): madeira torcida, crescente dourado e cristal flutuante
    st = Vox()
    wood = C("#5a3a2a")
    top = Y + 10
    LEAN = 0.27

    def sx(y):
        return round(max(0.0, y - HAND_Y - 2) * LEAN)
    for y in range(0, top + 1):
        st.box(8 + sx(y), y, 3, 9 + sx(y), y, 4, tone_c(wood, 0.5 if (y // 3) % 2 else -0.5))
    for y in (HAND_Y + 6, HAND_Y - 3, top - 2):
        st.box(7 + sx(y), y, 2, 10 + sx(y), y, 5, GOLD)
    st.box(8, 0, 3, 9, 1, 4, GOLD_D)
    shaft = st
    st = Vox()
    for i in range(15):                                      # crescente
        a = -0.6 + i * (math.pi + 1.2) / 14
        x, y = 8.5 + 5.2 * math.cos(a + math.pi) * 1.0, top + 5.5 - 5.2 * math.sin(a + math.pi) * -1.0
        x, y = 8.5 - 5.2 * math.cos(a), top + 5.5 - 5.2 * math.sin(a)
        st.box(round(x - 0.5), round(y - 0.5), 3, round(x - 0.5) + 1, round(y - 0.5) + 1, 4, GOLD)
    cr = C("#7ff2ff", "emissive")
    cr_l = C("#e2fdff", "emissive")
    for dy in range(-4, 5):                                  # octaedro
        r = 2.6 * (1 - abs(dy) / 4.6)
        for x in range(5, 13):
            for z in range(0, 8):
                if abs(x + 0.5 - 9) + abs(z + 0.5 - 4) <= r + 0.3:
                    st.set(x, top + 6 + dy, z, cr_l if (x + 0.5 < 9 and dy > 0) else cr)
    st.set(13, top + 9, 3, cr)
    st.set(4, top + 3, 5, cr)
    st.set(12, top + 2, 6, cr_l)
    st.translate(sx(top) + 1, 0, 0)
    st.merge(shaft)
    shade(st, amp=0.8)
    hollow(st)

    return make_rig("Maga (D)", [
        ("torso", t, (0, HIP, 0.5), None),
        ("head", head, (0, NECK, 0.5), "torso"),
        ("hat", hat, (0, NECK + 13, -1.0), "head"),
        ("arm_l", arm_l, (8.5, SHOULDER, 0.5), "torso"),
        ("arm_r", arm_r, (-8.5, SHOULDER, 0.5), "torso"),
        ("leg_l", leg_l, (3.5, HIP, 0.5), None),
        ("leg_r", leg_r, (-3.5, HIP, 0.5), None),
        ("skirt", sk2, (0, HIP + 1, 0.5), "torso"),
        ("staff", st, (8.5, HAND_Y + 2, 3.5), "arm_l"),
    ])


def build(out):
    print("chars_d:")
    adventurer().save(f"{out}/characters/d/adventurer")
    ranger().save(f"{out}/characters/d/ranger")
    mage().save(f"{out}/characters/d/mage")
