# Direção B — Storybook Voxel

Guia da direção artística B. Fontes: `tools/chars_b.py`, `tools/creatures_b.py`, `tools/env_b.py`
(tudo gerado por script, determinístico, só stdlib). Luz e pós: `StyleProfile.b()`.

## 1. Princípios

1. **Livro ilustrado em voxels.** O cubo continua evidente; o que muda é a composição: poucas
   massas grandes e redondas, lidas de longe, com detalhe concentrado em pontos de interesse
   (rosto, fivela, janela acesa).
2. **Redondo, gordinho, assimétrico.** Toda massa principal é um superelipsoide (`p` ≈ 2,2–2,6) ou
   uma pilha de fatias superelípticas; cantos vivos são amaciados em um degrau. Nada é perfeitamente
   simétrico nem a prumo: tronco em S, casa cisalhada, chaminé torta, cumeeira selada.
3. **Paleta curta e quente.** 3–4 matizes por modelo. Sem preto puro e sem branco puro.
4. **Sombra pintada, sem ruído.** Cada material tem 3 tons (sombra / base / luz) escolhidos pela
   normal suavizada do volume (`chars_b.shade`). Sombra = multiplicar por roxo-vinho
   `(0,78 0,66 0,82)`; luz = somar amarelo-pêssego. Personagens não têm pontilhado; a folhagem usa
   manchas largas (blocos de 3–4 voxels), como pinceladas.
5. **Pequeno aventureiro, mundo grande.** Herói ≤ 1,25 u; porta de 1,5 u; flores na altura do joelho;
   cogumelo gigante com o dobro da altura do herói.

## 2. Paleta

| Uso | Base | Observação |
|---|---|---|
| Pele | `#f6c9a0` (clara `#f9d6b4`, morena `#eeb98a`) | sombra a 60% |
| Bochecha / boca | `#f29a8c` / `#b9604e` | |
| Olho / íris / brilho | `#3a2238` / `#7c4a3c` / `#fff8ea` | ameixa escuro no lugar do preto |
| Couro / couro escuro / camurça | `#9a6238` / `#6e4130` / `#c98f55` | |
| Creme (debruns, gola) | `#f3e3c0` | |
| Ouro (metal) | `#f2c14e` / `#c98f3a` | |
| Lâmina | `#f7f1e3` / `#d8cbc6` | fosca (ver limitações) |
| Aventureiro | túnica `#3f9388`, cachecol `#eaa93c`, calça `#6b4a5e`, cabelo `#b9542d` | |
| Ranger | capa `#7c9a46` / `#5f7d3c`, túnica `#f0dcae`, calça `#8c5340`, cabelo `#eab04e`, laço `#d6513f` | |
| Maga | manto `#7b4c86` / `#5d3a6e`, dourado `#ecb84c`, cabelo `#e4d8f4`, cristal `#86f0dc` (emissivo) | |
| Folhagem | carvalho `#7fae4a`, outono `#e89a3e`, pinheiro `#4a8a66`, bétula `#c2cc58`, marco `#6fa44c`, arbusto `#6f9f4a`, grama `#a9b854`, musgo `#9ab552` | |
| Casca | `#8f5d40` / `#6f4336`; bétula `#f1e6cf` com marcas `#6b4a5e` | |
| Pedra | `#b8aaa6`, escura `#9a8a90`, clara `#d2c6bc` | lilás-quente, nunca cinza neutro |
| Madeira | `#b07a48`, `#84533a`, `#d3a066`; enxaimel `#7a4a3a` | |
| Casa | estuque `#f5e6c8`, telha `#cf5a44` / `#a8433f` / `#e6805a`, porta `#3f8a8c`, palha `#e0b057` | |
| Acentos | coral `#e2644c`, rosa `#f2a0a6`, amarelo `#f4c653`, azul `#8098e0` | azul só em flor |
| Luz | lanterna `#ffd77a`, fogo `#ff9340`, magia `#45cdb8` | emissivos |

## 3. Personagens (voxel 1/32 u, rig `biped`)

Marcos comuns: botas 0–4, quadril (pivô das pernas) em y = 9, ombro em y = 19, crânio de
14 × 12 × 13 voxels em y = 21–32, olhos 2 × 3 com brilho, bochechas de 2 voxels.

| Personagem | Caixa (L × A × P) | Altura | Cabeças (sem arma/chapéu) | Pernas |
|---|---|---|---|---|
| `adventurer` | 20 × 37 × 18 | 1,16 u | 2,31 (cabeça com cabelo: 16) | 24% |
| `ranger` | 18 × 39 × 22 (corpo 35) | 1,22 u com pena | 2,50 (cabeça 14) | 26% |
| `mage` | 22 × 40 × 30 (corpo 35) | 1,25 u com chapéu | 2,50 (cabeça 14) | 26% |

Silhuetas: aventureiro = topete em gancho + mochila com saco de dormir + espada larga + ponta de
cachecol (`cape`); ranger = gorro de bico com pena (`hat`) + trança (`tail`) + arco na mão esquerda
(parte rígida `bow`) + aljava; maga = chapéu de aba larga com ponta caída (`hat`) + manto-sino
(`skirt`) + cajado com cristal emissivo.

Regras: corpo em pera (quadril mais largo que o ombro); mãos e botas maiores que o membro; rosto sem
sobrancelha nem nariz (testadas e descartadas: viram ruído nesta resolução); o cabelo avança 1 voxel
sobre a testa. O cajado é modelado inclinado 45° para trás porque o animador soma ~60° a toda
`weapon`; o resultado é um cajado quase vertical.

## 4. Criaturas

| Criatura | Voxel | Caixa | Altura | Anim |
|---|---|---|---|---|
| `pompom` | 1/32 | 14 × 22 × 20 | 0,69 u | hopper |
| `lumi` | 1/32 | 24 × 26 × 20 | 0,81 u | floater (miolo emissivo `core`, fagulhas `ring`) |
| `fungo` | 1/28 | 24 × 26 × 22 | 0,93 u | hopper (chapéu `cap`, braços de cipó) |
| `tortuga` | 1/16 | 32 × 37 × 57 | 2,31 u | quadruped (jardim no casco, folhagem com vento) |

## 5. Regras de forma — vegetação

- Copa = 3 a 7 novelos (superelipsoide `p` 2,15) que se sobrepõem; um dominante, os outros satélites.
- Tronco em pera (`trunk_b`): base larga que afina depressa, eixo em S, raízes bojudas.
- Pinheiro = saias arredondadas empilhadas, ponta dobrada. Bétula = tronco creme fino e sinuoso.
- `tree_oak2` é a árvore de outono (âmbar): o acento quente que quebra o verde da floresta.
- Cogumelos: pé gordo, chapéu em cúpula com aba, pintas redondas creme.
- Pedras: seixos redondos empilhados, veios largos em 2 tons, musgo só nas faces de cima.
- Orçamento medido (voxels de superfície): carvalho 1,8 mil; pinheiro 2,1 mil; árvore-marco 7,7 mil.

## 6. Regras de forma — arquitetura e objetos

- Casa: fundação de pedra, estuque com enxaimel, telhado côncavo de beiral largo com cumeeira selada
  e pontas levantadas, fiadas de telha (linha escura a cada 3 voxels), cisalhamento de 0,08 voxel por
  voxel de altura, chaminé torta, porta em arco deslocada, janelas emissivas. 8,2 mil voxels de superfície.
- Madeira nunca reta: postes inclinam um degrau, réguas selam.
- Toda construção tem uma luz quente (lanterna, janela) e um toque vegetal (musgo, trepadeira, flor).
- Santuário = portal-lua (anel de pedra) com cristal flutuante; o emissivo frio é reservado à magia.

## 7. Restrições

- Sem preto/branco puros, sem cinza neutro, sem azul saturado em grandes áreas.
- Sem pontilhado de 1 voxel em personagens; sem detalhes de 1 voxel que não sejam lidos a 10 u.
- Heróis entre 2,3 e 2,6 cabeças, pernas ≤ 30% da altura, ≤ 1,25 u com chapéu.
- Tudo original; nada derivado de outros jogos; nenhum asset de terceiros nem imagem gerada por IA.

## 8. Limitações conhecidas

- **Proporção do aventureiro no limite inferior** (2,31) por causa do topete; ranger e maga em 2,50.
- **Lâminas foscas.** Com material `metal`, a lâmina refletia o céu e ficava malva-acinzentada; passou
  a fosca creme. Na sombra ainda puxa para o lilás.
- **Cajado serrilhado.** A compensação de 45° deixa o cabo em degraus diagonais (lê-se como madeira
  nodosa, mas não é limpo). No golpe, o cajado atravessa a aba do chapéu.
- **Interpenetrações de animação:** a perna da maga sai pela frente do manto ao andar; a capelinha da
  ranger é rígida e os braços a atravessam na corrida; o arco balança junto com o braço esquerdo.
- **Nuca chapada** na ranger e na maga (placa de cabelo com marca de sombreamento em cruz).
- **Rosto do fungo** poluído (sobrancelhas e boca disputam poucos voxels); o chapéu o encobre de cima.
- **Ranger de costas** é a vista mais fraca: aljava, bolsa e trança competem.
- **Árvore-marco:** porta, janela e cogumelos-prateleira não foram verificados de perto (a floresta
  encobre a câmera); copa e lanternas foram vistas só nas capturas gerais.
- **Manchas da folhagem** lembram camuflagem em copas muito grandes vistas de cima.
- **Pintas do cogumelo gigante** saem grandes e irregulares.
- Grama e flores são um pouco grossas para o voxel de 1/16 u; `flower_blue` é o único matiz frio.
- Não rodei `./gradlew test` (compilação compartilhada nesta rodada); `heroesAreCompact` deve passar
  pelos números acima, mas não foi executado.
