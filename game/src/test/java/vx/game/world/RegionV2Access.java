package vx.game.world;

/** Acesso dos testes de {@code vx.game} às constantes de pacote do gerador v2 (cópias: o teste não altera nada). */
public final class RegionV2Access {
    private RegionV2Access() { }

    public static float[] gnarledCenter() { return RegionV2.GNARLED_CENTER.clone(); }

    public static float[] gnarledHalf() { return RegionV2.GNARLED_HALF.clone(); }

    /** Rodada 12.2 — quanto o terreno natural sobe da frente aos fundos da abóbada que a v2 exige. */
    public static float vaultRise() { return RegionV2.VAULT_RISE; }

    /** Rodada 12.2 — um ponto do espaço do modelo no mundo (a mesma conta do gerador). */
    public static float[] world(float x, float z, float rotDeg, float lx, float lz) { return RegionV2.world(x, z, rotDeg, lx, lz); }

    /** Rodada 12.2 — o terreno natural (sem patamar nem trilha) do campo da região. */
    public static float natural(vx.viewer.TerrainField f, float x, float z) { return ((ProceduralField) f).natural(x, z); }
}
