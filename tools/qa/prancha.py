"""Prancha de contato: prancha.py SAIDA COLUNAS LARGURA_CELULA IMG..."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw
out, cols, cw = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
files = sys.argv[4:]
ims = []
for f in files:
    im = Image.open(f).convert("RGB")
    h = round(im.height * cw / im.width)
    ims.append((Path(f).stem, im.resize((cw, h), Image.LANCZOS)))
ch = max(i.height for _, i in ims) + 18
rows = -(-len(ims) // cols)
sheet = Image.new("RGB", (cols * cw, rows * ch), (24, 24, 28))
d = ImageDraw.Draw(sheet)
for k, (name, im) in enumerate(ims):
    x, y = (k % cols) * cw, (k // cols) * ch
    sheet.paste(im, (x, y + 18))
    d.text((x + 4, y + 3), name, fill=(235, 235, 235))
sheet.save(out, quality=88)
print(out, sheet.size)
