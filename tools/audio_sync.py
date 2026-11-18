#!/usr/bin/env python3
"""Rodada 09.1 — confere a sincronia do som GRAVADO PELA APLICAÇÃO (loopback, audio.wav) com os eventos lógicos.

Entrada: o audio.wav do roteiro e o registro do jogo (linhas "gravação: começa no passo N, quadro F" e
"som: passo T (...) efeito arquivo ... tom P"). Para cada efeito, o instante esperado é o do quadro em que o passo T foi
processado (o som entra no começo da mixagem desse quadro). O instante real é achado por correlação: o envelope do
arquivo-fonte (esticado pelo tom tocado) contra o envelope gravado, de −60 a +120 ms do esperado. A correlação
normalizada (r) diz quão claramente o som está lá. Só entram na conta os sons ISOLADOS (nenhum outro pedido a menos
de 0,3 s) com r ≥ 0,9: com dois sons por cima um do outro o envelope não separa as fontes (impreciso, não medido).

Uso: python3 tools/audio_sync.py audio.wav registro.txt [fps] > relatorio.txt
"""
import re
import sys
import wave

import numpy as np

TICK = 1 / 120


def main():
    wav, log = sys.argv[1], sys.argv[2]
    fps = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    with wave.open(wav) as w:
        sr, ch = w.getframerate(), w.getnchannels()
        y = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(float).reshape(-1, ch).mean(1) / 32768
    hop = sr // 1000                                                      # envelope a cada 1 ms (janela de 3 ms)
    env = np.array([np.sqrt(np.mean(y[i:i + 3 * hop] ** 2) + 1e-12) for i in range(0, len(y) - 3 * hop, hop)])
    db = 20 * np.log10(env)
    start_tick = None
    rows = []
    for line in open(log, encoding="utf-8"):
        m = re.match(r"gravação: começa no passo (\d+)", line)
        if m:
            start_tick = int(m.group(1))
        m = re.match(r"som: passo (\d+) \(.*?\) (\S+) (\S+) ganho \S+ tom ([\d,.]+)", line)
        if m:
            rows.append((int(m.group(1)), m.group(2), m.group(3), float(m.group(4).replace(",", "."))))
    if start_tick is None:
        sys.exit("registro sem a linha 'gravação: começa no passo'")
    per_frame = round(120 / fps)
    print(f"# sincronia som × lógica (loopback, {sr} Hz, {fps} quadros/s; passo lógico 1/120 s)")
    print(f"# esperado = começo do quadro que processou o passo; medido = melhor encaixe do envelope do arquivo")
    print(f"{'passo':>6} {'efeito':18} {'esperado s':>10} {'medido s':>9} {'dif ms':>7} {'r':>5}  nota")
    folder = sys.argv[4] if len(sys.argv) > 4 else "assets/audio/sintese"
    diffs = []
    for t, cue, f, pitch in rows:
        if t <= start_tick:
            continue
        frame = (t - start_tick - 1) // per_frame                          # quadro (0 = primeiro gravado) que rodou o passo t
        exp = frame / fps
        with wave.open(f"{folder}/{f}") as w:
            src = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(float) / 32768
        src = np.interp(np.arange(0, len(src), pitch), np.arange(len(src)), src)   # tocado com tom p = mais curto
        tpl = np.array([np.sqrt(np.mean(src[i:i + 3 * hop] ** 2) + 1e-12) for i in range(0, len(src) - 3 * hop, hop)])
        tpl = tpl[: min(len(tpl), 250)]
        a = int(exp * 1000)
        best, lag = -2.0, 0
        for k in range(max(a - 60, 0), min(a + 120, len(env) - len(tpl))):
            seg = env[k:k + len(tpl)]
            r = np.corrcoef(seg, tpl)[0, 1] if np.std(seg) > 1e-9 else -1
            if r > best:
                best, lag = r, k
        found = lag / 1000
        d = (found - exp) * 1000
        near = [c2 for t2, c2, _, _ in rows if t2 != t and abs(t2 - t) * TICK < 0.3]
        if best < 0.9 or near:
            why = f"sobreposto a {', '.join(sorted(set(near)))}" if near else "encoberto (outro som mais forte por cima)"
            print(f"{t:6d} {cue:18} {exp:10.3f} {found:9.3f} {d:7.1f} {best:5.2f}  não entra na conta: {why}")
            continue
        diffs.append(d)
        print(f"{t:6d} {cue:18} {exp:10.3f} {found:9.3f} {d:7.1f} {best:5.2f}")
    if diffs:
        d = np.array(diffs)
        print(f"# {len(d)} isolados medidos: diferença média {d.mean():.1f} ms, máx |{np.max(np.abs(d)):.1f}| ms "
              f"(resolução: 1 ms de envelope; a mixagem do loopback é por quadro de {1000 / fps:.1f} ms)")


if __name__ == "__main__":
    main()
