# Relatório — Rodada 09.3: polimento da esquiva, do salto e das transições

**Estado:** entregue para revisão, sem commit nem push. Tag `playable-foundation-v0.1` intocada; a Rodada 10 não foi
iniciada.

**Pacote:** `output/visual-review/round-09-3/`. O zip `rodada-09-3-imagens-e-documentos.zip` leva imagens, pranchas e
documentos; os MP4 ficam separados.

**Reprodução:** `tools/review93.sh tudo`. As partes: `animacao`, `videos`, `antes`, `desempenho`, `testes`,
`validadores`, `regressao`, `documentos`, `zip`.

> **Decisão do proprietário, depois da entrega:** a esquiva é a **R2**. O rolamento da 09.2 e o R1 saíram do jogo
> (arquivados em `docs/historico/esquivas-09-3/`), junto com os estilos (`-Dvx.esquiva`) e as janelas próprias do R1.
> P43 ficou resolvido: o R2 nunca inverte o corpo, e a cabeça fica a mais de 0,15 u do chão (teste). Testes: 215
> (os 208 anteriores e 7 da rodada). As seções abaixo registram a comparação que levou à escolha.

**Em uma frase:** a esquiva e o salto ganharam forma corporal sem mudar um número de jogo. O rolamento para a frente
melhorou só em parte (§1). Distância, invulnerabilidade,
recarga, altura, tempo no ar, coyote e buffer são os mesmos da 09.2; o perfil C é o mesmo.

## 1. Diagnóstico do rolamento da 09.2

O rolamento antigo (`roll`) parecia um boneco girando suspenso. Na prancha `animacao/roll.png`, entre 0,15 e 0,25 s, o
corpo vira uma **coluna invertida**: cabeça no chão, pernas para cima ("bananeira"). Quatro causas se somavam:

1. **Giro rígido no eixo sagital, somado à inclinação do tronco.** O `spin` girava o corpo inteiro em torno de um eixo
   lateral. Com o tronco já dobrado 50–60°, o meio do giro ficava vertical.
2. **A cabeça era o volume mais baixo.** O contato com o chão (`rollContact`) prende o volume mais baixo ao relevo. No
   meio do giro esse volume era a cabeça, que era "plantada" no chão.
3. **Pivô alto.** O centro do giro ficava na altura do peito, e o corpo descrevia uma roda em vez de rolar sobre as
   costas.
4. **Espada saliente e subida mecânica.** A lâmina apontava para fora como um eixo, e no fim o corpo subia em linha reta
   até a pose de guarda.

**Principal causa:** os dois primeiros juntos. O eixo puramente sagital punha a cabeça embaixo, e o contato a prendia
ao chão.

**Limite medido na própria 09.3** (sonda a cada passo da peça mais baixa e da altura do corpo, agora o teste
`theR1RollStaysLowerThanThe092RollAndTheHeadNeverSinks`): com 2,4 cabeças de altura, a cabeça é a superfície da "bola"
sempre que o corpo inverte, em qualquer rolamento que passe pelas costas.

| | cabeça como peça mais baixa | altura média do corpo no meio do giro | máxima |
|---|---|---|---|
| 09.2 | 17 de 68 passos | 0,78 u | 0,97 u |
| R1 (entregue) | 19 de 68 passos | 0,68 u | 0,86 u |

A cabeça encosta sem afundar nos dois. Tentei três correções, medi cada uma e descartei todas:

| Tentativa | Resultado | Por que foi descartada |
|---|---|---|
| eixo quase lateral, rolamento "de tronco deitado" | cabeça embaixo em 8 passos | o corpo ficou **suspenso** até 0,34 u, apoiado na espada; com a lâmina ao longo do eixo, ainda apoiado nela |
| rolar pelo ombro do escudo | cabeça embaixo em 15–24 passos | pior |
| pernas recolhidas | altura 0,66 u, cabeça em 18–19 passos | quase igual (o IK limita) |

**Conclusão honesta:** o R1 deixa a coluna da 09.2 mais baixa e diagonal, mas não elimina a cabeça como contato. Uma
"bola" de verdade (cabeça recolhida entre os ombros) exigiria articular o pescoço, ou seja, mexer no rig dos
protagonistas, que estão congelados.

## 2. Alternativas: R1 × R2

| | R1 — rolamento diagonal pelo ombro (`roll_r1`) | R2 — salto esportivo baixo (`dodge_f`, `dodge_l`, `dodge_r`) |
|---|---|---|
| Ideia | rolar por cima do ombro, costas e quadril, em diagonal (`spinAxis` −25 a −40°), pivô baixo e à frente (`spinPivot`) | mergulho curto e baixo para a frente; para os lados, salto inclinado (`rollZ`) com os pés afastados (`feetX`) |
| Contato | corpo encostado no meio do giro (folga mediana < 0,02 u); a cabeça é a peça mais baixa por ~0,13 s, sem afundar | pés: é um salto, não inverte |
| Leitura | rolamento diagonal, 13% mais baixo que o da 09.2; ainda passa pela cabeça | lê como "desviar"; de frente parece um passo largo |
| Lados com a mira travada | vira o corpo (como a 09.2) | **mantém o rosto no alvo** |
| Cortes de golpe | não cabe no fim de `slash_h` e `slash_d` (lâmina × cabeça) → janelas próprias + substituto | cabe em todas as janelas gerais |

**Qual ficou melhor?** Nenhum sozinho:
- **Para a frente, nenhum resolve tudo.** O R1 é um rolamento, mais baixo e diagonal que o da 09.2, mas ainda passa
  pela cabeça. O `dodge_f` (R2) nunca inverte, mas é um mergulho curto, não um rolamento. Recomendo o R1 por ser um
  rolamento e pela melhora medida; se o proprietário achar que ainda "planta a cabeça", o `dodge_f` é a troca de uma
  linha (`esquiva.estilo` = `R2`).
- **R2 é melhor para os lados com a mira travada.** O corpo continua encarando o alvo; rolar de lado virando o corpo era
  o defeito P43.

**Recomendação técnica na entrega (substituída pela decisão do proprietário: R2):** o estilo **R1+R2**:
- R1 para a frente e para trás (o corpo vira para o rumo);
- R2 para os lados com a mira travada;
- `dodge_f` como substituto do R1 nos cortes tardios.

Na entrega, o proprietário escolhia pelo teste físico com `-Dvx.esquiva=09.2|R1|R2|R1+R2` (opção removida depois da escolha). O rolamento da 09.2
continua disponível, como pede o critério "não adote só porque custou trabalho".

## 3. Antes/depois do rolamento

**Pranchas** (perfil, frente e três quartos, 12 instantes de 0 a 0,56 s):
- `animacao/roll.png`: 09.2;
- `animacao/roll_r1.png`: R1;
- `dodge_f`, `dodge_l`, `dodge_r`: R2;
- `backstep`: o recuo, intocado.

**Vídeos do visualizador** (câmera fixa, 1× e 0,25×):
- `A1` (09.2 × R1 × R2 de perfil);
- `A2` (R1 em três quartos, frente e costas);
- `A3` (R2 lados e recuo).

**Vídeos do jogo** com o som da aplicação:
- 01 (livre), 02 (travada), 03 (contra a cabeçada), 04 (contra o poço), 05 (Maga com chapéu e robe);
- `B-01`, `B-02`: o mesmo roteiro no binário da 09.2 e no da 09.3, lado a lado.

## 4. Antes/depois do salto

- **Física:** idêntica (0,62 u, ~0,42 s, coyote 0,09 s, buffer 0,12 s).
- **Preparação:** a solução foi o **impulso por cima do voo**. A física sobe no **mesmo passo** do aperto (teste
  `theJumpLeavesTheGroundOnTheFirstStepAndShowsTheTakeoff`). Nos primeiros 0,10 s o desenho agacha o quadril (curva
  (1−u)²) e empurra os pés para trás. Na tela parece "agacha e salta", mas o comando não espera nada.
- **Atraso de comando:** nenhum. Zero passos entre o aperto e a subida.
- **Ar:** recolhe os pés e inclina o tronco (como na 09.2). Nos últimos 0,14 s antes do chão previsto, estende as pernas
  à frente (preparação da queda).
- **Pouso:**
  - a absorção escala com o impacto: 0,75× a 1,25× de 0,16 s (salto curto × queda de superfície alta);
  - correndo, é 35% mais curta e menos funda, e a velocidade não cai (`landingWhileRunningDoesNotStopTheRun`: mais de
    90% de 3,5 u/s durante o pouso);
  - parado, os pés abrem.
- **Evidência:**
  - pranchas `animacao/salto-{parado,correndo}.png` × `salto-antigo-*`;
  - vídeos `A4-salto-*-antes-x-depois-{normal,lento}`, 06 (saltos, pedra pequena, encosta), 07 (salto no combate,
    clique no ar sai no pouso) e `B-06` (09.2 × 09.3).

## 5. Transições, armas, chão e acessórios

- **Transições:** `viewer --check esquiva` cobre os seis gestos (`roll`, `backstep`, `roll_r1`, `dodge_f/l/r`) nos dois
  kits:
  - cortes de golpe → esquiva a cada 0,01 s nas janelas, com o R1 só nas próprias;
  - saídas esquiva → golpe, reação e guarda;
  - entradas parado, andando e correndo.

  Resultado: **segura**. As esquivas entram em 0,08 s a partir da locomoção (`fadeIn` novo por gesto); com os 0,16 s
  padrão, a ponta da espada saltava 0,23 u num passo.
- **Janelas do R1:** foram medidas pelo validador:
  - `slash_h`: [0,01–0,27] e [0,78–1,09];
  - `slash_d`: [0,01–0,30], [0,74–0,76] e [0,92–1,12];
  - `slash_v`: [0,01–0,36] e [0,53–1,19].

  Fora delas, mas dentro das gerais (as da 09.2), sai o `dodge_f`. A decisão de jogo não muda; só o gesto
  (`lateInTheSlashTheForwardDodgeUsesTheMeasuredSubstitute`).
- **Armas nas poses do JOGO:** `inGameDodgesAndJumpsKeepWeaponsSafeOnTheGroundAndContinuous` avalia a pose desenhada a
  cada passo. Cobre:
  - estilos R1+R2 e R2;
  - 5 rumos, mira livre e travada, esquiva e salto em seguida.

  Lâmina, cabo e escudo não passam das tolerâncias do `WeaponCheck` (as mesmas, sem afrouxar). Nada afunda no chão além
  dos pés andando; a ponta não salta mais que 0,45 u por passo.
- **Defeitos que os testes acharam e que foram corrigidos na pose (não na tolerância):**
  - **pomo × perna da Maga** (robe largo) no R1: a mão subiu, a lâmina passou a ir por cima com o pomo para trás, e
    entrou a pose `r1_rise`;
  - **canela afundando** no `dodge_r`: a inclinação lateral passou a levantar o corpo inteiro, quadril e pés.
- **Contato:**
  - `theR1RollTouchesTheGroundWithTheBodyInsteadOfFloating` mede o corpo no meio do giro: folga mediana abaixo de
    0,02 u, máxima abaixo de 0,08 u. Não flutua.
  - `theR1RollStaysLowerThanThe092RollAndTheHeadNeverSinks` exige o R1 mais baixo que o da 09.2 e a cabeça sem afundar,
    nos dois.
  - **A cabeça continua sendo a peça mais baixa por ~0,13 s** (§1).
- **Acessórios:** `appearancesWithHatsHoodsAndPacksStaySafeInEveryDodgeAndTheJump` cobre os três protagonistas (e todos
  os presets do catálogo) com o kit espada e escudo, mais uma aparência com chapéu de maga e outra com capuz:
  - seis sequências (entradas, saídas, corte para golpe);
  - zero casos.

  O chapéu, o capuz, a mochila e o cachecol seguem a cabeça e o tronco (são partes articuladas ou fundidas, sem
  simulação). Com o chapéu de maga, a aba pode encostar no chão no momento em que a cabeça encosta. A cabeça não
  afunda, mas o chapéu não foi medido separadamente.
- **Proporções:** nenhum asset foi tocado. `AssetContractTest` e `QualityTest` passam, e o gate dos 51 presets dá **0
  pixel** de diferença.

## 6. Câmera (P37, P41, P42)

- **Ajuste localizado testado e revertido.** Testei o "aperto": permitir mais desvio de rumo quando a câmera fica
  encurralada (pedra + encosta). O pior caso melhorou, mas a câmera travada e outros cenários pioraram. Revertido por
  inteiro.
- **Resultado:** os números do `CameraModesTest` são **idênticos** aos da 09.2 (`camera-modos.txt`):
  - travada: 19,3%, 4 quadros dentro;
  - livre centralizando: 37,2%;
  - alinhados: 58,6%;
  - +10°: 34,2%;
  - 09.1: 11,2%.
- **O que entrou:** só uma mitigação visual. Com a câmera a menos de 0,9 u do eixo do corpo (do quadril ao alto da
  cabeça), o herói se dissolve em pontilhado ordenado, até 75% (`Game.nearFade`, `uFade` no `scene.frag`). Não muda a
  posição da câmera nem o controle, e não há giro nem subida automáticos.
- **Evidência:** `camera-p37-p41-p42/t*.jpg` (09.2 × 09.3, mesmos instantes do roteiro 08). Em t=6,0 s, a câmera dentro
  do cabelo vira cabelo translúcido com o fungo visível através dele.
- **Avaliação honesta:** o enquadramento continua ruim nesses instantes, só que agora se vê através do herói. **Os
  problemas de câmera permaneceram**; mudou só o que se vê quando a câmera entra no herói.

## 7. Testes e validadores

- **Testes:** 217, 0 falhas. São os 208 anteriores, todos passando, mais 9 novos em `DodgeJumpPolishTest`:
  - paridade entre estilos;
  - lados encarando o alvo;
  - substituto medido;
  - poses do jogo seguras;
  - contato do R1;
  - altura do R1 × 09.2 e cabeça sem afundar;
  - salto no primeiro passo;
  - pouso correndo;
  - acessórios.
- **Validadores:**
  - `viewer --check`: TODOS OK;
  - `interrupcao-fina`: seguros;
  - `golpe-andando`: tudo seguro;
  - `esquiva`: segura;
  - `--occlusion`: igual à 09.2. O único alerta continua sendo o `bow_shot` da Exploradora (arco fora do combate); os
    quatro gestos novos passam.
- **Gate:** 51 pares, 0 com diferença.

## 8. Desempenho

Mesma cena, roteiro, resolução (1920×1080, 60 quadros/s) e carga, duas repetições (`desempenho/medicao.txt`).

| | quadro médio | mediana | p95 | p99 | animação/avaliação |
|---|---|---|---|---|---|
| 09.2, um fungo | 11,07 / 11,07 | 10,81 / 10,77 | 12,87 / 12,91 | 15,04 / 14,97 | 0,047 / 0,045 ms |
| **09.3, um fungo** | 11,14 / 10,92 | 10,94 / 10,69 | 13,16 / 12,84 | 15,10 / 14,60 | 0,051 / 0,047 ms |
| 09.2, três fungos | 11,07 / 11,38 | 10,91 / 11,22 | 13,32 / 13,29 | 14,92 / 15,16 | 0,036 / 0,038 ms |
| **09.3, três fungos** | 11,02 / 11,07 | 11,01 / 10,92 | 12,71 / 13,20 | 14,62 / 15,49 | 0,039 / 0,040 ms |

- **Quadro:** igual dentro do ruído.
- **Animação:** +0,003 ms por avaliação (o quatérnio do giro diagonal só roda quando `spinAxis` ≠ 0). Isso é menos de
  0,01 ms por quadro.
- **Pontilhado:** um `discard` por fragmento do herói, só quando a câmera está perto.
- **Inalterados:** draws (56 e 40), triângulos (416 560 e 338 912), malhas (142, 75,2 MB) e uma coleta de lixo por
  medição.
- **Memória:** 100–162 MB em uso nos dois binários (varia com o momento da coleta).
- **Câmera:** sem custo novo, porque não foi modificada.

## 9. Respostas explícitas

- **Principal causa da estranheza do rolamento:** o giro rígido no eixo sagital por cima do tronco dobrado, que punha a
  cabeça embaixo, com o contato prendendo a cabeça ao chão. Somava-se a proporção: com 2,4 cabeças, a cabeça é a
  superfície da "bola". O R1 atenua a coluna (13% mais baixa), mas não elimina a cabeça como contato. Ver §1.
- **R1 ou R2:**
  - para os lados com a mira travada, R2 claramente;
  - para a frente, R1 com ressalva (melhora parcial, ainda passa pela cabeça) ou `dodge_f`, que não inverte mas não é
    rolamento.

  Na entrega, o padrão técnico era R1+R2. **Escolha do proprietário: R2** (estado atual).
- **A solução preserva a sensação de esquiva?** Sim, nos números: mesma distância (1,9 u), duração (0,56 s),
  invulnerabilidade (0–0,32 s), recarga (0,22 s) e janelas gerais. A sensação em mãos só o teste físico confirma.
- **O personagem continua protegido durante a janela prevista?** Sim. A invulnerabilidade é lógica, em passos, e igual
  em todos os estilos (teste de paridade, ±2 passos).
- **As esquivas laterais ficaram coerentes?** Com a mira travada, sim: salto inclinado, rosto no alvo (desvio < 25°
  medido) e 1,9 u para o lado. Na mira livre os lados usavam o R1 virando o corpo para o rumo; hoje usam o `dodge_f` (R2) virando o corpo.
- **Preparação do salto:** agachamento visual nos primeiros 0,10 s do voo, por cima da física que já sobe.
- **Atraso perceptível no comando?** Não. A subida começa no passo do aperto.
- **Aterrissagem:** absorção proporcional ao impacto, mais curta e rasa correndo, sem frear a corrida; parado, os pés
  abrem.
- **Seguras com espada e escudo?** Sim. Validador, poses do jogo e acessórios, sem afrouxar tolerância.
- **Câmera:** permaneceu (números idênticos). Entrou só o pontilhado do herói perto da lente. Ver §6.
- **Perfil C igual?** Sim: andar 1,75 e correr 3,5 u/s, aceleração e tetos de golpe intocados (`MobilityTest` passa sem
  mudança).
- **Testes anteriores passam?** Os 208, sim; total 217.
- **Impacto no desempenho:** nenhum mensurável. Ver §8.
- **O que precisa de aprovação humana:**
  1. R1, R2 ou R1+R2 (ou manter a 09.2), pelos vídeos `A1`–`A3` e pelo teste físico, sabendo que o R1 ainda passa
     pela cabeça;
  2. se o salto com impulso "responde na hora" com teclado de verdade;
  3. se o pontilhado do herói perto da câmera ajuda ou incomoda;
  4. a câmera de luta nos casos difíceis (P37, P41, P42), que continua igual;
  5. timbre e volume dos sons (P39), que não mudaram.

## 10. Problemas remanescentes

- **P43, parcial:** o rolamento para a frente ainda passa pela cabeça por ~0,13 s (§1). A correção completa pede
  pescoço articulado no rig, fora do escopo.
- **P37, P41, P42 (câmera):** inalterados em posição; P41 e P42 mitigados pelo pontilhado.
- **P44:** sem golpe aéreo (escopo).
- **P46:** a sensação dos perfis depende do teste físico.
- **Prévia de aparência no roteiro 05:** o primeiro quadro ainda mostra o aventureiro antes de a composição assíncrona
  da Maga chegar (efeito do roteiro, que começa a gravar logo após o clique).
- **Movimento secundário** (cabelo do aventureiro, aljava): continua fundido, como antes.

Lista completa: `docs/validation/problemas-conhecidos.md`, agora com o título "rodada 09.3".

## 11. Documentação e skills

- **ADRs:** adendo do ADR 0018 (estilos, janelas por gesto, eixo diagonal, canais) e do ADR 0019 (apresentação do
  salto).
- **Arquitetura:** `combate.md` §20 e `jogo.md` §13.
- **Guia de animação:** seção "Esquiva e salto".
- **Validação:** problemas conhecidos, roteiro de teste físico §7.1, `CLAUDE.md` e `README`.
- **Skills:** `combat`, `gameplay`, `weapon-animation` e `visual-qa`. A `weapon-animation` ganhou as receitas de esquiva
  autorada, contato com o chão, pivôs, variantes direcionais, preservação da física, antecipação e pouso, acessórios,
  comparação e clipping nas transições.
- Nenhuma skill nova.

## 12. Recomendação

O combate da 09.2 continua igual em função. A 09.3 trocou a forma da esquiva e do salto, com prova em teste,
validador, pranchas e vídeo.

Depois do teste físico e da escolha do estilo de esquiva, recomendo encerrar o polimento isolado do combate e seguir
para a primeira aventura completa. A câmera nos casos encurralados é o único ponto que merece voltar, e só se o teste
físico apontar.
