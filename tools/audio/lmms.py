"""Rodada 09.4 — escritor de sessões LMMS 1.2 (.mmp) e MIDI a partir de uma partitura escrita à mão.

Este módulo NÃO compõe nada: só transcreve notas decididas pelo compositor (as peças em fontes/audio/musica/)
para os formatos de uma ferramenta musical real. A sessão .mmp abre no LMMS (piano roll, mixer, automação)
e é renderizada pelo próprio LMMS (tools/audio/render.sh). Depois da primeira geração, a sessão é a fonte
editável: quem mexer à mão no LMMS deve salvar por cima do .mmp e deixar de regenerar a partir do .py.

Notação de compasso (uma string por compasso):
    "F#5:3 E5 D5 E5"        nota:duração (em unidades da peça; sem duração = repete a anterior)
    "r:2"                   pausa
    "D3+A3+F#4:6"           acorde (notas simultâneas)
    "C#6:4@60"              @ = intensidade 0–127 (padrão do instrumento)
    "~"                     compasso vazio
Durações em unidades (unit, em ticks LMMS: 192 por semibreve → colcheia = 24).
"""
from __future__ import annotations

import re
import struct
from dataclasses import dataclass, field
from xml.sax.saxutils import quoteattr

SF2 = "/usr/share/sounds/sf2/FluidR3_GM.sf2"
TICKS_WHOLE = 192
NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
_ids = iter(range(4100000, 9999999))


def midi(name: str) -> int:
    """'F#4' → 66 (C4 = 60)."""
    m = re.fullmatch(r"([A-G])(#|b)?(-?\d)", name)
    if not m:
        raise ValueError(f"nota inválida: {name}")
    n = NAMES[m[1]] + (1 if m[2] == "#" else -1 if m[2] == "b" else 0)
    return n + 12 * (int(m[3]) + 1)


@dataclass
class Note:
    pos: int      # ticks desde o início da peça
    len: int
    key: int      # MIDI
    vel: int


def parse_bars(bars: list[str], unit: int, bar_ticks: int, start_bar: int = 0, vel: int = 90) -> list[Note]:
    """Compassos → notas. Confere que cada compasso soma exatamente bar_ticks (erro de escrita aparece cedo)."""
    out: list[Note] = []
    for b, text in enumerate(bars):
        pos = (start_bar + b) * bar_ticks
        t = 0
        if text.strip() == "~":
            continue
        dur = None
        for tok in text.split():
            m = re.fullmatch(r"([^:@]+)(?::([\d.]+))?(?:@(\d+))?", tok)
            if not m:
                raise ValueError(f"compasso {start_bar + b + 1}: token inválido {tok!r}")
            if m[2]:
                dur = float(m[2])
            if dur is None:
                raise ValueError(f"compasso {start_bar + b + 1}: primeira nota sem duração")
            ticks = round(dur * unit)
            if m[1] != "r":
                v = int(m[3]) if m[3] else vel
                for n in m[1].split("+"):
                    out.append(Note(pos + t, ticks, midi(n), v))
            t += ticks
        if t != bar_ticks:
            raise ValueError(f"compasso {start_bar + b + 1} soma {t} ticks, esperado {bar_ticks}: {text!r}")
    return out


# --------------------------------------------------------------------------- instrumentos

def sf2(patch: int, bank: int = 0, gain: float = 1.0) -> str:
    return (f'<instrument name="sf2player"><sf2player src="{SF2}" bank="{bank}" patch="{patch}" gain="{gain}" '
            'reverbOn="0" chorusOn="0" reverbRoomSize="0.2" reverbDamping="0" reverbWidth="0.5" reverbLevel="0.9" '
            'chorusNum="3" chorusLevel="2" chorusSpeed="0.3" chorusDepth="8"/></instrument>')


def kicker(start=140.0, end=60.0, decay_ms=80.0, noise=0.0, click=0.0, gain=1.0, slope=0.06, env=0.12,
           dist=0.0, follow_note=False) -> str:
    """Síntese de percussão do LMMS (oscilador com queda de frequência + ruído + clique), sem amostras.
    follow_note=False: frequências fixas; True: a frequência inicial é a da nota (variação de altura)."""
    return (f'<instrument name="kicker"><kicker gain="{gain}" decay="{decay_ms}" decay_numerator="4" '
            f'decay_denominator="4" syncmode="0" slope="{slope}" env="{env}" startfreq="{start}" endfreq="{end}" '
            f'startnote="{int(follow_note)}" endnote="0" noise="{noise}" click="{click}" dist="{dist}" distend="{dist}" version="1"/>'
            '</instrument>')


def noise_osc(vol=100) -> str:
    """TripleOscillator só com ruído branco no oscilador 1 (sustentado enquanto a nota dura; L e R descorrelacionados,
    medido na rodada 09.4). Para camas de ambiente (vento, água) com EQ e automação de volume."""
    o = []
    for i, (v, w) in enumerate(((vol, 6), (0, 0), (0, 0))):
        o.append(f'vol{i}="{v}" pan{i}="0" coarse{i}="0" finel{i}="0" finer{i}="0" phoffset{i}="0" stphdetun{i}="0" '
                 f'wavetype{i}="{w}" modalgo{i + 1}="2" userwavefile{i}=""')
    return f'<instrument name="tripleoscillator"><tripleoscillator {" ".join(o)}/></instrument>'


# Mallets (STK) do LMMS 1.2.2: NÃO use. Testado na rodada 09.4: altura errada (C5 → pico em 5,5 kHz na marimba,
# 375 Hz no vidro), "madeira" vira clique de 11 ms e a tigela tibetana derrubou o render (QThread destruída).


# --------------------------------------------------------------------------- efeitos

def fx_reverb(size=0.85, color=9000, wet=1.0, out_gain=0.0) -> str:
    return (f'<effect name="reverbsc" on="1" wet="{wet}" gate="0" autoquit="1" autoquit_numerator="4" '
            f'autoquit_denominator="4" syncmode="0"><ReverbSCControls input_gain="0" size="{size}" '
            f'color="{color}" output_gain="{out_gain}"/></effect>')


def fx_eq(hp=None, lp=None, low_shelf=None, high_shelf=None, peaks=(), out_gain=0.0) -> str:
    """EQ paramétrico do LMMS. hp/lp em Hz; shelves = (Hz, dB); peaks = até 4 (Hz, dB, largura)."""
    a = {"Inputgain": 0, "Outputgain": out_gain, "HPactive": int(hp is not None), "HPfreq": hp or 30,
         "HPres": 0.707, "HP12": 1, "HP24": 0, "HP48": 0, "LPactive": int(lp is not None), "LPfreq": lp or 18000,
         "LPres": 0.707, "LP12": 1, "LP24": 0, "LP48": 0,
         "Lowshelfactive": int(low_shelf is not None), "LowShelffreq": (low_shelf or (80, 0))[0],
         "Lowshelfgain": (low_shelf or (80, 0))[1], "LowShelfres": 1.4,
         "Highshelfactive": int(high_shelf is not None), "Highshelffreq": (high_shelf or (8000, 0))[0],
         "HighShelfgain": (high_shelf or (8000, 0))[1], "HighShelfres": 1.4,
         "AnalyseIn": 0, "AnalyseOut": 0}
    base = [(120, 0, 0.3), (250, 0, 0.3), (2500, 0, 0.3), (5000, 0, 0.3)]
    for i, (f, g, bw) in enumerate(list(peaks) + base[len(peaks):]):
        a[f"Peak{i + 1}active"] = int(i < len(peaks))
        a[f"Peak{i + 1}freq"] = f
        a[f"Peak{i + 1}gain"] = g
        a[f"Peak{i + 1}bw"] = bw
    attrs = " ".join(f'{k}="{v}"' for k, v in a.items())
    return (f'<effect name="eq" on="1" wet="1" gate="0" autoquit="1" autoquit_numerator="4" autoquit_denominator="4" '
            f'syncmode="0"><Eq {attrs}/></effect>')


# Delay do LMMS: NÃO use. Escrito em XML ele não produz eco no LMMS 1.2.2 (testado na rodada 09.4 com quatro
# nomes de nó e autoquit 0/1). Eco = notas escritas na partitura (written_echo).


def written_echo(notes: list[Note], delay: int, repeats: int = 2, decay: float = 0.45, min_len: int = 0,
                 limit: int | None = None) -> list[Note]:
    """Eco ESCRITO: repete cada nota longa (≥ min_len ticks) depois de `delay` ticks, mais fraca a cada vez.
    Fica no piano roll como notas comuns (editável); não depende de plugin."""
    out = list(notes)
    for n in notes:
        if n.len < min_len:
            continue
        v = n.vel
        for k in range(1, repeats + 1):
            v = int(v * decay)
            pos = n.pos + k * delay
            if v < 8 or (limit is not None and pos >= limit):
                break
            out.append(Note(pos, max(12, n.len // 2), n.key, v))
    return out


# --------------------------------------------------------------------------- sessão

@dataclass
class Track:
    name: str
    instrument: str
    notes: list[Note]
    vol: int = 100           # 0–200 (%)
    pan: int = 0             # −100..100
    fx: list[str] = field(default_factory=list)     # efeitos do canal do mixer desta faixa
    sends: dict[str, float] = field(default_factory=dict)   # nome do canal de retorno → quantidade
    vol_automation: list[tuple[int, int]] | None = None     # [(tick, volume%)] — curva de volume da faixa
    channel: int = 0         # canal MIDI no .mid (9 = percussão GM)
    program: int = 0


@dataclass
class Bus:
    name: str
    fx: list[str]
    volume: float = 1.0


@dataclass
class Song:
    title: str
    bpm: float
    num: int
    den: int
    tracks: list[Track]
    buses: list[Bus] = field(default_factory=list)
    master_fx: list[str] = field(default_factory=list)
    loop: tuple[int, int] | None = None    # marcadores de laço (ticks): o corpo que o jogo repete
    notes_md: str = ""
    tail_bars: int = 2                     # silêncio no fim para a reverberação soar (nota de volume 0)

    @property
    def bar_ticks(self) -> int:
        return TICKS_WHOLE * self.num // self.den

    def end_tick(self) -> int:
        return max(n.pos + n.len for t in self.tracks for n in t.notes)

    # ------------------------------------------------------------ .mmp
    def mmp(self) -> str:
        bus_ch = {b.name: len(self.tracks) + 1 + i for i, b in enumerate(self.buses)}
        L = ['<?xml version="1.0"?>', "<!DOCTYPE lmms-project>",
             '<lmms-project creator="LMMS" creatorversion="1.2.2" type="song" version="1.0">',
             f'  <head bpm="{self.bpm}" timesig_numerator="{self.num}" timesig_denominator="{self.den}" '
             'mastervol="100" masterpitch="0"/>',
             "  <song>", '    <trackcontainer type="song">']
        autos = []
        for i, t in enumerate(self.tracks, start=1):
            vol_id = next(_ids)
            L.append(f'      <track muted="0" type="0" name={quoteattr(t.name)} solo="0">')
            L.append(f'        <instrumenttrack fxch="{i}" basenote="57" pitchrange="1" pitch="0" pan="{t.pan}" '
                     'usemasterpitch="1">')
            L.append(f'          <vol id="{vol_id}" value="{t.vol}" scale_type="linear"/>')
            L.append(f"          {t.instrument}")
            L.append('          <eldata fres="0.5" fcut="14000" fwet="0" ftype="0"/>')
            L.append('          <fxchain enabled="0" numofeffects="0"/>')
            L.append("        </instrumenttrack>")
            # um padrão por faixa, do tamanho da peça (fácil de editar no piano roll)
            L.append(f'        <pattern muted="0" type="1" steps="16" name={quoteattr(t.name)} pos="0">')
            for n in sorted(t.notes, key=lambda n: (n.pos, n.key)):
                L.append(f'          <note pos="{n.pos}" len="{n.len}" key="{n.key - 12}" vol="{round(n.vel * 100 / 127)}" pan="0"/>')
            if i == 1 and self.tail_bars:   # marca de fim (volume 0): o render inclui a cauda
                end = -(-self.end_tick() // self.bar_ticks) * self.bar_ticks + self.tail_bars * self.bar_ticks
                L.append(f'          <note pos="{end - 1}" len="1" key="57" vol="0" pan="0"/>')
            L.append("        </pattern>")
            L.append("      </track>")
            if t.vol_automation:
                autos.append((t.name, vol_id, t.vol_automation))
        for name, oid, pts in autos:
            L.append(f'      <track muted="0" type="5" name={quoteattr("Volume: " + name)} solo="0">')
            L.append("        <automationtrack/>")
            ln = max(p for p, _ in pts) + self.bar_ticks
            L.append(f'        <automationpattern prog="1" tens="1" mute="0" name={quoteattr(name + ">>Volume")} pos="0" len="{ln}">')
            for p, v in pts:
                L.append(f'          <time pos="{p}" value="{v}"/>')
            L.append(f'          <object id="{oid}"/>')
            L.append("        </automationpattern>")
            L.append("      </track>")
        L.append("    </trackcontainer>")
        L.append("    <fxmixer>")
        L.append(self._chan(0, "Master", self.master_fx, 1.0, {}))
        for i, t in enumerate(self.tracks, start=1):
            sends = {0: 1.0} | {bus_ch[k]: v for k, v in t.sends.items()}
            L.append(self._chan(i, t.name, t.fx, 1.0, sends))
        for b in self.buses:
            L.append(self._chan(bus_ch[b.name], b.name, b.fx, b.volume, {0: 1.0}))
        L.append("    </fxmixer>")
        lp0, lp1 = self.loop or (0, self.bar_ticks)
        L.append(f'    <timeline lp0pos="{lp0}" lp1pos="{lp1}" lpstate="{int(self.loop is not None)}"/>')
        L.append("    <controllers/>")
        L.append(f'    <projectnotes>{_esc(self.notes_md)}</projectnotes>')
        L.append("  </song>")
        L.append("</lmms-project>")
        return "\n".join(L) + "\n"

    @staticmethod
    def _chan(num, name, fx, volume, sends) -> str:
        s = [f'      <fxchannel muted="0" volume="{volume}" soloed="0" num="{num}" name={quoteattr(name)}>',
             f'        <fxchain enabled="{int(bool(fx))}" numofeffects="{len(fx)}">']
        s += [f"          {e}" for e in fx]
        s.append("        </fxchain>")
        s += [f'        <send amount="{v}" channel="{c}"/>' for c, v in sends.items()]
        s.append("      </fxchannel>")
        return "\n".join(s)

    # ------------------------------------------------------------ .mid (tipo 1, 96 ppq: 1 tick LMMS = 2 ppq/… )
    def mid(self) -> bytes:
        ppq = 48   # 1 tick LMMS = 1 tick MIDI quando ppq = 48 (semínima = 48 ticks)
        tempo = round(60_000_000 / self.bpm)
        def vlq(v):
            b = [v & 0x7F]
            while v > 0x7F:
                v >>= 7
                b.insert(0, (v & 0x7F) | 0x80)
            return bytes(b)
        def chunk(events):
            data, last = b"", 0
            for t, ev in sorted(events, key=lambda e: (e[0], e[1][0] & 0xF0 == 0x90)):
                data += vlq(t - last) + ev
                last = t
            data += b"\x00\xff\x2f\x00"
            return b"MTrk" + struct.pack(">I", len(data)) + data
        dd = {2: 1, 4: 2, 8: 3, 16: 4}[self.den]
        head = [(0, b"\xff\x51\x03" + tempo.to_bytes(3, "big")),
                (0, bytes([0xFF, 0x58, 4, self.num, dd, 24, 8])),
                (0, b"\xff\x03" + vlq(len(self.title.encode())) + self.title.encode())]
        tracks = [chunk(head)]
        for t in self.tracks:
            ch = t.channel
            ev = [(0, b"\xff\x03" + vlq(len(t.name.encode())) + t.name.encode())]
            if ch != 9:
                ev.append((0, bytes([0xC0 | ch, t.program])))
            for n in t.notes:
                ev.append((n.pos, bytes([0x90 | ch, n.key, max(1, n.vel)])))
                ev.append((n.pos + n.len, bytes([0x80 | ch, n.key, 0])))
            tracks.append(chunk(ev))
        return b"MThd" + struct.pack(">IHHH", 6, 1, len(tracks), ppq) + b"".join(tracks)


def _esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def humanize(notes: list[Note], bar_ticks: int, accents: list[int], seed: int = 0, spread: int = 6,
             timing: int = 0) -> list[Note]:
    """Intensidade pela métrica (acentos por colcheia do compasso) + desvio determinístico pequeno.
    Sem aleatoriedade: o desvio sai de uma tabela fixa indexada pela posição (mesma peça → mesmo resultado).
    timing = desvio máximo de ataque em ticks (só atrasa no tempo forte, para não antecipar o pulso)."""
    table = [0, 3, -2, 4, -3, 1, -4, 2, -1, 3, -2, 0, 4, -3, 2, -1]
    late = [1, 0, 2, 1, 0, 2, 1, 1, 0, 2, 0, 1, 2, 0, 1, 0]
    unit = bar_ticks // len(accents)
    out = []
    for n in notes:
        k = (n.pos % bar_ticks) // unit
        i = (n.pos // unit + seed) % len(table)
        d = table[i] * spread // 4
        dt = (late[i] * timing) // 2 if timing else 0
        out.append(Note(n.pos + dt, max(1, n.len - dt), n.key, max(1, min(127, n.vel + accents[k] + d))))
    return out
