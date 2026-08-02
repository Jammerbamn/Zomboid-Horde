package com.jammerbam.zomboid.event;

import com.jammerbam.zomboid.ai.ZombieAlertManager;
import com.jammerbam.zomboid.ai.ZombieBlockBreakingManager;
import com.jammerbam.zomboid.behavior.NoiseManager;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class SoundSimulationEvents {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer) {
            long startedAt = RuntimePerformanceTelemetry.begin();
            try {
                NoiseManager.tick((WorldServer) event.world);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    event.world, PerformancePhase.SOUND_SIMULATION, startedAt
                );
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        NoiseManager.clear(event.getWorld());
        ZombieAlertManager.clear(event.getWorld());
        ZombieBlockBreakingManager.clear(event.getWorld());
    }
}
