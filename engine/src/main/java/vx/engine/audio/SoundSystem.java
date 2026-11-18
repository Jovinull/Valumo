package vx.engine.audio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Rodada 09 — regras de reprodução, sem conhecer buffers nem dispositivo (testável com uma saída falsa).
 * <ul>
 *   <li><b>Eventos</b>: variação escolhida sem repetir a anterior (gerador com semente: o mesmo roteiro toca a
 *       mesma sequência), tom e ganho variando dentro da faixa do catálogo;</li>
 *   <li><b>intervalo mínimo</b> por evento (um gatilho duplicado no mesmo quadro não toca duas vezes);</li>
 *   <li><b>limite de vozes</b> por evento (rouba a mais antiga do mesmo evento) e global (rouba a mais antiga de
 *       prioridade menor ou igual; se só houver vozes mais importantes, o som novo é descartado);</li>
 *   <li><b>barramentos</b> (música, efeitos, interface) × geral, ajustáveis a qualquer momento;</li>
 *   <li><b>música</b>: troca com transição (sai em 1,2 s, entra em 1,5 s), abafamento suave (pausa); nunca
 *       reinicia se pedirem a mesma música de novo.</li>
 * </ul>
 * Tempo: o relógio só anda em {@link #update(float)} (determinístico com o tempo simulado dos roteiros).
 */
public final class SoundSystem implements AutoCloseable {
    public static final int MAX_VOICES = 24;
    static final float MUSIC_OUT = 1.2f, MUSIC_IN = 1.5f, AMBIENT_FADE = 2f;

    private final SoundCatalog cat;
    private final AudioOut out;
    private final SplittableRandom rng;
    private final Map<String, int[]> buffers = new HashMap<>();
    private final Map<String, Float> busVolume = new LinkedHashMap<>();
    private final Map<String, Float> lastTime = new HashMap<>();
    private final Map<String, Integer> lastChoice = new HashMap<>();
    private final List<Voice> voices = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Path base;
    private float master = 1f, time;

    // métricas (testes e relatório)
    public int played, suppressedInterval, suppressedLimit, stolen;
    public String lastEvent;
    public int lastVariation;
    public float lastPitch, lastGain;

    private record Voice(int id, String event, int priority, float start, float baseGain) { }

    private final class Track {
        final String id;
        final int stream;
        final float gain;
        float level, target;      // 0–1 (transição)
        Track(String id, int stream, float gain) { this.id = id; this.stream = stream; this.gain = gain; }
    }

    private Track music;
    private final Map<String, Track> ambients = new LinkedHashMap<>();
    private final Map<String, String> ambientBus = new HashMap<>();
    private final List<Track> fading = new ArrayList<>();
    private float duck = 1f, duckTarget = 1f;

    public SoundSystem(SoundCatalog cat, Path base, AudioOut out, long seed) {
        this.cat = cat;
        this.out = out;
        this.base = base;
        rng = new SplittableRandom(seed);
        busVolume.putAll(cat.buses);
        for (SoundCatalog.Event e : cat.events.values()) {
            int[] b = new int[e.files().size()];
            for (int i = 0; i < b.length; i++) {
                try {
                    Pcm p = Pcm.readWav(base.resolve(e.files().get(i)));
                    if (p.channels() != 1) problems.add(e.id() + ": " + e.files().get(i) + " não é mono");
                    b[i] = out.load(p);
                } catch (IOException ex) {
                    problems.add(e.id() + ": " + ex.getMessage());
                    b[i] = -1;                                             // arquivo ausente: a variação nunca toca
                }
            }
            buffers.put(e.id(), b);
        }
        for (String p : problems) System.err.println("áudio: " + p);
    }

    /** Arquivos ausentes/inválidos encontrados ao carregar (o jogo segue; os testes exigem lista vazia). */
    public List<String> problems() { return List.copyOf(problems); }

    private void note(String p) {
        if (!problems.contains(p)) { problems.add(p); System.err.println("áudio: " + p); }
    }

    public AudioOut out() { return out; }

    public float time() { return time; }

    // ------------------------------------------------------------------ eventos

    /** Toca um evento; devolve false se foi suprimido (intervalo, limite, desconhecido, sem arquivo). */
    public boolean event(String id) {
        SoundCatalog.Event e = cat.events.get(id);
        if (e == null) { note("evento desconhecido: " + id); return false; }
        Float last = lastTime.get(id);
        if (last != null && time - last < e.minInterval()) { suppressedInterval++; return false; }
        // limite do próprio evento: rouba a mais antiga dele
        List<Voice> mine = voices.stream().filter(v -> v.event.equals(id)).toList();
        if (mine.size() >= e.maxVoices()) { stopVoice(mine.getFirst()); stolen++; }
        // limite global: rouba a mais antiga de prioridade ≤; senão descarta
        if (voices.size() >= MAX_VOICES) {
            Voice victim = null;
            for (Voice v : voices) if (v.priority <= e.priority() && (victim == null || v.start < victim.start)) victim = v;
            if (victim == null) { suppressedLimit++; return false; }
            stopVoice(victim);
            stolen++;
        }
        int[] b = buffers.get(id);
        int n = b.length, k = 0;
        if (n > 1) {                                                       // nunca a mesma variação duas vezes seguidas
            int prev = lastChoice.getOrDefault(id, -1);
            k = rng.nextInt(prev >= 0 ? n - 1 : n);
            if (prev >= 0 && k >= prev) k++;
        }
        float semis = e.pitchSemitones() > 0 ? (float) (rng.nextDouble() * 2 - 1) * e.pitchSemitones() : 0f;
        float jit = e.gainJitterDb() > 0 ? (float) (rng.nextDouble() * 2 - 1) * e.gainJitterDb() : 0f;
        float pitch = (float) Math.pow(2, semis / 12.0);
        float baseGain = db(e.gainDb() + jit);
        lastTime.put(id, time);
        lastChoice.put(id, k);
        lastEvent = id;
        lastVariation = k;
        lastPitch = pitch;
        lastGain = baseGain;
        if (b[k] < 0) return false;
        int v = out.play(b[k], baseGain * bus(e.bus()), pitch);
        if (v <= 0) return false;
        voices.add(new Voice(v, id, e.priority(), time, baseGain));
        played++;
        return true;
    }

    private void stopVoice(Voice v) {
        out.stop(v.id);
        voices.remove(v);
    }

    public int activeVoices() { return voices.size(); }

    // ------------------------------------------------------------------ volume

    public void volume(String bus, float v) {
        if (bus.equals("geral")) master = clamp(v);
        else if (busVolume.containsKey(bus)) busVolume.put(bus, clamp(v));
        for (Voice x : voices) out.gain(x.id, x.baseGain * bus(cat.events.get(x.event).bus()));
        applyMusicGain();
    }

    public float volume(String bus) { return bus.equals("geral") ? master : busVolume.getOrDefault(bus, 0f); }

    /** Preferência do jogador (0–1) sobre o equilíbrio do catálogo: barramento = base do catálogo × f. */
    public void userVolume(String bus, float f) {
        Float base = cat.buses.get(bus);
        if (base != null) volume(bus, base * clamp(f));
    }

    private float bus(String b) { return master * busVolume.getOrDefault(b, 1f); }

    // ------------------------------------------------------------------ música

    /** Pede a música (null = silêncio). A mesma música pedida de novo não reinicia. */
    public void music(String id) {
        if (music != null && music.id.equals(id)) return;
        if (music != null) { music.target = 0; fading.add(music); music = null; }
        if (id == null) return;
        SoundCatalog.Music m = cat.music.get(id);
        if (m == null) { note("música desconhecida: " + id); return; }
        try {
            LoopingReader r = new LoopingReader(new VorbisSource(base.resolve(m.file())), m.loopStart(), m.loopEnd(), true);
            int s = out.stream(r, 0f);
            if (s <= 0) return;
            music = new Track(id, s, db(m.gainDb()));
            music.target = 1;
        } catch (IOException | RuntimeException ex) {
            note("música " + id + ": " + ex.getMessage());
            System.err.println("áudio: música " + id + " indisponível: " + ex.getMessage());
        }
    }

    public String currentMusic() { return music == null ? null : music.id; }

    // ------------------------------------------------------------------ ambiente

    /**
     * Liga uma camada de ambiente em laço (entra em 2 s). {@code pos} = posição no mundo (só para ambientes
     * posicionais do catálogo); null = cama sem posição. Pedir de novo o mesmo ambiente não reinicia.
     */
    public void ambient(String id, float[] pos) {
        if (ambients.containsKey(id)) return;
        SoundCatalog.Ambient a = cat.ambient.get(id);
        if (a == null) { note("ambiente desconhecido: " + id); return; }
        try {
            VorbisSource src = new VorbisSource(base.resolve(a.file()));
            if (a.positional() && src.channels() != 1) { src.close(); note("ambiente " + id + ": posicional exige mono"); return; }
            int s = out.stream(new LoopingReader(src, 0, src.frames(), true), 0f);
            if (s <= 0) return;
            if (a.positional() && pos != null) out.streamPosition(s, pos[0], pos[1], pos[2], a.ref(), a.max());
            Track t = new Track(id, s, db(a.gainDb()));
            t.target = 1;
            ambients.put(id, t);
            ambientBus.put(id, a.bus());
        } catch (IOException | RuntimeException ex) {
            note("ambiente " + id + ": " + ex.getMessage());
        }
    }

    public void stopAmbient(String id) {
        Track t = ambients.get(id);
        if (t != null) t.target = 0;                                   // sai em 2 s; parado no update
    }

    public java.util.Set<String> activeAmbients() { return ambients.keySet(); }

    /** Ouvinte (posição e direção do olhar) para os sons posicionados. */
    public void listener(float x, float y, float z, float fx, float fy, float fz) { out.listener(x, y, z, fx, fy, fz); }

    /** Abafa a música (pausa, diálogo): 1 = normal. Transição suave. */
    public void duck(float level) { duckTarget = clamp(level); }

    private void applyMusicGain() {
        float b = bus("musica") * duck;
        if (music != null) out.streamGain(music.stream, music.gain * music.level * b);
        for (Track t : fading) out.streamGain(t.stream, t.gain * t.level * b);
        for (Track t : ambients.values()) out.streamGain(t.stream, t.gain * t.level * bus(ambientBus.get(t.id)));
    }

    // ------------------------------------------------------------------ quadro

    public void update(float dt) {
        time += dt;
        duck += (duckTarget - duck) * (1f - (float) Math.exp(-dt * 6f));
        if (music != null) music.level = Math.min(music.target, music.level + dt / MUSIC_IN);
        for (Track t : fading) t.level = Math.max(0, t.level - dt / MUSIC_OUT);
        for (Track t : ambients.values())
            t.level = t.target > t.level ? Math.min(t.target, t.level + dt / AMBIENT_FADE) : Math.max(t.target, t.level - dt / AMBIENT_FADE);
        ambients.values().removeIf(t -> {
            if (t.target > 0 || t.level > 0) return false;
            out.stopStream(t.stream);
            ambientBus.remove(t.id);
            return true;
        });
        applyMusicGain();
        fading.removeIf(t -> {
            if (t.level > 0) return false;
            out.stopStream(t.stream);
            return true;
        });
        voices.removeIf(v -> !out.playing(v.id));
        out.update();
    }

    @Override
    public void close() {
        if (music != null) out.stopStream(music.stream);
        for (Track t : fading) out.stopStream(t.stream);
        for (Track t : ambients.values()) out.stopStream(t.stream);
        ambients.clear();
        for (Voice v : voices) out.stop(v.id);
        voices.clear();
        for (int[] b : buffers.values()) for (int x : b) if (x > 0) out.unload(x);
        buffers.clear();
        out.close();
    }

    static float db(float d) { return (float) Math.pow(10, d / 20.0); }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
