#!/usr/bin/env python3
"""Laboratório de conteúdo — vaga do feirante na barraca aprovada (`market_stall`, que NÃO muda: balcão, toldo e
mercadorias iguais). Atrás do balcão, na câmera de jogo, só aparecem a boina e os olhos do Quelmo. Este estudo compara
a vaga antiga com duas vagas laterais, definidas no espaço LOCAL da barraca (u; +z = frente/balcão, rumo em graus no
sentido do rotateY do viewer), em barracas giradas a 0°, 90°, 180°, 270° e 37°:

  folga      distância do centro do NPC ao sólido medido da barraca (formas compostas do catálogo) ≥ raio do corpo do
             NPC no jogo (colisao_e.json, corpos.padrao = 0,3 u)
  teto       nenhum voxel da barraca sobre a cabeça (o toldo é da barraca: a câmera de conversa não entra nele)
  conversa   o herói (raio 0,3 u) de pé a 1,0 u, na frente do NPC, em chão livre e ALCANÇÁVEL; linha herói → NPC sem
             sólido no meio; dentro do alcance de conversa do jogo (1,8 u)
  passagem   balcão e ponto de compra alcançáveis com o NPC no lugar; no conjunto do comerciante (sobrado + barraca +
             caixotes + banco + placa), as entradas e interações alcançáveis com o NPC no lugar, comparadas com o MESMO
             conjunto sem NPC (o que já falhava sem NPC não é da vaga)
  visível    pixels do Quelmo na câmera de jogo (cena com NPC × a mesma cena sem NPC), herói na distância de conversa

    .venv/bin/python tools/conteudo/vaga_feirante.py [SAIDA]      (relatório; com SAIDA, também as capturas)
Uma barraca por cena (cenas temporárias em assets/scenes/tmp_feirante_*, apagadas no fim): com várias barracas, a
câmera de jogo de uma entrava no toldo da vizinha e a medida de visibilidade saía contaminada.
"""
import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import vitrines as V  # noqa: E402

# (x, z, rumo) no espaço local da barraca. A antiga é o spawn "feirante" do catálogo (atrás do balcão).
CANDIDATAS = {"antiga": None, "lado_direito": (1.60, 0.30, -35.0), "lado_esquerdo": (-1.60, 0.30, 35.0)}
ROTS = (0, 90, 180, 270, 37)
NPC_R, HERO_R, TALK_D, TALK_RANGE = 0.3, 0.3, 1.0, 1.8
LUGAR = (0.5, -4.0)          # uma barraca POR CENA, sempre no mesmo lugar plano (vizinhas atrapalhavam a câmera)


def facing(deg):
    a = math.radians(deg)
    return math.sin(a), math.cos(a)


def spot(cat, cand):
    """A antiga é o spawn 'feirante'; a escolhida (lado direito) é a vaga 'feirante_lateral' do catálogo, para a
    medida valer exatamente para o que foi publicado; a do lado esquerdo é o espelho dela."""
    if cand == "antiga":
        sp = next(s for s in cat["market_stall"]["pontos"]["spawns"] if s["id"] == "feirante")
        return sp["pos_u"][0], sp["pos_u"][2], 0.0
    vaga = next((q for q in cat["market_stall"]["pontos"].get("vagas", []) if q["id"] == "feirante_lateral"), None)
    if vaga is None:
        return CANDIDATAS[cand]
    x, z, r = vaga["pos_u"][0], vaga["pos_u"][2], vaga["rumo"]
    return (x, z, r) if cand == "lado_direito" else (-x, z, -r)


def talk_point(cat, cand, sx, sz, rr):
    """Onde o herói fica para conversar (espaço local): 1,0 u à frente do NPC; na antiga, o ponto 'comprar'."""
    if cand == "antiga":
        p = next(p for p in cat["market_stall"]["pontos"]["interacao"] if p["id"] == "comprar")
        return p["pos_u"][0], p["pos_u"][2]
    fx, fz = facing(rr)
    return sx + fx * TALK_D, sz + fz * TALK_D


def seg_hits(obs, a, b, pad=0.0):
    for i in range(41):
        t = i / 40
        if V.inside(obs, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, pad):
            return True
    return False


def bfs(obs, g, start):
    step, half = 0.25, 20.0
    n = int(2 * half / step)
    cell = lambda x, z: (int((x + half) / step), int((z + half) / step))
    center = lambda i, j: (-half + (i + 0.5) * step, -half + (j + 0.5) * step)
    free = {}

    def ok(i, j):
        if (i, j) not in free:
            x, z = center(i, j)
            free[(i, j)] = g.at(x, z) > -0.42 and not V.inside(obs, x, z, HERO_R)
        return free[(i, j)]
    s = cell(*start)
    seen = {s} if ok(*s) else set()
    stack = list(seen)
    while stack:
        i, j = stack.pop()
        hx, hz = center(i, j)
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = (i + di, j + dj)
            if k in seen or not (0 <= k[0] < n and 0 <= k[1] < n) or not ok(*k):
                continue
            if abs(g.at(*center(*k)) - g.at(hx, hz)) > 0.30:
                continue
            seen.add(k)
            stack.append(k)
    return lambda x, z: any((cell(x, z)[0] + a, cell(x, z)[1] + b) in seen for a in (-1, 0, 1) for b in (-1, 0, 1))


def overhead(stall_vox, vs, sx, sz):
    """Voxels da barraca sobre a cabeça do NPC (coluna de raio 0,3 u, acima de 1,25 u)."""
    n = 0
    for (x, y, z) in stall_vox.v:
        if y * vs >= 1.25 and math.hypot((x + 0.5) * vs - sx, (z + 0.5) * vs - sz) <= NPC_R:
            n += 1
    return n


def scene_doc(cand, stall, npc):
    doc = {"_doc": f"Laboratório de conteúdo — vaga do feirante ({cand}); gerada por tools/conteudo/vaga_feirante.py, "
                   f"só para medir e capturar (temporária). A barraca é a aprovada, intocada.",
           "hero": [0.6, 3.0, 190], "scatter": [{"props": V.BACK_TREES, "count": 40, "ring": [19.5, 24], "seed": 47,
                                                 "minDist": 2.8}],
           "creatures": [], "clouds": V.CLOUDS, "props": [{"n": stall[0], "x": stall[1], "z": stall[2], "r": stall[3]}],
           "npcs": []}
    if npc:
        doc["npcs"] = [{"rig": "characters/npc/quelmo", "x": round(npc[0], 3), "z": round(npc[1], 3),
                        "r": round(npc[2], 1), "clip": "idle"}]
    return doc


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else None
    cat = {a["id"]: a for a in json.load(open(V.CAT))["assets"]}
    V.load_sets()
    man = json.load(open(os.path.join(V.ROOT, "assets", "environment", "e", "props.json")))
    prof = json.load(open(os.path.join(V.ROOT, "assets", "scenes", "colisao_e.json")))
    g = V.Ground()
    import conteudo_props
    stall_vox, _ = conteudo_props.market_stall()
    vs = cat["market_stall"]["medidas"]["voxel"]
    rec = cat["market_stall"]
    report, result, cases = [], {c: {"rotacoes": []} for c in CANDIDATAS}, []
    for r in ROTS:
        stall = V.village(cat, g, [("market_stall", LUGAR, r)], radius=4.0)[0]
        _, x, z, _ = stall
        obs = V.obstacles([stall], cat, man, prof)
        for cand in CANDIDATAS:
            sx, sz, rr = spot(cat, cand)
            px, pz = V.rot(sx, sz, r)
            nx, nz, yaw = x + px, z + pz, (r + rr) % 360
            tx, tz = talk_point(cat, cand, sx, sz, rr)
            tx, tz = V.rot(tx, tz, r)
            tx, tz = x + tx, z + tz
            reach = bfs(obs + [("circ", (nx, nz, NPC_R))], g, (0.6, 3.0))
            pad = 0.0                                                   # folga: maior distância livre até o sólido
            while pad < 2.0 and not V.inside(obs, nx, nz, pad + 0.01):
                pad += 0.01
            clear = not V.inside(obs, nx, nz, NPC_R)
            talk_free = not V.inside(obs, tx, tz, HERO_R) and math.hypot(tx - nx, tz - nz) >= NPC_R + HERO_R
            los = not seg_hits(obs, (tx, tz), (nx, nz))
            dist = math.hypot(tx - nx, tz - nz)
            ent = []
            for kind in ("entradas", "interacao"):
                for p in rec["pontos"][kind]:
                    ex, ez = V.rot(p["pos_u"][0], p["pos_u"][2], r)
                    ent.append(reach(x + ex, z + ez))
            ov = overhead(stall_vox, vs, sx, sz)
            ok = clear and talk_free and los and reach(tx, tz) and all(ent) and ov == 0 and dist <= TALK_RANGE
            result[cand]["local"] = [sx, sz, rr]
            result[cand]["rotacoes"].append(dict(rot=r, barraca=[x, z], npc=[round(nx, 3), round(nz, 3)], rumo=round(yaw, 1),
                                                 conversa=[round(tx, 3), round(tz, 3)], folga_u=round(pad, 2),
                                                 teto_voxels=ov, conversa_livre=talk_free, linha_livre=los,
                                                 conversa_alcancavel=reach(tx, tz), distancia_conversa_u=round(dist, 2),
                                                 balcao_e_compra=all(ent), ok=ok))
            report.append(f"{cand:14s} rot {r:3d}: folga {pad:.2f} u, teto {ov} voxels, conversa livre {talk_free}, "
                          f"linha livre {los}, alcançável {reach(tx, tz)}, distância {dist:.2f} u, balcão/compra "
                          f"{all(ent)} → {'ok' if ok else 'REPROVA'}")
            cases.append((cand, r, stall, (nx, nz, yaw), (tx, tz)))
    for cand in CANDIDATAS:
        result[cand]["ok"] = all(x["ok"] for x in result[cand]["rotacoes"])
    # conjunto do comerciante a 0° e a 37°, com o NPC na vaga de cada candidata (e o mesmo conjunto SEM NPC)
    for cand in ("sem_npc",) + tuple(CANDIDATAS):
        for rot in (0, 37):
            placed = V.expand(V.village(cat, g, [("conjunto.comerciante", (0.0, -6.0), rot)], radius=6.0))
            st = next(p for p in placed if p[0] == "market_stall")
            extra, talk = [], None
            if cand != "sem_npc":
                sx, sz, rr = result[cand]["local"]
                px, pz = V.rot(sx, sz, st[3])
                extra = [("circ", (st[1] + px, st[2] + pz, NPC_R))]
                tx, tz = V.rot(*talk_point(cat, cand, sx, sz, rr), st[3])
            reach = bfs(V.obstacles(placed, cat, man, prof) + extra, g, (0.6, 3.0))
            tot, bad = 0, []
            for (pn, px_, pz_, pr) in placed:
                prec = cat.get(pn) or cat.get(pn.split("__")[0])
                if prec is None or "pontos" not in prec:
                    continue
                for kind in ("entradas", "interacao"):
                    for p in prec["pontos"][kind]:
                        ex, ez = V.rot(p["pos_u"][0], p["pos_u"][2], pr)
                        tot += 1
                        if not reach(px_ + ex, pz_ + ez):
                            bad.append(f"{pn}/{p['id']}")
            if cand != "sem_npc":
                talk = reach(st[1] + tx, st[2] + tz)
                result[cand].setdefault("conjunto", []).append(dict(rot=rot, alvos=tot, inalcancaveis=bad,
                                                                    conversa_alcancavel=talk))
            report.append(f"{cand:14s} conjunto do comerciante a {rot}°: {tot - len(bad)}/{tot} alvos"
                          f"{', conversa alcançável ' + str(talk) if talk is not None else ''}"
                          f"{' — inalcançável: ' + ', '.join(bad) if bad else ''}")
    if out_dir:
        shots(cases, result, out_dir, report)
        videos(cases, out_dir)
    path = os.path.join(V.ROOT, "output", "conteudo", "vaga-feirante.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(result, open(path, "w"), indent=1, ensure_ascii=False)
    if out_dir:
        json.dump(result, open(os.path.join(out_dir, "vaga-feirante.json"), "w"), indent=1, ensure_ascii=False)
        open(os.path.join(out_dir, "vaga-feirante.txt"), "w").write("\n".join(report) + "\n")
    print("\n".join(report))


def shots(cases, result, out_dir, report):
    """Câmera de jogo (herói no ponto de conversa, olhando o NPC; 4,6 u, 17°) em todas as rotações; frente, perfil e
    fim de tarde a 0°. Visibilidade: a mesma tomada com a cena SEM o NPC; pixels diferentes = o Quelmo e a sombra dele."""
    from PIL import Image, ImageChops
    os.makedirs(out_dir, exist_ok=True)
    specs, pairs, tmp = [], [], []
    scenes = os.path.join(V.ROOT, "assets", "scenes")
    for (cand, r, stall, npc, (tx, tz)) in cases:
        with_npc, without = f"tmp_feirante_{cand}_{r}", f"tmp_feirante_{cand}_{r}_sem"
        for name, doc in ((with_npc, scene_doc(cand, stall, npc)), (without, scene_doc(cand, stall, None))):
            tmp.append(os.path.join(scenes, name + ".json"))
            json.dump(doc, open(tmp[-1], "w"))
        hy = math.degrees(math.atan2(npc[0] - tx, npc[1] - tz)) % 360       # herói olha o NPC
        cam = (hy + 180) % 360
        base = f"mode=scene,style=e,nohud=1,size=1280x720,hx={tx:.3f},hz={tz:.3f},hyaw={hy:.1f}"
        jogo = f"{base},yaw={cam:.1f},pitch=17,dist=4.6,lift=0.9"
        a = os.path.join(out_dir, f"{cand}-rot{r:03d}-jogo.png")
        b = os.path.join(out_dir, f"tmp-sem-{cand}-{r}.png")
        specs += [f"{jogo},layout={with_npc},out={a}", f"{jogo},layout={without},out={b}"]
        pairs.append((cand, r, a, b))
        if r == 0:
            front = (stall[3]) % 360                                    # câmera do lado da frente da barraca
            specs += [f"{base},layout={with_npc},yaw={front},pitch=8,dist=3.6,lift=0.75,"
                      f"out={os.path.join(out_dir, f'{cand}-rot000-frente.png')}",
                      f"{base},layout={with_npc},yaw={(front + 90) % 360},pitch=8,dist=3.6,lift=0.75,"
                      f"out={os.path.join(out_dir, f'{cand}-rot000-perfil.png')}",
                      f"{jogo},layout={with_npc},preset=tarde,out={os.path.join(out_dir, f'{cand}-rot000-jogo-tarde.png')}"]
    try:
        subprocess.run([os.path.join(V.ROOT, "tools", "snap.sh"), *specs], check=True, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)
    finally:
        for t in tmp:                                                   # cenas de medida: temporárias
            os.remove(t)
    for (cand, r, a, b) in pairs:
        d = ImageChops.difference(Image.open(a).convert("RGB"), Image.open(b).convert("RGB")).convert("L")
        px = sum(1 for v in d.getdata() if v > 24)                     # o NPC e a sombra dele
        next(x for x in result[cand]["rotacoes"] if x["rot"] == r)["pixels_visiveis"] = px
        report.append(f"{cand:14s} rot {r:3d}: Quelmo (com a sombra) visível em {px} pixels na câmera de jogo (1280×720)")
        os.remove(b)


def videos(cases, out_dir):
    """Gesto de ofício e aceno do Quelmo na vaga escolhida (barraca a 0°), com o herói na distância de conversa: vista
    de 3/4 da frente (o herói não tapa o gesto) e câmera de jogo; prancha com quatro instantes do gesto de ofício."""
    from PIL import Image  # noqa: F401  (prancha.py usa o PIL do .venv)
    cand, r, stall, npc, (tx, tz) = next(c for c in cases if c[0] == "lado_direito" and c[1] == 0)
    hy = math.degrees(math.atan2(npc[0] - tx, npc[1] - tz)) % 360
    tmp = []
    try:
        for clip, label in (("attack", "oficio"), ("emote", "aceno")):
            doc = scene_doc(cand, stall, npc)
            doc["npcs"][0]["clip"] = clip
            name = f"tmp_feirante_video_{clip}"
            tmp.append(os.path.join(V.ROOT, "assets", "scenes", name + ".json"))
            json.dump(doc, open(tmp[-1], "w"))
            for tag, yaw, pitch, dist, lift in (("frente", 40, 8, 3.0, 0.75), ("jogo", (hy + 180) % 360, 17, 4.6, 0.9)):
                spec = (f"mode=scene,style=e,nohud=1,size=1280x720,layout={name},hx={tx:.3f},hz={tz:.3f},hyaw={hy:.1f},"
                        f"yaw={yaw:.1f},pitch={pitch},dist={dist},lift={lift}")
                subprocess.run([os.path.join(V.ROOT, "tools", "anim.sh"), os.path.join(out_dir, f"video-{label}-{cand}-{tag}"),
                                spec, "110", "30"], check=True, stdout=subprocess.DEVNULL)
    finally:
        for t in tmp:
            os.remove(t)
    frames = []
    for t in (0.4, 1.2, 2.2, 2.8):
        f = os.path.join(out_dir, f"tmp-quadro-{t}.png")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", str(t), "-i",
                        os.path.join(out_dir, f"video-oficio-{cand}-frente.mp4"), "-frames:v", "1", f], check=True)
        frames.append(f)
    labels = ("0,4 s", "1,2 s (ajeita a mercadoria)", "2,2 s (oferece)", "2,8 s")
    subprocess.run([sys.executable, os.path.join(HERE, "prancha.py"), os.path.join(out_dir, "gesto-oficio-na-vaga-nova.png"),
                    "4", "480", *[f"{f}:{lab}" for f, lab in zip(frames, labels)]], check=True, stdout=subprocess.DEVNULL)
    for f in frames:
        os.remove(f)


if __name__ == "__main__":
    main()
