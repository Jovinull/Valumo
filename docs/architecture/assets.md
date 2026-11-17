# Pipeline de assets

```
tools/voxlib.py      volume esparso, primitivas, cores com material, escritor .vox, Rig, PropSet, PNG
tools/envlib.py      formas de ambiente (superelipsoide, rampa iluminada de cima, tronco, pedra, telhado)
tools/cuboidlib.py   (direção C) paleta, canvas de pixel art, atlas, rigs e props de cuboides
tools/chars_<x>.py   personagens da direção x        -> assets/characters/<x>/<nome>/
tools/creatures_<x>.py  criaturas                     -> assets/creatures/<x>/<nome>/
tools/env_<x>.py     ambiente                         -> assets/environment/<x>/ (+ assets/textures/<x>/)
tools/build_assets.py   ponto de entrada; reconstrói módulos na ordem
```

## Por que modelagem por script

Decisão registrada no [ADR 0002](../decisions/0002-assets-por-script.md). Em resumo: os modelos
ficam versionáveis como texto, reproduzíveis com um comando, parametrizáveis (o estudo de proporções
é o mesmo personagem com dois números diferentes) e podem ser revisados por diff. O custo é que
ninguém "esculpe" diretamente; por isso a saída é `.vox` padrão, que abre no MagicaVoxel e no Goxel
para inspeção ou para um artista continuar à mão.

## Formatos

| Arquivo | Conteúdo |
|---|---|
| `*.vox` | MagicaVoxel 150, um modelo, paleta `RGBA` obrigatória; material pela faixa do índice |
| `rig.json` | partes, hierarquia, pivôs, tamanho do voxel, tipo de animação, metadados |
| `props.json` | manifesto dos modelos estáticos de um estilo |
| `atlas.png` | atlas de pixel art (direção C), lido com filtragem nearest |
| `textures/c/terrain.png` + `terrain.json` | tiles de terreno da direção C |
| `scenes/clearing.json` | disposição da clareira (escrita à mão, compartilhada pelas 4 direções) |

Os campos estão especificados em [../art/contrato-de-assets.md](../art/contrato-de-assets.md).

## Importação no Java

- `VoxModel.load` lê `SIZE`/`XYZI`/`RGBA`, converte Z-para-cima em Y-para-cima e expõe o volume como
  `VoxelSource` (cor + material).
- `VoxelMesher.build` gera a malha já em unidades de mundo, com a origem do modelo no lugar certo
  (`origin` do manifesto).
- `CharacterModel.load` monta as partes e valida a hierarquia (pai antes do filho).
- `CuboidMesh.build` gera as faces texturizadas dos cuboides.
- `Assets` guarda tudo em cache por caminho.

## Editar um modelo e ver o resultado

```bash
$EDITOR tools/chars_a.py
MODULES="chars_a" tools/snap.sh "mode=char,style=a,char=0,out=output/tmp/teste.png"
```

Para inspecionar num editor voxel: abra `assets/characters/a/adventurer/head.vox` (cada parte é um
arquivo). Edições feitas no editor **não** voltam para o script: se um artista assumir um modelo à
mão, o módulo Python correspondente deve deixar de gerá-lo (registre isso no guia da direção).

## Determinismo

Os scripts não usam relógio nem aleatoriedade não semeada: variações vêm de `hash3(x, y, z, seed)` ou
de `random.Random(seed)`. Rodar `build_assets.py` duas vezes produz arquivos idênticos byte a byte
(verificado em [../validation/resultados.md](../historico/rodada-01-direcoes/resultados.md)).

## Rodada 02

Módulos novos em `tools/`: `charlib_e.py` (biblioteca de personagem da E), `chars_e.py`,
`creatures_e.py`, `env_e.py`, `chars_variants.py` (experimento V1/V2/V3), além de `anim.sh` (vídeo
com ffmpeg) e `sheet.py` (pranchas; usa o `.venv`). `voxlib.Rig` grava `alts` (expressões) e chaves
extras; `PropSet.add` aceita chaves extras (`affineWind`). Os `.vox` foram verificados no Goxel
([ADR 0008](../decisions/0008-ferramentas-de-arte.md)). Procedimento completo:
[../art/pipeline-de-producao.md](../art/pipeline-de-producao.md).
