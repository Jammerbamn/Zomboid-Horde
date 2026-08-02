package com.jammerbam.zomboid.population;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SeededPopulationGenerator {
    public static final int GENERATOR_VERSION = 2;

    private static final long FREQUENCY_SALT = 0x5A17D3E4B92C6F01L;
    private static final long CENTER_SALT = 0x6D2B79F5A1C843E7L;
    private static final long TYPE_SALT = 0x37A0E91BC45D62F8L;
    private static final long SIZE_SALT = 0x4C8F12DA73B590E6L;
    private static final long PRIORITY_SALT = 0x71E4C9A2365BD80FL;
    private static final long SLOT_SALT = 0x28F19C74A6E35B0DL;

    private SeededPopulationGenerator() {
    }

    public static PlanningResult generatePlanningRegion(
        long worldSeed,
        int dimension,
        int planningRegionX,
        int planningRegionZ,
        HordeCatalog catalog,
        BiomeResolver biomeResolver
    ) {
        int regionSize = HordeCatalog.PLANNING_REGION_SIZE_CHUNKS;
        int minChunkX = planningRegionX * regionSize;
        int minChunkZ = planningRegionZ * regionSize;
        int maxChunkX = minChunkX + regionSize - 1;
        int maxChunkZ = minChunkZ + regionSize - 1;
        int overlapRangeChunks = (int) Math.ceil(catalog.getMaximumRadius() * 2.0D / 16.0D);

        List<RawCandidate> nearbyCandidates = new ArrayList<>();
        for (int chunkX = minChunkX - overlapRangeChunks;
             chunkX <= maxChunkX + overlapRangeChunks;
             chunkX++) {
            for (int chunkZ = minChunkZ - overlapRangeChunks;
                 chunkZ <= maxChunkZ + overlapRangeChunks;
                 chunkZ++) {
                RawCandidate candidate = generateCandidate(
                    worldSeed, dimension, chunkX, chunkZ, catalog, biomeResolver
                );
                if (candidate != null) {
                    nearbyCandidates.add(candidate);
                }
            }
        }

        List<HordeRecord> accepted = new ArrayList<>();
        int requested = 0;
        int blocked = 0;
        for (RawCandidate candidate : nearbyCandidates) {
            if (candidate.anchorChunkX < minChunkX || candidate.anchorChunkX > maxChunkX
                || candidate.anchorChunkZ < minChunkZ || candidate.anchorChunkZ > maxChunkZ) {
                continue;
            }
            requested++;
            if (isBlockedByHigherPriority(candidate, nearbyCandidates)) {
                blocked++;
                continue;
            }
            accepted.add(candidate.toRecord(planningRegionX, planningRegionZ));
        }
        accepted.sort((first, second) -> first.getGroupId().compareTo(second.getGroupId()));
        return new PlanningResult(accepted, requested, blocked);
    }

    public static List<ZombieSpawnPlan> generateSlots(
        long worldSeed,
        int dimension,
        HordeRecord horde
    ) {
        if (horde == null || horde.getPlannedSize() <= 0) {
            return Collections.emptyList();
        }

        boolean legacy = isLegacyDefinition(horde.getDefinitionId());
        int[] legacyRegion = legacy ? legacyRegionCoordinates(horde.getGroupId()) : null;
        Random random = new Random(legacy
            ? deriveLegacySeed(
                worldSeed, dimension, legacyRegion[0], legacyRegion[1], SLOT_SALT
            )
            : deriveSeed(
                worldSeed,
                dimension,
                horde.getAnchorChunkX(),
                horde.getAnchorChunkZ(),
                SLOT_SALT
            ));
        List<ZombieSpawnPlan> result = new ArrayList<>(horde.getPlannedSize());
        for (int slot = 0; slot < horde.getPlannedSize(); slot++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(random.nextDouble()) * horde.getSpreadRadius();
            int x = (int) Math.round(horde.getCenterX() + Math.cos(angle) * radius);
            int z = (int) Math.round(horde.getCenterZ() + Math.sin(angle) * radius);
            HordeMember member = legacy
                ? chooseLegacyMember(random, horde)
                : chooseMember(random, horde);
            result.add(new ZombieSpawnPlan(
                populationId(horde, slot),
                horde.getGroupId(),
                horde.getPlanningRegionX(),
                horde.getPlanningRegionZ(),
                slot,
                x,
                z,
                member.getEntityId(),
                chooseVariationId(random, member)
            ));
        }
        return result;
    }

    public static String groupId(int dimension, int anchorChunkX, int anchorChunkZ) {
        return "d" + dimension + ":c" + anchorChunkX + "," + anchorChunkZ + ":g0";
    }

    public static String populationId(HordeRecord horde, int slot) {
        if (isLegacyDefinition(horde.getDefinitionId())) {
            int groupMarker = horde.getGroupId().lastIndexOf(":g");
            String prefix = groupMarker < 0
                ? horde.getGroupId()
                : horde.getGroupId().substring(0, groupMarker);
            return prefix + ":z" + slot;
        }
        return horde.getGroupId() + ":z" + slot;
    }

    private static RawCandidate generateCandidate(
        long worldSeed,
        int dimension,
        int anchorChunkX,
        int anchorChunkZ,
        HordeCatalog catalog,
        BiomeResolver biomeResolver
    ) {
        Random frequency = new Random(deriveSeed(
            worldSeed, dimension, anchorChunkX, anchorChunkZ, FREQUENCY_SALT
        ));
        if (frequency.nextDouble() * 100.0D >= catalog.getFrequencyPercentPerChunk()) {
            return null;
        }

        Random centerRandom = new Random(deriveSeed(
            worldSeed, dimension, anchorChunkX, anchorChunkZ, CENTER_SALT
        ));
        int centerX = anchorChunkX * 16 + centerRandom.nextInt(16);
        int centerZ = anchorChunkZ * 16 + centerRandom.nextInt(16);
        BiomeDescriptor biome = biomeResolver.resolve(centerX, centerZ);
        HordeDefinition definition = catalog.select(
            new Random(deriveSeed(worldSeed, dimension, anchorChunkX, anchorChunkZ, TYPE_SALT)),
            biome
        );

        Random sizeRandom = new Random(deriveSeed(
            worldSeed, dimension, anchorChunkX, anchorChunkZ, SIZE_SALT
        ));
        int sizeRange = definition.getMaximumSize() - definition.getMinimumSize() + 1;
        int plannedSize = definition.getMinimumSize()
            + sizeRandom.nextInt(Math.max(1, sizeRange));
        long priority = deriveSeed(
            worldSeed, dimension, anchorChunkX, anchorChunkZ, PRIORITY_SALT
        );
        return new RawCandidate(
            anchorChunkX,
            anchorChunkZ,
            centerX,
            centerZ,
            plannedSize,
            definition,
            priority,
            dimension
        );
    }

    private static boolean isBlockedByHigherPriority(
        RawCandidate candidate,
        List<RawCandidate> nearbyCandidates
    ) {
        for (RawCandidate other : nearbyCandidates) {
            if (other == candidate || !overlaps(candidate, other)) {
                continue;
            }
            int priorityComparison = Long.compareUnsigned(other.priority, candidate.priority);
            if (priorityComparison > 0
                || (priorityComparison == 0
                && compareAnchor(other, candidate) > 0)) {
                return true;
            }
        }
        return false;
    }

    private static int compareAnchor(RawCandidate first, RawCandidate second) {
        int x = Integer.compare(first.anchorChunkX, second.anchorChunkX);
        return x != 0 ? x : Integer.compare(first.anchorChunkZ, second.anchorChunkZ);
    }

    private static boolean overlaps(RawCandidate first, RawCandidate second) {
        long dx = (long) first.centerX - second.centerX;
        long dz = (long) first.centerZ - second.centerZ;
        long minimumDistance =
            (long) first.definition.getRadius() + second.definition.getRadius();
        return dx * dx + dz * dz < minimumDistance * minimumDistance;
    }

    private static HordeMember chooseMember(Random random, HordeRecord horde) {
        long total = 0L;
        for (HordeMember member : horde.getMembers()) {
            total += member.getWeight();
        }
        if (total <= 0L) {
            return new HordeMember("minecraft:zombie", 1);
        }

        double roll = random.nextDouble() * total;
        for (HordeMember member : horde.getMembers()) {
            roll -= member.getWeight();
            if (roll < 0.0D) {
                return member;
            }
        }
        return horde.getMembers().get(0);
    }

    private static HordeMember chooseLegacyMember(Random random, HordeRecord horde) {
        int total = 0;
        for (HordeMember member : horde.getMembers()) {
            total += member.getWeight();
        }
        if (total <= 0) {
            return new HordeMember("minecraft:zombie", 1);
        }

        int roll = random.nextInt(total);
        for (HordeMember member : horde.getMembers()) {
            if (roll < member.getWeight()) {
                return member;
            }
            roll -= member.getWeight();
        }
        return horde.getMembers().get(0);
    }

    private static String chooseVariationId(Random random, HordeMember member) {
        long total = 0L;
        for (HordeVariation variation : member.getVariations()) {
            total += variation.getWeight();
        }
        if (total <= 0L) {
            return null;
        }

        double roll = random.nextDouble() * total;
        for (HordeVariation variation : member.getVariations()) {
            roll -= variation.getWeight();
            if (roll < 0.0D) {
                return variation.isStandard() ? null : variation.getVariationId();
            }
        }
        HordeVariation fallback = member.getVariations().get(0);
        return fallback.isStandard() ? null : fallback.getVariationId();
    }

    private static int[] legacyRegionCoordinates(String groupId) {
        int regionMarker = groupId.indexOf(":r");
        int groupMarker = groupId.lastIndexOf(":g");
        if (regionMarker < 0 || groupMarker <= regionMarker + 2) {
            return new int[]{0, 0};
        }
        String[] coordinates =
            groupId.substring(regionMarker + 2, groupMarker).split(",", 2);
        if (coordinates.length != 2) {
            return new int[]{0, 0};
        }
        try {
            return new int[]{
                Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1])
            };
        } catch (NumberFormatException ignored) {
            return new int[]{0, 0};
        }
    }

    private static boolean isLegacyDefinition(String definitionId) {
        return "zomboid:legacy".equals(definitionId)
            || "zomboidzombies:legacy".equals(definitionId);
    }

    private static long deriveSeed(
        long worldSeed,
        int dimension,
        int chunkX,
        int chunkZ,
        long salt
    ) {
        long value = worldSeed ^ salt;
        value ^= mix64(((long) dimension << 32) ^ dimension);
        value ^= mix64(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        value ^= (long) GENERATOR_VERSION * 0x9E3779B97F4A7C15L;
        return mix64(value);
    }

    private static long deriveLegacySeed(
        long worldSeed,
        int dimension,
        int regionX,
        int regionZ,
        long salt
    ) {
        long value = worldSeed ^ salt;
        value ^= mix64(((long) dimension << 32) ^ dimension);
        value ^= mix64(((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL));
        value ^= 0x9E3779B97F4A7C15L;
        return mix64(value);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static final class PlanningResult {
        private final List<HordeRecord> hordes;
        private final int requestedCount;
        private final int blockedCount;

        private PlanningResult(List<HordeRecord> hordes, int requestedCount, int blockedCount) {
            this.hordes = Collections.unmodifiableList(new ArrayList<>(hordes));
            this.requestedCount = requestedCount;
            this.blockedCount = blockedCount;
        }

        public List<HordeRecord> getHordes() {
            return hordes;
        }

        public int getRequestedCount() {
            return requestedCount;
        }

        public int getBlockedCount() {
            return blockedCount;
        }
    }

    private static final class RawCandidate {
        private final int anchorChunkX;
        private final int anchorChunkZ;
        private final int centerX;
        private final int centerZ;
        private final int plannedSize;
        private final HordeDefinition definition;
        private final long priority;
        private final int dimension;

        private RawCandidate(int anchorChunkX, int anchorChunkZ, int centerX, int centerZ,
                             int plannedSize, HordeDefinition definition, long priority,
                             int dimension) {
            this.anchorChunkX = anchorChunkX;
            this.anchorChunkZ = anchorChunkZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.plannedSize = plannedSize;
            this.definition = definition;
            this.priority = priority;
            this.dimension = dimension;
        }

        private HordeRecord toRecord(int planningRegionX, int planningRegionZ) {
            return new HordeRecord(
                planningRegionX,
                planningRegionZ,
                anchorChunkX,
                anchorChunkZ,
                groupId(dimension, anchorChunkX, anchorChunkZ),
                definition.getId(),
                centerX,
                centerZ,
                plannedSize,
                definition.getRadius(),
                definition.getMembers()
            );
        }
    }
}
