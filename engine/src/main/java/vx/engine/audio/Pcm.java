package vx.engine.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rodada 09.4 — áudio PCM 16 bits intercalado em memória (efeitos curtos). O leitor de WAV é próprio (sem
 * dependência): aceita PCM 16 bits, 1 ou 2 canais, cabeçalho RIFF/WAVE com blocos extras (LIST etc.).
 */
public record Pcm(short[] data, int channels, int rate) {

    public int frames() { return data.length / channels; }

    public float seconds() { return frames() / (float) rate; }

    /** Lê um WAV PCM 16 bits. Erro claro para qualquer outro formato (o catálogo exige 16 bits). */
    public static Pcm readWav(Path file) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157)
            throw new IOException(file + ": não é RIFF/WAVE");
        int pos = 12, channels = 0, rate = 0, bits = 0, format = 0;
        while (pos + 8 <= b.limit()) {
            int id = b.getInt(pos), size = b.getInt(pos + 4);
            int body = pos + 8;
            if (id == 0x20746d66) {                                   // "fmt "
                format = b.getShort(body) & 0xffff;
                channels = b.getShort(body + 2);
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14);
                if (format == 0xFFFE && size >= 26) format = b.getShort(body + 24) & 0xffff;   // WAVE_FORMAT_EXTENSIBLE
            } else if (id == 0x61746164) {                            // "data"
                if (format != 1 || bits != 16) throw new IOException(file + ": só PCM 16 bits (formato " + format + ", " + bits + " bits)");
                if (channels < 1 || channels > 2) throw new IOException(file + ": " + channels + " canais");
                int n = Math.min(size, b.limit() - body) / 2;
                short[] d = new short[n - n % channels];
                for (int i = 0; i < d.length; i++) d[i] = b.getShort(body + 2 * i);
                return new Pcm(d, channels, rate);
            }
            pos = body + size + (size & 1);
        }
        throw new IOException(file + ": sem bloco de dados");
    }
}
