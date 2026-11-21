package vx.game.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Rodada 13 — a mochila: {@link #SLOTS} espaços, cada um vazio ou com uma {@link ItemStack}.
 * <ul>
 *   <li><b>Transacional:</b> {@link #add(List)} entrega TUDO o que foi pedido ou NADA (confere antes numa cópia); nunca
 *   metade marcada como coletada.</li>
 *   <li><b>Determinística:</b> primeiro completa as pilhas do mesmo item, na ordem dos espaços; o resto vai para os
 *   espaços vazios, em ordem, cada um até a pilha máxima. A mesma sequência de coletas dá sempre a mesma mochila.</li>
 *   <li><b>Sem número inválido:</b> quantidade ≤ 0, maior que o pedido possível ou acima da pilha é recusada.</li>
 * </ul>
 */
public final class Inventory {
    public static final int SLOTS = 24, MAX_GRANT = 9999;
    private final ItemStack[] slots = new ItemStack[SLOTS];

    /** Um pedido de entrega: o item e quantas unidades. */
    public record Grant(ItemDefinition item, int count) {
        public Grant {
            if (item == null) throw new IllegalArgumentException("entrega sem item");
            if (count < 1 || count > MAX_GRANT) throw new IllegalArgumentException("quantidade inválida: " + count);
        }
    }

    public int size() { return SLOTS; }

    public ItemStack slot(int i) { return i >= 0 && i < SLOTS ? slots[i] : null; }

    public int used() {
        int n = 0;
        for (ItemStack s : slots) if (s != null) n++;
        return n;
    }

    public boolean isEmpty() { return used() == 0; }

    /** Quantas unidades do item {@code id} há em todos os espaços. */
    public int count(String id) {
        int n = 0;
        for (ItemStack s : slots) if (s != null && s.item().id().equals(id)) n += s.count();
        return n;
    }

    /** Tudo cabe? Não muda nada. */
    public boolean fits(List<Grant> grants) { return place(slots.clone(), grants); }

    /** Entrega tudo ou nada. Devolve se entregou. */
    public boolean add(List<Grant> grants) {
        ItemStack[] copy = slots.clone();
        if (!place(copy, grants)) return false;
        System.arraycopy(copy, 0, slots, 0, SLOTS);
        return true;
    }

    public boolean add(ItemDefinition item, int count) { return add(List.of(new Grant(item, count))); }

    /** Tira {@code n} unidades do espaço {@code i} (o espaço esvazia quando chega a 0). Devolve se tirou. */
    public boolean remove(int i, int n) {
        ItemStack s = slot(i);
        if (s == null || n < 1 || n > s.count()) return false;
        slots[i] = s.count() == n ? null : new ItemStack(s.item(), s.count() - n);
        return true;
    }

    private static boolean place(ItemStack[] a, List<Grant> grants) {
        for (Grant g : grants) {
            int left = g.count();
            for (int i = 0; i < SLOTS && left > 0; i++) {                   // completa as pilhas do mesmo item
                ItemStack s = a[i];
                if (s == null || !s.item().id().equals(g.item().id())) continue;
                int put = Math.min(left, g.item().stack() - s.count());
                if (put > 0) { a[i] = new ItemStack(s.item(), s.count() + put); left -= put; }
            }
            for (int i = 0; i < SLOTS && left > 0; i++) {                   // o resto, nos espaços vazios
                if (a[i] != null) continue;
                int put = Math.min(left, g.item().stack());
                a[i] = new ItemStack(g.item(), put);
                left -= put;
            }
            if (left > 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ save

    /** Só os espaços ocupados: {@code [{"espaco": 0, "item": "item.ervas_trilha", "qtd": 3}, …]}. */
    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("espaco", i);
            o.addProperty("item", slots[i].item().id());
            o.addProperty("qtd", slots[i].count());
            a.add(o);
        }
        return a;
    }

    /**
     * Lê o que {@link #toJson} escreveu. Estrutura ou número inválido = {@link ItemProgress.Invalid}; id que o catálogo não
     * tem = {@link ItemProgress.UnknownItem} (o arquivo está íntegro: é de um jogo com mais itens).
     */
    public static Inventory fromJson(JsonArray a, ItemCatalog cat) {
        if (a.size() > SLOTS) throw new ItemProgress.Invalid("espaços demais na mochila");
        Inventory inv = new Inventory();
        for (JsonElement e : a) {
            if (!e.isJsonObject()) throw new ItemProgress.Invalid("espaço da mochila que não é objeto");
            JsonObject o = e.getAsJsonObject();
            int i = ItemProgress.integer(o, "espaco", 0, SLOTS - 1);
            String id = ItemProgress.string(o, "item");
            if (!ItemCatalog.ID.matcher(id).matches()) throw new ItemProgress.Invalid("id de item inválido: " + id);
            ItemDefinition d = cat.get(id);
            if (d == null) throw new ItemProgress.UnknownItem("item desconhecido: " + id);
            int n = ItemProgress.integer(o, "qtd", 1, d.stack());
            if (inv.slots[i] != null) throw new ItemProgress.Invalid("espaço repetido: " + i);
            inv.slots[i] = new ItemStack(d, n);
        }
        return inv;
    }
}
