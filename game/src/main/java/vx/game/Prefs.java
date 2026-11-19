package vx.game;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rodada 09.2 — preferências do jogador (separadas da aparência do personagem): barras de vida de inimigos comuns,
 * perfil de sensação e encontro da arena. Arquivo {@code -Dvx.prefs} (padrão {@code output/preferencias.json}; os roteiros
 * não gravam). Arquivo ausente ou ilegível = padrões, sem erro.
 */
public final class Prefs {
    /** Barras de inimigos comuns: "nunca", "dano" (quando leva dano ou está travado) ou "sempre" (engajado e visível). */
    public String bars;
    public String feel, encounter;
    /** Rodada 10 — rastreador da missão visível (T no diário). */
    public boolean tracker = true;
    public final Path file;
    public boolean persist = true;
    /**
     * Sensação ou encontro vindos de {@code -Dvx.sensacao}/{@code -Dvx.encontro} (comparação de uma sessão): valem no
     * jogo, mas {@link #save()} grava o valor que estava no arquivo — antes a comparação virava preferência definitiva no
     * primeiro ajuste da pausa. Escolher na pausa limpa a marca (aí a escolha é do jogador).
     */
    public boolean feelFromCommandLine, encounterFromCommandLine;
    private String storedFeel, storedEncounter;

    public Prefs(Path file, String barsDefault, String feelDefault, String encounterDefault) {
        this.file = file;
        bars = barsDefault;
        feel = storedFeel = feelDefault;
        encounter = storedEncounter = encounterDefault;
    }

    /** Maior arquivo de preferências aceito (bytes). */
    static final long MAX_BYTES = 64 * 1024;

    /** Arquivo das preferências; {@code -Dvx.prefs=-} = nenhum (roteiros: sempre os padrões, nada gravado). */
    public static Path defaultFile() {
        String f = System.getProperty("vx.prefs", "output/preferencias.json");
        return f.equals("-") ? null : Path.of(f);
    }

    /**
     * Lê o arquivo. Ilegível (JSON quebrado, tipo errado, grande demais) = os padrões, e o original é guardado ao lado
     * ({@link #preserveInvalid}) antes que o próximo {@link #save()} o substitua — o diagnóstico não se perde.
     */
    public Prefs load() {
        if (file == null || !Files.isRegularFile(file)) return this;
        String b = bars, f = feel, e = encounter;
        boolean t = tracker;
        try {
            if (Files.size(file) > MAX_BYTES) throw new IOException("grande demais");
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (o.has("barrasInimigos")) b = valid(text(o, "barrasInimigos"), b);
            if (o.has("sensacao")) f = text(o, "sensacao");
            if (o.has("encontro")) e = text(o, "encontro");
            if (o.has("rastreador")) {
                var v = o.get("rastreador");
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("\"rastreador\" não é verdadeiro/falso");
                t = v.getAsBoolean();
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("preferências ilegíveis (usando os padrões; original em " + preserveInvalid(file) + "): " + ex.getMessage());
            return this;
        }
        bars = b;
        feel = storedFeel = f;
        encounter = storedEncounter = e;
        tracker = t;
        return this;
    }

    /** Valor de texto obrigatório (número, objeto ou lista = arquivo inválido, não conversão silenciosa). */
    static String text(JsonObject o, String k) {
        var v = o.get(k);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("\"" + k + "\" não é texto");
        return v.getAsString();
    }

    /** Copia um arquivo de preferências inválido para {@code ARQUIVO.invalido} (o último); devolve o destino. */
    static Path preserveInvalid(Path f) {
        Path keep = f.resolveSibling(f.getFileName() + ".invalido");
        try {
            Files.copy(f, keep, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return keep;
        } catch (IOException ex) {
            return f;
        }
    }

    static String valid(String v, String def) { return v.equals("nunca") || v.equals("dano") || v.equals("sempre") ? v : def; }

    public void save() {
        if (!persist || file == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — preferências do jogador (rodada 09.2). barrasInimigos: nunca | dano | sempre.");
        o.addProperty("barrasInimigos", bars);
        if (!feelFromCommandLine) storedFeel = feel;
        if (!encounterFromCommandLine) storedEncounter = encounter;
        o.addProperty("sensacao", storedFeel);
        o.addProperty("encontro", storedEncounter);
        o.addProperty("rastreador", tracker);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n");
        } catch (IOException e) {
            System.err.println("não consegui gravar as preferências em " + file + ": " + e.getMessage());
        }
    }

    /** Próximo modo de barra (nunca → dano → sempre → nunca). */
    public void cycleBars() {
        bars = switch (bars) { case "nunca" -> "dano"; case "dano" -> "sempre"; default -> "nunca"; };
        save();
    }
}
