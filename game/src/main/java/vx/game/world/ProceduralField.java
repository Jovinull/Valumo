package vx.game.world;

import vx.viewer.TerrainField;
import vx.viewer.World;

/**
 * Rodada 11 — relevo de uma região gerada: o mesmo vocabulário do relevo da vila (ruído suave em duas escalas, encostas
 * que fecham a região, patamar onde há construção, bacia do lago), com os PARÂMETROS da semente e os lugares do
 * {@link RegionLayout}. A trilha é aplainada até uma rampa entre o patamar do assentamento e o da clareira — por isso
 * ela é transitável por construção (a validação confere com a colisão real).
 */
public final class ProceduralField implements TerrainField {
    private final RegionLayout L;
    private final String id;
    private final float[] cum;                                           // comprimento acumulado da trilha
    private final float total;
    private final float cx, cz, forestR;

    public ProceduralField(RegionLayout layout) {
        this.L = layout;
        this.id = "proc." + Long.toUnsignedString(layout.seed) + ".v" + layout.version + "." + layout.attempt;
        cum = new float[layout.path.size()];
        float t = 0;
        for (int i = 1; i < layout.path.size(); i++) {
            float[] a = layout.path.get(i - 1), b = layout.path.get(i);
            t += (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            cum[i] = t;
        }
        total = Math.max(t, 1e-3f);
        cx = (layout.sx + layout.px) / 2;
        cz = (layout.sz + layout.pz) / 2;
        float half = (float) Math.hypot(layout.px - layout.sx, layout.pz - layout.sz) / 2;
        forestR = half + Math.max(layout.plateauR, layout.clearingR) + 4.5f;
    }

    public String id() { return id; }

    static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** Altura natural (antes dos patamares e da trilha): ruído com deslocamento e amplitude da semente + encostas. */
    float natural(float x, float z) {
        float n = noise(x * 0.11f + L.noiseX, z * 0.11f + L.noiseZ) * 1.1f + noise(x * 0.31f + L.noiseX * 2 + 7, z * 0.31f + L.noiseZ * 2 + 3) * 0.3f;
        float h = n * L.amplitude;
        // vales rasos: sem lago, o chão nunca desce ao nível da água (a água só existe na bacia do lago)
        h = 0.05f + (h > 0 ? h : h * 0.15f);
        h += smooth(13f, 22f, -z) * L.rimN + smooth(13f, 22f, z) * L.rimS + smooth(13f, 22f, x) * L.rimE + smooth(13f, 22f, -x) * L.rimW;
        return h;
    }

    public float height(float x, float z, boolean carvePath) {
        float h = natural(x, z);
        float ds = (float) Math.hypot(x - L.sx, z - L.sz);
        h += (L.plateauH - h) * smooth(L.plateauR + 4.2f, L.plateauR + 1.0f, ds);
        float dc = (float) Math.hypot(x - L.px, z - L.pz);
        h += (L.clearingH - h) * smooth(L.clearingR + 3.0f, L.clearingR + 0.3f, dc);
        float[] pr = project(x, z);
        float target = L.plateauH + (L.clearingH - L.plateauH) * smooth(0f, 1f, pr[1]);
        h += (target - h) * smooth(2.8f, 1.1f, pr[0]);
        if (L.hill != null) {
            float dh = (float) Math.hypot(x - L.hill[0], z - L.hill[1]);
            h += L.hill[3] * smooth(L.hill[2], L.hill[2] * 0.25f, dh);
        }
        if (L.pond != null) {
            float dp = pondDistance(x, z);
            h += (-1.25f - h) * smooth(4.6f, 1.6f, dp);
        }
        return h;
    }

    /** {distância ao eixo, fração do comprimento (0 no assentamento, 1 na clareira)}. */
    float[] project(float x, float z) {
        float best = Float.MAX_VALUE, at = 0;
        for (int i = 0; i + 1 < L.path.size(); i++) {
            float[] a = L.path.get(i), b = L.path.get(i + 1);
            float dx = b[0] - a[0], dz = b[1] - a[1], ll = dx * dx + dz * dz;
            float t = ll < 1e-9f ? 0 : Math.clamp(((x - a[0]) * dx + (z - a[1]) * dz) / ll, 0f, 1f);
            float qx = a[0] + dx * t - x, qz = a[1] + dz * t - z, d = (float) Math.sqrt(qx * qx + qz * qz);
            if (d < best) { best = d; at = (cum[i] + (cum[i + 1] - cum[i]) * t) / total; }
        }
        return new float[]{best, at};
    }

    public float pathDistance(float x, float z) { return L.path.size() < 2 ? 99f : project(x, z)[0]; }

    public float pondDistance(float x, float z) {
        if (L.pond == null) return 99f;
        float dx = x - L.pond[0], dz = (z - L.pond[1]) * 1.2f;
        return (float) Math.sqrt(dx * dx + dz * dz) + noise(x * 0.6f, z * 0.6f) * 0.35f;
    }

    public float[] pondCenter() { return L.pond == null ? null : new float[]{L.pond[0], World.WATER_LEVEL, L.pond[1]}; }

    /** A floresta começa fora da área jogável: medida do meio entre o assentamento e a clareira, 4 u além da mais longe. */
    @Override public float forestRadius() { return forestR; }

    @Override public float[] center() { return new float[]{cx, cz}; }

    // ruído de valor suave (o mesmo da vila, aqui para não depender de um método privado do viewer)
    static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    static float noise(float x, float z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), d = hash(xi + 1, zi + 1);
        return (a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz) * 2 - 1;
    }
}
