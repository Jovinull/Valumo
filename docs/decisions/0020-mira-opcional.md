# 0020 — Mira opcional: livre por padrão, trava com Q, troca com Tab pela ordem na tela (rodada 09.2)

**Status:** aceita (rodada 09.2). Substitui o ajuste automático de câmera da rodada 09.1 como padrão (ele continua em
`-Dvx.camera=091`, só para comparar). Detalhes: [combate.md](../architecture/combate.md) §16.

## Contexto

O proprietário não quer câmera presa automaticamente ao inimigo. O ajuste da 09.1 girava a vista 35° e subia a
inclinação até ~52° com o fungo colado: bom para enquadrar, ruim para a liberdade e cedo demais para virar câmera de
cima. Com vários inimigos, "o inimigo" deixa de existir.

## Decisão

1. **Mira livre (padrão).**
   - **Câmera:** é a da exploração; o mouse manda no rumo e na inclinação, sem giro nem subida automáticos.
   - **Composição na luta:** com um inimigo engajado a menos de 4,5 u, o pivô vai 0,65 u para a direita (ombro).
   - **Busca de vista:** usa a da luta (desvia de pedra, tronco e copa; o olho nunca fica dentro de nada), sem viés.
   - **Golpe:** vai para onde o jogador empurra, com uma assistência suave (40°, 2,4 u) para o inimigo mais perto desse
     rumo.
2. **Mira travada (Q).**
   - **Escolha do alvo:** o melhor inimigo vivo dentro de 8 u, com linha de visão. "Melhor" = distância + afastamento do
     centro da vista. Nunca um morto, nunca um atrás de parede.
   - **Rumo da câmera:** o rumo do jogador gira para trás do herói em relação ao alvo, com velocidade limitada
     (4 rad/s).
   - **Lado do alvo na tela:** 35° com ele colado e 22° com ele longe. A cabeça grande do herói esconderia um fungo
     próximo com menos que isso.
   - **Inclinação:** terceira pessoa, até 0,70 rad (≈ 40°) com o alvo colado.
   - **Corpo:** o herói fica de frente para o alvo e anda de lado e de costas com a passada certa (ADR 0017); correndo
     (Shift), vira para onde corre.
   - **Golpes:** vão para o alvo.
   - **Mouse:** desloca a vista ±0,40 rad e ajusta a inclinação.
3. **Troca (Tab / Shift+Tab):** próximo à direita / à esquerda NA TELA (ordem pelo ângulo em relação à vista, dando a
   volta). Nunca pela ordem de criação.
4. **Soltura:** Q de novo; alvo morto (passa ao engajado mais perto ou solta, `mira.aoMorrer`); além de 11 u; escondido
   por mais de 1,2 s.
5. **Referência do WASD** (`FollowCamera.inputYaw`): o rumo do mouse na hora, mais o desvio automático da câmera seguido
   no máximo a 2,5 rad/s. Um desvio grande da câmera (parede, encurralado) ou a soltura da mira não viram o controle de
   uma vez. Esse era o P38 da 09.1: esquiva para o lado errado.
6. **Marcador:** cantoneiras douradas fora do contorno do alvo (não cobrem cabeça nem preparação) e um losango no chão.

## Consequências

- **Livre:** com o fungo colado bem atrás da cabeça do herói, ele fica escondido até o jogador mexer o mouse. É o preço
  de não girar sozinho. Medido em `CameraModesTest` e registrado como problema aberto.
- **Travada:** nos 10 cenários em movimento da 09.1, cerca de 19% do tempo "ruim", contra 11% do ajuste automático da
  09.1 (que subia até 52°) e 37–59% da livre sem corrigir a mira.
- **Arquitetura:** pronta para comportamentos por tipo de confronto (chefe, grupo), sem câmera de chefe nesta rodada.
