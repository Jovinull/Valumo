# Relatório da rodada 06 — primeira expansão controlada da Fábula Voxel

Data: 2026-11-17. Pacote: `output/visual-review/round-06/` (índice em `LEIA-ME.md`). Quatro assets novos,
nenhum sistema novo de jogo, nenhum commit. Os quatro são **candidatos**: não entram nas 22 referências nem
na clareira canônica sem a aprovação do proprietário.

## 1. Auditoria e pré-condição

- Linha de base (`round-06/linha-de-base/`): regeneração idêntica (898 arquivos), 45/45 testes, `--check`
  TODOS OK, 22/22 referências idênticas pixel a pixel → expansão liberada.
- **Errata encontrada:** a rodada 05.1 publicou "lâmina longa 14/47" cortes com interseção na interrupção por
  HIT; no estado entregue são **18/47** (a varredura citada era anterior à última mudança de pose). Corrigido
  em `problemas-conhecidos.md`, na matriz e com errata no relatório 05.1. As interseções continuam ABERTAS.
- **Correção documental:** `character-design` dizia, na seção histórica, que as medidas dos heróis valiam
  para humanoides; a descrição citava o olho 3 × 5 sem ressalva. Agora: medidas exatas = só protagonistas;
  NPCs pela faixa do contrato.

## 2. Os quatro assets

| # | Asset | Fonte editável | Categoria / parâmetros |
|---|---|---|---|
| 1 | Cartógrafa | `tools/npcs_e.py`, `assets/anim/cartografa.json` | NPC, 2,71 cabeças, 1,15 u, 5 184 faces, voxel 1/40 |
| 2 | Mariposa | `tools/creatures_e.py::mariposa` | criatura nível 1, 1/32, 2 320 faces, tipo `flutter` |
| 3 | Salgueiro | `tools/env_e.py::tree_willow` | árvore 1/8, 3,6 × 4 u, 5 722 voxels |
| 4 | Posto de trilha | `tools/env_e.py::trail_post` | estrutura 1/8, ~3,4 u de altura, 5 261 voxels |

Vitrine (não substitui a clareira): `assets/scenes/vitrine_e.json`, chave `layout=vitrine_e` (capturas e
bench). Integração no viewer: lineups `r6-npc`, `r6-criaturas`; NPCs em composições (`npcs`); tipo `flutter`.

## 3. Avaliação crítica por asset (escala 1–5; 5 = no nível do melhor asset aprovado)

### Cartógrafa (NPC)

| Critério | Nota | Comentário |
|---|---|---|
| Silhueta | 4 | coque lateral + tubo de mapas ao lado da cabeça dão contorno próprio; de perfil é a mais esguia do grupo |
| Originalidade | 4 | óculos no cabelo, lápis no coque, casaco de abas, bússola — nada repete os heróis |
| Consistência com E | 4 | placa plana, blush, sombra em bandas, paleta de rampas; lê como da mesma família |
| Modelagem | 3,5 | cabeça ainda um pouco quadrada de frente (gramática da placa); mãos e botas boas |
| Paleta | 4 | azul-ardósia + pergaminho + ocre + vermelho-terroso: distinta dos três heróis |
| Escala | 4 | 1,15 u ao lado de heróis de 1,18–1,25 u; adulta esguia, não "quarta classe" |
| Animação | 3,5 | idle e caminhada herdados funcionam; o gesto lê (consultar → apontar), mas é simples |
| Complexidade / desempenho | 4 | 5 184 faces (< aventureiro 6 286) |
| Reprodutibilidade | 5 | idêntica na cópia limpa |

Iterações: v1 capacete + óculos-goggles + cabeça-cubo; v2 barras verticais no rosto; v3 rosto bom, silhueta
retangular; v4/v5 coque e tubo fora do contorno; lápis refeito como linha 6-conexa. Comparação com os heróis:
`round-06/npc/comparacao-protagonistas.png`, `silhueta-com-protagonistas.png`.

### Mariposa

| Critério | Nota | Comentário |
|---|---|---|
| Silhueta | 4,5 | V de asas em coração: a mais diferente do bestiário; legível de frente graças à corda inclinada |
| Originalidade | 4 | inseto de quatro asas com ocelo e antenas plumosas; não remete a criatura conhecida |
| Consistência com E | 4 | rosto em placa com olhos ovais (sem olho de herói), blush, P1 em um voxel por asa |
| Modelagem | 3,5 | asas de 1 voxel são finas vistas exatamente de lado; corpo e cabeça bons |
| Paleta | 3,5 | rosa-poeira + pergaminho + ameixa; próxima do creme do pompom — distinta pela forma, não pela cor |
| Escala | 4 | criatura pequena (envergadura 1,06 u) |
| Animação | 4 | quatro clipes sem cintilação (medido); susto refeito depois de cintilar |
| Complexidade / desempenho | 4,5 | 2 320 faces (nível 1 ≤ 3 mil) |
| Reprodutibilidade | 5 | idêntica |

### Salgueiro

| Critério | Nota | Comentário |
|---|---|---|
| Silhueta | 4 | cúpula + cortinas em sino: inconfundível com o carvalho e o pinheiro (silhueta ortográfica) |
| Originalidade | 4 | gramática própria de copa pendente com vãos |
| Consistência com E | 4 | mesma pintura por normal e avesso escuro; verde-amarelado dentro da paleta |
| Modelagem | 3,5 | de perto as cortinas ainda são "fitas" regulares; à distância de jogo leem bem |
| Paleta | 4 | testado em dia, tarde e neutra (`arvore/luz-dia-tarde-neutra.png`) |
| Escala | 4 | ~4 u, entre o carvalho e o pinheiro |
| Desempenho | 3 | 6 180 faces expostas = 1,7× o carvalho → árvore de ACENTO, não de massa |
| Reprodutibilidade | 5 | idêntica |

Iterações: v1 galhos furando a copa + bola no topo + saia de abajur; v2 cortinas com vãos; v3 cúpula e sino.

### Posto de trilha

| Critério | Nota | Comentário |
|---|---|---|
| Silhueta | 4 | abrigo aberto de duas águas com placa-seta e lanterna; não é uma segunda casa |
| Originalidade | 4 | quadro de mapas e avisos liga o posto à cartógrafa |
| Consistência com E | 4,5 | telhado coral em fiadas, enxaimel nos oitões, pedra assentada, fora de prumo — mesma cultura da casa |
| Modelagem | 4 | fundos e laterais com contraventamento, barril, hera; nada liso |
| Paleta / materiais | 4 | os mesmos da casa; uma luz quente |
| Escala | 4 | menor que a casa (0,48× as faces), confortável ao lado do herói e do NPC |
| Desempenho | 4 | 5 898 faces |
| Reprodutibilidade | 5 | idêntica |

Iterações: v1 telhado de pagode e placa pequena; mãos-francesas e hera quebradas pelo cisalhamento.

## 4. Testes, validação e reprodução

- **Testes:** 55/55 — os 45 anteriores intactos + 10 em `ExpansionTest` (proporção do NPC na faixa e fora da
  dos heróis, partes e expressões, cabeça estruturalmente diferente, sem fragmentos, animações no validador e
  sem combate; mariposa nível 1/`flutter`/4 asas/P1 ≤ 3%/batida sem cintilação medida a 30 fps; salgueiro
  inteiro, dimensões, folhagem pendente; posto menor que a casa com uma luz; vitrine usa os novos e a clareira
  não). Contrato atualizado: tipo `flutter` (`contrato-de-assets.md` + `AssetContractTest`).
- **`viewer --check`:** TODOS OK (cartógrafa incluída). **`--occlusion`:** só a exceção conhecida do arco.
  **Interrupção por HIT:** nenhum teleporte; interseções ABERTAS (espada 7/21, lâmina longa 18/47).
- **Cena original preservada:** 22/22 referências idênticas pixel a pixel; heróis, criaturas antigas, árvores,
  casa e cerca idênticos byte a byte.
- **Reprodução (cópia limpa):** 928 arquivos de assets idênticos byte a byte; 55 testes; `--check` OK;
  referências e capturas dos assets novos idênticas pixel a pixel; fontes e metadados preservados
  (`round-06/reproducao/resumo.txt`). Nenhuma etapa não determinística encontrada.

## 5. Desempenho

Clareira: 158 draws / 367 504 triângulos (idênticos às rodadas anteriores), média 10,26–10,62 ms — sem
regressão. Vitrine (cena diferente, menos floresta): 49 draws, média 9,24 ms, mas p95 13,7–14,1 ms — causa
não isolada, registrada. Custo por categoria em `round-06/performance/resumo.md`: NPC, criatura e estrutura
mais baratos que os análogos; salgueiro 1,7× o carvalho.

## 6. Problemas que permanecem

Ver `problemas-conhecidos.md` (rodada 06) e matriz P23–P27: salgueiro mais caro (árvore de acento); p95 da
vitrine não explicado; NPC com poucos gestos; mariposa sem pouso/IA; interrupções de combate abertas;
os quatro assets são candidatos.

## 7. Documentação e skills

Atualizados: `visual-identity.md`, `contrato-de-assets.md` (NPCs, vitrine, `flutter`), `contrato-de-qualidade.md`
(sem mudança de critério), `especificacao-de-personagem.md` (§9 NPC), `guia-de-animacao.md` (rodada 06),
`regras-de-vegetacao.md`, `estilo-arquitetonico.md`, `pipeline-de-producao.md`, `problemas-conhecidos.md`,
`matriz-de-problemas.md`, `CLAUDE.md`. Skills: `character-design` (procedimento de NPC + correção), `voxel-art`,
`visual-qa`, `weapon-animation`, `rendering`. Ferramentas novas: `tools/review6.sh`, `tools/prop_silhouette.py`.

## 8. Gate da rodada (§20)

| Critério | Situação |
|---|---|
| Qualidade comparável aos existentes | sim nos quatro, com ressalvas registradas (notas 3–4,5; nenhum no nível dos melhores em todos os critérios) |
| Silhuetas distintas e originais | sim (silhuetas lado a lado) |
| Nenhuma recoloração | sim (cabeça do NPC testada estruturalmente; árvore e estrutura com gramática própria) |
| Direção E coesa | sim (vitrine, câmera de jogo, três presets) |
| Cena original preservada | sim (22/22) |
| Testes antigos passando / novos relevantes | sim (45 + 10) |
| Reproduzíveis / fontes editáveis | sim |
| Documentação correta / skills suficientes | sim |
| Sem defeitos visuais graves conhecidos nos novos | sim (os defeitos achados foram corrigidos; residuais são de severidade baixa) |

## 9. Recomendação

O pipeline **reproduziu a linguagem E em quatro categorias novas**, com defeitos encontrados e corrigidos pelo
próprio processo (silhueta ao lado dos heróis, auditoria de fragmentos, métrica de cintilação, comparação com a
casa). A qualidade ficou **boa, não excelente**: nenhum dos quatro supera o melhor asset aprovado da sua
categoria, e o salgueiro é caro.

Recomendo: **aprovar os quatro como candidatos de referência para as suas categorias** (se o proprietário
concordar após ver o pacote) e, numa próxima expansão, **um lote ainda pequeno (4–8 assets)** que repita o
método — não uma produção em escala. Antes de escala, resolver: custo de copas pendentes, o p95 da vitrine e
um conjunto de gestos de NPC reutilizáveis. Nada de armas novas com animação de combate até a rodada de
combate tratar as interrupções.

A decisão de aprovação é do proprietário; nada aqui foi declarado aprovado.

## 10. Adendo — expressões com atuação por personagem (pedido do proprietário)

O proprietário achou as expressões "cópia uma da outra". Confirmado nas capturas
(`round-06/expressoes/antes-16-expressoes.png`): alegre, determinada e surpresa eram o mesmo desenho nos três
heróis, e a surpresa virava um olho todo branco. Correção: `charlib_e.act_face` com três temperamentos
(`bold` aventureiro, `cool` exploradora, `soft` maga) — tabela em `especificacao-de-personagem.md` §2; a
cartógrafa ganhou uma determinada "avaliando" e alegre/surpresa mais abertas. Resultado:
`round-06/expressoes/depois-16-expressoes.png` e `antes-depois.png`.

- Rosto neutro dos três heróis idêntico byte a byte ao da linha de base (identidade aprovada preservada).
- Novo teste `QualityTest.expressionsActDifferentlyPerHeroInsteadOfOneTemplate`: **56/56** testes;
  `--check` TODOS OK; `--occlusion` só a exceção conhecida do arco.
- **Mudança em referência canônica, feita a pedido:** 06 (rosto alegre), 07 (rosto determinado) e as poses
  10, 11 e 12 (usam a expressão determinada) mudaram e voltaram a *candidatas*; as versões aprovadas
  anteriores estão em `docs/art/reference/anteriores/r051/`. As outras 17 continuam idênticas.

Avaliação honesta: as expressões agora têm personalidade e são distintas lado a lado; a da maga determinada é
a mais fraca (sutil à distância de jogo) e a exploradora "mira" pode ler como piscadela em tamanho pequeno.

## 11. Adendo — NPCs da vila: Kelyra, Índira e Súvia (pedido do proprietário)

Fonte: `tools/npcs_vila_e.py` (um construtor, três fichas), gesto `assets/anim/npc_vila.json` (acenar), cena
`assets/scenes/vila_e.json` (`layout=vila_e`), lineup `set=vila`, pacote `round-06/npcs-vila/`
(`tools/review_vila.sh`). Ficha de cada uma: `especificacao-de-personagem.md` §10. São **candidatas**.

Defeitos encontrados nas capturas e corrigidos: olho de uma linha (sonolento); sobrancelha engolida pela
franja; furo de pele no meio da franja; broche em "+" lendo como cruz; coração lendo como "Y"; cabelo da
Súvia todo acobreado; "+" claro na nuca (casco da cabeça entre as massas); três cabelos com a mesma forma
(cabeça Kelyra × Súvia 98% igual → 75%); aceno com a mão colada no rosto; expressões de duas personagens
pintando o mesmo desenho (mudada a atuação, não o limite do teste).

| Critério | Kelyra | Índira | Súvia | Comentário |
|---|---|---|---|---|
| Silhueta | 3 | 3 | 4 | as orelhas dão contorno próprio à Súvia; Kelyra e Índira de frente são dois blocos de cabelo longo parecidos (diferem de perfil, de costas e no valor) |
| Consistência com E | 4 | 4 | 4 | placa plana, blush, sombra em bandas, corpo na linguagem dos NPCs |
| Rosto / atuação | 3,5 | 4 | 4 | olho amendoado com delineado; atuações distintas; a sobrancelha alta da Kelyra fica em parte sob a franja |
| Roupa / paleta | 3,5 | 3,5 | 3 | Súvia muito escura: perde leitura em sombra |
| Animação | 3,5 | 3,5 | 3,5 | idle, caminhada e aceno; cabelo não atravessa as costas nos vídeos |
| Custo | 4,5 | 4,5 | 4,5 | 4 594–5 426 faces; as três somam ~0,2 ms na mediana (vitrine 10,94 → vila 11,15 ms, mesma sessão) |

Provas: 64/64 testes (8 novos em `VilaNpcTest`); `--check` TODOS OK (as três com aceno, caminhada, corrida
e transições); `--occlusion` sem rosto coberto nelas (só a exceção conhecida do arco); auditoria sem
fragmentos; regeneração idêntica byte a byte; 22/22 referências canônicas inalteradas pela entrada delas;
clareira e vitrine não mudaram (teste). Saídas em `round-06/npcs-vila/validacao/`.

Avaliação honesta: boas como população de vila, abaixo da cartógrafa (que tem item, bolsa e coque fora do
contorno). Recomendação se forem aprovadas: um item ou prop por personagem ligado ao papel delas na vila,
para separar as silhuetas de frente.
