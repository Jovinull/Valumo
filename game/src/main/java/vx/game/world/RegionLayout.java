package vx.game.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Rodada 11 — o que a semente determinou para UMA região: parâmetros do relevo, o assentamento, a trilha, os lugares
 * (POIs), o lago, os props e os NPCs já posicionados, o encontro e o marco. É dado puro (sem OpenGL, sem regra de jogo);
 * {@link RegionGenerator} o cria e o {@code ProceduralScene} o transforma em cena jogável. Unidades: u (mundo), graus.
 */
public final class RegionLayout {
    public final long seed;
    /** Tentativa de composição que passou na validação (0 = a primeira). */
    public final int attempt;
    public final String regionId;
    /**
     * Rodada 12 (ADR 0029) — a versão do gerador que compôs esta região. Cada mundo usa para sempre a versão com que
     * nasceu: o relevo, a cena e o save leem a versão DAQUI, nunca da mais nova.
     */
    public final int version;

    // relevo
    public float noiseX, noiseZ, amplitude, rimN, rimS, rimE, rimW;
    // assentamento: centro, eixo da saída da trilha (unitário), patamar
    public float sx, sz, fx, fz, plateauR, plateauH;
    public int arrangement;
    // lugar da missão (clareira do posto): centro, eixo de chegada (unitário, do caminho para dentro), patamar
    public float px, pz, gx, gz, clearingR, clearingH;
    /** Eixo da trilha, pontos a cada ~0,5 u (do assentamento à clareira). */
    public final List<float[]> path = new ArrayList<>();
    /** Lago: centro (x, z) ou null. Colina: (x, z, raio, altura) ou null. Mata: 0 carvalhal, 1 pinhal, 2 bétulas. */
    public float[] pond, hill;
    public int forest;

    public record Prop(String name, float x, float z, float rotDeg) { }

    /** NPC colocado: id (pasta do rig), posição e rumo (graus). */
    public record Npc(String id, float x, float z, float yawDeg) { }

    /** Inimigo de missão: identidade estável, variante, posição, rumo. */
    public record Spawn(String key, String variant, float x, float z, float yawDeg) { }

    /** Objeto com estado (o marco): id estável, posição, rumo. */
    public record Feature(String id, String kind, float x, float z, float yawDeg) { }

    /**
     * Lugar reconhecível. {@code type}: settlement | trail_post (os do protótipo). {@code state}: estado inicial
     * gerado ("sinal_apagado"); o que o jogador muda fica no save, não aqui.
     */
    public record PointOfInterest(String id, String type, String region, float x, float z, float radius, String state,
                                  List<String> features, List<String> spawns) { }

    // ------------------------------------------------------------------ rodada 12: só a v2 preenche (a v1 deixa vazio)

    /** Estilo do assentamento da v2: "caminho" (vila de caminho, compacta, com praça) ou "rural" (sítios espaçados). */
    public String style;
    /** Rodada 12.1 (v2, P81): o porte da vila de caminho ("pequena" ou "media"); a rural não tem ("—"). */
    public String size = "";

    /** Patamar plano (x, z, raio, altura) de um lugar fora do assentamento (a ruína). */
    public final List<float[]> pads = new ArrayList<>();

    /**
     * Trilha secundária: pontos a cada ~0,5 u, alturas na ponta de partida e na de chegada; {@code carve} = o relevo segue
     * a trilha (a da ruína); senão só pinta o chão (os caminhos da praça até as portas, no patamar plano).
     */
    public record Branch(List<float[]> points, float h0, float h1, boolean carve) { }

    public final List<Branch> branches = new ArrayList<>();

    /** Construção (ou conjunto de ofício) posta pelo gerador: asset, conjunto (ou null), posição, rumo, papel. */
    public record Building(String asset, String set, float x, float z, float rotDeg, String role) { }

    public final List<Building> buildings = new ArrayList<>();

    /** Lugar que a conferência de alcance exige (entrada, ponto de interação, descoberta): alguém chega a {@code within} u. */
    public record Checkpoint(String name, float x, float z, float within) { }

    public final List<Checkpoint> checkpoints = new ArrayList<>();

    /**
     * Rodada 12.1 (v2): o que cada descoberta da região é. {@code type}: o tipo de lugar ("casa", "torre", "arvore": a
     * chave em {@code descobertas_e.json}); {@code y}: a altura do ponto acima da origem do modelo (o pote na laje, o mirante
     * em cima da muralha); {@code yawDeg}: o rumo do objeto do achado. A {@link Feature} de mesmo id dá a posição. A v1 não
     * tem nenhuma.
     */
    public record DiscoverySpot(String id, String type, float y, float yawDeg) { }

    /**
     * Rodada 12.1 (v2, P76): quem trabalha num conjunto — o morador, o ofício, o conjunto e o spawn da âncora onde ele fica.
     * O mesmo morador também está em {@link #npcs} (posição e rumo).
     */
    /** Rodada 12.1; 12.2: {@code y} = altura do ponto acima do chão (a laje da oficina), {@code spawn} = a origem do ponto. */
    public record Worker(String npc, String trade, String set, String spawn, float y) { }
    public final List<Worker> workers = new ArrayList<>();
    public final List<DiscoverySpot> discoverySpots = new ArrayList<>();

    public final List<Prop> props = new ArrayList<>();
    public final List<Npc> npcs = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<Feature> features = new ArrayList<>();
    public final List<PointOfInterest> pois = new ArrayList<>();
    /** Círculos jogáveis (x, z, raio): assentamento, trilha e clareira. */
    public final List<float[]> limits = new ArrayList<>();
    public float startX, startZ, startYawDeg, restartX, restartZ, restartYawDeg;
    /** Raio da arena (a clareira do posto). */
    public float arenaR;

    RegionLayout(long seed, int attempt, String regionId, int version) {
        this.seed = seed;
        this.attempt = attempt;
        this.regionId = regionId;
        this.version = version;
    }

    /** Identidade da região gerada (semente, versão, região): "s1024.g1.r0". A mesma semente em outra versão é outra região. */
    public String key() { return "s" + Long.toUnsignedString(seed) + ".g" + version + "." + regionId; }

    public PointOfInterest poi(String type) {
        for (PointOfInterest p : pois) if (p.type().equals(type)) return p;
        return null;
    }

    public Feature feature(String id) {
        for (Feature f : features) if (f.id().equals(id)) return f;
        return null;
    }
}
