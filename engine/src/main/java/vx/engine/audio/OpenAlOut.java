package vx.engine.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/**
 * Rodada 09.4 — saída OpenAL Soft (LWJGL). Dois modos:
 * <ul>
 *   <li><b>dispositivo</b>: o padrão do sistema (PipeWire/Pulse/ALSA pelo OpenAL Soft);</li>
 *   <li><b>loopback</b> (ALC_SOFT_loopback): sem dispositivo; a mixagem é puxada com {@link #render(int)}. Serve
 *       para gravar o áudio do jogo em sincronia com os quadros de um roteiro e para testes sem placa de som.</li>
 * </ul>
 * Fontes reservadas no início (pool fixo, nada alocado por quadro); buffers liberados no {@link #close()}.
 * Falha ao abrir → {@link #open} devolve {@code null} e quem chamou usa {@link SilentOut}.
 */
public final class OpenAlOut implements AudioOut {
    public static final int RATE = 48000;
    /**
     * Fila de cada fluxo (música, ambiente): pedaços de 0,25 s; começa com {@link #STREAM_START} (1 s, como antes) e cresce
     * UM pedaço por quadro até {@link #STREAM_BUFFERS} (4 s). Com 1 s fixo, montar o criador de dentro do jogo (a thread do
     * jogo fica ~2 s sem quadro) esvaziava a fila: 1,9 s de silêncio medidos em tempo real (fechamento do QA).
     * Volume e transições não atrasam: vão pelo ganho da fonte, não pelo conteúdo da fila.
     */
    static final int STREAM_BUFFERS = 16, STREAM_START = 4, STREAM_FRAMES = RATE / 4;

    private final long device, context;
    private final boolean loopback;
    private final String name;
    private final int[] sources;
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private final Map<Integer, Integer> voices = new HashMap<>();          // voz → fonte
    private final Map<Integer, Stream> streams = new HashMap<>();
    private final List<Integer> buffers = new ArrayList<>();
    private int nextVoice = 1;

    private final class Stream {
        final LoopingReader reader;
        final int source;
        final int[] bufs = new int[STREAM_BUFFERS];
        final short[] pcm;
        final ShortBuffer native_;
        boolean ended;
        int primed;                                                        // pedaços de bufs já postos na fila

        Stream(LoopingReader r, int source) {
            reader = r;
            this.source = source;
            pcm = new short[STREAM_FRAMES * r.channels()];
            native_ = MemoryUtil.memAllocShort(pcm.length);
            alGenBuffers(bufs);
            while (primed < STREAM_START && fill(bufs[primed])) primed++;
            alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
            alSource3f(source, AL_POSITION, 0, 0, 0);
            alSourcePlay(source);
        }

        boolean fill(int buf) {
            int n = reader.read(pcm, STREAM_FRAMES);
            if (n <= 0) { ended = true; return false; }
            native_.clear().put(pcm, 0, n * reader.channels()).flip();
            alBufferData(buf, reader.channels() == 2 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16, native_, reader.rate());
            alSourceQueueBuffers(source, buf);
            return true;
        }

        void update() {
            int done = alGetSourcei(source, AL_BUFFERS_PROCESSED);
            while (done-- > 0) {
                int b = alSourceUnqueueBuffers(source);
                if (!ended) fill(b);
            }
            if (!ended && primed < bufs.length && fill(bufs[primed])) primed++;   // a fila cresce um pedaço por quadro
            if (!ended && alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(source);   // falta de dados
        }

        void close() {
            alSourceStop(source);
            alSourcei(source, AL_BUFFER, 0);
            alDeleteBuffers(bufs);
            MemoryUtil.memFree(native_);
            reader.close();
        }
    }

    private OpenAlOut(long device, long context, boolean loopback, String name, int nSources) {
        this.device = device;
        this.context = context;
        this.loopback = loopback;
        this.name = name;
        sources = new int[nSources];
        alGenSources(sources);
        for (int s : sources) free.add(s);
    }

    /** Dispositivo padrão do sistema; {@code null} se não houver (o jogo segue mudo). */
    public static OpenAlOut open(int nSources) {
        try {
            long dev = alcOpenDevice((ByteBuffer) null);
            if (dev == MemoryUtil.NULL) return null;
            ALCCapabilities caps = ALC.createCapabilities(dev);
            long ctx = alcCreateContext(dev, (IntBuffer) null);
            if (ctx == MemoryUtil.NULL || !alcMakeContextCurrent(ctx)) { alcCloseDevice(dev); return null; }
            AL.createCapabilities(caps);
            String n = alcGetString(dev, ALC_DEVICE_SPECIFIER);
            return new OpenAlOut(dev, ctx, false, n == null ? "OpenAL" : n, nSources);
        } catch (Throwable t) {                     // natives ausentes, servidor de áudio fora do ar…
            System.err.println("áudio: OpenAL indisponível (" + t.getMessage() + "); seguindo sem som");
            return null;
        }
    }

    /** Loopback a 48 kHz estéreo 16 bits: nada sai no alto-falante; a mixagem vem de {@link #render}. */
    public static OpenAlOut loopback(int nSources) {
        try {
            long dev = alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
            if (dev == MemoryUtil.NULL) return null;
            ALCCapabilities caps = ALC.createCapabilities(dev);
            long ctx;
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer attr = st.ints(ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_SHORT_SOFT,
                        ALC_FREQUENCY, RATE, 0);
                ctx = alcCreateContext(dev, attr);
            }
            if (ctx == MemoryUtil.NULL || !alcMakeContextCurrent(ctx)) { alcCloseDevice(dev); return null; }
            AL.createCapabilities(caps);
            return new OpenAlOut(dev, ctx, true, "loopback 48 kHz", nSources);
        } catch (Throwable t) {
            System.err.println("áudio: loopback indisponível (" + t.getMessage() + ")");
            return null;
        }
    }

    /** Só no loopback: mistura {@code frames} quadros (estéreo intercalado) — o que o alto-falante tocaria. */
    public short[] render(int frames) {
        if (!loopback) throw new IllegalStateException("render só no loopback");
        ShortBuffer b = MemoryUtil.memAllocShort(frames * 2);
        try {
            alcRenderSamplesSOFT(device, b, frames);
            short[] out = new short[frames * 2];
            b.get(out);
            return out;
        } finally {
            MemoryUtil.memFree(b);
        }
    }

    @Override
    public int load(Pcm pcm) {
        int b = alGenBuffers();
        ShortBuffer d = MemoryUtil.memAllocShort(pcm.data().length);
        d.put(pcm.data()).flip();
        alBufferData(b, pcm.channels() == 2 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16, d, pcm.rate());
        MemoryUtil.memFree(d);
        buffers.add(b);
        return b;
    }

    @Override
    public void unload(int buffer) {
        alDeleteBuffers(buffer);
        buffers.remove((Integer) buffer);
    }

    @Override
    public int play(int buffer, float gain, float pitch) {
        if (buffer <= 0) return 0;                                      // antes de tirar a fonte do pool (não a perde)
        reclaim();
        Integer s = free.poll();
        if (s == null) return 0;
        alSourcei(s, AL_BUFFER, buffer);
        alSourcei(s, AL_SOURCE_RELATIVE, AL_TRUE);                       // sem posição: herói e interface
        alSource3f(s, AL_POSITION, 0, 0, 0);
        alSourcef(s, AL_GAIN, gain);
        alSourcef(s, AL_PITCH, pitch);
        alSourcei(s, AL_LOOPING, AL_FALSE);
        alSourcePlay(s);
        int v = nextVoice++;
        voices.put(v, s);
        return v;
    }

    @Override
    public boolean playing(int voice) {
        Integer s = voices.get(voice);
        return s != null && alGetSourcei(s, AL_SOURCE_STATE) == AL_PLAYING;
    }

    @Override
    public void gain(int voice, float gain) {
        Integer s = voices.get(voice);
        if (s != null) alSourcef(s, AL_GAIN, gain);
    }

    @Override
    public void stop(int voice) {
        Integer s = voices.remove(voice);
        if (s != null) {
            alSourceStop(s);
            alSourcei(s, AL_BUFFER, 0);
            free.add(s);
        }
    }

    private void reclaim() {
        voices.entrySet().removeIf(e -> {
            if (alGetSourcei(e.getValue(), AL_SOURCE_STATE) == AL_PLAYING) return false;
            alSourcei(e.getValue(), AL_BUFFER, 0);
            free.add(e.getValue());
            return true;
        });
    }

    @Override
    public int stream(LoopingReader reader, float gain) {
        reclaim();
        Integer s = free.poll();
        if (s == null) { reader.close(); return 0; }
        alSourcef(s, AL_GAIN, gain);
        alSourcef(s, AL_PITCH, 1f);
        int id = nextVoice++;
        streams.put(id, new Stream(reader, s));
        return id;
    }

    @Override
    public void streamGain(int stream, float gain) {
        Stream s = streams.get(stream);
        if (s != null) alSourcef(s.source, AL_GAIN, gain);
    }

    @Override
    public void stopStream(int stream) {
        Stream s = streams.remove(stream);
        if (s != null) {
            s.close();
            alSourcei(s.source, AL_SOURCE_RELATIVE, AL_TRUE);                  // devolve ao pool sem posição
            alSource3f(s.source, AL_POSITION, 0, 0, 0);
            free.add(s.source);
        }
    }

    @Override
    public void streamPosition(int stream, float x, float y, float z, float ref, float max) {
        Stream s = streams.get(stream);
        if (s == null) return;
        alSourcei(s.source, AL_SOURCE_RELATIVE, AL_FALSE);
        alSource3f(s.source, AL_POSITION, x, y, z);
        alSourcef(s.source, AL_REFERENCE_DISTANCE, ref);
        alSourcef(s.source, AL_MAX_DISTANCE, max);
        alSourcef(s.source, AL_ROLLOFF_FACTOR, 1f);
    }

    @Override
    public void listener(float x, float y, float z, float fx, float fy, float fz) {
        alListener3f(AL_POSITION, x, y, z);
        alListenerfv(AL_ORIENTATION, new float[]{fx, fy, fz, 0f, 1f, 0f});
    }

    @Override
    public void update() {
        for (Stream s : streams.values()) s.update();
        reclaim();
    }

    /** O dispositivo (outros conjuntos de vozes no MESMO contexto, como os efeitos do combate, consultam o relógio). */
    public long device() { return device; }

    /** Saída loopback (nada no alto-falante; a mistura vem de {@link #render}). */
    public boolean isLoopback() { return loopback; }

    /** Fontes ocupadas agora (vozes + fluxos) — testes e métricas. */
    public int busySources() { return sources.length - free.size(); }

    public int loadedBuffers() { return buffers.size(); }

    @Override public String name() { return name; }

    @Override
    public void close() {
        for (int id : List.copyOf(streams.keySet())) stopStream(id);
        for (int v : List.copyOf(voices.keySet())) stop(v);
        alDeleteSources(sources);
        for (int b : buffers) alDeleteBuffers(b);
        buffers.clear();
        alcMakeContextCurrent(MemoryUtil.NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
