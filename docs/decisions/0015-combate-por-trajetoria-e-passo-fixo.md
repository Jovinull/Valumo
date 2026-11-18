# 0015 — Combate: passo fixo, dano pela trajetória da arma e interrupções autoradas (rodada 09)

**Status:** aceita (rodada 09). Complementa o [ADR 0011](0011-coreografia-por-trajetorias.md) (coreografia) e o
[ADR 0013](0013-modulo-game.md) (módulo game). Detalhes: [combate.md](../architecture/combate.md).

## Contexto

A rodada 09 transforma golpes de vitrine em combate: dano, defesa, reação, morte. Havia quatro riscos:

1. **Acertos por quadro:** a ponta da espada anda até cerca de 0,1 u em 1/480 s. A 30 FPS, testar só a pose de cada quadro
   pula alvos e torna o dano dependente da taxa.
2. **Colisão como acerto:** usar o círculo de movimento como "acertou" fere quem está atrás e ignora o alcance real do
   golpe.
3. **Interrupções:** cortar um golpe e deixar a reação assumir atravessava o corpo (espada 7/21, lâmina longa 18/47 nas
   rodadas 05–08). Aceitável sem combate, inaceitável agora.
4. **Animação ao vivo ≠ validada:** o validador aprova roteiros do `Sequencer`. Se o jogo misturasse poses de outro jeito
   (ou suavizasse por cima), o que se vê não seria o que foi validado.

## Decisão

1. **Passo lógico fixo de 1/120 s** para exploração, combate e câmera, com acumulador em `double` e desenho interpolado
   entre dois passos.
   - **Ordem do passo fixa:** herói → inimigo → separação.
   - **Pausa de impacto:** é contada em passos.
   - **Entrada:** o aperto vale para o próximo passo.
2. **Três volumes por combatente, separados:**
   - colisão de movimento (círculo);
   - hurtbox (cápsula);
   - hitbox (cápsula da lâmina ao longo da peça `weapon`, ou esfera presa ao chapéu do inimigo).

   Os volumes do validador continuam só do validador.
3. **Dano pela trajetória.**
   - **Onde:** na janela de dano autorada de cada golpe (`ativo`, dentro da execução e só com a ponta na metade da
     frente).
   - **Como:** a pose vem da própria animação em subamostras de 1/480 s, mais o rastro da ponta. A raiz é interpolada no
     passo.
   - **Quantas vezes:** um acerto por golpe por alvo.
   - **Obstáculo:** só sem sólido entre o peito e o ponto de contato.
4. **Interrupções autoradas por fase no perfil** (`"interrupt": [{"until", "rate", "exits"}]`):
   - o golpe cortado rebobina ou segue pela própria trajetória;
   - na fase em que nenhum dos dois serve, uma **saída autorada** substitui a reação (`hit_cross_h`, `hit_cross_d`).
   - Os instantes de encadear, liberar e agir depois da reação foram **medidos com o validador**, não escolhidos a olho.
5. **Uma só semântica de mistura:** `ActionLayer` (jogo) reproduz o `Sequencer` (validador e vídeos), incluindo a origem
   dupla (corte durante uma transição). Um teste compara as duas, e o herói é desenhado com a pose exata, sem suavização
   extra.
6. **Inimigo por máquina de estados pequena**, sem árvore de comportamento nem busca de caminho:
   - percepção por distância, campo e linha de visão;
   - coleira;
   - contorno simples de obstáculo.

## Alternativas descartadas

- **Dano por colisão de corpos ou por cone em frente ao herói:** ignora a arma e fere fora do alcance real.
- **Testar só a pose do quadro:** depende da taxa e pula alvos finos.
- **Golpes imunes a interrupção:** esconderia o recorte em vez de resolvê-lo. A rodada pede regras explícitas.
- **Interpolação linear da pose cortada para a reação:** é o defeito original (atravessa o corpo).
- **Física (corpos rígidos, colisão contínua genérica):** desproporcional para um golpe autorado. A subamostragem tem
  garantia medida (passo da ponta menor que o raio da hurtbox).

## Consequências

- **Determinismo:** o mesmo confronto sai idêntico em 30, 60, 120 e 240 FPS, e em qualquer taxa com a mesma chegada de
  entrada.
- **Custo:** a detecção avalia a animação cerca de 70 vezes por golpe, só na janela de dano. O custo medido fica em
  `round-09/desempenho/`.
- **Arma nova:** uma arma jogável precisa de quatro coisas:
  - janela de dano;
  - janelas de interrupção (com saídas onde preciso);
  - instantes de encadear e liberar validados;
  - o kit no `combate_e.json`.

  O procedimento está na skill `combat`.
- **Dependências:** nenhuma nova.
- **Lâmina longa:** continua fora do jogo até ganhar as mesmas janelas.
