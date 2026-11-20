package vx.game.world;

import java.util.ArrayList;
import java.util.List;

import static vx.game.world.RegionGenerator.HALF;
import static vx.game.world.RegionGenerator.crowded;
import static vx.game.world.RegionGenerator.deg;
import static vx.game.world.RegionGenerator.minPathDist;
import static vx.game.world.RegionGenerator.playableEdge;
import static vx.game.world.RegionGenerator.sampleCurve;

/**
 * Rodada 12 — o gerador v2 ("Mundos com Identidade"): a mesma região controlada da v1 (±16,5 u jogáveis, a floresta fecha
 * a borda), composta com a biblioteca dos lotes 1 e 2. As decisões:
 * <ul>
 *   <li><b>assentamento</b> em um de dois estilos — vila de CAMINHO (compacta, praça com chafariz, comércio e ofícios
 *       virados para a praça, caminhos da praça às portas) ou RURAL (sítios espaçados, horta com espantalho, paiol,
 *       poço de manivela, o caçador na borda da mata). A estalagem (a casa aprovada, com a Kelyra) fica sempre ao fundo da
 *       praça. Construções e conjuntos de ofício são postos INTEIROS (âncora + itens girados juntos), virados para a praça,
 *       sem sobrepor nada nem a saída da trilha;</li>
 *   <li><b>ruína</b> ({@code ruin_house}) fora da trilha principal, num patamar, com uma trilha secundária até a entrada: o
 *       lugar a explorar (POI {@code poi.r0.ruina.0});</li>
 *   <li><b>posto e missão</b>: os da v1 (as mesmas peças), no fim da trilha;</li>
 *   <li><b>mata</b> com as árvores novas (bifurcada, álamo em fileiras junto à trilha, a seca e a retorcida como raras).</li>
 * </ul>
 * Cada decisão usa um gerador derivado por rótulo com o prefixo {@code "v2"} ({@link Rng#of}): a v1 não é tocada e acrescentar
 * uma árvore não move a missão. Os dados dos assets vêm do perfil congelado ({@link RegionProfile}). Quem confere se a
 * região é jogável é o {@code ProceduralScene}, na colisão real.
 */
final class RegionV2 {
    private RegionV2() { }

    static final String CAMINHO = "caminho", RURAL = "rural", PEQUENA = "pequena", MEDIA = "media";
    /** Raio da praça (u), por estilo (e porte, na de caminho). */
    static final float PLAZA_C = 3.1f, PLAZA_R = 3.4f, PLAZA_CM = 3.4f;
    /** Rodada 12.1 (P81): chance de a vila de caminho ser média (a decisão de design: metade pequena, metade média). */
    static final float MEDIUM_CHANCE = 0.5f;

    static RegionLayout compose(long seed, int attempt, RegionProfile P, boolean vegetation) {
        if (P == null) throw new IllegalArgumentException("a v2 precisa do perfil (assets/scenes/regiao_v2_e.json)");
        String region = "r0", a = "t" + attempt;
        RegionLayout L = new RegionLayout(seed, attempt, region, RegionGenerator.V2);
        // ---------------------------------------------------------- relevo (o mesmo vocabulário da v1, outra semente de rótulo)
        Rng tr = Rng.of(seed, "v2", region, "relevo", a);
        L.noiseX = tr.range(-60f, 60f);
        L.noiseZ = tr.range(-60f, 60f);
        L.amplitude = tr.range(0.5f, 1.4f);
        L.rimN = tr.range(0.0f, 4.5f);
        L.rimS = tr.range(0.0f, 4.0f);
        L.rimE = tr.range(0.0f, 4.0f);
        L.rimW = tr.range(0.0f, 4.0f);
        // ---------------------------------------------------------- estilo (fica o mesmo em todas as tentativas: é a identidade)
        L.style = Rng.of(seed, "v2", region, "estilo").chance(0.55f) ? CAMINHO : RURAL;
        // rodada 12.1 (P81): a vila de caminho tem PORTE — pequena (a da rodada 12: estalagem + 2–3) ou média (patamar maior,
        // praça um pouco maior, 4–5 construções desejadas, piso de 3). Sorteio da semente, como o estilo (identidade do lugar)
        L.size = !L.style.equals(CAMINHO) ? "" : Rng.of(seed, "v2", region, "porte").chance(MEDIUM_CHANCE) ? MEDIA : PEQUENA;
        L.plateauR = L.style.equals(CAMINHO) ? (L.size.equals(MEDIA) ? 11.8f : 10.4f) : 11.2f;
        L.clearingR = 3.6f;
        // ---------------------------------------------------------- lugares: assentamento perto de uma borda, posto do outro lado
        Rng pr = Rng.of(seed, "v2", region, "lugares", a);
        float ang = pr.range(0f, (float) (Math.PI * 2)), rad = pr.range(4.5f, 7.0f);
        if (L.size.equals(MEDIA)) rad = 3.8f + (rad - 4.5f) * 0.88f;         // a vila média um pouco mais perto do centro: o patamar cabe
        L.sx = (float) Math.cos(ang) * rad;
        L.sz = (float) Math.sin(ang) * rad;
        float base = ang + (float) Math.PI + pr.range(-0.9f, 0.9f), dist = L.plateauR + pr.range(5.0f, 7.0f) - (L.size.equals(MEDIA) ? 0.8f : 0f);
        boolean placed = false;
        for (int k = 0; k < 14 && !placed; k++) {
            float t = base + (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.3f;
            float x = L.sx + (float) Math.cos(t) * dist, z = L.sz + (float) Math.sin(t) * dist;
            if (Math.abs(x) <= HALF - 2.5f && Math.abs(z) <= HALF - 2.5f) { L.px = x; L.pz = z; placed = true; }
        }
        if (!placed) {
            L.px = Math.clamp(L.sx + (float) Math.cos(base) * dist, -HALF + 2.5f, HALF - 2.5f);
            L.pz = Math.clamp(L.sz + (float) Math.sin(base) * dist, -HALF + 2.5f, HALF - 2.5f);
        }
        ProceduralField probe = new ProceduralField(L);
        L.plateauH = Math.clamp(probe.natural(L.sx, L.sz), 0.15f, 0.9f);
        L.clearingH = Math.clamp(probe.natural(L.px, L.pz), L.plateauH - 0.9f, L.plateauH + 1.1f);
        // ---------------------------------------------------------- trilha principal (a mesma curva da v1)
        Rng rr = Rng.of(seed, "v2", region, "trilha", a);
        float dx = L.px - L.sx, dz = L.pz - L.sz, len = (float) Math.hypot(dx, dz);
        float ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        float off1 = rr.range(1.0f, 3.0f) * (rr.chance(0.5f) ? 1 : -1), off2 = rr.range(-2.6f, 2.6f);
        float[][] ctrl = {
                {L.sx + ux * 2.0f, L.sz + uz * 2.0f},
                {L.sx + dx * 0.40f + nx * off1, L.sz + dz * 0.40f + nz * off1},
                {L.sx + dx * 0.72f + nx * off2, L.sz + dz * 0.72f + nz * off2},
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
        // ---------------------------------------------------------- a ruína (o lugar a explorar), longe da trilha, com a dela
        List<Box> taken = new ArrayList<>();
        ruin(L, P, a, taken);
        // ---------------------------------------------------------- lago e colina (opcionais), longe de tudo
        Rng wr = Rng.of(seed, "v2", region, "agua", a);
        if (wr.chance(0.6f)) {
            for (int k = 0; k < 40; k++) {
                float x = wr.range(-HALF - 1, HALF + 1), z = wr.range(-HALF - 1, HALF + 1);
                if (Math.hypot(x - L.sx, z - L.sz) < L.plateauR + 3.6f || Math.hypot(x - L.px, z - L.pz) < L.clearingR + 4.2f) continue;
                if (minPathDist(L, x, z) < 5.4f || nearPads(L, x, z, 5.0f)) continue;
                L.pond = new float[]{x, z};
                break;
            }
        }
        Rng hr = Rng.of(seed, "v2", region, "colina", a);
        for (int k = 0; k < 40; k++) {
            float x = hr.range(-HALF - 2, HALF + 2), z = hr.range(-HALF - 2, HALF + 2), r = hr.range(3.5f, 5.5f);
            if (Math.hypot(x - L.sx, z - L.sz) < L.plateauR + r + 1.5f || Math.hypot(x - L.px, z - L.pz) < L.clearingR + r + 1.5f) continue;
            if (minPathDist(L, x, z) < r + 2.6f || nearPads(L, x, z, r + 2.5f)) continue;
            if (L.pond != null && Math.hypot(x - L.pond[0], z - L.pond[1]) < r + 6f) continue;
            L.hill = new float[]{x, z, r, hr.range(1.4f, 2.6f)};
            break;
        }
        // ---------------------------------------------------------- área jogável
        L.limits.add(new float[]{L.sx, L.sz, L.plateauR + 2.0f});
        for (int i = 0; i < L.path.size(); i += 4) L.limits.add(new float[]{L.path.get(i)[0], L.path.get(i)[1], 2.6f});
        L.limits.add(new float[]{L.path.getLast()[0], L.path.getLast()[1], 2.6f});
        for (RegionLayout.Branch b : L.branches)
            if (b.carve()) for (int i = 0; i < b.points().size(); i += 3) L.limits.add(new float[]{b.points().get(i)[0], b.points().get(i)[1], 2.2f});
        for (float[] p : L.pads) L.limits.add(new float[]{p[0], p[1], p[2] + 1.2f});
        L.arenaR = L.clearingR;
        settlement(L, P, a, taken);
        RegionGenerator.composePost(L);                                    // o posto e a missão: as peças da v1
        if (vegetation) vegetation(L, a, taken);
        return L;
    }

    // ------------------------------------------------------------------ geometria

    /** Retângulo girado no chão (os 4 cantos no mundo). */
    record Box(float[] xs, float[] zs) {
        float cx() { return (xs[0] + xs[2]) / 2; }
        float cz() { return (zs[0] + zs[2]) / 2; }
        float reach() { return (float) Math.hypot(xs[0] - xs[2], zs[0] - zs[2]) / 2; }
    }

    /** Ponto local (lx, lz) de um modelo em (x, z) com rumo {@code rotDeg} (o rotateY dos props) no mundo. */
    static float[] world(float x, float z, float rotDeg, float lx, float lz) {
        double t = Math.toRadians(rotDeg);
        float c = (float) Math.cos(t), s = (float) Math.sin(t);
        return new float[]{x + c * lx + s * lz, z - s * lx + c * lz};
    }

    static Box box(float x, float z, float rotDeg, float x0, float z0, float x1, float z1) {
        float[][] c = {world(x, z, rotDeg, x0, z0), world(x, z, rotDeg, x1, z0), world(x, z, rotDeg, x1, z1), world(x, z, rotDeg, x0, z1)};
        return new Box(new float[]{c[0][0], c[1][0], c[2][0], c[3][0]}, new float[]{c[0][1], c[1][1], c[2][1], c[3][1]});
    }

    /** Separação de eixos: as caixas se sobrepõem (encostar não conta). */
    static boolean overlap(Box a, Box b) {
        for (Box p : new Box[]{a, b})
            for (int i = 0; i < 4; i++) {
                float ex = p.xs[(i + 1) % 4] - p.xs[i], ez = p.zs[(i + 1) % 4] - p.zs[i], ax = -ez, az = ex;
                float amin = Float.MAX_VALUE, amax = -Float.MAX_VALUE, bmin = Float.MAX_VALUE, bmax = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    float da = ax * a.xs[k] + az * a.zs[k], db = ax * b.xs[k] + az * b.zs[k];
                    amin = Math.min(amin, da); amax = Math.max(amax, da);
                    bmin = Math.min(bmin, db); bmax = Math.max(bmax, db);
                }
                if (amax <= bmin + 1e-4f || bmax <= amin + 1e-4f) return false;
            }
        return true;
    }

    /** Distância de um ponto à caixa (0 dentro). */
    static float dist(Box b, float x, float z) {
        boolean inside = true;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float ax = b.xs[i], az = b.zs[i], bx = b.xs[(i + 1) % 4], bz = b.zs[(i + 1) % 4];
            float ex = bx - ax, ez = bz - az, ll = ex * ex + ez * ez;
            float t = ll < 1e-9f ? 0 : Math.clamp(((x - ax) * ex + (z - az) * ez) / ll, 0f, 1f);
            best = Math.min(best, (float) Math.hypot(ax + ex * t - x, az + ez * t - z));
            if (ex * (z - az) - ez * (x - ax) < 0) inside = false;
        }
        boolean in2 = true;                                               // os cantos podem vir no outro sentido
        for (int i = 0; i < 4; i++) {
            float ax = b.xs[i], az = b.zs[i], ex = b.xs[(i + 1) % 4] - ax, ez = b.zs[(i + 1) % 4] - az;
            if (ex * (z - az) - ez * (x - ax) > 0) in2 = false;
        }
        return inside || in2 ? 0f : best;
    }

    static boolean within(Box b, float cx, float cz, float r) {
        for (int i = 0; i < 4; i++) if (Math.hypot(b.xs[i] - cx, b.zs[i] - cz) > r) return false;
        return true;
    }

    /** O chão em volta de (x, z) varia no máximo {@code tol} num raio {@code r} (a árvore e a pedra não ficam penduradas). */
    static boolean flat(ProceduralField f, float x, float z, float r, float tol) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float[] q : new float[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
            float h = f.height(x + q[0], z + q[1], false);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo <= tol;
    }

    static boolean nearPads(RegionLayout L, float x, float z, float margin) {
        for (float[] p : L.pads) if (Math.hypot(x - p[0], z - p[1]) < p[2] + margin) return true;
        return false;
    }

    // ------------------------------------------------------------------ a ruína

    /** Rodada 12.1 — um tipo de ruína: o asset, o tipo do POI, o rótulo dos ids, o ponto da descoberta e o tipo dela. */
    record RuinKind(String asset, String poiType, String label, String discoveryPoint, String discoveryType) { }

    /**
     * A casa tomada pela mata (o pote na lareira), a torre caída (o mirante em cima da muralha baixa) e, na rodada 12.2 (PR
     * #8), a abóbada soterrada (os barris do fundo) e o sítio abandonado (a pedra do marco).
     */
    static final RuinKind HOUSE = new RuinKind("ruin_house", "ruin", "ruina", "lareira", "casa"),
            TOWER = new RuinKind("ruin_tower", "ruin_tower", "torre", "mirante", "torre"),
            VAULT = new RuinKind("ruin_vault", "ruin_vault", "abobada", "barris", "abobada"),
            FARMSTEAD = new RuinKind("ruin_farmstead", "ruin_farmstead", "sitio", "pedra_do_marco", "sitio");
    /** Rodada 12.2 — a abóbada encosta o morro dela num declive: o terreno natural sobe pelo menos isto (u) da frente aos fundos. */
    static final float VAULT_RISE = 0.3f;
    /** Rodada 12.2 — o sítio quer chão aberto: entre os pontos da pegada, o terreno natural varia no máximo isto (u). */
    static final float FARM_FLAT = 0.6f;

    /**
     * Rodada 12.2 — a ruína da região, entre os quatro tipos: a ORDEM em que se tenta vem de um sorteio com peso (rótulo
     * "ruina-tipos", o mesmo em todas as tentativas, como o estilo) e cada tipo só entra onde o lugar faz sentido (a abóbada
     * num declive, o sítio em chão aberto: {@link #ruin(RegionLayout, RegionProfile, String, List, RuinKind)}). Pesos: a
     * casa 4 (a base, sempre aparece), a torre 3, a abóbada 3 com morro na região e 1 sem, o sítio 3 na região rural (aberta)
     * e 1 na de caminho. O que não couber cede a vez ao seguinte; sem lugar para nenhum, a tentativa é recusada.
     */
    static void ruin(RegionLayout L, RegionProfile P, String a, List<Box> taken) {
        List<RuinKind> kinds = new ArrayList<>(List.of(HOUSE, TOWER, VAULT, FARMSTEAD));
        List<Integer> weights = new ArrayList<>(List.of(4, 3, L.hill != null ? 3 : 1, L.style.equals(RURAL) ? 3 : 1));
        Rng r = Rng.of(L.seed, "v2", L.regionId, "ruina-tipos");
        while (!kinds.isEmpty()) {
            int total = 0;
            for (int w : weights) total += w;
            int pick = r.below(total), i = 0;
            while (pick >= weights.get(i)) pick -= weights.get(i++);
            RuinKind k = kinds.remove(i);
            weights.remove(i);
            if (P.has(k.asset()) && ruin(L, P, a, taken, k)) return;
        }
    }

    /**
     * A ruína num lugar ao lado da trilha (5–8 u do eixo, no meio do caminho entre o assentamento e o posto), num patamar,
     * virada para a trilha, com uma trilha secundária da principal até a entrada. Devolve se coube (até 140 tentativas
     * determinísticas, com o RNG do tipo: a casa usa o mesmo rótulo de antes).
     */
    static boolean ruin(RegionLayout L, RegionProfile P, String a, List<Box> taken, RuinKind kind) {
        RegionProfile.Asset R = P.asset(kind.asset());
        Rng rr = Rng.of(L.seed, "v2", L.regionId, kind == HOUSE ? "ruina" : "ruina-" + kind.label(), a);
        float padR = R.reach() + 0.9f;
        for (int k = 0; k < 140; k++) {
            float x = rr.range(-HALF + 3.0f, HALF - 3.0f), z = rr.range(-HALF + 3.0f, HALF - 3.0f);
            if (Math.hypot(x - L.sx, z - L.sz) < L.plateauR + 1.0f + padR) continue;              // fora do plano do assentamento
            if (Math.hypot(x - L.px, z - L.pz) < L.clearingR + padR + 1.2f) continue;
            if (minPathDist(L, x, z) < padR + 1.4f || minPathDist(L, x, z) > padR + 7.0f) continue;   // perto da trilha, fora dela
            // o ponto da trilha principal mais perto, ANTES da arena do posto (quem vai à ruína não passa pelos fungos)
            int i = -1;
            for (int j = 0; j < L.path.size(); j++) {
                float[] q = L.path.get(j);
                if (Math.hypot(q[0] - L.px, q[1] - L.pz) < L.clearingR + 3.5f) continue;
                if (i < 0 || Math.hypot(q[0] - x, q[1] - z) < Math.hypot(L.path.get(i)[0] - x, L.path.get(i)[1] - z)) i = j;
            }
            if (i < 0) continue;
            float[] p = L.path.get(i);
            float rot = deg(p[0] - x, p[1] - z) + rr.range(-20f, 20f);       // a entrada (frente) virada para a trilha
            Box b = box(x, z, rot, R.x0() - 0.6f, R.z0() - 0.6f, R.x1() + 0.6f, R.z1() + 1.2f);
            ProceduralField probe = new ProceduralField(L);
            if (kind == VAULT) {                                            // o morro dela encosta num declive que sobe para os fundos
                float[] fr = world(x, z, rot, 0f, R.z1()), bk = world(x, z, rot, 0f, R.z0());
                if (probe.natural(bk[0], bk[1]) - probe.natural(fr[0], fr[1]) < VAULT_RISE) continue;
            }
            if (kind == FARMSTEAD) {                                        // chão aberto: pouco desnível em toda a pegada
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (int u = 0; u <= 2; u++)
                    for (int v = 0; v <= 2; v++) {
                        float[] q = world(x, z, rot, R.x0() + (R.x1() - R.x0()) * u / 2f, R.z0() + (R.z1() - R.z0()) * v / 2f);
                        float hh = probe.natural(q[0], q[1]);
                        lo = Math.min(lo, hh);
                        hi = Math.max(hi, hh);
                    }
                if (hi - lo > FARM_FLAT) continue;
            }
            float h = Math.clamp(probe.natural(x, z), L.plateauH - 0.8f, L.plateauH + 0.9f);
            L.pads.add(new float[]{x, z, padR, h});
            L.props.add(new RegionLayout.Prop(kind.asset(), x, z, rot));
            L.buildings.add(new RegionLayout.Building(kind.asset(), null, x, z, rot, "ruina"));
            taken.add(b);
            // trilha secundária: do ponto da trilha principal até a frente da entrada
            RegionProfile.Point arch = R.entrances().getFirst();
            float[] door = world(x, z, rot, arch.x(), arch.z() + 0.9f);
            float mx = (p[0] + door[0]) / 2 + rr.range(-0.8f, 0.8f), mz = (p[1] + door[1]) / 2 + rr.range(-0.8f, 0.8f);
            List<float[]> pts = new ArrayList<>();
            sampleCurve(new float[][]{{p[0], p[1]}, {mx, mz}, {door[0], door[1]}}, 0.5f, pts);
            float hp = L.plateauH + (L.clearingH - L.plateauH) * ProceduralField.smooth(0f, 1f, (float) i / (L.path.size() - 1));
            L.branches.add(new RegionLayout.Branch(List.copyOf(pts), hp, h, true));
            String poi = "poi." + L.regionId + "." + kind.label() + ".0";
            // a descoberta: no ponto do modelo (a lareira, o mirante), id derivado do POI — nunca de coordenada
            String desc = "desc." + L.regionId + "." + kind.label() + ".0";
            RegionProfile.Point dp = R.interactions().stream().filter(e -> e.id().equals(kind.discoveryPoint())).findFirst().orElse(R.interactions().getFirst());
            float[] hp2 = world(x, z, rot, dp.x(), dp.z());
            L.features.add(new RegionLayout.Feature(desc, "descoberta", hp2[0], hp2[1], rot));
            L.discoverySpots.add(new RegionLayout.DiscoverySpot(desc, kind.discoveryType(), dp.y(), rot));
            L.checkpoints.add(new RegionLayout.Checkpoint("descoberta:" + desc, hp2[0], hp2[1], 1.0f));
            L.pois.add(new RegionLayout.PointOfInterest(poi, kind.poiType(), L.regionId, x, z, padR, "inexplorada", List.of(desc), List.of()));
            for (RegionProfile.Point e : R.entrances()) {
                float[] w = world(x, z, rot, e.x(), e.z());
                L.checkpoints.add(new RegionLayout.Checkpoint("entrada:" + poi + ":" + e.id(), w[0], w[1], 0.6f));
            }
            for (RegionProfile.Point e : R.interactions()) {
                float[] w = world(x, z, rot, e.x(), e.z());
                L.checkpoints.add(new RegionLayout.Checkpoint("interacao:" + poi + ":" + e.id(), w[0], w[1], 1.0f));
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ assentamento

    /** O que pode ocupar uma vaga: um conjunto inteiro, uma construção só, ou a lavoura (grupo autoral da v2). */
    record Candidate(String id, int weight, boolean edgeOnly, boolean outer) { }

    static final List<Candidate> POOL_CAMINHO = List.of(
            new Candidate("conjunto.comerciante", 5, false, false),
            new Candidate("conjunto.ferreiro", 4, false, false),
            new Candidate("conjunto.herbalista", 2, false, false),
            new Candidate("shed_storage", 2, false, false),
            new Candidate("conjunto.guardiao", 3, true, true));
    /** Rodada 12.1 (P81): a vila de caminho média tem também o caçador (na borda) — mais ofícios, não mais do mesmo. */
    static final List<Candidate> POOL_CAMINHO_MEDIA = List.of(
            new Candidate("conjunto.comerciante", 5, false, false),
            new Candidate("conjunto.ferreiro", 4, false, false),
            new Candidate("conjunto.herbalista", 3, false, false),
            new Candidate("shed_storage", 2, false, false),
            new Candidate("conjunto.cacador", 2, false, true),
            new Candidate("conjunto.guardiao", 3, true, true));
    static final List<Candidate> POOL_RURAL = List.of(
            new Candidate("lavoura", 5, false, false),
            new Candidate("conjunto.cacador", 4, false, true),
            new Candidate("conjunto.herbalista", 4, false, false),
            new Candidate("granary_raised", 3, false, false),
            new Candidate("shed_storage", 2, false, false),
            new Candidate("conjunto.ferreiro", 1, false, false),
            new Candidate("conjunto.guardiao", 2, true, true));

    /** A lavoura (rural): dois canteiros, espantalho, muretas de pedra e a carroça, no referencial dela (+z = frente). */
    static final Object[][] LAVOURA = {
            {"crop_field", 0.0f, 0.4f, 0f}, {"crop_field", 0.0f, -1.7f, 0f}, {"scarecrow", 2.3f, -0.6f, 200f},
            {"fence_stone", -2.3f, -0.6f, 90f}, {"fence_stone", 0.0f, -3.1f, 0f}, {"cart_hay", -2.6f, 1.9f, 35f}};
    static final float[] LAVOURA_BOX = {-3.2f, -3.4f, 3.0f, 2.6f};

    /** Caixa local (referencial do modelo/âncora) de uma vaga, com a folga. */
    static float[] localBox(RegionProfile P, String id) {
        if (id.equals("lavoura")) return LAVOURA_BOX.clone();
        if (id.startsWith("conjunto.")) {
            RegionProfile.SetDef s = P.set(id);
            return new float[]{s.x0() - 0.4f, s.z0() - 0.4f, s.x1() + 0.4f, s.z1() + 0.5f};
        }
        RegionProfile.Asset A = P.asset(id);
        return new float[]{A.x0() - Math.max(0.3f, A.sides()), A.z0() - Math.max(0.2f, A.back()), A.x1() + Math.max(0.3f, A.sides()),
                A.z1() + Math.max(0.8f, A.front())};
    }

    static void settlement(RegionLayout L, RegionProfile P, String a, List<Box> taken) {
        Rng sr = Rng.of(L.seed, "v2", L.regionId, "assentamento", a);
        boolean caminho = L.style.equals(CAMINHO), medium = L.size.equals(MEDIA);
        float R = L.plateauR, plaza = caminho ? (medium ? PLAZA_CM : PLAZA_C) : PLAZA_R;
        float exit = deg(L.fx, L.fz), side = sr.chance(0.5f) ? 1 : -1;
        // reservado: a praça e o corredor da saída da trilha
        List<Box> reserved = new ArrayList<>();
        reserved.add(box(L.sx, L.sz, exit, -1.7f, 0f, 1.7f, R + 2.0f));
        // ---- estalagem ao fundo da praça, virada para ela (a porta para a praça e a saída)
        RegionProfile.Asset inn = P.asset("house");
        float innD = plaza + 1.1f + inn.z1();
        float innAng = exit + 180f + sr.range(-12f, 12f);
        float[] ip = {L.sx + (float) Math.sin(Math.toRadians(innAng)) * innD, L.sz + (float) Math.cos(Math.toRadians(innAng)) * innD};
        float innRot = deg(L.sx - ip[0], L.sz - ip[1]);
        place(L, "house", ip[0], ip[1], innRot);
        L.buildings.add(new RegionLayout.Building("house", null, ip[0], ip[1], innRot, "estalagem"));
        taken.add(box(ip[0], ip[1], innRot, inn.x0() - 0.3f, inn.z0() - 0.3f, inn.x1() + 0.3f, inn.z1() + 0.6f));
        placeLocal(L, ip, innRot, "sign_trade__oficio-estalagem", -1.9f * side, inn.z1() + 0.35f, 0f);   // do lado oposto ao da Kelyra
        placeLocal(L, ip, innRot, "barrel", inn.x1() + 0.45f, inn.z1() - 0.6f, 0f);
        float[] kel = world(ip[0], ip[1], innRot, 1.4f * side, inn.z1() + 1.0f);
        L.npcs.add(new RegionLayout.Npc("kelyra", kel[0], kel[1], innRot + 75f * side));   // de lado: a fala fica ao longo da fachada
        // ---- o centro da praça: chafariz (caminho) ou poço de manivela (rural)
        // (o que fica na praça ocupa lugar, com folga: um conjunto não se encosta no chafariz nem no poço)
        if (caminho && sr.chance(0.75f)) {
            place(L, "fountain_square", L.sx, L.sz, exit + 45f);
            L.buildings.add(new RegionLayout.Building("fountain_square", null, L.sx, L.sz, exit + 45f, "praca"));
            RegionProfile.Asset F = P.asset("fountain_square");
            taken.add(box(L.sx, L.sz, exit + 45f, F.x0() - 0.6f, F.z0() - 0.6f, F.x1() + 0.6f, F.z1() + 0.6f));
            for (float s : new float[]{1, -1}) {
                float[] b = world(L.sx, L.sz, exit, s * 2.85f, -0.6f);
                place(L, "bench_wood", b[0], b[1], exit + s * 90f);
                RegionProfile.Asset B = P.asset("bench_wood");
                taken.add(box(b[0], b[1], exit + s * 90f, B.x0() - 0.3f, B.z0() - 0.5f, B.x1() + 0.3f, B.z1() + 0.8f));
            }
        } else {
            float[] w = world(L.sx, L.sz, exit, -2.2f * side, 0.6f);
            place(L, "well_windlass", w[0], w[1], exit + 90f * side);
            L.buildings.add(new RegionLayout.Building("well_windlass", null, w[0], w[1], exit + 90f * side, "praca"));
            RegionProfile.Asset W = P.asset("well_windlass");
            taken.add(box(w[0], w[1], exit + 90f * side, W.x0() - 0.9f, W.z0() - 0.9f, W.x1() + 0.9f, W.z1() + 1.3f));
        }
        // ---- construções e conjuntos em volta da praça, virados para ela: posição sorteada (ângulo a partir da saída, folga
        // entre a praça e a fachada), conferida PEÇA A PEÇA (a pegada da âncora e a de cada item, giradas juntas)
        List<Candidate> pool = new ArrayList<>(caminho ? (medium ? POOL_CAMINHO_MEDIA : POOL_CAMINHO) : POOL_RURAL), missed = new ArrayList<>();
        int want = 3 + sr.below(2) + (medium ? 1 : 0);                     // 3 ou 4 além da estalagem; 4 ou 5 na vila média
        int built = 0;
        while (built < want && !pool.isEmpty()) {
            Candidate c = pick(pool, sr, true);
            pool.remove(c);
            if (fit(L, P, sr, c, caminho, plaza, taken, reserved, 40, 0f)) built++;
            else missed.add(c);
        }
        // vila rala demais (menos de duas além da estalagem; três na média): uma segunda passada nos que não couberam, com mais folga
        for (int k = 0; k < missed.size() && built < (medium ? 3 : 2); k++)
            if (fit(L, P, sr, missed.get(k), caminho, plaza, taken, reserved, 60, 0.8f)) built++;
        // ---- a saída da trilha: placa e duas lanternas (a "porta" do assentamento, como na v1)
        for (Object[] it : new Object[][]{{"signpost", 1.5f, R - 0.9f, -20f}, {"lantern_post", -1.7f, R - 2.0f, 0f}, {"lantern_post", 1.7f, R - 2.4f, 0f}}) {
            float[] w = world(L.sx, L.sz, exit, (float) it[1] * side, (float) it[2]);
            place(L, (String) it[0], w[0], w[1], exit + (float) it[3] * side);
        }
        // ---- caminhos de chão batido da praça até cada porta (só pintam: o patamar já é plano)
        for (RegionLayout.Building b : L.buildings) {
            if (b.role().equals("praca") || b.role().equals("ruina") || !P.has(b.asset()) || P.asset(b.asset()).entrances().isEmpty()) continue;
            RegionProfile.Point e = P.asset(b.asset()).entrances().getFirst();
            float[] door = world(b.x(), b.z(), b.rotDeg(), e.x(), e.z() + 0.3f);
            float vx = door[0] - L.sx, vz = door[1] - L.sz, vl = (float) Math.hypot(vx, vz);
            float[] start = {L.sx + vx / vl * (plaza - 0.6f), L.sz + vz / vl * (plaza - 0.6f)};
            List<float[]> pts = new ArrayList<>();
            sampleCurve(new float[][]{start, door}, 0.5f, pts);
            L.branches.add(new RegionLayout.Branch(List.copyOf(pts), L.plateauH, L.plateauH, false));
        }
        // ---- moradores dos ofícios (rodada 12.1, P76): cada conjunto posto tem um ofício (o do catálogo) e o spawn do
        // trabalhador na âncora; QUEM exerce o ofício vem do perfil congelado (habitantes_e.json → oficio), nunca do nome do
        // NPC. Um morador por conjunto, um conjunto por morador, um spawn por morador; sem ninguém para o ofício, o spawn
        // fica vazio (o conjunto se lê pelos objetos) até o personagem novo entrar no perfil (moradores-dos-oficios.md).
        // RNG próprio: pôr ou tirar um morador não move construção, POI nem missão. Depois, os outros nas vagas da praça
        // Rodada 12.2: o ofício pode ser de um morador SÓ de ofício (Brásia, Quelmo, Arvel, Índira: fora de RESIDENTS, a lista
        // dos moradores comuns, que a v1 também usa e não pode mudar); quem já está na região não repete (um morador por
        // conjunto; com várias vilas no futuro, o perfil lista mais de um por ofício). O ponto vem do perfil: o spawn da
        // âncora ou a vaga recomendada de um item (o feirante ao lado da barraca), com a altura e o rumo dele.
        List<String> pool2 = new ArrayList<>(List.of(RegionGenerator.RESIDENTS));
        if (L.pond == null) pool2.remove("ondina");
        List<String> residents = List.of(RegionGenerator.RESIDENTS);
        Rng wr = Rng.of(L.seed, "v2", L.regionId, "oficios", a);
        for (RegionLayout.Building b : L.buildings) {
            if (b.set() == null) continue;
            RegionProfile.SetDef sd = P.set(b.set());
            List<String> who = new ArrayList<>(P.workers(sd.trade()));
            who.removeIf(id -> residents.contains(id) ? !pool2.contains(id) : L.npcs.stream().anyMatch(n -> n.id().equals(id)));
            RegionProfile.Point sp = P.asset(sd.anchor()).spawns().stream().filter(p -> "npc".equals(p.kind())).findFirst().orElse(null);
            RegionProfile.Spot spot = sd.worker() != null ? sd.worker()
                    : sp == null ? null : new RegionProfile.Spot(sp.x(), sp.z(), 0f, 0f, "ancora:" + sp.id());
            if (who.isEmpty() || spot == null) continue;
            String id = who.get(wr.below(who.size()));
            float[] w = world(b.x(), b.z(), b.rotDeg(), spot.x(), spot.z());
            L.npcs.add(new RegionLayout.Npc(id, w[0], w[1], b.rotDeg() + spot.yawDeg()));   // de frente para a praça (+ o rumo da vaga)
            L.workers.add(new RegionLayout.Worker(id, sd.trade(), b.set(), spot.origin(), spot.y()));
            pool2.remove(id);
        }
        int count = 2 + sr.below(2);
        List<float[]> ring = new ArrayList<>();
        for (int k = 0; k < 28; k++) ring.add(new float[]{sr.range(0f, 360f), sr.range(plaza - 0.4f, plaza + 1.8f)});
        for (float[] slot : ring) {
            if (count == 0 || pool2.isEmpty()) break;
            float ang = slot[0];
            float x = L.sx + (float) Math.sin(Math.toRadians(ang)) * slot[1], z = L.sz + (float) Math.cos(Math.toRadians(ang)) * slot[1];
            float yaw = ang + sr.range(-30f, 30f);                          // de costas para o centro: quem fala fica do lado de fora
            float tx = x + (float) Math.sin(Math.toRadians(yaw)) * 1.2f, tz = z + (float) Math.cos(Math.toRadians(yaw)) * 1.2f;
            if (!clear(L, P, x, z, 0.9f) || !clear(L, P, tx, tz, 0.6f) || reserved.stream().anyMatch(t -> dist(t, x, z) < 0.6f)) continue;
            boolean nearNpc = false;
            for (RegionLayout.Npc n : L.npcs) if (Math.hypot(n.x() - x, n.z() - z) < 2.6f) nearNpc = true;
            if (nearNpc) continue;
            L.npcs.add(new RegionLayout.Npc(pool2.remove(sr.below(pool2.size())), x, z, yaw));
            count--;
        }
        L.pois.add(new RegionLayout.PointOfInterest("poi." + L.regionId + ".assentamento.0", "settlement", L.regionId, L.sx, L.sz, L.plateauR,
                "habitado", List.of(), List.of()));
        float[] st = world(L.sx, L.sz, exit, 0f, plaza + 0.4f);
        L.startX = st[0];
        L.startZ = st[1];
        L.startYawDeg = exit;
    }

    /**
     * As peças de uma vaga para conferir o encaixe: a pegada (com folga) de cada asset no referencial da vaga — {x0, z0,
     * x1, z1, x, z, rumo} —, a âncora primeiro. A folga da frente da âncora é a área livre dela (a porta precisa de espaço).
     */
    static List<float[]> pieces(RegionProfile P, String id) {
        List<float[]> out = new ArrayList<>();
        if (id.equals("lavoura")) {
            for (Object[] it : LAVOURA) {
                RegionProfile.Asset A = P.asset((String) it[0]);
                out.add(new float[]{A.x0() - 0.2f, A.z0() - 0.2f, A.x1() + 0.2f, A.z1() + 0.2f, (float) it[1], (float) it[2], (float) it[3]});
            }
            return out;
        }
        String anchor = id.startsWith("conjunto.") ? P.set(id).anchor() : id;
        RegionProfile.Asset A = P.asset(anchor);
        // a âncora: a pegada com folga lateral e de fundos limitada e 1 u na frente (a porta). A área livre da frente do catálogo
        // pode ser a própria praça numa vila compacta; quem confere se a porta é alcançável é a conferência de alcance
        out.add(new float[]{A.x0() - Math.clamp(A.sides(), 0.3f, 0.6f), A.z0() - Math.clamp(A.back(), 0.2f, 0.5f),
                A.x1() + Math.clamp(A.sides(), 0.3f, 0.6f), A.z1() + 1.0f, 0f, 0f, 0f});
        if (id.startsWith("conjunto."))
            for (RegionProfile.Item it : P.set(id).items()) {
                RegionProfile.Asset I = P.asset(it.prop());
                out.add(new float[]{I.x0() - 0.15f, I.z0() - 0.15f, I.x1() + 0.15f, I.z1() + 0.15f, it.x(), it.z(), it.yawDeg()});
            }
        return out;
    }

    /**
     * Tenta encaixar o candidato em até {@code tries} posições sorteadas (ângulo a partir da saída, folga entre a praça e a
     * fachada; {@code extra} alarga a folga), conferindo PEÇA A PEÇA. Encaixou = posto e as caixas entram no ocupado.
     */
    static boolean fit(RegionLayout L, RegionProfile P, Rng sr, Candidate c, boolean caminho, float plaza, List<Box> taken,
                       List<Box> reserved, int tries, float extra) {
        float exit = deg(L.fx, L.fz);
        for (int t = 0; t < tries; t++) {
            float rel = c.edgeOnly() ? sr.range(30f, 115f) : sr.range(34f, 168f);   // a torre: de olho na saída da trilha
            float ang = exit + rel * (sr.chance(0.5f) ? 1 : -1);
            // a vila média tem o anel mais largo (até 3,2 u da praça à fachada): cabem mais conjuntos sem a praça crescer
            float gap = (caminho ? sr.range(0.9f, L.size.equals(MEDIA) ? 3.2f : 2.4f) : sr.range(1.4f, 2.8f)) + (c.outer() ? 0.8f : 0f) + extra;
            List<float[]> pieces = pieces(P, c.id());                        // {x0, z0, x1, z1, lx, lz, yaw} no referencial da vaga
            float d = plaza + gap + frontOf(P, c.id());                      // a FACHADA a {@code gap} da borda da praça
            float px = L.sx + (float) Math.sin(Math.toRadians(ang)) * d, pz = L.sz + (float) Math.cos(Math.toRadians(ang)) * d;
            float rot = deg(L.sx - px, L.sz - pz) + (caminho ? sr.range(-5f, 5f) : sr.range(-14f, 14f));
            List<Box> boxes = new ArrayList<>();
            boolean fits = true;
            for (int k = 0; k < pieces.size() && fits; k++) {
                float[] q = pieces.get(k);
                float[] o = world(px, pz, rot, q[4], q[5]);
                Box b = box(o[0], o[1], rot + q[6], q[0], q[1], q[2], q[3]);
                // entre as peças do MESMO conjunto não se confere: a placa e a barraca ficam junto da porta de propósito, e a
                // composição interna já é conferida no catálogo (ConteudoMundoTest.craftSetsKeepAnchorEntrancesFreeAndDoNotOverlap)
                fits = within(b, L.sx, L.sz, L.plateauR + 0.6f) && dist(b, L.sx, L.sz) >= (k == 0 ? plaza - 0.6f : plaza - 0.3f)
                        && taken.stream().noneMatch(x -> overlap(x, b)) && reserved.stream().noneMatch(x -> overlap(x, b));
                boxes.add(b);
            }
            if (!fits) continue;
            taken.addAll(boxes);
            build(L, P, c.id(), px, pz, rot);
            return true;
        }
        return false;
    }

    /**
     * Caixa do modelo da árvore retorcida (centro e meias-medidas em x, z, u), para a conferência de desnível ao plantá-la
     * junto da ruína. Medida do {@code props.json} (5,62 × 2,88 u); {@code GeneratorV2Test} confere que o modelo não mudou.
     */
    static final float[] GNARLED_CENTER = {-0.0625f, -0.0625f}, GNARLED_HALF = {2.8125f, 1.4375f};

    /** Profundidade da frente (u) da âncora de uma vaga: a fachada fica a esta distância do centro dela. */
    static float frontOf(RegionProfile P, String id) {
        if (id.equals("lavoura")) return 1.2f;
        return P.asset(id.startsWith("conjunto.") ? P.set(id).anchor() : id).z1();
    }

    /** Sorteia um candidato pelo peso ({@code edge}: a vaga junto à saída aceita a torre; as outras não). */
    static Candidate pick(List<Candidate> pool, Rng r, boolean edge) {
        int total = 0;
        for (Candidate c : pool) if (!c.edgeOnly() || edge) total += c.weight();
        if (total == 0) return null;
        int k = r.below(total);
        for (Candidate c : pool) {
            if (c.edgeOnly() && !edge) continue;
            if ((k -= c.weight()) < 0) return c;
        }
        return null;
    }

    /** Põe um conjunto inteiro, uma construção só ou a lavoura, e registra entradas e pontos para a conferência. */
    static void build(RegionLayout L, RegionProfile P, String id, float x, float z, float rot) {
        if (id.equals("lavoura")) {
            float[] o = {x, z};
            for (Object[] it : LAVOURA) placeLocal(L, o, rot, (String) it[0], (float) it[1], (float) it[2], (float) it[3]);
            L.buildings.add(new RegionLayout.Building("crop_field", null, x, z, rot, "lavoura"));
            return;
        }
        String anchor = id, set = null;
        if (id.startsWith("conjunto.")) { set = id; anchor = P.set(id).anchor(); }
        place(L, anchor, x, z, rot);
        L.buildings.add(new RegionLayout.Building(anchor, set, x, z, rot, set == null ? "construcao" : P.set(set).trade()));
        if (set != null) for (RegionProfile.Item it : P.set(set).items()) placeLocal(L, new float[]{x, z}, rot, it.prop(), it.x(), it.z(), it.yawDeg());
        RegionProfile.Asset A = P.asset(anchor);
        String tag = "b" + L.buildings.size() + "." + anchor;
        for (RegionProfile.Point e : A.entrances()) {
            float[] w = world(x, z, rot, e.x(), e.z());
            L.checkpoints.add(new RegionLayout.Checkpoint("entrada:" + tag + ":" + e.id(), w[0], w[1], 0.6f));
        }
        if (anchor.equals("workshop_forge") || anchor.equals("market_stall"))   // interiores: quem usa chega a 1 u do objeto
            for (RegionProfile.Point e : A.interactions()) {
                float[] w = world(x, z, rot, e.x(), e.z());
                L.checkpoints.add(new RegionLayout.Checkpoint("interacao:" + tag + ":" + e.id(), w[0], w[1], 1.0f));
            }
        if (set != null)
            for (RegionProfile.Item it : P.set(set).items())
                if (it.prop().equals("market_stall")) {
                    float[] o = world(x, z, rot, it.x(), it.z());
                    for (RegionProfile.Point e : P.asset("market_stall").interactions()) {
                        float[] w = world(o[0], o[1], rot + it.yawDeg(), e.x(), e.z());
                        L.checkpoints.add(new RegionLayout.Checkpoint("interacao:" + tag + ".barraca:" + e.id(), w[0], w[1], 1.0f));
                    }
                }
    }

    static void place(RegionLayout L, String n, float x, float z, float rot) { L.props.add(new RegionLayout.Prop(n, x, z, rot)); }

    static void placeLocal(RegionLayout L, float[] o, float rot, String n, float lx, float lz, float yaw) {
        float[] w = world(o[0], o[1], rot, lx, lz);
        place(L, n, w[0], w[1], rot + yaw);
    }

    /** Ponto livre de toda construção (pegada + {@code margin}) e de todo prop pequeno da praça. */
    static boolean clear(RegionLayout L, RegionProfile P, float x, float z, float margin) {
        for (RegionLayout.Prop p : L.props) {
            if (!P.has(p.name())) continue;
            RegionProfile.Asset A = P.asset(p.name());
            Box b = box(p.x(), p.z(), p.rotDeg(), A.x0(), A.z0(), A.x1(), A.z1());
            if (dist(b, x, z) < margin) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ mata

    static void vegetation(RegionLayout L, String a, List<Box> boxes) {
        ProceduralField f = new ProceduralField(L);
        List<float[]> taken = new ArrayList<>();
        for (Box b : boxes) taken.add(new float[]{b.cx(), b.cz(), b.reach() + 0.4f});
        for (RegionLayout.Prop p : L.props) taken.add(new float[]{p.x(), p.z(), 1.0f});
        for (RegionLayout.Npc n : L.npcs) taken.add(new float[]{n.x(), n.z(), 1.8f});
        for (RegionLayout.Feature ft : L.features) taken.add(new float[]{ft.x(), ft.z(), 1.6f});
        for (RegionLayout.Spawn s : L.spawns) taken.add(new float[]{s.x(), s.z(), 1.2f});
        Rng vr = Rng.of(L.seed, "v2", L.regionId, "vegetacao", a);
        L.forest = Rng.of(L.seed, "v2", L.regionId, "mata").below(3);
        String[] trees = switch (L.forest) {
            case 0 -> new String[]{"tree_oak", "tree_forked", "tree_oak2", "tree_oak3", "tree_forked", "tree_oak4", "tree_birch", "tree_pine"};    // carvalhal misto
            case 1 -> new String[]{"tree_pine", "tree_pine", "tree_forked", "tree_pine", "tree_oak3", "tree_pine", "tree_birch", "tree_pine"};     // pinhal
            default -> new String[]{"tree_birch", "tree_willow", "tree_birch", "tree_forked", "tree_birch", "tree_oak", "tree_willow", "tree_oak4"}; // bétulas e salgueiros
        };
        // rara: a árvore retorcida junto à ruína (o marco do lugar), por trás dela, NO patamar (chão plano), ANTES da mata.
        // Ela nunca saía (0 de 60 sementes; GeneratorV2Test confere): a reserva da própria ruína (pegada + margens) cobria o
        // patamar inteiro, e as 80 árvores da mata tomavam o anel em volta. Agora a reserva da PRÓPRIA ruína é trocada pela
        // pegada dela com as margens (a árvore fica fora), e o tronco cabe no plano do patamar
        RegionLayout.Building ruin = L.buildings.stream().filter(b -> b.role().equals("ruina")).findFirst().orElse(null);
        if (ruin != null && vr.chance(0.4f)) {
            float[] p = L.pads.getFirst();
            Box own = null;                                                  // a pegada da ruína com as margens (a do ruin())
            for (Box b : boxes)
                if (own == null || Math.hypot(b.cx() - ruin.x(), b.cz() - ruin.z()) < Math.hypot(own.cx() - ruin.x(), own.cz() - ruin.z())) own = b;
            for (int k = 0; k < 20; k++) {
                float ang = (float) Math.toRadians(ruin.rotDeg() + 180f + vr.range(-50f, 50f)), d = vr.range(0.5f, 1.0f) * (p[2] - 0.7f);
                float x = p[0] + (float) Math.sin(ang) * d, z = p[1] + (float) Math.cos(ang) * d;
                if (own == null || dist(own, x, z) < 0.25f) continue;
                boolean near = false;
                for (float[] t : taken) {
                    boolean ownBox = Math.abs(t[0] - own.cx()) < 1e-4f && Math.abs(t[1] - own.cz()) < 1e-4f;
                    if (!ownBox && Math.hypot(t[0] - x, t[1] - z) < Math.max(1.8f, t[2])) { near = true; break; }
                }
                if (near || f.pathDistance(x, z) < 2.2f || f.pondDistance(x, z) < 4f) continue;
                // a MESMA conferência de desnível da Reachability (cantos de 80% da caixa do modelo, girados, e o centro): a
                // copa é larga e os cantos dela saem do patamar
                float rot = vr.range(0f, 360f), c = (float) Math.cos(Math.toRadians(rot)), sn = (float) Math.sin(Math.toRadians(rot));
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (float[] q : new float[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, 0}}) {
                    float lx = GNARLED_CENTER[0] + q[0] * GNARLED_HALF[0] * 0.8f, lz = GNARLED_CENTER[1] + q[1] * GNARLED_HALF[1] * 0.8f;
                    float h = f.height(x + c * lx + sn * lz, z - sn * lx + c * lz, false);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
                if (hi - lo > 0.45f) continue;
                L.props.add(new RegionLayout.Prop("tree_gnarled", x, z, rot));
                taken.add(new float[]{x, z, 2.0f});
                // rodada 12.1 (P77): o marco natural — a descoberta só existe quando a árvore existe (rara, ~40%)
                String desc = "desc." + L.regionId + ".arvore.0";
                L.features.add(new RegionLayout.Feature(desc, "descoberta", x, z, rot));
                L.discoverySpots.add(new RegionLayout.DiscoverySpot(desc, "arvore", 0f, rot));
                L.checkpoints.add(new RegionLayout.Checkpoint("descoberta:" + desc, x, z, 1.3f));
                L.pois.add(new RegionLayout.PointOfInterest("poi." + L.regionId + ".arvore.0", "landmark", L.regionId, x, z, 1.0f, "intacta",
                        List.of(desc), List.of()));
                break;
            }
        }
        int placedTrees = 0;
        for (int k = 0; k < 900 && placedTrees < 80; k++) {
            float x = vr.range(-23f, 23f), z = vr.range(-23f, 23f);
            if (playableEdge(L, x, z) < 1.8f || f.pondDistance(x, z) < 5.0f || f.pathDistance(x, z) < 2.6f || nearPads(L, x, z, 1.2f)) continue;
            if (L.hill != null && Math.hypot(x - L.hill[0], z - L.hill[1]) < 2.8f) continue;
            if (crowded(taken, x, z, 2.7f)) continue;
            L.props.add(new RegionLayout.Prop(trees[vr.below(trees.length)], x, z, vr.below(4) * 90f));
            taken.add(new float[]{x, z, 2.7f});
            placedTrees++;
        }
        // álamos em fileira, de um lado da trilha (o desenho da paisagem rural: alinhados, não espalhados)
        int rows = L.style.equals(RURAL) ? 2 : 1;
        for (int r = 0; r < rows; r++) {
            int i0 = (int) (L.path.size() * vr.range(0.15f, 0.55f));
            float side = vr.chance(0.5f) ? 1 : -1;
            int n = 4 + vr.below(3), put = 0;
            for (int k = 0; k < n * 4 && put < n; k++) {
                int i = i0 + k * 3;
                if (i + 1 >= L.path.size()) break;
                float[] p = L.path.get(i), q = L.path.get(i + 1);
                float tx = q[0] - p[0], tz = q[1] - p[1], tl = (float) Math.hypot(tx, tz);
                float x = p[0] - tz / tl * 3.0f * side, z = p[1] + tx / tl * 3.0f * side;
                if (f.pathDistance(x, z) < 2.6f || f.pondDistance(x, z) < 4f || RegionGenerator.nearPlaces(L, x, z, 1.0f) || nearPads(L, x, z, 1.0f)
                        || crowded(taken, x, z, 1.4f) || !flat(f, x, z, 0.5f, 0.4f)) continue;
                L.props.add(new RegionLayout.Prop("tree_poplar", x, z, vr.below(4) * 90f));
                taken.add(new float[]{x, z, 1.4f});
                put++;
            }
        }
        // rara: a árvore seca na borda da mata
        if (vr.chance(0.35f))
            for (int k = 0; k < 60; k++) {
                float x = vr.range(-20f, 20f), z = vr.range(-20f, 20f), e = playableEdge(L, x, z);
                if (e < 0.8f || e > 3.0f || f.pathDistance(x, z) < 2.6f || crowded(taken, x, z, 2.0f) || f.pondDistance(x, z) < 4f) continue;
                L.props.add(new RegionLayout.Prop("tree_dead", x, z, vr.below(4) * 90f));
                taken.add(new float[]{x, z, 2.0f});
                break;
            }
        // pedras, arbustos e cogumelos grandes nos vazios (como na v1)
        String[] solids = {"rock_l", "rock_m", "rock_s", "bush", "bush_berry", "rock_m", "mushroom_big", "sapling"};
        int solidsLeft = 8 + vr.below(5);
        for (int k = 0; k < 600 && solidsLeft > 0; k++) {
            float x = vr.range(-HALF - 3, HALF + 3), z = vr.range(-HALF - 3, HALF + 3);
            String n = solids[vr.below(solids.length)];
            float need = n.equals("rock_l") || n.equals("mushroom_big") ? 2.8f : 2.0f;
            if (f.pathDistance(x, z) < need || RegionGenerator.nearPlaces(L, x, z, 1.0f) || nearPads(L, x, z, 1.0f) || f.pondDistance(x, z) < 4.6f
                    || crowded(taken, x, z, need) || !flat(f, x, z, need * 0.45f, 0.45f)) continue;
            L.props.add(new RegionLayout.Prop(n, x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, need});
            solidsLeft--;
        }
        if (L.hill != null) {
            L.props.add(new RegionLayout.Prop("rock_l", L.hill[0], L.hill[1], vr.range(0f, 360f)));
            L.props.add(new RegionLayout.Prop("rock_m", L.hill[0] + 1.6f, L.hill[1] - 0.8f, vr.range(0f, 360f)));
            L.props.add(new RegionLayout.Prop(trees[vr.below(trees.length)], L.hill[0] - 1.8f, L.hill[1] + 1.4f, vr.below(4) * 90f));
        }
        // miúdos (atravessáveis): flores e tufos nas margens das trilhas, samambaias
        String[] small = {"flower_white", "flower_yellow", "flower_blue", "flower_red", "grass_tall", "grass_tuft", "fern", "grass_tuft"};
        int smallLeft = 30 + vr.below(12);
        for (int k = 0; k < 900 && smallLeft > 0; k++) {
            float[] p = L.path.get(vr.below(L.path.size()));
            float ang = vr.range(0f, 6.283f), d = vr.range(1.0f, 2.4f);
            float x = p[0] + (float) Math.cos(ang) * d, z = p[1] + (float) Math.sin(ang) * d;
            if (f.pathDistance(x, z) < 0.95f || f.pondDistance(x, z) < 3.4f || crowded(taken, x, z, 0.5f)) continue;
            L.props.add(new RegionLayout.Prop(small[vr.below(small.length)], x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, 0.45f});
            smallLeft--;
        }
        for (int i = 0; i < 4; i++) {
            float ang = vr.range(0f, 6.283f), d = L.clearingR + vr.range(0.5f, 1.4f);
            float x = L.px + (float) Math.cos(ang) * d, z = L.pz + (float) Math.sin(ang) * d;
            if (f.pathDistance(x, z) < 1.2f || crowded(taken, x, z, 0.8f)) continue;
            L.props.add(new RegionLayout.Prop("mushroom_red", x, z, vr.range(0f, 360f)));
            taken.add(new float[]{x, z, 0.8f});
        }
        if (L.pond != null) for (int i = 0; i < 6; i++) {
            float ang = vr.range(0f, 6.283f), d = vr.range(3.0f, 4.0f);
            float x = L.pond[0] + (float) Math.cos(ang) * d, z = L.pond[1] + (float) Math.sin(ang) * d / 1.2f;
            String n = i % 3 == 2 ? "lily" : "reeds";
            if (n.equals("lily")) { x = L.pond[0] + (float) Math.cos(ang) * 1.8f; z = L.pond[1] + (float) Math.sin(ang) * 1.5f; }
            L.props.add(new RegionLayout.Prop(n, x, z, vr.range(0f, 360f)));
        }
    }
}
