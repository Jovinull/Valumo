package vx.engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Janela GLFW + contexto OpenGL 3.3 core + estado de entrada por quadro.
 * Com {@code visible=false} a janela nunca é exibida (captura offscreen).
 */
public final class Window implements AutoCloseable {
    public final long handle;
    private int fbWidth, fbHeight;

    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private final Deque<Integer> pressed = new ArrayDeque<>();
    private final boolean[] buttons = new boolean[8];
    private double mouseX, mouseY, lastX, lastY;
    private double dx, dy, scroll;
    private boolean firstMouse = true;

    public Window(String title, int width, int height, boolean visible) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("glfwInit falhou");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, visible ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new IllegalStateException("não foi possível criar a janela/contexto OpenGL 3.3");
        // Wayland não permite posicionar janelas; nas demais plataformas, centraliza.
        if (visible && glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vm != null) glfwSetWindowPos(handle, Math.max(0, (vm.width() - width) / 2), Math.max(0, (vm.height() - height) / 2));
        }
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        GL.createCapabilities();

        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(handle, w, h);
        fbWidth = w[0];
        fbHeight = h[0];

        glfwSetFramebufferSizeCallback(handle, (win, nw, nh) -> {
            fbWidth = nw;
            fbHeight = nh;
        });
        glfwSetKeyCallback(handle, (win, key, sc, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) {
                keys[key] = true;
                pressed.add(key);
            } else if (action == GLFW_RELEASE) {
                keys[key] = false;
            }
        });
        glfwSetMouseButtonCallback(handle, (win, b, action, mods) -> {
            if (b >= 0 && b < buttons.length) buttons[b] = action != GLFW_RELEASE;
        });
        glfwSetCursorPosCallback(handle, (win, x, y) -> {
            mouseX = x;
            mouseY = y;
        });
        glfwSetScrollCallback(handle, (win, sx, sy) -> scroll += sy);
    }

    /** Processa eventos; deve ser chamado uma vez por quadro, antes de ler a entrada. */
    public void poll() {
        pressed.clear();
        scroll = 0;
        glfwPollEvents();
        if (firstMouse) {
            lastX = mouseX;
            lastY = mouseY;
            firstMouse = false;
        }
        dx = mouseX - lastX;
        dy = mouseY - lastY;
        lastX = mouseX;
        lastY = mouseY;
    }

    public void swap() { glfwSwapBuffers(handle); }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void requestClose() { glfwSetWindowShouldClose(handle, true); }
    public void setTitle(String t) { glfwSetWindowTitle(handle, t); }
    public void setVsync(boolean on) { glfwSwapInterval(on ? 1 : 0); }

    public int width() { return fbWidth; }
    public int height() { return fbHeight; }
    public boolean key(int k) { return keys[k]; }
    /** Teclas pressionadas neste quadro (borda de subida). */
    public Iterable<Integer> pressedKeys() { return pressed; }
    public boolean button(int b) { return buttons[b]; }
    public double mouseDx() { return dx; }
    public double mouseDy() { return dy; }
    public double scroll() { return scroll; }
    public double time() { return glfwGetTime(); }

    @Override
    public void close() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
