"""Rodada 11.1 (fechamento) — gera tools/menu/12-estabilidade.txt: três ciclos × 6 voltas com amostra de recursos.

Uso: python3 tools/menu/gerar_estabilidade.py [VOLTAS]
"""
import sys

n = int(sys.argv[1]) if len(sys.argv) > 1 else 6
L = ["# Rodada 11.1 (fechamento) — estabilidade (§10): três ciclos repetidos, uma amostra de recursos vivos por volta (malhas,",
     "# texturas, FBOs, heap pós-GC, RSS, buffers/fluxos/vozes do OpenAL) e uma linha menu-recursos (+ grades de relevo).",
     "# A: menu → personagem → criador → Cancelar → mundo → aventura → Menu principal. B: criar mundo → prévia → cancelar.",
     "# C: semente recusada → outra semente → cancelar. Gerado por tools/menu/gerar_estabilidade.py; dados temporários.",
     "size 1280 720", "esperar-menu 20", "amostra recursos.csv", "menu-recursos inicio"]
for i in range(n):
    L += ["click jogar", "esperar-menu 20", "click criar-personagem", "criador tools/menu/criador-cancelar.txt", "esperar-menu 20",
          "click escolher-personagem", "esperar-menu 60", "menu-mundo vila", "click entrar", "esperar-menu 60", "hold W 0.5", "key ESC",
          "click menu-principal", "click menu-sem-salvar", "esperar-menu 60", f"menu-recursos A{i + 1}", "amostra recursos.csv"]
for i in range(n):
    L += ["click jogar", "esperar-menu 20", "click escolher-personagem", "esperar-menu 60", "click criar-mundo", "click campo:1",
          "digitar 2048", "click ver-previa", "esperar-menu 60", "click cancelar-mundo", "key ESC", "key ESC", "esperar-menu 20",
          f"menu-recursos B{i + 1}", "amostra recursos.csv"]
for i in range(n):
    L += ["click jogar", "esperar-menu 20", "click escolher-personagem", "esperar-menu 60", "click criar-mundo", "click campo:1",
          "digitar 4242", "click ver-previa", "esperar-menu 60", "key BACKSPACE", "key BACKSPACE", "key BACKSPACE", "key BACKSPACE",
          "digitar 77", "click ver-previa", "esperar-menu 60", "click cancelar-mundo", "key ESC", "key ESC", "esperar-menu 20",
          f"menu-recursos C{i + 1}", "amostra recursos.csv"]
open("tools/menu/12-estabilidade.txt", "w").write("\n".join(L) + "\n")
print(len(L), "linhas")
