#!/usr/bin/env python3
"""Rodada 12 — planta (vista de cima) da colisão composta de um asset, para revisão e evidência.

Desenha, no espaço do modelo (+z = frente, para baixo na imagem):
- os voxels do modelo (cinza, mais escuro = mais alto; folhagem em verde);
- as formas de `assets/scenes/colisao_e.json`: corpo (vermelho, borda cheia), só câmera (azul, tracejado), piso pisável
  (verde-claro);
- as entradas do catálogo (seta no rumo), os pontos de interação (amarelo, com o círculo de 1 u onde quem usa fica de pé)
  e os spawns (roxo, com o corpo de 0,3 u do NPC e a folga de 0,56 u = corpo + herói).

Uso: tools/conteudo/planta_colisao.py SAIDA_DIR [asset ...]   (padrão: os compostos)
Lê o .vox gerado, o catálogo gerado e o colisao_e.json: rode depois de `build_assets.py conteudo_e`.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import voxlib  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ENV = os.path.join(ROOT, "assets", "environment", "e")
COL = os.path.join(ROOT, "assets", "scenes", "colisao_e.json")
PX = 96                       # pixels por u
PAD = 1.4                     # margem (u) em volta da caixa


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    col = json.load(open(COL, encoding="utf-8"))["props"]
    man = json.load(open(os.path.join(ENV, "props.json"), encoding="utf-8"))
    cat = {a["id"]: a for a in json.load(open(os.path.join(ENV, "conteudo_catalogo.json"), encoding="utf-8"))["assets"]}
    names = sys.argv[2:] or sorted(k for k, v in col.items() if v.get("tipo") == "composta")
    for name in names:
        draw_one(name, col[name], man[name], cat.get(name), os.path.join(out, f"planta-{name}.png"))


def draw_one(name, prof, entry, rec, path):
    vs, o = entry["voxelSize"], entry["origin"]
    v = voxlib.load_vox(os.path.join(ENV, entry["file"]))
    top, leaf = {}, set()
    for (x, y, z), c in v.v.items():
        k = (x, z)
        if c[3] == "foliage":
            leaf.add(k)
            continue
        top[k] = max(top.get(k, -1e9), (y + o[1] + 1) * vs)
    xs = [(x + o[0]) * vs for x, _ in list(top) + list(leaf)]
    zs = [(z + o[2]) * vs for _, z in list(top) + list(leaf)]
    x0, x1, z0, z1 = min(xs) - PAD, max(xs) + vs + PAD, min(zs) - PAD, max(zs) + vs + PAD
    W, H = int((x1 - x0) * PX), int((z1 - z0) * PX) + 120
    im = Image.new("RGB", (W, H), (250, 248, 240))
    d = ImageDraw.Draw(im, "RGBA")
    P = lambda x, z: ((x - x0) * PX, (z - z0) * PX)                      # noqa: E731
    hmax = max(top.values()) if top else 1
    for k in leaf - set(top):
        a = P((k[0] + o[0]) * vs, (k[1] + o[2]) * vs)
        d.rectangle([a, (a[0] + vs * PX, a[1] + vs * PX)], fill=(120, 170, 90, 90))
    for k, h in top.items():
        g = int(215 - 150 * max(0.0, h) / hmax)
        a = P((k[0] + o[0]) * vs, (k[1] + o[2]) * vs)
        d.rectangle([a, (a[0] + vs * PX, a[1] + vs * PX)], fill=(g, g, g))
    # 1 u de grade
    for gx in range(int(x0) - 1, int(x1) + 2):
        d.line([P(gx, z0), P(gx, z1)], fill=(0, 0, 0, 25))
    for gz in range(int(z0) - 1, int(z1) + 2):
        d.line([P(x0, gz), P(x1, gz)], fill=(0, 0, 0, 25))
    d.line([P(0, z0), P(0, z1)], fill=(0, 0, 0, 70))
    d.line([P(x0, 0), P(x1, 0)], fill=(0, 0, 0, 70))
    counts = {"corpo": 0, "camera": 0, "piso": 0}
    for s in prof["formas"]:
        body, cam, walk = s.get("corpo", True), s.get("camera", True), s.get("pisavel", False)
        if s["tipo"] == "caixa":
            box = [P(s["min"][0], s["min"][1]), P(s["max"][0], s["max"][1])]
        else:
            cx, cz, r = s["centro"][0], s["centro"][1], s["raio"]
            box = [P(cx - r, cz - r), P(cx + r, cz + r)]
        shape = d.rectangle if s["tipo"] == "caixa" else d.ellipse
        if walk and not body:
            shape(box, fill=(120, 220, 120, 70), outline=(60, 150, 60, 160))
            counts["piso"] += 1
        elif body:
            shape(box, fill=(220, 60, 50, 60), outline=(200, 30, 20, 230), width=2)
            counts["corpo"] += 1
        elif cam:
            dashed(d, box, (40, 90, 220, 220))
            counts["camera"] += 1
    pts = (rec or {}).get("pontos", {})
    for e in pts.get("entradas", []):
        x, _, z = e["pos_u"]
        a = math.radians(e.get("rumo", 0))
        tip = P(x + math.sin(a) * 0.6, z + math.cos(a) * 0.6)
        d.line([P(x, z), tip], fill=(20, 20, 20), width=3)
        d.ellipse([P(x - 0.08, z - 0.08), P(x + 0.08, z + 0.08)], fill=(20, 20, 20))
        d.text((P(x, z)[0] + 6, P(x, z)[1] + 4), e["id"], fill=(20, 20, 20), font=text_font(14))
    for e in pts.get("interacao", []):
        x, _, z = e["pos_u"]
        d.ellipse([P(x - 1, z - 1), P(x + 1, z + 1)], outline=(200, 150, 0, 200), width=1)
        d.ellipse([P(x - 0.1, z - 0.1), P(x + 0.1, z + 0.1)], fill=(240, 180, 0))
        d.text((P(x, z)[0] + 6, P(x, z)[1] - 14), e["id"], fill=(120, 80, 0), font=text_font(14))
    for e in pts.get("spawns", []):
        x, _, z = e["pos_u"]
        d.ellipse([P(x - 0.56, z - 0.56), P(x + 0.56, z + 0.56)], outline=(130, 60, 170, 150), width=1)
        d.ellipse([P(x - 0.3, z - 0.3), P(x + 0.3, z + 0.3)], fill=(150, 80, 190, 160))
        d.text((P(x, z)[0] + 6, P(x, z)[1] + 6), e.get("id", "spawn"), fill=(90, 30, 120), font=text_font(14))
    estado = (rec or {}).get("aprovacao", {}).get("integracao", "?")
    legend = (f"{name} ({estado}) — {len(prof['formas'])} formas: {counts['corpo']} de corpo (vermelho), {counts['camera']} só de câmera "
              f"(azul tracejado), {counts['piso']} de piso (verde). Entradas (seta), interação (amarelo, círculo de 1 u), "
              f"spawn (roxo: corpo 0,3 u; folga 0,56 u). Grade de 1 u, +z = frente (para baixo).")
    font = text_font(15)
    y = H - 110
    for line in wrap(legend, W // 8):
        d.text((8, y), line, fill=(20, 20, 20), font=font)
        y += 18
    im.save(path)
    print(path, counts)


def text_font(size):
    """Uma fonte do sistema com acentos (a padrão do PIL não tem); sem nenhuma, a padrão."""
    for f in ("/usr/share/fonts/liberation-sans-fonts/LiberationSans-Regular.ttf",
              "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def dashed(d, box, color):
    (ax, ay), (bx, by) = box
    for (p, q) in (((ax, ay), (bx, ay)), ((bx, ay), (bx, by)), ((bx, by), (ax, by)), ((ax, by), (ax, ay))):
        n = max(1, int(max(abs(q[0] - p[0]), abs(q[1] - p[1])) // 6))
        for i in range(0, n, 2):
            s, e = i / n, min(1, (i + 1) / n)
            d.line([(p[0] + (q[0] - p[0]) * s, p[1] + (q[1] - p[1]) * s), (p[0] + (q[0] - p[0]) * e, p[1] + (q[1] - p[1]) * e)],
                   fill=color, width=2)


def wrap(text, n):
    out, line = [], ""
    for w in text.split():
        if len(line) + len(w) + 1 > n:
            out.append(line)
            line = w
        else:
            line = (line + " " + w).strip()
    return out + [line]


if __name__ == "__main__":
    main()
