package vx.viewer;

import org.joml.Matrix4f;

/** Instância animada de um {@link CharacterModel}: mantém a pose corrente e faz a transição entre clipes. */
public final class Actor {
    public final CharacterModel model;
    private final Animator animator;
    private final Animator.Pose pose, target;
    private final Matrix4f[] mats;
    private boolean first = true;

    public Actor(CharacterModel model, float exaggeration) {
        this.model = model;
        this.animator = new Animator(model, exaggeration);
        this.pose = animator.newPose();
        this.target = animator.newPose();
        this.mats = new Matrix4f[model.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
    }

    public float duration(Animator.Clip clip) { return animator.duration(clip); }

    /**
     * Avalia o clipe e envia as partes ao quadro.
     *
     * @param blend 1 = pose exata do clipe (capturas); menor que 1 suaviza a troca de clipes
     */
    public void submit(Renderer.Frame frame, Matrix4f world, Animator.Clip clip, float t, float blend) {
        submit(frame, world, clip, t, blend, null);
    }

    /** Expressão automática por clipe (rodada 02): rigs sem a malha alternativa ficam na neutra. */
    public static String autoExpression(Animator.Clip clip) {
        return switch (clip) {
            case ATTACK -> "determined";
            case HIT -> "surprise";
            case EMOTE -> "joy";
            default -> null;
        };
    }

    /** @param expression nome da malha alternativa ("joy", "determined", "surprise"); null = automática */
    public void submit(Renderer.Frame frame, Matrix4f world, Animator.Clip clip, float t, float blend, String expression) {
        submit(frame, world, clip, t, blend, expression, null);
    }

    /** Tempo acumulado de avaliação de animação (ns) e número de avaliações — métrica do benchmark. */
    public static long animNanos, animCount;
    /** Liga o desenho dos volumes, pivôs, empunhaduras e trajetórias (ferramenta de QA, rodada 04). */
    public static boolean debug;

    public Animator animator() { return animator; }

    /** Últimas posições da ponta da arma (modo de depuração). */
    final java.util.ArrayDeque<org.joml.Vector3f> trail = new java.util.ArrayDeque<>();

    /** @param ground altura do terreno (IK de pé); null = chão plano */
    public void submit(Renderer.Frame frame, Matrix4f world, Animator.Clip clip, float t, float blend, String expression,
                       Animator.Ground ground) {
        submit(frame, world, new Animator.Motion().set(clip, t), blend, expression, ground);
    }

    /** Rodada 04: avalia locomoção + camada de ação do perfil de arma. */
    public void submit(Renderer.Frame frame, Matrix4f world, Animator.Motion motion, float blend, String expression,
                       Animator.Ground ground) {
        // expressão pelo tipo de gesto: golpe recebido = surpresa, comemoração = alegria, ataque = determinação
        Animator.Clip clip = motion.move != null && motion.weight > 0.5f
                ? (motion.move.equals("hit") ? Animator.Clip.HIT : motion.move.equals("emote") ? Animator.Clip.EMOTE : Animator.Clip.ATTACK)
                : motion.clip;
        long t0 = System.nanoTime();
        animator.evaluate(motion, target, world, ground);
        animNanos += System.nanoTime() - t0;
        animCount++;
        if (first || blend >= 1f) {
            pose.lerp(target, 1f);
            first = false;
        } else {
            pose.lerp(target, blend);
        }
        animator.compose(pose, world, mats);
        for (int i = 0; i < mats.length; i++) {
            CharacterModel.Part p = model.parts.get(i);
            String ex = expression == null ? autoExpression(clip) : expression;
            vx.engine.Mesh mesh = ex != null && p.alts.containsKey(ex) ? p.alts.get(ex) : p.mesh;
            if (Math.abs(mats[i].determinant()) < 1e-12f) continue;          // parte escondida (escala 0)
            frame.draws.add(new Renderer.Draw(mesh, new Matrix4f(mats[i]), model.atlas, false, true));
        }
        for (Animator.Fx f : animator.fx) {
            Matrix4f m = new Matrix4f(world).mul(f.m);
            if (f.kind == Animator.Fx.ARROW) {
                Integer ai = model.index.get("arrow");
                if (ai != null && f.size > 0.01f) frame.draws.add(new Renderer.Draw(model.parts.get(ai).mesh, m, null, false, true));
            } else {
                float sz = f.size * (f.kind == Animator.Fx.GLOW ? 0.55f : 1f);
                if (sz > 0.01f) frame.draws.add(new Renderer.Draw(Fx3d.orb(), m.scale(sz), null, false, false).outline(0.3f));
            }
        }
        if (debug) DebugDraw.actor(frame, this, world, mats);
    }

    /** Matrizes de mundo das partes do último submit (para QA e depuração). */
    public Matrix4f[] lastMatrices() { return mats; }
}
