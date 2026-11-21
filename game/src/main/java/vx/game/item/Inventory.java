package vx.game.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Rodada 13 — a mochila; rodada 13.1 — SEM limite de espaços: uma lista de pilhas ({@link ItemStack}) na ordem de chegada.
 * <ul>
 *   <li><b>Transacional:</b> {@link #add(List)} entrega TUDO o que foi pedido ou NADA (confere antes numa cópia); nunca
 *   metade marcada como coletada.</li>
 *   <li><b>Determinística:</b> primeiro completa as pilhas do mesmo item, na ordem; o resto vira pilhas novas no fim, cada
 *   uma até a pilha máxima do item. A mesma sequência de coletas dá sempre a mesma mochila.</li>
 *   <li><b>Compacta:</b> a pilha que esvazia sai da lista (não fica buraco).</li>
 *   <li><b>Teto técnico</b> ({@link #MAX_STACKS}): só para o leitor de save nunca alocar sem limite; o jogo normal não chega
 *   perto. Acima dele a entrega é recusada como antes ("mochila cheia").</li>
 *   <li><b>Sem número inválido:</b> quantidade ≤ 0, maior que o pedido possível ou acima da pilha é recusada.</li>
 * </ul>
 */
public final class Inventory {
    public static final int MAX_STACKS = 4096, MAX_GRANT = 9999;
    private final int limit;
    private final ArrayList<ItemStack> stacks = new ArrayList<>();

    /** Um pedido de entrega: o item e quantas unidades. */
    public record Grant(ItemDefinition item, int count) {
        public Grant {
            if (item == null) throw new IllegalArgumentException("entrega sem item");
            if (count < 1 || count > MAX_GRANT) throw new IllegalArgumentException("quantidade inválida: " + count);
        }
    }

    public Inventory() { this(MAX_STACKS); }

    private Inventory(int limit) { this.limit = limit; }

    /** Mochila com um teto pequeno (testes do caminho "mochila cheia", que no jogo só existe no teto técnico). */
    public static Inventory limited(int stacks) {
        if (stacks < 1 || stacks > MAX_STACKS) throw new IllegalArgumentException("teto inválido: " + stacks);
        return new Inventory(stacks);
    }

    /** Quantas pilhas há (a mochila não tem espaços fixos). */
    public int size() { return stacks.size(); }

    /** O teto técnico desta mochila. */
    public int limit() { return limit; }

    public ItemStack slot(int i) { return i >= 0 && i < stacks.size() ? stacks.get(i) : null; }

    public int used() { return stacks.size(); }

    public boolean isEmpty() { return stacks.isEmpty(); }

    /** Quantas unidades do item {@code id} há em todas as pilhas. */
    public int count(String id) {
        int n = 0;
        for (ItemStack s : stacks) if (s.item().id().equals(id)) n += s.count();
        return n;
    }

    /** Tudo cabe? Não muda nada. */
    public boolean fits(List<Grant> grants) { return place(new ArrayList<>(stacks), grants, limit); }

    /** Entrega tudo ou nada. Devolve se entregou. */
    public boolean add(List<Grant> grants) {
        ArrayList<ItemStack> copy = new ArrayList<>(stacks);
        if (!place(copy, grants, limit)) return false;
        stacks.clear();
        stacks.addAll(copy);
        return true;
    }

    public boolean add(ItemDefinition item, int count) { return add(List.of(new Grant(item, count))); }

    /** Tira {@code n} unidades da pilha {@code i} (a pilha sai da lista quando chega a 0). Devolve se tirou. */
    public boolean remove(int i, int n) {
        ItemStack s = slot(i);
        if (s == null || n < 1 || n > s.count()) return false;
        if (s.count() == n) stacks.remove(i);
        else stacks.set(i, new ItemStack(s.item(), s.count() - n));
        return true;
    }

    private static boolean place(List<ItemStack> a, List<Grant> grants, int limit) {
        for (Grant g : grants) {
            int left = g.count();
            for (int i = 0; i < a.size() && left > 0; i++) {              // completa as pilhas do mesmo item
                ItemStack s = a.get(i);
                if (!s.item().id().equals(g.item().id())) continue;
                int put = Math.min(left, g.item().stack() - s.count());
                if (put > 0) { a.set(i, new ItemStack(s.item(), s.count() + put)); left -= put; }
            }
            while (left > 0 && a.size() < limit) {                        // o resto, em pilhas novas no fim
                int put = Math.min(left, g.item().stack());
                a.add(new ItemStack(g.item(), put));
                left -= put;
            }
            if (left > 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ save

    /** Pilhas em ordem: {@code [{"espaco": 0, "item": "item.ervas_trilha", "qtd": 3}, …]} ("espaco" = a posição na lista). */
    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (int i = 0; i < stacks.size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("espaco", i);
            o.addProperty("item", stacks.get(i).item().id());
            o.addProperty("qtd", stacks.get(i).count());
            a.add(o);
        }
        return a;
    }

    /**
     * Lê o que {@link #toJson} escreveu — e o da rodada 13 (24 espaços, com buracos): as pilhas entram na ordem de "espaco" e
     * a lista se compacta. Estrutura ou número inválido = {@link ItemProgress.Invalid}; id que o catálogo não tem =
     * {@link ItemProgress.UnknownItem} (o arquivo está íntegro: é de um jogo com mais itens).
     */
    public static Inventory fromJson(JsonArray a, ItemCatalog cat) {
        if (a.size() > MAX_STACKS) throw new ItemProgress.Invalid("pilhas demais na mochila");
        TreeMap<Integer, ItemStack> byPos = new TreeMap<>();
        for (JsonElement e : a) {
            if (!e.isJsonObject()) throw new ItemProgress.Invalid("espaço da mochila que não é objeto");
            JsonObject o = e.getAsJsonObject();
            int i = ItemProgress.integer(o, "espaco", 0, MAX_STACKS - 1);
            String id = ItemProgress.string(o, "item");
            if (!ItemCatalog.ID.matcher(id).matches()) throw new ItemProgress.Invalid("id de item inválido: " + id);
            ItemDefinition d = cat.get(id);
            if (d == null) throw new ItemProgress.UnknownItem("item desconhecido: " + id);
            int n = ItemProgress.integer(o, "qtd", 1, d.stack());
            if (byPos.containsKey(i)) throw new ItemProgress.Invalid("espaço repetido: " + i);
            byPos.put(i, new ItemStack(d, n));
        }
        Inventory inv = new Inventory();
        inv.stacks.addAll(byPos.values());
        return inv;
    }
}
