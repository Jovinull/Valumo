package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.audio.AudioBackend;
import vx.engine.audio.Wav;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 09.1 — efeitos sonoros do jogo sobre um {@link AudioBackend} (OpenAL, mudo ou o registrador dos testes).
 *
 * <ul>
 *   <li>Carrega TUDO na criação (assets/audio/sintese/ gerados + mistura assets/scenes/sons_e.json): tocar não lê disco, não
 *       decodifica e não aloca buffer de som — o primeiro golpe não engasga.</li>
 *   <li>Regras por efeito: vozes simultâneas, intervalo mínimo, um pedido igual no mesmo passo vira um só.</li>
 *   <li>Variação (qual arquivo, desvio de tom) só na apresentação e determinística: vem de um hash de (semente, efeito,
 *       passo lógico). A mesma luta soa igual; a lógica nunca lê nada daqui.</li>
 *   <li>Volumes: principal × efeitos, mudo. Pausa: congela o que toca e não aceita pedidos.</li>
 * </ul>
 * Cada reprodução fica registrada em {@link #played} (testes, relatório de sincronização, vídeos).
 */
public final class Sfx implements AutoCloseable {
    /** Uma reprodução: passo lógico, efeito, arquivo, ganho, tom, espacial e onde; nanoTime do pedido ao backend. */
    public record Played(long tick, String cue, String file, float gain, float pitch, boolean spatial, float x, float y, float z,
                         int voice, long nanos) { }

    record Mix(float gain, boolean spatial, float pitchJitter, int voices, float interval) { }

    public final AudioBackend backend;
    private final Map<String, Mix> mix = new HashMap<>();
    private final Map<String, int[]> sounds = new HashMap<>();
    private final Map<String, String[]> files = new HashMap<>();
    private final Map<String, Float> peak = new HashMap<>(), length = new HashMap<>();
    private final Map<String, List<int[]>> live = new HashMap<>();        // efeito → {voz, passo de início, amostras}
    private final Map<String, Long> lastTick = new HashMap<>();
    public final List<Played> played = new ArrayList<>();
    public float master, effects;
    public boolean muted, paused;
    /** Semente da variação (presentação). Fixa por padrão: vídeos e testes reproduzíveis. */
    public long seed = 0x5EEDL;

    public Sfx(AudioBackend backend, Path assets) {
        this.backend = backend;
        JsonObject m = read(assets.resolve("scenes/sons_e.json"));
        JsonObject v = read(assets.resolve("audio/sintese/variantes.json")).getAsJsonObject("efeitos");
        master = m.get("volumePrincipal").getAsFloat();
        effects = m.get("volumeEfeitos").getAsFloat();
        JsonObject fx = m.getAsJsonObject("efeitos");
        for (String cue : fx.keySet()) {
            JsonObject e = fx.getAsJsonObject(cue);
            mix.put(cue, new Mix(e.get("ganho").getAsFloat(), e.get("espacial").getAsBoolean(), e.get("tom").getAsFloat(),
                    e.get("vozes").getAsInt(), e.get("intervalo").getAsFloat()));
            if (!v.has(cue)) throw new IllegalStateException("efeito sem som gerado: " + cue + " (tools/audio_e.py)");
            var arr = v.getAsJsonArray(cue);
            int[] ids = new int[arr.size()];
            String[] names = new String[arr.size()];
            float pk = 0f, len = 0f;
            for (int i = 0; i < ids.length; i++) {
                names[i] = arr.get(i).getAsString();
                try {
                    Wav w = Wav.read(assets.resolve("audio/sintese").resolve(names[i]));
                    if (w.channels != 1) throw new IllegalStateException("efeito precisa ser mono (espacial): " + names[i]);
                    ids[i] = backend.load(names[i], w.samples, w.rate);
                    pk += peakTime(w.samples, w.rate) / ids.length;
                    len = Math.max(len, (float) w.samples.length / w.rate);
                } catch (IOException ex) {
                    throw new UncheckedIOException("som ausente: " + names[i] + " (rode tools/build_assets.py)", ex);
                }
            }
            sounds.put(cue, ids);
            files.put(cue, names);
            peak.put(cue, pk);
            length.put(cue, len);
            live.put(cue, new ArrayList<>());
        }
        applyGain();
        warmUp();
    }

    /**
     * Rodada 09.1 — aquecimento em silêncio (saída a 0): cada som é tocado e parado, e o caminho inteiro de um pedido
     * roda {@link #WARM} vezes, até o JIT compilá-lo. Sem isso, com poucos pedidos espaçados, cada som custava ~0,3 ms
     * na thread do jogo (código ainda interpretado) e o primeiro passo, 12 ms; em regime custa ~3 µs. Depois, o estado
     * volta a zero (nada fica tocando nem registrado).
     */
    static final int WARM = 3000;

    private void warmUp() {
        backend.masterGain(0f);
        for (int[] ids : sounds.values())
            for (int id : ids) backend.stop(backend.play(id, 0f, 1f, true, 0f, 0f, 0f));
        String any = mix.keySet().iterator().next();
        for (int i = 0; i < WARM; i++) cueNow(any, 1_000_000L + i * 1000L, 0f, 0f, 0f);
        stopAll();
        played.clear();
        applyGain();
    }

    private static JsonObject read(Path p) {
        try {
            return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(p + " ausente", e);
        }
    }

    /** Instante (s) do trecho de 10 ms mais forte: onde o "fuu" da lâmina é mais alto. */
    static float peakTime(short[] s, int rate) {
        int win = rate / 100;
        double best = -1;
        int at = 0;
        for (int a = 0; a + win <= s.length; a += win / 2) {
            double e = 0;
            for (int i = a; i < a + win; i++) e += (double) s[i] * s[i];
            if (e > best) { best = e; at = a; }
        }
        return (at + win / 2f) / rate;
    }

    /**
     * Escolhe a saída: "mudo", "loopback" ou o dispositivo padrão. QUALQUER falha (sem dispositivo, biblioteca nativa
     * ausente, contexto recusado) vira {@link vx.engine.audio.SilentBackend} com o motivo — o jogo nunca cai por som.
     */
    public static AudioBackend backendFor(String mode, java.util.function.Supplier<AudioBackend> device,
                                          java.util.function.Supplier<AudioBackend> loopback) {
        try {
            return switch (mode) {
                case "mudo" -> new vx.engine.audio.SilentBackend("pedido (-Dvx.som=mudo)");
                case "loopback" -> loopback.get();
                default -> device.get();
            };
        } catch (Throwable t) {
            return new vx.engine.audio.SilentBackend(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public boolean has(String cue) { return mix.containsKey(cue); }

    public java.util.Set<String> cues() { return mix.keySet(); }

    /** Instante do pico do efeito (média das variações), em s. */
    public float peakTime(String cue) { return peak.getOrDefault(cue, 0f); }

    public float length(String cue) { return length.getOrDefault(cue, 0f); }

    /**
     * Pede um efeito no passo {@code tick} (posição usada se for espacial). Devolve a voz, ou -1 se não tocou (pausa,
     * limite de vozes, intervalo, efeito repetido no mesmo passo, backend sem voz livre).
     */
    public int cue(String name, long tick, float x, float y, float z) {
        long t0 = System.nanoTime();
        try { return cueNow(name, tick, x, y, z); } finally {
            long d = System.nanoTime() - t0;
            cueNanos += d; cueCount++; cueMax = Math.max(cueMax, d);
        }
    }

    /** Medição: tempo gasto pedindo sons na thread do jogo (escolha + chamada ao backend). */
    public long cueNanos, cueCount, cueMax;

    private int cueNow(String name, long tick, float x, float y, float z) {
        Mix m = mix.get(name);
        if (m == null) throw new IllegalArgumentException("efeito desconhecido: " + name);
        if (paused) return -1;
        Long last = lastTick.get(name);
        if (last != null && (tick == last || (tick - last) * GameLogic.TICK < m.interval() - 1e-6f)) return -1;
        List<int[]> lv = live.get(name);
        lv.removeIf(v -> (tick - v[1]) * GameLogic.TICK >= length(name));   // terminaram (pelo relógio do jogo)
        if (lv.size() >= m.voices()) return -1;
        long h = mixHash(seed, name.hashCode(), tick);
        int[] ids = sounds.get(name);
        int k = (int) Math.floorMod(h, (long) ids.length);
        float pitch = 1f + m.pitchJitter() * (((h >>> 20) & 0xffff) / 32767.5f - 1f);
        int voice = backend.play(ids[k], m.gain(), pitch, m.spatial(), x, y, z);
        lastTick.put(name, tick);
        lv.add(new int[]{voice, (int) tick});
        played.add(new Played(tick, name, files.get(name)[k], m.gain(), pitch, m.spatial(), x, y, z, voice, System.nanoTime()));
        if (played.size() > 4096) played.subList(0, 2048).clear();
        return voice;
    }

    static long mixHash(long seed, int a, long b) {
        long x = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return x;
    }

    public void stop(int voice) { backend.stop(voice); }

    /** Para tudo e esquece quem está tocando (recomeço do confronto, volta à vila). */
    public void stopAll() {
        backend.stopAll();
        for (List<int[]> l : live.values()) l.clear();
        lastTick.clear();
    }

    public void setPaused(boolean p) {
        if (p == paused) return;
        paused = p;
        backend.pauseAll(p);
    }

    public void setVolumes(float master, float effects, boolean muted) {
        this.master = Math.clamp(master, 0f, 1f);
        this.effects = Math.clamp(effects, 0f, 1f);
        this.muted = muted;
        applyGain();
    }

    /** Ganho que chega ao dispositivo (0 no mudo). */
    public float outputGain() { return muted ? 0f : master * effects; }

    private void applyGain() { backend.masterGain(outputGain()); }

    @Override public void close() { backend.close(); }
}
