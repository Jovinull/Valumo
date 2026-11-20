#!/usr/bin/env python3
"""Laboratório de conteúdo — prancha (grade de capturas com rótulos), para revisar um lote numa imagem só.

    .venv/bin/python tools/conteudo/prancha.py SAIDA.png COLUNAS LARGURA_CELULA a.png[:rótulo] b.png[:rótulo] ...
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# Fonte com acentos para os rótulos (a padrão do PIL não tem "ó", "ã", "ç"); sem nenhuma, cai na padrão.
FONTS = ("/usr/share/fonts/google-droid-sans-fonts/DroidSans.ttf", "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")


def font():
    for f in FONTS:
        if os.path.exists(f):
            return ImageFont.truetype(f, 14)
    return ImageFont.load_default()


def main():
    out, cols, cw = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    items = []
    for a in sys.argv[4:]:
        path, _, label = a.partition(":")
        im = Image.open(path).convert("RGB")
        ch = round(im.height * cw / im.width)
        items.append((im.resize((cw, ch), Image.LANCZOS), label or path.rsplit("/", 1)[-1]))
    ch = max(i.height for i, _ in items)
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cw, rows * (ch + 22)), (236, 230, 216))
    d = ImageDraw.Draw(sheet)
    fnt = font()
    for k, (im, label) in enumerate(items):
        x, y = (k % cols) * cw, (k // cols) * (ch + 22)
        sheet.paste(im, (x, y + 22))
        d.text((x + 6, y + 3), label, fill=(60, 40, 50), font=fnt)
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
