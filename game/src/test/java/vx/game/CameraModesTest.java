package vx.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.2 — as câmeras de luta nos MESMOS 10 cenários em movimento da rodada 09.1 (CombatCameraTest.scenarios: campo
 * aberto, pedra, carvalho, toco, encosta, colado, de costas, giro rápido, defesa e dano junto à pedra), perfil C:
 * <ul>
 *   <li>09.1 — ajuste automático (viés de 35° e inclinação com o fungo colado; hoje só para comparar);</li>
 *   <li>livre — padrão da 09.2: a câmera da exploração, o mouse manda. Medida com o jogador mirando o fungo no centro
 *       (o pior caso: ele fica atrás da cabeça) e com 20° de folga, como faz quem joga;</li>
 *   <li>travada — Q aos 0,3 s: rumo atrás do herói em relação ao alvo, alvo 16° à direita, sem vista de cima.</li>
 * </ul>
 * "Ruim" = herói ocupando mais de 20% da tela ou fungo menos de 50% visível (CameraFraming).
 */
class CameraModesTest {
    record Mode(String name, List<CombatCameraTest.Run> runs) {
        double mean() { return runs.stream().mapToDouble(CombatCameraTest.Run::badFrac).average().orElse(0); }
        int inside() { return runs.stream().mapToInt(CombatCameraTest.Run::insideFrames).sum(); }
        double meanDist() { return runs.stream().mapToDouble(CombatCameraTest.Run::meanDist).average().orElse(0); }
    }

    static Mode mode(String name, boolean assist, boolean lock, float bias) { return mode(name, assist, lock, bias, false); }

    static Mode mode(String name, boolean assist, boolean lock, float bias, boolean screen) {
        FollowCamera.combatAssist = assist;
        CombatCameraTest.LOCK = lock;
        CombatCameraTest.AIM_BIAS = bias;
        CombatCameraTest.AIM_SCREEN = screen;
        System.setProperty("vx.sensacao", "C");
        System.setProperty("vx.encontro", "um");
        try { return new Mode(name, CombatCameraTest.scenarios()); }
        finally {
            FollowCamera.combatAssist = false;
            CombatCameraTest.LOCK = false;
            CombatCameraTest.AIM_BIAS = 0f;
            CombatCameraTest.AIM_SCREEN = false;
            System.clearProperty("vx.sensacao");
            System.clearProperty("vx.encontro");
        }
    }

    @Test
    void theLockedCameraKeepsTheFightReadableAndTheFreeOneStaysTheExplorationCamera() {
        Mode a = mode("09.1 ajuste automático", true, false, 0f), free = mode("livre, herói e fungo alinhados", false, false, 0f),
                freeScreen = mode("livre, fungo no centro da tela", false, false, 0f, true),
                freeOff = mode("livre, centro + 10° de folga", false, false, (float) Math.toRadians(10), true), lock = mode("travada", false, true, 0f);
        for (Mode m : List.of(a, free, freeScreen, freeOff, lock)) {
            System.out.printf("câmera %-24s tempo ruim médio %5.1f%%, câmera dentro %d quadros, distância média %.2f u%n", m.name(), 100 * m.mean(), m.inside(), m.meanDist());
            for (CombatCameraTest.Run r : m.runs()) System.out.println("   " + r);
        }
        // travada: legível no movimento (alvo à vista, herói sem dominar), sem câmera dentro de obstáculo, sem vista de cima
        // limites = o estado medido na entrega (regressão), não a meta: a travada ainda fica atrás da 09.1 (P41)
        assertTrue(lock.mean() < 0.25, String.format("travada: %.1f%% do tempo ruim", 100 * lock.mean()));
        assertTrue(lock.mean() < free.mean() / 2, "travada bem melhor que a livre com herói e fungo alinhados");
        assertTrue(lock.inside() <= 30, "travada: câmera dentro de obstáculo em " + lock.inside() + " quadros");
        assertTrue(lock.meanDist() > 3.2, "travada: não aproxima demais (" + lock.meanDist() + " u)");
        // livre: o mouse manda (nada de giro nem subida automáticos). Os quadros "dentro" que restam são a câmera
        // encurralada entre a pedra grande e o herói na encosta (dentro da cápsula do herói, que o desenho deixa
        // translúcido) — problema aberto P42; o limite é o estado medido, não a meta
        assertTrue(free.inside() + freeScreen.inside() + freeOff.inside() <= 200, "livre: câmera dentro em " + (free.inside() + freeScreen.inside() + freeOff.inside()));
    }
}
