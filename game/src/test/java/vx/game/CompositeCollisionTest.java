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
 *   <li>a câmera não atravessa as paredes, e o telhado (forma só de câmera) segura a câmera de quem está dentro;</li>
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
    static final String[] ENTERABLE = {"workshop_forge", "ruin_house", "market_stall"};
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

    static Flood flood(Collision c, float sx, float sz) {
        float x0 = OX - 8f, z0 = OZ - 8f;
        int n = (int) (16f / CELL);
        boolean[] free = new boolean[n * n], seen = new boolean[n * n];
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                float x = x0 + (ix + 0.5f) * CELL, z = z0 + (iz + 0.5f) * CELL;
                free[ix + iz * n] = Reachability.free(c, x, z, c.floor(x, z, 99f, Player.RADIUS));
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
                if (jx < 0 || jz < 0 || jx >= n || jz >= n || seen[j] || !free[j]) continue;
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
                assertNotNull(m);
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
     * A torre arruinada (rodada 12, P74): sob o arco da porta, o vão livre entre as colunas que descem abaixo da cabeça do
     * herói é de ~0,5 u — menos que o corpo dele (2 × {@link Player#RADIUS} = 0,52 u) —, então o interior é inalcançável
     * com a colisão real, em qualquer rumo. Por isso a torre NÃO é liberada (o catálogo continua dizendo que depende) e o
     * gerador v2 não a planta. Corrigir é decisão de arte (alargar o arco), não de colisão.
     */
    @Test
    void aTorreArruinadaTemAPortaEstreitaDemaisENaoELiberada() throws Exception {
        JsonObject rec = catalog().getAsJsonObject("ruin_tower");
        for (float yaw : YAWS) {
            Collision c = build("ruin_tower", yaw, 1f, false, rec);
            float[] o = outside(rec, yaw);
            Flood f = flood(c, o[0], o[1]);
            float[] centro = points(rec, "interacao", null, yaw, 1f).getFirst();
            assertFalse(f.near(centro[0], centro[2], 0.35f), "a 37°/90°/…: se passou a entrar, reavalie a liberação da torre");
        }
        assertNotEquals("liberado", rec.getAsJsonObject("aprovacao").get("integracao").getAsString());
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
