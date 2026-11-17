package vx.engine;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;

/** Lê o framebuffer atualmente ligado e grava PNG. É a única fonte das capturas de validação. */
public final class Screenshot {
    private Screenshot() { }

    public static BufferedImage read(int width, int height) {
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int i = (x + (height - 1 - y) * width) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        return img;
    }

    public static void save(BufferedImage img, Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ImageIO.write(img, "png", file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("falha ao gravar " + file, e);
        }
    }
}
