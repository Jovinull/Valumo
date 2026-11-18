package vx.game;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rodada 09.2 — mira opcional (ADR 0020), lógica pura (testável sem OpenGL). A mira LIVRE é o padrão: nada aqui age
 * sem o jogador pedir.
 *
 * <ul>
 *   <li>{@link #toggle}: trava no melhor inimigo válido ou solta. Válido = vivo, dentro de {@code mira.alcance} e com
 *       linha de visão (nada sólido entre o peito do herói e o do inimigo). Melhor = menor distância + afastamento do
 *       centro da vista × {@code mira.angulo}: perto e à frente da câmera ganha; nunca um morto nem um atrás da parede.</li>
 *   <li>{@link #cycle}: próximo à direita (+1) ou à esquerda (−1) NA TELA: candidatos ordenados pelo ângulo em relação ao
 *       rumo da câmera, a partir do alvo atual (dá a volta). Nada de ordem de criação: o vizinho ao lado vem antes do
 *       distante do outro lado.</li>
 *   <li>{@link #update}: solta sozinha se o alvo morreu ({@code mira.aoMorrer}: passa ao próximo engajado mais perto ou
 *       solta), saiu de {@code mira.solta} u ou ficou escondido mais de {@code mira.oculto} s.</li>
 * </ul>
 */
public final class Targeting {
    public Enemy target;
    /** Motivo da última soltura automática (relatório, roteiros): "morreu", "longe", "escondido" ou null. */
    public String lastRelease;
    /** Trocas e travas (testes, relatório). */
    public int locks, switches;

    private final Vector3f a = new Vector3f(), b = new Vector3f();

    public boolean locked() { return target != null; }

    /** Inimigo pode ser travado agora. */
    boolean valid(Enemy e, Player p, Combat c, float range) {
        if (e == null || !e.alive()) return false;
        float d = (float) Math.hypot(e.x - p.x, e.z - p.z);
        if (d > range) return false;
        return visible(e, p, c);
    }

    boolean visible(Enemy e, Player p, Combat c) {
        a.set(p.x, p.y + 0.85f, p.z);
        b.set(e.x, e.y + 0.4f * e.scale, e.z);
        return c.collision.solidBetween(a, b) == null;
    }

    /** Afastamento (rad) do inimigo em relação ao rumo para onde a câmera olha (fx, fz no chão). */
    static float offAngle(Enemy e, Player p, float camFwdYaw) {
        return Hero.angle((float) Math.atan2(e.x - p.x, e.z - p.z) - camFwdYaw);
    }

    /** Melhor alvo para travar agora (ou null). {@code camFwdYaw}: rumo do chão para onde a câmera olha. */
    public Enemy best(Player p, Combat c, float camFwdYaw) {
        Enemy best = null;
        float bestScore = Float.MAX_VALUE;
        for (Enemy e : c.enemies) {
            if (!valid(e, p, c, c.def.lockRange)) continue;
            float d = (float) Math.hypot(e.x - p.x, e.z - p.z);
            float score = d + Math.abs(offAngle(e, p, camFwdYaw)) * c.def.lockAngleWeight;
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /** Q: trava no melhor alvo ou solta. Retorna o alvo novo (null = livre). */
    public Enemy toggle(Player p, Combat c, float camFwdYaw) {
        if (target != null) { target = null; lastRelease = "jogador"; return null; }
        target = best(p, c, camFwdYaw);
        if (target != null) { locks++; target.hiddenT = 0f; }
        lastRelease = null;
        return target;
    }

    /** Tab (+1, à direita na tela) / Shift+Tab (−1, à esquerda). Sem alvo: trava como o Q. */
    public Enemy cycle(int dir, Player p, Combat c, float camFwdYaw) {
        if (target == null) return toggle(p, c, camFwdYaw);
        List<Enemy> cand = new ArrayList<>();
        for (Enemy e : c.enemies) if (e == target || valid(e, p, c, c.def.lockRange)) cand.add(e);
        if (cand.size() < 2) return target;
        // ângulo na tela: + = à esquerda do centro (rumo cresce no sentido anti-horário visto de cima); "direita" = menor
        float cur = offAngle(target, p, camFwdYaw);
        Enemy pick = null;
        float bestDelta = Float.MAX_VALUE;
        for (Enemy e : cand) {
            if (e == target) continue;
            float off = offAngle(e, p, camFwdYaw);
            float delta = dir > 0 ? cur - off : off - cur;                // à direita: ângulo menor
            if (delta <= 1e-4f) delta += (float) (2 * Math.PI);          // dá a volta
            if (delta < bestDelta) { bestDelta = delta; pick = e; }
        }
        if (pick != null) { target = pick; switches++; target.hiddenT = 0f; }
        return target;
    }

    /** Depois de cada passo: soltura automática (morte, distância, escondido). */
    public void update(float h, Player p, Combat c, float camFwdYaw) {
        if (target == null) return;
        if (!target.alive()) {
            Enemy dead = target;
            target = null;
            lastRelease = "morreu";
            if (c.def.lockNextOnDeath) {                                  // passa ao engajado mais perto, se houver
                Enemy next = null;
                float bd = Float.MAX_VALUE;
                for (Enemy e : c.enemies) {
                    if (e == dead || !e.engaged() || !valid(e, p, c, c.def.lockRange)) continue;
                    float d = (float) Math.hypot(e.x - p.x, e.z - p.z);
                    if (d < bd) { bd = d; next = e; }
                }
                if (next != null) { target = next; switches++; next.hiddenT = 0f; }
            }
            return;
        }
        float d = (float) Math.hypot(target.x - p.x, target.z - p.z);
        if (d > c.def.lockKeep) { target = null; lastRelease = "longe"; return; }
        target.hiddenT = visible(target, p, c) ? 0f : target.hiddenT + h;
        if (target.hiddenT > c.def.lockHidden) { target = null; lastRelease = "escondido"; }
    }

    public void clear() { target = null; }
}
