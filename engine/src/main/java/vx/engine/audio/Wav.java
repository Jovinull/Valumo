package vx.engine.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Rodada 09.1 — WAV PCM 16 bits (leitura mono para os efeitos; escrita estéreo para a gravação por loopback). */
public final class Wav {
    public final short[] samples;
    public final int rate, channels;

    private Wav(short[] samples, int rate, int channels) { this.samples = samples; this.rate = rate; this.channels = channels; }

    public static Wav read(Path p) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("não é WAV: " + p);
        int pos = 12, rate = 0, ch = 0, bits = 0;
        while (pos + 8 <= b.limit()) {
            int id = b.getInt(pos), len = b.getInt(pos + 4);
            if (id == 0x20746d66) {                                       // "fmt "
                if (b.getShort(pos + 8) != 1) throw new IOException("WAV não é PCM: " + p);
                ch = b.getShort(pos + 10);
                rate = b.getInt(pos + 12);
                bits = b.getShort(pos + 22);
            } else if (id == 0x61746164) {                                // "data"
                if (bits != 16) throw new IOException("WAV precisa de 16 bits: " + p);
                short[] s = new short[len / 2];
                for (int i = 0; i < s.length; i++) s[i] = b.getShort(pos + 8 + i * 2);
                return new Wav(s, rate, ch);
            }
            pos += 8 + len + (len & 1);
        }
        throw new IOException("WAV sem dados: " + p);
    }

    /** Escreve PCM 16 bits intercalado. */
    public static void write(Path p, short[] interleaved, int count, int rate, int channels) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + count * 2).putInt(0x45564157);
        b.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate)
                .putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        b.putInt(0x61746164).putInt(count * 2);
        for (int i = 0; i < count; i++) b.putShort(interleaved[i]);
        Files.write(p, b.array());
    }
}
