package vx.game.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.game.SaveGame;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Rodada 11.1 — personagens do jogador: identidade estável separada da aparência. Um perfil por arquivo em
 * {@code -Dvx.personagens} (padrão {@code output/personagens/}; {@code -} = desligado), nome de arquivo = o ID (nunca o
 * nome visível). Contrato {@code valumo.personagem} v1: {@code docs/architecture/perfis-de-personagem.md}.
 *
 * <p>Os três modelos (aventureiro, exploradora, maga) são PONTOS DE PARTIDA da criação, não classes: o jogador pode ter
 * quantos personagens quiser, de qualquer modelo. As aparências soltas do criador ({@code output/aparencias/}) continuam
 * acessíveis pelo "Carregar" do próprio criador ao criar ou editar um personagem.
 */
public final class CharacterStore {
    public static final String FORMAT = "valumo.personagem";
    public static final int VERSION = 1;
    public static final long MAX_BYTES = 256 * 1024;
    public static final int MAX_NAME = 32;

    /** Perfil: ID estável, nome visível, aparência, modelo de origem, datas (ms), importação de origem (ou null). */
    public record Profile(String id, String name, Appearance appearance, String origin, long createdAt, long usedAt) {
        public Profile withAppearance(Appearance a, String newName) { return new Profile(id, newName, a.copy(), origin, createdAt, usedAt); }

        public Profile used(long now) { return new Profile(id, name, appearance, origin, createdAt, now); }
    }

    /** Perfil ilegível: o arquivo e o motivo (aparece na lista, sem poder ser escolhido). */
    public record Broken(Path file, String reason) { }

    public final Path dir;
    private final ComponentCatalog cat;
    private final RandomGenerator rnd;

    public CharacterStore(Path dir, ComponentCatalog cat, RandomGenerator rnd) {
        this.dir = dir;
        this.cat = cat;
        this.rnd = rnd;
    }

    public static Path defaultDir() {
        String d = System.getProperty("vx.personagens", "output/personagens");
        return d.equals("-") ? null : Path.of(d);
    }

    /** Nome limpo: sem controle nem quebra de linha, espaços colapsados, até {@link #MAX_NAME} caracteres; vazio = null. */
    public static String cleanName(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        s.codePoints().filter(c -> !Character.isISOControl(c)).forEach(b::appendCodePoint);
        String t = b.toString().strip().replaceAll("\\s+", " ");
        if (t.codePointCount(0, t.length()) > MAX_NAME) t = t.substring(0, t.offsetByCodePoints(0, MAX_NAME)).strip();
        return t.isEmpty() ? null : t;
    }

    String newId() {
        for (int i = 0; i < 100; i++) {
            String id = "p-" + HexFormat.of().toHexDigits(rnd.nextLong()).substring(4);
            if (dir == null || !Files.exists(file(id))) return id;
        }
        throw new IllegalStateException("não consegui um id livre");
    }

    public Path file(String id) { return dir.resolve(id + ".json"); }

    // ------------------------------------------------------------------ leitura

    /** Perfis válidos, do usado mais recentemente ao mais antigo (empate: nome, id). Os ilegíveis vão em {@code broken}. */
    public List<Profile> list(List<Broken> broken) {
        List<Profile> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            for (Path f : s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    out.add(read(f));
                } catch (RuntimeException | IOException e) {
                    if (broken != null) broken.add(new Broken(f, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
        } catch (IOException e) {
            if (broken != null) broken.add(new Broken(dir, e.getMessage()));
        }
        out.sort(Comparator.comparingLong(Profile::usedAt).reversed().thenComparing(Profile::name).thenComparing(Profile::id));
        return out;
    }

    public Profile read(Path f) throws IOException {
        if (Files.size(f) > MAX_BYTES) throw new IllegalArgumentException("arquivo grande demais");
        return parse(Files.readString(f, StandardCharsets.UTF_8), f.getFileName().toString().replaceFirst("\\.json$", ""));
    }

    /** Lê o texto de um perfil; {@code expectedId}: o id que o arquivo precisa ter (o nome dele). */
    Profile parse(String json, String expectedId) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON ilegível");
        }
        if (!FORMAT.equals(str(o, "formato")) || !o.has("versao") || o.get("versao").getAsInt() != VERSION)
            throw new IllegalArgumentException("não é um personagem do Valumo (" + FORMAT + " v" + VERSION + ")");
        String id = str(o, "id"), name = cleanName(str(o, "nome"));
        if (id == null || !id.matches("p-[0-9a-f]{12}")) throw new IllegalArgumentException("id inválido");
        if (!id.equals(expectedId)) throw new IllegalArgumentException("o nome do arquivo não é o id");
        if (name == null) throw new IllegalArgumentException("sem nome");
        JsonElement ap = o.get("aparencia");
        if (ap == null || !ap.isJsonObject()) throw new IllegalArgumentException("sem aparência");
        Appearance a;
        try {
            a = Appearance.fromJson(ap.toString(), cat);
        } catch (Appearance.InvalidAppearance e) {
            throw new IllegalArgumentException("aparência incompatível: " + e.getMessage());
        }
        a.name = name;
        return new Profile(id, name, a, str(o, "origem") != null ? str(o, "origem") : a.preset, num(o, "criadoEm"), num(o, "usadoEm"));
    }

    public Profile get(String id) {
        try {
            return dir == null || id == null || !id.matches("p-[0-9a-f]{12}") || !Files.exists(file(id)) ? null : read(file(id));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ escrita

    /** Novo perfil (novo ID; o nome pode repetir o de outro). */
    public Profile create(String name, Appearance a, long now) throws IOException {
        String n = cleanName(name);
        if (n == null) n = "Sem nome";
        Profile p = new Profile(newId(), n, withName(a, n), a.preset, now, now);
        save(p);
        return p;
    }

    /** Atualiza um perfil EXISTENTE pelo ID (aparência e nome); nunca cria outro nem toca em outro arquivo. */
    public Profile update(Profile p, Appearance a, String name) throws IOException {
        if (p == null || !Files.exists(file(p.id()))) throw new IOException("personagem inexistente");
        String n = cleanName(name);
        Profile q = p.withAppearance(withName(a, n == null ? p.name() : n), n == null ? p.name() : n);
        save(q);
        return q;
    }

    public Profile touch(Profile p, long now) throws IOException {
        Profile q = p.used(now);
        save(q);
        return q;
    }

    static Appearance withName(Appearance a, String n) {
        Appearance c = a.copy();
        c.name = n;
        return c;
    }

    void save(Profile p) throws IOException {
        if (dir == null) throw new IOException("personagens desligados nesta sessão");
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — personagem (rodada 11.1). O id é a identidade; o nome é só exibição. Contrato: docs/architecture/perfis-de-personagem.md.");
        o.addProperty("formato", FORMAT);
        o.addProperty("versao", VERSION);
        o.addProperty("id", p.id());
        o.addProperty("nome", p.name());
        o.addProperty("origem", p.origin());
        o.addProperty("criadoEm", p.createdAt());
        o.addProperty("usadoEm", p.usedAt());
        o.add("aparencia", JsonParser.parseString(p.appearance().toJson()));
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
        SaveGame.writeFileAtomic(file(p.id()), text, t -> {
            if (!parse(t, p.id()).id().equals(p.id())) throw new IllegalStateException("id mudou ao reler");
        });
    }

    // ------------------------------------------------------------------ utilitários

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? Math.max(0L, e.getAsLong()) : 0L;
    }

    public static long now() { return Instant.now().toEpochMilli(); }
}
