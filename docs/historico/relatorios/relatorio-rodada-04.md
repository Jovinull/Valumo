# Relatório da rodada 04 — coreografia de armas, animação e polimento final

Data: 16/11/2026. Máquina: i5-1335U + Iris Xe (Mesa 26.2.6), Fedora 45. Entregas em
`output/visual-review/round-04/` (gerado por `tools/review4.sh`). Nenhum commit foi feito.
Decisões aprovadas na abertura da rodada (H2, C3, M1, O3, S3, T1, P1) registradas em
`docs/art/visual-identity.md`, `assinatura-visual.md` e ADR 0006.

## 1. Causa dos problemas anteriores

Medido antes de mudar qualquer código (`round-04/antes/`, `validacao/auditoria-rodada-03-legacy.txt`):

| Herói | Defeito (ataque da rodada 03) | Penetração medida |
|---|---|---|
| Aventureiro | lâmina atravessando a cabeça na preparação; empurrão à frente cruzando o rosto; início = fim | lâmina × cabeça **7,9 voxels**, × costas 2,7, mão × rosto 3,1 |
| Exploradora | girava o tronco e atirava **para o lado**; mão da corda diante do peito | arco × cabeça 1,1, mão × rosto 2,0 |
| Maga | cajado erguido por cima do chapéu e apontado na cintura como arma de fogo | cajado × cabeça **6,9**, × chapéu 6,0; até em repouso 0,8 |

Causa: os ataques eram **somas de ângulos de Euler** ponderadas por duas rampas num único plano
(braço erguido atrás → braço à frente). Não havia trajetória: a arma era efeito colateral de rotações,
o que gerava o vai e volta. As trocas de clipe interpolavam ângulos quadro a quadro. O IK de pé
lia o chão **suavizado**, não o topo real do degrau.

## 2. Arquitetura (ADR 0011)

- **Perfis de arma** declarativos e versionados (`assets/anim/*.json`, escritos à mão):
  - poses nomeadas com herança;
  - golpes como chaves no tempo, com fases e eventos;
  - transporte por clipe de locomoção;
  - herança entre perfis (`extends`/`merge`).
- **A pose descreve o que se vê**: posição da mão, direção da ponta, fio, polo do cotovelo, tronco,
  cabeça, giro do corpo e base dos pés.
- **Interpolação Hermite** no tempo, com tangentes de Catmull-Rom (`stop` para; `snap` acentua).
- **IK analítico de dois ossos** nos braços, com plano de dobra pelo polo e bases ortonormais. O
  resultado volta ao rig de partes rígidas como Euler ZYX + deslocamento.
- **Mistura no espaço de controle** (transporte ↔ ação ↔ ação) pelo `Sequencer`: tudo é função do tempo.
- **Validação espacial** (`WeaponCheck`, `viewer --check`, testes):
  - volumes aproximados por parte, a cada 1/480 s;
  - mede penetração, alcance, mão × rosto, antebraço × tronco e salto da ponta.
- **Ferramentas:**
  - modo de depuração (tecla `J` / `dbg=1`, sobreposição sem profundidade);
  - bancada de terreno (`lab=`);
  - `tools/anim_reach.py` (alcance por pose);
  - `tools/review4.sh` (pacote completo).
- **Hierarquia:** nenhum osso novo foi necessário (root, tronco, braço/antebraço, coxa/canela já
  existiam). Entraram os pontos de empunhadura (`meta.hands`, pivô do item), a corda do arco em duas
  partes e o broquel.

## 3. Antigo × novo

Vídeos lado a lado no mesmo enquadramento: `videos/16-antes-depois-{espada,arco,cajado}.mp4`. Na
validação, o antigo falha em 5 itens graves e o novo passa com 0 falhas em todos os golpes. A captura
`validacao/depuracao-antigo-espada-na-cabeca.png` mostra o volume da lâmina dentro da cabeça.

## 4. Espada e escudo

- **Guarda média:** ponta à frente na altura do peito, mão à direita. A primeira versão cobria o rosto de
  frente **sem interseção**: só a inspeção visual pegou.
- **Corte horizontal:**
  - preparação com a lâmina em pé ao lado da cabeça;
  - arco amplo da direita para a esquerda à frente do corpo, com tronco e pé da frente participando;
  - continuação e recuperação passando pela lâmina em pé.
- **Diagonal:** de alto à direita para baixo à esquerda.
- **Descendente:** erguida ao lado da cabeça (fora da silhueta), desce à frente até perto do chão.
- **Sequência:** horizontal → revés → descendente, sem voltar à guarda.
- **Broquel** (estudo `characters/r4/adventurer_shield`):
  - guarda baixa, guarda alta, bloqueio frontal com a espada recolhida e retorno;
  - durante os cortes, o broquel recua para a lateral para a lâmina passar à frente (validado: lâmina × escudo).
- Pranchas: `armas/espada-*`, `armas/escudo-*`.

## 5. Lâmina longa

- **L1** (esquerda → direita), **L2** (direita → esquerda), **L3** (descendente ao lado da cabeça).
- **L4:** L1 → L2 → L3 encadeados. O fim de cada corte é a preparação do seguinte, e entre L2 e L3 a
  lâmina passa baixa à frente (um oito).
- Vídeos a 30 FPS e a meia velocidade.
- **Duas mãos: medido e inviável nas proporções da E.** Com 10,6 voxels de braço, as mãos só se
  encontram junto à barriga, e o pomo entra no tronco (raio 6) em qualquer guarda à frente. A lâmina
  ficou de uma mão, com a mão livre equilibrando do lado oposto. O mecanismo de duas mãos (`lGripW`
  contínuo) existe e é testado numa pose sintética.

## 6. Arco

- **Sequência:** pegar a flecha por cima do ombro, girar pelo lado de fora, levantar, encaixar, puxar,
  sustentar, soltar, recuo, recuperação.
- **Corda:** desenhada em V até a mão.
- **Flecha:** alinhada à frente do personagem (< 12°, teste); voa a partir da própria matriz na soltura,
  sem colisão.
- **Limite medido:** uma puxada alinhada só fecha com o **corpo de lado** (`yaw` −63°, postura real de
  arqueiro), puxada curta e flecha de 15 voxels.
- **De frente,** o arco fica diante do rosto porque aponta para a câmera. Perfis e 3/4 leem bem.

## 7. Cajado

- **Conjuração frontal:**
  - erguido **de lado** (nunca acima do chapéu);
  - topo alinhado à frente e concentração (brilho no cristal);
  - projeção com o tronco, e o orbe verde-água (P1) nasce no cristal e voa à frente (teste).
- **Segunda conjuração:** a mão livre varre um arco alto, reúne e empurra a magia. Ainda parecida
  demais com a primeira (ver §16).

## 8. Guardas

Guarda ≠ repouso em todos os perfis:

- **Espada:** média. **Broquel:** baixa e alta. **Lâmina longa:** média.
- **Arco:** em repouso ao lado, ponta inclinada para fora. **Cajado:** "pronta".

`animacoes/repouso-guarda-repouso-*.png`.

## 9. Validação das interseções

- **Resultado:** `validacao/check.txt` e `check.csv`, zero falhas em todos os golpes dos 5 perfis e em
  3 roteiros de transição por herói.
- **Tolerâncias** (voxels de penetração):

  | Par | Tolerância |
  |---|---|
  | Item × cabeça/chapéu | 0,5 |
  | Item × tronco/costas/pernas | 1,0 |
  | Item × braço que não o segura | 1,2 |
  | Antebraço × tronco | 2,5 |
  | Mão × rosto | 1,0 |
  | Alcance | 0,5 |

- **Fora da checagem (contato legítimo):** a mão com o cabo, a flecha com as mãos do arqueiro, o escudo
  com o braço que o segura.
- **Continuidade:** o maior passo da ponta entre amostras fica entre 0,2 e 3,8 voxels, abaixo da soma dos
  raios, então nenhum cruzamento passa despercebido.
- **Autoteste:** um golpe sintético com a lâmina na cabeça **é reprovado**.
- **Limite do método:** volumes aproximados não provam ausência absoluta de clipping. Todos os golpes
  foram revistos em 5 ângulos + câmera de exploração (`armas/`), e as falhas que o validador não pega
  (guarda cobrindo o rosto) foram corrigidas pela inspeção.

## 10. Transições

- Roteiros validados e testados: idle/andar/correr → golpe → locomoção, e golpe → golpe.
- A mistura é no controle: entra em 0,16 s, sai em 0,28 s, golpe → golpe em 0,12 s.
- **Teste de continuidade:** nenhum pivô salta mais de 2 voxels em 1/240 s.
- **Teste de taxa de quadros:** a pose é idêntica a 30 e a 60 FPS.

Vídeos `13-corrida-para-ataque` e `00-demonstracao-cenario`. Ressalva: não existe cancelamento de golpe.

## 11. Locomoção e IK

- **Chão do IK:** o topo real dos voxels, com **pegada** (calcanhar, meio, ponta).
- **Pé em balanço:** mira a altura do pouso e ganha folga sobre a quina.
- **Limites explícitos:** um pé alcança 0,62 da perna; diferença entre os pés acima de 0,55 da perna
  (~0,13 u) não é escalada.
- **Bancada de perfil** (`animacoes/pes-perfil-{flat,ramp,steps,big}.png`, vídeos 14–14d): rampa e degraus
  de 1 voxel apoiam bem. Nos degraus de 0,2 u o pé alto fica no limite, como documentado.
- **Carga com arma:** transporte de cada arma validado em caminhada e corrida (nada atravessa pernas ou
  tronco). A bancada revelou a ponta da espada afundando no degrau ao subir, e o transporte de caminhada
  foi erguido.

## 12. Ambiente

- **Fungo:** a aba da frente recua, os olhos passam a 3 × 2 com brilho e a inclinação cai pela metade.
  Na câmera alta de jogo, antes não havia rosto e agora há (`ambiente/fungo-*`).
- **Lumi:** a conta da antena passou a verde-água (P1); o corpo continua quente.
- **Morros:** tratamento de distância das faces verticais do terreno, de 12 a 32 u. Contraste das listras
  −37% no panorama, −6% no fundo da exploração e 0% no herói e no primeiro plano. Sem geometria nova e
  sem chanfro; o T1 é mantido (`ambiente/relevo-*`).
- **HUD, água e sombras:** não mexidos (fora da prioridade).

## 13. Desempenho

1920 × 1080, E, qualidade PADRÃO, 3 × 300 quadros + aquecimento, **mesmo binário**, execuções intercaladas.
`r03-equivalente` = ataques antigos (`legacy`) e sem o tratamento de relevo à distância. Máquina:
Roblox fechado, RDD Pro ativo. Tabela completa em `performance/resumo.md`.

| Configuração | média ms (FPS) | p95 ms | 1% low FPS | draws | animação µs/quadro |
|---|---|---|---|---|---|
| Rodada 03 equivalente (1ª / repetição) | 10,04 / 10,08 (99,6 / 99,2) | 11,50 / 11,31 | 78 / 74 | 156 | 127 / 131 |
| Rodada 04, herói andando (1ª / repetição) | 10,21 / 10,84 (97,9 / 92,2) | 11,56 / 12,22 | 72 / 74 | 156 | 268 / 282 |
| Rodada 04, herói atacando em laço | 10,51 (95,1) | 11,99 | 71 | 156 | 271 |
| Rodada 04, 3 heróis (1 atacando, 2 em transporte com IK) | 11,02 (90,8) | 12,92 | 62 | 184 | 326 |
| Rodada 04, depuração ligada | 11,00 (90,9) | 12,78 | 67 | 232 | 272 |
| Rodada 04, ALTA (SSAA 1,5×) | 18,53 (54,0) | 20,19 | 44 | 156 | 316 |

- **Animação:** avaliar os atores passou de ~130 para ~270 µs por quadro (6 atores: 1 herói com IK + 5
  criaturas), ou seja **~0,14 ms por herói** com camada de ação e IK. Cada herói a mais em transporte
  com IK custa ~30 µs.
- **Quadro total:** +0,2 a +0,8 ms entre rodada 03 e rodada 04; a dispersão entre a 1ª execução e a
  repetição (0,6 ms) é da mesma ordem. Triângulos, malhas e memória de vértices são **idênticos**, e a
  fusão exata de faces foi mantida.
- **Depuração:** custa ~0,5 ms e +76 desenhos; fica desligada no jogo.
- **Validação geométrica** (offline, ferramenta e testes): as 35 animações e roteiros levam ~1,0 s no
  total, cerca de 24 µs por amostra a cada 1/480 s.
- **Alocações:** o IK ainda cria vetores e matrizes temporários por avaliação. Com dezenas de
  personagens animados, o próximo passo é reaproveitar temporários (registrado em problemas conhecidos).

## 14. Testes

`./gradlew test`: **33 testes, 0 falhas**. Os 19 anteriores ficaram intactos; os 14 novos em `ChoreoTest`:

- hierarquia e itens nas mãos;
- item rígido na mão sob IK;
- todos os golpes passam na validação;
- transições passam;
- o validador reprova lâmina na cabeça;
- intenção do corte horizontal, do descendente e da alternância da lâmina longa;
- alinhamento da flecha e voo;
- emissão da magia;
- duas mãos (mecanismo);
- determinismo e independência da taxa de quadros;
- continuidade sem salto;
- perfis e herança.

## 15. Documentação

- **Atualizados:**
  - `guia-de-animacao.md` (seção da rodada 04: estrutura dos golpes, formato do perfil, passo a passo
    para arma nova, limites das proporções, pés);
  - `visual-identity.md` (aprovações, armas);
  - `especificacao-de-personagem.md` (§8), `contrato-de-assets.md` (§9);
  - `checklist-de-aceitacao.md` (itens 18–20), `problemas-conhecidos.md`;
  - `assinatura-visual.md` (P1 aprovada);
  - ADR 0003 e 0006, `CLAUDE.md`.
- **Novos:**
  - ADR 0011;
  - skill `.claude/skills/weapon-animation/SKILL.md` (procedimento, pivôs, mãos, trajetórias, armadilhas,
    critérios, testes, revisão);
  - este relatório.

## 16. Defeitos ainda existentes

- Duas mãos inviáveis nas proporções (limitação documentada).
- Arco diante do rosto quando visto de frente.
- Segunda conjuração parecida com a primeira.
- HIT e EMOTE continuam FK e fora da validação.
- Degraus acima de ~0,13 u entre os pés.
- Não há cancelamento de golpe nem curvas de giro do corpo inteiro na locomoção.
- O IK aloca vetores temporários por quadro (custo medido em §13).
- Volumes aproximados.

## 17. Vídeos para aprovação artística

Pasta `output/visual-review/round-04/videos/`:

| Arquivo | Conteúdo |
|---|---|
| `00-demonstracao-cenario.mp4` | os três protagonistas no cenário, câmera de jogo |
| `01`–`03` | espada horizontal, diagonal, descendente |
| `04` | escudo em guarda e bloqueio |
| `05` / `05b` | sequência da espada, a 30 e a 60 FPS |
| `06` / `07` / `07b` | lâmina longa alternando lados, L1 → L2 → L3, meia velocidade |
| `08` | arco |
| `09` / `10` | cajado: as duas conjurações |
| `11` / `12` | caminhada e corrida com todos os equipamentos |
| `13` | corrida → ataque |
| `14`–`14d` | encosta, degraus, degraus grandes, corrida irregular (de perfil) |
| `15` | depuração |
| `16-*` | antes × depois |

## 18. Comandos

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 tools/build_assets.py && ./gradlew test :viewer:installDist     # 33 testes
viewer/build/install/viewer/bin/viewer --check output/tmp/check.csv     # validação de todos os golpes
viewer/build/install/viewer/bin/viewer --check legacy                   # mede os ataques da rodada 03
python3 tools/anim_reach.py sword                                       # alcance por pose ao autorar
tools/snap.sh "mode=char,style=e,char=0,act=slash_h,dbg=1,out=output/tmp/x.png"
tools/anim.sh output/tmp/x "mode=char,style=e,hero=characters/r4/adventurer_longblade,act=long_combo,speed=0.5" 170 30
tools/snap.sh "mode=char,style=e,char=0,lab=steps,clip=walk,yaw=90,out=output/tmp/pes.png"
tools/review4.sh todos                                                  # pacote completo da rodada
```

## 19. Avaliação crítica

**O que mudou de verdade.**

- As armas cortam, apontam e conjuram com trajetória: o vai e volta acabou.
- Interseções graves com cabeça, tronco e rosto foram de 6,9–7,9 voxels para zero dentro das tolerâncias.
- A infraestrutura (perfis, IK, validação, testes, depuração) permite criar armas novas sem mexer no
  `Animator`.

**O que ainda é protótipo.**

- **Ritmo:** é bom em espada e lâmina longa, mas falta peso secundário (cabelo, cachecol e mochila
  reagindo ao golpe) e não há impacto (micro-pausa, tremor, som).
- **Cajado e arco:** corretos, mas com menos personalidade que a espada.
- **Proporções:** a chibi limita duas mãos e arco. Ou a próxima rodada aceita isso como linguagem do
  jogo (preferível), ou estuda braços ligeiramente maiores só nos itens que pedem.

**Avaliação final.** Para um action RPG comercial, a coreografia agora é uma **base sólida e
reprodutível, ainda não polida**. A aprovação artística dos movimentos é do proprietário, pelos vídeos.
