package vx.game.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rodada 13 — o que a AVENTURA (personagem × mundo) tem de itens: a mochila e as fontes já coletadas (o presente da Índira,
 * o achado de cada descoberta). Vai no save junto com o resto do progresso, no mesmo arquivo atômico.
 * <p>
 * <b>Entregar</b> ({@link #grant}) é uma operação só: a fonte é marcada coletada <i>se e somente se</i> tudo entrou na
 * mochila. Fonte já coletada não entrega de novo (apertar E várias vezes, recomeçar o confronto, carregar o save); mochila
 * sem espaço não entrega nada e a fonte continua disponível.
 */
public final class ItemProgress {
    public static final String FIELD_BAG = "inventario", FIELD_SOURCES = "fontesColetadas";
    public static final int MAX_SOURCES = 2000;
    static final Pattern SOURCE = Pattern.compile("[a-z]+:[A-Za-z0-9_.:-]{1,120}");

    public final Inventory inventory;
    private final TreeSet<String> collected = new TreeSet<>();

    public ItemProgress() { this(new Inventory()); }

    private ItemProgress(Inventory inventory) { this.inventory = inventory; }

    public enum Outcome { GRANTED, ALREADY, FULL }

    /** Dado de item inválido no save (estrutura, número, fonte repetida): o motivo vai na mensagem. */
    public static class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Invalid(String msg) { super(msg); }
    }

    /** Id de item que este jogo não conhece: o arquivo está íntegro, é de um jogo com mais itens. */
    public static final class UnknownItem extends Invalid {
        private static final long serialVersionUID = 1L;

        public UnknownItem(String msg) { super(msg); }
    }

    /** Fonte que a cena de agora não tem (um achado de outra região): o leitor de save decide como recusar. */
    public static final class UnknownSource extends Invalid {
        private static final long serialVersionUID = 1L;

        public UnknownSource(String msg) { super(msg); }
    }

    public boolean collected(String source) { return collected.contains(source); }

    public Set<String> collectedSources() { return Collections.unmodifiableSet(collected); }

    /** Entrega {@code items} pela fonte {@code source}, uma vez, tudo ou nada. */
    public Outcome grant(String source, List<Inventory.Grant> items) {
        if (!SOURCE.matcher(source).matches()) throw new IllegalArgumentException("fonte inválida: " + source);
        if (collected.contains(source)) return Outcome.ALREADY;
        if (!inventory.add(items)) return Outcome.FULL;
        collected.add(source);
        return Outcome.GRANTED;
    }

    /** Nada para gravar (save de antes da rodada 13 continua byte a byte igual). */
    public boolean isEmpty() { return inventory.isEmpty() && collected.isEmpty(); }

    /** Acrescenta os campos OPCIONAIS ao bloco {@code progresso}; vazios não aparecem. */
    public void writeTo(JsonObject progresso) {
        if (!inventory.isEmpty()) progresso.add(FIELD_BAG, inventory.toJson());
        if (!collected.isEmpty()) {
            JsonArray s = new JsonArray();
            collected.forEach(s::add);
            progresso.add(FIELD_SOURCES, s);
        }
    }

    /**
     * Lê os campos opcionais do bloco {@code progresso} (ausentes = mochila vazia, nada coletado). {@code knownSource} diz
     * se uma fonte existe na cena de agora.
     */
    public static ItemProgress read(JsonObject progresso, ItemCatalog cat, Predicate<String> knownSource) {
        Inventory inv = new Inventory();
        if (progresso.has(FIELD_BAG)) {
            if (!progresso.get(FIELD_BAG).isJsonArray()) throw new Invalid("\"" + FIELD_BAG + "\" não é lista");
            inv = Inventory.fromJson(progresso.getAsJsonArray(FIELD_BAG), cat);
        }
        ItemProgress p = new ItemProgress(inv);
        if (progresso.has(FIELD_SOURCES)) {
            if (!progresso.get(FIELD_SOURCES).isJsonArray()) throw new Invalid("\"" + FIELD_SOURCES + "\" não é lista");
            JsonArray a = progresso.getAsJsonArray(FIELD_SOURCES);
            if (a.size() > MAX_SOURCES) throw new Invalid("fontes coletadas demais");
            for (JsonElement e : a) {
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new Invalid("fonte coletada que não é texto");
                String s = e.getAsString();
                if (!SOURCE.matcher(s).matches()) throw new Invalid("fonte coletada inválida: " + s);
                if (!p.collected.add(s)) throw new Invalid("fonte coletada repetida: " + s);
                if (!knownSource.test(s)) throw new UnknownSource("fonte coletada desconhecida: " + s);
            }
        }
        return p;
    }

    static int integer(JsonObject o, String k, int min, int max) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive() || !o.get(k).getAsJsonPrimitive().isNumber()) throw new Invalid(k + " ausente");
        double v = o.get(k).getAsDouble();
        if (v != Math.rint(v) || v < min || v > max) throw new Invalid(k + " fora de faixa: " + o.get(k));
        return (int) v;
    }

    static String string(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive() || !o.get(k).getAsJsonPrimitive().isString()) throw new Invalid(k + " ausente");
        return o.get(k).getAsString();
    }
}
