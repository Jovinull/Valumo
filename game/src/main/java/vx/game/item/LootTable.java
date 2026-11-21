package vx.game.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 13 — de onde vêm os itens ({@code assets/scenes/recompensas_e.json}, escrito à mão). Uma camada de jogo POR CIMA
 * dos mundos: só reage a eventos estáveis que já existem (terminar a conversa com um morador; descobrir um lugar de um
 * tipo) e nunca muda a geração, as descobertas nem as falas congeladas da v2.
 * <ul>
 *   <li><b>presentes</b> — por morador (o id do rig, {@code indira}): a fonte é {@code presente:<morador>}, uma vez por
 *   aventura;</li>
 *   <li><b>achados</b> — por TIPO de descoberta ({@code casa}, {@code abobada}…): a fonte é {@code achado:<id da
 *   descoberta>}, uma vez por descoberta.</li>
 * </ul>
 * Sem sorteio nesta versão (a recompensa é fixa por fonte). O que já foi entregue está no save, então mudar esta tabela
 * (nova {@code versao}) não muda o passado de ninguém.
 */
public final class LootTable {
    public static final String FORMAT = "valumo.recompensas";
    public static final int VERSION = 1;
    public static final String GIFT = "presente", FIND = "achado";

    /** O que uma fonte entrega e o aviso curto da tela. */
    public record Reward(List<Inventory.Grant> items, String notice) { }

    private final Map<String, Reward> gifts, finds;

    public static final LootTable EMPTY = new LootTable(Map.of(), Map.of());

    private LootTable(Map<String, Reward> gifts, Map<String, Reward> finds) {
        this.gifts = Collections.unmodifiableMap(gifts);
        this.finds = Collections.unmodifiableMap(finds);
    }

    public static LootTable load(Path assets, ItemCatalog cat) {
        try {
            return parse(Files.readString(assets.resolve("scenes/recompensas_e.json")), cat);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LootTable parse(String json, ItemCatalog cat) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new ItemCatalog.Invalid("recompensas: não é objeto JSON");
            o = e.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ItemCatalog.Invalid("recompensas: JSON ilegível");
        }
        try {
            if (!o.has("formato") || !o.get("formato").isJsonPrimitive() || !FORMAT.equals(o.get("formato").getAsString()))
                throw new ItemCatalog.Invalid("recompensas: formato não é " + FORMAT);
            if (!o.has("versao") || !o.get("versao").isJsonPrimitive() || o.get("versao").getAsDouble() != VERSION)
                throw new ItemCatalog.Invalid("recompensas: versão desconhecida");
            return new LootTable(section(o, "presentes", cat), section(o, "achados", cat));
        } catch (ItemCatalog.Invalid e) {
            throw e;
        } catch (RuntimeException e) {                                     // tipo errado num campo (texto no lugar de número…)
            throw new ItemCatalog.Invalid("recompensas: dado de tipo errado (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static Map<String, Reward> section(JsonObject o, String k, ItemCatalog cat) {
        Map<String, Reward> out = new LinkedHashMap<>();
        if (!o.has(k)) return out;
        if (!o.get(k).isJsonObject()) throw new ItemCatalog.Invalid("recompensas: " + k + " não é objeto");
        for (var e : o.getAsJsonObject(k).entrySet()) {
            String key = e.getKey();
            if (!key.matches("[a-z_]{1,32}")) throw new ItemCatalog.Invalid("recompensas: chave inválida em " + k + ": " + key);
            if (!e.getValue().isJsonObject()) throw new ItemCatalog.Invalid("recompensas: " + k + "." + key + " não é objeto");
            JsonObject r = e.getValue().getAsJsonObject();
            if (!r.has("itens") || !r.get("itens").isJsonArray()) throw new ItemCatalog.Invalid("recompensas: " + key + " sem itens");
            List<Inventory.Grant> items = new ArrayList<>();
            for (JsonElement g : r.getAsJsonArray("itens")) {
                if (!g.isJsonArray() || g.getAsJsonArray().size() != 2) throw new ItemCatalog.Invalid("recompensas: " + key + ": item deve ser [id, quantidade]");
                JsonArray a = g.getAsJsonArray();
                String id = a.get(0).getAsString();
                ItemDefinition d = cat.get(id);
                if (d == null) throw new ItemCatalog.Invalid("recompensas: " + key + ": item desconhecido: " + id);
                double n = a.get(1).getAsDouble();
                if (n != Math.rint(n) || n < 1 || n > d.stack()) throw new ItemCatalog.Invalid("recompensas: " + key + ": quantidade inválida de " + id);
                items.add(new Inventory.Grant(d, (int) n));
            }
            if (items.isEmpty()) throw new ItemCatalog.Invalid("recompensas: " + key + " sem itens");
            String notice = r.has("aviso") ? r.get("aviso").getAsString().strip() : "";
            if (notice.isEmpty() || notice.length() > 60) throw new ItemCatalog.Invalid("recompensas: " + key + ": aviso ausente ou longo");
            out.put(key, new Reward(List.copyOf(items), notice));
        }
        return out;
    }

    /** O presente do morador {@code npc} (o id do rig), ou null. */
    public Reward gift(String npc) { return gifts.get(npc); }

    /** O achado das descobertas do tipo {@code type}, ou null. */
    public Reward find(String type) { return finds.get(type); }

    public Map<String, Reward> gifts() { return gifts; }

    public Map<String, Reward> finds() { return finds; }

    public static String giftSource(String npc) { return GIFT + ":" + npc; }

    public static String findSource(String discoveryId) { return FIND + ":" + discoveryId; }
}
