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
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ENV = os.path.join(ROOT, "assets", "environment", "e")
OUT = os.path.join(ROOT, "assets", "scenes", "regiao_v2_e.json")
# props aprovados da v1 que a v2 reaproveita (a pegada sai do props.json: a caixa da malha)
V1_PROPS = ["house", "lantern_post", "signpost", "trail_post", "barrel", "crate", "log", "stump", "well", "bush",
            "bush_berry", "rock_s", "rock_m", "rock_l", "mushroom_big", "sapling"]


def r3(v):
    return round(v, 3)


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
            "interacao": [[e["id"], r3(e["pos_u"][0]), r3(e["pos_u"][2])] for e in p.get("interacao", [])],
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
    for s in cat["conjuntos"]:
        if s["aprovacao"]["integracao"] != "liberado":
            continue
        sets[s["id"]] = {"oficio": s["oficio"], "ancora": s["ancora"],
                         "itens": [[i["id"], r3(i["pos_u"][0]), r3(i["pos_u"][1]), i.get("rumo", 0)] for i in s["itens"]],
                         "caixa": [r3(s["caixa_u"]["min"][0]), r3(s["caixa_u"]["min"][1]), r3(s["caixa_u"]["max"][0]), r3(s["caixa_u"]["max"][1])]}
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
                   "conjuntos de ofício (itens [prop, x, z, rumo] no referencial da âncora). Escrito por tools/conteudo/perfil_regiao_v2.py "
                   "a partir do catálogo e revisado; o gerador lê ESTE arquivo, nunca o catálogo gerado. RegionProfileTest confere.",
           "formato": "valumo.regiao.perfil", "versao": 2, "assets": assets, "conjuntos": sets}
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(json.dumps(doc, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(assets)} assets e {len(sets)} conjuntos em {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
