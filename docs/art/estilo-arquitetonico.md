# Estilo arquitetônico (direção E)

Modelo de referência: a casa (`house`, `tools/env_e.py` sobre `env_b.house`). Evidências:
`round-02/mundo/casa.png`, `casa-detalhe.png`, `ao-lado-da-estrutura.png`.

## Gramática

| Elemento | Regra | Na casa |
|---|---|---|
| Implantação | alinhada à grade (rotações de 90°), sobre platô do relevo | platô de 0,75 u em (−6,5; −8) |
| Base | pedra lilás-quente em blocos de 3 tons, 0,6 u enterrada | fundação até y = −5 voxels |
| Paredes | estuque claro **liso** (sem pintas), cantos amaciados | `#f4e4c4` |
| Estrutura | enxaimel escuro de 1 voxel: prumos de canto, frechal, mãos-francesas curvas | `#7a4a3a` |
| Telhado | côncavo, beiral largo (3 voxels), cumeeira selada, pontas levantadas | fiadas: linha escura a cada 3 voxels, luz/sombra só pela normal |
| Prumo | cisalhamento de 0,08 voxel por voxel de altura | casa "fora de prumo" de conto |
| Porta | em arco, deslocada do centro, ~1,5 u, com postigo iluminado | + alpendre de telha e escada de 3 degraus |
| Janelas | emissivas, com pinázio, venezianas teal e floreira | 1 frontal grande, laterais menores |
| Chaminé | torta, de pedra, com capelo coral | |
| Luz | ≥ 1 luz quente (lanterna ou janela) | lanterna ao lado da porta |
| Vida | ≥ 1 toque vegetal (trepadeira, musgo, floreira) | trepadeira no canto, musgo no beiral |

## Escala

Porta 1,5 u (1,25× o herói); beiral a ~2 u; cumeeira a ~4,4 u; base 4,5 × 3,5 u. O herói deve
caber com folga na porta e o beiral deve passar acima da cabeça.

## Relação com o terreno

Platô no relevo (função `World.height`), escada de pedra até o nível do caminho, sem degraus de
terreno cortando a soleira. Decoração (barris, caixotes) encostada nas paredes, nunca no caminho.

## Para novas construções

Reutilize a gramática e mude **um** traço de silhueta por construção (torre, alpendre largo,
água-furtada). Telhado sempre saturado (coral, teal ou mostarda) para ler a 30 u. Nada de alvenaria
"realista" com ruído de 1 voxel.

## Pendências conhecidas

O telhado ainda mostra degraus diagonais longos na água inclinada (inerente ao voxel de 1/8 u); o
alpendre é discreto visto de longe. Ver [../validation/relatorio-rodada-02.md](../historico/relatorios/relatorio-rodada-02.md).

## Rodada 06 — posto de trilha (`env_e.trail_post`, candidato)

Abrigo aberto de viajantes, MENOR que a casa e da mesma cultura: base de pedra assentada (`EB.stones`) com
degrau e musgo, quatro esteios de `TIMBER` em sapatas de pedra, frechais, mãos-francesas curvas, telhado
côncavo suave (`(1 − t)^1,12`, cumeeira cede 1 voxel no meio) em fiadas coral, oitões de estuque com
enxaimel, quadro de avisos/mapas com banco, lanterna quente no beiral, placa-seta num esteio; fundos com
contraventamento em X, barril e hera. Cisalhamento 0,06.

Lições (valem para qualquer construção nova):
- **Cisalhamento quebra peças de 1 voxel de largura** (hera, mão-francesa, lápis): depois do fora-de-prumo
  elas só se tocam na diagonal e viram fragmentos. Faça traços finos com `charlib_e.connect_path` e/ou 2 de
  largura; confira com `tools/asset_audit.py`.
- Telhado côncavo forte (expoente 1,35) com pico nos oitões lê como pagode: a casa usa curva suave.
- Compare sempre ao lado da casa (`round-06/estrutura/comparacao-com-a-casa.png`) e com herói + NPC.
