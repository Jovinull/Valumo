---
name: conteudo-mundo
description: Produzir conteúdo NOVO para o mundo (direção E) no laboratório de conteúdo — construções, árvores, pontos de interesse e props como candidatos, com catálogo valumo.conteudo/1 para o gerador procedural (pegada, entradas, interação, spawns, área livre, inclinação, colisão, frequência, custo), variações controladas, vistas no modo LINHA, vitrines no relevo medido, testes reais (ConteudoMundoTest) e custo pelo mesher. Use ao criar ou revisar uma casa, oficina, ruína, marco, árvore ou prop novo para mundos gerados, ou ao preparar a integração deles com a rodada 11.
---

# Conteúdo do mundo (laboratório)

Guia de arte: `docs/art/conteudo-mundo.md` (matriz de referência, gramática por categoria, paleta nova, lições).
Contrato: `docs/architecture/catalogo-de-conteudo.md`. A gramática de base continua em `voxel-art`,
`estilo-arquitetonico.md` e `regras-de-vegetacao.md` — esta skill não a repete.

## Onde mora

| O quê | Arquivo |
|---|---|
| biblioteca (paleta, cisalhamento, paredes, janela, porta, telhados, chaminé, `line6`, medição, `collision_boxes`, registro `@asset`) | `tools/conteudo_lib.py` |
| construções / vegetação / POIs | `tools/conteudo_construcoes.py`, `conteudo_vegetacao.py`, `conteudo_poi.py` |
| build (props.json + catálogo + rigs de estudo + conjuntos de LINHA + variações) | `tools/conteudo_e.py` (módulo do `build_assets`, depois de `env_e`) |
| testes | `viewer/src/test/java/vx/viewer/ConteudoMundoTest.java` |
| QA | `tools/conteudo/` (`vistas.sh`, `prancha.py`, `vitrines.py`, `cenas.py`, `GroundProbe.java`, `MeshCost.java`, `determinismo.py`) |

## Criar um asset

1. Escreva a função com `@asset("id", nome=, categoria=, sub=, lote=, voxel=, frequencia=, wind=, tags=, funcao=,
   terreno=, apoio=, inclinacao_max_u=, area_livre=, colisao=, variacoes=)`. Ela devolve `(Vox, pontos)`, com os pontos
   em voxels do modelo.
2. Construção:
   - desenhe janelas, portas e enxaimel no plano da parede (`Face`) ANTES de `shear()`;
   - peça solta tem 2 voxels em X;
   - diagonal com `face_line` ou `line6`;
   - chaminé e tudo o que tem o próprio torto vêm depois do cisalhamento.
3. Árvore: tronco no centro da caixa (a colisão do jogo põe o círculo ali); declare `tronco_faixa`.
4. Vento: vegetação PRESA a sólido (musgo, hera, folha de floreira) é fosca (`MOSS_FIX`, `IVY`). Folhagem é só o que
   balança solto (copa, arbusto, erva pendurada). O vento afim desloca a folhagem proporcional à altura e abre fresta
   onde ela encosta em sólido.
5. Estado: `aprovacao` (artística) × `integracao` (liberado ou a dependência); quem usa `colisao.formas` não é
   `liberado`.
6. Pontos:
   - entrada fora da parede e da colisão, olhando para o asset;
   - interação e spawn com lugar livre de pé a até 1 u;
   - interior caminhável (galpão, ruína) = `colisao.formas`.
7. `python3 tools/build_assets.py conteudo_e`.

## Revisar (sempre olhando)

1. `tools/conteudo/vistas.sh <id>` → 8 vistas com herói (3/4, frente, ±x, fundos, 3/4 fundos, silhueta, tarde).
2. `set=conteudo-<categoria>-comparacao` (em cor e com `sil=1`) ao lado dos aprovados: a espécie nova precisa de
   silhueta própria.
3. `set=conteudo-var-<id>`: os extremos das variações.
4. No mundo:
   - `java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/GroundProbe.java 0.25 > output/conteudo/relevo.json`;
   - `.venv/bin/python tools/conteudo/vitrines.py`, que assenta pelo relevo, confere a área livre e põe NPCs nos spawns;
   - `.venv/bin/python tools/conteudo/cenas.py SAIDA`: câmera de jogo, 3/4, frente, fundos, alto e panorama, em 720p e
     1080p, de dia e no fim de tarde.
5. Crítica escrita com o defeito concreto. A correção vai no script; versões rejeitadas ficam no material de revisão
   (`estado="rejeitado"` + `motivo_rejeicao`).

## Assentamentos (lote 2)

- Conjunto de ofício = âncora + itens em `tools/conteudo_conjuntos.py` (posição no referencial da âncora). O build
  recusa conjunto que invade a âncora, sobrepõe itens ou bloqueia uma entrada.
- Variante que precisa existir no jogo (pictograma, conteúdo): `publicar_variantes=True` → prop
  `<id>__<param>-<valor>`.
- Vila de vitrine: `village()` em `tools/conteudo/vitrines.py` assenta construções e conjuntos no lugar válido mais
  perto do desejado.
- A **caminhabilidade** (`walkability`: grade de 0,25 u, raio 0,3, degrau 0,30, água, perfis e formas compostas) tem de
  dar 100% dos alvos alcançáveis. Inalcançável é defeito do asset ou da composição, nunca da conferência.

## Validar

- `./gradlew :viewer:test --tests vx.viewer.ConteudoMundoTest` (e a suíte inteira antes de entregar). Os 10 testes:
  - catálogo e ids;
  - medidas = voxels;
  - materiais e vento;
  - sem fragmento < 4;
  - entradas;
  - pontos de pé;
  - folhagem presa a sólido (vento);
  - tronco × colisão;
  - variações;
  - orçamento de triângulos relativo aos aprovados.
- `.venv/bin/python tools/conteudo/determinismo.py PASTA_TEMP`: dois processos, bytes iguais.
- `java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/MeshCost.java`: triângulos, MB de vértices e ms de carga
  de cada prop.
- `viewer --bench "1920x1080,style=e,layout=bench_conteudo_{baixa,normal,alta}"`. A máquina compartilhada dá ruído
  de ±30% no tempo de quadro: compare draws e triângulos, e intercale a cena canônica antes e depois.

## Nunca

- Mudar asset aprovado, referência canônica ou cena do jogo (as vitrines são `vitrine_conteudo_*` e `bench_conteudo_*`).
- Afrouxar teste ou tolerância para passar: os testes deste laboratório acharam entrada bloqueada, galpão fechado, peças
  soltas e piso contado como parede, e todos foram corrigidos no asset.
- Pôr no catálogo uma posição que o teste não confere.
