#!/usr/bin/env python3
"""Laboratório de conteúdo — capturas nas vitrines (renderer real, modo CENA), derivadas do catálogo.

Para cada construção/POI de cada vitrine, o herói é posto na ENTRADA declarada no catálogo (girada como o prop) e a
câmera enquadra a partir dela:
  jogo     câmera do jogo (FollowCamera: 4,6 u, 17°), atrás do herói que olha a entrada
  34       3/4 da frente (yaw = rumo + 35°, 22°)        frente   de frente (yaw = rumo, 12°)
  fundos   herói atrás do asset, câmera pelos fundos    alto     de cima (55°): telhado/copa, contato com o chão
e, por vitrine, um panorama. Em 1920×1080 e 1280×720 (dia) e 1920×1080 (tarde).

    .venv/bin/python tools/conteudo/cenas.py SAIDA [vitrine ...]
"""
import json
import math
import os
import subprocess
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
CAT = os.path.join(ROOT, "assets", "environment", "e", "conteudo_catalogo.json")


def rot(x, z, deg):
    a = math.radians(deg)
    return x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)


def shots(scene, cat):
    doc = json.load(open(os.path.join(ROOT, "assets", "scenes", scene + ".json")))
    out = []
    for p in doc["props"]:
        rec = cat.get(p["n"])
        if not rec or rec["categoria"] not in ("construcao", "poi"):
            continue
        x, z, r = p["x"], p["z"], p["r"]
        size = max(rec["medidas"]["dimensoes_u"])
        ent = rec["pontos"]["entradas"][0]
        ex, ez = rot(ent["pos_u"][0], ent["pos_u"][2], r)
        hx, hz = x + ex, z + ez
        face = (r + ent["rumo"]) % 360                           # para onde a entrada olha, no mundo
        bx, bz = rot(0, rec["medidas"]["pegada_u"]["min"][1] - 1.4, r)
        d = 3.2 + size * 1.15
        base = f"layout={scene},hx={hx:.2f},hz={hz:.2f}"
        n = p["n"]
        out += [
            (f"{n}-jogo", f"{base},hyaw={(face + 180) % 360:.0f},yaw={face:.0f},pitch=17,dist=4.6,lift=0.9"),
            (f"{n}-34", f"{base},hyaw={face:.0f},yaw={(face + 35) % 360:.0f},pitch=22,dist={d:.1f},lift={size * 0.28:.2f}"),
            (f"{n}-frente", f"{base},hyaw={face:.0f},yaw={face:.0f},pitch=12,dist={d:.1f},lift={size * 0.3:.2f}"),
            (f"{n}-fundos", f"layout={scene},hx={x + bx:.2f},hz={z + bz:.2f},hyaw={(face + 180) % 360:.0f},"
                            f"yaw={(face + 180) % 360:.0f},pitch=16,dist={d:.1f},lift={size * 0.3:.2f}"),
            (f"{n}-alto", f"{base},hyaw={face:.0f},yaw={(face + 20) % 360:.0f},pitch=55,dist={d * 1.15:.1f},lift=0.5"),
        ]
    hx, hz = doc["hero"][0], doc["hero"][1]
    out.append(("panorama", f"layout={scene},hx={hx},hz={hz},yaw=10,pitch=34,dist=24,lift=0.5"))
    return out


def main():
    dest = sys.argv[1]
    scenes = sys.argv[2:] or ["vitrine_conteudo_construcoes", "vitrine_conteudo_vegetacao", "vitrine_conteudo_poi"]
    cat = {a["id"]: a for a in json.load(open(CAT))["assets"]}
    specs, index = [], []
    for sc in scenes:
        short = sc.replace("vitrine_conteudo_", "")
        for name, spec in shots(sc, cat):
            for (cfg, extra) in (("1080-dia", "size=1920x1080"), ("720-dia", "size=1280x720"),
                                 ("1080-tarde", "size=1920x1080,preset=tarde")):
                if cfg != "1080-dia" and name.endswith(("-frente", "-fundos", "-alto")):
                    continue                                         # 720p e tarde: jogo, 3/4 e panorama bastam
                rel = f"{short}/{cfg}/{name}.png"
                os.makedirs(os.path.join(dest, short, cfg), exist_ok=True)
                specs.append(f"mode=scene,style=e,nohud=1,{extra},{spec},out={os.path.join(dest, rel)}")
                index.append(f"| {short} | {name} | {cfg} | `{spec}` | [{rel}]({rel}) |")
    env = dict(os.environ)
    for i in range(0, len(specs), 12):                                # lotes (um processo do viewer por lote)
        subprocess.run([os.path.join(ROOT, "tools", "snap.sh")] + specs[i:i + 12], check=True, env=env,
                       stdout=subprocess.DEVNULL)
    with open(os.path.join(dest, "INDICE.md"), "w") as f:
        f.write("# Capturas nas vitrines (renderer real)\n\nGeradas por `tools/conteudo/cenas.py`. Herói na entrada do "
                "catálogo; câmeras derivadas do rumo do prop.\n\n| vitrine | tomada | config | especificação | imagem |\n"
                "|---|---|---|---|---|\n" + "\n".join(index) + "\n")
    print(f"{len(specs)} capturas em {dest}")


if __name__ == "__main__":
    main()
