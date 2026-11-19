package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.quest.QuestDefinition;
import vx.game.quest.QuestState;
import vx.game.world.ProceduralField;
import vx.game.world.RegionGenerator;
import vx.game.world.RegionLayout;
import vx.game.world.Rng;
import vx.viewer.ComponentCatalog;
import vx.viewer.Terrain;
import vx.viewer.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 11 — mundo procedural: reprodutibilidade por semente, diferença real entre sementes, identidades estáveis,
 * alcançabilidade na colisão real, a missão instanciada a partir de fatos do mundo, a aventura concluída em cada semente
 * de teste e o save v2 (mundo, vínculos) sem quebrar o v1 da rodada 10.
 */
class ProceduralWorldTest {
    static final Path A = Path.of("assets");
    static final ComponentCatalog CAT = ComponentCatalog.load(A);
    /** Sementes de validação (a 77 é a de teste com lago). */
    static final long[] SEEDS = {1024, 2048, 4096, 77};

    static ProceduralScene scene(long seed) { return ProceduralScene.create(A, seed); }

    /** Tudo que a semente decide, como texto (ordem fixa): para comparar duas gerações. */
    static String fingerprint(ProceduralScene ps) {
        StringBuilder sb = new StringBuilder();
        RegionLayout L = ps.layout;
        sb.append(L.attempt).append(L.arrangement).append(L.forest).append(java.util.Arrays.toString(L.pond)).append(java.util.Arrays.toString(L.hill));
        sb.append(ps.worldDoc).append(ps.sceneDoc).append(ps.combatDoc);
        sb.append(ps.binding);
        for (RegionLayout.PointOfInterest p : L.pois) sb.append(p);
        for (int i = -20; i <= 20; i += 4)
            for (int k = -20; k <= 20; k += 4) sb.append(ps.field.height(i, k, false)).append(',');
        return sb.toString();
    }

    // ------------------------------------------------------------------ mundo

    @Test
    void mesmaSementeGeraOMesmoMundoEmQualquerOrdem() {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (long s : SEEDS) a.add(fingerprint(scene(s)));
        for (int i = SEEDS.length - 1; i >= 0; i--) b.addFirst(fingerprint(scene(SEEDS[i])));   // ordem inversa
        assertEquals(a, b, "a ordem de geração não muda nada");
        // a mesma grade de relevo (o desenho e a colisão leem dela)
        ProceduralScene p = scene(2048), q = scene(2048);
        Terrain t1 = Terrain.of(p.field), t2 = Terrain.of(q.field);
        assertSame(t1, t2, "mesmo campo, mesma grade (cache pela identidade)");
        assertEquals(p.field.id(), q.field.id());
    }

    @Test
    void sementesDiferentesSaoLugaresDiferentes() {
        List<ProceduralScene> ps = new ArrayList<>();
        for (long s : SEEDS) ps.add(scene(s));
        for (int i = 0; i < ps.size(); i++)
            for (int j = i + 1; j < ps.size(); j++) {
                RegionLayout a = ps.get(i).layout, b = ps.get(j).layout;
                float ds = (float) Math.hypot(a.sx - b.sx, a.sz - b.sz), dp = (float) Math.hypot(a.px - b.px, a.pz - b.pz);
                assertTrue(ds > 3f || dp > 3f, "assentamento ou posto em outro lugar: " + i + "×" + j);
                // o relevo muda de verdade (não só flores): diferença média de altura numa grade de 2 u
                double sum = 0;
                int n = 0;
                for (int x = -16; x <= 16; x += 2)
                    for (int z = -16; z <= 16; z += 2) { sum += Math.abs(ps.get(i).field.height(x, z, false) - ps.get(j).field.height(x, z, false)); n++; }
                assertTrue(sum / n > 0.25, "relevo quase igual entre " + i + " e " + j + ": " + sum / n);
                assertNotEquals(ps.get(i).binding.questId(), ps.get(j).binding.questId());
            }
        Set<String> rumos = new HashSet<>(), arranjos = new HashSet<>();
        for (ProceduralScene p : ps) { rumos.add(p.binding.rumo()); arranjos.add(p.layout.arrangement + "/" + p.layout.forest); }
        assertTrue(rumos.size() >= 3, "a trilha sai para lados diferentes: " + rumos);
        assertTrue(arranjos.size() >= 3, "composições diferentes: " + arranjos);
        assertTrue(ps.stream().anyMatch(p -> p.layout.pond != null) && ps.stream().anyMatch(p -> p.layout.pond == null), "com e sem lago");
    }

    @Test
    void identidadesUnicasELimitesValidos() {
        Set<String> quests = new HashSet<>();
        for (long s : SEEDS) {
            ProceduralScene ps = scene(s);
            RegionLayout L = ps.layout;
            Set<String> ids = new HashSet<>();
            for (RegionLayout.PointOfInterest p : L.pois) assertTrue(ids.add(p.id()), p.id());
            for (RegionLayout.Feature f : L.features) assertTrue(ids.add(f.id()), f.id());
            for (RegionLayout.Spawn sp : L.spawns) assertTrue(ids.add(sp.key()), sp.key());
            Set<String> npcs = new HashSet<>();
            for (RegionLayout.Npc n : L.npcs) assertTrue(npcs.add(n.id()), "morador repetido " + n.id());
            assertTrue(npcs.contains("kelyra"), "a estalagem sempre tem a Kelyra");
            if (L.pond == null) assertFalse(npcs.contains("ondina"), "sem lago não há guia costeira");
            assertTrue(quests.add(ps.binding.questId()), "instância repetida entre mundos");
            assertTrue(ps.binding.questId().startsWith("quest.instance." + ps.worldId() + ".r0."), ps.binding.questId());
            for (RegionLayout.Prop p : L.props) {
                assertTrue(Math.abs(p.x()) <= World.SIZE / 2 && Math.abs(p.z()) <= World.SIZE / 2, p + " fora do mundo");
                float h = ps.field.height(p.x(), p.z(), false);
                assertTrue(Float.isFinite(h) && h > World.Y_MIN && h < World.Y_MAX, p + " altura " + h);
            }
            for (float[] l : L.limits) assertTrue(Math.abs(l[0]) + l[2] <= World.SIZE / 2 && Math.abs(l[1]) + l[2] <= World.SIZE / 2, "limite fora do mundo");
        }
    }

    @Test
    void tudoQueImportaEAlcancavelNaColisaoReal() {
        for (long s : new long[]{1024, 2048, 4096, 77, 1, 2, 3, 9001, 31337, 555}) {
            ProceduralScene ps = scene(s);
            assertTrue(ps.reach.problems().isEmpty(), s + ": " + ps.reach.problems());
            assertFalse(ps.reach.targets().isEmpty());
            ps.reach.targets().forEach((k, v) -> assertTrue(v, s + ": " + k));
            assertTrue(ps.reach.arenaFree() >= 0.75f, s + ": arena " + ps.reach.arenaFree());
            assertTrue(ps.reach.targets().keySet().stream().filter(k -> k.startsWith("falar:")).count() == ps.layout.npcs.size());
        }
    }

    @Test
    void obstaculoNoCaminhoEMarcoTapadoSaoRecusados() {
        ProceduralScene ps = scene(1024);
        RegionLayout L = RegionGenerator.compose(1024, ps.layout.attempt);
        // um muro de pedras atravessando a trilha a 3/4 do caminho (fora do círculo do assentamento: lá dentro o herói dá
        // a volta, e a validação acerta ao aprovar)
        int i = L.path.size() * 3 / 4;
        float[] mid = L.path.get(i);
        float nx = -(L.path.get(i + 1)[1] - mid[1]), nz = L.path.get(i + 1)[0] - mid[0];
        float nl = (float) Math.hypot(nx, nz);
        for (float d = -5.5f; d <= 5.5f; d += 1.1f) L.props.add(new RegionLayout.Prop("rock_l", mid[0] + nx / nl * d, mid[1] + nz / nl * d, 0f));
        Reachability.Report r = check(L);
        assertFalse(r.problems().isEmpty(), "a trilha bloqueada precisa reprovar");
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("marco") || p.contains("inimigo") || p.contains("recomeço")), r.problems().toString());
    }

    static Reachability.Report check(RegionLayout L) {
        ProceduralField f = new ProceduralField(L);
        ProceduralScene.Docs d = ProceduralScene.docs(A, L);
        SceneDef s = SceneDef.fromDoc(A, ProceduralScene.LAYOUT, d.scene(), d.combat(), f);
        Terrain t = Terrain.of(f);
        return Reachability.check(L, s, Collision.build(t, Collision.profiles(A), s.handPlaced, s.bodies, s), t);
    }

    @Test
    void vegetacaoNaoMoveLugaresEncontroNemMarco() {
        for (long s : SEEDS) {
            RegionLayout full = RegionGenerator.compose(s, 0), bare = RegionGenerator.compose(s, 0, false);
            assertEquals(bare.spawns, full.spawns, "encontro");
            assertEquals(bare.features, full.features, "marco");
            assertEquals(bare.npcs, full.npcs, "moradores");
            assertEquals(bare.pois, full.pois, "lugares");
            assertTrue(full.props.size() > bare.props.size() + 40, "a vegetação existe");
        }
        // rótulos diferentes, geradores independentes; os mesmos rótulos, o mesmo número
        assertEquals(Rng.derive(7, "a", "b"), Rng.derive(7, "a", "b"));
        assertNotEquals(Rng.derive(7, "ab", "c"), Rng.derive(7, "a", "bc"));
        assertNotEquals(Rng.derive(7, "a"), Rng.derive(8, "a"));
    }

    // ------------------------------------------------------------------ missão vinculada

    @Test
    void modeloEncontraOPostoEVinculaIdsEstaveis() {
        for (long s : SEEDS) {
            ProceduralScene ps = scene(s);
            QuestDefinition q = ps.quest;
            assertNotNull(q, s + ": o mundo tem os fatos do modelo");
            RegionLayout.PointOfInterest poi = ps.layout.poi("trail_post");
            assertEquals(poi.id(), ps.binding.poi());
            assertEquals(poi.spawns(), q.objectives().getFirst().targets(), "alvos = o encontro do posto");
            assertEquals(poi.features().getFirst(), q.objectives().get(1).object());
            assertEquals(ps.binding.giver(), q.giver());
            assertTrue(ps.layout.npcs.stream().anyMatch(n -> n.id().equals(q.giver())), "o doador mora ali");
            assertTrue(List.of("kelyra", "bramor").contains(q.giver()), "papel compatível (caminhos/trilhas)");
            assertEquals(q.giver(), q.objectives().getLast().npc());
            // o encontro do combate é o do posto, com as mesmas identidades
            SceneDef sc = ps.scene();
            assertEquals("posto", sc.combat.encounterDefault);
            assertEquals(poi.spawns(), sc.combat.encounter("posto").stream().map(CombatDef.Spawn::key).toList());
            // textos: nenhum parâmetro sem valor, o rumo real aparece, ninguém "vê" o marco
            String desc = q.description();
            assertFalse(desc.contains("{"), desc);
            assertTrue(desc.contains("trilha do " + ps.binding.rumo()), desc);
            for (String ctx : DialogueBook.GIVER_CONTEXTS)
                for (SceneDef.Line l : ps.dialogues.entry(q.giver(), q.id(), ctx).lines()) {
                    assertFalse(l.text().contains("{") || l.text().contains("}"), l.text());
                    assertFalse(l.text().matches("(?i).*(daqui eu vejo|eu vi!|dá para ver).*"), "afirma ver o marco: " + l.text());
                }
            for (SceneDef.Interactive n : sc.interactives)
                for (SceneDef.Line l : n.lines()) assertFalse(l.text().contains("{"), n.name() + ": " + l.text());
        }
    }

    @Test
    void semOsFatosNaoHaMissao() {
        RegionLayout L = RegionGenerator.compose(1024, 0);
        JsonObject tpl = ProceduralScene.read(A, "modelos_missao_e.json").getAsJsonArray("modelos").get(0).getAsJsonObject();
        JsonObject res = ProceduralScene.read(A, "habitantes_e.json").getAsJsonObject("habitantes");
        // posto já aceso (estado diferente do exigido)
        RegionLayout.PointOfInterest p = L.poi("trail_post");
        L.pois.set(L.pois.indexOf(p), new RegionLayout.PointOfInterest(p.id(), p.type(), p.region(), p.x(), p.z(), p.radius(), "sinal_aceso", p.features(), p.spawns()));
        assertNull(ProceduralScene.instantiate(A, L, tpl, res, "sul").quest());
        // nenhum morador com papel de caminhos ou trilhas
        RegionLayout M = RegionGenerator.compose(1024, 0);
        M.npcs.removeIf(n -> n.id().equals("kelyra") || n.id().equals("bramor"));
        assertNull(ProceduralScene.instantiate(A, M, tpl, res, "sul").quest());
        // inimigo de outra variante
        RegionLayout N = RegionGenerator.compose(1024, 0);
        RegionLayout.Spawn sp = N.spawns.getFirst();
        N.spawns.set(0, new RegionLayout.Spawn(sp.key(), "fungo_anciao", sp.x(), sp.z(), sp.yawDeg()));
        assertNull(ProceduralScene.instantiate(A, N, tpl, res, "sul").quest());
    }

    @Test
    void rumoEmPortugues() {
        assertEquals("sul", ProceduralScene.rumo(0, 1));
        assertEquals("norte", ProceduralScene.rumo(0, -1));
        assertEquals("leste", ProceduralScene.rumo(1, 0));
        assertEquals("oeste", ProceduralScene.rumo(-1, 0));
        assertEquals("nordeste", ProceduralScene.rumo(1, -1));
        assertThrows(IllegalArgumentException.class, () -> ProceduralScene.fill("a {b}", java.util.Map.of()));
        assertEquals("trilha do sul", ProceduralScene.fill("trilha do {rumo}", java.util.Map.of("rumo", "sul")));
    }

    // ------------------------------------------------------------------ a aventura jogada em cada semente

    static GameLogic proc(long seed, Path saves) {
        ProceduralScene ps = scene(seed);
        SceneDef s = ps.scene();
        Collision c = Collision.build(Terrain.of(ps.field), Collision.profiles(A), s.handPlaced, s.bodies, s);
        GameLogic g = new GameLogic(s, c);
        g.saveDir = saves.resolve(ps.worldId());
        g.loadChoices(CAT, saves.resolve("sem-aparencias"));
        return g;
    }

    static String talk(GameLogic g, String id, int choice) {
        GameLogic.Npc n = AdventureTest.npc(g, id);
        float a = (float) Math.toRadians(n.def.yawDeg());
        float x = n.def.x() + (float) Math.sin(a) * 1.1f, z = n.def.z() + (float) Math.cos(a) * 1.1f;
        g.player.place(x, z, (float) Math.atan2(n.def.x() - x, n.def.z() - z), g.collision);
        g.player.snapshot();
        for (int i = 0; i < 1200 && g.combat.engaged(); i++) AdventureTest.frames(g, 1);
        AdventureTest.frames(g, 1);
        assertSame(n, g.candidate, "E alcança " + id);
        AdventureTest.press(g, GLFW_KEY_E);
        String ctx = g.dialogContext;
        for (int i = 0; i < 40 && g.state == GameLogic.State.DIALOG; i++) {
            if (g.lastLine() && !g.dialogChoices.isEmpty()) g.choiceSelected = choice;
            g.action("continuar-fala");
        }
        assertEquals(GameLogic.State.EXPLORE, g.state);
        return ctx;
    }

    static void useBeacon(GameLogic g) {
        SceneDef.WorldObject o = g.scene.objects.getFirst();
        float a = (float) Math.toRadians(o.yawDeg());
        float x = o.x() + (float) Math.sin(a) * 1.15f, z = o.z() + (float) Math.cos(a) * 1.15f;
        g.player.place(x, z, (float) Math.atan2(o.x() - x, o.z() - z), g.collision);
        g.player.snapshot();
        AdventureTest.frames(g, 1);
        assertSame(o, g.objectCandidate, "E alcança o marco");
        AdventureTest.press(g, GLFW_KEY_E);
    }

    @Test
    void aAventuraPodeSerConcluidaEmCadaSemente(@TempDir Path saves) {
        for (long s : SEEDS) {
            GameLogic g = proc(s, saves.resolve("s" + s));
            g.action("comecar");
            QuestDefinition q = g.scene.procedural.quest;
            assertEquals("posto", g.combat.encounter);
            assertEquals("oferta", talk(g, q.giver(), 0));
            assertEquals(QuestState.ACEITA, g.quests.get(q.id()).state());
            for (String k : q.objectives().getFirst().targets()) AdventureTest.kill(g, k);
            AdventureTest.frames(g, 30);
            useBeacon(g);
            assertTrue(g.quests.objectOn(q.objectives().get(1).object()));
            assertEquals("entrega", talk(g, q.giver(), 0));
            assertEquals(QuestState.CONCLUIDA, g.quests.get(q.id()).state());
            assertEquals(30, g.quests.coins());
            assertEquals("concluida", talk(g, q.giver(), 0));
            assertEquals(30, g.quests.coins(), "sem segunda recompensa");
            assertTrue(g.saves >= 4, "os marcos salvaram");
        }
    }

    // ------------------------------------------------------------------ save v2

    @Test
    void saveDoMundoProceduralGuardaMundoEVinculos(@TempDir Path saves) throws Exception {
        GameLogic g = proc(4096, saves);
        g.action("comecar");
        QuestDefinition q = g.scene.procedural.quest;
        talk(g, q.giver(), 0);
        AdventureTest.kill(g, q.objectives().getFirst().targets().getFirst());
        AdventureTest.frames(g, 30);
        g.action("salvar");
        assertNull(g.saveError);
        Path f = g.saveDir.resolve(SaveGame.FILE);
        JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        assertEquals(2, o.get("versao").getAsInt());
        assertEquals("4096", o.getAsJsonObject("mundo").get("semente").getAsString());
        assertEquals(RegionGenerator.VERSION, o.getAsJsonObject("mundo").get("versaoGerador").getAsInt());
        assertEquals(g.scene.procedural.binding.toJson(), o.getAsJsonArray("vinculos").get(0));
        assertTrue(f.toString().contains("s4096"), "pasta própria do mundo: " + f);
        // reabrir: a mesma região, o mesmo progresso, os mesmos vínculos
        GameLogic r = proc(4096, saves);
        assertNull(r.continueBlocker(), r.continueBlocker());
        r.action("continuar-jogo");
        assertEquals(1, r.quests.get(q.id()).done(0));
        assertFalse(AdventureTest.enemy(r, q.objectives().getFirst().targets().getFirst()).alive(), "o fungo contado não volta");
        // outro mundo não enxerga este save
        GameLogic other = proc(2048, saves);
        assertEquals("Nenhum jogo salvo ainda", other.continueBlocker());
        // vínculos adulterados (como se o gerador tivesse mudado o lugar): recusado com motivo, original guardado
        String txt = Files.readString(f);
        Files.writeString(f, txt.replace(q.objectives().get(1).object(), "obj.r0.posto_0.outro"));
        GameLogic bad = proc(4096, saves);
        assertTrue(bad.continueBlocker().startsWith("Jogo salvo inválido"), bad.continueBlocker());
        assertTrue(Files.exists(f.resolveSibling(SaveGame.FILE + ".invalido")));
        // versão do gerador diferente: recusado (nada de reposicionar uma missão aceita em silêncio)
        JsonObject v = JsonParser.parseString(txt).getAsJsonObject();
        v.getAsJsonObject("mundo").addProperty("versaoGerador", RegionGenerator.VERSION + 1);
        Files.writeString(f, SaveGame.text(v));
        assertTrue(proc(4096, saves).continueBlocker().contains("versaoGerador"), "motivo cita o gerador");
    }

    @Test
    void saveV1DaRodada10ESaveV2NaoSeConfundem(@TempDir Path saves) throws Exception {
        // v1 (mundo fixo) aberto no procedural: recusado com a explicação
        GameLogic fixed = AdventureTest.started(saves.resolve("fixo"));
        AdventureTest.talk(fixed, "kelyra", 0);                             // salva v1
        String v1 = Files.readString(saves.resolve("fixo").resolve(SaveGame.FILE));
        assertEquals(1, JsonParser.parseString(v1).getAsJsonObject().get("versao").getAsInt());
        GameLogic p = proc(1024, saves);
        Files.createDirectories(p.saveDir);
        Files.writeString(p.saveDir.resolve(SaveGame.FILE), v1);
        p.refreshSaved();
        assertTrue(p.continueBlocker().contains("mundo fixo"), p.continueBlocker());
        // v2 aberto no mundo fixo: recusado
        GameLogic q = proc(1024, saves.resolve("b"));
        q.action("comecar");
        talk(q, q.scene.procedural.quest.giver(), 0);
        String v2 = Files.readString(q.saveDir.resolve(SaveGame.FILE));
        Files.writeString(saves.resolve("fixo").resolve(SaveGame.FILE), v2);
        GameLogic f2 = AdventureTest.adventure(saves.resolve("fixo"));
        assertTrue(f2.continueBlocker().contains("procedural"), f2.continueBlocker());
        // o v1 continua lendo no mundo fixo
        Files.writeString(saves.resolve("fixo").resolve(SaveGame.FILE), v1);
        assertNull(AdventureTest.adventure(saves.resolve("fixo")).continueBlocker());
    }
}
