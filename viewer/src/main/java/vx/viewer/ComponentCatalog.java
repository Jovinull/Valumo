package vx.viewer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 07 — catálogo de componentes de personalização ({@code assets/components/catalog.json}, gerado por
 * {@code tools/components_e.py}). Só leitura; IDs são estáveis e nunca reaproveitados.
 */
public final class ComponentCatalog {
    public static final String CONTRACT = "valumo.componentes/1";

    public static final class Component {
        public String id, slot;
        public int version;
        public JsonObject doc;
    }

    public static final class Preset {
        public String id, body, face, hair, hairColor, skin, iris, cover;
        public JsonObject bodyMasks;
    }

    public final Path root;                       // assets/components
    public final Map<String, Component> components = new LinkedHashMap<>();
    public final Map<String, Preset> presets = new LinkedHashMap<>();
    public final Map<String, List<String[]>> colors = new LinkedHashMap<>();   // canal → [id, #hex]
    public final Map<String, Integer> channels = new LinkedHashMap<>();      // nome → rgb da máscara
    public final int[] headPivot = new int[3];

    public static ComponentCatalog load(Path assetsRoot) {
        try {
            Path root = assetsRoot.resolve("components");
            JsonObject d = JsonParser.parseString(Files.readString(root.resolve("catalog.json"))).getAsJsonObject();
            if (!CONTRACT.equals(d.get("contrato").getAsString()))
                throw new IllegalStateException("catálogo com contrato " + d.get("contrato") + " (esperado " + CONTRACT + ")");
            ComponentCatalog c = new ComponentCatalog(root);
            var hp = d.getAsJsonArray("pivoCabeca");
            for (int i = 0; i < 3; i++) c.headPivot[i] = Math.round(hp.get(i).getAsFloat());
            for (var e : d.getAsJsonObject("canais").entrySet())
                c.channels.put(e.getKey(), Integer.parseInt(e.getValue().getAsString().substring(1), 16));
            for (var e : d.getAsJsonObject("componentes").entrySet()) {
                Component k = new Component();
                k.id = e.getKey();
                k.doc = e.getValue().getAsJsonObject();
                k.slot = k.doc.get("slot").getAsString();
                k.version = k.doc.get("versao").getAsInt();
                c.components.put(k.id, k);
            }
            for (var e : d.getAsJsonObject("presets").entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                Preset s = new Preset();
                s.id = e.getKey();
                s.body = p.get("corpo").getAsString();
                s.face = p.get("rosto").getAsString();
                s.hair = p.get("cabelo").getAsString();
                s.hairColor = p.get("corCabelo").getAsString();
                s.skin = p.get("pele").getAsString();
                s.iris = p.get("iris").getAsString();
                s.cover = p.get("cabeca").isJsonNull() ? null : p.get("cabeca").getAsString();
                s.bodyMasks = p.getAsJsonObject("mascarasDoCorpo");
                c.presets.put(s.id, s);
            }
            for (var e : d.getAsJsonObject("cores").entrySet()) {
                List<String[]> l = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray())
                    l.add(new String[]{el.getAsJsonObject().get("id").getAsString(), el.getAsJsonObject().get("hex").getAsString()});
                c.colors.put(e.getKey(), l);
            }
            return c;
        } catch (IOException e) {
            throw new IllegalStateException("catálogo de componentes ausente — rode python3 tools/build_assets.py", e);
        }
    }

    private ComponentCatalog(Path root) { this.root = root; }

    /** IDs de um slot, na ordem do catálogo. */
    public List<String> ids(String slot) {
        List<String> out = new ArrayList<>();
        for (Component c : components.values()) if (c.slot.equals(slot)) out.add(c.id);
        return out;
    }

    // ------------------------------------------------------------------ resolução (null = valor do preset)

    public String face(Appearance a) { return a.face != null ? a.face : presets.get(a.preset).face; }

    /** Botas (rodada 09.4): a escolhida ou "botas.originais" (as do corpo do preset). */
    public String feet(Appearance a) { return a.feet != null ? a.feet : "botas.originais"; }

    /** Formato dos olhos (rodada 09.4): o escolhido ou o nativo do rosto. */
    public String eyes(Appearance a) {
        if (a.eyes != null) return a.eyes;
        JsonObject f = components.get(face(a)).doc;
        return f.has("olhos") ? f.get("olhos").getAsString() : null;
    }
    public String hair(Appearance a) { return a.hair != null ? a.hair : presets.get(a.preset).hair; }
    public String hairColor(Appearance a) { return a.hairColor != null ? a.hairColor : presets.get(a.preset).hairColor; }
    public String skin(Appearance a) { return a.skin != null ? a.skin : presets.get(a.preset).skin; }
    public String iris(Appearance a) { return a.iris != null ? a.iris : presets.get(a.preset).iris; }

    /** Cobertura efetiva (null = sem cobertura). */
    public String cover(Appearance a) {
        if (a.cover == null) return presets.get(a.preset).cover;
        return Appearance.NO_COVER.equals(a.cover) ? null : a.cover;
    }

    /**
     * Variante do cabelo exigida pela cobertura: null (sem cobertura), "nativo" (desenhados juntos: nada muda),
     * "sob_aba" (o chapéu esconde o cabelo das colunas cobertas, da aba para cima) ou "faixa" (capuz C3: faixa
     * pintada sobre o cabelo). "X" no catálogo = incompatível → {@link Appearance.InvalidAppearance}.
     */
    public String coverVariant(Appearance a) {
        String cov = cover(a);
        if (cov == null) return null;
        JsonObject rules = components.get(cov).doc.getAsJsonObject("cabelo");
        String style = hair(a).substring("cabelo.".length());
        String v = rules.has(style) ? rules.get(style).getAsString() : rules.get("*").getAsString();
        if ("X".equals(v)) throw new Appearance.InvalidAppearance(hair(a) + " é incompatível com " + cov);
        return v;
    }
}
