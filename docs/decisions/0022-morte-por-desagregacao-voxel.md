# 0022 — Morte por desagregação voxel: o corpo se desfaz nos próprios blocos (rodada 09.2)

**Status:** aceita (rodada 09.2). Substitui a morte da rodada 09 (cair de costas e sumir encolhendo), que fica em
`-Dvx.morte=antiga` só para comparar. Detalhes: [combate.md](../architecture/combate.md) §18.

## Contexto

A morte antiga caía e encolhia o modelo inteiro. O proprietário viu o corpo esticar e achatar, e o sumiço encolhendo
parecia artificial. O pedido: desagregação voxel original e contida, ligada à criatura, sem física completa, leve com
vários morrendo juntos, e validada em vídeo.

## Decisão

1. **Sequência:** ao morrer, o inimigo reage ao golpe por 0,14 s (pose de reação), some e no mesmo instante vira os
   PRÓPRIOS blocos.
   - **Origem dos blocos:** `VoxelCloud` agrupa os voxels do rig em 2³, com a cor mais comum de cada grupo, ou seja, a
     paleta e as bandas pintadas do modelo.
   - **Posição inicial:** cada fragmento nasce onde estava na pose do último quadro desenhado.
2. **Movimento leve e determinístico** (`DeathFx`, sem OpenGL, testável):
   - a semente vem do inimigo e do passo;
   - passo fixo de 1/120 s;
   - no máximo 150 fragmentos por morte (amostra uniforme);
   - duração fixa de 1,35 s.
   - **A onda:** solta primeiro o lado do golpe, depois o resto, em até 0,22 s.
   - **Impulso:** pequeno, para fora e para cima, mais o empurrão do golpe.
   - **Queda:** gravidade, quique baixo, atrito, giro.
   - **Fim:** cada bloco some encolhendo no seu tempo (o corpo inteiro nunca estica nem achata). No fim não resta nada.
3. **Variantes:**
   - **D1:** só os blocos.
   - **D2 (padrão):** os blocos clareiam um pouco antes de sumir, e até 7 sementes de luz (motivo P1, emissivas,
     pequenas) sobem devagar.
4. **Desenho:**
   - todos os fragmentos de todas as mortes numa malha dinâmica só, ou seja, um desenho por quadro, com
     `Mesh.update` e buffer reaproveitado;
   - cada fragmento é o cubo de um voxel do `VoxelMesher` (mesmo formato de vértice e de cor da cena);
   - nada de remontar a malha do modelo;
   - os fragmentos não colidem com nada do jogo.
5. **Som:** `fungo_desfaz` (estalinhos de blocos caindo e um sopro de esporos) no instante em que o corpo se desfaz, junto
   da `fungo_derrota` que já existia.

## Consequências

- Serve para qualquer criatura com rig voxel; só a cor e o tamanho dos blocos vêm do modelo.
- Custo medido no relatório (lógica por quadro, montagem da malha, pico de fragmentos), também com várias mortes juntas.
- A escolha D1 × D2 é visual e cabe ao proprietário. O vídeo lado a lado está no pacote da rodada.
