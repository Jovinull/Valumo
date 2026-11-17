# Relatório da rodada 02 — consolidação da identidade visual

**Resposta curta:** existe agora uma direção única candidata, a **E — "Fábula Voxel"**, construída
sobre a B com o acabamento de personagem da D, testada contra alternativas em comparação controlada,
documentada com parâmetros reproduzíveis e **mais leve que a B** (≈ 100 FPS em 1080p nativo na Iris Xe
desta máquina). Ela **não** está aprovada: é uma proposta para avaliação do proprietário. Há pontos
fracos reais (seção 10).

Navegar: `./gradlew :viewer:run` abre direto na E (tecla `5`); `Tab` até "COMPARAÇÃO EM LINHA";
`V` troca o conjunto (variantes, heróis, criaturas…). Detalhes na seção 8.

## 1. Resultado artístico

Direção-base: **B** (decisão do proprietário). A E toma da **B** a paleta quente, as formas redondas,
a casa, a composição e o sombreamento pintado em poucos tons; da **D**, o rosto (olho alto com íris,
brilho e sobrancelha), o cabelo em mechas com volume e as camadas de equipamento; da **A**, cores
vivas e peças bem separadas (silhueta testada em preto); da **C**, a disciplina de custo (fusão exata
de faces, assets desenhados para malhas enxutas). Não houve mistura automática de assets: personagens,
pompom, árvores novas, terreno e composição foram refeitos; a casa, o pinheiro, a bétula, a
árvore-marco e objetos pequenos reaproveitam a **forma** da B com paleta e pintura revistas.

Documento central: [../art/visual-identity.md](../../art/visual-identity.md).

## 2. Personagens

**Experimento V1 / V2 / V3** (detalhes e notas por critério em
[../experiments/variantes-do-aventureiro.md](../experimentos/variantes-do-aventureiro.md)):

| | V1 Storybook Refined | **V2 Storybook Fusion** | V3 Stylized Compact |
|---|---|---|---|
| Voxel / cabeças / faces | 1/32 / 2,31 / 3 860 | **1/40 / 2,44 / 5 902** | 1/44 / 2,38 / 7 508 |
| Soma (8 critérios, 1–3) | 15 | **22** | 14 |

V2 venceu (silhueta própria, melhor encaixe no ambiente B, biblioteca expansível) e incorporou da V3
o olho alto 3 × 5. Capturas: `round-02/personagens/variantes-*.png`, vídeos `videos/variantes-*.mp4`.

**Arquétipos consolidados** (`assets/characters/e/`), mesma grade de rig e proporção, silhuetas
diferentes:

| | Aventureiro | Exploradora | Maga |
|---|---|---|---|
| Silhueta | topete em gancho, mochila + saco de dormir | capuz pontudo levantado, ponta caída | chapéu de aba larga, copa torta, amuleto |
| Cabeças / altura / faces | 2,44 / 1,18 u / 5 902 | 2,53 / 1,25 u / 6 198 | 2,53 / 1,25 u / 7 974 |
| Arma (`weaponMode`) | espada em folha (`sword`) | arco recurvo na mão esq. (`bow`) | cajado de meia-lua (`staff`) |
| Postura | neutra | à frente (`lean` 0,045) | para trás (−0,03) |

Rostos: placa plana, olho 3 × 5, quatro expressões trocadas pelo clipe (`arquetipos/expressoes.png`).
Capturas: `arquetipos/herois-{frente,perfil,costas,isometrica,silhueta,sem-textura,luz-neutra,tamanho-reduzido}.png`,
`herois-b-e-d.png` (B × E × D lado a lado), `equipamentos.png`, `anim-*.png`, `herois-no-cenario.png`;
vídeos `videos/herois-{idle,walk,run,attack,hit,emote}.mp4`.

## 3. Ambiente

| Tema | B (rodada 01) | E (rodada 02) |
|---|---|---|
| Névoa | desde a câmera, véu creme sobre a clareira | começa depois do foco (10 u + 0,8 × distância), teto 0,42, azulada no dia |
| Contorno | forte em tudo (riscava tufos e degraus) | discreto; × 0,18 folhagem, × 0,30 terreno, × 0,25 plantas |
| Luz | fim de tarde único | dia (padrão), tarde, neutra + preenchimento por trás |
| Terreno | voxel 1/4 u, jitter, caminho rebaixado | 1/8 u, manchas contínuas, borda de grama gasta, caminho sem degrau serrilhado |
| Vegetação | 5 árvores; manchas "camuflagem" | 7 árvores (4 carvalhos distintos); pinceladas largas; tufos de borda |
| Plantas pequenas | 1/16 u, 186 espalhadas | 1/24 u, 50 sorteios em touceiras com vazios |
| Casa | manchas no estuque e no telhado | fiadas limpas, alpendre, escada de 3 degraus |

Mesmas câmeras da rodada 01 para comparação direta: `round-02/mundo/comparacao-b-e-*.png`.
Vistas novas: `casa.png`, `casa-detalhe.png`, `floresta.png`, `caminho.png`, `vegetacao-close.png`,
`estrutura-santuario.png`, `luz-presets.png`, `nevoa-0-1-2-3.png`, `contorno-0-1-2-3.png`.

## 4. Criaturas

Escala de complexidade em três níveis (visual-identity §12). **Pompom refeito como criatura de
referência** (1/40 u, olho da E em placa plana, bochechas, broto, rabo listrado, 2 744 faces).
Fungo com rosto limpo (era "poluído"); lumi e tortuga mantidos da B. Capturas:
`round-02/criaturas/{escala,comparacao-b-e,individuais,referencia-pompom,no-cenario-*}.png`,
vídeo `videos/criaturas-idle.mp4`.

## 5. Correções

| Defeito (rodada 01 ou encontrado nesta) | Severidade | Correção | Situação |
|---|---|---|---|
| Costas escuras | incomoda | luz de preenchimento | corrigido |
| Pernas atravessam o manto | incomoda | manto em duas metades que seguem as pernas | corrigido (E) |
| Capa rígida | incomoda | capa com restrição + segmento inferior (V3); heróis E sem capa longa | corrigido/retirado |
| Cajado mal resolvido | incomoda | modo `staff`; topo abaixo da aba | corrigido |
| Arco fraco | cosmético | arco de 3 voxels, girado; ataque próprio | corrigido |
| Lâmina atravessa a cabeça (todas as direções) | incomoda | antecipação com braço aberto | corrigido |
| Nuca chapada / ranger B confusa de costas | incomoda | massa + mechas + sulcos; trança e aljava separadas | corrigido (E) |
| Rosto do fungo poluído | cosmético | rosto refeito | corrigido (E) |
| Grama/flores grossas; contorno nos tufos | cosmético | 1/24 u; peso de contorno | corrigido (E) |
| Cena lavada por névoa | incomoda | névoa depois do foco + teto | corrigido (E) |
| `.vox` nunca aberto em editor | — | Goxel verificado | resolvido |
| Teclado/mouse não testados | — | `--demo` na janela real (5 modos × 5 direções) | parcial: roteiro automático, sem pessoa |
| *Novo:* xadrez na nuca da V2 | incomoda | massa + sulcos pintados | corrigido |
| *Novo:* rosto do pompom fragmentado | incomoda | placa plana | corrigido |
| *Novo:* tiara no capuz / faixa na testa da maga | incomoda | forro escuro; cabelo em 3 tons | corrigido |
| *Novo:* trança horizontal na corrida | cosmético | `tailGain` | corrigido |
| *Novo:* degrau serrilhado escuro no caminho | incomoda | caminho sem rebaixo na E | corrigido |
| *Novo:* "tarde" com verdes oliváceos | incomoda | — | **aberto** |
| Sem cotovelo/joelho, sem IK, sombra sem cascata, água plana, HUD sem acento | cosmético/incomoda | — | aberto |

Lista completa: [problemas-conhecidos.md](../../validation/problemas-conhecidos.md#situação-na-rodada-02-direção-e).

## 6. Performance

Método: `viewer --bench` offscreen, glFinish por quadro, câmera de exploração orbitando o herói que
caminha, 1 execução de aquecimento descartada + **3 execuções × 300 quadros**; mesma sessão, nada mais
na GPU. Máquina: i5-1335U / Iris Xe (Raptor Lake), Mesa 26.2.6, Fedora 45. CSV e logs em
`output/visual-review/round-02/performance/`.

| Cena completa, 1920 × 1080 | Média | Mediana | p95 | 1% low | Triângulos (cena / sombra) | Draws |
|---|---|---|---|---|---|---|
| B, nativo | 13,74 ms (72,8 FPS) | 13,64 ms | 15,91 ms | 57,3 FPS | 398 mil / 629 mil | 136 |
| D, nativo | 20,35 ms (49,1 FPS) | 20,24 ms | 22,81 ms | 40,5 FPS | 1,17 mi / 1,51 mi | 135 |
| **E, nativo (padrão)** | **10,14 ms (98,6 FPS)** | **9,87 ms** | **12,14 ms** | **74,1 FPS** | 369 mil / 493 mil | 156 |
| B, SSAA 1,5× | 19,75 ms (50,6 FPS) | 19,65 ms | 22,34 ms | 42,2 FPS | | |
| D, SSAA 1,5× | 26,06 ms (38,4 FPS) | 26,09 ms | 28,64 ms | 32,8 FPS | | |
| **E, SSAA 1,5×** | **13,36 ms (74,8 FPS)** | 13,11 ms | 15,49 ms | 59,9 FPS | | |

| E, 1080p | Média | 1% low | Observação |
|---|---|---|---|
| qualidade BAIXA (3 × 3, sem bloom) | 9,93 ms (100,7 FPS) | 80,1 | |
| qualidade PADRÃO (rodada isolada) | 9,65 ms (103,6 FPS) | 85,4 | |
| qualidade ALTA (SSAA 1,5×, 5 × 5) | 17,79 ms (56,2 FPS) | 47,4 | abaixo de 60 |
| preset tarde | 10,03 ms (99,7 FPS) | 81,3 | |
| contorno 0 / 1 / 3 | 9,10 / 9,77 / 9,72 ms | 89,8 / 81,2 / 84,0 | contorno ≈ 0,7 ms |
| **sem fusão de faces** | 15,26 ms (65,5 FPS) | 54,6 | 1,03 mi tri, 146 MB → com fusão 369 mil, 49 MB |

Memória (só a E carregada): 76 malhas, 48,6 MB de vértices, heap ~93 MB. Resolução de renderização =
saída × SSAA. **Alvo de 60 FPS em 1080p nativo: atingido** com folga (1% low 74–85 FPS). Com SSAA 1,5×
fica em ~75 FPS de média (qualidade PADRÃO com SSAA) e 56 FPS na ALTA (5 × 5).

Comparação com a rodada 01: os números **não** são comparáveis (outra GPU — Raptor Lake é mais rápida
que a Tiger Lake da rodada 01). A comparação válida é B × D × E acima, medida aqui: E é 35% mais rápida
que B e 2× mais rápida que D, com personagens mais detalhados que os da B.

Otimizações feitas (só onde o gargalo foi medido): fusão exata de faces + vento afim (ADR 0007),
sombra Poisson 12 em vez de PCF 5 × 5 (−3,5 ms com SSAA 1,5×). Não otimizado: draw calls (156, não é
gargalo), instancing, LOD.

## 7. Documentação

| Documento | Conteúdo |
|---|---|
| [../art/visual-identity.md](../../art/visual-identity.md) | documento central, 20 seções, aprovados × reprovados com evidência |
| [../art/sistema-de-proporcoes.md](../../art/sistema-de-proporcoes.md), [especificacao-de-personagem.md](../../art/especificacao-de-personagem.md) | proporção, rosto, cabelo, roupa, rig |
| [../art/paleta-e-materiais.md](../../art/paleta-e-materiais.md) | paleta semântica com hex e rampas |
| [../art/regras-de-vegetacao.md](../../art/regras-de-vegetacao.md), [estilo-arquitetonico.md](../../art/estilo-arquitetonico.md) | ambiente |
| [../art/guia-de-animacao.md](../../art/guia-de-animacao.md) | animação, correções, regras |
| [../art/presets-graficos.md](../../art/presets-graficos.md) | luz, névoa, contorno, qualidade (valores) |
| [../art/pipeline-de-producao.md](../../art/pipeline-de-producao.md) | ferramentas, fluxo, edição no Goxel, proveniência |
| [processo-de-revisao-visual.md](../../validation/processo-de-revisao-visual.md), [checklist-de-aceitacao.md](../../validation/checklist-de-aceitacao.md) | QA |
| [../experiments/variantes-do-aventureiro.md](../experimentos/variantes-do-aventureiro.md) | experimento V1/V2/V3 |
| ADR [0006](../../decisions/0006-direcao-consolidada-e.md), [0007](../../decisions/0007-fusao-exata-de-faces.md), [0008](../../decisions/0008-ferramentas-de-arte.md) | decisões |
| `.claude/skills/{character-design,voxel-art,visual-qa,rendering}` | skills do Claude Code para a E |

Atualizados: contrato de assets (§7), arquitetura, renderização, assets, ambiente, problemas
conhecidos, riscos legais (licenças e revisão de semelhança), README, CLAUDE.md, `skills/README.md`.

## 8. Como executar

```bash
cd /home/jovinull/Workspace/voxel-rpg
export JAVA_HOME=~/.sdkman/candidates/java/current       # Temurin 25 (instalado nesta rodada)
python3 tools/build_assets.py                            # todos os assets (inclui E e variantes)
./gradlew test                                           # 16 testes (engine + contrato)
./gradlew :viewer:run                                    # abre na direção E
```

No viewer: `1`–`5` direção (5 = E) · `Tab` modo (cena → personagem → 5 lado a lado → proporções →
**linha**) · `V` conjunto da linha (variantes, v1-v2-v3, herois, herois-b-e-d, criaturas, criaturas-b-e) ·
`C` personagem · `Q`/`E` clipe · `X` expressão · `L` luz (dia/tarde/neutra) · `O` contorno 0–3 ·
`F7` névoa 0–3 · `K` silhueta · `F9` sem cor · `P` qualidade · `M` métricas · `T` girar ·
WASD/Shift/Espaço/R/G na cena · `F12` captura.

Reproduzir as evidências: `viewer/build/install/viewer/bin/viewer --review2` (todas as imagens) e os
comandos de `tools/anim.sh` e `--bench` listados nas skills `visual-qa` e `rendering`.

## 9. Evidências

`output/visual-review/round-02/` (rodada 01 preservada em `output/visual-review/{a,b,c,d,comparacao}`):

- `personagens/` — 15 imagens (variantes: 4 vistas, silhuetas, sem textura, luz neutra, reduzido, rostos, no cenário, walk/attack)
- `arquetipos/` — 18 imagens (heróis: vistas, silhueta, argila, neutra, reduzido, B × E × D, expressões, equipamento, 6 animações, no cenário)
- `mundo/` — 26 imagens (6 câmeras da rodada 01 em E e B × E, vistas novas, luz, névoa, contorno, sem efeitos)
- `criaturas/` — 7 imagens; `performance/` — 3 capturas + CSV/logs; `ferramentas/goxel-abre-vox.png`
- `videos/` — 11 vídeos MP4 + GIF gerados pelo viewer (variantes, 6 clipes dos heróis, exploração andando e correndo, criaturas)

## 10. Avaliação crítica

Notas de 1 a 5, justificadas; nenhuma nota máxima automática.

| Grupo | Nota | Justificativa (evidência) |
|---|---|---|
| Personagem | 4 | pequenos, compactos, carismáticos, originais na combinação; mãos ainda simples (`herois-isometrica`) |
| Rosto | 4 | legível de perto e reduzido; expressões claras; boca baixa no queixo, sem variação de formato de olho entre personagens (`expressoes`) |
| Silhueta | 4 | três recortes distintos em preto (`herois-silhueta`); a exploradora de frente é a mais "redonda" (o capuz só lê de perfil) |
| Equipamento | 3 | espada e cajado bons; arco melhorou mas ainda é fino; sem flecha; mochila e aljava rígidas |
| Animação | 3 | pés plantados, armas coerentes, manto sem interseção; sem cotovelo/joelho, golpe rígido, ~10% de deslize de pé |
| Cenário | 4 | hierarquia e descanso visual claros no panorama; primeiro plano pode ficar vazio demais (`cena-rumo-a-casa`) |
| Vegetação | 3 | pinceladas melhores que a B; copas com tufos ficaram mais recortadas — discutível frente aos novelos lisos da B; terraços lembram curvas de nível |
| Arquitetura | 4 | casa coerente e bem resolvida; telhado com degraus diagonais longos |
| Iluminação | 4 (dia) / 3 (tarde) | dia nítido sem lavar; tarde ainda olivácea |
| Coesão | 4 | heróis, pompom e casa parecem do mesmo jogo; lumi/tortuga/fungo ainda são B renomeados (nível 1/3) |
| Performance | 5 | 99–104 FPS nativo, 1% low ≥ 74, medido em 3 × 300 quadros contra B e D |
| Originalidade | 4 | combinação própria; ponto de atenção: arquétipo "capuz verde + arco" (mitigado, ver riscos legais) |

**Mais fracos:** preset tarde; arco/flecha; animação sem articulações intermediárias; criaturas além do
pompom; primeiro plano das cenas abertas.

**Decisões que dependem do proprietário:**
1. Aprovar a E como direção (ou pedir ajustes) — em especial V2 como linguagem de personagem.
2. Copas recortadas (E) × novelos lisos (B).
3. Preset padrão: dia (escolhido) ou tarde (mais "livro ilustrado", hoje mais fraco).
4. Capa longa: manter fora dos heróis ou investir em tecido articulado.
5. Exploradora de capuz verde: manter ou trocar cor/forma do capuz por cautela.
6. SSAA no padrão (custo ~3 ms) ou antisserrilhado mais barato.

## 11. Recomendação para a rodada 03 (sem iniciar)

1. **Preset tarde e paleta por horário** (rampa de folhagem própria para luz quente).
2. **Animação com articulação intermediária** (cotovelo/joelho em 2 segmentos) e IK de pé simples —
   desbloqueia golpes melhores, flecha e capa longa articulada.
3. **Criaturas nível 1/3 refeitas** na gramática do pompom (fungo, lumi, tortuga) + 1 inimigo novo.
4. **Mundo em chunks** com a fusão exata já pronta, LOD de árvores e instancing — só então ampliar a área.
5. **Teste com pessoas** (teste de bolso de originalidade e leitura dos heróis) antes de fixar o design.
