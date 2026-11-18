package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.audio.Audio;
import vx.engine.audio.SoundSystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Rodada 09.4 — preferências de volume do jogador (música e efeitos), independentes. Multiplicam o equilíbrio do
 * catálogo (assets/audio/sons.json), nunca o substituem: "efeitos" vale para os barramentos de efeitos, de
 * interface e de ambiente (e para os efeitos do combate). Gravadas em {@code -Dvx.prefs.audio} (padrão
 * {@code output/preferencias/audio.json}, contrato {@code valumo.preferencias} v1); arquivo ausente ou inválido → padrão
 * (100%), nunca exceção. {@code -Dvx.prefs=-} (roteiros e testes) = nem lê nem grava, como as outras preferências.
 */
public final class AudioPrefs {
    /** null = não lê nem grava ({@code -Dvx.prefs=-}). */
    public static final Path FILE = "-".equals(System.getProperty("vx.prefs")) ? null
            : Path.of(System.getProperty("vx.prefs.audio", "output/preferencias/audio.json"));
    public static final float STEP = 0.1f;
    public float music = 1f, effects = 1f;

    public static AudioPrefs load(Path f) {
        AudioPrefs p = new AudioPrefs();
        if (f == null) return p;
        try {
            if (Files.isRegularFile(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if ("valumo.preferencias".equals(o.get("contrato").getAsString()) && o.get("versao").getAsInt() == 1) {
                    p.music = clamp(o.get("musica").getAsFloat());
                    p.effects = clamp(o.get("efeitos").getAsFloat());
                }
            }
        } catch (Exception e) {
            System.err.println("preferências de áudio ignoradas (" + e.getMessage() + ")");
        }
        return p;
    }

    public void save(Path f) {
        if (f == null) return;
        try {
            if (f.getParent() != null) Files.createDirectories(f.getParent());
            Files.writeString(f, String.format(Locale.ROOT,
                    "{\"contrato\": \"valumo.preferencias\", \"versao\": 1, \"musica\": %.2f, \"efeitos\": %.2f}%n", music, effects));
        } catch (Exception e) {
            System.err.println("não consegui gravar as preferências de áudio: " + e.getMessage());
        }
    }

    /** Ajusta um controle ("musica"/"efeitos") em ±1 passo; devolve se mudou. */
    public boolean nudge(String which, int dir) {
        float before = which.equals("musica") ? music : effects;
        float v = clamp(Math.round((before + dir * STEP) * 10) / 10f);
        if (which.equals("musica")) music = v; else effects = v;
        return v != before;
    }

    public void apply() {
        SoundSystem s = Audio.get();
        if (s == null) return;
        s.userVolume("musica", music);
        s.userVolume("efeitos", effects);
        s.userVolume("interface", effects);
        s.userVolume("ambiente", effects);
    }

    public static String percent(float v) { return Math.round(v * 100) + "%"; }

    static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
