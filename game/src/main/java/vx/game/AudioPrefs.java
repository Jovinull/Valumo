package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.audio.Audio;
import vx.engine.audio.SoundSystem;

import java.io.IOException;
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
 * {@code -Dvx.prefs=ARQUIVO} sem {@code -Dvx.prefs.audio}: o áudio vai para um arquivo IRMÃO do indicado
 * ({@code ARQUIVO} + {@code .audio.json}) — quem redireciona as preferências (pacotes de revisão) nunca toca nas do jogador.
 */
public final class AudioPrefs {
    /** null = não lê nem grava ({@code -Dvx.prefs=-}). */
    public static final Path FILE = fileFor(System.getProperty("vx.prefs"), System.getProperty("vx.prefs.audio"));

    /** Arquivo das preferências de áudio para os valores de {@code -Dvx.prefs} e {@code -Dvx.prefs.audio} (null = nenhum). */
    static Path fileFor(String prefs, String audio) {
        if ("-".equals(prefs)) return null;
        if (audio != null) return Path.of(audio);
        if (prefs != null) {                                             // preferências redirecionadas: o áudio acompanha
            Path p = Path.of(prefs);
            return p.resolveSibling(p.getFileName() + ".audio.json");
        }
        return Path.of("output/preferencias/audio.json");
    }
    public static final float STEP = 0.1f;
    public float music = 1f, effects = 1f;

    public static AudioPrefs load(Path f) {
        AudioPrefs p = new AudioPrefs();
        if (f == null) return p;
        try {
            if (Files.isRegularFile(f)) {
                if (Files.size(f) > Prefs.MAX_BYTES) throw new IOException("grande demais");
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if ("valumo.preferencias".equals(o.get("contrato").getAsString()) && o.get("versao").getAsInt() == 1) {
                    float m = o.get("musica").getAsFloat(), e = o.get("efeitos").getAsFloat();
                    if (!Float.isFinite(m) || !Float.isFinite(e)) throw new IllegalArgumentException("volume não finito");
                    p.music = clamp(m);
                    p.effects = clamp(e);
                }
            }
        } catch (Exception e) {
            // os padrões (100%), e o original guardado ao lado antes que a próxima gravação o substitua
            System.err.println("preferências de áudio ignoradas (" + e.getMessage() + "); original em " + Prefs.preserveInvalid(f));
            return new AudioPrefs();
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
