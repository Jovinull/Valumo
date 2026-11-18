# Relatório — rodada 09.2: mobilidade, esquiva, salto, mira opcional, grupos e morte voxel

**Entregue sem commit nem push.** A tag `playable-foundation-v0.1` não foi tocada e a rodada 10 não foi iniciada.
Ninguém além do proprietário testou com as mãos: as afirmações abaixo são medidas, testes e vídeos reais da aplicação,
não julgamento de diversão.

- **Pacote:** `output/visual-review/round-09-2/`, com `LEIA-ME.md` e o zip
  `output/visual-review/rodada-09-2-imagens-e-documentos.zip`, só com imagens e documentos.
- **Teste físico:** [roteiro-de-teste-fisico-combate.md](roteiro-de-teste-fisico-combate.md).

## 1. Pesquisa (antes de escrever código)

Fontes consultadas (abertas na rodada):
- Engadget, *Block by block: hands-on with the Cube World alpha* (2013);
- Kotaku, *Tips for playing the Cube World alpha*;
- resultados de busca sobre os controles do Cube World (versões divergem entre si);
- Prima Games e fóruns sobre a mira travada do Dark Souls III;
- critpoints.net, *Lock-on styles in action games* (2015).

As páginas da wiki do Cube World (Controls, Warrior) e o guia da Prima recusaram acesso automático (HTTP 402/403); o
que se diz delas veio dos resumos da busca.

| Tema | Fato nas fontes | Interpretação de design (minha, para o Valumo) |
|---|---|---|
| Cube World: ataque | "combate ativo e simples"; o ataque vai para onde o mouse aponta (Engadget) | o golpe precisa sair na hora e para onde o jogador quer; mira livre como padrão |
| Cube World: esquiva | rolar com o botão do meio, na direção do WASD; tira do caminho de golpes e projéteis (Engadget, Kotaku, guias) | esquiva direcional com o meio do mouse como alternativa; função física real |
| Cube World: custo | guias dizem que a esquiva gasta stamina e dá imunidade breve (versões posteriores; a alfa de 2013 não detalha) | não copiar stamina: recarga curta + janela limitada bastam para o ritmo do Valumo |
| Cube World: grupos | "grupos de inimigos podem te derrubar no começo" (Kotaku) | grupos pequenos e legíveis: nem todos atacam juntos |
| Dark Souls III: mira | clicar o analógico trava/solta; analógico para os lados troca de alvo (Prima); a trava cai por distância e alguns ataques a soltam (relatos de jogadores) | Q trava/solta; Tab troca pela ordem na tela; solta sozinha por morte, distância e alvo escondido |
| Estilos de mira (critpoints) | trava rígida dá controle direcional e alvo claro, mas pode reduzir o combate a distância; sem trava é flexível, mas perde-se o alvo com vários inimigos; a câmera presa pode empurrar o jogador para paredes | mira travada OPCIONAL; nunca presa por padrão; marcador claro; o mouse continua mandando na livre |

**Nada copiado:** nenhum número, tempo, animação, interface ou código dessas referências virou parâmetro. Todos os
números do Valumo foram medidos aqui (§3–7).

## 2. Por que o combate antigo parecia travado

| Medida (09.1, perfil A) | Valor |
|---|---|
| Velocidade de andar / correr | 1,14 / 2,33 u/s |
| Deslocamento durante um corte com o jogador empurrando (1,12 s) | **0,54 u** (só o avanço autorado + o embalo freando) |
| Controle de direção no golpe | só na preparação (giro de 12 rad/s); execução e recuperação: zero |
| Esquiva | não existia (só andar com Shift) |
| Salto | não existia |
| Câmera com o fungo colado | subia até 0,9 rad (≈ 52°) e girava 35° sozinha |

O golpe era uma animação que prendia o corpo do começo ao fim, e a única saída do perigo era andar devagar.

## 3. Movimento durante os golpes (ADR 0017)

- **Fases:** teto de velocidade e giro em cada fase (preparação, execução, continuação, recuperação), vindos do perfil
  de sensação.
- **Pernas:** seguem a passada real por baixo do golpe (`Motion.legW`). Tronco, braços, quadril e espada continuam o
  golpe validado.
- **Rumo da passada:** de lado e de costas.
- **Lâmina:** avaliada com as mesmas pernas do desenho.

| Perfil | andar / correr | deslocamento num corte empurrando | de lado | recuando (travado) |
|---|---|---|---|---|
| A (09.1) | 1,14 / 2,33 | 0,54 u | 0,54 u | 0,54 u |
| B | 1,45 / 2,90 | 1,23 u | — | — |
| **C (padrão)** | 1,75 / 3,50 | **1,63 u** | 1,63 u | 1,63 u |

- **Pé de apoio no golpe andando (perfil C):** mediana de **0,001 u/s** com a passada, contra **1,34 u/s** se a base do
  golpe deslizasse (`MobilityTest`).
- **Validador:** `viewer --check golpe-andando` passa cada golpe jogável, a guarda e o bloqueio por cima de andar e
  correr, em 4 rumos × 4 fases, nos dois kits: **0 problemas**.
- **Interrupções da espada:** `interrupcao-fina` continua segura.

**Por que C como padrão:**
- é o único que mantém a ação em movimento contínuo (1,63 u por corte, quase a velocidade de andar);
- pela vista lateral, o tronco e a espada continuam os do golpe e as pernas dão passos de verdade;
- B é o meio-termo.

A escolha final é do teste físico: os três estão na pausa → **Sensação**.

## 4. Esquiva (ADR 0018)

| | Rolamento (com direção) | Recuo (sem direção) |
|---|---|---|
| deslocamento real | 1,9 u (teste: ± 0,25 u, em 5 direções) | 0,95 u |
| invulnerável | 0–0,32 s de 0,56 s | 0–0,20 s de 0,40 s |
| golpe / andar ou guarda a partir de | 0,44 / 0,46 s | 0,26 / 0,30 s |

- **Invulnerabilidade:** é estado lógico em passos de 1/120 s; a parede continua parede (teste).
- **Custo:** recarga de 0,22 s; apertando sem parar, menos de 45% do tempo invulnerável (teste).
- **Janelas medidas** (`viewer --check esquiva`, a cada 0,01 s, nos dois kits): de cada corte só se esquiva onde cortar
  para rolar E para recuar é seguro. Os números estão em `combate.md` §15; a execução fica sempre de fora; da guarda, a
  qualquer momento. Resultado: **segura**.
- **Achado do validador:** com a transição de corte padrão (0,30 s), a mistura golpe × bola atravessava o punho no tronco
  em quase todos os instantes. A esquiva passou a entrar em 0,12 s (`cutFade`), o que também a deixa mais seca.
- **Animação:**
  - mergulho, bola e levantar;
  - canal `spin` (o giro desconta a inclinação do tronco);
  - espada ao lado, no eixo do giro;
  - o ponto mais baixo dos volumes do rig fica no chão: a cabeça grande não afunda (antes da correção ela descia 2,6
    voxels abaixo do chão).
- **Prancha:** `animacao/rolamento.png`.

## 5. Salto (ADR 0019)

- **Física:**
  - altura de 0,62 u (teste ± 0,04);
  - tempo no ar igual ao da conta das duas gravidades (± 0,04 s);
  - coyote de 0,09 s e buffer de 0,12 s (teste);
  - controle no ar de 55%.
- **Plataformas:** sobe em props `pisavel` com topo ao alcance (pedra pequena 0,63 u, caixote e barril 0,81 u). Não sobe
  no toco nem no tronco caído (1,0 e 1,25 u: alto demais), nem na casa (vídeo 11).
- **Golpe no ar:** não existe nesta rodada; o clique no ar sai no pouso (teste). É decisão de qualidade, não pendência
  escondida.

## 6. Câmera: livre e travada (ADR 0020)

Medida nos mesmos 10 cenários em movimento da 09.1 (`CameraModesTest`, perfil C). "Ruim" = herói em mais de 20% da tela
ou fungo menos de 50% visível.

| Câmera | Tempo ruim | Câmera dentro de algo | Distância média |
|---|---|---|---|
| 09.1, ajuste automático (até 52°) | 11,2% | 2 quadros | 4,31 u |
| **travada** | **19,3%** | 4 quadros | 3,95 u |
| livre, jogador centralizando o fungo na tela | 37,2% | 56 quadros | 3,76 u |
| livre, herói e fungo alinhados (fungo atrás da cabeça) | 58,6% | 89 quadros | 3,92 u |

**Primeira versão da travada:**
- **Problema:** 66,8% do tempo ruim e 676 quadros dentro de obstáculo. Prender o rumo atrás do herói empurrava a câmera
  para dentro da pedra e do toco, e 16° de lado não tiravam o fungo colado de trás da cabeça.
- **Refeita:**
  - busca de vista da luta;
  - lado de 35° → 22° pela distância;
  - inclinação de 0,50/0,70 rad, escolhida por varredura. 0,42/0,62 dava 22% e 96 quadros dentro.

**Mira livre:**
- **Controle:** sem giro nem subida automáticos (teste com 6 s de luta e o mouse parado: rumo intacto, `combatYaw` 0).
- **Composição:** ombro de 0,65 u na luta.
- **Preço:** com o fungo colado atrás da cabeça ele some até o jogador mexer o mouse (P41).
- **Referência do WASD:** `inputYaw`, com no máximo 2,5 rad/s de desvio automático. Soltar a mira não vira o controle de
  uma vez (teste: menos de 0,06 rad por quadro). Resolve o P38 da 09.1.

## 7. Vários inimigos, ensaio de chefe (ADR 0021)

- **Arquitetura:** `Combat.enemies`, a mesma classe para todos, com números por instância.
- **Encontros:** `um`, `grupo` (3) e `chefe` (ancião protótipo × 1,6 + 2).
- **Golpe legítimo em dois:** dois fungos à frente, um de cada lado, e um atrás → **2 acertos, o de trás intacto**
  (teste e vídeo 17). O corte horizontal vai da direita para a esquerda; mirando o da direita, a janela de dano acaba
  antes de alcançar o da esquerda. É geometria, não regra.
- **Grupo:** em 14 s de luta, no máximo 2 atacando juntos, ≥ 0,5 s entre preparações e corpos nunca sobrepostos
  (teste). O terceiro espera no anel.
- **Chefe:** barra grande só para ele; a mira trava nele, solta, rola, trava num menor, Tab volta (vídeo 18).

## 8. Morte (ADR 0022)

- **D2 (padrão):** os próprios blocos (≤ 150), com a cor do modelo, soltos numa onda a partir do golpe; caem, quicam
  baixo e somem encolhendo em 1,35 s, com até 7 sementes de luz.
- **D1:** só os blocos.
- **Antiga:** `-Dvx.morte=antiga`.
- **Testes:** cor existente no modelo; nasce dentro do corpo; nunca atravessa o chão; espalha menos de 2,2 u; termina
  sem nada; determinístico; 4 mortes juntas em menos de 0,5 ms de lógica por quadro.
- **Desenho:** uma malha dinâmica, um desenho por quadro.
- **Vídeo:** `videos/19-morte-lado-a-lado.mp4` (antiga × D1 × D2, mesmo golpe final).

## 9. Barras

- **Comum:** pequena sobre o inimigo, só ao levar dano (3 s) ou estando travado (padrão). Preferência: nunca / dano /
  sempre, gravada fora da aparência.
- **Chefe:** grande no topo, com moldura e marca "protótipo de teste".
- **Marcador da mira:** cantoneiras fora do contorno e um losango no chão (capturas 02, 05, 09).

## 10. Som

- **Reaproveitado:** todo o catálogo da 09.1 (sem mudança de arquivo nem de ganho).
- **Novos, sintetizados na mesma linguagem:**
  - `esquiva_rolar`, `esquiva_recuo`, `salto`, `pouso`, `fungo_desfaz`;
  - todos com pico de −3 dBFS, 0 amostras cortadas e mistura por alvo de nível.
- **Diretor:** guarda o estado por inimigo.
- **Gravação:** vídeos com o som gravado pela aplicação (loopback). Nenhum teste tocou nos alto-falantes nesta rodada.

## 11. Testes e validadores

- **Testes: 208, 0 falhas.** São os 176 da 09.1 mais 32 novos:
  - `MobilityTest`, 15;
  - `GroupCombatTest`, 16;
  - `CameraModesTest`, 1.

  Lista por classe em `round-09-2/testes.txt`.
- **Testes da 09.1 alterados**, explicando cada mudança (nenhum afrouxado):
  - os 7 de `CombatCameraTest` e o de câmera do `CombatTest` rodam explicitamente no modo de comparação 09.1, porque o
    ajuste automático deixou de ser o padrão;
  - o de passos calcula o ritmo pelo perfil, porque a velocidade mudou;
  - o de "fungo morto em silêncio" aceita exatamente um `fungo_desfaz`, no instante da desagregação.

| Validador | Resultado |
|---|---|
| `viewer --check` | TODOS OK |
| `viewer --check interrupcao-fina` | golpes jogáveis seguros (0 cortes inseguros a cada 0,01 s) |
| `viewer --check golpe-andando` (novo) | tudo seguro: 3 cortes + guarda + bloqueio × andar/correr × 4 rumos × 4 fases × 2 kits |
| `viewer --check esquiva` (novo) | segura: janelas medidas × rolar/recuar × 2 kits, saídas nos instantes do jogo |
| gate dos presets (`review7.sh gate`) | 51 pares, 0 com diferença |
| `viewer --occlusion` | igual à 09.1 (o aviso final de rosto coberto já existia); rolamento e recuo entram sem rosto coberto |

## 12. Desempenho

Medido em `round-09-2/desempenho/medicao.txt`: 1920×1080, janela oculta, 60 quadros/s de simulação, GPU integrada,
duas repetições, depois do aquecimento.

- **Roteiro da 09.2:** o mesmo nos três encontros: golpes andando, esquiva, salto, mira travada, troca de alvo; vida 2
  nos inimigos para as mortes entrarem na medição.
- **Binário da 09.1:** só pode fazer o equivalente sem esquiva, salto nem mira. As cenas não são idênticas, e por isso a
  comparação é indicativa.

| | mediana | p95 | 1% pior | máximo | IA por inimigo | câmera | morte (lógica / malha) |
|---|---|---|---|---|---|---|---|
| 09.1, um fungo | 8,75–8,79 ms | 10,06–10,15 | 11,4–12,1 | 12,1–14,6 | — | — | — |
| 09.2, um fungo | 9,38–9,39 | 10,71–11,02 | 13,3–14,1 | 19,8–20,1 | 0,028 ms/passo | 0,034–0,036 | 0,10–0,15 / 0,54–0,60 ms/quadro, até 150 blocos |
| 09.2, três fungos | 9,31–9,39 | 10,79–10,83 | 13,3–14,4 | 15,7–19,4 | 0,022 | 0,037–0,040 | 0,12–0,13 / 0,71–0,75, até 300 |
| 09.2, chefe + 2 | 9,58–9,69 | 11,07–11,09 | 14,1–16,4 | 16,8–20,9 | 0,030 | 0,039 | 0,10 / 0,68, até 300 |

- **Custo extra:** cerca de 0,6–0,9 ms de mediana.
  - Mais personagens animados.
  - A passada sob o golpe.
  - A morte: lógica dos blocos mais montagem da malha, só enquanto há blocos.
  - A câmera de luta (busca de vista).
- **Desenho:** a morte é um único desenho por quadro, qualquer que seja o número de mortes. Draws de 44–51 e triângulos
  na casa de 345–356 mil, praticamente iguais.
- **Pico corrigido:** a primeira versão carregava os voxels do fungo na primeira morte e dava picos de 28–33 ms. Agora
  são pré-carregados na criação do jogo.
- **Coleta de lixo:** uma coleta curta (3–11 ms) em 3 das 8 medições da 09.2.
- **Lógica:** o combate continua no passo fixo, fora da thread gráfica.

## 13. Regressões

- **Presets:** 51/51 idênticos ao canônico (gate pixel a pixel). O `Animator` novo (passada, rumo, ar, rolamento) é
  idêntico ao anterior com os valores padrão.
- **Golpes, guarda e interrupções:** sem mudança em `assets/anim` para os golpes existentes; validadores verdes.
- **Exploração:**
  - **Velocidade e câmera:** a velocidade mudou de propósito (perfil C). A câmera fora da luta é a da rodada 08: o
    teste passo a passo `explorationCameraIsUnchanged` continua passando.
  - **Defeitos da exploração vistos nos vídeos:** a câmera encosta perto de muros na vila (vídeo 11, primeira versão).
    É o comportamento da 08 (piso de 0,9 u), não muda nesta rodada e já estava registrado como limitação aceita.
- **Diálogo, criador, presets, referências e malha conforme:** sem mudança de código.
- **Som:** os 33 arquivos e a mistura da 09.1 iguais; `CombatAudioTest` (13) passa.
- **Regressões encontradas e corrigidas durante a rodada:**
  - mistura da esquiva atravessando o punho no tronco (corrigido com `cutFade`);
  - cabeça afundando no chão no rolamento (contato pelos volumes);
  - câmera travada dentro de obstáculos (66,8% → 19,3%);
  - câmera livre com o olho dentro da pedra grande;
  - pico na primeira morte;
  - aperto de salto no ar descartado (buffer e coyote quebrados);
  - marca de protótipo ilegível;
  - três fungos colando no herói ao mesmo tempo (vez por distância).

## 14. Problemas abertos

Ver [problemas-conhecidos.md](problemas-conhecidos.md):
- P37 (travada junto a obstáculos);
- P41 (livre com o fungo atrás da cabeça);
- P42 (livre encurralada na encosta);
- P43 (um rolamento só);
- P44 (sem golpe aéreo);
- P45 (IA de grupo simples, chefe protótipo);
- P46 (perfil padrão escolhido por medida, não pelo proprietário);
- e os do som (P39, P40).

## 15. Avaliação crítica

- **O que mudou de verdade:**
  - o herói não fica mais preso ao golpe: anda 1,63 u durante um corte (antes 0,54), de lado e de costas, com o pé de
    apoio plantado;
  - tem rolamento e recuo com deslocamento e janela de invulnerabilidade reais, e salto com coyote e buffer;
  - trava e troca de alvo existem e são opcionais;
  - três fungos se revezam;
  - a morte não estica mais o modelo.
- **Liberdade de movimento:** pelos vídeos, a diferença A × C é grande. No C os três cortes saem andando, sem a parada
  que dava a sensação de travado.
- **O que eu não sei e só o teste físico responde:**
  - se o peso dos golpes sobreviveu à mobilidade: o C pode parecer "deslizante" para quem gosta de compromisso; o B
    existe para isso;
  - se a velocidade nova (1,75/3,5 u/s) é a certa com mouse e teclado;
  - se o rolamento, com a espada ao lado e a cabeça encostando no chão, fica bonito na velocidade do jogo ou estranho;
  - se a câmera livre incomoda quando o fungo cola atrás da cabeça (ela não gira sozinha de propósito).
- **Onde está mais fraco:**
  - **Câmera travada perto de obstáculos (P37):** 19% do tempo ruim nos piores cenários, pior que os 11% da 09.1. A 09.1
    ganhava subindo para 52° e girando sozinha, o que o proprietário não quer.
  - **Um rolamento só, para a frente (P43).**
  - **IA do grupo é simples (P45):** pressão por vez e espaço, sem táticas.
  - **Chefe:** é o fungo ampliado, honestamente marcado como protótipo.
- **Recomendação:** testar com as mãos os três perfis (pausa → Sensação) antes de qualquer polimento. Os números dizem
  que a fluidez melhorou muito; o que dizem os dedos é com você.
