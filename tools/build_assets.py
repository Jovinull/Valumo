#!/usr/bin/env python3
"""Gera todos os assets do protótipo a partir dos scripts-fonte (determinístico).

Uso:  python3 tools/build_assets.py [módulo ...]
Sem argumentos, reconstrói tudo. Os módulos são os arquivos tools/<nome>.py que expõem build(out).
"""
import importlib
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.join(os.path.dirname(HERE), "assets")

# Ordem de construção; módulos ainda não escritos são ignorados.
MODULES = ["chars_a", "creatures_a", "env_a",
           "chars_b", "creatures_b", "env_b",
           "chars_c", "creatures_c", "env_c",
           "chars_d", "creatures_d", "env_d",
           # rodada 02: direção consolidada E e o experimento das três variantes do aventureiro
           "chars_e", "creatures_e", "env_e", "chars_variants",
           # laboratório de conteúdo do mundo: construções, vegetação, POIs e props NOVOS (candidatos) + catálogo
           # valumo.conteudo/1; acrescenta ao props.json da E, por isso roda depois de env_e
           "conteudo_e",
           # rodada 03: estudos controlados dos protagonistas
           "chars_e_study",
           # rodada 06: primeira expansão controlada (NPC, criatura, árvore, estrutura)
           "npcs_e",
           # NPCs da vila (Kelyra, Índira, Súvia) — e o construtor em lote que os de ofício reaproveitam
           "npcs_vila_e",
           # NPCs de ofício com altura narrativa (Bramor, Teodor, Pluma, Ondina, Lunara)
           "npcs_oficios_e",
           # laboratório: NPCs de ofício do mundo procedural (Brásia, Quelmo, Arvel), mesma altura narrativa
           "npcs_mundo_e",
           # NPC Acapela (criaturinha de rabo de lagarto, cabeça franjada), sobre o esqueleto da vila
           "npc_acapela_e",
           # rodada 06.1: prova técnica de personalização modular (lê assets de chars_e)
           "custom_e",
           # rodada 07: componentes de personalização em tempo de execução (lê assets de chars_e)
           "components_e",
           # rodada 13.1: modelos voxel dos itens da mochila (assets/items/e/)
           "itens_e",
           # rodada 09.1: efeitos sonoros do combate (assets/audio/sintese/)
           "audio_e"]


def main():
    wanted = sys.argv[1:] or [m for m in MODULES if os.path.exists(os.path.join(HERE, m + ".py"))]
    for name in wanted:
        t = time.time()
        try:
            module = importlib.import_module(name)
        except ModuleNotFoundError as e:
            if e.name in (name, None) or os.path.exists(os.path.join(HERE, e.name + ".py")):
                raise                                   # módulo do próprio projeto: erro de verdade
            # biblioteca de terceiros ausente (hoje só o numpy, usado pela síntese de tools/audio_e.py)
            sys.exit(f"{name}: falta a biblioteca Python '{e.name}' em {sys.executable}.\n"
                     f"  Instale numa .venv na raiz (o Gradle a usa sozinho):\n"
                     f"    python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt\n"
                     f"  ou aponte outro interpretador: ./gradlew assets -Pvx.python=/caminho/python3 (ou VX_PYTHON).")
        module.build(OUT)
        print(f"{name}: ok ({time.time() - t:.2f}s)")


if __name__ == "__main__":
    main()
