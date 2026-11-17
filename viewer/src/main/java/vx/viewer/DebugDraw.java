package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Modo de depuração das armas (rodada 04, tecla {@code J} / captura {@code dbg=1}). Desenha, por cima do
 * personagem: o eixo de cada volume de colisão (verde = ok, vermelho = penetração além da tolerância), os
 * pivôs das partes (branco), as empunhaduras (amarelo), a ponta da arma e o ponto de emissão (ciano) e a
 * TRAJETÓRIA recente da ponta (magenta, 0,4 s). Desativado por padrão; não roda no jogo.
 */
final class DebugDraw {
    private static final int OK = 0x3cdc64, BAD = 0xff3040, PIVOT = 0xf4f4f4, GRIP = 0xffd23c, EMIT = 0x40e8ff, TRAIL = 0xff40d0;

    private DebugDraw() { }

    static void actor(Renderer.Frame f, Actor a, Matrix4f world, Matrix4f[] mats) {
        CharacterModel m = a.model;
        if (a.animator().prof == null || m.meta == null || !m.meta.has("hands")) return;   // só rigs com perfil de arma
        float vs = m.voxelSize;
        List<WeaponCheck.Vol> pv = WeaponCheck.posed(WeaponCheck.volumes(m), mats);
        float[] ex = WeaponCheck.excess(m, pv);
        for (int i = 0; i < pv.size(); i++) {
            WeaponCheck.Vol v = pv.get(i);
            int col = ex[i] > 0 ? BAD : OK;
            float w = Math.max(0.35f * vs, v.r() * 0.35f);
            if (v.b() != null) rod(f, v.a(), v.b(), w, col);
            cube(f, v.a(), v.r() * 0.6f, col);
            if (v.b() != null) cube(f, v.b(), v.r() * 0.6f, col);
        }
        for (int i = 0; i < m.parts.size(); i++) {
            CharacterModel.Part p = m.parts.get(i);
            cube(f, mats[i].transformPosition(new Vector3f(p.px, p.py, p.pz)), 0.9f * vs, PIVOT);
        }
        Animator an = a.animator();
        cube(f, WeaponCheck.handWorld(an, mats, "r"), 1.6f * vs, GRIP);
        cube(f, WeaponCheck.handWorld(an, mats, "l"), 1.6f * vs, GRIP);
        if (an.prof != null && an.prof.rightItem != null && m.index.containsKey(an.prof.rightItem)) {
            int wi = m.index.get(an.prof.rightItem);
            CharacterModel.Part wp = m.parts.get(wi);
            if (an.prof.tipLength > 0) cube(f, mats[wi].transformPosition(new Vector3f(wp.px, wp.py + an.prof.tipLength * vs, wp.pz)), 1.4f * vs, EMIT);
            if (an.prof.emit != null)
                cube(f, mats[wi].transformPosition(new Vector3f(an.prof.emit[0], an.prof.emit[1], an.prof.emit[2]).mul(vs)), 2.0f * vs, EMIT);
        }
        // trajetória: a ponta registrada nos últimos submits (vídeos e modo interativo)
        a.trail.add(new Vector3f(world.transformPosition(new Vector3f(an.dbgTip))));
        while (a.trail.size() > 24) a.trail.removeFirst();
        Vector3f prev = null;
        for (Vector3f t : a.trail) {
            if (prev != null && prev.distance(t) > 1e-4f) rod(f, prev, t, 0.6f * vs, TRAIL);
            prev = t;
        }
    }

    static void cube(Renderer.Frame f, Vector3f c, float size, int col) {
        f.draws.add(new Renderer.Draw(Fx3d.cube(col), new Matrix4f().translation(c).scale(size), null, false, false).outline(0f).overlay());
    }

    static void rod(Renderer.Frame f, Vector3f a, Vector3f b, float w, int col) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1e-6f) return;
        Matrix4f mm = new Matrix4f().translation(new Vector3f(a).add(b).mul(0.5f))
                .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.div(len))).scale(w, len, w);
        f.draws.add(new Renderer.Draw(Fx3d.cube(col), mm, null, false, false).outline(0f).overlay());
    }
}
