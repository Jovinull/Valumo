# Relatório — Rodada 08.1: malha conforme (fim do tremor) e dentes

Pacote local (fora do git): `output/visual-review/round-08-1/` (`LEIA-ME.md`). Regenerável com
`tools/review81.sh tudo` e `R=output/visual-review/round-08-1/entrega tools/review8.sh capturas videos`.
A entrega da rodada 08 (`round-08/`) foi preservada.

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo                                   # protótipo jogável
./gradlew editor                                 # criador de personagens
./gradlew test                                   # 124 testes
viewer/build/install/viewer/bin/viewer --check   # validador de armas
viewer/build/install/viewer/bin/viewer --occlusion
tools/review7.sh gate                            # presets recompostos × canônicos
tools/review81.sh tudo                           # validação temporal, dentes, referências, regressão, desempenho
```

## 1. Linha de base

- **Git:** `main` em sincronia com `origin/main` (`2cf88c3`), 34 entradas locais (rodada 08 + dentes + câmera).
- **Testes:** 119/119.
- **Registro:** em `round-08-1/linha-de-base/`.

## 2. Dentes (correção aprovada pelo proprietário)

- **Cor:** `TEETH = #dccbbb` (marfim quente) no lugar de `EYE_HI` (o branco do brilho do olho) em dois lugares:
  - `charlib_e.act_face`: aventureiro, expressão determinada;
  - `npcs_trio.face`: Kelyra, alegre.
- **Verificação:** close e distância de jogo, em luz de dia, tarde e neutra (`round-08-1/dentes/`):
  - os dentes leem como dentes, sem mancha;
  - o olho continua o ponto mais claro do rosto;
  - a boca mantém a expressão.
- **Limpeza:** a auditoria da Kelyra não acha voxel isolado nem fragmento.

## 3. Diagnóstico do tremor

| Causa | Onde | Por quê |
|---|---|---|
| T-junctions (frestas) | `VoxelMesher.buildDataMerged` — fusão gulosa | o vértice de um quad pequeno fica no meio da aresta do grande vizinho; as arestas não compartilham vértices e a rasterização deixa pixels sem cobertura |
| Margem anti-fresta (tremor) | `VoxelMesher.emitQuad` — `crackMargin = 0,06` (rodada 03, ADR 0010) | cada quad fundido avançava 0,06 voxel sobre o vizinho; faces coplanares de cores diferentes se sobrepunham numa faixa de 0,12 voxel e a profundidade empatava (z-fighting) |
| Deriva da câmera | `FollowCamera.update` | a distância convergia sem nunca parar (milionésimos por quadro) e mantinha o z-fighting ativo mesmo com o jogador parado |

**Demonstração:** cena controlada da fachada com perturbação de câmera de 0,0003 px. A geometria praticamente
não se move, então o que muda é a disputa de profundidade:
- **Malha legada:** 3.000 a 5.700 pixels mudam, ao longo das bordas da casa, dos degraus e dos caixotes.
- **Malha conforme:** só o herói, que respira (animação).

O recuo das faces tentado na 08 tirava o tremor, mas reabria frestas (fachada: 5 → 93 pontos claros).
Abandonado.

## 4. Correção (ADR 0014)

**Malha conforme:**
1. Coleta os quads fundidos e o conjunto dos seus vértices (inteiros da grade).
2. Em cada aresta, insere os vértices da malha que caem no interior.
3. Triangula o polígono:
   - leque a partir de um canto livre de inserções;
   - ou leque pelo centro, se não houver canto livre.
4. Margem 0.

**Efeito:**
- toda aresta compartilhada tem os mesmos vértices dos dois lados, então é estanque pela regra de rasterização;
- cor, material e AO não mudam, porque o quad fundido já é uniforme;
- quads sem T-junction continuam com 2 triângulos.

**Comparação com o modo antigo:** `-Dvx.mesh=legado` reproduz o comportamento anterior.

**Diagnósticos novos:**
- `-Dvx.wind=0`: desliga o vento;
- `tools/temporal/`: roteiros, métrica de alternância A→B→A e comparador de referências;
- `estado` e `camera` nos roteiros do jogo.

**Prova contra a verdade geométrica** (malha sem fusão, `-Dvx.merge=false`, sem T-junction nem margem por
construção), na referência 16 com os mesmos parâmetros:

| Malha | Pixels diferentes da malha sem fusão | Acima de 25 |
|---|---|---|
| Conforme | 0,039% | 8 |
| Legada | 7,6% | 27.024 |

## 5. Validação temporal (câmera lenta, mesmo binário, legado × conforme)

Métrica: pixels que vão e voltam (A→B→A) por quadro. É o sintoma de z-fighting; o deslocamento normal de câmera
lenta é monótono.

| Cena | Legado (média/quadro) | Conforme | Queda | Observação |
|---|---|---|---|---|
| Fachada da estalagem | 2.013 | 539 | −73% | o resto é animação e serrilhado de árvores distantes |
| Rosto (criador, close) | 1.707 | 648 | −62% | olhos, sobrancelhas e boca deixam de cintilar |
| Outros (salgueiro, posto, NPCs, pompom, espada) | 1.971 | 1.656 | −16% | o resto é folhagem ao vento e animação |
| Terreno (degraus e encostas) | 3.218 | 2.275 | −29% | o resto é serrilhado subpixel: a malha sem fusão dá **2.255** |
| Movimento (correr pela vila) | 5.436 | 5.225 | −4% | em câmera rápida a métrica conta o próprio movimento; vale o vídeo lado a lado |

Mapas de calor, vídeos do conforme e vídeos lado a lado: `round-08-1/temporal/`. Antes e depois com mapa:
`round-08-1/antes-depois/`.

**Câmera parada:** os quadros seguidos ficam bit a bit iguais (distância 4,600000), com desvio automático
zerado. Os testes de câmera do jogo continuam passando: escada sem quicar, parede sem atravessar, volta à
distância e corredor.

## 6. Referências canônicas

As 22 foram refeitas e auditadas uma a uma (`round-08-1/referencias/`: novas, diferenças e pranchas
antiga | nova | diferença). As anteriores ficam em `docs/art/reference/anteriores/r08/`.

**Como mudaram:** só nas bordas entre cores e nas silhuetas, porque a faixa sobreposta e as pontas de 0,06 voxel
saíram.
- **Faixa de mudança:** de 0,2% a 2,3% dos pixels acima de 25/255.
- **Rostos 06–08:** sem o pontilhado nos olhos.
- **Referência 10:** com os dentes novos.

**Pontos claros isolados (frestas) nas 22:** 165 → 61.
- **O que sobe:** a 16 sobe de 8 para 25 e a 05 de 1 para 2.
- **Por que não é regressão:** os mesmos pontos aparecem na malha exata sem fusão, então vêm do modelo (borda de
  baixo da floreira dos fundos), não de fresta da malha. A margem antiga só os cobria por sobreposição.

**Reprodução:** as referências instaladas se reproduzem a **0/255** (22/22).

## 7. Regressões

| Verificação | Resultado |
|---|---|
| Testes Java | **124/124** (119 + 5 de `MeshSeamTest`) |
| `--check` | TODOS OK |
| `--occlusion` | só a exceção conhecida (`bow_shot`) |
| Gate dos presets | 51 pares, 0 diferenças |
| Geração determinística | 1.066 arquivos, hash idêntico em duas gerações |
| Auditoria de assets | heróis e NPCs sem voxel isolado novo |
| Referências | 22/22 a 0/255 |

**`MeshSeamTest`** cobre:
- cobertura exata (sem buraco nem sobreposição, inclusive a 0,02 voxel da borda);
- estanqueidade (sem T-junction; a superfície da água, aberta por desenho, fica de fora);
- área;
- os casos sintéticos pedidos e assets reais: casa, posto, poço, caixote, pedra, carvalho, rosto do aventureiro.

O teste de regressão exige que o modo legado **falhe** em sobreposição e T-junction.

## 8. Desempenho

Medido em 1920×1080, qualidade padrão, Iris Xe, 7 s de aquecimento, mesmo percurso e mesma câmera nas duas malhas.

| | Legado | Conforme | Δ |
|---|---|---|---|
| Jogo: mediana / p95 / pior 1% | 8,83 / 11,22 / 12,98 ms | 9,40 / 11,51 / 13,37 ms | +0,6 ms (+6%) |
| Viewer, mesma cena: mediana / p95 / 1% low | 9,06 / 10,20 ms / 90,6 FPS | 9,90 / 11,18 ms / 79,9 FPS | +0,8 ms (+9%) |
| Triângulos desenhados (jogo) | 372.552 | 478.000 | +28% |
| Memória de vértices | 57,8 MB | 74,6 MB | +29% |
| Montagem da cena (inclui o meshing) | 1,80 s | 2,02 s | +0,23 s |
| Draws | 94 | 94 | = |

O aumento vem dos vértices inseridos nas T-junctions e do leque pelo centro. Fica abaixo de 10 ms de mediana, e
o defeito corrigido é grave para a jogabilidade. Se pesar em cenários maiores, há um passo seguinte possível:
fundir respeitando as fronteiras dos vizinhos, em vez de inserir vértices depois.

## 9. Entrega regenerada

Em `round-08-1/entrega/`, todas produzidas depois das alterações finais:
- capturas da exploração e do diálogo com a Kelyra;
- os 6 vídeos de exploração (movimento, encosta, obstáculos, câmera, Kelyra, aparência).

## 10. Problemas abertos

- Serrilhado de detalhe menor que um pixel ao longe (degraus de morros), o mesmo da malha exata. Solução seria
  antisserrilhado temporal ou MSAA, que é decisão de renderização.
- Pontos de 1 pixel na floreira dos fundos da casa, que são do modelo.
- Custo de +28% de triângulos.
- As interrupções por HIT da espada e da lâmina longa continuam abertas, como antes.
