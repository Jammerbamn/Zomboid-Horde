package com.ethan.zomboidzombies.population;

import com.ethan.zomboidzombies.config.ModConfig;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.List;
import java.util.Random;

public final class PopulationManager {
    private static final int[][] POSITION_OFFSETS = new int[][]{
        {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
        {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
        {5, 1}, {-5, 1}, {1, 5}, {1, -5}
    };

    private PopulationManager() {
    }

    public static boolean materializeChunk(WorldServer world, Chunk chunk) {
        if (!ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(world.provider.getDimension())) {
            return false;
        }

        ZombiePopulationData data = ZombiePopulationData.get(world);
        int regionSizeChunks = data.getRegionSizeChunks();
        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        int regionX = Math.floorDiv(chunkX, regionSizeChunks);
        int regionZ = Math.floorDiv(chunkZ, regionSizeChunks);

        if (!data.isRegionInitialized(regionX, regionZ)) {
            HordeRecord generated = SeededPopulationGenerator.generateHorde(
                world.getSeed(),
                world.provider.getDimension(),
                regionX,
                regionZ,
                regionSizeChunks
            );
            data.initializeRegion(regionX, regionZ, generated);
        }

        HordeRecord horde = data.getHorde(regionX, regionZ);
        if (horde == null) {
            return false;
        }

        List<ZombieSpawnPlan> slots = SeededPopulationGenerator.generateSlots(
            world.getSeed(), world.provider.getDimension(), regionSizeChunks, horde
        );
        boolean retryRequired = false;

        for (ZombieSpawnPlan plan : slots) {
            if (Math.floorDiv(plan.getX(), 16) != chunkX
                || Math.floorDiv(plan.getZ(), 16) != chunkZ
                || data.isDead(plan.getPopulationId())
                || data.isMaterialized(plan.getPopulationId())) {
                continue;
            }

            if (!materializeZombie(world, plan)) {
                retryRequired = true;
            }
        }

        return retryRequired;
    }

    private static boolean materializeZombie(WorldServer world, ZombieSpawnPlan plan) {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return false;
        }

        BlockPos spawnPos = findSpawnPosition(world, plan);
        if (spawnPos == null) {
            return false;
        }

        EntityZombie zombie = plan.getKind().create(world);
        Random orientation = new Random(world.getSeed() ^ plan.getPopulationId().hashCode());
        zombie.setLocationAndAngles(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            orientation.nextFloat() * 360.0F,
            0.0F
        );

        if (!zombie.isNotColliding()) {
            return false;
        }
        if (!ModConfig.allowDaylightZombies && !zombie.getCanSpawnHere()) {
            return false;
        }

        PopulationTags.apply(zombie, plan);
        zombie.enablePersistence();
        zombie.onInitialSpawn(
            world.getDifficultyForLocation(spawnPos),
            (IEntityLivingData) null
        );

        if (!world.spawnEntity(zombie)) {
            return false;
        }

        ZombiePopulationData.get(world).markMaterialized(plan.getPopulationId());
        return true;
    }

    private static BlockPos findSpawnPosition(WorldServer world, ZombieSpawnPlan plan) {
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
            if (WorldEntitySpawner.canCreatureTypeSpawnAtLocation(
                EntityLiving.SpawnPlacementType.ON_GROUND, world, top
            )) {
                return top;
            }
        }

        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
