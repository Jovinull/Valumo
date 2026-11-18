"""Interface — família de efeitos da rodada 09 (estudo; NÃO canônico).

Eventos reais (CreatorController.action e GameLogic.action): selecionar uma opção, confirmar (Concluir,
Começar, Restaurar confirmado), cancelar (Esc, fechar diálogo), abrir um painel/diálogo e salvar a aparência.

A interface do Valumo é madeira, pergaminho e dourado (UiKit). Duas hipóteses:
  A "madeira e papel": bloco de madeira + kalimba (madeira afinada, dedilhada) e grãos de "papel" (ruído em
     banda média com envelope de inchaço) — tátil, quente, discreto; dourado só no salvar (sininho).
  B "vidro e dourado": celesta e caixa de música — brilho mágico em tudo; mais "fantasia", mais cansativo
     em repetição (o selecionar toca dezenas de vezes por minuto no criador).
Regras comuns: ataque imediato (≤ 10 ms), curto (selecionar ≤ 180 ms), confirmar sobe (quinta justa),
cancelar desce (quarta), salvar é o único arpejo completo. Tudo na escala de Ré maior (mesma tônica da
música da vila: os sons não brigam com ela).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402

BPM = 240
BAR = 192
WOOD_HI, WOOD_LO = 76, 77          # GM percussão: bloco de madeira agudo / grave

T = {k: [] for k in ["Bloco de madeira", "Kalimba", "Sininho", "Papel", "Toque grave", "Celesta", "Caixa de música"]}
fatias = []
slot = 0


def put(sid, group, max_ms, events):
    """events: (faixa, tick relativo, nota ou número MIDI, intensidade, duração em ticks)."""
    global slot
    t0 = slot * BAR
    for tr, tk, n, v, ln in events:
        T[tr].append(L.Note(t0 + tk, ln, n if isinstance(n, int) else L.midi(n), v))
    fatias.append({"id": sid, "grupo": group, "tick": t0, "max_ms": max_ms})
    slot += 1


def swell(peak_tick=14, length=30, top=72, step=2):
    """Grãos de papel em inchaço: sobem até peak_tick e descem — o 'fru' de abrir um pergaminho."""
    out = []
    for tk in range(0, length, step):
        x = tk / peak_tick if tk <= peak_tick else max(0.0, 1 - (tk - peak_tick) / (length - peak_tick))
        out.append(("Papel", tk, 60, max(6, int(top * (0.25 + 0.75 * x))), 4))   # Kicker: nota ≥ decaimento
    return out


# ------------------------------------------------------------------ A: madeira e papel
for i, (k, v) in enumerate([("D6", 70), ("E6", 64), ("F#6", 74)]):
    put(f"a_selecionar_{i + 1}", "a_selecionar", 180,
        [("Bloco de madeira", 0, WOOD_HI, v, 2), ("Kalimba", 0, k, 34, 3)])
put("a_confirmar_1", "a_confirmar", 450,
    [("Bloco de madeira", 0, WOOD_LO, 58, 2), ("Kalimba", 0, "D5", 82, 4), ("Kalimba", 12, "A5", 92, 6)])
put("a_cancelar_1", "a_cancelar", 380,
    [("Bloco de madeira", 0, WOOD_LO, 46, 2), ("Kalimba", 0, "A5", 66, 4), ("Kalimba", 12, "E5", 56, 6)])
put("a_abrir_1", "a_abrir", 360, swell() + [("Toque grave", 4, 60, 46, 12)])
put("a_salvar_1", "a_salvar", 950,
    [("Kalimba", 0, "D5", 74, 4), ("Kalimba", 8, "F#5", 80, 4), ("Kalimba", 16, "A5", 86, 4),
     ("Kalimba", 24, "D6", 92, 8), ("Sininho", 24, "D7", 40, 8), ("Bloco de madeira", 0, WOOD_LO, 40, 2)])

# ------------------------------------------------------------------ B: vidro e dourado
for i, (k, v) in enumerate([("A6", 52), ("B6", 48), ("F#6", 54)]):
    put(f"b_selecionar_{i + 1}", "b_selecionar", 180, [("Celesta", 0, k, v, 3)])
put("b_confirmar_1", "b_confirmar", 450,
    [("Celesta", 0, "D6", 70, 4), ("Celesta", 12, "F#6", 78, 6), ("Caixa de música", 12, "A6", 50, 6)])
put("b_cancelar_1", "b_cancelar", 380, [("Celesta", 0, "F#6", 62, 4), ("Celesta", 12, "D6", 54, 6)])
put("b_abrir_1", "b_abrir", 360, swell(top=48) + [("Caixa de música", 2, "A5", 52, 6)])
put("b_salvar_1", "b_salvar", 950,
    [("Caixa de música", 0, "D6", 70, 4), ("Caixa de música", 10, "A6", 76, 4), ("Caixa de música", 20, "D7", 80, 8),
     ("Sininho", 20, "D7", 44, 8)])

TR = {
    "Bloco de madeira": (L.sf2(0, bank=128), [L.fx_eq(hp=300, peaks=[(1200, 2.0, 0.6)])], 100),
    "Kalimba": (L.sf2(108, gain=2.0), [L.fx_eq(hp=200, peaks=[(2400, 1.5, 0.6)])], 100),
    "Sininho": (L.sf2(112, gain=2.0), [L.fx_eq(hp=1500)], 100),
    "Papel": (L.kicker(noise=1.0, decay_ms=30, gain=5.0), [L.fx_eq(hp=1200, lp=6000, peaks=[(2600, 3.0, 0.7)])], 200),
    "Toque grave": (L.kicker(start=120, end=70, decay_ms=45, gain=0.8, slope=0.08), [L.fx_eq(lp=600)], 100),
    "Celesta": (L.sf2(8, gain=2.5), [L.fx_eq(hp=500)], 100),
    "Caixa de música": (L.sf2(10, gain=2.0), [L.fx_eq(hp=400)], 100),
}
song = L.Song(
    title="Valumo — interface (rodada 09)", bpm=BPM, num=4, den=4,
    tracks=[L.Track(n, TR[n][0], T[n], vol=TR[n][2], fx=TR[n][1], channel=9 if n == "Bloco de madeira" else i,
                    program=0) for i, n in enumerate(TR)],
    buses=[L.Bus("Sala", [L.fx_reverb(size=0.55, color=9000, wet=1.0)], volume=0.5)],
    tail_bars=1, notes_md=__doc__,
)
for t in song.tracks:          # sala curta e discreta (o painel é um objeto próximo, não um salão)
    t.sends = {"Sala": 0.12}

if __name__ == "__main__":
    out = Path(__file__).parent
    (out / "interface.mmp").write_text(song.mmp(), encoding="utf-8")
    (out / "interface.fatias.json").write_text(json.dumps({"bpm": BPM, "compasso_ticks": BAR, "fatias": fatias},
                                                          indent=1, ensure_ascii=False))
    print(f"interface: {len(fatias)} sons em {len(song.tracks)} faixas")
