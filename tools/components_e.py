"""Rodada 07 — componentes de personalização dos protagonistas (saída: assets/components/, gerada).

O compositor Java (viewer/.../custom/) monta personagens a partir daqui SEM rodar Python a cada escolha. Este
módulo só PREPARA componentes; o corpo dos presets é lido dos rigs canônicos (characters/e/*) sem cópia.

Separação de cada cabeça canônica (aventureiro, exploradora, maga), feita depois de fundir e limpar exatamente
como o gerador oficial (head_with_expressions + prune_specks), para que rosto + cabelo + capuz recompostos
sejam idênticos voxel a voxel à cabeça canônica:
  rosto.<preset>   crânio + rosto, 4 expressões (mesmas posições)
  cabelo.<estilo>  massa do cabelo que vai junto da cabeça (+ partes próprias, ex.: rabo do 'rabo')
  capuz.c3         nó, fitas e pena do capuz C3 da exploradora + regra da FAIXA pintada sobre o cabelo
  chapeu.maga      o hat.vox canônico da maga (parte própria, pivô e movimento secundário próprios)

Canais semânticos (máscaras .vox ao lado de cada peça; cor da máscara = canal) — obtidos na ORIGEM por
geração de sonda: gera de novo com a cor-base trocada e marca as células que mudaram. Nada é inferido por
RGB do resultado. Canais: 1 pele, 2 cabelo, 3 sobrancelha (segue o cabelo), 4 íris.
"""
import json
import os

import chars_b as B
import chars_e as E
import charlib_e as L
from voxlib import C, Vox, write_vox

CONTRACT = "valumo.componentes/1"
CH = {"pele": C("#010000"), "cabelo": C("#020000"), "sobrancelha": C("#030000"), "iris": C("#040000")}
PROBE_HAIR, PROBE_IRIS, PROBE_SKIN = "#13a37b", "#a3137b", "#7b13a3"

PRESETS = {
    # id: (gerador, kwargs, pasta canônica, estilo de cabelo, cor do cabelo, íris, pele)
    "aventureiro": (E.adventurer, {"variant": "e"}, "characters/e/adventurer", "topete", "#c2582c", "#3d9c78", "#f6c9a0"),
    "exploradora": (E.ranger, {}, "characters/e/ranger", "rabo", "#e3a64a", "#3d8fd8", "#e8b185"),
    "maga": (E.mage, {}, "characters/e/mage", "chanel", "#e2d4f2", "#8a58c8", "#f9d8ba"),
}
HAIR_RAMP = {"#c2582c": (0.85, 0.55), "#e3a64a": (0.85, 0.5), "#e2d4f2": (0.8, 0.4)}

# paletas pequenas e escolhidas à mão (ver docs/architecture/personalizacao.md)
COLORS = {
    "cabelo": [("cobre", "#c2582c"), ("mel", "#e3a64a"), ("lilas", "#e2d4f2"), ("castanho", "#6b4630"),
               ("preto", "#2e2632"), ("loiro", "#e9c768"), ("ruivo", "#a83a2a"), ("cinza", "#a3a0ab"),
               ("musgo", "#5f8a5a"), ("anil", "#4a5a9a")],
    "pele": [("pessego", "#f6c9a0"), ("bronzeada", "#e8b185"), ("clara", "#f9d8ba"), ("morena", "#c98a5e"),
             ("escura", "#8f5c3e")],
    "iris": [("verde", "#3d9c78"), ("azul", "#3d8fd8"), ("violeta", "#8a58c8"), ("ambar", "#c08a2a"),
             ("castanho", "#6b4630")],
}


def _quiet(fn, *a, **k):
    """Os geradores imprimem progresso; aqui só interessa o resultado."""
    import contextlib
    import io
    with contextlib.redirect_stdout(io.StringIO()):
        return fn(*a, **k)


def _gen(pid, **over):
    fn, kw, *_ = PRESETS[pid]
    parts = {}
    rig = _quiet(fn, **dict(kw, **over), _parts=parts)
    return rig, parts


def _merged(parts):
    """Cabeça fundida e limpa por expressão — o MESMO procedimento do gerador (head_with_expressions + finish)."""
    heads = L.head_with_expressions(parts["sk"], parts["hair"], parts["iris"], parts["brow"], extra=parts["hood"],
                                    style=parts["face"])
    for v in heads.values():
        _quiet(L.prune_specks, v)
    return heads


def _rig_parts(rig):
    return {n: v for (n, v, _, _) in rig.parts}


def _diff(a, b):
    return {k for k, c in a.v.items() if k in b.v and b.v[k] != c}


def _with_skin_probe(fn):
    """Troca as peles (do herói e a global usada por paint_face/mãos) por uma cor-sonda durante a geração."""
    probe = B.P(PROBE_SKIN, ks=0.55)
    saved = {(m, n): getattr(m, n) for m in (L, E) for n in ("SKIN", "SKIN_TAN", "SKIN_PALE") if hasattr(m, n)}
    try:
        for (m, n) in saved:
            setattr(m, n, probe)
        return fn()
    finally:
        for (m, n), v in saved.items():
            setattr(m, n, v)


def _save(out, rel, vox):
    path = os.path.join(out, "components", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return {"file": rel, "origin": write_vox(path, vox)}


def _mask(out, rel, cells_by_channel):
    m = Vox()
    for ch, cells in cells_by_channel.items():
        for k in cells:
            m.v[k] = CH[ch]
    return _save(out, rel, m) if m.v else None


def build(out):
    catalog = {"contrato": CONTRACT, "versao": 1, "esqueleto": "heroi-e", "voxelSize": L.VS,
               "pivoCabeca": [0.0, float(L.NECK), 0.5], "canais": {k: f"#{v[0]:02x}{v[1]:02x}{v[2]:02x}" for k, v in CH.items()},
               "componentes": {}, "presets": {}, "cores": {k: [{"id": i, "hex": h} for i, h in v] for k, v in COLORS.items()}}
    comps = catalog["componentes"]
    for pid, (fn, kw, canon, style, hair_hex, iris_hex, skin_hex) in PRESETS.items():
        rig0, p0 = _gen(pid)
        merged = _merged(p0)
        canon_head = _rig_parts(rig0)["head"]
        assert merged["neutral"].v == canon_head.v, f"{pid}: cabeça refundida difere da canônica"
        hood_cells = set(p0["hood"].v) & set(merged["neutral"].v) if p0["hood"] is not None else set()
        hair_cells = (set(p0["hair"].v) & set(merged["neutral"].v)) - hood_cells

        # sondas (mesma geometria, cor-base trocada)
        _, ph = _gen(pid, hair_hex=PROBE_HAIR)
        mh = _merged(ph)
        _, pi = _gen(pid, iris_hex=PROBE_IRIS)
        mi = _merged(pi)
        rig_s, ps = _with_skin_probe(lambda: _gen(pid))
        ms = _with_skin_probe(lambda: _merged(ps))

        # rosto: 4 expressões, sem cabelo nem capuz
        face_exprs = {}
        for ex, v in merged.items():
            f = Vox()
            for k, c in v.v.items():
                if k not in hair_cells and k not in hood_cells:
                    f.v[k] = c
            face_exprs[ex] = f
        fid = f"rosto.{pid}"
        entry = {"versao": 1, "slot": "rosto", "origem": canon, "pele": skin_hex, "iris": iris_hex,
                 "sobrancelhaBase": hair_hex, "exprs": {}}
        for ex, f in face_exprs.items():
            chans = {"pele": _diff(f, ms[ex]) & set(f.v),
                     "sobrancelha": _diff(f, mh[ex]) & set(f.v),
                     "iris": _diff(f, mi[ex]) & set(f.v)}
            e = _save(out, f"{fid}/{ex}.vox", f)
            e["mascara"] = _mask(out, f"{fid}/{ex}.mask.vox", chans)
            entry["exprs"][ex] = e
        comps[fid] = entry

        # cabelo (massa que vai na cabeça + partes próprias)
        hid = f"cabelo.{style}"
        src_hair = p0.get("hair_sem_faixa") or p0["hair"]                    # sem a faixa do capuz
        hair = Vox()
        for k in hair_cells:
            hair.v[k] = src_hair.v[k]
        hair_probe = ph.get("hair_sem_faixa") or ph["hair"]
        hentry = {"versao": 1, "slot": "cabelo", "origem": canon, "base": hair_hex, "rampa": HAIR_RAMP[hair_hex],
                  "cabeca": _save(out, f"{hid}/hair.vox", hair), "partes": [],
                  "ocupa": ["cabeca.topo", "cabeca.nuca"]}
        hentry["cabeca"]["mascara"] = _mask(out, f"{hid}/hair.mask.vox", {"cabelo": _diff(hair, hair_probe)})
        if style == "rabo":                                                   # rabo-de-cavalo: parte própria
            tail_doc = next(p for p in json.load(open(os.path.join(out, canon, "rig.json")))["parts"] if p["name"] == "tail")
            tail = p0["tail"]
            hentry["partes"].append({"nome": "tail", "pai": "head", "pivo": tail_doc["pivot"],
                                     "file": f"../{canon}/tail.vox", "origin": tail_doc["origin"],
                                     "mascara": _mask(out, f"{hid}/tail.mask.vox", {"cabelo": _diff(tail, ph["tail"])})})
            hentry["meta"] = {"tailGain": json.load(open(os.path.join(out, canon, "rig.json")))["meta"].get("tailGain", 1.0)}
        comps[hid] = hentry

        if p0["hood"] is not None:                                           # capuz C3: geometria + faixa
            hood = Vox()
            for k in hood_cells:
                hood.v[k] = merged["neutral"].v[k]
            lin = p0["lining"]
            comps["capuz.c3"] = {"versao": 1, "slot": "cabeca", "origem": canon, "cabeca": _save(out, "capuz.c3/hood.vox", hood),
                                 "faixa": {"cor": f"#{lin[0]:02x}{lin[1]:02x}{lin[2]:02x}", "linhas": [L.NECK + 13, L.NECK + 14],
                                           "zMin": -7, "zMax": 5},
                                 "cabelo": {"rabo": "faixa", "*": "faixa"}}
        if pid == "maga":                                                    # chapéu: hat.vox canônico
            hat_doc = next(p for p in json.load(open(os.path.join(out, canon, "rig.json")))["parts"] if p["name"] == "hat")
            comps["chapeu.maga"] = {"versao": 1, "slot": "cabeca", "origem": canon,
                                    "partes": [{"nome": "hat", "pai": "head", "pivo": hat_doc["pivot"],
                                                "file": f"../{canon}/hat.vox", "origin": hat_doc["origin"]}],
                                    "cabelo": {"chanel": "nativo", "*": "sob_aba"}}

        # corpo: partes canônicas + máscara de pele (sonda da pele aplicada ao herói inteiro)
        rp, rs = _rig_parts(rig0), _rig_parts(rig_s)
        doc = json.load(open(os.path.join(out, canon, "rig.json")))
        masks = {}
        for pdoc in doc["parts"]:
            n = pdoc["name"]
            if n in ("head", "hat", "tail"):
                continue
            cells = _diff(rp[n], rs[n])
            if cells:
                masks[n] = _mask(out, f"corpo.{pid}/{n}.mask.vox", {"pele": cells})
        catalog["presets"][pid] = {"corpo": canon, "rosto": fid, "cabelo": hid, "corCabelo": hair_hex, "pele": skin_hex,
                                   "iris": iris_hex, "cabeca": "chapeu.maga" if pid == "maga" else ("capuz.c3" if pid == "exploradora" else None),
                                   "mascarasDoCorpo": masks}

    # estilo extra: 'curto' (rodada 07) — curto e bagunçado. O 'natural' (h1) ficou fora do catálogo: medido,
    # é 82% do mesmo volume do topete (93% de frente) — seria variação, não estilo.
    rig_n, pn = _gen("aventureiro", hair_style="curto")
    mn = _merged(pn)
    cells = set(pn["hair"].v) & set(mn["neutral"].v)
    hair = Vox()
    for k in cells:
        hair.v[k] = pn["hair"].v[k]
    _, pnh = _gen("aventureiro", hair_style="curto", hair_hex=PROBE_HAIR)
    e = {"versao": 1, "slot": "cabelo", "origem": "tools/chars_e.py adventurer_hair curto", "base": "#c2582c",
         "rampa": HAIR_RAMP["#c2582c"], "cabeca": _save(out, "cabelo.curto/hair.vox", hair), "partes": [],
         "ocupa": ["cabeca.topo", "cabeca.nuca"]}
    e["cabeca"]["mascara"] = _mask(out, "cabelo.curto/hair.mask.vox", {"cabelo": _diff(hair, pnh["hair"])})
    comps["cabelo.curto"] = e

    os.makedirs(os.path.join(out, "components"), exist_ok=True)
    with open(os.path.join(out, "components", "catalog.json"), "w") as f:
        json.dump(catalog, f, indent=1, sort_keys=True)
    print(f"  components_e: {len(comps)} componentes, {len(catalog['presets'])} presets")
