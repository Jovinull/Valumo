package vx.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.viewer.Animator;
import vx.viewer.CharacterModel;
import vx.viewer.Choreo;
import vx.viewer.WeaponCheck;

/**
 * Rodada 09 — o herói em combate (lógica, sem OpenGL): espada e broquel sobre o corpo do {@link Player}.
 *
 * <p>Estados: LIVRE (anda como na rodada 08) → GOLPE (corte horizontal, encadeia diagonal e descendente) · GUARDA
 * (botão direito segurado) · ESQUIVA (rodada 09.2: rolamento com direção ou recuo sem direção) · ATINGIDO (reação +
 * atordoamento) · CAÍDO.
 *
 * <p>O dano NÃO vem da colisão de movimento nem do estado visual de um quadro: vem da TRAJETÓRIA da lâmina. A cada passo
 * lógico, durante a janela de dano do golpe ({@code ativo} em combate_e.json, dentro da execução do perfil e só enquanto a
 * ponta varre a metade da frente), a pose é avaliada em subamostras de no
 * máximo 1/480 s (a mesma resolução do validador de armas) e a cápsula da lâmina — eixo +Y da peça {@code weapon}, do
 * punho à ponta — é testada contra a hurtbox de CADA inimigo vivo (rodada 09.2); o rastro da ponta entre subamostras
 * também conta. Um golpe atinge cada alvo no máximo uma vez, e só se nada sólido estiver entre o peito do herói e o ponto
 * de contato. Dois inimigos na trajetória = dois acertos legítimos; ninguém fora dela perde vida.
 *
 * <p>Rodada 09.2 — mobilidade ({@link CombatDef.Feel}): no golpe o jogador continua controlando o deslocamento, com um
 * teto de velocidade por fase (preparação, execução, continuação, recuperação) e um giro por fase (na execução pouco: a
 * trajetória fica). As PERNAS seguem a passada real por baixo do golpe ({@link #legW}): tronco, braços e espada continuam
 * exatamente a pose validada.
 */
public final class Hero {
    public enum State { FREE, ATTACK, BLOCK, HIT, DODGE, DEAD }
    public enum Outcome { IGNORED, HURT, BLOCKED, KILLED, DODGED }

    /**
     * Intenção de um passo: velocidade desejada (mundo), aperto de ataque, guarda segurada, rumo da câmera. Rodada 09.2:
     * correr, esquiva e salto apertados, alvo travado (posição) ou nenhum.
     */
    public static final class Intent {
        public float vx, vz, camYaw;
        public boolean moving, attack, block;
        public boolean run, dodge, jump;
        /** Alvo travado (null = mira livre). */
        public Enemy target;

        public Intent set(float vx, float vz, boolean moving, boolean attack, boolean block, float camYaw) {
            this.vx = vx; this.vz = vz; this.moving = moving; this.attack = attack; this.block = block; this.camYaw = camYaw;
            run = dodge = jump = false;
            target = null;
            return this;
        }
    }

    static final float SUB = 1f / 480f;
    /**
     * Guarda do jogo: golpe "guard_hold" do perfil. Esperas e pulos só em platôs parados da curva (pose E movimento
     * secundário iguais): segura em GUARD_UP, impacto de GUARD_IMPACT a GUARD_IMPACT_END (segura ali), baixa de GUARD_DOWN.
     */
    static final String GUARD = "guard_hold";
    static final float GUARD_UP = 0.40f, GUARD_IMPACT = 0.50f, GUARD_IMPACT_END = 1.00f, GUARD_DOWN = 1.20f;
    /** Quanto antes do fim da reação a derrota assume (corte validado em CombatAnimationTest). */
    static final float DEFEAT_LEAD = 0.06f;
    /** Rodada 09.2 — taxa (1/s) com que as pernas passam da base do golpe à passada (e o rumo da passada acompanha). */
    static final float LEG_RATE = 14f, DIR_RATE = 12f;
    /** Rodada 09.2 — esquiva: aceleração que faz a velocidade seguir a curva do gesto (u/s²). */
    static final float DODGE_ACCEL = 90f;

    final CombatDef def;
    final Choreo prof;
    final CharacterModel skel;
    final Animator anim;
    final ActionLayer layer, shown;
    private final Animator.Pose pose;
    private final Matrix4f[] mats;
    private final int blade;
    private final Animator.Motion mo = new Animator.Motion();

    public State state = State.FREE;
    public int hp;
    public float invuln, stun, stateT, buffer = -1f, deadT;
    public CombatDef.Attack attack;
    public boolean chainQueued, guardImpact;
    /** Identidade do golpe atual (um alvo só é atingido uma vez por golpe). */
    public int swing;
    public float aimYaw, lungeV;
    /** Últimas cápsulas da lâmina avaliadas (depuração: só na fase ativa). */
    public final java.util.List<float[]> debugBlade = new java.util.ArrayList<>();
    /** Tempo desde o último dano (feedback). */
    public float hurtT = 99f;

    // rodada 09.2
    /** Perfil de sensação em uso (o GameLogic troca; o padrão do construtor é A, a 09.1). */
    public CombatDef.Feel feel;
    /** Esquiva atual, direção (mundo), recarga, aperto guardado. */
    public CombatDef.Dodge dodge;
    public float dodgeX, dodgeZ, dodgeYaw, dodgeCooldown, dodgeBuffer = -1f;
    /** Esquivas feitas, ataques evitados pela janela de invulnerabilidade (testes, relatório, som). */
    public int dodges, evaded;
    /** Clique dado no ar: sai ao pousar. */
    boolean airAttack;
    /** Pernas na passada por baixo do golpe (0–1) e rumo da passada em relação ao corpo (rad), com o passo anterior. */
    public float legW, locoDir, pLegW, pLocoDir;

    Hero(CombatDef def, CharacterModel skel) {
        this.def = def;
        this.skel = skel;
        this.anim = new Animator(skel, 1f);
        this.prof = anim.prof;
        if (prof == null || !prof.name.equals(def.heroProfile))
            throw new IllegalStateException("o kit " + def.heroKit + " não usa o perfil " + def.heroProfile);
        layer = new ActionLayer(prof);
        shown = new ActionLayer(prof);
        pose = anim.newPose();
        mats = new Matrix4f[skel.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        blade = skel.index.get(def.bladeItem);
        feel = def.feels.containsKey("A") ? def.feels.get("A") : def.feels.values().iterator().next();
        reset();
    }

    void reset() {
        state = State.FREE;
        hp = def.heroHp;
        invuln = stun = stateT = deadT = 0f;
        buffer = -1f;
        dodgeBuffer = -1f;
        dodgeCooldown = 0f;
        dodge = null;
        airAttack = false;
        attack = null;
        chainQueued = guardImpact = false;
        hurtT = 99f;
        legW = pLegW = locoDir = pLocoDir = 0f;
        layer.copyFrom(new ActionLayer(prof));
        shown.copyFrom(layer);
    }

    public boolean alive() { return state != State.DEAD; }

    /** Guarda erguida a ponto de bloquear (subida do escudo concluída). */
    public boolean guardUp() { return state == State.BLOCK && layer.move() != null && layer.moveT() >= def.shieldRaise; }

    /** Rodada 09.2 — dentro da janela de invulnerabilidade da esquiva (estado lógico, não quadros). */
    public boolean evading() {
        if (state != State.DODGE || dodge == null || dodgeGesture == null || !dodgeGesture.equals(layer.move())) return false;
        float t = layer.moveT();
        return t >= dodge.invFrom() && t <= dodge.invTo();
    }

    /** Fase do golpe atual no instante t: 0 prep, 1 exec, 2 follow, 3 recover. */
    static int phase(Choreo.Move mv, float t) {
        float[] ex = mv.phases.get("exec"), fo = mv.phases.get("follow");
        if (ex == null) return 3;
        if (t < ex[0]) return 0;
        if (t < ex[1]) return 1;
        if (fo != null && t < fo[1]) return 2;
        return 3;
    }

    static String phaseName(int i) { return switch (i) { case 0 -> "prep"; case 1 -> "exec"; case 2 -> "follow"; default -> "recover"; }; }

    // ------------------------------------------------------------------ passo lógico

    void tick(float h, Intent in, Player p, Combat c) {
        shown.copyFrom(layer);
        pLegW = legW;
        pLocoDir = locoDir;
        invuln = Math.max(0f, invuln - h);
        dodgeCooldown = Math.max(0f, dodgeCooldown - h);
        stateT += h;
        hurtT += h;
        p.accelOverride = 0f;
        if (in.attack) {
            if (p.grounded) buffer = def.attackBuffer;
            else airAttack = true;                                        // no ar: sai ao pousar
        } else if (!layer.fadingOut()) buffer -= h;                       // espera a saída da pose anterior (ver FREE)
        if (airAttack && p.grounded) { airAttack = false; buffer = def.attackBuffer; }
        if (in.dodge && def.roll != null) dodgeBuffer = def.dodgeBuffer;
        else if (!layer.fadingOut()) dodgeBuffer -= h;
        debugBlade.clear();

        switch (state) {
            case FREE -> {
                if (layer.busy() && in.moving) layer.release();         // cauda de reação/guarda: andar libera
                if (in.jump && !layer.busy()) p.requestJump();             // o Player guarda o aperto (buffer) e aceita o coyote
                if (layer.fadingOut()) break;                            // uma ação nova esperaria a pose sair (sem salto)
                if (canDodge(p)) startDodge(in, p, c);
                else if (!p.grounded) { }                                 // no ar: nada de golpe nem guarda
                else if (in.block) startBlock();
                else if (buffer > 0f) startAttack(def.attacks.get(def.firstAttack), in, p, c);
            }
            case ATTACK -> {
                float t = layer.moveT();
                if (canDodge(p) && def.dodgeAllowed(layer.move(), t) && layer.canCut()) {   // janelas medidas pelo validador
                    startDodge(in, p, c);
                    break;
                }
                if (buffer > 0f && attack.next() != null && !chainQueued && t >= attack.acceptFrom() && t <= attack.acceptTo()) {
                    chainQueued = true;
                    buffer = -1f;
                }
                if (chainQueued && t >= attack.chainAt()) startAttack(def.attacks.get(attack.next()), in, p, c);
                else if (!chainQueued && t >= attack.release()) {
                    if (buffer > 0f) startAttack(def.attacks.get(def.firstAttack), in, p, c);   // recomeça a sequência
                    else if (in.block) startBlock();
                    else if (in.moving) { layer.release(); state = State.FREE; }
                }
                if (state == State.ATTACK && !layer.busy()) state = State.FREE;
            }
            case BLOCK -> {
                if (guardImpact && layer.moveT() >= GUARD_IMPACT_END) guardImpact = false;
                if (!guardImpact && canDodge(p) && layer.canCut()) { startDodge(in, p, c); break; }
                if (!in.block && !guardImpact) {
                    layer.seek(GUARD_DOWN);                             // baixa a guarda pela curva autorada
                    state = State.FREE;
                    stateT = 0f;
                }
            }
            case DODGE -> {
                float t = layer.moveT();
                boolean same = layer.busy() && dodgeGesture.equals(layer.move());
                if (!same) { endDodge(); break; }
                if (buffer > 0f && t >= dodge.attackFrom() && layer.canCut()) { endDodge(); startAttack(def.attacks.get(def.firstAttack), in, p, c); }
                else if (t >= dodge.releaseFrom() && in.block && layer.canCut()) { endDodge(); startBlock(); }
                else if (t >= dodge.releaseFrom() && in.moving) { layer.release(); endDodge(); }
            }
            case HIT -> {
                stun -= h;
                if (stun <= 0f) { state = State.FREE; stateT = 0f; }
            }
            case DEAD -> {
                deadT += h;
                // a reação (ou a saída autorada) termina no repouso, onde a derrota começa: troca logo antes do fim
                if (!"defeat".equals(layer.move()) && (!layer.busy() || layer.moveT() >= layer.cur.full - DEFEAT_LEAD)) {
                    layer.start("defeat");
                    layer.holdAt = prof.moves.get("defeat").duration();
                }
            }
        }

        // corpo: velocidade e rumo por estado
        float bx = p.x, bz = p.z, by = p.y, byaw = p.yaw;
        Enemy tg = in.target != null && in.target.alive() ? in.target : null;
        switch (state) {
            case FREE -> {
                if (tg != null && !in.run && p.grounded)                  // mira travada: anda de frente para o alvo (de lado, de costas)
                    p.drive(h, in.vx, in.vz, in.moving, (float) Math.atan2(tg.x - p.x, tg.z - p.z), p.turnRate, c.collision);
                else p.drive(h, in.vx, in.vz, in.moving, Float.NaN, 0f, c.collision);
            }
            case ATTACK -> {
                float t = layer.moveT();
                Choreo.Move mv = prof.moves.get(layer.move());
                float[] ex = mv.phases.get("exec");
                int ph = phase(mv, t);
                boolean lunging = ex != null && t >= ex[0] && t < ex[1];
                float fx = (float) Math.sin(p.yaw), fz = (float) Math.cos(p.yaw);
                // deslocamento do jogador: teto da fase (fração da velocidade de andar do perfil); A = 0 (rodada 09.1)
                float cap = feel.move()[ph] * feel.walk(), ivx = 0f, ivz = 0f;
                float il = (float) Math.hypot(in.vx, in.vz);
                if (cap > 0f && in.moving && il > 1e-4f) { float k = Math.min(il, cap) / il; ivx = in.vx * k; ivz = in.vz * k; }
                if (lunging && lungeV == 0f) {                          // avanço: impulso no começo da execução
                    lungeV = lungeSpeed(attack.lunge(), ex[1] - ex[0], p.decel);
                    p.vx = fx * lungeV + ivx;
                    p.vz = fz * lungeV + ivz;
                }
                if (tg != null) aimYaw = (float) Math.atan2(tg.x - p.x, tg.z - p.z);   // travado: o golpe acompanha o alvo
                float face, turn = feel.turnAtk()[ph];
                if (ph == 0 || ph == 1 || tg != null) face = aimYaw;
                else if (in.moving && il > 1e-4f) face = (float) Math.atan2(in.vx, in.vz);   // continuação/recuperação: vira para onde anda
                else face = p.yaw;
                if (feel.name().equals("A") && ph > 0) face = p.yaw;      // rodada 09.1: mira só na preparação
                p.drive(h, (lunging ? fx * lungeV : 0f) + ivx, (lunging ? fz * lungeV : 0f) + ivz, lunging || (ivx != 0f || ivz != 0f),
                        face, turn, c.collision);
            }
            case BLOCK -> p.drive(h, in.vx * def.shieldSpeed, in.vz * def.shieldSpeed, in.moving,
                    tg != null ? (float) Math.atan2(tg.x - p.x, tg.z - p.z) : in.camYaw, def.shieldTurn, c.collision);
            case DODGE -> {
                float t = layer.moveT();
                float v = dodgeSpeed(dodge, t);
                p.accelOverride = t <= dodge.moveTo() + 0.06f ? DODGE_ACCEL : 0f;
                Enemy tg2 = in.target != null && in.target.alive() ? in.target : null;
                float face = dodgeTurns ? dodgeYaw : tg2 != null && dodge != def.backstep ? (float) Math.atan2(tg2.x - p.x, tg2.z - p.z) : p.yaw;
                p.drive(h, dodgeX * v, dodgeZ * v, v > 0.05f, face, dodgeTurns ? dodge.turn() : tg2 != null ? 6f : 0f, c.collision);
            }
            default -> p.drive(h, 0f, 0f, false, p.yaw, 0f, c.collision);
        }
        updateLegs(h, p);

        // golpe: trajetória da lâmina na fase ativa (antes de avançar a camada: subamostras em (0, h])
        if (state == State.ATTACK) { long s0 = System.nanoTime(); sweep(h, bx, bz, by, byaw, p, c); c.hitNanos += System.nanoTime() - s0; }
        layer.update(h);
    }

    /** Rodada 09.2 — pernas: passada por baixo de golpe/guarda/reação quando o corpo anda; o rolamento manda nas pernas. */
    private void updateLegs(float h, Player p) {
        float sp = p.speed();
        float want = state == State.DODGE || state == State.DEAD ? 0f : Math.clamp((sp - 0.12f) / 0.45f, 0f, 1f);
        legW += (want - legW) * (1f - (float) Math.exp(-h * LEG_RATE));
        if (sp > 0.08f) {
            float c = (float) Math.cos(p.yaw), s = (float) Math.sin(p.yaw);
            float lx = p.vx * c - p.vz * s, lz = p.vx * s + p.vz * c;        // +x local = esquerda do personagem
            float d = (float) Math.atan2(lx, lz);
            locoDir += angle(d - locoDir) * (1f - (float) Math.exp(-h * DIR_RATE));
            locoDir = angle(locoDir);
        } else locoDir += -locoDir * (1f - (float) Math.exp(-h * DIR_RATE));
    }

    /** Velocidade inicial do avanço para percorrer {@code dist} u: mantém na execução (dur) e freia depois. */
    static float lungeSpeed(float dist, float dur) { return lungeSpeed(dist, dur, Player.DECEL); }

    static float lungeSpeed(float dist, float dur, float decel) {
        float d = decel;
        return d * (-dur + (float) Math.sqrt(dur * dur + 2f * dist / d));
    }

    /**
     * Rodada 09.2 — velocidade da esquiva no instante t do gesto: sobe em 15% da janela, fica e desce nos últimos 30%
     * (cosseno). O pico sai da distância: a área da curva é a distância pedida.
     */
    static float dodgeSpeed(CombatDef.Dodge d, float t) {
        float w = d.moveTo() - d.moveFrom();
        if (t < d.moveFrom() || t > d.moveTo() || w <= 0f) return 0f;
        float u = (t - d.moveFrom()) / w, peak = d.distance() / (w * (1f - 0.15f / 2f - 0.30f / 2f));
        if (u < 0.15f) return peak * 0.5f * (1f - (float) Math.cos(Math.PI * u / 0.15f));
        if (u > 0.70f) return peak * 0.5f * (1f + (float) Math.cos(Math.PI * (u - 0.70f) / 0.30f));
        return peak;
    }

    /** Pode esquivar agora (no chão, sem recarga, com aperto guardado, perfil com esquiva). */
    private boolean canDodge(Player p) {
        return dodgeBuffer > 0f && dodgeCooldown <= 0f && p.grounded && def.roll != null;
    }

    /** Rodada 09.3 — gesto em uso na esquiva atual (o do estilo para a direção) e se o corpo vira para o rumo dela. */
    public String dodgeGesture;
    public boolean dodgeTurns;

    void startDodge(Intent in, Player p, Combat c) {
        boolean dir = in.moving && Math.hypot(in.vx, in.vz) > 1e-3f;
        dodge = dir ? def.roll : def.backstep;
        if (dir) {
            float l = (float) Math.hypot(in.vx, in.vz);
            dodgeX = in.vx / l;
            dodgeZ = in.vz / l;
        } else {                                                          // sem direção: passo evasivo para trás, de frente
            dodgeX = -(float) Math.sin(p.yaw);
            dodgeZ = -(float) Math.cos(p.yaw);
        }
        dodgeYaw = (float) Math.atan2(dodgeX, dodgeZ);
        // rodada 09.3: com a mira travada e o alvo vivo, esquiva de lado SEM virar o corpo (gesto lateral do R2); para a
        // frente, para trás e na mira livre, o corpo vira para o rumo e usa o gesto de "rolar" (dodge_f)
        dodgeGesture = dodge.move();
        dodgeTurns = dodge.turn() > 0f;
        if (dir) {
            Enemy tg = in.target != null && in.target.alive() ? in.target : null;
            if (tg != null) {
                float rel = angle(dodgeYaw - (float) Math.atan2(tg.x - p.x, tg.z - p.z));   // + = para a esquerda do herói
                String side = Math.abs(rel) <= Math.toRadians(45) || Math.abs(rel) >= Math.toRadians(135) ? null : rel > 0 ? "esquerda" : "direita";
                String g = side == null ? null : def.dodgeSide(side);
                if (g != null) { dodgeGesture = g; dodgeTurns = false; }
            }
        }
        layer.start(dodgeGesture);
        state = State.DODGE;
        stateT = 0f;
        attack = null;
        chainQueued = false;
        guardImpact = false;
        dodgeBuffer = -1f;
        dodges++;
        c.log(dir ? "esquiva: " + dodgeGesture : "esquiva: recuo");
    }

    private void endDodge() {
        if (state == State.DODGE) state = State.FREE;
        dodgeCooldown = def.dodgeCooldown;
        stateT = 0f;
    }

    void startAttack(CombatDef.Attack a, Intent in, Player p, Combat c) {
        attack = a;
        String used = layer.start(a.move());
        if (!used.equals(a.move())) throw new IllegalStateException("golpe trocado por saída: " + used);
        state = State.ATTACK;
        stateT = 0f;
        chainQueued = false;
        buffer = -1f;
        swing++;
        lungeV = 0f;
        Enemy tg = in.target != null && in.target.alive() ? in.target : null;
        if (tg != null) { aimYaw = (float) Math.atan2(tg.x - p.x, tg.z - p.z); return; }   // travado: para o alvo
        // mira livre: para onde o jogador empurra (senão para onde já olha); giro suave para o inimigo mais perto desse
        // rumo, se perto e dentro do ângulo da assistência
        aimYaw = in.moving ? (float) Math.atan2(in.vx, in.vz) : p.yaw;
        Enemy best = null;
        float bestScore = Float.MAX_VALUE;
        for (Enemy e : c.enemies) {
            if (!e.alive()) continue;
            float dx = e.x - p.x, dz = e.z - p.z, dist = (float) Math.hypot(dx, dz);
            float off = Math.abs(angle((float) Math.atan2(dx, dz) - aimYaw));
            if (dist > def.assistRange || off > def.assistAngle) continue;
            float score = off * 1.5f + dist;
            if (score < bestScore) { bestScore = score; best = e; }
        }
        if (best != null) aimYaw = (float) Math.atan2(best.x - p.x, best.z - p.z);
    }

    void startBlock() {
        layer.start(GUARD);
        layer.holdAt = GUARD_UP;
        state = State.BLOCK;
        stateT = 0f;
        guardImpact = false;
        attack = null;
    }

    // ------------------------------------------------------------------ geometria do golpe

    /** Cápsula da lâmina no tempo {@code ahead} do passo (mundo): {ax, ay, az, bx, by, bz}. */
    float[] bladeAt(float ahead, float rx, float ry, float rz, float ryaw, Collision col) { return bladeAt(ahead, rx, ry, rz, ryaw, col, null, 1f); }

    /**
     * Rodada 09.2 — com {@code p}: as pernas também entram como no desenho (passada por baixo do golpe), então até o
     * ajuste do pé ao relevo é o mesmo da tela (a lâmina não depende das pernas no plano; num degrau, um pouco).
     */
    float[] bladeAt(float ahead, float rx, float ry, float rz, float ryaw, Collision col, Player p, float frac) {
        mo.set(Animator.Clip.IDLE, 0f);
        if (p != null) legs(mo, p, frac);
        layer.eval(mo, ahead);
        Matrix4f root = new Matrix4f().translate(rx, ry, rz).rotateY(ryaw);
        anim.evaluate(mo, pose, root, col.ground);
        anim.compose(pose, root, mats);
        CharacterModel.Part w = skel.parts.get(blade);
        float vs = skel.voxelSize;
        Vector3f a = mats[blade].transformPosition(new Vector3f(w.px, w.py + def.bladeFrom * vs, w.pz));
        Vector3f b = mats[blade].transformPosition(new Vector3f(w.px, w.py + def.bladeTo * vs, w.pz));
        return new float[]{a.x, a.y, a.z, b.x, b.y, b.z};
    }

    private void sweep(float h, float bx, float bz, float by, float byaw, Player p, Combat c) {
        if (attack == null) return;
        float[] ex = {attack.activeFrom(), attack.activeTo()};              // janela de DANO autorada (dentro da execução)
        float t0 = layer.moveT();
        if (t0 + h < ex[0] || t0 > ex[1]) return;
        int n = Math.max(1, (int) Math.ceil(h / SUB));
        float[] last = null;
        for (int k = 1; k <= n; k++) {
            float ahead = h * k / n, t = t0 + ahead;
            if (t < ex[0] || t > ex[1]) { last = null; continue; }
            float f = ahead / h;
            float yaw = byaw + angle(p.yaw - byaw) * f;
            float[] s = bladeAt(ahead, bx + (p.x - bx) * f, by + (p.y - by) * f, bz + (p.z - bz) * f, yaw, c.collision, p, f);
            debugBlade.add(s);
            for (Enemy e : c.enemies) {                                  // rodada 09.2: cada inimigo, no máximo uma vez por golpe
                if (!e.alive() || e.struckBy == swing) continue;
                float[] hit = e.touch(s, def.bladeR);
                if (hit == null && last != null) hit = e.touch(new float[]{last[3], last[4], last[5], s[3], s[4], s[5]}, def.bladeR);   // rastro da ponta
                if (hit == null) continue;
                Vector3f chest = new Vector3f(p.x, p.y + 0.85f, p.z), at = new Vector3f(hit[0], hit[1], hit[2]);
                String wall = c.collision.solidBetween(chest, at);
                if (wall == null) {
                    e.struckBy = swing;
                    c.heroHits(this, e, attack.damage(), at);
                } else if (e.barredBy != swing) {
                    e.barredBy = swing;
                    c.log("golpe barrado por " + wall);
                }
            }
            last = s;
        }
    }

    // ------------------------------------------------------------------ dano recebido

    /** Ataque inimigo válido chegou ao herói vindo de (fx, fz). */
    Outcome receive(int damage, float fx, float fz, Player p, Combat c) {
        if (state == State.DEAD || invuln > 0f) return Outcome.IGNORED;
        if (evading()) { evaded++; return Outcome.DODGED; }             // rodada 09.2: janela da esquiva
        float toSrc = (float) Math.atan2(fx - p.x, fz - p.z);
        if (guardUp() && Math.abs(angle(toSrc - p.yaw)) <= def.shieldAngle) {
            guardImpact = true;
            layer.seek(GUARD_IMPACT);
            layer.holdAt = GUARD_IMPACT_END;
            float dx = p.x - fx, dz = p.z - fz, l = Math.max(1e-4f, (float) Math.hypot(dx, dz));
            p.push(dx / l * def.shieldPush, dz / l * def.shieldPush);
            return Outcome.BLOCKED;
        }
        hp = Math.max(0, hp - damage);
        invuln = def.heroInvuln;
        hurtT = 0f;
        float dx = p.x - fx, dz = p.z - fz, l = Math.max(1e-4f, (float) Math.hypot(dx, dz));
        p.push(dx / l * def.heroPush, dz / l * def.heroPush);
        attack = null;
        chainQueued = false;
        dodge = null;
        String used = layer.start("hit");                               // a saída autorada da fase entra aqui
        if (hp == 0) {
            state = State.DEAD;
            deadT = 0f;
            return Outcome.KILLED;
        }
        state = State.HIT;
        stun = Math.max(def.heroStun, def.exitStun.getOrDefault(used, 0f));
        stateT = 0f;
        return Outcome.HURT;
    }

    // ------------------------------------------------------------------ desenho

    /** Motion do desenho: locomoção do Player + camada de ação, interpolada no passo (a = 0..1). */
    public Animator.Motion motion(Animator.Motion out, Player p, float a) {
        out.set(p.clip, p.pAnimT + (p.animT - p.pAnimT) * a);
        if (p.fade < 1f && p.fromClip != null) { out.fromClip = p.fromClip; out.locoW = p.fade; }
        legs(out, p, a);
        return shown.eval(out, a * GameLogic.TICK);
    }

    /** Rodada 09.2 — passada, rumo, pernas por baixo do golpe, salto e pouso (mesmos valores no desenho e na lâmina). */
    void legs(Animator.Motion out, Player p, float a) {
        out.stride = p.stride;
        float dir = pLocoDir + angle(locoDir - pLocoDir) * a;
        out.locoDir = dir;
        out.legW = pLegW + (legW - pLegW) * a;
        out.legClip = p.clip == Animator.Clip.IDLE ? null : p.clip;
        out.legT = p.pAnimT + (p.animT - p.pAnimT) * a;
        out.legDir = dir;
        out.air = p.airWeight();
        out.land = p.landWeight();
        out.takeoff = p.takeoffPhase();
        out.fall = p.fallPrep();
        out.landRun = p.landRunning();
    }

    static float angle(float a) { return (float) Math.atan2(Math.sin(a), Math.cos(a)); }

    /** Distância segmento × segmento (a mesma função do validador de armas). */
    static float segDist(float[] s, Vector3f c0, Vector3f c1) {
        return WeaponCheck.segDist(new Vector3f(s[0], s[1], s[2]), new Vector3f(s[3], s[4], s[5]), c0, c1);
    }
}
