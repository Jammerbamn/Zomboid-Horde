package com.ethan.zomboidzombies.event;

import com.ethan.zomboidzombies.config.ModConfig;
import com.ethan.zomboidzombies.population.PopulationManager;
import com.ethan.zomboidzombies.population.PopulationTags;
import com.ethan.zomboidzombies.population.ZombiePopulationData;
import net.minecraft.entity.monster.EntityZombie;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class PopulationEvents {
    private final Map<WorldServer, LinkedHashMap<Long, Long>> pendingChunks = new WeakHashMap<>();

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer)
            || !ModConfig.enableSeededPopulation
            || !ModConfig.isPopulationDimension(event.getWorld().provider.getDimension())) {
            return;
        }

        WorldServer world = (WorldServer) event.getWorld();
        Chunk chunk = event.getChunk();
        queueChunk(world, chunk.x, chunk.z, world.getTotalWorldTime() + 1L);
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

            boolean retry = PopulationManager.materializeChunk(world, chunk);
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
            || !(event.getEntity() instanceof EntityZombie)
            || !PopulationTags.isManaged(event.getEntity())) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntity();
        String populationId = PopulationTags.getPopulationId(zombie);
        if (populationId.isEmpty()) {
            return;
        }

        ZombiePopulationData data = ZombiePopulationData.get((WorldServer) event.getWorld());
        if (data.isDead(populationId)) {
            event.setCanceled(true);
            zombie.setDead();
            return;
        }

        zombie.enablePersistence();
        data.markMaterialized(populationId);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving().world instanceof WorldServer)
            || !(event.getEntityLiving() instanceof EntityZombie)
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
        if (event.getEntityLiving() instanceof EntityZombie
            && PopulationTags.isManaged(event.getEntityLiving())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            pendingChunks.remove((WorldServer) event.getWorld());
        }
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
