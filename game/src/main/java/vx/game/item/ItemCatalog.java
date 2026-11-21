package vx.game.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rodada 13 — o catálogo oficial de itens ({@code assets/scenes/itens_e.json}, escrito à mão). Lido UMA vez ao montar a
 * cena e conferido inteiro: formato e versão, ids únicos ({@code item.[a-z0-9_]+}), nome e descrição curtos, categoria
 * declarada, pilha entre 1 e {@link #MAX_STACK}, modelo voxel com nome válido (e, ao ler dos assets, gerado em
 * {@code assets/items/e/itens.json} — rodada 13.1), efeito que o código conhece
 * ({@link ItemEffects}) com número dentro do limite. Qualquer erro recusa o catálogo inteiro com o motivo — nunca um item
 * pela metade. A ordem do arquivo é a ordem de exibição.
 */
public final class ItemCatalog {
    public static final String FORMAT = "valumo.itens";
    public static final int VERSION = 1, MAX_STACK = 99, MAX_ITEMS = 500;
    static final Pattern ID = Pattern.compile("item\\.[a-z0-9_]{1,40}"), MODEL = Pattern.compile("[a-z0-9_]{1,24}");
    /** Rodada 13.1: o manifesto dos modelos dos itens (gerado por {@code tools/itens_e.py}). */
    public static final String MODELS = "items/e/itens.json";
    static final long MAX_BYTES = 256 * 1024;

    private final Map<String, ItemDefinition> items;
    private final Map<String, String> categories;

    /** Catálogo vazio: cenas sem itens e leitores de save antigos (todo id de item é desconhecido). */
    public static final ItemCatalog EMPTY = new ItemCatalog(Map.of(), Map.of());

    private ItemCatalog(Map<String, ItemDefinition> items, Map<String, String> categories) {
        this.items = Collections.unmodifiableMap(items);
        this.categories = Collections.unmodifiableMap(categories);
    }

    /** Catálogo recusado: o motivo vai na mensagem. */
    public static final class Invalid extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public Invalid(String msg) { super(msg); }
    }

    public static ItemCatalog load(Path assets) {
        Path f = assets.resolve("scenes/itens_e.json");
        try {
            if (Files.size(f) > MAX_BYTES) throw new Invalid("itens_e.json grande demais");
            ItemCatalog c = parse(Files.readString(f));
            Path m = assets.resolve(MODELS);                                  // rodada 13.1: todo modelo foi gerado
            if (!Files.exists(m)) throw new Invalid("modelos dos itens ausentes (" + MODELS + "): gere os assets");
            JsonObject made = JsonParser.parseString(Files.readString(m)).getAsJsonObject();
            for (ItemDefinition d : c.all())
                if (!made.has(d.model())) throw new Invalid(d.id() + ": modelo não gerado: " + d.model());
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ItemCatalog parse(String json) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new Invalid("o catálogo não é um objeto JSON");
            o = e.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new Invalid("JSON ilegível");
        }
        if (!FORMAT.equals(str(o, "formato"))) throw new Invalid("formato não é " + FORMAT);
        if (!o.has("versao") || !o.get("versao").isJsonPrimitive() || o.get("versao").getAsDouble() != VERSION)
            throw new Invalid("versão do catálogo desconhecida");
        if (!o.has("categorias") || !o.get("categorias").isJsonObject()) throw new Invalid("categorias ausentes");
        Map<String, String> cats = new LinkedHashMap<>();
        for (var c : o.getAsJsonObject("categorias").entrySet()) {
            if (!c.getKey().matches("[a-z_]{1,24}")) throw new Invalid("categoria com nome inválido: " + c.getKey());
            String label = c.getValue().isJsonPrimitive() ? c.getValue().getAsString().strip() : "";
            if (label.isEmpty() || label.length() > 24) throw new Invalid("categoria sem rótulo: " + c.getKey());
            cats.put(c.getKey(), label);
        }
        if (!o.has("itens") || !o.get("itens").isJsonArray()) throw new Invalid("itens ausentes");
        var arr = o.getAsJsonArray("itens");
        if (arr.size() > MAX_ITEMS) throw new Invalid("itens demais");
        Map<String, ItemDefinition> items = new LinkedHashMap<>();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) throw new Invalid("item que não é objeto");
            JsonObject i = e.getAsJsonObject();
            String id = str(i, "id");
            if (id == null || !ID.matcher(id).matches()) throw new Invalid("id de item inválido: " + id);
            if (items.containsKey(id)) throw new Invalid("id repetido: " + id);
            String name = text(i, "nome", 32, id), desc = text(i, "descricao", 200, id), cat = str(i, "categoria"), model = str(i, "modelo");
            if (cat == null || !cats.containsKey(cat)) throw new Invalid(id + ": categoria desconhecida: " + cat);
            if (model == null || !MODEL.matcher(model).matches()) throw new Invalid(id + ": modelo inválido: " + model);
            int stack = integer(i, "pilha", 1, MAX_STACK, id);
            ItemUse use = null;
            if (i.has("uso")) {
                if (!i.get("uso").isJsonObject()) throw new Invalid(id + ": uso não é objeto");
                JsonObject u = i.getAsJsonObject("uso");
                String effect = str(u, "efeito");
                if (effect == null || !ItemEffects.KNOWN.contains(effect)) throw new Invalid(id + ": efeito desconhecido: " + effect);
                int amount = integer(u, "vida", 0, 1000, id);
                if (!ItemEffects.validAmount(effect, amount)) throw new Invalid(id + ": número do efeito fora do limite: " + amount);
                use = new ItemUse(effect, amount, text(u, "rotulo", 16, id));
            }
            items.put(id, new ItemDefinition(id, name, desc, cat, stack, model, use));
        }
        if (items.isEmpty()) throw new Invalid("catálogo sem itens");
        return new ItemCatalog(items, cats);
    }

    public ItemDefinition get(String id) { return items.get(id); }

    public boolean has(String id) { return items.containsKey(id); }

    /** Na ordem do arquivo. */
    public List<ItemDefinition> all() { return List.copyOf(items.values()); }

    public String categoryLabel(String category) { return categories.getOrDefault(category, category); }

    public Map<String, String> categories() { return categories; }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isString() ? o.get(k).getAsString() : null;
    }

    private static String text(JsonObject o, String k, int max, String at) {
        String s = str(o, k);
        if (s == null || s.isBlank()) throw new Invalid(at + ": " + k + " ausente");
        if (s.length() > max) throw new Invalid(at + ": " + k + " longo demais");
        return s.strip();
    }

    private static int integer(JsonObject o, String k, int min, int max, String at) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive() || !o.get(k).getAsJsonPrimitive().isNumber()) throw new Invalid(at + ": " + k + " ausente");
        double v = o.get(k).getAsDouble();
        if (v != Math.rint(v) || v < min || v > max) throw new Invalid(at + ": " + k + " fora de faixa: " + o.get(k));
        return (int) v;
    }
}
