#!/usr/bin/env python3
"""Laboratório de conteúdo — vitrines no renderer real (composições layout=..., NÃO substituem nenhuma cena do jogo).

As POSIÇÕES são escritas à mão aqui (como numa cena), e este script faz o que não se deve chutar:
  * assenta cada construção pelo relevo medido (output/conteudo/relevo.json, de tools/conteudo/GroundProbe.java):
    dy = altura mais alta sob a pegada girada − altura no centro (a fundação de pedra cobre o lado baixo), e recusa
    o lugar se o desnível passar da inclinação máxima do catálogo;
  * põe NPCs existentes nos PONTOS DE SPAWN do catálogo (girados e transladados como o viewer gira o prop) — prova
    de que os metadados batem com a geometria no mundo;
  * escreve assets/scenes/vitrine_conteudo_*.json.

    .venv/bin/python tools/conteudo/vitrines.py        (depois de build_assets e do GroundProbe)
"""
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
CAT = os.path.join(ROOT, "assets", "environment", "e", "conteudo_catalogo.json")
RELEVO = os.path.join(ROOT, "output", "conteudo", "relevo.json")


def rot(x, z, deg):
    """O mesmo giro do viewer (Matrix4f.rotateY): +Z do modelo vai para +X com 90°."""
    a = math.radians(deg)
    return x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)


class Ground:
    def __init__(self):
        d = json.load(open(RELEVO))
        self.h, self.s, self.n = d["h"], d["passo"], d["n"]

    def at(self, x, z):
        ix = min(self.n - 1, max(0, round((x + 24) / self.s)))
        iz = min(self.n - 1, max(0, round((z + 24) / self.s)))
        return self.h[iz][ix]


def seat(g, rec, x, z, r):
    """dy para assentar a pegada girada no ponto mais alto do chão sob ela; devolve (dy, desnível)."""
    f = rec["medidas"]["pegada_u"]
    (x0, z0), (x1, z1) = f["min"], f["max"]
    hs = []
    steps = 8
    for i in range(steps + 1):
        for j in range(steps + 1):
            px, pz = rot(x0 + (x1 - x0) * i / steps, z0 + (z1 - z0) * j / steps, r)
            hs.append(g.at(x + px, z + pz))
    return round(max(hs) - g.at(x, z), 3), round(max(hs) - min(hs), 3)


def footprint_world(rec, x, z, r, grow=None):
    """Retângulo da pegada (opcionalmente crescido pela área livre: frente = +z, fundos = −z, lados = ±x), girado e
    transladado — devolve os 4 cantos no mundo."""
    (x0, z0), (x1, z1) = rec["medidas"]["pegada_u"]["min"], rec["medidas"]["pegada_u"]["max"]
    if grow:
        al = rec.get("area_livre") or {}
        side = al.get("lados_u", al.get("raio_u", 0))
        x0, x1 = x0 - side, x1 + side
        z0, z1 = z0 - al.get("fundos_u", al.get("raio_u", 0)), z1 + al.get("frente_u", al.get("raio_u", 0))
    return [tuple(a + b for a, b in zip(rot(cx, cz, r), (x, z))) for (cx, cz) in ((x0, z0), (x1, z0), (x1, z1), (x0, z1))]


def overlap(p, q):
    """Separação de eixos (SAT) entre dois retângulos girados."""
    for poly in (p, q):
        for i in range(4):
            ax, az = poly[(i + 1) % 4][1] - poly[i][1], -(poly[(i + 1) % 4][0] - poly[i][0])
            pa = [ax * x + az * z for (x, z) in p]
            qa = [ax * x + az * z for (x, z) in q]
            if max(pa) <= min(qa) or max(qa) <= min(pa):
                return False
    return True


def check_clearance(name, placements, cat):
    """Nenhuma pegada entra na ÁREA LIVRE de outra construção/POI (frente, lados, fundos do catálogo)."""
    big = [(n, x, z, r) for (n, x, z, r) in placements if n in cat and cat[n]["categoria"] in ("construcao", "poi", "estrutura")]
    for i, a in enumerate(big):
        for b in big:
            if a is b:
                continue
            if overlap(footprint_world(cat[a[0]], *a[1:], grow=True), footprint_world(cat[b[0]], *b[1:])):
                sys.exit(f"vitrine {name}: {b[0]} invade a área livre de {a[0]} (catálogo: area_livre)")


PATH = [(-6.5, -4.6), (-5.5, -2.0), (-2.5, 0.5), (0.5, 2.2), (1.8, 6.5), (0.2, 11), (-1.5, 16), (-0.5, 24)]   # World.PATH
POND = (7.8, 4.6)


def path_distance(x, z):
    best = 1e9
    for (ax, az), (bx, bz) in zip(PATH, PATH[1:]):
        dx, dz = bx - ax, bz - az
        t = max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)))
        best = min(best, math.hypot(ax + dx * t - x, az + dz * t - z))
    return best


def find_spot(n, want, r, placed, cat, g, step=0.5, radius=9.0):
    """O lugar válido mais perto de `want`: desnível ≤ 85% do máximo do catálogo, nenhuma área livre invadida (nos
    dois sentidos), fora do lago e com a pegada fora do caminho legado. Determinístico (varre em anéis)."""
    rec = cat[n]
    half = max(rec["medidas"]["dimensoes_u"][0], rec["medidas"]["dimensoes_u"][2]) / 2
    cands = sorted(((want[0] + i * step, want[1] + j * step) for i in range(-int(radius / step), int(radius / step) + 1)
                    for j in range(-int(radius / step), int(radius / step) + 1)),
                   key=lambda p: (round(math.hypot(p[0] - want[0], p[1] - want[1]), 3), p))
    for (x, z) in cands:
        if math.hypot(x - POND[0], z - POND[1]) < 4.8 + half or path_distance(x, z) < half * 0.9 or abs(x) > 20 or abs(z) > 20:
            continue
        anchor = cat.get(SETS[n]["ancora"]) if n in SETS else rec
        if seat(g, anchor, x, z, r)[1] > (rec.get("inclinacao_max_u") or 0.5) * 0.85:
            continue
        mine, mine_free = footprint_world(rec, x, z, r), footprint_world(rec, x, z, r, grow=True)
        if any(overlap(mine_free, footprint_world(cat[o[0]], *o[1:])) or overlap(footprint_world(cat[o[0]], *o[1:], grow=True), mine)
               for o in placed if o[0] in cat):
            continue
        return (n, round(x, 2), round(z, 2), r)
    sys.exit(f"sem lugar válido para {n} perto de {want}")


def build_scene(name, doc, placements, npcs, cat, g, report, walk=None):
    check_clearance(name, placements, cat)
    placements = expand(placements)
    if walk:
        walkability(name, placements, doc["hero"], cat, walk[0], walk[1], g, report)
    props = []
    blocks = [footprint_world(cat[n], x, z, r) for (n, x, z, r) in placements
              if n in cat and cat[n]["categoria"] in ("construcao", "poi", "estrutura")]
    for (n, x, z, r) in placements:
        rec = cat.get(n)
        if g.at(x, z) <= -0.42:                                  # nada posicionado dentro d'água (World.WATER_LEVEL)
            sys.exit(f"vitrine {name}: {n} em ({x}, {z}) cai dentro do lago")
        if (rec is None or rec["categoria"] not in ("construcao", "poi", "estrutura")) and any(
                overlap(b, [(x - 0.3, z - 0.3), (x + 0.3, z - 0.3), (x + 0.3, z + 0.3), (x - 0.3, z + 0.3)]) for b in blocks):
            report.append(f"{name}: {n} em ({x}, {z}) cairia dentro de uma pegada — removido")
            continue
        e = {"n": n, "x": x, "z": z, "r": r}
        if rec and rec["categoria"] in ("construcao", "poi", "estrutura") and n != "house":
            dy, slope = seat(g, rec, x, z, r)
            lim = rec.get("inclinacao_max_u") or 0.5
            ok = slope <= lim
            report.append(f"{name}: {n} em ({x}, {z}) r={r}: dy={dy} u, desnível {slope} u (máx {lim}) {'ok' if ok else 'RECUSADO'}")
            if not ok:
                sys.exit(f"vitrine {name}: {n} num lugar inclinado demais ({slope} > {lim}); escolha outro lugar")
            if dy > 0:
                e["dy"] = dy
        props.append(e)
    out_npcs = []
    for (rig, asset_id, spawn_id, clip) in npcs:
        pl = next(p for p in placements if p[0] == asset_id)
        rec = cat[asset_id]
        sp = next(s for s in rec["pontos"]["spawns"] if s["id"] == spawn_id)
        px, pz = rot(sp["pos_u"][0], sp["pos_u"][2], pl[3])
        out_npcs.append({"rig": rig, "x": round(pl[1] + px, 3), "z": round(pl[2] + pz, 3), "r": pl[3], "clip": clip})
        report.append(f"{name}: {rig} no spawn '{spawn_id}' de {asset_id} → ({out_npcs[-1]['x']}, {out_npcs[-1]['z']})")
    doc = dict(doc)
    doc["props"] = props
    doc["npcs"] = out_npcs
    path = os.path.join(ROOT, "assets", "scenes", f"{name}.json")
    with open(path, "w") as f:
        json.dump(doc, f, indent=1, ensure_ascii=False)
        f.write("\n")


def village(cat, g, wishes, radius=9.0):
    """Assenta as construções (ou CONJUNTOS de ofício) na ordem, cada um no lugar válido mais perto do desejado
    (find_spot, com a caixa do conjunto como pegada); a casa aprovada fica onde está (patamar canônico)."""
    placed = []
    for (n, want, r) in wishes:
        if n in SETS and n not in cat:
            cat[n] = set_record(cat, n)
        if n not in cat:
            placed.append((n, want[0], want[1], r))
            continue
        placed.append(find_spot(n, want, r, placed, cat, g, radius=radius))
    return placed


# ----------------------------------------------------------------------------- lote 2: conjuntos e vilas

SETS = {}


def load_sets():
    c = json.load(open(CAT))
    for s in c.get("conjuntos", []):
        SETS[s["id"]] = s


def set_record(cat, sid):
    """Registro sintético de um conjunto para a busca de lugar: pegada = caixa do conjunto, área livre = a da âncora."""
    s = SETS[sid]
    anc = cat[s["ancora"]]
    return {"id": sid, "categoria": "construcao", "inclinacao_max_u": anc["inclinacao_max_u"], "area_livre": anc["area_livre"],
            "medidas": {"pegada_u": s["caixa_u"], "dimensoes_u": [s["caixa_u"]["max"][0] - s["caixa_u"]["min"][0], 1,
                                                                   s["caixa_u"]["max"][1] - s["caixa_u"]["min"][1]]}}


def expand(placed):
    """Troca cada conjunto posicionado pela âncora + itens (girados e transladados como a âncora)."""
    out = []
    for (n, x, z, r) in placed:
        if n in SETS:
            s = SETS[n]
            out.append((s["ancora"], x, z, r))
            for it in s["itens"]:
                px, pz = rot(it["pos_u"][0], it["pos_u"][1], r)
                out.append((it["id"], round(x + px, 3), round(z + pz, 3), (r + it["rumo"]) % 360))
        else:
            out.append((n, x, z, r))
    return out


def obstacles(placements, cat, man, prof):
    """Formas de colisão no mundo (para a caminhabilidade): formas compostas quando o catálogo as declara, senão o perfil
    simples (o do catálogo, ou o de colisao_e.json para os aprovados) — a mesma conta de Collision.addProp."""
    out = []
    for (n, x, z, r) in placements:
        rec = cat.get(n) or cat.get(n.split("__")[0])
        if rec is not None and "medidas" in rec and "caixa_u" in rec["medidas"]:
            col = rec["colisao"]
            lo, hi = rec["medidas"]["caixa_u"]["min"], rec["medidas"]["caixa_u"]["max"]
            if col.get("formas"):
                for b in rec["medidas"].get("formas_medidas_u", []):
                    out.append(("poly", rect_box(b, x, z, r)))
                for f in rec["pontos"].get("formas_u", []):
                    cx, cz = rot(f["centro"][0], f["centro"][1], r)
                    out.append(("circ", (x + cx, z + cz, f["raio"])))
                continue
            kind, k = col["tipo"], col
        else:
            e = man.get(n)
            if e is None:
                continue
            vs, o, sz = e["voxelSize"], e["origin"], e["sizeVoxels"]
            lo = [o[0] * vs, o[1] * vs, o[2] * vs]
            hi = [(o[0] + sz[0]) * vs, (o[1] + sz[1]) * vs, (o[2] + sz[2]) * vs]
            k = profile_for(prof, n)
            kind = k["tipo"]
        cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
        wx, wz = rot(cx, cz, r)
        w, d = hi[0] - lo[0], hi[2] - lo[2]
        if kind == "caixa":
            sc = k.get("escala", 1.0)
            out.append(("poly", rect_box([cx - w / 2 * sc, cz - d / 2 * sc, cx + w / 2 * sc, cz + d / 2 * sc], x, z, r)))
        elif kind == "circulo":
            out.append(("circ", (x + wx, z + wz, k.get("raio", min(w, d) / 2))))
        elif kind == "tronco":
            out.append(("circ", (x + wx, z + wz, k.get("raio", 0.3))))
    return out


def rect_box(b, x, z, r):
    return [tuple(a + c for a, c in zip(rot(px, pz, r), (x, z))) for (px, pz) in ((b[0], b[1]), (b[2], b[1]), (b[2], b[3]), (b[0], b[3]))]


def profile_for(prof, name):
    props = prof["props"]
    if name in props:
        return props[name]
    for k in props:
        if k.endswith("*") and name.startswith(k[:-1]):
            return props[k]
    return prof["padrao"]


def inside(obs, x, z, pad):
    for kind, g in obs:
        if kind == "circ":
            if math.hypot(x - g[0], z - g[1]) < g[2] + pad:
                return True
        else:
            # ponto dentro do polígono convexo crescido por pad (distância às arestas)
            inn = True
            for i in range(4):
                (ax, az), (bx, bz) = g[i], g[(i + 1) % 4]
                ex, ez = bx - ax, bz - az
                ln = math.hypot(ex, ez) or 1
                cross = (ex * (z - az) - ez * (x - ax)) / ln
                if cross < -pad:                                  # orientação dos cantos: sentido anti-horário
                    inn = False
                    break
            if inn:
                return True
    return False


def walkability(name, placements, hero, cat, man, prof, g, report):
    """Busca em grade de 0,25 u (a resolução da conferência de alcance da rodada 11): célula livre se fora de toda forma
    de colisão crescida pelo raio do herói (0,3 u), fora d'água; vizinhas ligadas se o degrau do relevo ≤ 0,30 u.
    Alvos: cada ENTRADA de construção/estrutura e cada PONTO DE INTERAÇÃO dos assets do laboratório."""
    obs = obstacles(placements, cat, man, prof)
    step, half = 0.25, 20.0
    n = int(2 * half / step)
    def cell(x, z):
        return int((x + half) / step), int((z + half) / step)
    def center(i, j):
        return -half + (i + 0.5) * step, -half + (j + 0.5) * step
    free = {}
    def ok(i, j):
        if (i, j) not in free:
            x, z = center(i, j)
            free[(i, j)] = g.at(x, z) > -0.42 and not inside(obs, x, z, 0.3)      # World.WATER_LEVEL = −0,42
        return free[(i, j)]
    start = cell(hero[0], hero[1])
    seen = {start} if ok(*start) else set()
    stack = list(seen)
    while stack:
        i, j = stack.pop()
        hx, hz = center(i, j)
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = (i + di, j + dj)
            if k in seen or not (0 <= k[0] < n and 0 <= k[1] < n) or not ok(*k):
                continue
            kx, kz = center(*k)
            if abs(g.at(kx, kz) - g.at(hx, hz)) > 0.30:
                continue
            seen.add(k)
            stack.append(k)
    targets, bad = 0, []
    for (pn, x, z, r) in placements:
        rec = cat.get(pn) or cat.get(pn.split("__")[0])
        if rec is None or "pontos" not in rec:
            continue
        for kind in ("entradas", "interacao"):
            for pt in rec["pontos"][kind]:
                px, pz = rot(pt["pos_u"][0], pt["pos_u"][2], r)
                tx, tz = x + px, z + pz
                ti, tj = cell(tx, tz)
                hit = any((ti + a, tj + b) in seen for a in (-1, 0, 1) for b in (-1, 0, 1))
                targets += 1
                if not hit:
                    bad.append(f"{pn}/{kind}/{pt['id']} em ({tx:.2f}, {tz:.2f})")
    report.append(f"{name}: caminhabilidade {targets - len(bad)}/{targets} alvos alcançáveis a partir do herói; "
                  f"{len(seen)} células livres ligadas")
    for b_ in bad:
        report.append(f"{name}:   INALCANÇÁVEL {b_}")
    return targets, bad


BACK_TREES = ["tree_oak", "tree_oak3", "tree_oak", "tree_oak2", "tree_pine", "tree_pine", "tree_birch", "tree_oak4"]
CLOUDS = [[-18, 13, -20], [6, 15, -26], [22, 12, -18]]


def main():
    cat = {a["id"]: a for a in json.load(open(CAT))["assets"]}
    load_sets()
    man = json.load(open(os.path.join(ROOT, "assets", "environment", "e", "props.json")))
    prof = json.load(open(os.path.join(ROOT, "assets", "scenes", "colisao_e.json")))
    for c in list(cat.values()):                     # variantes publicadas usam o registro do asset-base
        for v in c["variacoes"]["extremos"]:
            if v.get("prop"):
                cat[v["prop"]] = {**c, "id": v["prop"], "medidas": {**c["medidas"], "pegada_u": v["pegada_u"]}}
    # a casa aprovada entra na conferência de área livre com a pegada do props.json (não é do catálogo do laboratório)
    hp = json.load(open(os.path.join(ROOT, "assets", "environment", "e", "props.json")))["house"]
    vs, o, sz = hp["voxelSize"], hp["origin"], hp["sizeVoxels"]
    cat["house"] = {"id": "house", "categoria": "construcao", "inclinacao_max_u": 1.0,
                    "area_livre": {"frente_u": 2.0, "lados_u": 0.6, "fundos_u": 0.6},
                    "medidas": {"pegada_u": {"min": [o[0] * vs, o[2] * vs], "max": [(o[0] + sz[0]) * vs, (o[2] + sz[2]) * vs]},
                                "dimensoes_u": [sz[0] * vs, sz[1] * vs, sz[2] * vs]}}
    g = Ground()
    report = []
    # ---------------------------------------------------------------- construções (com a casa aprovada como régua)
    build_scene("vitrine_conteudo_construcoes", {
        "_doc": "Laboratório de conteúdo, lote 1 — vitrine de CONSTRUÇÕES (gerada por tools/conteudo/vitrines.py; NÃO "
                "substitui nenhuma cena). Casa aprovada no patamar como régua; sobrado, oficina, cabana redonda, cabana do "
                "caçador e torre de vigia assentados pelo relevo medido; NPCs existentes nos pontos de spawn do catálogo.",
        "hero": [3.4, 0.6, 200],
        "scatter": [{"props": BACK_TREES, "count": 55, "ring": [19.0, 24], "seed": 11, "minDist": 2.8}],
        "creatures": [], "clouds": CLOUDS,
    }, vil := village(cat, g, [("house", (-6.5, -8.0), 0), ("house_tall", (1.0, -8.5), 0), ("workshop_forge", (8.5, -5.0), 0),
                         ("hut_herbalist", (-2.0, -2.0), 20), ("cabin_hunter", (-12.5, -2.0), 70), ("tower_watch", (12.5, -9.5), 315)]) + [
        ("barrel", -2.4, -4.6, 0), ("crate", 3.6, -5.2, 20), ("lantern_post", -2.6, -2.6, 0),
        ("bush", -4.0, 1.8, 0), ("flower_yellow", 2.6, 1.6, 0), ("grass_tall", 7.6, 1.2, 0),
    ], [
        ("characters/npc/indira", "hut_herbalist", "herbalista", "idle"),
        ("characters/npc/bramor", "tower_watch", "vigia", "idle"),
        ("characters/npc/kelyra", "house_tall", "comerciante", "idle"),
    ], cat, g, report)
    # ---------------------------------------------------------------- vegetação (mesmas condições, aprovadas ao lado)
    build_scene("vitrine_conteudo_vegetacao", {
        "_doc": "Laboratório de conteúdo, lote 1 — vitrine de VEGETAÇÃO: as quatro árvores novas entre as aprovadas, na "
                "mesma luz e relevo; anel de fundo misturando novas e aprovadas (como uma mata gerada as usaria).",
        "hero": [1.0, 2.0, 190],
        "scatter": [{"props": ["tree_oak", "tree_forked", "tree_poplar", "tree_oak3", "tree_pine", "tree_forked", "tree_oak",
                               "tree_poplar", "tree_birch", "tree_dead"], "count": 70, "ring": [15.0, 24], "seed": 23, "minDist": 2.6}],
        "creatures": [], "clouds": CLOUDS,
    }, [
        ("tree_oak", -6.0, -6.0, 0), ("tree_forked", -1.5, -7.0, 0), ("tree_poplar", 2.8, -7.4, 0),
        ("tree_gnarled", 8.0, -6.6, 0), ("tree_dead", 12.4, -3.6, 0), ("tree_willow", 11.0, 3.4, 0),
        ("tree_pine", -9.5, -1.0, 0), ("tree_poplar", 4.6, -9.6, 90), ("tree_forked", -4.0, -11.0, 90),
        ("bush", 0.6, -4.2, 0), ("fern", 6.0, -4.0, 0), ("rock_m", -3.2, -3.4, 30), ("flower_white", 3.6, -3.0, 0),
    ], [], cat, g, report)
    # ---------------------------------------------------------------- pontos de interesse
    build_scene("vitrine_conteudo_poi", {
        "_doc": "Laboratório de conteúdo, lote 1 — vitrine de PONTOS DE INTERESSE: torre arruinada, círculo de pedras e "
                "ruína de casa num pequeno terreno, com a árvore antiga e a seca como acompanhantes (mesma cena, mesma luz).",
        "hero": [3.0, 1.6, 200],
        "scatter": [{"props": BACK_TREES, "count": 50, "ring": [19.5, 24], "seed": 31, "minDist": 2.8}],
        "creatures": [], "clouds": CLOUDS,
    }, village(cat, g, [("stone_circle", (2.5, -6.5), 0), ("ruin_tower", (11.0, -9.0), 20),
                         ("ruin_house", (-6.5, 1.5), 30)]) + [
        ("tree_gnarled", -3.5, -12.5, 0), ("tree_dead", -2.8, -9.6, 0), ("rock_l", 5.8, -12.4, 0), ("rock_s", 6.6, -1.4, 0),
        ("bush", -1.8, -1.6, 0), ("grass_tall", 3.2, 0.6, 0), ("fern", 12.2, -9.8, 0),
    ], [
        ("characters/npc/teodor", "ruin_house", "escombros", "idle"),
    ], cat, g, report)
    # ---------------------------------------------------------------- lote 2: duas vilas (mesma luz, relevo e tomadas)
    walk = (man, prof)
    vila_a = village(cat, g, [("house", (-6.5, -8.0), 0), ("fountain_square", (1.6, -0.6), 0),
                              ("conjunto.comerciante", (6.8, -4.6), 305), ("conjunto.ferreiro", (-4.6, 4.2), 125),
                              ("conjunto.guardiao", (4.4, 10.6), 260), ("shed_storage", (-10.5, -2.5), 90)], radius=6.0)
    build_scene("vitrine_conteudo_vila_a", {
        "_doc": "Laboratório de conteúdo, lote 2 — VILA A, assentamento de caminho (compacto): construções coladas no "
                "caminho e viradas para ele, praça com chafariz, mesa e bancos, conjuntos de ofício (comerciante, "
                "ferreiro, guardião de trilhas) como dados do catálogo, a estalagem aprovada no patamar. Vitrine artística: "
                "NÃO substitui a vila do jogo. Gerada por tools/conteudo/vitrines.py.",
        "hero": [0.4, 2.6, 200],
        "scatter": [{"props": BACK_TREES, "count": 55, "ring": [18.5, 24], "seed": 51, "minDist": 2.8}],
        "creatures": [], "clouds": CLOUDS,
    }, vila_a + [
        ("table_bench", -0.9, -3.0, 20), ("bench_wood", 4.6, 1.6, 270), ("bench_wood", -1.8, 1.4, 120),
        ("lantern_post", -3.6, -1.6, 0), ("lantern_post", 2.8, 5.0, 0), ("barrel", -5.2, -4.4, 0),
        ("cart_hay", 2.4, 13.6, 160), ("fence_picket", -8.8, -3.4, 0),
    ], [
        ("characters/npc/kelyra", "house_tall", "comerciante", "idle"),
        ("characters/npc/bramor", "tower_watch", "vigia", "idle"),
    ], cat, g, report, walk=walk)
    vila_b = village(cat, g, [("conjunto.cacador", (-13.0, -1.5), 80), ("conjunto.herbalista", (-6.0, 10.5), 100),
                              ("granary_raised", (9.5, -9.5), 330), ("crop_field", (12.6, -3.6), 0),
                              ("crop_field", (5.0, -12.0), 90), ("well_windlass", (2.6, -3.4), 20)], radius=6.0)
    build_scene("vitrine_conteudo_vila_b", {
        "_doc": "Laboratório de conteúdo, lote 2 — VILA B, comunidade rural (dispersa): sítios afastados (caçador na "
                "borda da mata, herbalista junto ao caminho), paiol e hortas como área de trabalho, poço de sarilho como "
                "ponto de encontro, mureta de pedra, carroça, árvores entre as casas e muito espaço aberto. Vitrine "
                "artística: NÃO substitui a vila do jogo. Gerada por tools/conteudo/vitrines.py.",
        "hero": [0.4, 2.6, 200],
        "scatter": [{"props": BACK_TREES + ["tree_forked", "tree_poplar"], "count": 60, "ring": [18.5, 24], "seed": 52,
                     "minDist": 2.6}],
        "creatures": [], "clouds": CLOUDS,
    }, vila_b + [
        ("scarecrow", 13.2, -3.4, 200), ("cart_hay", 6.2, -6.6, 120), ("woodpile_axe", 8.0, -12.4, 0),
        ("fence_stone", 15.4, -7.0, 90), ("fence_stone", 15.4, -5.0, 90), ("fence_stone", 15.4, -3.0, 90),   # divisa
        ("fence_stone", 14.4, -1.9, 0), ("fence_stone", 12.4, -1.9, 0),                                      # contínua
        ("basket_group", 10.4, -0.4, 0), ("bench_wood", 4.4, -1.4, 290),
        ("tree_oak", -3.0, -6.0, 0), ("tree_forked", 4.4, 10.2, 0), ("tree_poplar", 0.2, -9.6, 0),
        ("tree_poplar", -1.6, -10.4, 0), ("tree_gnarled", -14.0, 8.0, 0), ("bush", -8.6, 2.0, 0),
        ("bush_berry", 6.8, -15.0, 0), ("flower_yellow", 3.6, 2.8, 0), ("grass_tall", -2.6, 4.0, 0),
    ], [
        ("characters/npc/indira", "hut_herbalist", "herbalista", "idle"),
    ], cat, g, report, walk=walk)

    # ---------------------------------------------------------------- desempenho (três densidades; só para o --bench)
    lab_trees = ["tree_forked", "tree_poplar", "tree_gnarled", "tree_dead", "tree_forked", "tree_poplar"]
    buildings = ["house_tall", "workshop_forge", "hut_herbalist", "cabin_hunter", "tower_watch", "ruin_tower", "ruin_house"]
    small = ["bush", "fern", "grass_tall", "flower_white", "rock_s", "mushroom_red"]
    for (name, scat) in (
        ("bench_conteudo_baixa", [{"props": lab_trees, "count": 12, "ring": [15.0, 24], "seed": 41, "minDist": 3.0}]),
        ("bench_conteudo_normal", [{"props": BACK_TREES + lab_trees, "count": 84, "ring": [13.0, 24], "seed": 42, "minDist": 2.6},
                                   {"props": small, "count": 40, "ring": [3.0, 14], "seed": 43, "minDist": 1.2, "cluster": 3}]),
        ("bench_conteudo_alta", [{"props": buildings, "count": 30, "ring": [6.0, 22], "seed": 44, "minDist": 4.5},
                                 {"props": BACK_TREES + lab_trees + lab_trees, "count": 260, "ring": [3.0, 24], "seed": 45, "minDist": 1.4},
                                 {"props": small, "count": 160, "ring": [2.0, 23], "seed": 46, "minDist": 0.8, "cluster": 4}]),
    ):
        doc = {"_doc": f"Laboratório de conteúdo — cena de DESEMPENHO ({name.split('_')[-1]}), só para viewer --bench "
                       "(layout=...). Não é composição artística.",
               "hero": [0.0, 0.0, 180], "scatter": scat, "creatures": [], "clouds": CLOUDS}
        pl = [] if name.endswith("alta") else [p for p in vil if p[0] != "house"]   # o mesmo assentamento validado
        build_scene(name, doc, pl, [], cat, g, report)
    os.makedirs(os.path.join(ROOT, "output", "conteudo"), exist_ok=True)
    with open(os.path.join(ROOT, "output", "conteudo", "vitrines.txt"), "w") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))


if __name__ == "__main__":
    main()
