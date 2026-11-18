package vx.engine.audio;

/**
 * Rodada 09 — leitura com laço EXATO na amostra: toca do início até {@code loopEnd} e, a partir daí, volta a
 * {@code loopStart} sem pular nem repetir nenhum quadro. A música do jogo é renderizada com introdução + duas
 * passadas do corpo; o laço é a 2ª passada, que já começa com a cauda da 1ª (tools/audio/render_musica.py).
 * Sem OpenGL nem OpenAL: testável com uma fonte sintética.
 */
public final class LoopingReader {
    private final PcmSource src;
    private final long loopStart, loopEnd;
    private long pos;
    private final boolean loop;

    public LoopingReader(PcmSource src, long loopStart, long loopEnd, boolean loop) {
        long n = src.frames();
        if (loopEnd <= 0 || loopEnd > n) loopEnd = n;
        if (loopStart < 0 || loopStart >= loopEnd) throw new IllegalArgumentException("laço inválido: " + loopStart + "–" + loopEnd + " de " + n);
        this.src = src;
        this.loopStart = loopStart;
        this.loopEnd = loopEnd;
        this.loop = loop;
    }

    public int channels() { return src.channels(); }

    public int rate() { return src.rate(); }

    public long position() { return pos; }

    /** Preenche até {@code frames} quadros; devolve quantos (menos que o pedido só no fim sem laço). */
    public int read(short[] out, int frames) {
        int ch = src.channels(), done = 0;
        short[] tmp = out;
        while (done < frames) {
            if (pos >= loopEnd) {
                if (!loop) break;
                src.seek(loopStart);
                pos = loopStart;
            }
            int want = (int) Math.min(frames - done, loopEnd - pos);
            short[] part = done == 0 ? tmp : new short[want * ch];
            int got = src.read(part, want);
            if (got <= 0) {                                    // fonte acabou antes do esperado: trata como fim do laço
                if (!loop) break;
                pos = loopEnd;
                continue;
            }
            if (part != tmp) System.arraycopy(part, 0, out, done * ch, got * ch);
            done += got;
            pos += got;
        }
        return done;
    }

    public void close() { src.close(); }
}
