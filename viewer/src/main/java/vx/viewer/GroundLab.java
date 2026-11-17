package vx.viewer;

import org.joml.Matrix4f;

/**
 * Bancada de terreno (rodada 04): chão analítico que desliza sob o herói parado no modo PERSONAGEM, para
 * provar os pés de perfil em condições conhecidas. O mesmo chão é desenhado (faixas de degrau) e lido pelo
 * IK de pé, então o que se vê é exatamente o que o animador recebeu.
 *
 * <ul>
 *   <li>{@code flat}: plano;</li>
 *   <li>{@code ramp}: encosta de ~14° quantizada em degraus de 1/8 u (como o T1);</li>
 *   <li>{@code steps}: degraus de 1 voxel de terreno (0,125 u) a cada 0,35 u;</li>
 *   <li>{@code big}: degraus de 0,2 u a cada 0,6 u (perto do limite do IK: ver Animator.STEP_LIMIT).</li>
 * </ul>
 */
final class GroundLab {
    final String kind;
    float scroll;     // avanço do herói (u): o chão anda para trás
    float base;       // altura do chão sob a raiz: tudo é deslocado por ela (o herói fica parado no quadro)

    GroundLab(String kind) { this.kind = kind; }

    /** Altura do chão no ponto do mundo (o herói está na origem, olhando para +Z). */
    float height(float x, float z) { return raw(x, z) - base; }

    private float raw(float x, float z) {
        float zz = z + scroll;
        return switch (kind) {
            case "ramp" -> (float) Math.floor(Math.max(0f, zz) * 0.25f / 0.125f) * 0.125f;
            case "steps" -> (float) Math.floor(Math.max(0f, zz) / 0.35f) * 0.125f;
            case "big" -> (float) Math.floor(Math.max(0f, zz) / 0.6f) * 0.2f;
            default -> 0f;
        };
    }

    /** Altura suavizada para a raiz (média ao redor), como World.groundSmooth. */
    float smooth(float x, float z) {
        float s = 0;
        for (int k = -2; k <= 2; k++) s += raw(x, z + k * 0.08f);
        return s / 5f;
    }

    /** Desenha as faixas de degrau entre z = -3 e 3 (largura 1,6 u). */
    void submit(Renderer.Frame f) {
        float step = 0.0625f;
        for (float z = -3f; z < 3f; z += step) {
            float h = height(0, z + step * 0.5f);
            float bottom = -0.6f;
            int col = (Math.floor((z + scroll) / 0.35f) % 2 == 0) ? 0x86b45a : 0x7aa852;
            f.add(Fx3d.matteCube(col), new Matrix4f().translate(0, (h + bottom) / 2f, z + step / 2f).scale(1.6f, h - bottom, step + 0.002f));
        }
    }
}
