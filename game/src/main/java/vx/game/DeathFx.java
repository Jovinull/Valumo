package vx.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.viewer.Animator;

/**
 * Rodada 09.2 — morte por desagregação voxel (ADR 0022): ao morrer, a criatura reage ao golpe por {@link #BREAK} s e então
 * se desfaz nos PRÓPRIOS blocos ({@link VoxelCloud}): cada fragmento nasce exatamente onde estava na pose do último quadro,
 * com a cor do modelo, solta-se numa onda que parte do ponto do golpe, recebe um impulso pequeno (para fora e para cima),
 * cai, quica pouco, perde energia e some encolhendo — o corpo nunca é esticado nem achatado inteiro.
 *
 * <p>Só visual: nada aqui colide com a jogabilidade. Lógica pura (sem OpenGL), determinística (semente = inimigo + passo
 * da morte), com quantidade máxima ({@link #MAX}) e duração fixa; termina sozinha ({@link #done}).
 *
 * <ul>
 *   <li><b>D1 — fragmentos</b>: só os blocos.</li>
 *   <li><b>D2 — dissolução fantástica</b>: os blocos clareiam um pouco antes de sumir e poucas sementes de luz (motivo
 *       P1, emissivas, no máximo {@link #SEEDS}) sobem devagar do corpo.</li>
 * </ul>
 */
public final class DeathFx {
    public enum Style { D1, D2 }

    /** Reação ao golpe antes de se desfazer (s) e vida total do efeito (s). */
    public static final float BREAK = 0.14f, LIFE = 1.35f;
    public static final int MAX = 150, SEEDS = 7;
    static final float GRAVITY = 9.5f, STEP = 1f / 120f;

    public final Style style;
    public final int n, seeds;
    public final float[] x, y, z, vx, vy, vz, ax, ay, az, spin, ang, size, delay;
    public final int[] rgb;
    /** Sementes de luz (D2): posição, velocidade de subida, fase. */
    public final float[] sx, sy, sz, sv, sph;
    public float t;
    /** Medição: ns gastos em update e passos. */
    public long nanos;
    private final float floor;
    private float acc;

    /**
     * @param cloud blocos do rig
     * @param mats  matrizes de mundo das partes na pose do último quadro (Actor/compose)
     * @param hitX  ponto de onde veio o golpe (a onda parte daí), hitZ idem
     * @param ground chão (pode ser null: plano em y do inimigo)
     */
    public DeathFx(VoxelCloud cloud, Matrix4f[] mats, float scale, float cx, float cy, float cz, float hitX, float hitZ, long seed,
                   Style style, Animator.Ground ground) {
        this.style = style;
        int total = cloud.chunks.size();
        int keep = Math.min(MAX, total);
        n = keep;
        x = new float[n]; y = new float[n]; z = new float[n]; vx = new float[n]; vy = new float[n]; vz = new float[n];
        ax = new float[n]; ay = new float[n]; az = new float[n]; spin = new float[n]; ang = new float[n]; size = new float[n];
        delay = new float[n]; rgb = new int[n];
        floor = ground != null ? ground.height(cx, cz) : cy;
        java.util.Random r = new java.util.Random(seed);
        // amostra uniforme e determinística quando há mais blocos que o máximo (passo fixo pela lista)
        float stepK = (float) total / keep;
        Vector3f tmp = new Vector3f();
        float hdx = cx - hitX, hdz = cz - hitZ, hl = Math.max(1e-4f, (float) Math.hypot(hdx, hdz));
        hdx /= hl;
        hdz /= hl;
        for (int i = 0; i < n; i++) {
            VoxelCloud.Chunk c = cloud.chunks.get(Math.min(total - 1, (int) (i * stepK)));
            mats[c.part()].transformPosition(tmp.set(c.x(), c.y(), c.z()));
            x[i] = tmp.x; y[i] = tmp.y; z[i] = tmp.z;
            size[i] = c.size() * scale;
            rgb[i] = c.rgb();
            y[i] = Math.max(y[i], floor + size[i] * 0.5f);              // a pose de reação afunda o pé ~2 cm: o bloco nasce no chão
            float ox = x[i] - cx, oz = z[i] - cz, ol = (float) Math.hypot(ox, oz);
            float ux = ol > 1e-4f ? ox / ol : (float) Math.cos(i), uz = ol > 1e-4f ? oz / ol : (float) Math.sin(i);
            float out = (0.55f + 0.9f * r.nextFloat()) * (float) Math.sqrt(scale);
            float push = 0.55f * (0.5f + r.nextFloat());                    // empurrão do golpe: para longe de quem bateu
            vx[i] = ux * out + hdx * push + (r.nextFloat() - 0.5f) * 0.4f;
            vz[i] = uz * out + hdz * push + (r.nextFloat() - 0.5f) * 0.4f;
            vy[i] = (0.9f + 1.5f * r.nextFloat() + 0.8f * Math.max(0f, (y[i] - floor) / Math.max(0.2f, 0.8f * scale))) * (float) Math.sqrt(scale);
            float a1 = r.nextFloat() - 0.5f, a2 = r.nextFloat() - 0.5f, a3 = r.nextFloat() - 0.5f, al = (float) Math.sqrt(a1 * a1 + a2 * a2 + a3 * a3) + 1e-4f;
            ax[i] = a1 / al; ay[i] = a2 / al; az[i] = a3 / al;
            spin[i] = (3f + 8f * r.nextFloat()) * (r.nextBoolean() ? 1 : -1);
            // a onda: solta primeiro o que está do lado do golpe, depois o resto (até 0,22 s)
            float side = ((x[i] - hitX) * -hdx + (z[i] - hitZ) * -hdz);
            delay[i] = Math.clamp(0.11f + side * 0.35f, 0f, 0.22f) + r.nextFloat() * 0.04f;
        }
        seeds = style == Style.D2 ? SEEDS : 0;
        sx = new float[seeds]; sy = new float[seeds]; sz = new float[seeds]; sv = new float[seeds]; sph = new float[seeds];
        for (int i = 0; i < seeds; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = 0.12f * scale + 0.1f * r.nextFloat();
            sx[i] = cx + (float) Math.cos(a) * rr;
            sz[i] = cz + (float) Math.sin(a) * rr;
            sy[i] = cy + (0.25f + 0.35f * r.nextFloat()) * scale;
            sv[i] = 0.35f + 0.3f * r.nextFloat();
            sph[i] = r.nextFloat() * 6.28f;
        }
    }

    public boolean done() { return t >= LIFE; }

    /** Avança dt segundos em passos fixos (independente da taxa de quadros). */
    public void update(float dt) {
        long t0 = System.nanoTime();
        acc += dt;
        while (acc >= STEP - 1e-7f && !done()) {
            acc -= STEP;
            step(STEP);
        }
        nanos += System.nanoTime() - t0;
    }

    private void step(float h) {
        t += h;
        for (int i = 0; i < n; i++) {
            if (t < delay[i]) continue;                                    // ainda preso ao corpo (onda)
            vy[i] -= GRAVITY * h;
            x[i] += vx[i] * h;
            y[i] += vy[i] * h;
            z[i] += vz[i] * h;
            ang[i] += spin[i] * h;
            float bottom = floor + size[i] * HALF_DIAG * scaleAt(i);       // cubo girado: o canto mais baixo, não a face
            if (y[i] < bottom) {                                           // quica pouco e escorrega até parar
                y[i] = bottom;
                if (vy[i] < 0f) vy[i] = -vy[i] * 0.28f;
                vx[i] *= 0.62f;
                vz[i] *= 0.62f;
                spin[i] *= 0.6f;
            }
        }
        for (int i = 0; i < seeds; i++) { sy[i] += sv[i] * h; sv[i] *= 1f - 0.4f * h; }
    }

    /** Metade da diagonal do cubo de lado 1 (√3/2): o canto de um cubo girado nunca desce mais que isso do centro. */
    static final float HALF_DIAG = 0.8661f;

    /** Ponto mais baixo possível do fragmento i (preso ao corpo: alinhado; solto: girando). */
    public float lowest(int i) { return y[i] - size[i] * scaleAt(i) * (t < delay[i] ? 0.5f : HALF_DIAG); }

    /** Escala do fragmento i agora (1 → 0 na parte final da vida: some encolhendo, cada um no seu tempo). */
    public float scaleAt(int i) {
        float end = LIFE - 0.05f, start = 0.55f * LIFE + delay[i];
        if (t <= start) return 1f;
        float u = Math.clamp((t - start) / (end - start), 0f, 1f);
        return 1f - u * u * (3 - 2 * u);
    }

    /** Clareamento do fragmento (D2: até 35% para o creme antes de sumir; D1: 0). */
    public float lighten(int i) {
        if (style != Style.D2) return 0f;
        float u = Math.clamp((t - 0.45f * LIFE) / (0.4f * LIFE), 0f, 1f);
        return 0.35f * u;
    }

    /** Brilho da semente de luz i (0–1): acende rápido, apaga devagar. */
    public float seedGlow(int i) {
        float on = Math.clamp((t - BREAK * 0.5f) / 0.15f, 0f, 1f), off = 1f - Math.clamp((t - 0.6f * LIFE) / (0.4f * LIFE), 0f, 1f);
        return on * off * (0.75f + 0.25f * (float) Math.sin(t * 9f + sph[i]));
    }

    /** Fragmentos vivos (com tamanho visível). */
    public int visible() {
        int k = 0;
        for (int i = 0; i < n; i++) if (scaleAt(i) > 0.01f) k++;
        return k;
    }
}
