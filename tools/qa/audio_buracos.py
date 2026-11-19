#!/usr/bin/env python3
"""QA de áudio em tempo real — procura buracos de silêncio numa gravação (WAV de qualquer formato).

Uso: .venv/bin/python tools/qa/audio_buracos.py GRAVACAO.wav [ms_minimo=20]
Buraco = trecho abaixo de −80 dBFS, depois do primeiro som, com pelo menos ms_minimo. Música e ambiente tocam juntos:
um fluxo que passa fome deixa a saída em silêncio digital (o OpenAL Soft para a fonte quando a fila esvazia).
Saída 1 se houver buraco.
"""
import sys

import numpy as np
import soundfile as sf

x, sr = sf.read(sys.argv[1], always_2d=True)
minimo = (float(sys.argv[2]) if len(sys.argv) > 2 else 20.0) / 1000.0
m = np.abs(x).max(axis=1)
win = int(0.010 * sr)
n = len(m) // win
env = m[:n * win].reshape(n, win).max(axis=1)
start = int(np.argmax(env > 0.01))
gaps, i = [], start
while i < n:
    if env[i] < 1e-4:
        j = i
        while j < n and env[j] < 1e-4:
            j += 1
        if (j - i) * 0.010 >= minimo:
            gaps.append((i * 0.010, (j - i) * 0.010))
        i = j
    else:
        i += 1
print(f"duração {len(x) / sr:.2f} s, pico {m.max():.3f}, som desde {start * 0.010:.2f} s")
print(f"silêncios (< -80 dBFS) de {minimo * 1000:.0f} ms ou mais depois do início: "
      + (", ".join(f"{a:.2f} s por {d:.2f} s" for a, d in gaps) or "nenhum"))
sys.exit(1 if gaps else 0)
