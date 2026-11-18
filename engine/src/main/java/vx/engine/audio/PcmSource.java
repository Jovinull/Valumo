package vx.engine.audio;

/** Fonte de PCM 16 bits intercalado lida em pedaços (música em fluxo). */
public interface PcmSource extends AutoCloseable {
    int channels();

    int rate();

    /** Total de quadros (amostras por canal). */
    long frames();

    /** Posiciona no quadro (exato). */
    void seek(long frame);

    /** Lê até {@code maxFrames} quadros em {@code out} (intercalado); devolve os quadros lidos (0 = fim). */
    int read(short[] out, int maxFrames);

    @Override
    void close();
}
