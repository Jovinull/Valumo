#!/usr/bin/env python3
"""Rodada 09.4 — renderiza uma sessão de música do LMMS e prepara as versões de escuta e de jogo.

    python3 tools/audio/render_musica.py fontes/audio/musica/vila/vila_a/vila_a.mmp SAIDA [--runtime assets/audio/musica/vila_a]

1. Escuta: render do LMMS (48 kHz, float) da sessão inteira (introdução + corpo + cauda).
2. Jogo: lê os marcadores de laço da PRÓPRIA sessão (lp0pos/lp1pos), duplica o corpo numa cópia temporária,
   renderiza e corta em [0, fim da 2ª passada). O laço do jogo é [início da 2ª passada, fim]: a 2ª passada já
   começa com a cauda da 1ª, então voltar do fim ao início do laço não corta a reverberação.
3. Masterização: um ganho único (medido na escuta, alvo −18 LUFS integrados) aplicado às duas versões +
   limitador de pico a −1 dBFS (ffmpeg alimiter). Nada de normalizar cada arquivo separadamente.
4. Saídas: master WAV 24 bits (escuta e jogo) em SAIDA; OGG Vorbis q5 + laço.json em --runtime.

Funciona com a sessão editada à mão no LMMS: só depende dos marcadores de laço e das notas da sessão.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

TARGET_LUFS = -18.0
SR = 48000


def run(*cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode:
        raise SystemExit(f"falhou: {' '.join(map(str, cmd))}\n{r.stderr[-2000:]}")
    return r


def lmms_command() -> list[str]:
    """Como chamar o LMMS 1.2.2 nesta máquina: VX_LMMS (comando completo, separado por espaços), `lmms` no PATH (pacote do
    sistema, Ubuntu na rodada 09.4) ou o Flatpak io.lmms.LMMS (Fedora, instalado com --user, sem privilégio). No Flatpak
    o sandbox tem /tmp próprio: as permissões abrem o disco e o /tmp do sistema para ler a sessão e gravar o WAV."""
    if os.environ.get("VX_LMMS"):
        return os.environ["VX_LMMS"].split()
    if shutil.which("lmms"):
        return ["lmms"]
    if shutil.which("flatpak") and subprocess.run(["flatpak", "info", "io.lmms.LMMS"], capture_output=True).returncode == 0:
        return ["flatpak", "run", "--filesystem=host", "--filesystem=/tmp", "--command=lmms", "io.lmms.LMMS"]
    raise SystemExit("LMMS não encontrado: instale o pacote lmms, ou `flatpak install --user flathub io.lmms.LMMS`, "
                     "ou aponte VX_LMMS para o comando")


def lmms_render(mmp: Path, wav: Path):
    run(*lmms_command(), "render", str(mmp), "-f", "wav", "-s", str(SR), "-a", "-i", "sincbest", "-o", str(wav),
        env={"QT_QPA_PLATFORM": "offscreen", "HOME": str(Path.home()), "PATH": "/usr/bin:/bin",
             "XDG_RUNTIME_DIR": os.environ.get("XDG_RUNTIME_DIR", ""), "DBUS_SESSION_BUS_ADDRESS": os.environ.get("DBUS_SESSION_BUS_ADDRESS", "")})


def loudness(wav: Path) -> tuple[float, float]:
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(wav), "-af", "ebur128=peak=true", "-f", "null", "-"],
                       capture_output=True, text=True)
    i = float(re.findall(r"I:\s+(-?[\d.]+) LUFS", r.stderr)[-1])
    tp = float(re.findall(r"Peak:\s+(-?[\d.]+|-inf) dBFS", r.stderr)[-1])
    return i, tp


def master(src: Path, dst: Path, gain_db: float, start=None, end=None):
    af = f"volume={gain_db:.2f}dB,alimiter=limit=0.891:attack=5:release=80:level=false:latency=true"   # −1 dBFS
    cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(src)]
    if start is not None:
        af = f"atrim=start_sample={start}:end_sample={end}," + af
    cmd += ["-af", af, "-ar", str(SR), "-c:a", "pcm_s24le", str(dst)]
    run(*cmd)


def looped_session(mmp: Path, out: Path) -> tuple[int, int, float]:
    tree = ET.parse(mmp)
    root = tree.getroot()
    tl = root.find("song/timeline")
    lp0, lp1 = int(tl.get("lp0pos")), int(tl.get("lp1pos"))
    body = lp1 - lp0
    bpm = float(root.find("head").get("bpm"))
    for pat in root.iter("pattern"):
        notes = list(pat.findall("note"))
        tail = [n for n in notes if int(n.get("pos")) >= lp1]          # marca de fim e cauda: empurra
        for n in tail:
            n.set("pos", str(int(n.get("pos")) + body))
        for n in notes:
            p = int(n.get("pos"))
            if lp0 <= p < lp1:
                c = ET.SubElement(pat, "note", dict(n.attrib))
                c.set("pos", str(p + body))
        # padrão em ordem de posição (como o LMMS grava): a marca de fim fica por último
        allnotes = sorted(pat.findall("note"), key=lambda n: int(n.get("pos")))
        for n in allnotes:
            pat.remove(n)
        pat.extend(allnotes)
    for ap in root.iter("automationpattern"):
        pts = list(ap.findall("time"))
        ap.set("len", str(int(ap.get("len")) + body))
        for t in pts:
            p = int(t.get("pos"))
            if p >= lp1:
                t.set("pos", str(p + body))
        for t in pts:
            p = int(t.get("pos"))
            if lp0 <= p < lp1:
                c = ET.Element("time", dict(t.attrib))
                c.set("pos", str(p + body))
                ap.insert(list(ap).index(ap.find("object")), c)
    tree.write(out, encoding="unicode", xml_declaration=True)
    return lp0, lp1, bpm


# clique da sonda de calibração: síntese própria (sem banco de amostras), ataque imediato e queda de 15 ms
def _click() -> str:
    import sintese as S
    ins, el = S._inst([{"wave": S.SQUARE, "vol": 100}], {"att": 0, "dec": S.t(0.015), "sus": 0.0, "rel": S.t(0.005)})
    return ins + el


CLICK = None


def calibrate(mmp: Path, td: Path, ticks: list[int]) -> list[int]:
    """Posição real (amostra) de cada tick no render do LMMS: sonda com o MESMO cabeçalho (andamento e
    compasso) e cliques curtos (bloco de madeira GM) no tick 0 e nos ticks pedidos. Amostra = início do
    clique − início do clique do tick 0 (a latência do instrumento se cancela). O clique é síntese própria
    (quadrada com queda de 15 ms); com o banco de amostras, a sonda ficava MUDA sem o banco instalado e o limiar caía a
    zero — todas as posições saíam 50 ms adiantadas sem aviso (achado na integração da 09.4). Agora sonda muda = erro."""
    import numpy as np
    import soundfile as sf
    global CLICK
    CLICK = CLICK or _click()
    head = ET.parse(mmp).getroot().find("head")
    allt = [0] + ticks
    notes = "".join(f'<note pos="{t}" len="6" key="64" vol="100" pan="0"/>' for t in allt)
    end = max(allt) + 192
    xml = ('<?xml version="1.0"?><!DOCTYPE lmms-project><lmms-project creator="LMMS" creatorversion="1.2.2" '
           f'type="song" version="1.0"><head bpm="{head.get("bpm")}" timesig_numerator="{head.get("timesig_numerator")}" '
           f'timesig_denominator="{head.get("timesig_denominator")}" mastervol="100" masterpitch="0"/><song>'
           '<trackcontainer type="song"><track muted="0" type="0" name="sonda" solo="0"><instrumenttrack fxch="0" '
           'basenote="57" vol="100" pan="0" pitchrange="1" pitch="0" usemasterpitch="1">'
           + CLICK + '<fxchain enabled="0" '
           f'numofeffects="0"/></instrumenttrack><pattern muted="0" type="1" steps="16" name="s" pos="0">{notes}'
           f'<note pos="{end}" len="1" key="57" vol="0" pan="0"/></pattern></track></trackcontainer>'
           '<fxmixer><fxchannel num="0" name="Master" volume="1" muted="0" soloed="0"><fxchain enabled="0" '
           'numofeffects="0"/></fxchannel></fxmixer></song></lmms-project>')
    (td / "sonda.mmp").write_text(xml)
    lmms_render(td / "sonda.mmp", td / "sonda.wav")
    x, sr = sf.read(str(td / "sonda.wav"), always_2d=True)
    a = np.abs(x).max(1)
    if a.max() < 1e-3:
        raise SystemExit("calibração: a sonda saiu muda — as posições do laço seriam chutadas")
    thr = a.max() * 0.05
    framepertick = sr * 60.0 / (float(head.get("bpm")) * 48)
    pos = []
    for t in allt:                     # procura o ataque perto da posição teórica (±50 ms)
        lo = max(0, int(t * framepertick - 0.05 * sr))
        seg = a[lo:lo + int(0.1 * sr) + 2000]
        pos.append(lo + int(np.argmax(seg > thr)))
    return [p - pos[0] for p in pos[1:]]


def main():
    args = sys.argv[1:]
    runtime = None
    if "--runtime" in args:
        i = args.index("--runtime")
        runtime = Path(args[i + 1])
        del args[i:i + 2]
    mmp, outdir = Path(args[0]), Path(args[1])
    outdir.mkdir(parents=True, exist_ok=True)
    name = mmp.stem
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        raw = td / "escuta.wav"
        lmms_render(mmp, raw)
        lp0, lp1, bpm = looped_session(mmp, td / "laco.mmp")
        # o sf2player do LMMS 1.2 dispara notas na grade do buffer (256 quadros): se o corpo não tem um número
        # inteiro de buffers, a 2ª passada cai noutra fase da grade e difere da 1ª (emenda audível)
        frames = (lp1 - lp0) * 60 * SR / (bpm * 48)
        if abs(frames - round(frames)) > 1e-6 or round(frames) % 256:
            print(f"AVISO: corpo do laço = {frames:.2f} quadros, não múltiplo de 256 — ajuste o andamento "
                  f"(ex.: 60000/bpm inteiro e par)", file=sys.stderr)
        raw_loop = td / "laco.wav"
        lmms_render(td / "laco.mmp", raw_loop)
        lufs, tp = loudness(raw)
        gain = TARGET_LUFS - lufs
        # o relógio do LMMS não é a conta teórica (medido: +306 amostras em 44 s a 99 bpm): calibra
        s_loop0, s_loop1 = calibrate(mmp, td, [lp1, 2 * lp1 - lp0])
        esc, jogo = outdir / f"{name}-escuta.wav", outdir / f"{name}-jogo.wav"
        master(raw, esc, gain)
        master(raw_loop, jogo, gain, 0, s_loop1)
        shutil.copy(mmp, outdir / mmp.name)
    info = {"fonte": str(mmp), "lufs_bruto": lufs, "pico_bruto_dbfs": tp, "ganho_db": round(gain, 2),
            "taxa": SR, "laco_inicio_amostra": s_loop0, "laco_fim_amostra": s_loop1,
            "laco_inicio_s": round(s_loop0 / SR, 4), "laco_fim_s": round(s_loop1 / SR, 4)}
    (outdir / f"{name}-render.json").write_text(json.dumps(info, indent=2, ensure_ascii=False))
    if runtime:
        runtime.parent.mkdir(parents=True, exist_ok=True)
        run("ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(jogo), "-c:a", "libvorbis", "-q:a", "5",
            str(runtime.with_suffix(".ogg")))
        run("ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(esc), "-c:a", "libvorbis", "-q:a", "5",
            str(outdir / f"{name}-escuta.ogg"))
    print(json.dumps(info, ensure_ascii=False))


if __name__ == "__main__":
    main()
