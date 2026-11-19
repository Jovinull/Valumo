package vx.game.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rodada 11.1 — a câmera do fundo do menu: tomadas escritas à mão ({@code assets/scenes/apresentacao_e.json}), em laço,
 * com movimento lento suavizado nas pontas e um escurecimento curto na troca. Só calcula; quem posiciona a câmera é o
 * {@code Game}. Sem OpenGL (o {@code ApresentacaoTest} confere cada olho contra a colisão real da vila).
 */
public final class Cinematic {
    /** Uma tomada: alvo, rumo/inclinação (radianos) e distância no início e no fim, duração, luz. */
    public record Shot(String name, float tx, float ty, float tz, float yaw0, float yaw1, float pitch0, float pitch1, float dist0,
                       float dist1, float duration, String light) { }

    /** Onde a câmera está agora: alvo, rumo, inclinação, distância, escurecimento (0–1), a tomada e se ela acabou de começar. */
    public record Pose(float tx, float ty, float tz, float yaw, float pitch, float dist, float fade, Shot shot, boolean cut) { }

    public final List<Shot> shots;
    public final float transition;
    public final String scene;
    /** Vitrine da tela de personagens: x, z, rumo (radianos) do herói. */
    public final float[] showcase;
    private float t;
    private int last = -1;

    Cinematic(List<Shot> shots, float transition, String scene, float[] showcase) {
        this.shots = List.copyOf(shots);
        this.transition = transition;
        this.scene = scene;
        this.showcase = showcase;
    }

    public static Cinematic load(Path assets) {
        try {
            return parse(Files.readString(assets.resolve("scenes/apresentacao_e.json")));
        } catch (IOException e) {
            throw new UncheckedIOException("apresentação ausente", e);
        }
    }

    public static Cinematic parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (!"valumo.apresentacao".equals(o.get("formato").getAsString())) throw new IllegalArgumentException("formato da apresentação");
        List<Shot> l = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("tomadas")) {
            JsonObject s = e.getAsJsonObject();
            JsonArray a = s.getAsJsonArray("alvo");
            float dur = s.get("duracao").getAsFloat();
            if (!(dur > 1f)) throw new IllegalArgumentException("duração curta demais: " + s.get("nome"));
            l.add(new Shot(s.get("nome").getAsString(), a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                    rad(s, "rumo", 0), rad(s, "rumo", 1), rad(s, "inclinacao", 0), rad(s, "inclinacao", 1),
                    s.getAsJsonArray("distancia").get(0).getAsFloat(), s.getAsJsonArray("distancia").get(1).getAsFloat(), dur,
                    s.has("luz") ? s.get("luz").getAsString() : "dia"));
        }
        if (l.isEmpty()) throw new IllegalArgumentException("nenhuma tomada");
        JsonArray h = o.getAsJsonObject("vitrine").getAsJsonArray("heroi");
        float[] show = {h.get(0).getAsFloat(), h.get(1).getAsFloat(), (float) Math.toRadians(h.get(2).getAsFloat())};
        return new Cinematic(l, o.has("transicao") ? o.get("transicao").getAsFloat() : 0.6f, o.get("cena").getAsString(), show);
    }

    private static float rad(JsonObject s, String k, int i) { return (float) Math.toRadians(s.getAsJsonArray(k).get(i).getAsFloat()); }

    public float total() {
        float sum = 0;
        for (Shot s : shots) sum += s.duration();
        return sum;
    }

    public void restart() { t = 0; last = -1; }

    /** Vai para o instante {@code time} do laço (capturas de uma tomada). */
    public void seek(float time) { t = ((time % total()) + total()) % total(); last = -1; }

    /** Avança o relógio da apresentação e devolve a pose (laço). */
    public Pose advance(float dt) {
        t = (t + dt) % total();
        return at(t);
    }

    /** Pose no instante {@code time} (s desde o começo do laço). */
    public Pose at(float time) {
        float u = ((time % total()) + total()) % total();
        int i = 0;
        while (u >= shots.get(i).duration()) { u -= shots.get(i).duration(); i++; }
        Shot s = shots.get(i);
        float k = u / s.duration();
        float e = k * k * (3 - 2 * k);                                   // devagar nas pontas
        float fadeIn = Math.clamp(1f - u / transition, 0f, 1f), fadeOut = Math.clamp(1f - (s.duration() - u) / transition, 0f, 1f);
        boolean cut = i != last;
        last = i;
        return new Pose(s.tx(), s.ty(), s.tz(), s.yaw0() + (s.yaw1() - s.yaw0()) * e, s.pitch0() + (s.pitch1() - s.pitch0()) * e,
                s.dist0() + (s.dist1() - s.dist0()) * e, Math.max(fadeIn, fadeOut), s, cut);
    }

    /** Câmera da vitrine (personagens): {alvo x, y, z, rumo, inclinação, distância} para o herói em (x, chão, z) de rumo {@code yaw}. */
    public static float[] showcaseCamera(float x, float ground, float z, float yaw) {
        float cy = yaw + 0.18f, rx = (float) Math.cos(cy), rz = -(float) Math.sin(cy);
        return new float[]{x - rx * 1.05f, ground + 0.78f, z - rz * 1.05f, cy, 0.07f, 3.5f};
    }

    /** O olho da pose (a mesma conta da câmera do jogo). */
    public static float[] eye(Pose p) {
        float cp = (float) Math.cos(p.pitch());
        return new float[]{p.tx() + p.dist() * cp * (float) Math.sin(p.yaw()), p.ty() + p.dist() * (float) Math.sin(p.pitch()),
                p.tz() + p.dist() * cp * (float) Math.cos(p.yaw())};
    }
}
