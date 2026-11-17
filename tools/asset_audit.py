#!/usr/bin/env python3
"""Rodada 05 — auditoria estrutural dos modelos voxel (QA objetivo, complementa a revisão visual).

Lê os .vox gerados (formato MagicaVoxel 150) e aponta, por arquivo:
  * voxels ISOLADOS (nenhum vizinho nas 6 direções) — quase sempre ruído de 1 voxel;
  * FRAGMENTOS soltos (componentes 6-conexos com menos de `--frag` voxels, além do principal) — peças
    que flutuam ou se desprenderam;
  * número de cores — densidade de paleta (a E pede 3–5 matizes por modelo; muitas cores numa peça
    pequena costumam ser pintura ruidosa).

Uso:
    python3 tools/asset_audit.py assets/characters/e assets/creatures/e      # relatório na tela
    python3 tools/asset_audit.py --md saida.md assets/characters/e            # tabela Markdown

Uma linha com isolados/fragmentos NÃO é automaticamente defeito (pena, corda, conta de luz são finas de
propósito): o relatório serve para olhar cada caso na captura e decidir.
"""
import argparse
import os
import struct
import sys
from collections import deque


def read_vox(path):
    """Retorna dict {(x, y, z): índice de paleta} (Y para cima, como o viewer) e a paleta RGBA."""
    data = open(path, "rb").read()
    if data[:4] != b"VOX ":
        raise ValueError(f"{path}: não é .vox")
    pos = 8
    vox, palette = {}, None

    def chunks(p, end):
        while p < end:
            cid = data[p:p + 4]
            n, m = struct.unpack_from("<ii", data, p + 4)
            yield cid, p + 12, n, p + 12 + n, p + 12 + n + m
            p = p + 12 + n + m

    for cid, cs, n, ce, end in chunks(pos, len(data)):
        if cid == b"MAIN":
            for c2, s2, n2, e2, _ in chunks(ce, end):
                if c2 == b"XYZI":
                    cnt = struct.unpack_from("<i", data, s2)[0]
                    for i in range(cnt):
                        x, y, z, c = data[s2 + 4 + 4 * i: s2 + 8 + 4 * i]
                        vox[(x, z, y)] = c          # MagicaVoxel é Z para cima
                elif c2 == b"RGBA":
                    palette = [data[s2 + 4 * i: s2 + 4 * i + 4] for i in range(256)]
    return vox, palette


def components(cells):
    seen, comps = set(), []
    for c in cells:
        if c in seen:
            continue
        q, comp = deque([c]), []
        seen.add(c)
        while q:
            x, y, z = q.popleft()
            comp.append((x, y, z))
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                nb = (x + d[0], y + d[1], z + d[2])
                if nb in cells and nb not in seen:
                    seen.add(nb)
                    q.append(nb)
        comps.append(comp)
    comps.sort(key=len, reverse=True)
    return comps


def audit_file(path, frag):
    vox, _ = read_vox(path)
    cells = set(vox)
    iso = sum(1 for (x, y, z) in cells if not any((x + dx, y + dy, z + dz) in cells for dx, dy, dz in
                                                      ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))))
    comps = components(cells)
    small = [len(c) for c in comps[1:] if len(c) < frag]
    return {"voxels": len(cells), "cores": len(set(vox.values())), "isolados": iso,
            "componentes": len(comps), "fragmentos": small}


def orphans(d):
    """.vox dentro de pastas de rig que o rig.json não referencia (sobras de versões antigas)."""
    import json
    out = []
    for root, _, files in os.walk(d):
        if "rig.json" not in files:
            continue
        doc = json.load(open(os.path.join(root, "rig.json")))
        used = set()
        for p in doc.get("parts", []):
            used.add(p.get("file"))
            for a in (p.get("alts") or {}).values():
                used.add(a.get("file"))
        out += [os.path.join(root, f) for f in files if f.endswith(".vox") and f not in used]
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dirs", nargs="+")
    ap.add_argument("--frag", type=int, default=4, help="componente menor que isto (além do principal) = fragmento")
    ap.add_argument("--md", help="grava tabela Markdown")
    a = ap.parse_args()
    rows = []
    for d in a.dirs:
        for root, _, files in sorted(os.walk(d)):
            for f in sorted(files):
                if f.endswith(".vox"):
                    p = os.path.join(root, f)
                    r = audit_file(p, a.frag)
                    rows.append((p, r))
    out = ["| arquivo | voxels | cores | isolados | componentes | fragmentos < %d |" % a.frag, "|---|---|---|---|---|---|"]
    for p, r in rows:
        flag = " ⚠" if r["isolados"] or r["fragmentos"] else ""
        out.append(f"| `{p}`{flag} | {r['voxels']} | {r['cores']} | {r['isolados']} | {r['componentes']} | {r['fragmentos'] or '—'} |")
    text = "\n".join(out)
    if a.md:
        open(a.md, "w").write(text + "\n")
    print(text)
    bad = sum(1 for _, r in rows if r["isolados"] or r["fragmentos"])
    print(f"\n{len(rows)} arquivos; {bad} com isolados/fragmentos", file=sys.stderr)
    orph = [o for d in a.dirs for o in orphans(d)]
    for o in orph:
        print(f"ÓRFÃO (não referenciado por rig.json): {o}", file=sys.stderr)


if __name__ == "__main__":
    main()
