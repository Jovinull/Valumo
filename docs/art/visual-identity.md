# Identidade visual — direção E, "Fábula Voxel"

> **Estado:** a E é a direção **oficial aprovada** (rodada 02), com a V2 como linguagem dos
> protagonistas. **Aprovados na abertura da rodada 04:** cabelo **H2** do aventureiro, capuz **C3** da
> exploradora, chapéu **M1** da maga, carvalho **O3**, carapaça **S3** da tortuga, relevo **T1**
> (provisório: pode ser aprimorado sem mudar a identidade nem trazer defeitos como os dentes do T3) e a
> assinatura **P1** "sementes de luz" (uso muito moderado). A rodada 04 tratou a coreografia das armas:
> [guia-de-animacao.md](guia-de-animacao.md) e [../validation/relatorio-rodada-04.md](../historico/relatorios/relatorio-rodada-04.md).
> A **rodada 05** poliu equipamentos e animações e fixou o padrão em
> [contrato-de-qualidade.md](contrato-de-qualidade.md) e nas imagens canônicas de [reference/](reference/README.md)
> (recomendadas; aprovação do proprietário pendente).
> Evidências: `output/visual-review/round-02/` e `round-03/`; relatórios em
> [../validation/relatorio-rodada-02.md](../historico/relatorios/relatorio-rodada-02.md) e
> [../validation/relatorio-rodada-03.md](../historico/relatorios/relatorio-rodada-03.md).

Este é o documento de referência para criar qualquer asset novo (personagem, criatura, arma, árvore,
construção). Os guias complementares detalham cada tema; quando um número aparece aqui e lá, **o guia
complementar manda** (este documento só resume).

| Tema | Guia |
|---|---|
| Proporções (números, medição) | [sistema-de-proporcoes.md](sistema-de-proporcoes.md) |
| Construção de personagem (rosto, cabelo, roupa, equipamento, rig) | [especificacao-de-personagem.md](especificacao-de-personagem.md) |
| Paleta semântica e materiais | [paleta-e-materiais.md](paleta-e-materiais.md) |
| Vegetação | [regras-de-vegetacao.md](regras-de-vegetacao.md) |
| Arquitetura | [estilo-arquitetonico.md](estilo-arquitetonico.md) |
| Animação | [guia-de-animacao.md](guia-de-animacao.md) |
| Luz, névoa, contorno, qualidade | [presets-graficos.md](presets-graficos.md) |
| Produção de assets (passo a passo) | [pipeline-de-producao.md](pipeline-de-producao.md) |
| Revisão visual e aceite | [../validation/processo-de-revisao-visual.md](../validation/processo-de-revisao-visual.md), [../validation/checklist-de-aceitacao.md](../validation/checklist-de-aceitacao.md) |
| Contrato técnico (nomes, eixos, formatos) | [contrato-de-assets.md](contrato-de-assets.md) |
| Assinatura visual e revisão de semelhança | [assinatura-visual.md](assinatura-visual.md) |
| Critérios de aceite por categoria ([O]/[F]/[B]/[P]) | [contrato-de-qualidade.md](contrato-de-qualidade.md) |
| Imagens canônicas e reprovados | [reference/](reference/README.md) |
| Estudos da rodada 03 (histórico) | [../historico/experimentos/rodada-03-estudos.md](../historico/experimentos/rodada-03-estudos.md) |

## 0. Invariantes × parâmetros flexíveis

**Invariantes** definem a identidade: mudar um deles é criar outro estilo, e exige decisão do
proprietário e ADR. **Parâmetros flexíveis** são ajustados por asset ou por rodada, dentro da faixa,
sem consulta (registrar no guia do tema).

| Invariante (não muda) | Parâmetro flexível (faixa) |
|---|---|
| Herói com 2,35–2,60 cabeças, ≤ 1,25 u, voxel 1/40 u | cabeças exatas por personagem; altura de chapéu/capuz dentro de 1,25 u |
| Rosto em **placa plana**, sem nariz, brilho do lado da luz, 4 expressões | forma do olho (`round`/`almond`/`tilt`), sobrancelha, boca, sardas, cílio (`FACE_DEFAULT`) |
| Corpo em pera, mãos e botas maiores que o membro | largura de ombro/quadril ±1 voxel, comprimento de manga |
| Cabelo = massa + poucas mechas grandes + sulcos | número de mechas (3–6), topete, franja, rabo |
| Sombra pintada em **bandas largas** (3–4 tons, roxo → pêssego) | tons exatos dentro da família da paleta |
| Silhueta legível em preto na escala de exploração | acessórios, número de peças, assimetria |
| Voxel evidente (sem suavização de malha) | relevo T0–T4, chanfro de degrau de 1 voxel, margem anti-fresta |
| Névoa começa depois do foco; contorno discreto | início/densidade da névoa, peso do contorno (níveis 0–3) |
| Emissivo só em magia e habitação | intensidade do brilho, cor da luz por preset |
| Animação procedural por partes, sem física de pano, sem capa longa | ângulos, cadência, passada, IK de pé ligado/desligado |
| Originalidade (nada reconhecível de outra obra) | motivos de assinatura P1–P3 |

---

## 1. Visão artística

Um livro de fantasia ilustrado, construído em voxels: **pequenos aventureiros carismáticos num mundo
grande, quente e legível**. A atmosfera vem da B (cores quentes, formas redondas, casa fora de
prumo); o acabamento de personagem vem da D (olhos com íris e brilho, cabelo em mechas, camadas de
equipamento); a legibilidade vem da A (cores vivas, peças bem separadas); a disciplina técnica vem da C
(texturas nítidas, malhas enxutas). O cubo continua evidente — é voxel — mas nunca parece bloco de
construção genérico.

Frase de teste: *"Se um quadro do jogo fosse impresso numa página de livro infantil de aventura, ele
pareceria ilustrado por uma única pessoa?"*

## 2. Princípios visuais (em ordem de prioridade)

1. **Silhueta antes de detalhe.** Todo personagem e objeto importante é reconhecível em preto chapado
   (modo `K` do viewer). Se precisar de cor para ser identificado, a silhueta falhou.
2. **Pequeno aventureiro, mundo grande.** Herói ≈ 1,2 u; porta 1,5 u; casa 4,4 u; carvalho 4,5–5 u;
   árvore-marco 8–9 u.
3. **Redondo, não liso.** Massas são superelipsoides (p ≈ 2,2–2,7) construídas em degraus de voxel.
   Nada de malha suavizada; nada de caixa crua sem chanfro em organismo.
4. **Sombra pintada em bandas largas.** Cada material tem 3–4 tons (sombra arroxeada / base / luz
   pêssego) escolhidos pela normal do volume. Nunca ruído de 1 voxel.
5. **Detalhe em focos, descanso entre eles.** Rosto, mãos, arma e 1–2 acessórios por personagem;
   casa, poço, santuário, árvore-marco e lago no cenário. O resto descansa.
6. **Luz conta a história.** Sombras ancoram; emissivos só em magia e habitação (janelas, lanternas,
   cristais, runas).
7. **Original.** Nenhum elemento reconhecível de outro jogo; ver §19.

## 3. Proporções de personagens

Resumo (números completos em [sistema-de-proporcoes.md](sistema-de-proporcoes.md)):

| Medida | Valor | Teste |
|---|---|---|
| Cabeças de altura (sem chapéu/capuz/pontas) | **2,35–2,60**, alvo 2,45 | `AssetContractTest.consolidatedHeroesFollowTheIdentity` |
| Altura total com chapéu | ≤ 1,25 u | idem |
| Pernas | ≈ 25% da altura (11 de 44 voxels) | `meta.legFraction` |
| Voxel do herói | 1/40 u | idem |
| Cabeça | 16 × 16 × 15 voxels (crânio) + 2 de cabelo | `charlib_e.skull` |

Medidos: aventureiro 2,44; exploradora 2,53; maga 2,53. Corpo em pera (quadril mais largo que o
ombro), mãos e botas maiores que o membro, braços terminam no quadril.

## 4. Linguagem de formas

| Família | Forma | Exemplo |
|---|---|---|
| Personagem | pera + esfera grande; membros curtos e grossos | `charlib_e.torso`, `skull` |
| Cabelo/tecido | massa única + 3–6 mechas grandes (Bézier) que quebram o contorno | `hair_locks` |
| Vegetação | 3–4 massas achatadas com avesso escuro + franja de cachos pendentes | `env_e.crown_masses` |
| Pedra | superelipsoide cortado por planos, musgo só no topo | `envlib.rock` |
| Construção | caixa alinhada à grade, cisalhada 0,08 voxel/voxel, telhado côncavo | `env_b.house` |
| Magia | cristal emissivo abraçado por galhos, com aro dourado | `charlib_e.staff_fork` |

Regra de assimetria: cada personagem tem **um** elemento assimétrico forte (ombreira à direita do
aventureiro, capuz abaixado enrolado no ombro esquerdo da exploradora, ponta caída do chapéu da maga).

## 5. Rostos

Placa frontal **plana** (z = 6) — os traços são decalques nessa placa. Olho 3 × 5: cílio, brilho
(sempre do lado da luz, igual nos dois olhos), pupila 2 × 2, íris em três tons. Sobrancelha de
3 voxels no tom escuro do cabelo, 2 voxels acima do cílio. Boca de 2 voxels com cantos em pele escura.
Blush em L ao lado do canto externo do olho. **Sem nariz** (1 voxel vira ruído; testado na V1).

Expressões (cabeças alternativas no `rig.json`, trocadas pelo clipe ou pela tecla `X`): **neutra**,
**alegre** (olho fechado em ∩, boca aberta), **determinada** (pálpebra baixa, sobrancelha em V,
boca reta) e **surpresa** (branco grande, pupila pequena, boca em O). Prancha:
`round-02/arquetipos/expressoes.png`.

Variações por personagem (rodada 03, mesma gramática): aventureiro de olho `tilt`, sobrancelha
grossa e meio sorriso; exploradora de olho amendoado 3 × 4, sobrancelha fina e sardas; maga de olho
redondo com brilho de 2 voxels. Proibido: pálpebra escura permanente (lê como sono), sobrancelha
escondida sob a franja, sobrancelha em arco de pontos soltos. Prancha:
`round-03/personagens/rostos-expressoes-close.png` e `-escala-exploracao.png`.

## 6. Cabelos

Massa única (casca de 1,1–2,0 voxels em volta do crânio) + **3 a 6 mechas grandes** com pontas que
saem do contorno + franja de 2–3 mechas que emolduram o rosto + **sulcos pintados** nas costas
(`hair_strands`). Três tons, sem sombra profunda (vira faixa na testa) e uma faixa de brilho no topo
frontal para cabelos escuros/médios. Proibido: cabelo-capacete (casca lisa sem recorte), muitas mechas
finas cruzadas (padrão xadrez nas costas — defeito real encontrado e corrigido na rodada 02); pontas
finas e paralelas saindo da coroa (leem como **antenas** — defeito do aventureiro R02, corrigido no H2).

## 7. Vestimentas e equipamentos

Hierarquia de detalhe (a camada 1 tem que funcionar sozinha):

1. **Silhueta** — cabelo/chapéu/capuz, arma, mochila/capa curta.
2. **Grandes volumes** — túnica, manto, botas, luvas, mangas.
3. **Materiais e cores** — couro, tecido, metal dourado, aço.
4. **Identidade** — cachecol, ombreira, broche, aljava, bolsa.
5. **Ornamentos** — fivela, estrelas bordadas, costura.

Armas sempre modeladas na vertical com o punho no pivô; `meta.weaponMode` diz como o animador as
segura (`sword`, `staff`, `bow`). Capas longas **não** são usadas nesta fase (ver §17).

## 8. Paleta

Tabela semântica completa em [paleta-e-materiais.md](paleta-e-materiais.md). Regras: 3–5 matizes por
modelo; uma cor de destaque por personagem; sombra para o roxo, luz para o pêssego; sem preto nem
branco puros em áreas grandes; azul saturado só em magia, olhos e flores.

| Papel | Valor |
|---|---|
| Vegetação iluminada / base / sombreada / profunda | `#9bc064` → `#76ad47` → `#617e3d` → `#4c4f33` (rampa do carvalho) |
| Gramado (terreno) | `#7eab58` `#72a051` `#8bb65f`, seco `#9fbf66` |
| Terra / caminho | `#a47a58` / `#dcbd8c` `#d3b283`, borda gasta `#b8bd7c` `#c9bc86` |
| Madeira / casca | `#b07a48` `#84533a` / `#8f5d40` `#6f4336` |
| Pedra (lilás-quente) | `#b8aaa6` `#9a8a90` `#d2c6bc` |
| Telhado / estuque / porta | `#cb553f` `#a2403b` / `#f4e4c4` / `#3f8a8c` |
| Água | `#63b2c6` |
| Metal dourado / aço | `#f2c14e` `#c98f3a` / `#d2d8e2` `#9aa4b8` |
| Magia / luz habitada | `#7ff0dc` (ciano) / `#ffd77a` `#ff9340` |
| Destaques | coral `#e2644c`, mostarda `#eeaa3a`, rosa `#f2a0a6`, amarelo `#f4c653` |

## 9. Escala do mundo

| Elemento | Altura (u) | Voxel |
|---|---|---|
| Herói | 1,18–1,25 | 1/40 |
| Criatura pequena | 0,65–0,95 | 1/32–1/40 |
| Criatura grande (tortuga) | ~2,3 | 1/16 |
| Flor, tufo | 0,2–0,35 | 1/24 |
| Arbusto | 0,6–1,0 | 1/8 |
| Objetos de mão (barril, lanterna, placa) | 0,7–2,2 | 1/16 |
| Árvores, casa, pedras grandes | 4–9 | 1/8 |
| Terreno | degrau de 1/8 u, limiar irregular (T1) | 1/8 |

## 10. Vegetação

Resumo de [regras-de-vegetacao.md](regras-de-vegetacao.md): 7 árvores (4 carvalhos de silhueta
distinta, pinheiro, bétula, árvore-marco); copa dos carvalhos = 3–4 **massas** de base achatada com
avesso escuro e franja de cachos pendentes (O3, rodada 03); 4 tons por normal com manchas largas e fracas; plantas pequenas a 1/24 u em **touceiras** (`cluster` em `clearing_e.json`)
com espaços vazios entre elas; folhagem com vento afim (permite fundir faces).

## 11. Arquitetura

Resumo de [estilo-arquitetonico.md](estilo-arquitetonico.md): base de pedra, estuque claro com
enxaimel escuro, telhado côncavo coral em fiadas limpas (linha escura a cada 3 voxels), beiral largo,
cumeeira selada, chaminé torta, porta em arco com alpendre e escada de três degraus, janelas
emissivas com venezianas e floreira, uma luz quente e um toque vegetal por construção, cisalhamento de
0,08 voxel por voxel de altura.

## 12. Criaturas

Escala de complexidade reutilizável:

| Nível | Uso | Voxel | Rosto | Orçamento |
|---|---|---|---|---|
| 1 | criatura comum, inimigo simples | 1/32 u | olhos 2 × 2–3 com brilho | ≤ 3 mil faces |
| 2 | criatura de referência, mascote | 1/40 u (o do herói) | olho da E (3 × 5, íris) em placa plana | ≤ 6 mil faces |
| 3 | criatura grande, "paisagem viva" | 1/16–1/20 u | olho simples grande | ≤ 15 mil faces |

**Referência: pompom** (nível 2). Toda criatura usa a gramática do rosto da E (placa plana, brilho
do mesmo lado, blush) e no máximo 4 cores-base. Rodada 03: olhos de bicho no pompom (sem íris de
herói), **lumi** como broto-espírito flutuante com uma conta de luz, **fungo** refeito a 1/32 u com
chapéu-capuz e postura inclinada, **tortuga** com carapaça S3 (ecossistema próprio, sem árvore).
Criatura não copia o rosto do herói: olhos menores, sem sobrancelha.

## 13. Materiais

| Material | Índices de paleta | Onde | Regra |
|---|---|---|---|
| fosco | 1–175 | quase tudo | rampa de 3–4 tons |
| metal | 176–199 | lâminas, fivelas, guarda, ferragens | base de valor médio; nunca área grande clara (estoura) |
| emissivo | 200–219 | cristal, janela, lanterna, fogo, runa | área pequena, ponto focal |
| folhagem | 220–249 | copas, grama, musgo, broto | balança com vento afim |
| água | 250–255 | lago | reservado |

## 14. Texturas

A E é voxel puro com cor por voxel. Texturas de pixel art (técnica da C) ficam disponíveis via
cuboides texturizados, mas **não foram necessárias**: a placa de rosto plana resolveu a qualidade
facial que justificaria um decalque. Se um dia usadas: 1 texel = 1 voxel do modelo, filtragem
nearest, sem mipmap, nunca resolução maior para esconder modelagem.

## 15. Iluminação

Três presets reproduzíveis (`L` no viewer, `preset=` na captura), valores em
[presets-graficos.md](presets-graficos.md): **dia** (padrão), **tarde** (fim de tarde),
**neutra** (julgamento de modelo). Luz de preenchimento por trás (corrige as costas escuras da
rodada 01), sombra suave de 12 amostras de Poisson, névoa que **começa depois do foco**. O fim de
tarde é corrigido **pela luz** (céu ambiente azul, sombra menos amarela), nunca saturando tudo: o
critério é o matiz das copas fora da faixa oliva (60–75°).

## 16. Contornos

Padrão **discreto** (nível 1): peso 0,42, largura 1,1, normal 0,10; folhagem × 0,18; terreno × 0,30;
plantas pequenas × 0,25; some entre 8 e 30 u. Níveis 0–3 comparáveis com a tecla `O`
(`round-02/mundo/contorno-0-1-2-3.png`). O nível 3 é o contorno original da B.

## 17. Animação

Resumo de [guia-de-animacao.md](guia-de-animacao.md). **Armas (rodada 04):** golpes autorados como
**trajetórias** (posição da mão + direção da ponta + fio) em perfis de arma (`assets/anim/*.json`),
braços resolvidos por IK de dois ossos, validação espacial obrigatória (`viewer --check`); a arma nunca
cruza o rosto, a cabeça ou o tronco, nem na guarda. Locomoção: procedural por partes; **cotovelos e joelhos**
(antebraço e canela como partes filhas, com tampa de dobra) desde a rodada 03; pés plantados por
**IK analítico de 2 ossos** que segue o chão (terreno irregular); velocidade de deslocamento derivada da
passada (sem pé deslizando); cajado e arco compensam o giro do braço; manto em
duas metades que acompanham as pernas; capa com restrição (nunca entra no corpo) e segmento inferior
com atraso — mas **capas longas ficam fora dos heróis desta fase**: só mantos curtos/capuzes rígidos.

## 18. Qualidade mínima de um asset

Ver [../validation/checklist-de-aceitacao.md](../validation/checklist-de-aceitacao.md). Em resumo:
silhueta reconhecível em preto; legível na câmera de exploração (≈ 60 px de altura); costas e perfil
revisados; sem interseção visível nos 6 clipes; contagem de faces dentro do orçamento; capturas de
frente, perfil, costas, 3/4, sem cor e em luz neutra.

## 19. O que evitar

- Proporção humana; pernas longas; tronco estreito; "cabeça grande colada em corpo normal".
- Cabelo-capacete; mechas finas cruzadas; sombra profunda em cabelo claro.
- Ruído de 1 voxel; manchas aleatórias em telhado, estuque ou copa (a B tinha; foram retiradas).
- Folhagem em caixas (C); copa com dezenas de tufos pequenos (vira couve-flor).
- Contorno forte em tudo; névoa que vela o plano do herói; bloom que estoura metal.
- Capa longa atravessando pernas; arma atravessando cabeça/aba de chapéu.
- Qualquer elemento reconhecível de Cube World ou outro jogo (personagem, arma, criatura, interface,
  marca). Inspiração no **gênero**, nunca na obra. Esta análise não substitui avaliação jurídica.

## 20. Exemplos aprovados e reprovados

| Aprovado (seguir) | Reprovado (evitar) | Evidência |
|---|---|---|
| V2: topete em gancho + saco de dormir = silhueta própria | V1/B: cabeça-caixa idêntica em preto | `personagens/variantes-silhueta-frente.png` |
| Olho 3 × 5 em placa plana | olho decalcado em superfície curva (pompom 1ª versão: rosto fragmentado) | `criaturas/referencia-pompom.png` |
| Massa de cabelo + sulcos pintados | 12 mechas finas cruzadas nas costas | `personagens/variantes-costas.png` |
| Capuz com forro escuro enquadrando o rosto | forro claro: lê como tiara/capacete | `arquetipos/expressoes.png` |
| Névoa começando depois do foco | névoa desde a câmera (B: clareira lavada) | `mundo/comparacao-b-e-cena-panorama.png` |
| Contorno discreto | contorno forte nos degraus do terreno | `mundo/contorno-0-1-2-3.png` |
| Flores em touceiras com vazios | flores grossas espalhadas por toda superfície | `mundo/comparacao-b-e-cena-apresentacao.png` |
| Cajado abaixo da aba do chapéu | cristal atravessando a aba | `arquetipos/herois-isometrica.png` |
| Topete-onda que termina em cacho (H2) | duas pontas na coroa (antenas) | `round-03/personagens/estudo-cabelo-aventureiro.png` |
| Capuz abaixado com faixa, cabelo à mostra (C3) | capuz verde pontudo (arquétipo de outra obra) | `round-03/personagens/estudo-capuz-exploradora.png` |
| Aba menor com avesso escuro separando do cabelo (M1) | aba pesada colada ao cabelo | `round-03/personagens/estudo-chapeu-maga.png` |
| Copa em massas com franja de cachos (O3) | copa em novelos encaroçados (O2) | `round-03/vegetacao/estudo-carvalho-frente.png` |
| Degrau com limiar irregular (T1) | curvas de nível regulares (T0); rampa em cada degrau em zigue-zague (dentes) | `round-03/terreno/comparacao-casa.png`, `chanfro-dentes.png` |
| Lâmina com dorso escuro, corpo e fio claros, curva (rodada 05) | lâmina de um tom só ("bastão branco") | `docs/art/reference/reprovados/r3-*` |
| Janela desenhada acompanhando o cisalhamento, fundos com enxaimel | parede lisa; janela recortada em degrau | `docs/art/reference/16-*`, `reprovados/r6-*` |

Imagens oficiais atuais (capturas reais, reproduzíveis): [reference/](reference/README.md).

## Rodada 06 — primeira expansão controlada (candidatos, aguardando aprovação)

| Asset | Categoria | Fonte | Evidência |
|---|---|---|---|
| Cartógrafa | NPC humanoide (2,71 cabeças, olho 2 × 3, óculos no cabelo) | `tools/npcs_e.py`, `assets/anim/cartografa.json` | `output/visual-review/round-06/npc/` |
| Mariposa | criatura nível 1, tipo `flutter` (4 asas, ocelo com P1) | `tools/creatures_e.py` (`mariposa`) | `round-06/criatura/` |
| Salgueiro | árvore (copa pendente) | `tools/env_e.py` (`tree_willow`) | `round-06/arvore/` |
| Posto de trilha | estrutura pequena | `tools/env_e.py` (`trail_post`) | `round-06/estrutura/` |

Vitrine (não substitui a clareira): `assets/scenes/vitrine_e.json`, chave de captura `layout=vitrine_e`.
Relatório: `docs/validation/relatorio-rodada-06.md`.
