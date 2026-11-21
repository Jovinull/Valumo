package vx.game.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 13 — a mochila: empilhar, separar pilhas, tudo-ou-nada, tirar, ordem estável, efeito de uso e a ida e volta pelo
 * JSON do save (com as recusas). Rodada 13.1: sem limite de espaços (a lista cresce e se compacta), "cheia" só no teto
 * técnico, e o save da rodada 13 (24 espaços com buracos) lido compactado.
 */
class InventoryTest {
    static final ItemCatalog C = ItemCatalog.load(ItemCatalogTest.A);
    static final ItemDefinition ERVAS = C.get("item.ervas_trilha"), CATA = C.get("item.cataplasma"), PEDRA = C.get("item.fragmento_pedra"),
            REG = C.get("item.registro_viagem");

    @Test
    void empilhaAteOLimiteESeparaOResto() {
        Inventory inv = new Inventory();
        assertEquals(0, inv.size(), "sem espaços fixos: começa vazia");
        assertTrue(inv.add(ERVAS, 90));
        assertTrue(inv.add(ERVAS, 20));                                  // 90 + 9 completa a pilha de 99; 11 viram outra pilha
        assertEquals(110, inv.count("item.ervas_trilha"));
        assertEquals(99, inv.slot(0).count());
        assertEquals(11, inv.slot(1).count());
        assertEquals(2, inv.used());
        assertTrue(inv.add(REG, 1));
        assertTrue(inv.add(REG, 1));
        assertEquals(2, inv.count("item.registro_viagem"));
        assertEquals(4, inv.used(), "o que não empilha ocupa um espaço por unidade");
    }

    @Test
    void semLimiteDeEspacos() {
        Inventory inv = new Inventory();
        for (int i = 0; i < 1000; i++) assertTrue(inv.add(REG, 1), "o milésimo caderno também entra");
        assertTrue(inv.add(ERVAS, Inventory.MAX_GRANT));
        assertEquals(1000 + (Inventory.MAX_GRANT + 98) / 99, inv.used());
        assertEquals(Inventory.MAX_GRANT, inv.count("item.ervas_trilha"));
        assertEquals(Inventory.MAX_STACKS, inv.limit(), "o único teto é o técnico");
        Inventory back = Inventory.fromJson(inv.toJson(), C);
        assertEquals(inv.toJson(), back.toJson(), "e volta igual pelo save");
    }

    @Test
    void noTetoTecnicoNaoEntregaNadaEAEntregaETudoOuNada() {
        Inventory inv = Inventory.limited(24);
        for (int i = 0; i < 23; i++) assertTrue(inv.add(REG, 1));
        assertTrue(inv.add(ERVAS, 97));                                   // a última pilha: 97 de 99
        // pedido de duas coisas em que só a primeira cabe: NADA entra
        List<Inventory.Grant> both = List.of(new Inventory.Grant(ERVAS, 2), new Inventory.Grant(CATA, 1));
        assertFalse(inv.fits(both));
        assertFalse(inv.add(both));
        assertEquals(97, inv.count("item.ervas_trilha"), "nem as ervas que caberiam entraram");
        assertEquals(0, inv.count("item.cataplasma"));
        assertTrue(inv.add(ERVAS, 2), "completar a pilha cabe");
        assertFalse(inv.add(ERVAS, 1), "agora não cabe mais nada");
        assertEquals(24, inv.used());
    }

    @Test
    void quantidadesInvalidasSaoRecusadas() {
        Inventory inv = new Inventory();
        assertThrows(IllegalArgumentException.class, () -> inv.add(ERVAS, 0));
        assertThrows(IllegalArgumentException.class, () -> inv.add(ERVAS, -3));
        assertThrows(IllegalArgumentException.class, () -> inv.add(ERVAS, Inventory.MAX_GRANT + 1));
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(ERVAS, 100));
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(ERVAS, 0));
        assertTrue(inv.add(ERVAS, 3));
        assertFalse(inv.remove(0, 4), "não tira mais do que há");
        assertFalse(inv.remove(0, 0));
        assertFalse(inv.remove(0, -1));
        assertFalse(inv.remove(5, 1), "pilha que não existe");
        assertFalse(inv.remove(-1, 1), "índice negativo");
        assertTrue(inv.remove(0, 3));
        assertNull(inv.slot(0), "esvaziou");
        assertTrue(inv.isEmpty());
    }

    @Test
    void aOrdemEEstavelEAPilhaQueEsvaziaSaiDaLista() {
        Inventory a = new Inventory(), b = new Inventory();
        for (Inventory inv : new Inventory[]{a, b}) {
            inv.add(ERVAS, 3);
            inv.add(PEDRA, 1);
            inv.add(CATA, 2);
        }
        assertEquals(a.toJson(), b.toJson(), "a mesma sequência dá a mesma mochila");
        assertEquals("item.fragmento_pedra", a.slot(1).item().id());
        a.remove(1, 1);
        assertEquals("item.cataplasma", a.slot(1).item().id(), "sem buraco: a lista se compacta");
        a.add(REG, 1);
        assertEquals("item.registro_viagem", a.slot(2).item().id(), "a pilha nova entra no fim");
        assertEquals(3, a.used());
    }

    @Test
    void idaEVoltaPeloJsonPreservaOrdemEQuantidades() {
        Inventory inv = new Inventory();
        inv.add(ERVAS, 120);
        inv.add(CATA, 2);
        inv.remove(0, 99);                                                // a pilha cheia sai; ficam 21 ervas e as cataplasmas
        JsonArray j = inv.toJson();
        Inventory back = Inventory.fromJson(j, C);
        assertEquals(j, back.toJson());
        assertEquals(21, back.slot(0).count());
        assertEquals("item.cataplasma", back.slot(1).item().id());
    }

    @Test
    void oSaveDaRodada13ComBuracosAbreCompactadoNaMesmaOrdem() {
        Inventory back = Inventory.fromJson(arr("[{\"espaco\":7,\"item\":\"item.registro_viagem\",\"qtd\":1},"
                + "{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":5},{\"espaco\":3,\"item\":\"item.cataplasma\",\"qtd\":2}]"), C);
        assertEquals(3, back.used());
        assertEquals("item.ervas_trilha", back.slot(0).item().id(), "na ordem dos espaços de antes");
        assertEquals("item.cataplasma", back.slot(1).item().id());
        assertEquals("item.registro_viagem", back.slot(2).item().id());
        assertEquals(arr("[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":5},{\"espaco\":1,\"item\":\"item.cataplasma\",\"qtd\":2},"
                + "{\"espaco\":2,\"item\":\"item.registro_viagem\",\"qtd\":1}]"), back.toJson(), "e grava compactado");
    }

    static JsonArray arr(String json) { return JsonParser.parseString(json).getAsJsonArray(); }

    @Test
    void jsonInvalidoOuItemDesconhecidoSaoRecusados() {
        assertThrows(ItemProgress.UnknownItem.class, () -> Inventory.fromJson(arr("[{\"espaco\":0,\"item\":\"item.espada_nova\",\"qtd\":1}]"), C));
        for (String bad : new String[]{
                "[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":0}]",
                "[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":-2}]",
                "[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":100}]",
                "[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":1.5}]",
                "[{\"espaco\":4096,\"item\":\"item.ervas_trilha\",\"qtd\":1}]",
                "[{\"espaco\":-1,\"item\":\"item.ervas_trilha\",\"qtd\":1}]",
                "[{\"espaco\":0,\"item\":\"item.ervas_trilha\",\"qtd\":1},{\"espaco\":0,\"item\":\"item.cataplasma\",\"qtd\":1}]",
                "[{\"espaco\":0,\"item\":\"Ervas da trilha\",\"qtd\":1}]",
                "[{\"espaco\":0,\"qtd\":1}]",
                "[3]",
                "[{\"espaco\":\"zero\",\"item\":\"item.ervas_trilha\",\"qtd\":1}]"})
            assertThrows(ItemProgress.Invalid.class, () -> Inventory.fromJson(arr(bad), C), bad);
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= Inventory.MAX_STACKS; i++) many.append(i > 0 ? "," : "").append("{\"espaco\":").append(i).append(",\"item\":\"item.ervas_trilha\",\"qtd\":1}");
        assertThrows(ItemProgress.Invalid.class, () -> Inventory.fromJson(arr(many + "]"), C), "pilhas demais: o leitor tem teto");
    }

    /** Alvo de cura de teste. */
    static final class Hero implements ItemEffects.Target {
        int hp = 6, max = 6;
        boolean alive = true, engaged, has = true;

        public boolean hasHero() { return has; }
        public int hp() { return hp; }
        public int maxHp() { return max; }
        public boolean alive() { return alive; }
        public boolean engaged() { return engaged; }
        public void setHp(int v) { hp = v; }
    }

    @Test
    void aCataplasmaCuraUmCoracaoSemPassarDoMaximoESoQuandoServe() {
        Hero h = new Hero();
        ItemEffects.Result r = ItemEffects.apply(CATA, h);
        assertFalse(r.consumed(), "inteiro: não gasta");
        assertEquals(6, h.hp);
        h.hp = 3;
        r = ItemEffects.apply(CATA, h);
        assertTrue(r.consumed());
        assertEquals(5, h.hp, "+2 (um coração)");
        r = ItemEffects.apply(CATA, h);
        assertTrue(r.consumed());
        assertEquals(6, h.hp, "não passa do máximo");
        h.hp = 2;
        h.engaged = true;
        r = ItemEffects.apply(CATA, h);
        assertFalse(r.consumed(), "no meio da luta: não");
        assertEquals(2, h.hp);
        assertTrue(r.message().contains("luta"));
        h.engaged = false;
        h.alive = false;
        assertFalse(ItemEffects.apply(CATA, h).consumed(), "caído: não");
        h.has = false;
        assertFalse(ItemEffects.apply(CATA, h).consumed(), "sem herói na cena: não");
        assertFalse(ItemEffects.apply(ERVAS, new Hero()).consumed(), "material não se usa");
    }

    @Test
    void entregaUmaVezPorFonteTudoOuNadaEMochilaCheiaGuardaAFonte() {
        ItemProgress p = new ItemProgress();
        List<Inventory.Grant> gift = List.of(new Inventory.Grant(ERVAS, 2), new Inventory.Grant(CATA, 2));
        assertEquals(ItemProgress.Outcome.GRANTED, p.grant("presente:indira", gift));
        assertEquals(ItemProgress.Outcome.ALREADY, p.grant("presente:indira", gift), "a mesma fonte não entrega de novo");
        for (int i = 0; i < 50; i++) p.grant("presente:indira", gift);   // E apertado muitas vezes
        assertEquals(2, p.inventory.count("item.cataplasma"));
        // enche a mochila e tenta outra fonte
        ItemProgress full = ItemProgress.of(Inventory.limited(24));      // só no teto técnico
        for (int i = 0; i < 24; i++) full.inventory.add(REG, 1);
        assertEquals(ItemProgress.Outcome.FULL, full.grant("achado:desc.r0.ruina.0", List.of(new Inventory.Grant(PEDRA, 1))));
        assertFalse(full.collected("achado:desc.r0.ruina.0"), "cheia: a fonte continua disponível");
        full.inventory.remove(0, 1);
        assertEquals(ItemProgress.Outcome.GRANTED, full.grant("achado:desc.r0.ruina.0", List.of(new Inventory.Grant(PEDRA, 1))));
        assertThrows(IllegalArgumentException.class, () -> p.grant("sem fonte", gift));
    }

    @Test
    void progressoVazioNaoEscreveNadaECampoAusenteEMochilaVazia() {
        JsonObject prog = new JsonObject();
        new ItemProgress().writeTo(prog);
        assertEquals(0, prog.size(), "save sem itens: nenhum campo novo");
        ItemProgress back = ItemProgress.read(new JsonObject(), C, s -> true);
        assertTrue(back.isEmpty());
    }

    @Test
    void progressoIdaEVoltaEFonteRecusada() {
        ItemProgress p = new ItemProgress();
        p.grant("presente:indira", List.of(new Inventory.Grant(ERVAS, 2), new Inventory.Grant(CATA, 2)));
        p.grant("achado:desc.r0.ruina.0", List.of(new Inventory.Grant(REG, 1)));
        JsonObject prog = new JsonObject();
        p.writeTo(prog);
        ItemProgress back = ItemProgress.read(prog, C, s -> true);
        assertEquals(p.inventory.toJson(), back.inventory.toJson());
        assertEquals(p.collectedSources(), back.collectedSources());
        assertThrows(ItemProgress.UnknownSource.class, () -> ItemProgress.read(prog, C, s -> !s.startsWith("achado")));
        JsonObject dup = prog.deepCopy();
        dup.getAsJsonArray(ItemProgress.FIELD_SOURCES).add("presente:indira");
        assertThrows(ItemProgress.Invalid.class, () -> ItemProgress.read(dup, C, s -> true), "fonte repetida");
        JsonObject bad = prog.deepCopy();
        bad.addProperty(ItemProgress.FIELD_BAG, "nada");
        assertThrows(ItemProgress.Invalid.class, () -> ItemProgress.read(bad, C, s -> true));
        JsonObject weird = prog.deepCopy();
        weird.getAsJsonArray(ItemProgress.FIELD_SOURCES).add("com espaço");
        assertThrows(ItemProgress.Invalid.class, () -> ItemProgress.read(weird, C, s -> true));
    }
}
