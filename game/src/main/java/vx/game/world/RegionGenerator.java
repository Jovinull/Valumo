package vx.game.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Rodada 11 — compõe UMA região a partir da semente: onde ficam o assentamento e o posto de trilha, por onde a trilha
 * passa, se há lago, como o assentamento se arruma (um de três arranjos autorais), quem mora lá, onde ficam o encontro,
 * o marco e a vegetação. A autoria está nos componentes (os props e NPCs aprovados da direção E); a variedade está na
 * composição. Cada decisão usa um gerador derivado por categoria ({@link Rng#of}): mudar a vegetação não move a missão.
 *
 * <p>Só compõe; quem confere se a região é jogável (colisão real, alcançabilidade) é o {@code ProceduralScene}, que
 * pede outra tentativa ({@code attempt}) — no máximo {@link #MAX_ATTEMPTS} — ou recusa a semente com o motivo.
 */
public final class RegionGenerator {
    public static final String ID = "valumo.regiao";
    /**
     * Rodada 12 (ADR 0029) — versões que convivem no mesmo executável. Cada mundo guarda a versão com que nasceu e é
     * sempre gerado por ela: a v1 é o algoritmo da rodada 11, CONGELADO (referência {@code regioes-v1.txt}); uma versão
     * nova nunca muda o caminho de uma antiga.
     */
    public static final int V1 = 1, V2 = 2;
    /** A versão com que os mundos NOVOS nascem (a mais nova aprovada). Rodada 12: a v2. */
    public static final int LATEST = V2;
    public static final int MAX_ATTEMPTS = 4;

    /** Esta versão do jogo sabe gerar a versão {@code v}? (Desconhecida = mundo de um jogo mais novo: não abre.) */
    public static boolean supports(int v) { return v == V1 || v == V2; }

    /**
     * Rodada 12.2 — a versão está PUBLICADA (congelada: o resultado de uma semente nunca mais muda)? Uma versão ainda em
     * testes pode mudar a região de uma semente antes da publicação; um save de um mundo dela que não bate mais com a região
     * de agora é "de uma versão de testes" ({@link vx.game.SaveGame.Incompatible}), não arquivo corrompido.
     * <p>
     * Fechamento da v2 (tag {@code procedural-world-v0.6}): a v2 foi aprovada no teste físico e PUBLICADA. As duas estão
     * congeladas; qualquer mudança no resultado de uma semente é uma versão nova (v3, ADR 0029), nunca uma mudança aqui.
     */
    public static boolean published(int v) { return v == V1 || v == V2; }

    /**
     * Fechamento da v2 — a versão teve builds de TESTES antes de ser publicada: a v2, nas rodadas 12 a 12.3 (os mundos
     * experimentais do proprietário, como o "RUINAS", nasceram nelas). Depois da publicação a região de uma semente não
     * muda mais; então um save ÍNTEGRO de um mundo dela que não bate com a região de agora vem desse período, e a recusa diz
     * isso ("de uma versão de testes", {@link vx.game.SaveGame.Incompatible}), nunca "arquivo corrompido". A v1 não teve:
     * um save dela que não bate "não corresponde a este mundo".
     */
    public static boolean hadTestBuilds(int v) { return v == V2; }
    /** Metade do lado da região jogável (u): tudo cabe em ±17, a floresta fecha até a borda do mundo (±24). */
    static final float HALF = 16.5f;

    /** NPCs que podem morar no assentamento (pasta do rig) e se exigem lago. A Kelyra mora sempre (a estalagem). */
    static final String[] RESIDENTS = {"bramor", "teodor", "pluma", "ondina", "lunara"};

    private RegionGenerator() { }

    /** Compõe a região da semente na versão pedida (a do MUNDO, nunca "a mais nova"). A v1 não usa perfil. */
    public static RegionLayout compose(long seed, int attempt, int version) { return compose(seed, attempt, version, null, true); }

    /**
     * {@code profile}: os dados congelados da v2 ({@link RegionProfile}; a v1 ignora). {@code vegetation=false}: sem a
     * vegetação (teste de independência: ela não pode mover lugar, encontro nem marco).
     */
    public static RegionLayout compose(long seed, int attempt, int version, RegionProfile profile, boolean vegetation) {
        return switch (version) {
            case V1 -> composeV1(seed, attempt, vegetation);
            case V2 -> RegionV2.compose(seed, attempt, profile, vegetation);
            default -> throw new IllegalArgumentException("versão do gerador não suportada: " + version);
        };
    }

    public static RegionLayout compose(long seed, int attempt, int version, boolean vegetation) {
        return compose(seed, attempt, version, null, vegetation);
    }

    /** A v1 (rodada 11), congelada: nenhuma conta, rótulo ou ordem de sorteio daqui muda (referência {@code regioes-v1.txt}). */
    static RegionLayout composeV1(long seed, int attempt, boolean vegetation) {
        String region = "r0";
        RegionLayout L = new RegionLayout(seed, attempt, region, V1);
        String a = "t" + attempt;
        // ---------------------------------------------------------- relevo
        Rng tr = Rng.of(seed, region, "relevo", a);
        L.noiseX = tr.range(-60f, 60f);
        L.noiseZ = tr.range(-60f, 60f);
        L.amplitude = tr.range(0.6f, 1.6f);
        L.rimN = tr.range(0.0f, 4.5f);
        L.rimS = tr.range(0.0f, 4.0f);
        L.rimE = tr.range(0.0f, 4.0f);
        L.rimW = tr.range(0.0f, 4.0f);
        // ---------------------------------------------------------- lugares: assentamento e posto
        Rng pr = Rng.of(seed, region, "lugares", a);
        float ang = pr.range(0f, (float) (Math.PI * 2)), rad = pr.range(1.5f, 6.0f);
        L.sx = (float) Math.cos(ang) * rad;
        L.sz = (float) Math.sin(ang) * rad;
        L.plateauR = 7.0f;
        L.clearingR = 3.6f;
        // o posto: do outro lado da região, 13–16 u, dentro dos limites (ângulos candidatos em ordem fixa)
        float base = ang + (float) Math.PI + pr.range(-1.0f, 1.0f), dist = pr.range(13f, 16f);
        boolean placed = false;
        for (int k = 0; k < 12 && !placed; k++) {
            float t = base + (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.35f;
            float x = L.sx + (float) Math.cos(t) * dist, z = L.sz + (float) Math.sin(t) * dist;
            if (Math.abs(x) <= HALF - 1 && Math.abs(z) <= HALF - 1) { L.px = x; L.pz = z; placed = true; }
        }
        if (!placed) { L.px = Math.clamp(L.sx + (float) Math.cos(base) * dist, -HALF + 1, HALF - 1); L.pz = Math.clamp(L.sz + (float) Math.sin(base) * dist, -HALF + 1, HALF - 1); }
        // patamares: a altura natural de cada lugar, presa a uma faixa caminhável
        ProceduralField probe = new ProceduralField(L);
        L.plateauH = Math.clamp(probe.natural(L.sx, L.sz), 0.15f, 0.9f);
        L.clearingH = Math.clamp(probe.natural(L.px, L.pz), L.plateauH - 0.9f, L.plateauH + 1.1f);
        // ---------------------------------------------------------- trilha: curva com 2 pontos de controle desviados
        Rng rr = Rng.of(seed, region, "trilha", a);
        float dx = L.px - L.sx, dz = L.pz - L.sz, len = (float) Math.hypot(dx, dz);
        float ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        float off1 = rr.range(1.2f, 3.4f) * (rr.chance(0.5f) ? 1 : -1), off2 = rr.range(-3.0f, 3.0f);
        float[][] ctrl = {
                {L.sx + ux * 2.0f, L.sz + uz * 2.0f},
                {L.sx + dx * 0.36f + nx * off1, L.sz + dz * 0.36f + nz * off1},
                {L.sx + dx * 0.70f + nx * off2, L.sz + dz * 0.70f + nz * off2},
                {L.px - ux * L.clearingR * 0.55f, L.pz - uz * L.clearingR * 0.55f}};
        sampleCurve(ctrl, 0.5f, L.path);
        float[] p0 = L.path.get(0), p1 = L.path.get(Math.min(4, L.path.size() - 1));
        float fl = (float) Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
        L.fx = (p1[0] - p0[0]) / fl;
        L.fz = (p1[1] - p0[1]) / fl;
        float[] q0 = L.path.get(Math.max(0, L.path.size() - 5)), q1 = L.path.getLast();
        float gl = (float) Math.hypot(q1[0] - q0[0], q1[1] - q0[1]);
        L.gx = (q1[0] - q0[0]) / gl;
        L.gz = (q1[1] - q0[1]) / gl;
        // ---------------------------------------------------------- lago (opcional), longe de tudo
        Rng wr = Rng.of(seed, region, "agua", a);
        if (wr.chance(0.65f)) {
            for (int k = 0; k < 40; k++) {                                // pode ficar na borda (visível da área jogável)
                float x = wr.range(-HALF - 1, HALF + 1), z = wr.range(-HALF - 1, HALF + 1);
                if (Math.hypot(x - L.sx, z - L.sz) < L.plateauR + 3.6f) continue;
                if (Math.hypot(x - L.px, z - L.pz) < L.clearingR + 4.2f) continue;
                if (minPathDist(L, x, z) < 5.4f) continue;
                L.pond = new float[]{x, z};
                break;
            }
        }
        // ---------------------------------------------------------- colina (marco da paisagem), longe da trilha e da água
        Rng hr = Rng.of(seed, region, "colina", a);
        for (int k = 0; k < 40; k++) {
            float x = hr.range(-HALF - 2, HALF + 2), z = hr.range(-HALF - 2, HALF + 2), r = hr.range(3.5f, 5.5f);
            if (Math.hypot(x - L.sx, z - L.sz) < L.plateauR + r + 1.5f || Math.hypot(x - L.px, z - L.pz) < L.clearingR + r + 1.5f) continue;
            if (minPathDist(L, x, z) < r + 2.6f) continue;
            if (L.pond != null && Math.hypot(x - L.pond[0], z - L.pond[1]) < r + 6f) continue;
            L.hill = new float[]{x, z, r, hr.range(1.4f, 2.6f)};
            break;
        }
        // ---------------------------------------------------------- área jogável
        L.limits.add(new float[]{L.sx, L.sz, L.plateauR + 2.0f});
        for (int i = 0; i < L.path.size(); i += 4) L.limits.add(new float[]{L.path.get(i)[0], L.path.get(i)[1], 2.6f});
        L.limits.add(new float[]{L.path.getLast()[0], L.path.getLast()[1], 2.6f});
        L.arenaR = L.clearingR;
        composeSettlement(L, a);
        composePost(L);
        if (vegetation) composeVegetation(L, a);
        return L;
    }

    // ------------------------------------------------------------------ assentamento

    /**
     * Três arranjos autorais em volta da praça, girados para a saída da trilha (eixo f): a estalagem sempre atrás da
     * praça com a porta para ela; poço, fogueira, caixotes, cercas e lanternas em posições de cada arranjo; a placa e
     * duas lanternas marcam a saída da trilha. Coordenadas locais: (lado, frente) em u.
     */
    static void composeSettlement(RegionLayout L, String a) {
        Rng sr = Rng.of(L.seed, L.regionId, "assentamento", a);
        L.arrangement = sr.below(3);
        float fx = L.fx, fz = L.fz, rx = fz, rz = -fx;                    // r = f girado −90° (direita de quem sai)
        float side = sr.chance(0.5f) ? 1 : -1;                              // espelha o arranjo
        java.util.function.BiFunction<Float, Float, float[]> at = (s, f) ->
                new float[]{L.sx + rx * s * side + fx * f, L.sz + rz * s * side + fz * f};
        float facePlaza = deg(fx, fz);                                     // de frente para a saída (porta para a praça)
        String settlement = "poi." + L.regionId + ".assentamento.0";
        record Item(String n, float s, float f, float rot) { }
        List<Item> items = new ArrayList<>();
        switch (L.arrangement) {
            case 0 -> {                                                     // estalagem ao fundo, poço à direita
                items.add(new Item("house", 0f, -5.0f, 0f));
                items.add(new Item("well", 3.8f, -1.2f, 0f));
                items.add(new Item("barrel", -3.4f, -2.8f, 0f));
                items.add(new Item("crate", -2.7f, -3.3f, 15f));
                items.add(new Item("crate", -3.9f, -3.6f, -10f));
                items.add(new Item("fence", -5.6f, -1.0f, 90f));
                items.add(new Item("fence", -5.6f, 1.2f, 90f));
            }
            case 1 -> {                                                     // estalagem inclinada, fogueira com bancos
                items.add(new Item("house", -1.4f, -4.9f, 22f));
                items.add(new Item("campfire", 2.6f, 0.4f, 0f));
                items.add(new Item("log", 2.6f, 1.9f, 0f));
                items.add(new Item("stump", 4.1f, -0.4f, 0f));
                items.add(new Item("well", -4.2f, -0.6f, 0f));
                items.add(new Item("barrel", 2.9f, -3.6f, 0f));
                items.add(new Item("crate", 3.6f, -3.2f, 20f));
            }
            default -> {                                                    // estalagem à direita, cerca à esquerda, poço à frente
                items.add(new Item("house", 1.8f, -5.0f, -18f));
                items.add(new Item("well", -3.4f, 2.0f, 0f));
                items.add(new Item("fence", -5.4f, -2.2f, 70f));
                items.add(new Item("fence", -5.9f, 0.0f, 90f));
                items.add(new Item("crate", 4.4f, -2.4f, 10f));
                items.add(new Item("barrel", 4.9f, -1.6f, 0f));
                items.add(new Item("log", -2.6f, -2.9f, 60f));
            }
        }
        // saída da trilha: placa e duas lanternas (igual em todo arranjo: é a "porta" do assentamento)
        items.add(new Item("signpost", 1.5f, L.plateauR - 0.9f, -20f));
        items.add(new Item("lantern_post", -1.7f, L.plateauR - 2.0f, 0f));
        items.add(new Item("lantern_post", 1.7f, L.plateauR - 2.4f, 0f));
        for (Item it : items) {
            float[] p = at.apply(it.s(), it.f());
            L.props.add(new RegionLayout.Prop(it.n(), p[0], p[1], facePlaza + it.rot() * side));
        }
        // moradores: a Kelyra diante da estalagem; os outros em vagas da praça (3 a 5, sem lago não há Ondina)
        float[] inn = at.apply(items.getFirst().s() + 1.6f, items.getFirst().f() + 4.3f);
        L.npcs.add(new RegionLayout.Npc("kelyra", inn[0], inn[1], facePlaza + 25f * side));
        List<String> pool = new ArrayList<>(List.of(RESIDENTS));
        if (L.pond == null) pool.remove("ondina");
        int count = 3 + sr.below(Math.min(3, pool.size() - 2));
        // vagas da praça: só as que ficam longe de todo prop do arranjo e dos outros moradores (a pessoa e o lugar de
        // quem fala com ela, 1,2 u à frente, livres)
        float[][] slots = {{-3.2f, 1.6f}, {3.4f, 3.0f}, {-1.4f, 4.4f}, {4.6f, -1.0f}, {-4.4f, -0.8f}, {0.4f, 2.2f},
                {2.2f, 4.8f}, {-4.6f, 3.4f}, {5.2f, 1.6f}, {-2.4f, -1.4f}};
        List<float[]> free = new ArrayList<>(List.of(slots));
        for (int i = 0; i < count && !free.isEmpty(); ) {
            float[] slot = free.remove(sr.below(free.size()));
            float[] p = at.apply(slot[0], slot[1]);
            float toCenter = deg(L.sx - p[0], L.sz - p[1]) + sr.range(-25f, 25f);
            float tx = p[0] + (float) Math.sin(Math.toRadians(toCenter)) * 1.2f, tz = p[1] + (float) Math.cos(Math.toRadians(toCenter)) * 1.2f;
            if (!clearOfProps(L, p[0], p[1], 1.0f) || !clearOfProps(L, tx, tz, 0.6f) || !clearOfNpcs(L, p[0], p[1], 2.6f)) continue;
            String who = pool.remove(sr.below(pool.size()));
            L.npcs.add(new RegionLayout.Npc(who, p[0], p[1], toCenter));
            i++;
        }
        L.pois.add(new RegionLayout.PointOfInterest(settlement, "settlement", L.regionId, L.sx, L.sz, L.plateauR, "habitado",
                List.of(), List.of()));
        float[] st = at.apply(0f, 1.6f);
        L.startX = st[0];
        L.startZ = st[1];
        L.startYawDeg = deg(fx, fz);
    }

    // ------------------------------------------------------------------ posto de trilha (o lugar da missão)

    /**
     * O posto: a clareira no fim da trilha, o quadro do posto de lado, o marco no fundo (a lanterna voltada para quem
     * chega) e dois fungos-cardo entre a chegada e o marco. Identidades estáveis: tudo deriva do id do POI.
     */
    static void composePost(RegionLayout L) {
        String poi = "poi." + L.regionId + ".posto_trilha.0";
        String tag = L.regionId + ".posto_0";
        float gx = L.gx, gz = L.gz, rx = gz, rz = -gx;
        float back = deg(-gx, -gz);                                        // virado para quem chega pela trilha
        float bx = L.px + gx * 2.9f, bz = L.pz + gz * 2.9f;
        String beacon = "obj." + tag + ".marco";
        L.features.add(new RegionLayout.Feature(beacon, "marco_de_luz", bx, bz, back));
        Rng br = Rng.of(L.seed, L.regionId, "posto");
        float sideBoard = br.chance(0.5f) ? 1 : -1;
        L.props.add(new RegionLayout.Prop("trail_post", L.px + rx * 3.4f * sideBoard + gx * 0.6f, L.pz + rz * 3.4f * sideBoard + gz * 0.6f,
                deg(-rx * sideBoard, -rz * sideBoard)));
        L.props.add(new RegionLayout.Prop("stump", L.px - rx * 2.9f * sideBoard - gx * 0.4f, L.pz - rz * 2.9f * sideBoard - gz * 0.4f, 0f));
        String s1 = tag + ".fungo.1", s2 = tag + ".fungo.2";
        L.spawns.add(new RegionLayout.Spawn(s1, "fungo_cardo", L.px + gx * 0.9f - rx * 1.1f, L.pz + gz * 0.9f - rz * 1.1f, back + 10f));
        L.spawns.add(new RegionLayout.Spawn(s2, "fungo_cardo", L.px + gx * 1.4f + rx * 1.0f, L.pz + gz * 1.4f + rz * 1.0f, back - 12f));
        L.pois.add(new RegionLayout.PointOfInterest(poi, "trail_post", L.regionId, L.px, L.pz, L.clearingR, "sinal_apagado",
                List.of(beacon), List.of(s1, s2)));
        float[] rp = L.path.get(Math.max(0, L.path.size() - 7));
        L.restartX = rp[0];
        L.restartZ = rp[1];
        L.restartYawDeg = deg(gx, gz);
    }

    // ------------------------------------------------------------------ vegetação

    /**
     * Árvores fechando a região (fora da área jogável), algumas de acento junto à trilha, pedras e arbustos nos vazios,
     * flores e tufos nas margens da trilha, cogumelos perto do posto e juncos na margem do lago. Nada sobre a trilha,
     * a praça, a clareira ou a água (distâncias medidas no mesmo relevo).
     */
    static void composeVegetation(RegionLayout L, String a) {
        ProceduralField f = new ProceduralField(L);
        List<float[]> taken = new ArrayList<>();
        for (RegionLayout.Prop p : L.props) taken.add(new float[]{p.x(), p.z(), p.name().equals("house") ? 4.2f : 1.4f});
        for (RegionLayout.Npc n : L.npcs) taken.add(new float[]{n.x(), n.z(), 1.8f});
        for (RegionLayout.Feature ft : L.features) taken.add(new float[]{ft.x(), ft.z(), 1.6f});
        for (RegionLayout.Spawn s : L.spawns) taken.add(new float[]{s.x(), s.z(), 1.2f});
        Rng vr = Rng.of(L.seed, L.regionId, "vegetacao", a);
        // a mata da região: uma de três composições autorais (mesmos modelos, proporções diferentes)
        L.forest = Rng.of(L.seed, L.regionId, "mata").below(3);
        String[] trees = switch (L.forest) {
            case 0 -> new String[]{"tree_oak", "tree_oak2", "tree_oak3", "tree_oak4", "tree_oak", "tree_oak2", "tree_pine", "tree_birch"};   // carvalhal
            case 1 -> new String[]{"tree_pine", "tree_pine", "tree_pine", "tree_oak3", "tree_pine", "tree_oak4", "tree_pine", "tree_birch"}; // pinhal
            default -> new String[]{"tree_birch", "tree_birch", "tree_willow", "tree_oak3", "tree_birch", "tree_oak", "tree_willow", "tree_birch"}; // bétulas e salgueiros
        };
        // anel de floresta: fora da área jogável (mas visível dela)
        int placedTrees = 0;
        for (int k = 0; k < 900 && placedTrees < 85; k++) {
            float x = vr.range(-23f, 23f), z = vr.range(-23f, 23f);
            float edge = playableEdge(L, x, z);
            if (edge < 1.8f || f.pondDistance(x, z) < 5.0f || f.pathDistance(x, z) < 2.6f) continue;
            if (L.hill != null && Math.hypot(x - L.hill[0], z - L.hill[1]) < 2.8f) continue;   // o topo da colina é das pedras
            if (crowded(taken, x, z, 2.7f)) continue;
            String t = trees[vr.below(trees.length)];
            L.props.add(new RegionLayout.Prop(t, x, z, vr.below(4) * 90f));
            taken.add(new float[]{x, z, 2.7f});
            placedTrees++;
        }
        // acentos junto à trilha (dentro da faixa de 2,6–3,6 u do eixo, fora da praça e da clareira)
        int accents = 2 + vr.below(3);
        for (int k = 0; k < 200 && accents > 0; k++) {
            float[] p = L.path.get(vr.below(L.path.size()));
            float side = vr.chance(0.5f) ? 1 : -1, d = vr.range(2.9f, 3.6f), ang = vr.range(0f, 6.283f);
            float x = p[0] + (float) Math.cos(ang) * d * side, z = p[1] + (float) Math.sin(ang) * d * side;
            if (f.pathDistance(x, z) < 2.7f || nearPlaces(L, x, z, 1.6f) || f.pondDistance(x, z) < 5f || crowded(taken, x, z, 2.4f)) continue;
            String t = trees[vr.below(trees.length)];
            L.props.add(new RegionLayout.Prop(t, x, z, vr.below(4) * 90f));
            taken.add(new float[]{x, z, 2.4f});
            accents--;
        }
        // pedras, arbustos e cogumelos grandes (obstáculos de verdade, nos vazios)
        String[] solids = {"rock_l", "rock_m", "rock_s", "bush", "bush_berry", "rock_m", "mushroom_big", "sapling"};
        int solidsLeft = 9 + vr.below(5);
        for (int k = 0; k < 600 && solidsLeft > 0; k++) {
            float x = vr.range(-HALF - 3, HALF + 3), z = vr.range(-HALF - 3, HALF + 3);
            String n = solids[vr.below(solids.length)];
            float need = n.equals("rock_l") || n.equals("mushroom_big") ? 2.8f : 2.0f;
            if (f.pathDistance(x, z) < need || nearPlaces(L, x, z, 1.0f) || f.pondDistance(x, z) < 4.6f || crowded(taken, x, z, need)) continue;
            L.props.add(new RegionLayout.Prop(n, x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, need});
            solidsLeft--;
        }
        // a colina ganha um afloramento de pedra no topo (o marco da paisagem; fora da área jogável)
        if (L.hill != null) {
            L.props.add(new RegionLayout.Prop("rock_l", L.hill[0], L.hill[1], vr.range(0f, 360f)));
            L.props.add(new RegionLayout.Prop("rock_m", L.hill[0] + 1.6f, L.hill[1] - 0.8f, vr.range(0f, 360f)));
            L.props.add(new RegionLayout.Prop(trees[vr.below(trees.length)], L.hill[0] - 1.8f, L.hill[1] + 1.4f, vr.below(4) * 90f));
        }
        // miúdos (atravessáveis): flores e tufos nas margens da trilha, samambaias
        String[] small = {"flower_white", "flower_yellow", "flower_blue", "flower_red", "grass_tall", "grass_tuft", "fern", "grass_tuft"};
        int smallLeft = 26 + vr.below(10);
        for (int k = 0; k < 900 && smallLeft > 0; k++) {
            float[] p = L.path.get(vr.below(L.path.size()));
            float ang = vr.range(0f, 6.283f), d = vr.range(1.0f, 2.4f);
            float x = p[0] + (float) Math.cos(ang) * d, z = p[1] + (float) Math.sin(ang) * d;
            if (f.pathDistance(x, z) < 0.95f || f.pondDistance(x, z) < 3.4f || crowded(taken, x, z, 0.5f)) continue;
            L.props.add(new RegionLayout.Prop(small[vr.below(small.length)], x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, 0.45f});
            smallLeft--;
        }
        // cogumelos vermelhos na borda da clareira (o lugar dos fungos)
        for (int i = 0; i < 4; i++) {
            float ang = vr.range(0f, 6.283f), d = L.clearingR + vr.range(0.5f, 1.4f);
            float x = L.px + (float) Math.cos(ang) * d, z = L.pz + (float) Math.sin(ang) * d;
            if (f.pathDistance(x, z) < 1.2f || crowded(taken, x, z, 0.8f)) continue;
            L.props.add(new RegionLayout.Prop("mushroom_red", x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, 0.8f});
        }
        // margem do lago: juncos e vitórias-régias
        if (L.pond != null) for (int i = 0; i < 6; i++) {
            float ang = vr.range(0f, 6.283f), d = vr.range(3.0f, 4.0f);
            float x = L.pond[0] + (float) Math.cos(ang) * d, z = L.pond[1] + (float) Math.sin(ang) * d / 1.2f;
            String n = i % 3 == 2 ? "lily" : "reeds";
            if (n.equals("lily")) { x = L.pond[0] + (float) Math.cos(ang) * 1.8f; z = L.pond[1] + (float) Math.sin(ang) * 1.5f; }
            L.props.add(new RegionLayout.Prop(n, x, z, vr.range(0f, 360f)));
        }
    }

    // ------------------------------------------------------------------ utilitários

    /** Rumo (graus) de um vetor no chão: o mesmo de {@code rotateY} dos props (+z do modelo aponta para lá). */
    static float deg(float x, float z) { return (float) Math.toDegrees(Math.atan2(x, z)); }

    /** Curva Catmull-Rom pelos pontos de controle, amostrada a cada {@code step} u (extremos repetidos). */
    static void sampleCurve(float[][] c, float step, List<float[]> out) {
        out.add(new float[]{c[0][0], c[0][1]});
        for (int i = 0; i + 1 < c.length; i++) {
            float[] p0 = c[Math.max(0, i - 1)], p1 = c[i], p2 = c[i + 1], p3 = c[Math.min(c.length - 1, i + 2)];
            float segLen = (float) Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int n = Math.max(1, Math.round(segLen / step));
            for (int k = 1; k <= n; k++) {
                float t = (float) k / n, t2 = t * t, t3 = t2 * t;
                float x = 0.5f * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                float z = 0.5f * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                out.add(new float[]{x, z});
            }
        }
    }

    static float minPathDist(RegionLayout L, float x, float z) {
        float best = Float.MAX_VALUE;
        for (float[] p : L.path) best = Math.min(best, (float) Math.hypot(p[0] - x, p[1] - z));
        return best;
    }

    /** Quanto o ponto está FORA da área jogável (negativo = dentro). */
    static float playableEdge(RegionLayout L, float x, float z) {
        float best = Float.MAX_VALUE;
        for (float[] c : L.limits) best = Math.min(best, (float) Math.hypot(x - c[0], z - c[1]) - c[2]);
        best = Math.min(best, (float) Math.hypot(x - L.px, z - L.pz) - L.clearingR);
        return best;
    }

    static boolean nearPlaces(RegionLayout L, float x, float z, float margin) {
        return Math.hypot(x - L.sx, z - L.sz) < L.plateauR + margin || Math.hypot(x - L.px, z - L.pz) < L.clearingR + margin;
    }

    /** Raio aproximado (u) da pegada de um prop no chão (só para afastar moradores na composição). */
    static float footprint(String n) {
        return switch (n) {
            case "house" -> 3.4f; case "well", "fence", "log", "trail_post" -> 1.3f; case "campfire", "stump" -> 0.7f;
            case "signpost" -> 0.8f; default -> 0.45f;
        };
    }

    static boolean clearOfProps(RegionLayout L, float x, float z, float margin) {
        for (RegionLayout.Prop p : L.props) if (Math.hypot(p.x() - x, p.z() - z) < footprint(p.name()) + margin) return false;
        return true;
    }

    static boolean clearOfNpcs(RegionLayout L, float x, float z, float d) {
        for (RegionLayout.Npc n : L.npcs) if (Math.hypot(n.x() - x, n.z() - z) < d) return false;
        return true;
    }

    static boolean crowded(List<float[]> taken, float x, float z, float r) {
        for (float[] t : taken) if (Math.hypot(t[0] - x, t[1] - z) < Math.max(r, t[2])) return true;
        return false;
    }
}
