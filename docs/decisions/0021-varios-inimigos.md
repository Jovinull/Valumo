# 0021 — Vários inimigos: uma classe para todos, encontros por dados, coordenação leve e golpe que acerta cada um uma vez (rodada 09.2)

**Status:** aceita (rodada 09.2). Detalhes: [combate.md](../architecture/combate.md) §17.

## Contexto

O confronto da 09 foi feito para um fungo (`Combat.enemy`). O pedido: grupos pequenos, um ensaio de chefe com inimigos
menores, um corte amplo que acerte mais de um alvo quando a lâmina passa por eles, nada de dano em área, e nenhum ECS
gigante sem necessidade.

## Decisão

1. **`Combat.enemies`:** uma lista da mesma classe `Enemy`, com números próprios por instância.
   - `CombatDef.forSpawn` cria a cópia dos números com a variante e o lugar do encontro (casa e coleira).
   - Vida, estado, posição, dano, percepção, morte e identidade (`id`) são de cada um.
   - `Combat.enemy` continua sendo o primeiro (compatibilidade com o 1 × 1).
2. **Encontros e variantes por dados** (`combate_e.json`):
   - **encontros:** `um`, `grupo` (três fungos) e `chefe` (um ancião + dois fungos);
   - **variante `fungo_anciao`:** o mesmo modelo × 1,6, marcado `chefe` e `prototipo`. É recurso TEMPORÁRIO de teste,
     não o chefe artístico;
   - **troca:** pela pausa (Arena), por `-Dvx.encontro=` ou pela preferência gravada.
3. **Golpe contra vários:** a mesma varredura da lâmina (subamostras de 1/480 s, rastro da ponta, sólido no meio barra)
   é testada contra a hurtbox de CADA inimigo vivo, no máximo uma vez por golpe por alvo. Dois na trajetória = dois
   acertos; quem está fora da trajetória não perde nada.
4. **Grupo** (`grupo`):
   - **Vez:** só os `atacantes` (2) mais perto do herói têm a vez de chegar ao alcance (por distância, não por criação).
   - **Ataques:** no máximo 2 preparando/atacando juntos, com 0,5 s entre o começo de duas preparações.
   - **Espera:** quem não tem a vez espera no anel (1,75 u).
   - **Corpos:** se afastam uns dos outros (0,95 u) e não se sobrepõem.
   - **Fora do escopo:** busca de caminho geral.
5. **Morto não pensa:** só o tempo da morte anda. Não ataca, não se move, sai da separação e da seleção.

## Consequências

- Acrescentar um inimigo é dado: um item no encontro, ou uma variante com campos trocados.
- Inimigo de outro tipo, com outra IA, ainda é trabalho: a máquina de estados é a do fungo.
- **Vitória:** com todos mortos.
- **Medidas no relatório:** custo de IA por inimigo e de câmera por passo.
