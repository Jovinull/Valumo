#!/usr/bin/env python3
"""Rodada 09.1 — prancha de revisão dos efeitos sonoros: forma de onda (com o envelope RMS) e espectrograma de cada
arquivo de assets/audio/sintese/, mais os números de analise.json. Para revisar ataque, decaimento, faixa de frequência,
repetição entre variações e cortes sem depender só do ouvido.

Uso: python3 tools/audio_board.py <saida.png> [pasta-de-áudio]
"""
import json
import os
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw

SR = 48000
W, H_WAVE, H_SPEC, LABEL = 560, 70, 110, 16
FMAX = 12000


def load(path):
    with wave.open(path) as w:
        return np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(float) / 32768


def spectrogram(y, cols, rows):
    n = 1024
    hop = max(1, (len(y) - n) // max(cols - 1, 1)) if len(y) > n else 1
    yy = np.pad(y, (0, n))
    win = np.hanning(n)
    img = np.zeros((rows, cols))
    for c in range(cols):
        a = min(c * hop, len(yy) - n)
        mag = np.abs(np.fft.rfft(yy[a:a + n] * win))
        fr = np.fft.rfftfreq(n, 1 / SR)
        col = np.interp(np.linspace(0, FMAX, rows), fr, mag)
        img[:, c] = 20 * np.log10(col + 1e-6)
    img = np.clip((img + 70) / 70, 0, 1)                               # −70..0 dB
    return img[::-1]


def tile(name, y, info, dur_max):
    im = Image.new("RGB", (W, LABEL + H_WAVE + H_SPEC), (250, 248, 242))
    d = ImageDraw.Draw(im)
    txt = name if not info else f"{name}  {info['duracao_s']:.2f}s  pico {info['pico_dbfs']} dBFS  RMS {info['rms_dbfs']}  centr {info['centroide_hz']} Hz  cortes {info['cortadas']}"
    d.text((4, 2), txt, fill=(30, 30, 30))
    wpx = int(W * len(y) / SR / dur_max)                               # mesma escala de tempo em todas
    mid = LABEL + H_WAVE // 2
    for x in range(max(wpx, 1)):
        a, b = int(x * len(y) / wpx), int((x + 1) * len(y) / wpx)
        seg = y[a:max(b, a + 1)]
        d.line([(x, mid - seg.max() * H_WAVE / 2), (x, mid - seg.min() * H_WAVE / 2)], fill=(70, 110, 160))
        r = np.sqrt(np.mean(seg ** 2))
        d.point((x, mid - r * H_WAVE / 2), fill=(200, 80, 40))
    for db in (-3, -12):
        yy = 10 ** (db / 20) * H_WAVE / 2
        d.line([(0, mid - yy), (W, mid - yy)], fill=(220, 210, 200))
    sp = spectrogram(y, max(wpx, 2), H_SPEC)
    rgb = np.stack([sp ** 0.8 * 255, sp ** 1.6 * 220, (1 - sp) * 90 + sp * 40], -1).astype(np.uint8)
    im.paste(Image.fromarray(rgb), (0, LABEL + H_WAVE))
    for f in (1000, 4000, 8000):
        yy = LABEL + H_WAVE + int(H_SPEC * (1 - f / FMAX))
        d.line([(0, yy), (6, yy)], fill=(255, 255, 255))
        d.text((8, yy - 6), f"{f // 1000}k", fill=(255, 255, 255))
    return im


def main():
    out = sys.argv[1]
    folder = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets", "audio", "sintese")
    info = json.load(open(os.path.join(folder, "analise.json")))
    names = [n[:-4] for n in sorted(os.listdir(folder)) if n.endswith(".wav")]
    ys = {n: load(os.path.join(folder, n + ".wav")) for n in names}
    dur_max = max(len(y) for y in ys.values()) / SR
    tiles = [tile(n, ys[n], info.get(n), dur_max) for n in names]
    cols = 3
    rows = (len(tiles) + cols - 1) // cols
    th = tiles[0].height
    board = Image.new("RGB", (cols * (W + 6), rows * (th + 6) + 24), (255, 255, 255))
    ImageDraw.Draw(board).text((6, 6), f"Valumo 09.1 — efeitos sonoros ({len(names)} arquivos, escala de tempo comum: {dur_max:.2f} s na largura; espectro 0–12 kHz, −70..0 dB)", fill=(0, 0, 0))
    for i, t in enumerate(tiles):
        board.paste(t, ((i % cols) * (W + 6), 24 + (i // cols) * (th + 6)))
    board.save(out)
    print(out, board.size)


if __name__ == "__main__":
    main()
