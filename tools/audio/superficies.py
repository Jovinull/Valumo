#!/usr/bin/env python3
"""Rodada 09.4 — distinção entre superfícies de passo (o jogador precisa reconhecer em que pisa).

    .venv/bin/python tools/audio/superficies.py PASTA [--md SAIDA.md] grama_a terra_a areia pedra

Por grupo (andar): espectro médio em bandas de 1/3 de oitava (100 Hz–12 kHz), duração mediana até −40 dB e fator de
crista (pico/RMS, "estalado" × "macio"). Entre grupos: distância espectral média (dB). Critério de trabalho: pares de
superfícies ≥ 3 dB de distância espectral OU ≥ 2× de diferença em duração/crista.
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf


def bands(x, sr):
    f = np.fft.rfftfreq(len(x), 1 / sr)
    P = np.abs(np.fft.rfft(x, n=max(len(x), 4096))) ** 2
    f = np.fft.rfftfreq(max(len(x), 4096), 1 / sr)
    edges = 100 * 2 ** (np.arange(0, 21) / 3)
    e = [P[(f >= a) & (f < b)].sum() + 1e-12 for a, b in zip(edges, edges[1:]) if b < 12500]
    e = np.array(e)
    return 10 * np.log10(e / e.sum())


def main():
    a = sys.argv[1:]
    md = None
    if "--md" in a:
        i = a.index("--md"); md = a[i + 1]; del a[i:i + 2]
    d, groups = Path(a[0]), a[1:]
    stats = {}
    for g in groups:
        fs = sorted(d.glob(f"{g}_andar_*.wav"))
        spec, dur, crest = [], [], []
        for f in fs:
            x, sr = sf.read(f)
            spec.append(bands(x, sr))
            ab = np.abs(x); pk = ab.max()
            dur.append(1000 * (np.nonzero(ab > pk * 0.01)[0][-1]) / sr)
            crest.append(20 * np.log10(pk / (np.sqrt((x ** 2).mean()) + 1e-12)))
        stats[g] = (np.mean(spec, axis=0), np.median(dur), np.median(crest))
    out = ["| superfície | duração mediana até −40 dB (ms) | fator de crista (dB) |", "|---|---|---|"]
    out += [f"| {g} | {s[1]:.0f} | {s[2]:.1f} |" for g, s in stats.items()]
    out += ["", "Distância espectral média entre superfícies (dB, bandas de 1/3 de oitava):", "",
            "| | " + " | ".join(groups) + " |", "|---" * (len(groups) + 1) + "|"]
    for g in groups:
        out.append(f"| {g} | " + " | ".join("—" if g == h else f"{np.mean(np.abs(stats[g][0] - stats[h][0])):.1f}"
                                            for h in groups) + " |")
    text = "\n".join(out) + "\n"
    print(text)
    if md:
        Path(md).write_text(text)


if __name__ == "__main__":
    main()
