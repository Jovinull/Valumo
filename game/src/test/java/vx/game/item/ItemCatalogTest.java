package vx.game.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 13 — o catálogo oficial ({@code itens_e.json}) e a tabela de recompensas ({@code recompensas_e.json}): válidos,
 * coerentes com o jogo (os tipos de descoberta e os moradores que existem) e recusados inteiros quando um campo está
 * errado.
 */
class ItemCatalogTest {
    static final Path A = Path.of("assets");

    static String official() throws Exception { return Files.readString(A.resolve("scenes/itens_e.json")); }

    @Test
    void oCatalogoOficialEValidoComAsQuatroCategorias() {
        ItemCatalog c = ItemCatalog.load(A);
        assertEquals(5, c.all().size(), "a primeira coleção: 5 itens");
        Set<String> ids = new HashSet<>(), cats = new HashSet<>();
        for (ItemDefinition d : c.all()) {
            assertTrue(ids.add(d.id()), "id único: " + d.id());
            assertTrue(d.id().startsWith("item."));
            assertTrue(d.stack() >= 1 && d.stack() <= ItemCatalog.MAX_STACK);
            assertTrue(Files.exists(A.resolve("items/e/" + d.model() + ".vox")), "modelo gerado: " + d.model());
            cats.add(d.category());
        }
        assertEquals(Set.of("material", "achado", "consumivel", "curiosidade"), cats, "as quatro categorias da rodada");
        ItemDefinition cat = c.get("item.cataplasma");
        assertTrue(cat.usable());
        assertEquals(ItemEffects.HEAL, cat.use().effect());
        assertEquals(2, cat.use().amount(), "um coração");
        assertEquals(1, c.get("item.registro_viagem").stack(), "a curiosidade não empilha");
        assertFalse(c.get("item.ervas_trilha").usable(), "material não se usa");
    }

    /**
     * Rodada 13.1 — os modelos voxel dos itens (tools/itens_e.py): objeto de mão em voxel 1/40 u, até 0,4 u, custo pequeno, e
     * silhuetas que não se confundem (a caixa de cada par difere em proporção ou em ocupação).
     */
    @Test
    void osModelosDosItensSaoObjetosDeMaoPequenosEDistintos() throws Exception {
        JsonObject m = JsonParser.parseString(Files.readString(A.resolve(ItemCatalog.MODELS))).getAsJsonObject();
        ItemCatalog c = ItemCatalog.load(A);
        Set<String> used = new HashSet<>();
        for (ItemDefinition d : c.all()) used.add(d.model());
        assertEquals(used, m.keySet(), "um modelo por item do catálogo, nenhum sobrando");
        java.util.List<double[]> shapes = new java.util.ArrayList<>();
        for (String n : m.keySet()) {
            JsonObject e = m.getAsJsonObject(n);
            assertEquals(1 / 40.0, e.get("voxelSize").getAsDouble(), 1e-9, n + ": voxel do herói (objeto de mão)");
            var sz = e.getAsJsonArray("sizeVoxels");
            int x = sz.get(0).getAsInt(), y = sz.get(1).getAsInt(), z = sz.get(2).getAsInt();
            assertTrue(Math.max(x, Math.max(y, z)) / 40.0 <= 0.4 + 1e-9, n + ": até 0,4 u");
            assertEquals(0, e.getAsJsonArray("origin").get(1).getAsInt(), n + ": apoiado no chão");
            assertTrue(e.get("faces").getAsInt() <= 1500, n + ": custo de objeto pequeno");
            shapes.add(new double[]{(double) y / x, e.get("voxels").getAsDouble() / (x * y * z)});
        }
        for (int i = 0; i < shapes.size(); i++)
            for (int j = i + 1; j < shapes.size(); j++) {
                double[] a = shapes.get(i), b = shapes.get(j);
                assertTrue(Math.abs(a[0] - b[0]) > 0.12 || Math.abs(a[1] - b[1]) > 0.12, "silhuetas parecidas demais: " + i + " × " + j);
            }
    }

    /** Uma alteração no catálogo oficial que tem de ser recusada, com o motivo. */
    static void refused(String why, Consumer<JsonObject> edit) throws Exception {
        JsonObject o = JsonParser.parseString(official()).getAsJsonObject();
        edit.accept(o);
        ItemCatalog.Invalid e = assertThrows(ItemCatalog.Invalid.class, () -> ItemCatalog.parse(o.toString()), why);
        assertFalse(e.getMessage().isBlank());
    }

    static JsonObject item(JsonObject o, int i) { return o.getAsJsonArray("itens").get(i).getAsJsonObject(); }

    @Test
    void dadoInvalidoRecusaOCatalogoInteiro() throws Exception {
        refused("formato", o -> o.addProperty("formato", "outro"));
        refused("versão", o -> o.addProperty("versao", 2));
        refused("id repetido", o -> item(o, 1).addProperty("id", "item.ervas_trilha"));
        refused("id fora do padrão", o -> item(o, 0).addProperty("id", "Ervas"));
        refused("categoria desconhecida", o -> item(o, 0).addProperty("categoria", "lendario"));
        refused("pilha zero", o -> item(o, 0).addProperty("pilha", 0));
        refused("pilha grande", o -> item(o, 0).addProperty("pilha", 500));
        refused("pilha fracionária", o -> item(o, 0).addProperty("pilha", 2.5));
        refused("modelo com nome inválido", o -> item(o, 0).addProperty("modelo", "../fora"));
        refused("sem modelo", o -> item(o, 0).remove("modelo"));
        refused("efeito desconhecido", o -> item(o, 1).getAsJsonObject("uso").addProperty("efeito", "explodir"));
        refused("efeito fora do limite", o -> item(o, 1).getAsJsonObject("uso").addProperty("vida", 500));
        refused("nome ausente", o -> item(o, 0).remove("nome"));
        refused("nome longo", o -> item(o, 0).addProperty("nome", "x".repeat(60)));
        refused("sem itens", o -> o.add("itens", new com.google.gson.JsonArray()));
        assertThrows(ItemCatalog.Invalid.class, () -> ItemCatalog.parse("{não é json"));
        assertThrows(ItemCatalog.Invalid.class, () -> ItemCatalog.parse("[1, 2]"));
    }

    @Test
    void aTabelaDeRecompensasSoCitaItensTiposEMoradoresQueExistem() throws Exception {
        ItemCatalog c = ItemCatalog.load(A);
        LootTable t = LootTable.load(A, c);
        JsonObject desc = JsonParser.parseString(Files.readString(A.resolve("scenes/descobertas_e.json"))).getAsJsonObject().getAsJsonObject("tipos");
        for (String type : t.finds().keySet()) assertTrue(desc.has(type), "tipo de descoberta que existe: " + type);
        JsonObject hab = JsonParser.parseString(Files.readString(A.resolve("scenes/habitantes_e.json"))).getAsJsonObject().getAsJsonObject("habitantes");
        for (String npc : t.gifts().keySet()) assertTrue(hab.has(npc), "morador que existe: " + npc);
        assertNotNull(t.gift("indira"), "a herbalista dá o consumível");
        assertTrue(t.gift("indira").items().stream().anyMatch(g -> g.item().usable()), "o presente traz algo que se usa");
        assertNull(t.find("torre"), "o mirante é um lugar para ver, não um depósito");
        for (var r : t.finds().values()) for (var g : r.items()) assertTrue(g.count() <= g.item().stack());
    }

    @Test
    void tabelaComItemDesconhecidoOuQuantidadeErradaERecusada() {
        ItemCatalog c = ItemCatalog.load(A);
        String ok = "{\"formato\":\"valumo.recompensas\",\"versao\":1,\"achados\":{\"casa\":{\"itens\":[[\"item.ervas_trilha\",2]],\"aviso\":\"x\"}}}";
        assertNotNull(LootTable.parse(ok, c).find("casa"));
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace("item.ervas_trilha", "item.espada_lendaria"), c));
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace(",2]]", ",0]]"), c));
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace(",2]]", ",100]]"), c), "acima da pilha");
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace(",2]]", ",\"duas\"]]"), c));
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace("\"versao\":1", "\"versao\":2"), c));
        assertThrows(ItemCatalog.Invalid.class, () -> LootTable.parse(ok.replace("\"aviso\":\"x\"", "\"aviso\":\"\""), c));
    }
}
