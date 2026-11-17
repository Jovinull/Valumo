# Proporções de personagem: de 2 a 4 cabeças

Pesquisa feita em 2026-11-16. Complementa `referencias-visuais.md`. As medidas em voxels deste documento são **recomendações de projeto** derivadas dos princípios pesquisados; não são medidas de nenhum jogo existente. Onde um número vem de fonte, a fonte está indicada.

## Recomendação em uma linha

Personagem-base de **32 voxels de altura com cabeça de 11**, cerca de **2,9 cabeças**: cabeça 34% da altura, tronco 31%, pernas 34%. É compacto e cabeçudo sem virar mascote.

## Por que o personagem anterior parecia "gente grande em cubos"

Três fatores, em ordem de impacto:

1. **Cabeça pequena.** O corpo adulto real tem de 6 a 9 cabeças segundo o tutorial da Clip Studio (https://tips.clip-studio.com/en-us/articles/4806); personagens de anime comuns têm 7 a 8 (https://en.wikipedia.org/wiki/Chibi_(style)). Qualquer coisa acima de 4,5 lê como adulto.
2. **Pernas longas.** Pernas com metade da altura são o sinal mais forte de "proporção humana".
3. **Voxel de personagem igual ao bloco do mundo.** Sem resolução para rosto e mãos, sobra só a proporção, e ela denuncia o realismo. O Veloren resolve com duas escalas: bloco de paisagem de 11 voxels pequenos e personagem de cerca de 22×10 voxels pequenos (https://book.veloren.net/contributors/artists/voxel-models.html).

## Comparação das cinco proporções

Percentuais da altura total. A coluna "pernas" inclui os pés. Valores são modelos de referência meus, coerentes com a faixa de 1/3 a 1/2 de cabeça citada pela Wikipedia e de 1:2 a 1:4 citada pela Clip Studio.

| Cabeças | Cabeça | Tronco | Pernas | Leitura | Combate e animação | Veredito para este projeto |
| --- | --- | --- | --- | --- | --- | --- |
| 2 | 50% | 27% | 23% | Mascote, brinquedo, bebê | Braços mal alcançam acima da cabeça; arma some atrás do rosto | Infantil demais |
| 2,5 | 40% | 30% | 30% | Chibi clássico, fofo | Funciona com poses simples; pernas quase sem joelho | Limite inferior; bom para NPCs crianças ou raças pequenas |
| **3** | **33%** | **32%** | **35%** | **Aventureiro compacto, heroico e simpático** | **Braço alcança, perna dá passada visível, arma lê bem** | **Alvo principal** |
| 3,5 | 29% | 32% | 39% | Jovem estilizado | Animação mais fácil, menos carisma no rosto | Limite superior; bom para raças altas |
| 4 | 25% | 31% | 44% | Adulto em blocos (proporção do boneco padrão do Minecraft: 32 px com cabeça de 8 px, segundo resultados de busca) | Folgado | É o que se quer evitar |

A faixa útil é **2,75 a 3,25 cabeças**. Variar dentro dela entre raças ou tipos de corpo dá diversidade sem quebrar o estilo.

## Modelo-base recomendado: 32 voxels

Eixos: X = largura, Y = altura, Z = profundidade. Medidas em voxels de personagem.

| Parte | Largura (X) | Altura (Y) | Profundidade (Z) | Observações |
| --- | --- | --- | --- | --- |
| Cabeça (sem cabelo) | 12 | 11 | 10 | Mais larga que o tronco; cantos superiores podem perder 1 voxel para suavizar |
| Cabelo / elmo | +1 por lado | +1 a +3 | +1 atrás | É aqui que a silhueta do topo muda |
| Pescoço | — | 0 | — | Sem pescoço: a cabeça assenta direto no tronco |
| Tronco | 10 | 10 | 6 | Pode afunilar para 8 na cintura |
| Braço (cada) | 3 | 9 | 3 | Do ombro à ponta da mão; os 9 já incluem a mão |
| Mão | 4 | 3 | 4 | São os 3 voxels de baixo do braço, alargados em 1 voxel |
| Perna (cada) | 4 | 11 | 5 | Inclui o pé; vão de 2 voxels entre as pernas |
| Pé / bota | 4 | 3 | 6 | Avança 1 voxel à frente da perna |

Soma vertical: 11 (pernas) + 10 (tronco) + 11 (cabeça) = **32**. Razão 32 ÷ 11 ≈ **2,9 cabeças**.

Esclarecimento sobre o braço: o comprimento total do ombro à ponta da mão é **9 voxels**, dos quais os 3 de baixo são a mão alargada. Com o ombro no topo do tronco (Y = 21), a mão termina em Y = 12, ou seja, 1 voxel acima do topo das pernas. Isso respeita a regra da Clip Studio de que a mão relaxada chega à parte superior da coxa quando o braço balança, sem deixar o braço comprido.

Larguras de silhueta:

- Cabeça: 12 (14 com cabelo).
- Ombros com braços: 10 + 3 + 3 = 16.
- Quadril: 10.
- Pés: 4 + 2 + 4 = 10.

A cabeça tem 75% da largura dos ombros com braços e 120% da largura do tronco. Essa relação — cabeça mais larga que o tronco, mais estreita que os ombros com braços — é o que dá o ar de "herói compacto" em vez de "bebê" (cabeça mais larga que tudo) ou "adulto" (cabeça com metade dos ombros).

### Rosto

| Elemento | Medida | Posição |
| --- | --- | --- |
| Olho | 2 de largura × 3 de altura | Base do olho a 3 voxels do queixo; topo a 6 |
| Distância entre olhos | 4 voxels | Colunas 3–4 e 9–10 de uma face de 12 |
| Brilho do olho | 1 voxel claro no canto superior | Opcional; ajuda muito de perto |
| Sobrancelha | 2 × 1, um voxel acima do olho | Opcional; é o principal recurso de expressão |
| Boca | 2 × 1 ou nenhuma | 1 voxel acima do queixo; omitir é aceitável |
| Nariz | nenhum | Em 12 de largura, nariz vira ruído |

O olho ocupa 27% da altura da cabeça e 17% da largura. Fica na **metade inferior** do rosto (centro do olho a cerca de 41% da altura da cabeça), deixando testa alta. Para um personagem mais velho ou severo, suba os olhos 1 voxel e reduza para 2 × 2.

Olhos podem ser voxels da própria cabeça (mais simples) ou uma pequena textura sobreposta que permita piscar e mudar de expressão. O Hytale justifica densidade maior no rosto exatamente por expressividade (https://hytale.com/news/2025/12/an-introduction-to-making-models-for-hytale).

### Largura par ou ímpar

Com largura **par** (12, 10) não existe coluna central: olhos e pernas ficam simétricos, mas detalhes de 1 voxel (fivela, nariz, crista de elmo) não centralizam e precisam ter 2 de largura. Com largura **ímpar** (11, 9) acontece o contrário: o detalhe central fica exato e o vão entre as pernas tem 1 ou 3 voxels. A tabela acima usa par. Se preferir ímpar, use cabeça 11 × 11 × 9 e tronco 9 × 10 × 5, com pernas de 4 e vão de 1. Decida uma vez e aplique a todos os humanoides.

## Variações para 24 e 40 voxels

| Altura total | Cabeça (X × Y × Z) | Tronco (X × Y × Z) | Perna (X × Y × Z) | Braço (X × Y × Z) | Olho | Cabeças |
| --- | --- | --- | --- | --- | --- | --- |
| 24 | 9 × 8 × 8 | 7 × 8 × 5 | 3 × 8 × 4 | 2 × 7 × 2 | 1 × 2 ou 2 × 2 | 3,0 |
| 28 | 10 × 10 × 9 | 8 × 9 × 5 | 3 × 9 × 4 | 2 × 8 × 3 | 2 × 2 | 2,8 |
| **32** | **12 × 11 × 10** | **10 × 10 × 6** | **4 × 11 × 5** | **3 × 9 × 3** | **2 × 3** | **2,9** |
| 36 | 13 × 12 × 11 | 11 × 12 × 7 | 5 × 12 × 6 | 3 × 11 × 3 | 2 × 3 | 3,0 |
| 40 | 14 × 13 × 12 | 12 × 13 × 7 | 5 × 14 × 6 | 4 × 12 × 4 | 3 × 4 | 3,1 |

Notas:

- Em 24 o olho de 1 voxel de largura some à distância; prefira 2 × 2 e aceite um rosto mais "fofo".
- Em 40 a superfície já pede detalhe secundário (costuras, rebites). Resista: mantenha massas grandes de cor.
- 32 é o melhor compromisso entre rosto legível e custo de modelagem e animação.

## Equipamento

Medidas para o personagem de 32. Escale proporcionalmente para outras alturas. O limite de empunhadura segue a mesma lógica do guia de armas do Veloren, que fixa o cabo em no máximo 3 × 3 voxels (https://book.veloren.net/contributors/guides/adding-weapons/guide.html).

| Item | Comprimento | Outras medidas | Relação com o corpo |
| --- | --- | --- | --- |
| Adaga | 8 a 10 | Lâmina 2 de largura | ~30% da altura |
| Espada de uma mão | 15 a 18 | Lâmina 3 de largura, 1 de espessura; guarda 5 a 7; cabo 3 + pomo 2 | ~50% da altura |
| Espada de duas mãos | 24 a 30 | Lâmina 4 a 5 de largura; guarda 9 | 75 a 95% da altura |
| Machado | 16 a 20 | Cabeça 7 × 7 | A cabeça do machado é quase do tamanho do tronco |
| Cajado | 32 a 36 | Ponta ornamentada 5 × 5 × 5 | 100 a 110% da altura |
| Arco | 20 a 24 | 2 de espessura | ~70% da altura |
| Escudo | 9 a 11 de diâmetro | 2 de espessura | Entre o tronco e a cabeça em tamanho |
| Mochila | 8 × 8 × 4 | Nas costas | Não ultrapassar a largura do tronco |

Princípios:

- **Empunhadura padrão.** Todo cabo tem seção de no máximo 2 × 2 ou 3 × 3 e um ponto de âncora fixo, para qualquer arma encaixar na mesma mão.
- **Exagero controlado.** Lâminas com 3 voxels de largura parecem gordas no papel e corretas no jogo. Lâmina de 1 voxel some.
- **A arma não cobre o rosto na pose parada.** Segure-a de lado ou nas costas.
- **Arma embainhada conta na silhueta.** Uma espada nas costas, em diagonal, passando 3 a 5 voxels acima do ombro, identifica a classe de costas — que é como o jogador vê o personagem a maior parte do tempo.

## Armadura sem estragar a proporção

- Camada de armadura = corpo-base + 1 voxel para fora. Nunca + 2 no tronco inteiro.
- Ombreiras: 5 × 3 × 5, passando 1 a 2 voxels da linha do braço.
- Elmo: cabeça + 1 voxel por lado e + 1 a + 3 no topo (crista, chifres, aba). Aumentar o elmo junto com o tronco preserva a razão de cabeças.
- Botas e manoplas: + 1 voxel de largura; reforçam o peso das extremidades.
- Saia de armadura ou tasset: alarga o quadril para 12, o que cria uma silhueta em ampulheta invertida (ombros 18, cintura 10, quadril 12).
- Três arquétipos distinguíveis em silhueta preta: pesado (ombros largos, elmo fechado e alto), ágil (capuz pontudo, capa, linhas diagonais), místico (chapéu ou capuz volumoso, manto que esconde as pernas).

## Escala do mundo em relação ao personagem

Defina o bloco de terreno em voxels de personagem. Três opções para um personagem de 32:

| Bloco de mundo | Personagem em blocos | Efeito |
| --- | --- | --- |
| 16 voxels | 2,0 | Terreno grosso, leitura de "mundo de blocos" clássico; mesma relação do Veloren (2 blocos) |
| 12 voxels | 2,7 | Terreno mais fino, encostas mais suaves, construções com mais forma |
| 8 voxels | 4,0 | Terreno detalhado; custo de malha e memória 8× maior que com 16 |

Recomendo começar em **12 ou 16** e decidir com um protótipo de terreno na tela. Não achei a razão usada pelo Cube World Alpha em nenhuma fonte, então ela não entra como argumento.

Medidas de mundo em alturas de personagem (AP), detalhadas em `referencias-visuais.md`:

- Porta: 1,3 a 1,5 AP de altura; 0,9 a 1,1 AP de largura (42 a 48 × 29 a 35 voxels de personagem).
- Pé-direito: 1,8 a 2,2 AP.
- Casa térrea: 3 a 4 AP até a cumeeira.
- Árvore comum: 5 a 8 AP; árvore jovem 2,5 a 4 AP.
- Arbusto: 0,4 a 0,8 AP. Grama e flores: 0,15 a 0,4 AP.
- Degrau transponível andando: até 0,25 AP.

## Lista de verificação para cada personagem novo

1. A silhueta em preto chapado identifica a classe a 40 px de altura na tela?
2. A razão está entre 2,75 e 3,25 cabeças (contando elmo)?
3. A cabeça é mais larga que o tronco e mais estreita que ombros + braços?
4. As pernas têm no máximo 36% da altura?
5. Os olhos têm pelo menos 2 voxels de largura e estão na metade inferior do rosto?
6. Há no máximo 16 cores, sem preto ou branco puros?
7. A arma tem pelo menos 3 voxels de largura na parte mais visível?
8. O modelo usa o mesmo tamanho físico de voxel que todos os outros?
9. Visto de costas, em diagonal, ainda é reconhecível?

## Limites desta recomendação

Os números em voxels são propostas de partida, não medições. Não foi possível confirmar em fonte primária as dimensões de personagem de Cube World, Trove, Hytale (em blocos) ou Dragon Quest Builders. O único dado oficial de dimensão de personagem voxel usado aqui é o do Veloren. Ajuste depois de ver o modelo em movimento, com a câmera e a iluminação reais do jogo.
