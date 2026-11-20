# Relatório da Rodada 12.1 — Acabamento da v2

**Situação:** entregue para avaliação no mesmo ramo LOCAL `feature/world-generation-v2`. São 68 commits
pequenos sobre o relatório da rodada 12 (`58c71a8`), 112 sobre a `main` `4cbde86`.

- **Não feito:** push, PR, tag e publicação da v2. A `main` e as tags antigas não foram tocadas.
- **Não criados:** v3, modelos de NPC (o contrato para o artista está pronto), inventário, comércio, ferraria. O Titã
  não foi tocado.
- **v1 congelada:** 300 sementes iguais às do binário da rodada 12 (região, props, moradores, POIs, inimigos e vínculo),
  conferidas no fim com o binário final; `regioes-v1.txt` intocado.

**Antes de testar (importante):** o mundo v2 que o proprietário criou com o binário da rodada 12, semente 31337, agora é
uma região com a TORRE caída. A descoberta salva nele (a lareira) não existe mais, e o save é recusado com o motivo
("descoberta desconhecida"). O arquivo fica como estava, com a cópia `.invalido` ao lado. Isso foi medido numa CÓPIA
dos dados; os originais não foram abertos pelo jogo. Para testar a 12.1, crie um mundo novo. As aventuras da Vila do
Posto abrem normalmente (medido). Ver P87.

**Pacote local:** `output/visual-review/round-12-1/`, reproduzível com `tools/review121.sh`.

**Contratos:**

- [descobertas](../architecture/descobertas.md) (novo);
- [moradores dos ofícios](../architecture/moradores-dos-oficios.md) (novo, com o contrato do agente artista);
- [colisão composta](../architecture/colisao-composta.md) (refeito);
- [mundo procedural](../architecture/mundo-procedural.md) §10;
- [ADR 0019](../decisions/0019-salto.md), adendo da 12.1;
- [save](../architecture/save.md);
- [como evoluir o gerador](../processo/como-evoluir-o-gerador.md) §10, §13 e §14.

## O que foi feito

### 1. O teste físico do proprietário (o que ele achou)

| Relato | Causa medida | Correção |
|---|---|---|
| "entro no chão e subo" ao chegar andando na casa da ruína | a colisão tinha o piso a 0,30 u (o degrau) e o desenho, a laje a 0,375. Andando, o herói ficava 0,075 u dentro da laje e depois o degrau o subia. E, com o apoio só pelo centro do corpo, quem entrava na diagonal descia do degrau e raspava a lateral da laje no chão | topos EXATOS da geometria, degraus de pedra reais de 0,125 u nas entradas (P75) e o apoio pelo corpo (abaixo) |
| "parede que parece uma escada quebrada; só subo pulando e buga para baixo" | a fusão das formas juntava colunas de alturas diferentes e um corpo parava de empurrar a um degrau dos pés sem passar a segurar: quem pousava em cima afundava | formas fundidas só com a mesma base e o mesmo topo; **todo sólido segura quem está em cima** (`Collision.supports`); o **apoio pelo corpo** na borda e a **borda como parede na descida** (`Collision.support`, `Player.step`): um teste novo, que falha com a física anterior (0,113 u dentro da laje), pula em cada um dos 377 sólidos baixos dos compostos sem nunca ficar dentro |
| semente 1024: a torre de vigia e o quadro de avisos perto do Bramor piscam entre pedra e grama | o relevo do patamar era quantizado com ondulação de ruído: debaixo de uma construção o chão saía um degrau acima do centro dela, e a primeira fiada de pedra ficava no plano da grama | relevo **plano por projeto** nos patamares da v2 (`TerrainField.flat`); 400 de 400 pontos do patamar da 1024 sem desnível; v1 sem nenhuma conta nova |
| "o pulo parece pesado; não subo nas pedras pequenas" | altura 0,62 u, gravidade 24/34, 55% de controle no ar; e o problema de suporte acima | salto de 1,0 u, gravidade 18/26, coyote e buffer mais tolerantes, 85% de controle no ar (ADR 0019, adendo) |
| "faz sentido uma lareira com moedas? e 'descoberta' com lugares infinitos?" | — | ver "A pergunta da lareira" abaixo |

### 2. P74 — a torre arruinada

- **O diagnóstico da rodada 12 estava errado.** Medindo voxel a voxel, o arco tinha 0,75 u livres até acima da cabeça.
  O que fechava a passagem eram os degraus 3 e 4 da escada interna, logo atrás da boca da porta.
- **Duas variantes comparadas** (silhuetas no pacote): escada "caída" × "recuada", com o arco de 6 e de 8 voxels.
- **A escolhida:** escada recuada e arco de 8 voxels (1,0 u) nascendo acima da cabeça. A torre continua velha e
  quebrada.
- **Medido:**
  - passa um corpo 0,12 u mais largo de cada lado, em 5 rumos;
  - entrada, interior, saída e ponto de interação alcançáveis;
  - a muralha baixa (1 u) se sobe com um salto andando (`CompositeCollisionTest`).
- **No gerador:** a torre está liberada e é POI da v2 (`poi.r0.torre.0`), sorteada com a casa (abaixo).

### 3. P75 — o piso dos interiores, corrigido na origem

- **Duas soluções comparadas:**
  - **(a)** baixar a laje dos modelos para 0,30 u: muda a arte aprovada e não resolve o muro quebrado;
  - **(b)** topo exato na colisão + degraus reais.
- **A escolhida:** (b). Ela corrige também o relato do muro e não pede alçar o herói nem tolerância nova.
- **Onde há degraus:** de pedra, em `conteudo_lib.stone_steps`, quebrados nas ruínas. Ficam no arco e na brecha da ruína
  de casa e na porta e no galpão da oficina.
- **Medido** (`CompositeCollisionTest`): por dentro, o chão é 0,375. Por CADA entrada, a pé e sem pular, com a física
  real do herói seguindo o caminho que a busca na grade acha, sobe-se pelos degraus e chega-se ao meio do interior.
  Com a física anterior, a ruína a 90° não chega.
- **Outros corpos:** todas as aparências usam o mesmo círculo de colisão (0,26 u) e a mesma física. A Exploradora e
  a Maga, entrando a pé pelo arco da semente 77, terminam em cima da laje (y = 0,375), com os pés à mostra
  (`round-12-1/corpos/`).
- **Comparação:** `round-12-1/comparacoes/laje-r12-r121.png`. Na rodada 12, as pernas somem dentro da laje (y = 0); na
  12.1, ele sobe os degraus (y = 0,375).

### 4. P76 — os moradores dos ofícios

- **O vínculo:** conjunto → ofício → spawn → morador, todo em dados (`habitantes_e.json → oficio` → perfil congelado
  `oficios`). Nada de `if nome ==` no gerador.
  - O Bramor virou `guardiao_de_trilhas` por dado.
  - Um morador por conjunto e por spawn.
  - O corpo dele fica livre, a ≥ 0,6 u das entradas e a ≥ 0,56 u dos pontos.
  - A conversa é exigida na conferência de alcance (`falar:<id>`).
- **Auditoria:** só o Bramor tem um ofício de conjunto. Índira, Súvia e a cartógrafa não têm papel nos dados; dar um
  ofício a elas é decisão narrativa (P88).
- **200 sementes:**

  | Conjunto | Regiões | Com morador |
  |---|---|---|
  | guardião | 71 | 71 (o Bramor) |
  | herbalista | 89 | 0 |
  | caçador | 68 | 0 |
  | ferreiro | 61 | 0 |
  | comerciante | 51 | 0 |

- **O contrato para o agente artista:** em [moradores dos ofícios](../architecture/moradores-dos-oficios.md) §3 (nome
  técnico, altura, rig, animações, expressões, spawn, metadados, testes).
- **Nenhum modelo foi criado aqui**, como pedido.

### 5. P77 e a pergunta da lareira — descobertas por tipo de lugar

**A pergunta:** "na ruína abandonada faz sentido interagir com uma lareira e ter moedas? E faz sentido chamar isso de
descoberta, se o procedural vai ter infinitos desses lugares?"

**Resposta e decisão:**

- **As moedas precisam de algo que se veja.** Nas cinzas da lareira agora há um **pote de barro com moedas**
  (`pote_achado`).
  - Ele está cheio antes de pegar e vazio depois (uma variante de estado, conforme o progresso salvo).
  - A placa diz "Pegar o pote da lareira", não "usar a lareira".
  - Faz sentido: quem morou ali guardou o que tinha perto do fogo; o texto diz isso e só isso.
- **"Descoberta" não pode ser cada ruína.** Num mundo grande, o mesmo aviso em toda ruína vira ruído e um diário com
  centenas de linhas. O desenho separa duas coisas:
  - **Achado:** o objeto daquele lugar. Dá as moedas **uma vez** e fica marcado.
  - **Lugar:** um TIPO de lugar. Na primeira vez da aventura, o aviso diz "Lugar novo: Casa tomada pela mata" e o diário
    ganha o tipo, em "Lugares". Os seguintes do mesmo tipo são só o achado ("Pote de barro · +5 moedas").
  - O registro é **derivado** dos achados salvos: sem campo novo, sem save v3.
- **Três tipos**, cada um só onde existe:

  | Tipo | Como se acha | Moedas | 200 sementes |
  |---|---|---|---|
  | casa tomada pela mata | E no pote nas cinzas | 5 | 119 |
  | torre caída | **chegar**: de pé em cima da muralha baixa, subida com um salto; a placa "Subir na muralha" embaixo | 8 (pede o salto) | 81 |
  | árvore retorcida | E na árvore (o marco natural) | 0 (nem todo lugar tem tesouro) | 77 |

- **Textos:** só fatos da região (rumo da vila, do posto, do lago; ao lado de que ruína a árvore cresceu), escritos à
  mão em `descobertas_e.json`.
- **Economia:** no máximo 8 moedas de achados por região, contra 30 da missão.

### 6. P81 — vilas de caminho pequenas e médias

- **Sorteio:** pela semente (rótulo `"porte"`, 50%).
- **A média não é a pequena ampliada:**
  - patamar de 11,8 u, mas a praça do mesmo tamanho (3,4 u);
  - anel de vagas mais largo e uma construção desejada a mais;
  - folga de até 3,2 u entre vizinhas;
  - segunda passada com piso 3.
- **200 sementes** (médias):

  | Porte | Regiões | Construções além da estalagem | Conjuntos | Praça → construção | Tentativas extras | Formas | Criar |
  |---|---|---|---|---|---|---|---|
  | caminho, pequena | 54 | 2,09 (49 com 2) | 1,76 | 7,1 u | 5 | 547 | 114 ms |
  | caminho, média | 54 | 3,07 (46 com 3, 6 com 4) | 2,35 | 7,8 u | 21 | 511 | 131 ms |
  | rural | 92 | 3,04 | 1,28 | 7,4 u | 9 | 379 | 110 ms |

- **Recusas:** nenhuma semente. Das 35 tentativas recusadas e refeitas, 15 foram poste da trilha em desnível e 13 foram
  falta de lugar para a ruína.
- **Os tempos de criar** foram medidos com o computador gravando vídeo; o desempenho limpo está em "Números".

### 7. P82 — a copa da árvore jovem da ruína

- **O que mudou:** o tronco subiu e a copa começa a ~2,2 u, em vez de 1,375. A folhagem presa à ruína acima de 1,7 u é
  fosca, pela regra do catálogo: não desliza com o vento.
- **A câmera global não mudou.**
- **Medido:** câmera do jogador dentro da ruína, em 3 posições (lareira, entrada, ao lado da árvore) × 8 rumos. Colada
  em **9 de 24 → 0 de 24** (`CompositeCollisionTest`).
- **Antes × depois:** `round-12-1/comparacoes/`.

### 8. P84 — enquadramento das capturas

- **O que mudou:** as tomadas das construções miram a frente (com reserva por trás) e não repetem construção.
- **Tomadas novas:** o achado, o mirante, a árvore, o morador do ofício e a ruína de cima. São 20 tomadas por semente,
  em 12 sementes.

### 9. Variedade das ruínas

- **Casa ou torre:** os dois POIs são compatíveis (mesmo patamar, mesma trilha, mesma conferência) e são sorteados com
  um rótulo próprio (`"ruina-tipo"`, torre 40%). Nada que já era sorteado mudou por isso.
- **A árvore retorcida**, quando existe, é marco natural com POI próprio.

## Defeitos achados e corrigidos na rodada

1. **Afundar ao pousar em cima de sólidos baixos:** a regra de suporte.
2. **O herói começava no telhado:** com o salto mais alto, a colocação antiga punha o herói em cima da casa (6,1 u) ou da
   pedra grande. Agora vale a superfície livre mais baixa ao alcance (`Collision.standAt`). Achado pelos testes de
   aventura e câmera.
3. **A referência das regiões quebrou sem a região mudar:** a impressão digital embutia o `combate_e.json` inteiro, e o
   salto mudou o JSON. Agora a referência usa uma cópia congelada do combate (`combate-da-referencia.json`). Provado com
   as 300 sementes da v1 que nenhuma região mudou.
4. **Teste do salto com número copiado:** `DodgeJumpPolishTest` tinha a janela do salto escrita no teste. Agora a lê do
   JSON.
5. **Busca de alcance permissiva demais:** ela subia por saliências de 1 voxel até o telhado (a oficina saía com 376
   formas). Agora exige que o corpo caiba.
6. **Folhagem presa não fosca:** a copa da árvore jovem e as moedas soltas do pote eram fragmentos. O
   `ConteudoMundoTest` pegou os dois.
7. **Sair andando da borda e descer rente a ela:** com o apoio só pelo centro, quem saía da borda da laje descia 0,375 u
   com o corpo ainda 0,26 u sobre ela e era jogado de lado; quem descia de um salto rente à borda escorregava por dentro
   da lateral. Achado pelo teste novo de salto; corrigido com o apoio pelo corpo (só topos na altura dos pés ou abaixo)
   e a borda como parede na descida. A conferência de alcance não mudou.
8. **A placa do pote vazio** ainda dizia "Pegar o pote da lareira"; agora diz "Olhar o pote vazio" (`verboVisto`).
9. **Roteiros:**
   - o andador de roteiro empurrava o tronco da árvore por 25 s no vídeo da torre (o ponto ficava entre a árvore e a
     muralha) e a escada da casa por 22 s no vídeo da casa (a câmera girada antes de andar);
   - a tomada do morador saía com a câmera presa na vista anterior;
   - o texto do mirante saía com "e … e …".
10. **Um aviso do `-Xlint:all`** que a rodada introduziu: um comentário de documentação ficou solto.

## Números

**Testes:** 413, 0 falhas, 0 erros (engine 45, viewer 128, game 240). 1 pulado: o gravador da referência, que só roda a
pedido. Rodada 12: 406.

**Determinismo:**

- **v1:** 300 sementes iguais às da rodada 12; as 8 da referência iguais (`regioes-v1.txt` intocado).
- **v2:** 200 sementes iguais em dois processos e em qualquer ordem (`GeneratorV2Test`). 0 recusadas: 172 na primeira
  tentativa, 23 na segunda, 3 na terceira, 2 na quarta.
- **`regioes-v2.txt`:** refeito três vezes nesta rodada, todas com motivo no commit:
  - o acabamento inteiro;
  - a frase do lago no mirante, em 5 sementes com torre e lago;
  - a placa do pote vazio, nas 2 sementes da referência com a casa.
- **A física nova não muda região:** a conferência de alcance usa o apoio pelo centro, como antes. Com o binário final,
  a v1 dá 300 sementes iguais e a v2, 200 arranjos iguais aos de antes da física.

**Colisão** (sementes 1–30, sem GPU):

| | rodada 12 | 12.1 |
|---|---|---|
| formas por região v2 | 272–365 | 340–718 (mediana 369) |
| conferência de alcance (mediana) | 17 ms | 1 ms (índice espacial) |
| criar a região (mediana) | 112 ms | 96 ms |

**Desempenho** (1080p, janela oculta, mudo, glFinish por quadro; os DOIS binários — rodada 12 num clone compilado de
`58c71a8` × 12.1 — intercalados na mesma sessão, 3 repetições, mediana; Iris Xe; `round-12-1/desempenho/resumo.md`):

| Cenário | quadro mediana (ms) r12 → 12.1 | p95 | lógica | GPU sombra | formas de colisão | gerar + conferir, 1ª vez (ms) |
|---|---|---|---|---|---|---|
| vila fixa | 9,10 → 8,50 | 12,47 → 11,87 | 0,17 → 0,15 | 1,35 → 1,34 | 185 → 185 | — |
| região v1 s1024 | 9,37 → 9,46 | 12,94 → 13,02 | 0,26 → 0,33 | 1,28 → 1,30 | 212 → 212 | 151 → 168 |
| região v2 s1024 (rural, torre) | 9,40 → 9,22 | 12,84 → 12,78 | 0,24 → 0,44 | 1,07 → 1,04 | 278 → 343 | 429 → 318 |
| região v2 s7 (caminho média) | 9,71 → 9,64 | 13,20 → 13,27 | 0,46 → 0,41 | 1,28 → 1,26 | 348 → 406 | 249 → 568 |
| região v2 s31337 (caminho pequena) | 9,24 → 10,21 | 12,82 → 14,36 | 0,44 → 0,59 | 1,22 → 1,22 | 353 → 640 | 275 → 210 |

- **Iguais dentro do ruído** (±1 ms entre repetições do mesmo binário): vila fixa, v1, v2 1024 e v2 7. Memória e malhas
  iguais. Nenhuma coleta de lixo na medição.
- **31337:**
  - na 12.1 é OUTRA região: torre, vila pequena, 107 desenhos contra 99 e 640 formas;
  - a 3ª repetição da 12.1 saiu num pico do sistema;
  - em 3 pares extras, as duas versões ficaram em 16–19 ms (a máquina em estado lento) e a 12.1 ficou 0,3–1,1 ms
    acima;
  - a lógica sobe ~0,13 ms (mais formas).
- **Semente 7:** a região da 12.1 só passa na 4ª tentativa. Abrir esse mundo leva 568 ms na 1ª vez (rodada 12: 249),
  uma vez só. A conferência de cada tentativa caiu de 19,3 para 2,9 ms.
- **Menu:**
  - primeiro quadro: 3,50 → 3,39 s;
  - gerar a 1024: 238 → 195 ms;
  - montar o mundo: 2,09–2,58 s → 2,20–2,43 s (as faixas se sobrepõem);
  - 284 malhas, ~176 MB nos dois.
- **Linha de base pedida:** v1 9,32, v2 rural 9,07, caminho 9,70 ms (rodada 12). Nesta sessão: v1 9,46, v2 rural 9,22,
  caminho 9,64 ms.

**Validadores e portão** (`round-12-1/validadores.txt`):

- **Armas e esquiva:** `viewer --check` TODOS OK (129 animações); interrupção fina, golpe andando e esquiva seguros;
  cabelos sem piora.
- **Oclusão:** só a exceção documentada do arco.
- **Imagem:** presets 51 pares, 0 pixel de diferença; referências canônicas 22/22 equivalentes.
- **Documentação e áudio:** links 0 quebrados; áudio 47 arquivos sem alerta.
- **Compilação:** `javac -Xlint:all` 0 avisos (o único da rodada foi corrigido); IDE num import limpo 0 diagnósticos.
- **Clone limpo:**
  - sem a `.venv` do checkout, a geração para com a mensagem que manda criar a `.venv`;
  - com uma `.venv` nova do `tools/requirements.txt`: 413 testes, 0 falhas, instalação e `viewer --check` ok;
  - os 1674 arquivos gerados são idênticos aos do checkout;
  - `formas_compostas.py` reproduz o `colisao_e.json` versionado sem diferença.

**Saves:**

- **Saves da `main` publicada** (`tools/qa/saves_antigos.sh`, v1, v2 e menu, abertos pelo binário novo): 8/8 iguais, 0
  `.invalido`, 0 erros.
- **Mundo v2 da rodada 12, aberto na 12.1** (com cópias, pelo menu):
  - semente que continua casa: abre, com a região nova;
  - semente que virou torre, com a lareira achada: recusado com o motivo, o arquivo intacto e a cópia `.invalido`
    (P87).
- **Os dados reais do proprietário**, abertos a partir de uma CÓPIA:
  - as 2 aventuras da Vila do Posto abrem;
  - a do mundo "RUINAS" (31337) é recusada pelo P87.
  - Os originais não foram abertos pelo jogo, e os scripts conferem por sha256 `output/aparencias` e `output/saves`
    antes e depois de cada etapa: intactos.

## Decisões do proprietário

1. **Teste físico:**
   - a laje, os degraus e o muro quebrado;
   - o salto novo (altura e controle no ar);
   - o piso da torre de vigia e do quadro na semente 1024;
   - a torre por dentro;
   - os três tipos de descoberta.
2. **O pote do achado** é um asset novo, desta rodada (P86): aprovar ou pedir outro desenho (o ponto e a pegada ficam).
3. **A câmera dentro da torre** (P85): aceitar, subir a câmera quando não há teto (muda a câmera global) ou alargar a
   torre (arte).
4. **Os personagens dos ofícios** (P76): encomendar ao agente artista pelo contrato; e decidir se a Índira (ou outra)
   vira herbalista (P88).
5. **Escalada:** a referência pedida sobe nas coisas principalmente escalando (segurar uma tecla, com fôlego) e
   planando, não com um salto mais alto. A 12.1 fez o salto e o suporte. Escalada é sistema novo, fora do escopo desde
   a ADR 0019, e fica para decisão.
6. **Publicar a v2:** só depois do teste físico e da decisão sobre os moradores. Publicada, cada mudança é versão nova.
