package vx.engine.audio;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rodada 09 — ponto único do áudio no processo (jogo e criador usam o mesmo). Enquanto ninguém chamar
 * {@link #start}, tudo é mudo e sem custo: capturas, testes e roteiros não abrem dispositivo nenhum.
 * <pre>
 *   -Dvx.audio=dispositivo|loopback|mudo     (padrão: o que o chamador pedir)
 *   -Dvx.vol.geral=0..1  -Dvx.vol.musica=…  -Dvx.vol.efeitos=…  -Dvx.vol.interface=…  -Dvx.vol.ambiente=…
 *   -Dvx.musica=vila_a|vila_b|nenhuma         (estudos da rodada 09; nenhuma é canônica)
 *   -Dvx.audio.gravar=arquivo.wav             (com loopback) grava a mixagem do jogo, {@code dt} a {@code dt},
 *                                             em sincronia com os quadros de um roteiro
 * </pre>
 * Quem chama só emite eventos semânticos ({@link #event}); nunca caminhos, buffers ou dispositivo.
 */
public final class Audio {
    public enum Mode { DEVICE, LOOPBACK, OFF }

    private static SoundSystem sys;
    private static OpenAlOut al;
    private static java.io.RandomAccessFile capture;
    private static long captured;
    private static double carry;
    private static boolean recording = true;

    private Audio() { }

    /** Abre o áudio uma vez (as chamadas seguintes devolvem o mesmo sistema). Falha → mudo, nunca exceção. */
    public static synchronized SoundSystem start(Path assets, Mode wanted) {
        if (sys != null) return sys;
        Mode mode = switch (System.getProperty("vx.audio", "")) {
            case "dispositivo" -> Mode.DEVICE;
            case "loopback" -> Mode.LOOPBACK;
            case "mudo", "off" -> Mode.OFF;
            default -> wanted;
        };
        Path catFile = assets.resolve("audio/sons.json");
        SoundCatalog cat;
        try {
            cat = SoundCatalog.load(catFile);
        } catch (Exception e) {
            System.err.println("áudio: catálogo indisponível (" + e.getMessage() + "); seguindo sem som");
            return null;
        }
        AudioOut out = switch (mode) {
            case DEVICE -> al = OpenAlOut.open(32);
            case LOOPBACK -> al = OpenAlOut.loopback(32);
            case OFF -> null;
        };
        if (out == null) out = new SilentOut(mode == Mode.OFF ? "desligado" : "sem dispositivo");
        sys = new SoundSystem(cat, assets.resolve("audio"), out, 0x5A1L);
        for (String b : new String[]{"geral", "musica", "efeitos", "interface", "ambiente"}) {
            String v = System.getProperty("vx.vol." + b);
            if (v != null) sys.volume(b, Float.parseFloat(v));
        }
        String rec = System.getProperty("vx.audio.gravar");
        if (rec != null && mode == Mode.LOOPBACK && al != null) {
            try {
                Path f = Path.of(rec);
                if (f.getParent() != null) Files.createDirectories(f.getParent());
                capture = new java.io.RandomAccessFile(f.toFile(), "rw");
                capture.setLength(0);
                capture.write(new byte[44]);                                   // cabeçalho escrito no fim
            } catch (java.io.IOException e) {
                System.err.println("áudio: não consegui gravar em " + rec + ": " + e.getMessage());
            }
        }
        System.out.println("áudio: " + out.name() + (capture != null ? " (gravando " + rec + ")" : "")
                + (sys.problems().isEmpty() ? "" : " — " + sys.problems().size() + " problema(s)"));
        return sys;
    }

    /** Música pedida por propriedade (ou o padrão do chamador). "nenhuma" desliga. */
    public static String musicChoice(String fallback) {
        String m = System.getProperty("vx.musica", fallback);
        return m == null || m.equals("nenhuma") ? null : m;
    }

    public static SoundSystem get() { return sys; }

    /** Saída loopback (gravação sincronizada), ou null. */
    public static OpenAlOut loopback() { return al != null && sys != null && sys.out() == al && isLoopback() ? al : null; }

    private static boolean isLoopback() { return al.name().startsWith("loopback"); }

    public static void event(String id) { if (sys != null) sys.event(id); }

    public static void music(String id) { if (sys != null) sys.music(id); }

    public static void duck(float level) { if (sys != null) sys.duck(level); }

    public static void ambient(String id, float[] pos) { if (sys != null) sys.ambient(id, pos); }

    public static void listener(float x, float y, float z, float fx, float fy, float fz) {
        if (sys != null) sys.listener(x, y, z, fx, fy, fz);
    }

    /** Por quadro. Gravando: mistura exatamente dt segundos (resto acumulado) — áudio e quadros em sincronia. */
    public static void update(float dt) {
        if (sys == null) return;
        sys.update(dt);
        if (al != null && isLoopback()) {                 // o loopback só avança quando misturado: mistura sempre
            carry += dt * OpenAlOut.RATE;
            int n = (int) carry;
            carry -= n;
            if (n <= 0) return;
            short[] pcm = al.render(n);
            if (capture == null || !recording) return;    // grava só enquanto o roteiro grava quadros
            byte[] b = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) { b[2 * i] = (byte) pcm[i]; b[2 * i + 1] = (byte) (pcm[i] >> 8); }
            try { capture.write(b); captured += n; } catch (java.io.IOException e) { capture = null; }
        }
    }

    /** Roteiros: grava áudio só entre {@code record on} e {@code record off} (mesmos quadros do vídeo). */
    public static void recording(boolean on) { recording = on; }

    public static synchronized void stop() {
        if (capture != null) {
            try {
                java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                int data = (int) (captured * 4);
                h.putInt(0x46464952).putInt(36 + data).putInt(0x45564157).putInt(0x20746d66).putInt(16)
                        .putShort((short) 1).putShort((short) 2).putInt(OpenAlOut.RATE).putInt(OpenAlOut.RATE * 4)
                        .putShort((short) 4).putShort((short) 16).putInt(0x61746164).putInt(data);
                capture.seek(0);
                capture.write(h.array());
                capture.close();
                System.out.printf("áudio: gravados %.2f s%n", captured / (double) OpenAlOut.RATE);
            } catch (java.io.IOException e) {
                System.err.println("áudio: falha ao fechar a gravação: " + e.getMessage());
            }
            capture = null;
            captured = 0;
        }
        if (sys != null) sys.close();
        sys = null;
        al = null;
    }

    /** Há catálogo de som neste diretório de assets? */
    public static boolean available(Path assets) { return Files.isRegularFile(assets.resolve("audio/sons.json")); }
}
