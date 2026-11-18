"""Vila — versão B: "Fantasia misteriosa" (estudo da rodada 09.4; NÃO canônica).

Hipótese artística: a vila fica na borda de um mundo grande e um pouco encantado. Em vez de "lar seguro"
(versão A), a música sugere curiosidade: o que há além da trilha? Mais espaço e silêncio, harmonia modal
sobre pedal, timbres de brilho (celesta, sino, cristal) e uma melodia que só aparece na seção central.

Decisões (cada uma diferente da versão A de propósito):
- Compasso 4/4 lento, semínima = 75 (A: 6/8 balançado ~67). 75 bpm também dá 800 quadros por tick a
  48 kHz: o corpo do laço fecha em múltiplo de 256 quadros (ver tools/audio/render_musica.py).
- Ré LÍDIO sobre pedal de Ré nas seções A: Ré(add9) ↔ Mi/Ré (o Sol# é a cor "mágica" permanente, não um
  acorde de passagem como em A). Seção B desce para Si menor (relativa), com Fá# maior como dominante
  "antiga" (Lá#) — o mistério da peça.
- Motivo do Valumo (5̣→3, 2, 1, 2) AUMENTADO na celesta (mínimas), espaçado por pausas — chamada distante,
  não cabeça de melodia.
- Textura: ostinato de harpa em colcheias (A) que vira semínimas na B para dar lugar à flauta de pã;
  violoncelo em pedal; cordas lentas; sino grave (vibrafone em Ré3) marcando as seções; brilhos de cristal; eco
  ESCRITO na celesta (repetições mais fracas na colcheia pontuada — o Delay do LMMS não funciona por XML).
- Forma: introdução 2 compassos | A 8 (celesta + harpa + pedal) | B 8 (flauta de pã, Si menor) |
  A'' 4 (motivo de volta, termina suspenso em Lá sus4 → laço). Laço = compassos 3–22.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "audio"))
import lmms as L  # noqa: E402

U = 24          # colcheia
BAR = 192       # 4/4
INTRO = 2

# ------------------------------------------------------------------ harpa: figura por acorde (8 colcheias)
HARP = {
    "Dadd9": "D3 A3 E4 F#4 A4 F#4 E4 A3",
    "E/D": "D3 B3 E4 G#4 B4 G#4 E4 B3",
    "Bm7": "B2 F#3 D4 A4 B4 A4 D4 F#3",
    "Gmaj7#11": "G2 D3 B3 F#4 C#5 F#4 B3 D3",
    "Em9": "E2 B2 G3 D4 F#4 D4 G3 B2",
    "Asus4": "A2 E3 A3 D4 E4 D4 A3 E3",
    "Gmaj7": "G2 D3 B3 F#4 B4 F#4 B3 D3",
}
HARP_Q = {   # seção B: semínimas (metade da densidade)
    "Bm": "B2 F#3 D4 F#4", "G": "G2 D3 B3 D4", "Em": "E2 B2 G3 B3", "F#sus4/F#": "F#2 C#3 B3 A#3",
    "Em7": "E2 B2 D3 G3", "Asus4/A": "A2 E3 D4 C#4",
}
INTRO_H = ["Dadd9", "Dadd9"]
A_H = ["Dadd9", "E/D", "Dadd9", "E/D", "Bm7", "Gmaj7#11", "Em9", "Asus4"]
B_H = ["Bm", "G", "Em", "F#sus4/F#", "Bm", "G", "Em7", "Asus4/A"]
A3_H = ["Dadd9", "E/D", "Gmaj7", "Asus4"]

harp_bars = [" ".join(f"{n}:1" for n in HARP[h].split()) for h in INTRO_H + A_H]
harp_bars += [" ".join(f"{n}:2" for n in HARP_Q[h].split()) for h in B_H]
harp_bars += [" ".join(f"{n}:1" for n in HARP[h].split()) for h in A3_H]
harp = L.parse_bars(harp_bars, U, BAR, vel=74)
for n in harp:
    n.len = max(n.len, 4 * U)        # a harpa ressoa (cordas soltas)
harp = L.humanize(harp, BAR, accents=[8, -6, 0, -4, 4, -6, 0, -4], seed=2, spread=5, timing=2)

# ------------------------------------------------------------------ celesta: motivo aumentado
cel_A = [
    "A5:4 F#6:4",              # 5̣ → 3 (sexta) em mínimas
    "E6:2 D6:2 E6:4",          # 2, 1, 2
    "r:4 A5:2 B5:2",
    "G#5:6 r:2",               # a nota lídia sozinha
    "F#6:4 E6:2 D6:2",
    "C#6:8",                   # #11 sobre Sol: o brilho mais "estranho" da peça
    "B5:4 F#5:4",
    "A5:8@60",
]
cel_B = ["~", "~", "~", "C#6:4@46 A#5:4@44", "~", "~", "~", "E6:4@44 C#6:4@40"]   # ecos da harmonia, distantes
cel_A3 = ["A5:4 F#6:4", "E6:2 D6:2 E6:4", "D6:6 C#6:2", "A5:8@56"]
celesta = L.parse_bars(["~", "~"] + cel_A + cel_B + cel_A3, U, BAR, vel=66)
celesta = L.humanize(celesta, BAR, accents=[4, 0, 0, 0, 2, 0, 0, 0], seed=4, spread=4)
# eco escrito na colcheia pontuada (36 ticks = 0,6 s), duas repetições, só nas notas de mínima ou mais
celesta = L.written_echo(celesta, delay=36, repeats=2, decay=0.42, min_len=4 * U, limit=(INTRO + 20) * BAR)

# ------------------------------------------------------------------ flauta de pã: melodia só na B (Si menor)
pan_B = [
    "F#5:3 E5:1 D5:2 F#5:2",
    "B5:6 A5:2",
    "G5:2 F#5:2 E5:2 B4:2",
    "C#5:4 A#4:4",             # Lá# sobre Fá#: o "antigo" da seção
    "D5:3 C#5:1 B4:2 F#5:2",
    "G5:4 F#5:2 E5:2",
    "D5:2 E5:2 G5:2 F#5:2",
    "E5:8",
]
pan = L.parse_bars(pan_B, U, BAR, start_bar=INTRO + 8, vel=80)
pan = L.humanize(pan, BAR, accents=[6, -4, 0, -4, 3, -4, 0, -4], seed=6, spread=4, timing=2)

# ------------------------------------------------------------------ violoncelo: pedal e fundamentais
cello_bars = ["D2:8", "D2:8"] + ["D2:8"] * 4 + ["B1:8", "G1:8", "E2:8", "A1:8"] + \
             ["B1:8", "G1:8", "E2:8", "F#2:8", "B1:8", "G1:8", "E2:8", "A1:8"] + ["D2:8", "D2:8", "G1:8", "A1:8"]
cello = L.parse_bars(cello_bars, U, BAR, vel=62)

# ------------------------------------------------------------------ cordas lentas: tapete (A acima; B mais grave)
PAD = {"Dadd9": "A3+E4+F#4", "E/D": "B3+E4+G#4", "Bm7": "A3+D4+F#4", "Gmaj7#11": "B3+C#4+F#4",
       "Em9": "B3+D4+F#4", "Asus4": "A3+D4+E4", "Bm": "F#3+B3+D4", "G": "G3+B3+D4", "Em": "G3+B3+E4",
       "F#sus4/F#": "F#3+B3+C#4", "Em7": "G3+B3+D4", "Asus4/A": "A3+D4+E4", "Gmaj7": "B3+D4+F#4"}
pad_bars = ["~", "~"] + [f"{PAD[h]}:8@46" for h in A_H]
pad_bars += [f"{PAD[h]}:8@{50 + 2 * i}" for i, h in enumerate(B_H)]   # cresce de leve ao longo da B
pad_bars[INTRO + 8 + 3] = "F#3+B3+C#4:4@56 F#3+A#3+C#4:4@58"          # Fá# sus4 → Fá#
pad_bars[INTRO + 8 + 7] = "A3+D4+E4:4@60 A3+C#4+E4:4@58"
pad_bars += [f"{PAD[h]}:8@44" for h in A3_H]
pad = L.parse_bars(pad_bars, U, BAR, vel=46)

# ------------------------------------------------------------------ sino grave (vibrafone, Ré3) e cristal (FX GM)
# (planejado como tigela tibetana e vidro do Mallets STK; o Mallets do LMMS 1.2.2 desafina e travou o render)
bowl = L.parse_bars(["D3:8@70", "~"] + ["D3:8@58"] + ["~"] * 7 + ["B2:8@60"] + ["~"] * 7 + ["D3:8@56", "~", "~", "~"],
                    U, BAR)
glass = L.parse_bars(["~", "~", "~", "~", "~", "r:6 E6:2@50", "~", "~", "~", "r:6 A6:2@46"] + ["~"] * 12, U, BAR)

song = L.Song(
    title="Valumo — Vila (B: fantasia misteriosa)", bpm=75, num=4, den=4,
    tracks=[
        L.Track("Harpa", L.sf2(46), harp, vol=84, pan=-16, channel=0, program=46,
                fx=[L.fx_eq(hp=70, peaks=[(300, -2.5, 0.8)])], sends={"Reverberação": 0.38}),
        L.Track("Celesta", L.sf2(8, gain=3.5), celesta, vol=90, pan=20, channel=1, program=8,
                fx=[L.fx_eq(hp=500)], sends={"Reverberação": 0.50}),
        L.Track("Flauta de pã", L.sf2(75), pan, vol=64, pan=4, channel=2, program=75,
                fx=[L.fx_eq(hp=250, peaks=[(1500, 1.0, 0.6)])], sends={"Reverberação": 0.45}),
        L.Track("Violoncelo", L.sf2(42), cello, vol=70, pan=-6, channel=3, program=42,
                fx=[L.fx_eq(hp=35, peaks=[(220, -2.0, 0.6)])], sends={"Reverberação": 0.25}),
        L.Track("Cordas lentas", L.sf2(49), pad, vol=64, pan=8, channel=4, program=49,
                fx=[L.fx_eq(hp=160, high_shelf=(7000, -3.0))], sends={"Reverberação": 0.45}),
        L.Track("Sino grave", L.sf2(11, gain=2.5), bowl, vol=100, pan=0, channel=5, program=11,
                fx=[L.fx_eq(hp=90, high_shelf=(5000, -4.0))], sends={"Reverberação": 0.60}),
        L.Track("Cristal", L.sf2(98, gain=2.0), glass, vol=100, pan=28, channel=6, program=98,
                fx=[L.fx_eq(hp=1200)], sends={"Reverberação": 0.65}),
    ],
    buses=[L.Bus("Reverberação", [L.fx_reverb(size=0.91, color=7500, wet=1.0)], volume=0.95)],
    master_fx=[L.fx_eq(hp=28, peaks=[(250, -1.0, 0.7)], high_shelf=(6000, 1.5))],
    loop=(INTRO * BAR, (INTRO + 20) * BAR),
    tail_bars=3,
    notes_md=__doc__,
)

if __name__ == "__main__":
    out = Path(__file__).with_name("vila_b")
    out.mkdir(exist_ok=True)
    (out / "vila_b.mmp").write_text(song.mmp(), encoding="utf-8")
    (out / "vila_b.mid").write_bytes(song.mid())
    secs = (INTRO + 20) * BAR / 48 * 60 / song.bpm
    print(f"vila_b: {len(song.tracks)} faixas, {sum(len(t.notes) for t in song.tracks)} notas, "
          f"{INTRO + 20} compassos ≈ {secs:.1f} s; laço {INTRO}–{INTRO + 20}")
