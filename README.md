# Valumo

RPG voxel 3D de fantasia em terceira pessoa, com personagens pequenos e expressivos, em **engine própria**
(Java 25 + LWJGL 3 + OpenGL 3.3 core, sem game engine pronta).

**Estado: protótipo com a primeira aventura completa** — "As Luzes do Posto" (rodada 10): aceitar a missão da Kelyra,
limpar a trilha dos fungos-cardo, reacender o marco de luz do posto, voltar e receber a recompensa, com diário,
rastreador, jogo salvo e "Continuar". Antes disso, dá para escolher ou
criar a aparência do herói, andar, correr e pular pela vila com câmera em terceira pessoa, conversar com a Kelyra e os
cinco NPCs de ofício e lutar numa arena ao sul (espada e escudo): atacar andando, esquivar (salto baixo), travar a mira e trocar de alvo, contra um fungo-cardo,
três ou um protótipo de chefe com dois menores (rodada 09.2; esquiva R2 e salto polidos na 09.3). O repositório também tem o visualizador que fixa a
identidade visual, as ferramentas de modelagem e de síntese de som por script, a animação procedural com coreografia
de armas, validadores e cenas de demonstração. Há música, ambiente e efeitos sonoros por síntese própria (rodada 09.4): as músicas A (padrão) e B foram aprovadas pelo
proprietário; efeitos, passos, interface e ambiente têm integração validada e escuta pendente.
Não há inventário, loja, progressão nem mundo aberto; há uma missão e moedas.

Direção de arte oficial: **E — "Fábula Voxel"** ([identidade](docs/art/visual-identity.md),
[contrato de qualidade](docs/art/contrato-de-qualidade.md), [referências canônicas](docs/art/reference/README.md)).

## O que existe

| Área | Situação |
|---|---|
| Heróis | aventureiro, exploradora e maga — aprovados e congelados (22 imagens canônicas) |
| NPCs | cartógrafa; **Kelyra** (estalajadeira), **Índira** (herbalista) e **Súvia** (mensageira); de ofício, com altura narrativa e conversa na vila: **Bramor** (guardião de trilhas), **Teodor** (estudioso), **Pluma** (cronista), **Ondina** (guia costeira) e **Lunara** (cantora noturna) — candidatos |
| Criaturas | pompom, tortuga, fungo, lumi (aprovados), mariposa (candidata) e fungo-cardo (o inimigo do combate) |
| Cenário | clareira canônica; vitrine da expansão; vila (NPCs da vila) |
| Animação | locomoção com IK de pé, golpes por perfil (espada, escudo, lâmina longa, arco, cajado), gestos de NPC com atuação por personagem |
| Validação | testes Java, validador espacial de golpes, oclusão de rosto/arma, sonda de pegada, auditoria de voxels, reprodução determinística |
| Personalização | **em tempo de execução**: os três heróis como presets (idênticos ao original), 6 estilos de cabelo, 10 cores, 5 tons de pele, 3 rostos, 3 formatos de olhos, 5 íris, chapéu, capuz e botas; salvar e carregar — [arquitetura](docs/architecture/personalizacao.md) |
| Criador de personagens | interface do jogador com mouse: cena própria, miniaturas reais, amostras de cor, giro e zoom, poses, salvar/carregar/restaurar — [interface](docs/architecture/criador-de-personagens.md) |
| Aventura (rodada 10) | uma missão completa com falas por contexto (10 situações da Kelyra), fungos com identidade estável, um marco que acende e fica aceso, recompensa única, diário (`J`), rastreador, jogo salvo com escrita atômica e Continuar — [missões](docs/architecture/missoes.md), [save](docs/architecture/save.md) |
| Jogo (exploração) | herói controlável na vila: andar/correr com aceleração, colisão com o cenário, degraus e encostas, câmera em terceira pessoa que não atravessa paredes, conversa com a Kelyra e os cinco NPCs de ofício, aparência escolhida ou criada no editor — [jogo](docs/architecture/jogo.md) |

Rodadas: 01 comparou quatro direções (A–D), 02 consolidou a E, 03 poliu, 04 refez a coreografia de armas,
05/05.1 auditaram e provaram reprodutibilidade, 06 fez a primeira expansão controlada, 06.1 refinou os NPCs e
07 levou a personalização para dentro do viewer, com os heróis como presets idênticos, 07.2 deu a ela um
criador clicável e **08** fez a primeira exploração jogável ([relatório](docs/validation/relatorio-rodada-08.md)).
09–09.3 fizeram o combate; a 09.4, o áudio, cabelos/olhos/botas e os NPCs de ofício
([relatório](docs/validation/relatorio-integracoes-09-4.md)). Depois veio a auditoria de engenharia e o **fechamento do QA**
(checkpoint `engineering-foundation-v0.3`, [relatório](docs/validation/relatorio-fechamento-qa.md)): validadores que
reprovam o que não conferiram, leitores resistentes a arquivo corrompido, sessão longa sem vazamento, interface em sete
resoluções, referências canônicas refeitas e integração contínua. A **Rodada 10** fez a primeira aventura completa
([relatório](docs/validation/relatorio-rodada-10.md)), aprovada pelo proprietário após teste físico (checkpoint
`adventure-foundation-v0.4`). A **Rodada 11** começou o mundo procedural: uma região gerada por semente, com
assentamento, trilha, posto, encontro e a mesma aventura vinculada ao lugar gerado
([relatório](docs/validation/relatorio-rodada-11.md); `./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024`). A **Rodada 11.1** deu ao jogo uma porta de entrada: menu principal com a vila ao fundo, personagens
salvos, mundos criados pela interface (nome e semente, com prévia real) e a aventura de cada personagem em cada mundo
([relatório](docs/validation/relatorio-rodada-11-1.md); [fechamento](docs/validation/relatorio-fechamento-rodadas-11.md)).
Depois, os **lotes 1 e 2 do laboratório de conteúdo** entraram como **biblioteca**:

- 35 modelos, entre construções, árvores, ruínas, estruturas e props;
- 6 variantes publicadas e 5 conjuntos de ofício;
- vitrines de vila;
- um catálogo com medidas, pontos e estado de cada um.

O jogo só carrega o que a cena usa ([relatório](docs/validation/relatorio-integracao-conteudo.md),
[guia](docs/processo/como-integrar-conteudo-ao-mundo.md)).

As **Rodadas 12 a 12.3 — "Mundos com Identidade"** foram aprovadas pelo proprietário no teste físico completo e
**publicadas** na tag `procedural-world-v0.6` ([relatório da publicação](docs/validation/relatorio-publicacao-v2.md)).
O gerador **v2 está publicado e congelado**, como a v1: cada mundo usa para sempre a versão com que nasceu, e uma mudança
no resultado de uma semente será a v3.

A **Rodada 12** pôs a biblioteca de conteúdo nos mundos gerados:

- **Versões do gerador convivendo:** um mundo criado antes continua idêntico, para sempre, e os novos nascem na v2.
- **A v2:** vilas de caminho (praça com chafariz, comércio, oficina) e rurais (sítios, horta, paiol, caçador), com os
  conjuntos de ofício inteiros e uma ruína fora da vila, com trilha própria.
- **Colisão composta:** dá para entrar na oficina e na ruína.
- **Exploração:** a primeira descoberta com recompensa, na lareira da ruína.

Detalhes no [relatório](docs/validation/relatorio-rodada-12.md) e no [guia](docs/processo/como-evoluir-o-gerador.md).

A **Rodada 12.1** fez o acabamento da v2:

- **Ruínas:** a casa ou a torre caída (agora dá para entrar nela) e, às vezes, a árvore retorcida ao lado.
- **Salto:** mais alto e com mais controle no ar; dá para subir em pedras, tocos, caixotes e muros baixos.
- **Piso:** o chão das construções é o que se vê, com degraus de pedra nas entradas; o piso da vila não pisca mais.
- **Vilas de caminho:** pequenas ou médias.
- **Moradores:** cada um no conjunto do seu ofício.
- **Descobertas:** por tipo de lugar. O achado se vê (o pote de moedas nas cinzas fica vazio depois) e o diário guarda
  os tipos de lugar.

Detalhes no [relatório](docs/validation/relatorio-rodada-12-1.md).

A **Rodada 12.2** integrou a arte aprovada:

- **Moradores nos ofícios:** a Brásia na forja, o Quelmo ao lado da barraca, o Arvel na cabana do caçador, a Índira
  herbalista e o Bramor no posto de vigia, cada um com o seu gesto.
- **Quatro ruínas:** a casa, a torre, a abóbada soterrada (no declive, com teto e um rasgo de luz) e o sítio abandonado
  (empena, curral e cocho), cada uma com a sua descoberta.
- **Saves seguros:** um mundo de uma versão de testes que mudou deixa a aventura guardada, sem marcá-la como corrompida.

Detalhes no [relatório](docs/validation/relatorio-rodada-12-2.md).

A **Rodada 12.3** fechou a v2:

- **A câmera dentro da torre caída:** ela fica com o braço inteiro, e a torre se recorta só entre a câmera e o herói. A
  câmera colada caiu de 40,6% para 2,1% dos quadros, sem mudar a torre nem a câmera do resto do jogo
  ([recorte da câmera](docs/architecture/recorte-da-camera.md)).
- **A Índira junto ao varal de ervas:** a vista da rua não passa mais atrás da placa do ofício.
- **O Acapela na biblioteca de personagens** (do estudo do artista), sem lugar nas vilas até ganhar um papel.

Detalhes no [relatório](docs/validation/relatorio-rodada-12-3.md).

A **Rodada 13 — Inventário, itens e recompensas** (ramo `feature/inventory-and-loot`, em avaliação, não publicada) dá ao
Valumo a primeira progressão de RPG:

- **A mochila** (refeita na 13.1, depois do teste do proprietário):
  - abre com I ou pelo botão Inventário da pausa, num painel translúcido sobre o jogo, que continua correndo (o herói anda
    com ela aberta);
  - não tem limite de espaços: a grade rola e tem abas por categoria;
  - o seletor segue o mouse;
  - os itens aparecem pelos modelos voxel, com o selecionado girando no detalhe.
- **Cinco itens:** ervas, cataplasma, fragmento de pedra, madeira envelhecida e registro de viagem.
- **As fontes:**
  - o presente da Índira, a herbalista, ao fim da conversa;
  - os achados das ruínas e da árvore, junto com cada descoberta.

  Cada uma entrega uma vez, tudo ou nada.
- **A cataplasma:** devolve um coração fora da luta.
- **O save:** tudo continua ao salvar e voltar, e os mundos publicados não mudaram em nada.

Detalhes no [relatório](docs/validation/relatorio-rodada-13.md).

## Requisitos

- Linux x86-64 com OpenGL ≥ 3.3 (testado em Mesa 26.2 / Intel Iris Xe).
- JDK 25 (o Gradle vem pelo wrapper).
- Python ≥ 3.10 **com numpy** para regenerar os assets: os efeitos do combate (`tools/audio_e.py`) são sintetizados com
  numpy; os modelos só usam a biblioteca padrão. Instale numa `.venv` na raiz, que o Gradle usa sozinho:
  `python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt` (outro interpretador:
  `./gradlew assets -Pvx.python=/caminho/python3` ou a variável `VX_PYTHON`).
- Opcional (QA): Pillow, imageio, scipy e soundfile (no mesmo `tools/requirements.txt`) e ffmpeg para vídeos. Os scripts
  `tools/review*.sh` chamam o `python3` do PATH: ative a `.venv` antes. Detalhes: [docs/validation/ambiente.md](docs/validation/ambiente.md).

## Executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current   # ou o caminho do seu JDK 25
./gradlew jogo                                       # o jogo: menu principal → personagem → mundo → aventura
./gradlew editor                                     # criador de personagens (interface do jogador)
./gradlew editorDebug                                # editor técnico da personalização (depuração)
./gradlew :viewer:run                                # visualizador na clareira canônica
./gradlew :viewer:run --args="--layout vila_e"       # a vila: cartógrafa, Kelyra, Índira e Súvia
./gradlew :viewer:run --args="--layout vitrine_e"    # vitrine da rodada 06
```

Os scripts em `tools/` são a fonte dos modelos; os `.vox`, rigs e texturas que eles geram **não são
versionados**. O Gradle gera tudo sozinho antes de `run`, `editor`, `test` e `installDist` (tarefa `assets`,
pulada quando nada em `tools/` mudou; precisa de `python3`). Para forçar: `./gradlew assets` ou
`python3 tools/build_assets.py`. Depois de `./gradlew :viewer:installDist`, o mesmo binário fica em
`viewer/build/install/viewer/bin/viewer`.

### Jogar

`./gradlew jogo` (ou, depois de `./gradlew :game:installDist`, direto: `game/build/install/game/bin/game`):
1. no **menu principal**, **Jogar**;
2. **Personagens:** escolha um personagem, crie um (**Novo personagem** abre o criador; **Cancelar** volta sem gravar),
   edite a aparência de um existente (as aparências salvas antes ficam no **Carregar** do criador); arraste o herói para
   girá-lo;
3. **Mundos:** escolha a **Vila do Posto** (a vila fixa da rodada 10) ou um mundo gerado, ou **Criar mundo** (nome +
   semente; vazia = sorteada; **Ver prévia** mostra a região de verdade). Cada personagem tem a própria aventura em cada
   mundo: **Continuar aventura** ou **Iniciar aventura**. Um jogo salvo de antes (`output/saves/aventura.json`, ou
   `output/saves-procedural/s<N>/`) aparece sozinho, uma vez, como a aventura de um personagem com a aparência dele
   (cópia; o original fica);
4. converse com a **Kelyra**, junto à estalagem: ela oferece a missão "As Luzes do Posto";
5. desça a trilha para o sul, derrote os dois **fungos-cardo** da clareira, reacenda o **marco** no fim dela e volte à
   Kelyra.

| Tecla | Ação |
|---|---|
| `W A S D` / setas | andar (relativo à câmera) |
| `Shift` | correr |
| mouse | girar a câmera |
| roda | aproximar e afastar |
| `E` | conversar (perto e de frente para um NPC: Kelyra, Bramor, Teodor, Pluma, Ondina, Lunara), avançar as falas, confirmar a escolha (← → escolhem) e usar o marco |
| `J` | diário (missão, objetivos, recompensa, moedas); `T` no diário mostra/esconde o rastreador |
| `I` | mochila (rodada 13.1, sobre o jogo correndo): o mouse seleciona ao passar, o clique usa (o direito também), setas andam pela grade, `Enter` usa, a roda rola, WASD continua andando com o herói, `I`/`Esc` fecham; também pelo botão Inventário da pausa |
| clique esquerdo | atacar (no ritmo, encadeia horizontal → diagonal → descendente; dá para andar durante o golpe) |
| botão direito (segurar) | defender com o escudo (só de frente) |
| `Espaço` | pular (sobe em caixote, barril e pedra pequena) |
| `Alt` esquerdo / botão do meio | esquivar: com direção, salto baixo (de lado com a mira travada, sem virar o corpo); sem direção, recua |
| `Q` | travar / soltar a mira no inimigo |
| `Tab` / `Shift+Tab` | trocar o alvo travado (direita / esquerda na tela) |
| `R` / `Enter` | recomeçar o confronto (depois de vitória / na tela de derrota) |
| `Esc` | pausa (continuar, **menu principal** (volta à tela inicial; se houver progresso não salvo, pergunta: salvar e voltar / voltar sem salvar), **salvar jogo**, **diário**, **inventário**, sensibilidade, inverter vertical, volume da música e dos efeitos, mudo, barras de inimigos, sair; com `-Ptecnico` também a sensação A/B/C e a arena de comparação: um fungo / três / chefe + 2 / trilha da missão) |
| `M` | liga/desliga o som |
| `F12` | captura em `output/visual-review/manual/` |

Fechar a janela (o X) durante a aventura sempre pergunta antes: com progresso ainda não salvo, salvar e sair / sair sem
salvar / cancelar; com tudo salvo, sair / cancelar. No menu, fecha direto.

O som usa o dispositivo padrão do sistema (OpenAL Soft, que vem com o LWJGL); sem dispositivo, o jogo segue mudo e
avisa no console.

Detalhes: [jogo](docs/architecture/jogo.md), [combate](docs/architecture/combate.md) e [som](docs/architecture/audio.md). Teste com teclado e mouse
físicos: [exploração](docs/validation/roteiro-de-teste-fisico.md), [combate](docs/validation/roteiro-de-teste-fisico-combate.md) e
[aventura](docs/validation/roteiro-de-teste-fisico-aventura.md).

### Criar um personagem

`./gradlew editor` abre o criador:
- **Mouse:**
  - clique nas abas (Modelo, Cabelo, Rosto, Pele, Chapéu, Roupa), nas miniaturas e nas cores;
  - arraste sobre o personagem para girá-lo (o botão direito sobe e desce o enquadramento);
  - a roda aproxima sobre o personagem e rola sobre o painel.
- **Botões:**
  - Restaurar (com confirmação), Salvar, Carregar (lista dentro do editor) e Pronto (vitrine);
  - poses, Rosto, Corpo e Centralizar sob o personagem.
- **Teclado:**
  - `Tab` categoria, `←`/`→` opção, `↑`/`↓` cor, `Enter` Pronto;
  - `F2`/`F3` salvar/carregar, `Backspace` restaurar, `Esc` fecha.

Detalhes: [criador de personagens](docs/architecture/criador-de-personagens.md).

### Editor técnico (depuração)

`./gradlew editorDebug` — ou `./gradlew :viewer:run` e `Tab` até **PERSONALIZACAO**:

| Tecla | Ação |
|---|---|
| `↑` / `↓` | escolhe a linha (preset, rosto, cabelo, cor do cabelo, pele, íris, cobertura, expressão, clipe) |
| `←` / `→` | troca o valor (combinação incompatível é pulada e informada) |
| `Backspace` | volta ao preset original |
| `F2` / `F3` | salva em `output/aparencias/` (nunca sobrescreve) / carrega o mais recente |
| arrastar / roda / `T` | gira, aproxima, turntable |

Sem janela: `tools/snap.sh "mode=char,style=e,custom=aventureiro,cabelo=cabelo.curto,corCabelo=6b4630,cobertura=chapeu.maga,out=output/tmp/eu.png"`.

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
./gradlew test :viewer:installDist                         # testes (engine, malha, contrato, qualidade, NPCs, jogo)
tools/review81.sh temporal                                 # cintilação em câmera lenta: malha legada × conforme
tools/review9.sh videos                                    # combate: vídeos reais (roteiros em tools/combate/)
tools/review92.sh videos                                   # 09.2: golpe andando, esquiva, salto, mira, grupos, morte (tools/combate92/)
tools/review93.sh animacao                                 # 09.3: esquiva R2 e recuo, salto antes × depois (tools/combate93/)
viewer/build/install/viewer/bin/viewer --check golpe-andando   # golpes com as pernas andando: 0 problemas
viewer/build/install/viewer/bin/viewer --check esquiva         # cortes para esquivar/recuar nas janelas medidas: segura
tools/review91.sh camera                                   # câmera de luta: antes × depois (o "antes" exige o binário da 09 guardado em output/tmp/game-09, local)
tools/review91.sh videos                                   # vídeos com o som gravado pela aplicação + sincronia
tools/review91.sh sons                                     # catálogo dos efeitos (arquivos, faixa, prancha)
viewer/build/install/viewer/bin/viewer --check interrupcao-fina   # golpes do combate cortados a cada 0,01 s
viewer/build/install/viewer/bin/viewer --check             # golpes e gestos sem interseção (0 falhas)
tools/review7.sh gate                                      # presets recompostos × originais, pixel a pixel (0 diferenças)
viewer/build/install/viewer/bin/viewer --occlusion         # rosto coberto / arma escondida (a exceção do arco é explícita: saída 0)
viewer/build/install/viewer/bin/viewer --check-cabelos     # arma e broquel × cabeça composta (penteados, chapéu, capuz, salto)
tools/qa/audio_tempo_real.sh tools/qa/audio-criador.txt output/tmp/a   # buracos de áudio em tempo real, sem alto-falante
python3 tools/qa/sessao_longa.py 160 > R && tools/qa/audio_tempo_real.sh R output/tmp/s 50   # sessão longa (~35 min)
game/build/install/game/bin/game -Dvx.saves=PASTA --roteiro tools/qa/resolucoes.txt SAIDA 30  # interface em 7 resoluções
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
engine/    núcleo: janela e eventos, shaders, malhas, leitor .vox, mesher, câmera, FBO, HUD, lote 2D, fonte própria
viewer/    runtime (renderer, mundo, relevo, animador, compositor, criador, peças de interface) + visualizador:
           estilos, modos, captura, bench, validadores
game/      o jogo: jogador, colisão, câmera, NPCs interativos, diálogo, combate, som do jogo, estados, interface
tools/     fontes dos assets (Python): heróis (chars_e, charlib_e), NPCs (npcs_e, npcs_vila_e, npcs_oficios_e), criaturas,
           ambiente, prova de personalização (custom_e) + utilitários de QA
assets/    feitos à mão (versionados): scenes/*.json, anim/*.json e audio/ (sons.json, musica/, ambiente/, sfx/);
           gerados por build_assets.py (fora do git): characters/ (e = heróis, npc = NPCs, custom = prova modular,
           r3/r4/variants = estudos), creatures/, environment/, textures/, studies/, components/ (peças da
           personalização) e audio/sintese/ (efeitos do combate)
fontes/    fontes do áudio versionado: partituras (.py), sessões LMMS (.mmp) e .mid
docs/      documentação oficial (visão, arte, arquitetura, decisões, validação, processo) + historico/
.claude/skills/  procedimentos para agentes
output/    local, fora do git: capturas e pacotes de revisão por rodada (visual-review/), temporários (tmp/)
```

Comece por [docs/README.md](docs/README.md).

## Limitações conhecidas

- Câmera, aceitas na publicação da v2 (sem reclamação no teste físico): ao sair da torre caída de costas, a câmera encosta
  na parede no fim da saída (P95); o recorte tem um anel pontilhado fino, visível parado (P96); dentro da abóbada a câmera
  fica curta, sob o teto (P97).
- Itens (rodada 13): a vila fixa e as regiões v1 não têm fonte de item (não têm descobertas nem a Índira); a vida não vai
  para o save (ao continuar, volta cheia); os modelos dos itens são a primeira versão, à espera da arte autoral.
- Protótipo: uma vila pequena, seis conversas curtas, uma missão e um confronto (espada e escudo contra os fungos da
  trilha; na pausa, um fungo, três ou um protótipo de chefe com dois menores). Sem inventário, loja, experiência,
  classes nem progressão; um save só.
- A lâmina longa não é jogável: interrompê-la por dano ainda atravessa o corpo em algumas janelas (a espada foi
  resolvida na rodada 09).
- NPCs e assets da rodada 06 são candidatos; NPCs têm só um gesto compartilhado.
- A aparência não define classe nem equipamento; a validação com mouse e teclado físicos é do proprietário.
- Capturas, vídeos e pacotes de revisão (`output/`) não são versionados; os relatórios em `docs/validation/`
  citam esses caminhos locais, regeneráveis pelos scripts de revisão.

Lista completa: [docs/validation/problemas-conhecidos.md](docs/validation/problemas-conhecidos.md).

## Originalidade

Inspiração apenas no *gênero* de RPG voxel de exploração. Nenhum modelo, textura, som, animação ou interface
de outro jogo foi usado ou reconstruído; todos os assets são gerados pelos scripts deste repositório.
Ver [docs/historico/pesquisa/riscos-legais.md](docs/historico/pesquisa/riscos-legais.md).
