# Relatório da Rodada 12.3 — Fechamento da geração v2 e preparação para publicação

**Situação:** o ramo `feature/world-generation-v2` foi publicado no remoto, com um pull request para revisão e CI (§10).

- **Não feito:** merge na `main`, tag, squash, force push e reescrita de commit.
- **Não criados:** NPC novo, ruína nova, inventário, combate novo.
- **A v2 continua sem congelar** (`RegionGenerator.published` = só a v1) até o teste físico.

**Pacote:** `output/visual-review/round-12-3-final/` (LEIA-ME, roteiro de teste físico, vídeos, comparações, matrizes,
desempenho, ZIP sem os vídeos), reproduzível com `tools/review123.sh`.

## 1. Auditoria de partida

- **Ramo:** `feature/world-generation-v2` sobre a `main` `4cbde86`; a 12.2 terminou em `7261f3e` (175 commits sobre a
  `main`, nada publicado).
- **Testes:** 439, 1 pulado (o escritor da referência, só quando pedido).
- **Abertos da 12.2:**
  - P85: a câmera na torre;
  - P86: o pote;
  - P92: miniaturas;
  - P93: Acapela;
  - P94: teste físico.

## 2. A câmera dentro da torre (P85)

**A causa:**

- **A torre:** o interior é um vão de ~2,6 u numa muralha de até 4,4 u.
- **A câmera:** o braço é de 4,6 u e encosta quando algo bloqueia; dentro da torre, todo rumo atravessa a muralha.
- **Por que transparência não basta:** a câmera continuaria presa na parede.

**As alternativas, nos mesmos trajetos** (`TowerCameraTest`):

- **Rumos da torre:** 0°, 37°, 90°, 180°, 270°.
- **Cenários (12):** entrada, escada, centro, mirante com salto, descida, saída, giro rápido, mouse parado, parede de
  costas e de frente, diagonal, salto no lugar.

| Alternativa | Resultado |
|---|---|
| câmera da 12.2 | colada 40,6%, trechos de 2,00 s, desvio médio 21,6° |
| zona com câmera de cima (12.2) | 26% no melhor caso, com câmera quase vertical em volta da torre: rejeitada na 12.2 |
| **A — recorte contextual** | colada 2,1%, trecho máximo 0,15 s, herói legível 100% |
| B — geometria | não foi preciso: mudaria a silhueta aprovada e as regiões, e um vão maior ainda não comporta o braço |

**A solução** ([recorte da câmera](../architecture/recorte-da-camera.md)) trata o enquadramento e a obstrução juntos:

- **Enquadramento:** com o herói dentro da zona da torre (dado do perfil, `"recorte"`), as consultas da câmera ignoram
  as formas DAQUELA torre. A câmera fica com o braço que o jogador escolheu.
- **Obstrução:** o desenho recorta só aquela torre num cone câmera → herói, com borda pontilhada e abertura suave
  (5/s). A câmera só atravessa com o recorte já aberto.
- **Bordas da zona:** histerese de 0,35 u no vão da porta; quem sai leva o recorte por 2,5 u ou 1,5 s; quem chega de fora
  nunca ganha recorte.
- **O que não muda:**
  - a colisão do corpo;
  - a sombra;
  - a câmera fora da zona (igual passo a passo);
  - a vila, a v1 e os outros compostos (sem zona).

**A medida completa** (12.2 × recorte; total dos quadros com o herói dentro):

| Medida | 12.2 | 12.3 |
|---|---|---|
| câmera colada (< 1,2 u) | 40,6% | 2,1% |
| maior trecho colado | 2,00 s | 0,15 s |
| herói legível | 99,0% | 100% |
| interior visto (no quadro, sem nada na frente) | 57,1% | 74,3% |
| desvio automático do rumo, médio / máximo | 21,6° / 87,7° | 2,3° / 29,2° |
| referência do WASD, mudança máxima | 87,7° | 28,7° |
| vai-e-volta da distância / saltos > 0,8 u | 145 / 64 | 8 / 4 |
| na porta (até 2,5 u fora): colada / maior trecho | 11,6% / 1,30 s | 0% / 0 s |

**Por cenário (colada 12.2 → 12.3):**

| Cenário | Colada | Herói legível no 12.3 | Interior visto, 12.2 → 12.3 |
|---|---|---|---|
| entrada | 45,8% → 0% | 100% | 75 → 98% |
| escada | 20,7% → 8,8% | 100% | 58 → 55% |
| centro | 39,2% → 2,3% | 100% | 62 → 88% |
| mirante | 37,9% → 0% | 100% | 37 → 54% |
| descida | 4,1% → 0% | 100% | 63 → 69% |
| saída | 76,7% → 7,8% | 100% | 21 → 33% |
| giro rápido | 91,0% → 10,3% | 100% | 43 → 80% |
| mouse parado | 43,6% → 0% | 100% | 76 → 99% |
| parede de costas | 40,0% → 3,0% | 100% | 86 → 99% |
| parede de frente | 100% → 7,5% | 100% | 0 → 16% |
| diagonal | 46,1% → 1,0% | 100% | 50 → 70% |
| salto no lugar | 0% → 0% | 100% | 93 → 93% |

**O que sobra:**

- **Câmera colada:** só nos ~0,15 s iniciais dos cenários que COMEÇAM dentro da torre (a câmera espera o recorte
  abrir).
- **Saída:** no fim, a câmera encosta a ~2 u, nunca na nuca (P95).
- **A borda pontilhada:** com o contorno, forma um anel fino, visível parado (P96).
- **A escada:** o interior visto cai de 58% para 55%; com o braço inteiro, o cone mostra menos chão em volta do pé da
  escada.

**Visto em movimento:** vídeos 01 a 05 (antes × depois na mesma rota, as duas torres). Na 12.2 a nuca toma a tela em
quase todo o interior. Na 12.3 o herói, a escada, o pote e o mirante aparecem pelo disco. Nenhuma parede some vista de
fora, e nada atrás do herói é recortado.

**Regressões fora da torre** (o recorte não existe fora dela):

- **Testes:** os de câmera de antes passam sem mudança (`CameraModesTest`, os 10 cenários em movimento,
  `CompositeCollisionTest`, `MobilityTest`, `GroupCombatTest`, `CombatTest`).
- **Igualdade passo a passo:** rodeando a torre por fora, a câmera é bit a bit a de sem recorte (teste).
- **Vídeos:** 06 (câmera livre), 07 (combate junto ao carvalho e à pedra) e 11 (salto e corrida), com o binário novo.

## 3. A Índira

Três vagas comparadas no referencial do conjunto, nos 5 rumos, e capturadas no jogo (semente 28, roteiro
`tools/mundo/18-indira.txt`):

| Vaga | Folga do corpo | Porta | Fala | Capturas |
|---|---|---|---|---|
| lado da porta (12.2) | 0,60 u | 0,71 u, passa com folga | alcançável | **atrás da placa do ofício**: some da rua e de lado; na conversa a placa entra na frente da câmera |
| **varal de ervas** | 0,48 u | 2,45 u | alcançável | inteira da rua, dos dois lados e na conversa |
| canteiro | 0,24 u | 1,52 u | alcançável | atrás da cerca; espaço do gesto 0,5 u |

- **A escolhida:** a do varal (`herbalista_varal`, recomendada no catálogo). Ela não bloqueia a porta, não flutua (o pé
  assenta no piso real, `npcFloor`) e fica virada 20° para a rua.
- **A vaga atual não era a melhor:** a placa a escondia, e é um defeito demonstrável (vídeo 09, capturas
  `capturas/indira/`).
- **Ressalva de medida:** a conta por raios dava 5/5 para as três, porque a caixa de colisão da placa não representa a
  tabuleta.
- **O efeito no gerador:** só o ponto dela muda (88 de 200 regiões). Em 3 de 200 (52, 54, 138) muda também a decoração
  miúda em volta, porque o espalhamento evita o lugar dela; construções, POIs, inimigos e missões ficam iguais.
  `regioes-v2.txt` foi refeito: das 8, só a 2048 mudou.

## 4. O pote, a Brásia e a abóbada

- **Pote** (conceito aprovado pelo proprietário): validação visual final.
  - **Vistas:** frente, perfil, 3/4, fundos, silhueta, dia e fim de tarde (cheio e vazio lado a lado) e no jogo, na
    lareira da casa (vídeo 10, capturas `capturas/pote/`) e entre os barris da abóbada (vídeo 08).
  - **Cheio:** moedas na boca e uma no pé, lidas como moedas.
  - **Vazio:** a tampa vai para o chão e não sobra ouro; os dois estados se distinguem nas duas luzes.
  - **Defeito demonstrável:** nenhum, então nada mudou.
- **Brásia e a tina:** sem defeito novo demonstrável; nada mudou (a decisão P91 continua).
- **Abóbada:** dá para ver o pote, abrir, girar, sair e ler o herói (vídeo 08). Ao girar lá dentro, a câmera fica na
  nuca.
  - **Medida sem recorte** (`aAbobadaPorDentroComACameraDeSempre`): colada em 57% dos quadros (o teto e o morro), herói
    legível em 95,6%, pote visto em 99,5% das vezes em que se olha para ele.
  - **Ensaio do recorte nas abóbadas reais** (44, 54, 101, com o relevo): colada 63–77% → 0%, herói e pote 100%.
  - **Ainda não é seguro aplicar:** o topo do morro é alcançável pelo declive de trás e fica dentro da zona; ali a câmera
    entraria no modelo. Falta um limite de altura da zona.
  - **Fica com a câmera de sempre:** o teto de câmera desenhado pelo artista e o pedido de não abrir um segundo
    trabalho de câmera pesaram também.
  - **Registro:** P97, com a recomendação ([recorte da câmera](../architecture/recorte-da-camera.md) §5).
  - **Defeito achado no ensaio e corrigido:** o desenho procurava a construção perto do CENTRO da zona. Agora é pela
    origem do modelo, a 1 cm (teste).
- **Miniaturas dos mundos antigos:** registradas (P92); nenhuma miniatura regenerada e nenhum save mexido.

## 5. O Acapela

**Avaliação** (num clone isolado com o ramo do artista, sem tocar no ramo de trabalho):

- **Testes:** `AcapelaTest` 11/11 e `MundoNpcTest` 13/13.
- **Capturas do renderer real** (`capturas/acapela/`):
  - seis rumos;
  - o rabo parado, andando e correndo (3/4 e de trás);
  - o aceno de perto;
  - a alegria;
  - a tomada na distância e inclinação da câmera de jogo, ao lado do herói, da Kelyra, da Pluma e do Quelmo.

**O que se viu:**

- **Silhueta e proporção:** silhueta própria; 2,14 cabeças, 1,125 u (a altura da Kelyra).
- **Custo:** 4.224 faces (Kelyra 4.852, Pluma 4.248).
- **O rabo:** nasce no corpo, descansa no chão parado, balança andando e sobe correndo, sem atravessar as pernas nas
  tomadas (a sonda mede membro × rabo ≤ 1 voxel e rabo × chão zero).
- **O aceno:** passa à frente das franjas.
- **O rosto:** as quatro expressões leem.

**Defeito concreto:** nenhum, então nada mudou na identidade.

**Varredura:** nenhuma referência externa; autor noreply; mensagens só com título.

**Integração:** merge do ramo do artista (`d2a7134`), com os 9 commits dele (`218f504` a `103d50c`) preservados.

**Papel:** os documentos dele não definem profissão (`meta.role` = `morador`). Ele fica na biblioteca, fora de
`habitantes_e.json` e do perfil da v2, e nenhuma região muda. **Sugestões para o futuro** (decisão do proprietário,
P98):

- um morador "de passagem" que aparece na trilha perto de ruínas (comenta o lugar, sem missão);
- o ajudante da herbalista (a cor menta e as patas de pelo combinam com ervas e potes);
- um vendedor itinerante numa barraca de feira (a jardineira e o bolso já dizem "trabalho").

## 6. A v1, os saves e os mundos experimentais

- **A v1 não mudou:**
  - **Regiões:** 300 sementes iguais à base da 12.2 e `regioes-v1.txt` intocado (`RegionGoldenTest`).
  - **Código e perfis:** `RegionGenerator` v1 e os perfis de colisão que ela usa não foram tocados; o perfil novo
    (`recorte`) é só da torre, que a v1 não planta.
- **Saves antigos:** os 8 saves feitos por um binário antigo, abertos pelo binário novo, ficam iguais e sem `.invalido`
  (§9).
- **Mundos experimentais:** "RUINAS" e "31337" do proprietário não foram abertos nem modificados (hashes antes e depois
  no pacote). A cópia `.invalido` histórica continua lá.

## 7. A v2: determinismo e sementes

- **Sementes 1–200:**
  - **Tentativas:** sem recusa; 172 na 1ª tentativa, 23 na 2ª, 4 na 3ª e 1 na 4ª (igual à 12.2).
  - **Determinismo:** iguais em dois processos.
- **Ruínas** (iguais à 12.2): casa 94, torre 71, sítio 21, abóbada 14. A torre não ficou mais rara.
- **Comparadas à 12.2 sem os moradores:** iguais, menos a decoração miúda das sementes 52, 54 e 138 (§3).
- **Referência:** `regioes-v2.txt` refeito no commit `dee868b`, só pela Índira. Nenhuma semente foi ajustada à mão.

## 8. Testes, validadores e desempenho

**Testes:** 456, 0 falhas, 1 pulado (o escritor da referência, só quando pedido).

- **Por módulo:** engine 45, game 255, viewer 156.
- **Novos (17):** `TowerCameraTest` (6) e `AcapelaTest` (11, do artista).
- **Os de câmera de antes** passam sem mudança.

**Compilação:** `-Xlint:all` sem nenhum aviso (todos os módulos, código e testes).

**Validadores e portões** (`validadores.txt`):

| Conferência | Resultado |
|---|---|
| `--check` | 129 animações, todas ok |
| `--check interrupcao-fina`, `golpe-andando`, `esquiva` | seguros |
| `--check-cabelos` | nenhum estudo pior que a referência |
| `--occlusion` | só a exceção documentada do arco |
| presets × canônico | 51 pares, 0 pixel de diferença |
| referências canônicas | 22, equivalentes |
| links da documentação | 0 quebrados |
| áudio | 47 arquivos, 0 alerta |

**Desempenho:** §8.1.

### 8.1 Desempenho (12.2 × 12.3, binário final)

**Como se mediu:**

- **Binários:** os dois intercalados na mesma sessão, 3 repetições cada; a 12.2 é um clone compilado de `7261f3e`.
- **Condições:** 1080p, janela oculta, mudo, `glFinish` por quadro, GPU por passo.
- **Roteiros:**
  - torre por dentro: `tools/mundo/desempenho-torre.txt` (duas voltas de câmera, a escada e a volta);
  - vila fixa: `tools/jogo/desempenho.txt`;
  - regiões: `tools/mundo/desempenho.txt`.

| Cenário | Binário | Quadro mediana | p95 | p99 | Lógica | GPU cena | GPU soma | Draws | Triângulos | Malhas (MB) |
|---|---|---|---|---|---|---|---|---|---|---|
| vila fixa | 12.2 | 9,79 | 11,81 | 12,56 | 0,28 | 4,66 | 6,98 | 114 | 492.840 | 206 (106,1) |
| vila fixa | 12.3 | 9,92 | 11,83 | 12,71 | 0,28 | 4,75 | 7,11 | 114 | 492.840 | 206 (106,1) |
| torre por dentro (s2) | 12.2 | 10,95 | 12,45 | 13,12 | 1,18 | 5,31 | 7,65 | 18 | 252.684 | 138 (90,7) |
| torre por dentro (s2) | 12.3 | 10,24 | 11,74 | 12,53 | 0,39 | 5,17 | 7,42 | 155 | 549.364 | 138 (90,7) |
| v2 s44 (abóbada) | 12.2 | 9,51 | 11,95 | 12,68 | 0,48 | 4,53 | 6,75 | 98 | 431.464 | 141 (93,7) |
| v2 s44 (abóbada) | 12.3 | 9,46 | 11,43 | 12,10 | 0,48 | 4,40 | 6,54 | 98 | 431.464 | 141 (93,7) |
| v2 s28 (rural, Índira) | 12.2 | 9,32 | 10,66 | 11,20 | 0,41 | 4,36 | 6,57 | 77 | 406.614 | 158 (94,6) |
| v2 s28 (rural, Índira) | 12.3 | 9,64 | 11,07 | 12,18 | 0,44 | 4,46 | 6,73 | 77 | 406.614 | 158 (94,6) |

Tempos em ms; tabela completa em `round-12-3-final/desempenho/resumo.md`.

- **Dentro da torre, a 12.3 é mais leve:**
  - quadro −0,7 ms e lógica 1,18 → 0,39 ms, porque a câmera colada da 12.2 rodava a busca de desvio (dezenas de raios) a
    cada passo;
  - a GPU também cai, mesmo desenhando 155 draws e 549 mil triângulos, porque a câmera agora vê o mundo;
  - malhas e memória são as mesmas.
- **Fora da torre:** o recorte não existe e o shader dos outros desenhos é o de antes (variante `VX_CUT`). As diferenças
  (−0,05 a +0,32 ms de quadro) ficam dentro do ruído: as faixas das 3 repetições se sobrepõem, e a s28 deu +0,01 ms na
  medição anterior.
- **Correção feita pela medida:** a primeira versão (um `if (uCut <= 0.0)` no shader de todos os desenhos) custava
  +0,17 ms de GPU no passo de cena da vila, constante nas 3 repetições. O código do recorte passou para uma variante
  compilada à parte, e só os desenhos recortados a usam.
- **Custo da solução de câmera:** a zona é um círculo por construção com recorte; a câmera faz menos consultas, não mais.

## 9. Saves

| Save | Resultado |
|---|---|
| os 8 saves feitos pelo binário da `main` publicada (`4cbde86`): v1 ativa e concluída, v2 ativa (marco aceso) e concluída, 4 combinações do menu | abertos pelos dois binários de cópias idênticas: **8 de 8 iguais**, 0 `.invalido`, 0 exceção (`tools/qa/saves_antigos.sh`) |
| saves v2 feitos na 12.2 | abrem: o save v2 guarda semente, versão, tentativa, região e vínculos, e a 12.3 não mudou nenhum (a Índira não entra no save) |
| JSON cortado, formato estranho, versão mais nova, região de testes | as mesmas respostas da 12.2 (`SaveCompatTest`) |
| voltar ao menu salvando e continuar | volta no mesmo lugar (vídeo 12) |
| mundos "RUINAS" e "31337" do proprietário | não abertos nem modificados; a cópia `.invalido` histórica continua lá |

Os dados do proprietário foram conferidos por sha256 antes e depois de cada etapa do pacote, e ficaram intactos.

## 10. Publicação

(preenchido abaixo)

## 11. As 21 respostas

1. **O P85 foi corrigido?** **Sim, pelas medidas.**
   - colada 40,6% → 2,1%, trechos de 2,00 → 0,15 s;
   - na porta 11,6% → 0%;
   - herói legível 100%, nos 12 cenários e 5 rumos da torre.

   A aprovação final é do seu teste físico.
2. **Qual solução venceu?** A — recorte contextual:
   - a câmera ignora só a torre em que o herói está;
   - o desenho recorta só essa torre num cone até o herói.

   A B (geometria) não foi preciso.
3. **Taxa de câmera colada antes e depois?** 40,6% → 2,1% (o resto são os 0,15 s iniciais de quem já começa dentro).
4. **Maior trecho ruim?** 2,00 s → 0,15 s dentro da torre; 1,30 s → 0 s na porta.
5. **Houve regressão global?** Não.
   - Fora da torre a câmera é idêntica passo a passo (teste) e os testes de câmera de antes passam.
   - A vila, a v1 e os outros compostos não têm zona.
   - O shader dos outros desenhos é o de antes: o custo de +0,17 ms da 1ª versão foi achado e tirado.
6. **A torre ficou coerente?** Sim: a silhueta, a geometria e a colisão não mudaram.
   - Dentro, o disco recortado mostra o herói, a escada, o pote e o mirante; nada atrás do herói é recortado.
   - Ficam o anel pontilhado parado (P96) e o salto da distância no fim da saída de costas (P95), os dois baixos.
7. **A Índira ficou melhor?** Sim.
   - **A vaga antiga:** a placa do ofício a escondia da rua e de lado, e entrava na frente da câmera na conversa.
   - **A do varal:** ela aparece inteira em todas as tomadas, sem bloquear a porta e sem flutuar.
8. **O pote foi validado?** Visualmente, sim: frente, perfil, câmera de jogo, cheio × vazio, dia e fim de tarde, na
   lareira e entre os barris. Não achei defeito demonstrável e nada mudou; a aprovação artística é sua.
9. **A abóbada é funcional?** Sim: dá para ver o pote, abrir, girar, sair e ler o herói.
   - **A câmera lá dentro:** colada em 57% (o teto).
   - **O recorte:** o ensaio nas abóbadas reais mostra que ele resolveria (63–77% → 0%), mas pede um limite de altura da
     zona. Fica recomendado (P97), sem aplicar.
10. **A v1 ficou idêntica?** Sim: 300 sementes iguais, `regioes-v1.txt` intocado, `RegionGenerator` v1 e os perfis dela
    não tocados.
11. **Os saves antigos continuam válidos?** Sim: os 8 da `main` publicada abrem iguais, sem `.invalido`, e os da 12.2
    também abrem.
12. **Os mundos experimentais foram preservados?** Sim: "RUINAS" e "31337" não foram abertos nem modificados, e a cópia
    `.invalido` histórica continua lá.
13. **Quantos testes passaram?** 456, 0 falhas, 1 pulado; `-Xlint:all` sem aviso; todos os validadores e portões verdes.
14. **Quantas sementes foram verificadas?** v1: 300. v2: 200, em dois processos (deterministas, sem recusa), mais 200
    comparadas à 12.2 com e sem os moradores, e as 8 das referências de cada versão.
15. **Qual o impacto de desempenho?** Dentro da torre, melhor (quadro −0,7 ms, lógica −0,8 ms). Fora, dentro do ruído.
    Memória e malhas iguais.
16. **Quais problemas permanecem?**
    - P95: o fim da saída de costas;
    - P96: o anel pontilhado;
    - P97: a câmera da abóbada;
    - P98: o papel do Acapela;
    - P99: o teste físico;
    - e os de antes: P91 (decisão registrada) e P92 (miniaturas).
17. **O clone limpo funciona?** (§10)
18. **O CI passou?** (§10)
19. **Qual a URL do PR?** (§10)
20. **Está pronto para o teste físico completo?** Sim. Roteiro com as sementes em `round-12-3-final/roteiro-de-teste-fisico.md`.
21. **Algum bloqueio para congelar a v2?** Nenhum bloqueio técnico conhecido; falta o seu teste físico. Se a câmera da
    torre, a Índira e a abóbada passarem nele, congelar a v2 é:
    - pôr a v2 em `RegionGenerator.published`;
    - dar a lista do `mundo-procedural.md` §11 e §12 como fechada;
    - fazer o merge e a tag.
