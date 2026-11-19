#!/usr/bin/env python3
"""Laboratório de conteúdo — prova de determinismo: gera env_e + conteudo_e em DUAS pastas, em dois processos
separados, e compara byte a byte cada .vox e .json do laboratório (props.json, catálogo, rigs de estudo, conjuntos).

    .venv/bin/python tools/conteudo/determinismo.py PASTA_TEMP
"""
import hashlib
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)


def gen(out):
    code = (f"import sys; sys.path.insert(0, {TOOLS!r}); import env_e, conteudo_e; "
            f"env_e.build({out!r}); conteudo_e.build({out!r})")
    subprocess.run([sys.executable, "-c", code], check=True, stdout=subprocess.DEVNULL)


def digest(root):
    out = {}
    for d, _, files in os.walk(root):
        for f in files:
            p = os.path.join(d, f)
            out[os.path.relpath(p, root)] = hashlib.sha256(open(p, "rb").read()).hexdigest()
    return out


def main():
    tmp = sys.argv[1]
    a, b = os.path.join(tmp, "a"), os.path.join(tmp, "b")
    gen(a)
    gen(b)
    da, db = digest(a), digest(b)
    diff = sorted(k for k in set(da) | set(db) if da.get(k) != db.get(k))
    lab = [k for k in da if "conteudo" in k or k.endswith("props.json")]
    print(f"arquivos: {len(da)} (do laboratório ou manifesto: {len(lab)}); diferentes: {len(diff)}")
    for k in diff[:20]:
        print("  DIFERENTE:", k)
    sys.exit(1 if diff else 0)


if __name__ == "__main__":
    main()
