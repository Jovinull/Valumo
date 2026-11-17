package vx.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Leitura do formato .vox e convenções do projeto (eixos, materiais por faixa de paleta). */
class VoxModelTest {
    private static byte[] chunk(String id, byte[] content, byte[] children) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(id.getBytes());
        o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(content.length).putInt(children.length).array());
        o.write(content);
        o.write(children);
        return o.toByteArray();
    }

    /** .vox 2x3x4 (x, y, z no espaço Z-para-cima) com um voxel fosco e um metálico. */
    private static Path write(Path dir) throws IOException {
        byte[] size = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(2).putInt(3).putInt(4).array();
        ByteBuffer xyzi = ByteBuffer.allocate(4 + 8).order(ByteOrder.LITTLE_ENDIAN).putInt(2);
        xyzi.put((byte) 1).put((byte) 0).put((byte) 3).put((byte) 5);        // fosco, índice 5
        xyzi.put((byte) 0).put((byte) 2).put((byte) 0).put((byte) 180);      // metal, índice 180
        byte[] pal = new byte[1024];
        pal[4 * 4] = (byte) 0x11; pal[4 * 4 + 1] = (byte) 0x22; pal[4 * 4 + 2] = (byte) 0x33;         // índice 5
        pal[179 * 4] = (byte) 0xAA; pal[179 * 4 + 1] = (byte) 0xBB; pal[179 * 4 + 2] = (byte) 0xCC;   // índice 180
        ByteArrayOutputStream kids = new ByteArrayOutputStream();
        kids.write(chunk("SIZE", size, new byte[0]));
        kids.write(chunk("XYZI", xyzi.array(), new byte[0]));
        kids.write(chunk("RGBA", pal, new byte[0]));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("VOX ".getBytes());
        o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(150).array());
        o.write(chunk("MAIN", new byte[0], kids.toByteArray()));
        Path f = dir.resolve("t.vox");
        Files.write(f, o.toByteArray());
        return f;
    }

    @Test
    void axesAreConvertedToYUp(@TempDir Path dir) throws IOException {
        VoxModel m = VoxModel.load(write(dir));
        // vox (sx=2, sy=3, sz=4)  ->  engine (x=2, y=4, z=3)
        assertEquals(2, m.sizeX());
        assertEquals(4, m.sizeY());
        assertEquals(3, m.sizeZ());
        // vox (1,0,3) -> engine (1, 3, 3-1-0 = 2)
        assertEquals(0x112233, m.get(1, 3, 2) & 0xFFFFFF);
        // vox (0,2,0) -> engine (0, 0, 0)
        assertEquals(0xAABBCC, m.get(0, 0, 0) & 0xFFFFFF);
        assertEquals(0, m.get(1, 0, 0));
    }

    @Test
    void materialComesFromPaletteRange(@TempDir Path dir) throws IOException {
        VoxModel m = VoxModel.load(write(dir));
        assertEquals(VoxModel.MAT_MATTE + 1, m.get(1, 3, 2) >>> 24);
        assertEquals(VoxModel.MAT_METAL + 1, m.get(0, 0, 0) >>> 24);
        assertEquals(VoxModel.MAT_EMISSIVE, VoxModel.materialOf(200));
        assertEquals(VoxModel.MAT_FOLIAGE, VoxModel.materialOf(249));
        assertEquals(VoxModel.MAT_WATER, VoxModel.materialOf(250));
    }
}
