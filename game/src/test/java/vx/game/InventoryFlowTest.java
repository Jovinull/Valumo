package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.item.Inventory;
import vx.game.item.ItemProgress;
import vx.game.item.LootTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static vx.game.AdventureTest.frames;
import static vx.game.AdventureTest.press;

/**
 * Rodada 13 — a mochila no jogo de verdade (GameLogic, sem GL): o presente da Índira e os achados das descobertas, uma vez
 * cada, tudo ou nada; a cataplasma na vida do herói; salvar, continuar e salvar de novo; saves antigos sem os campos novos;
 * recusas certas; aventuras isoladas. Rodada 13.1: a mochila SOBRE o jogo correndo (anda, o tempo passa, o fungo ataca, o
 * salvamento automático acontece), o seletor pelo mouse, clique que usa, abas, setas × WASD, o que a fecha, sem limite de
 * espaços (o "cheia" só no teto técnico).
 */
class InventoryFlowTest {
    static final long S_INDIRA = 7, S_FARM = 23;                         // 7: Índira, casa e árvore; 23: sítio e árvore

    static GameLogic proc(long seed, Path dir) {
        GameLogic g = DiscoveryTest.proc2(seed, dir);
        g.action("comecar");
        return g;
    }

    /** Diante da Índira, E, todas as falas até o fim (o presente sai no fim da conversa). */
    static void talkToIndira(GameLogic g) {
        GameLogic.Npc n = AdventureTest.npc(g, "indira");
        float a = (float) Math.toRadians(n.def.yawDeg());
        float x = n.def.x() + (float) Math.sin(a) * 1.1f, z = n.def.z() + (float) Math.cos(a) * 1.1f;
        g.player.place(x, z, (float) Math.atan2(n.def.x() - x, n.def.z() - z), g.collision);
        g.player.snapshot();
        frames(g, 1);
        assertSame(n, g.candidate, "E alcança a Índira");
        press(g, GLFW_KEY_E);
        assertEquals(GameLogic.State.DIALOG, g.state);
        for (int i = 0; i < 40 && g.state == GameLogic.State.DIALOG; i++) g.action("continuar-fala");
        assertEquals(GameLogic.State.EXPLORE, g.state);
    }

    /** Mochila com teto pequeno (o único "cheia" que existe é o teto técnico). */
    static void limit(GameLogic g, int stacks) { g.items = ItemProgress.of(Inventory.limited(stacks)); }

    static void fill(GameLogic g, int slots) {
        var reg = g.itemRules.catalog().get("item.registro_viagem");
        for (int i = 0; i < slots; i++) assertTrue(g.items.inventory.add(reg, 1));
    }

    // ------------------------------------------------------------------ a tela (rodada 13.1: sobre o jogo correndo)

    static vx.engine.Window.InputEvent ev(vx.engine.Window.InputEvent.Kind k, float x, int code) {
        return new vx.engine.Window.InputEvent(k, x, 0, 0, 0, code);
    }

    @Test
    void aMochilaAbreSobreOJogoQueContinuaCorrendo(@TempDir Path d) {
        GameLogic g = AdventureTest.started(d);
        frames(g, 5);
        press(g, GLFW_KEY_I);
        assertTrue(g.bagOpen, "I abre");
        assertEquals(GameLogic.State.EXPLORE, g.state, "a mochila não é outra tela: o jogo continua em exploração");
        float x0 = g.player.x, z0 = g.player.z, yaw0 = g.cam.yaw;
        double t0 = g.playTime;
        Input in = AdventureTest.IN;
        in.feed(List.of(ev(vx.engine.Window.InputEvent.Kind.KEY, 0, GLFW_KEY_W), ev(vx.engine.Window.InputEvent.Kind.MOVE, 100, 0)));
        g.update(1 / 60f, in);
        for (int i = 0; i < 60; i++) {                                     // W segurado, o mouse andando e o botão esquerdo
            in.feed(List.of(ev(vx.engine.Window.InputEvent.Kind.MOVE, 100 + 9 * i, 0)));
            in.leftPressed = 1;
            g.update(1 / 60f, in);
        }
        assertTrue(Math.hypot(g.player.x - x0, g.player.z - z0) > 0.5, "o herói anda com a mochila aberta");
        assertTrue(g.playTime - t0 > 0.9, "o tempo do jogo corre");
        assertEquals(yaw0, g.cam.yaw, 1e-4f, "o mouse é da mochila: a câmera não gira");
        assertNull(g.combat.hero.attack, "clique na mochila não vira golpe");
        in.feed(List.of(ev(vx.engine.Window.InputEvent.Kind.KEY_UP, 0, GLFW_KEY_W)));
        in.leftPressed = 0;
        g.update(1 / 60f, in);
        press(g, GLFW_KEY_ESCAPE);
        assertFalse(g.bagOpen, "Esc fecha a mochila");
        assertEquals(GameLogic.State.EXPLORE, g.state, "e não abre a pausa");
        float x1 = g.player.x;
        frames(g, 30);
        assertNull(g.combat.hero.attack, "fechar não dispara golpe");
        assertEquals(x1, g.player.x, 0.05f, "W solto: nada preso");
        press(g, GLFW_KEY_I);
        assertTrue(g.bagOpen);
        press(g, GLFW_KEY_I);
        assertFalse(g.bagOpen, "I fecha também");
        press(g, GLFW_KEY_ESCAPE);
        assertEquals(GameLogic.State.PAUSE, g.state, "com a mochila fechada, Esc pausa como sempre");
    }

    @Test
    void comAMochilaAbertaODefenderEDoMouseEOPuloEDoTeclado(@TempDir Path d) {
        GameLogic g = AdventureTest.started(d);
        frames(g, 5);
        g.action("inventario");
        Input in = AdventureTest.IN;
        in.feed(List.of(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.DOWN, 0, 0, 1, 0, 0)));
        for (int i = 0; i < 40; i++) { g.update(1 / 60f, in); in.feed(List.of()); in.rightDown = true; }
        assertFalse(g.combat.hero.guardUp(), "o botão direito é da mochila: o escudo não sobe");
        in.rightDown = false;
        float y0 = g.player.y;
        press(g, GLFW_KEY_SPACE);
        boolean up = false;
        for (int i = 0; i < 20; i++) { frames(g, 1); up |= g.player.y > y0 + 0.05f; }
        assertTrue(up, "Espaço pula com a mochila aberta (não usa item)");
        assertTrue(g.bagOpen);
    }

    @Test
    void asSetasAndamPelaGradeOWasdAndaComOHeroiEEnterUsa(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        fill(g, 9);                                                        // 2 pilhas do presente + 9 cadernos = 11
        press(g, GLFW_KEY_I);
        assertTrue(g.bagOpen);
        assertEquals(0, g.invSelected, "abre na primeira pilha");
        press(g, GLFW_KEY_RIGHT);
        assertEquals(1, g.invSelected);
        press(g, GLFW_KEY_DOWN);
        assertEquals(1 + GameLogic.INV_COLS, g.invSelected);
        press(g, GLFW_KEY_DOWN);
        assertEquals(10, g.invSelected, "abaixo do fim: a última pilha (sem dar a volta)");
        press(g, GLFW_KEY_UP);
        press(g, GLFW_KEY_UP);
        press(g, GLFW_KEY_LEFT);
        assertEquals(0, g.invSelected);
        press(g, GLFW_KEY_LEFT);
        assertEquals(0, g.invSelected, "no começo, fica");
        float x0 = g.player.x, z0 = g.player.z;
        for (int k : new int[]{GLFW_KEY_RIGHT, GLFW_KEY_LEFT}) press(g, k);
        assertEquals(x0, g.player.x, 1e-3f, "as setas não andam com o herói");
        assertEquals(z0, g.player.z, 1e-3f);
        Input in = AdventureTest.IN;
        in.feed(List.of(ev(vx.engine.Window.InputEvent.Kind.KEY, 0, GLFW_KEY_D)));
        for (int i = 0; i < 30; i++) { g.update(1 / 60f, in); in.feed(List.of()); }
        in.feed(List.of(ev(vx.engine.Window.InputEvent.Kind.KEY_UP, 0, GLFW_KEY_D)));
        g.update(1 / 60f, in);
        assertTrue(Math.hypot(g.player.x - x0, g.player.z - z0) > 0.2, "WASD anda com o herói");
        assertEquals(0, g.invSelected, "e não mexe na seleção");
        press(g, GLFW_KEY_RIGHT);                                           // a cataplasma
        g.combat.hero.hp = 2;
        press(g, GLFW_KEY_ENTER);
        assertEquals(4, g.combat.hero.hp, "Enter usa");
        assertTrue(g.bagOpen, "e a mochila continua aberta");
    }

    @Test
    void oSeletorSegueOMouseEClicarUsaOQueSeUsa(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        g.action("inventario");
        g.action("pairar:1");
        assertEquals(1, g.invSelected, "passar o mouse seleciona");
        g.action("pairar:0");
        assertEquals(0, g.invSelected);
        g.action("pairar:57");
        assertEquals(0, g.invSelected, "pilha que não existe: nada");
        g.combat.hero.hp = 2;
        g.action("clique:0");
        assertEquals(2, g.items.inventory.count("item.ervas_trilha"), "clicar no material só seleciona");
        assertEquals(2, g.combat.hero.hp);
        g.action("clique:1");
        assertEquals(4, g.combat.hero.hp, "clicar na cataplasma aplica");
        assertEquals(1, g.items.inventory.count("item.cataplasma"));
        g.action("usar:1");
        assertEquals(6, g.combat.hero.hp, "o clique direito também");
        assertEquals(0, g.items.inventory.count("item.cataplasma"));
        assertEquals(0, g.invSelected, "a pilha que acabou sai; a seleção fica na vizinha");
        g.action("usar:0");
        assertEquals(2, g.items.inventory.count("item.ervas_trilha"), "material não se usa");
        g.action("fechar-inventario");
        g.combat.hero.hp = 2;
        g.action("pairar:0");
        g.action("clique:0");
        g.action("usar:0");
        assertEquals(2, g.combat.hero.hp, "mochila fechada: nada");
    }

    @Test
    void asAbasFiltramPorCategoria(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        g.useDiscovery(DiscoveryTest.approach(g, "desc.r0.ruina.0"));     // + o caderno
        g.action("inventario");
        assertEquals(List.of("", "material", "achado", "consumivel", "curiosidade"), g.bagTabs(), "Tudo e as categorias do catálogo");
        assertEquals(List.of(0, 1, 2), g.bagView());
        g.action("aba:consumivel");
        assertEquals(List.of(1), g.bagView());
        assertEquals(1, g.invSelected, "a seleção vai para a aba");
        g.action("pairar:0");
        assertEquals(1, g.invSelected, "pilha de outra aba: não seleciona");
        g.action("aba:achado");
        assertTrue(g.bagView().isEmpty());
        assertEquals(-1, g.invSelected, "aba vazia: nada selecionado");
        g.action("usar-item");
        assertEquals(1, g.items.inventory.count("item.registro_viagem"), "e usar não faz nada");
        g.action("aba:lendario");
        assertEquals("achado", g.bagTab, "aba que não existe: nada");
        g.action("aba:");
        assertEquals(3, g.bagView().size());
    }

    @Test
    void conversaDiarioPausaEDerrotaFechamAMochila(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        GameLogic.Npc n = AdventureTest.npc(g, "indira");
        float a = (float) Math.toRadians(n.def.yawDeg());
        float x = n.def.x() + (float) Math.sin(a) * 1.1f, z = n.def.z() + (float) Math.cos(a) * 1.1f;
        g.player.place(x, z, (float) Math.atan2(n.def.x() - x, n.def.z() - z), g.collision);
        g.player.snapshot();
        frames(g, 1);
        press(g, GLFW_KEY_I);
        assertTrue(g.bagOpen);
        press(g, GLFW_KEY_E);
        assertEquals(GameLogic.State.DIALOG, g.state, "E conversa com a mochila aberta");
        assertFalse(g.bagOpen, "e a conversa fecha a mochila");
        for (int i = 0; i < 40 && g.state == GameLogic.State.DIALOG; i++) g.action("continuar-fala");
        g.action("inventario");
        press(g, GLFW_KEY_J);
        assertEquals(GameLogic.State.JOURNAL, g.state);
        assertFalse(g.bagOpen, "o diário fecha a mochila");
        g.action("fechar-diario");
        g.action("inventario");
        g.action("pausar");
        assertEquals(GameLogic.State.PAUSE, g.state);
        assertFalse(g.bagOpen, "a pausa fecha a mochila");
        g.action("inventario-pausa");
        assertEquals(GameLogic.State.EXPLORE, g.state, "o Inventário da pausa volta ao jogo correndo");
        assertTrue(g.bagOpen, "com a mochila aberta");
        g.combat.outcome = Combat.Outcome.DEFEAT;
        frames(g, 2);
        assertFalse(g.bagOpen, "a derrota fecha a mochila");
        g.action("inventario");
        assertFalse(g.bagOpen, "e ela não abre caído");
    }

    @Test
    void naLutaComAMochilaAbertaOFungoContinuaENaoSeAplica(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        Enemy e = g.combat.enemies.getFirst();
        g.player.place(e.homeX() + 0.8f, e.homeZ(), 0f, g.collision);
        for (int i = 0; i < 240 && !g.combat.engaged(); i++) frames(g, 1);
        assertTrue(g.combat.engaged(), "a luta começou");
        g.action("inventario");
        int hp = g.combat.hero.hp;
        for (int i = 0; i < 600 && g.combat.hero.hp == hp; i++) frames(g, 1);
        assertTrue(g.combat.hero.hp < hp, "o fungo continua atacando com a mochila aberta (o jogo não para)");
        g.combat.hero.hp = 2;
        g.action("clique:1");
        assertEquals(2, g.combat.hero.hp, "engajado: não cura");
        assertEquals(2, g.items.inventory.count("item.cataplasma"), "e não gasta");
    }

    @Test
    void oSalvamentoAutomaticoAconteceComAMochilaAberta(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        g.action("inventario");
        talkToIndira(g);                                                    // o presente marca o salvamento automático
        g.action("inventario");
        frames(g, 5);
        assertFalse(g.unsaved(), "o primeiro passo seguro salva, mesmo com a mochila aberta");
        assertTrue(Files.exists(d.resolve(SaveGame.FILE)));
    }

    @Test
    void naoAbreNoMeioDaConversa(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        GameLogic.Npc n = AdventureTest.npc(g, "indira");
        float a = (float) Math.toRadians(n.def.yawDeg());
        float x = n.def.x() + (float) Math.sin(a) * 1.1f, z = n.def.z() + (float) Math.cos(a) * 1.1f;
        g.player.place(x, z, (float) Math.atan2(n.def.x() - x, n.def.z() - z), g.collision);
        g.player.snapshot();
        frames(g, 1);
        press(g, GLFW_KEY_E);
        assertEquals(GameLogic.State.DIALOG, g.state);
        press(g, GLFW_KEY_I);
        assertEquals(GameLogic.State.DIALOG, g.state, "I no meio da conversa: nada");
        g.action("inventario");
        assertEquals(GameLogic.State.DIALOG, g.state, "nem pelo pedido");
    }

    @Test
    void naoAbreForaDaExploracao(@TempDir Path d) {
        GameLogic g = AdventureTest.adventure(d);
        g.action("inventario");
        assertEquals(GameLogic.State.SELECT, g.state, "antes de começar, não");
        g.action("comecar");
        g.action("pausar");
        g.action("inventario");
        assertEquals(GameLogic.State.PAUSE, g.state, "na pausa, não");
        assertFalse(g.bagOpen);
    }

    // ------------------------------------------------------------------ as fontes

    @Test
    void oPresenteDaIndiraSaiUmaVezNoFimDaConversa(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        assertTrue(g.items.isEmpty());
        talkToIndira(g);
        assertEquals(2, g.items.inventory.count("item.ervas_trilha"));
        assertEquals(2, g.items.inventory.count("item.cataplasma"));
        assertTrue(g.items.collected(LootTable.giftSource("indira")));
        for (int k = 0; k < 3; k++) talkToIndira(g);                     // conversar de novo não dá de novo
        assertEquals(2, g.items.inventory.count("item.cataplasma"));
        assertEquals(2, g.items.inventory.used());
    }

    @Test
    void semLimiteOPresenteEntraMesmoComMilItens(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        fill(g, 1000);
        talkToIndira(g);
        assertEquals(2, g.items.inventory.count("item.cataplasma"), "a mochila não enche");
        assertEquals(1002, g.items.inventory.used());
    }

    @Test
    void noTetoTecnicoNaoEntregaNadaEAIndiraEsperaVocêVoltar(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        limit(g, 24);
        fill(g, 24);
        talkToIndira(g);
        assertEquals(0, g.items.inventory.count("item.cataplasma"), "nada pela metade");
        assertFalse(g.items.collected(LootTable.giftSource("indira")), "a fonte continua disponível");
        assertEquals("Mochila cheia", g.itemNoticeTitle);
        g.items.inventory.remove(0, 1);
        talkToIndira(g);
        assertEquals(0, g.items.inventory.count("item.cataplasma"), "um espaço não basta para dois itens diferentes");
        g.items.inventory.remove(1, 1);
        talkToIndira(g);
        assertEquals(2, g.items.inventory.count("item.cataplasma"), "com espaço, entrega tudo");
        assertTrue(g.items.collected(LootTable.giftSource("indira")));
    }

    @Test
    void oAchadoSaiComADescobertaUmaVezEOQueNaoCoubeFicaNoLugar(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        SceneDef.Discovery casa = DiscoveryTest.approach(g, "desc.r0.ruina.0");
        int coins = g.quests.coins();
        assertTrue(g.useDiscovery(casa));
        assertEquals(coins + casa.coins(), g.quests.coins(), "as moedas congeladas da v2 continuam as mesmas");
        assertEquals(1, g.items.inventory.count("item.registro_viagem"), "o caderno ao lado do pote");
        for (int k = 0; k < 5; k++) g.useDiscovery(casa);
        assertEquals(1, g.items.inventory.count("item.registro_viagem"), "E de novo não duplica");
        // árvore com a mochila cheia: a descoberta acontece, o achado fica
        GameLogic h = proc(S_INDIRA, d.resolve("b"));
        limit(h, 24);
        fill(h, 24);
        SceneDef.Discovery tree = DiscoveryTest.of(h, "desc.r0.arvore.0");
        h.player.place(tree.x() + 0.9f, tree.z(), (float) Math.atan2(-0.9f, 0f), h.collision);
        assertTrue(h.useDiscovery(tree), "a descoberta não depende da mochila");
        assertTrue(h.quests.discovered(tree.id()));
        assertEquals(0, h.items.inventory.count("item.ervas_trilha"));
        assertFalse(h.items.collected(LootTable.findSource(tree.id())));
        h.items.inventory.remove(3, 1);
        assertFalse(h.useDiscovery(tree), "já descoberta");
        assertEquals(3, h.items.inventory.count("item.ervas_trilha"), "usar o lugar de novo recolhe o que ficou");
        h.useDiscovery(tree);
        assertEquals(3, h.items.inventory.count("item.ervas_trilha"));
    }

    @Test
    void oSitioDaUmFragmentoEOMiranteNaoEDeposito(@TempDir Path d) {
        GameLogic g = proc(S_FARM, d);
        SceneDef.Discovery sitio = g.scene.discoveries.stream().filter(x -> x.type().equals("sitio")).findFirst().orElseThrow();
        g.player.place(sitio.x() + 0.9f, sitio.z(), 0f, g.collision);
        g.useDiscovery(sitio);
        assertEquals(1, g.items.inventory.count("item.fragmento_pedra"));
        GameLogic t = proc(DiscoveryTest.S_TOWER, d.resolve("torre"));
        SceneDef.Discovery mirante = t.scene.discoveries.stream().filter(x -> x.type().equals("torre")).findFirst().orElseThrow();
        t.useDiscovery(mirante);
        assertTrue(t.quests.discovered(mirante.id()));
        assertTrue(t.items.isEmpty(), "o mirante paga as moedas de sempre e nenhum item");
    }

    @Test
    void descobertaDeSaveAntigoRecolheQuandoOJogadorVolta(@TempDir Path d) throws Exception {
        GameLogic g = proc(S_INDIRA, d);
        assertTrue(g.quests.discover("desc.r0.ruina.0", 5), "descoberta feita antes da rodada 13 (sem achado)");
        assertTrue(g.save(false), g.saveError);
        GameLogic back = reload(S_INDIRA, d);
        assertTrue(back.items.isEmpty(), "carregar não dá nada sozinho");
        SceneDef.Discovery casa = DiscoveryTest.approach(back, "desc.r0.ruina.0");
        back.useDiscovery(casa);
        assertEquals(1, back.items.inventory.count("item.registro_viagem"), "voltar ao lugar e usar recolhe, uma vez");
        assertEquals(5, back.quests.coins(), "as moedas não pagam de novo");
    }

    // ------------------------------------------------------------------ o uso

    @Test
    void aCataplasmaDevolveUmCoracaoFicaNaMochilaQuandoNaoServeENuncaNaLuta(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        g.action("inventario");
        g.action("espaco:1");
        g.action("usar-item");
        assertEquals(2, g.items.inventory.count("item.cataplasma"), "inteiro: não gasta");
        g.combat.hero.hp = 3;
        g.action("usar-item");
        assertEquals(5, g.combat.hero.hp);
        assertEquals(1, g.items.inventory.count("item.cataplasma"));
        g.action("usar-item");
        assertEquals(6, g.combat.hero.hp, "não passa da vida máxima");
        assertEquals(0, g.items.inventory.count("item.cataplasma"));
        assertNull(g.items.inventory.slot(1), "o espaço esvaziou");
        g.action("usar-item");
        assertEquals(6, g.combat.hero.hp, "espaço vazio: nada");
        g.action("espaco:0");
        g.action("usar-item");
        assertEquals(2, g.items.inventory.count("item.ervas_trilha"), "material não se usa");
    }

    @Test
    void naoSeAplicaNoMeioDaLutaNemPelaPausa(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        Enemy e = g.combat.enemies.getFirst();
        g.player.place(e.homeX() + 0.8f, e.homeZ(), 0f, g.collision);    // ao lado do fungo: ele engaja
        for (int i = 0; i < 240 && !g.combat.engaged(); i++) frames(g, 1);
        assertTrue(g.combat.engaged(), "a luta começou");
        g.combat.hero.hp = 2;
        g.action("inventario");
        g.action("espaco:1");
        g.action("usar-item");
        assertEquals(2, g.combat.hero.hp, "engajado: não cura");
        assertEquals(2, g.items.inventory.count("item.cataplasma"), "e não gasta");
        g.action("fechar-inventario");
        g.action("pausar");
        g.action("usar-item");
        assertEquals(2, g.combat.hero.hp, "a pausa não usa item");
    }

    @Test
    void depoisDaLutaACataplasmaCura(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        float sx = g.player.x, sz = g.player.z;
        Enemy e = g.combat.enemies.getFirst();
        g.player.place(e.homeX() + 0.8f, e.homeZ(), 0f, g.collision);
        for (int i = 0; i < 240 && !g.combat.engaged(); i++) frames(g, 1);
        assertTrue(g.combat.engaged(), "a luta começou");
        g.combat.hero.hp = 2;
        g.player.place(sx, sz, 0f, g.collision);                        // longe da coleira: o fungo desiste e volta
        for (int i = 0; i < 2400 && g.combat.engaged(); i++) frames(g, 1);
        assertFalse(g.combat.engaged(), "a luta acabou");
        int hp = g.combat.hero.hp;
        assertTrue(hp > 0 && hp < 6, "o herói saiu ferido: " + hp);
        g.action("inventario");
        g.action("espaco:1");
        g.action("usar-item");
        assertEquals(Math.min(6, hp + 2), g.combat.hero.hp, "depois da luta: cura um coração");
        assertEquals(1, g.items.inventory.count("item.cataplasma"));
    }

    @Test
    void recomecarOConfrontoNaoMexeNaMochila(@TempDir Path d) {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        String before = g.items.inventory.toJson().toString();
        g.combat.outcome = Combat.Outcome.VICTORY;
        g.action("recomecar");
        assertEquals(before, g.items.inventory.toJson().toString());
        talkToIndira(g);
        assertEquals(before, g.items.inventory.toJson().toString(), "nem o presente volta");
    }

    // ------------------------------------------------------------------ save

    static GameLogic reload(long seed, Path dir) {
        GameLogic g = DiscoveryTest.proc2(seed, dir);
        g.refreshSaved();
        assertNull(g.continueBlocker(), "Continuar disponível: " + g.continueBlocker());
        g.action("continuar-jogo");
        assertEquals(GameLogic.State.EXPLORE, g.state);
        return g;
    }

    @Test
    void salvarContinuarESalvarDeNovoPreservaMochilaEFontes(@TempDir Path d) throws Exception {
        GameLogic g = proc(S_INDIRA, d);
        talkToIndira(g);
        g.useDiscovery(DiscoveryTest.approach(g, "desc.r0.ruina.0"));
        g.combat.hero.hp = 3;
        g.action("inventario");
        g.action("espaco:1");
        g.action("usar-item");
        g.action("fechar-inventario");
        assertTrue(g.unsaved(), "usar um item é progresso a salvar");
        assertTrue(g.save(false), g.saveError);
        assertFalse(g.unsaved());
        byte[] first = Files.readAllBytes(d.resolve(SaveGame.FILE));
        GameLogic b = reload(S_INDIRA, d);
        assertEquals(g.items.inventory.toJson(), b.items.inventory.toJson(), "a mochila volta igual, espaço por espaço");
        assertEquals(g.items.collectedSources(), b.items.collectedSources());
        talkToIndira(b);
        b.useDiscovery(DiscoveryTest.approach(b, "desc.r0.ruina.0"));
        assertEquals(1, b.items.inventory.count("item.cataplasma"), "depois de carregar, nada duplica");
        assertEquals(1, b.items.inventory.count("item.registro_viagem"));
        for (int k = 0; k < 3; k++) {                                     // salvar e carregar várias vezes: o arquivo não deriva
            assertTrue(b.save(false), b.saveError);
            b = reload(S_INDIRA, d);
        }
        JsonObject o1 = JsonParser.parseString(new String(first)).getAsJsonObject(), o2 = JsonParser.parseString(Files.readString(d.resolve(SaveGame.FILE))).getAsJsonObject();
        assertEquals(o1.getAsJsonObject("progresso"), o2.getAsJsonObject("progresso"), "o progresso não deriva");
    }

    @Test
    void saveSemItensNaoGanhaCamposNovosEAbreComMochilaVazia(@TempDir Path d) throws Exception {
        Path f1 = SaveCompatTest.v1Save(d.resolve("v1"), o -> { });
        String t1 = Files.readString(f1);
        assertFalse(t1.contains(ItemProgress.FIELD_BAG) || t1.contains(ItemProgress.FIELD_SOURCES), "v1 sem item: o formato de antes");
        Path f2 = SaveCompatTest.v2Save(d.resolve("v2"), o -> { });
        String t2 = Files.readString(f2);
        assertFalse(t2.contains(ItemProgress.FIELD_BAG) || t2.contains(ItemProgress.FIELD_SOURCES), "v2 sem item: o formato de antes");
        GameLogic g = AdventureTest.reload(d.resolve("v1"));
        assertTrue(g.items.isEmpty(), "save antigo: mochila vazia, nada inventado");
        assertTrue(g.save(false), g.saveError);
        assertEquals(t1.replaceAll("\"tempoDeJogo\": [0-9.]+", ""), Files.readString(f1).replaceAll("\"tempoDeJogo\": [0-9.]+", ""),
                "salvar de novo sem item não reescreve o formato");
    }

    @Test
    void itemDesconhecidoEJogoMaisNovoQuantidadeErradaEInvalida(@TempDir Path d) throws Exception {
        Path f = SaveCompatTest.v2Save(d.resolve("novo"), o -> {
            JsonArray a = new JsonArray();
            JsonObject s = new JsonObject();
            s.addProperty("espaco", 0);
            s.addProperty("item", "item.espada_de_cristal");
            s.addProperty("qtd", 1);
            a.add(s);
            o.getAsJsonObject("progresso").add(ItemProgress.FIELD_BAG, a);
        });
        GameLogic g = SaveCompatTest.reread(DiscoveryTest.proc2(SaveCompatTest.SEED, d.resolve("novo")), f, Files.readAllBytes(f), true, false);
        assertTrue(g.continueBlocker().contains("itens de uma versão mais nova"), g.continueBlocker());
        Path f2 = SaveCompatTest.v2Save(d.resolve("ruim"), o -> {
            JsonArray a = new JsonArray();
            JsonObject s = new JsonObject();
            s.addProperty("espaco", 0);
            s.addProperty("item", "item.ervas_trilha");
            s.addProperty("qtd", 999);
            a.add(s);
            o.getAsJsonObject("progresso").add(ItemProgress.FIELD_BAG, a);
        });
        SaveCompatTest.reread(DiscoveryTest.proc2(SaveCompatTest.SEED, d.resolve("ruim")), f2, Files.readAllBytes(f2), false, true);
        Path f3 = SaveCompatTest.v2Save(d.resolve("regiao"), o -> {
            JsonArray a = new JsonArray();
            a.add("achado:desc.r9.ruina.0");
            o.getAsJsonObject("progresso").add(ItemProgress.FIELD_SOURCES, a);
        });
        SaveCompatTest.reread(DiscoveryTest.proc2(SaveCompatTest.SEED, d.resolve("regiao")), f3, Files.readAllBytes(f3), true, false);
        Path f4 = SaveCompatTest.v1Save(d.resolve("vila"), o -> {
            JsonArray a = new JsonArray();
            a.add("achado:desc.r0.ruina.0");
            o.getAsJsonObject("progresso").add(ItemProgress.FIELD_SOURCES, a);
        });
        SaveCompatTest.reread(AdventureTest.adventure(d.resolve("vila")), f4, Files.readAllBytes(f4), false, true);
    }

    @Test
    void cadaAventuraTemAMochilaDela(@TempDir Path d) throws Exception {
        GameLogic a = proc(S_INDIRA, d.resolve("mundo/aventuras/a"));
        talkToIndira(a);
        assertTrue(a.save(false), a.saveError);
        GameLogic b = proc(S_INDIRA, d.resolve("mundo/aventuras/b"));       // outro personagem, o mesmo mundo
        assertTrue(b.items.isEmpty());
        talkToIndira(b);
        assertEquals(2, b.items.inventory.count("item.cataplasma"), "o presente é de cada aventura");
        GameLogic other = proc(S_FARM, d.resolve("outro/aventuras/a"));     // o mesmo personagem, outro mundo
        assertTrue(other.items.isEmpty());
        GameLogic a2 = reload(S_INDIRA, d.resolve("mundo/aventuras/a"));
        assertEquals(2, a2.items.inventory.count("item.cataplasma"));
    }
}
