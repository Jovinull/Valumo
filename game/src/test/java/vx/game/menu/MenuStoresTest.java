package vx.game.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.SaveGame;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 11.1 — personagens ({@link CharacterStore}) e mundos ({@link WorldStore}) em pastas temporárias: identidade
 * estável, isolamento, arquivos ilegíveis recusados com motivo, importação que copia (o original fica) e nunca duplica.
 */
class MenuStoresTest {
    static final ComponentCatalog CAT = ComponentCatalog.load(Path.of("assets"));

    static String sha(Path f) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
    }

    // ------------------------------------------------------------------ personagens

    @Test
    void personagemTemIdEstavelEEditarNaoDuplicaNemTocaOutros(@TempDir Path d) throws Exception {
        CharacterStore cs = new CharacterStore(d, CAT, new Random(1));
        var a = cs.create("Brisa", Appearance.preset("exploradora"), 1000);
        var b = cs.create("Brisa", Appearance.preset("maga"), 2000);       // nome repetido: outro id
        assertNotEquals(a.id(), b.id());
        assertTrue(a.id().matches("p-[0-9a-f]{12}"));
        String hb = sha(cs.file(b.id()));
        Appearance changed = a.appearance().copy();
        changed.cover = "nenhum";
        var a2 = cs.update(a, changed, "Brisa da Vila");
        assertEquals(a.id(), a2.id(), "editar mantém o id");
        assertEquals(2, cs.list(null).size(), "editar não cria outro");
        assertEquals(hb, sha(cs.file(b.id())), "o outro perfil não muda nem um byte");
        assertEquals("Brisa da Vila", cs.get(a.id()).name());
        assertEquals(changed.signature(), cs.get(a.id()).appearance().signature());
        assertThrows(java.io.IOException.class, () -> cs.update(new CharacterStore.Profile("p-000000000000", "x", changed, "x", 0, 0), changed, "x"),
                "editar um id que não existe não cria nada");
        assertEquals(2, cs.list(null).size());
    }

    @Test
    void nomeUnicodeIdaEVolta(@TempDir Path d) throws Exception {
        CharacterStore cs = new CharacterStore(d, CAT, new Random(9));
        var p = cs.create("Iára Çãmõ 🌿", Appearance.preset("maga"), 1);
        assertEquals("Iára Çãmõ 🌿", cs.get(p.id()).name());
        assertTrue(cs.file(p.id()).getFileName().toString().matches("p-[0-9a-f]{12}\\.json"), "o nome nunca vira caminho");
        var q = cs.create("../../etc", Appearance.preset("maga"), 2);
        assertTrue(Files.exists(cs.file(q.id())));
        assertEquals("../../etc", cs.get(q.id()).name());
        try (var st = Files.list(d)) { assertEquals(2, st.count(), "nada fora da pasta"); }
    }

    @Test
    void nomeLimpoECurto(@TempDir Path d) {
        assertNull(CharacterStore.cleanName("   "));
        assertEquals("Ana", CharacterStore.cleanName(" A\u0000n\u001ba "));
        assertEquals(32, CharacterStore.cleanName("x".repeat(80)).codePointCount(0, 32));
    }

    @Test
    void perfilIlegivelFicaDeForaComMotivo(@TempDir Path d) throws Exception {
        CharacterStore cs = new CharacterStore(d, CAT, new Random(2));
        var a = cs.create("Ok", Appearance.preset("aventureiro"), 1);
        Files.writeString(d.resolve("p-aaaaaaaaaaaa.json"), "{ quebrado");
        Files.writeString(d.resolve("p-bbbbbbbbbbbb.json"), Files.readString(cs.file(a.id())));   // id interno ≠ nome do arquivo
        Files.write(d.resolve("p-cccccccccccc.json"), new byte[300 * 1024]);
        List<CharacterStore.Broken> broken = new ArrayList<>();
        var l = cs.list(broken);
        assertEquals(1, l.size());
        assertEquals(3, broken.size(), broken.toString());
        for (var b : broken) assertNotNull(b.reason());
    }

    // ------------------------------------------------------------------ mundos

    @Test
    void sementeDeSessentaEQuatroBitsSemSinal(@TempDir Path d) throws Exception {
        WorldStore ws = new WorldStore(d, new Random(4));
        assertEquals(1024L, ws.parseSeed("1024"));
        assertEquals(-1L, ws.parseSeed("18446744073709551615"), "o maior valor sem sinal");
        assertEquals(0L, ws.parseSeed("0"));
        for (String bad : new String[]{"18446744073709551616", "-1", "12a", "1 2", "+5", "1".repeat(21)})
            assertThrows(WorldStore.CreateFailed.class, () -> ws.parseSeed(bad), bad);
        long r = ws.parseSeed("   ");
        assertTrue(r >= 1 && r <= 999_999_999L, "vazia = sorteada uma vez: " + r);
    }

    @Test
    void geracaoRecusadaNaoCriaNada(@TempDir Path d) {
        WorldStore ws = new WorldStore(d, new Random(5));
        var e = assertThrows(WorldStore.CreateFailed.class,
                () -> ws.create("Ruim", 4242, 1, s -> { throw new WorldStore.CreateFailed("semente sem região"); }));
        assertEquals("semente sem região", e.getMessage());
        assertEquals(1, ws.list(null).size(), "só a vila");
        assertTrue(!Files.exists(d) || d.toFile().list().length == 0, "nenhum arquivo parcial");
    }

    @Test
    void mesmaSementeDoisMundosEAventurasIsoladas(@TempDir Path d) throws Exception {
        WorldStore ws = new WorldStore(d, new Random(6));
        var a = ws.create("A", 1024, 1, s -> { });
        var b = ws.create("", 1024, 2, s -> { });
        assertNotEquals(a.id(), b.id());
        assertEquals("Mundo 1024", b.name());
        var l = ws.list(null);
        assertEquals(3, l.size());
        assertTrue(l.getLast().legacy(), "a vila fixa sempre na lista, por último");
        Path adv = ws.adventureDir(a.id(), "p-111111111111");
        Files.createDirectories(adv);
        Files.writeString(adv.resolve(SaveGame.FILE), "{}");
        assertTrue(ws.hasAdventure(a.id(), "p-111111111111"));
        assertFalse(ws.hasAdventure(b.id(), "p-111111111111"), "mesma semente, outro mundo: outra aventura");
        assertFalse(ws.hasAdventure(a.id(), "p-222222222222"), "mesmo mundo, outro personagem: outra aventura");
        ws.save(a.used(9).withPreview(a.expectedPreviewKey(), "Uma estalagem com 3 moradores."));
        assertEquals(a.id(), ws.list(null).getFirst().id(), "o usado por último vem primeiro");
        assertEquals("1024.g" + a.generatorVersion() + ".v" + WorldStore.PREVIEW_VERSION, ws.get(a.id()).previewKey());
        assertEquals("Uma estalagem com 3 moradores.", ws.get(a.id()).summary(), "o resumo da região vai e volta");
    }

    @Test
    void mundoIlegivelFicaDeFora(@TempDir Path d) throws Exception {
        WorldStore ws = new WorldStore(d, new Random(7));
        var a = ws.create("A", 7, 1, s -> { });
        Files.createDirectories(d.resolve("m-ffffffffffff"));
        Files.writeString(d.resolve("m-ffffffffffff/mundo.json"), Files.readString(ws.dir(a.id()).resolve("mundo.json")));   // id ≠ pasta
        Files.createDirectories(d.resolve("m-eeeeeeeeeeee"));
        Files.writeString(d.resolve("m-eeeeeeeeeeee/mundo.json"), "{\"formato\":\"valumo.mundo\",\"versao\":99}");
        List<CharacterStore.Broken> broken = new ArrayList<>();
        assertEquals(2, ws.list(broken).size());
        assertEquals(2, broken.size(), broken.toString());
    }

    @Test
    void importarSaveAntigoCopiaValidaENuncaSobrescreve(@TempDir Path d) throws Exception {
        Path legacy = d.resolve("saves"), proc = d.resolve("saves-procedural");
        Files.createDirectories(legacy);
        Files.createDirectories(proc.resolve("s1024"));
        Files.writeString(legacy.resolve(SaveGame.FILE), "{\"v\":1}");
        Files.writeString(proc.resolve("s1024").resolve(SaveGame.FILE), "{\"v\":2}");
        String h = sha(legacy.resolve(SaveGame.FILE));
        WorldStore ws = new WorldStore(d.resolve("mundos"), new Random(8));
        var olds = ws.oldSaves(legacy, proc);
        assertEquals(2, olds.size());
        var v1 = olds.stream().filter(o -> o.kind() == WorldStore.Kind.LEGADO).findFirst().orElseThrow();
        // validação recusa: nada é criado
        var e = assertThrows(WorldStore.CreateFailed.class, () -> ws.importSave(v1, WorldStore.LEGACY_ID, "p-111111111111", 1, f -> "estado que não fecha"));
        assertTrue(e.getMessage().contains("estado que não fecha"));
        assertFalse(ws.hasAdventure(WorldStore.LEGACY_ID, "p-111111111111"));
        // válido: copia, o original fica idêntico, e não é mais oferecido
        ws.importSave(v1, WorldStore.LEGACY_ID, "p-111111111111", 2, f -> null);
        assertTrue(ws.hasAdventure(WorldStore.LEGACY_ID, "p-111111111111"));
        assertEquals(h, sha(legacy.resolve(SaveGame.FILE)), "o original não muda");
        assertTrue(Files.exists(legacy.resolve(SaveGame.FILE)), "o original continua lá");
        assertEquals(1, ws.oldSaves(legacy, proc).size(), "o já trazido não é oferecido de novo");
        // de novo (outro personagem): recusado — a mesma recompensa não entra duas vezes
        assertThrows(WorldStore.CreateFailed.class, () -> ws.importSave(v1, WorldStore.LEGACY_ID, "p-222222222222", 3, f -> null));
        // progresso existente nunca é substituído por um antigo
        var v2 = ws.oldSaves(legacy, proc).getFirst();
        var w = ws.create("S", 1024, 4, s -> { });
        Path adv = ws.adventureDir(w.id(), "p-111111111111");
        Files.createDirectories(adv);
        Files.writeString(adv.resolve(SaveGame.FILE), "{\"novo\":true}");
        assertThrows(WorldStore.CreateFailed.class, () -> ws.importSave(v2, w.id(), "p-111111111111", 5, f -> null));
        assertEquals("{\"novo\":true}", Files.readString(adv.resolve(SaveGame.FILE)));
        // nenhum temporário largado
        try (var s = Files.walk(d.resolve("mundos"))) { assertTrue(s.noneMatch(f -> f.toString().endsWith(".tmp"))); }
    }

    @Test
    void registroIlegivelBloqueiaImportacao(@TempDir Path d) throws Exception {
        Path legacy = d.resolve("saves");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve(SaveGame.FILE), "{}");
        WorldStore ws = new WorldStore(d.resolve("mundos"), new Random(9));
        var old = ws.oldSaves(legacy, null).getFirst();
        Files.createDirectories(d.resolve("mundos"));
        Files.writeString(d.resolve("mundos/importacoes.json"), "{{{");
        assertTrue(ws.oldSaves(legacy, null).isEmpty(), "registro ilegível: nada oferecido");
        assertThrows(WorldStore.CreateFailed.class, () -> ws.importSave(old, WorldStore.LEGACY_ID, "p-111111111111", 1, f -> null));
        assertFalse(ws.hasAdventure(WorldStore.LEGACY_ID, "p-111111111111"));
    }

    @Test
    void resumoDoProgresso(@TempDir Path d) throws Exception {
        assertNull(WorldStore.progress(d));
        Files.writeString(d.resolve(SaveGame.FILE), "{\"tempoDeJogo\":754,\"progresso\":{\"moedas\":30,\"missoes\":{\"q\":{\"concluida\":true,\"aceita\":true}}}}");
        assertEquals("12 min de jogo · missão concluída · 30 moedas", WorldStore.progress(d));
        Files.writeString(d.resolve(SaveGame.FILE), "lixo");
        assertEquals("jogo salvo ilegível", WorldStore.progress(d));
    }
}
