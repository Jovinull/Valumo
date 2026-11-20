#!/usr/bin/env python3
"""Laboratório de conteúdo — vitrines do ESTUDO de NPCs de ofício e ruínas (cenas NOVAS; nenhuma cena existente muda).

  vitrine_estudo_ruinas   a ruína de casa aprovada (régua), a abóbada soterrada e o sítio abandonado, no mesmo relevo
                          medido, com o herói; caminhabilidade até cada entrada e cada ponto de interação.
  vitrine_estudo_npcs     um quarteirão de ofícios: oficina com forja (Brásia), barraca de feira (Quelmo), cabana do
                          caçador (Arvel), ao lado da cabana da herbalista com Índira e da torre de vigia com Bramor
                          (aprovados, para comparar escala, cor e silhueta no mundo).

Brásia fica no ponto da oficina corrigido na rodada 12 (P73: (1,94; 0,44) no espaço do modelo, 0,32 u da peça mais
perto); o spawn da `main` ainda é o antigo, a 0,125 u da bigorna, e não é alterado aqui (asset aprovado).

    .venv/bin/python tools/conteudo/vitrine_estudo.py      (depois de build_assets conteudo_e npcs_mundo_e)
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vitrines as V  # noqa: E402

# Defeitos de assets APROVADOS, já conhecidos e corrigidos nos dados da rodada 12 (não alterados aqui): relatados,
# nunca escondidos; só os assets deste estudo reprovam a vitrine.
PRE_EXISTENTES = {"ruin_house/interacao/escombros": "o ponto fica dentro da placa do telhado caído na main; a rodada 12 o "
                                                    "moveu para a frente do monte (colisao-composta.md §3)"}
FERREIRA_R12 = (1.94, 0.44)      # spawn da ferreira na oficina, rodada 12 (docs/architecture/colisao-composta.md §3)


def main():
    cat = {a["id"]: a for a in json.load(open(V.CAT))["assets"]}
    V.load_sets()
    man = json.load(open(os.path.join(V.ROOT, "assets", "environment", "e", "props.json")))
    prof = json.load(open(os.path.join(V.ROOT, "assets", "scenes", "colisao_e.json")))
    g = V.Ground()
    report = []
    walk = (man, prof)
    V.build_scene("vitrine_estudo_ruinas", {
        "_doc": "Laboratório de conteúdo, estudo de NPCs e ruínas — vitrine de RUÍNAS (gerada por "
                "tools/conteudo/vitrine_estudo.py; NÃO substitui nenhuma cena): a ruína de casa aprovada como régua, a "
                "abóbada soterrada e o sítio abandonado no relevo medido, com o herói.",
        "hero": [0.6, 2.4, 190],
        "scatter": [{"props": V.BACK_TREES, "count": 50, "ring": [19.5, 24], "seed": 41, "minDist": 2.8}],
        "creatures": [], "clouds": V.CLOUDS,
    }, V.village(cat, g, [("ruin_house", (-10.0, 0.0), 30), ("ruin_vault", (-3.0, -10.5), 0),
                          ("ruin_farmstead", (5.0, -3.0), 345)]) + [
        ("tree_oak", -9.5, -12.5, 0), ("rock_l", 5.2, -11.6, 0), ("bush", -4.6, -3.4, 0), ("grass_tall", -3.2, 1.6, 0),
        ("fern", 11.6, 1.0, 0), ("rock_s", -13.8, -5.0, 0),
    ], [], cat, g, report, walk=walk)
    V.build_scene("vitrine_estudo_npcs", {
        "_doc": "Laboratório de conteúdo, estudo de NPCs e ruínas — vitrine de NPCS DE OFÍCIO (gerada por "
                "tools/conteudo/vitrine_estudo.py; NÃO substitui nenhuma cena): Brásia na oficina, Quelmo atrás do balcão "
                "da barraca, Arvel na porta da cabana do caçador, ao lado de Índira e Bramor (aprovados).",
        "hero": [1.2, 3.2, 190],
        "scatter": [{"props": V.BACK_TREES, "count": 50, "ring": [19.5, 24], "seed": 43, "minDist": 2.8}],
        "creatures": [], "clouds": V.CLOUDS,
    }, V.village(cat, g, [("workshop_forge", (-7.5, -6.0), 0), ("market_stall", (0.5, -4.0), 0),
                          ("cabin_hunter", (8.5, -7.0), 340), ("hut_herbalist", (-11.0, 2.0), 40),
                          ("tower_watch", (12.5, 0.5), 300)]) + [
        ("barrel", -3.2, -3.6, 0), ("crate", 2.6, -3.0, 20), ("lantern_post", -1.6, -1.2, 0), ("bush", 5.0, -1.4, 0),
    ], [
        ("characters/npc/quelmo", "market_stall", "feirante", "idle"),
        ("characters/npc/arvel", "cabin_hunter", "cacador", "idle"),
        ("characters/npc/indira", "hut_herbalist", "herbalista", "idle"),
        ("characters/npc/bramor", "tower_watch", "vigia", "idle"),
    ], cat, g, report, walk=walk)
    path = os.path.join(V.ROOT, "assets", "scenes", "vitrine_estudo_npcs.json")
    doc = json.load(open(path))
    forge = next(p for p in doc["props"] if p["n"] == "workshop_forge")
    px, pz = V.rot(FERREIRA_R12[0], FERREIRA_R12[1], forge["r"])
    doc["npcs"].insert(0, {"rig": "characters/npc/brasia", "x": round(forge["x"] + px, 3), "z": round(forge["z"] + pz, 3),
                           "r": forge["r"], "clip": "idle"})
    report.append(f"vitrine_estudo_npcs: characters/npc/brasia no ponto da ferreira da rodada 12 → "
                  f"({doc['npcs'][0]['x']}, {doc['npcs'][0]['z']})")
    with open(path, "w") as f:
        json.dump(doc, f, indent=1, ensure_ascii=False)
        f.write("\n")
    out = os.path.join(V.ROOT, "output", "conteudo", "vitrines-estudo.txt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))
    novos = [r for r in report if "INALCANÇÁVEL" in r and not any(k in r for k in PRE_EXISTENTES)]
    for r in report:
        for k, why in PRE_EXISTENTES.items():
            if "INALCANÇÁVEL" in r and k in r:
                print(f"  (pré-existente: {k} — {why})")
    if novos:
        sys.exit("vitrine com alvo inalcançável de asset deste estudo")


if __name__ == "__main__":
    main()
