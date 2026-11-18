#!/usr/bin/env python3
"""Rodada 09.4 — renderiza uma sessão de efeitos do LMMS e fatia cada som em seu arquivo.

    .venv/bin/python tools/audio/render_sfx.py fontes/audio/sfx/passos/passos.mmp SAIDA [--pico -6]

A sessão tem um som por compasso; `<sessao>.fatias.json` (escrito pelo gerador ou à mão) diz o id, o grupo,
o tick de início e a duração máxima de cada som. Passos:
1. render do LMMS (48 kHz, float);
2. posição real de cada tick calibrada (sonda com o mesmo andamento — render_musica.calibrate);
3. corte: do ataque (−50 dB do pico do grupo, recuado 1 ms) até o fim da cauda (−60 dB do pico do próprio som, +15 ms de
   folga) ou max_ms. Se o
   som ainda soa em max_ms, a duração é DESENHADA: descida cúbica nos últimos 35% (nunca um corte seco);
   senão, descida de 6 ms (sem estalo);
4. consistência: RMS do corpo (60 ms) de cada variação a ±--consistencia dB (padrão 2) da mediana do
   grupo — corrige só o excesso, para o acaso de fase dos grãos de ruído não virar "passo fraco";
5. mono (efeito posicional no OpenAL exige mono) e ganho ÚNICO por grupo: com --alvo-rms X, a mediana da
   sonoridade do corpo do grupo vai a X dBFS (pico limitado a −1 dBFS); sem ela, o pico do grupo vai a
   --pico dBFS. A diferença natural entre variações do mesmo evento é preservada; a mixagem ENTRE eventos
   fica no catálogo do jogo (assets/audio/sons.json), não nos arquivos;
6. WAV PCM 16 bits 48 kHz em SAIDA/<id>.wav + SAIDA/fatias.json com o ganho aplicado.
"""
import json
import sys
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).parent))
import render_musica as R  # noqa: E402


def main():
    args = sys.argv[1:]
    peak_db = -6.0
    spread_db = 2.0
    if "--consistencia" in args:
        i = args.index("--consistencia")
        spread_db = float(args[i + 1])
        del args[i:i + 2]
    if "--pico" in args:
        i = args.index("--pico")
        peak_db = float(args[i + 1])
        del args[i:i + 2]
    target_rms = None
    if "--alvo-rms" in args:
        i = args.index("--alvo-rms")
        target_rms = float(args[i + 1])
        del args[i:i + 2]
    mmp, outdir = Path(args[0]), Path(args[1])
    outdir.mkdir(parents=True, exist_ok=True)
    spec = json.loads(mmp.with_suffix(".fatias.json").read_text())
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        R.lmms_render(mmp, td / "sfx.wav")
        x, sr = sf.read(str(td / "sfx.wav"), always_2d=True)
        ticks = [f["tick"] for f in spec["fatias"]]
        pos = R.calibrate(mmp, td, [t for t in ticks if t > 0])
        pos = [0] * (len(ticks) - len(pos)) + pos
    from scipy.signal import butter, sosfiltfilt
    mono = sosfiltfilt(butter(2, 20, 'highpass', fs=sr, output='sos'), np.nan_to_num(x.mean(1)))   # sem DC
    bar = round(spec["compasso_ticks"] * 60 * sr / (spec["bpm"] * 48))
    clips = {}
    for f, p in zip(spec["fatias"], pos):
        seg = mono[p:p + bar]
        clips[f["id"]] = (f, seg)
    groups = {}
    for fid, (f, seg) in clips.items():
        groups.setdefault(f["grupo"], []).append(fid)
    report = []
    # consistência: RMS dos primeiros 60 ms de cada variação a no máximo ±spread_db da mediana do grupo
    # (a variação escrita nas intensidades fica; o acaso de fase dos grãos de ruído não)
    def body_rms(seg):
        a = np.abs(seg)
        on = int(np.argmax(a > a.max() * 0.03))
        w = seg[on:on + int(0.06 * sr)]
        return 20 * np.log10(np.sqrt((w ** 2).mean()) + 1e-12)
    fix = {}
    for g, ids in groups.items():
        lv = {i: body_rms(clips[i][1]) for i in ids}
        med = float(np.median(list(lv.values())))
        for i in ids:
            corr = float(np.clip(med - lv[i], -spread_db, spread_db)) if abs(med - lv[i]) > spread_db else 0.0
            corr = (med - lv[i]) - np.sign(med - lv[i]) * spread_db if corr else 0.0
            fix[i] = corr
            f, seg = clips[i]
            clips[i] = (f, seg * 10 ** (corr / 20))
    for g, ids in groups.items():
        gpk = max(np.abs(clips[i][1]).max() for i in ids)
        gain = 10 ** (peak_db / 20) / max(gpk, 1e-9)
        if target_rms is not None:     # sonoridade do corpo padronizada; pico nunca acima de −1 dBFS
            med = float(np.median([body_rms(clips[i][1]) for i in ids]))
            gain = min(10 ** ((target_rms - med) / 20), 10 ** (-2.5 / 20) / max(gpk, 1e-9))   # pico real ≤ −1
        for fid in ids:
            f, seg = clips[fid]
            a = np.abs(seg)
            on = max(0, int(np.argmax(a > gpk * 10 ** (-50 / 20))) - int(0.001 * sr))
            tail = np.nonzero(a > a.max() * 10 ** (-60 / 20))[0]          # fim pelo pico do PRÓPRIO som
            natural = min(len(a), (int(tail[-1]) + 1 if len(tail) else len(a)) + int(0.015 * sr))   # + 15 ms de folga
            end = min(natural, on + int(f["max_ms"] / 1000 * sr))
            y = seg[on:end].copy() * gain
            if natural > end:          # ainda soando: descida desenhada (cúbica) nos últimos 35%, não um corte
                n = int(0.35 * len(y))
            else:
                n = min(len(y), int(0.006 * sr))
            y[-n:] *= np.linspace(1, 0, n) ** 3
            sf.write(str(outdir / f"{fid}.wav"), y, sr, subtype="PCM_16")
            report.append({"id": fid, "grupo": g, "ganho_db": round(20 * np.log10(gain) + fix[fid], 2),
                           "correcao_consistencia_db": round(fix[fid], 2),
                           "dur_ms": round(1000 * len(y) / sr, 1), "pico_dbfs": round(20 * np.log10(np.abs(y).max() + 1e-12), 2)})
    (outdir / "fatias.json").write_text(json.dumps(report, indent=1, ensure_ascii=False))
    print(f"{len(report)} sons em {outdir}")


if __name__ == "__main__":
    main()
