#!/usr/bin/env python3
"""QA de engenharia — o que a aba Problems do VS Code está mostrando SEM abrir a interface: lê os marcadores gravados pelo
Java Language Server (.markers + .markers.snap, formato Eclipse core.resources) no workspace da extensão redhat.java.

Uso: python3 tools/qa/ide_marcadores.py ~/.config/Code/User/workspaceStorage/<id>/redhat.java/jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects saida.json
  (o <id> é a pasta cujo workspace.json aponta para o repositório). Projetos listados = módulos que a IDE importou: se
  faltar um módulo do settings.gradle.kts, o import está desatualizado.
"""
import struct, sys, os, json, collections

class R:
    def __init__(s, b): s.b = b; s.p = 0
    def eof(s): return s.p >= len(s.b)
    def i(s): v = struct.unpack_from('>i', s.b, s.p)[0]; s.p += 4; return v
    def l(s): v = struct.unpack_from('>q', s.b, s.p)[0]; s.p += 8; return v
    def h(s): v = struct.unpack_from('>h', s.b, s.p)[0]; s.p += 2; return v
    def by(s): v = s.b[s.p]; s.p += 1; return v
    def utf(s):
        n = struct.unpack_from('>H', s.b, s.p)[0]; s.p += 2
        v = s.b[s.p:s.p+n].decode('utf-8', 'replace'); s.p += n; return v

def marker(r, types):
    mid = r.l(); k = r.by()
    if k == 2: t = r.utf(); types.append(t)
    elif k == 1: t = types[r.i()]
    else: raise ValueError(f'tipo {k} @ {r.p}')
    n = r.h(); attrs = {}
    for _ in range(n):
        key = r.utf(); vt = r.by()
        if vt == 2: v = r.i()
        elif vt == 1: v = bool(r.by())
        elif vt == 3: v = r.utf()
        elif vt == 0: v = None
        else: raise ValueError(f'attr {vt} @ {r.p}')
        attrs[key] = v
    ct = r.l()
    attrs["_mid"] = mid; attrs["type"] = t; return attrs

def load(projdir):
    state = {}
    full = os.path.join(projdir, '.markers')
    if os.path.exists(full):
        r = R(open(full, 'rb').read()); r.i(); types = []
        while not r.eof():
            path = r.utf(); n = r.i()
            state[path] = [marker(r, types) for _ in range(n)]
    snap = os.path.join(projdir, '.markers.snap')
    if os.path.exists(snap):
        raw = open(snap, 'rb').read()
        B = bytes([64, 177, 139, 129, 35, 188, 0, 20, 26, 37, 150, 231, 163, 147, 190, 30])
        E = bytes([192, 88, 251, 243, 35, 188, 0, 20, 26, 81, 243, 140, 123, 187, 119, 198])
        data = b''; pos = 0
        while True:   # SafeChunkyInputStream: só valem os trechos entre BEGIN e END
            b = raw.find(B, pos)
            if b < 0: break
            e = raw.find(E, b + 16)
            nb = raw.find(B, b + 16)
            if e < 0: break
            if nb >= 0 and nb < e: pos = nb; continue
            data += raw[b + 16:e]; pos = e + 16
        r = R(data)
        while not r.eof():
            try:
                r.i(); path = r.utf(); n = r.i(); types = []
                state[path] = [marker(r, types) for _ in range(n)]
            except Exception as e:
                print('  snap interrompido em', projdir.split('/')[-1], r.p, len(r.b), e, file=sys.stderr); break
    return state

if __name__ == '__main__':
    base = sys.argv[1]; out = []
    for p in sorted(os.listdir(base)):
        try: st = load(os.path.join(base, p))
        except Exception as e: print('ERRO', p, e, file=sys.stderr); continue
        for path, ms in st.items():
            for m in ms: m['resource'] = path; m['project'] = p; out.append(m)
    json.dump(out, open(sys.argv[2], 'w'), ensure_ascii=False, indent=1)
    print(len(out), 'marcadores')
    sev = {2: 'erro', 1: 'aviso', 0: 'info'}
    c = collections.Counter((m['project'], sev.get(m.get('severity'), '?')) for m in out)
    for k, v in sorted(c.items()): print(k, v)
