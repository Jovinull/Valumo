package vx.game.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Rodada 11.1 — navegação do menu sem OpenGL: telas, Esc, seleção preservada, campos de texto e pedidos. */
class MenuFlowTest {
    static final ComponentCatalog CAT = MenuStoresTest.CAT;

    static MenuFlow flow(Path d) {
        return new MenuFlow(new CharacterStore(d.resolve("p"), CAT, new Random(1)), new WorldStore(d.resolve("m"), new Random(2)));
    }

    @Test
    void navegacaoComEscPreservaEscolhas(@TempDir Path d) throws Exception {
        MenuFlow f = flow(d);
        var p1 = f.characters.create("A", Appearance.preset("maga"), 1);
        var p2 = f.characters.create("B", Appearance.preset("aventureiro"), 2);
        var w = f.worlds.create("Mundo", 77, 3, s -> { });
        f.key(GLFW_KEY_ENTER);                                             // foco em "Jogar"
        assertEquals(MenuFlow.Screen.CHARACTERS, f.screen);
        f.action("personagem:1");
        String chosen = f.profile().id();
        f.action("escolher-personagem");
        assertEquals(MenuFlow.Screen.WORLDS, f.screen);
        f.action("mundo:0");
        assertEquals(w.id(), f.world().id());
        f.key(GLFW_KEY_ESCAPE);
        assertEquals(MenuFlow.Screen.CHARACTERS, f.screen);
        assertEquals(chosen, f.profile().id(), "voltar preserva o personagem escolhido");
        f.action("escolher-personagem");
        assertEquals(w.id(), f.world().id(), "e o mundo escolhido");
        f.key(GLFW_KEY_ESCAPE);
        f.key(GLFW_KEY_ESCAPE);
        assertEquals(MenuFlow.Screen.MAIN, f.screen);
        f.key(GLFW_KEY_ESCAPE);
        assertEquals(MenuFlow.Screen.MAIN, f.screen, "Esc no menu principal não sai do jogo");
        assertNull(f.take());
        assertNotNull(p1);
        assertNotNull(p2);
    }

    @Test
    void escolherSemPersonagemAvisa(@TempDir Path d) {
        MenuFlow f = flow(d);
        f.action("jogar");
        f.action("escolher-personagem");
        assertEquals(MenuFlow.Screen.CHARACTERS, f.screen);
        assertNotNull(f.message);
        f.action("editar-personagem");
        assertNull(f.take(), "editar sem personagem não pede nada");
    }

    @Test
    void formularioDeMundoCamposESemente(@TempDir Path d) throws Exception {
        MenuFlow f = flow(d);
        f.characters.create("A", Appearance.preset("maga"), 1);
        f.action("jogar");
        f.action("escolher-personagem");
        f.key(GLFW_KEY_N);
        assertEquals(MenuFlow.Screen.CREATE_WORLD, f.screen);
        assertTrue(f.typing(), "campo com foco: teclas de jogo não valem");
        for (char c : "Vale N".toCharArray()) f.chr(c);
        f.key(GLFW_KEY_N);                                                 // N digitado não abre outra coisa
        assertEquals(MenuFlow.Screen.CREATE_WORLD, f.screen);
        f.key(GLFW_KEY_TAB);
        for (char c : "12x3".toCharArray()) f.chr(c);
        assertEquals("Vale N", f.nameField.toString());
        assertEquals("123", f.seedField.toString(), "semente só aceita algarismos");
        f.key(GLFW_KEY_BACKSPACE);
        assertEquals("12", f.seedField.toString());
        f.key(GLFW_KEY_ENTER);
        var r = f.take();
        assertEquals(MenuFlow.RequestKind.CREATE_WORLD, r.kind());
        assertEquals(12L, r.seed());
        assertEquals("Vale N", r.name());
        f.failed(r, "não deu");
        assertEquals(MenuFlow.Screen.CREATE_WORLD, f.screen);
        assertEquals("não deu", f.error);
        assertEquals("Vale N", f.nameField.toString(), "falha preserva o nome");
        assertEquals("12", f.seedField.toString(), "e a semente");
        // vazia: sorteia uma vez e MOSTRA no campo antes de pedir
        f.seedField.setLength(0);
        f.action("confirmar-mundo");
        var r2 = f.take();
        assertEquals(Long.toUnsignedString(r2.seed()), f.seedField.toString());
        var made = f.worlds.create(r2.name(), r2.seed(), 5, s -> { });
        f.done(r2, made.id());
        assertEquals(MenuFlow.Screen.WORLDS, f.screen);
        assertEquals(made.id(), f.world().id(), "o mundo novo fica escolhido");
        f.key(GLFW_KEY_N);
        f.key(GLFW_KEY_ESCAPE);
        assertEquals(MenuFlow.Screen.WORLDS, f.screen, "Esc cancela o formulário");
    }

    @Test
    void entrarPedeAventuraDoPar(@TempDir Path d) throws Exception {
        MenuFlow f = flow(d);
        f.characters.create("A", Appearance.preset("maga"), 1);
        f.action("jogar");
        f.action("escolher-personagem");
        assertTrue(f.world().legacy(), "sem mundos criados, a vila fixa continua acessível");
        f.key(GLFW_KEY_ENTER);
        var r = f.take();
        assertEquals(MenuFlow.RequestKind.ENTER, r.kind());
        assertEquals(f.worlds.adventureDir(WorldStore.LEGACY_ID, r.profile().id()), f.adventureDir());
        assertFalse(f.hasAdventure());
    }
}
