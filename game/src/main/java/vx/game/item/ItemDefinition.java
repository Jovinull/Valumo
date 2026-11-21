package vx.game.item;

/**
 * Rodada 13 — o que um item É (do catálogo {@code itens_e.json}, nunca por classe): id estável ({@code item.*}), nome e
 * descrição da tela, categoria, quantas unidades cabem num espaço da mochila ({@code stack}, 1 = não empilha), o ícone de
 * pixel e, se dá para usar, o uso. A identidade é o id: o nome pode mudar sem mexer em save nenhum.
 */
public record ItemDefinition(String id, String name, String description, String category, int stack, String icon, ItemUse use) {
    public boolean usable() { return use != null; }

    public boolean stackable() { return stack > 1; }
}
