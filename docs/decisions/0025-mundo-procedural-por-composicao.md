# 0025 — Mundo procedural por composição validada: relevo como dado, região por semente, missão instanciada de fatos (rodada 11)

**Status:** aceita como FUNDAÇÃO experimental (rodada 11). Uma região, um modelo de missão. A direção do jogo (mundo
procedural) é do proprietário; esta é a primeira prova.

## Contexto

O Valumo não será um RPG de mundo fixo. A vila e "As Luzes do Posto" (rodada 10) são a referência de qualidade, não o
modelo de produção. O pedido:
- regiões geradas por semente, reproduzíveis, coerentes com a direção E;
- POIs que as missões consultem por fatos ("existe um posto alcançável?") e não por coordenadas;
- missões que não possam ser impossíveis;
- persistência que não mova uma missão aceita.

Restrições:
- sem engine;
- os assets são os aprovados;
- o mundo fixo, as referências canônicas e o gate não podem mudar.

## Decisão

1. **O relevo vira dado** (`vx.viewer.TerrainField`).
   - O `World` e o `Terrain` recebem o campo.
   - `World.LEGACY` é a função de antes, sem nenhuma conta nova: as referências 22/22 saem com desvio 0.
   - O campo procedural (`ProceduralField`) usa o mesmo vocabulário (ruído, encostas, patamares, bacia), com os
     parâmetros e os lugares da semente.
2. **A região é COMPOSTA de componentes autorais** (`RegionGenerator`). A autoria está nos componentes, a variedade na
   composição.
   - **Componentes:** os props e NPCs da E, três arranjos de assentamento e três composições de mata.
   - **Composição:** lugares, trilha, lago e colina; nada de voxel por voxel aleatório.
3. **Cada decisão tem um gerador derivado por rótulo** (`Rng`: SplitMix64 + FNV-1a). Os resultados são iguais em toda
   JVM, não dependem de ordem, e uma árvore a mais não move o encontro.
4. **A composição só vale se passar na colisão REAL** (`Reachability`): a mesma `Collision` do jogo e o T1, numa busca em
   grade com o raio e o degrau do herói.
   - Reprovou: outra tentativa, no máximo 4. Nenhuma passou: a semente é recusada com os motivos. Nunca há laço
     infinito, e nunca há "caso raro" aceito.
5. **A região vira os MESMOS documentos das cenas em arquivo:** desenho, jogo e combate (`ProceduralScene`). O jogo, o
   combate, a missão, o save e o som são os de sempre.
6. **Missões: modelo + instância.**
   - Um modelo (`quest.template.restaurar_sinal`) declara os fatos que exige e os vínculos (`$encontro`, `$marco`,
     `$doador`).
   - A instância é um `QuestDefinition` comum, com ids estáveis do mundo (`quest.instance.s1024.r0.posto_trilha_0.1`).
   - O doador é escolhido por papel, e as falas vêm em vozes autorais por papel, com parâmetros que correspondem aos
     fatos.
   - Sem os fatos, não há instância.
7. **A fundação não impõe "derrotar → interagir → falar":**
   - `conclusao: "ultimo_objetivo"` termina sem voltar ao doador;
   - um NPC pode ter várias missões em sequência.
8. **Modo experimental separado:** `-Dvx.mundo=procedural -Dvx.semente=N`, com o mesmo executável e o mesmo renderer. O
   modo padrão continua sendo a vila da rodada 10.

## Alternativas descartadas

- **Ruído com props jogados ao acaso:** sai uma vila caótica, com objetos dentro de árvores e missões impossíveis.
- **Mapas inteiros feitos à mão, sorteando entre eles:** não escala e não é procedural.
- **Gerar casas voxel a voxel:** perde a autoria da direção E, e os assets novos não foram aprovados.
- **Validar alcance por linha reta entre pontos:** a linha atravessa pedra e água. A grade com a colisão real é o que o
  herói sente.
- **Uma API generativa para os textos:** o núcleo precisa funcionar offline e com texto revisado.
- **`java.util.Random` e `HashMap` na geração:** dependem de implementação e de ordem.

## Consequências

- Um modelo de missão ou um tipo de POI novo é dados, regras de composição e uma conferência a mais
  ([guia de POI](../processo/como-adicionar-um-tipo-de-poi.md)).
- Toda mudança que altera o resultado de uma semente sobe `RegionGenerator.VERSION` (o save recusa a versão antiga).
- O custo é ~50–200 ms por região, uma vez, antes do primeiro quadro (números no relatório da rodada 11). O streaming de
  várias regiões fica planejado ([mundo-procedural.md](../architecture/mundo-procedural.md) §6).
