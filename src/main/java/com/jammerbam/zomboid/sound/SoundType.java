package com.jammerbam.zomboid.sound;

public enum SoundType {
    WALK(AcousticProfile.LIGHT_FOOTSTEP),
    SPRINT(AcousticProfile.HEAVY_FOOTSTEP),
    JUMP(AcousticProfile.HEAVY_FOOTSTEP),
    LANDING(AcousticProfile.HEAVY_FOOTSTEP),
    BLOCK_BREAK(AcousticProfile.STRUCTURAL_IMPACT),
    BLOCK_PLACE(AcousticProfile.CONSTRUCTION_IMPACT),
    COMBAT(AcousticProfile.COMBAT_IMPACT),
    DEBUG(AcousticProfile.NEUTRAL);

    private final AcousticProfile acousticProfile;

    SoundType(AcousticProfile acousticProfile) {
        this.acousticProfile = acousticProfile;
    }

    public AcousticProfile getAcousticProfile() {
        return acousticProfile;
    }
}
