package com.jammerbam.zomboid.event;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.ai.ZombieAlertManager;
import com.jammerbam.zomboid.ai.ZombieBlockBreakingManager;
import com.jammerbam.zomboid.ai.WanderCoordinator;
import com.jammerbam.zomboid.ai.PursuitPathScheduler;
import com.jammerbam.zomboid.ai.navigation.NavigationManager;
import com.jammerbam.zomboid.audio.ZombieAudioController;
import com.jammerbam.zomboid.behavior.NoiseManager;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.population.PopulationManager;
import com.jammerbam.zomboid.population.PopulationTags;
import com.jammerbam.zomboid.population.HordeDefinitions;
import com.jammerbam.zomboid.population.ZombiePopulationData;
import com.jammerbam.zomboid.performance.AiPerformanceTelemetry;
import com.jammerbam.zomboid.performance.AutomaticRepathTelemetry;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class PopulationEvents {
    public static final PopulationEvents INSTANCE = new PopulationEvents();

    private final Map<WorldServer, LinkedHashMap<Long, Long>> pendingChunks = new WeakHashMap<>();

    private PopulationEvents() {
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer)
            || !ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(event.getWorld().provider.getDimension())) {
            return;
        }

        WorldServer world = (WorldServer) event.getWorld();
        Chunk chunk = event.getChunk();
        long startedAt = RuntimePerformanceTelemetry.begin();
        try {
            queueChunk(world, chunk.x, chunk.z, world.getTotalWorldTime() + 1L);
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                world, PerformancePhase.CHUNK_CALLBACK, startedAt
            );
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            Chunk chunk = event.getChunk();
            long startedAt = RuntimePerformanceTelemetry.begin();
            try {
                NavigationManager.invalidateChunk(event.getWorld(), chunk.x, chunk.z);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    event.getWorld(), PerformancePhase.CHUNK_CALLBACK, startedAt
                );
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
            return;
        }

        WorldServer world = (WorldServer) event.world;
        LinkedHashMap<Long, Long> queue = pendingChunks.get(world);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        long now = world.getTotalWorldTime();
        int processed = 0;
        Iterator<Map.Entry<Long, Long>> iterator = queue.entrySet().iterator();
        while (iterator.hasNext() && processed < ModConfig.materializedChunksPerTick) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue;
            }

            int chunkX = chunkX(entry.getKey());
            int chunkZ = chunkZ(entry.getKey());
            Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
            if (chunk == null) {
                iterator.remove();
                continue;
            }

            long materializationStartedAt = RuntimePerformanceTelemetry.begin();
            boolean retry;
            try {
                retry = PopulationManager.materializeChunk(world, chunk);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    world,
                    PerformancePhase.POPULATION_MATERIALIZATION,
                    materializationStartedAt
                );
            }
            processed++;
            if (retry) {
                entry.setValue(now + ModConfig.materializationRetryTicks);
            } else {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!(event.getWorld() instanceof WorldServer)
            || !(event.getEntity() instanceof EntityLiving)
            || !PopulationTags.isManaged(event.getEntity())) {
            return;
        }

        EntityLiving living = (EntityLiving) event.getEntity();
        String populationId = PopulationTags.getPopulationId(living);
        if (populationId.isEmpty()) {
            return;
        }

        ZombiePopulationData data = ZombiePopulationData.get((WorldServer) event.getWorld());
        if (PopulationTags.getRegenerationEpoch(living) != data.getRegenerationEpoch()) {
            event.setCanceled(true);
            living.setDead();
            return;
        }
        if (data.isDead(populationId)) {
            event.setCanceled(true);
            living.setDead();
            return;
        }

        BlockPos home;
        if (PopulationTags.hasHome(living)) {
            home = PopulationTags.getHome(living);
        } else {
            home = new BlockPos(living);
            PopulationTags.setHome(living, home);
        }
        PopulationManager.applyWanderRestriction(living, home);
        living.enablePersistence();
        data.markMaterialized(populationId);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving().world instanceof WorldServer)
            || !PopulationTags.isManaged(event.getEntityLiving())) {
            return;
        }

        String populationId = PopulationTags.getPopulationId(event.getEntityLiving());
        if (!populationId.isEmpty()) {
            ZombiePopulationData.get((WorldServer) event.getEntityLiving().world)
                .markDead(populationId);
        }
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.enableSeededPopulation
            || !ModConfig.replaceNaturalZombieSpawns
            || !(event.getEntityLiving() instanceof EntityZombie)
            || PopulationTags.isManaged(event.getEntityLiving())
            || event.isSpawner()
            || !ModConfig.isPopulationDimension(event.getWorld().provider.getDimension())) {
            return;
        }

        event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void onAllowDespawn(LivingSpawnEvent.AllowDespawn event) {
        if (PopulationTags.isManaged(event.getEntityLiving())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer)
            || !ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(event.getWorld().provider.getDimension())) {
            return;
        }

        WorldServer world = (WorldServer) event.getWorld();
        long startedAt = RuntimePerformanceTelemetry.begin();
        try {
            ZombiePopulationData data = ZombiePopulationData.get(world);
            Zomboid.logger.info(
                "Population ledger ready for dimension {}: {} initialized regions, {} hordes, "
                    + "{} materialized, {} dead. Planning uses 32x32-chunk regions and {}% "
                    + "frequency per chunk across {} definitions.",
                world.provider.getDimension(),
                data.getInitializedRegionCount(),
                data.getHordeCount(),
                data.getMaterializedCount(),
                data.getDeadCount(),
                HordeDefinitions.get().getFrequencyPercentPerChunk(),
                HordeDefinitions.get().getDefinitions().size()
            );
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                world, PerformancePhase.WORLD_LOAD_CALLBACK, startedAt
            );
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        clearRuntimeState(event.getWorld());
        if (event.getWorld() instanceof WorldServer) {
            pendingChunks.remove((WorldServer) event.getWorld());
        }
    }

    public int resetAndQueueLoadedChunks(WorldServer world) {
        pendingChunks.remove(world);
        clearRuntimeState(world);
        if (!ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(world.provider.getDimension())) {
            return 0;
        }

        int queued = 0;
        long processAt = world.getTotalWorldTime() + 1L;
        for (Chunk chunk : new ArrayList<>(world.getChunkProvider().getLoadedChunks())) {
            queueChunk(world, chunk.x, chunk.z, processAt);
            queued++;
        }
        return queued;
    }

    private static void clearRuntimeState(net.minecraft.world.World world) {
        NoiseManager.clear(world);
        ZombieAlertManager.clear(world);
        ZombieBlockBreakingManager.clear(world);
        WanderCoordinator.clear(world);
        PursuitPathScheduler.clear(world);
        NavigationManager.clear(world);
        AiPerformanceTelemetry.clear(world);
        VanillaPathRequestTelemetry.clear(world);
        AutomaticRepathTelemetry.clear(world);
        ZombieAudioController.clear(world);
        RuntimePerformanceTelemetry.clear(world);
    }

    private void queueChunk(WorldServer world, int chunkX, int chunkZ, long processAt) {
        LinkedHashMap<Long, Long> queue = pendingChunks.computeIfAbsent(
            world, ignored -> new LinkedHashMap<>()
        );
        long key = chunkKey(chunkX, chunkZ);
        Long current = queue.get(key);
        if (current == null || processAt < current) {
            queue.put(key, processAt);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }
}
