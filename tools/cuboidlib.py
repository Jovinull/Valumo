"""Biblioteca da Direção C — cuboides texturizados com pixel art (somente stdlib).

Convenções (iguais às da engine):
  * Y para cima, +Z = frente do modelo, braço direito em x < 0. Coordenadas em TEXELS (1 texel =
    1/32 u), contínuas: um cuboide é (x, y, z, w, h, d) a partir do canto mínimo.
  * Um pixel é o NOME de uma cor da paleta mestra (ou None = transparente). Nada fora da paleta
    chega ao atlas; sombrear/clarear é andar um degrau na rampa (`tone`), nunca multiplicar RGB.
  * Orientação das faces (como CuboidMesh.java): nas laterais o topo da imagem é +Y e a imagem é
    vista de FORA da face; em +Y o topo da imagem é -Z (costas); em -Y o topo da imagem é +Z.

Peças:
  Canvas / spr()      desenho por grade ASCII e por regras (bordas, dithering ordenado, padrões)
  box()               um cuboide com material por regra + faces desenhadas à mão
  prism()             extrusão de um sprite (silhueta recortada em retângulos, sem alfa "oco")
  Atlas               empacotador em prateleiras, 1 px de sangria em volta de cada região
  CuboidRig           rig.json (type cuboid) + atlas.png
  CuboidPropSet       props.json + atlas.png
"""
from __future__ import annotations

import json
import math
import os
import struct
import sys
import zlib

from voxlib import write_png

TEXEL = 1 / 32

# ----------------------------------------------------------------------------- paleta mestra
# Rampas do escuro para o claro. As sombras puxam para o azul/violeta e as luzes para o amarelo.
RAMPS = {
    "skin": ["#a9654f", "#db9a6e", "#f2c08f", "#fddfb9"],
    "brown": ["#432a33", "#6b3f2e", "#96592f", "#c07f3e", "#e3ad5f"],
    "red": ["#8f2a45", "#cc3d3d", "#f0664a"],
    "pink": ["#d9608f", "#f7a3c0"],
    "gold": ["#c07a2a", "#f2b93b", "#ffe37e"],
    "green": ["#1f4a47", "#2f7049", "#4d9c44", "#76ba48", "#b4dc62"],
    "blue": ["#28346e", "#3257ad", "#4585dc", "#7ebcf5"],
    "cyan": ["#3aa7bd", "#7fe3dc", "#d2fbf0"],
    "purple": ["#37275e", "#5a3f9c", "#8562cf", "#b99aea"],
    "grey": ["#3f4460", "#646e8a", "#94a1b5", "#c5d0dc", "#eef3f6"],
    "sand": ["#b39a66", "#d9c58a", "#f2e6b4"],
    "dirt": ["#5e4136", "#855b3d", "#a97a4c"],
}
SINGLES = {"ink": "#2b2137", "blush": "#ef8f7a", "white": "#ffffff"}


def _hex(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


PALETTE: dict[str, tuple] = {}
_RAMP_OF: dict[str, tuple] = {}
for _r, _cols in RAMPS.items():
    _names = [f"{_r}{i}" for i in range(len(_cols))]
    for _i, _c in enumerate(_cols):
        PALETTE[_names[_i]] = _hex(_c)
        _RAMP_OF[_names[_i]] = (_names, _i)
for _n, _c in SINGLES.items():
    PALETTE[_n] = _hex(_c)


def tone(name, n):
    """n degraus na rampa (negativo = mais escuro). Abaixo do fim da rampa vira 'ink'."""
    if name is None or n == 0 or name not in _RAMP_OF:
        return name
    names, i = _RAMP_OF[name]
    j = i + n
    if j < 0:
        return "ink"
    return names[min(j, len(names) - 1)]


def hsh(x, y, seed=0):
    """Hash inteiro determinístico (para variação ORDENADA de padrões, não ruído borrado)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


# ----------------------------------------------------------------------------- canvas

class Canvas:
    __slots__ = ("w", "h", "p")

    def __init__(self, w, h, fill=None):
        self.w, self.h = int(w), int(h)
        self.p = [[fill] * self.w for _ in range(self.h)]

    def copy(self):
        c = Canvas(self.w, self.h)
        c.p = [row[:] for row in self.p]
        return c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[y][x]
        return None

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c
        return self

    def rect(self, x, y, w, h, c):
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                self.p[yy][xx] = c
        return self

    def hline(self, y, c, x0=0, x1=None):
        return self.rect(x0, y, (self.w if x1 is None else x1) - x0, 1, c)

    def vline(self, x, c, y0=0, y1=None):
        return self.rect(x, y0, 1, (self.h if y1 is None else y1) - y0, c)

    def blit(self, src, x=0, y=0):
        for yy in range(src.h):
            for xx in range(src.w):
                c = src.p[yy][xx]
                if c is not None:
                    self.set(x + xx, y + yy, c)
        return self

    def crop(self, x, y, w, h):
        c = Canvas(w, h)
        for yy in range(h):
            for xx in range(w):
                c.p[yy][xx] = self.get(x + xx, y + yy)
        return c

    def flip_h(self):
        c = Canvas(self.w, self.h)
        c.p = [row[::-1] for row in self.p]
        return c

    def flip_v(self):
        c = Canvas(self.w, self.h)
        c.p = [row[:] for row in self.p[::-1]]
        return c

    def rot90(self):
        """Gira 90° no sentido horário."""
        c = Canvas(self.h, self.w)
        for y in range(self.h):
            for x in range(self.w):
                c.p[x][self.h - 1 - y] = self.p[y][x]
        return c

    def tile(self, w, h, ox=0, oy=0):
        c = Canvas(w, h)
        for y in range(h):
            for x in range(w):
                c.p[y][x] = self.p[(y + oy) % self.h][(x + ox) % self.w]
        return c

    # --- regras de pixel art
    def tone_rect(self, x, y, w, h, n):
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                self.p[yy][xx] = tone(self.p[yy][xx], n)
        return self

    def edges(self, top=0, bottom=0, left=0, right=0):
        """Contorno interno de 1 px: desloca o tom das bordas (cantos recebem o mais escuro)."""
        done = {}
        for (cond, n) in ((lambda x, y: y == 0, top), (lambda x, y: y == self.h - 1, bottom),
                          (lambda x, y: x == 0, left), (lambda x, y: x == self.w - 1, right)):
            if n == 0:
                continue
            for y in range(self.h):
                for x in range(self.w):
                    if cond(x, y):
                        done[(x, y)] = min(done.get((x, y), 9), n)
        for (x, y), n in done.items():
            self.p[y][x] = tone(self.p[y][x], n)
        return self

    def replace(self, a, b):
        for row in self.p:
            for i, c in enumerate(row):
                if c == a:
                    row[i] = b
        return self

    def remap(self, mapping):
        for row in self.p:
            for i, c in enumerate(row):
                if c in mapping:
                    row[i] = mapping[c]
        return self

    def checker(self, x, y, w, h, c, phase=0):
        """Dithering ordenado 50%: pinta c nas casas (x+y) pares do retângulo."""
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                if (xx + yy + phase) % 2 == 0:
                    self.p[yy][xx] = c
        return self

    def tone_checker(self, x, y, w, h, n, phase=0):
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                if (xx + yy + phase) % 2 == 0:
                    self.p[yy][xx] = tone(self.p[yy][xx], n)
        return self

    def avg(self):
        r = g = b = n = 0
        for row in self.p:
            for c in row:
                if c is not None:
                    cr, cg, cb = PALETTE[c]
                    r, g, b, n = r + cr, g + cg, b + cb, n + 1
        if n == 0:
            return None
        return [round(r / n), round(g / n), round(b / n)]

    def opaque(self):
        return sum(1 for row in self.p for c in row if c is not None)

    def key(self):
        return (self.w, self.h, tuple(tuple(r) for r in self.p))


def spr(rows, legend, **extra):
    """Canvas a partir de uma grade ASCII. legend: 'a=nome b=nome' ou dict; '.' = transparente."""
    if isinstance(legend, str):
        legend = dict(tok.split("=") for tok in legend.split())
    legend = dict(legend, **extra)
    if isinstance(rows, str):
        rows = [r.strip() for r in rows.strip().splitlines()]
    w = len(rows[0])
    c = Canvas(w, len(rows))
    for y, row in enumerate(rows):
        if len(row) != w:
            raise ValueError(f"linha {y} com {len(row)} colunas (esperado {w}): {row!r}")
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            name = legend[ch]
            if name not in PALETTE:
                raise ValueError(f"cor fora da paleta: {name}")
            c.p[y][x] = name
    return c


def fill(w, h, base, top=0, bottom=0, left=0, right=0):
    """Face lisa com deslocamento de tom nas bordas (regra do contorno interno)."""
    return Canvas(max(1, int(round(w))), max(1, int(round(h))), base).edges(top, bottom, left, right)


# ----------------------------------------------------------------------------- padrões por regra

def planks(w, h, base, size=5, vertical=False, seed=0, joints=True):
    """Tábuas: frisos 1 px mais escuros, luz no topo de cada tábua, veios curtos e emendas alternadas."""
    if vertical:
        return planks(h, w, base, size, False, seed, joints).rot90()
    c = Canvas(w, h, base)
    for y in range(h):
        k, r = divmod(y, size)
        if r == size - 1:
            c.hline(y, tone(base, -1))
            continue
        if r == 0 and size >= 4:
            c.hline(y, tone(base, 1))
        for x in range(w):
            hv = hsh(x // 3, k, seed)
            if r not in (0, size - 1) and hv % 11 == 0 and r == 1 + (hv >> 4) % max(1, size - 2):
                for i in range(2 + (hv >> 7) % 3):
                    c.set(x + i, y, tone(base, -1))
        if joints:
            jx = (hsh(k, 7, seed) % max(4, w - 2)) + 1
            if w > 10 and r != size - 1:
                c.set(jx, y, tone(base, -1))
    return c


def bricks(w, h, base, bw=8, bh=4, seed=0, mortar=-1):
    """Pedras/tijolos assentados: junta escura, luz no canto superior esquerdo, variação por peça."""
    c = Canvas(w, h, base)
    for y in range(h):
        row, ry = divmod(y, bh)
        off = (bw // 2) * (row % 2) + (hsh(row, 3, seed) % 2)
        for x in range(w):
            col, rx = divmod(x + off, bw)
            v = hsh(col, row, seed) % 7
            n = 1 if v == 0 else (-1 if v == 1 else 0)
            t = tone(base, n)
            if ry == bh - 1 or rx == bw - 1:
                t = tone(base, mortar - 1 if n < 0 else mortar)
            elif ry == 0 and rx < bw - 2 and n >= 0:
                t = tone(base, n + 1)
            c.p[y][x] = t
    return c


def bark(w, h, base, seed=0):
    """Casca: sulcos verticais interrompidos, mais escuros à direita (lado da sombra)."""
    c = Canvas(w, h, base)
    for x in range(w):
        kind = hsh(x, 0, seed) % 4
        for y in range(h):
            seg = hsh(x, (y + hsh(x, 1, seed) % 5) // 5, seed)
            if kind == 0 and seg % 4 != 0:
                c.p[y][x] = tone(base, -1)
            elif kind == 1 and seg % 5 == 0:
                c.p[y][x] = tone(base, 1)
    if w >= 4:
        c.tone_rect(w - 1, 0, 1, h, -1)
    return c


LEAF_CLUMP = [
    "02333210",
    "23322221",
    "23222211",
    "22221211",
    "12111110",
    "01100100",
]


def leaves(w, h, ramp, seed=0, lift=0, cut="", grad=True):
    """Folhagem: tufos em escama de 8x6 (luz em cima/esquerda, sombra embaixo, fresta escura entre
    eles), fileiras deslocadas, um em cada quatro tufos um tom abaixo/acima; gradiente vertical em
    degraus com transição em xadrez; recorte por alfa nas bordas pedidas em `cut` (t, b, l, r)."""
    w, h = max(1, int(round(w))), max(1, int(round(h)))
    c = Canvas(w, h)
    ox, oy = hsh(seed, 1) % 8, hsh(seed, 2) % 6
    for y in range(h):
        row = (y + oy) // 6
        sh = (4 if row % 2 else 0) + hsh(row, 5, seed) % 3
        for x in range(w):
            col, cx = divmod(x + ox + sh, 8)
            lv = int(LEAF_CLUMP[(y + oy) % 6][cx]) + lift
            v = hsh(col, row, seed) % 4
            if v == 0 and lv > 0:
                lv -= 1
            elif v == 1 and lv >= 2:
                lv += 1
            if grad and h >= 6:
                f = y / (h - 1)
                if f < 0.18 or (f < 0.30 and (x + y) % 2 == 0):
                    lv += 1
                elif f > 0.84 or (f > 0.70 and (x + y) % 2 == 0):
                    lv -= 1
            c.p[y][x] = ramp[max(0, min(len(ramp) - 1, lv))]
    scallop = (0, 1, 2, 2, 1, 0, 0, 1, 1, 0, 2, 1)
    for side in cut:
        n = w if side in "tb" else h
        ph = hsh(seed, ord(side)) % 12
        for i in range(n):
            dep = scallop[(i + ph) % 12]
            if i < 2 or i >= n - 2:
                dep = max(dep, 1 if (i == 0 or i == n - 1) else 0)
            for k in range(dep):
                if side == "t":
                    c.set(i, k, None)
                elif side == "b":
                    c.set(i, h - 1 - k, None)
                elif side == "l":
                    c.set(k, i, None)
                else:
                    c.set(w - 1 - k, i, None)
    return c


def shingles(w, h, base, size=4, seed=0):
    """Telhas em escama: fileiras deslocadas, borda inferior escura, luz no topo da fileira."""
    c = Canvas(w, h, base)
    for y in range(h):
        row, ry = divmod(y, size)
        off = (size if row % 2 else 0)
        for x in range(w):
            col, rx = divmod(x + off, size * 2)
            v = hsh(col, row, seed) % 6
            t = tone(base, 1 if v == 0 else 0)
            if ry == size - 1:
                t = tone(base, -1)
            elif rx == size * 2 - 1 and ry >= 1:
                t = tone(base, -1)
            elif ry == 0:
                t = tone(base, 1)
            c.p[y][x] = t
    return c


# ----------------------------------------------------------------------------- cuboides

FACES = ("px", "nx", "py", "ny", "pz", "nz")


class Cub:
    __slots__ = ("x", "y", "z", "w", "h", "d", "faces", "rot", "origin")

    def __init__(self, x, y, z, w, h, d, faces, rot=None, origin=None):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.faces = faces
        self.rot = rot
        self.origin = origin if origin is not None else ((x + w / 2, y + h / 2, z + d / 2) if rot else None)

    def corners(self):
        pts = []
        for i in (0, 1):
            for j in (0, 1):
                for k in (0, 1):
                    pts.append(self._xf((self.x + i * self.w, self.y + j * self.h, self.z + k * self.d)))
        return pts

    def _xf(self, p):
        if not self.rot:
            return p
        ox, oy, oz = self.origin
        x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
        rx, ry, rz = (math.radians(a) for a in self.rot)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        return (x + ox, y + oy, z + oz)


def _dim(v):
    return max(1, int(round(v)))


def face_dims(w, h, d):
    return {"px": (_dim(d), _dim(h)), "nx": (_dim(d), _dim(h)), "py": (_dim(w), _dim(d)),
            "ny": (_dim(w), _dim(d)), "pz": (_dim(w), _dim(h)), "nz": (_dim(w), _dim(h))}


def M(base, top=0, bottom=-1, left=0, right=0, up=0, down=-1):
    """Material liso por regra: laterais com bordas deslocadas; topo `up` degraus; base `down`."""
    def mat(face, w, h):
        if face == "py":
            return fill(w, h, tone(base, up))
        if face == "ny":
            return fill(w, h, tone(base, down))
        return fill(w, h, base, top, bottom if h >= 3 else 0, left, right)
    return mat


def box(x, y, z, w, h, d, mat, faces=None, skip="", rot=None, origin=None, free=False):
    """Cuboide. mat: nome de cor ou callable(face, w, h) -> Canvas; faces: {face: Canvas} desenhadas à mão.
    skip: faces a omitir, ex. "ny nz"."""
    if isinstance(mat, str):
        mat = M(mat)
    dims = face_dims(w, h, d)
    out = {}
    skip = skip.split()
    for f in FACES:
        if f in skip:
            continue
        if faces and f in faces:
            cv = faces[f]
            if not free and (cv.w, cv.h) != dims[f]:
                raise ValueError(f"face {f}: canvas {cv.w}x{cv.h}, esperado {dims[f][0]}x{dims[f][1]}")
        else:
            cv = mat(f, *dims[f])
        out[f] = cv
    return Cub(x, y, z, w, h, d, out, rot, origin)


def _rects(s: Canvas):
    """Decompõe a máscara do sprite em retângulos (corridas por linha, fundidas na vertical)."""
    open_runs, out = {}, []
    for j in range(s.h + 1):
        runs = []
        if j < s.h:
            i = 0
            while i < s.w:
                if s.p[j][i] is not None:
                    i0 = i
                    while i < s.w and s.p[j][i] is not None:
                        i += 1
                    runs.append((i0, i - i0))
                else:
                    i += 1
        nxt = {}
        for r in runs:
            nxt[r] = open_runs.pop(r, j)
        for r, j0 in open_runs.items():
            out.append((r[0], j0, r[1], j - j0))
        open_runs = nxt
    return sorted(out, key=lambda r: (r[1], r[0]))


def prism(sprite, depth, at, axis="z", back=None, rim=-1, rim_color=None, rot=None, origin=None, faces=None):
    """Extrusão de um sprite: a silhueta vira geometria (sem buracos de alfa).
    axis 'z': sprite visto de frente (+Z), canto inferior esquerdo em at=(x, y, z), z cresce `depth`.
    axis 'x': sprite visto de +X (esquerda da imagem = frente, +Z); at = (x, y, z mínimos).
    axis 'y': sprite visto de cima (topo da imagem = -Z); at = (x, y, z mínimos).
    back: sprite do lado oposto (como visto desse lado). rim: degraus de tom das faces de borda."""
    x0, y0, z0 = at
    W, H = sprite.w, sprite.h
    if back is None:
        back = sprite.flip_v() if axis == "y" else sprite.flip_h()

    def edge(c):
        return rim_color or tone(c, rim)

    cubs = []
    dd = _dim(depth)
    for (i0, j0, w, h) in _rects(sprite):
        fr = sprite.crop(i0, j0, w, h)
        f = {}
        if axis == "z":
            f["pz"] = fr
            f["nz"] = back.crop(W - i0 - w, j0, w, h)
            a, b, t, u = Canvas(dd, h), Canvas(dd, h), Canvas(w, dd), Canvas(w, dd)
            for r in range(h):
                a.hline(r, edge(sprite.p[j0 + r][i0 + w - 1]))
                b.hline(r, edge(sprite.p[j0 + r][i0]))
            for c in range(w):
                t.vline(c, edge(sprite.p[j0][i0 + c]))
                u.vline(c, edge(tone(sprite.p[j0 + h - 1][i0 + c], -1)))
            f.update(px=a, nx=b, py=t, ny=u)
            for k, cv in (faces or {}).items():   # faces laterais desenhadas (só no eixo z)
                f[k] = cv.crop(0, j0, dd, h) if k in ("px", "nx") else cv.crop(i0, 0, w, dd)
            cub = Cub(x0 + i0, y0 + H - j0 - h, z0, w, h, depth, f, rot, origin)
        elif axis == "x":
            f["px"] = fr
            f["nx"] = back.crop(W - i0 - w, j0, w, h)
            a, b, t, u = Canvas(dd, h), Canvas(dd, h), Canvas(dd, w), Canvas(dd, w)
            for r in range(h):
                a.hline(r, edge(sprite.p[j0 + r][i0]))
                b.hline(r, edge(sprite.p[j0 + r][i0 + w - 1]))
            for c in range(w):
                t.hline(c, edge(sprite.p[j0][i0 + w - 1 - c]))
                u.hline(c, edge(tone(sprite.p[j0 + h - 1][i0 + c], -1)))
            f.update(pz=a, nz=b, py=t, ny=u)
            cub = Cub(x0, y0 + H - j0 - h, z0 + W - i0 - w, depth, h, w, f, rot, origin)
        else:
            f["py"] = fr
            f["ny"] = back.crop(i0, H - j0 - h, w, h)
            a, b, t, u = Canvas(w, dd), Canvas(w, dd), Canvas(h, dd), Canvas(h, dd)
            for c in range(w):
                a.vline(c, edge(sprite.p[j0 + h - 1][i0 + c]))
                b.vline(c, edge(sprite.p[j0][i0 + w - 1 - c]))
            for c in range(h):
                t.vline(c, edge(sprite.p[j0 + h - 1 - c][i0 + w - 1]))
                u.vline(c, edge(sprite.p[j0 + c][i0]))
            f.update(pz=a, nz=b, px=t, nx=u)
            cub = Cub(x0 + i0, y0, z0 + j0, w, depth, h, f, rot, origin)
        if rot and origin is None:
            raise ValueError("prism com rot exige origin")
        cubs.append(cub)
    return cubs


def mirror_x(cubs):
    """Espelha uma lista de cuboides em X (x -> -x), espelhando também as texturas."""
    out = []
    for c in cubs:
        f = {}
        for k, cv in c.faces.items():
            k2 = {"px": "nx", "nx": "px"}.get(k, k)
            f[k2] = cv.flip_h()
        rot = (c.rot[0], -c.rot[1], -c.rot[2]) if c.rot else None
        org = (-c.origin[0], c.origin[1], c.origin[2]) if c.origin else None
        out.append(Cub(-(c.x + c.w), c.y, c.z, c.w, c.h, c.d, f, rot, org))
    return out


def moved(cubs, dx=0, dy=0, dz=0):
    out = []
    for c in cubs:
        org = (c.origin[0] + dx, c.origin[1] + dy, c.origin[2] + dz) if c.origin else None
        out.append(Cub(c.x + dx, c.y + dy, c.z + dz, c.w, c.h, c.d, c.faces, c.rot, org))
    return out


def turned(cubs, quarter=1):
    """Gira a lista 90° em torno de Y (x,z) -> (z,-x), `quarter` vezes. Só para cuboides sem rot."""
    out = cubs
    for _ in range(quarter % 4):
        nxt = []
        for c in out:
            if c.rot:
                raise ValueError("turned() não suporta cuboides com rot")
            f = {}
            m = {"pz": "px", "px": "nz", "nz": "nx", "nx": "pz"}
            for k, cv in c.faces.items():
                if k == "py":
                    f[k] = cv.rot90().rot90().rot90()
                elif k == "ny":
                    f[k] = cv.rot90()
                else:
                    f[m[k]] = cv
            nxt.append(Cub(c.z, c.y, -(c.x + c.w), c.d, c.h, c.w, f))
        out = nxt
    return out


def bounds(cubs):
    pts = [p for c in cubs for p in c.corners()]
    return ([min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)])


# ----------------------------------------------------------------------------- atlas

class Atlas:
    """Empacotador em prateleiras. Cada região ganha 1 px de sangria (borda replicada) em volta,
    então há 2 px entre regiões vizinhas: a amostragem nearest nunca vaza para o vizinho."""

    def __init__(self):
        self.items: dict = {}
        self.pos: dict = {}
        self.size = (0, 0)

    def add(self, cv: Canvas):
        k = cv.key()
        self.items.setdefault(k, cv)
        return k

    def pack(self):
        order = sorted(self.items.items(),
                       key=lambda kv: (-kv[1].h, -kv[1].w, tuple(tuple(c or "" for c in r) for r in kv[0][2])))
        area = sum((cv.w + 2) * (cv.h + 2) for _, cv in order)
        maxw = max(cv.w + 2 for _, cv in order)
        W = 32
        while W < maxw or W * W < area * 1.08:
            W *= 2
        if W * W >= area * 2.3 and W // 2 >= maxw:
            W //= 2
        x = y = shelf = 0
        for k, cv in order:
            if x + cv.w + 2 > W:
                x, y, shelf = 0, y + shelf, 0
            self.pos[k] = (x + 1, y + 1)
            x += cv.w + 2
            shelf = max(shelf, cv.h + 2)
        H = y + shelf
        H = (H + 3) // 4 * 4
        self.size = (W, H)
        return self

    def rect(self, k):
        x, y = self.pos[k]
        return [x, y, k[0], k[1]]

    def save(self, path):
        W, H = self.size
        buf = bytearray(W * H * 4)
        for k, cv in self.items.items():
            x0, y0 = self.pos[k]
            for yy in range(-1, cv.h + 1):
                sy = min(max(yy, 0), cv.h - 1)
                row = cv.p[sy]
                for xx in range(-1, cv.w + 1):
                    c = row[min(max(xx, 0), cv.w - 1)]
                    if c is None:
                        continue
                    o = ((y0 + yy) * W + x0 + xx) * 4
                    r, g, b = PALETTE[c]
                    buf[o], buf[o + 1], buf[o + 2], buf[o + 3] = r, g, b, 255
        write_png(path, W, H, bytes(buf))


def _r(v):
    return round(v, 4)


def cubs_json(cubs, atlas: Atlas):
    out = []
    for c in cubs:
        faces, flats = {}, {}
        tot, n = [0, 0, 0], 0
        for f in FACES:
            cv = c.faces.get(f)
            if cv is None:
                continue
            a = cv.avg()
            if a is None:
                continue
            faces[f] = atlas.rect(cv.key())
            flats[f] = a
            k = cv.opaque()
            tot = [tot[i] + a[i] * k for i in range(3)]
            n += k
        if not faces:
            continue
        d = {"from": [_r(c.x), _r(c.y), _r(c.z)], "size": [_r(c.w), _r(c.h), _r(c.d)],
             "flat": [round(t / n) for t in tot], "flatFaces": flats, "faces": faces}
        if c.rot:
            d["rot"] = [_r(a) for a in c.rot]
            d["origin"] = [_r(a) for a in c.origin]
        out.append(d)
    return out


def _dump(doc, path):
    """JSON compacto: um cuboide por linha."""
    txt = json.dumps(doc, separators=(",", ":"))
    txt = txt.replace('{"from"', '\n{"from"').replace('"parts":[', '"parts":[\n').replace('{"name"', '\n{"name"')
    with open(path, "w") as f:
        f.write(txt + "\n")


class CuboidRig:
    """Personagem/criatura de cuboides: partes rígidas com pivôs (em texels, espaço do modelo)."""

    def __init__(self, name, anim="biped", meta=None, texel=TEXEL):
        self.name, self.anim, self.meta, self.texel = name, anim, meta or {}, texel
        self.parts = []

    def part(self, name, cubs, pivot, parent=None):
        cubs = [c for c in cubs if c is not None]
        if cubs:
            self.parts.append((name, cubs, [float(p) for p in pivot], parent))
        return self

    def count(self):
        return sum(len(p[1]) for p in self.parts)

    def body_bounds(self, exclude=("weapon",)):
        return bounds([c for n, cubs, _, _ in self.parts if n not in exclude for c in cubs])

    def save(self, directory, exclude=("weapon",)):
        os.makedirs(directory, exist_ok=True)
        atlas = Atlas()
        for _, cubs, _, _ in self.parts:
            for c in cubs:
                for cv in c.faces.values():
                    if cv.opaque():
                        atlas.add(cv)
        atlas.pack().save(os.path.join(directory, "atlas.png"))
        lo, hi = self.body_bounds(exclude)
        meta = dict(self.meta, cuboids=self.count(), atlas=list(atlas.size))
        doc = {"name": self.name, "type": "cuboid", "atlas": "atlas.png", "voxelSize": self.texel,
               "anim": self.anim,
               "sizeVoxels": [math.ceil(hi[0] - lo[0]), math.ceil(hi[1]), math.ceil(hi[2] - lo[2])],
               "meta": meta,
               "parts": [{"name": n, "parent": parent, "pivot": pivot, "cuboids": cubs_json(cubs, atlas)}
                         for n, cubs, pivot, parent in self.parts]}
        _dump(doc, os.path.join(directory, "rig.json"))
        return doc


class CuboidPropSet:
    """Modelos estáticos de environment/c: props.json + um único atlas.png."""

    def __init__(self, directory, texel=TEXEL):
        self.directory, self.texel = directory, texel
        self.entries = {}

    def add(self, name, cubs):
        self.entries[name] = [c for c in cubs if c is not None]

    def save(self):
        os.makedirs(self.directory, exist_ok=True)
        atlas = Atlas()
        for cubs in self.entries.values():
            for c in cubs:
                for cv in c.faces.values():
                    if cv.opaque():
                        atlas.add(cv)
        atlas.pack().save(os.path.join(self.directory, "atlas.png"))
        doc = {}
        for name in sorted(self.entries):
            cubs = self.entries[name]
            lo, hi = bounds(cubs)
            doc[name] = {"voxelSize": self.texel,
                         "sizeVoxels": [math.ceil(hi[0] - lo[0]), math.ceil(hi[1]), math.ceil(hi[2] - lo[2])],
                         "cuboids": cubs_json(cubs, atlas)}
        _dump(doc, os.path.join(self.directory, "props.json"))
        return atlas.size


def save_strip(path, tiles):
    """Faixa horizontal de tiles quadrados (terreno)."""
    t = tiles[0].w
    buf = bytearray(t * len(tiles) * t * 4)
    for i, cv in enumerate(tiles):
        assert cv.w == t and cv.h == t
        for y in range(t):
            for x in range(t):
                r, g, b = PALETTE[cv.p[y][x]]
                o = (y * t * len(tiles) + i * t + x) * 4
                buf[o], buf[o + 1], buf[o + 2], buf[o + 3] = r, g, b, 255
    write_png(path, t * len(tiles), t, bytes(buf))


# ----------------------------------------------------------------------------- utilitário de revisão

def _read_png(path):
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    return w, h, [raw[y * (w * 4 + 1) + 1:(y + 1) * (w * 4 + 1)] for y in range(h)]


def upscale(src, dst, k=6, tile_x=1, tile_y=1):
    """Amplia (nearest) um PNG gravado por esta biblioteca sobre fundo xadrez — para revisar o atlas.
    tile_x/tile_y repetem a imagem (para conferir se os tiles ladrilham)."""
    w, h, rows = _read_png(src)
    W, H = w * k * tile_x, h * k * tile_y
    buf = bytearray(W * H * 4)
    for Y in range(H):
        y = (Y // k) % h
        row = rows[y]
        for X in range(W):
            x = (X // k) % w
            r, g, b, a = row[x * 4:x * 4 + 4]
            if a < 128:
                r = g = b = 70 if ((X // k) + (Y // k)) % 2 else 90
            o = (Y * W + X) * 4
            buf[o], buf[o + 1], buf[o + 2], buf[o + 3] = r, g, b, 255
    write_png(dst, W, H, bytes(buf))


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "upscale":
        upscale(sys.argv[2], sys.argv[3], *(int(a) for a in sys.argv[4:]))
    else:
        print("uso: cuboidlib.py upscale entrada.png saida.png [fator] [rep_x] [rep_y]")
