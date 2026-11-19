package vx.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.game.quest.QuestProgress;
import vx.game.quest.QuestState;
import vx.game.quest.QuestSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rodada 10 — falas por contexto, lidas de {@code assets/scenes/dialogos_e.json} (conteúdo separado do desenho). Para
 * quem dá uma missão, o contexto sai do estado da missão e das lembranças ({@link #giverContext}); os 10 contextos são
 * obrigatórios e o arquivo é recusado se faltar um. Para os outros NPCs, um contexto opcional acrescenta uma fala às de
 * sempre.
 */
public final class DialogueBook {

    /** Escolha na última fala: {@code action} = aceitar | recusar; {@code next} = contexto de reação. */
    public record Choice(String text, String action, String next) { }

    public record Entry(List<SceneDef.Line> lines, List<Choice> choices) { }

    /** Frases de um objeto do mundo, por resultado do uso. */
    public record ObjectText(String name, String verb, String verbActive, Map<QuestSystem.Use, String> results) { }

    /**
     * Os 10 contextos de quem dá a missão, na ordem de prioridade documentada em {@link #giverContext}. As reações
     * ({@code aceitou_agora}, {@code recusou_agora}) vêm das escolhas.
     */
    public static final List<String> GIVER_CONTEXTS = List.of("oferta", "oferta_ja_viu", "oferta_ja_lutou", "oferta_apos_recusa",
            "aceita", "em_andamento", "caiu", "trilha_limpa", "entrega", "concluida");
    public static final Set<String> ACTIONS = Set.of("aceitar", "recusar");

    /** Uma missão de um NPC e as falas dela. */
    record Offer(String quest, Map<String, Entry> contexts) { }

    /**
     * Rodada 11: um NPC pode ter VÁRIAS missões em sequência ({@code "missoes": [...]}; o formato da rodada 10,
     * {@code "missao"} + {@code "contextos"}, é uma lista de uma). Vale a primeira ainda não concluída; com todas
     * concluídas, a última (o agradecimento). Sem missão nenhuma, {@code offers} fica vazia.
     */
    record Npc(List<Offer> offers, boolean append) {
        String quest() { return offers.isEmpty() ? null : offers.getFirst().quest(); }
    }

    private final Map<String, Npc> npcs = new HashMap<>();
    private final Map<String, ObjectText> objects = new HashMap<>();

    public static final long MAX_BYTES = 256 * 1024;

    public static final class InvalidDialogue extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidDialogue(String msg) { super(msg); }
    }

    public static DialogueBook load(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) throw new InvalidDialogue("arquivo grande demais: " + file);
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new InvalidDialogue("não consegui ler " + file + ": " + e.getMessage());
        }
    }

    public static DialogueBook parse(String json) {
        DialogueBook b = new DialogueBook();
        JsonObject d;
        try {
            d = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new InvalidDialogue("JSON ilegível: " + e.getMessage());
        }
        if (!"valumo.dialogos".equals(opt(d, "formato")) || !d.has("versao") || d.get("versao").getAsInt() != 1)
            throw new InvalidDialogue("formato ou versão desconhecidos (esperado valumo.dialogos v1)");
        JsonObject ns = obj(d, "npcs", "");
        for (String id : ns.keySet()) {
            JsonObject n = obj(ns, id, "npcs");
            boolean append = n.has("acrescenta") && n.get("acrescenta").getAsBoolean();
            List<JsonObject> raw = new ArrayList<>();
            if (n.has("missoes")) for (JsonElement e : n.getAsJsonArray("missoes")) {
                if (!e.isJsonObject()) throw new InvalidDialogue("npcs." + id + ".missoes: item não é objeto");
                raw.add(e.getAsJsonObject());
            } else raw.add(n);
            List<Offer> offers = new ArrayList<>();
            for (JsonObject m : raw) {
                Map<String, Entry> ctx = new HashMap<>();
                JsonObject cs = obj(m, "contextos", "npcs." + id);
                for (String c : cs.keySet()) ctx.put(c, entry(obj(cs, c, "npcs." + id + ".contextos"), "npcs." + id + "." + c));
                String quest = opt(m, "missao");
                if (quest != null && !append)
                    for (String c : GIVER_CONTEXTS) if (!ctx.containsKey(c)) throw new InvalidDialogue("npcs." + id + ": falta o contexto \"" + c + "\"");
                for (var e : ctx.entrySet())
                    for (Choice ch : e.getValue().choices())
                        if (!ctx.containsKey(ch.next())) throw new InvalidDialogue("npcs." + id + "." + e.getKey() + ": escolha segue para contexto inexistente \"" + ch.next() + "\"");
                if (quest == null && n.has("missoes")) throw new InvalidDialogue("npcs." + id + ".missoes: item sem \"missao\"");
                if (quest != null || !ctx.isEmpty()) offers.add(new Offer(quest, Map.copyOf(ctx)));
            }
            b.npcs.put(id, new Npc(List.copyOf(offers), append));
        }
        if (d.has("objetos")) {
            JsonObject os = obj(d, "objetos", "");
            for (String id : os.keySet()) {
                JsonObject o = obj(os, id, "objetos");
                Map<QuestSystem.Use, String> r = new HashMap<>();
                r.put(QuestSystem.Use.ATIVOU, req(o, "ativou", id));
                r.put(QuestSystem.Use.JA_ATIVO, req(o, "jaAtivo", id));
                r.put(QuestSystem.Use.SEM_MISSAO, req(o, "semMissao", id));
                r.put(QuestSystem.Use.CEDO, req(o, "cedo", id));
                r.put(QuestSystem.Use.NADA, req(o, "nada", id));
                b.objects.put(id, new ObjectText(req(o, "nome", id), req(o, "acao", id), req(o, "acaoAtivo", id), Map.copyOf(r)));
            }
        }
        return b;
    }

    private static Entry entry(JsonObject o, String at) {
        List<SceneDef.Line> lines = new ArrayList<>();
        JsonElement fa = o.get("falas");
        if (fa == null || !fa.isJsonArray() || fa.getAsJsonArray().isEmpty()) throw new InvalidDialogue(at + ".falas ausente ou vazia");
        for (JsonElement e : fa.getAsJsonArray()) {
            if (!e.isJsonObject()) throw new InvalidDialogue(at + ".falas: item não é objeto");
            JsonObject l = e.getAsJsonObject();
            lines.add(new SceneDef.Line(req(l, "texto", at), opt(l, "expressao")));
        }
        List<Choice> choices = new ArrayList<>();
        if (o.has("escolhas")) {
            JsonElement ea = o.get("escolhas");
            if (!ea.isJsonArray()) throw new InvalidDialogue(at + ".escolhas não é lista");
            for (JsonElement e : ea.getAsJsonArray()) {
                JsonObject c = e.getAsJsonObject();
                String a = req(c, "acao", at);
                if (!ACTIONS.contains(a)) throw new InvalidDialogue(at + ": ação desconhecida \"" + a + "\"");
                choices.add(new Choice(req(c, "texto", at), a, req(c, "segue", at)));
            }
        }
        return new Entry(List.copyOf(lines), List.copyOf(choices));
    }

    private static JsonObject obj(JsonObject o, String k, String at) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonObject()) throw new InvalidDialogue((at.isEmpty() ? "" : at + ".") + k + " ausente ou não é objeto");
        return e.getAsJsonObject();
    }

    private static String opt(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String req(JsonObject o, String k, String at) {
        String v = opt(o, k);
        if (v == null || v.isBlank()) throw new InvalidDialogue(at + "." + k + " ausente");
        return v;
    }

    // ------------------------------------------------------------------ consulta

    /** Missão que o NPC oferece/recebe (ou que comenta, se {@code acrescenta}); null = nenhuma. */
    public String questOf(String npc) {
        Npc n = npcs.get(npc);
        return n == null ? null : n.quest();
    }

    /** Rodada 11 — a missão ATUAL do NPC: a primeira ainda não concluída (ou a última, se todas acabaram). */
    public String questOf(String npc, QuestSystem quests) {
        Offer o = active(npcs.get(npc), quests);
        return o == null ? null : o.quest();
    }

    private static Offer active(Npc n, QuestSystem quests) {
        if (n == null || n.offers().isEmpty()) return null;
        if (quests != null)
            for (Offer o : n.offers()) {
                QuestProgress p = o.quest() == null ? null : quests.get(o.quest());
                if (p != null && !p.completed()) return o;
            }
        return n.offers().getLast();
    }

    public boolean gives(String npc) {
        Npc n = npcs.get(npc);
        return n != null && n.quest() != null && !n.append();
    }

    public Entry entry(String npc, String context) {
        Npc n = npcs.get(npc);
        return n == null || n.offers().isEmpty() ? null : n.offers().getFirst().contexts().get(context);
    }

    /** Rodada 11 — contexto de uma missão específica do NPC (reações às escolhas da oferta atual). */
    public Entry entry(String npc, String quest, String context) {
        Npc n = npcs.get(npc);
        if (n == null) return null;
        for (Offer o : n.offers()) if (java.util.Objects.equals(o.quest(), quest)) return o.contexts().get(context);
        return null;
    }

    public ObjectText object(String id) { return objects.get(id); }

    public Set<String> objectIds() { return objects.keySet(); }

    /**
     * Contexto de quem dá a missão, por prioridade: concluída → {@code concluida}; pronta → {@code entrega}; aceita →
     * {@code caiu} (o herói caiu desde a última conversa) › {@code trilha_limpa} (falta só acender) › {@code em_andamento}
     * › {@code aceita}; disponível → {@code oferta_apos_recusa} › {@code oferta_ja_lutou} › {@code oferta_ja_viu} ›
     * {@code oferta}.
     */
    public static String giverContext(QuestProgress p) {
        QuestState s = p.state();
        return switch (s) {
            case CONCLUIDA -> "concluida";
            case PRONTA_PARA_ENTREGAR -> "entrega";
            case ACEITA, EM_ANDAMENTO -> {
                if (p.fact(QuestSystem.FACT_FELL)) yield "caiu";
                var cur = p.current();
                if (cur != null && cur.kind() == vx.game.quest.QuestDefinition.Kind.INTERAGIR) yield "trilha_limpa";
                yield s == QuestState.EM_ANDAMENTO ? "em_andamento" : "aceita";
            }
            case DISPONIVEL -> {
                if (p.declined()) yield "oferta_apos_recusa";
                if (p.fact(QuestSystem.FACT_FOUGHT)) yield "oferta_ja_lutou";
                if (p.fact(QuestSystem.FACT_SAW_OBJECT)) yield "oferta_ja_viu";
                yield "oferta";
            }
        };
    }

    /** Contexto de quem só comenta a missão ({@code acrescenta}): ativa ou concluída; null = só as falas de sempre. */
    public static String commentContext(QuestProgress p) {
        if (p == null) return null;
        if (p.completed()) return "missao_concluida";
        if (p.accepted()) return "missao_ativa";
        return null;
    }

    /** Falas a mostrar para um NPC agora: as de sempre (cena) ou as do contexto. Escolhas só para a última fala. */
    public Entry linesFor(String npc, List<SceneDef.Line> base, QuestSystem quests) {
        Npc n = npcs.get(npc);
        Offer o = active(n, quests);
        if (o == null || o.quest() == null || quests == null || quests.get(o.quest()) == null) return new Entry(base, List.of());
        QuestProgress p = quests.get(o.quest());
        if (n.append()) {
            String c = commentContext(p);
            Entry e = c == null ? null : o.contexts().get(c);
            if (e == null) return new Entry(base, List.of());
            List<SceneDef.Line> all = new ArrayList<>(base);
            all.addAll(e.lines());
            return new Entry(List.copyOf(all), List.of());
        }
        return o.contexts().get(giverContext(p));
    }
}
