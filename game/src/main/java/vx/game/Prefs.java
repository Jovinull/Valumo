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
    public final Path file;
    public boolean persist = true;

    public Prefs(Path file, String barsDefault, String feelDefault, String encounterDefault) {
        this.file = file;
        bars = barsDefault;
        feel = feelDefault;
        encounter = encounterDefault;
    }

    /** Arquivo das preferências; {@code -Dvx.prefs=-} = nenhum (roteiros: sempre os padrões, nada gravado). */
    public static Path defaultFile() {
        String f = System.getProperty("vx.prefs", "output/preferencias.json");
        return f.equals("-") ? null : Path.of(f);
    }

    public Prefs load() {
        if (file == null || !Files.isRegularFile(file)) return this;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (o.has("barrasInimigos")) bars = valid(o.get("barrasInimigos").getAsString(), bars);
            if (o.has("sensacao")) feel = o.get("sensacao").getAsString();
            if (o.has("encontro")) encounter = o.get("encontro").getAsString();
        } catch (IOException | RuntimeException e) {
            System.err.println("preferências ilegíveis (usando os padrões): " + file);
        }
        return this;
    }

    static String valid(String v, String def) { return v.equals("nunca") || v.equals("dano") || v.equals("sempre") ? v : def; }

    public void save() {
        if (!persist || file == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("_doc", "Valumo — preferências do jogador (rodada 09.2). barrasInimigos: nunca | dano | sempre.");
        o.addProperty("barrasInimigos", bars);
        o.addProperty("sensacao", feel);
        o.addProperty("encontro", encounter);
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
