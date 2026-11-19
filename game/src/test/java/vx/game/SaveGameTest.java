package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.game.quest.QuestDefinition;
import vx.game.quest.QuestState;
import vx.game.quest.QuestSystem;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Rodada 10 — jogo salvo v1: ida e volta, escrita atômica, arquivo corrompido em todas as formas pedidas. */
class SaveGameTest {
    static final Path A = Path.of("assets");
    static final ComponentCatalog CAT = ComponentCatalog.load(A);
    static final Collection<QuestDefinition> DEFS = QuestDefinition.load(A.resolve("scenes/missoes_e.json")).values();
    static final Set<String> OBJS = Set.of(QuestTest.MARCO);
    static final String SCENE = "jogo_vila_e";

    static String sample(int step, Appearance a) {
        QuestSystem q = QuestTest.upTo(step);
        return SaveGame.text(SaveGame.compose("0123456789abcdef", SCENE, a, 1.25f, -2.5f, 33f, 61.5f, q));
    }

    static SaveGame.Data parse(String json) { return SaveGame.parse(json, CAT, SCENE, DEFS, OBJS); }

    @Test
    void idaEVoltaPreservaTudo() {
        for (String preset : CAT.presets.keySet()) {
            Appearance a = Appearance.preset(preset);
            SaveGame.Data d = parse(sample(3, a));
            assertEquals(a, d.appearance(), "preset sem mudança volta idêntico: " + preset);
            assertNull(d.appearanceProblem());
            assertEquals(1.25f, d.x(), 1e-6f);
            assertEquals(-2.5f, d.z(), 1e-6f);
            assertEquals(33f, d.yawDeg(), 1e-6f);
            assertEquals(61.5f, d.playTime(), 1e-6f);
            assertEquals(QuestState.EM_ANDAMENTO, d.quests().get(QuestTest.Q).state());
            assertEquals(Set.of("trilha.fungo.1", "trilha.fungo.2"), d.quests().get(QuestTest.Q).defeated());
        }
        Appearance custom = Appearance.preset("maga");
        custom.hairColor = "#5b3a2c";
        custom.name = "Teste";
        assertEquals(custom, parse(sample(5, custom)).appearance(), "aparência personalizada embutida");
        assertEquals(30, parse(sample(5, custom)).quests().coins());
    }

    @Test
    void escritaAtomicaSubstituiSoDepoisDeValidar(@TempDir Path dir) throws IOException {
        String a = sample(1, Appearance.preset("aventureiro"));
        Path f = SaveGame.write(dir, a, t -> parse(t));
        assertEquals(a, Files.readString(f));
        // a validação do relido falha → o save antigo fica intacto e não sobra temporário
        String b = sample(3, Appearance.preset("aventureiro"));
        assertThrows(SaveGame.Invalid.class, () -> SaveGame.write(dir, b, t -> { throw new SaveGame.Invalid("simulado"); }));
        assertEquals(a, Files.readString(f));
        try (var s = Files.list(dir)) { assertEquals(List.of(SaveGame.FILE), s.map(p -> p.getFileName().toString()).toList()); }
        // jogo novo (outro id) por cima: o anterior vira aventura.anterior.json
        String other = b.replace("0123456789abcdef", "fedcba9876543210");
        SaveGame.write(dir, other, t -> parse(t));
        assertEquals(a, Files.readString(dir.resolve(SaveGame.PREVIOUS)));
        assertEquals(other, Files.readString(f));
    }

    @Test
    void falhaDeEscritaNaoEstragaOSaveAntigoNemDeixaTemporario(@TempDir Path dir) throws IOException {
        // destino impossível: no lugar do arquivo há uma pasta com conteúdo (o movimento atômico falha)
        Path blocked = dir.resolve("bloqueado");
        Files.createDirectories(blocked.resolve(SaveGame.FILE).resolve("dentro"));
        String a = sample(1, Appearance.preset("aventureiro"));
        assertThrows(IOException.class, () -> SaveGame.write(blocked, a, t -> parse(t)));
        try (var st = Files.list(blocked)) { assertTrue(st.noneMatch(f -> f.toString().endsWith(".tmp")), "temporário apagado"); }
        // pasta do save é um ARQUIVO: falha com IOException, nada é escrito
        Path file = Files.writeString(dir.resolve("arquivo"), "x");
        assertThrows(IOException.class, () -> SaveGame.write(file, a, t -> parse(t)));
        assertEquals("x", Files.readString(file));
        // o GameLogic mostra o motivo e não conta o save
        GameLogic g = AdventureTest.started(dir.resolve("ok"));
        g.saveDir = blocked;
        assertFalse(g.save(false));
        assertNotNull(g.saveError);
        assertEquals(0, g.saves);
        assertTrue(g.message.startsWith("Não consegui salvar"), g.message);
    }

    @Test
    void semArquivoNaoEErroESemPastaEDesligado(@TempDir Path dir) {
        SaveGame.Loaded l = SaveGame.read(dir, CAT, SCENE, DEFS, OBJS);
        assertFalse(l.exists());
        assertFalse(SaveGame.read(null, CAT, SCENE, DEFS, OBJS).exists());
    }

    @Test
    void arquivoCorrompidoERecusadoComMotivoEGuardado(@TempDir Path dir) throws IOException {
        String good = sample(3, Appearance.preset("exploradora"));
        JsonObject g = JsonParser.parseString(good).getAsJsonObject();
        record Case(String what, String json) { }
        List<Case> cases = List.of(
                new Case("vazio", ""),
                new Case("JSON inválido", good.substring(0, good.length() / 2)),
                new Case("lista no lugar do objeto", "[1,2,3]"),
                new Case("binário", "\u0000\u0001\u0002ÿ"),
                new Case("formato errado", good.replace("valumo.save", "valumo.aparencia")),
                new Case("versão futura", good.replace("\"versao\": 1,", "\"versao\": 2,")),
                new Case("versão texto", good.replace("\"versao\": 1,", "\"versao\": \"1\",")),
                new Case("sem jogador", without(g, "jogador")),
                new Case("sem progresso", without(g, "progresso")),
                new Case("sem aparência", without(g, "aparencia")),
                new Case("id inválido", good.replace("0123456789abcdef", "../../etc")),
                new Case("cena desconhecida", good.replace("\"cena\": \"jogo_vila_e\"", "\"cena\": \"outra\"")),
                new Case("x fora de faixa", good.replace("\"x\": 1.25", "\"x\": 1e30")),
                new Case("z NaN", good.replace("\"z\": -2.5", "\"z\": NaN")),
                new Case("tempo negativo", good.replace("\"tempoDeJogo\": 61.5", "\"tempoDeJogo\": -4")),
                new Case("moedas negativas", good.replace("\"moedas\": 0", "\"moedas\": -1")),
                new Case("missão desconhecida", good.replace("quest.trilha.luzes_do_posto", "quest.outra")),
                new Case("inconsistente", good.replace("\"recompensaEntregue\": false", "\"recompensaEntregue\": true")),
                new Case("objeto desconhecido", good.replace("\"objetosAtivos\": []", "\"objetosAtivos\": [\"obj.x\"]")));
        for (Case c : cases) {
            assertTrue(c.json().length() < 4 || !c.json().equals(good), "o caso precisa mudar o save: " + c.what());
            Files.writeString(dir.resolve(SaveGame.FILE), c.json());
            Files.deleteIfExists(dir.resolve(SaveGame.FILE + ".invalido"));
            SaveGame.Loaded l = SaveGame.read(dir, CAT, SCENE, DEFS, OBJS);
            assertNull(l.data(), c.what() + ": aceito");
            assertNotNull(l.problem(), c.what());
            assertFalse(l.problem().isBlank(), c.what());
            assertEquals(c.json(), Files.readString(dir.resolve(SaveGame.FILE + ".invalido")), c.what() + ": original guardado");
        }
        // grande demais: recusado sem ler tudo
        Files.writeString(dir.resolve(SaveGame.FILE), " ".repeat((int) SaveGame.MAX_BYTES + 10));
        assertNotNull(SaveGame.read(dir, CAT, SCENE, DEFS, OBJS).problem());
    }

    @Test
    void aparenciaIncompativelNaoDerrubaOProgresso() {
        String good = sample(4, Appearance.preset("maga"));
        String bad = good.replace("\"preset\": \"maga\"", "\"preset\": \"inexistente\"");
        assertNotEquals(good, bad);
        SaveGame.Data d = parse(bad);
        assertNotNull(d.appearanceProblem());
        assertEquals(Appearance.preset("aventureiro"), d.appearance());
        assertEquals(QuestState.PRONTA_PARA_ENTREGAR, d.quests().get(QuestTest.Q).state(), "o progresso continua");
        assertTrue(d.quests().objectOn(QuestTest.MARCO));
    }

    @Test
    void campoAMaisDeVersaoFuturaNaoViraMigracaoFalsa() {
        // só a versão 1 existe: campo desconhecido é ignorado, mas versão diferente é recusada (sem "migrar" no escuro)
        String good = sample(2, Appearance.preset("aventureiro"));
        JsonObject o = JsonParser.parseString(good).getAsJsonObject();
        o.addProperty("campoNovo", 1);
        assertNotNull(parse(o.toString()));
        o.addProperty("versao", 0);
        assertThrows(SaveGame.Invalid.class, () -> parse(o.toString()));
    }

    static String without(JsonObject o, String k) {
        JsonObject c = o.deepCopy();
        c.remove(k);
        return c.toString();
    }
}
