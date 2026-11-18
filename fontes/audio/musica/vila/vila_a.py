"""Vila — versão A: "Exploração acolhedora" (estudo da rodada 09; NÃO canônica).

Hipótese artística: a vila é um lugar seguro de onde se parte. Calor de violão dedilhado e madeiras
(flauta e clarinete) em 6/8, que balança como um passo tranquilo, com um único momento de "maravilha":
o acorde de Mi maior (II maior, empréstimo lídio) no compasso 14.

Decisões:
- Compasso 6/8, semínima pontuada ≈ 66,7 (LMMS conta semínimas: 100 — ver render_musica: corpo em múltiplo de 256 quadros). Balanço de caminhada, não marcha.
- Ré maior. Harmonia funcional simples nas seções A (I–IV/I–I–V/3–vi–IV–ii7–V), seção B sobe ao IV e
  passa pelo II maior (Mi), a cor "mágica" da peça, depois volta pelo V.
- Motivo do Valumo (comum às versões A e B, tratado de modo diferente em cada uma):
  5̣ → 3 (sexta maior ascendente), 2, 1, 2 — "olhar para longe e dar um passo".
  Na A ele é a cabeça da melodia da flauta; na B aparece aumentado na celesta.
- Forma: introdução de 4 compassos (violão + motivo no glockenspiel) | A (8) flauta | B (8) clarinete com
  contracanto da flauta e entrada do baixo | A' (8) flauta com cordas e glockenspiel, cadência no I.
  O laço do jogo repete A–B–A' (24 compassos); a introdução toca só na primeira vez.
- Textura cresce por seção (violão → +baixo/clarinete → +cordas/glock/chocalho) e volta ao mínimo no laço,
  para a repetição não cansar: o ponto mais denso fica no fim do laço, não no começo.
- Dinâmica por intensidade das notas (acento na 1ª e 4ª colcheias) e uma automação de volume nas cordas.
- Instrumentos: banco FluidR3 GM (licença MIT) no sf2player do LMMS; mixer com EQ por canal e um retorno
  de reverberação (ReverbSC) compartilhado.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402

U = 24            # unidade = colcheia
BAR = 144         # 6/8
INTRO = 4

# ------------------------------------------------------------------ harmonia (um acorde por compasso)
# vozes do violão, do grave ao agudo (4 notas, abertas, região confortável do violão)
V = {
    "D": "D3 A3 D4 F#4", "G/D": "D3 B3 D4 G4", "A/C#": "C#3 A3 E4 A4", "Bm": "B2 F#3 B3 D4",
    "G": "G2 D3 G3 B3", "Em7": "E2 B2 G3 D4", "A": "A2 E3 A3 C#4", "Asus4": "A2 E3 A3 D4",
    "F#m": "F#2 C#3 F#3 A3", "E": "E2 B2 E3 G#3", "G/A": "A2 G3 B3 D4", "A7": "A2 E3 G3 C#4",
    "Em7/A7": None,  # compasso dividido (tratado à parte)
}
INTRO_H = ["D", "G/D", "D", "Asus4"]
A_H = ["D", "G/D", "D", "A/C#", "Bm", "G", "Em7", "Asus4"]
B_H = ["G", "A", "F#m", "Bm", "G", "E", "G/A", "A7"]
A2_H = ["D", "G/D", "D", "A/C#", "Bm", "G", "Em7/A7", "D"]
HARM = INTRO_H + A_H + B_H + A2_H

# dedilhado: índice da voz por colcheia. A: baixo–quinta–terça–topo–terça–quinta (onda);
# B: baixo–terça–quinta–topo–quinta–terça (mais "aberto", sente-se a mudança sem mudar o tempo)
PAT_A = [0, 1, 2, 3, 2, 1]
PAT_B = [0, 2, 1, 3, 1, 2]


def guitar_bar(chord: str, pat: list[int]) -> str:
    if chord == "Em7/A7":   # meio compasso cada
        e, a = V["Em7"].split(), V["A7"].split()
        return f"{e[0]}:1 {e[2]} {e[3]} {a[0]} {a[2]} {a[3]}"
    v = V[chord].split()
    # cada nota soa por 2 colcheias (deixa as cordas ressoarem, como dedos que não abafam)
    return " ".join(f"{v[i]}:1" for i in pat)


guitar_bars = [guitar_bar(h, PAT_A) for h in INTRO_H + A_H] + [guitar_bar(h, PAT_B) for h in B_H] + \
              [guitar_bar(h, PAT_A) for h in A2_H]
guitar = L.parse_bars(guitar_bars, U, BAR, vel=78)
for n in guitar:                       # ressonância: dura duas colcheias
    n.len = 2 * U
# o último acorde da introdução abre (respira) antes da entrada da flauta: baixo longo
guitar = L.humanize(guitar, BAR, accents=[10, -6, -2, 4, -6, -4], seed=1, timing=2)

# ------------------------------------------------------------------ flauta (melodia)
# anacruse A4 no fim de cada seção que leva à melodia
flute_intro = ["~", "~", "~", "r:5 A4:1@72"]
flute_A = [
    "F#5:3 E5:1 D5 E5",        # motivo: 5̣→3, 2, 1, 2
    "B4:2 D5:1 G5:3",          # sobe ao 4º grau sobre Sol: abre a frase
    "F#5:2 E5:1 D5:2 A4:1",
    "E5:5 A4:1",               # semicadência, respira
    "F#5:3 G5:1 F#5 E5",
    "D5:2 E5:1 B5:3",          # sexta ascendente de novo (Ré → Si): o motivo ecoa ampliado
    "A5:2 G5:1 F#5:2 E5:1",
    "E5:6",
]
flute_B = [                    # contracanto discreto, notas longas acima do clarinete
    "r:6", "E5:6@58", "A5:6@60", "F#5:6@58",
    "G5:3@60 F#5:3@56", "E5:3@60 G#5:3@64", "F#5:6@58", "E5:5@56 A4:1@70",
]
flute_A2 = [
    "F#5:3 E5:1 D5 E5",
    "B4:2 D5:1 G5:3",
    "F#5:2 E5:1 D5:2 A4:1",
    "E5:3 F#5:1 E5:1 A4:1",    # variação: não repete a semicadência longa
    "F#5:3 G5:1 F#5 E5",
    "D5:2 E5:1 B5:3",
    "A5:2 G5:1 E5:2 C#5:1",    # sobre Em7 → A7: conduz à tônica
    "D5:5 A4:1@70",            # tônica + anacruse que devolve ao começo do laço
]
flute = L.parse_bars(flute_intro + flute_A + flute_B + flute_A2, U, BAR, vel=84)
flute = L.humanize(flute, BAR, accents=[8, -4, -2, 4, -4, -2], seed=3, spread=4, timing=2)

# ------------------------------------------------------------------ clarinete (melodia da seção B)
clar_B = [
    "B4:3 A4:1 G4 A4",
    "C#5:3 E5:3",
    "A4:2 C#5:1 F#5:3",
    "E5:2 D5:1 B4:3",
    "D5:3 E5:1 D5 B4",
    "B4:2 G#4:1 B4:1 E5:2",    # Sol# — a nota "mágica" do II maior
    "D5:3 C#5:1 B4:1 A4:1",
    "A4:3 G4:1 E4:1 C#5:1",
]
clar = L.parse_bars(clar_B, U, BAR, start_bar=INTRO + 8, vel=80)
clar = L.humanize(clar, BAR, accents=[8, -4, -2, 4, -4, -2], seed=5, spread=4, timing=2)

# ------------------------------------------------------------------ baixo (entra na B; pulso em semínimas pontuadas)
ROOTS = {"G": "G2", "A": "A2", "F#m": "F#2", "Bm": "B2", "E": "E2", "G/A": "A2", "A7": "A2", "D": "D2",
         "G/D": "D2", "A/C#": "C#2", "Em7": "E2"}
bass_B = [f"{ROOTS[h]}:3 {ROOTS[h]}:3" for h in B_H]
bass_B[-1] = "A2:3 C#3:3"                       # condução para o Ré
bass_A2 = [f"{ROOTS[h]}:3 {ROOTS[h]}:3" if h != "Em7/A7" else "E2:3 A2:3" for h in A2_H]
bass_A2[-1] = "D2:6"
bass = L.parse_bars(bass_B + bass_A2, U, BAR, start_bar=INTRO + 8, vel=70)
bass = L.humanize(bass, BAR, accents=[8, 0, 0, 0, 0, 0], seed=7, spread=3)

# ------------------------------------------------------------------ cordas (só A'; tapete baixo)
PADS = {"D": "A3+D4+F#4", "G/D": "B3+D4+G4", "A/C#": "A3+C#4+E4", "Bm": "B3+D4+F#4", "G": "B3+D4+G4",
        "Em7/A7": None}
strings_A2 = []
for h in A2_H:
    if h == "Em7/A7":
        strings_A2.append("G3+B3+D4:3@54 G3+C#4+E4:3@56")
    else:
        strings_A2.append(f"{PADS[h]}:6@52")
strings = L.parse_bars(strings_A2, U, BAR, start_bar=INTRO + 16, vel=52)
strings_auto = [(0, 0), ((INTRO + 16) * BAR, 0), ((INTRO + 16) * BAR + 1, 55), ((INTRO + 18) * BAR, 85),
                ((INTRO + 22) * BAR, 95), ((INTRO + 24) * BAR, 70)]

# ------------------------------------------------------------------ glockenspiel (sinal do motivo)
glock_intro = ["~", "~", "A5:3@50 F#6:3@56", "E6:1@50 D6:1@46 E6:4@52"]
glock_A2 = ["F#6:6@40", "~", "~", "~", "F#6:3@38 r:3", "B6:6@40", "~", "D6:6@44"]
glock = L.parse_bars(glock_intro, U, BAR, vel=50) + L.parse_bars(glock_A2, U, BAR, start_bar=INTRO + 16, vel=40)

# ------------------------------------------------------------------ chocalho (só A'; colcheias leves)
shk = L.parse_bars(["F#2:1@44 F#2:1@26 F#2:1@30 F#2:1@40 F#2:1@24 F#2:1@30"] * 8, U, BAR, start_bar=INTRO + 16)
for n in shk:
    n.key = 82          # GM: shaker
shk = L.humanize(shk, BAR, accents=[0, 0, 0, 0, 0, 0], seed=9, spread=6)

REV = {"Reverberação": 0}
song = L.Song(
    title="Valumo — Vila (A: exploração acolhedora)", bpm=100, num=6, den=8,
    tracks=[
        L.Track("Violão", L.sf2(24), guitar, vol=92, pan=-12, channel=0, program=24,
                fx=[L.fx_eq(hp=85, peaks=[(250, -3.0, 0.8), (3200, 2.0, 0.6)])], sends={"Reverberação": 0.22}),
        L.Track("Flauta", L.sf2(73), flute, vol=88, pan=8, channel=1, program=73,
                fx=[L.fx_eq(hp=220, peaks=[(1800, 1.0, 0.5)], high_shelf=(9000, -2.0))], sends={"Reverberação": 0.32}),
        L.Track("Clarinete", L.sf2(71), clar, vol=86, pan=-6, channel=2, program=71,
                fx=[L.fx_eq(hp=140, peaks=[(400, -2.0, 0.7)])], sends={"Reverberação": 0.30}),
        L.Track("Baixo", L.sf2(32), bass, vol=78, pan=0, channel=3, program=32,
                fx=[L.fx_eq(hp=38, lp=4200, peaks=[(180, -1.5, 0.6)])], sends={"Reverberação": 0.06}),
        L.Track("Cordas", L.sf2(49), strings, vol=60, pan=4, channel=4, program=49,
                fx=[L.fx_eq(hp=180, high_shelf=(7000, -4.0))], sends={"Reverberação": 0.40},
                vol_automation=strings_auto),
        L.Track("Glockenspiel", L.sf2(9, gain=4.0), glock, vol=100, pan=18, channel=5, program=9,
                fx=[L.fx_eq(hp=900, high_shelf=(10000, -3.0))], sends={"Reverberação": 0.45}),
        L.Track("Chocalho", L.sf2(0, bank=128, gain=5.0), shk, vol=100, pan=24, channel=9,
                fx=[L.fx_eq(hp=2500, high_shelf=(11000, -4.0))], sends={"Reverberação": 0.12}),
    ],
    buses=[L.Bus("Reverberação", [L.fx_reverb(size=0.80, color=8500, wet=1.0)], volume=0.9)],
    master_fx=[L.fx_eq(hp=28, peaks=[(220, -1.5, 0.7)], high_shelf=(6000, 2.5))],
    loop=(INTRO * BAR, (INTRO + 24) * BAR),
    notes_md=__doc__,
)

if __name__ == "__main__":
    out = Path(__file__).with_name("vila_a")
    out.mkdir(exist_ok=True)
    (out / "vila_a.mmp").write_text(song.mmp(), encoding="utf-8")
    (out / "vila_a.mid").write_bytes(song.mid())
    secs = (INTRO + 24) * BAR / 48 * 60 / song.bpm
    print(f"vila_a: {len(song.tracks)} faixas, {sum(len(t.notes) for t in song.tracks)} notas, "
          f"{INTRO + 24} compassos ≈ {secs:.1f} s; laço {INTRO}–{INTRO + 24}")
