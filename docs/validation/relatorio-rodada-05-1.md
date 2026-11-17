# Relatório da rodada 05.1 — correções finais, auditoria de animação e aprovação do pipeline

Data: 2026-11-16. Pacote: `output/visual-review/round-05-1/` (índice em `LEIA-ME.md`). Nenhum asset de
conteúdo novo, nenhum gameplay, nenhum commit. Aprovação artística **não** declarada: é do proprietário.

## Respostas objetivas

**1. Qual era a causa exata do defeito na guarda de duas mãos?**
O repouso da lâmina longa (`rest` em `longblade.json`) segurava a lâmina **para cima e para trás**
(`rDir` −0,18; 0,60; −0,78), apoiada por trás do ombro. A saída da guarda ia da lâmina em pé diante do corpo
(`two_up` → `two_out`) até esse repouso, girando ~120° **por trás do braço e da cabeça**. Não havia
interseção, e por isso o validador passava. Havia oclusão:
- de 3/4, a lâmina ficava 59% escondida no próprio repouso;
- na entrada e na saída (`two_up`), a lâmina em pé passava diante do rosto: cobertura de 37% por 0,52 s
  na câmera −40°.

Números medidos com os perfis da rodada 05 (`validacao/oclusao-antes-r05.txt`).

**2. Como a trajetória foi corrigida?**
- **Novo repouso:** lateral, em pé, por fora do ombro, inclinado para fora e com o fio para fora.
- **Entrada desenhada pela frente e por fora:**
  1. repouso;
  2. `two_front` (a lâmina sobe à frente do ombro, ainda em pé);
  3. `two_low` (frente-direita, com o tronco já girado para a guarda);
  4. `two_reach` (a segunda mão se aproxima, `lGripW` 0);
  5. `guard_two` (as duas mãos no cabo).
- **Saída:** `two_release` (a mão esquerda solta primeiro), depois o mesmo caminho ao contrário.
- A lâmina nunca sobe diante do rosto nem gira por trás do braço. Não foram usados offsets arbitrários.

Para chegar aqui, uma tentativa intermediária (girar pela lateral) foi descartada porque punha o pomo
dentro do tronco (cabo × tronco 4,2 voxels).

**3. A arma ainda desaparece atrás do braço em algum momento?**
Nas câmeras de frente e 3/4 (0°, ±40°), não: a lâmina fica ≥ 77% visível durante todo o movimento, e há
teste para isso. De **perfil esquerdo e de costas**, a lâmina em repouso (do lado direito do corpo) fica
atrás do corpo. É oclusão natural de um objeto do lado oposto, não um giro por trás do braço. Isso está
registrado e é visível nos vídeos 03 e 04.

**4. A orientação do arco durante o HIT era correta ou defeituosa?**
Defeituosa, e medida. `hit_recoil`/`hit_hold` herdavam `lDir`/`lEdge` do repouso, então o arco ficava
**parado no mundo** (giro 0,5°) enquanto o antebraço girava 50° em volta dele, com o tronco recuando.
Visualmente, isso lê como "o arco virou de lado na mão".

**5. O que foi modificado?**

| Área | Mudança |
|---|---|
| `assets/anim/longblade.json` | `rest`, `carry_run`; poses `two_front`, `two_low`, `two_reach`, `two_release` (removidas `two_up`, `two_out`); `guard_two_hands` refeita; `"interrupt": "forward"` nos cortes |
| `assets/anim/bow.json` | `lDir`/`lEdge` em `hit_recoil`/`hit_hold` acompanhando 80% do antebraço |
| `assets/anim/staff.json` | `align`/`focus` com o cajado inclinado para fora; chave `circ_return` na conjuração circular |
| `tools/chars_e.py` | mochila e pontas do cachecol do aventureiro viram partes (`pack`, `scarf`) recortadas depois da pintura |
| `Animator` | camada secundária sem estado (`secondaryLag`, `-Dvx.secondary=false`) |
| `Sequencer` | interrupção `golpe!segundos`: o golpe cortado anda pela própria trajetória por 0,30 s; o peso continua de onde estava |
| `Choreo` | `-Dvx.anim=pasta` (comparação antes/depois); campo `interrupt` |
| Ferramentas novas | `GripProbe` (`viewer --grip`), `OcclusionCheck` (`viewer --occlusion`), `viewer --check interrupcao`, `tools/review51.sh` |
| Documentação | ver a resposta 11 |

**6. Como foram verificadas as empunhaduras?**
Por três caminhos:
- **Medição:** `viewer --grip rig golpe` mede, por instante, a orientação do item em relação ao antebraço
  (torção, flexão, Δpunho), a velocidade angular do item e o alerta de "item parado no mundo". Também
  imprime a orientação que o item teria se acompanhasse o antebraço, e foi isso que guiou a correção do arco.
- **Visual:** vídeos em 4 câmeras, closes da mão e capturas com depuração (`dbg=1`).
- **Testes:** `bowFollowsTheForearmDuringHit`, `noHeldItemStaysFrozenWhileTheArmTurnsInHit` e
  `longBladeTwoHandedGuardKeepsBothHandsOnTheHandle`.

Resultados:
- HIT do arco: o punho varia 13,5° (antes 50°), e o arco gira 38° junto com o braço (antes 0,5°).
- Guarda de duas mãos: com o fio para fora no repouso, a troca de empunhadura até a guarda caiu de 180°
  para 115°.

Achado de método: o Δpunho absoluto **não** serve de limite, porque a amarração item/antebraço é
arbitrária (golpes aprovados da espada chegam a 113°). Só o alerta de item parado reprova.

**7. As animações secundárias melhoraram?**
Sim, onde havia parte articulável:
- mochila e pontas do cachecol do aventureiro (novas partes);
- trança da exploradora;
- chapéu da maga.

Os limites são unilaterais: a mochila só se afasta das costas e o cachecol só vai para a frente. Não há
mola nem física, e o efeito é reproduzível e testado. A mudança é sutil: 1–2% dos pixels no impacto, em
close de costas. Na câmera de jogo é discreta (vídeos 10–13: sem × com). Cabelo do aventureiro, aljava e
bolsas continuam fundidos ao tronco ou à cabeça e **não** foram simulados (limitação documentada).

**8. Os 39 testes originais continuam passando?** Sim, inalterados.

**9. Quantos testes novos foram adicionados?** 6, em `AnimationQaTest`: arco no HIT, nenhum item parado no
HIT, rosto livre em todos os golpes canônicos (exceção documentada: linha de mira do arco), guarda de duas
mãos visível e fora do rosto, interrupção por HIT (sem teleporte; segura onde garantido), movimento
secundário determinístico e limitado. **Total: 45/45.**

**10. O validador apresenta alguma falha?** Não. `viewer --check`: TODOS OK, incluindo o novo roteiro de
interrupção na preparação. Nenhuma tolerância foi alterada.

**11. Que contradições de documentação foram corrigidas?**
1. **HIT/EMOTE:** o guia dizia "HIT/EMOTE seguem FK" numa seção e "coreografados" em outra. Agora: heróis
   da E e rigs r4 usam gestos coreografados; A–D e variantes seguem FK, só para comparação.
2. **Degraus:** "~0,2 u" e "~0,13 u" eram grandezas diferentes. Agora há uma tabela com quatro grandezas:
   - alcance de um pé: ±0,147 u;
   - desnível entre os pés: 0,131 u;
   - obstáculo superável: ≤ 0,131 u (o degrau T1 de 0,125 u cabe);
   - degrau que exige subida: > 0,131 u. O 0,2 u é o degrau de **teste** da bancada.
   Corrigido no guia, na ADR 0009 e nos problemas conhecidos.
3. **Leitura do arco:** "lê bem na câmera de jogo" × "depende do ângulo". Agora há uma tabela por câmera,
   medida (ver "Leitura do arco" em problemas-conhecidos).
4. **NPCs:** o contrato exigia proporções e olho dos protagonistas para todo humanoide. Agora os
   protagonistas ficam congelados, e os NPCs futuros têm faixa de 2,0–3,0 cabeças, rosto sempre em placa
   e forma do olho livre. Propagado para a especificação de personagem, o sistema de proporções, o
   checklist, `CLAUDE.md` e a skill. Os testes dos protagonistas não foram afrouxados.
5. **Guarda de duas mãos:** afirmações da rodada 05 sobre "entra e sai pela lâmina em pé" foram
   atualizadas no guia; a ADR 0011 ganhou nota de revisão.

**12. Quais skills foram atualizadas?** As cinco:
- `weapon-animation`: falhas reais da guarda e do arco, receita de entrada/saída de guarda, as três
  ferramentas, transições, interrupção, acessórios;
- `visual-qa`: procedimento obrigatório de inspeção em vídeo e escala de jogo;
- `character-design`: protagonistas × NPCs, partes secundárias;
- `voxel-art`: recorte de partes depois da pintura, peças soltas;
- `rendering`: presets inalterados, sondas sem render.

Todas apontam para uma fonte única de números (contrato de qualidade, guia de animação, sistema de
proporções).

**13. O pipeline continua determinístico?** Sim, numa cópia limpa do repositório, no estado final:
- build do zero com assets idênticos byte a byte;
- os mesmos 45 testes passam;
- validador OK;
- referências idênticas pixel a pixel;
- fontes preservadas.

Detalhes em `reproducao/resumo.txt`.

**14. Quais problemas residuais permanecem?** Ver [problemas-conhecidos.md](problemas-conhecidos.md) e a
[matriz](matriz-de-problemas.md). Os relevantes:
- **Interrupção por HIT em algumas janelas:** sem teleporte, mas atravessa o corpo na continuação da
  espada (7/21 cortes) e no meio do combo da lâmina longa (14/47). Não há combate hoje; vai para a rodada
  de combate. Severidade: média quando houver combate.
- **Linha de mira do arco:** cobre o rosto de frente e de 3/4 frontal; de costas o arco fica escondido em
  ~50% do disparo. Limitação aceita, medida.
- **Movimento secundário:** não existe em acessórios fundidos ao corpo.
- **Lâmina longa:** cortes de uma mão.
- **Validação por volumes:** é aproximada; o vídeo continua obrigatório.

**15. A direção E está pronta para uma expansão pequena e controlada?**
**Recomendo que sim, condicionada à aprovação humana** desta entrega e das referências candidatas.
1. Os dois defeitos P0 foram corrigidos e demonstrados em vídeo, em 4 câmeras.
2. As duas classes de falha que o validador não via (oclusão e orientação na mão) agora têm ferramenta,
   teste e procedimento.
3. A documentação não tem mais as contradições apontadas.
4. As skills refletem a implementação.
5. O pipeline é reproduzível.

Os residuais acima são de severidade baixa, ou dependem do combate, que está fora do escopo. Nenhum é
defeito visual grave nas animações canônicas. Sugestão para a primeira rodada de produção: poucos assets
(um NPC humanoide, uma criatura nível 1, uma árvore, uma construção), justamente para testar o contrato de
NPC num caso real.

**Não** recomendo produzir em escala armas que dependam de duas mãos ou de interrupção de golpes antes da
rodada de combate.

## Critérios do gate (§14)

| # | Critério | Situação | Evidência |
|---|---|---|---|
| 1 | Saída da guarda de duas mãos sem interseção ou oclusão inadequada | atendido | vídeos 01–05; `--occlusion`; teste |
| 2 | HIT do arco com orientação e empunhadura coerentes | atendido | vídeos 06–09; `--grip`; teste |
| 3 | Sem clipping visual grave nas animações canônicas | atendido | `--check` TODOS OK; `--occlusion`; vídeos |
| 4 | Armas legíveis na câmera real de jogo | atendido, com limitação do arco por ângulo (medida) | vídeos 20–25; problemas-conhecidos |
| 5 | Transições importantes contínuas | atendido (locomoção ↔ golpe, golpe ↔ golpe, guarda ↔ repouso, HIT sem teleporte) | `ChoreoTest`, `--check interrupcao` |
| 6 | Acabamento consistente | atendido | referências 01–12 |
| 7 | Sem regressão artística importante | atendido | `validacao/registro-de-regressoes.md` |
| 8 | Testes anteriores passam | atendido (39/39 + 6) | `validacao/testes.txt` |
| 9 | Validador espacial passa | atendido | `validacao/check.txt` |
| 10 | Capturas e vídeos reais confirmam | atendido | `videos/`, `capturas/` |
| 11 | Documentação sem contradições materiais | atendido (5 resolvidas) | resposta 11 |
| 12 | Skills refletem a implementação | atendido | resposta 12 |
| 13 | Pipeline reproduzível | atendido | `reproducao/` |
| 14 | Referências afetadas atualizadas | atendido (candidatas; anteriores preservadas) | `docs/art/reference/` |
| 15 | Problemas abertos com severidade, evidência e justificativa | atendido | problemas-conhecidos, matriz |

## Desempenho

Comparado com a rodada 05, mesmas specs e mesmo controle (10,27 → 10,26 ms): **sem regressão relevante**.
Padrão: 10,12 ms (média de 2 execuções), p95 11,81 ms, 1% low 75 FPS. A camada secundária custa menos que
o ruído. Detalhes: `performance/resumo.md`.

## Comandos para reproduzir

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 tools/build_assets.py && ./gradlew test :viewer:installDist
B=viewer/build/install/viewer/bin/viewer
$B --check && $B --check interrupcao && $B --occlusion
$B --grip characters/e/ranger clip:HIT
JAVA_OPTS="-Dvx.anim=output/visual-review/round-05-1/linha-de-base/anim-r05" $B --occlusion   # antes (R05)
tools/review51.sh todos        # vídeos e capturas da rodada
tools/reference.sh             # referências canônicas
python3 tools/check_links.py
```

## Errata (registrada na rodada 06)

Na resposta 14, "meio do combo da lâmina longa (14/47)" está **errado**: o número saiu de uma varredura
anterior à última mudança do repouso da lâmina longa (fio para fora, 23:01). No estado entregue, a
própria evidência da rodada (`round-05-1/validacao/interrupcao-por-hit.txt`, 23:07) mostra **18/47**
cortes com interseção (29/47 seguros), nas janelas 0,75–1,25 s e 1,50–1,80 s. A espada continua 7/21.
Nenhum teleporte, como relatado. Corrigido em `problemas-conhecidos.md` e `matriz-de-problemas.md`.
