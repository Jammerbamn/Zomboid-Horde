package com.jammerbam.zomboid.variation;

import net.minecraft.inventory.EntityEquipmentSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ZombieVariationDefinition {
    private final String id;
    private final Set<String> entityIds;
    private final Double maximumHealth;
    private final Double movementSpeed;
    private final Double attackDamage;
    private final Double knockbackResistance;
    private final Double swimSpeed;
    private final Integer blockBreakingLevel;
    private final Map<EntityEquipmentSlot, EquipmentPool> equipment;
    private final List<OnHitEffect> onHitEffects;

    public ZombieVariationDefinition(
        String id,
        Set<String> entityIds,
        Double maximumHealth,
        Double movementSpeed,
        Double attackDamage,
        Double knockbackResistance,
        Double swimSpeed,
        Integer blockBreakingLevel,
        Map<EntityEquipmentSlot, EquipmentPool> equipment,
        List<OnHitEffect> onHitEffects
    ) {
        this.id = id;
        this.entityIds = Collections.unmodifiableSet(new LinkedHashSet<>(entityIds));
        this.maximumHealth = maximumHealth;
        this.movementSpeed = movementSpeed;
        this.attackDamage = attackDamage;
        this.knockbackResistance = knockbackResistance;
        this.swimSpeed = swimSpeed;
        this.blockBreakingLevel = blockBreakingLevel;
        EnumMap<EntityEquipmentSlot, EquipmentPool> copied =
            new EnumMap<>(EntityEquipmentSlot.class);
        copied.putAll(equipment);
        this.equipment = Collections.unmodifiableMap(copied);
        this.onHitEffects = Collections.unmodifiableList(new ArrayList<>(onHitEffects));
    }

    public String getId() {
        return id;
    }

    public Set<String> getEntityIds() {
        return entityIds;
    }

    public boolean appliesTo(String entityId) {
        return entityIds.contains(entityId);
    }

    public Double getMaximumHealth() {
        return maximumHealth;
    }

    public Double getMovementSpeed() {
        return movementSpeed;
    }

    public Double getAttackDamage() {
        return attackDamage;
    }

    public Double getKnockbackResistance() {
        return knockbackResistance;
    }

    public Double getSwimSpeed() {
        return swimSpeed;
    }

    public Integer getBlockBreakingLevel() {
        return blockBreakingLevel;
    }

    public Map<EntityEquipmentSlot, EquipmentPool> getEquipment() {
        return equipment;
    }

    public List<OnHitEffect> getOnHitEffects() {
        return onHitEffects;
    }

    public static final class OnHitEffect {
        private final String potionId;
        private final double durationSeconds;
        private final int amplifier;
        private final double chancePercent;

        public OnHitEffect(String potionId, double durationSeconds, int amplifier,
                           double chancePercent) {
            this.potionId = potionId;
            this.durationSeconds = durationSeconds;
            this.amplifier = amplifier;
            this.chancePercent = chancePercent;
        }

        public String getPotionId() {
            return potionId;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public int getDurationTicks() {
            return Math.max(1, (int) Math.round(durationSeconds * 20.0D));
        }

        public int getAmplifier() {
            return amplifier;
        }

        public double getChancePercent() {
            return chancePercent;
        }

    }

    public static final class EquipmentPool {
        private final double chancePercent;
        private final List<EquipmentChoice> choices;

        public EquipmentPool(double chancePercent, List<EquipmentChoice> choices) {
            this.chancePercent = chancePercent;
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        }

        public double getChancePercent() {
            return chancePercent;
        }

        public List<EquipmentChoice> getChoices() {
            return choices;
        }
    }

    public static final class EquipmentChoice {
        private final String itemId;
        private final int weight;
        private final int count;
        private final int metadata;
        private final double dropChancePercent;
        private final String nbtJson;

        public EquipmentChoice(String itemId, int weight, int count, int metadata,
                               double dropChancePercent, String nbtJson) {
            this.itemId = itemId;
            this.weight = Math.max(0, weight);
            this.count = count;
            this.metadata = metadata;
            this.dropChancePercent = dropChancePercent;
            this.nbtJson = nbtJson;
        }

        public String getItemId() {
            return itemId;
        }

        public int getWeight() {
            return weight;
        }

        public int getCount() {
            return count;
        }

        public int getMetadata() {
            return metadata;
        }

        public double getDropChancePercent() {
            return dropChancePercent;
        }

        public String getNbtJson() {
            return nbtJson;
        }
    }
}
