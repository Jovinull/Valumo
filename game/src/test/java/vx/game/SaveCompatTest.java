package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.world.RegionGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 12.2 — o que o leitor faz com cada tipo de save, e que NADA é destruído. Os cinco casos:
 * <ol>
 *   <li>JSON corrompido → recusado com o motivo, original copiado para {@code .invalido};</li>
 *   <li>formato inválido → idem;</li>
 *   <li>versão futura (do formato) → recusado como "de uma versão mais nova", SEM {@code .invalido};</li>
 *   <li>versão antiga válida (v1 da vila, v2 de região) → abre;</li>
 *   <li>mundo de uma versão do gerador ainda em testes cuja região mudou (o mundo "RUINAS": uma descoberta, uma missão,
 *       um alvo, o mundo ou os vínculos que a região de agora não tem) → recusado como "de uma versão de testes", SEM
 *       {@code .invalido}.</li>
 * </ol>
 * Em todos, o arquivo fica byte a byte igual; na vila fixa uma referência desconhecida continua sendo dado inválido (não
 * há geração que possa ter mudado).
 */
class SaveCompatTest {
    static final long SEED = DiscoveryTest.S_TREE;

    /** Um save v2 de verdade (a região {@link #SEED}), editado por {@code edit} e regravado. */
    static Path v2Save(Path dir, Consumer<JsonObject> edit) throws Exception {
        GameLogic g = DiscoveryTest.proc2(SEED, dir);
        g.action("comecar");
        assertTrue(g.save(false), g.saveError);
        Path f = dir.resolve(SaveGame.FILE);
        JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        edit.accept(o);
        Files.writeString(f, SaveGame.text(o));
        return f;
    }

    static Path v1Save(Path dir, Consumer<JsonObject> edit) throws Exception {
        GameLogic g = AdventureTest.started(dir);
        assertTrue(g.save(false), g.saveError);
        Path f = dir.resolve(SaveGame.FILE);
        JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        edit.accept(o);
        Files.writeString(f, SaveGame.text(o));
        return f;
    }

    /** Lê de novo e confere: recusado ou não, com ou sem {@code .invalido}, e o arquivo intacto. */
    static GameLogic reread(GameLogic g, Path f, byte[] before, boolean incompatible, boolean preserved) throws Exception {
        assertNull(g.saved.data(), "recusado");
        assertNotNull(g.saved.problem());
        assertEquals(incompatible, g.saved.incompatible(), g.saved.problem());
        assertEquals(preserved, Files.exists(f.resolveSibling(SaveGame.FILE + ".invalido")), "cópia .invalido: " + g.saved.problem());
        assertArrayEquals(before, Files.readAllBytes(f), "o arquivo ficou como estava");
        return g;
    }

    @Test
    void jsonCorrompidoEFormatoInvalidoVaoParaInvalido(@TempDir Path d) throws Exception {
        Path a = d.resolve("a");
        Files.createDirectories(a);
        Path f = a.resolve(SaveGame.FILE);
        Files.writeString(f, "{ \"formato\": \"valumo.save\", \"versao\": 1, ");     // cortado no meio
        byte[] b = Files.readAllBytes(f);
        GameLogic g = reread(AdventureTest.adventure(a), f, b, false, true);
        assertTrue(g.continueBlocker().startsWith("Jogo salvo inválido: JSON ilegível"), g.continueBlocker());

        Path c = d.resolve("c");
        Files.createDirectories(c);
        Path f2 = c.resolve(SaveGame.FILE);
        Files.writeString(f2, "{\"formato\": \"outra.coisa\", \"versao\": 1}");
        reread(AdventureTest.adventure(c), f2, Files.readAllBytes(f2), false, true);
    }

    @Test
    void savesValidosV1EV2Abrem(@TempDir Path d) throws Exception {
        v1Save(d.resolve("v1"), o -> { });
        GameLogic g1 = AdventureTest.adventure(d.resolve("v1"));
        assertNull(g1.continueBlocker(), g1.continueBlocker());
        assertFalse(g1.saved.incompatible());
        v2Save(d.resolve("v2"), o -> { });
        GameLogic g2 = DiscoveryTest.proc2(SEED, d.resolve("v2"));
        assertNull(g2.continueBlocker(), g2.continueBlocker());
    }

    @Test
    void versaoFuturaDoFormatoFicaIntactaSemInvalido(@TempDir Path d) throws Exception {
        Path f1 = v1Save(d.resolve("v1"), o -> o.addProperty("versao", 7));
        GameLogic g1 = reread(AdventureTest.adventure(d.resolve("v1")), f1, Files.readAllBytes(f1), true, false);
        assertTrue(g1.continueBlocker().startsWith("Esta aventura foi salva por uma versão mais nova do jogo"), g1.continueBlocker());
        Path f2 = v2Save(d.resolve("v2"), o -> o.addProperty("versao", 3));
        reread(DiscoveryTest.proc2(SEED, d.resolve("v2")), f2, Files.readAllBytes(f2), true, false);
        // versão absurda (não inteira, negativa) não é "mais nova": é dado inválido
        Path f3 = v1Save(d.resolve("v3"), o -> o.addProperty("versao", 1.5));
        reread(AdventureTest.adventure(d.resolve("v3")), f3, Files.readAllBytes(f3), false, true);
    }

    @Test
    void mundoDeVersaoDeTestesQueMudouNaoECorrompido(@TempDir Path d) throws Exception {
        assertFalse(RegionGenerator.published(RegionGenerator.V2), "a v2 ainda não foi publicada (rodada 12.2)");
        record Case(String name, Consumer<JsonObject> edit) { }
        Case[] cases = {
                new Case("descoberta", o -> {
                    JsonArray a = new JsonArray();
                    a.add("desc.r0.ruina.0");                              // o caso do mundo RUINAS (a casa virou torre)
                    o.getAsJsonObject("progresso").add("descobertas", a);
                }),
                new Case("tentativa", o -> o.getAsJsonObject("mundo").addProperty("tentativa", 3)),
                new Case("vinculos", o -> o.add("vinculos", new JsonArray())),
                new Case("missao", o -> {
                    JsonObject ms = o.getAsJsonObject("progresso").getAsJsonObject("missoes");
                    String id = ms.keySet().iterator().next();
                    ms.add("quest.instance.s" + SEED + ".r0.outra_0.1", ms.remove(id));
                }),
        };
        for (Case k : cases) {
            Path dir = d.resolve(k.name());
            Path f = v2Save(dir, k.edit());
            GameLogic g = reread(DiscoveryTest.proc2(SEED, dir), f, Files.readAllBytes(f), true, false);
            String why = g.continueBlocker();
            assertTrue(why.startsWith("Esta aventura é de uma versão de testes deste mundo"), k.name() + ": " + why);
            assertTrue(why.contains("crie outro mundo"), why);
            // entrar de novo não grava nada por cima: a aventura não começa e o arquivo continua igual
            byte[] b = Files.readAllBytes(f);
            assertNotNull(g.beginAdventure(vx.viewer.Appearance.preset("aventureiro"), "Teste", true), k.name());
            assertArrayEquals(b, Files.readAllBytes(f), k.name() + ": nada gravado");
        }
    }

    @Test
    void mundoDeVersaoPublicadaQueNaoBateNaoECorrompidoNemDeTestes(@TempDir Path d) throws Exception {
        // a v1 procedural é publicada: um save dela que não bate com a região é "não corresponde", nunca "versão de testes"
        ProceduralScene ps = ProceduralWorldTest.scene(1024);
        assertTrue(RegionGenerator.published(ps.version()));
        SaveGame.Incompatible e = SaveGame.otherRegion(ps, "teste");
        assertTrue(e.getMessage().startsWith("Esta aventura não corresponde a este mundo"), e.getMessage());
        assertEquals("teste", e.detail);
    }

    @Test
    void naVilaFixaReferenciaDesconhecidaContinuaInvalida(@TempDir Path d) throws Exception {
        Path f = v1Save(d.resolve("a"), o -> {
            JsonArray a = new JsonArray();
            a.add("obj.que.nao.existe");
            o.getAsJsonObject("progresso").add("objetosAtivos", a);
        });
        reread(AdventureTest.adventure(d.resolve("a")), f, Files.readAllBytes(f), false, true);
    }

    @Test
    void oMesmoPersonagemEmOutroMundoNaoEAfetado(@TempDir Path d) throws Exception {
        // o mesmo personagem tem duas aventuras (pastas do par): a de um mundo de testes mudado e a de um mundo bom
        Path bad = v2Save(d.resolve("mundo-a").resolve("p"), o -> o.getAsJsonObject("mundo").addProperty("tentativa", 2));
        v2Save(d.resolve("mundo-b").resolve("p"), o -> { });
        byte[] b = Files.readAllBytes(bad);
        GameLogic a = DiscoveryTest.proc2(SEED, d.resolve("mundo-a").resolve("p"));
        assertTrue(a.saved.incompatible());
        GameLogic ok = DiscoveryTest.proc2(SEED, d.resolve("mundo-b").resolve("p"));
        assertNull(ok.continueBlocker(), ok.continueBlocker());
        ok.action("continuar-jogo");
        assertEquals(GameLogic.State.EXPLORE, ok.state);
        assertTrue(ok.save(false), ok.saveError);
        assertArrayEquals(b, Files.readAllBytes(bad), "a outra aventura ficou como estava");
        assertFalse(Files.exists(bad.resolveSibling(SaveGame.FILE + ".invalido")));
    }
}
