#!/usr/bin/env python3
"""Rodada 12 — perfis de colisão dos assets da biblioteca em assets/scenes/colisao_e.json (o MESMO arquivo que o jogo lê).

* Assets com interior (o catálogo marca `colisao.formas`): perfil `"tipo": "composta"`, com as formas MEDIDAS dos voxels
  no espaço do modelo (u), que giram com o prop:
    - corpo: cada coluna de voxels com algo na faixa [0,375; 1,2) u — acima do degrau, abaixo da cabeça do herói —, da
      base desse voxel ao topo da parede contínua (vão de até 2 voxels conta como parede: janela, frincha), fundidas em
      retângulos gulosos na resolução do voxel. Peça baixa (topo < 1,2 u: bigorna, tina, balcão) não bloqueia a câmera;
      peça de até 0,8 u é pisável (como o caixote);
    - arco, lintel, telhado, toldo: o que fica acima da parede contínua ou só existe acima da cabeça vira forma SÓ de
      câmera (corpo livre), fundida numa grade de 0,25 u, da base ao topo do que está lá.
* Os outros assets da biblioteca: o perfil simples que o catálogo já entrega (`colisao`, sem as notas); variantes
  publicadas (`<id>__…`) usam o do original por prefixo (`"<id>*"`).

Os perfis dos props que o gerador v1 planta NÃO são tocados (a referência das sementes da v1 depende deles). O resto do
arquivo sai byte a byte igual. Revise o diff antes de commitar; o teste CompositeCollisionTest confere a cobertura.

    python3 tools/conteudo/formas_compostas.py [--conferir]
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
sys.path.insert(0, os.path.join(ROOT, "tools"))
import voxlib  # noqa: E402

ENV = os.path.join(ROOT, "assets", "environment", "e")
COL = os.path.join(ROOT, "assets", "scenes", "colisao_e.json")
BODY_LO = 0.375          # faixa do corpo (o que fica acima do degrau): a mesma do catálogo (collision_boxes)
HEAD = 1.2               # altura do herói (Player.HEIGHT): o que começa acima disto não bloqueia o corpo
GAP = 2                  # vão (em voxels) que ainda conta como a mesma parede
CAMERA_MIN = 1.2         # topo abaixo disso: peça baixa, a câmera passa por cima
STAND_MAX = 0.8          # topo até isso: pisável (o salto sobe; caixote 0,5, barril 0,75)
GRID = 0.25              # grade das formas de câmera (a da conferência de alcance)
STEP = 0.30              # o degrau que se sobe a pé (Player.STEP): o topo do piso elevado pisável
FLOOR_MIN = 0.25         # laje abaixo da faixa do corpo com topo a partir disto = piso elevado


def r4(v):
    return round(v * 16) / 16                                    # 1/16 u: o que o voxel mais fino resolve


def columns(name, man):
    """{(cx, cz) em u do centro da coluna: [(y0, y1) em u, ordenados]} dos voxels não-folhagem do modelo."""
    e = man[name]
    vs, o = e["voxelSize"], e["origin"]
    v = voxlib.load_vox(os.path.join(ENV, e["file"]))
    cols = {}
    for (x, y, z), c in v.v.items():
        if c[3] == "foliage":
            continue
        cx, cz = (x + o[0]) * vs + vs / 2, (z + o[2]) * vs + vs / 2
        y0 = (y + o[1]) * vs
        cols.setdefault((cx, cz), []).append((y0, y0 + vs))
    for k in cols:
        cols[k].sort()
    return cols, vs


def run_top(spans, vs):
    """Topo da coluna contínua que começa na faixa do corpo (None se a coluna não tem nada nela) e o que sobra acima."""
    start = next((i for i, (a, b) in enumerate(spans) if b > BODY_LO and a < BODY_HI), None)
    if start is None:
        return None, [s for s in spans if s[0] >= BODY_LO]
    top = spans[start][1]
    i = start + 1
    while i < len(spans) and spans[i][0] - top <= GAP * vs + 1e-6:
        top = max(top, spans[i][1])
        i += 1
    return top, spans[i:]


def inside(px, pz, rect):
    return rect[0] <= px < rect[2] and rect[1] <= pz < rect[3]


def merge(cells, grid):
    """Retângulos gulosos sobre células {(i, j): (y0, y1)} de lado `grid` (u): [(x0, z0, x1, z1, y0, y1)]."""
    left, out = set(cells), []
    for g in sorted(cells, key=lambda k: (k[1], k[0])):
        if g not in left:
            continue
        w = 1
        while (g[0] + w, g[1]) in left:
            w += 1
        h = 1
        while all((g[0] + i, g[1] + h) in left for i in range(w)):
            h += 1
        lo, hi = 1e9, -1e9
        for i in range(w):
            for j in range(h):
                left.discard((g[0] + i, g[1] + j))
                a, b = cells[(g[0] + i, g[1] + j)]
                lo, hi = min(lo, a), max(hi, b)
        out.append((g[0] * grid, g[1] * grid, (g[0] + w) * grid, (g[1] + h) * grid, lo, hi))
    return out


def composite(name, rec, man):
    """Formas medidas coluna a coluna (o voxel do modelo): o que bloqueia o CORPO é a coluna com voxel na faixa
    [0,375; 1,2) u — acima do degrau, abaixo da cabeça do herói —, da base desse voxel ao topo da parede contínua; o que
    fica acima (arco, lintel, telhado, toldo) e o que só existe acima da cabeça seguram só a câmera."""
    cols, vs = columns(name, man)
    body, upper, floor = {}, {}, {}
    for (px, pz), spans in cols.items():
        i, j = round((px - vs / 2) / vs), round((pz - vs / 2) / vs)
        low = [b for a, b in spans if a < BODY_LO - 1e-6]
        if low and max(low) >= FLOOR_MIN - 1e-6:                          # laje de piso elevada (oficina, ruína: 0,375 u)
            floor[(i, j)] = (0.0, max(low))
        first = next((k for k, (a, b) in enumerate(spans) if a >= BODY_LO - 1e-6 and a < HEAD), None)
        rest = spans
        if first is not None:
            lo, top, k = spans[first][0], spans[first][1], first + 1
            while k < len(spans) and spans[k][0] - top <= GAP * vs + 1e-6:
                top = max(top, spans[k][1])
                k += 1
            body[(i, j)] = (lo, top)
            rest = spans[k:]
        rest = [sp for sp in rest if sp[0] >= BODY_LO - 1e-6]
        if rest:
            upper[(i, j)] = (rest[0][0], rest[-1][1])
    shapes = []
    # piso elevado onde não há corpo: pisável, com o topo no degrau que se sobe a pé (o herói entra sem pular; o pé fica
    # no máximo 0,075 u abaixo da laje de 0,375). Não bloqueia corpo nem câmera
    for k in list(floor):
        if k in body:
            del floor[k]
    for (x0, z0, x1, z1, lo, hi) in merge(floor, vs):
        shapes.append({"tipo": "caixa", "min": [r4(x0), r4(z0)], "max": [r4(x1), r4(z1)], "altura": [0.0, min(r4(hi), STEP)],
                       "corpo": False, "camera": False, "pisavel": True})
    for (x0, z0, x1, z1, lo, hi) in merge(body, vs):
        s = {"tipo": "caixa", "min": [r4(x0), r4(z0)], "max": [r4(x1), r4(z1)], "altura": [r4(lo), r4(hi)]}
        if hi < CAMERA_MIN:
            s["camera"] = False
        if hi <= STAND_MAX:
            s["pisavel"] = True
        shapes.append(s)
    # o que fica acima: só para a câmera, numa grade mais grossa (o telhado não precisa de precisão de voxel)
    coarse = {}
    k = max(1, round(GRID / vs))
    for (i, j), (a, b) in upper.items():
        g = (i // k, j // k)
        lo, hi = coarse.get(g, (1e9, -1e9))
        coarse[g] = (min(lo, a), max(hi, b))
    for (x0, z0, x1, z1, lo, hi) in merge(coarse, k * vs):
        shapes.append({"tipo": "caixa", "min": [r4(x0), r4(z0)], "max": [r4(x1), r4(z1)], "altura": [r4(lo), r4(hi)], "corpo": False})
    return {"tipo": "composta", "_fonte": "medido dos voxels por tools/conteudo/formas_compostas.py (revisado)", "formas": shapes}


def simple(rec):
    return {k: v for k, v in rec["colisao"].items() if not k.startswith("_") and k != "formas"}


def profiles():
    cat = json.load(open(os.path.join(ENV, "conteudo_catalogo.json")))
    man = json.load(open(os.path.join(ENV, "props.json")))
    out = {}
    published = {v.get("variante_de") for v in man.values() if v.get("variante_de")}
    for rec in sorted(cat["assets"], key=lambda a: a["id"]):
        if rec["estado"] == "rejeitado" or rec["id"] not in man:
            continue
        key = rec["id"] + "*" if rec["id"] in published else rec["id"]
        out[key] = composite(rec["id"], rec, man) if rec["colisao"].get("formas") else simple(rec)
    return out


def dump(doc):
    """O formato do arquivo: uma chave de topo por linha, um prop por linha; perfil composto com uma forma por linha."""
    j = lambda v: json.dumps(v, ensure_ascii=False)
    lines = ["{"]
    keys = list(doc)
    for ki, k in enumerate(keys):
        end = "," if ki < len(keys) - 1 else ""
        if k != "props":
            lines.append(f" {j(k)}: {j(doc[k])}{end}")
            continue
        lines.append(' "props": {')
        names = list(doc["props"])
        for ni, n in enumerate(names):
            p = doc["props"][n]
            pend = "," if ni < len(names) - 1 else ""
            if p.get("tipo") != "composta":
                lines.append(f"  {j(n)}: {j(p)}{pend}")
                continue
            head = {x: y for x, y in p.items() if x != "formas"}
            lines.append(f"  {j(n)}: {j(head)[:-1]}, \"formas\": [")
            for fi, f in enumerate(p["formas"]):
                lines.append(f"   {j(f)}{',' if fi < len(p['formas']) - 1 else ''}")
            lines.append(f"  ]}}{pend}")
        lines.append(" }" + end)
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    raw = open(COL, encoding="utf-8").read()
    doc = json.loads(raw)
    lib = profiles()
    v1 = {k for k in doc["props"] if k not in lib}
    if "--conferir" in sys.argv:
        same = dump(doc) == raw
        print("formato reproduzido byte a byte:", same)
        for k, p in lib.items():
            print(f"{k:22} {p['tipo']:9} {len(p.get('formas', []))} formas")
        return 0 if same else 1
    for k, p in lib.items():
        doc["props"][k] = p
    assert all(k in doc["props"] for k in v1), "um perfil da v1 sumiu"
    open(COL, "w", encoding="utf-8").write(dump(doc))
    print(f"{len(lib)} perfis da biblioteca em {os.path.relpath(COL, ROOT)} ({sum(p['tipo'] == 'composta' for p in lib.values())} compostos)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
