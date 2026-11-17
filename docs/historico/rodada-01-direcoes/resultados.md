# Resultados medidos

Máquina e versões: [ambiente.md](../../validation/ambiente.md). Medições de 2026-11-16, com o notebook na tomada e
sem outras cargas de GPU conhecidas. São de **uma execução cada**; a variação entre execuções
observada ao longo do dia foi de ±10–15%.

## Build e testes

| Verificação | Comando | Resultado |
|---|---|---|
| Compilação | `./gradlew :viewer:installDist` | sem erros |
| Testes | `./gradlew test` | 11 testes (5 mesher, 2 leitor `.vox`, 4 contrato de assets), 0 falhas |
| Determinismo dos assets | dois `python3 tools/build_assets.py` seguidos + `sha256sum` | 401 arquivos, 0 diferenças |
| Pacote de revisão | `viewer --review` | 73 PNG em `output/visual-review/`, saída 0 |
| Caminho interativo | `viewer --demo 600` (janela real, percorre modos/direções/clipes) | saída 0, sem exceções |

Tamanho: 348 arquivos `.vox`, `assets/` com 4,7 MB; ~9,4 mil linhas de Python (modelos) e ~3,4 mil
de Java.

## Tempo de quadro — cena completa (`viewer --bench`)

Offscreen, 240 quadros com a câmera orbitando, herói andando, `glFinish` por quadro, sem vsync,
sombra 2048², 1920×1080 de saída.

| Direção | SSAA 1,5× (2880×1620 interno) | SSAA 1,0× | Triângulos na cena / no passo de sombra |
|---|---|---|---|
| A | 19,4 ms — **52 FPS** (p95 26,9 ms) | 11,6 ms — 86 FPS | 0,37 M / 0,50 M |
| B | 24,1 ms — **42 FPS** (p95 28,6 ms) | 16,2 ms — 62 FPS | 0,43 M / 0,71 M |
| C | 11,0 ms — **91 FPS** (p95 13,5 ms) | 6,6 ms — 152 FPS | 0,04 M / 0,05 M |
| D | 34,7 ms — **29 FPS** (p95 43,8 ms) | 26,8 ms — 37 FPS | 1,23 M / 1,67 M |

O modo interativo usa SSAA 1,5×. ~133 draws por quadro em todas as direções.

### Janela real (`--demo 600`, 1600×900, vsync ligado, média entre as 4 direções)

| Modo | FPS médio |
|---|---|
| Cena completa | 44,6 |
| Personagem isolado | 74,6 |
| Lado a lado | 102,0 |
| Estudo de proporções | 85,1 |

## Experimentos de desempenho (direção A, 1080p)

| Configuração | ms/quadro |
|---|---|
| Sombra 4096², SSAA 1,5× (primeira versão) | 24,5 |
| idem, sombra desligada | 13,9 |
| Sombra 4096² → 2048² (a 640×360, isolando o custo da sombra) | 13,7 → 9,1 |
| Pré-passo de profundidade ligado × desligado (SSAA 1,5×, 2048²) | 19,2 × 19,4 (D: 38,1 × 34,7) |

Conclusões: o custo dominante é **vértices** (dois passos sobre malhas sem greedy meshing), não
pixels. Reduzir o mapa de sombra ajudou; o pré-passo não, e ficou desligado. A direção D paga pelos
1,2 milhão de triângulos de voxel fino.

## Leitura

- A, B e C rodam com folga numa GPU integrada de 2020; D fica abaixo de 30 FPS em 1080p com SSAA.
- Nada disso é desempenho de jogo: é uma cena fechada de 48×48 u, sem lógica, IA, física ou
  streaming. Serve para comparar o custo relativo das direções e dizer que a D **exige** greedy
  meshing/LOD antes de virar mundo aberto.
- Não foi medido: outra GPU, outro sistema, uso de memória, tempo de carga (percebido: 1–3 s).
