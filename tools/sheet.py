#!/usr/bin/env python3
"""Pranchas de revisão a partir de capturas reais do viewer (ferramenta de QA, usa Pillow do .venv).

  .venv/bin/python tools/sheet.py grid  saida.png COLS img1 img2 ...      mosaico simples
  .venv/bin/python tools/sheet.py small saida.png FATOR img               reduz (escala de jogo) e amplia
                                                                          sem filtro para inspeção
  .venv/bin/python tools/sheet.py seq   saida.png COLS prefixo_###.png     todos os quadros de uma sequência

Nenhum pixel é desenhado aqui além de rótulos opcionais (LABELS="a|b|c"); as imagens vêm do renderer.
"""
import glob
import os
import sys

from PIL import Image, ImageDraw


def grid(out, cols, files):
    ims = [Image.open(f).convert("RGB") for f in files]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    s = Image.new("RGB", (w * cols, h * rows), (24, 22, 28))
    labels = os.environ.get("LABELS", "").split("|") if os.environ.get("LABELS") else []
    d = ImageDraw.Draw(s)
    for i, im in enumerate(ims):
        x, y = (i % cols) * w, (i // cols) * h
        s.paste(im.resize((w, h)), (x, y))
        if i < len(labels) and labels[i]:
            d.rectangle([x, y + h - 26, x + 8 + 8 * len(labels[i]), y + h], fill=(20, 18, 24))
            d.text((x + 5, y + h - 21), labels[i], fill=(240, 236, 220))
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    s.save(out)


def small(out, factor, f):
    im = Image.open(f).convert("RGB")
    w, h = im.size
    sm = im.resize((max(1, int(w / factor)), max(1, int(h / factor))), Image.LANCZOS)
    sm.resize((w, h), Image.NEAREST).save(out)


def main():
    mode, out = sys.argv[1], sys.argv[2]
    if mode == "grid":
        grid(out, int(sys.argv[3]), sys.argv[4:])
    elif mode == "small":
        small(out, float(sys.argv[3]), sys.argv[4])
    elif mode == "seq":
        pat = sys.argv[4].replace("###", "[0-9][0-9][0-9]")
        grid(out, int(sys.argv[3]), sorted(glob.glob(pat)))


if __name__ == "__main__":
    main()
