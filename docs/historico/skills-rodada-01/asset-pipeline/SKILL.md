---
name: asset-pipeline
description: Fluxo completo de um asset — fonte em script, exportação (.vox / rig.json / props.json / PNG), importação no Java, nomes, versionamento e reprodução; inclui como acrescentar props à cena e uma nova direção artística.
---

# Pipeline de assets

Referências: `docs/art/contrato-de-assets.md` (formatos e nomes) e `docs/architecture/assets.md`.

## Fluxo

```
tools/<módulo>.py  --python3 tools/build_assets.py-->  assets/...  --Assets/CharacterModel/World-->  viewer
```

- **Fonte de verdade:** os scripts em `tools/` e `assets/scenes/clearing.json`. Todo o resto de
  `assets/` é saída gerada e pode ser apagado e reconstruído.
- **Um módulo por direção e por tipo:** `chars_<x>.py`, `creatures_<x>.py`, `env_<x>.py`, cada um com
  `build(out)`. Registre módulos novos em `MODULES` de `tools/build_assets.py`.
- **Determinismo:** sem relógio, sem `random` sem semente. Use `envlib.hash3(x, y, z, seed)`.

## Nomes

- Diretórios de rig: `characters/<estilo>/{adventurer,ranger,mage}`,
  `creatures/<estilo>/{pompom,lumi,fungo,tortuga}`. Arquivos de parte = nome da parte + `.vox`.
- Props: minúsculas com `_`, radical + variante (`tree_oak2`, `rock_s`, `flower_red`).
- Estilos: uma letra (`a`–`d`). Texto de `name` em `rig.json` é livre (aparece no HUD, sem acentos
  funciona melhor na fonte bitmap).

## Tarefas comuns

### Acrescentar um prop à cena
1. Modele e registre no `env_<x>.py` de **cada** direção (as que não tiverem simplesmente não mostram).
2. Acrescente o nome à lista do contrato.
3. Coloque-o em `assets/scenes/clearing.json` (`props` para posição exata; `scatter` para espalhar
   por anel com semente e distância mínima).
4. `MODULES="env_a" tools/snap.sh "mode=scene,style=a,out=output/tmp/t.png"` e confira a escala.

### Acrescentar um personagem ou criatura
1. Novo diretório de rig via `Rig(...).save(...)`; para aparecer na ordem certa do seletor, use os
   nomes fixos ou acrescente o nome a `Assets.CHARACTER_ORDER`.
2. Criatura na cena: entrada em `creatures` do `clearing.json` (`n`, `x`, `z`, `r`, `clip`,
   opcionais `lift` e `clear`).
3. Nova locomoção → novo ramo em `Animator.evaluate` + linha na tabela do contrato.

### Acrescentar uma direção artística
1. Módulos `chars_e.py`, `creatures_e.py`, `env_e.py` + registro em `build_assets.py`.
2. `StyleProfile.e()` e o caso em `StyleProfile.of`; paleta/resolução de terreno em `World.TerrainStyle.of`.
3. Tecla e contagem de estilos em `App` (`GLFW_KEY_1..4`, laços `i < 4`) e em `Review`/`AssetContractTest`.
4. Guia `docs/art/direcao-e.md`.

### Mudar um formato
Mude escritor (Python) e leitor (Java) no mesmo trabalho, atualize o contrato e
`AssetContractTest`, regenere tudo (`python3 tools/build_assets.py`) e rode `./gradlew test`.

## Versionamento

- Versione `tools/`, `assets/scenes/` e também as saídas de `assets/` (são pequenas e permitem abrir
  o viewer logo após o clone); `output/tmp/` fica fora do git.
- Antes de entregar: `python3 tools/build_assets.py && ./gradlew test` e confira que
  `git status` não mostra saída gerada desatualizada.

## Reproduzir do zero

```bash
rm -rf assets/characters assets/creatures assets/environment assets/textures
python3 tools/build_assets.py
./gradlew test :viewer:installDist
viewer/build/install/viewer/bin/viewer --review
```

## Inspecionar num editor voxel

Qualquer `.vox` de `assets/` abre no MagicaVoxel ou no Goxel (`sudo apt install goxel`). Lembre:
o `.vox` é Z-para-cima (a engine converte) e a faixa do índice de paleta carrega o material — um
editor que reordene a paleta ao salvar quebra essa convenção (ADR 0002).
