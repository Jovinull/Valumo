# Pipeline de produção de assets (direção E)

Passo a passo para criar ou alterar um asset compatível com a identidade. Detalhes de formato no
[contrato](contrato-de-assets.md); arquitetura em [../architecture/assets.md](../architecture/assets.md).

## Ferramentas

| Ferramenta | Para quê | Instalação nesta máquina | Licença |
|---|---|---|---|
| Python ≥ 3.10 (stdlib) | modelar por script (`tools/*.py`) | sistema | PSF |
| JDK 25 (Temurin) + Gradle wrapper | viewer, capturas, testes | SDKMAN (`~/.sdkman`) | GPLv2+CE / Apache-2.0 |
| `.venv` (numpy, Pillow, imageio) | pranchas e análise de imagem (só QA) | `python3 -m venv .venv && .venv/bin/pip install numpy pillow imageio imageio-ffmpeg` | BSD / HPND |
| ffmpeg | vídeos de animação (`tools/anim.sh`) | sistema (Fedora: `ffmpeg-free`, usa libopenh264) | LGPL/GPL |
| Goxel 0.13 | abrir/inspecionar/editar `.vox` | `flatpak install --user flathub io.github.guillaumechereau.Goxel` | GPL-3.0 |
| LibreSprite | pixel art (texturas, se um dia usadas) | `flatpak install --user flathub com.github.libresprite.LibreSprite` | GPL-2.0 |
| Blockbench | cuboides texturizados (direção C) | `flatpak install --user flathub net.blockbench.Blockbench` | GPL-3.0 |

Nenhuma exige sudo (Flatpak de usuário). MagicaVoxel não tem build Linux oficial; Goxel lê e grava o
mesmo `.vox` — verificado: `round-02/ferramentas/goxel-abre-vox.png` (exportado pelo próprio Goxel).

## Fluxo

1. **Escreva o modelo** no módulo da direção (`tools/chars_e.py`, `creatures_e.py`, `env_e.py`)
   usando `charlib_e` / `env_e`. Cor-base sempre com `P()` (registra a rampa).
2. **Gere**: `python3 tools/build_assets.py chars_e` (só o módulo) — imprime cabeças, altura, faces.
3. **Capture** (viewer real, offscreen):
   ```bash
   tools/snap.sh "mode=char,style=e,char=0,yaw=30,out=output/tmp/x.png"          # isolado
   tools/snap.sh "mode=lineup,style=e,set=herois,myaw=0,out=output/tmp/l.png"    # ao lado dos outros
   tools/snap.sh "mode=lineup,style=e,set=herois,sil=1,out=output/tmp/s.png"     # silhueta
   tools/snap.sh "mode=scene,style=e,out=output/tmp/c.png"                       # no mundo
   ```
   `REBUILD=1` se mudou Java/GLSL.
4. **Revise** seguindo [../validation/processo-de-revisao-visual.md](../validation/processo-de-revisao-visual.md)
   e o [checklist](../validation/checklist-de-aceitacao.md).
5. **Teste**: `./gradlew test` (contrato, proporções, orçamento, expressões, articulações).
   Alternativas de design: crie variantes no módulo de estudo (`tools/chars_e_study.py`, saída em
   `assets/characters/r3/`) e compare no modo LINHA mudando **uma variável por vez**; o pacote completo
   sai com `viewer --review3 [seções]` em `output/visual-review/round-03/`.
6. **Registre** no guia correspondente o que mudou e por quê.

## Edição à mão num editor voxel (verificada na rodada 05)

Os `.vox` em `assets/` são **saída**: o build sobrescreve. Para um artista assumir uma peça sem que a
regeneração a perca:

1. Abra o `.vox` gerado no Goxel (ou exporte sem UI:
   `flatpak run --filesystem=$PWD --command=goxel io.github.guillaumechereau.Goxel in.vox --export out.vox`).
2. Edite e salve em `tools/fontes/<direção>/<nome>.vox` — essa passa a ser a **fonte** daquela peça.
3. No módulo Python, troque a construção da peça por
   `voxlib.load_vox("tools/fontes/e/<nome>.vox", like=<modelo gerado original>)`. O `like=` restaura o
   material (metal, emissivo, folhagem) pela cor: o Goxel reescreve a paleta e a faixa de material se perde.
   Cor nova que não existia no original entra como `matte` — declare-a no script se precisar de outro material.
4. Registre `meta.source = "manual"` e a troca no guia correspondente; regenere e siga o fluxo acima
   (auditoria, testes, `--check`, capturas comparáveis às referências).

Prova (rodada 05, `output/visual-review/round-05/reproducao/pipeline-de-edicao.md`): espada, lâmina longa,
broquel e cajado passam por `write_vox → load_vox → write_vox` com bytes idênticos; a espada passa pelo
Goxel e volta com posições, cores e materiais idênticos. LibreSprite e Blockbench não fazem parte do
fluxo da E (sem texturas pintadas; cuboides só na direção C histórica).

## Fluxo completo (resumo)

`tools/*.py` (+ `tools/fontes/*.vox`) → `build_assets.py` → `assets/` → viewer (`snap.sh`, `anim.sh`) →
QA (`asset_audit.py`, `./gradlew test`, `viewer --check`, comparação com `docs/art/reference/`) →
correção no script → regeneração. Reprodução numa cópia limpa: `tools/repro_compare.py`.

## Proveniência

Tudo em `assets/` é gerado por scripts deste repositório (determinístico: dois builds dão os mesmos
voxels; a compressão PNG pode variar com a versão do zlib — verificado pixel a pixel). Nenhum asset
de terceiros, nenhuma imagem por IA, nada extraído de jogo. Ver [../research/riscos-legais.md](../historico/pesquisa/riscos-legais.md).

## Expansão de conteúdo (rodada 06): onde mora a fonte de cada categoria

| Categoria | Fonte editável | Gerar | Ver | Testar |
|---|---|---|---|---|
| NPC humanoide | `tools/npcs_e.py` + `assets/anim/<npc>.json` | `python3 tools/build_assets.py npcs_e` | `set=r6-npc` (LINHA), `layout=vitrine_e` | `ExpansionTest`, `viewer --check` |
| Criatura | `tools/creatures_e.py` (uma função por espécie) | `... creatures_e` | `set=r6-criaturas` | `ExpansionTest`, `AssetContractTest` |
| Árvore / estrutura | `tools/env_e.py` (`ps.add(...)`) | `... env_e` | `layout=vitrine_e` + `tools/prop_silhouette.py` | `ExpansionTest`, `tools/asset_audit.py` |

Regra: a vitrine e os conjuntos `r6-*` servem para avaliar; a clareira canônica e as 22 referências só mudam
com aprovação. Pacote de revisão: `tools/review6.sh`.
