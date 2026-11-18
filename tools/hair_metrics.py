#!/usr/bin/env python3
"""Rodada 09 — medidas dos estilos de cabelo do catálogo de personalização (sem GPU).

    python3 tools/hair_metrics.py [saida.md]

Lê assets/components/catalog.json e cada cabelo.*/hair.vox no espaço do modelo, com o MESMO mapeamento do
leitor Java (VoxModel: eixos (x, z, sy−1−y) do MagicaVoxel; VoxGrid.of: + origem). Imprime voxels, caixa e o
Jaccard de volume entre pares — o critério do CustomizationTest (< 0,80) e a leitura de "variação × estilo".
"""
import json
import struct
import sys
from pathlib import Path


def load_cells(path, origin):
    b = Path(path).read_bytes()
    i, size, cells = 8, None, set()
    while i < len(b):
        cid = b[i:i + 4]
        n, m = struct.unpack("<II", b[i + 4:i + 12])
        if cid == b"MAIN":
            i += 12
            continue
        if cid == b"SIZE":
            size = struct.unpack("<III", b[i + 12:i + 24])
        elif cid == b"XYZI":
            k = struct.unpack("<I", b[i + 12:i + 16])[0]
            sy = size[1]
            for j in range(k):
                x, y, z, _ = b[i + 16 + 4 * j:i + 20 + 4 * j]
                cells.add((x + origin[0], z + origin[1], sy - 1 - y + origin[2]))
        i += 12 + n + m
    return cells


def main():
    cat = json.load(open("assets/components/catalog.json"))
    ids = sorted(k for k, v in cat["componentes"].items() if v["slot"] == "cabelo")
    cells = {k: load_cells("assets/components/" + cat["componentes"][k]["cabeca"]["file"],
                           cat["componentes"][k]["cabeca"]["origin"]) for k in ids}
    neck = cat["pivoCabeca"][1]
    out = ["# Medidas dos estilos de cabelo (voxels do cabelo fundido na cabeça; espaço do modelo)", "",
           "| estilo | voxels | largura x | topo (voxels acima do pescoço) | profundidade z | experimental |",
           "|---|---|---|---|---|---|"]
    for k in ids:
        xs, ys, zs = zip(*cells[k])
        out.append(f"| {k} | {len(cells[k])} | {max(xs) - min(xs) + 1} | {max(ys) - neck:.0f} | {max(zs) - min(zs) + 1} | "
                   f"{'sim' if cat['componentes'][k].get('experimental') else ''} |")
    short = [k.split(".", 1)[1] for k in ids]
    out += ["", "Jaccard de volume entre pares (CustomizationTest exige < 0,80; o 'natural' ficou fora com 0,82):", "",
            "| | " + " | ".join(short) + " |", "|---" * (len(ids) + 1) + "|"]
    worst = 0.0
    for a in ids:
        row = []
        for b in ids:
            if a == b:
                row.append("—")
                continue
            j = len(cells[a] & cells[b]) / len(cells[a] | cells[b])
            worst = max(worst, j)
            row.append(f"{j:.2f}")
        out.append(f"| {a.split('.', 1)[1]} | " + " | ".join(row) + " |")
    out += ["", f"Maior Jaccard entre pares: {worst:.2f}."]
    text = "\n".join(out) + "\n"
    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text(text)
    print(text)


if __name__ == "__main__":
    main()
