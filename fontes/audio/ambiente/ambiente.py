"""Ambiente da vila — rodada 09.4 (estudo; NÃO canônico). Duas camadas, cada uma numa sessão LMMS própria.

VENTO (cama, sem posição, estéreo, laço de 24 s): o mundo é grande e aberto — ar sempre presente, discreto.
  - "Ar": ruído contínuo, passa-baixa 650 Hz (o corpo do vento), nível fixo e baixo.
  - "Rajada": ruído em banda 400–2 500 Hz com AUTOMAÇÃO de volume desenhada (curva de 24 s, termina onde começa).
  - "Folhas": grãos agudos (3–8 kHz) só perto dos picos das rajadas — as folhas mexem quando o vento passa.
LAGO (posicional, mono, laço de 16 s): água parada que encosta na margem — nada de cachoeira.
  - "Marulho": ruído grave (passa-baixa 500 Hz) com ondas de período IRREGULAR (2,6–3,4 s) por automação.
  - "Respingos": grãos de ruído médios (0,8–2,5 kHz) na crista de cada onda.
  - "Gotas": 4 "plinks" esparsos (Kicker tonal 1,8 → 0,9 kHz, 60 ms).
Tudo síntese do LMMS (ruído e tom), sem amostras. O laço sai de um render mais longo com a sobra fundida no início
(tools/audio/render_ambiente.py).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402

BPM = 120                # 1 compasso 4/4 = 2 s; 1 tick ≈ 10,4 ms
BAR = 192
SEC = BAR // 2           # ticks por segundo
OVERLAP = 2              # segundos a mais para o crossfade do laço


def vento():
    n = 24 + OVERLAP
    ar = [L.Note(0, n * SEC, 60, 100)]
    raj = [L.Note(0, n * SEC, 60, 100)]
    # curva das rajadas (segundo, volume %): a de 24 s repete a de 0 s (laço); a sobra repete o começo
    gust = [(0, 30), (2.5, 52), (4.0, 38), (7.0, 78), (8.5, 62), (10.5, 34), (13.0, 30), (15.5, 66), (17.0, 84),
            (18.5, 48), (21.0, 36), (24.0, 30), (26.5, 52)]
    auto = [(round(t * SEC), v) for t, v in gust]
    folhas = []
    for peak, k in ((7.0, 9), (8.5, 6), (15.5, 7), (17.0, 10)):         # grãos em volta dos picos
        for i in range(k):
            t = peak - 0.6 + i * (1.2 / k) + (0.05 if i % 2 else 0)
            folhas.append(L.Note(round(t * SEC), 3, 60, 40 + (i * 37) % 45))
    for f in list(folhas):                                              # sobra do laço: mesmo desenho
        if f.pos < OVERLAP * SEC:
            folhas.append(L.Note(f.pos + 24 * SEC, f.len, f.key, f.vel))
    return L.Song(
        title="Valumo — ambiente: vento", bpm=BPM, num=4, den=4,
        tracks=[L.Track("Ar", L.noise_osc(), ar, vol=45, fx=[L.fx_eq(hp=60, lp=650)]),
                L.Track("Rajada", L.noise_osc(), raj, vol=100, fx=[L.fx_eq(hp=400, lp=2500, peaks=[(1100, 2.0, 0.6)])],
                        vol_automation=auto),
                L.Track("Folhas", L.kicker(noise=1.0, decay_ms=40, gain=3.0), folhas, vol=70,
                        fx=[L.fx_eq(hp=3000, lp=8000, peaks=[(5000, 2.0, 0.6)])])],
        tail_bars=0, notes_md=__doc__)


def lago():
    n = 16 + OVERLAP
    # cristas das ondas (segundos): período irregular 2,6–3,4 s; o padrão de 16 s repete
    crests = [1.2, 4.1, 6.8, 10.1, 12.9, 15.6]
    auto = []
    for c in crests + [c + 16 for c in crests if c + 16 < n + 1]:
        auto += [(round((c - 1.1) * SEC), 25), (round(c * SEC), 70), (round((c + 0.9) * SEC), 35)]
    auto = sorted(dict(auto).items())
    splash, drops = [], []
    for c in crests:
        for i, (dt, v) in enumerate(((0.0, 70), (0.06, 52), (0.15, 40), (0.27, 26))):
            splash.append(L.Note(round((c + dt) * SEC), 3, 60, v - 6 * (int(c) % 3)))
    for t, key in ((2.7, "E6"), (8.9, "B5"), (11.4, "F#6"), (14.3, "C#6")):
        drops.append(L.Note(round(t * SEC), 6, L.midi(key), 46))
    for lst in (splash, drops):
        for x in list(lst):
            if x.pos < OVERLAP * SEC:
                lst.append(L.Note(x.pos + 16 * SEC, x.len, x.key, x.vel))
    return L.Song(
        title="Valumo — ambiente: lago", bpm=BPM, num=4, den=4,
        tracks=[L.Track("Marulho", L.noise_osc(), [L.Note(0, n * SEC, 60, 100)], vol=100,
                        fx=[L.fx_eq(hp=70, lp=500)], vol_automation=auto),
                L.Track("Respingos", L.kicker(noise=1.0, decay_ms=40, gain=3.5), splash, vol=90,
                        fx=[L.fx_eq(hp=800, lp=2500, peaks=[(1400, 2.0, 0.6)])]),
                L.Track("Gotas", L.kicker(start=1800, end=900, decay_ms=60, gain=0.5, slope=0.1, follow_note=True), drops,
                        vol=60, fx=[L.fx_eq(hp=500)])],
        tail_bars=0, notes_md=__doc__)


if __name__ == "__main__":
    out = Path(__file__).parent
    for name, fn, loop_s, stereo in (("vento", vento, 24, True), ("lago", lago, 16, False)):
        s = fn()
        (out / f"{name}.mmp").write_text(s.mmp(), encoding="utf-8")
        (out / f"{name}.laco.json").write_text(json.dumps({"bpm": BPM, "laco_s": loop_s, "sobra_s": OVERLAP,
                                                           "estereo": stereo}, ensure_ascii=False))
        print(f"{name}: {sum(len(t.notes) for t in s.tracks)} notas, laço {loop_s} s")
