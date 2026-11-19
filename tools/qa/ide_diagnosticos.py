#!/usr/bin/env python3
"""QA de engenharia — sobe o MESMO Java Language Server da extensão do VS Code (redhat.java) num workspace de dados NOVO,
importa o projeto indicado e grava todos os diagnósticos publicados: o que a aba Problems mostraria depois de um import
limpo ("Java: Clean Java Language Server Workspace"). Não toca no workspace da IDE do proprietário.

Uso: python3 tools/qa/ide_diagnosticos.py RAIZ_DO_PROJETO PASTA_DE_DADOS_NOVA saida.json [arquivos.java para abrir...]
  PASTA_DE_DADOS_NOVA fora do repositório (ex.: /tmp/jdt/ws). QUIET=25 (s sem mensagens até considerar terminado).
"""
import json, os, subprocess, sys, threading, time, glob, shutil

root, data, out = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2]), sys.argv[3]
EXT = sorted(glob.glob(os.path.expanduser('~/.vscode/extensions/redhat.java-*')))[-1]   # a versão instalada mais nova
java = glob.glob(EXT + '/jre/*/bin/java')[0]
launcher = glob.glob(EXT + '/server/plugins/org.eclipse.equinox.launcher_*.jar')[0]
cfg = os.path.join(os.path.dirname(data), 'config_linux')
if not os.path.exists(cfg): shutil.copytree(EXT + '/server/config_linux', cfg)
cmd = [java, '--add-modules=ALL-SYSTEM', '--add-opens', 'java.base/java.util=ALL-UNNAMED', '--add-opens',
       'java.base/java.lang=ALL-UNNAMED', '--add-opens', 'java.base/sun.nio.fs=ALL-UNNAMED',
       '-Declipse.application=org.eclipse.jdt.ls.core.id1', '-Dosgi.bundles.defaultStartLevel=4',
       '-Declipse.product=org.eclipse.jdt.ls.core.product', '-Djava.import.generatesMetadataFilesAtProjectRoot=false',
       '-DDetectVMInstallationsJob.disabled=true', '-Dfile.encoding=utf8', '-Xmx2G', '-Xlog:disable',
       '-jar', launcher, '-configuration', cfg, '-data', data]
p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=open(out + '.stderr', 'w'))
diags, status, last = {}, [], [time.time()]
wl = threading.Lock()
ready = threading.Event()

def send(obj):
    b = json.dumps(obj).encode()
    with wl: p.stdin.write(b'Content-Length: %d\r\n\r\n' % len(b) + b); p.stdin.flush()

def reader():
    f = p.stdout
    while True:
        h = {}
        while True:
            line = f.readline()
            if not line: return
            line = line.strip()
            if not line: break
            k, v = line.split(b':', 1); h[k.lower()] = v.strip()
        body = f.read(int(h[b'content-length'])); m = json.loads(body); last[0] = time.time()
        try: handle(m)
        except Exception as e: print('erro no leitor:', repr(e), str(m)[:200], file=sys.stderr)

def handle(m):
        meth = m.get('method')
        if meth == 'textDocument/publishDiagnostics':
            diags[m['params']['uri']] = m['params']['diagnostics']
        elif meth == 'language/status':
            status.append((round(time.time() - t0, 1), m['params'].get('type'), m['params'].get('message', '')[:120]))
            if m['params'].get('type') in ('ServiceReady', 'Started'): ready.set()
        elif 'id' in m and meth:   # pedido do servidor: responde vazio
            pr = m.get('params')
            res = [None] * len(pr.get('items', [])) if meth == 'workspace/configuration' and isinstance(pr, dict) else None
            send({'jsonrpc': '2.0', 'id': m['id'], 'result': res})

t0 = time.time(); threading.Thread(target=reader, daemon=True).start()
uri = 'file://' + root
send({'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
    'processId': os.getpid(), 'rootUri': uri, 'workspaceFolders': [{'uri': uri, 'name': os.path.basename(root)}],
    'capabilities': {'textDocument': {'publishDiagnostics': {'relatedInformation': True}}, 'workspace': {'workspaceFolders': True, 'configuration': True}},
    'initializationOptions': {'bundles': [], 'workspaceFolders': [uri],
                              'settings': {'java': {'autobuild': {'enabled': True}, 'import': {'gradle': {'enabled': True, 'wrapper': {'enabled': True}}}}},
                              'extendedClientCapabilities': {'progressReportProvider': False, 'classFileContentsSupport': False}}}})
send({'jsonrpc': '2.0', 'method': 'initialized', 'params': {}})
ok = ready.wait(600)
quiet = float(os.environ.get('QUIET', '25'))
while time.time() - last[0] < quiet and time.time() - t0 < 900: time.sleep(1)
if len(sys.argv) > 4:   # abre arquivos (o servidor só reconcilia o que está aberto; os demais vêm do build)
    for f in sys.argv[4:]:
        send({'jsonrpc': '2.0', 'method': 'textDocument/didOpen', 'params': {'textDocument': {
            'uri': 'file://' + os.path.abspath(f), 'languageId': 'java', 'version': 1, 'text': open(f).read()}}})
    time.sleep(2); last[0] = time.time()
    while time.time() - last[0] < quiet: time.sleep(1)
send({'jsonrpc': '2.0', 'id': 2, 'method': 'shutdown', 'params': None}); time.sleep(3)
send({'jsonrpc': '2.0', 'method': 'exit', 'params': None})
try: p.wait(20)
except Exception: p.kill()
flat = []
for u, ds in diags.items():
    for d in ds:
        flat.append({'resource': u.replace('file://' + root + '/', '').replace('file://' + root, '.'), 'severity': d.get('severity'),
                     'code': str(d.get('code')), 'source': d.get('source'), 'message': d.get('message'),
                     'line': d['range']['start']['line'] + 1})
json.dump({'pronto': ok, 'segundos': round(time.time() - t0, 1), 'status': status, 'diagnosticos': flat}, open(out, 'w'), ensure_ascii=False, indent=1)
print('pronto', ok, 'diagnósticos', len(flat), 'em', len([u for u in diags if diags[u]]), 'arquivos')
