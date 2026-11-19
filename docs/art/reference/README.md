# Referências canônicas da direção E

**O que é:** as imagens que definem o padrão de qualidade atual. Todas são **capturas reais do viewer**,
geradas por `tools/reference.sh`. Um asset novo é comparado com elas, na mesma configuração.
**Estado (rodada 05.1):** todas são **candidatas à aprovação final**; a aprovação artística é do
proprietário. A coluna `situacao` do `manifesto.tsv` diz quais mudaram na 05.1.

**Reprodução:** `tools/reference.sh` (saída aqui) ou `tools/reference.sh <outra-pasta>` (para comparar).
O `manifesto.tsv` guarda, por imagem: asset, fonte (scripts), perfil de animação, especificação exata da
captura (modo, câmera, luz, clipe, instante, resolução, SSAA), **hash SHA-256 das dependências** (fonte +
perfil: identifica a versão mesmo sem commit), testes que a cobrem, revisão do Git, data e situação. Configuração comum: direção E, qualidade PADRÃO (FXAA), SSAA
2× da captura, contorno e névoa padrão, relevo T1, assets gerados por `python3 tools/build_assets.py`.

| Imagem | Mostra | Câmera / luz | Regras que ela fixa |
|---|---|---|---|
| `01-proporcoes-herois-frente` | os três protagonistas | LINHA, frente, neutra | 2,35–2,60 cabeças, ≤ 1,25 u, corpo em pera |
| `02-personagens-isometrica-dia` | protagonistas | LINHA 3/4, dia | leitura de cor e material na luz do jogo |
| `03-personagens-costas`, `04-personagens-perfil` | costas e perfil | LINHA, neutra | cabelo em massa + sulcos; mochila/aljava; nada liso demais |
| `05-silhuetas-frente` | silhuetas chapadas | LINHA, silhueta | cada herói reconhecível em preto (H2, C3, M1) |
| `06`–`08` rostos | expressão por herói | close (`dist=1,3`, `ty`), dia | placa plana, olho 3×5, brilho do mesmo lado, sem nariz |
| `09-equipamentos-em-repouso` | todos os kits | LINHA 3/4, neutra | transporte da arma ao lado do corpo, sem cruzar o rosto |
| `10-espada-corte-horizontal-impacto` | espada no impacto | 3/4 alta, dia | lâmina com fio/corpo/sulco; arco à frente do corpo |
| `11-arco-puxado-perfil` | arqueira de lado | perfil, dia | flecha alinhada, corda em V, arco recurvo legível |
| `12-cajado-concentracao` | cajado e cristal | 3/4, dia | cajado ao lado (nunca acima do chapéu); cristal lapidado; P1 |
| `13-criaturas-escala` | pompom, lumi, fungo, tortuga | LINHA 3/4, dia | escala relativa e rostos próprios das criaturas |
| `14-floresta-carvalho-o3` | floresta | cena, dia | copa O3 (massas + avesso escuro + franja) |
| `15`/`16` arquitetura | casa frente e fundos | cena, dia | enxaimel, janelas emissivas, fora de prumo, fundos com acabamento |
| `17-cenario-exploracao` | clareira | câmera de exploração | composição, névoa depois do foco, relevo T1 |
| `18-cenario-panorama-terreno` | panorama | 26 u | tratamento de distância do relevo (sem listras fortes) |
| `19`–`21` luz | mesmo quadro | dia / tarde / neutra | presets oficiais |
| `22-equipe-camera-de-jogo` | equipe no cenário | câmera de jogo | **a escala em que o jogador vê os personagens** |

Animações de referência (vídeos, fora da documentação por tamanho):
`output/visual-review/round-05/videos/`. A rodada 04 guarda as anteriores, para comparação.

## Rodada 05.1 — o que mudou

| Imagem | Mudança | Motivo |
|---|---|---|
| `09-equipamentos-em-repouso` | lâmina longa em repouso **lateral, em pé, inclinada para fora** (antes: para cima e para trás, sobre o ombro) | a lâmina sumia atrás do braço/cabeça e a saída da guarda de duas mãos girava por trás do corpo |
| `10-espada-corte-horizontal-impacto` | mochila e pontas do cachecol com atraso secundário | movimento secundário (rodada 05.1) |
| `11-arco-puxado-perfil` | trança com atraso secundário | idem |
| `12-cajado-concentracao` | cajado inclinado para fora na concentração | o cristal cobria o rosto vista pela frente-direita |
| demais (01–08, 13–22) | ≤ 0,05% dos pixels (borda de sombreamento entre mochila/cachecol e tronco, agora partes separadas) | sem mudança visível |

Versões anteriores: as quatro alteradas em [anteriores/r05/](anteriores/r05/); o conjunto completo da
rodada 05 em `output/visual-review/round-05-1/referencias-r05/`.

### Rodada 08.1 — malha conforme e dentes

As 22 imagens foram refeitas com a **malha conforme** ([ADR 0014](../../decisions/0014-malha-conforme-sem-t-junctions.md)):
- **O que saiu:** a faixa onde faces coplanares se sobrepunham e as pontas de 0,06 voxel da margem
  anti-fresta.
- **Onde muda:** só as bordas entre cores e as silhuetas. De 0,2% a 2,3% dos pixels mudam mais que 25/255.
- **Rostos 06–08:** ficaram sem o serrilhado pontilhado nos olhos.
- **Referência 10:** também ganhou os dentes em marfim (cor `TEETH`, aprovada pelo proprietário).
- **Pontos claros isolados nas 22:** 165 → 61. O que sobra existe igual na malha exata sem fusão.

Comparação, diferenças e pranchas: `output/visual-review/round-08-1/referencias/`. As anteriores (conjunto
completo + manifesto) ficam em [anteriores/r08/](anteriores/r08/).

### Fechamento do QA (09.4) — repouso do cajado

Oito imagens (01–05, 08, 09, 22) foram refeitas porque a **pose de repouso do cajado da maga** mudou na rodada 09.4,
com aprovação do proprietário (cajado inclinado para fora e à frente, empunhadura mais alta), e as imagens não tinham
sido refeitas. Como foi conferido:
- o código final com o `assets/anim/staff.json` de antes da 09.4 reproduz as 22 versionadas com **desvio 0/255**: a
  única causa da diferença é o cajado;
- os pixels que mudam ficam só no cajado, na mão que o segura e na sombra dele;
- as outras 14 imagens saem idênticas, pixel a pixel.

As anteriores (as oito + o manifesto delas) ficam em [anteriores/r094/](anteriores/r094/).

## Reprovados (o que NÃO fazer)

Não são a direção oficial: são defeitos reais, corrigidos, guardados para ensinar o que evitar.

| Imagem | Defeito | Correção (onde) |
|---|---|---|
| `reprovados/r1-ataque-antigo-lamina-na-cabeca` | ataque por ângulos somados; a lâmina atravessa o crânio (volume vermelho) | coreografia por trajetória + validação (ADR 0011) |
| `reprovados/r2-cabelo-antenas-e-alternativas` | coluna R02: duas pontas finas na coroa leem como antenas | cabelo H2 (aprovado) |
| `reprovados/r3-lamina-longa-bastao-branco` | lâmina de um tom claro só: "bastão branco", sem fio nem dorso | dorso escuro + corpo + fio, curva (rodada 05) |
| `reprovados/r4-relevo-t3-dentes` | chanfro em todo degrau gera dentes nas bordas em zigue-zague | T1 + tratamento de distância |
| `reprovados/r5-fungo-rosto-escondido` | aba cobre o rosto na câmera alta de jogo | aba recuada, olhos 3 × 2 |
| `reprovados/r6-casa-fundos-lisos-janela-em-degrau` | parede lisa demais; janela desencontrada pelo cisalhamento | janela desenhada com o cisalhamento + enxaimel |
| `reprovados/r7-arco-mira-para-o-lado` | rodada 03: tronco gira e a flecha sai para o lado | arqueiro de lado com a flecha à frente |
| `reprovados/r8-lamina-longa-some-atras-do-braco` | rodada 05: repouso sobre o ombro; a lâmina fica escondida atrás do braço/cabeça e a saída da guarda gira por trás do corpo (passa no validador) | repouso lateral em pé + entrada/saída pela frente (05.1) |
| `reprovados/r9-arco-parado-no-hit` | rodada 05: o arco fica parado no mundo enquanto o braço recua — lê como arco virando de lado na mão | direção do arco acompanhando o antebraço (05.1) |
