# Riscos de propriedade intelectual ao se inspirar em Cube World

Pesquisa feita em 2026-11-16.

> **Aviso.** Este documento não é aconselhamento jurídico. É um resumo de pesquisa feito por um não advogado, baseado sobretudo em fontes secundárias sobre o direito dos Estados Unidos. A lei varia por país e os casos dependem dos fatos. Antes de lançar comercialmente, consulte um advogado de propriedade intelectual.

## Resumo

1. Gênero, mecânicas e estilo geral ("RPG de ação em mundo voxel colorido com personagens compactos") são ideias, e ideias não são protegidas por direito autoral.
2. O que é protegido é a expressão concreta: modelos, texturas, criaturas, personagens, mapas, interface, textos, música, código.
3. O risco real para este projeto não é "ser voxel", e sim acumular muitas escolhas específicas iguais às de um jogo só. Nos casos de 2012 que os clones perderam, o problema foi a soma de semelhanças.
4. Nomes e logotipos são tema de marca, não de direito autoral: não use "Cube World", "Picroma" nem variações no nome, no logotipo ou na divulgação.

## O que não é protegível: ideias, regras, gênero, estilo

A distinção ideia–expressão diz que o direito autoral protege uma expressão de uma ideia, não a ideia (https://en.wikipedia.org/wiki/Idea%E2%80%93expression_distinction). Em jogos, a jogabilidade em si não é protegida; código, arte e outros assets são (https://en.wikipedia.org/wiki/Video_game_clone).

Duas doutrinas reforçam isso, segundo o resumo do caso Tetris na Wikipedia (https://en.wikipedia.org/wiki/Tetris_Holding,_LLC_v._Xio_Interactive,_Inc.):

- **Scènes à faire**: elementos de estoque e detalhes genéricos de um gênero não são protegidos. Espadas, slimes, masmorras, vilas medievais, barras de vida e árvores de talentos entram aqui.
- **Fusão (merger)**: quando uma ideia só pode ser expressa de poucas maneiras, a expressão não é protegida, para não dar monopólio sobre a ideia. Um cubo é um cubo; grama verde sobre terra marrom é a maneira óbvia de representar um gramado em blocos.

Na prática, para este projeto, são **livres** como ideia:

- Mundo procedural feito de voxels, com biomas.
- RPG de ação com classes, níveis, equipamentos, montarias, planador, escalada.
- Personagens de cabeça grande e corpo curto (estilo chibi, tradição muito anterior a qualquer jogo voxel: https://en.wikipedia.org/wiki/Chibi_(style)).
- Modelos de voxels pequenos sobre terreno de blocos maiores (o Veloren, de código aberto, documenta essa técnica: https://book.veloren.net/contributors/artists/voxel-models.html).
- Paleta saturada, AO por vértice, névoa de distância (técnicas publicadas, por exemplo em https://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/).

## O que é protegível: a expressão concreta

No caso **Tetris Holding v. Xio Interactive** (2012), a juíza considerou não protegidas as ideias de blocos caindo, rotação e formação de linhas, mas considerou expressão protegida um conjunto de escolhas específicas: o tabuleiro de 20 por 10, a "sombra" da peça, a exibição da próxima peça, a mudança de cor ao pousar, entre outras. Copiar um desses elementos isoladamente não bastaria; o clone copiou **todos**, e os jogos lado a lado eram quase indistinguíveis (https://en.wikipedia.org/wiki/Tetris_Holding,_LLC_v._Xio_Interactive,_Inc.).

No caso **Spry Fox v. Lolapps** (2012), o juiz entendeu que o jogo acusado se apropriou de muitos elementos expressivos do original e observou que a análise deve focar no que é semelhante, não no que é diferente. O caso terminou em acordo (https://en.wikipedia.org/wiki/Spry_Fox,_LLC_v._Lolapps,_Inc.).

Duas lições:

- Trocar a "pele" de um jogo mantendo todas as demais escolhas específicas não protege ninguém.
- Diferenças pontuais não apagam um conjunto grande de semelhanças.

São **protegidos** no Cube World (e em qualquer outro jogo):

- Arquivos de modelo, sprites, texturas, sons, música e código. Extrair, converter ou "redesenhar por cima" desses arquivos é cópia.
- O desenho específico de cada criatura, mascote, NPC, arma e peça de armadura.
- A aparência específica de personagens jogáveis (a combinação particular de formato de cabeça, rosto, cabelo, mãos, pés e animações).
- Layout e arte da interface, ícones, mapa, fontes customizadas.
- Textos: nomes de habilidades, descrições de itens, diálogos.
- Estruturas e marcos de mundo reconhecíveis.
- Capturas de tela e material de divulgação.

## Trade dress (conjunto-imagem)

Trade dress é a proteção da aparência geral de um produto quando ela identifica a origem. Segundo o artigo do escritório Fenwick (https://www.fenwick.com/insights/publications/how-trade-dress-can-help-game-developers-level-up), quem alega precisa mostrar, entre outros pontos, que o conjunto é **não funcional**, que adquiriu **significado secundário** (o público o associa a um produtor) e que há **risco de confusão**. O mesmo artigo relata que, no caso Tetris, o tribunal reconheceu trade dress nas peças coloridas formadas por quatro blocos delineados e no campo vertical mais alto que largo, porque cor e estilo das peças não eram necessários para o jogo funcionar e havia inúmeras alternativas de desenho.

Para este projeto:

- "Voxel colorido" sozinho dificilmente é trade dress de alguém: é comum a dezenas de jogos e em parte funcional.
- O risco cresce se o jogo puder ser **confundido** com Cube World numa captura de tela ou numa página de loja: mesma construção de personagem, mesma paleta, mesma interface, mesma composição de céu e terreno, mesmo estilo de logotipo.
- Um argumento comunitário comum ("parece Cube World 2") é ótimo para marketing e ruim para a defesa. Evite alimentá-lo.

## Marcas e nomes

- "Cube World" é o nome do jogo da Picroma; não confirmei nesta pesquisa se há registro de marca nem em quais países. Trate como marca de terceiro de qualquer forma: direitos de marca podem existir pelo uso, e existe ainda um brinquedo eletrônico homônimo (a Wikipedia mantém desambiguação: https://en.wikipedia.org/wiki/Cube_World).
- Não use "Cube", "World", "Picroma" ou trocadilhos próximos no nome do jogo, do estúdio, do executável, do repositório público ou do domínio.
- Não use o nome do outro jogo em tags de loja, títulos de vídeo ou descrição ("o novo Cube World"). Dizer em entrevistas que o gênero foi uma inspiração é diferente de usar a marca alheia para se promover; na dúvida, pergunte a um advogado.
- Nomes de classes genéricos (guerreiro, mago, patrulheiro) são livres. Nomes inventados de habilidades, itens, criaturas e lugares de outro jogo não são para reutilizar.

## Brasil

Não consultei textos legais brasileiros nesta sessão; os pontos abaixo são de memória e precisam ser conferidos:

- A Lei de Direitos Autorais (Lei 9.610/1998), no artigo 8º, exclui da proteção ideias, sistemas, métodos e esquemas, planos ou regras de jogos — a mesma lógica da distinção ideia–expressão.
- Programas de computador têm lei própria (Lei 9.609/1998).
- Marcas e concorrência desleal são tratadas na Lei de Propriedade Industrial (Lei 9.279/1996). O "conjunto-imagem" não tem artigo específico e costuma ser discutido como concorrência desleal.

Como o jogo provavelmente será distribuído mundialmente (Steam, itch.io), o direito dos Estados Unidos e da União Europeia importa tanto quanto o brasileiro.

## Regras práticas para o projeto

### Processo de criação

1. **Sala limpa de assets.** Não baixar, extrair, converter nem abrir em editor arquivos de modelo, textura, som ou dados de Cube World ou de qualquer outro jogo comercial. Nem "só para medir".
2. **Sem decalque.** Não modelar com captura de tela de outro jogo aberta ao lado para reproduzir voxel a voxel. Estude princípios, feche a referência, e então modele a partir de conceitos próprios.
3. **Várias referências, nunca uma.** Cada decisão de arte deve ter pelo menos três fontes de inspiração de origens diferentes (outros jogos, ilustração, brinquedos, arquitetura real). É a melhor vacina contra semelhança acumulada.
4. **Conceito antes do voxel.** Desenhe o personagem ou criatura em papel ou pixel art antes de modelar. O esboço datado é prova de criação independente.
5. **Registro de procedência.** Versione os arquivos-fonte (`.vox` ou equivalente) com histórico; anote autor, data e referências de cada asset.
6. **Licenças de terceiros.** Assets de bancos como OpenGameArt têm licenças variadas (algumas exigem atribuição ou compartilhamento pela mesma licença). O Veloren é de código aberto com licença própria para código e assets. Leia a licença antes de usar qualquer coisa; "é grátis" não significa "é livre".
7. **IA generativa.** Se usar, não peça "no estilo de" um jogo específico nem alimente o modelo com capturas de tela de terceiros.

### Decisões de design para se afastar

8. **Construção de personagem própria.** Defina a anatomia do seu boneco (como cabeça, tronco e membros se conectam, formato de mãos e pés, tratamento do rosto) como uma decisão original e documentada em `proporcoes.md`. Evite replicar o conjunto específico de escolhas de construção de qualquer jogo isolado.
9. **Bestiário original.** Arquétipos (lobo, esqueleto, slime) são livres; o desenho específico não. Invente criaturas próprias para os papéis de mascote e de inimigo icônico.
10. **Paleta própria.** Monte a paleta a partir de teoria de cor e referências naturais; não use conta-gotas em capturas de tela.
11. **Interface própria.** Layout, molduras, ícones e fonte diferentes. A interface é das primeiras coisas comparadas lado a lado.
12. **Mundo próprio.** Regras de geração, formas de bioma, marcos e arquitetura com identidade própria. Não reproduza estruturas reconhecíveis.
13. **Textos e nomes próprios.** Nenhum nome de habilidade, item ou lugar reaproveitado.
14. **Pelo menos uma assinatura visual sua.** Algo que nenhuma das referências tenha (um tratamento de luz, um motivo gráfico recorrente, uma forma de vegetação), visível em qualquer captura de tela.

### Comunicação

15. Nome, logotipo e ícone sem relação com "Cube World".
16. Página de loja e trailers sem o nome de outros jogos.
17. Não aceitar contribuições de fãs que incluam assets extraídos ou "recriações fiéis".

### Teste de bolso

Antes de cada marco, monte uma captura de tela do jogo ao lado de capturas de três referências e pergunte a alguém de fora: "de qual jogo é esta imagem?". Se a resposta for o nome de outro jogo, liste o que causou a confusão e mude pelo menos os dois elementos mais citados.

## Limites desta pesquisa

- As fontes sobre os casos são a Wikipedia e um artigo de escritório de advocacia; não li as decisões judiciais originais.
- Não verifiquei registros de marca em nenhuma base oficial (INPI, USPTO, EUIPO).
- Não encontrei notícia de ação judicial envolvendo Cube World e jogos inspirados nele; ausência de resultado na busca não prova que não exista.
- A legislação brasileira foi citada de memória.

## Rodada 02: referências, licenças e originalidade

- **Nenhum asset de terceiros** foi usado: todo modelo vem dos scripts de `tools/`. Nenhuma imagem
  gerada por IA. Nenhuma referência visual externa foi baixada nesta rodada: o trabalho partiu só das
  capturas da rodada 01 (próprias).
- Ferramentas instaladas (não distribuídas com o projeto): Goxel 0.13 (GPL-3.0), LibreSprite 1.1-dev
  (GPL-2.0), Blockbench 5.2.2 (GPL-3.0), Temurin 25.0.5 (GPLv2 + Classpath Exception), numpy e Pillow
  (BSD/HPND, só QA), ffmpeg (LGPL/GPL, só para vídeos de revisão). Nenhuma entra no binário do viewer.
- Revisão de semelhança feita nesta rodada (análise artística, **não** avaliação jurídica):
  aventureiro de cabelo ruivo com topete, cachecol mostarda e saco de dormir; exploradora de capuz
  verde **levantado**, trança e arco recurvo; maga de chapéu de aba larga com copa torta, amuleto de
  estrela e manto aberto. São arquétipos genéricos de fantasia; a combinação de proporção 2,44,
  rosto em placa plana com olho 3 × 5 e paleta B não corresponde a personagem conhecido. O ponto de
  atenção é a **exploradora de capuz verde com arco**, arquétipo muito associado a "herói de capuz
  verde" de outras franquias: por isso o capuz é pontudo com ponta **caída para trás**, a túnica é
  creme (não verde), o cabelo é mel com trança lateral e não há chapéu/espada. Recomendo o teste de
  bolso acima com pessoas de fora antes de fixar o design.
- Nada de Cube World foi consultado, extraído, reconstruído ou imitado: proporção, rosto, paleta,
  casa e criaturas desta rodada descendem das direções B e D da rodada 01.
