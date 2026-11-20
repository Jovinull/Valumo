package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Laboratório de conteúdo do mundo — propriedades REAIS dos assets novos e do catálogo {@code valumo.conteudo/1}
 * ({@code assets/environment/e/conteudo_catalogo.json}, gerado por {@code tools/conteudo_e.py}).
 *
 * <p>Tudo é conferido contra a geometria carregada pelo MESMO leitor do jogo ({@link VoxModel}) e malhada pelo MESMO
 * mesher ({@link VoxelMesher}, fusão exata): medidas declaradas = medidas dos voxels; entradas fora das paredes e da
 * forma de colisão; pontos de interação de pé num lugar livre; tronco onde a colisão do jogo o põe; variações que não
 * mudam a pegada; orçamento de triângulos por categoria. Nenhum asset aprovado é tocado (os ids do catálogo não
 * existem no props.json aprovado).
 */
class ConteudoMundoTest {
    static final Path A = Path.of("assets");
    static final Path ENV = A.resolve("environment/e");
    static final float EPS = 1e-3f;
    /** Altura do corpo do herói em voxels de 1/8 u (1,25 u): a coluna de pé precisa estar livre nessa faixa. */
    static final int BODY = 10;
    /**
     * Orçamento de triângulos (malha fundida) RELATIVO aos aprovados, medidos com o mesmo mesher
     * (tools/conteudo/MeshCost.java): casa 17 776, posto 9 492, carvalho O3 6 946, salgueiro 11 904, árvore-marco 26 538.
     * Construção ≤ 1,15 × casa; POI ≤ casa; árvore comum ≤ 1,2 × carvalho; árvore rara/incomum ≤ salgueiro;
     * estrutura complementar ≤ posto de trilha (9 492); prop comum ≤ 3 000 (≈ 2 × fogueira 1 440, que repete muito);
     * prop incomum/raro ≤ 4 500 (≈ 3 × fogueira).
     */
    static final Map<String, Integer> TRI_BUDGET = Map.of("construcao", 20500, "poi", 17800, "vegetacao/comum", 8400,
            "vegetacao", 11900, "estrutura", 9500, "prop/comum", 3000, "prop", 4500);

    static JsonObject json(Path p) throws IOException { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }

    static JsonObject catalog() throws IOException { return json(ENV.resolve("conteudo_catalogo.json")); }

    static List<JsonObject> assets() throws IOException {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : catalog().getAsJsonArray("assets")) out.add(e.getAsJsonObject());
        return out;
    }

    /** Asset carregado no espaço do modelo: voxels sólidos (chave → cor com material) e a origem. */
    record Model(VoxModel vox, int ox, int oy, int oz, float vs) {
        int at(int x, int y, int z) {
            int gx = x - ox, gy = y - oy, gz = z - oz;
            if (gx < 0 || gy < 0 || gz < 0 || gx >= vox.sx || gy >= vox.sy || gz >= vox.sz) return 0;
            return vox.get(gx, gy, gz);
        }
        /** Voxel sólido (não folhagem) na coluna (x, z) entre y0 e y1. */
        boolean solidIn(int x, int z, int y0, int y1) {
            for (int y = y0; y <= y1; y++) {
                int c = at(x, y, z);
                if (c != 0 && (c >>> 24) - 1 != VoxModel.MAT_FOLIAGE) return true;
            }
            return false;
        }
        int vx(float u) { return (int) Math.floor(u / vs); }
    }

    static Model load(JsonObject a) throws IOException {
        String id = a.get("id").getAsString();
        boolean rejected = a.get("estado").getAsString().equals("rejeitado");
        Path dir = rejected ? A.resolve("studies/conteudo/" + id) : ENV;
        JsonObject entry = rejected
                ? json(dir.resolve("rig.json")).getAsJsonArray("parts").get(0).getAsJsonObject()
                : json(ENV.resolve("props.json")).getAsJsonObject(id);
        JsonArray o = entry.getAsJsonArray("origin");
        VoxModel m = VoxModel.load(dir.resolve(rejected ? "body.vox" : entry.get("file").getAsString()));
        return new Model(m, o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt(), a.getAsJsonObject("medidas").get("voxel").getAsFloat());
    }

    static float[] arr(JsonObject o, String k) {
        JsonArray a = o.getAsJsonArray(k);
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    static void finite(String where, JsonElement e) {
        if (e.isJsonObject()) for (var en : e.getAsJsonObject().entrySet()) finite(where + "." + en.getKey(), en.getValue());
        else if (e.isJsonArray()) { int i = 0; for (JsonElement x : e.getAsJsonArray()) finite(where + "[" + i++ + "]", x); }
        else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
            assertTrue(Double.isFinite(e.getAsDouble()), where + " não é finito");
    }

    @Test
    void catalogIsWellFormedAndDoesNotTouchApprovedProps() throws IOException {
        JsonObject cat = catalog();
        assertEquals("valumo.conteudo", cat.get("contrato").getAsString());
        assertEquals(1, cat.get("versao").getAsInt());
        JsonObject props = json(ENV.resolve("props.json"));
        Set<String> ids = new HashSet<>();
        List<JsonObject> all = assets();
        assertTrue(all.size() >= 12, "o lote 1 tem pelo menos 12 candidatos; achei " + all.size());
        for (JsonObject a : all) {
            String id = a.get("id").getAsString();
            assertTrue(ids.add(id), "id repetido: " + id);
            assertTrue(id.matches("[a-z][a-z0-9_]*"), "id fora do padrão do props.json: " + id);
            finite(id, a);
            for (String k : List.of("nome", "categoria", "funcao", "frequencia", "estado", "fonte"))
                assertTrue(a.has(k) && !a.get(k).isJsonNull() && !a.get(k).getAsString().isBlank(), id + ": falta " + k);
            assertTrue(a.getAsJsonArray("tags").size() >= 2, id + ": tags");
            assertTrue(a.has("colisao") && a.getAsJsonObject("colisao").has("tipo"), id + ": colisão");
            String st = a.get("estado").getAsString();
            assertTrue(List.of("aprovado", "candidato", "rejeitado").contains(st), id + ": estado " + st);
            JsonObject ap = a.getAsJsonObject("aprovacao");
            String art = ap.get("artistica").getAsString(), integ = ap.get("integracao").getAsString();
            assertTrue(List.of("aprovado", "aprovado_conceito", "candidato", "rejeitado").contains(art), id + ": aprovação " + art);
            assertTrue(List.of("liberado", "depende_colisao_composta", "aguardando_refinamento", "aguardando_avaliacao",
                    "restrito_a_conjuntos", "nao_distribuivel").contains(integ),
                    id + ": integração " + integ);
            assertEquals(integ.equals("liberado"), ap.get("distribuicao_procedural").getAsBoolean(), id + ": distribuição");
            if (!integ.equals("liberado"))                // dependência nunca escondida
                assertTrue(ap.getAsJsonArray("dependencias").size() > 0 || integ.equals("nao_distribuivel")
                        || integ.equals("aguardando_avaliacao") || integ.equals("restrito_a_conjuntos"),
                        id + ": pendência sem dependência");
            assertEquals(art.equals("candidato"), integ.equals("aguardando_avaliacao"), id + ": candidato só aguarda avaliação");
            // aprovado, conceito ou rejeitado só com a DECISÃO do proprietário registrada (lotes 1 e 2: avaliação de
            // 2026-11-19; NPCs e ruínas do estudo: fechamento de 2026-11-20); candidato ainda aguarda
            String dec = ap.get("decisao").getAsString();
            if (art.equals("candidato")) assertTrue(dec.startsWith("aguardando"), id + ": candidato com decisão registrada");
            else assertTrue(dec.startsWith("proprietário"), id + ": " + art + " sem decisão registrada do proprietário: " + dec);
            if (a.getAsJsonObject("colisao").has("formas"))  // forma composta ainda não existe no jogo: não pode estar liberado
                assertNotEquals("liberado", integ, id + ": precisa de formas compostas e está liberado");
            assertEquals(art.equals("rejeitado"), st.equals("rejeitado"), id + ": estado × aprovação");
            assertEquals(art.equals("candidato"), st.equals("candidato"), id + ": estado × aprovação (candidato)");
            if (st.equals("rejeitado")) {
                assertFalse(props.has(id), id + " rejeitado não pode ir para o props.json");
                assertTrue(a.has("motivo_rejeicao") && !a.get("motivo_rejeicao").isJsonNull(), id + ": rejeitado sem motivo");
            } else {
                assertTrue(props.has(id), id + " ausente do props.json");
                assertTrue(props.getAsJsonObject(id).has("conteudo"), id + ": entrada sem a marca 'conteudo'");
            }
        }
        Set<String> published = new HashSet<>(ids);     // variantes publicadas como prop próprio (ex.: placa por ofício)
        for (JsonObject a : all)
            for (JsonElement e : a.getAsJsonObject("variacoes").getAsJsonArray("extremos")) {
                JsonObject v = e.getAsJsonObject();
                if (v.has("prop") && !v.get("prop").isJsonNull()) {
                    assertTrue(props.has(v.get("prop").getAsString()), v.get("prop").getAsString() + " publicada e ausente do props.json");
                    assertEquals(a.get("id").getAsString(), props.getAsJsonObject(v.get("prop").getAsString()).get("variante_de").getAsString());
                    published.add(v.get("prop").getAsString());
                }
            }
        for (String name : props.keySet())   // o que é do laboratório está no catálogo; o resto é aprovado e intocado
            if (props.getAsJsonObject(name).has("conteudo")) assertTrue(published.contains(name), name + " marcado e fora do catálogo");
        for (String approved : List.of("house", "trail_post", "well", "tree_oak", "tree_willow", "trail_beacon_on"))
            assertFalse(props.getAsJsonObject(approved).has("conteudo"), approved + " é aprovado e não pode virar do laboratório");
    }

    @Test
    void declaredMeasuresMatchTheLoadedVoxels() throws IOException {
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            JsonObject med = a.getAsJsonObject("medidas");
            float vs = m.vs;
            float expected = switch (a.get("categoria").getAsString()) {
                case "construcao", "poi", "vegetacao" -> a.has("subcategoria") && "planta".equals(a.get("subcategoria").getAsString()) ? -1 : 0.125f;
                default -> -1;
            };
            if (expected > 0) assertEquals(expected, vs, EPS, id + ": voxel da família");
            float[] lo = arr(med.getAsJsonObject("caixa_u"), "min"), hi = arr(med.getAsJsonObject("caixa_u"), "max");
            assertEquals(m.ox * vs, lo[0], EPS, id + " caixa x");
            assertEquals(m.oy * vs, lo[1], EPS, id + " caixa y");
            assertEquals(m.oz * vs, lo[2], EPS, id + " caixa z");
            assertEquals((m.ox + m.vox.sx) * vs, hi[0], EPS, id + " caixa x1");
            assertEquals((m.oy + m.vox.sy) * vs, hi[1], EPS, id + " caixa y1");
            assertEquals((m.oz + m.vox.sz) * vs, hi[2], EPS, id + " caixa z1");
            // pegada = colunas com voxel na faixa do chão (y 0..3), medida aqui de novo
            int n = 0, fx0 = Integer.MAX_VALUE, fz0 = Integer.MAX_VALUE, fx1 = Integer.MIN_VALUE, fz1 = Integer.MIN_VALUE, voxels = 0;
            for (int x = m.ox; x < m.ox + m.vox.sx; x++)
                for (int z = m.oz; z < m.oz + m.vox.sz; z++) {
                    boolean any = false;
                    for (int y = m.oy; y < m.oy + m.vox.sy; y++) {
                        if (m.at(x, y, z) == 0) continue;
                        voxels++;
                        if (y >= 0 && y <= 3) any = true;
                    }
                    if (any) { n++; fx0 = Math.min(fx0, x); fz0 = Math.min(fz0, z); fx1 = Math.max(fx1, x); fz1 = Math.max(fz1, z); }
                }
            JsonObject foot = med.getAsJsonObject("pegada_u");
            assertEquals(n * vs * vs, foot.get("area_u2").getAsFloat(), 0.01f, id + ": área da pegada");
            assertEquals(fx0 * vs, arr(foot, "min")[0], EPS, id + ": pegada x0");
            assertEquals((fz1 + 1) * vs, arr(foot, "max")[1], EPS, id + ": pegada z1");
            assertEquals(voxels, med.get("voxels").getAsInt(), id + ": número de voxels");
            assertTrue(voxels > 0, id + ": modelo vazio");
            // a origem do prop é o centro da pegada: o centro da pegada fica perto de x = z = 0 (até 0,6 u)
            assertTrue(Math.abs((fx0 + fx1 + 1) * 0.5f * vs) < 0.9f && Math.abs((fz0 + fz1 + 1) * 0.5f * vs) < 0.9f,
                    id + ": pegada longe da origem (" + (fx0 + fx1 + 1) * 0.5f * vs + ", " + (fz0 + fz1 + 1) * 0.5f * vs + ")");
        }
    }

    @Test
    void paletteMaterialsAreValidAndFoliageDeclaresWind() throws IOException {
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            int[] per = new int[5];
            for (int x = 0; x < m.vox.sx; x++)
                for (int y = 0; y < m.vox.sy; y++)
                    for (int z = 0; z < m.vox.sz; z++) {
                        int c = m.vox.get(x, y, z);
                        if (c == 0) continue;
                        int mat = (c >>> 24) - 1;
                        assertTrue(mat >= 0 && mat <= 4, id + ": material inválido " + mat);
                        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                        assertFalse(r + g + b < 30 || r + g + b > 750, id + ": preto/branco puro em (" + x + "," + y + "," + z + ")");
                        per[mat]++;
                    }
            if (per[VoxModel.MAT_FOLIAGE] > 0) assertTrue(a.get("affineWind").getAsBoolean(), id + ": folhagem sem affineWind");
            // luz é ponto focal: ≤ 6% das faces; FONTE DE LUZ (tag) até a fração da fogueira aprovada (25,1%, medida;
            // o poste com lanterna aprovado tem 7,9% e o marco aceso 7,4%)
            boolean source = a.getAsJsonArray("tags").toString().contains("\"fonte_de_luz\"");
            float em = a.getAsJsonObject("custo").get("emissivo_pct").getAsFloat();
            assertTrue(em <= (source ? 25.1f : 6f), id + ": emissivo " + em + "% acima do limite (" + (source ? "fonte de luz" : "ponto focal") + ")");
        }
    }

    /**
     * O vento afim da E (scene.vert) desloca SÓ o material folhagem, proporcional à altura acima da base do prop
     * (0,022 × vento 0,8 × altura). Folhagem encostada em voxel rígido (musgo no telhado, hera na parede, folha de
     * floreira) desliza sobre ele e abre fresta onde o mesher descartou a face do vizinho — reproduzido no laboratório
     * (inclusive na casa aprovada). Em construções e POIs, acima de 1,7 u (deslocamento ≥ 1/4 de voxel de 1/8) nenhuma
     * folhagem pode tocar sólido: vegetação presa é fosca; folhagem é o que balança solto.
     */
    @Test
    void attachedFoliageDoesNotSlideOnRigidStructures() throws IOException {
        int checked = 0;
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (JsonObject a : assets()) {
            String cat = a.get("categoria").getAsString();
            if (!cat.equals("construcao") && !cat.equals("poi") && !cat.equals("estrutura")) continue;
            String id = a.get("id").getAsString();
            Model m = load(a);
            int bad = 0, minY = (int) Math.ceil(1.7f / m.vs);
            for (int x = 0; x < m.vox.sx; x++)
                for (int y = 0; y < m.vox.sy; y++)
                    for (int z = 0; z < m.vox.sz; z++) {
                        int c = m.vox.get(x, y, z);
                        if (c == 0 || (c >>> 24) - 1 != VoxModel.MAT_FOLIAGE || y + m.oy < minY) continue;
                        for (int[] d : nb) {
                            int n = m.at(x + m.ox + d[0], y + m.oy + d[1], z + m.oz + d[2]);
                            if (n != 0 && (n >>> 24) - 1 != VoxModel.MAT_FOLIAGE) { bad++; break; }
                        }
                    }
            assertEquals(0, bad, id + ": " + bad + " voxel(s) de folhagem presos a sólido acima de 1,7 u (deslizam com o vento)");
            checked++;
        }
        assertTrue(checked >= 8, "construções/POIs conferidos: " + checked);
    }

    @Test
    void noLooseVoxelsOrTinyFragments() throws IOException {
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            Set<Long> cells = new HashSet<>();
            for (int x = 0; x < m.vox.sx; x++)
                for (int y = 0; y < m.vox.sy; y++)
                    for (int z = 0; z < m.vox.sz; z++) if (m.vox.get(x, y, z) != 0) cells.add(QualityTest.key(x, y, z));
            Set<Long> seen = new HashSet<>();
            int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            List<Integer> small = new ArrayList<>();
            for (long c : cells) {
                if (!seen.add(c)) continue;
                ArrayDeque<Long> q = new ArrayDeque<>(List.of(c));
                int size = 0;
                while (!q.isEmpty()) {
                    long k = q.pop();
                    size++;
                    int x = (int) ((k >>> 40) & 0xFFFFF) - 512, y = QualityTest.y(k), z = QualityTest.z(k);
                    for (int[] d : nb) {
                        long n2 = QualityTest.key(x + d[0], y + d[1], z + d[2]);
                        if (cells.contains(n2) && seen.add(n2)) q.push(n2);
                    }
                }
                if (size < 4) small.add(size);
            }
            assertTrue(small.isEmpty(), id + ": " + small.size() + " fragmento(s) com menos de 4 voxels (peça solta)");
        }
    }

    /** Forma de colisão do jogo hoje (Collision.addProp): caixa/círculo centrados no CENTRO DA CAIXA da malha. */
    static boolean insideProfile(JsonObject a, float x, float z) {
        JsonObject c = a.getAsJsonObject("colisao");
        float[] lo = arr(a.getAsJsonObject("medidas").getAsJsonObject("caixa_u"), "min"), hi = arr(a.getAsJsonObject("medidas").getAsJsonObject("caixa_u"), "max");
        float cx = (lo[0] + hi[0]) / 2, cz = (lo[2] + hi[2]) / 2, w = hi[0] - lo[0], d = hi[2] - lo[2];
        return switch (c.get("tipo").getAsString()) {
            case "caixa" -> {
                float k = c.has("escala") ? c.get("escala").getAsFloat() : 1f;
                yield Math.abs(x - cx) < w / 2 * k && Math.abs(z - cz) < d / 2 * k;
            }
            case "circulo" -> Math.hypot(x - cx, z - cz) < (c.has("raio") ? c.get("raio").getAsFloat() : Math.min(w, d) / 2);
            case "tronco" -> Math.hypot(x - cx, z - cz) < c.get("raio").getAsFloat();
            default -> false;
        };
    }

    /** Formas compostas (medidas: retângulos; autoradas: círculos), em u no espaço do modelo, com folga do raio do herói. */
    static boolean insideComposite(JsonObject a, float x, float z) {
        float hero = 0.3f;
        JsonObject med = a.getAsJsonObject("medidas"), pts = a.getAsJsonObject("pontos");
        if (med.has("formas_medidas_u")) for (JsonElement e : med.getAsJsonArray("formas_medidas_u")) {
            JsonArray b = e.getAsJsonArray();
            if (x > b.get(0).getAsFloat() - hero && x < b.get(2).getAsFloat() + hero && z > b.get(1).getAsFloat() - hero && z < b.get(3).getAsFloat() + hero) return true;
        }
        if (pts.has("formas_u")) for (JsonElement e : pts.getAsJsonArray("formas_u")) {
            JsonObject f = e.getAsJsonObject();
            float[] c = arr(f, "centro");
            if (Math.hypot(x - c[0], z - c[1]) < f.get("raio").getAsFloat() + hero) return true;
        }
        return false;
    }

    @Test
    void entrancesAreOutsideWallsAndOutsideTheCollisionShape() throws IOException {
        int checked = 0;
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            for (JsonElement e : a.getAsJsonObject("pontos").getAsJsonArray("entradas")) {
                JsonObject p = e.getAsJsonObject();
                float[] pos = arr(p, "pos_u");
                int x = m.vx(pos[0]), z = m.vx(pos[2]);
                assertFalse(m.solidIn(x, z, 1, BODY), id + "/" + p.get("id").getAsString() + ": entrada dentro de parede/sólido");
                String who = id + "/" + p.get("id").getAsString();
                if (insideProfile(a, pos[0], pos[2])) {       // só vale com formas compostas, e fora de todas elas
                    assertTrue(a.getAsJsonObject("colisao").has("formas"), who + ": entrada dentro da forma de colisão simples");
                    assertFalse(insideComposite(a, pos[0], pos[2]), who + ": entrada dentro das formas compostas");
                }
                // a entrada olha para o asset: andando no rumo invertido, acha sólido antes de passar do centro da
                // pegada (porta de casa a ~1 u; vão de um anel aberto até o altar no meio)
                double r = Math.toRadians(p.get("rumo").getAsFloat());
                float dx = (float) -Math.sin(r), dz = (float) -Math.cos(r);
                JsonObject foot = a.getAsJsonObject("medidas").getAsJsonObject("pegada_u");
                float fcx = (arr(foot, "min")[0] + arr(foot, "max")[0]) / 2, fcz = (arr(foot, "min")[1] + arr(foot, "max")[1]) / 2;
                float reach = (float) Math.hypot(pos[0] - fcx, pos[2] - fcz) + 0.5f;
                boolean hit = false;
                for (float t = 0; t <= reach && !hit; t += 0.0625f) hit = m.solidIn(m.vx(pos[0] + dx * t), m.vx(pos[2] + dz * t), 0, BODY + 6);
                assertTrue(hit, id + "/" + p.get("id").getAsString() + ": entrada não aponta para o asset");
                checked++;
            }
        }
        assertTrue(checked >= 10, "conferiu poucas entradas: " + checked);
    }

    @Test
    void interactionAndSpawnPointsStandOnFreeGroundAndCompositeShapesCoverThem() throws IOException {
        int checked = 0;
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            JsonObject pts = a.getAsJsonObject("pontos");
            boolean composite = a.getAsJsonObject("colisao").has("formas");
            for (String kind : List.of("interacao", "spawns", "vagas")) {
                if (!pts.has(kind)) continue;                       // vagas de NPC: só quem tem morador
                for (JsonElement e : pts.getAsJsonArray(kind)) {
                    JsonObject p = e.getAsJsonObject();
                    float[] pos = arr(p, "pos_u");
                    int x = m.vx(pos[0]), y = m.vx(pos[1]), z = m.vx(pos[2]);
                    String who = id + "/" + kind + "/" + p.get("id").getAsString();
                    // de pé: há um lugar livre do corpo a até 1 u do ponto, com chão (relevo y < 0 ou piso do asset)
                    boolean stand = false;
                    for (int dx = -8; dx <= 8 && !stand; dx++)
                        for (int dz = -8; dz <= 8 && !stand; dz++) {
                            if (dx * dx + dz * dz > 64) continue;
                            int fy = y;
                            while (fy > 0 && m.at(x + dx, fy - 1, z + dz) == 0) fy--;
                            stand = !m.solidIn(x + dx, z + dz, fy, fy + BODY - 1);
                        }
                    assertTrue(stand, who + ": sem lugar livre de pé a até 1 u");
                    if (insideProfile(a, pos[0], pos[2]))
                        assertTrue(composite, who + ": ponto dentro da forma de colisão simples sem 'formas' compostas declaradas");
                    checked++;
                }
            }
        }
        assertTrue(checked >= 15, "conferiu poucos pontos: " + checked);
    }

    @Test
    void npcSpotsHaveRoomForTheBodyAndOpenSky() throws IOException {
        // vaga de NPC morador (ex.: feirante ao lado do balcão): nenhum sólido do asset no corpo do NPC (raio 0,3 u, o
        // de colisao_e.json, da altura do piso pisável à cabeça) e nada por cima da cabeça (a câmera de conversa não
        // entra em toldo nem telhado)
        int checked = 0;
        for (JsonObject a : assets()) {
            JsonObject pts = a.getAsJsonObject("pontos");
            if (!pts.has("vagas")) continue;
            Model m = load(a);
            int lo = (int) Math.ceil(0.375 / m.vs - 1e-9), head = (int) Math.ceil(1.3 / m.vs - 1e-9), r = (int) Math.ceil(0.3 / m.vs);
            for (JsonElement e : pts.getAsJsonArray("vagas")) {
                JsonObject v = e.getAsJsonObject();
                String who = a.get("id").getAsString() + "/vagas/" + v.get("id").getAsString();
                assertFalse(v.get("papel").getAsString().isBlank(), who + ": sem papel");
                assertTrue(Math.abs(v.get("rumo").getAsDouble()) <= 360, who + ": rumo");
                float[] pos = arr(v, "pos_u");
                int cx = m.vx(pos[0]), cz = m.vx(pos[2]), body = 0, roof = 0;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.hypot(dx, dz) > r) continue;
                        if (m.solidIn(cx + dx, cz + dz, lo, head)) body++;
                        if (m.solidIn(cx + dx, cz + dz, head + 1, m.oy + m.vox.sy)) roof++;
                    }
                assertEquals(0, body, who + ": sólido do asset dentro do corpo do NPC");
                assertEquals(0, roof, who + ": teto do asset sobre a cabeça do NPC");
                checked++;
            }
        }
        assertTrue(checked >= 1, "nenhuma vaga de NPC conferida");
    }

    @Test
    void reservedFootprintIsTheExactUnionOfTheVariants() throws IOException {
        // o gerador reserva a união EXATA das pegadas válidas (base e todas as variações) e o máximo das dimensões;
        // nenhuma tolerância: a variação que muda a pegada aparece na reserva
        int n = 0;
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            JsonObject med = a.getAsJsonObject("medidas");
            List<JsonObject> all = new ArrayList<>(List.of(med.getAsJsonObject("pegada_u")));
            List<float[]> dims = new ArrayList<>(List.<float[]>of(arr(med, "dimensoes_u")));
            for (JsonElement e : a.getAsJsonObject("variacoes").getAsJsonArray("extremos")) {
                all.add(e.getAsJsonObject().getAsJsonObject("pegada_u"));
                dims.add(arr(e.getAsJsonObject(), "dimensoes_u"));
            }
            JsonObject res = med.getAsJsonObject("pegada_reservada_u");
            for (int i = 0; i < 2; i++) {
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (JsonObject p : all) { lo = Math.min(lo, arr(p, "min")[i]); hi = Math.max(hi, arr(p, "max")[i]); }
                assertEquals(lo, arr(res, "min")[i], 1e-6f, id + ": reserva (min) não é a união das pegadas");
                assertEquals(hi, arr(res, "max")[i], 1e-6f, id + ": reserva (max) não é a união das pegadas");
            }
            for (int i = 0; i < 3; i++) {
                float mx = 0;
                for (float[] d : dims) mx = Math.max(mx, d[i]);
                assertEquals(mx, arr(med, "dimensoes_reservadas_u")[i], 1e-6f, id + ": dimensões reservadas");
            }
            n++;
        }
        assertTrue(n >= 20, "reservas conferidas: " + n);
    }

    @Test
    void eachVariantCollisionCoversItsOwnVoxels() throws IOException {
        // colisão medida da BASE e de CADA variação: todo voxel sólido na faixa do corpo (y 3..12, a de collision_boxes)
        // está dentro de uma forma daquela variação; formas_iguais diz a verdade
        int checked = 0;
        for (JsonObject a : assets()) {
            JsonObject med = a.getAsJsonObject("medidas");
            if (!med.has("formas_medidas_u")) continue;
            String id = a.get("id").getAsString();
            checked += coverage(id, load(a), med.getAsJsonArray("formas_medidas_u"));
            for (JsonElement e : a.getAsJsonObject("variacoes").getAsJsonArray("extremos")) {
                JsonObject v = e.getAsJsonObject();
                String vid = v.get("id").getAsString();
                assertTrue(v.has("formas_medidas_u"), vid + ": variação sem a colisão dela");
                assertEquals(v.get("formas_iguais").getAsBoolean(), v.get("formas_medidas_u").equals(med.get("formas_medidas_u")),
                        vid + ": formas_iguais não confere");
                Path dir = A.resolve("studies/conteudo/" + vid);
                JsonObject part = json(dir.resolve("rig.json")).getAsJsonArray("parts").get(0).getAsJsonObject();
                JsonArray o = part.getAsJsonArray("origin");
                Model vm = new Model(VoxModel.load(dir.resolve(part.get("file").getAsString())), o.get(0).getAsInt(),
                        o.get(1).getAsInt(), o.get(2).getAsInt(), med.get("voxel").getAsFloat());
                checked += coverage(vid, vm, v.getAsJsonArray("formas_medidas_u"));
            }
        }
        assertTrue(checked > 1000, "voxels de corpo conferidos: " + checked);
    }

    static int coverage(String who, Model m, JsonArray boxes) {
        int n = 0;
        for (int x = 0; x < m.vox.sx; x++)
            for (int y = 0; y < m.vox.sy; y++)
                for (int z = 0; z < m.vox.sz; z++) {
                    int c = m.vox.get(x, y, z), wy = y + m.oy;
                    if (c == 0 || (c >>> 24) - 1 == VoxModel.MAT_FOLIAGE || wy < 3 || wy > 12) continue;
                    float ux = (x + m.ox + 0.5f) * m.vs, uz = (z + m.oz + 0.5f) * m.vs;
                    boolean in = false;
                    for (JsonElement b : boxes) {
                        JsonArray q = b.getAsJsonArray();
                        if (ux >= q.get(0).getAsFloat() && ux <= q.get(2).getAsFloat() && uz >= q.get(1).getAsFloat()
                                && uz <= q.get(3).getAsFloat()) { in = true; break; }
                    }
                    assertTrue(in, who + ": voxel de corpo fora da colisão em (" + ux + ", " + uz + ")");
                    n++;
                }
        return n;
    }

    @Test
    void coverIsRealRoofOverWalkableGround() throws IOException {
        // cobertura_u = onde a câmera precisa de forma só de câmera: em cada coluna da cobertura, nada sólido entre o
        // piso pisável (0,375 u) e a cabeça do herói (1,43 u) e algo sólido acima; quem declara camera "teto_coberto"
        // tem cobertura sobre a área segura do interior
        int cols = 0, declared = 0;
        for (JsonObject a : assets()) {
            JsonObject med = a.getAsJsonObject("medidas");
            String id = a.get("id").getAsString();
            JsonObject col = a.getAsJsonObject("colisao");
            boolean roof = col.has("camera") && col.get("camera").getAsString().equals("teto_coberto");
            if (roof) {
                declared++;
                assertTrue(med.has("cobertura_u") && med.getAsJsonArray("cobertura_u").size() > 0, id + ": teto sem cobertura medida");
                boolean over = false;
                for (JsonElement s : a.getAsJsonObject("pontos").getAsJsonArray("area_segura_u"))
                    for (JsonElement b : med.getAsJsonArray("cobertura_u")) {
                        JsonArray r = s.getAsJsonArray(), q = b.getAsJsonArray();
                        over |= q.get(0).getAsFloat() < r.get(2).getAsFloat() && r.get(0).getAsFloat() < q.get(2).getAsFloat()
                                && q.get(1).getAsFloat() < r.get(3).getAsFloat() && r.get(1).getAsFloat() < q.get(3).getAsFloat();
                    }
                assertTrue(over, id + ": a cobertura não fica sobre o interior (área segura)");
            }
            if (!med.has("cobertura_u")) continue;
            Model m = load(a);
            int lo = (int) Math.ceil(0.375 / m.vs - 1e-9), hi = (int) Math.ceil(1.43 / m.vs - 1e-9);
            for (JsonElement b : med.getAsJsonArray("cobertura_u")) {
                JsonArray q = b.getAsJsonArray();
                for (int x = m.vx(q.get(0).getAsFloat() + 1e-4f); x < m.vx(q.get(2).getAsFloat() - 1e-4f) + 1; x++)
                    for (int z = m.vx(q.get(1).getAsFloat() + 1e-4f); z < m.vx(q.get(3).getAsFloat() - 1e-4f) + 1; z++) {
                        assertFalse(m.solidIn(x, z, lo, hi - 1), id + ": cobertura com sólido na altura do corpo em " + x + "," + z);
                        assertTrue(m.solidIn(x, z, hi, m.oy + m.vox.sy), id + ": cobertura sem teto em " + x + "," + z);
                        cols++;
                    }
            }
        }
        assertTrue(declared >= 1 && cols > 100, "coberturas conferidas: " + cols + " colunas, " + declared + " tetos declarados");
    }

    @Test
    void treeTrunksSitWhereTheGameCollisionPutsThem() throws IOException {
        int trees = 0;
        for (JsonObject a : assets()) {
            JsonObject c = a.getAsJsonObject("colisao");
            if (!c.get("tipo").getAsString().equals("tronco")) continue;
            String id = a.get("id").getAsString();
            JsonObject med = a.getAsJsonObject("medidas");
            assertTrue(med.has("tronco_u"), id + ": árvore sem tronco medido");
            float[] tc = arr(med.getAsJsonObject("tronco_u"), "centro");
            float[] lo = arr(med.getAsJsonObject("caixa_u"), "min"), hi = arr(med.getAsJsonObject("caixa_u"), "max");
            float off = (float) Math.hypot(tc[0] - (lo[0] + hi[0]) / 2, tc[1] - (lo[2] + hi[2]) / 2);
            float r = c.get("raio").getAsFloat(), tr = med.getAsJsonObject("tronco_u").get("raio").getAsFloat();
            // Collision.addProp põe o círculo no centro da CAIXA; se o tronco está longe dele, o herói atravessa o tronco
            assertTrue(off <= Math.max(0.15f, r * 0.6f) || c.has("deslocamento"),
                    id + ": tronco a " + off + " u do centro da caixa (raio " + r + "): declare 'deslocamento' ou recentre");
            assertTrue(r <= tr + 0.15f && r >= tr * 0.5f, id + ": raio de colisão " + r + " incoerente com o tronco medido " + tr);
            trees++;
        }
        assertTrue(trees >= 4, "árvores conferidas: " + trees);
    }

    @Test
    void variantsKeepTheFootprintAndThePoints() throws IOException {
        int n = 0;
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            JsonObject base = a.getAsJsonObject("medidas").getAsJsonObject("pegada_u");
            float[] bl = arr(base, "min"), bh = arr(base, "max");
            float bH = arr(a.getAsJsonObject("medidas"), "dimensoes_u")[1];
            for (JsonElement e : a.getAsJsonObject("variacoes").getAsJsonArray("extremos")) {
                JsonObject v = e.getAsJsonObject();
                String vid = v.get("id").getAsString();
                assertTrue(vid.startsWith(id + "__"), vid + ": a variação não é do asset " + id + " (a pegada comparada seria de outro)");
                float[] vl = arr(v.getAsJsonObject("pegada_u"), "min"), vh = arr(v.getAsJsonObject("pegada_u"), "max");
                for (int i = 0; i < 2; i++) {
                    assertEquals(bl[i], vl[i], 0.51f, vid + ": a pegada mudou (min)");
                    assertEquals(bh[i], vh[i], 0.51f, vid + ": a pegada mudou (max)");
                }
                float vH = arr(v, "dimensoes_u")[1];
                assertEquals(bH, vH, bH * 0.16f, vid + ": a altura mudou mais que 16%");
                assertTrue(v.get("pontos_iguais").getAsBoolean(), vid + ": a variação moveu entrada/interação");
                n++;
            }
        }
        assertTrue(n >= 20, "variações conferidas: " + n);
    }

    /** Cantos (mundo) de uma pegada {"min","max"} crescida por g, girada por r graus (rotateY do viewer) e transladada. */
    static double[][] rect(JsonObject peg, double x, double z, double r, double g) {
        float[] lo = arr(peg, "min"), hi = arr(peg, "max");
        double[][] c = {{lo[0] - g, lo[1] - g}, {hi[0] + g, lo[1] - g}, {hi[0] + g, hi[1] + g}, {lo[0] - g, hi[1] + g}};
        double a = Math.toRadians(r), cs = Math.cos(a), sn = Math.sin(a);
        for (double[] p : c) { double px = p[0], pz = p[1]; p[0] = x + px * cs + pz * sn; p[1] = z - px * sn + pz * cs; }
        return c;
    }

    /** Separação de eixos (encostar não conta). */
    static boolean overlap(double[][] p, double[][] q) {
        for (double[][] poly : new double[][][]{p, q})
            for (int i = 0; i < 4; i++) {
                double ax = poly[(i + 1) % 4][1] - poly[i][1], az = -(poly[(i + 1) % 4][0] - poly[i][0]);
                double pmin = 1e9, pmax = -1e9, qmin = 1e9, qmax = -1e9;
                for (double[] v : p) { double d = ax * v[0] + az * v[1]; pmin = Math.min(pmin, d); pmax = Math.max(pmax, d); }
                for (double[] v : q) { double d = ax * v[0] + az * v[1]; qmin = Math.min(qmin, d); qmax = Math.max(qmax, d); }
                if (pmax <= qmin + 1e-6 || qmax <= pmin + 1e-6) return false;
            }
        return true;
    }

    /**
     * Conjuntos de ofício (lote 2): cada item existe no props.json, não invade a pegada da âncora nem a de outro item,
     * e nenhuma entrada da âncora fica a menos de 0,3 u (o raio do herói) de um item. Conferido de novo aqui, com a
     * pegada do catálogo, independente do validador do build.
     */
    @Test
    void craftSetsKeepAnchorEntrancesFreeAndDoNotOverlap() throws IOException {
        JsonObject cat = catalog(), props = json(ENV.resolve("props.json"));
        Map<String, JsonObject> peg = new HashMap<>();
        Map<String, JsonObject> rec = new HashMap<>();
        for (JsonObject a : assets()) {
            peg.put(a.get("id").getAsString(), a.getAsJsonObject("medidas").getAsJsonObject("pegada_u"));
            rec.put(a.get("id").getAsString(), a);
            for (JsonElement e : a.getAsJsonObject("variacoes").getAsJsonArray("extremos")) {
                JsonObject v = e.getAsJsonObject();
                if (v.has("prop") && !v.get("prop").isJsonNull()) peg.put(v.get("prop").getAsString(), v.getAsJsonObject("pegada_u"));
            }
        }
        int sets = 0;
        for (JsonElement e : cat.getAsJsonArray("conjuntos")) {
            JsonObject s = e.getAsJsonObject();
            String sid = s.get("id").getAsString(), anchor = s.get("ancora").getAsString();
            assertTrue(props.has(anchor) && peg.containsKey(anchor), sid + ": âncora " + anchor);
            double[][] a = rect(peg.get(anchor), 0, 0, 0, 0);
            List<double[][]> placed = new ArrayList<>();
            for (JsonElement ie : s.getAsJsonArray("itens")) {
                JsonObject it = ie.getAsJsonObject();
                String iid = it.get("id").getAsString();
                assertTrue(props.has(iid) && peg.containsKey(iid), sid + ": item " + iid + " inexistente");
                float[] pos = arr(it, "pos_u");
                double r = it.get("rumo").getAsDouble();
                double[][] box = rect(peg.get(iid), pos[0], pos[1], r, 0);
                assertFalse(overlap(box, a), sid + ": " + iid + " invade a âncora");
                for (double[][] o : placed) assertFalse(overlap(box, o), sid + ": " + iid + " sobrepõe outro item");
                double[][] grown = rect(peg.get(iid), pos[0], pos[1], r, 0.3);
                for (JsonElement de : rec.get(anchor).getAsJsonObject("pontos").getAsJsonArray("entradas")) {
                    float[] d = arr(de.getAsJsonObject(), "pos_u");
                    double[][] pt = {{d[0] - 0.01, d[2] - 0.01}, {d[0] + 0.01, d[2] - 0.01}, {d[0] + 0.01, d[2] + 0.01}, {d[0] - 0.01, d[2] + 0.01}};
                    assertFalse(overlap(grown, pt), sid + ": " + iid + " bloqueia uma entrada da âncora");
                }
                placed.add(box);
            }
            assertTrue(placed.size() >= 3, sid + ": conjunto com menos de 3 itens não comunica o ofício");
            // situação do conjunto = a do membro mais restrito: membro que depende de formas compostas não deixa o
            // conjunto liberado
            JsonObject sap = s.getAsJsonObject("aprovacao");
            boolean blocked = rec.get(anchor).getAsJsonObject("aprovacao").get("integracao").getAsString().equals("depende_colisao_composta");
            for (JsonElement ie : s.getAsJsonArray("itens")) {
                String base = ie.getAsJsonObject().get("id").getAsString().split("__")[0];
                blocked |= rec.get(base).getAsJsonObject("aprovacao").get("integracao").getAsString().equals("depende_colisao_composta");
            }
            assertEquals(blocked ? "depende_colisao_composta" : "liberado", sap.get("integracao").getAsString(), sid + ": situação do conjunto");
            sets++;
        }
        assertTrue(sets >= 5, "conjuntos conferidos: " + sets);
        // restrito a conjuntos: nunca distribuído sozinho, e o asset (ou uma variante publicada) está em algum conjunto
        Set<String> inSets = new HashSet<>();
        for (JsonElement e : cat.getAsJsonArray("conjuntos"))
            for (JsonElement ie : e.getAsJsonObject().getAsJsonArray("itens"))
                inSets.add(ie.getAsJsonObject().get("id").getAsString().split("__")[0]);
        for (JsonObject a : assets()) {
            JsonObject ap = a.getAsJsonObject("aprovacao");
            if (!ap.get("integracao").getAsString().equals("restrito_a_conjuntos")) continue;
            assertFalse(ap.get("distribuicao_procedural").getAsBoolean(), a.get("id").getAsString() + ": restrito e distribuível");
            assertTrue(inSets.contains(a.get("id").getAsString()), a.get("id").getAsString() + ": restrito a conjuntos e fora de todos");
        }
    }

    @Test
    void meshCostStaysWithinTheCategoryBudget() throws IOException {
        Map<String, Integer> worst = new HashMap<>();
        for (JsonObject a : assets()) {
            String id = a.get("id").getAsString();
            Model m = load(a);
            float[] data = VoxelMesher.buildDataMerged(m.vox, m.vs, m.ox, m.oy, m.oz, a.get("affineWind").getAsBoolean());
            int tris = data.length / 13 / 3;
            String cat = a.get("categoria").getAsString();
            String freq = a.get("frequencia").getAsString();
            int budget = TRI_BUDGET.getOrDefault(cat + "/" + freq, TRI_BUDGET.getOrDefault(cat, 3000));
            assertTrue(tris > 0, id + ": malha vazia");
            assertTrue(tris <= budget, id + ": " + tris + " triângulos > orçamento " + budget + " (" + cat + ")");
            worst.merge(cat, tris, Math::max);
        }
        System.out.println("conteudo: pior custo por categoria (triângulos) " + worst);
    }
}
