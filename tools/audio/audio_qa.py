#!/usr/bin/env python3
"""Rodada 09.4 — QA técnico de áudio (música e efeitos). Mede; NÃO julga a arte (isso é escuta humana).

    .venv/bin/python tools/audio/audio_qa.py [--tipo musica|efeito] [--md SAIDA.md] [--imagens PASTA]
                                             [--laco INICIO,FIM] [--duracao MIN,MAX] arquivo...
    .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json [--md ...]   # tudo do catálogo

Mede por arquivo: formato, taxa, canais, duração, pico de amostra e pico real (sobreamostragem 4×),
amostras saturadas, RMS, sonoridade integrada (LUFS, ffmpeg ebur128), silêncio no início/fim, nível dos
últimos 5 ms (cauda cortada), deslocamento DC e, com --laco, a emenda do laço (salto na junção comparado à
variação normal do próprio sinal). Gera forma de onda + espectrograma (ffmpeg) com --imagens.

Regras (alertas, não aprovação):
  todos   taxa 48 000 Hz; nada saturado; pico real ≤ −1 dBFS; DC < 0,002; decodifica
  música  sonoridade entre −20 e −16 LUFS; laço sem salto (≤ 3× a variação normal, ou ≤ 1,2× o salto da passagem natural); fim da 2ª passada ≈ fim da 1ª
          (nível ≤ 1 dB e envelope por oitava ≤ 1,5 dB em média). A correlação de forma de onda é só
          informativa: o ReverbSC do LMMS modula as linhas de atraso, então caudas iguais nunca têm a mesma
          fase (seco dá ~1,0; com sala grande e eco, ~0,89)
  ambiente sonoridade entre −28 e −20 LUFS; laço inteiro (fim → começo) sem salto (≤ 3× a variação normal)
  efeito  ataque em ≤ 10 ms (latência percebida); fim em silêncio (últimos 5 ms < −50 dB do pico);
          duração dentro da faixa do evento
"""
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT = Path(__file__).resolve().parents[2]


def db(x):
    return 20 * np.log10(max(float(x), 1e-12))


def lufs(path: Path) -> float | None:
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128", "-f", "null", "-"],
                       capture_output=True, text=True)
    m = re.findall(r"I:\s+(-?[\d.]+|-inf) LUFS", r.stderr)
    return None if not m or m[-1] == "-inf" else float(m[-1])


def measure(path: Path, kind: str, loop=None, dur_range=None) -> dict:
    r = {"arquivo": str(path), "alertas": []}
    try:
        x, sr = sf.read(str(path), always_2d=True)
        info = sf.info(str(path))
    except Exception as e:      # noqa: BLE001
        r["alertas"].append(f"não decodifica: {e}")
        return r
    r |= {"formato": f"{info.format}/{info.subtype}", "taxa": sr, "canais": x.shape[1], "duracao_s": len(x) / sr}
    if not np.isfinite(x).all():
        r["alertas"].append("NaN/inf no sinal")
        x = np.nan_to_num(x)
    mono = x.mean(1)
    a = np.abs(x).max(1)
    peak = a.max()
    r["pico_dbfs"] = db(peak)
    up = resample_poly(x, 4, 1, axis=0) if len(x) < sr * 400 else x
    r["pico_real_dbfs"] = db(np.abs(up).max())
    r["saturadas"] = int((a >= 0.999).sum())
    r["rms_dbfs"] = db(np.sqrt((mono ** 2).mean()))
    r["lufs"] = lufs(path) if r["duracao_s"] >= 0.4 else None
    r["dc"] = float(abs(mono.mean()))
    thr = peak * 10 ** (-50 / 20)
    on = np.argmax(a > max(thr, 10 ** (-60 / 20)))
    r["ataque_ms"] = 1000 * on / sr
    nz = np.nonzero(a > thr)[0]
    r["silencio_final_ms"] = 1000 * (len(a) - 1 - (nz[-1] if len(nz) else 0)) / sr
    n5 = max(1, int(0.005 * sr))
    r["ultimos5ms_db_rel"] = db(a[-n5:].max() / peak) if peak > 0 else -120
    if loop:
        s0, s1 = loop
        w = int(0.010 * sr)
        d = np.abs(np.diff(mono))
        typical = np.percentile(d, 99.5)
        joint = np.concatenate([mono[s1 - w:s1], mono[s0:s0 + w]])
        jump = np.abs(np.diff(joint)).max()
        r["laco_salto_razao"] = float(jump / max(typical, 1e-9))
        if s0 >= w:   # o mesmo salto na passagem natural (fim da 1ª → início da 2ª): ataque musical, não emenda
            r["laco_salto_natural"] = float(np.abs(np.diff(mono[s0 - w:s0 + w])).max() / max(typical, 1e-9))
        # A emenda tem de soar como a passagem NATURAL do corpo para ele mesmo. Se o arquivo tem a 1ª passada
        # antes do laço (fim da 1ª passada = s0), compara os 250 ms antes de s1 (fim da 2ª) com os 250 ms antes
        # de s0 (fim da 1ª): iguais → voltar de s1 a s0 equivale a seguir tocando.
        q = sr // 4
        e0 = db(np.sqrt((mono[s1 - q:s1] ** 2).mean()))
        e1 = db(np.sqrt((mono[s0:s0 + q] ** 2).mean()))
        r["laco_energia_db"] = (round(float(e0), 1), round(float(e1), 1))
        if s0 >= q:
            a0, a1 = mono[s0 - q:s0], mono[s1 - q:s1]
            r["laco_correlacao_natural"] = float(np.dot(a0, a1) / (np.linalg.norm(a0) * np.linalg.norm(a1) + 1e-12))
            r["laco_dif_nivel_natural_db"] = float(abs(db(np.sqrt((a0 ** 2).mean())) - e0))
            # envelope espectral por oitava (125 Hz–8 kHz): robusto à fase aleatória da reverberação
            f = np.fft.rfftfreq(q, 1 / sr)
            p0, p1 = np.abs(np.fft.rfft(a0 * np.hanning(q))) ** 2, np.abs(np.fft.rfft(a1 * np.hanning(q))) ** 2
            difs = []
            for lo in (125, 250, 500, 1000, 2000, 4000):
                k = (f >= lo) & (f < 2 * lo)
                difs.append(abs(10 * np.log10((p0[k].sum() + 1e-15) / (p1[k].sum() + 1e-15))))
            r["laco_dif_espectral_db"] = float(np.mean(difs))
    al = r["alertas"]
    if sr != 48000:
        al.append(f"taxa {sr}")
    if r["saturadas"]:
        al.append(f"{r['saturadas']} amostras saturadas")
    if r["pico_real_dbfs"] > -1.0:
        al.append(f"pico real {r['pico_real_dbfs']:.1f} dBFS > −1")
    if r["dc"] > 0.002:
        al.append(f"DC {r['dc']:.4f}")
    if kind == "musica":
        if r["lufs"] is not None and not -20 <= r["lufs"] <= -16:
            al.append(f"sonoridade {r['lufs']:.1f} LUFS fora de −20..−16")
        if loop and r["laco_salto_razao"] > max(3.0, 1.2 * r.get("laco_salto_natural", 0.0)):
            al.append(f"emenda do laço com salto ({r['laco_salto_razao']:.1f}× a variação normal)")
        if loop and "laco_dif_espectral_db" in r and (r["laco_dif_espectral_db"] > 1.5
                                                       or r["laco_dif_nivel_natural_db"] > 1.0):
            al.append(f"emenda difere da passagem natural (espectro {r['laco_dif_espectral_db']:.1f} dB, "
                      f"nível {r['laco_dif_nivel_natural_db']:.1f} dB)")
    elif kind == "ambiente":
        if r["lufs"] is not None and not -28 <= r["lufs"] <= -20:
            al.append(f"sonoridade {r['lufs']:.1f} LUFS fora de −28..−20")
        d = np.abs(np.diff(mono))
        seam = abs(mono[0] - mono[-1]) / max(np.percentile(d, 99.5), 1e-9)
        r["laco_fim_comeco_razao"] = float(seam)
        if seam > 3:
            al.append(f"emenda do laço com salto ({seam:.1f}×)")
    else:
        if r["ataque_ms"] > 10:
            al.append(f"ataque em {r['ataque_ms']:.0f} ms (silêncio antes do som)")
        if r["ultimos5ms_db_rel"] > -50:
            al.append(f"cauda cortada (últimos 5 ms a {r['ultimos5ms_db_rel']:.0f} dB do pico)")
        if dur_range and not dur_range[0] <= r["duracao_s"] <= dur_range[1]:
            al.append(f"duração {r['duracao_s']:.2f} s fora de {dur_range}")
    return r


def images(path: Path, outdir: Path, kind: str):
    outdir.mkdir(parents=True, exist_ok=True)
    stem = path.stem
    w = 1600 if kind == "musica" else 800
    subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(path), "-lavfi",
                    f"showspectrumpic=s={w}x400:legend=1:scale=log:fscale=log:color=intensity",
                    str(outdir / f"{stem}-espectro.png")])
    subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(path), "-lavfi",
                    f"showwavespic=s={w}x200:split_channels=1:scale=sqrt:colors=#c9a24a|#7a9a5c",
                    "-frames:v", "1", str(outdir / f"{stem}-onda.png")])


def table(rows: list[dict]) -> str:
    h = ("| arquivo | formato | taxa | dur (s) | pico real | LUFS | RMS | ataque (ms) | fim (5 ms) | alertas |\n"
         "|---|---|---|---|---|---|---|---|---|---|\n")
    for r in rows:
        if "taxa" not in r:
            h += f"| `{Path(r['arquivo']).name}` | — | — | — | — | — | — | — | — | {'; '.join(r['alertas'])} |\n"
            continue
        lu = "—" if r["lufs"] is None else f"{r['lufs']:.1f}"
        h += (f"| `{Path(r['arquivo']).name}` | {r['formato']} | {r['taxa']} | {r['duracao_s']:.2f} | "
              f"{r['pico_real_dbfs']:.1f} | {lu} | {r['rms_dbfs']:.1f} | {r['ataque_ms']:.1f} | "
              f"{r['ultimos5ms_db_rel']:.0f} dB | {'; '.join(r['alertas']) or 'ok'} |\n")
    return h


def main():
    args = sys.argv[1:]
    opt = {}
    for k in ("--tipo", "--md", "--imagens", "--laco", "--duracao", "--catalogo"):
        if k in args:
            i = args.index(k)
            opt[k] = args[i + 1]
            del args[i:i + 2]
    rows = []
    if "--catalogo" in opt:
        cat = json.loads(Path(opt["--catalogo"]).read_text())
        base = Path(opt["--catalogo"]).parent
        for ev, d in cat["eventos"].items():
            for f in d["arquivos"]:
                rows.append(measure(base / f, "efeito", dur_range=d.get("duracao")) | {"evento": ev})
        for m, d in cat.get("ambientes", {}).items():
            rows.append(measure(base / d["arquivo"], "ambiente") | {"evento": m})
        for m, d in cat.get("musicas", {}).items():
            rows.append(measure(base / d["arquivo"], "musica",
                                loop=(d["laco_inicio"], d["laco_fim"])) | {"evento": m})
    else:
        kind = opt.get("--tipo", "efeito")
        loop = tuple(int(v) for v in opt["--laco"].split(",")) if "--laco" in opt else None
        dr = tuple(float(v) for v in opt["--duracao"].split(",")) if "--duracao" in opt else None
        for a in args:
            rows.append(measure(Path(a), kind, loop, dr))
            if "--imagens" in opt:
                images(Path(a), Path(opt["--imagens"]), kind)
    md = table(rows)
    extra = [r for r in rows if "laco_salto_razao" in r]
    for r in extra:
        md += f"\nLaço `{Path(r['arquivo']).name}`: salto na emenda {r['laco_salto_razao']:.2f}× a variação normal " \
              f"(passagem natural {r.get('laco_salto_natural', float('nan')):.2f}×); " \
              f"energia 250 ms antes/depois {r['laco_energia_db']} dBFS"
        if "laco_correlacao_natural" in r:
            md += f"; fim da 2ª passada × fim da 1ª: espectro {r['laco_dif_espectral_db']:.2f} dB, correlação {r['laco_correlacao_natural']:.4f}, " \
                  f"nível {r['laco_dif_nivel_natural_db']:.2f} dB"
        md += ".\n"
    n = sum(bool(r["alertas"]) for r in rows)
    md += f"\n**{len(rows)} arquivos, {n} com alerta.**\n"
    if "--md" in opt:
        Path(opt["--md"]).parent.mkdir(parents=True, exist_ok=True)
        Path(opt["--md"]).write_text(md)
    print(md)
    sys.exit(1 if n else 0)


if __name__ == "__main__":
    main()
