"""Rodada 06.1 — PROVA TÉCNICA de personalização modular (composição por componentes, em tempo de geração).

Não é o criador de personagens: verifica se dá para trocar cor de cabelo, estilo de cabelo e chapéu sobre um
personagem-base SEM reconstruí-lo, sem mudar proporções e animações e sem tocar os presets canônicos.

    aparência (JSON versionado, docs/architecture/personalizacao.md) → rig em assets/characters/custom/<id>/

Componentes desta prova:
  * corpo   — partes do preset canônico (characters/e/adventurer) copiadas sem mudança: torso, mochila,
              cachecol, braços, pernas, arma;
  * cabeça  — crânio + rosto (4 expressões) gerados pelas MESMAS funções do aventureiro; o cabelo vira parte
              própria `hair` (filha de `head`, mesmo pivô), então a expressão troca só o rosto;
  * cabelo  — estilos existentes do gerador (`topete` = h2, oficial; `natural` = h1, estudo da rodada 03) e
              cor livre (rampa de sombra gerada pela paleta, sobrancelha derivada da cor do cabelo);
  * chapéu  — `hat.vox` da maga (mesmo crânio nos três heróis, mesmo pivô). Regra de ocultação: com chapéu,
              o cabelo usa a variante `sob_chapeu` = o cabelo menos tudo que fica dentro das colunas cobertas
              pelo chapéu acima da aba (o chapéu esconde; nada é esticado ou deformado).

Dependência de ordem: lê assets já gerados por chars_e (build_assets roda chars_e antes deste módulo).
"""
import json
import os

import chars_b as B
import chars_e as E
import charlib_e as L
from voxlib import C, Rig, Vox, exposed_faces, load_vox

CONTRACT = "valumo.aparencia/1"
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets")

# id estável do componente → parâmetro do gerador (o save guarda o id, nunca a geometria)
HAIR_STYLES = {"topete": "h2", "natural": "h1"}
HATS = {"chapeu_maga": ("characters/e/mage", "hat")}
PRESETS = {
    "aventureiro": {
        "base": "characters/e/adventurer",
        "aparencia": {"pele": "padrao", "cabelo": {"estilo": "topete", "cor": "#c2582c"},
                      "olhos": {"forma": "tilt", "cor": "#3d9c78"}, "atuacao": "bold"},
        "equipamento_visual": {"cabeca": None},
        "hair_ramp": {"ks": 0.85, "kl": 0.55},
    },
}

# combinações da prova (ids de pasta → documento de aparência)
PROOF = {
    "c0_preset": {"origem": {"preset": "aventureiro"}},
    "c1_cabelo_castanho": {"origem": {"preset": "aventureiro"}, "aparencia": {"cabelo": {"cor": "#5b3a2c"}}},
    "c2_cabelo_natural": {"origem": {"preset": "aventureiro"}, "aparencia": {"cabelo": {"estilo": "natural"}}},
    "c3_chapeu": {"origem": {"preset": "aventureiro"}, "equipamento_visual": {"cabeca": "chapeu_maga"}},
    "c4_natural_castanho_chapeu": {"origem": {"preset": "aventureiro"},
                                   "aparencia": {"cabelo": {"estilo": "natural", "cor": "#5b3a2c"}},
                                   "equipamento_visual": {"cabeca": "chapeu_maga"}},
}


def resolve(doc):
    """Documento salvo (só o que difere) + preset → aparência completa. O preset é configuração, não classe."""
    pre = PRESETS[doc["origem"]["preset"]]
    ap = json.loads(json.dumps(pre["aparencia"]))
    for k, v in (doc.get("aparencia") or {}).items():
        if isinstance(v, dict):
            ap.setdefault(k, {}).update(v)
        else:
            ap[k] = v
    eq = dict(pre["equipamento_visual"])
    eq.update(doc.get("equipamento_visual") or {})
    return pre, ap, eq


def load_part(rig_dir, name):
    """(Vox no espaço do modelo, pivô, pai) de uma parte gerada."""
    doc = json.load(open(os.path.join(ASSETS, rig_dir, "rig.json")))
    for p in doc["parts"]:
        if p["name"] == name:
            ox, oy, oz = p["origin"]
            local = load_vox(os.path.join(ASSETS, rig_dir, p["file"]))
            v = Vox()
            for (x, y, z), c in local.v.items():
                v.set(x + ox, y + oy, z + oz, c)
            return v, p["pivot"], p["parent"]
    raise KeyError(f"{rig_dir}: parte {name}")


def compose(cid, doc):
    pre, ap, eq = resolve(doc)
    base = pre["base"]
    bdoc = json.load(open(os.path.join(ASSETS, base, "rig.json")))
    hair_col = B.P(ap["cabelo"]["cor"], **pre["hair_ramp"])
    style = HAIR_STYLES[ap["cabelo"]["estilo"]]
    iris = C(ap["olhos"]["cor"])
    brow = B.shadow(hair_col, 1.6)
    face_style = dict(E.ADV_FACE)

    sk = L.skull(L.SKIN)
    hair, head_top, _ = E.adventurer_hair(sk, hair_col, style)
    merged = L.head_with_expressions(sk, hair, iris, brow, style=face_style)     # igual ao preset canônico
    for k, v in merged.items():
        L.prune_specks(v, label=f"{cid}/head:{k}")
    neutral = merged["neutral"]
    hair_part = Vox()
    for k in hair.v:
        if k in neutral.v:
            hair_part.v[k] = neutral.v[k]
    heads = {}
    for k, v in merged.items():                                                  # rosto = cabeça − cabelo
        h = Vox()
        for c, col in v.v.items():
            if c not in hair_part.v:
                h.v[c] = col
        heads[k] = h

    hat = None
    if eq.get("cabeca"):
        src, pname = HATS[eq["cabeca"]]
        hat, hat_piv, _ = load_part(src, pname)
        y0 = min(k[1] for k in hat.v)
        cols = {(x, z) for (x, _, z) in hat.v}
        for k in [k for k in hair_part.v if (k[0], k[2]) in cols and k[1] >= y0]:   # variante sob_chapeu
            del hair_part.v[k]

    rig = Rig(f"Personalizado {cid} (prova 06.1)", bdoc["voxelSize"], bdoc["anim"])
    piv_head = None
    for p in bdoc["parts"]:
        n = p["name"]
        if n == "head":
            piv_head = p["pivot"]
            rig.part("head", heads["neutral"], p["pivot"], p["parent"],
                     alts={k: heads[k] for k in ("joy", "determined", "surprise")})
            rig.part("hair", hair_part, p["pivot"], "head")
            if hat is not None:
                rig.part("hat", hat, hat_piv, "head")
            continue
        v, piv, par = load_part(base, n)
        rig.part(n, v, piv, par)
    meta = dict(bdoc["meta"])
    head_h = head_top - E.NECK + 1 if hat is None else meta["headH"]
    if hat is None:
        meta.update({"headsTall": round((head_top + 1) / head_h, 2), "headH": head_h, "bodyHeightVoxels": head_top + 1})
    meta["faces"] = sum(exposed_faces(v) for _, v, _, _ in rig.parts)
    meta["appearance"] = {"contrato": CONTRACT, "versao": 1, **doc}
    meta["note"] = ("prova técnica 06.1: composição por componentes; cabelo em parte própria; com chapéu, "
                    "cabelo na variante sob_chapeu (medida de cabeças do preset)")
    rig.meta = meta
    print(f"  {cid}: {meta['headsTall']} cabeças, {meta['faces']} faces, chapéu={eq.get('cabeca')}, "
          f"cabelo={ap['cabelo']}")
    return rig


def build(out):
    for cid, doc in PROOF.items():
        compose(cid, doc).save(os.path.join(out, "characters", "custom", cid))
