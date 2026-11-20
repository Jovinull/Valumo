package vx.game;

import org.junit.jupiter.api.Test;
import vx.game.world.Rng;
import vx.viewer.Terrain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 12.1 — o índice espacial da colisão não muda nenhum resultado: chão, empurrão e "tem corpo aqui?" pelo índice
 * saem IGUAIS (bit a bit) aos da varredura da lista inteira, na mesma ordem, em milhares de consultas sobre regiões v1 e
 * v2 (a vila com as formas compostas da oficina e das ruínas), inclusive nas bordas das células e com o corpo empurrado
 * para longe.
 */
class CollisionIndexTest {

    static float floorAll(Collision c, float x, float z, float y) {
        float g = c.ground.height(x, z);
        for (Collision.Shape s : c.shapes) {
            if (!Collision.supports(s) || s.y1 <= g || s.y1 > y + Player.STEP) continue;
            if (s.contains(x, s.y1, z, 0f)) g = s.y1;
        }
        return g;
    }

    static float[] resolveAll(Collision c, float[] p, float rad, float y, float h, float step) {
        float tx = 0, tz = 0;
        for (int it = 0; it < 4; it++) {
            boolean any = false;
            for (Collision.Shape s : c.shapes) {
                if (!s.body || s.y1 <= y + step || s.y0 >= y + h) continue;
                float[] d = s.push(p[0], p[1], rad);
                if (d == null) continue;
                p[0] += d[0];
                p[1] += d[1];
                tx += d[0];
                tz += d[1];
                any = true;
            }
            if (!any) break;
        }
        return new float[]{tx, tz};
    }

    @Test
    void oIndiceDaOMesmoResultadoDaVarreduraCompleta() {
        int n = 0;
        for (ProceduralScene ps : new ProceduralScene[]{ProceduralWorldTest.scene(1024), GeneratorV2Test.v2(1), GeneratorV2Test.v2(77), GeneratorV2Test.v2(2048)}) {
            SceneDef s = ps.scene();
            Collision c = Collision.build(Terrain.of(ps.field), Collision.profiles(ProceduralWorldTest.A), s.handPlaced, s.bodies, s);
            Rng r = Rng.of(ps.layout.seed, "teste", "indice");
            for (Collision.Shape sh : c.shapes.subList(0, Math.min(c.shapes.size(), 400))) {
                for (int k = 0; k < 6; k++) {
                    // perto de cada forma (e nas bordas das células de 1 u), em várias alturas, com raios pequenos e grandes
                    float x = sh.cx + r.range(-sh.bound - 0.6f, sh.bound + 0.6f), z = sh.cz + r.range(-sh.bound - 0.6f, sh.bound + 0.6f);
                    if (k == 0) { x = (float) Math.floor(x); z = (float) Math.floor(z); }
                    float y = c.ground.height(x, z) + r.range(-0.2f, 2.5f), rad = k == 5 ? 0.9f : Player.RADIUS;
                    assertEquals(floorAll(c, x, z, y), c.floor(x, z, y, Player.RADIUS), "chão em " + x + ", " + z);
                    float[] a = {x, z}, b = {x, z};
                    float[] da = resolveAll(c, a, rad, y, Player.HEIGHT, Player.STEP), db = c.resolve(b, rad, y, Player.HEIGHT, Player.STEP);
                    assertArrayEquals(da, db, "empurrão em " + x + ", " + z);
                    assertArrayEquals(a, b, "posição final em " + x + ", " + z);
                    assertEquals(Math.hypot(da[0], da[1]) > 0 || touches(c, x, z, y, rad), c.bodyAt(x, z, y, rad, Player.HEIGHT), "corpo em " + x + ", " + z);
                    n++;
                }
            }
        }
        assertTrue(n > 4000, "consultas: " + n);
    }

    static boolean touches(Collision c, float x, float z, float y, float rad) {
        for (Collision.Shape s : c.shapes)
            if (s.body && s.y1 > y + Player.STEP && s.y0 < y + Player.HEIGHT && s.push(x, z, rad) != null) return true;
        return false;
    }
}
