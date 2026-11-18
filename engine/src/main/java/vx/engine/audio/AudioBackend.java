package vx.engine.audio;

/**
 * Rodada 09.1 — saída de som da engine (ADR 0016). Só toca sons já carregados; não sabe nada de jogo. O jogo produz
 * eventos lógicos e um diretor (módulo game) decide o que tocar. Implementações:
 * <ul>
 *   <li>{@link OpenAlBackend}: OpenAL Soft pelo LWJGL — dispositivo de saída real ou "loopback" (mixagem para um
 *       buffer, em sincronia com o relógio do jogo: gravação de vídeo com o som da própria aplicação);</li>
 *   <li>{@link SilentBackend}: sem dispositivo (falha ao abrir, testes, roteiros sem som) — o jogo segue mudo.</li>
 * </ul>
 * Uma thread só (a do jogo). Tudo carregado antes da luta: tocar não aloca nem decodifica.
 */
public interface AudioBackend extends AutoCloseable {
    /** Nome para o registro ("OpenAL Soft: <dispositivo>", "mudo: <motivo>"). */
    String describe();

    /** Há saída de som de verdade. */
    boolean audible();

    /** Carrega PCM mono 16 bits; devolve o identificador do som. */
    int load(String id, short[] mono, int rate);

    /**
     * Toca um som. {@code spatial}: posição no mundo (atenuação pela distância ao ouvinte); senão, colado ao ouvinte
     * (sons do próprio herói e interface). Devolve a voz (para parar) ou -1 se não havia voz livre.
     */
    int play(int sound, float gain, float pitch, boolean spatial, float x, float y, float z);

    /** Para uma voz (se ainda for a mesma: vozes reaproveitadas não são afetadas). */
    void stop(int voice);

    void stopAll();

    /** Pausa/retoma tudo o que está tocando (menu de pausa). */
    void pauseAll(boolean paused);

    /** Ouvinte (câmera): posição, frente e cima. */
    void listener(float x, float y, float z, float fx, float fy, float fz, float ux, float uy, float uz);

    /** Ganho final (volume principal × efeitos, 0 no mudo). */
    void masterGain(float gain);

    /** Vozes tocando agora. */
    int activeVoices();

    /** Latência de saída que o dispositivo informa (s), ou -1 se não sabe. */
    double reportedLatency();

    @Override void close();
}
