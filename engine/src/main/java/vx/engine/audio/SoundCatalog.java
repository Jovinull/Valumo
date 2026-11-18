package vx.engine.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 09.4 — catálogo de sons ({@code assets/audio/sons.json}, contrato {@code valumo.sons} v1, escrito à mão).
 * O jogo pede EVENTOS semânticos ("passo.grama.andar", "ui.confirmar"); arquivos, ganhos, variação, limites e
 * barramento ficam aqui. Nenhum caminho de arquivo no código de jogo.
 */
public final class SoundCatalog {
    public static final String CONTRACT = "valumo.sons";
    public static final int VERSION = 1;

    /** Evento sonoro: arquivos (variações) e regras de reprodução. */
    public record Event(String id, String bus, List<String> files, float gainDb, float pitchSemitones,
                        float gainJitterDb, float minInterval, int maxVoices, int priority, float minDur, float maxDur) { }

    /** Música: OGG com laço em quadros (laço = 2ª passada do corpo; ver tools/audio/render_musica.py). */
    public record Music(String id, String file, long loopStart, long loopEnd, float gainDb, String note) { }

    public final Map<String, Float> buses = new LinkedHashMap<>();
    public final Map<String, Event> events = new LinkedHashMap<>();
    public final Map<String, Music> music = new LinkedHashMap<>();
    public final Map<String, Ambient> ambient = new LinkedHashMap<>();

    /** Ambiente em laço (OGG). Posicional = mono e com distâncias (o volume cai de ref até max, em unidades). */
    public record Ambient(String id, String file, String bus, float gainDb, boolean positional, float ref, float max) { }

    public static SoundCatalog load(Path file) throws IOException {
        JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!CONTRACT.equals(str(o, "contrato", "")) || o.get("versao").getAsInt() != VERSION)
            throw new IOException(file + ": contrato " + str(o, "contrato", "?") + " v" + o.get("versao") + " (esperado " + CONTRACT + " v" + VERSION + ")");
        SoundCatalog c = new SoundCatalog();
        for (var e : o.getAsJsonObject("barramentos").entrySet()) c.buses.put(e.getKey(), e.getValue().getAsFloat());
        for (var e : o.getAsJsonObject("eventos").entrySet()) {
            JsonObject v = e.getValue().getAsJsonObject();
            String bus = str(v, "barramento", "efeitos");
            if (!c.buses.containsKey(bus)) throw new IOException("evento " + e.getKey() + ": barramento desconhecido " + bus);
            List<String> files = new ArrayList<>();
            for (JsonElement f : v.getAsJsonArray("arquivos")) files.add(f.getAsString());
            if (files.isEmpty()) throw new IOException("evento " + e.getKey() + " sem arquivos");
            JsonArray dur = v.has("duracao") ? v.getAsJsonArray("duracao") : null;
            c.events.put(e.getKey(), new Event(e.getKey(), bus, List.copyOf(files), num(v, "ganho_db", 0),
                    num(v, "variacao_tom", 0), num(v, "variacao_ganho_db", 0), num(v, "intervalo_min", 0.03f),
                    (int) num(v, "max_vozes", 2), (int) num(v, "prioridade", 1),
                    dur == null ? 0 : dur.get(0).getAsFloat(), dur == null ? 99 : dur.get(1).getAsFloat()));
        }
        if (o.has("musicas"))
            for (var e : o.getAsJsonObject("musicas").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                c.music.put(e.getKey(), new Music(e.getKey(), v.get("arquivo").getAsString(), v.get("laco_inicio").getAsLong(),
                        v.get("laco_fim").getAsLong(), num(v, "ganho_db", 0), str(v, "nota", "")));
            }
        if (o.has("ambientes"))
            for (var e : o.getAsJsonObject("ambientes").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                String bus = str(v, "barramento", "ambiente");
                if (!c.buses.containsKey(bus)) throw new IOException("ambiente " + e.getKey() + ": barramento desconhecido " + bus);
                c.ambient.put(e.getKey(), new Ambient(e.getKey(), v.get("arquivo").getAsString(), bus, num(v, "ganho_db", 0),
                        v.has("posicional") && v.get("posicional").getAsBoolean(), num(v, "distancia_ref", 3), num(v, "distancia_max", 20)));
            }
        return c;
    }

    private static float num(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
}
