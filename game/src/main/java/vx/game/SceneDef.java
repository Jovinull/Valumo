package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import vx.viewer.Terrain;
import vx.viewer.World;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 08 — definição da cena jogável lida do JSON (sem OpenGL): a parte "jogo" (início, limite,
 * personagens interativos e falas) e, para os testes, os props posicionados à mão com as caixas tiradas do
 * {@code props.json}. No jogo de verdade os props vêm de {@link World#placements()} (o que foi desenhado).
 */
public final class SceneDef {
    public record Line(String text, String expression) { }

    /** {@code height}: altura do personagem no mundo (u), lida do rig.json — a câmera de conversa enquadra por ela. */
    /**
     * NPC com quem se fala. Rodada 12.2 (moradores de ofício, opcionais no JSON): {@code y} = altura do ponto acima do chão
     * (a laje da oficina: ele fica de pé no chão de colisão, não afundado nela); {@code gesture} = de tempos em tempos toca
     * o gesto de ofício do perfil de animação dele (só desenho); {@code trade} = o ofício do conjunto em que ele trabalha
     * (null = morador comum): o ponto dele é AUTORADO no catálogo (spawn ou vaga), conferido à parte.
     */
    public record Interactive(String rig, String name, String title, float x, float z, float yawDeg, float range, List<Line> lines,
                              float height, float y, boolean gesture, String trade) {
        /** Rodada 10 — id do NPC (falas por contexto, missões): o nome da pasta do rig ({@code kelyra}). */
        public String id() { return rig.substring(rig.lastIndexOf('/') + 1); }
    }

    /**
     * Rodada 10 — objeto do mundo com estado (o marco de luz): id estável, posição, rumo, alcance do E, os dois modelos
     * (props gerados: apagado/aceso), raio do corpo sólido e altura da placa de interação.
     */
    public record WorldObject(String id, float x, float z, float yawDeg, float range, String modelOff, String modelOn, float radius,
                              float plateY) { }

    /**
     * Rodada 12 — descoberta de exploração (o ponto da ruína): id estável (derivado do POI), nome do lugar, posição, alcance
     * do E, altura da placa, moedas (uma vez), o verbo da placa e as frases (a primeira vez e as seguintes).
     */
    public record Discovery(String id, String name, float x, float z, float range, float plateY, int coins, String verb, String text,
                            String seen, String type, String category, String mode, float y, float yawDeg, String find, String modelFull,
                            String modelEmpty, String journal, String verbSeen) {
        /** Rodada 12.1: chegar ao ponto (o mirante) em vez de usar com E. */
        public boolean arrive() { return "chegar".equals(mode); }
    }

    public final List<Discovery> discoveries = new ArrayList<>();

    /** Altura do rig (u): {@code meta.worldUnits} (NPC com altura narrativa) ou corpo × voxel; 1,25 se não houver. */
    static float rigHeight(Path assets, String rig) {
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(assets.resolve(rig).resolve("rig.json"))).getAsJsonObject();
            JsonObject meta = doc.has("meta") ? doc.getAsJsonObject("meta") : new JsonObject();
            if (meta.has("worldUnits")) return meta.get("worldUnits").getAsFloat();
            if (meta.has("bodyHeightVoxels") && doc.has("voxelSize"))
                return meta.get("bodyHeightVoxels").getAsFloat() * doc.get("voxelSize").getAsFloat();
        } catch (Exception ignored) { }
        return 1.25f;
    }

    public final String layout;
    /** Raiz dos assets (o combate carrega os esqueletos de lá). */
    public Path assets;
    public float startX, startZ, startYawDeg;
    public float limitX, limitZ, limitR = 12f;
    /** Rodada 09: círculos jogáveis a mais (arena do combate), unidos ao limite da vila. */
    public final List<float[]> extraLimits = new ArrayList<>();
    /** Rodada 09: combate da cena (null = cena sem combate). */
    public CombatDef combat;
    public final List<Interactive> interactives = new ArrayList<>();
    /** Rodada 10 — objetos com estado, e os arquivos de missões e falas da cena (null = cena sem missões). */
    public final List<WorldObject> objects = new ArrayList<>();
    public String questsFile, dialoguesFile;
    /** Props escritos à mão na cena (o espalhamento procedural fica de fora: ele está além do limite jogável). */
    public final List<World.Placement> handPlaced = new ArrayList<>();
    /** NPCs e criaturas não interativos da cena (rig, x, z). */
    public final List<World.Body> bodies = new ArrayList<>();

    private SceneDef(String layout) { this.layout = layout; }

    public static SceneDef load(Path assets, String layout) {
        JsonObject doc;
        try {
            doc = JsonParser.parseString(Files.readString(assets.resolve("scenes/" + layout + ".json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cena ausente: " + layout, e);
        }
        return fromDoc(assets, layout, doc, null, World.LEGACY);
    }

    /** Rodada 11 — relevo da cena (o legado para as cenas em arquivo; o gerado para o mundo procedural). */
    public vx.viewer.TerrainField field = World.LEGACY;
    /** Rodada 11 — a região procedural de onde a cena saiu (missão instanciada, vínculos, semente); null = cena em arquivo. */
    public ProceduralScene procedural;

    /**
     * Rodada 11 — a cena a partir de um documento já montado (o mundo procedural gera o mesmo formato de
     * {@code assets/scenes/jogo_vila_e.json}). {@code combatDoc}: o combate já ajustado ao lugar (arena e encontros
     * gerados); null = {@code jogo.combate} lido de {@code assets/scenes}.
     */
    public static SceneDef fromDoc(Path assets, String layout, JsonObject doc, JsonObject combatDoc, vx.viewer.TerrainField field) {
        SceneDef d = new SceneDef(layout);
        d.assets = assets;
        d.field = field;
        JsonObject game = doc.getAsJsonObject("jogo");
        if (game == null) throw new IllegalArgumentException("a cena " + layout + " não tem a seção \"jogo\"");
        JsonArray s = game.getAsJsonArray("inicio");
        d.startX = s.get(0).getAsFloat();
        d.startZ = s.get(1).getAsFloat();
        d.startYawDeg = s.get(2).getAsFloat();
        JsonObject lim = game.getAsJsonObject("limite");
        d.limitX = lim.getAsJsonArray("centro").get(0).getAsFloat();
        d.limitZ = lim.getAsJsonArray("centro").get(1).getAsFloat();
        d.limitR = lim.get("raio").getAsFloat();
        if (game.has("limitesExtras")) for (JsonElement el : game.getAsJsonArray("limitesExtras")) {   // rodada 11: trilha gerada
            JsonArray c = el.getAsJsonArray();
            d.extraLimits.add(new float[]{c.get(0).getAsFloat(), c.get(1).getAsFloat(), c.get(2).getAsFloat()});
        }
        if (combatDoc != null) {                                           // rodada 11: arena e encontros gerados
            d.combat = CombatDef.parse(combatDoc);
            d.extraLimits.add(new float[]{d.combat.arenaX, d.combat.arenaZ, d.combat.arenaR});
        } else if (game.has("combate")) {                                  // rodada 09: arena unida ao limite
            d.combat = CombatDef.load(assets, game.get("combate").getAsString());
            d.extraLimits.add(new float[]{d.combat.arenaX, d.combat.arenaZ, d.combat.arenaR});
        }
        for (JsonElement el : game.getAsJsonArray("interativos")) {
            JsonObject o = el.getAsJsonObject();
            List<Line> lines = new ArrayList<>();
            for (JsonElement l : o.getAsJsonArray("falas")) {
                JsonObject lo = l.getAsJsonObject();
                lines.add(new Line(lo.get("texto").getAsString(), lo.has("expressao") ? lo.get("expressao").getAsString() : null));
            }
            d.interactives.add(new Interactive(o.get("rig").getAsString(), o.get("nome").getAsString(),
                    o.has("titulo") ? o.get("titulo").getAsString() : null, o.get("x").getAsFloat(), o.get("z").getAsFloat(),
                    o.get("r").getAsFloat(), o.has("alcance") ? o.get("alcance").getAsFloat() : 1.7f, List.copyOf(lines),
                    rigHeight(assets, o.get("rig").getAsString()), o.has("y") ? o.get("y").getAsFloat() : 0f,
                    o.has("gesto") && o.get("gesto").getAsString().equals("oficio"), o.has("oficio") ? o.get("oficio").getAsString() : null));
        }
        if (game.has("missoes")) d.questsFile = "scenes/" + game.get("missoes").getAsString() + ".json";
        if (game.has("dialogos")) d.dialoguesFile = "scenes/" + game.get("dialogos").getAsString() + ".json";
        if (game.has("descobertas")) for (JsonElement el : game.getAsJsonArray("descobertas")) {
            JsonObject o = el.getAsJsonObject();
            JsonObject obj = o.has("objeto") ? o.getAsJsonObject("objeto") : null;
            d.discoveries.add(new Discovery(o.get("id").getAsString(), o.get("lugar").getAsString(), o.get("x").getAsFloat(), o.get("z").getAsFloat(),
                    o.get("alcance").getAsFloat(), o.get("placa").getAsFloat(), o.get("moedas").getAsInt(), o.get("verbo").getAsString(),
                    o.get("texto").getAsString(), o.get("jaVisto").getAsString(), o.get("tipo").getAsString(), o.get("categoria").getAsString(),
                    o.get("modo").getAsString(), o.get("y").getAsFloat(), o.get("r").getAsFloat(), o.has("achado") ? o.get("achado").getAsString() : null,
                    obj != null ? obj.get("cheio").getAsString() : null, obj != null ? obj.get("vazio").getAsString() : null,
                    o.get("diario").getAsString(), o.has("verboVisto") ? o.get("verboVisto").getAsString() : o.get("verbo").getAsString()));
        }
        if (game.has("objetos")) for (JsonElement el : game.getAsJsonArray("objetos")) {
            JsonObject o = el.getAsJsonObject();
            JsonObject m = o.getAsJsonObject("modelos");
            d.objects.add(new WorldObject(o.get("id").getAsString(), o.get("x").getAsFloat(), o.get("z").getAsFloat(), o.get("r").getAsFloat(),
                    o.get("alcance").getAsFloat(), m.get("apagado").getAsString(), m.get("aceso").getAsString(), o.get("raio").getAsFloat(),
                    o.get("placa").getAsFloat()));
        }
        Map<String, float[]> boxes = propBounds(assets, "e");
        Terrain t = Terrain.of(field);
        for (JsonElement el : doc.getAsJsonArray("props")) {
            JsonObject o = el.getAsJsonObject();
            String n = o.get("n").getAsString();
            float[] b = boxes.get(n);
            if (b == null) continue;
            float x = o.get("x").getAsFloat(), z = o.get("z").getAsFloat(), dy = o.has("dy") ? o.get("dy").getAsFloat() : 0f;
            boolean abs = o.has("abs") && o.get("abs").getAsBoolean();
            float y = abs ? dy : t.groundAt(x, z) + dy;
            float r = o.has("r") ? o.get("r").getAsFloat() : 0f;
            d.handPlaced.add(new World.Placement(n, new Matrix4f().translate(x, y, z).rotateY((float) Math.toRadians(r)), b));
        }
        for (String key : new String[]{"npcs", "creatures"}) {
            if (!doc.has(key)) continue;
            for (JsonElement el : doc.getAsJsonArray(key)) {
                JsonObject o = el.getAsJsonObject();
                String name = o.has("rig") ? o.get("rig").getAsString() : "creatures/e/" + o.get("n").getAsString();
                if (o.has("lift") && o.get("lift").getAsFloat() > 0.3f) continue;          // voando: não ocupa o chão
                d.bodies.add(new World.Body(name, o.get("x").getAsFloat(), o.get("z").getAsFloat(), (float) Math.toRadians(o.get("r").getAsFloat())));
            }
        }
        return d;
    }

    /** Caixas (espaço do modelo) dos props do estilo, pelo props.json: origem·vs até (origem+tamanho)·vs. */
    static Map<String, float[]> propBounds(Path assets, String style) {
        Map<String, float[]> out = new HashMap<>();
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(assets.resolve("environment/" + style + "/props.json"))).getAsJsonObject();
            for (String n : doc.keySet()) {
                JsonObject e = doc.getAsJsonObject(n);
                if (!e.has("origin")) continue;
                float vs = e.get("voxelSize").getAsFloat();
                JsonArray o = e.getAsJsonArray("origin"), sz = e.getAsJsonArray("sizeVoxels");
                float[] b = new float[6];
                for (int i = 0; i < 3; i++) {
                    b[i] = o.get(i).getAsFloat() * vs;
                    b[3 + i] = (o.get(i).getAsFloat() + sz.get(i).getAsFloat()) * vs;
                }
                out.put(n, b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
