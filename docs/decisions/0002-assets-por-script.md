# ADR 0002 — Assets modelados por script Python, exportados em `.vox`

**Estado:** aceito (rodada 01) — reavaliar quando houver artista dedicado

## Contexto
A rodada exige modelos reais, editáveis e reproduzíveis, feitos com ferramentas locais e sem
geradores de imagem. A máquina não tinha editor voxel instalado e a instalação por `apt` exige
senha de administrador; além disso, quem modela nesta rodada é um agente, que trabalha melhor com
texto do que com interface gráfica.

## Decisão
- Os modelos são **programas** (`tools/chars_*.py`, `creatures_*.py`, `env_*.py`) sobre uma
  biblioteca pequena (`voxlib.py`, `envlib.py`, `cuboidlib.py`), só com a biblioteca padrão do Python.
- A saída é **MagicaVoxel `.vox`** (formato aberto e documentado, lido por MagicaVoxel e Goxel),
  uma parte por arquivo, mais `rig.json`/`props.json`.
- O material vai na **faixa do índice de paleta** (fosco, metal, emissivo, folhagem, água).
- Pixel art da direção C é desenhada em grades ASCII e por regras explícitas dentro dos scripts e
  gravada como PNG.

## Por quê
- Reprodutível com um comando e determinístico; diffs legíveis; fácil de parametrizar (o estudo de
  proporções é o mesmo construtor com alturas diferentes).
- O agente consegue iterar: editar script → capturar no viewer → olhar → corrigir.

## Custos e riscos
- Script não é escultura: ajustes finos de silhueta custam mais do que num editor. A qualidade
  depende de revisão visual disciplinada (ver `.claude/skills/visual-qa`).
- A convenção de material por faixa de paleta se perde se um editor reordenar a paleta ao salvar.
  Quem editar à mão deve conferir os índices (ou a engine deve passar a ler o chunk `MATL`).
- Fluxo de ida apenas: edição manual de um `.vox` é sobrescrita na próxima geração.

## Caminho de evolução
Quando houver artista: instalar Goxel (`sudo apt install goxel`) ou MagicaVoxel, promover os `.vox`
de personagens a fonte primária (pasta própria, fora do gerador) e manter scripts só para o que é
naturalmente procedural (vegetação, pedras, variações).

**Nota (rodada 05):** o Goxel 0.13 foi instalado por Flatpak de usuário (sem sudo; ADR 0008) e a ida e volta `.vox` → Goxel → `voxlib.load_vox(like=...)` foi verificada sem perda; procedimento em `docs/art/pipeline-de-producao.md`.
