package com.jammerbam.zomboid.event;

import com.jammerbam.zomboid.ai.navigation.CrowdNavigationManager;
import com.jammerbam.zomboid.ai.navigation.LocalPointNavigationManager;
import com.jammerbam.zomboid.ai.navigation.SharedNavigationManager;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Advances shared field builds independently of per-entity steering cadence. */
public final class NavigationEvents {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.world.isRemote) {
            long startedAt = RuntimePerformanceTelemetry.begin();
            try {
                CrowdNavigationManager.beginTick(event.world);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    event.world, PerformancePhase.CROWD_COORDINATION, startedAt
                );
            }
        }
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
            long startedAt = RuntimePerformanceTelemetry.begin();
            try {
                SharedNavigationManager.tick(event.world);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    event.world, PerformancePhase.SHARED_NAVIGATION, startedAt
                );
            }
            startedAt = RuntimePerformanceTelemetry.begin();
            try {
                LocalPointNavigationManager.tick(event.world);
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    event.world, PerformancePhase.LOCAL_NAVIGATION, startedAt
                );
            }
        }
    }
}
