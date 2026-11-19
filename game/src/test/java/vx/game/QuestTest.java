package vx.game;

import org.junit.jupiter.api.Test;
import vx.game.quest.QuestDefinition;
import vx.game.quest.QuestEvent;
import vx.game.quest.QuestProgress;
import vx.game.quest.QuestState;
import vx.game.quest.QuestSystem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Rodada 10 — modelo lógico de missão (sem interface): estados, contagem, ordem, recompensa única, consistência. */
class QuestTest {
    static final Path A = Path.of("assets");
    static final String Q = "quest.trilha.luzes_do_posto", MARCO = "obj.posto.marco";

    static QuestSystem fresh() { return new QuestSystem(QuestDefinition.load(A.resolve("scenes/missoes_e.json")).values()); }

    static List<QuestSystem.NoticeKind> kinds(QuestSystem s) { return s.drain().stream().map(QuestSystem.Notice::kind).toList(); }

    @Test
    void arquivoDaMissaoTemOContratoDaRodada() {
        Map<String, QuestDefinition> m = QuestDefinition.load(A.resolve("scenes/missoes_e.json"));
        QuestDefinition d = m.get(Q);
        assertNotNull(d, "missão com o id estável");
        assertEquals("kelyra", d.giver());
        assertEquals("trilha", d.encounter());
        assertEquals(List.of(QuestDefinition.Kind.DERROTAR, QuestDefinition.Kind.INTERAGIR, QuestDefinition.Kind.FALAR),
                d.objectives().stream().map(QuestDefinition.Objective::kind).toList());
        assertEquals(2, d.objectives().getFirst().targets().size(), "cerca de 2 fungos");
        assertEquals(MARCO, d.objectives().get(1).object());
        assertEquals(30, d.reward("moedas"));
    }

    @Test
    void caminhoFelizPassaPorTodosOsEstados() {
        QuestSystem s = fresh();
        QuestProgress p = s.get(Q);
        assertEquals(QuestState.DISPONIVEL, p.state());
        assertTrue(s.accept(Q));
        assertEquals(QuestState.ACEITA, p.state());
        assertEquals(List.of(QuestSystem.NoticeKind.ACEITA), kinds(s));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals(QuestState.EM_ANDAMENTO, p.state());
        assertEquals(1, p.done(0));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.2"));
        assertEquals(List.of(QuestSystem.NoticeKind.PROGRESSO, QuestSystem.NoticeKind.PROGRESSO, QuestSystem.NoticeKind.OBJETIVO), kinds(s));
        assertEquals("acender_marco", p.current().id());
        assertEquals(QuestSystem.Use.ATIVOU, s.use(MARCO));
        assertTrue(s.objectOn(MARCO));
        assertEquals(QuestState.PRONTA_PARA_ENTREGAR, p.state());
        assertEquals(List.of(Q), s.talkTo("kelyra"));
        assertEquals(QuestState.CONCLUIDA, p.state());
        assertEquals(30, s.coins());
        assertNull(s.check(Set.of(MARCO)));
    }

    @Test
    void mesmoInimigoNuncaContaDuasVezes() {
        QuestSystem s = fresh();
        s.accept(Q);
        for (int i = 0; i < 5; i++) s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals(1, s.get(Q).done(0));
        assertEquals(QuestState.EM_ANDAMENTO, s.get(Q).state());
    }

    @Test
    void morteAntesDeAceitarEForaDaMissaoNaoContam() {
        QuestSystem s = fresh();
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));          // antes de aceitar
        s.handle(new QuestEvent.EnemyDefeated("um.0"));                    // arena de teste
        assertTrue(s.get(Q).fact(QuestSystem.FACT_FOUGHT), "vira lembrança para a fala");
        s.accept(Q);
        assertEquals(0, s.get(Q).done(0), "nada contou antes de aceitar");
        for (String k : List.of("grupo.0", "grupo.1", "grupo.2", "chefe.0", "chefe.1", "chefe.2")) s.handle(new QuestEvent.EnemyDefeated(k));
        assertEquals(QuestState.ACEITA, s.get(Q).state(), "grupo e chefe da arena não completam a missão");
    }

    @Test
    void marcoAntesDaHoraNaoMudaNada() {
        QuestSystem s = fresh();
        assertEquals(QuestSystem.Use.SEM_MISSAO, s.use(MARCO));
        assertTrue(s.get(Q).fact(QuestSystem.FACT_SAW_OBJECT));
        s.accept(Q);
        assertEquals(QuestSystem.Use.CEDO, s.use(MARCO));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals(QuestSystem.Use.CEDO, s.use(MARCO));
        assertFalse(s.objectOn(MARCO));
        assertEquals("limpar_trilha", s.get(Q).current().id());
        assertEquals(QuestSystem.Use.NADA, s.use("obj.outro"));
    }

    @Test
    void entregaAntesDaHoraNaoDaRecompensa() {
        QuestSystem s = fresh();
        assertTrue(s.talkTo("kelyra").isEmpty());
        s.accept(Q);
        assertTrue(s.talkTo("kelyra").isEmpty());
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.2"));
        assertTrue(s.talkTo("kelyra").isEmpty(), "trilha limpa, marco apagado: ainda não entrega");
        assertEquals(0, s.coins());
    }

    @Test
    void recompensaSaiUmaVezEConcluidaNuncaReabre() {
        QuestSystem s = completed();
        int c = s.coins();
        for (int i = 0; i < 3; i++) assertTrue(s.talkTo("kelyra").isEmpty());
        assertFalse(s.accept(Q));
        assertFalse(s.decline(Q));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        s.handle(new QuestEvent.HeroDefeated());
        assertEquals(QuestSystem.Use.JA_ATIVO, s.use(MARCO));
        assertEquals(c, s.coins());
        assertEquals(QuestState.CONCLUIDA, s.get(Q).state());
        assertTrue(s.drain().isEmpty(), "nada mudou");
    }

    @Test
    void recusarMantemAOfertaEAceitarDepoisFunciona() {
        QuestSystem s = fresh();
        assertTrue(s.decline(Q));
        assertTrue(s.get(Q).declined());
        assertEquals(QuestState.DISPONIVEL, s.get(Q).state());
        assertTrue(s.decline(Q), "pode recusar de novo");
        assertTrue(s.accept(Q));
        assertFalse(s.get(Q).declined());
        assertFalse(s.accept(Q), "aceitar duas vezes não reinicia");
    }

    @Test
    void cairNaoApagaProgresso() {
        QuestSystem s = fresh();
        s.accept(Q);
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        s.handle(new QuestEvent.HeroDefeated());
        assertEquals(1, s.get(Q).done(0));
        assertTrue(s.get(Q).fact(QuestSystem.FACT_FELL));
        assertEquals(Set.of("trilha.fungo.1"), s.retiredEnemies(), "o fungo contado não volta ao recomeçar");
    }

    @Test
    void saveDaMissaoIdaEVoltaEmCadaEstado() {
        for (int step = 0; step <= 5; step++) {
            QuestSystem s = upTo(step);
            QuestSystem r = fresh();
            r.fromJson(s.toJson(), Set.of(MARCO));
            assertEquals(s.get(Q).state(), r.get(Q).state(), "passo " + step);
            assertEquals(s.get(Q).defeated(), r.get(Q).defeated());
            assertEquals(s.coins(), r.coins());
            assertEquals(s.objectOn(MARCO), r.objectOn(MARCO));
            assertEquals(s.toJson(), r.toJson(), "passo " + step + ": o mesmo save sai igual");
        }
    }

    @Test
    void saveInconsistenteERecusadoComMotivo() {
        String[][] bad = {
                {"\"recompensaEntregue\":false", "\"recompensaEntregue\":true"},          // recompensa sem concluir
                {"\"objetivo\":1", "\"objetivo\":7"},                                     // fora de faixa
                {"\"derrotados\":[\"trilha.fungo.1\",\"trilha.fungo.2\"]", "\"derrotados\":[\"trilha.fungo.1\"]"},  // passou sem todos
                {"\"objetosAtivos\":[]", "\"objetosAtivos\":[\"obj.posto.marco\"]"},    // aceso fora de hora
                {"\"moedas\":0", "\"moedas\":-5"},
                {"\"aceita\":true", "\"aceita\":\"sim\""},
                {"\"estado\":\"em_andamento\"", "\"estado\":\"concluida\""},
                {"\"derrotados\":[\"trilha.fungo.1\",\"trilha.fungo.2\"]", "\"derrotados\":[\"trilha.fungo.1\",\"grupo.0\"]"},
        };
        String good = upTo(3).toJson().toString();
        for (String[] b : bad) {
            assertTrue(good.contains(b[0]), "o caso precisa tocar o save: " + b[0]);
            var o = com.google.gson.JsonParser.parseString(good.replace(b[0], b[1])).getAsJsonObject();
            QuestSystem r = fresh();
            QuestSystem.InvalidState e = assertThrows(QuestSystem.InvalidState.class, () -> r.fromJson(o, Set.of(MARCO)), b[1]);
            assertFalse(e.getMessage().isBlank());
        }
    }

    @Test
    void arquivoDeMissaoMalformadoERecusado() {
        String ok = "{\"formato\":\"valumo.missoes\",\"versao\":1,\"missoes\":[{\"id\":\"quest.a\",\"nome\":\"A\",\"descricao\":\"d\",\"doador\":\"x\","
                + "\"objetivos\":[{\"id\":\"o\",\"tipo\":\"falar\",\"texto\":\"t\",\"npc\":\"x\"}],\"recompensa\":{\"moedas\":1}}]}";
        assertEquals(1, QuestDefinition.parse(ok).size());
        for (String[] b : new String[][]{{"\"versao\":1", "\"versao\":2"}, {"quest.a", "missao a"}, {"\"npc\":\"x\"", "\"npc\":\"y\""},
                {"\"moedas\":1", "\"ouro\":1"}, {"\"moedas\":1", "\"moedas\":-1"}, {"\"falar\"", "\"voar\""}, {"\"objetivos\":[", "\"objetivos\":[{},"}}) {
            assertTrue(ok.contains(b[0]));
            assertThrows(QuestDefinition.InvalidQuestFile.class, () -> QuestDefinition.parse(ok.replace(b[0], b[1])), b[1]);
        }
        assertThrows(QuestDefinition.InvalidQuestFile.class, () -> QuestDefinition.parse("{"));
    }

    @Test
    void falasDaKelyraCobremOsDezContextos() {
        DialogueBook b = DialogueBook.load(A.resolve("scenes/dialogos_e.json"));
        assertTrue(b.gives("kelyra"));
        for (String c : DialogueBook.GIVER_CONTEXTS) assertNotNull(b.entry("kelyra", c), c);
        // cada contexto alcançável pela regra
        QuestSystem s = fresh();
        assertEquals("oferta", DialogueBook.giverContext(s.get(Q)));
        s.use(MARCO);
        assertEquals("oferta_ja_viu", DialogueBook.giverContext(s.get(Q)));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals("oferta_ja_lutou", DialogueBook.giverContext(s.get(Q)));
        s.decline(Q);
        assertEquals("oferta_apos_recusa", DialogueBook.giverContext(s.get(Q)));
        assertFalse(b.entry("kelyra", "oferta_apos_recusa").choices().isEmpty(), "a oferta continua de pé");
        s.accept(Q);
        assertEquals("aceita", DialogueBook.giverContext(s.get(Q)));
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals("em_andamento", DialogueBook.giverContext(s.get(Q)));
        s.handle(new QuestEvent.HeroDefeated());
        assertEquals("caiu", DialogueBook.giverContext(s.get(Q)));
        s.forget(Q, QuestSystem.FACT_FELL);
        s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.2"));
        assertEquals("trilha_limpa", DialogueBook.giverContext(s.get(Q)));
        s.use(MARCO);
        assertEquals("entrega", DialogueBook.giverContext(s.get(Q)));
        s.talkTo("kelyra");
        assertEquals("concluida", DialogueBook.giverContext(s.get(Q)));
        // Bramor: uma fala a mais, sem segunda missão
        assertFalse(b.gives("bramor"));
        List<SceneDef.Line> base = List.of(new SceneDef.Line("oi", null));
        assertEquals(2, b.linesFor("bramor", base, s).lines().size());
        assertEquals(1, b.linesFor("bramor", base, fresh()).lines().size());
        assertNotNull(b.object(MARCO));
    }

    /** 0 nova · 1 aceita · 2 um fungo · 3 trilha limpa · 4 marco aceso · 5 concluída. */
    static QuestSystem upTo(int step) {
        QuestSystem s = fresh();
        if (step >= 1) s.accept(Q);
        if (step >= 2) s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.1"));
        if (step >= 3) s.handle(new QuestEvent.EnemyDefeated("trilha.fungo.2"));
        if (step >= 4) s.use(MARCO);
        if (step >= 5) s.talkTo("kelyra");
        s.drain();
        return s;
    }

    static QuestSystem completed() { return upTo(5); }
}
