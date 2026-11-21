package vx.game;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.game.quest.QuestDefinition;
import vx.game.quest.QuestSystem;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Rodada 10 — jogo salvo, versão 1 ({@code valumo.save}). Um arquivo, separado das aparências e das preferências:
 * {@code -Dvx.savegame} (pasta; padrão {@code output/saves}; {@code -} = nenhum, nada lido nem gravado). Contrato:
 * {@code docs/architecture/save.md}.
 *
 * <p>Conteúdo: versão, id do save (muda a cada jogo novo), cena, aparência EMBUTIDA (o mesmo contrato do compositor:
 * preset sem mudança volta idêntico), posição e direção, tempo de jogo e a parte das missões ({@link QuestSystem#toJson}:
 * progresso, inimigos de missão derrotados, objetos acesos, recompensas e moedas).
 *
 * <p>Escrita atômica: arquivo temporário na mesma pasta → gravado e sincronizado → fechado → relido e validado → movido
 * por cima numa operação atômica (sem suporte: movimento com substituição). Um jogo novo que salva por cima de um save de
 * outro jogo guarda o anterior em {@code aventura.anterior.json}. Leitura: arquivo ausente = sem save; qualquer problema
 * (JSON, campo, versão, faixa, cena, missão inconsistente) = recusado com o motivo e o original guardado em
 * {@code .invalido}. Aparência incompatível NÃO derruba o progresso: volta o modelo Aventureiro, com aviso. Sem migração
 * falsa: só a versão 1 existe.
 */
public final class SaveGame {
    public static final String FORMAT = "valumo.save";
    /** v1 = o mundo fixo (rodada 10, inalterado); v2 = mundo procedural (rodada 11: semente, gerador, vínculos). */
    public static final int VERSION = 1, VERSION_PROCEDURAL = 2;
    public static final String FILE = "aventura.json", PREVIOUS = "aventura.anterior.json";
    /** Maior save aceito (bytes): um válido tem ~2 KB. */
    public static final long MAX_BYTES = 512 * 1024;
    /** Coordenada máxima aceita (u): o mundo tem 48 u de lado. */
    static final float MAX_COORD = 64f;

    /** Pasta dos saves ({@code -Dvx.savegame}); null = desligado. */
    public static Path dir() {
        String d = System.getProperty("vx.savegame", "output/saves");
        return d.equals("-") ? null : Path.of(d);
    }

    /**
     * Rodada 11 — pasta do save de um mundo procedural: {@code output/saves-procedural/<mundo>/} (nunca a do save
     * principal); com {@code -Dvx.savegame=PASTA}, {@code PASTA/<mundo>/}; {@code -} = desligado.
     */
    public static Path procDir(String worldId) {
        String d = System.getProperty("vx.savegame");
        if ("-".equals(d)) return null;
        return (d != null ? Path.of(d) : Path.of("output/saves-procedural")).resolve(worldId);
    }

    /** Id novo de save (hexadecimal, 16 dígitos). */
    public static String newId(RandomGenerator rnd) { return HexFormat.of().toHexDigits(rnd.nextLong()); }

    /** Save lido. {@code appearanceProblem}: aparência salva incompatível (usada a do modelo); null = veio a salva. */
    public record Data(String id, String scene, Appearance appearance, String appearanceProblem, float x, float z, float yawDeg,
                       float playTime, QuestSystem quests) { }

    /**
     * Resultado da leitura: {@code data} ou {@code problem} (motivo para o jogador); ambos null = não há save. Rodada 12.2:
     * {@code incompatible} = o arquivo está íntegro mas é de OUTRA região ou de um jogo mais novo ({@link Incompatible}):
     * nada é copiado para {@code .invalido} e nada é gravado ({@code preserved} null).
     */
    public record Loaded(Data data, String problem, Path preserved, boolean incompatible) {
        public Loaded(Data data, String problem, Path preserved) { this(data, problem, preserved, false); }

        public boolean exists() { return data != null || problem != null; }
    }

    public static JsonObject compose(String id, String scene, Appearance a, float x, float z, float yawDeg, float playTime, QuestSystem q) {
        return compose(id, scene, a, x, z, yawDeg, playTime, q, null);
    }

    /** Rodada 11: {@code ps} != null = mundo procedural (v2, com o bloco "mundo" e os vínculos resolvidos). */
    public static JsonObject compose(String id, String scene, Appearance a, float x, float z, float yawDeg, float playTime, QuestSystem q,
                                     ProceduralScene ps) {
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — jogo salvo (rodada 10; v2 = mundo procedural, rodada 11). Contrato: docs/architecture/save.md. Editar à mão pode invalidar o arquivo.");
        o.addProperty("formato", FORMAT);
        o.addProperty("versao", ps == null ? VERSION : VERSION_PROCEDURAL);
        o.addProperty("id", id);
        o.addProperty("cena", scene);
        if (ps != null) {
            o.add("mundo", worldJson(ps));
            JsonArray b = new JsonArray();
            if (ps.binding != null) b.add(ps.binding.toJson());
            o.add("vinculos", b);
        }
        o.add("aparencia", JsonParser.parseString(a.toJson()));
        JsonObject p = new JsonObject();
        p.addProperty("x", round(x));
        p.addProperty("z", round(z));
        p.addProperty("direcao", round(yawDeg));
        o.add("jogador", p);
        o.addProperty("tempoDeJogo", round(playTime));
        o.add("progresso", q.toJson());
        return o;
    }

    private static float round(float v) { return Math.round(v * 1000f) / 1000f; }

    public static String text(JsonObject o) { return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n"; }

    /** O que identifica o mundo procedural no save: semente, gerador e versão, tentativa aceita, região. */
    static JsonObject worldJson(ProceduralScene ps) {
        JsonObject m = new JsonObject();
        m.addProperty("tipo", "procedural");
        m.addProperty("semente", Long.toUnsignedString(ps.seed));
        m.addProperty("gerador", vx.game.world.RegionGenerator.ID);
        m.addProperty("versaoGerador", ps.version());                     // rodada 12: a versão DA REGIÃO (a do mundo)
        m.addProperty("tentativa", ps.layout.attempt);
        m.addProperty("regiao", ps.layout.regionId);
        return m;
    }

    // ------------------------------------------------------------------ escrita

    /**
     * Grava de forma atômica. {@code validate} confere o texto RELIDO do temporário antes de substituir o save (se a
     * validação falha, o save antigo fica intacto e o temporário some). Devolve o caminho gravado.
     */
    public static Path write(Path dir, String json, java.util.function.Consumer<String> validate) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE);
        Path tmp = Files.createTempFile(dir, "aventura-", ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer b = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
                while (b.hasRemaining()) ch.write(b);
                ch.force(true);
            }
            validate.accept(Files.readString(tmp, StandardCharsets.UTF_8));
            keepPrevious(dir, file, json);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return file;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Rodada 11.1 — a mesma escrita atômica para qualquer arquivo pequeno do jogo (perfis de personagem, mundos): temporário
     * na mesma pasta → gravado e sincronizado → fechado → relido e validado → movido por cima (atômico, ou com substituição
     * se o sistema de arquivos não suportar). Falhou em qualquer passo: o arquivo antigo fica intacto e o temporário some.
     */
    public static void writeFileAtomic(Path file, String text, java.util.function.Consumer<String> validate) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "valumo-", ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer b = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (b.hasRemaining()) ch.write(b);
                ch.force(true);
            }
            validate.accept(Files.readString(tmp, StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Save de OUTRO jogo (id diferente) vira {@code aventura.anterior.json} antes de ser substituído. */
    private static void keepPrevious(Path dir, Path file, String json) {
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > MAX_BYTES) return;
            JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject now = JsonParser.parseString(json).getAsJsonObject();
            if (old.has("id") && !old.get("id").equals(now.get("id"))) Files.copy(file, dir.resolve(PREVIOUS), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // save antigo ilegível: a leitura já o guardou em .invalido quando foi lido
        }
    }

    // ------------------------------------------------------------------ leitura

    /**
     * Rodada 12 — a versão do gerador gravada num save de mundo procedural, SEM validar nada (só para escolher com que
     * versão gerar a região antes de ler o save de verdade). Ausente, ilegível ou save da vila fixa = -1. Não escreve nada.
     */
    public static int peekGenerator(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return -1;
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement m = o.get("mundo");
            if (m == null || !m.isJsonObject()) return -1;
            JsonElement v = m.getAsJsonObject().get("versaoGerador");
            return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : -1;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Lê o save da pasta (sem save = {@code Loaded(null, null, null)}); inválido = motivo + original em {@code .invalido}. */
    public static Loaded read(Path dir, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects) {
        return read(dir, cat, scene, quests, objects, null);
    }

    public static Loaded read(Path dir, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects,
                              ProceduralScene ps) {
        if (dir == null) return new Loaded(null, null, null);
        Path file = dir.resolve(FILE);
        if (!Files.isRegularFile(file)) return new Loaded(null, null, null);
        try {
            if (Files.size(file) > MAX_BYTES) throw new Invalid("arquivo grande demais (" + Files.size(file) + " bytes)");
            return new Loaded(parse(Files.readString(file, StandardCharsets.UTF_8), cat, scene, quests, objects, ps), null, null);
        } catch (Incompatible e) {                                         // rodada 12.2: íntegro, de outra região: nada é tocado
            System.out.println("save: " + dir + " — " + e.getMessage() + " (" + e.detail + "); o arquivo ficou como estava, sem .invalido");
            return new Loaded(null, e.getMessage(), null, true);
        } catch (IOException | RuntimeException e) {
            String why = e instanceof Invalid || e instanceof QuestSystem.InvalidState ? e.getMessage() : brief(e);
            return new Loaded(null, why, Prefs.preserveInvalid(file));
        }
    }

    /** Motivo curto de um erro do leitor (sem o nome da classe nem o link de ajuda do Gson). */
    static String brief(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int i = m.indexOf("See https");
        if (i > 0) m = m.substring(0, i);
        m = m.replaceFirst("^[a-zA-Z.]*Exception: ", "").strip();
        if (m.endsWith(".")) m = m.substring(0, m.length() - 1);
        return m.length() > 70 ? m.substring(0, 70) + "…" : m;
    }

    /** Save recusado (o motivo vai na mensagem). */
    public static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Invalid(String msg) { super(msg); }
    }

    /**
     * Rodada 12.2 — save ÍNTEGRO que não serve para esta leitura: de um jogo mais novo (versão do formato maior que a
     * conhecida) ou de um mundo gerado cuja região de agora é outra (mundo salvo, vínculos da missão, missão, objeto,
     * descoberta ou inimigo que a região de agora não tem). Não é arquivo corrompido: não vai para {@code .invalido} e nada é
     * gravado. Numa versão do gerador ainda em testes ({@link vx.game.world.RegionGenerator#published}) é "de uma versão de
     * testes": a região daquela semente mudou antes da publicação. A mensagem é para o jogador; {@code detail}, para o log.
     */
    public static final class Incompatible extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final String detail;

        public Incompatible(String msg, String detail) {
            super(msg);
            this.detail = detail;
        }
    }

    /**
     * A recusa de um save íntegro de outra região: "de uma versão de testes" quando a versão do mundo ainda não foi publicada
     * ou teve builds de testes antes da publicação (a v2: os mundos experimentais das rodadas 12 a 12.3); senão "não
     * corresponde a este mundo".
     */
    static Incompatible otherRegion(ProceduralScene ps, String detail) {
        int v = ps.version();
        return vx.game.world.RegionGenerator.published(v) && !vx.game.world.RegionGenerator.hadTestBuilds(v)
                ? new Incompatible("Esta aventura não corresponde a este mundo. Ela continua guardada como estava", detail)
                : new Incompatible("Esta aventura é de uma versão de testes deste mundo, que mudou antes do lançamento. Ela continua guardada como estava; crie outro mundo para jogar", detail);
    }

    public static Data parse(String json, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects) {
        return parse(json, cat, scene, quests, objects, null);
    }

    /**
     * Rodada 11: v1 só no mundo fixo ({@code ps} null); v2 só no procedural, e só se o mundo salvo for ESTE (mesma semente,
     * gerador, versão e tentativa) e os vínculos salvos forem os que a região gera agora — senão recusado: uma missão
     * aceita nunca muda de lugar em silêncio porque o gerador mudou.
     */
    public static Data parse(String json, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects,
                             ProceduralScene ps) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new Invalid("o arquivo não é um objeto JSON");
            o = e.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new Invalid("JSON ilegível (" + brief(e) + ")");
        }
        if (!FORMAT.equals(str(o, "formato"))) throw new Invalid("não é um save do Valumo (formato)");
        JsonElement v = o.get("versao");
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new Invalid("versão ausente");
        int want = ps == null ? VERSION : VERSION_PROCEDURAL;
        if (v.getAsDouble() == Math.rint(v.getAsDouble()) && v.getAsDouble() > VERSION_PROCEDURAL && v.getAsDouble() < 1e6)
            throw new Incompatible("Esta aventura foi salva por uma versão mais nova do jogo. Ela continua guardada como estava", "versão " + v.getAsString() + " do save");
        if (v.getAsDouble() != want) {
            if (v.getAsDouble() == VERSION_PROCEDURAL && ps == null) throw new Invalid("é um save de mundo procedural (v2): abra com -Dvx.mundo=procedural");
            if (v.getAsDouble() == VERSION && ps != null) throw new Invalid("é um save do mundo fixo (v1): abra sem -Dvx.mundo");
            throw new Invalid("versão " + v.getAsString() + " não suportada (este jogo lê a 1 e a 2)");
        }
        if (ps != null) {
            JsonObject m = obj(o, "mundo");
            JsonObject now = worldJson(ps);
            for (String k : now.keySet())                                  // rodada 12.2: íntegro, de outra região (não corrompido)
                if (!now.get(k).equals(m.get(k))) throw otherRegion(ps, "mundo salvo diferente deste (" + k + ": " + m.get(k) + " × " + now.get(k) + ")");
            JsonElement b = o.get("vinculos");
            JsonArray expect = new JsonArray();
            if (ps.binding != null) expect.add(ps.binding.toJson());
            if (b == null || !b.isJsonArray()) throw new Invalid("\"vinculos\" ausente ou não é lista");
            if (!b.equals(expect)) throw otherRegion(ps, "os vínculos da missão salvos não batem com a região gerada agora");
        }
        String id = str(o, "id");
        if (id == null || !id.matches("[0-9a-f]{16}")) throw new Invalid("id do save inválido");
        String sc = str(o, "cena");
        if (sc == null) throw new Invalid("cena ausente");
        if (!sc.equals(scene)) throw new Invalid("cena desconhecida: " + sc);
        JsonObject p = obj(o, "jogador");
        float x = num(p, "x", -MAX_COORD, MAX_COORD), z = num(p, "z", -MAX_COORD, MAX_COORD), yaw = num(p, "direcao", -3600f, 3600f);
        float play = num(o, "tempoDeJogo", 0f, 1e8f);
        QuestSystem q = new QuestSystem(quests);
        try {
            q.fromJson(obj(o, "progresso"), objects);
        } catch (QuestSystem.UnknownReference e) {                         // rodada 12.2: na região gerada, é outra região
            if (ps != null) throw otherRegion(ps, e.getMessage());
            throw e;
        }
        q.clock = play;
        Appearance a;
        String problem = null;
        JsonObject ap = obj(o, "aparencia");
        try {
            a = Appearance.fromJson(ap.toString(), cat);
        } catch (Appearance.InvalidAppearance e) {
            a = Appearance.preset("aventureiro");
            problem = e.getMessage();
        }
        return new Data(id, sc, a, problem, x, z, yaw, play, q);
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonObject()) throw new Invalid("\"" + k + "\" ausente ou não é objeto");
        return e.getAsJsonObject();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static float num(JsonObject o, String k, float min, float max) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new Invalid("\"" + k + "\" ausente ou não é número");
        double v = e.getAsDouble();
        if (!Double.isFinite(v) || v < min || v > max) throw new Invalid("\"" + k + "\" fora de faixa: " + e.getAsString());
        return (float) v;
    }
}
