package com.jammerbam.zomboid.behavior;

import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.sound.AcousticFieldSolver;
import com.jammerbam.zomboid.sound.AcousticProfile;
import com.jammerbam.zomboid.sound.BlockAcousticCosts;
import com.jammerbam.zomboid.sound.SoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative sound stimuli and transient block-aware wavefronts. */
public final class NoiseManager {
    private static final Map<World, WorldSoundState> WORLD_STATES = new WeakHashMap<>();
    private static final Set<UUID> DEBUG_VIEWERS = new HashSet<>();
    private static long nextEventId = 1L;

    private NoiseManager() {
    }

    public static void recordNoise(World world, BlockPos position, double strength,
                                   int lifetimeTicks) {
        recordNoise(world, position, strength, lifetimeTicks, SoundType.DEBUG, null);
    }

    public static void recordNoise(World world, BlockPos position, double strength,
                                   int lifetimeTicks, SoundType type,
                                   @Nullable UUID sourceId) {
        if (world.isRemote || strength <= 0.0D || lifetimeTicks <= 0) {
            return;
        }
        WorldSoundState state = WORLD_STATES.computeIfAbsent(
            world, ignored -> new WorldSoundState()
        );
        long now = world.getTotalWorldTime();
        state.prune(now);
        if (sourceId != null) {
            Iterator<SoundEvent> iterator = state.events.iterator();
            while (iterator.hasNext()) {
                SoundEvent existing = iterator.next();
                if (sourceId.equals(existing.sourceId) && type == existing.type) {
                    if (existing.createdAt == now) {
                        return;
                    }
                    if (!ModConfig.realisticSimulation) {
                        iterator.remove();
                    }
                }
            }
        }
        while (state.events.size() >= ModConfig.soundMaximumActiveEvents) {
            state.events.removeFirst();
        }
        SoundEvent event = new SoundEvent(
            nextEventId++, position, strength, now, lifetimeTicks, type, sourceId,
            ModConfig.realisticSimulation
                ? new AcousticFieldSolver(
                    position, strength, ModConfig.soundMaximumNodesPerEvent
                )
                : null
        );
        state.events.add(event);
        state.latest = event;
        state.record(event);
    }

    public static void tick(WorldServer world) {
        WorldSoundState state = WORLD_STATES.get(world);
        if (state == null) {
            return;
        }
        long now = world.getTotalWorldTime();
        state.prune(now);
        for (SoundEvent event : state.events) {
            event.currentWave.clear();
        }
        int remainingBudget = ModConfig.soundPropagationNodesPerTick;
        int unfinished = 0;
        for (SoundEvent event : state.events) {
            if (event.field != null && !event.field.isComplete()
                && now >= event.nextWaveAt) {
                unfinished++;
            }
        }
        for (SoundEvent event : state.events) {
            if (remainingBudget <= 0 || event.field == null || event.field.isComplete()
                || now < event.nextWaveAt) {
                continue;
            }
            int allocation = Math.max(1, remainingBudget / Math.max(1, unfinished));
            int used = event.field.step(
                allocation,
                position -> BlockAcousticCosts.cost(
                    world, position, event.acousticProfile
                )
            );
            event.currentWave.putAll(event.field.drainNewlyReached());
            event.nextWaveAt = now + ModConfig.soundWaveIntervalTicks;
            if (event.field.isComplete()) {
                event.completedAt = now;
            }
            remainingBudget -= used;
            unfinished--;
        }

        deliverWaveArrivals(world, state, now);

        if (!DEBUG_VIEWERS.isEmpty()) {
            for (EntityPlayerMP player : world.getMinecraftServer()
                .getPlayerList().getPlayers()) {
                if (player.world == world && DEBUG_VIEWERS.contains(player.getUniqueID())) {
                    visualize(player);
                }
            }
        }
    }

    @Nullable
    public static NoiseTarget findBestNoise(EntityZombie zombie) {
        WorldSoundState state = WORLD_STATES.get(zombie.world);
        if (state == null) {
            return null;
        }
        state.prune(zombie.world.getTotalWorldTime());

        SoundEvent best = null;
        double bestStrength = 0.0D;
        for (SoundEvent event : state.events) {
            if (event.field != null || !detects(zombie, event)) {
                continue;
            }
            double perceived = event.perceivedStrength(zombie);
            if (perceived > bestStrength) {
                best = event;
                bestStrength = perceived;
            }
        }
        return best == null ? null : new NoiseTarget(
            best.id, estimateSource(best, zombie, bestStrength),
            best.expiresAt, bestStrength, best.type
        );
    }

    public static void setDebug(EntityPlayerMP player, boolean enabled) {
        if (enabled) {
            DEBUG_VIEWERS.add(player.getUniqueID());
        } else {
            DEBUG_VIEWERS.remove(player.getUniqueID());
        }
    }

    public static boolean isDebugEnabled(EntityPlayerMP player) {
        return DEBUG_VIEWERS.contains(player.getUniqueID());
    }

    public static String status(World world) {
        WorldSoundState state = WORLD_STATES.get(world);
        if (state == null) {
            return "Sound simulation: realisticSimulation=" + ModConfig.realisticSimulation
                + ", activeEvents=0, recordedTotal=0";
        }
        state.prune(world.getTotalWorldTime());
        if (state.events.isEmpty()) {
            return "Sound simulation: realisticSimulation=" + ModConfig.realisticSimulation
                + ", activeEvents=0" + state.recordedSummary();
        }
        SoundEvent latest = state.latest;
        String progress = latest.field == null
            ? "occlusionCells=" + latest.simpleOcclusions.size()
            : "waveCells=" + latest.currentWave.size()
                + ", discovered=" + latest.field.getStrengths().size()
                + ", processed=" + latest.field.getProcessedNodes()
                + ", frontier=" + latest.field.getFrontierSize()
                + ", complete=" + latest.field.isComplete();
        return "Sound simulation: realisticSimulation=" + ModConfig.realisticSimulation
            + ", activeEvents=" + state.events.size()
            + state.recordedSummary()
            + ", latest=#" + latest.id + " " + latest.type
            + "/" + latest.acousticProfile
            + " at " + format(latest.position) + ", " + progress;
    }

    public static boolean visualize(EntityPlayerMP player) {
        WorldSoundState state = WORLD_STATES.get(player.world);
        if (state == null || state.latest == null) {
            return false;
        }
        SoundEvent event = state.latest;
        if (event.field == null) {
            visualizeSimple(player, event);
            return true;
        }

        int totalCells = 0;
        for (SoundEvent active : state.events) {
            totalCells += active.currentWave.size();
        }
        if (totalCells == 0) {
            return false;
        }
        int budget = ModConfig.soundDebugParticleBudget;
        int stride = Math.max(1, totalCells / budget);
        int index = 0;
        int sent = 0;
        for (SoundEvent active : state.events) {
            for (Map.Entry<Long, Double> entry : active.currentWave.entrySet()) {
                if (index++ % stride != 0) {
                    continue;
                }
                BlockPos position = BlockPos.fromLong(entry.getKey());
                if (player.getDistanceSqToCenter(position) > 96.0D * 96.0D) {
                    continue;
                }
                spawnPoint(player, position, entry.getValue() / active.strength);
                if (++sent >= budget) {
                    return true;
                }
            }
        }
        return true;
    }

    private static void deliverWaveArrivals(WorldServer world, WorldSoundState state,
                                            long now) {
        if (state.events.isEmpty()) {
            return;
        }
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityZombie) || !entity.isEntityAlive()) {
                continue;
            }
            EntityZombie zombie = (EntityZombie) entity;
            BlockPos feet = zombie.getPosition();
            BlockPos head = new BlockPos(
                zombie.posX, zombie.posY + zombie.getEyeHeight(), zombie.posZ
            );
            SoundEvent best = null;
            double bestStrength = 0.0D;
            for (SoundEvent event : state.events) {
                if (event.field == null || event.currentWave.isEmpty()
                    || !detects(zombie, event)) {
                    continue;
                }
                double perceived = Math.max(
                    valueAt(event.currentWave, feet), valueAt(event.currentWave, head)
                );
                if (perceived > bestStrength) {
                    best = event;
                    bestStrength = perceived;
                }
            }
            if (best != null) {
                ZombieBrainManager.hearSound(
                    zombie,
                    best.id,
                    estimateSource(best, zombie, bestStrength),
                    now + best.memoryDurationTicks,
                    bestStrength
                );
            }
        }
    }

    private static boolean detects(EntityZombie zombie, SoundEvent event) {
        return SoundDetectionRoll.accepts(
            zombie.getUniqueID(), event.id, ModConfig.soundDetectionChancePercent
        );
    }

    private static double valueAt(Map<Long, Double> wave, BlockPos position) {
        Double value = wave.get(position.toLong());
        return value == null ? 0.0D : value;
    }

    private static BlockPos estimateSource(SoundEvent event, EntityZombie zombie,
                                           double perceivedStrength) {
        double normalized = Math.max(0.0D, Math.min(1.0D,
            perceivedStrength / event.strength));
        int uncertainty = event.acousticProfile.localizationUncertainty(normalized);
        if (uncertainty <= 0) {
            return event.position;
        }
        UUID id = zombie.getUniqueID();
        long seed = event.id * 0x9E3779B97F4A7C15L
            ^ id.getMostSignificantBits()
            ^ Long.rotateLeft(id.getLeastSignificantBits(), 23);
        Random random = new Random(seed);
        return event.position.add(
            random.nextInt(uncertainty * 2 + 1) - uncertainty,
            0,
            random.nextInt(uncertainty * 2 + 1) - uncertainty
        );
    }

    public static void clear(World world) {
        WORLD_STATES.remove(world);
    }

    public static void clearAll() {
        WORLD_STATES.clear();
    }

    private static void spawnPoint(EntityPlayerMP player, BlockPos position,
                                   double normalizedStrength) {
        spawnPoint(
            player,
            new Vec3d(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
            ),
            normalizedStrength
        );
    }

    private static void spawnPoint(EntityPlayerMP player, Vec3d position,
                                   double normalizedStrength) {
        EnumParticleTypes particle = normalizedStrength >= 0.66D
            ? EnumParticleTypes.VILLAGER_HAPPY
            : normalizedStrength >= 0.33D
                ? EnumParticleTypes.SPELL_MOB_AMBIENT
                : EnumParticleTypes.REDSTONE;
        ((WorldServer) player.world).spawnParticle(
            player, particle, true,
            position.x,
            position.y,
            position.z,
            1, 0.0D, 0.0D, 0.0D, 0.0D
        );
    }

    private static void visualizeSimple(EntityPlayerMP player, SoundEvent event) {
        spawnPoint(player, event.position, 1.0D);
        int budget = Math.max(1, ModConfig.soundDebugParticleBudget - 1);
        int sent = 0;
        for (SimpleOcclusionSample sample : event.simpleOcclusions.values()) {
            if (sent >= budget) {
                break;
            }
            Vec3d start = new Vec3d(
                sample.listener.getX() + 0.5D,
                sample.listener.getY() + 0.5D,
                sample.listener.getZ() + 0.5D
            );
            Vec3d end = new Vec3d(
                event.position.getX() + 0.5D,
                event.position.getY() + 0.5D,
                event.position.getZ() + 0.5D
            );
            for (int point = 0; point <= 6 && sent < budget; point++) {
                double fraction = point / 6.0D;
                spawnPoint(player, new Vec3d(
                    start.x + (end.x - start.x) * fraction,
                    start.y + (end.y - start.y) * fraction,
                    start.z + (end.z - start.z) * fraction
                ), sample.multiplier);
                sent++;
            }
            if (sample.obstruction != null && sent < budget) {
                spawnPoint(player, sample.obstruction, 0.0D);
                sent++;
            }
        }
    }

    private static String format(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static final class WorldSoundState {
        private final LinkedList<SoundEvent> events = new LinkedList<>();
        private final Map<SoundType, Long> recordedByType =
            new EnumMap<>(SoundType.class);
        private SoundEvent latest;
        private long recordedTotal;
        private SoundEvent lastRecorded;

        private void record(SoundEvent event) {
            recordedTotal++;
            Long count = recordedByType.get(event.type);
            recordedByType.put(event.type, count == null ? 1L : count + 1L);
            lastRecorded = event;
        }

        private String recordedSummary() {
            String last = lastRecorded == null
                ? "none"
                : "#" + lastRecorded.id + " " + lastRecorded.type
                    + "@" + lastRecorded.createdAt;
            return ", recordedTotal=" + recordedTotal
                + ", recordedByType=" + recordedByType
                + ", lastRecorded=" + last;
        }

        private void prune(long now) {
            Iterator<SoundEvent> iterator = events.iterator();
            while (iterator.hasNext()) {
                SoundEvent event = iterator.next();
                if (event.isExpired(now)) {
                    iterator.remove();
                }
            }
            if (latest != null && latest.isExpired(now)) {
                latest = events.isEmpty() ? null : events.getLast();
            }
        }
    }

    private static final class SoundEvent {
        private final long id;
        private final BlockPos position;
        private final double strength;
        private final long createdAt;
        private final long expiresAt;
        private final int memoryDurationTicks;
        private final SoundType type;
        private final AcousticProfile acousticProfile;
        private final UUID sourceId;
        private final AcousticFieldSolver field;
        private final Map<Long, SimpleOcclusionSample> simpleOcclusions = new HashMap<>();
        private final Map<Long, Double> currentWave = new HashMap<>();
        private long nextWaveAt;
        private long completedAt = -1L;

        private SoundEvent(long id, BlockPos position, double strength, long createdAt,
                           int memoryDurationTicks,
                           SoundType type, UUID sourceId,
                           @Nullable AcousticFieldSolver field) {
            this.id = id;
            this.position = position.toImmutable();
            this.strength = strength;
            this.createdAt = createdAt;
            this.memoryDurationTicks = memoryDurationTicks;
            this.expiresAt = createdAt + memoryDurationTicks;
            this.type = type;
            this.acousticProfile = type.getAcousticProfile();
            this.sourceId = sourceId;
            this.field = field;
            this.nextWaveAt = createdAt;
        }

        private boolean isExpired(long now) {
            if (field == null) {
                return expiresAt < now;
            }
            return completedAt >= 0L && completedAt < now;
        }

        private double perceivedStrength(EntityZombie zombie) {
            if (field == null) {
                double distance = Math.sqrt(zombie.getDistanceSqToCenter(position));
                if (distance >= strength) {
                    return 0.0D;
                }
                int cellSize = ModConfig.simpleSoundOcclusionCellSize;
                BlockPos listener = new BlockPos(
                    zombie.posX,
                    zombie.posY + zombie.getEyeHeight(),
                    zombie.posZ
                );
                BlockPos cell = new BlockPos(
                    Math.floorDiv(listener.getX(), cellSize),
                    Math.floorDiv(listener.getY(), cellSize),
                    Math.floorDiv(listener.getZ(), cellSize)
                );
                long cellKey = cell.toLong();
                SimpleOcclusionSample sample = simpleOcclusions.get(cellKey);
                if (sample == null) {
                    sample = traceSimpleOcclusion(zombie.world, listener);
                    simpleOcclusions.put(cellKey, sample);
                }
                return Math.max(0.0D, strength * sample.multiplier - distance);
            }
            return 0.0D;
        }

        private SimpleOcclusionSample traceSimpleOcclusion(World world, BlockPos listener) {
            Vec3d start = new Vec3d(
                listener.getX() + 0.5D,
                listener.getY() + 0.5D,
                listener.getZ() + 0.5D
            );
            Vec3d end = new Vec3d(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
            );
            RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
            BlockPos obstruction = hit != null
                && hit.typeOfHit == RayTraceResult.Type.BLOCK
                && !position.equals(hit.getBlockPos())
                    ? hit.getBlockPos()
                    : null;
            double multiplier = obstruction == null
                ? 1.0D
                : BlockAcousticCosts.simpleOcclusionMultiplier(
                    world.getBlockState(obstruction).getMaterial(), acousticProfile
                );
            return new SimpleOcclusionSample(listener.toImmutable(), obstruction, multiplier);
        }
    }

    private static final class SimpleOcclusionSample {
        private final BlockPos listener;
        private final BlockPos obstruction;
        private final double multiplier;

        private SimpleOcclusionSample(BlockPos listener, @Nullable BlockPos obstruction,
                                      double multiplier) {
            this.listener = listener;
            this.obstruction = obstruction == null ? null : obstruction.toImmutable();
            this.multiplier = multiplier;
        }
    }

    public static final class NoiseTarget {
        public final long eventId;
        public final BlockPos position;
        public final long expiresAt;
        public final double perceivedStrength;
        public final SoundType type;

        private NoiseTarget(long eventId, BlockPos position, long expiresAt,
                            double perceivedStrength, SoundType type) {
            this.eventId = eventId;
            this.position = position;
            this.expiresAt = expiresAt;
            this.perceivedStrength = perceivedStrength;
            this.type = type;
        }
    }
}
