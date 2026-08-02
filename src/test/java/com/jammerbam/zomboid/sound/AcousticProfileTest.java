package com.jammerbam.zomboid.sound;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AcousticProfileTest {
    @Test
    public void soundTypesSelectDistinctProfiles() {
        assertEquals(AcousticProfile.LIGHT_FOOTSTEP,
            SoundType.WALK.getAcousticProfile());
        assertEquals(AcousticProfile.HEAVY_FOOTSTEP,
            SoundType.SPRINT.getAcousticProfile());
        assertEquals(AcousticProfile.HEAVY_FOOTSTEP,
            SoundType.JUMP.getAcousticProfile());
        assertEquals(AcousticProfile.HEAVY_FOOTSTEP,
            SoundType.LANDING.getAcousticProfile());
        assertEquals(AcousticProfile.STRUCTURAL_IMPACT,
            SoundType.BLOCK_BREAK.getAcousticProfile());
    }

    @Test
    public void weakStructuralImpactIsLessPreciselyLocalizedThanFootstep() {
        int footstepError = AcousticProfile.LIGHT_FOOTSTEP
            .localizationUncertainty(0.25D);
        int structuralError = AcousticProfile.STRUCTURAL_IMPACT
            .localizationUncertainty(0.25D);

        assertTrue(structuralError > footstepError);
        assertEquals(0, AcousticProfile.LIGHT_FOOTSTEP
            .localizationUncertainty(1.0D));
    }

    @Test
    public void localizationQualityIsClamped() {
        assertEquals(
            AcousticProfile.COMBAT_IMPACT.localizationUncertainty(0.0D),
            AcousticProfile.COMBAT_IMPACT.localizationUncertainty(-5.0D)
        );
        assertEquals(
            AcousticProfile.COMBAT_IMPACT.localizationUncertainty(1.0D),
            AcousticProfile.COMBAT_IMPACT.localizationUncertainty(5.0D)
        );
    }
}
