package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.network.ServerTpsMessage;
import com.jammerbam.zomboid.network.ZomboidNetwork;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Measures server work time and periodically publishes a five-second TPS sample. */
public final class ServerTpsMonitor {
    public static final ServerTpsMonitor INSTANCE = new ServerTpsMonitor();
    private static final int SAMPLE_WINDOW_TICKS = 100;
    private static final int SYNC_INTERVAL_TICKS = 40;

    private final TpsSampler sampler = new TpsSampler(SAMPLE_WINDOW_TICKS);
    private long tickStartedNanos;
    private int ticksUntilSync = SYNC_INTERVAL_TICKS;

    private ServerTpsMonitor() {
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartedNanos = System.nanoTime();
            RuntimePerformanceTelemetry.beginServerTick();
            VanillaEntityWorkSampler.beginTick(Thread.currentThread());
            return;
        }

        if (tickStartedNanos == 0L) {
            return;
        }
        long elapsedNanoseconds = System.nanoTime() - tickStartedNanos;
        VanillaEntityWorkSampler.endTick(elapsedNanoseconds);
        RuntimePerformanceTelemetry.endServerTick(elapsedNanoseconds);
        VanillaPathRequestTelemetry.endServerTick();
        AutomaticRepathTelemetry.endServerTick();
        sampler.recordTick(elapsedNanoseconds);
        tickStartedNanos = 0L;
        ticksUntilSync--;
        if (ticksUntilSync <= 0) {
            ticksUntilSync = SYNC_INTERVAL_TICKS;
            ZomboidNetwork.CHANNEL.sendToAll(new ServerTpsMessage(
                (float) sampler.getTicksPerSecond()
            ));
        }
    }

    public void reset() {
        VanillaEntityWorkSampler.reset();
        VanillaPathRequestTelemetry.reset();
        AutomaticRepathTelemetry.reset();
        RuntimePerformanceTelemetry.reset();
        sampler.reset();
        tickStartedNanos = 0L;
        ticksUntilSync = SYNC_INTERVAL_TICKS;
    }

    public double getTicksPerSecond() {
        return sampler.getTicksPerSecond();
    }
}
