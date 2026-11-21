package vx.game.item;

/** Rodada 13 — um espaço ocupado da mochila: o item e quantas unidades (1 a {@code item.stack()}). Imutável. */
public record ItemStack(ItemDefinition item, int count) {
    public ItemStack {
        if (item == null) throw new IllegalArgumentException("pilha sem item");
        if (count < 1 || count > item.stack()) throw new IllegalArgumentException("quantidade fora da pilha: " + count + " de " + item.id());
    }
}
