package vx.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rodada 12 — o perfil CONGELADO do gerador v2 ({@code assets/scenes/regiao_v2_e.json}): a pegada, a altura, a área livre,
 * as entradas, os pontos de interação e os spawns de cada asset que a v2 planta, e os conjuntos de ofício (âncora + itens no
 * referencial dela). É uma cópia revisada do catálogo, não o catálogo: um modelo melhorado depois não move uma região v2
 * (ADR 0029). Dado puro, sem arquivo: quem lê o JSON é o {@code ProceduralScene}. Mapas ordenados (determinismo).
 */
public final class RegionProfile {
    public static final String FORMAT = "valumo.regiao.perfil";

    /**
     * Ponto no espaço do modelo (u): entrada (com rumo, graus), interação (com a altura {@code y} acima da origem do modelo,
     * rodada 12.1: o mirante em cima da muralha, o pote na laje) ou spawn (com tipo).
     */
    public record Point(String id, float x, float z, float yawDeg, String kind, float y) { }

    /**
     * Um asset: pegada {@code [x0, z0] – [x1, z1]} no espaço do modelo (+z = frente), altura, área livre (frente, lados,
     * fundos, raio) e os pontos. {@code frequency}: comum | incomum | rara.
     */
    public record Asset(String id, String category, String frequency, float x0, float z0, float x1, float z1, float height,
                        float front, float sides, float back, float radius, List<Point> entrances, List<Point> interactions,
                        List<Point> spawns) {
        /** Meia-diagonal da pegada a partir da origem do modelo (raio que a envolve). */
        public float reach() {
            return (float) Math.max(Math.max(Math.hypot(x0, z0), Math.hypot(x1, z0)), Math.max(Math.hypot(x0, z1), Math.hypot(x1, z1)));
        }
    }

    /** Item de um conjunto, no referencial da âncora (u; rumo somado ao da âncora). */
    public record Item(String prop, float x, float z, float yawDeg) { }

    /** Conjunto de ofício: âncora, itens e a caixa que envolve tudo (referencial da âncora). */
    public record SetDef(String id, String trade, String anchor, List<Item> items, float x0, float z0, float x1, float z1, Spot worker) { }

    /**
     * Rodada 12.2 — onde o trabalhador do conjunto fica, no referencial da âncora (x, z, altura do ponto acima do chão, rumo
     * em relação ao conjunto) e de onde veio: o spawn "npc" da âncora ou a vaga recomendada de um item (a do feirante, ao lado
     * da barraca). Ausente no perfil = o spawn da âncora, como na 12.1.
     */
    public record Spot(float x, float z, float y, float yawDeg, String origin) { }

    private final Map<String, Asset> assets;
    private final Map<String, SetDef> sets;
    /** Rodada 12.1 (P76): ofício de conjunto → moradores que o exercem (em ordem; vazio = o spawn fica sem ninguém). */
    private final Map<String, List<String>> workers = new java.util.TreeMap<>();

    private RegionProfile(Map<String, Asset> assets, Map<String, SetDef> sets) {
        this.assets = Collections.unmodifiableMap(assets);
        this.sets = Collections.unmodifiableMap(sets);
    }

    public Asset asset(String id) {
        Asset a = assets.get(id);
        if (a == null) throw new IllegalArgumentException("asset fora do perfil v2: " + id);
        return a;
    }

    public boolean has(String id) { return assets.containsKey(id); }

    /** Os moradores que exercem o ofício (do perfil congelado; nunca pelo nome do NPC). */
    public List<String> workers(String trade) { return workers.getOrDefault(trade, List.of()); }

    public SetDef set(String id) {
        SetDef s = sets.get(id);
        if (s == null) throw new IllegalArgumentException("conjunto fora do perfil v2: " + id);
        return s;
    }

    public Map<String, Asset> assets() { return assets; }

    public Map<String, SetDef> sets() { return sets; }

    /** Lê o perfil; formato ou versão diferentes, ou campo faltando = {@link IllegalArgumentException} com o motivo. */
    public static RegionProfile parse(JsonObject o) {
        if (!FORMAT.equals(o.has("formato") ? o.get("formato").getAsString() : null))
            throw new IllegalArgumentException("não é um perfil de região (" + FORMAT + ")");
        if (o.get("versao").getAsInt() != RegionGenerator.V2) throw new IllegalArgumentException("perfil de outra versão: " + o.get("versao"));
        Map<String, Asset> assets = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("assets").entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            JsonArray p = a.getAsJsonArray("pegada");
            JsonObject l = a.getAsJsonObject("livre");
            assets.put(e.getKey(), new Asset(e.getKey(), a.get("categoria").getAsString(), a.get("frequencia").getAsString(),
                    p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), p.get(3).getAsFloat(), a.get("altura").getAsFloat(),
                    f(l, "frente_u"), f(l, "lados_u"), f(l, "fundos_u"), f(l, "raio_u"),
                    points(a.getAsJsonArray("entradas"), 0), points(a.getAsJsonArray("interacao"), 1),
                    points(a.getAsJsonArray("spawns"), 2)));
        }
        Map<String, SetDef> sets = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("conjuntos").entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();
            List<Item> items = new ArrayList<>();
            for (JsonElement i : s.getAsJsonArray("itens")) {
                JsonArray it = i.getAsJsonArray();
                items.add(new Item(it.get(0).getAsString(), it.get(1).getAsFloat(), it.get(2).getAsFloat(), it.get(3).getAsFloat()));
            }
            JsonArray b = s.getAsJsonArray("caixa");
            Spot w = null;
            if (s.has("trabalhador")) {
                JsonArray t = s.getAsJsonArray("trabalhador");
                w = new Spot(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat(), t.get(3).getAsFloat(), t.get(4).getAsString());
            }
            sets.put(e.getKey(), new SetDef(e.getKey(), s.get("oficio").getAsString(), s.get("ancora").getAsString(), List.copyOf(items),
                    b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat(), b.get(3).getAsFloat(), w));
        }
        RegionProfile r = new RegionProfile(assets, sets);
        if (o.has("oficios"))                                              // rodada 12.1 (opcional: perfil sem moradores de ofício)
            for (var e : o.getAsJsonObject("oficios").entrySet()) {
                List<String> who = new ArrayList<>();
                for (JsonElement n : e.getValue().getAsJsonArray()) who.add(n.getAsString());
                r.workers.put(e.getKey(), List.copyOf(who));
            }
        for (SetDef s : sets.values()) {                                    // tudo o que um conjunto usa está no perfil
            r.asset(s.anchor());
            for (Item i : s.items()) r.asset(i.prop());
        }
        return r;
    }

    static float f(JsonObject o, String k) { return o != null && o.has(k) ? o.get(k).getAsFloat() : 0f; }

    /** {@code kind}: 0 = entradas [id, x, z, rumo]; 1 = interação [id, x, z, (y)]; 2 = spawns [id, x, z, tipo]. */
    static List<Point> points(JsonArray a, int kind) {
        List<Point> out = new ArrayList<>();
        for (JsonElement e : a) {
            JsonArray p = e.getAsJsonArray();
            out.add(new Point(p.get(0).getAsString(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                    kind == 0 ? p.get(3).getAsFloat() : 0f, kind == 2 && p.size() > 3 ? p.get(3).getAsString() : null,
                    kind == 1 && p.size() > 3 ? p.get(3).getAsFloat() : 0f));
        }
        return List.copyOf(out);
    }
}
