"""QA de engenharia — entradas REAIS da geração de assets: roda tools/build_assets.py registrando todo arquivo do
repositório aberto para leitura, todo módulo do projeto importado e as bibliotecas de terceiros usadas. Serve para conferir
se as entradas declaradas na tarefa `assets` do Gradle (hoje tools/*.py) cobrem o que a geração lê.

Uso (da raiz): TRACE_OUT=saida.json python3 tools/qa/entradas_dos_assets.py   (regenera os assets; resultado idêntico)
"""
import os, sys, runpy
ROOT = os.getcwd(); reads, writes = set(), set()
def hook(ev, args):
    if ev == 'open':
        p, mode = args[0], args[1]
        if isinstance(p, (str, bytes)):
            p = os.path.abspath(os.fsdecode(p))
            if p.startswith(ROOT + os.sep):
                (writes if (mode and any(c in str(mode) for c in 'wax+')) else reads).add(os.path.relpath(p, ROOT))
sys.addaudithook(hook)
sys.argv = ['tools/build_assets.py']
try: runpy.run_path('tools/build_assets.py', run_name='__main__')
finally:
    mods = sorted({os.path.relpath(m.__file__, ROOT) for m in list(sys.modules.values())
                   if getattr(m, '__file__', None) and os.path.abspath(m.__file__).startswith(ROOT + os.sep)})
    ext = sorted({(m.__name__.split('.')[0]) for m in list(sys.modules.values())
                  if getattr(m, '__file__', None) and 'site-packages' in m.__file__})
    rd = sorted(r for r in reads if '__pycache__' not in r)
    import json
    json.dump(dict(modulos=mods, terceiros=ext, lidos=rd, escritos=len(writes)), open(os.environ["TRACE_OUT"], 'w'), indent=1)
