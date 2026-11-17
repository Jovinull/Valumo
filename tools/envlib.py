"""Primitivas de modelagem de ambiente compartilhadas pelas direções voxel (A, B, D).

As formas são paramétricas (ex.: expoente 'p' do superelipsoide: 2 = redondo, 5+ = cúbico) para que
cada direção tenha geometria própria sem reescrever o básico. Tudo é determinístico (hash por posição).
"""
import math

from voxlib import Vox, tone


def hash3(x, y, z, seed=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def ramp(base, spread=1.0):
    """Rampa de 4 tons [sombra, escuro, base, luz] com desvio de matiz."""
    return [tone(base, -1.9 * spread), tone(base, -0.9 * spread), base, tone(base, 0.9 * spread)]


def lobe(v: Vox, cx, cy, cz, rx, ry, rz, col, p=2.0, rough=0.0, seed=0):
    """Superelipsoide |x|^p+|y|^p+|z|^p <= 1 com borda irregular opcional."""
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                d = (abs((x + 0.5 - cx) / rx) ** p + abs((y + 0.5 - cy) / ry) ** p + abs((z + 0.5 - cz) / rz) ** p)
                if d <= 1.0 + (hash3(x, y, z, seed) - 0.5) * rough:
                    v.set(x, y, z, col)
    return v


def toplight(v: Vox, target, rmp, sparkle=0.10, seed=0):
    """Pinta os voxels para os quais target(cor) é verdadeiro com uma rampa 'iluminada de cima':
    topo exposto = luz, logo abaixo = base, fundo = escuro, face inferior exposta = sombra."""
    for (x, y, z), c in list(v.v.items()):
        if not target(c):
            continue
        d = 0
        while d < 5 and (x, y + d + 1, z) in v.v:
            d += 1
        under = (x, y - 1, z) not in v.v
        i = 3 if d == 0 else (2 if d <= 2 else 1)
        if under and d > 0:
            i = 0
        r = hash3(x, y, z, seed)
        if r < sparkle:
            i = max(0, i - 1)
        elif r > 1 - sparkle * 0.6:
            i = min(3, i + 1)
        v.v[(x, y, z)] = rmp[i]
    return v


def trunk(v: Vox, cx, cz, y0, h, r0, r1, bark, lean=(0.0, 0.0), roots=0, seed=0):
    """Tronco de discos empilhados, com inclinação e raízes; estrias verticais mais escuras."""
    dark = tone(bark, -1.1)
    light = tone(bark, 0.7)
    for i in range(h):
        t = i / max(1, h - 1)
        r = r0 + (r1 - r0) * t
        ox, oz = cx + lean[0] * t, cz + lean[1] * t
        for x in range(int(ox - r) - 1, int(ox + r) + 2):
            for z in range(int(oz - r) - 1, int(oz + r) + 2):
                if (x + 0.5 - ox) ** 2 + (z + 0.5 - oz) ** 2 <= r * r:
                    k = hash3(x, 0, z, seed)
                    v.set(x, y0 + i, z, dark if k < 0.28 else (light if k > 0.85 else bark))
    for i in range(roots):
        a = (i + 0.35) * 2 * math.pi / roots
        for s in range(1, int(r0) + 3):
            x, z = round(cx - 0.5 + math.cos(a) * (r0 - 0.3 + s)), round(cz - 0.5 + math.sin(a) * (r0 - 0.3 + s))
            for y in range(max(1, int(r0) + 2 - s)):
                v.set(x, y0 + y, z, dark if (s + y) % 2 else bark)
    return v


def rock(v: Vox, cx, cy, cz, rx, ry, rz, base, seed=0, moss=None, p=2.6, cuts=4, moss_amount=0.55):
    """Pedra facetada: superelipsoide cortado por planos aleatórios, sombreada de cima, com musgo."""
    tmp = Vox()
    lobe(tmp, cx, cy, cz, rx, ry, rz, base, p=p)
    for i in range(cuts):
        a = hash3(i, 1, 0, seed) * 2 * math.pi
        up = 0.2 + hash3(i, 2, 0, seed) * 0.7
        n = (math.cos(a), up, math.sin(a))
        ln = math.sqrt(sum(k * k for k in n))
        n = tuple(k / ln for k in n)
        dist = 0.55 + hash3(i, 3, 0, seed) * 0.3
        for (x, y, z) in list(tmp.v):
            d = ((x + 0.5 - cx) / rx) * n[0] + ((y + 0.5 - cy) / ry) * n[1] + ((z + 0.5 - cz) / rz) * n[2]
            if d > dist:
                del tmp.v[(x, y, z)]
    toplight(tmp, lambda c: True, ramp(base), sparkle=0.14, seed=seed)
    if moss:
        mr = ramp(moss)
        for (x, y, z) in list(tmp.v):
            if (x, y + 1, z) not in tmp.v and hash3(x, 7, z, seed) < moss_amount and (y + 0.5 - cy) / ry > 0.1:
                tmp.v[(x, y, z)] = mr[2 if hash3(x, 9, z, seed) < 0.6 else 3]
    for k in [k for k in tmp.v if k[1] < 0]:
        del tmp.v[k]
    v.merge(tmp)
    return v


def frame_box(v: Vox, x0, y0, z0, x1, y1, z1, fill, edge):
    """Caixa com as 12 arestas em outra cor (caixotes, molduras)."""
    v.box(x0, y0, z0, x1, y1, z1, fill)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                on = (x in (x0, x1)) + (y in (y0, y1)) + (z in (z0, z1))
                if on >= 2:
                    v.set(x, y, z, edge)
    return v


def gable_roof(v: Vox, x0, x1, z0, z1, y0, tiles, ridge, overhang=1, steep=1.0, curve=0.0):
    """Telhado de duas águas com cumeeira ao longo de X. tiles = rampa de 4 tons.
    steep = voxels de subida por voxel de avanço; curve > 0 dá o perfil côncavo 'de conto'."""
    zc = (z0 + z1) / 2.0
    half = (z1 - z0) / 2.0 + overhang
    top = y0
    for z in range(z0 - overhang, z1 + overhang + 1):
        d = abs(z + 0.0 - zc)
        t = 1 - d / half
        hgt = half * steep * (t ** (1 + curve) if t > 0 else 0)
        yt = y0 + int(round(hgt))
        top = max(top, yt)
        for x in range(x0 - overhang, x1 + overhang + 1):
            for y in range(max(y0, yt - 1), yt + 1):
                k = hash3(x // 2, y, z, 5)
                c = tiles[2] if k < 0.6 else (tiles[1] if k < 0.85 else tiles[3])
                if x in (x0 - overhang, x1 + overhang):
                    c = tiles[1]
                v.set(x, y, z, c)
    zr = int(round(zc - 0.5))
    for x in range(x0 - overhang, x1 + overhang + 1):
        v.set(x, top + 1, zr, ridge)
        if (z1 - z0) % 2 == 1:
            v.set(x, top + 1, zr + 1, ridge)
    return top + 1


def gable_fill(v: Vox, x, z0, z1, y0, wall, beam, steep=1.0, curve=0.0, overhang=1):
    """Fecha a empena (triângulo de parede sob o telhado) no plano x."""
    zc = (z0 + z1) / 2.0
    half = (z1 - z0) / 2.0 + overhang
    for z in range(z0, z1 + 1):
        t = 1 - abs(z - zc) / half
        yt = y0 + int(round(half * steep * (t ** (1 + curve))))
        for y in range(y0, yt - 1):
            v.set(x, y, z, beam if (z in (z0, z1) or abs(z - zc) < 1) else wall)
    return v
