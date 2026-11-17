# Referências visuais para um RPG voxel original

Pesquisa feita em 2026-11-16. Este documento reúne referências públicas e extrai **princípios gerais** de direção de arte. Não é um guia para reproduzir nenhum jogo: nenhum modelo, criatura, personagem ou estrutura de terceiros deve ser copiado. Veja também `proporcoes.md` (números para modelagem) e `riscos-legais.md`.

## Como ler os números

Cada número está marcado com sua origem:

- **[fonte]** — lido numa página aberta nesta pesquisa (URL na tabela).
- **[busca]** — apareceu só em resultado de busca; a página não foi aberta.
- **[estimativa]** — inferência minha a partir de observação geral ou de memória, sem fonte verificada nesta sessão. Trate como hipótese a testar no jogo.
- **[recomendação]** — proposta para este projeto, não um fato sobre outro jogo.

## Resumo

1. O problema "pessoas grandes de proporção realista feitas de cubos" tem três causas somadas: cabeça pequena demais (6 a 8 cabeças de altura), pernas longas demais (cerca de metade da altura) e voxels do personagem do mesmo tamanho dos blocos do mundo.
2. As referências que agradam ao público-alvo usam personagens de **2,5 a 4 cabeças**, com voxels de personagem bem menores que os blocos do terreno. O único número oficial que encontrei é o do Veloren: personagem com cerca de 22 voxels de altura por 10 de largura, num mundo cujo bloco vale 11 voxels pequenos, ou seja, 2 blocos de altura.
3. Leitura vem de silhueta e de poucas cores sólidas, não de detalhe. As diretrizes de Trove e de Hytale concordam nisso, embora discordem sobre pintar ou não luz na superfície.
4. Não achei fonte primária com as dimensões de modelo do Cube World Alpha. Tudo o que este documento diz sobre a escala dele é princípio geral ou estimativa.

## Tabela de referências

| Nome | URL | O que observar | Princípios aproveitáveis | O que evitar (originalidade) |
| --- | --- | --- | --- | --- |
| Cube World (visão geral) | https://en.wikipedia.org/wiki/Cube_World | Histórico: desenvolvimento desde 2011, alpha em 2 de julho de 2013; inspirações declaradas em Zelda, Secret of Mana e World of Warcraft | RPG de exploração em mundo voxel colorido; inspiração em gêneros, não em um jogo só | Nome, logotipo, classes, criaturas, mascotes, layout de interface e ícones específicos |
| Cube World Alpha, impressões | https://www.engadget.com/2013/10/30/block-by-block-hands-on-with-the-cube-world-alpha/ | Descrição de paleta "mais brilhante" e gráficos "mais nítidos" que Minecraft | Saturação alta, superfícies limpas sem textura ruidosa | Reproduzir a combinação exata de paleta, céu e geração de terreno |
| Cube World, comentário de fórum | https://www.tigsource.com/?p=24268 | Comentário de leitor nota personagens feitos de voxels menores que os cubos do mundo [busca, não é declaração do autor] | Duas escalas de voxel: fina para personagem, grossa para terreno | Copiar a construção específica dos personagens daquele jogo |
| Veloren, guia de modelos | https://book.veloren.net/contributors/artists/voxel-models.html | Bloco de paisagem = 11×11×11 voxels pequenos; personagem ~22 de altura × 10 de largura = 1×2 blocos [fonte] | Definir cedo a razão voxel de personagem / bloco de mundo e medir tudo nela | Os modelos e assets do Veloren têm licença própria; não importar nem redesenhar por cima |
| Veloren, guia de armas | https://book.veloren.net/contributors/guides/adding-weapons/guide.html | Cabo da arma com no máximo 3 voxels de comprimento e 3 de largura na empunhadura [fonte] | Padronizar o ponto de empunhadura para toda arma encaixar na mão | Silhuetas de armas específicas |
| Veloren, guia de armaduras | https://book.veloren.net/contributors/guides/adding-armor/guide.html | Armadura dividida em peças por parte do corpo (ombro, peito, mão, cinto, pernas, pés, costas) [fonte] | Peças modulares de armadura sobre um corpo-base fixo | Conjuntos de armadura reconhecíveis |
| Hytale, introdução a modelos | https://hytale.com/news/2025/12/an-introduction-to-making-models-for-hytale | Corpos "pequenos, volumosos e cartunescos"; só cubos e quads; 64 px por unidade em personagens e 32 px em blocos; texturas em múltiplos de 32 px; esticar entre 0,7× e 1,3× [fonte] | Densidade de texel fixa por categoria; personagem com o dobro da densidade do cenário para se destacar; sombra e AO pintados na textura | Rostos, raças e criaturas do Hytale; o "jeito" específico de pintura dele |
| Hytale Modding, assets de arte | https://hytalemodding.dev/en/docs/established-information/server/content-categories/art-assets | Resumo comunitário das densidades (32 px blocos, 64 px jogador e equipamento) [fonte] | Tabela de densidades como regra de projeto | Idem |
| Trove, guia de estilo | https://trovesaurus.com/page=5624 | Quebrar formas orgânicas em caixas; cores sólidas; não pintar brilho especular; gradiente só suave; não usar preto abaixo de RGB 10,10,10; evitar ruído de alto contraste; voxels conectados [fonte] | Caricatura por remoção de informação; a iluminação do motor faz o volume | Modelos de classes, montarias e chapéus do Trove |
| Trove, masmorras | https://trovesaurus.com/page=3622 | Limites de 125×125×125 para masmorras e 39×90×39 para covis; ruído automático desaconselhado [fonte] | Orçamento de volume por tipo de estrutura; maquete de blocos coloridos antes do detalhe | Plantas de masmorras existentes |
| Minecraft, jogador | https://minecraft.wiki/w/Player | Caixa de colisão de 0,6 × 1,8 blocos [fonte]; modelo de 32 px de altura com cabeça de 8 px, ou seja, 4 cabeças [busca] | Ponto de comparação: 4 cabeças com membros retos lê como "adulto em blocos" | É exatamente a proporção que o projeto quer evitar |
| Chibi / super deformed | https://en.wikipedia.org/wiki/Chibi_(style) | Cabeça entre 1/3 e 1/2 da altura; personagem de anime comum tem 7 a 8 cabeças; detalhe simplificado e traço característico exagerado [fonte] | Base numérica para a faixa de 2 a 3 cabeças | Ir ao extremo de 2 cabeças se a meta é "aventureiro", não mascote |
| Clip Studio Tips, razão de corpo chibi | https://tips.clip-studio.com/en-us/articles/4806 | Faixa de 1:4 (criança pequena) a 1:2 (super chibi); mão relaxada chega à metade superior da coxa; cotovelo na altura do umbigo; em 1:3 a distância queixo–umbigo vale uma cabeça [fonte] | Manter as relações de braço mesmo encurtando o corpo, para continuar lendo como humano | — |
| Ambient occlusion em mundos de blocos (0fps) | https://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/ | AO por vértice com 4 níveis a partir de 3 vizinhos; correção da diagonal do quad [fonte] | Sombra de contato barata e independente da câmera | — (técnica, não expressão artística) |
| Iluminação voxel (0fps) | https://0fps.net/2018/02/21/voxel-lighting/ | Luz por flood fill e AO que acompanha o sol [fonte] | Luz do céu e luz de tochas propagadas na grade | — |
| Estilos de voxel art em jogos | https://www.gamedeveloper.com/art/voxelart-styles-in-video-games | Classificação de estilos (voxel "em blocos" tipo Minecraft, voxel "pixelado" com sombreamento chapado, etc.) [fonte] | Escolher um estilo e não misturar; sombreamento chapado pede cores vibrantes | — |
| Crossy Road, making of | https://www.pocketgamer.biz/making-of-crossy-road/ | Arte feita no Qubicle; câmera girada porque "voxels ficam melhores em ângulo"; caixas de colisão óbvias e bordas nítidas [fonte] | Câmera em diagonal mostra duas faces e dá volume; forma = colisão | Os mascotes do jogo |
| Abordagem de voxel e pixel art (Lucas Rgznsk) | https://80.lv/articles/approaching-voxel-pixel-art | Começa pela silhueta em uma cor só; limita a paleta para não perder leitura [fonte] | Teste da silhueta preta antes de colorir | — |
| Pixel Unwrapper (Blender) | https://github.com/noio/Pixel-Unwrapper | Ferramenta que fixa "pixels por unidade" no UV [fonte] | Se houver textura, a densidade é um parâmetro global, não uma escolha por modelo | — |
| Tutorial MagicaVoxel (Kodeco) | https://www.kodeco.com/375-magicavoxel-3d-art-tutorial | Fluxo básico da ferramenta [fonte, só fluxo de trabalho] | Ferramenta candidata para os modelos | — |
| 3D Dot Game Heroes | https://en.wikipedia.org/wiki/3D_Dot_Game_Heroes | Sprites 2D retrô extrudados em voxels; espada que cresce a tamanhos enormes; editor de personagem [fonte] | Arma grande como assinatura visual; personagem como "sprite 3D" | A paródia direta de Zelda que o jogo faz |
| Dragon Quest Builders (resenhas) | https://www.gameskinny.com/reviews/dragon-quest-builders-more-than-a-minecraft-clone/ | Personagens de cabeça grande sobre mundo de blocos [busca] | Personagem arredondado e expressivo pode conviver com mundo cúbico | Traço de Akira Toriyama e monstros da série |
| Riverbond / trilogia voxel da Cococucumber | https://www.gamedeveloper.com/design/deep-dive-ravenlok-art-tech-production-pipeline | Fluxo conceito → pixel art → voxel no Qubicle; resolução crescente entre jogos [busca] | Desenhar o personagem em pixel art de frente e de lado antes de extrudar | Personagens e chefes desses jogos |
| Teardown, iluminação | https://techradar.com/gaming/consoles-pc/ray-tracing-is-so-crucial-to-teardown-that-the-world-goes-black-if-you-turn-it-off | Contraste entre forma em blocos e luz realista [busca] | Luz suave e sombras macias valorizam geometria simples | O custo: traçado de raios está fora do escopo de um motor inicial |
| Fugl, Staxel | (sem página própria aberta) | Fugl aparece como exemplo de voxel com sombreamento chapado no artigo da Game Developer; sobre Staxel não achei fonte primária | — | — |

## Análise comparativa pelos dez itens

### 1. Relação cabeça / altura total

A teoria de proporção dá a régua. Um personagem de anime comum tem 7 a 8 cabeças; no estilo super deformed a cabeça ocupa de 1/3 a 1/2 da altura, isto é, 2 a 3 cabeças [fonte: Wikipedia]. O tutorial da Clip Studio estende a faixa "chibi" até 1:4, que ele associa a criança pequena [fonte].

O boneco padrão do Minecraft tem 32 px de altura e cabeça de 8 px, portanto 4 cabeças [busca]. Com membros retos e ombros estreitos, essa proporção já lê como adulto. A tentativa anterior do projeto provavelmente estava em 5 cabeças ou mais.

Para Cube World, Trove e Veloren não encontrei a razão cabeça/altura publicada. De memória, os três ficam entre 2,5 e 3,5 cabeças [estimativa]. O Hytale descreve seus corpos como "pequenos, volumosos e cartunescos" sem dar número [fonte].

Conclusão: o alvo "aventureiro compacto, não boneco infantil" fica em **2,75 a 3,25 cabeças**. Abaixo de 2,5 o personagem vira mascote; acima de 3,5 volta a parecer gente comum em cubos.

### 2. Proporção tronco / membros

No corpo realista as pernas ocupam cerca de metade da altura [conhecimento geral de desenho, não verificado nesta sessão]. Encolher o personagem só "achatando" tudo por igual mantém essa metade e não resolve o problema. O que muda a leitura é **tirar altura das pernas**: num corpo de 3 cabeças sobram 2 cabeças para tronco e pernas, e dividir isso em partes quase iguais deixa as pernas com cerca de 1/3 da altura total [recomendação].

Regras úteis da Clip Studio que sobrevivem à compressão [fonte]: a mão relaxada alcança a parte de cima da coxa e o cotovelo fica na altura do umbigo. Braços que param na cintura dão aparência de bebê; braços que passam do joelho dão aparência simiesca.

Mãos e pés um pouco maiores que o membro (1 voxel a mais de cada lado) ancoram a pose e dão silhueta às extremidades [recomendação].

### 3. Escala dos olhos e elementos faciais

Não encontrei número publicado em nenhuma das referências. O que as fontes dão é a direção: olhos grandes, nariz mínimo e pouco detalhe no estilo chibi [fonte: Wikipedia]; no Hytale, a justificativa para dobrar a densidade de texel dos personagens é justamente o rosto, que é "o que se vê primeiro" [fonte].

Em resolução de voxel, o olho precisa de pelo menos 2 voxels para não sumir à distância [recomendação]. Um olho de 2 de largura por 3 de altura numa cabeça de 11 de altura ocupa cerca de 27% da altura da cabeça [recomendação, detalhada em `proporcoes.md`]. Olhos posicionados na metade inferior do rosto deixam testa alta, o que rejuvenesce; subir os olhos para o meio "amadurece" o personagem sem mudar o corpo.

### 4. Tamanho dos equipamentos relativo ao corpo

O único número oficial é do Veloren: empunhadura de no máximo 3×3 voxels para um personagem de 22 de altura [fonte]. O 3D Dot Game Heroes faz da espada gigante a assinatura visual do jogo [fonte].

Princípio comum: em personagem pequeno, equipamento em escala realista desaparece. Armas e escudos são exagerados em 1,3× a 2× em relação à proporção real [estimativa]. Uma espada de uma mão realista teria cerca de 55% da altura de um adulto; num corpo de 3 cabeças, manter esses 55% da altura **do personagem compacto** já a deixa enorme em relação ao tronco, e é isso que dá o ar de aventura.

### 5. Silhueta de personagens com armadura

O Veloren modela armadura em peças separadas por parte do corpo [fonte]. O guia de estilo de Trove pede formas quebradas em caixas, com "conceitos divertidos, linhas retas e cores majoritariamente sólidas" [fonte]. O artista Lucas Rgznsk começa pela silhueta numa cor só [fonte].

Aplicação: a armadura deve mudar o **contorno**, não só a cor. Ombreiras que passam 1 a 2 voxels da linha do ombro, elmo que altera o topo da cabeça (crista, chifres, aba), saia ou tasset que alarga o quadril [recomendação]. Cada classe ou conjunto deve ser reconhecível em preto chapado a uns 40 px de altura na tela [recomendação].

Cuidado: armadura não pode engrossar tanto o tronco que a cabeça pareça pequena de novo. Aumente a cabeça junto (elmo 1 voxel maior em cada lado) para preservar a razão.

### 6. Uso de cores e paletas

Trove: cores sólidas, sem brilho pintado, gradiente apenas suave, ruído de alto contraste desaconselhado e nunca preto puro (mínimo RGB 10,10,10) para que a peça ainda receba luz [fonte]. Hytale faz o oposto em um ponto: pinta sombra, AO e realce na textura, mas também evita ruído, grão e superfícies perfeitamente lisas [fonte]. O artigo da Game Developer observa que sombreamento chapado favorece cores vibrantes [fonte]. O Cube World Alpha foi descrito como de paleta mais brilhante que Minecraft [fonte: Engadget].

Síntese para o projeto [recomendação]:

- 8 a 16 cores por personagem, organizadas em 3 ou 4 materiais (pele, tecido, metal, couro) com 2 ou 3 tons cada.
- Uma cor de destaque saturada por personagem, em área pequena (cachecol, pluma, gema).
- Variação de tom entre voxels vizinhos de no máximo 5 a 8% de luminosidade se quiser "textura"; mais que isso vira ruído.
- Sem preto puro nem branco puro; deixe margem para a iluminação.
- A paleta do mundo deve ser própria. Não amostre cores de capturas de tela de outros jogos.

### 7. Quantidade de detalhe por modelo

Veloren: cerca de 22×10 voxels por personagem [fonte]. Minecraft: 32 px de altura [busca]. Guias genéricos encontrados na busca afirmam que um personagem de 32³ costuma ler melhor que um de 64³ [busca, fonte fraca].

Faixas práticas [recomendação]:

- Personagem jogável: 24 a 40 voxels de altura. Abaixo de 20 não cabe rosto com expressão; acima de 48 o modelo pede detalhe que briga com a estética de blocos.
- Criaturas pequenas: 8 a 16. Criaturas grandes e chefes: 48 a 96, mantendo o **mesmo tamanho de voxel** do personagem.
- Itens na mão: 8 a 30 no maior eixo.

Regra de ouro: todos os modelos de entidade usam voxels do mesmo tamanho físico. Misturar tamanhos de voxel entre personagem e inimigo é o equivalente 3D dos "mixels" em pixel art.

### 8. Densidade visual dos ambientes

O Hytale justifica densidade maior nos personagens porque isso os "descola do ambiente" e melhora a leitura da cena [fonte]. Trove limita o volume de cada estrutura e pede maquete antes do detalhe [fonte].

Princípio: o ambiente deve ter **menos frequência visual** que o personagem. Terreno em grandes massas de cor, com detalhe concentrado em pontos de interesse (entrada de caverna, acampamento, ruína) [recomendação]. Uma medida prática: num quadro típico de jogo, não mais que 20 a 30% da área da tela com detalhe fino (flores, pedrinhas, grama alta) [estimativa de projeto, a calibrar].

### 9. Escala da vegetação

Não achei números publicados. O Veloren informa apenas que árvores são feitas de blocos de paisagem e que plantações são sprites de voxels pequenos [fonte].

Em jogos estilizados a vegetação costuma ser exagerada para cima: o personagem pequeno sob árvores grandes reforça a sensação de aventura [estimativa]. Proposta em alturas de personagem (AP) [recomendação]:

- Grama e flores: 0,15 a 0,4 AP.
- Arbustos: 0,4 a 0,8 AP.
- Árvore jovem: 2,5 a 4 AP.
- Árvore comum: 5 a 8 AP, com copa de 3 a 5 AP de largura.
- Árvore monumental (marco de bioma): 12 AP ou mais.

Tronco com largura mínima de 0,5 AP numa árvore comum; tronco fino demais ao lado de um personagem cabeçudo parece poste.

### 10. Relação personagem / arquitetura

Pontos de referência: no Minecraft o jogador tem 1,8 bloco [fonte] e a porta 2 blocos [conhecimento geral, não verificado nesta sessão], isto é, porta de cerca de 1,1 AP — apertado, coerente com corpo de 4 cabeças. No Veloren o personagem tem 2 blocos de paisagem [fonte].

Personagem compacto tem cabeça larga: o que limita a passagem é a largura da cabeça com elmo, não a dos ombros. Proposta [recomendação]:

- Porta: 1,3 a 1,5 AP de altura e 0,9 a 1,1 AP de largura.
- Pé-direito: 1,8 a 2,2 AP.
- Casa térrea até a cumeeira: 3 a 4 AP.
- Janela: peitoril a 0,5–0,6 AP, para o rosto aparecer.
- Degrau: no máximo 0,25 AP se subir andando; 0,5 AP exige pulo.
- Torre ou marco de vila: 8 a 12 AP, visível acima das copas.

A arquitetura levemente superdimensionada combina com a vegetação alta e evita o efeito "gigante em casa de boneca".

## Iluminação artística

- **AO por vértice** [fonte: 0fps]: para cada vértice de face, olhe dois vizinhos de lado e um de canto. Se os dois lados estão cheios, nível 0; senão `3 - (lado1 + lado2 + canto)`. São 4 níveis. Quando `a00 + a11 > a01 + a10`, troque a diagonal do quad para evitar artefato de interpolação. Mesclar faces no greedy meshing só quando os valores de AO coincidem.
- **Sombreamento por face**: multiplicador fixo por orientação (por exemplo topo 1,0; lados voltados ao sol 0,85; lados opostos 0,7; base 0,55) dá volume sem custo [recomendação; valores são ponto de partida].
- **AO nos modelos**: aplique a mesma AO de vértice aos modelos de personagem na hora de gerar a malha. É o que faz cabeça, braço e mochila se separarem visualmente.
- **Névoa**: névoa de distância na cor do horizonte esconde o limite de carregamento e cria profundidade atmosférica; variar cor por bioma e hora do dia [recomendação].
- **Sombra**: uma sombra circular ou "blob" sob o personagem já ancora o modelo no chão; mapa de sombra com borda suavizada é o passo seguinte [recomendação].
- **Luz pintada ou não?** Trove diz para não pintar luz no modelo; Hytale pinta [fontes]. Com cor por voxel e AO do motor, a linha de Trove é a mais simples e coerente.

## Pixel art em geometria 3D (caso o projeto use texturas)

- Densidade de texel fixa por categoria, como no Hytale (64 px/unidade em personagens, 32 em blocos) [fonte].
- Filtragem `GL_NEAREST` na ampliação; se usar mipmaps, gere-os com cuidado para não borrar, ou desative-os em modelos pequenos [conhecimento geral de OpenGL].
- Não esticar além de 0,7× a 1,3× num eixo [fonte: Hytale].
- Um texel de personagem deveria ter o mesmo tamanho na tela que um voxel de personagem, se as duas técnicas conviverem [recomendação].

## Fontes não verificadas e lacunas

- Blog do autor do Cube World (`wollay.blogspot.com`) respondeu 404; TV Tropes respondeu 403. Nenhuma dimensão de modelo do Cube World foi confirmada.
- O blog técnico do Teardown não abriu (404 na URL tentada); o tamanho de voxel do Teardown não foi confirmado.
- Dimensões de personagem de Trove, Staxel, Dragon Quest Builders, Riverbond, Fugl e do editor do 3D Dot Game Heroes não foram encontradas.
- A página `hytalemodding.dev/en/docs/guides/3d-modeling` respondeu 404; usei a página de assets de arte do mesmo site.
- Vários resultados de busca sobre "voxel art tips" eram agregadores de baixa qualidade; não os usei como base de nenhum número.
