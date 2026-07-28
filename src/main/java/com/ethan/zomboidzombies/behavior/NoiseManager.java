package com.ethan.zomboidzombies.behavior;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class NoiseManager {
    private static final Map<World, List<Noise>> NOISES = new WeakHashMap<>();

    private NoiseManager() {
    }

    public static void recordNoise(World world, BlockPos position, double radius, int lifetimeTicks) {
        if (world.isRemote || radius <= 0.0D) {
            return;
        }

        List<Noise> worldNoises = NOISES.computeIfAbsent(world, ignored -> new ArrayList<>());
        long now = world.getTotalWorldTime();
        prune(worldNoises, now);
        worldNoises.add(new Noise(position.toImmutable(), radius, now + lifetimeTicks));
    }

    @Nullable
    public static NoiseTarget findBestNoise(EntityZombie zombie) {
        List<Noise> worldNoises = NOISES.get(zombie.world);
        if (worldNoises == null) {
            return null;
        }

        long now = zombie.world.getTotalWorldTime();
        prune(worldNoises, now);

        Noise best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Noise noise : worldNoises) {
            double distanceSq = distanceSq(zombie, noise.position);
            if (distanceSq > noise.radius * noise.radius) {
                continue;
            }

            double score = noise.radius - Math.sqrt(distanceSq);
            if (score > bestScore) {
                bestScore = score;
                best = noise;
            }
        }

        return best == null ? null : new NoiseTarget(best.position, best.expiresAt);
    }

    private static void prune(List<Noise> noises, long now) {
        Iterator<Noise> iterator = noises.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt < now) {
                iterator.remove();
            }
        }
    }

    private static double distanceSq(EntityZombie zombie, BlockPos position) {
        double x = position.getX() + 0.5D - zombie.posX;
        double y = position.getY() + 0.5D - zombie.posY;
        double z = position.getZ() + 0.5D - zombie.posZ;
        return x * x + y * y + z * z;
    }

    private static final class Noise {
        private final BlockPos position;
        private final double radius;
        private final long expiresAt;

        private Noise(BlockPos position, double radius, long expiresAt) {
            this.position = position;
            this.radius = radius;
            this.expiresAt = expiresAt;
        }
    }

    public static final class NoiseTarget {
        public final BlockPos position;
        public final long expiresAt;

        private NoiseTarget(BlockPos position, long expiresAt) {
            this.position = position;
            this.expiresAt = expiresAt;
        }
    }
}
