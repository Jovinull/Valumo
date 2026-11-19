# ADR 0001 — Java 25 + LWJGL 3 + OpenGL 3.3 core, sem engine

**Estado:** aceito (rodada 01). Os módulos `lwjgl-openal` e `lwjgl-stb` foram acrescentados depois pelos ADRs 0016 e 0023.

## Contexto
O proprietário quer engine própria em Java, sem Unity/Unreal/Godot/jMonkeyEngine/LibGDX, com
bibliotecas de baixo nível quando houver justificativa.

## Decisão
- **Java 25 (LTS)** com Gradle 9.9.1 (wrapper) e toolchain declarada no build.
- **LWJGL 3.4.3**: GLFW (janela/entrada) e OpenGL (render). Só os módulos `lwjgl`, `lwjgl-glfw`,
  `lwjgl-opengl`.
- **OpenGL 3.3 core** como piso: roda em GPU integrada e em Mesa; tudo o que a rodada precisa
  (FBO, MRT, texturas float, sombra com comparação) existe em 3.3.
- **JOML 1.10.8** para matemática; **Gson 2.13.2** para JSON (rigs, manifestos, cena).
- PNG por `javax.imageio` (JDK), com `-Djava.awt.headless=true` para não conflitar com o GLFW.

## Alternativas descartadas
- Vulkan: custo de infraestrutura alto demais para uma prova visual; revisitar só se o OpenGL virar gargalo.
- `lwjgl-stb` para imagens/fonte: o JDK já lê e grava PNG; a fonte do HUD é um bitmap 5×7 embutido.
- Parser JSON próprio: Gson é pequeno, estável e não esconde nenhum sistema da engine.

## Consequências
- LWJGL 3.4 em Java 25 exige `--enable-native-access=ALL-UNNAMED` (já nos argumentos do `viewer`) e
  ainda emite avisos de `sun.misc.Unsafe` na inicialização — inofensivos hoje, a acompanhar em
  versões futuras do JDK.
- Os natives configurados são `natives-linux`; outro SO exige trocar o classificador em `engine/build.gradle.kts`.
- No Wayland a janela roda via XWayland (padrão do GLFW embutido); funcionou sem ajustes.
