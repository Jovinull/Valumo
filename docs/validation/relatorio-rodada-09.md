# Relatório — Rodada 09: primeiro combate funcional

**Pacote local** (fora do git): `output/visual-review/round-09/` (`LEIA-ME.md`) e o zip
`output/visual-review/rodada-09-imagens-e-documentos.zip` (sem os MP4). Regenerável com
`tools/review9.sh tudo`.

**Documentos:**
- sistema: [combate.md](../architecture/combate.md);
- decisão: [ADR 0015](../decisions/0015-combate-por-trajetoria-e-passo-fixo.md);
- teste físico: [roteiro-de-teste-fisico-combate.md](roteiro-de-teste-fisico-combate.md).

**Sem commit nem push nesta rodada.** A tag `playable-foundation-v0.1` não foi tocada.

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo                     # escolher a aparência → vila → descer a trilha para o sul → arena da pedra grande
./gradlew jogo -Ptecnico           # idem; F3 mostra hitboxes, hurtboxes e estados
./gradlew editor                   # criador (sem mudanças)
./gradlew test                     # testes
viewer/build/install/viewer/bin/viewer --check                    # golpes, reações, saídas, derrota
viewer/build/install/viewer/bin/viewer --check interrupcao-fina   # cortes a cada 0,01 s nos golpes jogáveis
viewer/build/install/viewer/bin/viewer --occlusion
tools/review7.sh gate              # presets idênticos ao canônico
tools/review9.sh tudo              # evidências: capturas, hitboxes, câmera, vídeos, 4 ângulos, interrupções,
                                   # validadores, regressão, desempenho, testes, zip
```

## 1. Linha de base (auditoria)

- **Git:**
  - raiz do repositório local, `main` em sincronia com `origin/main` (`24e83df`), tag
    `playable-foundation-v0.1`;
  - remoto `Jovinull/Valumo`;
  - árvore limpa.
- **Validação:**
  - **124/124** testes (o número da 08.1 confirmou);
  - `--check` TODOS OK;
  - oclusão só com a exceção conhecida;
  - 22 referências a 0/255.
- **Interrupção** (passo 0,05 s):
  - espada **14/21** seguros (7 inseguros);
  - lâmina longa **29/47** (18 inseguros).
- **Registro:** `round-09/linha-de-base/`.

## 2. O que foi implementado

Detalhes em [combate.md](../architecture/combate.md).
- **Arena:** na borda sul da vila, alcançada pela trilha; o limite jogável é a união da vila com a arena. Obstáculos
  postos à mão: pedra grande, carvalho, toco, tronco caído e cogumelos.
- **Herói:** espada e broquel em qualquer aparência (kit do compositor; os três corpos passam no validador).
  - Corte horizontal; encadeia o diagonal e o descendente.
  - Guarda frontal.
  - Reação a dano com atordoamento e invulnerabilidade.
  - Derrota (ajoelha) com tela de recomeço.
- **Inimigo:** o fungo-cardo, uma variante de combate do fungo canônico, que continua idêntico.
  - Máquina de estados: repouso → alerta → persegue → prepara → golpe → recupera, mais atingido, morto e volta.
  - Percepção por distância, campo e linha de visão.
  - Coleira.
  - Contorno de obstáculo.
  - Cabeçada telegrafada.
- **Dano pela trajetória:** a hitbox da lâmina e a do chapéu são avaliadas pela animação em subamostras de 1/480 s,
  separadas da hurtbox e da colisão.
- **Interrupções:** janelas autoradas por fase no perfil, com saídas autoradas. O jogo e o validador usam a mesma
  semântica.
- **Passo fixo:** 1/120 s para exploração, combate e câmera, com o desenho interpolado.
- **Retorno visual:** pausa de impacto, lampejo, acento de pixel, recuo e vinheta. Sem som (seção 12).
- **Interface:** corações, broquel aceso na guarda, barra do inimigo, dica de controles, vitória e derrota.
- **Câmera:** ajuste de combate (não é trava de mira).

## 3. O inimigo escolhido

- **Variante, não substituição:** o fungo canônico era manso, e transformá-lo em inimigo mudaria a função dele na vila.
  Por isso a rodada criou uma variante, `fungo_cardo` em `tools/creatures_e.py`.
- **Quatro mudanças**, todas para ler ameaça a 4–6 u:
  - chapéu carmim escuro;
  - coroa de 5 espinhos;
  - olhos em fenda inclinada;
  - mãos de nó de espinhos.
- **Mantém:** proporção, corpo, pés e boca do canônico.
- **Conferências:**
  - o hash do fungo canônico é o mesmo;
  - auditoria: os braços têm o mesmo fragmento diagonal do canônico, sem defeito novo.
- **Animação própria (`brawler`):** alerta, preparação com tremor, cabeçada, recuperação cambaleante, recuo e queda.

## 4. IA (resumo)

| Regra | Valor |
|---|---|
| vê | até 5,2 u, campo de 140° (ou até 1,6 u em qualquer direção), sem sólido na linha de visão |
| desiste | 2,5 s sem ver ou herói fora da coleira (4,4 u de casa); volta e recupera a vida |
| persegue | 1,15 u/s, anda para onde olha, giro ≤ 4,5 rad/s |
| ataca | a ≤ 1,05 u e ≤ 25° de frente: prepara 0,70 s (mira só nos primeiros 60%), golpe 0,26 s com avanço, recupera 0,85 s |
| cambaleia | 0,45 s; não na cabeçada (compromete); imune 0,8 s depois; espera o intervalo antes de atacar de novo |
| obstáculo | sem progresso 0,7 s: contorna pelo lado livre; preso 3 vezes: desiste (não há busca de caminho) |

## 5. Como os golpes causam dano

1. **A cada passo de 1/120 s**, se o golpe está na sua **janela de dano** (`ativo`):
   - **onde:** dentro da execução do perfil, só com a ponta na metade da frente;
   - **como:** a pose do herói vem da própria animação em 4 subamostras (1/480 s), com a raiz interpolada no passo.
2. **Teste da lâmina:**
   - a cápsula ao longo da peça `weapon` (do fim do punho à ponta, raio 0,05 u) e o rastro da ponta entre subamostras
     são testados contra a cápsula da hurtbox do inimigo;
   - a ponta anda até cerca de 0,1 u por subamostra, menos que o raio da hurtbox; o teste com alvo de 2 cm mostra que
     nada é pulado.
3. **Contato:** precisa de nada sólido entre o peito do herói e o ponto de contato.
4. **Um acerto por golpe por alvo.**
5. **Danos:** horizontal e diagonal tiram 1, o descendente tira 2.
6. **Cabeçada:** usa a mesma regra, com a esfera presa ao chapéu, avaliada pela animação do fungo.

**Medido** (`CombatTest`, `CombatAnimationTest`):
- não fere fora do alcance, atrás nem ao lado;
- não fere fora da janela;
- não atravessa a pedra;
- não conta duas vezes;
- não depende da taxa de quadros nem da janela.

## 6. Escudo

- **Bloqueia:** só de frente (±65°), só com a guarda erguida (0,12 s de subida) e em guarda.
- **Bloqueado:** sem dano, recuo curto, impacto no broquel e o fungo ricocheteia (recuperação de 1,25 s).
- **Não bloqueia:** pelas costas nem erguido tarde demais (testes).
- **Em guarda:** anda a 45% e vira para onde a câmera olha.
- **Sem extras:** não há aparar nem vigor.

## 7. Reações a impacto

**Herói:**
- reação coreografada (ou a saída autorada da fase);
- recuo de 2,2 u/s;
- lampejo de 0,14 s, pausa de 0,05 s e vinheta curta;
- atordoamento de 0,42 s, ou 0,60/0,70 s se a reação for a saída autorada;
- invulnerável por 0,75 s.

**Fungo:**
- recuo e chapéu chacoalhando;
- lampejo e pausa de 0,07 s;
- acento de pixel no ponto de contato.

**Sem tremor de câmera.** O primeiro lampejo (mistura para branco) lavava o herói inteiro nas capturas; foi trocado por
um clareamento quente de até 35%.

## 8. A interrupção da espada

| | Antes (08.1) | Depois (09) |
|---|---|---|
| espada, passo 0,05 s (`--check interrupcao`) | 14/21 seguros | **21/21** |
| espada e broquel, passo 0,01 s, todos os golpes jogáveis (`--check interrupcao-fina`) | 7 inseguros só no horizontal | **0 inseguros** em `slash_h`, `slash_d`, `slash_v`, `shield_block`, `guard_hold` |
| transições produzidas pelo jogo (encadear, dano no encadeamento, liberar, agir depois da reação, segundo dano, derrota) | não existiam | **todas válidas** (`CombatAnimationTest`) |

**Como:**
- a regra de corte é autorada por fase no perfil: rebobina na preparação e na execução, segue em frente na recuperação;
- logo após a execução do horizontal e do diagonal, quando nenhuma das duas serve, entra uma **saída autorada**
  (`hit_cross_h`, `hit_cross_d`): o corpo recua com a espada do lado onde o corte terminou e volta pela recuperação
  autorada;
- os parâmetros saíram de busca com as duas exigências do validador.

**Instantes medidos pelo validador**, nunca chutados:
- encadear: h → d a partir de 0,62 s (usado 0,64); d → v a partir de 0,58 s;
- liberar: h 0,78, d 0,92, v 0,62 (usados com folga);
- agir depois da saída: 0,56 e 0,66 s (atordoamento de 0,60 e 0,70 s).

**Regras de interrupção** (explícitas e testadas):
- dano sempre interrompe (o herói não tem armadura);
- golpe só cancela golpe pelo encadeamento ou depois de `libera`;
- a guarda não cancela golpe;
- dentro da invulnerabilidade não há corte novo, então nunca há três transições sobrepostas.

**Evidência:**
- `round-09/interrupcoes/` (relatórios do validador);
- `round-09/angulos/` (sequência, dano após o corte h e d, dano no encadeamento, guarda, derrota, em 4 ângulos);
- vídeo `06-interrupcao` (visão técnica).

## 9. A lâmina longa continua pendente

**Sim.** Não é jogável nesta rodada e continua com **18/47** cortes inseguros: o mesmo número, fixado por
`AnimationQaTest.longBladeKeepsItsHistoricalInterruptionRecordUntilItBecomesPlayable`.

Para habilitá-la, ela precisa das mesmas janelas e saídas da espada.

## 10. Defeitos encontrados e corrigidos nesta rodada

| Defeito | Como apareceu | Correção |
|---|---|---|
| janela de dano = execução inteira feria quem estava atrás e ao lado | teste "atrás do herói" | janela autorada por golpe, só com a ponta na frente (teste na animação) |
| encadear h → d aos 0,60 s cruzava a lâmina na frente da cabeça | `CombatAnimationTest` | instante medido (0,64) |
| liberar o diagonal aos 0,82 s passava o cabo pelo tronco | idem | 0,94 |
| reação cortada por golpe ou guarda rebobinava e atravessava cabeça e tronco | idem | reações seguem em frente; atordoamento ≥ instante seguro |
| saltos de pose ao pular dentro da guarda | teste de platôs | `guard_hold` com esperas e pulos só em platôs |
| segundo corte durante uma transição saltava a pose | análise do `Sequencer` | origem dupla (`fromMove2`) no `Animator`, `Sequencer` e jogo |
| o desenho suavizava a pose do herói por cima (deformaria trajetórias validadas) | revisão | herói desenhado com a pose exata |
| fim natural de golpe adiantado meio passo | teste camada × `Sequencer` | o tempo além do fim conta na saída |
| luta mudava com o FPS: a direção vinha da câmera suavizada por quadro | teste de FPS | câmera no passo fixo, desenho interpolado |
| fungo escondido atrás do herói na câmera de exploração | capturas | ajuste de combate da câmera (giro pela linha de vista) |
| lampejo lavava o herói de branco | capturas | clareamento quente sutil |
| alvo da câmera de combate entrava na pedra | teste da câmera | deslocamento limitado pela linha livre |
| custo da câmera dobrou no passo fixo (p95 da lógica 2,07 → 3,82 ms) | medição | busca de desvio a 60 Hz de tempo de jogo (2,25 ms) |
| a sequência de três cortes nunca completava contra o fungo (imunidade longa + ataque logo depois de cambalear) | vídeos | intervalo depois de cambalear e imunidade de 0,8 s: a sequência completa cabe depois de uma defesa |
| `CreatorTest` intermitente (08.1) | análise | contagem de descartes da fila + invariante sem tempo |
| a 1ª e a 2ª versões dessa correção contavam em dobro sob carga (corrida com a thread de composição) | a própria invariante, sob carga total (1 falha em 8) | contar só o que `queue.remove` confirma (atômico) |
| peças de arco órfãs ao vestir o kit na exploradora | teste do kit nos três corpos | remove também o que pende dos itens |
| `review72.sh` sobrescreveria a entrega da 07.2 | revisão | pasta configurável (`R=`) |

## 11. Testes adicionados

**Total:** 156 testes, 0 falhas. Eram 124 na linha de base.

| Classe | Testes novos | O que cobre |
|---|---|---|
| `CombatTest` | 21 | janela de dano e acerto único; alcance, atrás e ao lado; alvo fino entre quadros; pedra barra o golpe; encadeamento e buffer; percepção (frente, costas, audição, pedra); telegrafia e esquiva para trás; esquiva lateral; coleira e volta curado; obstáculo; escudo (frente, costas, tarde); reação, atordoamento e invulnerabilidade; saída autorada; morte e recomeço (inimigo, herói); FPS; conversa × luta; moradoras; câmera de combate (separa e solta; não piora obstáculos) |
| `CombatAnimationTest` | 8 | encadeamentos e dano no encadeamento; liberações; depois da reação, segundo dano e derrota; platôs da guarda; janelas só na frente; kit nos três corpos; camada ao vivo = `Sequencer` |
| `AnimationQaTest` | 3 | golpes jogáveis seguros a cada 0,01 s; escolha da saída; lâmina longa histórica |
| `CreatorTest` | 1 asserção | invariante de descartes (sem tempo) |

**Sob carga** (`round-09/testes/creator-sob-carga.txt`): o `CreatorTest` corrigido passou **20/20** com todos os núcleos
ocupados e nada compilando em paralelo. As duas primeiras versões da correção falharam 1 vez cada nessas condições;
foi assim que a contagem dupla por corrida apareceu.

## 12. Desempenho

Medido em 1920×1080, qualidade padrão, Iris Xe, aquecido (7 s), 60 quadros/s de simulação, `glFinish` por quadro.
Linha de base 08.1: exploração com mediana 9,40, p95 11,51, 478 mil triângulos, 74,6 MB e 94 draws.

| | Exploração 08.1 | Exploração 09 | **Combate 09** |
|---|---|---|---|
| quadro: mediana / p95 / p99 / 1% pior | 9,40 / 11,51 / 12,37 / 13,37 | 10,09 / 12,12 / 13,14 / 13,82 | **9,59 / 11,05 / 12,15 / 12,36** |
| lógica: mediana / p95 | 0,30 / 2,07 | 0,44 / 2,25 | 0,62 / 1,19 |
| desenho: mediana / p95 | 9,05 / 10,26 | 9,58 / 10,75 | 8,94 / 10,16 |
| draws / triângulos | 94 / 478 000 | 95 / 478 680 | 87 / 481 624 |
| memória de vértices | 74,6 MB | 75,2 MB | 75,2 MB |
| quadros > 20 ms | 0 | 0 | 0 |

**Custo do combate** (por passo lógico de 1/120 s):

| Parte | Custo |
|---|---|
| herói (estado + corpo + lâmina) | 0,036 ms |
| inimigo (IA + corpo + cabeçada) | 0,034 ms |
| só a detecção de golpe | 0,019 ms |
| animação no desenho | 0,034 ms por avaliação |
| coleta de lixo durante a luta | 0 |

**Leitura:**
- **Combate:** custa menos de 0,15 ms por quadro a 60 FPS.
- **Exploração:** ficou cerca de 0,6 ms mais lenta na mediana.
  - **A arena nova** (9 props: +4 draws, +6,8 mil triângulos de cena, +10,8 mil de sombra) explica cerca de 0,4 ms.
    Confirmado pelo bench do viewer na mesma cena, sem lógica: mediana de 9,90 para 10,28 ms.
  - **O passo fixo** (câmera e corpo a 120 Hz) explica o resto. A busca de desvio da câmera foi limitada a 60 Hz de
    tempo de jogo depois de a medição mostrar que dobrava.
  - Variação entre execuções: ±0,15 ms.
- **A malha conforme não foi reaberta.**

## 13. Regressões

| Verificação | Resultado |
|---|---|
| Testes | 156 / 0 falhas |
| `--check` | TODOS OK (inclui `hit_cross_h`, `hit_cross_d`, `guard_hold`, `defeat`) |
| `--occlusion` | só a exceção conhecida (`bow_shot`), igual à linha de base |
| Gate dos presets | 51 pares, 0 com diferença |
| 22 referências canônicas | 22/22 a 0/255 (o lampejo novo no shader não muda nada com valor 0) |
| Criador (capturas da 07.2) | 33/33 idênticas ao código publicado da 08.1 (clone limpo de `24e83df`); contra as capturas da 07.2 todas diferem nas bordas da cena 3D de fundo, efeito da malha conforme da 08.1 (já aprovada), não desta rodada |
| Geração determinística | 1.073 arquivos, hash idêntico em duas gerações |
| Auditoria de assets | heróis (51) e NPCs (64) sem fragmentos; criaturas 10 arquivos marcados = os 8 da linha de base (fungo, pompom, tortuga) + os 2 braços do fungo-cardo (mesmo fragmento diagonal herdado do canônico) |
| Exploração e conversa da 08 | `GameplayTest` 21/21 + capturas dos roteiros da 08 em `round-09/regressao/jogo-08/` |
| Fungo canônico | hash idêntico (a variante é outro asset) |

## 14. O que falta validar fisicamente

O roteiro de 10–15 minutos ([roteiro-de-teste-fisico-combate.md](roteiro-de-teste-fisico-combate.md)) cobre:
- mouse e teclado reais (os roteiros injetam na mesma fila da janela; Wayland impede automação do sistema);
- a latência sentida do clique;
- o ritmo dos golpes e da sequência;
- se a preparação é justa;
- se a defesa é útil sem ser trivial;
- se a câmera ajuda perto da pedra e do carvalho;
- a dificuldade.

**Divertido ou não é decisão do proprietário.** O que a rodada garante é que é tecnicamente correto, legível nos vídeos
e sem recorte nas transições.

## 15. Problemas que continuam

- **Sem som** (golpe, defesa, dano). Exige o módulo OpenAL do LWJGL, um ADR e uma direção de áudio.
- **Lâmina longa:** interrupção 18/47; não jogável.
- **Câmera colada num obstáculo:** por alguns quadros, a câmera entra nele. É limite da rodada 08: o ajuste de combate
  não piora e, no carvalho, melhora.
- **Sem busca de caminho:** o inimigo contorna ou desiste.
- **Clique em taxa baixa:** a menos de 30 FPS o clique é lido até um quadro depois (regra igual; chegada diferente).
- **Exploradora e maga** usam a trajetória lógica do rig de referência do kit: mesmos pivôs de braço e quadril; joelho
  da exploradora 1 voxel acima.
- **Demonstrações:** os roteiros usam montagem técnica (`posicionar`, `inimigo`, `vida-heroi`) para começar cada vídeo
  no ponto que demonstra. A luta em si é só entrada de jogo.
- **A interface de combate é mínima:** corações e barra, sem números.

## 16. Pronto para aprofundar?

**Tecnicamente, sim.**
- A base é a que um aprofundamento precisa:
  - passo fixo determinístico;
  - dano pela trajetória;
  - interrupções autoradas;
  - instantes medidos;
  - a mesma semântica de animação no jogo e no validador.
- O roteiro para adicionar um golpe ou um inimigo está na skill `combat`.

**Antes de aprofundar:**
1. o teste físico do proprietário (sensação, ritmo e dificuldade);
2. a decisão sobre som;
3. só então, um segundo inimigo ou um segundo golpe.

A rodada 10 não foi iniciada.
