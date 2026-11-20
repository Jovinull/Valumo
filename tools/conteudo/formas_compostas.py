#!/usr/bin/env python3
"""Rodada 12 — perfis de colisão dos assets da biblioteca em assets/scenes/colisao_e.json (o MESMO arquivo que o jogo lê).

* Assets com interior (o catálogo marca `colisao.formas`): perfil `"tipo": "composta"`, com as formas MEDIDAS dos voxels
  no espaço do modelo (u), que giram com o prop:
    - sólido (rodada 12.1): toda peça que começa abaixo da cabeça de quem está no chão sob ela é corpo E pisável, com a
      base e o topo EXATOS do voxel (laje, degrau, parede, peça baixa, muro quebrado); fundidas em retângulos SÓ entre
      colunas da mesma base e do mesmo topo. Peça baixa (topo < 1,2 u) não bloqueia a câmera;
    - arco, lintel, telhado, toldo e a copa acima da cabeça: forma SÓ de câmera (corpo livre), fundida numa grade de
      0,25 u, da base ao topo do que está lá.
* Os outros assets da biblioteca: o perfil simples que o catálogo já entrega (`colisao`, sem as notas); variantes
  publicadas (`<id>__…`) usam o do original por prefixo (`"<id>*"`).

Os perfis dos props que o gerador v1 planta NÃO são tocados (a referência das sementes da v1 depende deles). O resto do
arquivo sai byte a byte igual. Revise o diff antes de commitar; o teste CompositeCollisionTest confere a cobertura.

    python3 tools/conteudo/formas_compostas.py [--conferir]
"""
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
sys.path.insert(0, os.path.join(ROOT, "tools"))
import voxlib  # noqa: E402

ENV = os.path.join(ROOT, "assets", "environment", "e")
COL = os.path.join(ROOT, "assets", "scenes", "colisao_e.json")
HEAD = 1.2               # altura do herói (Player.HEIGHT): o que começa acima disto não bloqueia o corpo
GAP = 2                  # vão (em voxels) que ainda conta como a mesma parede
CAMERA_MIN = 1.2         # topo abaixo disso: peça baixa, a câmera passa por cima
GRID = 0.25              # grade das formas de câmera (a da conferência de alcance)
REACH = 2.0              # topo que ninguém alcança (laje 0,375 + salto + degrau < 2 u): o topo exato só importa à câmera,
                         # então paredes altas agrupam o topo numa grade de GRID para cima (menos formas, mesmo corpo)
STEP = 0.30              # o degrau que se sobe a pé (Player.STEP); no ar também se pega um topo até um degrau acima dos pés


def jump_reach():
    """Quanto acima dos pés o herói pega um topo: a altura do salto (combate_e.json → salto) mais o degrau."""
    doc = json.load(open(os.path.join(ROOT, "assets", "scenes", "combate_e.json"), encoding="utf-8"))
    return doc["salto"]["altura"] + STEP


def r4(v):
    return round(v * 16) / 16                                    # 1/16 u: o que o voxel mais fino resolve


def columns(name, man, foliage=False):
    """{(cx, cz) em u do centro da coluna: [(y0, y1) em u, ordenados]} dos voxels não-folhagem do modelo (ou só da
    folhagem, com `foliage`)."""
    e = man[name]
    vs, o = e["voxelSize"], e["origin"]
    v = voxlib.load_vox(os.path.join(ENV, e["file"]))
    cols = {}
    for (x, y, z), c in v.v.items():
        if (c[3] == "foliage") != foliage:
            continue
        cx, cz = (x + o[0]) * vs + vs / 2, (z + o[2]) * vs + vs / 2
        y0 = (y + o[1]) * vs
        cols.setdefault((cx, cz), []).append((y0, y0 + vs))
    for k in cols:
        cols[k].sort()
    return cols, vs


def inside(px, pz, rect):
    return rect[0] <= px < rect[2] and rect[1] <= pz < rect[3]


def reachable_tops(runs_of, jump, vs):
    """Topos que o herói alcança: busca sobre os lugares de pé (coluna, altura), a partir do chão livre em volta. Um lugar
    só é de pé se o corpo cabe ali: nenhuma coluna a até RADIUS tem sólido entre um degrau acima dos pés e a cabeça (sem
    isso a busca subia por saliências de 1 voxel até o telhado). De um lugar de pé passa a outro vizinho até `jump` acima
    (salto + degrau) ou mais abaixo. Não confere o caminho pelo ar: no máximo sobra forma sólida onde ninguém chega."""
    from collections import deque
    rad = max(1, round(0.26 / vs))                                         # o raio do herói (Player.RADIUS) em voxels
    xs = [c[0] for c in runs_of]
    zs = [c[1] for c in runs_of]
    x0, x1, z0, z1 = min(xs) - rad - 1, max(xs) + rad + 1, min(zs) - rad - 1, max(zs) + rad + 1

    def fits(c, t):
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                for lo, hi in runs_of.get((c[0] + dx, c[1] + dz), ()):
                    if hi > t + STEP + 1e-6 and lo < t + HEAD - 1e-6:
                        return False
        return True

    def places(c):
        runs = runs_of.get(c, [])
        out = [] if runs and runs[0][0] <= 1e-6 else [(-1, 0.0)]          # o chão, se a coluna está livre embaixo
        out += [(k, r[1]) for k, r in enumerate(runs)]
        return [(k, t) for k, t in out if fits(c, t)]

    seen, q = set(), deque()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for k, t in places((x, z)):
                if k == -1 and (x in (x0, x1) or z in (z0, z1)):        # a borda: o chão de fora, por onde se chega
                    seen.add(((x, z), -1))
                    q.append(((x, z), 0.0))
    while q:
        (x, z), h = q.popleft()
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if not (x0 <= n[0] <= x1 and z0 <= n[1] <= z1):
                    continue
                for k, t in places(n):
                    if (n, k) in seen or t > h + jump + 1e-6:
                        continue
                    seen.add((n, k))
                    q.append((n, t))
    return {s for s in seen if s[1] >= 0}


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
    """Formas medidas coluna a coluna (o voxel do modelo). Rodada 12.1: UMA regra para tudo o que é sólido.

    Em cada coluna, os voxels (sem folhagem) formam corridas contínuas (vão de até GAP voxels conta como a mesma peça).
    Subindo a partir do chão (0):
      - corrida que começa abaixo de chão + altura do herói é SÓLIDA: corpo E pisável, com a base e o topo EXATOS do
        voxel (laje, degrau, parede, bigorna, muro quebrado). Quem está acima dela fica em cima; quem está ao lado e
        abaixo do topo menos um degrau é barrado; um degrau (topo até 0,30 u acima dos pés) se sobe andando.
        Segura a câmera se o topo passa de 1,2 u (peça baixa: a câmera passa por cima);
      - o que começa a uma altura de herói ou mais acima do topo sólido (arco, lintel, telhado, toldo) é SÓ de câmera.
    A copa de folhagem acima da cabeça também é só de câmera. Fusão em retângulos gulosos SÓ entre colunas da mesma
    base e do mesmo topo: um retângulo nunca tem um topo que a geometria não tem (rodada 12: a parede em degraus virava
    um bloco com o topo do degrau mais alto, e quem pulava nela afundava)."""
    cols, vs = columns(name, man)
    runs_of = {}
    for (px, pz), spans in cols.items():
        i, j = round((px - vs / 2) / vs), round((pz - vs / 2) / vs)
        runs = []
        for a0, b0 in spans:
            if runs and a0 - runs[-1][1] <= GAP * vs + 1e-6:
                runs[-1][1] = max(runs[-1][1], b0)
            else:
                runs.append([a0, b0])
        runs = [[max(a0, 0.0), b0] for a0, b0 in runs if b0 > 1e-6]      # sem a fundação enterrada
        if runs:
            runs_of[(i, j)] = runs
    reach = reachable_tops(runs_of, jump_reach(), vs)
    solid, upper = {}, {}
    for (i, j), runs in runs_of.items():
        f, pieces = 0.0, []
        for k, (lo, hi) in enumerate(runs):
            # sólido: começa abaixo da cabeça de quem está no topo sólido de baixo, OU o herói alcança o topo dele (degrau
            # alto de escada, beiral ao lado de um balcão): o que se alcança tem de segurar, senão se atravessa a pedra
            if lo < f + HEAD - 1e-6 or ((i, j), k) in reach:
                top = hi if hi <= REACH else math.ceil(hi / GRID - 1e-6) * GRID
                pieces.append((r4(lo), r4(top)))
                f = hi
            else:
                rest = runs[k:]
                upper[(i, j)] = (rest[0][0], rest[-1][1])
                break
        if pieces:
            solid[(i, j)] = tuple(pieces)
    # folhagem não empurra o corpo (o herói passa entre os arbustos), mas a COPA acima da cabeça segura a câmera, como a
    # copa das árvores soltas (perfil "tronco": copa só para a câmera). Arbusto baixo (abaixo da cabeça) não segura nada
    leaves, _ = columns(name, man, foliage=True)
    for (px, pz), spans in leaves.items():
        i, j = round((px - vs / 2) / vs), round((pz - vs / 2) / vs)
        high = [sp for sp in spans if sp[0] >= HEAD - 1e-6]
        if high:
            a, b = upper.get((i, j), (1e9, -1e9))
            upper[(i, j)] = (min(a, high[0][0]), max(b, high[-1][1]))
    shapes = []
    # sólidos: agrupados pela (base, topo) exata de cada peça da coluna; um retângulo por grupo contíguo
    groups = {}
    for cell, pieces in solid.items():
        for piece in pieces:
            groups.setdefault(piece, {})[cell] = piece
    for (lo, hi) in sorted(groups):
        for (x0, z0, x1, z1, _, _) in merge(groups[(lo, hi)], vs):
            s = {"tipo": "caixa", "min": [r4(x0), r4(z0)], "max": [r4(x1), r4(z1)], "altura": [lo, hi], "pisavel": True}
            if hi < CAMERA_MIN:
                s["camera"] = False
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
