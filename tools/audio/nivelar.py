#!/usr/bin/env python3
"""Rodada 09.4 (opção B, síntese própria) — sugere o volume de cada faixa de uma partitura para que, TOCANDO SOZINHA
(com o EQ e a reverberação dela), cada uma fique no nível do seu papel.

    .venv/bin/python tools/audio/nivelar.py fontes/audio/musica/vila/vila_a.py "Flauta=0" "Violão=-4" ...

Os alvos são em dB relativos à faixa de referência (a primeira). A sonoridade é a integrada (LUFS) só sobre o trecho
em que a faixa toca. Imprime o `vol=` novo de cada faixa, para copiar na partitura (o gerador continua puro, sem
precisar do LMMS). Por que existe: os volumes antigos foram equilibrados para o banco de amostras; cada sintetizador
tem uma sonoridade diferente, então o equilíbrio tem de ser medido de novo (não chutado).
"""
import copy
import importlib.util
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import lmms as L  # noqa: E402
import render_musica as R  # noqa: E402


def load_song(py: Path):
    spec = importlib.util.spec_from_file_location("partitura", py)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.song


def lufs(wav: Path, start: float, end: float) -> float:
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-ss", f"{start:.3f}", "-to", f"{end:.3f}", "-i", str(wav),
                        "-af", "ebur128", "-f", "null", "-"], capture_output=True, text=True)
    return float(re.findall(r"I:\s+(-?[\d.]+) LUFS", r.stderr)[-1])


def main():
    py = Path(sys.argv[1])
    targets = dict((k, float(v)) for k, v in (a.split("=") for a in sys.argv[2:]))
    song = load_song(py)
    tick_s = 60 / song.bpm / 48                                  # 48 ticks por semínima
    meas = {}
    with tempfile.TemporaryDirectory(dir="/tmp") as td:
        for t in song.tracks:
            if t.name not in targets:
                continue
            solo = copy.copy(song)
            solo.tracks = [t]
            solo.loop = None
            mmp, wav = Path(td) / "solo.mmp", Path(td) / "solo.wav"
            mmp.write_text(solo.mmp(), encoding="utf-8")
            R.lmms_render(mmp, wav)
            a = min(n.pos for n in t.notes) * tick_s
            b = max(n.pos + n.len for n in t.notes) * tick_s
            meas[t.name] = (lufs(wav, a, b), t.vol)
            print(f"{t.name:16s} vol {t.vol:3d}: {meas[t.name][0]:6.1f} LUFS sozinha", flush=True)
    ref = next(iter(targets))
    r0 = meas[ref][0]
    print("\nsugestão (alvo relativo à faixa '%s'):" % ref)
    for name, (l, vol) in meas.items():
        want = r0 + targets[name]
        new = vol * 10 ** ((want - l) / 20)
        flag = "  (passa de 200: suba o ganho do instrumento)" if new > 200 else ""
        print(f"  {name:16s} {l:6.1f} → {want:6.1f} LUFS   vol {vol:3d} → {min(200, round(new)):3d}{flag}")


if __name__ == "__main__":
    main()
