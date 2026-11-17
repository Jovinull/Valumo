package vx.viewer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rodada 07 — aparência de um personagem jogável (contrato {@value #CONTRACT}, versão {@value #VERSION}).
 * Guarda só IDs e parâmetros, nunca geometria: o {@link Composer} reconstrói o modelo a partir do catálogo de
 * componentes. Campo {@code null} = "o do preset". Aparência é independente de classe/combate (não há esses
 * campos aqui de propósito).
 */
public final class Appearance {
    public static final String CONTRACT = "valumo.aparencia";
    public static final int VERSION = 1;
    static final Pattern HEX = Pattern.compile("#[0-9a-f]{6}");
    /** Valor de {@link #cover} que tira a cobertura do preset (null = a do preset). */
    public static final String NO_COVER = "nenhum";

    public String preset;            // aventureiro | exploradora | maga
    public String face;              // rosto.<preset> ou null
    public String hair;              // cabelo.<estilo> ou null
    public String hairColor;         // #rrggbb ou null
    public String skin;              // #rrggbb ou null
    public String iris;              // #rrggbb ou null
    public String cover;             // chapeu.maga | capuz.c3 | "nenhum" | null
    public String name;              // identidade (opcional, não afeta a geometria)

    public static Appearance preset(String preset) {
        Appearance a = new Appearance();
        a.preset = preset;
        return a;
    }

    public Appearance copy() {
        Appearance a = new Appearance();
        a.preset = preset; a.face = face; a.hair = hair; a.hairColor = hairColor; a.skin = skin; a.iris = iris;
        a.cover = cover; a.name = name;
        return a;
    }

    /** Volta ao preset (mantém só o preset e o nome). */
    public void reset() {
        face = hair = hairColor = skin = iris = cover = null;
    }

    /** Assinatura estável da GEOMETRIA/CORES (cache do compositor): nome não entra. */
    public String signature() {
        return String.join("|", preset, s(face), s(hair), s(hairColor), s(skin), s(iris), s(cover));
    }

    private static String s(String v) { return v == null ? "-" : v; }

    @Override public boolean equals(Object o) {
        return o instanceof Appearance a && signature().equals(a.signature()) && Objects.equals(name, a.name);
    }

    @Override public int hashCode() { return signature().hashCode(); }

    // ------------------------------------------------------------------ persistência

    public String toJson() {
        JsonObject d = new JsonObject();
        d.addProperty("contrato", CONTRACT);
        d.addProperty("versao", VERSION);
        if (name != null) {
            JsonObject id = new JsonObject();
            id.addProperty("nome", name);
            d.add("identidade", id);
        }
        JsonObject o = new JsonObject();
        o.addProperty("preset", preset);
        d.add("origem", o);
        JsonObject ap = new JsonObject();
        if (face != null) ap.addProperty("rosto", face);
        if (hair != null || hairColor != null) {
            JsonObject h = new JsonObject();
            if (hair != null) h.addProperty("estilo", hair);
            if (hairColor != null) h.addProperty("cor", hairColor);
            ap.add("cabelo", h);
        }
        if (skin != null) ap.addProperty("pele", skin);
        if (iris != null) ap.addProperty("iris", iris);
        d.add("aparencia", ap);
        JsonObject eq = new JsonObject();
        if (cover != null) eq.addProperty("cabeca", cover);
        d.add("equipamento_visual", eq);
        return new GsonBuilder().setPrettyPrinting().create().toJson(d);
    }

    /** Lê e VALIDA contra o catálogo; qualquer problema vira {@link InvalidAppearance} com mensagem clara. */
    public static Appearance fromJson(String json, ComponentCatalog cat) {
        JsonObject d;
        try {
            d = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new InvalidAppearance("JSON inválido: " + e.getMessage());
        }
        String contract = str(d, "contrato");
        if (!CONTRACT.equals(contract)) throw new InvalidAppearance("contrato desconhecido: " + contract);
        int v = d.has("versao") ? d.get("versao").getAsInt() : -1;
        if (v != VERSION) throw new InvalidAppearance("versão " + v + " não suportada (esta aplicação lê a " + VERSION + ")");
        Appearance a = new Appearance();
        JsonObject o = obj(d, "origem");
        a.preset = o == null ? null : str(o, "preset");
        JsonObject id = obj(d, "identidade");
        a.name = id == null ? null : str(id, "nome");
        JsonObject ap = obj(d, "aparencia");
        if (ap != null) {
            a.face = str(ap, "rosto");
            JsonObject h = obj(ap, "cabelo");
            if (h != null) {
                a.hair = str(h, "estilo");
                a.hairColor = lower(str(h, "cor"));
            }
            a.skin = lower(str(ap, "pele"));
            a.iris = lower(str(ap, "iris"));
        }
        JsonObject eq = obj(d, "equipamento_visual");
        a.cover = eq == null ? null : str(eq, "cabeca");
        a.validate(cat);
        return a;
    }

    /** IDs existentes, slots corretos, cores #rrggbb e compatibilidade (variante resolvível). */
    public void validate(ComponentCatalog cat) {
        if (preset == null || !cat.presets.containsKey(preset)) throw new InvalidAppearance("preset desconhecido: " + preset);
        checkId(cat, face, "rosto");
        checkId(cat, hair, "cabelo");
        if (cover != null && !NO_COVER.equals(cover)) checkId(cat, cover, "cabeca");
        for (String[] c : new String[][]{{hairColor, "cor do cabelo"}, {skin, "pele"}, {iris, "íris"}})
            if (c[0] != null && !HEX.matcher(c[0]).matches()) throw new InvalidAppearance(c[1] + " inválida: " + c[0] + " (esperado #rrggbb)");
        cat.coverVariant(this);                          // lança se a combinação for incompatível
    }

    private static void checkId(ComponentCatalog cat, String id, String slot) {
        if (id == null) return;
        ComponentCatalog.Component c = cat.components.get(id);
        if (c == null) throw new InvalidAppearance("componente desconhecido: " + id);
        if (!c.slot.equals(slot)) throw new InvalidAppearance(id + " não é do slot " + slot + " (é " + c.slot + ")");
    }

    /** Grava sem sobrescrever: se o arquivo existir e overwrite for falso, falha com mensagem. */
    public void save(Path file, boolean overwrite) throws IOException {
        if (Files.exists(file) && !overwrite) throw new IOException("já existe (não sobrescrevo): " + file);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
    }

    public static Appearance load(Path file, ComponentCatalog cat) throws IOException {
        return fromJson(Files.readString(file), cat);
    }

    private static JsonObject obj(JsonObject d, String k) {
        JsonElement e = d.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String str(JsonObject d, String k) {
        JsonElement e = d.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String lower(String v) { return v == null ? null : v.toLowerCase(Locale.ROOT); }

    /** Aparência inválida ou incompatível (o viewer mostra a mensagem; nada é ignorado em silêncio). */
    public static final class InvalidAppearance extends RuntimeException {
        public InvalidAppearance(String msg) { super(msg); }
    }
}
