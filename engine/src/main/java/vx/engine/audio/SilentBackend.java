package vx.engine.audio;

/** Rodada 09.1 — sem som (nenhum dispositivo, ou roteiro/teste): aceita tudo e não toca nada. */
public final class SilentBackend implements AudioBackend {
    private final String why;
    private int sounds;

    public SilentBackend(String why) { this.why = why; }

    @Override public String describe() { return "mudo: " + why; }
    @Override public boolean audible() { return false; }
    @Override public int load(String id, short[] mono, int rate) { return sounds++; }
    @Override public int play(int sound, float gain, float pitch, boolean spatial, float x, float y, float z) { return -1; }
    @Override public void stop(int voice) { }
    @Override public void stopAll() { }
    @Override public void pauseAll(boolean paused) { }
    @Override public void listener(float x, float y, float z, float fx, float fy, float fz, float ux, float uy, float uz) { }
    @Override public void masterGain(float gain) { }
    @Override public int activeVoices() { return 0; }
    @Override public double reportedLatency() { return -1; }
    @Override public void close() { }
}
