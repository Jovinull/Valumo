#!/usr/bin/env python3
"""Resumo de um .jfr: métodos mais amostrados (CPU, thread main) e classes/locais que mais alocam.
Uso: tools/qa/jfr_resumo.py ARQ.jfr [N]
Gravar: JAVA_OPTS="-XX:StartFlightRecording=filename=x.jfr,settings=profile" game/build/install/game/bin/game --roteiro ...
(usa o `jfr` do JAVA_HOME, senão o do SDKMAN, senão o do PATH)."""
import collections, json, subprocess, sys, os
import shutil
jfr = next((c for c in [os.path.join(os.environ.get('JAVA_HOME', ''), 'bin', 'jfr'), os.path.expanduser('~/.sdkman/candidates/java/current/bin/jfr')] if os.path.isfile(c)), None) or shutil.which('jfr') or 'jfr'
f, n = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 25

def events(name):
    out = subprocess.run([jfr, 'print', '--json', '--stack-depth', '48', '--events', name, f], capture_output=True, text=True).stdout
    return json.loads(out)['recording']['events'] if out.strip() else []

def frames(ev):
    st = ev['values'].get('stackTrace') or {}
    return [(fr['method']['type']['name'] + '.' + fr['method']['name'], fr.get('lineNumber')) for fr in st.get('frames', [])]

ex = [e for e in events('jdk.ExecutionSample') if (e['values'].get('sampledThread') or {}).get('javaName') == 'main']
print(f'# CPU — {len(ex)} amostras da thread main')
top, incl, proj = collections.Counter(), collections.Counter(), collections.Counter()
for e in ex:
    fr = frames(e)
    if not fr: continue
    top[fr[0][0]] += 1
    seen = set()
    for m, _ in fr:
        if m not in seen: incl[m] += 1; seen.add(m)
    for m, ln in fr:                      # primeiro quadro do projeto (onde o tempo é "nosso")
        if m.startswith('vx.'): proj[f'{m}:{ln}'] += 1; break
tot = max(1, len(ex))
print('## no topo da pilha (auto)'); [print(f'{c*100/tot:5.1f}%  {m}') for m, c in top.most_common(n)]
print('## inclusivo (método em qualquer nível)'); [print(f'{c*100/tot:5.1f}%  {m}') for m, c in incl.most_common(n + 15) if m.startswith('vx.')]
print('## primeira linha do projeto na pilha'); [print(f'{c*100/tot:5.1f}%  {m}') for m, c in proj.most_common(n)]

al = events('jdk.ObjectAllocationSample')
byc, bys = collections.Counter(), collections.Counter()
for e in al:
    w = e['values'].get('weight', 0)
    byc[e['values']['objectClass']['name']] += w
    for m, ln in frames(e):
        if m.startswith('vx.'): bys[f'{m}:{ln}'] += w; break
tw = max(1, sum(byc.values()))
print(f'\n# Alocação — {len(al)} amostras, ~{tw/1048576:.0f} MB estimados')
print('## por classe'); [print(f'{c*100/tw:5.1f}%  {c/1048576:8.1f} MB  {m}') for m, c in byc.most_common(15)]
print('## por local do projeto'); [print(f'{c*100/tw:5.1f}%  {c/1048576:8.1f} MB  {m}') for m, c in bys.most_common(n)]
