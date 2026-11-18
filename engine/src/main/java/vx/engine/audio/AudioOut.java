package vx.engine.audio;

/**
 * Rodada 09.4 — saída de áudio de baixo nível (buffers, vozes, fluxo). A regra de quando e o que tocar fica em
 * {@link SoundSystem}; aqui só o dispositivo. Implementações: {@link OpenAlOut} (dispositivo real ou loopback
 * para gravar/testar) e {@link SilentOut} (sem dispositivo: o jogo segue sem som).
 */
public interface AudioOut extends AutoCloseable {
    /** Envia o PCM ao dispositivo; devolve o identificador do buffer (≤ 0 = indisponível). */
    int load(Pcm pcm);

    void unload(int buffer);

    /** Toca o buffer uma vez; devolve a voz (≤ 0 = não tocou). */
    int play(int buffer, float gain, float pitch);

    boolean playing(int voice);

    void gain(int voice, float gain);

    void stop(int voice);

    /** Começa um fluxo (música) a partir do leitor; devolve o fluxo (≤ 0 = não tocou). */
    int stream(LoopingReader reader, float gain);

    void streamGain(int stream, float gain);

    void stopStream(int stream);

    /** Põe um fluxo MONO no mundo (posição em unidades; volume cai de ref até max). */
    void streamPosition(int stream, float x, float y, float z, float ref, float max);

    /** Ouvinte: posição e direção para onde olha (o "para cima" é +Y). */
    void listener(float x, float y, float z, float fx, float fy, float fz);

    /** Por quadro: reabastece os fluxos e recolhe vozes encerradas. */
    void update();

    /** Nome legível do dispositivo (relatórios). */
    String name();

    @Override
    void close();
}
