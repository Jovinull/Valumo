#!/usr/bin/env python3
"""Rodada 05 — busca geométrica de empunhaduras de duas mãos viáveis nas proporções da E.

Varre giro do tronco (yaw), posição da mão direita (grade à frente do corpo), direção da lâmina e distância
entre as mãos no cabo (lGrip), e conta configurações que respeitam, ao mesmo tempo:
  * as duas mãos dentro do alcance dos braços (10,6 voxels a partir dos ombros girados);
  * o pomo (lGrip + 1,5 abaixo da mão direita) FORA do tronco (cápsula y 13–22, raio 6 + 1,1);
  * a lâmina (do guarda até 20 voxels) fora da cabeça (cápsula y 33,5–37, raio 8,6 + 1,3).
Mesmas medidas dos volumes de validação (charlib_e.body_volumes). Saída: contagem e exemplos por família
de pose (guarda média, guarda alta lateral, corte à frente).
"""
import math
import itertools

ARM = 10.6
SR, SL, PIV = (-6.5, 23.5, 0.5), (6.5, 23.5, 0.5), (0.0, 11.0, 0.5)


def rot_y(p, deg):
    a = math.radians(deg)
    x, y, z = p[0] - PIV[0], p[1], p[2] - PIV[2]
    return (x * math.cos(a) + z * math.sin(a) + PIV[0], y, -x * math.sin(a) + z * math.cos(a) + PIV[2])


def seg_point_dist(a, b, p):
    ab = [b[i] - a[i] for i in range(3)]
    t = max(0, min(1, sum((p[i] - a[i]) * ab[i] for i in range(3)) / max(1e-9, sum(c * c for c in ab))))
    q = [a[i] + ab[i] * t for i in range(3)]
    return math.dist(q, p)


def seg_seg_min(a, b, c, d, n=24):
    return min(seg_point_dist(c, d, [a[i] + (b[i] - a[i]) * k / n for i in range(3)]) for k in range(n + 1))


def ok(yaw, R, D, g):
    sr, sl = rot_y(SR, yaw), rot_y(SL, yaw)
    L = tuple(R[i] - D[i] * g for i in range(3))
    P = tuple(R[i] - D[i] * (g + 1.5) for i in range(3))
    if math.dist(R, sr) > ARM or math.dist(L, sl) > ARM:
        return False
    for H in (R, L):                                          # mãos fora do rosto e do tronco
        if seg_point_dist((0, 33.5, 0), (0, 37, 0), H) < 8.6 + 1.0 or seg_point_dist((0, 13, 0.5), (0, 22, 0.5), H) < 6.0 + 1.5:
            return False
    # antebraço esquerdo: o ponto médio entre ombro e mão não pode afundar no tronco
    M = tuple((sl[i] + L[i]) / 2 for i in range(3))
    if seg_point_dist((0, 13, 0.5), (0, 22, 0.5), M) < 6.0:
        return False
    torso_a, torso_b = (0, 13, 0.5), (0, 22, 0.5)
    if seg_seg_min(L, P, torso_a, torso_b) < 6.0 + 1.1:      # cabo/pomo no tronco
        return False
    G = tuple(R[i] + D[i] * 4 for i in range(3)); T = tuple(R[i] + D[i] * 24 for i in range(3))
    if seg_seg_min(G, T, (0, 33.5, 0), (0, 37, 0)) < 8.6 + 1.3:
        return False
    return True


def main():
    dirs = {}
    for el in range(-60, 90, 15):          # elevação da lâmina
        for az in range(-90, 91, 15):      # azimute (0 = frente)
            e, a = math.radians(el), math.radians(az)
            dirs[(el, az)] = (math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a))
    hits, total = [], 0
    for yaw in range(-60, 61, 10):
        for x, y, z in itertools.product(range(-10, 9, 2), range(12, 29, 2), range(4, 15, 2)):
            for (el, az), D in dirs.items():
                for g in (3.0, 4.5, 6.0):
                    total += 1
                    if ok(yaw, (x, y, z), D, g):
                        hits.append((yaw, (x, y, z), el, az, g))
    print(f"configurações testadas: {total}; viáveis: {len(hits)}")
    fam = {"guarda à frente (elev. 0–30°, az. ±30°)": lambda h: 0 <= h[2] <= 30 and abs(h[3]) <= 30,
           "guarda alta (elev. ≥ 45°)": lambda h: h[2] >= 45,
           "lâmina para baixo (elev. ≤ −30°)": lambda h: h[2] <= -30,
           "lateral (|az.| ≥ 60°)": lambda h: abs(h[3]) >= 60}
    for name, f in fam.items():
        hs = [h for h in hits if f(h)]
        print(f"  {name}: {len(hs)}" + (f"  ex.: yaw {hs[0][0]}, mão {hs[0][1]}, elev {hs[0][2]}, az {hs[0][3]}, cabo {hs[0][4]}" if hs else ""))
    gs = sorted({h[4] for h in hits})
    print("  distâncias entre mãos com solução:", gs)


if __name__ == "__main__":
    main()
