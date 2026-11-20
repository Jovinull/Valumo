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
    big = [(n, x, z, r) for (n, x, z, r) in placements if n in cat and cat[n]["categoria"] in ("construcao", "poi")]
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
        if seat(g, rec, x, z, r)[1] > (rec.get("inclinacao_max_u") or 0.5) * 0.85:
            continue
        mine, mine_free = footprint_world(rec, x, z, r), footprint_world(rec, x, z, r, grow=True)
        if any(overlap(mine_free, footprint_world(cat[o[0]], *o[1:])) or overlap(footprint_world(cat[o[0]], *o[1:], grow=True), mine)
               for o in placed if o[0] in cat):
            continue
        return (n, round(x, 2), round(z, 2), r)
    sys.exit(f"sem lugar válido para {n} perto de {want}")


def build_scene(name, doc, placements, npcs, cat, g, report):
    check_clearance(name, placements, cat)
    props = []
    blocks = [footprint_world(cat[n], x, z, r) for (n, x, z, r) in placements
              if n in cat and cat[n]["categoria"] in ("construcao", "poi")]
    for (n, x, z, r) in placements:
        rec = cat.get(n)
        if (rec is None or rec["categoria"] not in ("construcao", "poi")) and any(
                overlap(b, [(x - 0.3, z - 0.3), (x + 0.3, z - 0.3), (x + 0.3, z + 0.3), (x - 0.3, z + 0.3)]) for b in blocks):
            report.append(f"{name}: {n} em ({x}, {z}) cairia dentro de uma pegada — removido")
            continue
        e = {"n": n, "x": x, "z": z, "r": r}
        if rec and rec["categoria"] in ("construcao", "poi") and n != "house":
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


def village(cat, g, wishes):
    """Assenta as construções na ordem, cada uma no lugar válido mais perto do desejado (find_spot); a casa aprovada
    fica onde está (patamar canônico)."""
    placed = []
    for (n, want, r) in wishes:
        if n not in cat:
            placed.append((n, want[0], want[1], r))
            continue
        placed.append(find_spot(n, want, r, placed, cat, g))
    return placed


BACK_TREES = ["tree_oak", "tree_oak3", "tree_oak", "tree_oak2", "tree_pine", "tree_pine", "tree_birch", "tree_oak4"]
CLOUDS = [[-18, 13, -20], [6, 15, -26], [22, 12, -18]]


def main():
    cat = {a["id"]: a for a in json.load(open(CAT))["assets"]}
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
