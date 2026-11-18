package vx.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.viewer.Animator;
import vx.viewer.CharacterModel;
import vx.viewer.WeaponCheck;

/**
 * Rodada 09 — o primeiro inimigo (fungo-cardo): máquina de estados pequena e legível, sem árvore de comportamento.
 *
 * <pre>
 *   REPOUSO ──vê/ouve──▶ ALERTA ──▶ PERSEGUE ──perto e de frente──▶ PREPARA ──▶ GOLPE ──▶ RECUPERA ─┐
 *      ▲                              │  ▲                                                          │
 *      └──── VOLTA ◀──perdeu/coleira──┘  └──────────────────────(vê, fora do alcance)──────────────────┘
 *   qualquer estado vivo ──dano──▶ ATINGIDO (salvo GOLPE: compromete; e imune logo depois de um atordoamento)
 *   vida 0 ──▶ MORTO
 * </pre>
 *
 * <ul>
 *   <li>Percepção: distância ≤ visão E dentro do campo (ou ≤ audição em qualquer direção) E linha de visão livre de
 *       sólidos ({@link Collision#solidBetween}). Sem ver por {@code perde} s, ou o herói fora da coleira → volta.</li>
 *   <li>Movimento: o mesmo relevo e as mesmas formas do herói (círculo empurrado, degrau, água, limite) e nunca sai da
 *       coleira em volta de casa. Anda para onde olha (giro limitado): não desliza de lado nem vira de estalo.</li>
 *   <li>Obstáculo: sem progresso por {@code preso} s, contorna para o lado livre por 0,8 s; preso de novo → desiste e
 *       volta. Não há busca de caminho geral nesta rodada (ver docs/architecture/combate.md).</li>
 *   <li>Ataque telegrafado: PREPARA (0,70 s, mira só nos primeiros 60%, depois trava) → GOLPE (cabeçada com avanço,
 *       fase ativa no meio) → RECUPERA (0,85 s parado: a janela do jogador). Bloqueado → ricochete (recuperação maior).</li>
 *   <li>O dano vem da hitbox presa ao chapéu, avaliada pela própria animação em subamostras de 1/480 s.</li>
 * </ul>
 */
public final class Enemy {
    public enum State { IDLE, ALERT, CHASE, WINDUP, ATTACK, RECOVER, HIT, DEAD, RETURN }

    /**
     * Rodada 09.2 — morte da rodada 09 (cai de costas e some encolhendo) só para comparar: {@code -Dvx.morte=antiga}. O
     * padrão é a desagregação voxel ({@link DeathFx}): o corpo reage ao golpe e se desfaz nos próprios blocos.
     */
    public static boolean oldDeath = "antiga".equals(System.getProperty("vx.morte"));

    static final float STEP = 0.30f, HEIGHT = 0.8f, SUB = 1f / 480f, ACCEL = 10f;

    final CombatDef def;
    final CharacterModel skel;
    final Animator anim;
    private final Animator.Pose pose;
    private final Matrix4f[] mats;
    private final int capPart;

    public State state = State.IDLE;
    public float x, z, y, yaw, vx, vz, stateT, lockYaw, seenAgo, stuckT, sideT, poiseUntil, cooldown, walkT, clock;
    public int hp, side, stuckTimes;
    public boolean bounced, struckThis;
    /** Golpe do herói que já atingiu este inimigo (um acerto por golpe). */
    public int struckBy = -1;
    /** Rodada 09.2 — golpe do herói já registrado como barrado por obstáculo (log uma vez por golpe). */
    public int barredBy = -1;
    /** Rodada 09.2 — identidade (ordem do encontro), escala do modelo, chefe, tempo desde a morte. */
    public final int id;
    public final float scale;
    public float deadT;
    /** Rodada 09.2 — quanto tempo seguido escondido atrás de sólido para a mira (Targeting). */
    public float hiddenT;
    public float hurtT = 99f;
    // retrato do passo anterior (desenho)
    public float px, pz, pY, pYaw, pStateT, pWalkT;
    public State pState = State.IDLE;
    /** Hitbox avaliada no último passo (depuração). */
    public final java.util.List<float[]> debugHit = new java.util.ArrayList<>();

    Enemy(CombatDef def, CharacterModel skel, Collision c) { this(def, skel, c, 0); }

    Enemy(CombatDef def, CharacterModel skel, Collision c, int id) {
        this.def = def;
        this.skel = skel;
        this.id = id;
        this.scale = def.enemyScale;
        anim = new Animator(skel, 1f);
        pose = anim.newPose();
        mats = new Matrix4f[skel.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        capPart = skel.index.get(def.hitPart);
        reset(c);
    }

    void reset(Collision c) {
        x = def.enemyX;
        z = def.enemyZ;
        y = c.ground.height(x, z);
        yaw = (float) Math.toRadians(def.enemyYawDeg);
        vx = vz = 0f;
        hp = def.enemyHp;
        state = State.IDLE;
        stateT = seenAgo = stuckT = sideT = cooldown = walkT = 0f;
        poiseUntil = -1f;
        stuckTimes = 0;
        bounced = struckThis = false;
        struckBy = barredBy = -1;
        hurtT = 99f;
        deadT = hiddenT = 0f;
        snapshot();
    }

    public String name() { return def.enemyName; }

    public boolean boss() { return def.enemyBoss; }

    void snapshot() { px = x; pz = z; pY = y; pYaw = yaw; pState = state; pStateT = stateT; pWalkT = walkT; }

    public boolean alive() { return state != State.DEAD; }

    /** Em combate (barra de vida visível, música/feedback): percebeu o herói e ainda não desistiu. */
    public boolean engaged() {
        return switch (state) { case ALERT, CHASE, WINDUP, ATTACK, RECOVER, HIT -> true; default -> false; };
    }

    float homeX() { return def.enemyX; }
    float homeZ() { return def.enemyZ; }

    private void to(State s) { state = s; stateT = 0f; }

    // ------------------------------------------------------------------ percepção

    /** Vê o herói agora (distância, campo, audição, linha de visão). */
    boolean sees(Player p, Collision c) {
        float dx = p.x - x, dz = p.z - z, d = (float) Math.hypot(dx, dz);
        if (d > def.sight) return false;
        boolean inFov = d <= def.hearing || Math.abs(Hero.angle((float) Math.atan2(dx, dz) - yaw)) <= def.fov / 2f;
        if (!inFov) return false;
        return c.solidBetween(new Vector3f(x, y + 0.5f, z), new Vector3f(p.x, p.y + 0.8f, p.z)) == null;
    }

    boolean heroInLeash(Player p) { return Math.hypot(p.x - homeX(), p.z - homeZ()) <= def.leash + 0.8f; }

    // ------------------------------------------------------------------ passo lógico

    void tick(float h, Hero hero, Player p, Combat c) {
        snapshot();
        stateT += h;
        clock += h;
        hurtT += h;
        debugHit.clear();
        if (state == State.DEAD) {                                       // rodada 09.2: morto não pensa, não anda, não ataca
            deadT += h;
            vx = vz = 0f;
            return;
        }
        boolean see = hero.alive() && sees(p, c.collision);
        seenAgo = see ? 0f : seenAgo + h;
        float dx = p.x - x, dz = p.z - z, dist = (float) Math.hypot(dx, dz), toHero = (float) Math.atan2(dx, dz);
        float tvx = 0f, tvz = 0f, turnTo = Float.NaN, turnRate = def.turn;
        switch (state) {
            case IDLE -> {
                turnTo = (float) Math.toRadians(def.enemyYawDeg) + 0.5f * (float) Math.sin(clock * 0.45f);   // olha em volta
                turnRate = 1.2f;
                if (see && heroInLeash(p)) to(State.ALERT);
            }
            case ALERT -> {
                turnTo = toHero;
                if (stateT >= def.alertTime) to(State.CHASE);
            }
            case CHASE -> {
                if (!hero.alive() || seenAgo > def.loseAfter || !heroInLeash(p)) { to(State.RETURN); break; }
                cooldown -= h;
                turnTo = toHero;
                float err = Math.abs(Hero.angle(toHero - yaw));
                boolean turn = c.hasTurn(this);                           // rodada 09.2: vez no grupo (os mais perto)
                if (dist <= def.range && err <= def.attackAngle && cooldown <= 0f && see && c.mayAttack(this)) { c.attackStarted(this); to(State.WINDUP); break; }
                // rodada 09.2: sem a vez, espera no anel em volta do herói; separação dos outros corpos do grupo
                float want = turn || c.def.ring <= 0f ? def.range * 0.85f : Math.max(def.range * 0.85f, c.def.ring * scale);
                float dirX = 0f, dirZ = 0f;
                if (dist > want && dist > 1e-4f) { dirX = dx / dist; dirZ = dz / dist; }
                float[] sep = c.separation(this);
                dirX += sep[0];
                dirZ += sep[1];
                if (sideT > 0f) {                                        // contornando: anda de lado do obstáculo
                    sideT -= h;
                    float sy = yaw + side * (float) Math.PI / 2;
                    tvx = (float) Math.sin(sy) * def.speed;
                    tvz = (float) Math.cos(sy) * def.speed;
                } else if (Math.hypot(dirX, dirZ) > 0.2f) {
                    if (sep[0] != 0f || sep[1] != 0f) turnTo = (float) Math.atan2(dirX, dirZ);
                    float k = Math.max(0f, (float) Math.cos(Hero.angle(turnTo - yaw)));   // anda para onde olha
                    float mag = Math.min(1f, (float) Math.hypot(dirX, dirZ));
                    tvx = (float) Math.sin(yaw) * def.speed * k * mag;
                    tvz = (float) Math.cos(yaw) * def.speed * k * mag;
                }
            }
            case WINDUP -> {
                if (stateT < def.windup * def.prepLock) { turnTo = toHero; turnRate = def.prepTurnEnemy; }
                else lockYaw = yaw;
                if (stateT >= def.windup) { lockYaw = yaw; struckThis = false; to(State.ATTACK); }
            }
            case ATTACK -> {
                float u = stateT / def.strike;
                tvx = (float) Math.sin(lockYaw) * def.lungeSpeed * (u < 0.8f ? 1f : 0f);
                tvz = (float) Math.cos(lockYaw) * def.lungeSpeed * (u < 0.8f ? 1f : 0f);
                if (stateT >= def.strike) { bounced = false; to(State.RECOVER); }
            }
            case RECOVER -> {
                float dur = bounced ? def.bounce : def.recover;
                if (stateT >= dur) {
                    cooldown = def.interval;
                    bounced = false;
                    to(hero.alive() && heroInLeash(p) ? State.CHASE : State.RETURN);
                }
            }
            case HIT -> {
                if (stateT >= def.enemyStun) {
                    poiseUntil = clock + def.poise;
                    cooldown = def.interval;                             // recompõe-se antes de atacar de novo
                    to(hero.alive() && heroInLeash(p) ? State.CHASE : State.RETURN);
                }
            }
            case RETURN -> {
                float hx = homeX() - x, hz = homeZ() - z, hd = (float) Math.hypot(hx, hz);
                if (see && heroInLeash(p) && hero.alive()) { to(State.ALERT); break; }
                if (hd < 0.15f) { hp = def.enemyHp; stuckTimes = 0; to(State.IDLE); break; }   // em casa: recupera a vida
                float want = (float) Math.atan2(hx, hz);
                turnTo = want;
                float k = Math.max(0f, (float) Math.cos(Hero.angle(want - yaw)));
                tvx = (float) Math.sin(yaw) * def.speed * 0.8f * k;
                tvz = (float) Math.cos(yaw) * def.speed * 0.8f * k;
            }
            case DEAD -> { }
        }
        if (!Float.isNaN(turnTo)) {
            float d = Hero.angle(turnTo - yaw);
            yaw = Hero.angle(yaw + Math.clamp(d, -turnRate * h, turnRate * h));
        }
        move(h, tvx, tvz, c.collision);
        if (state == State.CHASE || state == State.RETURN) checkStuck(h, tvx, tvz, c.collision);
        if (state == State.ATTACK) { long s0 = System.nanoTime(); strike(h, hero, p, c); c.hitNanos += System.nanoTime() - s0; }
    }

    /** Mesmo chão, formas e limite do herói; nunca sai da coleira. */
    void move(float h, float tvx, float tvz, Collision c) {
        float ax = tvx - vx, az = tvz - vz, d = (float) Math.hypot(ax, az);
        float acc = (state == State.ATTACK) ? 60f : (state == State.HIT ? Player.DECEL * 0.6f : ACCEL);
        if (d > 1e-6f) {
            float k = Math.min(1f, acc * h / d);
            vx += ax * k;
            vz += az * k;
        }
        float[] p = {x + vx * h, z + vz * h};
        c.resolve(p, def.enemyRadius, y, HEIGHT, STEP);
        if (!allowed(p[0], p[1], c)) {
            if (allowed(p[0], z, c)) { p[1] = z; vz = 0; }
            else if (allowed(x, p[1], c)) { p[0] = x; vx = 0; }
            else { p[0] = x; p[1] = z; vx = vz = 0; }
        }
        walkT += (float) Math.hypot(p[0] - x, p[1] - z);
        x = p[0];
        z = p[1];
        y = c.ground.height(x, z);
    }

    private boolean allowed(float nx, float nz, Collision c) {
        if (!c.walkable(nx, nz)) return false;
        if (Math.hypot(nx - homeX(), nz - homeZ()) > def.leash) return false;
        return Math.abs(c.ground.height(nx, nz) - y) <= STEP;
    }

    private void checkStuck(float h, float tvx, float tvz, Collision c) {
        boolean wants = Math.hypot(tvx, tvz) > 0.3f;
        if (wants && Math.hypot(vx, vz) < 0.15f) stuckT += h; else stuckT = Math.max(0f, stuckT - h);
        if (stuckT < def.stuckAfter) return;
        stuckT = 0f;
        if (++stuckTimes > 2) { to(State.RETURN); stuckTimes = 0; sideT = 0f; return; }
        // lado livre: o que tem mais espaço 0,6 u à frente-lado
        float best = -1;
        for (int s : new int[]{1, -1}) {
            float sy = yaw + s * (float) Math.PI / 2, nx = x + (float) Math.sin(sy) * 0.6f, nz = z + (float) Math.cos(sy) * 0.6f;
            float[] q = {nx, nz};
            float[] push = c.resolve(q, def.enemyRadius, y, HEIGHT, STEP);
            float free = allowed(nx, nz, c) ? 1f - (float) Math.hypot(push[0], push[1]) : -1f;
            if (free > best) { best = free; side = s; }
        }
        sideT = 0.8f;
    }

    // ------------------------------------------------------------------ golpe e dano

    /** Esfera da hitbox no instante (mundo): {x, y, z} a partir da pose da animação. */
    float[] hitboxAt(float stateTime, float rx, float ry, float rz, float ryaw, Collision c) {
        Animator.Motion mo = new Animator.Motion().set(Animator.Clip.ATTACK, 1f + Math.min(stateTime / def.strike, 0.999f));
        Matrix4f root = new Matrix4f().translate(rx, ry, rz).rotateY(ryaw).scale(scale);
        anim.evaluate(mo, pose, root, c.ground);
        anim.compose(pose, root, mats);
        CharacterModel.Part cp = skel.parts.get(capPart);
        float vs = skel.voxelSize;
        Vector3f w = mats[capPart].transformPosition(new Vector3f(cp.px + def.hitX * vs, cp.py + def.hitY * vs, cp.pz + def.hitZ * vs));
        return new float[]{w.x, w.y, w.z};
    }

    private void strike(float h, Hero hero, Player p, Combat c) {
        if (struckThis) return;
        float t0 = stateT - h;
        int n = Math.max(1, (int) Math.ceil(h / SUB));
        for (int k = 1; k <= n; k++) {
            float f = (float) k / n, st = t0 + h * f, u = st / def.strike;
            if (u < def.activeFrom || u > def.activeTo) continue;
            float[] s = hitboxAt(st, px + (x - px) * f, pY + (y - pY) * f, pz + (z - pz) * f, yaw, c.collision);
            debugHit.add(s);
            Vector3f hb = new Vector3f(s[0], s[1], s[2]);
            float d = WeaponCheck.segDist(hb, null, new Vector3f(p.x, p.y + hero.def.heroHurt.from(), p.z),
                    new Vector3f(p.x, p.y + hero.def.heroHurt.to(), p.z));
            if (d > def.hitR + hero.def.heroHurt.r()) continue;
            if (c.collision.solidBetween(new Vector3f(x, y + 0.4f, z), hb) != null) continue;   // não ataca através de sólidos
            struckThis = true;
            c.enemyHits(this, hero, hb);
            return;
        }
    }

    /** Toque da cápsula (lâmina) na hurtbox: ponto de contato ou null. */
    float[] touch(float[] s, float r) {
        CombatDef.Hurtbox hb = def.enemyHurt;
        Vector3f c0 = new Vector3f(x, y + hb.from(), z), c1 = new Vector3f(x, y + hb.to(), z);
        Vector3f a = new Vector3f(s[0], s[1], s[2]), b = new Vector3f(s[3], s[4], s[5]);
        if (WeaponCheck.segDist(a, b, c0, c1) > r + hb.r()) return null;
        // ponto de contato aproximado: o ponto da lâmina mais perto do eixo da hurtbox
        Vector3f best = a;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i <= 8; i++) {
            Vector3f q = new Vector3f(a).lerp(b, i / 8f);
            float dd = WeaponCheck.segDist(q, null, c0, c1);
            if (dd < bd) { bd = dd; best = q; }
        }
        return new float[]{best.x, best.y, best.z};
    }

    /** Recebe um golpe válido. Retorna true se morreu. */
    boolean receive(int damage, float fx, float fz) {
        if (!alive()) return false;
        hp = Math.max(0, hp - damage);
        hurtT = 0f;
        float dx = x - fx, dz = z - fz, l = Math.max(1e-4f, (float) Math.hypot(dx, dz));
        if (hp == 0) {
            to(State.DEAD);
            deadT = 0f;
            vx = dx / l * def.enemyPush * 0.6f;
            vz = dz / l * def.enemyPush * 0.6f;
            return true;
        }
        boolean commits = state == State.ATTACK;                         // a cabeçada já saiu: não para
        boolean poised = clock < poiseUntil;                             // acabou de se recompor: não cambaleia de novo
        if (!commits && !poised) {
            to(State.HIT);
            vx = dx / l * def.enemyPush;
            vz = dz / l * def.enemyPush;
        }
        if (state == State.IDLE || state == State.RETURN) to(State.ALERT);   // golpe pelas costas: percebe
        return false;
    }

    /** Ataque bloqueado pelo escudo: ricochete (recuperação maior, recua). */
    void bounce(float fx, float fz) {
        float dx = x - fx, dz = z - fz, l = Math.max(1e-4f, (float) Math.hypot(dx, dz));
        bounced = true;
        to(State.RECOVER);
        vx = dx / l * def.enemyPush * 0.8f;
        vz = dz / l * def.enemyPush * 0.8f;
    }

    // ------------------------------------------------------------------ desenho

    /** Clipe e tempo da animação de um estado. */
    Animator.Motion motionOf(State s, float st, float wt, float clock, Animator.Motion out) {
        return switch (s) {
            case IDLE -> out.set(Animator.Clip.IDLE, clock);
            case ALERT -> out.set(Animator.Clip.EMOTE, Math.min(st, Animator.BRAWLER_ALERT));
            case CHASE, RETURN -> out.set(Animator.Clip.WALK, wt / Math.max(0.3f, def.speed) / scale);
            case WINDUP -> out.set(Animator.Clip.ATTACK, Math.min(0.999f, st / def.windup));
            case ATTACK -> out.set(Animator.Clip.ATTACK, 1f + Math.min(0.999f, st / def.strike));
            case RECOVER -> bounced && st < Animator.BRAWLER_HIT ? out.set(Animator.Clip.HIT, st)
                    : out.set(Animator.Clip.ATTACK, 2f + Math.min(0.999f, (st - (bounced ? Animator.BRAWLER_HIT : 0f)) / def.recover));
            case HIT -> out.set(Animator.Clip.HIT, st);
            case DEAD -> oldDeath ? out.set(Animator.Clip.DEATH, st) : out.set(Animator.Clip.HIT, Math.min(st, DeathFx.BREAK));
        };
    }

    /** Motion do desenho, interpolada entre o passo anterior e o atual. */
    public Animator.Motion motion(Animator.Motion out, float a) {
        if (pState == state) return motionOf(state, pStateT + (stateT - pStateT) * a, pWalkT + (walkT - pWalkT) * a, clock, out);
        return motionOf(state, stateT * a, walkT, clock, out);
    }

    public float rx(float a) { return px + (x - px) * a; }
    public float rz(float a) { return pz + (z - pz) * a; }
    public float rY(float a) { return pY + (y - pY) * a; }
    public float rYaw(float a) { return pYaw + Hero.angle(yaw - pYaw) * a; }
}
