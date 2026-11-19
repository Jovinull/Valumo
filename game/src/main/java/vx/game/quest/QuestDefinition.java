package vx.game.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rodada 10 — definição de uma missão, lida de {@code assets/scenes/missoes_e.json} (escrito à mão). Só dados: quem dá,
 * objetivos em ordem, recompensa e o encontro da arena que pertence à missão. O mundo se refere à missão pelo id estável
 * ({@code quest.trilha.luzes_do_posto}); o progresso fica em {@link QuestProgress}.
 *
 * <p>Objetivos são cumpridos em ordem: o seguinte só vale depois do anterior (acender o marco com a trilha cheia de
 * fungos não conta). O último é sempre {@link Kind#FALAR} com quem deu a missão (a entrega).
 */
public record QuestDefinition(String id, String name, String description, String giver, String encounter,
                              List<Objective> objectives, List<Reward> rewards) {

    public enum Kind { DERROTAR, INTERAGIR, FALAR }

    /**
     * Um passo da missão. {@code targets}: identidades estáveis dos inimigos que contam ({@link Kind#DERROTAR});
     * {@code object}: id do objeto do mundo ({@link Kind#INTERAGIR}); {@code npc}: id do NPC ({@link Kind#FALAR}).
     */
    public record Objective(String id, Kind kind, String text, List<String> targets, String object, String npc) {
        public int total() { return kind == Kind.DERROTAR ? targets.size() : 1; }
    }

    /** Recompensa extensível por tipo; hoje só {@code "moedas"} (sem inventário nem loja). */
    public record Reward(String kind, int amount) { }

    public static final String FORMAT = "valumo.missoes";
    public static final int VERSION = 1;
    /** Tipos de recompensa conhecidos. Um tipo novo entra aqui e no {@link QuestSystem}, nunca em silêncio. */
    public static final Set<String> REWARD_KINDS = Set.of("moedas");
    static final Pattern QUEST_ID = Pattern.compile("quest(\\.[a-z0-9_]+)+");
    static final Pattern KEY = Pattern.compile("[a-z0-9_.\\-]+");
    /** Maior arquivo de missões aceito (bytes). */
    public static final long MAX_BYTES = 256 * 1024;
    public static final int MAX_AMOUNT = 1_000_000;

    public int index(String objectiveId) {
        for (int i = 0; i < objectives.size(); i++) if (objectives.get(i).id().equals(objectiveId)) return i;
        return -1;
    }

    /** Todos os inimigos que contam para a missão (em todos os objetivos de derrotar). */
    public Set<String> allTargets() {
        Set<String> s = new HashSet<>();
        for (Objective o : objectives) if (o.kind() == Kind.DERROTAR) s.addAll(o.targets());
        return s;
    }

    public int reward(String kind) {
        int n = 0;
        for (Reward r : rewards) if (r.kind().equals(kind)) n += r.amount();
        return n;
    }

    /** Arquivo de missões malformado: o motivo vai na mensagem (nada é ignorado em silêncio). */
    public static final class InvalidQuestFile extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidQuestFile(String msg) { super(msg); }
    }

    public static Map<String, QuestDefinition> load(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) throw new InvalidQuestFile("arquivo grande demais: " + file);
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new InvalidQuestFile("não consegui ler " + file + ": " + e.getMessage());
        }
    }

    /** Lê e valida; qualquer problema vira {@link InvalidQuestFile} com o caminho do campo. */
    public static Map<String, QuestDefinition> parse(String json) {
        JsonObject d;
        try {
            d = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new InvalidQuestFile("JSON ilegível: " + e.getMessage());
        }
        if (!FORMAT.equals(str(d, "formato", "")) ) throw new InvalidQuestFile("formato não é " + FORMAT);
        if (!d.has("versao") || d.get("versao").getAsInt() != VERSION) throw new InvalidQuestFile("versão diferente de " + VERSION);
        Map<String, QuestDefinition> out = new LinkedHashMap<>();
        JsonArray qs = arr(d, "missoes", "");
        for (int qi = 0; qi < qs.size(); qi++) {
            String at = "missoes[" + qi + "]";
            JsonObject q = obj(qs.get(qi), at);
            String id = str(q, "id", at);
            if (!QUEST_ID.matcher(id).matches()) throw new InvalidQuestFile(at + ".id inválido: " + id);
            if (out.containsKey(id)) throw new InvalidQuestFile("id repetido: " + id);
            String giver = key(q, "doador", at);
            List<Objective> objs = new ArrayList<>();
            Set<String> seenObj = new HashSet<>(), seenTargets = new HashSet<>();
            JsonArray oa = arr(q, "objetivos", at);
            if (oa.isEmpty()) throw new InvalidQuestFile(at + ".objetivos vazio");
            for (int oi = 0; oi < oa.size(); oi++) {
                String ot = at + ".objetivos[" + oi + "]";
                JsonObject o = obj(oa.get(oi), ot);
                String oid = key(o, "id", ot);
                if (!seenObj.add(oid)) throw new InvalidQuestFile(ot + ".id repetido: " + oid);
                Kind kind;
                try {
                    kind = Kind.valueOf(str(o, "tipo", ot).toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new InvalidQuestFile(ot + ".tipo desconhecido: " + str(o, "tipo", ot));
                }
                List<String> targets = List.of();
                String object = null, npc = null;
                switch (kind) {
                    case DERROTAR -> {
                        List<String> t = new ArrayList<>();
                        for (JsonElement e : arr(o, "alvos", ot)) {
                            String k = e.isJsonPrimitive() ? e.getAsString() : "";
                            if (!KEY.matcher(k).matches()) throw new InvalidQuestFile(ot + ".alvos: identidade inválida \"" + k + "\"");
                            if (!seenTargets.add(k)) throw new InvalidQuestFile(ot + ".alvos: \"" + k + "\" repetido");
                            t.add(k);
                        }
                        if (t.isEmpty()) throw new InvalidQuestFile(ot + ".alvos vazio");
                        targets = List.copyOf(t);
                    }
                    case INTERAGIR -> object = key(o, "objeto", ot);
                    case FALAR -> npc = key(o, "npc", ot);
                }
                objs.add(new Objective(oid, kind, str(o, "texto", ot), targets, object, npc));
            }
            Objective last = objs.getLast();
            if (last.kind() != Kind.FALAR || !last.npc().equals(giver))
                throw new InvalidQuestFile(at + ": o último objetivo precisa ser falar com quem deu a missão (" + giver + ")");
            List<Reward> rewards = new ArrayList<>();
            JsonObject ro = q.has("recompensa") ? obj(q.get("recompensa"), at + ".recompensa") : new JsonObject();
            for (String k : ro.keySet()) {
                if (!REWARD_KINDS.contains(k)) throw new InvalidQuestFile(at + ".recompensa: tipo desconhecido \"" + k + "\"");
                int n;
                try {
                    n = ro.get(k).getAsInt();
                } catch (RuntimeException e) {
                    throw new InvalidQuestFile(at + ".recompensa." + k + " não é número");
                }
                if (n < 0 || n > MAX_AMOUNT) throw new InvalidQuestFile(at + ".recompensa." + k + " fora de 0.." + MAX_AMOUNT);
                rewards.add(new Reward(k, n));
            }
            String enc = q.has("encontro") ? key(q, "encontro", at) : null;
            out.put(id, new QuestDefinition(id, str(q, "nome", at), str(q, "descricao", at), giver, enc, List.copyOf(objs), List.copyOf(rewards)));
        }
        if (out.isEmpty()) throw new InvalidQuestFile("nenhuma missão");
        return out;
    }

    private static JsonObject obj(JsonElement e, String at) {
        if (e == null || !e.isJsonObject()) throw new InvalidQuestFile(at + " não é objeto");
        return e.getAsJsonObject();
    }

    private static JsonArray arr(JsonObject o, String k, String at) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonArray()) throw new InvalidQuestFile(at + (at.isEmpty() ? "" : ".") + k + " ausente ou não é lista");
        return e.getAsJsonArray();
    }

    private static String str(JsonObject o, String k, String at) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank())
            throw new InvalidQuestFile(at + (at.isEmpty() ? "" : ".") + k + " ausente ou não é texto");
        return e.getAsString();
    }

    private static String key(JsonObject o, String k, String at) {
        String v = str(o, k, at);
        if (!KEY.matcher(v).matches()) throw new InvalidQuestFile(at + "." + k + " inválido: " + v);
        return v;
    }
}
