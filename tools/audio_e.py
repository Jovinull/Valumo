#!/usr/bin/env python3
"""Rodada 09.1 — efeitos sonoros do primeiro combate (direção E, "Fábula Voxel"), sintetizados aqui.

Tudo é síntese controlada e determinística (sementes fixas), sem amostras de terceiros nem geradores por IA: este
arquivo É a fonte editável de cada som. Saída: assets/audio/*.wav (PCM 16 bits mono, 48 kHz) + variantes.json (qual
arquivo é variação de qual efeito). A mistura (ganho, espacial, limites de vozes) é escrita à mão em
assets/scenes/sons_e.json.

Direção sonora: fantasia acolhedora, personagens pequenos, materiais reconhecíveis (aço, madeira do broquel, carne
macia de cogumelo), golpes com peso, reações caricaturais discretas. Nada de violência realista, nada de bip
eletrônico genérico, nada de 8 bits obrigatório.

Técnicas (todas aqui embaixo):
  - ruído filtrado com filtro de estado variável (SVF) de frequência que varia no tempo (lâmina cortando o ar);
  - síntese modal (soma de senoides amortecidas com parciais inarmônicas: madeira, aço);
  - "voz" por pulso + formantes (filtros passa-faixa em frequências de vogal: o fungo);
  - Karplus-Strong (corda dedilhada: os sinais de vitória e derrota);
  - envelopes exponenciais, saturação suave (tanh), remoção de DC e normalização de pico.

Uso: python3 tools/audio_e.py [pasta-de-saída-da-revisão]   (ou via tools/build_assets.py)
"""
import json
import math
import os
import sys
import wave

import numpy as np

SR = 48000
PEAK = 10 ** (-3 / 20)          # todos normalizados a −3 dBFS de pico; o equilíbrio fica na mistura (sons_e.json)


# ---------------------------------------------------------------------------------------------- blocos de síntese

def t_axis(dur):
    return np.arange(int(round(dur * SR))) / SR


def rng(seed):
    return np.random.default_rng(seed)


def env_ad(n, attack, decay, curve=4.0):
    """Ataque linear (s) e decaimento exponencial (s até ~e^-curve)."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-5), 0, 1)
    d = np.exp(-curve * np.clip(t - attack, 0, None) / max(decay, 1e-5))
    return a * d


def bump(n, center, width):
    """Envelope em sino (velocidade da lâmina): pico em center (s), largura width (s)."""
    t = np.arange(n) / SR
    return np.exp(-0.5 * ((t - center) / width) ** 2)


def svf(x, cutoff, q, mode="bp"):
    """Filtro de estado variável (topologia TPT, estável com corte variável). cutoff: escalar ou array (Hz)."""
    n = len(x)
    fc = np.broadcast_to(np.asarray(cutoff, dtype=float), (n,))
    g = np.tan(np.pi * np.clip(fc, 20, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    ic1 = ic2 = 0.0
    out = np.empty(n)
    for i in range(n):
        v3 = x[i] - ic2
        v1 = a1[i] * ic1 + g[i] * a1[i] * v3
        v2 = ic2 + g[i] * v1
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v1 if mode == "bp" else v2 if mode == "lp" else x[i] - k * v1 - v2
    return out


def osc(freq, n, phase=0.0):
    """Senoide com frequência variável (array ou escalar)."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.sin(2 * np.pi * (phase + np.cumsum(f) / SR))


def glottal(freq, n, limit=6000.0, tilt=1.15):
    """Fonte de 'voz' por soma de harmônicos (sem aliasing: cada harmônico some antes de passar de limit Hz),
    amplitudes caindo 1/k^tilt (mais escuro = tilt maior)."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.zeros(n)
    k = 1
    while k * np.min(f) < limit:
        fade = np.clip((limit - k * f) / (0.25 * limit), 0, 1)      # entra/sai suave conforme o tom varia
        y += np.sin(k * ph) * fade / k ** tilt
        k += 1
    return y


def dc_block(y, fc=25.0):
    """Passa-altas de 1 polo (tira DC e subgrave inaudível sem mexer no corpo do som)."""
    a = math.exp(-2 * math.pi * fc / SR)
    out = np.empty_like(y)
    px = py = 0.0
    for i, x in enumerate(y):
        py = x - px + a * py
        px = x
        out[i] = py
    return out


def modal(n, partials, seed=0):
    """Soma de modos amortecidos: [(freq Hz, decaimento s, ganho)], fase inicial aleatória fixa."""
    r = rng(seed)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for f, dec, g in partials:
        y += g * np.sin(2 * np.pi * f * t + r.uniform(0, 2 * np.pi)) * np.exp(-t / dec)
    return y


def formants(src, freqs, qs, gains):
    return sum(g * svf(src, f, q, "bp") for f, q, g in zip(freqs, qs, gains))


def pluck(freq, dur, bright=0.5, seed=0):
    """Karplus-Strong: ruído num atraso com média (corda); bright controla o amortecimento."""
    n = int(dur * SR)
    p = int(SR / freq)
    buf = rng(seed).uniform(-1, 1, p)
    buf = svf(buf, 2000 + 6000 * bright, 0.7, "lp")
    out = np.empty(n)
    damp = 0.4985 + 0.0013 * bright
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = damp * (buf[i % p] + buf[(i + 1) % p])
    return out


def gate(t, start, end):
    """Janela 1 → 0 em cosseno entre start e end (s): corta sem estalo."""
    k = np.clip((t - start) / max(end - start, 1e-6), 0, 1)
    return 0.5 + 0.5 * np.cos(np.pi * k)


def finish(y, fade_in=0.0003, fade_out=0.012):
    """DC fora (passa-altas de 25 Hz), bordas sem estalo e pico em −3 dBFS."""
    y = dc_block(y)
    n = len(y)
    fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
    y[:fi] *= np.linspace(0, 1, fi)
    y[-fo:] *= np.linspace(1, 0, fo) ** 2
    pk = np.max(np.abs(y))
    if pk > 0:
        y = y / pk
    return y * PEAK


# ---------------------------------------------------------------------------------------------- os efeitos

def whoosh(seed, dur, lo, hi, peak_at, width, low_body=0.0):
    """Lâmina cortando o ar: ruído num passa-faixa cujo centro acompanha a velocidade da lâmina (sobe e desce)."""
    n = int(dur * SR)
    r = rng(seed)
    speed = bump(n, peak_at, width)
    noise = svf(r.normal(0, 1, n), 4500, 0.7, "lp")                  # ar: sem chiado acima de ~5 kHz
    centre = lo + (hi - lo) * speed
    y = svf(svf(noise, centre, 1.6, "bp"), centre, 1.6, "bp") * speed   # duas passagens: faixa mais definida ("fuu")
    y += 0.12 * svf(noise, centre * 1.9, 5.0, "bp") * speed ** 3    # um fio de assobio só no auge
    if low_body:
        y += low_body * svf(noise, 160 + 140 * speed, 1.0, "lp") * speed * 3
    return finish(y, fade_in=0.004, fade_out=0.03)


def sword_hit(seed):
    """Aço em carne de cogumelo: estalo curto (contato) + baque grave + 'chap' úmido + leve anel do aço."""
    n = int(0.30 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    click = svf(svf(r.normal(0, 1, n), 2600, 0.8, "hp"), 7000, 0.7, "lp") * env_ad(n, 0.0002, 0.006, 5)
    thump = osc(150 * np.exp(-t * 9) + 55, n) * env_ad(n, 0.001, 0.09, 4)
    wob = 820 + 260 * np.sin(2 * np.pi * 24 * t) * np.exp(-t * 20)
    squish = svf(r.normal(0, 1, n), wob, 3.0, "bp") * env_ad(n, 0.002, 0.07, 4)
    ring = modal(n, [(2260 * r.uniform(0.97, 1.03), 0.10, 1.0), (3710, 0.07, 0.6), (5230, 0.05, 0.35)], seed)
    y = 0.9 * click + 1.0 * thump + 0.75 * squish + 0.10 * ring
    return finish(np.tanh(1.4 * y))


def shield_block(seed):
    """Broquel de madeira com aro: 'tunc' cheio (modos de madeira) + baque + o chapéu do fungo quicando (boing curto)."""
    n = int(0.32 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    s = r.uniform(0.96, 1.04)
    wood = modal(n, [(196 * s, 0.11, 1.0), (468 * s, 0.07, 0.8), (1012 * s, 0.045, 0.55), (1735 * s, 0.03, 0.35),
                     (2890 * s, 0.018, 0.25)], seed)
    rim = modal(n, [(3150 * s, 0.05, 1.0), (4420 * s, 0.035, 0.6)], seed + 1)
    thump = osc(110 * np.exp(-t * 14) + 48, n) * env_ad(n, 0.0008, 0.07, 4)
    knock = svf(r.normal(0, 1, n), 1500, 0.9, "bp") * env_ad(n, 0.0002, 0.012, 5)
    boing = osc(260 + 140 * (1 - np.exp(-t * 30)), n) * env_ad(n, 0.01, 0.10, 4) * (t > 0.02)
    y = 1.0 * wood + 0.18 * rim + 0.9 * thump + 0.6 * knock + 0.22 * boing
    return finish(np.tanh(1.2 * y))


def voice(n, pitch, vowel, breath=0.06, seed=0):
    """'Voz' pequena: pulso com o tom dado (array) filtrado por formantes de vogal."""
    vowels = {"o": ([450, 820, 2400], [5, 6, 8], [1.0, 0.55, 0.12]),
              "u": ([330, 840, 2300], [5, 6, 8], [1.0, 0.4, 0.1]),
              "i": ([300, 2250, 3000], [5, 8, 9], [0.8, 0.7, 0.3]),
              "a": ([720, 1180, 2500], [5, 6, 8], [1.0, 0.7, 0.15]),
              "m": ([260, 1200, 2400], [4, 6, 8], [1.0, 0.15, 0.05])}
    src = glottal(pitch, n) + breath * svf(rng(seed).normal(0, 1, n), 3000, 0.7, "lp")
    f, q, g = vowels[vowel]
    return formants(src, f, q, g)


def fungus_alert(seed):
    """Percebeu o herói: 'hup-hup!' curtinho e subindo (curioso/bravo, nunca monstro)."""
    n = int(0.30 * SR)
    t = np.arange(n) / SR
    s = 1 + 0.04 * (seed % 3 - 1)
    p1 = np.where(t < 0.11, 470 + 900 * t, 560 + 1700 * (t - 0.13)) * s
    second = np.concatenate([np.zeros(int(0.13 * SR)), env_ad(n - int(0.13 * SR), 0.006, 0.12, 3)])
    env = env_ad(n, 0.006, 0.08, 3) * gate(t, 0.095, 0.115) + second
    return finish(voice(n, p1, "o", seed=seed) * env, fade_out=0.02)


def fungus_windup(seed, dur=0.70):
    """Preparação da cabeçada (dura o mesmo que a preparação): resmungo que sobe e treme com o chapéu."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = t / dur
    pitch = (150 + 140 * k ** 1.6) * (1 + 0.03 * np.sin(2 * np.pi * 13 * t))      # tremor do chapéu (13 Hz)
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * 13 * t + 1.0)
    y = voice(n, pitch, "m", breath=0.12, seed=seed) * trem
    y += 0.25 * svf(rng(seed).normal(0, 1, n), 300 + 500 * k, 2.0, "bp")              # 'raspar' de pé no chão
    env = np.clip(k * 1.6, 0, 1) ** 1.5 * (0.55 + 0.45 * k)
    return finish(y * env, fade_in=0.01, fade_out=0.035)


def headbutt(seed):
    """Cabeçada saindo: 'fwump' grave e curto (o corpo todo do cogumelo se lançando)."""
    return whoosh(seed, 0.20, 160, 620, 0.07, 0.035, low_body=0.9)


def headbutt_hit(seed):
    """Cabeçada que pegou no herói: 'bonc' de desenho animado, macio (chapéu de cogumelo), sem violência."""
    n = int(0.30 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    bonk = osc(380 * np.exp(-t * 7) + 140, n) * env_ad(n, 0.001, 0.16, 4)
    body = osc(95 * np.exp(-t * 10) + 45, n) * env_ad(n, 0.001, 0.08, 4)
    pad = svf(r.normal(0, 1, n), 700, 1.2, "bp") * env_ad(n, 0.0005, 0.03, 5)
    return finish(np.tanh(1.3 * (0.8 * bonk + 0.9 * body + 0.5 * pad)))


def hero_hurt(seed):
    """Herói levou dano: 'uf!' curto e estilizado (voz pequena descendo), discreto."""
    n = int(0.20 * SR)
    t = np.arange(n) / SR
    pitch = 330 * np.exp(-t * 2.2) * (1 + 0.04 * (seed % 3 - 1))
    y = voice(n, pitch, "u", breath=0.2, seed=seed) * env_ad(n, 0.008, 0.14, 3)
    return finish(y, fade_out=0.03)


def fungus_hurt(seed):
    """Fungo atingido: 'iip!' agudo e curto + tremida do chapéu."""
    n = int(0.22 * SR)
    t = np.arange(n) / SR
    s = 1 + 0.05 * (seed % 3 - 1)
    pitch = (980 - 900 * t) * s * (1 + 0.05 * np.sin(2 * np.pi * 30 * t))
    y = voice(n, pitch, "i", seed=seed) * env_ad(n, 0.004, 0.13, 3)
    return finish(y, fade_out=0.03)


def fungus_death(seed):
    """Fungo derrotado: murcha ('piiuuu' descendo com vibrato) e solta um 'puf' de esporos."""
    n = int(1.0 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    pitch = (620 * np.exp(-t * 2.4) + 120) * (1 + 0.05 * np.sin(2 * np.pi * 7 * t))
    v = voice(n, pitch, "o", breath=0.1, seed=seed) * env_ad(n, 0.01, 0.6, 3) * gate(t, 0.62, 0.74)
    puff_env = np.where(t > 0.62, env_ad(n, 0.004, 0.25, 4)[np.clip(((t - 0.62) * SR).astype(int), 0, n - 1)], 0)
    puff = svf(r.normal(0, 1, n), 900, 0.8, "lp") * puff_env
    return finish(0.9 * v + 0.6 * puff, fade_out=0.05)


def jingle(notes, step, dur, bright, seed, tail=0.5):
    """Notas dedilhadas (Karplus-Strong) em sequência: (semitons a partir de C5)."""
    n = int((step * (len(notes) - 1) + dur + tail) * SR)
    y = np.zeros(n)
    for i, st in enumerate(notes):
        f = 523.25 * 2 ** (st / 12)
        s = svf(pluck(f, dur + tail, bright, seed + i), 3800, 0.7, "lp")
        s += 0.35 * pluck(f * 2, dur + tail, bright, seed + 10 + i) * np.exp(-np.arange(len(s)) / SR * 6)
        s *= gate(np.arange(len(s)) / SR, 0.6 * len(s) / SR, len(s) / SR)   # cada nota some suave (sem estalo no fim)
        a = int(i * step * SR)
        y[a:a + len(s)] += s[:n - a]
    return finish(y, fade_out=0.15)


def footstep(seed, surface):
    """Passo (escopo secundário): terra/grama — batida macia + raspar curto."""
    n = int(0.12 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    thud = osc(90 + 40 * r.uniform(), n) * env_ad(n, 0.002, 0.03, 4)
    cut = 1400 if surface == "grama" else 800
    scrape = svf(svf(r.normal(0, 1, n), cut * r.uniform(0.85, 1.15), 1.2, "bp"), 3000, 0.7, "lp") * env_ad(n, 0.003, 0.05, 4)
    return finish(1.0 * thud + (0.8 if surface == "grama" else 0.6) * scrape)


# ---------------------------------------------------------------------------------------------- rodada 09.2

def roll(seed):
    """Rolamento: corpo e roupa girando no ar (sopro grave que cresce e some), ombro tocando o chão e pés pousando."""
    n = int(0.52 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    noise = svf(r.normal(0, 1, n), 3200, 0.7, "lp")
    spin = bump(n, 0.22, 0.09)
    centre = 260 + 520 * spin
    body = svf(svf(noise, centre, 1.3, "bp"), centre, 1.3, "bp") * spin
    def thud(at, f0, amt):
        k = np.clip((t - at) / 0.001, 0, None)
        e = np.where(t >= at, np.exp(-(t - at) / 0.045), 0.0) * np.minimum(k, 1.0)
        return amt * osc(f0 * np.exp(-np.clip(t - at, 0, None) * 8) + 45, n) * e
    shoulder = thud(0.11, 120 + 12 * r.uniform(), 0.55)
    feet = thud(0.41, 105 + 10 * r.uniform(), 0.75)
    scrape = svf(svf(r.normal(0, 1, n), 900 * r.uniform(0.9, 1.1), 1.2, "bp"), 2800, 0.7, "lp")
    scrape *= 0.35 * (bump(n, 0.13, 0.03) + bump(n, 0.43, 0.035))
    return finish(1.0 * body + shoulder + feet + scrape, fade_in=0.004, fade_out=0.03)


def backstep(seed):
    """Recuo: sopro curto (o corpo saltando para trás) e um pouso firme."""
    n = int(0.34 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    noise = svf(r.normal(0, 1, n), 3000, 0.7, "lp")
    sp = bump(n, 0.11, 0.05)
    body = svf(noise, 300 + 500 * sp, 1.2, "bp") * sp
    land = osc(110 * np.exp(-np.clip(t - 0.25, 0, None) * 9) + 50, n) * np.where(t >= 0.25, np.exp(-(t - 0.25) / 0.04), 0.0)
    scrape = svf(svf(r.normal(0, 1, n), 850, 1.2, "bp"), 2800, 0.7, "lp") * 0.4 * bump(n, 0.26, 0.025)
    return finish(0.9 * body + 0.8 * land + scrape, fade_in=0.004, fade_out=0.03)


def jump(seed):
    """Salto: impulso curto (raspar do pé saindo do chão) e um sopro leve de roupa — sem voz."""
    n = int(0.20 * SR)
    r = rng(seed)
    push = svf(svf(r.normal(0, 1, n), 1100 * r.uniform(0.9, 1.1), 1.3, "bp"), 3200, 0.7, "lp") * env_ad(n, 0.002, 0.04, 4)
    sp = bump(n, 0.07, 0.035)
    air = svf(svf(r.normal(0, 1, n), 2600, 0.7, "lp"), 500 + 700 * sp, 1.3, "bp") * sp
    return finish(0.7 * push + 0.8 * air, fade_out=0.03)


def land(seed):
    """Pouso: o passo mais pesado — baque grave com mais corpo e o raspar de terra."""
    n = int(0.16 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    thud = osc(85 + 25 * r.uniform() + 60 * np.exp(-t * 30), n) * env_ad(n, 0.0015, 0.05, 4)
    scrape = svf(svf(r.normal(0, 1, n), 800 * r.uniform(0.85, 1.15), 1.2, "bp"), 3000, 0.7, "lp") * env_ad(n, 0.003, 0.06, 4)
    return finish(1.0 * thud + 0.55 * scrape)


def crumble(seed):
    """O fungo se desfaz em blocos: chuva de estalinhos secos e baixos (pedacinhos caindo), cada vez mais esparsos,
    com um sopro de esporos por baixo. Sem música, sem explosão."""
    n = int(0.75 * SR)
    r = rng(seed)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for k in range(34):
        at = 0.6 * (r.uniform() ** 1.7)                                  # mais no começo
        i0 = int(at * SR)
        m = int(0.018 * SR)
        if i0 + m >= n:
            continue
        f = r.uniform(900, 2400) * (1.0 - 0.35 * at / 0.6)
        g = 0.25 + 0.75 * r.uniform()
        tick = svf(r.normal(0, 1, m), f, 4.0, "bp") * env_ad(m, 0.0004, 0.006, 5)
        y[i0:i0 + m] += g * tick * (1.0 - 0.6 * at / 0.6)
        if r.uniform() < 0.35:                                          # alguns batem no chão (mais grave)
            bm = int(0.04 * SR)
            if i0 + bm < n:
                y[i0:i0 + bm] += 0.35 * osc(np.full(bm, 160 + 80 * r.uniform()), bm) * env_ad(bm, 0.001, 0.015, 4)
    puff = svf(svf(r.normal(0, 1, n), 1800, 0.8, "lp"), 700, 1.0, "bp") * env_ad(n, 0.02, 0.35, 3)
    return finish(np.tanh(1.2 * (y + 0.45 * puff)), fade_out=0.06)


# ---------------------------------------------------------------------------------------------- catálogo

def catalog():
    """Efeito → lista de (arquivo, função que gera). Variações = sementes/parâmetros diferentes."""
    c = {}
    c["golpe_ar_h"] = [(f"golpe_ar_h_{i}", lambda i=i: whoosh(100 + i, 0.24, 520 * (1 + 0.06 * (i - 1)), 2500, 0.075, 0.03)) for i in range(3)]
    c["golpe_ar_d"] = [(f"golpe_ar_d_{i}", lambda i=i: whoosh(110 + i, 0.26, 440 * (1 + 0.06 * (i - 1)), 2100, 0.085, 0.035, 0.25)) for i in range(3)]
    c["golpe_ar_v"] = [(f"golpe_ar_v_{i}", lambda i=i: whoosh(120 + i, 0.28, 330 * (1 + 0.06 * (i - 1)), 1700, 0.095, 0.04, 0.5)) for i in range(3)]
    c["golpe_acerto"] = [(f"golpe_acerto_{i}", lambda i=i: sword_hit(200 + i)) for i in range(3)]
    c["escudo_bloqueio"] = [(f"escudo_bloqueio_{i}", lambda i=i: shield_block(300 + i)) for i in range(2)]
    c["fungo_alerta"] = [(f"fungo_alerta_{i}", lambda i=i: fungus_alert(400 + i)) for i in range(2)]
    c["fungo_preparo"] = [("fungo_preparo_0", lambda: fungus_windup(500))]
    c["fungo_cabecada"] = [(f"fungo_cabecada_{i}", lambda i=i: headbutt(600 + i)) for i in range(2)]
    c["cabecada_acerto"] = [(f"cabecada_acerto_{i}", lambda i=i: headbutt_hit(700 + i)) for i in range(2)]
    c["heroi_dano"] = [(f"heroi_dano_{i}", lambda i=i: hero_hurt(800 + i)) for i in range(2)]
    c["fungo_dano"] = [(f"fungo_dano_{i}", lambda i=i: fungus_hurt(900 + i)) for i in range(3)]
    c["fungo_derrota"] = [("fungo_derrota_0", lambda: fungus_death(1000))]
    c["vitoria"] = [("vitoria_0", lambda: jingle([0, 4, 7, 12], 0.12, 0.35, 0.65, 1100, tail=0.6))]
    c["derrota"] = [("derrota_0", lambda: jingle([7, 3, -2, -5], 0.22, 0.45, 0.25, 1200, tail=0.7))]
    c["passo_terra"] = [(f"passo_terra_{i}", lambda i=i: footstep(1300 + i, "terra")) for i in range(4)]
    # rodada 09.2: esquiva, salto, pouso e a morte em fragmentos
    c["esquiva_rolar"] = [(f"esquiva_rolar_{i}", lambda i=i: roll(1400 + i)) for i in range(2)]
    c["esquiva_recuo"] = [("esquiva_recuo_0", lambda: backstep(1500))]
    c["salto"] = [(f"salto_{i}", lambda i=i: jump(1600 + i)) for i in range(2)]
    c["pouso"] = [(f"pouso_{i}", lambda i=i: land(1700 + i)) for i in range(2)]
    c["fungo_desfaz"] = [(f"fungo_desfaz_{i}", lambda i=i: crumble(1800 + i)) for i in range(2)]
    return c


def write_wav(path, y):
    pcm = np.round(np.clip(y, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def analyse(y):
    """Números de revisão: duração, pico, RMS, fator de crista, DC, amostras cortadas, centroide espectral."""
    pk = float(np.max(np.abs(y)))
    rms = float(np.sqrt(np.mean(y ** 2)))
    spec = np.abs(np.fft.rfft(y * np.hanning(len(y))))
    fr = np.fft.rfftfreq(len(y), 1 / SR)
    cen = float(np.sum(fr * spec) / max(np.sum(spec), 1e-9))
    return {"duracao_s": round(len(y) / SR, 3), "pico_dbfs": round(20 * math.log10(max(pk, 1e-9)), 2),
            "rms_dbfs": round(20 * math.log10(max(rms, 1e-9)), 2), "crista_db": round(20 * math.log10(pk / max(rms, 1e-9)), 1),
            "dc": round(float(np.mean(y)), 6), "cortadas": int(np.sum(np.abs(y) >= 0.9999)), "centroide_hz": round(cen)}


def build(out_assets):
    out = os.path.join(out_assets, "audio")
    os.makedirs(out, exist_ok=True)
    variants, report = {}, {}
    for cue, items in catalog().items():
        variants[cue] = []
        for name, fn in items:
            y = fn()
            write_wav(os.path.join(out, name + ".wav"), y)
            variants[cue].append(name + ".wav")
            report[name] = analyse(y)
    with open(os.path.join(out, "variantes.json"), "w", encoding="utf-8") as f:
        json.dump({"_doc": "GERADO por tools/audio_e.py — efeito → arquivos (variações). Não editar.",
                   "taxa": SR, "efeitos": variants}, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out, "analise.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    return report


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets")
    rep = build(root)
    for k, v in rep.items():
        print(f"{k:22s} {v}")
