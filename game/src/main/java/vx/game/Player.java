package vx.game;

import vx.viewer.Animator;

/**
 * Rodada 08 — corpo do personagem controlável (lógica, sem OpenGL).
 *
 * <ul>
 *   <li>Velocidade com aceleração e frenagem (u/s²), em subpassos fixos de no máximo 1/120 s: o percurso não
 *       depende da taxa de quadros.</li>
 *   <li>Colisão: círculo de raio {@link #RADIUS} empurrado para fora das formas; a velocidade perde só a
 *       componente contra a parede (desliza ao longo dela).</li>
 *   <li>Chão: sobe degraus até {@link #STEP}; desnível maior bloqueia; descida até {@link #SNAP_DOWN} acompanha o
 *       chão; mais que isso, cai com gravidade. Água e fora do limite não são pisáveis.</li>
 *   <li>Animação: o clipe (parado/andar/correr) sai da velocidade REAL e a fase avança pela distância
 *       percorrida, então o pé de apoio fica parado no chão em qualquer velocidade. A altura visual suaviza os
 *       degraus de 1 voxel; o IK dos pés (visual) pisa no mesmo relevo.</li>
 * </ul>
 */
public final class Player {
    public static final float WALK = Animator.WALK_SPEED, RUN = Animator.RUN_SPEED;
    public static final float RADIUS = 0.26f, HEIGHT = 1.2f, STEP = 0.30f, SNAP_DOWN = 0.40f, GRAVITY = 18f;
    public static final float ACCEL = 9f, DECEL = 11f, TURN_RATE = 11f, SUBSTEP = 1f / 120f;
    static final float LOCO_FADE = 0.2f;

    public float x, z, y, yaw, vx, vz, vy, visualY;
    public boolean grounded = true;
    /** Bloqueado neste quadro (encostou em algo ou num desnível). */
    public boolean blocked;
    public float traveled;

    // estado de animação
    public Animator.Clip clip = Animator.Clip.IDLE, fromClip;
    public float animT, fade = 1f;

    public void place(float x, float z, float yaw, Collision c) {
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        y = visualY = c.ground.height(x, z);
        vx = vz = vy = 0;
        grounded = true;
    }

    public float speed() { return (float) Math.hypot(vx, vz); }

    /**
     * Avança dt segundos com a intenção (ix, iz) em coordenadas de mundo (comprimento 0–1) e correr ligado ou
     * não.
     */
    public void update(float dt, float ix, float iz, boolean run, Collision c) {
        blocked = false;
        float len = (float) Math.hypot(ix, iz);
        if (len > 1) { ix /= len; iz /= len; len = 1; }
        float max = run ? RUN : WALK;
        float t = dt;
        while (t > 1e-6f) {
            float h = Math.min(SUBSTEP, t);
            t -= h;
            step(h, ix * max, iz * max, len > 0.01f, c);
        }
        animate(dt);
        visualY += (y - visualY) * (1f - (float) Math.exp(-dt * 22f));
        if (!grounded || Math.abs(y - visualY) > 0.6f) visualY = y;
    }

    private void step(float h, float tx, float tz, boolean moving, Collision c) {
        // aceleração até a velocidade desejada; frenagem mais forte quando solta ou inverte
        float dx = tx - vx, dz = tz - vz, d = (float) Math.hypot(dx, dz);
        float a = moving && (tx * vx + tz * vz) >= 0 ? ACCEL : DECEL;
        if (d > 1e-6f) {
            float k = Math.min(1f, a * h / d);
            vx += dx * k;
            vz += dz * k;
        }
        float ox = x, oz = z;
        float[] p = {x + vx * h, z + vz * h};
        float[] push = c.resolve(p, RADIUS, y, HEIGHT, STEP);
        if (push[0] != 0 || push[1] != 0) {
            float pl = (float) Math.hypot(push[0], push[1]);
            float nx = push[0] / pl, nz = push[1] / pl, vn = vx * nx + vz * nz;
            if (vn < 0) { vx -= nx * vn; vz -= nz * vn; }
            blocked = true;
        }
        if (!stepAllowed(p[0], p[1], c)) {
            // desnível ou água: tenta deslizar por um eixo; senão fica
            blocked = true;
            if (stepAllowed(p[0], oz, c) && free(p[0], oz, c)) { p[1] = oz; vz = 0; }
            else if (stepAllowed(ox, p[1], c) && free(ox, p[1], c)) { p[0] = ox; vx = 0; }
            else { p[0] = ox; p[1] = oz; vx = vz = 0; }
        }
        traveled += (float) Math.hypot(p[0] - x, p[1] - z);
        x = p[0];
        z = p[1];
        // contato com o chão
        float g = c.ground.height(x, z);
        if (grounded) {
            if (g >= y - SNAP_DOWN) y = g;
            else { grounded = false; vy = 0; }
        }
        if (!grounded) {
            vy -= GRAVITY * h;
            y += vy * h;
            if (y <= g) { y = g; vy = 0; grounded = true; }
        }
        // rumo segue o deslocamento (giro com velocidade angular limitada)
        float sp = (float) Math.hypot(vx, vz);
        if (sp > 0.05f) {
            float want = (float) Math.atan2(vx, vz);
            float diff = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
            float maxTurn = TURN_RATE * h;
            yaw += Math.clamp(diff, -maxTurn, maxTurn);
            yaw = (float) Math.atan2(Math.sin(yaw), Math.cos(yaw));       // rumo sempre em (−π, π]
        }
    }

    private boolean stepAllowed(float nx, float nz, Collision c) {
        if (!c.walkable(nx, nz)) return false;
        float g = c.ground.height(nx, nz);
        return !grounded || g - y <= STEP;
    }

    private boolean free(float nx, float nz, Collision c) {
        float[] p = {nx, nz};
        float[] push = c.resolve(p, RADIUS, y, HEIGHT, STEP);
        return Math.abs(push[0]) + Math.abs(push[1]) < 1e-4f;
    }

    /** Vira-se para (wx, wz) suavemente (diálogo). */
    public void face(float wx, float wz, float dt) {
        float want = (float) Math.atan2(wx - x, wz - z);
        float diff = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
        yaw += diff * (1f - (float) Math.exp(-dt * 8f));
    }

    // ------------------------------------------------------------------ animação pela velocidade real

    static float cadence(Animator.Clip c) {
        return c == Animator.Clip.RUN ? Animator.RUN_CADENCE : c == Animator.Clip.WALK ? Animator.WALK_CADENCE : 1f;
    }

    static float clipSpeed(Animator.Clip c) { return c == Animator.Clip.RUN ? RUN : WALK; }

    private void animate(float dt) {
        float s = speed();
        Animator.Clip want = clip;
        switch (clip) {                                                   // histerese: sem pisca-pisca nas fronteiras
            case IDLE -> want = s > 0.12f ? (s > WALK * 1.5f ? Animator.Clip.RUN : Animator.Clip.WALK) : Animator.Clip.IDLE;
            case WALK -> want = s < 0.06f ? Animator.Clip.IDLE : s > WALK * 1.5f ? Animator.Clip.RUN : Animator.Clip.WALK;
            case RUN -> want = s < 0.06f ? Animator.Clip.IDLE : s < WALK * 1.25f ? Animator.Clip.WALK : Animator.Clip.RUN;
            default -> want = Animator.Clip.IDLE;
        }
        if (want != clip) {
            if ((clip == Animator.Clip.WALK || clip == Animator.Clip.RUN) && (want == Animator.Clip.WALK || want == Animator.Clip.RUN))
                animT = animT * cadence(clip) / cadence(want);           // mesma fase do passo no clipe novo
            fromClip = clip;
            clip = want;
            fade = 0;
        }
        fade = Math.min(1f, fade + dt / LOCO_FADE);
        // fase pela distância: a cadência escala com a velocidade, a passada fica igual → pé plantado
        animT += clip == Animator.Clip.IDLE ? dt : dt * Math.max(0.15f, s / clipSpeed(clip));
    }

    /** Motion para o Animator (locomoção com troca suave). */
    public Animator.Motion motion(Animator.Motion out) {
        out.set(clip, animT);
        if (fade < 1f && fromClip != null) {
            out.fromClip = fromClip;
            out.locoW = fade;
        }
        return out;
    }
}
