# Relatório da rodada 07 — personalização modular dos protagonistas em tempo de execução

Data: 2026-11-17. Pacote local: `output/visual-review/round-07/` (índice em `LEIA-ME.md`). Sem commit nem push.
Nada aqui foi declarado aprovado; a decisão é do proprietário.

## 1. Auditoria do código existente

Linha de base (`round-07/linha-de-base/`): árvore limpa e sincronizada com o `Valumo` (o trabalho da 06.1 já
estava todo commitado); 1 106 assets regenerados deterministicamente; 73/73 testes; `--check` TODOS OK;
22/22 referências em 0/255.

| Achado | Consequência |
|---|---|
| Cabelo fundido na cabeça e em cada expressão (`head_with_expressions`) | separar os componentes na saída, não reescrever os geradores |
| Capuz C3 da exploradora = nó, fitas e pena + **faixa pintada sobre o cabelo** | o capuz vira geometria + regra de pintura; tirar o capuz restaura o cabelo |
| Chapéu da maga: parte própria, mas a maga é a única com cabelo desenhado para ele | variante `nativo` (maga) × `sob_aba` (outros) |
| Pele: cada herói tem a sua, mas `paint_face` deriva os decalques da `SKIN` global | a sonda de pele troca as duas |
| `VoxelMesher` aceita qualquer `VoxelSource`; `buildDataMerged` roda sem GL | grade em memória (`VoxGrid`) e testes de malha offline |
| Prova da 06.1 em Python, com cabeça e cabelo como malhas separadas | causa da costura de 0,28% e de +27% de faces |

## 2. Arquitetura do compositor Java

`Appearance` → `ComponentCatalog` → `Composer.compose`, na CPU:
- valida a aparência;
- recolore por canal;
- aplica a variante da cobertura e o assento;
- **funde** rosto, cabelo e capuz numa grade por expressão;
- copia o corpo canônico.

`Composer.build` faz `buildDataMerged`, cria a `Mesh` e envia à GPU. O cache é LRU por assinatura, e as malhas
são fechadas ao sair dele. Detalhes: `docs/architecture/personalizacao.md`.

## 3. Como os presets são preservados

O exportador refaz cada cabeça com o mesmo procedimento do gerador e confere por asserção contra o `head.vox`
canônico. O compositor sobrepõe na mesma ordem (rosto, cabelo, capuz) e malha com a mesma origem. Corpo,
chapéu e rabo vêm dos arquivos canônicos, que o compositor só lê (teste `canonicalAssetsAreNotTouchedByComposition`).

## 4. Comparação pixel a pixel

`tools/review7.sh gate`: preset recomposto (`custom=`) × rig canônico (`hero=`), sem tolerância. Para cada
herói, mesmas câmeras em:
- frente, perfil, costas e isométrica, em luz neutra e de dia;
- close nas 4 expressões, em neutra e de dia;
- câmera de exploração na clareira.

**51 pares, 0 pixels diferentes** (`round-07/presets/gate.md`). Os testes também conferem igualdade de voxels,
cores, malha, pivôs, pais, ordem de partes e as 4 expressões.

## 5. Correção da costura

A causa era a cabeça e o cabelo em malhas separadas: faces internas desenhadas e contorno na fronteira. A
correção funde as peças rígidas do mesmo referencial numa grade antes do meshing. O preset volta ao número de
faces e à imagem originais. O chapéu e o rabo continuam partes próprias porque têm pivô e movimento secundário.

## 6. Catálogo de cabelos

| ID | Leitura | Origem |
|---|---|---|
| `cabelo.topete` | topete compacto | h2 do aventureiro |
| `cabelo.curto` | curto e bagunçado | **novo** (casca 1,5, laterais e nuca curtas, tufos deitados) |
| `cabelo.chanel` | médio com franja | cabelo da maga |
| `cabelo.rabo` | preso, com rabo-de-cavalo | cabelo da exploradora + `tail` |

Decisão registrada: o `natural` (h1) ficou **fora**, porque media 82% do mesmo volume do topete (93% de
frente). Volume entre pares agora: 32–58%. Iterações do `curto`:
- v1: tufos espetados liam como chifres;
- v2: o lado da cabeça ficava careca.

Silhuetas e estilos em `round-07/cabelos/`.

## 7. Catálogo de cores

10 cores de cabelo (cobre, mel, lilás, castanho, preto, loiro, ruivo, cinza, musgo, anil) e 5 de íris. A
recoloração reaplica, canal a canal, as relações `shadow()` e `light()` do gerador, e a cor original não muda
nada (`round-07/cabelos/cores-de-cabelo.png`).

## 8. Tons de pele

5 tons (pêssego, bronzeada, clara, morena, escura). Os canais vêm da sonda na origem: pele do herói + `SKIN`
global, crânio, decalques do rosto e mãos da maga. Blush, olhos, boca e separação do cabelo legíveis nos
cinco, em luz neutra e de dia (`round-07/pele-e-rostos/`). Um close confirmou que a faixa vista numa miniatura
de pele escura era só sombreamento.

## 9. Opções faciais

Seleção pequena:
- **rosto inteiro** de qualquer herói (forma do olho, sobrancelha, boca e atuação nas 4 expressões);
- **cor da íris**;
- a sobrancelha acompanha a cor do cabelo.

Olho, sobrancelha e boca avulsos ficam como **[PLANEJADO]**.

## 10. Chapéus e capuzes

| | Resultado |
|---|---|
| Chapéu da maga | geometria oficial; assento automático (corrige a fresta e o chapéu flutuando); o cabelo só perde o que atravessa ou sai por cima; os quatro estilos testados de frente e de costas |
| Capuz C3 | separado da cabeça e idêntico no preset; com outro cabelo, a faixa é pintada pela mesma regra e o nó, as fitas e a pena entram |
| Regras | sem cobertura = cabelo completo; tirar a cobertura restaura; chapéu × capuz exclusivos (mesmo slot); `"X"` no catálogo bloqueia e informa |

Dois erros intermediários foram encontrados e corrigidos nas capturas:
- o corte "da aba para cima" deixava o crânio exposto atrás;
- o filtro de pedaços apagava mechas legítimas.

## 11. Interface

Modo **PERSONALIZACAO** (`Tab`), com painel de 9 linhas:
- setas: escolher linha e valor;
- `Backspace`: restaurar o preset;
- `F2`/`F3`: salvar e carregar;
- `X` e `Q`/`E`: expressão e clipe;
- arrastar e roda do mouse: girar e aproximar.

Mostra o custo (compor, malha, GPU, cache) e mensagens de erro. O roteiro `viewer --custom-demo` passa pelas
mesmas funções das teclas: 14 capturas, incluindo salvar, restaurar, carregar e um arquivo inválido
(`round-07/interface/`, `interface-roteiro.txt`).

## 12. Persistência

`valumo.aparencia` v1: identidade, origem, aparência e equipamento visual, só com o que difere do preset. A
validação dá mensagem clara para:
- contrato ou versão diferente;
- preset desconhecido;
- ID desconhecido ou de slot errado;
- cor inválida;
- combinação incompatível.

O viewer grava `output/aparencias/<preset>-NNN.json` e **nunca sobrescreve**. A migração entre versões está
documentada como planejada.

## 13. Animações

Validador TODOS OK. `CustomizationTest` valida idle, caminhada, corrida, HIT, EMOTE e o golpe do perfil em cada
preset e numa combinação carregada (outro cabelo, preto, pele morena, chapéu). Vídeos do original × personalizado
(idle, caminhada, corrida, golpe), maga com capuz correndo, exploradora golpeando e cena (`round-07/videos/`,
pranchas em `videos-pranchas/`).

## 14. Testes

**83/83.** Os 73 anteriores continuam intactos, e há 10 novos em `CustomizationTest`:

| Teste | O que confere |
|---|---|
| identidade dos presets | voxels, cores, pivôs, pais, ordem e expressões |
| malha idêntica | sem costura e sem faces internas |
| animação | presets e combinações |
| estilos distintos | 4 estilos com volumes diferentes |
| rampa | a cor preserva a rampa e a original não muda nada |
| fórmula de recoloração | sombra e luz |
| chapéu | assento, nada atravessando nem saindo por cima, variante só removendo |
| capuz | faixa e separação |
| persistência | round-trip e erros |
| assets canônicos | intocados |

Validador TODOS OK. Oclusão: só a exceção do arco. Interrupções: espada 7/21 e lâmina longa 18/47 com
interseção, **inalteradas e abertas**.

## 15. Benchmarks

Medidos com `viewer --custom-bench` sobre 39 aparências (`round-07/performance/personalizacao.md`):

| Etapa | Média | Máximo |
|---|---|---|
| Compor | 2,8 ms | 8,7 ms |
| Malha | 8,1 ms | 23 ms |
| Envio à GPU | 1,4 ms | 3,5 ms |

- **Memória:** 2,3–3 MB de vértices por aparência (com 3 cabeças de expressão extras).
- **Draws:** 13 por personagem, igual ao canônico.
- **Trocas:** 400 trocas com cache de 12 custaram 5,4 ms em média, e limpar o cache deixa **0 malhas e
  0 bytes**, sem vazamento.
- **Restaurar o preset:** 7,8 ms compondo do zero; 0,005 ms do cache.

## 16. Defeitos que permanecem

- A composição roda na thread do GL: até cerca de 35 ms na pior troca, um soluço perceptível ao trocar.
- Rostos só inteiros.
- NPCs e orelhas de fantasia fora do sistema.
- Só chapéu de aba e capuz C3.
- O topete sob o chapéu mostra um cacho atrás, sob a aba (plausível, mas é uma escolha).
- Interrupções de combate abertas.

## 17. Documentação e skills

Atualizados:
- `docs/architecture/personalizacao.md` (reescrito);
- `contrato-de-assets.md`, `contrato-de-qualidade.md`, `especificacao-de-personagem.md` §11, `pipeline-de-producao.md`;
- `problemas-conhecidos.md`, `README.md`, `CLAUDE.md`;
- skill `character-customization` (reescrita: criar cabelo, chapéu e cor, montar, validar, gate e defeitos já
  vistos).

## 18. Capturas e vídeos

`round-07/`: `presets/`, `cabelos/`, `pele-e-rostos/`, `chapeus-e-capuzes/`, `matriz/` (13 combinações),
`interface/`, `videos/` e `videos-pranchas/`.

## 19. Comandos

```bash
python3 tools/build_assets.py && ./gradlew :viewer:run          # Tab até PERSONALIZACAO
tools/snap.sh "mode=char,style=e,custom=maga,cabelo=cabelo.curto,corCabelo=e9c768,cobertura=capuz.c3,out=output/tmp/x.png"
viewer/build/install/viewer/bin/viewer --custom-demo output/tmp/demo
viewer/build/install/viewer/bin/viewer --custom-bench output/tmp/bench.md
tools/review7.sh gate                                            # presets × originais, pixel a pixel
```

## 20. Recomendação para a rodada 08

1. Compor fora da thread do GL e pré-compor o grupo da cena, para tirar o soluço de troca.
2. Rostos por partes (olho, sobrancelha, boca) com o mesmo método de sonda.
3. Capuz de tecido e capacete aberto como primeiras coberturas novas, com variantes desenhadas por estilo.

Roupas por região e combate continuam separados. Não comecei a rodada 08.
