# Relatório: integração dos lotes 1 e 2 de conteúdo (PRs #3 e #5)

**Base:** `main` `9c2c5ac` (tag `worlds-and-menus-v0.5`), com as rodadas 11 e 11.1.

**O que entra:** a biblioteca de conteúdo do laboratório, só como **biblioteca**:

- 35 modelos (34 aprovados + 1 rejeitado, que fica fora do `props.json`);
- 6 variantes publicadas e 5 conjuntos de ofício;
- vitrines e 2 vilas demonstrativas;
- o catálogo `valumo.conteudo/1`, com os testes e as ferramentas.

**O que não muda:** o gerador procedural continua o mesmo (v1) e nenhuma região muda. O pacote de revisão (local) fica
em `output/visual-review/content-integration/`.

## 1. Como entrou

| Ramo | Conteúdo |
|---|---|
| `integration/world-content-lote-1` | referência das sementes da v1, regra de autoria no CI, os 15 commits do PR #3, carga sob demanda dos props (+ teste e roteiro de medida) |
| `integration/world-content-lote-2` | os 12 commits exclusivos do PR #5, as correções achadas na auditoria, a matriz de saves antigos e esta documentação |

- **Autoria:** os 27 commits dos PRs tinham o e-mail pessoal como autor e committer. Por decisão do proprietário,
  foram recriados com o noreply.
  - Mesma mensagem, mesmo conteúdo e mesma ordem; 0 linha de diferença para `f07de05` e `eefb7b9`.
  - Os ramos dos PRs não foram alterados.
  - **Para não repetir:** `regras.yml` no CI e a skill `git-commits`.
- **Conflitos:** nenhum de texto. Os de comportamento foram procurados de propósito: memória, sementes, colisão,
  saves, menu e criador.

## 2. Biblioteca, não distribuição

- **Sementes:** `RegionGoldenTest` guarda o sha256 da região inteira de 8 sementes (1024, 2048, 4096, 77, 1, 555, 9001,
  31337). A referência foi capturada na `main` antes da integração.
  - A impressão digital cobre tentativa, arranjo, mata, lago, colina, documento do mundo, cena, encontros e ids de
    spawn, vínculos, POIs, relevo e missão.
  - Resultado: **8/8 idênticas**, com `RegionGenerator.VERSION` = 1.
- **O que os lotes não tocam:** `RegionGenerator`, `ProceduralField`, `Reachability`, `ProceduralScene`,
  `colisao_e.json`, `habitantes_e.json`, `modelos_missao_e.json` e `combate_e.json`.
- **Estados no catálogo:**

| Integração | Quantos |
|---|---|
| `liberado` | 28 |
| `depende_colisao_composta` | 4: `workshop_forge`, `ruin_tower`, `ruin_house`, `market_stall` |
| `aguardando_refinamento` | 1: `stone_circle` |
| `restrito_a_conjuntos` | 1: `basket_group` |
| `nao_distribuivel` | 1: `tree_tiered`, rejeitado |

Conjuntos: `herbalista`, `cacador` e `guardiao` liberados; `ferreiro` (âncora `workshop_forge`) e `comerciante` (item
`market_stall`) dependem da colisão composta.

## 3. Auditoria (achados e o que foi feito)

| Achado | Onde | Feito |
|---|---|---|
| A biblioteca registrada era malhada inteira na GPU: +34,6 MB no menu, ~+69 MB depois do criador | `Assets.props` | catálogo sob demanda ([ADR 0030](../decisions/0030-catalogo-de-props-sob-demanda.md)) |
| O modo LINHA relia o JSON do conjunto a cada quadro, duas vezes | `App.lineup` (lote 1) | lido uma vez por nome; captura idêntica pixel a pixel |
| Variável sem uso no teste das variações (verificação esquecida) | `ConteudoMundoTest` | agora confere que a variação é do asset comparado |
| Oficina: o ponto `bigorna` fica a 0,125 u do spawn `ferreira` (e `bancada` a 0,28 u); o herói não alcança com ela no lugar | catálogo (lote 1) | registrado (P73). A oficina não está liberada |
| 3 comentários de documentação soltos (`-Xlint`) | `Game`, `GameLogic`, `WorldStore` (11.1) | corrigidos: `-Xlint:all` 0 |
| O roteiro da aventura do menu esperava "Menu principal" → lista de mundos (comportamento de antes do fechamento) | `tools/menu/04-aventura.txt` (11.1) | ajustado; falhava na própria `main` |
| O estado do `CLAUDE.md` dizia que as rodadas 11/11.1 esperavam publicação | `CLAUDE.md` | corrigido |
| Musgo de folhagem na casa aprovada desliza com o vento (anterior aos lotes) | `house` (v1) | registrado (P71), sem mexer: é asset da v1 com referência |

Correção da oficina (`fix(tools): libera o interior da oficina…`), conferida:

- mãos-francesas acima da cabeça (y ≥ 1,625 u);
- corredor de ~1 u até a forja;
- bigorna junto à sala e tina do lado de fora;
- entradas inalteradas;
- colisão continua `depende_colisao_composta`.

## 4. Carregamento e desempenho

Mesma máquina e mesma sessão, `main` × integração sem e com a carga sob demanda. Roteiro `tools/menu/14-memoria.txt`,
3 rodadas intercaladas (a última com a ordem invertida).

| Medida | `main` | integração |
|---|---|---|
| malhas / MB de vértices no menu | 212 / 112,7 | **206 / 106,1** |
| depois do criador, na vila, na região 1024 | 283 / 188–192 | **267–269 / 169–173** |
| primeiro quadro do menu | 4036 ms | 4062 ms |
| montar a região 1024 | 1787 ms | 1793 ms |
| abrir o criador (1ª vez) | 1735 ms | 1891 ms (**P72**, sem causa encontrada) |
| quadro mediano menu / vila | 11,17 / 9,46 ms | 11,20 / 9,77 ms |
| draws e triângulos da vila | 96 / 463 634 | 96 / 463 634 |

- **Custos, separados:** a biblioteca NÃO usada custa 0; o asset usado custa o que custa (`MeshCost`); a composição
  densa é a da vitrine (bench no pacote).
- **Investigação do carregamento:** os seis níveis, de registrado a instanciado. Está no pacote (`carregamento.md`).

## 5. Saves antigos

`tools/qa/saves_antigos.sh` (novo):

1. o binário da `main` publicada cria os saves;
2. os dois binários os abrem de cópias idênticas;
3. as linhas `missao`, `regiao`, `menu-estado` e a posição têm de sair iguais.

| Caso | Resultado |
|---|---|
| vila v1 com missão ativa; vila v1 concluída | iguais |
| região 1024 (v2) com o marco aceso (POI restaurado); região concluída | iguais |
| menu: mesmo personagem na vila e na região; outro personagem na vila e na região | iguais |
| `.invalido` | **0** |
| exceção ou erro de save | **0** |

## 6. Validação

- **Testes:** 377 testes, 0 falhas.
- **Compilador e IDE:** `-Xlint:all` 0; IDE (import limpo) 0.
- **Validadores:**
  - `--check` (129 animações), interrupção fina, golpe andando, esquiva R2 e cabelos: ok;
  - oclusão idêntica à `main`.
- **Imagens:** gate dos presets 51/0; referências 22/22 com desvio 0.
- **Outros:** áudio 47/0 alerta; determinismo do laboratório 396/0; links 0.
- **Clone limpo e CI:** no pacote.

## 7. Propostas e o que fica aberto

- **Versões do gerador:** [ADR 0029](../decisions/0029-versoes-do-gerador.md), proposta. A v1 fica congelada para os
  mundos v1 e a v2 vale só para mundos novos. Uma versão antiga e suportada nunca vira `.invalido`.
- **Colisão composta:** [colisao-composta.md](../architecture/colisao-composta.md), proposta. Traz o contrato, quem
  precisa dela e os testes.
- **Guia:** [como integrar conteúdo ao mundo](../processo/como-integrar-conteudo-ao-mundo.md).
- **Problemas abertos:** P67–P73 em [problemas-conhecidos.md](problemas-conhecidos.md).
