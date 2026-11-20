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

## Ruínas e lugares com interior (estudo pós-rodada 12)

- **Sem degrau falso:** piso e soleira com topo ≤ 0,25 u (abaixo do degrau de 0,30 u: o pé não afunda); entulho com 1
  voxel sobre o piso; peça baixa com topo plano e uma altura só (≤ 0,8 u, pisável); parede alta ≥ 1,25 u. A laje de
  0,375 u e os topos de parede em degraus da `ruin_house` são o contraexemplo.
- **Vão com folga:** corpo do herói = 0,6 u de diâmetro; boca, portão e porteira com ≥ 1,0 u; curral ou sala com
  pelo menos 2,5 u por dentro (o curral de 1,9 u deixava o cocho inalcançável).
- **Composição centrada:** a origem do prop é o centro da pegada (até 0,9 u): desloque a composição inteira, pontos
  juntos (`conteudo_ruinas._shift`).
- **Vitrine:** `tools/conteudo/vitrine_estudo.py` (cenas novas + caminhabilidade). Defeito de asset APROVADO já
  conhecido vai para `PRE_EXISTENTES` com o motivo: relatado, nunca escondido, e não reprova o estudo.
- **Teto:** interior coberto declara `colisao.camera: "teto_coberto"`; o catálogo mede `cobertura_u` (onde a câmera
  precisa de forma só de câmera).
- **Variações:** pontos invariantes (exato); pegada e colisão são as REAIS de cada variação; o gerador reserva
  `pegada_reservada_u` (união exata). Nunca esconda a diferença com tolerância.

## Vaga de NPC morador (provado com o Quelmo na barraca)

- **Lugar:** a vaga fica no espaço LOCAL da construção (`pontos.vagas`: `pos_u`, `rumo`, `papel`), nunca em coordenada do
  mundo. Spawn antigo fica por compatibilidade, com `substitui` na vaga nova.
- **Medida** (`tools/conteudo/vaga_feirante.py`) em 0°, 90°, 180°, 270° e um ângulo torto:
  - folga até o sólido ≥ raio do corpo (0,3 u);
  - nada sobre a cabeça;
  - herói a 1,0 u na frente, em chão livre e alcançável, com linha livre até o NPC;
  - entradas da construção e do conjunto alcançáveis com o NPC no lugar;
  - pixels visíveis na câmera de jogo.
- **Uma construção por cena:** com vizinhas, a câmera de jogo entra no telhado da outra e a medida mente. A
  visibilidade é a diferença entre a mesma cena com e sem SÓ aquele NPC.

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

## Integração (depois da aprovação artística)

O caminho completo está no guia `docs/processo/como-integrar-conteudo-ao-mundo.md`.

- **Três aprovações separadas:** artística, integração e distribuição procedural.
- **Biblioteca:** o asset entra no `props.json` e no catálogo, e o jogo só o carrega quando uma cena o pede (ADR 0030).
- **Gerador:** distribuir é versão nova do gerador (ADR 0029). A v1 está fixada por `RegionGoldenTest`.
- **Colisão:** asset com interior espera a colisão composta (`docs/architecture/colisao-composta.md`).
- **Autoria:** commits do laboratório entram com a autoria noreply (skill `git-commits`). Um PR com outro e-mail é
  reprovado pelo `regras.yml`.

## Nunca

- Mudar asset aprovado, referência canônica ou cena do jogo (as vitrines são `vitrine_conteudo_*` e `bench_conteudo_*`).
- Afrouxar teste ou tolerância para passar: os testes deste laboratório acharam entrada bloqueada, galpão fechado, peças
  soltas e piso contado como parede, e todos foram corrigidos no asset.
- Pôr no catálogo uma posição que o teste não confere.

## Rodada 12.2 — integração do PR #8 (o que o integrador conferiu)

- **Vaga** (`pontos.vagas`, com `recomendada`): é como o artista sugere o ponto do morador. O integrador confere no jogo:
  corpo livre (0,55 u para o morador em chão aberto), porta a ≥ 0,6 u, conversa alcançável, visível pela câmera de jogo.
  A do feirante saiu 1 voxel; a da herbalista é nova.
- **Ruína com interior coberto:** `medidas.cobertura_u` vira formas só de câmera (o script de formas compostas já faz
  isso); o teto nunca é corpo.
- **Variação** com `formas_iguais: false` NÃO herda a colisão da base. Hoje nenhuma variação das ruínas novas é publicada.
- **Candidato no mesmo PR** (o Acapela): fica fora do `build_assets.py` e do mundo até ser aprovado
  (`docs/validation/integracao-pr8.md`).
