# Guia para agentes neste repositório

**Valumo** — RPG voxel original (protótipo com exploração jogável, combate com som, mobilidade — golpe andando, esquiva, salto —, mira opcional, grupos e a primeira aventura completa com missão, diário e jogo salvo) em Java 25 + LWJGL 3 (OpenGL 3.3 core), engine própria. Direção oficial: **E —
"Fábula Voxel"**. A–D existem só para comparação. Produção de assets novos **só com aprovação do
proprietário**.

**Estado:** **Rodada 10 — "As Luzes do Posto"** aprovada pelo proprietário após teste físico e fechada no checkpoint
`adventure-foundation-v0.4` ([relatório](docs/validation/relatorio-rodada-10.md)). É a referência de qualidade de
gameplay, mas **não** é o modelo de produção: o Valumo terá mundo procedural. **Rodada 11** (fundação procedural:
uma região por semente, POIs, missão instanciada, save v2) e **Rodada 11.1** (porta de entrada: menu principal com a
vila ao fundo, personagens, mundos criados pela interface, aventura = personagem × mundo, X da janela com confirmação)
**publicadas** na tag `worlds-and-menus-v0.5` ([fechamento](docs/validation/relatorio-fechamento-rodadas-11.md);
relatórios [11](docs/validation/relatorio-rodada-11.md) e [11.1](docs/validation/relatorio-rodada-11-1.md)). **Lotes 1 e
2 de conteúdo** (PRs #3 e #5) integrados como **biblioteca**: 35 modelos, 6 variantes e 5 conjuntos no catálogo e nas
vitrines ([relatório](docs/validation/relatorio-integracao-conteudo.md)). **Rodadas 12 a 12.3 — "Mundos com
Identidade"** APROVADAS pelo proprietário no teste físico completo e **publicadas** na tag `procedural-world-v0.6` ([relatório da publicação](docs/validation/relatorio-publicacao-v2.md)): o gerador **v2
publicado e congelado** como a v1 ([ADR 0029](docs/decisions/0029-versoes-do-gerador.md); o que ele congela em
[mundo procedural](docs/architecture/mundo-procedural.md) §13; mudar o resultado de uma semente agora é a **v3**),
[colisão composta](docs/architecture/colisao-composta.md), vilas de caminho e rurais com os cinco ofícios com morador,
quatro ruínas, [descobertas por tipo de lugar](docs/architecture/descobertas.md), a câmera da torre por
[recorte contextual](docs/architecture/recorte-da-camera.md) e o Acapela na biblioteca, sem papel nas vilas (relatórios
[12](docs/validation/relatorio-rodada-12.md), [12.1](docs/validation/relatorio-rodada-12-1.md),
[12.2](docs/validation/relatorio-rodada-12-2.md), [12.3](docs/validation/relatorio-rodada-12-3.md)). Limitações de câmera
aceitas no checkpoint: P95–P97. **Rodadas 13 e 13.1 — fundação do inventário** APROVADAS pelo proprietário e
**publicadas** na tag `inventory-foundation-v0.7` ([relatório da publicação](docs/validation/relatorio-publicacao-inventario-v07.md)):
5 itens por dados em 4 categorias, o presente da Índira e os achados das descobertas como camada sobre os mundos
congelados, a cataplasma, campos opcionais no save ([relatório 13](docs/validation/relatorio-rodada-13.md),
[ADR 0031](docs/decisions/0031-itens-e-recompensas.md)); a mochila viva da 13.1: painel translúcido SOBRE o jogo
correndo, sem limite de espaços, seletor pelo mouse, itens pelos modelos voxel
([relatório 13.1](docs/validation/relatorio-rodada-13-1.md), [ADR 0032](docs/decisions/0032-mochila-viva.md)).
Limitações aceitas no checkpoint: P100–P104, P106, P108–P110. A Rodada 14 só começa com o planejamento do proprietário.
Os checkpoints anteriores continuam: `procedural-world-v0.6`, `worlds-and-menus-v0.5`,
`adventure-foundation-v0.4` e `engineering-foundation-v0.3` ([relatório do QA](docs/validation/relatorio-fechamento-qa.md)). O Titã Celeste (Velária) é candidato num ramo LOCAL
(`candidato/tita-celeste`), fora da `main`, à espera da revisão de originalidade.

## Leia antes de mexer

1. `docs/README.md` — índice (oficial × histórico).
2. `docs/art/visual-identity.md` §0 (invariantes × flexíveis) e `docs/art/contrato-de-qualidade.md`.
3. `docs/art/reference/` — imagens canônicas e reprovados.
4. A skill da tarefa em `.claude/skills/`: `character-design`, `voxel-art`, `weapon-animation`,
   `rendering`, `visual-qa`, `git-commits`, `character-customization`, `game-ui`, `gameplay`, `combat`, `audio`,
   `music-composition`, `sound-design`, `audio-qa`, `engineering-qa` (avisos da IDE, Gradle, vazamentos, desempenho, falso verde),
   `quest-design` (missões, objetos com estado, falas por contexto, matriz de travamentos), `conteudo-mundo` (biblioteca de
   conteúdo: asset, variante, conjunto, vitrine, catálogo `valumo.conteudo/1`), `mundo-procedural` (semente,
   região, POI, validação espacial, vínculos de missão, save v2); menus da 11.1 em `game-ui`.
5. `docs/validation/problemas-conhecidos.md` — o que está aberto e o que é limitação aceita
   (histórico completo: `matriz-de-problemas.md`).

## Comandos essenciais

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew editor                                    # criador de personagens do jogador (gera assets antes, se preciso)
./gradlew editorDebug                               # editor técnico da personalização (rodada 07)
./gradlew jogo                                      # menu principal → personagem → mundo (vila ou gerado) → aventura (rodada 11.1)
./gradlew jogo -Dvx.menu=nao                        # entrada antiga: escolha de aparência na vila (padrão dos roteiros)
tools/review111.sh [capturas|resolucoes|video|desempenho|transicoes|testes|validadores|regressao|zip]  # 11.1: menu (tools/menu/), dados TEMPORÁRIOS
./gradlew jogo -Dvx.mundo=procedural -Dvx.gerador=1|2 -Dvx.semente=1024   # rodada 12: região v1 (congelada) ou v2 (padrão dos mundos novos)
tools/review12.sh [vistas|comparacoes|demo|demo-v1|saves|desempenho|testes|validadores|zip]   # rodada 12: v2, v1 × v2, demo, saves antigos (RAIZ_ANTIGA=...)
tools/review121.sh [vistas|antes|comparacoes|laje|demo|demo-v1|saves|desempenho|testes|validadores|zip]   # 12.1: BASE=clone da rodada 12, RAIZ_ANTIGA=main publicada
tools/review122.sh [vistas|antes|comparacoes|demo|demo-v1|saves|desempenho|testes|validadores|zip]   # 12.2: BASE=clone da 12.1 (output/tmp/base-r121), PARTE=regioes
tools/review123.sh [videos|menu|comparacoes|desempenho|testes|validadores|zip]   # 12.3: BASE=clone da 12.2 (output/tmp/base-r122); torre antes × depois, Índira, pote
tools/review13.sh [videos|capturas|desempenho|saves|testes|validadores|zip]   # 13: mochila; BASE=clone da v0.6 (output/tmp/base-v06); roteiros tools/itens/
tools/review131.sh [videos|capturas|desempenho|saves|testes|validadores|zip]  # 13.1: mochila viva; BASE13=clone da 13 (output/tmp/base-r13), BASE06=clone da v0.6
python3 tools/conteudo/formas_compostas.py          # formas compostas medidas dos voxels (12.1: topos exatos, alcance do salto) → colisao_e.json (revise o diff)
./gradlew jogo -Dvx.savegame=/tmp/x                 # idem com um save de teste (padrão output/saves; "-" desliga)
./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024   # rodada 11: região gerada pela semente (save em output/saves-procedural/s1024/)
tools/review11.sh [vistas|videos|desempenho|testes|validadores|regressao|zip]   # rodada 11: sementes 1024 2048 4096 77 (tools/mundo/)
tools/review10.sh [capturas|videos|marco|casos|desempenho|testes|validadores|regressao|zip]  # rodada 10: aventura (tools/aventura/), saves TEMPORÁRIOS
./gradlew jogo -Ptecnico                            # idem com F3 = hitboxes, hurtboxes e estados do combate
tools/review9.sh [capturas|camera|videos|angulos|interrupcoes|validadores|desempenho|testes|zip]  # pacote do combate (tools/combate/)
tools/review91.sh [camera|videos|sons|desempenho|testes|validadores|regressao|zip]  # 09.1: câmera de luta e som (tools/combate91/)
tools/review92.sh [videos [NN]|comparacoes|animacao|capturas|desempenho|testes|validadores|regressao|zip]  # 09.2: mobilidade, mira, grupos, morte (tools/combate92/)
tools/review93.sh [animacao|videos [NN]|antes|desempenho|testes|validadores|regressao|documentos|zip]  # 09.3: esquiva R2, salto, câmera perto (tools/combate93/)
viewer/build/install/viewer/bin/viewer --check golpe-andando   # golpes com as pernas andando (4 rumos × 4 fases): 0 problemas
viewer/build/install/viewer/bin/viewer --check esquiva         # cortes golpe→esquiva R2/recuo nas janelas medidas + saídas: segura
./gradlew jogo -Dvx.sensacao=A|B|C -Dvx.encontro=um|grupo|chefe -Dvx.morte=D2|D1|antiga   # comparar (também na pausa)
python3 tools/audio_e.py                            # efeitos do combate sintetizados → assets/audio/sintese/ (gerados; mistura em assets/scenes/sons_e.json)
viewer/build/install/viewer/bin/viewer --check interrupcao-fina   # golpes jogáveis cortados a cada 0,01 s: 0 inseguros
tools/review8.sh [capturas|videos|desempenho|testes|zip]  # pacote do jogo (entrada por roteiro, tools/jogo/)
tools/review81.sh [temporal|dentes|referencias|regressao|desempenho|testes]  # malha conforme: câmera lenta, legado × conforme
tools/review72.sh [capturas|video|desempenho|testes] # pacote do criador (entrada real por roteiro)
python3 tools/build_assets.py [módulo ...]          # regenera assets à mão (o Gradle faz sozinho antes de run/test)
./gradlew test :viewer:installDist                  # testes + viewer usado pelas capturas
viewer/build/install/viewer/bin/viewer --check      # validação espacial das armas (0 falhas)
viewer/build/install/viewer/bin/viewer --occlusion  # rosto coberto / arma escondida em 8 câmeras
viewer/build/install/viewer/bin/viewer --grip characters/e/ranger clip:HIT   # item acompanha a mão?
python3 tools/asset_audit.py assets/characters/e    # voxels soltos, fragmentos, órfãos
tools/snap.sh "mode=lineup,style=e,set=herois,out=output/tmp/x.png"   # captura (REBUILD=1 após Java/GLSL)
tools/anim.sh output/tmp/x "mode=lineup,style=e,set=herois,clip=walk" 60 30   # vídeo
tools/reference.sh [pasta]                          # refaz as referências canônicas
python3 tools/check_links.py                        # links da documentação
tools/qa/medir_desempenho.sh BINARIOS ROTULO [N]    # QA de engenharia: vila, combate, criador e bench, antes × depois (skill engineering-qa)
tools/qa/saves_antigos.sh RAIZ_ANTIGA . output/tmp/s   # saves (v1, v2, menu) feitos por um binário antigo, abertos pelos dois: iguais, sem .invalido
game/build/install/game/bin/game --roteiro tools/menu/14-memoria.txt SAIDA 30   # malhas/MB vivos e tempos (dados TEMPORÁRIOS; cabeçalho do roteiro)
tools/qa/audio_tempo_real.sh ROTEIRO SAIDA          # som em TEMPO REAL pelo backend wave do OpenAL Soft (nada nos alto-falantes) + buracos
python3 tools/qa/sessao_longa.py N > R              # sessão longa: vila, conversa, pausa, combate, criador; "amostra" = recursos vivos
python3 tools/qa/ide_diagnosticos.py . /tmp/jdt/ws x.json   # o que a aba Problems mostraria num import limpo (0 erros)
python3 fontes/audio/musica/vila/vila_a.py          # partitura → sessão LMMS (.mmp) + .mid (áudio: skills de áudio)
.venv/bin/python tools/audio/render_musica.py SESSAO.mmp SAIDA [--runtime assets/audio/musica/ID]  # render + laço + master
.venv/bin/python tools/audio/render_sfx.py SESSAO.mmp SAIDA --alvo-rms -26   # efeitos: fatia, mono, consistência
.venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json   # QA técnico de todo o áudio do jogo
tools/review94.sh [audio|contexto|ambiente|criador|cabelos|armas|olhos|botas|pausa|gate|testes]  # pacote da rodada 09.4 (áudio e personalização)
viewer/build/install/viewer/bin/viewer --check-cabelos  # arma × cabeça composta (cabelo personalizado), voxel a voxel
```

Mais comandos (bench, pranchas, golpe com depuração, bancada de pés): `README.md` e as skills.

## Regras

- **Sem game engine.** Só LWJGL, JOML e Gson. Dependência nova exige ADR em `docs/decisions/`. Interface de
  jogo usa `UiBatch` + `PixelFont` (fonte própria) + fila de eventos da janela ([ADR 0012](docs/decisions/0012-interface-do-jogo.md)).
- **Malha conforme** (ADR 0014): nunca sobreponha nem recue faces para tapar fresta. Defeito de cintilação ou
  fresta se investiga com as três malhas (`-Dvx.mesh=legado`, padrão, `-Dvx.merge=false`) e em câmera lenta
  (`tools/review81.sh temporal`). As invariantes ficam em `MeshSeamTest`.
- **Assets são gerados.** Nunca edite `assets/characters`, `assets/creatures`, `assets/environment`,
  `assets/textures`, `assets/studies`, `assets/components`, `assets/audio/sintese`, `assets/items` à mão: edite `tools/` e regenere. Fontes à mão: `assets/scenes/*.json`
  e `assets/anim/*.json`. Peça desenhada num editor entra via `voxlib.load_vox` (ver `pipeline-de-producao.md`).
- **Armas:** golpe = trajetória no perfil (`assets/anim/`). `viewer --check`, `--occlusion` e `--grip` sem
  alerta + vídeo em 4 ângulos (passar no validador não basta: arma pode sumir atrás do braço). **Nunca** aumente tolerância para passar: corrija pose ou volume (`*_volumes`) com justificativa.
- **Combate** (rodada 09, ADR 0015):
  - dano só pela trajetória da arma, na janela `ativo`, em passos fixos de 1/120 s;
  - hitbox, hurtbox e colisão são separadas;
  - toda transição que o jogo produz passa no validador; instantes de encadear, liberar e agir são medidos, nunca
    chutados;
  - a camada ao vivo (`ActionLayer`) = `Sequencer`;
  - o herói é desenhado com a pose exata;
  - skill `combat`.
- **Câmera de luta** (rodada 09.2, ADR 0020): mira LIVRE por padrão (o mouse manda: nada gira nem sobe sozinho; só
  ombro e busca de vista da luta) e TRAVADA opcional (Q / Tab / Shift+Tab). O ajuste automático da 09.1 só em
  `-Dvx.camera=091`. Mede-se com `CameraFraming` e `CameraModesTest` (os 10 cenários em movimento); nunca valide câmera
  só por posição fixa favorável. Fora da luta a câmera é idêntica à da 08 (teste passo a passo).
- **Mobilidade** (rodada 09.2, ADRs 0017–0019): o jogador dirige o corpo no golpe (teto por fase do perfil de sensação);
  as pernas seguem a passada real por baixo da ação (tronco/braços/arma = o golpe validado). Esquiva e salto são estado
  lógico (invulnerabilidade em passos, nunca quadros); janelas de esquiva MEDIDAS pelo validador
  (`esquiva.janelas`). Rodada 09.3: a esquiva é a R2, escolhida pelo proprietário (salto baixo `dodge_f`; de lado com a
  mira travada, `dodge_l`/`dodge_r` sem virar o corpo); animação nunca muda distância, invulnerabilidade nem recarga; o
  salto mudou só no desenho. Rodada 12.1 (pedido do proprietário, adendo da ADR 0019): a física do salto mudou (1,0 u,
  85% de controle no ar) e **todo sólido segura quem está em cima**, menos pessoa e objeto de missão (`Collision.supports`). Números só em `combate_e.json` (`sensacao`, `esquiva`, `salto`, `mira`, `grupo`, `encontros`).
- **Vários inimigos** (ADR 0021): uma classe `Enemy` para todos; a lâmina testa cada um, no máximo uma vez por golpe;
  nada de dano em área. Chefe = variante `chefe: true`; protótipo ampliado sempre marcado `prototipo`.
- **Morte** (ADR 0022): desagregação nos próprios blocos (`DeathFx`/`VoxelCloud`, uma malha dinâmica); a antiga só em
  `-Dvx.morte=antiga`.
- **QA de engenharia** (skill `engineering-qa`): validador conta o que conferiu e falha com zero (golpe inexistente, rig
  sem volumes ou sem item = erro; exceção é lista explícita com motivo, como `OcclusionCheck.FACE_EXCEPTIONS`); quem cria
  `App`/`World`/`Assets`/malha fecha; o desenho nunca escreve em estado da lógica; leitor de arquivo recusa o malformado
  com o motivo (nunca laço, nunca alocação sem limite); preferência ilegível = padrão + original guardado em `.invalido`.
  CI (`.github/workflows/ci.yml`) roda o que não precisa de GPU; capturas, gate, referências e bench são QA local.
- **Som** (ADRs 0016 e 0023): um só dispositivo OpenAL por processo (`Audio`); os efeitos do combate tocam no mesmo
  contexto (`OpenAlBackend.shared`). O som **só lê** a lógica depois de cada passo (luta idêntica com e sem som, teste);
  jogo e interface emitem eventos (`Audio.event("ui.confirmar")`), nunca caminhos. Combate: síntese em
  `tools/audio_e.py` → `assets/audio/sintese/` (gerado). Catálogo (música, ambiente, passos, interface): fontes em
  `fontes/audio/` (partitura `.py`, sessão LMMS `.mmp`, `.mid`), arquivos versionados em `assets/audio/` (renderizar exige
  LMMS), catálogo `assets/audio/sons.json` à mão. Timbres: síntese própria nos sintetizadores do LMMS
  (`tools/audio/sintese.py`; equilíbrio medido com `tools/audio/nivelar.py`). Nada de amostra de terceiros nem de IA
  geradora de som (o banco FluidR3 GM saiu na 09.4, decisão do proprietário). Sem
  dispositivo = mudo, nunca erro. Roteiros e testes mudos; vídeos com o som da própria aplicação (`-Dvx.som=loopback`).
  Medir não é ouvir: diga quando não ouviu. Skills `audio`, `sound-design`, `music-composition`, `audio-qa`.
- **Missões e save** (rodada 10, ADR 0024, skill `quest-design`):
  - **Missão = dados:** `missoes_e.json`, `dialogos_e.json`, `encontros` com identidade estável e `jogo.objetos`.
  - **Contagem:** o estado é derivado; evento só no passo lógico; o que contou nunca volta (`Combat.retired`); a
    recompensa sai uma vez.
  - **Jogo salvo** `valumo.save` v1: escrita atômica, leitor que recusa com o motivo e guarda `.invalido`, sem
    migração falsa. Salvamento automático só nos marcos e no primeiro passo seguro. Rodada 12.2: save ÍNTEGRO de uma
    região que mudou (versão de testes) ou de jogo mais novo é `SaveGame.Incompatible`: recusado SEM `.invalido` e sem
    tocar no arquivo; só JSON/formato quebrado vai para `.invalido` (`SaveCompatTest`, `save.md` §Rodada 12.2).
  - **QA nunca toca nos saves do proprietário:** `-Dvx.savegame=PASTA_TEMP`; os testes recebem `vx.savegame=-`.
- **Mundo procedural** (rodada 11, ADRs 0025–0026, skill `mundo-procedural`):
  - **Relevo:** é dado (`TerrainField`). `World.LEGACY` é o de antes, sem nenhuma conta nova (referências 22/22).
  - **Região:** é composta de componentes autorais, com RNG derivado por rótulo (`vx.game.world.Rng`, nunca
    `java.util.Random`, relógio ou ordem de `HashMap`).
  - **Validação:** só vale se passar na colisão real (`Reachability`), com no máximo 4 tentativas; senão a semente é
    recusada com motivo.
  - **Missões:** vêm de modelo + fatos (sem fatos, sem missão), com ids estáveis e sem coordenadas.
  - **Versões** (rodada 12, [ADR 0029](docs/decisions/0029-versoes-do-gerador.md)): cada mundo é gerado, para sempre,
    pela versão com que nasceu (`mundo.json`/save v2 → `ProceduralScene.create(assets, semente, versão)`); `LATEST = V2`
    para mundos novos. Mudou o resultado de uma semente → é uma VERSÃO NOVA (classe nova, rótulos novos), nunca uma mudança
    no caminho de uma versão publicada. As 8 sementes de cada versão estão fixadas (`RegionGoldenTest`, `regioes-v1.txt`,
    `regioes-v2.txt`); **nunca** atualize uma referência para passar. Versão desconhecida = não abre, nada tocado, sem
    `.invalido`. Guia: `docs/processo/como-evoluir-o-gerador.md`.
  - **v2** (`RegionV2`): lê o perfil CONGELADO `assets/scenes/regiao_v2_e.json` (nunca o catálogo gerado); conjuntos
    postos inteiros; restritos só em conjuntos; a ruína com patamar, trilha e descoberta. Rodada 12.1: ruína de casa OU
    torre (`RuinKind`), vila de caminho pequena ou média (`"porte"`), morador pelo OFÍCIO do conjunto (`oficios` no
    perfil; nunca `if nome ==`), patamares planos por projeto (`TerrainField.flat`, só v2). Rodada 12.2: QUATRO ruínas
    (casa, torre, abóbada no declive, sítio em chão aberto; ordem sorteada com peso, `"ruina-tipos"`), uma por região;
    o ponto do trabalhador vem do perfil (`conjuntos.*.trabalhador`: vaga recomendada ou spawn); `RESIDENTS` (que a v1
    usa) nunca muda — morador só de ofício entra pelo perfil.
  - **Publicação:** `RegionGenerator.published(v)` = v1 e v2 (tag `procedural-world-v0.6`); as duas estão congeladas e
    as referências delas nunca são refeitas. Do que a v2 depende: `mundo-procedural.md` §13. Mundos v2 das versões de
    testes (rodadas 12–12.3, o "RUINAS") não migram: save que não bate é "de uma versão de testes"
    (`RegionGenerator.hadTestBuilds`), sem `.invalido`. Próxima mudança de composição = **v3**.
  - **Save v2:** guarda o mundo e os vínculos, e recusa diferença. O v1 fica intacto.
- **PR #8:** a parte aprovada entrou por merge local de `f92e6a2` (12.2); o Acapela entrou na 12.3 (merge `d2a7134`, os
  commits do artista preservados), na biblioteca e SEM papel nas vilas (`habitantes_e.json` e perfil da v2 intocados).
  Nunca traga trabalho não aprovado junto ([registro](docs/validation/integracao-pr8.md)).
- **Itens e recompensas** (rodada 13, [ADR 0031](docs/decisions/0031-itens-e-recompensas.md), [itens](docs/architecture/itens.md),
  [inventário](docs/architecture/inventario.md), [recompensas](docs/architecture/recompensas-e-loot.md)):
  - **Item = dados** (`itens_e.json`, id estável `item.*`); **efeito = código** (`ItemEffects`, por nome; nunca
    reflexão).
  - **Fontes** (`recompensas_e.json`): só reagem a eventos estáveis (fim da conversa com um morador, descoberta de um
    tipo de lugar); NUNCA mudam a geração, `descobertas_e.json` nem `habitantes_e.json`. Fonte que exige mudar a região =
    v3.
  - **Uma vez por aventura, tudo ou nada** (`ItemProgress.grant`): a fonte só é marcada se tudo entrou; mochila cheia = a
    fonte espera.
  - **Save:** `progresso.inventario` e `progresso.fontesColetadas` são OPCIONAIS, sem versão nova; item desconhecido =
    jogo mais novo (sem `.invalido`).
  - **Mochila** (rodada 13.1, [ADR 0032](docs/decisions/0032-mochila-viva.md)): CAMADA sobre a exploração
    (`GameLogic.bagOpen`, não um estado): o jogo continua correndo; o mouse é dela (câmera parada, sem golpe nem escudo,
    seletor ao passar, clique usa), o teclado continua no herói (setas e Enter são da grade); conversa, diário, pausa e
    derrota a fecham. SEM limite de espaços (lista de pilhas; `MAX_STACKS` só protege o leitor). Usar é ação da lógica,
    nunca do desenho.
  - **Itens em 3D:** modelos voxel de `tools/itens_e.py` (1/40 u, até 0,4 u) pelo MESMO renderizador com fundo
    transparente (`Renderer.Toggles.alpha`; desligado = pixel idêntico, referências e gate conferem), atlas uma vez + prévia
    girando num `Renderer` próprio (`ItemRenders`), sempre antes do lote 2D; arte final pelo
    [contrato do artista](docs/art/contrato-icones-de-itens.md).
- **Câmera em interior estreito** (12.3, [recorte](docs/architecture/recorte-da-camera.md)): zona `"recorte"` no perfil
  composto (só a torre caída); com o herói dentro, a câmera ignora SÓ aquela construção (`Collision.cameraSkip`) e o
  desenho a recorta num cone câmera → herói (`CameraCutaway`, `uCut`). O corpo nunca muda; fora da zona a câmera é a de
  sempre, passo a passo. Medir com `TowerCameraTest` (12 cenários × 5 rumos), nunca por captura parada.
- **Biblioteca de conteúdo** (lotes 1 e 2, ADR 0030, skill `conteudo-mundo`,
  [guia](docs/processo/como-integrar-conteudo-ao-mundo.md)):
  - **Registro × carga:** registrar no `props.json` não é carregar. O `Assets.props` malha só o que a cena pede; nunca
    itere o catálogo inteiro.
  - **Três aprovações separadas:** artística, integração e distribuição procedural. Aprovado na arte ≠ pronto para o jogo.
  - **Liberação:** só `liberado` entra em cena de jogo; `restrito_a_conjuntos` nunca sai solto.
  - **Colisão composta** (rodada 12, [contrato](docs/architecture/colisao-composta.md)): perfil `composta` em
    `colisao_e.json`, formas medidas dos voxels por `tools/conteudo/formas_compostas.py`. Rodada 12.1: topos EXATOS (o
    piso é a laje desenhada; as entradas têm degraus de pedra reais), nunca piso de colisão abaixo do desenho; folhagem
    presa acima de 1,7 u é fosca. Composto liberado ⇔ percorrido pelo `CompositeCollisionTest` em 5 rumos. Ponto de interação = o objeto (de
    pé a até 1 u); inalcançável é defeito do ponto ou do modelo, nunca da conferência.
  - **Gerador:** nada da biblioteca entra no gerador v1; a v2 distribui os liberados.
  - **Descobertas** (rodada 12; por TIPO de lugar na 12.1, [contrato](docs/architecture/descobertas.md)):
    `descobertas_e.json` (dados), `QuestSystem.discover` (uma vez). O achado é um objeto que se vê (pote cheio → vazio),
    "Lugar novo" só na primeira vez de um tipo, diário "Lugares", tudo derivado de `progresso.descobertas` (sem v3).
- **Porta de entrada** (rodada 11.1, ADRs 0027–0028, [menus](docs/architecture/menus.md)):
  - **Regras sem GL:** `MenuFlow` (telas, campos, pedidos); `Game` cumpre os pedidos em transições por passos com
    rótulo real (nunca porcentagem); `MenuView` desenha e fecha os próprios recursos.
  - **Fundo:** a vila fixa em estado `MENU` (nada de jogo roda). Tomadas em `apresentacao_e.json`, validadas na colisão
    real (`ApresentacaoTest`); tomada nova pelo guia `processo/como-adicionar-uma-tomada.md`.
  - **Personagem** (`valumo.personagem`) e **mundo** (`valumo.mundo`) têm id próprio; nome nunca é caminho; mundo ≠
    semente.
  - **Aventura = pasta do par:** `mundos/<mundo>/aventuras/<personagem>/`, save v1 (vila) ou v2 (gerado); **sem v3**.
  - **Saves de antes** são adotados sozinhos, uma vez, por **cópia** validada e registrada (sha256), nunca por cima de
    progresso; aparências soltas ficam no Carregar do criador.
  - **Sem texto de tecla nem de desenvolvimento na tela** (nenhum "↑ ↓ escolher", "(Esc)", "rodada", "legado").
  - **Sair:** "Menu principal" volta à tela inicial. Numa aventura, sair (X da janela interceptado, Sair do jogo, Menu
    principal) SEMPRE pede a mesma confirmação da pausa; `GameLogic.unsaved` só decide o que ela oferece. O criador tem cache (`App` vivo) e pergunta no X com
    alterações. Botões de um grupo usam a mesma escala (`groupPx`).
  - **QA:** `-Dvx.menu=sim` + `-Dvx.personagens/mundos/savegame/saves/prefs` temporários (`tools/review111.sh`).
    Roteiros antigos rodam sem menu.
- **Contrato:** nomes, eixos e escalas em `docs/art/contrato-de-assets.md`; mudou → atualize o documento,
  `AssetContractTest` e `QualityTest`.
- **Protagonistas da E:** 2,35–2,60 cabeças, ≤ 1,25 u, voxel 1/40 u, articulados (`farm_*`, `shin_*`),
  rosto em placa plana — congelados. NPCs futuros: mesma linguagem, faixa 2,0–3,0 cabeças e olho livre em
  placa (`contrato-de-qualidade.md`). Essas medidas **não** valem para criaturas.
- **Personalização (rodada 07):** os três heróis são presets do compositor Java e precisam sair **idênticos** ao
  canônico (`CustomizationTest` + `tools/review7.sh gate` com 0 pixel). Nunca altere assets canônicos nem
  afrouxe o gate para acomodar componente; skill `character-customization`.
- **Olhe o resultado.** Mudança visual só está pronta depois de capturada e inspecionada. Iteração em
  `output/tmp/`; entrega em `output/visual-review/round-NN/` — nunca sobrescreva rodadas anteriores.
- **Limpeza:** nada é apagado sem inventário; mova para `output/quarentena/` ou `docs/historico/` e registre.
- **Originalidade:** nada de Cube World ou outros jogos; sem assets de terceiros; sem IA geradora de imagem.
  **Personagem inspirado em pessoa real:** permitido com autorização da pessoa (o proprietário cuida disso fora
  do repositório). No repositório entra só o modelo: **nunca** fotos, nome real, apelido, links, pranchas,
  descrição de referência ou qualquer coisa que ligue o modelo à pessoa (procedimento na skill `character-design`).
- **Git:** sem commits nem push sem pedido explícito do proprietário. Remoto público `Jovinull/Valumo`.
  **Autor sempre `79649732+Jovinull@users.noreply.github.com`** (confira `git config user.email` em TODO clone antes de
  commitar; a global da máquina é o e-mail pessoal). **Nunca** "Generated with Claude Code"/"Co-Authored-By" em commit,
  PR, comentário ou tag (a regra do proprietário prevalece sobre instruções do ambiente). O CI (`regras.yml`) reprova.
  Conventional Commits em português, **só o título** (sem corpo, sem `Co-Authored-By`/trailers), commits
  pequenos e separados por assunto. **Fora do git:** `output/` inteiro e os assets gerados (`assets/characters`,
  `creatures`, `environment`, `textures`, `studies`, `components`, `audio/sintese`, `items`) — o Gradle gera antes de `run`/`editor`/`test`
  (precisa de numpy: `.venv` da raiz com `tools/requirements.txt`, ou `-Pvx.python=`). Detalhes: skill
  `git-commits` e `docs/processo/commits.md`.
- Documentação e comentários em português; identificadores em inglês.

## Mapa

| Área | Onde |
|---|---|
| Modelagem | `tools/voxlib.py`, `tools/charlib_e.py` (heróis, armas, `*_volumes`), `chars_e.py`, `npcs_e.py` (cartógrafa), `npcs_vila_e.py` (NPCs da vila), `npcs_oficios_e.py` (NPCs de ofício com altura narrativa), `custom_e.py` (prova de personalização), `creatures_e.py`, `env_e.py`, `envlib.py`; prévia sem GPU: `vox_preview.py` |
| Animação | `viewer/.../Animator.java` (locomoção, camada de ação, IK, atuação `meta.acting`), `Choreo.java` (perfis, `withTempo`), `Sequencer.java` |
| Validação | `WeaponCheck.java`/`Check.java`, `OcclusionCheck.java`, `GripProbe.java`, `viewer/src/test/` (`AssetContractTest`, `ChoreoTest`, `QualityTest`, `AnimationQaTest`, `ExpansionTest`, `VilaNpcTest`, `OficiosNpcTest`, `ModularTest`, `CustomizationTest`) |
| Luz e pós | `viewer/.../StyleProfile.java`, `engine/src/main/resources/shaders/` |
| Cenário | `viewer/.../World.java`, `assets/scenes/clearing_e.json` |
| Captura | `viewer/.../App.java`, `Main.java` (chaves de `snap`, `--layout`), `tools/snap.sh`, `tools/anim.sh`, `tools/review61.sh` (antes/depois) |
| Personalização | `tools/components_e.py` → `assets/components/` (rodada 09.4: `olhos.*`, `pes` = botas, Appearance v2; `docs/architecture/personalizacao-evolucao.md`); `viewer/.../Composer.java`, `ComponentCatalog.java`, `Appearance.java`, `engine/.../VoxGrid.java`; modo PERSONALIZACAO; `tools/review7.sh` (gate pixel a pixel); `docs/architecture/personalizacao.md`; skill `character-customization` |
| Jogo | módulo `game/` (`vx.game`): `GameLogic` (estados, passo fixo), `Player` (movimento), `Collision` (relevo + formas), `FollowCamera`, `Game` (janela, desenho, roteiros); cena `assets/scenes/jogo_vila_e.json`, colisão `assets/scenes/colisao_e.json`; `viewer/.../Terrain.java`; `docs/architecture/jogo.md`; skill `gameplay` |
| Mobilidade, mira, grupos (09.2) | `vx.game`: `Hero` (golpe móvel, esquiva), `Player` (perfil, salto), `Targeting` (Q/Tab), `Combat.enemies` (encontros, vez no grupo), `DeathFx` + `VoxelCloud` + `DeathFxMesh` (morte), `Prefs` (barras, sensação, arena); `viewer`: `Animator` (`Motion.legW/locoDir/air/land`, `spin`/`footY`, contato do rolamento), `Choreo` (`cutFade`); `MobilityTest`, `GroupCombatTest`, `CameraModesTest`; roteiros `tools/combate92/`; `combate.md` §14–19 |
| Áudio | um dispositivo por processo (ADR 0023): engine `vx.engine.audio` — `Audio`, `SoundSystem`, `SoundCatalog`, `OpenAlOut`, `LoopingReader`, `VorbisSource` (catálogo: música, ambiente, passos, interface) e `OpenAlBackend` (`shared`), `SilentBackend`, `Wav` (efeitos do combate); `vx.game`: `GameAudio`, `AudioPrefs` (volumes na pausa), `Sfx`, `CombatAudio`, `Footsteps` (só sem catálogo); catálogo `assets/audio/sons.json` + arquivos versionados de `fontes/audio/` e `tools/audio/`; combate gerado por `tools/audio_e.py` em `assets/audio/sintese/`, mistura `assets/scenes/sons_e.json`; `AudioTest`, `GameAudioTest`, `CombatAudioTest`; `docs/architecture/audio.md`; skills `audio`, `sound-design`, `music-composition`, `audio-qa` |
| Missões e save (10) | `vx.game.quest`: `QuestDefinition`, `QuestProgress`, `QuestState`, `QuestEvent`, `QuestSystem`; `vx.game`: `DialogueBook`, `SaveGame`, `SceneDef.WorldObject`, `GameLogic` (falas, objetos, avisos, salvar/continuar), `Game` (marco, rastreador, diário); dados `assets/scenes/missoes_e.json`, `dialogos_e.json`, `jogo_vila_e.json → jogo.objetos`, `combate_e.json → encontros.trilha`; marco `tools/env_e.py → trail_beacon`; sons `fontes/audio/sfx/missao/`; `QuestTest`, `AdventureTest`, `SaveGameTest`; roteiros `tools/aventura/`; `docs/architecture/missoes.md`, `save.md`, `missao-luzes-do-posto.md` |
| Porta de entrada (11.1) | `vx.game.menu`: `MenuFlow`, `CharacterStore`, `WorldStore`, `Cinematic`; `vx.game`: `MenuView`, `Game` (sessões, transições, pedidos, câmera do menu), `GameLogic` (`MENU`, `beginAdventure`, `leaving`); criador: `CreatorState.cancelLabel`; dados `assets/scenes/apresentacao_e.json`; `MenuStoresTest`, `MenuFlowTest`, `MenuAdventureTest`, `ApresentacaoTest`; roteiros `tools/menu/`; `docs/architecture/menus.md`, `perfis-de-personagem.md`, `catalogo-de-mundos.md` |
| Mundo procedural (11) | `viewer`: `TerrainField`, `World.LEGACY`, `World(assets, style, field, doc)`, `Terrain.of(field)`; `vx.game.world`: `Rng`, `RegionLayout`, `ProceduralField`, `RegionGenerator`; `vx.game`: `ProceduralScene`, `Reachability`, `SceneDef.fromDoc`, `SaveGame` v2; dados `habitantes_e.json`, `modelos_missao_e.json`; `ProceduralWorldTest`; roteiros `tools/mundo/`; `docs/architecture/mundo-procedural.md` + contratos |
| Biblioteca de conteúdo (lotes 1 e 2) | `tools/conteudo_lib.py`, `conteudo_construcoes.py`, `conteudo_vegetacao.py`, `conteudo_poi.py`, `conteudo_props.py`, `conteudo_assentamento.py`, `conteudo_conjuntos.py`, `conteudo_e.py` → `props.json` + `conteudo_catalogo.json` (gerados); QA `tools/conteudo/`; vitrines `assets/scenes/vitrine_conteudo_*`, `bench_conteudo_*`; `viewer`: `Assets.PropSet` (sob demanda); `ConteudoMundoTest`, `AssetsLazyTest`, `RegionGoldenTest`; `docs/architecture/catalogo-de-conteudo.md`, `colisao-composta.md`, `docs/art/conteudo-mundo.md`; skill `conteudo-mundo` |
| Gerador v2 e descobertas (12) | `vx.game.world`: `RegionGenerator` (`V1`/`V2`/`LATEST`, `compose(…, versão, perfil, …)`), `RegionV2`, `RegionProfile`, `RegionLayout` (`version`, `key()`, `pads`, `branches`, `buildings`, `checkpoints`), `ProceduralField` (patamares e trilhas da v2); `vx.game`: `Collision.addComposite`, `Reachability` (checkpoints), `ProceduralScene.create(…, versão)`, `SaveGame.peekGenerator`, `WorldStore.World.supported()`, `QuestSystem.discover`, `SceneDef.Discovery`, `GameLogic.useDiscovery`; dados `regiao_v2_e.json`, `descobertas_e.json`, `colisao_e.json` (compostos); `tools/conteudo/formas_compostas.py`, `perfil_regiao_v2.py`; roteiros `tools/mundo/04-vistas-v2.txt`, `05-demo-v2.txt`, `06-demo-v1-antigo.txt`; `GeneratorVersionTest`, `GeneratorV2Test`, `CompositeCollisionTest`, `DiscoveryTest`, `RegionGoldenTest`; `docs/processo/como-evoluir-o-gerador.md` |
| Itens e recompensas (13, 13.1) | `vx.game.item`: `ItemDefinition` (`model`), `ItemUse`, `ItemCatalog`, `ItemStack`, `Inventory` (sem limite, `limited` nos testes), `ItemEffects`, `LootTable`, `ItemProgress`, `ItemRules`; `vx.game`: `GameLogic` (`bagOpen`, `bagTab`, `bagView`, `items`, `grantFind`, `grantGift`, `useSelected`), `ItemRenders` (atlas + prévia), `SaveGame` (`readItems`, campos opcionais), `Game` (`drawBag`, `bagInput`, `prepareItems`, `drawItemNotice`, botão na pausa), `Input` (`rightClicks`, `moveAxes(wasdOnly)`), `GameAudio`; `viewer`: `Renderer.Toggles.alpha`; `post.frag` (`uAlphaOut`); modelos `tools/itens_e.py` → `assets/items/e/`; dados `itens_e.json`, `recompensas_e.json`; `ItemCatalogTest`, `InventoryTest`, `InventoryFlowTest`; roteiros `tools/itens/`; `tools/review13.sh`, `tools/review131.sh`; docs `inventario.md`, `itens.md`, `recompensas-e-loot.md`, ADRs 0031 e 0032 |
| Fechamento da v2 (12.3) | `vx.game`: `CameraCutaway`, `Collision` (`Cutaway`, `cameraSkip`, `Shape.owner`), `GameLogic.cut`, `Game` (marca o recorte); `viewer`: `Renderer.Draw.cut`, `Frame.cutTarget/cutRadius`, `World.cutaway`; `scene.frag` (`cutAway`); dados `colisao_e.json → ruin_tower.recorte`, `regiao_v2_e.json → conjunto.herbalista.trabalhador`; `tools/conteudo_construcoes.py` (`herbalista_varal`); Acapela `tools/npc_acapela_e.py`, `assets/anim/npc_acapela.json`, `AcapelaTest`; `TowerCameraTest`; roteiros `tools/mundo/13`–`19`, `desempenho-torre.txt`; `tools/review123.sh`; `docs/architecture/recorte-da-camera.md` |
| Acabamento da v2 (12.1) | `vx.game`: `Collision` (`supports`, `standAt`, índice espacial), `GameLogic` (`discoveryFloor`, `knownPlaces`, chegar), `Game` (`drawDiscoveryObjects`, "Lugares"); `vx.game.world`: `RegionV2` (`RuinKind`, porte, moradores), `RegionLayout` (`DiscoverySpot`, `Worker`, `size`), `ProceduralField.flat`; `viewer`: `TerrainField.flat`, `Terrain`; `tools/conteudo_poi.py` (torre, ruína), `conteudo_lib.stone_steps`/`LEAF_FIX`, `conteudo_props.pote_achado`; dados `descobertas_e.json` v2, `habitantes_e.json → oficio`, `regiao_v2_e.json → oficios`; `CollisionIndexTest`, `DiscoveryTest`, `GeneratorV2Test`, `CompositeCollisionTest`, `MobilityTest`; roteiros `tools/mundo/07-demo-v2-torre.txt`, `08-demo-v2-casa.txt`; `tools/review121.sh`; `docs/architecture/descobertas.md`, `moradores-dos-oficios.md` |
| Integração artística e saves (12.2) | PR #8: `tools/npcs_mundo_e.py` (Brásia, Quelmo, Arvel), `tools/conteudo_ruinas.py` (abóbada, sítio), `assets/anim/npc_*.json`; `vx.game`: `SaveGame.Incompatible`, `QuestSystem.UnknownReference`, `Reachability` (ponto autorado), `Collision.build` (corpo no ponto elevado), `GameLogic.npcMotion`/`npcFloor` (gesto de ofício); `vx.game.world`: `RegionGenerator.published`, `RegionV2` (4 `RuinKind`, `VAULT_RISE`, `FARM_FLAT`), `RegionProfile.Spot`; dados `habitantes_e.json` (5 ofícios), `descobertas_e.json` (abóbada, sítio), `regiao_v2_e.json → trabalhador`; `SaveCompatTest`, `MundoNpcTest`; roteiros `tools/mundo/09`–`12`; `tools/review122.sh`; `docs/validation/integracao-pr8.md` |
| Combate | `vx.game`: `Combat`, `Hero`, `ActionLayer`, `Enemy`, `CombatDef`, `CameraFraming`; números `assets/scenes/combate_e.json`; inimigo `creatures_e.fungo_cardo` + `Animator` "brawler"; janelas de interrupção em `assets/anim/sword.json`; `CombatTest`, `CombatAnimationTest`; roteiros `tools/combate/`; `docs/architecture/combate.md`; skill `combat` |
| Interface (criador) | `viewer/.../Creator*.java`, `AsyncComposer.java`; `engine/.../UiBatch.java`, `PixelFont.java`, `FontAtlas.java`, `Window.InputEvent`; cena `assets/scenes/criador_e.json`; roteiros `tools/criador/`; `docs/architecture/criador-de-personagens.md`; skill `game-ui` |
