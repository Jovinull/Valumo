# 0013 — Módulo `game` sobre o runtime do viewer (rodada 08)

**Status:** aceita (rodada 08).

## Contexto

O módulo `viewer` contém duas coisas:
- o **runtime** que qualquer tela do jogo precisa: `Renderer`, `World`/`Terrain`, `Assets`, `Actor`/`Animator`,
  `Composer`/`AsyncComposer`, `UiKit`, `Creator`;
- a **ferramenta de inspeção**: `App`, `Main`, bench, capturas, checagens.

A rodada 08 precisava de regras de jogo (jogador, colisão, câmera, interação) sem misturá-las à ferramenta e sem
duplicar o renderer nem o carregamento de assets.

## Decisão

- Módulo Gradle **`game`** (`vx.game`), dependendo de `engine` e de `viewer`. As regras ficam todas nele, e as
  testáveis não usam OpenGL (`GameLogic`, `Player`, `FollowCamera`, `Collision`, `SceneDef`, `Input`).
- O viewer só ganhou ganchos genéricos, sem regra de jogo: `Terrain`, `World.placements/bodies`, `UiKit`,
  `Creator.start` e o botão de saída configurável.
- **Não** se separa agora um módulo `runtime`. Mover o runtime para fora do viewer tocaria quase todos os
  arquivos e as ferramentas de QA, sem ganho para esta rodada.

## Consequências

- O jogo depende de um módulo que também carrega código de inspeção (capturas, bench) no classpath. O custo é
  pequeno: são poucos MB e nada roda sem ser chamado.
- **Critério para separar `runtime`:** quando um segundo aplicativo além do jogo precisar do runtime sem as
  ferramentas, ou quando o jogo for empacotado para distribuição. Nesse ponto, mover para `runtime` as classes
  sem `App`/`Main`/`Review*`/`Check*`.
- Nenhuma dependência externa nova.
