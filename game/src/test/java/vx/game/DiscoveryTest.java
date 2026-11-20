package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.world.RegionGenerator;
import vx.game.world.RegionLayout;
import vx.viewer.Terrain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;

/**
 * Rodada 12 / 12.1 — as descobertas de exploração, por TIPO de lugar:
 * <ul>
 *   <li><b>casa</b> (vestígio): o pote de barro nas cinzas da lareira; E paga 5 moedas UMA vez e o pote fica vazio;</li>
 *   <li><b>torre</b> (mirante): CHEGAR em cima da muralha baixa (um salto) registra e paga 8 moedas uma vez; o E não faz
 *   nada; embaixo, a placa mostra o verbo;</li>
 *   <li><b>arvore</b> (marco natural): só quando a árvore retorcida existe; E registra, sem moedas.</li>
 * </ul>
 * Ids derivados de rótulos; "Lugar novo" só na primeira vez de um tipo nesta aventura; o diário lista os tipos; o save
 * guarda (o campo opcional de antes), reabrir mantém, outro personagem e outro mundo não enxergam; os textos só dizem o
 * que a região tem (rumos conferidos aqui por conta própria).
 */
class DiscoveryTest {
    static final Path A = ProceduralWorldTest.A;
    static final String HOUSE = "desc.r0.ruina.0", TOWER = "desc.r0.torre.0", TREE = "desc.r0.arvore.0";
    /** Sementes de cada caso (conferidas no primeiro teste): casa, torre, torre + árvore. */
    static final long S_HOUSE = 77, S_TOWER = 2048, S_TREE = 4096;

    static GameLogic proc2(long seed, Path saveDir) {
        ProceduralScene ps = GeneratorV2Test.v2(seed);
        SceneDef s = ps.scene();
        Collision c = Collision.build(Terrain.of(ps.field), Collision.profiles(A), s.handPlaced, s.bodies, s);
        GameLogic g = new GameLogic(s, c);
        g.saveDir = saveDir;
        g.loadChoices(ProceduralWorldTest.CAT, saveDir.resolveSibling("sem-aparencias"));
        return g;
    }

    static SceneDef.Discovery of(GameLogic g, String id) {
        return g.scene.discoveries.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    /** Põe o herói a 0,9 u da descoberta, do lado da ruína, olhando para ela (a lareira, a árvore). */
    static SceneDef.Discovery approach(GameLogic g, String id) {
        SceneDef.Discovery d = of(g, id);
        RegionLayout.Building ruin = g.scene.procedural.layout.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow();
        float vx = ruin.x() - d.x(), vz = ruin.z() - d.z(), vl = (float) Math.hypot(vx, vz);
        float x = d.x() + vx / vl * 0.9f, z = d.z() + vz / vl * 0.9f;
        g.player.place(x, z, (float) Math.atan2(d.x() - x, d.z() - z), g.collision);
        g.player.snapshot();
        AdventureTest.frames(g, 1);
        return d;
    }

    @Test
    void cadaRuinaTemADescobertaDoSeuTipoEAArvoreSoQuandoExiste() {
        int houses = 0, towers = 0, trees = 0;
        for (long s : GeneratorV2Test.SEEDS) {
            ProceduralScene ps = GeneratorV2Test.v2(s);
            RegionLayout L = ps.layout;
            SceneDef sc = ps.scene();
            String ruin = L.buildings.stream().filter(b -> b.role().equals("ruina")).map(RegionLayout.Building::asset).findFirst().orElseThrow();
            boolean tree = L.props.stream().anyMatch(p -> p.name().equals("tree_gnarled"));
            assertEquals(1 + (tree ? 1 : 0), sc.discoveries.size(), "s" + s + ": a da ruína e, se houver, a da árvore");
            SceneDef.Discovery d = sc.discoveries.stream().filter(x -> !x.type().equals("arvore")).findFirst().orElseThrow();
            if (ruin.equals("ruin_house")) {
                houses++;
                assertEquals(HOUSE, d.id(), "id derivado do rótulo do POI");
                assertEquals("casa", d.type());
                assertEquals(5, d.coins(), "a comum");
                assertFalse(d.arrive());
                assertEquals("pote_achado", d.modelFull(), "as moedas vêm de algo que se vê");
            } else {
                towers++;
                assertEquals("ruin_tower", ruin);
                assertEquals(TOWER, d.id());
                assertEquals("torre", d.type());
                assertEquals(8, d.coins(), "a do mirante (pede o salto)");
                assertTrue(d.arrive(), "o mirante é de chegar");
            }
            RegionLayout.PointOfInterest poi = L.pois.stream().filter(p -> p.features().contains(d.id())).findFirst().orElseThrow();
            assertTrue(Math.hypot(d.x() - poi.x(), d.z() - poi.z()) < poi.radius(), "dentro do lugar");
            assertTrue(ps.reach.targets().get("descoberta:" + d.id()), "s" + s + ": alcançável na colisão real");
            if (tree) {
                trees++;
                SceneDef.Discovery t = of(new GameLogic(sc, Collision.build(Terrain.of(ps.field), Collision.profiles(A), sc.handPlaced, sc.bodies, sc)), TREE);
                assertEquals(0, t.coins(), "nem todo lugar tem tesouro: a árvore é só o registro");
                assertNull(t.modelFull());
                assertTrue(ps.reach.targets().get("descoberta:" + TREE), "s" + s + ": a árvore alcançável");
            }
        }
        assertTrue(houses >= 1 && towers >= 1 && trees >= 1, "as sementes de revisão cobrem os três: " + houses + "/" + towers + "/" + trees);
        assertEquals("ruin_house", GeneratorV2Test.v2(S_HOUSE).layout.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow().asset());
        assertEquals("ruin_tower", GeneratorV2Test.v2(S_TOWER).layout.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow().asset());
        assertTrue(GeneratorV2Test.v2(S_TREE).layout.props.stream().anyMatch(p -> p.name().equals("tree_gnarled")));
        assertTrue(ProceduralWorldTest.scene(1024).scene().discoveries.isEmpty(), "a v1 não tem descoberta (nada muda nela)");
    }

    @Test
    void oPoteDaLareiraPagaUmaVezEmQualquerTaxaDeQuadrosEFicaVazio(@TempDir Path d) {
        GameLogic g = proc2(S_HOUSE, d.resolve("a"));
        g.action("comecar");
        approach(g, HOUSE);
        assertNotNull(g.discoveryCandidate, "a placa aparece: o E alcança o pote");
        assertEquals("Pegar o pote da lareira", g.discoveryCandidate.verb());
        int before = g.quests.coins();
        AdventureTest.press(g, GLFW_KEY_E);
        assertTrue(g.quests.discovered(HOUSE));
        assertEquals(before + 5, g.quests.coins());
        assertTrue(g.toast.startsWith("Lugar novo: Casa tomada pela mata · +5 moedas"), g.toast);
        for (int i = 0; i < 120; i++) { AdventureTest.press(g, GLFW_KEY_E); AdventureTest.frames(g, 1); }   // apertar e apertar
        assertEquals(before + 5, g.quests.coins(), "a recompensa não repete");
        assertFalse(g.useDiscovery(of(g, HOUSE)), "de novo: nada pago");
        assertEquals("Olhar o pote vazio", of(g, HOUSE).verbSeen(), "a placa depois do achado não manda pegar o pote vazio");
        // o pote fica no chão de verdade (a laje), não no meio voxel do catálogo
        SceneDef.Discovery h = of(g, HOUSE);
        assertEquals(g.collision.ground.height(h.x(), h.z()) + 0.375f, g.discoveryFloor(h), 0.13f, "o pote na laje da casa");
    }

    @Test
    void oMirantePedeChegarEmCimaDaMuralhaEOEnaoFazNada(@TempDir Path d) {
        GameLogic g = proc2(S_TOWER, d.resolve("a"));
        g.action("comecar");
        SceneDef.Discovery m = of(g, TOWER);
        float top = g.discoveryFloor(m), ground = g.collision.ground.height(m.x(), m.z());
        assertTrue(top > ground + 0.9f && top < ground + 1.2f, "o ponto é o topo da muralha baixa (" + (top - ground) + " u)");
        // embaixo, do lado de dentro: a placa mostra o verbo, e o E não registra
        RegionLayout.Building ruin = g.scene.procedural.layout.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow();
        float vx = ruin.x() - m.x(), vz = ruin.z() - m.z(), vl = (float) Math.hypot(vx, vz);
        g.player.place(m.x() + vx / vl * 0.9f, m.z() + vz / vl * 0.9f, (float) Math.atan2(-vx, -vz), g.collision);
        g.player.snapshot();
        AdventureTest.frames(g, 2);
        assertSame(m, g.arriveHint, "embaixo: a placa do mirante");
        assertEquals("Subir na muralha", g.arriveHint.verb());
        assertNull(g.discoveryCandidate, "o mirante não se usa com E");
        int before = g.quests.coins();
        AdventureTest.press(g, GLFW_KEY_E);
        assertFalse(g.quests.discovered(TOWER));
        // em cima da muralha, no ponto: registra e paga uma vez (a subida com o salto é conferida na colisão composta, P74)
        g.player.place(m.x(), m.z(), g.player.yaw, g.collision);
        g.player.snapshot();
        assertEquals(top, g.player.y, 1e-3f, "de pé em cima da muralha");
        AdventureTest.frames(g, 2);
        assertTrue(g.quests.discovered(TOWER));
        assertEquals(before + 8, g.quests.coins());
        assertTrue(g.toast.startsWith("Lugar novo: Torre caída · +8 moedas"), g.toast);
        AdventureTest.frames(g, 30);
        assertEquals(before + 8, g.quests.coins(), "ficar lá em cima não paga de novo");
        assertNull(g.arriveHint, "achado: a placa some");
        // o texto fala do que a região tem: o rumo do posto visto da torre (conferido aqui por conta própria)
        RegionLayout L = g.scene.procedural.layout;
        String post = ProceduralScene.rumo(L.px - m.x(), L.pz - m.z()), village = ProceduralScene.rumo(L.sx - m.x(), L.sz - m.z());
        assertTrue(m.text().contains("posto da trilha a " + post) && m.text().contains("vila a " + village), m.text());
        assertEquals(L.pond != null, m.text().contains("lago"), "o lago só aparece se existe: " + m.text());
    }

    @Test
    void aArvoreRetorcidaRegistraSemMoedasEODiarioListaOsTipos(@TempDir Path d) {
        GameLogic g = proc2(S_TREE, d.resolve("a"));
        g.action("comecar");
        SceneDef.Discovery t = approach(g, TREE);
        assertNotNull(g.discoveryCandidate);
        assertEquals("Examinar a árvore", g.discoveryCandidate.verb());
        assertEquals(g.discoveryCandidate.verb(), g.discoveryCandidate.verbSeen(), "sem verbo próprio: o mesmo depois do achado");
        int before = g.quests.coins();
        AdventureTest.press(g, GLFW_KEY_E);
        assertTrue(g.quests.discovered(TREE));
        assertEquals(before, g.quests.coins(), "sem moedas");
        assertEquals("Lugar novo: Árvore retorcida", g.toast);
        assertTrue(t.text().contains("da torre caída"), "ao lado da ruína que existe aqui: " + t.text());
        // o diário: os tipos achados, na ordem, um registro por tipo
        g.useDiscovery(of(g, TOWER));
        List<String> places = g.knownPlaces().keySet().stream().map(SceneDef.Discovery::name).toList();
        assertEquals(List.of("Torre caída", "Árvore retorcida"), places, "um registro por tipo, na ordem dos lugares da região");
    }

    @Test
    void salvarEReabrirMantemEOutraAventuraNaoEnxerga(@TempDir Path d) throws Exception {
        Path mine = d.resolve("mundos/m-1/aventuras/p-a"), other = d.resolve("mundos/m-1/aventuras/p-b");
        GameLogic g = proc2(S_HOUSE, mine);
        g.action("comecar");
        approach(g, HOUSE);
        AdventureTest.press(g, GLFW_KEY_E);
        int coins = g.quests.coins();
        assertTrue(g.save(false), g.saveError);
        JsonObject o = JsonParser.parseString(Files.readString(mine.resolve(SaveGame.FILE))).getAsJsonObject();
        assertEquals(HOUSE, o.getAsJsonObject("progresso").getAsJsonArray("descobertas").get(0).getAsString());
        // reabrir: descoberta e moedas iguais; E de novo não paga (o pote está vazio)
        GameLogic r = proc2(S_HOUSE, mine);
        assertNull(r.continueBlocker(), r.continueBlocker());
        r.action("continuar-jogo");
        assertTrue(r.quests.discovered(HOUSE));
        assertEquals(coins, r.quests.coins());
        approach(r, HOUSE);
        AdventureTest.press(r, GLFW_KEY_E);
        assertEquals(coins, r.quests.coins());
        // outro personagem no MESMO mundo: a aventura dele não tem a descoberta
        GameLogic b = proc2(S_HOUSE, other);
        b.action("comecar");
        assertFalse(b.quests.discovered(HOUSE));
        approach(b, HOUSE);
        AdventureTest.press(b, GLFW_KEY_E);
        assertTrue(b.quests.discovered(HOUSE), "ele acha por conta própria");
        assertTrue(b.save(false), b.saveError);
        assertEquals(coins, coinsOf(mine), "o save do primeiro não mudou");
        // outro mundo (outra semente): nada
        GameLogic w = proc2(S_TOWER, d.resolve("mundos/m-2/aventuras/p-a"));
        w.action("comecar");
        assertTrue(w.quests.discoveries().isEmpty());
    }

    static int coinsOf(Path dir) throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(SaveGame.FILE))).getAsJsonObject().getAsJsonObject("progresso").get("moedas").getAsInt();
    }

    @Test
    void saveSemOCampoAbreIgualEDescobertaDesconhecidaERecusada(@TempDir Path d) throws Exception {
        GameLogic g = proc2(S_TREE, d.resolve("a"));
        g.action("comecar");
        assertTrue(g.save(false), g.saveError);
        Path f = d.resolve("a").resolve(SaveGame.FILE);
        String txt = Files.readString(f);
        assertFalse(txt.contains("descobertas"), "sem descoberta, o campo nem existe (o save fica como era)");
        GameLogic r = proc2(S_TREE, d.resolve("a"));
        assertNull(r.continueBlocker(), r.continueBlocker());
        // um save com descoberta que esta região não tem: recusado com o motivo (corrupção de verdade), original guardado
        JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        a.add("desc.r0.outra.9");
        o.getAsJsonObject("progresso").add("descobertas", a);
        Files.writeString(f, SaveGame.text(o));
        GameLogic bad = proc2(S_TREE, d.resolve("a"));
        assertTrue(bad.continueBlocker().contains("descoberta desconhecida"), bad.continueBlocker());
        assertTrue(Files.exists(f.resolveSibling(SaveGame.FILE + ".invalido")));
        assertEquals(RegionGenerator.V2, SaveGame.peekGenerator(f));
    }
}
