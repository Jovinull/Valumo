#!/usr/bin/env python3
"""QA de engenharia — gera o roteiro da SESSÃO LONGA (estabilidade): N ciclos que alternam vila, movimento, salto,
conversa com um NPC diferente, pausa (volumes, barras; o mudo a cada 10 ciclos), combate com um, três fungos e chefe (mortes por
desagregação), derrota e reinício a cada 5 ciclos, criador (troca de aparência) a cada 3 e troca pela escolha a cada 4.
Ao fim de cada ciclo, "amostra recursos.csv" grava heap, RSS, malhas/texturas/FBOs vivos, OpenAL, threads, GC e quadros.

Uso: python3 tools/qa/sessao_longa.py N > SAIDA/roteiro.txt
     tools/qa/audio_tempo_real.sh SAIDA/roteiro.txt SAIDA        (som em tempo real pelo backend wave, sem alto-falante)
"""
import math
import sys

N = int(sys.argv[1]) if len(sys.argv) > 1 else 90
# NPCs da vila: posição (x, z), rumo e número de falas (a conversa termina com falas + 1 apertos de E)
NPCS = [(-3.6, 0.4, 40, 4), (-10.0, -2.0, 73, 3), (2.0, -4.0, -22, 3), (-1.5, -10.0, 3, 3), (8.0, -1.0, -76, 3), (1.0, -8.0, -6, 3)]
ENC = ["um", "grupo", "chefe"]
L = ["# Sessão longa de estabilidade (gerada por tools/qa/sessao_longa.py; não edite à mão)", "size 1280 720",
     "perf on", "click comecar", "wait 1.0", "amostra recursos.csv"]
for i in range(N):
    x, z, r, falas = NPCS[i % len(NPCS)]
    hx, hz = x + 1.3 * math.sin(math.radians(r)), z + 1.3 * math.cos(math.radians(r))
    enc = ENC[i % 3]
    # cada ciclo começa com a arena reiniciada: sem luta engajada não há conversa (GameLogic: candidato só fora da luta)
    L += [f"# ciclo {i + 1}", "encontro um", "posicionar 0.6 4.4 200", "camera-recompor",
          "segurar W", "wait 1.2", "soltar W", "segurar W+SHIFT", "wait 1.0", "soltar W+SHIFT", "look 400 0 0.6", "pular", "wait 0.8",
          f"posicionar {hx:.2f} {hz:.2f} {(r + 180) % 360:.0f}", "camera-recompor", "wait 0.4"]
    # rodada 10: Esc encerra a conversa sem decidir (na Kelyra a última fala é a oferta da missão, e E aceitaria)
    L += ["key E", "wait 0.5"] * falas + ["key ESC", "wait 0.5"]
    # o mudo (a cada 10 ciclos) deixa ~0,4 s de silêncio de propósito: os buracos ESPERADOS no áudio são N/10
    L += ["key ESC", "wait 0.3", "click mus-", "click mus+", "click efe-", "click efe+"]
    if i % 10 == 9:
        L += ["click mudo", "click mudo"]
    L += ["click barras", "click continuar", "wait 0.3",
          f"encontro {enc}", "posicionar -1.6 10.4 0", "camera-recompor", "wait 0.5", "inimigo -1.6 11.15 180 RECOVER"]
    L += [f"vida-inimigo 1 #{k}" for k in range({"um": 1, "grupo": 3, "chefe": 1}[enc])]
    L += ["atacar", "wait 0.5", "mira", "atacar", "wait 0.4", "segurar S", "esquiva", "wait 0.6", "soltar S", "pular", "wait 0.6",
          "atacar", "wait 0.4", "atacar", "wait 2.0"]
    if i % 5 == 4:
        L += ["vida-heroi 1", "esperar-resultado 8", "wait 0.6", "key ENTER", "wait 0.8"]
    if i % 3 == 2:
        # o criador volta para a ESCOLHA (o jogador confirma a aparência criada e entra na vila)
        L += [f"criador tools/qa/criador-troca-{(i // 3) % 3}.txt", "wait 0.4", "click comecar", "wait 1.0"]
    if i % 4 == 3:
        L += ["key ESC", "wait 0.3", "click trocar-aparencia", "wait 0.3", "key DOWN", "wait 0.3", "click comecar", "wait 1.0"]
    L += ["amostra recursos.csv"]
L += ["perf off desempenho-final.txt", "estado"]
print("\n".join(L))
