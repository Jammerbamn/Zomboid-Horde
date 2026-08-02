package com.jammerbam.zomboid.audio;

import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.ai.brain.ZombieBrain;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.behavior.GroupActivitySchedule;
import com.jammerbam.zomboid.population.PopulationTags;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ZombieAudioController {
    private static final Map<World, Map<String, Long>> NEXT_AMBIENT_SOUND_AT =
        new WeakHashMap<>();
    private static final Map<EntityZombie, ZombieAmbientTimer> INDIVIDUAL_TIMERS =
        new WeakHashMap<>();

    private ZombieAudioController() {
    }

    public static void tick(EntityZombie zombie) {
        if (!ModConfig.enableStateAwareZombieAudio) {
            return;
        }

        int talkInterval = zombie.getTalkInterval();
        // Suppress EntityLiving's unmanaged timer and reproduce its exact
        // randomized opportunity cadence here so the horde admission limiter
        // can reject a sound before it is played.
        zombie.livingSoundTime = -talkInterval;
        ZombieAmbientTimer individual = INDIVIDUAL_TIMERS.computeIfAbsent(
            zombie, ignored -> new ZombieAmbientTimer(talkInterval)
        );
        if (!individual.advance(talkInterval, zombie.getRNG().nextInt(1000))) {
            return;
        }

        long now = zombie.world.getTotalWorldTime();
        ZombieBrain brain = ZombieBrainManager.get(zombie);
        boolean alerted = brain.getState().isAlertedForAudio();
        String scheduleKey = (alerted ? "alerted:" : "idle:") + soundGroup(zombie);
        Map<String, Long> worldSchedule = NEXT_AMBIENT_SOUND_AT.computeIfAbsent(
            zombie.world,
            ignored -> new HashMap<>()
        );
        Long nextAllowed = worldSchedule.get(scheduleKey);
        if (!GroupActivitySchedule.isDue(nextAllowed, now)) {
            return;
        }

        zombie.playLivingSound();
        zombie.livingSoundTime = -zombie.getTalkInterval();
        int interval = alerted
            ? ModConfig.alertedHordeSoundIntervalTicks
            : ModConfig.idleHordeSoundIntervalTicks;
        worldSchedule.put(scheduleKey, GroupActivitySchedule.nextAt(now, interval));
    }

    public static void clear(World world) {
        NEXT_AMBIENT_SOUND_AT.remove(world);
        INDIVIDUAL_TIMERS.keySet().removeIf(zombie -> zombie.world == world);
    }

    static String soundGroup(EntityZombie zombie) {
        String groupId = PopulationTags.getGroupId(zombie);
        if (!groupId.isEmpty()) {
            return groupId;
        }
        int cellSize = ModConfig.unmanagedZombieSoundCellSize;
        int cellX = Math.floorDiv(zombie.getPosition().getX(), cellSize);
        int cellZ = Math.floorDiv(zombie.getPosition().getZ(), cellSize);
        return "cell:" + cellX + "," + cellZ;
    }
}
