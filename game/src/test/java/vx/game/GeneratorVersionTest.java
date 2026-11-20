package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.menu.WorldStore;
import vx.game.world.RegionGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 12 (ADR 0029) — versões do gerador convivendo: cada mundo é gerado, para sempre, pela versão com que nasceu. A
 * v1 continua v1 em tudo o que grava; o mundo novo nasce na mais nova; o mundo de um jogo mais novo (versão desconhecida)
 * não abre e o arquivo fica intacto; um save antigo e válido nunca vira {@code .invalido}.
 */
class GeneratorVersionTest {
    static final Path A = ProceduralWorldTest.A;

    @Test
    void aV1ContinuaV1EmTudoQueGrava() {
        ProceduralScene ps = ProceduralWorldTest.scene(1024);
        assertEquals(RegionGenerator.V1, ps.version());
        assertEquals("s1024.g1.r0", ps.layout.key());
        assertTrue(ps.field.id().startsWith("proc.1024.v1."), ps.field.id());
        assertTrue(ps.sceneDoc.getAsJsonObject("jogo").get("_doc").getAsString().contains(RegionGenerator.ID + " v1,"));
        JsonObject m = SaveGame.worldJson(ps);
        assertEquals(1, m.get("versaoGerador").getAsInt(), "o save grava a versão DA REGIÃO");
        assertEquals(RegionGenerator.ID, m.get("gerador").getAsString());
    }

    @Test
    void mundoNovoNasceNaMaisNovaEOutraVersaoSoQuandoPedida(@TempDir Path d) throws Exception {
        WorldStore ws = new WorldStore(d.resolve("mundos"), new Random(3));
        var novo = ws.create("Novo", 1024, 1, s -> { });
        assertEquals(RegionGenerator.LATEST, novo.generatorVersion(), "criado pelo menu: a versão mais nova aprovada");
        var antigo = ws.create("Antigo", 1024, RegionGenerator.V1, 2, s -> { });
        assertEquals(RegionGenerator.V1, antigo.generatorVersion());
        assertNotEquals(novo.id(), antigo.id(), "a mesma semente, dois mundos independentes");
        long before;
        try (Stream<Path> l = Files.list(d.resolve("mundos"))) { before = l.count(); }
        assertThrows(WorldStore.CreateFailed.class, () -> ws.create("Futuro", 1024, 99, 3, s -> { }), "versão desconhecida: não cria");
        try (Stream<Path> l = Files.list(d.resolve("mundos"))) { assertEquals(before, l.count(), "nada gravado"); }
    }

    @Test
    void mundoDeUmJogoMaisNovoNaoAbreENadaETocado(@TempDir Path d) throws Exception {
        WorldStore ws = new WorldStore(d.resolve("mundos"), new Random(4));
        var w = new WorldStore.World("m-0123456789ab", "Do futuro", WorldStore.Kind.PROCEDURAL, 1024, 99, 1L, 0L, null, null);
        ws.save(w);
        Path file = d.resolve("mundos").resolve(w.id()).resolve("mundo.json");
        // uma aventura dele, gravada pelo jogo mais novo
        Path adv = ws.adventureDir(w.id(), "p-111111111111");
        Files.createDirectories(adv);
        JsonObject save = new JsonObject();
        save.addProperty("formato", SaveGame.FORMAT);
        save.addProperty("versao", 2);
        JsonObject mundo = new JsonObject();
        mundo.addProperty("versaoGerador", 99);
        save.add("mundo", mundo);
        Files.writeString(adv.resolve(SaveGame.FILE), save.toString());
        String h1 = MenuStoresTestSupport.sha(file), h2 = MenuStoresTestSupport.sha(adv.resolve(SaveGame.FILE));
        var listed = ws.list(null).stream().filter(x -> x.id().equals(w.id())).findFirst().orElseThrow();
        assertFalse(listed.supported(), "continua na lista, marcado como não suportado");
        assertFalse(ws.get(w.id()).supported());
        assertEquals(99, SaveGame.peekGenerator(adv.resolve(SaveGame.FILE)), "a versão é lida sem validar");
        assertThrows(ProceduralScene.Rejected.class, () -> ProceduralScene.create(A, 1024, 99));
        assertNotNull(WorldStore.progress(adv), "o resumo da aventura só lê");
        assertEquals(h1, MenuStoresTestSupport.sha(file), "o mundo.json não mudou");
        assertEquals(h2, MenuStoresTestSupport.sha(adv.resolve(SaveGame.FILE)), "o save não mudou");
        try (Stream<Path> all = Files.walk(d)) {
            assertTrue(all.noneMatch(p -> p.getFileName().toString().endsWith(".invalido")), "versão desconhecida não é corrupção");
        }
    }

    @Test
    void saveValidoDeRegiaoV1ContinuaValidoENuncaViraInvalido(@TempDir Path d) {
        GameLogic p = ProceduralWorldTest.proc(1024, d);
        p.action("comecar");
        assertTrue(p.save(false), p.saveError);
        Path f = p.saveDir.resolve(SaveGame.FILE);
        int v = SaveGame.peekGenerator(f);
        assertEquals(RegionGenerator.V1, v);
        assertNull(Game.validateSave(A, ProceduralWorldTest.CAT, f, ProceduralScene.create(A, 1024, v)), "a região da versão gravada");
        GameLogic r = ProceduralWorldTest.proc(1024, d);
        assertNull(r.continueBlocker(), r.continueBlocker());
        assertFalse(Files.exists(f.resolveSibling(SaveGame.FILE + ".invalido")));
    }

    @Test
    void saveAntigoDeSementeEAdotadoNumMundoDaVersaoDele(@TempDir Path d) throws Exception {
        GameLogic p = ProceduralWorldTest.proc(2048, d.resolve("proc"));   // rodada 11: save por semente, gerador v1
        p.action("comecar");
        assertTrue(p.save(false), p.saveError);
        var cs = new vx.game.menu.CharacterStore(d.resolve("personagens"), ProceduralWorldTest.CAT, new Random(5));
        var ws = new WorldStore(d.resolve("mundos"), new Random(6));
        var log = Game.adoptOldSaves(A, ProceduralWorldTest.CAT, cs, ws, d.resolve("sem-vila"), d.resolve("proc"), 7);
        assertEquals(1, log.size(), log.toString());
        var w = ws.list(null).stream().filter(x -> !x.legacy() && x.seed() == 2048).findFirst().orElseThrow();
        assertEquals(RegionGenerator.V1, w.generatorVersion(), "o mundo do save antigo é v1, seja qual for a mais nova");
        Path copy = ws.adventureDir(w.id(), cs.list(null).getFirst().id()).resolve(SaveGame.FILE);
        assertNull(Game.validateSave(A, ProceduralWorldTest.CAT, copy, ProceduralScene.create(A, 2048, w.generatorVersion())));
    }

    @Test
    void espiarAVersaoNaoAceitaOQueNaoESaveDeRegiao(@TempDir Path d) throws Exception {
        GameLogic g = AdventureTest.started(d.resolve("v1"));
        AdventureTest.talk(g, "kelyra", 0);                                  // save v1 da vila: sem "mundo"
        assertEquals(-1, SaveGame.peekGenerator(d.resolve("v1").resolve(SaveGame.FILE)));
        Files.writeString(d.resolve("ruim.json"), "{ quebrado");
        assertEquals(-1, SaveGame.peekGenerator(d.resolve("ruim.json")));
        assertEquals(-1, SaveGame.peekGenerator(d.resolve("nao-existe.json")));
        assertTrue(JsonParser.parseString(Files.readString(d.resolve("v1").resolve(SaveGame.FILE))).isJsonObject());
    }
}
