package vx.engine.audio;

import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Rodada 09.4 — OGG Vorbis por stb_vorbis (LWJGL), lido em pedaços. O arquivo comprimido fica em memória
 * (≈ 2 MB por música); o PCM é decodificado só à medida que toca.
 */
public final class VorbisSource implements PcmSource {
    private final ByteBuffer file;
    private final long handle;
    private final int channels, rate;
    private final long frames;
    private ShortBuffer scratch;

    /** Maior OGG aceito (bytes): a música mais longa do projeto tem ~2,6 MB. */
    public static final long MAX_BYTES = 64L << 20;

    /** Abre um OGG Vorbis. Vazio, inválido, sem amostras, com mais de dois canais ou grande demais = {@link IOException}. */
    public VorbisSource(Path path) throws IOException {
        long size = Files.size(path);
        if (size == 0 || size > MAX_BYTES) throw new IOException(path + ": OGG com " + size + " bytes");
        byte[] bytes = Files.readAllBytes(path);
        file = memAlloc(bytes.length);
        file.put(bytes).flip();
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer err = st.mallocInt(1);
            handle = stb_vorbis_open_memory(file, err, null);
            if (handle == NULL) {
                memFree(file);
                throw new IOException(path + ": OGG inválido (stb_vorbis erro " + err.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(st);
            stb_vorbis_get_info(handle, info);
            channels = info.channels();
            rate = info.sample_rate();
        }
        frames = stb_vorbis_stream_length_in_samples(handle);
        if (frames <= 0 || channels < 1 || channels > 2 || rate <= 0) {
            close();
            throw new IOException(path + ": OGG sem amostras utilizáveis (" + frames + " quadros, " + channels + " canais, " + rate + " Hz)");
        }
    }

    @Override public int channels() { return channels; }
    @Override public int rate() { return rate; }
    @Override public long frames() { return frames; }

    @Override
    public void seek(long frame) { stb_vorbis_seek(handle, (int) frame); }

    @Override
    public int read(short[] out, int maxFrames) {
        int n = maxFrames * channels;
        if (scratch == null || scratch.capacity() < n) {
            if (scratch != null) memFree(scratch);
            scratch = memAllocShort(n);
        }
        scratch.clear().limit(n);
        int got = stb_vorbis_get_samples_short_interleaved(handle, channels, scratch);
        scratch.get(0, out, 0, got * channels);
        return got;
    }

    @Override
    public void close() {
        stb_vorbis_close(handle);
        memFree(file);
        if (scratch != null) memFree(scratch);
        scratch = null;
    }
}
