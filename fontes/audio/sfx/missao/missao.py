"""Missão — efeitos da rodada 10 ("As Luzes do Posto"; estudo, aprovação do proprietário pendente).

Eventos reais (GameAudio.questEvent, a partir dos avisos do QuestSystem): missão aceita, objetivo cumprido (trilha
limpa), marco de luz aceso (o único som "do mundo": toca no lugar do marco, no momento do E) e recompensa recebida.
Volume e frequência: raros (4 a 6 vezes numa aventura inteira) — podem ser mais longos e mais "musicais" que a interface,
sem virar fanfarra (o protótipo é calmo; a música da vila continua tocando por baixo).

Duas hipóteses, na escala de Ré maior (a da música da vila e da interface):
  A "madeira e luz": continua a família A da interface (aprovada como recomendação na 09.4): bloco de madeira e
     kalimba para aceitar e cumprir, sininho só onde há LUZ (o marco) e moedas (a recompensa). O marco junta um inchaço
     de grãos (o "fôlego" da chama pegando) a um arpejo de sininho que sobe uma oitava.
  B "vidro": celesta e caixa de música em tudo — mais mágico, mas some a ligação com a interface de madeira.
Regras: ataque imediato (≤ 10 ms); aceitar sobe uma terça + quinta (abre), objetivo é a quinta justa resolvendo na
tônica (fecha um passo), marco é o mais longo (≈ 1,4 s), recompensa é tilintar curto em notas repetidas (moeda).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402
import sintese as S  # noqa: E402  (síntese própria: nenhuma amostra de terceiros)

BPM = 240
BAR = 192 * 2                     # dois compassos por som (o marco passa de 1 s)
WOOD_HI, WOOD_LO = L.midi("A6"), L.midi("E6")

T = {k: [] for k in ["Bloco de madeira", "Kalimba", "Sininho", "Fôlego", "Toque grave", "Celesta", "Caixa de música"]}
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


def breath(start=0, peak=40, length=80, top=46, step=3):
    """Grãos em inchaço lento: a chama pegando dentro da lanterna (mais longo e mais baixo que o 'papel' da interface)."""
    out = []
    for tk in range(0, length, step):
        x = tk / peak if tk <= peak else max(0.0, 1 - (tk - peak) / (length - peak))
        out.append(("Fôlego", start + tk, 60, max(4, int(top * (0.15 + 0.85 * x))), 4))
    return out


# ------------------------------------------------------------------ A: madeira e luz
put("a_aceita", "a_aceita", 700,
    [("Bloco de madeira", 0, WOOD_LO, 52, 2), ("Kalimba", 0, "D5", 80, 4), ("Kalimba", 10, "F#5", 84, 4),
     ("Kalimba", 20, "A5", 92, 10)])
put("a_objetivo", "a_objetivo", 800,
    [("Bloco de madeira", 0, WOOD_HI, 46, 2), ("Kalimba", 0, "A5", 82, 4), ("Kalimba", 12, "D6", 94, 12),
     ("Sininho", 12, "D7", 30, 10)])
put("a_sinal", "a_sinal", 1500,
    breath(0) + [("Toque grave", 30, 60, 40, 16), ("Sininho", 34, "D6", 48, 8), ("Sininho", 42, "F#6", 54, 8),
                 ("Sininho", 50, "A6", 60, 8), ("Sininho", 58, "D7", 66, 24), ("Kalimba", 58, "D5", 58, 16)])
put("a_recompensa", "a_recompensa", 900,
    [("Bloco de madeira", 0, WOOD_HI, 40, 2), ("Sininho", 0, "A6", 50, 4), ("Sininho", 6, "A6", 44, 4),
     ("Sininho", 12, "D7", 58, 12), ("Kalimba", 12, "F#5", 60, 8)])

# ------------------------------------------------------------------ B: vidro
put("b_aceita", "b_aceita", 700, [("Celesta", 0, "D6", 64, 4), ("Celesta", 10, "F#6", 70, 4), ("Celesta", 20, "A6", 76, 10)])
put("b_objetivo", "b_objetivo", 800, [("Celesta", 0, "A6", 66, 4), ("Caixa de música", 12, "D7", 70, 12)])
put("b_sinal", "b_sinal", 1500,
    breath(0, top=36) + [("Caixa de música", 34, "D6", 60, 8), ("Caixa de música", 42, "F#6", 64, 8),
                         ("Caixa de música", 50, "A6", 68, 8), ("Celesta", 58, "D7", 72, 24)])
put("b_recompensa", "b_recompensa", 900,
    [("Caixa de música", 0, "A6", 60, 4), ("Caixa de música", 6, "A6", 54, 4), ("Celesta", 12, "D7", 70, 12)])

TR = {
    "Bloco de madeira": (L.kicker(start=2300, end=1500, decay_ms=24, click=0.25, noise=0.02, gain=1.4, slope=0.1, follow_note=True),
                         [L.fx_eq(hp=300, peaks=[(1200, 2.0, 0.6)])], 100, None),
    "Kalimba": (*S.kalimba()[:1], [L.fx_eq(hp=200, peaks=[(2400, 1.5, 0.6)])], 100, S.kalimba()[1]),
    "Sininho": (*S.sininho()[:1], [L.fx_eq(hp=1500)], 100, S.sininho()[1]),
    "Fôlego": (L.kicker(noise=1.0, decay_ms=30, gain=4.0), [L.fx_eq(hp=900, lp=4500, peaks=[(1800, 2.0, 0.7)])], 160, None),
    "Toque grave": (L.kicker(start=120, end=70, decay_ms=60, gain=0.8, slope=0.08), [L.fx_eq(lp=600)], 100, None),
    "Celesta": (*S.celesta()[:1], [L.fx_eq(hp=500)], 100, S.celesta()[1]),
    "Caixa de música": (*S.caixa_de_musica()[:1], [L.fx_eq(hp=400)], 100, S.caixa_de_musica()[1]),
}
song = L.Song(
    title="Valumo — missão (rodada 10)", bpm=BPM, num=4, den=4,
    tracks=[L.Track(n, TR[n][0], T[n], vol=TR[n][2], fx=TR[n][1], channel=9 if n == "Bloco de madeira" else i,
                    program=0, eldata=TR[n][3]) for i, n in enumerate(TR)],
    buses=[L.Bus("Sala", [L.fx_reverb(size=0.6, color=9000, wet=1.0)], volume=0.5)],
    tail_bars=2, notes_md=__doc__,
)
for t in song.tracks:          # um pouco mais de sala que a interface: o marco é um lugar, não um painel
    t.sends = {"Sala": 0.16}

if __name__ == "__main__":
    out = Path(__file__).parent
    (out / "missao.mmp").write_text(song.mmp(), encoding="utf-8")
    (out / "missao.fatias.json").write_text(json.dumps({"bpm": BPM, "compasso_ticks": BAR, "fatias": fatias},
                                                       indent=1, ensure_ascii=False))
    print(f"missão: {len(fatias)} sons em {len(song.tracks)} faixas")
