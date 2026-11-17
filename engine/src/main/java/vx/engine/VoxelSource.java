package vx.engine;

/**
 * Volume de voxels consultável. {@link #get} devolve 0 para vazio ou
 * {@code ((material+1) << 24) | 0xRRGGBB} para um voxel sólido.
 */
public interface VoxelSource {
    int sizeX();
    int sizeY();
    int sizeZ();
    int get(int x, int y, int z);
}
