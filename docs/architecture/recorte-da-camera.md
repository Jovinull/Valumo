# Recorte contextual da câmera (torre caída, P85)

**Status:** implementado na rodada 12.3, só na torre caída (`ruin_tower`), com medida 12.2 × 12.3 nos mesmos trajetos. A
câmera do resto do jogo não mudou: vila, v1, oficina, ruína de casa, barraca, sítio, abóbada, combate e conversa são os
mesmos de antes, passo a passo fora da zona (teste).

## 1. O problema (P85)

- **A torre:** o interior é um vão de ~2,6 u dentro de uma muralha de até 4,4 u, com a porta estreita.
- **A câmera de sempre** (`FollowCamera`) tem um braço de 4,6 u e encosta na hora quando algo bloqueia a linha de
  visão. Dentro da torre, qualquer rumo de câmera atravessa a muralha, e ela vai para trás da nuca.
- **Medido na 12.2** (andando com a física real): câmera colada (< 1,2 u) em 52% do tempo, trechos de até 1,43 s; o
  desvio automático girava a câmera até 88° sozinha.

A causa é geométrica: um braço de 4,6 u não cabe num vão de 2,6 u. Transparência sozinha não resolve, porque a câmera
continuaria presa na parede. O recorte trata as duas coisas juntas: o enquadramento (o braço) e a obstrução (o desenho).

## 2. As alternativas

| Alternativa | O que faz | Por que ficou ou não |
|---|---|---|
| Câmera da 12.2 (base) | braço curto contra a muralha | 40,6% colada nos 12 cenários, trechos de 2,0 s, desvio médio 21,6° |
| Zona com braço curto e câmera de cima (12.2) | inclinação até quase vertical em volta da torre | melhor caso 26% colada; câmera quase vertical num raio de 3 u, inclusive fora — rejeitada na 12.2 |
| **A — recorte contextual** (adotada) | a câmera ignora só a torre; o desenho recorta só a torre num cone câmera → herói | 2,1% colada (só a partida dentro da torre), trecho máximo 0,15 s, herói legível 100%, desvio 2,3° |
| B — geometria (vão mais largo) | alargar o interior | não foi preciso: muda a silhueta aprovada e as regiões v2, e um vão de 4 u ainda não comporta o braço de 4,6 u |

## 3. O contrato

**Dado, não nome.** O perfil composto da torre em `assets/scenes/colisao_e.json` tem uma zona:

```json
"ruin_tower": {"tipo": "composta", "recorte": {"centro_u": [0.062, 0.062], "raio_u": 2.0}, "formas": [ … ]}
```

- **De onde vem:** `colisao.recorte` do catálogo (`tools/conteudo_poi.py`), copiado por `tools/conteudo/formas_compostas.py`.
- **Espaço:** centro no espaço do modelo (passa pela matriz do posicionamento) e raio em u (com a escala).
- **Quem tem:** só a torre (`TowerCameraTest.soATorreCaidaTemRecorte`). Construção nova com recorte é decisão de projeto,
  medida antes; nunca `if nome ==`.

**Colisão** (`Collision`):

- `addComposite` registra a zona (`Cutaway`) e marca cada forma da construção com `owner` = o índice da zona.
- `cameraSkip` = a zona cujas formas as consultas DA CÂMERA ignoram nesta chamada: `rayClear`, `cameraInside` e as buscas
  de vista. O corpo (`resolve`, `floor`, `bodyAt`, `support`) nunca olha `cameraSkip`
  (`TowerCameraTest.oRecorteNaoMudaAColisaoDoCorpo`).

**Estado** (`CameraCutaway`, um por `GameLogic`):

| Constante | Valor | Para quê |
|---|---|---|
| `RATE` | 5/s | o peso do recorte sobe e desce (entrada e saída suaves) |
| `OPEN` | 0,5 | a câmera só atravessa a torre com o recorte já aberto até aqui (~0,14 s) |
| `HYST` | 0,35 u | parado no vão da porta, nada pisca |
| `EXIT`, `EXIT_T` | 2,5 u, 1,5 s | quem estava DENTRO e sai leva o recorte até 2,5 u além da zona ou 1,5 s, o que vier antes |
| `RADIUS`, `CORE` | 1,4 u, 0,75 | o raio do cone na altura do herói e o miolo sem pontilhado |

- **Quem chega de fora** nunca ganha recorte, nem encostado na parede: só ao entrar na zona.
- **A cada quadro:**
  1. `GameLogic.update` chama `cut.update` com a posição do herói;
  2. põe `collision.cameraSkip = cut.skip()`;
  3. roda o quadro;
  4. devolve `cameraSkip = -1` num `finally`.

  Fora do quadro lógico (vistas, prévias, medições do roteiro) nada é ignorado.

**Desenho** (`World.cutaway`, `Renderer.Draw.cut`, `scene.frag`):

- **O que se marca:** só o desenho daquela construção, achado pelo nome do prop e pela ORIGEM do modelo (a mesma matriz
  do posicionamento, a 1 cm; a zona guarda `ox`, `oz`), com o peso.
  - **Por que a origem:** a primeira versão procurava a 0,6 u do centro da zona. Funcionava na torre (centro a 0,09 u da
    origem), mas não numa zona deslocada: no ensaio da abóbada (centro a 0,8 u) a câmera atravessava e o desenho não era
    recortado.
- **O shader:** descarta, só nesses desenhos, o fragmento ANTES do herói (até 0,3 u da frente dele) dentro de um CONE com
  o vértice na câmera e raio 1,4 u na altura do peito do herói.
  - **Na tela:** o cone é um disco de tamanho constante em volta do herói. O cilindro da primeira versão abria um buraco
    enorme no que estava perto da câmera (capturas).
  - **A borda:** o quarto de fora do raio é pontilhado (Bayer 4×4), para o buraco não ter aresta dura.
- **O que não muda:**
  - **Sombra:** é outro passo; a torre continua fazendo a sombra inteira.
  - **Pré-passo de profundidade:** pula os desenhos recortados.
  - **Os outros desenhos:** não pagam nada. O código do recorte só existe na variante `VX_CUT` do `scene.frag`
    (`Renderer.sceneCut`, `Shader` com `#define`), e só os desenhos marcados usam essa variante.
    - **Por que a variante:** a primeira versão, com um `if (uCut <= 0.0)` no shader de todos, custou +0,17 ms de GPU
      no passo de cena na vila fixa, que nem tem recorte (três repetições intercaladas com o binário da 12.2).

## 4. O que se vê

- **Dentro:** a câmera fica com o braço que o jogador escolheu, atrás da muralha. A parede entre ela e o herói se abre
  num disco em volta dele, e o piso, a escada, o pote e o mirante aparecem pelo buraco.
- **Atrás do herói:** nada da torre é recortado (a muralha do outro lado continua inteira). O mapa não fica à vista
  através de construções: só a torre em que o herói está, só no cone.
- **Fora:** igual à rodada 08 (as paredes de fora seguram a câmera como as de qualquer casa), menos nos 2,5 u / 1,5 s
  depois de sair de dentro.

## 5. Medida (12.2 × recorte, `TowerCameraTest`, relatório em `build/medidas/torre-camera.txt`)

**Como se mede:**

- **Torre:** a 0°, 37°, 90°, 180° e 270°.
- **Simulação:** física e câmera reais, quadros de 1/60 s com dois passos de 1/120 s.
- **Cenários (12):** entrada, escada, centro (volta lenta), mirante (salto + volta), descida, saída, giro rápido,
  mouse parado, parede de costas, parede de frente, diagonal, salto no lugar.

| Total (quadros com o herói dentro) | 12.2 | recorte |
|---|---|---|
| câmera colada (< 1,2 u) | 40,6% | 2,1% |
| maior trecho colado | 2,00 s | 0,15 s |
| herói legível (3 de 5 pontos do corpo vistos) | 99,0% | 100% |
| interior visto (pontos do piso no quadro e sem nada na frente) | 57,1% | 74,3% |
| desvio automático do rumo: médio / máximo | 21,6° / 87,7° | 2,3° / 29,2° |
| referência do WASD: mudança máxima | 87,7° | 28,7° |
| vai-e-volta da distância / saltos > 0,8 u | 145 / 64 | 8 / 4 |
| na porta (até 2,5 u fora), colada / maior trecho | 11,6% / 1,30 s | 0,0% / 0,00 s |

- **Os 2,1% que sobram:** são os primeiros 0,15 s dos cenários que COMEÇAM dentro da torre (o herói posto lá, como ao
  carregar um jogo): a câmera espera o recorte abrir (`OPEN`).
- **Os 4 saltos que sobram:** são o fim da saída, quando o herói passa de 2,5 u. A câmera, que ficou dentro da torre
  olhando para fora, encosta na parede de trás da porta, a ~2 u, não na nuca.

**A abóbada fica com a câmera de sempre nesta rodada.**

- **No teste de chão plano:** colada em 57% dos quadros (o teto e o morro do modelo), herói legível em 95,6%, pote visto
  em 99,5% das vezes em que se olha para ele.
- **Ensaio do recorte nas abóbadas REAIS** (sementes 44, 54 e 101, com o relevo da região; ferramenta à parte, zona de
  2,2 u no interior; da boca ao pote, uma volta, a saída):

  | Semente | Colada | Herói legível | Pote visto |
  |---|---|---|---|
  | 44 | 62,8% → 0% | 95,9% → 100% | 99,6% → 100% |
  | 54 | 76,5% → 0% | 94,6% → 100% | 98,7% → 100% |
  | 101 | 64,7% → 0% | 91,6% → 100% | 100% → 100% |

  Com o braço inteiro, a câmera passa por cima do morro, e o relevo não a segura.
- **Por que não foi aplicado agora:**
  - **O morro:** o herói pode chegar ao topo do morro pelo declive de trás, dentro da zona. Lá a câmera ignoraria o modelo
    e poderia entrar nele. A zona precisa de um limite de altura (os pés até ~1 u acima do piso de dentro), que ainda não
    existe.
  - **O teto:** o teto de câmera da abóbada é desenho do artista, aprovado sem redesenho.
  - **O pedido da rodada:** não abrir um segundo trabalho de câmera.
- **O que fica:** recomendação para a próxima rodada (P97). O dado já cabe no contrato: `recorte` no catálogo da abóbada,
  mais o limite de altura e o mesmo teste.

## 6. Custo

- **Lógica:** a zona é um círculo por construção com recorte e o peso é uma exponencial. As consultas de câmera pulam
  formas (menos trabalho, não mais).
- **Desenho:** o cone (um produto escalar e uma distância) só nos fragmentos da torre, pela variante `VX_CUT`; a troca de
  programa só acontece em volta dos desenhos recortados.
- **Medido:** em `docs/validation/relatorio-rodada-12-3.md` (§Desempenho), os dois binários intercalados na mesma sessão.

## 7. Testes

`TowerCameraTest`:

- `oRecorteTiraACameraDaNucaDentroDaTorreSemPerderOHeroi` — a medida acima, com a meta da rodada:
  - trecho colado ≤ 0,5 s, colada ≤ 10% e ≤ 1/3 da 12.2;
  - herói legível ≥ 95%;
  - interior visto, desvio e WASD não pioram.
- `foraDaTorreACameraEIdenticaPassoAPasso` — rodeando a torre por fora, a câmera é bit a bit a de sem recorte.
- `asTransicoesDoRecorteSaoSuavesELimitadas` — de fora nada; abre em < 0,25 s; nada pisca no vão; a saída dura no máximo
  `EXIT_T`; a v1 e a vila não têm zonas.
- `soATorreCaidaTemRecorte`, `oRecorteNaoMudaAColisaoDoCorpo`, `aAbobadaPorDentroComACameraDeSempre`.

Os testes de câmera de antes (`CameraModesTest`, `CompositeCollisionTest`, `MobilityTest`, `GroupCombatTest`) passam sem
mudança.

## 8. Limites

- **A borda pontilhada:** com o contorno do pós-processamento, ela forma um anel fino em volta do buraco. É visível
  parado e some em movimento (vídeos da rodada).
- **Herói no vão da porta com a câmera do lado de fora:** se a parede do lado de fora tapa, a câmera continua a de sempre
  (a zona só começa dentro).
- **Construção nova com interior estreito e aberto:** o recorte vale para ela só depois de medida com este teste; a
  abóbada (coberta) não ganhou.
