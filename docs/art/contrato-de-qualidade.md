# Contrato de qualidade dos assets (direção E)

Fonte de verdade para aceitar ou recusar um asset. Complementa o contrato **técnico**
([contrato-de-assets.md](contrato-de-assets.md): nomes, eixos, formatos) e a identidade
([visual-identity.md](visual-identity.md)). Evidência visual: [reference/](reference/README.md).

Cada regra tem uma classe:

- **[O] Obrigatória de identidade** — mudar exige decisão do proprietário + ADR.
- **[F] Parâmetro flexível** — ajuste por asset dentro da faixa, sem consulta (registre no guia).
- **[B] Boa prática** — o padrão; desviar exige motivo escrito.
- **[P] Preferência artística** — sujeita a aprovação humana caso a caso.

Medidas exatas (2,44 cabeças, olho 3 × 5, 1,18 u) são dos **três protagonistas aprovados** e não mudam.
Futuros NPCs humanoides herdam a **linguagem** da E, com variação autoral controlada (tabela abaixo).
Criaturas, espécies e estruturas seguem só as regras da sua categoria.

## Verificações comuns a todo asset (automáticas)

| Verificação | Comando | Critério |
|---|---|---|
| Geração determinística | `python3 tools/build_assets.py <módulo>` | mesmo hash que a referência (ver reprodução) |
| Estrutura voxel | `python3 tools/asset_audit.py assets/<pasta>` | 0 voxel solto e 0 fragmento < 4 nos heróis; nos demais, cada caso justificado (traço diagonal intencional, faísca) |
| Testes | `./gradlew test` | todos aprovados |
| Animações com arma | `viewer --check` | 0 falhas |

## Personagens — protagonistas canônicos (aventureiro, exploradora, maga)

Valores congelados (aprovados H2/C3/M1). Mudar qualquer um exige decisão do proprietário + ADR.

| Regra | Classe | Valor / critério | Verificação |
|---|---|---|---|
| Proporção | [O] | 2,35–2,60 cabeças, altura ≤ 1,25 u, pernas ~25% | `AssetContractTest.consolidatedHeroesFollowTheIdentity` |
| Densidade | [O] | voxel 1/40 u | idem |
| Rosto | [O] | placa plana, olho 3 × 5 (ou variação de `FACE_DEFAULT`), brilho do mesmo lado, sem nariz, 4 expressões | close `ref 06–08` |
| Silhueta | [O] | reconhecível em preto de frente, perfil e costas; um elemento assimétrico forte | `sil=1` |
| Paleta | [O] | a aprovada de cada um (luz neutra `ref 21`) | luz neutra |
| Cabelo / capuz / chapéu | [O] | H2, C3, M1 como estão | costas `ref 03` |
| Equipamento preso ao corpo | [B] | não cruza o rosto em nenhum ângulo; mochila/aljava nas costas com volume `back` | 5 ângulos + `viewer --occlusion` |
| Rig | [O] | bípede articulado (`farm_*`, `shin_*`), itens filhos do antebraço, `meta.hands`, `meta.volumes`, `meta.weaponProfile`; partes secundárias opcionais (`tail`, `hat`, `pack`, `scarf`) filhas do tronco/cabeça | `ChoreoTest`, `QualityTest`, `AnimationQaTest` |
| Pivôs | [O] | ombro (±6,5; 23,5), cotovelo (±7,45; 17), mão (±7; 13; 1), quadril (±3,5; 11) | `rig.json` |
| Animação | [O] | locomoção procedural; ações e HIT/EMOTE por perfil (`assets/anim`) | `viewer --check`, `--occlusion`, `--grip` |
| Faces | [B] | ≤ 9 mil faces expostas | `meta.faces` |
| Capturas obrigatórias | [O] | frente, perfil, costas, 3/4, silhueta, luz neutra, câmera de exploração, vídeo das ações | `tools/review5.sh`/`review51.sh` ou equivalente |

## Personagens — NPCs humanoides futuros

**Regra de origem [O]:** um personagem pode ser inspirado em pessoa real, desde que a pessoa tenha autorizado
(responsabilidade do proprietário, registrada fora do repositório). O repositório guarda **só o modelo**:
nunca fotos, nome real ou apelido, links, pranchas, textos descrevendo a referência, nem metadados, comentários,
nomes de arquivo ou mensagens de commit que liguem o modelo à pessoa. O personagem tem nome próprio de
fantasia e é documentado só pelo que ele é no jogo (forma, paleta, roupa, atuação). Corpo sempre na
linguagem da direção E (tronco em massa única, sem ênfase em busto ou decote profundo). Se a autorização for
retirada, o personagem sai do jogo. Procedimento: skill `character-design`.

O que mantém a família visual (obrigatório) e onde o autor tem liberdade (flexível). Nenhum NPC existe
ainda; os testes de proporção dos protagonistas **não** se aplicam a eles (ganharão teste próprio com o
primeiro NPC aprovado).

| Regra | Classe | Valor / critério |
|---|---|---|
| Proporção | [O] faixa / [F] valor | **2,0–3,0 cabeças** (regra do projeto); dentro disso o autor escolhe: criança ~2,0, adulto robusto ~2,3, idoso curvado, alto e esguio ~2,9. Altura livre até 1,5 u (mais alto que os heróis só com motivo de história) |
| Densidade | [O] | voxel 1/40 u (mesma escala dos heróis) |
| Construção | [O] | corpo em massas arredondadas (superelipsoides), mãos em bloco, pés curtos; nada de proporção realista |
| Rosto | [O] linguagem / [F] forma | **sempre placa plana**, brilho do olho do lado da luz, sem nariz desenhado, blush; **forma do olho livre** dentro de `FACE_DEFAULT` e variações novas (redondo, amêndoa, inclinado, olhos fechados de idoso, olho menor 2 × 3, sobrancelha grossa), desde que leia como da mesma família na câmera de exploração |
| Expressões | [B] | 4 (neutra, alegre, determinada, surpresa) para NPCs com diálogo; figurantes podem ter 1–2 |
| Silhueta | [O] | distinta dos três protagonistas em preto (não pode ser confundido com um deles) |
| Cabelo | [O] massa + sulcos / [F] forma | massa principal + 3–6 mechas grossas; proibido antena/capacete; forma, comprimento e cor livres |
| Paleta | [F] | 3–5 matizes, 1 destaque; rampas `P()`; sem preto/branco puros em área |
| Rig | [O] | o mesmo bípede articulado (para reaproveitar locomoção e perfis); pivôs podem mudar com a proporção, registrados em `meta` |
| Aprovação | [P] | primeiro NPC de cada tipo passa por aprovação do proprietário ao lado de `ref 01` |

## Criaturas

| Regra | Classe | Valor / critério |
|---|---|---|
| Nível de complexidade | [O] | 1 comum (≤ 3 mil faces, 1/32 u), 2 referência (≤ 6 mil, 1/40 u), 3 grande (≤ 15 mil, 1/16–1/20 u) |
| Anatomia | [F] | livre (quadrúpede, saltador, flutuante, outra); escala relativa ao herói conforme `ref 13` |
| Rosto | [O] | gramática da E (placa plana, brilho do mesmo lado, blush), **sem** copiar o olho de herói; olhos menores, sem sobrancelha |
| Silhueta | [O] | identificável em preto; a forma da espécie vem de uma massa principal (chapéu do fungo, carapaça da tortuga) |
| Materiais | [B] | ≤ 4 cores-base; emissivo só como acento (P1 ou luz própria) |
| Animação | [B] | ciclo próprio por tipo (`hopper`, `floater`, `quadruped`); nada de clipping de partes em 5 ângulos |
| Cenário | [O] | legível na câmera de exploração (rosto visível de cima: lição do fungo) |
| Originalidade | [O] | sem semelhança com criaturas de outras obras (revisão de `assinatura-visual.md`) |

## Vegetação

| Regra | Classe | Valor / critério |
|---|---|---|
| Biblioteca | [O] | uma espécie nova só entra com silhueta distinta das 7 em preto |
| Carvalhos | [O] | copa O3: 3–4 massas achatadas, avesso escuro, franja de cachos, tronco e galhos visíveis |
| Pintura | [O] | 4 tons por normal, manchas largas e fracas (nunca ruído de 1 voxel) |
| Densidade | [F] | árvores 1/8 u; plantas pequenas 1/24 u em touceiras com vazios |
| Variação | [F] | variantes de cor/altura por espécie; ≤ 1 acento quente em ~9 árvores |
| Renderer | [O] | folhagem com `affineWind` (fusão exata de faces); traços finos de plantas podem ser diagonais |

## Arquitetura

| Regra | Classe | Valor / critério |
|---|---|---|
| Escala | [O] | porta 1,5 u, casa ~4,4 u, herói ~1,2 u |
| Materiais | [O] | base de pedra, estuque claro + enxaimel escuro, telhado coral em fiadas (linha escura a cada 3 voxels) |
| Formas | [O] | fora de prumo (cisalhamento 0,08 voxel/voxel), telhado côncavo, beiral largo |
| Portas e janelas | [O] | janela emissiva com moldura + travessa, desenhada **acompanhando o cisalhamento** (recorte em degrau = defeito) |
| Todas as faces | [O] | nenhuma parede lisa sem enxaimel/abertura: frente, lados e **fundos** recebem acabamento |
| Detalhes | [B] | uma luz quente e um toque vegetal por construção; ≤ 1 elemento com borda em folha (P3) |
| Terreno | [O] | base de pedra apoiada no relevo, escada até a soleira |

## Armas e itens segurados

| Regra | Classe | Valor / critério |
|---|---|---|
| Modelagem | [O] | VERTICAL, +Y = ponta, +X = fio, pivô no centro da mão |
| Leitura | [O] | três tons por seção (fio claro, corpo médio, dorso/sulco escuro); nada de "bastão" de um tom |
| Escala | [B] | espada ~27 voxels da mão à ponta; escudo ≥ 12 voxels de diâmetro; flecha ≤ 15 voxels (proporção chibi) |
| Volumes | [O] | `*_volumes()` seguindo a geometria real; atualizar quando a geometria mudar, **nunca** a tolerância |
| Perfil | [O] | `assets/anim/<arma>.json` com repouso, transporte, guarda e golpes com fases |
| Trajetória | [O] | arco amplo à frente do corpo; preparação passa pela lâmina em pé; nada cruza cabeça/rosto/tronco |
| Câmera de jogo | [O] | golpe legível na câmera de exploração (`ref 22`, vídeos) |
| Rosto livre | [O] | `viewer --occlusion`: rosto coberto < 25% (ou só por ≤ 0,10 s) em todas as câmeras; exceção documentada: linha de mira do arco |
| Empunhadura | [O] | `viewer --grip`: o item acompanha o antebraço (sem "ITEM PARADO NO MUNDO"). O Δpunho absoluto **não** é limite (a amarração item/antebraço é arbitrária: golpes aprovados chegam a 110°+); valor alto pede close em vídeo da mão: guarda acima do punho, pomo abaixo, sem o item "trocar de lado" no punho |
| Arma visível | [B] | lâmina/arco ≥ 60% visível nas câmeras de frente e 3/4 durante guardas e repouso |
| Ornamentação | [P] | moderada; gemas e brilho são preferência sujeita a aprovação |

## Terreno, luz e efeitos

| Regra | Classe | Valor / critério |
|---|---|---|
| Relevo | [O] | T1 (degrau de 1/8 u, limiar irregular); T3 só como estudo |
| Distância | [F] | tratamento de faces verticais 12–32 u (`StyleProfile.terrainFar*`) |
| Luz | [O] | presets dia (padrão), tarde, neutra; névoa depois do foco |
| Efeitos | [O] | P1 com moderação (≤ 1 por objeto); efeitos nunca escondem animação ruim |
