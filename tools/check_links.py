#!/usr/bin/env python3
"""Rodada 05 — verifica (e, com --fix, corrige) links relativos em Markdown.

    python3 tools/check_links.py            # lista links quebrados em docs/, README.md, CLAUDE.md, .claude/skills
    python3 tools/check_links.py --fix      # reescreve links quebrados cujo arquivo-alvo existe em outro lugar
                                            # com o mesmo nome (único no repositório) — usado após mover documentos

Ignora URLs externas, âncoras puras e caminhos para output/ (pacotes de revisão, fora do Git).
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINK = re.compile(r"\[([^\]]*)\]\(([^)\s]+)\)")
SCAN = ["docs", "README.md", "CLAUDE.md", ".claude/skills"]


def md_files():
    for s in SCAN:
        p = os.path.join(ROOT, s)
        if os.path.isfile(p):
            yield p
        else:
            for r, _, fs in os.walk(p):
                for f in fs:
                    if f.endswith(".md"):
                        yield os.path.join(r, f)


def index_by_name():
    idx = {}
    for r, dirs, fs in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in (".git", "build", "output", ".gradle", ".venv", "node_modules")]
        for f in fs:
            idx.setdefault(f, []).append(os.path.join(r, f))
    return idx


def main():
    fix = "--fix" in sys.argv
    idx = index_by_name()
    broken = fixed = 0
    for md in md_files():
        text = open(md, encoding="utf-8").read()
        new = text
        for m in LINK.finditer(text):
            target = m.group(2)
            if re.match(r"^[a-z]+://", target) or target.startswith("#") or target.startswith("mailto:"):
                continue
            path = target.split("#")[0]
            if not path or path.startswith("output/") or "/output/" in path:
                continue
            full = os.path.normpath(os.path.join(os.path.dirname(md), path))
            if os.path.exists(full):
                continue
            broken += 1
            cands = idx.get(os.path.basename(path), [])
            if fix and len(cands) == 1:
                rel = os.path.relpath(cands[0], os.path.dirname(md))
                anchor = target[len(path):]
                new = new.replace(f"]({target})", f"]({rel}{anchor})")
                fixed += 1
            else:
                print(f"QUEBRADO {os.path.relpath(md, ROOT)} -> {target}" + (f"  (candidatos: {len(cands)})" if cands else ""))
        if new != text:
            open(md, "w", encoding="utf-8").write(new)
    print(f"links quebrados: {broken}; corrigidos: {fixed}")
    return 0 if broken == fixed else 1


if __name__ == "__main__":
    sys.exit(main())
