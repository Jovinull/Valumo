#!/usr/bin/env python3
"""Rodada 05 — compara uma reprodução com a referência canônica.

    python3 tools/repro_compare.py assets  <outra>/assets        # assets: hash (vox/json) + pixels (png)
    .venv/bin/python tools/repro_compare.py imgs docs/art/reference <outra>/ref   # capturas: diferença de pixels

Assets: .vox/.json devem ser idênticos byte a byte (gerador determinístico). PNG compara PIXELS (a
codificação zlib pode variar com a versão do Python sem mudar a imagem). Capturas do viewer: reporta, por
imagem, o maior desvio de canal e a fração de pixels com desvio > 8/255 — capturas podem variar com
driver/GPU, então o critério é declarado, não escondido.
"""
import hashlib
import os
import sys


def files(root):
    out = {}
    for r, _, fs in os.walk(root):
        for f in fs:
            p = os.path.join(r, f)
            out[os.path.relpath(p, root)] = p
    return out


def png_pixels(path):
    try:
        from PIL import Image
        import numpy as np
        return np.asarray(Image.open(path).convert("RGBA"))
    except ImportError:
        return None


def cmp_assets(a, b):
    fa, fb = files(a), files(b)
    only_a, only_b = sorted(set(fa) - set(fb)), sorted(set(fb) - set(fa))
    same = diff = png_same_pixels = 0
    diffs = []
    for k in sorted(set(fa) & set(fb)):
        ha = hashlib.sha256(open(fa[k], "rb").read()).hexdigest()
        hb = hashlib.sha256(open(fb[k], "rb").read()).hexdigest()
        if ha == hb:
            same += 1
            continue
        if k.endswith(".png"):
            pa, pb = png_pixels(fa[k]), png_pixels(fb[k])
            if pa is not None and pa.shape == pb.shape and (pa == pb).all():
                png_same_pixels += 1
                continue
        diff += 1
        diffs.append(k)
    print(f"idênticos (bytes): {same}; PNG com pixels idênticos e bytes diferentes: {png_same_pixels}; DIFERENTES: {diff}")
    print(f"só na referência: {len(only_a)} {only_a[:10]}; só na reprodução: {len(only_b)} {only_b[:10]}")
    for k in diffs[:30]:
        print("  diferente:", k)
    return diff == 0 and not only_a and not only_b


def cmp_imgs(a, b):
    import numpy as np
    from PIL import Image
    ok = True
    for k in sorted(f for f in os.listdir(a) if f.endswith(".png")):
        pb = os.path.join(b, k)
        if not os.path.exists(pb):
            print(f"  FALTA {k}")
            ok = False
            continue
        x = np.asarray(Image.open(os.path.join(a, k)).convert("RGB")).astype(int)
        y = np.asarray(Image.open(pb).convert("RGB")).astype(int)
        if x.shape != y.shape:
            print(f"  {k}: tamanhos diferentes {x.shape} × {y.shape}")
            ok = False
            continue
        d = np.abs(x - y)
        frac = float((d.max(-1) > 8).mean()) * 100
        print(f"  {k:45s} desvio máx. {int(d.max()):3d}/255   pixels > 8/255: {frac:6.3f}%")
        ok &= frac < 0.5
    print("capturas equivalentes (< 0,5% dos pixels acima de 8/255):", ok)
    return ok


if __name__ == "__main__":
    mode, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
    sys.exit(0 if (cmp_assets(a, b) if mode == "assets" else cmp_imgs(a, b)) else 1)
