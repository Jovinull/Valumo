package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vx.game.world.RegionGenerator;
import vx.game.world.RegionLayout;
import vx.game.world.RegionProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 12 — o gerador v2 ("Mundos com Identidade"): determinismo (a mesma semente e versão dão a mesma região, em
 * qualquer ordem), a mesma semente na v1 e na v2 são regiões DIFERENTES, só assets liberados são distribuídos, os
 * restritos só dentro dos conjuntos, os conjuntos postos inteiros (itens no lugar exato do referencial da âncora), a ruína
 * presente com id estável, a densidade dentro do orçamento e a vegetação que não move a missão. Todas as regiões passam
 * pela conferência de alcance na colisão real (é o {@code ProceduralScene} que aceita a tentativa).
 */
class GeneratorV2Test {
    static final Path A = ProceduralWorldTest.A;
    static final long[] SEEDS = {1024, 2048, 4096, 77, 1, 555, 9001, 31337};
    static final Map<Long, ProceduralScene> CACHE = new HashMap<>();

    static ProceduralScene v2(long seed) { return CACHE.computeIfAbsent(seed, s -> ProceduralScene.create(A, s, RegionGenerator.V2)); }

    static RegionProfile profile() throws Exception {
        return RegionProfile.parse(JsonParser.parseString(Files.readString(A.resolve("scenes/regiao_v2_e.json"))).getAsJsonObject());
    }

    @Test
    void aMesmaSementeEVersaoDaoAMesmaRegiaoEmQualquerOrdem() {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (long s : SEEDS) a.add(ProceduralWorldTest.fingerprint(ProceduralScene.create(A, s, RegionGenerator.V2)));
        for (int i = SEEDS.length - 1; i >= 0; i--) b.addFirst(ProceduralWorldTest.fingerprint(ProceduralScene.create(A, SEEDS[i], RegionGenerator.V2)));
        assertEquals(a, b, "a ordem de geração não muda nada");
        // a v1 no meio não interfere na v2 (nenhum estado compartilhado)
        ProceduralWorldTest.scene(1024);
        assertEquals(a.getFirst(), ProceduralWorldTest.fingerprint(ProceduralScene.create(A, 1024, RegionGenerator.V2)));
    }

    @Test
    void aMesmaSementeNaV1ENaV2SaoRegioesDiferentes() {
        for (long s : SEEDS) {
            ProceduralScene p1 = ProceduralWorldTest.scene(s), p2 = v2(s);
            assertEquals(RegionGenerator.V2, p2.version());
            assertNotEquals(p1.layout.key(), p2.layout.key(), "identidade da região");
            assertNotEquals(p1.field.id(), p2.field.id(), "o relevo não é compartilhado no cache");
            assertNotEquals(ProceduralWorldTest.fingerprint(p1), ProceduralWorldTest.fingerprint(p2));
            assertEquals(2, SaveGame.worldJson(p2).get("versaoGerador").getAsInt(), "o save grava a v2");
        }
    }

    @Test
    void soAssetsLiberadosERestritosSoDentroDosConjuntos() throws Exception {
        RegionProfile P = profile();
        JsonObject cat = new JsonObject();
        for (var e : JsonParser.parseString(Files.readString(A.resolve("environment/e/conteudo_catalogo.json"))).getAsJsonObject().getAsJsonArray("assets"))
            cat.add(e.getAsJsonObject().get("id").getAsString(), e);
        Set<String> forbidden = Set.of("tree_tiered", "stone_circle");                   // a torre foi liberada na rodada 12.1 (P74)
        int sheds = 0, regions = 0;
        for (long s = 1; s <= 30; s++) {
            RegionLayout L = v2(s).layout;
            regions++;
            Set<String> inSets = new java.util.HashSet<>();
            for (RegionLayout.Building b : L.buildings)
                if (b.set() != null) for (RegionProfile.Item it : P.set(b.set()).items()) inSets.add(it.prop());
            int shedsHere = 0;
            for (RegionLayout.Prop p : L.props) {
                assertFalse(forbidden.contains(p.name()), "s" + s + ": " + p.name() + " não é distribuível");
                String base = p.name().split("__")[0];
                if (cat.has(base)) {
                    String integ = cat.getAsJsonObject(base).getAsJsonObject("aprovacao").get("integracao").getAsString();
                    assertTrue(integ.equals("liberado") || integ.equals("restrito_a_conjuntos"), "s" + s + ": " + p.name() + " está " + integ);
                    if (integ.equals("restrito_a_conjuntos")) assertTrue(inSets.contains(p.name()), "s" + s + ": " + p.name() + " solto, fora de conjunto");
                }
                if (p.name().equals("woodshed_open")) shedsHere++;
            }
            assertTrue(shedsHere <= 1, "s" + s + ": lenheiro coberto é incomum (no máximo um)");
            if (shedsHere == 1) assertTrue(L.buildings.stream().anyMatch(b -> "conjunto.cacador".equals(b.set())), "o lenheiro só vem com o caçador");
            sheds += shedsHere;
        }
        assertTrue(sheds < regions / 2, "lenheiro em " + sheds + " de " + regions + " regiões: incomum");
    }

    @Test
    void conjuntosSaoPostosInteirosComOsItensNoLugarExato() throws Exception {
        RegionProfile P = profile();
        int sets = 0;
        Set<String> seen = new java.util.TreeSet<>();
        for (long s = 1; s <= 30; s++) {
            RegionLayout L = v2(s).layout;
            for (RegionLayout.Building b : L.buildings) {
                if (b.set() == null) continue;
                sets++;
                seen.add(b.set());
                RegionProfile.SetDef d = P.set(b.set());
                assertEquals(d.anchor(), b.asset());
                for (RegionProfile.Item it : d.items()) {
                    double t = Math.toRadians(b.rotDeg());
                    float wx = b.x() + (float) (Math.cos(t) * it.x() + Math.sin(t) * it.z()), wz = b.z() + (float) (-Math.sin(t) * it.x() + Math.cos(t) * it.z());
                    boolean found = L.props.stream().anyMatch(p -> p.name().equals(it.prop()) && Math.hypot(p.x() - wx, p.z() - wz) < 1e-3
                            && Math.abs(Math.IEEEremainder(p.rotDeg() - b.rotDeg() - it.yawDeg(), 360)) < 1e-2);
                    assertTrue(found, "s" + s + " " + b.set() + ": " + it.prop() + " fora do lugar do conjunto");
                }
            }
        }
        assertTrue(sets >= 30, "conjuntos postos em 30 regiões: " + sets);
        assertEquals(Set.of("conjunto.cacador", "conjunto.comerciante", "conjunto.ferreiro", "conjunto.guardiao", "conjunto.herbalista"), seen,
                "os cinco conjuntos aparecem");
    }

    /**
     * Rodada 12.1 — a ruína é casa OU torre (sorteio da semente; a casa a mais comum), sempre uma, com id derivado do rótulo
     * do tipo; e nas sementes 1–30 aparecem as duas.
     */
    @Test
    void aRuinaEOLugarAExplorarComIdEstavel() {
        int houses = 0, towers = 0;
        for (long s = 1; s <= 30; s++) {
            RegionLayout L = v2(s).layout;
            RegionLayout.Building b = L.buildings.stream().filter(x -> x.role().equals("ruina")).findFirst().orElseThrow();
            if (b.asset().equals("ruin_house")) houses++; else towers++;
        }
        assertTrue(houses > towers && towers >= 5, "casa a mais comum, torre presente: " + houses + " casas, " + towers + " torres");
        for (long s : SEEDS) {
            RegionLayout L = v2(s).layout;
            assertEquals(1, L.buildings.stream().filter(b -> b.role().equals("ruina")).count(), "s" + s + ": a v2 sempre tem uma ruína");
            boolean tower = L.buildings.stream().anyMatch(b -> b.role().equals("ruina") && b.asset().equals("ruin_tower"));
            RegionLayout.PointOfInterest r = L.poi(tower ? "ruin_tower" : "ruin");
            assertNotNull(r, "s" + s);
            assertEquals(tower ? "poi.r0.torre.0" : "poi.r0.ruina.0", r.id(), "id derivado de rótulo, nunca de coordenada");
            assertTrue(L.branches.stream().anyMatch(RegionLayout.Branch::carve), "s" + s + ": a trilha até a ruína");
            assertTrue(v2(s).reach.targets().keySet().stream().anyMatch(k -> k.startsWith("entrada:" + r.id())), "a entrada é conferida");
            assertTrue(v2(s).reach.targets().values().stream().allMatch(Boolean::booleanValue), "s" + s + ": tudo alcançável");
            float d = (float) Math.hypot(r.x() - L.sx, r.z() - L.sz);
            assertTrue(d > L.plateauR, "a ruína fica fora do assentamento");
            // quem vai à ruína não passa pela arena dos fungos: a trilha dela sai da principal antes do posto
            float[] fork = L.branches.stream().filter(RegionLayout.Branch::carve).findFirst().orElseThrow().points().getFirst();
            assertTrue(Math.hypot(fork[0] - L.px, fork[1] - L.pz) >= L.clearingR + 3.4f, "s" + s + ": a trilha da ruína sai de dentro da arena");
        }
    }

    /** Orçamento de densidade (medido na rodada 12): construções por região, props totais e formas de colisão. */
    @Test
    void densidadeDentroDoOrcamento() {
        for (long s = 1; s <= 30; s++) {
            ProceduralScene ps = v2(s);
            RegionLayout L = ps.layout;
            long built = L.buildings.stream().filter(b -> !b.role().equals("praca") && !b.role().equals("ruina")).count();
            assertTrue(built >= 3 && built <= 5, "s" + s + ": construções " + built + " (estalagem + 2 a 4)");
            assertTrue(L.props.size() <= 190, "s" + s + ": props " + L.props.size());
            SceneDef sc = ps.scene();
            Collision c = Collision.build(vx.viewer.Terrain.of(ps.field), Collision.profiles(A), sc.handPlaced, sc.bodies, sc);
            // rodada 12.1: as formas compostas passaram a ter o topo EXATO (a parede em degraus, a laje, os degraus) e a medida
            // subiu (340–718 nas sementes 1–30); com o índice espacial da colisão a conferência de alcance mediana CAIU de 17
            // para 1 ms. O limite antigo (520) existia porque cada consulta percorria a lista inteira
            assertTrue(c.shapes.size() <= 900, "s" + s + ": formas de colisão " + c.shapes.size());
        }
    }

    /**
     * As raras da mata saem de verdade (a árvore retorcida chegou a não sair em nenhuma de 60 sementes: o anel em volta do
     * patamar já estava tomado pela mata) e a retorcida fica junto da ruína, fora da trilha.
     */
    @Test
    void asArvoresRarasSaemEARetorcidaFicaJuntoDaRuina() throws Exception {
        int gnarled = 0, dead = 0;
        for (long s = 1; s <= 30; s++) {
            RegionLayout L = v2(s).layout;
            RegionLayout.Building r = L.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow();
            boolean tree = L.props.stream().anyMatch(p -> p.name().equals("tree_gnarled"));
            assertEquals(tree, L.features.stream().anyMatch(f -> f.id().equals("desc.r0.arvore.0")), "s" + s + ": a descoberta da árvore ⇔ a árvore existe");
            for (RegionLayout.Prop p : L.props) {
                if (p.name().equals("tree_gnarled")) {
                    gnarled++;
                    float[] pad = L.pads.getFirst();
                    double d = Math.hypot(p.x() - pad[0], p.z() - pad[1]);
                    assertTrue(d <= pad[2] - 0.69f, "s" + s + ": a retorcida fora do plano do patamar da ruína (" + d + ")");
                    assertTrue(Math.hypot(p.x() - r.x(), p.z() - r.z()) > 1.5f, "s" + s + ": a retorcida em cima da ruína");
                }
                if (p.name().equals("tree_dead")) dead++;
            }
        }
        assertTrue(gnarled >= 5 && gnarled <= 20, "árvore retorcida em " + gnarled + " de 30 regiões (rara, mas presente)");
        // a caixa usada na conferência de desnível é a do modelo de verdade
        JsonObject e = JsonParser.parseString(Files.readString(A.resolve("environment/e/props.json"))).getAsJsonObject().getAsJsonObject("tree_gnarled");
        float vs = e.get("voxelSize").getAsFloat();
        var o = e.getAsJsonArray("origin");
        var sz = e.getAsJsonArray("sizeVoxels");
        assertEquals((o.get(0).getAsFloat() + sz.get(0).getAsFloat() / 2) * vs, vx.game.world.RegionV2Access.gnarledCenter()[0], 1e-4f);
        assertEquals((o.get(2).getAsFloat() + sz.get(2).getAsFloat() / 2) * vs, vx.game.world.RegionV2Access.gnarledCenter()[1], 1e-4f);
        assertEquals(sz.get(0).getAsFloat() * vs / 2, vx.game.world.RegionV2Access.gnarledHalf()[0], 1e-4f);
        assertEquals(sz.get(2).getAsFloat() * vs / 2, vx.game.world.RegionV2Access.gnarledHalf()[1], 1e-4f);
        assertTrue(dead >= 3 && dead <= 20, "árvore seca em " + dead + " de 30 regiões");
    }

    @Test
    void aVegetacaoNaoMoveAMissaoNemAsConstrucoes() throws Exception {
        RegionProfile P = profile();
        for (long s : SEEDS) {
            RegionLayout full = RegionGenerator.compose(s, 0, RegionGenerator.V2, P, true), bare = RegionGenerator.compose(s, 0, RegionGenerator.V2, P, false);
            assertEquals(full.buildings, bare.buildings);
            assertEquals(full.spawns, bare.spawns);
            // a descoberta da árvore retorcida é da mata (rodada 12.1): o resto das features e dos POIs não muda
            assertEquals(full.features.stream().filter(f -> !f.id().contains(".arvore.")).toList(), bare.features);
            assertEquals(full.npcs, bare.npcs);
            assertEquals(full.pois.stream().filter(p -> !p.id().contains(".arvore.")).toList(), bare.pois);
        }
    }

    @Test
    void oPerfilCongeladoAindaBateComOCatalogo() throws Exception {
        RegionProfile P = profile();
        JsonObject cat = new JsonObject();
        for (var e : JsonParser.parseString(Files.readString(A.resolve("environment/e/conteudo_catalogo.json"))).getAsJsonObject().getAsJsonArray("assets"))
            cat.add(e.getAsJsonObject().get("id").getAsString(), e);
        int n = 0;
        for (RegionProfile.Asset a : P.assets().values()) {
            String base = a.id().split("__")[0];
            if (!cat.has(base)) continue;                                   // props aprovados da v1 (pegada do props.json)
            var peg = cat.getAsJsonObject(base).getAsJsonObject("medidas").getAsJsonObject("pegada_u");
            assertEquals(peg.getAsJsonArray("min").get(0).getAsFloat(), a.x0(), 1e-3f, a.id() + ": pegada mudou (um asset mudou: prop novo ou versão nova)");
            assertEquals(peg.getAsJsonArray("max").get(1).getAsFloat(), a.z1(), 1e-3f, a.id());
            String integ = cat.getAsJsonObject(base).getAsJsonObject("aprovacao").get("integracao").getAsString();
            assertTrue(integ.equals("liberado") || integ.equals("restrito_a_conjuntos"), a.id() + " no perfil está " + integ);
            n++;
        }
        assertTrue(n >= 35, "assets da biblioteca no perfil: " + n);
    }
}
