package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 09 — números do combate lidos de {@code assets/scenes/combate_e.json} (escrito à mão). Nada de atributos de
 * RPG: vida, tempos, alcances e as três famílias de volume — colisão de movimento (raio), hurtbox (onde se é
 * atingido) e hitbox (o que fere, só na fase ativa).
 */
public final class CombatDef {
    /** Cápsula vertical presa aos pés: de/ate = alturas (u), raio (u). */
    public record Hurtbox(float from, float to, float r) { }

    /** Um golpe do herói: dano, avanço (u), próximo da sequência, janela em que o clique encadeia, instante da troca, liberação. */
    public record Attack(String move, int damage, float activeFrom, float activeTo, float lunge, String next, float acceptFrom, float acceptTo,
                         float chainAt, float release) { }

    public float arenaX, arenaZ, arenaR, restartX, restartZ, restartYawDeg;

    // herói
    public String heroProfile, heroKit;
    public int heroHp;
    public float heroInvuln, heroStun, heroPush, attackBuffer, prepTurn, assistRange, assistAngle;
    /** Atordoamento maior quando a reação é uma saída autorada (golpe levado logo após o próprio corte). */
    public final Map<String, Float> exitStun = new LinkedHashMap<>();
    public Hurtbox heroHurt;
    public String bladeItem;
    public float bladeFrom, bladeTo, bladeR;
    public final Map<String, Attack> attacks = new LinkedHashMap<>();
    public String firstAttack;
    public float shieldRaise, shieldAngle, shieldSpeed, shieldTurn, shieldPush;

    // inimigo
    public String enemyRig, enemyName;
    public float enemyX, enemyZ, enemyYawDeg, enemyRadius;
    public int enemyHp, enemyDamage;
    public Hurtbox enemyHurt;
    public String hitPart;
    public float hitX, hitY, hitZ, hitR;
    public float sight, fov, hearing, loseAfter, leash, speed, turn, prepTurnEnemy, prepLock, range, attackAngle;
    public float alertTime, windup, strike, recover, interval, lungeSpeed, activeFrom, activeTo;
    public float enemyStun, poise, bounce, enemyPush, stuckAfter;

    // impacto
    public float pauseHit, pauseHurt, pauseBlock;

    // ------------------------------------------------------------------ rodada 09.2

    /**
     * Perfil de sensação: velocidades (u/s), passada, aceleração/frenagem (u/s²), giro (rad/s); {@code move}: fração da
     * velocidade de andar permitida em cada fase do corte (prep, exec, follow, recover); {@code turnAtk}: giro do corpo
     * (rad/s) em cada fase; {@code air}: controle no ar.
     */
    public record Feel(String name, float walk, float run, float stride, float accel, float decel, float turn, float[] move, float[] turnAtk,
                       float air) {
        /** Índice da fase do perfil de golpe: 0 prep, 1 exec, 2 follow, 3 recover. */
        public static int phase(String p) { return switch (p) { case "prep" -> 0; case "exec" -> 1; case "follow" -> 2; default -> 3; }; }
    }

    public final Map<String, Feel> feels = new LinkedHashMap<>();
    public String feelDefault = "A";

    /** Esquiva: gesto do perfil, distância (u), janelas (s do gesto), giro inicial (rad/s; 0 = mantém o rumo). */
    public record Dodge(String move, float distance, float moveFrom, float moveTo, float invFrom, float invTo, float attackFrom,
                        float releaseFrom, float turn) { }

    public Dodge roll, backstep;
    /**
     * Rodada 09.3 — gestos laterais (esquiva.lados: "esquerda", "direita"): com a mira travada a esquiva de lado usa o
     * gesto sem virar o corpo. Os números (distância, janelas, invulnerabilidade) são os de {@link #roll}.
     */
    public final Map<String, String> dodgeSides = new LinkedHashMap<>();
    public float dodgeCooldown, dodgeBuffer;
    /** Rodada 09.2 — janelas (s do golpe) das quais um corte pode virar esquiva: medidas pelo validador (esquiva.janelas). */
    public final Map<String, float[][]> dodgeWindows = new LinkedHashMap<>();

    /** Este golpe, no instante t, pode ser cortado por uma esquiva (rolar ou recuar)? */
    public boolean dodgeAllowed(String move, float t) {
        float[][] w = dodgeWindows.get(move);
        if (w == null) return false;
        for (float[] x : w) if (t >= x[0] - 1e-4f && t <= x[1] + 1e-4f) return true;
        return false;
    }

    public float jumpHeight, gravityUp, gravityDown, coyote, jumpBuffer, landTime;
    public float lockRange, lockKeep, lockHidden, lockAngleWeight;
    public boolean lockNextOnDeath;
    public int maxAttackers;
    public float attackGap, spacing, ring;

    /** Rodada 09.3 — gesto lateral ("esquerda" ou "direita") com a mira travada, ou null. */
    public String dodgeSide(String side) { return dodgeSides.get(side); }

    /**
     * Inimigo de um encontro: variante e lugar. Rodada 10: {@code key} = identidade ESTÁVEL do spawn (missões contam por ela, nunca pela ordem na lista); o 5º item
     * do JSON, ou {@code <encontro>.<índice>} quando ausente.
     */
    public record Spawn(String variant, float x, float z, float yawDeg, String key) {
        public Spawn(String variant, float x, float z, float yawDeg) { this(variant, x, z, yawDeg, null); }
    }

    public final Map<String, List<Spawn>> encounters = new LinkedHashMap<>();
    public String encounterDefault = "um";
    public String barDefault = "dano";
    public float barShow = 3f;

    // por variante (copiados para cada inimigo): escala do modelo, chefe, recurso temporário de teste
    public float enemyScale = 1f;
    public boolean enemyBoss, enemyPrototype;
    public String enemyVariant = "fungo_cardo";
    private JsonObject raw;

    /**
     * Cópia com os números do inimigo de um encontro: a variante troca campos de "inimigo", a escala multiplica raio,
     * hurtbox, hitbox e alcance, e "inicio" vira o lugar do encontro (casa e coleira do inimigo).
     */
    public CombatDef forSpawn(Spawn s) {
        JsonObject d = raw.deepCopy();
        JsonObject e = d.getAsJsonObject("inimigo");
        JsonObject v = raw.has("variantes") && raw.getAsJsonObject("variantes").has(s.variant())
                ? raw.getAsJsonObject("variantes").getAsJsonObject(s.variant()) : null;
        if (v == null) throw new IllegalArgumentException("variante de inimigo inexistente: " + s.variant());
        for (var en : v.entrySet()) if (!en.getKey().startsWith("_")) e.add(en.getKey(), en.getValue());
        JsonArray st = new JsonArray();
        st.add(s.x()); st.add(s.z()); st.add(s.yawDeg());
        e.add("inicio", st);
        CombatDef c = parse(d);
        c.enemyVariant = s.variant();
        float k = c.enemyScale;
        if (k != 1f) {
            c.enemyRadius *= k;
            c.enemyHurt = new Hurtbox(c.enemyHurt.from() * k, c.enemyHurt.to() * k, c.enemyHurt.r() * k);
            c.hitR *= k;
            c.range *= k;
        }
        return c;
    }

    /** Inimigos do encontro (o padrão se o nome não existir). */
    public List<Spawn> encounter(String name) {
        List<Spawn> l = encounters.get(name);
        return l != null ? l : encounters.getOrDefault(encounterDefault, List.of(new Spawn("fungo_cardo", enemyX, enemyZ, enemyYawDeg)));
    }

    public static CombatDef load(Path assets, String name) {
        Path f = assets.resolve("scenes/" + name + ".json");
        try {
            com.google.gson.JsonElement doc = JsonParser.parseString(Files.readString(f));
            finiteEverywhere(doc, "");
            return parse(doc.getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("combate ilegível: " + f, e);
        } catch (RuntimeException e) {                    // campo ausente, tipo errado, NaN: arquivo do projeto quebrado
            throw new IllegalStateException("combate inválido em " + f + ": " + e.getMessage(), e);
        }
    }

    static float f(JsonObject o, String k) {
        if (!o.has(k)) throw new IllegalArgumentException("combate_e.json: falta \"" + k + "\"");
        return finite(o.get(k).getAsFloat(), k);
    }

    /**
     * O leitor de JSON aceita NaN e Infinity; no combate eles são erro do arquivo em QUALQUER campo (muitos são lidos
     * direto por {@code getAsInt}/{@code getAsFloat}, e {@code (int) NaN} vira 0 em silêncio).
     */
    static void finiteEverywhere(com.google.gson.JsonElement e, String path) {
        if (e.isJsonObject()) for (var en : e.getAsJsonObject().entrySet()) finiteEverywhere(en.getValue(), path + "." + en.getKey());
        else if (e.isJsonArray()) for (int i = 0; i < e.getAsJsonArray().size(); i++) finiteEverywhere(e.getAsJsonArray().get(i), path + "[" + i + "]");
        else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && !Double.isFinite(e.getAsDouble()))
            throw new IllegalArgumentException("\"" + path.substring(1) + "\" não é um número finito");
    }

    /** NaN e infinito no combate são erro do arquivo, nunca um número que "funciona" em silêncio. */
    static float finite(float v, String k) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("combate_e.json: \"" + k + "\" não é um número finito (" + v + ")");
        return v;
    }

    static float[] floats(JsonArray a) {
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = finite(a.get(i).getAsFloat(), "lista");
        return r;
    }

    static Dodge dodge(JsonObject o) {
        JsonArray mv = o.getAsJsonArray("desloca"), inv = o.getAsJsonArray("invulneravel");
        return new Dodge(o.get("golpe").getAsString(), f(o, "distancia"), mv.get(0).getAsFloat(), mv.get(1).getAsFloat(),
                inv.get(0).getAsFloat(), inv.get(1).getAsFloat(), f(o, "atacarDe"), f(o, "liberaDe"), f(o, "giro"));
    }

    static Hurtbox hurt(JsonObject o) { return new Hurtbox(f(o, "de"), f(o, "ate"), f(o, "raio")); }

    static CombatDef parse(JsonObject d) {
        CombatDef c = new CombatDef();
        JsonObject a = d.getAsJsonObject("arena");
        c.arenaX = a.getAsJsonArray("centro").get(0).getAsFloat();
        c.arenaZ = a.getAsJsonArray("centro").get(1).getAsFloat();
        c.arenaR = f(a, "raio");
        JsonArray rs = a.getAsJsonArray("recomeco");
        c.restartX = rs.get(0).getAsFloat(); c.restartZ = rs.get(1).getAsFloat(); c.restartYawDeg = rs.get(2).getAsFloat();

        JsonObject h = d.getAsJsonObject("heroi");
        c.heroProfile = h.get("perfil").getAsString();
        c.heroKit = h.get("kit").getAsString();
        c.heroHp = h.get("vida").getAsInt();
        c.heroInvuln = f(h, "invulneravel"); c.heroStun = f(h, "atordoado"); c.heroPush = f(h, "empurrao");
        if (h.has("atordoadoSaida")) for (var en : h.getAsJsonObject("atordoadoSaida").entrySet())
            if (!en.getKey().startsWith("_")) c.exitStun.put(en.getKey(), en.getValue().getAsFloat());
        c.heroHurt = hurt(h.getAsJsonObject("hurtbox"));
        JsonObject bl = h.getAsJsonObject("lamina");
        c.bladeItem = bl.get("item").getAsString(); c.bladeFrom = f(bl, "de"); c.bladeTo = f(bl, "ate"); c.bladeR = f(bl, "raio");
        JsonObject gs = h.getAsJsonObject("golpes");
        for (String mv : gs.keySet()) {
            if (mv.startsWith("_")) continue;
            JsonObject g = gs.getAsJsonObject(mv);
            JsonArray act = g.getAsJsonArray("ativo");
            JsonArray acc = g.has("aceita") ? g.getAsJsonArray("aceita") : null;
            c.attacks.put(mv, new Attack(mv, g.get("dano").getAsInt(), act.get(0).getAsFloat(), act.get(1).getAsFloat(), f(g, "avanco"), g.has("encadeia") ? g.get("encadeia").getAsString() : null,
                    acc != null ? acc.get(0).getAsFloat() : Float.NaN, acc != null ? acc.get(1).getAsFloat() : Float.NaN,
                    g.has("encadeiaEm") ? f(g, "encadeiaEm") : Float.NaN, f(g, "libera")));
            if (c.firstAttack == null) c.firstAttack = mv;
        }
        c.attackBuffer = f(h, "bufferAtaque");
        c.prepTurn = f(h, "giroPreparo");
        c.assistRange = f(h.getAsJsonObject("assistencia"), "alcance");
        c.assistAngle = (float) Math.toRadians(f(h.getAsJsonObject("assistencia"), "angulo"));
        JsonObject es = h.getAsJsonObject("escudo");
        c.shieldRaise = f(es, "subida"); c.shieldAngle = (float) Math.toRadians(f(es, "angulo"));
        c.shieldSpeed = f(es, "velocidade"); c.shieldTurn = f(es, "giro"); c.shieldPush = f(es, "empurrao");

        JsonObject e = d.getAsJsonObject("inimigo");
        c.enemyRig = e.get("rig").getAsString();
        c.enemyName = e.get("nome").getAsString();
        JsonArray st = e.getAsJsonArray("inicio");
        c.enemyX = st.get(0).getAsFloat(); c.enemyZ = st.get(1).getAsFloat(); c.enemyYawDeg = st.get(2).getAsFloat();
        c.enemyHp = e.get("vida").getAsInt();
        c.enemyRadius = f(e, "raio");
        c.enemyHurt = hurt(e.getAsJsonObject("hurtbox"));
        JsonObject hb = e.getAsJsonObject("hitbox");
        c.hitPart = hb.get("parte").getAsString();
        JsonArray pt = hb.getAsJsonArray("ponto");
        c.hitX = pt.get(0).getAsFloat(); c.hitY = pt.get(1).getAsFloat(); c.hitZ = pt.get(2).getAsFloat();
        c.hitR = f(hb, "raio");
        c.sight = f(e, "visao"); c.fov = (float) Math.toRadians(f(e, "campo")); c.hearing = f(e, "ouvir");
        c.loseAfter = f(e, "perde"); c.leash = f(e, "coleira"); c.speed = f(e, "velocidade"); c.turn = f(e, "giro");
        c.prepTurnEnemy = f(e, "giroPreparo"); c.prepLock = f(e, "travaPreparo");
        c.range = f(e, "alcance"); c.attackAngle = (float) Math.toRadians(f(e, "anguloAtaque"));
        c.alertTime = f(e, "alerta"); c.windup = f(e, "preparo"); c.strike = f(e, "golpe"); c.recover = f(e, "recuperacao");
        c.interval = f(e, "intervalo"); c.lungeSpeed = f(e, "avanco");
        c.activeFrom = e.getAsJsonArray("ativo").get(0).getAsFloat(); c.activeTo = e.getAsJsonArray("ativo").get(1).getAsFloat();
        c.enemyDamage = e.get("dano").getAsInt();
        c.enemyStun = f(e, "atordoado"); c.poise = f(e, "imune"); c.bounce = f(e, "ricochete"); c.enemyPush = f(e, "empurrao");
        c.stuckAfter = f(e, "preso");

        JsonObject im = d.getAsJsonObject("impacto");
        c.pauseHit = f(im, "pausaAcerto"); c.pauseHurt = f(im, "pausaDano"); c.pauseBlock = f(im, "pausaDefesa");
        c.raw = d;
        c.enemyScale = e.has("escala") ? f(e, "escala") : 1f;
        c.enemyBoss = e.has("chefe") && e.get("chefe").getAsBoolean();
        c.enemyPrototype = e.has("prototipo") && e.get("prototipo").getAsBoolean();

        // rodada 09.2 (blocos opcionais: sem eles, o comportamento é o da 09.1)
        if (d.has("sensacao")) {
            JsonObject sn = d.getAsJsonObject("sensacao");
            c.feelDefault = sn.get("padrao").getAsString();
            JsonObject ps = sn.getAsJsonObject("perfis");
            for (String k : ps.keySet()) {
                JsonObject o = ps.getAsJsonObject(k);
                c.feels.put(k, new Feel(k, f(o, "andar"), f(o, "correr"), f(o, "passada"), f(o, "acel"), f(o, "freio"), f(o, "giro"),
                        floats(o.getAsJsonArray("golpe")), floats(o.getAsJsonArray("giroGolpe")), f(o, "ar")));
            }
        }
        if (c.feels.isEmpty()) c.feels.put("A", new Feel("A", Player.WALK, Player.RUN, 1f, Player.ACCEL, Player.DECEL, Player.TURN_RATE,
                new float[4], new float[]{c.prepTurn, 0, 0, 0}, 0.45f));
        if (!c.feels.containsKey(c.feelDefault)) c.feelDefault = c.feels.keySet().iterator().next();
        if (d.has("esquiva")) {
            JsonObject es2 = d.getAsJsonObject("esquiva");
            c.roll = dodge(es2.getAsJsonObject("rolar"));
            c.backstep = dodge(es2.getAsJsonObject("recuo"));
            c.dodgeCooldown = f(es2, "recarga");
            if (es2.has("lados")) {
                JsonObject ld = es2.getAsJsonObject("lados");
                for (String k : ld.keySet()) if (!k.startsWith("_")) c.dodgeSides.put(k, ld.get(k).getAsString());
            }
            c.dodgeBuffer = f(es2, "buffer");
            JsonObject jw = es2.getAsJsonObject("janelas");
            for (String k : jw.keySet()) {
                if (k.startsWith("_")) continue;
                JsonArray arr = jw.getAsJsonArray(k);
                float[][] w = new float[arr.size()][];
                for (int i = 0; i < w.length; i++) w[i] = floats(arr.get(i).getAsJsonArray());
                c.dodgeWindows.put(k, w);
            }
        }
        if (d.has("salto")) {
            JsonObject j = d.getAsJsonObject("salto");
            c.jumpHeight = f(j, "altura"); c.gravityUp = f(j, "gravidadeSobe"); c.gravityDown = f(j, "gravidadeDesce");
            c.coyote = f(j, "coyote"); c.jumpBuffer = f(j, "buffer"); c.landTime = f(j, "pouso");
        }
        if (d.has("mira")) {
            JsonObject m = d.getAsJsonObject("mira");
            c.lockRange = f(m, "alcance"); c.lockKeep = f(m, "solta"); c.lockHidden = f(m, "oculto"); c.lockAngleWeight = f(m, "angulo");
            c.lockNextOnDeath = m.get("aoMorrer").getAsString().equals("proximo");
        }
        if (d.has("grupo")) {
            JsonObject g = d.getAsJsonObject("grupo");
            c.maxAttackers = g.get("atacantes").getAsInt(); c.attackGap = f(g, "intervalo"); c.spacing = f(g, "espaco"); c.ring = f(g, "anel");
        } else { c.maxAttackers = 99; c.spacing = 0f; c.ring = 0f; }
        if (d.has("encontros")) {
            JsonObject en = d.getAsJsonObject("encontros");
            c.encounterDefault = en.get("padrao").getAsString();
            for (String k : en.keySet()) {
                if (k.startsWith("_") || k.equals("padrao")) continue;
                List<Spawn> l = new ArrayList<>();
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (var x : en.getAsJsonArray(k)) {
                    JsonArray a2 = x.getAsJsonArray();
                    String key = a2.size() > 4 ? a2.get(4).getAsString() : k + "." + l.size();
                    if (!keys.add(key)) throw new IllegalArgumentException("encontro " + k + ": identidade repetida " + key);
                    l.add(new Spawn(a2.get(0).getAsString(), a2.get(1).getAsFloat(), a2.get(2).getAsFloat(), a2.get(3).getAsFloat(), key));
                }
                c.encounters.put(k, l);
            }
        }
        if (d.has("interface")) {
            JsonObject ui = d.getAsJsonObject("interface");
            c.barDefault = ui.get("barraPadrao").getAsString();
            c.barShow = f(ui, "mostra");
        }
        return c;
    }
}
