package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.Mesh;
import vx.engine.VoxGrid;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 07 — compositor de personagens em TEMPO DE EXECUÇÃO.
 *
 * <p>Pipeline: {@link Appearance} validada → {@link #compose} (CPU: carrega componentes, recolore por canal
 * semântico, aplica a variante da cobertura de cabeça e FUNDE as peças rígidas do mesmo referencial — rosto +
 * cabelo + capuz viram uma grade só por expressão) → {@link #build} (malha com fusão exata de faces e envio à
 * GPU). A fusão em grade antes do meshing elimina as faces internas e a costura entre cabeça e cabelo: um
 * preset sem mudanças gera exatamente os mesmos voxels — portanto a mesma malha — que o rig canônico.
 * Peças com pivô próprio ({@code hat}, {@code tail}) continuam partes separadas (movimento secundário).
 *
 * <p>Cache por {@link Appearance#signature()}: compor é feito quando a aparência muda, nunca por quadro; ao
 * sair do cache as malhas são fechadas (sem vazamento de GPU).
 */
public final class Composer {
    /** Peça composta (pivô em voxels do modelo). */
    public static final class Piece {
        public String name, parent;
        public float[] pivot;
        public VoxGrid grid;
        public final Map<String, VoxGrid> alts = new LinkedHashMap<>();
    }

    /** Resultado da composição na CPU (sem OpenGL; usado também pelos testes). */
    public static final class ComposedRig {
        public String name, anim;
        public float voxelSize;
        public boolean affine;
        public JsonObject meta;
        public final List<Piece> pieces = new ArrayList<>();
        public long composeNanos;

        public Piece piece(String n) {
            for (Piece p : pieces) if (p.name.equals(n)) return p;
            return null;
        }
    }

    static final String[] EXPRS = {"neutral", "joy", "determined", "surprise"};
    /**
     * Rodada 09 — equipamento de combate (pasta de um rig com as peças de item, ex.: "characters/r4/adventurer_shield").
     * Troca SÓ as peças de item (arma, escudo, arco, flecha), o perfil de animação e os volumes de item do validador; corpo,
     * rosto, cabelo, cobertura e cores ficam os da aparência. null = sem troca (criador, presets canônicos, gate 0 pixel).
     */
    public volatile String kit;
    static final java.util.Set<String> ITEM_PARTS = java.util.Set.of("weapon", "shield", "bow", "arrow");
    static final java.util.Set<String> ITEM_TAGS = java.util.Set.of("blade", "handle", "shield", "bow", "arrow", "staff");
    private final Path assets;
    public final ComponentCatalog catalog;
    private final Map<String, VoxGrid> gridCache = new HashMap<>();          // arquivos já lidos (imutáveis)
    private final Map<String, JsonObject> rigDocs = new HashMap<>();
    private final LinkedHashMap<String, CharacterModel> models;
    /** Métricas da última construção (ms): compor (CPU), malha (CPU) e envio (GPU). */
    public double lastComposeMs, lastMeshMs, lastUploadMs;
    public int lastFaces, cacheHits, cacheMisses;

    public Composer(Path assets, int cacheSize) {
        this.assets = assets;
        this.catalog = ComponentCatalog.load(assets);
        this.models = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, CharacterModel> e) {
                if (size() <= cacheSize) return false;
                release(e.getValue());
                return true;
            }
        };
    }

    // ------------------------------------------------------------------ CPU

    public ComposedRig compose(Appearance a) {
        long t0 = System.nanoTime();
        a.validate(catalog);
        ComponentCatalog.Preset pre = catalog.presets.get(a.preset);
        JsonObject base = rigDoc(pre.body);
        JsonObject face = catalog.components.get(catalog.face(a)).doc;
        ComponentCatalog.Component hairC = catalog.components.get(catalog.hair(a));
        String cover = catalog.cover(a), variant = catalog.coverVariant(a);
        int skinT = rgb(catalog.skin(a)), hairT = rgb(catalog.hairColor(a)), irisT = rgb(catalog.iris(a));
        Map<String, JsonObject> feetParts = new java.util.HashMap<>();    // rodada 09.4: peças que as botas substituem
        JsonObject feetDoc = catalog.components.get(catalog.feet(a)).doc;
        if (feetDoc.has("porCorpo") && feetDoc.getAsJsonObject("porCorpo").has(a.preset))
            for (JsonElement el : feetDoc.getAsJsonObject("porCorpo").getAsJsonObject(a.preset).getAsJsonArray("partes"))
                feetParts.put(el.getAsJsonObject().get("nome").getAsString(), el.getAsJsonObject());

        // cabelo (massa da cabeça), recolorido pela rampa
        JsonObject hh = hairC.doc.getAsJsonObject("cabeca");
        VoxGrid hair = load(hh).copy();
        recolor(hair, mask(hh), "cabelo", rgb(hairC.doc.get("base").getAsString()), hairT);
        int hairTop = Integer.MIN_VALUE;
        for (long k : hair.cells().keySet()) hairTop = Math.max(hairTop, VoxGrid.ky(k));
        VoxGrid hood = null;
        List<Piece> coverPieces = new ArrayList<>();
        if (cover != null) {
            JsonObject cd = catalog.components.get(cover).doc;
            if (cd.has("cabeca")) hood = load(cd.getAsJsonObject("cabeca"));
            if (cd.has("partes")) for (JsonElement el : cd.getAsJsonArray("partes")) coverPieces.add(piece(el.getAsJsonObject(), null, 0, 0));
            if ("sob_aba".equals(variant)) {
                // assento: o chapéu desce até a aba encostar no ponto mais alto da cabeça (crânio ou cabelo)
                // sob ela; depois o cabelo acima da aba some. Nada é esticado.
                VoxGrid headTop = load(face.getAsJsonObject("exprs").getAsJsonObject("neutral")).copy().overlay(hair);
                int drop = seat(headTop, coverPieces.get(0).grid);
                if (drop > 0) for (Piece cp : coverPieces) shiftY(cp, -drop);
                r0Seat = drop;
                underBrim(hair, coverPieces.get(0).grid);
            }
            else if ("faixa".equals(variant)) band(hair, cd.getAsJsonObject("faixa"));
        }

        ComposedRig r = new ComposedRig();
        r.name = base.get("name").getAsString() + " — " + a.signature();
        r.anim = base.get("anim").getAsString();
        r.voxelSize = base.get("voxelSize").getAsFloat();
        r.affine = base.has("affineWind") && base.get("affineWind").getAsBoolean();
        r.meta = base.getAsJsonObject("meta").deepCopy();
        for (JsonElement pe : base.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            String n = p.get("name").getAsString();
            if (n.equals("tail") || n.equals("hat")) continue;          // vêm do cabelo / da cobertura
            Piece piece = new Piece();
            piece.name = n;
            piece.parent = p.get("parent").isJsonNull() ? null : p.get("parent").getAsString();
            piece.pivot = floats(p.getAsJsonArray("pivot"));
            if (n.equals("head")) {
                for (String ex : EXPRS) {
                    // rodada 09.4: formato dos olhos ≠ o nativo do rosto → as expressões daquela variante (mesmo pintor)
                    JsonObject exprs = face.getAsJsonObject("exprs");
                    String eyes = catalog.eyes(a);
                    if (eyes != null && face.has("variantesOlhos") && face.getAsJsonObject("variantesOlhos").has(eyes))
                        exprs = face.getAsJsonObject("variantesOlhos").getAsJsonObject(eyes).getAsJsonObject("exprs");
                    JsonObject fe = exprs.getAsJsonObject(ex);
                    VoxGrid g = load(fe).copy();
                    Map<Long, Integer> fm = mask(fe);
                    recolor(g, fm, "pele", rgb(face.get("pele").getAsString()), skinT);
                    recolor(g, fm, "sobrancelha", rgb(face.get("sobrancelhaBase").getAsString()), hairT);
                    recolor(g, fm, "iris", rgb(face.get("iris").getAsString()), irisT);
                    g.overlay(hair);                                       // mesma ordem do gerador: rosto, cabelo, capuz
                    if (hood != null) g.overlay(hood);
                    if (ex.equals("neutral")) piece.grid = g;
                    else piece.alts.put(ex, g);
                }
            } else if (feetParts.containsKey(n)) {                         // rodada 09.4: botas trocam as canelas
                piece.grid = load(feetParts.get(n));
            } else {
                piece.grid = load(p, pre.body);
                JsonElement bm = pre.bodyMasks.get(n);
                if (bm != null && skinT != rgb(pre.skin)) {
                    piece.grid = piece.grid.copy();
                    recolor(piece.grid, mask(bm.getAsJsonObject()), "pele", rgb(pre.skin), skinT);
                }
            }
            r.pieces.add(piece);
        }
        // partes próprias do cabelo (rabo); integração da 09.4: o cabelo pode ter partes POR CORPO ("porCorpo", como as
        // botas) — o "Preso" usa a trança canônica na exploradora e um rabo centrado nos outros corpos
        JsonArray hairParts = hairC.doc.has("porCorpo") && hairC.doc.getAsJsonObject("porCorpo").has(a.preset)
                ? hairC.doc.getAsJsonObject("porCorpo").getAsJsonObject(a.preset).getAsJsonArray("partes")
                : hairC.doc.has("partes") ? hairC.doc.getAsJsonArray("partes") : null;
        if (hairParts != null)
            for (JsonElement el : hairParts)
                r.pieces.add(piece(el.getAsJsonObject(), "cabelo", rgb(hairC.doc.get("base").getAsString()), hairT));
        r.pieces.addAll(coverPieces);
        if (hairC.doc.has("meta")) for (var e : hairC.doc.getAsJsonObject("meta").entrySet()) r.meta.add(e.getKey(), e.getValue());
        int neck = catalog.headPivot[1];
        if (!catalog.hair(a).equals(pre.hair)) {                           // proporção com o cabelo novo (chapéu fora)
            int headH = hairTop - neck + 1;
            r.meta.addProperty("headsTall", Math.round((hairTop + 1) * 100.0 / headH) / 100.0);
            r.meta.addProperty("headH", headH);
            r.meta.addProperty("bodyHeightVoxels", hairTop + 1);
        }
        r.meta.addProperty("appearance", a.signature());
        String k = kit;
        if (k != null) equip(r, k);
        r.composeNanos = System.nanoTime() - t0;
        return r;
    }

    /** Troca as peças de item pelas do rig {@code kitDir} (mesmos pivôs de braço nos três corpos; ver jogo.md). */
    void equip(ComposedRig r, String kitDir) {
        JsonObject kd = rigDoc(kitDir);
        java.util.Set<String> gone = new java.util.HashSet<>(ITEM_PARTS);
        boolean more = true;
        while (more) {                                                     // e o que pende deles (cordas do arco)
            more = false;
            for (Piece p : r.pieces) if (!gone.contains(p.name) && p.parent != null && gone.contains(p.parent)) { gone.add(p.name); more = true; }
        }
        r.pieces.removeIf(p -> gone.contains(p.name));
        for (JsonElement pe : kd.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            String n = p.get("name").getAsString();
            if (!ITEM_PARTS.contains(n)) continue;
            Piece piece = new Piece();
            piece.name = n;
            piece.parent = p.get("parent").getAsString();
            piece.pivot = floats(p.getAsJsonArray("pivot"));
            piece.grid = load(p, kitDir);
            r.pieces.add(piece);
        }
        JsonObject km = kd.getAsJsonObject("meta");
        r.meta.add("weaponProfile", km.get("weaponProfile"));
        r.meta.addProperty("weaponMode", "sword");
        r.meta.remove("weaponRest");
        com.google.gson.JsonArray vols = new com.google.gson.JsonArray();
        for (JsonElement v : r.meta.getAsJsonArray("volumes"))
            if (!ITEM_TAGS.contains(v.getAsJsonObject().get("tag").getAsString())) vols.add(v);
        for (JsonElement v : km.getAsJsonArray("volumes"))
            if (ITEM_TAGS.contains(v.getAsJsonObject().get("tag").getAsString())) vols.add(v);
        r.meta.add("volumes", vols);
        r.name = r.name + " + " + kitDir;
    }

    /**
     * Assento: quanto o chapéu desce até a aba encostar na cabeça (crânio ou cabelo), medido na metade da FRENTE
     * (z ≥ 1) — a aba da maga é inclinada para trás e o cabelo de trás de qualquer estilo passa da altura dela;
     * ali ele fica por dentro da aba. Nunca sobe o chapéu.
     */
    static int seat(VoxGrid head, VoxGrid hat) {
        Map<Long, Integer> bottom = new HashMap<>(), top = new HashMap<>();
        for (long k : hat.cells().keySet()) bottom.merge(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)), VoxGrid.ky(k), Math::min);
        for (long k : head.cells().keySet()) top.merge(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)), VoxGrid.ky(k), Math::max);
        int gap = Integer.MAX_VALUE;
        for (var e : bottom.entrySet()) {
            Integer t = top.get(e.getKey());
            if (t != null && VoxGrid.kz(e.getKey()) >= 1) gap = Math.min(gap, e.getValue() - t - 1);
        }
        return gap == Integer.MAX_VALUE ? 0 : Math.max(0, gap);
    }

    static void shiftY(Piece p, int dy) {
        VoxGrid g = new VoxGrid();
        for (var e : p.grid.cells().entrySet())
            g.put(VoxGrid.key(VoxGrid.kx(e.getKey()), VoxGrid.ky(e.getKey()) + dy, VoxGrid.kz(e.getKey())), e.getValue());
        p.grid = g;
        p.pivot = new float[]{p.pivot[0], p.pivot[1] + dy, p.pivot[2]};
    }

    /** Última descida de assento aplicada (voxels; diagnóstico/testes). */
    public int r0Seat;

    /**
     * Cabelo sob a aba ("sob_aba"): some só o que ATRAVESSA o chapéu (mesmo voxel) ou SAI POR CIMA dele (acima
     * do topo do chapéu naquela coluna; fora das colunas do chapéu, acima da aba mais baixa). O cabelo de dentro
     * do chapéu fica — não aparece e fecha o furo da aba (sem fresta). Pedaços pequenos que o corte soltou
     * (ponta de mecha) saem, como no prune_specks do gerador.
     */
    static void underBrim(VoxGrid hair, VoxGrid hat) {
        Map<Long, Integer> topOfHat = new HashMap<>();
        int lowest = Integer.MAX_VALUE;
        for (long k : hat.cells().keySet()) {
            topOfHat.merge(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)), VoxGrid.ky(k), Math::max);
            lowest = Math.min(lowest, VoxGrid.ky(k));
        }
        int low = lowest;
        hair.cells().keySet().removeIf(k -> {
            if (hat.has(k)) return true;
            Integer t = topOfHat.get(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)));
            return t != null ? VoxGrid.ky(k) > t : VoxGrid.ky(k) >= low + 2;
        });
        dropSmallPieces(hair, 40, low);
    }

    /** Remove componentes (6-conexos) menores que min voxels que estejam INTEIROS da aba para cima (pontas que o
     *  corte soltou); mechas pequenas que já eram separadas no desenho e ficam abaixo da aba continuam. */
    static void dropSmallPieces(VoxGrid g, int min, int brim) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<java.util.Set<Long>> comps = new ArrayList<>();
        for (long start : g.cells().keySet()) {
            if (!seen.add(start)) continue;
            java.util.Set<Long> comp = new java.util.HashSet<>();
            java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>(List.of(start));
            while (!q.isEmpty()) {
                long k = q.poll();
                comp.add(k);
                int x = VoxGrid.kx(k), y = VoxGrid.ky(k), z = VoxGrid.kz(k);
                for (long n : new long[]{VoxGrid.key(x + 1, y, z), VoxGrid.key(x - 1, y, z), VoxGrid.key(x, y + 1, z),
                        VoxGrid.key(x, y - 1, z), VoxGrid.key(x, y, z + 1), VoxGrid.key(x, y, z - 1)})
                    if (g.has(n) && seen.add(n)) q.add(n);
            }
            comps.add(comp);
        }
        comps.sort((a, b) -> b.size() - a.size());
        for (int i = 1; i < comps.size(); i++) {
            java.util.Set<Long> c = comps.get(i);
            boolean floating = c.stream().allMatch(k -> VoxGrid.ky(k) >= brim);
            if (c.size() < min && floating) for (long k : c) g.remove(k);
        }
    }

    /** Faixa do capuz C3 pintada sobre o cabelo (mesma regra do gerador da exploradora). */
    static void band(VoxGrid hair, JsonObject f) {
        int color = (1 << 24) | rgb(f.get("cor").getAsString());
        JsonArray rows = f.getAsJsonArray("linhas");
        int r0 = rows.get(0).getAsInt(), r1 = rows.get(1).getAsInt(), z0 = f.get("zMin").getAsInt(), z1 = f.get("zMax").getAsInt();
        List<Long> paint = new ArrayList<>();
        for (long k : hair.cells().keySet()) {
            int x = VoxGrid.kx(k), y = VoxGrid.ky(k), z = VoxGrid.kz(k);
            if ((y == r0 || y == r1) && z >= z0 && z <= z1 && hair.has(VoxGrid.key(x, y + 1, z))) paint.add(k);
        }
        for (long k : paint) hair.put(k, color);
    }

    /**
     * Recolore as células de um canal preservando a rampa: cada canal RGB da cor original c foi derivado da
     * base b pelo gerador por multiplicação (sombra: c ≤ b) ou por mistura em direção ao branco (luz: c ≥ b);
     * a mesma relação é reaplicada à base nova n. Material (bits altos) não muda. Base igual → nada muda.
     */
    static void recolor(VoxGrid g, Map<Long, Integer> mask, String channel, int base, int target) {
        if (base == target || mask == null) return;
        int ch = CHANNEL_IDS.get(channel);
        for (var e : mask.entrySet()) {
            if (e.getValue() != ch || !g.has(e.getKey())) continue;
            g.put(e.getKey(), recolor(g.get(e.getKey()), base, target));
        }
    }

    static int recolor(int packed, int base, int target) {
        int out = packed & 0xFF000000;
        for (int s = 16; s >= 0; s -= 8) {
            int c = (packed >> s) & 0xFF, b = (base >> s) & 0xFF, n = (target >> s) & 0xFF, v;
            if (c <= b) v = b == 0 ? n : Math.round(n * (float) c / b);
            else v = b >= 255 ? 255 : Math.round(n + (255 - n) * (float) (c - b) / (255 - b));
            out |= Math.min(255, Math.max(0, v)) << s;
        }
        return out;
    }

    static final Map<String, Integer> CHANNEL_IDS = new HashMap<>();

    private Piece piece(JsonObject p, String channel, int base, int target) {
        Piece piece = new Piece();
        piece.name = p.get("nome").getAsString();
        piece.parent = p.get("pai").getAsString();
        piece.pivot = floats(p.getAsJsonArray("pivo"));
        piece.grid = load(p);
        if (channel != null && p.has("mascara") && !p.get("mascara").isJsonNull() && base != target) {
            piece.grid = piece.grid.copy();
            recolor(piece.grid, mask(p), channel, base, target);
        }
        return piece;
    }

    // ------------------------------------------------------------------ GPU

    /** Modelo renderizável (do cache, se a aparência já foi composta). */
    public CharacterModel model(Appearance a) {
        String sig = a.signature();
        CharacterModel m = models.get(sig);
        if (m != null) {
            cacheHits++;
            return m;
        }
        cacheMisses++;
        m = build(compose(a), true);
        models.put(sig, m);
        return m;
    }

    /** Converte a composição em CharacterModel; meshes=false = só esqueleto (testes/validação offline). */
    public CharacterModel build(ComposedRig r, boolean meshes) {
        return meshes ? upload(prepare(r)) : upload(skeletonOnly(r));
    }

    /** Composição + dados de vértice prontos, SÓ CPU (seguro fora da thread do OpenGL; rodada 07.2). */
    public static final class Prepared {
        public ComposedRig rig;
        public final List<float[]> data = new ArrayList<>();                 // uma por peça (null = sem malha)
        public final List<Map<String, float[]>> alts = new ArrayList<>();
        public double meshMs;
        public int faces;
    }

    public Prepared prepare(Appearance a) { return prepare(compose(a)); }

    public Prepared prepare(ComposedRig r) {
        Prepared p = new Prepared();
        p.rig = r;
        long t0 = System.nanoTime();
        for (Piece pc : r.pieces) {
            float[] d = meshData(pc.grid, r.voxelSize, r.affine);
            p.data.add(d);
            p.faces += d.length / (13 * 6);
            Map<String, float[]> am = new LinkedHashMap<>();
            for (var e : pc.alts.entrySet()) am.put(e.getKey(), meshData(e.getValue(), r.voxelSize, false));   // como CharacterModel.load
            p.alts.add(am);
        }
        p.meshMs = (System.nanoTime() - t0) / 1e6;
        return p;
    }

    private static Prepared skeletonOnly(ComposedRig r) {
        Prepared p = new Prepared();
        p.rig = r;
        for (int i = 0; i < r.pieces.size(); i++) { p.data.add(null); p.alts.add(Map.of()); }
        return p;
    }

    /** Cria as malhas (GPU: chame na thread do OpenGL) e o CharacterModel. */
    public CharacterModel upload(Prepared pr) {
        ComposedRig r = pr.rig;
        CharacterModel m = new CharacterModel();
        m.name = r.name;
        m.anim = r.anim;
        m.voxelSize = r.voxelSize;
        m.meta = r.meta;
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        long t0 = System.nanoTime();
        for (int i = 0; i < r.pieces.size(); i++) {
            Piece p = r.pieces.get(i);
            for (long k : p.grid.cells().keySet()) {
                int[] c = {VoxGrid.kx(k), VoxGrid.ky(k), VoxGrid.kz(k)};
                for (int a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c[a]); hi[a] = Math.max(hi[a], c[a]); }
            }
            CharacterModel.Part part = new CharacterModel.Part();
            part.name = p.name;
            part.px = p.pivot[0] * r.voxelSize;
            part.py = p.pivot[1] * r.voxelSize;
            part.pz = p.pivot[2] * r.voxelSize;
            if (pr.data.get(i) != null) {
                part.mesh = new Mesh(pr.data.get(i), Mesh.SCENE_LAYOUT);
                for (var e : pr.alts.get(i).entrySet()) part.alts.put(e.getKey(), new Mesh(e.getValue(), Mesh.SCENE_LAYOUT));
            }
            m.index.put(part.name, m.parts.size());
            m.parts.add(part);
        }
        for (int i = 0; i < r.pieces.size(); i++) {
            String par = r.pieces.get(i).parent;
            if (par != null) m.parts.get(i).parent = m.index.get(par);
        }
        m.heightVoxels = hi[1] - lo[1] + 1;
        m.height = m.heightVoxels * m.voxelSize;
        m.width = (hi[0] - lo[0] + 1) * m.voxelSize;
        m.depth = (hi[2] - lo[2] + 1) * m.voxelSize;
        lastComposeMs = r.composeNanos / 1e6;
        lastMeshMs = pr.meshMs;
        lastUploadMs = (System.nanoTime() - t0) / 1e6;
        lastFaces = pr.faces;
        return m;
    }

    /** Mesmos dados de vértice que CharacterModel.load geraria para esta grade. */
    static float[] meshData(VoxGrid g, float vs, boolean affine) {
        int[] o = g.origin();
        return VoxelMesher.merge ? VoxelMesher.buildDataMerged(g.source(), vs, o[0], o[1], o[2], affine)
                : VoxelMesher.buildData(g.source(), vs, o[0], o[1], o[2]);
    }

    /** Fecha as malhas (GPU) de um modelo que saiu de uso. */
    public static void release(CharacterModel m) {
        for (CharacterModel.Part p : m.parts) {
            if (p.mesh != null) p.mesh.close();
            for (Mesh a : p.alts.values()) a.close();
            p.alts.clear();
        }
    }

    public void clear() {
        for (CharacterModel m : models.values()) release(m);
        models.clear();
    }

    public int cachedModels() { return models.size(); }

    // ------------------------------------------------------------------ arquivos

    private JsonObject rigDoc(String dir) {
        return rigDocs.computeIfAbsent(dir, d -> {
            try {
                return JsonParser.parseString(Files.readString(assets.resolve(d).resolve("rig.json"))).getAsJsonObject();
            } catch (IOException e) {
                throw new IllegalStateException("rig ausente: " + d, e);
            }
        });
    }

    /** Peça de componente: "file" relativo a assets/components; "origin" = canto mínimo no modelo. */
    private VoxGrid load(JsonObject e) {
        return grid(catalog.root.resolve(e.get("file").getAsString()).normalize(), e.getAsJsonArray("origin"));
    }

    /** Parte de rig canônico ("file" relativo à pasta do rig). */
    private VoxGrid load(JsonObject p, String rigDir) {
        return grid(assets.resolve(rigDir).resolve(p.get("file").getAsString()), p.getAsJsonArray("origin"));
    }

    private VoxGrid grid(Path f, JsonArray o) {
        String key = f + "@" + o;
        return gridCache.computeIfAbsent(key, k -> VoxGrid.of(VoxModel.load(f), o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt()));
    }

    private Map<Long, Integer> mask(JsonObject e) {
        if (!e.has("mascara") || e.get("mascara").isJsonNull()) return null;
        JsonObject m = e.getAsJsonObject("mascara");
        VoxGrid g = load(m);
        Map<Long, Integer> out = new HashMap<>();
        for (var c : g.cells().entrySet()) out.put(c.getKey(), c.getValue() & 0xFFFFFF);
        return out;
    }

    static int rgb(String hex) { return Integer.parseInt(hex.substring(1), 16); }

    private static float[] floats(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    static {
        CHANNEL_IDS.put("pele", 0x010000);
        CHANNEL_IDS.put("cabelo", 0x020000);
        CHANNEL_IDS.put("sobrancelha", 0x030000);
        CHANNEL_IDS.put("iris", 0x040000);
    }
}
