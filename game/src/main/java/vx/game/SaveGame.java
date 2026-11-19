package vx.game;

import com.google.gson.GsonBuilder;
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
    public static final int VERSION = 1;
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

    /** Id novo de save (hexadecimal, 16 dígitos). */
    public static String newId(RandomGenerator rnd) { return HexFormat.of().toHexDigits(rnd.nextLong()); }

    /** Save lido. {@code appearanceProblem}: aparência salva incompatível (usada a do modelo); null = veio a salva. */
    public record Data(String id, String scene, Appearance appearance, String appearanceProblem, float x, float z, float yawDeg,
                       float playTime, QuestSystem quests) { }

    /** Resultado da leitura: {@code data} ou {@code problem} (motivo para o jogador); ambos null = não há save. */
    public record Loaded(Data data, String problem, Path preserved) {
        public boolean exists() { return data != null || problem != null; }
    }

    public static JsonObject compose(String id, String scene, Appearance a, float x, float z, float yawDeg, float playTime, QuestSystem q) {
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — jogo salvo (rodada 10). Contrato: docs/architecture/save.md. Editar à mão pode invalidar o arquivo.");
        o.addProperty("formato", FORMAT);
        o.addProperty("versao", VERSION);
        o.addProperty("id", id);
        o.addProperty("cena", scene);
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

    /** Lê o save da pasta (sem save = {@code Loaded(null, null, null)}); inválido = motivo + original em {@code .invalido}. */
    public static Loaded read(Path dir, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects) {
        if (dir == null) return new Loaded(null, null, null);
        Path file = dir.resolve(FILE);
        if (!Files.isRegularFile(file)) return new Loaded(null, null, null);
        try {
            if (Files.size(file) > MAX_BYTES) throw new Invalid("arquivo grande demais (" + Files.size(file) + " bytes)");
            return new Loaded(parse(Files.readString(file, StandardCharsets.UTF_8), cat, scene, quests, objects), null, null);
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

    public static Data parse(String json, ComponentCatalog cat, String scene, Collection<QuestDefinition> quests, Set<String> objects) {
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
        if (v.getAsDouble() != VERSION) throw new Invalid("versão " + v.getAsString() + " não suportada (este jogo lê a " + VERSION + ")");
        String id = str(o, "id");
        if (id == null || !id.matches("[0-9a-f]{16}")) throw new Invalid("id do save inválido");
        String sc = str(o, "cena");
        if (sc == null) throw new Invalid("cena ausente");
        if (!sc.equals(scene)) throw new Invalid("cena desconhecida: " + sc);
        JsonObject p = obj(o, "jogador");
        float x = num(p, "x", -MAX_COORD, MAX_COORD), z = num(p, "z", -MAX_COORD, MAX_COORD), yaw = num(p, "direcao", -3600f, 3600f);
        float play = num(o, "tempoDeJogo", 0f, 1e8f);
        QuestSystem q = new QuestSystem(quests);
        q.fromJson(obj(o, "progresso"), objects);
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
