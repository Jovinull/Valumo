package vx.engine.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTLoopback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Rodada 09.1 — {@link AudioBackend} sobre OpenAL Soft (LWJGL). Um conjunto fixo de {@link #VOICES} fontes criado na
 * abertura; tocar escolhe uma fonte livre (sem alocar). Atenuação "inverse distance clamped": referência
 * {@link #REF_DIST} u (a distância da câmera: na luta o inimigo soa no nível da mistura; de longe, mais baixo), nada mais baixo que a 25 u. O OpenAL Soft limita a saída (limitador de picos) por padrão.
 *
 * <p>Dois modos:
 * <ul>
 *   <li>{@link #open()}: dispositivo de saída padrão do sistema (jogo);</li>
 *   <li>{@link #loopback(int)}: dispositivo "loopback" (extensão ALC_SOFT_loopback) — o mesmo mixer, mas a saída
 *       vai para um buffer que o jogo pede ({@link #render}) a cada quadro gravado: o som do vídeo é o que a aplicação
 *       produziu, sincronizado com o relógio do jogo e não com o relógio de parede.</li>
 * </ul>
 * Qualquer falha ao abrir lança {@link IllegalStateException}; quem chama cai para {@link SilentBackend}.
 */
public final class OpenAlBackend implements AudioBackend {
    public static final int VOICES = 24;
    public static final float REF_DIST = 4.5f, MAX_DIST = 25f;

    private final long device, context;
    private final boolean loopback;
    private final int rate;
    private final String name;
    private final int[] sources = new int[VOICES], generation = new int[VOICES];
    private final List<Integer> buffers = new ArrayList<>();
    private boolean hasClock;
    private final float[] orient = new float[6];                          // sem alocar a cada quadro
    private int next = VOICES - 1;

    private OpenAlBackend(long device, long context, boolean loopback, int rate, String name) {
        this.device = device;
        this.context = context;
        this.loopback = loopback;
        this.rate = rate;
        this.name = name;
    }

    /** Dispositivo de saída padrão. */
    public static OpenAlBackend open() {
        long dev = alcOpenDevice((ByteBuffer) null);
        if (dev == 0L) throw new IllegalStateException("nenhum dispositivo OpenAL disponível");
        ALCCapabilities caps = ALC.createCapabilities(dev);
        long ctx = alcCreateContext(dev, (int[]) null);
        if (ctx == 0L) { alcCloseDevice(dev); throw new IllegalStateException("contexto OpenAL recusado (erro " + alcGetError(dev) + ")"); }
        String nm = alcGetString(dev, ALC_DEVICE_SPECIFIER);
        OpenAlBackend b = new OpenAlBackend(dev, ctx, false, 0, "OpenAL Soft: " + nm);
        b.init(caps);
        return b;
    }

    /** Dispositivo loopback estéreo 16 bits a {@code rate} Hz (gravação sincronizada). */
    public static OpenAlBackend loopback(int rate) {
        long dev = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
        if (dev == 0L) throw new IllegalStateException("OpenAL sem ALC_SOFT_loopback");
        ALCCapabilities caps = ALC.createCapabilities(dev);
        if (!SOFTLoopback.alcIsRenderFormatSupportedSOFT(dev, rate, SOFTLoopback.ALC_STEREO_SOFT, SOFTLoopback.ALC_SHORT_SOFT)) {
            alcCloseDevice(dev);
            throw new IllegalStateException("loopback não aceita estéreo 16 bits a " + rate + " Hz");
        }
        long ctx = alcCreateContext(dev, new int[]{SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_SHORT_SOFT, ALC_FREQUENCY, rate, 0});
        if (ctx == 0L) { alcCloseDevice(dev); throw new IllegalStateException("contexto loopback recusado"); }
        OpenAlBackend b = new OpenAlBackend(dev, ctx, true, rate, "OpenAL Soft (loopback " + rate + " Hz, gravação)");
        b.init(caps);
        return b;
    }

    private void init(ALCCapabilities caps) {
        if (!alcMakeContextCurrent(context)) throw new IllegalStateException("não foi possível ativar o contexto OpenAL");
        AL.createCapabilities(caps);
        hasClock = caps.ALC_SOFT_device_clock;
        alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
        alGenSources(sources);
        for (int s : sources) {
            alSourcef(s, AL_REFERENCE_DISTANCE, REF_DIST);
            alSourcef(s, AL_MAX_DISTANCE, MAX_DIST);
            alSourcef(s, AL_ROLLOFF_FACTOR, 1f);
        }
        int err = alGetError();
        if (err != AL_NO_ERROR) { close(); throw new IllegalStateException("OpenAL: erro " + err + " ao criar as vozes"); }
    }

    @Override public String describe() { return name; }
    @Override public boolean audible() { return !loopback; }

    @Override public int load(String id, short[] mono, int sampleRate) {
        int buf = alGenBuffers();
        alBufferData(buf, AL_FORMAT_MONO16, mono, sampleRate);
        int err = alGetError();
        if (err != AL_NO_ERROR) throw new IllegalStateException("OpenAL: erro " + err + " ao carregar " + id);
        buffers.add(buf);
        return buffers.size() - 1;
    }

    @Override public int play(int sound, float gain, float pitch, boolean spatial, float x, float y, float z) {
        for (int k = 1; k <= VOICES; k++) {                               // rodízio a partir da última: a próxima quase
            int i = (next + k) % VOICES;                                  // sempre está livre (1 consulta, não 24)
            int s = sources[i], st = alGetSourcei(s, AL_SOURCE_STATE);
            if (st == AL_PLAYING || st == AL_PAUSED) continue;
            alSourcei(s, AL_BUFFER, buffers.get(sound));
            alSourcef(s, AL_GAIN, gain);
            alSourcef(s, AL_PITCH, pitch);
            alSourcei(s, AL_SOURCE_RELATIVE, spatial ? AL_FALSE : AL_TRUE);
            if (spatial) alSource3f(s, AL_POSITION, x, y, z); else alSource3f(s, AL_POSITION, 0f, 0f, 0f);
            alSourcePlay(s);
            next = i;
            generation[i]++;
            return i | (generation[i] << 8);
        }
        return -1;
    }

    @Override public void stop(int voice) {
        if (voice < 0) return;
        int i = voice & 0xff;
        if (i < VOICES && generation[i] == voice >>> 8) alSourceStop(sources[i]);
    }

    @Override public void stopAll() { for (int s : sources) alSourceStop(s); }

    @Override public void pauseAll(boolean paused) {
        for (int s : sources) {
            int st = alGetSourcei(s, AL_SOURCE_STATE);
            if (paused && st == AL_PLAYING) alSourcePause(s);
            else if (!paused && st == AL_PAUSED) alSourcePlay(s);
        }
    }

    @Override public void listener(float x, float y, float z, float fx, float fy, float fz, float ux, float uy, float uz) {
        alListener3f(AL_POSITION, x, y, z);
        orient[0] = fx; orient[1] = fy; orient[2] = fz; orient[3] = ux; orient[4] = uy; orient[5] = uz;
        alListenerfv(AL_ORIENTATION, orient);
    }

    @Override public void masterGain(float gain) { alListenerf(AL_GAIN, gain); }

    @Override public int activeVoices() {
        int n = 0;
        for (int s : sources) if (alGetSourcei(s, AL_SOURCE_STATE) == AL_PLAYING) n++;
        return n;
    }

    @Override public double reportedLatency() {
        if (!hasClock || loopback) return -1;
        long[] v = new long[2];
        SOFTDeviceClock.alcGetInteger64vSOFT(device, SOFTDeviceClock.ALC_DEVICE_CLOCK_LATENCY_SOFT, v);
        return v[1] / 1e9;
    }

    /** Loopback: mixa {@code frames} quadros estéreo em {@code out} (intercalado). */
    public void render(short[] out, int frames) {
        if (!loopback) throw new IllegalStateException("render só no loopback");
        SOFTLoopback.alcRenderSamplesSOFT(device, out, frames);
    }

    public int rate() { return rate; }

    @Override public void close() {
        try {
            for (int s : sources) if (s != 0) alSourceStop(s);
            alDeleteSources(sources);
            for (int b : buffers) alDeleteBuffers(b);
        } catch (RuntimeException ignored) { }
        alcMakeContextCurrent(0L);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
