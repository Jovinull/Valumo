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

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;

/**
 * Rodada 12 — a primeira descoberta de exploração: o ponto na lareira da ruína (id derivado do POI), E paga as moedas UMA
 * vez (em qualquer taxa de quadros), o diário e o save guardam, reabrir mantém, outro personagem e outro mundo não
 * enxergam, e um save sem o campo (de antes) abre igual.
 */
class DiscoveryTest {
    static final Path A = ProceduralWorldTest.A;
    static final String ID = "desc.r0.ruina.0";

    static GameLogic proc2(long seed, Path saveDir) {
        ProceduralScene ps = GeneratorV2Test.v2(seed);
        SceneDef s = ps.scene();
        Collision c = Collision.build(Terrain.of(ps.field), Collision.profiles(A), s.handPlaced, s.bodies, s);
        GameLogic g = new GameLogic(s, c);
        g.saveDir = saveDir;
        g.loadChoices(ProceduralWorldTest.CAT, saveDir.resolveSibling("sem-aparencias"));
        return g;
    }

    /** Põe o herói a 0,9 u da descoberta, do lado de dentro da ruína, olhando para ela. */
    static SceneDef.Discovery approach(GameLogic g) {
        SceneDef.Discovery d = g.scene.discoveries.getFirst();
        RegionLayout.Building ruin = g.scene.procedural.layout.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElseThrow();
        float vx = ruin.x() - d.x(), vz = ruin.z() - d.z(), vl = (float) Math.hypot(vx, vz);
        float x = d.x() + vx / vl * 0.9f, z = d.z() + vz / vl * 0.9f;
        g.player.place(x, z, (float) Math.atan2(d.x() - x, d.z() - z), g.collision);
        g.player.snapshot();
        AdventureTest.frames(g, 1);
        return d;
    }

    @Test
    void aDescobertaTemIdEstavelEFicaNaLareiraDaRuina() {
        for (long s : new long[]{1024, 2048, 77}) {
            ProceduralScene ps = GeneratorV2Test.v2(s);
            SceneDef sc = ps.scene();
            assertEquals(1, sc.discoveries.size());
            SceneDef.Discovery d = sc.discoveries.getFirst();
            assertEquals(ID, d.id(), "derivado do POI, igual em toda semente");
            assertEquals(5, d.coins(), "pequena perto da missão (30)");
            RegionLayout.PointOfInterest r = ps.layout.poi("ruin");
            assertEquals(java.util.List.of(ID), r.features());
            assertTrue(Math.hypot(d.x() - r.x(), d.z() - r.z()) < r.radius(), "dentro do lugar");
            assertTrue(ps.reach.targets().get("descoberta:" + ID), "alcançável na colisão real");
        }
        assertTrue(ProceduralWorldTest.scene(1024).scene().discoveries.isEmpty(), "a v1 não tem descoberta (nada muda nela)");
    }

    @Test
    void pagaUmaVezSoEmQualquerTaxaDeQuadros(@TempDir Path d) {
        GameLogic g = proc2(1024, d.resolve("a"));
        g.action("comecar");
        approach(g);
        assertNotNull(g.discoveryCandidate, "a placa aparece: o E alcança a lareira");
        int before = g.quests.coins();
        AdventureTest.press(g, GLFW_KEY_E);
        assertTrue(g.quests.discovered(ID));
        assertEquals(before + 5, g.quests.coins());
        for (int i = 0; i < 120; i++) { AdventureTest.press(g, GLFW_KEY_E); AdventureTest.frames(g, 1); }   // apertar e apertar
        assertEquals(before + 5, g.quests.coins(), "a recompensa não repete");
        assertFalse(g.useDiscovery(g.scene.discoveries.getFirst()), "de novo: nada pago");
    }

    @Test
    void salvarEReabrirMantemEOutraAventuraNaoEnxerga(@TempDir Path d) throws Exception {
        Path mine = d.resolve("mundos/m-1/aventuras/p-a"), other = d.resolve("mundos/m-1/aventuras/p-b");
        GameLogic g = proc2(2048, mine);
        g.action("comecar");
        approach(g);
        AdventureTest.press(g, GLFW_KEY_E);
        int coins = g.quests.coins();
        assertTrue(g.save(false), g.saveError);
        JsonObject o = JsonParser.parseString(Files.readString(mine.resolve(SaveGame.FILE))).getAsJsonObject();
        assertEquals(ID, o.getAsJsonObject("progresso").getAsJsonArray("descobertas").get(0).getAsString());
        // reabrir: descoberta e moedas iguais; E de novo não paga
        GameLogic r = proc2(2048, mine);
        assertNull(r.continueBlocker(), r.continueBlocker());
        r.action("continuar-jogo");
        assertTrue(r.quests.discovered(ID));
        assertEquals(coins, r.quests.coins());
        approach(r);
        AdventureTest.press(r, GLFW_KEY_E);
        assertEquals(coins, r.quests.coins());
        // outro personagem no MESMO mundo: a aventura dele não tem a descoberta
        GameLogic b = proc2(2048, other);
        b.action("comecar");
        assertFalse(b.quests.discovered(ID));
        approach(b);
        AdventureTest.press(b, GLFW_KEY_E);
        assertTrue(b.quests.discovered(ID), "ele descobre por conta própria");
        assertTrue(b.save(false), b.saveError);
        assertEquals(coins, coinsOf(mine), "o save do primeiro não mudou");
        // outro mundo (outra semente): nada
        GameLogic w = proc2(77, d.resolve("mundos/m-2/aventuras/p-a"));
        w.action("comecar");
        assertFalse(w.quests.discovered(ID));
    }

    static int coinsOf(Path dir) throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(SaveGame.FILE))).getAsJsonObject().getAsJsonObject("progresso").get("moedas").getAsInt();
    }

    @Test
    void saveSemOCampoAbreIgualEDescobertaDesconhecidaERecusada(@TempDir Path d) throws Exception {
        GameLogic g = proc2(4096, d.resolve("a"));
        g.action("comecar");
        assertTrue(g.save(false), g.saveError);
        Path f = d.resolve("a").resolve(SaveGame.FILE);
        String txt = Files.readString(f);
        assertFalse(txt.contains("descobertas"), "sem descoberta, o campo nem existe (o save fica como era)");
        GameLogic r = proc2(4096, d.resolve("a"));
        assertNull(r.continueBlocker(), r.continueBlocker());
        // um save com descoberta que esta região não tem: recusado com o motivo (corrupção de verdade), original guardado
        JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        a.add("desc.r0.outra.9");
        o.getAsJsonObject("progresso").add("descobertas", a);
        Files.writeString(f, SaveGame.text(o));
        GameLogic bad = proc2(4096, d.resolve("a"));
        assertTrue(bad.continueBlocker().contains("descoberta desconhecida"), bad.continueBlocker());
        assertTrue(Files.exists(f.resolveSibling(SaveGame.FILE + ".invalido")));
        assertEquals(RegionGenerator.V2, SaveGame.peekGenerator(f));
    }
}
