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

    /**
     * Lê um WAV PCM 16 bits. Malformado (tamanho negativo, "fmt " curto, canais fora de 1–2, taxa ≤ 0, grande demais) =
     * {@link IOException}; nunca laço infinito nem leitura além do fim (um "data" maior que o arquivo vai até o fim).
     */
    public static Wav read(Path p) throws IOException {
        if (Files.size(p) > Pcm.MAX_BYTES) throw new IOException("WAV grande demais: " + p);
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.limit() < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("não é WAV: " + p);
        int pos = 12, rate = 0, ch = 0, bits = 0;
        while (pos + 8 <= b.limit()) {
            int id = b.getInt(pos), len = b.getInt(pos + 4);
            if (len < 0) throw new IOException("WAV com bloco de tamanho negativo: " + p);
            if (id == 0x20746d66) {                                       // "fmt "
                if (len < 16 || pos + 24 > b.limit()) throw new IOException("WAV com fmt truncado: " + p);
                if (b.getShort(pos + 8) != 1) throw new IOException("WAV não é PCM: " + p);
                ch = b.getShort(pos + 10);
                rate = b.getInt(pos + 12);
                bits = b.getShort(pos + 22);
            } else if (id == 0x61746164) {                                // "data"
                if (bits != 16) throw new IOException("WAV precisa de 16 bits: " + p);
                if (ch < 1 || ch > 2 || rate <= 0) throw new IOException("WAV com " + ch + " canais a " + rate + " Hz: " + p);
                short[] s = new short[Math.min(len, b.limit() - pos - 8) / 2];
                for (int i = 0; i < s.length; i++) s[i] = b.getShort(pos + 8 + i * 2);
                return new Wav(s, rate, ch);
            }
            long next = (long) pos + 8 + len + (len & 1);
            if (next > b.limit()) break;
            pos = (int) next;
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
