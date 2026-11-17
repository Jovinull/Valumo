---
name: voxel-art
description: Modelar e pintar voxels na identidade consolidada (direção E) — escalas de voxel por categoria, sombra pintada em bandas, paleta semântica, materiais por faixa de paleta, vegetação, arquitetura, exportação .vox, edição no Goxel e fusão de faces. Use ao criar/alterar props, árvores, construções ou ao pintar qualquer modelo.
---

# Arte voxel (direção E)

Referências: `docs/art/visual-identity.md` (§4, §8–§14), `docs/art/paleta-e-materiais.md`,
`docs/art/regras-de-vegetacao.md`, `docs/art/estilo-arquitetonico.md`, `docs/art/pipeline-de-producao.md`.
Código: `tools/env_e.py` (ambiente E), `tools/env_b.py` (formas-base), `tools/voxlib.py`, `tools/envlib.py`.

## Densidade (não invente escalas)

| Categoria | Voxel | Exemplos |
|---|---|---|
| Herói/NPC, criatura de referência | 1/40 u | `characters/e/*`, pompom |
| Criatura comum | 1/32 u | lumi |
| Árvores, casa, pedras, arbustos, terreno | 1/8 u | `tree_*`, `house` |
| Objetos de mão / médios | 1/16 u | barril, lanterna, placa |
| Flores e grama | 1/24 u | `flower_*`, `grass_*` |
| Criatura grande | 1/16 u | tortuga |

## Pintura

- Cor-base sempre com `P("#hex", material)` (registra rampa sombra-roxa/luz-pêssego); depois
  `shade()` (personagem: `charlib_e.shade`; ambiente: `chars_b.shade`; copa: `env_e.canopy_paint`).
- Bandas largas. **Proibido** ruído de 1 voxel, manchas aleatórias em telhado/estuque, `speckle` em área
  grande. Variação de cor = manchas de 4+ voxels com amplitude baixa.
- 3–5 matizes por modelo; sem preto/branco puros; azul só em íris, magia e flor.
- Materiais pela faixa de paleta: metal (lâmina, ferragem — nunca área grande clara), emissivo (ponto
  focal ≤ 3%), folhagem (tudo que balança; o prop declara `affineWind=True` em `PropSet.add`).

## Vegetação e construção (resumo)

- Árvore = novelos + ≤ 7 tufos grandes (`env_e.crown`). Nova espécie só com silhueta distinta das 7.
- Construção = base de pedra + estuque liso + enxaimel + telhado côncavo em fiadas + luz quente + toque
  vegetal + cisalhamento 0,08.

## Custo

Faces expostas (`voxlib.exposed_faces`) — herói ≤ 9 mil; criatura nível 1/2/3 ≤ 3/6/15 mil. Cores
contínuas e AO uniforme deixam a fusão exata do mesher trabalhar (ADR 0007): terreno da E sem jitter,
folhagem com vento afim.

## Exportar e editar

`python3 tools/build_assets.py env_e` gera `.vox` (MagicaVoxel 150) + `props.json`. Para inspecionar
num editor: `flatpak run io.github.guillaumechereau.Goxel assets/environment/e/house.vox`. Exportar
imagem sem abrir UI: `flatpak run --filesystem=$PWD --command=goxel io.github.guillaumechereau.Goxel
$PWD/assets/... --export $PWD/output/tmp/x.png`. Edição manual: ver "Edição à mão" no pipeline.

## Antes de entregar

Captura no mundo (`mode=scene,style=e`), close, luz neutra e panorama; checklist de prop em
`docs/validation/checklist-de-aceitacao.md`; `./gradlew test`.

## Rodada 03

- Carvalhos: `env_e.crown_masses` (3–4 massas achatadas, avesso escuro, franja de cachos); estudos
  O1–O3 em `assets/studies/`. Pinheiro, bétula e árvore-marco mantêm forma própria.
- Criaturas grandes: ecossistema próprio nas costas (tortuga S3), nunca "árvore no casco".
- Motivos de assinatura: P1 aprovado (uso moderado); P2 e P3 propostos, não aprovados (`docs/art/assinatura-visual.md`).

## Rodada 05 — limpeza, edição externa e pipeline

- **Limpeza estrutural**: `charlib_e.finish` remove componentes < 4 voxels de cada parte
  (`prune_specks`, registra no log). Traço fino que precisa existir (pena, fronde) desenhe com
  `connect_path` (linha 6-conexa), nunca voxel a voxel na diagonal.
- **Auditoria**: `python3 tools/asset_audit.py assets/<pasta> [--md saida.md]` — voxels soltos,
  fragmentos, cores e `.vox` **órfãos** (não referenciados pelo `rig.json`). Heróis: 0 soltos/fragmentos.
- **Cisalhamento**: tudo que fica numa parede fora de prumo (janela, porta, moldura) é desenhado
  acompanhando `sh(y)`; recortar depois gera degrau (reprovado `r6`). Toda face visível recebe acabamento.
- **Itens**: três tons por seção (fio claro, corpo médio, dorso/sulco escuro) — reprovado `r3`.
- **Edição num editor (Goxel/MagicaVoxel)**: `flatpak run --command=goxel io.github.guillaumechereau.Goxel
  in.vox --export out.vox` preserva posições e cores mas **reescreve a paleta** (perde o material).
  Guarde a peça editada em `tools/fontes/` e importe com `voxlib.load_vox(path, like=modelo_original)`,
  que restaura o material pela cor. Ida e volta verificada na rodada 05 (sem perda). Nunca salve por cima
  de `assets/`.

### Pipeline (conteúdo transferido da skill `asset-pipeline` da rodada 01)

- Fonte de verdade: `tools/*.py`, `assets/scenes/*.json`, `assets/anim/*.json`. Todo o resto de `assets/`
  é gerado e pode ser reconstruído (`python3 tools/build_assets.py`, ~9 s).
- **Determinismo**: sem relógio, sem `random` sem semente (`envlib.hash3(x, y, z, seed)`). Prova:
  `.venv/bin/python tools/repro_compare.py assets assets <copia>/assets` (bytes de .vox/.json, pixels de PNG).
- Nomes: rig em `characters/<estilo>/<nome>`, `creatures/<estilo>/<nome>`; parte = nome + `.vox`; props em
  minúsculas com `_`, radical + variante (`tree_oak2`, `rock_s`).
- Prop novo na cena: modele em `env_e.py` → contrato → `assets/scenes/clearing_e.json` → captura de escala.
- Mudar um formato: escritor (Python) e leitor (Java) no mesmo trabalho + contrato + `AssetContractTest`.
- Versione as saídas de `assets/` (pequenas); antes de entregar, `build_assets` + `./gradlew test` e
  confira que o `git status` não mostra saída desatualizada (variantes dependem do `charlib_e`).

## Rodada 05.1

- **Recorte de partes depois da pintura**: para dar movimento a um acessório já modelado (mochila, ponta
  de cachecol), construa e pinte tudo junto (`shade`), depois MOVA os voxels para uma `Vox` nova pela região
  ou pelo conjunto de coordenadas do traço original (ex.: `chars_e.adventurer`, `pk`/`st`). As cores ficam
  idênticas e a captura em repouso não muda.
- **Peças soltas em props**: a auditoria (`asset_audit.py`) aponta; corrija ligando a peça (talo, emenda)
  ou removendo o voxel flutuante; traço diagonal intencional e faíscas/sementes de luz ficam (justifique).
- Fontes editáveis e materiais: sem mudança (ver "Exportar e editar" e o pipeline).

## Rodada 06 — árvore nova, estrutura nova

- Copa pendente (salgueiro): receita em `docs/art/regras-de-vegetacao.md` (rodada 06). Folhagem tem
  prioridade sobre galhos (sem pintas de casca); cortinas com VÃOS; nada de bola solta no topo.
- Construção nova: `docs/art/estilo-arquitetonico.md` (rodada 06). O cisalhamento de fora-de-prumo quebra
  peças de 1 voxel em pedaços diagonais → `connect_path`/2 de largura; audite com `tools/asset_audit.py`.
- Compare silhuetas de props na mesma escala: `.venv/bin/python tools/prop_silhouette.py saida.png a b c`.
- Avalie no mundo pela vitrine (`layout=vitrine_e`), nunca mexendo na clareira canônica.
