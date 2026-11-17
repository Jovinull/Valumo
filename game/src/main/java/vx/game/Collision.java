package vx.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.viewer.Animator;
import vx.viewer.World;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rodada 08 — colisão do jogo, separada da geometria visual: o relevo (colunas de voxel do T1, as mesmas que
 * aparecem) mais formas simples em XZ com faixa de altura — círculos (troncos, pedras, pessoas) e caixas
 * orientadas (casa, cerca, caixotes). As formas vêm de perfis escritos à mão por tipo de prop
 * ({@code assets/scenes/colisao_e.json}); a malha nunca é usada para colidir.
 *
 * <p>Não é um motor físico: empurra um círculo para fora das formas, diz se o chão é pisável e testa a linha
 * de visão da câmera. Tudo sem OpenGL (testável).
 */
public final class Collision {
    /** Forma de colisão. Círculo: r; caixa: meias-extensões hx, hz e rumo (cos, sin). */
    public static final class Shape {
        public final String name;
        public final boolean circle, body, camera;
        public final float cx, cz, r, hx, hz, cos, sin, y0, y1;

        Shape(String name, boolean circle, float cx, float cz, float r, float hx, float hz, float cos, float sin,
              float y0, float y1, boolean body, boolean camera) {
            this.name = name; this.circle = circle; this.cx = cx; this.cz = cz; this.r = r; this.hx = hx; this.hz = hz;
            this.cos = cos; this.sin = sin; this.y0 = y0; this.y1 = y1; this.body = body; this.camera = camera;
        }

        static Shape circle(String n, float x, float z, float r, float y0, float y1, boolean body, boolean cam) {
            return new Shape(n, true, x, z, r, 0, 0, 1, 0, y0, y1, body, cam);
        }

        static Shape box(String n, float x, float z, float hx, float hz, float yaw, float y0, float y1, boolean body, boolean cam) {
            return new Shape(n, false, x, z, 0, hx, hz, (float) Math.cos(yaw), (float) Math.sin(yaw), y0, y1, body, cam);
        }

        /**
         * Vetor para tirar um círculo (x, z, rad) de dentro da forma, ou null se não encosta. Caixa: no espaço
         * local (rotateY do JOML: mundo = (c·lx + s·lz, −s·lx + c·lz)).
         */
        float[] push(float x, float z, float rad) {
            float dx = x - cx, dz = z - cz;
            if (circle) {
                float d2 = dx * dx + dz * dz, min = r + rad;
                if (d2 >= min * min) return null;
                float d = (float) Math.sqrt(d2);
                if (d < 1e-5f) return new float[]{min, 0};
                return new float[]{dx / d * (min - d), dz / d * (min - d)};
            }
            float lx = cos * dx - sin * dz, lz = sin * dx + cos * dz;
            float qx = Math.clamp(lx, -hx, hx), qz = Math.clamp(lz, -hz, hz);
            float ox, oz;
            if (qx == lx && qz == lz) {                                   // centro dentro da caixa: sai pelo lado mais perto
                float px = hx - Math.abs(lx), pz = hz - Math.abs(lz);
                if (px < pz) { ox = Math.signum(lx == 0 ? 1 : lx) * (px + rad); oz = 0; }
                else { ox = 0; oz = Math.signum(lz == 0 ? 1 : lz) * (pz + rad); }
            } else {
                float ex = lx - qx, ez = lz - qz, d2 = ex * ex + ez * ez;
                if (d2 >= rad * rad) return null;
                float d = (float) Math.sqrt(d2);
                ox = ex / d * (rad - d);
                oz = ez / d * (rad - d);
            }
            return new float[]{cos * ox + sin * oz, -sin * ox + cos * oz};
        }

        /** Ponto dentro da forma inflada por m (câmera). */
        boolean contains(float x, float y, float z, float m) {
            if (y < y0 - m || y > y1 + m) return false;
            float dx = x - cx, dz = z - cz;
            if (circle) return dx * dx + dz * dz < (r + m) * (r + m);
            float lx = cos * dx - sin * dz, lz = sin * dx + cos * dz;
            return Math.abs(lx) < hx + m && Math.abs(lz) < hz + m;
        }
    }

    public final Animator.Ground ground;
    public final List<Shape> shapes = new ArrayList<>();
    public float waterLevel = World.WATER_LEVEL;
    public float limitX, limitZ, limitR = Float.MAX_VALUE;
    public Collision(Animator.Ground ground) { this.ground = ground; }

    // ------------------------------------------------------------------ montagem a partir dos perfis

    /** Perfis por tipo de prop ({@code colisao_e.json}). */
    public static JsonObject profiles(Path assets) {
        try {
            return JsonParser.parseString(Files.readString(assets.resolve("scenes/colisao_e.json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("assets/scenes/colisao_e.json ausente", e);
        }
    }

    static JsonObject profileFor(JsonObject prof, String name) {
        JsonObject props = prof.getAsJsonObject("props");
        if (props.has(name)) return props.getAsJsonObject(name);
        for (String k : props.keySet())
            if (k.endsWith("*") && name.startsWith(k.substring(0, k.length() - 1))) return props.getAsJsonObject(k);
        return prof.getAsJsonObject("padrao");
    }

    static float num(JsonObject o, String k, float def) { return o.has(k) ? o.get(k).getAsFloat() : def; }

    /** Acrescenta as formas de um prop posicionado (matriz de mundo + caixa da malha no espaço do modelo). */
    public void addProp(JsonObject prof, World.Placement p) {
        JsonObject pr = profileFor(prof, p.name());
        String kind = pr.get("tipo").getAsString();
        if (kind.equals("nenhum")) return;
        float[] b = p.bounds();
        Matrix4f m = p.model();
        Vector3f c = m.transformPosition(new Vector3f((b[0] + b[3]) / 2, b[1], (b[2] + b[5]) / 2));
        float yaw = (float) Math.atan2(m.m20(), m.m00());                 // rotateY: m00 = cos, m20 = sin
        float w = b[3] - b[0], d = b[5] - b[2], h = b[4] - b[1], y0 = c.y, y1 = c.y + h * num(pr, "altura", 1f);
        boolean cam = !pr.has("camera") || pr.get("camera").getAsBoolean();
        switch (kind) {
            case "caixa" -> {
                float k = num(pr, "escala", 1f);
                shapes.add(Shape.box(p.name(), c.x, c.z, w / 2 * k, d / 2 * k, yaw, y0, y1, true, cam));
            }
            case "circulo" -> shapes.add(Shape.circle(p.name(), c.x, c.z, num(pr, "raio", Math.min(w, d) / 2), y0, y1, true, cam));
            case "tronco" -> {
                // o corpo bate no tronco; a copa só existe para a câmera (o herói passa por baixo dela)
                shapes.add(Shape.circle(p.name(), c.x, c.z, num(pr, "raio", 0.3f), y0, y1, true, cam));
                float cr = num(pr, "copa", 0.42f) * Math.min(w, d);
                if (cr > 0) shapes.add(Shape.circle(p.name() + ":copa", c.x, c.z, cr, y0 + h * num(pr, "copaDe", 0.35f), y1, false, true));
            }
            default -> throw new IllegalArgumentException("tipo de colisão desconhecido em " + p.name() + ": " + kind);
        }
    }

    /** Personagem ou criatura parada na cena: círculo (perfil "corpos"). */
    public void addBody(JsonObject prof, String name, float x, float z) {
        JsonObject bodies = prof.getAsJsonObject("corpos");
        float r = bodies.has(name) ? bodies.get(name).getAsFloat() : bodies.get("padrao").getAsFloat();
        if (r <= 0) return;
        float y = ground.height(x, z);
        shapes.add(Shape.circle(name, x, z, r, y, y + 1.3f, true, false));
    }

    /** Monta a colisão da cena: perfis + props + corpos + limite. */
    public static Collision build(Animator.Ground ground, JsonObject prof, List<World.Placement> props, List<World.Body> bodies,
                                  SceneDef scene) {
        Collision c = new Collision(ground);
        for (World.Placement p : props) c.addProp(prof, p);
        for (World.Body b : bodies) c.addBody(prof, b.name(), b.x(), b.z());
        for (SceneDef.Interactive i : scene.interactives) c.addBody(prof, i.rig(), i.x(), i.z());
        c.limitX = scene.limitX;
        c.limitZ = scene.limitZ;
        c.limitR = scene.limitR;
        return c;
    }

    // ------------------------------------------------------------------ consultas

    /** Chão pisável: dentro do limite e fora da água. */
    public boolean walkable(float x, float z) {
        float dx = x - limitX, dz = z - limitZ;
        if (dx * dx + dz * dz > limitR * limitR) return false;
        return ground.height(x, z) >= waterLevel + 0.06f;           // a faixa rente à água (−0,375 na E) já é margem molhada
    }

    /**
     * Tira o círculo de todas as formas de corpo que ele toca na altura [y, y+h] (formas cujo topo fica até
     * {@code step} acima dos pés são pisáveis e não bloqueiam). Retorna o deslocamento aplicado.
     */
    public float[] resolve(float[] p, float rad, float y, float h, float step) {
        float tx = 0, tz = 0;
        for (int it = 0; it < 4; it++) {
            boolean any = false;
            for (Shape s : shapes) {
                if (!s.body || s.y1 <= y + step || s.y0 >= y + h) continue;
                float[] d = s.push(p[0], p[1], rad);
                if (d == null) continue;
                p[0] += d[0];
                p[1] += d[1];
                tx += d[0];
                tz += d[1];
                any = true;
            }
            if (!any) break;
        }
        return new float[]{tx, tz};
    }

    /**
     * Linha de visão da câmera de {@code from} até {@code to}: fração livre (0–1). Bloqueiam o relevo (com
     * folga) e as formas marcadas para a câmera (paredes, troncos, copas), infladas por {@code margin}.
     */
    public float rayClear(Vector3f from, Vector3f to, float margin) {
        final int n = 48;
        float lastFree = 0;
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n;
            if (blocked(from, to, t, margin)) {
                float lo = lastFree, hi = t;                              // refina a borda
                for (int k = 0; k < 6; k++) {
                    float mid = (lo + hi) / 2;
                    if (blocked(from, to, mid, margin)) hi = mid; else lo = mid;
                }
                return lo;
            }
            lastFree = t;
        }
        return 1f;
    }

    /** O que corta a linha de visão (depuração e relatório): "relevo", nome da forma ou null. */
    public String blocker(Vector3f from, Vector3f to, float margin) {
        for (int i = 1; i <= 48; i++) {
            float t = i / 48f, x = from.x + (to.x - from.x) * t, y = from.y + (to.y - from.y) * t, z = from.z + (to.z - from.z) * t;
            if (y < ground.height(x, z) + 0.22f) return String.format("relevo em %.0f%%", t * 100);
            for (Shape s : shapes) if (s.camera && s.contains(x, y, z, margin)) return String.format("%s em %.0f%%", s.name, t * 100);
        }
        return null;
    }

    private boolean blocked(Vector3f a, Vector3f b, float t, float m) {
        float x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t, z = a.z + (b.z - a.z) * t;
        if (y < ground.height(x, z) + 0.22f) return true;
        for (Shape s : shapes) if (s.camera && s.contains(x, y, z, m)) return true;
        return false;
    }

    /** Formas de um prop (para testes e depuração). */
    public List<Shape> named(String prefix) {
        List<Shape> out = new ArrayList<>();
        for (Shape s : shapes) if (s.name.startsWith(prefix)) out.add(s);
        return out;
    }
}
