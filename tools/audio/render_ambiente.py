#!/usr/bin/env python3
"""Rodada 09 — renderiza uma camada de ambiente do LMMS como laço sem emenda.

    .venv/bin/python tools/audio/render_ambiente.py fontes/audio/ambiente/vento.mmp SAIDA [--runtime assets/audio/ambiente/vento]

A sessão tem laço_s + sobra_s segundos (ver <sessao>.laco.json, escrito pelo gerador). O laço é [0, laço_s) com a
sobra fundida no começo (crossfade de igual potência): o fim do laço continua no começo como continuaria no render.
Sonoridade padronizada em −24 LUFS (a mistura do jogo fica no catálogo). Estéreo para camas sem posição; mono para
fontes posicionais (o OpenAL só posiciona mono). Saídas: SAIDA/<nome>-master.wav (24 bits) e, com --runtime, o OGG.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).parent))
import render_musica as R  # noqa: E402

TARGET = -24.0


def main():
    a = sys.argv[1:]
    runtime = None
    if "--runtime" in a:
        i = a.index("--runtime"); runtime = Path(a[i + 1]); del a[i:i + 2]
    mmp, out = Path(a[0]), Path(a[1])
    out.mkdir(parents=True, exist_ok=True)
    spec = json.loads(mmp.with_suffix(".laco.json").read_text())
    with tempfile.TemporaryDirectory() as td:
        raw = Path(td) / "a.wav"
        R.lmms_render(mmp, raw)
        x, sr = sf.read(str(raw), always_2d=True)
    L, O = int(spec["laco_s"] * sr), int(spec["sobra_s"] * sr)
    if len(x) < L + O:
        raise SystemExit(f"render curto: {len(x)} < {L + O}")
    if not spec["estereo"]:
        x = x.mean(1, keepdims=True)
    y = x[:L].copy()
    t = np.linspace(0, np.pi / 2, O)[:, None]
    y[:O] = x[:O] * np.sin(t) + x[L:L + O] * np.cos(t)                 # sobra entra no começo, igual potência
    y -= y.mean(0)                                                    # sem DC
    master = out / f"{mmp.stem}-master.wav"
    sf.write(str(master), y, sr, subtype="FLOAT")
    lufs, _ = R.loudness(master)
    y *= 10 ** ((TARGET - lufs) / 20)
    peak = np.abs(y).max()
    if peak > 0.89:
        y *= 0.89 / peak
    sf.write(str(master), y, sr, subtype="PCM_24")
    info = {"fonte": str(mmp), "laco_amostras": L, "lufs_bruto": lufs, "estereo": spec["estereo"]}
    if runtime:
        runtime.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(master), "-c:a", "libvorbis",
                        "-q:a", "4", str(runtime.with_suffix(".ogg"))], check=True)
    (out / f"{mmp.stem}-render.json").write_text(json.dumps(info, indent=1, ensure_ascii=False))
    print(json.dumps(info, ensure_ascii=False))


if __name__ == "__main__":
    main()
