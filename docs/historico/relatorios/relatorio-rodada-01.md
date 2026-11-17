# Relatório da rodada 01

**Resposta curta:** sim — é viável fazer, em Java com engine própria, um RPG voxel original com
personagens pequenos e estética suficientemente bonita para seguir adiante. As quatro direções
rodam no mesmo viewer, com personagens entre 2,3 e 2,8 cabeças que não leem como "pessoas de cubos".
Recomendo seguir com a **forma e a luz da direção B com a resolução de personagem da direção D**
(seção E). Há pendências reais, listadas em [problemas-conhecidos.md](../../validation/problemas-conhecidos.md).

## A. Ambiente

Detalhes em [ambiente.md](../../validation/ambiente.md). Ubuntu 24.04, i5-1135G7, Intel Iris Xe (Mesa 25.2.8,
OpenGL 4.6; contexto 3.3 core), JDK Temurin 25.0.3, Gradle 9.9.1 (wrapper), LWJGL 3.4.3, JOML
1.10.8, Gson 2.13.2, Python 3.12 (stdlib). Nenhuma ferramenta artística instalada: tudo foi
modelado por script ([ADR 0002](../../decisions/0002-assets-por-script.md)).

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 tools/build_assets.py && ./gradlew :viewer:run     # 1–4 direção, Tab modo, C personagem
```

## B. Propostas visuais

| | A — Miniature Fantasy | B — Storybook Voxel | C — Pixel Voxel Adventure | D — Stylized Voxel RPG |
|---|---|---|---|---|
| Ideia | voxel clássico cúbico, cores vivas | livro ilustrado, formas redondas | cuboides + pixel art | voxel fino, materiais |
| Personagem | voxel 1/24 u, 24–26 vox, 2,4–2,5 cabeças | 1/32 u, 37–40 vox, 2,3–2,5 | texel 1/32 u, 2,3–2,8 | 1/44 u, 52–55 vox, 2,55 |
| Texturas | — | — | atlas desenhado, nearest | — |
| Luz | meio-dia, sombra dura | fim de tarde, sombra roxa suave, contorno, vento | simples, sombra dura | sombra macia, rim, especular, bloom, vento |
| Modelos | 3 heróis, 4 criaturas, 35 props | idem | idem + 13 tiles de terreno | idem |
| Animações | idle, walk, run, attack, hit, emote (as mesmas, procedurais) | + exagero 1,2 | idem | idem |
| Pontos fortes | leitura imediata; barato de produzir; escala convence | identidade mais própria; cena acolhedora; melhores criaturas | mais leve (91 FPS); herói e construções bons; detalhe barato | rostos e equipamento mais expressivos; casa e luz mais ricas |
| Pontos fracos | o mais genérico; pouco detalhe; chão liso | costas da aventureira; grama grossa | árvores em caixas; risco de "parecer Minecraft"; sem AO/emissivo | 29 FPS; mais caro de modelar; arco e cajado mal resolvidos |

Guias: [A](../rodada-01-direcoes/direcao-a.md) · [B](../rodada-01-direcoes/direcao-b.md) · [C](../rodada-01-direcoes/direcao-c.md) · [D](../rodada-01-direcoes/direcao-d.md).
Estudo de proporções: [../experiments/proporcoes.md](../experimentos/proporcoes.md).

## C. Evidências visuais

Todas renderizadas pelo viewer (`--review`); nenhuma imagem conceitual. Em `output/visual-review/`:

- `comparacao/matriz-7-vistas.png` — linhas A–D × frente, perfil, costas, isométrica, no cenário,
  junto à casa, junto à criatura (mesmas câmeras).
- `comparacao/lado-a-lado-{adventurer,ranger,mage}.png`, `lado-a-lado-luz-neutra.png`, `lado-a-lado-caminhada.png`.
- `comparacao/proporcoes-{mesmo-voxel,mesma-altura,perfil}.png`.
- Por direção (`a/`, `b/`, `c/`, `d/`): `cena-apresentacao.png`, `cena-panorama.png`,
  `cena-rumo-a-casa.png`, `personagens-4-angulos.png`, `criaturas.png`, `animacoes.png` (10 poses),
  `sem-texturas.png`, `luz-neutra.png`, `cena-sem-efeitos.png` e as 7 vistas avulsas.

## D. Avaliação técnica

Números e método em [resultados.md](../rodada-01-direcoes/resultados.md). Cena completa, 1920×1080, SSAA 1,5×, Iris Xe:
**A 52 FPS, B 42, C 91, D 29**. Testes: 11/11. Assets determinísticos (401 arquivos idênticos em
dois builds). Decisões: [ADRs](../../decisions/README.md). Limitações principais: sem greedy meshing,
LOD ou instancing; sombra sem cascatas; animação sem cotovelos/joelhos nem IK; cuboides sem AO nem
materiais. O teclado e o mouse não foram testados por uma pessoa — só o roteiro automático `--demo`.

## E. Avaliação artística

Critérios do proprietário: personagens pequenos e compactos, sem "humano de cubos"; mundo em que o
herói pareça um pequeno aventureiro.

1. **O problema dos personagens está resolvido nas quatro direções.** A causa era proporção: a
   2,3–2,6 cabeças, com pernas ≤ 30% da altura, o personagem lê como miniatura; a 3,5–4 volta a ser
   pessoa de cubos. A escala relativa (porta 1,5×, casa 4×, árvores 4–8× o herói) faz o resto.
2. **D tem os melhores personagens.** Com cabeça de ~20 voxels cabem olhos com íris e brilho, mechas
   de cabelo, capa com forro. É onde mais se "gosta do personagem" em close. Custa desempenho e
   tempo de modelagem.
3. **B tem a identidade mais própria.** Formas redondas, paleta quente, telhado curvo, contorno: é a
   que menos lembra outro jogo, e a cena é a mais convidativa.
4. **A é competente e a menos original** — a mais próxima das referências do gênero. Boa como base
   barata, fraca como identidade.
5. **C é a mais arriscada.** O herói funciona, mas vegetação em caixas e terreno em blocos
   texturizados puxam a leitura para "Minecraft com modelos melhores".

**Recomendação:** linguagem de forma, paleta e luz da **B**, com personagens na resolução da **D**
(~1/40 u) e os materiais da D (metal, emissivo, bloom contido). Ambiente em voxel de 1/8 u como B,
que custa metade dos triângulos da D. Ressalva: **essa combinação não foi renderizada** — é uma
inferência das duas direções lado a lado e precisa de um protótipo próprio antes de qualquer produção.

Não escolhi A (a mais próxima de Cube World) justamente por originalidade; nem D pura, pelo custo.

## F. Decisões que dependem do proprietário

1. Qual direção (ou combinação) segue. Se B+D, a próxima rodada começa por um herói híbrido.
2. Proporção final: 2,5 cabeças agrada? Olhar `comparacao/proporcoes-mesmo-voxel.png`.
3. Contorno a tinta (B): manter, suavizar ou tirar.
4. Modelagem por script × artista com editor voxel (exige instalar Goxel/MagicaVoxel).
5. Aceitar investir em greedy meshing/LOD antes de ampliar o mundo (obrigatório para D).
6. Animação: continuar procedural ou criar formato de keyframes com cotovelos/joelhos.

## Critérios de aceitação (seção 19 da missão)

| # | Critério | Estado |
|---|---|---|
| 1 | Protótipo compila e executa | atendido |
| 2 | Quatro direções implementadas | atendido |
| 3 | Comparação objetiva de proporções | atendido |
| 4–5 | Personagens compactos, não humanos realistas | atendido (maga C em 2,82, acima do alvo) |
| 6 | Cenário de fantasia consistente | atendido |
| 7 | Inspeção por vários ângulos | atendido |
| 8 | Animações básicas | atendido (6 clipes; limitações no item B/D) |
| 9 | Fontes editáveis | atendido (scripts + `.vox`); abertura em editor externo **não testada** |
| 10 | Documentação reproduz o processo | atendido; reprodução em outra máquina não testada |
| 11 | Capturas do protótipo real | atendido |
| 12–13 | Problemas identificados; avaliação crítica | atendido |
| 14 | Nada proprietário de Cube World | atendido (tudo gerado pelos scripts do repositório) |
| 15 | Sem erros críticos na demonstração | atendido no roteiro automático; uso manual não testado |

Sem commits, sem push, nada instalado no sistema.
