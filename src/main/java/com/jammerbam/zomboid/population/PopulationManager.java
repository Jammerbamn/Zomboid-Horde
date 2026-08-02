package com.jammerbam.zomboid.population;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.variation.ZombieVariationDefinitions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class PopulationManager {
    private static final int[][] POSITION_OFFSETS = new int[][]{
        {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
        {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
        {5, 1}, {-5, 1}, {1, 5}, {1, -5}
    };
    private static final Set<String> WARNED_MISSING_ENTITIES = new HashSet<>();

    private PopulationManager() {
    }

    public static boolean materializeChunk(WorldServer world, Chunk chunk) {
        if (!ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(world.provider.getDimension())) {
            return false;
        }

        ZombiePopulationData data = ZombiePopulationData.get(world);
        int regionSize = HordeCatalog.PLANNING_REGION_SIZE_CHUNKS;
        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        int regionX = Math.floorDiv(chunkX, regionSize);
        int regionZ = Math.floorDiv(chunkZ, regionSize);

        if (!data.isRegionInitialized(regionX, regionZ)) {
            SeededPopulationGenerator.PlanningResult generated =
                SeededPopulationGenerator.generatePlanningRegion(
                    world.getSeed(),
                    world.provider.getDimension(),
                    regionX,
                    regionZ,
                    HordeDefinitions.get(),
                    (blockX, blockZ) -> BiomeDescriptor.from(
                        world.getBiomeProvider().getBiome(new BlockPos(blockX, 0, blockZ))
                    )
                );
            List<HordeRecord> accepted = new ArrayList<>();
            List<HordeRecord> savedNeighbors = data.getHordesNearChunk(
                regionX * regionSize + regionSize / 2,
                regionZ * regionSize + regionSize / 2
            );
            int savedOverlapBlocks = 0;
            for (HordeRecord candidate : generated.getHordes()) {
                if (overlapsAny(candidate, savedNeighbors)) {
                    savedOverlapBlocks++;
                } else {
                    accepted.add(candidate);
                }
            }
            data.initializeRegion(regionX, regionZ, accepted);
            Zomboid.logger.info(
                "Planned population region {},{} in dimension {}: {} requested, {} placed, "
                    + "{} blocked by overlapping footprints.",
                regionX,
                regionZ,
                world.provider.getDimension(),
                generated.getRequestedCount(),
                accepted.size(),
                generated.getBlockedCount() + savedOverlapBlocks
            );
        }

        boolean retryRequired = false;
        for (HordeRecord horde : data.getHordesNearChunk(chunkX, chunkZ)) {
            List<ZombieSpawnPlan> slots = SeededPopulationGenerator.generateSlots(
                world.getSeed(), world.provider.getDimension(), horde
            );
            for (ZombieSpawnPlan plan : slots) {
                if (Math.floorDiv(plan.getX(), 16) != chunkX
                    || Math.floorDiv(plan.getZ(), 16) != chunkZ
                    || data.isDead(plan.getPopulationId())
                    || data.isMaterialized(plan.getPopulationId())) {
                    continue;
                }

                if (!materializeEntity(world, plan)) {
                    retryRequired = true;
                }
            }
        }
        return retryRequired;
    }

    private static boolean materializeEntity(WorldServer world, ZombieSpawnPlan plan) {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return false;
        }

        EntityLiving living = createLivingEntity(world, plan.getEntityId());
        if (living == null) {
            return false;
        }
        EntityLiving.SpawnPlacementType placement =
            EntitySpawnPlacementRegistry.getPlacementForEntity(living.getClass());
        BlockPos spawnPos = findSpawnPosition(world, plan, placement);
        if (spawnPos == null) {
            living.setDead();
            return false;
        }

        Random orientation = new Random(world.getSeed() ^ plan.getPopulationId().hashCode());
        living.setLocationAndAngles(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            orientation.nextFloat() * 360.0F,
            0.0F
        );

        if (!living.isNotColliding()) {
            living.setDead();
            return false;
        }
        boolean bypassVanillaSpawnCheck =
            ModConfig.allowDaylightZombies && living instanceof EntityZombie;
        if (!bypassVanillaSpawnCheck && !living.getCanSpawnHere()) {
            living.setDead();
            return false;
        }

        PopulationTags.apply(
            living, plan, ZombiePopulationData.get(world).getRegenerationEpoch()
        );
        living.enablePersistence();
        living.onInitialSpawn(
            world.getDifficultyForLocation(spawnPos),
            (IEntityLivingData) null
        );
        ZombieVariationDefinitions.apply(world, living, plan);
        BlockPos personalSpawn = living.getPosition();
        PopulationTags.setHome(living, personalSpawn);
        applyWanderRestriction(living, personalSpawn);

        if (!world.spawnEntity(living)) {
            living.setDead();
            return false;
        }

        ZombiePopulationData.get(world).markMaterialized(plan.getPopulationId());
        return true;
    }

    private static EntityLiving createLivingEntity(WorldServer world, String configuredEntityId) {
        Entity entity = null;
        try {
            entity = EntityList.createEntityByIDFromName(
                new ResourceLocation(configuredEntityId),
                world
            );
        } catch (RuntimeException ignored) {
            // Corrupt or manually edited saved IDs use the same fallback as removed mods.
        }
        if (entity instanceof EntityLiving) {
            return (EntityLiving) entity;
        }
        if (WARNED_MISSING_ENTITIES.add(configuredEntityId)) {
            Zomboid.logger.warn(
                "Managed entity {} is unavailable; materializing minecraft:zombie instead.",
                configuredEntityId
            );
        }
        Entity fallback = EntityList.createEntityByIDFromName(
            new ResourceLocation("minecraft:zombie"),
            world
        );
        return fallback instanceof EntityLiving ? (EntityLiving) fallback : null;
    }

    public static void applyWanderRestriction(EntityLiving living, BlockPos home) {
        if (!(living instanceof EntityCreature)) {
            return;
        }
        // EntityAITarget treats Minecraft's vanilla home radius as a hard target gate.
        // Personal wandering is enforced by EntityAIPersonalWander instead, so managed
        // mobs must remain detached from the vanilla restriction.
        ((EntityCreature) living).detachHome();
    }

    /** Moves the persistent personal anchor used exclusively by custom idle wandering. */
    public static void moveWanderAnchor(EntityLiving living, BlockPos anchor) {
        if (!PopulationTags.isManaged(living)) {
            return;
        }
        BlockPos immutableAnchor = anchor.toImmutable();
        PopulationTags.setHome(living, immutableAnchor);
        applyWanderRestriction(living, immutableAnchor);
    }

    private static BlockPos findSpawnPosition(
        WorldServer world,
        ZombieSpawnPlan plan,
        EntityLiving.SpawnPlacementType placement
    ) {
        int chunkX = Math.floorDiv(plan.getX(), 16);
        int chunkZ = Math.floorDiv(plan.getZ(), 16);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int[] offset : POSITION_OFFSETS) {
            int x = clamp(plan.getX() + offset[0], minX, maxX);
            int z = clamp(plan.getZ() + offset[1], minZ, maxZ);
            BlockPos top = world.getHeight(new BlockPos(x, 0, z));
            if (top.getY() <= 0 || top.getY() >= world.getHeight()) {
                continue;
            }
            if (placement == EntityLiving.SpawnPlacementType.ON_GROUND) {
                if (WorldEntitySpawner.canCreatureTypeSpawnAtLocation(placement, world, top)) {
                    return top;
                }
                continue;
            }
            int minimumY = Math.max(1, top.getY() - 32);
            for (int y = top.getY(); y >= minimumY; y--) {
                BlockPos candidate = new BlockPos(x, y, z);
                if (WorldEntitySpawner.canCreatureTypeSpawnAtLocation(
                    placement, world, candidate
                )) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean overlapsAny(
        HordeRecord candidate,
        List<HordeRecord> existingHordes
    ) {
        for (HordeRecord existing : existingHordes) {
            long dx = (long) candidate.getCenterX() - existing.getCenterX();
            long dz = (long) candidate.getCenterZ() - existing.getCenterZ();
            long minimum =
                (long) candidate.getSpreadRadius() + existing.getSpreadRadius();
            if (dx * dx + dz * dz < minimum * minimum) {
                return true;
            }
        }
        return false;
    }
}
