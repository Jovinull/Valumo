package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.Mesh;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Carrega e mantém em cache os assets de {@code assets/} (rigs, props). É DONO das malhas e texturas que carregou:
 * {@link #close()} as libera (quem cria um {@code Assets} temporário — o criador aberto de dentro do jogo — precisa
 * fechá-lo, senão cada visita deixa uma cena inteira de malhas na GPU).
 */
public final class Assets implements AutoCloseable {
    /** Modelo estático: malha com origem no ponto de apoio (x,z centrados, y=0 no chão). */
    public record Prop(Mesh mesh, float voxelSize, float height, float width, vx.engine.Texture texture) { }

    public static final String[] STYLE_KEYS = {"a", "b", "c", "d", "e"};
    /** Ordem de apresentação dos personagens; os que existirem no estilo aparecem nesta ordem. */
    private static final List<String> CHARACTER_ORDER = List.of("adventurer", "ranger", "mage");

    private final Path root;
    private final Map<String, CharacterModel> rigs = new HashMap<>();
    private final Map<String, PropSet> props = new HashMap<>();

    public Assets(Path root) { this.root = root; }

    /** Libera as malhas (rigs e props) e as texturas (atlas) carregadas por este cache. Thread do GL. */
    @Override
    public void close() {
        java.util.Set<vx.engine.Texture> textures = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (CharacterModel m : rigs.values()) {
            Composer.release(m);
            if (m.atlas != null) textures.add(m.atlas);
        }
        rigs.clear();
        for (PropSet set : props.values())
            for (Prop p : set.loaded()) {                              // só o que foi carregado (o resto nunca existiu)
                p.mesh().close();
                if (p.texture() != null) textures.add(p.texture());   // o atlas é compartilhado pelos props do estilo
            }
        props.clear();
        for (vx.engine.Texture t : textures) t.close();
    }

    public Path root() { return root; }

    public CharacterModel rig(String relativeDir) {
        return rigs.computeIfAbsent(relativeDir, d -> CharacterModel.load(root.resolve(d)));
    }

    public boolean exists(String relative) { return Files.exists(root.resolve(relative)); }

    /** Nomes dos personagens jogáveis disponíveis no estilo, na ordem de apresentação. */
    public List<String> characters(String styleKey) {
        List<String> found = subdirs("characters/" + styleKey);
        List<String> out = new ArrayList<>();
        for (String c : CHARACTER_ORDER) if (found.contains(c)) out.add(c);
        for (String c : found) if (!out.contains(c)) out.add(c);
        return out;
    }

    public List<String> subdirs(String relative) {
        Path dir = root.resolve(relative);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> Files.exists(p.resolve("rig.json")))
                    .map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Props do diretório {@code environment/<estilo>}: o CATÁLOGO inteiro do {@code props.json}, carregado sob demanda.
     * Integração dos lotes de conteúdo: o catálogo ficou grande (a biblioteca do laboratório soma dezenas de modelos que
     * nenhuma cena jogável usa ainda) e antes cada prop do catálogo era lido, malhado e enviado à GPU na primeira chamada. Agora cada
     * prop vira malha só quando uma cena o pede ({@link PropSet#get}); {@code containsKey}/{@code keySet} não carregam
     * nada. Thread do GL (a malha sobe para a GPU no {@code get}).
     */
    public Map<String, Prop> props(String styleKey) {
        return props.computeIfAbsent(styleKey, k -> new PropSet(root.resolve("environment/" + k)));
    }

    /** Props vivos (malha na GPU) de um estilo — QA: a biblioteca registrada não pode virar memória gasta. */
    public int loadedProps(String styleKey) {
        PropSet set = props.get(styleKey);
        return set == null ? 0 : set.loaded().size();
    }

    /**
     * O catálogo de um estilo: as entradas do {@code props.json} (registradas) e as malhas já pedidas (carregadas). Um
     * {@code Map} comum para quem lê por nome; iterar {@code entrySet}/{@code values} carregaria tudo, e ninguém faz isso
     * (o {@code close} usa {@link #loaded()}).
     */
    static final class PropSet extends java.util.AbstractMap<String, Prop> {
        private final Path dir;
        private final Map<String, JsonObject> entries = new java.util.LinkedHashMap<>();
        private final Map<String, Prop> loaded = new HashMap<>();
        private vx.engine.Texture atlas;
        private boolean atlasTried;

        PropSet(Path dir) {
            this.dir = dir;
            Path manifest = dir.resolve("props.json");
            if (!Files.exists(manifest)) return;
            try {
                JsonObject doc = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                for (String name : doc.keySet()) entries.put(name, doc.getAsJsonObject(name));
            } catch (IOException ex) {
                throw new IllegalStateException("falha ao ler " + manifest, ex);
            }
        }

        @Override
        public Prop get(Object key) {
            if (!(key instanceof String name)) return null;
            Prop p = loaded.get(name);
            if (p != null) return p;
            JsonObject e = entries.get(name);
            if (e == null) return null;
            p = build(e);
            loaded.put(name, p);
            return p;
        }

        @Override public boolean containsKey(Object key) { return entries.containsKey(key); }

        @Override public int size() { return entries.size(); }

        @Override public java.util.Set<String> keySet() { return java.util.Collections.unmodifiableSet(entries.keySet()); }

        @Override
        public java.util.Set<Entry<String, Prop>> entrySet() {
            java.util.Set<Entry<String, Prop>> out = new java.util.LinkedHashSet<>();
            for (String name : entries.keySet()) out.add(new java.util.AbstractMap.SimpleImmutableEntry<>(name, get(name)));
            return out;
        }

        java.util.Collection<Prop> loaded() { return loaded.values(); }

        private Prop build(JsonObject e) {
            try {
                float vs = e.get("voxelSize").getAsFloat();
                JsonArray sz = e.getAsJsonArray("sizeVoxels");
                float h = sz.get(1).getAsFloat() * vs, w = sz.get(0).getAsFloat() * vs;
                if (e.has("cuboids")) {
                    // Props de cuboides texturizados (direção C) compartilham environment/<estilo>/atlas.png.
                    if (!atlasTried) {
                        atlasTried = true;
                        if (Files.exists(dir.resolve("atlas.png"))) atlas = vx.engine.Texture.load(dir.resolve("atlas.png"), true);
                    }
                    if (atlas == null) throw new IOException("atlas.png ausente em " + dir);
                    return new Prop(CuboidMesh.build(e.getAsJsonArray("cuboids"), vs, atlas.width, atlas.height), vs, h, w, atlas);
                }
                JsonArray o = e.getAsJsonArray("origin");
                boolean affine = e.has("affineWind") && e.get("affineWind").getAsBoolean();
                Mesh mesh = VoxelMesher.build(VoxModel.load(dir.resolve(e.get("file").getAsString())), vs,
                        o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(), affine);
                return new Prop(mesh, vs, h, w, null);
            } catch (IOException ex) {
                throw new IllegalStateException("falha ao ler o prop em " + dir, ex);
            }
        }
    }
}
