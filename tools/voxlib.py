"""Biblioteca de modelagem voxel por script (somente stdlib).

Convenções (iguais às da engine):
  * Y para cima, +Z = frente do modelo, X = direita de quem olha o modelo de frente.
    O braço DIREITO do personagem fica portanto em x < 0.
  * Modelos são centrados em x=0 e z=0; y=0 é o chão. Com largura par, os voxels ocupam
    x em [-w/2, w/2-1], e o espelhamento em X é x -> -1 - x.
  * Uma cor é a tupla (r, g, b, material). Materiais: matte, metal, emissive, foliage, water.

Saídas:
  * .vox (MagicaVoxel 150) — abre em MagicaVoxel e Goxel. O material é codificado pela faixa do
    índice de paleta (ver MAT_RANGES); a engine lê a mesma convenção.
  * rig.json — partes rígidas + pivôs + hierarquia de um personagem/criatura.
  * PNG RGBA (escritor mínimo via zlib) para pixel art.
"""
from __future__ import annotations

import colorsys
import json
import os
import random
import struct
import zlib

MAT_RANGES = {
    "matte": (1, 175),
    "metal": (176, 199),
    "emissive": (200, 219),
    "foliage": (220, 249),
    "water": (250, 255),
}


# ----------------------------------------------------------------------------- cores

def C(hexstr: str, mat: str = "matte"):
    """Cor a partir de '#rrggbb'."""
    h = hexstr.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), mat)


def tone(col, k: float, hue_shift: float = 0.035, sat: float = 0.0):
    """Clareia (k>0) ou escurece (k<0) com desvio de matiz: luzes puxam para o amarelo,
    sombras para o azul/roxo. É o que evita rampas 'lavadas' de mesma matiz."""
    r, g, b, m = col
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    v2 = max(0.0, min(1.0, v * (1 + 0.16 * k)))
    s2 = max(0.0, min(1.0, s * (1 - 0.07 * k) + sat))
    # alvo de matiz: 0.14 (amarelo) para luz, 0.70 (azul-violeta) para sombra
    target = 0.14 if k > 0 else 0.70
    d = ((target - h + 0.5) % 1.0) - 0.5
    h2 = (h + d * min(1.0, abs(k)) * hue_shift * 4) % 1.0
    r2, g2, b2 = colorsys.hsv_to_rgb(h2, s2, v2)
    return (round(r2 * 255), round(g2 * 255), round(b2 * 255), m)


def mix(a, b, t: float):
    return (round(a[0] + (b[0] - a[0]) * t), round(a[1] + (b[1] - a[1]) * t),
            round(a[2] + (b[2] - a[2]) * t), a[3])


def with_mat(col, mat: str):
    return (col[0], col[1], col[2], mat)


# ----------------------------------------------------------------------------- volume

class Vox:
    """Volume esparso de voxels: dict (x, y, z) -> cor."""

    def __init__(self):
        self.v: dict[tuple[int, int, int], tuple] = {}

    # --- primitivas
    def set(self, x, y, z, c):
        if c is None:
            self.v.pop((x, y, z), None)
        else:
            self.v[(x, y, z)] = c
        return self

    def get(self, x, y, z):
        return self.v.get((x, y, z))

    def has(self, x, y, z):
        return (x, y, z) in self.v

    def box(self, x0, y0, z0, x1, y1, z1, c):
        """Caixa cheia, limites inclusivos. c=None apaga."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, c)
        return self

    def ellipsoid(self, cx, cy, cz, rx, ry, rz, c, fn=None):
        """Elipsoide em coordenadas contínuas (centros de voxel em +0.5). fn(x,y,z)->cor opcional."""
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
                for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                    dx, dy, dz = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry, (z + 0.5 - cz) / rz
                    if dx * dx + dy * dy + dz * dz <= 1.0:
                        self.set(x, y, z, fn(x, y, z) if fn else c)
        return self

    def cyl(self, cx, cz, y0, y1, r, c, r_top=None):
        """Cilindro/tronco de cone vertical (raio r na base, r_top no topo)."""
        r_top = r if r_top is None else r_top
        n = max(1, y1 - y0)
        for y in range(y0, y1 + 1):
            rr = r + (r_top - r) * (y - y0) / n
            for x in range(int(cx - rr) - 1, int(cx + rr) + 2):
                for z in range(int(cz - rr) - 1, int(cz + rr) + 2):
                    if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 <= rr * rr:
                        self.set(x, y, z, c)
        return self

    def line(self, a, b, c, thick=0):
        """Linha 3D de voxels de a até b (inclusive), com espessura opcional."""
        n = max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]), 1)
        for i in range(n + 1):
            t = i / n
            x, y, z = (round(a[k] + (b[k] - a[k]) * t) for k in range(3))
            if thick:
                self.box(x - thick, y - thick, z - thick, x + thick, y + thick, z + thick, c)
            else:
                self.set(x, y, z, c)
        return self

    # --- operações de conjunto
    def merge(self, other: "Vox", dx=0, dy=0, dz=0, overwrite=True):
        for (x, y, z), c in other.v.items():
            k = (x + dx, y + dy, z + dz)
            if overwrite or k not in self.v:
                self.v[k] = c
        return self

    def cut(self, pred):
        """Remove e devolve (como novo Vox) os voxels para os quais pred(x,y,z,c) é verdadeiro."""
        out = Vox()
        for k in [k for k, c in self.v.items() if pred(k[0], k[1], k[2], c)]:
            out.v[k] = self.v.pop(k)
        return out

    def copy(self):
        o = Vox()
        o.v = dict(self.v)
        return o

    def mirror_x(self):
        """Copia x>=0 para x<0 (x -> -1-x), sobrescrevendo."""
        for (x, y, z), c in list(self.v.items()):
            if x >= 0:
                self.v[(-1 - x, y, z)] = c
        return self

    def flipped_x(self):
        o = Vox()
        for (x, y, z), c in self.v.items():
            o.v[(-1 - x, y, z)] = c
        return o

    def translate(self, dx, dy, dz):
        self.v = {(x + dx, y + dy, z + dz): c for (x, y, z), c in self.v.items()}
        return self

    def rot_y90(self, times=1):
        """Gira 90° em torno de Y (em torno do eixo x=0,z=0), 'times' vezes."""
        for _ in range(times % 4):
            self.v = {(z, y, -1 - x): c for (x, y, z), c in self.v.items()}
        return self

    # --- pintura
    def paint(self, fn):
        """fn(x,y,z,c) -> nova cor ou None para manter."""
        for k, c in list(self.v.items()):
            n = fn(k[0], k[1], k[2], c)
            if n is not None:
                self.v[k] = n
        return self

    def replace(self, a, b):
        return self.paint(lambda x, y, z, c: b if c == a else None)

    def speckle(self, base, variants, prob, seed):
        """Troca aleatoriamente (determinístico por seed) voxels de cor 'base' por variantes."""
        rnd = random.Random(seed)
        for k in sorted(self.v):
            if self.v[k] == base and rnd.random() < prob:
                self.v[k] = rnd.choice(variants)
        return self

    def is_exposed(self, x, y, z, d):
        return (x + d[0], y + d[1], z + d[2]) not in self.v

    def bounds(self):
        xs = [k[0] for k in self.v]
        ys = [k[1] for k in self.v]
        zs = [k[2] for k in self.v]
        return (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))

    def __len__(self):
        return len(self.v)


# ----------------------------------------------------------------------------- .vox

def write_vox(path: str, vox: Vox):
    """Grava .vox e devolve a origem (canto mínimo, em voxels do espaço do modelo)."""
    if not vox.v:
        raise ValueError(f"modelo vazio: {path}")
    x0, y0, z0, x1, y1, z1 = vox.bounds()
    sx, sy, sz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    if max(sx, sy, sz) > 256:
        raise ValueError(f"{path}: modelo maior que 256 voxels ({sx}x{sy}x{sz})")

    # paleta determinística, por faixa de material
    palette = [(0, 0, 0)] * 256
    index = {}
    by_mat: dict[str, list] = {}
    for c in sorted(set(vox.v.values())):
        by_mat.setdefault(c[3], []).append(c)
    for mat, cols in by_mat.items():
        lo, hi = MAT_RANGES[mat]
        if len(cols) > hi - lo + 1:
            raise ValueError(f"{path}: {len(cols)} cores '{mat}' excedem a faixa {lo}-{hi}")
        for i, c in enumerate(cols):
            index[c] = lo + i
            palette[lo + i - 1] = c[:3]

    # engine (x, y, z) -> vox (x, sizeZ-1-z, y)   [vox é Z para cima]
    xyzi = bytearray()
    for (x, y, z) in sorted(vox.v):
        xyzi += struct.pack("4B", x - x0, z1 - z, y - y0, index[vox.v[(x, y, z)]])

    def chunk(cid: bytes, content: bytes, children: bytes = b""):
        return cid + struct.pack("<ii", len(content), len(children)) + content + children

    size = chunk(b"SIZE", struct.pack("<iii", sx, sz, sy))
    body = chunk(b"XYZI", struct.pack("<i", len(vox.v)) + bytes(xyzi))
    rgba = chunk(b"RGBA", b"".join(struct.pack("4B", r, g, b, 255) for (r, g, b) in palette))
    data = b"VOX " + struct.pack("<i", 150) + chunk(b"MAIN", b"", size + body + rgba)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    return [x0, y0, z0]


# ----------------------------------------------------------------------------- PNG

def load_vox(path: str, like: "Vox | None" = None) -> "Vox":
    """Rodada 05: lê um .vox (MagicaVoxel 150, ex.: editado no Goxel) como Vox, com o material de cada cor
    deduzido pela faixa do índice de paleta (MAT_RANGES). É assim que uma peça desenhada à mão entra no
    pipeline sem se perder na regeneração: guarde o .vox em tools/fontes/ e importe-o no script do asset
    (ver docs/art/pipeline-de-producao.md). Eixos: Y para cima, como o resto do voxlib."""
    import struct
    data = open(path, "rb").read()
    if data[:4] != b"VOX ":
        raise ValueError(f"{path}: não é .vox")
    xyzi, rgba, size = [], None, (0, 0, 0)

    def walk(p, end):
        while p < end:
            cid = data[p:p + 4]
            n, m = struct.unpack_from("<ii", data, p + 4)
            yield cid, p + 12, n, p + 12 + n, p + 12 + n + m
            p += 12 + n + m

    for cid, cs, n, ce, end in walk(8, len(data)):
        if cid == b"MAIN":
            for c2, s2, n2, e2, _ in walk(ce, end):
                if c2 == b"SIZE":
                    size = struct.unpack_from("<iii", data, s2)
                elif c2 == b"XYZI":
                    cnt = struct.unpack_from("<i", data, s2)[0]
                    xyzi = [tuple(data[s2 + 4 + 4 * i: s2 + 8 + 4 * i]) for i in range(cnt)]
                elif c2 == b"RGBA":
                    rgba = [tuple(data[s2 + 4 * i: s2 + 4 * i + 3]) for i in range(256)]
    v = Vox()
    depth = size[1]                               # extensão em Z do modelo (o write_vox espelha: vy = z1 - z)
    for (x, vy, vz, ci) in xyzi:
        mat = next((m for m, (lo, hi) in MAT_RANGES.items() if lo <= ci <= hi), "matte")
        r, g, b = rgba[ci - 1] if rgba else (200, 200, 200)
        v.v[(x, vz, depth - 1 - vy)] = (r, g, b, mat)   # .vox é Z para cima; Vox é Y para cima
    if like is not None:
        # editores como o Goxel reescrevem a paleta e perdem a faixa de material: reatribui pela cor RGB
        by_rgb = {c[:3]: c[3] for c in like.v.values()}
        for k, c in v.v.items():
            if c[:3] in by_rgb:
                v.v[k] = (*c[:3], by_rgb[c[:3]])
    return v


def write_png(path: str, width: int, height: int, rgba: bytes):
    assert len(rgba) == width * height * 4
    raw = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ----------------------------------------------------------------------------- rig / props

class Rig:
    """Personagem/criatura como partes rígidas com pivôs (em voxels, espaço do modelo)."""

    def __init__(self, name: str, voxel_size: float, anim: str = "biped", meta: dict | None = None):
        self.name, self.voxel_size, self.anim = name, voxel_size, anim
        self.meta = meta or {}
        self.parts = []
        self.alts = {}          # nome da parte -> {variante: Vox} (ex.: expressões da cabeça)
        self.extra = {}         # chaves extras no topo do rig.json (ex.: affineWind)

    def part(self, name: str, vox: Vox, pivot, parent: str | None = None, alts: dict | None = None):
        if len(vox):
            self.parts.append((name, vox, [float(p) for p in pivot], parent))
            if alts:
                self.alts[name] = alts
        return self

    def all_voxels(self) -> Vox:
        o = Vox()
        for _, v, _, _ in self.parts:
            o.merge(v)
        return o

    def save(self, directory: str):
        os.makedirs(directory, exist_ok=True)
        parts = []
        for name, vox, pivot, parent in self.parts:
            origin = write_vox(os.path.join(directory, f"{name}.vox"), vox)
            entry = {"name": name, "file": f"{name}.vox", "parent": parent, "origin": origin, "pivot": pivot}
            if name in self.alts:
                entry["alts"] = {}
                for key, av in self.alts[name].items():
                    fn = f"{name}_{key}.vox"
                    entry["alts"][key] = {"file": fn, "origin": write_vox(os.path.join(directory, fn), av)}
            parts.append(entry)
        b = self.all_voxels().bounds()
        doc = {"name": self.name, "type": "vox", "voxelSize": self.voxel_size, "anim": self.anim,
               "sizeVoxels": [b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1],
               "meta": self.meta, "parts": parts}
        doc.update(self.extra)
        with open(os.path.join(directory, "rig.json"), "w") as f:
            json.dump(doc, f, indent=1)
        return doc


def exposed_faces(vox: Vox) -> int:
    """Faces de voxel expostas (o que o mesher emitiria sem fusão): medida de custo do modelo."""
    d = vox.v
    return sum(1 for (x, y, z) in d for (a, b, c) in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
               if (x + a, y + b, z + c) not in d)


class PropSet:
    """Conjunto de modelos estáticos de um diretório, com manifesto props.json."""

    def __init__(self, directory: str, voxel_size: float):
        self.directory, self.voxel_size = directory, voxel_size
        self.entries = {}

    def add(self, name: str, vox: Vox, voxel_size: float | None = None, **extra):
        origin = write_vox(os.path.join(self.directory, f"{name}.vox"), vox)
        b = vox.bounds()
        self.entries[name] = {"file": f"{name}.vox", "voxelSize": voxel_size or self.voxel_size,
                              "origin": origin,
                              "sizeVoxels": [b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1]}
        self.entries[name].update(extra)

    def save(self):
        os.makedirs(self.directory, exist_ok=True)
        with open(os.path.join(self.directory, "props.json"), "w") as f:
            json.dump(self.entries, f, indent=1, sort_keys=True)
