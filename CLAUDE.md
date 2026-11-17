# Guia para agentes neste repositório

**Valumo** — RPG voxel original (protótipo artístico e técnico) em Java 25 + LWJGL 3 (OpenGL 3.3 core), engine própria. Direção oficial: **E —
"Fábula Voxel"**. A–D existem só para comparação. Produção de assets novos **só com aprovação do
proprietário**.

## Leia antes de mexer

1. `docs/README.md` — índice (oficial × histórico).
2. `docs/art/visual-identity.md` §0 (invariantes × flexíveis) e `docs/art/contrato-de-qualidade.md`.
3. `docs/art/reference/` — imagens canônicas e reprovados.
4. A skill da tarefa em `.claude/skills/`: `character-design`, `voxel-art`, `weapon-animation`,
   `rendering`, `visual-qa`, `git-commits`, `character-customization`, `game-ui`.
5. `docs/validation/problemas-conhecidos.md` — o que está aberto e o que é limitação aceita
   (histórico completo: `matriz-de-problemas.md`).

## Comandos essenciais

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew editor                                    # criador de personagens do jogador (gera assets antes, se preciso)
./gradlew editorDebug                               # editor técnico da personalização (rodada 07)
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
- **Assets são gerados.** Nunca edite `assets/characters`, `assets/creatures`, `assets/environment`,
  `assets/textures`, `assets/studies` à mão: edite `tools/` e regenere. Fontes à mão: `assets/scenes/*.json`
  e `assets/anim/*.json`. Peça desenhada num editor entra via `voxlib.load_vox` (ver `pipeline-de-producao.md`).
- **Armas:** golpe = trajetória no perfil (`assets/anim/`). `viewer --check`, `--occlusion` e `--grip` sem
  alerta + vídeo em 4 ângulos (passar no validador não basta: arma pode sumir atrás do braço). **Nunca** aumente tolerância para passar: corrija pose ou volume (`*_volumes`) com justificativa.
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
| Interface (criador) | `viewer/.../Creator*.java`, `AsyncComposer.java`; `engine/.../UiBatch.java`, `PixelFont.java`, `FontAtlas.java`, `Window.InputEvent`; cena `assets/scenes/criador_e.json`; roteiros `tools/criador/`; `docs/architecture/criador-de-personagens.md`; skill `game-ui` |
