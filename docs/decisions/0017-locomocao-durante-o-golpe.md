# 0017 — Locomoção durante o golpe: teto de velocidade por fase, pernas na passada real, perfis de sensação (rodada 09.2)

**Status:** aceita (rodada 09.2). Complementa o [ADR 0015](0015-combate-por-trajetoria-e-passo-fixo.md) (dano pela
trajetória, passo fixo). Detalhes: [combate.md](../architecture/combate.md) §14.

## Contexto

O proprietário jogou a rodada 09.1 e achou o combate travado: durante o golpe o herói só avançava o trecho autorado e
freava; não dava para recuar, andar de lado nem mudar de direção até o golpe acabar. Locomoção e corrida também eram
lentas para personagens pequenos (1,14 e 2,33 u/s).

A trajetória da arma é validada parada (`viewer --check`, `interrupcao-fina`) e é ela que causa dano. Duas
armadilhas óbvias:
- deixar o corpo deslizar com a base do golpe parada (pé patinando);
- usar *root motion* visual que não bate com a posição lógica.

## Decisão

1. **O jogador continua dirigindo o corpo durante o golpe.**
   - A velocidade desejada é a da entrada, limitada por um teto por fase (preparação, execução, continuação,
     recuperação), em fração da velocidade de andar do perfil.
   - O avanço autorado da execução soma por cima.
   - O giro do corpo também tem taxa por fase. Na execução é pequeno, para a trajetória ficar.
2. **As pernas seguem a passada real por baixo da ação** (`Motion.legW`, `legClip`, `legT`, `legDir`).
   - Tronco, braços, quadril, inclinação e arma continuam exatamente os do golpe validado; só os alvos dos pés mudam.
   - A fase da passada avança pela distância percorrida, então o pé de apoio fica plantado.
   - O peso sobe e desce suave com a velocidade: parado, volta à base autorada do golpe.
3. **Passada com rumo** (`Motion.locoDir`).
   - Andar de lado e de costas move o pé ao longo do deslocamento.
   - A coxa abre (rotação lateral) para o pé ir de lado; de lado a passada encolhe e os pés se afastam, para não
     cruzarem.
   - Com rumo 0 e passada 1, a pose é idêntica à das rodadas anteriores (gate dos presets).
4. **A lâmina que fere é avaliada com as mesmas pernas do desenho** (`Hero.bladeAt(..., Player)`).
5. **Validação de golpe andando:** `viewer --check golpe-andando` passa cada golpe jogável, a guarda e o bloqueio por
   cima das pernas andando e correndo, em quatro rumos e quatro fases do passo.
6. **Perfis de sensação** em `combate_e.json → sensacao`, para comparar:
   - **A:** a 09.1 (golpe parado);
   - **B:** responsivo;
   - **C:** ágil, o padrão.
   - Cada um define velocidade, passada, aceleração, frenagem, giro, teto e giro por fase do golpe e controle no ar.
   - A locomoção mais rápida vem sobretudo da cadência: as pernas do herói têm 0,24 u, e a passada já estava no limite
     do alcance.
   - A troca é pela pausa (Sensação), por `-Dvx.sensacao=` ou pela preferência gravada.

## Consequências

- A ação continua com peso: antecipação, execução e recuperação existem; o jogador só não fica preso a elas.
- O validador continua sendo a referência. Instantes de encadear e liberar não mudaram. Janelas novas (esquiva) foram
  medidas, não chutadas.
- O perfil A existe para comparar com a 09.1 no mesmo binário. A escolha final do padrão é do teste físico do
  proprietário.
- Custo: uma passada a mais avaliada por personagem com golpe (desprezível).
