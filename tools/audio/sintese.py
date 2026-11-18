#!/usr/bin/env python3
"""Rodada 09.4 (opção B do proprietário) — instrumentos de SÍNTESE PRÓPRIA para as sessões do LMMS.

Nenhuma amostra de terceiros: cada timbre é só parâmetros dos sintetizadores do próprio LMMS 1.2.2
(TripleOscillator: osciladores somados, envelope de volume e filtro; Vibed: corda pinçada por Karplus-Strong com o
impulso GERADO aqui, não de predefinição; Kicker: percussão por oscilador com queda de altura e ruído). O desenho de
cada instrumento está na docstring dele; os números de tempo foram calibrados medindo renders do LMMS (ver
`calibrar()` e docs/architecture/audio.md, parte A).

Uso nas partituras: `instrument, eldata = sintese.violao()` e `L.Track(..., instrument, notas, eldata=eldata, ...)`.
"""
from __future__ import annotations

import base64
import math
import struct

# formas de onda do TripleOscillator (LMMS 1.2.2)
SINE, TRIANGLE, SAW, SQUARE, MOOG, EXP, NOISE = 0, 1, 2, 3, 4, 5, 6
# filtros do instrumento (eldata ftype)
LOWPASS, HIPASS, BANDPASS = 0, 1, 2


def _env(tag, att=0.0, hold=0.0, dec=0.5, sus=0.5, rel=0.1, amt=0.0, lfo_amt=0.0, lfo_spd=0.1, lfo_shape=0) -> str:
    """Um envelope do LMMS (elvol/elcut/elres). Os tempos são os valores do botão (0–2, escala não linear do LMMS);
    `sus` é o NÍVEL de sustentação (1 = fica no máximo, 0 = cai a zero). Medido no LMMS 1.2.2: tempo ≈ 5 s × valor²."""
    return (f'<{tag} lspd_denominator="4" sustain="{sus}" pdel="0" userwavefile="" dec="{dec}" lamt="{lfo_amt}" '
            f'syncmode="0" latt="0" rel="{rel}" amt="{amt}" x100="0" att="{att}" lpdel="0" hold="{hold}" '
            f'lshp="{lfo_shape}" lspd="{lfo_spd}" ctlenvamt="0" lspd_numerator="4"/>')


def eldata(vol: dict | None = None, cut: float = 14000, res: float = 0.5, ftype: int = LOWPASS, filt: bool = False,
           cut_env: dict | None = None) -> str:
    """Envelope de volume (`vol`, parâmetros de `_env`), filtro do instrumento e envelope do corte."""
    v = _env("elvol", **(vol | {"amt": vol.get("amt", 1.0)})) if vol else _env("elvol")
    c = _env("elcut", **cut_env) if cut_env else _env("elcut")
    return (f'<eldata fres="{res}" ftype="{ftype}" fcut="{cut}" fwet="{int(filt)}">{v}{c}{_env("elres")}</eldata>')


def tri(oscs: list[dict]) -> str:
    """TripleOscillator com até três osciladores SOMADOS (modulação "mistura"). Cada um: wave, vol (0–200),
    coarse (semitons, ±24), fine (cents, ±100; igual nos dois canais, ou fine_l/fine_r para abrir o estéreo), pan."""
    o = list(oscs) + [{"vol": 0}] * (3 - len(oscs))
    a = []
    for i, x in enumerate(o):
        fl = x.get("fine_l", x.get("fine", 0)); fr = x.get("fine_r", x.get("fine", 0))
        a.append(f'vol{i}="{x.get("vol", 100)}" pan{i}="{x.get("pan", 0)}" coarse{i}="{x.get("coarse", 0)}" '
                 f'finel{i}="{fl}" finer{i}="{fr}" phoffset{i}="{x.get("phase", 0)}" stphdetun{i}="{x.get("stphase", 0)}" '
                 f'wavetype{i}="{x.get("wave", SINE)}" modalgo{i + 1}="2" userwavefile{i}=""')
    return f'<instrument name="tripleoscillator"><tripleoscillator {" ".join(a)}/></instrument>'


def impulse(kind: str = "pluck", n: int = 128, seed: int = 1) -> str:
    """Impulso da corda do Vibed (128 floats, base64), GERADO aqui: "pluck" = triângulo de dedo (ponto de toque a 1/5),
    "soft" = meia senoide (dedo macio), "bright" = triângulo + um pouco de ruído determinístico (unha/palheta)."""
    v = []
    rnd = seed
    for i in range(n):
        x = i / (n - 1)
        if kind == "soft":
            y = math.sin(math.pi * x)
        else:
            y = x / 0.2 if x < 0.2 else (1 - x) / 0.8
            if kind == "bright":
                rnd = (rnd * 1103515245 + 12345) & 0x7FFFFFFF
                y = 0.85 * y + 0.15 * ((rnd / 0x7FFFFFFF) * 2 - 1)
        v.append(y * 2 - 1 if kind == "centered" else y)
    return base64.b64encode(struct.pack("<" + "f" * n, *v)).decode()


def vibed(strings: list[dict]) -> str:
    """Vibed com até 9 cordas. Cada uma: octave (índice do LMMS, 2 = fundamental), length (1–16: mais longa = soa mais),
    stiffness (0–0,05: mais rígida = mais brilho e queda mais rápida), pick e pickup (0–0,5: onde se toca e onde se
    escuta), vol (0–200), detune, pan, slap, kind do impulso."""
    a = ['version="0.1"']
    for i in range(9):
        s = strings[i] if i < len(strings) else None
        on = s is not None
        s = s or {}
        a.append(f'active{i}="{int(on)}" volume{i}="{s.get("vol", 100)}" stiffness{i}="{s.get("stiffness", 0)}" '
                 f'pick{i}="{s.get("pick", 0)}" pickup{i}="{s.get("pickup", 0.05)}" octave{i}="{s.get("octave", 2)}" '
                 f'length{i}="{s.get("length", 1)}" detune{i}="{s.get("detune", 0)}" pan{i}="{s.get("pan", 0)}" '
                 f'slap{i}="{s.get("slap", 0)}" impulse{i}="{int(on)}" graph{i}="{impulse(s.get("kind", "pluck"))}"')
    return f'<instrument name="vibedstrings"><vibedstrings {" ".join(a)}/></instrument>'


# ============================================================================ instrumentos
# Tempos do LMMS 1.2.2, medidos (calibrar()): ataque, decaimento e soltura ≈ 5 s × valor²
# (0,1 → 50 ms; 0,2 → 200 ms; 0,3 → 450 ms; 0,4 → 800 ms; 0,6 → 1,8 s). `sus` é o nível sustentado.
# Corte do filtro: o envelope do corte com amt 1 leva o centroide de ~400 Hz a ~2,1 kHz no ataque e volta em `dec`.

def t(seconds: float) -> float:
    """Valor do botão de tempo do LMMS para uma duração (s)."""
    return round(math.sqrt(max(seconds, 0.0) / 5.0), 4)


def _inst(oscs, vol_env, cut=14000, res=0.5, filt=False, cut_env=None):
    return tri(oscs), eldata(vol_env, cut=cut, res=res, filt=filt, cut_env=cut_env)


def violao():
    """Violão de náilon dedilhado: dente de serra + triângulo uma oitava acima (corpo), low-pass com envelope de corte
    rápido (o brilho do dedo some em ~0,25 s), queda de volume em ~1,6 s até um resto que se apaga ao soltar."""
    return _inst([{"wave": SAW, "vol": 110, "fine_l": -3, "fine_r": 3}, {"wave": TRIANGLE, "vol": 130},
                  {"wave": TRIANGLE, "vol": 40, "coarse": 12}],
                 {"att": t(0.002), "dec": t(1.6), "sus": 0.0, "rel": t(0.25)},
                 cut=950, res=0.35, filt=True, cut_env={"att": 0, "dec": t(0.32), "sus": 0.0, "rel": t(0.1), "amt": 1.0})


def harpa():
    """Harpa: triângulo (corda macia) + seno, corte com envelope suave e cauda longa (~2,8 s)."""
    return _inst([{"wave": TRIANGLE, "vol": 90}, {"wave": SINE, "vol": 60, "coarse": 12}, {"wave": SAW, "vol": 14}],
                 {"att": t(0.003), "dec": t(2.8), "sus": 0.0, "rel": t(0.6)},
                 cut=1500, res=0.3, filt=True, cut_env={"att": 0, "dec": t(0.5), "sus": 0.0, "rel": t(0.2), "amt": 0.8})


def baixo():
    """Baixo dedilhado: triângulo + seno na MESMA oitava (corpo; uma oitava abaixo dominava e soava uma oitava grave —
    medido), serra fraca para o ataque, corte baixo com um toque de envelope, queda de ~1,2 s."""
    return _inst([{"wave": TRIANGLE, "vol": 100}, {"wave": SINE, "vol": 60}, {"wave": SAW, "vol": 16}],
                 {"att": t(0.004), "dec": t(1.2), "sus": 0.25, "rel": t(0.12)},
                 cut=380, res=0.3, filt=True, cut_env={"att": 0, "dec": t(0.15), "sus": 0.0, "rel": t(0.05), "amt": 0.35})


def flauta():
    """Flauta: triângulo (quase seno, com os harmônicos ímpares fracos da flauta) + triângulo uma oitava acima + sopro, ataque de 70 ms, sustenta,
    leve tremolo de volume (vibrato percebido), soltura de 0,25 s."""
    return _inst([{"wave": TRIANGLE, "vol": 90}, {"wave": TRIANGLE, "vol": 40, "coarse": 12}, {"wave": NOISE, "vol": 8}],
                 {"att": t(0.07), "dec": t(0.2), "sus": 0.85, "rel": t(0.25), "lfo_amt": 0.06, "lfo_spd": 0.22},
                 cut=9500, res=0.3, filt=True)


def flauta_pa():
    """Flauta de pã: seno com sopro mais presente e ataque de sopro (corte que abre com o envelope)."""
    return _inst([{"wave": SINE, "vol": 100}, {"wave": NOISE, "vol": 12}, {"wave": SINE, "vol": 10, "coarse": 12}],
                 {"att": t(0.09), "dec": t(0.3), "sus": 0.75, "rel": t(0.35), "lfo_amt": 0.04, "lfo_spd": 0.2},
                 cut=5200, res=0.4, filt=True, cut_env={"att": t(0.02), "dec": t(0.25), "sus": 0.2, "rel": t(0.2), "amt": 0.3})


def clarinete():
    """Clarinete: quadrada (harmônicos ímpares) + seno, low-pass em ~4,8 kHz com ressonância leve, ataque de 50 ms."""
    return _inst([{"wave": SQUARE, "vol": 70}, {"wave": SINE, "vol": 60}],
                 {"att": t(0.05), "dec": t(0.3), "sus": 0.85, "rel": t(0.18)},
                 cut=4800, res=0.5, filt=True)


def cordas(lentas: bool = False):
    """Cordas em naipe: duas serras desafinadas ±8 cents abertas no estéreo + seno uma oitava abaixo, low-pass em
    4,3 kHz (3,3 nas lentas), ataque lento (0,5 s; 1,0 s nas lentas) e soltura longa."""
    return _inst([{"wave": SAW, "vol": 60, "fine_l": -8, "fine_r": 6}, {"wave": SAW, "vol": 60, "fine_l": 7, "fine_r": -9},
                  {"wave": SINE, "vol": 10, "coarse": -12}],
                 {"att": t(1.0 if lentas else 0.5), "dec": t(0.5), "sus": 0.9, "rel": t(0.7)},
                 cut=3300 if lentas else 4300, res=0.3, filt=True)


def violoncelo():
    """Violoncelo: duas serras desafinadas + quadrada fraca, low-pass em 2,0 kHz, arcada de 0,3 s."""
    return _inst([{"wave": SAW, "vol": 70, "fine_l": -5, "fine_r": 4}, {"wave": SAW, "vol": 50, "fine_l": 6, "fine_r": -5},
                  {"wave": SQUARE, "vol": 18}],
                 {"att": t(0.3), "dec": t(0.5), "sus": 0.85, "rel": t(0.5), "lfo_amt": 0.03, "lfo_spd": 0.2},
                 cut=2000, res=0.35, filt=True)


def _sino(partials, decay, rel=None, att=0.0015, cut=14000):
    """Barra ou sino percutido: senos somados em razões não harmônicas (`partials` = [(semitons, cents, vol)]), queda
    exponencial aparente (decaimento do LMMS até 0)."""
    return _inst([{"wave": SINE, "vol": v, "coarse": c, "fine": f} for c, f, v in partials],
                 {"att": t(att), "dec": t(decay), "sus": 0.0, "rel": t(rel if rel is not None else decay * 0.4)},
                 cut=cut, filt=cut < 14000)


def glockenspiel():
    """Glockenspiel: barra de metal — parciais 1; 2,76 (+17 st +58 c); 4,24 (+25 st, limite do oscilador). ~1,3 s."""
    return _sino([(0, 0, 100), (17, 58, 34), (24, 100, 12)], decay=1.3)


def celesta():
    """Celesta: barra com ressonador — fundamental forte, oitava fraca e o brilho do martelo (quadrada fraca duas
    oitavas acima: harmônicos ímpares até o agudo), ~1,0 s."""
    return _inst([{"wave": SINE, "vol": 100}, {"wave": SINE, "vol": 26, "coarse": 12}, {"wave": SQUARE, "vol": 7, "coarse": 24}],
                 {"att": t(0.0015), "dec": t(1.0), "sus": 0.0, "rel": t(0.4)})


def caixa_de_musica():
    """Caixa de música: lâmina de pente — parciais 1; 3,0 (+19 st +2 c); 4,24; ataque seco, ~0,9 s."""
    return _sino([(0, 0, 100), (19, 2, 22), (24, 100, 14)], decay=0.9)


def sininho():
    """Sininho: sino pequeno e agudo — parciais 1; 2,4 (+15 st +16 c); 4,24; ~0,7 s."""
    return _sino([(0, 0, 100), (15, 16, 40), (24, 100, 20)], decay=0.7)


def cristal():
    """Cristal: copo de vidro — seno + quadrada fraca duas oitavas acima (o brilho) e triângulo na oitava, ataque suave
    (20 ms) e cauda de ~1,3 s (mais longa vazava na emenda do laço da versão B)."""
    return _inst([{"wave": SINE, "vol": 100}, {"wave": SQUARE, "vol": 14, "coarse": 24}, {"wave": TRIANGLE, "vol": 18, "coarse": 12, "fine": 5}],
                 {"att": t(0.02), "dec": t(1.3), "sus": 0.1, "rel": t(0.6)})


def kalimba():
    """Kalimba: lâmina de metal dedilhada — fundamental, oitava fraca e a parcial aguda da lâmina (quadrada fraca a
    +24 st +100 c: o "tim" do dedo), ~0,8 s."""
    return _inst([{"wave": SINE, "vol": 100}, {"wave": SQUARE, "vol": 4, "coarse": 24, "fine": 100}, {"wave": TRIANGLE, "vol": 16, "coarse": 12}],
                 {"att": t(0.001), "dec": t(0.8), "sus": 0.0, "rel": t(0.32)})


def vibrafone():
    """Vibrafone: barra de metal com tubos — parciais 1 e 4 (+24 st, a do vibrafone), tremolo do motor (~5 Hz) e
    cauda de ~2,2 s (mais longa vazava na emenda do laço: QA da emenda). É o "sino grave" da versão B."""
    return _inst([{"wave": SINE, "vol": 100}, {"wave": SINE, "vol": 30, "coarse": 24}, {"wave": SINE, "vol": 8, "coarse": 12}],
                 {"att": t(0.003), "dec": t(2.2), "sus": 0.0, "rel": t(0.9), "lfo_amt": 0.22, "lfo_spd": 0.3})


def chocalho():
    """Chocalho: ruído branco com queda de 70 ms (as sementes), sem corpo grave; o EQ da faixa tira o resto."""
    return _inst([{"wave": NOISE, "vol": 120}], {"att": t(0.002), "dec": t(0.07), "sus": 0.0, "rel": t(0.03)})
