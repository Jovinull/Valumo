package vx.engine.audio;

/** Saída muda: sem dispositivo, áudio desligado, capturas e testes. Nada falha; nada toca. */
public final class SilentOut implements AudioOut {
    private final String reason;

    public SilentOut(String reason) { this.reason = reason; }

    @Override public int load(Pcm pcm) { return 0; }
    @Override public void unload(int buffer) { }
    @Override public int play(int buffer, float gain, float pitch) { return 0; }
    @Override public boolean playing(int voice) { return false; }
    @Override public void gain(int voice, float gain) { }
    @Override public void stop(int voice) { }
    @Override public int stream(LoopingReader reader, float gain) { reader.close(); return 0; }
    @Override public void streamGain(int stream, float gain) { }
    @Override public void stopStream(int stream) { }
    @Override public void streamPosition(int stream, float x, float y, float z, float ref, float max) { }
    @Override public void listener(float x, float y, float z, float fx, float fy, float fz) { }
    @Override public void update() { }
    @Override public String name() { return "mudo (" + reason + ")"; }
    @Override public void close() { }
}
