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

    /**
     * Rodada 09.2 — números do perfil de sensação (padrão = rodada 08/09.1). {@code stride}: escala da passada; a fase
     * avança pela distância dividida pela passada, então o pé de apoio continua plantado em qualquer velocidade.
     */
    public float walk = WALK, run = RUN, accel = ACCEL, decel = DECEL, turnRate = TURN_RATE, stride = 1f, airControl = 0.45f;
    /** Rodada 09.2 — salto: velocidade inicial (u/s), gravidades de subida e queda, coyote, buffer e amortecimento (s). */
    public float jumpV, gravUp = GRAVITY, gravDown = GRAVITY, coyote, jumpBuffer, landTime = 0.16f;
    /** Rodada 09.2 — estado do salto: no ar por salto, desde quando saiu do chão, aperto guardado, amortecimento. */
    public boolean jumping;
    public float airT, jumpQueued = -1f, landT = 99f, landImpact;
    /** Rodada 09.2 — saltos dados e pousos (testes e som). */
    public int jumps, landings;

    public void applyFeel(CombatDef.Feel f) {
        walk = f.walk(); run = f.run(); accel = f.accel(); decel = f.decel(); turnRate = f.turn(); stride = f.stride(); airControl = f.air();
    }

    public void applyJump(CombatDef d) {
        if (d.jumpHeight <= 0f) return;
        gravUp = d.gravityUp;
        gravDown = d.gravityDown;
        jumpV = (float) Math.sqrt(2 * d.gravityUp * d.jumpHeight);
        coyote = d.coyote;
        jumpBuffer = d.jumpBuffer;
        landTime = d.landTime;
    }

    /** Rodada 12.1 — o mais alto que o herói chega acima do relevo: o salto (pés) mais o degrau que se pega no ar. */
    public float reach() { return (jumpV > 0f ? jumpV * jumpV / (2f * gravUp) : 0f) + STEP; }

    /** Pede um salto (sai no próximo passo em que puder: no chão ou até {@link #coyote} s depois de sair dele). */
    public void requestJump() { if (jumpV > 0f) jumpQueued = jumpBuffer; }

    /** No ar (para o desenho e as regras): 0–1 de pés recolhidos. */
    public float airWeight() { return grounded ? 0f : Math.min(1f, airT / 0.08f); }

    /** Amortecimento do pouso (0–1). Rodada 09.3: a duração depende do impacto e de estar correndo ({@link #landDur}). */
    public float landWeight() {
        if (landT >= landDur) return 0f;
        float u = landT / landDur;
        return landImpact * (float) Math.sin(Math.PI * Math.min(1f, u * 1.4f)) * (1f - u * 0.3f);
    }

    /**
     * Rodada 09.3 — apresentação do salto (só desenho; a física não muda): fase do impulso nos primeiros
     * {@link #TAKEOFF} s no ar (−1 fora dele) e preparação do pouso (0 → 1 nos últimos {@link #FALL_PREP} s antes do chão).
     */
    public static final float TAKEOFF = 0.10f, FALL_PREP = 0.14f;

    public float takeoffPhase() { return jumping && !grounded && airT < TAKEOFF ? airT / TAKEOFF : -1f; }

    public float fallPrep() {
        if (grounded || vy >= 0f) return 0f;
        float t = (y - lastFloor) / Math.max(0.3f, -vy);                 // segundos até o chão na velocidade atual
        return Math.clamp(1f - t / FALL_PREP, 0f, 1f);
    }

    /** Pouso correndo (0–1): absorção mais curta e mais baixa, a corrida segue. */
    public float landRunning() { return Math.clamp((landSpeed - walk) / Math.max(0.1f, run - walk), 0f, 1f); }

    /** Duração do amortecimento deste pouso, velocidade no pouso e chão sob o corpo no último passo. */
    public float landDur = 0.16f, landSpeed, lastFloor;

    public float x, z, y, yaw, vx, vz, vy, visualY;
    public boolean grounded = true;
    /** Bloqueado neste quadro (encostou em algo ou num desnível). */
    public boolean blocked;
    public float traveled;

    // estado de animação
    public Animator.Clip clip = Animator.Clip.IDLE, fromClip;
    public float animT, fade = 1f;

    // rodada 09.4 — pisadas: contador que sobe quando um pé começa o apoio (mesma fase que planta o pé na
    // animação: frac(t·cadência) ou frac(t·cadência + ½) volta a 0). O som sai disso, então coincide com o pé.
    public int steps;
    public boolean stepRun;
    private long stepPhase = Long.MIN_VALUE;
    /** Rodada 09: retrato do passo lógico anterior (o desenho interpola entre ele e o atual). */
    public float px, pz, pVisualY, pYaw, pAnimT, pFade;
    public Animator.Clip pClip = Animator.Clip.IDLE, pFromClip;

    public void snapshot() {
        px = x; pz = z; pVisualY = visualY; pYaw = yaw; pAnimT = animT; pFade = fade; pClip = clip; pFromClip = fromClip;
    }

    /** Posição de desenho entre o passo anterior e o atual (a = 0..1). */
    public float rx(float a) { return px + (x - px) * a; }
    public float rz(float a) { return pz + (z - pz) * a; }
    public float rVisualY(float a) { return pVisualY + (visualY - pVisualY) * a; }
    public float rYaw(float a) { return pYaw + (float) Math.atan2(Math.sin(yaw - pYaw), Math.cos(yaw - pYaw)) * a; }

    public void place(float x, float z, float yaw, Collision c) {
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        y = visualY = c.standAt(x, z, reach(), RADIUS, HEIGHT);
        vx = vz = vy = 0;
        grounded = true;
        jumping = false;
        jumpQueued = -1f;
        airT = 0f;
        landT = 99f;
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
        float max = run ? this.run : walk;
        drive(dt, ix * max, iz * max, len > 0.01f, Float.NaN, 0f, c);
    }

    /**
     * Rodada 09 — o mesmo corpo com velocidade-alvo (u/s, mundo) e, se {@code faceYaw} não for NaN, rumo controlado
     * por quem chama (golpe, guarda) com giro até {@code turnRate} rad/s em vez de seguir o deslocamento.
     */
    public void drive(float dt, float tvx, float tvz, boolean moving, float faceYaw, float turnRate, Collision c) {
        blocked = false;
        steered = !Float.isNaN(faceYaw);
        float t = dt;
        while (t > 1e-6f) {
            float h = Math.min(SUBSTEP, t);
            t -= h;
            step(h, tvx, tvz, moving, c);
            if (!Float.isNaN(faceYaw)) turnTo(faceYaw, turnRate * h);
        }
        animate(dt);
        visualY += (y - visualY) * (1f - (float) Math.exp(-dt * 22f));
        if (!grounded || Math.abs(y - visualY) > 0.6f || landT < landDur) visualY = y;
    }

    /** Aceleração forte (esquiva: a velocidade segue a curva do gesto quase exatamente). 0 = a do perfil. */
    public float accelOverride;

    private void step(float h, float tx, float tz, boolean moving, Collision c) {
        // aceleração até a velocidade desejada; frenagem mais forte quando solta ou inverte
        float dx = tx - vx, dz = tz - vz, d = (float) Math.hypot(dx, dz);
        float a = accelOverride > 0f ? accelOverride : moving && (tx * vx + tz * vz) >= 0 ? accel : decel;
        if (!grounded && accelOverride <= 0f) a *= airControl;              // rodada 09.2: no ar, controle parcial
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
        // contato com o chão (rodada 09.2: relevo ou topo de prop pisável; salto com coyote e buffer)
        float g = c.floor(x, z, y, RADIUS);
        lastFloor = g;
        landT += h;
        if (jumpQueued >= 0f) {
            boolean canJump = grounded || (!jumping && airT <= coyote);
            if (canJump) {
                vy = jumpV;
                grounded = false;
                jumping = true;
                airT = 0f;
                jumps++;
                jumpQueued = -1f;
            } else jumpQueued -= h;
        }
        if (grounded) {
            if (g >= y - SNAP_DOWN) y = g;
            else { grounded = false; vy = 0; airT = 0f; }
        }
        if (!grounded) {
            airT += h;
            vy -= (vy > 0f ? gravUp : gravDown) * h;
            y += vy * h;
            if (y <= g) {
                landImpact = Math.clamp(-vy / Math.max(1f, jumpV > 0f ? jumpV * 1.2f : 6f), 0.25f, 1f);
                if (airT > 0.12f) {
                    landT = 0f;
                    landings++;
                    landSpeed = (float) Math.hypot(vx, vz);
                    // rodada 09.3: queda mais forte = absorção mais longa; correndo = mais curta (não interrompe a corrida)
                    landDur = landTime * (0.75f + 0.5f * landImpact) * (1f - 0.35f * landRunning());
                }
                y = g;
                vy = 0;
                grounded = true;
                jumping = false;
            }
        }
        // rumo segue o deslocamento (giro com velocidade angular limitada), salvo quando o combate controla o rumo
        float sp = (float) Math.hypot(vx, vz);
        if (sp > 0.05f && !steered) {
            float want = (float) Math.atan2(vx, vz);
            float diff = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
            float maxTurn = TURN_RATE * h;
            yaw += Math.clamp(diff, -maxTurn, maxTurn);
            yaw = (float) Math.atan2(Math.sin(yaw), Math.cos(yaw));       // rumo sempre em (−π, π]
        }
    }

    /** O combate controla o rumo neste passo (ligado/desligado pelo {@link Hero}). */
    public boolean steered;

    /** Gira até {@code maxTurn} rad na direção de {@code want}. */
    public void turnTo(float want, float maxTurn) {
        float diff = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
        yaw += Math.clamp(diff, -maxTurn, maxTurn);
        yaw = (float) Math.atan2(Math.sin(yaw), Math.cos(yaw));
    }

    /** Empurrão (recuo de golpe, ricochete): soma à velocidade; a frenagem normal o consome. */
    public void push(float dvx, float dvz) { vx += dvx; vz += dvz; }

    private boolean stepAllowed(float nx, float nz, Collision c) {
        if (!c.walkable(nx, nz)) return false;
        float g = c.floor(nx, nz, y, RADIUS);
        return g - y <= STEP;                                            // no ar também: degrau alto é parede, não teleporte
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

    /** Velocidade de desenho do clipe (passada 1): a fase avança por distância / (isto × passada). */
    static float clipSpeed(Animator.Clip c) { return c == Animator.Clip.RUN ? RUN : WALK; }

    private void animate(float dt) {
        float s = speed();
        Animator.Clip want = clip;
        float wk = walk;
        switch (clip) {                                                   // histerese: sem pisca-pisca nas fronteiras
            case IDLE -> want = s > 0.12f ? (s > wk * 1.5f ? Animator.Clip.RUN : Animator.Clip.WALK) : Animator.Clip.IDLE;
            case WALK -> want = s < 0.06f ? Animator.Clip.IDLE : s > wk * 1.5f ? Animator.Clip.RUN : Animator.Clip.WALK;
            case RUN -> want = s < 0.06f ? Animator.Clip.IDLE : s < wk * 1.25f ? Animator.Clip.WALK : Animator.Clip.RUN;
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
        animT += clip == Animator.Clip.IDLE ? dt : dt * Math.max(0.15f, s / (clipSpeed(clip) * stride));
        if ((clip == Animator.Clip.WALK || clip == Animator.Clip.RUN) && grounded && s > 0.12f) {
            long k = (long) Math.floor(animT * cadence(clip) * 2);
            if (stepPhase != Long.MIN_VALUE && k != stepPhase) {
                steps++;
                stepRun = clip == Animator.Clip.RUN;
            }
            stepPhase = k;
        } else {
            stepPhase = Long.MIN_VALUE;                                   // parado ou no ar: a próxima pisada recomeça a contagem
        }
    }

    /** Motion para o Animator (locomoção com troca suave; rodada 09.2: passada, salto e pouso). */
    public Animator.Motion motion(Animator.Motion out) {
        out.set(clip, animT);
        if (fade < 1f && fromClip != null) {
            out.fromClip = fromClip;
            out.locoW = fade;
        }
        out.stride = stride;
        out.air = airWeight();
        out.land = landWeight();
        out.takeoff = takeoffPhase();
        out.fall = fallPrep();
        out.landRun = landRunning();
        return out;
    }
}
