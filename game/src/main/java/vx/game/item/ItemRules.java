package vx.game.item;

import java.nio.file.Path;
import java.util.Set;

/**
 * Rodada 13 — o catálogo de itens e a tabela de recompensas de uma sessão de jogo (lidos uma vez ao montar a cena). Diz,
 * para o leitor de save, de que tipo é uma fonte coletada que a cena de agora não tem.
 */
public record ItemRules(ItemCatalog catalog, LootTable loot) {
    /** Sem itens (cenas sem jogo, leitores antigos): todo id de item é desconhecido. */
    public static final ItemRules NONE = new ItemRules(ItemCatalog.EMPTY, LootTable.EMPTY);

    public static ItemRules load(Path assets) {
        ItemCatalog c = ItemCatalog.load(assets);
        return new ItemRules(c, LootTable.load(assets, c));
    }

    /** Como uma fonte coletada se encaixa na cena: existe, é de outra região, ou é de um jogo mais novo. */
    public enum SourceFit { KNOWN, OTHER_REGION, NEWER_GAME }

    /** {@code objects}: os ids de objeto e de descoberta da cena de agora. */
    public SourceFit fit(String source, Set<String> objects) {
        if (source.startsWith(LootTable.FIND + ":"))
            return objects.contains(source.substring(LootTable.FIND.length() + 1)) ? SourceFit.KNOWN : SourceFit.OTHER_REGION;
        if (source.startsWith(LootTable.GIFT + ":"))
            return loot.gift(source.substring(LootTable.GIFT.length() + 1)) != null ? SourceFit.KNOWN : SourceFit.NEWER_GAME;
        return SourceFit.NEWER_GAME;
    }
}
