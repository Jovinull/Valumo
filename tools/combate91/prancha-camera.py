#!/usr/bin/env python3
"""Rodada 09.1 — prancha antes × depois da câmera de combate (capturas reais do jogo + medição de enquadramento).
Uso: prancha-camera.py <pasta camera> <log legado> <log depois>"""
import glob, os, re, sys
from PIL import Image, ImageDraw
R, leg, dep = sys.argv[1:4]
log = {}
for m, f in (("antes", leg), ("depois", dep)):
    for line in open(f, encoding="utf-8"):
        mm = re.match(r"enquadramento (\S+): dist ([\d,]+) u, herói (\d+)% da tela.*inimigo (\d+)% visível", line)
        if mm:
            log[(m, mm[1])] = f"dist {mm[2]} u · herói {mm[3]}% da tela · fungo {mm[4]}% visível"
names = [os.path.basename(f)[:-4] for f in sorted(glob.glob(R + "/antes/*.png"))]
W, H = 640, 360
board = Image.new("RGB", (2 * W + 10, len(names) * (H + 26) + 30), "white")
d = ImageDraw.Draw(board)
d.text((8, 8), "Rodada 09.1 - camera de combate no jogo real: ANTES (binario da rodada 09) x DEPOIS (09.1), mesmo instante logico", fill=(0, 0, 0))
for i, n in enumerate(names):
    y = 30 + i * (H + 26)
    for j, m in enumerate(("antes", "depois")):
        board.paste(Image.open(f"{R}/{m}/{n}.png").convert("RGB").resize((W, H)), (j * (W + 10), y + 22))
        d.text((j * (W + 10) + 4, y + 6), f"{n} - {m.upper()}: {log.get((m, n), '')}", fill=(0, 0, 0))
board.save(R + "/prancha-antes-depois.png")
print(board.size)
