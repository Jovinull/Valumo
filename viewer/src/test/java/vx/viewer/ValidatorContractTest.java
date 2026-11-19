package vx.viewer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fechamento do QA de engenharia: um validador só aprova o que conferiu. Golpe inexistente, rig sem volumes, rig sem item
 * e exceção não tratada nunca saem como "ok" (antes: uma amostra em repouso e saída 0). Rode build_assets antes.
 */
class ValidatorContractTest {
    static final Path A = Path.of("assets");

    static CharacterModel rig(String dir) { return CharacterModel.loadSkeleton(A.resolve(dir)); }

    @Test
    void aMoveThatDoesNotExistIsAnErrorInEveryValidator() {
        CharacterModel adv = rig("characters/e/adventurer"), ranger = rig("characters/e/ranger");
        assertThrows(IllegalArgumentException.class, () -> WeaponCheck.validate(adv, "golpe_x"));
        assertThrows(IllegalArgumentException.class, () -> OcclusionCheck.run(adv, "golpe_x"));
        assertThrows(IllegalArgumentException.class, () -> GripProbe.probe(ranger, "golpe_x", "bow", "farm_l", 1f, false));
        assertThrows(IllegalArgumentException.class, () -> GripProbe.probe(ranger, "clip:NADA", "bow", "farm_l", 1f, false));
    }

    @Test
    void aRigWithoutVolumesIsNotApproved() {
        CharacterModel m = rig("characters/e/adventurer");
        assertTrue(WeaponCheck.validate(m, "slash_h").ok(), "com volumes o golpe passa");
        m.meta.remove("volumes");
        WeaponCheck.Report r = WeaponCheck.validate(m, "slash_h");
        assertFalse(r.ok(), "sem meta.volumes nenhuma penetração foi medida: " + r);
    }

    @Test
    void occlusionCountsWhatItMeasured() {
        OcclusionCheck.Result npc = OcclusionCheck.run(rig("characters/npc/kelyra"), "emote");
        assertEquals(0, npc.samples(), "NPC sem arma: nada a medir");
        OcclusionCheck.Result hero = OcclusionCheck.run(rig("characters/e/adventurer"), "slash_h");
        assertTrue(hero.samples() > 100, "o golpe inteiro foi amostrado: " + hero.samples());
    }

    @Test
    void theDocumentedFaceExceptionStillHappensAndIsTheOnlyOne() {
        // se o arco deixar de cobrir o rosto, a exceção ficou obsoleta: tire-a da lista e do contrato de qualidade
        assertEquals(java.util.Set.of("bow_shot"), OcclusionCheck.FACE_EXCEPTIONS.keySet());
        assertFalse(OcclusionCheck.run(rig("characters/e/ranger"), "bow_shot").faceOk());
    }

    @Test
    void theCommandLineTurnsExceptionsAndFalseIntoFailure() {
        assertEquals(0, Main.validator("t", () -> true));
        assertEquals(1, Main.validator("t", () -> false));
        assertEquals(1, Main.validator("t", () -> { throw new IllegalStateException("asset ausente"); }));
    }
}
