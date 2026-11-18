# Guia para agentes neste repositório

**Valumo** — RPG voxel original (protótipo com exploração jogável e combate com som, mobilidade — golpe andando, esquiva, salto —, mira opcional e grupos) em Java 25 + LWJGL 3 (OpenGL 3.3 core), engine própria. Direção oficial: **E —
"Fábula Voxel"**. A–D existem só para comparação. Produção de assets novos **só com aprovação do
proprietário**.

## Leia antes de mexer

1. `docs/README.md` — índice (oficial × histórico).
2. `docs/art/visual-identity.md` §0 (invariantes × flexíveis) e `docs/art/contrato-de-qualidade.md`.
3. `docs/art/reference/` — imagens canônicas e reprovados.
4. A skill da tarefa em `.claude/skills/`: `character-design`, `voxel-art`, `weapon-animation`,
   `rendering`, `visual-qa`, `git-commits`, `character-customization`, `game-ui`, `gameplay`, `combat`, `audio`.
5. `docs/validation/problemas-conhecidos.md` — o que está aberto e o que é limitação aceita
   (histórico completo: `matriz-de-problemas.md`).

## Comandos essenciais

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew editor                                    # criador de personagens do jogador (gera assets antes, se preciso)
./gradlew editorDebug                               # editor técnico da personalização (rodada 07)
./gradlew jogo                                      # protótipo jogável (escolha de aparência → vila → arena ao sul)
./gradlew jogo -Ptecnico                            # idem com F3 = hitboxes, hurtboxes e estados do combate
tools/review9.sh [capturas|camera|videos|angulos|interrupcoes|validadores|desempenho|testes|zip]  # pacote do combate (tools/combate/)
tools/review91.sh [camera|videos|sons|desempenho|testes|validadores|regressao|zip]  # 09.1: câmera de luta e som (tools/combate91/)
tools/review92.sh [videos [NN]|comparacoes|animacao|capturas|desempenho|testes|validadores|regressao|zip]  # 09.2: mobilidade, mira, grupos, morte (tools/combate92/)
tools/review93.sh [animacao|videos [NN]|antes|desempenho|testes|validadores|regressao|documentos|zip]  # 09.3: esquiva R2, salto, câmera perto (tools/combate93/)
viewer/build/install/viewer/bin/viewer --check golpe-andando   # golpes com as pernas andando (4 rumos × 4 fases): 0 problemas
viewer/build/install/viewer/bin/viewer --check esquiva         # cortes golpe→esquiva R2/recuo nas janelas medidas + saídas: segura
./gradlew jogo -Dvx.sensacao=A|B|C -Dvx.encontro=um|grupo|chefe -Dvx.morte=D2|D1|antiga   # comparar (também na pausa)
python3 tools/audio_e.py                            # efeitos sonoros sintetizados → assets/audio/ (gerados; mistura em assets/scenes/sons_e.json)
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
```

Mais comandos (bench, pranchas, golpe com depuração, bancada de pés): `README.md` e as skills.

## Regras

- **Sem game engine.** Só LWJGL, JOML e Gson. Dependência nova exige ADR em `docs/decisions/`. Interface de
  jogo usa `UiBatch` + `PixelFont` (fonte própria) + fila de eventos da janela ([ADR 0012](docs/decisions/0012-interface-do-jogo.md)).
- **Malha conforme** (ADR 0014): nunca sobreponha nem recue faces para tapar fresta. Defeito de cintilação ou
  fresta se investiga com as três malhas (`-Dvx.mesh=legado`, padrão, `-Dvx.merge=false`) e em câmera lenta
  (`tools/review81.sh temporal`). As invariantes ficam em `MeshSeamTest`.
- **Assets são gerados.** Nunca edite `assets/characters`, `assets/creatures`, `assets/environment`,
  `assets/textures`, `assets/studies`, `assets/audio` à mão: edite `tools/` e regenere. Fontes à mão: `assets/scenes/*.json`
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
  salto mudou só no desenho (física congelada). Números só em `combate_e.json` (`sensacao`, `esquiva`, `salto`, `mira`, `grupo`, `encontros`).
- **Vários inimigos** (ADR 0021): uma classe `Enemy` para todos; a lâmina testa cada um, no máximo uma vez por golpe;
  nada de dano em área. Chefe = variante `chefe: true`; protótipo ampliado sempre marcado `prototipo`.
- **Morte** (ADR 0022): desagregação nos próprios blocos (`DeathFx`/`VoxelCloud`, uma malha dinâmica); a antiga só em
  `-Dvx.morte=antiga`.
- **Som** (rodada 09.1, ADR 0016): OpenAL Soft pelo LWJGL; o som **só lê** a lógica depois de cada passo (luta idêntica
  com e sem som, teste); sons só sintetizados em `tools/audio_e.py` (nada de terceiros nem IA); sem dispositivo = mudo,
  nunca erro; vídeos com o som gravado pela própria aplicação (`-Dvx.som=loopback`). Skill `audio`.
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
  Conventional Commits em português, **só o título** (sem corpo, sem `Co-Authored-By`/trailers), commits
  pequenos e separados por assunto. **Fora do git:** `output/` inteiro e os assets gerados (`assets/characters`,
  `creatures`, `environment`, `textures`, `studies`, `components`) — o Gradle gera antes de `run`/`editor`/`test`. Detalhes: skill
  `git-commits` e `docs/processo/commits.md`.
- Documentação e comentários em português; identificadores em inglês.

## Mapa

| Área | Onde |
|---|---|
| Modelagem | `tools/voxlib.py`, `tools/charlib_e.py` (heróis, armas, `*_volumes`), `chars_e.py`, `npcs_e.py` (cartógrafa), `npcs_trio.py` (NPCs da vila), `custom_e.py` (prova de personalização), `creatures_e.py`, `env_e.py`, `envlib.py`; prévia sem GPU: `vox_preview.py` |
| Animação | `viewer/.../Animator.java` (locomoção, camada de ação, IK, atuação `meta.acting`), `Choreo.java` (perfis, `withTempo`), `Sequencer.java` |
| Validação | `WeaponCheck.java`/`Check.java`, `OcclusionCheck.java`, `GripProbe.java`, `viewer/src/test/` (`AssetContractTest`, `ChoreoTest`, `QualityTest`, `AnimationQaTest`, `ExpansionTest`, `VilaNpcTest`, `ModularTest`, `CustomizationTest`) |
| Luz e pós | `viewer/.../StyleProfile.java`, `engine/src/main/resources/shaders/` |
| Cenário | `viewer/.../World.java`, `assets/scenes/clearing_e.json` |
| Captura | `viewer/.../App.java`, `Main.java` (chaves de `snap`, `--layout`), `tools/snap.sh`, `tools/anim.sh`, `tools/review61.sh` (antes/depois) |
| Personalização | `tools/components_e.py` → `assets/components/`; `viewer/.../Composer.java`, `ComponentCatalog.java`, `Appearance.java`, `engine/.../VoxGrid.java`; modo PERSONALIZACAO; `tools/review7.sh` (gate pixel a pixel); `docs/architecture/personalizacao.md`; skill `character-customization` |
| Jogo | módulo `game/` (`vx.game`): `GameLogic` (estados, passo fixo), `Player` (movimento), `Collision` (relevo + formas), `FollowCamera`, `Game` (janela, desenho, roteiros); cena `assets/scenes/jogo_vila_e.json`, colisão `assets/scenes/colisao_e.json`; `viewer/.../Terrain.java`; `docs/architecture/jogo.md`; skill `gameplay` |
| Mobilidade, mira, grupos (09.2) | `vx.game`: `Hero` (golpe móvel, esquiva), `Player` (perfil, salto), `Targeting` (Q/Tab), `Combat.enemies` (encontros, vez no grupo), `DeathFx` + `VoxelCloud` + `DeathFxMesh` (morte), `Prefs` (barras, sensação, arena); `viewer`: `Animator` (`Motion.legW/locoDir/air/land`, `spin`/`footY`, contato do rolamento), `Choreo` (`cutFade`); `MobilityTest`, `GroupCombatTest`, `CameraModesTest`; roteiros `tools/combate92/`; `combate.md` §14–19 |
| Som | engine `vx.engine.audio` (`OpenAlBackend`, `SilentBackend`, `Wav`); `vx.game`: `Sfx`, `CombatAudio`, `Footsteps`; síntese `tools/audio_e.py`, revisão `tools/audio_board.py`, sincronia `tools/audio_sync.py`; mistura `assets/scenes/sons_e.json`; `CombatAudioTest`; `docs/architecture/audio.md`; skill `audio` |
| Combate | `vx.game`: `Combat`, `Hero`, `ActionLayer`, `Enemy`, `CombatDef`, `CameraFraming`; números `assets/scenes/combate_e.json`; inimigo `creatures_e.fungo_cardo` + `Animator` "brawler"; janelas de interrupção em `assets/anim/sword.json`; `CombatTest`, `CombatAnimationTest`; roteiros `tools/combate/`; `docs/architecture/combate.md`; skill `combat` |
| Interface (criador) | `viewer/.../Creator*.java`, `AsyncComposer.java`; `engine/.../UiBatch.java`, `PixelFont.java`, `FontAtlas.java`, `Window.InputEvent`; cena `assets/scenes/criador_e.json`; roteiros `tools/criador/`; `docs/architecture/criador-de-personagens.md`; skill `game-ui` |
