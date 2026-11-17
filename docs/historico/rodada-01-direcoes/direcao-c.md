# Direção C — Pixel Voxel Adventure

RPG clássico com acabamento moderno: **cuboides bem proporcionados + pixel art nítida**. Tudo é
gerado por script (`tools/cuboidlib.py`, `chars_c.py`, `creatures_c.py`, `env_c.py`), só com a
biblioteca padrão do Python, de forma determinística. Nada é imagem reduzida, filtro ou ruído.

## Princípios

1. **A silhueta vem da geometria.** Cabelo em mechas, cachecol, ombreira, saia da túnica, botas mais
   largas que a perna, aba e ponta de chapéu, beiral, galhos e raízes são cuboides (ou prismas de
   sprite). O modo sem textura (`notex=1`) tem de continuar legível.
2. **A textura dá leitura, não forma.** Rosto, costura, cinto, tábuas, telhas, runas.
3. **Uma densidade só: 1 texel = 1/32 u** em personagem, prop e terreno (tile 16 px num bloco de 1/2 u).
4. **Paleta mestra fechada.** Um pixel é o *nome* de uma cor; sombrear é descer um degrau da rampa
   (`tone`), nunca multiplicar RGB. Nenhuma cor fora da paleta chega aos atlas.
5. **Personagem compacto:** 2,3–2,6 cabeças, pernas visíveis ≤ 25% da altura, cabeça mais larga
   que o tronco, sem pescoço (o queixo afunda no cachecol/gola).

## Paleta mestra (47 cores)

Rampas do escuro para o claro; sombras puxam para o violeta, luzes para o amarelo. Não há preto puro.

| Rampa | Cores |
|---|---|
| ink (contorno) | `#2b2137` |
| skin | `#a9654f` `#db9a6e` `#f2c08f` `#fddfb9` |
| blush | `#ef8f7a` |
| brown (cabelo, couro, madeira) | `#432a33` `#6b3f2e` `#96592f` `#c07f3e` `#e3ad5f` |
| red | `#8f2a45` `#cc3d3d` `#f0664a` |
| pink | `#d9608f` `#f7a3c0` |
| gold | `#c07a2a` `#f2b93b` `#ffe37e` |
| green | `#1f4a47` `#2f7049` `#4d9c44` `#76ba48` `#b4dc62` |
| blue | `#28346e` `#3257ad` `#4585dc` `#7ebcf5` |
| cyan | `#3aa7bd` `#7fe3dc` `#d2fbf0` |
| purple | `#37275e` `#5a3f9c` `#8562cf` `#b99aea` |
| grey (pedra, aço) | `#3f4460` `#646e8a` `#94a1b5` `#c5d0dc` `#eef3f6` |
| sand | `#b39a66` `#d9c58a` `#f2e6b4` |
| dirt | `#5e4136` `#855b3d` `#a97a4c` |
| white (brilho) | `#ffffff` |

## Regras de pixel art (todas explícitas no código)

- **Grade ASCII** (`spr`) para o que importa: rostos 13×11, frente/costas das túnicas, cintos,
  franjas, escudo, espada, arco, porta, janela, placa, chama, flores, tufos, tiles de terreno.
- **Olhos** 2×3 px: brilho branco no canto superior, íris colorida embaixo; bochecha de 2 px; boca de
  1–3 px. Os olhos ficam na metade de baixo do rosto.
- **Contorno interno** (`edges`): bordas de face um tom abaixo/acima — nunca linha preta.
- **Pontas** (`shade_tips`): pixel com vazio logo abaixo (ponta de mecha, barra) desce um tom.
- **Dithering ordenado** (`checker`, `tone_checker`): só em transições de 2 linhas (gradiente da copa,
  rodapé do reboco, base das nuvens, musgo).
- **Padrões por regra**: `planks` (frisos, luz no topo da tábua, veios e emendas), `bricks` (junta
  escura, luz no canto superior esquerdo, variação por peça), `bark`, `shingles`, `stone` (chanfro
  claro/escuro, poucas trincas), `leaves` (tufos em escama 8×6, fileiras deslocadas, gradiente em
  degraus, recorte por alfa nas bordas). A variação usa um hash inteiro para *posicionar* elementos
  desenhados; não existe ruído borrado.
- **Prismas de sprite** (`prism`): a silhueta de um sprite vira geometria de 1 texel de espessura
  (franja, coroa de mechas, rabo de cavalo, lâmina, escudo redondo, aba do chapéu, empena da casa,
  vitória-régia). Não há buraco de alfa "oco" nem sombra retangular.
- **Alfa** só em planos cruzados (flores, tufos, samambaia, juncos, chama) e na borda das copas.

## Densidade de texel: por que 1/32 u

Na captura de jogo (1600×900, câmera padrão da cena) o herói ocupa ~180 px de altura: **~4,9 px de
tela por texel**. Um olho de 2×3 vira ~10×15 px e lê; a boca de 1 px ainda aparece. Na vista aérea
(`dist=26`) cai para ~1 px por texel: o rosto some, sobra a mancha de cor — que é o que a geometria
tem de sustentar. Com 1/16 u a cabeça teria ~7 texels (olho de 1 px, sem rosto); com 1/64 u o degrau
de 1 texel da geometria ficaria invisível e a textura passaria a carregar a forma. 1/32 também casa
com o bloco de terreno: 16 px por 1/2 u. (As alternativas 1/16 e 1/64 foram descartadas por conta;
**não** foram renderizadas.)

### Teste de textura × geometria (feito nas capturas)

`output/tmp/c_adv_notex.png` contra `c_adv_front.png`: sem textura continuam legíveis cabelo
espetado, franja, cachecol, ombreira, saia da túnica, botas, espada e broquel; somem rosto, alça do
peito e emblema do escudo. Conclusão: a forma se sustenta; o rosto depende 100% da textura.

## Personagens (texels; 1 texel = 1/32 u)

| | Altura | Largura × prof. | Cabeça (c/ cabelo) | Cabeças | Cuboides | Atlas |
|---|---|---|---|---|---|---|
| `adventurer` | 37 (1,16 u) | 22 × 15 (com broquel) | crânio 13×11×11, 16 com mechas | 2,31 | 82 | 128×96 |
| `ranger` | 35,8; 40 com pena/rabo | 19 × 23 | 14,8 | 2,42 | 89 | 128×92 |
| `mage` | 31 sem chapéu; 41 com (1,28 u) | 19 × 20 | 11 (sem chapéu) | 2,82 sem chapéu | 61 | 128×104 |

- Pernas: 9–10 texels, das quais ~7 visíveis sob a saia (19–23% da altura).
- `adventurer`: túnica azul em sino (peito 10, cinto 11, saia 12), cachecol vermelho com ponta que
  balança (`cape`), ombreira no braço da espada, broquel, bolsa, coroa de mechas em dois planos.
- `ranger`: capelete, saia (`skirt`), faixa com pena, rabo de cavalo em S (`tail`), aljava com
  flechas sobre o ombro, arco como `weapon`, botas de cano alto.
- `mage`: chapéu de aba larga com ponta dobrada (`hat`), cabelo branco longo com mechas presas, gola
  larga com broche, mangas em sino, manto em três degraus (`skirt`), cajado com crescente e orbe.

Criaturas: `pompom` 0,68 u (15 cuboides), `lumi` 0,83 u (36), `fungo` 0,86 u (26), `tortuga` 2,28 u
(53, jardim com árvore, flores, pedra e cogumelos no casco).

## Ambiente e terreno

- 35 props, 454 cuboides, um atlas de 2048×1012. Casa 158×157×136 texels (porta 28×48 = 0,9×1,5 u).
- Copas: bloco com folhas recortadas por alfa nas bordas + miolo escuro 2 texels para dentro; blocos
  girados em Y quebram o alinhamento (pinheiro alterna 0°/45°).
- Terreno: faixa de 13 tiles 16×16 (`grass_top` ×3, `forest_top`, `path` ×2, `sand`, `rock` ×2,
  `grass_side`, `forest_side`, `dirt` ×2). Detalhes ficam longe das bordas para ladrilhar.

## Restrições

- Um cuboide tem uma única rotação (`rot`/`origin`); não há rotações aninhadas.
- Faces sobrepostas no mesmo plano cintilam: peças de cobertura são 0,5–1 texel maiores.
- Texturas têm o tamanho exato da face em texels (`box` recusa canvas de tamanho errado).
- O atlas tem 1 px de sangria em volta de cada região.

## Limitações conhecidas

- **Árvores e copas ainda leem como pilha de caixas** de perto; o recorte de borda ajuda só na silhueta.
- **Chapéu da maga**: copa em degraus baixos, a ponta dobrada vira um caroço visto de trás; com ele a
  maga passa do teto de 1,25 u (1,28 u). Sem chapéu ela tem 2,82 cabeças (fora do alvo 2,3–2,6).
- **Manto da maga**: o manto não acompanha as pernas; no passo, a bota atravessa a frente do manto.
- **Lado da cabeça**: área grande de pele lisa com a orelha; faltam costeletas/volume lateral.
- **Rabo de cavalo e aljava da patrulheira** se cruzam na corrida.
- **Grama**: a repetição do tile aparece em planos abertos (só 3 variações).
- **Sombras ignoram alfa** (`depth.frag` vazio): planos de flor/tufo projetam o retângulo inteiro.
- **Sem material**: `CuboidMesh` grava material 0, então lanterna, orbe, chama e a Lumi não emitem
  luz/bloom, metal não tem especular e a folhagem não balança ao vento.
- **Sem AO** nos cuboides (só o terreno tem AO por vértice).
- Rochas e nuvens são caixas giradas; servem a distância de jogo, não de perto.
- Capturas com tamanhos diferentes no mesmo `snap.sh` saíram corrompidas uma vez (faixas pretas);
  repetir com um tamanho só resolveu.
