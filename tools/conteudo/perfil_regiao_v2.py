#!/usr/bin/env python3
"""Rodada 12 — perfil CONGELADO do gerador v2 (assets/scenes/regiao_v2_e.json): os dados de posicionamento dos assets que a
v2 distribui, copiados do catálogo (valumo.conteudo/1) e do props.json UMA vez e revisados.

O gerador v2 lê este arquivo, nunca o catálogo gerado: um modelo melhorado depois não pode mover uma região v2 já criada
(ADR 0029). O teste RegionProfileTest confere que o perfil ainda bate com o catálogo; se um asset mudar de pegada, a
correção é uma decisão (prop novo ou versão nova do gerador), nunca um ajuste silencioso.

Só entram assets com aprovação de integração "liberado" (ou restritos a conjuntos, que só aparecem dentro deles) e os
props aprovados da v1 que a v2 também usa (estalagem, lanterna, placa, posto, marco).

    python3 tools/conteudo/perfil_regiao_v2.py
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ENV = os.path.join(ROOT, "assets", "environment", "e")
OUT = os.path.join(ROOT, "assets", "scenes", "regiao_v2_e.json")
# props aprovados da v1 que a v2 reaproveita (a pegada sai do props.json: a caixa da malha)
V1_PROPS = ["house", "lantern_post", "signpost", "trail_post", "barrel", "crate", "log", "stump", "well", "bush",
            "bush_berry", "rock_s", "rock_m", "rock_l", "mushroom_big", "sapling"]


def r3(v):
    return round(v, 3)


def workers():
    """Rodada 12.1 (P76): quem exerce cada ofício de conjunto ({ofício: [moradores, em ordem]}), de habitantes_e.json →
    oficio. Congelado com o resto: um morador novo entra no perfil ANTES de a v2 ser publicada (depois, é versão nova)."""
    hab = json.load(open(os.path.join(ROOT, "assets", "scenes", "habitantes_e.json"), encoding="utf-8"))["habitantes"]
    out = {}
    for npc in sorted(hab):
        if hab[npc].get("oficio"):
            out.setdefault(hab[npc]["oficio"], []).append(npc)
    return out


def worker_spot(cat_by_id, s):
    """Rodada 12.2 — onde o trabalhador do conjunto fica, no referencial da âncora: [x, z, y, rumo, origem]. Padrão: o spawn
    "npc" da âncora (rumo 0: de frente, como o conjunto). Se um ITEM do conjunto tem uma vaga recomendada (pontos.vagas,
    "recomendada": true — a do feirante na barraca, PR #8), vale a vaga: a posição dela no item, girada e deslocada pelo
    item (a mesma conta de RegionV2.world: x' = c·x + s·z, z' = −s·x + c·z), e o rumo do item + o da vaga. Regra de dado:
    nunca o nome do morador."""
    anchor = cat_by_id[s["ancora"]]
    spot = None
    for e in anchor["pontos"].get("spawns", []):
        if e.get("tipo") == "npc":
            spot = [r3(e["pos_u"][0]), r3(e["pos_u"][2]), r3(e["pos_u"][1]), 0, "ancora:" + e["id"]]
            break
    for vg in anchor["pontos"].get("vagas", []) or []:                    # vaga recomendada na PRÓPRIA âncora (a herbalista)
        if vg.get("recomendada"):
            spot = [r3(vg["pos_u"][0]), r3(vg["pos_u"][2]), r3(vg["pos_u"][1]), r3(vg.get("rumo", 0)), "vaga:" + s["ancora"] + "#" + vg["id"]]
    for i in s["itens"]:
        rec = cat_by_id.get(i["id"].split("__")[0])
        for vg in (rec or {}).get("pontos", {}).get("vagas", []) or []:
            if not vg.get("recomendada"):
                continue
            t = math.radians(i.get("rumo", 0))
            c, sn = math.cos(t), math.sin(t)
            vx, vz = vg["pos_u"][0], vg["pos_u"][2]
            spot = [r3(i["pos_u"][0] + c * vx + sn * vz), r3(i["pos_u"][1] - sn * vx + c * vz), r3(vg["pos_u"][1]),
                    r3(i.get("rumo", 0) + vg.get("rumo", 0)), "vaga:" + i["id"] + "#" + vg["id"]]
    return spot


def main():
    cat = json.load(open(os.path.join(ENV, "conteudo_catalogo.json")))
    man = json.load(open(os.path.join(ENV, "props.json")))
    assets = {}
    for rec in sorted(cat["assets"], key=lambda a: a["id"]):
        ap = rec["aprovacao"]
        if ap["integracao"] not in ("liberado", "restrito_a_conjuntos"):
            continue
        p, m = rec["pontos"], rec["medidas"]
        peg = m["pegada_u"]
        a = {
            "categoria": rec["categoria"],
            "frequencia": rec.get("frequencia", "comum"),
            "integracao": ap["integracao"],
            "pegada": [r3(peg["min"][0]), r3(peg["min"][1]), r3(peg["max"][0]), r3(peg["max"][1])],
            "altura": r3(m["dimensoes_u"][1]),
            "livre": {k: r3(v) for k, v in sorted(rec.get("area_livre", {}).items())},
            "entradas": [[e["id"], r3(e["pos_u"][0]), r3(e["pos_u"][2]), e.get("rumo", 0)] for e in p.get("entradas", [])],
            "interacao": [[e["id"], r3(e["pos_u"][0]), r3(e["pos_u"][2]), r3(e["pos_u"][1])] for e in p.get("interacao", [])],
            "spawns": [[e["id"], r3(e["pos_u"][0]), r3(e["pos_u"][2]), e["tipo"]] for e in p.get("spawns", [])],
        }
        assets[rec["id"]] = a
    for n in V1_PROPS:
        e = man[n]
        vs, o, sz = e["voxelSize"], e["origin"], e["sizeVoxels"]
        assets[n] = {"categoria": "v1", "frequencia": "comum", "integracao": "liberado",
                     "pegada": [r3(o[0] * vs), r3(o[2] * vs), r3((o[0] + sz[0]) * vs), r3((o[2] + sz[2]) * vs)],
                     "altura": r3(sz[1] * vs), "livre": {}, "entradas": [], "interacao": [], "spawns": []}
    sets = {}
    cat_by_id = {a["id"]: a for a in cat["assets"]}
    for s in cat["conjuntos"]:
        if s["aprovacao"]["integracao"] != "liberado":
            continue
        sets[s["id"]] = {"oficio": s["oficio"], "ancora": s["ancora"],
                         "itens": [[i["id"], r3(i["pos_u"][0]), r3(i["pos_u"][1]), i.get("rumo", 0)] for i in s["itens"]],
                         "caixa": [r3(s["caixa_u"]["min"][0]), r3(s["caixa_u"]["min"][1]), r3(s["caixa_u"]["max"][0]), r3(s["caixa_u"]["max"][1])]}
        w = worker_spot(cat_by_id, s)
        if w:
            sets[s["id"]]["trabalhador"] = w
        for i in s["itens"]:
            base = i["id"].split("__")[0]
            if i["id"] not in assets and base in assets:            # variante publicada: a mesma pegada do original
                assets[i["id"]] = dict(assets[base], integracao="variante")
    for n, e in sorted(man.items()):                                     # variantes publicadas (a placa da estalagem, por ex.)
        base = e.get("variante_de")
        if base and n not in assets and base in assets:
            assets[n] = dict(assets[base], integracao="variante")
    doc = {"_doc": "Rodada 12 — perfil CONGELADO do gerador v2 (ADR 0029): pegada (u, espaço do modelo, x0 z0 x1 z1), altura, área "
                   "livre, entradas [id, x, z, rumo], interação [id, x, z] e spawns [id, x, z, tipo] de cada asset que a v2 usa, e os "
                   "conjuntos de ofício (itens [prop, x, z, rumo] no referencial da âncora; trabalhador [x, z, y, rumo, origem]: o spawn npc da âncora ou a vaga recomendada de um item, rodada 12.2). Escrito por tools/conteudo/perfil_regiao_v2.py "
                   "a partir do catálogo e revisado; o gerador lê ESTE arquivo, nunca o catálogo gerado. RegionProfileTest confere.",
           "formato": "valumo.regiao.perfil", "versao": 2, "assets": assets, "conjuntos": sets, "oficios": workers()}
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(json.dumps(doc, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(assets)} assets e {len(sets)} conjuntos em {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
