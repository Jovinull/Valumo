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

MODULES = ("conteudo_construcoes", "conteudo_vegetacao", "conteudo_poi", "conteudo_assentamento", "conteudo_props",
           "conteudo_ruinas")
CONTRACT = "valumo.conteudo"
VERSION = 1
HERO = "characters/e/adventurer"
REFS = ("house", "trail_post", "well", "shrine", "tree_oak", "tree_oak3", "tree_oak4", "tree_oak2", "tree_pine",
        "tree_birch", "tree_willow", "tree_big", "rock_l", "mushroom_big", "barrel", "crate", "lantern_post", "signpost",
        "fence", "campfire", "log", "stump")   # aprovados, só para comparar (LINHA)


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
    if pts.get("vagas"):                                  # vaga de NPC morador: posição local, rumo e papel (+ notas)
        out["vagas"] = []
        for item in pts["vagas"]:
            i, p, r, papel = item[:4]
            extra = item[4] if len(item) > 4 else {}
            out["vagas"].append({"id": i, "pos_u": L.u(p, vs), "rumo": r, "papel": papel, **extra})
    if pts.get("formas"):
        out["formas_u"] = [{"tipo": t, "centro": [round(c[0] * vs, 3), round(c[1] * vs, 3)], "raio": round(r * vs, 3)}
                           for (t, c, r) in pts["formas"]]
    return out


def _same_points(a, b):
    """Entradas, interação, spawns, observação e área segura iguais (as formas de colisão podem mudar com a semente:
    pedras de um círculo, por exemplo — a variação guarda as dela)."""
    keys = [k for k in a if k != "formas_u"]
    return all(a[k] == b.get(k) for k in keys)


def _conjuntos(catalog, manifest):
    """Valida e exporta os conjuntos de ofício (tools/conteudo_conjuntos.py): item fora da pegada da âncora e dos
    outros itens, entradas da âncora livres (folga de 0,3 u), ids existentes no props.json. Recusa o build se falhar."""
    try:
        import conteudo_conjuntos as CJ
    except ModuleNotFoundError:
        return []
    by_id = {c["id"]: c for c in catalog}
    for c in catalog:
        for v in c["variacoes"]["extremos"]:
            if v.get("prop"):
                by_id[v["id"]] = {**c, "id": v["id"], "medidas": {**c["medidas"], "pegada_u": v["pegada_u"]}}
    out = []
    for cj in CJ.CONJUNTOS:
        anc = by_id.get(cj["ancora"])
        if anc is None or cj["ancora"] not in manifest:
            raise SystemExit(f"conjunto {cj['id']}: âncora {cj['ancora']} inexistente")
        a_rect = L.rect_world(anc["medidas"]["pegada_u"], 0, 0, 0)
        doors = [e["pos_u"] for e in anc["pontos"]["entradas"]]
        rects, items = [], []
        for (iid, x, z, r) in cj["itens"]:
            it = by_id.get(iid)
            if it is None or iid not in manifest:
                raise SystemExit(f"conjunto {cj['id']}: item {iid} inexistente no props.json")
            rr = L.rect_world(it["medidas"]["pegada_u"], x, z, r)
            if L.rects_overlap(rr, a_rect):
                raise SystemExit(f"conjunto {cj['id']}: {iid} invade a pegada da âncora")
            for (oid, orr) in rects:
                if L.rects_overlap(rr, orr):
                    raise SystemExit(f"conjunto {cj['id']}: {iid} sobrepõe {oid}")
            grown = L.rect_world(it["medidas"]["pegada_u"], x, z, r, grow=(0.3, 0.3, 0.3, 0.3))
            for d in doors:
                pt = [(d[0] - 0.01, d[2] - 0.01), (d[0] + 0.01, d[2] - 0.01), (d[0] + 0.01, d[2] + 0.01), (d[0] - 0.01, d[2] + 0.01)]
                if L.rects_overlap(grown, pt):
                    raise SystemExit(f"conjunto {cj['id']}: {iid} bloqueia a entrada da âncora em {d}")
            rects.append((iid, rr))
            items.append({"id": iid, "pos_u": [x, z], "rumo": r})
        xs = [p[0] for _, rr in rects for p in rr] + [p[0] for p in a_rect]
        zs = [p[1] for _, rr in rects for p in rr] + [p[1] for p in a_rect]
        members = [by_id[cj["ancora"]]] + [by_id[i] for (i, *_r) in cj["itens"]]
        blocking = sorted({m["id"] for m in members if m["aprovacao"]["integracao"] == "depende_colisao_composta"})
        out.append({"id": cj["id"], "nome": cj["nome"], "oficio": cj["oficio"], "ancora": cj["ancora"],
                    "leitura": cj["leitura"], "referencial": "u no espaço da âncora (+z = frente); gira com a âncora",
                    "itens": items, "caixa_u": {"min": [round(min(xs), 3), round(min(zs), 3)],
                                                 "max": [round(max(xs), 3), round(max(zs), 3)]},
                    "estado": "aprovado",
                    "aprovacao": {"artistica": "aprovado_como_proposta",
                                  "decisao": "proprietário, avaliação do lote 2 (2026-11-19): proposta de composição",
                                  "integracao": "depende_colisao_composta" if blocking else "liberado",
                                  "dependencias": [f"formas compostas no Collision ({', '.join(blocking)})"] if blocking else []}})
    return out


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
        state = {"rejeitado": "rejeitado", "candidato": "candidato"}.get(meta["aprovacao"], "aprovado")
        # affineWind MEDIDO: só quem ainda tem material folhagem (o vento afim do estilo E desloca só a folhagem; a flag
        # do prop autoriza o mesher a fundir essas faces). Estrutura rígida sem folhagem não declara vento.
        wind = any(c[3] == "foliage" for c in vox.v.values())
        if state != "rejeitado":
            origin = write_vox(os.path.join(env, name + ".vox"), vox)
            entry = {"file": name + ".vox", "voxelSize": vs, "origin": origin, "sizeVoxels": size, "conteudo": True}
            if wind:
                entry["affineWind"] = True
            manifest[name] = entry
            _rig(os.path.join(studies, name), meta["nome"], vs, f"../../../environment/e/{name}.vox", origin, size)
        else:
            origin = write_vox(os.path.join(studies, name, "body.vox"), vox)
            _rig(os.path.join(studies, name), meta["nome"], vs, "body.vox", origin, size)
        m = L.measure(vox, vs, trunk_band=meta.get("tronco_faixa"))
        measured = meta.get("colisao", {}).get("formas") and not pts.get("formas")
        if measured:
            m["formas_medidas_u"] = [[round(c * vs, 3) for c in b] for b in L.collision_boxes(vox)]
        cover = L.cover_boxes(vox, vs)
        if cover:                                          # teto sobre chão caminhável: formas só de câmera
            m["cobertura_u"] = [[round(c * vs, 3) for c in b] for b in cover]
        variants = []
        for tag, params in _variants(meta):
            vv, vpts = fn(**params)
            vb = vv.bounds()
            vname = f"{name}__{tag}"
            vsize = [vb[3] - vb[0] + 1, vb[4] - vb[1] + 1, vb[5] - vb[2] + 1]
            published = bool(meta.get("publicar_variantes")) and state != "rejeitado"
            if published:                                       # variante publicada: prop próprio no props.json
                vo = write_vox(os.path.join(env, vname + ".vox"), vv)
                ventry = {"file": vname + ".vox", "voxelSize": vs, "origin": vo, "sizeVoxels": vsize, "conteudo": True,
                          "variante_de": name}
                if any(c[3] == "foliage" for c in vv.v.values()):
                    ventry["affineWind"] = True
                manifest[vname] = ventry
                _rig(os.path.join(studies, vname), f"{meta['nome']} ({tag})", vs, f"../../../environment/e/{vname}.vox",
                     vo, vsize)
            else:
                vo = write_vox(os.path.join(studies, vname, "body.vox"), vv)
                _rig(os.path.join(studies, vname), f"{meta['nome']} ({tag})", vs, "body.vox", vo, vsize)
            vm = L.measure(vv, vs, trunk_band=meta.get("tronco_faixa"))
            vrec = {"id": vname, "prop": vname if published else None,
                    "parametros": {k: v for k, v in params.items()},
                    "dimensoes_u": vm["dimensoes_u"], "pegada_u": vm["pegada_u"],
                    "faces_expostas": vm["faces_expostas"],
                    "pontos_iguais": _same_points(_points(vpts, vs), _points(pts, vs)),
                    **({"formas_u": _points(vpts, vs)["formas_u"]} if vpts.get("formas") else {})}
            if measured:                                    # colisão DA VARIAÇÃO (pode diferir da base)
                vrec["formas_medidas_u"] = [[round(c * vs, 3) for c in b] for b in L.collision_boxes(vv)]
                vrec["formas_iguais"] = vrec["formas_medidas_u"] == m["formas_medidas_u"]
            variants.append(vrec)
        # reserva do gerador: a UNIÃO exata das pegadas e o máximo das dimensões da base e de todas as variações
        allp = [m["pegada_u"]] + [x["pegada_u"] for x in variants]
        m["pegada_reservada_u"] = {"min": [min(p["min"][i] for p in allp) for i in range(2)],
                                   "max": [max(p["max"][i] for p in allp) for i in range(2)]}
        m["dimensoes_reservadas_u"] = [max(d[i] for d in [m["dimensoes_u"]] + [x["dimensoes_u"] for x in variants])
                                       for i in range(3)]
        rec = {
            "id": name, "nome": meta["nome"], "categoria": meta["categoria"], "subcategoria": meta.get("sub"),
            "lote": meta.get("lote"), "estado": state, "versao": meta["versao"],
            "aprovacao": {"artistica": meta["aprovacao"],
                          "decisao": meta.get("decisao") or (f"proprietário, avaliação do lote {meta.get('lote')} (2026-11-19)"
                                                             if meta.get("lote") in (1, 2)
                                                             else "aguardando avaliação do proprietário"),
                          "integracao": meta["integracao"],
                          "distribuicao_procedural": meta["integracao"] == "liberado",
                          "dependencias": meta["dependencias"]},
            "fonte": f"tools/{fn.__module__}.py:{fn.__name__}",
            "prop": name if state != "rejeitado" else None,
            "origem": "x = z = 0 no centro da pegada; y = 0 = chão (soleira); unidades em u; voxel em u",
            "orientacao": "+z = frente (entrada principal); o gerador gira o prop por 'r' graus (rotateY do viewer)",
            "funcao": meta.get("funcao"), "tags": meta.get("tags", []), "frequencia": meta.get("frequencia"),
            "terreno": meta.get("terreno", []), "apoio": meta.get("apoio"),
            "inclinacao_max_u": meta.get("inclinacao_max_u"), "area_livre": meta.get("area_livre"),
            "colisao": meta.get("colisao"), "restricoes": meta.get("restricoes", []),
            "affineWind": wind,
            "medidas": m,
            "custo": {"faces_expostas": m["faces_expostas"], "voxels": m["voxels"], "classe": _cost_class(m["faces_expostas"]),
                      "emissivo_pct": m["emissivo_pct"]},
            "pontos": _points(pts, vs),
            "variacoes": {"parametros": meta["variacoes"], "extremos": variants},
            "relacoes": [{"tipo": t, "alvo": a, "distancia_u": d} for (t, a, d) in meta["relacoes"]],
            "notas": meta.get("notas"), "motivo_rejeicao": meta.get("motivo_rejeicao"),
        }
        catalog.append(rec)
        for s in ([f"conteudo-lote{meta.get('lote')}", f"conteudo-{meta['categoria']}"]
                  + ([f"conteudo-{meta['categoria']}-liberados"] if meta["integracao"] == "liberado" else [])):
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
               "construcao": ["house", "trail_post"], "poi": ["shrine", "well", "rock_l"],
               "estrutura": ["well", "trail_post", "shrine"],
               "prop": ["barrel", "crate", "lantern_post", "signpost", "fence", "campfire", "log", "stump"]}
    for cat, refs in compare.items():
        if cat in by_cat:
            lineups[f"conteudo-{cat}-comparacao"] = [f"ref_{r}" for r in refs if r in manifest] + by_cat[cat]
    for s, names in sorted(lineups.items()):
        cats = [L.ASSETS.get(n.split("__")[0], (None, {}))[1].get("categoria") for n in names if not n.startswith("ref_")]
        wide = any(c in ("construcao", "poi", "estrutura") for c in cats)
        doc = {"dirs": [HERO] + [f"studies/conteudo/{n}" for n in names],
               "larguraMax": 9.0 if wide else 5.0, "vao": 0.6}
        with open(os.path.join(ldir, s + ".json"), "w") as f:
            json.dump(doc, f, indent=1)
    sets = _conjuntos(catalog, manifest)
    with open(os.path.join(env, "conteudo_catalogo.json"), "w") as f:
        json.dump({"contrato": CONTRACT, "versao": VERSION, "conjuntos": sets,
                   "_doc": "Catálogo do laboratório de conteúdo (provisório, isolado do módulo game). Medidas tiradas dos "
                           "voxels; pontos autorados conferidos por ConteudoMundoTest. Ver docs/architecture/catalogo-de-conteudo.md.",
                   "assets": catalog}, f, indent=1, ensure_ascii=False)
    print(f"  conteudo_e: {len(catalog)} assets ({sum(1 for c in catalog if c['estado'] != 'rejeitado')} no props.json), "
          f"{sum(len(c['variacoes']['extremos']) for c in catalog)} variações, {len(lineups)} conjuntos")
