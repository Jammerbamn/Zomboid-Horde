package com.jammerbam.zomboid.performance;

/** The behavior that synchronously asked Minecraft's vanilla navigator for a route. */
public enum VanillaPathRequestSource {
    PERSONAL_WANDER("personalWander"),
    RETURN_TO_ANCHOR("returnToAnchor"),
    SOUND_INVESTIGATION("soundInvestigation"),
    LAST_KNOWN_POSITION("lastKnownPosition"),
    ALERT_LEADER("alertLeader"),
    PLAYER_PURSUIT_FALLBACK("playerPursuitFallback");

    private final String label;

    VanillaPathRequestSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
