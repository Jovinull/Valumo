# Mundo procedural (rodada 11 — fundação)

Decisões: [ADR 0025](../decisions/0025-mundo-procedural-por-composicao.md) (região por semente, composição validada,
missão instanciada) e [ADR 0026](../decisions/0026-save-v2-mundo-procedural.md) (save v2). Contratos:
[sementes](contrato-sementes.md), [regiões](contrato-regioes.md), [POIs](contrato-pontos-de-interesse.md),
[instâncias de missão](contrato-instancias-de-missao.md), [persistência](persistencia-mundo-procedural.md). Guias:
[reproduzir sementes](../processo/reproduzir-sementes.md), [novo tipo de POI](../processo/como-adicionar-um-tipo-de-poi.md).

Legenda: **[IMPLEMENTADO]** existe, testado e capturado · **[PROTÓTIPO]** existe e funciona, mas é uma primeira versão
pequena (uma região, um tipo de missão) · **[PLANEJADO]** desenho sem código · **[FORA DO ESCOPO]** decidido não fazer
agora.

## 1. Estudo do que existia (antes da rodada 11)

| # | Pergunta | Resposta (no código da rodada 10) |
|---|---|---|
| 1 | O que do terreno já era gerado por código? | Todo o relevo: `World.height(x, z)` é uma função analítica (ruído de valor em duas escalas + encostas + bacia do lago + patamar da casa), quantizada em T1 por `World.quantize` e guardada em colunas no `Terrain`. Cores e superfícies (`surfaceClass`) também são funções. |
| 2 | O que era fixo? | As CONSTANTES dessa função: o traçado do caminho (`World.PATH`, 8 pontos), o centro do lago (`POND_X/Z`), o patamar da casa (`HOUSE_X/Z`), as encostas fixas por eixo. Uma seed não mudava nada. |
| 3 | Como os props eram posicionados? | À mão em `assets/scenes/*.json` ("props": nome, x, z, rumo) mais um espalhamento com semente fixa ("scatter") fora da área jogável, que evitava o caminho, o lago e a casa pelas constantes acima. |
| 4 | Como a vila era definida? | Pela mesma lista à mão (`jogo_vila_e.json`): uma casa, poço, cercas, caixotes, lanternas, placa, posto. |
| 5 | Como os NPCs eram posicionados? | `jogo.interativos` com x, z, rumo escritos à mão (vagas medidas na 09.4 por `GameplayTest`). |
| 6 | Como a colisão era construída? | `Collision.build` a partir do que o `World` desenhou (`World.placements()`), dos perfis `colisao_e.json` e dos NPCs — sem OpenGL, a mesma fonte do desenho. |
| 7 | Como os inimigos recebiam IDs? | Rodada 10: identidade do spawn (5º item de `encontros`, ou `<encontro>.<índice>`). |
| 8 | Como a missão escolhia o encontro? | `QuestDefinition.encounter` ("trilha"), trocado ao aceitar. |
| 9 | Como os objetos eram associados à missão? | `jogo.objetos` (id, x, z…) + `objetivos[].objeto` com o mesmo id. |
| 10 | Como o save identificava a cena? | `"cena": "jogo_vila_e"` (v1); outra cena = recusado. |
| 11 | Coordenadas fixas no código | `World.PATH/POND/HOUSE`, `CombatDef` (arena, recomeço, encontros vindos do JSON), `GameplayTest`/`AdventureTest` (posições da vila), `Game` (nenhuma no fluxo; as dos roteiros ficam nos roteiros). |
| 12 | O que já era reutilizável | O ruído, a quantização T1, a malha conforme, o `VoxelMesher`, a `Collision` (perfis, formas, `walkable`, `insideLimit` com círculos extras), o `SceneDef` (formato de cena), o `CombatDef.parse`, a missão inteira (`QuestSystem` não conhece coordenadas), o save atômico. |

Conclusão: o relevo já era procedural, mas com parâmetros fixos; o que faltava era (a) o relevo receber seus
parâmetros de fora, (b) alguém compor a região e (c) a missão sair dos fatos do mundo em vez de coordenadas.

## 2. O que a semente determina × o que foi gerado × o que existe × o que o jogador mudou

| Camada | Onde | Persistido? |
|---|---|---|
| **Regras do mundo** (o que é uma região, que componentes existem, como validar) | `RegionGenerator` (versão `valumo.regiao` v1), `habitantes_e.json`, `modelos_missao_e.json`, props da E | não (é código e dados do jogo) |
| **O que a semente determina** | `RegionLayout`: relevo (deslocamento e amplitude do ruído, encostas, colina, lago), lugares, trilha, arranjo do assentamento, moradores, posto, encontro, marco, vegetação | não: regenerado da semente + versão + tentativa |
| **O que existe no mundo atual** | `ProceduralScene`: os documentos de cena (desenho, jogo, combate), a missão instanciada e o vínculo, a conferência de alcance | não: derivado |
| **O que o jogador mudou** | `QuestSystem`: missões, inimigos derrotados, objetos acesos, moedas; posição, aparência | **sim** (save v2) |
| **Vínculos resolvidos** | `ProceduralScene.Binding`: modelo, POI, doador, inimigos, objeto e posição dele, rumo | **sim** (save v2), e conferidos na volta |

"Guardar a semente não basta": o save guarda a semente E o que o jogador mudou E os vínculos da missão. Uma versão
nova do gerador que mudasse o lugar do marco faz o save ser recusado com o motivo, nunca reposiciona a missão em
silêncio ([persistência](persistencia-mundo-procedural.md)).

## 3. Peças

| Peça | Estado | O quê |
|---|---|---|
| `vx.viewer.TerrainField` | [IMPLEMENTADO] | o relevo como dado: altura, distância ao caminho e ao lago, centro do lago, raio da floresta. `World.LEGACY` = as mesmas funções de antes (referências 22/22 com desvio 0) |
| `World(assets, style, field, doc)`, `Terrain.of(field)` | [IMPLEMENTADO] | o mesmo desenho (malha conforme, T1, cores, água) para qualquer campo; cache da grade pela identidade do campo |
| `vx.game.world.Rng` | [IMPLEMENTADO] | SplitMix64 próprio + derivação por rótulos (FNV-1a): resultado igual em toda JVM, independente de ordem ([sementes](contrato-sementes.md)) |
| `vx.game.world.RegionLayout` | [IMPLEMENTADO] | o que a semente decidiu, dado puro ([regiões](contrato-regioes.md)) |
| `vx.game.world.ProceduralField` | [IMPLEMENTADO] | o relevo gerado: o vocabulário da vila com parâmetros da semente; patamares onde há construção; trilha aplainada em rampa |
| `vx.game.world.RegionGenerator` | [PROTÓTIPO] | compõe UMA região (assentamento + trilha + posto, lago e colina opcionais, vegetação) com 3 arranjos autorais de assentamento e 3 composições de mata |
| `vx.game.Reachability` | [IMPLEMENTADO] | confere na colisão real e no T1: busca em grade de 0,25 u (raio do herói, degrau de 0,30 u), alvos (falar com cada morador, usar o marco, cada fungo, recomeço), arena livre ≥ 75%, nada afundado nem em desnível |
| `vx.game.ProceduralScene` | [IMPLEMENTADO] | região → documentos de cena; até 4 tentativas determinísticas, senão recusa com motivos; o modelo de missão vira instância |
| `assets/scenes/habitantes_e.json` | [IMPLEMENTADO] | moradores possíveis com papéis, artigo, exigências (lago) e falas sem fatos fixos |
| `assets/scenes/modelos_missao_e.json` | [PROTÓTIPO] | um modelo (`quest.template.restaurar_sinal`) com duas vozes autorais (estalajadeira, guardião) |
| Save v2 | [IMPLEMENTADO] | `valumo.save` v2 só para o mundo procedural ([ADR 0026](../decisions/0026-save-v2-mundo-procedural.md)) |
| Modo `-Dvx.mundo=procedural -Dvx.semente=N` | [PROTÓTIPO] | o mesmo jogo, o mesmo renderer; semente na tela inicial e na pausa; save em `output/saves-procedural/s<N>/` |

## 4. Como uma região nasce (fluxo)

1. `RegionGenerator.compose(semente, tentativa)`:
   - **relevo:** deslocamento do ruído, amplitude e encostas;
   - **lugares:** o assentamento a 1,5–6 u do centro, e o posto do outro lado, a 13–16 u, dentro de ±16,5 u;
   - **patamares:** a altura natural de cada lugar, presa a uma faixa;
   - **trilha:** curva Catmull-Rom com dois pontos de controle desviados, amostrada a 0,5 u;
   - **lago** (opcional, longe de tudo) e **colina** (opcional, longe da trilha);
   - **área jogável:** círculos no assentamento, ao longo da trilha e na clareira;
   - **assentamento:** um de 3 arranjos, girado para a saída da trilha e espelhado pela semente, com a Kelyra diante da
     estalagem e 2 a 4 moradores em vagas livres de props;
   - **posto:** quadro do posto, marco no fundo virado para quem chega, dois fungos-cardo entre a chegada e o marco;
   - **vegetação:** anel de mata fora da área jogável, acentos junto à trilha, pedras e arbustos nos vazios, flores na
     margem, cogumelos no posto, juncos no lago.
2. `ProceduralScene.docs`: monta os três documentos (desenho, jogo, combate) com tudo explícito e instancia a missão se
   os fatos existirem.
3. `SceneDef.fromDoc` + `Collision.build` + `Reachability.check`: a colisão do jogo e o relevo T1.
4. **Reprovou?** Volta ao passo 1 com a tentativa seguinte (outros sorteios derivados), no máximo
   `RegionGenerator.MAX_ATTEMPTS = 4`. Nenhuma passou: `ProceduralScene.Rejected`, com os motivos de cada tentativa. Nada
   de laço infinito.
5. `Game`: `new World(assets, style, field, worldDoc)` e o `GameLogic` de sempre com a cena gerada.

A geração pesada acontece uma vez, antes do primeiro quadro (no `Game`), nunca no laço de desenho. Nenhum recurso de
GPU é criado fora da thread do GL: a geração é dado puro, e o `World` monta as malhas como sempre.

## 5. Estado da validação (sementes 1024, 2048, 4096 e 77; mais 1, 2, 3, 555, 9001, 31337 nos testes)

- todas passam na **primeira** tentativa, com 0 problemas de alcance;
- geração + conferência: ~50–200 ms por região (a primeira é mais lenta: carga de classes e arquivos);
- números e capturas: relatório da rodada.

## 6. O que fica para depois

| Item | Estado |
|---|---|
| Várias regiões costuradas (bordas, trilhas que atravessam) e streaming | [PLANEJADO]: a região já tem id (`r0`) e todo rótulo de RNG leva o id da região; a costura exige bordas compartilhadas (alturas e trilhas na fronteira derivadas do par de regiões), um gerador assíncrono que produza `RegionLayout` + documentos fora da thread do GL e uma fila que só monte malhas na thread do GL |
| Mais tipos de POI (ruína, entrada de caverna, acampamento) | [PLANEJADO]: [guia](../processo/como-adicionar-um-tipo-de-poi.md) |
| Mais modelos de missão (exploração, resgate, defesa…) | [PLANEJADO]: a fundação já não impõe "derrotar → interagir → falar" (`conclusao: ultimo_objetivo`) e um NPC pode ter várias missões em sequência |
| Biomas | [FORA DO ESCOPO] nesta rodada (as 3 matas são composições dos mesmos modelos) |
| Mundo infinito, dungeons, inventário, comércio, novas criaturas, LLM | [FORA DO ESCOPO] |

## 7. Rodada 11.1 — mundos pela interface

A semente agora também é digitada no jogo:

- **Criar:** menu → personagem → **Criar mundo** (nome + semente opcional; vazia = sorteada e mostrada).
- **Prévia:** real, desenhada da região gerada.
- **Entrar:** a mesma `ProceduralScene.create` da linha de comando, com as mesmas tentativas e recusas.
- **Mundo:** ganhou id próprio (`m-…`), independente da semente. O save v2 da aventura mora na pasta do par personagem ×
  mundo.

O gerador não mudou (`RegionGenerator.VERSION` = 1); só a grade de relevo de uma tentativa recusada passou a sair do
cache (`Terrain.release`), sem mudar o resultado de nenhuma semente. Detalhes: [catálogo de mundos](catalogo-de-mundos.md).

## 8. Biblioteca de conteúdo e versões do gerador (integração dos lotes 1 e 2)

- **Biblioteca, não distribuição:** os 35 modelos do laboratório (lotes 1 e 2), as 6 variantes publicadas e os 5
  conjuntos de ofício entraram **só como biblioteca**: `props.json`, `conteudo_catalogo.json` e as vitrines. O gerador
  usa listas fixas de nomes e não lê o `props.json`, então nenhuma semente mudou.
- **Referência da v1:** a região inteira de 8 sementes (1024, 2048, 4096, 77, 1, 555, 9001, 31337) está fixada em
  `game/src/test/resources/regioes-v1.txt`.
  - Ela foi capturada na `main` publicada antes da integração.
  - `RegionGoldenTest` confere a referência e falha se `VERSION` mudar.
  - Mudou um hash = um mundo v1 mudou. **Não se atualiza a referência para passar.**
- **Distribuir a biblioteca exige uma versão nova do gerador.** Os mundos v1 precisam continuar abrindo:
  [ADR 0029](../decisions/0029-versoes-do-gerador.md) (proposta). A colisão dos assets com interior:
  [colisao-composta.md](colisao-composta.md).

## 9. Rodada 12 — duas versões do gerador, a v2 e a primeira descoberta

**Versões** ([ADR 0029](../decisions/0029-versoes-do-gerador.md), implementada):

- **Cada mundo usa para sempre a versão com que nasceu.** A v1 é o código da rodada 11, intocado, e
  `regioes-v1.txt` continua 8/8. A v2 é a padrão dos mundos novos.
- **Versão desconhecida:** o mundo continua na lista e não abre, com a explicação; nada é alterado.

**A v2** (`vx.game.world.RegionV2`, perfil congelado `assets/scenes/regiao_v2_e.json`) tem a mesma região controlada da
v1 (±16,5 u jogáveis):

| Peça | Regra |
|---|---|
| estilo | **caminho** (55%: compacta, praça com chafariz em 75% das vezes, bancos) ou **rural** (sítios espaçados, poço de manivela). O mesmo em todas as tentativas: é a identidade do lugar |
| patamar | 10,4 u (caminho) ou 11,2 u (rural); assentamento a 4,5–7 u do centro, posto do outro lado (patamar + 5–7 u) |
| estalagem | a casa aprovada, ao fundo da praça, virada para ela; Kelyra ao lado da porta, de lado (quem fala com ela fica ao longo da fachada); placa da estalagem e barril |
| construções | 3 ou 4 desejadas além da estalagem (piso de 2: uma segunda passada, com mais folga, tenta de novo as que não couberam), sorteadas por peso do estilo, giradas para a praça e conferidas **peça a peça** (a pegada da âncora e a de cada item do conjunto): dentro do patamar, sem invadir a praça, o corredor da saída da trilha, o chafariz/poço nem outra construção. Caminho: comerciante 5, ferreiro 4, guardião 3, herbalista 2, depósito 2. Rural: lavoura 5, caçador 4, herbalista 4, paiol 3, depósito 2, guardião 2, ferreiro 1 |
| conjuntos | postos INTEIROS: âncora + itens no referencial dela, girados juntos (cestos só aqui; o lenheiro coberto só com o caçador) |
| caminhos | de chão batido, da praça até cada porta (só pintam: o patamar já é plano) |
| torre de vigia | o conjunto do guardião, numa vaga de borda de olho na saída da trilha; o Bramor mora nela quando ela existe |
| ruína | `ruin_house` fora do assentamento, perto da trilha (a 1,4–7 u além do patamar dela), num **patamar** próprio, com uma **trilha secundária** escavada desde o ponto da trilha principal mais perto FORA da arena do posto (quem vai à ruína não passa pelos fungos) até o arco. POI `poi.r0.ruina.0` |
| descoberta | na lareira da ruína: `desc.r0.ruina.0` (derivado do POI) |
| posto e missão | as peças da v1 (`composePost`): o mesmo modelo de missão, o mesmo marco e os mesmos fungos, no fim da trilha |
| mata | três composições com as árvores novas (bifurcada nas três); álamos em fileira de um lado da trilha (1 fileira na vila de caminho, 2 na rural); a retorcida (rara, ~40%) atrás da ruína, no plano do patamar, plantada antes da mata e com a mesma conferência de desnível da `Reachability`; a seca (rara) na borda da mata; pedras e arbustos só em chão quase plano |

**Conferência:**

- O `Reachability` passou a exigir os `checkpoints` da v2:
  - cada entrada de construção (0,6 u);
  - os pontos de interação dos interiores (1 u);
  - a descoberta (1 u).
- A v1 não tem nenhum, e o resultado dela não muda.
- **Resultado** (sementes 1–60, binário final da rodada 12): 60 aceitas, 0 recusadas, 14 tentativas extras.

**Determinismo:**

- Rótulos com o prefixo `"v2"`.
- A ordem de geração não muda nada, e a vegetação não move construção, missão, NPC nem POI (`GeneratorV2Test`).
- A mesma semente na v1 e na v2 é outra região: outra chave e outro relevo no cache.

**Descoberta:**

- Usar (E) a lareira dá o aviso "Descoberta: …", 5 moedas **uma vez**, a frase do lugar e o registro no diário.
- O save ganha o campo opcional `progresso.descobertas` (ausente = nenhuma).
- **O que não muda:** a v1 e a vila não têm descoberta, e o save delas sai igual.

**Densidade** (sementes 1–30):

- 3–5 construções com a estalagem (as de caminho quase sempre 3: P81);
- ≤ 190 props;
- 272–365 formas de colisão (mediana 287).

**v1 × v2:**

| | v1 | v2 |
|---|---|---|
| assentamento | estalagem + poço/fogueira, 7 props em 3 arranjos | estalagem + 3–4 construções e conjuntos de ofício, chafariz ou poço de manivela, caminhos às portas, lavoura no rural |
| lugares | assentamento e posto | + ruína com patamar e trilha própria |
| exploração | a missão | a missão + uma descoberta |
| árvores | 8 modelos aprovados | + bifurcada, álamo em fileira, seca e retorcida |
| comparações | `output/visual-review/round-12/comparacoes/` | |


## 10. Rodada 12.1 — acabamento da v2 (ainda não publicada)

A v2 **não está publicada**. Por isso esta rodada mudou o resultado dela e refez `regioes-v2.txt` uma vez, no fim, com
o motivo no commit. Publicada, qualquer mudança assim vira versão nova (ADR 0029).

**A v1 não mudou:**

- **300 sementes:** saem iguais às do binário da rodada 12, conferidas duas vezes: região, relevo, caminhos, encontros,
  POIs, ids e vínculos.
- **Referência:** `regioes-v1.txt` está intocado.
- **Golden:** a única mudança foi no teste, porque o salto alterou o `combate_e.json`, que a impressão digital
  embutia. Agora ela usa a cópia congelada `combate-da-referencia.json`.

| Peça | Rodada 12 | 12.1 | Por quê |
|---|---|---|---|
| ruína | sempre `ruin_house` | **casa ou torre** (`RuinKind`, rótulo `"ruina-tipo"`, torre 40%): 200 sementes → casa 119, torre 81 | P74 resolvido + variedade pedida ("escolher entre POIs compatíveis") |
| torre | não liberada (vão de 0,5 u) | **liberada**: escada recuada, arco de 8 voxels; POI `poi.r0.torre.0`, mirante na muralha baixa | P74 ([colisão composta](colisao-composta.md) §3) |
| descobertas | uma (a lareira, 5 moedas) | **três tipos** (casa, torre, árvore), achado visível, "Lugar novo" por tipo | P77 + pergunta do proprietário ([descobertas](descobertas.md)) |
| árvore retorcida | decoração (~40%) | **marco natural**: POI `poi.r0.arvore.0` e descoberta própria; 200 sementes → 77 | P77 (o tipo C só onde ela existe) |
| vila de caminho | uma (3 construções, praça grande) | **pequena ou média** (rótulo `"porte"`, 50%) | P81 (tabela abaixo) |
| moradores | Bramor "na torre" | **pelo ofício do conjunto** (`oficios` no perfil, rótulo `"oficios"`) | P76 ([moradores dos ofícios](moradores-dos-oficios.md)) |
| relevo dos patamares | quantizado com ondulação | **plano por projeto** (`TerrainField.flat`, só v2): sem a ondulação de ruído no patamar, nos patamares das ruínas e na clareira do posto | o chão da torre de vigia e do quadro de avisos piscava (teste físico, semente 1024): o chão debaixo da construção saía um degrau acima do centro dela, e a primeira fiada de pedra ficava no plano da grama |
| copa da ruína | a 1,4 u, colada na câmera | tronco mais alto, copa a ~2,2 u, fosca (presa à ruína) | P82: câmera colada 9/24 → 0/24 |
| piso da laje | 0,30 u (pisável) sobre 0,375 desenhado | **exato** (0,375) com degraus de pedra nas entradas | P75: o herói afundava e subia |
| colisão | lista inteira por consulta | índice espacial (mesmo resultado) | 340–718 formas por região v2 com os topos exatos |

**Vila de caminho, pequena × média** (sementes 1–200, binário final; `p81` no pacote da rodada):

| Porte | Regiões | Construções além da estalagem (média) | Conjuntos (média) | Praça → construção (média) | Patamar | Tentativas extras | Formas (média / máx.) | Criar (média) |
|---|---|---|---|---|---|---|---|---|
| caminho, pequena | 54 | 2,09 (2–3; 49 com 2) | 1,76 | 7,1 u | 10,4 u | 5 | 547 / 720 | 114 ms |
| caminho, média | 54 | 3,07 (2–4; 46 com 3, 6 com 4) | 2,35 | 7,8 u | 11,8 u | 21 | 511 / 721 | 131 ms |
| rural | 92 | 3,04 (2–4) | 1,28 | 7,4 u | 11,2 u | 9 | 379 / 662 | 110 ms |

- **O que a média é:**
  - patamar 11,8 u (não "a pequena ampliada");
  - praça 3,4 u (a mesma ordem da pequena: nada de praça enorme com três casas);
  - anel de vagas mais largo;
  - uma construção desejada a mais;
  - folga até 3,2 u entre vizinhas;
  - segunda passada com piso 3.
- **Tentativas:** nenhuma semente recusada. Das 35 tentativas extras, 15 foram poste da trilha em desnível e 13 foram
  falta de lugar para a ruína (todas resolvidas na tentativa seguinte).
- **Tempo:** criar a região leva cerca de 0,1 s, medido com o computador ocupado gravando vídeo; o desempenho limpo
  está no pacote.

**Descobertas e moradores na conferência:**

- `descoberta:<id>` é exigido para os três tipos: o mirante a 1 u de altura, com o alcance do salto.
- `falar:<id>` é exigido para o morador do ofício.

**Testes novos ou refeitos:**

- `GeneratorV2Test`: casa ou torre; a árvore ⇔ a descoberta dela; porte da vila; morador do ofício sem bloquear; orçamento
  de formas 900.
- `DiscoveryTest`: os três tipos, o pote cheio/vazio, o mirante, o diário e o save.
- `CompositeCollisionTest`: a torre, o piso exato, a câmera ao lado da árvore.
- `CollisionIndexTest`, `MobilityTest` e `RegionGoldenTest`: a v1 com o combate congelado; a v2 refeita.

## 11. Rodada 12.2 — moradores de ofício, quatro ruínas e saves de versão de testes (a v2 ainda não publicada)

**A v1 não mudou:** 300 sementes iguais às da rodada 12 (conferidas com o binário final) e `regioes-v1.txt` intocado.
`RegionGenerator.RESIDENTS`, que a v1 usa, não mudou.

| Peça | 12.1 | 12.2 | Por quê |
|---|---|---|---|
| moradores de ofício | só o Bramor (guardião) | os cinco ofícios com personagem: Brásia, Quelmo, Arvel (PR #8), Índira, Bramor; todo conjunto posto tem o seu (190 de 200 regiões com pelo menos um) | P76 ([moradores](moradores-dos-oficios.md)) |
| ponto do trabalhador | spawn `npc` da âncora | o do perfil (`conjuntos.*.trabalhador`): vaga recomendada da âncora ou de um item (o feirante ao lado da barraca), ou o spawn | o feirante atrás das mercadorias ficava escondido; a herbalista bloqueava a porta |
| ruína | casa (60%) ou torre (40%) | **quatro**: casa, torre, abóbada soterrada e sítio abandonado (PR #8), com ordem sorteada com peso (rótulo `"ruina-tipos"`) e encaixe pelo terreno | variedade com propósito |
| descobertas | casa, torre, árvore | + abóbada (o pote entre os barris, 5 moedas) e sítio (a pedra do marco, 0) | um lugar próprio por ruína |

**Como o gerador escolhe a ruína** (`RegionV2.ruin`):

1. **Ordem:** sorteada com peso, a mesma em todas as tentativas. Casa 4 (a base, sempre tem vez), torre 3, abóbada 3 com
   morro na região e 1 sem, sítio 3 na região rural (aberta) e 1 na de caminho.
2. **Encaixe:** cada tipo procura lugar perto da trilha, como antes (patamar próprio, trilha secundária, fora da arena dos
   fungos).
   - **Abóbada:** só onde o terreno natural sobe ≥ 0,3 u da frente aos fundos (o morro dela encosta num declive).
   - **Sítio:** só onde o terreno natural varia ≤ 0,6 u na pegada inteira (chão aberto).
3. **Sem lugar:** o tipo que não couber cede a vez ao seguinte; sem nenhum, a tentativa é recusada.

**Sementes 1–200 (binário final):**

| Ruína | Sementes |
|---|---|
| casa | 94 |
| torre | 71 |
| sítio | 21 |
| abóbada | 14 |
| árvore retorcida (junto da ruína) | 75 |

- **Tentativas:** 0 sementes recusadas. 172 na 1ª tentativa, 23 na 2ª, 4 na 3ª, 1 na 4ª.
- **Moradores:**

  | Ofício | Morador | Regiões |
  |---|---|---|
  | herbalista | Índira | 88 |
  | guardião de trilhas | Bramor | 73 |
  | caçador | Arvel | 69 |
  | ferreiro | Brásia | 60 |
  | comerciante | Quelmo | 49 |

**Determinismo:**

- 200 sementes iguais em dois processos.
- A ordem dos assets não importa: o perfil é congelado e lido inteiro.
- O sorteio da ruína e o dos moradores têm rótulos próprios: não movem a missão, o marco nem os inimigos.
- `regioes-v2.txt` foi refeito com o motivo no commit.

**Saves de versão de testes:** um save íntegro de um mundo cuja região mudou antes da publicação é recusado sem `.invalido`
e sem tocar no arquivo ([save](save.md) §Rodada 12.2).

**O que fica congelado quando a v2 for publicada:**

- o código de `RegionV2` (e das peças que ela chama);
- o perfil `regiao_v2_e.json` (assets, pegadas, pontos, conjuntos, pontos dos trabalhadores, `oficios`);
- os textos e moedas de `descobertas_e.json` (entram na cena, e a referência os inclui);
- as falas e papéis de `habitantes_e.json`;
- os perfis de colisão dos assets que ela planta (`colisao_e.json`);
- `regioes-v2.txt`.

A partir daí, mudar qualquer um deles muda regiões, e isso é uma versão nova (ADR 0029). `RegionGenerator.published`
passa a incluir a v2.
