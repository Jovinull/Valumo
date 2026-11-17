#!/usr/bin/env python3
"""Rodada 04 — ajuda de autoria dos perfis de arma (assets/anim/*.json).

Para cada pose do perfil, mostra o alcance de cada braço (distância ombro → alvo da mão, com o tronco já
girado e inclinado) contra o comprimento do braço, e, nas poses de duas mãos, onde fica a mão esquerda no
cabo. É uma conferência rápida enquanto se escreve o JSON; a validação de verdade (interseções ao longo
do tempo, com as mesmas contas do viewer) é `viewer --check`.

    python3 tools/anim_reach.py longblade            # todas as poses
    python3 tools/anim_reach.py longblade l1_wind     # só uma

Convenções (iguais ao viewer): espaço da raiz em voxels, +Z frente, +X esquerda do personagem; tronco gira
em torno do pivô (0, 11, 0,5) na ordem Z·Y·X (graus em [pitch, yaw, roll]).
"""
import json
import math
import sys

SHOULDER = {"r": (-6.5, 23.5, 0.5), "l": (6.5, 23.5, 0.5)}
PIVOT = (0.0, 11.0, 0.5)
ARM = 6.57 + 4.07          # braço + antebraço até a empunhadura (herói da E)


def rot(p, deg):
    x, y, z = (p[0] - PIVOT[0], p[1] - PIVOT[1], p[2] - PIVOT[2])
    ax, ay, az = (math.radians(a) for a in deg)
    # R = Rz·Ry·Rx aplicada ao vetor
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + PIVOT[0], y + PIVOT[1], z + PIVOT[2])


def load(name):
    doc = json.load(open(f"assets/anim/{name}.json"))
    if "extends" in doc:
        base = load(doc["extends"])
        for k, v in doc.items():
            if k in ("poses", "moves"):
                for n, o in v.items():
                    if o.get("merge") and n in base[k]:
                        base[k][n] = {**base[k][n], **{f: x for f, x in o.items() if f != "merge"}}
                    else:
                        base[k][n] = o
            elif k != "extends":
                base[k] = v
        return base
    return doc


def resolve(poses, n, defaults):
    o = poses[n]
    out = dict(defaults)
    if "base" in o:
        out.update(resolve(poses, o["base"], defaults))
    out.update(o)
    return out


def main():
    prof = load(sys.argv[1])
    only = sys.argv[2] if len(sys.argv) > 2 else None
    d = {"torso": [0, 0, 0], "lGrip": 4.5, "lMode": "ik", **prof.get("defaults", {})}
    for n in prof["poses"]:
        if only and n != only:
            continue
        p = resolve(prof["poses"], n, d)
        tor = p.get("torso", [0, 0, 0])
        line = f"{n:12s}"
        r = p.get("r")
        if r:
            s = rot(SHOULDER["r"], tor)
            dist = math.dist(s, r)
            line += f"  dir {dist:5.1f}/{ARM:.1f}{' !' if dist > ARM else '  '}"
        if p.get("lMode") == "grip" and r:
            dv = p.get("rDir", [0, 1, 0])
            ln = math.sqrt(sum(c * c for c in dv))
            l = tuple(r[i] - dv[i] / ln * p.get("lGrip", 4.5) for i in range(3))
        else:
            l = p.get("l")
        if l and p.get("lMode") != "free":
            s = rot(SHOULDER["l"], tor)
            dist = math.dist(s, l)
            line += f"  esq {dist:5.1f}/{ARM:.1f}{' !' if dist > ARM else '  '} mão esq ({l[0]:.1f}, {l[1]:.1f}, {l[2]:.1f})"
        print(line)


if __name__ == "__main__":
    main()
