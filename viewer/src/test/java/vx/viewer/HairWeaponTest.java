package vx.viewer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.4 — todo penteado NOVO do catálogo (estudo experimental ou já aprovado), em cada corpo armado, não pode
 * ser atravessado pela arma mais que o penteado do próprio preset. Medição exata, voxel a voxel ({@link HairWeaponCheck}).
 */
class HairWeaponTest {
    static final Composer C = new Composer(Path.of("assets"), 2);

    @Test
    void newHairIsNeverCutMoreThanThePresetHair() {
        int checked = 0;
        for (String preset : C.catalog.presets.keySet()) {
            String ref = C.catalog.presets.get(preset).hair;
            HairWeaponCheck.Result r0 = HairWeaponCheck.measure(C, preset, ref);
            for (String hair : C.catalog.ids("cabelo")) {
                if (!HairWeaponCheck.isNew(C.catalog, hair)) continue;
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
    void newHairIsNeverCutMoreThanThePresetHairWithTheCombatKit() {
        // integração 09.3 × 09.4: no jogo, toda aparência luta com espada e broquel (cortes, guarda, reação, esquiva R2 e
        // recuo) — os estudos de cabelo também têm de passar com ESSE equipamento, não só com a arma de cada herói
        Composer k = new Composer(Path.of("assets"), 2);
        k.kit = HairWeaponCheck.COMBAT_KIT;
        int checked = 0;
        for (String preset : k.catalog.presets.keySet()) {
            String ref = k.catalog.presets.get(preset).hair;
            HairWeaponCheck.Result r0 = HairWeaponCheck.measure(k, preset, ref);
            for (String hair : k.catalog.ids("cabelo")) {
                if (!HairWeaponCheck.isNew(k.catalog, hair)) continue;
                HairWeaponCheck.Result r = HairWeaponCheck.measure(k, preset, hair);
                assertTrue(r.worstPen() <= Math.max(HairWeaponCheck.TOL, r0.worstPen()),
                        "kit de combate: " + preset + " × " + hair + ": " + r.worstPen() + " voxels na cabeça em " + r.worstWhere()
                                + " (referência " + ref + ": " + r0.worstPen() + ")");
                checked++;
            }
        }
        assertTrue(checked >= 6, "estudos medidos: " + checked);
    }

    @Test
    void theShieldIsMeasuredAndTheCombatDodgeLeavesEveryHairAndCoverFree() {
        // fechamento do QA: a sonda media só espada, arco e flecha. Com o broquel, a esquiva R2 de frente do kit de combate
        // roçava a ponta do chanel (5 voxels, 0,09–0,24 s) em todo corpo — corrigido na pose (sword_shield r2f_push/air)
        assertTrue(HairWeaponCheck.ITEMS.contains("shield"), "o broquel entra na medição");
        Composer k = new Composer(Path.of("assets"), 2);
        k.kit = HairWeaponCheck.COMBAT_KIT;
        int checked = 0;
        for (String preset : k.catalog.presets.keySet())
            for (String cover : java.util.List.of(Appearance.NO_COVER, "chapeu.maga", "capuz.c3")) {
                HairWeaponCheck.Result r = HairWeaponCheck.measure(k, preset, "cabelo.chanel", cover);
                assertNotNull(r, preset + " × chanel × " + cover);
                assertEquals(0, r.worstPen(), "kit de combate: " + preset + " × chanel × " + cover + " em " + r.worstWhere());
                checked++;
            }
        assertEquals(9, checked);
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
