package vx.game;

import vx.engine.Window.InputEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 08 — entrada de um quadro, montada só a partir da fila de eventos da janela (a mesma que os roteiros
 * alimentam): teclas seguradas, teclas apertadas neste quadro (sem repetição automática), deslocamento do
 * mouse, roda e cliques.
 */
public final class Input {
    public final Set<Integer> down = new HashSet<>();
    public final List<Integer> pressed = new ArrayList<>();
    public float mouseX = -1, mouseY = -1, mdx, mdy, wheel;
    public final List<float[]> clicks = new ArrayList<>();         // {x, y} de cliques esquerdos (soltar)
    public boolean rightDown;
    /** Rodada 09: botão esquerdo APERTADO neste quadro (o golpe sai no aperto, não no soltar). */
    public int leftPressed;
    /** Rodada 09.2: botão do meio apertado neste quadro (esquiva alternativa ao Alt). */
    public int middlePressed;
    private boolean haveMouse;

    /** Consome os eventos do quadro (limpa o que é só deste quadro; mantém o que está segurado). */
    public void feed(List<InputEvent> events) {
        pressed.clear();
        clicks.clear();
        mdx = mdy = wheel = 0;
        leftPressed = 0;
        middlePressed = 0;
        for (InputEvent e : events) {
            switch (e.kind()) {
                case KEY -> {
                    down.add(e.code());
                    if (e.button() == 0) pressed.add(e.code());          // repetição automática não conta como novo aperto
                }
                case KEY_UP -> down.remove(e.code());
                case MOVE -> {
                    if (haveMouse) { mdx += e.x() - mouseX; mdy += e.y() - mouseY; }
                    mouseX = e.x();
                    mouseY = e.y();
                    haveMouse = true;
                }
                case DOWN -> {
                    if (e.button() == 1) rightDown = true;
                    if (e.button() == 0) leftPressed++;
                    if (e.button() == 2) middlePressed++;
                }
                case UP -> {
                    if (e.button() == 0) clicks.add(new float[]{e.x(), e.y()});
                    if (e.button() == 1) rightDown = false;
                }
                case SCROLL -> wheel += e.scroll();
                default -> { }
            }
        }
    }

    /** Esquece a posição anterior do mouse (ao prender/soltar o cursor, para não dar salto). */
    public void resetMouse() { haveMouse = false; }

    public boolean held(int... keys) {
        for (int k : keys) if (down.contains(k)) return true;
        return false;
    }

    public boolean hit(int... keys) {
        for (int k : keys) if (pressed.contains(k)) return true;
        return false;
    }

    /** Intenção de movimento da câmera: x = direita, y = frente (−1..1). */
    public float[] moveAxes() {
        float f = (held(GLFW_KEY_W, GLFW_KEY_UP) ? 1 : 0) - (held(GLFW_KEY_S, GLFW_KEY_DOWN) ? 1 : 0);
        float r = (held(GLFW_KEY_D, GLFW_KEY_RIGHT) ? 1 : 0) - (held(GLFW_KEY_A, GLFW_KEY_LEFT) ? 1 : 0);
        return new float[]{r, f};
    }

    public boolean running() { return held(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT); }
}
