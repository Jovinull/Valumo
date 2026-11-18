# Visão do projeto

## Objetivo
Um RPG de ação e exploração em terceira pessoa, num mundo de fantasia voxel, com personagens
**pequenos, compactos e expressivos**, combate em tempo real, progressão permanente e possibilidade
futura de cooperativo. Código e arte originais, em Java, sobre engine própria.

A sensação que perseguimos: *um pequeno aventureiro explorando um mundo grande e convidativo*.

## Direção artística (decidida)
A direção oficial é a **E — "Fábula Voxel"**: um livro de fantasia ilustrado construído em voxels.
Identidade em [../art/visual-identity.md](../art/visual-identity.md); padrão de qualidade em
[../art/contrato-de-qualidade.md](../art/contrato-de-qualidade.md); imagens oficiais em
[../art/reference/](../art/reference/README.md).

Como chegamos aqui: a rodada 01 comparou quatro direções (A — Miniature Fantasy, B — Storybook
Voxel, C — Pixel Voxel Adventure, D — Stylized Voxel RPG); a rodada 02 consolidou a E (forma e luz da B,
acabamento de personagem da D); a 03 poliu protagonistas, terreno, vegetação e luz; a 04 reconstruiu a
coreografia das armas; a 05 estabilizou e documentou o padrão. Registro completo em
[../historico/](../historico/README.md). A–D seguem no código apenas para comparação.

## Onde o projeto está
A fundação artística e técnica (identidade, protagonistas, criaturas de referência, cenário de teste,
animação com armas, pipeline reproduzível, validação automática e visual) está consolidada. **A
produção de novos assets em escala depende de aprovação do proprietário** sobre a rodada 05.

## Inspirações
O gênero de RPG voxel de exploração, do qual Cube World Alpha é a referência mais lembrada — pela
escala dos personagens, pela estética cúbica e pelo prazer de explorar. Usamos essas referências para
entender **princípios** (proporção, leitura, composição), nunca para copiar formas
([../historico/pesquisa/referencias-visuais.md](../historico/pesquisa/referencias-visuais.md),
[../historico/pesquisa/riscos-legais.md](../historico/pesquisa/riscos-legais.md)).

## Diferenciais
- Identidade visual própria e consistente, com regras escritas e imagens de referência.
- Personagens que funcionam como silhueta antes de funcionar como detalhe.
- Mundo com pontos de interesse desenhados: clareiras, ruínas, vilarejos.
- Animação com armas por trajetória autorada e validação espacial automática.
- Pipeline de arte reproduzível: assets gerados por script, idênticos byte a byte numa cópia limpa.

## Restrições técnicas
- Java 25, Gradle, LWJGL 3 (GLFW + OpenGL), JOML, Gson, GLSL. Sem game engine.
- Linux como ambiente principal; deve rodar em GPU integrada (Iris Xe ~100 FPS a 1080p na cena de teste).
- Sem assets de terceiros sem licença; sem conteúdo de outros jogos; sem geradores de imagem por IA.

## Fora do escopo até nova decisão
Combate funcional, inventário, sistema de equipamentos, mana/habilidades, multiplayer, mundo infinito,
missões, talentos, editor de mapas, física.

Áudio entrou na rodada 09.4 (pedido do proprietário): direção sonora em estudo, música e efeitos compostos de forma
deliberada em ferramentas reais (LMMS), integração mínima por eventos ([ADR 0023](../decisions/0023-audio-openal-stb-vorbis.md),
[audio.md](../architecture/audio.md)). Nada sonoro está aprovado ainda. A engine cresce conforme a necessidade real,
não por antecipação.
