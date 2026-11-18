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

    public record Interactive(String rig, String name, String title, float x, float z, float yawDeg, float range, List<Line> lines) { }

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
    /** Props escritos à mão na cena (o espalhamento procedural fica de fora: ele está além do limite jogável). */
    public final List<World.Placement> handPlaced = new ArrayList<>();
    /** NPCs e criaturas não interativos da cena (rig, x, z). */
    public final List<World.Body> bodies = new ArrayList<>();

    private SceneDef(String layout) { this.layout = layout; }

    public static SceneDef load(Path assets, String layout) {
        SceneDef d = new SceneDef(layout);
        d.assets = assets;
        JsonObject doc;
        try {
            doc = JsonParser.parseString(Files.readString(assets.resolve("scenes/" + layout + ".json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cena ausente: " + layout, e);
        }
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
        if (game.has("combate")) {                                         // rodada 09: arena unida ao limite
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
                    o.get("r").getAsFloat(), o.has("alcance") ? o.get("alcance").getAsFloat() : 1.7f, List.copyOf(lines)));
        }
        Map<String, float[]> boxes = propBounds(assets, "e");
        Terrain t = Terrain.e();
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
