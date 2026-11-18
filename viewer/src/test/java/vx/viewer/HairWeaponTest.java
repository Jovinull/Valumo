package vx.viewer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09 — todo penteado EXPERIMENTAL do catálogo, em cada corpo armado, não pode ser atravessado pela arma
 * mais que o penteado do próprio preset (aprovado). Medição exata, voxel a voxel ({@link HairWeaponCheck}).
 */
class HairWeaponTest {
    static final Composer C = new Composer(Path.of("assets"), 2);

    @Test
    void experimentalHairIsNeverCutMoreThanThePresetHair() {
        int checked = 0;
        for (String preset : C.catalog.presets.keySet()) {
            String ref = C.catalog.presets.get(preset).hair;
            HairWeaponCheck.Result r0 = HairWeaponCheck.measure(C, preset, ref);
            for (String hair : C.catalog.ids("cabelo")) {
                if (!C.catalog.components.get(hair).doc.has("experimental")) continue;
                HairWeaponCheck.Result r = HairWeaponCheck.measure(C, preset, hair);
                assertTrue(r.worstPen() <= Math.max(HairWeaponCheck.TOL, r0.worstPen()),
                        preset + " × " + hair + ": " + r.worstPen() + " voxels da arma na cabeça em " + r.worstWhere()
                                + " (referência " + ref + ": " + r0.worstPen() + ")");
                checked++;
            }
        }
        assertTrue(checked >= 6, "estudos medidos: " + checked);
    }

    @Test
    void theMeasureSeesARealContactAndARealGap() {
        // a exceção conhecida do arco (o tiro cobre o rosto) tem de aparecer como contato; o cabelo curto não toca
        assertTrue(HairWeaponCheck.measure(C, "exploradora", "cabelo.rabo").worstPen() > 0, "contato conhecido do arco");
        HairWeaponCheck.Result curto = HairWeaponCheck.measure(C, "exploradora", "cabelo.curto");
        assertEquals(0, curto.worstPen());
        assertTrue(curto.minGap() >= 1f, "folga " + curto.minGap());
    }
}
