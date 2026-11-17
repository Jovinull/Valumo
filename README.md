# Valumo

RPG voxel 3D de fantasia em terceira pessoa, com personagens pequenos e expressivos, em **engine própria**
(Java 25 + LWJGL 3 + OpenGL 3.3 core, sem game engine pronta).

**Estado: protótipo artístico e técnico, não um RPG jogável.** O repositório tem um visualizador que fixa a
identidade visual, as ferramentas de modelagem por script, a animação procedural com coreografia de armas,
validadores e cenas de demonstração. Não há combate de verdade, inventário, diálogo, salvamento ou mundo
aberto.

Direção de arte oficial: **E — "Fábula Voxel"** ([identidade](docs/art/visual-identity.md),
[contrato de qualidade](docs/art/contrato-de-qualidade.md), [referências canônicas](docs/art/reference/README.md)).

## O que existe

| Área | Situação |
|---|---|
| Heróis | aventureiro, exploradora e maga — aprovados e congelados (22 imagens canônicas) |
| NPCs | cartógrafa; **Kelyra** (estalajadeira), **Índira** (herbalista) e **Súvia** (mensageira) — candidatas |
| Criaturas | pompom, tortuga, fungo, lumi (aprovados) e mariposa (candidata) |
| Cenário | clareira canônica; vitrine da expansão; vila (NPCs da vila) |
| Animação | locomoção com IK de pé, golpes por perfil (espada, escudo, lâmina longa, arco, cajado), gestos de NPC com atuação por personagem |
| Validação | testes Java, validador espacial de golpes, oclusão de rosto/arma, sonda de pegada, auditoria de voxels, reprodução determinística |
| Personalização | só **prova técnica** (cabelo, cor de cabelo, chapéu sobre o aventureiro) — [estudo](docs/architecture/personalizacao.md) |

Rodadas: 01 comparou quatro direções (A–D), 02 consolidou a E, 03 poliu, 04 refez a coreografia de armas,
05/05.1 auditaram e provaram reprodutibilidade, 06 fez a primeira expansão controlada e **06.1** refinou os
NPCs, investigou desempenho e estudou a personalização ([relatório](docs/validation/relatorio-rodada-06-1.md)).

## Requisitos

- Linux x86-64 com OpenGL ≥ 3.3 (testado em Mesa 26.2 / Intel Iris Xe).
- JDK 25 (o Gradle vem pelo wrapper).
- Python ≥ 3.10 (somente stdlib) para regenerar os assets.
- Opcional (QA): `.venv` com numpy/Pillow (`python3 -m venv .venv && .venv/bin/pip install numpy pillow`),
  ffmpeg para vídeos. Detalhes: [docs/validation/ambiente.md](docs/validation/ambiente.md).

## Executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current   # ou o caminho do seu JDK 25
python3 tools/build_assets.py                        # gera assets/ a partir dos scripts (segundos)
./gradlew :viewer:run                                # visualizador na clareira canônica
./gradlew :viewer:run --args="--layout vila_e"       # a vila: cartógrafa, Kelyra, Índira e Súvia
./gradlew :viewer:run --args="--layout vitrine_e"    # vitrine da rodada 06
```

Os assets gerados **não são versionados**: depois de clonar, rode `build_assets.py` (a geração é
determinística e leva segundos). Depois de `./gradlew :viewer:installDist`, o mesmo binário fica em
`viewer/build/install/viewer/bin/viewer`.

### Ver os NPCs

- Na vila (`--layout vila_e`), ande com `W A S D` até o posto de trilha.
- Modo linha: `Tab` até **comparação em linha** e `V` até os conjuntos `trio` (as três), `elenco` (heróis +
  NPCs) ou `vila`.
- Sem janela:
  `tools/snap.sh "mode=lineup,style=e,set=elenco,myaw=20,out=output/tmp/elenco.png"` e
  `tools/snap.sh "mode=char,style=e,hero=characters/npc/indira,yaw=30,out=output/tmp/indira.png"`.

### Controles

| Entrada | Ação |
|---|---|
| `1` … `5` | direção artística (5 = E, padrão; 1–4 = direções históricas A–D) |
| `Tab` | modo: cena → personagem isolado → lado a lado → proporções → comparação em linha |
| `C` / `V` | próximo personagem ou criatura / próximo conjunto do modo linha |
| `Q` / `E` | clipe de animação anterior / seguinte |
| `X` | expressão facial (auto, neutra, alegre, determinada, surpresa) |
| `L` / `O` / `K` | luz da E (dia, tarde, neutra) / contorno 0–3 / silhueta chapada |
| `P` / `M` | qualidade (baixa, padrão, alta) / métricas |
| `T` | girar o modelo |
| arrastar botão esquerdo / roda / botão direito | orbitar / zoom / subir-descer o alvo |
| `W A S D`, `Shift`, `Espaço`, `R`, `G` | (cena) andar, correr, atacar, comemorar/gesto, reagir a dano |
| `F5` `F6` `F7` `F8` | sombras, oclusão de ambiente, névoa, pós-processo |
| `F9` / `F10` | sem textura / iluminação neutra de estúdio |
| `N` | (proporções) mesma altura × mesmo voxel |
| `H` / `F12` / `Esc` | HUD / captura em `output/visual-review/manual/` / sair |

## Validar

```bash
./gradlew test :viewer:installDist                         # testes (engine, contrato, qualidade, NPCs, modularidade)
viewer/build/install/viewer/bin/viewer --check             # golpes e gestos sem interseção (0 falhas)
viewer/build/install/viewer/bin/viewer --occlusion         # rosto coberto / arma escondida (exceção conhecida: arco)
python3 tools/asset_audit.py assets/characters/npc         # voxels soltos e fragmentos
tools/reference.sh output/tmp/ref && .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref
python3 tools/check_links.py                               # links da documentação
viewer/build/install/viewer/bin/viewer --bench "1920x1080,style=e,runs=3,frames=300,layout=vila_e"
```

O bench descarta 400 quadros de aquecimento (a GPU integrada demora ~3 s para subir de frequência); use
`dump=arquivo.csv` para tempo por quadro e GC. Capturas e vídeos: `tools/snap.sh`, `tools/anim.sh`; pacotes
de revisão por rodada em `tools/review*.sh` (o da 06.1 é `tools/review61.sh antes|depois`).

## Onde está cada coisa

```
engine/    núcleo: janela, shaders, malhas, leitor .vox, mesher, câmera, FBO, HUD
viewer/    visualizador: estilos, renderer, animador, coreografia, mundo, modos, captura, bench, validadores
tools/     fontes dos assets (Python): heróis (chars_e, charlib_e), NPCs (npcs_e, npcs_trio), criaturas,
           ambiente, prova de personalização (custom_e) + utilitários de QA
assets/    feitos à mão (versionados): scenes/*.json e anim/*.json; gerados por build_assets.py (fora do git):
           characters/ (e = heróis, npc = NPCs, custom = prova modular, r3/r4/variants = estudos), creatures/,
           environment/, textures/, studies/
docs/      documentação oficial (visão, arte, arquitetura, decisões, validação, processo) + historico/
.claude/skills/  procedimentos para agentes
output/    local, fora do git: capturas e pacotes de revisão por rodada (visual-review/), temporários (tmp/)
```

Comece por [docs/README.md](docs/README.md).

## Limitações conhecidas

- Protótipo: sem jogo (combate real, IA, diálogo, inventário, save).
- Interromper um golpe por dano ainda atravessa o corpo em algumas janelas (espada, lâmina longa).
- NPCs e assets da rodada 06 são candidatos; NPCs têm só um gesto compartilhado.
- Personalização é só prova em tempo de geração; o criador de personagens não existe.
- Capturas, vídeos e pacotes de revisão (`output/`) não são versionados; os relatórios em `docs/validation/`
  citam esses caminhos locais, regeneráveis pelos scripts de revisão.

Lista completa: [docs/validation/problemas-conhecidos.md](docs/validation/problemas-conhecidos.md).

## Originalidade

Inspiração apenas no *gênero* de RPG voxel de exploração. Nenhum modelo, textura, som, animação ou interface
de outro jogo foi usado ou reconstruído; todos os assets são gerados pelos scripts deste repositório.
Ver [docs/historico/pesquisa/riscos-legais.md](docs/historico/pesquisa/riscos-legais.md).
