"""Laboratório de conteúdo do mundo (direção E) — gera a biblioteca de assets novos e o catálogo para o mundo procedural.

    python3 tools/build_assets.py env_e conteudo_e      (roda depois de env_e: acrescenta ao props.json da E)

Saídas (todas GERADAS, fora do git):
  assets/environment/e/<id>.vox + entradas no props.json   candidatos e recomendados (marcados "conteudo": true)
  assets/environment/e/conteudo_catalogo.json             catálogo valumo.conteudo/1 (metadados medidos)
  assets/studies/conteudo/<id>/rig.json                    rig estático de cada asset (modo LINHA), inclusive
                                                           rejeitados e variações extremas (<id>__<param>-<valor>)
  assets/studies/lineups/<conjunto>.json                   conjuntos do modo LINHA (set=<conjunto>)

Nada aqui altera um asset aprovado: só ACRESCENTA entradas novas ao props.json (ids que não existiam). Se env_e rodar
sozinho depois, as entradas somem até este módulo rodar de novo (o Gradle roda todos os módulos em ordem).
"""
import json
import os

import conteudo_lib as L
from voxlib import write_vox

MODULES = ("conteudo_construcoes", "conteudo_vegetacao", "conteudo_poi", "conteudo_props")
CONTRACT = "valumo.conteudo"
VERSION = 1
HERO = "characters/e/adventurer"
REFS = ("house", "trail_post", "well", "shrine", "tree_oak", "tree_oak3", "tree_oak4", "tree_oak2", "tree_pine",
        "tree_birch", "tree_willow", "tree_big", "rock_l", "mushroom_big")   # aprovados, só para comparar (LINHA)


def _load_modules():
    import importlib
    for m in MODULES:
        if os.path.exists(os.path.join(os.path.dirname(os.path.abspath(__file__)), m + ".py")):
            importlib.import_module(m)


def _variants(meta):
    """Variações EXTREMAS a conferir: cada parâmetro em cada valor fora do padrão (um de cada vez) e todos no último
    valor ao mesmo tempo. O padrão é o primeiro valor da lista."""
    var = meta["variacoes"]
    out = []
    for k, vals in var.items():
        for val in vals[1:]:
            out.append((f"{k}-{_tag(val)}", {k: val}))
    if len(var) > 1:
        out.append(("extremo", {k: vals[-1] for k, vals in var.items()}))
    return out


def _tag(val):
    return {True: "sim", False: "nao"}.get(val, str(val)) if isinstance(val, bool) else str(val)


def _rig(path_dir, name, vs, vox_rel, origin, size):
    os.makedirs(path_dir, exist_ok=True)
    doc = {"name": name, "type": "vox", "voxelSize": vs, "anim": "static", "sizeVoxels": size,
           "meta": {"conteudo": True},
           "parts": [{"name": "body", "file": vox_rel, "parent": None, "origin": origin, "pivot": [0.0, 0.0, 0.0]}],
           "affineWind": True}
    with open(os.path.join(path_dir, "rig.json"), "w") as f:
        json.dump(doc, f, indent=1)


def _points(pts, vs):
    out = {}
    for key in ("entradas", "observacao", "conexoes"):
        out[key] = [{"id": i, "pos_u": L.u(p, vs), "rumo": r} for (i, p, r) in pts.get(key, [])]
    for key in ("interacao", "spawns"):
        out[key] = [{"id": i, "pos_u": L.u(p, vs), "tipo": t} for (i, p, t) in pts.get(key, [])]
    out["area_segura_u"] = [[round(a * vs, 3) for a in r] for r in pts.get("area_segura", [])]
    if pts.get("formas"):
        out["formas_u"] = [{"tipo": t, "centro": [round(c[0] * vs, 3), round(c[1] * vs, 3)], "raio": round(r * vs, 3)}
                           for (t, c, r) in pts["formas"]]
    return out


def _same_points(a, b):
    """Entradas, interação, spawns, observação e área segura iguais (as formas de colisão podem mudar com a semente:
    pedras de um círculo, por exemplo — a variação guarda as dela)."""
    keys = [k for k in a if k != "formas_u"]
    return all(a[k] == b.get(k) for k in keys)


def _cost_class(faces):
    return "leve" if faces < 3000 else "medio" if faces < 9000 else "pesado" if faces < 20000 else "muito_pesado"


def build(out):
    _load_modules()
    env = os.path.join(out, "environment", "e")
    mpath = os.path.join(env, "props.json")
    if not os.path.exists(mpath):
        raise SystemExit("conteudo_e: rode env_e antes (assets/environment/e/props.json ausente)")
    with open(mpath) as f:
        manifest = json.load(f)
    for k in [k for k, e in manifest.items() if e.get("conteudo")]:   # idempotente: tira as do laboratório
        del manifest[k]
    approved = set(manifest)
    studies = os.path.join(out, "studies", "conteudo")
    lineups = {}
    catalog = []
    for name in sorted(L.ASSETS):
        fn, meta = L.ASSETS[name]
        if name in approved:
            raise SystemExit(f"conteudo_e: '{name}' já existe no props.json aprovado — escolha outro id")
        vox, pts = fn()
        vs = meta["voxel"]
        b = vox.bounds()
        size = [b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1]
        state = meta.get("estado", "candidato")
        if state != "rejeitado":
            origin = write_vox(os.path.join(env, name + ".vox"), vox)
            entry = {"file": name + ".vox", "voxelSize": vs, "origin": origin, "sizeVoxels": size, "conteudo": True}
            if meta.get("wind"):
                entry["affineWind"] = True
            manifest[name] = entry
            _rig(os.path.join(studies, name), meta["nome"], vs, f"../../../environment/e/{name}.vox", origin, size)
        else:
            origin = write_vox(os.path.join(studies, name, "body.vox"), vox)
            _rig(os.path.join(studies, name), meta["nome"], vs, "body.vox", origin, size)
        m = L.measure(vox, vs, trunk_band=meta.get("tronco_faixa"))
        if meta.get("colisao", {}).get("formas") and not pts.get("formas"):
            m["formas_medidas_u"] = [[round(c * vs, 3) for c in b] for b in L.collision_boxes(vox)]
        variants = []
        for tag, params in _variants(meta):
            vv, vpts = fn(**params)
            vb = vv.bounds()
            vname = f"{name}__{tag}"
            vo = write_vox(os.path.join(studies, vname, "body.vox"), vv)
            _rig(os.path.join(studies, vname), f"{meta['nome']} ({tag})", vs, "body.vox", vo,
                 [vb[3] - vb[0] + 1, vb[4] - vb[1] + 1, vb[5] - vb[2] + 1])
            vm = L.measure(vv, vs, trunk_band=meta.get("tronco_faixa"))
            variants.append({"id": vname, "parametros": {k: v for k, v in params.items()},
                             "dimensoes_u": vm["dimensoes_u"], "pegada_u": vm["pegada_u"],
                             "faces_expostas": vm["faces_expostas"],
                             "pontos_iguais": _same_points(_points(vpts, vs), _points(pts, vs)),
                             **({"formas_u": _points(vpts, vs)["formas_u"]} if vpts.get("formas") else {})})
        rec = {
            "id": name, "nome": meta["nome"], "categoria": meta["categoria"], "subcategoria": meta.get("sub"),
            "lote": meta.get("lote"), "estado": state, "versao": meta["versao"],
            "fonte": f"tools/{fn.__module__}.py:{fn.__name__}",
            "prop": name if state != "rejeitado" else None,
            "origem": "x = z = 0 no centro da pegada; y = 0 = chão (soleira); unidades em u; voxel em u",
            "orientacao": "+z = frente (entrada principal); o gerador gira o prop por 'r' graus (rotateY do viewer)",
            "funcao": meta.get("funcao"), "tags": meta.get("tags", []), "frequencia": meta.get("frequencia"),
            "terreno": meta.get("terreno", []), "apoio": meta.get("apoio"),
            "inclinacao_max_u": meta.get("inclinacao_max_u"), "area_livre": meta.get("area_livre"),
            "colisao": meta.get("colisao"), "restricoes": meta.get("restricoes", []),
            "affineWind": bool(meta.get("wind")),
            "medidas": m,
            "custo": {"faces_expostas": m["faces_expostas"], "voxels": m["voxels"], "classe": _cost_class(m["faces_expostas"]),
                      "emissivo_pct": m["emissivo_pct"]},
            "pontos": _points(pts, vs),
            "variacoes": {"parametros": meta["variacoes"], "extremos": variants},
            "notas": meta.get("notas"), "motivo_rejeicao": meta.get("motivo_rejeicao"),
        }
        catalog.append(rec)
        for s in ([f"conteudo-lote{meta.get('lote')}", f"conteudo-{meta['categoria']}"]
                  + ([f"conteudo-{meta['categoria']}-recomendados"] if state == "recomendado" else [])):
            lineups.setdefault(s, []).append(name)
        lineups[f"conteudo-{name.replace('_', '-')}"] = [name]
        if variants:
            lineups[f"conteudo-var-{name.replace('_', '-')}"] = [name] + [x["id"] for x in variants]
    with open(mpath, "w") as f:
        json.dump(manifest, f, indent=1, sort_keys=True)
    for r in REFS:                                                     # rigs de comparação (aprovados, só leitura)
        e = manifest.get(r)
        if e:
            _rig(os.path.join(studies, "ref_" + r), f"(aprovado) {r}", e["voxelSize"], f"../../../environment/e/{e['file']}",
                 e["origin"], e["sizeVoxels"])
    ldir = os.path.join(out, "studies", "lineups")
    os.makedirs(ldir, exist_ok=True)
    # comparação com os aprovados da mesma categoria (mesma luz, mesmo chão, herói como escala)
    by_cat = {}
    for c in catalog:
        by_cat.setdefault(c["categoria"], []).append(c["id"])
    compare = {"vegetacao": ["tree_oak", "tree_oak3", "tree_oak4", "tree_oak2", "tree_pine", "tree_birch", "tree_willow"],
               "construcao": ["house", "trail_post"], "poi": ["shrine", "well", "rock_l"]}
    for cat, refs in compare.items():
        if cat in by_cat:
            lineups[f"conteudo-{cat}-comparacao"] = [f"ref_{r}" for r in refs if r in manifest] + by_cat[cat]
    for s, names in sorted(lineups.items()):
        cats = [L.ASSETS.get(n.split("__")[0], (None, {}))[1].get("categoria") for n in names if not n.startswith("ref_")]
        wide = any(c in ("construcao", "poi") for c in cats)
        doc = {"dirs": [HERO] + [f"studies/conteudo/{n}" for n in names],
               "larguraMax": 9.0 if wide else 5.0, "vao": 0.6}
        with open(os.path.join(ldir, s + ".json"), "w") as f:
            json.dump(doc, f, indent=1)
    with open(os.path.join(env, "conteudo_catalogo.json"), "w") as f:
        json.dump({"contrato": CONTRACT, "versao": VERSION,
                   "_doc": "Catálogo do laboratório de conteúdo (provisório, isolado do módulo game). Medidas tiradas dos "
                           "voxels; pontos autorados conferidos por ConteudoMundoTest. Ver docs/architecture/catalogo-de-conteudo.md.",
                   "assets": catalog}, f, indent=1, ensure_ascii=False)
    print(f"  conteudo_e: {len(catalog)} assets ({sum(1 for c in catalog if c['estado'] != 'rejeitado')} no props.json), "
          f"{sum(len(c['variacoes']['extremos']) for c in catalog)} variações, {len(lineups)} conjuntos")
