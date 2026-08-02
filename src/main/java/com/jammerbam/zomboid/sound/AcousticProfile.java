package com.jammerbam.zomboid.sound;

import net.minecraft.block.material.Material;

/**
 * A low-cost spectral approximation for one sound category.
 *
 * Profiles keep one scalar wave per event. Multipliers above one represent
 * high-frequency sounds that barriers absorb readily; values below one represent
 * heavier impulses that retain more energy through dense materials.
 */
public enum AcousticProfile {
    LIGHT_FOOTSTEP(
        1.15D, 1.25D, 1.20D, 1.35D, 1.25D, 1.55D, 1.70D, 1.35D, 0, 3
    ),
    HEAVY_FOOTSTEP(
        1.00D, 1.10D, 1.05D, 0.95D, 0.90D, 0.85D, 0.80D, 0.90D, 1, 4
    ),
    STRUCTURAL_IMPACT(
        0.90D, 1.10D, 0.95D, 0.75D, 0.80D, 0.70D, 0.65D, 0.80D, 1, 8
    ),
    CONSTRUCTION_IMPACT(
        1.00D, 1.10D, 1.00D, 0.90D, 0.95D, 0.90D, 0.85D, 0.95D, 1, 5
    ),
    COMBAT_IMPACT(
        1.00D, 1.10D, 1.00D, 0.90D, 0.90D, 0.85D, 0.80D, 0.90D, 1, 6
    ),
    NEUTRAL(
        1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1, 6
    );

    private final double foliageMultiplier;
    private final double waterMultiplier;
    private final double glassMultiplier;
    private final double woodMultiplier;
    private final double earthMultiplier;
    private final double stoneMultiplier;
    private final double metalMultiplier;
    private final double otherMultiplier;
    private final int minimumUncertainty;
    private final int maximumUncertainty;

    AcousticProfile(double foliageMultiplier, double waterMultiplier,
                    double glassMultiplier, double woodMultiplier,
                    double earthMultiplier, double stoneMultiplier,
                    double metalMultiplier, double otherMultiplier,
                    int minimumUncertainty, int maximumUncertainty) {
        this.foliageMultiplier = foliageMultiplier;
        this.waterMultiplier = waterMultiplier;
        this.glassMultiplier = glassMultiplier;
        this.woodMultiplier = woodMultiplier;
        this.earthMultiplier = earthMultiplier;
        this.stoneMultiplier = stoneMultiplier;
        this.metalMultiplier = metalMultiplier;
        this.otherMultiplier = otherMultiplier;
        this.minimumUncertainty = Math.max(0, minimumUncertainty);
        this.maximumUncertainty = Math.max(this.minimumUncertainty, maximumUncertainty);
    }

    public double materialCostMultiplier(Material material) {
        if (material == Material.AIR || material == Material.FIRE
            || material == Material.PORTAL) {
            return 1.0D;
        }
        if (material == Material.LEAVES || material == Material.PLANTS
            || material == Material.VINE || material == Material.CARPET
            || material == Material.WEB) {
            return foliageMultiplier;
        }
        if (material == Material.WATER) {
            return waterMultiplier;
        }
        if (material == Material.GLASS || material == Material.ICE) {
            return glassMultiplier;
        }
        if (material == Material.WOOD || material == Material.CLOTH
            || material == Material.GOURD) {
            return woodMultiplier;
        }
        if (material == Material.GROUND || material == Material.GRASS
            || material == Material.SAND || material == Material.CLAY
            || material == Material.SNOW || material == Material.CRAFTED_SNOW) {
            return earthMultiplier;
        }
        if (material == Material.ROCK || material == Material.PISTON
            || material == Material.PACKED_ICE) {
            return stoneMultiplier;
        }
        if (material == Material.IRON || material == Material.ANVIL
            || material == Material.BARRIER) {
            return metalMultiplier;
        }
        return otherMultiplier;
    }

    public double adjustTransmission(double baseTransmission, Material material) {
        double bounded = Math.max(0.0D, Math.min(1.0D, baseTransmission));
        return Math.pow(bounded, materialCostMultiplier(material));
    }

    public int localizationUncertainty(double normalizedStrength) {
        double quality = Math.max(0.0D, Math.min(1.0D, normalizedStrength));
        return minimumUncertainty + (int) Math.ceil(
            (1.0D - quality) * (maximumUncertainty - minimumUncertainty)
        );
    }
}
