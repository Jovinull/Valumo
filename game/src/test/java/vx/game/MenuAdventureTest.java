package vx.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.menu.WorldStore;
import vx.viewer.Appearance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 11.1 — a aventura aberta pelo menu (lógica, sem janela): começar e continuar com a aparência do PERSONAGEM, a
 * confirmação antes de sair (nada se perde em silêncio), e a compatibilidade dos saves v1 (vila) e v2 (semente) trazidos
 * para o catálogo: válidos no mundo certo, recusados no errado, recompensa sem duplicar.
 */
class MenuAdventureTest {
    static final Path A = AdventureTest.A;

    @Test
    void comecarEContinuarComAAparenciaDoPersonagem(@TempDir Path d) throws Exception {
        Appearance look = Appearance.preset("maga");
        GameLogic g = AdventureTest.adventure(d);
        assertNull(g.beginAdventure(look, "Brisa", false));
        assertEquals(GameLogic.State.EXPLORE, g.state);
        assertTrue(g.menuAvailable);
        assertEquals(look.signature(), g.appearance.signature());
        AdventureTest.talk(g, "kelyra", 0);                                // aceita a missão (salva sozinho no marco)
        assertTrue(g.quests.get(QuestTest.Q).accepted());
        assertTrue(Files.exists(d.resolve(SaveGame.FILE)));
        // continuar: o progresso vem do save; a aparência vem do personagem (que pode ter sido editado depois)
        Appearance edited = Appearance.preset("exploradora");
        GameLogic h = AdventureTest.adventure(d);
        assertNull(h.beginAdventure(edited, "Brisa", true));
        assertTrue(h.quests.get(QuestTest.Q).accepted(), "progresso continuado");
        assertEquals(edited.signature(), h.appearance.signature(), "aparência do perfil, não a do save");
        // save inválido: motivo, nada muda
        Files.writeString(d.resolve(SaveGame.FILE), "{ quebrado");
        GameLogic k = AdventureTest.adventure(d);
        String why = k.beginAdventure(edited, "Brisa", true);
        assertNotNull(why);
        assertFalse(k.started);
    }

    /** O herói anda 1 u (o progresso que um save novo mudaria). */
    static void walk(GameLogic g) {
        g.player.place(g.player.x + 1f, g.player.z, g.player.yaw, g.collision);
        g.player.snapshot();
        AdventureTest.frames(g, 2);
    }

    @Test
    void sairPedeConfirmacao(@TempDir Path d) {
        GameLogic g = AdventureTest.adventure(d);
        g.beginAdventure(Appearance.preset("aventureiro"), "A", false);
        g.action("pausar");
        g.action("menu-principal");
        assertEquals("menu", g.leaving, "numa aventura, sair sempre pergunta (revisto no teste físico: só às vezes parecia aleatório)");
        assertFalse(g.unsaved(), "e o texto diz que tudo está salvo");
        g.action("menu-cancelar");
        g.action("continuar");
        AdventureTest.frames(g, 60);
        assertFalse(g.unsaved(), "parado: tempo de jogo sozinho não é progresso a perder");
        walk(g);
        assertTrue(g.unsaved(), "andou: a posição nova ainda não está no disco");
        g.action("pausar");
        g.action("sair");
        assertFalse(g.quit, "na aventura do menu, Sair pergunta antes");
        assertEquals("sair", g.leaving);
        AdventureTest.press(g, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertNull(g.leaving, "Esc cancela a confirmação");
        assertEquals(GameLogic.State.PAUSE, g.state);
        g.action("menu-principal");
        assertEquals("menu", g.leaving);
        g.action("menu-salvar");
        assertTrue(g.wantMenu, "salvou e pediu o menu");
        assertTrue(Files.exists(d.resolve(SaveGame.FILE)));
        g.wantMenu = false;
        assertFalse(g.unsaved(), "acabou de salvar");
        g.action("continuar");
        walk(g);
        g.action("pausar");
        g.action("sair");
        g.action("menu-sem-salvar");
        assertTrue(g.quit);
        // fora do menu (roteiros antigos, -Dvx.menu=nao): Sair continua direto
        GameLogic old = AdventureTest.started(d.resolve("x"));
        old.action("pausar");
        old.action("menu-principal");
        assertNull(old.leaving, "sem menu, não há Menu principal");
        old.action("sair");
        assertTrue(old.quit);
    }

    @Test
    void xDaJanelaConfirmaDeQualquerEstadoECancelarVoltaAoQueEra(@TempDir Path d) throws Exception {
        GameLogic g = AdventureTest.adventure(d);
        g.requestQuit();
        assertTrue(g.quit, "na escolha, antes de começar: nada a perder, fecha");
        g = AdventureTest.adventure(d);
        g.beginAdventure(Appearance.preset("aventureiro"), "A", false);
        AdventureTest.frames(g, 120);
        g.requestQuit();
        assertFalse(g.quit, "numa aventura o X sempre pergunta");
        assertEquals("sair", g.leaving);
        assertFalse(g.unsaved(), "parado: a pergunta só oferece Sair e Cancelar");
        g.action("menu-sem-salvar");
        assertTrue(g.quit);
        g = AdventureTest.adventure(d);
        g.beginAdventure(Appearance.preset("aventureiro"), "A", false);
        AdventureTest.frames(g, 60);
        AdventureTest.talk(g, "kelyra", 0);                                // aceitar a missão salva sozinho (marco)
        assertTrue(Files.exists(d.resolve(SaveGame.FILE)));
        String autosave = Files.readString(d.resolve(SaveGame.FILE));
        assertFalse(g.unsaved(), "o salvamento automático do marco conta como salvo");
        walk(g);
        // em diálogo: o X abre a confirmação; cancelar volta AO DIÁLOGO, na mesma fala
        GameLogic.Npc k = AdventureTest.npc(g, "kelyra");
        g.startDialog(k);
        int line = g.line;
        g.requestQuit();
        assertFalse(g.quit);
        assertEquals(GameLogic.State.PAUSE, g.state);
        assertEquals("sair", g.leaving);
        g.action("menu-cancelar");
        assertEquals(GameLogic.State.DIALOG, g.state, "cancelar volta ao diálogo");
        assertEquals(line, g.line);
        assertNull(g.leaving);
        g.endDialog();
        // explorando: cancelar volta a explorar; sair sem salvar não mexe no que já estava salvo
        g.requestQuit();
        g.action("menu-cancelar");
        assertEquals(GameLogic.State.EXPLORE, g.state);
        g.requestQuit();
        g.action("menu-sem-salvar");
        assertTrue(g.quit);
        assertEquals(autosave, Files.readString(d.resolve(SaveGame.FILE)), "sair sem salvar não desfaz o salvamento automático");
        // salvar e sair: grava uma vez
        GameLogic h = AdventureTest.adventure(d);
        assertNull(h.beginAdventure(Appearance.preset("aventureiro"), "A", true));
        assertFalse(h.unsaved(), "acabou de continuar");
        walk(h);
        int before = h.saves;
        h.requestQuit();
        h.action("menu-salvar");
        assertTrue(h.quit);
        assertEquals(before + 1, h.saves, "uma gravação");
    }

    @Test
    void falhaDeGravacaoNaoSaiNemPerdeNada(@TempDir Path d) throws Exception {
        Path notADir = d.resolve("arquivo");
        Files.writeString(notADir, "x");                                   // a "pasta" do save é um arquivo: gravar falha
        GameLogic g = AdventureTest.adventure(notADir);
        g.beginAdventure(Appearance.preset("aventureiro"), "A", false);
        walk(g);
        g.requestQuit();
        g.action("menu-salvar");
        assertFalse(g.quit, "não sai se o salvamento falhou");
        assertEquals("sair", g.leaving, "a confirmação continua aberta (pode sair sem salvar ou cancelar)");
        assertNotNull(g.saveError);
        assertTrue(g.message.startsWith("Não consegui salvar"), g.message);
        assertEquals("x", Files.readString(notADir), "nada escrito por cima");
        try (var st = Files.list(d)) { assertEquals(1, st.count(), "nenhum temporário largado"); }
        g.action("menu-cancelar");
        assertEquals(GameLogic.State.EXPLORE, g.state);
    }

    @Test
    void salvarEVoltarRespeitaOBloqueio(@TempDir Path d) {
        GameLogic g = AdventureTest.adventure(d);
        g.beginAdventure(Appearance.preset("aventureiro"), "A", false);
        walk(g);
        g.player.grounded = false;                                         // no ar: não dá para salvar
        g.action("pausar");
        g.action("menu-principal");
        g.action("menu-salvar");
        assertFalse(g.wantMenu, "não volta sem salvar quando pediu para salvar");
        assertEquals("menu", g.leaving, "a confirmação continua aberta");
        assertFalse(Files.exists(d.resolve(SaveGame.FILE)));
    }

    @Test
    void jogosSalvosDeAntesViramAventurasUmaVez(@TempDir Path d) throws Exception {
        var cat = AdventureTest.CAT;
        GameLogic g = AdventureTest.started(d.resolve("saves"));            // v1 da vila, com a missão aceita
        AdventureTest.talk(g, "kelyra", 0);
        GameLogic p = ProceduralWorldTest.proc(1024, d.resolve("proc"));   // v2 da semente 1024
        p.action("comecar");
        assertTrue(p.save(false), p.saveError);
        java.nio.file.Files.createDirectories(d.resolve("proc/s7"));       // um ruim: fica de fora, registrado
        java.nio.file.Files.writeString(d.resolve("proc/s7").resolve(SaveGame.FILE), "{ quebrado");
        String h1 = MenuStoresTestSupport.sha(d.resolve("saves").resolve(SaveGame.FILE));
        var cs = new vx.game.menu.CharacterStore(d.resolve("personagens"), cat, new Random(1));
        var ws = new WorldStore(d.resolve("mundos"), new Random(2));
        var log = Game.adoptOldSaves(A, cat, cs, ws, d.resolve("saves"), d.resolve("proc"), 5);
        assertEquals(3, log.size(), log.toString());
        assertEquals(1, cs.list(null).size(), "a mesma aparência nos dois saves: um personagem só");
        var hero = cs.list(null).getFirst();
        assertEquals("Aventureiro", hero.name(), "nome do modelo da aparência gravada");
        assertTrue(ws.hasAdventure(WorldStore.LEGACY_ID, hero.id()), "a vila continua de onde estava");
        var seeded = ws.list(null).stream().filter(w -> !w.legacy() && w.seed() == 1024).findFirst().orElseThrow();
        assertEquals("Mundo 1024", seeded.name());
        assertTrue(ws.hasAdventure(seeded.id(), hero.id()));
        assertEquals(h1, MenuStoresTestSupport.sha(d.resolve("saves").resolve(SaveGame.FILE)), "o original não muda");
        assertTrue(ws.oldSaves(d.resolve("saves"), d.resolve("proc")).isEmpty(), "nada é oferecido de novo (nem o recusado)");
        assertTrue(Game.adoptOldSaves(A, cat, cs, ws, d.resolve("saves"), d.resolve("proc"), 6).isEmpty(), "abrir de novo não duplica");
        GameLogic c = AdventureTest.adventure(ws.adventureDir(WorldStore.LEGACY_ID, hero.id()));
        assertNull(c.beginAdventure(hero.appearance(), hero.name(), true));
        assertTrue(c.quests.get(QuestTest.Q).accepted(), "o progresso veio junto");
    }

    @Test
    void savesAntigosValemNoMundoCerto(@TempDir Path d) throws Exception {
        var cat = AdventureTest.CAT;
        // v1 da vila (rodada 10)
        GameLogic g = AdventureTest.started(d.resolve("v1"));
        AdventureTest.talk(g, "kelyra", 0);
        Path v1 = d.resolve("v1").resolve(SaveGame.FILE);
        assertNull(Game.validateSave(A, cat, v1, null), "v1 vale na vila");
        // v2 da semente 1024 (rodada 11)
        ProceduralScene ps = ProceduralWorldTest.scene(1024);
        GameLogic p = ProceduralWorldTest.proc(1024, d.resolve("v2"));
        p.action("comecar");
        assertTrue(p.save(false), p.saveError);
        Path v2 = p.saveDir.resolve(SaveGame.FILE);
        assertNull(Game.validateSave(A, cat, v2, ps), "v2 vale na região da mesma semente");
        assertNotNull(Game.validateSave(A, cat, v2, null), "v2 na vila: recusado");
        assertNotNull(Game.validateSave(A, cat, v1, ps), "v1 numa região gerada: recusado");
        assertNotNull(Game.validateSave(A, cat, v2, ProceduralWorldTest.scene(2048)), "v2 de outra semente: recusado");
        // trazidos para o catálogo: cópia válida, original intacto, recompensa uma vez só
        WorldStore ws = new WorldStore(d.resolve("mundos"), new Random(1));
        Path legacyDir = d.resolve("v1"), procRoot = d.resolve("v2");
        var olds = ws.oldSaves(legacyDir, procRoot);
        assertEquals(2, olds.size(), olds.toString());
        String before = Files.readString(v1);
        var o1 = olds.stream().filter(o -> o.kind() == WorldStore.Kind.LEGADO).findFirst().orElseThrow();
        ws.importSave(o1, WorldStore.LEGACY_ID, "p-111111111111", 1, f -> Game.validateSave(A, cat, f, null));
        assertEquals(before, Files.readString(v1));
        var o2 = olds.stream().filter(o -> o.kind() == WorldStore.Kind.PROCEDURAL).findFirst().orElseThrow();
        assertEquals(1024L, o2.seed());
        var w = ws.create("Semente 1024", 1024, 2, s -> { });
        ws.importSave(o2, w.id(), "p-111111111111", 3, f -> Game.validateSave(A, cat, f, ps));
        // o trazido continua de onde estava
        GameLogic c = AdventureTest.adventure(ws.adventureDir(WorldStore.LEGACY_ID, "p-111111111111"));
        assertNull(c.beginAdventure(Appearance.preset("maga"), "A", true));
        assertTrue(c.quests.get(QuestTest.Q).accepted());
        assertTrue(ws.oldSaves(legacyDir, procRoot).isEmpty(), "os dois já foram trazidos");
    }
}
