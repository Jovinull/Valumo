# Relatório da rodada 06.1 — refinamento dos NPCs, auditoria da expansão e preparação da personalização

Data: 2026-11-17. Pacote: `output/visual-review/round-06-1/` (índice em `LEIA-ME.md`). Nenhum NPC novo,
nenhum commit. Tudo aqui é **candidato**; a aprovação é do proprietário.

## 1. Auditoria inicial

Repositório local sincronizado com `github.com/Jovinull/Valumo` (`main`, 119 commits, árvore limpa); 64/64
testes, `--check` TODOS OK, links OK. Linha de base em `round-06-1/linha-de-base/` (hashes de 978 assets,
22 referências, commit).

## 2. O que mudou

| Item | Mudança | Fonte |
|---|---|---|
| Kelyra | estalajadeira: mecha varrida à frente do ombro esquerdo (parte `lock`), ponta em V, saia do casaco em sino, avental claro, chaves; atuação confiante | `tools/npcs_vila_e.py` |
| Índira | herbalista: cabelo rente com franja em cortina e trança de gomos (parte `lock`), mangas bufantes creme, jardineira índigo, avental de sálvia, bolsa de ervas, raminho de lavanda; atuação doce | idem |
| Súvia | mensageira: corpete vinho, mangas ardósia, gola e punhos claros, cinto e punhos de cobre, bolsa de cartas; cabelo, orelhas e expressões preservados; atuação travessa | idem |
| Cartógrafa | queixo afinado (cabeça deixa de ser quadrada de frente); o resto preservado | `tools/npcs_e.py` |
| Atuação | `meta.acting` (postura de repouso e andamento do gesto) + `Choreo.withTempo` | `Animator.java`, `Choreo.java` |
| Desempenho | bench: aquecimento 400, `dump=` (quadro, CPU, GC), `warm=` | `App.java` |
| Personalização | estudo + prova técnica | `docs/architecture/personalizacao.md`, `tools/custom_e.py` |
| Viewer | `--layout NOME` no modo interativo; lineups `trio` e `elenco`; título "Valumo" | `Main.java`, `App.java` |

Antes/depois com as mesmas câmeras: `round-06-1/npcs/antes/`, `depois/`, `comparacoes/`.

## 3. Avaliação crítica (1–5; 5 = no nível do melhor asset aprovado da categoria)

| Critério | Kelyra | Índira | Súvia | Comentário |
|---|---|---|---|---|
| Silhueta (em preto) | 3,5 | 4 | 4 | Índira: cabeça redonda + ombros bufantes; Súvia: orelhas; Kelyra: bloco longo + saia em sino — reconhecível, mas a mais "retângulo" das três |
| Cabelo | 3,5 | 4 | 4 | Kelyra deixou de ser bloco uniforme (mecha à frente, V atrás), ainda é uma massa escura grande; trança da Índira lê pelos gomos |
| Rosto / expressões | 3,5 | 4 | 4 | atuações distintas; sobrancelha "impressionada" da Kelyra some em parte sob a franja |
| Roupa / papel | 3,5 | 4 | 3,5 | avental e bolsa de ervas contam o ofício; chaves da Kelyra e bolsa da Súvia são pequenas |
| Valor / sombra | 3,5 | 4 | 4 | Súvia legível sob o salgueiro (Δ luminância manga × cabelo 6 → 41) |
| Animação | 3,5 | 3,5 | 3,5 | atuação sutil e distinta; ainda um gesto só |
| Custo | 4,5 | 4,5 | 4,5 | 4 176–5 506 faces |

Cartógrafa: continua a NPC mais completa (item, bolsa, coque e tubo fora do contorno).

## 4. Respostas

1. **Os três NPCs ficaram suficientemente distintos?** Sim em silhueta de perfil e de costas, e de frente
   pelos traços de cabeça/ombro/saia (`trio-silhueta-antes-depois.png`). A sobreposição de área de frente
   Kelyra × Índira ainda é alta (0,96 → 0,92) porque o corpo é o mesmo; de perfil caiu de 0,90 para 0,85.
   Não forcei o número: o teste mede as assinaturas do desenho.
2. **Aproximam-se da cartógrafa?** Índira e Súvia chegam perto; Kelyra fica um degrau abaixo (silhueta mais
   simples, objeto pequeno).
3. **Cartógrafa e demais assets precisam de mais ajustes?** Cartógrafa: queixo corrigido; nada mais
   demonstrado. Mariposa: leitura lateral adequada, sem mudança. Posto: sem defeito demonstrado. Salgueiro:
   ver 4. (`round-06-1/auditoria/resumo.md`)
4. **O salgueiro continua caro?** 1,7× o carvalho por árvore, mas ~0,3% da cena. Variante sem roda interna
   (−18%, mesma silhueta) existe como parâmetro; não aplicada porque perde profundidade por ganho desprezível.
5. **Causa do p95 da vitrine?** Artefato de medição: 90 quadros de aquecimento terminavam com a GPU integrada
   ainda a ~550 MHz; ela sobe para ~1 100 MHz depois de ~3 s, mais tarde em cena leve. Provado com CSV por
   quadro (sem GC, mesmos ângulos rápidos nas execuções seguintes) e traço de frequência. Com aquecimento de
   400, vitrine p95 9,0–9,7 ms × clareira 10,6–10,8 ms (`round-06-1/performance/resumo.md`).
6. **O pipeline de NPCs é escalável?** Sim para NPCs do mesmo esqueleto: uma ficha por personagem, testes de
   forma, valor e atuação, captura antes/depois por script. Limite: o esqueleto de NPC é um só; estatura
   diferente exigiria parametrizar o esqueleto (os perfis usam alvos absolutos de mão).
7. **O que pode ser customizado hoje?** Nos geradores: estilos de cabelo, chapéus, capuzes, rosto (olho,
   sobrancelha, boca, sardas, atuação), cores. Na saída: só a prova (`custom_e.py`) separa cabelo e chapéu;
   os heróis canônicos têm cabelo fundido na cabeça.
8. **Cabelo × chapéu?** Por variante: com aba, `sob_chapeu` corta só o cabelo das colunas cobertas da aba para
   cima (validado); capuz e capacete aberto precisam de variantes desenhadas; capacete fechado oculta o cabelo.
   Nunca deformar. Cada chapéu declara o assento (fresta achada na prova).
9. **Equipamento × roupa?** Regiões do corpo com `ocupa`/`oculta`; peça que substitui remove a base da região;
   peça que oculta pede a variante do componente oculto; sem variante = incompatível (matriz §4 do documento).
10. **Protagonistas como presets idênticos?** Sim em voxels (provado para o aventureiro nas 4 expressões, outras
    12 partes byte a byte). Em pixels, 0,28% muda pela costura entre malhas: o jogo precisa fundir as partes
    do mesmo pai depois de compor.
11. **A prova técnica funcionou?** Sim: 5 combinações (preset, cor, estilo, chapéu, as três juntas), sem mudar
    corpo nem proporção, com golpe e caminhada válidos (`ModularTest`, `round-06-1/modularidade/`).
12. **Adaptações antes do criador:** separar cabelo/capuz na saída dos geradores; composição em tempo de
    execução com fusão de malhas; paleta com canais recoloríveis; pele como parâmetro de `paint_face`;
    assento de chapéu; roupas por região com variantes de manga/cano.
13. **Testes novos (64 → 73):** `VilaNpcTest` (mecha presa ao tronco, assinaturas de silhueta, valor da Súvia,
    atuação sutil e própria), `ChoreoTest.withTempoScales…`, `ModularTest` (identidade do preset, cor × estilo,
    chapéu sem interseção e variante só removendo, animação válida).
14. **Defeitos que permanecem:** sobreposição frontal Kelyra × Índira alta em área; um gesto de NPC; objetos
    pequenos (chaves, bolsa de cartas); fresta do chapéu na prova; costura de malhas na personalização;
    interrupções de combate abertas (inalteradas); todos os novos são candidatos.
15. **Recomendação para a rodada 07:** (a) aprovar ou recusar os NPCs refeitos pelo pacote; (b) **composição em
    tempo de execução** mínima (fusão de partes por pai + canais de paleta) para os três presets, com a meta de
    referências pixel-idênticas; (c) separar o capuz da exploradora em componente; (d) dois a três gestos de NPC
    reutilizáveis. Combate (interrupções) continua separado.

## 5. Validação

| Prova | Resultado | Onde |
|---|---|---|
| Testes | 73/73 | `round-06-1/validacao/gradle-test.log` |
| `viewer --check` | TODOS OK (NPCs: gesto, transições, HIT, EMOTE) | `validacao/check.txt` |
| `viewer --occlusion` | só a exceção conhecida do arco | `validacao/oclusao.txt` |
| Auditoria voxel | sem soltos nem fragmentos (117 arquivos) | `validacao/auditoria-voxel.md` |
| Determinismo | duas gerações completas idênticas (1 106 arquivos) | — |
| Canônico | heróis, criaturas, ambiente e cenas idênticos byte a byte à linha de base; 22/22 referências 0/255 | `validacao/diferencas-de-assets.txt`, `validacao/referencias-canonicas.txt` |
| Links | 0 quebrados | `check_links.py` |
| Desempenho | ver resposta 5 | `performance/` |
