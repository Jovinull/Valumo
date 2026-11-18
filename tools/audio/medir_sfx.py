#!/usr/bin/env python3
"""Rodada 09.4 — medidas por grupo de efeitos fatiados (duração, pico, centroide, energia grave, variação).
    .venv/bin/python tools/audio/medir_sfx.py PASTA   (PASTA com fatias.json do render_sfx)"""
import json, sys
import numpy as np, soundfile as sf
d = sys.argv[1]
r = json.load(open(f"{d}/fatias.json"))
groups = {}
for e in r:
    x, sr = sf.read(f"{d}/{e['id']}.wav")
    f = np.fft.rfftfreq(len(x), 1 / sr); P = np.abs(np.fft.rfft(x)) ** 2
    a = np.abs(x); on = int(np.argmax(a > a.max() * 0.03)); w = x[on:on + int(0.06 * sr)]
    rms = 20 * np.log10(np.sqrt((w ** 2).mean()) + 1e-12)
    groups.setdefault(e["grupo"], []).append((e["dur_ms"], rms, (f * P).sum() / P.sum(), P[f < 300].sum() / P.sum(),
                                              np.log10(P[(f > 200) & (f < 12000)] + 1e-20)))
print("| grupo | n | duração (ms) | RMS do corpo 60 ms (dBFS) | centroide (Hz) | energia < 300 Hz | distância espectral entre variações |")
print("|---|---|---|---|---|---|---|")
for g, v in groups.items():
    a = np.array([x[:4] for x in v], float)
    L = min(len(x[4]) for x in v)
    specs = [x[4][:L] for x in v]
    dist = np.mean([np.mean(np.abs(specs[i] - specs[j])) * 10 for i in range(len(specs)) for j in range(i + 1, len(specs))]) if len(specs) > 1 else 0
    print(f"| {g} | {len(v)} | {a[:,0].min():.0f}–{a[:,0].max():.0f} | {a[:,1].min():.1f} a {a[:,1].max():.1f} | "
          f"{a[:,2].mean():.0f} | {100*a[:,3].mean():.0f}% | {dist:.1f} dB |")
