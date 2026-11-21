package vx.game.item;

/**
 * Rodada 13 — o que um item É (do catálogo {@code itens_e.json}, nunca por classe): id estável ({@code item.*}), nome e
 * descrição da tela, categoria, quantas unidades cabem numa pilha da mochila ({@code stack}, 1 = não empilha), o modelo
 * voxel (rodada 13.1: {@code assets/items/e/<model>.vox}, renderizado pelo jogo) e, se dá para usar, o uso. A identidade é
 * o id: o nome pode mudar sem mexer em save nenhum.
 */
public record ItemDefinition(String id, String name, String description, String category, int stack, String model, ItemUse use) {
    public boolean usable() { return use != null; }

    public boolean stackable() { return stack > 1; }
}
