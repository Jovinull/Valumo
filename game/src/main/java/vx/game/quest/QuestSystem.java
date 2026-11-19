package vx.game.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rodada 10 — camada pequena e reutilizável de missões (sem OpenGL, sem som, sem interface). Guarda o progresso de cada
 * missão, as moedas e o estado dos objetos do mundo que as missões ligam (o marco aceso). Tudo que muda sai como
 * {@link Notice} na ordem em que aconteceu; o jogo esvazia a lista depois de cada passo ({@link #drain()}) e decide o que
 * mostrar, tocar ou salvar.
 *
 * <p>Garantias (testadas em {@code QuestTest}): o mesmo inimigo nunca conta duas vezes; morte antes de aceitar não conta;
 * inimigo que não é alvo não conta; objetivo fora de ordem não conta; a recompensa sai uma única vez; missão concluída
 * nunca reabre; nada aqui depende de quadro (só de eventos).
 */
public final class QuestSystem {

    public enum NoticeKind { ACEITA, RECUSADA, PROGRESSO, OBJETIVO, OBJETO, RECOMPENSA, CONCLUIDA }

    /** O que mudou. {@code objective}: id do objetivo envolvido (ou null); {@code amount}: moedas da recompensa. */
    public record Notice(NoticeKind kind, String quest, String objective, int amount) { }

    /** Resultado de usar um objeto do mundo (o jogo escolhe a frase pelo resultado; o conteúdo fica no JSON das falas). */
    public enum Use {
        /** Era o objetivo atual: o objeto mudou de estado. */
        ATIVOU,
        /** Já estava ativo (nada muda). */
        JA_ATIVO,
        /** Uma missão pede este objeto, mas ainda não foi aceita (vira lembrança para a fala de quem a oferece). */
        SEM_MISSAO,
        /** A missão foi aceita, mas falta um objetivo anterior (a trilha ainda tem fungos). */
        CEDO,
        /** Nenhuma missão se importa com este objeto. */
        NADA
    }

    public static final String FACT_SAW_OBJECT = "viu_objeto_antes", FACT_FOUGHT = "lutou_antes", FACT_FELL = "caiu";
    /** Maior quantidade de moedas guardada (o save recusa acima disso). */
    public static final int MAX_COINS = 10_000_000;

    private final Map<String, QuestProgress> quests = new LinkedHashMap<>();
    private final TreeSet<String> objectsOn = new TreeSet<>();
    private final List<Notice> notices = new ArrayList<>();
    private int coins;
    /** Tempo de jogo (s), escrito pelo jogo a cada passo: carimba aceitar e concluir no diário. */
    public float clock;

    public QuestSystem(Collection<QuestDefinition> defs) {
        for (QuestDefinition d : defs) quests.put(d.id(), new QuestProgress(d));
    }

    public QuestProgress get(String id) { return quests.get(id); }

    public Collection<QuestProgress> all() { return Collections.unmodifiableCollection(quests.values()); }

    public int coins() { return coins; }

    public boolean objectOn(String object) { return objectsOn.contains(object); }

    public Set<String> objectsOn() { return Collections.unmodifiableSet(objectsOn); }

    /** Missão acompanhada: a primeira aceita e não concluída (ou null). */
    public QuestProgress tracked() {
        for (QuestProgress p : quests.values()) if (p.accepted && !p.completed) return p;
        return null;
    }

    /** Inimigos que já contaram para alguma missão: não voltam ao recomeçar o confronto nem ao carregar. */
    public Set<String> retiredEnemies() {
        TreeSet<String> s = new TreeSet<>();
        for (QuestProgress p : quests.values()) s.addAll(p.defeated);
        return s;
    }

    /** Os avisos desde o último {@code drain}, em ordem; a lista interna fica vazia. */
    public List<Notice> drain() {
        if (notices.isEmpty()) return List.of();
        List<Notice> out = List.copyOf(notices);
        notices.clear();
        return out;
    }

    // ------------------------------------------------------------------ escolhas do jogador

    /** Aceita (só a partir de {@link QuestState#DISPONIVEL}; recusar antes não impede). */
    public boolean accept(String id) {
        QuestProgress p = quests.get(id);
        if (p == null || p.state() != QuestState.DISPONIVEL) return false;
        p.accepted = true;
        p.declined = false;
        p.acceptedAt = clock;
        notices.add(new Notice(NoticeKind.ACEITA, id, p.current().id(), 0));
        return true;
    }

    /** "Agora não": a oferta continua; só fica a lembrança para a próxima fala. */
    public boolean decline(String id) {
        QuestProgress p = quests.get(id);
        if (p == null || p.state() != QuestState.DISPONIVEL) return false;
        p.declined = true;
        notices.add(new Notice(NoticeKind.RECUSADA, id, null, 0));
        return true;
    }

    /** Esquece uma lembrança de fala (ex.: "caiu", depois que quem deu a missão comentou). */
    public void forget(String id, String fact) {
        QuestProgress p = quests.get(id);
        if (p != null) p.facts.remove(fact);
    }

    // ------------------------------------------------------------------ eventos do mundo

    public void handle(QuestEvent ev) {
        switch (ev) {
            case QuestEvent.EnemyDefeated(String key) -> {
                for (QuestProgress p : quests.values()) {
                    if (!p.accepted) {
                        if (!p.completed && p.def.allTargets().contains(key)) p.facts.add(FACT_FOUGHT);
                        continue;
                    }
                    QuestDefinition.Objective o = p.current();
                    if (o == null || o.kind() != QuestDefinition.Kind.DERROTAR || !o.targets().contains(key)) continue;
                    if (!p.defeated.add(key)) continue;                         // a mesma identidade nunca conta duas vezes
                    notices.add(new Notice(NoticeKind.PROGRESSO, p.def.id(), o.id(), 0));
                    if (p.defeated.containsAll(o.targets())) advance(p);
                }
            }
            case QuestEvent.HeroDefeated() -> {
                for (QuestProgress p : quests.values()) if (p.accepted && !p.completed) p.facts.add(FACT_FELL);
            }
        }
    }

    /** Usar (E) um objeto do mundo. */
    public Use use(String object) {
        if (objectsOn.contains(object)) return Use.JA_ATIVO;
        for (QuestProgress p : quests.values()) {
            QuestDefinition.Objective o = p.current();
            if (o != null && o.kind() == QuestDefinition.Kind.INTERAGIR && o.object().equals(object)) {
                objectsOn.add(object);
                notices.add(new Notice(NoticeKind.OBJETO, p.def.id(), o.id(), 0));
                advance(p);
                return Use.ATIVOU;
            }
        }
        for (QuestProgress p : quests.values()) {
            if (p.completed || !wants(p.def, object)) continue;
            if (!p.accepted) {
                p.facts.add(FACT_SAW_OBJECT);
                return Use.SEM_MISSAO;
            }
            return Use.CEDO;
        }
        return Use.NADA;
    }

    /**
     * Falar com um NPC: entrega toda missão cujo objetivo atual é falar com ele. A recompensa sai aqui, UMA vez (a marca
     * {@code rewardGiven} e o estado concluído saem juntos, e uma missão concluída não tem objetivo atual).
     * Devolve as missões entregues agora.
     */
    public List<String> talkTo(String npc) {
        List<String> done = new ArrayList<>();
        for (QuestProgress p : quests.values()) {
            QuestDefinition.Objective o = p.current();
            if (o == null || o.kind() != QuestDefinition.Kind.FALAR || !o.npc().equals(npc)) continue;
            if (!p.rewardGiven) {
                int c = p.def.reward("moedas");
                coins = (int) Math.min(MAX_COINS, (long) coins + c);
                p.rewardGiven = true;
                notices.add(new Notice(NoticeKind.RECOMPENSA, p.def.id(), o.id(), c));
            }
            p.objective = p.def.objectives().size();
            p.completed = true;
            p.completedAt = clock;
            p.facts.clear();
            notices.add(new Notice(NoticeKind.CONCLUIDA, p.def.id(), o.id(), 0));
            done.add(p.def.id());
        }
        return done;
    }

    private void advance(QuestProgress p) {
        notices.add(new Notice(NoticeKind.OBJETIVO, p.def.id(), p.current().id(), 0));
        p.objective++;
    }

    private static boolean wants(QuestDefinition d, String object) {
        for (QuestDefinition.Objective o : d.objectives())
            if (o.kind() == QuestDefinition.Kind.INTERAGIR && o.object().equals(object)) return true;
        return false;
    }

    // ------------------------------------------------------------------ consistência e persistência

    /**
     * O conjunto fecha? null = sim; senão o motivo. Além de {@link QuestProgress#check()}: objeto de missão aceso ⇔ o
     * objetivo que o acende já passou; objeto desconhecido de {@code knownObjects} é recusado.
     */
    public String check(Set<String> knownObjects) {
        for (QuestProgress p : quests.values()) {
            String r = p.check();
            if (r != null) return p.def.id() + ": " + r;
            for (int i = 0; i < p.def.objectives().size(); i++) {
                QuestDefinition.Objective o = p.def.objectives().get(i);
                if (o.kind() != QuestDefinition.Kind.INTERAGIR) continue;
                boolean passed = i < p.objective;
                if (passed != objectsOn.contains(o.object()))
                    return p.def.id() + ": objeto \"" + o.object() + "\" " + (passed ? "apagado com o objetivo cumprido" : "aceso sem o objetivo cumprido");
            }
        }
        for (String o : objectsOn) if (!knownObjects.contains(o)) return "objeto desconhecido: " + o;
        if (coins < 0 || coins > MAX_COINS) return "moedas fora de 0.." + MAX_COINS;
        return null;
    }

    /** Parte do save que é das missões (o {@code SaveGame} embrulha com versão, cena, aparência e posição). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("moedas", coins);
        JsonArray on = new JsonArray();
        objectsOn.forEach(on::add);
        o.add("objetosAtivos", on);
        JsonObject qs = new JsonObject();
        for (QuestProgress p : quests.values()) {
            JsonObject q = new JsonObject();
            q.addProperty("estado", p.state().key());                     // informativo (o estado é derivado dos campos)
            q.addProperty("aceita", p.accepted);
            q.addProperty("recusada", p.declined);
            q.addProperty("objetivo", p.objective);
            q.addProperty("concluida", p.completed);
            q.addProperty("recompensaEntregue", p.rewardGiven);
            JsonArray d = new JsonArray();
            p.defeated.forEach(d::add);
            q.add("derrotados", d);
            JsonArray f = new JsonArray();
            p.facts.forEach(f::add);
            q.add("lembrancas", f);
            q.addProperty("aceitaEm", p.acceptedAt);
            q.addProperty("concluidaEm", p.completedAt);
            qs.add(p.def.id(), q);
        }
        o.add("missoes", qs);
        return o;
    }

    /** Dado inválido no save: o motivo vai na mensagem. */
    public static final class InvalidState extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidState(String msg) { super(msg); }
    }

    /**
     * Lê o que {@link #toJson()} escreveu num sistema NOVO (nada aceito). Campo ausente, de tipo errado ou fora de faixa,
     * missão desconhecida, estado gravado que não bate com os campos, ou conjunto inconsistente ({@link #check}) =
     * {@link InvalidState}. Missão do jogo ausente no save = ainda disponível (save antigo de antes dela existir).
     */
    public void fromJson(JsonObject o, Set<String> knownObjects) {
        if (!quests.values().stream().allMatch(p -> !p.accepted && !p.declined && p.facts.isEmpty()) || coins != 0 || !objectsOn.isEmpty())
            throw new IllegalStateException("fromJson só num sistema novo");
        coins = integer(o, "moedas", 0, MAX_COINS);
        for (JsonElement e : array(o, "objetosAtivos")) objectsOn.add(text(e, "objetosAtivos"));
        JsonObject qs = obj(o, "missoes");
        for (String id : qs.keySet()) {
            QuestProgress p = quests.get(id);
            if (p == null) throw new InvalidState("missão desconhecida: " + id);
            JsonObject q = obj(qs, id);
            p.accepted = bool(q, "aceita");
            p.declined = bool(q, "recusada");
            p.objective = integer(q, "objetivo", 0, p.def.objectives().size());
            p.completed = bool(q, "concluida");
            p.rewardGiven = bool(q, "recompensaEntregue");
            for (JsonElement e : array(q, "derrotados")) if (!p.defeated.add(text(e, id + ".derrotados"))) throw new InvalidState(id + ": inimigo repetido");
            for (JsonElement e : array(q, "lembrancas")) p.facts.add(text(e, id + ".lembrancas"));
            p.acceptedAt = time(q, "aceitaEm");
            p.completedAt = time(q, "concluidaEm");
            String st = q.has("estado") && q.get("estado").isJsonPrimitive() ? q.get("estado").getAsString() : null;
            if (st == null) throw new InvalidState(id + ": estado ausente");
            String r = p.check();
            if (r != null) throw new InvalidState(id + ": " + r);
            if (!st.equals(p.state().key())) throw new InvalidState(id + ": estado gravado \"" + st + "\" não bate com o progresso (" + p.state().key() + ")");
        }
        String r = check(knownObjects);
        if (r != null) throw new InvalidState(r);
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonObject()) throw new InvalidState("\"" + k + "\" ausente ou não é objeto");
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonArray()) throw new InvalidState("\"" + k + "\" ausente ou não é lista");
        if (e.getAsJsonArray().size() > 1000) throw new InvalidState("\"" + k + "\" longo demais");
        return e.getAsJsonArray();
    }

    private static String text(JsonElement e, String at) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new InvalidState(at + ": item não é texto");
        String s = e.getAsString();
        if (s.isBlank() || s.length() > 200) throw new InvalidState(at + ": texto vazio ou longo demais");
        return s;
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new InvalidState("\"" + k + "\" ausente ou não é verdadeiro/falso");
        return e.getAsBoolean();
    }

    static int integer(JsonObject o, String k, int min, int max) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new InvalidState("\"" + k + "\" ausente ou não é número");
        double v = e.getAsDouble();
        if (v != Math.rint(v) || v < min || v > max) throw new InvalidState("\"" + k + "\" fora de " + min + ".." + max + ": " + e.getAsString());
        return (int) v;
    }

    private static float time(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new InvalidState("\"" + k + "\" ausente ou não é número");
        double v = e.getAsDouble();
        if (!Double.isFinite(v) || v < -1 || v > 1e8) throw new InvalidState("\"" + k + "\" fora de faixa: " + e.getAsString());
        return (float) v;
    }
}
