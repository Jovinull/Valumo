# Problemas conhecidos — estado atual (rodada 05.1)

Fonte única do que falta ou não funciona. Severidade: **grave** (bloqueia aprovação de asset ou
expansão), **média** (visível na câmera de jogo), **baixa** (só de perto / cosmético). Rastreabilidade
completa (rodadas 01–05.1, com evidência verificada): [matriz-de-problemas.md](matriz-de-problemas.md).

## Abertos

| Problema | Severidade | Evidência | Próximo passo |
|---|---|---|---|
| Interrupção por HIT atravessa o corpo em algumas janelas: espada `slash_h` 7/21 cortes (continuação, 0,50–0,90 s), lâmina longa `long_combo` 18/47 (0,75–1,25 s e 1,50–1,80 s; errata da rodada 06: a 05.1 publicou 14/47, número anterior à última mudança do repouso) | média quando houver combate; nenhuma hoje (não há cancelamento) | `round-05-1/validacao/interrupcao-por-hit.txt`, vídeos `14–16` | saídas de interrupção autoradas por janela (ou janelas não canceláveis) na rodada de combate; nenhum teleporte em nenhum corte |
| Movimento secundário só onde há parte articulada (mochila e pontas do cachecol do aventureiro, trança da exploradora, chapéu da maga); cabelo do aventureiro, aljava e bolsas são fundidos ao tronco/cabeça | baixa | vídeos `round-05-1/videos/10–13` | articular só se o proprietário pedir (exige remodelar a parte); não simular |
| Sem acento de impacto (hitstop, tremor) | baixa | — | decisão da rodada de combate |
| Salgueiro custa ~1,7× um carvalho em faces expostas (6 180 × 3 620): as cortinas finas expõem mais faces | baixa (árvore de acento, não de massa) | `round-06/performance/` | auditado na 06.1: sem a roda interna cai para 5 094 faces (−18%), mesma silhueta, menos profundidade; ganho ~0,3% da cena → não aplicado; variante `tree_willow(inner=0)` disponível para plantio em massa |
| ~~Vitrine com p95 maior que a clareira~~ **RESOLVIDO na 06.1**: artefato de medição (aquecimento de 90 quadros × subida de frequência da GPU integrada, mais lenta em cena leve); em regime a vitrine é mais barata (p95 9,0–9,7 × 10,6–10,8 ms). Bench agora aquece 400 quadros | — | `round-06-1/performance/resumo.md` | nenhum |
| Cartógrafa: só idle, caminhada e um gesto; sem reação a HIT nem fala; cabelo e abas sem atraso secundário próprio (abas seguem as pernas) | baixa (escopo da rodada) | `round-06/videos/01–04` | gestos novos só quando houver diálogo/sistema de NPC |
| Mariposa: sem pouso nem IA (posição fixa na cena, voo em "oito" no lugar) | baixa (escopo) | `round-06/videos/05–06` | pouso/rota quando houver IA de fauna |
| Os quatro assets da rodada 06 são CANDIDATOS: não estão nas 22 referências nem na clareira | — | `docs/validation/relatorio-rodada-06.md` | aprovação do proprietário |
| NPCs da vila (Kelyra, Índira, Súvia) refeitas na 06.1 (papel na vila, silhuetas estruturais, Súvia legível na sombra) — continuam CANDIDATAS; de frente a sobreposição de área Kelyra × Índira ainda é 0,92 (corpo igual; o que as separa é cabeça, ombros, saia) | baixa | `round-06-1/npcs/` | aprovação do proprietário |
| Um gesto compartilhado de NPC (`acenar`), agora com atuação por personagem (postura e andamento); sem fala nem reação própria a HIT; sobrancelha "impressionada" da Kelyra fica parcialmente sob a franja | baixa | `round-06-1/videos/` | biblioteca de gestos de NPC |
| Expressões dos heróis refeitas com atuação por personagem (antes: mesmo carimbo nos três); referências 06, 07, 10, 11 e 12 mudaram e voltaram a CANDIDATAS (anteriores em `docs/art/reference/anteriores/r051/`) | — | `round-06/expressoes/antes-depois.png`, `especificacao-de-personagem.md` §2 | aprovação do proprietário |
| ~~Personalização só em Python, costura de 0,28%, +27% de faces, fresta do chapéu~~ **RESOLVIDO na 07**: compositor Java com cabeça fundida (gate 51/51 pares em 0 pixel), assento do chapéu, cor por canal | — | `round-07/`, `docs/architecture/personalizacao.md` | — |
| Personalização: rostos só inteiros (3), sem olho/sobrancelha/boca avulsos; NPCs e orelhas fora do sistema | baixa | `round-07/performance/personalizacao.md` | rostos por partes |
| ~~Composição na thread do GL (até ~35 ms por troca)~~ **RESOLVIDO na 07.2** no criador do jogador: thread de trabalho + descarte de obsoletos; pior quadro 23 ms nos cliques rápidos (o editor técnico continua síncrono) | — | `round-07-2/desempenho/` | — |
| Criador (07.2): nenhuma combinação do catálogo atual é incompatível, então "indisponível por incompatibilidade" só aparece nos testes (regra marcada em memória); a captura de opção indisponível mostra Restaurar sem nada a restaurar | — | `CreatorTest`, `round-07-2/capturas/18-*` | aparece sozinho quando o catálogo tiver uma regra `X` |
| Criador: o laço e a pena do capuz C3 são discretos de frente (o laço fica atrás); a miniatura usa 3/4 lateral para identificá-lo | baixa | `round-07-2/capturas/09-*`, `11-*` | só se o proprietário quiser o C3 mais visível (asset aprovado, não alterado) |
| Criador: poses Andar e Correr são no lugar (sem deslocamento) | baixa | vídeo `round-07-2/video/` | aceitável para prévia de aparência |
| Criador: a entrada real foi validada por roteiro na mesma fila de eventos do GLFW e por teste de fumaça da janela; o clique físico e uma tela HiDPI de verdade não foram exercitados nesta máquina (Wayland, sem automação de SO) | média até o proprietário testar à mão | `round-07-2/LEIA-ME.md` | teste manual do proprietário |
| Criador: em 720p o texto usa a escala 1× da fonte de pixel (pequeno, nítido); a fonte não tem tamanhos intermediários | baixa | `round-07-2/capturas/19-*` | segunda fonte maior, se o proprietário achar pequeno |
| Criador: abrir leva ≈ 2 s para montar a cena (mesmo custo da clareira) | baixa | `viewer --editor --demo` | tela de abertura, se incomodar |
| Personalização: só chapéu de aba e capuz C3; capacetes, capuz de tecido e roupas por região não existem | — | `docs/architecture/personalizacao.md` §5/§8 | rodada própria |
| Mariposa (auditoria 06.1): leitura lateral das asas adequada (V + corda inclinada); sem mudança | — | `round-06-1/auditoria/` | — |
| Lâmina longa: só a guarda (`guard_two_hands`) é de duas mãos; os cortes amplos são de uma mão | média | `round-05/auditoria/duas-maos-busca.txt` | redesenhar os cortes em torno da guarda assimétrica, se o proprietário quiser duas mãos nos golpes |
| Volumes de colisão aproximados: não provam ausência absoluta de clipping | método | `WeaponCheck` | manter a revisão visual multiângulo como obrigatória |
| IK aloca vetores temporários por avaliação | baixa (medido: animação inteira ~0,27 ms/quadro com 6 atores, ~3% do quadro) | `round-05/performance/` | otimizar só se muitos personagens animados pesarem |
| Água plana, sombra única sem cascatas, fonte do HUD de depuração sem acentos (a interface do jogador tem fonte própria com acentos desde a 07.2) | baixa | — | dívida técnica; fora da validação de assets |
| Linhas finas diagonais (frondes da tortuga, samambaia) contam como "voxels isolados" na auditoria | baixa (intencional) | `tools/asset_audit.py` | aceitas por desenho; justificar caso a caso |

## Corrigidos (resumo; detalhes nos relatórios)

| Problema | Rodada | Como |
|---|---|---|
| Lâmina longa entrando na silhueta / diante do rosto na guarda de duas mãos | 05.1 | repouso lateral em pé; entrada/saída pela frente; `--occlusion` |
| Arco "parado no ar" (girando de lado na mão) durante o HIT | 05.1 | direção do arco autorada acompanhando o antebraço; `--grip` |
| Cajado cobrindo o rosto na concentração e na volta da conjuração circular | 05.1 | cajado inclinado para fora; `circ_return` |
| Golpes sem reação secundária (mochila, cachecol, trança, chapéu) | 05.1 (parcial) | atraso sem estado em partes articuladas |
| Interrupção por HIT com teleporte da ponta | 05.1 | trajetória própria durante 0,30 s + peso contínuo |
| Armas atravessando cabeça/tronco/rosto (lâmina × cabeça 7,9 voxels) | 04 | coreografia por trajetória + IK de braço + `viewer --check` (ADR 0011) |
| HIT/EMOTE em FK atravessando a cabeça (cajado × cabeça 3,9; lâmina × cabeça 3,5) | 05 | gestos coreografados por perfil e validados |
| Segunda conjuração igual à primeira | 05 | conjuração circular (`staff_cast_wide`) + teste de diferença |
| Lâmina longa "bastão branco"; espada chapada; broquel pequeno; arco "pau" em repouso; cristal em bolha | 05 | equipamentos refeitos (dorso/corpo/fio, nervura, broquel 6,4, curva funda, cristal lapidado) |
| Voxels soltos (antebraços, pena da exploradora, aba da maga, broquel) | 05 | `prune_specks` + `connect_path`; teste `canonicalHeroesHaveNoStraySpecks` |
| Fundos da casa lisos, janela em degrau pelo cisalhamento | 05 | janela desenhada acompanhando o cisalhamento + enxaimel |
| Arquivos órfãos de versões antigas (orelhas da tortuga R02) | 05 | quarentena + checagem de órfãos |
| Rosto do fungo escondido pela aba; lumi fora de P1; listras dos morros | 04 | aba recuada; conta verde-água; tratamento de distância |
| Pés no ar/afundados em degraus | 04 | topo real do voxel + pegada + balanço que antecipa o pouso |
| Preset "tarde" oliva; frestas de T-junction; antisserrilhado | 03 | luz; margem anti-fresta; FXAA |
| Costas escuras, mantos atravessados, capa rígida, nuca chapada | 02 | ver relatório da rodada 02 |

## Limitações aceitas (comportamento conhecido, não é defeito)

| Limitação | Por quê |
|---|---|
| Duas mãos no mesmo cabo só numa família de poses (guarda assimétrica à esquerda) | braço 10,6 voxels e tronco de raio 6 na proporção chibi; medido por busca geométrica — é propriedade da implementação atual, não impossibilidade de armas de duas mãos |
| Arqueiro atira de lado (corpo girado ~63°), com puxada curta e flecha de 15 voxels | única forma de alinhar a flecha com braços chibi sem atravessar o peito |
| **Leitura do arco depende da câmera** (medido em `viewer --occlusion`, disparo completo): **perfil do lado do arco** (+90°) — melhor leitura, arco ≥ 50% visível em 100% do golpe, rosto livre; **frente e 3/4 frontal** (0°, ±40°) — arco 100% visível, mas a linha de mira cobre 31–39% do rosto durante a puxada (até 0,89 s a −40°); **perfil oposto** (−90°) — 67%; **3/4 de costas** (±140°) — ~52%; **diretamente atrás** (180°) — 41%: o corpo esconde o arco em mais da metade do disparo | consequência da arqueira de lado; na câmera de jogo padrão (frente-3/4) lê bem; com a câmera atrás do herói o tiro lê pela pose e pela flecha, não pelo arco |
| Arma erguida sempre AO LADO da cabeça, nunca por cima | o braço não alcança acima do crânio sem cruzá-lo |
| Desnível acima de 0,131 u entre os pés (degrau do relevo T1 = 0,125 u cabe): o pé alto não apoia | IK de dois ossos sem animação de subir/escalar |
| Personagem em T1 com degraus visíveis de 1/8 u | identidade voxel (T3 com chanfros gera dentes; rejeitado) |
| A–D e as variantes V1–V3 usam os ataques antigos | preservadas só para comparação histórica |

## Fora do escopo (não implementado por decisão)

Combate funcional (dano, hitboxes de jogo, IA), inventário, sistema de equipamentos, mana, progressão,
multiplayer, cancelamento de golpes, física de pano, capas longas, novos biomas/criaturas/personagens
(aguardam aprovação da expansão), editor de animação visual.
