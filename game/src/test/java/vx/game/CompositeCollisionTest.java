package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vx.viewer.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 12 — colisão composta (construções com interior), na colisão REAL do jogo ({@link Collision}, o mesmo contrato do
 * movimento, da câmera e da conferência de alcance). Cada asset composto, sozinho num chão plano, deslocado da origem e
 * girado 0°, 90°, 180°, 270° e 37°:
 * <ol>
 *   <li>as colunas sólidas medidas do modelo (catálogo) ficam dentro de formas de CORPO (parede não se atravessa);</li>
 *   <li>cada entrada é alcançável de fora, e cada ponto de interação no chão é alcançável lá dentro — com os NPCs nos
 *       spawns (corpo 0,3 u) — e dá para falar com cada NPC de frente;</li>
 *   <li>a câmera não atravessa as paredes, e o telhado (forma só de câmera) e a copa acima da cabeça seguram a câmera de quem
 *   está dentro;</li>
 *   <li>escala e translação acompanham o prop.</li>
 * </ol>
 * A busca é a mesma da {@link Reachability} (célula livre = o herói cabe sem encostar em sólido acima de um degrau; passo
 * ≤ {@link Player#STEP}), numa grade de 0,125 u para o resultado não depender do alinhamento da grade com a peça.
 */
class CompositeCollisionTest {
    static final Path A = Path.of("assets");
    static final float[] YAWS = {0f, 90f, 180f, 270f, 37f};
    static final String[] COMPOSITE = {"workshop_forge", "ruin_tower", "ruin_house", "market_stall"};
    /** Os que se entram (a torre arruinada não: ver {@link #aTorreArruinadaTemAPortaEstreitaDemaisENaoELiberada}). */
    static final String[] ENTERABLE = {"workshop_forge", "ruin_house", "market_stall", "ruin_tower"};
    static final float CELL = 0.125f, NPC = 0.3f;
    static final float OX = 6.0f, OZ = -4.0f;                            // longe da origem: a translação também conta

    static JsonObject catalog() throws Exception {
        JsonObject out = new JsonObject();
        for (JsonElement e : JsonParser.parseString(Files.readString(A.resolve("environment/e/conteudo_catalogo.json")))
                .getAsJsonObject().getAsJsonArray("assets")) out.add(e.getAsJsonObject().get("id").getAsString(), e);
        return out;
    }

    /** Colisão com só o prop (girado {@code yaw}°, escala {@code s}) e, se pedido, os NPCs nos spawns de tipo npc. */
    static Collision build(String name, float yaw, float s, boolean npcs, JsonObject rec) {
        Collision c = new Collision((x, z) -> 0f);
        float[] b = SceneDef.propBounds(A, "e").get(name);
        assertNotNull(b, name + " no props.json");
        Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw)).scale(s);
        c.addProp(Collision.profiles(A), new World.Placement(name, m, b));
        if (npcs) for (float[] p : points(rec, "spawns", "npc", yaw, s))
            c.shapes.add(Collision.Shape.circle("npc", p[0], p[2], NPC, 0f, 1.3f, true, false));
        c.limitX = OX;
        c.limitZ = OZ;
        c.limitR = 30f;
        return c;
    }

    /** Pontos do catálogo no mundo: {x, y, z, rumo°}. {@code tipo} filtra spawns (null = todos). */
    static List<float[]> points(JsonObject rec, String kind, String tipo, float yaw, float s) {
        List<float[]> out = new ArrayList<>();
        Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw)).scale(s);
        for (JsonElement e : rec.getAsJsonObject("pontos").getAsJsonArray(kind)) {
            JsonObject p = e.getAsJsonObject();
            if (tipo != null && !tipo.equals(p.get("tipo").getAsString())) continue;
            JsonArray u = p.getAsJsonArray("pos_u");
            Vector3f w = m.transformPosition(new Vector3f(u.get(0).getAsFloat(), u.get(1).getAsFloat(), u.get(2).getAsFloat()));
            out.add(new float[]{w.x, w.y, w.z, (p.has("rumo") ? p.get("rumo").getAsFloat() : 0f) + yaw});
        }
        return out;
    }

    /** Grade livre (o herói cabe) e a busca a partir de {@code (sx, sz)}. */
    record Flood(float x0, float z0, int n, boolean[] free, boolean[] seen) {
        boolean near(float x, float z, float within) {
            for (int iz = 0; iz < n; iz++)
                for (int ix = 0; ix < n; ix++) {
                    if (!seen[ix + iz * n]) continue;
                    float cx = x0 + (ix + 0.5f) * CELL, cz = z0 + (iz + 0.5f) * CELL;
                    if (Math.hypot(cx - x, cz - z) <= within) return true;
                }
            return false;
        }

        boolean fits(float x, float z) {
            int ix = (int) ((x - x0) / CELL), iz = (int) ((z - z0) / CELL);
            return ix >= 0 && iz >= 0 && ix < n && iz < n && free[ix + iz * n];
        }
    }

    static Flood flood(Collision c, float sx, float sz) { return flood(c, sx, sz, Player.RADIUS); }

    /** A busca com um corpo de raio {@code rad} (a folga: um corpo mais largo que o do herói também passa?). */
    static Flood flood(Collision c, float sx, float sz, float rad) {
        float x0 = OX - 8f, z0 = OZ - 8f;
        int n = (int) (16f / CELL);
        boolean[] free = new boolean[n * n], seen = new boolean[n * n];
        float[] h = new float[n * n];
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                float x = x0 + (ix + 0.5f) * CELL, z = z0 + (iz + 0.5f) * CELL;
                h[ix + iz * n] = c.floor(x, z, 99f, Player.RADIUS);
                free[ix + iz * n] = !c.bodyAt(x, z, h[ix + iz * n], rad, Player.HEIGHT);
            }
        int s = (int) ((sx - x0) / CELL) + (int) ((sz - z0) / CELL) * n;
        assertTrue(free[s], "o ponto de partida (fora do prop) é livre");
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(s);
        seen[s] = true;
        while (!q.isEmpty()) {
            int k = q.poll(), ix = k % n, iz = k / n;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int jx = ix + d[0], jz = iz + d[1], j = jx + jz * n;
                if (jx < 0 || jz < 0 || jx >= n || jz >= n || seen[j] || !free[j] || Math.abs(h[j] - h[k]) > Player.STEP) continue;
                seen[j] = true;
                q.add(j);
            }
        }
        return new Flood(x0, z0, n, free, seen);
    }

    /** Partida: 3,5 u à frente da primeira entrada, no rumo dela (fora do prop). */
    static float[] outside(JsonObject rec, float yaw) {
        float[] e = points(rec, "entradas", null, yaw, 1f).getFirst();
        double a = Math.toRadians(e[3]);
        return new float[]{e[0] + (float) Math.sin(a) * 3.5f, e[2] + (float) Math.cos(a) * 3.5f};
    }

    /**
     * Contra os VOXELS do modelo (não contra o script que mediu): todo voxel sólido (não folhagem) com a base na faixa do
     * corpo — acima do degrau que se sobe a pé (0,375 u) e abaixo da cabeça do herói ({@link Player#HEIGHT}) — fica dentro
     * de uma forma de corpo, e o herói não cabe ali.
     */
    @Test
    void osVoxelsDaFaixaDoCorpoFicamDentroDeFormasDeCorpo() throws Exception {
        JsonObject man = JsonParser.parseString(Files.readString(A.resolve("environment/e/props.json"))).getAsJsonObject();
        for (String name : COMPOSITE) {
            JsonObject e = man.getAsJsonObject(name);
            float vs = e.get("voxelSize").getAsFloat();
            JsonArray o = e.getAsJsonArray("origin");
            vx.engine.VoxModel vox = vx.engine.VoxModel.load(A.resolve("environment/e/" + e.get("file").getAsString()));
            for (float yaw : YAWS) {
                Collision c = build(name, yaw, 1f, false, catalog().getAsJsonObject(name));
                Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw));
                int checked = 0;
                for (int x = 0; x < vox.sx; x++)
                    for (int z = 0; z < vox.sz; z++)
                        for (int y = 0; y < vox.sy; y++) {
                            int v = vox.get(x, y, z);
                            if (v == 0 || (v >>> 24) - 1 == vx.engine.VoxModel.MAT_FOLIAGE) continue;
                            float y0 = (y + o.get(1).getAsFloat()) * vs;
                            if (y0 < 0.375f - 1e-4f || y0 >= Player.HEIGHT) continue;
                            float lx = (x + o.get(0).getAsFloat() + 0.5f) * vs, lz = (z + o.get(2).getAsFloat() + 0.5f) * vs;
                            Vector3f w = m.transformPosition(new Vector3f(lx, 0, lz));
                            float yy = y0 + vs / 2;
                            boolean in = c.shapes.stream().anyMatch(s -> s.body && s.contains(w.x, yy, w.z, 2e-3f));
                            assertTrue(in, name + " a " + yaw + "°: voxel sólido fora do corpo em (" + lx + ", " + y0 + ", " + lz + ")");
                            checked++;
                            break;                                       // um por coluna basta (a forma vai da base dele para cima)
                        }
                assertTrue(checked > 50, name + ": colunas conferidas " + checked);
            }
        }
    }

    @Test
    void entradasEInteriorAlcancaveisComOsNpcsNoLugar() throws Exception {
        JsonObject cat = catalog();
        for (String name : ENTERABLE)
            for (float yaw : YAWS) {
                JsonObject rec = cat.getAsJsonObject(name);
                Collision c = build(name, yaw, 1f, true, rec);
                float[] o = outside(rec, yaw);
                Flood f = flood(c, o[0], o[1]);
                String at = name + " a " + yaw + "°";
                for (float[] e : points(rec, "entradas", null, yaw, 1f))
                    assertTrue(f.near(e[0], e[2], 0.3f), at + ": entrada fora do alcance em (" + e[0] + ", " + e[2] + ")");
                // o contrato do catálogo: o ponto de interação marca o OBJETO; quem usa fica de pé a até 1 u dele
                int inter = 0;
                for (float[] p : points(rec, "interacao", null, yaw, 1f)) {
                    if (p[1] > 0.6f) continue;                            // ponto no alto (topo da escada da torre): não é de chão
                    assertTrue(f.near(p[0], p[2], 1.0f), at + ": ninguém chega a 1 u do ponto de interação em (" + p[0] + ", " + p[2] + ")");
                    inter++;
                }
                assertTrue(inter >= 1, at + ": nenhum ponto de interação de chão");
                // o interior declarado (área segura) é caminhável de verdade: alguém chega lá dentro
                Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw));
                for (JsonElement e : rec.getAsJsonObject("pontos").getAsJsonArray("area_segura_u")) {
                    JsonArray r = e.getAsJsonArray();
                    int in = 0, reached = 0;
                    for (float lx = r.get(0).getAsFloat(); lx < r.get(2).getAsFloat(); lx += CELL)
                        for (float lz = r.get(1).getAsFloat(); lz < r.get(3).getAsFloat(); lz += CELL) {
                            Vector3f w = m.transformPosition(new Vector3f(lx + CELL / 2, 0, lz + CELL / 2));
                            in++;
                            if (f.near(w.x, w.z, CELL * 0.75f)) reached++;
                        }
                    assertTrue(reached >= Math.max(4, in / 5), at + ": interior pouco caminhável (" + reached + " de " + in + " células)");
                }
                for (float[] n : points(rec, "spawns", "npc", yaw, 1f)) {
                    boolean talk = false;                                 // falar de frente: alguém chega a ~1 u dele, por algum lado
                    for (int k = 0; k < 16 && !talk; k++) {
                        double a = k * Math.PI / 8;
                        float tx = n[0] + (float) Math.sin(a) * 0.95f, tz = n[2] + (float) Math.cos(a) * 0.95f;
                        talk = f.near(tx, tz, 0.2f);
                    }
                    assertTrue(talk, at + ": ninguém chega para falar com o NPC do spawn");
                }
            }
    }

    @Test
    void cameraNaoAtravessaParedesEOTelhadoSeguraQuemEstaDentro() throws Exception {
        JsonObject cat = catalog();
        for (String name : COMPOSITE)
            for (float yaw : YAWS) {
                JsonObject rec = cat.getAsJsonObject(name);
                Collision c = build(name, yaw, 1f, false, rec);
                Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw));
                String at = name + " a " + yaw + "°";
                // paredes: todo raio horizontal que cruza o meio de uma coluna ALTA medida é cortado
                int walls = 0;
                for (Collision.Shape s : c.shapes) {
                    if (!s.body || !s.camera || s.y1 < 1.4f) continue;
                    float yy = (s.y0 + s.y1) / 2;                           // no meio da altura da peça
                    Vector3f a = new Vector3f(s.cx - 3f, yy, s.cz), b = new Vector3f(s.cx + 3f, yy, s.cz);
                    assertTrue(c.rayClear(a, b, 0f) < 0.999f, at + ": a câmera atravessa a parede " + s.name);
                    walls++;
                }
                assertTrue(walls >= 1, at + ": nenhuma parede alta");
                // peças baixas (bigorna, tina, balcão) não seguram a câmera
                for (Collision.Shape s : c.shapes)
                    if (s.body && s.y1 < 1.2f) assertFalse(s.camera, at + ": peça baixa " + s.name + " bloquearia a câmera");
                // telhado/toldo: de dentro (o primeiro ponto de interação de chão), um raio para cima e para trás é cortado
                if (name.equals("workshop_forge") || name.equals("market_stall")) {
                    // de dentro: o primeiro ponto de interação da oficina (a forja), o feirante sob o toldo da barraca
                    float[] p = name.equals("market_stall") ? points(rec, "spawns", "npc", yaw, 1f).getFirst() : points(rec, "interacao", null, yaw, 1f).getFirst();
                    Vector3f eye = new Vector3f(p[0], 1.45f, p[2]);
                    Vector3f up = new Vector3f(p[0], 6f, p[2]);
                    assertTrue(c.rayClear(eye, up, 0f) < 0.999f, at + ": o telhado não segura a câmera de quem está dentro");
                    assertTrue(c.shapes.stream().anyMatch(s -> !s.body && s.camera), at + ": sem forma só de câmera (telhado)");
                }
                // a copa da árvore que cresce dentro da ruína segura a câmera, como a copa das árvores soltas (perfil "tronco"):
                // sem ela, quem examina a lareira some atrás das folhas (visto na captura da rodada 12)
                if (name.equals("ruin_house")) {
                    Vector3f under = m.transformPosition(new Vector3f(0.35f, 1.3f, 0f)), over = m.transformPosition(new Vector3f(0.35f, 6f, 0f));
                    assertTrue(c.rayClear(under, over, 0f) < 0.999f, at + ": a copa da árvore de dentro não segura a câmera");
                }
                assertNotNull(m);
            }
    }

    /**
     * P75 (rodada 12.1) — o piso dos interiores É a laje desenhada. A laje de pedra da oficina e da ruína tem o topo a
     * 0,375 u; na rodada 12 o chão pisável era um degrau a 0,30 u (o herói afundava 0,075 u, e ao chegar andando afundava
     * mais até a altura desenhada alcançar). Agora: (1) por dentro, o chão é o topo EXATO da laje; (2) por CADA entrada, a pé
     * e sem pular, com a física real do herói, sobe-se pelos degraus de pedra (cada um 0,125 u) e termina-se de pé sobre a
     * laje, na altura dela.
     */
    @Test
    void oPisoDosInterioresEALajeDesenhadaESeEntraAPePelosDegraus() throws Exception {
        JsonObject cat = catalog();
        final float slab = 0.375f;
        for (String name : new String[]{"workshop_forge", "ruin_house"})
            for (float yaw : YAWS) {
                JsonObject rec = cat.getAsJsonObject(name);
                Collision c = build(name, yaw, 1f, false, rec);
                Matrix4f m = new Matrix4f().translate(OX, 0, OZ).rotateY((float) Math.toRadians(yaw));
                JsonArray r = rec.getAsJsonObject("pontos").getAsJsonArray("area_segura_u").get(0).getAsJsonArray();
                String at = name + " a " + yaw + "°";
                int exact = 0, n = 0;
                for (float lx = r.get(0).getAsFloat(); lx < r.get(2).getAsFloat(); lx += 0.25f)
                    for (float lz = r.get(1).getAsFloat(); lz < r.get(3).getAsFloat(); lz += 0.25f) {
                        Vector3f w = m.transformPosition(new Vector3f(lx + 0.125f, 0, lz + 0.125f));
                        float y = c.floor(w.x, w.z, slab + 0.05f, Player.RADIUS);
                        if (!Reachability.free(c, w.x, w.z, y)) continue;
                        n++;
                        if (Math.abs(y - slab) < 1e-4f) exact++;
                    }
                assertTrue(n > 0 && exact >= n * 3 / 4, at + ": o chão de dentro é o topo da laje, exato (" + exact + " de " + n + ")");
                // a pé, por cada entrada, até o meio da área segura
                Vector3f mid = m.transformPosition(new Vector3f((r.get(0).getAsFloat() + r.get(2).getAsFloat()) / 2, 0,
                        (r.get(1).getAsFloat() + r.get(3).getAsFloat()) / 2));
                for (float[] e : points(rec, "entradas", null, yaw, 1f)) {
                    float dx = e[0] - mid.x, dz = e[2] - mid.z, d = (float) Math.hypot(dx, dz);
                    Player p = new Player();
                    p.place(e[0] + dx / d * 1.2f, e[2] + dz / d * 1.2f, (float) Math.atan2(-dx, -dz), c);
                    float[] target = {e[0], e[2]};
                    boolean inside = false;
                    for (int i = 0; i < 600; i++) {
                        if (!inside && Math.hypot(target[0] - p.x, target[1] - p.z) < 0.3f) { target = new float[]{mid.x, mid.z}; inside = true; }
                        float ex = target[0] - p.x, ez = target[1] - p.z, el = (float) Math.hypot(ex, ez);
                        boolean go = el > 0.15f;
                        p.update(1f / 60f, go ? ex / el : 0f, go ? ez / el : 0f, false, c);
                        assertEquals(0, p.jumps, at + ": entrou sem pular");
                    }
                    // dentro da área segura do catálogo (o caminho em linha reta até o meio pode parar atrás do tronco da árvore
                    // jovem da ruína: o que se confere é ter entrado a pé e estar de pé lá dentro)
                    Vector3f lp = new Matrix4f(m).invert().transformPosition(new Vector3f(p.x, 0, p.z));
                    boolean inArea = lp.x >= r.get(0).getAsFloat() && lp.x <= r.get(2).getAsFloat() && lp.z >= r.get(1).getAsFloat() && lp.z <= r.get(3).getAsFloat();
                    assertTrue(inside && inArea, at + ": entrou a pé pela " + java.util.Arrays.toString(e) + " (parou em " + lp.x + ", " + lp.z + " do modelo)");
                    assertEquals(slab, p.y, 1e-3f, at + ": de pé na altura da laje, sem afundar");
                }
            }
    }

    /** Nenhum asset com formas compostas sai liberado sem passar aqui dentro (a lista ENTERABLE é a dos conferidos). */
    @Test
    void todoCompostoLiberadoFoiPercorridoAqui() throws Exception {
        JsonObject cat = catalog();
        int n = 0;
        for (String id : cat.keySet()) {
            JsonObject rec = cat.getAsJsonObject(id);
            if (!rec.getAsJsonObject("colisao").has("formas")) continue;
            boolean free = rec.getAsJsonObject("aprovacao").get("integracao").getAsString().equals("liberado");
            assertEquals(free, java.util.Arrays.asList(ENTERABLE).contains(id), id + ": liberado ⇔ percorrido por este teste");
            n++;
        }
        assertTrue(n >= 5, "assets compostos no catálogo: " + n);
    }

    @Test
    void escalaETranslacaoAcompanhamOProp() throws Exception {
        JsonObject rec = catalog().getAsJsonObject("workshop_forge");
        Collision c1 = build("workshop_forge", 37f, 1f, false, rec), c2 = build("workshop_forge", 37f, 1.5f, false, rec);
        assertEquals(c1.shapes.size(), c2.shapes.size());
        for (int i = 0; i < c1.shapes.size(); i++) {
            Collision.Shape a = c1.shapes.get(i), b = c2.shapes.get(i);
            assertEquals(a.name, b.name);
            assertEquals((a.cx - OX) * 1.5f, b.cx - OX, 1e-3f, "o centro escala a partir da origem do prop");
            assertEquals((a.cz - OZ) * 1.5f, b.cz - OZ, 1e-3f);
            assertEquals((a.y1 - a.y0) * 1.5f, b.y1 - b.y0, 1e-3f, "a altura escala");
            if (a.circle) assertEquals(a.r * 1.5f, b.r, 1e-3f); else { assertEquals(a.hx * 1.5f, b.hx, 1e-3f); assertEquals(a.hz * 1.5f, b.hz, 1e-3f); }
        }
        // a forma acompanha o rumo: girar 90° leva a coluna da forja (atrás) para o lado
        Collision r0 = build("workshop_forge", 0f, 1f, false, rec), r90 = build("workshop_forge", 90f, 1f, false, rec);
        Collision.Shape f0 = r0.named("workshop_forge#3").getFirst(), f90 = r90.named("workshop_forge#3").getFirst();
        assertEquals(f0.cz - OZ, f90.cx - OX, 1e-3f, "rotateY de 90°: o z local vira +x no mundo");
        assertEquals(-(f0.cx - OX), f90.cz - OZ, 1e-3f, "e o x local vira −z");
    }

    /**
     * P74 (rodada 12.1) — a torre arruinada com a escada recuada e o arco de 8 voxels. Na rodada 12 sobravam ~0,5 u sob o
     * arco (o corpo tem 0,52 u) e ela ficou de fora; a causa eram os degraus 3 e 4 da escada interna logo atrás da boca da
     * porta. Agora, em todos os rumos: entra-se e chega-se ao centro COM FOLGA (o mesmo percurso com um corpo 0,12 u mais
     * largo de cada lado também chega), sai-se (a busca é de ida e volta), e quem está no chão de dentro sobe na muralha
     * baixa do mirante com um salto, com a física real do herói, e fica de pé em cima dela.
     */
    @Test
    void p74ATorreArruinadaEntraComFolgaESeSobeNaMuralhaDoMirante() throws Exception {
        JsonObject rec = catalog().getAsJsonObject("ruin_tower");
        assertEquals("liberado", rec.getAsJsonObject("aprovacao").get("integracao").getAsString());
        CombatDef def = CombatDef.load(ProceduralWorldTest.A, "combate_e");
        for (float yaw : YAWS) {
            Collision c = build("ruin_tower", yaw, 1f, false, rec);
            float[] o = outside(rec, yaw);
            String at = "torre a " + yaw + "°";
            float[] centro = points(rec, "interacao", "examinar", yaw, 1f).getFirst();
            assertTrue(flood(c, o[0], o[1]).near(centro[0], centro[2], 0.35f), at + ": entra e chega ao centro");
            assertTrue(flood(c, o[0], o[1], Player.RADIUS + 0.12f).near(centro[0], centro[2], 0.45f), at + ": com folga (corpo 0,12 u mais largo de cada lado)");
            // o mirante: em cima da muralha baixa; parte do chão de dentro, 0,9 u antes dele, e pula andando para ele
            float[] m = points(rec, "interacao", "olhar", yaw, 1f).getFirst();
            float dx = m[0] - centro[0], dz = m[2] - centro[2], d = (float) Math.hypot(dx, dz);
            Player p = new Player();
            p.applyFeel(def.feels.get("C"));
            p.applyJump(def);
            p.place(m[0] - dx / d * 0.9f, m[2] - dz / d * 0.9f, (float) Math.atan2(dx, dz), c);
            float floor0 = p.y;
            for (int i = 0; i < 180; i++) {
                if (i == 6) p.requestJump();
                float ex = m[0] - p.x, ez = m[2] - p.z, el = (float) Math.hypot(ex, ez);
                boolean go = el > 0.12f;
                p.update(1f / 60f, go ? ex / el : 0f, go ? ez / el : 0f, false, c);
            }
            assertTrue(p.grounded, at + ": pousou");
            assertEquals(m[1], p.y, 0.13f, at + ": de pé em cima da muralha (pés a " + p.y + ", muralha a " + m[1] + ", chão a " + floor0 + ")");
            assertTrue(Math.hypot(m[0] - p.x, m[2] - p.z) < 0.5f, at + ": no ponto do mirante");
        }
    }

    /**
     * P73 (rodada 12) — na oficina, com a ferreira no lugar, o herói: entra pelo galpão, chega à forja, à bigorna e à
     * bancada, consegue falar com a ferreira e sai — em todos os rumos testados.
     */
    @Test
    void p73NaOficinaComAFerreiraNoLugar() throws Exception {
        JsonObject rec = catalog().getAsJsonObject("workshop_forge");
        for (float yaw : YAWS) {
            Collision c = build("workshop_forge", yaw, 1f, true, rec);
            float[] o = outside(rec, yaw);
            Flood f = flood(c, o[0], o[1]);
            String at = "oficina a " + yaw + "°";
            Map<String, float[]> pts = new java.util.LinkedHashMap<>();
            for (String kind : new String[]{"entradas", "interacao", "spawns"}) {
                List<float[]> l = points(rec, kind, null, yaw, 1f);
                JsonArray raw = rec.getAsJsonObject("pontos").getAsJsonArray(kind);
                for (int i = 0; i < l.size(); i++) pts.put(kind + ":" + raw.get(i).getAsJsonObject().get("id").getAsString(), l.get(i));
            }
            float[] smith = pts.get("spawns:ferreira");
            assertTrue(f.near(pts.get("entradas:galpao")[0], pts.get("entradas:galpao")[2], 0.3f), at + ": 1. entrar pelo galpão");
            for (String k : new String[]{"interacao:forja", "interacao:bigorna", "interacao:bancada"}) {
                float[] p = pts.get(k);
                assertTrue(f.near(p[0], p[2], 0.35f), at + ": chegar a " + k);
                assertTrue(Math.hypot(p[0] - smith[0], p[2] - smith[2]) >= NPC + Player.RADIUS, at + ": " + k + " colado na ferreira");
            }
            boolean talk = false;
            for (int k = 0; k < 16 && !talk; k++) {
                double a = k * Math.PI / 8;
                talk = f.near(smith[0] + (float) Math.sin(a) * 0.95f, smith[2] + (float) Math.cos(a) * 0.95f, 0.2f);
            }
            assertTrue(talk, at + ": 6. falar com a ferreira");
            assertTrue(f.fits(o[0], o[1]), at + ": 7. sair (a busca é de ida e volta: chegar implica voltar)");
        }
    }
}
