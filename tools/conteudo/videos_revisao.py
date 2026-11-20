#!/usr/bin/env python3
"""Laboratório de conteúdo — vídeos de REVISÃO final dos NPCs de ofício (Brásia, Quelmo, Arvel), renderer real:

  linha    idle e caminhada de frente, perfil e costas; transições andar → ofício → andar (frente, perfil, costas) e
           andar → aceno → andar (frente, perfil); os três lado a lado (as três alturas no mesmo quadro)
  jogo     cada NPC no seu lugar da vitrine do estudo (Quelmo na vaga lateral), fazendo o gesto de ofício e o aceno, com
           o herói a 1,0 u na frente dele e a câmera de jogo (4,6 u, 17°)

Para cada vídeo, uma folha de quadros (6 instantes) para inspeção de clipping, apoio dos pés e acessórios.

    .venv/bin/python tools/conteudo/videos_revisao.py SAIDA
"""
import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ANIM = os.path.join(ROOT, "tools", "anim.sh")
PY = sys.executable
NPCS = ("brasia", "quelmo", "arvel")
LINHA = "mode=lineup,style=e,set=npcs-mundo-so,size=1280x720"
ANGULOS = {"frente": 0, "perfil": -90, "costas": 180}


def video(out, spec, frames):
    subprocess.run([ANIM, out, spec, str(frames), "30"], check=True, stdout=subprocess.DEVNULL, cwd=ROOT)
    sheet(out + ".mp4", frames / 30.0)


def sheet(mp4, dur):
    """Folha com 6 quadros igualmente espaçados (prancha do laboratório)."""
    base = mp4[:-4]
    frames = []
    for i in range(6):
        t = dur * (i + 0.5) / 6
        f = f"{base}-q{i}.png"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", f"{t:.2f}", "-i", mp4, "-frames:v", "1", f], check=True)
        frames.append((f, f"{t:.1f} s"))
    subprocess.run([PY, os.path.join(HERE, "prancha.py"), base + "-quadros.png", "6", "320",
                    *[f"{f}:{lab}" for f, lab in frames]], check=True, stdout=subprocess.DEVNULL)
    for f, _ in frames:
        os.remove(f)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for tag, yaw in ANGULOS.items():
        video(os.path.join(out, f"idle-{tag}"), f"{LINHA},clip=idle,myaw={yaw}", 75)
        video(os.path.join(out, f"walk-{tag}"), f"{LINHA},clip=walk,myaw={yaw}", 60)
        video(os.path.join(out, f"transicao-oficio-{tag}"), f"{LINHA},seq=walk:1.0+oficio+walk:1.0,myaw={yaw}", 175)
        if tag != "costas":
            video(os.path.join(out, f"transicao-aceno-{tag}"), f"{LINHA},seq=walk:1.0+emote+walk:1.0,myaw={yaw}", 140)
    scene = json.load(open(os.path.join(ROOT, "assets", "scenes", "vitrine_estudo_npcs.json")))
    tmp = []
    try:
        for k in NPCS:
            npc = next(n for n in scene["npcs"] if n["rig"] == f"characters/npc/{k}")
            fx, fz = math.sin(math.radians(npc["r"])), math.cos(math.radians(npc["r"]))
            hx, hz = npc["x"] + fx * 1.0, npc["z"] + fz * 1.0                 # herói a 1,0 u, na frente do NPC
            hy = (npc["r"] + 180) % 360                                         # olhando o NPC
            for clip, label in (("attack", "oficio"), ("emote", "aceno")):
                doc = json.loads(json.dumps(scene))
                for n in doc["npcs"]:
                    if n["rig"] == npc["rig"]:
                        n["clip"] = clip
                name = f"tmp_revisao_{k}_{clip}"
                tmp.append(os.path.join(ROOT, "assets", "scenes", name + ".json"))
                json.dump(doc, open(tmp[-1], "w"))
                spec = (f"mode=scene,style=e,nohud=1,size=1280x720,layout={name},hx={hx:.3f},hz={hz:.3f},hyaw={hy:.1f},"
                        f"yaw={npc['r']:.1f},pitch=17,dist=4.6,lift=0.9")
                video(os.path.join(out, f"jogo-{k}-{label}"), spec, 115)
    finally:
        for t in tmp:
            os.remove(t)


if __name__ == "__main__":
    main()
