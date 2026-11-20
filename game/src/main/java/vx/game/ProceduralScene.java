package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.game.quest.QuestDefinition;
import vx.game.world.ProceduralField;
import vx.game.world.RegionGenerator;
import vx.game.world.RegionLayout;
import vx.game.world.Rng;
import vx.viewer.Terrain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rodada 11 — uma região procedural transformada em cena jogável: o {@link RegionLayout} da semente vira os mesmos
 * documentos das cenas em arquivo (o do {@code World} para o desenho, o do {@link SceneDef} para o jogo e o do combate
 * com a arena e o encontro no lugar gerado), e o modelo de missão {@code quest.template.restaurar_sinal} vira uma
 * INSTÂNCIA (um {@link QuestDefinition} comum, com ids estáveis do mundo) só se o mundo tiver os fatos que ele exige.
 *
 * <p>Antes de aceitar a composição, ela é conferida na colisão REAL ({@link Collision} montada como no jogo) e no relevo
 * T1 ({@link Reachability}); composição reprovada pede outra tentativa (no máximo
 * {@link RegionGenerator#MAX_ATTEMPTS}), e se nenhuma passar a semente é recusada com os motivos. Nada aqui usa OpenGL.
 */
public final class ProceduralScene {
    public static final String LAYOUT = "procedural";
    public static final String TEMPLATE = "quest.template.restaurar_sinal";

    public final long seed;
    public final RegionLayout layout;
    public final ProceduralField field;
    public final JsonObject worldDoc, sceneDoc, combatDoc;
    /** A missão instanciada (null = o mundo não tem os fatos do modelo) e o vínculo resolvido dela. */
    public final QuestDefinition quest;
    public final Binding binding;
    public final DialogueBook dialogues;
    /** Tentativas recusadas (motivos), tempos (ns) e a conferência da composição aceita. */
    public final List<String> log;
    public long composeNanos, validateNanos;
    public final Reachability.Report reach;
    private final Path assets;

    /**
     * Vínculo da instância com o mundo gerado (o que o save guarda para nunca reposicionar uma missão aceita): o modelo e
     * a versão, o POI, o doador, os inimigos, o marco e a posição dele, o rumo usado nas falas.
     */
    public record Binding(String template, int templateVersion, String questId, String poi, String giver, List<String> enemies,
                          String feature, float featureX, float featureZ, String rumo) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("modelo", template);
            o.addProperty("versaoModelo", templateVersion);
            o.addProperty("missao", questId);
            o.addProperty("poi", poi);
            o.addProperty("doador", giver);
            JsonArray e = new JsonArray();
            enemies.forEach(e::add);
            o.add("inimigos", e);
            o.addProperty("objeto", feature);
            // double já arredondado: o número relido do JSON é igual ao escrito (float viraria 4.592000007…)
            o.addProperty("objetoX", Math.round(featureX * 1000) / 1000.0);
            o.addProperty("objetoZ", Math.round(featureZ * 1000) / 1000.0);
            o.addProperty("rumo", rumo);
            return o;
        }
    }

    /** A semente não deu uma região jogável em nenhuma tentativa (os motivos vão na mensagem). */
    public static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Rejected(String msg) { super(msg); }
    }

    private ProceduralScene(Path assets, long seed, RegionLayout layout, ProceduralField field, JsonObject worldDoc, JsonObject sceneDoc,
                            JsonObject combatDoc, QuestDefinition quest, Binding binding, DialogueBook dialogues, List<String> log,
                            Reachability.Report reach) {
        this.assets = assets;
        this.seed = seed;
        this.layout = layout;
        this.field = field;
        this.worldDoc = worldDoc;
        this.sceneDoc = sceneDoc;
        this.combatDoc = combatDoc;
        this.quest = quest;
        this.binding = binding;
        this.dialogues = dialogues;
        this.log = log;
        this.reach = reach;
    }

    /** Rodada 12: o perfil congelado da v2 (lido a cada geração: é pequeno, e o arquivo é a fonte da verdade). */
    static vx.game.world.RegionProfile profile(Path assets) { return vx.game.world.RegionProfile.parse(read(assets, "regiao_v2_e.json")); }

    /** Id do mundo para ids de instância e para a pasta do save ("s1024"). */
    public static String worldId(long seed) { return "s" + Long.toUnsignedString(seed); }

    public String worldId() { return worldId(seed); }

    /** A versão do gerador desta região (a do mundo). */
    public int version() { return layout.version; }

    /**
     * Gera a região da semente na versão {@code version} do gerador — a do MUNDO (rodada 12, ADR 0029): um mundo v1 é
     * sempre gerado pela v1, mesmo num jogo que já cria mundos v2. Versão não suportada = {@link Rejected}.
     */
    public static ProceduralScene create(Path assets, long seed, int version) {
        if (!RegionGenerator.supports(version))
            throw new Rejected("versão " + version + " do gerador não suportada por este jogo (semente " + Long.toUnsignedString(seed) + ")");
        List<String> log = new ArrayList<>();
        vx.game.world.RegionProfile profile = version >= RegionGenerator.V2 ? profile(assets) : null;
        for (int attempt = 0; attempt < RegionGenerator.MAX_ATTEMPTS; attempt++) {
            long t0 = System.nanoTime();
            RegionLayout L = RegionGenerator.compose(seed, attempt, version, profile, true);
            ProceduralField field = new ProceduralField(L);
            Docs d = docs(assets, L);
            long t1 = System.nanoTime();
            SceneDef scene = SceneDef.fromDoc(assets, LAYOUT, d.scene, d.combat, field);
            Terrain terrain = Terrain.of(field);
            Collision col = Collision.build(terrain, Collision.profiles(assets), scene.handPlaced, scene.bodies, scene);
            Reachability.Report rep = Reachability.check(L, scene, col, terrain);
            long t2 = System.nanoTime();
            if (version >= RegionGenerator.V2 && L.buildings.stream().noneMatch(b -> b.role().equals("ruina")))   // a v2 sempre tem o lugar a explorar (casa ou torre)
                rep = new Reachability.Report(List.of("sem lugar para a ruína"), rep.cells(), rep.walkable(), rep.reachable(), rep.arenaFree(),
                        rep.targets(), rep.nanos());
            if (rep.problems().isEmpty()) {
                ProceduralScene ps = new ProceduralScene(assets, seed, L, field, d.world, d.scene, d.combat, d.quest, d.binding, d.dialogues,
                        List.copyOf(log), rep);
                ps.composeNanos = t1 - t0;
                ps.validateNanos = t2 - t1;
                return ps;
            }
            log.add("tentativa " + attempt + " recusada: " + String.join("; ", rep.problems()));
            Terrain.release(field);                                         // rodada 11.1: a grade recusada não fica no cache
        }
        throw new Rejected("semente " + Long.toUnsignedString(seed) + " sem região jogável em " + RegionGenerator.MAX_ATTEMPTS
                + " tentativas — " + String.join(" | ", log));
    }

    /**
     * Rodada 11.1 — o resumo da região para a lista de mundos, só com FATOS do que foi gerado e que o jogo não muda: quantos
     * moradores, para onde a trilha vai, lago ou colina (nada sobre o marco ou os fungos, que a aventura muda).
     */
    public String summary() {
        var L = layout;
        int n = L.npcs.size();
        StringBuilder b = new StringBuilder("Uma estalagem com ").append(n).append(n == 1 ? " morador. " : " moradores. ");
        b.append("A trilha segue para o ").append(rumo(L.px - L.sx, L.pz - L.sz)).append(" até um posto.");
        if (L.pond != null) b.append(" Há um lago por perto.");
        else if (L.hill != null) b.append(" Uma colina se ergue ao lado.");
        return b.toString();
    }

    /** A cena do jogo desta região (o mesmo {@link SceneDef} das cenas em arquivo, com a missão instanciada). */
    public SceneDef scene() {
        SceneDef s = SceneDef.fromDoc(assets, LAYOUT, sceneDoc, combatDoc, field);
        s.procedural = this;
        return s;
    }

    // ------------------------------------------------------------------ documentos

    record Docs(JsonObject world, JsonObject scene, JsonObject combat, QuestDefinition quest, Binding binding, DialogueBook dialogues) { }

    static JsonObject read(Path assets, String file) {
        try {
            return JsonParser.parseString(Files.readString(assets.resolve("scenes/" + file))).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cena ausente: " + file, e);
        }
    }

    static Docs docs(Path assets, RegionLayout L) {
        JsonObject residents = read(assets, "habitantes_e.json").getAsJsonObject("habitantes");
        String rumo = rumo(L.px - L.sx, L.pz - L.sz);
        // ---- props (desenho e colisão usam a MESMA lista)
        JsonArray props = new JsonArray();
        for (RegionLayout.Prop p : L.props) {
            JsonObject o = new JsonObject();
            o.addProperty("n", p.name());
            o.addProperty("x", r3(p.x()));
            o.addProperty("z", r3(p.z()));
            o.addProperty("r", r3(p.rotDeg()));
            props.add(o);
        }
        // ---- o mundo (formato de assets/scenes/*.json): tudo explícito, sem espalhamento
        JsonObject world = new JsonObject();
        world.add("hero", arr(L.startX, L.startZ, L.startYawDeg));
        world.add("props", props);
        world.add("scatter", new JsonArray());
        JsonArray clouds = new JsonArray();
        Rng cr = Rng.of(L.seed, L.regionId, "nuvens");
        for (int i = 0; i < 3; i++) clouds.add(arr(cr.range(-30, 30), cr.range(13, 17), cr.range(-36, -26)));
        world.add("clouds", clouds);
        // ---- missão: fatos → instância (ou nenhuma)
        JsonObject templates = read(assets, "modelos_missao_e.json");
        JsonObject tpl = null;
        for (JsonElement e : templates.getAsJsonArray("modelos"))
            if (e.getAsJsonObject().get("id").getAsString().equals(TEMPLATE)) tpl = e.getAsJsonObject();
        if (tpl == null) throw new IllegalStateException("modelo ausente: " + TEMPLATE);
        Instance inst = instantiate(assets, L, tpl, residents, rumo);
        // ---- NPCs interativos
        JsonArray inter = new JsonArray();
        for (RegionLayout.Npc n : L.npcs) {
            JsonObject r = residents.getAsJsonObject(n.id());
            JsonObject o = new JsonObject();
            o.addProperty("rig", r.get("rig").getAsString());
            o.addProperty("nome", r.get("nome").getAsString());
            o.addProperty("titulo", r.get("titulo").getAsString());
            o.addProperty("x", r3(n.x()));
            o.addProperty("z", r3(n.z()));
            o.addProperty("r", r3(n.yawDeg()));
            o.addProperty("alcance", 1.8f);
            JsonArray lines = (L.pond == null && r.has("falasSemLago") ? r.getAsJsonArray("falasSemLago") : r.getAsJsonArray("falas")).deepCopy();
            for (JsonElement l : lines) {
                JsonObject lo = l.getAsJsonObject();
                lo.addProperty("texto", fill(lo.get("texto").getAsString(), Map.of("rumo_posto", rumo)));
            }
            o.add("falas", lines);
            inter.add(o);
        }
        JsonArray objects = new JsonArray(), discoveries = new JsonArray();
        JsonObject kinds = L.version >= RegionGenerator.V2 ? read(assets, "descobertas_e.json").getAsJsonObject("tipos") : null;
        for (RegionLayout.Feature f : L.features) {
            if (f.kind().equals("descoberta")) {                            // rodada 12 (v2): a descoberta do lugar
                RegionLayout.DiscoverySpot spot = L.discoverySpots.stream().filter(d -> d.id().equals(f.id())).findFirst().orElseThrow();
                discoveries.add(discovery(kinds.getAsJsonObject(spot.type()), spot, f, L));
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("x", r3(f.x()));
            o.addProperty("z", r3(f.z()));
            o.addProperty("r", r3(f.yawDeg()));
            o.addProperty("alcance", 1.8f);
            JsonObject m = new JsonObject();
            m.addProperty("apagado", "trail_beacon_off");
            m.addProperty("aceso", "trail_beacon_on");
            o.add("modelos", m);
            o.addProperty("raio", 0.26f);
            o.addProperty("placa", 1.75f);
            objects.add(o);
        }
        JsonObject game = new JsonObject();
        game.addProperty("_doc", "Gerado (rodada 11): semente " + Long.toUnsignedString(L.seed) + ", " + RegionGenerator.ID + " v" + L.version
                + ", tentativa " + L.attempt + ".");
        game.add("inicio", arr(L.startX, L.startZ, L.startYawDeg));
        JsonObject lim = new JsonObject();
        lim.add("centro", arr(L.limits.getFirst()[0], L.limits.getFirst()[1]));
        lim.addProperty("raio", L.limits.getFirst()[2]);
        game.add("limite", lim);
        JsonArray extra = new JsonArray();
        for (int i = 1; i < L.limits.size(); i++) extra.add(arr(L.limits.get(i)[0], L.limits.get(i)[1], L.limits.get(i)[2]));
        game.add("limitesExtras", extra);
        game.add("interativos", inter);
        game.add("objetos", objects);
        if (!discoveries.isEmpty()) game.add("descobertas", discoveries);    // rodada 12: só a v2 tem (a v1 sai igual)
        JsonObject scene = new JsonObject();
        scene.add("props", props);
        scene.add("jogo", game);
        // ---- combate: arena e encontro no lugar gerado (os encontros de comparação da vila ficam de fora: são de lá)
        JsonObject combat = read(assets, "combate_e.json").deepCopy();
        JsonObject arena = combat.getAsJsonObject("arena");
        arena.add("centro", arr(L.px, L.pz));
        arena.addProperty("raio", L.arenaR);
        arena.add("recomeco", arr(L.restartX, L.restartZ, L.restartYawDeg));
        JsonObject enc = new JsonObject();
        enc.addProperty("_doc", "Gerado (rodada 11): o encontro do posto, com as identidades estáveis do mundo.");
        enc.addProperty("padrao", "posto");
        JsonArray spawns = new JsonArray();
        for (RegionLayout.Spawn s : L.spawns) {
            JsonArray a = new JsonArray();
            a.add(s.variant());
            a.add(r3(s.x()));
            a.add(r3(s.z()));
            a.add(r3(s.yawDeg()));
            a.add(s.key());
            spawns.add(a);
        }
        enc.add("posto", spawns);
        combat.add("encontros", enc);
        return new Docs(world, scene, combat, inst.quest, inst.binding, inst.dialogues);
    }

    // ------------------------------------------------------------------ modelo → instância

    record Instance(QuestDefinition quest, Binding binding, DialogueBook dialogues) { }

    /**
     * Fatos exigidos pelo modelo: um POI do tipo pedido, no estado pedido, com inimigos da variante pedida e um objeto, e
     * um morador com um dos papéis do doador. Sem os fatos não há instância (a região continua jogável, só não oferece
     * esta missão). A alcançabilidade é conferida depois, na colisão real, junto com o resto da composição.
     */
    static Instance instantiate(Path assets, RegionLayout L, JsonObject tpl, JsonObject residents, String rumo) {
        JsonObject req = tpl.getAsJsonObject("requer");
        RegionLayout.PointOfInterest poi = null;
        for (RegionLayout.PointOfInterest p : L.pois)
            if (p.type().equals(req.get("poi").getAsString()) && p.state().equals(req.get("estado").getAsString())
                    && !p.features().isEmpty() && !p.spawns().isEmpty()) { poi = p; break; }
        if (poi == null) return new Instance(null, null, null);
        for (String k : poi.spawns()) {
            RegionLayout.Spawn s = L.spawns.stream().filter(x -> x.key().equals(k)).findFirst().orElseThrow();
            if (!s.variant().equals(req.get("inimigos").getAsString())) return new Instance(null, null, null);
        }
        // doador: moradores com papel compatível, em ordem de id (estável), escolha pela semente da missão
        List<String> roles = new ArrayList<>();
        req.getAsJsonObject("doador").getAsJsonArray("papeis").forEach(e -> roles.add(e.getAsString()));
        TreeMap<String, String> candidates = new TreeMap<>();                 // npc → papel (o primeiro do modelo que ele tem)
        for (RegionLayout.Npc n : L.npcs) {
            JsonObject r = residents.getAsJsonObject(n.id());
            for (String role : roles)
                for (JsonElement e : r.getAsJsonArray("papeis"))
                    if (e.getAsString().equals(role) && !candidates.containsKey(n.id())) candidates.put(n.id(), role);
        }
        if (candidates.isEmpty()) return new Instance(null, null, null);
        List<String> ids = new ArrayList<>(candidates.keySet());
        String giver = ids.get(Rng.of(L.seed, L.regionId, "missao", TEMPLATE, poi.id()).below(ids.size()));
        String voice = candidates.get(giver);
        JsonObject g = residents.getAsJsonObject(giver);
        String name = g.get("nome").getAsString(), article = g.get("artigo").getAsString();
        Map<String, String> params = Map.of("rumo", rumo, "doador", name, "ao_doador", (article.equals("a") ? "à " : "ao ") + name);
        String feature = poi.features().getFirst();
        RegionLayout.Feature ft = L.feature(feature);
        String questId = "quest.instance." + worldId(L.seed) + "." + L.regionId + "." + poi.id().replace("poi." + L.regionId + ".", "").replace('.', '_') + ".1";
        // a definição (o mesmo formato de missoes_e.json, validada pelo mesmo leitor)
        JsonObject q = new JsonObject();
        q.addProperty("id", questId);
        q.addProperty("nome", tpl.get("nome").getAsString());
        q.addProperty("descricao", fill(tpl.get("descricao").getAsString(), params));
        q.addProperty("doador", giver);
        q.addProperty("encontro", "posto");
        JsonArray objs = new JsonArray();
        for (JsonElement e : tpl.getAsJsonArray("objetivos")) {
            JsonObject o = e.getAsJsonObject().deepCopy();
            o.addProperty("texto", fill(o.get("texto").getAsString(), params));
            if (o.has("alvos")) { JsonArray a = new JsonArray(); poi.spawns().forEach(a::add); o.add("alvos", a); }
            if (o.has("objeto")) o.addProperty("objeto", feature);
            if (o.has("npc")) o.addProperty("npc", giver);
            objs.add(o);
        }
        q.add("objetivos", objs);
        q.add("recompensa", tpl.get("recompensa").deepCopy());
        JsonObject file = new JsonObject();
        file.addProperty("formato", QuestDefinition.FORMAT);
        file.addProperty("versao", QuestDefinition.VERSION);
        JsonArray qs = new JsonArray();
        qs.add(q);
        file.add("missoes", qs);
        QuestDefinition def = QuestDefinition.parse(file.toString()).get(questId);
        // as falas: a voz do papel do doador, parâmetros preenchidos; quem mais mora ali comenta (sem segunda missão)
        JsonObject voices = tpl.getAsJsonObject("vozes").getAsJsonObject(voice).deepCopy();
        fillAll(voices, params);
        JsonObject npcs = new JsonObject();
        JsonObject giverEntry = new JsonObject();
        giverEntry.addProperty("missao", questId);
        giverEntry.add("contextos", voices);
        npcs.add(giver, giverEntry);
        if (tpl.has("comentarios")) for (Map.Entry<String, JsonElement> c : tpl.getAsJsonObject("comentarios").entrySet()) {
            if (c.getKey().equals(giver) || L.npcs.stream().noneMatch(n -> n.id().equals(c.getKey()))) continue;
            JsonObject ctx = new JsonObject();
            for (Map.Entry<String, JsonElement> k : c.getValue().getAsJsonObject().entrySet()) {
                JsonObject en = new JsonObject();
                JsonArray lines = k.getValue().getAsJsonArray().deepCopy();
                for (JsonElement l : lines) l.getAsJsonObject().addProperty("texto", fill(l.getAsJsonObject().get("texto").getAsString(), params));
                en.add("falas", lines);
                ctx.add(k.getKey(), en);
            }
            JsonObject en = new JsonObject();
            en.addProperty("missao", questId);
            en.addProperty("acrescenta", true);
            en.add("contextos", ctx);
            npcs.add(c.getKey(), en);
        }
        JsonObject book = new JsonObject();
        book.addProperty("formato", "valumo.dialogos");
        book.addProperty("versao", 1);
        book.add("npcs", npcs);
        JsonObject objTexts = new JsonObject();
        JsonObject marco = read(assets, "dialogos_e.json").getAsJsonObject("objetos").getAsJsonObject("obj.posto.marco").deepCopy();
        objTexts.add(feature, marco);
        book.add("objetos", objTexts);
        DialogueBook dialogues = DialogueBook.parse(book.toString());
        Binding b = new Binding(TEMPLATE, tpl.get("versao").getAsInt(), questId, poi.id(), giver, List.copyOf(poi.spawns()), feature, ft.x(), ft.z(), rumo);
        return new Instance(def, b, dialogues);
    }

    // ------------------------------------------------------------------ texto

    /**
     * Rumo do vetor no chão, nas oito direções: +z = sul (a trilha da vila de sempre vai para +z e é "a trilha do
     * sul"), −z = norte, +x = leste, −x = oeste. Todos masculinos ("do sul", "para o nordeste").
     */
    static String rumo(float dx, float dz) {
        double a = Math.toDegrees(Math.atan2(dx, dz));                     // 0 = +z (sul), 90 = +x (leste)
        String[] names = {"sul", "sudeste", "leste", "nordeste", "norte", "noroeste", "oeste", "sudoeste"};
        int i = (int) Math.floorMod(Math.round(a / 45.0), 8L);
        return names[i];
    }

    /** Troca {chave} pelos valores; chave desconhecida = erro (texto com lacuna nunca chega à tela). */
    /**
     * Rodada 12.1 — a descoberta como dado da cena: o tipo de lugar ({@code descobertas_e.json → tipos}), o modo (usar com E
     * ou chegar ao ponto), o achado e o objeto que o mostra (cheio/vazio), as moedas e os textos com os FATOS desta região
     * (rumo da vila, do posto e do lago vistos do lugar; ao lado de que ruína a árvore cresceu): nada inventado.
     */
    static JsonObject discovery(JsonObject k, RegionLayout.DiscoverySpot spot, RegionLayout.Feature f, RegionLayout L) {
        Map<String, String> facts = new java.util.HashMap<>();
        facts.put("rumo_vila", rumo(L.sx - f.x(), L.sz - f.z()));
        facts.put("rumo_posto", rumo(L.px - f.x(), L.pz - f.z()));
        facts.put("lago", L.pond == null ? "" : " Mais longe, brilha o lago a " + rumo(L.pond[0] - f.x(), L.pond[1] - f.z()) + ".");
        String ruinAsset = L.buildings.stream().filter(b -> b.role().equals("ruina")).map(RegionLayout.Building::asset).findFirst().orElse("");
        facts.put("ruina", ruinAsset.equals("ruin_tower") ? "da torre caída" : "da casa em ruína");
        JsonObject o = new JsonObject();
        o.addProperty("id", f.id());
        o.addProperty("tipo", spot.type());
        o.addProperty("lugar", k.get("lugar").getAsString());
        o.addProperty("categoria", k.get("categoria").getAsString());
        o.addProperty("modo", k.get("modo").getAsString());
        o.addProperty("x", r3(f.x()));
        o.addProperty("z", r3(f.z()));
        o.addProperty("y", r3(spot.y()));
        o.addProperty("r", r3(spot.yawDeg()));
        o.addProperty("alcance", k.get("modo").getAsString().equals("chegar") ? 0.6f : 1.6f);
        o.addProperty("placa", r3(spot.y() + 1.1f));
        o.addProperty("moedas", k.get("moedas").getAsInt());
        o.addProperty("verbo", k.get("verbo").getAsString());
        if (k.has("achado")) o.addProperty("achado", k.get("achado").getAsString());
        if (k.has("objeto")) o.add("objeto", k.getAsJsonObject("objeto").deepCopy());
        o.addProperty("texto", fill(k.get("texto").getAsString(), facts));
        o.addProperty("jaVisto", fill(k.get("jaVisto").getAsString(), facts));
        o.addProperty("diario", k.get("diario").getAsString());
        return o;
    }

    static String fill(String t, Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            int a = t.indexOf('{', i);
            if (a < 0) { out.append(t, i, t.length()); break; }
            int b = t.indexOf('}', a);
            if (b < 0) throw new IllegalArgumentException("chave aberta no texto: " + t);
            String k = t.substring(a + 1, b);
            String v = params.get(k);
            if (v == null) throw new IllegalArgumentException("parâmetro sem valor {" + k + "} em: " + t);
            out.append(t, i, a).append(v);
            i = b + 1;
        }
        return out.toString();
    }

    static void fillAll(JsonElement e, Map<String, String> params) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                if (en.getKey().equals("texto") && en.getValue().isJsonPrimitive()) o.addProperty("texto", fill(en.getValue().getAsString(), params));
                else fillAll(en.getValue(), params);
            }
        } else if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) fillAll(x, params);
    }

    static float r3(float v) { return Math.round(v * 1000f) / 1000f; }

    static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float x : v) a.add(r3(x));
        return a;
    }
}
