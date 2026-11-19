#!/usr/bin/env python3
"""NPCs de ofício da direção E: Bramor, Teodor, Pluma, Ondina e Lunara.

O esqueleto e o rosto são os dos NPCs da vila (`npcs_vila_e.npc`). Cada um ganha a própria roupa, os detalhes de rosto
(barba, óculos, asinhas, halo de cachos, chapéu) e uma ALTURA NARRATIVA. A escala do mundo é 0,75 u por metro
narrativo: Bramor 2,30 m, Teodor 1,80 m e Pluma 1,60 m mantêm entre si a relação 2,30 : 1,80 : 1,60.

A altura vem de reamostrar o rig pronto (`scale_vox`), e não de alongar o corpo. Por isso a proporção em cabeças
continua na faixa de NPC (2,0–3,0). Os limites do contrato que dependem do tamanho (faces e profundidade do tronco)
escalam com o rig e ficam no `meta.escala` (ver `OficiosNpcTest`).

Saída: assets/characters/npc/<chave>/ (gerado; não edite à mão).
"""
from pathlib import Path

import chars_b as B
import charlib_e as L
import npcs_vila_e as T
from voxlib import C, Rig, Vox, exposed_faces

P = B.P
VS = 1 / 40
UNITS_PER_METER = 0.75

PROFILES = {
    'bramor': dict(title='Bramor (NPC, E)', meters=2.30, role='guardiao_de_trilhas',
        skin=P('#c99376'), hair=P('#3b3032'), tip=None, coat=P('#e2d6bc'), coat_d=P('#bcae96'), shirt=P('#f1e9d4'),
        vest=P('#d4c6a6'), pants=P('#473c44'), boot=P('#3b323b'), cuff=P('#625156'), iris=C('#55423c'), ink=C('#30292b'),
        lip=C('#9e665f'), lip_d=C('#694641'), collar='high', belt='sash', charm=None, tails=None, hair_style='long',
        hair_bottom=13, crown=1.4, flare=0.5, fringe='curtain', front_bottom=14, act_style='confident', freckles=False,
        ears=False, ribbon=None, acting={'sway': 0.6},
        note='Guardião de trilhas muito alto; cabelo longo, barba cheia, óculos e camiseta de viagem clara',
        beard='full', glasses='rect'),
    'teodor': dict(title='Teodor (NPC, E)', meters=1.80, role='estudioso',
        skin=P('#d2a080'), hair=P('#423333'), tip=None, coat=P('#343b46'), coat_d=P('#262d35'), shirt=P('#d2d5d4'),
        vest=P('#313b47'), pants=P('#55505b'), boot=P('#34343d'), cuff=P('#727783'), iris=C('#57453b'), ink=C('#2c292e'),
        lip=C('#976966'), lip_d=C('#614845'), collar='high', belt='sash', charm=None, tails=None, hair_style='short',
        hair_bottom=T.NECK + 5, crown=1.5, flare=0, fringe='swept', front_bottom=T.NECK, act_style='confident',
        freckles=False, ears=False, ribbon=None, acting={'headRoll': -3},
        note='Estudioso de casaco escuro aberto sobre camisa clara; barba curta e óculos redondos',
        beard='trim', glasses='round'),
    'pluma': dict(title='Pluma (NPC, E)', meters=1.60, role='cronista',
        skin=P('#c59376'), hair=P('#2e2a34'), tip=None, coat=P('#35343d'), coat_d=P('#27262f'), shirt=P('#ded6ca'),
        vest=P('#45434e'), pants=P('#343540'), boot=P('#34313a'), cuff=P('#55505b'), iris=C('#52647b'), ink=C('#292733'),
        lip=C('#98645a'), lip_d=C('#60433d'), collar='high', belt='sash', charm=None, tails=None, hair_style='short',
        hair_bottom=T.NECK + 3, crown=2.1, flare=0, fringe='swept', front_bottom=T.NECK, act_style='playful',
        freckles=False, ears=False, ribbon=None, acting={'headRoll': 5, 'sway': 1.2},
        note='Cronista compacto; cabelo desalinhado, óculos, cavanhaque e asinhas de pena nas têmporas',
        beard='goatee', glasses='rect'),
    'ondina': dict(title='Ondina (NPC, E)', meters=1.70, role='guia_costeira',
        skin=P('#b98168'), hair=P('#2c2833'), tip=None, coat=P('#e7ddc3'), coat_d=P('#b9aa8e'), shirt=P('#e8d8b9'),
        vest=P('#245a83'), pants=P('#416d88'), boot=P('#836e56'), cuff=P('#d0b792'), iris=C('#403b39'), ink=C('#26242b'),
        lip=C('#a96060'), lip_d=C('#704548'), collar='round', belt='sash', charm=None, tails=None, hair_style='long',
        hair_bottom=15, crown=1.4, flare=0.7, fringe='curtain', front_bottom=16, act_style='sweet', freckles=False,
        ears=False, ribbon=None, acting={'sway': 1.0},
        note='Guia costeira; cabelo longo, chapéu de aba, roupa leve de praia e sandálias', hat=True),
    'lunara': dict(title='Lunara (NPC, E)', meters=1.66, role='cantora_noturna',
        skin=P('#c38e75'), hair=P('#30272e'), tip=None, coat=P('#533b52'), coat_d=P('#352b3c'), shirt=P('#d4b98c'),
        vest=P('#dfd2bd'), pants=P('#493146'), boot=P('#80634f'), cuff=P('#9a5d73'), iris=C('#4b4850'), ink=C('#292530'),
        lip=C('#a45467'), lip_d=C('#6a3545'), collar='round', belt='sash', charm=None, tails='short',
        hair_style='short', hair_bottom=T.NECK + 7, crown=1.5, flare=0, fringe='curtain', front_bottom=T.NECK,
        act_style='confident', freckles=False, ears=False, ribbon=None, acting={'headRoll': -5, 'sway': 0.9},
        note='Cantora noturna; halo de cachos, óculos finos e vestido longo de linho com faixa ameixa',
        afro=True, glasses='thin'),
}


def add_face_details(v, s):
    """Barba e óculos pintados na placa do rosto. A barba não cobre a boca (y = NECK+1 e NECK+2): sem isso, com o bigode
    logo acima, a boca neutra lia como uma boca aberta."""
    hair = B.shadow(s['hair'], 1.2)
    eye, n = T.EYE_Y, T.NECK
    if s.get('beard') == 'full':                                   # cheia: queixo, laterais até as bochechas, bigode fino
        for x in range(-5, 5):
            T.decal(v, x, n, hair)
            if abs(x + 0.5) >= 3.0:
                for y in range(n + 1, n + 4):
                    T.decal(v, x, y, hair)
        for x in (-3, 2):
            T.decal(v, x, n + 3, hair)
    elif s.get('beard') == 'trim':                                 # curta: queixo e laterais baixas, sem bigode
        for x in range(-4, 4):
            T.decal(v, x, n, hair)
            if abs(x + 0.5) > 2.5:
                T.decal(v, x, n + 1, hair)
    elif s.get('beard') == 'goatee':
        for x in (-2, -1, 0, 1):
            T.decal(v, x, n, hair)
        for x in (-1, 0):
            T.decal(v, x, n - 1, hair)
    if s.get('glasses'):
        kind = s['glasses']
        frame = C('#8a716d', 'metal') if kind != 'thin' else C('#9d827b', 'metal')
        for lo, hi in ((-6, -1), (0, 5)):
            if kind == 'round':
                for x in range(lo + 1, hi):
                    T.decal(v, x, eye + 4, frame)
                    T.decal(v, x, eye + 1, frame)
                for y in (eye + 2, eye + 3):
                    T.decal(v, lo, y, frame)
                    T.decal(v, hi, y, frame)
            elif kind == 'thin':                                   # finos e baixos: a linha de cima em eye+3 deixa a
                for x in range(lo, hi + 1):                            # sobrancelha (eye+4/+5) livre para atuar
                    T.decal(v, x, eye + 3, frame)
                T.decal(v, lo, eye + 2, frame)
                T.decal(v, hi, eye + 2, frame)
            else:
                for x in range(lo, hi + 1):
                    T.decal(v, x, eye + 4, frame)
                for y in (eye + 2, eye + 3):
                    T.decal(v, lo, y, frame)
                    T.decal(v, hi, y, frame)
        for x in (-1, 0):
            T.decal(v, x, eye + (2 if kind == 'thin' else 3), frame)


def add_afro(v, s):
    """Halo de cachos em grande volume, com a frente do rosto livre."""
    hair = s['hair']
    halo = Vox()
    B.sblob(halo, 0, T.NECK + 11, -1.8, 8.8, 7.6, 8.2, hair, p=2.35)
    B.sblob(halo, 0, T.NECK + 20, -2, 6.0, 4.2, 5.4, hair, p=2.3)
    for cx, cy, cz, rr in [(-10, 41, -1, 3.0), (-9, 46, -1, 3.2), (-6, 50, -2, 3.0), (-2, 52, -2, 3.0), (3, 52, -2, 3.0),
                           (7, 49, -1, 3.0), (10, 45, -1, 3.1), (11, 40, -1, 3.0), (-10, 36, -2, 2.8), (10, 36, -2, 2.8)]:
        B.sblob(halo, cx, cy, cz, rr, rr, rr, hair, p=2.2)
    for cx, cy in ((-4, T.NECK + 13), (-1, T.NECK + 14), (2, T.NECK + 13), (5, T.NECK + 12)):
        B.sblob(halo, cx, cy, 3.3, 1.7, 1.8, 1.8, hair, p=2.2)
    for k in [k for k in halo.v if k[2] >= T.FACE_Z - 1 and T.NECK + 1 <= k[1] <= T.NECK + 11 and -7 <= k[0] <= 6]:
        del halo.v[k]
    L.shade(halo)
    v.merge(halo, overwrite=False)


def add_hat(rig):
    """Chapéu claro de aba larga, assentado na coroa do cabelo e com a copa vincada."""
    hat = Vox()
    felt = P('#e5dfd0')
    underside = C('#c5beb0')
    band = C('#383b3d')
    y = T.NECK + 13                                                # a aba atravessa de leve o cabelo: sem faixa de ar
    B.sblob(hat, 0, y + 0.5, -0.2, 11.0, 1.55, 8.3, felt, p=2.55)
    B.sblob(hat, 0, y + 2.8, -0.4, 6.4, 4.5, 5.3, felt, p=2.4)
    for (x, yy, z), c in list(hat.v.items()):                     # sombra sob a aba, sem virar um aro preto
        if yy <= y + 1 and (abs(x) >= 7 or abs(z) >= 5):
            hat.v[(x, yy, z)] = underside
    for (x, yy, z), c in list(hat.v.items()):                     # faixa estreita na base da copa
        if yy == y + 2 and abs(x) <= 6 and abs(z) <= 5:
            if any((x + dx, yy, z + dz) not in hat.v for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                hat.v[(x, yy, z)] = band
    top = max(k[1] for k in hat.v)                                 # vinco no topo da copa
    for k in [k for k in hat.v if k[1] == top and abs(k[0]) <= 1 and -2 <= k[2] <= 2]:
        del hat.v[k]
    L.shade(hat)
    rig.part('hat', hat, (0, y, 0), 'head')


def dress_lunara(body, s):
    """Vestido longo de linho que VESTE o tronco inteiro (frente, lados e costas), com faixa ameixa contínua na cintura
    e broche dourado no ombro. O tronco continua uma massa única (nenhum volume extra na frente) e o colo é redondo e
    alto. Pintar só a frente deixava as costas com a cor do tronco base: de costas, o vestido sumia."""
    cream = s['vest']
    wine = s['coat']
    gold = C('#c5a36c', 'metal')
    fold = C('#c9bba3')
    waist = (T.HIP + 4, T.HIP + 5)
    for (x, y, z), c in list(body.v.items()):                     # linho em volta de todo o tronco, da barra ao colo
        if 10 <= y <= T.NECK - 2:
            body.v[(x, y, z)] = wine if y in waist else cream
    L.shade(body, only={cream, wine})
    for y in range(11, T.HIP + 3, 3):                              # dobras da saia, ritmo regular (só abaixo da faixa)
        T._front_paint(body, [(-3, y), (2, y)], fold)
    T._front_paint(body, [(-1, T.NECK - 1), (0, T.NECK - 1)], s['skin'])   # colo redondo e alto
    T._front_paint(body, [(-4, T.NECK - 3), (-3, T.NECK - 3)], gold, out=1)   # broche no ombro


def dress_ondina(rig, body, s):
    """Roupa costeira leve: top azul de colo reto, camisa de linho aberta, short e sandálias."""
    skin = s['skin']
    blue = s['vest']
    short = s['pants']
    linen = s['coat']
    for (x, y, z), c in list(body.v.items()):                     # tira o painel de avental: top, cintura e short
        if z >= 2 and -4 <= x <= 3:
            if 17 <= y <= 21:
                body.v[(x, y, z)] = skin
            elif 10 <= y <= 16:
                body.v[(x, y, z)] = short
            elif 22 <= y <= 26:
                body.v[(x, y, z)] = blue
            elif 27 <= y <= 28:
                body.v[(x, y, z)] = skin
    for y in range(17, 29):
        T._front_paint(body, [(-5, y), (4, y)], linen)
    for y in range(21, 28):
        T._front_paint(body, [(-4, y), (3, y)], linen, out=1)
    for y in (27, 28):                                             # alças do top (colo reto, sem decote)
        T._front_paint(body, [(-3, y), (2, y)], blue)
    T._front_paint(body, [(x, 16) for x in range(-4, 4)], C('#315774'))   # cós e costura do short
    T._front_paint(body, [(-1, y) for y in range(12, 16)], C('#315774'))
    T._front_paint(body, [(x, 12) for x in (-4, -3, -2, 1, 2, 3)], C('#315774'))
    for n, v, p, par in rig.parts:                                 # mangas curtas, pernas de fora, sandálias baixas
        if n in ('arm_l', 'arm_r', 'farm_l', 'farm_r'):
            for pos, c in list(v.v.items()):
                if pos[1] < 24:
                    v.v[pos] = skin
        elif n in ('leg_l', 'leg_r', 'shin_l', 'shin_r'):
            for (x, y, z), c in list(v.v.items()):
                if y >= 12:
                    v.v[(x, y, z)] = short
                elif y == 11:
                    v.v[(x, y, z)] = C('#315774')
                elif y >= 3:
                    v.v[(x, y, z)] = skin
                elif y >= 1:
                    v.v[(x, y, z)] = s['boot'] if y == 2 and 1 <= z <= 3 else skin
                else:
                    v.v[(x, y, z)] = s['boot']


def dress_bramor(rig, body, s):
    """Camiseta de viagem clara com um emblema abstrato de trilha e horizonte; mangas curtas."""
    for (x, y, z), c in list(body.v.items()):
        if y <= 13:
            body.v[(x, y, z)] = s['pants']
    sunset = C('#c67d66')
    trail = C('#567b91')
    sand = C('#d8b27f')
    T._front_paint(body, [(x, y) for x in range(-3, 3) for y in (20, 21)], sunset)
    T._front_paint(body, [(x, 19) for x in range(-3, 3)], sand)
    T._front_paint(body, [(-2, 18), (-1, 18), (0, 18), (1, 17), (2, 16)], trail)
    for n, v, p, par in rig.parts:
        if n in ('arm_l', 'arm_r', 'farm_l', 'farm_r'):
            for pos, c in list(v.v.items()):
                if pos[1] < 23:
                    v.v[pos] = s['skin']


def dress_teodor(body, s):
    """Casaco de estudioso aberto sobre camisa clara e estreita, com uma presilha dourada."""
    for (x, y, z), c in list(body.v.items()):
        if y <= 13:
            body.v[(x, y, z)] = s['pants']
    T._front_paint(body, [(x, y) for x in range(-2, 2) for y in range(17, 27)], s['shirt'])
    for y in range(19, 27):
        T._front_paint(body, [(-3, y), (2, y)], s['coat_d'], out=1)
    T._front_paint(body, [(-1, y) for y in range(20, 25)], C('#48576c'))
    T._front_paint(body, [(-1, 26), (0, 26)], C('#c2a774', 'metal'), out=1)


def dress_pluma(rig, body, s):
    """Camiseta escura curta com marcas claras e cabelo desarrumado, mantendo a escala baixa."""
    for (x, y, z), c in list(body.v.items()):
        if y <= 14:
            body.v[(x, y, z)] = s['pants']
    cream = C('#d3c8b8')
    T._front_paint(body, [(-3, 23), (-2, 23), (1, 21), (2, 21), (-1, 18), (0, 18)], cream)
    for n, v, p, par in rig.parts:
        if n in ('arm_l', 'arm_r', 'farm_l', 'farm_r'):
            for pos, c in list(v.v.items()):
                if pos[1] < 23:
                    v.v[pos] = s['skin']


def sandals(rig, s):
    for n, v, p, par in rig.parts:
        if n not in ('leg_l', 'leg_r', 'shin_l', 'shin_r'):
            continue
        for (x, y, z), c in list(v.v.items()):
            if 3 <= y <= 11:
                v.v[(x, y, z)] = s['skin']
            elif y == 2:
                v.v[(x, y, z)] = s['boot'] if 1 <= z <= 3 else s['skin']
            elif y == 1:
                v.v[(x, y, z)] = s['skin']
            elif y == 0:
                v.v[(x, y, z)] = s['boot']


def tousle(head, s):
    """Mechas soltas no alto da cabeça (cabelo desarrumado)."""
    for cx, cy, cz, rx, ry, rz in ((-5, T.NECK + 14, -1, 2.4, 2.0, 2.1), (3, T.NECK + 15, -2, 2.0, 2.0, 2.0),
                                    (6, T.NECK + 10, 0, 1.8, 2.6, 2.0)):
        B.sblob(head, cx, cy, cz, rx, ry, rz, s['hair'], p=2.2)


def head_wings(head):
    """Asinhas de pena nas têmporas: lóbulo alto e duas penas baixas (enfeite, não asa de voo).

    Pintadas na própria cabeça, acompanham todas as expressões sem criar duas malhas coincidentes sobre o cabelo nem
    mudar o rig de animação.
    """
    feather = C('#eee8dc')
    feather_low = C('#c6c8c4')
    root = C('#aaaeb0')
    rows = {35: (8, 9), 36: (8, 11), 37: (8, 11), 38: (9, 10), 39: (9, 12), 40: (10, 13), 41: (10, 13), 42: (11, 13),
            43: (12, 13)}
    for side in (-1, 1):
        for y, (lo, hi) in rows.items():
            for a in range(lo, hi + 1):
                x = a if side == 1 else -1 - a
                zs = (-1, 0, 1) if a <= 9 else (0, 1) if y <= 42 else (0,)
                for z in zs:
                    head.set(x, y, z, root if a <= 8 and z == -1 else feather_low if z <= 0 or y <= 36 else feather)
        for a in (7, 8):                                           # filete claro liga a base ao cabelo
            head.set(a if side == 1 else -1 - a, 38, 1, feather_low)


HIGHLIGHTS = {'bramor': C('#65575a'), 'teodor': C('#594b4d'), 'pluma': C('#4a464e'), 'ondina': C('#514b55'),
              'lunara': C('#584b51')}


def soften_hair_highlight(head, s, key):
    """A rampa do cabelo escuro clareia o topo até parecer um segundo chapéu: troca o realce por um tom mais baixo."""
    original = B.RAMPS[s['hair']][1]
    for pos, col in list(head.v.items()):
        if col == original:
            head.v[pos] = HIGHLIGHTS[key]


def scale_vox(v, scale):
    """Reamostra a grade: cada voxel de origem vira o bloco [round(i·s), round((i+1)·s)) em cada eixo."""
    out = Vox()
    for (x, y, z), c in v.v.items():
        xr = range(round(x * scale), max(round((x + 1) * scale), round(x * scale) + 1))
        yr = range(round(y * scale), max(round((y + 1) * scale), round(y * scale) + 1))
        zr = range(round(z * scale), max(round((z + 1) * scale), round(z * scale) + 1))
        for xx in xr:
            for yy in yr:
                for zz in zr:
                    out.set(xx, yy, zz, c)
    return out


def build_one(key, out):
    s = PROFILES[key]
    spec = {k: v for k, v in s.items() if k not in ('meters', 'beard', 'glasses', 'hat', 'afro')}
    rig = T.npc('suvia', **spec)
    heads = [v for n, v, p, par in rig.parts if n == 'head'] + list(rig.alts['head'].values())
    for head in heads:                                             # detalhes em todas as expressões
        if s.get('afro'):
            add_afro(head, s)
            jewel = C('#c8a675', 'metal')
            head.set(7, T.NECK + 3, 0, jewel)
            head.set(-8, T.NECK + 3, 0, jewel)
        if key == 'pluma':
            tousle(head, s)
            head_wings(head)
        add_face_details(head, s)
        soften_hair_highlight(head, s, key)
    body = next(v for n, v, p, par in rig.parts if n == 'torso')
    for x in (-1, 0):                                              # tira o enfeite de colo do esqueleto base
        for y in (T.NECK - 8, T.NECK - 7):
            body.v.pop((x, y, 4), None)
    if key == 'bramor':
        dress_bramor(rig, body, s)
    elif key == 'teodor':
        dress_teodor(body, s)
    elif key == 'pluma':
        dress_pluma(rig, body, s)
    elif key == 'ondina':
        dress_ondina(rig, body, s)
        T._front_paint(body, [(-1, T.NECK - 3), (0, T.NECK - 3)], C('#e4d5ac', 'metal'), out=1)
    elif key == 'lunara':
        dress_lunara(body, s)
        sandals(rig, s)
        for n, v, p, par in rig.parts:                             # braços de fora: o vestido difere dos casacos
            if n in ('arm_l', 'arm_r', 'farm_l', 'farm_r'):
                for pos, c in list(v.v.items()):
                    if pos[1] >= 18:
                        v.v[pos] = s['skin']
                L.shade(v)
    if s.get('hat'):
        add_hat(rig)
    base_h = rig.all_voxels().bounds()[4] + 1
    scale = round(s['meters'] * UNITS_PER_METER / VS) / base_h
    scaled = Rig(s['title'], VS, 'biped')
    for n, v, p, par in rig.parts:
        alt = {k: scale_vox(a, scale) for k, a in rig.alts.get(n, {}).items()} or None
        sv = scale_vox(v, scale)
        L.prune_specks(sv, label=f'{key}/{n}')
        scaled.part(n, sv, tuple(q * scale for q in p), par, alts=alt)
    top = scaled.all_voxels().bounds()[4] + 1
    body_top = round(rig.meta['bodyHeightVoxels'] * scale)
    head_h = round(rig.meta['headH'] * scale)
    faces = sum(exposed_faces(v) for _, v, _, _ in scaled.parts)
    scaled.meta = dict(rig.meta, headsTall=round(body_top / head_h, 2), bodyHeightVoxels=body_top, headH=head_h,
                       legH=round(T.HIP * scale), torsoH=round((T.NECK - T.HIP) * scale),
                       legFraction=round(T.HIP * scale / body_top, 3), faces=faces, metersNarrative=s['meters'],
                       worldUnits=round(top * VS, 3), escala=round(scale, 4), role=s['role'], note=s['note'])
    scaled.meta['hands'] = {side: [round(q * scale, 2) for q in coords] for side, coords in rig.meta['hands'].items()}
    for vol in scaled.meta.get('volumes', []):
        vol['a'] = [round(q * scale, 2) for q in vol['a']]
        if 'b' in vol:
            vol['b'] = [round(q * scale, 2) for q in vol['b']]
        vol['r'] = round(vol['r'] * scale, 2)
    path = Path(out) / 'characters/npc' / key
    if key == 'ondina':
        for old in ('skirt_l.vox', 'skirt_r.vox'):
            (path / old).unlink(missing_ok=True)
    scaled.save(str(path))
    print(f"{key:7s} {top} voxels = {top * VS:.3f} u, {body_top / head_h:.2f} cabeças, {faces} faces, escala {scale:.3f}")
    return scaled


def build(out):
    for key in PROFILES:
        build_one(key, out)


if __name__ == '__main__':
    build(Path(__file__).resolve().parents[1] / 'assets')
