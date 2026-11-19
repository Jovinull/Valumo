package vx.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import vx.engine.audio.Pcm;
import vx.engine.audio.VorbisSource;
import vx.engine.audio.Wav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fechamento do QA de engenharia — arquivos malformados (.vox, .wav, .ogg) viram erro com o motivo, em pouco tempo e sem
 * alocação descontrolada. Antes: um tamanho de bloco negativo voltava a posição e o leitor girava para sempre. Fixtures
 * mínimas escritas aqui, numa pasta temporária (nunca arquivos do jogador nem assets reais).
 */
@Timeout(10)
class CorruptFilesTest {

    // ------------------------------------------------------------------ .vox

    static byte[] chunk(String id, int declaredContent, int declaredChildren, byte[] body) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(id.getBytes());
        o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(declaredContent).putInt(declaredChildren).array());
        o.write(body);
        return o.toByteArray();
    }

    static byte[] le(int... v) {
        ByteBuffer b = ByteBuffer.allocate(4 * v.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int x : v) b.putInt(x);
        return b.array();
    }

    /** VOX com os blocos dados dentro de MAIN. */
    static Path vox(Path dir, byte[]... kids) throws IOException {
        ByteArrayOutputStream k = new ByteArrayOutputStream();
        for (byte[] c : kids) k.write(c);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("VOX ".getBytes());
        o.write(le(150));
        o.write(chunk("MAIN", 0, k.size(), new byte[0]));
        o.write(k.toByteArray());
        Path f = dir.resolve("x.vox");
        Files.write(f, o.toByteArray());
        return f;
    }

    static byte[] size(int x, int y, int z) throws IOException { return chunk("SIZE", 12, 0, le(x, y, z)); }

    static byte[] rgba() throws IOException { return chunk("RGBA", 1024, 0, new byte[1024]); }

    static byte[] xyzi(int declared, byte[] voxels) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(le(declared));
        b.write(voxels);
        return chunk("XYZI", b.size(), 0, b.toByteArray());
    }

    static void badVox(Path f, String why) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> VoxModel.load(f), why);
        assertTrue(e.getMessage().contains(f.toString()), "a mensagem diz qual arquivo: " + e.getMessage());
    }

    @Test
    void aWellFormedVoxStillLoads(@TempDir Path d) throws IOException {
        VoxModel m = VoxModel.load(vox(d, size(2, 2, 2), xyzi(1, new byte[]{1, 1, 1, 5}), rgba()));
        assertEquals(2, m.sizeX());
        assertNotEquals(0, m.get(1, 1, 0));
    }

    @Test
    void negativeChunkSizeIsAnErrorNotAnEndlessLoop(@TempDir Path d) throws IOException {
        badVox(vox(d, size(2, 2, 2), chunk("nTRN", -12, 0, new byte[0]), rgba()), "tamanho negativo");
        badVox(vox(d, size(2, 2, 2), chunk("nTRN", 0, -5, new byte[0]), rgba()), "filhos negativos");
    }

    @Test
    void chunksThatRunPastTheEndAreRejected(@TempDir Path d) throws IOException {
        badVox(vox(d, size(2, 2, 2), chunk("XYZI", 4000, 0, le(1))), "XYZI além do fim");
        badVox(vox(d, chunk("SIZE", 4, 0, le(2))), "SIZE curto");
        badVox(vox(d, size(2, 2, 2), xyzi(1, new byte[]{0, 0, 0, 1}), chunk("RGBA", 16, 0, new byte[16])), "paleta curta");
    }

    @Test
    void absurdDimensionsAndVoxelsOutsideTheModelAreRejected(@TempDir Path d) throws IOException {
        badVox(vox(d, size(100000, 100000, 100000), xyzi(0, new byte[0]), rgba()), "dimensão enorme (alocaria GB)");
        badVox(vox(d, size(0, 2, 2), xyzi(0, new byte[0]), rgba()), "dimensão zero");
        badVox(vox(d, size(2, 2, 2), xyzi(1, new byte[]{9, 0, 0, 1}), rgba()), "voxel fora do tamanho");
        badVox(vox(d, size(2, 2, 2), xyzi(1000000, new byte[]{0, 0, 0, 1}), rgba()), "contagem maior que o bloco");
        badVox(vox(d, size(2, 2, 2), xyzi(-3, new byte[0]), rgba()), "contagem negativa");
        badVox(vox(d, xyzi(1, new byte[]{0, 0, 0, 1}), rgba()), "XYZI antes de SIZE");
    }

    @Test
    void emptyGarbageAndMissingPartsAreRejected(@TempDir Path d) throws IOException {
        Path f = d.resolve("x.vox");
        Files.write(f, new byte[0]);
        badVox(f, "vazio");
        byte[] junk = new byte[4096];
        new Random(7).nextBytes(junk);
        Files.write(f, junk);
        badVox(f, "lixo");
        badVox(vox(d, size(2, 2, 2), rgba()), "sem XYZI");
        badVox(vox(d, size(2, 2, 2), xyzi(0, new byte[0])), "sem paleta");
    }

    // ------------------------------------------------------------------ .wav

    /** RIFF/WAVE com os blocos dados. */
    static Path wav(Path dir, byte[]... chunks) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] c : chunks) body.write(c);
        ByteBuffer h = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46464952).putInt(4 + body.size()).putInt(0x45564157);
        Path f = dir.resolve("x.wav");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(h.array());
        o.write(body.toByteArray());
        Files.write(f, o.toByteArray());
        return f;
    }

    static byte[] riff(int id, int declared, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN).putInt(id).putInt(declared).put(body);
        return b.array();
    }

    static final int FMT = 0x20746d66, DATA = 0x61746164;

    static byte[] fmt(int channels, int rate) {
        ByteBuffer b = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 1).putShort((short) channels).putInt(rate).putInt(rate * channels * 2)
                .putShort((short) (channels * 2)).putShort((short) 16);
        return riff(FMT, 16, b.array());
    }

    static void badWav(Path f, String why) {
        assertThrows(IOException.class, () -> Pcm.readWav(f), "Pcm: " + why);
        assertThrows(IOException.class, () -> Wav.read(f), "Wav: " + why);
    }

    @Test
    void aWellFormedWavStillLoadsInBothReaders(@TempDir Path d) throws IOException {
        Path f = wav(d, fmt(1, 48000), riff(DATA, 8, new byte[8]));
        assertEquals(4, Pcm.readWav(f).frames());
        assertEquals(4, Wav.read(f).samples.length);
    }

    @Test
    void negativeOrTruncatedWavChunksAreErrorsNotLoops(@TempDir Path d) throws IOException {
        badWav(wav(d, riff(0x5453494c, -16, new byte[0]), fmt(1, 48000), riff(DATA, 4, new byte[4])), "bloco negativo antes do fmt");
        badWav(wav(d, riff(FMT, 4, new byte[4])), "fmt curto");
        badWav(wav(d, fmt(0, 48000), riff(DATA, 4, new byte[4])), "zero canais");
        badWav(wav(d, fmt(1, 0), riff(DATA, 4, new byte[4])), "taxa zero");
        badWav(wav(d, fmt(1, 48000)), "sem dados");
        badWav(wav(d, fmt(1, 48000), riff(DATA, -4, new byte[0])), "data negativo");
    }

    @Test
    void aDataChunkLongerThanTheFileIsReadUpToTheEnd(@TempDir Path d) throws IOException {
        Path f = wav(d, fmt(1, 48000), riff(DATA, 2_000_000_000, new byte[6]));   // gravação interrompida
        assertEquals(3, Pcm.readWav(f).frames());
        assertEquals(3, Wav.read(f).samples.length);
    }

    @Test
    void emptyAndGarbageWavAreRejected(@TempDir Path d) throws IOException {
        Path f = d.resolve("x.wav");
        Files.write(f, new byte[0]);
        badWav(f, "vazio");
        byte[] junk = new byte[2048];
        new Random(3).nextBytes(junk);
        Files.write(f, junk);
        badWav(f, "lixo");
    }

    // ------------------------------------------------------------------ .ogg

    @Test
    void emptyAndGarbageOggAreRejected(@TempDir Path d) throws IOException {
        Path f = d.resolve("x.ogg");
        Files.write(f, new byte[0]);
        assertThrows(IOException.class, () -> new VorbisSource(f), "vazio");
        byte[] junk = new byte[8192];
        new Random(11).nextBytes(junk);
        System.arraycopy("OggS".getBytes(), 0, junk, 0, 4);                  // começa como OGG e é lixo depois
        Files.write(f, junk);
        assertThrows(IOException.class, () -> new VorbisSource(f), "lixo com cabeçalho OggS");
    }
}
