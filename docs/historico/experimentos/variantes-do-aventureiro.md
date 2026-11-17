# Experimento: três variantes do aventureiro (rodada 02)

**Pergunta:** qual tratamento de personagem sustenta a direção B como base, com o acabamento da D,
sem perder o charme nem o custo controlado?

## Variantes

| | V1 — Storybook Refined | V2 — Storybook Fusion | V3 — Stylized Compact |
|---|---|---|---|
| Origem | aventureiro da B, refinado | linguagem nova: proporção e materiais da B + rosto/cabelo/equipamento da D | aventureiro da D, recompactado |
| Voxel | 1/32 u | 1/40 u | 1/44 u |
| Cabeças | 2,31 | 2,44 | 2,38 |
| Faces expostas | 3 860 | 5 902 | 7 508 |
| Rosto | olho 2 × 3 + sobrancelha e cílio novos | olho 3 × 5 em placa plana, 4 expressões | olho 4 × 5 da D |
| Cabelo | casca da B + nuca recortada + sulcos | massa + 6 mechas grossas + sulcos | casca + metade das mechas da D |
| Equipamento | o da B (mochila, saco de dormir, espada) | espada em folha, ombreira de couro, mochila, cachecol com nó | espada da D, ombreira de couro, capa em 2 segmentos |
| Código | `tools/chars_variants.py:v1` | `tools/chars_e.py:adventurer` | `tools/chars_variants.py:v3` |

## Procedimento (controlado)

Modo LINHA do viewer: as cinco figuras (B original, V1, V2, V3, D original) no mesmo chão, com a
mesma luz (preset "dia" da E), câmera tele de FOV 16°, mesma pose e mesmo tempo de clipe. Ângulos por
rotação dos **modelos**. Uma única variável por imagem. Capturas em `output/visual-review/round-02/personagens/`:

`variantes-frente`, `-perfil`, `-costas`, `-isometrica`, `-silhueta-frente`, `-silhueta-perfil`,
`-sem-textura`, `-luz-neutra`, `-tamanho-reduzido` (render real a 600 × 205 ampliado sem filtro),
`variantes-rostos`, `variante-v{1,2,3}-no-cenario` (mesmas câmeras da rodada 01),
`variantes-walk-8-quadros`, `variantes-attack-8-quadros`; vídeos `videos/variantes-caminhada.mp4`,
`variantes-ataque.mp4`.

## Avaliação (1 = pior, 3 = melhor entre as três)

| Critério | V1 | V2 | V3 | Evidência e comentário |
|---|---|---|---|---|
| 1. Personalidade | 1 | 3 | 2 | V2: topete + cachecol + ombreira + 4 expressões. V3 tem o olhar mais forte, mas é "personagem da D recolorido". |
| 2. Parece pequeno aventureiro | 2 | 3 | 2 | V1 lê infantil (2,31) e de "pijama"; V3 tem cabeça-capacete. |
| 3. Funciona em movimento | 2 | 3 | 2 | todas com pés plantados; V3 ganha a capa em 2 segmentos, mas a capa lê como "lençol mostarda" de costas; o topete da V2 dá vida à caminhada. |
| 4. Qualidade facial | 1 | 2 | 3 | olho 4 × 5 da V3 é o melhor de perto e à distância → a V2 **adotou** o olho alto (3 × 5) desta lição. |
| 5. Reconhecível à distância | 1 | 3 | 2 | silhueta: B/V1 são caixas idênticas; V2 é a única com recorte próprio. |
| 6. Combina com o ambiente B | 3 | 3 | 1 | V3 tem sombreado fino da D (8 degraus) ao lado da folhagem pintada em 4 tons. |
| 7. Fácil de expandir | 2 | 3 | 1 | V2 é uma biblioteca parametrizada (`charlib_e`); V3 depende de reescrever globais da D. |
| 8. Menos complexidade desnecessária | 3 | 2 | 1 | 3,9 / 5,9 / 7,5 mil faces. |
| **Soma** | **15** | **22** | **14** | |

## Decisão (proposta)

**V2 vence e virou a linguagem dos três arquétipos** (`characters/e/`). Ela incorporou da V3 o
olho alto; da V1, a simplicidade do sombreamento em poucos tons. A V3 confirmou que a capa articulada
em dois segmentos **funciona** tecnicamente (o segmento de baixo atrasa e nunca entra no corpo), mas
como desenho a capa grande domina as costas — fica documentada, fora dos heróis desta fase.

Riscos aceitos: V2 custa 50% mais faces que V1; o rosto de 1/40 u exige placa plana (regra
obrigatória). Decisão final é do proprietário.

## Defeitos encontrados durante o experimento (e corrigidos)

| Defeito | Onde | Correção |
|---|---|---|
| Padrão cruzado e "rosto" na nuca (12 mechas finas) | V2, 1ª versão | massa única + 6 mechas grossas + sulcos pintados |
| Lâmina atravessa a cabeça na antecipação | todas as direções | braço aberto (rz −0,8) na antecipação |
| Olho 3 × 4 pequeno à distância | V2 | olho 3 × 5 (lição da V3) |
| Comparação em 3/4 enganosa (perspectiva) | método | câmera tele fixa; girar os modelos |
