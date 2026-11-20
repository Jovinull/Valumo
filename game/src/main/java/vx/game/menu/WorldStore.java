package vx.game.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.game.SaveGame;
import vx.game.world.RegionGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Rodada 11.1 — mundos do jogador e as aventuras (personagem × mundo). Raiz {@code -Dvx.mundos} (padrão
 * {@code output/mundos/}; {@code -} = desligado):
 * <pre>
 *   mundos/&lt;id&gt;/mundo.json                          o mundo: nome, tipo, semente, versão do gerador, datas, prévia
 *   mundos/&lt;id&gt;/previa.png                          prévia renderizada (cache; refeita se a semente/versão mudar)
 *   mundos/&lt;id&gt;/aventuras/&lt;personagem&gt;/aventura.json  o save (v1 no mundo fixo, v2 no procedural — formatos inalterados)
 *   mundos/vila/aventuras/&lt;personagem&gt;/aventura.json  a vila fixa da rodada 10 (mundo legado, sem semente nem mundo.json)
 *   mundos/importacoes.json                          o que já foi importado dos saves antigos (nunca importa duas vezes)
 * </pre>
 * A identidade do mundo é o ID ({@code m-…}), não a semente: dois mundos com a mesma semente nascem iguais e têm saves
 * separados. Contrato: {@code docs/architecture/catalogo-de-mundos.md}.
 */
public final class WorldStore {
    public static final String FORMAT = "valumo.mundo";
    public static final int VERSION = 1;
    /** Versão da apresentação da prévia (enquadramento, tamanho): mudou → as prévias são refeitas. */
    public static final int PREVIEW_VERSION = 1;
    public static final String LEGACY_ID = "vila";
    public static final long MAX_BYTES = 64 * 1024;
    public static final int MAX_NAME = 32;

    public enum Kind { PROCEDURAL, LEGADO }

    /**
     * Um mundo. {@code seed} só no procedural; {@code generatorVersion}: a versão com que foi criado (outra versão = o jogo
     * avisa e não abre os saves dele em silêncio).
     *
     * <p>{@code summary}: o resumo para o jogador, tirado dos FATOS da região gerada ({@code ProceduralScene.summary}) quando a
     * prévia é feita; null enquanto não houver.
     */
    public record World(String id, String name, Kind kind, long seed, int generatorVersion, long createdAt, long usedAt,
                        String previewKey, String summary) {
        public boolean legacy() { return kind == Kind.LEGADO; }

        public String seedText() { return legacy() ? "—" : Long.toUnsignedString(seed); }

        /** Chave de cache da prévia esperada para este mundo agora. */
        public String expectedPreviewKey() {
            return legacy() ? "legado.v" + PREVIEW_VERSION : seedText() + ".g" + generatorVersion + ".v" + PREVIEW_VERSION;
        }

        public World used(long now) { return new World(id, name, kind, seed, generatorVersion, createdAt, now, previewKey, summary); }

        public World withPreview(String key, String sum) { return new World(id, name, kind, seed, generatorVersion, createdAt, usedAt, key, sum); }

        public World named(String n) { return new World(id, n, kind, seed, generatorVersion, createdAt, usedAt, previewKey, summary); }
    }

    /** Criar mundo falhou (semente malformada, região recusada, disco): a mensagem é para o jogador. */
    public static final class CreateFailed extends Exception {
        private static final long serialVersionUID = 1L;

        public CreateFailed(String msg) { super(msg); }
    }

    public final Path root;
    private final RandomGenerator rnd;

    public WorldStore(Path root, RandomGenerator rnd) {
        this.root = root;
        this.rnd = rnd;
    }

    public static Path defaultRoot() {
        String d = System.getProperty("vx.mundos", "output/mundos");
        return d.equals("-") ? null : Path.of(d);
    }

    /** O mundo fixo da rodada 10 (sempre na lista: a vila e "As Luzes do Posto"). */
    public static World legacy() { return new World(LEGACY_ID, "Vila do Posto", Kind.LEGADO, 0L, 0, 0L, 0L, null, LEGACY_SUMMARY); }

    /** O que a vila é (a mesma cena de sempre: a Kelyra na estalagem, o lago a leste, a trilha do posto ao sul). */
    public static final String LEGACY_SUMMARY = "A vila da Kelyra: a estalagem e seus moradores, um lago a leste e a trilha do posto ao sul.";

    // ------------------------------------------------------------------ semente

    /**
     * Semente digitada: vazia = sorteada AGORA (uma vez, antes de gerar; depois fica gravada); senão um inteiro decimal sem
     * sinal de 64 bits (o contrato da rodada 11). Nome de mundo nunca vira semente.
     */
    public long parseSeed(String text) throws CreateFailed {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return randomSeed();
        if (!t.matches("[0-9]{1,20}")) throw new CreateFailed("A semente é um número inteiro (só algarismos, até 20).");
        try {
            return Long.parseUnsignedLong(t);
        } catch (NumberFormatException e) {
            throw new CreateFailed("Semente grande demais (máximo 18446744073709551615).");
        }
    }

    /** Semente aleatória (só sementes curtas e fáceis de anotar: até 9 algarismos). */
    public long randomSeed() { return 1 + Math.floorMod(rnd.nextLong(), 999_999_999L); }

    // ------------------------------------------------------------------ mundos

    public Path dir(String id) { return root.resolve(id); }

    public Path previewFile(String id) { return dir(id).resolve("previa.png"); }

    /** Pasta do save de um personagem num mundo (o arquivo é {@link SaveGame#FILE}). */
    public Path adventureDir(String worldId, String characterId) { return dir(worldId).resolve("aventuras").resolve(characterId); }

    public boolean hasAdventure(String worldId, String characterId) {
        return root != null && Files.isRegularFile(adventureDir(worldId, characterId).resolve(SaveGame.FILE));
    }

    /** Mundos criados (do usado mais recentemente), e no fim a vila fixa. Ilegíveis vão em {@code broken}. */
    public List<World> list(List<CharacterStore.Broken> broken) {
        List<World> out = new ArrayList<>();
        if (root != null && Files.isDirectory(root)) {
            try (var s = Files.list(root)) {
                for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                    if (d.getFileName().toString().equals(LEGACY_ID)) continue;
                    Path f = d.resolve("mundo.json");
                    if (!Files.exists(f)) continue;
                    try {
                        out.add(read(f));
                    } catch (IOException | RuntimeException e) {
                        if (broken != null) broken.add(new CharacterStore.Broken(f, e.getMessage()));
                    }
                }
            } catch (IOException e) {
                if (broken != null) broken.add(new CharacterStore.Broken(root, e.getMessage()));
            }
        }
        out.sort(Comparator.comparingLong(World::usedAt).reversed().thenComparing(World::createdAt, Comparator.reverseOrder()).thenComparing(World::id));
        out.add(legacy());
        return out;
    }

    public World read(Path f) throws IOException {
        if (Files.size(f) > MAX_BYTES) throw new IllegalArgumentException("arquivo grande demais");
        return parse(Files.readString(f, StandardCharsets.UTF_8), f.getParent().getFileName().toString());
    }

    World parse(String json, String expectedId) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON ilegível");
        }
        if (!FORMAT.equals(CharacterStore.str(o, "formato")) || !o.has("versao") || o.get("versao").getAsInt() != VERSION)
            throw new IllegalArgumentException("não é um mundo do Valumo (" + FORMAT + " v" + VERSION + ")");
        String id = CharacterStore.str(o, "id"), name = cleanName(CharacterStore.str(o, "nome"));
        if (id == null || !id.matches("m-[0-9a-f]{12}") || !id.equals(expectedId)) throw new IllegalArgumentException("id inválido ou diferente da pasta");
        if (name == null) throw new IllegalArgumentException("sem nome");
        if (!"procedural".equals(CharacterStore.str(o, "tipo"))) throw new IllegalArgumentException("tipo desconhecido");
        String seed = CharacterStore.str(o, "semente");
        long s;
        try {
            s = Long.parseUnsignedLong(seed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("semente inválida");
        }
        int gv = o.has("versaoGerador") ? o.get("versaoGerador").getAsInt() : -1;
        if (gv < 1) throw new IllegalArgumentException("versão do gerador inválida");
        return new World(id, name, Kind.PROCEDURAL, s, gv, CharacterStore.num(o, "criadoEm"), CharacterStore.num(o, "usadoEm"),
                CharacterStore.str(o, "previa"), CharacterStore.str(o, "resumo"));
    }

    public World get(String id) {
        if (LEGACY_ID.equals(id)) return legacy();
        try {
            return root == null || id == null || !id.matches("m-[0-9a-f]{12}") ? null : read(dir(id).resolve("mundo.json"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String cleanName(String s) {
        String t = CharacterStore.cleanName(s);
        if (t != null && t.codePointCount(0, t.length()) > MAX_NAME) t = t.substring(0, t.offsetByCodePoints(0, MAX_NAME)).strip();
        return t;
    }

    String newId() {
        for (int i = 0; i < 100; i++) {
            String id = "m-" + HexFormat.of().toHexDigits(rnd.nextLong()).substring(4);
            if (!Files.exists(dir(id))) return id;
        }
        throw new IllegalStateException("não consegui um id livre");
    }

    /**
     * Cria um mundo procedural. {@code check} gera e confere a região da semente ANTES de qualquer arquivo existir
     * (recusada = {@link CreateFailed} com a mensagem dela, nada gravado). Nome vazio = "Mundo &lt;semente&gt;".
     */
    public World create(String name, long seed, long now, SeedCheck check) throws CreateFailed {
        if (root == null) throw new CreateFailed("Os mundos estão desligados nesta sessão.");
        check.check(seed);                                                  // lança CreateFailed: nada é criado
        String n = cleanName(name);
        if (n == null) n = "Mundo " + Long.toUnsignedString(seed);
        World w = new World(newId(), n, Kind.PROCEDURAL, seed, RegionGenerator.VERSION, now, 0L, null, null);
        try {
            save(w);
        } catch (IOException e) {
            throw new CreateFailed("Não consegui gravar o mundo: " + e.getMessage());
        }
        return w;
    }

    /** Confere a região de uma semente (o jogo usa {@code ProceduralScene.create}). */
    @FunctionalInterface
    public interface SeedCheck {
        void check(long seed) throws CreateFailed;
    }

    public void save(World w) throws IOException {
        if (w.legacy()) return;                                             // a vila fixa não tem mundo.json
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — mundo (rodada 11.1). O id é a identidade (a semente pode repetir). Contrato: docs/architecture/catalogo-de-mundos.md.");
        o.addProperty("formato", FORMAT);
        o.addProperty("versao", VERSION);
        o.addProperty("id", w.id());
        o.addProperty("nome", w.name());
        o.addProperty("tipo", "procedural");
        o.addProperty("semente", Long.toUnsignedString(w.seed()));
        o.addProperty("gerador", RegionGenerator.ID);
        o.addProperty("versaoGerador", w.generatorVersion());
        o.addProperty("criadoEm", w.createdAt());
        o.addProperty("usadoEm", w.usedAt());
        if (w.previewKey() != null) o.addProperty("previa", w.previewKey());
        if (w.summary() != null) o.addProperty("resumo", w.summary());
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
        SaveGame.writeFileAtomic(dir(w.id()).resolve("mundo.json"), text, t -> parse(t, w.id()));
    }

    /**
     * Resumo do progresso de uma aventura para a lista de mundos ("12 min de jogo · missão concluída · 30 moedas"), lido do
     * save SEM validar contra a cena (isso é do Continuar). null = sem aventura; arquivo ilegível = "jogo salvo ilegível".
     */
    public static String progress(Path adventureDir) {
        Path f = adventureDir == null ? null : adventureDir.resolve(SaveGame.FILE);
        if (f == null || !Files.isRegularFile(f)) return null;
        try {
            if (Files.size(f) > SaveGame.MAX_BYTES) return "jogo salvo ilegível";
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            float t = o.get("tempoDeJogo").getAsFloat();
            JsonObject pr = o.getAsJsonObject("progresso");
            int done = 0, open = 0;
            for (var e : pr.getAsJsonObject("missoes").entrySet()) {
                JsonObject q = e.getValue().getAsJsonObject();
                if (q.get("concluida").getAsBoolean()) done++;
                else if (q.get("aceita").getAsBoolean()) open++;
            }
            String quest = done > 0 ? (done == 1 ? "missão concluída" : done + " missões concluídas")
                    : open > 0 ? "missão em andamento" : "nenhuma missão aceita";
            String time = t < 60 ? "menos de 1 min" : (int) (t / 60) + " min";
            return time + " de jogo · " + quest + " · " + pr.get("moedas").getAsInt() + " moedas";
        } catch (IOException | RuntimeException e) {
            return "jogo salvo ilegível";
        }
    }

    // ------------------------------------------------------------------ saves antigos (rodadas 10 e 11)

    /** Um save antigo ainda não importado: o arquivo, o tipo e (v2) a semente. */
    public record OldSave(Path file, Kind kind, long seed) { }

    /**
     * Saves antigos que ainda não foram trazidos para o catálogo: o da vila fixa ({@code output/saves/aventura.json}, v1) e
     * os por semente da rodada 11 ({@code output/saves-procedural/s<semente>/aventura.json}, v2). Nada é lido além do
     * caminho; quem importa valida.
     */
    public List<OldSave> oldSaves(Path legacySaveDir, Path procSaveRoot) {
        List<OldSave> out = new ArrayList<>();
        List<String> done = importedHashes();
        if (done == null) return out;                                      // registro ilegível: nada é oferecido
        if (legacySaveDir != null && Files.isRegularFile(legacySaveDir.resolve(SaveGame.FILE)) && !done.contains(hash(legacySaveDir.resolve(SaveGame.FILE))))
            out.add(new OldSave(legacySaveDir.resolve(SaveGame.FILE), Kind.LEGADO, 0L));
        if (procSaveRoot != null && Files.isDirectory(procSaveRoot)) {
            try (var s = Files.list(procSaveRoot)) {
                for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                    String n = d.getFileName().toString();
                    Path f = d.resolve(SaveGame.FILE);
                    if (!n.matches("s[0-9]{1,20}") || !Files.isRegularFile(f) || done.contains(hash(f))) continue;
                    try {
                        out.add(new OldSave(f, Kind.PROCEDURAL, Long.parseUnsignedLong(n.substring(1))));
                    } catch (NumberFormatException ignored) {
                        // pasta com número fora de faixa: não é de semente
                    }
                }
            } catch (IOException ignored) {
                // pasta ilegível: nada a importar
            }
        }
        return out;
    }

    /**
     * Traz um save antigo para (mundo, personagem): COPIA (o original fica) depois de {@code validate} aceitar a cópia, só se
     * o personagem ainda não tem aventura nesse mundo (progresso novo nunca é substituído por um antigo) e se o mesmo
     * arquivo (pelo conteúdo) nunca foi importado. Registra em {@code importacoes.json}.
     */
    public void importSave(OldSave old, String worldId, String characterId, long now, SaveValidator validate) throws CreateFailed {
        String h = hash(old.file());
        List<String> done = importedHashes();
        if (done == null) throw new CreateFailed("O registro de importações (mundos/importacoes.json) está ilegível; nada foi trazido.");
        if (done.contains(h)) throw new CreateFailed("Esta aventura antiga já foi trazida.");
        Path target = adventureDir(worldId, characterId);
        if (Files.exists(target.resolve(SaveGame.FILE))) throw new CreateFailed("Este personagem já tem uma aventura neste mundo.");
        Path tmp = null;
        try {
            Files.createDirectories(target);
            tmp = Files.createTempFile(target, "importa-", ".tmp");
            Files.copy(old.file(), tmp, StandardCopyOption.REPLACE_EXISTING);
            String why = validate.problem(tmp);
            if (why != null) throw new CreateFailed("A aventura antiga não pôde ser trazida: " + why);
            Files.move(tmp, target.resolve(SaveGame.FILE), StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
            recordImport(old, h, worldId, characterId, now);
        } catch (IOException e) {
            throw new CreateFailed("Não consegui copiar a aventura antiga: " + e.getMessage());
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }

    /** Confere um save copiado (null = válido; senão o motivo). */
    @FunctionalInterface
    public interface SaveValidator {
        String problem(Path copy);
    }

    Path importsFile() { return root.resolve("importacoes.json"); }

    List<String> importedHashes() {
        List<String> out = new ArrayList<>();
        if (root == null || !Files.isRegularFile(importsFile())) return out;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(importsFile())).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("importacoes")) out.add(e.getAsJsonObject().get("sha256").getAsString());
        } catch (IOException | RuntimeException e) {
            return null;                                                    // ilegível: ninguém importa (evita duplicar recompensa)
        }
        return out;
    }

    /**
     * Um save antigo que não vale (ilegível, de outra cena, semente sem região): registrado como recusado, para não ser
     * tentado de novo a cada abertura. O arquivo continua onde estava.
     */
    public void recordRejected(OldSave old, String why, long now) throws IOException {
        if (root == null || importedHashes() == null) return;
        recordImport(old, hash(old.file()), null, null, now, why);
    }

    private void recordImport(OldSave old, String h, String worldId, String characterId, long now) throws IOException {
        recordImport(old, h, worldId, characterId, now, null);
    }

    private void recordImport(OldSave old, String h, String worldId, String characterId, long now, String rejected) throws IOException {
        JsonObject o;
        try {
            o = Files.isRegularFile(importsFile()) ? JsonParser.parseString(Files.readString(importsFile())).getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        if (!o.has("importacoes")) o.add("importacoes", new JsonArray());
        o.addProperty("_doc", "Valumo — jogos salvos de antes já adotados pelo catálogo (ou recusados, com o motivo). Os originais não foram alterados.");
        JsonObject r = new JsonObject();
        r.addProperty("origem", old.file().toString());
        r.addProperty("sha256", h);
        if (worldId != null) r.addProperty("mundo", worldId);
        if (characterId != null) r.addProperty("personagem", characterId);
        if (rejected != null) r.addProperty("recusado", rejected);
        r.addProperty("em", now);
        o.getAsJsonArray("importacoes").add(r);
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
        SaveGame.writeFileAtomic(importsFile(), text, t -> JsonParser.parseString(t).getAsJsonObject().getAsJsonArray("importacoes"));
    }

    static String hash(Path f) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
        } catch (Exception e) {
            return "?";
        }
    }
}
