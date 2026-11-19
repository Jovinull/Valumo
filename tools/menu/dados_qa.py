"""Rodada 11.1 (fechamento) — dados de QA para listas longas, numa pasta TEMPORÁRIA já com um personagem e um mundo
criados pelos roteiros (02 + 03): clona o primeiro perfil e o primeiro mundo com ids novos, nomes longos e acentuados e
sementes variadas. Os mundos clonados ficam SEM prévia (o menu prepara quando são selecionados). Nunca use em output/.

Uso: python3 tools/menu/dados_qa.py PASTA_TEMPORARIA
"""
import json
import random
import sys
from pathlib import Path

root = Path(sys.argv[1])
assert "tmp" in root.parts, "só em pasta temporária"
rnd = random.Random(111)
NOMES = ["Iára da Clareira", "Ãngelo Çarvalho", "Bartolomeu Pé-de-Vento Andarilho", "Zé", "Maëlle",
         "Ñoño", "Ofélia Brasa", "Tomé", "Antônia Lúcia Conceição", "Kauã", "Lívia", "Úrsula"]
pdir = root / "p"
base = json.loads(next(pdir.glob("p-*.json")).read_text())
for n in NOMES:
    o = dict(base)
    o["id"] = "p-" + "".join(rnd.choice("0123456789abcdef") for _ in range(12))
    o["nome"] = n
    (pdir / (o["id"] + ".json")).write_text(json.dumps(o, ensure_ascii=False, indent=2))
mdir = root / "m"
mbase = json.loads(next(mdir.glob("m-*/mundo.json")).read_text())
for i, (n, s) in enumerate([("Planície das Lanternas do Norte", "2048"), ("Vale", "4096"), ("Água Rasa", "77"),
                            ("Semente máxima", "18446744073709551615"), ("Brumas", "555"), ("Ermo", "9001")]):
    o = dict(mbase)
    o["id"] = "m-" + "".join(rnd.choice("0123456789abcdef") for _ in range(12))
    o["nome"] = n
    o["semente"] = s
    o.pop("previa", None)
    o.pop("resumo", None)
    (mdir / o["id"]).mkdir()
    (mdir / o["id"] / "mundo.json").write_text(json.dumps(o, ensure_ascii=False, indent=2))
print(len(NOMES), "personagens e 6 mundos clonados em", root)
