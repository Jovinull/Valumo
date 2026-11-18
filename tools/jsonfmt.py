"""Formata JSON escrito à mão no estilo do repositório: um nível por linha, mas objetos e listas curtos numa linha só.

Uso: python3 tools/jsonfmt.py arquivo.json [largura]
"""
import collections
import json
import sys


def fmt(v, ind, width):
    one = json.dumps(v, ensure_ascii=False)
    if len(one) + ind <= width or not isinstance(v, (dict, list)) or not v:
        return one
    pad = ' ' * (ind + 1)
    if isinstance(v, dict):
        items = [pad + json.dumps(k, ensure_ascii=False) + ': ' + fmt(x, ind + 1, width) for k, x in v.items()]
        return '{\n' + ',\n'.join(items) + '\n' + ' ' * ind + '}'
    items = [pad + fmt(x, ind + 1, width) for x in v]
    return '[\n' + ',\n'.join(items) + '\n' + ' ' * ind + ']'


if __name__ == '__main__':
    path = sys.argv[1]
    width = int(sys.argv[2]) if len(sys.argv) > 2 else 150
    data = json.load(open(path), object_pairs_hook=collections.OrderedDict)
    open(path, 'w').write(fmt(data, 0, width) + '\n')
