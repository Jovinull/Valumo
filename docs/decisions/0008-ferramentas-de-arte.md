# ADR 0008 — Ferramentas de arte instaladas sem privilégio

**Estado:** aceito (rodada 02)

## Contexto
A rodada 01 não instalou nenhum editor (o `apt` pedia senha) e não verificou a abertura dos `.vox`
em editor externo.

## Decisão
- Editores via **Flatpak de usuário** (sem sudo): Goxel 0.13 (voxel, lê/grava `.vox`), LibreSprite
  (pixel art), Blockbench (cuboides). JDK via SDKMAN (Temurin 25.0.5). Ferramentas de QA de imagem
  num `.venv` local (numpy, Pillow, imageio), **fora** do pipeline de assets (que continua só stdlib).
- A modelagem continua por script ([ADR 0002](0002-assets-por-script.md)); o editor serve para
  inspeção e para um artista assumir um modelo, com o procedimento em
  [../art/pipeline-de-producao.md](../art/pipeline-de-producao.md).

## Verificado
Goxel abre e exporta `.vox` gerados (cabeça, chapéu, casa, pompom) com cores e orientação corretas:
`output/visual-review/round-02/ferramentas/goxel-abre-vox.png`.

## Riscos
MagicaVoxel (referência do formato) não tem build Linux oficial; não foi testado.
