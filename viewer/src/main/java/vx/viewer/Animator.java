package vx.viewer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Animação procedural por partes rígidas. Cada clipe é uma função do tempo que escreve rotações
 * (Euler XYZ, radianos), deslocamentos (em alturas do personagem) e escalas por parte; a pose final
 * é a composição hierárquica em torno dos pivôs. Poses são vetores de parâmetros, então a transição
 * entre clipes é uma interpolação linear ({@link Pose#lerp}).
 *
 * <p>Convenções: rotação X negativa leva um membro pendente para a frente (+Z);
 * rotação Z positiva abre o braço esquerdo (x>0) para fora.
 */
public final class Animator {
    /**
     * DEATH (rodada 09): queda/morte, hoje só na animação de combate ("brawler"); nos outros rigs vira HIT segurado.
     */
    public enum Clip {
        IDLE, WALK, RUN, ATTACK, HIT, EMOTE, DEATH;

        /** Os seis clipes das rodadas 01–08 (ferramentas de revisão e o ciclo do viewer não mudam). */
        public static final Clip[] BASIC = {IDLE, WALK, RUN, ATTACK, HIT, EMOTE};
    }

    /** Parâmetros de pose: por parte rx,ry,rz, ox,oy,oz, sx,sy,sz. Índice n = raiz. */
    public static final class Pose {
        final float[] v;
        final int n;

        Pose(int parts) {
            n = parts;
            v = new float[(parts + 1) * 9];
            reset();
        }

        void reset() {
            for (int i = 0; i <= n; i++) {
                int b = i * 9;
                for (int k = 0; k < 6; k++) v[b + k] = 0;
                v[b + 6] = v[b + 7] = v[b + 8] = 1;
            }
        }

        /** Mistura com outra pose; nas rotações usa a diferença angular mais curta (sem volta inteira em ±π). */
        public void lerp(Pose o, float t) {
            for (int i = 0; i < v.length; i++) {
                float d = o.v[i] - v[i];
                if (i % 9 < 3) d = (float) Math.IEEEremainder(d, Math.PI * 2);
                v[i] += d * t;
            }
        }
    }

    /** Altura do chão no mundo (para o IK de pé em terreno irregular). */
    public interface Ground { float height(float x, float z); }

    /**
     * Estado de animação num instante (rodada 04): clipe de base (locomoção) + camada de AÇÃO do perfil de
     * arma do rig. A ação pode vir de outra ação (encadeamento): o controle final é
     * lerp(transporte, lerp(ação anterior, ação, xfade), peso). Tudo é função do tempo: a mesma Motion dá a
     * mesma pose em 30 ou 60 FPS.
     */
    public static final class Motion {
        public Clip clip = Clip.IDLE;
        public float t;
        public String move, fromMove;
        public float moveT, fromT, weight, xfade = 1f;
        /**
         * Rodada 09: a origem da transição também estava em transição (ex.: encadeamento ainda misturando quando chega
         * um dano). A origem passa a ser lerp(fromMove2 em fromT2, fromMove em fromT, xfade2): contínua no instante do
         * segundo corte. null = origem simples.
         */
        public String fromMove2;
        public float fromT2, xfade2 = 1f;
        /** Troca de locomoção (parado ↔ andar ↔ correr): clipe anterior e peso do atual (0 → 1). */
        public Clip fromClip;
        public float locoW = 1f;

        /**
         * Rodada 09.2 — locomoção do jogo:
         * <ul>
         *   <li>{@code stride}: escala da passada (comprimento e altura do pé); o jogo avança a fase pela distância, então
         *       o pé de apoio continua plantado com a passada maior;</li>
         *   <li>{@code locoDir}: rumo do deslocamento em relação ao corpo (rad; 0 = para a frente, +π/2 = para a
         *       esquerda do personagem, π = de costas) — andar de lado e recuar encarando o alvo;</li>
         *   <li>{@code legW}, {@code legClip}, {@code legT}, {@code legDir}: durante um golpe, as PERNAS seguem a passada
         *       real (0 = base autorada do golpe, 1 = passada); tronco, braços, quadril e arma continuam exatamente os do
         *       golpe validado;</li>
         *   <li>{@code air}: no ar (0–1, pés recolhidos); {@code land}: impacto da aterrissagem (0–1, quadril cede).</li>
         * </ul>
         * Tudo 0/1 por padrão: o resultado é idêntico ao das rodadas anteriores.
         */
        public float stride = 1f, locoDir, legW, legT, legDir, air, land;
        /**
         * Rodada 09.3 — salto: {@code takeoff} = fase do impulso (0 → 1 nos primeiros 0,10 s no ar; −1 fora dele: joelhos
         * dobrados que estendem enquanto o corpo já sobe); {@code fall} = preparação do pouso (0 → 1 quando o chão chega:
         * pernas descem para o contato); {@code landRun} = pouso correndo (absorção mais curta e mais baixa).
         */
        public float takeoff = -1f, fall, landRun;
        public Clip legClip;

        public Motion set(Clip clip, float t) {
            this.clip = clip;
            this.t = t;
            move = fromMove = fromMove2 = null;
            weight = 0;
            xfade = xfade2 = 1f;
            fromClip = null;
            locoW = 1f;
            stride = 1f;
            locoDir = legW = legT = legDir = air = land = fall = landRun = 0f;
            takeoff = -1f;
            legClip = null;
            return this;
        }
    }

    /** Efeito visual emitido pela animação (sem jogabilidade): flecha em voo, orbe de magia, brilho. */
    public static final class Fx {
        public static final int ARROW = 0, ORB = 1, GLOW = 2;
        public final int kind;
        public final Matrix4f m = new Matrix4f();   // espaço da raiz; o Actor leva ao mundo
        public float size;

        Fx(int kind) { this.kind = kind; }
    }

    /** Rodada 04: true reproduz os ataques antigos (comparação antes × depois). */
    public static boolean legacy = false;

    // Passada do bípede articulado: velocidade de deslocamento = 2 · meia-passada · cadência / fração de apoio,
    // então o pé de apoio fica parado no chão quando o herói anda nessa velocidade (App.WALK_RUN usa estes valores).
    public static final float WALK_CADENCE = 2.2f, WALK_STANCE = 0.56f, WALK_HALF_STRIDE = 0.145f;
    public static final float RUN_CADENCE = 2.8f, RUN_STANCE = 0.36f, RUN_HALF_STRIDE = 0.15f;
    public static final float WALK_SPEED = 2 * WALK_HALF_STRIDE * WALK_CADENCE / WALK_STANCE;
    public static final float RUN_SPEED = 2 * RUN_HALF_STRIDE * RUN_CADENCE / RUN_STANCE;

    private final CharacterModel m;
    private Pose p;
    private Ground ground;
    private org.joml.Matrix4f world;
    private final float amp;   // exagero do estilo (1 = padrão)
    /** Perfil de arma (assets/anim/<meta.weaponProfile>.json); null = rig sem coreografia. */
    public final Choreo prof;
    private Motion motion = new Motion();
    /** Efeitos do último evaluate (espaço da raiz). */
    public final List<Fx> fx = new ArrayList<>();
    /** Diagnóstico do IK do último evaluate (voxels): quanto cada mão ficou longe do alvo. */
    public float reachErrR, reachErrL;

    /**
     * Voxel da COREOGRAFIA no espaço deste modelo: {@code voxelSize × meta.escala}. Um perfil de gesto (npc_vila,
     * sword…) é desenhado para o esqueleto de origem; um rig REAMOSTRADO (NPC de ofício com altura narrativa,
     * {@code meta.escala}) executa o mesmo gesto em proporção. Sem {@code escala} (todos os outros): o voxel do modelo.
     */
    private final float cvs;

    public Animator(CharacterModel m, float exaggeration) {
        this.m = m;
        this.amp = exaggeration;
        this.cvs = m.voxelSize * (m.meta != null && m.meta.has("escala") ? m.meta.get("escala").getAsFloat() : 1f);
        Choreo c = null;
        if (m.meta != null && m.meta.has("weaponProfile") && m.anim.equals("biped") && m.index.containsKey("farm_r")) {
            try {
                c = Choreo.get(m.meta.get("weaponProfile").getAsString());
            } catch (IllegalStateException e) {
                System.err.println("aviso: " + e.getMessage());
            }
        }
        // rodada 06.1 — atuação por personagem (meta.acting): postura de repouso e andamento do gesto. Ausente = 0/1.
        if (m.meta != null && m.meta.has("acting") && m.meta.get("acting").isJsonObject()) {
            var ac = m.meta.getAsJsonObject("acting");
            actHeadPitch = (float) Math.toRadians(ac.has("headPitch") ? ac.get("headPitch").getAsFloat() : 0f);
            actHeadRoll = (float) Math.toRadians(ac.has("headRoll") ? ac.get("headRoll").getAsFloat() : 0f);
            actTorsoPitch = (float) Math.toRadians(ac.has("torsoPitch") ? ac.get("torsoPitch").getAsFloat() : 0f);
            actHipRoll = (float) Math.toRadians(ac.has("hipRoll") ? ac.get("hipRoll").getAsFloat() : 0f);
            actSway = ac.has("sway") ? ac.get("sway").getAsFloat() : 1f;
            float tempo = ac.has("emoteTempo") ? ac.get("emoteTempo").getAsFloat() : 1f;
            if (c != null) c = c.withTempo("emote", tempo);
        }
        prof = c;
    }

    /**
     * Atuação de repouso (graus no rig.json, radianos aqui): cabeça baixa/erguida (headPitch, + = olha para
     * baixo), inclinada de lado (headRoll), tronco inclinado (torsoPitch), quadril deslocado para um apoio
     * (hipRoll, contraposto fixo), amplitude da troca de apoio (sway, 1 = padrão).
     */
    private float actHeadPitch, actHeadRoll, actTorsoPitch, actHipRoll, actSway = 1f;

    /** Duração de um golpe do perfil (s); 0 se não existe. */
    public float moveDuration(String move) {
        if (prof == null || move == null || !prof.moves.containsKey(move)) return 0f;
        return prof.moves.get(move).duration();
    }

    public Pose newPose() { return new Pose(m.parts.size()); }

    public CharacterModel model() { return m; }

    // --- acesso por nome (partes ausentes são ignoradas)
    private void rot(String part, float x, float y, float z) {
        Integer i = m.index.get(part);
        if (i == null) return;
        p.v[i * 9] += x;
        p.v[i * 9 + 1] += y;
        p.v[i * 9 + 2] += z;
    }

    private float rx(String part) {
        Integer i = m.index.get(part);
        return i == null ? 0f : p.v[i * 9];
    }

    private float rz(String part) {
        Integer i = m.index.get(part);
        return i == null ? 0f : p.v[i * 9 + 2];
    }

    private void setRx(String part, float x) {
        Integer i = m.index.get(part);
        if (i != null) p.v[i * 9] = x;
    }

    private boolean has(String part) { return m.index.containsKey(part); }

    private void off(String part, float x, float y, float z) {
        Integer i = m.index.get(part);
        if (i == null) return;
        p.v[i * 9 + 3] += x;
        p.v[i * 9 + 4] += y;
        p.v[i * 9 + 5] += z;
    }

    private void scale(String part, float x, float y, float z) {
        Integer i = m.index.get(part);
        if (i == null) return;
        p.v[i * 9 + 6] *= x;
        p.v[i * 9 + 7] *= y;
        p.v[i * 9 + 8] *= z;
    }

    private void root(float ox, float oy, float oz, float rx, float ry, float rz) {
        int b = m.parts.size() * 9;
        p.v[b] += rx;
        p.v[b + 1] += ry;
        p.v[b + 2] += rz;
        p.v[b + 3] += ox;
        p.v[b + 4] += oy;
        p.v[b + 5] += oz;
    }

    /** Achata/estica preservando volume aparente; aplicado na raiz (pés ficam no chão). */
    private void squash(float sy) {
        int b = m.parts.size() * 9;
        float sxz = (float) (1.0 / Math.sqrt(sy));
        p.v[b + 6] *= sxz;
        p.v[b + 7] *= sy;
        p.v[b + 8] *= sxz;
    }

    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
    private static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
    private static final float TAU = (float) (Math.PI * 2);

    /** Duração de um ciclo do clipe em segundos (para captura de quadros e laços). */
    public float duration(Clip clip) {
        if (jointed()) {
            String mode = m.meta.has("weaponMode") ? m.meta.get("weaponMode").getAsString() : "sword";
            return switch (clip) {
                case IDLE -> 2.9f;
                case WALK -> 1f / WALK_CADENCE;
                case RUN -> 1f / RUN_CADENCE;
                case ATTACK -> prof != null && !legacy ? prof.moves.get(prof.attack).duration()
                        : mode.equals("bow") ? 1.6f : mode.equals("staff") ? 1.3f : 1.1f;
                case HIT -> prof != null && !legacy && prof.moves.containsKey("hit") ? prof.moves.get("hit").duration() : 0.9f;
                case EMOTE -> prof != null && !legacy && prof.moves.containsKey("emote") ? prof.moves.get("emote").duration() : 1.6f;
                case DEATH -> 1.2f;
            };
        }
        return switch (m.anim) {
            case "biped" -> switch (clip) {
                case IDLE -> 2.9f;
                case WALK -> 1f / 1.75f;
                case RUN -> 1f / 2.5f;
                case ATTACK -> 1.1f;
                case HIT -> 0.9f;
                case EMOTE -> 1.6f;
                case DEATH -> 1.2f;
            };
            case "brawler" -> switch (clip) {
                case ATTACK -> 3f;                       // preparação [0,1) · golpe [1,2) · recuperação [2,3)
                case HIT -> BRAWLER_HIT;
                case EMOTE -> BRAWLER_ALERT;
                case DEATH -> BRAWLER_DEATH;
                case WALK -> 0.8f;
                case RUN -> 0.55f;
                default -> 1.9f;
            };
            default -> 2f;
        };
    }

    /** Avalia com chão: os pés procuram a altura real do terreno (só rigs articulados). */
    public void evaluate(Clip clip, float t, Pose out, org.joml.Matrix4f rootWorld, Ground g) {
        world = rootWorld;
        ground = g;
        motion = defaultMotion.set(clip, t);
        evaluateInner(clip, t, out);
        world = null;
        ground = null;
    }

    private boolean jointed() {
        return m.anim.equals("biped") && m.index.containsKey("shin_l") && m.index.containsKey("farm_l");
    }

    /** Avalia uma Motion (locomoção + ação) com chão. */
    public void evaluate(Motion mo, Pose out, Matrix4f rootWorld, Ground g) {
        world = rootWorld;
        ground = g;
        motion = mo;
        if (mo.fromClip != null && mo.locoW < 1f && mo.fromClip != mo.clip) {
            // troca de locomoção: avalia o clipe anterior e mistura as poses (clipes de locomoção são
            // próximos entre si; braços com arma continuam vindo do mesmo perfil de transporte)
            if (blendPose == null || blendPose.n != m.parts.size()) blendPose = newPose();
            evaluateInner(mo.fromClip, mo.t, blendPose);
            List<Fx> keep = new ArrayList<>(fx);
            evaluateInner(mo.clip, mo.t, out);
            float w = mo.locoW * mo.locoW * (3 - 2 * mo.locoW);
            out.lerp(blendPose, 1f - w);
            if (fx.isEmpty()) fx.addAll(keep);
        } else
        evaluateInner(mo.clip, mo.t, out);
        motion = defaultMotion;
        world = null;
        ground = null;
    }

    private final Motion defaultMotion = new Motion();
    private Pose blendPose;

    public void evaluate(Clip clip, float t, Pose out) {
        if (motion == defaultMotion || motion == null) motion = defaultMotion.set(clip, t);
        evaluateInner(clip, t, out);
        motion = defaultMotion;
    }

    private void evaluateInner(Clip clip, float t, Pose out) {
        p = out;
        p.reset();
        fx.clear();
        if (clip == Clip.DEATH && !m.anim.equals("brawler")) { clip = Clip.HIT; t = Math.min(t, duration(Clip.HIT) * 0.4f); }
        reachErrR = reachErrL = 0;
        switch (m.anim) {
            case "biped" -> {
                if (jointed()) biped2(clip, t);
                else biped(clip, t);
            }
            case "quadruped" -> quadruped(clip, t);
            case "hopper" -> hopper(clip, t);
            case "brawler" -> brawler(clip, t);
            case "floater" -> floater(clip, t);
            case "flutter" -> flutter(clip, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ bípede
    private void biped(Clip clip, float t) {
        boolean armed = m.index.containsKey("weapon");
        // Ângulo de repouso da arma; rigs podem sobrepor em meta.weaponRest (ex.: cajado quase vertical).
        float weaponRest = m.meta.has("weaponRest") ? m.meta.get("weaponRest").getAsFloat() : 1.05f;
        // Modo da arma (rodada 02): "sword" (padrão), "staff" (cajado na mão direita, sempre quase
        // vertical) ou "bow" (parte "bow" na mão esquerda; ataque = puxar e soltar a corda).
        String mode = m.meta.has("weaponMode") ? m.meta.get("weaponMode").getAsString() : "sword";
        // Pernas rígidas giram em arco: o quadril desce para os dois pés continuarem no chão.
        float legLen = m.index.containsKey("leg_l") ? m.parts.get(m.index.get("leg_l")).py : 0f;
        boolean plant = m.meta.has("footPlant") && m.meta.get("footPlant").getAsBoolean();
        if (clip == Clip.ATTACK && mode.equals("bow")) {
            bowShot(t);
            secondary(clip, t, mode, weaponRest);
            return;
        }
        if (clip == Clip.ATTACK && mode.equals("staff")) {
            staffCast(t);
            secondary(clip, t, mode, weaponRest);
            return;
        }
        if (mode.equals("staff") || mode.equals("bow")) armed = false;   // braço direito livre ou cajado leve
        switch (clip) {
            case IDLE -> {
                float b = sin(t * 2.2f);
                scale("torso", 1, 1 + 0.018f * b * amp, 1);
                root(0, 0.004f * b, 0, 0, 0, 0);
                rot("head", 0.035f * sin(t * 2.2f - 0.7f), 0.16f * sin(t * 0.72f) * amp, 0.03f * sin(t * 0.9f));
                rot("arm_l", 0.07f * sin(t * 2.2f + 1.0f), 0, 0.07f + 0.02f * b);
                rot("arm_r", 0.05f * sin(t * 2.2f + 2.0f) - (armed ? 0.25f : 0), 0, -0.07f - 0.02f * b);
                rot("weapon", weaponRest + 0.05f * sin(t * 2.2f + 0.5f), 0, 0);
                rot("cape", 0.10f + 0.06f * sin(t * 1.7f), 0, 0.03f * sin(t * 1.1f));
                rot("tail", 0.12f + 0.07f * sin(t * 1.9f + 0.5f), 0, 0.05f * sin(t * 1.3f));
                rot("skirt", 0.02f * sin(t * 2.2f), 0, 0);
                rot("hat", 0.02f * sin(t * 2.2f - 1.2f), 0, 0.02f * sin(t * 0.9f - 0.5f));
            }
            case WALK -> {
                float ph = t * TAU * 1.75f, s = sin(ph);
                float bob = 0.5f - 0.5f * cos(2 * ph);
                float a = 0.62f * amp;
                rot("leg_l", -a * s, 0, 0);
                rot("leg_r", a * s, 0, 0);
                rot("arm_l", 0.55f * amp * s, 0, 0.08f);
                rot("arm_r", armed ? -0.25f - 0.18f * s : -0.55f * amp * s, 0, -0.08f);
                rot("weapon", weaponRest - 0.1f * s, 0, 0);
                if (plant && m.height > 0) {
                    float drop = legLen * (1 - cos(a * s)) / m.height;
                    float lift = 0.10f * legLen / m.height;
                    root(0, -drop + 0.008f * (1 - bob), 0, 0.05f, 0, 0.035f * s * amp);
                    off("leg_l", 0, lift * Math.max(0f, cos(ph)), 0);
                    off("leg_r", 0, lift * Math.max(0f, -cos(ph)), 0);
                } else
                root(0, 0.030f * bob * amp, 0, 0.05f, 0, 0.035f * s * amp);
                rot("torso", 0.02f, 0.13f * s * amp, 0);
                rot("head", -0.03f + 0.04f * cos(2 * ph - 0.6f), -0.10f * s * amp, -0.03f * s);
                rot("cape", 0.38f + 0.14f * sin(2 * ph + 1.2f), 0, 0.08f * s);
                rot("tail", 0.42f + 0.2f * sin(2 * ph + 0.6f), 0, 0.12f * s);
                rot("skirt", 0.05f * sin(2 * ph), 0, 0.04f * s);
                rot("hat", 0.05f * sin(2 * ph - 0.9f), 0, 0);
                squash(1 + 0.025f * amp * (bob - 0.5f));
            }
            case RUN -> {
                float ph = t * TAU * 2.5f, s = sin(ph);
                float bob = 0.5f - 0.5f * cos(2 * ph);
                rot("leg_l", -1.0f * amp * s - 0.1f, 0, 0);
                rot("leg_r", 1.0f * amp * s - 0.1f, 0, 0);
                rot("arm_l", 0.95f * amp * s, 0, 0.16f);
                rot("arm_r", armed ? 0.55f + 0.2f * s : -0.95f * amp * s, 0, -0.16f);
                rot("weapon", armed ? weaponRest + 1.05f - 0.15f * s : weaponRest, 0, 0);
                if (plant && m.height > 0) {
                    float drop = legLen * (1 - cos(amp * s)) / m.height;
                    float lift = 0.16f * legLen / m.height;
                    root(0, -drop + 0.045f * (1 - bob), 0, 0.26f, 0, 0.05f * s * amp);
                    off("leg_l", 0, lift * Math.max(0f, cos(ph)), 0);
                    off("leg_r", 0, lift * Math.max(0f, -cos(ph)), 0);
                } else
                root(0, 0.055f * bob * amp, 0, 0.26f, 0, 0.05f * s * amp);
                rot("torso", 0.04f, 0.20f * s * amp, 0);
                rot("head", -0.20f + 0.05f * cos(2 * ph - 0.6f), -0.14f * s * amp, 0);
                rot("cape", 0.95f + 0.18f * sin(2 * ph + 1.0f), 0, 0.10f * s);
                rot("tail", 1.0f + 0.25f * sin(2 * ph + 0.4f), 0, 0.15f * s);
                rot("skirt", 0.12f + 0.06f * sin(2 * ph), 0, 0.05f * s);
                rot("hat", -0.06f + 0.07f * sin(2 * ph - 0.9f), 0, 0);
                squash(1 + 0.05f * amp * (bob - 0.5f));
            }
            case ATTACK -> {
                float u = (t % 1.1f) / 1.1f;
                float wind = smooth(0.00f, 0.28f, u) - smooth(0.28f, 0.40f, u);   // antecipação
                float hit = smooth(0.28f, 0.40f, u) - smooth(0.55f, 0.95f, u);    // golpe + recuperação
                float idle = sin(t * 2.2f);
                // Antecipação com o braço aberto para fora (rz): a lâmina passa por cima do ombro, não pela
                // cabeça (defeito visto na rodada 02 em todas as direções).
                rot("arm_r", -2.35f * wind - 1.15f * hit - 0.25f * (1 - wind - hit), -0.3f * wind + 0.5f * hit, -0.80f * wind);
                rot("weapon", weaponRest + 0.1f * wind + 0.55f * hit, 0, -0.35f * wind);
                rot("torso", -0.12f * wind + 0.30f * hit, 0.45f * wind - 0.55f * hit, 0);
                rot("head", 0.06f * wind - 0.16f * hit, -0.3f * wind + 0.35f * hit, 0);
                rot("arm_l", 0.5f * wind - 0.7f * hit, 0, 0.3f * wind + 0.5f * hit);
                rot("leg_l", 0.25f * wind - 0.55f * hit, 0, 0);
                rot("leg_r", -0.15f * wind + 0.45f * hit, 0, 0);
                root(0, -0.02f * hit + 0.01f * wind + 0.003f * idle, -0.04f * wind + 0.13f * hit, 0, 0, 0);
                rot("cape", 0.15f + 0.5f * hit + 0.2f * wind, 0, 0);
                rot("tail", 0.2f + 0.6f * hit, 0, 0.2f * wind);
                squash(1 + 0.05f * wind - 0.06f * hit);
            }
            case HIT -> {
                float u = (t % 0.9f) / 0.9f;
                float e = (float) (Math.exp(-u * 5.5) * Math.sin(Math.min(u * 9.0, Math.PI * 0.5) ));
                float sh = (float) (Math.exp(-u * 6.0) * Math.sin(u * 40.0));
                rot("torso", -0.5f * e, 0, 0.06f * sh);
                rot("head", -0.45f * e, 0, 0.12f * sh);
                rot("arm_l", -0.9f * e, 0, 0.9f * e);
                rot("arm_r", -0.9f * e, 0, -0.9f * e);
                rot("leg_l", -0.35f * e, 0, 0);
                rot("leg_r", 0.2f * e, 0, 0);
                rot("weapon", weaponRest + 0.4f * e, 0, 0);
                rot("cape", -0.3f * e + 0.1f, 0, 0);
                rot("tail", -0.5f * e + 0.1f, 0, 0);
                root(0.012f * sh, 0.02f * e, -0.16f * e, 0, 0, 0);
                squash(1 - 0.10f * e);
            }
            case EMOTE -> {
                float u = (t % 1.6f) / 1.6f;
                float crouch = smooth(0.0f, 0.16f, u) - smooth(0.16f, 0.26f, u) + (smooth(0.62f, 0.70f, u) - smooth(0.70f, 0.86f, u));
                float air = smooth(0.18f, 0.30f, u) - smooth(0.50f, 0.66f, u);
                float jump = (float) Math.sin(Math.clamp((u - 0.2f) / 0.46f, 0f, 1f) * Math.PI);
                root(0, 0.34f * jump * amp, 0, 0, 0, 0.05f * sin(u * TAU * 2) * air);
                squash(1 - 0.16f * crouch + 0.10f * air * (float) Math.abs(Math.cos(Math.clamp((u - 0.2f) / 0.46f, 0f, 1f) * Math.PI)));
                rot("arm_l", -0.3f * air, 0, 2.5f * air + 0.1f);
                rot("arm_r", armed ? -2.7f * air - 0.25f : -0.3f * air, 0, armed ? -0.15f * air : -2.5f * air - 0.1f);
                rot("weapon", weaponRest - 0.9f * air, 0, 0);
                rot("leg_l", -0.5f * air, 0, 0.18f * air);
                rot("leg_r", 0.35f * air, 0, -0.18f * air);
                rot("head", -0.28f * air + 0.2f * crouch, 0, 0.08f * sin(u * TAU * 2) * air);
                rot("cape", 0.1f + 0.9f * air * (1 - jump * 0.6f), 0, 0);
                rot("tail", 0.1f + 1.2f * air * (1 - jump * 0.5f), 0, 0);
                rot("hat", -0.1f * air, 0, 0);
            }
        }
        secondary(clip, t, mode, weaponRest);
    }

    /**
     * Movimento secundário e restrições aplicados depois do clipe (rodada 02):
     * cajado e arco compensam o giro do braço para ficarem quase verticais; as metades do manto
     * ({@code skirt_l}/{@code skirt_r}) acompanham a perna do mesmo lado; a capa nunca gira para dentro
     * do corpo e o segmento de baixo ({@code cape_lo}) atrasa em relação ao de cima.
     */
    private void secondary(Clip clip, float t, String mode, float weaponRest) {
        // Postura própria do personagem (meta.lean, rad): inclina o corpo inteiro sobre os pés.
        if (m.meta.has("lean")) root(0, 0, 0, m.meta.get("lean").getAsFloat(), 0, 0);
        // Tranças longas balançam menos (meta.tailGain): o ganho padrão é pensado para rabos curtos.
        if (m.meta.has("tailGain") && has("tail")) setRx("tail", rx("tail") * m.meta.get("tailGain").getAsFloat());
        boolean choreo = prof != null && !legacy && jointed();
        if (mode.equals("staff") && clip != Clip.ATTACK && !choreo) {
            setRx("weapon", weaponRest - (rx("arm_r") + rx("farm_r")) * 0.92f);
            Integer wi = m.index.get("weapon");
            if (wi != null) p.v[wi * 9 + 2] = -(rz("arm_r") + rz("farm_r")) * 0.95f;   // e de lado também
        }
        if (mode.equals("bow") && clip != Clip.ATTACK && !choreo) {
            setRx("bow", -(rx("arm_l") + rx("farm_l")) * 0.85f);
            rot("bow", 0, 0.6f, 0);       // repouso: arco girado ~35° para mostrar a curva de frente
        }
        if (has("arrow") && !choreo && !(mode.equals("bow") && clip == Clip.ATTACK)) scale("arrow", 0, 0, 0);
        if (has("arrow") && choreo && (clip == Clip.HIT || clip == Clip.EMOTE)) scale("arrow", 0, 0, 0);
        if (has("skirt_l")) {
            rot("skirt_l", rx("leg_l") * 0.78f, 0, 0);
            rot("skirt_r", rx("leg_r") * 0.78f, 0, 0);
        }
        if (choreo) applySecondaryLag();
        if (has("cape")) {
            float c = Math.max(rx("cape"), 0.03f);
            setRx("cape", c);
            if (has("cape_lo")) {
                float lag = clip == Clip.RUN ? 0.45f : (clip == Clip.WALK ? 0.18f : 0.06f);
                setRx("cape_lo", Math.max(0f, c * 0.55f + lag + 0.05f * sin(t * 3.1f)));
            }
        }
    }

    /** Arco: gira o tronco de lado, estende o braço esquerdo com o arco em pé, puxa e solta a corda. */
    private void bowShot(float t) {
        float u = (t % 1.1f) / 1.1f;
        float raise = smooth(0.0f, 0.22f, u) - smooth(0.80f, 1.0f, u);
        float draw = smooth(0.18f, 0.50f, u) - smooth(0.60f, 0.64f, u);   // solta de repente
        float rel = smooth(0.60f, 0.66f, u) - smooth(0.72f, 0.95f, u);    // braço direito recua após soltar
        float idle = sin(t * 2.2f);
        rot("torso", 0.02f, -0.55f * raise, 0);
        rot("head", 0.05f * raise, 0.50f * raise, 0);
        rot("arm_l", -1.48f * raise, 0.10f * raise, 0.10f);
        rot("bow", 1.48f * raise, 0, 0);
        rot("arm_r", -1.30f * raise - 0.25f * draw + 0.45f * rel, 0.35f * draw, -0.35f * raise);
        rot("leg_l", -0.18f * raise, 0, 0);
        rot("leg_r", 0.18f * raise, 0, 0);
        rot("tail", 0.15f + 0.3f * rel, 0, 0);
        root(0, 0.003f * idle, -0.02f * rel, 0, 0, 0);
        squash(1 - 0.03f * rel);
    }

    /** Cajado: ergue acima da cabeça (antecipação) e aponta para a frente com o braço livre aberto. */
    private void staffCast(float t) {
        float u = (t % 1.1f) / 1.1f;
        float wind = smooth(0.00f, 0.30f, u) - smooth(0.30f, 0.42f, u);
        float cast = smooth(0.30f, 0.42f, u) - smooth(0.62f, 0.98f, u);
        float idle = sin(t * 2.2f);
        float armR = -2.4f * wind - 1.25f * cast;
        rot("arm_r", armR, 0, -0.55f * wind);    // aberto: o cajado passa fora da aba do chapéu
        rot("weapon", -armR * (0.95f - 0.55f * cast), 0, 0);
        rot("arm_l", 0.3f * wind - 1.2f * cast, 0, 0.25f * wind + 0.35f * cast);
        rot("torso", -0.10f * wind + 0.18f * cast, 0.25f * wind - 0.2f * cast, 0);
        rot("head", 0.08f * wind - 0.10f * cast, 0, 0);
        rot("hat", -0.12f * cast + 0.05f * wind, 0, 0);
        rot("leg_l", 0.15f * wind - 0.35f * cast, 0, 0);
        rot("leg_r", -0.10f * wind + 0.25f * cast, 0, 0);
        root(0, 0.012f * wind - 0.012f * cast + 0.003f * idle, 0.06f * cast, 0, 0, 0);
        squash(1 + 0.04f * wind - 0.05f * cast);
    }

    // ------------------------------------------------------------------ bípede articulado (rodada 03)
    private CharacterModel.Part part(String n) { return m.parts.get(m.index.get(n)); }

    private static float frac(float x) { return x - (float) Math.floor(x); }

    /** Rodada 09.2 — pés no ar: quanto cada pé sobe (fração da perna) e quanto o quadril cede ao pousar. */
    static final float AIR_TUCK_L = 0.34f, AIR_TUCK_R = 0.20f, LAND_DIP = 0.16f, TAKEOFF_DIP = 0.14f;
    /** Rodada 09.2 — fração do passo que vira deslocamento lateral (pés não se cruzam ao andar de lado). */
    static final float LATERAL = 0.62f;
    private final float[] lf = new float[9];
    /** Giro do rolamento desta avaliação (graus, já ponderado e levado a (−180, 180]). */
    private float spinNow;

    /**
     * Rodada 09.2 — passada de andar/correr no tempo t: {zEsq, ergueEsq, zDir, ergueDir, xEsq, xDir, balançoEsq,
     * balançoDir, zPouso}. Com stride 1 e rumo 0 é exatamente a passada das rodadas anteriores. Rumo ≠ 0: o pé anda ao
     * longo do deslocamento (x = lado, z = frente); de lado a passada encolhe e os pés abrem um pouco para não cruzar.
     */
    private void locoFeet(Clip c, float t, float stride, float dir, float[] out) {
        boolean run = c == Clip.RUN;
        float cad = run ? RUN_CADENCE : WALK_CADENCE, st = run ? RUN_STANCE : WALK_STANCE;
        float half = (run ? RUN_HALF_STRIDE : WALK_HALF_STRIDE) * stride, lift = (run ? 0.085f : 0.045f) * stride;
        float fl = frac(t * cad), fr = frac(t * cad + 0.5f);
        float[] a = footCycle(fl, st, half, lift), b = footCycle(fr, st, half, lift);
        out[0] = a[0]; out[1] = a[1]; out[2] = b[0]; out[3] = b[1]; out[4] = 0f; out[5] = 0f;
        out[6] = fl >= st ? (fl - st) / (1 - st) : -1f;
        out[7] = fr >= st ? (fr - st) / (1 - st) : -1f;
        out[8] = half;
        if (dir != 0f) {
            float sn = (float) Math.sin(dir), cs = (float) Math.cos(dir), side = Math.abs(sn);
            out[4] = a[0] * sn * LATERAL + 0.025f * side;               // esquerdo (+x) abre para a esquerda
            out[5] = b[0] * sn * LATERAL - 0.025f * side;               // direito abre para a direita
            out[0] = a[0] * cs;
            out[2] = b[0] * cs;
            out[8] = half * cs;
        }
    }

    /**
     * Rodada 09.2 — gira a raiz em X em torno do ponto (0, cy, 0) do modelo: mantém a rotação/translação já postas e
     * troca o pivô da sola pelo centro da bola. Só X (rumo e rolagem da raiz são ~0 no rolamento).
     */
    private void spinRoot(float a, float cy, float cz, float axisYaw) {
        int b = m.parts.size() * 9;
        if (axisYaw != 0f) {                                            // rodada 09.3: eixo diagonal (rolamento de ombro)
            float h = m.height;
            org.joml.Quaternionf cur = new org.joml.Quaternionf().rotateZ(p.v[b + 2]).rotateY(p.v[b + 1]).rotateX(p.v[b]);
            org.joml.Quaternionf sp = new org.joml.Quaternionf().rotateAxis(a, (float) Math.cos(axisYaw), 0f, -(float) Math.sin(axisYaw));
            Vector3f e = sp.mul(cur, new org.joml.Quaternionf()).getEulerAnglesZYX(new Vector3f());
            p.v[b] = e.x;
            p.v[b + 1] = e.y;
            p.v[b + 2] = e.z;
            Vector3f t = sp.transform(new Vector3f(p.v[b + 3] * h, p.v[b + 4] * h - cy, p.v[b + 5] * h - cz));
            p.v[b + 3] = t.x / h;
            p.v[b + 4] = (t.y + cy) / h;
            p.v[b + 5] = (t.z + cz) / h;
            return;
        }
        float h = m.height, ty = p.v[b + 4] * h - cy, tz = p.v[b + 5] * h - cz;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        p.v[b] += a;
        p.v[b + 4] = (ty * c - tz * s + cy) / h;
        p.v[b + 5] = (ty * s + tz * c + cz) / h;
    }

    /** Rodada 09.3 — inclinação lateral, pés para o lado e pivô do giro desta avaliação (já ponderados pelo peso da ação). */
    private float rollNow, feetXl, feetXr, spinPy, spinPz, spinAx;

    /** Pé numa fase f ∈ [0,1): apoio (pé desliza para trás no chão) e balanço (sobe e avança). {z, ergue} */
    private static float[] footCycle(float f, float stance, float half, float lift) {
        if (f < stance) return new float[]{half - 2 * half * f / stance, 0f};
        float u = (f - stance) / (1 - stance);
        float e = u * u * (3 - 2 * u);
        return new float[]{-half + 2 * half * e, lift * (float) Math.sin(Math.PI * u)};
    }

    /**
     * IK analítico de duas barras no plano sagital: dado o tornozelo-alvo (y, z no espaço do modelo),
     * calcula coxa e canela. O joelho sempre dobra para a frente. Alvo fora de alcance = perna esticada.
     */
    private void legIK(String thigh, String shin, float hipY, float hipZ, float footY, float footZ, float l1, float l2, float tilt) {
        float dz = footZ - hipZ, dy = footY - hipY;
        float d = (float) Math.sqrt(dz * dz + dy * dy);
        d = Math.clamp(d, Math.abs(l1 - l2) + 1e-3f, (l1 + l2) * 0.999f);
        float a = (float) Math.atan2(dz, -dy);
        float a1 = (float) Math.acos(Math.clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1f, 1f));
        float b = (float) Math.acos(Math.clamp((l1 * l1 + l2 * l2 - d * d) / (2 * l1 * l2), -1f, 1f));
        setRx(thigh, -(a + a1 + tilt));
        setRx(shin, (float) (Math.PI - b));
    }

    /** Diferença de altura do chão sob um ponto do modelo em relação ao chão sob a raiz (limitada). */
    private float groundOffset(float x, float z) {
        if (ground == null || world == null) return 0f;
        // rodada 04: a PEGADA do pé (calcanhar, meio, ponta) — o pé apoia no ponto mais alto sob ele, então
        // não fica pendurado na quina do degrau; o chão vem do topo real dos voxels (World.groundAt)
        float vs = m.voxelSize, g = -1e9f;
        for (float dz : new float[]{-2.5f * vs, 0f, 3.5f * vs}) {
            org.joml.Vector3f wp = world.transformPosition(new org.joml.Vector3f(x, 0, z + dz));
            g = Math.max(g, ground.height(wp.x, wp.z));
        }
        org.joml.Vector3f w0 = world.transformPosition(new org.joml.Vector3f(0, 0, 0));
        float lim = GROUND_REACH * legLen();
        return Math.clamp(g - ground.height(w0.x, w0.z), -lim, lim);
    }

    /** Maior diferença de altura que um pé alcança em relação à raiz (fração do comprimento da perna). */
    static final float GROUND_REACH = 0.62f;
    /** Diferença máxima entre os dois pés (fração da perna): acima disso o pé alto não apoia — pede escalada. */
    static final float STEP_LIMIT = 0.55f;

    private float legLen() {
        CharacterModel.Part lL = part("leg_l");
        float ankle = (m.meta.has("ankle") ? m.meta.get("ankle").getAsFloat() : 1.5f) * m.voxelSize;
        return lL.py - ankle;
    }

    /**
     * Clipes do bípede ARTICULADO (braço/antebraço, coxa/canela; ver tools/charlib_e.limb_parts).
     * As pernas são posicionadas por IK a partir de trajetórias de pé; a raiz controla só a altura do
     * quadril, o avanço do corpo e a inclinação. Rigs sem articulação continuam em {@link #biped}.
     */
    private void biped2(Clip clip, float t) {
        float h = m.height;
        CharacterModel.Part lL = part("leg_l"), lR = part("leg_r"), kL = part("shin_l");
        float hip = lL.py, knee = kL.py;
        float ankle = (m.meta.has("ankle") ? m.meta.get("ankle").getAsFloat() : 1.5f) * m.voxelSize;
        float l1 = hip - knee, l2 = knee - ankle, lmax = l1 + l2;
        String mode = m.meta.has("weaponMode") ? m.meta.get("weaponMode").getAsString() : "sword";
        float weaponRest = m.meta.has("weaponRest") ? m.meta.get("weaponRest").getAsFloat() : 1.05f;
        boolean sword = mode.equals("sword") && has("weapon");
        float lean = m.meta.has("lean") ? m.meta.get("lean").getAsFloat() : 0f;

        // alvos: tornozelo (z, ergue) de cada pé, altura do quadril, avanço e inclinação da raiz
        float zl = 0.02f, zr = -0.015f, yl = 0, yr = 0, hipH = ankle + lmax * 0.96f, rootZ = 0, tilt = 0;
        float xl = 0f, xr = 0f;                                   // rodada 09.2: pé para o lado (andar de lado)
        spinNow = rollNow = feetXl = feetXr = spinPy = spinPz = spinAx = 0f;
        float idle = sin(t * 2.2f);
        boolean choreo = prof != null && !legacy;
        Clip base = clip;
        if (choreo && clip == Clip.ATTACK) {                // ataque antigo → golpe padrão do perfil sobre o repouso
            base = Clip.IDLE;
            if (motion.move == null) {
                motion.move = prof.attack;
                motion.moveT = t % prof.moves.get(prof.attack).duration();
                motion.weight = 1f;
            }
        }
        // rodada 05: golpe recebido e comemoração também são coreografados (antes eram FK e atravessavam a cabeça)
        String gesture = clip == Clip.HIT ? "hit" : clip == Clip.EMOTE ? "emote" : null;
        if (choreo && gesture != null && prof.moves.containsKey(gesture)) {
            base = Clip.IDLE;
            if (motion.move == null) {
                motion.move = gesture;
                motion.moveT = t % prof.moves.get(gesture).duration();
                motion.weight = 1f;
            }
        }
        float locoPhase = 0f, rootYaw = 0f;
        swingL = swingR = -1f;

        switch (base) {
            case IDLE -> {
                float shift = sin(t * 0.7f);                      // troca de apoio lenta (contraposto)
                hipH = ankle + lmax * (0.955f + 0.006f * idle);
                yl = 0;
                root(0, 0, 0, 0, 0, 0.018f * shift * actSway + actHipRoll);
                scale("torso", 1, 1 + 0.016f * idle * amp, 1);
                rot("torso", 0.01f + actTorsoPitch, 0.04f * shift * actSway, -0.5f * actHipRoll);
                rot("head", 0.035f * sin(t * 2.2f - 0.7f) + actHeadPitch, 0.16f * sin(t * 0.72f) * amp,
                        0.03f * sin(t * 0.9f) + actHeadRoll);
                rot("arm_l", 0.06f * sin(t * 2.2f + 1f), 0, 0.08f + 0.02f * idle);
                rot("farm_l", -0.18f - 0.05f * sin(t * 2.2f + 1.4f), 0, 0);
                // espada baixa e para fora (não cobre o rosto de frente); cajado afastado do corpo
                boolean staffM = mode.equals("staff");
                rot("arm_r", 0.05f * sin(t * 2.2f + 2f) - (sword ? 0.12f : 0), 0,
                        (sword ? -0.20f : staffM ? -0.45f : -0.08f) - 0.02f * idle);
                rot("farm_r", sword ? -0.32f : staffM ? -0.10f : -0.18f - 0.05f * sin(t * 2.2f + 2.4f), 0, 0);
                rot("weapon", weaponRest + (sword ? 0.30f : 0f) + 0.05f * sin(t * 2.2f + 0.5f), 0, sword ? -0.25f : 0f);
                rot("tail", 0.12f + 0.07f * sin(t * 1.9f + 0.5f), 0, 0.05f * sin(t * 1.3f));
                rot("hat", 0.02f * sin(t * 2.2f - 1.2f), 0, 0.02f * sin(t * 0.9f - 0.5f));
                rot("skirt_l", 0.02f * idle, 0, 0);
                rot("skirt_r", 0.02f * idle, 0, 0);
            }
            case WALK, RUN -> {
                boolean run = clip == Clip.RUN;
                float cad = run ? RUN_CADENCE : WALK_CADENCE, st = run ? RUN_STANCE : WALK_STANCE;
                locoFeet(clip, t, motion.stride, motion.locoDir, lf);
                zl = lf[0]; yl = lf[1]; zr = lf[2]; yr = lf[3]; xl = lf[4]; xr = lf[5];
                // balanço: o pé já mira a altura do ponto onde vai pousar (antecipa o degrau) e passa por cima da quina
                swingL = lf[6];
                swingR = lf[7];
                landZ = lf[8];
                float fl = frac(t * cad);
                float ph = (float) (2 * Math.PI) * fl;
                locoPhase = ph;
                float c = cos(ph);                                // +1: pé esquerdo à frente
                // quadril mais alto no meio do apoio de cada pé; na corrida, fase aérea
                float bob = cos(2 * ph - (float) Math.PI * st);
                hipH = ankle + lmax * (run ? 0.80f : 0.83f) + (run ? 0.022f * Math.max(0f, -bob) : 0.008f * bob) * amp;
                tilt = run ? 0.24f : 0.06f;
                float sw = (run ? 0.85f : 0.42f) * amp;           // balanço dos braços
                rot("torso", run ? 0.04f : 0.02f, (run ? 0.17f : 0.11f) * c * amp, 0);
                root(0, 0, 0, 0, 0, (run ? 0.035f : 0.028f) * sin(ph) * amp);
                rot("head", -0.03f + 0.04f * cos(2 * ph - 0.6f) - (run ? 0.18f : 0f), -0.09f * c * amp, 0);
                float al = sw * c, ar = -sw * c;
                rot("arm_l", al, 0, 0.09f);
                rot("farm_l", -(run ? 1.05f : 0.28f) - 0.40f * Math.max(0f, -al) / Math.max(sw, 1e-3f), 0, 0);
                if (sword) {
                    rot("arm_r", run ? 0.45f + 0.15f * c : -0.22f + 0.12f * c, 0, -0.10f);
                    rot("farm_r", run ? -0.6f : -0.5f, 0, 0);
                    rot("weapon", run ? weaponRest + 0.9f : weaponRest - 0.1f * c, 0, 0);
                } else {
                    rot("arm_r", ar, 0, -0.09f);
                    rot("farm_r", -(run ? 1.05f : 0.28f) - 0.40f * Math.max(0f, -ar) / Math.max(sw, 1e-3f), 0, 0);
                    rot("weapon", weaponRest, 0, 0);
                }
                rot("tail", (run ? 1.0f : 0.42f) + (run ? 0.25f : 0.2f) * sin(2 * ph + 0.6f), 0, 0.12f * c);
                rot("hat", (run ? -0.06f : 0f) + 0.05f * sin(2 * ph - 0.9f), 0, 0);
                scale("torso", 1, 1 + 0.02f * amp * bob, 1);
            }
            case ATTACK -> {
                if (mode.equals("bow")) {
                    float[] r = bowShot2(t);
                    zl = r[0]; zr = r[1]; hipH = ankle + lmax * r[2]; rootZ = r[3];
                } else if (mode.equals("staff")) {
                    float[] r = staffCast2(t, weaponRest);
                    zl = r[0]; zr = r[1]; hipH = ankle + lmax * r[2]; rootZ = r[3];
                } else {
                    float u = frac(t / 1.1f);
                    float wind = smooth(0.00f, 0.28f, u) - smooth(0.28f, 0.37f, u);
                    float hit = smooth(0.28f, 0.37f, u) - smooth(0.52f, 0.95f, u);
                    float rest = Math.max(0f, 1 - wind - hit);
                    rot("arm_r", -2.25f * wind - 1.10f * hit - 0.22f * rest, -0.3f * wind + 0.5f * hit, -0.80f * wind + 0.15f * hit);
                    rot("farm_r", -0.95f * wind - 0.10f * hit - 0.5f * rest, 0, 0);
                    rot("weapon", weaponRest + 0.1f * wind + 0.55f * hit, 0, -0.35f * wind);
                    rot("torso", -0.10f * wind + 0.26f * hit, 0.45f * wind - 0.55f * hit, 0);
                    rot("head", 0.06f * wind - 0.14f * hit, -0.30f * wind + 0.32f * hit, 0);
                    rot("arm_l", 0.45f * wind - 0.6f * hit, 0, 0.30f * wind + 0.45f * hit);
                    rot("farm_l", -0.5f * wind - 0.7f * hit - 0.2f, 0, 0);
                    rot("tail", 0.2f + 0.6f * hit, 0, 0.2f * wind);
                    zl = 0.07f + 0.02f * hit;                     // passo à frente com o pé esquerdo
                    zr = -0.06f;
                    rootZ = -0.02f * wind + 0.06f * hit;           // investida: o corpo avança, os pés ficam
                    hipH = ankle + lmax * (0.90f - 0.10f * hit - 0.03f * wind);
                    tilt = 0.10f * hit - 0.04f * wind;
                    scale("torso", 1, 1 + 0.04f * wind - 0.04f * hit, 1);
                }
            }
            case HIT -> {
                float u = frac(t / 0.9f);
                float e = (float) (Math.exp(-u * 5.5) * Math.sin(Math.min(u * 9.0, Math.PI * 0.5)));
                float sh = (float) (Math.exp(-u * 6.0) * Math.sin(u * 40.0));
                rot("torso", -0.45f * e, 0, 0.06f * sh);
                rot("head", -0.40f * e, 0, 0.12f * sh);
                rot("arm_l", -0.85f * e, 0, 0.55f * e);
                rot("farm_l", -0.25f - 1.3f * e, 0, 0);       // antebraços sobem para proteger o rosto
                rot("arm_r", -0.85f * e - (sword ? 0.2f : 0), 0, -0.55f * e);
                rot("farm_r", -0.25f - 1.3f * e, 0, 0);
                rot("weapon", weaponRest + 0.4f * e, 0, 0);
                rot("tail", -0.5f * e + 0.1f, 0, 0);
                zl = 0.02f;
                zr = -0.03f - 0.07f * e;                         // recua um pé
                rootZ = -0.05f * e;
                hipH = ankle + lmax * (0.95f - 0.12f * e);
                tilt = -0.15f * e;
                root(0.012f * sh * 0, 0, 0, 0, 0, 0.04f * sh);
            }
            case EMOTE -> {
                float u = frac(t / 1.6f);
                float crouch = smooth(0.0f, 0.16f, u) - smooth(0.16f, 0.24f, u) + (smooth(0.62f, 0.70f, u) - smooth(0.70f, 0.86f, u));
                float air = smooth(0.20f, 0.30f, u) - smooth(0.50f, 0.64f, u);
                float jump = (float) Math.sin(Math.clamp((u - 0.2f) / 0.44f, 0f, 1f) * Math.PI);
                float jh = 0.30f * jump * amp;
                hipH = ankle + lmax * (0.96f - 0.30f * crouch) + jh;
                yl = jh + 0.07f * air;
                yr = jh + 0.05f * air;
                zl = 0.02f + 0.03f * air;
                zr = -0.02f - 0.02f * air;
                float wave = 0.5f * sin(u * (float) Math.PI * 8) * air;
                rot("arm_l", -0.3f * air, 0, 2.3f * air + 0.1f);
                rot("farm_l", -0.3f - 0.4f * air + wave, 0, 0);
                if (sword || mode.equals("staff")) {           // arma erguida pela frente (cajado fica em pé)
                    rot("arm_r", -2.6f * air - 0.22f, 0, -0.15f * air);
                    rot("farm_r", -0.4f + 0.3f * air, 0, 0);
                } else {
                    rot("arm_r", -0.3f * air, 0, -2.3f * air - 0.1f);
                    rot("farm_r", -0.3f - 0.4f * air - wave, 0, 0);
                }
                rot("weapon", weaponRest - 0.9f * air, 0, 0);
                rot("head", -0.26f * air + 0.18f * crouch, 0, 0.08f * sin(u * TAU * 2) * air);
                rot("tail", 0.1f + 1.0f * air * (1 - jump * 0.5f), 0, 0);
                rot("hat", -0.1f * air, 0, 0);
                scale("torso", 1, 1 - 0.08f * crouch + 0.05f * air, 1);
                tilt = 0.12f * crouch;
            }
        }

        if (choreo && (base == Clip.IDLE || base == Clip.WALK || base == Clip.RUN)) {
            legs[0] = zl; legs[1] = zr; legs[2] = yl; legs[3] = yr; legs[4] = hipH; legs[5] = rootZ; legs[6] = tilt; legs[7] = 0f;
            actionLayer(base, t, locoPhase, ankle, lmax);
            zl = legs[0]; zr = legs[1]; yl = legs[2]; yr = legs[3]; hipH = legs[4]; rootZ = legs[5]; tilt = legs[6];
            rootYaw = legs[7];
            xl += feetXl;                                            // rodada 09.3: pés para o lado (esquiva lateral)
            xr += feetXr;
        }
        // rodada 09.2 — golpe andando: as PERNAS seguem a passada real (fase pela distância: pé plantado); tronco, braços,
        // quadril e arma ficam os do golpe (a trajetória validada não muda). Peso suave: parar devolve a base do golpe.
        if (choreo && motion.legW > 0f && (motion.legClip == Clip.WALK || motion.legClip == Clip.RUN)) {
            locoFeet(motion.legClip, motion.legT, motion.stride, motion.legDir, lf);
            float k = Math.clamp(motion.legW, 0f, 1f);
            k = k * k * (3 - 2 * k);
            zl += (lf[0] - zl) * k; yl += (lf[1] - yl) * k;
            zr += (lf[2] - zr) * k; yr += (lf[3] - yr) * k;
            xl += (lf[4] - xl) * k; xr += (lf[5] - xr) * k;
            if (k > 0.5f) { swingL = lf[6]; swingR = lf[7]; landZ = lf[8]; }
        }
        // rodada 09.2 — salto: no ar os pés recolhem (o esquerdo mais, o direito atrás) e o corpo inclina um pouco;
        // ao pousar o quadril cede e volta (amortecimento). O corpo sobe pela física do jogo, não pela pose.
        if (motion.air > 0f) {
            float a = Math.clamp(motion.air, 0f, 1f);
            a = a * a * (3 - 2 * a);
            float f = Math.clamp(motion.fall, 0f, 1f);               // rodada 09.3: o pouso chegando desdobra as pernas
            f = f * f * (3 - 2 * f);
            float tuck = a * (1f - 0.85f * f);
            yl += AIR_TUCK_L * lmax * tuck;
            yr += AIR_TUCK_R * lmax * tuck;
            zl += (0.05f + 0.03f * f) * lmax * a;                  // pé da frente vai à frente para receber o chão
            zr -= (0.06f - 0.03f * f) * lmax * a;
            hipH -= 0.04f * lmax * a * (1f - f);
            tilt += (0.10f - 0.05f * f) * a;
            swingL = swingR = -1f;
        }
        if (motion.takeoff >= 0f && motion.takeoff < 1f) {          // rodada 09.3: impulso — agacha e estende no começo
            float u = motion.takeoff;
            float crouch = (1f - u) * (1f - u);                       // joelhos dobrados no instante do aperto, abrindo
            float push = (float) Math.sin(Math.PI * u);               // perna de trás estendida, ponta do pé para baixo
            hipH -= TAKEOFF_DIP * lmax * crouch;
            yl -= AIR_TUCK_L * lmax * Math.min(1f, motion.air) * (1f - u) * 0.9f;   // ainda não recolheu
            yr -= AIR_TUCK_R * lmax * Math.min(1f, motion.air) * (1f - u) * 0.9f;
            zr -= 0.07f * lmax * push;
            zl += 0.02f * lmax * push;
            tilt += 0.10f * crouch + 0.04f * push;
        }
        if (motion.land > 0f) {
            float d = Math.clamp(motion.land, 0f, 1f);
            float run = Math.clamp(motion.landRun, 0f, 1f);          // correndo: absorve menos e segue
            hipH -= LAND_DIP * lmax * d * (1f - 0.45f * run);
            tilt += (0.08f + 0.06f * run) * d;
            zl += 0.03f * lmax * d * (1f - run);                    // parado: um pé à frente segurando o corpo
            zr -= 0.03f * lmax * d * (1f - run);
        }

        // chão irregular: cada pé procura a altura do terreno; o quadril acompanha o pé mais baixo
        float gl = swingGround(lL.px, zl, swingL), gr = swingGround(lR.px, zr, swingR);
        float lmaxLeg = l1 + l2;
        if (Math.abs(gl - gr) > STEP_LIMIT * lmaxLeg) {            // degrau fora do alcance: o pé alto fica no limite
            if (gl > gr) gl = gr + STEP_LIMIT * lmaxLeg; else gr = gl + STEP_LIMIT * lmaxLeg;
        }
        if (spinNow != 0f) gl = gr = 0f;                                 // rodando: os pés não procuram o chão
        hipH += Math.min(gl, gr);
        if (rollNow != 0f) {                                             // rodada 09.3: inclinação lateral gira em torno do meio
            float sn = (float) Math.sin(rad(rollNow));                   // dos pés: sobe o quanto o pé do lado dela desceria
            float footL = lL.px + xl, footR = lR.px + xr;
            float up = Math.max(0f, Math.max(footL * sn, footR * sn)) + 2.5f * m.voxelSize * Math.abs(sn);   // + a espessura da canela
            hipH += up;                                                  // o corpo inteiro sobe (quadril E pés, como o lift):
            yl += up;                                                    // só o quadril dobraria os joelhos e o pé continuaria
            yr += up;                                                    // descendo com a inclinação
        }
        float rootTilt = tilt + lean;
        root(0, (hipH - hip) / h, rootZ / h, rootTilt, rootYaw, -rad(rollNow));   // rotateZ + inclina para a direita: + = esquerda
        // o quadril gira com a inclinação da raiz (em torno dos pés): desloca o alvo relativo
        float hz = lL.pz + hip * rootTilt;
        legIK("leg_l", "shin_l", hipH, hz, ankle + yl + gl, zl - rootZ, l1, l2, rootTilt);
        legIK("leg_r", "shin_r", hipH, hz, ankle + yr + gr, zr - rootZ, l1, l2, rootTilt);
        // rodada 09.2 — pé para o lado: a coxa abre (rz) o quanto leva o tornozelo ao deslocamento lateral pedido
        if (xl != 0f) rot("leg_l", 0, 0, (float) Math.atan2(xl, Math.max(0.05f, hipH - ankle - yl - gl)));
        if (xr != 0f) rot("leg_r", 0, 0, (float) Math.atan2(xr, Math.max(0.05f, hipH - ankle - yr - gr)));
        // rodada 09.2 — rolamento: o corpo inteiro gira em torno do centro da bola (prof.spinCenter), não da sola
        if (spinNow != 0f) spinRoot(rad(spinNow), (spinPy > 0f ? spinPy : prof.spinCenter) * cvs, spinPz * cvs, rad(spinAx));
        secondary(clip, t, mode, weaponRest);
        if (spinNow != 0f) rollContact();
        if (choreo) fxToModel();
    }

    /** Rodada 09.2 — volumes do rig (meta.volumes, voxels): parte, a, b, raio. Para o contato do rolamento. */
    private float[][] vols;
    private Matrix4f[] contactMats;

    /**
     * Rodada 09.2 — rolamento como uma bola de verdade: o ponto mais baixo do corpo (volumes do rig, itens incluídos)
     * fica no chão enquanto gira — nem afunda (cabeça grande atravessando o relevo) nem flutua. O peso sobe com o giro e
     * some perto de 0°/360° (em pé o contato já é o dos pés).
     */
    private void rollContact() {
        if (vols == null) {
            java.util.List<float[]> l = new ArrayList<>();
            if (m.meta.has("volumes"))
                for (var e : m.meta.getAsJsonArray("volumes")) {
                    var o = e.getAsJsonObject();
                    Integer pi = m.index.get(o.get("part").getAsString());
                    if (pi == null) continue;
                    var a = o.getAsJsonArray("a");
                    var b = o.has("b") ? o.getAsJsonArray("b") : a;
                    String part = o.get("part").getAsString();
                    float isItem = part.equals("weapon") || part.equals("shield") || part.equals("bow") || part.equals("arrow") ? 1f : 0f;
                    l.add(new float[]{pi, a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                            b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat(), o.get("r").getAsFloat(), isItem});
                }
            vols = l.toArray(new float[0][]);
            contactMats = new Matrix4f[m.parts.size()];
            for (int i = 0; i < contactMats.length; i++) contactMats[i] = new Matrix4f();
        }
        if (vols.length == 0) return;
        float k = Math.clamp((Math.abs(spinNow) - 4f) / 26f, 0f, 1f);
        if (k <= 0f) return;
        compose(p, mT.identity(), contactMats);
        float vs = m.voxelSize, body = Float.MAX_VALUE, item = Float.MAX_VALUE;
        for (float[] v : vols) {
            Matrix4f mm = contactMats[(int) v[0]];
            float ya = mm.m01() * v[1] * vs + mm.m11() * v[2] * vs + mm.m21() * v[3] * vs + mm.m31();
            float yb = mm.m01() * v[4] * vs + mm.m11() * v[5] * vs + mm.m21() * v[6] * vs + mm.m31();
            float lo = Math.min(ya, yb) - v[7] * vs;
            if (v[8] != 0f) item = Math.min(item, lo); else body = Math.min(body, lo);
        }
        float min = Math.min(body, item);                                // o mais baixo encosta: corpo, ou o item se estiver abaixo dele
        p.v[m.parts.size() * 9 + 4] -= min * k / m.height;
    }

    /** Arco (1,6 s): virar de lado, puxar a flecha da aljava, erguer, puxar a corda, segurar, soltar, recuperar. */
    private float[] bowShot2(float t) {
        float u = frac(t / 1.6f);
        float turn = smooth(0.00f, 0.14f, u) - smooth(0.86f, 1.00f, u);
        float reach = smooth(0.10f, 0.20f, u) - smooth(0.24f, 0.34f, u);      // mão direita vai à aljava
        float raise = smooth(0.26f, 0.40f, u) - smooth(0.84f, 1.00f, u);
        float draw = smooth(0.40f, 0.64f, u) - smooth(0.70f, 0.715f, u);      // solta de repente
        float rel = smooth(0.70f, 0.74f, u) - smooth(0.78f, 0.98f, u);        // braço recua após soltar
        boolean arrow = u > 0.30f && u < 0.70f;
        rot("torso", 0.02f, -0.60f * turn, 0);
        rot("head", 0.04f * raise, 0.55f * turn, 0);
        rot("arm_l", -1.50f * raise, 0.60f * raise, 0.10f);
        rot("farm_l", -0.10f * raise - 0.2f * (1 - raise), 0, 0);
        setRx("bow", 1.60f * raise);
        rot("bow", 0, 0.6f * (1 - raise), 0);
        // mão da corda: do arco (braço à frente) até o queixo (braço de lado, cotovelo dobrado)
        rot("arm_r", -2.6f * reach - 1.45f * raise * (1 - draw) + 0.30f * rel, 0.60f * raise * (1 - draw), -1.40f * raise * draw - 0.1f);
        rot("farm_r", -1.4f * reach - 1.35f * draw * raise + 0.9f * rel - 0.2f, 0, 0);
        scale("arrow", arrow ? 1 : 0, arrow ? 1 : 0, arrow ? 1 : 0);
        off("arrow", 0, 0, -0.10f * draw / m.height);
        rot("tail", 0.15f + 0.3f * rel, 0, 0);
        rot("torso", -0.06f * rel, 0, 0);
        return new float[]{0.07f * turn + 0.02f, -0.05f * turn - 0.015f, 0.90f - 0.02f * raise, -0.015f * rel};
    }

    /** Cajado (1,3 s): erguer com o braço aberto, firmar os pés, apontar para a frente e recuperar. */
    private float[] staffCast2(float t, float weaponRest) {
        float u = frac(t / 1.3f);
        float wind = smooth(0.00f, 0.32f, u) - smooth(0.32f, 0.44f, u);
        float cast = smooth(0.32f, 0.44f, u) - smooth(0.66f, 0.98f, u);
        float armR = -2.3f * wind - 1.30f * cast, farmR = -0.35f * wind - 0.05f * cast - 0.2f;
        rot("arm_r", armR, 0, -0.55f * wind);
        rot("farm_r", farmR, 0, 0);
        rot("weapon", weaponRest - (armR + farmR) * (0.95f - 0.6f * cast), 0, 0);
        rot("arm_l", 0.2f * wind - 1.25f * cast, 0, 0.25f * wind + 0.30f * cast);
        rot("farm_l", -1.1f * wind - 0.15f * cast - 0.2f, 0, 0);           // mão junto ao peito, depois aberta
        rot("torso", -0.10f * wind + 0.18f * cast, 0.25f * wind - 0.20f * cast, 0);
        rot("head", 0.08f * wind - 0.10f * cast, 0, 0);
        rot("hat", -0.12f * cast + 0.05f * wind, 0, 0);
        scale("torso", 1, 1 + 0.03f * wind - 0.03f * cast, 1);
        return new float[]{0.03f + 0.06f * cast, -0.04f, 0.92f - 0.08f * cast, 0.05f * cast};
    }

    // ------------------------------------------------------------------ coreografia (rodada 04)

    private final float[] legs = new float[8];
    private final float[] ctl = new float[Choreo.N], actA = new float[Choreo.N], actB = new float[Choreo.N];
    private final int[] ctlD = new int[Choreo.ND], actDA = new int[Choreo.ND], actDB = new int[Choreo.ND];
    private float[] actC;
    private int[] actDC;

    // ---- movimento secundário das ações (rodada 05.1) ----
    /** Atraso estilizado (s): partes secundárias reagem ao tronco/raiz de LAG segundos atrás. */
    static final float SEC_LAG = 0.09f;
    /** Liga/desliga a camada (comparação antes/depois: -Dvx.secondary=false). */
    static boolean secondaryOn = !"false".equals(System.getProperty("vx.secondary"));
    private final float[] lagA = new float[Choreo.N];
    private final int[] lagDA = new int[Choreo.ND];
    /** Diferenças "atrasado − atual" do golpe, já ponderadas pelo peso da ação: pitch, yaw, roll (rad), raiz z, elevação (voxels). */
    private float secP, secY, secR, secZ, secL;
    private boolean secActive;
    private static final float[] NO_CARRY = Choreo.defaults();
    private static final int[] NO_CARRY_D = {Choreo.LM_FREE, Choreo.AR_HIDDEN};
    // temporários (sem alocação por quadro)
    private final Matrix4f mA = new Matrix4f(), mB = new Matrix4f(), mC = new Matrix4f(), mT = new Matrix4f();
    private final Matrix4f mUp = new Matrix4f(), mFore = new Matrix4f(), mItemR = new Matrix4f(), mItemL = new Matrix4f();
    private final Matrix3f r3a = new Matrix3f(), r3b = new Matrix3f();
    private final Vector3f va = new Vector3f(), vb = new Vector3f(), vc = new Vector3f(), vd = new Vector3f(),
            ve = new Vector3f(), vf = new Vector3f(), vg = new Vector3f(), handR = new Vector3f(), handL = new Vector3f(),
            elbowR = new Vector3f(), elbowL = new Vector3f();
    private final int[] chainIdx = new int[16];

    /** Pontos de controle do último evaluate, no espaço da RAIZ (unidades de mundo), para depuração e QA. */
    public final Vector3f dbgHandR = new Vector3f(), dbgHandL = new Vector3f(), dbgTip = new Vector3f(), dbgEmit = new Vector3f(),
            dbgTargetR = new Vector3f(), dbgTargetL = new Vector3f();
    public boolean dbgHasEmit;

    private static float rad(float deg) { return deg * (float) (Math.PI / 180.0); }

    /**
     * Camada de ação: parte da pose de TRANSPORTE do perfil (como a arma é levada em repouso/caminhada/corrida,
     * com o balanço da passada), mistura a AÇÃO pelo peso da Motion e resolve tronco, braços e itens.
     */
    private void actionLayer(Clip base, float t, float ph, float ankle, float lmax) {
        float vs = cvs;                                                   // voxel da coreografia (escala do rig)
        String cp = prof.carryPose(base);
        System.arraycopy(cp != null ? prof.pose(cp) : NO_CARRY, 0, ctl, 0, Choreo.N);
        System.arraycopy(cp != null ? prof.poseD(cp) : NO_CARRY_D, 0, ctlD, 0, Choreo.ND);
        boolean moving = base == Clip.WALK || base == Clip.RUN;
        if (moving) {
            float c = cos(ph), sw = base == Clip.RUN ? prof.swingRun : prof.swingWalk;
            ctl[Choreo.R_POS + 2] += sw * c;                       // mão direita vai à frente com o pé esquerdo
            ctl[Choreo.L_POS + 2] -= sw * c;
            ctl[Choreo.R_POS + 1] += 0.35f * Math.abs(c);
            ctl[Choreo.L_POS + 1] += 0.35f * Math.abs(c);
        } else {
            float b = 0.25f * sin(t * 2.2f);                       // respiração
            ctl[Choreo.R_POS + 1] += b;
            ctl[Choreo.L_POS + 1] += b;
        }
        // mãos livres no transporte seguem o balanço FK da locomoção
        boolean cFreeR = prof.carryFree == 'r', cFreeL = prof.carryFree == 'l' || ctlD[Choreo.D_LMODE] == Choreo.LM_FREE;
        boolean aFreeR = cFreeR, aFreeL = cFreeL;
        float w = 0f;
        Choreo.Move mv = motion.move != null ? prof.moves.get(motion.move) : null;
        if (mv != null && motion.weight > 0f) {
            prof.sample(mv, motion.moveT, actA, actDA);
            Choreo.Move from = motion.fromMove != null ? prof.moves.get(motion.fromMove) : null;
            if (from != null && motion.xfade < 1f) {
                prof.sample(from, motion.fromT, actB, actDB);
                Choreo.Move from2 = motion.fromMove2 != null ? prof.moves.get(motion.fromMove2) : null;
                if (from2 != null && motion.xfade2 < 1f) {                  // rodada 09: origem que ainda misturava
                    if (actC == null) { actC = new float[Choreo.N]; actDC = new int[Choreo.ND]; }
                    prof.sample(from2, motion.fromT2, actC, actDC);
                    float x2 = motion.xfade2 * motion.xfade2 * (3 - 2 * motion.xfade2);
                    Choreo.lerp(actC, actB, x2, actB);
                    if (x2 < 0.5f) System.arraycopy(actDC, 0, actDB, 0, Choreo.ND);
                }
                float x = motion.xfade * motion.xfade * (3 - 2 * motion.xfade);
                Choreo.lerp(actB, actA, x, actA);
                if (x < 0.5f) System.arraycopy(actDB, 0, actDA, 0, Choreo.ND);
            }
            w = Math.clamp(motion.weight, 0f, 1f);
            w = w * w * (3 - 2 * w);
            aFreeR = false;
            aFreeL = actDA[Choreo.D_LMODE] == Choreo.LM_FREE;
            legs[0] += (actA[Choreo.FEET] * vs - legs[0]) * w;
            legs[1] += (actA[Choreo.FEET + 1] * vs - legs[1]) * w;
            legs[2] *= 1 - w;
            legs[3] *= 1 - w;
            legs[4] += (ankle + lmax * actA[Choreo.HIP] - legs[4]) * w;
            legs[5] += (actA[Choreo.ROOTZ] * vs - legs[5]) * w;
            legs[6] += (rad(actA[Choreo.TILT]) - legs[6]) * w;
            legs[7] = rad(actA[Choreo.YAW]) * w;
            float lift = actA[Choreo.LIFT] * vs * w;                // pulo: quadril e pés sobem juntos
            legs[4] += lift;
            legs[2] += lift;
            legs[3] += lift;
            legs[2] += actA[Choreo.FOOTY] * vs * w;                 // rodada 09.2: pés recolhidos (rolamento)
            legs[3] += actA[Choreo.FOOTY + 1] * vs * w;
            spinNow = Choreo.wrapDeg(actA[Choreo.SPIN]) * w;
            rollNow = actA[Choreo.ROLLZ] * w;                        // rodada 09.3: esquiva de lado
            feetXl = actA[Choreo.FEETX] * vs * w;
            feetXr = actA[Choreo.FEETX + 1] * vs * w;
            spinPy = actA[Choreo.SPINP];
            spinPz = actA[Choreo.SPINP + 1];
            spinAx = actA[Choreo.SPINA];
        } else System.arraycopy(ctl, 0, actA, 0, Choreo.N);
        // lado livre: troca o alvo autorado pela posição/polo atuais por FK antes de misturar
        if (cFreeR || aFreeR) fkHand("r", "farm_r", prof.rightItem, cFreeR ? ctl : null, aFreeR ? actA : null, Choreo.R_POS, Choreo.R_ELBOW);
        if (cFreeL || aFreeL) fkHand("l", "farm_l", prof.leftItem, cFreeL ? ctl : null, aFreeL ? actA : null, Choreo.L_POS, Choreo.L_ELBOW);
        Choreo.lerp(ctl, actA, w, ctl);
        if (w > 0.5f) System.arraycopy(actDA, 0, ctlD, 0, Choreo.ND);
        boolean ikR = !(cFreeR && (w < 1e-3f || aFreeR)), ikL = !(cFreeL && (w < 1e-3f || aFreeL));
        if (aFreeL && w > 0.999f) ikL = false;
        applyControl(ikR, ikL, mv);
        secondaryLag(mv, w);
    }

    /**
     * Rodada 05.1 — atraso das partes secundárias durante uma ação, sem estado e sem física: compara o tronco e
     * a raiz do golpe AGORA com SEC_LAG segundos antes (amostrando a mesma curva). Quando o golpe para, a
     * diferença volta a zero pela própria curva (recuperação progressiva, sem mola). Aplicado em secondary().
     */
    private void secondaryLag(Choreo.Move mv, float w) {
        secActive = false;
        secP = secY = secR = secZ = secL = 0f;
        if (!secondaryOn || mv == null || w <= 0f) return;
        prof.sample(mv, Math.max(0f, motion.moveT - SEC_LAG), lagA, lagDA);
        secP = rad(lagA[Choreo.TORSO] - actA[Choreo.TORSO]) * w;
        secY = rad(lagA[Choreo.TORSO + 1] - actA[Choreo.TORSO + 1]) * w;
        secR = rad(lagA[Choreo.TORSO + 2] - actA[Choreo.TORSO + 2]) * w;
        secZ = (lagA[Choreo.ROOTZ] - actA[Choreo.ROOTZ]) * w;
        secL = (lagA[Choreo.LIFT] - actA[Choreo.LIFT]) * w;
        secActive = true;
    }

    /** Aplica o atraso às partes que existirem; limites por parte (unilaterais onde a parte encostaria no corpo). */
    private void applySecondaryLag() {
        if (!secActive) return;
        secActive = false;                       // vale só para a avaliação corrente
        // trança/rabo (filho da cabeça): segue 80% do giro do tronco com atraso, recua no avanço da raiz
        if (has("tail"))
            rot("tail", Math.clamp(0.8f * secP - 0.09f * secZ - 0.04f * secL, -0.30f, 0.55f), 0,
                    Math.clamp(-0.5f * secY + 0.6f * secR, -0.30f, 0.30f));
        // chapéu (filho da cabeça): balança pouco, a aba não pode cobrir os olhos
        if (has("hat"))
            rot("hat", Math.clamp(0.35f * secP - 0.03f * secZ, -0.10f, 0.12f), 0, Math.clamp(0.35f * secR - 0.2f * secY, -0.10f, 0.10f));
        // mochila (pivô no alto das costas): só se AFASTA das costas (rx > 0 leva o fundo para trás)
        if (has("pack"))
            rot("pack", Math.clamp(0.45f * secP - 0.06f * secZ, 0f, 0.16f), 0, Math.clamp(0.35f * secR - 0.25f * secY, -0.08f, 0.08f));
        // pontas do cachecol (pivô no nó, à frente do peito): só vão para a FRENTE (rx < 0), balançam de lado
        if (has("scarf"))
            rot("scarf", Math.clamp(0.9f * secP + 0.10f * secZ, -0.45f, 0f), 0, Math.clamp(-0.9f * secY + 0.8f * secR, -0.35f, 0.35f));
    }

    /** Posição e polo da mão por FK (pose de locomoção atual) escritos nos vetores de controle indicados. */
    private void fkHand(String side, String fore, String item, float[] c1, float[] c2, int posCh, int elbowCh) {
        float vs = cvs;
        Vector3f h = new Vector3f(handWorldFk(fore, item, handPoint(side)));
        CharacterModel.Part f = part(fore), u = part(fore.replace("farm", "arm"));
        Vector3f e = chain(m.index.get(fore), mA).transformPosition(new Vector3f(f.px, f.py, f.pz));
        Vector3f s = chain(u.parent, mB).transformPosition(new Vector3f(u.px, u.py, u.pz));
        Vector3f pole = new Vector3f(e).sub(new Vector3f(h).add(s).mul(0.5f));
        if (pole.lengthSquared() < 1e-10f) pole.set(side.equals("r") ? -1 : 1, -0.5f, -1);
        pole.normalize();
        for (float[] c : new float[][]{c1, c2}) {
            if (c == null) continue;
            for (int k = 0; k < 3; k++) {
                c[posCh + k] = h.get(k) / vs;
                c[elbowCh + k] = pole.get(k);
            }
        }
    }

    /** Escreve tronco, cabeça, braços (IK), itens, corda/flecha e efeitos a partir de ctl. */
    private void applyControl(boolean ikR, boolean ikL, Choreo.Move mv) {
        float vs = cvs;
        float tp = rad(ctl[Choreo.TORSO]), ty = rad(ctl[Choreo.TORSO + 1]), tr = rad(ctl[Choreo.TORSO + 2]);
        rot("torso", tp, ty, tr);
        rot("head", rad(ctl[Choreo.HEAD]) - 0.45f * tp, rad(ctl[Choreo.HEAD + 1]) - 0.8f * ty, rad(ctl[Choreo.HEAD + 2]) - 0.6f * tr);

        if (ikR) {
            dbgTargetR.set(ctl[Choreo.R_POS], ctl[Choreo.R_POS + 1], ctl[Choreo.R_POS + 2]).mul(vs);
            reachErrR = solveArm("arm_r", "farm_r", prof.rightItem, "r", dbgTargetR,
                    new Vector3f(ctl[Choreo.R_ELBOW], ctl[Choreo.R_ELBOW + 1], ctl[Choreo.R_ELBOW + 2]),
                    new Vector3f(ctl[Choreo.R_DIR], ctl[Choreo.R_DIR + 1], ctl[Choreo.R_DIR + 2]),
                    new Vector3f(ctl[Choreo.R_EDGE], ctl[Choreo.R_EDGE + 1], ctl[Choreo.R_EDGE + 2]), handR, mItemR) / vs;
        } else handR.set(handWorldFk("farm_r", prof.rightItem, handPoint("r")));
        if (ikL) {
            dbgTargetL.set(ctl[Choreo.L_POS], ctl[Choreo.L_POS + 1], ctl[Choreo.L_POS + 2]).mul(vs);
            float gw = Math.clamp(ctl[Choreo.GRIP_W], 0f, 1f);
            if (gw > 0f) {
                // duas mãos: a esquerda segura o cabo abaixo da direita, ao longo do eixo do item
                Vector3f grip = new Vector3f(ctl[Choreo.R_DIR], ctl[Choreo.R_DIR + 1], ctl[Choreo.R_DIR + 2]).mul(-ctl[Choreo.L_GRIP] * vs).add(handR);
                dbgTargetL.lerp(grip, gw * gw * (3 - 2 * gw));
            }
            reachErrL = solveArm("arm_l", "farm_l", prof.leftItem, "l", dbgTargetL,
                    new Vector3f(ctl[Choreo.L_ELBOW], ctl[Choreo.L_ELBOW + 1], ctl[Choreo.L_ELBOW + 2]),
                    new Vector3f(ctl[Choreo.L_DIR], ctl[Choreo.L_DIR + 1], ctl[Choreo.L_DIR + 2]),
                    new Vector3f(ctl[Choreo.L_EDGE], ctl[Choreo.L_EDGE + 1], ctl[Choreo.L_EDGE + 2]), handL, mItemL) / vs;
        } else handL.set(handWorldFk("farm_l", prof.leftItem, handPoint("l")));
        dbgHandR.set(handR);
        dbgHandL.set(handL);
        if (prof.rightItem != null && has(prof.rightItem) && prof.tipLength > 0)
            chain(m.index.get(prof.rightItem), mA).transformPosition(itemTip(prof.rightItem, dbgTip));

        // arco: corda em V até a mão direita e flecha da mão/corda até o descanso do arco
        if (has("bow")) {
            CharacterModel.Part bp = part("bow");
            chain(m.index.get("bow"), mA).transformPosition(dbgTip.set(bp.px - 0.5f * vs, bp.py + 17f * vs, 0f));
            bowRig(ctlD[Choreo.D_ARROW], ctl[Choreo.DRAW], mv);
        }
        // cajado: brilho de concentração no cristal e orbe emitido
        dbgHasEmit = false;
        float glow = ctl[Choreo.GLOW];
        if (prof.emit != null && prof.rightItem != null && has(prof.rightItem)) {
            chain(m.index.get(prof.rightItem), mA).transformPosition(dbgEmit.set(prof.emit[0], prof.emit[1], prof.emit[2]).mul(vs));
            dbgHasEmit = true;
            if (glow > 0.02f) {
                Fx g = new Fx(Fx.GLOW);
                g.m.translation(dbgEmit);
                g.size = glow;
                fx.add(g);
            }
        }
        if (mv != null && !Float.isNaN(mv.emit) && motion.moveT >= mv.emit && motion.weight > 0.5f) {
            float dt = motion.moveT - mv.emit, life = 0.9f;
            if (dt < life) {
                Matrix4f origin = releaseOrigin(mv, mv.emit - 1e-3f, true);
                Fx o = new Fx(Fx.ORB);
                o.m.set(origin).translateLocal(0, 0, prof.orbSpeed * dt);
                o.size = Math.min(1f, 0.45f + dt * 4f) * (1f - smooth(life * 0.7f, life, dt));
                fx.add(o);
            }
        }
    }

    /** Ponto da mão (meta.hands, voxels → mundo) no espaço de bind. */
    private Vector3f handPoint(String side) {
        Vector3f out = side.equals("r") ? vf : vg;
        if (m.meta.has("hands")) {
            var a = m.meta.getAsJsonObject("hands").getAsJsonArray(side);
            return out.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()).mul(m.voxelSize);
        }
        return out.set(side.equals("r") ? -7f : 7f, 13f, 1f).mul(m.voxelSize);
    }

    /** Efetuador: o pivô do item segurado (é ali que ele se prende) ou o ponto da mão. */
    private Vector3f effector(String item, Vector3f handBind, Vector3f out) {
        if (item != null && has(item)) {
            CharacterModel.Part it = part(item);
            return out.set(it.px, it.py, it.pz);
        }
        return out.set(handBind);
    }

    private Vector3f handWorldFk(String fore, String item, Vector3f handBind) {
        effector(item, handBind, vd);
        return chain(m.index.get(fore), mA).transformPosition(vd);
    }

    /**
     * IK analítico de dois ossos (ombro → cotovelo → mão) no espaço da raiz. O cotovelo fica no plano que
     * contém o polo (preferência de dobra: para fora/trás, nunca para dentro do tronco); o antebraço gira só
     * no eixo da dobra. Se houver item, ele é orientado por dir (+Y do item) e fio (+X do item).
     * Retorna o erro de alcance (distância alvo–mão, em unidades de mundo).
     */
    private float solveArm(String up, String fore, String item, String side, Vector3f target, Vector3f pole,
                           Vector3f dir, Vector3f edge, Vector3f handOut, Matrix4f itemOut) {
        int iu = m.index.get(up), iFo = m.index.get(fore);
        CharacterModel.Part pu = m.parts.get(iu), pf = m.parts.get(iFo);
        Matrix4f mt = chain(pu.parent, mT);
        Vector3f sb = new Vector3f(pu.px, pu.py, pu.pz), eb = new Vector3f(pf.px, pf.py, pf.pz);
        Vector3f hb = effector(item, handPoint(side), new Vector3f());
        Vector3f S = mt.transformPosition(new Vector3f(sb));
        float l1 = eb.distance(sb), l2 = hb.distance(eb);
        Vector3f d = new Vector3f(target).sub(S);
        float dist = d.length(), err = Math.max(0f, dist - (l1 + l2));
        float D = Math.clamp(dist, Math.abs(l1 - l2) + 1e-4f, (l1 + l2) * 0.999f);
        Vector3f dn = dist > 1e-6f ? d.normalize() : new Vector3f(0, -1, 0);
        Vector3f perp = new Vector3f(pole).sub(new Vector3f(dn).mul(pole.dot(dn)));
        if (perp.lengthSquared() < 1e-8f) perp.set(0, 0, -1).sub(new Vector3f(dn).mul(-dn.z));
        perp.normalize();
        float a = (l1 * l1 - l2 * l2 + D * D) / (2 * D), hgt = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
        Vector3f E = new Vector3f(S).add(new Vector3f(dn).mul(a)).add(new Vector3f(perp).mul(hgt));
        Vector3f T = new Vector3f(S).add(new Vector3f(dn).mul(D));
        Vector3f n1 = new Vector3f(dn).cross(perp).negate().normalize();
        // bind: braço quase reto, cotovelo "aponta" para trás
        Vector3f dir0 = new Vector3f(hb).sub(sb).normalize();
        Vector3f back = new Vector3f(0, 0, -1);
        Vector3f perp0 = new Vector3f(back).sub(new Vector3f(dir0).mul(back.dot(dir0))).normalize();
        Vector3f n0 = new Vector3f(dir0).cross(perp0).negate().normalize();
        Matrix3f rUp = basisRot(new Vector3f(eb).sub(sb), n0, new Vector3f(E).sub(S), n1);
        Matrix3f rFo = basisRot(new Vector3f(hb).sub(eb), n0, new Vector3f(T).sub(E), n1);
        // braço: gira em torno do ombro; antebraço: em torno do cotovelo
        Matrix4f dUp = new Matrix4f().translation(S).mul(new Matrix4f().set(rUp)).translate(-sb.x, -sb.y, -sb.z);
        setLocal(iu, new Matrix4f(mt).invert().mul(dUp));
        Matrix4f dFo = new Matrix4f().translation(E).mul(new Matrix4f().set(rFo)).translate(-eb.x, -eb.y, -eb.z);
        setLocal(iFo, new Matrix4f(dUp).invert().mul(dFo));
        handOut.set(T);
        if (item != null && has(item)) {
            int ii = m.index.get(item);
            Vector3f y = new Vector3f(dir).normalize();
            Vector3f x = new Vector3f(edge).sub(new Vector3f(y).mul(edge.dot(y)));
            if (x.lengthSquared() < 1e-8f) x.set(1, 0, 0).sub(new Vector3f(y).mul(y.x));
            x.normalize();
            Vector3f z = new Vector3f(x).cross(y);
            Matrix3f rI = new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
            Matrix4f dI = new Matrix4f().translation(T).mul(new Matrix4f().set(rI)).translate(-hb.x, -hb.y, -hb.z);
            setLocal(ii, new Matrix4f(dFo).invert().mul(dI));
            itemOut.set(dI);
        }
        return err;
    }

    /** Rotação que leva a base (a0, n0) na base (a1, n1): R = B1·B0ᵀ, com B = [â, n̂⊥, â×n̂]. */
    private static Matrix3f basisRot(Vector3f a0, Vector3f n0, Vector3f a1, Vector3f n1) {
        return basis(a1, n1).mul(basis(a0, n0).transpose());
    }

    private static Matrix3f basis(Vector3f a, Vector3f n) {
        Vector3f x = new Vector3f(a).normalize();
        Vector3f y = new Vector3f(n).sub(new Vector3f(x).mul(n.dot(x)));
        if (y.lengthSquared() < 1e-10f) y.set(Math.abs(x.x) < 0.9f ? 1 : 0, Math.abs(x.x) < 0.9f ? 0 : 1, 0).sub(new Vector3f(x).mul(x.x));
        y.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
    }

    /** Matriz local desejada → parâmetros da parte (Euler ZYX + deslocamento; escala preservada = 1). */
    private void setLocal(int i, Matrix4f L) {
        CharacterModel.Part pt = m.parts.get(i);
        Vector3f e = L.getEulerAnglesZYX(new Vector3f());
        Vector3f rp = L.transformDirection(new Vector3f(pt.px, pt.py, pt.pz));
        float h = m.height;
        int k = i * 9;
        p.v[k] = e.x;
        p.v[k + 1] = e.y;
        p.v[k + 2] = e.z;
        p.v[k + 3] = (L.m30() - pt.px + rp.x) / h;
        p.v[k + 4] = (L.m31() - pt.py + rp.y) / h;
        p.v[k + 5] = (L.m32() - pt.pz + rp.z) / h;
    }

    /** Matriz local da parte i a partir da pose corrente. */
    private Matrix4f local(int i, Matrix4f dst) {
        CharacterModel.Part pt = m.parts.get(i);
        int k = i * 9;
        float h = m.height;
        return dst.translation(pt.px + p.v[k + 3] * h, pt.py + p.v[k + 4] * h, pt.pz + p.v[k + 5] * h)
                .rotateZ(p.v[k + 2]).rotateY(p.v[k + 1]).rotateX(p.v[k])
                .scale(p.v[k + 6], p.v[k + 7], p.v[k + 8])
                .translate(-pt.px, -pt.py, -pt.pz);
    }

    /** Matriz da parte no espaço da raiz (cadeia de pais); i < 0 = identidade (a própria raiz). */
    private Matrix4f chain(int i, Matrix4f dst) {
        int n = 0;
        for (int j = i; j >= 0; j = m.parts.get(j).parent) chainIdx[n++] = j;
        dst.identity();
        for (int q = n - 1; q >= 0; q--) dst.mul(local(chainIdx[q], mC));
        return dst;
    }

    /** Ponta do item (bind): pivô + tipLength ao longo de +Y. */
    private Vector3f itemTip(String item, Vector3f out) {
        CharacterModel.Part it = part(item);
        return out.set(it.px, it.py + prof.tipLength * m.voxelSize, it.pz);
    }

    /** Arco: corda em V até a mão (DRAW), flecha na mão ou na corda, soltura como efeito. */
    private void bowRig(int arrowState, float draw, Choreo.Move mv) {
        float vs = m.voxelSize;
        Matrix4f bowM = chain(m.index.get("bow"), new Matrix4f());
        Matrix4f inv = new Matrix4f(bowM).invert();
        Vector3f handB = inv.transformPosition(new Vector3f(handR));          // mão direita no espaço do arco
        CharacterModel.Part bp = part("bow");
        Vector3f restB = prof.rest != null ? new Vector3f(prof.rest).mul(vs) : new Vector3f(bp.px, bp.py + vs, bp.pz);
        Vector3f mid = new Vector3f(bp.px, bp.py, 0.5f * vs);                // meio da corda em repouso
        Vector3f nockB = new Vector3f(mid).lerp(handB, Math.clamp(draw, 0f, 1f));
        for (String sp : new String[]{"string_u", "string_d"}) {
            if (!has(sp)) continue;
            CharacterModel.Part s = part(sp);
            Vector3f piv = new Vector3f(s.px, s.py, s.pz);
            Vector3f rest = new Vector3f(mid).sub(piv);
            Vector3f want = new Vector3f(nockB).sub(piv);
            float len0 = rest.length(), len1 = want.length();
            Matrix4f L = new Matrix4f().translation(piv).rotate(new org.joml.Quaternionf().rotationTo(rest.normalize(), want.normalize()))
                    .translate(-piv.x, -piv.y, -piv.z);
            int i = m.index.get(sp);
            setLocal(i, L);
            // estica ao longo do próprio eixo: escala Y no espaço local (a metade é vertical no bind)
            p.v[i * 9 + 7] = len0 > 1e-6f ? len1 / len0 : 1f;
        }
        boolean released = mv != null && !Float.isNaN(mv.release) && motion.moveT >= mv.release && motion.weight > 0.5f;
        if (has("arrow")) {
            int ai = m.index.get("arrow");
            CharacterModel.Part ap = part("arrow");
            if (arrowState == Choreo.AR_HIDDEN || released) {
                scale("arrow", 0, 0, 0);
            } else {
                // bind da flecha: entalhe (nock) em (9,5; 14,5; 0,5) voxels, ponta para +Z
                Vector3f nockBind = new Vector3f(9.5f, 14.5f, 0.5f).mul(vs);
                Vector3f nockW, dirW;
                if (arrowState == Choreo.AR_STRING) {
                    nockW = bowM.transformPosition(new Vector3f(nockB));
                    Vector3f restW = bowM.transformPosition(new Vector3f(restB));
                    dirW = restW.sub(nockW).normalize();
                } else {
                    nockW = new Vector3f(handR);
                    dirW = new Vector3f(ctl[Choreo.R_DIR], ctl[Choreo.R_DIR + 1], ctl[Choreo.R_DIR + 2]).normalize();
                }
                Vector3f upW = bowM.transformDirection(new Vector3f(0, 1, 0)).normalize();
                Matrix4f A = arrowMatrix(nockW, dirW, upW, nockBind);
                setLocal(ai, new Matrix4f(inv).mul(A));
            }
        }
        if (released) {
            float dt = motion.moveT - mv.release, life = 0.75f;
            if (dt < life) {
                Matrix4f origin = releaseOrigin(mv, mv.release - 1e-3f, false);
                Fx a = new Fx(Fx.ARROW);
                a.m.set(origin).translateLocal(0, -1.2f * dt * dt, 0);
                Vector3f fwd = a.m.transformDirection(new Vector3f(0, 0, 1)).normalize();
                a.m.translateLocal(fwd.mul(prof.arrowSpeed * dt));
                a.size = 1f - smooth(life * 0.8f, life, dt);
                fx.add(a);
            }
        }
    }

    /** Matriz que leva o entalhe de bind até nockW e o eixo +Z da flecha até dirW (rolagem pelo "cima" do arco). */
    private static Matrix4f arrowMatrix(Vector3f nockW, Vector3f dirW, Vector3f upW, Vector3f nockBind) {
        Vector3f z = new Vector3f(dirW).normalize();
        Vector3f y = new Vector3f(upW).sub(new Vector3f(z).mul(upW.dot(z)));
        if (y.lengthSquared() < 1e-8f) y.set(0, 1, 0);
        y.normalize();
        Vector3f x = new Vector3f(y).cross(z);
        Matrix3f r = new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
        return new Matrix4f().translation(nockW).mul(new Matrix4f().set(r)).translate(-nockBind.x, -nockBind.y, -nockBind.z);
    }

    private final java.util.Map<String, Matrix4f> releaseCache = new java.util.HashMap<>();

    /**
     * Origem de um efeito no instante do evento, no espaço do MODELO (pés no chão): avalia o golpe sozinho
     * naquele instante numa pose de rascunho. Flecha: matriz da flecha na soltura (o voo segue o eixo dela).
     * Orbe: no cristal, apontando para a frente do personagem (+Z), com o tronco já girado.
     */
    private Matrix4f releaseOrigin(Choreo.Move mv, float at, boolean orb) {
        String key = mv.name + (orb ? "#orb" : "#arrow");
        Matrix4f cached = releaseCache.get(key);
        if (cached != null) return cached;
        Pose save = p;
        Motion saveM = motion;
        Pose scratch = newPose();
        Motion mo = new Motion().set(Clip.IDLE, 0);
        mo.move = mv.name;
        mo.moveT = at;
        mo.weight = 1f;
        motion = mo;
        Matrix4f saveW = world;
        Ground saveG = ground;
        world = null;
        ground = null;
        List<Fx> saveFx = new ArrayList<>(fx);
        float[] sLegs = legs.clone(), sCtl = ctl.clone();
        int[] sCtlD = ctlD.clone();
        Vector3f[] sv = {new Vector3f(handR), new Vector3f(handL), new Vector3f(dbgHandR), new Vector3f(dbgHandL),
                new Vector3f(dbgTip), new Vector3f(dbgEmit), new Vector3f(dbgTargetR), new Vector3f(dbgTargetL)};
        float sErrR = reachErrR, sErrL = reachErrL;
        boolean sHasEmit = dbgHasEmit;
        p = scratch;
        p.reset();
        biped2(Clip.IDLE, 0f);
        Matrix4f rootM = rootMatrix(new Matrix4f());
        Matrix4f out;
        if (orb) {
            Vector3f pos = rootM.transformPosition(new Vector3f(dbgEmit));
            out = new Matrix4f().translation(pos);                        // voo reto para a frente do modelo
        } else {
            out = new Matrix4f(rootM).mul(chain(m.index.get("arrow"), new Matrix4f()));
        }
        p = save;
        motion = saveM;
        world = saveW;
        ground = saveG;
        fx.clear();
        fx.addAll(saveFx);
        System.arraycopy(sLegs, 0, legs, 0, legs.length);
        System.arraycopy(sCtl, 0, ctl, 0, ctl.length);
        System.arraycopy(sCtlD, 0, ctlD, 0, ctlD.length);
        handR.set(sv[0]); handL.set(sv[1]); dbgHandR.set(sv[2]); dbgHandL.set(sv[3]);
        dbgTip.set(sv[4]); dbgEmit.set(sv[5]); dbgTargetR.set(sv[6]); dbgTargetL.set(sv[7]);
        reachErrR = sErrR;
        reachErrL = sErrL;
        dbgHasEmit = sHasEmit;
        releaseCache.put(key, out);
        return out;
    }

    private float swingL = -1f, swingR = -1f, landZ = 0f;

    /** Chão sob o pé: no apoio, o da pegada; no balanço, mistura decolagem → pouso, nunca abaixo do caminho. */
    private float swingGround(float x, float z, float u) {
        float here = groundOffset(x, z);
        if (u < 0f) return here;
        float land = groundOffset(x, landZ);
        float e = u * u * (3 - 2 * u);
        float base = Math.max(here, here + (land - here) * e);           // decolagem → pouso, nunca dentro do chão
        return base + Math.abs(land - here) * 0.6f * (float) Math.sin(Math.PI * u);   // folga sobre a quina
    }

    /** Matriz da raiz (deslocamento e giro da pose corrente) — espaço do modelo → espaço da raiz. */
    private Matrix4f rootMatrix(Matrix4f dst) {
        int b = m.parts.size() * 9;
        float h = m.height;
        return dst.translation(p.v[b + 3] * h, p.v[b + 4] * h, p.v[b + 5] * h)
                .rotateZ(p.v[b + 2]).rotateY(p.v[b + 1]).rotateX(p.v[b]);
    }

    /** Leva os efeitos do espaço da raiz para o do modelo (chamado ao fim do bípede articulado). */
    private void fxToModel() {
        if (fx.isEmpty()) return;
        Matrix4f r = rootMatrix(new Matrix4f());
        for (Fx f : fx) if (f.kind == Fx.GLOW) f.m.set(new Matrix4f(r).mul(f.m));
    }

    // ------------------------------------------------------------------ criaturas
    private void quadruped(Clip clip, float t) {
        boolean moving = clip == Clip.WALK || clip == Clip.RUN;
        float speed = clip == Clip.RUN ? 2.4f : 1.3f;
        float ph = t * TAU * speed, s = sin(ph);
        float b = sin(t * 1.8f);
        if (moving) {
            float a = clip == Clip.RUN ? 0.7f : 0.45f;
            rot("leg_fl", -a * s, 0, 0);
            rot("leg_br", -a * s, 0, 0);
            rot("leg_fr", a * s, 0, 0);
            rot("leg_bl", a * s, 0, 0);
            root(0, 0.03f * (0.5f - 0.5f * cos(2 * ph)), 0, 0, 0, 0.03f * s);
            rot("head", 0.06f * cos(2 * ph), 0.05f * s, 0);
        } else {
            scale("body", 1, 1 + 0.02f * b, 1);
            rot("head", 0.06f * sin(t * 1.8f - 0.8f) + 0.12f * smooth(0.6f, 1f, sin(t * 0.5f)), 0.25f * sin(t * 0.45f), 0.05f * sin(t * 0.7f));
        }
        rot("tail", 0, 0.35f * sin(t * (moving ? 6f : 3.2f)), 0);
        rot("ear_l", 0.10f * sin(t * 2.3f), 0, 0.10f * sin(t * 1.7f) + 0.25f * smooth(0.93f, 1f, sin(t * 1.3f)));
        rot("ear_r", 0.10f * sin(t * 2.3f + 0.4f), 0, -0.10f * sin(t * 1.7f) - 0.25f * smooth(0.93f, 1f, sin(t * 1.3f + 1f)));
        if (clip == Clip.HIT) {
            float e = (float) Math.exp(-(t % 0.9f) * 6);
            squash(1 - 0.15f * e);
            root(0, 0, -0.1f * e, 0, 0, 0);
        }
    }

    private void hopper(Clip clip, float t) {
        if (m.meta.has("lean")) root(0, 0, 0, m.meta.get("lean").getAsFloat(), 0, 0);   // postura (fungo curvado)
        float period = clip == Clip.RUN ? 0.55f : (clip == Clip.WALK ? 0.8f : 1.9f);
        float u = (t % period) / period;
        float hopWindow = clip == Clip.IDLE ? 0.38f : 0.75f;
        float k = Math.clamp(u / hopWindow, 0f, 1f);
        float jump = (float) Math.sin(k * Math.PI);
        float height = clip == Clip.IDLE ? 0.22f : 0.38f;
        float pre = smooth(0.0f, 0.12f, k) - smooth(0.12f, 0.25f, k);
        float land = smooth(0.9f, 1.0f, k) * (1 - smooth(hopWindow, hopWindow + 0.12f, u));
        root(0, height * jump * amp, 0, -0.10f * (float) Math.cos(k * Math.PI) * jump, 0, 0);
        squash(1 - 0.18f * pre - 0.20f * land + 0.12f * jump);
        float flop = (float) Math.cos(k * Math.PI) * jump;
        rot("ear_l", 0.5f * flop + 0.06f * sin(t * 2.1f), 0, 0.15f + 0.2f * land);
        rot("ear_r", 0.5f * flop + 0.06f * sin(t * 2.1f + 0.7f), 0, -0.15f - 0.2f * land);
        rot("tail", 0, 0.3f * sin(t * 5f), 0);
        rot("head", 0.10f * flop, 0.15f * sin(t * 0.6f), 0);
        rot("arm_l", 0, 0, 0.5f * jump);
        rot("arm_r", 0, 0, -0.5f * jump);
        rot("leg_l", 0.5f * jump, 0, 0);
        rot("leg_r", 0.5f * jump, 0, 0);
        rot("cap", -0.08f * flop, 0, 0.04f * sin(t * 1.7f));
    }

    /** Tempos da animação de combate da criatura (s); a preparação, o golpe e a recuperação vêm normalizados. */
    public static final float BRAWLER_HIT = 0.45f, BRAWLER_ALERT = 0.55f, BRAWLER_DEATH = 1.2f;

    /**
     * Rodada 09 — criatura de combate (fungo-cardo). Locomoção = a do saltitante; o resto é atuação de ameaça:
     * <ul>
     *   <li>EMOTE = alerta: pulinho de susto com o chapéu erguido e os braços abertos (0,55 s);</li>
     *   <li>ATTACK, t em [0,3): [0,1) PREPARAÇÃO (agacha, recua o corpo, chapéu inclinado para trás, braços para
     *       trás e, no último terço, tremor que avisa o golpe); [1,2) GOLPE (cabeçada: o corpo estica para a frente
     *       com o chapéu de espinhos à frente); [2,3) RECUPERAÇÃO (volta devagar e cambaleia: a janela do jogador);</li>
     *   <li>HIT = recuo: tomba para trás, achata e o chapéu chacoalha (0,45 s);</li>
     *   <li>DEATH = cai de costas, fica e some encolhendo como esporos (1,2 s).</li>
     * </ul>
     * Os tempos reais de cada fase são da lógica do jogo; aqui só o formato do gesto.
     */
    private void brawler(Clip clip, float t) {
        float lean = m.meta.has("lean") ? m.meta.get("lean").getAsFloat() : 0f;
        switch (clip) {
            case ATTACK -> {
                root(0, 0, 0, lean, 0, 0);
                if (t < 1f) {                                   // preparação
                    float k = smooth(0f, 0.55f, t), shiver = t > 0.62f ? sin(t * 95f) * smooth(0.62f, 0.75f, t) : 0f;
                    root(0, -0.02f * k, -0.05f * k, -0.32f * k, 0.05f * shiver, 0.03f * shiver);
                    squash(1f - 0.16f * k);
                    rot("cap", -0.42f * k, 0, 0.05f * shiver);
                    rot("arm_l", -0.9f * k, 0, 0.6f * k);
                    rot("arm_r", -0.9f * k, 0, -0.6f * k);
                    rot("leg_l", 0.25f * k, 0, 0);
                    rot("leg_r", 0.25f * k, 0, 0);
                } else if (t < 2f) {                            // golpe: cabeçada
                    float u = t - 1f, k = smooth(0f, 0.35f, u), back = 1f - smooth(0f, 0.25f, u);
                    float pitch = -0.32f * back + 0.62f * k;
                    root(0, 0.05f * sin((float) Math.PI * Math.min(1f, u * 1.6f)), 0.10f * k, pitch, 0, 0);
                    squash(0.84f * back + (1f + 0.14f * k) * (1f - back));
                    rot("cap", -0.42f * back + 0.55f * k, 0, 0);
                    rot("arm_l", -0.9f * back + 0.9f * k, 0, 0.6f * back + 0.3f * k);
                    rot("arm_r", -0.9f * back + 0.9f * k, 0, -0.6f * back - 0.3f * k);
                    rot("leg_l", -0.35f * k, 0, 0);
                    rot("leg_r", 0.35f * k, 0, 0);
                } else {                                        // recuperação: volta e cambaleia
                    float u = Math.min(1f, t - 2f), k = 1f - smooth(0f, 0.85f, u), wob = sin(u * 13f) * (1f - u);
                    root(0, 0, 0.10f * k, 0.62f * k, 0.18f * wob, 0.10f * wob);
                    squash(1f + 0.14f * k - 0.06f * wob * wob);
                    rot("cap", 0.55f * k + 0.12f * wob, 0, 0.10f * wob);
                    rot("arm_l", 0.9f * k, 0, 0.3f * k + 0.25f * wob);
                    rot("arm_r", 0.9f * k, 0, -0.3f * k - 0.25f * wob);
                }
            }
            case EMOTE -> {                                     // alerta
                float u = Math.min(t, BRAWLER_ALERT) / BRAWLER_ALERT, jump = sin((float) Math.PI * smooth(0.05f, 0.55f, u));
                root(0, 0.16f * jump, 0, lean - 0.18f * jump, 0, 0);
                squash(1f - 0.15f * (1f - smooth(0f, 0.12f, u)) * (u < 0.12f ? 1f : 0f) + 0.10f * jump - 0.12f * smooth(0.55f, 0.7f, u) * (1f - smooth(0.7f, 1f, u)));
                rot("cap", -0.30f * jump, 0, 0);
                rot("arm_l", -0.5f * jump, 0, 0.9f * jump);
                rot("arm_r", -0.5f * jump, 0, -0.9f * jump);
            }
            case HIT -> {
                float u = Math.min(t, BRAWLER_HIT) / BRAWLER_HIT, k = smooth(0f, 0.18f, u) * (1f - smooth(0.45f, 1f, u));
                float wob = sin(u * 30f) * (1f - u);
                root(0, 0, -0.06f * k, lean - 0.45f * k, 0, 0.12f * wob);
                squash(1f - 0.20f * k);
                rot("cap", -0.35f * k + 0.15f * wob, 0, 0.12f * wob);
                rot("arm_l", 0.6f * k, 0, 0.8f * k);
                rot("arm_r", 0.6f * k, 0, -0.8f * k);
            }
            case DEATH -> {
                float u = Math.min(t, BRAWLER_DEATH), fall = smooth(0f, 0.45f, u), bounce = sin(Math.clamp((u - 0.45f) / 0.2f, 0f, 1f) * (float) Math.PI) * 0.12f;
                float gone = smooth(0.80f, 1.15f, u);
                root(0, 0.03f * bounce, -0.10f * fall, lean - 1.35f * fall + bounce, 0, 0.12f * fall);
                squash(Math.max(0.02f, (1f - 0.18f * fall) * (1f - gone)));
                scale("cap", 1f - gone, 1f - gone, 1f - gone);
                rot("cap", -0.5f * fall, 0, 0.2f * fall);
                rot("arm_l", 0.4f * fall, 0, 1.1f * fall);
                rot("arm_r", 0.4f * fall, 0, -1.1f * fall);
                rot("leg_l", -0.6f * fall, 0, 0);
                rot("leg_r", -0.6f * fall, 0, 0);
            }
            default -> hopper(clip, t);
        }
    }

    private void floater(Clip clip, float t) {
        root(0, 0.10f * sin(t * 1.6f) + 0.03f * sin(t * 3.7f), 0, 0.08f * sin(t * 1.1f), 0.3f * sin(t * 0.5f), 0.06f * sin(t * 1.3f));
        float pulse = 1 + 0.05f * sin(t * 3.2f);
        scale("core", pulse, pulse, pulse);
        rot("ring", 0, t * 1.4f, 0.15f * sin(t * 0.9f));
        rot("wing_l", 0, 0, 0.5f * sin(t * 9f) + 0.2f);
        rot("wing_r", 0, 0, -0.5f * sin(t * 9f) - 0.2f);
        rot("tail", 0.25f * sin(t * 2.4f), 0, 0.2f * sin(t * 1.7f));
        rot("leaf_l", 0, 0, 0.25f * sin(t * 2.6f));
        rot("leaf_r", 0, 0, -0.25f * sin(t * 2.6f + 0.5f));
    }

    /**
     * Rodada 06 — inseto voador de quatro asas (mariposa). Batida LENTA e ampla (idle ~1,1 Hz, voo ~1,8 Hz): em
     * 30 fps cada ciclo tem 17–27 quadros, sem cintilação. Asas de trás atrasadas 0,7 rad e com menos
     * amplitude; antenas e abdômen seguem o corpo com atraso. IDLE = pairar; WALK/RUN = voo com pequeno
     * deslocamento em oito (sem IA); HIT = susto (asas para cima, recuo e tremor curto que amortece);
     * EMOTE = volta curiosa (giro completo em torno do eixo vertical). ATTACK não existe: vira IDLE.
     */
    private void flutter(Clip clip, float t) {
        boolean fly = clip == Clip.WALK || clip == Clip.RUN;
        float w = clip == Clip.RUN ? 13.0f : fly ? 11.0f : 7.0f;
        float amp = fly ? 0.55f : 0.32f, base = fly ? 0.45f : 0.72f;   // asas em V: o desenho aparece de frente
        float bob = fly ? 0.05f * sin(t * w + 0.9f) : 0.07f * sin(t * 2.0f) + 0.02f * sin(t * w + 0.9f);
        float yaw = 0f, pitch = fly ? 0.22f : 0.04f * sin(t * 1.3f), roll = 0.05f * sin(t * 0.9f);
        float dx = 0f, dz = 0f, lift = 0f;
        if (fly) {                                          // pequeno deslocamento em oito, deriva lenta
            float ph = t * 0.9f;
            dx = 0.16f * sin(ph);
            dz = 0.10f * sin(2 * ph);
            yaw = 0.35f * cos(ph);
            roll = -0.18f * cos(ph);
        }
        float startle = 0f;
        if (clip == Clip.HIT) {                             // susto: as asas PARAM de bater e sobem num gesto único
            float u = t % 0.9f;
            startle = smooth(0f, 0.16f, u) * (1f - smooth(0.32f, 0.8f, u));   // sobe em ~5 quadros, segura, desce
            dz -= 0.12f * startle;
            lift += 0.08f * startle;
            pitch -= 0.45f * startle;
            amp *= 1f - startle;                            // sem soma de batida + estalo (v1 cintilava: inversões a 1 quadro)
            base += 0.75f * startle;
        }
        if (clip == Clip.EMOTE) {                           // volta curiosa: um giro completo e um pulinho
            float u = (t % 1.6f) / 1.6f;
            yaw = (float) (2 * Math.PI) * smooth(0.1f, 0.8f, u);
            lift += 0.08f * sin((float) Math.PI * smooth(0.05f, 0.9f, u));
            w = 9.0f;
        }
        root(dx, bob + lift, dz, pitch, yaw, roll);
        float f = sin(t * w), b2 = sin(t * w - 0.7f);
        rot("wing_fl", 0.45f, 0.05f * f, base + amp * f);
        rot("wing_fr", 0.45f, -0.05f * f, -(base + amp * f));
        rot("wing_bl", 0.6f * 0.45f, 0.04f * b2, 0.7f * (base + amp * 0.8f * b2));
        rot("wing_br", 0.6f * 0.45f, -0.04f * b2, -0.7f * (base + amp * 0.8f * b2));
        rot("abdomen", -0.10f * sin(t * w - 1.4f) + 0.25f * startle + (fly ? 0.12f : 0f), 0, 0.04f * sin(t * 1.1f));
        rot("head", 0.06f * sin(t * 1.7f) - 0.2f * startle, 0.12f * sin(t * 0.7f), 0);
    }

    // ------------------------------------------------------------------ composição
    /** Converte a pose em matrizes de mundo por parte: out[i] = rootWorld × hierarquia. */
    public void compose(Pose pose, Matrix4f rootWorld, Matrix4f[] out) {
        int n = m.parts.size();
        float h = m.height;
        int b = n * 9;
        Matrix4f root = new Matrix4f(rootWorld)
                .translate(pose.v[b + 3] * h, pose.v[b + 4] * h, pose.v[b + 5] * h)
                .rotateZ(pose.v[b + 2]).rotateY(pose.v[b + 1]).rotateX(pose.v[b])
                .scale(pose.v[b + 6], pose.v[b + 7], pose.v[b + 8]);
        for (int i = 0; i < n; i++) {
            CharacterModel.Part part = m.parts.get(i);
            int k = i * 9;
            Matrix4f parent = part.parent < 0 ? root : out[part.parent];
            out[i].set(parent)
                    .translate(part.px + pose.v[k + 3] * h, part.py + pose.v[k + 4] * h, part.pz + pose.v[k + 5] * h)
                    .rotateZ(pose.v[k + 2]).rotateY(pose.v[k + 1]).rotateX(pose.v[k])
                    .scale(pose.v[k + 6], pose.v[k + 7], pose.v[k + 8])
                    .translate(-part.px, -part.py, -part.pz);
        }
    }
}
