"""Passos do herói — família de efeitos da rodada 09 (estudo; NÃO canônico).

Evento real: o Player do jogo planta um pé a cada meia passada (fase da locomoção pela distância percorrida;
ver docs/architecture/audio.md). Superfícies que o relevo da vila classifica (World.surfaceClass):
grama/floresta → GRAMA, caminho → TERRA (areia e rocha ficam para depois).

Construção (síntese deliberada, sem amostras):
- camada PISADA: Kicker do LMMS (tom com queda de frequência) — o peso do pé. Herói pequeno (chibi): pé
  leve, sem estrondo; passa-baixa forte.
- camada MATERIAL: grãos curtos de ruído (Kicker com ruído = 1, ~13 ms cada) com EQ de banda — a grama
  "farfalha" em banda alta, a terra "range" em banda média. Os grãos são escritos um a um: o desenho do
  farfalhar/rangido está nos tempos e intensidades, não em aleatoriedade.

Duas propostas por superfície (hipóteses diferentes, não só volume):
  GRAMA A "macia":    pisada grave e abafada + farfalhar longo e esparso (cauda ~110 ms), banda 1,4–6 kHz
  GRAMA B "seca":     pisada mais leve + farfalhar curto e denso, mais agudo (banda 2,2–7,5 kHz) — capim seco
  TERRA A "batida":   pisada firme com clique + poucos grãos médios (0,7–3 kHz) — chão compacto
  TERRA B "cascalho": pisada média + muitos grãos 1–3,5 kHz espalhados — pedrisco
  AREIA (2ª parte):   pisada abafada (passa-baixa 480 Hz) + "chff" de grãos densos e longos em 0,5–3,5 kHz
  PEDRA (2ª parte):   pisada dura e curta com clique (220 → 90 Hz) + um ou dois estalos agudos (2,5–9 kHz)
Variações: 4 por proposta (andar) + 4 (correr: pisada mais forte, grãos comprimidos no tempo). As variações
mudam a altura da pisada (nota → frequência inicial), o desenho dos grãos e a intensidade — a identidade do
evento fica igual.

Nota técnica: o Kicker do LMMS só soa enquanto a nota está ligada (sem release). Nota mais curta que o
decaimento = corte seco (clique). Pisada: 16 ticks (≈ 83 ms); grão: 4 ticks.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402

BPM = 240          # 1 tick = 60/(240·48) s ≈ 5,2 ms (resolução dos grãos)
BAR = 192          # cada som num compasso (1 s)
TICK_MS = 60000 / (BPM * 48)

# grãos: (tick, intensidade) — desenhados à mão por proposta e variação
GRAINS = {
    "grama_a": [
        [(0, 100), (1, 78), (3, 70), (6, 54), (10, 40), (15, 26), (21, 16)],
        [(0, 96), (2, 80), (4, 62), (7, 58), (11, 38), (16, 24)],
        [(0, 100), (1, 70), (4, 74), (8, 50), (12, 36), (18, 22), (22, 14)],
        [(0, 92), (2, 76), (5, 64), (9, 46), (14, 30), (20, 18)],
    ],
    "grama_b": [
        [(0, 100), (1, 90), (2, 74), (3, 66), (5, 48), (7, 30)],
        [(0, 96), (1, 82), (2, 80), (4, 58), (6, 40), (8, 22)],
        [(0, 100), (1, 76), (3, 72), (4, 60), (6, 36)],
        [(0, 94), (1, 88), (2, 70), (3, 62), (5, 44), (8, 26)],
    ],
    "terra_a": [
        [(0, 100), (2, 66), (5, 44), (9, 24)],
        [(0, 96), (1, 70), (4, 50), (8, 28)],
        [(0, 100), (3, 60), (6, 40)],
        [(0, 92), (2, 72), (6, 46), (10, 22)],
    ],
    # rodada 09 (2ª parte): areia — grãos densos e longos, ataque macio (chff); pedra — quase nada de material
    "areia": [
        [(0, 54), (1, 70), (2, 82), (3, 80), (5, 70), (7, 58), (9, 46), (12, 34), (15, 22), (19, 12)],
        [(0, 50), (1, 66), (2, 80), (4, 78), (6, 64), (8, 52), (11, 40), (14, 26), (18, 14)],
        [(0, 58), (1, 74), (3, 84), (4, 76), (6, 66), (9, 50), (12, 36), (16, 22), (20, 12)],
        [(0, 52), (1, 68), (2, 78), (3, 82), (5, 68), (8, 54), (10, 42), (13, 28), (17, 16)],
    ],
    "pedra": [
        [(0, 100), (1, 38)],
        [(0, 96), (2, 30)],
        [(0, 100), (1, 44), (3, 18)],
        [(0, 92), (1, 34)],
    ],
    "terra_b": [
        [(0, 100), (1, 80), (2, 64), (4, 72), (5, 50), (7, 56), (9, 34), (12, 22)],
        [(0, 96), (1, 74), (3, 70), (4, 52), (6, 60), (8, 38), (11, 24)],
        [(0, 100), (2, 76), (3, 66), (5, 58), (6, 44), (9, 40), (13, 20)],
        [(0, 94), (1, 84), (2, 58), (4, 66), (7, 48), (8, 36), (12, 22)],
    ],
}
# pisada: nota (→ frequência inicial do Kicker) e intensidade por variação
THUMP_NOTES = ["A2", "G2", "B2", "G#2"]
PROPOSALS = {   # faixa de pisada, faixa de material, intensidade base da pisada, da camada material
    "grama_a": ("Pisada macia", "Grama A (macia)", 78, 70),
    "grama_b": ("Pisada leve", "Grama B (seca)", 60, 66),
    "terra_a": ("Pisada firme", "Terra A (batida)", 88, 80),   # refinada: +2 dB de rangido (era 92/64)
    "terra_b": ("Pisada leve", "Terra B (cascalho)", 74, 62),
    "areia": ("Pisada abafada", "Areia", 70, 74),
    "pedra": ("Pisada dura", "Pedra", 90, 60),
}

notes: dict[str, list[L.Note]] = {k: [] for k in
                                  ["Pisada macia", "Pisada leve", "Pisada firme", "Grama A (macia)",
                                   "Grama B (seca)", "Terra A (batida)", "Terra B (cascalho)", "Pisada abafada",
                                   "Pisada dura", "Areia", "Pedra"]}
fatias = []
slot = 0
for prop, (thump_tr, mat_tr, tv, mv) in PROPOSALS.items():
    for gait in ("andar", "correr"):
        for v in range(4):
            t0 = slot * BAR
            run = gait == "correr"
            notes[thump_tr].append(L.Note(t0, 16, L.midi(THUMP_NOTES[v]), min(127, tv + (18 if run else 0) - 3 * v)))
            for tk, vel in GRAINS[prop][v]:
                tk = round(tk * 0.7) if run else tk          # correr: o pé entra e sai mais rápido
                notes[mat_tr].append(L.Note(t0 + tk, 4, 60, min(127, int(vel * mv / 100) + (10 if run else 0))))
            fatias.append({"id": f"{prop}_{gait}_{v + 1}", "grupo": f"{prop}_{gait}", "tick": t0,
                           "max_ms": 140 if run else 170})   # < intervalo entre passos (178 ms correndo, 227 andando)
            slot += 1

TR = {
    "Pisada macia": (L.kicker(start=110, end=48, decay_ms=55, noise=0.0, click=0.03, gain=1.2, slope=0.08, follow_note=True),
                     [L.fx_eq(lp=700, peaks=[(160, 2.0, 0.6)])]),
    "Pisada leve": (L.kicker(start=130, end=60, decay_ms=40, noise=0.0, click=0.05, gain=1.0, slope=0.08, follow_note=True),
                    [L.fx_eq(hp=60, lp=1100)]),
    "Pisada firme": (L.kicker(start=140, end=58, decay_ms=60, noise=0.05, click=0.18, gain=1.3, slope=0.06, follow_note=True),
                     [L.fx_eq(hp=45, lp=2200, peaks=[(110, 2.0, 0.5)])]),
    "Grama A (macia)": (L.kicker(noise=1.0, decay_ms=60, gain=5.0),
                        [L.fx_eq(hp=1400, lp=6000, peaks=[(3200, 3.0, 0.8)])]),
    "Grama B (seca)": (L.kicker(noise=1.0, decay_ms=40, gain=5.0),
                       [L.fx_eq(hp=2200, lp=7500, peaks=[(4500, 3.0, 0.6)])]),
    "Terra A (batida)": (L.kicker(noise=1.0, decay_ms=60, gain=5.0),
                         [L.fx_eq(hp=700, lp=3000, peaks=[(1400, 4.0, 0.6)])]),
    "Terra B (cascalho)": (L.kicker(noise=1.0, decay_ms=40, gain=5.0),
                           [L.fx_eq(hp=1000, lp=3500, peaks=[(2000, 4.0, 0.6)])]),
    "Pisada abafada": (L.kicker(start=100, end=42, decay_ms=50, noise=0.0, click=0.0, gain=1.1, slope=0.08, follow_note=True),
                       [L.fx_eq(lp=480)]),
    "Pisada dura": (L.kicker(start=220, end=90, decay_ms=35, noise=0.0, click=0.40, gain=1.0, slope=0.05, follow_note=True),
                    [L.fx_eq(hp=70, lp=3200, peaks=[(1800, 3.0, 0.6)])]),
    "Areia": (L.kicker(noise=1.0, decay_ms=60, gain=5.0), [L.fx_eq(hp=500, lp=3500, peaks=[(1400, 2.0, 0.7)])]),
    "Pedra": (L.kicker(noise=1.0, decay_ms=20, gain=5.0), [L.fx_eq(hp=2500, lp=9000, peaks=[(4200, 4.0, 0.6)])]),
}
# volume das faixas: material à frente, pisada como apoio (medido: com 100/100 a pisada tinha 95% da energia)
VOL = {"Pisada macia": 100, "Pisada leve": 100, "Pisada firme": 80, "Grama A (macia)": 200, "Grama B (seca)": 200,
       "Terra A (batida)": 200, "Terra B (cascalho)": 200, "Pisada abafada": 100, "Pisada dura": 90, "Areia": 200,
       "Pedra": 120}
song = L.Song(
    title="Valumo — passos (rodada 09)", bpm=BPM, num=4, den=4,
    tracks=[L.Track(n, TR[n][0], notes[n], vol=VOL[n], fx=TR[n][1], channel=i) for i, n in enumerate(TR)],
    tail_bars=1, notes_md=__doc__,
)

if __name__ == "__main__":
    out = Path(__file__).parent
    (out / "passos.mmp").write_text(song.mmp(), encoding="utf-8")
    (out / "passos.fatias.json").write_text(json.dumps({"bpm": BPM, "compasso_ticks": BAR, "fatias": fatias},
                                                       indent=1, ensure_ascii=False))
    print(f"passos: {len(fatias)} sons em {len(song.tracks)} faixas")
