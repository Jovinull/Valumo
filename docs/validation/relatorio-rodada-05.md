# Relatório da rodada 05 — auditoria de excelência, polimento final e consolidação do pipeline

Data: 2026-11-16. Máquina: [ambiente.md](ambiente.md). Pacote de evidências:
`output/visual-review/round-05/` (índice em `LEIA-ME.md`). Nenhum asset de conteúdo novo foi criado;
nenhum commit foi feito.

## 1. Resumo da auditoria

Linha de base gravada antes de qualquer mudança (`round-05/linha-de-base/`: git, hashes dos 880 assets,
33 testes, validador TODOS OK). A auditoria combinou três fontes:

- **estrutural** (`tools/asset_audit.py`, novo): 142 `.vox`, **33 com voxels soltos ou fragmentos**,
  14 deles em heróis e rigs r4 (antebraços, pena da exploradora, aba da maga, broquel); depois: **0** nos
  heróis, e os 19 restantes do ambiente/criaturas justificados caso a caso
  (`auditoria/fragmentos-justificados.md`) — dois deles eram defeitos e foram corrigidos (casa, cerca);
- **espacial** (`viewer --check`): os gestos HIT/EMOTE não eram validados e ainda usavam FK; ao incluí-los,
  apareceram lâmina × cabeça 3,5 voxels, cajado × cabeça 3,9 e arco × tronco 3,5;
- **visual** (capturas e vídeos do viewer): lâmina longa lendo como "bastão branco", espada chapada,
  broquel pequeno demais na câmera de jogo, arco reto em repouso, cristal em bolha, fundos da casa lisos
  com janela em degrau, segunda conjuração quase igual à primeira, guarda de duas mãos passando a lâmina
  diante do rosto (vista de frente).

## 2. Assets refinados (sem criar conteúdo novo)

| Asset | Defeito | Correção | Onde |
|---|---|---|---|
| Espada | lâmina chapada, um tom | fio / corpo / sulco em três tons, nervura em relevo, guarda com avesso escuro, cabo enfaixado, pomo redondo | `charlib_e.sword` |
| Lâmina longa | "bastão branco" | um fio só: dorso escuro, corpo médio, fio claro; curva para trás; ponta em folha; guarda oval de bronze, colar, cabo cruzado; volumes refeitos (2 segmentos curvos) | `charlib_e.longblade`, `longblade_volumes` |
| Broquel | pequeno, chato | raio 6,4, aro de aço, tábuas em dois tons, centro abaulado, umbo, 4 rebites, folha dentro da face; volumes atualizados | `charlib_e.buckler`, `buckler_volumes` |
| Arco | reto em repouso ("pau") | curva recurva mais funda; ventre claro × dorso escuro | `charlib_e` (arco) |
| Cajado | cristal em bolha, P1 forte | cristal lapidado com brilho pontual; pontas da forquilha em bronze | `charlib_e.staff_fork` |
| Heróis | voxels soltos | `prune_specks` em todas as partes; pena com `connect_path` | `charlib_e.finish`, `chars_e` |
| Casa (E) | fundos lisos; janela em degrau; folha flutuando | janela de dois vidros acompanhando o cisalhamento, venezianas, floreira, prumos e mãos-francesas; folha solta removida | `env_e._house_back`, `env_e.house` |
| Cerca (E) | trecho da régua, folha e flor soltos | peças ligadas | `env_e._fence` |
| Tortuga | 2 `.vox` órfãos da rodada 02 | quarentena | `output/quarentena/rodada-05/` |

Critério mantido: H2, C3, M1, proporções, silhuetas, paletas e rostos **não** mudaram (referências 01–08).

## 3. Comparação visual antes/depois (configurações equivalentes)

- Equipamentos: `round-05/equipamentos/antes/` × `depois/` (mesma spec), `antes-depois-closes.png`.
- Casa e cerca: `round-05/ambiente/casa-fundos-antes-depois.png`, `cerca-antes-depois.png`.
- Movimento: `videos/30-r04-x-r05-*.mp4` (esquerda = vídeo entregue na rodada 04, direita = mesma spec
  hoje) e `videos/31-antes-depois-{hit,emote}-*.mp4` (FK antigo × gesto coreografado).
- Galeria de reprovados: `docs/art/reference/reprovados/` (r1–r7).

## 4. Animações

Arquitetura da rodada 04 preservada (perfis + IK + validador); só autoria e três mecanismos pequenos:

| Item | Resultado |
|---|---|
| Espada | corpo participa: agacha (quadril 0,83), avança até 2,6 voxels, tronco −46° na preparação; antecipação segurada ~0,05 s; mãos levadas para fora/frente onde o pomo encostava |
| Lâmina longa L1→L2→L3 | transferência de peso contínua (raiz −0,4 → +2,2 → +2,8) |
| Lâmina longa, duas mãos | busca geométrica exaustiva (2,7 mi configurações; 2 054 viáveis): só uma família assimétrica funciona (tronco +20..+40°, mãos no peito esquerdo, lâmina à frente-direita). Implementada como `guard_two_hands`; cortes amplos seguem de uma mão. A pose de passagem foi afastada do rosto nesta rodada (`two_up` x −3); de frente a lâmina ainda passa rente ao lado direito do rosto por ~0,2 s. É **propriedade da implementação atual**, documentada como tal |
| Arco | `drawn_full` com tensão segurada, recuo mais forte, curva visível em repouso; flecha alinhada; de frente o arco ainda fica diante do rosto (limitação aceita) |
| Cajado | segunda conjuração circular (baixo → trás → alto → recolhe → solta) — diferença média do cristal > 5 voxels (teste) |
| HIT/EMOTE | gestos por perfil nos 4 kits (canal `lift` para o pulo); 0 interseções |
| Impacto | sem hitstop e sem partículas extras: antecipação segurada + `snap` + continuação com `stop` |
| Transições | crossfade de locomoção (`Motion.fromClip`), `Pose.lerp` pelo menor ângulo |

Revisão: 35 vídeos em velocidade normal, 5 na câmera de jogo (`videos/20–24`). Observação honesta: na
câmera de jogo o herói ocupa cerca de 1/8 da altura do quadro; os golpes de espada e lâmina longa leem, a
conjuração lê pelo brilho, mas o arco disparado em direção à câmera lê pouco — a leitura do arco depende
do ângulo.

## 5. Validação geométrica

`viewer --check`: **TODOS OK** em todos os golpes, guardas, transportes, sequências, HIT e EMOTE dos
quatro kits (`round-05/validacao-check.txt`/`.csv`). Nenhuma tolerância foi alterada; onde a geometria
mudou, os `*_volumes` foram refeitos para seguir a peça real. Volumes são aproximados: a revisão visual
multiângulo continua obrigatória.

## 6. Testes

39 testes, 0 falhas (eram 33). Novos em `QualityTest`: heróis sem voxels soltos; lâmina longa com ≥ 3 tons
e contraste dorso/fio; broquel ≥ 12 voxels; HIT/EMOTE coreografados; segunda conjuração distinta; guarda
de duas mãos com as duas mãos no cabo. Os mesmos 39 passam numa cópia limpa.

## 7. Benchmarks

Mesmas specs da rodada 04, execuções intercaladas, controle `r03-equivalente` para medir a deriva da
sessão (`round-05/performance/resumo.md`). 1080p, qualidade PADRÃO:

| | R04 | R05 |
|---|---|---|
| padrão: média / p95 / 1% low | 10,21 ms / 11,56 ms / 71,6 FPS | 10,44 ms / 11,83 ms / 79,2 FPS |
| três heróis atacando | 11,02 / 12,92 / 62,1 | 10,54 / 12,08 / 71,7 |
| alta (SSAA 1,5×) | 18,53 / 20,19 / 43,6 | 18,69 / 20,31 / 46,5 |
| animação (6 atores) | 268 µs/quadro | 272 µs/quadro |
| draws / tri cena / MB vértices | 156 / 367 158 / 49,0 | 156 / 367 482 / 49,2 |

Diferenças dentro da deriva do controle (+2%) e da variação entre execuções: **sem regressão**. A
animação custa ~3% do quadro; as alocações temporárias do IK não são gargalo e não foram otimizadas.

## 8. Documentos atualizados

`README.md`, `CLAUDE.md` (curto), `docs/README.md` (oficial × histórico), `vision/visao.md` (E escolhida),
`art/visual-identity.md`, `art/contrato-de-qualidade.md` (novo), `art/reference/` (novo),
`art/guia-de-animacao.md` (seção rodada 05; duas mãos revisto), `art/especificacao-de-personagem.md`,
`art/pipeline-de-producao.md` (edição no Goxel verificada), `art/assinatura-visual.md` (estado de P2/P3),
`art/presets-graficos.md`, ADRs 0002, 0003, 0011 (notas de revisão), `validation/ambiente.md` (máquina
atual), `validation/problemas-conhecidos.md` (reestruturado), `validation/checklist-de-aceitacao.md`.
Links verificados por `tools/check_links.py`: 0 quebrados.

## 9. Skills atualizadas

As cinco de `.claude/skills/` ganharam procedimentos, referências, comandos, falhas já vistas e critérios
de aceite da rodada 05; o conteúdo útil das skills da rodada 01 (pipeline, convenções OpenGL) foi
transferido para `voxel-art` e `rendering`. Cópias anteriores em `round-05/organizacao/skills-antes/`.

## 10. Arquivos reorganizados (nada apagado)

Inventário em `round-05/organizacao/inventario-de-movimentacoes.md`: relatórios 01–04, direções A–D,
guia geral, resultados da rodada 01, experimentos, pesquisa e `skills/` → `docs/historico/`; dois `.vox`
órfãos → `output/quarentena/rodada-05/`. `output/tmp/` (354 MB de iteração, no `.gitignore`) foi mantido —
pode ser apagado pelo proprietário sem perda.

## 11. Fontes artísticas preservadas

Fontes: `tools/*.py`, `assets/anim/*.json`, `assets/scenes/*.json`. Saídas em `assets/` regeneráveis.
Peças desenhadas à mão têm lugar definido (`tools/fontes/`, importadas por `voxlib.load_vox(..., like=)`);
nenhuma existe ainda.

## 12. Reprodução determinística

Cópia limpa do repositório sem nenhum asset gerado → `build_assets.py` → **878/878 assets idênticos byte a
byte**; 39 testes; validador TODOS OK; **22/22 referências idênticas pixel a pixel**. Divergências achadas e
resolvidas no repositório canônico (não no gerador): variantes v2 desatualizadas (regeneradas), atlas PNG da
direção C com bytes diferentes e pixels iguais (zlib; comparação passou a ser por pixel), 2 órfãos.
Prova por categoria (protagonista, arma, criatura, árvore, casa): arquivo apagado, regenerado, hash e
referências idênticos. Fluxo com editor: `.vox` → Goxel → `load_vox(like=)` sem perda.
Evidências: `round-05/reproducao/`. Limite: mesma máquina e driver.

## 13. Problemas ainda existentes

[problemas-conhecidos.md](problemas-conhecidos.md) (abertos com severidade e evidência). Principais:
golpes sem reação secundária (cabelo/cachecol) — média; leitura do arco depende do ângulo — baixa;
cortes da lâmina longa com uma mão — média (limitação de proporção documentada); volumes aproximados no
validador — método; água/sombra/HUD — dívida técnica.

## 14. Referências canônicas

`docs/art/reference/` (22 capturas reais + `manifesto.tsv` com a spec exata, revisão e data; 7 reprovados;
~15 MB). Reprodução: `tools/reference.sh [pasta]`. Estado: **recomendadas**, aguardando o proprietário.

## 15. Avaliação da maturidade artística

Os protagonistas, o equipamento e o cenário de teste formam um conjunto coerente, sem defeitos graves
conhecidos, com regras escritas por classe, imagens de referência reproduzíveis, validação automática e
um pipeline provado do zero. O ponto mais fraco não é a modelagem, é a **leitura do movimento na escala de
jogo**: os golpes são legíveis, mas pequenos, sem reação secundária, e o arco depende do ângulo. A lâmina
longa de duas mãos é limitada pela proporção chibi — aceitável se o proprietário aceitar armas grandes de
uma mão.

## 16. Comandos para reproduzir a entrega

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 tools/build_assets.py
./gradlew test :viewer:installDist
viewer/build/install/viewer/bin/viewer --check
python3 tools/asset_audit.py assets/characters/e assets/characters/r4 assets/creatures/e assets/environment/e
tools/reference.sh output/tmp/ref && .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref
tools/review5.sh todos          # vídeos
tools/bench5.sh                 # benchmark (máquina sem outra carga de GPU)
python3 tools/check_links.py
```

## 17. Recomendação sobre a expansão de assets

**Recomendo liberar uma expansão controlada**, condicionada à aprovação das referências canônicas pelo
proprietário: o padrão está escrito, ilustrado, testado e reproduzível, e um asset novo tem critério
objetivo de aceite (contrato de qualidade + checklist + comparação com a referência). Sugiro que a
primeira rodada de produção seja pequena (um NPC humanoide, uma criatura nível 1, uma árvore, uma
construção, uma arma) justamente para testar o contrato em asset que não foi usado para escrevê-lo. Antes
de produzir armas grandes em quantidade, convém decidir se armas de duas mãos são desejadas (exigiria
revisar a proporção do braço ou aceitar a guarda assimétrica). A decisão é do proprietário.
