package vx.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vx.game.menu.Cinematic;
import vx.viewer.Terrain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 11.1 — as tomadas do fundo do menu ({@code assets/scenes/apresentacao_e.json}) contra a colisão REAL da vila:
 * em 60 instantes de cada tomada o olho não está dentro de nada (relevo, parede, tronco, copa, poste, cerca), não fica a
 * menos de 0,5 u de nenhuma forma, nem a menos de 1,6 u de um morador, e a linha do olho até o alvo é livre (o mesmo
 * {@code rayClear} da câmera do jogo). Nenhuma tomada é validada só pela posição inicial.
 */
class ApresentacaoTest {
    static final Path A = Path.of("assets");

    @Test
    void tomadasNuncaAtravessamNadaNemColamEmMoradores() {
        SceneDef s = SceneDef.load(A, "jogo_vila_e");
        Collision c = Collision.build(Terrain.e(), Collision.profiles(A), s.handPlaced, s.bodies, s);
        Cinematic cine = Cinematic.load(A);
        assertEquals(s.layout, cine.scene, "a apresentação é da vila fixa");
        assertTrue(cine.shots.size() >= 4, "pelo menos quatro tomadas");
        List<String> bad = new ArrayList<>();
        int checked = 0;
        float t0 = 0;
        for (Cinematic.Shot sh : cine.shots) {
            for (int i = 0; i <= 60; i++) {
                float t = t0 + sh.duration() * i / 60f * 0.9999f;
                Cinematic.Pose p = cine.at(t);
                assertSame(sh, p.shot(), "instante " + t + " cai na tomada certa");
                float[] e = Cinematic.eye(p);
                Vector3f eye = new Vector3f(e[0], e[1], e[2]), tg = new Vector3f(p.tx(), p.ty(), p.tz());
                checked++;
                if (c.cameraInside(eye)) bad.add(sh.name() + " @" + i + ": olho dentro de " + c.blocker(eye, eye, 0f));
                if (eye.y < c.ground.height(eye.x, eye.z) + 0.6f) bad.add(sh.name() + " @" + i + ": olho rente ao chão");
                for (Collision.Shape q : c.shapes)
                    if (q.contains(eye.x, eye.y, eye.z, 0.5f)) { bad.add(sh.name() + " @" + i + ": olho a menos de 0,5 u de " + q.name); break; }
                float clear = c.rayClear(tg, eye, 0.2f);
                if (clear < 0.999f) bad.add(String.format("%s @%d: vista cortada a %.0f%% por %s", sh.name(), i, clear * 100, c.blocker(tg, eye, 0.2f)));
                for (SceneDef.Interactive n : s.interactives)
                    if (Math.hypot(n.x() - eye.x, n.z() - eye.z) < 1.6f && eye.y < c.ground.height(n.x(), n.z()) + 2.2f)
                        bad.add(sh.name() + " @" + i + ": olho colado em " + n.name());
            }
            t0 += sh.duration();
        }
        assertTrue(checked >= 240, "conferiu " + checked + " olhos");
        assertTrue(bad.isEmpty(), bad.size() + " problema(s):\n" + String.join("\n", bad.subList(0, Math.min(40, bad.size()))));
    }

    @Test
    void vitrineDosPersonagensComVistaLivre() {
        SceneDef s = SceneDef.load(A, "jogo_vila_e");
        Collision c = Collision.build(Terrain.e(), Collision.profiles(A), s.handPlaced, s.bodies, s);
        float[] h = Cinematic.load(A).showcase;
        assertTrue(c.walkable(h[0], h[1]), "o herói da vitrine pisa em chão caminhável");
        int fails = 0;                                                      // o giro (arrastar) só muda o desenho: a câmera fica
        float g = c.ground.height(h[0], h[1]);
        float[] v = Cinematic.showcaseCamera(h[0], g, h[1], h[2]);
        float cp = (float) Math.cos(v[4]);
        Vector3f tg = new Vector3f(v[0], v[1], v[2]);
        Vector3f eye = new Vector3f(v[0] + v[5] * cp * (float) Math.sin(v[3]), v[1] + v[5] * (float) Math.sin(v[4]), v[2] + v[5] * cp * (float) Math.cos(v[3]));
        assertFalse(c.cameraInside(eye), "olho fora de tudo");
        assertTrue(c.rayClear(new Vector3f(h[0], g + 0.8f, h[1]), eye, 0.25f) >= 0.999f, "do peito do herói ao olho: " + c.blocker(new Vector3f(h[0], g + 0.8f, h[1]), eye, 0.25f));
        assertTrue(c.rayClear(tg, eye, 0.25f) >= 0.999f, "do alvo ao olho: " + c.blocker(tg, eye, 0.25f));
        for (Collision.Shape q : c.shapes) if (q.contains(eye.x, eye.y, eye.z, 0.4f)) fails++;
        assertEquals(0, fails, "nenhuma forma a menos de 0,4 u do olho");
        for (int k = 0; k <= 20; k++) {                                     // o relevo não sobe entre o olho e o herói
            float t = k / 20f, x = eye.x + (h[0] - eye.x) * t, y = eye.y + (g + 0.5f - eye.y) * t, z = eye.z + (h[1] - eye.z) * t;
            assertTrue(y - c.ground.height(x, z) > 0.3f, "relevo na frente da vitrine em " + t);
        }
    }

    @Test
    void lacoContinuoComEscurecimentoSoNaTroca() {
        Cinematic cine = Cinematic.load(A);
        float total = cine.total();
        assertTrue(total > 30f, "laço longo o bastante para não cansar: " + total + " s");
        Cinematic.Pose mid = cine.at(cine.shots.getFirst().duration() / 2);
        assertEquals(0f, mid.fade(), 1e-6, "meio da tomada sem escurecimento");
        assertEquals(1f, cine.at(0f).fade(), 1e-6, "a troca começa no escuro");
        Cinematic.Pose a = cine.at(total - 1e-3f), b = cine.at(total + 1e-3f);
        assertTrue(a.fade() > 0.99f && b.fade() > 0.99f, "a volta do laço acontece no escuro (sem salto visível)");
        // movimento contínuo dentro da tomada: passo de 1/60 s nunca move o olho mais que 0,1 u
        float[] prev = Cinematic.eye(cine.at(0.7f));
        for (float t = 0.7f + 1 / 60f; t < cine.shots.getFirst().duration() - 0.7f; t += 1 / 60f) {
            float[] e = Cinematic.eye(cine.at(t));
            assertTrue(Math.hypot(Math.hypot(e[0] - prev[0], e[1] - prev[1]), e[2] - prev[2]) < 0.1f, "olho pulou em t=" + t);
            prev = e;
        }
    }

    @Test
    void apresentacaoMalformadaERecusada() {
        assertThrows(RuntimeException.class, () -> Cinematic.parse("{\"formato\":\"outro\",\"cena\":\"x\",\"tomadas\":[]}"));
        assertThrows(RuntimeException.class, () -> Cinematic.parse("{\"formato\":\"valumo.apresentacao\",\"cena\":\"x\",\"tomadas\":[]}"));
        assertThrows(RuntimeException.class, () -> Cinematic.parse("{\"formato\":\"valumo.apresentacao\",\"cena\":\"x\",\"tomadas\":[{\"nome\":\"a\","
                + "\"alvo\":[0,0,0],\"rumo\":[0,0],\"inclinacao\":[0,0],\"distancia\":[1,1],\"duracao\":0.5}]}"));
    }
}
