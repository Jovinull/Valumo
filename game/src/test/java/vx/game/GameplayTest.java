package vx.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.engine.Window.InputEvent;
import vx.engine.Window.InputEvent.Kind;
import vx.viewer.Animator;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;
import vx.viewer.Terrain;
import vx.viewer.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 08 — propriedades reais do gameplay, sem OpenGL: independência de FPS, colisão, chão, degraus,
 * desníveis, água, câmera, interação, diálogo, pausa, aparência e a cena jogável com o relevo real da vila.
 */
class GameplayTest {
    static final Path A = Path.of("assets");

    /** Chão sintético. */
    static Collision flat(float h) {
        Collision c = new Collision((x, z) -> h);
        c.waterLevel = -100;
        return c;
    }

    static Collision stepAt(float z0, float rise) {
        Collision c = new Collision((x, z) -> z >= z0 ? rise : 0f);
        c.waterLevel = -100;
        return c;
    }

    /** Simula exatamente secs segundos em quadros de 1/fps (o último quadro é parcial). */
    static float walkFor(Player p, Collision c, float secs, float fps, float ix, float iz, boolean run) {
        float dt = 1f / fps, t = 0;
        while (secs - t > 1e-6f) {
            float h = Math.min(dt, secs - t);
            p.update(h, ix, iz, run, c);
            t += h;
        }
        return p.traveled;
    }

    // ------------------------------------------------------------------ movimento

    @Test
    void movementDoesNotDependOnFrameRate() {
        float[] fps = {17.3f, 30, 60, 144, 240};
        float[] dist = new float[fps.length];
        for (int i = 0; i < fps.length; i++) {
            Player p = new Player();
            Collision c = flat(0);
            p.place(0, 0, 0, c);
            walkFor(p, c, 2.0f, fps[i], 0, 1, false);                   // acelera e anda
            walkFor(p, c, 1.0f, fps[i], 0, 0, false);                   // solta: freia até parar
            dist[i] = p.z;
        }
        for (int i = 1; i < fps.length; i++)
            assertEquals(dist[0], dist[i], 0.02f, "percurso a " + fps[i] + " FPS difere do de " + fps[0]);
        // e é o percurso esperado: ~2 s de caminhada menos a aceleração, mais a frenagem
        assertTrue(dist[0] > 2 * Player.WALK - 0.2f && dist[0] < 2 * Player.WALK + 0.2f, "percurso " + dist[0]);
    }

    @Test
    void accelerationAndBrakingAreShortButNotInstant() {
        Player p = new Player();
        Collision c = flat(0);
        p.place(0, 0, 0, c);
        p.update(1 / 60f, 0, 1, true, c);
        assertTrue(p.speed() > 0 && p.speed() < Player.RUN * 0.3f, "não salta para a velocidade máxima");
        walkFor(p, c, 0.5f, 60, 0, 1, true);
        assertEquals(Player.RUN, p.speed(), 0.01f, "corre na velocidade da passada em menos de meio segundo");
        walkFor(p, c, 0.4f, 60, 0, 0, true);
        assertEquals(0, p.speed(), 1e-3f, "para em menos de meio segundo");
    }

    @Test
    void turningFollowsTheMovementSmoothly() {
        Player p = new Player();
        Collision c = flat(0);
        p.place(0, 0, 0, c);
        walkFor(p, c, 0.5f, 60, 0, 1, false);
        assertEquals(0, p.yaw, 0.01f);
        p.update(1 / 60f, 1, 0, false, c);                              // vira para +x: não gira tudo num quadro
        assertTrue(p.yaw > 0 && p.yaw < Math.PI / 2 - 0.3f, "giro limitado por quadro: " + p.yaw);
        walkFor(p, c, 0.6f, 60, 1, 0, false);
        assertEquals(Math.PI / 2, p.yaw, 0.03f);
    }

    @Test
    void animationFollowsTheRealSpeedAndPlantsTheFeet() {
        Player p = new Player();
        Collision c = flat(0);
        p.place(0, 0, 0, c);
        assertEquals(Animator.Clip.IDLE, p.clip);
        walkFor(p, c, 1f, 60, 0, 1, false);
        assertEquals(Animator.Clip.WALK, p.clip);
        // em regime: a fase avança exatamente pela distância (passada fixa → pé de apoio parado)
        float t0 = p.animT, d0 = p.traveled;
        walkFor(p, c, 1f, 60, 0, 1, false);
        assertEquals((p.traveled - d0) / Player.WALK, p.animT - t0, 0.01f);
        walkFor(p, c, 1f, 60, 0, 1, true);
        assertEquals(Animator.Clip.RUN, p.clip);
        float r0 = p.animT, rd = p.traveled;
        walkFor(p, c, 1f, 60, 0, 1, true);
        assertEquals((p.traveled - rd) / Player.RUN, p.animT - r0, 0.01f);
        walkFor(p, c, 1f, 60, 0, 0, false);
        assertEquals(Animator.Clip.IDLE, p.clip);
        Animator.Motion m = p.motion(new Animator.Motion());
        assertEquals(Animator.Clip.IDLE, m.clip);
    }

    // ------------------------------------------------------------------ colisão e chão

    @Test
    void obstaclesBlockAndTheBodySlidesAlongWalls() {
        Collision c = flat(0);
        c.shapes.add(Collision.Shape.circle("pedra", 0, 3, 0.5f, 0, 1, true, true));
        Player p = new Player();
        p.place(0, 0, 0, c);
        float dt = 1 / 60f;
        for (int i = 0; i < 300; i++) {
            p.update(dt, 0, 1, true, c);
            assertTrue(Math.hypot(p.x, p.z - 3) >= 0.5f + Player.RADIUS - 1e-3f, "entrou na pedra no quadro " + i);
        }
        assertTrue(p.blocked);
        // parede comprida inclinada 30°: andando de frente nela, desliza ao longo (não gruda)
        Collision w = flat(0);
        w.shapes.add(Collision.Shape.box("muro", 0, 2, 3f, 0.2f, (float) Math.toRadians(30), 0, 2, true, true));
        Player q = new Player();
        q.place(0, 0, 0, w);
        for (int i = 0; i < 180; i++) q.update(dt, 0, 1, false, w);
        Collision.Shape s = w.shapes.get(0);
        assertNull(s.push(q.x, q.z, Player.RADIUS - 0.01f), "dentro do muro");
        assertTrue(Math.abs(q.x) > 0.5f, "deslizou ao longo do muro: x = " + q.x);
    }

    @Test
    void smallStepsAreClimbedAndTallOnesBlock() {
        float dt = 1 / 60f;
        Collision low = stepAt(2f, 0.25f);                                // 2 voxels da E: sobe
        Player p = new Player();
        p.place(0, 0, 0, low);
        for (int i = 0; i < 240; i++) {
            p.update(dt, 0, 1, false, low);
            assertEquals(low.ground.height(p.x, p.z), p.y, 1e-4f, "pés no chão");
        }
        assertTrue(p.z > 3f, "subiu o degrau baixo");
        Collision high = stepAt(2f, 0.5f);                                // 4 voxels: bloqueia
        Player q = new Player();
        q.place(0, 0, 0, high);
        for (int i = 0; i < 240; i++) q.update(dt, 0, 1, false, high);
        assertTrue(q.z < 2f, "desnível alto bloqueia: z = " + q.z);
        assertEquals(0f, q.y, 1e-4f);
        assertTrue(q.blocked);
    }

    @Test
    void ledgesDropWithGravityInsteadOfTeleporting() {
        Collision c = new Collision((x, z) -> z >= 1f ? -1.2f : 0f);
        c.waterLevel = -100;
        Player p = new Player();
        p.place(0, 0, 0, c);
        float dt = 1 / 60f;
        boolean wasAir = false;
        float prevY = 0;
        for (int i = 0; i < 120; i++) {
            p.update(dt, 0, 1, false, c);
            if (!p.grounded) {
                wasAir = true;
                assertTrue(prevY - p.y < 0.5f, "queda contínua, sem salto");
            }
            prevY = p.y;
        }
        assertTrue(wasAir, "passou pelo ar");
        assertTrue(p.grounded);
        assertEquals(-1.2f, p.y, 1e-4f);
    }

    // ------------------------------------------------------------------ câmera

    @Test
    void cameraIsStableWhileStandingAndOnStairs() {
        Collision c = new Collision((x, z) -> (float) Math.floor(z / 0.5f) * 0.125f);   // escada de 1 voxel a cada 0,5 u
        c.waterLevel = -100;
        Player p = new Player();
        p.place(0, 0, 0, c);
        FollowCamera cam = new FollowCamera();
        cam.yaw = (float) Math.PI;
        float dt = 1 / 60f;
        Vector3f prev = new Vector3f(), pos = new Vector3f();
        float maxVy = 0;
        int reversals = 0;
        for (int i = 0; i < 360; i++) {
            p.update(dt, 0, 1, false, c);
            cam.update(dt, p.x, p.visualY, p.z, c);
            cam.position(pos);
            if (i > 30) {
                float vy = (pos.y - prev.y) / dt;
                if (vy < -1e-4f) reversals++;                             // subindo a escada, a câmera nunca desce
                maxVy = Math.max(maxVy, vy);
            }
            prev.set(pos);
        }
        // a escada sobe 0,125 u a cada 0,5 u (≈ 0,29 u/s andando): a câmera sobe sem bater nem quicar
        assertEquals(0, reversals, "câmera quicou nos degraus");
        assertTrue(maxVy < 0.9f, "tranco vertical da câmera: " + maxVy + " u/s");
        // parado: posição constante
        for (int i = 0; i < 120; i++) { p.update(dt, 0, 0, false, c); cam.update(dt, p.x, p.visualY, p.z, c); }
        Vector3f a = cam.position(new Vector3f());
        p.update(dt, 0, 0, false, c);
        cam.update(dt, p.x, p.visualY, p.z, c);
        assertTrue(a.distance(cam.position(new Vector3f())) < 1e-3f);
    }

    @Test
    void cameraNeverCrossesWallsSwingsAsideAndComesBackSlowly() {
        float dt = 1 / 60f;
        // parede comprida logo atrás do herói (câmera do lado −z): a câmera não atravessa e, em vez de encostar
        // na cabeça, desvia para o lado/para cima
        Collision c = flat(0);
        c.shapes.add(Collision.Shape.box("parede", 0, -1.2f, 6f, 0.3f, 0, 0, 4, true, true));
        FollowCamera cam = new FollowCamera();
        cam.yaw = (float) Math.PI;
        cam.pitch = 0.2f;
        for (int i = 0; i < 240; i++) {
            cam.update(dt, 0, 0, 0, c);
            Vector3f pos = cam.position(new Vector3f());
            assertFalse(c.shapes.getFirst().contains(pos.x, pos.y, pos.z, 0f), "câmera dentro da parede no quadro " + i);
            if (i > 20) assertEquals(1f, c.rayClear(cam.pivot, pos, 0f), 1e-3f, "parede entre a câmera e o herói no quadro " + i);
        }
        assertTrue(cam.dist > 1.6f, "desviou em vez de colar na cabeça: " + cam.dist);
        assertTrue(Math.abs(cam.avoidYaw) + cam.avoidPitch > 0.2f, "houve desvio");
        assertEquals((float) Math.PI, cam.yaw, 1e-6f, "o rumo escolhido pelo jogador não muda");
        // parede some: desvio volta a 0 e a distância volta devagar
        c.shapes.clear();
        float d0 = cam.dist;
        cam.update(dt, 0, 0, 0, c);
        assertTrue(cam.dist - d0 < 0.2f, "não salta de volta");
        for (int i = 0; i < 360; i++) cam.update(dt, 0, 0, 0, c);
        assertEquals(FollowCamera.DEFAULT_DIST, cam.dist, 0.02f);
        assertEquals(0f, cam.avoidYaw, 0.01f);
        assertEquals(0f, cam.avoidPitch, 0.01f);
        // corredor fechado (paredes altas dos dois lados e atrás): sem desvio possível, a câmera encosta
        Collision box = flat(0);
        box.shapes.add(Collision.Shape.box("fundo", 0, -1.4f, 3f, 0.3f, 0, 0, 12, true, true));
        box.shapes.add(Collision.Shape.box("esq", -1.4f, 0, 0.3f, 3f, 0, 0, 12, true, true));
        box.shapes.add(Collision.Shape.box("dir", 1.4f, 0, 0.3f, 3f, 0, 0, 12, true, true));
        FollowCamera c2 = new FollowCamera();
        c2.yaw = (float) Math.PI;
        for (int i = 0; i < 240; i++) {
            c2.update(dt, 0, 0, 0, box);
            Vector3f pos = c2.position(new Vector3f());
            for (Collision.Shape s : box.shapes) assertFalse(s.contains(pos.x, pos.y, pos.z, 0f), "câmera dentro de " + s.name);
        }
        assertTrue(c2.dist < 2.0f, "encostou (bem abaixo dos 4,6 u pedidos): " + c2.dist);
        // terreno: nunca abaixo do chão
        Collision hill = new Collision((x, z) -> z < -1 ? 3f : 0f);
        hill.waterLevel = -100;
        FollowCamera c3 = new FollowCamera();
        c3.yaw = (float) Math.PI;
        c3.pitch = 0.05f;
        for (int i = 0; i < 120; i++) c3.update(dt, 0, 0, 0, hill);
        Vector3f p3 = c3.position(new Vector3f());
        assertTrue(p3.y > hill.ground.height(p3.x, p3.z), "câmera dentro do morro");
    }

    @Test
    void cameraLimitsZoomAndPitch() {
        FollowCamera cam = new FollowCamera();
        for (int i = 0; i < 50; i++) cam.zoom(1);
        assertEquals(FollowCamera.MIN_DIST, cam.wantDist, 1e-4f);
        for (int i = 0; i < 80; i++) cam.zoom(-1);
        assertEquals(FollowCamera.MAX_DIST, cam.wantDist, 1e-4f);
        cam.look(0, 1e6f);
        assertEquals(FollowCamera.MAX_PITCH, cam.pitch, 1e-4f);
        cam.look(0, -1e6f);
        assertEquals(FollowCamera.MIN_PITCH, cam.pitch, 1e-4f);
        float y = cam.yaw;
        cam.sensitivity *= 2;
        cam.look(100, 0);
        assertEquals(y - 100 * cam.sensitivity, cam.yaw, 1e-5f, "sensibilidade ajustável");
    }

    // ------------------------------------------------------------------ cena real (relevo T1 da vila)

    static GameLogic village() {
        SceneDef s = SceneDef.load(A, "jogo_vila_e");
        Collision c = Collision.build(Terrain.e(), Collision.profiles(A), s.handPlaced, s.bodies, s);
        GameLogic g = new GameLogic(s, c);
        g.appearance = Appearance.preset("aventureiro");
        return g;
    }

    @Test
    void playableSceneLoadsWithCollidersAndKelyra() {
        GameLogic g = village();
        assertEquals(6, g.npcs.size(), "Kelyra e os cinco NPCs de ofício");
        GameLogic.Npc k = g.npcs.getFirst();
        assertEquals("Kelyra", k.def.name());
        assertTrue(k.def.lines().size() >= 3);
        assertTrue(g.collision.walkable(g.player.x, g.player.z), "início pisável");
        assertEquals(Terrain.e().groundAt(g.player.x, g.player.z), g.player.y, 1e-5f, "início no chão");
        List<Collision.Shape> house = g.collision.named("house");
        assertEquals(1, house.size());
        assertFalse(house.getFirst().circle);
        assertTrue(house.getFirst().hx > 2f && house.getFirst().hz > 1.8f, "casa com a pegada da malha");
        assertTrue(g.collision.named("flower_").isEmpty(), "flores atravessáveis");
        assertFalse(g.collision.named("tree_willow:copa").isEmpty(), "copa do salgueiro bloqueia a câmera");
        assertFalse(g.collision.named("tree_willow:copa").getFirst().body, "copa não bloqueia o corpo");
        assertFalse(g.collision.named("characters/npc/kelyra").isEmpty(), "Kelyra ocupa espaço");
        // a cena canônica e a vila continuam sem a seção de jogo (cópia controlada)
        assertThrows(IllegalArgumentException.class, () -> SceneDef.load(A, "vila_e"));
        assertThrows(IllegalArgumentException.class, () -> SceneDef.load(A, "clearing_e"));
    }

    @Test
    void everyResidentStandsOnFreeGroundWithRoomToTalk() {
        // integração da 09.4: os NPCs de ofício ficam em vagas medidas — chão caminhável dentro do limite, ocupando
        // espaço, sem NENHUMA forma (corpo ou câmera: pedra, poste, cerca, copa) a menos de 1,6 u, céu livre acima da
        // cabeça (a câmera de conversa não entra numa copa) e longe uns dos outros. A Kelyra fica junto à estalagem
        // por desenho (08) e só entra nas regras gerais.
        GameLogic g = village();
        for (GameLogic.Npc n : g.npcs) {
            float x = n.def.x(), z = n.def.z();
            assertTrue(g.collision.walkable(x, z) && g.collision.insideLimit(x, z), n.def.name() + ": chão caminhável na vila");
            assertFalse(g.collision.named(n.def.rig()).isEmpty(), n.def.name() + " ocupa espaço");
            assertTrue(n.def.lines().size() >= 3, n.def.name() + ": pelo menos três falas");
            for (GameLogic.Npc o : g.npcs)
                if (o != n) assertTrue(Math.hypot(x - o.def.x(), z - o.def.z()) > 2.5f, n.def.name() + " colado em " + o.def.name());
            if (n.def.name().equals("Kelyra")) continue;
            for (Collision.Shape sh : g.collision.named(""))
                if (!sh.name.startsWith(n.def.rig()))
                    assertNull(sh.push(x, z, 1.6f), n.def.name() + ": " + sh.name + " a menos de 1,6 u");
            float y = Terrain.e().groundAt(x, z);
            assertTrue(g.collision.rayClear(new Vector3f(x, y + 1.2f, z), new Vector3f(x, y + 4.5f, z), 0f) > 0.999f,
                    n.def.name() + ": copa por cima da cabeça");
        }
    }

    @Test
    void releasingAllInputDropsKeysHeldWhenAnotherScreenTookTheWindow() {
        Input in = new Input();
        in.feed(List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W)));
        assertTrue(in.down.contains(GLFW_KEY_W));
        in.releaseAll();                                                  // o criador recebeu o KEY_UP do W
        assertTrue(in.down.isEmpty());
        assertFalse(in.rightDown);
    }

    @Test
    void terrainUsedByGameplayIsTheDrawnOne() {
        Terrain t = Terrain.e();
        // o chão do jogo é o topo do voxel quantizado do relevo contínuo (T1 oscila o limiar em até ~0,6 voxel)
        for (float x = -10; x <= 10; x += 0.7f)
            for (float z = -10; z <= 10; z += 0.7f) {
                float g = t.groundAt(x, z), h = World.height(x, z, false);
                assertEquals(h, g, 0.2f, "chão longe do relevo em " + x + "," + z);
                assertEquals(0, Math.round((g - World.Y_MIN) / t.voxel) - (g - World.Y_MIN) / t.voxel, 1e-3f, "altura em voxels inteiros");
            }
    }

    /** O herói consegue sair dali em pelo menos uma das 8 direções (colisão não o prende). */
    static boolean canEscape(Player p, Collision c) {
        for (int k = 0; k < 8; k++) {
            Player q = new Player();
            q.place(p.x, p.z, p.yaw, c);
            q.y = p.y;
            double a = k * Math.PI / 4;
            for (int i = 0; i < 20; i++) q.update(1 / 60f, (float) Math.sin(a), (float) Math.cos(a), false, c);
            if (Math.hypot(q.x - p.x, q.z - p.z) > 0.05f) return true;
        }
        return false;
    }

    @Test
    void walkingAcrossTheVillageKeepsFeetOnTheGroundAndNeverEntersProps() {
        GameLogic g = village();
        Player p = g.player;
        Collision c = g.collision;
        float dt = 1 / 60f;
        // passeio: começa no caminho, vai até a casa, desvia, volta pelo lado do lago
        float[][] targets = {{-2.0f, 0.5f}, {-4.0f, -4.0f}, {0.5f, -3.0f}, {4.5f, 0.5f}, {3.0f, 6.0f}, {-3.0f, 6.0f}};
        int reached = 0;
        for (float[] t : targets) {
            int noProgress = 0, side = 1;
            for (int i = 0; i < 60 * 15; i++) {
                float dx = t[0] - p.x, dz = t[1] - p.z, d = (float) Math.hypot(dx, dz);
                if (d < 0.5f) { reached++; break; }
                float bx = p.x, bz = p.z;
                // como um jogador: segue para o alvo; encostado, contorna; sem avançar, tenta o outro lado
                float ix = dx / d, iz = dz / d;
                if (p.blocked) { float k = ix; ix = ix * 0.3f - iz * side; iz = iz * 0.3f + k * side; }
                p.update(dt, ix, iz, i % 240 > 120, c);
                if (Math.hypot(p.x - bx, p.z - bz) < 1e-4f) {
                    if (++noProgress % 30 == 0) side = -side;
                    if (noProgress % 60 == 1) { assertTrue(canEscape(p, c), "preso de verdade em " + p.x + "," + p.z); }
                } else noProgress = 0;
                if (p.grounded) assertEquals(c.ground.height(p.x, p.z), p.y, 1e-4f, "pés fora do chão");
                assertTrue(c.walkable(p.x, p.z), "fora do pisável");
                for (Collision.Shape s : c.shapes)
                    if (s.body && s.y1 > p.y + Player.STEP && s.y0 < p.y + Player.HEIGHT)
                        assertNull(s.push(p.x, p.z, Player.RADIUS - 0.02f), "dentro de " + s.name + " em " + p.x + "," + p.z);
            }
        }
        assertEquals(targets.length, reached, "chegou a " + reached + " de " + targets.length + " pontos");
    }

    @Test
    void pondWaterIsNotWalkable() {
        GameLogic g = village();
        Player p = g.player;
        p.place(4.0f, 4.6f, (float) (Math.PI / 2), g.collision);           // margem oeste do lago, olhando para leste
        for (int i = 0; i < 600; i++) p.update(1 / 60f, 1, 0, true, g.collision);
        assertTrue(g.collision.ground.height(p.x, p.z) >= World.WATER_LEVEL + 0.06f, "entrou na água (ou na margem rente a ela)");
        assertTrue(p.x < 7.8f - 1f, "atravessou o lago: x = " + p.x);
    }

    @Test
    void theHeroCanReachKelyraFromTheStart() {
        GameLogic g = village();
        g.action("comecar");
        GameLogic.Npc k = g.npcs.getFirst();
        float dt = 1 / 60f;
        int frames = 0;
        while (g.findCandidate() == null && frames++ < 60 * 20) {
            float dx = k.def.x() - g.player.x, dz = k.def.z() - g.player.z, d = (float) Math.hypot(dx, dz);
            // contorna o que estiver no caminho: desvia para a direita quando bloqueado
            float ix = dx / d, iz = dz / d;
            if (g.player.blocked) { float t = ix; ix = ix * 0.3f - iz; iz = iz * 0.3f + t; }
            g.player.update(dt, ix, iz, false, g.collision);
        }
        assertNotNull(g.findCandidate(), "não chegou até a Kelyra em 20 s");
    }

    // ------------------------------------------------------------------ interação, diálogo, pausa

    static List<InputEvent> key(int k) { return List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, k), new InputEvent(Kind.KEY_UP, 0, 0, 0, 0, k)); }

    static void frame(GameLogic g, Input in, List<InputEvent> evs) {
        in.feed(evs);
        g.update(1 / 60f, in);
    }

    @Test
    void interactionNeedsDistanceAndFacing() {
        GameLogic g = village();
        GameLogic.Npc k = g.npcs.getFirst();
        float kx = k.def.x(), kz = k.def.z();
        // perto e de frente
        g.player.place(kx, kz - 1.2f, 0f, g.collision);
        assertSame(k, g.findCandidate());
        // perto, de costas
        g.player.place(kx, kz - 1.2f, (float) Math.PI, g.collision);
        assertNull(g.findCandidate());
        // de frente, mas longe
        g.player.place(kx, kz - (k.def.range() + 0.3f), 0f, g.collision);
        assertNull(g.findCandidate());
        // na borda do alcance, de lado (dentro do ângulo)
        g.player.place(kx - 1.0f, kz - 1.0f, (float) Math.toRadians(80), g.collision);
        assertSame(k, g.findCandidate());
    }

    @Test
    void dialogStartsAdvancesEndsAndPreservesThePlayer() {
        GameLogic g = village();
        Input in = new Input();
        g.action("comecar");
        GameLogic.Npc k = g.npcs.getFirst();
        g.player.place(k.def.x(), k.def.z() - 1.3f, 0.1f, g.collision);
        frame(g, in, List.of());
        float x = g.player.x, z = g.player.z;
        frame(g, in, key(GLFW_KEY_E));
        assertEquals(GameLogic.State.DIALOG, g.state);
        assertSame(k, g.talking);
        assertTrue(g.cam.framing());
        // segurar W durante a conversa não move o herói
        List<InputEvent> w = List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W));
        frame(g, in, w);
        for (int i = 0; i < 60; i++) frame(g, in, List.of());
        assertEquals(x, g.player.x, 1e-3f);
        assertEquals(z, g.player.z, 1e-3f);
        frame(g, in, List.of(new InputEvent(Kind.KEY_UP, 0, 0, 0, 0, GLFW_KEY_W)));
        // Kelyra vira para o herói
        for (int i = 0; i < 90; i++) frame(g, in, List.of());
        float want = (float) Math.atan2(g.player.x - k.def.x(), g.player.z - k.def.z());
        assertEquals(0, Math.atan2(Math.sin(want - k.yaw), Math.cos(want - k.yaw)), 0.05f);
        // rodada 10: a Kelyra oferece a missão — as falas vêm do contexto e a última tem escolha (E = a selecionada)
        assertEquals("oferta", g.dialogContext);
        int n = g.dialogLines.size();
        for (int i = 0; i < n - 1; i++) {
            frame(g, in, key(GLFW_KEY_E));
            assertEquals(GameLogic.State.DIALOG, g.state);
            assertEquals(i + 1, g.line);
        }
        assertFalse(g.dialogChoices.isEmpty());
        frame(g, in, key(GLFW_KEY_DOWN));                                  // "Agora não"
        assertEquals(1, g.choiceSelected);
        frame(g, in, key(GLFW_KEY_E));                                     // recusa: a reação da Kelyra
        assertEquals(GameLogic.State.DIALOG, g.state);
        assertTrue(g.quests.get("quest.trilha.luzes_do_posto").declined());
        for (int i = 0; i < g.dialogLines.size() - 1; i++) frame(g, in, key(GLFW_KEY_E));
        frame(g, in, key(GLFW_KEY_E));                                    // última fala → volta a explorar
        assertEquals(GameLogic.State.EXPLORE, g.state);
        assertNull(g.talking);
        assertFalse(g.cam.framing());
        // Esc no meio da conversa também encerra
        g.player.place(k.def.x(), k.def.z() - 1.3f, 0.1f, g.collision);
        frame(g, in, key(GLFW_KEY_E));
        frame(g, in, key(GLFW_KEY_ESCAPE));
        assertEquals(GameLogic.State.EXPLORE, g.state);
        // tecla com repetição automática não abre conversa de novo
        frame(g, in, List.of(new InputEvent(Kind.KEY, 0, 0, 1, 0, GLFW_KEY_E)));
        assertEquals(GameLogic.State.EXPLORE, g.state);
    }

    @Test
    void pausePreservesPositionOrientationAndCamera() {
        GameLogic g = village();
        Input in = new Input();
        g.action("comecar");
        List<InputEvent> w = List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W));
        frame(g, in, w);
        for (int i = 0; i < 40; i++) frame(g, in, List.of());
        frame(g, in, key(GLFW_KEY_ESCAPE));
        assertEquals(GameLogic.State.PAUSE, g.state);
        float x = g.player.x, z = g.player.z, yaw = g.player.yaw, cy = g.cam.yaw;
        for (int i = 0; i < 120; i++) frame(g, in, List.of());            // W ainda segurado: pausado não anda
        assertEquals(x, g.player.x, 1e-6f);
        assertEquals(z, g.player.z, 1e-6f);
        assertEquals(yaw, g.player.yaw, 1e-6f);
        assertEquals(cy, g.cam.yaw, 1e-6f);
        frame(g, in, key(GLFW_KEY_ESCAPE));
        assertEquals(GameLogic.State.EXPLORE, g.state);
    }

    @Test
    void mouseTurnsTheCameraAndMovementIsCameraRelative() {
        GameLogic g = village();
        Input in = new Input();
        g.action("comecar");
        frame(g, in, List.of(new InputEvent(Kind.MOVE, 100, 100, 0, 0, 0)));
        float y0 = g.cam.yaw;
        frame(g, in, List.of(new InputEvent(Kind.MOVE, 300, 100, 0, 0, 0)));
        assertEquals(y0 - 200 * g.cam.sensitivity, g.cam.yaw, 1e-5f);
        for (int i = 0; i < 60; i++) frame(g, in, List.of());             // câmera assenta
        float fx = g.cam.forwardX(), fz = g.cam.forwardZ(), x0 = g.player.x, z0 = g.player.z;
        frame(g, in, List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W)));
        for (int i = 0; i < 30; i++) frame(g, in, List.of());
        float mx = g.player.x - x0, mz = g.player.z - z0, m = (float) Math.hypot(mx, mz);
        assertTrue(m > 0.2f);
        assertTrue((mx * fx + mz * fz) / m > 0.95f, "W anda para onde a câmera olha");
    }

    // ------------------------------------------------------------------ aparência

    @Test
    void appearanceChoicesIncludePresetsAndSavedFilesAndDriveTheComposer(@TempDir Path tmp) throws Exception {
        ComponentCatalog cat = ComponentCatalog.load(A);
        Appearance mine = Appearance.preset("exploradora");
        mine.hair = "cabelo.curto";
        mine.name = "Brisa";
        mine.save(tmp.resolve("brisa-001.json"), false);
        Files.writeString(tmp.resolve("quebrada.json"), "{\"contrato\":\"valumo.aparencia\",\"versao\":1,\"origem\":{\"preset\":\"dragao\"}}");
        GameLogic g = village();
        List<String> composed = new ArrayList<>();
        g.onAppearance = a -> composed.add(a.signature());
        g.loadChoices(cat, tmp);
        assertEquals(5, g.choices.size());
        assertEquals("Aventureiro", g.choices.get(0).label());
        assertEquals(Appearance.preset("aventureiro").signature(), g.appearance.signature());
        int brisa = -1, broken = -1;
        for (int i = 0; i < g.choices.size(); i++) {
            if (g.choices.get(i).label().equals("Brisa")) brisa = i;
            if (!g.choices.get(i).valid()) broken = i;
        }
        assertTrue(brisa >= 0 && broken >= 0);
        g.action("escolha:" + brisa);
        assertEquals(mine.signature(), g.appearance.signature());
        assertEquals(mine.signature(), composed.getLast(), "a escolha vai para o mesmo compositor");
        g.action("escolha:" + broken);
        assertEquals(mine.signature(), g.appearance.signature(), "arquivo inválido não troca a aparência");
        assertNotNull(g.message);
        // a aparência não decide classe nem equipamento: só a aparência entra no jogo
        g.action("comecar");
        assertEquals(GameLogic.State.EXPLORE, g.state);
        // vinda do criador (em memória)
        Appearance made = Appearance.preset("maga");
        made.cover = Appearance.NO_COVER;
        g.useCreated(made, "Criada agora");
        assertEquals(made.signature(), g.appearance.signature());
    }

    @Test
    void choicesMatchSavedFilesWrittenByTheCreatorFormat(@TempDir Path tmp) throws Exception {
        ComponentCatalog cat = ComponentCatalog.load(A);
        GameLogic g = village();
        g.loadChoices(cat, tmp.resolve("nao-existe"));
        assertEquals(3, g.choices.size(), "sem pasta: só os presets");
    }
}
