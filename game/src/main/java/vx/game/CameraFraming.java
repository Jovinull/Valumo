package vx.game;

import org.joml.Vector3f;

/**
 * Rodada 09.1 — mede o enquadramento da luta como o jogador o veria, sem OpenGL: projeta herói e inimigo pela câmera
 * lógica (mesma convenção da {@link vx.engine.Camera}) e responde três perguntas.
 *
 * <ul>
 *   <li><b>Quanto da tela o herói ocupa</b> ({@code heroCover}: área da caixa projetada do corpo, recortada à tela).
 *       Na distância padrão de 4,6 u ele fica perto de 3%; com a câmera encostada nele (0,96 u, o defeito do
 *       carvalho na rodada 09) passa de 40%: a cabeça domina a imagem.</li>
 *   <li><b>Quanto do inimigo se vê</b> ({@code enemyShown}: fração de pontos do corpo dele que caem dentro da tela,
 *       não ficam atrás do corpo do herói nem atrás de cenário que bloqueia a câmera).</li>
 *   <li><b>A câmera está dentro de algo</b> ({@code inside}: relevo, forma de câmera ou o corpo do herói).</li>
 * </ul>
 *
 * Os corpos são aproximações declaradas (cápsulas da hurtbox, alargadas para a cabeça grande dos protagonistas):
 * servem para comparar câmeras e achar casos ruins, não para medir pixels. As capturas reais confirmam.
 */
public final class CameraFraming {
    /** Corpo do herói para a oclusão: cápsula até o alto do cabelo; a cabeça é larga (2,35–2,60 cabeças). */
    public static final float HERO_TOP = 1.22f, HERO_R = 0.27f;
    /** Corpo do fungo: chapéu largo e baixo. */
    public static final float ENEMY_TOP = 0.72f, ENEMY_R = 0.30f;
    public static final float ASPECT = 16f / 9f;

    /** offscreen/byHero/byScenery: frações dos pontos do inimigo perdidas por cada motivo (diagnóstico). */
    public record Result(float dist, float heroCover, float heroHeight, float enemyShown, boolean inside, float offscreen, float byHero, float byScenery) {
        /** A cabeça/corpo domina a tela (o defeito da rodada 09 junto ao carvalho). */
        public boolean heroDominates() { return heroCover > 0.20f; }

        @Override public String toString() {
            return String.format("dist %.2f u, herói %.0f%% da tela (altura %.0f%%), inimigo %.0f%% visível (fora da tela %.0f%%, atrás do herói %.0f%%, atrás do cenário %.0f%%)%s",
                    dist, heroCover * 100, heroHeight * 100, enemyShown * 100, offscreen * 100, byHero * 100, byScenery * 100, inside ? ", CÂMERA DENTRO" : "");
        }
    }

    private CameraFraming() {}

    /** Mede a câmera como está (posição e rumo atuais, sem interpolar). Colisão opcional (null: sem cenário). */
    public static Result measure(FollowCamera cam, float hx, float hy, float hz, float ex, float ey, float ez, Collision c) {
        Vector3f eye = cam.position(new Vector3f());
        Vector3f fwd = new Vector3f(cam.pivot).sub(eye).normalize();
        Vector3f right = new Vector3f(fwd).cross(0, 1, 0).normalize();
        Vector3f up = new Vector3f(right).cross(fwd);
        float ty = (float) Math.tan(Math.toRadians(FollowCamera.FOV) / 2), tx = ty * ASPECT;

        // herói: caixa projetada de anéis em várias alturas
        float minX = 9, maxX = -9, minY = 9, maxY = -9;
        boolean behind = false;
        Vector3f p = new Vector3f();
        for (int k = 0; k <= 6; k++) {
            float y = hy + HERO_TOP * k / 6f;
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                p.set(hx + HERO_R * (float) Math.sin(ang), y, hz + HERO_R * (float) Math.cos(ang)).sub(eye);
                float z = p.dot(fwd);
                if (z < 0.1f) { behind = true; continue; }
                float sx = p.dot(right) / z / tx, sy = p.dot(up) / z / ty;
                minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            }
        }
        float cover, height;
        if (behind) { cover = 1f; height = 9f; }
        else {
            float w = Math.max(0, Math.min(1, maxX) - Math.max(-1, minX)), h = Math.max(0, Math.min(1, maxY) - Math.max(-1, minY));
            cover = w * h / 4f;
            height = (maxY - minY) / 2f;
        }

        // inimigo: pontos do corpo (anel + topo), cada um visível se na tela, fora do herói e sem cenário no caminho
        int seen = 0, total = 0, off = 0, hero = 0, scen = 0;
        Vector3f a0 = new Vector3f(hx, hy + HERO_R, hz), a1 = new Vector3f(hx, hy + HERO_TOP - HERO_R, hz);
        for (int k = 0; k < 3; k++) {
            float y = ey + 0.30f + (ENEMY_TOP - 0.30f) * k / 2f;
            float r = k == 2 ? ENEMY_R * 0.5f : ENEMY_R * 0.8f;
            for (int a = 0; a < 6; a++) {
                double ang = a * Math.PI / 3 + k * 0.5;
                Vector3f q = new Vector3f(ex + r * (float) Math.sin(ang), y, ez + r * (float) Math.cos(ang));
                total++;
                p.set(q).sub(eye);
                float z = p.dot(fwd);
                if (z < 0.1f || Math.abs(p.dot(right) / z / tx) > 0.98f || Math.abs(p.dot(up) / z / ty) > 0.98f) { off++; continue; }
                if (segSeg(eye, q, a0, a1) < HERO_R) { hero++; continue; }
                if (c != null && c.rayClear(eye, q, 0f) < 0.98f) { scen++; continue; }
                seen++;
            }
        }
        boolean inside = (c != null && c.cameraInside(eye))                // dentro do cenário ou da cabeça/corpo do herói
                || segSeg(eye, eye, new Vector3f(hx, hy, hz), new Vector3f(hx, hy + HERO_TOP, hz)) < HERO_R;
        return new Result(cam.dist, cover, height, (float) seen / total, inside, (float) off / total, (float) hero / total, (float) scen / total);
    }

    /** Distância entre os segmentos p0–p1 e q0–q1. */
    static float segSeg(Vector3f p0, Vector3f p1, Vector3f q0, Vector3f q1) {
        Vector3f d1 = new Vector3f(p1).sub(p0), d2 = new Vector3f(q1).sub(q0), r = new Vector3f(p0).sub(q0);
        float a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r), s, t;
        float c = d1.dot(r), b = d1.dot(d2), den = a * e - b * b;
        s = den > 1e-9f ? Math.clamp((b * f - c * e) / den, 0f, 1f) : 0f;
        t = (b * s + f) / e;
        if (t < 0) { t = 0; s = Math.clamp(-c / a, 0f, 1f); }
        else if (t > 1) { t = 1; s = Math.clamp((b - c) / a, 0f, 1f); }
        Vector3f c1 = new Vector3f(p0).fma(s, d1), c2 = new Vector3f(q0).fma(t, d2);
        return c1.distance(c2);
    }
}
